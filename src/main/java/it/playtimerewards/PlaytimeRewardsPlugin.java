package it.playtimerewards;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlaytimeRewardsPlugin extends JavaPlugin {
    private PlayerDataStore dataStore;
    private RewardService rewardService;
    private AfkManager afkManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        MessageService messages = new MessageService(this);
        dataStore = new PlayerDataStore(this);
        dataStore.load();

        Economy economy = setupEconomy();
        rewardService = new RewardService(this, dataStore, messages, economy);
        afkManager = new AfkManager(this, messages);
        WelcomeGuiManager welcomeGuiManager = new WelcomeGuiManager(this, rewardService);

        registerCommand("playtime", new PlaytimeCommand(rewardService, messages));
        registerCommand("setplaytimereward", new SetPlaytimeRewardCommand(this, messages));
        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(rewardService, welcomeGuiManager), this);
        getServer().getPluginManager().registerEvents(new PlayerMovementListener(afkManager), this);
        getServer().getPluginManager().registerEvents(welcomeGuiManager, this);

        rewardService.start();
        afkManager.start();
        getLogger().info("PlaytimeRewards abilitato.");

        if (economy == null && getConfig().getDouble("reward.money", 0.0D) > 0.0D) {
            getLogger().warning(
                    "Vault o un provider economy non è disponibile: le ricompense in denaro saranno saltate."
            );
        }
    }

    @Override
    public void onDisable() {
        if (rewardService != null) {
            rewardService.shutdown();
        }
        if (afkManager != null) {
            afkManager.shutdown();
        }
        if (dataStore != null) {
            dataStore.save();
        }
        getLogger().info("PlaytimeRewards disabilitato e dati salvati.");
    }

    private Economy setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return null;
        }

        RegisteredServiceProvider<Economy> registration =
                getServer().getServicesManager().getRegistration(Economy.class);
        return registration == null ? null : registration.getProvider();
    }

    private void registerCommand(String name, org.bukkit.command.CommandExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            throw new IllegalStateException("Comando mancante in plugin.yml: " + name);
        }
        command.setExecutor(executor);
    }
}
