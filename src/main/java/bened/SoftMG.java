/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package bened;

import java.sql.Connection;
import java.util.List;

/**
 *
 * @author zoi
 */
public interface SoftMG {
    public List<SMGBlock.Payout> check(SMGBlock smgBlock, int height, SMGBlock smgBlockIncognito) throws HGException;
    public boolean canReceive(SMGBlock.Transaction trx);
    public SoftMGs getMetrics(long accountID);
    public long getFixedFee(long amount);
    public void rollbackToBlock(int blockHeight);
    public void shutdown();
    public void init();
    public void popLastBlock();
    public Connection getConnection ();
    public boolean isZeroblockFixed();
    public void zeroblockFixed();
    
    public long _getGenesEm();
}
