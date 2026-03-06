package dev.astralclash.bot;

import dev.astralclash.AstralClash;
import dev.astralclash.champion.Champion;
import dev.astralclash.champion.ChampionInstance;
import dev.astralclash.champion.ChampionTier;
import dev.astralclash.champion.StarLevel;
import dev.astralclash.player.ArenaPlayer;
import dev.astralclash.player.PlayerStats;
import dev.astralclash.shop.Shop;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * A bot player that simulates a real player for solo testing.
 * Bots automatically buy champions, place them on the board, and participate in combat.
 */
public class BotPlayer {

    private final UUID uuid;
    private final String name;
    private final AstralClash plugin;
    
    // Game state (mirrors ArenaPlayer fields)
    private int health = 100;
    private int gold = 0;
    private int level = 1;
    private int xp = 0;
    private boolean shopLocked = false;
    
    private Shop shop;
    private final List<ChampionInstance> bench = new ArrayList<>();
    private final List<ChampionInstance> deployed = new ArrayList<>();
    
    // AI difficulty (0.0 = random, 1.0 = optimal)
    private final double difficulty;
    private final Random rng = new Random();
    
    // Bot strategies
    public enum Strategy {
        AGGRESSIVE,    // Prioritizes high-damage units
        DEFENSIVE,     // Prioritizes tanks and healers
        BALANCED,      // Mix of both
        ECON          // Saves gold, levels fast
    }
    
    private final Strategy strategy;
    
    public BotPlayer(AstralClash plugin, String name, double difficulty) {
        this.uuid = UUID.randomUUID();
        this.name = name;
        this.plugin = plugin;
        this.difficulty = Math.max(0, Math.min(1, difficulty));
        this.strategy = Strategy.values()[rng.nextInt(Strategy.values().length)];
    }
    
    public BotPlayer(AstralClash plugin, String name) {
        this(plugin, name, 0.5); // Medium difficulty by default
    }
    
    /**
     * Called at the start of each planning phase.
     * Bot decides what to buy, sell, and how to position units.
     */
    public void planPhase() {
        // Income: base + interest
        int interest = Math.min(5, gold / 10);
        gold += 5 + interest;
        
        // Reroll shop if not locked - bots get a fresh random shop each phase
        if (!shopLocked) {
            shop = createBotShop();
        }
        
        // AI decision making based on difficulty
        if (rng.nextDouble() < difficulty) {
            makeOptimalDecisions();
        } else {
            makeRandomDecisions();
        }
    }
    
    private void makeOptimalDecisions() {
        // 1. Buy XP if we're behind on levels
        while (gold >= 4 && level < 9 && rng.nextDouble() < 0.3) {
            buyXp();
        }
        
        // 2. Buy champions from shop
        if (shop != null) {
            for (int i = 0; i < shop.size(); i++) {
                Champion c = shop.getSlot(i);
                if (c == null) continue;
                
                int cost = c.getTier().getCost();
                if (gold >= cost && shouldBuy(c)) {
                    buyChampion(i);
                }
            }
        }
        
        // 3. Try to combine champions for star-ups
        tryCombineChampions();
        
        // 4. Deploy best units
        deployOptimalUnits();
    }
    
    private void makeRandomDecisions() {
        // Random: 50% chance to buy each affordable champion
        if (shop != null) {
            for (int i = 0; i < shop.size(); i++) {
                Champion c = shop.getSlot(i);
                if (c == null) continue;
                
                int cost = c.getTier().getCost();
                if (gold >= cost && rng.nextBoolean()) {
                    buyChampion(i);
                }
            }
        }
        
        // Random deploy
        while (deployed.size() < getBoardSizeLimit() && !bench.isEmpty()) {
            ChampionInstance ci = bench.remove(rng.nextInt(bench.size()));
            deployed.add(ci);
        }
    }
    
    private boolean shouldBuy(Champion c) {
        // Check if we can combine this champion
        long sameChampCount = bench.stream()
            .filter(ci -> ci.getChampion().getId().equals(c.getId()))
            .count();
        sameChampCount += deployed.stream()
            .filter(ci -> ci.getChampion().getId().equals(c.getId()))
            .count();
        
        if (sameChampCount >= 2) return true; // Can combine to 2-star
        
        // Strategy-based buying
        return switch (strategy) {
            case AGGRESSIVE -> c.getBaseAttackDamage() > 60 || c.getTier().getCost() >= 3;
            case DEFENSIVE -> c.getBaseHp() > 700 || c.getTraits().stream()
                .anyMatch(t -> t.name().contains("PRESERVATION") || t.name().contains("ABUNDANCE"));
            case ECON -> c.getTier().getCost() <= 2 && gold > 30;
            case BALANCED -> bench.size() < 5 || sameChampCount >= 1;
        };
    }
    
    private void buyChampion(int slot) {
        if (shop == null) return;
        Champion c = shop.getSlot(slot);
        if (c == null) return;
        
        int cost = c.getTier().getCost();
        if (gold < cost) return;
        if (bench.size() >= 9) return;
        
        gold -= cost;
        ChampionInstance ci = new ChampionInstance(c, StarLevel.ONE, uuid);
        bench.add(ci);
        shop.clearSlot(slot);
    }
    
    private void buyXp() {
        if (gold < 4) return;
        gold -= 4;
        xp += 4;
        
        int xpNeeded = level * 4 + 4; // Simple formula
        while (xp >= xpNeeded && level < 9) {
            xp -= xpNeeded;
            level++;
            xpNeeded = level * 4 + 4;
        }
    }
    
    private void tryCombineChampions() {
        Map<String, List<ChampionInstance>> byChampion = new HashMap<>();
        
        for (ChampionInstance ci : bench) {
            byChampion.computeIfAbsent(ci.getChampion().getId(), k -> new ArrayList<>()).add(ci);
        }
        for (ChampionInstance ci : deployed) {
            byChampion.computeIfAbsent(ci.getChampion().getId(), k -> new ArrayList<>()).add(ci);
        }
        
        for (List<ChampionInstance> units : byChampion.values()) {
            // Group by star level
            List<ChampionInstance> oneStar = units.stream()
                .filter(ci -> ci.getStarLevel() == StarLevel.ONE)
                .toList();
            
            while (oneStar.size() >= 3) {
                // Combine 3 1-star into 1 2-star
                ChampionInstance combined = oneStar.get(0);
                combined.starUp();
                
                for (int i = 1; i < 3; i++) {
                    ChampionInstance toRemove = oneStar.get(i);
                    bench.remove(toRemove);
                    deployed.remove(toRemove);
                }
                
                oneStar = units.stream()
                    .filter(ci -> ci.getStarLevel() == StarLevel.ONE)
                    .toList();
            }
        }
    }
    
    private void deployOptimalUnits() {
        // This method is called by planPhase() but we need to actually place on board
        // The actual deployment happens via BotArenaPlayer syncing with board
        // So we just prepare the list here
        bench.sort((a, b) -> {
            int starDiff = b.getStarLevel().getStars() - a.getStarLevel().getStars();
            if (starDiff != 0) return starDiff;
            return b.getChampion().getTier().getCost() - a.getChampion().getTier().getCost();
        });
        
        // Deploy until board is full
        while (deployed.size() < getBoardSizeLimit() && !bench.isEmpty()) {
            deployed.add(bench.remove(0));
        }
    }
    
    /**
     * Actually places deployed champions on the board.
     * Called after planPhase() completes.
     */
    public void deployToBoard(dev.astralclash.board.Board board) {
        if (board == null) return;
        
        // Clear board first
        board.clearAll();
        deployed.clear();
        
        // Sort bench by priority
        bench.sort((a, b) -> {
            int starDiff = b.getStarLevel().getStars() - a.getStarLevel().getStars();
            if (starDiff != 0) return starDiff;
            return b.getChampion().getTier().getCost() - a.getChampion().getTier().getCost();
        });
        
        // Deploy best units to board
        int limit = getBoardSizeLimit();
        int deployedCount = 0;
        for (int i = 0; i < bench.size() && deployedCount < limit; i++) {
            ChampionInstance ci = bench.get(i);
            if (board.placeOnFirstEmpty(ci)) {
                bench.remove(i);
                deployed.add(ci);
                deployedCount++;
                i--; // Adjust index after removal
            }
        }
    }
    
    public int getBoardSizeLimit() {
        return Math.min(level, 9);
    }
    
    // ── Getters & Setters ────────────────────────────────────────────────────
    
    public UUID getUuid() { return uuid; }
    public String getName() { return name; }
    public int getHealth() { return health; }
    public void setHealth(int h) { health = Math.max(0, h); }
    public int getGold() { return gold; }
    public void setGold(int g) { gold = g; }
    public int getLevel() { return level; }
    public int getXp() { return xp; }
    public Shop getShop() { return shop; }
    public void setShop(Shop s) { shop = s; }
    public List<ChampionInstance> getBench() { return bench; }
    public List<ChampionInstance> getDeployed() { return Collections.unmodifiableList(deployed); }
    public Strategy getStrategy() { return strategy; }
    public double getDifficulty() { return difficulty; }
    public boolean isShopLocked() { return shopLocked; }
    public void setShopLocked(boolean v) { shopLocked = v; }
    
    public void takeDamage(int damage) {
        health = Math.max(0, health - damage);
    }
    
    public boolean isEliminated() {
        return health <= 0;
    }
    
    /**
     * Creates a shop for the bot based on level and tier odds.
     */
    private Shop createBotShop() {
        Shop newShop = new Shop(5);
        List<Champion> allChampions = new ArrayList<>(plugin.getChampionManager().getAllChampions());
        
        for (int i = 0; i < 5; i++) {
            if (!allChampions.isEmpty()) {
                // Weight by tier based on level
                List<Champion> eligibleChampions = allChampions.stream()
                    .filter(c -> shouldOfferTier(c.getTier()))
                    .toList();
                
                if (!eligibleChampions.isEmpty()) {
                    Champion selected = eligibleChampions.get(rng.nextInt(eligibleChampions.size()));
                    newShop.setSlot(i, selected);
                }
            }
        }
        return newShop;
    }
    
    /**
     * Determines if a champion tier should be offered based on bot's level.
     */
    private boolean shouldOfferTier(ChampionTier tier) {
        int cost = tier.getCost();
        // Simple odds: lower level = lower tier champs more likely
        double roll = rng.nextDouble();
        return switch (cost) {
            case 1 -> roll < (1.0 - level * 0.05);  // Tier 1: 95% at L1, 50% at L9
            case 2 -> roll < (level * 0.08);         // Tier 2: 8% at L1, 72% at L9
            case 3 -> level >= 3 && roll < (level * 0.06);  // Tier 3: unlocks L3
            case 4 -> level >= 5 && roll < (level * 0.04);  // Tier 4: unlocks L5
            case 5 -> level >= 7 && roll < (level * 0.02);  // Tier 5: unlocks L7
            default -> false;
        };
    }
}
