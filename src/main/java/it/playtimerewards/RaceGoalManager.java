package it.playtimerewards;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.block.data.Ageable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

final class RaceGoalManager implements Listener {
    private static final List<Integer> DEFAULT_THRESHOLDS = List.of(100, 300, 500, 800, 1000);
    private static final Set<Material> SIMPLE_CROPS = Set.of(
            Material.MELON,
            Material.PUMPKIN,
            Material.SUGAR_CANE,
            Material.CACTUS,
            Material.BAMBOO
    );

    private final JavaPlugin plugin;
    private final RaceManager raceManager;
    private final Economy economy;
    private final File file;
    private final Map<UUID, EnumMap<PlayerRace, Integer>> progress = new HashMap<>();
    private final Map<UUID, BossBar> bossBars = new HashMap<>();
    private BukkitTask refreshTask;

    RaceGoalManager(JavaPlugin plugin, RaceManager raceManager, Economy economy) {
        this.plugin = plugin;
        this.raceManager = raceManager;
        this.economy = economy;
        this.file = new File(plugin.getDataFolder(), "race-goals.yml");
        installDefaults();
        load();
        startRefreshTask();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTask(plugin, () -> refreshBossBar(event.getPlayer()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        removeBossBar(event.getPlayer().getUniqueId());
        save();
    }

    @EventHandler(ignoreCancelled = true)
    public void onMinerOrFarmerProgress(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!raceManager.isRaceEnabled(player)) return;

        PlayerRace race = raceManager.getRace(player.getUniqueId());
        Material material = event.getBlock().getType();

        if (race == PlayerRace.MINER
                && plugin.getConfig().getDouble("races.rewards.miner." + material.name(), 0.0D) > 0.0D) {
            addProgress(player, PlayerRace.MINER, 1);
            return;
        }

        if (race == PlayerRace.CONTADINO && isHarvestable(event)) {
            addProgress(player, PlayerRace.CONTADINO, 1);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onShieldBlock(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player defender)) return;
        if (!raceManager.isRaceEnabled(defender)) return;
        if (raceManager.getRace(defender.getUniqueId()) != PlayerRace.SCUDO) return;
        if (!defender.isBlocking()) return;
        if (!holdsShield(defender)) return;
        addProgress(defender, PlayerRace.SCUDO, 1);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMonsterKill(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Monster)) return;
        Player killer = event.getEntity().getKiller();
        if (killer == null || !raceManager.isRaceEnabled(killer)) return;
        if (raceManager.getRace(killer.getUniqueId()) != PlayerRace.SPADA) return;
        addProgress(killer, PlayerRace.SPADA, 1);
    }

    private void addProgress(Player player, PlayerRace race, int amount) {
        UUID uuid = player.getUniqueId();
        EnumMap<PlayerRace, Integer> playerProgress = progress.computeIfAbsent(uuid,
                ignored -> new EnumMap<>(PlayerRace.class));
        int oldValue = Math.max(0, playerProgress.getOrDefault(race, 0));
        int newValue = oldValue + Math.max(0, amount);

        int reachedThreshold = reachedThreshold(oldValue, newValue);
        if (reachedThreshold > 0) {
            rewardObjective(player, race, reachedThreshold);
            if (reachedThreshold >= maximumThreshold()) {
                newValue = 0;
                player.sendMessage("§aHai completato tutti gli obiettivi §e" + race.displayName()
                        + "§a! Il percorso riparte dal primo obiettivo.");
            }
        }

        playerProgress.put(race, newValue);
        refreshBossBar(player);
        save();
    }

    private int reachedThreshold(int oldValue, int newValue) {
        for (int threshold : thresholds()) {
            if (oldValue < threshold && newValue >= threshold) return threshold;
        }
        return -1;
    }

    private void rewardObjective(Player player, PlayerRace race, int threshold) {
        double reward = objectiveReward(race, threshold);
        if (reward <= 0.0D) return;
        if (economy == null || !economy.depositPlayer(player, reward).transactionSuccess()) {
            plugin.getLogger().warning("Impossibile pagare l'obiettivo razza di " + player.getName());
            return;
        }
        player.sendMessage("§6§lOBIETTIVO COMPLETATO! §e" + threshold + " " + unitName(race)
                + " §7- §a+" + format(reward) + "€");
    }

    private void refreshBossBar(Player player) {
        if (!player.isOnline()) return;
        if (!raceManager.isRaceEnabled(player)) {
            removeBossBar(player.getUniqueId());
            return;
        }

        PlayerRace race = raceManager.getRace(player.getUniqueId());
        int current = getProgress(player.getUniqueId(), race);
        int target = nextThreshold(current);
        double reward = objectiveReward(race, target);

        BossBar bar = bossBars.computeIfAbsent(player.getUniqueId(), ignored ->
                Bukkit.createBossBar("", colorFor(race), BarStyle.SOLID, new BarFlag[0]));
        bar.setColor(colorFor(race));
        bar.setStyle(BarStyle.SOLID);
        bar.setTitle("§f" + race.displayName() + " §8• §e" + current + "§7/§e" + target
                + " §8• §a" + format(reward) + "€");
        bar.setProgress(Math.max(0.0D, Math.min(1.0D, (double) current / (double) target)));
        if (!bar.getPlayers().contains(player)) bar.addPlayer(player);
        bar.setVisible(true);
    }

    private int getProgress(UUID uuid, PlayerRace race) {
        EnumMap<PlayerRace, Integer> playerProgress = progress.get(uuid);
        return playerProgress == null ? 0 : Math.max(0, playerProgress.getOrDefault(race, 0));
    }

    private int nextThreshold(int current) {
        for (int threshold : thresholds()) {
            if (current < threshold) return threshold;
        }
        return maximumThreshold();
    }

    private int maximumThreshold() {
        List<Integer> thresholds = thresholds();
        return thresholds.get(thresholds.size() - 1);
    }

    private List<Integer> thresholds() {
        List<Integer> configured = plugin.getConfig().getIntegerList("races.objectives.thresholds").stream()
                .filter(value -> value != null && value > 0)
                .distinct()
                .sorted()
                .toList();
        return configured.isEmpty() ? DEFAULT_THRESHOLDS : configured;
    }

    private double objectiveReward(PlayerRace race, int threshold) {
        return plugin.getConfig().getDouble(
                "races.objectives.rewards." + race.name().toLowerCase(Locale.ROOT) + "." + threshold,
                defaultReward(race, threshold));
    }

    private double defaultReward(PlayerRace race, int threshold) {
        double base = threshold * 10.0D;
        return race == PlayerRace.CONTADINO ? base * 1.25D : base;
    }

    private BarColor colorFor(PlayerRace race) {
        return switch (race) {
            case MINER -> BarColor.WHITE;
            case CONTADINO -> BarColor.GREEN;
            case SCUDO -> BarColor.WHITE;
            case SPADA -> BarColor.BLUE;
        };
    }

    private String unitName(PlayerRace race) {
        return switch (race) {
            case MINER -> "minerali scavati";
            case CONTADINO -> "crop raccolti";
            case SCUDO -> "parate";
            case SPADA -> "mostri uccisi";
        };
    }

    private boolean isHarvestable(BlockBreakEvent event) {
        Material material = event.getBlock().getType();
        if (SIMPLE_CROPS.contains(material)) return true;
        if (!(event.getBlock().getBlockData() instanceof Ageable ageable)) return false;
        return ageable.getAge() >= ageable.getMaximumAge();
    }

    private boolean holdsShield(Player player) {
        return player.getInventory().getItemInMainHand().getType() == Material.SHIELD
                || player.getInventory().getItemInOffHand().getType() == Material.SHIELD;
    }

    private void startRefreshTask() {
        refreshTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) refreshBossBar(player);
        }, 20L, 20L);
    }

    private void removeBossBar(UUID uuid) {
        BossBar bar = bossBars.remove(uuid);
        if (bar != null) {
            bar.removeAll();
            bar.setVisible(false);
        }
    }

    void shutdown() {
        if (refreshTask != null) refreshTask.cancel();
        for (BossBar bar : bossBars.values()) {
            bar.removeAll();
            bar.setVisible(false);
        }
        bossBars.clear();
        save();
    }

    private void installDefaults() {
        plugin.getConfig().addDefault("races.objectives.thresholds", DEFAULT_THRESHOLDS);
        for (PlayerRace race : PlayerRace.values()) {
            for (int threshold : DEFAULT_THRESHOLDS) {
                plugin.getConfig().addDefault(
                        "races.objectives.rewards." + race.name().toLowerCase(Locale.ROOT) + "." + threshold,
                        defaultReward(race, threshold));
            }
        }
        plugin.getConfig().options().copyDefaults(true);
        plugin.saveConfig();
    }

    private void load() {
        progress.clear();
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) return;

        for (String rawUuid : players.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(rawUuid);
                EnumMap<PlayerRace, Integer> values = new EnumMap<>(PlayerRace.class);
                for (PlayerRace race : PlayerRace.values()) {
                    values.put(race, Math.max(0, yaml.getInt(
                            "players." + rawUuid + "." + race.name().toLowerCase(Locale.ROOT), 0)));
                }
                progress.put(uuid, values);
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("UUID non valido ignorato in race-goals.yml: " + rawUuid);
            }
        }
    }

    void save() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) return;
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, EnumMap<PlayerRace, Integer>> entry : progress.entrySet()) {
            for (PlayerRace race : PlayerRace.values()) {
                yaml.set("players." + entry.getKey() + "." + race.name().toLowerCase(Locale.ROOT),
                        entry.getValue().getOrDefault(race, 0));
            }
        }
        try {
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Impossibile salvare race-goals.yml", exception);
        }
    }

    private String format(double value) {
        if (value == Math.rint(value)) return String.format(Locale.US, "%.0f", value);
        return String.format(Locale.US, "%.2f", value);
    }
}
