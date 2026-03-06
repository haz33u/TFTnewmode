package dev.astralclash.game;

import dev.astralclash.AstralClash;
import dev.astralclash.board.Board;
import dev.astralclash.champion.ChampionInstance;
import dev.astralclash.player.ArenaPlayer;
import dev.astralclash.player.PlayerStats;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.List;
import java.util.UUID;

/**
 * Builds a PvE "opponent" for test mode: an ArenaPlayer with no real Player,
 * whose board is filled with pre-defined champion instances for the given round.
 */
public class PvEOpponentFactory {

    private static final UUID PVE_OPPONENT_UUID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final AstralClash plugin;

    public PvEOpponentFactory(AstralClash plugin) {
        this.plugin = plugin;
    }

    /**
     * Creates an ArenaPlayer representing the PvE wave for the given round.
     * Board is filled with 1-star champions from config (pve.waves.{round}).
     */
    public ArenaPlayer buildPvEOpponent(int round) {
        World world = plugin.getServer().getWorld(plugin.getConfigManager().getArenaWorld());
        if (world == null) {
            plugin.getLogger().warning("[PvE] Arena world not loaded — using default world.");
            world = plugin.getServer().getWorlds().isEmpty() ? null : plugin.getServer().getWorlds().get(0);
        }
        if (world == null) {
            plugin.getLogger().warning("[PvE] No world available — PvE opponent will have empty board.");
        }

        int rows = plugin.getConfigManager().getBoardRows();
        int cols = plugin.getConfigManager().getBoardCols();
        Location origin = world != null ? new Location(world, 0, 64, 0) : new Location(null, 0, 64, 0);
        Board board = new Board(PVE_OPPONENT_UUID, rows, cols, origin, world != null ? world : origin.getWorld());

        List<String> championIds = plugin.getConfigManager().getPveWaveChampions(round);
        for (String id : championIds) {
            ChampionInstance ci = plugin.getChampionManager().createInstance(id, PVE_OPPONENT_UUID);
            if (ci != null && board.placeOnFirstEmpty(ci)) {
                // placeOnFirstEmpty already sets ci.setCell(...)
            }
        }

        PlayerStats dummyStats = new PlayerStats(PVE_OPPONENT_UUID, "PvE");
        ArenaPlayer pve = new ArenaPlayer(plugin, null, dummyStats);
        pve.setBoard(board);
        pve.setHealth(999); // unused; we never apply nexus damage to PvE
        return pve;
    }
}
