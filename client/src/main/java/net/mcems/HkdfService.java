package net.mcems;

import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class HkdfService {
    private static final int HASH_LENGTH = CryptoConfig.HKDF_SHA256_HASH_BYTES;
    private static final int MAX_OUTPUT_LENGTH =
            CryptoConfig.HKDF_MAX_OUTPUT_BYTES;

    /**
     * Derives outputLength bytes using HKDF-SHA-256 (RFC 5869).
     * A null salt is treated as an all-zero salt; a null info value as empty.
     */
    public byte[] deriveKey(byte[] ikm, byte[] salt, byte[] info, int outputLength)
            throws GeneralSecurityException {
        Objects.requireNonNull(ikm, "ikm");

        if (outputLength < 1 || outputLength > MAX_OUTPUT_LENGTH) {
            throw new IllegalArgumentException(
                    "outputLength must be between 1 and " + MAX_OUTPUT_LENGTH);
        }

        byte[] effectiveSalt =
                salt == null ? new byte[HASH_LENGTH] : salt.clone();
        byte[] effectiveInfo = info == null ? new byte[0] : info.clone();

        byte[] prk = null;
        byte[] previousBlock = new byte[0];

        try {
            Mac mac = CryptoConfig.newHkdfMac();

            // HKDF-Extract: PRK = HMAC(salt, IKM)
            mac.init(new SecretKeySpec(effectiveSalt,
                    CryptoConfig.HKDF_MAC_ALGORITHM));
            prk = mac.doFinal(ikm);

            // HKDF-Expand: T(i) = HMAC(PRK, T(i-1) || info || i)
            byte[] output = new byte[outputLength];
            int offset = 0;

            for (int counter = 1; offset < outputLength; counter++) {
                mac.init(new SecretKeySpec(prk,
                        CryptoConfig.HKDF_MAC_ALGORITHM));
                mac.update(previousBlock);
                mac.update(effectiveInfo);
                mac.update((byte) counter);

                byte[] nextBlock = mac.doFinal();
                Arrays.fill(previousBlock, (byte) 0);
                previousBlock = nextBlock;

                int bytesToCopy = Math.min(
                        nextBlock.length, outputLength - offset);
                System.arraycopy(nextBlock, 0, output, offset, bytesToCopy);
                offset += bytesToCopy;
            }

            return output;
        } finally {
            Arrays.fill(effectiveSalt, (byte) 0);
            Arrays.fill(effectiveInfo, (byte) 0);
            Arrays.fill(previousBlock, (byte) 0);
            if (prk != null) {
                Arrays.fill(prk, (byte) 0);
            }
        }
    }
}
