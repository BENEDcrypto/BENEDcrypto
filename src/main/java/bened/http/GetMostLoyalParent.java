package bened.http;

import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;
import bened.Account;
import bened.BNDException;
import bened.util.Convert;
import bened.util.BenedTree;

import javax.servlet.http.HttpServletRequest;

public class GetMostLoyalParent extends BenedTree.APIHierarchyRequestHandler {

    static final GetMostLoyalParent instance = new GetMostLoyalParent();

    private GetMostLoyalParent() {
        super(new APITag[] {APITag.MGM}, "accountChild");
    }

    @Override
    protected JSONStreamAware processHierarchyRequest(HttpServletRequest req) throws BNDException {

        long accountId = ParameterParser.getAccountId(req, "accountChild", true);
        if (accountId == 0L)
            return BenedTree.createErrorResponse("Invalid \"accountChild\"!", 9899);
        Account account = Account.getAccount(accountId);
        if (account == null)
            throw new BNDException.NotValidException("Invalid account");
        BenedTree.AccountLoyaltyContainer container = BenedTree.getMostLoyalParentFaster (account);
        if (container == null || container.account == null)
            throw new BNDException.NotValidException ("Loyal parent not found (2)");
        JSONObject response = new JSONObject();
        response.put("loyalParent", Convert.rsAccount(container.account.getId()));
        response.put("loyalty", container.loyalty);
        return response;
    }
}
