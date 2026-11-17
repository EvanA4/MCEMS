package net.mcems;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class HandshakeCoordinator
        implements PayloadDispatcher.HandshakeHandler {

    private static final int MAX_PENDING_HANDSHAKES = 256;
    private static final long HANDSHAKE_TIMEOUT_MILLIS = 60_000L;

    private final Plugin plugin;
    private final PlayerIdentityService identities;
    private final Map<UUID, PendingHandshake> pending = new HashMap<>();
    private final SessionStore sessionStore;

    public HandshakeCoordinator(
            Plugin plugin,
            PlayerIdentityService identities,
            SessionStore sessionStore
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.identities = Objects.requireNonNull(identities, "identities");
        this.sessionStore = sessionStore;
    }

    @Override
    public synchronized void handleOffer(
            Player sender,
            HandshakeService.Offer offer
    ) throws IOException {
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(offer, "offer");
        removeExpired();

        String senderId = identities.idFor(sender);
        if (!senderId.equals(offer.senderId())) {
            throw new IllegalArgumentException(
                    "Offer sender ID does not match the connected player"
            );
        }

        if (senderId.equals(offer.recipientId())) {
            throw new IllegalArgumentException(
                    "Handshake sender and recipient must differ"
            );
        }

        Player recipient =
                identities.findOnlinePlayerById(offer.recipientId());
        if (recipient == null || !recipient.isOnline()) {
            throw new IllegalArgumentException(
                    "Handshake recipient is not online"
            );
        }

        if (pending.size() >= MAX_PENDING_HANDSHAKES
                && !pending.containsKey(offer.sessionId())) {
            throw new IllegalStateException("Too many pending handshakes");
        }

        PendingHandshake old = pending.put(
                offer.sessionId(),
                new PendingHandshake(senderId, offer.recipientId(),
                        System.currentTimeMillis() + HANDSHAKE_TIMEOUT_MILLIS)
        );

        // Don't replace an existing session with different participants.
        if (old != null
                && (!old.initiatorId.equals(senderId)
                || !old.recipientId.equals(offer.recipientId()))) {
            pending.put(offer.sessionId(), old);
            throw new IllegalArgumentException(
                    "Session ID is already pending for other players"
            );
        }

        try {
            send(recipient, NetworkPayloads.encodeOffer(offer));
        } catch (IOException | RuntimeException e) {
            pending.remove(offer.sessionId());
            throw e;
        }
    }

    @Override
    public synchronized void handleResponse(
            Player sender,
            HandshakeService.Response response
    ) throws IOException {
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(response, "response");
        removeExpired();

        String senderId = identities.idFor(sender);
        if (!senderId.equals(response.senderId())) {
            throw new IllegalArgumentException(
                    "Response sender ID does not match the connected player"
            );
        }

        PendingHandshake handshake = pending.get(response.sessionId());
        if (handshake == null) {
            throw new IllegalArgumentException(
                    "Response has no matching pending offer"
            );
        }

        if (!handshake.recipientId.equals(senderId)
                || !handshake.initiatorId.equals(response.recipientId())) {
            throw new IllegalArgumentException(
                    "Response participants do not match the pending offer"
            );
        }

        Player initiator =
                identities.findOnlinePlayerById(handshake.initiatorId);
        if (initiator == null || !initiator.isOnline()) {
            pending.remove(response.sessionId());
            throw new IllegalArgumentException(
                    "Handshake initiator is no longer online"
            );
        }

        // Consume before sending so a repeated response cannot be relayed.
        sessionStore.register(
                response.sessionId(),
                handshake.initiatorId,
                handshake.recipientId,
                System.currentTimeMillis() + 60 * 60 * 1000L
        );
        pending.remove(response.sessionId());
        send(initiator, NetworkPayloads.encodeResponse(response));
    }

    public synchronized void removePlayer(Player player) {
        Objects.requireNonNull(player, "player");
        String playerId = identities.idFor(player);
        pending.entrySet().removeIf(entry ->
                entry.getValue().initiatorId.equals(playerId)
                        || entry.getValue().recipientId.equals(playerId));
    }

    public synchronized void clear() {
        pending.clear();
    }

    private void removeExpired() {
        long now = System.currentTimeMillis();
        Iterator<PendingHandshake> iterator = pending.values().iterator();

        while (iterator.hasNext()) {
            if (iterator.next().expiresAtMillis <= now) {
                iterator.remove();
            }
        }
    }

    private void send(Player recipient, byte[] bytes) {
        recipient.sendPluginMessage(
                plugin,
                ProtocolConstants.CHANNEL_ID,
                bytes
        );
    }

    private static final class PendingHandshake {
        private final String initiatorId;
        private final String recipientId;
        private final long expiresAtMillis;

        private PendingHandshake(
                String initiatorId,
                String recipientId,
                long expiresAtMillis
        ) {
            this.initiatorId = initiatorId;
            this.recipientId = recipientId;
            this.expiresAtMillis = expiresAtMillis;
        }
    }
}

