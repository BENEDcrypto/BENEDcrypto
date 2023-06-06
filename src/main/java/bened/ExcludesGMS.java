/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package bened;

import java.util.Set;
import java.util.TreeMap;

public class ExcludesGMS {

    private static final TreeMap<Long, Long> BLOCK_LIST = new TreeMap<>();
    private static long[] blockIDs = {0l,1L}; // и так далее - эти айдишники блочатсяя
    
    private static final Set<Long> TrxIDs = Set.of(0L, -7766826245306434741L,-2360739124811072033L);  //{0l,-7766826245306434741L}; // и так далее - эти айдишники блочатсяя
    private static Set<Integer> BlockHt = Set.of(-1, 677321,678141); 
    
    private static boolean initialized = false;


    public static boolean check(long account, int height) {
		synchronized (BLOCK_LIST) {
            if (!initialized) {
                initialized = true;
                for (long ID : blockIDs) {
                    BLOCK_LIST.put(ID, ID);
                }                
            }
            if (height < Constants.THIEF_BLOCK_BEGIN) return false;
            return BLOCK_LIST.containsKey(account);
        }
    }
    
    public static boolean check(SMGBlock.Transaction tx, int height)  {
       if(TrxIDs.contains(tx.getID()) && BlockHt.contains(height)){
           return true;
       }
        
        return false;
    }
}
