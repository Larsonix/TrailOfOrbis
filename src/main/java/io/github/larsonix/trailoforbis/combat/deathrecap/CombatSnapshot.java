package io.github.larsonix.trailoforbis.combat.deathrecap;

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
 *   <li>Defender stats at time of hit</li>
 * </ul>
 *
 * <h3>Field Accuracy Annotations</h3>
 * <p>Each field is annotated with its accuracy level for debugging purposes:
 * <ul>
 *   <li><b>[EXACT]</b> - Value comes directly from the damage calculation system and is accurate</li>
 *   <li><b>[APPROX]</b> - Value is reverse-calculated and may differ from actual due to non-linear formulas</li>
 *   <li><b>[NOT_TRACKED]</b> - Value is always 0 because DamageBreakdown doesn't capture it</li>
 * </ul>
 *
 * <p>For debugging, rely on [EXACT] fields. The [APPROX] and [NOT_TRACKED] fields are
 * provided for display purposes but should not be used for precise calculations.
 *
 * @param timestamp Time the damage occurred
 * @param attackerName Display name of the attacker (player username or formatted mob name)
 * @param attackerType Type of attacker: "player", "mob", or "environment"
 * @param attackerLevel Attacker's level (player level or mob level), 0 for environment
 * @param attackerClass Mob classification (MONSTER, ELITE, LEGENDARY, MYTHIC), null for players/environment
 * @param baseDamage Original base damage from weapon/attack [EXACT]
 * @param flatBonus Flat damage bonus from attacker stats [NOT_TRACKED - always 0]
 * @param percentBonus Percentage damage bonus from attacker stats [NOT_TRACKED - always 0]
 * @param wasCritical Whether this was a critical hit [EXACT]
 * @param critMultiplier Critical multiplier applied (1.0 if not crit, e.g., 2.5 for 250% crit) [EXACT]
 * @param damageAfterAttackerBonuses Damage after attacker bonuses, before armor [EXACT - from DamageBreakdown.preDefenseDamage()]
 * @param defenderArmor Defender's total armor value [EXACT - from ComputedStats]
 * @param effectiveArmor Effective armor after penetration [EXACT - calculated with 50% min effectiveness floor]
 * @param armorPenetration Attacker's armor penetration percentage [EXACT - from ComputedStats]
 * @param reductionPercent Percentage of damage reduced by armor (0-90) [EXACT - from DamageBreakdown.armorReduction()]
 * @param damageAfterArmor Physical damage after armor reduction [EXACT - from DamageBreakdown.physicalDamage()]
 * @param elementalDamage Final elemental damage per element type after resistance [EXACT - from DamageBreakdown]
 * @param elementalResist Effective resistance used in calculation per element [EXACT - from DamageBreakdown.resistanceReductions()]
 * @param totalElementalDamage Total elemental damage added [EXACT - sum of elementalDamage values]
 * @param defenderRawResistances Defender's raw resistance values before penetration [EXACT - from ElementalStats]
 * @param attackerPenetration Attacker's penetration values per element [EXACT - from ElementalStats]
 * @param finalDamage Final damage dealt after all calculations [EXACT - from DamageBreakdown.totalDamage()]
 * @param damageType Type of damage (PHYSICAL, MAGIC) [EXACT - from DamageBreakdown.damageType()]
 * @param wasDodged Whether the attack was dodged (if true, finalDamage should be 0) [EXACT]
 * @param defenderMaxHealth Defender's max health at time of hit [EXACT - from EntityStatValue.getMax()]
 * @param defenderHealthBefore Defender's health before this damage [EXACT - from EntityStatValue.get()]
 * @param defenderHealthAfter Defender's health after this damage [EXACT - calculated as healthBefore - totalDamage]
 * @param defenderEvasion Defender's evasion rating (for display purposes) [EXACT - from ComputedStats]
 */
public record CombatSnapshot(
    long timestamp,
    @Nonnull String attackerName,
    @Nonnull String attackerType,
    int attackerLevel,
    @Nullable RPGMobClass attackerClass,
    float baseDamage,
    float flatBonus,
    float percentBonus,
    boolean wasCritical,
    float critMultiplier,
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
    boolean wasDodged,
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
            0, 0, false, 1.0f, damage,
            0, 0, 0, 0, damage,
            null, null, 0,
            null, null,  // No raw resistances/penetration for environment damage
            damage,
            damageType,
            false,
            defenderMaxHealth,
            defenderHealthBefore,
            healthAfter,
            0
        );
    }

    /**
     * Creates a snapshot from a DamageBreakdown and attacker info.
     *
     * <p>This factory method bridges the new RPGDamageCalculator output
     * (DamageBreakdown) with the death recap system (CombatSnapshot).
     *
     * @param breakdown The damage calculation result
     * @param attackerName Display name of the attacker
     * @param attackerType Type of attacker: "player", "mob", or "environment"
     * @param attackerLevel Attacker's level
     * @param attackerClass Mob classification (null for players/environment)
     * @param baseDamage Original base damage before calculations
     * @param defenderMaxHealth Defender's max health at time of hit
     * @param defenderHealthBefore Defender's health before this damage
     * @param defenderEvasion Defender's evasion rating
     * @param defenderArmor Defender's total armor value
     * @param armorPenetration Attacker's armor penetration percentage
     * @param defenderRawResistances Defender's raw elemental resistances (before penetration)
     * @param attackerElemPenetration Attacker's elemental penetration values
     * @return A new CombatSnapshot for death recap display
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

        // Use EXACT pre-defense damage captured during calculation (no reverse-calculation needed!)
        // This value was stored in DamageBreakdown right before defenses were applied.
        float damageAfterAttacker = breakdown.preDefenseDamage();

        // Calculate effective armor after penetration
        // IMPORTANT: Must match CombatCalculator formula with 50% floor!
        // Formula: effectiveness = max(0.5, 1 - pen%), then effectiveArmor = armor * effectiveness
        float clampedPen = Math.max(0f, Math.min(armorPenetration, 100f));
        float penFactor = clampedPen / 100.0f;
        float effectiveness = Math.max(0.5f, 1.0f - penFactor);  // 50% min effectiveness floor
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
            0f,  // flatBonus - not tracked in breakdown
            0f,  // percentBonus - not tracked in breakdown
            breakdown.wasCritical(),
            breakdown.critMultiplier(),
            damageAfterAttacker,
            defenderArmor,
            effectiveArmor,
            armorPenetration,
            breakdown.armorReduction(),
            breakdown.physicalDamage(),  // damageAfterArmor
            elementalDamageMap,
            resistMap,
            totalElemental,
            rawResistCopy,
            penCopy,
            breakdown.totalDamage(),
            breakdown.damageType(),
            breakdown.wasDodged(),
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
            sb.append(" (CRIT !");
        } else {
            sb.append(" (");
        }

        sb.append(String.format("%.0f base", baseDamage));

        if (flatBonus > 0) {
            sb.append(String.format(" +%.0f flat", flatBonus));
        }

        if (percentBonus > 0) {
            sb.append(String.format(" +%.0f%%", percentBonus));
        }

        if (reductionPercent > 0) {
            sb.append(String.format(" -%.0f%% armor", reductionPercent));
        }

        if (totalElementalDamage > 0) {
            sb.append(String.format(" +%.0f elem", totalElementalDamage));
        }

        sb.append(")");
        return sb.toString();
    }
}
