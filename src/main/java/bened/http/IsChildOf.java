package bened.http;


import bened.Genesis;
import bened.Bened;
import bened.BNDException;
import bened.Constants;
import bened.util.Convert;
import bened.util.BenedTree;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.servlet.http.HttpServletRequest;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;



public class IsChildOf extends BenedTree.APIHierarchyRequestHandler {

    static final IsChildOf instance = new IsChildOf();

    private IsChildOf() {
        super(new APITag[] {APITag.ACCOUNTS}, "childRS", "parentRS");
    }

    @Override
    protected JSONStreamAware processHierarchyRequest(HttpServletRequest req) throws BNDException {

        long child = ParameterParser.getAccountId(req, "childRS", true);
        long parent = ParameterParser.getAccountId(req, "parentRS", true);

        JSONObject response = new JSONObject();

        if (child == 0L || parent == 0L)
            return BenedTree.createErrorResponse("Invalid parameters!", 9950);

        if (child == parent) {
            response.put("errorDescription", "Child equals parent");
            response.put("errorCode", "9502");
            return response;
        }

        final Connection conn = Bened.softMG().getConnection();
        if (conn == null) {
            final JSONObject err = new JSONObject();
            err.put("errorDescription","Failed to retrieve database connection!");
            err.put("errorCode","9506");
            return err;
        }

        PreparedStatement statement = null;
        ResultSet rs = null;


        try {
            long solvedParent = child;
            try {
                statement = conn.prepareStatement("select parent_id from soft where id=?");
                statement.setLong(1, child);
                try {
                    rs = statement.executeQuery();
                    while (rs.next()) {
                        solvedParent = rs.getLong(1);
                    }
                    rs.close();
                    statement.close();
                    
                    if (solvedParent == child) {
                        response.put("errorDescription", "Account " + Convert.rsAccount(child) + " is not part of any hierarchy");
                        response.put("errorCode", "9503");
                        return response;
                    }

                    int i = 0;
                    while (solvedParent != parent && solvedParent != Genesis.CREATOR_ID) {
                        statement = conn.prepareStatement("select parent_id from soft where id=?");
                        statement.setLong(1, solvedParent);
                        rs = statement.executeQuery();
                        boolean haveParent = false;
                        while (rs.next()) {
                            solvedParent = rs.getLong(1);
                            haveParent = true;
                        }
                        if (i == (Bened.getBlockchain().getHeight()<Constants.change_evendek22? Constants.affil_struct : Constants.affil_struct*100)) {   //// было 88
                            solvedParent = Genesis.CREATOR_ID;
                            break;
                        }
                        rs.close();
                        statement.close();
                        
                        if (!haveParent) {
                            response.put("errorDescription", "Error solving top-level parent account - it is impossible situation, please report this");
                            response.put("errorCode", "9504");
                            return response;
                        }
                        i++;
                    }
                    if (solvedParent == parent) {
                        response.put("childRS", req.getParameter("childRS"));
                        response.put("isChild", true);
                        return response;
                    }
                    if (solvedParent == Genesis.CREATOR_ID) {
                        response.put("childRS", req.getParameter("childRS"));
                        response.put("isChild", false);
                        return response;
                    }
                    response.put("errorDescription", "Unknown error");
                    response.put("errorCode", "9505");
                    return response;
                } finally {
                   if(rs!=null) rs.close();
                }
            } finally {
                if(statement!=null)statement.close();
            }
        } catch (SQLException e) {
            throw new BNDException.NotValidException (e.getMessage(), e.getCause());
        }
    }
}
