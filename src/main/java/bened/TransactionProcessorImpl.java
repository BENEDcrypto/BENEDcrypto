/******************************************************************************
 * Copyright © 2013-2016 The Nxt Core Developers.                             *
 *                                                                            *
 * See the AUTHORS.txt, DEVELOPER-AGREEMENT.txt and LICENSE.txt files at      *
 * the top-level directory of this distribution for the individual copyright  *
 * holder information and the developer policies on copyright and licensing.  *
 *                                                                            *
 * Unless otherwise agreed in a custom licensing agreement, no part of the    *
 * Nxt software, including this file, may be copied, modified, propagated,    *
 * or distributed except according to the terms contained in the LICENSE.txt  *
 * file.                                                                      *
 *                                                                            *
 * Removal or modification of this copyright notice is prohibited.            *
 *                                                                            *
 ******************************************************************************/

package bened;

import bened.util.ThreadPool;
import bened.util.Listener;
import bened.util.Convert;
import bened.util.Logger;
import bened.util.Listeners;
import bened.util.JSON;
import bened.db.DbClause;
import bened.db.DbIterator;
import bened.db.DbKey;
import bened.db.EntityDbTable;
import bened.peer.Peer;
import bened.peer.Peers;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

final class TransactionProcessorImpl implements TransactionProcessor {

    private static final boolean enableTransactionRebroadcasting = Bened.getBooleanProperty("bened.enableTransactionRebroadcasting");
    private static final boolean testUnconfirmedTransactions = Bened.getBooleanProperty("bened.testUnconfirmedTransactions");
    private static final int maxUnconfirmedTransactions;
    static {
        int n = Bened.getIntProperty("bened.maxUnconfirmedTransactions");
        maxUnconfirmedTransactions = n <= 0 ? Integer.MAX_VALUE : n;
    }

    private static final TransactionProcessorImpl instance = new TransactionProcessorImpl();

    static TransactionProcessorImpl getInstance() {
        return instance;
    }

    private final Map<DbKey, UnconfirmedTransaction> transactionCache = new HashMap<>();
    private volatile boolean cacheInitialized = false;
    private volatile boolean revalidateUnconfirmedTransactions = true;

    final DbKey.LongKeyFactory<UnconfirmedTransaction> unconfirmedTransactionDbKeyFactory = new DbKey.LongKeyFactory<UnconfirmedTransaction>("id") {

        @Override
        public DbKey newKey(UnconfirmedTransaction unconfirmedTransaction) {
            return unconfirmedTransaction.getTransaction().getDbKey();
        }

    };

    private final EntityDbTable<UnconfirmedTransaction> unconfirmedTransactionTable = new EntityDbTable<UnconfirmedTransaction>("unconfirmed_transaction", unconfirmedTransactionDbKeyFactory) {

        @Override
        protected UnconfirmedTransaction load(Connection con, ResultSet rs, DbKey dbKey) throws SQLException {
            return new UnconfirmedTransaction(rs);
        }

        @Override
        protected void save(Connection con, UnconfirmedTransaction unconfirmedTransaction) throws SQLException {
            boolean saved = unconfirmedTransaction.save(con);
            if (saved && ((transactionCache.size() < maxUnconfirmedTransactions)) ) {
                transactionCache.put(unconfirmedTransaction.getDbKey(), unconfirmedTransaction);
            }
        }

        @Override
        public void rollback(int height) {
            try (Connection con = Db.db.getConnection();
                 PreparedStatement pstmt = con.prepareStatement("SELECT * FROM unconfirmed_transaction WHERE height > ?")) {
                pstmt.setInt(1, height);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        UnconfirmedTransaction unconfirmedTransaction = load(con, rs, null);
                        waitingTransactions.add(unconfirmedTransaction);
                        transactionCache.remove(unconfirmedTransaction.getDbKey());
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException(e.toString(), e);
            }
            super.rollback(height);
            unconfirmedDuplicates.clear();
        }

        @Override
        public void truncate() {
            super.truncate();
            clearCache();
        }

        @Override
        protected String defaultSort() {
            return " ORDER BY transaction_height ASC, fee_per_byte DESC, arrival_timestamp ASC, id ASC ";
        }

    };

    private final Set<TransactionImpl> broadcastedTransactions = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Listeners<List<? extends Transaction>,Event> transactionListeners = new Listeners<>();

    private final PriorityQueue<UnconfirmedTransaction> waitingTransactions = new PriorityQueue<UnconfirmedTransaction>(
            (UnconfirmedTransaction o1, UnconfirmedTransaction o2) -> {
                int result;
                if ((result = Integer.compare(o2.getHeight(), o1.getHeight())) != 0) {
                    return result;
                }
                if ((result = Boolean.compare(o2.getTransaction().referencedTransactionFullHash() != null,
                        o1.getTransaction().referencedTransactionFullHash() != null)) != 0) {
                    return result;
                }
                if ((result = Long.compare(o1.getFeePerByte(), o2.getFeePerByte())) != 0) {
                    return result;
                }
                if ((result = Long.compare(o2.getArrivalTimestamp(), o1.getArrivalTimestamp())) != 0) {
                    return result;
                }
                return Long.compare(o2.getId(), o1.getId());
            })
    {

        @Override
        public boolean add(UnconfirmedTransaction unconfirmedTransaction) {
            if (!super.add(unconfirmedTransaction)) {
                return false;
            }
            if (size() > maxUnconfirmedTransactions) {
                UnconfirmedTransaction removed = remove();
                //Logger.logDebugMessage("Dropped unconfirmed transaction " + removed.getJSONObject().toJSONString());
            }
            return true;
        }

    };

    private final Map<TransactionType, Map<String, Integer>> unconfirmedDuplicates = new HashMap<>();


    private final Runnable removeUnconfirmedTransactionsThread = () -> {

        try {
            try {
                if (Bened.getBlockchainProcessor().isDownloading() && ! testUnconfirmedTransactions) {
                    return;
                }
                List<UnconfirmedTransaction> expiredTransactions = new ArrayList<>();
                DbIterator<UnconfirmedTransaction> iterator = unconfirmedTransactionTable.getManyBy(
                        new DbClause.IntClause("expiration", DbClause.Op.LT, Bened.getEpochTime()), 0, -1, "");
                while (iterator.hasNext()) {
                    expiredTransactions.add(iterator.next());
                }
                if (!expiredTransactions.isEmpty()) {
                    BlockchainImpl.getInstance().writeLock();
                    try {
                        try {
                            Db.db.beginTransaction();
                            for (UnconfirmedTransaction unconfirmedTransaction : expiredTransactions) {
                                removeUnconfirmedTransaction(unconfirmedTransaction.getTransaction());
                            }
                            Db.db.commitTransaction();
                        } catch (Exception e) {
                            Logger.logErrorMessage(e.toString(), e);
                            Db.db.rollbackTransaction();
                            throw e;
                        } finally {
                            Db.db.endTransaction();
                        }
                    } finally {
                        BlockchainImpl.getInstance().writeUnlock();
                    }
                }
                if (revalidateUnconfirmedTransactions) {
                    revalidateUnconfirmedTransactions = false;
                    revalidateUnconfirmedTransactions();
                }
            } catch (Exception e) {
                Logger.logMessage("Error removing unconfirmed transactions", e);
            }
        } catch (Throwable t) {
            Logger.logErrorMessage("CRITICAL ERROR. PLEASE REPORT TO THE DEVELOPERS.\n" + t.toString());
            System.exit(1);
        }

    };

    private final Runnable rebroadcastTransactionsThread = () -> {

        try {
            try {
                if (Bened.getBlockchainProcessor().isDownloading() && ! testUnconfirmedTransactions) {
                    return;
                }
                List<Transaction> transactionList = new ArrayList<>();
                int curTime = Bened.getEpochTime();
                for (TransactionImpl transaction : broadcastedTransactions) {
                    if (transaction.getExpiration() < curTime || TransactionDb.hasTransaction(transaction.getId())) {
                        broadcastedTransactions.remove(transaction);
                    } else if (transaction.getTimestamp() < curTime - 30) {
                        transactionList.add(transaction);
                    }
                }

                if (transactionList.size() > 0) {
                    Peers.sendToSomePeers(transactionList);
                }

            } catch (Exception e) {
                Logger.logMessage("Error in transaction re-broadcasting thread", e);
            }
        } catch (Throwable t) {
            Logger.logErrorMessage("CRITICAL ERROR. PLEASE REPORT TO THE DEVELOPERS.\n" + t.toString());
            t.printStackTrace();
            System.exit(1);
        }

    };

    private final Runnable processTransactionsThread = () -> {
        try {
            try {
                if (Bened.getBlockchainProcessor().isDownloading() && ! testUnconfirmedTransactions) {
                    return;
                }
                Peer peer = Peers.getAnyPeer(Peer.State.CONNECTED, true);
                if (peer == null) {
                    return;
                }
                JSONObject request = new JSONObject();
                request.put("requestType", "getUnconfirmedTransactions");
                JSONArray exclude = new JSONArray();
                getAllUnconfirmedTransactionIds().forEach(transactionId -> exclude.add(Long.toUnsignedString(transactionId)));
                Collections.sort(exclude);
                request.put("exclude", exclude);
                JSONObject response = peer.send(JSON.prepareRequest(request), Peers.MAX_MESSAGE_SIZE);
                if (response == null) {
                    return;
                }
                JSONArray transactionsData = (JSONArray)response.get("unconfirmedTransactions");
                if (transactionsData == null || transactionsData.size() == 0) {
                    return;
                }
                try {
                    processPeerTransactions(transactionsData);
                } catch (BNDException.ValidationException|RuntimeException e) {
                     Logger.logWarningMessage("processTransactionsThread: "+peer.getHost()+" blacklisted:"+e);
                    peer.blacklist(e);
                }
            } catch (Exception e) {
                Logger.logMessage("Error processing unconfirmed transactions", e);
            }
        } catch (Throwable t) {
            Logger.logErrorMessage("CRITICAL ERROR. PLEASE REPORT TO THE DEVELOPERS.\n" + t.toString());
            t.printStackTrace();
            System.exit(1);
        }

    };

    private final Runnable processWaitingTransactionsThread = () -> {

        try {
            try {
                if (Bened.getBlockchainProcessor().isDownloading() && ! testUnconfirmedTransactions) {
                    return;
                }
                processWaitingTransactions();
            } catch (Exception e) {
                Logger.logMessage("Error processing waiting transactions", e);
            }
        } catch (Throwable t) {
            Logger.logErrorMessage("CRITICAL ERROR. PLEASE REPORT TO THE DEVELOPERS.\n" + t.toString());
            t.printStackTrace();
            System.exit(1);
        }

    };


    private TransactionProcessorImpl() {
        if (!Constants.isLightClient) {
            if (!Constants.isOffline) {
        ThreadPool.scheduleThread("ProcessTransactions", processTransactionsThread, 40); //bilo 10
        ThreadPool.runAfterStart(this::rebroadcastAllUnconfirmedTransactions);
        ThreadPool.scheduleThread("RebroadcastTransactions", rebroadcastTransactionsThread, 45); // bilo 30
            }
            ThreadPool.scheduleThread("RemoveUnconfirmedTransactions", removeUnconfirmedTransactionsThread, 40);
            ThreadPool.scheduleThread("ProcessWaitingTransactions", processWaitingTransactionsThread, 45); //bilo 10
        }
    }

    @Override
    public boolean addListener(Listener<List<? extends Transaction>> listener, Event eventType) {
        return transactionListeners.addListener(listener, eventType);
    }

    @Override
    public boolean removeListener(Listener<List<? extends Transaction>> listener, Event eventType) {
        return transactionListeners.removeListener(listener, eventType);
    }

    void notifyListeners(List<? extends Transaction> transactions, Event eventType) {
        transactionListeners.notify(transactions, eventType);
    }

    @Override
    public DbIterator<UnconfirmedTransaction> getAllUnconfirmedTransactions() {
        return unconfirmedTransactionTable.getAll(0, -1);
    }

    @Override
    public DbIterator<UnconfirmedTransaction> getAllUnconfirmedTransactions(int from, int to) {
        return unconfirmedTransactionTable.getAll(from, to);
    }

    @Override
    public DbIterator<UnconfirmedTransaction> getAllUnconfirmedTransactions(String sort) {
        return unconfirmedTransactionTable.getAll(0, -1, sort);
    }

    @Override
    public DbIterator<UnconfirmedTransaction> getAllUnconfirmedTransactions(int from, int to, String sort) {
        return unconfirmedTransactionTable.getAll(from, to, sort);
    }

    @Override
    public Transaction getUnconfirmedTransaction(long transactionId) {
        DbKey dbKey = unconfirmedTransactionDbKeyFactory.newKey(transactionId);
        return getUnconfirmedTransaction(dbKey);
    }

    Transaction getUnconfirmedTransaction(DbKey dbKey) {
        Bened.getBlockchain().readLock();
        try {
            Transaction transaction = transactionCache.get(dbKey);
            if (transaction != null) {
                return transaction;
            }
        } finally {
            Bened.getBlockchain().readUnlock();
        }
        return unconfirmedTransactionTable.get(dbKey);
    }

    private List<Long> getAllUnconfirmedTransactionIds() {
        List<Long> result = new ArrayList<>();
        try (Connection con = Db.db.getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT id FROM unconfirmed_transaction");
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                result.add(rs.getLong("id"));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
        return result;
    }

    @Override
    public UnconfirmedTransaction[] getAllWaitingTransactions() {
        UnconfirmedTransaction[] transactions;
        BlockchainImpl.getInstance().readLock();
        try {
            transactions = waitingTransactions.toArray(new UnconfirmedTransaction[waitingTransactions.size()]);
        } finally {
            BlockchainImpl.getInstance().readUnlock();
        }
        Arrays.sort(transactions, waitingTransactions.comparator());
        return transactions;
    }

    Collection<UnconfirmedTransaction> getWaitingTransactions() {
        return Collections.unmodifiableCollection(waitingTransactions);
    }

    @Override
    public TransactionImpl[] getAllBroadcastedTransactions() {
        BlockchainImpl.getInstance().readLock();
        try {
            return broadcastedTransactions.toArray(new TransactionImpl[broadcastedTransactions.size()]);
        } finally {
            BlockchainImpl.getInstance().readUnlock();
        }
    }

    @Override
    public void broadcast(Transaction transaction) throws BNDException.ValidationException {
        BlockchainImpl.getInstance().writeLock();
        try {
            if (transaction.getSenderId() == transaction.getRecipientId()) {
                Logger.logMessage("Transaction " + transaction.getStringId() + " to self: " + transaction.getSenderId());
                return;
            }
            if (TransactionDb.hasTransaction(transaction.getId())) {
                Logger.logMessage("Transaction " + transaction.getStringId() + " already in blockchain, will not broadcast again");
                return;
            }
           if (getUnconfirmedTransaction(((TransactionImpl)transaction).getDbKey()) != null) {
                if (enableTransactionRebroadcasting) {
                    broadcastedTransactions.add((TransactionImpl) transaction);
                    Logger.logMessage("Transaction " + transaction.getStringId() + " already in unconfirmed pool, will re-broadcast");
                } else {
                   Logger.logMessage("Transaction " + transaction.getStringId() + " already in unconfirmed pool, will not broadcast again");
                }
                return;
            }
            transaction.validate();
            UnconfirmedTransaction unconfirmedTransaction = new UnconfirmedTransaction((TransactionImpl) transaction, System.currentTimeMillis());
            boolean broadcastLater = BlockchainProcessorImpl.getInstance().isProcessingBlock();
            if (broadcastLater) {
                waitingTransactions.add(unconfirmedTransaction);
                broadcastedTransactions.add((TransactionImpl) transaction);
                Logger.logDebugMessage("Will broadcast new transaction later " + transaction.getStringId());
            } else {
                processTransaction(unconfirmedTransaction);
                Logger.logDebugMessage("Accepted new transaction " + transaction.getStringId());
                List<Transaction> acceptedTransactions = Collections.singletonList(transaction);
                if (acceptedTransactions.size() > 0) {
                    Peers.sendToSomePeers(acceptedTransactions);
                }
                transactionListeners.notify(acceptedTransactions, Event.ADDED_UNCONFIRMED_TRANSACTIONS);
                if (enableTransactionRebroadcasting) {
                    broadcastedTransactions.add((TransactionImpl) transaction);
                }
            }
        } finally {
            BlockchainImpl.getInstance().writeUnlock();
        }
    }

    @Override
    public void processPeerTransactions(JSONObject request) throws BNDException.ValidationException {
        JSONArray transactionsData = (JSONArray)request.get("transactions");
        processPeerTransactions(transactionsData);
    }

    @Override
    public void clearUnconfirmedTransactions() {
        BlockchainImpl.getInstance().writeLock();
        try {
            List<Transaction> removed = new ArrayList<>();
            try {
                Db.db.beginTransaction();
                DbIterator<UnconfirmedTransaction> unconfirmedTransactions = getAllUnconfirmedTransactions();
                while (unconfirmedTransactions.hasNext()) {
                    UnconfirmedTransaction unconfirmedTransaction = unconfirmedTransactions.next();
                    unconfirmedTransaction.getTransaction().undoUnconfirmed();
                    removed.add(unconfirmedTransaction.getTransaction());
                }
                unconfirmedTransactionTable.truncate();
                Db.db.commitTransaction();
            } catch (Exception e) {
                Logger.logErrorMessage(e.toString(), e);
                Db.db.rollbackTransaction();
                throw e;
            } finally {
                Db.db.endTransaction();
            }
            unconfirmedDuplicates.clear();
            waitingTransactions.clear();
            broadcastedTransactions.clear();
            transactionCache.clear();
            transactionListeners.notify(removed, Event.REMOVED_UNCONFIRMED_TRANSACTIONS);
        } finally {
            BlockchainImpl.getInstance().writeUnlock();
        }
    }

    void revalidateUnconfirmedTransactions() {
        BlockchainImpl.getInstance().writeLock();
        try {
            List<TransactionImpl> toRemove = new ArrayList<>();
            try {
                Db.db.beginTransaction();
                DbIterator<UnconfirmedTransaction> unconfirmedTransactions = getAllUnconfirmedTransactions();
                    while (unconfirmedTransactions.hasNext()) {
                        UnconfirmedTransaction unconfirmedTransaction = unconfirmedTransactions.next();
                        if (unconfirmedTransaction.getSenderId() == unconfirmedTransaction.getRecipientId()) {
                            toRemove.add(unconfirmedTransaction.getTransaction());
                        }
                    }
                if (toRemove.size() > 0) {
                    for (TransactionImpl transaction : toRemove) {
                        removeUnconfirmedTransaction(transaction);
                        Logger.logWarningMessage("Removed unconfirmed transaction to self! Account: " + Long.toUnsignedString(transaction.getSenderId()) + ", transaction: "+transaction.getId());

                    }
                    Logger.logWarningMessage("Removed " + toRemove.size() + " unconfirmed transactions to self");
                }
                Db.db.commitTransaction();
            } catch (Exception e) {
                Logger.logErrorMessage(e.toString(), e);
                Db.db.rollbackTransaction();
                throw e;
            } finally {
                Db.db.endTransaction();
            }
        } finally {
            BlockchainImpl.getInstance().writeUnlock();
        }
    }

    @Override
    public void requeueAllUnconfirmedTransactions() {
        BlockchainImpl.getInstance().writeLock();
        try {
            if (!Db.db.isInTransaction()) {
                try {
                    Db.db.beginTransaction();
                    requeueAllUnconfirmedTransactions();
                    Db.db.commitTransaction();
                } catch (Exception e) {
                    Logger.logErrorMessage(e.toString(), e);
                    Db.db.rollbackTransaction();
                    throw e;
                } finally {
                    Db.db.endTransaction();
                }
                return;
            }
            List<Transaction> removed = new ArrayList<>();
            DbIterator<UnconfirmedTransaction> unconfirmedTransactions = getAllUnconfirmedTransactions();
                while (unconfirmedTransactions.hasNext()) {
                    UnconfirmedTransaction unconfirmedTransaction = unconfirmedTransactions.next();
                    unconfirmedTransaction.getTransaction().undoUnconfirmed();
                    if (removed.size() < maxUnconfirmedTransactions) {
                        removed.add(unconfirmedTransaction.getTransaction());
                    }
                    waitingTransactions.add(unconfirmedTransaction);
                }
            
            unconfirmedTransactionTable.truncate();
            unconfirmedDuplicates.clear();
            transactionCache.clear();
            transactionListeners.notify(removed, Event.REMOVED_UNCONFIRMED_TRANSACTIONS);
        } finally {
            BlockchainImpl.getInstance().writeUnlock();
        }
    }

    @Override
    public void rebroadcastAllUnconfirmedTransactions() {
        BlockchainImpl.getInstance().writeLock();
        try {
            DbIterator<UnconfirmedTransaction> oldNonBroadcastedTransactions = getAllUnconfirmedTransactions();
                while (oldNonBroadcastedTransactions.hasNext()) {
                    UnconfirmedTransaction unconfirmedTransaction = oldNonBroadcastedTransactions.next();
                    if (unconfirmedTransaction.getTransaction().isUnconfirmedDuplicate(unconfirmedDuplicates)) {
                        Logger.logDebugMessage("Skipping duplicate unconfirmed transaction " + unconfirmedTransaction.getTransaction().getJSONObject().toString());
                    } else if (enableTransactionRebroadcasting) {
                        broadcastedTransactions.add(unconfirmedTransaction.getTransaction());
                    }
                }
            
        } finally {
            BlockchainImpl.getInstance().writeUnlock();
        }
    }

    void removeUnconfirmedTransaction(TransactionImpl transaction) {
        if (!Db.db.isInTransaction()) {
            try {
                Db.db.beginTransaction();
                removeUnconfirmedTransaction(transaction);
                Db.db.commitTransaction();
            } catch (Exception e) {
                Logger.logErrorMessage(e.toString(), e);
                Db.db.rollbackTransaction();
                throw e;
            } finally {
                Db.db.endTransaction();
            }
            return;
        }
        try (Connection con = Db.db.getConnection();
             PreparedStatement pstmt = con.prepareStatement("DELETE FROM unconfirmed_transaction WHERE id = ?")) {
            pstmt.setLong(1, transaction.getId());
            int deleted = pstmt.executeUpdate();
            if (deleted > 0) {
                transaction.undoUnconfirmed();
                transactionCache.remove(transaction.getDbKey());
                transactionListeners.notify(Collections.singletonList(transaction), Event.REMOVED_UNCONFIRMED_TRANSACTIONS);
            }
        } catch (SQLException e) {
            Logger.logErrorMessage(e.toString(), e);
            throw new RuntimeException(e.toString(), e);
        }
    }

    @Override
    public void processLater(Collection<? extends Transaction> transactions) {
        long currentTime = System.currentTimeMillis();
        BlockchainImpl.getInstance().writeLock();
        try {
            for (Transaction transaction : transactions) {
                BlockDb.transactionCache.remove(transaction.getId());
                if (TransactionDb.hasTransaction(transaction.getId())) {
                    continue;
                }
                ((TransactionImpl)transaction).unsetBlock();
                try {
                    SMGBlock.Transaction trx = SoftMG.convert((TransactionImpl)transaction);
                    if (trx.getSender() == Genesis.CREATOR_ID && !Bened.softMG().canReceive(trx)) continue;
                    waitingTransactions.add(new UnconfirmedTransaction((TransactionImpl)transaction, Math.min(currentTime, Convert.fromEpochTime(transaction.getTimestamp()))));
                } catch (HGException ex) {
                    continue;
                }
            }
        } finally {
            BlockchainImpl.getInstance().writeUnlock();
        }
    }

    void processWaitingTransactions() {
        BlockchainImpl.getInstance().writeLock();
        try {
            if (waitingTransactions.size() > 0) {
                int currentTime = Bened.getEpochTime();
                List<Transaction> addedUnconfirmedTransactions = new ArrayList<>();
                Iterator<UnconfirmedTransaction> iterator = waitingTransactions.iterator();
                while (iterator.hasNext()) {
                    UnconfirmedTransaction unconfirmedTransaction = iterator.next();
                    try {
                        unconfirmedTransaction.validate();
                        processTransaction(unconfirmedTransaction);
                        iterator.remove();
                        addedUnconfirmedTransactions.add(unconfirmedTransaction.getTransaction());
                    } catch (BNDException.ExistingTransactionException e) {
                        iterator.remove();
                    } catch (BNDException.NotCurrentlyValidException e) {
                        if (unconfirmedTransaction.getExpiration() < currentTime
                                || currentTime - Convert.toEpochTime(unconfirmedTransaction.getArrivalTimestamp()) > 3600) {
                            iterator.remove();
                        }
                    } catch (BNDException.ValidationException|RuntimeException e) {
                        iterator.remove();
                    }
                }
                if (addedUnconfirmedTransactions.size() > 0) {
                    transactionListeners.notify(addedUnconfirmedTransactions, Event.ADDED_UNCONFIRMED_TRANSACTIONS);
                }
            }
        } finally {
            BlockchainImpl.getInstance().writeUnlock();
        }
    }

    
    private void processPeerTransactions(JSONArray transactionsData) throws BNDException.NotValidException {
        if (Bened.getBlockchain().getHeight() <= Constants.LAST_KNOWN_BLOCK && !testUnconfirmedTransactions) {
            return;
        }
        if (transactionsData == null || transactionsData.isEmpty()) {
            return;
        }
        long arrivalTimestamp = System.currentTimeMillis();
        List<TransactionImpl> receivedTransactions = new ArrayList<>();
        List<TransactionImpl> sendToPeersTransactions = new ArrayList<>();
        List<TransactionImpl> addedUnconfirmedTransactions = new ArrayList<>();
        List<Exception> exceptions = new ArrayList<>();
        for (Object transactionData : transactionsData) {
            try {
                TransactionImpl transaction = TransactionImpl.parseTransaction((JSONObject) transactionData);

                TransactionImpl trxn = BlockchainImpl.getInstance().getTransaction(transaction.getId());

                if (trxn != null && trxn.getHeight() != transaction.getHeight()) continue;

                if (transaction.getType().getType() > 2 && BlockchainImpl.getInstance().getHeight() > Constants.CONTROL_TRX_TO_ORDINARY
                || transaction.getType().getType() == 1 && (transaction.getType().getSubtype() == 1 || transaction.getType().getSubtype() == 8) && BlockchainImpl.getInstance().getHeight() > Constants.LAST_ALIASES_BLOCK)
                    continue;

                if (transaction.getSenderId() == Genesis.CREATOR_ID) {
                    continue;
                } else {
                    if (transaction.getAmountNQT() < 0l) {
                        continue;
                    }
                    if (transaction.getFeeNQT() < Bened.softMG().getFixedFee(transaction)) {
                        continue;
                    }
                    if (transaction.getSenderId() == transaction.getRecipientId()) {
                        Logger.logWarningMessage("Blocked transaction to self received from peer! Account: " + Long.toUnsignedString(transaction.getSenderId()) + ", transaction: "+transaction.getId()+" " + transactionData.toString());
                        continue;
                    }
                }
                receivedTransactions.add(transaction);
                if (getUnconfirmedTransaction(transaction.getDbKey()) != null || TransactionDb.hasTransaction(transaction.getId())) {
                    continue;
                }
                transaction.validate();
                UnconfirmedTransaction unconfirmedTransaction = new UnconfirmedTransaction(transaction, arrivalTimestamp);
                processTransaction(unconfirmedTransaction);
                if (broadcastedTransactions.contains(transaction)) {
                    Logger.logDebugMessage("Received back transaction " + transaction.getStringId()
                            + " that we broadcasted, will not forward again to peers");
                } else {
                    sendToPeersTransactions.add(transaction);
                }
                addedUnconfirmedTransactions.add(transaction);

            } catch (BNDException.NotCurrentlyValidException ignore) {
            } catch (BNDException.ValidationException|RuntimeException e) {
                Logger.logDebugMessage(String.format("Invalid transaction from peer: %s", ((JSONObject) transactionData).toJSONString()), e);
                exceptions.add(e);
            }
        }
        if (sendToPeersTransactions.size() > 0) {
            Peers.sendToSomePeers(sendToPeersTransactions);
        }
        if (addedUnconfirmedTransactions.size() > 0) {
            transactionListeners.notify(addedUnconfirmedTransactions, Event.ADDED_UNCONFIRMED_TRANSACTIONS);
        }
        broadcastedTransactions.removeAll(receivedTransactions);
        if (!exceptions.isEmpty()) {
            throw new BNDException.NotValidException("Peer sends invalid transactions: " + exceptions.toString());
        }
    }

    private void processTransaction(UnconfirmedTransaction unconfirmedTransaction) throws BNDException.ValidationException {
        TransactionImpl transaction = unconfirmedTransaction.getTransaction();
        int curTime = Bened.getEpochTime();
        if  (!UnconfirmedTransaction.transactionBytesIsValid(transaction.getBytes())) {
            throw new BNDException.NotValidException("Invalid transaction bytes");
        }
        if (transaction.getTimestamp() > curTime + Constants.MAX_TIMEDRIFT+Constants.Allow_future_inVM || transaction.getExpiration() < curTime) {
            throw new BNDException.NotCurrentlyValidException("Invalid transaction timestamp"
                    +(Constants.Allow_future_inVM>0?", future +"+(transaction.getTimestamp()-curTime)+", allow +"+Constants.Allow_future_inVM:""));
        }
        if (transaction.getVersion() < 1) {
            throw new BNDException.NotValidException("Invalid transaction version");
        }
        if (transaction.getId() == 0L) {
            throw new BNDException.NotValidException("Invalid transaction id 0");
        }
        BlockchainImpl.getInstance().writeLock();
        try {
            try {
                Db.db.beginTransaction();
                if (Bened.getBlockchain().getHeight() <= Constants.LAST_KNOWN_BLOCK && !testUnconfirmedTransactions) {
                    throw new BNDException.NotCurrentlyValidException("Blockchain not ready to accept transactions");
                }

                if (getUnconfirmedTransaction(transaction.getDbKey()) != null || TransactionDb.hasTransaction(transaction.getId())) {
                    throw new BNDException.ExistingTransactionException("Transaction already processed");
                }

                if (! transaction.verifySignature()) {
                    if (Account.getAccount(transaction.getSenderId()) != null) {
                        throw new BNDException.NotValidException("Transaction signature verification failed");
                    } else {
                        throw new BNDException.NotCurrentlyValidException("Unknown transaction sender");
                    }
                }

                if (! transaction.applyUnconfirmed()) {
                    throw new BNDException.InsufficientBalanceException("Insufficient balance");
                }

                if (transaction.isUnconfirmedDuplicate(unconfirmedDuplicates)) {
                    throw new BNDException.NotCurrentlyValidException("Duplicate unconfirmed transaction");
                }

                unconfirmedTransactionTable.insert(unconfirmedTransaction);

                Db.db.commitTransaction();
            } catch (Exception e) {
                Db.db.rollbackTransaction();
                throw e;
            } finally {
                Db.db.endTransaction();
            }
        } finally {
            BlockchainImpl.getInstance().writeUnlock();
        }
    }

    private static final Comparator<UnconfirmedTransaction> cachedUnconfirmedTransactionComparator = (UnconfirmedTransaction t1, UnconfirmedTransaction t2) -> {
        int compare;
        // Sort by transaction_height ASC
        compare = Integer.compare(t1.getHeight(), t2.getHeight());
        if (compare != 0)
            return compare;
        // Sort by fee_per_byte DESC
        compare = Long.compare(t1.getFeePerByte(), t2.getFeePerByte());
        if (compare != 0)
            return -compare;
        // Sort by arrival_timestamp ASC
        compare = Long.compare(t1.getArrivalTimestamp(), t2.getArrivalTimestamp());
        if (compare != 0)
            return compare;
        // Sort by transaction ID ASC
        return Long.compare(t1.getId(), t2.getId());
    };

    /**
     * Get the cached unconfirmed transactions
     *
     * @param   exclude                 List of transaction identifiers to exclude
     */
    @Override
    public SortedSet<? extends Transaction> getCachedUnconfirmedTransactions(List<String> exclude) {
        SortedSet<UnconfirmedTransaction> transactionSet = new TreeSet<>(cachedUnconfirmedTransactionComparator);
        Bened.getBlockchain().readLock();
        try {
            //
            // Initialize the unconfirmed transaction cache if it hasn't been done yet
            //
            synchronized(transactionCache) {
                if (!cacheInitialized) {
                    DbIterator<UnconfirmedTransaction> it = getAllUnconfirmedTransactions();
                    while (it.hasNext()) {
                        UnconfirmedTransaction unconfirmedTransaction = it.next();
                        transactionCache.put(unconfirmedTransaction.getDbKey(), unconfirmedTransaction);
                    }
                    cacheInitialized = true;
                }
            }
            //
            // Build the result set
            //
            transactionCache.values().forEach(transaction -> {
                if (Collections.binarySearch(exclude, transaction.getStringId()) < 0) {
                    transactionSet.add(transaction);
                }
            });
        } finally {
            Bened.getBlockchain().readUnlock();
        }
        return transactionSet;
    }

    /**
     * Restore expired prunable data
     *
     * @param   transactions                        Transactions containing prunable data
     * @return                                      Processed transactions
     * @throws  BenedException.NotValidException    Transaction is not valid
     */
    @Override
    public List<Transaction> restorePrunableData(JSONArray transactions) throws BNDException.NotValidException {
        List<Transaction> processed = new ArrayList<>();
        Bened.getBlockchain().readLock();
        try {
            Db.db.beginTransaction();
            try {
                //
                // Check each transaction returned by the archive peer
                //
                for (Object transactionJSON : transactions) {
                    TransactionImpl transaction = TransactionImpl.parseTransaction((JSONObject)transactionJSON);
                    TransactionImpl myTransaction = TransactionDb.findTransactionByFullHash(transaction.fullHash());
                    if (myTransaction != null) {
                        boolean foundAllData = true;
                        //
                        // Process each prunable appendage
                        //
                        appendageLoop: for (Appendix.AbstractAppendix appendage : transaction.getAppendages()) {
                            if ((appendage instanceof Appendix.Prunable)) {
                                //
                                // Don't load the prunable data if we already have the data
                                //
                                for (Appendix.AbstractAppendix myAppendage : myTransaction.getAppendages()) {
                                    if (myAppendage.getClass() == appendage.getClass()) {
                                        myAppendage.loadPrunable(myTransaction, true);
                                        if (((Appendix.Prunable)myAppendage).hasPrunableData()) {
                                            Logger.logDebugMessage(String.format("Already have prunable data for transaction %s %s appendage",
                                                    myTransaction.getStringId(), myAppendage.getAppendixName()));
                                            continue appendageLoop;
                                        }
                                        break;
                                    }
                                }
                                //
                                // Load the prunable data
                                //
                                if (((Appendix.Prunable)appendage).hasPrunableData()) {
                                    Logger.logDebugMessage(String.format("Loading prunable data for transaction %s %s appendage",
                                            Long.toUnsignedString(transaction.getId()), appendage.getAppendixName()));
                                    ((Appendix.Prunable)appendage).restorePrunableData(transaction, myTransaction.getBlockTimestamp(), myTransaction.getHeight());
                                } else {
                                    foundAllData = false;
                                }
                            }
                        }
                        if (foundAllData) {
                            processed.add(myTransaction);
                        }
                        Db.db.clearCache();
                        Db.db.commitTransaction();
                    }
                }
                Db.db.commitTransaction();
            } catch (Exception e) {
                Db.db.rollbackTransaction();
                processed.clear();
                throw e;
            } finally {
                Db.db.endTransaction();
            }
        } finally {
            Bened.getBlockchain().readUnlock();
        }
        return processed;
    }
}
