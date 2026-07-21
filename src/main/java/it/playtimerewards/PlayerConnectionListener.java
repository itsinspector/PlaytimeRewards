package it.playtimerewards;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

final class PlayerConnectionListener implements Listener {
    private final RewardService rewardService;
    private final WelcomeGuiManager welcomeGuiManager;

    PlayerConnectionListener(RewardService rewardService, WelcomeGuiManager welcomeGuiManager) {
        this.rewardService = rewardService;
        this.welcomeGuiManager = welcomeGuiManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        rewardService.startTracking(event.getPlayer());
        
        PlayerTimeData playerData = rewardService.getPlayerData(event.getPlayer().getUniqueId());
        if (playerData != null && !playerData.hasSeenWelcomeGui()) {
            welcomeGuiManager.openWelcomeGui(event.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        rewardService.stopTracking(event.getPlayer());
    }
}
