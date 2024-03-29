/** ****************************************************************************
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
 ***************************************************************************** */
package bened;

import bened.AccountLedger.LedgerEvent;
import static bened.Constants.LAST_KNOWN_BLOCK;
import bened.crypto.Crypto;
import bened.util.Convert;
import bened.util.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

final class BlockImpl implements Block {

    private final int version;
    private final int timestamp;
    private final long previousBlockId;
    private volatile byte[] generatorPublicKey;
    private final byte[] previousBlockHash;
    private final long totalAmountNQT;
    private final long totalFeeNQT;
    private final int payloadLength;
    private final byte[] generationSignature;
    private final byte[] payloadHash;
    private volatile List<TransactionImpl> blockTransactions;

    private byte[] blockSignature;
    private BigInteger cumulativeDifficulty = BigInteger.ZERO;
    private long baseTarget = Constants.getINITIAL_BASE_TARGET(0);
    private volatile long nextBlockId;
    private int height = -1;
    private volatile long id;
    private volatile String stringId = null;
    private volatile long generatorId;
    private volatile byte[] bytes = null;
    
    public String otner_cumulativeDifficulty = "";
    public long other_baseTarget = 0;

    BlockImpl(int version, int timestamp, long previousBlockId, long totalAmountNQT, long totalFeeNQT, int payloadLength, byte[] payloadHash,
            byte[] generatorPublicKey, byte[] generationSignature, byte[] previousBlockHash, List<TransactionImpl> transactions, String secretPhrase) {
        this(version, timestamp, previousBlockId, totalAmountNQT, totalFeeNQT, payloadLength, payloadHash,
                generatorPublicKey, generationSignature, null, previousBlockHash, transactions, -100 ,"-101");
        blockSignature = Crypto.sign(bytes(), secretPhrase);
        bytes = null;
    }

    BlockImpl(int version, int timestamp, long previousBlockId, long totalAmountNQT, long totalFeeNQT, int payloadLength, byte[] payloadHash,
            byte[] generatorPublicKey, byte[] generationSignature, byte[] blockSignature, byte[] previousBlockHash, List<TransactionImpl> transactions, 
            long _other_baseTarget, String _otner_cumulativeDifficulty) {
        this.version = version;
        this.timestamp = timestamp;
        this.previousBlockId = previousBlockId;
        this.totalAmountNQT = totalAmountNQT;
        this.totalFeeNQT = totalFeeNQT;
        this.payloadLength = payloadLength;
        this.payloadHash = payloadHash;
        this.generatorPublicKey = generatorPublicKey;
        this.generationSignature = Arrays.copyOfRange(generationSignature, 0, version<1?64:32) ; //  generationSignature;  // kastyl pri perehode na 2.1.214
        this.blockSignature = blockSignature;
        this.previousBlockHash = previousBlockHash;
        if (transactions != null) {
            this.blockTransactions = Collections.unmodifiableList(transactions);
        }
        this.other_baseTarget = _other_baseTarget;
        this.otner_cumulativeDifficulty= _otner_cumulativeDifficulty;
    }

    BlockImpl(int version, int timestamp, long previousBlockId, long totalAmountNQT, long totalFeeNQT, int payloadLength,
            byte[] payloadHash, long generatorId, byte[] generationSignature, byte[] blockSignature,
            byte[] previousBlockHash, BigInteger cumulativeDifficulty, long baseTarget, long nextBlockId, int height, long id,
            List<TransactionImpl> blockTransactions) {
        this(version, timestamp, previousBlockId, totalAmountNQT, totalFeeNQT, payloadLength, payloadHash,
                null, generationSignature, blockSignature, previousBlockHash, null, -200, "-201");
        this.cumulativeDifficulty = cumulativeDifficulty;
        this.baseTarget = baseTarget;
        this.nextBlockId = nextBlockId;
        this.height = height;
        this.id = id;
        this.generatorId = generatorId;
        this.blockTransactions = blockTransactions;
    }

    @Override
    public int getVersion() {
        return version;
    }

    @Override
    public int getTimestamp() {
        return timestamp;
    }

    @Override
    public long getPreviousBlockId() {
        return previousBlockId;
    }

    @Override
    public byte[] getGeneratorPublicKey() {
        if (generatorPublicKey == null) {
            generatorPublicKey = Account.getPublicKey(generatorId);
        }
        return generatorPublicKey;
    }

    @Override
    public byte[] getPreviousBlockHash() {
        return previousBlockHash;
    }

    @Override
    public long getTotalAmountNQT() {
        return totalAmountNQT;
    }

    @Override
    public long getTotalFeeNQT() {
        return totalFeeNQT;
    }

    @Override
    public int getPayloadLength() {
        return payloadLength;
    }

    @Override
    public byte[] getPayloadHash() {
        return payloadHash;
    }

    @Override
    public byte[] getGenerationSignature() {
        return generationSignature;
    }

    @Override
    public byte[] getBlockSignature() {
        return blockSignature;
    }

    @Override
    public List<TransactionImpl> getTransactions() {
        if (this.blockTransactions == null) {
            List<TransactionImpl> transactions = Collections.unmodifiableList(TransactionDb.findBlockTransactions(getId()));
            for (TransactionImpl transaction : transactions) {
                transaction.setBlock(this);
            }
            this.blockTransactions = transactions;
        }
        return this.blockTransactions;
    }

    @Override
    public long getBaseTarget() {
        return baseTarget;
    }

    @Override
    public BigInteger getCumulativeDifficulty() {
        return cumulativeDifficulty;
    }

    @Override
    public long getNextBlockId() {
        return nextBlockId;
    }

    void setNextBlockId(long nextBlockId) {
        this.nextBlockId = nextBlockId;
    }

    @Override
    public int getHeight() {
        if (height == -1) {
            throw new IllegalStateException("Block height not yet set");
        }
        return height;
    }

    @Override
    public long getId() {
        if (id == 0) {
            if (blockSignature == null) {
                throw new IllegalStateException("Block is not signed yet");
            }
            byte[] hash = Crypto.sha256().digest(bytes());
            BigInteger bigInteger = new BigInteger(1, new byte[]{hash[7], hash[6], hash[5], hash[4], hash[3], hash[2], hash[1], hash[0]});
            id = bigInteger.longValue();
            stringId = bigInteger.toString();
        }
        return id;
    }

    @Override
    public String getStringId() {
        if (stringId == null) {
            getId();
            if (stringId == null) {
                stringId = Long.toUnsignedString(id);
            }
        }
        return stringId;
    }

    @Override
    public long getGeneratorId() {
        if (generatorId == 0) {
            generatorId = Account.getId(getGeneratorPublicKey());
        }
        return generatorId;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof BlockImpl && this.getId() == ((BlockImpl) o).getId();
    }

    @Override
    public int hashCode() {
        return (int) (getId() ^ (getId() >>> 32));
    }

    @Override
    public JSONObject getJSONObject() {
        JSONObject json = new JSONObject();
        json.put("version", version);
        json.put("timestamp", timestamp);
        json.put("previousBlock", Long.toUnsignedString(previousBlockId));
        json.put("totalAmountNQT", totalAmountNQT);
        json.put("totalFeeNQT", totalFeeNQT);
        json.put("payloadLength", payloadLength);
        json.put("payloadHash", Convert.toHexString(payloadHash));
        json.put("generatorPublicKey", Convert.toHexString(getGeneratorPublicKey()));
        json.put("generationSignature", Convert.toHexString(generationSignature));
        if (version > 1) {
            json.put("previousBlockHash", Convert.toHexString(previousBlockHash));
        }
        json.put("blockSignature", Convert.toHexString(blockSignature));
        JSONArray transactionsData = new JSONArray();
        getTransactions().forEach(transaction ->{
            transactionsData.add(transaction.getJSONObject());
        });
        json.put("transactions", transactionsData);
        //
        json.put("baseTarget", Long.toUnsignedString(baseTarget));
        json.put("cumulativeDifficulty", cumulativeDifficulty.toString());
        return json;
    }

    static BlockImpl parseBlock(JSONObject blockData) throws BNDException.NotValidException {
        try {
            int version = ((Long) blockData.get("version")).intValue();
            int timestamp = ((Long) blockData.get("timestamp")).intValue();
            long previousBlock = Convert.parseUnsignedLong((String) blockData.get("previousBlock"));
            long totalAmountNQT = Convert.parseLong(blockData.get("totalAmountNQT"));
            long totalFeeNQT = Convert.parseLong(blockData.get("totalFeeNQT"));
            int payloadLength = ((Long) blockData.get("payloadLength")).intValue();
            byte[] payloadHash = Convert.parseHexString((String) blockData.get("payloadHash"));
            byte[] generatorPublicKey = Convert.parseHexString((String) blockData.get("generatorPublicKey"));
            byte[] generationSignature = Convert.parseHexString((String) blockData.get("generationSignature"));
            byte[] blockSignature = Convert.parseHexString((String) blockData.get("blockSignature"));
            byte[] previousBlockHash = version == 1 ? null : Convert.parseHexString((String) blockData.get("previousBlockHash"));
            List<TransactionImpl> blockTransactions = new ArrayList<>();
            for (Object transactionData : (JSONArray) blockData.get("transactions")) {
                blockTransactions.add(TransactionImpl.parseTransaction((JSONObject) transactionData));
            }
            long _other_baseTarget = Long.parseLong((String) blockData.get("baseTarget"));
            String _other_cumulativeDifficulty =(String) blockData.get("cumulativeDifficulty");
            if(_other_baseTarget==0 || _other_cumulativeDifficulty.isBlank()){
                System.out.println("ahtung pb !!!");
                Logger.logErrorMessage("from node json data no parsing other Cd and BaseTarget");
            }
            
            BlockImpl block = new BlockImpl(version, timestamp, previousBlock, totalAmountNQT, totalFeeNQT, payloadLength, payloadHash, generatorPublicKey,
                    generationSignature, blockSignature, previousBlockHash, blockTransactions, _other_baseTarget, _other_cumulativeDifficulty);
            if (!block.checkSignature()) {
                throw new BNDException.NotValidException("Invalid block signature");
            }
            return block;
        } catch (BNDException.NotValidException | RuntimeException e) {
            Logger.logDebugMessage("Failed to parse block: " + blockData.toJSONString());
            throw e;
        }
    }

    @Override
    public byte[] getBytes() {
        return Arrays.copyOf(bytes(), bytes.length);
    }

    byte[] bytes() {
        try{
        if (bytes == null) {                                                                                          //dolzno bit 64 a tut 32  
            ByteBuffer buffer = ByteBuffer.allocate(4 + 4 + 8 + 4 + (version < 3 ? (4 + 4) : (8 + 8)) + 4 + 32 + 32 + (32 + 32) + (blockSignature != null ? 64 : 0));
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            buffer.putInt(version);
            buffer.putInt(timestamp);
            buffer.putLong(previousBlockId);
            buffer.putInt(getTransactions().size());
            if (version < 3) {
                buffer.putInt((int) (totalAmountNQT / Constants.ONE_BND));
                buffer.putInt((int) (totalFeeNQT / Constants.ONE_BND));
            } else {
                buffer.putLong(totalAmountNQT);
                buffer.putLong(totalFeeNQT);
            }
            buffer.putInt(payloadLength);
            buffer.put(payloadHash);
            buffer.put(getGeneratorPublicKey());
            buffer.put(generationSignature); // eto po idee 64 ! denis eto slomal  - sdelal 32
            if (version > 1) {
                buffer.put(previousBlockHash);
            }
            if (blockSignature != null) {
                buffer.put(blockSignature);
            }
            bytes = buffer.array();
        }
        }catch(Exception e){
           Logger.logErrorMessage("BlchImpl get byte ERR:"+e);
        }
        return bytes;
    }

    boolean verifyBlockSignature() {
        return checkSignature() && Account.setOrVerify(getGeneratorId(), getGeneratorPublicKey());
    }

    private volatile boolean hasValidSignature = false;

    private boolean checkSignature() {
        if (!hasValidSignature) {
            byte[] data = Arrays.copyOf(bytes(), bytes.length - 64);
            hasValidSignature = blockSignature != null && Crypto.verify(blockSignature, data, getGeneratorPublicKey(), version >= 3);
        }
        return hasValidSignature;
    }

    boolean verifyGenerationSignature() throws BlockchainProcessor.BlockOutOfOrderException {
        try {
            BlockImpl previousBlock = BlockchainImpl.getInstance().getBlock(getPreviousBlockId());
            if (previousBlock == null) {
                throw new BlockchainProcessor.BlockOutOfOrderException("Can't verify signature because previous block is missing", this);
            }
            if (version == 1 && !Crypto.verify(generationSignature, previousBlock.generationSignature, getGeneratorPublicKey(), version >= 3)) {  
                Logger.logDebugMessage("Refused block " + (previousBlock.getHeight()+1) + " because of wrong signature");
                return false;
            }
            Account account = Account.getAccount(getGeneratorId());
            long effectiveBalance = account == null ? 0 : account.getEffectiveBalanceBND(Bened.getBlockchain().getHeight());
            if (effectiveBalance <= 0) {
                Logger.logDebugMessage("Refused block " + (previousBlock.getHeight()+1) + " because of zero effective balance");
                return false;
            }
            
            int currentHeight = BlockchainImpl.getInstance().getHeight();
            
            if (ExcludesGMS.check(getGeneratorId(), currentHeight)) {
                Logger.logDebugMessage("Refused block " + (previousBlock.getHeight()+1) + " because it is excluded");
                return false;
            }

            if (getTransactions() != null && !getTransactions().isEmpty()) {
                for (TransactionImpl trx : getTransactions()) {
                    if (ExcludesGMS.check(trx.getSenderId(), currentHeight)) {
                        Logger.logDebugMessage("Refused block " + (previousBlock.getHeight()+1) + " because it's transaction is excluded");
                        return false;
                    }
                }
            }

            MessageDigest digest = Crypto.sha256();
            byte[] generationSignatureHash;
            if (version == 1) {
                generationSignatureHash = digest.digest(generationSignature);
            } else {
                digest.update(previousBlock.generationSignature);
                generationSignatureHash = digest.digest(getGeneratorPublicKey());
                if (!Arrays.equals(generationSignature, generationSignatureHash)) {
                    Logger.logDebugMessage("Refused block " + (previousBlock.getHeight()+1) + " because of wrong generation signature");
                    return false;
                }
            }

            BigInteger hit = new BigInteger(1, new byte[]{generationSignatureHash[7], generationSignatureHash[6], generationSignatureHash[5], generationSignatureHash[4], generationSignatureHash[3], generationSignatureHash[2], generationSignatureHash[1], generationSignatureHash[0]});
            final boolean verified =  SimplDimpl.verifyHit(hit, BigInteger.valueOf(effectiveBalance), previousBlock, timestamp);
            if (!verified){
                Logger.logWarningMessage("Refused block "+ (previousBlock.getHeight()+1) +" because of failed verify hit");
		}
            return verified;
        } catch (RuntimeException e) {

            Logger.logWarningMessage("Error verifying block generation signature", e);
            return false;
        }
    }

private static final long[] badBlocks = new long[]{};
    
    
    static {
        Arrays.sort(badBlocks);
    }

    void apply() {
        Account generatorAccount = Account.addOrGetAccount(getGeneratorId());
        generatorAccount.apply(getGeneratorPublicKey());
        generatorAccount.addToBalanceAndUnconfirmedBalanceNQT(LedgerEvent.BLOCK_GENERATED, getId(), totalFeeNQT);
        generatorAccount.addToForgedBalanceNQT(totalFeeNQT);
    }

    void setPrevious(BlockImpl block) {
        if (block != null) {
            if (block.getId() != getPreviousBlockId()) {
                // shouldn't happen as previous id is already verified, but just in case
                throw new IllegalStateException("Previous block id doesn't match");
            }
            this.height = block.getHeight() + 1;
            this.calculateBaseTarget(block);
        } else {
            this.height = 0;
        }
        short index = 0;
        for (TransactionImpl transaction : getTransactions()) {
            transaction.setBlock(this);
            transaction.setIndex(index++);
        }
    }

    void loadTransactions() {
        for (TransactionImpl transaction : getTransactions()) {
            transaction.bytes();
            transaction.getAppendages();
        }
    }
    
    
    
    private void calculateBaseTarget(BlockImpl previousBlock) {
        if(Constants.USKORITEL_GENERACII_BLOCKOV!=1)Logger.logErrorMessage("\n-------- uskoritel != 1  !!!!!  nuzno ispravit!! razrabotka!");
        long prevBaseTarget = previousBlock.baseTarget;
        BigInteger CUMULATIVE_DIFFICULTY_MULTIPLIER = Convert.two64.multiply(BigInteger.valueOf( (Bened.getBlockchain().getHeight()<Constants.change_evendek22?Constants.BLOCK_TIME:Constants.BLOCK_TIME*5) ));
        cumulativeDifficulty = previousBlock.cumulativeDifficulty.add(CUMULATIVE_DIFFICULTY_MULTIPLIER.divide( BigInteger.valueOf(prevBaseTarget).multiply(BigInteger.valueOf(this.timestamp - previousBlock.timestamp))));
        int blockchainHeight = previousBlock.height;
        if(blockchainHeight>Constants.fix_evendek_210123){
            int targetBlocktime =  (Constants.BLOCK_TIME*4) / Constants.USKORITEL_GENERACII_BLOCKOV;
                BlockImpl block = BlockDb.findBlockAtHeight(blockchainHeight-2)  ;
                int blocktimeAverage = (this.timestamp- previousBlock.timestamp); // - block.timestamp) / 3;
                if (blocktimeAverage > targetBlocktime) {
                    baseTarget = (prevBaseTarget * Math.min(blocktimeAverage, targetBlocktime + Constants.MAX_BLOCKTIME_DELTA)) / targetBlocktime;
                } else {
                    baseTarget = prevBaseTarget - prevBaseTarget * Constants.BASE_TARGET_GAMMA* (targetBlocktime - Math.max(blocktimeAverage, targetBlocktime - Constants.MIN_BLOCKTIME_DELTA)) / (100 * targetBlocktime);
                }
                long consMAXBS = Constants.getMAX_BASE_TARGET(height);
                if (baseTarget < 0 || baseTarget > consMAXBS * Constants.USKORITEL_GENERACII_BLOCKOV) {
                   baseTarget = consMAXBS * Constants.USKORITEL_GENERACII_BLOCKOV;
                }
                long consMINBS = Constants.getMIN_BASE_TARGET(height);
                if (baseTarget < consMINBS * Constants.USKORITEL_GENERACII_BLOCKOV) {
                    baseTarget = consMINBS * Constants.USKORITEL_GENERACII_BLOCKOV;
                }
        }else{
            if (Bened.getBlockchain().getHeight()>Constants.change_evendek22 || (blockchainHeight > 2 && blockchainHeight % 2 == 0) ) {
                int targetBlocktime = (Bened.getBlockchain().getHeight()<Constants.change_evendek22? Constants.BLOCK_TIME : Constants.BLOCK_TIME*5) / Constants.USKORITEL_GENERACII_BLOCKOV;
                BlockImpl block = Bened.getBlockchain().getHeight()<Constants.change_evendek22? BlockDb.findBlockAtHeight(blockchainHeight-2) : previousBlock ;
                int blocktimeAverage = (this.timestamp - block.timestamp) / 3;
                if (blocktimeAverage > targetBlocktime) {
                    baseTarget = (prevBaseTarget * Math.min(blocktimeAverage, targetBlocktime + Constants.MAX_BLOCKTIME_DELTA)) / targetBlocktime;
                } else {
                    baseTarget = prevBaseTarget - prevBaseTarget * ((Bened.getBlockchain().getHeight()<Constants.change_evendek22? Constants.BASE_TARGET_GAMMA:Constants.BASE_TARGET_GAMMA*5))
                        * (targetBlocktime - Math.max(blocktimeAverage, targetBlocktime - Constants.MIN_BLOCKTIME_DELTA)) / (100 * targetBlocktime);
                }
                long consMAXBS = Constants.getMAX_BASE_TARGET(height);
                if (baseTarget < 0 || baseTarget > consMAXBS * Constants.USKORITEL_GENERACII_BLOCKOV) {
                    baseTarget = consMAXBS * Constants.USKORITEL_GENERACII_BLOCKOV;
                }
                long consMINBS = Constants.getMIN_BASE_TARGET(height);
                if (baseTarget < consMINBS * Constants.USKORITEL_GENERACII_BLOCKOV) {
                    baseTarget = consMINBS * Constants.USKORITEL_GENERACII_BLOCKOV;
                }
            } else {
                baseTarget = prevBaseTarget;
            }
        }
        
        cumulativeDifficulty = previousBlock.cumulativeDifficulty.add(Convert.two64.divide(BigInteger.valueOf(baseTarget)));
    int _cdo=100;
    try{_cdo=Integer.getInteger(this.otner_cumulativeDifficulty);}catch(Exception e){_cdo=1;}  
    if(this.other_baseTarget>0 && _cdo>0){
        int _theight = previousBlock.getHeight()+1;
        if( (this.baseTarget!=this.other_baseTarget || !this.otner_cumulativeDifficulty.equals(this.cumulativeDifficulty.toString())) 
                &&  _theight<LAST_KNOWN_BLOCK){
            System.out.println("basetarget wrong b:"+_theight+" bs:"+this.baseTarget+" obt:"+this.other_baseTarget
                 +", cd:"+this.cumulativeDifficulty.toString()+" ocd:"+this.otner_cumulativeDifficulty+"\ntst:"+previousBlock.getTimestamp());
            if(Constants.verbadtime(previousBlock.getTimestamp()) ){
                this.baseTarget = this.other_baseTarget;
                this.cumulativeDifficulty = new BigInteger(this.otner_cumulativeDifficulty);
                if(this.baseTarget == this.other_baseTarget && this.cumulativeDifficulty.equals(new BigInteger(this.otner_cumulativeDifficulty)) ){
                }else{
                    Logger.logWarningMessage("!Ahtung basetarget and Cd no repair !!!");
                }
            }
        }
    }    
    }

}
