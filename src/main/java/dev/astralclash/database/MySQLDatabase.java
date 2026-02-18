package dev.astralclash.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.astralclash.AstralClash;
import dev.astralclash.config.ConfigManager;
import dev.astralclash.player.PlayerStats;

import java.sql.*;
import java.util.UUID;
import java.util.logging.Level;

public class MySQLDatabase implements Database {

    private final AstralClash plugin;
    private HikariDataSource dataSource;

    public MySQLDatabase(AstralClash plugin) {
        this.plugin = plugin;
    }

    @Override
    public void initialize() {
        ConfigManager cfg = plugin.getConfigManager();

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true",
                cfg.getDbHost(), cfg.getDbPort(), cfg.getDbName()));
        config.setUsername(cfg.getDbUser());
        config.setPassword(cfg.getDbPassword());
        config.setMaximumPoolSize(cfg.getPoolSize());
        config.setConnectionTestQuery("SELECT 1");
        config.setPoolName("AstralClash-MySQL");
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        dataSource = new HikariDataSource(config);
        createTables();
    }

    private void createTables() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_stats (
                    uuid          VARCHAR(36) PRIMARY KEY,
                    username      VARCHAR(16) NOT NULL,
                    games_played  INT DEFAULT 0,
                    games_won     INT DEFAULT 0,
                    total_damage  INT DEFAULT 0,
                    highest_place INT DEFAULT 8,
                    last_seen     BIGINT DEFAULT 0
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create MySQL tables", e);
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
                stats.setUsername(rs.getString("username"));
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
            ON DUPLICATE KEY UPDATE
                username      = VALUES(username),
                games_played  = VALUES(games_played),
                games_won     = VALUES(games_won),
                total_damage  = VALUES(total_damage),
                highest_place = VALUES(highest_place),
                last_seen     = VALUES(last_seen)
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
