package net.mcems;

import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.interfaces.ECPublicKey;
import java.util.Objects;

public final class PublicKeyCodec {
    private static final int MAX_ENCODED_KEY_BYTES = 512;
    private static final ECParameterSpec P256_PARAMETERS = loadP256Parameters();

    private PublicKeyCodec() {
        // Utility class.
    }

    /** Encodes a P-256 EC public key as X.509 SubjectPublicKeyInfo. */
    public static byte[] encode(PublicKey publicKey)
            throws GeneralSecurityException {
        Objects.requireNonNull(publicKey, "publicKey");

        if (!(publicKey instanceof ECPublicKey ecPublicKey)
                || !isP256(ecPublicKey.getParams())) {
            throw new GeneralSecurityException("Expected a P-256 EC public key");
        }

        byte[] encoded = publicKey.getEncoded();
        if (encoded == null || encoded.length == 0
                || encoded.length > MAX_ENCODED_KEY_BYTES) {
            throw new GeneralSecurityException("Invalid encoded public key size");
        }

        return encoded.clone();
    }

    /** Decodes an X.509 SubjectPublicKeyInfo encoding and requires P-256. */
    public static PublicKey decode(byte[] encoded)
            throws GeneralSecurityException {
        Objects.requireNonNull(encoded, "encoded");

        if (encoded.length == 0 || encoded.length > MAX_ENCODED_KEY_BYTES) {
            throw new GeneralSecurityException("Invalid encoded public key size");
        }

        KeyFactory keyFactory = KeyFactory.getInstance(CryptoConfig.EC_KEY_ALGORITHM);
        PublicKey publicKey = keyFactory.generatePublic(
                new java.security.spec.X509EncodedKeySpec(encoded.clone()));

        if (!(publicKey instanceof ECPublicKey ecPublicKey)
                || !isP256(ecPublicKey.getParams())) {
            throw new GeneralSecurityException("Expected a P-256 EC public key");
        }

        return publicKey;
    }

    private static ECParameterSpec loadP256Parameters() {
        try {
            AlgorithmParameters parameters =
                    AlgorithmParameters.getInstance(CryptoConfig.EC_KEY_ALGORITHM);
            parameters.init(new ECGenParameterSpec(CryptoConfig.EC_CURVE));
            return parameters.getParameterSpec(ECParameterSpec.class);
        } catch (GeneralSecurityException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static boolean isP256(ECParameterSpec candidate) {
        if (candidate == null) {
            return false;
        }

        ECParameterSpec expected = P256_PARAMETERS;
        return candidate.getCofactor() == expected.getCofactor()
                && candidate.getOrder().equals(expected.getOrder())
                && candidate.getGenerator().equals(expected.getGenerator())
                && candidate.getCurve().equals(expected.getCurve());
    }
}
