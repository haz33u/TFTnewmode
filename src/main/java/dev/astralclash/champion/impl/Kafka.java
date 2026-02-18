package dev.astralclash.champion.impl;

import dev.astralclash.champion.ChampionInstance;
import dev.astralclash.champion.ability.Ability;
import dev.astralclash.combat.StatusEffect;

import java.util.List;

/**
 * Kafka — Tier 3 | Nihility + Lightning
 *
 * Ability: "Twilight Trill"
 * Forces all existing DoT effects on enemies to immediately trigger an extra
 * time, then applies Shock (lightning DoT) to every enemy.
 */
public class Kafka {

    private Kafka() {}

    public static class KafkaAbility implements Ability {

        private static final double SHOCK_DAMAGE_PER_TICK = 80;
        private static final int    SHOCK_DURATION_TICKS  = 60; // 3 seconds @ 20tps

        @Override
        public String getName()        { return "Twilight Trill"; }

        @Override
        public String getDescription() {
            return "Forces all DoT effects on enemies to trigger immediately once extra, " +
                   "then applies Shock to all enemies dealing " + SHOCK_DAMAGE_PER_TICK +
                   " lightning damage per tick for 3 seconds.";
        }

        @Override
        public void execute(ChampionInstance caster,
                            List<ChampionInstance> allies,
                            List<ChampionInstance> enemies) {
            for (ChampionInstance enemy : enemies) {
                if (!enemy.isAlive()) continue;

                // Proc all existing DoT effects once instantly
                if (enemy.hasStatus(StatusEffect.BURN)) {
                    enemy.takeMagicDamage(SHOCK_DAMAGE_PER_TICK * 0.6);
                }
                if (enemy.hasStatus(StatusEffect.SHOCK)) {
                    enemy.takeMagicDamage(SHOCK_DAMAGE_PER_TICK);
                }
                if (enemy.hasStatus(StatusEffect.POISON)) {
                    enemy.takeTrueDamage(SHOCK_DAMAGE_PER_TICK * 0.8);
                }

                // Apply Shock DoT
                enemy.applyStatus(StatusEffect.SHOCK, SHOCK_DURATION_TICKS);
            }
        }
    }
}
