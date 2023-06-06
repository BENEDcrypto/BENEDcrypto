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

import bened.BNDException;
import bened.Bened;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import bened.SimplDimpl;
import bened.util.JSON;
import org.json.simple.JSONObject;


public final class GetBNDActivGenerators extends APIServlet.APIRequestHandler {

    static final GetBNDActivGenerators instance = new GetBNDActivGenerators();

    private GetBNDActivGenerators() {
        super(new APITag[] {APITag.MGM});
    }

    
    private static int _height = -1; 
    private static JSONStreamAware _znachenie = JSON.emptyJSON;
    static int key=0;
    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws BNDException {
        if(Bened.getBlockchainProcessor().isDownloading()) {
            JSONObject retisdownl = new JSONObject();
            JSONObject json_end = new JSONObject();
            json_end.put("rs", "no result blockchain is downloading ..." ); 
            json_end.put("id", "--");
            json_end.put("effectBalans", "--");
            json_end.put("dedline", "--");
            retisdownl.put(++key,json_end );
            return retisdownl;
        }
        int _bgh =Bened.getBlockchain().getHeight();
        if((_bgh-_height)>5){
            synchronized (_znachenie) {
                key=0;
                _height=_bgh;
                _znachenie = emptyJSA();
                new Thread(() -> { SimplDimpl.getBNDgenerators((JSONObject)_znachenie, key);}).start();
            }
        }
        return _znachenie;
    }
    
    static JSONStreamAware emptyJSA(){
        JSONObject json_list = new JSONObject();
        JSONObject json_end = new JSONObject();
            json_end.put("rs", "the result is being prepared for "+_height+" block, please wait a while and refresh the page..." ); 
            json_end.put("id", "--");
            json_end.put("effectBalans", "--");
            json_end.put("dedline", "--");
        json_list.put(++key,json_end );
        return json_list;
    }

}
