package dev.astralclash.champion;

/**
 * Three-star upgrade system (like TFT): 3 copies → upgrade.
 */
public enum StarLevel {
    ONE(1, 1.0),
    TWO(2, 1.8),
    THREE(3, 3.24);

    private final int    stars;
    private final double multiplier;

    StarLevel(int stars, double multiplier) {
        this.stars      = stars;
        this.multiplier = multiplier;
    }

    public int    getStars()      { return stars; }
    public double getMultiplier() { return multiplier; }

    public StarLevel next() {
        return switch (this) {
            case ONE   -> TWO;
            case TWO   -> THREE;
            case THREE -> THREE;
        };
    }

    public boolean isMaxStar() { return this == THREE; }
}
