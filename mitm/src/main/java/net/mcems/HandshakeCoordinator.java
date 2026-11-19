package net.mcems;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.*;

import net.mcems.mitm.SessionManager;
import net.mcems.shared.HandshakeService;
import net.mcems.shared.NetworkPayloads;
import net.mcems.shared.ProtocolConstants;
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
    private final SessionManager sessionManager;
    private final HandshakeService handshakeService;

    private final Map<String, UUID> sessionsByPeer = new HashMap<>();

    public HandshakeCoordinator(
            Plugin plugin,
            PlayerIdentityService identities,
            SessionStore sessionStore,
            SessionManager sessionManager,
            HandshakeService handshakeService
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.identities = Objects.requireNonNull(identities, "identities");
        this.sessionStore = sessionStore;
        this.sessionManager = sessionManager;
        this.handshakeService = handshakeService;
    }

    @Override
    public synchronized void handleOffer(
            Player sender,
            HandshakeService.Offer offer
    ) throws IOException, GeneralSecurityException {
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

        // Accept offer and return response to sender
        HandshakeService.AcceptedHandshake accepted =
                handshakeService.acceptOffer(offer);
        HandshakeService.Response response = accepted.response();
        send(sender, NetworkPayloads.encodeResponse(response));
        installSession(
                offer.senderId(),
                offer.sessionId(),
                accepted.sessionKey());
        Arrays.fill(accepted.sessionKey(), (byte) 0);

        sessionStore.register(
                offer.sessionId(),
                senderId,
                offer.recipientId(),
                System.currentTimeMillis() + 60 * 60 * 1000L
        );

        // Create new offer and send it to recipient
        HandshakeService.Offer toSend =
            handshakeService.createOffer(senderId, offer.recipientId());

        PendingHandshake old = pending.put(
                toSend.sessionId(),
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
            send(recipient, NetworkPayloads.encodeOffer(toSend));
        } catch (IOException | RuntimeException e) {
            pending.remove(offer.sessionId());
            throw e;
        }
    }

    @Override
    public synchronized void handleResponse(
            Player sender,
            HandshakeService.Response response
    ) throws IOException, GeneralSecurityException {
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

        // Swallow message
        //send(initiator, NetworkPayloads.encodeResponse(response));

        // Accept offer and return response to sender
        HandshakeService.EstablishedSession established =
                handshakeService.complete(response);
        installSession(
                established.peerId(),
                established.sessionId(),
                established.sessionKey());
        Arrays.fill(established.sessionKey(), (byte) 0);
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

    // MITM functions

    private void installSession(String peerId, UUID sessionId, byte[] key) {
        sessionManager.addSession(
                sessionId,
                peerId,
                key,
                System.currentTimeMillis() + 60 * 60 * 1000L);
        sessionsByPeer.put(peerId, sessionId);
    }
}

