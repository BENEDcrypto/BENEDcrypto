/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package bened;

import static bened.Constants.LAST_KNOWN_BLOCK;
import static bened.Constants.techForgeDelayMs;
import bened.util.Logger;
import java.math.BigInteger;
import org.json.simple.JSONObject;

/**
 *
 * @author du44
 */
public class SimplDimpl {

    private static int delayTime;
    
   public static JSONObject getBNDgenerators(JSONObject json_list, int key){
        Block lastBlock = Bened.getBlockchain().getLastBlock();
        int _height = lastBlock.getHeight();
        //JSONObject json_list = new JSONObject();
        JSONObject json_end = new JSONObject();
        json_end.put("rs", "the result is prepared for "+_height+" block" ); 
            json_end.put("id", "--");
            json_end.put("effectBalans", "--");
            json_end.put("dedline", "--");
        json_list.put(++key,json_end );
        JSONObject json_ = new JSONObject();
        try {

    return BlockDb.getBlockGenerators( (_height>15000)?(_height-770):(10), json_list, key  );
    }catch (Exception e) {
            json_end = new JSONObject();
            json_end.put("rs", "CRASH get gen act:"+e ); 
            json_end.put("id", "--");
            json_end.put("effectBalans", "--");
            json_end.put("dedline", "--");
            json_list.put(++key,json_end ); 
            return json_list;
        }
    }



     public static boolean verifyHit(BigInteger hit, BigInteger effectiveBalance, Block previousBlock, int timestamp) {
        
        int elapsedTime = timestamp - previousBlock.getTimestamp();
        if (elapsedTime <= 0) {
            Logger.logErrorMessage("verefihit elapsedtime("+elapsedTime+") <=0 + ---> return false");
            return false;
        }

        
        
        BigInteger effectiveBaseTarget = BigInteger.valueOf(previousBlock.getBaseTarget()).multiply(effectiveBalance);
        BigInteger prevTarget = effectiveBaseTarget.multiply(BigInteger.valueOf(elapsedTime - 1));
        BigInteger target = prevTarget.add(effectiveBaseTarget);
        boolean _verhit =hit.compareTo(target) < 0
                && (hit.compareTo(prevTarget) >= 0
                || (Constants.isTestnet ? elapsedTime > 600 : elapsedTime > techForgeDelayMs/30) //16,6 mnt -- 1000 sek
                || Constants.isOffline);
        
        if(!_verhit){
            if((previousBlock.getHeight()+1)<LAST_KNOWN_BLOCK){
                _verhit = hit.compareTo(target) < 0 && Constants.verbadtime(previousBlock.getTimestamp()); 
            }
        }

        return _verhit; 
    }
     
     static void setDelay(int delay) {
        SimplDimpl.delayTime = delay;
    }
    
}
