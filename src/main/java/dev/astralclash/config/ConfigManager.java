package dev.astralclash.config;

import dev.astralclash.AstralClash;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Typed access to config.yml values.
 */
public class ConfigManager {

    private final AstralClash plugin;

    public ConfigManager(AstralClash plugin) {
        this.plugin = plugin;
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    public void reload() {
        plugin.reloadConfig();
    }

    // ── Database ─────────────────────────────────────────────────────────────

    public String  getDbType()     { return cfg().getString("database.type", "SQLITE").toUpperCase(); }
    public String  getDbHost()     { return cfg().getString("database.host", "localhost"); }
    public int     getDbPort()     { return cfg().getInt("database.port", 3306); }
    public String  getDbName()     { return cfg().getString("database.name", "astralclash"); }
    public String  getDbUser()     { return cfg().getString("database.user", "root"); }
    public String  getDbPassword() { return cfg().getString("database.password", ""); }
    public int     getPoolSize()   { return cfg().getInt("database.pool-size", 10); }

    // ── Game ─────────────────────────────────────────────────────────────────

    public int     getMinPlayers()          { return cfg().getInt("game.min-players", 2); }
    public int     getMaxPlayers()          { return cfg().getInt("game.max-players", 8); }
    public int     getLobbyCountdown()      { return cfg().getInt("game.lobby-countdown", 60); }
    public int     getPlanningDuration()    { return cfg().getInt("game.planning-duration", 35); }
    public int     getCombatDuration()      { return cfg().getInt("game.combat-duration", 60); }
    public int     getRoundIntermission()   { return cfg().getInt("game.round-intermission", 5); }

    public int     getBoardRows()           { return cfg().getInt("game.board-rows", 4); }
    public int     getBoardCols()           { return cfg().getInt("game.board-cols", 7); }
    public int     getBenchSize()           { return cfg().getInt("game.bench-size", 9); }

    public int     getStartingHealth()      { return cfg().getInt("game.starting-health", 100); }
    public int     getStartingGold()        { return cfg().getInt("game.starting-gold", 2); }

    public int     getGoldPerRound()        { return cfg().getInt("game.gold-per-round", 5); }
    public int     getGoldInterestCap()     { return cfg().getInt("game.gold-interest-cap", 5); }
    public int     getGoldInterestThresh()  { return cfg().getInt("game.gold-interest-threshold", 10); }

    public int     getXpPerRound()          { return cfg().getInt("game.xp-per-round", 2); }
    public int     getXpBuyCost()           { return cfg().getInt("game.xp-buy-cost", 4); }
    public int     getXpBuyAmount()         { return cfg().getInt("game.xp-buy-amount", 4); }

    public int     getRerollCost()          { return cfg().getInt("game.reroll-cost", 2); }
    public int     getLossDamageBase()      { return cfg().getInt("game.loss-damage-base", 2); }

    public String  getArenaWorld()          { return cfg().getString("game.arena-world", "astral_arena"); }
    public int     getPlatformSpacing()     { return cfg().getInt("game.platform-spacing", 64); }

    // ── Shop ─────────────────────────────────────────────────────────────────

    public int     getShopSlots()           { return cfg().getInt("shop.slots", 5); }
    public int     getPoolSize(int tier)    { return cfg().getInt("shop.pool-sizes." + tier, 18); }

    /** Returns odds array [tier1%, tier2%, tier3%, tier4%, tier5%] for a given player level. */
    public int[]   getShopOdds(int level) {
        String path = "shop.odds." + Math.max(1, Math.min(level, 9));
        var list = cfg().getIntegerList(path);
        if (list.size() == 5) return list.stream().mapToInt(Integer::intValue).toArray();
        return new int[]{100, 0, 0, 0, 0};
    }

    // ── UI ───────────────────────────────────────────────────────────────────

    public int     getHudUpdateInterval()   { return cfg().getInt("ui.hud-update-interval", 10); }
    public boolean isShowDamageNumbers()    { return cfg().getBoolean("ui.show-damage-numbers", true); }

    // ── ModelEngine ──────────────────────────────────────────────────────────

    public boolean isModelEngineEnabled()   { return cfg().getBoolean("modelengine.enabled", true); }
    public String  getAnimation(String key) {
        return cfg().getString("modelengine.animations." + key, key);
    }
}
