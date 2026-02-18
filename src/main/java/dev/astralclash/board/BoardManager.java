package dev.astralclash.board;

import dev.astralclash.AstralClash;
import dev.astralclash.player.ArenaPlayer;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Creates and manages the physical board platforms for all players in a game.
 *
 * <p>Each player's board is a floating platform in the arena world, spaced
 * {@code platform-spacing} blocks apart (X-axis). Boards are built with
 * plain stone slabs as the floor; no blocks are placed programmatically
 * (the admin pre-builds the arena or we generate it via WorldEdit/schematic).
 *
 * <p>For the plugin MVP we compute world locations and rely on the arena
 * world already being set up. Actual block generation is a stretch goal.
 */
public class BoardManager {

    private final AstralClash plugin;
    private final Map<UUID, Board> boards = new HashMap<>();

    // Platform origin for player index 0 (first player)
    private static final int BASE_X = 0;
    private static final int BASE_Y = 64;
    private static final int BASE_Z = 0;

    public BoardManager(AstralClash plugin) {
        this.plugin = plugin;
    }

    /**
     * Allocates and stores a {@link Board} for the given player.
     * The platform index determines the X offset in the arena world.
     *
     * @param player        ArenaPlayer to assign the board to
     * @param platformIndex 0-based index in the arena (determines world offset)
     */
    public Board createBoard(ArenaPlayer player, int platformIndex) {
        int rows = plugin.getConfigManager().getBoardRows();
        int cols = plugin.getConfigManager().getBoardCols();
        int spacing = plugin.getConfigManager().getPlatformSpacing();

        World world = plugin.getServer().getWorld(plugin.getConfigManager().getArenaWorld());
        if (world == null) {
            // Fall back to default world during development / testing
            world = plugin.getServer().getWorlds().get(0);
        }

        int x = BASE_X + (platformIndex * spacing);
        Location origin = new Location(world, x, BASE_Y, BASE_Z);

        Board board = new Board(player.getUuid(), rows, cols, origin, world);
        boards.put(player.getUuid(), board);
        player.setBoard(board);
        return board;
    }

    public Board getBoard(UUID playerId) {
        return boards.get(playerId);
    }

    public void removeBoard(UUID playerId) {
        Board board = boards.remove(playerId);
        if (board != null) board.clearAll();
    }

    public void removeAll() {
        for (Board b : boards.values()) b.clearAll();
        boards.clear();
    }
}
