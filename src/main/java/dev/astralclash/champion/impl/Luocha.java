package dev.astralclash.champion.impl;

import dev.astralclash.champion.ChampionInstance;
import dev.astralclash.champion.ability.Ability;
import dev.astralclash.combat.StatusEffect;

import java.util.List;

/**
 * Luocha — Tier 4 | Abundance + Imaginary
 *
 * Ability: "Prayer of Abyss Flower"
 * Heals all allies for 400 HP and removes one debuff from each.
 */
public class Luocha {

    private Luocha() {}

    public static class LuochaAbility implements Ability {

        private static final double HEAL_ALL_AMOUNT = 400;

        @Override
        public String getName()        { return "Prayer of Abyss Flower"; }

        @Override
        public String getDescription() {
            return "Heals all allies for " + (int) HEAL_ALL_AMOUNT +
                   " HP and removes one debuff from each.";
        }

        @Override
        public void execute(ChampionInstance caster,
                            List<ChampionInstance> allies,
                            List<ChampionInstance> enemies) {
            for (ChampionInstance ally : allies) {
                if (!ally.isAlive()) continue;
                ally.heal(HEAL_ALL_AMOUNT);
                // Remove one negative status per ally (priority: Burn > Poison > Slow > Stun)
                for (StatusEffect bad : List.of(StatusEffect.BURN, StatusEffect.POISON,
                                                StatusEffect.SLOW, StatusEffect.STUN)) {
                    if (ally.hasStatus(bad)) {
                        ally.removeStatus(bad);
                        break;
                    }
                }
            }
        }
    }
}
