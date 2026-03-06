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
 *   <li>Each alive, non-stunned unit finds its target (HUNT: lowest HP; others: nearest).</li>
 *   <li>If in attack range, the unit attacks; otherwise waits (visual movement only).</li>
 *   <li>When a unit's mana fills, it casts its ability.</li>
 *   <li>DESTRUCTION trait: triggers ATK/omnivamp boost when a unit drops below 50% HP.</li>
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
    /** Damage dealt this round per champion (for post-combat UI). */
    private final Map<ChampionInstance, Double> lastDamageDealt = new IdentityHashMap<>();

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

        lastDamageDealt.clear();

        if (teamA.isEmpty() && teamB.isEmpty()) return BattleResult.draw(playerA, playerB);
        if (teamA.isEmpty()) return BattleResult.win(playerB, playerA,
                calcDamage(plugin.getConfigManager().getLossDamageBase(), teamB), teamB);
        if (teamB.isEmpty()) return BattleResult.win(playerA, playerB,
                calcDamage(plugin.getConfigManager().getLossDamageBase(), teamA), teamA);

        // Apply trait bonuses before combat starts
        applyTraitBonuses(teamA, playerA);
        applyTraitBonuses(teamB, playerB);

        // NIHILITY cross-team: enemies of a NIHILITY team take amplified DoT
        double nihilityMulA = computeNihilityMultiplier(teamA);
        double nihilityMulB = computeNihilityMultiplier(teamB);
        if (nihilityMulA > 1.0) teamB.forEach(ci -> ci.setDotReceivedMultiplier(nihilityMulA));
        if (nihilityMulB > 1.0) teamA.forEach(ci -> ci.setDotReceivedMultiplier(nihilityMulB));

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

        // DESTRUCTION: check if any unit crossed the 50%-HP threshold this tick
        checkDestructionTrigger(teamA);
        checkDestructionTrigger(teamB);
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

            // DoT ticks every 20 ticks (1 second); amplified by NIHILITY on the opposing team
            int acc = dotAccumulator.merge(ci, 1, Integer::sum);
            if (acc >= TICKS_PER_SECOND) {
                dotAccumulator.put(ci, 0);
                double mul = ci.getDotReceivedMultiplier();
                if (ci.hasStatus(StatusEffect.BURN))   ci.takeMagicDamage(60 * mul);
                if (ci.hasStatus(StatusEffect.SHOCK))  ci.takeMagicDamage(80 * mul);
                if (ci.hasStatus(StatusEffect.POISON)) ci.takeTrueDamage(50 * mul);
            }
        }
    }

    private void processTeam(List<ChampionInstance> team, List<ChampionInstance> enemies) {
        for (ChampionInstance ci : team) {
            if (!ci.isAlive()) continue;
            if (ci.hasStatus(StatusEffect.STUN) || ci.hasStatus(StatusEffect.FREEZE)) continue;

            // HUNT trait: target the lowest-HP enemy; others: nearest
            Optional<ChampionInstance> targetOpt = ci.isHasHuntTargeting()
                    ? findLowestHp(enemies)
                    : findNearest(ci, enemies);
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

        // ── IMAGINARY / WEAKNESS: amplify damage if target has Weakness marker ──
        if (target.hasStatus(StatusEffect.WEAKNESS)) {
            dmgToTarget *= 1.5;
            target.removeStatus(StatusEffect.WEAKNESS);
        }

        double dealt = target.takeDamage(dmgToTarget);
        lastDamageDealt.merge(attacker, dealt, Double::sum);

        // Omnivamp healing (also triggers for DESTRUCTION if already proc'd)
        if (attacker.getOmnivampPercent() > 0) {
            attacker.heal(dealt * attacker.getOmnivampPercent());
        }

        // ── Elemental on-hit effects ──────────────────────────────────────────

        // FIRE trait: chance to apply Burn
        if (attacker.getBurnChance() > 0 && rng.nextDouble() < attacker.getBurnChance()) {
            target.applyStatus(StatusEffect.BURN, 40);
        }

        // ICE trait: apply Slow on every hit
        if (attacker.isHasIceSlow()) {
            target.applyStatus(StatusEffect.SLOW, 20);
        }

        // QUANTUM trait: shred target's Magic Resist on every hit
        if (attacker.getMagicResistShred() > 0) {
            target.setMagicResist(Math.max(0, target.getMagicResist() - attacker.getMagicResistShred()));
        }

        // PHYSICAL trait: every N-th basic attack deals 50% ATK as bonus true damage
        if (attacker.getPhysicalBonusEveryN() > 0
                && attacker.getAttackCounter() % attacker.getPhysicalBonusEveryN() == 0) {
            double bonus = attacker.getAttackDamage() * 0.5;
            target.takeTrueDamage(bonus);
            lastDamageDealt.merge(attacker, bonus, Double::sum);
        }

        // Gepard shield — counter-freeze on hit (25% proc chance)
        if (target.hasStatus(StatusEffect.GEPARD_SHIELD) && rng.nextInt(100) < 25) {
            attacker.applyStatus(StatusEffect.FREEZE, 20);
        }

        // Mana gain for attacker and target
        boolean abilityReady = attacker.addMana(MANA_PER_ATTACK);
        target.addMana(dealt / 100.0 * MANA_PER_DAMAGE);

        // ── Ability cast ──────────────────────────────────────────────────────
        if (abilityReady && attacker.getChampion().getAbility() != null) {
            attacker.consumeMana();
            attacker.getChampion().getAbility().execute(attacker, allies, allEnemies);

            // IMAGINARY: on first ability cast, apply Weakness to the primary attack target
            if (attacker.isHasImaginaryTrait() && !attacker.isImaginaryFirstCastDone()) {
                target.applyStatus(StatusEffect.WEAKNESS, 60);
                attacker.setImaginaryFirstCastDone(true);
            }

            // ERUDITION: bonus true-damage pulse to all living enemies after ability
            if (attacker.getAoeBonusPercent() > 0) {
                double bonusDmg = attacker.getAttackDamage() * attacker.getAoeBonusPercent();
                double totalAoe = 0;
                for (ChampionInstance enemy : allEnemies) {
                    if (enemy.isAlive()) {
                        enemy.takeTrueDamage(bonusDmg);
                        totalAoe += bonusDmg;
                    }
                }
                if (totalAoe > 0) lastDamageDealt.merge(attacker, totalAoe, Double::sum);
            }

            // LIGHTNING: chain chainDamagePercent * ATK as magic damage to a random enemy
            if (attacker.getChainDamagePercent() > 0) {
                double chainDmg = attacker.getAttackDamage() * attacker.getChainDamagePercent();
                allEnemies.stream()
                        .filter(ChampionInstance::isAlive)
                        .findAny()
                        .ifPresent(e -> {
                            e.takeMagicDamage(chainDmg);
                            lastDamageDealt.merge(attacker, chainDmg, Double::sum);
                        });
            }
        }

        // Reset attack cooldown based on attack speed (reduced if slowed)
        double effectiveAS = attacker.hasStatus(StatusEffect.SLOW)
                ? attacker.getAttackSpeed() * 0.6
                : attacker.getAttackSpeed();
        int cooldownTicks = Math.max(1, (int) (TICKS_PER_SECOND / effectiveAS));
        attackCooldowns.put(attacker, cooldownTicks);
    }

    // ── DESTRUCTION: mid-combat HP threshold trigger ─────────────────────────

    private void checkDestructionTrigger(List<ChampionInstance> team) {
        for (ChampionInstance ci : team) {
            if (!ci.isAlive()) continue;
            if (!ci.isHasDestructionTrait() || ci.isDestructionTriggered()) continue;
            if (ci.getHpPercent() < 0.5) {
                ci.setDestructionTriggered(true);
                ci.applyStatus(StatusEffect.DESTRUCTION_PROC, Integer.MAX_VALUE);
                ci.setAttackDamage(ci.getAttackDamage() * (1 + ci.getDestructionAtkBonus()));
                ci.setOmnivampPercent(ci.getOmnivampPercent() + ci.getDestructionOmnivampBonus());
            }
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

                switch (trait) {
                    case DESTRUCTION -> {
                        // Store bonuses — applied mid-combat when HP drops below 50%
                        ci.setHasDestructionTrait(true);
                        ci.setDestructionAtkBonus(bonus.getAtkBonusPercent());
                        ci.setDestructionOmnivampBonus(bonus.getOmnivampPercent());
                    }
                    case HUNT -> {
                        ci.setHasHuntTargeting(true);
                        atkMult += bonus.getAtkBonusPercent();
                        spdMult += bonus.getAtkSpeedBonusPercent();
                    }
                    case ERUDITION ->
                        ci.setAoeBonusPercent(ci.getAoeBonusPercent() + bonus.getAoeBonusPercent());
                    case FIRE ->
                        ci.setBurnChance(Math.max(ci.getBurnChance(), bonus.getBurnChance()));
                    case ICE ->
                        ci.setHasIceSlow(true);
                    case LIGHTNING ->
                        ci.setChainDamagePercent(Math.max(ci.getChainDamagePercent(),
                                bonus.getChainDamagePercent()));
                    case QUANTUM ->
                        ci.setMagicResistShred(Math.max(ci.getMagicResistShred(),
                                bonus.getMagicResistShred()));
                    case IMAGINARY ->
                        ci.setHasImaginaryTrait(true);
                    case PHYSICAL -> {
                        int n = (int) bonus.getTrueDamageThreshold();
                        if (n > 0) ci.setPhysicalBonusEveryN(n);
                    }
                    default -> {
                        atkMult += bonus.getAtkBonusPercent();
                        spdMult += bonus.getAtkSpeedBonusPercent();
                        omni    += bonus.getOmnivampPercent();
                        shield  += bonus.getShieldAmountFlat();
                    }
                }
            }

            ci.setAttackDamage(ci.getAttackDamage() * atkMult);
            ci.setAttackSpeed(ci.getAttackSpeed() * spdMult);
            ci.setOmnivampPercent(omni);
            if (shield > 0) ci.addShield(shield);
        }

        // ABUNDANCE heal-per-round is handled by RoundManager, not here.
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Nearest living enemy (default targeting). */
    private Optional<ChampionInstance> findNearest(ChampionInstance attacker,
                                                    List<ChampionInstance> enemies) {
        return enemies.stream()
                .filter(ChampionInstance::isAlive)
                .min(Comparator.comparingDouble(e ->
                        (attacker.getCell() != null && e.getCell() != null)
                                ? attacker.getCell().distanceTo(e.getCell()) : 1));
    }

    /** Lowest-HP% living enemy (HUNT trait targeting). */
    private Optional<ChampionInstance> findLowestHp(List<ChampionInstance> enemies) {
        return enemies.stream()
                .filter(ChampionInstance::isAlive)
                .min(Comparator.comparingDouble(ChampionInstance::getHpPercent));
    }

    /**
     * Returns the NIHILITY dotMultiplier for the team (1.0 if no NIHILITY active).
     * Used to set dotReceivedMultiplier on the opposing team before combat.
     */
    private double computeNihilityMultiplier(List<ChampionInstance> team) {
        Map<Trait, TraitBonus> active = traitManager.computeActiveTraits(team);
        TraitBonus nb = active.get(Trait.NIHILITY);
        return nb != null ? nb.getDotMultiplier() : 1.0;
    }

    /**
     * Returns damage dealt this round per champion (for post-combat UI).
     * Call after {@link #simulate(ArenaPlayer, ArenaPlayer)}.
     */
    public Map<ChampionInstance, Double> getLastDamageDealt() {
        return new IdentityHashMap<>(lastDamageDealt);
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
