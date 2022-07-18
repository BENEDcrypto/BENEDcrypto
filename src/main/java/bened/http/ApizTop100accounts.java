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

package bened.http;

import bened.Account;
import java.io.IOException;
import java.io.PrintStream;
import java.text.DecimalFormat;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import bened.Bened;
import bened.Db;
import bened.Transaction;
import bened.crypto.Crypto;
import bened.util.Convert;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;




public final class ApizTop100accounts extends HttpServlet {

    private static final String header =
            "<!DOCTYPE html>\n" +
                    "<html>\n" +
                    "<head>\n" +
                    "    <meta charset=\"UTF-8\"/>\n" +
                    
                    "</head>\n" +
                    "<body>\n";

    private static final String footer =
                    "</body>\n" +
                    "</html>\n";

    
    
    String httrx;
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate, private");
        resp.setHeader("Pragma", "no-cache");
        resp.setDateHeader("Expires", 0);
        String reqgetparam = req.getParameter("param");
       
        try (Connection con = Db.db.getConnection();
            PreparedStatement pstmt = con.prepareStatement("SELECT * FROM account WHERE latest=? ORDER BY balance DESC LIMIT 100  ")) {
            pstmt.setBoolean(1, true);
            HashMap<Long, Long> top100acc = new HashMap<>();
            try (ResultSet rs = pstmt.executeQuery()) {
                //Account account = null;
                int _heig =Bened.getBlockchain().getHeight();
                while(rs.next()) {
                   // account = Account.getAccount(rs.getLong("id"));
                   // top100acc.put(Convert.toHexString( Account.getPublicKey(rs.getLong("id"))) , account.getEffectiveBalanceBND());
                    //top100acc.put(Convert.toHexString( Account.getPublicKey(rs.getLong("id"))) , account.getGuaranteedBalanceNQT(1441, _heig));
                    top100acc.put(rs.getLong("id") , rs.getLong("balance"));
                }
            }
            httrx = "<p style=\"margin-bottom: 0px;\">Top 100 accounts </p>";
            httrx = "<table class=\"table table-striped\" id=\"Top 100 accounts\" style=\"margin-bottom: 0px; display: table;\">";
            httrx =httrx +" <tbody><tr><td  style=\"font-weight:bold\">Bened balance </td>"
                    + "<td style=\"width:60%;text-transform:none;word-break:break-all\">Bened Public key</td>"
                    + "<td style=\"width:20%;text-transform:none;word-break:break-all;font-weight:bold\">Bened adress</td></tr>";
            
            top100acc.entrySet().stream()
            .sorted(Map.Entry.<Long, Long>comparingByValue().reversed()) 
            .forEach((t) -> {
                Account account = Account.getAccount(t.getKey());
                httrx = httrx + "<tr><td  style=\"font-weight:bold\">"+new DecimalFormat("#0.000000").format(Double.valueOf(t.getValue())/1000000)+" BND"+"</td>" // +t.getKey()+
                        + "<td style=\"width:60%;text-transform:none;word-break:break-all\">"+ Convert.toHexString(  Account.getPublicKey(t.getKey()) )+ "</td>"
                        + "<td style=\"width:20%;text-transform:none;word-break:break-all;font-weight:bold\"> bened"+ Crypto.rsEncode(t.getKey())+ "</td></tr>";
             });
             httrx = httrx +"</tbody></table>";
            
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
        
       
        
//        if(retrun.equals("")){
//            httrx = "<table class=\"table table-striped\" id=\"transaction_info_table\" style=\"margin-bottom: 0px; display: table;\">";
//            httrx =httrx +" <tbody><tr><td  style=\"font-weight:bold\">PaymentAmount:</td><td style=\"width:80%;text-transform:none;word-break:break-all\">";
//            httrx = httrx + new DecimalFormat("#0.000000").format(Double.valueOf(transaction.getAmountNQT())/1000000)+" BND"+"</td></tr><tr><td style=\"font-weight:bold\">Fee:</td><td style=\"width:80%;text-transform:none;word-break:break-all\">";
//            httrx = httrx + new DecimalFormat("#0.000000").format(Double.valueOf(transaction.getFeeNQT())/1000000)+" BND"+"</td></tr><tr><td style=\"font-weight:bold\">Recipient:</td><td style=\"width:80%;text-transform:none;word-break:break-all\">";
//            httrx = httrx + Convert.rsAccount(transaction.getRecipientId())+"</td></tr><tr><td  style=\"font-weight:bold\">Sender:</td><td style=\"width:80%;text-transform:none;word-break:break-all\">";
//            httrx = httrx + Convert.rsAccount(transaction.getSenderId())+"</td></tr><tr><td  style=\"font-weight:bold\">Confirmations:</td><td style=\"width:80%;text-transform:none;word-break:break-all\">";
//            long confirms =  (Bened.getBlockchain().getHeight()-transaction.getHeight())<0?0:(Bened.getBlockchain().getHeight()-transaction.getHeight());
//            httrx = httrx + ((confirms<1440)?confirms:"1440+")+"</td></tr></tbody></table>";
            
            
    //byte[] signature = Convert.emptyToNull(transaction.getSignature());
//            if (signature != null) {
//                json.put("signature", Convert.toHexString(signature));
//                json.put("signatureHash", Convert.toHexString(Crypto.sha256().digest(signature)));
//                json.put("fullHash", transaction.getFullHash());
//                json.put("transaction", transaction.getStringId());
//            }
            
           // httrx = httrx + 
           // </td></tr></tbody>
               // json.put("type", transaction.getType().getType());
//                json.put("subtype", transaction.getType().getSubtype());
//                json.put("timestamp", transaction.getTimestamp());
//                json.put("deadline", transaction.getDeadline());
//                json.put("senderPublicKey", Convert.toHexString(transaction.getSenderPublicKey()));
////                if (transaction.getRecipientId() != 0) {
//                    putAccount(json, "recipient", transaction.getRecipientId());
//                }
                //json.put("amountNQT", String.valueOf(transaction.getAmountNQT()));
        //json.put("feeNQT", String.valueOf(transaction.getFeeNQT()));
        //String referencedTransactionFullHash = transaction.getReferencedTransactionFullHash();
//        if (referencedTransactionFullHash != null) {
//            json.put("referencedTransactionFullHash", referencedTransactionFullHash);
//        }
//        byte[] signature = Convert.emptyToNull(transaction.getSignature());
//        
//        putAccount(json, "sender", transaction.getSenderId());
//        json.put("height", transaction.getHeight());
//        json.put("version", transaction.getVersion());
//        if (transaction.getVersion() > 0) {
//            json.put("ecBlockId", Long.toUnsignedString(transaction.getECBlockId()));
//            json.put("ecBlockHeight", transaction.getECBlockHeight());
//        }
//        }else{
//            httrx = retrun;
//            
//        }    
        

      //  String body = ""+bened.Constants.MAX_BALANCE_BND;
        
        
        try (PrintStream out = new PrintStream(resp.getOutputStream())) {
            out.print(header);
//            out.print(body);
            out.print("<div>"+httrx+"</div>");
            out.print(footer);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate, private");
        resp.setHeader("Pragma", "no-cache");
        resp.setDateHeader("Expires", 0);
       

        String body = ""+bened.Constants.MAX_BALANCE_BND;
        
        
        
            try (PrintStream out = new PrintStream(resp.getOutputStream())) {
                out.print(header);
                out.print(body);
                out.print(footer);
            }
     
        
       
        
    }

}
