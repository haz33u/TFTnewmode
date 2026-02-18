package dev.astralclash.board;

import dev.astralclash.champion.ChampionInstance;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Represents a single player's 4×7 battle board in the arena.
 *
 * <p>Layout (from player's perspective):
 * <pre>
 *   Row 0 (enemy-facing) — closest to the fight
 *   Row 3 (back row)     — furthest from the opponent
 *   Cols 0-6 (left-right)
 * </pre>
 * The world location of cell [r][c] is computed from the platform's origin.
 */
public class Board {

    private static final int CELL_SPACING = 3; // blocks between cell centres

    private final int rows;
    private final int cols;
    private final BoardCell[][] cells;

    private final UUID ownerId;

    public Board(UUID ownerId, int rows, int cols,
                 Location origin, World world) {
        this.ownerId = ownerId;
        this.rows    = rows;
        this.cols    = cols;
        this.cells   = new BoardCell[rows][cols];

        // Build cells with world locations based on origin
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                double x = origin.getX() + c * CELL_SPACING;
                double y = origin.getY();
                double z = origin.getZ() + r * CELL_SPACING;
                cells[r][c] = new BoardCell(r, c, new Location(world, x, y, z));
            }
        }
    }

    // ── Deployment ───────────────────────────────────────────────────────────

    /**
     * Attempts to place {@code ci} on cell [row][col].
     * Returns false if the cell is occupied or out of bounds.
     */
    public boolean place(ChampionInstance ci, int row, int col) {
        if (!inBounds(row, col)) return false;
        BoardCell cell = cells[row][col];
        if (cell.isOccupied()) return false;
        cell.setOccupant(ci);
        return true;
    }

    /**
     * Removes the champion from cell [row][col] and returns it, or null if empty.
     */
    public ChampionInstance remove(int row, int col) {
        if (!inBounds(row, col)) return null;
        BoardCell cell = cells[row][col];
        ChampionInstance ci = cell.getOccupant();
        cell.clearOccupant();
        return ci;
    }

    /** Moves a champion from one cell to another. Returns false if move is illegal. */
    public boolean move(int fromRow, int fromCol, int toRow, int toCol) {
        if (!inBounds(fromRow, fromCol) || !inBounds(toRow, toCol)) return false;
        BoardCell from = cells[fromRow][fromCol];
        BoardCell to   = cells[toRow][toCol];
        if (from.isEmpty() || to.isOccupied()) return false;
        ChampionInstance ci = from.getOccupant();
        from.clearOccupant();
        to.setOccupant(ci);
        return true;
    }

    /**
     * Places {@code ci} on the first empty cell (row-major order, back row first).
     * Returns true if placed, false if the board is full.
     */
    public boolean placeOnFirstEmpty(ChampionInstance ci) {
        for (int r = rows - 1; r >= 0; r--) {        // start from back row
            for (int c = 0; c < cols; c++) {
                if (!cells[r][c].isOccupied()) {
                    cells[r][c].setOccupant(ci);
                    ci.setCell(cells[r][c]);
                    return true;
                }
            }
        }
        return false;
    }

    // ── Queries ──────────────────────────────────────────────────────────────

    public List<ChampionInstance> getDeployedChampions() {
        List<ChampionInstance> deployed = new ArrayList<>();
        for (BoardCell[] row : cells) {
            for (BoardCell cell : row) {
                if (cell.isOccupied()) deployed.add(cell.getOccupant());
            }
        }
        return deployed;
    }

    public int getDeployedCount() {
        return (int) getDeployedChampions().size();
    }

    public Optional<BoardCell> findCell(ChampionInstance ci) {
        for (BoardCell[] row : cells) {
            for (BoardCell cell : row) {
                if (cell.getOccupant() == ci) return Optional.of(cell);
            }
        }
        return Optional.empty();
    }

    /** Finds the closest enemy to the given cell using Manhattan distance. */
    public Optional<ChampionInstance> findNearestEnemy(BoardCell from,
                                                        List<ChampionInstance> enemies) {
        ChampionInstance nearest = null;
        int bestDist = Integer.MAX_VALUE;

        for (ChampionInstance enemy : enemies) {
            if (!enemy.isAlive()) continue;
            BoardCell ec = enemy.getCell();
            if (ec == null) continue;
            int dist = from.distanceTo(ec);
            if (dist < bestDist) {
                bestDist = dist;
                nearest  = enemy;
            }
        }
        return Optional.ofNullable(nearest);
    }

    public void clearAll() {
        for (BoardCell[] row : cells) {
            for (BoardCell cell : row) cell.clearOccupant();
        }
    }

    public BoardCell getCell(int row, int col) {
        if (!inBounds(row, col)) return null;
        return cells[row][col];
    }

    public boolean inBounds(int row, int col) {
        return row >= 0 && row < rows && col >= 0 && col < cols;
    }

    public int  getRows()    { return rows; }
    public int  getCols()    { return cols; }
    public UUID getOwnerId() { return ownerId; }
}
