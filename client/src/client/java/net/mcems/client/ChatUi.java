
package net.mcems.client;

import java.util.Objects;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

public final class ChatUi {

    private static final String PREFIX = "[Secure] ";

    private ChatUi() {
        // Utility class.
    }

    /**
     * Displays a decrypted message locally.
     * Does not send anything to the server.
     */
    public static void showPrivateMessage(String senderName, String message) {
        Objects.requireNonNull(senderName, "senderName");
        Objects.requireNonNull(message, "message");

        Component line = Component.literal(PREFIX)
                .withStyle(ChatFormatting.AQUA)
                .append(Component.literal(senderName)
                        .withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(": ")
                        .withStyle(ChatFormatting.GRAY))
                .append(Component.literal(message)
                        .withStyle(ChatFormatting.WHITE));

        show(line);
    }

    public static void showStatus(String message) {
        Objects.requireNonNull(message, "message");

        Component line = Component.literal(PREFIX)
                .withStyle(ChatFormatting.AQUA)
                .append(Component.literal(message)
                        .withStyle(ChatFormatting.GRAY));

        show(line);
    }

    public static void showError(String message) {
        Objects.requireNonNull(message, "message");

        Component line = Component.literal(PREFIX)
                .withStyle(ChatFormatting.AQUA)
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
