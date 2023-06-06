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

import java.io.IOException;
import java.io.PrintStream;
import java.text.DecimalFormat;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import bened.Bened;
import bened.Transaction;
import bened.util.Convert;
import bened.util.JSON;
import java.io.Writer;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;




public final class ApizShow extends HttpServlet {

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
    
    private static String trxid = ""; 
    private static Transaction transaction = null;
    private void process(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    
        String transactionIdString = req.getParameter("Trx");
        String retrun="";
        long transactionId=0;
         String transactionFullHash = null;
        if(trxid != transactionIdString){
            try {
                if (transactionIdString != null) {
                    transactionId = Convert.parseUnsignedLong(transactionIdString);
                    transaction = Bened.getBlockchain().getTransaction(transactionId);
                } else {
                    transaction = Bened.getBlockchain().getTransactionByFullHash(transactionFullHash);
                    if (transaction == null) {
                        retrun= "UNKNOWN TRANSACTION";
                    }
                }
                if (transaction == null) {
                    transaction = Bened.getTransactionProcessor().getUnconfirmedTransaction(transactionId);
                    if (transaction == null) {
                        retrun= "UNKNOWN TRANSACTION";
                    }
                }
            } catch (RuntimeException e) {
                retrun= "INCORRECT TRANSACTION";
            }        
            trxid=transactionIdString;
        }
        
        ///////////gettrx
        String httrx = "";
        
        
        ///// do
       if(req.getRequestURI().contains("json")){
        resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate, private");
        resp.setHeader("Pragma", "no-cache");
        resp.setDateHeader("Expires", 0);
        resp.setContentType("text/plain; charset=UTF-8");
        JSONStreamAware response = JSON.emptyJSON;
        long startTime = System.currentTimeMillis();
        JSONObject json = new JSONObject();
        JSONObject jtrx = new JSONObject();
        try{
            if(retrun.equals("")){
            jtrx.put("PaymentAmount", ""+ new DecimalFormat("#0.000000").format(Double.valueOf(transaction.getAmountNQT())/1000000));
            jtrx.put("Fee",           ""+ new DecimalFormat("#0.000000").format(Double.valueOf(transaction.getFeeNQT())/1000000));
            jtrx.put("Recipient",     ""+ Convert.rsAccount(transaction.getRecipientId()) );
            jtrx.put("Sender",        ""+ Convert.rsAccount(transaction.getSenderId()) );
            long confirms =  (Bened.getBlockchain().getHeight()-transaction.getHeight())<0?0:(Bened.getBlockchain().getHeight()-transaction.getHeight());
            jtrx.put("Confirmations", ""+ ((confirms<1440)?confirms:"1440+") );
        }else{
            jtrx.put("Error get data", "No Data from transaction "+transactionIdString);
        }   
        json.put("Transaction", jtrx);
            response = JSON.prepare(json);
            } finally {
            if (response != null) {
                if (response instanceof JSONObject) {
                    ((JSONObject) response).put("requestProcessingTime", System.currentTimeMillis() - startTime);
                }
                try (Writer writer = resp.getWriter()) {
                    JSON.writeJSONString(response, writer);
                }
            }
        }
           
       }else{
        resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate, private");
        resp.setHeader("Pragma", "no-cache");
        resp.setDateHeader("Expires", 0);
        if(retrun.equals("")){
            httrx = "<table class=\"table table-striped\" id=\"transaction_info_table\" style=\"margin-bottom: 0px; display: table;\">";
            httrx =httrx +" <tbody><tr><td  style=\"font-weight:bold\">PaymentAmount:</td><td style=\"width:80%;text-transform:none;word-break:break-all\">";
            httrx = httrx + new DecimalFormat("#0.000000").format(Double.valueOf(transaction.getAmountNQT())/1000000)+" BND"+"</td></tr><tr><td style=\"font-weight:bold\">Fee:</td><td style=\"width:80%;text-transform:none;word-break:break-all\">";
            httrx = httrx + new DecimalFormat("#0.000000").format(Double.valueOf(transaction.getFeeNQT())/1000000)+" BND"+"</td></tr><tr><td style=\"font-weight:bold\">Recipient:</td><td style=\"width:80%;text-transform:none;word-break:break-all\">";
            httrx = httrx + Convert.rsAccount(transaction.getRecipientId())+"</td></tr><tr><td  style=\"font-weight:bold\">Sender:</td><td style=\"width:80%;text-transform:none;word-break:break-all\">";
            httrx = httrx + Convert.rsAccount(transaction.getSenderId())+"</td></tr><tr><td  style=\"font-weight:bold\">Confirmations:</td><td style=\"width:80%;text-transform:none;word-break:break-all\">";
            long confirms =  (Bened.getBlockchain().getHeight()-transaction.getHeight())<0?0:(Bened.getBlockchain().getHeight()-transaction.getHeight());
            httrx = httrx + ((confirms<1440)?confirms:"1440+")+"</td></tr></tbody></table>";
            
        }else{
            httrx = retrun+" No Data from transaction "+transactionIdString;
        }    
        try (PrintStream out = new PrintStream(resp.getOutputStream())) {
            out.print(header);
//            out.print(body);
            out.print("<div>"+httrx+"</div>");
            out.print(footer);
        }
   }
    
    }
       
    

   

}
