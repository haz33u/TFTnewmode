package dev.astralclash.champion.impl;

import dev.astralclash.champion.ChampionInstance;
import dev.astralclash.champion.ability.Ability;
import dev.astralclash.combat.StatusEffect;

import java.util.List;
import java.util.Random;

/**
 * Gepard — Tier 2 | Preservation + Ice
 *
 * Ability: "Enduring Bulwark"
 * Creates a shield (300) for all allies. Each time an ally with this shield
 * is hit, the attacker has a 25% chance to be Frozen (handled in CombatEngine).
 */
public class Gepard {

    private Gepard() {}

    public static class GepardAbility implements Ability {

        private static final double SHIELD_AMOUNT   = 300;
        private static final int    FREEZE_CHANCE   = 25; // percent — stored via status

        @Override
        public String getName()        { return "Enduring Bulwark"; }

        @Override
        public String getDescription() {
            return "Grants all allies a " + SHIELD_AMOUNT + " shield. " +
                   "Attackers hitting shielded allies have a " + FREEZE_CHANCE + "% chance to be Frozen.";
        }

        @Override
        public void execute(ChampionInstance caster,
                            List<ChampionInstance> allies,
                            List<ChampionInstance> enemies) {
            for (ChampionInstance ally : allies) {
                if (ally.isAlive()) {
                    ally.addShield(SHIELD_AMOUNT);
                    // Mark ally so CombatEngine can proc the freeze counter-attack
                    ally.applyStatus(StatusEffect.GEPARD_SHIELD, 40);
                }
            }
        }
    }
}
