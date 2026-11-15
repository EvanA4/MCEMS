package net.mcems;


import java.util.Objects;
import net.mcems.PayloadListener.PayloadHandler;
import org.bukkit.entity.Player;

public final class PayloadDispatcher implements PayloadHandler {
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
            handshakeHandler.handleOffer(sender, offerPayload.offer());
        } else if (payload instanceof NetworkPayloads.ResponsePayload responsePayload) {
            handshakeHandler.handleResponse(
                    sender,
                    responsePayload.response()
            );
        } else if (payload instanceof NetworkPayloads.MessagePayload messagePayload) {
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
                net.mcems.HandshakeService.Offer offer
        ) throws Exception;

        void handleResponse(
                Player sender,
                net.mcems.HandshakeService.Response response
        ) throws Exception;
    }

    @FunctionalInterface
    public interface PrivateMessageHandler {
        void handleMessage(
                Player sender,
                net.mcems.MessageEnvelope envelope
        ) throws Exception;
    }
}
