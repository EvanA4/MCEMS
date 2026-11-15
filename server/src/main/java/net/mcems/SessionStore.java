package net.mcems;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class SessionStore {
    private static final int MAX_SESSIONS = 256;

    private final Map<UUID, Session> sessions = new HashMap<>();

    /**
     * Registers a session between two server-identified player IDs.
     */
    public synchronized void register(
            UUID sessionId,
            String initiatorId,
            String recipientId,
            long expiresAtEpochMillis
    ) {
        Objects.requireNonNull(sessionId, "sessionId");
        requirePlayerId(initiatorId, "initiatorId");
        requirePlayerId(recipientId, "recipientId");

        if (initiatorId.equals(recipientId)) {
            throw new IllegalArgumentException(
                    "Session participants must differ"
            );
        }
        if (expiresAtEpochMillis <= System.currentTimeMillis()) {
            throw new IllegalArgumentException(
                    "Session expiry must be in the future"
            );
        }

        removeExpired();

        if (!sessions.containsKey(sessionId)
                && sessions.size() >= MAX_SESSIONS) {
            throw new IllegalStateException("Session limit reached");
        }

        Session existing = sessions.get(sessionId);
        if (existing != null
                && (!existing.initiatorId.equals(initiatorId)
                || !existing.recipientId.equals(recipientId))) {
            throw new IllegalArgumentException(
                    "Session ID is already registered for other participants"
            );
        }

        sessions.put(
                sessionId,
                new Session(initiatorId, recipientId, expiresAtEpochMillis)
        );
    }

    /**
     * Returns true only if the session is active and both IDs are its
     * participants, in either direction.
     */
    public synchronized boolean allows(
            UUID sessionId,
            String senderId,
            String recipientId
    ) {
        Objects.requireNonNull(sessionId, "sessionId");
        if (senderId == null || recipientId == null) {
            return false;
        }

        Session session = getActive(sessionId);
        if (session == null) {
            return false;
        }

        return (session.initiatorId.equals(senderId)
                && session.recipientId.equals(recipientId))
                || (session.recipientId.equals(senderId)
                && session.initiatorId.equals(recipientId));
    }

    public synchronized void remove(UUID sessionId) {
        if (sessionId != null) {
            sessions.remove(sessionId);
        }
    }

    public synchronized void removePlayer(String playerId) {
        if (playerId == null) {
            return;
        }

        sessions.entrySet().removeIf(entry ->
                entry.getValue().initiatorId.equals(playerId)
                        || entry.getValue().recipientId.equals(playerId));
    }

    public synchronized void removeExpired() {
        long now = System.currentTimeMillis();
        Iterator<Session> iterator = sessions.values().iterator();

        while (iterator.hasNext()) {
            if (iterator.next().expiresAtEpochMillis <= now) {
                iterator.remove();
            }
        }
    }

    public synchronized void clear() {
        sessions.clear();
    }

    private Session getActive(UUID sessionId) {
        Session session = sessions.get(sessionId);
        if (session != null
                && session.expiresAtEpochMillis <= System.currentTimeMillis()) {
            sessions.remove(sessionId);
            return null;
        }
        return session;
    }

    private static void requirePlayerId(String playerId, String fieldName) {
        Objects.requireNonNull(playerId, fieldName);

        if (playerId.isBlank()
                || playerId.getBytes(java.nio.charset.StandardCharsets.UTF_8)
                .length > ProtocolConstants.MAX_ID_BYTES) {
            throw new IllegalArgumentException(
                    fieldName + " must be nonblank and at most "
                            + ProtocolConstants.MAX_ID_BYTES
                            + " UTF-8 bytes"
            );
        }
    }

    private static final class Session {
        private final String initiatorId;
        private final String recipientId;
        private final long expiresAtEpochMillis;

        private Session(
                String initiatorId,
                String recipientId,
                long expiresAtEpochMillis
        ) {
            this.initiatorId = initiatorId;
            this.recipientId = recipientId;
            this.expiresAtEpochMillis = expiresAtEpochMillis;
        }
    }
}
