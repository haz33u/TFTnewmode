package dev.astralclash.board;

import dev.astralclash.champion.ChampionInstance;
import org.bukkit.Location;

/**
 * A single cell on the 4×7 board grid.
 * Tracks its grid coordinates, world location, and any champion deployed on it.
 */
public class BoardCell {

    private final int row;   // 0-indexed (0 = player-side front row)
    private final int col;   // 0-indexed
    private final Location worldLocation;

    private ChampionInstance occupant = null;

    public BoardCell(int row, int col, Location worldLocation) {
        this.row           = row;
        this.col           = col;
        this.worldLocation = worldLocation;
    }

    // ── Occupancy ─────────────────────────────────────────────────────────────

    public boolean isOccupied()  { return occupant != null; }
    public boolean isEmpty()     { return occupant == null; }

    public ChampionInstance getOccupant()            { return occupant; }
    public void             setOccupant(ChampionInstance ci) {
        this.occupant = ci;
        if (ci != null) ci.setCell(this);
    }
    public void clearOccupant()  {
        if (occupant != null) occupant.setCell(null);
        occupant = null;
    }

    // ── Grid distance ─────────────────────────────────────────────────────────

    /** Manhattan distance to another cell (simplified — no hex offset). */
    public int distanceTo(BoardCell other) {
        return Math.abs(this.row - other.row) + Math.abs(this.col - other.col);
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public int      getRow()           { return row; }
    public int      getCol()           { return col; }
    public Location getWorldLocation() { return worldLocation; }

    @Override
    public String toString() {
        return "Cell[" + row + "," + col + "]" + (isOccupied() ? "=" + occupant.getChampion().getId() : "");
    }
}
