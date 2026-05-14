package io.github.larsonix.trailoforbis.simulation.verify;

import io.github.larsonix.trailoforbis.elemental.ElementType;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.Map;

/**
 * Independent reference implementation of the damage pipeline formulas.
 *
 * <p>This class reimplements the key game formulas using simple arithmetic,
 * deliberately NOT calling any production code. It serves as an independent
 * oracle for verifying that the production pipeline produces correct numbers.
 *
 * <p>If the production code and this reference disagree, one of them has a bug.
 * Since this code is trivial arithmetic, it's easy to verify by hand.
 */
public final class ReferenceCalculator {

    private ReferenceCalculator() {}

    // ==================== Constants (from config.yml) ====================

    public static final float BASE_CRIT_CHANCE = 5.0f;
    public static final float BASE_CRIT_MULTIPLIER = 150.0f;
    public static final float BASE_ACCURACY = 10.0f;

    // Armor formula: reduction = armor / (armor + LEVEL_SCALE * level + BASE_CONSTANT)
    public static final float ARMOR_LEVEL_SCALE = 9.0f;
    public static final float ARMOR_BASE_CONSTANT = 50.0f;
    public static final float MAX_ARMOR_REDUCTION = 0.9f;

    // Resistance
    public static final float RESISTANCE_CAP = 75.0f;

    // Per-point attribute grants (from config.yml)
    public static final float FIRE_PHYS_DMG_PCT = 0.4f;
    public static final float FIRE_CRIT_MULT = 0.6f;
    public static final float FIRE_BURN_DMG_PCT = 0.4f;
    public static final float FIRE_IGNITE_CHANCE = 0.1f;
    public static final float FIRE_CHARGED_ATK_PCT = 0.3f;

    public static final float WATER_SPELL_DMG_PCT = 0.5f;
    public static final float WATER_MAX_MANA = 1.5f;
    public static final float WATER_ES_PCT = 0.5f;
    public static final float WATER_ES_REGEN = 0.2f;
    public static final float WATER_MANA_REGEN = 0.15f;
    public static final float WATER_FREEZE_CHANCE = 0.1f;

    public static final float LIGHTNING_ATK_SPD_PCT = 0.3f;
    public static final float LIGHTNING_MOVE_SPD_PCT = 0.15f;
    public static final float LIGHTNING_CRIT_CHANCE = 0.1f;
    public static final float LIGHTNING_STAM_REGEN = 0.1f;
    public static final float LIGHTNING_SHOCK_CHANCE = 0.1f;

    public static final float EARTH_MAX_HP_PCT = 0.5f;
    public static final float EARTH_ARMOR = 3.5f; // config/config.yml (deployed), NOT config/config/config.yml (5.0)
    public static final float EARTH_HP_REGEN = 0.2f;
    public static final float EARTH_BLOCK_CHANCE = 0.2f;
    public static final float EARTH_KB_RESIST = 0.3f;

    public static final float WIND_EVASION = 5.0f;
    public static final float WIND_ACCURACY = 3.0f;
    public static final float WIND_PROJ_DMG_PCT = 0.5f;
    public static final float WIND_JUMP_FORCE = 0.15f;
    public static final float WIND_PROJ_SPD_PCT = 0.3f;

    public static final float VOID_LIFE_STEAL = 0.2f;
    public static final float VOID_TRUE_DMG_PCT = 0.1f;
    public static final float VOID_DOT_DMG_PCT = 0.3f;
    public static final float VOID_MANA_ON_KILL = 0.5f;
    public static final float VOID_STATUS_DURATION = 0.3f;

    // ==================== Attribute → Stat Formulas ====================

    /** Expected physicalDamagePercent from fire attribute points. */
    public static float expectedPhysDmgPercent(int fire) {
        return fire * FIRE_PHYS_DMG_PCT;
    }

    /** Expected critical multiplier from fire attribute points (includes base 150%). */
    public static float expectedCritMultiplier(int fire) {
        return BASE_CRIT_MULTIPLIER + fire * FIRE_CRIT_MULT;
    }

    /** Expected critical chance from lightning attribute points (includes base 5%). */
    public static float expectedCritChance(int lightning) {
        return BASE_CRIT_CHANCE + lightning * LIGHTNING_CRIT_CHANCE;
    }

    /** Expected armor from earth attribute points (no equipment armor). */
    public static float expectedArmor(int earth) {
        return earth * EARTH_ARMOR;
    }

    /** Expected evasion from wind attribute points. */
    public static float expectedEvasion(int wind) {
        return wind * WIND_EVASION;
    }

    /** Expected accuracy from wind attribute points (includes base 10). */
    public static float expectedAccuracy(int wind) {
        return BASE_ACCURACY + wind * WIND_ACCURACY;
    }

    // ==================== Damage Pipeline ====================

    /**
     * Computes expected damage for a melee hit with an elemental weapon.
     *
     * <p>Implements the 10-step pipeline using simple arithmetic:
     * <ol>
     *   <li>Base damage → element slot</li>
     *   <li>+ flat physical from stats</li>
     *   <li>+ flat elemental from gear</li>
     *   <li>Conversion: physical → elemental (only affects physical slot)</li>
     *   <li>% increased: physical slot gets physDmg% + meleeDmg% + dmg%;
     *       element slot gets meleeDmg% + dmg%</li>
     *   <li>Per-element modifiers</li>
     *   <li>% more: allDmg% × dmgMult</li>
     *   <li>Conditional multiplier × attack type multiplier</li>
     *   <li>Crit multiplier (if crit)</li>
     *   <li>Defenses</li>
     * </ol>
     */
    public static DamageResult calculateMeleeDamage(MeleeInput input) {
        // Step 1: Base damage placement
        float physical = 0f;
        EnumMap<ElementType, Float> elemental = new EnumMap<>(ElementType.class);
        for (ElementType t : ElementType.values()) elemental.put(t, 0f);

        if (input.weaponElement != null) {
            elemental.put(input.weaponElement, input.baseDamage);
        } else {
            physical = input.baseDamage;
        }

        // Step 2: Flat physical
        physical += input.flatPhysical + input.flatMelee;

        // Step 3: Flat elemental
        for (ElementType t : ElementType.values()) {
            float flat = input.flatElemental.getOrDefault(t, 0f);
            if (flat > 0) elemental.merge(t, flat, Float::sum);
        }

        // Step 4: Conversion (only converts PHYSICAL slot)
        float totalConv = 0f;
        for (var entry : input.conversionPercent.entrySet()) {
            totalConv += entry.getValue();
        }
        float convScale = totalConv > 100f ? 100f / totalConv : 1f;
        float originalPhysical = physical;
        for (var entry : input.conversionPercent.entrySet()) {
            float effectivePct = entry.getValue() * convScale;
            float converted = originalPhysical * effectivePct / 100f;
            elemental.merge(entry.getKey(), converted, Float::sum);
            physical -= converted;
        }
        physical = Math.max(0f, physical);

        // Step 5: % Increased
        float physIncrease = input.physDmgPercent + input.meleeDmgPercent + input.dmgPercent;
        physical *= (1f + physIncrease / 100f);

        if (input.weaponElement != null) {
            float elemIncrease = input.meleeDmgPercent + input.dmgPercent;
            float current = elemental.get(input.weaponElement);
            elemental.put(input.weaponElement, current * (1f + elemIncrease / 100f));
        }

        // Step 6: Per-element modifiers
        for (ElementType t : ElementType.values()) {
            float current = elemental.getOrDefault(t, 0f);
            if (current <= 0) continue;
            float pctInc = input.elemPercentInc.getOrDefault(t, 0f);
            float pctMore = input.elemPercentMore.getOrDefault(t, 0f);
            current *= (1f + pctInc / 100f);
            current *= (1f + pctMore / 100f);
            elemental.put(t, current);
        }

        // Step 7: % More
        float moreMult = (1f + input.allDmgPercent / 100f) * (1f + input.dmgMultiplier / 100f);
        physical *= moreMult;
        for (ElementType t : ElementType.values()) {
            elemental.merge(t, 0f, (old, zero) -> old * moreMult);
        }

        // Step 8: Conditional × attack type
        float combinedMult = input.conditionalMultiplier * input.attackTypeMultiplier;
        physical *= combinedMult;
        for (ElementType t : ElementType.values()) {
            elemental.merge(t, 0f, (old, zero) -> old * combinedMult);
        }

        // Step 9: Crit
        if (input.forceCrit) {
            float critMult = input.critMultiplier / 100f;
            physical *= critMult;
            for (ElementType t : ElementType.values()) {
                elemental.merge(t, 0f, (old, zero) -> old * critMult);
            }
        }

        // Step 10: Defenses
        if (input.defenderArmor > 0 && physical > 0) {
            float reduction = calculateArmorReduction(input.defenderArmor, input.attackerLevel);
            physical *= (1f - reduction);
        }
        for (ElementType t : ElementType.values()) {
            float elemDmg = elemental.getOrDefault(t, 0f);
            if (elemDmg <= 0) continue;
            float resist = input.defenderResistance.getOrDefault(t, 0f);
            float effectiveResist = Math.max(-200f, Math.min(resist, RESISTANCE_CAP));
            elemental.put(t, elemDmg * (1f - effectiveResist / 100f));
        }

        // Step 11: True damage
        float trueDmg = input.trueDamage;
        if (input.pctHitAsTrueDmg > 0) {
            float postDefense = physical;
            for (float v : elemental.values()) postDefense += v;
            trueDmg += postDefense * input.pctHitAsTrueDmg / 100f;
        }

        return new DamageResult(physical, elemental, trueDmg);
    }

    // ==================== Defense Formulas ====================

    /** Armor reduction percentage (0.0 to MAX_ARMOR_REDUCTION). */
    public static float calculateArmorReduction(float armor, int attackerLevel) {
        float denominator = armor + ARMOR_LEVEL_SCALE * attackerLevel + ARMOR_BASE_CONSTANT;
        float reduction = armor / denominator;
        return Math.min(reduction, MAX_ARMOR_REDUCTION);
    }

    /** Effective resistance after penetration, then cap. Pen before cap, floor at -200%. */
    public static float effectiveResistance(float resistance, float penetration) {
        float afterPen = resistance - penetration;
        return Math.max(-200f, Math.min(afterPen, RESISTANCE_CAP));
    }

    // ==================== Data Classes ====================

    /** Input for a melee damage calculation. All fields default to 0/null (no-op). */
    public static class MeleeInput {
        public float baseDamage;
        public @Nullable ElementType weaponElement;

        // Flat additions
        public float flatPhysical;
        public float flatMelee;
        public Map<ElementType, Float> flatElemental = new EnumMap<>(ElementType.class);

        // Conversion
        public Map<ElementType, Float> conversionPercent = new EnumMap<>(ElementType.class);

        // % Increased
        public float physDmgPercent;
        public float meleeDmgPercent;
        public float dmgPercent;

        // Per-element modifiers
        public Map<ElementType, Float> elemPercentInc = new EnumMap<>(ElementType.class);
        public Map<ElementType, Float> elemPercentMore = new EnumMap<>(ElementType.class);

        // % More
        public float allDmgPercent;
        public float dmgMultiplier;

        // Multipliers
        public float conditionalMultiplier = 1.0f;
        public float attackTypeMultiplier = 1.0f;
        public boolean forceCrit;
        public float critMultiplier = BASE_CRIT_MULTIPLIER;

        // Defenses
        public float defenderArmor;
        public int attackerLevel = 1;
        public Map<ElementType, Float> defenderResistance = new EnumMap<>(ElementType.class);

        // True damage
        public float trueDamage;
        public float pctHitAsTrueDmg;
    }

    /** Result of a reference damage calculation. */
    public record DamageResult(
        float physical,
        EnumMap<ElementType, Float> elemental,
        float trueDamage
    ) {
        public float getElemental(ElementType type) {
            return elemental.getOrDefault(type, 0f);
        }

        public float total() {
            float sum = physical + trueDamage;
            for (float v : elemental.values()) sum += v;
            return sum;
        }
    }
}
