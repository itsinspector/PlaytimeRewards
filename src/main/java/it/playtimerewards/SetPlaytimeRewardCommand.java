package it.playtimerewards;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

final class SetPlaytimeRewardCommand implements CommandExecutor {
    private final PlaytimeRewardsPlugin plugin;
    private final MessageService messages;

    SetPlaytimeRewardCommand(PlaytimeRewardsPlugin plugin, MessageService messages) {
        this.plugin = plugin;
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

        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (itemInHand.getType() == Material.AIR || itemInHand.getAmount() <= 0) {
            messages.send(player, "empty-hand");
            return true;
        }

        ItemStack storedReward = itemInHand.clone();
        plugin.getConfig().set("reward.item", storedReward);
        plugin.saveConfig();

        messages.send(player, "reward-set", Map.of(
                "amount", Integer.toString(storedReward.getAmount()),
                "item", RewardService.displayName(storedReward)
        ));
        return true;
    }
}
