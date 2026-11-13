package net.mcems;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.security.KeyFactory;
import java.util.Objects;

public final class KeyManager {
    private final KeyPair identityKeyPair;

    public KeyManager() throws GeneralSecurityException {
        this.identityKeyPair = generateKeyPair();
    }

    /** Returns this instance's identity key pair. */
    public KeyPair identityKeyPair() {
        return identityKeyPair;
    }

    /** Returns the encoded identity public key for sharing with a peer. */
    public byte[] identityPublicKeyEncoded() {
        return identityKeyPair.getPublic().getEncoded().clone();
    }

    /** Generates a new key pair, suitable for a session's ephemeral key. */
    public KeyPair generateEphemeralKeyPair() throws GeneralSecurityException {
        return generateKeyPair();
    }

    /**
     * Decodes an EC public key in X.509 SubjectPublicKeyInfo format.
     * Validate that the decoded key is appropriate for your protocol before use.
     */
    public static PublicKey decodeEcPublicKey(byte[] encoded)
            throws GeneralSecurityException {
        Objects.requireNonNull(encoded, "encoded");
        if (encoded.length == 0 || encoded.length > 1024) {
            throw new IllegalArgumentException("Invalid public-key encoding size");
        }

        KeyFactory keyFactory = KeyFactory.getInstance(CryptoConfig.EC_KEY_ALGORITHM);
        return keyFactory.generatePublic(new X509EncodedKeySpec(encoded.clone()));
    }

    private static KeyPair generateKeyPair() throws GeneralSecurityException {
        return CryptoConfig.newEcKeyPairGenerator().generateKeyPair();
    }
}
