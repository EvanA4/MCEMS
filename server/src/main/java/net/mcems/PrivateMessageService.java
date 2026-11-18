package net.mcems;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import net.mcems.shared.MessageEnvelope;
import net.mcems.shared.NetworkPayloads;
import net.mcems.shared.ProtocolConstants;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class PrivateMessageService
        implements PayloadDispatcher.PrivateMessageHandler {

    private final Plugin plugin;
    private final PlayerIdentityService identities;
    private final SessionStore sessionStore;

    /*
     * Tracks the last accepted counter for each session and sender.
     * This assumes the Paper event handler runs on the server thread.
     */
    private final Map<CounterKey, Long> lastCounters = new HashMap<>();

    public PrivateMessageService(
            Plugin plugin,
            PlayerIdentityService identities,
            SessionStore sessionStore
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.identities = Objects.requireNonNull(identities, "identities");
        this.sessionStore = Objects.requireNonNull(sessionStore, "sessionStore");
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

        byte[] encodedPayload =
                NetworkPayloads.encodeMessage(envelope);

        /*
         * Update before sending so the same counter cannot be replayed if
         * handling is re-entered. If delivery fails, the sender should use a
         * new counter for a retry.
         */
        lastCounters.put(key, envelope.messageCounter());

        recipient.sendPluginMessage(
                plugin,
                ProtocolConstants.CHANNEL_ID,
                encodedPayload
        );
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
}
