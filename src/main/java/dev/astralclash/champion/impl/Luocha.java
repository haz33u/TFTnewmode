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
 * Passive: after each attack, heals the lowest-HP ally for 8% of max HP
 * (handled in CombatEngine via LUOCHA_PASSIVE status).
 */
public class Luocha {

    private Luocha() {}

    public static class LuochaAbility implements Ability {

        private static final double HEAL_ALL_AMOUNT = 400;

        @Override
        public String getName()        { return "Prayer of Abyss Flower"; }

        @Override
        public String getDescription() {
            return "Heals all allies for " + HEAL_ALL_AMOUNT + " HP and removes one debuff from each. " +
                   "Passive: heals lowest-HP ally for 8% max HP after each attack.";
        }

        @Override
        public void execute(ChampionInstance caster,
                            List<ChampionInstance> allies,
                            List<ChampionInstance> enemies) {
            for (ChampionInstance ally : allies) {
                if (!ally.isAlive()) continue;
                ally.heal(HEAL_ALL_AMOUNT);
                // Remove one negative status (priority: Burn > Poison > Slow > Stun)
                for (StatusEffect bad : List.of(StatusEffect.STUN, StatusEffect.BURN,
                                                StatusEffect.POISON, StatusEffect.SLOW)) {
                    var statuses = ally.getStatusEffects();
                    if (statuses.containsKey(bad)) {
                        // Can't remove via unmodifiableMap — we expose a remove method
                        // In ChampionInstance, getStatusEffects() returns unmodifiable view,
                        // but we can apply a 0-duration (expiry) next tick trick via ARMOR_SHRED reset:
                        // For now, the CombatEngine checks LUOCHA_CLEANSE status and removes debuffs.
                        ally.applyStatus(StatusEffect.LUOCHA_CLEANSE, 1);
                        break;
                    }
                }
            }
        }
    }
}
