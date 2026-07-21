package it.playtimerewards;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

final class PlayerDataStore {
    private final JavaPlugin plugin;
    private final File dataFile;
    private final Map<UUID, PlayerTimeData> players = new HashMap<>();

    PlayerDataStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "playerdata.yml");
    }

    void load() {
        players.clear();

        if (!dataFile.exists()) {
            return;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection playersSection = yaml.getConfigurationSection("players");
        if (playersSection == null) {
            return;
        }

        for (String rawUuid : playersSection.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(rawUuid);
                String path = "players." + rawUuid + ".";
                String lastKnownName = yaml.getString(path + "last-known-name", "Sconosciuto");
                long totalMillis = yaml.getLong(path + "total-playtime-millis", 0L);
                long progressMillis = yaml.getLong(path + "reward-progress-millis", 0L);
                PlayerTimeData data = new PlayerTimeData(uuid, lastKnownName, totalMillis, progressMillis);
                if (yaml.getBoolean(path + "has-seen-welcome-gui", false)) {
                    data.markWelcomeGuiSeen();
                }
                players.put(uuid, data);
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("UUID non valido ignorato in playerdata.yml: " + rawUuid);
            }
        }
    }

    PlayerTimeData getOrCreate(UUID uuid, String playerName) {
        PlayerTimeData data = players.computeIfAbsent(
                uuid,
                ignored -> new PlayerTimeData(uuid, playerName, 0L, 0L)
        );
        data.setLastKnownName(playerName);
        return data;
    }

    void save() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().severe("Impossibile creare la cartella dati del plugin.");
            return;
        }

        YamlConfiguration yaml = new YamlConfiguration();
        for (PlayerTimeData data : players.values()) {
            String path = "players." + data.uuid() + ".";
            yaml.set(path + "last-known-name", data.lastKnownName());
            yaml.set(path + "total-playtime-millis", data.totalPlaytimeMillis());
            yaml.set(path + "reward-progress-millis", data.rewardProgressMillis());
            yaml.set(path + "has-seen-welcome-gui", data.hasSeenWelcomeGui());
        }

        try {
            yaml.save(dataFile);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Impossibile salvare playerdata.yml", exception);
        }
    }
}
