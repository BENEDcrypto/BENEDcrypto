package bened.http;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;
import bened.Account;
import bened.Bened;
import bened.BNDException;
import bened.util.Logger;
import bened.util.BenedTree;
import javax.servlet.http.HttpServletRequest;
import java.sql.*;
import java.util.*;
import static bened.util.BenedTree.getDirectChildrenOf;
import static bened.util.BenedTree.getParentOf;
import static bened.util.BenedTree.getRootAccountMinimal;
import static bened.util.BenedTree.AccountMinimal;

public class GetAccountChildren extends BenedTree.APIHierarchyRequestHandler {

    static final GetAccountChildren instance = new GetAccountChildren ();

    private GetAccountChildren() {
        super(new APITag[] {APITag.ACCOUNTS}, "account", "firstIndex");
    }

 //   public static final int         MAX_DEPTH_PER_REQUEST =                 10;    //// было 88

    int bght=-1;
    long tacc=-1;
    private static JSONObject response = new JSONObject();
    @Override
    protected JSONStreamAware processHierarchyRequest(HttpServletRequest req) throws BNDException {
        if (Bened.softMG().getConnection() == null) {
            JSONObject response = new JSONObject();
            response.put("errorDescription", "GetAccountChildren API failed to connect to the database");
            response.put("errorCode", "123");
            return response;
        }
        int tech=Bened.getBlockchain().getHeight();
        final long accountID = ParameterParser.getAccountId(req, true);
        if (accountID == 0L) {
                return BenedTree.createErrorResponse("Invalid account!", 9699);
            }
        if(bght!=tech || tacc!=accountID ){
            
            final int startIndex = ParameterParser.getFirstIndex(req);
            final Account accountObject = Account.getAccount(accountID);
            if (accountObject == null){
                return BenedTree.createErrorResponse("Account "+accountID+" not found", 9601);
            }
            final AccountMinimal parent = getParentOf(accountID);
            final AccountMinimal account = getRootAccountMinimal(accountID);
            if (parent == null || account == null){
                return BenedTree.createErrorResponse("Impossible to solve hierarchy for this account", 9698);
            }
            List<AccountMinimal> children;
            try {
                children = getDirectChildrenOf(accountID, 1, 2, true, startIndex, true);
            } catch (SQLException e) {
                Logger.logErrorMessage(e.getMessage(), e);
                return BenedTree.createErrorResponse("Failed to process request", 9699);
            }
            JSONArray childrenJson = new JSONArray();
            for (AccountMinimal a : children) {
                childrenJson.add(a.toJSONObject());
            }
            response.put("children", childrenJson); 
            bght=tech;
            tacc=accountID;
            }

        return response;
    }

}

