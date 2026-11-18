package net.mcems.shared;

/**
 * Shared wire-protocol constants for the Fabric client and Paper plugin.
 *
 * Keep these values identical on both sides. This class has no Fabric or
 * Paper dependencies so it can live in a shared source set or be copied
 * into both projects.
 */
public final class ProtocolConstants {
    private ProtocolConstants() {
        throw new AssertionError("No instances");
    }

    /** Must match the channel ID registered by both the client and server. */
    public static final String CHANNEL_NAMESPACE = "mcems";
    public static final String CHANNEL_PATH = "secure_message";
    public static final String CHANNEL_ID =
            CHANNEL_NAMESPACE + ":" + CHANNEL_PATH;

    public static final int PROTOCOL_VERSION = 1;

    /** NetworkPayloads header magic: ASCII "NP01". */
    public static final int NETWORK_PAYLOAD_MAGIC = 0x4E503031;

    /** MessageEnvelope header magic: ASCII "MSG1". */
    public static final int MESSAGE_ENVELOPE_MAGIC = 0x4D534731;

    public static final int MAX_PAYLOAD_BYTES = 32 * 1024;
    public static final int MAX_ENVELOPE_BYTES = 32 * 1024;
    public static final int MAX_ID_BYTES = 128;
    public static final int MAX_PUBLIC_KEY_BYTES = 512;
    public static final int SALT_BYTES = 32;
    public static final int GCM_NONCE_BYTES = 12;
    public static final int GCM_TAG_BITS = 128;
    public static final int MAX_CIPHERTEXT_BYTES = 16 * 1024;

    /** Values used in the NetworkPayloads type byte. */
    public static final int TYPE_OFFER = 1;
    public static final int TYPE_RESPONSE = 2;
    public static final int TYPE_MESSAGE = 3;
}
