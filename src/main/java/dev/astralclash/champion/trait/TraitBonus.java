package dev.astralclash.champion.trait;

/**
 * Holds the computed bonus values for an active trait at a specific tier.
 * The fields that are relevant depend on the trait — unused fields stay at 0.
 */
public class TraitBonus {

    private final Trait trait;
    private final int   tier;           // 1, 2, 3 …

    // Common bonus fields
    private double atkBonusPercent     = 0;
    private double atkSpeedBonusPercent = 0;
    private double shieldAmountFlat    = 0;
    private double healPerRoundFlat    = 0;
    private double dotMultiplier       = 1.0;   // extra DoT multiplier
    private double aoeBonusPercent     = 0;
    private double trueDamageThreshold = 0;     // PHYSICAL: every N-th hit
    private double omnivampPercent     = 0;
    private double moveSpeedPercent    = 0;
    private double magicResistShred    = 0;
    private double critRateBonus       = 0;
    private double chainDamagePercent  = 0;
    private double burnChance          = 0;
    private double slowPercent         = 0;

    public TraitBonus(Trait trait, int tier) {
        this.trait = trait;
        this.tier  = tier;
        compute();
    }

    /** Fills in all bonus fields based on trait + tier. */
    private void compute() {
        switch (trait) {
            case HUNT -> {
                atkBonusPercent      = tier == 1 ? 0.15 : tier == 2 ? 0.30 : 0.50;
                atkSpeedBonusPercent = tier == 1 ? 0.10 : tier == 2 ? 0.20 : 0.40;
            }
            case DESTRUCTION -> {
                atkBonusPercent  = tier == 1 ? 0.20 : tier == 2 ? 0.40 : 0.70;
                omnivampPercent  = tier == 1 ? 0.10 : tier == 2 ? 0.20 : 0.35;
            }
            case ERUDITION -> {
                aoeBonusPercent  = tier == 1 ? 0.10 : tier == 2 ? 0.25 : 0.45;
            }
            case HARMONY -> {
                atkBonusPercent      = tier == 1 ? 0.10 : tier == 2 ? 0.20 : 0.35;
                atkSpeedBonusPercent = tier == 1 ? 0.10 : tier == 2 ? 0.20 : 0.30;
            }
            case NIHILITY -> {
                dotMultiplier        = tier == 1 ? 1.50 : 2.25;
            }
            case PRESERVATION -> {
                shieldAmountFlat     = tier == 1 ? 150 : tier == 2 ? 300 : 500;
            }
            case ABUNDANCE -> {
                healPerRoundFlat     = tier == 1 ? 40 : 100;
            }
            case FIRE -> {
                burnChance           = tier == 1 ? 0.20 : 0.45;
            }
            case ICE -> {
                slowPercent          = tier == 1 ? 0.20 : 0.40;
            }
            case WIND -> {
                moveSpeedPercent     = tier == 1 ? 0.15 : 0.30;
            }
            case LIGHTNING -> {
                chainDamagePercent   = tier == 1 ? 0.25 : 0.50;
            }
            case QUANTUM -> {
                magicResistShred     = tier == 1 ? 5 : 12;   // per-hit MR reduction
            }
            case IMAGINARY -> {
                // Applied via status in CombatEngine
            }
            case PHYSICAL -> {
                trueDamageThreshold  = tier == 1 ? 3 : 2;    // every N attacks = true dmg
            }
        }
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public Trait  getTrait()                  { return trait; }
    public int    getTier()                   { return tier; }

    public double getAtkBonusPercent()        { return atkBonusPercent; }
    public double getAtkSpeedBonusPercent()   { return atkSpeedBonusPercent; }
    public double getShieldAmountFlat()       { return shieldAmountFlat; }
    public double getHealPerRoundFlat()       { return healPerRoundFlat; }
    public double getDotMultiplier()          { return dotMultiplier; }
    public double getAoeBonusPercent()        { return aoeBonusPercent; }
    public double getTrueDamageThreshold()    { return trueDamageThreshold; }
    public double getOmnivampPercent()        { return omnivampPercent; }
    public double getMoveSpeedPercent()       { return moveSpeedPercent; }
    public double getMagicResistShred()       { return magicResistShred; }
    public double getCritRateBonus()          { return critRateBonus; }
    public double getChainDamagePercent()     { return chainDamagePercent; }
    public double getBurnChance()             { return burnChance; }
    public double getSlowPercent()            { return slowPercent; }
}
