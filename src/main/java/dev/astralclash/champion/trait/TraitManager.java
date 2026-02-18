package dev.astralclash.champion.trait;

import dev.astralclash.champion.ChampionInstance;

import java.util.*;

/**
 * Computes which traits are active for a set of deployed champion instances,
 * and provides the resulting {@link TraitBonus} values.
 */
public class TraitManager {

    /**
     * Given a collection of deployed champions, returns a map of
     * Trait → TraitBonus (only for traits that meet at least tier-1 threshold).
     */
    public Map<Trait, TraitBonus> computeActiveTraits(Collection<ChampionInstance> deployed) {
        Map<Trait, Integer> counts = new EnumMap<>(Trait.class);

        for (ChampionInstance ci : deployed) {
            for (Trait t : ci.getChampion().getTraits()) {
                counts.merge(t, 1, Integer::sum);
            }
        }

        Map<Trait, TraitBonus> active = new EnumMap<>(Trait.class);
        for (Map.Entry<Trait, Integer> entry : counts.entrySet()) {
            Trait trait = entry.getKey();
            int   count = entry.getValue();
            int   tier  = trait.getActiveTier(count);
            if (tier > 0) {
                active.put(trait, new TraitBonus(trait, tier));
            }
        }
        return active;
    }

    /**
     * Returns all trait counts (active + inactive) for UI display purposes.
     */
    public Map<Trait, Integer> countTraits(Collection<ChampionInstance> deployed) {
        Map<Trait, Integer> counts = new EnumMap<>(Trait.class);
        for (ChampionInstance ci : deployed) {
            for (Trait t : ci.getChampion().getTraits()) {
                counts.merge(t, 1, Integer::sum);
            }
        }
        return counts;
    }
}
