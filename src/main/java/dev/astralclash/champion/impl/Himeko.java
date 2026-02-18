package dev.astralclash.champion.impl;

import dev.astralclash.champion.ChampionInstance;
import dev.astralclash.champion.ability.Ability;
import dev.astralclash.combat.StatusEffect;

import java.util.List;

/**
 * Himeko — Tier 3 | Erudition + Fire
 *
 * Ability: "Starfire Celestia"
 * Launches a tri-beam blast hitting ALL enemies. Burns them for 60 magic
 * damage per tick for 3 seconds (60 ticks).
 */
public class Himeko {

    private Himeko() {}

    public static class HimekoAbility implements Ability {

        private static final double BASE_DAMAGE       = 280;
        private static final double BURN_DAMAGE_TICK  = 60;    // per 20 ticks (1 s)
        private static final int    BURN_DURATION     = 60;    // 3 seconds

        @Override
        public String getName()        { return "Starfire Celestia"; }

        @Override
        public String getDescription() {
            return "Deals " + BASE_DAMAGE + " fire magic damage to all enemies and Burns them for " +
                   BURN_DAMAGE_TICK + " damage/s for 3 seconds.";
        }

        @Override
        public void execute(ChampionInstance caster,
                            List<ChampionInstance> allies,
                            List<ChampionInstance> enemies) {
            for (ChampionInstance enemy : enemies) {
                if (!enemy.isAlive()) continue;
                enemy.takeMagicDamage(BASE_DAMAGE);
                enemy.applyStatus(StatusEffect.BURN, BURN_DURATION);
            }
        }
    }
}
