package dev.astralclash.champion.impl;

import dev.astralclash.champion.ChampionInstance;
import dev.astralclash.champion.ability.Ability;
import dev.astralclash.combat.StatusEffect;

import java.util.Comparator;
import java.util.List;

/**
 * Dan Heng — Tier 1 | Hunt + Wind
 *
 * Ability: "Ethereal Dream"
 * Hurls a gale at the target, dealing 180 Wind magic damage and Slowing
 * their attack speed by 40% for 2 s (40 ticks).
 */
public class DanHeng {

    private DanHeng() {}

    public static class DanHengAbility implements Ability {

        private static final double BASE_DAMAGE   = 180;
        private static final int    SLOW_DURATION = 40;
        private static final double SLOW_PERCENT  = 0.40;

        @Override
        public String getName()        { return "Ethereal Dream"; }

        @Override
        public String getDescription() {
            return "Deals " + BASE_DAMAGE + " Wind damage to the target and slows their " +
                   "attack speed by " + (int)(SLOW_PERCENT*100) + "% for 2 s.";
        }

        @Override
        public void execute(ChampionInstance caster,
                            List<ChampionInstance> allies,
                            List<ChampionInstance> enemies) {
            // Target nearest / lowest-HP enemy (Hunt trait — same as Seele)
            ChampionInstance target = enemies.stream()
                    .filter(ChampionInstance::isAlive)
                    .min(Comparator.comparingDouble(ChampionInstance::getCurrentHp))
                    .orElse(null);
            if (target == null) return;

            target.takeMagicDamage(BASE_DAMAGE);
            target.setAttackSpeed(target.getAttackSpeed() * (1 - SLOW_PERCENT));
            target.applyStatus(StatusEffect.SLOW, SLOW_DURATION);
        }
    }
}
