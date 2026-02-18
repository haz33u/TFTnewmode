package dev.astralclash.champion.impl;

import dev.astralclash.champion.ChampionInstance;
import dev.astralclash.champion.ability.Ability;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Blade — Tier 5 | Destruction + Wind
 *
 * Ability: "Death Sentence"
 * Blade sacrifices 20% of his own current HP to unleash a 3-target multi-slash.
 * Each slash deals 500 physical damage. He heals for 50% of total damage dealt.
 *
 * Note: Blade has maxMana=0 in config. His ability triggers after taking a certain
 * amount of damage (handled by CombatEngine tracking his DESTRUCTION proc).
 */
public class Blade {

    private Blade() {}

    public static class BladeAbility implements Ability {

        private static final double BASE_DAMAGE      = 500;
        private static final double SELF_COST_PERCENT = 0.20;
        private static final double LIFESTEAL_PERCENT = 0.50;
        private static final int    MAX_TARGETS      = 3;

        @Override
        public String getName()        { return "Death Sentence"; }

        @Override
        public String getDescription() {
            return "Costs 20% of Blade's current HP. Slashes up to 3 enemies for " +
                   BASE_DAMAGE + " physical damage each. Heals 50% of total damage dealt.";
        }

        @Override
        public void execute(ChampionInstance caster,
                            List<ChampionInstance> allies,
                            List<ChampionInstance> enemies) {
            // Self HP cost (true damage — cannot be blocked)
            double selfCost = caster.getCurrentHp() * SELF_COST_PERCENT;
            caster.takeTrueDamage(selfCost);
            if (!caster.isAlive()) return;

            List<ChampionInstance> alive = enemies.stream()
                    .filter(ChampionInstance::isAlive)
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            Collections.shuffle(alive);

            double totalDealt = 0;
            int hits = Math.min(MAX_TARGETS, alive.size());
            for (int i = 0; i < hits; i++) {
                double dealt = alive.get(i).takeDamage(BASE_DAMAGE);
                totalDealt += dealt;
            }

            // Lifesteal
            caster.heal(totalDealt * LIFESTEAL_PERCENT);
        }
    }
}
