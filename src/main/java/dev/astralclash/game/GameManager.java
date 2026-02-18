package dev.astralclash.game;

import dev.astralclash.AstralClash;
import dev.astralclash.board.BoardManager;
import dev.astralclash.player.ArenaPlayer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Top-level game controller. Manages the full game lifecycle:
 * WAITING → PLANNING → COMBAT → RESULTS → (repeat) → ENDED.
 *
 * <p>Uses a Bukkit scheduler task to drive phase transitions with proper timers.
 */
public class GameManager {

    private final AstralClash  plugin;
    private final GameLobby    lobby;
    private final BoardManager boardManager;
    private       RoundManager roundManager;

    private GamePhase          phase   = GamePhase.WAITING;
    private final List<ArenaPlayer> activePlayers = new ArrayList<>();
    private BukkitTask              phaseTask;

    public GameManager(AstralClash plugin) {
        this.plugin       = plugin;
        this.lobby        = new GameLobby(plugin);
        this.boardManager = new BoardManager(plugin);
    }

    // ── Lobby ─────────────────────────────────────────────────────────────────

    public boolean joinLobby(Player player) {
        if (plugin.getPlayerManager().isInGame(player.getUniqueId())) {
            player.sendMessage(Component.text("You are already in a game!", NamedTextColor.RED));
            return false;
        }
        return lobby.addPlayer(player);
    }

    public boolean leaveLobby(Player player) {
        return lobby.removePlayer(player.getUniqueId());
    }

    // ── Game start ────────────────────────────────────────────────────────────

    /**
     * Called by {@link GameLobby} when enough players have been collected.
     */
    public void startGame(List<UUID> playerUuids) {
        if (phase != GamePhase.WAITING) return;

        roundManager = new RoundManager(plugin);

        for (int i = 0; i < playerUuids.size(); i++) {
            Player p = plugin.getServer().getPlayer(playerUuids.get(i));
            if (p == null || !p.isOnline()) continue;

            ArenaPlayer ap = plugin.getPlayerManager().createArenaPlayer(p);
            boardManager.createBoard(ap, i);
            activePlayers.add(ap);

            ap.setShop(plugin.getShopManager().rollShop(ap));
        }

        broadcast(Component.text("═══ AstralClash — Game Starting! ═══",
                NamedTextColor.LIGHT_PURPLE));
        broadcast(Component.text(activePlayers.size() + " players are competing!",
                NamedTextColor.YELLOW));

        startPlanningPhase();
    }

    // ── Phase management ──────────────────────────────────────────────────────

    private void startPlanningPhase() {
        phase = GamePhase.PLANNING;
        int duration = plugin.getConfigManager().getPlanningDuration();
        broadcast(Component.text("Planning phase — " + duration + "s to arrange your board!",
                NamedTextColor.AQUA));

        for (ArenaPlayer ap : activePlayers) {
            plugin.getShopManager().rollShop(ap);
            plugin.getUIManager().openShop(ap);
        }

        // Update HUD
        plugin.getUIManager().broadcastHUD(activePlayers);

        phaseTask = plugin.getServer().getScheduler().runTaskLater(plugin,
                this::startCombatPhase, duration * 20L);
    }

    private void startCombatPhase() {
        phase = GamePhase.COMBAT;
        broadcast(Component.text("⚔ Combat! ⚔", NamedTextColor.RED));

        // Close all open shop UIs
        for (ArenaPlayer ap : activePlayers) {
            plugin.getUIManager().closeShop(ap);
        }

        // Run combat asynchronously to avoid blocking the main thread
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            List<ArenaPlayer> eliminated = roundManager.executeRound(activePlayers);

            // Back to main thread for Bukkit API calls
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                activePlayers.removeAll(eliminated);
                for (ArenaPlayer ap : eliminated) {
                    ap.setPlacement(activePlayers.size() + 1);
                    ap.getStats().incrementGamesPlayed();
                    ap.getStats().updatePlace(ap.getPlacement());
                    plugin.getPlayerManager().removeArenaPlayer(ap.getUuid());
                    boardManager.removeBoard(ap.getUuid());
                }
                startResultsPhase();
            });
        });
    }

    private void startResultsPhase() {
        phase = GamePhase.RESULTS;

        // Check win condition
        if (activePlayers.size() <= 1) {
            endGame();
            return;
        }

        broadcast(Component.text("Round " + roundManager.getRoundNumber() + " complete!",
                NamedTextColor.YELLOW));
        plugin.getUIManager().broadcastHUD(activePlayers);

        int intermission = plugin.getConfigManager().getRoundIntermission();
        phaseTask = plugin.getServer().getScheduler().runTaskLater(plugin,
                this::startPlanningPhase, intermission * 20L);
    }

    private void endGame() {
        phase = GamePhase.ENDED;

        if (activePlayers.size() == 1) {
            ArenaPlayer winner = activePlayers.get(0);
            winner.setPlacement(1);
            winner.getStats().incrementGamesPlayed();
            winner.getStats().incrementGamesWon();
            winner.getStats().updatePlace(1);
            broadcast(Component.text("🏆 " + winner.getPlayer().getName() + " wins AstralClash!",
                    NamedTextColor.GOLD));
        } else {
            broadcast(Component.text("The game ended in a draw!", NamedTextColor.YELLOW));
        }

        // Cleanup
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (ArenaPlayer ap : activePlayers) {
                plugin.getPlayerManager().removeArenaPlayer(ap.getUuid());
            }
            activePlayers.clear();
            boardManager.removeAll();
            phase = GamePhase.WAITING;
        }, 100L); // 5 seconds
    }

    // ── Utility ──────────────────────────────────────────────────────────────

    public void shutdown() {
        if (phaseTask != null) phaseTask.cancel();
        for (ArenaPlayer ap : activePlayers) {
            plugin.getPlayerManager().removeArenaPlayer(ap.getUuid());
        }
        activePlayers.clear();
        boardManager.removeAll();
    }

    private void broadcast(Component msg) {
        for (ArenaPlayer ap : activePlayers) {
            ap.getPlayer().sendMessage(msg);
        }
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public GamePhase            getPhase()          { return phase; }
    public GameLobby            getLobby()          { return lobby; }
    public List<ArenaPlayer>    getActivePlayers()  { return Collections.unmodifiableList(activePlayers); }
    public RoundManager         getRoundManager()   { return roundManager; }
    public BoardManager         getBoardManager()   { return boardManager; }
}
