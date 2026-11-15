package net.mcems;

import java.io.IOException;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

public final class PayloadListener implements PluginMessageListener {
    private final PayloadHandler handler;
    private final Logger logger;

    public PayloadListener(PayloadHandler handler, Logger logger) {
        this.handler = Objects.requireNonNull(handler, "handler");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public void onPluginMessageReceived(
            String channel,
            Player player,
            byte[] message
    ) {
        if (!ProtocolConstants.CHANNEL_ID.equals(channel)) {
            return;
        }

        if (message == null
                || message.length == 0
                || message.length > ProtocolConstants.MAX_PAYLOAD_BYTES) {
            logger.warning("Rejected invalid payload size from " + player.getName());
            return;
        }

        try {
            NetworkPayloads.Payload payload = NetworkPayloads.decode(message);
            handler.handle(player, payload);
        } catch (IOException | RuntimeException e) {
            logger.log(
                    Level.WARNING,
                    "Rejected malformed secure-message payload from "
                            + player.getName(),
                    e
            );
        } catch (Exception e) {
            logger.log(
                    Level.SEVERE,
                    "Failed to handle secure-message payload from "
                            + player.getName(),
                    e
            );
        }
    }

    @FunctionalInterface
    public interface PayloadHandler {
        void handle(Player sender, NetworkPayloads.Payload payload)
                throws Exception;
    }
}
