package dev.astralclash.ui;

import dev.astralclash.AstralClash;
import dev.astralclash.items.GameItems;
import dev.astralclash.player.ArenaPlayer;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages the arena spectator view - allows players to fly above the arena
 * and see all boards from above.
 */
public class ArenaViewManager {

    private final AstralClash plugin;
    private final Map<UUID, Location> savedLocations = new HashMap<>();
    private final Map<UUID, BukkitTask> viewTasks = new HashMap<>();

    public ArenaViewManager(AstralClash plugin) {
        this.plugin = plugin;
    }

    /**
     * Toggles arena view for the player - if not viewing, starts viewing; if viewing, stops.
     */
    public void toggleArenaView(ArenaPlayer ap) {
        Player player = ap.getPlayer();
        if (player == null) return;

        UUID uuid = player.getUniqueId();

        if (savedLocations.containsKey(uuid)) {
            // Currently viewing - restore normal view
            stopArenaView(ap);
        } else {
            // Not viewing - start arena view
            startArenaView(ap);
        }
    }

    private void startArenaView(ArenaPlayer ap) {
        Player player = ap.getPlayer();
        if (player == null) return;

        UUID uuid = player.getUniqueId();
        
        // Save current location
        savedLocations.put(uuid, player.getLocation().clone());
        
        // Calculate arena center (between all boards)
        var allPlayers = plugin.getGameManager().getActivePlayers();
        if (allPlayers.isEmpty()) {
            player.sendMessage("§cNo active game!");
            return;
        }

        // Find center point between all boards
        double minX = Double.MAX_VALUE, maxX = Double.MIN_VALUE;
        double minZ = Double.MAX_VALUE, maxZ = Double.MIN_VALUE;
        double y = 64;

        for (ArenaPlayer other : allPlayers) {
            if (other.getBoard() == null) continue;
            var board = other.getBoard();
            var origin = board.getCell(0, 0).getWorldLocation();
            
            minX = Math.min(minX, origin.getX());
            maxX = Math.max(maxX, origin.getX() + board.getCols() * 3);
            minZ = Math.min(minZ, origin.getZ());
            maxZ = Math.max(maxZ, origin.getZ() + board.getRows() * 3);
            y = origin.getY();
        }

        double centerX = (minX + maxX) / 2.0;
        double centerZ = (minZ + maxZ) / 2.0;
        double viewY = y + 30.0; // 30 blocks above arena

        Location viewLocation = new Location(player.getWorld(), centerX, viewY, centerZ, 0f, 90f);

        // Enable flight and teleport
        player.setGameMode(GameMode.SPECTATOR);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.teleport(viewLocation);

        player.sendMessage("§a§lArena View Enabled!");
        player.sendMessage("§7You can now fly around to view all boards.");
        player.sendMessage("§7Right-click the §cExit Arena View §7item (hotbar slot 9) to return.");

        // Give exit item so player can leave view without opening menu (spectator can't open menu normally)
        player.getInventory().setItem(GameItems.SLOT_EXIT_ARENA_VIEW, GameItems.createExitArenaViewItem());

        // Auto-return after 60 seconds if still in view
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (savedLocations.containsKey(uuid)) {
                stopArenaView(ap);
                player.sendMessage("§eArena view timed out - returned to normal view.");
            }
        }, 1200L); // 60 seconds

        viewTasks.put(uuid, task);
    }

    public void stopArenaView(ArenaPlayer ap) {
        Player player = ap.getPlayer();
        if (player == null) return;

        UUID uuid = player.getUniqueId();
        Location saved = savedLocations.remove(uuid);
        
        if (saved != null) {
            player.teleport(saved);
            player.setGameMode(GameMode.SURVIVAL);
            player.setFlying(false);
            player.setAllowFlight(false);
            player.getInventory().setItem(GameItems.SLOT_EXIT_ARENA_VIEW, null);
            player.sendMessage("§aReturned to normal view.");
        }

        BukkitTask task = viewTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }

    /** Returns true if the player is currently in arena spectator view. */
    public boolean isInArenaView(UUID uuid) {
        return savedLocations.containsKey(uuid);
    }

    /**
     * Called when player leaves - cleans up their view state.
     */
    public void onPlayerLeave(UUID uuid) {
        savedLocations.remove(uuid);
        BukkitTask task = viewTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }

    public void cleanup() {
        for (UUID uuid : new java.util.HashSet<>(savedLocations.keySet())) {
            Player p = plugin.getServer().getPlayer(uuid);
            if (p != null) {
                Location saved = savedLocations.get(uuid);
                if (saved != null) {
                    p.teleport(saved);
                    p.setGameMode(GameMode.SURVIVAL);
                    p.setFlying(false);
                }
            }
        }
        savedLocations.clear();
        viewTasks.values().forEach(BukkitTask::cancel);
        viewTasks.clear();
    }
}
