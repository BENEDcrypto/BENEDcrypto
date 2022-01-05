package bened.http;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;
import bened.Account;
import bened.Db;
import bened.Bened;
import bened.InnerException;
import bened.util.Convert;
import bened.util.Logger;
import bened.util.BenedTree;
import javax.servlet.http.HttpServletRequest;
import java.sql.*;
import java.util.*;
import static bened.util.BenedTree.getDirectChildrenOf;
import static bened.util.BenedTree.getParentOf;
import static bened.util.BenedTree.getRootAccountMinimal;
import static bened.util.BenedTree.AccountMinimal;

public class GetAccountHierarchy extends BenedTree.APIHierarchyRequestHandler {

    static final GetAccountHierarchy instance = new GetAccountHierarchy ();

    private GetAccountHierarchy() {
        super(new APITag[] {APITag.ACCOUNTS}, "account");
    }

    public static final int         MAX_DEPTH_PER_REQUEST =                 10;  

    protected JSONStreamAware processHierarchyRequest(HttpServletRequest req) throws InnerException {
        if (Bened.softMG().getConnection() == null) {
            JSONObject response = new JSONObject();
            response.put("errorDescription", "GetAccountChildren API failed to connect to the database");
            response.put("errorCode", "123");
            return response;
        }

        final long accountID = ParameterParser.getAccountId(req, true);
        if (accountID == 0L) {
            return BenedTree.createErrorResponse("Invalid account!", 9699);
        }

        final Account accountObject = Account.getAccount(accountID);

        if (accountObject == null)
            return BenedTree.createErrorResponse("Account "+accountID+" not found", 9601);

        JSONArray array = new JSONArray();

        final AccountMinimal parent = getParentOf(accountID);
        final AccountMinimal account = getRootAccountMinimal(accountID);

        if (parent == null || account == null)
            return BenedTree.createErrorResponse("Impossible to solve hierarchy for this account", 9698);

        List<AccountMinimal>
                layerAll = new ArrayList<>(),
                layerCurrent = new ArrayList<>(),
                layerNext = new ArrayList<>();

        layerAll.add(parent);
        layerCurrent.add(account);

        int currentDepth = 0, internalID = 2;

        while ( !layerCurrent.isEmpty() || !layerNext.isEmpty() ) {
            if (layerCurrent.isEmpty()) {
                List<AccountMinimal> ref = layerCurrent;
                layerCurrent = layerNext;
                layerNext = ref;
                currentDepth++;
            }
            final AccountMinimal target = layerCurrent.get(layerCurrent.size()-1);
            if (currentDepth < MAX_DEPTH_PER_REQUEST) {
                final List<AccountMinimal> children;
                try {
                    children = getDirectChildrenOf(target.id, target.internalID, internalID, false, 0, false);
                } catch (SQLException e) {
                    Logger.logErrorMessage(e.getMessage(), e);
                    layerCurrent.remove(target);
                    continue;
                }
                internalID += children.size();
                layerNext.addAll(children);
            }
            layerCurrent.remove(target);
            layerAll.add(target);
        }


         for (AccountMinimal a : layerAll) {

            if(a.parentInternalID<1)continue;
            array.add(a.toString());
        }

        return array;
    }

}

