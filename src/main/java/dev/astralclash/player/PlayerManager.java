package dev.astralclash.player;

import dev.astralclash.AstralClash;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Manages the lifecycle of {@link ArenaPlayer} instances.
 * Handles loading/saving player data on join/leave.
 */
public class PlayerManager {

    private final AstralClash plugin;
    /** Active arena players (only populated during a game). */
    private final Map<UUID, ArenaPlayer> arenaPlayers = new HashMap<>();

    public PlayerManager(AstralClash plugin) {
        this.plugin = plugin;
    }

    // ── Arena lifecycle ──────────────────────────────────────────────────────

    /**
     * Creates and registers an {@link ArenaPlayer} for the given Bukkit {@link Player}.
     * Loads their persistent stats from the database first.
     */
    public ArenaPlayer createArenaPlayer(Player player) {
        PlayerStats stats = plugin.getDatabaseManager()
                .loadStats(player.getUniqueId(), player.getName());
        stats.setUsername(player.getName());

        ArenaPlayer ap = new ArenaPlayer(plugin, player, stats);
        arenaPlayers.put(player.getUniqueId(), ap);
        return ap;
    }

    /**
     * Saves stats and removes the {@link ArenaPlayer} from memory.
     */
    public void removeArenaPlayer(UUID uuid) {
        ArenaPlayer ap = arenaPlayers.remove(uuid);
        if (ap != null) {
            ap.getStats().touchLastSeen();
            plugin.getDatabaseManager().saveStats(uuid, ap.getStats());
        }
    }

    /** Returns the {@link ArenaPlayer} or null if not in a game. */
    public ArenaPlayer getArenaPlayer(UUID uuid) {
        return arenaPlayers.get(uuid);
    }

    public ArenaPlayer getArenaPlayer(Player player) {
        return getArenaPlayer(player.getUniqueId());
    }

    public boolean isInGame(UUID uuid) {
        return arenaPlayers.containsKey(uuid);
    }

    public Collection<ArenaPlayer> getAllArenaPlayers() {
        return Collections.unmodifiableCollection(arenaPlayers.values());
    }
    
    /**
     * Registers an ArenaPlayer for a bot (doesn't require real Player object).
     */
    public void registerBotArenaPlayer(UUID uuid, ArenaPlayer ap) {
        arenaPlayers.put(uuid, ap);
    }

    /** Saves all in-memory player stats immediately (e.g., on shutdown). */
    public void saveAll() {
        for (Map.Entry<UUID, ArenaPlayer> e : arenaPlayers.entrySet()) {
            e.getValue().getStats().touchLastSeen();
            plugin.getDatabaseManager().saveStats(e.getKey(), e.getValue().getStats());
        }
    }
}
