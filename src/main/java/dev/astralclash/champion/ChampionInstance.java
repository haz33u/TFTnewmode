package dev.astralclash.champion;

import dev.astralclash.board.BoardCell;
import dev.astralclash.combat.StatusEffect;

import java.util.*;
import java.util.EnumSet;

/**
 * A live instance of a {@link Champion} on the board or bench.
 * Holds runtime state: current HP, mana, star level, position, status effects.
 */
public class ChampionInstance {

    private final Champion   champion;
    private StarLevel        starLevel;

    // Runtime stats (computed from base × star multiplier × buffs)
    private double currentHp;
    private double maxHp;
    private double armor;
    private double magicResist;
    private double attackDamage;
    private double attackSpeed;
    private int    attackRange;
    private double moveSpeed;
    private double currentMana;
    private double shield;      // flat shield absorbing damage first
    private int    attackCounter; // counts basic attacks (for PHYSICAL trait)

    // Board position (null = on bench)
    private BoardCell cell;

    // Status effects with remaining duration (in combat ticks)
    private final Map<StatusEffect, Integer> statusEffects = new EnumMap<>(StatusEffect.class);

    // Trait bonuses applied to this instance (set by CombatEngine)
    private double bonusAtkPercent      = 0;
    private double bonusAtkSpeedPercent = 0;
    private double omnivampPercent      = 0;

    // Temporary ATK bonus from Bronya's ability — tracked for revert on expiry
    private double harmonyBuffBonus     = 0;

    // ── Trait-specific combat mechanics ──────────────────────────────────────

    /** HUNT: target lowest-HP enemy instead of nearest */
    private boolean hasHuntTargeting       = false;

    /** DESTRUCTION: stored bonuses for 50%-HP trigger */
    private boolean hasDestructionTrait    = false;
    private double  destructionAtkBonus    = 0;
    private double  destructionOmnivampBonus = 0;
    private boolean destructionTriggered   = false;

    /** NIHILITY (received): how much harder DoTs tick against this unit */
    private double  dotReceivedMultiplier  = 1.0;

    /** ERUDITION: after any ability fires, send a bonus true-damage pulse to all enemies */
    private double  aoeBonusPercent        = 0;

    /** PHYSICAL: every physicalBonusEveryN-th basic attack deals 50% ATK as bonus true damage (0 = off) */
    private int     physicalBonusEveryN    = 0;

    /** FIRE: chance to apply Burn on each basic attack */
    private double  burnChance             = 0;

    /** ICE: apply Slow on each basic attack */
    private boolean hasIceSlow             = false;

    /** LIGHTNING: chain chainDamagePercent * ATK to a random enemy after ability */
    private double  chainDamagePercent     = 0;

    /** QUANTUM: reduce target MR by this amount on each basic attack */
    private double  magicResistShred       = 0;

    /** IMAGINARY: apply Weakness on first ability cast */
    private boolean hasImaginaryTrait      = false;
    private boolean imaginaryFirstCastDone = false;

    // Owner identifier (which ArenaPlayer this belongs to)
    private UUID ownerId;

    // ModelEngine entity UUID
    private UUID modelEntityId;

    // Alive flag
    private boolean alive = true;

    // ── Constructor ──────────────────────────────────────────────────────────

    public ChampionInstance(Champion champion, StarLevel starLevel, UUID ownerId) {
        this.champion  = champion;
        this.starLevel = starLevel;
        this.ownerId   = ownerId;
        recalculateStats();
    }

    // ── Stat computation ─────────────────────────────────────────────────────

    /**
     * Recalculates all runtime stats from base × star multiplier.
     * Called on star-up or when base trait bonuses change.
     */
    public void recalculateStats() {
        double m = starLevel.getMultiplier();
        maxHp        = champion.getBaseHp()           * m;
        armor        = champion.getBaseArmor()        * m;
        magicResist  = champion.getBaseMagicResist()  * m;
        attackDamage = champion.getBaseAttackDamage() * m * (1 + bonusAtkPercent);
        attackSpeed  = champion.getBaseAttackSpeed()  * (1 + bonusAtkSpeedPercent);
        attackRange  = champion.getBaseAttackRange();
        moveSpeed    = champion.getBaseMoveSpeed();
        currentHp    = maxHp;
        currentMana  = 0;
        shield       = 0;
    }

    // ── Damage / Healing ─────────────────────────────────────────────────────

    /**
     * Applies physical damage (reduced by armor).
     * Returns actual damage taken after mitigation.
     */
    public double takeDamage(double rawDamage) {
        double mitigation = armor / (armor + 100.0);
        double dmg = rawDamage * (1 - mitigation);
        return applyDamageAfterShield(dmg);
    }

    /**
     * Applies magic damage (reduced by magic resist).
     */
    public double takeMagicDamage(double rawDamage) {
        double mitigation = magicResist / (magicResist + 100.0);
        double dmg = rawDamage * (1 - mitigation);
        return applyDamageAfterShield(dmg);
    }

    /**
     * Applies true damage (no mitigation, bypasses shield only partially).
     */
    public double takeTrueDamage(double rawDamage) {
        return applyDamageAfterShield(rawDamage);
    }

    private double applyDamageAfterShield(double dmg) {
        if (shield > 0) {
            double absorbed = Math.min(shield, dmg);
            shield -= absorbed;
            dmg    -= absorbed;
        }
        currentHp -= dmg;
        if (currentHp <= 0) {
            currentHp = 0;
            alive     = false;
        }
        return dmg;
    }

    public void heal(double amount) {
        currentHp = Math.min(maxHp, currentHp + amount);
    }

    public void addShield(double amount) {
        shield += amount;
    }

    // ── Mana ─────────────────────────────────────────────────────────────────

    /** Adds mana. Returns true if the ability is now ready to fire. */
    public boolean addMana(double amount) {
        if (champion.getMaxMana() <= 0) return false; // Blade uses HP, no mana
        currentMana = Math.min(champion.getMaxMana(), currentMana + amount);
        return currentMana >= champion.getMaxMana();
    }

    /** Called after the ability fires to reset mana. */
    public void consumeMana() {
        currentMana = 0;
    }

    // ── Star-up ──────────────────────────────────────────────────────────────

    public void starUp() {
        if (!starLevel.isMaxStar()) {
            starLevel = starLevel.next();
            recalculateStats();
        }
    }

    // ── Status effects ───────────────────────────────────────────────────────

    public void applyStatus(StatusEffect effect, int durationTicks) {
        statusEffects.merge(effect, durationTicks, Math::max);
    }

    public boolean hasStatus(StatusEffect effect) {
        return statusEffects.containsKey(effect);
    }

    /** Removes a specific status effect immediately (used for cleanse effects). */
    public void removeStatus(StatusEffect effect) {
        statusEffects.remove(effect);
    }

    /**
     * Tick down status durations. Called each combat tick.
     * Returns the set of effects whose duration just expired this tick.
     */
    public Set<StatusEffect> tickStatuses() {
        Set<StatusEffect> expired = EnumSet.noneOf(StatusEffect.class);
        statusEffects.entrySet().removeIf(e -> {
            e.setValue(e.getValue() - 1);
            if (e.getValue() <= 0) {
                expired.add(e.getKey());
                return true;
            }
            return false;
        });
        return expired;
    }

    // ── Getters / Setters ────────────────────────────────────────────────────

    public Champion  getChampion()                        { return champion; }
    public StarLevel getStarLevel()                       { return starLevel; }

    public double    getCurrentHp()                       { return currentHp; }
    public double    getMaxHp()                           { return maxHp; }
    public double    getHpPercent()                       { return currentHp / maxHp; }
    public double    getArmor()                           { return armor; }
    public double    getMagicResist()                     { return magicResist; }
    public double    getAttackDamage()                    { return attackDamage; }
    public double    getAttackSpeed()                     { return attackSpeed; }
    public int       getAttackRange()                     { return attackRange; }
    public double    getMoveSpeed()                       { return moveSpeed; }
    public double    getCurrentMana()                     { return currentMana; }
    public double    getShield()                          { return shield; }

    public void      setArmor(double v)                   { armor = v; }
    public void      setMagicResist(double v)             { magicResist = v; }
    public void      setAttackDamage(double v)            { attackDamage = v; }
    public void      setAttackSpeed(double v)             { attackSpeed = v; }
    public void      setMoveSpeed(double v)               { moveSpeed = v; }

    public int       getAttackCounter()                   { return attackCounter; }
    public void      incrementAttackCounter()             { attackCounter++; }

    public BoardCell getCell()                            { return cell; }
    public void      setCell(BoardCell cell)              { this.cell = cell; }

    public UUID      getOwnerId()                         { return ownerId; }

    public UUID      getModelEntityId()                   { return modelEntityId; }
    public void      setModelEntityId(UUID id)            { this.modelEntityId = id; }

    public boolean   isAlive()                            { return alive; }
    public void      setAlive(boolean v)                  { alive = v; }

    public void      setBonusAtkPercent(double v)         { bonusAtkPercent = v; }
    public void      setBonusAtkSpeedPercent(double v)    { bonusAtkSpeedPercent = v; }
    public void      setOmnivampPercent(double v)         { omnivampPercent = v; }
    public double    getOmnivampPercent()                 { return omnivampPercent; }

    public double    getHarmonyBuffBonus()                { return harmonyBuffBonus; }
    public void      setHarmonyBuffBonus(double v)        { harmonyBuffBonus = v; }

    public Map<StatusEffect, Integer> getStatusEffects()  { return Collections.unmodifiableMap(statusEffects); }

    // ── Trait-specific getters / setters ─────────────────────────────────────

    public boolean isHasHuntTargeting()                        { return hasHuntTargeting; }
    public void    setHasHuntTargeting(boolean v)              { hasHuntTargeting = v; }

    public boolean isHasDestructionTrait()                     { return hasDestructionTrait; }
    public void    setHasDestructionTrait(boolean v)           { hasDestructionTrait = v; }
    public double  getDestructionAtkBonus()                    { return destructionAtkBonus; }
    public void    setDestructionAtkBonus(double v)            { destructionAtkBonus = v; }
    public double  getDestructionOmnivampBonus()               { return destructionOmnivampBonus; }
    public void    setDestructionOmnivampBonus(double v)       { destructionOmnivampBonus = v; }
    public boolean isDestructionTriggered()                    { return destructionTriggered; }
    public void    setDestructionTriggered(boolean v)          { destructionTriggered = v; }

    public double  getDotReceivedMultiplier()                  { return dotReceivedMultiplier; }
    public void    setDotReceivedMultiplier(double v)          { dotReceivedMultiplier = v; }

    public double  getAoeBonusPercent()                        { return aoeBonusPercent; }
    public void    setAoeBonusPercent(double v)                { aoeBonusPercent = v; }

    public int     getPhysicalBonusEveryN()                    { return physicalBonusEveryN; }
    public void    setPhysicalBonusEveryN(int v)               { physicalBonusEveryN = v; }

    public double  getBurnChance()                             { return burnChance; }
    public void    setBurnChance(double v)                     { burnChance = v; }

    public boolean isHasIceSlow()                              { return hasIceSlow; }
    public void    setHasIceSlow(boolean v)                    { hasIceSlow = v; }

    public double  getChainDamagePercent()                     { return chainDamagePercent; }
    public void    setChainDamagePercent(double v)             { chainDamagePercent = v; }

    public double  getMagicResistShred()                       { return magicResistShred; }
    public void    setMagicResistShred(double v)               { magicResistShred = v; }

    public boolean isHasImaginaryTrait()                       { return hasImaginaryTrait; }
    public void    setHasImaginaryTrait(boolean v)             { hasImaginaryTrait = v; }
    public boolean isImaginaryFirstCastDone()                  { return imaginaryFirstCastDone; }
    public void    setImaginaryFirstCastDone(boolean v)        { imaginaryFirstCastDone = v; }

    @Override
    public String toString() {
        return champion.getDisplayName() + " (" + starLevel.getStars() + "★) HP=" +
               String.format("%.0f/%.0f", currentHp, maxHp);
    }
}
