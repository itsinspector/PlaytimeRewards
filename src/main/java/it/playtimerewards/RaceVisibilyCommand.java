package it.playtimerewards;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

final class RaceVisibilityCommand implements CommandExecutor {
    private final RaceManager raceManager;

    RaceVisibilityCommand(RaceManager raceManager) {
        this.raceManager = raceManager;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Questo comando può essere usato solo da un giocatore.");
            return true;
        }

        if (!raceManager.canToggleRacePrefix(player)) {
            player.sendMessage("§cQuesta opzione è disponibile solo per VIP e VIP+.");
            return true;
        }

        boolean raceVisible = raceManager.toggleRacePrefix(player);
        player.sendMessage(
                raceVisible
                        ? "§aPrefisso visibile impostato su: §eRAZZA§a."
                        : "§aPrefisso visibile impostato su: §6VIP§a."
        );
        return true;
    }
}
