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
import bened.http.APIServlet.APIRequestHandler;
import bened.peer.Peer;
import bened.peer.Peers;
import bened.util.Convert;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

import static bened.http.JSONResponses.MISSING_PEER;

public class AddPeer extends APIRequestHandler {

    static final AddPeer instance = new AddPeer();
    
    private AddPeer() {
        super(new APITag[] {APITag.NETWORK}, "peer");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest request)
            throws BNDException {
        String peerAddress = Convert.emptyToNull(request.getParameter("peer"));
        if (peerAddress == null) {
            return MISSING_PEER;
        }
        if(peerAddress.contains("://"))peerAddress= peerAddress.substring(peerAddress.indexOf("://")+3, peerAddress.length());
        JSONObject response = new JSONObject();
        Peer peer = Peers.findOrCreatePeer(peerAddress, true);
        if (peer != null) {
            boolean isNewlyAdded = Peers.addPeer(peer, peerAddress);
            Peers.connectPeer(peer);
            response = JSONData.peer(peer);
            response.put("isNewlyAdded", isNewlyAdded);
        } else {
            response.put("errorCode", 8);
            response.put("errorDescription", "Failed to add peer");
        }
        return response;
    }

    @Override
    protected final boolean requirePost() {
        return true;
    }

    @Override
    protected boolean requirePassword() {
        return true;
    }

    @Override
    protected boolean allowRequiredBlockParameters() {
        return false;
    }

    @Override
    protected boolean requireBlockchain() {
        return false;
    }

}
