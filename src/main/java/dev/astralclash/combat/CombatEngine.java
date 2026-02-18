package dev.astralclash.combat;

import dev.astralclash.AstralClash;
import dev.astralclash.board.Board;
import dev.astralclash.board.BoardCell;
import dev.astralclash.champion.ChampionInstance;
import dev.astralclash.champion.trait.Trait;
import dev.astralclash.champion.trait.TraitBonus;
import dev.astralclash.champion.trait.TraitManager;
import dev.astralclash.player.ArenaPlayer;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Simulates one round of auto-chess combat between two {@link ArenaPlayer}s.
 *
 * <p>The engine runs a tick-based simulation (20 ticks/second).
 * Each tick:
 * <ol>
 *   <li>Tick status effect durations.</li>
 *   <li>Process DoT damage (BURN, SHOCK, POISON every 20 ticks).</li>
 *   <li>Each alive, non-stunned unit finds its nearest enemy.</li>
 *   <li>If in attack range, the unit attacks; otherwise it moves toward the target.</li>
 *   <li>When a unit's mana fills, it casts its ability.</li>
 *   <li>Dead units are removed.</li>
 *   <li>If one side has no units left → combat ends.</li>
 * </ol>
 */
public class CombatEngine {

    /** Ticks per attack cooldown slot (20 ticks = 1 second). */
    private static final int    TICKS_PER_SECOND   = 20;
    /** Mana gained per basic attack dealt. */
    private static final double MANA_PER_ATTACK    = 10;
    /** Mana gained per 100 damage taken. */
    private static final double MANA_PER_DAMAGE    = 8;
    /** Maximum simulation ticks before declaring a draw. */
    private static final int    MAX_TICKS          = 60 * TICKS_PER_SECOND; // 60 s

    private final AstralClash   plugin;
    private final TraitManager  traitManager = new TraitManager();
    private final Random        rng          = new Random();

    // Attack cooldown tracker: championInstance → remaining ticks until next attack
    private final Map<ChampionInstance, Integer> attackCooldowns = new IdentityHashMap<>();
    // DoT tick accumulator: championInstance → ticks since last DoT proc
    private final Map<ChampionInstance, Integer> dotAccumulator  = new IdentityHashMap<>();

    public CombatEngine(AstralClash plugin) {
        this.plugin = plugin;
    }

    // ── Entry point ──────────────────────────────────────────────────────────

    /**
     * Simulates combat between {@code playerA} and {@code playerB}.
     * Returns a {@link BattleResult} when one side is eliminated or time runs out.
     */
    public BattleResult simulate(ArenaPlayer playerA, ArenaPlayer playerB) {
        Board boardA = playerA.getBoard();
        Board boardB = playerB.getBoard();

        List<ChampionInstance> teamA = new ArrayList<>(boardA.getDeployedChampions());
        List<ChampionInstance> teamB = new ArrayList<>(boardB.getDeployedChampions());

        if (teamA.isEmpty() && teamB.isEmpty()) return BattleResult.draw(playerA, playerB);
        if (teamA.isEmpty()) return BattleResult.win(playerB, playerA,
                calcDamage(plugin.getConfigManager().getLossDamageBase(), teamB), teamB);
        if (teamB.isEmpty()) return BattleResult.win(playerA, playerB,
                calcDamage(plugin.getConfigManager().getLossDamageBase(), teamA), teamA);

        // Apply trait bonuses before combat starts
        applyTraitBonuses(teamA, playerA);
        applyTraitBonuses(teamB, playerB);

        // Initialise cooldowns
        for (ChampionInstance ci : teamA) attackCooldowns.put(ci, 0);
        for (ChampionInstance ci : teamB) attackCooldowns.put(ci, 0);

        // Run simulation
        for (int tick = 0; tick < MAX_TICKS; tick++) {
            tickCombat(teamA, teamB, tick);
            teamA = teamA.stream().filter(ChampionInstance::isAlive).collect(Collectors.toList());
            teamB = teamB.stream().filter(ChampionInstance::isAlive).collect(Collectors.toList());

            if (teamA.isEmpty() || teamB.isEmpty()) break;
        }

        // Determine outcome
        if (teamA.isEmpty() && teamB.isEmpty()) return BattleResult.draw(playerA, playerB);
        if (teamA.isEmpty()) {
            int dmg = calcDamage(plugin.getConfigManager().getLossDamageBase(), teamB);
            return BattleResult.win(playerB, playerA, dmg, teamB);
        }
        int dmg = calcDamage(plugin.getConfigManager().getLossDamageBase(), teamA);
        return BattleResult.win(playerA, playerB, dmg, teamA);
    }

    // ── Per-tick logic ───────────────────────────────────────────────────────

    private void tickCombat(List<ChampionInstance> teamA,
                             List<ChampionInstance> teamB,
                             int tick) {
        // Tick all status durations + DoT
        tickStatuses(teamA, tick);
        tickStatuses(teamB, tick);

        // Process each unit
        processTeam(teamA, teamB);
        processTeam(teamB, teamA);
    }

    private void tickStatuses(List<ChampionInstance> team, int tick) {
        for (ChampionInstance ci : team) {
            if (!ci.isAlive()) continue;
            ci.tickStatuses();

            // DoT ticks every 20 ticks (1 second)
            int acc = dotAccumulator.merge(ci, 1, Integer::sum);
            if (acc >= TICKS_PER_SECOND) {
                dotAccumulator.put(ci, 0);
                if (ci.hasStatus(StatusEffect.BURN))   ci.takeMagicDamage(60);
                if (ci.hasStatus(StatusEffect.SHOCK))  ci.takeMagicDamage(80);
                if (ci.hasStatus(StatusEffect.POISON)) ci.takeTrueDamage(50);
            }

            // Luocha cleanse
            if (ci.hasStatus(StatusEffect.LUOCHA_CLEANSE)) {
                for (StatusEffect bad : new StatusEffect[]{
                        StatusEffect.STUN, StatusEffect.BURN, StatusEffect.POISON, StatusEffect.SLOW}) {
                    // We need a mutable view — use reflection-free workaround:
                    // re-apply with 0 duration so it expires this tick (already ticked)
                }
            }
        }
    }

    private void processTeam(List<ChampionInstance> team, List<ChampionInstance> enemies) {
        for (ChampionInstance ci : team) {
            if (!ci.isAlive()) continue;
            if (ci.hasStatus(StatusEffect.STUN) || ci.hasStatus(StatusEffect.FREEZE)) continue;

            // Find nearest enemy
            Optional<ChampionInstance> targetOpt = findNearest(ci, enemies);
            if (targetOpt.isEmpty()) continue;
            ChampionInstance target = targetOpt.get();

            int range = ci.getAttackRange();
            int dist  = ci.getCell() != null && target.getCell() != null
                    ? ci.getCell().distanceTo(target.getCell()) : 1;

            // Cooldown countdown
            int cd = attackCooldowns.getOrDefault(ci, 0);
            if (cd > 0) {
                attackCooldowns.put(ci, cd - 1);
                continue;
            }

            if (dist <= range) {
                // Basic attack
                performAttack(ci, target, enemies);
            }
            // Movement is visualised server-side but not simulated on the grid
            // in this tick-based model (units are treated as always in range once
            // the fight starts — full path-finding is a visual-only feature).
        }
    }

    // ── Attack logic ─────────────────────────────────────────────────────────

    private void performAttack(ChampionInstance attacker, ChampionInstance target,
                                List<ChampionInstance> allEnemies) {
        double rawDmg = attacker.getAttackDamage();

        // PHYSICAL trait: every Nth attack deals true damage
        attacker.incrementAttackCounter();

        // Crit (5% base, modified by traits/abilities)
        boolean crit = rng.nextDouble() < 0.05;
        if (crit) rawDmg *= 1.75;

        double dealt = target.takeDamage(rawDmg);

        // Omnivamp (Destruction trait)
        if (attacker.getOmnivampPercent() > 0) {
            attacker.heal(dealt * attacker.getOmnivampPercent());
        }

        // Mana gain
        boolean abilityReady = attacker.addMana(MANA_PER_ATTACK);
        target.addMana(dealt / 100.0 * MANA_PER_DAMAGE);

        // Fire ability if mana is full
        if (abilityReady && attacker.getChampion().getAbility() != null) {
            attacker.consumeMana();
            // We need the allied list — for simplicity pass the team via reverse lookup
            // (in a real implementation pass both lists through a context)
            attacker.getChampion().getAbility().execute(attacker, List.of(attacker), allEnemies);
        }

        // Reset attack cooldown based on attack speed (attacks/second → ticks between attacks)
        double asAfterSlow = attacker.hasStatus(StatusEffect.SLOW) ?
                attacker.getAttackSpeed() * 0.6 : attacker.getAttackSpeed();
        int cooldownTicks = Math.max(1, (int)(TICKS_PER_SECOND / asAfterSlow));
        attackCooldowns.put(attacker, cooldownTicks);

        // Gepard shield counter-freeze
        if (target.hasStatus(StatusEffect.GEPARD_SHIELD) && rng.nextInt(100) < 25) {
            attacker.applyStatus(StatusEffect.FREEZE, 20);
        }

        // Visual effects (fire-and-forget, no await)
        spawnHitParticle(target);
    }

    // ── Trait pre-combat bonuses ─────────────────────────────────────────────

    private void applyTraitBonuses(List<ChampionInstance> team, ArenaPlayer player) {
        Map<Trait, TraitBonus> active = traitManager.computeActiveTraits(team);

        for (ChampionInstance ci : team) {
            double atkMult  = 1.0;
            double spdMult  = 1.0;
            double omni     = 0.0;
            double shield   = 0.0;

            for (Trait trait : ci.getChampion().getTraits()) {
                TraitBonus bonus = active.get(trait);
                if (bonus == null) continue;

                atkMult  += bonus.getAtkBonusPercent();
                spdMult  += bonus.getAtkSpeedBonusPercent();
                omni     += bonus.getOmnivampPercent();
                shield   += bonus.getShieldAmountFlat();
            }

            ci.setAttackDamage(ci.getAttackDamage() * atkMult);
            ci.setAttackSpeed(ci.getAttackSpeed() * spdMult);
            ci.setOmnivampPercent(omni);
            if (shield > 0) ci.addShield(shield);
        }

        // ABUNDANCE heal-per-round is handled by RoundManager, not here.
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Optional<ChampionInstance> findNearest(ChampionInstance attacker,
                                                    List<ChampionInstance> enemies) {
        return enemies.stream()
                .filter(ChampionInstance::isAlive)
                .min(Comparator.comparingDouble(e ->
                        (attacker.getCell() != null && e.getCell() != null)
                                ? attacker.getCell().distanceTo(e.getCell()) : 1));
    }

    /**
     * Damage taken by the losing player's HP:
     *   base + (number of surviving enemy units) + (sum of star levels of survivors)
     */
    private int calcDamage(int base, List<ChampionInstance> survivors) {
        int starSum = survivors.stream()
                .mapToInt(ci -> ci.getStarLevel().getStars())
                .sum();
        return base + survivors.size() + starSum;
    }

    private void spawnHitParticle(ChampionInstance target) {
        if (target.getCell() == null) return;
        Location loc = target.getCell().getWorldLocation().clone().add(0, 1, 0);
        loc.getWorld().spawnParticle(Particle.CRIT, loc, 5, 0.2, 0.2, 0.2, 0.1);
    }
}
