package io.github.larsonix.trailoforbis.mobs.model;

import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.elemental.ElementType;
import io.github.larsonix.trailoforbis.elemental.ElementalStats;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Immutable stat container for a scaled mob.
 *
 * <p>Contains the calculated level and stats from the unified pool system.
 * All stats come from a single pool (level + distance contributions) that is
 * distributed using a 40% fixed / 60% random split across 10 stats.
 *
 * <p><b>Stats in the Pool (10 total):</b>
 * <ol>
 *   <li>Health - Maximum hit points</li>
 *   <li>Physical Damage - Base attack damage</li>
 *   <li>Armor - Damage reduction</li>
 *   <li>Movement Speed - Movement speed multiplier</li>
 *   <li>Critical Chance - % chance to land a critical hit</li>
 *   <li>Critical Multiplier - Bonus damage on critical hits</li>
 *   <li>Dodge Chance - % chance to avoid incoming attacks</li>
 *   <li>Life Steal - % of damage dealt converted to healing</li>
 *   <li>Armor Penetration - % of target's armor ignored</li>
 *   <li>Health Regen - HP regenerated per second</li>
 * </ol>
 *
 * <p>MobStats integrates with the existing combat system through {@link #toComputedStats()},
 * allowing the {@code RPGDamageSystem} to use mob stats in damage calculations.
 *
 * <p>Example:
 * <pre>
 * // Create stats for a level 10 mob with 150 total pool
 * MobStats stats = new MobStats(10, 150.0, 80.0, 25.0, 15.0, 1.1,
 *     12.0, 180.0, 8.0, 5.0, 20.0, 3.0);
 *
 * // Use in damage system
 * ComputedStats combatStats = stats.toComputedStats();
 * </pre>
 *
 * @param level             Effective mob level (from player avg × group multiplier)
 * @param totalPool         Total pool points (level + distance, for debugging/display)
 * @param maxHealth         Calculated health from pool allocation
 * @param physicalDamage    Calculated damage from pool allocation
 * @param armor             Calculated armor from pool allocation
 * @param moveSpeed         Movement speed multiplier (1.0 = normal, 1.5 = 50% faster)
 * @param criticalChance    Critical hit chance percentage (0-30%)
 * @param criticalMultiplier Critical damage multiplier percentage (e.g., 150 = 1.5×, 250 = 2.5×)
 * @param dodgeChance       Chance to completely avoid an attack (0-25%)
 * @param lifeSteal         Percentage of damage dealt converted to healing (0-15%)
 * @param armorPenetration  Percentage of target's armor to ignore (0-40%)
 * @param healthRegen       HP regenerated per second (0-10)
 * @param blockChance       Chance to block and negate incoming damage (0-40%)
 * @param parryChance       Chance to parry and reflect damage back to attacker (0-30%)
 * @param trueDamage        Damage that bypasses armor entirely
 * @param accuracy          Accuracy rating for hit calculations (counters player evasion)
 * @param knockbackResistance Resistance to knockback (0-100%, reduces knockback taken)
 * @param elementalStats    Elemental damage and resistance stats (nullable for backwards compat)
 */
public record MobStats(
    int level,
    double totalPool,
    double maxHealth,
    double physicalDamage,
    double armor,
    double moveSpeed,
    double criticalChance,
    double criticalMultiplier,
    double dodgeChance,
    double lifeSteal,
    double armorPenetration,
    double healthRegen,
    double blockChance,
    double parryChance,
    double trueDamage,
    double accuracy,
    double knockbackResistance,
    @Nullable ElementalStats elementalStats
) {
    /**
     * Default stats for unscaled mobs (level 1, no pool).
     * Used for mobs in the safe zone or when scaling is disabled.
     *
     * <p>With the unified pool system, these are minimum viable stats
     * that represent a level 1 mob with no scaling applied.
     */
    public static final MobStats UNSCALED = new MobStats(
        1,      // level
        0.0,    // totalPool (no scaling)
        50.0,   // maxHealth (minimum)
        5.0,    // physicalDamage (minimum)
        0.0,    // armor (none)
        1.0,    // moveSpeed (normal)
        5.0,    // criticalChance (5% base)
        150.0,  // criticalMultiplier (1.5× base)
        0.0,    // dodgeChance (none)
        0.0,    // lifeSteal (none)
        0.0,    // armorPenetration (none)
        0.0,    // healthRegen (none)
        0.0,    // blockChance (none)
        0.0,    // parryChance (none)
        0.0,    // trueDamage (none)
        100.0,  // accuracy (base 100 = normal hit chance)
        0.0,    // knockbackResistance (none)
        null    // elementalStats (none for unscaled)
    );

    /**
     * Converts to ComputedStats for damage system compatibility.
     *
     * <p>This allows the {@code RPGDamageSystem} to treat mob stats the same as
     * player stats when calculating damage. The combat calculator can use these
     * stats for both attacker and defender calculations.
     *
     * @return A ComputedStats instance with the mob's combat-relevant stats
     */
    @Nonnull
    public ComputedStats toComputedStats() {
        ComputedStats.Builder builder = ComputedStats.builder()
            .maxHealth((float) maxHealth)
            .physicalDamage((float) physicalDamage)
            .armor((float) armor)
            .movementSpeedPercent((float) ((moveSpeed - 1.0) * 100.0)) // Convert to percent bonus
            .criticalChance((float) criticalChance)
            .criticalMultiplier((float) criticalMultiplier)
            .evasion((float) dodgeChance) // Maps to evasion in ComputedStats
            .lifeSteal((float) lifeSteal)
            .armorPenetration((float) armorPenetration)
            .healthRegen((float) healthRegen)
            .passiveBlockChance((float) blockChance)
            .parryChance((float) parryChance)
            .trueDamage((float) trueDamage)
            .accuracy((float) accuracy)
            .knockbackResistance((float) knockbackResistance)
            .mobStats(true); // Mark as mob-origin stats

        // Add elemental penetration from elementalStats if present
        if (elementalStats != null) {
            builder.firePenetration((float) elementalStats.getPenetration(ElementType.FIRE))
                   .waterPenetration((float) elementalStats.getPenetration(ElementType.WATER))
                   .lightningPenetration((float) elementalStats.getPenetration(ElementType.LIGHTNING))
                   .voidPenetration((float) elementalStats.getPenetration(ElementType.VOID));
        }

        return builder.build();
    }

    /**
     * Creates a modified copy with a boss multiplier applied.
     *
     * <p>Only combat stats are multiplied. Percentage-based stats (crit chance,
     * dodge chance, life steal, armor penetration) are NOT multiplied to prevent
     * unreasonably high values.
     *
     * <p>Elemental stats are copied as-is (flat damage scales with level already).
     *
     * @param multiplier The multiplier to apply (e.g., 1.5 for bosses, 1.25 for elites)
     * @return New MobStats with multiplied stats
     */
    @Nonnull
    public MobStats withMultiplier(double multiplier) {
        return new MobStats(
            level,
            totalPool * multiplier,
            maxHealth * multiplier,
            physicalDamage * multiplier,
            armor * multiplier,
            moveSpeed,  // Speed doesn't scale with multiplier
            criticalChance,  // Percentage stats don't scale
            criticalMultiplier,  // Keep crit multiplier as-is
            dodgeChance,  // Percentage stats don't scale
            lifeSteal,  // Percentage stats don't scale
            armorPenetration,  // Percentage stats don't scale
            healthRegen * multiplier,  // Regen scales with multiplier
            blockChance,  // Percentage stats don't scale
            parryChance,  // Percentage stats don't scale
            trueDamage * multiplier,  // Flat damage scales with multiplier
            accuracy,  // Accuracy doesn't scale with multiplier
            knockbackResistance,  // Knockback resistance doesn't scale
            elementalStats != null ? elementalStats.copy() : null  // Copy elemental stats
        );
    }

    /**
     * Checks if this mob is stronger than the unscaled baseline.
     *
     * @return true if any stat exceeds the unscaled default
     */
    public boolean isScaled() {
        return level > 1 || totalPool > 0;
    }

    /** Rough estimate combining level and total pool for display. */
    public int getEffectivePower() {
        return (int) (level + (totalPool / 10.0));
    }

    @Override
    public String toString() {
        return String.format(
            "MobStats{lv=%d, pool=%.0f, hp=%.0f, dmg=%.0f, armor=%.0f, spd=%.2f, " +
            "crit=%.0f%%/%.0f%%, dodge=%.0f%%, steal=%.0f%%, pen=%.0f%%, regen=%.1f/s, " +
            "block=%.0f%%, parry=%.0f%%, true=%.0f, acc=%.0f, kbRes=%.0f%%}",
            level, totalPool, maxHealth, physicalDamage, armor, moveSpeed,
            criticalChance, criticalMultiplier, dodgeChance, lifeSteal, armorPenetration, healthRegen,
            blockChance, parryChance, trueDamage, accuracy, knockbackResistance
        );
    }

    public String toShortString() {
        return String.format(
            "Lv%d HP%.0f DMG%.0f ARM%.0f SPD%.2f",
            level, maxHealth, physicalDamage, armor, moveSpeed
        );
    }

    // ==================== Elemental Stats Convenience Methods ====================

    public boolean hasElementalDamage() {
        return elementalStats != null && elementalStats.hasAnyElementalDamage();
    }

    /** @return element with highest flat damage, or null if none */
    @Nullable
    public ElementType getPrimaryElement() {
        return elementalStats != null ? elementalStats.getPrimaryElement() : null;
    }

    /** @return 0 if no elemental stats */
    public double getFireDamage() {
        return elementalStats != null ? elementalStats.getFlatDamage(ElementType.FIRE) : 0;
    }

    /** @return 0 if no elemental stats */
    public double getWaterDamage() {
        return elementalStats != null ? elementalStats.getFlatDamage(ElementType.WATER) : 0;
    }

    /** @return 0 if no elemental stats */
    public double getLightningDamage() {
        return elementalStats != null ? elementalStats.getFlatDamage(ElementType.LIGHTNING) : 0;
    }

    /** @return 0 if no elemental stats */
    public double getVoidDamage() {
        return elementalStats != null ? elementalStats.getFlatDamage(ElementType.VOID) : 0;
    }

    /** @return 0 if no elemental stats */
    public double getFireResistance() {
        return elementalStats != null ? elementalStats.getResistance(ElementType.FIRE) : 0;
    }

    /** @return 0 if no elemental stats */
    public double getWaterResistance() {
        return elementalStats != null ? elementalStats.getResistance(ElementType.WATER) : 0;
    }

    /** @return 0 if no elemental stats */
    public double getLightningResistance() {
        return elementalStats != null ? elementalStats.getResistance(ElementType.LIGHTNING) : 0;
    }

    /** @return 0 if no elemental stats */
    public double getVoidResistance() {
        return elementalStats != null ? elementalStats.getResistance(ElementType.VOID) : 0;
    }

    // ==================== Elemental Penetration Convenience Methods ====================

    /** @return 0 if no elemental stats */
    public double getFirePenetration() {
        return elementalStats != null ? elementalStats.getPenetration(ElementType.FIRE) : 0;
    }

    /** @return 0 if no elemental stats */
    public double getWaterPenetration() {
        return elementalStats != null ? elementalStats.getPenetration(ElementType.WATER) : 0;
    }

    /** @return 0 if no elemental stats */
    public double getLightningPenetration() {
        return elementalStats != null ? elementalStats.getPenetration(ElementType.LIGHTNING) : 0;
    }

    /** @return 0 if no elemental stats */
    public double getVoidPenetration() {
        return elementalStats != null ? elementalStats.getPenetration(ElementType.VOID) : 0;
    }

    @Nonnull
    public ElementalStats getElementalStatsOrEmpty() {
        return elementalStats != null ? elementalStats : new ElementalStats();
    }

    @Nonnull
    public MobStats withElementalStats(@Nullable ElementalStats elemental) {
        return new MobStats(
            level, totalPool, maxHealth, physicalDamage, armor, moveSpeed,
            criticalChance, criticalMultiplier, dodgeChance, lifeSteal,
            armorPenetration, healthRegen, blockChance, parryChance, trueDamage,
            accuracy, knockbackResistance, elemental
        );
    }
}
