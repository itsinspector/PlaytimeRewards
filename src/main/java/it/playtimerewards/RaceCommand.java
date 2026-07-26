package it.playtimerewards;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

final class RaceCommand implements CommandExecutor, TabCompleter {
    private static final long CONFIRM_TIMEOUT_MILLIS = 30_000L;
    private record PendingChange(PlayerRace race, double cost, boolean consumeFreeChange, long expiresAt) {}

    private final RaceManager raceManager;
    private final Map<UUID, PendingChange> pending = new HashMap<>();

    RaceCommand(RaceManager raceManager) {
        this.raceManager = raceManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Questo comando può essere usato solo da un giocatore.");
            return true;
        }
        raceManager.ensurePlayerInitialized(player);

        if (args.length == 0) {
            player.sendMessage("§eRazza attuale: §f" + raceManager.getRace(player.getUniqueId()).displayName());
            player.sendMessage(raceManager.hasFreeChange(player.getUniqueId())
                    ? "§aHai ancora un cambio razza gratuito."
                    : "§aIl prossimo cambio costa §f\uE0D8 §e" + format(raceManager.changeCost()) + ".");
            player.sendMessage("§cUsa §f/razza cambia <miner|contadino|scudo|spada>§7.");
            return true;
        }

        if (args[0].equalsIgnoreCase("conferma")) return confirm(player);
        if (!args[0].equalsIgnoreCase("cambia")) {
            if (args.length == 1) {
                return showPlayerRace(player, args[0]);
            }
            player.sendMessage("§cUso: /razza <giocatore>");
            return true;
        }
        if (args.length < 2) {
            player.sendMessage("§cUso: /razza cambia <miner|contadino|scudo|spada>");
            return true;
        }

        PlayerRace target = PlayerRace.parse(args[1]).orElse(null);
        if (target == null) {
            player.sendMessage("§cRazza non valida. Usa: miner, contadino, scudo oppure spada.");
            return true;
        }
        if (raceManager.getRace(player.getUniqueId()) == target) {
            player.sendMessage("§aHai già selezionato questa razza.");
            return true;
        }

        boolean free = raceManager.hasFreeChange(player.getUniqueId());
        double cost = free ? 0.0D : raceManager.changeCost();
        pending.put(player.getUniqueId(), new PendingChange(
                target, cost, free, System.currentTimeMillis() + CONFIRM_TIMEOUT_MILLIS));
        if (free) {
            player.sendMessage("§aStai per usare il tuo §ecambio gratuito §aper diventare §f" + target.displayName() + "§a.");
        } else {
            player.sendMessage("§aStai per diventare §f" + target.displayName() + "§a al costo di §f\uE0D8 §e" + format(cost) + "§e.");
        }
        player.sendMessage("§aScrivi §e/razza conferma §aentro 30 secondi per confermare.");
        return true;
    }

    private boolean showPlayerRace(Player viewer, String playerName) {
        Player onlineTarget = Bukkit.getPlayerExact(playerName);
        if (onlineTarget != null) {
            raceManager.ensurePlayerInitialized(onlineTarget);
            viewer.sendMessage(
                    "§eRazza di §f"
                            + onlineTarget.getName()
                            + "§e: §f"
                            + raceManager.getRace(onlineTarget.getUniqueId()).displayName()
            );
            return true;
        }

        OfflinePlayer offlineTarget = Stream.of(Bukkit.getOfflinePlayers())
                .filter(candidate -> candidate.getName() != null)
                .filter(candidate -> candidate.getName().equalsIgnoreCase(playerName))
                .findFirst()
                .orElse(null);

        if (offlineTarget == null
                || !raceManager.hasStoredRace(offlineTarget.getUniqueId())) {
            viewer.sendMessage("§cGiocatore non trovato o senza una razza salvata.");
            return true;
        }

        viewer.sendMessage(
                "§eRazza di §f"
                        + offlineTarget.getName()
                        + "§e: §f"
                        + raceManager.getRace(offlineTarget.getUniqueId()).displayName()
        );
        return true;
    }

    private boolean confirm(Player player) {
        PendingChange change = pending.remove(player.getUniqueId());
        if (change == null || change.expiresAt() < System.currentTimeMillis()) {
            player.sendMessage("§cNon hai nessun cambio razza da confermare, oppure è scaduto.");
            return true;
        }
        if (raceManager.getRace(player.getUniqueId()) == change.race()) {
            player.sendMessage("§eHai già questa razza.");
            return true;
        }
        if (!raceManager.canPay(player, change.cost())) {
            player.sendMessage("§cNon hai abbastanza soldi. Servono §f\uE0D8 §e" + format(change.cost()) + "§c.");
            return true;
        }
        if (!raceManager.withdraw(player, change.cost())) {
            player.sendMessage("§cIl pagamento non è riuscito. Riprova.");
            return true;
        }
        raceManager.changeRace(player, change.race(), change.consumeFreeChange());
        return true;
    }

    private String format(double value) {
        if (value == Math.rint(value)) return String.format(Locale.US, "%.0f", value);
        return String.format(Locale.US, "%.2f", value);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase(Locale.ROOT);
            return Stream.concat(
                            Stream.of("cambia", "conferma"),
                            Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    )
                    .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(input))
                    .distinct()
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("cambia")) {
            return List.of("miner", "contadino", "scudo", "spada");
        }
        return List.of();
    }
}
