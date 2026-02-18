package dev.astralclash.combat;

/**
 * All status effects that can be applied to a {@link dev.astralclash.champion.ChampionInstance}
 * during combat.
 */
public enum StatusEffect {

    // ── Negative (debuffs) ───────────────────────────────────────────────────
    BURN,           // Fire DoT — magic damage per 20 ticks
    SHOCK,          // Lightning DoT — magic damage per 20 ticks (Kafka)
    POISON,         // Nihility generic DoT — true damage per 20 ticks
    STUN,           // Cannot act (Welt Imprisonment)
    SLOW,           // Reduced attack speed (Dan Heng, Ice trait)
    FREEZE,         // Cannot act + loses armor bonus (Ice trait counter)
    ARMOR_SHRED,    // Armor has been reduced (Pela)
    WEAKNESS,       // Imaginary trait: 50% extra damage from next ability hit

    // ── Positive (buffs) ────────────────────────────────────────────────────
    HARMONY_BUFF,   // Generic: ATK/SPD buff from Harmony ability (Bronya, Asta)
    GEPARD_SHIELD,  // Gepard's freeze-counter shield marker
    FU_XUAN_MATRIX, // Allies marked for damage redirection to Fu Xuan
    FU_XUAN_TARGET, // Fu Xuan herself, marked as the redirect sink
    LUOCHA_CLEANSE, // Signal to CombatEngine to cleanse one debuff this tick

    // ── Passive markers ──────────────────────────────────────────────────────
    DESTRUCTION_PROC, // Destruction trait: below 50% HP buff triggered
}
