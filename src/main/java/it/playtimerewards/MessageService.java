package it.playtimerewards;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

final class MessageService {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final JavaPlugin plugin;

    MessageService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    void send(CommandSender sender, String messageKey) {
        send(sender, messageKey, Map.of());
    }

    void send(CommandSender sender, String messageKey, Map<String, String> placeholders) {
        String prefix = plugin.getConfig().getString("messages.prefix", "&6[PlaytimeRewards] &r");
        String message = plugin.getConfig().getString("messages." + messageKey, "&cMessaggio mancante: " + messageKey);

        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        }

        sender.sendMessage(deserialize(prefix + message));
    }

    String getMessage(String messageKey) {
        return plugin.getConfig().getString("messages." + messageKey, "&cMessaggio mancante: " + messageKey);
    }

    Component deserialize(String text) {
        return LEGACY.deserialize(text == null ? "" : text);
    }
}
