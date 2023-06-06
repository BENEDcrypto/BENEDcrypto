package bened;

import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Hello world!
 *
 */
public class TestFactory {

    public static SMGBlock.Transaction getTrx(long txID, int stamp, long from, long to, long amount, long fee) {
        SMGBlock.Transaction trx = new SMGBlock.Transaction();
        trx.setAmount(amount);
        trx.setID(txID);
        trx.setFee(fee);
        trx.setReceiver(to);
        trx.setSender(from);
        trx.setStamp(stamp);
        trx.setType(SMGBlock.Type.ORDINARY);
        return trx;
    }

    public static SMGBlock.Transaction getTrxSoftMG(long txID, int stamp, long from, long to, long amount, long fee, Long softBlockID, Long softTxID) {
        SMGBlock.Transaction trx = new SMGBlock.Transaction();
        trx.setAmount(amount);
        trx.setID(txID);
        trx.setFee(fee);
        trx.setReceiver(to);
        trx.setSender(from);
        trx.setStamp(stamp);
        trx.setType(SMGBlock.Type.softMG);
        trx.setSoftMGBlockID(softBlockID);
        trx.setSoftMGTxID(softTxID);
        return trx;
    }


}
