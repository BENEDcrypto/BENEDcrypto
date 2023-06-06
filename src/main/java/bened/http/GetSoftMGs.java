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
import bened.BNDException;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import bened.SoftMGs;
public final class GetSoftMGs extends APIServlet.APIRequestHandler {

    static final GetSoftMGs instance = new GetSoftMGs();

    private GetSoftMGs() {
        super(new APITag[] {APITag.MGM}, "account");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws BNDException {
        long accountId = ParameterParser.getAccountId(req, true);
        
        SoftMGs metrics = Bened.softMG().getMetrics(accountId);
        return JSONData.accountBNDmg(metrics,accountId);
    }

}
