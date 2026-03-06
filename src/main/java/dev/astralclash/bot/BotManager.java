package dev.astralclash.bot;

import dev.astralclash.AstralClash;

import java.util.*;

/**
 * Manages bot players for solo testing and filling lobbies.
 */
public class BotManager {

    private static final String[] BOT_NAMES = {
        "AstrBot_Alpha", "AstrBot_Beta", "AstrBot_Gamma", "AstrBot_Delta",
        "AstrBot_Epsilon", "AstrBot_Zeta", "AstrBot_Eta", "AstrBot_Theta"
    };
    
    private final AstralClash plugin;
    private final Map<UUID, BotPlayer> bots = new HashMap<>();
    private int botNameIndex = 0;
    
    public BotManager(AstralClash plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Creates a new bot with the specified difficulty.
     * @param difficulty 0.0 = random decisions, 1.0 = optimal play
     * @return The created bot
     */
    public BotPlayer createBot(double difficulty) {
        String name = BOT_NAMES[botNameIndex % BOT_NAMES.length];
        botNameIndex++;
        
        BotPlayer bot = new BotPlayer(plugin, name, difficulty);
        bots.put(bot.getUuid(), bot);
        
        plugin.getLogger().info("[BotManager] Created bot: " + name + 
            " (difficulty: " + String.format("%.1f", difficulty) + 
            ", strategy: " + bot.getStrategy() + ")");
        
        return bot;
    }
    
    /**
     * Creates a bot with medium difficulty.
     */
    public BotPlayer createBot() {
        return createBot(0.5);
    }
    
    /**
     * Creates multiple bots to fill a lobby.
     * @param count Number of bots to create
     * @param difficulty Bot difficulty
     * @return List of created bots
     */
    public List<BotPlayer> createBots(int count, double difficulty) {
        List<BotPlayer> created = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            created.add(createBot(difficulty));
        }
        return created;
    }
    
    /**
     * Removes a bot from the manager.
     */
    public void removeBot(UUID uuid) {
        BotPlayer removed = bots.remove(uuid);
        if (removed != null) {
            plugin.getLogger().info("[BotManager] Removed bot: " + removed.getName());
        }
    }
    
    /**
     * Removes all bots.
     */
    public void removeAllBots() {
        bots.clear();
        botNameIndex = 0;
        plugin.getLogger().info("[BotManager] All bots removed.");
    }
    
    /**
     * Gets a bot by UUID.
     */
    public BotPlayer getBot(UUID uuid) {
        return bots.get(uuid);
    }
    
    /**
     * Gets all active bots.
     */
    public Collection<BotPlayer> getAllBots() {
        return Collections.unmodifiableCollection(bots.values());
    }
    
    /**
     * Checks if a UUID belongs to a bot.
     */
    public boolean isBot(UUID uuid) {
        return bots.containsKey(uuid);
    }
    
    /**
     * Called at the start of each planning phase.
     * Makes all bots plan their moves.
     */
    public void planPhaseForAllBots() {
        for (BotPlayer bot : bots.values()) {
            if (!bot.isEliminated()) {
                try {
                    bot.planPhase();
                } catch (Exception e) {
                    plugin.getLogger().warning("[BotManager] Error in bot planning: " + e.getMessage());
                }
            }
        }
    }
    
    public int getBotCount() {
        return bots.size();
    }
    
    public int getAliveBotCount() {
        return (int) bots.values().stream().filter(b -> !b.isEliminated()).count();
    }
}
