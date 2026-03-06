package dev.astralclash.bot;

import dev.astralclash.AstralClash;
import dev.astralclash.board.Board;
import dev.astralclash.champion.ChampionInstance;
import dev.astralclash.player.ArenaPlayer;
import dev.astralclash.player.PlayerStats;
import dev.astralclash.shop.Shop;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * Wrapper that makes a BotPlayer compatible with ArenaPlayer interface.
 * Syncs game state between BotPlayer and ArenaPlayer.
 */
public class BotArenaPlayer extends ArenaPlayer {

    private final BotPlayer bot;
    private final AstralClash plugin;

    public BotArenaPlayer(AstralClash plugin, BotPlayer bot, PlayerStats stats) {
        // Pass null Player - ArenaPlayer now supports it for bots
        super(plugin, null, stats);
        this.bot = bot;
        this.plugin = plugin;
        
        // Sync initial state
        setGold(bot.getGold());
        setHealth(bot.getHealth());
    }

    @Override
    public Player getPlayer() {
        // Bots don't have real players, return null
        // Methods that need Player should check for null
        return null;
    }

    @Override
    public int getGold() {
        return bot.getGold();
    }

    @Override
    public void setGold(int v) {
        super.setGold(v);
        bot.setGold(v);
    }

    @Override
    public boolean spendGold(int amount) {
        if (bot.getGold() < amount) return false;
        bot.setGold(bot.getGold() - amount);
        super.setGold(bot.getGold());
        return true;
    }

    @Override
    public void addGold(int amount) {
        bot.setGold(Math.min(bot.getGold() + amount, 99));
        super.setGold(bot.getGold());
    }

    @Override
    public int getHealth() {
        return bot.getHealth();
    }

    @Override
    public void setHealth(int v) {
        super.setHealth(v);
        bot.setHealth(v);
    }

    @Override
    public void takeDamage(int dmg) {
        bot.takeDamage(dmg);
        super.setHealth(bot.getHealth());
    }

    @Override
    public int getLevel() {
        return bot.getLevel();
    }

    @Override
    public int getXp() {
        return bot.getXp();
    }

    @Override
    public void addXp(int amount) {
        bot.setGold(bot.getGold()); // Sync
        // Bot handles XP internally
    }

    @Override
    public int getBoardSizeLimit() {
        return bot.getBoardSizeLimit();
    }

    @Override
    public List<ChampionInstance> getBench() {
        return bot.getBench();
    }

    @Override
    public boolean hasBenchSpace() {
        return bot.getBench().size() < plugin.getConfigManager().getBenchSize();
    }

    @Override
    public void addToBench(ChampionInstance ci) {
        bot.getBench().add(ci);
    }

    @Override
    public boolean removeFromBench(ChampionInstance ci) {
        return bot.getBench().remove(ci);
    }

    @Override
    public int getDeployedCount() {
        Board board = super.getBoard();
        if (board != null) {
            return board.getDeployedCount();
        }
        return bot.getDeployed().size();
    }
    
    @Override
    public Board getBoard() {
        return super.getBoard();
    }

    @Override
    public Shop getShop() {
        return bot.getShop();
    }

    @Override
    public void setShop(Shop shop) {
        super.setShop(shop);
        bot.setShop(shop);
    }

    @Override
    public boolean isShopLocked() {
        return bot.isShopLocked();
    }

    @Override
    public void setShopLocked(boolean v) {
        super.setShopLocked(v);
        bot.setShopLocked(v);
    }

    public BotPlayer getBot() {
        return bot;
    }
}
