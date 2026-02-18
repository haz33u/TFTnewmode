package dev.astralclash.champion.impl;

import dev.astralclash.champion.ChampionInstance;
import dev.astralclash.champion.ability.Ability;

import java.util.Comparator;
import java.util.List;

/**
 * Natasha — Tier 1 | Abundance + Physical
 *
 * Ability: "Healer's Touch"
 * Heals the ally with the lowest current HP for 150 HP.
 */
public class Natasha {

    private Natasha() {}

    public static class NatashaAbility implements Ability {

        private static final double HEAL_AMOUNT = 150;

        @Override
        public String getName()        { return "Healer's Touch"; }

        @Override
        public String getDescription() {
            return "Heals the ally with the lowest HP for " + HEAL_AMOUNT + " HP.";
        }

        @Override
        public void execute(ChampionInstance caster,
                            List<ChampionInstance> allies,
                            List<ChampionInstance> enemies) {
            allies.stream()
                  .filter(ChampionInstance::isAlive)
                  .min(Comparator.comparingDouble(ChampionInstance::getCurrentHp))
                  .ifPresent(t -> t.heal(HEAL_AMOUNT));
        }
    }
}
