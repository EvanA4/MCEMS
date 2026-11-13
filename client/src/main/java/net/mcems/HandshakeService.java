package net.mcems;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class HandshakeService {
    private static final int PROTOCOL_VERSION = 1;
    private static final int SALT_BYTES = 32;
    private static final int MAX_PENDING_HANDSHAKES = 256;
    private static final long HANDSHAKE_TIMEOUT_MILLIS = 60_000;

    private final KeyManager keyManager;
    private final KeyAgreementService keyAgreementService;
    private final HkdfService hkdfService;
    private final Map<UUID, PendingHandshake> pending = new HashMap<>();

    public HandshakeService(
            KeyManager keyManager,
            KeyAgreementService keyAgreementService,
            HkdfService hkdfService) {
        this.keyManager = Objects.requireNonNull(keyManager, "keyManager");
        this.keyAgreementService =
                Objects.requireNonNull(keyAgreementService, "keyAgreementService");
        this.hkdfService = Objects.requireNonNull(hkdfService, "hkdfService");
    }

    /** Creates an offer; send it to the intended recipient. */
    public synchronized Offer createOffer(String senderId, String recipientId)
            throws GeneralSecurityException {
        validateId(senderId, "senderId");
        validateId(recipientId, "recipientId");

        if (senderId.equals(recipientId)) {
            throw new IllegalArgumentException("Sender and recipient must differ");
        }
        removeExpiredPending();
        if (pending.size() >= MAX_PENDING_HANDSHAKES) {
            throw new IllegalStateException("Too many pending handshakes");
        }

        UUID sessionId = UUID.randomUUID();
        KeyPair ephemeralKeyPair = keyManager.generateEphemeralKeyPair();
        byte[] salt = new byte[SALT_BYTES];
        CryptoConfig.secureRandom().nextBytes(salt);

        pending.put(sessionId, new PendingHandshake(
                senderId,
                recipientId,
                ephemeralKeyPair,
                salt,
                System.currentTimeMillis() + HANDSHAKE_TIMEOUT_MILLIS));

        return new Offer(
                sessionId,
                senderId,
                recipientId,
                PublicKeyCodec.encode(ephemeralKeyPair.getPublic()),
                salt);
    }

    /**
     * Accepts an offer and returns the response plus the responder's session key.
     * The caller should send response to the offer's sender.
     */
    public AcceptedHandshake acceptOffer(Offer offer)
            throws GeneralSecurityException {
        Objects.requireNonNull(offer, "offer");
        validateOffer(offer);

        KeyPair ephemeralKeyPair = keyManager.generateEphemeralKeyPair();
        PublicKey peerPublicKey = PublicKeyCodec.decode(offer.publicKey);

        byte[] sharedSecret = keyAgreementService.deriveSharedSecret(
                ephemeralKeyPair, peerPublicKey);

        byte[] info = makeInfo(
                offer.sessionId, offer.senderId, offer.recipientId);
        byte[] sessionKey;
        try {
            sessionKey = hkdfService.deriveKey(
                    sharedSecret, offer.salt, info, CryptoConfig.AES_KEY_BYTES);
        } finally {
            Arrays.fill(sharedSecret, (byte) 0);
            Arrays.fill(info, (byte) 0);
        }

        Response response = new Response(
                offer.sessionId,
                offer.recipientId,
                offer.senderId,
                PublicKeyCodec.encode(ephemeralKeyPair.getPublic()));

        return new AcceptedHandshake(response, sessionKey);
    }

    /** Completes an initiator's pending handshake using the received response. */
    public synchronized EstablishedSession complete(Response response)
            throws GeneralSecurityException {
        Objects.requireNonNull(response, "response");
        removeExpiredPending();

        PendingHandshake handshake = pending.get(response.sessionId);
        if (handshake == null) {
            throw new GeneralSecurityException(
                    "Unknown or expired handshake response");
        }
        if (!handshake.senderId.equals(response.recipientId)
                || !handshake.recipientId.equals(response.senderId)) {
            throw new GeneralSecurityException(
                    "Handshake response identities do not match");
        }

        PublicKey peerPublicKey = PublicKeyCodec.decode(response.publicKey);
        byte[] sharedSecret = keyAgreementService.deriveSharedSecret(
                handshake.ephemeralKeyPair, peerPublicKey);
        byte[] info = makeInfo(
                response.sessionId,
                handshake.senderId,
                handshake.recipientId);

        byte[] sessionKey;
        try {
            sessionKey = hkdfService.deriveKey(
                    sharedSecret, handshake.salt, info,
                    CryptoConfig.AES_KEY_BYTES);
        } finally {
            Arrays.fill(sharedSecret, (byte) 0);
            Arrays.fill(info, (byte) 0);
        }

        pending.remove(response.sessionId);
        handshake.clear();
        return new EstablishedSession(
                response.sessionId, handshake.recipientId, sessionKey);
    }

    private static void validateOffer(Offer offer)
            throws GeneralSecurityException {
        if (offer.protocolVersion != PROTOCOL_VERSION
                || offer.sessionId == null
                || offer.senderId == null
                || offer.recipientId == null
                || offer.senderId.equals(offer.recipientId)
                || offer.salt.length != SALT_BYTES
                || offer.publicKey.length == 0) {
            throw new GeneralSecurityException("Invalid handshake offer");
        }
    }

    private void removeExpiredPending() {
        long now = System.currentTimeMillis();
        pending.entrySet().removeIf(entry -> {
            PendingHandshake value = entry.getValue();
            if (value.expiresAtMillis <= now) {
                value.clear();
                return true;
            }
            return false;
        });
    }

    private static byte[] makeInfo(UUID sessionId, String initiatorId,
                                   String responderId) {
        byte[] initiator = initiatorId.getBytes(StandardCharsets.UTF_8);
        byte[] responder = responderId.getBytes(StandardCharsets.UTF_8);
        byte[] label = "demo messaging key v1"
                .getBytes(StandardCharsets.UTF_8);

        ByteBuffer buffer = ByteBuffer.allocate(
                label.length + 16 + 4 + initiator.length + 4 + responder.length);
        buffer.put(label);
        buffer.putLong(sessionId.getMostSignificantBits());
        buffer.putLong(sessionId.getLeastSignificantBits());
        buffer.putInt(initiator.length).put(initiator);
        buffer.putInt(responder.length).put(responder);
        return buffer.array();
    }

    private static void validateId(String id, String name) {
        Objects.requireNonNull(id, name);
        if (id.isBlank()
                || id.getBytes(StandardCharsets.UTF_8).length > 128) {
            throw new IllegalArgumentException("Invalid " + name);
        }
    }

    public static final class Offer {
        private final int protocolVersion = PROTOCOL_VERSION;
        private final UUID sessionId;
        private final String senderId;
        private final String recipientId;
        private final byte[] publicKey;
        private final byte[] salt;

        Offer(UUID sessionId, String senderId, String recipientId,
              byte[] publicKey, byte[] salt) {
            this.sessionId = sessionId;
            this.senderId = senderId;
            this.recipientId = recipientId;
            this.publicKey = publicKey.clone();
            this.salt = salt.clone();
        }

        public UUID sessionId() { return sessionId; }
        public String senderId() { return senderId; }
        public String recipientId() { return recipientId; }
        public byte[] publicKey() { return publicKey.clone(); }
        public byte[] salt() { return salt.clone(); }
    }

    public static final class Response {
        private final UUID sessionId;
        private final String senderId;
        private final String recipientId;
        private final byte[] publicKey;

        Response(UUID sessionId, String senderId, String recipientId,
                 byte[] publicKey) {
            this.sessionId = sessionId;
            this.senderId = senderId;
            this.recipientId = recipientId;
            this.publicKey = publicKey.clone();
        }

        public UUID sessionId() { return sessionId; }
        public String senderId() { return senderId; }
        public String recipientId() { return recipientId; }
        public byte[] publicKey() { return publicKey.clone(); }
    }

    public static final class AcceptedHandshake {
        private final Response response;
        private final byte[] sessionKey;

        private AcceptedHandshake(Response response, byte[] sessionKey) {
            this.response = response;
            this.sessionKey = sessionKey.clone();
        }

        public Response response() { return response; }
        public byte[] sessionKey() { return sessionKey.clone(); }
    }

    public static final class EstablishedSession {
        private final UUID sessionId;
        private final String peerId;
        private final byte[] sessionKey;

        private EstablishedSession(UUID sessionId, String peerId,
                                   byte[] sessionKey) {
            this.sessionId = sessionId;
            this.peerId = peerId;
            this.sessionKey = sessionKey.clone();
        }

        public UUID sessionId() { return sessionId; }
        public String peerId() { return peerId; }
        public byte[] sessionKey() { return sessionKey.clone(); }
    }

    private static final class PendingHandshake {
        private final String senderId;
        private final String recipientId;
        private final KeyPair ephemeralKeyPair;
        private final byte[] salt;
        private final long expiresAtMillis;

        private PendingHandshake(String senderId, String recipientId,
                                 KeyPair ephemeralKeyPair, byte[] salt,
                                 long expiresAtMillis) {
            this.senderId = senderId;
            this.recipientId = recipientId;
            this.ephemeralKeyPair = ephemeralKeyPair;
            this.salt = salt.clone();
            this.expiresAtMillis = expiresAtMillis;
        }

        private void clear() {
            Arrays.fill(salt, (byte) 0);
        }
    }
}
