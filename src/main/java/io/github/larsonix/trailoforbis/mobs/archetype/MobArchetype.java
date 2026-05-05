package io.github.larsonix.trailoforbis.mobs.archetype;

import javax.annotation.Nonnull;

/**
 * Combat archetype defining a mob's stat multipliers and identity.
 *
 * <p>8 archetypes validated against 78 actual Hytale NPC IDs.
 * Each archetype defines multipliers on base pool stats, plus
 * fixed offensive/defensive values.
 *
 * <p>Archetypes are auto-assigned via {@link ArchetypeResolver} using
 * config overrides, NPC group detection, and role name keywords.
 *
 * @see ArchetypeResolver
 */
public enum MobArchetype {
    /** Balanced baseline — no strengths or weaknesses. */
    WARRIOR("Warrior", 1.0, 1.0, 1.0, 1.0, 1.0, 10, 160, 5, 0, 0),

    /** Slow, tanky, heavy hits. High HP and armor, low evasion and speed. */
    BRUTE("Brute", 1.4, 1.2, 1.3, 0.5, 0.85, 5, 150, 10, 5, 50),

    /** Fragile, evasive, ranged. Low HP and armor, high evasion and speed. */
    RANGER("Ranger", 0.7, 1.1, 0.5, 1.5, 1.1, 15, 170, 0, 0, 0),

    /** Glass cannon, elemental focus. Very low armor, high damage. */
    CASTER("Caster", 0.6, 1.3, 0.3, 0.8, 0.9, 10, 180, 0, 0, 0),

    /** High crit, fast, evasive. Burst damage from critical strikes. */
    ASSASSIN("Assassin", 0.8, 1.4, 0.4, 1.8, 1.3, 30, 250, 20, 10, 0),

    /** Maximum durability. Extremely high HP and armor, very low damage. */
    TANK("Tank", 1.8, 0.7, 2.0, 0.3, 0.7, 5, 130, 0, 0, 80),

    /** Future: buffs allies. Moderate stats all around. */
    SUPPORT("Support", 0.9, 0.6, 0.8, 1.0, 1.0, 5, 130, 0, 0, 0),

    /** Animals/creatures with no role keywords. Moderate all-rounder, slightly fast. */
    BEAST("Beast", 1.1, 1.1, 0.7, 1.2, 1.15, 8, 160, 0, 0, 0);

    private final String displayName;

    // Multipliers applied to base pool stats
    private final double hpMultiplier;
    private final double damageMultiplier;
    private final double armorMultiplier;
    private final double evasionMultiplier;
    private final double speedMultiplier;

    // Fixed offensive values (not multipliers)
    private final double critChance;
    private final double critMultiplier;
    private final double armorPenetration;
    private final double lifeSteal;
    private final double knockbackResistance;

    MobArchetype(String displayName,
                 double hpMultiplier, double damageMultiplier, double armorMultiplier,
                 double evasionMultiplier, double speedMultiplier,
                 double critChance, double critMultiplier,
                 double armorPenetration, double lifeSteal, double knockbackResistance) {
        this.displayName = displayName;
        this.hpMultiplier = hpMultiplier;
        this.damageMultiplier = damageMultiplier;
        this.armorMultiplier = armorMultiplier;
        this.evasionMultiplier = evasionMultiplier;
        this.speedMultiplier = speedMultiplier;
        this.critChance = critChance;
        this.critMultiplier = critMultiplier;
        this.armorPenetration = armorPenetration;
        this.lifeSteal = lifeSteal;
        this.knockbackResistance = knockbackResistance;
    }

    @Nonnull
    public String getDisplayName() { return displayName; }

    public double getHpMultiplier() { return hpMultiplier; }
    public double getDamageMultiplier() { return damageMultiplier; }
    public double getArmorMultiplier() { return armorMultiplier; }
    public double getEvasionMultiplier() { return evasionMultiplier; }
    public double getSpeedMultiplier() { return speedMultiplier; }

    public double getCritChance() { return critChance; }
    public double getCritMultiplier() { return critMultiplier; }
    public double getArmorPenetration() { return armorPenetration; }
    public double getLifeSteal() { return lifeSteal; }
    public double getKnockbackResistance() { return knockbackResistance; }

    /**
     * Finds an archetype by name (case-insensitive).
     *
     * @param name The archetype name
     * @return The matching archetype, or {@code null} if not found
     */
    @javax.annotation.Nullable
    public static MobArchetype fromName(@Nonnull String name) {
        for (MobArchetype archetype : values()) {
            if (archetype.name().equalsIgnoreCase(name) ||
                archetype.displayName.equalsIgnoreCase(name)) {
                return archetype;
            }
        }
        return null;
    }
}
