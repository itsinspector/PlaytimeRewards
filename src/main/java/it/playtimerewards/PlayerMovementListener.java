package it.playtimerewards;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

final class PlayerMovementListener implements Listener {
    private final AfkManager afkManager;

    PlayerMovementListener(AfkManager afkManager) {
        this.afkManager = afkManager;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        if (event.getFrom().distance(event.getTo()) > 0.0D) {
            afkManager.resetAfk(player);
        }
    }
}
