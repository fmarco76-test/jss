/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.jss.crypto;

import java.security.NoSuchAlgorithmException;
import java.util.Hashtable;

import org.mozilla.jss.asn1.OBJECT_IDENTIFIER;

/**
 * Algorithms for performing HMACs. These can be used to create
 * MessageDigests.
 */
public class HMACAlgorithm extends DigestAlgorithm {

    protected HMACAlgorithm(int oidIndex, String name, OBJECT_IDENTIFIER oid,
                int outputSize) {
        super(oidIndex, name, oid, outputSize);

        if( oid!=null && oidMap.get(oid)==null) {
            oidMap.put(oid, this);
        }
    }

    ///////////////////////////////////////////////////////////////////////
    // OID mapping
    ///////////////////////////////////////////////////////////////////////
    private static Hashtable<OBJECT_IDENTIFIER, HMACAlgorithm> oidMap = new Hashtable<>();

    /**
     * Looks up the HMAC algorithm with the given OID.
     *
     * @param oid OID.
     * @return HMAC algorithm.
     * @exception NoSuchAlgorithmException If no registered HMAC algorithm
     *  has the given OID.
     */
    public static HMACAlgorithm fromOID(OBJECT_IDENTIFIER oid)
        throws NoSuchAlgorithmException
    {
        Object alg = oidMap.get(oid);
        if( alg == null ) {
            throw new NoSuchAlgorithmException();
        } else {
            return (HMACAlgorithm) alg;
        }
    }

    /**
     * SHA-X HMAC.  This is a Message Authentication Code that uses a
     * symmetric key together with SHA-X digesting to create a form of
     * signature.
     */
    @Deprecated(since="5.0.1", forRemoval=true)
    public static final HMACAlgorithm SHA1 = new HMACAlgorithm
        (CKM_SHA_1_HMAC, "SHA-1-HMAC",
             OBJECT_IDENTIFIER.ALGORITHM.subBranch(26), 20);

    public static final HMACAlgorithm SHA256 = new HMACAlgorithm
        (CKM_SHA256_HMAC, "SHA-256-HMAC",
             OBJECT_IDENTIFIER.RSA_DIGEST.subBranch(9), 32);

    public static final HMACAlgorithm SHA384 = new HMACAlgorithm
        (CKM_SHA384_HMAC, "SHA-384-HMAC",
             OBJECT_IDENTIFIER.RSA_DIGEST.subBranch(10), 48);

    public static final HMACAlgorithm SHA512 = new HMACAlgorithm
        (CKM_SHA512_HMAC, "SHA-512-HMAC",
             OBJECT_IDENTIFIER.RSA_DIGEST.subBranch(11), 64);

}
