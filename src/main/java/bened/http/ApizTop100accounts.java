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
import bened.util.JSON;
import java.io.Writer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;




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
    
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        process(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        process(req, resp);
    }

    String httrx;
    int index = 0;
    private static int _height = -1; 
    private static HashMap<Long, Long> top100acc = new HashMap<>();
    private void process(HttpServletRequest req, HttpServletResponse resp) throws IOException {
       if(Bened.getBlockchainProcessor().isDownloading()){
         top100acc.clear();
       }else{
        int _ght = Bened.getBlockchain().getHeight();
        if (_height != _ght) {
            _height = _ght;
            top100acc.clear();
            try (Connection con = Db.db.getConnection(); PreparedStatement pstmt = con.prepareStatement("SELECT id,balance FROM account WHERE latest IS TRUE ORDER BY balance DESC LIMIT 100  ");
                ResultSet rs = pstmt.executeQuery();) {
                    while (rs.next()) {
                        top100acc.put(rs.getLong(1), rs.getLong(2));
                    }
            } catch (SQLException ex) {
                Logger.getLogger(ApizTop100accounts.class.getName()).log(Level.SEVERE, null, ex);
            }

        }
       } 
        
        
        
       if(req.getRequestURI().contains("json")){
        // Set response values now in case we create an asynchronous context
        resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate, private");
        resp.setHeader("Pragma", "no-cache");
        resp.setDateHeader("Expires", 0);
        resp.setContentType("text/plain; charset=UTF-8");

        JSONStreamAware response = JSON.emptyJSON;
        long startTime = System.currentTimeMillis();
        JSONObject json = new JSONObject();
        
        index = 1;
        if(top100acc.isEmpty()){
           JSONObject jsus = new JSONObject();
                jsus.put("balance", "blockchain is downloading... please wite...");
                jsus.put("effectivebalance", "--");
                jsus.put("publickey", "--" );
                jsus.put("address",  "--");
                json.put(index++, jsus); 
        }else{
        top100acc.entrySet().stream()
            .sorted(Map.Entry.<Long, Long>comparingByValue().reversed()) 
            .forEach((t) -> {
                JSONObject jsus = new JSONObject();
                jsus.put("balance", new DecimalFormat("#0.000000").format(Double.valueOf(t.getValue())/1000000));
                jsus.put("effectivebalance", Account.getAccount(t.getKey()).getEffectiveBalanceBND(Bened.getBlockchain().getHeight()));
                jsus.put("publickey", Convert.toHexString(  Account.getPublicKey(t.getKey())) );
                jsus.put("address",  Crypto.rsEncode(t.getKey()) );
                json.put(index++, jsus);
             });
        }
        response = JSON.prepare(json);
        
            if (response != null) {
                if (response instanceof JSONObject) {
                    ((JSONObject) response).put("requestProcessingTime", System.currentTimeMillis() - startTime);
                }
                try (Writer writer = resp.getWriter()) {
                    JSON.writeJSONString(response, writer);
                }
            }
        
           
       }else{
        resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate, private");
        resp.setHeader("Pragma", "no-cache");
        resp.setDateHeader("Expires", 0);
        String reqgetparam = req.getParameter("param");
       
        
            httrx = "<p style=\"margin-bottom: 0px;\">Top 100 accounts </p>";
            httrx =httrx + "<table class=\"table table-striped\" id=\"Top 100 accounts\" style=\"margin-bottom: 0px; display: table;\">";
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
            
        
        
        try (PrintStream out = new PrintStream(resp.getOutputStream())) {
            out.print(header);
//            out.print(body);
            out.print("<div>"+httrx+"</div>");
            out.print(footer);
        }
        }
        
        
        
        
        
        

    }

    
    
    
//    private static final String header =
//            "<!DOCTYPE html>\n" +
//                    "<html>\n" +
//                    "<head>\n" +
//                    "    <meta charset=\"UTF-8\"/>\n" +
//                    "</head>\n" +
//                    "<body>\n";
//
//    private static final String footer =
//                    "</body>\n" +
//                    "</html>\n";
//    String httrx;
//    @Override
//    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
//        resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate, private");
//        resp.setHeader("Pragma", "no-cache");
//        resp.setDateHeader("Expires", 0);
//        String reqgetparam = req.getParameter("param");
//       
//        try (Connection con = Db.db.getConnection();
//            PreparedStatement pstmt = con.prepareStatement("SELECT * FROM account WHERE latest=? ORDER BY balance DESC LIMIT 100  ")) {
//            pstmt.setBoolean(1, true);
//            HashMap<Long, Long> top100acc = new HashMap<>();
//            try (ResultSet rs = pstmt.executeQuery()) {
//                //Account account = null;
//                int _heig =Bened.getBlockchain().getHeight();
//                while(rs.next()) {
//                   // account = Account.getAccount(rs.getLong("id"));
//                   // top100acc.put(Convert.toHexString( Account.getPublicKey(rs.getLong("id"))) , account.getEffectiveBalanceBND());
//                    //top100acc.put(Convert.toHexString( Account.getPublicKey(rs.getLong("id"))) , account.getGuaranteedBalanceNQT(1441, _heig));
//                    top100acc.put(rs.getLong("id") , rs.getLong("balance"));
//                }
//            }
//            httrx = "<p style=\"margin-bottom: 0px;\">Top 100 accounts </p>";
//            httrx = "<table class=\"table table-striped\" id=\"Top 100 accounts\" style=\"margin-bottom: 0px; display: table;\">";
//            httrx =httrx +" <tbody><tr><td  style=\"font-weight:bold\">Bened balance </td>"
//                    + "<td style=\"width:60%;text-transform:none;word-break:break-all\">Bened Public key</td>"
//                    + "<td style=\"width:20%;text-transform:none;word-break:break-all;font-weight:bold\">Bened adress</td></tr>";
//            
//            top100acc.entrySet().stream()
//            .sorted(Map.Entry.<Long, Long>comparingByValue().reversed()) 
//            .forEach((t) -> {
//                Account account = Account.getAccount(t.getKey());
//                httrx = httrx + "<tr><td  style=\"font-weight:bold\">"+new DecimalFormat("#0.000000").format(Double.valueOf(t.getValue())/1000000)+" BND"+"</td>" // +t.getKey()+
//                        + "<td style=\"width:60%;text-transform:none;word-break:break-all\">"+ Convert.toHexString(  Account.getPublicKey(t.getKey()) )+ "</td>"
//                        + "<td style=\"width:20%;text-transform:none;word-break:break-all;font-weight:bold\"> bened"+ Crypto.rsEncode(t.getKey())+ "</td></tr>";
//             });
//             httrx = httrx +"</tbody></table>";
//            
//        } catch (SQLException e) {
//            throw new RuntimeException(e.toString(), e);
//        }
//        
//        
//        try (PrintStream out = new PrintStream(resp.getOutputStream())) {
//            out.print(header);
////            out.print(body);
//            out.print("<div>"+httrx+"</div>");
//            out.print(footer);
//        }
//    }

  

}
