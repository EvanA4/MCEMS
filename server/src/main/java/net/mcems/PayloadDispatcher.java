package net.mcems;


import java.util.Objects;
import net.mcems.PayloadListener.PayloadHandler;
import net.mcems.shared.HandshakeService;
import net.mcems.shared.MessageEnvelope;
import net.mcems.shared.NetworkPayloads;
import org.bukkit.entity.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PayloadDispatcher implements PayloadHandler {
    private final Logger logger = LoggerFactory.getLogger("PayloadDispatcher");
    private final HandshakeHandler handshakeHandler;
    private final PrivateMessageHandler privateMessageHandler;

    public PayloadDispatcher(
            HandshakeHandler handshakeHandler,
            PrivateMessageHandler privateMessageHandler
    ) {
        this.handshakeHandler =
                Objects.requireNonNull(handshakeHandler, "handshakeHandler");
        this.privateMessageHandler =
                Objects.requireNonNull(
                        privateMessageHandler,
                        "privateMessageHandler"
                );
    }

    @Override
    public void handle(Player sender, NetworkPayloads.Payload payload)
            throws Exception {
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(payload, "payload");

        if (payload instanceof NetworkPayloads.OfferPayload offerPayload) {
            logger.info("Processing an offer!");
            handshakeHandler.handleOffer(sender, offerPayload.offer());
        } else if (payload instanceof NetworkPayloads.ResponsePayload responsePayload) {
            logger.info("Processing a response!");
            handshakeHandler.handleResponse(
                    sender,
                    responsePayload.response()
            );
        } else if (payload instanceof NetworkPayloads.MessagePayload messagePayload) {
            logger.info("Processing a message!");
            privateMessageHandler.handleMessage(
                    sender,
                    messagePayload.envelope()
            );
        } else {
            // Defensive fallback in case the protocol gains a new payload type.
            throw new IllegalArgumentException(
                    "Unsupported payload type: " + payload.getClass().getName()
            );
        }
    }

    public interface HandshakeHandler {
        void handleOffer(
                Player sender,
                HandshakeService.Offer offer
        ) throws Exception;

        void handleResponse(
                Player sender,
                HandshakeService.Response response
        ) throws Exception;
    }

    @FunctionalInterface
    public interface PrivateMessageHandler {
        void handleMessage(
                Player sender,
                MessageEnvelope envelope
        ) throws Exception;
    }
}
