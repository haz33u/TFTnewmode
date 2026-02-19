package dev.astralclash.board;

import dev.astralclash.AstralClash;
import dev.astralclash.player.ArenaPlayer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Creates and manages the physical board platforms for all players in a game.
 *
 * <p>Each player's board is a floating platform in the arena world, spaced
 * {@code platform-spacing} blocks apart (X-axis).
 *
 * <p>Platform layout (4 rows × 7 cols example):
 * <pre>
 *   P P P P P P P P P  ← POLISHED_ANDESITE border
 *   P G G G G G G G P  ← GRAY_CONCRETE board rows (cell markers: LIGHT_BLUE_CARPET)
 *   P G G G G G G G P
 *   P G G G G G G G P
 *   P G G G G G G G P
 *   P W W W S W W W P  ← WHITE_CONCRETE player area (S = spawn point)
 *   P W W W W W W W P
 *   P P P P P P P P P  ← POLISHED_ANDESITE border
 * </pre>
 * Corners have 2-block CHISELED_STONE_BRICK pillars for decoration.
 * When a board is removed, all placed blocks are reset to AIR.
 */
public class BoardManager {

    private final AstralClash plugin;

    private final Map<UUID, Board>          boards       = new HashMap<>();
    private final Map<UUID, Set<Location>>  placedBlocks = new HashMap<>();

    // Platform origin for player index 0
    private static final int BASE_X = 0;
    private static final int BASE_Y = 64;
    private static final int BASE_Z = 0;

    /** Blocks of player-standing area behind the back row. */
    private static final int PLAYER_AREA_DEPTH = 4;

    public BoardManager(AstralClash plugin) {
        this.plugin = plugin;
    }

    // ── Board lifecycle ──────────────────────────────────────────────────────

    /**
     * Allocates a {@link Board} for the given player, generates the physical platform
     * in the arena world, and teleports the player to their spawn point.
     *
     * @param player        ArenaPlayer to assign the board to
     * @param platformIndex 0-based index (determines X offset in the world)
     */
    public Board createBoard(ArenaPlayer player, int platformIndex) {
        int rows    = plugin.getConfigManager().getBoardRows();
        int cols    = plugin.getConfigManager().getBoardCols();
        int spacing = plugin.getConfigManager().getPlatformSpacing();

        World world = plugin.getServer().getWorld(plugin.getConfigManager().getArenaWorld());
        if (world == null) {
            world = plugin.getServer().getWorlds().get(0);
        }

        int x = BASE_X + (platformIndex * spacing);
        Location origin = new Location(world, x, BASE_Y, BASE_Z);

        Board board = new Board(player.getUuid(), rows, cols, origin, world);
        boards.put(player.getUuid(), board);
        player.setBoard(board);

        generatePlatform(player.getUuid(), board, origin, world);

        Location spawn = board.getSpawnLocation();
        if (spawn != null && spawn.getWorld() != null) {
            player.getPlayer().teleport(spawn);
        }

        return board;
    }

    public Board getBoard(UUID playerId) {
        return boards.get(playerId);
    }

    public void removeBoard(UUID playerId) {
        Board board = boards.remove(playerId);
        if (board != null) board.clearAll();
        clearPlatform(playerId);
    }

    public void removeAll() {
        for (Board b : boards.values()) b.clearAll();
        boards.clear();
        for (UUID uuid : new HashSet<>(placedBlocks.keySet())) {
            clearPlatform(uuid);
        }
    }

    // ── Arena platform generation ────────────────────────────────────────────

    private void generatePlatform(UUID ownerId, Board board, Location origin, World world) {
        int rows = board.getRows();
        int cols = board.getCols();
        int cs   = Board.CELL_SPACING;   // 3

        // Board occupies X=[0, boardMaxX], Z=[0, boardMaxZ]
        int boardMaxX = (cols - 1) * cs;
        int boardMaxZ = (rows - 1) * cs;

        // Full platform bounds (including 1-block border and player area)
        int minX = -1;
        int maxX = boardMaxX + 1;
        int minZ = -1;
        int maxZ = boardMaxZ + PLAYER_AREA_DEPTH + 1;

        int bx = origin.getBlockX();
        int by = origin.getBlockY();
        int bz = origin.getBlockZ();

        Set<Location> placed = new HashSet<>();

        // ── Floor layer at y=by ───────────────────────────────────────────────
        for (int dx = minX; dx <= maxX; dx++) {
            for (int dz = minZ; dz <= maxZ; dz++) {
                boolean isBorder     = (dx == minX || dx == maxX || dz == minZ || dz == maxZ);
                boolean isBoardArea  = (dx >= 0 && dx <= boardMaxX && dz >= 0 && dz <= boardMaxZ);
                boolean isPlayerArea = (!isBorder && !isBoardArea);

                Material mat;
                if (isBorder) {
                    mat = Material.POLISHED_ANDESITE;
                } else if (isBoardArea) {
                    mat = Material.GRAY_CONCRETE;
                } else {
                    mat = isPlayerArea ? Material.WHITE_CONCRETE : Material.POLISHED_ANDESITE;
                }

                Block block = world.getBlockAt(bx + dx, by, bz + dz);
                block.setType(mat, false);
                placed.add(block.getLocation());
            }
        }

        // ── Cell markers: LIGHT_BLUE_CARPET at each cell centre (y+1) ─────────
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                Block carpet = world.getBlockAt(bx + c * cs, by + 1, bz + r * cs);
                carpet.setType(Material.LIGHT_BLUE_CARPET, false);
                placed.add(carpet.getLocation());
            }
        }

        // ── Corner pillars: 2-block CHISELED_STONE_BRICKS ─────────────────────
        int[][] corners = {{minX, minZ}, {maxX, minZ}, {minX, maxZ}, {maxX, maxZ}};
        for (int[] corner : corners) {
            for (int dy = 1; dy <= 2; dy++) {
                Block pillar = world.getBlockAt(bx + corner[0], by + dy, bz + corner[1]);
                pillar.setType(Material.CHISELED_STONE_BRICKS, false);
                placed.add(pillar.getLocation());
            }
        }

        placedBlocks.put(ownerId, placed);
    }

    // ── Platform cleanup ─────────────────────────────────────────────────────

    private void clearPlatform(UUID ownerId) {
        Set<Location> placed = placedBlocks.remove(ownerId);
        if (placed == null) return;
        for (Location loc : placed) {
            World w = loc.getWorld();
            if (w != null) {
                w.getBlockAt(loc).setType(Material.AIR, false);
            }
        }
    }
}
