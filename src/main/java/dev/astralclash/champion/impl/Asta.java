package dev.astralclash.champion.impl;

import dev.astralclash.champion.ChampionInstance;
import dev.astralclash.champion.ability.Ability;
import dev.astralclash.combat.StatusEffect;

import java.util.List;

/**
 * Asta — Tier 1 | Harmony + Fire
 *
 * Ability: "Ignite the Fire"
 * Burns all enemies (BURN DoT) and boosts all allies' ATK speed.
 */
public class Asta {

    private Asta() {}

    public static class AstaAbility implements Ability {

        private static final double BURN_DAMAGE   = 100;
        private static final int    BURN_DURATION = 60;   // 3 s
        private static final double SPD_BUFF      = 0.15;
        private static final int    BUFF_DURATION = 40;   // 2 s

        @Override
        public String getName()        { return "Ignite the Fire"; }

        @Override
        public String getDescription() {
            return "Burns all enemies (" + BURN_DAMAGE + " fire dmg/s for 3 s) and grants all " +
                   "allies +15% attack speed for 2 s.";
        }

        @Override
        public void execute(ChampionInstance caster,
                            List<ChampionInstance> allies,
                            List<ChampionInstance> enemies) {
            // Burn all enemies
            for (ChampionInstance enemy : enemies) {
                if (enemy.isAlive()) {
                    enemy.applyStatus(StatusEffect.BURN, BURN_DURATION);
                }
            }
            // Speed buff to all allies
            for (ChampionInstance ally : allies) {
                if (ally.isAlive()) {
                    ally.setAttackSpeed(ally.getAttackSpeed() * (1 + SPD_BUFF));
                    ally.applyStatus(StatusEffect.HARMONY_BUFF, BUFF_DURATION);
                }
            }
        }
    }
}
