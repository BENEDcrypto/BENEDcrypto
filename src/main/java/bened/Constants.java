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

     public static final int autostartbefo = 600000; // 600000
     public static final int autostartmezo = 800000; // 1800000
    
    
    public static final boolean isTestnet = Bened.getBooleanProperty("bened.isTestnet");
    public static final boolean isOffline = Bened.getBooleanProperty("bened.isOffline");
    public static final boolean isLightClient = Bened.getBooleanProperty("bened.isLightClient");
    public static final int defaultNumberOfForkConfirmations = Bened.getIntProperty(Constants.isTestnet
            ? "bened.testnetNumberOfForkConfirmations" : "bened.numberOfForkConfirmations");
 //   public static final String customLoginWarning = Bened.getStringProperty("bened.customLoginWarning", null, false, "UTF-8");
    
    
    public static final int change_evendek22 = 663399;
    public static final int fix_evendek_210123 = 664000;
    public static long ght =0;
    
    // slowlemiti
    public static final int affil_struct = 10;
    public static int mindifforg = -10; // limit speed (ne umenshats)
    public static long techForgeDelayMs = 30000; 
    public static final int LAST_KNOWN_BLOCK = 682573+10; //683352; // без этого не начинает форжить с нуля //1440; // there was CHECKSUM_BLOCK_18; which is for height 251010
    public static final int MAX_TIMEDRIFT = 15; // allow up to 15 s clock difference
    public static final int Allow_future_inVM = Bened.getIntProperty("bened.allowfuturevm", 0); // allow up to 15 s clock difference
    public static int downloadbchsize =720;
    public static final int USKORITEL_GENERACII_BLOCKOV = 1;
    public static final boolean sleemlog = Bened.getBooleanProperty("bened.sleemlog", true);
    
   
    
    public static final int MAX_NUMBER_OF_TRANSACTIONS = 100;
    public static final int MAX_NAGRADNIH = MAX_NUMBER_OF_TRANSACTIONS * 70 / 100;   // 70 %
    public static final int MAX_PROSTIH = MAX_NUMBER_OF_TRANSACTIONS * 30 / 100;     // 30 %
    
    public static final int MIN_TRANSACTION_SIZE = 400;
    
    public static final int MAX_HASH_TRX_SIZE = 600000;
    public static final int MAX_HASH_TRX_COUNT = 2;
    
    public static final int MAX_BLOCK_PAYLOAD_LENGTH = (MAX_NUMBER_OF_TRANSACTIONS * MIN_TRANSACTION_SIZE)+((MAX_HASH_TRX_SIZE-100000)*MAX_HASH_TRX_COUNT); 
    
    public static final int MAX_TRANSACTION_PAYLOAD_LENGTH = 3536;  // максимальная длина транзакции
    public static final long MAX_BALANCE_BND = 15000000000L;  //  начальная эмисия
    public static final long OKON4ATELNAYA_EMISSIA = 150000000000L;  //  конечная эмисия
    public static final long ONE_BND = 1000000L; // копейки
    public static final long MAX_BALANCE_centesimo = MAX_BALANCE_BND * ONE_BND;
    public static final long MAX_full_BALANCE_centesimo = OKON4ATELNAYA_EMISSIA* ONE_BND;  //  максимальная эмисия
    
    //private static final long INITIAL_BASE_TARGET = 15372286728L;
    public static final int BLOCK_TIME = 60;
    public static final long INITIAL_BASE_TARGET = BigInteger.valueOf(2).pow(63).divide(BigInteger.valueOf(BLOCK_TIME * MAX_BALANCE_BND)).longValue(); //153722867;
    

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
    ///!!!! после начала холдинга с 50 блока менять нельзя - не будет грузиться
    public static final int HOLD_ENABLE_HEIGHT = 50;    //// это наверно с какова блока можно парамайнинг получать - но это не точно 1200000
    public static final int HOLD_RANGE = 100000;            /// за эти блоки холд работате                  100000
    public static final long HOLD_BALANCE_MIN = 100L;   //   1 000
    public static final long HOLD_BALANCE_MAX = 10000L; // 110 000

    
    public static final int ENABLE_BLOCK_VERSION_PREVALIDATION = 71; // visota
    public static final long MAX_BALANCE_Stop_mg_centesimo =  100000000000L;  //  после этой суммы парамайнинг выключается 100 000 000000L
    public static final long MAX_softMG_PAYOUT_centesimo   =  10000000000L;  //  максимальная выплата
    

    
    public static final long MIN_FEE_NQT = 150000L;
    public static final int MAX_ROLLBACK = Math.max(Bened.getIntProperty("bened.maxRollback"), 720);
    public static final int TRIM_FREQUENCY = Math.max(Bened.getIntProperty("bened.trimFrequency"), 1);
    public static final int GUARANTEED_BALANCE_CONFIRMATIONS = isTestnet ? Bened.getIntProperty("bened.testnetGuaranteedBalanceConfirmations", 1440) : 770;
    public static final int LEASING_DELAY = isTestnet ? Bened.getIntProperty("bened.testnetLeasingDelay", 1440) : 1440;
    public static final long MIN_FORGING_BALANCE_NQT = 1000 * ONE_BND;
    
    public static final int FORGING_DELAY = Math.min(MAX_TIMEDRIFT - 1, Bened.getIntProperty("nxt.forgingDelay"));
    public static final int FORGING_SPEEDUP = Bened.getIntProperty("nxt.forgingSpeedup");
    public static final int BATCH_COMMIT_SIZE = Bened.getIntProperty("bened.batchCommitSize", 500);

    public static final int MAX_ALIAS_URI_LENGTH = 512;
    public static final int MAX_ALIAS_LENGTH = 100;

    public static final int MAX_ARBITRARY_MESSAGE_LENGTH = 160;
    public static final int MAX_ENCRYPTED_MESSAGE_LENGTH = 160 + 16;

    public static final int MAX_PRUNABLE_MESSAGE_LENGTH = 512;  //ss * 42
    public static final int MAX_PRUNABLE_ENCRYPTED_MESSAGE_LENGTH = 512;  //ss * 42

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

    public static final int TRANSPARENT_FORGING_BLOCK = 0; // надо чтоб форжинг начался с нуля // 1440;
    public static final int TRANSPARENT_FORGING_BLOCK_firstforg = 200; // если реципиент находится в первых 100 блоках - отдадим ему ево баланс - как из генезиса
    public static final int TRANSPARENT_FORGING_BLOCK_3 = 1450; //1450; // это минимальные высоты чтоб баланс стал эффективным для форжинга
    public static final int TRANSPARENT_FORGING_BLOCK_8 = 1460; //78000;
    public static final int FIRST_FORK_BLOCK = 1500; //74000; /// !!!+++ определяет блок с которого можно руками ставить форк конфирмейшн

     public static final int ENFORCE_FULL_TRANSACTION_VALIDATION = 540; //1500;
    
    
    public static final int FORBID_FORGING_WITH_YOUNG_PUBLIC_KEY = 3001;//75000; // At this height we do not allow to forge if public key is announced in previous 1440 blocks or is not announced.
    public static final int NQT_BLOCK = isTestnet ? 0 : 0;   /// NQT ISPOLZUETSY S 0 BLOCKA
    public static final int MAX_REFERENCED_TRANSACTION_TIMESPAN = 60 * 600 * 60;

    public static final int[] MIN_VERSION = new int[] {0, 0, 0, 0};
    public static final int[] MIN_PROXY_VERSION = new int[] {0, 0, 0, 0};

    public static final boolean correctInvalidFees = Bened.getBooleanProperty("bened.correctInvalidFees");

    public static final int maxBlockchainHeight = Bened.getIntProperty("bened.maxBlockchainHeight"); ///!!!+++ поумолчанию 
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
    public static final int ADVANCED_MESSAGING_VALIDATION = 450;   // Should be OK

    // From this height we do not support aliases any more
    public static final int LAST_ALIASES_BLOCK = 1000; //378000;

    public static final int CONTROL_TRX_TO_ORDINARY = 550; //1500;     // allow only payments and messages
//    public static final int FEE_MAX_10 = 1440; // 1440                  // Limit fee by 10 sol (100 000 000 cents)
    public static final int THIEF_BLOCK_BEGIN = 560; //52573;
    public static final int CURRENT_BLOCK_VERSION = 3;
        
    public static final String GENESIS_SECRET_PHRASE ="Twas brillig, and the slithy toves Did gyre and gimble in the wabe: All mimsy were the borogoves, And the mome raths outgrabe. The END";
     // BENED5H5HPRS4UYTA8GY83
//    public static final int BEGIN_BLOCK_TIMESTAMP_CALCULATION = 70;// 546730;
   public static final int BEGIN_ZEROBLOCK_FIX = 0; //700000; //1046899;
 

    public static long getINITIAL_BASE_TARGET(int height) {
        if(height<1)return INITIAL_BASE_TARGET;

        long IBT = BigInteger.valueOf(2).pow(63).divide(BigInteger.valueOf((Bened.getBlockchain().getHeight()<Constants.change_evendek22? BLOCK_TIME : BLOCK_TIME*5) * (Account.getAccount(Genesis.CREATOR_ID).getBalanceNQT()*(-1)/ONE_BND) )).longValue();
        return IBT;
    }

    
    // repaired blocks
    public static HashSet<Integer> knowcurveblockheight = new HashSet<>(Arrays.asList(
         -1,650680, 664383, 677321, 678141));
    public static boolean verbadtime(long vertime){
        // 664687 -- -- -- 682207  `
        long a=35473086-1, a1=38605555+1;
        if(vertime>a&&vertime<a1){
            return true;
        }
        long b=37531495-1, b1=37531495+1;
        if(vertime>b&&vertime<b1){
            return true;
        }
        //...//
        return false;
    }
    

}
