package bened.http;

import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;
import bened.Constants;
import bened.Bened;
import bened.BNDException;

import javax.servlet.http.HttpServletRequest;

public final class GetBaseTarget extends APIServlet.APIRequestHandler {

    static final GetBaseTarget instance = new GetBaseTarget();

    private GetBaseTarget() {
        super(new APITag[] {APITag.INFO}, "height");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws BNDException {
        int height = ParameterParser.getHeight(req);
        if (height < 0 || height > Bened.getBlockchain().getHeight()) {
            height = Bened.getBlockchain().getHeight();
        }
        long baseTarget = Constants.getINITIAL_BASE_TARGET(height);
        JSONObject response = new JSONObject();
        response.put("height", height);
        response.put("initialBaseTarget", baseTarget);
        return response;
    }

}
