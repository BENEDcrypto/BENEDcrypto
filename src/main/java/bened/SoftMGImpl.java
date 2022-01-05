/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package bened;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import bened.util.Logger;
import bened.util.BoostMap;


public class SoftMGImpl implements SoftMG {

    public static final long MAXIMUM_softMG_AMOUNT = Constants.MAX_full_BALANCE_centesimo*(-1);

    public static final int CACHE_SIZE = 820;
    public static final int CACHE_DEEP = 1450;

    public static final String LAST_1440_BLOCK = "last_1440_block",
            FAST_ROLLBACK_ENABLED_HEIGHT = "fast_rollback_enabled_height",
            SoftMGBASE_FAST_ROLLBACK_UPDATE_HEIGHT = "softMGbase_fast_rollback_update_height",
            MIN_FAST_ROLLBACK_HEIGHT = "min_fast_rollback_height",
            HOLD_UPDATE_HEIGHT = "hold_update_height",
            ZEROBLOCK_FIXED = "zeroblock_fixed",
            HOLD_INTEGRITY_VALIDATED = "hold_validated";

   

    public static final String ERROR_DATABASE_CLOSED = "Database closed!";
    public static final String ERROR_CANT_COMMIT = "Can't commit transaction!";
    public static final String ERROR_CANT_INITIALIZE = "Can't initialize database!";
    public static final String ERROR_DRIVER_NOT_FOUND = "H2 Driver not found!";
    public static final String ERROR_CANT_CONNECT = "Can't connect to database!";
    public static final String ERROR_ALREADY = "Key already exists!";
    public static final String ERROR_ERROR = "Unknown core error!";
    public static final String ERROR_INVALID_TRANSACTION = "Invalid transaction!";
    public static final String ERROR_CANT_UPDATE_PARAMETER = "Can't update parameter!";
    public static final String ERROR_CANT_GET_BLOCK_FROM_BLOCKCHAIN = "Can't get block from BlockChain!";
    private static boolean useOnlyNewRollbackAlgo = false, zeroblockFixed = false;

    static boolean show = false;
    private static void log(boolean good, String message) {
        
        if (good && !show) {
            return;
        }
        if (good) {
            Logger.logInfoMessage(message);
        } else {
            Logger.logErrorMessage(message);
        }
    }

    private Connection conn = null;

    private String JDBC = null;
    private String login = null;
    private String password = null;
    private long blockHeight;

    private BoostMap<Long, Boolean> networkBooster = new BoostMap<>(8192, -1);
    private BoostMap<Long, Boolean> networkBooster1440 = new BoostMap<>(8192, -1);


    private boolean initialized = false;

    @Override
    public void init() {
        preinit();
    }

    private void preinit() {
        synchronized (LOCK_OBJECT) { 
            if (initialized) {
                return;
            }
            initialized = true;
            initDB();
            log(true, "DATABASE INITIALIZED");
            commit(false);
        }
    }

    public SoftMGImpl(String JDBC, String login, String password) {

        this.JDBC = JDBC;
        this.login = login;
        this.password = password;
    }

    private void update(String SQL) throws SQLException {
        PreparedStatement pre = conn.prepareStatement(SQL);
        pre.executeUpdate();
        pre.close();
    }

    private void initDBcreateIndices() throws SQLException {
        update("alter table force add foreign key (block_id) references block(id);");
        update("create unique index soft_pk on soft(id);");
        update("alter table soft add foreign key (parent_id) references soft(id);");
        update("create unique index block_pk on block(id);");
        update("create unique index block_height on block(height);");
        update("create unique index force_master on force(txid, to_id);");
        update("create index force_height on force(height);");
        update("create unique index force_stxid on force(stxid);");
        update("create index force_tech on force(tech);");
        update("create index hold_transfer_account_id on hold_transfer(height desc)");
    }

    private void initDBcreateIndicesFinalFix() throws SQLException {
        update("alter table soft alter column id bigint not null");
        update("alter table soft alter column amount bigint not null default 0");
        update("alter table soft alter column balance bigint not null default 0");
        update("alter table soft alter column last int not null");

        update("alter table block alter column id bigint not null");
        update("alter table block alter column height int not null");
        update("alter table block alter column fee bigint not null default 0");
        update("alter table block alter column stamp int not null default 0");
        update("alter table block alter column accepted boolean not null default false");

        update("alter table force alter column block_id bigint not null");
        update("alter table force alter column amount bigint not null");
        update("alter table force alter column to_id bigint not null");
        update("alter table force alter column announced boolean not null default false");
        update("alter table force alter column height int not null");
    }

    private void initDB() {
        try {
            Class.forName("org.h2.Driver");
            long maxCacheSize = Bened.getIntProperty("bened.dbCacheKB");
            if (maxCacheSize == 0) {
                maxCacheSize = Math.min(256, Math.max(16, (Runtime.getRuntime().maxMemory() / (1024 * 1024) - 128)/2)) * 1024;
            }
            JDBC += ";CACHE_SIZE=" + maxCacheSize;
            
            this.conn = DriverManager.getConnection(JDBC, login, password);
            this.conn.setAutoCommit(false);
            update("begin work;");

            PreparedStatement pre = conn.prepareStatement("select * from soft where id=-1");
            ResultSet rs = pre.executeQuery();
            rs.close();
            pre.close();

            final int height = BlockchainImpl.getInstance().getHeight();
            final Integer newRollbackHeight = getParameter(FAST_ROLLBACK_ENABLED_HEIGHT);
            if (height == 0 || (newRollbackHeight != null && newRollbackHeight <= height)) {
                log(true, height + " - Using new rollback algorithm as default " + ((newRollbackHeight != null && newRollbackHeight == 0) ? "from Genesis block" : "from transfer block " + newRollbackHeight));
                useOnlyNewRollbackAlgo = true;
            }

            try {
                pre = conn.prepareStatement("select last from force where block_id=-1");
                rs = pre.executeQuery();
                rs.close();
                pre.close();
            } catch (SQLException ex) {
                if (ex.toString().contains("LAST") || ex.toString().contains("ACTIVATION")) {
                    if (height > 0) {
                        log(true, "Updating softMGbase...");
                    }
                    update("alter table force add column last int;");
                   update("alter table force add column tech boolean not null default false;");
                    update("create index force_tech on force(tech);");
                    update("alter table force_1440 add column tech boolean not null default false;");
                    update("create index force_1440_tech on force_1440(tech);");
                    update("create table activation (soft_id bigint primary key, height int not null)");
                    update("alter table activation add foreign key (soft_id) references soft(id) on delete cascade;");
                    setParameter(SoftMGBASE_FAST_ROLLBACK_UPDATE_HEIGHT, height);
                    setParameter(MIN_FAST_ROLLBACK_HEIGHT, height + 100);
                    setParameter(FAST_ROLLBACK_ENABLED_HEIGHT, height + CACHE_SIZE);
                    if (height > 0) {
                        log(true, "SoftMGbase update completed! Fast rollback will be enabled at " + (height + CACHE_SIZE));
                    }
                    commit(false);
                } else {
                    ex.printStackTrace();
                }
            } finally {
                try {
                    if (rs != null) {
                        rs.close();
                    }
                    if (pre != null) {
                        pre.close();
                    }
                } catch (SQLException consumed) {
                }
            }
            try {
                pre = conn.prepareStatement("select hold from soft limit 1");
                rs = pre.executeQuery();
                rs.close();
                pre.close();
            } catch (SQLException ex) {
                if (ex.toString().contains("HOLD")) {
                    log(true, "Database update started, please wait");
                    log(true, "Altering tables...");
                    update("alter table soft add column last_forged_block_height int not null default 0;");
                    update("alter table soft add column hold bigint not null default 0 after balance;");
                    update("create table hold_transfer (id bigint not null, amount bigint not null, height int not null);");
                    update("create index hold_transfer_account_id on hold_transfer(height desc)");
                    setParameter(HOLD_UPDATE_HEIGHT, height);
                    int startHeight = Constants.HOLD_ENABLE_HEIGHT - Constants.HOLD_RANGE;
                    log(true, "Rescanning last blocks from height " + startHeight + "...");
                    if (height > startHeight || height == 0) {
                        Map<Long, Integer> blockCreators = new HashMap<>();
                        PreparedStatement ps = conn.prepareStatement("select creator_id,height from block where height>=?");
                        ps.setInt(1, startHeight);
                        ResultSet scanRs = ps.executeQuery();
                        while (scanRs.next()) {
                            long creatorId = scanRs.getLong(1);
                            int forgedHeight = scanRs.getInt(2);
                            if (!blockCreators.containsKey(creatorId)) {
                                blockCreators.put(creatorId, forgedHeight);
                            } else if (blockCreators.get(creatorId) < forgedHeight) {
                                blockCreators.put(creatorId, forgedHeight);
                            }
                        }
                        scanRs.close();
                        ps.close();
                        log(true, "Updating " + blockCreators.size() + " forging accounts...");
                        for (Long account : blockCreators.keySet()) {
                            setLastForgedBlockHeight(account, blockCreators.get(account));
                        }
                    }
                    log(true, "SoftMGbase update completed!");
                    commit(false);
                } else {
                    ex.printStackTrace();
                }
            } finally {
                try {
                    if (rs != null) {
                        rs.close();
                    }
                    if (pre != null) {
                        pre.close();
                    }
                } catch (SQLException consumed) {
                }
            }

            long badHoldsCounts = 0L;
            if (getParameter(HOLD_INTEGRITY_VALIDATED) == null) { 
                try {
                    log(true, "Starting database validation");
                    pre = conn.prepareStatement("select count(hold) from soft where hold<0");
                    rs = pre.executeQuery();
                    while (rs.next()) {
                        badHoldsCounts = rs.getLong(1);
                    }
                    rs.close();
                    pre.close();

                } catch (SQLException ex) {
                    ex.printStackTrace();
                } finally {
                    try {
                        if (rs != null) {
                            rs.close();
                        }
                        if (pre != null) {
                            pre.close();
                        }
                    } catch (SQLException consumed) {
                    }
                }
                if (badHoldsCounts <= 0) {
                    log(true, "Database validation: OK");
                    setParameter(HOLD_INTEGRITY_VALIDATED, height);
                } else {
                    for (int i = 0; i < 5; i++) {
                        log(true, "Database validation: ERROR - INTEGRITY COMPROMISED");
                        log(true, "CRITICAL ERROR - Blockchain integrity validation failed");
                        log(true, "Database validation: ERROR - DAMAGED BLOCKCHAIN");
                        log(true, "CRITICAL ERROR - Re-sync from scratch is required to continue");
                    }
                    log(true, "Database validation failed, detected " + badHoldsCounts + " unrecoverable error(s)");
                    log(true, "BenedCore is going to shutdown because blockchain data is corrupted");
                    log(true, "Please, delete the \"bened_db\" directory before restart");
                    System.exit(1);
                }
            } else {
                log(true, "Bypassing database validation (already validated earlier)");
            }

        } catch (SQLException ex) {
            if (ex.toString().contains("SOFT")) {
                try {
                    log(true, "Initialize database...");
                    update("create table lock (id bigint not null default -1);");
                    update("insert into lock(id) values (-1);");

                    PreparedStatement blockStatement = conn.prepareStatement("select id from lock for update");
                    ResultSet rs = blockStatement.executeQuery();
                    while (rs.next()) {
                        blockHeight = rs.getLong(1);
                    }
                    rs.close();
                    blockStatement.close();

                  
                    update("create table soft (id bigint not null, parent_id bigint, amount bigint not null default 0, balance bigint not null default 0, hold bigint not null default 0, last int not null, last_forged_block_height int not null default 0)");
                    update("create table block (id bigint not null, height int not null, fee bigint not null default 0, stamp int not null default 0, accepted boolean not null default false, creator_id long not null);");
                    update("create table force (block_id bigint not null, txid bigint, amount bigint not null, to_id bigint not null, announced boolean not null default false, stxid bigint, height int not null, last int, tech boolean not null default false);");
                    update("create table activation (soft_id bigint primary key, height int not null)");
                    update("create table hold_transfer (id bigint not null, amount bigint not null, height int not null);");
                    update("alter table activation add foreign key (soft_id) references soft(id) on delete cascade;");

                    initDBcreateIndices();

                    update("create table parameters (key varchar(80) primary key, value varchar);");

                    setParameter(SoftMGBASE_FAST_ROLLBACK_UPDATE_HEIGHT, 0);
                    setParameter(MIN_FAST_ROLLBACK_HEIGHT, 0);
                    setParameter(FAST_ROLLBACK_ENABLED_HEIGHT, 0);
                    setParameter(HOLD_UPDATE_HEIGHT, 0);
                    setParameter(ZEROBLOCK_FIXED, 0);
                    useOnlyNewRollbackAlgo = true;
                    log(true, "Using new rollback algorithm from Genesis block");

                    update("commit work;");
                    commit(false);
                    log(true, "Success!");
                } catch (SQLException exSQL) {
                    log(false, ERROR_CANT_INITIALIZE);
                }
            } else {
                ex.printStackTrace();
            }
        } catch (ClassNotFoundException ex) {
            log(false, ERROR_ERROR);
        }
    }

    private SMGBlock getBlockFromBlockchainWithNoTransactions(int height) {
       BlockImpl block;
        try {
            block = BlockchainImpl.getInstance().getBlockAtHeight(height);
        } catch (RuntimeException ex) {
            block = null;
        }
        if (block == null) {
            return null;
        }
        SMGBlock softBlock = new SMGBlock();
        softBlock.setID(block.getId());
        softBlock.setGeneratorID(block.getGeneratorId());
        softBlock.setFee(block.getTotalFeeNQT());
        softBlock.setHeight(block.getHeight());
        softBlock.setStamp(block.getTimestamp());

        if (block.getTransactions() != null) {
            for (TransactionImpl blockTransaction : block.getTransactions()) {
                try {
                    SMGBlock.Transaction trx = SoftMGImpl.convert(blockTransaction, block.getHeight());
                    softBlock.getTransactions().add(trx);
                } catch (HGException ex) {
                }
            }
        }
        if (softBlock.getTransactions().isEmpty()) {
            softBlock.setNoTransactions(true);
        }
        return softBlock;
    }

    private void addDiff(long amount, long account, Map<Long, Long> diffs) {
        if (diffs.containsKey(account)) {
            diffs.put(account, diffs.get(account) + amount);
        } else {
            diffs.put(account, amount);
        }
    }

    private void addDiff(long account, long amount, Integer stamp, Map<Long, Long> diffs, Map<Long, Integer> stamps) {
        addDiff(amount, account, diffs);
        if (!stamps.containsKey(account) || (stamps.containsKey(account) && stamp != null)) {
            stamps.put(account, stamp);
        }
    }

    private Integer getParameter(String key) {
        Integer value = null;
        try {
            PreparedStatement ps = conn.prepareStatement("select value from parameters where key=?");
            ps.setString(1, key);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                value = rs.getInt(1);
            }
            rs.close();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
        if (value == null || value == -1) {
            value = null;
        }
        if (value == null) {
            log(false, "getParameter: Parameter \"" + key + "\" is null!");
        }
        return value;
    }

    private void setParameter(String key, int value) {
        try {
            PreparedStatement ps = conn.prepareStatement("merge into parameters values(?,?)");
            ps.setString(1, key);
            ps.setInt(2, value);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        log(true, "($) Set parameter \"" + key + "\" = \"" + value + "\"");
    }

    @Override
    public void popLastBlock() {
        final Block lastBlock = BlockchainImpl.getInstance().getLastBlock();
        final int currentHeight = BlockchainImpl.getInstance().getHeight();
        if (!useOnlyNewRollbackAlgo) {
            return;
        }
        networkBooster.clear();
        preinit();
        boolean holdEnabled = currentHeight >= Constants.HOLD_ENABLE_HEIGHT;
        boolean shouldSetLastForgedBlockHeight = currentHeight >= Constants.HOLD_ENABLE_HEIGHT - Constants.HOLD_RANGE;
        synchronized (LOCK_OBJECT) {
            final List<Long> accountsToDelete = new ArrayList<>();
            final TreeMap<Long, Long> diffs = new TreeMap<>();
            final Set<Long> senders = new HashSet<>();
            List<Long> revertedsoftMGTransactions = new ArrayList<>();
            try {
                            
                if (lastBlock.getTransactions() != null && lastBlock.getTransactions().size() > 0) {
                    for (Transaction t : lastBlock.getTransactions()) {
                        senders.add(t.getSenderId());
                        final boolean hasRecipient = t.getRecipientId() != 0L;
                        final boolean issoftMG = hasRecipient && t.getSenderId() == Genesis.CREATOR_ID;
                        
                        if (issoftMG) {
                            revertedsoftMGTransactions.add(t.getId());
                            continue;
                        }
                        final long senderDiff = t.getAmountNQT() + t.getFeeNQT();
                        final long recipientDiff = hasRecipient ? 0L - t.getAmountNQT() : 0L;
                        addDiff(senderDiff, t.getSenderId(), diffs);
                        if (hasRecipient) {
                            addDiff(recipientDiff, t.getRecipientId(), diffs);
                        }
                    }
                }
                if (lastBlock.getTotalFeeNQT() > 0L) {
                    addDiff(0L - lastBlock.getTotalFeeNQT(), lastBlock.getGeneratorId(), diffs);
                }

                List<SMGBlock.Payout> forces = new ArrayList<>();
                PreparedStatement request = conn.prepareStatement("select to_id,amount,height,last from force where height=?");
                request.setLong(1, currentHeight);
                ResultSet rs = request.executeQuery();
                while (rs.next()) {
                    SMGBlock.Payout force = new SMGBlock.Payout();
                    force.setToID(rs.getLong(1));
                    force.setAmount(rs.getLong(2));
                    force.setHeight(rs.getInt(3));
                    force.setLast(rs.getInt(4));
                    forces.add(force);
                }
                rs.close();
                request.close();

                if (shouldSetLastForgedBlockHeight) {
                    int lastForgedBlockHeight = 0;
                    request = conn.prepareStatement("select max(height) from block where creator_id=? and height<?");
                    request.setLong(1, lastBlock.getGeneratorId());
                    request.setInt(2, currentHeight);
                    rs = request.executeQuery();
                    while (rs.next()) {
                        lastForgedBlockHeight = rs.getInt(1);
                    }
                    rs.close();
                    request.close();

                    request = conn.prepareStatement("update soft set last_forged_block_height=? where id=?");
                    request.setInt(1, lastForgedBlockHeight);
                    request.setLong(2, lastBlock.getGeneratorId());
                    request.executeUpdate();
                    request.close();
                }

                Map<Long, Long> holdTransfers = new HashMap<>();
                if (holdEnabled) {
                    request = conn.prepareStatement("select id,amount from hold_transfer where height=?");
                    request.setInt(1, currentHeight);
                    rs = request.executeQuery();
                    while (rs.next()) {
                        holdTransfers.put(rs.getLong(1), rs.getLong(2));
                    }
                    rs.close();
                    request.close();

                  
                    request = conn.prepareStatement("delete from hold_transfer where height=?");
                    request.setInt(1, currentHeight);
                    request.executeUpdate();
                    request.close();

                   
                    for (Long account : holdTransfers.keySet()) {
                        if (account == null) {
                            continue;
                        }
                        addDiff(-holdTransfers.get(account), account, diffs);
                        request = conn.prepareStatement("update soft set hold=? where id=?");
                        request.setLong(1, holdTransfers.get(account));
                        request.setLong(2, account);
                        request.executeUpdate();
                        request.close();
                    }
                }

                Set<Integer> blockHeights = new HashSet<>();

                // REVERT 'LAST' PARAMETERS AND DELETE FORCES
                int count = 0;
                if (forces.size() > 0) {
                    for (SMGBlock.Payout force : forces) {
                        request = conn.prepareStatement("update soft set last=? where id=?");
                        request.setLong(1, force.getLast());
                        request.setLong(2, force.getToID());
                        request.executeUpdate();
                        request.close();
                        count++;
                        addDiff(0L - force.getAmount(), force.getToID(), diffs);
                        addDiff(force.getAmount(), Genesis.CREATOR_ID, diffs);
                    }
                    PreparedStatement trimmer = conn.prepareStatement("delete from force where height>=?");
                    trimmer.setInt(1, currentHeight);
                    count = trimmer.executeUpdate();
                    trimmer.close();
                }

      
                if (revertedsoftMGTransactions.size() > 0) {
                    count = 0;
                    for (Long stxid : revertedsoftMGTransactions) {
                        count++;
                        request = conn.prepareStatement("select height from force where stxid=?");
                        request.setLong(1, stxid);
                        rs = request.executeQuery();
                        while (rs.next()) {
                            Integer height = rs.getInt(1);
                            if (height != null && height > 0) {
                                blockHeights.add(height);
                            }
                        }
                        rs.close();
                        request.close();
                        request = conn.prepareStatement("update force set stxid=? where stxid=?");
                        request.setNull(1, Types.BIGINT);
                        request.setLong(2, stxid);
                        request.executeUpdate();
                        request.close();
                    }

                }

        
                if (blockHeights.size() > 0) {
                    for (Integer notAcceptedHeight : blockHeights) {
                        request = conn.prepareStatement("update block set accepted=false where height=? and accepted=true");
                        request.setInt(1, notAcceptedHeight);
                        request.executeUpdate();
                        request.close();
                    }
                }

       
                count = 0;
                request = conn.prepareStatement("delete from block where height>?");
                request.setInt(1, currentHeight - 1);
                count = request.executeUpdate();
                request.close();
                if (count != 1) {
                    if (count < 1) {
                        log(false, "popLastBlock() - No blocks deleted (must be 1) at " + currentHeight);
                    }
                    if (count > 1) {
                        log(false, "popLastBlock() - Too many blocks deleted: " + count + " (must be 1) at " + currentHeight);
                    }
                }

                String msg = currentHeight + " <- this block is popped\n\tDiffs: [" + diffs.size() + "]";

                if (diffs.size() > 0) {
                    if (holdEnabled) {
                        for (Long accountId : diffs.keySet()) {
                            int height = 0;
                            long balance = 0l;
                            long hold = 0l;
                            request = conn.prepareStatement("select balance,last_forged_block_height,hold from soft where id=?");
                            request.setLong(1, accountId);
                            rs = request.executeQuery();
                            while (rs.next()) {
                                balance = rs.getLong(1);
                                height = rs.getInt(2);
                                hold = rs.getLong(3);
                            }
                            rs.close();
                            request.close();
                            long balanceBeforeBlock = balance + diffs.get(accountId);
                            boolean isEnterHoldFromLowerBalance = hold == 0L && diffs.get(accountId) < 0 && balanceBeforeBlock < Constants.HOLD_BALANCE_MIN;
                            boolean isOnHold = height >= currentHeight - Constants.HOLD_RANGE
                                    && balance >= Constants.HOLD_BALANCE_MIN
                                    && balance <= Constants.HOLD_BALANCE_MAX;
                            if (isOnHold
                                    && (!senders.contains(accountId))
                                    && (!isEnterHoldFromLowerBalance)) {
                                updateHold(accountId, diffs.get(accountId));
                            } else {
                                update(accountId, diffs.get(accountId), null);
                            }
                        }
                    } else {
                        for (Long accountId : diffs.keySet()) {
                            msg = msg + ", " + accountId + " " + diffs.get(accountId);
                            update(accountId, diffs.get(accountId), null);
                        }
                    }
                }

          
                request = conn.prepareStatement("select soft_id from activation where height=?");
                request.setInt(1, currentHeight);
                rs = request.executeQuery();
                while (rs.next()) {
                    accountsToDelete.add(rs.getLong(1));
                }
                rs.close();
                request.close();

                count = 0;
                msg = "\tDeleted accounts: [" + accountsToDelete.size() + "]";
                if (accountsToDelete.size() > 0) {
                    for (Long id : accountsToDelete) {
                        msg = msg + ", " + id;
                        request = conn.prepareStatement("delete from soft where id=?");
                        request.setLong(1, id);
                        count = count + request.executeUpdate();
                        request.close();
                    }
                }

                commit(false);
            } catch (Exception e) {
                // TODO
                rollback();
                log(false, "CRITICAL - FAILED TO POP LAST BLOCK BECAUSE OF \"" + e.getMessage() + "\"");
                e.printStackTrace();
            }
        }
    }

    @Override
    public void rollbackToBlock(int blockHeight) {
        networkBooster.clear();
    }


    private void dropOldDatabases(int height) throws SQLException {
        PreparedStatement statement = conn.prepareStatement("drop table soft_1440");
        statement.executeUpdate();
        statement.close();
        statement = conn.prepareStatement("drop table force_1440");
        statement.executeUpdate();
        statement.close();

        statement = conn.prepareStatement("drop table block_1440");
        statement.executeUpdate();
        statement.close();
        commit(false);
        log(true, "trimDerivedTables: Old database deleted at " + height);
    }

private void trimDerivedTables() throws SQLException {
        final int height = getParamLast();
        PreparedStatement statement = null;
        if (height % CACHE_SIZE != 0 || !useOnlyNewRollbackAlgo)
            return;
        final Integer minFastRollbackHeight = getParameter(MIN_FAST_ROLLBACK_HEIGHT);
        if (minFastRollbackHeight == null) {
            return;
        }
        if (height - minFastRollbackHeight < CACHE_SIZE) {
            int nextTrimHeight = minFastRollbackHeight + CACHE_SIZE;
            nextTrimHeight = ((nextTrimHeight / CACHE_SIZE) + 1) * CACHE_SIZE;
            log(true, "trimDerivedTables: Postponed trimming for " + (nextTrimHeight-height) + " more blocks");
            return;
        }
        final int newMinRollbackHeight = height - CACHE_SIZE;
        int forces = 0, activations = 0, holdTransfers = 0;
        statement = conn.prepareStatement("delete from force where height<? and ((stxid is not null) or (stxid is null and tech)) limit " + Constants.BATCH_COMMIT_SIZE);
        statement.setInt(1, newMinRollbackHeight);
        int deleted;
        do {
            deleted = statement.executeUpdate();
            forces += deleted;
            commit(false);
        } while (deleted >= Constants.BATCH_COMMIT_SIZE);
        statement.close();
        statement = conn.prepareStatement("delete from activation where height<? limit " + Constants.BATCH_COMMIT_SIZE);
        statement.setInt(1, newMinRollbackHeight);
        do {
            deleted = statement.executeUpdate();
            activations += deleted;
            commit(false);
        } while (deleted >= Constants.BATCH_COMMIT_SIZE);
        statement.close();
        statement = conn.prepareStatement("delete from hold_transfer where height<? limit " + Constants.BATCH_COMMIT_SIZE);
        statement.setInt(1, newMinRollbackHeight);
        do {
            deleted = statement.executeUpdate();
            holdTransfers += deleted;
            commit(false);
        } while (deleted >= Constants.BATCH_COMMIT_SIZE);
        statement.close();
        setParameter(MIN_FAST_ROLLBACK_HEIGHT, newMinRollbackHeight);
        commit(false);
        log(true, "trimDerivedTables: Trimmed " + forces + " payouts, " + activations + " activations and " + holdTransfers + " hold transfers at " + height);
    }
    
       

    private boolean rewriteWorkingDatabase(int currentBlock) throws SQLException {
        if (useOnlyNewRollbackAlgo) {
            log(false, currentBlock + " - rewriteWorkingDatabase: invoked while the new rollback system is enabled. This should never happen.");
            return true;
        } else {
            log(true, currentBlock + " - Using legacy rollback algorithm. Will switch to the new one at height " + getParameter(FAST_ROLLBACK_ENABLED_HEIGHT));
        }
        boolean allRight = true;
        update("DROP TABLE IF EXISTS soft;");
        update("DROP TABLE IF EXISTS force;");
        update("DROP TABLE IF EXISTS block;");

        update("CREATE TABLE soft AS SELECT * FROM soft_1440;");
        update("CREATE TABLE force AS SELECT * FROM force_1440;");
        update("CREATE TABLE block AS SELECT * FROM block_1440;");
        initDBcreateIndices();
        initDBcreateIndicesFinalFix();
        commit(false);
        int firstBlock = getParamLast() + 1;
        if (firstBlock >= currentBlock) {
            return allRight;
        }

        PreparedStatement activations = conn.prepareStatement("delete from activations where height>=?");
        activations.setInt(1, firstBlock);
        activations.executeUpdate();
        activations.close();

        for (int i = firstBlock; i < currentBlock; i++) {
            try {
                SMGBlock softBlock = getBlockFromBlockchainWithNoTransactions(i);
                if (softBlock != null) {
                    if (softBlock.hasNoTransactions()) {
                        insertBlock(softBlock.getID(), softBlock.getHeight(), 0, softBlock.getStamp(), softBlock.getGeneratorID(), true);
                        commit(false);
                    } else {
                        checkInternal(softBlock, true);
                    }
                }
            } catch (HGException ex) {
                allRight = false;
            }
        }
        return allRight;
    }

    private int getParamLast() throws SQLException {
        preinit();
        int retval = -1;
        PreparedStatement request = conn.prepareStatement("select max(height) from block");
        ResultSet rs = request.executeQuery();
        if (rs == null) {
            throw new SQLException(ERROR_CANT_UPDATE_PARAMETER + " [select]");
        }
        while (rs.next()) {
            if (rs.getString(1) != null) {
                retval = rs.getInt(1);
            }
        }
        rs.close();
        request.close();
        if (retval < 0) {
            throw new SQLException(ERROR_CANT_UPDATE_PARAMETER + " [<0]");
        }
        return retval;
    }

    private int getParamLast1440() throws SQLException {
        preinit();
        int retval = -1;
        PreparedStatement request = conn.prepareStatement("select max(height) from block_1440");
        ResultSet rs = request.executeQuery();
        if (rs == null) {
            throw new SQLException(ERROR_CANT_UPDATE_PARAMETER + " [select]");
        }
        while (rs.next()) {
            if (rs.getString(1) != null) {
                retval = rs.getInt(1);
            }
        }
        rs.close();
        request.close();
        if (retval < 0) {
            throw new SQLException(ERROR_CANT_UPDATE_PARAMETER + " [<0]");
        }
        return retval;
    }

    private void commit(boolean isShutdown) {
        if (conn == null) {
            return;
        }
        try {
            update("commit work;");
            conn.commit();
            update("begin work;");
        } catch (SQLException ex) {
        }
    }

    private void rollback() {
        if (conn == null) {
           return;
        }
        try {
            update("rollback;");
            conn.rollback();
            update("begin work;");
        } catch (SQLException ex) {
        }
    }

    private static void updateFix(Connection conn, Conc conc, long amount) throws SQLException {
        PreparedStatement updater = conn.prepareStatement(conc.query());
        updater.setLong(1, amount);
        updater.executeUpdate();
        updater.close();
    }

    private void createAccount(long accountID, Long senderID, int stamp, int height) throws SQLException {
        if (senderID == null) {
            PreparedStatement statement = conn.prepareStatement("insert into soft(id, last) values (?,?)");
            statement.setLong(1, accountID);
            statement.setInt(2, stamp);
            statement.executeUpdate();
            statement.close();
        } else {
            PreparedStatement statement = conn.prepareStatement("insert into soft(id, parent_id, last) values (?,?,?)");
            statement.setLong(1, accountID);
            statement.setLong(2, senderID);
            statement.setInt(3, stamp);
            statement.executeUpdate();
            statement.close();
        }
        PreparedStatement activation = conn.prepareStatement("insert into activation(soft_id, height) values (?,?)");
        activation.setLong(1, accountID);
        activation.setInt(2, height);
        activation.executeUpdate();
        activation.close();
    }

    private void createAccount1440(long accountID, Long senderID, int stamp) throws SQLException {
        if (senderID == null) {
            PreparedStatement statement = conn.prepareStatement("insert into soft_1440(id, last) values (?,?)");
            statement.setLong(1, accountID);
            statement.setInt(2, stamp);
            statement.executeUpdate();
            statement.close();
        } else {
            PreparedStatement statement = conn.prepareStatement("insert into soft_1440(id, parent_id, last) values (?,?,?)");
            statement.setLong(1, accountID);
            statement.setLong(2, senderID);
            statement.setInt(3, stamp);
            statement.executeUpdate();
            statement.close();
        }
    }

    private void createNetwork(long receiverID, long senderID, int stamp, int height) throws SQLException {
        Long receiverIDObj = new Long(receiverID);
        if (networkBooster.containsKey(receiverIDObj)) {
            return;
        }
        boolean receiver = false;
        ResultSet rs = null;
        PreparedStatement statement = conn.prepareStatement("select id from soft where id=?");
        statement.setLong(1, receiverID);
        rs = statement.executeQuery();
        while (rs.next()) {
            receiver = true;
        }
        rs.close();
        statement.close();
        if (stamp == 0) { 
            Long senderIDObj = new Long(senderID);
            if (!networkBooster.containsKey(senderIDObj)) {
                createAccount(senderID, null, stamp, 0);
                networkBooster.put(senderIDObj, true);
            }
        }
        if (!receiver) {
            createAccount(receiverID, senderID, stamp, height);
        }
        networkBooster.put(receiverIDObj, true);

    }

    private void createNetwork1440(long receiverID, long senderID, int stamp) throws SQLException {
        Long receiverIDObj = new Long(receiverID);
        if (networkBooster1440.containsKey(receiverIDObj)) {
            return;
        }
        boolean receiver = false;
        PreparedStatement statement = conn.prepareStatement("select id from soft_1440 where id=?");
        statement.setLong(1, receiverID);
        ResultSet rs = statement.executeQuery();
        while (rs.next()) {
            long accountID = rs.getLong(1);
            if (accountID == receiverID) {
                receiver = true;
            }
        }
        rs.close();
        statement.close();
        if (stamp == 0) {
            Long senderIDObj = new Long(senderID);
            if (!networkBooster1440.containsKey(senderIDObj)) {
                createAccount1440(senderID, null, stamp);
                networkBooster1440.put(senderIDObj, true);
            }
        }
        if (!receiver) {
            createAccount1440(receiverID, senderID, stamp);
        }
        networkBooster1440.put(receiverIDObj, true);
    }

    private long getGenesisEmission() throws SQLException {
        long retval = 0l;
        PreparedStatement statement = conn.prepareStatement("SELECT balance FROM SOFT where id=?");
        statement.setLong(1, Genesis.CREATOR_ID);
        ResultSet rs = statement.executeQuery();
        while (rs.next()) {
            retval = rs.getLong(1);
        }
        rs.close();
        statement.close();
        return retval;
    }
   

    private long getGenesisEmission1440() throws SQLException {
        long retval = 0l;
        PreparedStatement statement = conn.prepareStatement("SELECT balance FROM SOFT_1440 where id=?");
        statement.setLong(1, Genesis.CREATOR_ID);
        ResultSet rs = statement.executeQuery();
        while (rs.next()) {
            retval = rs.getLong(1);
        }
        rs.close();
        statement.close();
        return retval;
    }

    private SoftMGs getMetricsForAccount(long accountID, int stamp, int height, boolean holdEnabled) throws SQLException {
        SoftMGs metrics = new SoftMGs();
        metrics.setBeforeStamp(stamp);
        metrics.setAfterStamp(stamp);
        metrics.setGenesisEmission(getGenesisEmission());
        PreparedStatement statement = conn.prepareStatement("select id,parent_id,amount,balance,last,hold,last_forged_block_height from soft where id=?");
        statement.setLong(1, accountID);
        ResultSet rs = statement.executeQuery();
        while (rs.next()) {
            if (rs.getLong(1) == accountID) {
                metrics.setBeforeStamp(rs.getInt("last"));
                metrics.setBalance(rs.getLong("balance"));
                metrics.setAmount(rs.getLong("amount"));
                metrics.setAccountID(accountID);
                metrics.setHold(rs.getLong("hold"));
                metrics.setLastForgedBlockHeight(rs.getInt("last_forged_block_height"));
                metrics.calculatePyoutSet();
            }
        }
        rs.close();
        statement.close();
        return metrics;
    }

    private SoftMGs getMetricsForAccount1440(long accountID, int stamp,  int height, boolean holdEnabled) throws SQLException {
        SoftMGs metrics = new SoftMGs();

        metrics.setBeforeStamp(stamp);
        metrics.setAfterStamp(stamp);
        metrics.setGenesisEmission(getGenesisEmission());

        PreparedStatement statement = conn.prepareStatement("select id,parent_id,amount,balance,last,hold,last_forged_block_height from soft_1440 where id=?");
        statement.setLong(1, accountID);
        ResultSet rs = statement.executeQuery();
        while (rs.next()) {
            if (rs.getLong(1) == accountID) {
                metrics.setBeforeStamp(rs.getInt("last"));
                metrics.setBalance(rs.getLong("balance"));
                metrics.setAmount(rs.getLong("amount"));
                metrics.setAccountID(accountID);
                metrics.setHold(rs.getLong("hold"));
                metrics.setLastForgedBlockHeight(rs.getInt("last_forged_block_height"));
                boolean isOnHold = holdEnabled && metrics.isOnHoldAtHeight(height);
                    metrics.calculatePyoutSet();
            }
        }
        rs.close();
        statement.close();
       return metrics;
    }

    private List<SMGBlock.Payout> insertBlock(final long blockID, int height, long fee, int stamp, long creatorID, boolean withFinishedState) throws SQLException {
        boolean hasTransaction = false;
        PreparedStatement query = conn.prepareStatement("select id from block where id=? and height=?");
        query.setLong(1, blockID);
        query.setLong(2, height);
        ResultSet rs = query.executeQuery();
        while (rs.next()) {
            hasTransaction = true;
        }
        rs.close();
        query.close();

        if (hasTransaction) {
            List<SMGBlock.Payout> retval = new ArrayList<>();
            PreparedStatement request = conn.prepareStatement("select block_id,txid,amount,to_id from force where not tech and block_id=?");
            request.setLong(1, blockID);
            ResultSet reqres = request.executeQuery();
            while (reqres.next()) {
                SMGBlock.Payout payout = new SMGBlock.Payout();
                payout.setBlockID(reqres.getLong(1));
                payout.setTxID(reqres.getString(2) != null ? reqres.getLong(2) : null);
                payout.setHeight(height);
                payout.setAmount(reqres.getLong(3));
                payout.setToID(reqres.getLong(4));
                retval.add(payout);
            }
            reqres.close();
            request.close();
            return retval;
        }
        PreparedStatement statement = conn.prepareStatement("insert into block (id, height, fee, stamp, creator_id" + (withFinishedState ? ", accepted" : "") + ") values (?,?,?,?,?" + (withFinishedState ? ",true" : "") + ")");
        statement.setLong(1, blockID);
        statement.setLong(2, height);
        statement.setLong(3, fee);
        statement.setInt(4, stamp);
        statement.setLong(5, creatorID);
        int count = statement.executeUpdate();
        statement.close();
        if (count < 1) {
            throw new SQLException(ERROR_ALREADY);
        }
        boolean shouldSetLastForgedBlockHeight = height >= Constants.HOLD_ENABLE_HEIGHT - Constants.HOLD_RANGE;
        if (shouldSetLastForgedBlockHeight && withFinishedState) {
            setLastForgedBlockHeight(creatorID, height);
        }
        if (withFinishedState) {
        }
        return null;
    }

    private List<SMGBlock.Payout> insertBlock1440(final long blockID, int height, long fee, int stamp, long creatorID, boolean withFinishedState) throws SQLException {
        boolean hasTransaction = false;
       PreparedStatement query = conn.prepareStatement("select id from block_1440 where id=? and height=?");
        query.setLong(1, blockID);
        query.setLong(2, height);
        ResultSet rs = query.executeQuery();
        while (rs.next()) {
            hasTransaction = true;
        }
        rs.close();
        query.close();
        if (hasTransaction) {
            List<SMGBlock.Payout> retval = new ArrayList<>();
            PreparedStatement request = conn.prepareStatement("select block_id,txid,amount,to_id from force_1440 where not tech and block_id=?");
            request.setLong(1, blockID);
            ResultSet reqres = request.executeQuery();
            while (reqres.next()) {
                SMGBlock.Payout payout = new SMGBlock.Payout();
                payout.setBlockID(reqres.getLong(1));
                payout.setTxID(reqres.getString(2) != null ? reqres.getLong(2) : null);
                payout.setHeight(height);
                payout.setAmount(reqres.getLong(3));
                payout.setToID(reqres.getLong(4));
               retval.add(payout);
            }
            reqres.close();
            request.close();
            return retval;
        }

        PreparedStatement statement = conn.prepareStatement("insert into block_1440 (id, height, fee, stamp, creator_id" + (withFinishedState ? ", accepted" : "") + ") values (?,?,?,?,?" + (withFinishedState ? ",true" : "") + ")");
        statement.setLong(1, blockID);
        statement.setLong(2, height);
        statement.setLong(3, fee);
        statement.setInt(4, stamp);
        statement.setLong(5, creatorID);
        int count = statement.executeUpdate();
        statement.close();
        if (count < 1) {
            throw new SQLException(ERROR_ALREADY);
        }
        return null;
    }

    private List<SMGBlock.Payout> getUnpayedSoftMGTransactions(int height, int limit) throws SQLException {
        boolean hasTransaction = false;
        TreeMap<Long, Integer> blocksForSelect = new TreeMap<>();

        PreparedStatement query;
        if (height % 10000 == 0) {
            query = conn.prepareStatement("select id,height from block where height<=? and accepted=false");
        } else {
            query = conn.prepareStatement("select id,height from block where height<=? and height>=? and accepted=false");
            query.setLong(2, height - CACHE_DEEP);
        }
        query.setLong(1, height - 10);
        ResultSet rs = query.executeQuery();
        while (rs.next()) {
            long currentID = rs.getLong(1);
            if (!blocksForSelect.containsKey(currentID)) {
                blocksForSelect.put(rs.getLong(1), rs.getInt(2));
            }
            if (!hasTransaction) {
                hasTransaction = true;
            }
        }
        rs.close();
        query.close();
        if (hasTransaction) {
            List<SMGBlock.Payout> retval = new ArrayList<>();
            for (Entry<Long, Integer> block : blocksForSelect.entrySet()) {
                PreparedStatement request = conn.prepareStatement("select block_id,txid,amount,to_id from force where not tech and block_id=? and stxid is null");
                request.setLong(1, block.getKey());
                ResultSet reqres = request.executeQuery();
                while (reqres.next()) {
                    SMGBlock.Payout payout = new SMGBlock.Payout();
                    payout.setBlockID(reqres.getLong(1));
                    payout.setTxID(reqres.getString(2) != null ? reqres.getLong(2) : null);
                    payout.setHeight(block.getValue());
                    payout.setAmount(reqres.getLong(3));
                    payout.setToID(reqres.getLong(4));
                    retval.add(payout);
                }
                reqres.close();
                request.close();
            }
            if (retval.size() > limit) {
                List<SMGBlock.Payout> retvalLimited = new ArrayList<>();
                retvalLimited.addAll(retval.subList(0, limit));
                return retvalLimited;
            }
            return retval;
        }
        return new ArrayList<>();
    }

    private List<SMGBlock.Payout> getUnpayedSoftTransactions1440(int height, int limit) throws SQLException {
        boolean hasTransaction = false;
        TreeMap<Long, Integer> blocksForSelect = new TreeMap<>();
        PreparedStatement query;
        if (height % 10000 == 0) {
            query = conn.prepareStatement("select id,height from block_1440 where height<=? and accepted=false");
        } else {
            query = conn.prepareStatement("select id,height from block_1440 where height<=? and height>=? and accepted=false");
            query.setLong(2, height - CACHE_DEEP);
        }
        query.setLong(1, height - 10);
        ResultSet rs = query.executeQuery();
        while (rs.next()) {
            long currentID = rs.getLong(1);
            if (!blocksForSelect.containsKey(currentID)) {
                blocksForSelect.put(rs.getLong(1), rs.getInt(2));
            }
            if (!hasTransaction) {
                hasTransaction = true;
            }
        }
        rs.close();
        query.close();
        if (hasTransaction) {
            List<SMGBlock.Payout> retval = new ArrayList<>();
            for (Entry<Long, Integer> block : blocksForSelect.entrySet()) {
                PreparedStatement request = conn.prepareStatement("select block_id,txid,amount,to_id from force_1440 where not tech and block_id=? and stxid is null");
                request.setLong(1, block.getKey());
                ResultSet reqres = request.executeQuery();
                while (reqres.next()) {
                    SMGBlock.Payout payout = new SMGBlock.Payout();
                    payout.setBlockID(reqres.getLong(1));
                    payout.setTxID(reqres.getString(2) != null ? reqres.getLong(2) : null);
                    payout.setHeight(block.getValue());
                    payout.setAmount(reqres.getLong(3));
                    payout.setToID(reqres.getLong(4));
                    retval.add(payout);
                }
                reqres.close();
                request.close();
            }
            if (retval.size() > limit) {
                List<SMGBlock.Payout> retvalLimited = new ArrayList<>();
                retvalLimited.addAll(retval.subList(0, limit));
                return retvalLimited;
            }
            return retval;
        }
        return new ArrayList<>();
    }

    private void insertForce(long blockID, Long txID, long amount, long toID, int height) {
        try {
            int last = -1;
            PreparedStatement statement = conn.prepareStatement("select last from soft where id=? limit 1");
            statement.setLong(1, toID);
            ResultSet rs = statement.executeQuery();
            while (rs.next()) {
                last = rs.getInt(1);
            }
            rs.close();
            statement.close();
            statement = conn.prepareStatement(amount > 0
                    ? "insert into force (block_id, txid, amount, to_id, height, last, ) values (?,?,?,?,?,?)"
                    : "insert into force (block_id, txid, amount, to_id, height, last, tech) values (?,?,?,?,?,?,?)");
            statement.setLong(1, blockID);
            if (txID != null) {
                statement.setLong(2, txID);
            } else {
                statement.setNull(2, Types.BIGINT);
            }
            statement.setLong(3, amount);
            statement.setLong(4, toID);
            statement.setInt(5, height);
            statement.setInt(6, last);
            if (amount == 0) {
                statement.setBoolean(7, true);
            }
            int count = statement.executeUpdate();
            statement.close();
            if (count < 1) {
                throw new SQLException(ERROR_ALREADY);
            }
        } catch (SQLException ex) {
        }
    }

    private void insertForce1440(long blockID, Long txID, long amount, long toID, int height) {
        try {
            PreparedStatement statement = conn.prepareStatement(amount > 0
                    ? "insert into force_1440 (block_id, txid, amount, to_id, height) values (?,?,?,?,?)"
                    : "insert into force_1440 (block_id, txid, amount, to_id, height, tech) values (?,?,?,?,?,?)");
            statement.setLong(1, blockID);
            if (txID != null) {
                statement.setLong(2, txID);
            } else {
                statement.setNull(2, Types.BIGINT);
            }
            statement.setLong(3, amount);
            statement.setLong(4, toID);
            statement.setInt(5, height);
            if (amount == 0) {
                statement.setBoolean(6, true);
            }
            int count = statement.executeUpdate();
            statement.close();
            if (count < 1) {
                throw new SQLException(ERROR_ALREADY);
            }
        } catch (SQLException ex) {
        }
    }

    private boolean checkForce(SMGBlock.Transaction trx) throws SQLException {
        if (trx == null) {
            return false;
        }
        int count = 0;
        Long stxid = null;
        boolean found = false;
        if (trx.getType() != SMGBlock.Type.softMG) {
            throw new SQLException(ERROR_INVALID_TRANSACTION);
        }
        if (trx.getSoftMGTxID() == null) {
            PreparedStatement request = conn.prepareStatement("select stxid from force where not tech and txid is null and amount=? and to_id=?");
            request.setLong(1, trx.getAmount());
            request.setLong(2, trx.getReceiver());
            ResultSet rs = request.executeQuery();
            while (rs.next()) {
                stxid = rs.getString(1) != null ? rs.getLong(1) : null;
                found = true;
            }
            rs.close();
            request.close();
            if (found && stxid == null) {
                try (PreparedStatement statement = conn.prepareStatement("update force set stxid=? where not tech and stxid is null and txid is null and amount=? and to_id=?")) {
                    statement.setLong(1, trx.getID());
                    statement.setLong(2, trx.getAmount());
                    statement.setLong(3, trx.getReceiver());
                    count = statement.executeUpdate();
                }

            }
            if (found && stxid != null) {
                if (stxid == trx.getID()) {
                    count = 1;
                }
            }
        } else {
            try (PreparedStatement request = conn.prepareStatement("select stxid from force where not tech and txid=? and amount=? and to_id=?")) {
                request.setLong(1, trx.getSoftMGTxID());
                request.setLong(2, trx.getAmount());
                request.setLong(3, trx.getReceiver());
                try (ResultSet rs = request.executeQuery()) {
                    while (rs.next()) {
                        stxid = rs.getString(1) != null ? rs.getLong(1) : null;
                        found = true;
                    }
                }
            }
            if (found && stxid == null) {
                try (PreparedStatement statement = conn.prepareStatement("update force set stxid=? where not tech and stxid is null and txid=? and amount=? and to_id=?")) {
                    statement.setLong(1, trx.getID());
                    statement.setLong(2, trx.getSoftMGTxID());
                    statement.setLong(3, trx.getAmount());
                    statement.setLong(4, trx.getReceiver());
                    count = statement.executeUpdate();
                }
            }
            if (found && stxid != null) {
                if (stxid == trx.getID()) {
                    count = 1;
                }
            }
        }
        return count == 1;
    }

    private boolean checkForce1440(SMGBlock.Transaction trx) throws SQLException {
       if (trx == null) {
            return false;
        }
        int count = 0;
        Long stxid = null;
        boolean found = false;
        if (trx.getType() != SMGBlock.Type.softMG) {
            throw new SQLException(ERROR_INVALID_TRANSACTION);
        }
        if (trx.getSoftMGTxID() == null) {
           try (PreparedStatement request = conn.prepareStatement("select stxid from force_1440 where not tech and txid is null and amount=? and to_id=?")) {
               request.setLong(1, trx.getAmount());
               request.setLong(2, trx.getReceiver());
               try (ResultSet rs = request.executeQuery()) {
                   while (rs.next()) {
                       stxid = rs.getString(1) != null ? rs.getLong(1) : null;
                       found = true;
                   }
               }
           }
           if (found && stxid == null) {
               try (PreparedStatement statement = conn.prepareStatement("update force_1440 set stxid=? where not tech and stxid is null and txid is null and amount=? and to_id=?")) {
                   statement.setLong(1, trx.getID());
                   statement.setLong(2, trx.getAmount());
                   statement.setLong(3, trx.getReceiver());
                   count = statement.executeUpdate();
               }
            }
            if (found && stxid != null) {
                if (stxid == trx.getID()) {
                    count = 1;
                }
            }
        } else {
           try (PreparedStatement request = conn.prepareStatement("select stxid from force_1440 where not tech and txid=? and amount=? and to_id=?")) {
               request.setLong(1, trx.getSoftMGTxID());
               request.setLong(2, trx.getAmount());
               request.setLong(3, trx.getReceiver());
               try (ResultSet rs = request.executeQuery()) {
                   while (rs.next()) {
                       stxid = rs.getString(1) != null ? rs.getLong(1) : null;
                       found = true;
                   }
               }
           }
            if (found && stxid == null) {
               try (PreparedStatement statement = conn.prepareStatement("update force_1440 set stxid=? where not tech and stxid is null and txid=? and amount=? and to_id=?")) {
                   statement.setLong(1, trx.getID());
                   statement.setLong(2, trx.getSoftMGTxID());
                   statement.setLong(3, trx.getAmount());
                   statement.setLong(4, trx.getReceiver());
                   count = statement.executeUpdate();
               }
            }
            if (found && stxid != null) {
                if (stxid == trx.getID()) {
                    count = 1;
                }
            }
        }
        return count == 1;
    }

    private boolean checkAnnounceCanReceive(SMGBlock.Transaction trx) throws SQLException {
       if (trx.getType() != SMGBlock.Type.softMG) {
            return true;
        }
        boolean success = false;
        // Do nothing!
        if (trx.getSoftMGTxID() != null) {
           try (PreparedStatement statement = conn.prepareStatement("update force set announced=true where not tech and announced=false and block_id=? and txid=? and amount=? and to_id=?")) {
               statement.setLong(1, trx.getSoftMGBlockID());
               statement.setLong(2, trx.getSoftMGTxID());
               statement.setLong(3, trx.getAmount());
               statement.setLong(4, trx.getReceiver());
               success = (statement.executeUpdate() == 1);
           }
        } else {
           try (PreparedStatement statement = conn.prepareStatement("update force set announced=true where not tech and announced=false and block_id=? and txid is null and amount=? and to_id=?")) {
               statement.setLong(1, trx.getSoftMGBlockID());
               statement.setLong(2, trx.getAmount());
               statement.setLong(3, trx.getReceiver());
               success = (statement.executeUpdate() == 1);
           }
        }
        return success;
    }


    private void checkBlockIsSuccess(long blockID) throws SQLException {
        // Do nothing!
        // Do nothing!
        PreparedStatement statement = conn.prepareStatement("select count(*) from force where not tech and block_id=? and stxid is null");
        statement.setLong(1, blockID);

        ResultSet rs = statement.executeQuery();

        int opensoftMGTransactions = 0;

        while (rs.next()) {
            opensoftMGTransactions = rs.getInt(1);
        }
        rs.close();
        statement.close();

        if (opensoftMGTransactions > 0) {
            return;
        }

        statement = conn.prepareStatement("update block set accepted=true where id=? and accepted=false");
        statement.setLong(1, blockID);
        statement.executeUpdate();
        statement.close();
    }

    private void checkBlockIsSuccess1440(long blockID) throws SQLException {
        // Do nothing!
        // Do nothing!
        PreparedStatement statement = conn.prepareStatement("select count(*) from force_1440 where not tech and block_id=? and stxid is null");
        statement.setLong(1, blockID);

        ResultSet rs = statement.executeQuery();

        int opensoftMGTransactions = 0;

        while (rs.next()) {
            opensoftMGTransactions = rs.getInt(1);
        }
        rs.close();
        statement.close();

        if (opensoftMGTransactions > 0) {
            return;
        }

        statement = conn.prepareStatement("delete from force where not tech and block_id=?");
        statement.setLong(1, blockID);
        statement.executeUpdate();
        statement.close();

        statement = conn.prepareStatement("delete from force_1440 where not tech and block_id=?");
        statement.setLong(1, blockID);
        statement.executeUpdate();
        statement.close();

        statement = conn.prepareStatement("update block_1440 set accepted=true where not tech and id=? and accepted=false");
        statement.setLong(1, blockID);
        statement.executeUpdate();
        statement.close();
    }

    private void insertHoldTransfer(long account, long amount, int height) throws SQLException {
        try {
            PreparedStatement updater = conn.prepareStatement("insert into hold_transfer values (?,?,?)");
            updater.setLong(1, account);
            updater.setLong(2, amount);
            updater.setInt(3, height);
            updater.executeUpdate();
            updater.close();
        } catch (SQLException ex) {
            throw ex;
        }
    }

    private void setLastForgedBlockHeight(long account, int height) throws SQLException {
        PreparedStatement updater = conn.prepareStatement("update soft set last_forged_block_height=? where id=?");
        updater.setInt(1, height);
        updater.setLong(2, account);
        updater.executeUpdate();
        updater.close();
    }

    private void updateHold(long ID, long diff) throws Exception {
        try {
            PreparedStatement updater = conn.prepareStatement("update soft set hold=hold+? where id=?");
            updater.setLong(1, diff);
            updater.setLong(2, ID);
            updater.executeUpdate();
            updater.close();
        } catch (SQLException ex) {
            throw ex;
        }
    }

    private void update(long ID, long diff, Integer stamp) throws Exception {
        List<HeapStore> heaps = new ArrayList<HeapStore>();

        try {
            PreparedStatement values = conn.prepareStatement("set @value1 = ?");
            values.setLong(1, ID);
            values.executeUpdate();
            PreparedStatement statement = conn.prepareStatement("WITH LINK(ID, PARENT_ID, LEVEL) AS (\n"
                    + "    SELECT ID, PARENT_ID, 0 FROM SOFT WHERE ID = @value1\n"
                    + "    UNION ALL\n"
                    + "    SELECT SOFT.ID, SOFT.PARENT_ID, LEVEL + 1\n"
                    + "    FROM LINK INNER JOIN SOFT ON LINK.PARENT_ID = SOFT.ID AND LINK.LEVEL < 10\n" 
                    + " )\n"
                    + " select \n"
                    + "   link.id,\n"
                    + "   link.parent_id,\n"
                    + "   link.level\n"
                    + "from link");
            ResultSet rs = statement.executeQuery();
            while (rs.next()) {
                HeapStore item = new HeapStore(rs.getLong(1), rs.getLong(2), rs.getLong(3));
                heaps.add(item);
            }
            rs.close();
            statement.close();
            values.close();

            Conc conc = null;
            for (HeapStore item : heaps) {
                if (item.getLevel() < 1) {
                    continue;
                }
                if (conc == null) {
                    conc = new Conc();
                }
                if (!conc.add(item.getBasic())) {
                    updateFix(conn, conc, diff);
                    conc = null;
                }
            }
            if (conc != null) {
                updateFix(conn, conc, diff);
                conc = null;
            }

            if (stamp != null) {
                PreparedStatement updater = conn.prepareStatement("update soft set balance=balance+?, last=? where id=?");
                updater.setLong(1, diff);
                updater.setLong(2, stamp);
                updater.setLong(3, ID);
                updater.executeUpdate();
                updater.close();
            } else {
                PreparedStatement updater = conn.prepareStatement("update soft set balance=balance+? where id=?");
                updater.setLong(1, diff);
                updater.setLong(2, ID);
                updater.executeUpdate();
                updater.close();
            }

        } catch (SQLException ex) {
//            log(false, ERROR_ERROR);
            throw ex;
        }
    }

  
    public static SMGBlock.Transaction convert(TransactionImpl transaction) throws HGException {
        return convert(transaction, 1000);
    }

    public static SMGBlock.Transaction convert(TransactionImpl transaction, int height) throws HGException {
        if (transaction == null) {
            throw new HGException("NULL Transaction!");
        }
        SMGBlock.Transaction retval = new SMGBlock.Transaction();
        retval.setID(transaction.getId());
        retval.setAmount(transaction.getAmountNQT());
        retval.setFee(transaction.getFeeNQT());
        retval.setReceiver(transaction.getRecipientId());
        retval.setSender(transaction.getSenderId());
        retval.setStamp(transaction.getTimestamp());
        if (transaction.getSenderId() == Genesis.CREATOR_ID) {
            retval.setType(SMGBlock.Type.softMG);
            if (height > 0) {
                SMGBlock.SoftMGParams softMGParams = SoftMGImpl.getSoftmgParams(transaction);
                if (!softMGParams.isValid()) {
                    throw new HGException("Invalid SoftMG Transaction!");
                }
                retval.setSoftMGBlockID(softMGParams.getBlockID());
                retval.setSoftMGTxID(softMGParams.getBlockTxID());
            }
        } else {
            retval.setType(SMGBlock.Type.ORDINARY);
        }
        return retval;
    }

    private static SMGBlock.SoftMGParams getSoftmgParams(TransactionImpl transaction) {
        SMGBlock.SoftMGParams retval = new SMGBlock.SoftMGParams();
        if (transaction == null
                || transaction.getSenderId() != Genesis.CREATOR_ID
                || transaction.getAppendages(false) == null
                || transaction.getAppendages(false).isEmpty()
                || transaction.getFeeNQT() != 0) {
            return retval;
        }
        JSONParser parser = new JSONParser();
        for (Appendix.AbstractAppendix infos : transaction.getAppendages(false)) {
            JSONObject json;
            try {
                if (infos != null && infos.getJSONObject() != null && infos.getJSONObject().get("message") != null) {
                    json = (JSONObject) parser.parse(infos.getJSONObject().get("message").toString());
                    if (json == null
                            || json.get(Constants.IN_BLOCK_ID) == null
                            || json.get(Constants.IN_BLOCK_HEIGHT) == null) {
                        continue;
                    }
                    retval.setBlockID(Long.parseLong(json.get(Constants.IN_BLOCK_ID).toString()));
                    if (json.get(Constants.IN_TRANSACT_ID) != null) {
                        retval.setBlockTxID(Long.parseLong(json.get(Constants.IN_TRANSACT_ID).toString()));
                    }
                    retval.setValid(true);
                    return retval;
                }
            } catch (Exception ex) {
                return new SMGBlock.SoftMGParams();
            }
        }
        return retval;
    }

    private static final Object LOCK_OBJECT = new Object();
   private void checkSoftMGBlockIsValid(SMGBlock softBlock) throws SQLException {
        preinit();
        long ID = 0l;
        int stamp = 0;
        int maxHeight = 0;
        long creatorID = 0l;
        boolean hasBlock = false;

        PreparedStatement statement = conn.prepareStatement("select id,stamp,creator_id from block where height=?");
        statement.setLong(1, softBlock.getHeight());
        ResultSet rs = statement.executeQuery();
        while (rs.next()) {
            hasBlock = true;
            ID = rs.getLong(1);
            stamp = rs.getInt(2);
            creatorID = rs.getLong(3);
        }
        rs.close();
        statement.close();
        if (!hasBlock) {
            statement = conn.prepareStatement("select max(height) from block");
            rs = statement.executeQuery();
            while (rs.next()) {
                maxHeight = rs.getInt(1);
            }
            rs.close();
            statement.close();
            if (maxHeight > 0) {
                
                if ((maxHeight + 1) != softBlock.getHeight()) {
                    rewriteWorkingDatabase(softBlock.getHeight());
                    commit(false);
                    System.out.println("=========== LOOOSE START (INTERNAL DETECTOR) =============");
                    return;
                }
            }
            return;
        }
        if (ID == softBlock.getID() && stamp == softBlock.getStamp() && creatorID == softBlock.getGeneratorID()) {
            return;
        }
       rewriteWorkingDatabase(softBlock.getHeight());
        commit(false);
        System.out.println("=========== LOOOSE START =============");
    }

    private boolean checkSoftMGBlockIsAccepted(SMGBlock softBlock) throws SQLException {
        boolean accepted = false;
         PreparedStatement statement = conn.prepareStatement("select accepted from block where id=? and height=? and stamp=?");
        statement.setLong(1, softBlock.getID());
        statement.setInt(2, softBlock.getHeight());
        statement.setInt(3, softBlock.getStamp());
        ResultSet rs = statement.executeQuery();
        while (rs.next()) {
            accepted = rs.getBoolean(1);
        }
        rs.close();
        statement.close();
        return accepted;
    }

    private boolean checkSoftMGBlockIsAccepted1440(SMGBlock softBlock) throws SQLException {
       boolean accepted = false;
        PreparedStatement statement = conn.prepareStatement("select accepted from block_1440 where id=? and height=? and stamp=?");
        statement.setLong(1, softBlock.getID());
        statement.setInt(2, softBlock.getHeight());
        statement.setInt(3, softBlock.getStamp());
        ResultSet rs = statement.executeQuery();
        while (rs.next()) {
            accepted = rs.getBoolean(1);
        }
        rs.close();
        statement.close();
        return accepted;
    }

    private class CheckInternal {

        private List<SMGBlock.Payout> payouts = new ArrayList<>();
        private boolean hasTransactions = false;

        public List<SMGBlock.Payout> getPayouts() {
            return payouts;
        }

        public void setPayouts(List<SMGBlock.Payout> payouts) {
            if(payouts==null){
             return;
            }
            
            this.payouts = payouts;
        }

        public void setHasTransactions(boolean hasTransactions) {
            this.hasTransactions = hasTransactions;
        }

        public boolean isHasTransactions() {
            return hasTransactions;
        }
    }

    private CheckInternal checkInternal(SMGBlock softBlock, boolean ordinary) throws HGException {
        CheckInternal retvalue = new CheckInternal();
        boolean blockExists = false;
        if (softBlock == null) {
            return retvalue;
        }
        if (softBlock.getTransactions().isEmpty()) {
            return retvalue;
        }
        try {
            if (ordinary ? checkSoftMGBlockIsAccepted(softBlock) : checkSoftMGBlockIsAccepted1440(softBlock)) {
                return retvalue;
            }
            List<SMGBlock.Payout> payret = new ArrayList<>();
            if (!softBlock.getTransactions().isEmpty()) {
                retvalue.setHasTransactions(true);
            }
           payret = (ordinary ? insertBlock(softBlock.getID(), softBlock.getHeight(), softBlock.getFee(), softBlock.getStamp(), softBlock.getGeneratorID(), false)
                    : insertBlock1440(softBlock.getID(), softBlock.getHeight(), softBlock.getFee(), softBlock.getStamp(), softBlock.getGeneratorID(), false));  
            
            if (payret != null) {
               retvalue.setPayouts(payret);
                blockExists = true;
            }
            List<SMGBlock.Transaction> allTransactionsReverseSort = SMGBlock.sort(softBlock.getTransactions());
            List<SMGBlock.Transaction> allTransactionsDirectSort = SMGBlock.reverse(allTransactionsReverseSort);
            HashMap<Long, SMGBlock.Transaction> transactionsOrdinary = new HashMap<>();
            List<SMGBlock.Transaction> transactionsSoftMG = new ArrayList<>();
            List<SMGBlock.Transaction> transactionsSoftMGSorted = new ArrayList<>();
            Set<Long> blocksForCheck = new HashSet<>();
            Set<Long> senders = new HashSet<>();
            HashMap<Long, SoftMGs> metricsMap = new HashMap<>();
            boolean holdEnabled = softBlock.getHeight() >= Constants.HOLD_ENABLE_HEIGHT;
            HashMap<Long, Long> diffs = new HashMap<>();
            HashMap<Long, Integer> stamps = new HashMap<>();
            HashMap<Long, Long> diffs1440 = new HashMap<>();
            HashMap<Long, Integer> stamps1440 = new HashMap<>();

            if (holdEnabled) {
                for (SMGBlock.Transaction tx : allTransactionsDirectSort) { 
                    if (!senders.contains(tx.getSender()) && tx.getSender() != Genesis.CREATOR_ID) {
                        senders.add(tx.getSender());
                    }
                }
            }
            SMGComputator calculator = new SMGComputator(ordinary ? getGenesisEmission() : getGenesisEmission1440());
            for (SMGBlock.Transaction tx : allTransactionsReverseSort) {
                switch (tx.getType()) {
                    case ORDINARY:
                        if (!blockExists) {
                            if (!transactionsOrdinary.containsKey(tx.getSender())) {
                                transactionsOrdinary.put(tx.getSender(), tx);
                            }
                            if (!transactionsOrdinary.containsKey(tx.getReceiver())) {
                                transactionsOrdinary.put(tx.getReceiver(), tx);
                            }
                        }
                        break;
                    case softMG:
                        if (tx.getSoftMGBlockID() == null && softBlock.getHeight() > 0) {
                            throw new HGException("SoftMGblock with wrong internal structure!");
                        }
                        transactionsSoftMG.add(tx);
                        break;
                }
            }
            if (!blockExists) {
                boolean hasCoreTransaction = false;
                for (Map.Entry<Long, SMGBlock.Transaction> item : transactionsOrdinary.entrySet()) {

                        if (softBlock.getGeneratorID() == item.getKey()) {
                            SoftMGs metrics = ordinary ? getMetricsForAccount(softBlock.getGeneratorID(), softBlock.getStamp(), softBlock.getHeight(), holdEnabled)
                                    : getMetricsForAccount1440(softBlock.getGeneratorID(), softBlock.getStamp(), softBlock.getHeight(), holdEnabled);
                            if (holdEnabled) {
                                metricsMap.put(item.getKey(), metrics);
                                if (metrics.isOnHoldAtHeight(softBlock.getHeight())) {
                                    if (!senders.contains(item.getKey())) {
                                        continue;
                                    }
                                }
                            }
                            if (metrics.getPayout() < 0l) {
                                continue;
                            }
                            SMGBlock.Payout payout = new SMGBlock.Payout();
                            payout.setBlockID(softBlock.getID());
                            payout.setAmount(metrics.getPayout());
                            payout.setHeight(softBlock.getHeight());
                            payout.setToID(softBlock.getGeneratorID());
                            if (calculator.add(softBlock.getGeneratorID(), metrics.getPayout())) {
                                hasCoreTransaction = true;
                                retvalue.getPayouts().add(payout);
                                if (ordinary) {
                                    insertForce(softBlock.getID(), null, metrics.getPayout(), softBlock.getGeneratorID(), softBlock.getHeight());
                                } else {
                                    insertForce1440(softBlock.getID(), null, metrics.getPayout(), softBlock.getGeneratorID(), softBlock.getHeight());
                                }
                            }
                        } else {
                            SoftMGs metrics = ordinary ? getMetricsForAccount(item.getKey(), softBlock.getStamp(), softBlock.getHeight(), holdEnabled)
                                    : getMetricsForAccount1440(item.getKey(), softBlock.getStamp(), softBlock.getHeight(), holdEnabled);
                            if (holdEnabled) {
                                metricsMap.put(item.getKey(), metrics);
                                if (metrics.isOnHoldAtHeight(softBlock.getHeight())) {
                                    if (!senders.contains(item.getKey())) {
                                        continue;
                                    }
                                }
                            }
                            if (metrics.getPayout() < 0l) {
                                continue;
                            }
                            SMGBlock.Payout payout = new SMGBlock.Payout();
                            payout.setBlockID(softBlock.getID());
                            payout.setTxID(item.getValue().getID());
                            payout.setAmount(metrics.getPayout());
                            payout.setHeight(softBlock.getHeight());
                            payout.setToID(item.getKey());
                            if (calculator.add(item.getKey(), metrics.getPayout())) {
                                retvalue.getPayouts().add(payout);
                                if (ordinary) {
                                    insertForce(softBlock.getID(), item.getValue().getID(), metrics.getPayout(), item.getKey(), softBlock.getHeight());
                                } else {
                                    insertForce1440(softBlock.getID(), item.getValue().getID(), metrics.getPayout(), item.getKey(), softBlock.getHeight());
                                }
                            }
                        }
                   // }
                }
                if (!hasCoreTransaction && softBlock.getFee() > 0l) {
                    SoftMGs metrics = ordinary ? getMetricsForAccount(softBlock.getGeneratorID(), softBlock.getStamp(), softBlock.getHeight(), holdEnabled)
                            : getMetricsForAccount1440(softBlock.getGeneratorID(), softBlock.getStamp(), softBlock.getHeight(), holdEnabled);
                    if (holdEnabled && !metricsMap.containsKey(softBlock.getGeneratorID())) {
                        metricsMap.put(softBlock.getGeneratorID(), metrics);
                    }
                    if (metrics.getPayout() >= 0l && (!holdEnabled || !metrics.isOnHoldAtHeight(softBlock.getHeight()) || senders.contains(softBlock.getGeneratorID()) )) {
                        SMGBlock.Payout payout = new SMGBlock.Payout();
                        payout.setBlockID(softBlock.getID());
                        payout.setAmount(metrics.getPayout());
                        payout.setHeight(softBlock.getHeight());
                        payout.setToID(softBlock.getGeneratorID());
                        if (calculator.add(softBlock.getGeneratorID(), metrics.getPayout())) {
                            retvalue.getPayouts().add(payout);
                            if (ordinary) {
                                insertForce(softBlock.getID(), null, metrics.getPayout(), softBlock.getGeneratorID(), softBlock.getHeight());
                            } else {
                                insertForce1440(softBlock.getID(), null, metrics.getPayout(), softBlock.getGeneratorID(), softBlock.getHeight());
                            }
                        }
                    }
                }
               for (SMGBlock.Transaction item : allTransactionsDirectSort) {

                        if (item.getType() == SMGBlock.Type.ORDINARY) {
                            if (ordinary) {
                                createNetwork(item.getReceiver(), item.getSender(), softBlock.getStamp(), softBlock.getHeight());
                                addDiff(item.getReceiver(), item.getAmount() + calculator.get(item.getReceiver()), softBlock.getStamp(), diffs, stamps);
                                addDiff(item.getSender(), 0l - item.getAmount() + calculator.get(item.getSender()) - item.getFee(), softBlock.getStamp(), diffs, stamps);
                            } else {
                                createNetwork1440(item.getReceiver(), item.getSender(), softBlock.getStamp());
                                addDiff(item.getReceiver(), item.getAmount() + calculator.get(item.getReceiver()), softBlock.getStamp(), diffs1440, stamps1440);
                                addDiff(item.getSender(), 0l - item.getAmount() + calculator.get(item.getSender()) - item.getFee(), softBlock.getStamp(), diffs1440, stamps1440);
                            }
                        }
                  
                }
                if (softBlock.getFee() > 0l) {
                    if (ordinary) {
                        addDiff(softBlock.getGeneratorID(), softBlock.getFee() + calculator.get(softBlock.getGeneratorID()), softBlock.getStamp(), diffs, stamps);
                    } else {
                        addDiff(softBlock.getGeneratorID(), softBlock.getFee() + calculator.get(softBlock.getGeneratorID()), softBlock.getStamp(), diffs1440, stamps1440);
                    }
                }
                if (calculator.hasGenesisDiff()) {
                    if (ordinary) {
                        addDiff(Genesis.CREATOR_ID, calculator.getGenesisDiff(), softBlock.getStamp(), diffs, stamps);
                    } else {
                        addDiff(Genesis.CREATOR_ID, calculator.getGenesisDiff(), softBlock.getStamp(), diffs1440, stamps1440);
                    }
                }
            }
            transactionsSoftMGSorted = SMGBlock.reverse(SMGBlock.sort(transactionsSoftMG));
            for (SMGBlock.Transaction tx : transactionsSoftMGSorted) {
                if (softBlock.getHeight() == 0 || softBlock.getID() == Genesis.GENESIS_BLOCK_ID) {
                    if (ordinary) {
                        createNetwork(tx.getReceiver(), tx.getSender(), tx.getStamp(), 0);
                        addDiff(tx.getReceiver(), tx.getAmount(), tx.getStamp(), diffs, stamps);
                        addDiff(tx.getSender(), 0l - tx.getAmount() - tx.getFee(), tx.getStamp(), diffs, stamps);
                    } else {
                        createNetwork1440(tx.getReceiver(), tx.getSender(), tx.getStamp());
                        addDiff(tx.getReceiver(), tx.getAmount(), tx.getStamp(), diffs1440, stamps1440);
                        addDiff(tx.getSender(), 0l - tx.getAmount() - tx.getFee(), tx.getStamp(), diffs1440, stamps1440);
                    }
                } else {
                    if (!blocksForCheck.contains(tx.getSoftMGBlockID())) {
                        blocksForCheck.add(tx.getSoftMGBlockID());
                    }

                 
                        if (!(ordinary ? checkForce(tx) : checkForce1440(tx))) {
                            if (true) {
                                throw new HGException((softBlock.getHeight() + ": Genesis transaction wrong: " + tx.getID() + " > " + tx.getReceiver()) + " : " + tx.getAmount() + " \n" + tx.toString(), softBlock.getHeight());
                            } else {
                             
                            }
                        }


                }
            }
            if (ordinary) {
                for (Long account : diffs.keySet()) {
                    if (holdEnabled && account != Genesis.CREATOR_ID) {
                        boolean isOnHold = metricsMap.get(account) != null && metricsMap.get(account).isOnHoldAtHeight(softBlock.getHeight());
                        boolean shouldTransferHold = (!isOnHold || senders.contains(account)) && metricsMap.get(account) != null && metricsMap.get(account).getHold() > 0;
                        if (shouldTransferHold) {
                            addDiff(account, metricsMap.get(account).getHold(), null, diffs, stamps);
                            updateHold(account, -metricsMap.get(account).getHold());
                            insertHoldTransfer(account, metricsMap.get(account).getHold(), softBlock.getHeight());
                        }
                        if (isOnHold && !senders.contains(account)) {
                            updateHold(account, diffs.get(account));
                        } else {
                            update(account, diffs.get(account), stamps.get(account));
                        }
                    } else {
                        update(account, diffs.get(account), stamps.get(account));
                    }
                }
            }
            for (Long ID : blocksForCheck) {
                if (ID == null) {
                    continue;
                }
                if (ordinary) {
                    checkBlockIsSuccess(ID);
                } else {
                    checkBlockIsSuccess1440(ID);
                }
            }
            if (ordinary) {
                checkBlockIsSuccess(softBlock.getID());
            } else {
                checkBlockIsSuccess1440(softBlock.getID());
            }
            if (softBlock.getHeight() >= Constants.HOLD_ENABLE_HEIGHT - Constants.HOLD_RANGE) {
                setLastForgedBlockHeight(softBlock.getGeneratorID(), softBlock.getHeight());
            }
            int limit = 512 - retvalue.getPayouts().size();
            List<SMGBlock.Payout> finishPayouts = ordinary ? getUnpayedSoftMGTransactions(softBlock.getHeight(), limit) : getUnpayedSoftTransactions1440(softBlock.getHeight(), limit);
            retvalue.getPayouts().addAll(finishPayouts);
        } catch (Exception ex) {
            Logger.logErrorMessage(ex.getMessage(), ex); // More details on exception's source
            if (ex.getMessage().contains("Genesis transaction wrong") || ex.getMessage().contains("\"FORCE\"")) {
                try {
                    rewriteWorkingDatabase(BlockchainImpl.getInstance().getHeight());
                } catch (SQLException sqle) {
                    sqle.printStackTrace();
                    throw new HGException("Failed to resurrect BENED database: " + sqle.getMessage());
                }
            }
            throw new HGException(ex.getMessage());
        }
        commit(false);
        return retvalue;
    }

    private boolean finalizeOrdinaryCheck(int heightIn) {
        if (useOnlyNewRollbackAlgo) {
            return false;
        }
        int height1440 = -1;
        try {
            height1440 = getParamLast1440();
        } catch (SQLException ex) {
        }
        int height = heightIn - CACHE_SIZE;

        if (height < 0 || height <= height1440) {
            return false;
        }

        SMGBlock softBlock = getBlockFromBlockchainWithNoTransactions(height);
        if (softBlock == null) {
            return false;
        }
        if (softBlock != null && softBlock.hasNoTransactions()) {
            try {
                insertBlock1440(softBlock.getID(), softBlock.getHeight(), 0, softBlock.getStamp(), softBlock.getGeneratorID(), true);
            } catch (SQLException ex) {
            }
            commit(false);
            return false;
        }
        try {
            // Do nothing!
            checkInternal(softBlock, false);
            return true;
        } catch (HGException ex) {
        }
        return false;
    }

    private void ressurectDatabaseIfNeeded(int height) throws SQLException {
        boolean allRight = true;
        PreparedStatement statement = conn.prepareStatement("select id,stamp,height from block where height=?");
        statement.setInt(1, height - 1);
        ResultSet rs = statement.executeQuery();
        while (rs.next()) {
            long ID = rs.getLong(1);
            int stamp = rs.getInt(2);
            int i = rs.getInt(3);
            SMGBlock block = getBlockFromBlockchainWithNoTransactions(i);
            if (block.getID() != ID || block.getStamp() != stamp) {
                allRight = false;
                break;
            }

        }
        rs.close();
        statement.close();

        if (allRight) {
            return;
        }
        rewriteWorkingDatabase(height);
        commit(false);
    }

    @Override
    public List<SMGBlock.Payout> check(SMGBlock softBlock, int height, SMGBlock softBlockIncognito) throws HGException {
        try {
            preinit();
            try {
                ressurectDatabaseIfNeeded(height);
            } catch (SQLException ex) {
                Logger.logErrorMessage(" +++ DATABASE INCONSISTENCY DETECTED +++");
                try {
                    Logger.logErrorMessage(" +++ RECONSTRUCTING DATABASE - DO NOT RESTART CORE +++");
                    rewriteWorkingDatabase(height);
                    Logger.logErrorMessage("=== DATABASE IS POSSIBLY FIXED ===");
                } catch (SQLException sqle) {
                    sqle.printStackTrace();
                    Logger.logErrorMessage(" +++ DATABASE IS DEAD - RE-SYNCHRONIZATION INEVITABLE +++");
                    System.exit(1);
                }
            }

            boolean isOrdinaryChecked = false;
            synchronized (LOCK_OBJECT) {

                if (height > 0 && !useOnlyNewRollbackAlgo) {
                    isOrdinaryChecked = finalizeOrdinaryCheck(height);
                    if (isOrdinaryChecked) {
                        commit(false);
                    }
                }
                if (softBlock != null && softBlock.getTransactions() != null) {
                    for (SMGBlock.Transaction trx : softBlock.getTransactions()) {
                        if (trx != null && trx.getAmount() < 0d) {
                            trx.setAmount(0 - trx.getAmount());
                        }
                    }
                }
                if (softBlock == null && softBlockIncognito != null) {
                    try {
                        insertBlock(softBlockIncognito.getID(), softBlockIncognito.getHeight(), 0, softBlockIncognito.getStamp(), softBlockIncognito.getGeneratorID(), true);
                        commit(false);
                    } catch (SQLException ex) {
                    }
                    return new ArrayList<SMGBlock.Payout>();
                }
                try {
                    checkSoftMGBlockIsValid(softBlock);
                    CheckInternal checkInternalRetval = checkInternal(softBlock, true);
                    if (!useOnlyNewRollbackAlgo) {
                        if (softBlock.getHeight() == 0) {
                            checkInternal(softBlock, false);
                        }
                        final Integer newRollbackHeight = getParameter(FAST_ROLLBACK_ENABLED_HEIGHT);
                        if (newRollbackHeight != null && newRollbackHeight <= height) {
                            log(true, "Switched to the new rollback algorithm");
                            useOnlyNewRollbackAlgo = true;
                            dropOldDatabases(height);
                        }
                    } else {
                        trimDerivedTables();
                    }
                    commit(false);

 
                    return checkInternalRetval.getPayouts();

                } catch (HGException ex) {
                   rollback();
                    if (ex.hasHeight()) {
                        System.out.println(" === MIRACLE EXCHANGE ===                   (height: " + ex.getHeight() + ")");
                        try {
                            rewriteWorkingDatabase(ex.getHeight());
                        } catch (SQLException exception) {
                        }
                    }
                    commit(false);
                    throw ex;
                } catch (SQLException ex) {
                    rollback();
                    throw new HGException(ex.getMessage());
                }
            }
        } finally {
            commit(false);
        }
    }

    @Override
    public boolean canReceive(SMGBlock.Transaction trx) {
        synchronized (LOCK_OBJECT) {
            preinit();
            try {
                return checkAnnounceCanReceive(trx);
            } catch (SQLException ex) {
                return false;
            } finally {
                commit(false);
            }
        }
    }

    @Override
    public SoftMGs getMetrics(long accountID) {
        

        synchronized (LOCK_OBJECT) {
            preinit();
            SoftMGs metrics = new SoftMGs();
            try {
                PreparedStatement statement = conn.prepareStatement("select amount, balance, last, hold, last_forged_block_height from soft where id=?");
                statement.setLong(1, accountID);
                ResultSet rs = statement.executeQuery();
                while (rs.next()) {
                    metrics.setAmount(rs.getLong(1));
                    metrics.setAccountID(accountID);
                    metrics.setBalance(rs.getLong(2));
                    metrics.setBeforeStamp(rs.getInt(3));
   long time = System.currentTimeMillis() / 1000 ;
   long diff = metrics.getBeforeStamp();
        diff = diff + Constants.EPOCH_BEGINNING/1000; 

metrics.setAfterStamp((int)( (time - diff) + metrics.getBeforeStamp()) );

                  
                    metrics.setHold(rs.getLong(4));
                    metrics.setLastForgedBlockHeight(rs.getInt(5));
                }
                rs.close();
                statement.close();

metrics.setGenesisEmission(getGenesisEmission());

                    metrics.calculatePyoutSet();

            } catch (SQLException ex) {
            } finally {
                commit(false);
            }
            return metrics;
        }
    }

    public boolean isZeroblockFixed() {
        if (!zeroblockFixed) {
            preinit();
            Integer fixed = getParameter(ZEROBLOCK_FIXED);
            if (fixed != null) {
                zeroblockFixed = true;
            }
        }
        return zeroblockFixed;
    }

    public void zeroblockFixed() {
        preinit();
        setParameter(ZEROBLOCK_FIXED, 0);
        commit(false);
    }

    @Override
    public void shutdown() {
        commit(true);
    }

    @Override
    public Connection getConnection() {
        preinit();
        return conn;
    }

    @Override
    public long getFixedFee(long amount) {
        long fee;

       fee = (long) (amount * 0.01 <= 150000 ? 150000 : (amount * 0.01 >=100000000 ? 100000000 : amount * 0.01) ); 
       if (Constants.FEE_MAX_10 > BlockchainImpl.getInstance().getHeight()) {
           return fee;
       }
   
        return fee;
    }
    
    @Override
     public long  _getGenesEm(){
        long gem=-1;
        try {
            gem= getGenesisEmission();
        } catch (SQLException ex) {
            return gem;
            //java.util.logging.Logger.getLogger(SoftMGImpl.class.getName()).log(Level.SEVERE, null, ex);
        }
        return gem;
    }
}
