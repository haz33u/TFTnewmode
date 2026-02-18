package dev.astralclash.listeners;

import dev.astralclash.AstralClash;
import dev.astralclash.player.ArenaPlayer;
import dev.astralclash.ui.ShopUI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Handles player join/quit and shop inventory clicks.
 */
public class PlayerListener implements Listener {

    private final AstralClash plugin;

    public PlayerListener(AstralClash plugin) {
        this.plugin = plugin;
    }

    // ── Join / Quit ───────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        player.sendMessage(Component.text("Welcome to AstralClash! Type /astral join to play.",
                NamedTextColor.LIGHT_PURPLE));
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        // Remove from lobby if waiting
        plugin.getGameManager().leaveLobby(player);
        // Save and remove from active game
        plugin.getPlayerManager().removeArenaPlayer(player.getUniqueId());
    }

    // ── Shop inventory clicks ─────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        var title = event.getView().title();
        // Check if this is the AstralClash shop by comparing title text
        var shopTitle = Component.text(ShopUI.SHOP_TITLE);
        if (!event.getView().title().equals(shopTitle)
                && !event.getView().getTitle().equals(ShopUI.SHOP_TITLE)) return;

        event.setCancelled(true); // always cancel shop clicks

        ArenaPlayer ap = plugin.getPlayerManager().getArenaPlayer(player);
        if (ap == null) return;

        int slot = event.getRawSlot();

        // Champion purchase slots (0-4)
        if (slot >= ShopUI.getChampionSlotStart() && slot < ShopUI.getChampionSlotStart() + 5) {
            int shopSlot = slot - ShopUI.getChampionSlotStart();
            var ci = plugin.getShopManager().buyChampion(ap, shopSlot);
            if (ci != null) {
                player.sendMessage(Component.text(
                        "Purchased " + ci.getChampion().getDisplayName() + "! It's on your bench.",
                        NamedTextColor.GREEN));
                plugin.getUIManager().refreshShop(ap);
            } else {
                player.sendMessage(Component.text(
                        "Cannot buy — not enough gold or bench is full!", NamedTextColor.RED));
            }
            return;
        }

        // Reroll
        if (slot == ShopUI.getRerollSlot()) {
            boolean rerolled = plugin.getShopManager().reroll(ap);
            if (!rerolled) {
                player.sendMessage(Component.text("Not enough gold to reroll!", NamedTextColor.RED));
            } else {
                plugin.getUIManager().refreshShop(ap);
            }
            return;
        }

        // Lock / Unlock
        if (slot == ShopUI.getLockSlot()) {
            ap.setShopLocked(!ap.isShopLocked());
            player.sendMessage(Component.text(
                    ap.isShopLocked() ? "Shop locked!" : "Shop unlocked!", NamedTextColor.YELLOW));
            plugin.getUIManager().refreshShop(ap);
            return;
        }

        // Buy XP
        if (slot == ShopUI.getBuyXpSlot()) {
            boolean bought = plugin.getEconomyManager().buyXp(ap);
            if (bought) {
                player.sendMessage(Component.text(
                        "Bought XP! Now Lv" + ap.getLevel(), NamedTextColor.AQUA));
                plugin.getUIManager().refreshShop(ap);
            } else {
                player.sendMessage(Component.text("Not enough gold for XP!", NamedTextColor.RED));
            }
            return;
        }

        // Close
        if (slot == ShopUI.getCloseSlot()) {
            player.closeInventory();
        }
    }
}
