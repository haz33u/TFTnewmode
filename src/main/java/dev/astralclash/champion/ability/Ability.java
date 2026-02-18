package dev.astralclash.champion.ability;

import dev.astralclash.champion.ChampionInstance;

import java.util.List;

/**
 * Contract for a champion's active ability.
 * All champion implementations provide a concrete {@code Ability}.
 */
public interface Ability {

    /** Displayed in the shop and HUD. */
    String getName();

    String getDescription();

    /**
     * Executes the ability for {@code caster} against {@code enemies}.
     * The combat engine calls this when the caster's mana reaches max.
     *
     * @param caster  the champion casting the ability
     * @param allies  all allied units currently alive on the board
     * @param enemies all enemy units currently alive on the board
     */
    void execute(ChampionInstance caster,
                 List<ChampionInstance> allies,
                 List<ChampionInstance> enemies);
}
