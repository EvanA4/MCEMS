package net.mcems;

import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class SessionCleanupListener implements Listener, AutoCloseable {
    private static final long CLEANUP_INTERVAL_TICKS = 20L * 60L;

    private final PlayerIdentityService identities;
    private final SessionStore sessionStore;
    private final HandshakeCoordinator handshakeCoordinator;
    private final PrivateMessageService privateMessageService;
    private final BukkitTask cleanupTask;

    public SessionCleanupListener(
            Plugin plugin,
            PlayerIdentityService identities,
            SessionStore sessionStore,
            HandshakeCoordinator handshakeCoordinator,
            PrivateMessageService privateMessageService
    ) {
        Objects.requireNonNull(plugin, "plugin");

        this.identities =
                Objects.requireNonNull(identities, "identities");
        this.sessionStore =
                Objects.requireNonNull(sessionStore, "sessionStore");
        this.handshakeCoordinator =
                Objects.requireNonNull(
                        handshakeCoordinator,
                        "handshakeCoordinator"
                );
        this.privateMessageService =
                Objects.requireNonNull(
                        privateMessageService,
                        "privateMessageService"
                );

        this.cleanupTask = plugin.getServer().getScheduler()
                .runTaskTimer(
                        plugin,
                        sessionStore::removeExpired,
                        CLEANUP_INTERVAL_TICKS,
                        CLEANUP_INTERVAL_TICKS
                );
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        String playerId = identities.idFor(player);

        handshakeCoordinator.removePlayer(player);
        sessionStore.removePlayer(playerId);
        privateMessageService.removePlayer(player);
    }

    @Override
    public void close() {
        cleanupTask.cancel();
    }
}
