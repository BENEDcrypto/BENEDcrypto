/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package bened;

import bened.Attachment.HashTintAssignment;
import bened.crypto.Crypto;
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
import java.sql.Statement;
import java.util.Iterator;
import org.json.simple.parser.ParseException;
import static bened.Constants.knowcurveblockheight;
import java.sql.BatchUpdateException;
import java.util.logging.Level;


public class SoftMG{

    public static final long MAXIMUM_softMG_AMOUNT = Constants.MAX_full_BALANCE_centesimo*(-1);

    public static final int CACHE_SIZE = 820;
    public static final int CACHE_DEEP = 1450;
    
    public static boolean otlovforcerepair = false;

    public static final String
            FAST_ROLLBACK_ENABLED_HEIGHT = "fast_rollback_enabled_height",
            SoftMGBASE_FAST_ROLLBACK_UPDATE_HEIGHT = "softMGbase_fast_rollback_update_height",
            MIN_FAST_ROLLBACK_HEIGHT = "min_fast_rollback_height",
            HOLD_UPDATE_HEIGHT = "hold_update_height",
            ZEROBLOCK_FIXED = "zeroblock_fixed",
            HOLD_INTEGRITY_VALIDATED = "hold_validated";

    public static class SoftPair {

        private SoftMGs metricsSender;
        private SoftMGs metricsReceiver;

        public SoftMGs getMetricsSender() {
            return metricsSender;
        }

        public void setMetricsSender(SoftMGs metricsSender) {
            this.metricsSender = metricsSender;
        }

        public SoftMGs getMetricsReceiver() {
            return metricsReceiver;
        }

        public void setMetricsReceiver(SoftMGs metricsReceiver) {
            this.metricsReceiver = metricsReceiver;
        }
    }

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
    private static boolean zeroblockFixed = true;

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

    private final BoostMap<Long, Boolean> networkBooster = new BoostMap<>(8192, -1);
   
    
    private boolean initialized = false;
    
    public void init() {
        synchronized (LOCK_OBJECT) { 
            if (initialized) {
                return;
            }
            initialized = true;
            initDB();
            log(true, "DATABASE INITIALIZED");
            commit();
            
        }
    }

    public SoftMG(String JDBC, String login, String password) {

        this.JDBC = JDBC;
        this.login = login;
        this.password = password;
    }

    private void update(String SQL) throws SQLException {
        try (PreparedStatement pre = conn.prepareStatement(SQL)) {
            pre.executeUpdate();
        }
    }
 
   private void initDB() {
        try {
            Class.forName("org.h2.Driver");
            long maxCacheSize = Bened.getIntProperty("bened.dbCacheKB");
            if (maxCacheSize == 0) {
                maxCacheSize = Math.min(256, Math.max(16, (Runtime.getRuntime().maxMemory() / (1024 * 1024) - 128)/2)) * 1024;
            }
            JDBC += ";CACHE_SIZE=" + maxCacheSize; // + ";TRACE_LEVEL_FILE=1";
            
            this.conn = DriverManager.getConnection(JDBC, login, password);
            this.conn.setAutoCommit(false);
            update("begin work;");
            try( Statement stmt = this.conn.createStatement()) {
                stmt.executeUpdate("SET DEFAULT_LOCK_TIMEOUT " + (Bened.getIntProperty("bened.dbDefaultLockTimeout") * 1000));
                stmt.executeUpdate("SET MAX_MEMORY_ROWS " + Bened.getIntProperty("bened.dbMaxMemoryRows"));
            }
            commit();
            ///////////
            String query="SHOW force";
            
            try(Statement stmt = this.conn.createStatement();
                ResultSet rs=stmt.executeQuery("select * FROM force limit 1");){
                rs.next();
            }catch(SQLException exc){
                log(true, "Initialize softMG database...");
                    
            update("create table IF NOT EXISTS soft (id bigint not null, parent_id bigint, amount bigint not null default 0, "
                    + "balance bigint not null default 0, hold bigint not null default 0, last int not null,"
                    + " last_forged_block_height int not null default 0)");
            update("create unique index soft_pk on soft(id);");
            update("ALTER TABLE soft ADD CONSTRAINT parent_id UNIQUE(id)");//<<--mi!!2.1.214
            update("alter table soft add foreign key (parent_id) references soft(id);");
                    
            
            update("create table IF NOT EXISTS activation (soft_id bigint primary key, height int not null)");
            update("alter table activation add foreign key (soft_id) references soft(id) on delete cascade;");
            
            update("create table IF NOT EXISTS block (id bigint not null, height int not null, fee bigint not null default 0,"
                    + " stamp int not null default 0, accepted boolean not null default false,"
                    + " creator_id long not null);");
            update("create unique index block_pk on block(id);");
            update("ALTER TABLE block ADD CONSTRAINT block_id UNIQUE(id)");//<<--mi!!2.1.214
            update("create unique index block_height on block(height);");
                    
            update("create table IF NOT EXISTS force (block_id bigint not null, txid bigint, amount bigint not null, to_id bigint not null,"
                    + " announced boolean not null default false, stxid bigint, height int not null, last int, "
                    + "tech boolean not null default false);");
            update("create unique index force_master on force(txid, to_id);");
//            update("create index force_master on force(txid, to_id);");
            update("create index force_height on force(height);");
//            update("create unique index force_stxid on force(stxid);");
            update("create index force_stxid on force(stxid);");
            update("create index force_tech on force(tech);");        
            
            update("alter table force add foreign key (block_id) references block(id);");
            
            
            update("create table IF NOT EXISTS hold_transfer (id bigint not null, amount bigint not null, height int not null);");
            update("create index hold_transfer_account_id on hold_transfer(height desc)");
                            
                    
            update("create table IF NOT EXISTS parameters (_key varchar(80)  PRIMARY KEY, _value varchar)");
            }

                
            int height = Bened.getBlockchain().getHeight();
            
            setParameter(ZEROBLOCK_FIXED, 0);
            setParameter(SoftMGBASE_FAST_ROLLBACK_UPDATE_HEIGHT, height);
            setParameter(MIN_FAST_ROLLBACK_HEIGHT, height + 100);
            setParameter(FAST_ROLLBACK_ENABLED_HEIGHT, height + CACHE_SIZE);
            setParameter(HOLD_UPDATE_HEIGHT, height);
            setParameter(HOLD_INTEGRITY_VALIDATED, height);
            
            update("commit work;");
            commit();
            log(true, "Success!");
                    ///////////
            

        }catch(Exception e){
            System.out.println("DBinit wrong e:"+e);
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
            for(TransactionImpl blockTransaction : block.getTransactions()) {
                try {
                    SMGBlock.Transaction trx = SoftMG.convert(blockTransaction, block.getHeight());
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
          
            try (PreparedStatement ps = conn.prepareStatement("select _value from parameters where _key=?")) {
                ps.setString(1, key);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        value = rs.getInt(1);
                    }
                }
            }
        } catch (SQLException e) {

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
            try (PreparedStatement ps = conn.prepareStatement("merge into parameters values(?,?)")) {
                ps.setString(1, key);
                ps.setInt(2, value);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        log(true, "($) Set parameter \"" + key + "\" = \"" + value + "\"");
    }

    
    public void popLastBlock() {
        final Block lastBlock = BlockchainImpl.getInstance().getLastBlock();
        final int currentHeight = BlockchainImpl.getInstance().getHeight();
        networkBooster.clear();
        init();
        boolean holdEnabled = currentHeight >= Constants.HOLD_ENABLE_HEIGHT;
        boolean shouldSetLastForgedBlockHeight = currentHeight >= Constants.HOLD_ENABLE_HEIGHT - Constants.HOLD_RANGE;
        synchronized (LOCK_OBJECT) {
            final List<Long> accountsToDelete = new ArrayList<>();
            final TreeMap<Long, Long> diffs = new TreeMap<>();
            final Set<Long> senders = new HashSet<>();
            List<Long> revertedsoftMGTransactions = new ArrayList<>();
            try {
                            
                if (lastBlock.getTransactions() != null && !lastBlock.getTransactions().isEmpty()) {
                    for(Transaction t : lastBlock.getTransactions()) {
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
               
                try(PreparedStatement request = conn.prepareStatement("select to_id,amount,height,last from force where height=?");){
                request.setLong(1, currentHeight);
                try(ResultSet rs = request.executeQuery();){
                while (rs.next()) {
                    SMGBlock.Payout force = new SMGBlock.Payout();
                    force.setToID(rs.getLong(1));
                    force.setAmount(rs.getLong(2));
                    force.setHeight(rs.getInt(3));
                    force.setLast(rs.getInt(4));
                    forces.add(force);
                }
                }
                }

                if (shouldSetLastForgedBlockHeight) {
                    int lastForgedBlockHeight = 0;

                    try(PreparedStatement request = conn.prepareStatement("select max(height) from block where creator_id=? and height<? ");){
                    request.setLong(1, lastBlock.getGeneratorId());
                    request.setInt(2, currentHeight);
                    try(ResultSet rs = request.executeQuery();){
                    while (rs.next()) {
                        lastForgedBlockHeight = rs.getInt(1);
                    }
                    }
                    }

                    try(PreparedStatement request = conn.prepareStatement("update soft set last_forged_block_height=? where id=?");){
                    request.setInt(1, lastForgedBlockHeight);
                    request.setLong(2, lastBlock.getGeneratorId());
                    request.executeUpdate();
                    }
                }

                Map<Long, Long> holdTransfers = new HashMap<>();
                if (holdEnabled) {
                      
                    try(PreparedStatement request = conn.prepareStatement("select id,amount from hold_transfer where height=?");){
                    request.setInt(1, currentHeight);
                    try(ResultSet rs = request.executeQuery();){
                    while (rs.next()) {
                        holdTransfers.put(rs.getLong(1), rs.getLong(2));
                    }
                    }
                    }

                    // FIRST DELETE TRANSFERS
                    try(PreparedStatement request = conn.prepareStatement("delete from hold_transfer where height=?");){
                    request.setInt(1, currentHeight);
                    request.executeUpdate();
                    }

                    // AND THEN PUT THEM ONTO BALANCE
                    try(PreparedStatement pstmt = conn.prepareStatement("update soft set hold=? where id=?") ){
                        for (Long account : holdTransfers.keySet()) {
                            if (account == null) {
                                continue;
                            }
                            addDiff(-holdTransfers.get(account), account, diffs);
                            pstmt.setLong(1, holdTransfers.get(account));
                            pstmt.setLong(2, account);
                            pstmt.addBatch();    
                        }
                        try {
                            int[] result = pstmt.executeBatch(); //(4)
                            conn.commit();
                        } catch (BatchUpdateException ex) {
                            Logger.logErrorMessage("SoftMG AnP poplastblk batch erdo000:"+ex);
                            conn.rollback();
                        }
                    }
                }

                Set<Integer> blockHeights = new HashSet<>();

                // REVERT 'LAST' PARAMETERS AND DELETE FORCES
                int count = 0;
                if (!forces.isEmpty()) {
                    try(PreparedStatement pstmt = conn.prepareStatement("update soft set last=? where id=?")){
                        for(SMGBlock.Payout force : forces) {
                            pstmt.setLong(1, force.getLast());
                            pstmt.setLong(2, force.getToID());
                            pstmt.addBatch();
                            count++;
                            addDiff(0L - force.getAmount(), force.getToID(), diffs);
                            addDiff(force.getAmount(), Genesis.CREATOR_ID, diffs);
                        }
                        try {
                            int[] result = pstmt.executeBatch(); //(4)
                            conn.commit();
                        } catch (BatchUpdateException ex) {
                            Logger.logErrorMessage("SoftMG poplastblk batch er0001:"+ex);
                            conn.rollback();
                        }
                        try (PreparedStatement trimmer = conn.prepareStatement("delete from force where height>=?")) {
                            trimmer.setInt(1, currentHeight);
                            count = trimmer.executeUpdate();
                        }       
                    }      
//                    for (SMGBlock.Payout force : forces) {
//                        request = conn.prepareStatement("update soft set last=? where id=?");
//                        request.setLong(1, force.getLast());
//                        request.setLong(2, force.getToID());
//                        request.executeUpdate();
//                        request.close();
//                        count++;
//                        addDiff(0L - force.getAmount(), force.getToID(), diffs);
//                        addDiff(force.getAmount(), Genesis.CREATOR_ID, diffs);
//                    }
//                    try (PreparedStatement trimmer = conn.prepareStatement("delete from force where height>=?")) {
//                        trimmer.setInt(1, currentHeight);
//                        count = trimmer.executeUpdate();
//                    }
                }

                // RE-OPEN SATISFIED FORCES IN PREVIOUS BLOCKS
                if (!revertedsoftMGTransactions.isEmpty()) {
                    try(PreparedStatement pstmt = conn.prepareStatement("select height from force where stxid IN ("+ getLineOfQs(revertedsoftMGTransactions.size())+")" )) {
                        int ind=0;
                        for(Long stxid : revertedsoftMGTransactions) {
                            pstmt.setLong(++ind, stxid );
                        }
                        try(ResultSet rs = pstmt.executeQuery();){
                        while (rs.next()) {
                            Integer height = rs.getInt(1);
                            if ( height > 0) {
                                blockHeights.add(height);
                            } else {
                            }
                        }
                        }
                    }
                    try(PreparedStatement pstmt =  conn.prepareStatement("update force set stxid=? where stxid=?")){
                        for(Long stxid : revertedsoftMGTransactions) {
                            pstmt.setNull(1, Types.BIGINT);
                            pstmt.setLong(2, stxid);
                            pstmt.addBatch();
                        }
                        try {
                            int[] result = pstmt.executeBatch(); //(4)
                            conn.commit();
                        } catch (BatchUpdateException ex) {
                            Logger.logErrorMessage("SoftMG poplastblk batch er0002:"+ex);
                            conn.rollback();
                        }
                             
                    }                     
//                    for (Long stxid : revertedsoftMGTransactions) {
//                                             
//                        request = conn.prepareStatement("select height from force where stxid=?");
//                        request.setLong(1, stxid);
//                        rs = request.executeQuery();
//                        while (rs.next()) {
//                            Integer height = rs.getInt(1);
//                            if ( height > 0) {
//                                blockHeights.add(height);
//                            } else {
//                            }
//                        }
//                        rs.close();
//                        request.close();
//                        request = conn.prepareStatement("update force set stxid=? where stxid=? limit 1");
//                        request.setNull(1, Types.BIGINT);
//                        request.setLong(2, stxid);
//                        request.executeUpdate();
//                        request.close();
//                    }
                }

                // SET PREVIOUS softmgBLOCKS AS UNACCEPTED
                if (!blockHeights.isEmpty()) {
                    try(PreparedStatement pstmt =  conn.prepareStatement("update block set accepted=false where height=? and accepted=true")){
                        for(Integer notAcceptedHeight : blockHeights) {
                            pstmt.setInt(1, notAcceptedHeight);
                            pstmt.addBatch();
                        }
                        try {
                            int[] result = pstmt.executeBatch(); //(4)
                            conn.commit();
                        } catch (BatchUpdateException ex) {
                            Logger.logErrorMessage("SoftMG poplastblk batch er0003:"+ex);
                            conn.rollback();
                        }           
                    }  
//                    for (Integer notAcceptedHeight : blockHeights) {                    
//                        request = conn.prepareStatement("update block set accepted=false where height=? and accepted=true");
//                        request.setInt(1, notAcceptedHeight);
//                        request.executeUpdate();
//                        request.close();
//                    }
                }

                // DELETE FUTURE BLOCKS - EXPECTED ONLY 1 BLOCK TO BE DELETED (THE CURRENT ONE)
                count = 0;                                    
                try(PreparedStatement request = conn.prepareStatement("delete from block where height>?");){
                    request.setInt(1, currentHeight - 1);
                    count = request.executeUpdate();
                }
                if (count != 1) {
                    if (count < 1) {
                        log(false, "popLastBlock() e001 - No blocks deleted (must be 1) at " + currentHeight);
                    }
                    if (count > 1) {
                        log(false, "popLastBlock() e002 - Too many blocks deleted: " + count + " (must be 1) at " + currentHeight);
                        //--del onli 1
                        count = 0;                                    
                        try(PreparedStatement request = conn.prepareStatement("delete from block where height>?");){
                            request.setInt(1, currentHeight);
                            count = request.executeUpdate();
                        }
                        //
                    }
                }
                String msg = currentHeight + " <- this block is popped\n\tDiffs: [" + diffs.size() + "]";

                // APPLY BALANCE DIFFS
                if (!diffs.isEmpty()) {
                    if (holdEnabled) {
                        //
                        try(PreparedStatement pstmt = conn.prepareStatement("select balance,last_forged_block_height,hold,id from soft where id IN ("+ getLineOfQs(diffs.keySet().size())+")" )) {
                            int ind=0;
                            for(Long accountId : diffs.keySet()) {
                                pstmt.setLong(++ind, accountId);
                            }
                            try(ResultSet rst = pstmt.executeQuery()){
                            int height = 0;
                            long balance = 0l;
                            long hold = 0l;
                            Long accountId =0l;
                            ArrayList<Long> accounts = new ArrayList<>();
                            while (rst.next()) {
                                balance = rst.getLong(1);
                                height = rst.getInt(2);
                                hold = rst.getLong(3);
                                accountId =  rst.getLong(4);
                                
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
                                    accounts.add(accountId);
                                    //update(accountId, diffs.get(accountId), null);
                                }
                            }
                            _update(accounts, diffs, null);
                            }
                        }
                    } else {
                        ArrayList<Long> accounts = new ArrayList<>();
                        for (Long accountId : diffs.keySet()) {
                            msg = msg + ", " + accountId + " " + diffs.get(accountId);
//                            update(accountId, diffs.get(accountId), null);
                            accounts.add(accountId);
                        }
                        _update(accounts, diffs, null);
                    }
                }

                // FIND ACCOUNTS TO DELETE                 
                try(PreparedStatement request = conn.prepareStatement("select soft_id from activation where height=?");){
                request.setInt(1, currentHeight);
                try(ResultSet rs = request.executeQuery();){
                while (rs.next()) {
                    accountsToDelete.add(rs.getLong(1));
                }
                }
                }

                // DELETE ACTIVATED IN THIS BLOCK ACCOUNTS
                count = 0;
                msg = "\tDeleted accounts: [" + accountsToDelete.size() + "]";
                if (!accountsToDelete.isEmpty()) {
                    try(PreparedStatement pstmt =  conn.prepareStatement("delete from soft where id=?")){
                        for(Long id : accountsToDelete) {
                            msg = msg + ", " + id;
                            pstmt.setLong(1, id);
                            pstmt.addBatch();
                        }
                        try {
                            int[] result = pstmt.executeBatch(); //(4)
                            conn.commit();
                        } catch (BatchUpdateException ex) {
                            Logger.logErrorMessage("SoftMG poplastblk batch er0004:"+ex);
                            conn.rollback();
                        }           
                    } 
                    
//                    for(Long id : accountsToDelete) {
//                        msg = msg + ", " + id;
//                        request = conn.prepareStatement("delete from soft where id=?");
//                        request.setLong(1, id);
//                        count = count + request.executeUpdate();
//                        request.close();
//                    }
                }

                commit();
            } catch (Exception e) {
                // TODO
                rollback();
                log(false, "CRITICAL - FAILED TO POP LAST BLOCK BECAUSE OF \"" + e.getMessage() + "\"");
                e.printStackTrace();
            }
        }
    }

    
    public void rollbackToBlock(int blockHeight) {
        networkBooster.clear();
    }




private void trimDerivedTables(){

        final int height = getMGHeight();

        if (height % CACHE_SIZE != 0){ // || !useOnlyNewRollbackAlgo)
            return;
        }
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
    try{
        final int newMinRollbackHeight = height - CACHE_SIZE; // preserve last 820 blocks
        int forces = 0, activations = 0, holdTransfers = 0;
        PreparedStatement statement = conn.prepareStatement("delete from force where height<? and ((stxid is not null) or (stxid is null and tech)) limit " + Constants.BATCH_COMMIT_SIZE);
        statement.setInt(1, newMinRollbackHeight);
        int deleted;
        do {
            deleted = statement.executeUpdate();
            forces += deleted;
            commit();
        } while (deleted >= Constants.BATCH_COMMIT_SIZE);
        statement.close();
        statement = conn.prepareStatement("delete from activation where height<? limit " + Constants.BATCH_COMMIT_SIZE);
        statement.setInt(1, newMinRollbackHeight);
        do {
            deleted = statement.executeUpdate();
            activations += deleted;
            commit();
        } while (deleted >= Constants.BATCH_COMMIT_SIZE);
        statement.close();
        statement = conn.prepareStatement("delete from hold_transfer where height<? limit " + Constants.BATCH_COMMIT_SIZE);
        statement.setInt(1, newMinRollbackHeight);
        do {
            deleted = statement.executeUpdate();
            holdTransfers += deleted;
            commit();
        } while (deleted >= Constants.BATCH_COMMIT_SIZE);
        statement.close();
        setParameter(MIN_FAST_ROLLBACK_HEIGHT, newMinRollbackHeight);
        commit();
        log(true, "trimDerivedTables: Trimmed " + forces + " payouts, " + activations + " activations and " + holdTransfers + " hold transfers at " + height);
      }catch (SQLException ex) {       
            Logger.logErrorMessage(SoftMG.class.getName()+"++1", ex);
      }       
    }
    
       

    private int getMGHeight() {       
        init();
        int retval = -1;
        try ( PreparedStatement request = conn.prepareStatement("select max(height) from block "); 
                ResultSet rs = request.executeQuery()) {
            if (rs == null) {
                throw new SQLException(ERROR_CANT_UPDATE_PARAMETER + " [select]");
            }
            while (rs.next()) {
                if (rs.getString(1) != null) {
                    retval = rs.getInt(1);
                }
            }
        } catch (SQLException ex) {
          //  java.util.logging.Logger.getLogger(SoftMG.class.getName()).log(Level.SEVERE, null, ex);
            log(false, "error - "+SoftMG.class.getName() +" - "+ex);
        }
        if (retval < 0) {

            System.out.println("in base table block no retval -> height = 0");
            retval = 0;
        }       
        return retval;
    }


    private void commit() {
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



    private void createAccount(long accountID, Long senderID, int stamp, int height) throws SQLException {
        if (senderID == null) {
            try (PreparedStatement statement = conn.prepareStatement("insert into soft(id, last) values (?,?)")) {
                statement.setLong(1, accountID);
                statement.setInt(2, stamp);
                statement.executeUpdate();
            }
        } else {
            try (PreparedStatement _stmt = conn.prepareStatement("select * from soft where id=? And parent_id=? limit 1")) {
                _stmt.setLong(1, accountID);
                _stmt.setLong(2, senderID);
               // _stmt.setInt(3, stamp);
                try(ResultSet rs = _stmt.executeQuery();){
                    if(!rs.next()){
                    try (PreparedStatement statement = conn.prepareStatement("insert into soft(id, parent_id, last) values (?,?,?)")) {
                        statement.setLong(1, accountID);
                        statement.setLong(2, senderID);
                        statement.setInt(3, stamp);
                        statement.executeUpdate();
                    }catch(Exception e){
                        Logger.logDebugMessage("\nMG_CREATE_ACCOUNT ERR -SOFT- NEBILO I NETU!!!");
                        //NEBILO\" insert into soft(id, parent_id, last) values (?,?,?)\"\n I NE VSTAVIOS "
                        if(otlovforcerepair){
                            Logger.logWarningMessage("\nMG_CREATE_ACCOUNT ERR(offblock) insert soft uR_err:"+e);
                            Bened.getBlockchainProcessor().popOffTo(height-1);
                        }
                    }
                }else{
                    Logger.logDebugMessage("\nMG_CREATE_ACCOUNT ERR -- PREbilo!!! Updatetlas  !!!");
                    ///uze bil\"insert into soft(id, parent_id, last) values (?,?,?)\"");
                    Logger.logDebugMessage("gms uze est l:"+rs.getNString("last")+" setlast:"+stamp);
                    if(otlovforcerepair){
                        Bened.getBlockchainProcessor().popOffTo(height-1);
                    }
                } 
                }
       
            }
            
        }
        //act
        try (PreparedStatement _stmt = conn.prepareStatement("select * from activation where soft_id=? And height=? limit 1")) {
                _stmt.setLong(1, accountID);
                _stmt.setInt(2, height);
                try(ResultSet rs = _stmt.executeQuery();){
                    if(!rs.next()){
                    try (PreparedStatement activation = conn.prepareStatement("insert into activation(soft_id, height) values (?,?)")) {
                        activation.setLong(1, accountID);
                        activation.setInt(2, height);
                        activation.executeUpdate();
                    }catch(Exception e){
                        Logger.logDebugMessage("\nMG_CREATE_ACCOUNT ERR -ACTIVATION- NEBILO I NETU!!!");
                                //"\n!!!NEBILO\" \"select * from activation where soft_id=? And height=? limit 1\"\n I NE VSTAVIOS ");
                        if(otlovforcerepair){
                            Logger.logDebugMessage("udalim avtivation accountid:"+accountID+" parentid:"+senderID);
                            try (PreparedStatement statement = conn.prepareStatement("DELETE from activation where soft_id=? ")) {
                                statement.setLong(1, accountID);
//                                statement.setInt(2, height);
                                int deleted = statement.executeUpdate();
                                Logger.logDebugMessage("deeted activation :"+deleted);
                            }catch(Exception re){
                                 Logger.logDebugMessage("deeted activation err:"+re);
                            }
                        }
                    }
                }else{
                     Logger.logDebugMessage("\nMG_CREATE_ACCOUNT ERR -ACTIVATION- pre Est");
                     Logger.logDebugMessage("activation pre est acid:"+accountID+" h:"+height);
                    if(otlovforcerepair){
                       Logger.logDebugMessage("\nMG_CREATE_ACCOUNT ERR -ACTIVATION- Otlowe:"+otlovforcerepair+" wtf?");
                    }
                }
                }
                        
            }
        //--
//        try (PreparedStatement activation = conn.prepareStatement("insert into activation(soft_id, height) values (?,?)")) {
//            activation.setLong(1, accountID);
//            activation.setInt(2, height);
//            activation.executeUpdate();
//        }
    }


    private void createNetwork(long receiverID, long senderID, int stamp, int height) throws SQLException {
        Long receiverIDObj = receiverID;
        if (networkBooster.containsKey(receiverIDObj)) {
            return;
        }
        boolean receiver = false;
        try (PreparedStatement statement = conn.prepareStatement("select id from soft where id=? limit 1")) {
            statement.setLong(1, receiverID);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    receiver = true;
                }
            }
        }
        if (stamp == 0) { // Genesis block
            Long senderIDObj = senderID;
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


    public long  _getGenesEm(){
        long gem=-1;
        try {
            gem= getGenesisEmission();
        } catch (SQLException ex) {
            return gem;
            //java.util.logging.Logger.getLogger(SoftMG.class.getName()).log(Level.SEVERE, null, ex);
        }
        return gem;
    }
    private long getGenesisEmission() throws SQLException {
        long retval = 0l;

        try (PreparedStatement statement = conn.prepareStatement("SELECT balance FROM SOFT where id=? limit 1")) {  
            statement.setLong(1, Genesis.CREATOR_ID);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    retval = rs.getLong(1);
                }
            }
        }
        return retval;
    }
   

    private SoftMGs getMetricsForAccount(long accountID, int stamp) throws SQLException {
        SoftMGs metrics = new SoftMGs();
        metrics.setBeforeStamp(stamp);
        metrics.setAfterStamp(stamp);
        metrics.setGenesisEmission(getGenesisEmission());
        try (PreparedStatement statement = conn.prepareStatement("select id,parent_id,amount,balance,last,hold,last_forged_block_height from soft where id=? limit 1")) {
            statement.setLong(1, accountID);
            try (ResultSet rs = statement.executeQuery()) {
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
            }
        }
        return metrics;
    }


    private List<SMGBlock.Payout> insertBlock(final long blockID, int height, long fee, int stamp, long creatorID, boolean withFinishedState) throws SQLException {
        boolean hasTransaction = false;
        try (PreparedStatement query = conn.prepareStatement("select id from block where id=? and height=? limit 1")) {
            query.setLong(1, blockID);
            query.setLong(2, height);
            try (ResultSet rs = query.executeQuery()) {
                while (rs.next()) {
                    hasTransaction = true;
                }
            }
        }

        if (hasTransaction) {
            List<SMGBlock.Payout> retval = new ArrayList<>();
            try (PreparedStatement request = conn.prepareStatement("select block_id,txid,amount,to_id from force where not tech and block_id=?")) {
                request.setLong(1, blockID);
                try (ResultSet reqres = request.executeQuery()) {
                    while (reqres.next()) {
                        SMGBlock.Payout payout = new SMGBlock.Payout();
                        payout.setBlockID(reqres.getLong(1));
                        payout.setTxID(reqres.getString(2) != null ? reqres.getLong(2) : null);
                        payout.setHeight(height);
                        payout.setAmount(reqres.getLong(3));
                        payout.setToID(reqres.getLong(4));
                        retval.add(payout);
                    }
                }
            }
            return retval;
        }
        int count=-1;
        try (PreparedStatement statement = conn.prepareStatement("insert into block (id, height, fee, stamp, creator_id" + (withFinishedState ? ", accepted" : "") + ") values (?,?,?,?,?" + (withFinishedState ? ",true" : "") + ")")) {
            statement.setLong(1, blockID);
            statement.setLong(2, height);
            statement.setLong(3, fee);
            statement.setInt(4, stamp);
            statement.setLong(5, creatorID);
            count = statement.executeUpdate();
        }catch(Exception e){
            Logger.logErrorMessage("insert SMblock i:11d#1 er:"+e);
            popLastrepairBlock(height);
            throw new SQLException("insert SMblock i:11c#1 er:"+e);
            
        }
        if (count < 1) {
            throw new SQLException(ERROR_ALREADY);
        }
        boolean shouldSetLastForgedBlockHeight = height >= Constants.HOLD_ENABLE_HEIGHT - Constants.HOLD_RANGE;
        if (shouldSetLastForgedBlockHeight && withFinishedState) {
            setLastForgedBlockHeight(creatorID, height);
        }
        if (withFinishedState) { // Empty block
        }
        return null;
    }


    private List<SMGBlock.Payout> getUnpayedSoftMGTransactions(int height, int limit) throws SQLException {
        if(Bened.getBlockchainProcessor().isDownloading()){
            return new ArrayList<>();
        }
        
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
        try (ResultSet rs = query.executeQuery()) {
            while (rs.next()) {
                long currentID = rs.getLong(1);
                if (!blocksForSelect.containsKey(currentID)) {
                    blocksForSelect.put(rs.getLong(1), rs.getInt(2));
                }
                if (!hasTransaction) {
                    hasTransaction = true;
                }
            }
        }
        query.close();
        if (hasTransaction) {
            List<SMGBlock.Payout> retval = new ArrayList<>();
                                           //conn.prepareStatement("select height                       from force where stxid IN ("+ getLineOfQs(revertedsoftMGTransactions.size())+")" )) {
            try(PreparedStatement pstmt =  conn.prepareStatement("select block_id,txid,amount,to_id,height from force where not tech and block_id  IN ("+ getLineOfQs(blocksForSelect.entrySet().size())+") and stxid is null")) {
                        int ind=0;
                        for(Entry<Long, Integer> block : blocksForSelect.entrySet()) {
                            pstmt.setLong(++ind, block.getKey());
                        }
                        try (ResultSet reqres = pstmt.executeQuery()) {
                            while (reqres.next()) {
                                SMGBlock.Payout payout = new SMGBlock.Payout();
                                payout.setBlockID(reqres.getLong(1));
                                payout.setTxID(reqres.getString(2) != null ? reqres.getLong(2) : null);
                                payout.setAmount(reqres.getLong(3));
                                payout.setToID(reqres.getLong(4));
                                payout.setHeight(reqres.getInt(5));
                                retval.add(payout);
                            }
                        }
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
            try( PreparedStatement statement = conn.prepareStatement("select last from soft where id=? limit 1");){
                statement.setLong(1, toID);
                try(ResultSet rs = statement.executeQuery();){
                    while (rs.next()) {
                        last = rs.getInt(1);
                    }
                }//rs.close();
            }//statement.close();
            int count =0;
            try( PreparedStatement statement = conn.prepareStatement(amount > 0
                    ? "insert into force (block_id, txid, amount, to_id, height, last ) values (?,?,?,?,?,?)"
                    : "insert into force (block_id, txid, amount, to_id, height, last, tech) values (?,?,?,?,?,?,?)");){
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
            count = statement.executeUpdate();
            }
            if (count < 1) {
                throw new SQLException(ERROR_ALREADY);
            }
        } catch (SQLException ex) {
            System.out.println("!!! ahtung ERROR insert force: "+ex);
        }
    }

 
 private boolean repairbreackblock(SMGBlock.Transaction trx, int _height, boolean otlov)throws SQLException{
     if(otlov || knowcurveblockheight.contains(_height)){

               Long _stxid = null;
               boolean _found = false;
               if (trx.getSoftMGTxID() == null){
                                                                                        //not tech and stxid is null and
                   try (PreparedStatement request = conn.prepareStatement("select stxid from force where  txid is null and amount=? and to_id=? limit 1")) {
                    request.setLong(1, trx.getAmount());
                    request.setLong(2, trx.getReceiver());
                    try (ResultSet rs = request.executeQuery()) {
                    while (rs.next()) {
                      _stxid = rs.getString(1) != null ? rs.getLong(1) : null;
                      _found = true;
                        }
                        }
                    }

                   if(!_found){
                        insertForce(trx.getSoftMGBlockID(), null, trx.getAmount(), trx.getReceiver(),  _height-1) ;
                        
                   }           
                    try (PreparedStatement statement = conn.prepareStatement("update force set amount=?, tech=?, stxid=?  where  stxid=? and amount=? and to_id=? and txid is null")) {
                        statement.setLong(1, trx.getAmount());
                        statement.setBoolean(2, false);
                        statement.setNull(3, Types.NULL);  //trx.getID()
                        statement.setLong(4, _stxid!=null?_stxid : trx.getID());
                        statement.setLong(5, trx.getAmount());
                        statement.setLong(6, trx.getReceiver());
                        int count = statement.executeUpdate();
                        if(count!=1){
                            Logger.logDebugMessage("AHTUNG NO REPAIR !!! upd c="+count);
                            System.out.println("AHTUNG NO REPAIR !!! upd c="+count+" SoftMGTxID==nulll");
                            if(count>1){
                                try (PreparedStatement stmtd = conn.prepareStatement("delete from force where amount=? and to_id=? and txid is null and stxid is null limit 1")) {
                                    stmtd.setLong(1, trx.getAmount());
                                    stmtd.setLong(2, trx.getReceiver());
                                    count = stmtd.executeUpdate();
                                    System.out.println(" delete meny 1 trx t0 if full count:"+count);
                                    povtor=0;
                                    return count==1;
                                }catch(Exception e){
                                    System.out.println("err ft5 e:"+e);
                                }
                            }
                        }
                    }
                     
                   
                   
               }else{
                                                                                                    // not tech and stxid is null and  == where and amount=?
                   try (PreparedStatement request = conn.prepareStatement("select stxid from force where  txid=?  and to_id=? limit 1")) {
                        request.setLong(1, trx.getSoftMGTxID());
                     //   request.setLong(2, trx.getAmount());
                        request.setLong(2, trx.getReceiver());
                        try (ResultSet rs = request.executeQuery()) {
                        while (rs.next()) {
                            _stxid = rs.getString(1) != null ? rs.getLong(1) : null;
                            _found = true;
                        }
                        }
                    }
                   if(!_found){
                       insertForce(trx.getSoftMGBlockID(), trx.getSoftMGTxID(), trx.getAmount(), trx.getReceiver(),  _height-1) ;
                       
                   }           
                    try (PreparedStatement statement = conn.prepareStatement("update force set amount=?, tech=?, stxid=?  where  txid=? and to_id=?")) {
                        statement.setLong(1, trx.getAmount());
                        statement.setBoolean(2, false);
                        statement.setNull(3, Types.BIGINT);
                        statement.setLong(4, trx.getSoftMGTxID());
                        statement.setLong(5, trx.getReceiver());
                        int count = statement.executeUpdate();
//                        System.out.println("upd c="+count);
                        if(count>1){
                                try (PreparedStatement stmtd = conn.prepareStatement("delete from force where  txid=? and to_id=? limit 1")) {
                                    stmtd.setLong(1, trx.getSoftMGTxID());
                                    stmtd.setLong(2, trx.getReceiver());
                                    count = stmtd.executeUpdate();
                                    System.out.println(" delete meny 1 trx t1 if full count:"+count);
                                    povtor=0;
                                    return count==1;
                                }
                            }
                    }
                    }
            Logger.logDebugMessage("repaired"); 
            System.out.println( "SoftMGTxID="+trx.getSoftMGBlockID()+" REPAIRED");
            return true;
            }
      Logger.logDebugMessage("No repaired");
      System.out.println("!! no reair !!");
     return false;
 }
 
    public boolean checkForce_sobs(SMGBlock.Transaction trx){
        try { 
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
            try (PreparedStatement request = conn.prepareStatement("select stxid from force where not tech and txid is null and amount=? and to_id=? limit 1")) {
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
                return true;
            }
            if (found && stxid != null) {
                if (stxid == trx.getID()) {
                    return true;
                }
            }
        } else {
            try (PreparedStatement request = conn.prepareStatement("select stxid from force where not tech and txid=? and amount=? and to_id=? limit 1")) {
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
                return true;
            }
            if (found && stxid != null) {
                if (stxid == trx.getID()) {
                    return true;
                }
            }
        }
        
        } catch (SQLException ex) {
           return false;
        }
        return false;
    }
 
//    public boolean checkForce(SMGBlock.Transaction trx, boolean newer){
//        try{
//            return checkForce(trx);
//        }catch(Exception e){
//            return false;
//        }
//    }
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
            try (PreparedStatement request = conn.prepareStatement("select stxid from force where not tech and txid is null and amount=? and to_id=? limit 1")) {
                request.setLong(1, trx.getAmount());
                request.setLong(2, trx.getReceiver());
                try (ResultSet rs = request.executeQuery()) {
                    while (rs.next()) {
                        stxid = rs.getLong(1); //rs.getString(1) != null ? rs.getLong(1) : null;
                        found = true;
                    }
                }
            }
            if (found && (stxid == 0 || stxid== null)) { 
                try (PreparedStatement statement = conn.prepareStatement("update force set stxid=? where not tech and stxid is null and txid is null and amount=? and to_id=?")) {
                    statement.setLong(1, trx.getID());
                    statement.setLong(2, trx.getAmount());
                    statement.setLong(3, trx.getReceiver());
                    count = statement.executeUpdate();
                }

            }
            if (found && (stxid != 0 && stxid!= null)) {
                if (stxid == trx.getID()) {
                    count = 1;
                }
            }
        } else { //trx.getSoftMGTxID() != null
            try (PreparedStatement request = conn.prepareStatement("select stxid from force where not tech and txid=? and amount=? and to_id=? limit 1")) {
                request.setLong(1, trx.getSoftMGTxID());
                request.setLong(2, trx.getAmount());
                request.setLong(3, trx.getReceiver());
                try (ResultSet rs = request.executeQuery()) {
                    while (rs.next()) {
                        stxid = rs.getLong(1); //rs.getString(1) != null ? rs.getLong(1) : null;
                        found = true;
                    }
                }
            }
            if (found &&  (stxid == 0 || stxid== null)) { // == null
                try (PreparedStatement statement = conn.prepareStatement("update force set stxid=? where not tech and stxid is null and txid=? and amount=? and to_id=?")) {
                    statement.setLong(1, trx.getID());
                    statement.setLong(2, trx.getSoftMGTxID());
                    statement.setLong(3, trx.getAmount());
                    statement.setLong(4, trx.getReceiver());
                    count = statement.executeUpdate();
                }
            }
            if (found &&  (stxid != 0 && stxid != null)) {
                if (stxid == trx.getID()) {
                    count = 1;
                }
            }
        }
        if(count!=1){
            Logger.logDebugMessage("---vmgcheckerr height="+getMGHeight());
        }
        if(!(count==1) && !(povtor==trx.getID()) ){
            povtor=trx.getID();
            int _height =getMGHeight();
//            if(!otlovtrx.containsKey(trx.getID())){
//                otlovtrx.put(trx.getID(),0);
//            }
            if(repairbreackblock(trx, _height,  (otlovforcerepair || true) ) ){
                count = checkForce(trx)?1:0;
//                falbloc.add(_height);
//                otlovtrx.remove(trx.getID());
                Logger.logDebugMessage("\n------\ncheck repair! count:"+count+" h:"+_height+"  trx="+povtor+ "  softtxid="+trx.getID()+" ot:"+Crypto.rsEncode( trx.getSender())+" dlya:"+Crypto.rsEncode(trx.getReceiver()));
            }else{
//                otlovtrx.put(trx.getID(),otlovtrx.get(trx.getID())+1);
            }
        }
        return count == 1;
    }
    static long povtor = 0;
    HashSet<Integer> falbloc =new HashSet<>();
    
    
    

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


    private void checkBlockIsSuccess(Set<Long> blockIDs) throws SQLException {
        //Set<Long> updId= new HashSet<>();
        try(PreparedStatement pstmt = conn.prepareStatement("select DISTINCT block_id from force where not tech and block_id IN ("+ getLineOfQs(blockIDs.size())+") and stxid is null") ){
            int ind=0;
            for(Long ID : blockIDs) {    
                pstmt.setLong(++ind, ID);
            }
            try(ResultSet rs = pstmt.executeQuery() ){
                
                while (rs.next()) {
                    blockIDs.remove(rs.getLong(1));
                }   
            }catch(Exception e){
                System.out.println("e:"+e);
            }
        }
        if(!blockIDs.isEmpty()){
            try(PreparedStatement pstmt = conn.prepareStatement("update block set accepted=true where id IN ("+ getLineOfQs(blockIDs.size())+") and accepted=false"); ){
                int ind=0;
                for(Long ID : blockIDs) {    
                    pstmt.setLong(++ind, ID);
                }
                pstmt.executeUpdate();
            }
        }      

    }


    private void insertHoldTransfer(long account, long amount, int height) throws SQLException {
        try {
            try (PreparedStatement updater = conn.prepareStatement("insert into hold_transfer values (?,?,?)")) {
                updater.setLong(1, account);
                updater.setLong(2, amount);
                updater.setInt(3, height);
                updater.executeUpdate();
            }
        } catch (SQLException ex) {
            throw ex;
        }
    }

    private void setLastForgedBlockHeight(long account, int height) throws SQLException {
        try (PreparedStatement updater = conn.prepareStatement("update soft set last_forged_block_height=? where id=?")) {
            updater.setInt(1, height);
            updater.setLong(2, account);
            updater.executeUpdate();
        }
    }

    private void updateHold(long ID, long diff) throws Exception {
        try {
            try (PreparedStatement updater = conn.prepareStatement("update soft set hold=hold+? where id=?")) {
                updater.setLong(1, diff);
                updater.setLong(2, ID);
                updater.executeUpdate();
            }
        } catch (SQLException ex) {
            throw ex;
        }
    }
     private void _update(ArrayList<Long> IDs, TreeMap<Long, Long> DIFFs, HashMap<Long, Integer> STAMPs) throws Exception {
        List<HeapStore> heaps = new ArrayList<>();

        try (PreparedStatement statement = conn.prepareStatement("WITH LINK(ID, PARENT_ID, LEVEL) AS (\n"
                        + "    SELECT ID, PARENT_ID, 0 FROM SOFT WHERE ID IN ("+ getLineOfQs(IDs.size())+")\n"
                        + "    UNION ALL\n"
                        + "    SELECT SOFT.ID, SOFT.PARENT_ID, LEVEL + 1\n"
                        + "    FROM LINK INNER JOIN SOFT ON LINK.PARENT_ID = SOFT.ID AND LINK.LEVEL < "+(Bened.getBlockchain().getHeight()<Constants.change_evendek22? Constants.affil_struct : Constants.affil_struct*100)+"\n" //// было 88
                        + " )\n"
                        + " select \n"
                        + "   link.id,\n"
                        + "   link.parent_id,\n"
                        + "   link.level\n"
                        + "from link");){
            int ind=0;
            for(Long ID : IDs) {    
                statement.setLong(++ind, ID);
            }            
            try(ResultSet rs = statement.executeQuery();){
                while (rs.next()) {
                    HeapStore item = new HeapStore(rs.getLong(1), rs.getLong(2), rs.getLong(3));
                    if (item.getLevel() < 1) {
                        continue;
                    }
                    heaps.add(item);
                }
            }
        }catch(Exception e){
            System.out.println(" _upd new r :"+e);
        }
        if(!heaps.isEmpty()){
            int heapind=0;
            
                for(Long account: IDs){
                    heapind=0;
                    for(HeapStore item : heaps) {
                        if(item.getBasic()!=account)continue;
                        heapind++;
                    }
                    if(heapind<1)continue;
                    try(PreparedStatement pstmt = conn.prepareStatement("update soft set amount=amount+? where id in ("+ getLineOfQs(heapind)+")" )) {
                    int ind=0;
                    pstmt.setLong(++ind, DIFFs.get(account));
                    for(HeapStore item : heaps) {
                        if(item.getBasic()!=account)continue;
                        pstmt.setLong(++ind, item.getBasic() );
                    }
                    heapind = pstmt.executeUpdate();
                }catch(Exception e){
                   System.out.println("ind="+heapind +" er:"+e); 
                }
//                        System.out.println("ind="+ind);
            }

            if (STAMPs != null) {
                try(PreparedStatement updater = conn.prepareStatement("update soft set balance=balance+?, last=? where id=?")){
                    for (Long account : IDs) {
                        updater.setLong(1, DIFFs.get(account));
                        updater.setLong(2, STAMPs.get(account));
                        updater.setLong(3, account);
                        updater.addBatch();
                    }
                    try {
                        int[] result = updater.executeBatch(); //(4)
                        conn.commit();
                    } catch (BatchUpdateException ex) {
                        Logger.logErrorMessage("SoftMG _updater on if batch onif000:"+ex);
                        conn.rollback();
                    }
                }    
            } else {
                try(PreparedStatement updater = conn.prepareStatement("update soft set balance=balance+? where id=?")){
                    for (Long account : IDs) {
                        updater.setLong(1, DIFFs.get(account));
                        updater.setLong(2, account);
                        updater.addBatch();
                    }
                    try {
                        int[] result = updater.executeBatch(); //(4)
                        conn.commit();
                    } catch (BatchUpdateException ex) {
                        Logger.logErrorMessage("SoftMG _updater on if batch onif001:"+ex);
                        conn.rollback();
                    }
                }
            }
        }
    }

  
    
    
    private void update_old(long ID, long diff, Integer stamp) throws Exception {
        List<HeapStore> heaps = new ArrayList<>();

        try {
//            try (PreparedStatement values = conn.prepareStatement("set @value1 = ?")) {
//                values.setLong(1, ID);
//                values.executeUpdate();
                try (PreparedStatement statement = conn.prepareStatement("WITH LINK(ID, PARENT_ID, LEVEL) AS (\n"
                        + "    SELECT ID, PARENT_ID, 0 FROM SOFT WHERE ID = ?\n"
                        + "    UNION ALL\n"
                        + "    SELECT SOFT.ID, SOFT.PARENT_ID, LEVEL + 1\n"
                        + "    FROM LINK INNER JOIN SOFT ON LINK.PARENT_ID = SOFT.ID AND LINK.LEVEL < "+(Bened.getBlockchain().getHeight()<Constants.change_evendek22? Constants.affil_struct : Constants.affil_struct*100)+"\n" //// было 88
                        + " )\n"
                        + " select \n"
                        + "   link.id,\n"
                        + "   link.parent_id,\n"
                        + "   link.level\n"
                        + "from link");){
                        statement.setLong(1, ID);
                    try(ResultSet rs = statement.executeQuery();){
                    while (rs.next()) {
                        HeapStore item = new HeapStore(rs.getLong(1), rs.getLong(2), rs.getLong(3));
                        if (item.getLevel() < 1) {
                                continue;
                            }
                        heaps.add(item);
                    }
                    }
                }
            //}
            //"update soft set amount=amount+? where id in (";
            if(!heaps.isEmpty()){
             try(PreparedStatement pstmt = conn.prepareStatement("update soft set amount=amount+? where id in ("+ getLineOfQs(heaps.size())+")" )) {
                        int ind=0;
                        pstmt.setLong(++ind, diff);
                        for(HeapStore item : heaps) {
                            pstmt.setLong(++ind, item.getBasic() );
                        }
                        ind = pstmt.executeUpdate();
//                        System.out.println("ind="+ind);
                    }
            }
//            Conc conc = null;
//            for (HeapStore item : heaps) {
//                if (item.getLevel() < 1) {
//                    continue;
//                }
//                if (conc == null) {
//                    conc = new Conc();
//                }
//                if (!conc.add(item.getBasic())) {
//                    try (PreparedStatement updater = conn.prepareStatement(conc.query())) {
//                        updater.setLong(1, diff);
//                        updater.executeUpdate();
//                    }
//                    conc = null;
//                }
//            }
//            if (conc != null) {
//               try (PreparedStatement updater = conn.prepareStatement(conc.query())) {
//                        updater.setLong(1, diff);
//                        updater.executeUpdate();
//                    }
//                conc = null;
//            }

            if (stamp != null) {
                try(PreparedStatement updater = conn.prepareStatement("update soft set balance=balance+?, last=? where id=?")){
                    updater.setLong(1, diff);
                    updater.setLong(2, stamp);
                    updater.setLong(3, ID);
                    updater.executeUpdate();
                }
            } else {
                try(PreparedStatement updater = conn.prepareStatement("update soft set balance=balance+? where id=?")){
                    updater.setLong(1, diff);
                    updater.setLong(2, ID);
                    updater.executeUpdate();
                }
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
                SMGBlock.SoftMGParams softMGParams = SoftMG.getSoftmgParams(transaction);
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
            } catch (NumberFormatException | ParseException ex) {
                return new SMGBlock.SoftMGParams();
            }
        }
        return retval;
    }

    private static final Object LOCK_OBJECT = new Object();
   private void checkSoftMGBlockIsValid(SMGBlock softBlock)  {
        init();
        long ID = 0l;
        int stamp = 0;
        int maxHeight = 0;
        long creatorID = 0l;
        boolean hasBlock = false;
    try(PreparedStatement statement = conn.prepareStatement("select id,stamp,creator_id from block where height=? limit 1");){
        statement.setLong(1, softBlock.getHeight());
        try(ResultSet rs = statement.executeQuery();){
        while (rs.next()) {
            hasBlock = true;
            ID = rs.getLong(1);
            stamp = rs.getInt(2);
            creatorID = rs.getLong(3);
        }
        }//rs.close();
    }   catch (SQLException ex) {
               Logger.logErrorMessage(SoftMG.class.getName()+"+softmg tt0+", ex);
        }//statement.close();
        if (!hasBlock) {
            try(PreparedStatement statement = conn.prepareStatement("select max(height) from block");
            ResultSet rs = statement.executeQuery();){
            while (rs.next()) {
                maxHeight = rs.getInt(1);
            }
            } catch (SQLException ex) {
                   Logger.logErrorMessage(SoftMG.class.getName()+"+softmg tt1+", ex);
            }//rs.close();
            //statement.close();
            if (maxHeight > 0) {
                
                if ((maxHeight + 1) != softBlock.getHeight()) {
                    commit();
                    System.out.println("try repair base bls:"+maxHeight+ " softbl:"+ softBlock.getHeight());
                    popLastrepairBlock(maxHeight);
                    commit();
                    
                    System.out.println("=========== LOOOSE START (INTERNAL DETECTOR) =============");
                    return;
                }
            }
            return;
        }
        if (ID == softBlock.getID() && stamp == softBlock.getStamp() && creatorID == softBlock.getGeneratorID()) {
            return;
        }
       commit();
        System.out.println("=========== LOOOSE START =============");
//        } catch (SQLException ex) {
//           Logger.logErrorMessage(SoftMG.class.getName()+"++", ex);
//        }
    }

    private boolean checkSoftMGBlockIsAccepted(SMGBlock softBlock) throws SQLException {
        boolean accepted = false;
        try (PreparedStatement statement = conn.prepareStatement("select accepted from block where id=? and height=? and stamp=? limit 1")) {
            statement.setLong(1, softBlock.getID());
            statement.setInt(2, softBlock.getHeight());
            statement.setInt(3, softBlock.getStamp());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    accepted = rs.getBoolean(1);
                }
            }
        }
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

    private CheckInternal checkInternal(SMGBlock softBlock) throws HGException {
        CheckInternal retvalue = new CheckInternal();
        boolean blockExists = false;
        if (softBlock == null) {
            return retvalue;
        }
        if (softBlock.getTransactions().isEmpty()) {
            return retvalue;
        }
        try {
            if (checkSoftMGBlockIsAccepted(softBlock)) {
                return retvalue;
            }
            if (!softBlock.getTransactions().isEmpty()) {
                retvalue.setHasTransactions(true);
            }
           List<SMGBlock.Payout> payret =  insertBlock(softBlock.getID(), softBlock.getHeight(), softBlock.getFee(), softBlock.getStamp(), softBlock.getGeneratorID(), false);  
            
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
            TreeMap<Long, Long> diffs = new TreeMap<>();
            HashMap<Long, Integer> stamps = new HashMap<>();
           

            if (holdEnabled) {
                for (SMGBlock.Transaction tx : allTransactionsDirectSort) { // We need to know if an arbitrary account has an outgoing transaction in this block
                    if (!senders.contains(tx.getSender()) && tx.getSender() != Genesis.CREATOR_ID) {
                        senders.add(tx.getSender());
                    }
                }
            }
            SMGComputator calculator = new SMGComputator(getGenesisEmission());
            for (SMGBlock.Transaction tx : allTransactionsReverseSort) {
                switch (tx.getType()) {
                    case ORDINARY -> {
                        if (!blockExists) {
                            if (!transactionsOrdinary.containsKey(tx.getSender())) {
                                transactionsOrdinary.put(tx.getSender(), tx);
                            }
                            if (!transactionsOrdinary.containsKey(tx.getReceiver())) {
                                transactionsOrdinary.put(tx.getReceiver(), tx);
                            }
                        }
                    }
                    case softMG -> {
                        if (tx.getSoftMGBlockID() == null && softBlock.getHeight() > 0) {
                            throw new HGException("SoftMGblock with wrong internal structure!");
                        }
                        transactionsSoftMG.add(tx);
                    }
                }
            }
            if (!blockExists) {
                boolean hasCoreTransaction = false;
                for (Map.Entry<Long, SMGBlock.Transaction> item : transactionsOrdinary.entrySet()) {
                        if (softBlock.getGeneratorID() == item.getKey()) {
                            SoftMGs metrics =  getMetricsForAccount(softBlock.getGeneratorID(), softBlock.getStamp());
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
                                
                                    insertForce(softBlock.getID(), null, metrics.getPayout(), softBlock.getGeneratorID(), softBlock.getHeight());
                               
                            }
                        } else {
                            SoftMGs metrics = getMetricsForAccount(item.getKey(), softBlock.getStamp());
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
                                 insertForce(softBlock.getID(), item.getValue().getID(), metrics.getPayout(), item.getKey(), softBlock.getHeight());
                                
                            }
                        }
                 
                }
                if (!hasCoreTransaction && softBlock.getFee() > 0l) {
                    SoftMGs metrics = getMetricsForAccount(softBlock.getGeneratorID(), softBlock.getStamp());
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
                            insertForce(softBlock.getID(), null, metrics.getPayout(), softBlock.getGeneratorID(), softBlock.getHeight());
                            
                        }
                    }
                }
               for (SMGBlock.Transaction item : allTransactionsDirectSort) {
                        if (item.getType() == SMGBlock.Type.ORDINARY) {
                           
                                createNetwork(item.getReceiver(), item.getSender(), softBlock.getStamp(), softBlock.getHeight());
                                addDiff(item.getReceiver(), item.getAmount() + calculator.get(item.getReceiver()), softBlock.getStamp(), diffs, stamps);
                                addDiff(item.getSender(), 0l - item.getAmount() + calculator.get(item.getSender()) - item.getFee(), softBlock.getStamp(), diffs, stamps);
                           
                        }
                
                }
                if (softBlock.getFee() > 0l) {
                    addDiff(softBlock.getGeneratorID(), softBlock.getFee() + calculator.get(softBlock.getGeneratorID()), softBlock.getStamp(), diffs, stamps);
                    
                }
                if (calculator.hasGenesisDiff()) {
                   
                        addDiff(Genesis.CREATOR_ID, calculator.getGenesisDiff(), softBlock.getStamp(), diffs, stamps);
                    
                }
            }
            transactionsSoftMGSorted = SMGBlock.reverse(SMGBlock.sort(transactionsSoftMG));
            for (SMGBlock.Transaction tx : transactionsSoftMGSorted) {
                if (softBlock.getHeight() == 0 || softBlock.getID() == Genesis.GENESIS_BLOCK_ID) {
                    
                        createNetwork(tx.getReceiver(), tx.getSender(), tx.getStamp(), 0);
                        addDiff(tx.getReceiver(), tx.getAmount(), tx.getStamp(), diffs, stamps);
                        addDiff(tx.getSender(), 0l - tx.getAmount() - tx.getFee(), tx.getStamp(), diffs, stamps);
                    
                } else {
                    if (tx.getSoftMGBlockID()!=null && !blocksForCheck.contains(tx.getSoftMGBlockID())) {
                        blocksForCheck.add(tx.getSoftMGBlockID());
                    }

                    // Bad transaction available HERE =====|
                    if (!ExcludesGMS.check(tx, softBlock.getHeight())) {
                        // ===================================[AUTOFUCK]=|
                        if (!( checkForce(tx) )) {
                                throw new HGException((softBlock.getHeight() + ": Genesis transaction wrong: " + tx.getID() + " > " + tx.getReceiver()) + " : " + tx.getAmount() + " \n" + tx.toString(), softBlock.getHeight());  
                        }
                    }

                }
            }
                ArrayList<Long> accounts = new ArrayList<>();
                for (Long account : diffs.keySet()) {
                    if (holdEnabled && account != Genesis.CREATOR_ID) {
                        boolean isOnHold = metricsMap.get(account) != null && metricsMap.get(account).isOnHoldAtHeight(softBlock.getHeight());
                        boolean shouldTransferHold = (!isOnHold || senders.contains(account)) && metricsMap.get(account) != null && metricsMap.get(account).getHold() > 0;
                        if (shouldTransferHold) {//1
                            addDiff(account, metricsMap.get(account).getHold(), null, diffs, stamps);
                            updateHold(account, -metricsMap.get(account).getHold());
                            insertHoldTransfer(account, metricsMap.get(account).getHold(), softBlock.getHeight());
                        }
                        if (isOnHold && !senders.contains(account)) { //1
                            updateHold(account, diffs.get(account));
                        } else {//1
                            accounts.add(account);
                            //update(account, diffs.get(account), stamps.get(account));
                        }
                    } else { //1
                        accounts.add(account);
                        //update(account, diffs.get(account), stamps.get(account));
                    }
                }
                if(!accounts.isEmpty()){
                    _update(accounts, diffs, stamps);
                }
                
                
//            for (Long ID : blocksForCheck) {
//                if (ID == null) {
//                    continue;
//                }
                
                blocksForCheck.add(softBlock.getID());
                checkBlockIsSuccess(blocksForCheck);
               
            //}
            //checkBlockIsSuccess(softBlock.getID());
           
            if (softBlock.getHeight() >= Constants.HOLD_ENABLE_HEIGHT - Constants.HOLD_RANGE) {
                setLastForgedBlockHeight(softBlock.getGeneratorID(), softBlock.getHeight());
            }
            int limit = Constants.MAX_NAGRADNIH*10 - retvalue.getPayouts().size();
            if(limit<0){
                System.out.println("neverniy ostatok pustyh nagradnyh mest ="+limit);
                throw new HGException("softMG f77#3 neverniy ostatok pustyh nagradnyh mest ="+limit);
            }
            if(limit>2){
                limit=limit-1;
                List<SMGBlock.Payout> finishPayouts = getUnpayedSoftMGTransactions(softBlock.getHeight(), limit) ;
                retvalue.getPayouts().addAll(finishPayouts);
            }
        } catch (Exception ex) {
            Logger.logErrorMessage(ex.getMessage(), ex); // More details on exception's source
            if (ex.getMessage().contains("Genesis transaction wrong") || ex.getMessage().contains("\"FORCE\"")) {
                System.out.println("!!checkinternal -- "+ex.getMessage());
            }
            throw new HGException(ex.getMessage());
        }
        commit();
        return retvalue;
    }

    private void ressurectDatabaseIfNeeded(int height) throws SQLException {
        boolean allRight = true;

   try (PreparedStatement statement = conn.prepareStatement("select id,stamp,height from block where height=? ")) {
            statement.setInt(1, height - 1);
            try (ResultSet rs = statement.executeQuery()) {
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
            }
        }

        if (allRight) {
            return;
        }
        commit();
    }

    
    public List<SMGBlock.Payout> check(SMGBlock softBlock, int height, SMGBlock softBlockIncognito) throws HGException {
        try {
            init();
            try {
                ressurectDatabaseIfNeeded(height);
            } catch (SQLException ex) {
                Logger.logErrorMessage(" +++ DATABASE INCONSISTENCY DETECTED +++");
            }

            synchronized (LOCK_OBJECT) {

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
                        commit();
                    } catch (SQLException ex) {
                        rollback();
                        commit();
                    }
                    return new ArrayList<SMGBlock.Payout>();
                }
                try {
                    checkSoftMGBlockIsValid(softBlock);
                    CheckInternal checkInternalRetval = checkInternal(softBlock);

                    trimDerivedTables();
                    commit();

 
                    return checkInternalRetval.getPayouts();

                } catch (HGException ex) {
                   rollback();
                    if (ex.hasHeight()) {
                        System.out.println(" === MIRACLE EXCHANGE ===                   (height: " + ex.getHeight() + ")");
                    }
                    commit();
                    throw ex;
                }
            }
        } finally {
            commit();
        }
    }

    
    public boolean canReceive(SMGBlock.Transaction trx) {
        synchronized (LOCK_OBJECT) {
            init();
            try {
                return checkAnnounceCanReceive(trx);
            } catch (SQLException ex) {
                return false;
            } finally {
                commit();
            }
        }
    }

    
    public SoftMGs getMetrics(long accountID) {
        synchronized (LOCK_OBJECT) {
            init();
            SoftMGs metrics = new SoftMGs();
            try {
               try (PreparedStatement statement = conn.prepareStatement("select amount, balance, last, hold, last_forged_block_height from soft where id=?")) {
                    statement.setLong(1, accountID);
                    try (ResultSet rs = statement.executeQuery()) {
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
                    }
                }
                metrics.setGenesisEmission(getGenesisEmission());
                metrics.calculatePyoutSet();
            } catch (SQLException ex) {
                Logger.logErrorMessage("softMg, getMetrics err:"+ex);
            }
            //finally {
            //    commit();
            //}
            return metrics;
        }
    }

    public HashMap<Long, SoftMGs>  getMetricsPacketsOfId(HashMap<Long, String> mgens ) {
        HashMap<Long, SoftMGs> mapmerts =new HashMap<>();
        if(mgens.isEmpty())return mapmerts;
        synchronized (LOCK_OBJECT) {
            init();
            Set<Long> generators = new HashSet<>();
            for (Map.Entry<Long, String> _gm : mgens.entrySet()) {
                generators.add(_gm.getKey());
            }
            try {
               try (PreparedStatement statement = conn.prepareStatement("select amount, balance, last, hold, last_forged_block_height, id from soft where id IN ("+ getLineOfQs(generators.size())+")" )) {
                    int ind=0;
                    for (Iterator<Long> iterator = generators.iterator(); iterator.hasNext();) {
                        statement.setLong(++ind, iterator.next() );
                    }
                    try (ResultSet rs = statement.executeQuery()) {
                        long _genim = getGenesisEmission();
                        while (rs.next()) {
                            SoftMGs metrics = new SoftMGs();
                            metrics.setAmount(rs.getLong(1));
                            metrics.setBalance(rs.getLong(2));
                            metrics.setBeforeStamp(rs.getInt(3));
                            long time = System.currentTimeMillis() / 1000 ;
                            long diff = metrics.getBeforeStamp();
                            diff = diff + Constants.EPOCH_BEGINNING/1000; 
                            metrics.setAfterStamp((int)( (time - diff) + metrics.getBeforeStamp()) );
                            metrics.setHold(rs.getLong(4));
                            metrics.setLastForgedBlockHeight(rs.getInt(5));
                            metrics.setAccountID(rs.getLong(6));
                            metrics.setGenesisEmission(_genim);
                            mapmerts.put(rs.getLong(6), metrics);
                        } 
                    }
                }
            } catch (SQLException ex) {
                Logger.logErrorMessage("SoftMG getmetricsPackets err:"+ex);
            }
            return mapmerts;
        }
    }
   
    
    static String getLineOfQs(int num) {
    // Joiner and Iterables from the Guava library
    String n = "?";
        for (int i = 0; i < num-1; i++) {
            n=n+",?";
        }
    return n;
    }
    ////////////
    
    public boolean isZeroblockFixed() {
        if (!zeroblockFixed) {
            init();
            Integer fixed = getParameter(ZEROBLOCK_FIXED);
            if (fixed != null) {
                zeroblockFixed = true;
            }
        }
        return zeroblockFixed;
    }

    public void zeroblockFixed() {
        init();
        setParameter(ZEROBLOCK_FIXED, 0);
        commit();
    }

    
    public void shutdown() {
        
        try {
            commit();
            Statement stmt = conn.createStatement();
            stmt.execute("SHUTDOWN");
            //stmt.execute("SHUTDOWN COMPACT");
            //stmt.execute("SHUTDOWN DEFRAG");
            conn.close();
            Logger.logShutdownMessage("Database softMG shutdown completed");
        } catch (SQLException e) {
            Logger.logShutdownMessage(e.toString(), e);
        }
    }

    
    public Connection getConnection() {
        init();
        return conn;
    }

    
    public long getFixedFee(Transaction _trx) {
        long amount = _trx.getAmountNQT();
          int subtype= _trx.getType().getSubtype();
        if(_trx.getType()==TransactionType.Hashing.HASHTINT_ASSIGNMENT){
          // byte[] hashmesagedata= ((Appendix.AbstractHashMessage)_trx.getAppendages(appendix -> (appendix instanceof Appendix.AbstractHashMessage), false).get(0)).HashMesageData();
           long _amountNQT = (((HashTintAssignment)_trx.getAttachment()).getMySize()-4) *20000L;//Long.parseLong( String.valueOf( amount) );
           long _feeNQT = (long) (_amountNQT * 20)/100 ; // 20%
           amount =  _amountNQT;
         }
        return getFixedFee(amount, _trx.getType());
    }
    
    public long getFixedFee(long amount, TransactionType tType) {
        long fee = (long) (amount * 0.01 <= 150000 ? 150000 : (amount * 0.01 >=100000000 ? 100000000 : amount * 0.01) );             
        // fixed 150000 or 100 000000

        if(tType==TransactionType.Hashing.HASHTINT_ASSIGNMENT){   // hash fee
            fee = (long) (amount * 0.2);     
        }
        return fee<1?150000:fee;
    }
    
    // m 00 getlastFB
    public int getlastblockAccountId(long accountId) {
        int lastforgedblock = 0;             
        try (PreparedStatement statement = conn.prepareStatement("select last_forged_block_height from soft where id=? limit 1")) {
                    statement.setLong(1, accountId);
                    try (ResultSet rs = statement.executeQuery()) {
                        while (rs.next()) {
                            lastforgedblock=rs.getInt(1);
                        } 
                    }
                } catch (SQLException ex) {
            Logger.logErrorMessage("softMG crash getLastBlockAccountId err:"+ex);
        }
        return lastforgedblock;
    }
    
    static int prepairblcount=0;
    public void popLastrepairBlock(int softMH) {
        final Block lastBlock = BlockchainImpl.getInstance().getLastBlock();
        final int currentHeight = BlockchainImpl.getInstance().getHeight();
        networkBooster.clear();
        init();
        if(softMH!=currentHeight && prepairblcount<10){
            prepairblcount++;
            int rb= softMH>currentHeight?currentHeight:softMH;
            List<? extends Block> ogg = Bened.getBlockchainProcessor().popOffTo(rb);
            popLastrepairBlock(rb);
           return;
        }
        prepairblcount=1;
        boolean holdEnabled = currentHeight >= Constants.HOLD_ENABLE_HEIGHT;
        boolean shouldSetLastForgedBlockHeight = currentHeight >= Constants.HOLD_ENABLE_HEIGHT - Constants.HOLD_RANGE;
        synchronized (LOCK_OBJECT) {
            final List<Long> accountsToDelete = new ArrayList<>();
            final TreeMap<Long, Long> diffs = new TreeMap<>();
            final Set<Long> senders = new HashSet<>();
            List<Long> revertedsoftMGTransactions = new ArrayList<>();
            try {
                            
                if (lastBlock.getTransactions() != null && !lastBlock.getTransactions().isEmpty()) {
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
               
                try(PreparedStatement request = conn.prepareStatement("select to_id,amount,height,last from force where height=?");){
                request.setLong(1, currentHeight);
                try(ResultSet rs = request.executeQuery();){
                while (rs.next()) {
                    SMGBlock.Payout force = new SMGBlock.Payout();
                    force.setToID(rs.getLong(1));
                    force.setAmount(rs.getLong(2));
                    force.setHeight(rs.getInt(3));
                    force.setLast(rs.getInt(4));
                    forces.add(force);
                }
                }
                }
                if (shouldSetLastForgedBlockHeight) {
                    int lastForgedBlockHeight = 0;

                    try(PreparedStatement request = conn.prepareStatement("select max(height) from block where creator_id=? and height<? ");){
                    request.setLong(1, lastBlock.getGeneratorId());
                    request.setInt(2, currentHeight);
                    try(ResultSet rs = request.executeQuery();){
                    while (rs.next()) {
                        lastForgedBlockHeight = rs.getInt(1);
                    }
                    }
                    } catch (SQLException ex) {
                           Logger.logErrorMessage(SoftMG.class.getName()+"+softmg poplastrepair0 +", ex);
                    }

                    try(PreparedStatement request = conn.prepareStatement("update soft set last_forged_block_height=? where id=?");){
                    request.setInt(1, lastForgedBlockHeight);
                    request.setLong(2, lastBlock.getGeneratorId());
                    request.executeUpdate();
                    }
                }

                Map<Long, Long> holdTransfers = new HashMap<>();
                if (holdEnabled) {
                      
                    try(PreparedStatement request = conn.prepareStatement("select id,amount from hold_transfer where height=?");){
                    request.setInt(1, currentHeight);
                    try(ResultSet rs = request.executeQuery();){
                    while (rs.next()) {
                        holdTransfers.put(rs.getLong(1), rs.getLong(2));
                    }
                    }
                    }

                    // FIRST DELETE TRANSFERS
                    try(PreparedStatement request = conn.prepareStatement("delete from hold_transfer where height=?");){
                    request.setInt(1, currentHeight);
                    request.executeUpdate();
                    }

                    // AND THEN PUT THEM ONTO BALANCE
                     try(PreparedStatement pstmt = conn.prepareStatement("update soft set hold=? where id=?") ){
                        for (Long account : holdTransfers.keySet()) {
                            if (account == null) {
                                continue;
                            }
                            addDiff(-holdTransfers.get(account), account, diffs);
                            pstmt.setLong(1, holdTransfers.get(account));
                            pstmt.setLong(2, account);
                            pstmt.addBatch();    
                        }
                        try {
                            int[] result = pstmt.executeBatch(); //(4)
                            conn.commit();
                        } catch (BatchUpdateException ex) {
                            Logger.logErrorMessage("SoftMG AnP poplastblk batch erdo000:"+ex);
                            conn.rollback();
                        }
                    }
                }

                Set<Integer> blockHeights = new HashSet<>();

                // REVERT 'LAST' PARAMETERS AND DELETE FORCES
                int count = 0;
                if (!forces.isEmpty()) {
                    try(PreparedStatement pstmt = conn.prepareStatement("update soft set last=? where id=?")){
                        for (SMGBlock.Payout force : forces) {
                            pstmt.setLong(1, force.getLast());
                            pstmt.setLong(2, force.getToID());
                            pstmt.addBatch();
                            count++;
                            addDiff(0L - force.getAmount(), force.getToID(), diffs);
                            addDiff(force.getAmount(), Genesis.CREATOR_ID, diffs);
                        }
                        try {
                            int[] result = pstmt.executeBatch(); //(4)
                            conn.commit();
                        } catch (BatchUpdateException ex) {
                            Logger.logErrorMessage("SoftMG poplastblk batch er0001:"+ex);
                            conn.rollback();
                        }
                        try (PreparedStatement trimmer = conn.prepareStatement("delete from force where height>=?")) {
                            trimmer.setInt(1, currentHeight);
                            count = trimmer.executeUpdate();
                        }       
                    }      
//                    for (SMGBlock.Payout force : forces) {
//                        request = conn.prepareStatement("update soft set last=? where id=?");
//                        request.setLong(1, force.getLast());
//                        request.setLong(2, force.getToID());
//                        request.executeUpdate();
//                        request.close();
//                        count++;
//                        addDiff(0L - force.getAmount(), force.getToID(), diffs);
//                        addDiff(force.getAmount(), Genesis.CREATOR_ID, diffs);
//                    }
//                    try (PreparedStatement trimmer = conn.prepareStatement("delete from force where height>=?")) {
//                        trimmer.setInt(1, currentHeight);
//                        count = trimmer.executeUpdate();
//                    }
                }

                // RE-OPEN SATISFIED FORCES IN PREVIOUS BLOCKS
                if (!revertedsoftMGTransactions.isEmpty()) {
                    try(PreparedStatement pstmt = conn.prepareStatement("select height from force where stxid IN ("+ getLineOfQs(revertedsoftMGTransactions.size())+")" )) {
                        int ind=0;
                        for (Long stxid : revertedsoftMGTransactions) {
                            pstmt.setLong(++ind, stxid );
                        }
                        try(ResultSet rs = pstmt.executeQuery();){
                        while (rs.next()) {
                            Integer height = rs.getInt(1);
                            if ( height > 0) {
                                blockHeights.add(height);
                            } else {
                            }
                        }
                        }
                    }
                    try(PreparedStatement pstmt =  conn.prepareStatement("update force set stxid=? where stxid=?")){
                        for (Long stxid : revertedsoftMGTransactions) {
                            pstmt.setNull(1, Types.BIGINT);
                            pstmt.setLong(2, stxid);
                            pstmt.addBatch();
                        }
                        try {
                            int[] result = pstmt.executeBatch(); //(4)
                            conn.commit();
                        } catch (BatchUpdateException ex) {
                            Logger.logErrorMessage("SoftMG poplastblk batch er0002:"+ex);
                            conn.rollback();
                        }
                             
                    }                     
//                    for (Long stxid : revertedsoftMGTransactions) {
//                                             
//                        request = conn.prepareStatement("select height from force where stxid=?");
//                        request.setLong(1, stxid);
//                        rs = request.executeQuery();
//                        while (rs.next()) {
//                            Integer height = rs.getInt(1);
//                            if ( height > 0) {
//                                blockHeights.add(height);
//                            } else {
//                            }
//                        }
//                        rs.close();
//                        request.close();
//                        request = conn.prepareStatement("update force set stxid=? where stxid=? limit 1");
//                        request.setNull(1, Types.BIGINT);
//                        request.setLong(2, stxid);
//                        request.executeUpdate();
//                        request.close();
//                    }
                }

                // SET PREVIOUS softmgBLOCKS AS UNACCEPTED
                if (!blockHeights.isEmpty()) {
                    try(PreparedStatement pstmt =  conn.prepareStatement("update block set accepted=false where height=? and accepted=true")){
                        for (Integer notAcceptedHeight : blockHeights) {
                            pstmt.setInt(1, notAcceptedHeight);
                            pstmt.addBatch();
                        }
                        try {
                            int[] result = pstmt.executeBatch(); //(4)
                            conn.commit();
                        } catch (BatchUpdateException ex) {
                            Logger.logErrorMessage("SoftMG poplastblk batch er0003:"+ex);
                            conn.rollback();
                        }
                             
                    }  
//                    for (Integer notAcceptedHeight : blockHeights) {                    
//                        request = conn.prepareStatement("update block set accepted=false where height=? and accepted=true");
//                        request.setInt(1, notAcceptedHeight);
//                        request.executeUpdate();
//                        request.close();
//                    }
                }

                // DELETE FUTURE BLOCKS - EXPECTED ONLY 1 BLOCK TO BE DELETED (THE CURRENT ONE)
                count = 0;                                    
                try(PreparedStatement request = conn.prepareStatement("delete from block where height>?");){
                request.setInt(1, currentHeight - 1);
                count = request.executeUpdate();
                }
                if (count != 1) {
                    if (count < 1) {
                        log(false, "popLastBlock() e001 - No blocks deleted (must be 1) at " + currentHeight);
                    }
                    if (count > 1) {
                        log(false, "popLastBlock() e002 - Too many blocks deleted: " + count + " (must be 1) at " + currentHeight);
                        //--del onli 1
                        count = 0;                                    
                       try(PreparedStatement request = conn.prepareStatement("delete from block where height>?");){
                        request.setInt(1, currentHeight);
                        count = request.executeUpdate();
                       }
                        //
                    }
                }
                String msg = currentHeight + " <- this block is popped\n\tDiffs: [" + diffs.size() + "]";

                // APPLY BALANCE DIFFS
                if (!diffs.isEmpty()) {
                    if (holdEnabled) {
                        //
                        ArrayList<Long> accounts = new ArrayList<>();
                        try(PreparedStatement pstmt = conn.prepareStatement("select balance,last_forged_block_height,hold,id from soft where id IN ("+ getLineOfQs(diffs.keySet().size())+")" )) {
                            int ind=0;
                            for (Long accountId : diffs.keySet()) {
                                pstmt.setLong(++ind, accountId);
                            }
                            try(ResultSet rst_ = pstmt.executeQuery();){
                            int height = 0;
                            long balance = 0l;
                            long hold = 0l;
                            Long accountId =0l;
                            while (rst_.next()) {
                                balance = rst_.getLong(1);
                                height = rst_.getInt(2);
                                hold = rst_.getLong(3);
                                accountId =  rst_.getLong(4);
                                
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
                                    accounts.add(accountId);
//                                    update(accountId, diffs.get(accountId), null);
                                }
                            }
                            _update(accounts, diffs, null);
                            }catch(Exception e){
                                System.out.println("hhy c e:"+e);
                            }
                        //rs.close();
                        }
                    } else {
                        ArrayList<Long> accounts = new ArrayList<>();
                        for (Long accountId : diffs.keySet()) {
                            msg = msg + ", " + accountId + " " + diffs.get(accountId);
                            //update(accountId, diffs.get(accountId), null);
                            accounts.add(accountId);
                        }
                        _update(accounts, diffs, null);
                    }
                }

                // FIND ACCOUNTS TO DELETE                 
                try(PreparedStatement request = conn.prepareStatement("select soft_id from activation where height=?");){
                request.setInt(1, currentHeight);
                try(ResultSet rs = request.executeQuery();){
                while (rs.next()) {
                    accountsToDelete.add(rs.getLong(1));
                }
                }
                }

                // DELETE ACTIVATED IN THIS BLOCK ACCOUNTS
                count = 0;
                msg = "\tDeleted accounts: [" + accountsToDelete.size() + "]";
                if (!accountsToDelete.isEmpty()) {
                    try(PreparedStatement pstmt =  conn.prepareStatement("delete from soft where id=?")){
                        for(Long id : accountsToDelete) {
                            msg = msg + ", " + id;
                            pstmt.setLong(1, id);
                            pstmt.addBatch();
                        }
                        try {
                            int[] result = pstmt.executeBatch(); //(4)
                            conn.commit();
                        } catch (BatchUpdateException ex) {
                            Logger.logErrorMessage("SoftMG poplastblk batch er0004:"+ex);
                            conn.rollback();
                        }           
                    } 
                }

                commit();
            } catch (Exception e) {
                // TODO
                rollback();
                log(false, "CRITICAL - FAILED TO POP LAST BLOCK BECAUSE OF \"" + e.getMessage() + "\"");
                e.printStackTrace();
            }
        }
    }

}
