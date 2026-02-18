package dev.astralclash.champion.impl;

import dev.astralclash.champion.ChampionInstance;
import dev.astralclash.champion.ability.Ability;
import dev.astralclash.combat.StatusEffect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Welt — Tier 2 | Nihility + Imaginary
 *
 * Ability: "Imprisonment"
 * Deals Imaginary magic damage to up to 3 enemies and Imprisons them
 * (STUN for 30 ticks / 1.5 s).
 */
public class Welt {

    private Welt() {}

    public static class WeltAbility implements Ability {

        private static final double BASE_DAMAGE    = 200;
        private static final int    MAX_TARGETS    = 3;
        private static final int    IMPRISON_TICKS = 30;

        @Override
        public String getName()        { return "Imprisonment"; }

        @Override
        public String getDescription() {
            return "Deals " + BASE_DAMAGE + " Imaginary damage to up to " + MAX_TARGETS +
                   " enemies and imprisons them for 1.5 s.";
        }

        @Override
        public void execute(ChampionInstance caster,
                            List<ChampionInstance> allies,
                            List<ChampionInstance> enemies) {
            List<ChampionInstance> alive = enemies.stream()
                    .filter(ChampionInstance::isAlive)
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            Collections.shuffle(alive);

            int hits = Math.min(MAX_TARGETS, alive.size());
            for (int i = 0; i < hits; i++) {
                alive.get(i).takeMagicDamage(BASE_DAMAGE);
                alive.get(i).applyStatus(StatusEffect.STUN, IMPRISON_TICKS);
            }
        }
    }
}
