package dev.astralclash.listeners;

import dev.astralclash.AstralClash;
import dev.astralclash.champion.ChampionInstance;
import dev.astralclash.game.GamePhase;
import dev.astralclash.items.GameItems;
import dev.astralclash.player.ArenaPlayer;
import dev.astralclash.ui.BenchUI;
import dev.astralclash.ui.MainMenuUI;
import dev.astralclash.ui.ShopUI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

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
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        
        if (GameItems.isMenuOpener(item)) {
            ArenaPlayer ap = plugin.getPlayerManager().getArenaPlayer(player);
            if (ap == null) {
                player.sendMessage(Component.text("You are not in a game!", NamedTextColor.RED));
                return;
            }
            plugin.getUIManager().openMainMenu(ap);
            event.setCancelled(true);
            return;
        }
        if (GameItems.isExitArenaViewItem(item)) {
            ArenaPlayer ap = plugin.getPlayerManager().getArenaPlayer(player);
            if (ap != null && plugin.getUIManager().isInArenaView(player.getUniqueId())) {
                plugin.getUIManager().stopArenaView(ap);
                if (item.getAmount() <= 1) {
                    player.getInventory().setItemInMainHand(null);
                } else {
                    item.setAmount(item.getAmount() - 1);
                }
                event.setCancelled(true);
            }
        }
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
        } else if (viewTitle.equals(MainMenuUI.MENU_TITLE_COMPONENT)) {
            handleMainMenuClick(event, player);
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
            return;
        }
        
        if (slot == ShopUI.getBenchSlot()) {
            player.closeInventory();
            plugin.getUIManager().openBench(ap);
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
                // Spawn 3D model on the board (centre of cell, on top of carpet)
                var cell = ci.getCell();
                if (cell != null && plugin.getModelEngineService().isAvailable()) {
                    var loc = cell.getWorldLocation().clone().add(1.5, 1.0, 1.5); // cell centre, on carpet
                    plugin.getModelEngineService().spawnModel(ci, loc);
                }
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

    // ── Main Menu click handler ────────────────────────────────────────────────

    private void handleMainMenuClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);

        ArenaPlayer ap = plugin.getPlayerManager().getArenaPlayer(player);
        if (ap == null) return;

        int slot = event.getRawSlot();

        if (slot == MainMenuUI.getShopSlot()) {
            player.closeInventory();
            plugin.getUIManager().openShop(ap);
        } else if (slot == MainMenuUI.getBenchSlot()) {
            player.closeInventory();
            plugin.getUIManager().openBench(ap);
        } else if (slot == MainMenuUI.getBoardSlot()) {
            player.sendMessage(Component.text("§bYour Board: §f" + ap.getDeployedCount() + "/" + 
                ap.getBoardSizeLimit() + " units deployed", NamedTextColor.AQUA));
        } else if (slot == MainMenuUI.getStatsSlot()) {
            player.closeInventory();
            plugin.getUIManager().getHudRenderer().renderScoreboard(ap, 
                plugin.getGameManager().getActivePlayers());
        } else if (slot == MainMenuUI.getArenaViewSlot()) {
            player.closeInventory();
            plugin.getUIManager().toggleArenaView(ap);
        } else if (slot == MainMenuUI.getLeaveSlot()) {
            player.closeInventory();
            plugin.getGameManager().leaveLobby(player);
            plugin.getPlayerManager().removeArenaPlayer(player.getUniqueId());
            player.sendMessage(Component.text("§eYou left the game.", NamedTextColor.YELLOW));
        } else if (slot == MainMenuUI.getCloseSlot()) {
            player.closeInventory();
        }
    }
}
