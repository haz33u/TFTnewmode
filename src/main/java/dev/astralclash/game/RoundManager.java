package dev.astralclash.game;

import dev.astralclash.AstralClash;
import dev.astralclash.combat.BattleResult;
import dev.astralclash.combat.CombatEngine;
import dev.astralclash.champion.trait.Trait;
import dev.astralclash.champion.trait.TraitBonus;
import dev.astralclash.champion.trait.TraitManager;
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
    private final TraitManager  traitManager;

    private int roundNumber = 0;

    public RoundManager(AstralClash plugin) {
        this.plugin       = plugin;
        this.combatEngine = new CombatEngine(plugin);
        this.traitManager = new TraitManager();
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

        // 2. ABUNDANCE: heal players whose deployed team has active Abundance trait
        applyAbundanceHeal(players);

        // 3. Roll shops (only for unlocked players)
        for (ArenaPlayer ap : players) {
            if (!ap.isShopLocked()) {
                plugin.getShopManager().rollShop(ap);
            }
            plugin.getUIManager().openShop(ap);
        }

        // 4. Planning phase (handled by timer in GameManager — we just wait here
        //    in the real async flow; for simulation we skip to combat).

        // 5. Pair players and simulate combat (or PvE wave in test mode)
        List<ArenaPlayer> battlers = new ArrayList<>(players);
        boolean pveTestMode = plugin.getConfigManager().isPveTestMode();

        if (pveTestMode && battlers.size() == 1) {
            // PvE test mode: single player vs pre-made wave for this round
            ArenaPlayer human = battlers.get(0);
            ArenaPlayer pveOpponent = new PvEOpponentFactory(plugin).buildPvEOpponent(roundNumber);
            BattleResult result = combatEngine.simulate(human, pveOpponent);
            java.util.Map<dev.astralclash.champion.ChampionInstance, Double> dmg = combatEngine.getLastDamageDealt();
            processResult(result, human, pveOpponent, dmg);
        } else {
            Collections.shuffle(battlers);
            for (int i = 0; i + 1 < battlers.size(); i += 2) {
                ArenaPlayer a = battlers.get(i);
                ArenaPlayer b = battlers.get(i + 1);
                BattleResult result = combatEngine.simulate(a, b);
                java.util.Map<dev.astralclash.champion.ChampionInstance, Double> dmg = combatEngine.getLastDamageDealt();
                processResult(result, a, b, dmg);
            }
            if (battlers.size() % 2 == 1) {
                ArenaPlayer lonely = battlers.get(battlers.size() - 1);
                if (lonely.getPlayer() != null) {
                    lonely.getPlayer().sendMessage(Component.text(
                            "No opponent found — ghost round! No damage taken.", NamedTextColor.GRAY));
                }
                lonely.recordWin();
            }
        }

        // 6. Collect eliminated players
        for (ArenaPlayer ap : players) {
            if (ap.isDead()) {
                eliminated.add(ap);
                ap.getPlayer().sendMessage(Component.text(
                        "You have been eliminated! Better luck next time.", NamedTextColor.RED));
            }
        }

        return eliminated;
    }

    // ── ABUNDANCE: heal player HP each round if trait is active ─────────────

    private void applyAbundanceHeal(List<ArenaPlayer> players) {
        int maxHealth = plugin.getConfigManager().getStartingHealth();
        for (ArenaPlayer ap : players) {
            if (ap.getBoard() == null) continue;
            Map<Trait, TraitBonus> active =
                    traitManager.computeActiveTraits(ap.getBoard().getDeployedChampions());
            TraitBonus abundance = active.get(Trait.ABUNDANCE);
            if (abundance == null) continue;
            int heal = (int) abundance.getHealPerRoundFlat();
            if (heal <= 0) continue;
            int before = ap.getHealth();
            ap.setHealth(Math.min(maxHealth, before + heal));
            int actual = ap.getHealth() - before;
            if (actual > 0) {
                ap.getPlayer().sendMessage(Component.text(
                        "Abundance restored " + actual + " HP! (" +
                        ap.getHealth() + "/" + maxHealth + ")",
                        NamedTextColor.GREEN));
            }
        }
    }

    // ── Result processing ────────────────────────────────────────────────────

    private void processResult(BattleResult result, ArenaPlayer a, ArenaPlayer b,
                            java.util.Map<dev.astralclash.champion.ChampionInstance, Double> damageDealt) {
        if (result.isDraw()) {
            a.recordLoss();
            b.recordLoss();
            if (a.getPlayer() != null) a.getPlayer().sendMessage(Component.text("Draw! No damage dealt.", NamedTextColor.YELLOW));
            if (b.getPlayer() != null) b.getPlayer().sendMessage(Component.text("Draw! No damage dealt.", NamedTextColor.YELLOW));
            return;
        }

        UUID winnerId = result.getWinnerId();
        ArenaPlayer winner = winnerId.equals(a.getUuid()) ? a : b;
        ArenaPlayer loser  = winner == a ? b : a;

        winner.recordWin();
        loser.recordLoss();
        loser.takeDamage(result.getDamageTaken());

        if (winner.getPlayer() != null) {
            String loserName = loser.getPlayer() != null ? loser.getPlayer().getName() : "PvE";
            winner.getPlayer().sendMessage(Component.text(
                    "Victory! " + loserName + " takes " +
                    result.getDamageTaken() + " damage.", NamedTextColor.GREEN));
            if (plugin.getConfigManager().isSoundVictory()) {
                winner.getPlayer().playSound(winner.getPlayer().getLocation(),
                    org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.5f, 1.0f);
            }
        }
        if (loser.getPlayer() != null) {
            loser.getPlayer().sendMessage(Component.text(
                    "Defeat! You take " + result.getDamageTaken() + " damage. HP: " +
                    loser.getHealth() + "/" + plugin.getConfigManager().getStartingHealth(),
                    NamedTextColor.RED));
            if (plugin.getConfigManager().isSoundDefeat()) {
                loser.getPlayer().playSound(loser.getPlayer().getLocation(),
                    org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.4f, 0.9f);
            }
        }
        if (winner.getPlayer() != null) {
            winner.getStats().addDamage(result.getDamageTaken());
        }

        if (damageDealt != null && !damageDealt.isEmpty()) {
            if (a.getPlayer() != null) plugin.getCombatUI().renderRoundDamageReport(a, damageDealt);
            if (b.getPlayer() != null) plugin.getCombatUI().renderRoundDamageReport(b, damageDealt);
        }
    }

    public int getRoundNumber() { return roundNumber; }
}
