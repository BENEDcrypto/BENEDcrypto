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

import static bened.crypto.Crypto.sha256;
import bened.util.Convert;
import bened.util.Logger;
import org.json.simple.JSONObject;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import org.json.simple.JSONArray;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public interface Attachment extends Appendix {

    TransactionType getTransactionType();

    abstract class AbstractAttachment extends Appendix.AbstractAppendix implements Attachment {

        private AbstractAttachment(ByteBuffer buffer, byte transactionVersion) {
            super(buffer, transactionVersion);
        }

        private AbstractAttachment(JSONObject attachmentData) {
            super(attachmentData);
        }

        private AbstractAttachment(int version) {
            super(version);
        }

        private AbstractAttachment() {}

        @Override
        final String getAppendixName() {
            return getTransactionType().getName();
        }

        @Override
        final void validate(Transaction transaction) throws BNDException.ValidationException {
            getTransactionType().validateAttachment(transaction);
        }

        @Override
        final void apply(Transaction transaction, Account senderAccount, Account recipientAccount) {
            getTransactionType().apply((TransactionImpl) transaction, senderAccount, recipientAccount);
        }

        @Override
        public final Fee getBaselineFee(Transaction transaction) {
            return getTransactionType().getBaselineFee(transaction);
        }

        @Override
        public final Fee getNextFee(Transaction transaction) {
            return getTransactionType().getNextFee(transaction);
        }

        @Override
        public final int getBaselineFeeHeight() {
            return getTransactionType().getBaselineFeeHeight();
        }

        @Override
        public final int getNextFeeHeight() {
            return getTransactionType().getNextFeeHeight();
        }
    }

    abstract class EmptyAttachment extends AbstractAttachment {

        private EmptyAttachment() {
            super(0);
        }

        @Override
        final int getMySize() {
            return 0;
        }

        @Override
        final void putMyBytes(ByteBuffer buffer) {
        }

        @Override
        final void putMyJSON(JSONObject json) {
        }

        @Override
        final boolean verifyVersion(byte transactionVersion) {
            return getVersion() == 0;
        }

    }

    EmptyAttachment ORDINARY_PAYMENT = new EmptyAttachment() {

        @Override
        public TransactionType getTransactionType() {
            return TransactionType.Payment.ORDINARY;
        }

    };

    // the message payload is in the Appendix
    EmptyAttachment ARBITRARY_MESSAGE = new EmptyAttachment() {

        @Override
        public TransactionType getTransactionType() {
            return TransactionType.Messaging.ARBITRARY_MESSAGE;
        }

    };

    final class MessagingAliasAssignment extends AbstractAttachment {

        private final String aliasName;
        private final String aliasURI;

        MessagingAliasAssignment(ByteBuffer buffer, byte transactionVersion) throws BNDException.NotValidException {
            super(buffer, transactionVersion);
            aliasName = Convert.readString(buffer, buffer.get(), Constants.MAX_ALIAS_LENGTH).trim();
            aliasURI = Convert.readString(buffer, buffer.getShort(), Constants.MAX_ALIAS_URI_LENGTH).trim();
        }

        MessagingAliasAssignment(JSONObject attachmentData) {
            super(attachmentData);
            aliasName = Convert.nullToEmpty((String) attachmentData.get("alias")).trim();
            aliasURI = Convert.nullToEmpty((String) attachmentData.get("uri")).trim();
        }

        public MessagingAliasAssignment(String aliasName, String aliasURI) {
            this.aliasName = aliasName.trim();
            this.aliasURI = aliasURI.trim();
        }

        @Override
        int getMySize() {
            return 1 + Convert.toBytes(aliasName).length + 2 + Convert.toBytes(aliasURI).length;
        }

        @Override
        void putMyBytes(ByteBuffer buffer) {
            byte[] alias = Convert.toBytes(this.aliasName);
            byte[] uri = Convert.toBytes(this.aliasURI);
            buffer.put((byte)alias.length);
            buffer.put(alias);
            buffer.putShort((short) uri.length);
            buffer.put(uri);
        }

        @Override
        void putMyJSON(JSONObject attachment) {
            attachment.put("alias", aliasName);
            attachment.put("uri", aliasURI);
        }

        @Override
        public TransactionType getTransactionType() {
            return TransactionType.Messaging.ALIAS_ASSIGNMENT;
        }

        public String getAliasName() {
            return aliasName;
        }

        public String getAliasURI() {
            return aliasURI;
        }
    }

    final class MessagingAliasDelete extends AbstractAttachment {

        private final String aliasName;

        MessagingAliasDelete(final ByteBuffer buffer, final byte transactionVersion) throws BNDException.NotValidException {
            super(buffer, transactionVersion);
            this.aliasName = Convert.readString(buffer, buffer.get(), Constants.MAX_ALIAS_LENGTH);
        }

        MessagingAliasDelete(final JSONObject attachmentData) {
            super(attachmentData);
            this.aliasName = Convert.nullToEmpty((String) attachmentData.get("alias"));
        }

        public MessagingAliasDelete(final String aliasName) {
            this.aliasName = aliasName;
        }

        @Override
        public TransactionType getTransactionType() {
            return TransactionType.Messaging.ALIAS_DELETE;
        }

        @Override
        int getMySize() {
            return 1 + Convert.toBytes(aliasName).length;
        }

        @Override
        void putMyBytes(final ByteBuffer buffer) {
            byte[] aliasBytes = Convert.toBytes(aliasName);
            buffer.put((byte)aliasBytes.length);
            buffer.put(aliasBytes);
        }

        @Override
        void putMyJSON(final JSONObject attachment) {
            attachment.put("alias", aliasName);
        }

        public String getAliasName(){
            return aliasName;
        }
    }

    final class MessagingAccountInfo extends AbstractAttachment {

        private final String name;
        private final String description;

        MessagingAccountInfo(ByteBuffer buffer, byte transactionVersion) throws BNDException.NotValidException {
            super(buffer, transactionVersion);
            this.name = Convert.readString(buffer, buffer.get(), Constants.MAX_ACCOUNT_NAME_LENGTH);
            this.description = Convert.readString(buffer, buffer.getShort(), Constants.MAX_ACCOUNT_DESCRIPTION_LENGTH);
        }

        MessagingAccountInfo(JSONObject attachmentData) {
            super(attachmentData);
            this.name = Convert.nullToEmpty((String) attachmentData.get("name"));
            this.description = Convert.nullToEmpty((String) attachmentData.get("description"));
        }

        public MessagingAccountInfo(String name, String description) {
            this.name = name;
            this.description = description;
        }

        @Override
        int getMySize() {
            return 1 + Convert.toBytes(name).length + 2 + Convert.toBytes(description).length;
        }

        @Override
        void putMyBytes(ByteBuffer buffer) {
            byte[] name = Convert.toBytes(this.name);
            byte[] description = Convert.toBytes(this.description);
            buffer.put((byte)name.length);
            buffer.put(name);
            buffer.putShort((short) description.length);
            buffer.put(description);
        }

        @Override
        void putMyJSON(JSONObject attachment) {
            attachment.put("name", name);
            attachment.put("description", description);
        }

        @Override
        public TransactionType getTransactionType() {
            return TransactionType.Messaging.ACCOUNT_INFO;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

    }
    
    //////HashTint transaction
    /////---------------------/////////
    final class HashTintAssignment extends AbstractAttachment {

        private final String TnT_base;
        
        private final JSONObject JsBase;
        private final String  Part_name;
        
        HashTintAssignment(ByteBuffer buffer, byte transactionVersion) throws BNDException.NotValidException {
            super(buffer, transactionVersion);
            try {
                int atachlenght =buffer.getInt();
                if (atachlenght < 0) {
                    atachlenght &= Integer.MAX_VALUE;
                }
                if(atachlenght>Constants.MAX_HASH_TRX_SIZE-100000){
                    String msg ="--!a1--HashTint attach so mach:"+atachlenght+ " need:"+(Constants.MAX_HASH_TRX_SIZE-100000) ;
                    bened.util.Logger.logMessage(Attachment.class.getName()+","+msg);
                    TnT_base=null;
                    throw new Error("Attach HashTnt a#1a e:"+msg);
                }
                byte[] bytes = new byte[atachlenght];
                buffer.get(bytes);
                TnT_base = Base64.getEncoder().encodeToString(bytes);
                
                JsBase = (JSONObject) new JSONParser().parse( Convert.toString( Base64.getDecoder().decode( TnT_base))  );
                Part_name = Convert.nullToEmpty((String) JsBase.get("_tnt_name")).trim();
            } catch (ParseException ex) {
                bened.util.Logger.logMessage(Attachment.class.getName()+", HashTint construct a#2 e: "+ex); 
                throw new Error("Hash tint constructor a#1 e:"+ex);
            }
            
        }

        HashTintAssignment(JSONObject attachmentData) {
            super(attachmentData);
            try {
            TnT_base =attachmentData.get("hashTint").toString();
            byte[] bstrbase = Base64.getDecoder().decode(TnT_base);
            if(bstrbase.length>Constants.MAX_HASH_TRX_SIZE-100000){
                    String msg ="--!a2--HashTint attach so mach:"+bstrbase.length+ " need:"+(Constants.MAX_HASH_TRX_SIZE-100000) ;
                    bened.util.Logger.logMessage(Attachment.class.getName()+","+msg);
                    throw new Error("Attac HashTnt a#2A2 e:"+msg);
                }
            
                JsBase = (JSONObject) new JSONParser().parse( Convert.toString( bstrbase)  );
                Part_name = Convert.nullToEmpty((String) JsBase.get("_tnt_name")).trim();
            } catch (ParseException ex) {
                bened.util.Logger.logMessage(Attachment.class.getName()+", HashTint construct a#2 e: "+ex); 
                throw new Error("Hash tint constructor a#2A3 e:"+ex);
            }   
            
        }

//        public HashTintAssignment(String _TnTbase64, String _TnTname) {
//            this.TnT_base = new JSONObject();  // _TnTbase64.trim();
//            this.TnT_name = _TnTname.trim();
//        }
 
        
        @Override
        int getMySize() {
       //     return 1 + Convert.toBytes(TnT_base).length + 2 + Convert.toBytes(TnT_name).length;
            return 4+ Base64.getDecoder().decode(TnT_base ).length;    
        }

        @Override
        void putMyBytes(ByteBuffer buffer) {
            try{
                byte[] Ht_bytes = Base64.getDecoder().decode(this.TnT_base);
                buffer.putInt(Ht_bytes.length | Integer.MIN_VALUE);
                buffer.put(Ht_bytes);
            }catch(Exception e){
                Logger.logErrorMessage("HashTint putMS err:"+e);
                throw e;
            }
        }

        @Override
        void putMyJSON(JSONObject attachment) {
            attachment.put("hashTint",  TnT_base  );
        }

        @Override
        public TransactionType getTransactionType() {
            return TransactionType.Hashing.HASHTINT_ASSIGNMENT;
        }

        public String getTntBase() {
            return TnT_base;
        }
        public String  getTntName(){
           return Part_name;
        }
        public JSONObject  getjsBase(){
           return JsBase;
        }
        
        static List<String> staticHashTinteG = Arrays.asList("SEX","AUTO","ART","PHOTO","USD","EURO","RUR","KZT","DOC");
        public boolean testAllField(){
           
            try{
                String _ivent     = (String)JsBase.get("event");
                boolean _iscmp     = (boolean)JsBase.get("isCompressed");
                boolean _alsee      = (boolean)JsBase.get("ET_allsee");
                String _ev_name    = (String)JsBase.get("ET_eventname");
                String _ev_data    = (String)JsBase.get("editTextDate");
                long _ev_ver_ht    = (long)JsBase.get("version_ht");
                String _ev_ht      = (String)JsBase.get("ET_HashTag");
                String _ev_stat_tag= (String)JsBase.get("eventStaticTag");
                
                if( (!staticHashTinteG.contains(_ev_stat_tag)) || _ev_ht==null || _ev_data==null || _ev_name==null || _ivent==null ){ //|| _ev_ver_ht==null || _iscmp==null || _alsee==null){
                     new Throwable("HashTint parse header failed ");
                }
                        
                byte[] part_byte_r = _iscmp?Convert.uncompress( Base64.getDecoder().decode(_ivent)):Base64.getDecoder().decode(_ivent);part_byte_r = _iscmp?Convert.uncompress( Base64.getDecoder().decode(_ivent)):Base64.getDecoder().decode(_ivent);
            
                String _part_string =Convert.toString( part_byte_r );
            
                if(!Part_name.equals(Convert.toHexString(  sha256().digest(Convert.toBytes(_part_string) )) )){
                    bened.util.Logger.logMessage(Attachment.class.getName()+" Hash tint test atach t#rr1a name part no valid "); 
                    throw new Error("Hash tint test atach t#rr1b name part no valid "); 
                }
            
                JSONObject _part =(JSONObject) new JSONParser().parse(_part_string );
                if(_alsee){
                    String ET_Description = (String) _part.get("ET_Description");
                    JSONArray members = (JSONArray) _part.get("members");
                    if( members.size()>693 ){ //|| _ev_ver_ht==null || _iscmp==null || _alsee==null){
                        new Throwable("HashTint parse members failed: "+members.size());
                    }
                    byte[] ev_art = Base64.getDecoder().decode((String)_part.get("ev_art"));
                    String ev_art_datatype = (String) _part.get("ev_art_datatype");
                    byte[] ev_sgi = Base64.getDecoder().decode((String)_part.get("ev_sgi"));
                    String ev_sgi_datatype = (String) _part.get("ev_sgi_datatype");
                }else{
                    byte[] _data = Base64.getDecoder().decode((String)_part.get("_data"));
                    byte[] _nonce = Base64.getDecoder().decode((String)_part.get("_nonce"));
                }
                System.out.println("test hash atach complit");
            }catch(Exception e){
                System.out.println("  hash ttr4#  err e :"+e);
                bened.util.Logger.logMessage(Attachment.class.getName()+", HashTint allfieldtest err: "+e); 
                throw new Error(", HashTint allfieldtest err: "+e);
                
            }
//            isCompressed
//            ET_allsee
//            ET_eventname
//            event
//            editTextDate
//            version_ht
//            ET_HashTag
//            eventStaticTag
            return true;
        }

        
    }

    final class HashTintTransfer extends AbstractAttachment {

        private final String hashName;

        HashTintTransfer(final byte[] namebyte, final byte transactionVersion) throws BNDException.NotValidException {
            //super(buffer, transactionVersion);
            System.out.println("hashtransfer ! a1");
            this.hashName = Convert.toString(namebyte);
        }

        HashTintTransfer(final JSONObject attachmentData) {
           // super(attachmentData);
           System.out.println("hashtransfer ! a2");
            this.hashName = Convert.nullToEmpty((String) attachmentData.get("hashName"));
        }

        public HashTintTransfer(final String hashName) {
            System.out.println("hashtransfer ! a3");
            this.hashName = hashName;
        }

        @Override
        public TransactionType getTransactionType() {
            System.out.println("hashtransfer ! a4");
            
            return TransactionType.Hashing.HASHTINT_TRANSFER;
        }

        @Override
        int getMySize() {
            System.out.println("hashtransfer ! a5");
            
            return 1 + Convert.toBytes(hashName).length;
        }

        @Override
        void putMyBytes(final ByteBuffer buffer) {
            System.out.println("hashtransfer ! a6");
            
            byte[] hnbyte = Convert.toBytes(hashName);
            buffer.put((byte)hnbyte.length);
            buffer.put(hnbyte);
        }

        @Override
        void putMyJSON(final JSONObject attachment) {
            System.out.println("hashtransfer ! a7");
            
            attachment.put("hashName", hashName);
        }

        public String getHashName(){
            System.out.println("hashtransfer ! a8");
            
            return hashName;
        }
    }

    
}
