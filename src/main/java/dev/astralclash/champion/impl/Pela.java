package dev.astralclash.champion.impl;

import dev.astralclash.champion.ChampionInstance;
import dev.astralclash.champion.ability.Ability;
import dev.astralclash.combat.StatusEffect;

import java.util.List;

/**
 * Pela — Tier 1 | Nihility + Ice
 *
 * Ability: "Zone Suppression"
 * Shreds 30% defense (armor) from all enemies for 40 ticks (2 s).
 */
public class Pela {

    private Pela() {}

    public static class PelaAbility implements Ability {

        private static final double ARMOR_SHRED_PERCENT  = 0.30;
        private static final int    DEBUFF_DURATION      = 40;

        @Override
        public String getName()        { return "Zone Suppression"; }

        @Override
        public String getDescription() {
            return "Reduces all enemies' armor by 30% for 2 seconds.";
        }

        @Override
        public void execute(ChampionInstance caster,
                            List<ChampionInstance> allies,
                            List<ChampionInstance> enemies) {
            for (ChampionInstance enemy : enemies) {
                if (!enemy.isAlive()) continue;
                // Reduce armor directly (CombatEngine restores after duration via status)
                double shred = enemy.getArmor() * ARMOR_SHRED_PERCENT;
                enemy.setArmor(Math.max(0, enemy.getArmor() - shred));
                enemy.applyStatus(StatusEffect.ARMOR_SHRED, DEBUFF_DURATION);
            }
        }
    }
}
