package net.mcems;

import java.util.Locale;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;

public final class SecureMessageCommand implements BasicCommand {
    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "status" -> sendStatus(sender);
            default -> {
                sender.sendMessage(
                        "Usage: /securemsg [help|status]"
                );
            }
        }
    }

    private static void sendHelp(CommandSender sender) {
        sender.sendMessage("Secure messaging demo commands:");
        sender.sendMessage("/securemsg status - show connection status");
        sender.sendMessage(
                "Use the Fabric client command /securemsg start <player> "
                        + "to initiate a handshake."
        );
    }

    private static void sendStatus(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(
                    "Secure messaging is available to connected players."
            );
            return;
        }

        sender.sendMessage(
                "Secure-message transport is provided by the server."
        );
        sender.sendMessage(
                "Start a handshake from the Fabric client with "
                        + "/securemsg start <player>."
        );
    }

    @Override
    public String permission() {
        return "mcems.securemsg";
    }
}
