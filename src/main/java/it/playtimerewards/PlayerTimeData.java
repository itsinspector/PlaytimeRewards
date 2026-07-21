package it.playtimerewards;

import java.util.UUID;

final class PlayerTimeData {
    private final UUID uuid;
    private String lastKnownName;
    private long totalPlaytimeMillis;
    private long rewardProgressMillis;
    private boolean hasSeenWelcomeGui;

    PlayerTimeData(UUID uuid, String lastKnownName, long totalPlaytimeMillis, long rewardProgressMillis) {
        this.uuid = uuid;
        this.lastKnownName = lastKnownName;
        this.totalPlaytimeMillis = Math.max(0L, totalPlaytimeMillis);
        this.rewardProgressMillis = Math.max(0L, rewardProgressMillis);
        this.hasSeenWelcomeGui = false;
    }

    UUID uuid() {
        return uuid;
    }

    String lastKnownName() {
        return lastKnownName;
    }

    void setLastKnownName(String lastKnownName) {
        if (lastKnownName != null && !lastKnownName.isBlank()) {
            this.lastKnownName = lastKnownName;
        }
    }

    long totalPlaytimeMillis() {
        return totalPlaytimeMillis;
    }

    long rewardProgressMillis() {
        return rewardProgressMillis;
    }

    void addOnlineTime(long elapsedMillis) {
        if (elapsedMillis <= 0L) {
            return;
        }
        totalPlaytimeMillis = safeAdd(totalPlaytimeMillis, elapsedMillis);
        rewardProgressMillis = safeAdd(rewardProgressMillis, elapsedMillis);
    }

    void consumeRewardInterval(long intervalMillis) {
        rewardProgressMillis = Math.max(0L, rewardProgressMillis - intervalMillis);
    }

    boolean hasSeenWelcomeGui() {
        return hasSeenWelcomeGui;
    }

    void markWelcomeGuiSeen() {
        this.hasSeenWelcomeGui = true;
    }

    private static long safeAdd(long first, long second) {
        if (Long.MAX_VALUE - first < second) {
            return Long.MAX_VALUE;
        }
        return first + second;
    }
}
