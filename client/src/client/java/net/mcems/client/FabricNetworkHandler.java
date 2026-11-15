package net.mcems.client;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

import net.mcems.NetworkPayloads;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

public final class FabricNetworkHandler {
    private FabricNetworkHandler() {}

    /**
     * Call once from the common initializer,
     * on both client and server.
     */
    public static void registerPayloadTypes() {
        PayloadTypeRegistry.serverboundPlay().register(
                WirePayload.TYPE, WirePayload.CODEC);

        PayloadTypeRegistry.clientboundPlay().register(
                WirePayload.TYPE, WirePayload.CODEC);
    }

    /**
     * Call from the client initializer.
     */
    public static void registerClientReceiver(
            Consumer<NetworkPayloads.Payload> onPayload,
            Consumer<Exception> onError) {

        Objects.requireNonNull(onPayload, "onPayload");
        Objects.requireNonNull(onError, "onError");

        ClientPlayNetworking.registerGlobalReceiver(
                WirePayload.TYPE,
                (wirePayload, context) -> {
                    context.client().execute(() -> {
                        try {
                            NetworkPayloads.Payload decoded =
                                    NetworkPayloads.decode(wirePayload.data());

                            onPayload.accept(decoded);
                        } catch (IOException | RuntimeException e) {
                            onError.accept(e);
                        }
                    });
                });
    }

    /**
     * Sends an already encoded payload to the server.
     */
    public static void sendToServer(byte[] encodedPayload) {
        ClientPlayNetworking.send(new WirePayload(encodedPayload));
    }

    public record WirePayload(byte[] data)
            implements CustomPacketPayload {

        public static final Type<WirePayload> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(
                        "mcems", "secure_message"));

        public static final StreamCodec<
                RegistryFriendlyByteBuf, WirePayload> CODEC =
                StreamCodec.of(
                        (buf, payload) ->
                                buf.writeByteArray(payload.data),
                        buf -> new WirePayload(
                                buf.readByteArray(32 * 1024))
                );

        public WirePayload {
            Objects.requireNonNull(data, "data");

            if (data.length == 0 || data.length > 32 * 1024) {
                throw new IllegalArgumentException(
                        "Invalid payload size");
            }

            data = data.clone();
        }

        @Override
        public @NonNull Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        @Override
        public byte[] data() {
            return data.clone();
        }
    }
}
