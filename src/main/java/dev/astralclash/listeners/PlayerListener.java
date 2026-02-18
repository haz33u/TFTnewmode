package dev.astralclash.listeners;

import dev.astralclash.AstralClash;
import dev.astralclash.champion.ChampionInstance;
import dev.astralclash.game.GamePhase;
import dev.astralclash.player.ArenaPlayer;
import dev.astralclash.ui.BenchUI;
import dev.astralclash.ui.ShopUI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;

/**
 * Handles player join/quit and all GUI inventory clicks (shop + bench).
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
        player.sendMessage(Component.text(
                "Welcome to AstralClash! Type /astral join to play.",
                NamedTextColor.LIGHT_PURPLE));
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        // Clear UI state (scoreboard, boss bar)
        plugin.getUIManager().onPlayerLeave(player.getUniqueId());
        // Remove from lobby if waiting
        plugin.getGameManager().leaveLobby(player);
        // Save and remove from active game
        plugin.getPlayerManager().removeArenaPlayer(player.getUniqueId());
    }

    // ── Shop inventory clicks ─────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        net.kyori.adventure.text.Component viewTitle = event.getView().title();

        if (viewTitle.equals(ShopUI.SHOP_TITLE_COMPONENT)) {
            handleShopClick(event, player);
        } else if (viewTitle.equals(BenchUI.BENCH_TITLE_COMPONENT)) {
            handleBenchClick(event, player);
        }
    }

    // ── Shop click handler ────────────────────────────────────────────────────

    private void handleShopClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);

        ArenaPlayer ap = plugin.getPlayerManager().getArenaPlayer(player);
        if (ap == null) return;

        int slot = event.getRawSlot();

        // Champion purchase slots 0-4
        if (slot >= ShopUI.getChampionSlotStart() && slot < ShopUI.getChampionSlotStart() + 5) {
            int shopSlot = slot - ShopUI.getChampionSlotStart();
            ChampionInstance ci = plugin.getShopManager().buyChampion(ap, shopSlot);
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

        if (slot == ShopUI.getRerollSlot()) {
            if (!plugin.getShopManager().reroll(ap)) {
                player.sendMessage(Component.text("Not enough gold to reroll!", NamedTextColor.RED));
            } else {
                plugin.getUIManager().refreshShop(ap);
            }
            return;
        }

        if (slot == ShopUI.getLockSlot()) {
            ap.setShopLocked(!ap.isShopLocked());
            player.sendMessage(Component.text(
                    ap.isShopLocked() ? "Shop locked!" : "Shop unlocked!", NamedTextColor.YELLOW));
            plugin.getUIManager().refreshShop(ap);
            return;
        }

        if (slot == ShopUI.getBuyXpSlot()) {
            if (plugin.getEconomyManager().buyXp(ap)) {
                player.sendMessage(Component.text(
                        "Bought XP! Now Lv" + ap.getLevel(), NamedTextColor.AQUA));
                plugin.getUIManager().refreshShop(ap);
            } else {
                player.sendMessage(Component.text("Not enough gold for XP!", NamedTextColor.RED));
            }
            return;
        }

        if (slot == ShopUI.getCloseSlot()) {
            player.closeInventory();
        }
    }

    // ── Bench click handler ───────────────────────────────────────────────────

    private void handleBenchClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);

        ArenaPlayer ap = plugin.getPlayerManager().getArenaPlayer(player);
        if (ap == null) return;

        int slot = event.getRawSlot();
        List<ChampionInstance> bench = ap.getBench();

        if (slot < 0 || slot >= bench.size()) return;
        ChampionInstance ci = bench.get(slot);

        if (event.getClick() == ClickType.RIGHT) {
            // Right-click = sell
            int gold = plugin.getShopManager().sell(ap, ci);
            player.sendMessage(Component.text(
                    "Sold " + ci.getChampion().getDisplayName() + " for " + gold + "g.",
                    NamedTextColor.YELLOW));
            plugin.getUIManager().refreshBench(ap);

        } else if (event.getClick() == ClickType.LEFT) {
            // Left-click = deploy to board (only during PLANNING)
            if (plugin.getGameManager().getPhase() != GamePhase.PLANNING) {
                player.sendMessage(Component.text(
                        "You can only deploy during the Planning phase!", NamedTextColor.RED));
                return;
            }
            if (ap.getDeployedCount() >= ap.getBoardSizeLimit()) {
                player.sendMessage(Component.text(
                        "Board is full! Sell or upgrade a unit first.", NamedTextColor.RED));
                return;
            }
            if (ap.getBoard() == null) {
                player.sendMessage(Component.text("Your board is not set up yet.", NamedTextColor.RED));
                return;
            }
            // Place in first available board cell
            boolean placed = ap.getBoard().placeOnFirstEmpty(ci);
            if (placed) {
                ap.removeFromBench(ci);
                player.sendMessage(Component.text(
                        "Deployed " + ci.getChampion().getDisplayName() + " to your board!",
                        NamedTextColor.GREEN));
                plugin.getUIManager().refreshBench(ap);
            } else {
                player.sendMessage(Component.text(
                        "No empty cells on the board!", NamedTextColor.RED));
            }
        }
    }
}
