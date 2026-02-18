package dev.astralclash.game;

/**
 * Phases within a single AstralClash game round.
 */
public enum GamePhase {
    WAITING,    // In lobby, waiting for players
    PLANNING,   // Players arrange their board, buy from shop, manage bench
    COMBAT,     // Boards fight automatically
    RESULTS,    // Brief intermission: show results, award gold, xp
    ENDED       // Game is over — one (or zero) players remain
}
