package dev.astralclash.game;

import dev.astralclash.AstralClash;
import dev.astralclash.board.BoardManager;
import dev.astralclash.bot.BotArenaPlayer;
import dev.astralclash.bot.BotPlayer;
import dev.astralclash.player.ArenaPlayer;
import dev.astralclash.player.PlayerStats;
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

        int playerIndex = 0;
        
        // Process real players
        for (UUID uuid : playerUuids) {
            Player p = plugin.getServer().getPlayer(uuid);
            if (p != null && p.isOnline()) {
                // Real player
                ArenaPlayer ap = plugin.getPlayerManager().createArenaPlayer(p);
                boardManager.createBoard(ap, playerIndex);
                activePlayers.add(ap);
                ap.setShop(plugin.getShopManager().rollShop(ap));
                
                // Give game items (menu opener, etc.)
                dev.astralclash.items.GameItems.giveGameItems(p);
                
                playerIndex++;
            } else {
                // Bot player
                BotPlayer bot = plugin.getBotManager().getBot(uuid);
                if (bot != null) {
                    ArenaPlayer ap = createArenaPlayerForBot(bot);
                    boardManager.createBoard(ap, playerIndex);
                    activePlayers.add(ap);
                    ap.setShop(plugin.getShopManager().rollShop(ap));
                    
                    // Initialize bot with starting gold and shop
                    bot.setGold(plugin.getConfigManager().getStartingGold());
                    bot.setShop(ap.getShop());
                    
                    playerIndex++;
                }
            }
        }

        broadcast(Component.text("═══ AstralClash — Game Starting! ═══",
                NamedTextColor.LIGHT_PURPLE));
        broadcast(Component.text(activePlayers.size() + " players are competing!",
                NamedTextColor.YELLOW));

        startPlanningPhase();
    }
    
    /**
     * Creates an ArenaPlayer wrapper for a BotPlayer so bots can participate in the game.
     */
    private ArenaPlayer createArenaPlayerForBot(BotPlayer bot) {
        // Create fake PlayerStats for the bot
        PlayerStats stats = new PlayerStats(bot.getUuid(), bot.getName());
        stats.setUsername(bot.getName());
        
        // Create a wrapper ArenaPlayer that syncs with BotPlayer
        ArenaPlayer ap = new BotArenaPlayer(plugin, bot, stats);
        plugin.getPlayerManager().registerBotArenaPlayer(bot.getUuid(), ap);
        return ap;
    }

    // ── Phase management ──────────────────────────────────────────────────────

    private void startPlanningPhase() {
        phase = GamePhase.PLANNING;
        int duration = plugin.getConfigManager().getPlanningDuration();
        broadcast(Component.text("Planning phase — " + duration + "s to arrange your board!",
                NamedTextColor.AQUA));

        for (ArenaPlayer ap : activePlayers) {
            plugin.getShopManager().rollShop(ap);
            if (ap.getPlayer() != null) {
                // Only open shop for real players, not bots
                plugin.getUIManager().openShop(ap);
                plugin.getCombatUI().renderPlanningScoreboard(ap, activePlayers);
            }
        }

        // Update HUD
        plugin.getUIManager().broadcastHUD(activePlayers);
        
        // Make bots plan their moves and deploy
        for (ArenaPlayer ap : activePlayers) {
            if (ap instanceof BotArenaPlayer botAp) {
                BotPlayer bot = botAp.getBot();
                bot.planPhase();
                // Deploy bots' champions to their board
                if (ap.getBoard() != null) {
                    bot.deployToBoard(ap.getBoard());
                    // Spawn models for bot's deployed champions
                    if (plugin.getModelEngineService().isAvailable()) {
                        for (dev.astralclash.champion.ChampionInstance ci : ap.getBoard().getDeployedChampions()) {
                            var cell = ci.getCell();
                            if (cell != null) {
                                var loc = cell.getWorldLocation().clone().add(1.5, 1.0, 1.5);
                                plugin.getModelEngineService().spawnModel(ci, loc);
                            }
                        }
                    }
                }
            }
        }

        phaseTask = plugin.getServer().getScheduler().runTaskLater(plugin,
                this::startCombatPhase, duration * 20L);
    }

    private void startCombatPhase() {
        phase = GamePhase.COMBAT;
        int roundNum = roundManager != null ? roundManager.getRoundNumber() : 1;
        broadcast(Component.text("⚔ Combat! ⚔", NamedTextColor.RED));

        // Close all open shop UIs
        for (ArenaPlayer ap : activePlayers) {
            plugin.getUIManager().closeShop(ap);
        }
        
        // Next-gen feel: title, sound, particles per player (configurable)
        var cfg = plugin.getConfigManager();
        for (ArenaPlayer ap : activePlayers) {
            if (ap.getPlayer() == null || !ap.getPlayer().isOnline()) continue;
            if (cfg.isTitleCombat()) {
                ap.getPlayer().showTitle(net.kyori.adventure.title.Title.title(
                    net.kyori.adventure.text.Component.text("Round " + roundNum, net.kyori.adventure.text.format.NamedTextColor.GOLD, net.kyori.adventure.text.format.TextDecoration.BOLD),
                    net.kyori.adventure.text.Component.text("⚔ COMBAT ⚔", net.kyori.adventure.text.format.NamedTextColor.RED),
                    net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(300), java.time.Duration.ofMillis(1500), java.time.Duration.ofMillis(400))));
            }
            if (cfg.isSoundCombatStart()) {
                ap.getPlayer().playSound(ap.getPlayer().getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.2f);
            }
            ap.getPlayer().getWorld().spawnParticle(org.bukkit.Particle.CRIT, ap.getPlayer().getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.15);
        }
        
        // Show combat UI with turn order and synergies
        var combatUI = plugin.getCombatUI();
        for (ArenaPlayer ap : activePlayers) {
            var myTeam = ap.getBoard() != null ? ap.getBoard().getDeployedChampions() : List.<dev.astralclash.champion.ChampionInstance>of();
            combatUI.renderCombatScoreboard(ap, myTeam, List.of(), null);
            combatUI.showCombatBossBar(ap, "⚔ COMBAT ⚔ " + roundNum + " ⚔", 60);
        }

        // Run combat asynchronously to avoid blocking the main thread
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            List<ArenaPlayer> eliminated = roundManager.executeRound(activePlayers);

            // Back to main thread for Bukkit API calls
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                // Hide combat UI
                for (ArenaPlayer ap : activePlayers) {
                    plugin.getCombatUI().hideCombatBossBar(ap);
                }
                
                activePlayers.removeAll(eliminated);
                for (ArenaPlayer ap : eliminated) {
                    ap.setPlacement(activePlayers.size() + 1);
                    ap.getStats().incrementGamesPlayed();
                    ap.getStats().updatePlace(ap.getPlacement());
                    plugin.getPlayerManager().removeArenaPlayer(ap.getUuid());
                    boardManager.removeBoard(ap.getUuid());
                    plugin.getCombatUI().clearCombatUI(ap.getUuid());
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
            String winnerName = winner.getPlayer() != null 
                ? winner.getPlayer().getName() 
                : winner.getStats().getUsername();
            broadcast(Component.text("🏆 " + winnerName + " wins AstralClash!",
                    NamedTextColor.GOLD));
            if (winner.getPlayer() != null && winner.getPlayer().isOnline()) {
                if (plugin.getConfigManager().isTitleCombat()) {
                    winner.getPlayer().showTitle(net.kyori.adventure.title.Title.title(
                        net.kyori.adventure.text.Component.text("VICTORY!", net.kyori.adventure.text.format.NamedTextColor.GOLD, net.kyori.adventure.text.format.TextDecoration.BOLD),
                        net.kyori.adventure.text.Component.text("You won AstralClash!", net.kyori.adventure.text.format.NamedTextColor.YELLOW),
                        net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(500), java.time.Duration.ofMillis(3000), java.time.Duration.ofMillis(500))));
                }
                if (plugin.getConfigManager().isSoundVictory()) {
                    winner.getPlayer().playSound(winner.getPlayer().getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.0f);
                }
            }
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
            if (ap.getPlayer() != null) {
                ap.getPlayer().sendMessage(msg);
            }
            // Bots don't receive messages (they're AI)
        }
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public GamePhase            getPhase()          { return phase; }
    public GameLobby            getLobby()          { return lobby; }
    public List<ArenaPlayer>    getActivePlayers()  { return Collections.unmodifiableList(activePlayers); }
    public RoundManager         getRoundManager()   { return roundManager; }
    public BoardManager         getBoardManager()   { return boardManager; }
}
