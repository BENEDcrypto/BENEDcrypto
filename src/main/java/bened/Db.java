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

import bened.db.BasicDb;
import bened.db.TransactionalDb;

public final class Db {
    
    public static final String PREFIX = Constants.isTestnet ? "bened.testDb" : "bened.db";
    public static final String PREFIX_SoftMg = Constants.isTestnet ? "bened.dbSoftMg" : "bened.dbSoftMg"; //bened.testSoftMgDb
    
    public static final String SoftMG_DB_URL = String.format("jdbc:%s:%s;%s", "h2", Bened.getDbDir("./"+( Constants.isTestnet?"bened_test_db":"bened_db")+"/softMgsol"), "DB_CLOSE_ON_EXIT=FALSE;MV_STORE=FALSE");
   
    public static final String SoftMG_DB_USERNAME = Bened.getStringProperty(PREFIX_SoftMg + "Username");
    public static final String SoftMG_DB_PASSWORD = Bened.getStringProperty(PREFIX_SoftMg + "Password");
    
    public static final TransactionalDb db = new TransactionalDb(new BasicDb.DbProperties()
            .maxCacheSize(Bened.getIntProperty("bened.dbCacheKB"))
            .dbUrl(Bened.getStringProperty(PREFIX + "Url"))
            .dbType(Bened.getStringProperty(PREFIX + "Type"))
            .dbDir(Bened.getStringProperty(PREFIX + "Dir"))
            .dbParams(Bened.getStringProperty(PREFIX + "Params"))
            .dbUsername(Bened.getStringProperty(PREFIX + "Username"))
            .dbPassword(Bened.getStringProperty(PREFIX + "Password", null, true))
            .maxConnections(Bened.getIntProperty("bened.maxDbConnections"))
            .loginTimeout(Bened.getIntProperty("bened.dbLoginTimeout"))
            .defaultLockTimeout(Bened.getIntProperty("bened.dbDefaultLockTimeout") * 1000)
            .maxMemoryRows(Bened.getIntProperty("bened.dbMaxMemoryRows"))
    );

    public static void init() {
        db.init(new benedDbVersion());
    }

    static void shutdown() {
        db.shutdown();
    }

    private Db() {} // never

}
