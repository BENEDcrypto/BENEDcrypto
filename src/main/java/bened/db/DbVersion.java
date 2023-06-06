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

package bened.db;

import bened.util.Logger;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public abstract class DbVersion {

    protected BasicDb db;

    void init(BasicDb db) {
        this.db = db;
        int nextUpdate = 1;
        try(Connection con = db.getConnection();
            Statement  stmt = con.createStatement(); 
            ResultSet rs = stmt.executeQuery("SELECT next_update FROM version");) {
            if (! rs.next()) {
                throw new RuntimeException("Invalid version table");
            }
            nextUpdate = rs.getInt("next_update");
            if (! rs.isLast()) {
                throw new RuntimeException("Invalid version table");
            }
            Logger.logMessage("Database update may take a while if needed, current db version " + (nextUpdate - 1) + "...");
        } catch (SQLException e) {
             Logger.logMessage("Initializing an empty database");
             try(Connection con = db.getConnection();
                Statement  stmt = con.createStatement(); ){
                stmt.executeUpdate("CREATE TABLE version (next_update INT NOT NULL)");
                stmt.executeUpdate("INSERT INTO version VALUES (1)");
                con.commit();
                update(nextUpdate);
             }catch(Exception en){
               throw new RuntimeException(e.toString()+":"+en.toString(), e);  
             }
        }
    }

    protected void apply(String sql) {
        try(Connection con = db.getConnection();
            Statement stmt = con.createStatement();) {
            if (sql != null) {
                Logger.logDebugMessage("Will apply sql:\n" + sql);
                stmt.executeUpdate(sql);
            }
            stmt.executeUpdate("UPDATE version SET next_update = next_update + 1");
            con.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Database error executing " + sql, e);
        }
    }

    protected abstract void update(int nextUpdate);

}
