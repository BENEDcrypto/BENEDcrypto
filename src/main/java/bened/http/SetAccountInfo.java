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
import bened.Attachment;
import bened.Constants;
import bened.BNDException;
import bened.util.Convert;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

import static bened.http.JSONResponses.INCORRECT_ACCOUNT_DESCRIPTION_LENGTH;
import static bened.http.JSONResponses.INCORRECT_ACCOUNT_NAME_LENGTH;

public final class SetAccountInfo extends CreateTransaction {

    static final SetAccountInfo instance = new SetAccountInfo();

    private SetAccountInfo() {
        super(new APITag[] {APITag.ACCOUNTS, APITag.CREATE_TRANSACTION}, "name", "description");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws BNDException {

        String name = Convert.nullToEmpty(req.getParameter("name")).trim();
        String description = Convert.nullToEmpty(req.getParameter("description")).trim();

        if (name.length() > Constants.MAX_ACCOUNT_NAME_LENGTH) {
            return INCORRECT_ACCOUNT_NAME_LENGTH;
        }

        if (description.length() > Constants.MAX_ACCOUNT_DESCRIPTION_LENGTH) {
            return INCORRECT_ACCOUNT_DESCRIPTION_LENGTH;
        }

        Account account = ParameterParser.getSenderAccount(req);
        Attachment attachment = new Attachment.MessagingAccountInfo(name, description);
        return createTransaction(req, account, attachment);

    }

}
