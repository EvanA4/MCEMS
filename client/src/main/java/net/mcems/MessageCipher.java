package net.mcems;

import java.security.GeneralSecurityException;
import java.util.Objects;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class MessageCipher {
    private static final int MAX_PLAINTEXT_BYTES = 16 * 1024
            - (CryptoConfig.GCM_TAG_BITS / 8);

    public EncryptedPayload encrypt(
            byte[] aesKey, byte[] plaintext, byte[] associatedData)
            throws GeneralSecurityException {
        validateKey(aesKey);
        Objects.requireNonNull(plaintext, "plaintext");
        validatePlaintextLength(plaintext.length);

        byte[] nonce = new byte[CryptoConfig.GCM_NONCE_BYTES];
        CryptoConfig.secureRandom().nextBytes(nonce);

        Cipher cipher = CryptoConfig.newMessageCipher();
        cipher.init(
                Cipher.ENCRYPT_MODE,
                new SecretKeySpec(aesKey, CryptoConfig.AES_KEY_ALGORITHM),
                new GCMParameterSpec(CryptoConfig.GCM_TAG_BITS, nonce));

        if (associatedData != null) {
            cipher.updateAAD(associatedData);
        }

        byte[] ciphertext = cipher.doFinal(plaintext);
        return new EncryptedPayload(nonce, ciphertext);
    }

    public byte[] decrypt(
            byte[] aesKey,
            byte[] nonce,
            byte[] ciphertext,
            byte[] associatedData)
            throws GeneralSecurityException {
        validateKey(aesKey);
        Objects.requireNonNull(nonce, "nonce");
        Objects.requireNonNull(ciphertext, "ciphertext");

        if (nonce.length != CryptoConfig.GCM_NONCE_BYTES) {
            throw new IllegalArgumentException("Invalid GCM nonce length");
        }
        if (ciphertext.length < CryptoConfig.GCM_TAG_BITS / 8
                || ciphertext.length > 16 * 1024) {
            throw new IllegalArgumentException("Invalid ciphertext length");
        }

        Cipher cipher = CryptoConfig.newMessageCipher();
        cipher.init(
                Cipher.DECRYPT_MODE,
                new SecretKeySpec(aesKey, CryptoConfig.AES_KEY_ALGORITHM),
                new GCMParameterSpec(CryptoConfig.GCM_TAG_BITS, nonce));

        if (associatedData != null) {
            cipher.updateAAD(associatedData);
        }

        // Throws AEADBadTagException if ciphertext or AAD was altered.
        return cipher.doFinal(ciphertext);
    }

    private static void validateKey(byte[] aesKey) {
        Objects.requireNonNull(aesKey, "aesKey");
        if (aesKey.length != CryptoConfig.AES_KEY_BYTES) {
            throw new IllegalArgumentException("Expected a 256-bit AES key");
        }
    }

    private static void validatePlaintextLength(int length) {
        if (length > MAX_PLAINTEXT_BYTES) {
            throw new IllegalArgumentException("Plaintext exceeds maximum size");
        }
    }

    public static final class EncryptedPayload {
        private final byte[] nonce;
        private final byte[] ciphertext;

        private EncryptedPayload(byte[] nonce, byte[] ciphertext) {
            this.nonce = nonce.clone();
            this.ciphertext = ciphertext.clone();
        }

        public byte[] nonce() {
            return nonce.clone();
        }

        public byte[] ciphertext() {
            return ciphertext.clone();
        }
    }
}
