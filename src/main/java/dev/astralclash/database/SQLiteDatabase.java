package dev.astralclash.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.astralclash.AstralClash;
import dev.astralclash.player.PlayerStats;

import java.io.File;
import java.sql.*;
import java.util.UUID;
import java.util.logging.Level;

public class SQLiteDatabase implements Database {

    private final AstralClash plugin;
    private HikariDataSource dataSource;

    public SQLiteDatabase(AstralClash plugin) {
        this.plugin = plugin;
    }

    @Override
    public void initialize() {
        File dbFile = new File(plugin.getDataFolder(), "data.db");
        plugin.getDataFolder().mkdirs();

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
        config.setDriverClassName("org.sqlite.JDBC");
        config.setMaximumPoolSize(1);          // SQLite is single-writer
        config.setConnectionTestQuery("SELECT 1");
        config.setPoolName("AstralClash-SQLite");

        dataSource = new HikariDataSource(config);
        createTables();
    }

    private void createTables() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_stats (
                    uuid          TEXT PRIMARY KEY,
                    username      TEXT NOT NULL,
                    games_played  INTEGER DEFAULT 0,
                    games_won     INTEGER DEFAULT 0,
                    total_damage  INTEGER DEFAULT 0,
                    highest_place INTEGER DEFAULT 8,
                    last_seen     INTEGER DEFAULT 0
                )
                """);

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create SQLite tables", e);
        }
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    @Override
    public PlayerStats loadStats(UUID uuid, String username) {
        String sql = "SELECT * FROM player_stats WHERE uuid = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                PlayerStats stats = new PlayerStats();
                stats.setGamesPlayed(rs.getInt("games_played"));
                stats.setGamesWon(rs.getInt("games_won"));
                stats.setTotalDamage(rs.getInt("total_damage"));
                stats.setHighestPlace(rs.getInt("highest_place"));
                stats.setLastSeen(rs.getLong("last_seen"));
                return stats;
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load stats for " + uuid, e);
        }
        return new PlayerStats();
    }

    @Override
    public void saveStats(UUID uuid, PlayerStats stats) {
        String sql = """
            INSERT INTO player_stats (uuid, username, games_played, games_won, total_damage, highest_place, last_seen)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(uuid) DO UPDATE SET
                username      = excluded.username,
                games_played  = excluded.games_played,
                games_won     = excluded.games_won,
                total_damage  = excluded.total_damage,
                highest_place = excluded.highest_place,
                last_seen     = excluded.last_seen
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, uuid.toString());
            ps.setString(2, stats.getUsername());
            ps.setInt(3, stats.getGamesPlayed());
            ps.setInt(4, stats.getGamesWon());
            ps.setInt(5, stats.getTotalDamage());
            ps.setInt(6, stats.getHighestPlace());
            ps.setLong(7, stats.getLastSeen());
            ps.executeUpdate();

        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save stats for " + uuid, e);
        }
    }
}
