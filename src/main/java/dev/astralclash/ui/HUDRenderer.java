package dev.astralclash.ui;

import dev.astralclash.AstralClash;
import dev.astralclash.champion.ChampionInstance;
import dev.astralclash.champion.trait.Trait;
import dev.astralclash.champion.trait.TraitManager;
import dev.astralclash.game.GamePhase;
import dev.astralclash.player.ArenaPlayer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

/**
 * Renders the player HUD using:
 * <ul>
 *   <li>Action bar — quick status (gold, HP, round, level)</li>
 *   <li>Sidebar scoreboard — traits, unit count, standings</li>
 *   <li>Boss bar — phase timer (managed by {@link UIManager})</li>
 * </ul>
 */
public class HUDRenderer {

    private final AstralClash  plugin;
    private final TraitManager traitManager = new TraitManager();

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

        int deployed = ap.getDeployedCount();
        int maxDeploy = ap.getBoardSizeLimit();

        Component bar = Component.text(
                "§6♦ " + ap.getGold() + " gold  " +
                "§c♥ " + ap.getHealth() + " HP  " +
                "§a⬡ " + deployed + "/" + maxDeploy + " units  " +
                "§eLv" + ap.getLevel() + "  " +
                phaseStr);

        ap.getPlayer().sendActionBar(bar);
    }

    // ── Scoreboard ────────────────────────────────────────────────────────────

    /**
     * Updates the player's sidebar scoreboard with trait info and standings.
     * Uses vanilla scoreboard — PacketEvents can be used for fancier display.
     */
    public void renderScoreboard(ArenaPlayer ap, List<ArenaPlayer> allPlayers) {
        Player player = ap.getPlayer();

        // Build trait summary lines
        List<ChampionInstance> deployed = ap.getBoard() != null
                ? ap.getBoard().getDeployedChampions() : List.of();
        Map<Trait, Integer> traitCounts = traitManager.countTraits(deployed);

        // Use sendMessage for a simple MVP — a real impl would use ScoreboardManager
        StringBuilder sb = new StringBuilder();
        sb.append("§5§l✦ AstralClash ✦\n");
        sb.append("§7Round: §f").append(
                plugin.getGameManager().getRoundManager() != null
                        ? plugin.getGameManager().getRoundManager().getRoundNumber() : 0).append("\n");
        sb.append("§7Players alive: §f").append(allPlayers.size()).append("\n");
        sb.append("\n§7Active Traits:\n");

        for (Map.Entry<Trait, Integer> e : traitCounts.entrySet()) {
            Trait t = e.getKey();
            int count = e.getValue();
            int tier = t.getActiveTier(count);
            String color = tier > 0 ? "§a" : "§8";
            int next = tier < t.getThresholds().length ? t.getThresholds()[tier] : -1;
            String nextStr = next > 0 ? "§7(" + count + "/" + next + ")" : "§a(MAX)";
            sb.append(color).append(t.getDisplayName()).append(" ").append(nextStr).append("\n");
        }

        // Health standings
        sb.append("\n§7HP Standings:\n");
        allPlayers.stream()
                .sorted((a, b) -> Integer.compare(b.getHealth(), a.getHealth()))
                .forEach(other -> sb.append(
                        other.getUuid().equals(ap.getUuid()) ? "§e" : "§7")
                        .append(other.getPlayer().getName())
                        .append(" §c").append(other.getHealth()).append("HP\n"));

        // We log to player chat in MVP; real impl would use scoreboard sidebar
        // player.sendMessage(sb.toString());
        // ^ Commented out to avoid chat spam; action bar + bossbar is enough for MVP
    }

    // ── Floating damage numbers ───────────────────────────────────────────────

    /**
     * Spawns a floating text entity showing damage at a world location.
     * Uses PacketEvents to send a fake ArmorStand with a custom name.
     * For MVP we'll trigger a particle burst instead (simpler, no armorstand cleanup).
     */
    public void showDamageNumber(org.bukkit.Location loc, double damage, boolean isCrit) {
        if (!plugin.getConfigManager().isShowDamageNumbers()) return;
        // Particle flash to indicate the hit (particle system as stand-in for floating text)
        var particle = isCrit
                ? org.bukkit.Particle.CRIT_MAGIC
                : org.bukkit.Particle.DAMAGE_INDICATOR;
        loc.getWorld().spawnParticle(particle, loc.add(0, 1.5, 0),
                isCrit ? 12 : 6, 0.2, 0.2, 0.2, 0.05);
    }
}
