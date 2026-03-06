package dev.astralclash.ui;

import dev.astralclash.AstralClash;
import dev.astralclash.champion.ChampionInstance;
import dev.astralclash.champion.trait.Trait;
import dev.astralclash.champion.trait.TraitManager;
import dev.astralclash.player.ArenaPlayer;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.scoreboard.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Renders beautiful combat UI elements:
 * - Turn order display (who attacks next)
 * - Active synergies/traits with tier indicators
 * - Combat phase boss bar
 * - Health standings
 */
public class CombatUI {

    private static final int MAX_SIDEBAR_LINES = 15;
    
    // Trait tier colors (progressively more golden/valuable)
    private static final TextColor TIER_0_COLOR = TextColor.color(0x555555);  // Gray (inactive)
    private static final TextColor TIER_1_COLOR = TextColor.color(0xAA5500);  // Bronze
    private static final TextColor TIER_2_COLOR = TextColor.color(0xC0C0C0);  // Silver
    private static final TextColor TIER_3_COLOR = TextColor.color(0xFFD700);  // Gold
    
    // Unicode symbols for beautiful display
    private static final String STAR_EMPTY = "☆";
    private static final String STAR_FILLED = "★";
    private static final String SWORD = "⚔";
    private static final String SHIELD = "🛡";
    private static final String HEART = "♥";
    private static final String DIAMOND = "◆";
    private static final String ARROW_RIGHT = "▶";
    private static final String CHECKMARK = "✓";
    
    private final AstralClash plugin;
    private final TraitManager traitManager = new TraitManager();
    private final Map<UUID, Scoreboard> combatScoreboards = new HashMap<>();
    private final Map<UUID, BossBar> combatBossBars = new HashMap<>();
    
    public CombatUI(AstralClash plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Renders the combat scoreboard with traits and turn order.
     */
    public void renderCombatScoreboard(ArenaPlayer ap, List<ChampionInstance> myTeam,
                                       List<ChampionInstance> enemyTeam,
                                       ChampionInstance currentAttacker) {
        var player = ap.getPlayer();
        if (player == null || !player.isOnline()) return;
        
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        Scoreboard sb = combatScoreboards.computeIfAbsent(ap.getUuid(),
            k -> manager.getNewScoreboard());
        
        // Clear old objective
        Objective old = sb.getObjective("combat_ui");
        if (old != null) old.unregister();
        
        // Create new objective with beautiful title
        Objective obj = sb.registerNewObjective("combat_ui", Criteria.DUMMY,
            Component.text("✦ ", NamedTextColor.GOLD)
                .append(Component.text("AstralClash", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD))
                .append(Component.text(" ✦", NamedTextColor.GOLD)));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        
        List<String> lines = new ArrayList<>();
        
        // ── Header: Combat Phase ──
        lines.add("§c§l" + SWORD + " COMBAT " + SWORD);
        lines.add("");
        
        // ── Turn Order Section ──
        lines.add("§e§lTurn Order:");
        List<ChampionInstance> allUnits = new ArrayList<>();
        allUnits.addAll(myTeam.stream().filter(ChampionInstance::isAlive).toList());
        allUnits.addAll(enemyTeam.stream().filter(ChampionInstance::isAlive).toList());
        
        // Sort by attack speed (faster = acts first)
        allUnits.sort((a, b) -> Double.compare(b.getAttackSpeed(), a.getAttackSpeed()));
        
        int shown = 0;
        for (ChampionInstance ci : allUnits) {
            if (shown >= 5) break;
            
            boolean isMine = myTeam.contains(ci);
            boolean isCurrent = ci.equals(currentAttacker);
            String prefix = isCurrent ? "§f" + ARROW_RIGHT + " " : "  ";
            String color = isMine ? "§a" : "§c";
            String stars = getStarDisplay(ci.getStarLevel().getStars());
            
            lines.add(prefix + color + ci.getChampion().getDisplayName() + " " + stars);
            shown++;
        }
        
        lines.add("");
        
        // ── Active Synergies Section ──
        lines.add("§d§lSynergies:");
        Map<Trait, Integer> traitCounts = traitManager.countTraits(myTeam);
        
        // Sort traits: active first, then by tier
        List<Map.Entry<Trait, Integer>> sortedTraits = traitCounts.entrySet().stream()
            .sorted((a, b) -> {
                int tierA = a.getKey().getActiveTier(a.getValue());
                int tierB = b.getKey().getActiveTier(b.getValue());
                if (tierA != tierB) return tierB - tierA; // Higher tier first
                return b.getValue() - a.getValue(); // Then by count
            })
            .collect(Collectors.toList());
        
        int traitShown = 0;
        for (var entry : sortedTraits) {
            if (traitShown >= 6) break;
            
            Trait trait = entry.getKey();
            int count = entry.getValue();
            int tier = trait.getActiveTier(count);
            int[] thresholds = trait.getThresholds();
            
            // Build progress display
            String tierColor = getTierColorCode(tier);
            String progressBar = buildTraitProgress(count, thresholds, tier);
            
            lines.add(tierColor + trait.getDisplayName() + " §7" + progressBar);
            traitShown++;
        }
        
        if (traitCounts.isEmpty()) {
            lines.add("§8(No synergies)");
        }
        
        lines.add("");
        
        // ── HP Bar ──
        lines.add("§c" + HEART + " §7HP: §f" + ap.getHealth() + "/100");
        lines.add("§6" + DIAMOND + " §7Gold: §f" + ap.getGold());
        
        // Apply lines to scoreboard using teams
        ensureTeams(sb, MAX_SIDEBAR_LINES);
        
        int totalLines = Math.min(lines.size(), MAX_SIDEBAR_LINES);
        for (int i = 0; i < totalLines; i++) {
            String entry = "§" + Integer.toHexString(i);
            Team team = sb.getTeam("combat_l" + i);
            if (team != null) {
                team.prefix(Component.text(lines.get(i)));
                obj.getScore(entry).setScore(totalLines - i);
            }
        }
        
        player.setScoreboard(sb);
    }
    
    /**
     * Renders the planning phase scoreboard with synergies.
     */
    public void renderPlanningScoreboard(ArenaPlayer ap, List<ArenaPlayer> allPlayers) {
        var player = ap.getPlayer();
        if (player == null || !player.isOnline()) return;
        
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        Scoreboard sb = combatScoreboards.computeIfAbsent(ap.getUuid(),
            k -> manager.getNewScoreboard());
        
        // Clear old objective
        Objective old = sb.getObjective("combat_ui");
        if (old != null) old.unregister();
        
        Objective obj = sb.registerNewObjective("combat_ui", Criteria.DUMMY,
            Component.text("✦ ", NamedTextColor.GOLD)
                .append(Component.text("AstralClash", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD))
                .append(Component.text(" ✦", NamedTextColor.GOLD)));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        
        List<String> lines = new ArrayList<>();
        
        // ── Header ──
        int roundNum = plugin.getGameManager().getRoundManager() != null
            ? plugin.getGameManager().getRoundManager().getRoundNumber() : 0;
        lines.add("§b§lPLANNING §7- Round " + roundNum);
        lines.add("");
        
        // ── Your Stats ──
        lines.add("§e§lYour Stats:");
        lines.add("§c" + HEART + " HP: §f" + ap.getHealth());
        lines.add("§6" + DIAMOND + " Gold: §f" + ap.getGold());
        lines.add("§a⬡ Units: §f" + ap.getDeployedCount() + "/" + ap.getBoardSizeLimit());
        lines.add("§bLv " + ap.getLevel() + " §7(" + ap.getXp() + " XP)");
        lines.add("");
        
        // ── Active Synergies ──
        lines.add("§d§lActive Synergies:");
        
        List<ChampionInstance> deployed = ap.getBoard() != null
            ? ap.getBoard().getDeployedChampions() : List.of();
        Map<Trait, Integer> traitCounts = traitManager.countTraits(deployed);
        
        List<Map.Entry<Trait, Integer>> sortedTraits = traitCounts.entrySet().stream()
            .filter(e -> e.getKey().getActiveTier(e.getValue()) > 0)
            .sorted((a, b) -> {
                int tierA = a.getKey().getActiveTier(a.getValue());
                int tierB = b.getKey().getActiveTier(b.getValue());
                return tierB - tierA;
            })
            .collect(Collectors.toList());
        
        int traitShown = 0;
        for (var entry : sortedTraits) {
            if (traitShown >= 5) break;
            
            Trait trait = entry.getKey();
            int count = entry.getValue();
            int tier = trait.getActiveTier(count);
            
            String tierColor = getTierColorCode(tier);
            String stars = STAR_FILLED.repeat(tier);
            
            lines.add(tierColor + trait.getDisplayName() + " §e" + stars + " §8(" + count + ")");
            traitShown++;
        }
        
        if (sortedTraits.isEmpty()) {
            lines.add("§8(Deploy units for synergies)");
        }
        
        lines.add("");
        
        // ── HP Standings ──
        lines.add("§7§lStandings:");
        allPlayers.stream()
            .sorted((a, b) -> Integer.compare(b.getHealth(), a.getHealth()))
            .limit(4)
            .forEach(other -> {
                boolean isSelf = other.getUuid().equals(ap.getUuid());
                String prefix = isSelf ? "§e" + ARROW_RIGHT + " " : "§7  ";
                String name = other.getPlayer() != null ? other.getPlayer().getName() : "Bot";
                lines.add(prefix + name + " §c" + other.getHealth());
            });
        
        // Apply to scoreboard
        ensureTeams(sb, MAX_SIDEBAR_LINES);
        
        int totalLines = Math.min(lines.size(), MAX_SIDEBAR_LINES);
        for (int i = 0; i < totalLines; i++) {
            String entry = "§" + Integer.toHexString(i);
            Team team = sb.getTeam("combat_l" + i);
            if (team != null) {
                team.prefix(Component.text(lines.get(i)));
                obj.getScore(entry).setScore(totalLines - i);
            }
        }
        
        player.setScoreboard(sb);
    }
    
    /**
     * Shows a combat boss bar with phase timer.
     */
    public void showCombatBossBar(ArenaPlayer ap, String title, int maxSeconds) {
        if (ap.getPlayer() == null || !ap.getPlayer().isOnline()) return;
        BossBar bar = BossBar.bossBar(
            Component.text(title, NamedTextColor.RED, TextDecoration.BOLD),
            1.0f,
            BossBar.Color.RED,
            BossBar.Overlay.NOTCHED_10);
        
        combatBossBars.put(ap.getUuid(), bar);
        ap.getPlayer().showBossBar(bar);
    }
    
    /**
     * Updates the combat boss bar progress.
     */
    public void updateCombatBossBar(ArenaPlayer ap, float progress, String text) {
        BossBar bar = combatBossBars.get(ap.getUuid());
        if (bar != null) {
            bar.progress(Math.max(0, Math.min(1, progress)));
            bar.name(Component.text(text, NamedTextColor.RED));
        }
    }
    
    /**
     * Hides the combat boss bar.
     */
    public void hideCombatBossBar(ArenaPlayer ap) {
        BossBar bar = combatBossBars.remove(ap.getUuid());
        if (bar != null && ap.getPlayer() != null) {
            ap.getPlayer().hideBossBar(bar);
        }
    }

    /**
     * Shows round results with "Damage dealt" panel (like the screenshot's Урон tab).
     * Updates the player's sidebar with champion names and damage numbers, sorted by damage.
     */
    public void renderRoundDamageReport(ArenaPlayer ap, Map<ChampionInstance, Double> fullDamageMap) {
        var player = ap.getPlayer();
        if (player == null || !player.isOnline() || ap.getBoard() == null) return;

        List<ChampionInstance> myChamps = new ArrayList<>(ap.getBoard().getDeployedChampions());
        myChamps.sort(Comparator.comparingDouble(ci -> -fullDamageMap.getOrDefault(ci, 0.0)));

        ScoreboardManager manager = Bukkit.getScoreboardManager();
        Scoreboard sb = combatScoreboards.computeIfAbsent(ap.getUuid(), k -> manager.getNewScoreboard());

        Objective old = sb.getObjective("combat_ui");
        if (old != null) old.unregister();

        int roundNum = plugin.getGameManager().getRoundManager() != null
            ? plugin.getGameManager().getRoundManager().getRoundNumber() : 0;
        Objective obj = sb.registerNewObjective("combat_ui", Criteria.DUMMY,
            Component.text("§e§lRound " + roundNum + " — Results", NamedTextColor.GOLD));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        List<String> lines = new ArrayList<>();
        lines.add("");
        lines.add("§6§lDamage dealt:");
        int shown = 0;
        for (ChampionInstance ci : myChamps) {
            if (shown >= 8) break;
            double d = fullDamageMap.getOrDefault(ci, 0.0);
            if (d <= 0) continue;
            String stars = getStarDisplay(ci.getStarLevel().getStars());
            String name = ci.getChampion().getDisplayName() + " " + stars;
            lines.add("§7 " + name + " §f" + (int) d);
            shown++;
        }
        if (shown == 0) lines.add("§8(No damage)");
        lines.add("");
        lines.add("§c" + HEART + " §f" + ap.getHealth() + " §7HP");
        lines.add("§6" + DIAMOND + " §f" + ap.getGold() + " §7gold");

        ensureTeams(sb, MAX_SIDEBAR_LINES);
        int totalLines = Math.min(lines.size(), MAX_SIDEBAR_LINES);
        for (int i = 0; i < totalLines; i++) {
            String entry = "§" + Integer.toHexString(i);
            Team team = sb.getTeam("combat_l" + i);
            if (team != null) {
                team.prefix(Component.text(lines.get(i)));
                obj.getScore(entry).setScore(totalLines - i);
            }
        }
        player.setScoreboard(sb);
    }
    
    /**
     * Clears all combat UI for a player.
     */
    public void clearCombatUI(UUID uuid) {
        combatScoreboards.remove(uuid);
        BossBar bar = combatBossBars.remove(uuid);
        var player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            if (bar != null) player.hideBossBar(bar);
        }
    }
    
    /**
     * Clears all combat UI.
     */
    public void clearAll() {
        for (UUID uuid : new HashSet<>(combatScoreboards.keySet())) {
            clearCombatUI(uuid);
        }
    }
    
    // ── Helper methods ──
    
    private void ensureTeams(Scoreboard sb, int count) {
        for (int i = 0; i < count; i++) {
            String teamName = "combat_l" + i;
            if (sb.getTeam(teamName) == null) {
                Team t = sb.registerNewTeam(teamName);
                t.addEntry("§" + Integer.toHexString(i));
            }
        }
    }
    
    private String getStarDisplay(int stars) {
        return switch (stars) {
            case 1 -> "§7" + STAR_FILLED;
            case 2 -> "§e" + STAR_FILLED + STAR_FILLED;
            case 3 -> "§6" + STAR_FILLED + STAR_FILLED + STAR_FILLED;
            default -> "";
        };
    }
    
    private String getTierColorCode(int tier) {
        return switch (tier) {
            case 0 -> "§8";  // Gray
            case 1 -> "§6";  // Bronze/Gold
            case 2 -> "§f";  // Silver/White
            case 3 -> "§e";  // Gold/Yellow
            default -> "§d"; // Purple for max
        };
    }
    
    private String buildTraitProgress(int count, int[] thresholds, int activeTier) {
        if (thresholds.length == 0) return "(" + count + ")";
        
        StringBuilder sb = new StringBuilder();
        sb.append("(").append(count);
        
        if (activeTier < thresholds.length) {
            sb.append("/").append(thresholds[activeTier]);
        }
        
        sb.append(")");
        
        // Add tier indicators
        for (int i = 0; i < thresholds.length; i++) {
            if (i < activeTier) {
                sb.append(" §a").append(CHECKMARK);
            }
        }
        
        return sb.toString();
    }
}
