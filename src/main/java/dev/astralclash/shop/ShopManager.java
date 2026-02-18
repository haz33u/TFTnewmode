package dev.astralclash.shop;

import dev.astralclash.AstralClash;
import dev.astralclash.champion.Champion;
import dev.astralclash.champion.ChampionInstance;
import dev.astralclash.champion.StarLevel;
import dev.astralclash.player.ArenaPlayer;

import java.util.*;

/**
 * Manages champion shop rolls, purchases, sells, and star-up merging.
 *
 * <p>Purchasing flow:
 * <ol>
 *   <li>Player has enough gold.</li>
 *   <li>Champion copy is drawn from the global pool.</li>
 *   <li>A new {@link ChampionInstance} lands on the bench.</li>
 *   <li>If the bench now has 3 copies of the same champion at the same star level,
 *       they merge into a higher-star version.</li>
 * </ol>
 */
public class ShopManager {

    private final AstralClash plugin;
    private final Random       rng = new Random();

    public ShopManager(AstralClash plugin) {
        this.plugin = plugin;
    }

    // ── Roll ─────────────────────────────────────────────────────────────────

    /**
     * Generates a fresh shop for the player based on their current level.
     * Non-locked slots are cleared from the pool and new offers are drawn.
     */
    public Shop rollShop(ArenaPlayer player) {
        int slots = plugin.getConfigManager().getShopSlots();
        Shop shop = player.getShop();
        if (shop == null) {
            shop = new Shop(slots);
            player.setShop(shop);
        }

        // Return existing (unlocked) champions to pool
        for (int i = 0; i < slots; i++) {
            if (!shop.isLocked(i) && shop.getSlot(i) != null) {
                plugin.getChampionManager().returnToPool(shop.getSlot(i).getId());
            }
        }
        shop.clearAll();

        // Fill empty slots
        int[] odds = plugin.getConfigManager().getShopOdds(player.getLevel());
        for (int i = 0; i < slots; i++) {
            if (shop.isLocked(i)) continue;
            Champion offer = drawChampion(odds);
            shop.setSlot(i, offer);
        }
        return shop;
    }

    /** Draws one champion from the pool according to tier odds. */
    private Champion drawChampion(int[] odds) {
        int roll = rng.nextInt(100);
        int cumulative = 0;
        int tier = 1;
        for (int i = 0; i < odds.length; i++) {
            cumulative += odds[i];
            if (roll < cumulative) { tier = i + 1; break; }
        }

        List<Champion> available = plugin.getChampionManager().getAvailableByTier(tier);
        if (available.isEmpty()) {
            // Fall back to tier 1
            available = plugin.getChampionManager().getAvailableByTier(1);
        }
        if (available.isEmpty()) return null;

        Champion chosen = available.get(rng.nextInt(available.size()));
        plugin.getChampionManager().drawFromPool(chosen.getId());
        return chosen;
    }

    // ── Purchase ─────────────────────────────────────────────────────────────

    /**
     * Attempts to buy the champion in the given shop slot.
     *
     * @return the resulting ChampionInstance (possibly star-up merged), or null on failure.
     */
    public ChampionInstance buyChampion(ArenaPlayer player, int slotIndex) {
        Shop shop = player.getShop();
        if (shop == null) return null;

        Champion champion = shop.getSlot(slotIndex);
        if (champion == null) return null;
        if (!player.hasBenchSpace()) return null;
        if (!player.spendGold(champion.getTier().getCost())) return null;

        // Acquire instance
        ChampionInstance ci = plugin.getChampionManager()
                .createInstance(champion.getId(), player.getUuid());
        if (ci == null) return null;

        shop.setSlot(slotIndex, null);
        player.addToBench(ci);

        // Check for 3-copy merge
        return checkAndMerge(player, ci);
    }

    /**
     * Rerolls all unlocked shop slots. Costs {@link dev.astralclash.config.ConfigManager#getRerollCost()}.
     *
     * @return true if gold was sufficient and shop was rerolled
     */
    public boolean reroll(ArenaPlayer player) {
        int cost = plugin.getConfigManager().getRerollCost();
        if (!player.spendGold(cost)) return false;
        rollShop(player);
        return true;
    }

    // ── Sell ─────────────────────────────────────────────────────────────────

    /**
     * Sells a champion from the bench, refunding gold and returning pool copies.
     */
    public int sell(ArenaPlayer player, ChampionInstance ci) {
        // Remove from bench or board
        boolean removed = player.removeFromBench(ci);
        if (!removed && player.getBoard() != null) {
            player.getBoard().findCell(ci).ifPresent(cell -> {
                cell.clearOccupant();
            });
        }

        // Return the correct number of copies to the pool
        int copies = switch (ci.getStarLevel()) {
            case ONE   -> 1;
            case TWO   -> 3;
            case THREE -> 9;
        };
        for (int i = 0; i < copies; i++) {
            plugin.getChampionManager().returnToPool(ci.getChampion().getId());
        }

        return plugin.getEconomyManager().sellRefund(player, ci);
    }

    // ── Star-up merge ────────────────────────────────────────────────────────

    /**
     * Checks the bench for 3 copies of the same champion at the same star level.
     * If found, removes them and adds a higher-star version.
     * Recurses to check for further merges (e.g., 9 → 3★).
     */
    private ChampionInstance checkAndMerge(ArenaPlayer player, ChampionInstance newCi) {
        String id      = newCi.getChampion().getId();
        StarLevel level = newCi.getStarLevel();

        if (level.isMaxStar()) return newCi;

        List<ChampionInstance> matches = player.getBench().stream()
                .filter(ci -> ci.getChampion().getId().equals(id)
                           && ci.getStarLevel() == level)
                .toList();

        if (matches.size() >= 3) {
            // Remove 3 from bench, add 1 star-up
            int removed = 0;
            Iterator<ChampionInstance> it = player.getBench().iterator();
            while (it.hasNext() && removed < 3) {
                ChampionInstance ci = it.next();
                if (ci.getChampion().getId().equals(id) && ci.getStarLevel() == level) {
                    it.remove();
                    removed++;
                    // Return 2 of the 3 copies to pool (the 3rd becomes the new star)
                    if (removed < 3) plugin.getChampionManager().returnToPool(id);
                }
            }

            ChampionInstance merged = plugin.getChampionManager()
                    .createInstance(id, player.getUuid());
            if (merged != null) {
                merged.starUp();
                player.addToBench(merged);
                return checkAndMerge(player, merged);
            }
        }
        return newCi;
    }
}
