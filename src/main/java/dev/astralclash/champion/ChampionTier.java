package dev.astralclash.champion;

/**
 * Gold cost / rarity tier of a champion (1-5 like TFT).
 */
public enum ChampionTier {
    ONE(1),
    TWO(2),
    THREE(3),
    FOUR(4),
    FIVE(5);

    private final int cost;

    ChampionTier(int cost) { this.cost = cost; }

    public int getCost() { return cost; }

    public static ChampionTier fromCost(int cost) {
        for (ChampionTier t : values()) {
            if (t.cost == cost) return t;
        }
        return ONE;
    }
}
