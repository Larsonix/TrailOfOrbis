package io.github.larsonix.trailoforbis.combat.deathrecap;

import io.github.larsonix.trailoforbis.combat.AttackType;
import io.github.larsonix.trailoforbis.combat.DamageBreakdown;
import io.github.larsonix.trailoforbis.combat.DamageType;
import io.github.larsonix.trailoforbis.elemental.ElementType;
import io.github.larsonix.trailoforbis.mobs.classification.RPGMobClass;

import java.util.EnumMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;

/**
 * Immutable snapshot of combat data captured at the moment of damage.
 *
 * <p>Used by the death recap system to show players exactly how they died,
 * with full damage breakdown from base damage through all modifiers to final damage.
 *
 * <p>This record captures:
 * <ul>
 *   <li>Attacker information (name, type, level, mob class)</li>
 *   <li>Full damage calculation breakdown</li>
 *   <li>Armor reduction details</li>
 *   <li>Elemental damage and resistances</li>
 *   <li>Post-calc effects (shield, block, parry)</li>
 *   <li>Defender stats at time of hit</li>
 * </ul>
 *
 * @param timestamp Time the damage occurred
 * @param attackerName Display name of the attacker (player username, formatted mob name, or ailment name)
 * @param attackerType Type of attacker: "player", "mob", "environment", "dot", or "spell"
 * @param attackerLevel Attacker's level (player level or mob level), 0 for environment/dot
 * @param attackerClass Mob classification (MONSTER, ELITE, LEGENDARY, MYTHIC), null for players/environment/dot
 * @param baseDamage Original base damage from weapon/attack/dot tick
 * @param wasCritical Whether this was a critical hit (tier >= 1)
 * @param critMultiplier Effective critical multiplier applied (1.0 if not crit)
 * @param critTier Crit tier (0 = no crit, 1 = single, 2+ = multicrit)
 * @param damageAfterAttackerBonuses Damage after attacker bonuses, before armor (from DamageBreakdown.preDefenseDamage())
 * @param defenderArmor Defender's total armor value
 * @param effectiveArmor Effective armor after penetration
 * @param armorPenetration Attacker's armor penetration percentage
 * @param reductionPercent Percentage of damage reduced by armor (0-90)
 * @param damageAfterArmor Physical damage after armor reduction
 * @param elementalDamage Final elemental damage per element type after resistance
 * @param elementalResist Effective resistance used in calculation per element
 * @param totalElementalDamage Total elemental damage added
 * @param defenderRawResistances Defender's raw resistance values before penetration
 * @param attackerPenetration Attacker's penetration values per element
 * @param finalDamage Final damage dealt after all calculations
 * @param damageType Type of damage (PHYSICAL, FIRE, etc.)
 * @param attackType Attack classification (MELEE, PROJECTILE, SPELL, UNKNOWN)
 * @param wasDodged Whether the attack was dodged (flat dodge chance)
 * @param wasEvaded Whether the attack was evaded (evasion vs accuracy)
 * @param wasBlocked Whether the attack was actively blocked
 * @param wasParried Whether the attack was parried
 * @param shieldAbsorbed Amount of damage absorbed by energy shield
 * @param trueDamage True damage component (bypasses all defenses)
 * @param defenderMaxHealth Defender's max health at time of hit
 * @param defenderHealthBefore Defender's health before this damage
 * @param defenderHealthAfter Defender's health after this damage
 * @param defenderEvasion Defender's evasion rating
 */
public record CombatSnapshot(
    long timestamp,
    @Nonnull String attackerName,
    @Nonnull String attackerType,
    int attackerLevel,
    @Nullable RPGMobClass attackerClass,
    float baseDamage,
    boolean wasCritical,
    float critMultiplier,
    int critTier,
    float damageAfterAttackerBonuses,
    float defenderArmor,
    float effectiveArmor,
    float armorPenetration,
    float reductionPercent,
    float damageAfterArmor,
    @Nullable Map<ElementType, Float> elementalDamage,
    @Nullable Map<ElementType, Float> elementalResist,
    float totalElementalDamage,
    @Nullable Map<ElementType, Float> defenderRawResistances,
    @Nullable Map<ElementType, Float> attackerPenetration,
    float finalDamage,
    @Nonnull DamageType damageType,
    @Nonnull AttackType attackType,
    boolean wasDodged,
    boolean wasEvaded,
    boolean wasBlocked,
    boolean wasParried,
    float shieldAbsorbed,
    float trueDamage,
    float defenderMaxHealth,
    float defenderHealthBefore,
    float defenderHealthAfter,
    float defenderEvasion
) {
    /**
     * Creates a snapshot for environment damage (fall, lava, etc.).
     */
    public static CombatSnapshot forEnvironment(
        @Nonnull String causeName,
        float damage,
        @Nonnull DamageType damageType,
        float defenderMaxHealth,
        float defenderHealthBefore
    ) {
        float healthAfter = Math.max(0f, defenderHealthBefore - damage);
        return new CombatSnapshot(
            System.currentTimeMillis(),
            causeName,
            "environment",
            0,
            null,
            damage,
            false, 1.0f, 0, damage,
            0, 0, 0, 0, damage,
            null, null, 0,
            null, null,
            damage,
            damageType,
            AttackType.UNKNOWN,
            false, false, false, false,
            0f, 0f,
            defenderMaxHealth,
            defenderHealthBefore,
            healthAfter,
            0
        );
    }

    /**
     * Creates a snapshot for DOT tick damage (burn, poison).
     *
     * <p>DOT ticks have no crit, no armor reduction, and use the ailment
     * element for damage type coloring. The attacker name includes the
     * ailment type for clear identification in the death recap timeline.
     *
     * @param attackerName Source display name (e.g., "Burn (Trork Warrior)" or just "Burn")
     * @param attackerType Source type ("dot")
     * @param attackerLevel Source mob level (0 if source unknown)
     * @param attackerClass Source mob class (null for DOTs)
     * @param tickDamage Damage dealt this tick (after defenses)
     * @param preDef Pre-defense DOT damage (before resistance)
     * @param damageType Element-based damage type (FIRE for burn, VOID for poison)
     * @param resistPercent Resistance reduction applied (0 if none)
     * @param defenderMaxHealth Defender's max health
     * @param defenderHealthBefore Defender's health before this tick
     * @return A new CombatSnapshot for DOT damage
     */
    @Nonnull
    public static CombatSnapshot forDOT(
        @Nonnull String attackerName,
        @Nonnull String attackerType,
        int attackerLevel,
        @Nullable RPGMobClass attackerClass,
        float tickDamage,
        float preDef,
        @Nonnull DamageType damageType,
        float resistPercent,
        float defenderMaxHealth,
        float defenderHealthBefore
    ) {
        float healthAfter = Math.max(0f, defenderHealthBefore - tickDamage);

        // Build elemental damage map from the DOT element
        EnumMap<ElementType, Float> elemDamage = null;
        EnumMap<ElementType, Float> elemResist = null;
        float totalElem = 0f;
        float physDmg = tickDamage;

        // Map DamageType back to ElementType for elemental display
        ElementType dotElement = damageTypeToElement(damageType);
        if (dotElement != null) {
            elemDamage = new EnumMap<>(ElementType.class);
            elemDamage.put(dotElement, tickDamage);
            totalElem = tickDamage;
            physDmg = 0f; // Elemental DOT, not physical

            if (resistPercent != 0) {
                elemResist = new EnumMap<>(ElementType.class);
                elemResist.put(dotElement, resistPercent);
            }
        }

        return new CombatSnapshot(
            System.currentTimeMillis(),
            attackerName,
            attackerType,
            attackerLevel,
            attackerClass,
            preDef, // baseDamage = pre-defense tick damage
            false, 1.0f, 0, preDef, // no crit, preDefense = base
            0, 0, 0, 0, physDmg, // no armor for DOT
            elemDamage, elemResist, totalElem,
            null, null,
            tickDamage,
            damageType,
            AttackType.UNKNOWN,
            false, false, false, false,
            0f, 0f,
            defenderMaxHealth,
            defenderHealthBefore,
            healthAfter,
            0
        );
    }

    /**
     * Creates a snapshot from a DamageBreakdown and attacker info.
     *
     * <p>This factory method bridges the RPGDamageCalculator output
     * (DamageBreakdown) with the death recap system (CombatSnapshot).
     */
    @Nonnull
    public static CombatSnapshot fromBreakdown(
        @Nonnull DamageBreakdown breakdown,
        @Nonnull String attackerName,
        @Nonnull String attackerType,
        int attackerLevel,
        @Nullable RPGMobClass attackerClass,
        float baseDamage,
        float defenderMaxHealth,
        float defenderHealthBefore,
        float defenderEvasion,
        float defenderArmor,
        float armorPenetration,
        @Nullable Map<ElementType, Float> defenderRawResistances,
        @Nullable Map<ElementType, Float> attackerElemPenetration
    ) {
        // Convert elemental damage map
        Map<ElementType, Float> elementalDamageMap = null;
        if (breakdown.hasElementalDamage()) {
            elementalDamageMap = new EnumMap<>(breakdown.elementalDamage());
        }

        // Convert resistance reductions map (effective resistance used in calculation)
        Map<ElementType, Float> resistMap = null;
        if (!breakdown.resistanceReductions().isEmpty()) {
            resistMap = new EnumMap<>(breakdown.resistanceReductions());
        }

        // Copy raw resistance map (for UI display)
        Map<ElementType, Float> rawResistCopy = null;
        if (defenderRawResistances != null && !defenderRawResistances.isEmpty()) {
            rawResistCopy = new EnumMap<>(defenderRawResistances);
        }

        // Copy penetration map (for UI display)
        Map<ElementType, Float> penCopy = null;
        if (attackerElemPenetration != null && !attackerElemPenetration.isEmpty()) {
            penCopy = new EnumMap<>(attackerElemPenetration);
        }

        // Calculate total elemental damage
        float totalElemental = breakdown.getTotalElementalDamage();

        // Use EXACT pre-defense damage captured during calculation
        float damageAfterAttacker = breakdown.preDefenseDamage();

        // Calculate effective armor after penetration (matches CombatCalculator formula with 50% floor)
        float clampedPen = Math.max(0f, Math.min(armorPenetration, 100f));
        float penFactor = clampedPen / 100.0f;
        float effectiveness = Math.max(0.5f, 1.0f - penFactor);
        float effectiveArmor = defenderArmor * effectiveness;

        // Calculate health after damage
        float defenderHealthAfter = Math.max(0f, defenderHealthBefore - breakdown.totalDamage());

        return new CombatSnapshot(
            System.currentTimeMillis(),
            attackerName,
            attackerType,
            attackerLevel,
            attackerClass,
            baseDamage,
            breakdown.wasCritical(),
            breakdown.critMultiplier(),
            breakdown.critTier(),
            damageAfterAttacker,
            defenderArmor,
            effectiveArmor,
            armorPenetration,
            breakdown.armorReduction(),
            breakdown.physicalDamage(),
            elementalDamageMap,
            resistMap,
            totalElemental,
            rawResistCopy,
            penCopy,
            breakdown.totalDamage(),
            breakdown.damageType(),
            breakdown.attackType(),
            breakdown.wasDodged(),
            breakdown.wasEvaded(),
            breakdown.wasBlocked(),
            breakdown.wasParried(),
            breakdown.shieldAbsorbed(),
            breakdown.trueDamage(),
            defenderMaxHealth,
            defenderHealthBefore,
            defenderHealthAfter,
            defenderEvasion
        );
    }

    public boolean hasElementalDamage() {
        return elementalDamage != null && !elementalDamage.isEmpty() && totalElementalDamage > 0;
    }

    /**
     * Formatted single-line summary for compact death recap display.
     */
    @Nonnull
    public String getCompactSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%.0f", finalDamage)).append(" dmg");

        if (wasCritical) {
            String tierLabel = critTier > 1 ? " T" + critTier : "";
            sb.append(" (CRIT").append(tierLabel).append(" !");
        } else {
            sb.append(" (");
        }

        sb.append(String.format("%.0f base", baseDamage));

        if (reductionPercent > 0) {
            sb.append(String.format(" -%.0f%% armor", reductionPercent));
        }

        if (totalElementalDamage > 0) {
            sb.append(String.format(" +%.0f elem", totalElementalDamage));
        }

        if (trueDamage > 0) {
            sb.append(String.format(" +%.0f true", trueDamage));
        }

        if (shieldAbsorbed > 0) {
            sb.append(String.format(" -%.0f shield", shieldAbsorbed));
        }

        if (wasBlocked) {
            sb.append(" BLOCKED");
        }

        if (wasParried) {
            sb.append(" PARRIED");
        }

        sb.append(")");
        return sb.toString();
    }

    /**
     * Maps a DamageType back to its ElementType for DOT elemental display.
     */
    @Nullable
    private static ElementType damageTypeToElement(@Nonnull DamageType type) {
        return switch (type) {
            case FIRE -> ElementType.FIRE;
            case WATER -> ElementType.WATER;
            case LIGHTNING -> ElementType.LIGHTNING;
            case EARTH -> ElementType.EARTH;
            case WIND -> ElementType.WIND;
            case VOID -> ElementType.VOID;
            case PHYSICAL, MAGIC -> null;
        };
    }
}
