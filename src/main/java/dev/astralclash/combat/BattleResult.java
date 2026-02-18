package dev.astralclash.combat;

import dev.astralclash.champion.ChampionInstance;
import dev.astralclash.player.ArenaPlayer;

import java.util.List;
import java.util.UUID;

/**
 * Immutable result object returned by {@link CombatEngine} after a round of combat.
 */
public class BattleResult {

    public enum Outcome { WIN, LOSS, DRAW }

    private final UUID      winnerId;    // null on draw
    private final UUID      loserId;     // null on draw
    private final Outcome   outcome;
    private final int       damageTaken; // HP the loser's nexus takes
    private final List<ChampionInstance> survivingWinners;

    private BattleResult(UUID winnerId, UUID loserId, Outcome outcome,
                         int damageTaken, List<ChampionInstance> survivingWinners) {
        this.winnerId          = winnerId;
        this.loserId           = loserId;
        this.outcome           = outcome;
        this.damageTaken       = damageTaken;
        this.survivingWinners  = List.copyOf(survivingWinners);
    }

    // ── Factories ─────────────────────────────────────────────────────────────

    public static BattleResult win(ArenaPlayer winner, ArenaPlayer loser,
                                   int damageTaken, List<ChampionInstance> survivors) {
        return new BattleResult(winner.getUuid(), loser.getUuid(),
                Outcome.WIN, damageTaken, survivors);
    }

    public static BattleResult draw(ArenaPlayer p1, ArenaPlayer p2) {
        return new BattleResult(null, null, Outcome.DRAW, 0, List.of());
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public UUID   getWinnerId()         { return winnerId; }
    public UUID   getLoserId()          { return loserId; }
    public Outcome getOutcome()         { return outcome; }
    public int    getDamageTaken()      { return damageTaken; }
    public List<ChampionInstance> getSurvivingWinners() { return survivingWinners; }

    public boolean isDraw()             { return outcome == Outcome.DRAW; }
    public boolean isWin()              { return outcome == Outcome.WIN; }
}
