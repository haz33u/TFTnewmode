package dev.astralclash.game;

import dev.astralclash.AstralClash;
import dev.astralclash.combat.BattleResult;
import dev.astralclash.combat.CombatEngine;
import dev.astralclash.player.ArenaPlayer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.*;

/**
 * Drives one round of the game: planning → combat → results.
 * Called repeatedly by {@link GameManager} until the game ends.
 */
public class RoundManager {

    private final AstralClash   plugin;
    private final CombatEngine  combatEngine;

    private int roundNumber = 0;

    public RoundManager(AstralClash plugin) {
        this.plugin       = plugin;
        this.combatEngine = new CombatEngine(plugin);
    }

    // ── Round lifecycle ──────────────────────────────────────────────────────

    /**
     * Executes one full round for the given set of living players.
     * Returns the list of players who have been eliminated this round.
     */
    public List<ArenaPlayer> executeRound(List<ArenaPlayer> players) {
        roundNumber++;
        List<ArenaPlayer> eliminated = new ArrayList<>();

        // 1. Distribute round gold + XP
        for (ArenaPlayer ap : players) {
            int gold = plugin.getEconomyManager().awardRoundGold(ap);
            ap.addXp(plugin.getConfigManager().getXpPerRound());
            ap.getPlayer().sendMessage(Component.text(
                    "Round " + roundNumber + " — you received " + gold + " gold!",
                    NamedTextColor.GOLD));
        }

        // 2. Roll shops (only for unlocked players)
        for (ArenaPlayer ap : players) {
            if (!ap.isShopLocked()) {
                plugin.getShopManager().rollShop(ap);
            }
            plugin.getUIManager().openShop(ap);
        }

        // 3. Planning phase (handled by timer in GameManager — we just wait here
        //    in the real async flow; for simulation we skip to combat).

        // 4. Pair players and simulate combat
        List<ArenaPlayer> battlers = new ArrayList<>(players);
        Collections.shuffle(battlers);

        List<BattleResult> results = new ArrayList<>();

        // Pair up players (odd one gets a ghost round vs previous ghost)
        for (int i = 0; i + 1 < battlers.size(); i += 2) {
            ArenaPlayer a = battlers.get(i);
            ArenaPlayer b = battlers.get(i + 1);
            BattleResult result = combatEngine.simulate(a, b);
            results.add(result);
            processResult(result, a, b);
        }
        // If odd number of players, last player fights their own previous board (ghost)
        if (battlers.size() % 2 == 1) {
            ArenaPlayer lonely = battlers.get(battlers.size() - 1);
            lonely.getPlayer().sendMessage(Component.text(
                    "No opponent found — ghost round! No damage taken.", NamedTextColor.GRAY));
            lonely.recordWin();
        }

        // 5. Collect eliminated players
        for (ArenaPlayer ap : players) {
            if (ap.isDead()) {
                eliminated.add(ap);
                ap.getPlayer().sendMessage(Component.text(
                        "You have been eliminated! Better luck next time.", NamedTextColor.RED));
            }
        }

        return eliminated;
    }

    // ── Result processing ────────────────────────────────────────────────────

    private void processResult(BattleResult result, ArenaPlayer a, ArenaPlayer b) {
        if (result.isDraw()) {
            a.recordLoss();
            b.recordLoss();
            a.getPlayer().sendMessage(Component.text("Draw! No damage dealt.", NamedTextColor.YELLOW));
            b.getPlayer().sendMessage(Component.text("Draw! No damage dealt.", NamedTextColor.YELLOW));
            return;
        }

        UUID winnerId = result.getWinnerId();
        ArenaPlayer winner = winnerId.equals(a.getUuid()) ? a : b;
        ArenaPlayer loser  = winner == a ? b : a;

        winner.recordWin();
        loser.recordLoss();
        loser.takeDamage(result.getDamageTaken());

        winner.getPlayer().sendMessage(Component.text(
                "Victory! " + loser.getPlayer().getName() + " takes " +
                result.getDamageTaken() + " damage.", NamedTextColor.GREEN));
        loser.getPlayer().sendMessage(Component.text(
                "Defeat! You take " + result.getDamageTaken() + " damage. HP: " +
                loser.getHealth() + "/" + plugin.getConfigManager().getStartingHealth(),
                NamedTextColor.RED));

        // Stats
        winner.getStats().addDamage(result.getDamageTaken());
    }

    public int getRoundNumber() { return roundNumber; }
}
