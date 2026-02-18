package dev.astralclash.champion.impl;

import dev.astralclash.champion.ChampionInstance;
import dev.astralclash.champion.ability.Ability;
import dev.astralclash.combat.StatusEffect;

import java.util.Comparator;
import java.util.List;

/**
 * Seele — Tier 3 | Hunt + Quantum
 *
 * Ability: "Butterfly Flurry"
 * Seele enters Resurgence: deals massive single-target damage with a huge
 * ATK and SPD buff. If she kills the target her ability refreshes (mana stays full).
 */
public class Seele {

    private Seele() {}

    public static class SeeleAbility implements Ability {

        private static final double BASE_DAMAGE        = 300;
        private static final double ATK_BUFF_PERCENT   = 0.80;
        private static final double SPD_BUFF_PERCENT   = 0.25;
        private static final int    BUFF_DURATION      = 40; // 2 s

        @Override
        public String getName()        { return "Butterfly Flurry"; }

        @Override
        public String getDescription() {
            return "Deals " + BASE_DAMAGE + " quantum damage to the lowest-HP enemy. " +
                   "Grants +80% ATK and +25% AS for 2 s. Resets on kill.";
        }

        @Override
        public void execute(ChampionInstance caster,
                            List<ChampionInstance> allies,
                            List<ChampionInstance> enemies) {
            // Target lowest-HP enemy (Hunt trait behaviour)
            ChampionInstance target = enemies.stream()
                    .filter(ChampionInstance::isAlive)
                    .min(Comparator.comparingDouble(ChampionInstance::getCurrentHp))
                    .orElse(null);
            if (target == null) return;

            // Apply ATK / SPD buffs to Seele
            caster.setAttackDamage(caster.getAttackDamage() * (1 + ATK_BUFF_PERCENT));
            caster.setAttackSpeed(caster.getAttackSpeed() * (1 + SPD_BUFF_PERCENT));
            caster.applyStatus(StatusEffect.HARMONY_BUFF, BUFF_DURATION);

            double dmg = target.takeMagicDamage(BASE_DAMAGE);

            // If the target died, refund mana so the ability fires again next tick
            if (!target.isAlive()) {
                caster.addMana(caster.getChampion().getMaxMana()); // fills mana back
            }
        }
    }
}
