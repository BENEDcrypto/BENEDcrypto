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

import bened.Block;
import bened.Bened;
import bened.BNDException;
import bened.db.DbIterator;
import bened.util.JSON;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetBlocks extends APIServlet.APIRequestHandler {

    static final GetBlocks instance = new GetBlocks();

    private GetBlocks() {
        super(new APITag[] {APITag.BLOCKS}, "firstIndex", "lastIndex", "timestamp", "includeTransactions", "includeExecutedPhased");
    }

    
    private static int _height = -1; 
    private static JSONStreamAware _znachenie = JSON.emptyJSON;
    int firstIndex = 0;
    int lastIndex = 0;
    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws BNDException {

        int _bgh =Bened.getBlockchain().getHeight();
        int _firstIndex = ParameterParser.getFirstIndex(req);
        int _lastIndex = ParameterParser.getLastIndex(req);
        if(_height!=_bgh || ((JSONObject)_znachenie).isEmpty()
            || firstIndex!=_firstIndex || lastIndex!=_lastIndex    ){
            _height=_bgh;
            _znachenie = new JSONObject();
        
        firstIndex = _firstIndex;
        lastIndex = _lastIndex;
        final int timestamp = ParameterParser.getTimestamp(req);
        boolean includeTransactions = "true".equalsIgnoreCase(req.getParameter("includeTransactions"));
        boolean includeExecutedPhased = "true".equalsIgnoreCase(req.getParameter("includeExecutedPhased"));

        JSONArray blocks = new JSONArray();
        DbIterator<? extends Block> iterator = Bened.getBlockchain().getBlocks(firstIndex, lastIndex);
            while (iterator.hasNext()) {
                Block block = iterator.next();
                if (block.getTimestamp() < timestamp) {
                    iterator.close();
                    break;
                }
                blocks.add(JSONData.block(block, includeTransactions, includeExecutedPhased));
            }
        ((JSONObject)_znachenie).put("blocks", blocks);
        }
        return _znachenie;
    }

}
