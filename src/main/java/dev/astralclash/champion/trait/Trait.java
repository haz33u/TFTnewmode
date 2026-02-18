package dev.astralclash.champion.trait;

import net.kyori.adventure.text.format.TextColor;

/**
 * All traits (Paths + Elements) available in AstralClash.
 * Each trait has multiple activation thresholds mirroring TFT synergies.
 */
public enum Trait {

    // ── Paths (class-like traits) ─────────────────────────────────────────────
    HUNT(
        "The Hunt",
        "Hunt champions gain bonus AS and deal extra damage to the lowest-HP target.",
        new int[]{2, 4, 6},
        TextColor.color(0xF59E0B)   // amber
    ),
    DESTRUCTION(
        "Destruction",
        "When Destruction champions fall below 50% HP, they gain bonus ATK and omnivamp.",
        new int[]{2, 4, 6},
        TextColor.color(0xEF4444)   // red
    ),
    ERUDITION(
        "Erudition",
        "Erudition champions deal bonus damage to all enemies hit by their abilities (AoE bonus).",
        new int[]{2, 4, 6},
        TextColor.color(0x3B82F6)   // blue
    ),
    HARMONY(
        "Harmony",
        "At the start of combat, Harmony champions grant ATK/SPD buffs to adjacent allies.",
        new int[]{2, 4, 6},
        TextColor.color(0x10B981)   // emerald
    ),
    NIHILITY(
        "Nihility",
        "DoT effects applied by Nihility champions tick more frequently and deal more damage.",
        new int[]{2, 4},
        TextColor.color(0x8B5CF6)   // violet
    ),
    PRESERVATION(
        "Preservation",
        "Preservation champions generate a shield at the start of combat.",
        new int[]{2, 4, 6},
        TextColor.color(0x06B6D4)   // cyan
    ),
    ABUNDANCE(
        "Abundance",
        "Abundance champions grant passive HP regeneration to all allies each round.",
        new int[]{2, 4},
        TextColor.color(0x22C55E)   // green
    ),

    // ── Elements (origin-like traits) ────────────────────────────────────────
    FIRE(
        "Fire",
        "Fire champions have a chance to apply Burn on basic attacks.",
        new int[]{2, 4},
        TextColor.color(0xFF6200)   // orange-red
    ),
    ICE(
        "Ice",
        "Ice champions slow attack speed of enemies they hit.",
        new int[]{2, 4},
        TextColor.color(0x67E8F9)   // light-cyan
    ),
    WIND(
        "Wind",
        "Wind champions gain bonus movement speed during combat.",
        new int[]{2, 4},
        TextColor.color(0xA7F3D0)   // light-green
    ),
    LIGHTNING(
        "Lightning",
        "Lightning champions chain 30% of their ability damage to an adjacent enemy.",
        new int[]{2, 4},
        TextColor.color(0xFDE047)   // yellow
    ),
    QUANTUM(
        "Quantum",
        "Quantum champions reduce enemy Magic Resist on each hit (stacking).",
        new int[]{2, 4},
        TextColor.color(0xC084FC)   // purple
    ),
    IMAGINARY(
        "Imaginary",
        "Imaginary champions apply Weakness Implant on their first ability cast.",
        new int[]{2, 4},
        TextColor.color(0xFCD34D)   // warm-yellow
    ),
    PHYSICAL(
        "Physical",
        "Physical champions deal bonus true damage on every third basic attack.",
        new int[]{2, 4},
        TextColor.color(0xD1D5DB)   // gray
    );

    private final String   displayName;
    private final String   description;
    private final int[]    thresholds;
    private final TextColor color;

    Trait(String displayName, String description, int[] thresholds, TextColor color) {
        this.displayName = displayName;
        this.description = description;
        this.thresholds  = thresholds;
        this.color       = color;
    }

    public String    getDisplayName() { return displayName; }
    public String    getDescription() { return description; }
    public int[]     getThresholds()  { return thresholds; }
    public TextColor getColor()       { return color; }

    /**
     * Returns the active tier (0 = inactive, 1 = first threshold, etc.)
     * based on how many trait members are on the board.
     */
    public int getActiveTier(int count) {
        int tier = 0;
        for (int threshold : thresholds) {
            if (count >= threshold) tier++;
        }
        return tier;
    }

    /**
     * The minimum active threshold for a given tier (1-indexed).
     * Returns -1 if the tier is out of range.
     */
    public int getThresholdForTier(int tier) {
        if (tier < 1 || tier > thresholds.length) return -1;
        return thresholds[tier - 1];
    }
}
