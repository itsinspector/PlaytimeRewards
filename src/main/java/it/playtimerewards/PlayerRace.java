package it.playtimerewards;

import org.bukkit.Material;

import java.util.Locale;
import java.util.Optional;

public enum PlayerRace {
    MINER("Miner", Material.DIAMOND_PICKAXE, "%img_miner% &r"),
    CONTADINO("Contadino", Material.GOLDEN_HOE, "%img_contadino% &r"),
    SCUDO("Scudo", Material.SHIELD, "%img_scudo% &r"),
    SWORD("Sword", Material.DIAMOND_SWORD, "%img_sword% &r");

    private final String displayName;
    private final Material icon;
    private final String prefix;

    PlayerRace(String displayName, Material icon, String prefix) {
        this.displayName = displayName;
        this.icon = icon;
        this.prefix = prefix;
    }

    public String displayName() {
        return displayName;
    }

    public Material icon() {
        return icon;
    }

    public String prefix() {
        return prefix;
    }

    public static Optional<PlayerRace> parse(String input) {
        if (input == null) return Optional.empty();
        String normalized = input.trim().toUpperCase(Locale.ROOT);
        if (normalized.equals("FARMER")) normalized = "CONTADINO";
        if (normalized.equals("SHIELD")) normalized = "SCUDO";
        return switch (normalized) {
            case "MINER" -> Optional.of(MINER);
            case "CONTADINO" -> Optional.of(CONTADINO);
            case "SCUDO" -> Optional.of(SCUDO);
            case "SWORD" -> Optional.of(SWORD);
            default -> Optional.empty();
        };
    }
}
