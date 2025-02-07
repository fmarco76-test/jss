// --- BEGIN COPYRIGHT BLOCK ---
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; version 2 of the License.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License along
// with this program; if not, write to the Free Software Foundation, Inc.,
// 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
//
// (C) 2007 Red Hat, Inc.
// All rights reserved.
// --- END COPYRIGHT BLOCK ---
package org.mozilla.jss.netscape.security.x509;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.security.AlgorithmParameters;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.spec.InvalidParameterSpecException;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;

import org.apache.commons.lang3.ArrayUtils;
import org.mozilla.jss.netscape.security.util.DerEncoder;
import org.mozilla.jss.netscape.security.util.DerInputStream;
import org.mozilla.jss.netscape.security.util.DerOutputStream;
import org.mozilla.jss.netscape.security.util.DerValue;
import org.mozilla.jss.netscape.security.util.ObjectIdentifier;

/**
 * This class identifies algorithms, such as cryptographic transforms, each
 * of which may be associated with parameters. Instances of this base class
 * are used when this runtime environment has no special knowledge of the
 * algorithm type, and may also be used in other cases. Equivalence is
 * defined according to OID and (where relevant) parameters.
 *
 * <P>
 * Subclasses may be used, for example when when the algorithm ID has associated parameters which some code (e.g. code
 * using public keys) needs to have parsed. Two examples of such algorithms are Diffie-Hellman key exchange, and the
 * Digital Signature Standard Algorithm (DSS/DSA).
 *
 * <P>
 * The OID constants defined in this class correspond to some widely used algorithms, for which conventional string
 * names have been defined. This class is not a general repository for OIDs, or for such string names. Note that the
 * mappings between algorithm IDs and algorithm names is not one-to-one.
 *
 * @version 1.70
 *
 * @author David Brownell
 * @author Amit Kapoor
 * @author Hemma Prafullchandra
 */
public class AlgorithmId implements Serializable, DerEncoder {

    /** use serialVersionUID from JDK 1.1. for interoperability */
    private static final long serialVersionUID = 7205873507486557157L;

    /**
     * The object identitifer being used for this algorithm.
     */
    private ObjectIdentifier algid = null;

    // The (parsed) parameters
    private AlgorithmParameters algParams = null;
    // Use this for the various flavors of the RSA PSS alg.
    private String cachedAlgName = null;

    /**
     * Parameters for this algorithm. These are stored in unparsed
     * DER-encoded form; subclasses can be made to automaticaly parse
     * them so there is fast access to these parameters.
     */
    protected DerValue params = null;

    protected String paramsString = null;

    public AlgorithmParameters getParameters() {
        return this.algParams;
    }

    public String getParametersString() {
        return this.paramsString;
    }

    public void setParametersString(String paramStr) {

        this.paramsString = paramStr;
    }

    /**
     * Returns one of the algorithm IDs most commonly associated
     * with this algorithm name.
     *
     * @param algname the name being used
     * @return an ID for the algorithm.
     * @exception NoSuchAlgorithmException on error.
     */
    public static AlgorithmId get(String algname)
            throws NoSuchAlgorithmException {
        ObjectIdentifier oid = algOID(algname);
        if (oid == null)
            throw new NoSuchAlgorithmException("unrecognized algorithm name: " + algname);
        try {
            return new AlgorithmId(oid, algname);
        } catch (Exception e) {
            throw new NoSuchAlgorithmException(e);
        }
    }

    /**
     * Parse (unmarshal) an ID from a DER sequence input value. This form
     * parsing might be used when expanding a value which has already been
     * partially unmarshaled as a set or sequence member.
     *
     * @exception IOException on error.
     * @param val the input value, which contains the algid and, if
     *            there are any parameters, those parameters.
     * @return an ID for the algorithm. If the system is configured
     *         appropriately, this may be an instance of a class
     *         with some kind of special support for this algorithm.
     *         In that case, you may "narrow" the type of the ID.
     */
    public static AlgorithmId parse(DerValue val)
            throws IOException {
        if (val.tag != DerValue.tag_Sequence)
            throw new IOException("algid parse error, not a sequence");

        /*
         * Get the algorithm ID and any parameters.
         */
        ObjectIdentifier algid;
        DerValue params;
        DerInputStream in = val.toDerInputStream();

        algid = in.getOID();
        if (in.available() == 0)
            params = null;
        else {
            params = in.getDerValue();
            if (params.tag == DerValue.tag_Null)
                params = null;
        }

        /*
         * Figure out what class (if any) knows about this oid's
         * parameters.  Make one, and give it the data to decode.
         */
        AlgorithmId alg = null;
        // omit parameter field for ECDSA
        if (!algid.equals(sha224WithEC_oid) &&
                !algid.equals(sha256WithEC_oid) &&
                !algid.equals(sha384WithEC_oid) &&
                !algid.equals(sha512WithEC_oid)) {
            alg = new AlgorithmId(algid, params);
        } else {
            try {
                alg = new AlgorithmId(algid);
            } catch (Exception e) {
                throw new IOException(e);
            }
        }
        if (params != null)
            alg.decodeParams();

        /*
         * Set the raw params string in case
         * higher level code might want the info
        */

        String paramStr = null;

        if (params != null) {
            paramStr = params.toString();
        }

        alg.setParametersString(paramStr);

        return alg;
    }

    public static AlgorithmId parse(byte[] val)
            throws IOException {
        return null;
    }

    /**
     * Constructs an algorithm ID with a fully encoded params object
     * @param oid the identifier for the algorithm
     * @param params the fully encoded AlgorithmIdentifier Object
     * @throws NoSuchAlgorithmException
     * @throws IOException
     */
    public AlgorithmId(ObjectIdentifier oid, AlgorithmParameters params)
        throws IOException, NoSuchAlgorithmException {
        algid = oid;
        algParams = params;

        if (algParams == null) {
            throw new NoSuchAlgorithmException("AlgorithmId: null algParams.");
        }

        this.params = new DerValue(algParams.getEncoded());
    }

    /**
     * Constructor that takes the oid and name, so the name can be cachedf or laster use.
     * @param oid the identifier for the algorithm
     * @param algName the algorithm name
     * @throws NoSuchAlgorithmException
     * @throws IOException
     *
     */
    public AlgorithmId(ObjectIdentifier oid, String algName)
        throws IOException, NoSuchAlgorithmException {
        algid = oid;
        cachedAlgName = algName;

        /* Create the params if our algorithm is RSA PSS related */
        if (algName != null && algName.contains("PSS")) {
            this.algParams = getPSSParams(algName);
            this.params = new DerValue(this.algParams.getEncoded());
        }
    }

    /**
     * Constructs an  algorithm ID with possible RSAPSS params.
     *
     * @param oid the identifier for the algorithm
     */
    public AlgorithmId(ObjectIdentifier oid) {
        algid = oid;
        String algName = algName();

        /* Create the params if our algorithm is RSA PSS related */
        if (algName != null && algName.contains("PSS")) {
            try {
                this.algParams = getPSSParams(algName);

                if (this.algParams != null) {
                    try {
                        this.params = new DerValue(this.algParams.getEncoded());
                    } catch (IOException e) {
                        throw new IOException(e);
                    }
                }
            } catch (Exception e) {
                //Preserve original signature...
                throw new RuntimeException("Unable to create pssPrams in Algorithmid(ObjectIdentifier oid): " + e.getMessage(), e);
            }
        }
    }

    private AlgorithmParameters getPSSParams(String algName)
            throws NoSuchAlgorithmException, IOException {
        cachedAlgName = algName;

        AlgorithmParameters ret = null;
        /* Create the params if our algorithm is RSA PSS related */
        try {
            ret = createPSSAlgorithmParameters(algName);
        } catch (Exception e) {
            throw new NoSuchAlgorithmException(e);
        }

        return ret;

    }

    private AlgorithmId(ObjectIdentifier oid, DerValue params)
            throws IOException {
        this.algid = oid;
        this.params = params;
        if (this.params != null)
            decodeParams();
    }

    /**
     * Constructs an algorithm ID which will be initialized
     * separately, for example by deserialization.
     *
     * @deprecated use one of the other constructors.
     */
    @Deprecated
    public AlgorithmId() {
    }

    protected void decodeParams() throws IOException {
        try {

            if (algid.equals(AlgorithmId.rsaPSS_oid)) {
                try {
                    this.algParams = createPSSAlgorithmParametersFromData(this.params.toByteArray());
                    return;
                } catch (Exception e) {
                    throw new IOException(e);
                }
            } else {
                this.algParams = AlgorithmParameters.getInstance(this.algid.toString());
            }
        } catch (NoSuchAlgorithmException e) {
            /*
             * This algorithm parameter type is not supported, so we cannot
             * parse the parameters.
            */
            this.algParams = null;
            return;
        }
        // Decode (parse) the parameters
        this.algParams.init(this.params.toByteArray());
    }

    /**
     * Marshal a DER-encoded "AlgorithmID" sequence on the DER stream.
     * @param out the output stream on which to write the DER encoding.
     * @exception IOException on encoding error.
     */
    public final void encode(DerOutputStream out)
            throws IOException {
        derEncode(out);
    }

    /**
     * DER encode this object onto an output stream.
     * Implements the <code>DerEncoder</code> interface.
     * @param out the output stream on which to write the DER encoding.
     *
     * @exception IOException on encoding error.
     */
    @Override
    public void derEncode(OutputStream out) throws IOException {
        try (DerOutputStream tmp = new DerOutputStream()) {
            DerOutputStream bytes = new DerOutputStream();
            bytes.putOID(algid);

            // omit parameter field for ECDSA
            if (!algid.equals(sha224WithEC_oid) &&
                    !algid.equals(sha256WithEC_oid) &&
                    !algid.equals(sha384WithEC_oid) &&
                    !algid.equals(sha512WithEC_oid)) {
                if (params == null) {
                    bytes.putNull();
                } else
                    bytes.putDerValue(params);
            }

            tmp.write(DerValue.tag_Sequence, bytes);
            out.write(tmp.toByteArray());
        }
    }

    /**
     * DER encode this object onto an output stream.
     * Implements the <code>DerEncoder</code> interface.
     *
     * @param out the output stream on which to write the DER encoding params,
     *            using context value.
     * @param contextVal context value
     *
     * @exception IOException on encoding error.
     */
    public void derEncodeWithContext(OutputStream out, int contextVal) throws IOException {
        try (DerOutputStream tmp = new DerOutputStream()) {
            DerOutputStream bytes = new DerOutputStream();
            bytes.putOID(algid);

            byte val = (byte) contextVal;
            // omit parameter field for ECDSA
            if (!algid.equals(sha224WithEC_oid) &&
                    !algid.equals(sha256WithEC_oid) &&
                    !algid.equals(sha384WithEC_oid) &&
                    !algid.equals(sha512WithEC_oid)) {
                if (params == null) {
                    bytes.putNull();
                } else {
                    bytes.putDerValue(params);
                }
            }

            DerOutputStream seq = new DerOutputStream();

            seq.write(DerValue.tag_Sequence, bytes);

            tmp.write(DerValue.createTag(DerValue.TAG_CONTEXT,
                                             true, val), seq);



            out.write(tmp.toByteArray());
        }
    }



    // XXXX cleaning required
    /**
     * Returns the DER-encoded X.509 AlgorithmId as a byte array.
     * @return the byte array
     * @throws IOException If an error occurred.
     */
    public final byte[] encode() throws IOException {
        try (DerOutputStream out = new DerOutputStream()) {
            DerOutputStream bytes = new DerOutputStream();
            bytes.putOID(algid);

            // omit parameter field for ECDSA
            if (!algid.equals(sha224WithEC_oid) &&
                    !algid.equals(sha256WithEC_oid) &&
                    !algid.equals(sha384WithEC_oid) &&
                    !algid.equals(sha512WithEC_oid)) {
                if (params == null) {
                    bytes.putNull();
                } else
                    bytes.putDerValue(params);
            }

            out.write(DerValue.tag_Sequence, bytes);
            return out.toByteArray();
        }
    }

    /**
     * Returns list of signing algorithms for a key algorithm such as
     * RSA or DSA.
     * @param alg key algorithm
     * @return list of signing algorithms
     */
    public static String[] getSigningAlgorithms(AlgorithmId alg) {
        ObjectIdentifier algOid = alg.getOID();
        if (algOid.equals(DSA_oid) || algOid.equals(DSA_OIW_oid)) {
            return DSA_SIGNING_ALGORITHMS;
        } else if (algOid.equals(RSA_oid) || algOid.equals(RSAEncryption_oid)) {
            return RSA_SIGNING_ALGORITHMS;
        } else if (algOid.equals(ANSIX962_EC_Public_Key_oid) || algOid.equals(ANSIX962_SHA1_With_EC_oid)) {
            return EC_SIGNING_ALGORITHMS;
        } else {
            return null;
        }
    }

    /*
     * Translates from some common algorithm names to the
     * OID with which they're usually associated ... this mapping
     * is the reverse of the one below, except in those cases
     * where synonyms are supported or where a given algorithm
     * is commonly associated with multiple OIDs.
     */
    private static ObjectIdentifier algOID(String name) {
        // Digesting algorithms

        if (name.equals("MD5"))
            return AlgorithmId.MD5_oid;
        if (name.equals("MD2"))
            return AlgorithmId.MD2_oid;
        if (name.equals("SHA") || name.equals("SHA1")
                || name.equals("SHA-1"))
            return AlgorithmId.SHA_oid;
        if (name.equals("SHA256") || name.equals("SHA-256"))
            return AlgorithmId.SHA256_oid;
        if (name.equals("SHA512") || name.equals("SHA-512"))
            return AlgorithmId.SHA512_oid;

        // Various public key algorithms

        if (name.equals("RSA"))
            return AlgorithmId.RSA_oid;

        if (name.equals("RSAEncryption"))
            return AlgorithmId.RSAEncryption_oid;
        if (name.equals("Diffie-Hellman") || name.equals("DH"))
            return AlgorithmId.DH_oid;
        if (name.equals("DSA"))
            return AlgorithmId.DSA_oid;

        // Common signature types

        if (name.equals("SHA1withEC") || name.equals("SHA1/EC")
                || name.equals("1.2.840.10045.4.1"))
            return AlgorithmId.sha1WithEC_oid;
        if (name.equals("SHA224withEC") || name.equals("SHA224/EC")
                || name.equals("1.2.840.10045.4.3.1"))
            return AlgorithmId.sha224WithEC_oid;
        if (name.equals("SHA256withEC") || name.equals("SHA256/EC")
                || name.equals("1.2.840.10045.4.3.2"))
            return AlgorithmId.sha256WithEC_oid;
        if (name.equals("SHA384withEC") || name.equals("SHA384/EC")
                || name.equals("1.2.840.10045.4.3.3"))
            return AlgorithmId.sha384WithEC_oid;
        if (name.equals("SHA512withEC") || name.equals("SHA512/EC")
                || name.equals("1.2.840.10045.4.3.4"))
            return AlgorithmId.sha512WithEC_oid;
        if (name.equals("SHA1withRSA") || name.equals("SHA1/RSA")
                || name.equals("1.2.840.113549.1.1.5"))
            return AlgorithmId.sha1WithRSAEncryption_oid;
        if (name.equals("SHA256withRSA") || name.equals("SHA256/RSA")
                || name.equals("1.2.840.113549.1.1.11"))
            return AlgorithmId.sha256WithRSAEncryption_oid;
        if (name.equals("SHA384withRSA") || name.equals("SHA384/RSA")
                || name.equals("1.2.840.113549.1.1.12"))
            return AlgorithmId.sha384WithRSAEncryption_oid;
        if (name.equals("SHA512withRSA") || name.equals("SHA512/RSA")
                || name.equals("1.2.840.113549.1.1.13"))
            return AlgorithmId.sha512WithRSAEncryption_oid;
        if (name.equals("MD5withRSA") || name.equals("MD5/RSA"))
            return AlgorithmId.md5WithRSAEncryption_oid;
        if (name.equals("MD2withRSA") || name.equals("MD2/RSA"))
            return AlgorithmId.md2WithRSAEncryption_oid;
        if (name.equals("SHAwithDSA") || name.equals("SHA1withDSA")
                || name.equals("SHA/DSA") || name.equals("SHA1/DSA"))
            return AlgorithmId.sha1WithDSA_oid;
        if (name.equals("SHA256withRSA/PSS") || name.equals("SHA384withRSA/PSS") || name.equals("SHA512withRSA/PSS"))
            return AlgorithmId.rsaPSS_oid;

        return null;
    }

    /*
     * For the inevitable cases where key or signature types are not
     * configured in an environment which encounters such keys or
     * signatures, we still attempt to provide user-friendly names
     * for some of the most common algorithms.  Subclasses can of
     * course override getName().
     *
     * Wherever possible, the names are those defined by the IETF.
     * Such names are noted below.
     */
    private String algName() {
        // Common message digest algorithms
        if (algid.equals(AlgorithmId.MD5_oid))
            return "MD5"; // RFC 1423
        if (algid.equals(AlgorithmId.MD2_oid))
            return "MD2"; // RFC 1423
        if (algid.equals(AlgorithmId.SHA_oid))
            return "SHA";
        if (algid.equals(AlgorithmId.SHA256_oid))
            return "SHA256";
        if (algid.equals(AlgorithmId.SHA384_oid))
            return "SHA384";
        if (algid.equals(AlgorithmId.SHA512_oid))
            return "SHA512";

        if (algid.equals(AlgorithmId.rsaPSS_oid)) {
            if (cachedAlgName != null) {
                return cachedAlgName;
            }

            // Get alg variant from params info
            String paramStr = paramsToString();
            if (paramStr != null) {
                if (paramStr.contains("HashAlg: SHA-256")) {
                    cachedAlgName = "SHA256withRSA/PSS";
                } else if (paramStr.contains("HashAlg: SHA-384")) {
                    cachedAlgName = "SHA384withRSA/PSS";
                } else if (paramStr.contains("HashAlg: SHA-512")) {
                    cachedAlgName = "SHA512withRSA/PSS";
                } else {
                    throw new RuntimeException("Unknown or unsupported signature algorithm in PSS parameters: " + paramStr);
                }
            }

            return cachedAlgName;
        }

        // Common key types

        if (algid.equals(AlgorithmId.ANSIX962_EC_Public_Key_oid))
            return "EC";
        if (algid.equals(AlgorithmId.RSAEncryption_oid)
                || algid.equals(AlgorithmId.RSA_oid))
            return "RSA";
        if (algid.equals(AlgorithmId.DH_oid)
                || algid.equals(AlgorithmId.DH_PKIX_oid))
            return "Diffie-Hellman";
        if (algid.equals(AlgorithmId.DSA_oid)
                || algid.equals(AlgorithmId.DSA_OIW_oid))
            return "DSA";

        // Common signature types

        if (algid.equals(AlgorithmId.sha1WithEC_oid))
            return "SHA1withEC";
        if (algid.equals(AlgorithmId.sha256WithEC_oid))
            return "SHA256withEC";
        if (algid.equals(AlgorithmId.sha384WithEC_oid))
            return "SHA384withEC";
        if (algid.equals(AlgorithmId.sha512WithEC_oid))
            return "SHA512withEC";
        if (algid.equals(AlgorithmId.md5WithRSAEncryption_oid))
            return "MD5withRSA";
        if (algid.equals(AlgorithmId.md2WithRSAEncryption_oid))
            return "MD2withRSA";
        if (algid.equals(AlgorithmId.sha1WithRSAEncryption_oid))
            return "SHA1withRSA";
        if (algid.equals(AlgorithmId.sha256WithRSAEncryption_oid))
            return "SHA256withRSA";
        if (algid.equals(AlgorithmId.sha384WithRSAEncryption_oid))
            return "SHA384withRSA";
        if (algid.equals(AlgorithmId.sha512WithRSAEncryption_oid))
            return "SHA512withRSA";
        if (algid.equals(AlgorithmId.sha1WithDSA_oid)
                || algid.equals(AlgorithmId.sha1WithDSA_OIW_oid)
                || algid.equals(AlgorithmId.shaWithDSA_OIW_oid))
            return "SHA1withDSA";

        // default returns a dot-notation ID

        return "OID." + algid.toString();
    }

    /**
     * Returns the ISO OID for this algorithm. This is usually converted
     * to a string and used as part of an algorithm name, for example
     * "OID.1.3.14.3.2.13" style notation. Use the <code>getName</code> call when you do not need to ensure cross-system
     * portability
     * of algorithm names, or need a user friendly name.
     * @return the ISO OID
     */
    final public ObjectIdentifier getOID() {
        return algid;
    }

    /**
     * Returns a name for the algorithm which may be more intelligible
     * to humans than the algorithm's OID, but which won't necessarily
     * be comprehensible on other systems. For example, this might
     * return a name such as "MD5withRSA" for a signature algorithm on
     * some systems. It also returns names like "OID.1.2.3.4", when
     * no particular name for the algorithm is known.
     * @return the name
     */
    public String getName() {
        return algName();
    }

    /**
     * Returns a string describing only the algorithm without parameters.
     *
     * Use toStringWithParams() for algorithm name and paramaters, or
     * paramsToString() for just parameters.
     *
     * @return algorithm name
     */
    @Override
    public String toString() {
        return algName();
    }

    /**
     * Returns a string describing the algorithm and its parameters.
     * @return algorithm name and parameters
     */
    public String toStringWithParams() {
        if (params == null) {
            return algName();
        }

        return algName() + " " + paramsToString();
    }

    /**
     * Returns the DER encoded parameter, which can then be
     * used to initialize java.security.AlgorithmParamters.
     *
     * @return DER encoded parameters, or null not present.
     * @throws IOException If an error occurred.
     */
    public byte[] getEncodedParams() throws IOException {
        if (params == null)
            return null;
        else
            return params.toByteArray();
    }

    /**
     * Provides a human-readable description of the algorithm parameters.
     * This may be redefined by subclasses which parse those parameters.
     * @return algorithm parameters description
     */
    protected String paramsToString() {
        if (params == null) {
            return "";
        } else if (algParams != null) {
            return algParams.toString();
        } else {
            return ", params unparsed";
        }
    }

    /**
     * Returns true iff the argument indicates the same algorithm
     * with the same parameters.
     * @param other another algorithm ID
     * @return true iff it's identical
     */
    public boolean equals(AlgorithmId other) {
        if (!algid.equals(other.algid))
            return false;
        else if (params == null && other.params == null)
            return true;
        else if (params == null)
            return false;
        else
            return params.equals(other.params);
    }

    /**
     * Compares this AlgorithmID to another. If algorithm parameters are
     * available, they are compared. Otherwise, just the object IDs
     * for the algorithm are compared.
     *
     * @param other preferably an AlgorithmId, else an ObjectIdentifier
     */
    @Override
    public boolean equals(Object other) {
        if (other instanceof AlgorithmId)
            return equals((AlgorithmId) other);
        else if (other instanceof ObjectIdentifier)
            return equals((ObjectIdentifier) other);
        else
            return false;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((algParams == null) ? 0 : algParams.hashCode());
        result = prime * result + ((algid == null) ? 0 : algid.hashCode());
        result = prime * result + ((params == null) ? 0 : params.hashCode());
        result = prime * result + ((paramsString == null) ? 0 : paramsString.hashCode());
        return result;
    }

    /**
     * Compares two algorithm IDs for equality. Returns true iff
     * they are the same algorithm, ignoring algorithm parameters.
     * @param id another algorithm ID
     * @return true iff it's the same
     */
    public final boolean equals(ObjectIdentifier id) {
        return algid.equals(id);
    }

    public static AlgorithmParameters createPSSAlgorithmParametersFromData(byte[] der) throws Exception {
        if (der == null) {
            throw new Exception("Invalid input data.");
        }
        AlgorithmParameters pssParams = null;
        try {
            pssParams = AlgorithmParameters.getInstance("RSAPSSAlgorithmParameters", "Mozilla-JSS");
        } catch (NoSuchProviderException e) {
            throw new Exception(e);
        }

        try {
            pssParams.init(der);
        } catch (IOException e) {
            throw new Exception("Error intializing RSAPSS parameters: " + e);
        }
        return pssParams;
    }

    /**
     * Used to create the PSS algorithm params needed for RSA PSS signatures.
     * @param algName algorithm name
     * @return algorithm parameters
     * @throws IllegalArgumentException
     * @throws NoSuchProviderException
     * @throws InvalidParameterSpecException
     * @throws NoSuchAlgorithmException
    */
    public static AlgorithmParameters createPSSAlgorithmParameters(String algName) throws IllegalArgumentException, NoSuchProviderException, InvalidParameterSpecException, NoSuchAlgorithmException {
        if (algName == null) {
            throw new IllegalArgumentException("Invalid Algorithm name input.");
        }

        if (!algName.contains("PSS")) {
           throw new IllegalArgumentException("PSS Algorithm name not supported.");
        }

        AlgorithmParameters pssParams = null;
        PSSParameterSpec pssSpec = null;

        // Make sure we are in the RSA PSS family
        // Only support for now RSAPSS with SHA256 , 384, and 512
        // Resulting in different PSSParameterSpec values
        if ("SHA256withRSA/PSS".equals(algName)) {
            // Support the most often used SHA-256 hash alg version .
            pssSpec = new PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1);
        } else if ("SHA384withRSA/PSS".equals(algName)) {
            pssSpec = new PSSParameterSpec("SHA-384", "MGF1", MGF1ParameterSpec.SHA384, 48, 1);
        } else if ("SHA512withRSA/PSS".equals(algName)) {
            pssSpec = new PSSParameterSpec("SHA-512", "MGF1", MGF1ParameterSpec.SHA512, 64, 1);
        } else {
            throw new IllegalArgumentException("Unsupported algorithm: " + algName);
        }

        pssParams = AlgorithmParameters.getInstance("RSAPSSAlgorithmParameters", "Mozilla-JSS");
        pssParams.init(pssSpec);
        return pssParams;
    }

    /*****************************************************************/

    /*
     * HASHING ALGORITHMS
     */
    private static final int MD2_data[] = { 1, 2, 840, 113549, 2, 2 };
    private static final int MD5_data[] = { 1, 2, 840, 113549, 2, 5 };
    // sha = { 1, 3, 14, 3, 2, 18 };
    @Deprecated(since="5.0.1", forRemoval=true)
    private static final int SHA1_OIW_data[] = { 1, 3, 14, 3, 2, 26 };
    private static final int SHA256_data[] = { 2, 16, 840, 1, 101, 3, 4, 2, 1 };
    private static final int SHA384_data[] = { 2, 16, 840, 1, 101, 3, 4, 2, 2 };
    private static final int SHA512_data[] = { 2, 16, 840, 1, 101, 3, 4, 2, 3 };
    private static final int MGF1_data[] = { 1,2,840,113549,1,1,8 };
    /**
     * Algorithm ID for the MD2 Message Digest Algorthm, from RFC 1319.
     * OID = 1.2.840.113549.2.2
     */
    public static final ObjectIdentifier MD2_oid = new ObjectIdentifier(MD2_data);

    /**
     * Algorithm ID for the MD5 Message Digest Algorthm, from RFC 1321.
     * OID = 1.2.840.113549.2.5
     */
    public static final ObjectIdentifier MD5_oid = new ObjectIdentifier(MD5_data);

    /**
     * Algorithm ID for the SHA1 Message Digest Algorithm, from FIPS 180-1.
     * This is sometimes called "SHA", though that is often confusing since
     * many people refer to FIPS 180 (which has an error) as defining SHA.
     * OID = 1.3.14.3.2.26
     */
    @Deprecated(since="5.0.1", forRemoval=true)
    public static final ObjectIdentifier SHA_oid = new ObjectIdentifier(SHA1_OIW_data);

    public static final ObjectIdentifier SHA256_oid = new ObjectIdentifier(SHA256_data);
    public static final ObjectIdentifier SHA384_oid = new ObjectIdentifier(SHA384_data);

    public static final ObjectIdentifier SHA512_oid = new ObjectIdentifier(SHA512_data);

    public static final ObjectIdentifier MGF1_oid = new ObjectIdentifier(MGF1_data);
    /*
     * COMMON PUBLIC KEY TYPES
     */
    private static final int DH_data[] = { 1, 2, 840, 113549, 1, 3, 1 };
    private static final int DH_PKIX_data[] = { 1, 2, 840, 10046, 2, 1 };
    private static final int DSA_OIW_data[] = { 1, 3, 14, 3, 2, 12 };
    private static final int DSA_PKIX_data[] = { 1, 2, 840, 10040, 4, 1 };
    private static final int RSA_data[] = { 1, 2, 5, 8, 1, 1 };
    private static final int RSAEncryption_data[] =
                                         { 1, 2, 840, 113549, 1, 1, 1 };
    private static final int ANSI_X962_public_key_data[] =
                                         { 1, 2, 840, 10045, 2, 1 };
    private static final int ANSI_X962_sha1_with_ec_data[] =
                                         { 1, 2, 840, 10045, 4, 1 };

    public static final ObjectIdentifier ANSIX962_EC_Public_Key_oid = new ObjectIdentifier(ANSI_X962_public_key_data);
    @Deprecated(since="5.0.1", forRemoval=true)
    public static final ObjectIdentifier ANSIX962_SHA1_With_EC_oid = new ObjectIdentifier(ANSI_X962_sha1_with_ec_data);

    /*
     * Note the preferred OIDs are named simply with no "OIW" or
     * "PKIX" in them, even though they may point to data from these
     * specs; e.g. SHA_oid, DH_oid, DSA_oid, SHA1WithDSA_oid...
     */
    /**
     * Algorithm ID for Diffie Hellman Key agreement, from PKCS #3.
     * Parameters include public values P and G, and may optionally specify
     * the length of the private key X. Alternatively, algorithm parameters
     * may be derived from another source such as a Certificate Authority's
     * certificate.
     * OID = 1.2.840.113549.1.3.1
     */
    public static final ObjectIdentifier DH_oid = new ObjectIdentifier(DH_data);

    /**
     * Algorithm ID for the Diffie Hellman Key Agreement (DH), from the
     * IETF PKIX IPKI Part I.
     * Parameters may include public values P and G.
     * OID = 1.2.840.10046.2.1
     */
    public static final ObjectIdentifier DH_PKIX_oid = new ObjectIdentifier(DH_PKIX_data);

    /**
     * Algorithm ID for the Digital Signing Algorithm (DSA), from the
     * NIST OIW Stable Agreements part 12.
     * Parameters may include public values P, Q, and G; or these may be
     * derived from
     * another source such as a Certificate Authority's certificate.
     * OID = 1.3.14.3.2.12
     */
    public static final ObjectIdentifier DSA_OIW_oid = new ObjectIdentifier(DSA_OIW_data);

    /**
     * Algorithm ID for the Digital Signing Algorithm (DSA), from the
     * IETF PKIX IPKI Part I.
     * Parameters may include public values P, Q, and G; or these may be
     * derived from
     * another source such as a Certificate Authority's certificate.
     * OID = 1.2.840.10040.4.1
     */
    public static final ObjectIdentifier DSA_oid = new ObjectIdentifier(DSA_PKIX_data);

    /**
     * Algorithm ID for RSA keys used for any purpose, as defined in X.509.
     * The algorithm parameter is a single value, the number of bits in the
     * public modulus.
     * OID = 1.2.5.8.1.1
     */
    public static final ObjectIdentifier RSA_oid = new ObjectIdentifier(RSA_data);

    /**
     * Algorithm ID for RSA keys used with RSA encryption, as defined
     * in PKCS #1. There are no parameters associated with this algorithm.
     * OID = 1.2.840.113549.1.1.1
     */
    public static final ObjectIdentifier RSAEncryption_oid = new ObjectIdentifier(RSAEncryption_data);

    /*
     * COMMON SIGNATURE ALGORITHMS
     */
    @Deprecated(since="5.0.1", forRemoval=true)
    private static final int sha1WithEC_data[] =
                                   { 1, 2, 840, 10045, 4, 1 };
    private static final int sha224WithEC_data[] =
                                   { 1, 2, 840, 10045, 4, 3, 1 };
    private static final int sha256WithEC_data[] =
                                   { 1, 2, 840, 10045, 4, 3, 2 };
    private static final int sha384WithEC_data[] =
                                   { 1, 2, 840, 10045, 4, 3, 3 };
    private static final int sha512WithEC_data[] =
                                   { 1, 2, 840, 10045, 4, 3, 4 };
    private static final int md2WithRSAEncryption_data[] =
                                   { 1, 2, 840, 113549, 1, 1, 2 };
    private static final int md5WithRSAEncryption_data[] =
                                   { 1, 2, 840, 113549, 1, 1, 4 };
    @Deprecated(since="5.0.1", forRemoval=true)
    private static final int sha1WithRSAEncryption_data[] =
                                   { 1, 2, 840, 113549, 1, 1, 5 };
    private static final int sha256WithRSAEncryption_data[] =
                                   { 1, 2, 840, 113549, 1, 1, 11 };
    private static final int sha384WithRSAEncryption_data[] =
                                   { 1, 2, 840, 113549, 1, 1, 12 };
    private static final int sha512WithRSAEncryption_data[] =
                                   { 1, 2, 840, 113549, 1, 1, 13 };
    @Deprecated(since="5.0.1", forRemoval=true)
    private static final int sha1WithRSAEncryption_OIW_data[] =
                                   { 1, 3, 14, 3, 2, 29 };
    @Deprecated(since="5.0.1", forRemoval=true)
    private static final int shaWithDSA_OIW_data[] =
                                   { 1, 3, 14, 3, 2, 13 };
    @Deprecated(since="5.0.1", forRemoval=true)
    private static final int sha1WithDSA_OIW_data[] =
                                   { 1, 3, 14, 3, 2, 27 };
    @Deprecated(since="5.0.1", forRemoval=true)
    private static final int dsaWithSHA1_PKIX_data[] =
                                   { 1, 2, 840, 10040, 4, 3 };

    private static final int rsaPSS_data[] =
                                   { 1, 2, 840, 113549, 1, 1, 10 };

    @Deprecated(since="5.0.1", forRemoval=true)
    public static final ObjectIdentifier sha1WithEC_oid = new
            ObjectIdentifier(sha1WithEC_data);

    public static final ObjectIdentifier sha224WithEC_oid = new
            ObjectIdentifier(sha224WithEC_data);

    public static final ObjectIdentifier sha256WithEC_oid = new
            ObjectIdentifier(sha256WithEC_data);

    public static final ObjectIdentifier sha384WithEC_oid = new
            ObjectIdentifier(sha384WithEC_data);

    public static final ObjectIdentifier sha512WithEC_oid = new
            ObjectIdentifier(sha512WithEC_data);


    public static final ObjectIdentifier rsaPSS_oid = new
            ObjectIdentifier(rsaPSS_data);

    /**
     * Identifies a signing algorithm where an MD2 digest is encrypted
     * using an RSA private key; defined in PKCS #1. Use of this
     * signing algorithm is discouraged due to MD2 vulnerabilities.
     * OID = 1.2.840.113549.1.1.2
     */
    public static final ObjectIdentifier md2WithRSAEncryption_oid = new
            ObjectIdentifier(md2WithRSAEncryption_data);

    /**
     * Identifies a signing algorithm where an MD5 digest is
     * encrypted using an RSA private key; defined in PKCS #1.
     * OID = 1.2.840.113549.1.1.4
     */
    public static final ObjectIdentifier md5WithRSAEncryption_oid = new
            ObjectIdentifier(md5WithRSAEncryption_data);

    /**
     * The proper one for sha1/rsa
     */
    @Deprecated(since="5.0.1", forRemoval=true)
    public static final ObjectIdentifier sha1WithRSAEncryption_oid = new
            ObjectIdentifier(sha1WithRSAEncryption_data);

    /**
     * The proper one for sha256/rsa
     */
    public static final ObjectIdentifier sha256WithRSAEncryption_oid = new
            ObjectIdentifier(sha256WithRSAEncryption_data);

    /**
     * The proper one for sha384/rsa
     */
    public static final ObjectIdentifier sha384WithRSAEncryption_oid = new
            ObjectIdentifier(sha384WithRSAEncryption_data);

    /**
     * The proper one for sha512/rsa
     */
    public static final ObjectIdentifier sha512WithRSAEncryption_oid = new
            ObjectIdentifier(sha512WithRSAEncryption_data);

    /**
     * Identifies a signing algorithm where an SHA1 digest is
     * encrypted using an RSA private key; defined in NIST OIW.
     * OID = 1.3.14.3.2.29
     */
    @Deprecated(since="5.0.1", forRemoval=true)
    public static final ObjectIdentifier sha1WithRSAEncryption_OIW_oid = new
            ObjectIdentifier(sha1WithRSAEncryption_OIW_data);

    /**
     * Identifies the FIPS 186 "Digital Signature Standard" (DSS), where a
     * SHA digest is signed using the Digital Signing Algorithm (DSA).
     * This should not be used.
     * OID = 1.3.14.3.2.13
     */
    @Deprecated(since="5.0.1", forRemoval=true)
    public static final ObjectIdentifier shaWithDSA_OIW_oid = new ObjectIdentifier(shaWithDSA_OIW_data);

    /**
     * Identifies the FIPS 186 "Digital Signature Standard" (DSS), where a
     * SHA1 digest is signed using the Digital Signing Algorithm (DSA).
     * OID = 1.3.14.3.2.27
     */
    @Deprecated(since="5.0.1", forRemoval=true)
    public static final ObjectIdentifier sha1WithDSA_OIW_oid = new ObjectIdentifier(sha1WithDSA_OIW_data);

    /**
     * Identifies the FIPS 186 "Digital Signature Standard" (DSS), where a
     * SHA1 digest is signed using the Digital Signing Algorithm (DSA).
     * OID = 1.2.840.10040.4.3
     */
    @Deprecated(since="5.0.1", forRemoval=true)
    public static final ObjectIdentifier sha1WithDSA_oid = new ObjectIdentifier(dsaWithSHA1_PKIX_data);

    /**
     * Supported signing algorithms for a DSA key.
     */
    @Deprecated(since="5.0.1", forRemoval=true)
    public static final String[] DSA_SIGNING_ALGORITHMS = new String[]
    { "SHA1withDSA" };

    /**
     * Supported signing algorithms for a RSA key.
     */
    public static final String[] RSA_SIGNING_ALGORITHMS = new String[]
    { "SHA256withRSA", "SHA384withRSA", "SHA512withRSA", "SHA256withRSA/PSS", "SHA384withRSA/PSS", "SHA512withRSA/PSS" };

    public static final String[] EC_SIGNING_ALGORITHMS = new String[]
    { "SHA256withEC", "SHA384withEC", "SHA512withEC" };

    /**
     * All supported signing algorithms.
     */
    public static final String[] ALL_SIGNING_ALGORITHMS = ArrayUtils.addAll(RSA_SIGNING_ALGORITHMS, EC_SIGNING_ALGORITHMS);
}
