package io.github.larsonix.trailoforbis.skilltree.calculation;

import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.skilltree.model.ModifierType;
import io.github.larsonix.trailoforbis.skilltree.model.StatModifier;
import io.github.larsonix.trailoforbis.skilltree.model.StatType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for StatsCombiner.
 *
 * <p>Tests the ARPG deposit model where StatsCombiner deposits into accumulators,
 * and the full formula is applied later:
 *
 * <pre>
 * Final = (Base + ALL_FLAT) × (1 + ALL_PERCENT/100) × Π(1 + MORE/100)
 * </pre>
 *
 * <p>Three stat categories:
 * <ol>
 *   <li>Base stats with percent pair: FLAT adds to base, PERCENT routes to accumulator</li>
 *   <li>Accumulator stats (_PERCENT): all modifier types add to the pool</li>
 *   <li>Standalone stats: all modifier types add directly</li>
 * </ol>
 */
public class StatsCombinerTest {

    private StatsCombiner combiner;

    @BeforeEach
    void setUp() {
        combiner = new StatsCombiner();
    }

    // ==================== Deposit Model: Base Stats ====================

    @Nested
    @DisplayName("Base Stats with Percent Pair (deposit model)")
    class BaseStatTests {

        @Test
        @DisplayName("No modifiers returns base stats unchanged")
        void combine_noModifiers_returnsBase() {
            ComputedStats base = ComputedStats.builder()
                .maxHealth(100f)
                .physicalDamage(50f)
                .build();

            AggregatedModifiers emptyMods = AggregatedModifiers.builder().build();
            ComputedStats result = combiner.combine(base, emptyMods);

            assertEquals(100f, result.getMaxHealth(), 0.1f);
            assertEquals(50f, result.getPhysicalDamage(), 0.1f);
        }

        @Test
        @DisplayName("FLAT on MAX_HEALTH adds to base: 100 + 20 = 120")
        void combine_flatModifier_addsToBase() {
            ComputedStats base = ComputedStats.builder()
                .maxHealth(100f)
                .build();

            AggregatedModifiers mods = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.MAX_HEALTH, 20f, ModifierType.FLAT))
                .build();

            ComputedStats result = combiner.combine(base, mods);

            assertEquals(120f, result.getMaxHealth(), 0.1f, "100 + 20 flat = 120");
        }

        @Test
        @DisplayName("Multiple FLAT stack additively: 100 + 10 + 15 = 125")
        void combine_multipleFlatModifiers_stackAdditively() {
            ComputedStats base = ComputedStats.builder()
                .physicalDamage(100f)
                .build();

            AggregatedModifiers mods = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.PHYSICAL_DAMAGE, 10f, ModifierType.FLAT))
                .addModifier(new StatModifier(StatType.PHYSICAL_DAMAGE, 15f, ModifierType.FLAT))
                .build();

            ComputedStats result = combiner.combine(base, mods);

            assertEquals(125f, result.getPhysicalDamage(), 0.1f);
        }

        @Test
        @DisplayName("PERCENT on MAX_HEALTH routes to maxHealthPercent accumulator")
        void combine_percentModifier_routesToAccumulator() {
            ComputedStats base = ComputedStats.builder()
                .maxHealth(100f)
                .maxHealthPercent(10f)  // 10% from attributes
                .build();

            AggregatedModifiers mods = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.MAX_HEALTH, 50f, ModifierType.PERCENT))
                .build();

            ComputedStats result = combiner.combine(base, mods);

            // PERCENT routes to maxHealthPercent, doesn't multiply maxHealth
            assertEquals(100f, result.getMaxHealth(), 0.1f,
                "maxHealth unchanged — PERCENT deposited to accumulator");
            assertEquals(60f, result.getMaxHealthPercent(), 0.1f,
                "10% base + 50% routed from MAX_HEALTH = 60%");
        }

        @Test
        @DisplayName("PERCENT on PHYSICAL_DAMAGE routes to physicalDamagePercent")
        void combine_percentOnDamage_routesToAccumulator() {
            ComputedStats base = ComputedStats.builder()
                .physicalDamage(100f)
                .physicalDamagePercent(5f)  // 5% from attributes
                .build();

            AggregatedModifiers mods = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.PHYSICAL_DAMAGE, 30f, ModifierType.PERCENT))
                .addModifier(new StatModifier(StatType.PHYSICAL_DAMAGE, 20f, ModifierType.PERCENT))
                .build();

            ComputedStats result = combiner.combine(base, mods);

            assertEquals(100f, result.getPhysicalDamage(), 0.1f,
                "physicalDamage unchanged");
            assertEquals(55f, result.getPhysicalDamagePercent(), 0.1f,
                "5% + 30% + 20% = 55%");
        }

        @Test
        @DisplayName("FLAT + PERCENT on ARMOR: flat adds to base, percent to armorPercent")
        void combine_armorFlatAndPercent() {
            ComputedStats base = ComputedStats.builder()
                .armor(100f)
                .armorPercent(0f)
                .build();

            AggregatedModifiers mods = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.ARMOR, 50f, ModifierType.FLAT))
                .addModifier(new StatModifier(StatType.ARMOR, 25f, ModifierType.PERCENT))
                .build();

            ComputedStats result = combiner.combine(base, mods);

            assertEquals(150f, result.getArmor(), 0.1f, "100 + 50 flat = 150");
            assertEquals(25f, result.getArmorPercent(), 0.1f, "25% routed to armorPercent");
        }

        @Test
        @DisplayName("Negative flat modifier reduces stat")
        void combine_negativeFlatModifier_reduces() {
            ComputedStats base = ComputedStats.builder()
                .maxHealth(100f)
                .build();

            AggregatedModifiers mods = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.MAX_HEALTH, -20f, ModifierType.FLAT))
                .build();

            ComputedStats result = combiner.combine(base, mods);

            assertEquals(80f, result.getMaxHealth(), 0.1f, "100 - 20 = 80");
        }

        @Test
        @DisplayName("Negative PERCENT routes to accumulator as negative")
        void combine_negativePercent_routesToAccumulator() {
            ComputedStats base = ComputedStats.builder()
                .maxHealth(100f)
                .maxHealthPercent(50f)
                .build();

            AggregatedModifiers mods = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.MAX_HEALTH, -25f, ModifierType.PERCENT))
                .build();

            ComputedStats result = combiner.combine(base, mods);

            assertEquals(100f, result.getMaxHealth(), 0.1f);
            assertEquals(25f, result.getMaxHealthPercent(), 0.1f,
                "50% - 25% routed = 25%");
        }

        @Test
        @DisplayName("Large values don't overflow")
        void combine_largeValues_noOverflow() {
            ComputedStats base = ComputedStats.builder()
                .maxHealth(1000000f)
                .build();

            AggregatedModifiers mods = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.MAX_HEALTH, 1000000f, ModifierType.FLAT))
                .build();

            ComputedStats result = combiner.combine(base, mods);

            assertEquals(2000000f, result.getMaxHealth(), 1f);
        }
    }

    // ==================== Deposit Model: Accumulator Stats ====================

    @Nested
    @DisplayName("Accumulator Stats (_PERCENT fields)")
    class AccumulatorTests {

        @Test
        @DisplayName("FLAT on MAX_HEALTH_PERCENT adds percentage points")
        void accumulator_flat_addsDirectly() {
            ComputedStats base = ComputedStats.builder()
                .maxHealthPercent(10f)
                .build();

            AggregatedModifiers mods = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.MAX_HEALTH_PERCENT, 25f, ModifierType.FLAT))
                .build();

            ComputedStats result = combiner.combine(base, mods);

            assertEquals(35f, result.getMaxHealthPercent(), 0.1f, "10 + 25 = 35%");
        }

        @Test
        @DisplayName("PERCENT on MAX_HEALTH_PERCENT adds (not multiplies) — all types additive")
        void accumulator_percent_addsNotMultiplies() {
            ComputedStats base = ComputedStats.builder()
                .maxHealthPercent(10f)
                .build();

            AggregatedModifiers mods = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.MAX_HEALTH_PERCENT, 5f, ModifierType.PERCENT))
                .addModifier(new StatModifier(StatType.MAX_HEALTH_PERCENT, 5f, ModifierType.PERCENT))
                .addModifier(new StatModifier(StatType.MAX_HEALTH_PERCENT, 5f, ModifierType.PERCENT))
                .build();

            ComputedStats result = combiner.combine(base, mods);

            // All types additive for accumulators: 10 + 5 + 5 + 5 = 25
            assertEquals(25f, result.getMaxHealthPercent(), 0.1f,
                "10 + 5 + 5 + 5 = 25 (not 10 × 1.15)");
        }

        @Test
        @DisplayName("Zero base + PERCENT on accumulator still works (not percent-of-percent)")
        void accumulator_zeroBase_percentStillAdds() {
            ComputedStats base = ComputedStats.builder()
                .maxHealthPercent(0f)  // 0 Earth attribute
                .build();

            AggregatedModifiers mods = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.MAX_HEALTH_PERCENT, 50f, ModifierType.PERCENT))
                .build();

            ComputedStats result = combiner.combine(base, mods);

            // With old PoE: (0 + 0) × (1 + 50/100) = 0 — EVERYTHING LOST
            // With deposit:  0 + 50 = 50 — correct!
            assertEquals(50f, result.getMaxHealthPercent(), 0.1f,
                "50% bonus should NOT be lost when base is 0");
        }

        @Test
        @DisplayName("Pending percent from base stat routing adds to accumulator")
        void accumulator_includesPendingFromBaseStats() {
            ComputedStats base = ComputedStats.builder()
                .maxHealth(100f)
                .maxHealthPercent(10f)  // from Earth attr
                .build();

            AggregatedModifiers mods = AggregatedModifiers.builder()
                // PERCENT on MAX_HEALTH → routes to MAX_HEALTH_PERCENT as pending
                .addModifier(new StatModifier(StatType.MAX_HEALTH, 30f, ModifierType.PERCENT))
                // Direct PERCENT on MAX_HEALTH_PERCENT → adds directly
                .addModifier(new StatModifier(StatType.MAX_HEALTH_PERCENT, 20f, ModifierType.PERCENT))
                .build();

            ComputedStats result = combiner.combine(base, mods);

            // 10 (earth) + 30 (routed from MAX_HEALTH PERCENT) + 20 (direct) = 60
            assertEquals(60f, result.getMaxHealthPercent(), 0.1f,
                "10 + 30 routed + 20 direct = 60%");
            assertEquals(100f, result.getMaxHealth(), 0.1f,
                "maxHealth unchanged (no FLAT mods)");
        }
    }

    // ==================== Deposit Model: Standalone Stats ====================

    @Nested
    @DisplayName("Standalone Stats (all types additive)")
    class StandaloneTests {

        @Test
        @DisplayName("FLAT on CRITICAL_CHANCE adds percentage points")
        void standalone_flat_adds() {
            ComputedStats base = ComputedStats.builder()
                .criticalChance(5f)
                .build();

            AggregatedModifiers mods = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.CRITICAL_CHANCE, 2f, ModifierType.FLAT))
                .addModifier(new StatModifier(StatType.CRITICAL_CHANCE, 3f, ModifierType.FLAT))
                .build();

            ComputedStats result = combiner.combine(base, mods);

            assertEquals(10f, result.getCriticalChance(), 0.1f, "5 + 2 + 3 = 10");
        }

        @Test
        @DisplayName("PERCENT on standalone adds (not multiplies)")
        void standalone_percent_adds() {
            ComputedStats base = ComputedStats.builder()
                .knockbackResistance(10f)
                .build();

            AggregatedModifiers mods = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.KNOCKBACK_RESISTANCE, 15f, ModifierType.FLAT))
                .addModifier(new StatModifier(StatType.KNOCKBACK_RESISTANCE, 50f, ModifierType.PERCENT))
                .build();

            ComputedStats result = combiner.combine(base, mods);

            // Standalone: all types additive: 10 + 15 + 50 = 75
            assertEquals(75f, result.getKnockbackResistance(), 0.1f,
                "10 + 15 + 50 = 75 (all additive for standalone)");
        }

        @Test
        @DisplayName("MULTIPLIER on standalone adds (not multiplies)")
        void standalone_multiplier_adds() {
            ComputedStats base = ComputedStats.builder()
                .blockCounterDamage(10f)
                .build();

            AggregatedModifiers mods = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.BLOCK_COUNTER_DAMAGE, 5f, ModifierType.FLAT))
                .addModifier(new StatModifier(StatType.BLOCK_COUNTER_DAMAGE, 100f, ModifierType.PERCENT))
                .addModifier(new StatModifier(StatType.BLOCK_COUNTER_DAMAGE, 50f, ModifierType.MULTIPLIER))
                .build();

            ComputedStats result = combiner.combine(base, mods);

            // Standalone: all additive: 10 + 5 + 100 + 50 = 165
            assertEquals(165f, result.getBlockCounterDamage(), 0.1f,
                "10 + 5 + 100 + 50 = 165");
        }
    }

    // ==================== Full Pipeline Examples ====================

    @Nested
    @DisplayName("Full Pipeline (combiner + consolidation)")
    class FullPipelineTests {

        @Test
        @DisplayName("User scenario: 100 base + 50 flat + 100% = 300 HP after consolidation")
        void fullPipeline_userScenario_correctHP() {
            ComputedStats base = ComputedStats.builder()
                .maxHealth(100f)
                .maxHealthPercent(10f)  // 10% from Earth attribute
                .build();

            AggregatedModifiers mods = AggregatedModifiers.builder()
                // +50 flat HP from skill tree
                .addModifier(new StatModifier(StatType.MAX_HEALTH, 50f, ModifierType.FLAT))
                // +90% HP from skill tree (routes to maxHealthPercent)
                .addModifier(new StatModifier(StatType.MAX_HEALTH_PERCENT, 50f, ModifierType.PERCENT))
                .addModifier(new StatModifier(StatType.MAX_HEALTH_PERCENT, 40f, ModifierType.PERCENT))
                .build();

            ComputedStats result = combiner.combine(base, mods);

            // After combiner: maxHealth=150, maxHealthPercent=100 (10+50+40)
            assertEquals(150f, result.getMaxHealth(), 0.1f);
            assertEquals(100f, result.getMaxHealthPercent(), 0.1f);

            // After consolidation: 150 × (1 + 100/100) = 300
            result.consolidateResourcePercents();
            assertEquals(300f, result.getMaxHealth(), 0.1f,
                "150 × (1 + 100/100) = 300 HP");
        }

        @Test
        @DisplayName("Damage stat: combiner deposits, damage calc applies formula")
        void fullPipeline_damageDeposits() {
            ComputedStats base = ComputedStats.builder()
                .physicalDamage(0f)
                .physicalDamagePercent(5f)  // from Fire attribute
                .build();

            AggregatedModifiers mods = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.PHYSICAL_DAMAGE, 20f, ModifierType.FLAT))
                .addModifier(new StatModifier(StatType.PHYSICAL_DAMAGE, 50f, ModifierType.PERCENT))
                .addModifier(new StatModifier(StatType.PHYSICAL_DAMAGE, 30f, ModifierType.PERCENT))
                .build();

            ComputedStats result = combiner.combine(base, mods);

            // physicalDamage = 0 + 20 flat = 20
            assertEquals(20f, result.getPhysicalDamage(), 0.1f);
            // physicalDamagePercent = 5 + 50 + 30 = 85
            assertEquals(85f, result.getPhysicalDamagePercent(), 0.1f,
                "5% base + 50% + 30% routed = 85%");
            // Damage calculator would then apply: (weapon + 20) × (1 + 85/100) at combat time
        }
    }

    // ==================== Special Stat Handling Tests ====================

    @Nested
    @DisplayName("Special Stat Handling")
    class SpecialStatTests {

        @Test
        @DisplayName("EVASION_PERCENT folds into evasion")
        void combine_evasionPercent_foldsIntoEvasion() {
            ComputedStats base = ComputedStats.builder()
                .evasion(100f)
                .build();

            AggregatedModifiers mods = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.EVASION_PERCENT, 50f, ModifierType.FLAT))
                .build();

            ComputedStats result = combiner.combine(base, mods);

            assertEquals(150f, result.getEvasion(), 0.1f, "100 × (1 + 50/100) = 150");
        }

        @Test
        @DisplayName("ELEMENTAL_RESISTANCE distributes to all elements")
        void combine_elementalResistance_distributesToAll() {
            ComputedStats base = ComputedStats.builder()
                .fireResistance(10f)
                .waterResistance(10f)
                .lightningResistance(10f)
                .earthResistance(10f)
                .windResistance(10f)
                .voidResistance(0f)
                .build();

            AggregatedModifiers mods = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.ELEMENTAL_RESISTANCE, 15f, ModifierType.FLAT))
                .build();

            ComputedStats result = combiner.combine(base, mods);

            assertEquals(25f, result.getFireResistance(), 0.1f);
            assertEquals(25f, result.getWaterResistance(), 0.1f);
            assertEquals(25f, result.getLightningResistance(), 0.1f);
            assertEquals(25f, result.getEarthResistance(), 0.1f);
            assertEquals(25f, result.getWindResistance(), 0.1f);
            assertEquals(15f, result.getVoidResistance(), 0.1f);
        }

        @Test
        @DisplayName("ALL_ELEMENTAL_DAMAGE_PERCENT distributes to all elements")
        void combine_allElementalDamagePercent_distributesToAll() {
            ComputedStats base = ComputedStats.builder()
                .fireDamagePercent(10f)
                .waterDamagePercent(20f)
                .lightningDamagePercent(0f)
                .earthDamagePercent(15f)
                .windDamagePercent(8f)
                .voidDamagePercent(5f)
                .build();

            AggregatedModifiers mods = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.ALL_ELEMENTAL_DAMAGE_PERCENT, 25f, ModifierType.FLAT))
                .build();

            ComputedStats result = combiner.combine(base, mods);

            assertEquals(35f, result.getFireDamagePercent(), 0.1f);
            assertEquals(45f, result.getWaterDamagePercent(), 0.1f);
            assertEquals(25f, result.getLightningDamagePercent(), 0.1f);
            assertEquals(40f, result.getEarthDamagePercent(), 0.1f);
            assertEquals(33f, result.getWindDamagePercent(), 0.1f);
            assertEquals(30f, result.getVoidDamagePercent(), 0.1f);
        }
    }

    // ==================== Multiple Stats Tests ====================

    @Nested
    @DisplayName("Multiple Stats Combined")
    class MultipleStatsTests {

        @Test
        @DisplayName("Different stats are modified independently")
        void combine_differentStats_independentModification() {
            ComputedStats base = ComputedStats.builder()
                .maxHealth(100f)
                .maxMana(50f)
                .physicalDamage(10f)
                .build();

            AggregatedModifiers mods = AggregatedModifiers.builder()
                // PERCENT on MAX_HEALTH → routes to maxHealthPercent
                .addModifier(new StatModifier(StatType.MAX_HEALTH, 50f, ModifierType.PERCENT))
                .addModifier(new StatModifier(StatType.MAX_MANA, 20f, ModifierType.FLAT))
                .addModifier(new StatModifier(StatType.PHYSICAL_DAMAGE, 5f, ModifierType.FLAT))
                .build();

            ComputedStats result = combiner.combine(base, mods);

            assertEquals(100f, result.getMaxHealth(), 0.1f, "maxHealth unchanged (PERCENT routed)");
            assertEquals(50f, result.getMaxHealthPercent(), 0.1f, "50% routed from MAX_HEALTH");
            assertEquals(70f, result.getMaxMana(), 0.1f, "50 + 20 = 70");
            assertEquals(15f, result.getPhysicalDamage(), 0.1f, "10 + 5 = 15");
        }

        @Test
        @DisplayName("Modifiers don't affect unrelated stats")
        void combine_modifiersDoNotAffectUnrelatedStats() {
            ComputedStats base = ComputedStats.builder()
                .maxHealth(100f)
                .maxMana(50f)
                .armor(75f)
                .build();

            AggregatedModifiers mods = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.MAX_HEALTH, 100f, ModifierType.FLAT))
                .build();

            ComputedStats result = combiner.combine(base, mods);

            assertEquals(200f, result.getMaxHealth(), 0.1f);
            assertEquals(50f, result.getMaxMana(), 0.1f);
            assertEquals(75f, result.getArmor(), 0.1f);
        }
    }

    // ==================== Octant Keystone Stats Tests ====================

    @Nested
    @DisplayName("Octant Keystone Stats")
    class OctantKeystoneStats {

        @Test
        @DisplayName("HP_SCALING_DAMAGE: bonus physDmg% = (maxHP/100) × stat")
        void hpScalingDamage_addsPhysDmgPercentFromMaxHp() {
            ComputedStats base = ComputedStats.builder()
                .maxHealth(500f)
                .physicalDamagePercent(10f)
                .build();

            AggregatedModifiers mods = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.HP_SCALING_DAMAGE, 0.5f, ModifierType.FLAT))
                .build();

            ComputedStats result = combiner.combine(base, mods);

            assertEquals(12.5f, result.getPhysicalDamagePercent(), 0.1f,
                "bonus = (500/100) × 0.5 = 2.5 → 10 + 2.5 = 12.5");
        }

        @Test
        @DisplayName("SPEED_TO_SPELL_POWER: bonus spellDmg% = moveSpeed% × (stat/100)")
        void speedToSpellPower_convertsMoveSpeedToSpellDmg() {
            ComputedStats base = ComputedStats.builder()
                .movementSpeedPercent(30f)
                .spellDamagePercent(10f)
                .build();

            AggregatedModifiers mods = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.SPEED_TO_SPELL_POWER, 50f, ModifierType.FLAT))
                .build();

            ComputedStats result = combiner.combine(base, mods);

            assertEquals(25f, result.getSpellDamagePercent(), 0.1f,
                "30 × (50/100) = 15 → 10 + 15 = 25");
        }

        @Test
        @DisplayName("ATK_SPEED_TO_SPELL_POWER: bonus spellDmg% = atkSpeed% × (stat/100)")
        void atkSpeedToSpellPower_convertsAtkSpeedToSpellDmg() {
            ComputedStats base = ComputedStats.builder()
                .attackSpeedPercent(20f)
                .spellDamagePercent(5f)
                .build();

            AggregatedModifiers mods = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.ATK_SPEED_TO_SPELL_POWER, 75f, ModifierType.FLAT))
                .build();

            ComputedStats result = combiner.combine(base, mods);

            assertEquals(20f, result.getSpellDamagePercent(), 0.1f,
                "20 × (75/100) = 15 → 5 + 15 = 20");
        }

        @Test
        @DisplayName("Tier 2-3 stats pass through combine correctly")
        void tier2And3Stats_passThrough() {
            ComputedStats base = ComputedStats.builder().build();

            AggregatedModifiers mods = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.DETONATE_DOT_ON_CRIT, 25f, ModifierType.FLAT))
                .addModifier(new StatModifier(StatType.CONSECUTIVE_HIT_BONUS, 5f, ModifierType.FLAT))
                .addModifier(new StatModifier(StatType.BLOCK_COUNTER_DAMAGE, 30f, ModifierType.FLAT))
                .addModifier(new StatModifier(StatType.SHIELD_REGEN_ON_DOT, 10f, ModifierType.FLAT))
                .addModifier(new StatModifier(StatType.IMMUNITY_ON_AILMENT, 1f, ModifierType.FLAT))
                .addModifier(new StatModifier(StatType.SPELL_ECHO_CHANCE, 15f, ModifierType.FLAT))
                .build();

            ComputedStats result = combiner.combine(base, mods);

            assertEquals(25f, result.getDetonateDotOnCrit(), 0.1f);
            assertEquals(5f, result.getConsecutiveHitBonus(), 0.1f);
            assertEquals(30f, result.getBlockCounterDamage(), 0.1f);
            assertEquals(10f, result.getShieldRegenOnDot(), 0.1f);
            assertEquals(1f, result.getImmunityOnAilment(), 0.1f);
            assertEquals(15f, result.getSpellEchoChance(), 0.1f);
        }

        @Test
        @DisplayName("EVASION_TO_ARMOR: bonus armor = evasion × (stat/100)")
        void evasionToArmor_convertsEvasionToArmor() {
            ComputedStats base = ComputedStats.builder()
                .evasion(200f)
                .armor(50f)
                .build();

            AggregatedModifiers mods = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.EVASION_TO_ARMOR, 30f, ModifierType.FLAT))
                .build();

            ComputedStats result = combiner.combine(base, mods);

            assertEquals(110f, result.getArmor(), 0.1f, "50 + 200×0.3 = 110");
            assertEquals(200f, result.getEvasion(), 0.1f);
        }

        @Test
        @DisplayName("EVASION_TO_ARMOR: uses post-processed evasion value")
        void evasionToArmor_usesPostProcessedEvasion() {
            ComputedStats base = ComputedStats.builder()
                .evasion(100f)
                .armor(0f)
                .build();

            AggregatedModifiers mods = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.EVASION, 100f, ModifierType.FLAT))
                .addModifier(new StatModifier(StatType.EVASION_TO_ARMOR, 50f, ModifierType.FLAT))
                .build();

            ComputedStats result = combiner.combine(base, mods);

            assertEquals(200f, result.getEvasion(), 0.1f);
            assertEquals(100f, result.getArmor(), 0.1f, "200 × 0.5 = 100");
        }

        @Test
        @DisplayName("Derived stats use post-processed maxHP, not base")
        void derivedStats_usePostProcessedValues() {
            ComputedStats base = ComputedStats.builder()
                .maxHealth(100f)
                .physicalDamagePercent(0f)
                .build();

            AggregatedModifiers mods = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.MAX_HEALTH, 400f, ModifierType.FLAT))
                .addModifier(new StatModifier(StatType.HP_SCALING_DAMAGE, 1.0f, ModifierType.FLAT))
                .build();

            ComputedStats result = combiner.combine(base, mods);

            assertEquals(500f, result.getMaxHealth(), 0.1f);
            assertEquals(5.0f, result.getPhysicalDamagePercent(), 0.1f,
                "(500/100) × 1.0 = 5.0 (uses post-processed maxHP)");
        }
    }

    // ==================== Edge Cases ====================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Zero base with flat modifier works")
        void combine_zeroBaseWithFlat_works() {
            ComputedStats base = ComputedStats.builder()
                .physicalDamage(0f)
                .build();

            AggregatedModifiers mods = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.PHYSICAL_DAMAGE, 50f, ModifierType.FLAT))
                .build();

            ComputedStats result = combiner.combine(base, mods);

            assertEquals(50f, result.getPhysicalDamage(), 0.1f);
        }

        @Test
        @DisplayName("Zero base with PERCENT routes to accumulator (not lost)")
        void combine_zeroBaseWithPercent_routesToAccumulator() {
            ComputedStats base = ComputedStats.builder()
                .physicalDamage(0f)
                .physicalDamagePercent(0f)
                .build();

            AggregatedModifiers mods = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.PHYSICAL_DAMAGE, 100f, ModifierType.PERCENT))
                .build();

            ComputedStats result = combiner.combine(base, mods);

            assertEquals(0f, result.getPhysicalDamage(), 0.1f);
            assertEquals(100f, result.getPhysicalDamagePercent(), 0.1f,
                "100% routed to accumulator, not lost");
        }
    }

    // ==================== Stat Category Tests ====================

    @Nested
    @DisplayName("Stat Categories")
    class StatCategoryTests {

        @Test
        @DisplayName("Offensive stats are combined correctly")
        void combine_offensiveStats_correct() {
            ComputedStats base = ComputedStats.builder()
                .criticalChance(5f)
                .criticalMultiplier(150f)
                .attackSpeedPercent(0f)
                .build();

            AggregatedModifiers mods = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.CRITICAL_CHANCE, 10f, ModifierType.FLAT))
                .addModifier(new StatModifier(StatType.CRITICAL_MULTIPLIER, 50f, ModifierType.FLAT))
                .addModifier(new StatModifier(StatType.ATTACK_SPEED_PERCENT, 15f, ModifierType.FLAT))
                .build();

            ComputedStats result = combiner.combine(base, mods);

            assertEquals(15f, result.getCriticalChance(), 0.1f);
            assertEquals(200f, result.getCriticalMultiplier(), 0.1f);
            assertEquals(15f, result.getAttackSpeedPercent(), 0.1f);
        }

        @Test
        @DisplayName("Defensive stats are combined correctly")
        void combine_defensiveStats_correct() {
            ComputedStats base = ComputedStats.builder()
                .armor(100f)
                .evasion(50f)
                .blockChance(10f)
                .build();

            AggregatedModifiers mods = AggregatedModifiers.builder()
                // PERCENT on ARMOR → routes to armorPercent
                .addModifier(new StatModifier(StatType.ARMOR, 50f, ModifierType.PERCENT))
                .addModifier(new StatModifier(StatType.EVASION, 100f, ModifierType.FLAT))
                .addModifier(new StatModifier(StatType.BLOCK_CHANCE, 5f, ModifierType.FLAT))
                .build();

            ComputedStats result = combiner.combine(base, mods);

            assertEquals(100f, result.getArmor(), 0.1f, "base unchanged, PERCENT routed");
            assertEquals(50f, result.getArmorPercent(), 0.1f, "50% in accumulator");
            assertEquals(150f, result.getEvasion(), 0.1f, "50 + 100 = 150");
            assertEquals(15f, result.getBlockChance(), 0.1f, "10 + 5 = 15");
        }

        @Test
        @DisplayName("Resource stats are combined correctly")
        void combine_resourceStats_correct() {
            ComputedStats base = ComputedStats.builder()
                .maxHealth(100f)
                .maxMana(50f)
                .healthRegen(1f)
                .manaRegen(0.5f)
                .build();

            AggregatedModifiers mods = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.MAX_HEALTH, 50f, ModifierType.FLAT))
                // PERCENT on MAX_MANA → routes to maxManaPercent
                .addModifier(new StatModifier(StatType.MAX_MANA, 100f, ModifierType.PERCENT))
                .addModifier(new StatModifier(StatType.HEALTH_REGEN, 1f, ModifierType.FLAT))
                .addModifier(new StatModifier(StatType.MANA_REGEN, 0.5f, ModifierType.FLAT))
                .build();

            ComputedStats result = combiner.combine(base, mods);

            assertEquals(150f, result.getMaxHealth(), 0.1f, "100 + 50 flat");
            assertEquals(50f, result.getMaxMana(), 0.1f, "mana unchanged, PERCENT routed");
            assertEquals(100f, result.getMaxManaPercent(), 0.1f, "100% in accumulator");
            assertEquals(2f, result.getHealthRegen(), 0.1f, "1 + 1 = 2");
            assertEquals(1f, result.getManaRegen(), 0.01f, "0.5 + 0.5 = 1.0");
        }
    }

    // ==================== Pipeline Coverage Tests ====================

    @Nested
    @DisplayName("Pipeline Coverage — every StatType wired correctly")
    class PipelineCoverageTests {

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = StatType.class, mode = EnumSource.Mode.EXCLUDE, names = {
            // Derived (post-processed from already-combined values)
            "HP_SCALING_DAMAGE", "SPEED_TO_SPELL_POWER", "ATK_SPEED_TO_SPELL_POWER", "EVASION_TO_ARMOR",
            // Fan-out (distributed to multiple fields)
            "ELEMENTAL_RESISTANCE", "ALL_ELEMENTAL_DAMAGE_PERCENT", "EVASION_PERCENT",
            // Combat pass-through (no ComputedStats field)
            "SPELL_TO_PHYSICAL_CONVERSION", "PHYSICAL_TO_SPELL_CONVERSION", "PHYSICAL_TO_FIRE_CONVERSION",
            "DAMAGE_TO_MANA_CONVERSION", "DAMAGE_TO_VOID_CONVERSION",
            "ENEMY_ELEMENTAL_VULNERABILITY", "ENEMY_RESISTANCE_REDUCTION",
            // Not wired in combine()
            "EXPERIENCE_GAIN",
            // Dead stat
            "PASSIVE_BLOCK_CHANCE"
        })
        @DisplayName("StatType flows through pipeline")
        void everyStatType_flatModifier_producesNonZeroOutput(StatType stat) throws Exception {
            ComputedStats base = ComputedStats.builder().build();

            AggregatedModifiers mods = AggregatedModifiers.builder()
                .addModifier(new StatModifier(stat, 10f, ModifierType.FLAT))
                .build();

            ComputedStats result = combiner.combine(base, mods);

            String fieldName = stat.getFieldName();
            String getterName = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
            Method getter = ComputedStats.class.getMethod(getterName);
            float value = ((Number) getter.invoke(result)).floatValue();

            assertNotEquals(0f, value,
                String.format("%s: flat +10 should produce non-zero via %s() (got %.2f)",
                    stat, getterName, value));
        }
    }

    // ==================== Newly Wired Stats Tests ====================

    @Nested
    @DisplayName("Newly Wired Stats")
    class NewlyWiredStatsTests {

        @Test
        @DisplayName("LIFE_LEECH maps to lifeLeech field (not lifeSteal)")
        void lifeLeech_mapsToCorrectField() {
            ComputedStats base = ComputedStats.builder()
                .lifeLeech(5f)
                .lifeSteal(10f)
                .build();

            AggregatedModifiers mods = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.LIFE_LEECH, 3f, ModifierType.FLAT))
                .build();

            ComputedStats result = combiner.combine(base, mods);

            assertEquals(8f, result.getLifeLeech(), 0.1f);
            assertEquals(10f, result.getLifeSteal(), 0.1f);
        }

        @Test
        @DisplayName("STAMINA_REGEN flows through pipeline")
        void staminaRegen_flowsThrough() {
            ComputedStats base = ComputedStats.builder().staminaRegen(2f).build();

            AggregatedModifiers mods = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.STAMINA_REGEN, 3f, ModifierType.FLAT))
                .build();

            ComputedStats result = combiner.combine(base, mods);
            assertEquals(5f, result.getStaminaRegen(), 0.1f);
        }

        @Test
        @DisplayName("KNOCKBACK_RESISTANCE all types additive (standalone)")
        void knockbackResistance_additive() {
            ComputedStats base = ComputedStats.builder().knockbackResistance(10f).build();

            AggregatedModifiers mods = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.KNOCKBACK_RESISTANCE, 15f, ModifierType.FLAT))
                .addModifier(new StatModifier(StatType.KNOCKBACK_RESISTANCE, 50f, ModifierType.PERCENT))
                .build();

            ComputedStats result = combiner.combine(base, mods);
            assertEquals(75f, result.getKnockbackResistance(), 0.1f, "10+15+50=75");
        }

        @Test
        @DisplayName("CHARGED_ATTACK_DAMAGE_PERCENT flows through pipeline")
        void chargedAttackDamagePercent_flowsThrough() {
            ComputedStats base = ComputedStats.builder().build();

            AggregatedModifiers mods = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.CHARGED_ATTACK_DAMAGE_PERCENT, 25f, ModifierType.FLAT))
                .build();

            ComputedStats result = combiner.combine(base, mods);
            assertEquals(25f, result.getChargedAttackDamagePercent(), 0.1f);
        }

        @Test
        @DisplayName("ACCURACY_PERCENT flows through pipeline")
        void accuracyPercent_flowsThrough() {
            ComputedStats base = ComputedStats.builder().build();

            AggregatedModifiers mods = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.ACCURACY_PERCENT, 10f, ModifierType.FLAT))
                .build();

            ComputedStats result = combiner.combine(base, mods);
            assertEquals(10f, result.getAccuracyPercent(), 0.1f);
        }

        @Test
        @DisplayName("PERCENT_HIT_AS_TRUE_DAMAGE flows through pipeline")
        void percentHitAsTrueDamage_flowsThrough() {
            ComputedStats base = ComputedStats.builder().build();

            AggregatedModifiers mods = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.PERCENT_HIT_AS_TRUE_DAMAGE, 8f, ModifierType.FLAT))
                .build();

            ComputedStats result = combiner.combine(base, mods);
            assertEquals(8f, result.getPercentHitAsTrueDamage(), 0.1f);
        }

        @Test
        @DisplayName("THORNS_DAMAGE_PERCENT flows through pipeline")
        void thornsDamagePercent_flowsThrough() {
            ComputedStats base = ComputedStats.builder().thornsDamagePercent(5f).build();

            AggregatedModifiers mods = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.THORNS_DAMAGE_PERCENT, 10f, ModifierType.FLAT))
                .build();

            ComputedStats result = combiner.combine(base, mods);
            assertEquals(15f, result.getThornsDamagePercent(), 0.1f);
        }

        @Test
        @DisplayName("Ailment damage percent stats flow through pipeline")
        void ailmentDamagePercent_flowsThrough() {
            ComputedStats base = ComputedStats.builder().build();

            AggregatedModifiers mods = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.BURN_DAMAGE_PERCENT, 20f, ModifierType.FLAT))
                .addModifier(new StatModifier(StatType.FROST_DAMAGE_PERCENT, 15f, ModifierType.FLAT))
                .addModifier(new StatModifier(StatType.SHOCK_DAMAGE_PERCENT, 10f, ModifierType.FLAT))
                .build();

            ComputedStats result = combiner.combine(base, mods);

            assertEquals(20f, result.getBurnDamagePercent(), 0.1f);
            assertEquals(15f, result.getFrostDamagePercent(), 0.1f);
            assertEquals(10f, result.getShockDamagePercent(), 0.1f);
        }

        @Test
        @DisplayName("Resource sub-stats flow through pipeline")
        void resourceSubStats_flowThrough() {
            ComputedStats base = ComputedStats.builder().build();

            AggregatedModifiers mods = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.SIGNATURE_ENERGY_MAX_PERCENT, 30f, ModifierType.FLAT))
                .addModifier(new StatModifier(StatType.SIGNATURE_ENERGY_PER_HIT, 5f, ModifierType.FLAT))
                .addModifier(new StatModifier(StatType.MANA_ON_KILL, 8f, ModifierType.FLAT))
                .build();

            ComputedStats result = combiner.combine(base, mods);

            assertEquals(30f, result.getSignatureEnergyMaxPercent(), 0.1f);
            assertEquals(5f, result.getSignatureEnergyPerHit(), 0.1f);
            assertEquals(8f, result.getManaOnKill(), 0.1f);
        }

        @Test
        @DisplayName("JUMP_FORCE_PERCENT flows through pipeline")
        void jumpForcePercent_flowsThrough() {
            ComputedStats base = ComputedStats.builder().build();

            AggregatedModifiers mods = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.JUMP_FORCE_PERCENT, 15f, ModifierType.FLAT))
                .build();

            ComputedStats result = combiner.combine(base, mods);
            assertEquals(15f, result.getJumpForcePercent(), 0.1f);
        }

        @Test
        @DisplayName("Movement sub-stats flow through pipeline")
        void movementSubStats_flowThrough() {
            ComputedStats base = ComputedStats.builder().build();

            AggregatedModifiers mods = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.SPRINT_SPEED_BONUS, 5f, ModifierType.FLAT))
                .addModifier(new StatModifier(StatType.CLIMB_SPEED_BONUS, 3f, ModifierType.FLAT))
                .addModifier(new StatModifier(StatType.CROUCH_SPEED_PERCENT, 10f, ModifierType.FLAT))
                .build();

            ComputedStats result = combiner.combine(base, mods);

            assertEquals(5f, result.getSprintSpeedBonus(), 0.1f);
            assertEquals(3f, result.getClimbSpeedBonus(), 0.1f);
            assertEquals(10f, result.getCrouchSpeedPercent(), 0.1f);
        }

        @Test
        @DisplayName("Defense sub-stats flow through pipeline")
        void defenseSubStats_flowThrough() {
            ComputedStats base = ComputedStats.builder().build();

            AggregatedModifiers mods = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.PARRY_CHANCE, 10f, ModifierType.FLAT))
                .addModifier(new StatModifier(StatType.CRITICAL_REDUCTION, 15f, ModifierType.FLAT))
                .addModifier(new StatModifier(StatType.BLOCK_DAMAGE_REDUCTION, 20f, ModifierType.FLAT))
                .addModifier(new StatModifier(StatType.STAMINA_DRAIN_REDUCTION, 12f, ModifierType.FLAT))
                .addModifier(new StatModifier(StatType.ARMOR_PENETRATION, 8f, ModifierType.FLAT))
                .build();

            ComputedStats result = combiner.combine(base, mods);

            assertEquals(10f, result.getParryChance(), 0.1f);
            assertEquals(15f, result.getCriticalReduction(), 0.1f);
            assertEquals(20f, result.getBlockDamageReduction(), 0.1f);
            assertEquals(12f, result.getStaminaDrainReduction(), 0.1f);
            assertEquals(8f, result.getArmorPenetration(), 0.1f);
        }
    }
}
