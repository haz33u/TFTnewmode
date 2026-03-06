package dev.astralclash.player;

import dev.astralclash.AstralClash;
import dev.astralclash.board.Board;
import dev.astralclash.champion.ChampionInstance;
import dev.astralclash.shop.Shop;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents a player inside an active AstralClash game.
 * Holds the session-scoped state (gold, HP, XP, bench, board, shop).
 */
public class ArenaPlayer {

    private final AstralClash plugin;
    private final UUID uuid;
    private final Player player;

    // ── Persistent stats ─────────────────────────────────────────────────────
    private final PlayerStats stats;

    // ── Game state ───────────────────────────────────────────────────────────
    private int  gold;
    private int  health;
    private int  xp;
    private int  level;
    private int  placement = -1;   // final placement (1 = winner)

    // Bench: champions not deployed on the board
    private final List<ChampionInstance> bench = new ArrayList<>();

    // The board this player controls (4x7 grid)
    private Board board;

    // Current shop offerings
    private Shop shop;

    // Locked = shop doesn't reroll next round
    private boolean shopLocked = false;

    // Streak tracking (for economy bonus)
    private int winStreak  = 0;
    private int lossStreak = 0;

    // ── Constructor ──────────────────────────────────────────────────────────

    public ArenaPlayer(AstralClash plugin, Player player, PlayerStats stats) {
        this.plugin  = plugin;
        this.uuid    = player != null ? player.getUniqueId() : stats.getUuid();
        this.player  = player;
        this.stats   = stats;

        this.gold    = plugin.getConfigManager().getStartingGold();
        this.health  = plugin.getConfigManager().getStartingHealth();
        this.xp      = 0;
        this.level   = 1;
    }

    // ── Gold ─────────────────────────────────────────────────────────────────

    public boolean spendGold(int amount) {
        if (gold < amount) return false;
        gold -= amount;
        return true;
    }

    public void addGold(int amount) { gold = Math.min(gold + amount, 99); }

    /** Calculates and applies interest for this round. */
    public int calculateInterest() {
        int threshold = plugin.getConfigManager().getGoldInterestThresh();
        int cap       = plugin.getConfigManager().getGoldInterestCap();
        int interest  = Math.min(gold / threshold, cap);
        addGold(interest);
        return interest;
    }

    // ── XP / Level ───────────────────────────────────────────────────────────

    /** Maximum board size (deployed units) for this level. */
    public int getBoardSizeLimit() { return level; }

    public void addXp(int amount) {
        xp += amount;
        // XP thresholds to level up (TFT-style): 2,6,10,20,36,56,80,100
        int[] thresholds = {0, 2, 6, 10, 20, 36, 56, 80, 100};
        while (level < 9 && xp >= thresholds[level]) {
            xp -= thresholds[level];
            level++;
        }
    }

    // ── Health ───────────────────────────────────────────────────────────────

    /** Returns true if this player has been eliminated. */
    public boolean isDead() { return health <= 0; }

    public void takeDamage(int dmg) {
        health = Math.max(0, health - dmg);
    }

    // ── Bench ─────────────────────────────────────────────────────────────────

    public boolean hasBenchSpace() {
        return bench.size() < plugin.getConfigManager().getBenchSize();
    }

    public void addToBench(ChampionInstance ci) { bench.add(ci); }
    public boolean removeFromBench(ChampionInstance ci) { return bench.remove(ci); }

    // ── Streak ───────────────────────────────────────────────────────────────

    public void recordWin() {
        winStreak++;
        lossStreak = 0;
    }

    public void recordLoss() {
        lossStreak++;
        winStreak = 0;
    }

    /** Bonus gold from win/loss streak (TFT-style). */
    public int getStreakBonus() {
        int streak = Math.max(winStreak, lossStreak);
        if (streak >= 9) return 3;
        if (streak >= 6) return 2;
        if (streak >= 3) return 1;
        return 0;
    }

    // ── Deployed unit count ──────────────────────────────────────────────────

    public int getDeployedCount() {
        if (board == null) return 0;
        return board.getDeployedCount();
    }

    // ── Getters / Setters ────────────────────────────────────────────────────

    public UUID          getUuid()                      { return uuid; }
    public Player        getPlayer()                    { return player; }
    public PlayerStats   getStats()                     { return stats; }

    public int           getGold()                      { return gold; }
    public void          setGold(int v)                 { gold = v; }

    public int           getHealth()                    { return health; }
    public void          setHealth(int v)               { health = v; }

    public int           getXp()                        { return xp; }
    public int           getLevel()                     { return level; }

    public int           getPlacement()                 { return placement; }
    public void          setPlacement(int v)            { placement = v; }

    public List<ChampionInstance> getBench()            { return bench; }

    public Board         getBoard()                     { return board; }
    public void          setBoard(Board board)          { this.board = board; }

    public Shop          getShop()                      { return shop; }
    public void          setShop(Shop shop)             { this.shop = shop; }

    public boolean       isShopLocked()                 { return shopLocked; }
    public void          setShopLocked(boolean v)       { shopLocked = v; }

    public int           getWinStreak()                 { return winStreak; }
    public int           getLossStreak()                { return lossStreak; }
}
