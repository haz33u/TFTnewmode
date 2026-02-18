package dev.astralclash.champion.impl;

import dev.astralclash.champion.ChampionInstance;
import dev.astralclash.champion.ability.Ability;
import dev.astralclash.combat.StatusEffect;

import java.util.List;

/**
 * Jingliu — Tier 5 | Destruction + Ice
 *
 * Ability: "Transcendent Flash"
 * Enters Spectral Transmigration: doubles ATK but drains 8% HP from all allies.
 * Deals massive AoE Ice magic damage to all enemies.
 */
public class Jingliu {

    private Jingliu() {}

    public static class JingliuAbility implements Ability {

        private static final double BASE_DAMAGE        = 600;
        private static final double ATK_BUFF_PERCENT   = 1.00;  // +100% ATK
        private static final double TEAM_HP_DRAIN_PCT  = 0.08;  // 8% of ally max HP
        private static final int    BUFF_DURATION      = 40;

        @Override
        public String getName()        { return "Transcendent Flash"; }

        @Override
        public String getDescription() {
            return "Deals " + BASE_DAMAGE + " Ice damage to all enemies. " +
                   "Jingliu's ATK doubles for 2 s by draining 8% HP from all allies.";
        }

        @Override
        public void execute(ChampionInstance caster,
                            List<ChampionInstance> allies,
                            List<ChampionInstance> enemies) {
            // Drain HP from all allies (including self) to fuel power
            for (ChampionInstance ally : allies) {
                if (ally.isAlive()) {
                    double drain = ally.getMaxHp() * TEAM_HP_DRAIN_PCT;
                    ally.takeTrueDamage(drain);
                }
            }

            // Buff Jingliu
            caster.setAttackDamage(caster.getAttackDamage() * (1 + ATK_BUFF_PERCENT));
            caster.applyStatus(StatusEffect.HARMONY_BUFF, BUFF_DURATION);

            // AoE ice damage
            for (ChampionInstance enemy : enemies) {
                if (enemy.isAlive()) {
                    enemy.takeMagicDamage(BASE_DAMAGE);
                    // Slight slow from ice
                    enemy.applyStatus(StatusEffect.SLOW, 20);
                }
            }
        }
    }
}
