package dev.astralclash.champion.impl;

import dev.astralclash.champion.ChampionInstance;
import dev.astralclash.champion.ability.Ability;
import dev.astralclash.combat.StatusEffect;

import java.util.List;

/**
 * Fu Xuan — Tier 4 | Preservation + Quantum
 *
 * Ability: "Known by Stars, Shown by Hearts"
 * Marks all allies so that 40% of the damage they take is redirected to
 * Fu Xuan instead. Also grants all allies +15% crit rate (stored via status).
 */
public class FuXuan {

    private FuXuan() {}

    public static class FuXuanAbility implements Ability {

        private static final int REDIRECT_DURATION = 60; // 3 s

        @Override
        public String getName()        { return "Known by Stars, Shown by Hearts"; }

        @Override
        public String getDescription() {
            return "40% of damage taken by allies is redirected to Fu Xuan for 3 s. " +
                   "All allies gain +15% crit rate.";
        }

        @Override
        public void execute(ChampionInstance caster,
                            List<ChampionInstance> allies,
                            List<ChampionInstance> enemies) {
            for (ChampionInstance ally : allies) {
                if (!ally.isAlive()) continue;
                ally.applyStatus(StatusEffect.FU_XUAN_MATRIX, REDIRECT_DURATION);
                // Crit rate buff tracked via status (CombatEngine reads it)
                ally.applyStatus(StatusEffect.HARMONY_BUFF, REDIRECT_DURATION);
            }
            // Mark caster as the redirect target
            caster.applyStatus(StatusEffect.FU_XUAN_TARGET, REDIRECT_DURATION);
        }
    }
}
