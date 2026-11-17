
package net.mcems.client;

import java.util.Objects;

import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import net.minecraft.network.chat.Component;

public final class ChatCommandHandler {

    @FunctionalInterface
    public interface HandshakeStarter {
        void start(String playerName) throws Exception;
    }

    @FunctionalInterface
    public interface PrivateMessageSender {
        void send(String playerName, String message) throws Exception;
    }

    private final HandshakeStarter handshakeStarter;
    private final PrivateMessageSender messageSender;

    public ChatCommandHandler(
            HandshakeStarter handshakeStarter,
            PrivateMessageSender messageSender) {

        this.handshakeStarter =
                Objects.requireNonNull(handshakeStarter, "handshakeStarter");

        this.messageSender =
                Objects.requireNonNull(messageSender, "messageSender");
    }

    /**
     * Call once from your client initializer.
     */
    public void register() {
        ClientCommandRegistrationCallback.EVENT.register(
                (dispatcher, buildContext) -> {

                    dispatcher.register(
                            ClientCommands.literal("securemsg")

                                    .then(ClientCommands.literal("start")
                                            .then(ClientCommands.argument(
                                                            "player",
                                                            StringArgumentType.word())
                                                    .executes(context -> {
                                                        String player =
                                                                StringArgumentType.getString(
                                                                        context, "player");

                                                        return runAction(
                                                                context.getSource(),
                                                                () -> handshakeStarter.start(player),
                                                                    ChatUi.genSentHandshakeComponent(player));
                                                    })))

                                    .then(ClientCommands.literal("send")
                                            .then(ClientCommands.argument(
                                                            "player",
                                                            StringArgumentType.word())
                                                    .then(ClientCommands.argument(
                                                                    "message",
                                                                    StringArgumentType.greedyString())
                                                            .executes(context -> {
                                                                String player =
                                                                        StringArgumentType.getString(
                                                                                context, "player");

                                                                String message =
                                                                        StringArgumentType.getString(
                                                                                context, "message");

                                                                return runAction(
                                                                        context.getSource(),
                                                                        () -> messageSender.send(
                                                                                player, message),
                                                                            ChatUi.genSentMessageComponent(player, message));
                                                            }))))
                    );
                });
    }

    private static int runAction(
            FabricClientCommandSource source,
            ThrowingAction action,
            Component successMessage) {

        try {
            action.run();

            source.sendFeedback(successMessage);

            return 1;

        } catch (Exception e) {
            source.sendError(ChatUi.genErrorMessage(safeMessage(e)));

            return 0;
        }
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();

        return message == null || message.isBlank()
                ? e.getClass().getSimpleName()
                : message;
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run() throws Exception;
    }
}
