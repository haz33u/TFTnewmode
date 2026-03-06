package dev.astralclash.game;

import dev.astralclash.AstralClash;
import dev.astralclash.bot.BotPlayer;
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
    private final Set<UUID>        waitingBots    = new LinkedHashSet<>();
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
            int min = getMinPlayersEffective();
            if (getSize() < min && countingDown) {
                cancelCountdown();
            }
        }
        return removed;
    }

    private int getMinPlayersEffective() {
        return plugin.getConfigManager().isPveTestMode()
            ? 1
            : plugin.getConfigManager().getMinPlayers();
    }

    public boolean hasPlayer(UUID uuid) { return waitingPlayers.contains(uuid); }

    public int getSize() { return waitingPlayers.size() + waitingBots.size(); }
    
    public int getPlayerCount() { return waitingPlayers.size(); }
    
    public int getBotCount() { return waitingBots.size(); }
    
    // ── Bot management ───────────────────────────────────────────────────────
    
    public boolean addBot(BotPlayer bot) {
        int max = plugin.getConfigManager().getMaxPlayers();
        if (getSize() >= max) return false;
        
        waitingBots.add(bot.getUuid());
        broadcast(Component.text("[BOT] " + bot.getName() + " joined! (" + 
            getSize() + "/" + max + ")", NamedTextColor.AQUA));
        checkStart();
        return true;
    }
    
    public boolean removeBot(UUID uuid) {
        boolean removed = waitingBots.remove(uuid);
        if (removed) {
            int min = getMinPlayersEffective();
            if (getSize() < min && countingDown) {
                cancelCountdown();
            }
        }
        return removed;
    }
    
    public void removeAllBots() {
        waitingBots.clear();
    }
    
    public Set<UUID> getWaitingBots() { return Collections.unmodifiableSet(waitingBots); }

    // ── Countdown ─────────────────────────────────────────────────────────────

    private void checkStart() {
        int min = getMinPlayersEffective();
        if (!countingDown && getSize() >= min) {
            startCountdown();
        }
    }

    private void startCountdown() {
        countingDown = true;
        countdown    = plugin.getConfigManager().getLobbyCountdown();
        countdownTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (countdown <= 0) {
                countingDown = false;
                countdownTask.cancel();
                countdownTask = null;
                // Combine players and bots and start game
                List<UUID> allParticipants = new ArrayList<>(waitingPlayers);
                allParticipants.addAll(waitingBots);
                plugin.getGameManager().startGame(allParticipants);
                waitingPlayers.clear();
                waitingBots.clear();
                return;
            }
            if (countdown <= 5 || countdown % 10 == 0) {
                broadcast(Component.text("Game starts in " + countdown + "s!", NamedTextColor.GREEN));
            }
            countdown--;
        }, 0L, 20L);
    }

    private void cancelCountdown() {
        stopCountdownOnly();
        broadcast(Component.text("Countdown cancelled — not enough players.", NamedTextColor.RED));
    }
    
    /** Stops countdown task without broadcasting (e.g. when game starts successfully). */
    private void stopCountdownOnly() {
        countingDown = false;
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
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
