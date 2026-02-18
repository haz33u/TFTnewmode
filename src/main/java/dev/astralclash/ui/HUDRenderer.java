package dev.astralclash.ui;

import dev.astralclash.AstralClash;
import dev.astralclash.champion.ChampionInstance;
import dev.astralclash.champion.trait.Trait;
import dev.astralclash.champion.trait.TraitManager;
import dev.astralclash.game.GamePhase;
import dev.astralclash.player.ArenaPlayer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Renders the player HUD using:
 * <ul>
 *   <li>Action bar — quick status (gold, HP, round, level)</li>
 *   <li>Sidebar scoreboard — traits, unit count, standings</li>
 *   <li>Boss bar — phase timer (managed by {@link UIManager})</li>
 *   <li>TextDisplay entities — floating damage numbers</li>
 * </ul>
 */
public class HUDRenderer {

    /** Maximum sidebar lines (§0–§f gives us 16 unique "player" entries). */
    private static final int MAX_LINES = 16;

    private final AstralClash  plugin;
    private final TraitManager traitManager = new TraitManager();

    /** Per-player scoreboard instances (rebuilt each HUD cycle). */
    private final Map<UUID, Scoreboard> playerScoreboards = new HashMap<>();

    public HUDRenderer(AstralClash plugin) {
        this.plugin = plugin;
    }

    // ── Action bar ────────────────────────────────────────────────────────────

    /**
     * Sends a one-line action-bar summary to the player.
     * Called every {@code hud-update-interval} ticks.
     */
    public void renderActionBar(ArenaPlayer ap) {
        GamePhase phase = plugin.getGameManager().getPhase();

        String phaseStr = switch (phase) {
            case PLANNING -> "§bPlanning";
            case COMBAT   -> "§cCombat!";
            case RESULTS  -> "§eResults";
            default       -> "§7Waiting";
        };

        int deployed  = ap.getDeployedCount();
        int maxDeploy = ap.getBoardSizeLimit();

        Component bar = Component.text(
                "§6♦ " + ap.getGold() + "g  " +
                "§c♥ " + ap.getHealth() + "HP  " +
                "§a⬡ " + deployed + "/" + maxDeploy + "  " +
                "§eLv" + ap.getLevel() + "  " +
                phaseStr);

        ap.getPlayer().sendActionBar(bar);
    }

    // ── Scoreboard ────────────────────────────────────────────────────────────

    /**
     * Updates the player's sidebar scoreboard with trait info and standings.
     * Uses the Bukkit Scoreboard + Team API for clean, flash-free rendering.
     */
    public void renderScoreboard(ArenaPlayer ap, List<ArenaPlayer> allPlayers) {
        Player player = ap.getPlayer();
        if (!player.isOnline()) return;

        ScoreboardManager manager = Bukkit.getScoreboardManager();
        Scoreboard sb = playerScoreboards.computeIfAbsent(ap.getUuid(),
                k -> manager.getNewScoreboard());

        // ── Build content lines (top → bottom) ────────────────────────────────
        List<String> lines = new ArrayList<>();

        int roundNum = plugin.getGameManager().getRoundManager() != null
                ? plugin.getGameManager().getRoundManager().getRoundNumber() : 0;
        GamePhase phase = plugin.getGameManager().getPhase();

        lines.add("§7Round §f" + roundNum + " §8| " + phaseIcon(phase));
        lines.add("§7Alive: §f" + allPlayers.size() + " §7players");
        lines.add(""); // blank

        // Active traits
        List<ChampionInstance> deployed = ap.getBoard() != null
                ? ap.getBoard().getDeployedChampions() : List.of();
        Map<Trait, Integer> traitCounts = traitManager.countTraits(deployed);

        if (traitCounts.isEmpty()) {
            lines.add("§8No active traits");
        } else {
            for (Map.Entry<Trait, Integer> e : traitCounts.entrySet()) {
                Trait t = e.getKey();
                int count = e.getValue();
                int tier  = t.getActiveTier(count);
                int next  = tier < t.getThresholds().length ? t.getThresholds()[tier] : -1;
                String prog = next > 0 ? " §8(" + count + "/" + next + ")" : " §a★";
                String col  = tier > 0 ? "§a" : "§8";
                lines.add(col + t.getDisplayName() + prog);
                if (lines.size() >= MAX_LINES - 5) break; // leave room for HP standings
            }
        }

        lines.add(""); // blank

        // HP standings (sorted high → low)
        lines.add("§7HP Standings:");
        allPlayers.stream()
                .sorted((a, b) -> Integer.compare(b.getHealth(), a.getHealth()))
                .limit(5)
                .forEach(other -> {
                    boolean isSelf = other.getUuid().equals(ap.getUuid());
                    lines.add((isSelf ? "§e▶ " : "§7  ") +
                               other.getPlayer().getName() + " §c" + other.getHealth());
                });

        // ── Apply to scoreboard ───────────────────────────────────────────────
        // Re-register objective fresh each render (clears stale scores)
        Objective old = sb.getObjective("ac_hud");
        if (old != null) old.unregister();

        Objective obj = sb.registerNewObjective("ac_hud", Criteria.DUMMY,
                Component.text("✦ AstralClash ✦", NamedTextColor.LIGHT_PURPLE));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        // Ensure teams exist (one per line slot, re-used across renders)
        for (int i = 0; i < MAX_LINES; i++) {
            String teamName = "ac_l" + i;
            if (sb.getTeam(teamName) == null) {
                Team t = sb.registerNewTeam(teamName);
                // Each team has one unique entry: a color-code string "§0" … "§f"
                t.addEntry("§" + Integer.toHexString(i));
            }
        }

        // Fill lines top-to-bottom (higher score = higher position)
        int totalLines = lines.size();
        for (int i = 0; i < totalLines && i < MAX_LINES; i++) {
            String entry   = "§" + Integer.toHexString(i);
            String content = lines.get(i);
            sb.getTeam("ac_l" + i).prefix(Component.text(content));
            obj.getScore(entry).setScore(totalLines - i);
        }

        player.setScoreboard(sb);
    }

    /** Clears the AstralClash scoreboard and restores the server default. */
    public void clearScoreboard(UUID uuid) {
        playerScoreboards.remove(uuid);
        Player p = Bukkit.getPlayer(uuid);
        if (p != null && p.isOnline()) {
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    public void clearAllScoreboards() {
        for (UUID uuid : playerScoreboards.keySet()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
        }
        playerScoreboards.clear();
    }

    // ── Floating damage numbers ───────────────────────────────────────────────

    /**
     * Spawns a TextDisplay entity showing the damage number above the hit location.
     * Must be called from the main thread (or scheduled to it).
     */
    public void showDamageNumber(Location loc, double damage, boolean isCrit) {
        if (!plugin.getConfigManager().isShowDamageNumbers()) return;
        if (loc == null || loc.getWorld() == null) return;

        Location displayLoc = loc.clone().add(0, 2.2, 0);

        TextDisplay td = (TextDisplay) loc.getWorld().spawnEntity(displayLoc, EntityType.TEXT_DISPLAY);
        String prefix = isCrit ? "✦ " : "";
        NamedTextColor color = isCrit ? NamedTextColor.YELLOW : NamedTextColor.WHITE;
        td.text(Component.text(prefix + (int) damage, color));
        td.setBillboard(Display.Billboard.CENTER);
        td.setDefaultBackground(false);
        td.setShadowed(true);
        td.setViewRange(16f);

        // Remove after 1.5 seconds
        Bukkit.getScheduler().runTaskLater(plugin, td::remove, 30L);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String phaseIcon(GamePhase phase) {
        return switch (phase) {
            case PLANNING -> "§bPlanning";
            case COMBAT   -> "§c⚔ Combat";
            case RESULTS  -> "§eResults";
            default       -> "§7Waiting";
        };
    }
}
