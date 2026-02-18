package dev.astralclash.listeners;

import dev.astralclash.AstralClash;
import dev.astralclash.board.Board;
import dev.astralclash.board.BoardCell;
import dev.astralclash.champion.ChampionInstance;
import dev.astralclash.game.GamePhase;
import dev.astralclash.player.ArenaPlayer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * In-game event handling:
 * - Right-click on the board to place/pick up champions during planning phase.
 * - Left-click on a bench champion to select it for placement.
 */
public class GameListener implements Listener {

    private final AstralClash plugin;

    public GameListener(AstralClash plugin) {
        this.plugin = plugin;
    }

    /**
     * Very simple board interaction: right-clicking a block in the arena world
     * during planning phase will attempt to place the first bench champion onto
     * the nearest board cell. A proper drag-and-drop system would require
     * PacketEvents to intercept client movement and block-break packets.
     *
     * This is an MVP placeholder — production implementation should use
     * PacketEvents to capture right-click + entity interactions for smooth UX.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBoardInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;

        Player player = event.getPlayer();
        ArenaPlayer ap = plugin.getPlayerManager().getArenaPlayer(player);
        if (ap == null) return;

        if (plugin.getGameManager().getPhase() != GamePhase.PLANNING) return;

        Board board = ap.getBoard();
        if (board == null) return;

        // Find the board cell closest to the clicked block location
        var clickedLoc = event.getClickedBlock().getLocation().add(0.5, 0, 0.5);
        BoardCell nearest = findNearestCell(board, clickedLoc);
        if (nearest == null) return;

        if (nearest.isOccupied()) {
            // Pick up — move to bench
            ChampionInstance ci = nearest.getOccupant();
            nearest.clearOccupant();
            if (ap.hasBenchSpace()) {
                ap.addToBench(ci);
                player.sendMessage(Component.text(
                        ci.getChampion().getDisplayName() + " moved to bench.", NamedTextColor.YELLOW));
            } else {
                // No bench space — put it back
                nearest.setOccupant(ci);
                player.sendMessage(Component.text("Bench is full!", NamedTextColor.RED));
            }
        } else {
            // Place first bench champion
            if (ap.getBench().isEmpty()) {
                player.sendMessage(Component.text("No champions on bench!", NamedTextColor.RED));
                return;
            }
            if (ap.getDeployedCount() >= ap.getBoardSizeLimit()) {
                player.sendMessage(Component.text(
                        "Board is full! Upgrade your level to deploy more units.", NamedTextColor.RED));
                return;
            }
            ChampionInstance ci = ap.getBench().get(0);
            if (board.place(ci, nearest.getRow(), nearest.getCol())) {
                ap.removeFromBench(ci);
                player.sendMessage(Component.text(
                        ci.getChampion().getDisplayName() + " placed on the board!", NamedTextColor.GREEN));
            }
        }

        event.setCancelled(true);
    }

    private BoardCell findNearestCell(Board board, org.bukkit.Location loc) {
        BoardCell nearest = null;
        double minDist = Double.MAX_VALUE;
        for (int r = 0; r < board.getRows(); r++) {
            for (int c = 0; c < board.getCols(); c++) {
                BoardCell cell = board.getCell(r, c);
                if (cell == null) continue;
                double dist = cell.getWorldLocation().distanceSquared(loc);
                if (dist < minDist) {
                    minDist = dist;
                    nearest = cell;
                }
            }
        }
        // Only accept if within 3 blocks of a cell centre
        return (minDist <= 9) ? nearest : null;
    }
}
