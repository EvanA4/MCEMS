package net.mcems.client;

import java.util.Objects;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.SharedSuggestionProvider;
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

    private final SuggestionProvider<FabricClientCommandSource> playerSuggestions =
            (context, builder) -> {
                var connection = Minecraft.getInstance().getConnection();
                if (connection == null) {
                    return builder.buildFuture();
                }

                var names = connection.getListedOnlinePlayers().stream()
                        .map(entry -> entry.getProfile().name())
                        .toList();

                return SharedSuggestionProvider.suggest(names, builder);
            };

    public ChatCommandHandler(
            HandshakeStarter handshakeStarter,
            PrivateMessageSender messageSender) {
        this.handshakeStarter = Objects.requireNonNull(handshakeStarter, "handshakeStarter");
        this.messageSender = Objects.requireNonNull(messageSender, "messageSender");
    }

    public void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) ->
                dispatcher.register(
                        ClientCommands.literal("mcems")
                                .executes(context -> showHelp(context.getSource()))
                                .then(ClientCommands.literal("help")
                                        .executes(context -> showHelp(context.getSource())))
                                .then(ClientCommands.literal("start")
                                        .then(ClientCommands.argument(
                                                        "player",
                                                        StringArgumentType.word())
                                                .suggests(playerSuggestions)
                                                .executes(context -> {
                                                    String player = StringArgumentType.getString(
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
                                                .suggests(playerSuggestions)
                                                .then(ClientCommands.argument(
                                                                "message",
                                                                StringArgumentType.greedyString())
                                                        .executes(context -> {
                                                            String player = StringArgumentType.getString(
                                                                    context, "player");
                                                            String message = StringArgumentType.getString(
                                                                    context, "message");
                                                            return runAction(
                                                                    context.getSource(),
                                                                    () -> messageSender.send(player, message),
                                                                    ChatUi.genSentMessageComponent(
                                                                            player, message));
                                                        }))))));
    }

    private static int showHelp(FabricClientCommandSource source) {
        source.sendFeedback(Component.literal("MCEMS commands:"));
        source.sendFeedback(Component.literal("/mcems start <player> — start a secure session"));
        source.sendFeedback(Component.literal("/mcems send <player> <message> — send a secure message"));
        source.sendFeedback(Component.literal("/mcems help — show this help"));
        return 1;
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
