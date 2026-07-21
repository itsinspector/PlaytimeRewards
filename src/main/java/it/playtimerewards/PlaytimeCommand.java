package it.playtimerewards;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

final class PlaytimeCommand implements CommandExecutor {
    private final RewardService rewardService;
    private final MessageService messages;

    PlaytimeCommand(RewardService rewardService, MessageService messages) {
        this.rewardService = rewardService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "player-only");
            return true;
        }

        long playtimeMillis = rewardService.currentPlaytimeMillis(player);
        messages.send(player, "playtime", Map.of("time", formatDuration(playtimeMillis)));
        return true;
    }

    private static String formatDuration(long millis) {
        long totalSeconds = Math.max(0L, millis / 1_000L);
        long days = totalSeconds / 86_400L;
        long hours = (totalSeconds % 86_400L) / 3_600L;
        long minutes = (totalSeconds % 3_600L) / 60L;
        long seconds = totalSeconds % 60L;

        if (days > 0L) {
            return "%dg %02dh %02dm %02ds".formatted(days, hours, minutes, seconds);
        }
        if (hours > 0L) {
            return "%dh %02dm %02ds".formatted(hours, minutes, seconds);
        }
        return "%dm %02ds".formatted(minutes, seconds);
    }
}
