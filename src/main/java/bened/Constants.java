/** ****************************************************************************
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
 ***************************************************************************** */
package bened;


import java.math.BigInteger;
import java.util.*;

public final class Constants {

    public static final boolean isTestnet = Bened.getBooleanProperty("bened.isTestnet");
    public static final boolean isOffline = Bened.getBooleanProperty("bened.isOffline");
    public static final boolean isLightClient = Bened.getBooleanProperty("bened.isLightClient");
    public static final String customLoginWarning = Bened.getStringProperty("bened.customLoginWarning", null, false, "UTF-8");

    public static final int MAX_NUMBER_OF_TRANSACTIONS = 100;
    public static final int MAX_NAGRADNIH = MAX_NUMBER_OF_TRANSACTIONS * 70 / 100;
    public static final int MAX_PROSTIH = MAX_NUMBER_OF_TRANSACTIONS * 30 / 100;  
    
    public static final int MIN_TRANSACTION_SIZE = 400;
    public static final int MAX_PAYLOAD_LENGTH = MAX_NUMBER_OF_TRANSACTIONS * MIN_TRANSACTION_SIZE; 
    
    
    
    public static final int MAX_TRANSACTION_PAYLOAD_LENGTH = 1536;
    public static final long MAX_BALANCE_BND =       15000000000L;
    public static final long OKON4ATELNAYA_EMISSIA = 150000000000L; 
    public static final long ONE_BND = 1000000L; 
    public static final long MAX_BALANCE_centesimo = MAX_BALANCE_BND * ONE_BND;
    public static final long MAX_full_BALANCE_centesimo = OKON4ATELNAYA_EMISSIA* ONE_BND;
    
     public static final int BLOCK_TIME = 60;
    public static final long INITIAL_BASE_TARGET = BigInteger.valueOf(2).pow(63).divide(BigInteger.valueOf(BLOCK_TIME * MAX_BALANCE_BND)).longValue(); //153722867;
    

public static final int fasterer_GENERACII_BLOCKOV = 1;

public static final int MIN_BLOCKTIME_DELTA = 10;
public static final int MAX_BLOCKTIME_DELTA = 30;

public static final int BASE_TARGET_GAMMA = 72;
  
public static long getMAX_BASE_TARGET(int height) {

        long max= getINITIAL_BASE_TARGET( height)* ( (Account.getAccount(Genesis.CREATOR_ID).getBalanceNQT()*(-1)/ONE_BND) /100);
        return  max;
}
public static long getMIN_BASE_TARGET(int height) {
    long min = getINITIAL_BASE_TARGET( height)* 9 / 10;
    return min;
}

    public static final int HOLD_ENABLE_HEIGHT = 50;  
    public static final int HOLD_RANGE = 100000;    
    public static final long HOLD_BALANCE_MIN = 100L;
    public static final long HOLD_BALANCE_MAX = 10000L;

    
    public static final int ENABLE_BLOCK_VERSION_PREVALIDATION = 71;
    public static final long MAX_BALANCE_Stop_mg_centesimo =  100000000000L;
    public static final long MAX_softMG_PAYOUT_centesimo   =  10000000000L;  
    

    
    public static final long MIN_FEE_NQT = 150000L;
    public static final int MAX_ROLLBACK = Math.max(Bened.getIntProperty("bened.maxRollback"), 720);
    public static final int TRIM_FREQUENCY = Math.max(Bened.getIntProperty("bened.trimFrequency"), 1);
    public static final int GUARANTEED_BALANCE_CONFIRMATIONS = isTestnet ? Bened.getIntProperty("bened.testnetGuaranteedBalanceConfirmations", 1440) : 770;
    public static final int LEASING_DELAY = isTestnet ? Bened.getIntProperty("bened.testnetLeasingDelay", 1440) : 1440;
    public static final long MIN_FORGING_BALANCE_NQT = 1000 * ONE_BND;

    public static final int MAX_TIMEDRIFT = 15; 

    
    public static final int FORGING_DELAY = Math.min(MAX_TIMEDRIFT - 1, Bened.getIntProperty("nxt.forgingDelay"));
    public static final int FORGING_SPEEDUP = Bened.getIntProperty("nxt.forgingSpeedup");
    public static final int BATCH_COMMIT_SIZE = Bened.getIntProperty("bened.batchCommitSize", 100);

    public static final int MAX_ALIAS_URI_LENGTH = 512;
    public static final int MAX_ALIAS_LENGTH = 100;

    public static final int MAX_ARBITRARY_MESSAGE_LENGTH = 160;
    public static final int MAX_ENCRYPTED_MESSAGE_LENGTH = 160 + 16;

    public static final int MAX_PRUNABLE_MESSAGE_LENGTH = 512; 
    public static final int MAX_PRUNABLE_ENCRYPTED_MESSAGE_LENGTH = 512;

    public static final int MIN_PRUNABLE_LIFETIME = isTestnet ? 1440 * 60 : 14 * 600 * 60;
    public static final int MAX_PRUNABLE_LIFETIME;
    public static final boolean ENABLE_PRUNING;

    private static List<String> blacklistedBlocksList = Bened.getStringListProperty("bened.blacklist.blocks");
    
    static {
        int maxPrunableLifetime = Bened.getIntProperty("bened.maxPrunableLifetime");
        ENABLE_PRUNING = maxPrunableLifetime >= 0;
        MAX_PRUNABLE_LIFETIME = ENABLE_PRUNING ? Math.max(maxPrunableLifetime, MIN_PRUNABLE_LIFETIME) : Integer.MAX_VALUE;
        List<String> backup = blacklistedBlocksList;
        blacklistedBlocksList = new ArrayList<String>();
        blacklistedBlocksList.addAll(backup);

    }

    public static Set<String> getBlacklistedBlocks() {
        return new HashSet<>(blacklistedBlocksList);
    }
    
    public static final boolean INCLUDE_EXPIRED_PRUNABLE = Bened.getBooleanProperty("bened.includeExpiredPrunable");

    public static final boolean MEASURE_TRIMMING_TIME = Bened.getBooleanProperty("bened.measureTrimmingTime");

    public static final int MAX_ACCOUNT_NAME_LENGTH = 100;
    public static final int MAX_ACCOUNT_DESCRIPTION_LENGTH = 512;

    public static final int TRANSPARENT_FORGING_BLOCK = 1440;
    public static final int TRANSPARENT_FORGING_BLOCK_firstforg = 200;
    public static final int TRANSPARENT_FORGING_BLOCK_3 = 1450;
   public static final int TRANSPARENT_FORGING_BLOCK_8 = 1460;
    public static final int FIRST_FORK_BLOCK = 1500; 

     public static final int ENFORCE_FULL_TRANSACTION_VALIDATION = 540; 
    
    
    public static final int FORBID_FORGING_WITH_YOUNG_PUBLIC_KEY = 3001;
    public static final int NQT_BLOCK = isTestnet ? 0 : 0;  
    public static final int MAX_REFERENCED_TRANSACTION_TIMESPAN = 60 * 600 * 60;



   

    public static final int LAST_KNOWN_BLOCK = 1440; 

    public static final int[] MIN_VERSION = new int[] {0, 0, 0, 0};
    public static final int[] MIN_PROXY_VERSION = new int[] {0, 0, 0, 0};

    public static final boolean correctInvalidFees = Bened.getBooleanProperty("bened.correctInvalidFees");

    public static final int maxBlockchainHeight = Bened.getIntProperty("bened.maxBlockchainHeight"); 
    public static final boolean limitBlockchainHeight = maxBlockchainHeight > 0;
    public static final boolean SERVE_ONLY_LATEST_TRANSACTIONS = Bened.getBooleanProperty("bened.serveOnlyLatestTransactions", false);

    // --------[INIT #A]-------
    public static final long EPOCH_BEGINNING;

    static {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        calendar.set(Calendar.YEAR, 2021);
        calendar.set(Calendar.MONTH, Calendar.DECEMBER);
        calendar.set(Calendar.DAY_OF_MONTH, 19);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 40);
        calendar.set(Calendar.SECOND, 1);
        calendar.set(Calendar.MILLISECOND, 1);
        EPOCH_BEGINNING = calendar.getTimeInMillis();
    }

    public static final String ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyz";

    private Constants() {
    } // never

    public static final String IN_BLOCK_ID = "inblockID";
    public static final String IN_BLOCK_HEIGHT = "inblockHeight";
    public static final String IN_TRANSACT_ID = "inTransactId";
    public static final String RANDOM = "random";

    public static final int GlubinaPoiska = 30000;
    /*
     From this height we:
     1. validate encrypted message length (should be < MAX_ENCRYPTED_MESSAGE_LENGTH)
     2. validate duplicates for ACCOUNT INFO transactions
     Too long messages and duplicate ACCOUNT INFO transactions are rejected
    */
    public static final int ADVANCED_MESSAGING_VALIDATION = 450; 

    // From this height we do not support aliases any more
    public static final int LAST_ALIASES_BLOCK = 1000;

    public static final int CONTROL_TRX_TO_ORDINARY = 550; 
    public static final int FEE_MAX_10 = 1440; 
    public static final int THIEF_BLOCK_BEGIN = 560;
    public static final int CURRENT_BLOCK_VERSION = 3;
        
    public static final String GENESIS_SECRET_PHRASE ="Twas brillig, and the slithy toves Did gyre and gimble in the wabe: All mimsy were the borogoves, And the mome raths outgrabe. The END";
   
   public static final int BEGIN_ZEROBLOCK_FIX = 0;
 

    public static long getINITIAL_BASE_TARGET(int height) {
        if(height<1)return INITIAL_BASE_TARGET;

        long IBT = BigInteger.valueOf(2).pow(63).divide(BigInteger.valueOf(BLOCK_TIME * (Account.getAccount(Genesis.CREATOR_ID).getBalanceNQT()*(-1)/ONE_BND) )).longValue();
        return IBT;
    }

}
