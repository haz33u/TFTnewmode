package dev.astralclash.champion.impl;

import dev.astralclash.champion.ChampionInstance;
import dev.astralclash.champion.ability.Ability;

import java.util.List;

/**
 * Jing Yuan — Tier 4 | Erudition + Lightning
 *
 * Ability: "Lightbringer"
 * Summons the Lightning Lord to hit all enemies 6 times, each hit dealing
 * 150 lightning magic damage (AoE).
 */
public class JingYuan {

    private JingYuan() {}

    public static class JingYuanAbility implements Ability {

        private static final int    HITS            = 6;
        private static final double DAMAGE_PER_HIT  = 150;

        @Override
        public String getName()        { return "Lightbringer"; }

        @Override
        public String getDescription() {
            return "The Lightning Lord strikes all enemies " + HITS + " times, " +
                   "dealing " + DAMAGE_PER_HIT + " lightning damage per hit.";
        }

        @Override
        public void execute(ChampionInstance caster,
                            List<ChampionInstance> allies,
                            List<ChampionInstance> enemies) {
            for (int hit = 0; hit < HITS; hit++) {
                for (ChampionInstance enemy : enemies) {
                    if (enemy.isAlive()) {
                        enemy.takeMagicDamage(DAMAGE_PER_HIT);
                    }
                }
            }
        }
    }
}
