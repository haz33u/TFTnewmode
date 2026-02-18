package dev.astralclash.economy;

import dev.astralclash.AstralClash;
import dev.astralclash.player.ArenaPlayer;

/**
 * Handles gold distribution at the end of each planning phase:
 * <ul>
 *   <li>Base gold per round</li>
 *   <li>Interest (up to cap)</li>
 *   <li>Win/loss streak bonus</li>
 * </ul>
 */
public class EconomyManager {

    private final AstralClash plugin;

    public EconomyManager(AstralClash plugin) {
        this.plugin = plugin;
    }

    /**
     * Awards end-of-round gold to a player.
     * Call this at the START of the planning phase after combat results are resolved.
     *
     * @return total gold awarded
     */
    public int awardRoundGold(ArenaPlayer player) {
        int base     = plugin.getConfigManager().getGoldPerRound();
        int streak   = player.getStreakBonus();
        int interest = calculateInterest(player);

        int total = base + streak + interest;
        player.addGold(total);
        return total;
    }

    /**
     * Calculates interest gold: floor(currentGold / threshold), capped.
     */
    private int calculateInterest(ArenaPlayer player) {
        int threshold = plugin.getConfigManager().getGoldInterestThresh();
        int cap       = plugin.getConfigManager().getGoldInterestCap();
        return Math.min(player.getGold() / threshold, cap);
    }

    /**
     * Attempts to deduct the XP purchase cost and grant XP.
     *
     * @return true if successful
     */
    public boolean buyXp(ArenaPlayer player) {
        int cost   = plugin.getConfigManager().getXpBuyCost();
        int amount = plugin.getConfigManager().getXpBuyAmount();
        if (!player.spendGold(cost)) return false;
        player.addXp(amount);
        return true;
    }

    /**
     * Sells a champion from the bench, refunding gold equal to its tier cost.
     * Does NOT remove it from the bench — caller must do that.
     *
     * @return gold refunded
     */
    public int sellRefund(ArenaPlayer player,
                          dev.astralclash.champion.ChampionInstance ci) {
        int tierCost = ci.getChampion().getTier().getCost();
        // Star-level refund: 1★ = cost, 2★ = 2×cost+1, 3★ = 4×cost+3 (approximate TFT values)
        int refund = switch (ci.getStarLevel()) {
            case ONE   -> tierCost;
            case TWO   -> tierCost * 2 + 1;
            case THREE -> tierCost * 4 + 3;
        };
        player.addGold(refund);
        return refund;
    }
}
