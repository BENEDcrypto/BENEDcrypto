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

import bened.Bened;
import bened.util.JSON;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;
import java.text.DecimalFormat;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;





public final class ApizCirculationSupply extends HttpServlet {

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
    
    
   private static int _height = -1; 
   private static String _znachenie = "no result";
   private void process(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
       int _bhg =Bened.getBlockchain().getHeight();
    if(_height!=_bhg){
        _height=_bhg;
        _znachenie = ""+ new DecimalFormat("#0.000000").format((((Bened.softMG()._getGenesEm()*(-1D)))/1000000));       
    }
       
       if(req.getRequestURI().contains("json")){
        resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate, private");
        resp.setHeader("Pragma", "no-cache");
        resp.setDateHeader("Expires", 0);
        resp.setContentType("text/plain; charset=UTF-8");
        JSONStreamAware response = JSON.emptyJSON;
        
        long startTime = System.currentTimeMillis();
        JSONObject json = new JSONObject();
        try{
            
            json.put("CirculationSupply", _znachenie);
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
        try (PrintStream out = new PrintStream(resp.getOutputStream())) {
            out.print(header);
            out.print(_znachenie);
            out.print(footer);
            }
        }
   }


}
