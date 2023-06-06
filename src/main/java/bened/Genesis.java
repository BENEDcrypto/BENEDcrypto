/******************************************************************************
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
 ******************************************************************************/

package bened;

import bened.util.Convert;

public final class Genesis {

    public static final long GENESIS_BLOCK_ID = -6523299233707585364L;
    public static final long CREATOR_ID = 6961384895484640367L;
    public static final long GENESIS_BLOCK_AMOUNT =Constants.MAX_BALANCE_centesimo;
    public static final byte[] CREATOR_PUBLIC_KEY = {
        -30, -21, -18, 24, 53, 118, -128, -13, -68, -91, -27, 91, 36, -110, 11, -82, -54, 102, 15, 39, 22, 49, -87, -22, 91, 77, 99, 42, -117, -124, -12, 119
    };
    
    public static final long[] GENESIS_RECIPIENTS = {
            Long.parseUnsignedLong("17862279112056421364")
    };
    
    public static final long[] first_RECIPIENTS = {
            Account.getId(Convert.parseHexString("088ac78b0fce085549e33493e253d51908596423c0ed25ff5236f4f2f56dc85c")),
            Account.getId(Convert.parseHexString("f463dfb44a5fa07ee811eb41b693f1b527e147eaeaead65df3f74d1e66ae094b")),
            Account.getId(Convert.parseHexString("4b232a79a4bef30345d6fd3a46cedb5b61f12afe4be9d9372a99ffc93af5fd3b")),
            Account.getId(Convert.parseHexString("c7ebca4cc9c7d4da3cfc43bdbd493aa619a2e7a29e00862c785485c2ea387a5c")),
            Account.getId(Convert.parseHexString("44b14a722cc4ed587cc00852d494d50d46f63253c479aec4ef572bb110deff0c")),
            Account.getId(Convert.parseHexString("49c6777b50628242e2a7e7c582923bfce4e379c3a5113a98992c00738d419c5c"))
               
    };

    public static final long[] GENESIS_AMOUNTS = {
            15000000000L
    };
    
    public static final byte[][] GENESIS_SIGNATURES = {
            {12, 35, -35, 32, -28, 21, -45, -103, 33, 18, 114, -110, -128, -44, 118, -104, 113, -43, -46, 80, 67, -14, -123, -71, -97, -78, -67, 127, 32, -53, -56, 13, 110, -127, 83, -121, -110, -79, 93, -26, -65, -38, -104, 93, 32, 90, -95, 19, 86, 92, -52, -26, 71, -26, -22, 6, -76, -78, -71, -38, -15, -1, 49, 52}

        };

    public static final byte[] GENESIS_BLOCK_SIGNATURE = new byte[]{
    	62, 123, -53, -16, 58, 119, 68, -72, 35, 40, 5, -83, 62, 123, -51, -84, -125, -19, 14, -60, 67, 69, -37, -33, -59, -110, 119, 124, -12, -84, -74, 0, 62, -77, -97, 75, 1, -43, 65, -36, 72, -94, -94, 92, 125, 109, 26, -22, 43, 114, -83, -70, 3, -1, -22, 50, 26, -66, -27, 19, 53, 126, 66, 27
    };

    private Genesis() {} // never

}
