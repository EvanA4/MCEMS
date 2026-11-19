package net.mcems;

import java.util.Objects;
import java.util.UUID;

import net.mcems.shared.ProtocolConstants;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class PlayerIdentityService {

    /**
     * Returns the canonical protocol ID for a connected player.
     * Use this for sender and recipient IDs in handshake and message payloads.
     */
    public String idFor(Player player) {
        Objects.requireNonNull(player, "player");
        return player.getUniqueId().toString();
    }

    /**
     * Resolves a UUID-based protocol ID to a currently online player.
     * Returns null if the ID is malformed or the player is offline.
     */
    public Player findOnlinePlayerById(String playerId) {
        if (playerId == null || playerId.isBlank()
                || playerId.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                > ProtocolConstants.MAX_ID_BYTES) {
            return null;
        }

        try {
            UUID uuid = UUID.fromString(playerId);
            return Bukkit.getPlayer(uuid);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Resolves an exact player name to an online player.
     * Use this for a command argument, not as the identity stored in a session.
     */
    public Player findOnlinePlayerByExactName(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return null;
        }

        return Bukkit.getPlayerExact(playerName);
    }
}
