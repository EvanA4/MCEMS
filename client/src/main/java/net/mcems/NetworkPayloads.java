package net.mcems;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class NetworkPayloads {
    private static final int MAGIC = 0x4E503031; // "NP01"
    private static final int MAX_PAYLOAD_BYTES = 32 * 1024;
    private static final int MAX_ID_BYTES = 128;
    private static final int MAX_KEY_BYTES = 512;
    private static final int SALT_BYTES = 32;

    private static final int TYPE_OFFER = 1;
    private static final int TYPE_RESPONSE = 2;
    private static final int TYPE_MESSAGE = 3;

    private NetworkPayloads() {}

    public sealed interface Payload
            permits OfferPayload, ResponsePayload, MessagePayload {}

    public record OfferPayload(HandshakeService.Offer offer)
            implements Payload {}

    public record ResponsePayload(HandshakeService.Response response)
            implements Payload {}

    public record MessagePayload(MessageEnvelope envelope)
            implements Payload {}

    public static byte[] encodeOffer(HandshakeService.Offer offer)
            throws IOException {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                writeHeader(out, TYPE_OFFER);
                writeUuid(out, offer.sessionId());
                writeString(out, offer.senderId());
                writeString(out, offer.recipientId());
                writeBytes(out, offer.publicKey(), MAX_KEY_BYTES);
                writeBytes(out, offer.salt(), SALT_BYTES);
            }
            return finish(bytes);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid offer payload", e);
        }
    }

    public static byte[] encodeResponse(HandshakeService.Response response)
            throws IOException {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                writeHeader(out, TYPE_RESPONSE);
                writeUuid(out, response.sessionId());
                writeString(out, response.senderId());
                writeString(out, response.recipientId());
                writeBytes(out, response.publicKey(), MAX_KEY_BYTES);
            }
            return finish(bytes);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid response payload", e);
        }
    }

    public static byte[] encodeMessage(MessageEnvelope envelope)
            throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            writeHeader(out, TYPE_MESSAGE);
            writeBytes(out, envelope.encode(), MAX_PAYLOAD_BYTES);
        }
        return finish(bytes);
    }

    public static Payload decode(byte[] payload) throws IOException {
        if (payload == null || payload.length == 0
                || payload.length > MAX_PAYLOAD_BYTES) {
            throw new IOException("Invalid payload size");
        }

        try (DataInputStream in =
                     new DataInputStream(new ByteArrayInputStream(payload))) {
            if (in.readInt() != MAGIC) {
                throw new IOException("Invalid payload magic");
            }

            int type = in.readUnsignedByte();
            Payload decoded;

            switch (type) {
                case TYPE_OFFER -> {
                    UUID sessionId = readUuid(in);
                    String sender = readString(in);
                    String recipient = readString(in);
                    byte[] publicKey = readBytes(in, MAX_KEY_BYTES);
                    byte[] salt = readBytes(in, SALT_BYTES);

                    if (publicKey.length == 0 || salt.length != SALT_BYTES) {
                        throw new IOException("Invalid offer fields");
                    }

                    decoded = new OfferPayload(new HandshakeService.Offer(
                            sessionId, sender, recipient, publicKey, salt));
                }
                case TYPE_RESPONSE -> {
                    UUID sessionId = readUuid(in);
                    String sender = readString(in);
                    String recipient = readString(in);
                    byte[] publicKey = readBytes(in, MAX_KEY_BYTES);

                    if (publicKey.length == 0) {
                        throw new IOException("Invalid response fields");
                    }

                    decoded = new ResponsePayload(new HandshakeService.Response(
                            sessionId, sender, recipient, publicKey));
                }
                case TYPE_MESSAGE -> {
                    byte[] encodedEnvelope =
                            readBytes(in, MAX_PAYLOAD_BYTES);
                    decoded = new MessagePayload(
                            MessageEnvelope.decode(encodedEnvelope));
                }
                default -> throw new IOException("Unknown payload type");
            }

            if (in.available() != 0) {
                throw new IOException("Trailing data in payload");
            }
            return decoded;
        } catch (EOFException e) {
            throw new IOException("Truncated payload", e);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid payload fields", e);
        }
    }

    private static void writeHeader(DataOutputStream out, int type)
            throws IOException {
        out.writeInt(MAGIC);
        out.writeByte(type);
    }

    private static byte[] finish(ByteArrayOutputStream bytes)
            throws IOException {
        byte[] result = bytes.toByteArray();
        if (result.length > MAX_PAYLOAD_BYTES) {
            throw new IOException("Payload exceeds maximum size");
        }
        return result;
    }

    private static void writeUuid(DataOutputStream out, UUID id)
            throws IOException {
        out.writeLong(id.getMostSignificantBits());
        out.writeLong(id.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream in) throws IOException {
        return new UUID(in.readLong(), in.readLong());
    }

    private static void writeString(DataOutputStream out, String value)
            throws IOException {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        writeBytes(out, encoded, MAX_ID_BYTES);
    }

    private static String readString(DataInputStream in) throws IOException {
        byte[] encoded = readBytes(in, MAX_ID_BYTES);
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(encoded))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new IOException("Invalid UTF-8 string", e);
        }
    }

    private static void writeBytes(
            DataOutputStream out, byte[] value, int maxLength)
            throws IOException {
        if (value == null || value.length > maxLength) {
            throw new IOException("Invalid byte field length");
        }
        out.writeInt(value.length);
        out.write(value);
    }

    private static byte[] readBytes(DataInputStream in, int maxLength)
            throws IOException {
        int length = in.readInt();
        if (length < 0 || length > maxLength || length > in.available()) {
            throw new IOException("Invalid byte field length");
        }
        byte[] result = new byte[length];
        in.readFully(result);
        return result;
    }
}
