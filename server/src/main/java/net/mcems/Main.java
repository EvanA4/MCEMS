package net.mcems;

import net.mcems.shared.ProtocolConstants;
import org.bukkit.plugin.java.JavaPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Main extends JavaPlugin {
    private final Logger logger = LoggerFactory.getLogger("Main");

    private PlayerIdentityService identities;
    private SessionStore sessionStore;
    private HandshakeCoordinator handshakeCoordinator;
    private PrivateMessageService privateMessageService;
    private SessionCleanupListener sessionCleanupListener;

    @Override
    public void onEnable() {
        try {
            identities = new PlayerIdentityService();
            sessionStore = new SessionStore();

            handshakeCoordinator =
                    new HandshakeCoordinator(this, identities, sessionStore);

            privateMessageService = new PrivateMessageService(
                    this,
                    identities,
                    sessionStore
            );

            PayloadDispatcher dispatcher = new PayloadDispatcher(
                    handshakeCoordinator,
                    privateMessageService
            );

            PayloadListener payloadListener =
                    new PayloadListener(dispatcher, getLogger());

            getServer().getMessenger().registerIncomingPluginChannel(
                    this,
                    ProtocolConstants.CHANNEL_ID,
                    payloadListener
            );
            getServer().getMessenger().registerOutgoingPluginChannel(
                    this,
                    ProtocolConstants.CHANNEL_ID
            );

            sessionCleanupListener = new SessionCleanupListener(
                    this,
                    identities,
                    sessionStore,
                    handshakeCoordinator,
                    privateMessageService
            );
            getServer().getPluginManager().registerEvents(
                    sessionCleanupListener,
                    this
            );

            getLogger().info("MCEMS secure-message demo enabled.");
        } catch (RuntimeException e) {
            getLogger().severe(
                    "Failed to enable MCEMS secure-message demo: "
                            + e.getMessage()
            );
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (sessionCleanupListener != null) {
            sessionCleanupListener.close();
        }

        if (sessionStore != null) {
            sessionStore.clear();
        }
        if (handshakeCoordinator != null) {
            handshakeCoordinator.clear();
        }
        if (privateMessageService != null) {
            privateMessageService.clear();
        }

        getServer().getMessenger().unregisterIncomingPluginChannel(this);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);

        getLogger().info("MCEMS secure-message demo disabled.");
    }
}

