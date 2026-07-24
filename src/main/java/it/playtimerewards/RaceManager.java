package it.playtimerewards;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.PrefixNode;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.block.data.Ageable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

final class RaceManager implements Listener {
    private static final Set<Material> SIMPLE_HARVESTABLES = Set.of(
            Material.MELON,
            Material.PUMPKIN,
            Material.SUGAR_CANE,
            Material.CACTUS,
            Material.BAMBOO
    );

    private final JavaPlugin plugin;
    private final Economy economy;
    private final LuckPerms luckPerms;
    private final File file;
    private final Map<UUID, PlayerRace> races = new HashMap<>();
    private final Map<UUID, Boolean> freeChanges = new HashMap<>();
    private final Map<UUID, Integer> spadaCriticalCounters = new HashMap<>();

    RaceManager(JavaPlugin plugin, Economy economy, LuckPerms luckPerms) {
        this.plugin = plugin;
        this.economy = economy;
        this.luckPerms = luckPerms;
        this.file = new File(plugin.getDataFolder(), "races.yml");
        installConfigDefaults();
        load();
        startRaceEffectTask();
    }

    PlayerRace getRace(UUID uuid) {
        return races.getOrDefault(uuid, PlayerRace.MINER);
    }

    void ensurePlayerInitialized(Player player) {
        UUID uuid = player.getUniqueId();
        if (races.containsKey(uuid)) return;
        races.put(uuid, PlayerRace.MINER);
        freeChanges.put(uuid, true);
        save();
        updateLuckPermsPrefix(player, PlayerRace.MINER);
    }

    boolean hasFreeChange(UUID uuid) {
        return freeChanges.getOrDefault(uuid, true);
    }

    boolean selectFromInitialGui(Player player, PlayerRace race) {
        ensurePlayerInitialized(player);
        PlayerRace current = getRace(player.getUniqueId());
        if (current == race) {
            player.sendMessage("§aLa tua razza resta §e" + race.displayName() + "§a. Conservi ancora il cambio gratuito.");
            return false;
        }
        applyChange(player, race, true);
        return true;
    }

    void changeRace(Player player, PlayerRace race, boolean consumeFreeChange) {
        ensurePlayerInitialized(player);
        applyChange(player, race, consumeFreeChange);
    }

    private void applyChange(Player player, PlayerRace race, boolean consumeFreeChange) {
        UUID uuid = player.getUniqueId();
        races.put(uuid, race);
        if (consumeFreeChange) freeChanges.put(uuid, false);
        spadaCriticalCounters.remove(uuid);
        save();
        updateLuckPermsPrefix(player, race);
        applyRaceEffect(player, race);
        player.sendMessage("§aLa tua razza è ora §e" + race.displayName() + "§a.");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        ensurePlayerInitialized(player);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            PlayerRace race = getRace(player.getUniqueId());
            updateLuckPermsPrefix(player, race);
            applyRaceEffect(player, race);
        });
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin,
                () -> applyRaceEffect(player, getRace(player.getUniqueId())));
    }


    private void startRaceEffectTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                applyRaceEffect(player, getRace(player.getUniqueId()));
            }
        }, 20L, 100L);
    }

    private void applyRaceEffect(Player player, PlayerRace race) {
        if (!isRaceEnabled(player)) {
            removeManagedRaceEffects(player);
            return;
        }

        PotionEffectType desired = effectFor(race);

        for (PotionEffectType managed : managedEffectTypes()) {
            if (managed != desired && player.hasPotionEffect(managed)) {
                player.removePotionEffect(managed);
            }
        }

        PotionEffect current = player.getPotionEffect(desired);
        if (current != null && current.getAmplifier() >= 0 && current.getDuration() > 200) {
            return;
        }

        player.addPotionEffect(new PotionEffect(
                desired,
                Integer.MAX_VALUE,
                0,
                false,
                false,
                true
        ), true);
    }

    private void removeManagedRaceEffects(Player player) {
        for (PotionEffectType managed : managedEffectTypes()) {
            if (player.hasPotionEffect(managed)) {
                player.removePotionEffect(managed);
            }
        }
    }

    boolean isRaceEnabled(Player player) {
        String currentWorld = player.getWorld().getName();
        return plugin.getConfig().getStringList("races.disabled-worlds").stream()
                .noneMatch(world -> world.equalsIgnoreCase(currentWorld));
    }

    private PotionEffectType effectFor(PlayerRace race) {
        return switch (race) {
            case MINER -> PotionEffectType.HASTE;
            case SPADA -> PotionEffectType.STRENGTH;
            case CONTADINO -> PotionEffectType.SPEED;
            case SCUDO -> PotionEffectType.RESISTANCE;
        };
    }

    private Set<PotionEffectType> managedEffectTypes() {
        return Set.of(
                PotionEffectType.HASTE,
                PotionEffectType.STRENGTH,
                PotionEffectType.SPEED,
                PotionEffectType.RESISTANCE
        );
    }

    private void updateLuckPermsPrefix(Player player, PlayerRace race) {
        if (luckPerms == null) return;
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) {
            luckPerms.getUserManager().loadUser(player.getUniqueId()).thenAccept(loaded ->
                    plugin.getServer().getScheduler().runTask(plugin, () -> applyRacePrefix(loaded, race)));
            return;
        }
        applyRacePrefix(user, race);
    }

    private void applyRacePrefix(User user, PlayerRace race) {
        if (!"default".equalsIgnoreCase(user.getPrimaryGroup())) {
            removeManagedRacePrefixes(user);
            luckPerms.getUserManager().saveUser(user);
            return;
        }
        removeManagedRacePrefixes(user);
        int priority = Math.max(1, plugin.getConfig().getInt("races.luckperms-prefix-priority", 100));
        user.data().add(PrefixNode.builder(race.prefix(), priority).build());
        luckPerms.getUserManager().saveUser(user);
    }

    private void removeManagedRacePrefixes(User user) {
        for (Node node : user.data().toCollection()) {
            if (!(node instanceof PrefixNode prefixNode)) continue;
            for (PlayerRace managedRace : PlayerRace.values()) {
                if (prefixNode.getMetaValue().equals(managedRace.prefix())) {
                    user.data().remove(node);
                    break;
                }
            }
        }
    }

    double changeCost() {
        return plugin.getConfig().getDouble("races.change-cost", 10000.0D);
    }

    boolean canPay(Player player, double amount) {
        return amount <= 0.0D || (economy != null && economy.has(player, amount));
    }

    boolean withdraw(Player player, double amount) {
        return amount <= 0.0D || (economy != null && economy.withdrawPlayer(player, amount).transactionSuccess());
    }

    private void reward(Player player, double amount, String reason) {
        if (!isRaceEnabled(player) || amount <= 0.0D) return;
        if (economy == null) {
            plugin.getLogger().warning("Ricompensa razza non pagata: nessun provider economy disponibile.");
            return;
        }
        if (economy.depositPlayer(player, amount).transactionSuccess()
                && plugin.getConfig().getBoolean("races.show-reward-message", true)) {
            player.sendActionBar("§a+" + format(amount) + "$ §7(" + reason + ")");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!isRaceEnabled(player)) return;
        Material material = event.getBlock().getType();
        PlayerRace race = getRace(player.getUniqueId());

        if (race == PlayerRace.MINER) {
            reward(player, configuredMaterialReward("races.rewards.miner", material), "Miner");
            return;
        }

        if (race == PlayerRace.CONTADINO && isHarvestable(event)) {
            reward(player, configuredMaterialReward("races.rewards.contadino.harvest", material), "Raccolto");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (!isRaceEnabled(player)) return;
        if (getRace(player.getUniqueId()) != PlayerRace.CONTADINO) return;
        Material material = event.getBlockPlaced().getType();
        reward(player, configuredMaterialReward("races.rewards.contadino.place", material), "Semina");
    }

    @EventHandler(ignoreCancelled = true)
    public void onCombat(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player defender
                && isRaceEnabled(defender)
                && getRace(defender.getUniqueId()) == PlayerRace.SCUDO
                && defender.isBlocking()
                && holdsShield(defender)) {
            reward(defender, plugin.getConfig().getDouble("races.rewards.scudo.per-block", 0.25D), "Parata");
        }

        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!isRaceEnabled(attacker)) return;
        if (getRace(attacker.getUniqueId()) != PlayerRace.SPADA) return;
        if (!(event.getEntity() instanceof Monster monster)) return;
        if (!attacker.getInventory().getItemInMainHand().getType().name().endsWith("_SPADA")) return;
        if (!isCritical(attacker)) return;

        int needed = Math.max(1, plugin.getConfig().getInt("races.rewards.spada.criticals-required", 3));
        int current = spadaCriticalCounters.merge(attacker.getUniqueId(), 1, Integer::sum);
        if (current < needed) {
            attacker.sendActionBar("§eCritici §lSPADA§e: §f" + current + "§7/§f" + needed);
            return;
        }

        spadaCriticalCounters.put(attacker.getUniqueId(), 0);
        String type = monster.getType().name();
        double amount = plugin.getConfig().getDouble(
                "races.rewards.spada.mobs." + type,
                plugin.getConfig().getDouble("races.rewards.spada.default", 0.50D));
        reward(attacker, amount, "3 critici su " + pretty(type));
    }

    private boolean isHarvestable(BlockBreakEvent event) {
        Material material = event.getBlock().getType();
        if (SIMPLE_HARVESTABLES.contains(material)) return true;
        if (!(event.getBlock().getBlockData() instanceof Ageable ageable)) return false;
        return ageable.getAge() >= ageable.getMaximumAge();
    }

    private boolean holdsShield(Player player) {
        return player.getInventory().getItemInMainHand().getType() == Material.SHIELD
                || player.getInventory().getItemInOffHand().getType() == Material.SHIELD;
    }

    private boolean isCritical(Player player) {
        return player.getFallDistance() > 0.0F
                && !player.isOnGround()
                && !player.isInWater()
                && !player.hasPotionEffect(PotionEffectType.BLINDNESS)
                && !player.isInsideVehicle()
                && player.getAttackCooldown() > 0.9F;
    }

    private double configuredMaterialReward(String path, Material material) {
        return plugin.getConfig().getDouble(path + "." + material.name(), 0.0D);
    }

    private String format(double value) {
        if (value == Math.rint(value)) return String.format(Locale.US, "%.0f", value);
        return String.format(Locale.US, "%.2f", value);
    }

    private String pretty(String value) {
        return value.toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private void installConfigDefaults() {
        Map<String, Double> defaults = Map.ofEntries(
                Map.entry("COAL_ORE", 0.20), Map.entry("DEEPSLATE_COAL_ORE", 0.25),
                Map.entry("COPPER_ORE", 0.20), Map.entry("DEEPSLATE_COPPER_ORE", 0.25),
                Map.entry("IRON_ORE", 0.40), Map.entry("DEEPSLATE_IRON_ORE", 0.50),
                Map.entry("GOLD_ORE", 0.70), Map.entry("DEEPSLATE_GOLD_ORE", 0.85),
                Map.entry("REDSTONE_ORE", 0.30), Map.entry("DEEPSLATE_REDSTONE_ORE", 0.35),
                Map.entry("LAPIS_ORE", 0.50), Map.entry("DEEPSLATE_LAPIS_ORE", 0.60),
                Map.entry("DIAMOND_ORE", 2.00), Map.entry("DEEPSLATE_DIAMOND_ORE", 2.50),
                Map.entry("EMERALD_ORE", 2.50), Map.entry("DEEPSLATE_EMERALD_ORE", 3.00),
                Map.entry("NETHER_GOLD_ORE", 0.30), Map.entry("NETHER_QUARTZ_ORE", 0.25),
                Map.entry("ANCIENT_DEBRIS", 5.00)
        );
        defaults.forEach((material, value) -> plugin.getConfig().addDefault("races.rewards.miner." + material, value));

        addCropDefaults("harvest", Map.ofEntries(
                Map.entry("WHEAT", 0.20), Map.entry("CARROTS", 0.20), Map.entry("POTATOES", 0.20),
                Map.entry("BEETROOTS", 0.25), Map.entry("NETHER_WART", 0.30), Map.entry("COCOA", 0.25),
                Map.entry("MELON", 0.25), Map.entry("PUMPKIN", 0.25), Map.entry("SUGAR_CANE", 0.10),
                Map.entry("CACTUS", 0.10), Map.entry("BAMBOO", 0.05)
        ));
        addCropDefaults("place", Map.ofEntries(
                Map.entry("WHEAT", 0.03), Map.entry("CARROTS", 0.03), Map.entry("POTATOES", 0.03),
                Map.entry("BEETROOTS", 0.03), Map.entry("NETHER_WART", 0.04), Map.entry("COCOA", 0.04),
                Map.entry("MELON_STEM", 0.03), Map.entry("PUMPKIN_STEM", 0.03),
                Map.entry("SUGAR_CANE", 0.02), Map.entry("CACTUS", 0.02), Map.entry("BAMBOO", 0.01)
        ));

        plugin.getConfig().addDefault("races.disabled-worlds", java.util.List.of(
                "arena-pvp-unranked",
                "pillars"
        ));
        plugin.getConfig().addDefault("races.luckperms-prefix-priority", 100);
        plugin.getConfig().addDefault("races.change-cost", 10000.0D);
        plugin.getConfig().addDefault("races.show-reward-message", true);
        plugin.getConfig().addDefault("races.rewards.scudo.per-block", 0.25D);
        plugin.getConfig().addDefault("races.rewards.spada.criticals-required", 3);
        plugin.getConfig().addDefault("races.rewards.spada.default", 0.50D);
        plugin.getConfig().options().copyDefaults(true);
        plugin.saveConfig();
    }

    private void addCropDefaults(String action, Map<String, Double> values) {
        values.forEach((material, value) -> plugin.getConfig().addDefault(
                "races.rewards.contadino." + action + "." + material, value));
    }

    private void load() {
        races.clear();
        freeChanges.clear();
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("players");
        if (section == null) return;
        for (String rawUuid : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(rawUuid);
                PlayerRace race = PlayerRace.parse(yaml.getString("players." + rawUuid + ".race", "MINER"))
                        .orElse(PlayerRace.MINER);
                races.put(uuid, race);
                String freePath = "players." + rawUuid + ".free-change-available";
                freeChanges.put(uuid, yaml.contains(freePath) ? yaml.getBoolean(freePath) : true);
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("UUID non valido ignorato in races.yml: " + rawUuid);
            }
        }
    }

    void save() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) return;
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, PlayerRace> entry : races.entrySet()) {
            String path = "players." + entry.getKey() + ".";
            yaml.set(path + "race", entry.getValue().name());
            yaml.set(path + "free-change-available", freeChanges.getOrDefault(entry.getKey(), true));
        }
        try {
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Impossibile salvare races.yml", exception);
        }
    }
}
