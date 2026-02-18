package dev.astralclash.game;

import dev.astralclash.AstralClash;
import dev.astralclash.player.ArenaPlayer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Pre-game lobby. Collects players until the minimum is reached,
 * then runs a countdown before handing off to {@link GameManager}.
 */
public class GameLobby {

    private final AstralClash      plugin;
    private final Set<UUID>        waitingPlayers = new LinkedHashSet<>();
    private int                    countdown;
    private boolean                countingDown   = false;
    private org.bukkit.scheduler.BukkitTask countdownTask;

    public GameLobby(AstralClash plugin) {
        this.plugin    = plugin;
        this.countdown = plugin.getConfigManager().getLobbyCountdown();
    }

    // ── Player join/leave ─────────────────────────────────────────────────────

    public boolean addPlayer(Player player) {
        if (waitingPlayers.contains(player.getUniqueId())) return false;
        int max = plugin.getConfigManager().getMaxPlayers();
        if (waitingPlayers.size() >= max) {
            player.sendMessage(Component.text("The lobby is full!", NamedTextColor.RED));
            return false;
        }
        waitingPlayers.add(player.getUniqueId());
        broadcast(Component.text(player.getName() + " joined the lobby! ("
                + waitingPlayers.size() + "/" + max + ")", NamedTextColor.YELLOW));
        checkStart();
        return true;
    }

    public boolean removePlayer(UUID uuid) {
        boolean removed = waitingPlayers.remove(uuid);
        if (removed) {
            int min = plugin.getConfigManager().getMinPlayers();
            if (waitingPlayers.size() < min && countingDown) {
                cancelCountdown();
            }
        }
        return removed;
    }

    public boolean hasPlayer(UUID uuid) { return waitingPlayers.contains(uuid); }

    public int getSize() { return waitingPlayers.size(); }

    // ── Countdown ─────────────────────────────────────────────────────────────

    private void checkStart() {
        int min = plugin.getConfigManager().getMinPlayers();
        if (!countingDown && waitingPlayers.size() >= min) {
            startCountdown();
        }
    }

    private void startCountdown() {
        countingDown = true;
        countdown    = plugin.getConfigManager().getLobbyCountdown();
        countdownTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (countdown <= 0) {
                cancelCountdown();
                plugin.getGameManager().startGame(new ArrayList<>(waitingPlayers));
                waitingPlayers.clear();
                return;
            }
            if (countdown <= 5 || countdown % 10 == 0) {
                broadcast(Component.text("Game starts in " + countdown + "s!", NamedTextColor.GREEN));
            }
            countdown--;
        }, 0L, 20L);
    }

    private void cancelCountdown() {
        countingDown = false;
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
        broadcast(Component.text("Countdown cancelled — not enough players.", NamedTextColor.RED));
    }

    // ── Messaging ─────────────────────────────────────────────────────────────

    private void broadcast(Component msg) {
        for (UUID uuid : waitingPlayers) {
            Player p = plugin.getServer().getPlayer(uuid);
            if (p != null) p.sendMessage(msg);
        }
    }

    public Set<UUID> getWaitingPlayers() { return Collections.unmodifiableSet(waitingPlayers); }
}
