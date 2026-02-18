package dev.astralclash.combat;

import dev.astralclash.AstralClash;
import dev.astralclash.board.Board;
import dev.astralclash.champion.ChampionInstance;
import dev.astralclash.champion.trait.Trait;
import dev.astralclash.champion.trait.TraitBonus;
import dev.astralclash.champion.trait.TraitManager;
import dev.astralclash.player.ArenaPlayer;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Simulates one round of auto-chess combat between two {@link ArenaPlayer}s.
 *
 * <p>The engine runs a tick-based simulation (20 ticks/second).
 * Each tick:
 * <ol>
 *   <li>Tick status effect durations; handle expiry side-effects (e.g. Bronya buff revert).</li>
 *   <li>Process DoT damage (BURN, SHOCK, POISON every 20 ticks).</li>
 *   <li>Each alive, non-stunned unit finds its nearest enemy.</li>
 *   <li>If in attack range, the unit attacks; otherwise waits (visual movement only).</li>
 *   <li>When a unit's mana fills, it casts its ability.</li>
 *   <li>Dead units are removed.</li>
 *   <li>If one side has no units left → combat ends.</li>
 * </ol>
 */
public class CombatEngine {

    /** Ticks per second (20 ticks = 1 second). */
    private static final int    TICKS_PER_SECOND = 20;
    /** Mana gained per basic attack dealt. */
    private static final double MANA_PER_ATTACK  = 10;
    /** Mana gained per 100 damage taken. */
    private static final double MANA_PER_DAMAGE  = 8;
    /** Maximum simulation ticks before declaring a draw. */
    private static final int    MAX_TICKS        = 60 * TICKS_PER_SECOND; // 60 s

    private final AstralClash  plugin;
    private final TraitManager traitManager = new TraitManager();
    private final Random       rng          = new Random();

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
        tickStatuses(teamA, tick);
        tickStatuses(teamB, tick);

        processTeam(teamA, teamB);
        processTeam(teamB, teamA);
    }

    private void tickStatuses(List<ChampionInstance> team, int tick) {
        for (ChampionInstance ci : team) {
            if (!ci.isAlive()) continue;

            // Tick durations; get set of effects that expired this tick
            Set<StatusEffect> expired = ci.tickStatuses();

            // Bronya buff revert: when HARMONY_BUFF expires, subtract the stored bonus ATK
            if (expired.contains(StatusEffect.HARMONY_BUFF) && ci.getHarmonyBuffBonus() > 0) {
                double revertedAtk = Math.max(1.0, ci.getAttackDamage() - ci.getHarmonyBuffBonus());
                ci.setAttackDamage(revertedAtk);
                ci.setHarmonyBuffBonus(0);
            }

            // DoT ticks every 20 ticks (1 second)
            int acc = dotAccumulator.merge(ci, 1, Integer::sum);
            if (acc >= TICKS_PER_SECOND) {
                dotAccumulator.put(ci, 0);
                if (ci.hasStatus(StatusEffect.BURN))   ci.takeMagicDamage(60);
                if (ci.hasStatus(StatusEffect.SHOCK))  ci.takeMagicDamage(80);
                if (ci.hasStatus(StatusEffect.POISON)) ci.takeTrueDamage(50);
            }
        }
    }

    private void processTeam(List<ChampionInstance> team, List<ChampionInstance> enemies) {
        for (ChampionInstance ci : team) {
            if (!ci.isAlive()) continue;
            if (ci.hasStatus(StatusEffect.STUN) || ci.hasStatus(StatusEffect.FREEZE)) continue;

            // Find nearest alive enemy
            Optional<ChampionInstance> targetOpt = findNearest(ci, enemies);
            if (targetOpt.isEmpty()) continue;
            ChampionInstance target = targetOpt.get();

            int range = ci.getAttackRange();
            int dist  = (ci.getCell() != null && target.getCell() != null)
                    ? ci.getCell().distanceTo(target.getCell()) : 1;

            // Cooldown countdown
            int cd = attackCooldowns.getOrDefault(ci, 0);
            if (cd > 0) {
                attackCooldowns.put(ci, cd - 1);
                continue;
            }

            if (dist <= range) {
                // Pass the full allied team so abilities can target/buff correct units
                performAttack(ci, target, team, enemies);
            }
            // Movement is visual-only; units are treated as always in range once
            // the fight starts — full path-finding is a future visual feature.
        }
    }

    // ── Attack logic ─────────────────────────────────────────────────────────

    private void performAttack(ChampionInstance attacker, ChampionInstance target,
                                List<ChampionInstance> allies, List<ChampionInstance> allEnemies) {
        double rawDmg = attacker.getAttackDamage();

        // Track basic attack count (PHYSICAL trait every-Nth-attack mechanic)
        attacker.incrementAttackCounter();

        // Crit check (5% base)
        boolean crit = rng.nextDouble() < 0.05;
        if (crit) rawDmg *= 1.75;

        // ── Fu Xuan damage redirect ───────────────────────────────────────────
        // If the target has the FU_XUAN_MATRIX marker, 40% of raw damage is
        // redirected to Fu Xuan (FU_XUAN_TARGET) on the same team.
        double dmgToTarget = rawDmg;
        if (target.hasStatus(StatusEffect.FU_XUAN_MATRIX)) {
            double redirected = rawDmg * 0.40;
            dmgToTarget = rawDmg * 0.60;
            allEnemies.stream()
                    .filter(e -> e.isAlive() && e != target
                              && e.hasStatus(StatusEffect.FU_XUAN_TARGET))
                    .findFirst()
                    .ifPresent(fx -> fx.takeTrueDamage(redirected));
        }

        double dealt = target.takeDamage(dmgToTarget);

        // Omnivamp (Destruction trait)
        if (attacker.getOmnivampPercent() > 0) {
            attacker.heal(dealt * attacker.getOmnivampPercent());
        }

        // Mana gain for attacker and target
        boolean abilityReady = attacker.addMana(MANA_PER_ATTACK);
        target.addMana(dealt / 100.0 * MANA_PER_DAMAGE);

        // Fire ability when mana is full; pass the correct allied team
        if (abilityReady && attacker.getChampion().getAbility() != null) {
            attacker.consumeMana();
            attacker.getChampion().getAbility().execute(attacker, allies, allEnemies);
        }

        // Reset attack cooldown based on attack speed (reduced if slowed)
        double effectiveAS = attacker.hasStatus(StatusEffect.SLOW)
                ? attacker.getAttackSpeed() * 0.6
                : attacker.getAttackSpeed();
        int cooldownTicks = Math.max(1, (int) (TICKS_PER_SECOND / effectiveAS));
        attackCooldowns.put(attacker, cooldownTicks);

        // Gepard shield — counter-freeze on hit (25% proc chance)
        if (target.hasStatus(StatusEffect.GEPARD_SHIELD) && rng.nextInt(100) < 25) {
            attacker.applyStatus(StatusEffect.FREEZE, 20);
        }
    }

    // ── Trait pre-combat bonuses ─────────────────────────────────────────────

    private void applyTraitBonuses(List<ChampionInstance> team, ArenaPlayer player) {
        Map<Trait, TraitBonus> active = traitManager.computeActiveTraits(team);

        for (ChampionInstance ci : team) {
            double atkMult = 1.0;
            double spdMult = 1.0;
            double omni    = 0.0;
            double shield  = 0.0;

            for (Trait trait : ci.getChampion().getTraits()) {
                TraitBonus bonus = active.get(trait);
                if (bonus == null) continue;

                atkMult += bonus.getAtkBonusPercent();
                spdMult += bonus.getAtkSpeedBonusPercent();
                omni    += bonus.getOmnivampPercent();
                shield  += bonus.getShieldAmountFlat();
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
}
