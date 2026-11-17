
package net.mcems.client;

import java.util.Objects;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

public final class ChatUi {

    private static final Component PREFIX =
            Component.literal("[")
                    .withStyle(ChatFormatting.GREEN)
                .append(Component.literal("MCEMS")
                        .withStyle(ChatFormatting.DARK_GREEN))
                .append(Component.literal("] ")
                        .withStyle(ChatFormatting.GREEN));


    private ChatUi() {
        // Utility class.
    }

    public static Component genSentMessageComponent(String to, String message) {
        return PREFIX.copy()
                .append(Component.literal("You -> " + to)
                        .withStyle(ChatFormatting.GOLD))
                .append(Component.literal(": ")
                        .withStyle(ChatFormatting.GRAY))
                .append(Component.literal(message)
                        .withStyle(ChatFormatting.WHITE));
    }

    public static Component genSentHandshakeComponent(String to) {
        return PREFIX.copy()
                .append(Component.literal("Sent handshake request to " + to + ".")
                        .withStyle(ChatFormatting.GRAY));
    }

    public static Component genErrorMessage(String message) {
        return Component.literal("[")
                        .withStyle(ChatFormatting.DARK_RED)
                .append(Component.literal("MCEMS")
                        .withStyle(ChatFormatting.RED))
                .append(Component.literal("] ")
                        .withStyle(ChatFormatting.DARK_RED))
                .append(Component.literal("Error: " + message)
                        .withStyle(ChatFormatting.RED));
    }

    /**
     * Displays a decrypted message locally.
     * Does not send anything to the server.
     */
    public static void showPrivateMessage(String senderName, String message) {
        Objects.requireNonNull(senderName, "senderName");
        Objects.requireNonNull(message, "message");

        Component line = PREFIX.copy()
                .append(Component.literal(senderName)
                        .withStyle(ChatFormatting.GOLD))
                .append(Component.literal(": ")
                        .withStyle(ChatFormatting.GRAY))
                .append(Component.literal(message)
                        .withStyle(ChatFormatting.WHITE));

        show(line);
    }

    public static void showStatus(String message) {
        Objects.requireNonNull(message, "message");

        Component line = PREFIX.copy()
                .append(Component.literal(message)
                        .withStyle(ChatFormatting.GRAY));

        show(line);
    }

    public static void showError(String message) {
        Objects.requireNonNull(message, "message");

        Component line = PREFIX.copy()
                .append(Component.literal(message)
                        .withStyle(ChatFormatting.RED));

        show(line);
    }

    private static void show(Component line) {
        Minecraft client = Minecraft.getInstance();

        client.execute(() -> {
            if (client.gui != null && client.gui.hud != null) {
                client.gui.hud.getChat().addClientSystemMessage(line);
            }
        });
    }
}
