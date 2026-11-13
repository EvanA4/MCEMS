package net.mcems;

import java.security.GeneralSecurityException;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.spec.ECGenParameterSpec;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;

public final class CryptoConfig {
    private CryptoConfig() {
        // Utility class; do not instantiate.
    }

    public static final String EC_KEY_ALGORITHM = "EC";
    public static final String EC_CURVE = "secp256r1";
    public static final String KEY_AGREEMENT_ALGORITHM = "ECDH";

    public static final String HKDF_MAC_ALGORITHM = "HmacSHA256";
    public static final String MESSAGE_CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    public static final String AES_KEY_ALGORITHM = "AES";

    public static final int AES_KEY_BYTES = 32;
    public static final int GCM_NONCE_BYTES = 12;
    public static final int GCM_TAG_BITS = 128;
    public static final int HKDF_SHA256_HASH_BYTES = 32;
    public static final int HKDF_MAX_OUTPUT_BYTES = 255 * HKDF_SHA256_HASH_BYTES;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public static KeyPairGenerator newEcKeyPairGenerator()
            throws GeneralSecurityException {
        KeyPairGenerator generator =
                KeyPairGenerator.getInstance(EC_KEY_ALGORITHM);
        generator.initialize(new ECGenParameterSpec(EC_CURVE), SECURE_RANDOM);
        return generator;
    }

    public static KeyAgreement newKeyAgreement()
            throws GeneralSecurityException {
        return KeyAgreement.getInstance(KEY_AGREEMENT_ALGORITHM);
    }

    public static Mac newHkdfMac()
            throws GeneralSecurityException {
        return Mac.getInstance(HKDF_MAC_ALGORITHM);
    }

    public static Cipher newMessageCipher()
            throws GeneralSecurityException {
        return Cipher.getInstance(MESSAGE_CIPHER_ALGORITHM);
    }

    public static SecureRandom secureRandom() {
        return SECURE_RANDOM;
    }
}
