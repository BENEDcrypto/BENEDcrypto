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

import bened.Constants;
import bened.peer.Hallmark;
import bened.util.Convert;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

import static bened.http.JSONResponses.INCORRECT_DATE;
import static bened.http.JSONResponses.INCORRECT_HOST;
import static bened.http.JSONResponses.INCORRECT_WEIGHT;
import static bened.http.JSONResponses.MISSING_DATE;
import static bened.http.JSONResponses.MISSING_HOST;
import static bened.http.JSONResponses.MISSING_WEIGHT;


public final class MarkHost extends APIServlet.APIRequestHandler {

    static final MarkHost instance = new MarkHost();

    private MarkHost() {
        super(new APITag[] {APITag.TOKENS}, "secretPhrase", "host", "weight", "date");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws ParameterException {

        String secretPhrase = ParameterParser.getSecretPhrase(req, true);
        String host = Convert.emptyToNull(req.getParameter("host"));
        String weightValue = Convert.emptyToNull(req.getParameter("weight"));
        String dateValue = Convert.emptyToNull(req.getParameter("date"));
        if (host == null) {
            return MISSING_HOST;
        } else if (weightValue == null) {
            return MISSING_WEIGHT;
        } else if (dateValue == null) {
            return MISSING_DATE;
        }

        if (host.length() > 100) {
            return INCORRECT_HOST;
        }

        int weight;
        try {
            weight = Integer.parseInt(weightValue);
            if (weight <= 0 || weight > Constants.MAX_BALANCE_BND) {
                return INCORRECT_WEIGHT;
            }
        } catch (NumberFormatException e) {
            return INCORRECT_WEIGHT;
        }

        try {

            String hallmark = Hallmark.generateHallmark(secretPhrase, host, weight, Hallmark.parseDate(dateValue));

            JSONObject response = new JSONObject();
            response.put("hallmark", hallmark);
            return response;

        } catch (RuntimeException e) {
            return INCORRECT_DATE;
        }

    }

    @Override
    protected boolean requirePost() {
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
