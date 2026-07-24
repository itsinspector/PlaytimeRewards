package it.playtimerewards;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

final class RewardService {
    private static final long MILLIS_PER_MINUTE = 60_000L;

    private final PlaytimeRewardsPlugin plugin;
    private final PlayerDataStore dataStore;
    private final MessageService messages;
    private final Economy economy;
    private final Map<UUID, Long> lastUpdates = new HashMap<>();

    private BukkitTask trackingTask;
    private BukkitTask autosaveTask;

    RewardService(
            PlaytimeRewardsPlugin plugin,
            PlayerDataStore dataStore,
            MessageService messages,
            Economy economy
    ) {
        this.plugin = plugin;
        this.dataStore = dataStore;
        this.messages = messages;
        this.economy = economy;
    }

    void start() {
        long now = System.currentTimeMillis();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            dataStore.getOrCreate(player.getUniqueId(), player.getName());
            lastUpdates.put(player.getUniqueId(), now);
        }

        trackingTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);

        long autosaveSeconds = Math.max(30L, plugin.getConfig().getLong("autosave-seconds", 300L));
        autosaveTask = plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                dataStore::save,
                autosaveSeconds * 20L,
                autosaveSeconds * 20L
        );
    }

    void shutdown() {
        long now = System.currentTimeMillis();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            updatePlayer(player, now, true);
        }
        lastUpdates.clear();

        if (trackingTask != null) {
            trackingTask.cancel();
        }
        if (autosaveTask != null) {
            autosaveTask.cancel();
        }
    }

    void startTracking(Player player) {
        dataStore.getOrCreate(player.getUniqueId(), player.getName());
        lastUpdates.put(player.getUniqueId(), System.currentTimeMillis());
    }

    void stopTracking(Player player) {
        updatePlayer(player, System.currentTimeMillis(), true);
        lastUpdates.remove(player.getUniqueId());
        dataStore.save();
    }

    long currentPlaytimeMillis(Player player) {
        updatePlayer(player, System.currentTimeMillis(), false);
        return dataStore.getOrCreate(player.getUniqueId(), player.getName()).totalPlaytimeMillis();
    }

    void markWelcomeGuiSeen(UUID uuid) {
        PlayerTimeData data = dataStore.getOrCreate(uuid, "Sconosciuto");
        data.markWelcomeGuiSeen();
    }

    PlayerTimeData getPlayerData(UUID uuid) {
        return dataStore.getOrCreate(uuid, "Sconosciuto");
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            updatePlayer(player, now, true);
        }
    }

    private void updatePlayer(Player player, long now, boolean processRewards) {
        UUID uuid = player.getUniqueId();
        PlayerTimeData data = dataStore.getOrCreate(uuid, player.getName());
        Long previousUpdate = lastUpdates.put(uuid, now);

        if (previousUpdate == null) {
            return;
        }

        long elapsedMillis = Math.max(0L, now - previousUpdate);
        data.addOnlineTime(elapsedMillis);

        if (!processRewards) {
            return;
        }

        long intervalMillis = getIntervalMillis();
        while (data.rewardProgressMillis() >= intervalMillis) {
            data.consumeRewardInterval(intervalMillis);
            giveReward(player);
        }
    }

    private long getIntervalMillis() {
        long minutes = Math.max(1L, plugin.getConfig().getLong("reward-interval-minutes", 30L));
        return minutes * MILLIS_PER_MINUTE;
    }

    private void giveReward(Player player) {
        String worldName = player.getWorld().getName();
        if (worldName.equals("arena-pvp-unranked") || worldName.equals("pillars") || worldName.equals("bedfight")) {
            return;
        }

        List<String> grantedParts = new ArrayList<>();

        double money = Math.max(0.0D, plugin.getConfig().getDouble("reward.money", 0.0D));
        if (money > 0.0D) {
            if (economy == null) {
                messages.send(player, "vault-unavailable");
            } else {
                EconomyResponse response = economy.depositPlayer(player, money);
                if (response.transactionSuccess()) {
                    grantedParts.add("&e" + economy.format(money));
                } else {
                    plugin.getLogger().warning(
                            "Errore Vault durante il pagamento a " + player.getName() + ": " + response.errorMessage
                    );
                    messages.send(player, "economy-error");
                }
            }
        }

        ItemStack configuredItem = plugin.getConfig().getItemStack("reward.item");
        if (configuredItem != null && configuredItem.getType() != Material.AIR && configuredItem.getAmount() > 0) {
            ItemStack rewardItem = configuredItem.clone();
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(rewardItem);

            if (!leftovers.isEmpty()) {
                if (plugin.getConfig().getBoolean("reward.drop-overflow-items", true)) {
                    leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
                    messages.send(player, "inventory-full");
                } else {
                    plugin.getLogger().warning(
                            "Inventario pieno per " + player.getName() + ": alcuni oggetti ricompensa non sono stati consegnati."
                    );
                }
            }

            grantedParts.add("&e" + configuredItem.getAmount() + "x " + readableMaterialName(configuredItem.getType()));
        }

        if (grantedParts.isEmpty()) {
            messages.send(player, "reward-not-configured");
            return;
        }

        long intervalMinutes = Math.max(1L, plugin.getConfig().getLong("reward-interval-minutes", 30L));
        messages.send(player, "reward-received", Map.of(
                "interval", Long.toString(intervalMinutes),
                "reward", String.join("&a + ", grantedParts)
        ));
    }

    static String readableMaterialName(Material material) {
        String[] words = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }
}
