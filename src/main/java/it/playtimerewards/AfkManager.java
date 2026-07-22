package it.playtimerewards;

import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class AfkManager {

    private static final long MILLIS_PER_MINUTE = 60_000L;

    private final PlaytimeRewardsPlugin plugin;
    private final MessageService messages;
    private final Map<UUID, AfkState> afkStates = new HashMap<>();

    private BukkitTask afkTask;

    AfkManager(PlaytimeRewardsPlugin plugin, MessageService messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    void start() {
        if (!isAfkEnabled()) {
            return;
        }

        afkTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::checkAfkStatus, 20L, 20L);
    }

    void shutdown() {
        if (afkTask != null) {
            afkTask.cancel();
        }

        afkStates.clear();
    }

    void resetAfk(Player player) {
        UUID uuid = player.getUniqueId();
        AfkState oldState = afkStates.get(uuid);

        if (oldState != null && oldState.isAfk) {
            messages.send(player, "afk-disabled");
        }

        afkStates.put(uuid, new AfkState(System.currentTimeMillis(), false));
    }

    private void checkAfkStatus() {
        long now = System.currentTimeMillis();
        long afkTimeoutMillis = getAfkTimeoutMillis();
        long kickTimeoutMillis = getKickTimeoutMillis();

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            AfkState state = afkStates.computeIfAbsent(uuid, ignored -> new AfkState(now, false));
            long inactiveMillis = now - state.lastActivityTime;

            if (!state.isAfk && inactiveMillis >= afkTimeoutMillis) {
                state.isAfk = true;
                state.afkStartTime = now;
                messages.send(player, "afk-enabled");
            }

            if (!state.isAfk) {
                continue;
            }

            long afkDurationMillis = now - state.afkStartTime;
            if (afkDurationMillis < kickTimeoutMillis) {
                continue;
            }

            // Rimuove lo stato AFK prima del kick. In questo modo il giocatore
            // viene espulso una sola volta e al prossimo accesso riparte come attivo.
            afkStates.put(uuid, new AfkState(now, false));
            player.kickPlayer(messages.getMessage("afk-kicked"));
        }
    }

    private long getAfkTimeoutMillis() {
        long minutes = Math.max(1L, plugin.getConfig().getLong("afk.afk-timeout-minutes", 15L));
        return minutes * MILLIS_PER_MINUTE;
    }

    private long getKickTimeoutMillis() {
        long minutes = Math.max(1L, plugin.getConfig().getLong("afk.kick-timeout-minutes", 60L));
        return minutes * MILLIS_PER_MINUTE;
    }

    private boolean isAfkEnabled() {
        return plugin.getConfig().getBoolean("afk.enabled", true);
    }

    private static final class AfkState {
        long lastActivityTime;
        boolean isAfk;
        long afkStartTime;

        AfkState(long lastActivityTime, boolean isAfk) {
            this.lastActivityTime = lastActivityTime;
            this.isAfk = isAfk;
            this.afkStartTime = -1L;
        }
    }
}
