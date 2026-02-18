package dev.astralclash.champion;

import dev.astralclash.champion.ability.Ability;
import dev.astralclash.champion.trait.Trait;

import java.util.List;

/**
 * Static data for a champion — loaded once from champions.yml.
 * Instances on the board are represented by {@link ChampionInstance}.
 */
public class Champion {

    private final String        id;            // yaml key  e.g. "kafka"
    private final String        displayName;
    private final ChampionTier  tier;
    private final List<Trait>   traits;

    // Base stats (1-star)
    private final double        baseHp;
    private final double        baseArmor;
    private final double        baseMagicResist;
    private final double        baseAttackDamage;
    private final double        baseAttackSpeed;   // attacks/second
    private final int           baseAttackRange;   // cells
    private final double        baseMoveSpeed;
    private final int           maxMana;

    // ModelEngine model id
    private final String        modelId;

    // Ability (set separately after construction)
    private Ability ability;

    public Champion(String id,
                    String displayName,
                    ChampionTier tier,
                    List<Trait> traits,
                    double baseHp,
                    double baseArmor,
                    double baseMagicResist,
                    double baseAttackDamage,
                    double baseAttackSpeed,
                    int baseAttackRange,
                    double baseMoveSpeed,
                    int maxMana,
                    String modelId) {
        this.id              = id;
        this.displayName     = displayName;
        this.tier            = tier;
        this.traits          = List.copyOf(traits);
        this.baseHp          = baseHp;
        this.baseArmor       = baseArmor;
        this.baseMagicResist = baseMagicResist;
        this.baseAttackDamage = baseAttackDamage;
        this.baseAttackSpeed = baseAttackSpeed;
        this.baseAttackRange = baseAttackRange;
        this.baseMoveSpeed   = baseMoveSpeed;
        this.maxMana         = maxMana;
        this.modelId         = modelId;
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public String        getId()                { return id; }
    public String        getDisplayName()       { return displayName; }
    public ChampionTier  getTier()              { return tier; }
    public List<Trait>   getTraits()            { return traits; }

    public double        getBaseHp()            { return baseHp; }
    public double        getBaseArmor()         { return baseArmor; }
    public double        getBaseMagicResist()   { return baseMagicResist; }
    public double        getBaseAttackDamage()  { return baseAttackDamage; }
    public double        getBaseAttackSpeed()   { return baseAttackSpeed; }
    public int           getBaseAttackRange()   { return baseAttackRange; }
    public double        getBaseMoveSpeed()     { return baseMoveSpeed; }
    public int           getMaxMana()           { return maxMana; }
    public String        getModelId()           { return modelId; }

    public Ability       getAbility()           { return ability; }
    public void          setAbility(Ability a)  { this.ability = a; }

    @Override
    public String toString() {
        return "Champion{" + id + ", tier=" + tier.getCost() + "}";
    }
}
