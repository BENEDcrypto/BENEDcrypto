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

import bened.db.DbUtils;
import bened.db.DerivedDbTable;
import bened.util.Convert;
import bened.util.Listener;
import bened.util.Listeners;
import bened.util.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Maintain a ledger of changes to selected accounts
 */
public class AccountLedger {

    /** Account ledger is enabled */
    private static final boolean ledgerEnabled;

    /** Track all accounts */
    private static final boolean trackAllAccounts;

    /** Accounts to track */
    private static final SortedSet<Long> trackAccounts = new TreeSet<>();

    /** Unconfirmed logging */
    private static final int logUnconfirmed;

    /** Number of blocks to keep when trimming */
    public static final int trimKeep = Bened.getIntProperty("bened.ledgerTrimKeep", 30000);

    /** Blockchain */
    private static final Blockchain blockchain = Bened.getBlockchain();

    /** Blockchain processor */
    private static final BlockchainProcessor blockchainProcessor = Bened.getBlockchainProcessor();

    /** Pending ledger entries */
    private static final List<LedgerEntry> pendingEntries = new ArrayList<>();

    /**
     * Process ledgerAccounts
     */
    static {
        List<String> ledgerAccounts = Bened.getStringListProperty("bened.ledgerAccounts");
        ledgerEnabled = !ledgerAccounts.isEmpty();
        trackAllAccounts = ledgerAccounts.contains("*");
        if (ledgerEnabled) {
            if (trackAllAccounts) {
                Logger.logInfoMessage("Account ledger is tracking all accounts");
            } else {
                for (String account : ledgerAccounts) {
                    try {
                        trackAccounts.add(Convert.parseAccountId(account));
                        Logger.logInfoMessage("Account ledger is tracking account " + account);
                    } catch (RuntimeException e) {
                        Logger.logErrorMessage("Account " + account + " is not valid; ignored");
                    }
                }
            }
        } else {
            Logger.logInfoMessage("Account ledger is not enabled");
        }
        int temp = Bened.getIntProperty("bened.ledgerLogUnconfirmed", 1);
        logUnconfirmed = (temp >= 0 && temp <= 2 ? temp : 1);
    }

    /**
     * Account ledger table
     */
    private static class AccountLedgerTable extends DerivedDbTable {

        /**
         * Create the account ledger table
         */
        public AccountLedgerTable() {
            super("account_ledger");
        }

        /**
         * Insert an entry into the table
         *
         * @param   ledgerEntry             Ledger entry
         */
        public void insert(LedgerEntry ledgerEntry) {
            try (Connection con = db.getConnection()) {
                ledgerEntry.save(con);
            } catch (SQLException e) {
                throw new RuntimeException(e.toString(), e);
            }
        }

        /**
         * Trim the account ledger table
         *
         * @param   height                  Trim height
         */
        @Override
        public void trim(int height) {
            if (trimKeep <= 0)
                return;
            try (Connection con = db.getConnection(); PreparedStatement pstmt = con.prepareStatement("DELETE FROM account_ledger WHERE height <= ? LIMIT " + Constants.BATCH_COMMIT_SIZE)) {
                pstmt.setInt(1, Math.max(blockchain.getHeight() - trimKeep, 0));
                int trimmed;
                do {
                    trimmed = pstmt.executeUpdate();
                    Db.db.commitTransaction();
                } while (trimmed >= Constants.BATCH_COMMIT_SIZE);
            } catch (SQLException e) {
                throw new RuntimeException(e.toString(), e);
            }
        }
    }
    private static final AccountLedgerTable accountLedgerTable = new AccountLedgerTable();

    /**
     * Initialization
     *
     * We don't do anything but we need to be called from Nxt.init() in order to
     * register our table
     */
    static void init() {
    }

    /**
     * Account ledger listener events
     */
    public enum Event {
        ADD_ENTRY
    }

    /**
     * Account ledger listeners
     */
    private static final Listeners<LedgerEntry, Event> listeners = new Listeners<>();

    /**
     * Add a listener
     *
     * @param   listener                    Listener
     * @param   eventType                   Event to listen for
     * @return                              True if the listener was added
     */
    public static boolean addListener(Listener<LedgerEntry> listener, Event eventType) {
        return listeners.addListener(listener, eventType);
    }

    /**
     * Remove a listener
     *
     * @param   listener                    Listener
     * @param   eventType                   Event to listen for
     * @return                              True if the listener was removed
     */
    public static boolean removeListener(Listener<LedgerEntry> listener, Event eventType) {
        return listeners.removeListener(listener, eventType);
    }

    static boolean mustLogEntry(long accountId, boolean isUnconfirmed) {

        if (!ledgerEnabled || (!trackAllAccounts && !trackAccounts.contains(accountId))) {
            return false;
        }

        if (!blockchainProcessor.isProcessingBlock()) {
            return false;
        }

        if (isUnconfirmed && logUnconfirmed == 0) {
            return false;
        }
        if (!isUnconfirmed && logUnconfirmed == 2) {
            return false;
        }
        if (trimKeep > 0 && blockchain.getHeight() <= Constants.LAST_KNOWN_BLOCK - trimKeep) {
            return false;
        }

        if (blockchainProcessor.isScanning() && trimKeep > 0 &&
                blockchain.getHeight() <= blockchainProcessor.getInitialScanHeight() - trimKeep) {
            return false;
        }
        return true;
    }

    /**
     * Log an event in the account_ledger table
     *
     * @param   ledgerEntry                 Ledger entry
     */
    static void logEntry(LedgerEntry ledgerEntry) {
 
        if (!Db.db.isInTransaction()) {
            throw new IllegalStateException("Not in transaction");
        }

        int index = pendingEntries.indexOf(ledgerEntry);
        if (index >= 0) {
            LedgerEntry existingEntry = pendingEntries.remove(index);
            ledgerEntry.updateChange(existingEntry.getChange());
            long adjustedBalance = existingEntry.getBalance() - existingEntry.getChange();
            for (; index < pendingEntries.size(); index++) {
                existingEntry = pendingEntries.get(index);
                if (existingEntry.getAccountId() == ledgerEntry.getAccountId() &&
                        existingEntry.getHolding() == ledgerEntry.getHolding() &&
                        ((existingEntry.getHoldingId() == null && ledgerEntry.getHoldingId() == null) ||
                        (existingEntry.getHoldingId() != null && existingEntry.getHoldingId().equals(ledgerEntry.getHoldingId())))) {
                    adjustedBalance += existingEntry.getChange();
                    existingEntry.setBalance(adjustedBalance);
                }
            }
        }
        pendingEntries.add(ledgerEntry);
    }


    static void commitEntries() {
        for (LedgerEntry ledgerEntry : pendingEntries) {
            accountLedgerTable.insert(ledgerEntry);
            listeners.notify(ledgerEntry, Event.ADD_ENTRY);
        }
        pendingEntries.clear();
    }

  
    static void clearEntries() {
        pendingEntries.clear();
    }

    /**
     * Return a single entry identified by the ledger entry identifier
     *
     * @param   ledgerId                    Ledger entry identifier
     * @return                              Ledger entry or null if entry not found
     */
    public static LedgerEntry getEntry(long ledgerId) {
        if (!ledgerEnabled)
            return null;
        LedgerEntry entry;
        try (Connection con = Db.db.getConnection();
                PreparedStatement stmt = con.prepareStatement("SELECT * FROM account_ledger WHERE db_id = ?")) {
            stmt.setLong(1, ledgerId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next())
                    entry = new LedgerEntry(rs);
                else
                    entry = null;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
        return entry;
    }

    /**
     * Return the ledger entries sorted in descending insert order
     *
     *
     * @param   accountId                   Account identifier or zero if no account identifier
     * @param   event                       Ledger event or null
     * @param   eventId                     Ledger event identifier or zero if no event identifier
     * @param   holding                     Ledger holding or null
     * @param   holdingId                   Ledger holding identifier or zero if no holding identifier
     * @param   firstIndex                  First matching entry index, inclusive
     * @param   lastIndex                   Last matching entry index, inclusive
     * @return                              List of ledger entries
     */
    public static List<LedgerEntry> getEntries(long accountId, LedgerEvent event, long eventId,
                                                LedgerHolding holding, long holdingId,
                                                int firstIndex, int lastIndex) {
        if (!ledgerEnabled) {
            return Collections.emptyList();
        }
        List<LedgerEntry> entryList = new ArrayList<>();

        StringBuilder sb = new StringBuilder(128);
        sb.append("SELECT * FROM account_ledger USE INDEX (ACCOUNT_LEDGER_ID_IDX) ") ;
        if (accountId != 0 || event != null || holding != null) {
            sb.append("WHERE ");
        }
        if (accountId != 0) {
            sb.append("account_id = ? AND db_id>-1");
        }
        if (event != null) {
            if (accountId != 0) {
                sb.append("AND ");
            }
            sb.append("event_type = ? ");
            if (eventId != 0)
                sb.append("AND event_id = ? ");
        }
        if (holding != null) {
            if (accountId != 0 || event != null) {
                sb.append("AND ");
            }
            sb.append("holding_type = ? ");
            if (holdingId != 0)
                sb.append("AND holding_id = ? ");
        }
        sb.append("ORDER BY account_id,db_id DESC ");
        sb.append(DbUtils.limitsClause(firstIndex, lastIndex));

        blockchain.readLock();
        try (Connection con = Db.db.getConnection();
             PreparedStatement pstmt = con.prepareStatement(sb.toString())) {
            int i = 0;
            if (accountId != 0) {
                pstmt.setLong(++i, accountId);
            }
            if (event != null) {
                pstmt.setByte(++i, (byte)event.getCode());
                if (eventId != 0) {
                    pstmt.setLong(++i, eventId);
                }
            }
            if (holding != null) {
                pstmt.setByte(++i, (byte)holding.getCode());
                if (holdingId != 0) {
                    pstmt.setLong(++i, holdingId);
                }
            }
            DbUtils.setLimits(++i, pstmt, firstIndex, lastIndex);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    entryList.add(new LedgerEntry(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        } finally {
            blockchain.readUnlock();
        }
        return entryList;
    }

    /**
     * Ledger events
     *
     * There must be a ledger event defined for each transaction (type,subtype) pair.  When adding
     * a new event, do not change the existing code assignments since these codes are stored in
     * the event_type field of the account_ledger table.
     */
    public enum LedgerEvent {

            BLOCK_GENERATED(1, false),
            TRANSACTION_FEE(50, true),
            ORDINARY_PAYMENT(3, true),
            ACCOUNT_INFO(4, true),
            ALIAS_ASSIGNMENT(5, true),
            ALIAS_BUY(6, true),
            ALIAS_DELETE(7, true),
            ALIAS_SELL(8, true),
            ARBITRARY_MESSAGE(9, true);



        private static final Map<Integer, LedgerEvent> eventMap = new HashMap<>();
        static {
            for (LedgerEvent event : values()) {
                if (eventMap.put(event.code, event) != null) {
                    throw new RuntimeException("LedgerEvent code " + event.code + " reused");
                }
            }
        }


        private final int code;

        private final boolean isTransaction;

        /**
         * Create the ledger event
         *
         * @param   code                    Event code
         * @param   isTransaction           Event identifier is a transaction
         */
        LedgerEvent(int code, boolean isTransaction) {
            this.code = code;
            this.isTransaction = isTransaction;
        }

        /**
         * Check if the event identifier is a transaction
         *
         * @return                          TRUE if the event identifier is a transaction
         */
        public boolean isTransaction() {
            return isTransaction;
        }

        /**
         * Return the event code
         *
         * @return                          Event code
         */
        public int getCode() {
            return code;
        }

        /**
         * Get the event from the event code
         *
         * @param   code                    Event code
         * @return                          Event
         */
        public static LedgerEvent fromCode(int code) {
            LedgerEvent event = eventMap.get(code);
            if (event == null) {
                throw new IllegalArgumentException("LedgerEvent code " + code + " is unknown");
            }
            return event;
        }
    }

    /**
     * Ledger holdings
     *
     * When adding a new holding, do not change the existing code assignments since
     * they are stored in the holding_type field of the account_ledger table.
     */
    public enum LedgerHolding {
        UNCONFIRMED_BENED_BALANCE(1, true),
        BENED_BALANCE(2, false),
        UNCONFIRMED_ASSET_BALANCE(3, true),
        ASSET_BALANCE(4, false),
        UNCONFIRMED_CURRENCY_BALANCE(5, true),
        CURRENCY_BALANCE(6, false);


        private static final Map<Integer, LedgerHolding> holdingMap = new HashMap<>();
        static {
            for (LedgerHolding holding : values()) {
                if (holdingMap.put(holding.code, holding) != null) {
                    throw new RuntimeException("LedgerHolding code " + holding.code + " reused");
                }
            }
        }


        private final int code;


        private final boolean isUnconfirmed;

        /**
         * Create the holding event
         *
         * @param   code                    Holding code
         * @param   isUnconfirmed           TRUE if the holding is unconfirmed
         */
        LedgerHolding(int code, boolean isUnconfirmed) {
            this.code = code;
            this.isUnconfirmed = isUnconfirmed;
        }

        /**
         * Check if the holding is unconfirmed
         *
         * @return                          TRUE if the holding is unconfirmed
         */
        public boolean isUnconfirmed() {
            return this.isUnconfirmed;
        }

        /**
         * Return the holding code
         *
         * @return                          Holding code
         */
        public int getCode() {
            return code;
        }

        /**
         * Get the holding from the holding code
         *
         * @param   code                    Holding code
         * @return                          Holding
         */
        public static LedgerHolding fromCode(int code) {
            LedgerHolding holding = holdingMap.get(code);
            if (holding == null) {
                throw new IllegalArgumentException("LedgerHolding code " + code + " is unknown");
            }
            return holding;
        }
    }

    /**
     * Ledger entry
     */
    public static class LedgerEntry {

        /** Ledger identifier */
        private long ledgerId = -1;

        /** Ledger event */
        private final LedgerEvent event;

        /** Associated event identifier */
        private final long eventId;

        /** Account identifier */
        private final long accountId;

        /** Holding */
        private final LedgerHolding holding;

        /** Holding identifier */
        private final Long holdingId;

        /** Change in balance */
        private long change;

        /** New balance */
        private long balance;

        /** Block identifier */
        private final long blockId;

        /** Blockchain height */
        private final int height;

        /** Block timestamp */
        private final int timestamp;

        /**
         * Create a ledger entry
         *
         * @param   event                   Event
         * @param   eventId                 Event identifier
         * @param   accountId               Account identifier
         * @param   holding                 Holding or null
         * @param   holdingId               Holding identifier or null
         * @param   change                  Change in balance
         * @param   balance                 New balance
         */
        public LedgerEntry(LedgerEvent event, long eventId, long accountId, LedgerHolding holding, Long holdingId,
                                            long change, long balance) {
            this.event = event;
            this.eventId = eventId;
            this.accountId = accountId;
            this.holding = holding;
            this.holdingId = holdingId;
            this.change = change;
            this.balance = balance;
            Block block = blockchain.getLastBlock();
            this.blockId = block.getId();
            this.height = block.getHeight();
            this.timestamp = block.getTimestamp();
        }

        /**
         * Create a ledger entry
         *
         * @param   event                   Event
         * @param   eventId                 Event identifier
         * @param   accountId               Account identifier
         * @param   change                  Change in balance
         * @param   balance                 New balance
         */
        public LedgerEntry(LedgerEvent event, long eventId, long accountId, long change, long balance) {
            this(event, eventId, accountId, null, null, change, balance);
        }

        /**
         * Create a ledger entry from a database entry
         *
         * @param   rs                      Result set
         * @throws  SQLException            Database error occurred
         */
        private LedgerEntry(ResultSet rs) throws SQLException {
            ledgerId = rs.getLong("db_id");
            event = LedgerEvent.fromCode(rs.getByte("event_type"));
            eventId = rs.getLong("event_id");
            accountId = rs.getLong("account_id");
            int holdingType = rs.getByte("holding_type");
            if (holdingType >= 0) {
                holding = LedgerHolding.fromCode(holdingType);
            } else {
                holding = null;
            }
            long id = rs.getLong("holding_id");
            if (rs.wasNull()) {
                holdingId = null;
            } else {
                holdingId = id;
            }
            change = rs.getLong("change");
            balance = rs.getLong("balance");
            blockId = rs.getLong("block_id");
            height = rs.getInt("height");
            timestamp = rs.getInt("timestamp");
        }

        /**
         * Return the ledger identifier
         *
         * @return                          Ledger identifier or -1 if not set
         */
        public long getLedgerId() {
            return ledgerId;
        }

        /**
         * Return the ledger event
         *
         * @return                          Ledger event
         */
        public LedgerEvent getEvent() {
            return event;
        }

        /**
         * Return the associated event identifier
         *
         * @return                          Event identifier
         */
        public long getEventId() {
            return eventId;
        }

        /**
         * Return the account identifier
         *
         * @return                          Account identifier
         */
        public long getAccountId() {
            return accountId;
        }

        /**
         * Return the holding
         *
         * @return                          Holding or null if there is no holding
         */
        public LedgerHolding getHolding() {
            return holding;
        }

        /**
         * Return the holding identifier
         *
         * @return                          Holding identifier or null if there is no holding identifier
         */
        public Long getHoldingId() {
            return holdingId;
        }

        /**
         * Update the balance change
         *
         * @param   amount                  Change amount
         */
        private void updateChange(long amount) {
            change += amount;
        }

        /**
         * Return the balance change
         *
         * @return                          Balance changes
         */
        public long getChange() {
            return change;
        }

        /**
         * Set the new balance
         *
         * @param balance                   New balance
         */
        private void setBalance(long balance) {
            this.balance = balance;
        }

        /**
         * Return the new balance
         *
         * @return                          New balance
         */
        public long getBalance() {
            return balance;
        }

        /**
         * Return the block identifier
         *
         * @return                          Block identifier
         */
        public long getBlockId() {
            return blockId;
        }

        /**
         * Return the height
         *
         * @return                          Height
         */
        public int getHeight() {
            return height;
        }

        /**
         * Return the timestamp
         *
         * @return                          Timestamp
         */
        public int getTimestamp() {
            return timestamp;
        }

        /**
         * Return the hash code
         *
         * @return                          Hash code
         */
        @Override
        public int hashCode() {
            return (Long.hashCode(accountId) ^ event.getCode() ^ Long.hashCode(eventId) ^
                    (holding != null ? holding.getCode() : 0) ^ (holdingId != null ? Long.hashCode(holdingId) : 0));
        }

        /**
         * Check if two ledger events are equal
         *
         * @param   obj                     Ledger event to check
         * @return                          TRUE if the ledger events are the same
         */
        @Override
        public boolean equals(Object obj) {
            return (obj != null && (obj instanceof LedgerEntry) && accountId == ((LedgerEntry)obj).accountId &&
                    event == ((LedgerEntry)obj).event && eventId == ((LedgerEntry)obj).eventId &&
                    holding == ((LedgerEntry)obj).holding &&
                    (holdingId != null ? holdingId.equals(((LedgerEntry)obj).holdingId) : ((LedgerEntry)obj).holdingId == null));
        }

        /**
         * Save the ledger entry
         *
         * @param   con                     Database connection
         * @throws  SQLException            Database error occurred
         */
        private void save(Connection con) throws SQLException {
            try (PreparedStatement stmt = con.prepareStatement("INSERT INTO account_ledger "
                    + "(account_id, event_type, event_id, holding_type, holding_id, change, balance, "
                    + "block_id, height, timestamp) "
                    + "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                int i=0;
                stmt.setLong(++i, accountId);
                stmt.setByte(++i, (byte) event.getCode());
                stmt.setLong(++i, eventId);
                if (holding != null) {
                    stmt.setByte(++i, (byte)holding.getCode());
                } else {
                    stmt.setByte(++i, (byte)-1);
                }
                DbUtils.setLong(stmt, ++i, holdingId);
                stmt.setLong(++i, change);
                stmt.setLong(++i, balance);
                stmt.setLong(++i, blockId);
                stmt.setInt(++i, height);
                stmt.setInt(++i, timestamp);
                stmt.executeUpdate();
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        ledgerId = rs.getLong(1);
                    }
                }
            }
        }
    }
}