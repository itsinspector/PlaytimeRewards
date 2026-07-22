package it.playtimerewards;

import net.luckperms.api.LuckPerms;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlaytimeRewardsPlugin extends JavaPlugin {
    private PlayerDataStore dataStore;
    private RewardService rewardService;
    private AfkManager afkManager;
    private RaceManager raceManager;
    private RaceGoalManager raceGoalManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        MessageService messages = new MessageService(this);
        dataStore = new PlayerDataStore(this);
        dataStore.load();

        Economy economy = setupEconomy();
        LuckPerms luckPerms = setupLuckPerms();
        rewardService = new RewardService(this, dataStore, messages, economy);
        afkManager = new AfkManager(this, messages);
        raceManager = new RaceManager(this, economy, luckPerms);
        raceGoalManager = new RaceGoalManager(this, raceManager, economy);
        WelcomeGuiManager welcomeGuiManager = new WelcomeGuiManager(this, rewardService, raceManager);
        RaceCommand raceCommand = new RaceCommand(raceManager);

        registerCommand("playtime", new PlaytimeCommand(rewardService, messages));
        registerCommand("setplaytimereward", new SetPlaytimeRewardCommand(this, messages));
        registerCommand("razza", raceCommand, raceCommand);

        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(rewardService, welcomeGuiManager), this);
        getServer().getPluginManager().registerEvents(new PlayerMovementListener(afkManager), this);
        getServer().getPluginManager().registerEvents(welcomeGuiManager, this);
        getServer().getPluginManager().registerEvents(raceManager, this);
        getServer().getPluginManager().registerEvents(raceGoalManager, this);

        rewardService.start();
        afkManager.start();
        getLogger().info("PlaytimeRewards abilitato.");
        if (economy == null) {
            getLogger().warning("Vault o un provider economy non è disponibile: ricompense e cambi razza non funzioneranno.");
        }
        if (luckPerms == null) {
            getLogger().warning("LuckPerms non è disponibile: i prefissi delle razze non verranno applicati.");
        }
    }

    @Override
    public void onDisable() {
        if (rewardService != null) rewardService.shutdown();
        if (afkManager != null) afkManager.shutdown();
        if (raceGoalManager != null) raceGoalManager.shutdown();
        if (raceManager != null) raceManager.save();
        if (dataStore != null) dataStore.save();
        getLogger().info("PlaytimeRewards disabilitato e dati salvati.");
    }

    private LuckPerms setupLuckPerms() {
        return getServer().getServicesManager().load(LuckPerms.class);
    }

    private Economy setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return null;
        RegisteredServiceProvider<Economy> registration =
                getServer().getServicesManager().getRegistration(Economy.class);
        return registration == null ? null : registration.getProvider();
    }

    private void registerCommand(String name, CommandExecutor executor) {
        registerCommand(name, executor, null);
    }

    private void registerCommand(String name, CommandExecutor executor, TabCompleter tabCompleter) {
        PluginCommand command = getCommand(name);
        if (command == null) throw new IllegalStateException("Comando mancante in plugin.yml: " + name);
        command.setExecutor(executor);
        if (tabCompleter != null) command.setTabCompleter(tabCompleter);
    }
}
