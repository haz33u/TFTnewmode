package dev.astralclash.database;

import dev.astralclash.AstralClash;
import dev.astralclash.player.PlayerStats;

import java.util.UUID;

/**
 * Facade that picks the correct {@link Database} implementation based on
 * the config and forwards all calls to it.
 */
public class DatabaseManager {

    private final AstralClash plugin;
    private Database database;

    public DatabaseManager(AstralClash plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        String type = plugin.getConfigManager().getDbType();
        database = type.equals("MYSQL") ? new MySQLDatabase(plugin) : new SQLiteDatabase(plugin);
        database.initialize();
        plugin.getLogger().info("Database backend: " + type);
    }

    public void close() {
        if (database != null) database.close();
    }

    public PlayerStats loadStats(UUID uuid, String username) {
        return database.loadStats(uuid, username);
    }

    public void saveStats(UUID uuid, PlayerStats stats) {
        database.saveStats(uuid, stats);
    }
}
