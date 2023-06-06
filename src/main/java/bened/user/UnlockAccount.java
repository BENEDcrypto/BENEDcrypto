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

package bened.user;

import bened.Account;
import bened.Block;
import bened.Bened;
import bened.Transaction;
import bened.db.DbIterator;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.SortedSet;
import java.util.TreeSet;

import static bened.user.JSONResponses.LOCK_ACCOUNT;

public final class UnlockAccount extends UserServlet.UserRequestHandler {

    static final UnlockAccount instance = new UnlockAccount();

    private UnlockAccount() {}

    private static final Comparator<JSONObject> myTransactionsComparator = (o1, o2) -> {
        int t1 = ((Number)o1.get("timestamp")).intValue();
        int t2 = ((Number)o2.get("timestamp")).intValue();
        if (t1 < t2) {
            return 1;
        }
        if (t1 > t2) {
            return -1;
        }
        String id1 = (String)o1.get("id");
        String id2 = (String)o2.get("id");
        return id2.compareTo(id1);
    };

    @Override
    JSONStreamAware processRequest(HttpServletRequest req, User user) throws IOException {
        String secretPhrase = req.getParameter("secretPhrase");
        // lock all other instances of this account being unlocked
        Users.getAllUsers().forEach(u -> {
            if (secretPhrase.equals(u.getSecretPhrase())) {
                u.lockAccount();
                if (! u.isInactive()) {
                    u.enqueue(LOCK_ACCOUNT);
                }
            }
        });


        JSONObject response = new JSONObject();
        response.put("response", "unlockAccount");

        if (secretPhrase.length() < 30) {

            response.put("secretPhraseStrength", 1);

        } else {

            response.put("secretPhraseStrength", 5);

        }


        return response;
    }

    @Override
    boolean requirePost() {
        return true;
    }

}
