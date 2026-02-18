package dev.astralclash.player;

/**
 * Persistent statistics stored in the database.
 */
public class PlayerStats {

    private String username = "Unknown";
    private int    gamesPlayed  = 0;
    private int    gamesWon     = 0;
    private int    totalDamage  = 0;
    private int    highestPlace = 8;
    private long   lastSeen     = 0;

    public PlayerStats() {}

    // ── Mutators ─────────────────────────────────────────────────────────────

    public void incrementGamesPlayed()              { gamesPlayed++; }
    public void incrementGamesWon()                 { gamesWon++; }
    public void addDamage(int dmg)                  { totalDamage += dmg; }
    public void updatePlace(int place)              { if (place < highestPlace) highestPlace = place; }
    public void touchLastSeen()                     { lastSeen = System.currentTimeMillis(); }

    // ── Getters / Setters ────────────────────────────────────────────────────

    public String getUsername()                     { return username; }
    public void   setUsername(String username)      { this.username = username; }

    public int    getGamesPlayed()                  { return gamesPlayed; }
    public void   setGamesPlayed(int v)             { gamesPlayed = v; }

    public int    getGamesWon()                     { return gamesWon; }
    public void   setGamesWon(int v)                { gamesWon = v; }

    public int    getTotalDamage()                  { return totalDamage; }
    public void   setTotalDamage(int v)             { totalDamage = v; }

    public int    getHighestPlace()                 { return highestPlace; }
    public void   setHighestPlace(int v)            { highestPlace = v; }

    public long   getLastSeen()                     { return lastSeen; }
    public void   setLastSeen(long v)               { lastSeen = v; }

    public double getWinRate() {
        return gamesPlayed == 0 ? 0 : (double) gamesWon / gamesPlayed * 100.0;
    }
}
