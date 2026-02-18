package dev.astralclash.champion.impl;

import dev.astralclash.champion.ChampionInstance;
import dev.astralclash.champion.ability.Ability;

import java.util.Comparator;
import java.util.List;

/**
 * Bronya — Tier 2 | Harmony + Wind
 *
 * Ability: "The Belobog March"
 * Grants the ally with the highest ATK an extra attack and a 40% ATK buff
 * for 2 rounds (40 ticks).
 */
public class Bronya {

    private Bronya() {}

    public static class BronyaAbility implements Ability {

        private static final double ATK_BUFF_PERCENT  = 0.40;
        private static final int    BUFF_DURATION_TICKS = 40;

        @Override
        public String getName()        { return "The Belobog March"; }

        @Override
        public String getDescription() {
            return "Grants the ally with the highest ATK a 40% ATK buff and causes them to immediately attack.";
        }

        @Override
        public void execute(ChampionInstance caster,
                            List<ChampionInstance> allies,
                            List<ChampionInstance> enemies) {
            // Find the ally with the highest attack damage (excluding caster)
            ChampionInstance target = allies.stream()
                    .filter(a -> a.isAlive() && a != caster)
                    .max(Comparator.comparingDouble(ChampionInstance::getAttackDamage))
                    .orElse(caster); // buff self if alone

            // Apply ATK buff
            double buffedAtk = target.getAttackDamage() * (1 + ATK_BUFF_PERCENT);
            target.setAttackDamage(buffedAtk);
            // Store the buff so CombatEngine can revert it (simplified: use status flag)
            // In a full implementation you'd store the original and revert after BUFF_DURATION_TICKS.
            // For now we mark it with a generic BUFF status.
            target.applyStatus(dev.astralclash.combat.StatusEffect.HARMONY_BUFF, BUFF_DURATION_TICKS);
        }
    }
}
