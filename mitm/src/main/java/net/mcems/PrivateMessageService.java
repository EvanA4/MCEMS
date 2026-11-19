package net.mcems;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.*;

import net.mcems.mitm.MessageCipher;
import net.mcems.mitm.SessionManager;
import net.mcems.shared.HandshakeService;
import net.mcems.shared.MessageEnvelope;
import net.mcems.shared.NetworkPayloads;
import net.mcems.shared.ProtocolConstants;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PrivateMessageService
        implements PayloadDispatcher.PrivateMessageHandler {

    private static final Logger logger = LoggerFactory.getLogger(PrivateMessageService.class);

    private final Plugin plugin;
    private final PlayerIdentityService identities;
    private final SessionStore sessionStore;
    private final SessionManager sessionManager;
    private final HandshakeService handshakeService;
    private final MessageCipher messageCipher;

    /*
     * Tracks the last accepted counter for each session and sender.
     * This assumes the Paper event handler runs on the server thread.
     */
    private final Map<CounterKey, Long> lastCounters = new HashMap<>();

    public PrivateMessageService(
            Plugin plugin,
            PlayerIdentityService identities,
            SessionStore sessionStore,
            SessionManager sessionManager,
            HandshakeService handshakeService
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.identities = Objects.requireNonNull(identities, "identities");
        this.sessionStore = Objects.requireNonNull(sessionStore, "sessionStore");
        this.sessionManager = sessionManager;
        this.handshakeService = handshakeService;
        this.messageCipher = new MessageCipher();
    }

    @Override
    public synchronized void handleMessage(
            Player sender,
            MessageEnvelope envelope
    ) throws IOException {
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(envelope, "envelope");

        String senderId = identities.idFor(sender);

        if (!senderId.equals(envelope.senderId())) {
            throw new IllegalArgumentException(
                    "Envelope sender ID does not match the connected player"
            );
        }

        if (envelope.protocolVersion() != ProtocolConstants.PROTOCOL_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported message protocol version"
            );
        }

        if (!sessionStore.allows(
                envelope.sessionId(),
                senderId,
                envelope.recipientId()
        )) {
            throw new IllegalArgumentException(
                    "No active session permits this sender and recipient"
            );
        }

        Player recipient =
                identities.findOnlinePlayerById(envelope.recipientId());
        if (recipient == null || !recipient.isOnline()) {
            throw new IllegalArgumentException(
                    "Message recipient is not online"
            );
        }

        CounterKey key = new CounterKey(envelope.sessionId(), senderId);
        Long previousCounter = lastCounters.get(key);

        if (previousCounter != null
                && envelope.messageCounter() <= previousCounter) {
            throw new IllegalArgumentException(
                    "Message counter is not increasing"
            );
        }

        /*
         * Update before sending so the same counter cannot be replayed if
         * handling is re-entered. If delivery fails, the sender should use a
         * new counter for a retry.
         */
        lastCounters.put(key, envelope.messageCounter());

        // Swallow message
        // byte[] encodedPayload =
        //         NetworkPayloads.encodeMessage(envelope);

        // Decrypt message
        if (envelope.protocolVersion() != ProtocolConstants.PROTOCOL_VERSION) {
            return;
        }

        byte[] decryptionKey = sessionManager.getKey(envelope.sessionId());
        if (decryptionKey == null) {
            return;
        }

        byte[] plaintext = null;
        byte[] aad = messageAad(
                envelope.protocolVersion(),
                envelope.senderId(),
                envelope.recipientId(),
                envelope.sessionId(),
                envelope.messageCounter());

        try {
            // Authenticate before advancing the replay counter, so an invalid
            // ciphertext cannot consume a legitimate message counter.
            plaintext = messageCipher.decrypt(
                    decryptionKey, envelope.nonce(), envelope.ciphertext(), aad);

            if (!sessionManager.acceptIncoming(
                    envelope.sessionId(),
                    envelope.senderId(),
                    envelope.messageCounter())) {
                return;
            }

            String message = StandardCharsets.UTF_8.newDecoder()
                    .decode(ByteBuffer.wrap(plaintext))
                    .toString();

            // Log decrypted message
            logger.info(
                "{} -> {} : {}",
                uuidToName(envelope.senderId()),
                uuidToName(envelope.recipientId()),
                message
            );

            // Encrypt message with actual key
            byte[] encryptKey = new byte[]{};
            try {
                UUID outSessionId = sessionManager.getSessionId(envelope.recipientId());
                long counter = sessionManager.nextOutgoingCounter(outSessionId);
                byte[] plainBytes = message.getBytes(StandardCharsets.UTF_8);
                byte[] outAad = messageAad(
                        ProtocolConstants.PROTOCOL_VERSION,
                        envelope.senderId(),
                        envelope.recipientId(),
                        outSessionId,
                        counter);

                encryptKey = sessionManager.getKey(outSessionId);
                MessageCipher.EncryptedPayload encrypted =
                        messageCipher.encrypt(encryptKey, plainBytes, outAad);

                MessageEnvelope outEnvelope = new MessageEnvelope(
                        ProtocolConstants.PROTOCOL_VERSION,
                        envelope.senderId(),
                        envelope.recipientId(),
                        outSessionId,
                        counter,
                        encrypted.nonce(),
                        encrypted.ciphertext());

                // Encode and send message
                recipient.sendPluginMessage(
                        plugin,
                        ProtocolConstants.CHANNEL_ID,
                        NetworkPayloads.encodeMessage(outEnvelope)
                );

                Arrays.fill(plaintext, (byte) 0);
                Arrays.fill(aad, (byte) 0);
            } finally {
                assert encryptKey != null;
                Arrays.fill(encryptKey, (byte) 0);
            }


        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        } finally {
            Arrays.fill(decryptionKey, (byte) 0);
            Arrays.fill(aad, (byte) 0);
            if (plaintext != null) {
                Arrays.fill(plaintext, (byte) 0);
            }
        }
    }

    public synchronized void removePlayer(Player player) {
        Objects.requireNonNull(player, "player");
        removePlayerId(identities.idFor(player));
    }

    public synchronized void removePlayerId(String playerId) {
        if (playerId == null) {
            return;
        }

        Iterator<CounterKey> iterator = lastCounters.keySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().senderId.equals(playerId)) {
                iterator.remove();
            }
        }
    }

    public synchronized void clear() {
        lastCounters.clear();
    }

    private record CounterKey(UUID sessionId, String senderId) {
        private CounterKey {
            Objects.requireNonNull(sessionId, "sessionId");
            Objects.requireNonNull(senderId, "senderId");

            if (senderId.isBlank()
                    || senderId.getBytes(StandardCharsets.UTF_8).length
                    > ProtocolConstants.MAX_ID_BYTES) {
                throw new IllegalArgumentException("Invalid sender ID");
            }
        }
    }

    private static byte[] messageAad(
            int version, String sender, String recipient, UUID sessionId, long counter) {
        byte[] senderBytes = sender.getBytes(StandardCharsets.UTF_8);
        byte[] recipientBytes = recipient.getBytes(StandardCharsets.UTF_8);

        return ByteBuffer.allocate(
                        4 + 4 + senderBytes.length + 4 + recipientBytes.length + 16 + 8)
                .putInt(version)
                .putInt(senderBytes.length)
                .put(senderBytes)
                .putInt(recipientBytes.length)
                .put(recipientBytes)
                .putLong(sessionId.getMostSignificantBits())
                .putLong(sessionId.getLeastSignificantBits())
                .putLong(counter)
                .array();
    }

    private static String uuidToName(String uuid) {
        return Bukkit.getOfflinePlayer(UUID.fromString(uuid)).getName();
    }
}
