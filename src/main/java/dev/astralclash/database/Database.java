package dev.astralclash.database;

import dev.astralclash.player.PlayerStats;

import java.util.UUID;

/**
 * Abstraction layer over the backing data store.
 * Implementations: {@link SQLiteDatabase}, {@link MySQLDatabase}.
 */
public interface Database {

    /** Create tables / run migrations. Called once on plugin enable. */
    void initialize();

    /** Cleanly close the connection pool. */
    void close();

    // ── Player data ──────────────────────────────────────────────────────────

    /**
     * Load a player's persistent stats from storage.
     * Returns a fresh {@link PlayerStats} if the player has no record yet.
     */
    PlayerStats loadStats(UUID uuid, String username);

    /** Persist (insert or update) a player's stats. */
    void saveStats(UUID uuid, PlayerStats stats);
}
