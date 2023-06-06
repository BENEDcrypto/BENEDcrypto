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

import bened.db.DbClause;
import bened.db.DbIterator;
import bened.db.DbKey;
import bened.db.VersionedEntityDbTable;
import bened.util.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class HashTint {

    private static final DbKey.LongKeyFactory<HashTint> hashTntDbKeyFactory = new DbKey.LongKeyFactory<HashTint>("id") {

        @Override
        public DbKey newKey(HashTint hashtnt) {
            return hashtnt.dbKey;
        }

    };

    private static final VersionedEntityDbTable<HashTint> hashTntTable = new VersionedEntityDbTable<HashTint>("hashTint", hashTntDbKeyFactory) {

        @Override
        protected HashTint load(Connection con, ResultSet rs, DbKey dbKey) throws SQLException {
            HashTint _ht=null;
            try{
                _ht =  new HashTint(rs, dbKey);
            }catch(Exception e){
                Logger.logErrorMessage("HashTint load Parser db err:"+e);
            }finally{
                return _ht;
            }
            
        }

        @Override
        protected void save(Connection con, HashTint hashtnt) throws SQLException {
            hashtnt.save(con);
        }

        @Override
        protected String defaultSort() {
            return " ORDER BY hashtnt_name ";
        }

    };

    public static int getCount() {
        return hashTntTable.getCount();
    }

    public static int getAccountHashTntCount(long accountId) {
        return hashTntTable.getCount(new DbClause.LongClause("account_id", accountId));
    }

    public static DbIterator<HashTint> getHashTntsByOwner(long accountId, int from, int to) {
        return hashTntTable.getManyBy(new DbClause.LongClause("account_id", accountId), from, to);
    }

    public static HashTint getHashTnt(String HashTntIdent) {
        return hashTntTable.getBy(new DbClause.StringClause("hashtint_name", HashTntIdent.toLowerCase()));
    }

    public static DbIterator<HashTint> getHashTntLike(String HashTntIdent, int from, int to) {
        return hashTntTable.getManyBy(new DbClause.LikeClause("hashtnt_name", HashTntIdent.toLowerCase()), from, to);
    }

    public static HashTint getHashTnt(long id) {
        return hashTntTable.get(hashTntDbKeyFactory.newKey(id));
    }

    static void transferHashTnt(final String hashTntIdent) {
        final HashTint hashTnt = getHashTnt(hashTntIdent);
        //hashTntTable.transfer(hashTnt);
        addOrUpdateHashTnt(null, null);
    }

    static void addOrUpdateHashTnt(Transaction transaction, Attachment.HashTintAssignment attachment) {
        System.out.println("addorupd hashtint");
        HashTint hashtnt = getHashTnt(attachment.getTntName());
        if (hashtnt == null) {
            hashtnt = new HashTint(transaction, attachment);
        } else {
            hashtnt.accountId = transaction.getRecipientId()==Genesis.CREATOR_ID? transaction.getSenderId() : transaction.getRecipientId();
            String nameTnt = attachment.getTntName();
            hashtnt.timestamp = Bened.getBlockchain().getLastBlockTimestamp();
        }
        hashTntTable.insert(hashtnt);
        System.out.println("addorupd-1");
    }

    static void init() {}


    private final long id;
    private long accountId;
    private final String static_tag;
    private final String second_tag; 
    private final String event_data;
    private final Boolean allsee;
    private final String event_name; 
    private final String event; 
    private final String hashtint_name;
    private final DbKey dbKey;
    private int timestamp;

    private HashTint(Transaction transaction, Attachment.HashTintAssignment attachment) {
        this.id = transaction.getId();
        this.dbKey = hashTntDbKeyFactory.newKey(this.id);
        this.accountId = transaction.getRecipientId()==Genesis.CREATOR_ID? transaction.getSenderId() : transaction.getRecipientId();
        this.timestamp = Bened.getBlockchain().getLastBlockTimestamp();
        
        this.event=attachment.getTntBase();
        this.hashtint_name= attachment.getTntName();
        
        this.static_tag = attachment.getjsBase().get("eventStaticTag").toString();
        this.second_tag = attachment.getjsBase().get("ET_HashTag").toString();
        this.event_data = attachment.getjsBase().get("editTextDate").toString();
        this.allsee= (boolean)attachment.getjsBase().get("ET_allsee");
        this.event_name= attachment.getjsBase().get("ET_eventname").toString();
       
                
    }

    private HashTint(ResultSet rs, DbKey dbKey) throws SQLException {
        this.id = rs.getLong("id");
        this.dbKey = dbKey;
        this.accountId = rs.getLong("account_id");
        this.timestamp = rs.getInt("timestamp");
        
        this.event=rs.getString("event");
        this.hashtint_name= rs.getString("hashtint_name");
        
        this.static_tag = rs.getString("static_tag");
        this.second_tag = rs.getString("second_tag");
        this.event_data = rs.getString("event_data");
        this.allsee= rs.getBoolean("allsee");
        this.event_name= rs.getString("event_name");
        
    }

    private void save(Connection con) throws SQLException {
        try (PreparedStatement pstmt = con.prepareStatement("INSERT INTO hashTint (id, account_id, timestamp, event,"
                + " hashtint_name, static_tag, second_tag, event_data, "
                + "allsee, event_name, height) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            int i = 0;
            pstmt.setLong(++i, this.id);
            pstmt.setLong(++i, this.accountId);
            pstmt.setInt(++i, this.timestamp);
            pstmt.setString(++i, this.event);
            pstmt.setString(++i, this.hashtint_name);
            pstmt.setString(++i, this.static_tag);
            pstmt.setString(++i, this.second_tag);
            pstmt.setString(++i, this.event_data);
            pstmt.setBoolean(++i, this.allsee);
            pstmt.setString(++i, this.event_name);
            pstmt.setInt(++i, Bened.getBlockchain().getHeight());
            pstmt.executeUpdate();
        }catch(Exception e){
            Logger.logErrorMessage("-- HashTint sav err:"+e);
        }
    }

    public long getId() {
        return id;
    }

    public String getHashTntBase() {
        return this.event;
    }

    public String getHashTntName() {
        return this.hashtint_name;
    }

    public int getTimestamp() {
        return timestamp;
    }

    public long getOwnerAccountId() {
        return accountId;
    }

}
