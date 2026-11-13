package net.mcems;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

public final class MessageEnvelope {
    private static final int MAGIC = 0x4D534731; // "MSG1"
    private static final int MAX_ENVELOPE_BYTES = 32 * 1024;
    private static final int MAX_ID_BYTES = 128;
    private static final int MAX_CIPHERTEXT_BYTES = 16 * 1024;
    private static final int GCM_TAG_BYTES = CryptoConfig.GCM_TAG_BITS / 8;

    private final int protocolVersion;
    private final String senderId;
    private final String recipientId;
    private final UUID sessionId;
    private final long messageCounter;
    private final byte[] nonce;
    private final byte[] ciphertext;

    public MessageEnvelope(
            int protocolVersion,
            String senderId,
            String recipientId,
            UUID sessionId,
            long messageCounter,
            byte[] nonce,
            byte[] ciphertext) {

        if (protocolVersion <= 0) {
            throw new IllegalArgumentException("protocolVersion must be positive");
        }
        this.senderId = requireId(senderId, "senderId");
        this.recipientId = requireId(recipientId, "recipientId");
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");

        if (messageCounter < 0) {
            throw new IllegalArgumentException("messageCounter must not be negative");
        }

        Objects.requireNonNull(nonce, "nonce");
        if (nonce.length != CryptoConfig.GCM_NONCE_BYTES) {
            throw new IllegalArgumentException("Invalid GCM nonce length");
        }

        Objects.requireNonNull(ciphertext, "ciphertext");
        if (ciphertext.length < GCM_TAG_BYTES
                || ciphertext.length > MAX_CIPHERTEXT_BYTES) {
            throw new IllegalArgumentException("Invalid ciphertext length");
        }

        this.protocolVersion = protocolVersion;
        this.messageCounter = messageCounter;
        this.nonce = nonce.clone();
        this.ciphertext = ciphertext.clone();
    }

    public int protocolVersion() {
        return protocolVersion;
    }

    public String senderId() {
        return senderId;
    }

    public String recipientId() {
        return recipientId;
    }

    public UUID sessionId() {
        return sessionId;
    }

    public long messageCounter() {
        return messageCounter;
    }

    public byte[] nonce() {
        return nonce.clone();
    }

    public byte[] ciphertext() {
        return ciphertext.clone();
    }

    /** Encodes this envelope as a bounded, length-prefixed binary payload. */
    public byte[] encode() throws IOException {
        byte[] sender = senderId.getBytes(StandardCharsets.UTF_8);
        byte[] recipient = recipientId.getBytes(StandardCharsets.UTF_8);

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(buffer)) {
            out.writeInt(MAGIC);
            out.writeInt(protocolVersion);
            writeBytes(out, sender);
            writeBytes(out, recipient);
            out.writeLong(sessionId.getMostSignificantBits());
            out.writeLong(sessionId.getLeastSignificantBits());
            out.writeLong(messageCounter);
            writeBytes(out, nonce);
            writeBytes(out, ciphertext);
        }

        byte[] encoded = buffer.toByteArray();
        if (encoded.length > MAX_ENVELOPE_BYTES) {
            throw new IOException("Envelope exceeds maximum size");
        }
        return encoded;
    }

    /** Decodes an envelope, rejecting malformed or oversized payloads. */
    public static MessageEnvelope decode(byte[] encoded) throws IOException {
        Objects.requireNonNull(encoded, "encoded");
        if (encoded.length == 0 || encoded.length > MAX_ENVELOPE_BYTES) {
            throw new IOException("Invalid envelope size");
        }

        try (DataInputStream in =
                     new DataInputStream(new ByteArrayInputStream(encoded))) {
            if (in.readInt() != MAGIC) {
                throw new IOException("Invalid envelope magic");
            }

            int version = in.readInt();
            String sender = decodeUtf8(readBytes(in, MAX_ID_BYTES));
            String recipient = decodeUtf8(readBytes(in, MAX_ID_BYTES));

            UUID sessionId = new UUID(in.readLong(), in.readLong());
            long counter = in.readLong();
            byte[] nonce = readBytes(in, CryptoConfig.GCM_NONCE_BYTES);
            byte[] ciphertext = readBytes(in, MAX_CIPHERTEXT_BYTES);

            if (in.available() != 0) {
                throw new IOException("Trailing data in envelope");
            }

            try {
                return new MessageEnvelope(
                        version, sender, recipient, sessionId,
                        counter, nonce, ciphertext);
            } catch (IllegalArgumentException | NullPointerException e) {
                throw new IOException("Invalid envelope fields", e);
            }
        } catch (java.io.EOFException e) {
            throw new IOException("Truncated envelope", e);
        }
    }

    private static String requireId(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (value.isBlank() || bytes.length > MAX_ID_BYTES) {
            throw new IllegalArgumentException(
                    fieldName + " must be nonblank and at most "
                            + MAX_ID_BYTES + " UTF-8 bytes");
        }
        return value;
    }

    private static void writeBytes(DataOutputStream out, byte[] value)
            throws IOException {
        out.writeInt(value.length);
        out.write(value);
    }

    private static byte[] readBytes(DataInputStream in, int maximumLength)
            throws IOException {
        int length = in.readInt();
        if (length < 0 || length > maximumLength || length > in.available()) {
            throw new IOException("Invalid field length");
        }

        byte[] value = new byte[length];
        in.readFully(value);
        return value;
    }

    private static String decodeUtf8(byte[] bytes) throws IOException {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new IOException("Invalid UTF-8 field", e);
        }
    }
}
