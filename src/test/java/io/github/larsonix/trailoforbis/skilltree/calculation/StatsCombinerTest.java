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
 * <p>Tests the PoE-style stat calculation formula:
 * Final = (Base + Flat) × (1 + Sum(Percent)/100) × Product(1 + Multiplier/100)
 *
 * <p>Also tests special handling for:
 * <ul>
 *   <li>EVASION_PERCENT - applied as percent modifier to evasion</li>
 *   <li>ELEMENTAL_RESISTANCE - distributed to all 6 element resistances</li>
 *   <li>ALL_ELEMENTAL_DAMAGE_PERCENT - distributed to all 6 element damage %</li>
 * </ul>
 */
public class StatsCombinerTest {

    private StatsCombiner combiner;

    @BeforeEach
    void setUp() {
        combiner = new StatsCombiner();
    }

    // ==================== Basic Formula Tests ====================

    @Nested
    @DisplayName("PoE Formula: (Base + Flat) × (1 + %Sum/100) × Π(1 + Mult/100)")
    class FormulaTests {

        @Test
        @DisplayName("No modifiers returns base stats unchanged")
        void combine_noModifiers_returnsBase() {
            ComputedStats base = ComputedStats.builder()
                .maxHealth(100f)
                .physicalDamage(50f)
                .build();

            AggregatedModifiers emptyMods = AggregatedModifiers.builder().build();

            ComputedStats result = combiner.combine(base, emptyMods);

            assertEquals(100f, result.getMaxHealth(), 0.1f,
                "No modifiers should keep base health");
            assertEquals(50f, result.getPhysicalDamage(), 0.1f,
                "No modifiers should keep base damage");
        }

        @Test
        @DisplayName("Flat modifier: 100 base + 20 flat = 120")
        void combine_flatModifier_addsToBase() {
            ComputedStats base = ComputedStats.builder()
                .maxHealth(100f)
                .build();

            AggregatedModifiers mods = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.MAX_HEALTH, 20f, ModifierType.FLAT))
                .build();

            ComputedStats result = combiner.combine(base, mods);

            assertEquals(120f, result.getMaxHealth(), 0.1f,
                "100 + 20 flat = 120");
        }

        @Test
        @DisplayName("Multiple flat modifiers stack additively: 100 + 10 + 15 = 125")
        void combine_multipleFlatModifiers_stackAdditively() {
            ComputedStats base = ComputedStats.builder()
                .physicalDamage(100f)
                .build();

            AggregatedModifiers mods = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.PHYSICAL_DAMAGE, 10f, ModifierType.FLAT))
                .addModifier(new StatModifier(StatType.PHYSICAL_DAMAGE, 15f, ModifierType.FLAT))
                .build();

            ComputedStats result = combiner.combine(base, mods);

            assertEquals(125f, result.getPhysicalDamage(), 0.1f,
                "100 + 10 + 15 = 125");
        }

        @Test
        @DisplayName("Percent modifier: 100 × (1 + 50/100) = 150")
        void combine_percentModifier_multipliesTotal() {
            ComputedStats base = ComputedStats.builder()
                .maxHealth(100f)
                .build();

            AggregatedModifiers mods = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.MAX_HEALTH, 50f, ModifierType.PERCENT))
                .build();

            ComputedStats result = combiner.combine(base, mods);

            assertEquals(150f, result.getMaxHealth(), 0.1f,
                "100 × 1.5 = 150");
        }

        @Test
        @DisplayName("Multiple percent modifiers stack additively: 100 × (1 + 30 + 20)/100) = 150")
        void combine_multiplePercentModifiers_stackAdditively() {
            ComputedStats base = ComputedStats.builder()
                .physicalDamage(100f)
                .build();

            AggregatedModifiers mods = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.PHYSICAL_DAMAGE, 30f, ModifierType.PERCENT))
                .addModifier(new StatModifier(StatType.PHYSICAL_DAMAGE, 20f, ModifierType.PERCENT))
                .build();

            ComputedStats result = combiner.combine(base, mods);

            assertEquals(150f, result.getPhysicalDamage(), 0.1f,
                "100 × (1 + 50/100) = 150");
        }

        @Test
        @DisplayName("Multiplier modifier: 100 × (1 + 20/100) = 120")
        void combine_multiplierModifier_multipliesResult() {
            ComputedStats base = ComputedStats.builder()
                .maxHealth(100f)
                .build();

            AggregatedModifiers mods = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.MAX_HEALTH, 20f, ModifierType.MULTIPLIER))
                .build();

            ComputedStats result = combiner.combine(base, mods);

            assertEquals(120f, result.getMaxHealth(), 0.1f,
                "100 × 1.2 = 120");
        }

        @Test
        @DisplayName("Multiple multipliers chain: 100 × 1.2 × 1.15 = 138")
        void combine_multipleMultipliers_chainMultiplicatively() {
            ComputedStats base = ComputedStats.builder()
                .physicalDamage(100f)
                .build();

            AggregatedModifiers mods = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.PHYSICAL_DAMAGE, 20f, ModifierType.MULTIPLIER))
                .addModifier(new StatModifier(StatType.PHYSICAL_DAMAGE, 15f, ModifierType.MULTIPLIER))
                .build();

            ComputedStats result = combiner.combine(base, mods);

            assertEquals(138f, result.getPhysicalDamage(), 0.1f,
                "100 × 1.2 × 1.15 = 138");
        }

        @Test
        @DisplayName("Full formula: (100 + 20) × (1 + 50/100) × (1 + 20/100) = 216")
        void combine_fullFormula_correctOrder() {
            ComputedStats base = ComputedStats.builder()
                .physicalDamage(100f)
                .build();

            AggregatedModifiers mods = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.PHYSICAL_DAMAGE, 20f, ModifierType.FLAT))
                .addModifier(new StatModifier(StatType.PHYSICAL_DAMAGE, 50f, ModifierType.PERCENT))
                .addModifier(new StatModifier(StatType.PHYSICAL_DAMAGE, 20f, ModifierType.MULTIPLIER))
                .build();

            ComputedStats result = combiner.combine(base, mods);

            // (100 + 20) × 1.5 × 1.2 = 120 × 1.5 × 1.2 = 216
            assertEquals(216f, result.getPhysicalDamage(), 0.1f,
                "(100 + 20) × 1.5 × 1.2 = 216");
        }
    }

    // ==================== Skill Tree Example Tests ====================

    @Nested
    @DisplayName("Skill Tree Examples")
    class SkillTreeExampleTests {

        @Test
        @DisplayName("Example: 100 base, +20 flat, +50%+30%, 20% more = 259.2")
        void combine_docExampleCalculation() {
            ComputedStats base = ComputedStats.builder()
                .physicalDamage(100f)
                .build();

            AggregatedModifiers mods = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.PHYSICAL_DAMAGE, 20f, ModifierType.FLAT))
                .addModifier(new StatModifier(StatType.PHYSICAL_DAMAGE, 50f, ModifierType.PERCENT))
                .addModifier(new StatModifier(StatType.PHYSICAL_DAMAGE, 30f, ModifierType.PERCENT))
                .addModifier(new StatModifier(StatType.PHYSICAL_DAMAGE, 20f, ModifierType.MULTIPLIER))
                .build();

            ComputedStats result = combiner.combine(base, mods);

            // (100 + 20) × (1 + 80/100) × (1 + 20/100)
            // = 120 × 1.8 × 1.2 = 259.2
            assertEquals(259.2f, result.getPhysicalDamage(), 0.1f,
                "(100 + 20) × 1.80 × 1.20 = 259.2");
        }

        @Test
        @DisplayName("Critical chance from skill tree nodes")
        void combine_criticalChance_fromNodes() {
            ComputedStats base = ComputedStats.builder()
                .criticalChance(5f)  // 5% base crit
                .build();

            AggregatedModifiers mods = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.CRITICAL_CHANCE, 2f, ModifierType.FLAT))  // +2%
                .addModifier(new StatModifier(StatType.CRITICAL_CHANCE, 3f, ModifierType.FLAT))  // +3%
                .build();

            ComputedStats result = combiner.combine(base, mods);

            assertEquals(10f, result.getCriticalChance(), 0.1f,
                "5% + 2% + 3% = 10% crit chance");
        }

        @Test
        @DisplayName("Defense stats: armor with percent bonus")
        void combine_armorWithPercentBonus() {
            ComputedStats base = ComputedStats.builder()
                .armor(100f)
                .build();

            AggregatedModifiers mods = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.ARMOR, 50f, ModifierType.FLAT))
                .addModifier(new StatModifier(StatType.ARMOR, 25f, ModifierType.PERCENT))
                .build();

            ComputedStats result = combiner.combine(base, mods);

            // (100 + 50) × 1.25 = 187.5
            assertEquals(187.5f, result.getArmor(), 0.1f,
                "(100 + 50) × 1.25 = 187.5");
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

            // 100 × (1 + 50/100) = 150
            assertEquals(150f, result.getEvasion(), 0.1f,
                "100 evasion + 50% = 150");
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

            // All 6 resistances should gain +15
            assertEquals(25f, result.getFireResistance(), 0.1f,
                "10 + 15 = 25 fire res");
            assertEquals(25f, result.getWaterResistance(), 0.1f,
                "10 + 15 = 25 water res");
            assertEquals(25f, result.getLightningResistance(), 0.1f,
                "10 + 15 = 25 lightning res");
            assertEquals(25f, result.getEarthResistance(), 0.1f,
                "10 + 15 = 25 earth res");
            assertEquals(25f, result.getWindResistance(), 0.1f,
                "10 + 15 = 25 wind res");
            assertEquals(15f, result.getVoidResistance(), 0.1f,
                "0 + 15 = 15 void res");
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

            // All 6 elemental damage % should gain +25
            assertEquals(35f, result.getFireDamagePercent(), 0.1f,
                "10 + 25 = 35 fire dmg %");
            assertEquals(45f, result.getWaterDamagePercent(), 0.1f,
                "20 + 25 = 45 water dmg %");
            assertEquals(25f, result.getLightningDamagePercent(), 0.1f,
                "0 + 25 = 25 lightning dmg %");
            assertEquals(40f, result.getEarthDamagePercent(), 0.1f,
                "15 + 25 = 40 earth dmg %");
            assertEquals(33f, result.getWindDamagePercent(), 0.1f,
                "8 + 25 = 33 wind dmg %");
            assertEquals(30f, result.getVoidDamagePercent(), 0.1f,
                "5 + 25 = 30 void dmg %");
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
                .addModifier(new StatModifier(StatType.MAX_HEALTH, 50f, ModifierType.PERCENT))
                .addModifier(new StatModifier(StatType.MAX_MANA, 20f, ModifierType.FLAT))
                .addModifier(new StatModifier(StatType.PHYSICAL_DAMAGE, 20f, ModifierType.MULTIPLIER))
                .build();

            ComputedStats result = combiner.combine(base, mods);

            assertEquals(150f, result.getMaxHealth(), 0.1f,
                "Health: 100 × 1.5 = 150");
            assertEquals(70f, result.getMaxMana(), 0.1f,
                "Mana: 50 + 20 = 70");
            assertEquals(12f, result.getPhysicalDamage(), 0.1f,
                "Phys: 10 × 1.2 = 12");
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

            assertEquals(200f, result.getMaxHealth(), 0.1f,
                "Health should be modified");
            assertEquals(50f, result.getMaxMana(), 0.1f,
                "Mana should be unchanged");
            assertEquals(75f, result.getArmor(), 0.1f,
                "Armor should be unchanged");
        }
    }

    // ==================== Octant Keystone Stats Tests ====================

    @Nested
    @DisplayName("Octant Keystone Stats")
    class OctantKeystoneStats {

        @Test
        @DisplayName("HP_SCALING_DAMAGE: bonus physDmg% = (maxHP/100) × stat")
        void hpScalingDamage_addsPhysDmgPercentFromMaxHp() {
            // base maxHP=500, physDmgPct=10, HP_SCALING_DAMAGE=0.5
            // bonus physDmg% = (500/100) * 0.5 = 2.5
            // result physDmgPct = 10 + 2.5 = 12.5
            ComputedStats base = ComputedStats.builder()
                .maxHealth(500f)
                .physicalDamagePercent(10f)
                .build();

            AggregatedModifiers mods = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.HP_SCALING_DAMAGE, 0.5f, ModifierType.FLAT))
                .build();

            ComputedStats result = combiner.combine(base, mods);

            assertEquals(12.5f, result.getPhysicalDamagePercent(), 0.1f,
                "bonus physDmg% = (500/100) × 0.5 = 2.5 → 10 + 2.5 = 12.5");
        }

        @Test
        @DisplayName("SPEED_TO_SPELL_POWER: bonus spellDmg% = moveSpeed% × (stat/100)")
        void speedToSpellPower_convertsMoveSpeedToSpellDmg() {
            // base moveSpeedPct=30, spellDmgPct=10, SPEED_TO_SPELL_POWER=50
            // bonus spellDmg% = 30 * (50/100) = 15
            // result spellDmgPct = 10 + 15 = 25
            ComputedStats base = ComputedStats.builder()
                .movementSpeedPercent(30f)
                .spellDamagePercent(10f)
                .build();

            AggregatedModifiers mods = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.SPEED_TO_SPELL_POWER, 50f, ModifierType.FLAT))
                .build();

            ComputedStats result = combiner.combine(base, mods);

            assertEquals(25f, result.getSpellDamagePercent(), 0.1f,
                "bonus spellDmg% = 30 × (50/100) = 15 → 10 + 15 = 25");
        }

        @Test
        @DisplayName("ATK_SPEED_TO_SPELL_POWER: bonus spellDmg% = atkSpeed% × (stat/100)")
        void atkSpeedToSpellPower_convertsAtkSpeedToSpellDmg() {
            // base atkSpeedPct=20, spellDmgPct=5, ATK_SPEED_TO_SPELL_POWER=75
            // bonus spellDmg% = 20 * (75/100) = 15
            // result spellDmgPct = 5 + 15 = 20
            ComputedStats base = ComputedStats.builder()
                .attackSpeedPercent(20f)
                .spellDamagePercent(5f)
                .build();

            AggregatedModifiers mods = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.ATK_SPEED_TO_SPELL_POWER, 75f, ModifierType.FLAT))
                .build();

            ComputedStats result = combiner.combine(base, mods);

            assertEquals(20f, result.getSpellDamagePercent(), 0.1f,
                "bonus spellDmg% = 20 × (75/100) = 15 → 5 + 15 = 20");
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

            assertEquals(25f, result.getDetonateDotOnCrit(), 0.1f, "DETONATE_DOT_ON_CRIT");
            assertEquals(5f, result.getConsecutiveHitBonus(), 0.1f, "CONSECUTIVE_HIT_BONUS");
            assertEquals(30f, result.getBlockCounterDamage(), 0.1f, "BLOCK_COUNTER_DAMAGE");
            assertEquals(10f, result.getShieldRegenOnDot(), 0.1f, "SHIELD_REGEN_ON_DOT");
            assertEquals(1f, result.getImmunityOnAilment(), 0.1f, "IMMUNITY_ON_AILMENT");
            assertEquals(15f, result.getSpellEchoChance(), 0.1f, "SPELL_ECHO_CHANCE");
        }

        @Test
        @DisplayName("Octant stats apply PoE formula (FLAT+PERCENT+MULT)")
        void octantStat_appliesFullPoeFormula() {
            // BLOCK_COUNTER_DAMAGE: base=10, flat=5, percent=100, mult=50
            // Result = (10+5) × (1+100/100) × (1+50/100) = 15 × 2 × 1.5 = 45
            ComputedStats base = ComputedStats.builder()
                .blockCounterDamage(10f)
                .build();

            AggregatedModifiers mods = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.BLOCK_COUNTER_DAMAGE, 5f, ModifierType.FLAT))
                .addModifier(new StatModifier(StatType.BLOCK_COUNTER_DAMAGE, 100f, ModifierType.PERCENT))
                .addModifier(new StatModifier(StatType.BLOCK_COUNTER_DAMAGE, 50f, ModifierType.MULTIPLIER))
                .build();

            ComputedStats result = combiner.combine(base, mods);

            assertEquals(45f, result.getBlockCounterDamage(), 0.1f,
                "(10+5) × 2.0 × 1.5 = 45");
        }

        @Test
        @DisplayName("EVASION_TO_ARMOR: bonus armor = evasion × (stat/100)")
        void evasionToArmor_convertsEvasionToArmor() {
            // base evasion=200, armor=50, EVASION_TO_ARMOR=30
            // bonus armor = 200 × (30/100) = 60
            // result armor = 50 + 60 = 110
            ComputedStats base = ComputedStats.builder()
                .evasion(200f)
                .armor(50f)
                .build();

            AggregatedModifiers mods = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.EVASION_TO_ARMOR, 30f, ModifierType.FLAT))
                .build();

            ComputedStats result = combiner.combine(base, mods);

            assertEquals(110f, result.getArmor(), 0.1f,
                "bonus armor = 200 × (30/100) = 60 → 50 + 60 = 110");
            assertEquals(200f, result.getEvasion(), 0.1f,
                "evasion should be unchanged (conversion doesn't consume it)");
        }

        @Test
        @DisplayName("EVASION_TO_ARMOR: uses post-processed evasion value")
        void evasionToArmor_usesPostProcessedEvasion() {
            // base evasion=100, +100 flat → combined evasion=200
            // EVASION_TO_ARMOR=50 → bonus armor = 200 × (50/100) = 100
            ComputedStats base = ComputedStats.builder()
                .evasion(100f)
                .armor(0f)
                .build();

            AggregatedModifiers mods = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.EVASION, 100f, ModifierType.FLAT))
                .addModifier(new StatModifier(StatType.EVASION_TO_ARMOR, 50f, ModifierType.FLAT))
                .build();

            ComputedStats result = combiner.combine(base, mods);

            assertEquals(200f, result.getEvasion(), 0.1f, "evasion = 100 + 100 = 200");
            assertEquals(100f, result.getArmor(), 0.1f,
                "bonus armor = 200 × (50/100) = 100 (uses post-processed evasion)");
        }

        @Test
        @DisplayName("Derived stats use post-processed maxHP, not base")
        void derivedStats_usePostProcessedValues() {
            // Base maxHP=100, FLAT +400 → combined maxHP=500
            // HP_SCALING_DAMAGE=1.0 → bonus physDmg% = (500/100) × 1.0 = 5.0
            // Verifies that the derived stat reads the COMBINED maxHP, not base
            ComputedStats base = ComputedStats.builder()
                .maxHealth(100f)
                .physicalDamagePercent(0f)
                .build();

            AggregatedModifiers mods = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.MAX_HEALTH, 400f, ModifierType.FLAT))
                .addModifier(new StatModifier(StatType.HP_SCALING_DAMAGE, 1.0f, ModifierType.FLAT))
                .build();

            ComputedStats result = combiner.combine(base, mods);

            // maxHP should be 500 (100 + 400)
            assertEquals(500f, result.getMaxHealth(), 0.1f, "maxHP = 100 + 400 = 500");
            // physDmgPct should be 5.0 (from 500 HP, not 100 base)
            assertEquals(5.0f, result.getPhysicalDamagePercent(), 0.1f,
                "bonus physDmg% = (500/100) × 1.0 = 5.0 (uses post-processed maxHP)");
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

            assertEquals(50f, result.getPhysicalDamage(), 0.1f,
                "0 + 50 = 50");
        }

        @Test
        @DisplayName("Zero base with percent modifier stays zero")
        void combine_zeroBaseWithPercent_staysZero() {
            ComputedStats base = ComputedStats.builder()
                .physicalDamage(0f)
                .build();

            AggregatedModifiers mods = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.PHYSICAL_DAMAGE, 100f, ModifierType.PERCENT))
                .build();

            ComputedStats result = combiner.combine(base, mods);

            // 0 × 2.0 = 0
            assertEquals(0f, result.getPhysicalDamage(), 0.1f,
                "0 × any = 0");
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

            assertEquals(80f, result.getMaxHealth(), 0.1f,
                "100 - 20 = 80");
        }

        @Test
        @DisplayName("Negative percent modifier reduces stat")
        void combine_negativePercentModifier_reduces() {
            ComputedStats base = ComputedStats.builder()
                .maxHealth(100f)
                .build();

            AggregatedModifiers mods = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.MAX_HEALTH, -25f, ModifierType.PERCENT))
                .build();

            ComputedStats result = combiner.combine(base, mods);

            assertEquals(75f, result.getMaxHealth(), 0.1f,
                "100 × 0.75 = 75");
        }

        @Test
        @DisplayName("Large values don't overflow")
        void combine_largeValues_noOverflow() {
            ComputedStats base = ComputedStats.builder()
                .maxHealth(1000000f)
                .build();

            AggregatedModifiers mods = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.MAX_HEALTH, 1000000f, ModifierType.FLAT))
                .addModifier(new StatModifier(StatType.MAX_HEALTH, 100f, ModifierType.PERCENT))
                .build();

            ComputedStats result = combiner.combine(base, mods);

            // (1M + 1M) × 2.0 = 4M
            assertEquals(4000000f, result.getMaxHealth(), 1f,
                "Large values should not overflow");
        }

        @Test
        @DisplayName("Multiple multipliers with one being zero")
        void combine_multiplierWithZero_becomesZero() {
            ComputedStats base = ComputedStats.builder()
                .physicalDamage(100f)
                .build();

            AggregatedModifiers mods = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.PHYSICAL_DAMAGE, 50f, ModifierType.MULTIPLIER))
                .addModifier(new StatModifier(StatType.PHYSICAL_DAMAGE, -100f, ModifierType.MULTIPLIER))
                .build();

            ComputedStats result = combiner.combine(base, mods);

            // 100 × 1.5 × 0.0 = 0
            assertEquals(0f, result.getPhysicalDamage(), 0.1f,
                "Multiplier of -100% should reduce to zero");
        }
    }

    // ==================== Stat Categories Tests ====================

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
                .addModifier(new StatModifier(StatType.ARMOR, 50f, ModifierType.PERCENT))
                .addModifier(new StatModifier(StatType.EVASION, 100f, ModifierType.FLAT))
                .addModifier(new StatModifier(StatType.BLOCK_CHANCE, 5f, ModifierType.FLAT))
                .build();

            ComputedStats result = combiner.combine(base, mods);

            assertEquals(150f, result.getArmor(), 0.1f);
            assertEquals(150f, result.getEvasion(), 0.1f);
            assertEquals(15f, result.getBlockChance(), 0.1f);
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
                .addModifier(new StatModifier(StatType.MAX_MANA, 100f, ModifierType.PERCENT))
                .addModifier(new StatModifier(StatType.HEALTH_REGEN, 1f, ModifierType.FLAT))
                .addModifier(new StatModifier(StatType.MANA_REGEN, 50f, ModifierType.PERCENT))
                .build();

            ComputedStats result = combiner.combine(base, mods);

            assertEquals(150f, result.getMaxHealth(), 0.1f);
            assertEquals(100f, result.getMaxMana(), 0.1f);
            assertEquals(2f, result.getHealthRegen(), 0.1f);
            assertEquals(0.75f, result.getManaRegen(), 0.01f);
        }
    }

    // ==================== Pipeline Coverage Tests ====================

    @Nested
    @DisplayName("Pipeline Coverage — every StatType wired correctly")
    class PipelineCoverageTests {

        /**
         * Verifies that every standard StatType, when given a flat modifier,
         * produces a non-zero output. This catches stats that are silently
         * dropped due to missing applyModifier() calls or setStatInBuilder() cases.
         */
        @ParameterizedTest(name = "{0}")
        @EnumSource(value = StatType.class, mode = EnumSource.Mode.EXCLUDE, names = {
            // Derived (post-processed from already-combined values in combine())
            "HP_SCALING_DAMAGE", "SPEED_TO_SPELL_POWER", "ATK_SPEED_TO_SPELL_POWER", "EVASION_TO_ARMOR",
            // Fan-out (distributed to multiple fields, not a single field)
            "ELEMENTAL_RESISTANCE", "ALL_ELEMENTAL_DAMAGE_PERCENT", "EVASION_PERCENT",
            // Combat pass-through (empty case — read from AggregatedModifiers directly)
            "SPELL_TO_PHYSICAL_CONVERSION", "PHYSICAL_TO_SPELL_CONVERSION", "PHYSICAL_TO_FIRE_CONVERSION",
            "DAMAGE_TO_MANA_CONVERSION", "DAMAGE_TO_VOID_CONVERSION",
            "ENEMY_ELEMENTAL_VULNERABILITY", "ENEMY_RESISTANCE_REDUCTION",
            // Not wired in combine() — has setStatInBuilder case but no applyModifier call
            "EXPERIENCE_GAIN",
            // Dead stat — passive_block_chance redirected to BLOCK_CHANCE
            "PASSIVE_BLOCK_CHANCE"
        })
        @DisplayName("StatType flows through pipeline")
        void everyStatType_flatModifier_producesNonZeroOutput(StatType stat) throws Exception {
            ComputedStats base = ComputedStats.builder().build();

            AggregatedModifiers mods = AggregatedModifiers.builder()
                .addModifier(new StatModifier(stat, 10f, ModifierType.FLAT))
                .build();

            ComputedStats result = combiner.combine(base, mods);

            // Use reflection to read the corresponding field value
            String fieldName = stat.getFieldName();
            String getterName = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
            Method getter = ComputedStats.class.getMethod(getterName);
            float value = ((Number) getter.invoke(result)).floatValue();

            assertNotEquals(0f, value,
                String.format("%s: flat +10 should produce non-zero result via %s() (got %.2f)",
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

            assertEquals(8f, result.getLifeLeech(), 0.1f,
                "LIFE_LEECH should modify lifeLeech: 5 + 3 = 8");
            assertEquals(10f, result.getLifeSteal(), 0.1f,
                "LIFE_LEECH should NOT modify lifeSteal");
        }

        @Test
        @DisplayName("STAMINA_REGEN flows through pipeline")
        void staminaRegen_flowsThrough() {
            ComputedStats base = ComputedStats.builder()
                .staminaRegen(2f)
                .build();

            AggregatedModifiers mods = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.STAMINA_REGEN, 3f, ModifierType.FLAT))
                .build();

            ComputedStats result = combiner.combine(base, mods);

            assertEquals(5f, result.getStaminaRegen(), 0.1f,
                "2 + 3 = 5 stamina regen");
        }

        @Test
        @DisplayName("KNOCKBACK_RESISTANCE applies PoE formula")
        void knockbackResistance_appliesFormula() {
            ComputedStats base = ComputedStats.builder()
                .knockbackResistance(10f)
                .build();

            AggregatedModifiers mods = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.KNOCKBACK_RESISTANCE, 15f, ModifierType.FLAT))
                .addModifier(new StatModifier(StatType.KNOCKBACK_RESISTANCE, 50f, ModifierType.PERCENT))
                .build();

            ComputedStats result = combiner.combine(base, mods);

            // (10 + 15) × 1.5 = 37.5
            assertEquals(37.5f, result.getKnockbackResistance(), 0.1f,
                "(10 + 15) × 1.5 = 37.5");
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
            ComputedStats base = ComputedStats.builder()
                .thornsDamagePercent(5f)
                .build();

            AggregatedModifiers mods = AggregatedModifiers.builder()
                .addModifier(new StatModifier(StatType.THORNS_DAMAGE_PERCENT, 10f, ModifierType.FLAT))
                .build();

            ComputedStats result = combiner.combine(base, mods);

            assertEquals(15f, result.getThornsDamagePercent(), 0.1f,
                "5 + 10 = 15");
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
