package net.mcems.mitm;

import net.mcems.shared.CryptoConfig;

import java.util.*;

public final class SessionManager {
    private static final int MAX_SESSIONS = 256;

    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<String, UUID> sessionsByPeer = new HashMap<>();

    public synchronized UUID getSessionId(String peerId) {
        return sessionsByPeer.get(peerId);
    }

    /**
     * Adds an in-memory session. expiresAtEpochMillis is an absolute
     * System.currentTimeMillis()-style timestamp.
     */
    public synchronized void addSession(
            UUID sessionId,
            String peerId,
            byte[] aesKey,
            long expiresAtEpochMillis) {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(peerId, "peerId");
        Objects.requireNonNull(aesKey, "aesKey");

        if (peerId.isBlank()) {
            throw new IllegalArgumentException("peerId must not be blank");
        }
        if (aesKey.length != CryptoConfig.AES_KEY_BYTES) {
            throw new IllegalArgumentException("Expected a 256-bit AES key");
        }
        if (expiresAtEpochMillis <= System.currentTimeMillis()) {
            throw new IllegalArgumentException("Session expiry must be in the future");
        }
        if (!sessions.containsKey(sessionId) && sessions.size() >= MAX_SESSIONS) {
            throw new IllegalStateException("Session limit reached");
        }

        sessionsByPeer.put(peerId, sessionId);

        Session old = sessions.put(sessionId,
                new Session(sessionId, peerId, aesKey, expiresAtEpochMillis));
        if (old != null) {
            old.clearKey();
        }
    }

    /** Returns a defensive copy of the session key, or null if absent/expired. */
    public synchronized byte[] getKey(UUID sessionId) {
        Session session = getActiveSession(sessionId);
        return session == null ? null : session.key.clone();
    }

    /**
     * Reserves and returns the next outgoing counter, starting at zero.
     * Call once per message and place the returned value in its envelope.
     */
    public synchronized long nextOutgoingCounter(UUID sessionId) {
        Session session = requireActiveSession(sessionId);
        if (session.nextOutgoingCounter == Long.MAX_VALUE) {
            throw new IllegalStateException("Outgoing counter exhausted");
        }
        return session.nextOutgoingCounter++;
    }

    /**
     * Accepts only a strictly increasing incoming counter.
     * Returns false for an unknown/expired session, wrong peer, or replay.
     */
    public synchronized boolean acceptIncoming(
            UUID sessionId, String senderId, long counter) {
        if (senderId == null || counter < 0) {
            return false;
        }

        Session session = getActiveSession(sessionId);
        if (session == null || !session.peerId.equals(senderId)
                || counter <= session.lastIncomingCounter) {
            return false;
        }

        session.lastIncomingCounter = counter;
        return true;
    }

    public synchronized void removeSessionsForPeer(String peerId) {
        Objects.requireNonNull(peerId, "peerId");
        Iterator<Session> iterator = sessions.values().iterator();
        while (iterator.hasNext()) {
            Session session = iterator.next();
            if (session.peerId.equals(peerId)) {
                session.clearKey();
                iterator.remove();
            }
        }
        sessionsByPeer.remove(peerId);
    }

    public synchronized void clear() {
        for (Session session : sessions.values()) {
            session.clearKey();
        }
        sessions.clear();
        sessionsByPeer.clear();
    }


    private Session requireActiveSession(UUID sessionId) {
        Session session = getActiveSession(sessionId);
        if (session == null) {
            throw new IllegalStateException("Session is absent or expired");
        }
        return session;
    }

    private Session getActiveSession(UUID sessionId) {
        Objects.requireNonNull(sessionId, "sessionId");
        Session session = sessions.get(sessionId);
        if (session != null
                && session.expiresAtEpochMillis <= System.currentTimeMillis()) {
            sessions.remove(sessionId);
            session.clearKey();
            return null;
        }
        return session;
    }

    private static final class Session {
        private final UUID sessionId;
        private final String peerId;
        private final byte[] key;
        private final long expiresAtEpochMillis;

        private long nextOutgoingCounter = 0;
        private long lastIncomingCounter = -1;

        private Session(UUID sessionId, String peerId, byte[] key, long expiry) {
            this.sessionId = sessionId;
            this.peerId = peerId;
            this.key = key.clone();
            this.expiresAtEpochMillis = expiry;
        }

        private void clearKey() {
            java.util.Arrays.fill(key, (byte) 0);
        }
    }
}
