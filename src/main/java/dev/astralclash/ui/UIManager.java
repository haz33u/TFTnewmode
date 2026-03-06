package dev.astralclash.ui;

import dev.astralclash.AstralClash;
import dev.astralclash.player.ArenaPlayer;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Central UI orchestrator. Manages:
 * <ul>
 *   <li>Shop inventory GUI (open/close/refresh)</li>
 *   <li>Bench inventory GUI (open/close/refresh)</li>
 *   <li>Action-bar HUD tick loop</li>
 *   <li>Boss bar phase timer</li>
 *   <li>Scoreboard sidebar (via HUDRenderer)</li>
 * </ul>
 */
public class UIManager {

    private final AstralClash plugin;
    private final ShopUI          shopUI;
    private final BenchUI         benchUI;
    private final MainMenuUI      mainMenuUI;
    private final HUDRenderer      hudRenderer;
    private final ArenaViewManager arenaViewManager;

    /** Active boss bars per player. */
    private final Map<UUID, BossBar> bossBars = new HashMap<>();

    /** Periodic HUD update task. */
    private BukkitTask hudTask;

    public UIManager(AstralClash plugin) {
        this.plugin          = plugin;
        this.shopUI          = new ShopUI(plugin);
        this.benchUI         = new BenchUI(plugin);
        this.mainMenuUI      = new MainMenuUI(plugin);
        this.hudRenderer     = new HUDRenderer(plugin);
        this.arenaViewManager = new ArenaViewManager(plugin);
        startHudLoop();
    }

    // ── HUD loop ─────────────────────────────────────────────────────────────

    private void startHudLoop() {
        int interval = plugin.getConfigManager().getHudUpdateInterval();
        hudTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            List<ArenaPlayer> players = List.copyOf(
                    plugin.getPlayerManager().getAllArenaPlayers());
            for (ArenaPlayer ap : players) {
                hudRenderer.renderActionBar(ap);
            }
        }, interval, interval);
    }

    /**
     * Renders both action bar and sidebar scoreboard for all players.
     * Call this after phase transitions where state visibly changes.
     */
    public void broadcastHUD(List<ArenaPlayer> players) {
        for (ArenaPlayer ap : players) {
            if (ap.getPlayer() == null) continue; // Skip bots for HUD
            hudRenderer.renderActionBar(ap);
            hudRenderer.renderScoreboard(ap, players);
        }
    }

    // ── Shop ─────────────────────────────────────────────────────────────────

    public void openShop(ArenaPlayer ap) {
        if (ap.getPlayer() == null) return; // Bots don't open shops
        shopUI.openShop(ap);
    }

    public void closeShop(ArenaPlayer ap) {
        if (ap.getPlayer() == null) return; // Bots don't have inventories
        ap.getPlayer().closeInventory();
    }

    public void refreshShop(ArenaPlayer ap) {
        if (ap.getPlayer() == null) return; // Bots don't have inventories
        var topInv = ap.getPlayer().getOpenInventory().getTopInventory();
        if (topInv.getViewers().contains(ap.getPlayer())) {
            shopUI.populateShop(topInv, ap);
        }
    }

    // ── Bench ─────────────────────────────────────────────────────────────────

    public void openBench(ArenaPlayer ap) {
        if (ap.getPlayer() == null) return; // Bots don't open benches
        benchUI.openBench(ap);
    }

    public void refreshBench(ArenaPlayer ap) {
        if (ap.getPlayer() == null) return; // Bots don't have inventories
        var topInv = ap.getPlayer().getOpenInventory().getTopInventory();
        if (topInv.getViewers().contains(ap.getPlayer())) {
            benchUI.populateBench(topInv, ap);
        }
    }

    // ── Boss bar ─────────────────────────────────────────────────────────────

    /**
     * Shows a countdown boss bar to the player.
     *
     * @param player  the arena player
     * @param title   bar title
     * @param seconds total seconds for the timer
     */
    public void showTimerBar(ArenaPlayer player, String title, int seconds) {
        BossBar bar = BossBar.bossBar(
                Component.text(title, NamedTextColor.AQUA),
                1.0f,
                BossBar.Color.BLUE,
                BossBar.Overlay.PROGRESS);

        bossBars.put(player.getUuid(), bar);
        player.getPlayer().showBossBar(bar);

        final int[] remaining = {seconds};
        plugin.getServer().getScheduler().runTaskTimer(plugin,
                task -> {
                    remaining[0]--;
                    if (remaining[0] <= 0 || !player.getPlayer().isOnline()) {
                        task.cancel();
                        player.getPlayer().hideBossBar(bar);
                        bossBars.remove(player.getUuid());
                        return;
                    }
                    bar.progress(remaining[0] / (float) seconds);
                    bar.name(Component.text(title + " — " + remaining[0] + "s",
                            NamedTextColor.AQUA));
                }, 0L, 20L);
    }

    public void clearTimerBar(ArenaPlayer ap) {
        BossBar bar = bossBars.remove(ap.getUuid());
        if (bar != null) ap.getPlayer().hideBossBar(bar);
    }

    // ── Player cleanup ────────────────────────────────────────────────────────

    /** Called when a player leaves or is eliminated — clears all their UI state. */
    public void onPlayerLeave(UUID uuid) {
        BossBar bar = bossBars.remove(uuid);
        var p = plugin.getServer().getPlayer(uuid);
        if (bar != null && p != null) p.hideBossBar(bar);
        hudRenderer.clearScoreboard(uuid);
        arenaViewManager.onPlayerLeave(uuid);
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void shutdown() {
        if (hudTask != null) hudTask.cancel();
        bossBars.forEach((uuid, bar) -> {
            var p = plugin.getServer().getPlayer(uuid);
            if (p != null) p.hideBossBar(bar);
        });
        bossBars.clear();
        hudRenderer.clearAllScoreboards();
        arenaViewManager.cleanup();
    }

    // ── Main Menu ──────────────────────────────────────────────────────────────

    public void openMainMenu(ArenaPlayer ap) {
        if (ap.getPlayer() == null) return;
        mainMenuUI.openMenu(ap);
    }

    public void refreshMainMenu(ArenaPlayer ap) {
        if (ap.getPlayer() == null) return;
        var topInv = ap.getPlayer().getOpenInventory().getTopInventory();
        if (topInv.getViewers().contains(ap.getPlayer())) {
            mainMenuUI.populateMenu(topInv, ap);
        }
    }

    // ── Arena View ─────────────────────────────────────────────────────────

    public void toggleArenaView(ArenaPlayer ap) {
        arenaViewManager.toggleArenaView(ap);
    }

    public boolean isInArenaView(java.util.UUID uuid) {
        return arenaViewManager.isInArenaView(uuid);
    }

    public void stopArenaView(ArenaPlayer ap) {
        arenaViewManager.stopArenaView(ap);
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public ShopUI          getShopUI()          { return shopUI; }
    public BenchUI         getBenchUI()         { return benchUI; }
    public MainMenuUI      getMainMenuUI()      { return mainMenuUI; }
    public HUDRenderer     getHudRenderer()     { return hudRenderer; }
    public ArenaViewManager getArenaViewManager() { return arenaViewManager; }
}
