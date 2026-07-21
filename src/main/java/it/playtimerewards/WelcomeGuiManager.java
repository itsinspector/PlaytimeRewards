package it.playtimerewards;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.List;

final class WelcomeGuiManager implements Listener {

    private static final String GUI_TITLE = "§e§l§nᴠᴜᴏɪ ʟᴇɢɢᴇʀᴇ ʟᴀ ɢᴜɪᴅᴀ?";
    private static final int GUI_SIZE = 27;

    private static final int SKIP_SLOT = 11;
    private static final int GUIDE_SLOT = 15;

    private final JavaPlugin plugin;
    private final RewardService rewardService;

    WelcomeGuiManager(JavaPlugin plugin, RewardService rewardService) {
        this.plugin = plugin;
        this.rewardService = rewardService;
    }

    void openWelcomeGui(Player player) {
        Inventory inventory = Bukkit.createInventory(
                new WelcomeGuiHolder(),
                GUI_SIZE,
                GUI_TITLE
        );

        inventory.setItem(
                SKIP_SLOT,
                createItem(
                        Material.BARRIER,
                        "§f\uE060 §c§l§nɴᴏɴ ʟᴇɢɢᴇʀᴇ ʟᴀ ɢᴜɪᴅᴀ",
                        "§eᴄʟɪᴄᴋ-sɪɴɪsᴛʀᴏ ᴘᴇʀ sᴋɪᴘᴘᴀʀᴇ."
                )
        );

        inventory.setItem(
                GUIDE_SLOT,
                createItem(
                        Material.BOOK,
                        "§f\uE168 §a§l§nʟᴇɢɢɪ ʟᴀ ɢᴜɪᴅᴀ",
                        "§eᴄʟɪᴄᴋ-sɪɴɪsᴛʀᴏ ᴘᴇʀ ʟᴇɢɢᴇʀʟᴏ."
                )
        );

        player.openInventory(inventory);
    }

    private ItemStack createItem(
            Material material,
            String displayName,
            String... lore
    ) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta == null) {
            return item;
        }

        meta.setDisplayName(displayName);
        meta.setLore(List.of(lore));
        item.setItemMeta(meta);

        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof WelcomeGuiHolder)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int rawSlot = event.getRawSlot();
        int guiSize = event.getView().getTopInventory().getSize();

        if (rawSlot < 0 || rawSlot >= guiSize) {
            return;
        }

        if (rawSlot == SKIP_SLOT) {
            closeAndMarkAsSeen(player);
            return;
        }

        if (rawSlot == GUIDE_SLOT) {
            closeAndMarkAsSeen(player);
            executeGuideCommand(player);
        }
    }

    private void executeGuideCommand(Player player) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }

            String command = "/guida";

            PlayerCommandPreprocessEvent commandEvent =
                    new PlayerCommandPreprocessEvent(player, command);

            Bukkit.getPluginManager().callEvent(commandEvent);

            if (commandEvent.isCancelled()) {
                /*
                 * Un plugin ha intercettato il comando.
                 * Probabilmente ha già aperto la guida.
                 */
                return;
            }

            String commandLine = commandEvent.getMessage();

            if (commandLine.startsWith("/")) {
                commandLine = commandLine.substring(1);
            }

            boolean executed = Bukkit.dispatchCommand(player, commandLine);

            if (!executed) {
                player.sendMessage("§cNon è stato possibile eseguire /guida.");
                plugin.getLogger().warning(
                        "Il comando /guida non è stato trovato per "
                                + player.getName()
                );
            }
        }, 1L);
    }

    private void closeAndMarkAsSeen(Player player) {
        player.closeInventory();
        rewardService.markWelcomeGuiSeen(player.getUniqueId());
    }

    private static final class WelcomeGuiHolder implements InventoryHolder {

        @Override
        public Inventory getInventory() {
            throw new UnsupportedOperationException(
                    "Questo holder serve soltanto a identificare la GUI."
            );
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof WelcomeGuiHolder)) {
            return;
        }

        /*
         * Blocca il trascinamento soltanto quando almeno uno degli slot
         * coinvolti appartiene alla GUI superiore.
         */
        boolean affectsGui = event.getRawSlots().stream()
                .anyMatch(slot -> slot < event.getInventory().getSize());

        if (affectsGui) {
            event.setCancelled(true);
        }
    }
}