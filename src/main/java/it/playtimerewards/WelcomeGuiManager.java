package it.playtimerewards;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class WelcomeGuiManager implements Listener {
    private static final String GUI_TITLE = "§e§l§nᴠᴜᴏɪ ʟᴇɢɢᴇʀᴇ ʟᴀ ɢᴜɪᴅᴀ?";
    private static final String RACE_TITLE = "§f\uE0F8 §6§l§nsᴄᴇɢʟɪ ʟᴀ ᴛᴜᴀ ʀᴀᴢᴢᴀ";
    private static final int GUI_SIZE = 27;
    private static final int SKIP_SLOT = 11;
    private static final int GUIDE_SLOT = 15;

    private enum RequiredStage { WELCOME, RACE }

    private final JavaPlugin plugin;
    private final RewardService rewardService;
    private final RaceManager raceManager;
    private final Map<UUID, RequiredStage> requiredStages = new HashMap<>();
    private final Map<UUID, Boolean> guideAfterRace = new HashMap<>();

    WelcomeGuiManager(JavaPlugin plugin, RewardService rewardService, RaceManager raceManager) {
        this.plugin = plugin;
        this.rewardService = rewardService;
        this.raceManager = raceManager;
    }

    void openWelcomeGui(Player player) {
        raceManager.ensurePlayerInitialized(player);
        requiredStages.put(player.getUniqueId(), RequiredStage.WELCOME);
        Inventory inventory = Bukkit.createInventory(new WelcomeGuiHolder(), GUI_SIZE, GUI_TITLE);
        inventory.setItem(SKIP_SLOT, createItem(Material.BARRIER,
                "§f\uE060 §c§l§nɴᴏɴ ʟᴇɢɢᴇʀᴇ ʟᴀ ɢᴜɪᴅᴀ",
                "§eᴄʟɪᴄᴋ-sɪɴɪsᴛʀᴏ ᴘᴇʀ sᴋɪᴘᴘᴀʀᴇ."));
        inventory.setItem(GUIDE_SLOT, createItem(Material.BOOK,
                "§f\uE168 §a§l§nʟᴇɢɢɪ ʟᴀ ɢᴜɪᴅᴀ",
                "§eᴄʟɪᴄᴋ-sɪɴɪsᴛʀᴏ ᴘᴇʀ ʟᴇɢɢᴇʀʟᴏ."));
        player.openInventory(inventory);
    }

    private void openRaceGui(Player player, boolean openGuideAfter) {
        UUID uuid = player.getUniqueId();
        requiredStages.put(uuid, RequiredStage.RACE);
        guideAfterRace.put(uuid, openGuideAfter);

        Inventory inventory = Bukkit.createInventory(new RaceGuiHolder(), GUI_SIZE, RACE_TITLE);
        PlayerRace current = raceManager.getRace(uuid);
        inventory.setItem(10, raceItem(PlayerRace.MINER, current,
                "§7Razza assegnata inizialmente",
                "§fGuadagni scavando minerali.",
                "§8La ricompensa varia per minerale."));
        inventory.setItem(12, raceItem(PlayerRace.CONTADINO, current,
                "§fGuadagni raccogliendo crop maturi",
                "§fe piantando nuove coltivazioni."));
        inventory.setItem(14, raceItem(PlayerRace.SCUDO, current,
                "§fGuadagni quando pari un colpo",
                "§futilizzando uno scudo."));
        inventory.setItem(16, raceItem(PlayerRace.SWORD, current,
                "§fOgni 3 colpi critici contro mostri",
                "§fguadagni in base al mob."));
        player.openInventory(inventory);
    }

    private ItemStack raceItem(PlayerRace race, PlayerRace current, String... description) {
        String[] lore = new String[description.length + 3];
        lore[0] = race == current ? "§aRazza attuale" : "§7Cambio gratuito disponibile";
        lore[1] = "§7";
        System.arraycopy(description, 0, lore, 2, description.length);
        lore[lore.length - 1] = race == current
                ? "§eClicca per continuare mantenendo questa razza."
                : "§eClicca per usare il cambio gratuito.";
        return createItem(race.icon(), "§6§l" + race.displayName().toUpperCase(), lore);
    }

    private ItemStack createItem(Material material, String displayName, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(displayName);
        meta.setLore(List.of(lore));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof WelcomeGuiHolder) && !(holder instanceof RaceGuiHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= event.getView().getTopInventory().getSize()) return;
        UUID uuid = player.getUniqueId();

        if (holder instanceof WelcomeGuiHolder) {
            if (rawSlot == SKIP_SLOT) openRaceGui(player, false);
            else if (rawSlot == GUIDE_SLOT) openRaceGui(player, true);
            return;
        }

        PlayerRace selected = switch (rawSlot) {
            case 10 -> PlayerRace.MINER;
            case 12 -> PlayerRace.CONTADINO;
            case 14 -> PlayerRace.SCUDO;
            case 16 -> PlayerRace.SWORD;
            default -> null;
        };
        if (selected == null) return;

        boolean openGuide = guideAfterRace.getOrDefault(uuid, false);
        raceManager.selectFromInitialGui(player, selected);
        rewardService.markWelcomeGuiSeen(uuid);
        requiredStages.remove(uuid);
        guideAfterRace.remove(uuid);
        player.closeInventory();
        if (openGuide) executeGuideCommand(player);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();
        RequiredStage stage = requiredStages.get(uuid);
        InventoryHolder holder = event.getView().getTopInventory().getHolder();

        if (holder instanceof WelcomeGuiHolder && stage == RequiredStage.WELCOME) {
            reopenNextTick(player, () -> openWelcomeGui(player));
            return;
        }
        if (holder instanceof RaceGuiHolder && stage == RequiredStage.RACE) {
            boolean openGuide = guideAfterRace.getOrDefault(uuid, false);
            reopenNextTick(player, () -> openRaceGui(player, openGuide));
        }
    }

    private void reopenNextTick(Player player, Runnable opener) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            opener.run();
        });
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof WelcomeGuiHolder) && !(holder instanceof RaceGuiHolder)) return;
        int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlots().stream().anyMatch(slot -> slot < topSize)) event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        requiredStages.remove(uuid);
        guideAfterRace.remove(uuid);
    }

    private void executeGuideCommand(Player player) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            String command = "/guida";
            PlayerCommandPreprocessEvent commandEvent = new PlayerCommandPreprocessEvent(player, command);
            Bukkit.getPluginManager().callEvent(commandEvent);
            if (commandEvent.isCancelled()) return;
            String commandLine = commandEvent.getMessage();
            if (commandLine.startsWith("/")) commandLine = commandLine.substring(1);
            if (!Bukkit.dispatchCommand(player, commandLine)) {
                player.sendMessage("§cNon è stato possibile eseguire /guida.");
                plugin.getLogger().warning("Il comando /guida non è stato trovato per " + player.getName());
            }
        }, 1L);
    }

    private static final class WelcomeGuiHolder implements InventoryHolder {
        @Override public Inventory getInventory() { throw new UnsupportedOperationException(); }
    }

    private static final class RaceGuiHolder implements InventoryHolder {
        @Override public Inventory getInventory() { throw new UnsupportedOperationException(); }
    }
}
