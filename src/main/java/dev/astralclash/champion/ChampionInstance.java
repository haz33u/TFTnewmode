package dev.astralclash.champion;

import dev.astralclash.board.BoardCell;
import dev.astralclash.combat.StatusEffect;

import java.util.*;

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
    private double bonusAtkPercent     = 0;
    private double bonusAtkSpeedPercent = 0;
    private double omnivampPercent     = 0;

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

    /** Tick down status durations. Called each combat tick. */
    public void tickStatuses() {
        statusEffects.entrySet().removeIf(e -> {
            e.setValue(e.getValue() - 1);
            return e.getValue() <= 0;
        });
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

    public Map<StatusEffect, Integer> getStatusEffects()  { return Collections.unmodifiableMap(statusEffects); }

    @Override
    public String toString() {
        return champion.getDisplayName() + " (" + starLevel.getStars() + "★) HP=" +
               String.format("%.0f/%.0f", currentHp, maxHp);
    }
}
