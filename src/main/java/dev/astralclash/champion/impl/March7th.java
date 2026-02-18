package dev.astralclash.champion.impl;

import dev.astralclash.champion.ChampionInstance;
import dev.astralclash.champion.ability.Ability;

import java.util.Comparator;
import java.util.List;

/**
 * March 7th — Tier 1 | Preservation + Ice
 *
 * Ability: "I'm the Protector!"
 * Shields the lowest-HP ally and herself for 200 each.
 */
public class March7th {

    private March7th() {}

    public static class March7thAbility implements Ability {

        private static final double SHIELD_AMOUNT = 200;

        @Override
        public String getName()        { return "I'm the Protector!"; }

        @Override
        public String getDescription() {
            return "Grants a " + SHIELD_AMOUNT + " shield to the lowest-HP ally and herself.";
        }

        @Override
        public void execute(ChampionInstance caster,
                            List<ChampionInstance> allies,
                            List<ChampionInstance> enemies) {
            caster.addShield(SHIELD_AMOUNT);

            allies.stream()
                  .filter(a -> a.isAlive() && a != caster)
                  .min(Comparator.comparingDouble(ChampionInstance::getCurrentHp))
                  .ifPresent(t -> t.addShield(SHIELD_AMOUNT));
        }
    }
}
