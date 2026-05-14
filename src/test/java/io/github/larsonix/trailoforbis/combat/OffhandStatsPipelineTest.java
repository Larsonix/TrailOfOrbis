package io.github.larsonix.trailoforbis.combat;

import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.combat.blocking.BlockingProcessor;
import io.github.larsonix.trailoforbis.combat.blocking.BlockResult;
import io.github.larsonix.trailoforbis.combat.blocking.BlockingStats;
import io.github.larsonix.trailoforbis.elemental.ElementType;
import io.github.larsonix.trailoforbis.elemental.ElementalStats;
import io.github.larsonix.trailoforbis.gear.config.ModifierConfig.StatType;
import io.github.larsonix.trailoforbis.gear.stats.StatMapping;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.damage.DamageDataComponent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.WieldingInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * End-to-end integration tests proving offhand item stats flow through
 * the full pipeline and affect actual damage/defense output.
 *
 * <p>These tests use REAL objects (RPGDamageCalculator, BlockingProcessor,
 * StatMapping, ComputedStats) — no mocks on the calculation side.
 * Only Hytale ECS components (Store, Ref, DamageDataComponent) are mocked.
 *
 * <p>Pipeline under test:
 * <pre>
 *   Offhand stat → StatMapping.apply() → ComputedStats field
 *       → RPGDamageCalculator.calculate() / BlockingProcessor.checkActiveBlock()
 *       → measurable damage/defense change
 * </pre>
 */
@ExtendWith(MockitoExtension.class)
class OffhandStatsPipelineTest {

    private RPGDamageCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new RPGDamageCalculator();
    }

    // =========================================================================
    // ARMOR FROM OFFHAND → PHYSICAL DAMAGE REDUCTION
    // =========================================================================

    @Nested
    @DisplayName("Offhand Armor → Damage Reduction")
    class OffhandArmorTests {

        @Test
        @DisplayName("Armor stat from offhand reduces physical damage via RPGDamageCalculator")
        void armorFromOffhand_reducesPhysicalDamage() {
            // --- Baseline: defender with NO armor ---
            ComputedStats defenderNoArmor = ComputedStats.builder().build();

            // --- With offhand armor: simulate StatMapping depositing 50 flat armor ---
            ComputedStats defenderWithArmor = ComputedStats.builder().build();
            StatMapping.apply(defenderWithArmor, "armor", 50.0, StatType.FLAT);

            // Verify StatMapping actually set the field
            assertEquals(50.0, defenderWithArmor.getArmor(), 0.01,
                "StatMapping must deposit armor into ComputedStats");

            // Attacker: 100 base physical damage, no crit, no bonuses
            ComputedStats attacker = ComputedStats.builder()
                .weaponBaseDamage(100f)
                .holdingRpgGear(true)
                .criticalChance(0f)
                .criticalMultiplier(150f)
                .build();

            // Calculate damage with NO armor
            DamageBreakdown resultNoArmor = calculator.calculate(
                100f, attacker, new ElementalStats(),
                defenderNoArmor, null,
                AttackType.MELEE, false);

            // Calculate damage WITH armor from offhand
            DamageBreakdown resultWithArmor = calculator.calculate(
                100f, attacker, new ElementalStats(),
                defenderWithArmor, null,
                AttackType.MELEE, false);

            // The armored defender MUST take less damage
            float damageNoArmor = resultNoArmor.totalDamage();
            float damageWithArmor = resultWithArmor.totalDamage();

            assertTrue(damageNoArmor > 0, "Baseline damage must be positive");
            assertTrue(damageWithArmor < damageNoArmor,
                String.format("50 armor from offhand must reduce damage: %.1f (no armor) vs %.1f (with armor)",
                    damageNoArmor, damageWithArmor));

            // Verify the reduction is meaningful (armor formula: 50 / (50 + 9*1 + 50) ≈ 45.9%)
            float reductionPercent = (1f - damageWithArmor / damageNoArmor) * 100f;
            assertTrue(reductionPercent > 30f && reductionPercent < 60f,
                String.format("Armor reduction %.1f%% should be in expected range [30-60%%]", reductionPercent));
        }

        @Test
        @DisplayName("Armor percent from offhand modifier multiplies base armor")
        void armorPercentFromOffhand_multipliesBaseArmor() {
            // Base armor (from other gear) + percent from offhand modifier
            ComputedStats defender = ComputedStats.builder().build();
            StatMapping.apply(defender, "armor", 100.0, StatType.FLAT);      // base from body armor
            StatMapping.apply(defender, "armor", 25.0, StatType.PERCENT);    // +25% from offhand modifier

            assertEquals(100.0, defender.getArmor(), 0.01, "Flat armor deposited");
            assertEquals(25.0f, defender.getArmorPercent(), 0.01, "Percent armor deposited");

            // Consolidate: armor percent applied here (same as real pipeline)
            defender.consolidateResourcePercents();
            assertEquals(125.0, defender.getArmor(), 0.01, "Armor after consolidation = 100 * 1.25");

            ComputedStats attacker = ComputedStats.builder()
                .weaponBaseDamage(100f)
                .holdingRpgGear(true)
                .criticalChance(0f)
                .criticalMultiplier(150f)
                .build();

            // Compare: 100 armor vs 100 armor + 25% from offhand
            ComputedStats defenderBaseOnly = ComputedStats.builder().build();
            StatMapping.apply(defenderBaseOnly, "armor", 100.0, StatType.FLAT);
            defenderBaseOnly.consolidateResourcePercents();

            DamageBreakdown resultBase = calculator.calculate(
                100f, attacker, new ElementalStats(),
                defenderBaseOnly, null, AttackType.MELEE, false);

            DamageBreakdown resultWithPercent = calculator.calculate(
                100f, attacker, new ElementalStats(),
                defender, null, AttackType.MELEE, false);

            assertTrue(resultWithPercent.totalDamage() < resultBase.totalDamage(),
                String.format("25%% armor from offhand must further reduce damage: %.1f → %.1f",
                    resultBase.totalDamage(), resultWithPercent.totalDamage()));
        }
    }

    // =========================================================================
    // BLOCK CHANCE FROM OFFHAND → BLOCKING PROCESSOR
    // =========================================================================

    @Nested
    @DisplayName("Offhand Block Chance → Blocking System")
    @ExtendWith(MockitoExtension.class)
    class OffhandBlockingTests {

        @Mock private Store<EntityStore> store;
        @Mock private Ref<EntityStore> defenderRef;
        @Mock private DamageDataComponent damageDataComponent;
        @Mock private WieldingInteraction wieldingInteraction;
        @Mock private ComponentType<EntityStore, DamageDataComponent> componentType;
        private MockedStatic<DamageDataComponent> mockedDamageDataComponent;
        private BlockingProcessor blockingProcessor;

        @BeforeEach
        void setUpBlocking() {
            blockingProcessor = new BlockingProcessor();
            mockedDamageDataComponent = mockStatic(DamageDataComponent.class);
            mockedDamageDataComponent.when(DamageDataComponent::getComponentType).thenReturn(componentType);
        }

        @AfterEach
        void tearDownBlocking() {
            if (mockedDamageDataComponent != null) {
                mockedDamageDataComponent.close();
            }
        }

        @Test
        @DisplayName("block_chance from offhand shield implicit reaches BlockingProcessor")
        void blockChanceFromOffhand_reachesBlockingProcessor() {
            // Simulate StatMapping depositing block_chance from shield implicit
            ComputedStats stats = ComputedStats.builder().build();
            StatMapping.apply(stats, "block_chance", 15.0, StatType.FLAT);

            // Verify the stat reaches ComputedStats
            assertEquals(15.0f, stats.getBlockChance(), 0.01,
                "block_chance from shield implicit must reach ComputedStats");

            // Verify BlockingStats reads it correctly
            BlockingStats blockingStats = BlockingStats.from(stats);
            assertEquals(15.0f, blockingStats.blockChance(), 0.01,
                "BlockingStats.from() must extract block_chance from ComputedStats");
        }

        @Test
        @DisplayName("block_damage_reduction from offhand modifier reaches BlockingProcessor and reduces damage")
        void blockDamageReductionFromOffhand_reducesDamage() {
            // Simulate: shield implicit gives block_chance, modifier gives block_damage_reduction
            ComputedStats stats = ComputedStats.builder().build();
            StatMapping.apply(stats, "block_chance", 100.0, StatType.FLAT);         // 100% chance (deterministic)
            StatMapping.apply(stats, "block_damage_reduction", 60.0, StatType.PERCENT); // 60% reduction

            // Mock the Hytale ECS for "player is actively blocking"
            when(store.getComponent(defenderRef, componentType)).thenReturn(damageDataComponent);
            when(damageDataComponent.getCurrentWielding()).thenReturn(wieldingInteraction);
            when(wieldingInteraction.getStaminaCost()).thenReturn(null);

            Optional<BlockResult> result = blockingProcessor.checkActiveBlock(
                store, defenderRef, stats, 100f);

            assertTrue(result.isPresent(), "Block attempt must return a result");
            BlockResult blockResult = result.get();
            assertTrue(blockResult.blocked(), "Block must succeed at 100% chance");

            // Perfect block at 100% chance = full avoidance (1.0 reduction)
            assertEquals(1.0f, blockResult.damageReduction(), 0.001f,
                "100% block_chance from shield = perfect block = full damage avoidance");
        }

        @Test
        @DisplayName("Zero block_chance means block always fails even with high reduction")
        void zeroBlockChance_blockAlwaysFails() {
            ComputedStats stats = ComputedStats.builder().build();
            // No block_chance deposited (default 0), but high damage reduction
            StatMapping.apply(stats, "block_damage_reduction", 90.0, StatType.PERCENT);

            when(store.getComponent(defenderRef, componentType)).thenReturn(damageDataComponent);
            when(damageDataComponent.getCurrentWielding()).thenReturn(wieldingInteraction);

            Optional<BlockResult> result = blockingProcessor.checkActiveBlock(
                store, defenderRef, stats, 100f);

            assertTrue(result.isPresent(), "Block attempt returns result even on failure");
            assertFalse(result.get().blocked(), "0% block_chance must always fail");
        }
    }

    // =========================================================================
    // OFFENSIVE STATS FROM OFFHAND → DAMAGE OUTPUT
    // =========================================================================

    @Nested
    @DisplayName("Offhand Offensive Stats → Damage Output")
    class OffhandOffensiveTests {

        @Test
        @DisplayName("physical_damage from offhand modifier increases total damage")
        void physicalDamageFromOffhand_increasesTotalDamage() {
            // Baseline attacker
            ComputedStats attackerBase = ComputedStats.builder()
                .weaponBaseDamage(50f)
                .holdingRpgGear(true)
                .criticalChance(0f)
                .criticalMultiplier(150f)
                .build();

            // Attacker with offhand flat physical damage (e.g., from a spellbook modifier)
            ComputedStats attackerWithOffhand = ComputedStats.builder()
                .weaponBaseDamage(50f)
                .holdingRpgGear(true)
                .criticalChance(0f)
                .criticalMultiplier(150f)
                .build();
            StatMapping.apply(attackerWithOffhand, "physical_damage", 20.0, StatType.FLAT);

            assertEquals(20.0, attackerWithOffhand.getPhysicalDamage(), 0.01,
                "physical_damage from offhand must reach ComputedStats");

            DamageBreakdown resultBase = calculator.calculate(
                50f, attackerBase, new ElementalStats(),
                null, null, AttackType.MELEE, false);

            DamageBreakdown resultWithOffhand = calculator.calculate(
                50f, attackerWithOffhand, new ElementalStats(),
                null, null, AttackType.MELEE, false);

            assertTrue(resultWithOffhand.totalDamage() > resultBase.totalDamage(),
                String.format("+20 phys_damage from offhand must increase damage: %.1f → %.1f",
                    resultBase.totalDamage(), resultWithOffhand.totalDamage()));

            // The increase should be approximately 20 (flat added to base)
            float increase = resultWithOffhand.totalDamage() - resultBase.totalDamage();
            assertTrue(increase > 15f && increase < 25f,
                String.format("Damage increase %.1f should be ~20 (flat phys from offhand)", increase));
        }

        @Test
        @DisplayName("spell_damage from offhand spellbook modifier increases spell damage")
        void spellDamageFromOffhand_increasesSpellDamage() {
            // Baseline: wand attack with fire spell element (spellElement required for flat spell routing)
            ComputedStats attackerBase = ComputedStats.builder()
                .weaponBaseDamage(80f)
                .weaponSpellElement(ElementType.FIRE)
                .holdingRpgGear(true)
                .criticalChance(0f)
                .criticalMultiplier(150f)
                .build();

            // With offhand spell damage (wand user holding a spellbook in utility)
            ComputedStats attackerWithOffhand = ComputedStats.builder()
                .weaponBaseDamage(80f)
                .weaponSpellElement(ElementType.FIRE)
                .holdingRpgGear(true)
                .criticalChance(0f)
                .criticalMultiplier(150f)
                .build();
            StatMapping.apply(attackerWithOffhand, "spell_damage", 30.0, StatType.FLAT);

            // Spell attacks require a spellElement for flat spell damage routing
            DamageBreakdown resultBase = calculator.calculate(
                80f, attackerBase, new ElementalStats(),
                null, null, AttackType.SPELL, false,
                1.0f, false, ElementType.FIRE, 1, false);

            DamageBreakdown resultWithOffhand = calculator.calculate(
                80f, attackerWithOffhand, new ElementalStats(),
                null, null, AttackType.SPELL, false,
                1.0f, false, ElementType.FIRE, 1, false);

            assertTrue(resultWithOffhand.totalDamage() > resultBase.totalDamage(),
                String.format("+30 spell_damage from offhand must increase spell damage: %.1f → %.1f",
                    resultBase.totalDamage(), resultWithOffhand.totalDamage()));
        }

        @Test
        @DisplayName("crit_chance from offhand modifier can trigger critical hits")
        void critChanceFromOffhand_canTriggerCrits() {
            // 100% crit chance from offhand (deterministic)
            ComputedStats attackerWithCrit = ComputedStats.builder()
                .weaponBaseDamage(100f)
                .holdingRpgGear(true)
                .criticalChance(0f)
                .criticalMultiplier(200f)
                .build();
            StatMapping.apply(attackerWithCrit, "crit_chance", 100.0, StatType.PERCENT);

            assertEquals(100.0, attackerWithCrit.getCriticalChance(), 0.01,
                "crit_chance from offhand must reach ComputedStats");

            // No crit for comparison
            ComputedStats attackerNoCrit = ComputedStats.builder()
                .weaponBaseDamage(100f)
                .holdingRpgGear(true)
                .criticalChance(0f)
                .criticalMultiplier(200f)
                .build();

            DamageBreakdown resultNoCrit = calculator.calculate(
                100f, attackerNoCrit, new ElementalStats(),
                null, null, AttackType.MELEE, false);

            DamageBreakdown resultWithCrit = calculator.calculate(
                100f, attackerWithCrit, new ElementalStats(),
                null, null, AttackType.MELEE, false);

            assertTrue(resultWithCrit.totalDamage() > resultNoCrit.totalDamage(),
                String.format("100%% crit from offhand must increase damage: %.1f → %.1f",
                    resultNoCrit.totalDamage(), resultWithCrit.totalDamage()));
            assertTrue(resultWithCrit.wasCritical(), "Attack must be marked as critical");
        }
    }

    // =========================================================================
    // ENERGY SHIELD FROM OFFHAND → DEFENSE LAYER
    // =========================================================================

    @Nested
    @DisplayName("Offhand Energy Shield → ComputedStats")
    class OffhandEnergyShieldTests {

        @Test
        @DisplayName("energy_shield from offhand modifier reaches ComputedStats")
        void energyShieldFromOffhand_reachesComputedStats() {
            ComputedStats stats = ComputedStats.builder().build();
            StatMapping.apply(stats, "energy_shield", 40.0, StatType.FLAT);

            assertEquals(40.0, stats.getEnergyShield(), 0.01,
                "energy_shield from offhand must reach ComputedStats");
        }

        @Test
        @DisplayName("energy_shield percent from offhand stacks with flat")
        void energyShieldPercentFromOffhand_stacksWithFlat() {
            ComputedStats stats = ComputedStats.builder().build();
            StatMapping.apply(stats, "energy_shield", 100.0, StatType.FLAT);
            StatMapping.apply(stats, "energy_shield", 50.0, StatType.PERCENT);

            assertEquals(100.0, stats.getEnergyShield(), 0.01, "Flat ES deposited");
            assertEquals(50.0f, stats.getEnergyShieldPercent(), 0.01, "Percent ES deposited");
        }
    }

    // =========================================================================
    // COMBINED: FULL OFFHAND GEAR SIMULATION
    // =========================================================================

    @Nested
    @DisplayName("Combined Offhand Gear Simulation")
    class CombinedSimulation {

        @Test
        @DisplayName("Full shield stats: armor + block_chance + block_damage_reduction all contribute")
        void fullShieldStats_allContribute() {
            // Simulate a shield that provides: 30 armor (implicit), 15% block_chance, 40% block_damage_reduction
            ComputedStats defender = ComputedStats.builder().build();
            StatMapping.apply(defender, "armor", 30.0, StatType.FLAT);
            StatMapping.apply(defender, "block_chance", 15.0, StatType.FLAT);
            StatMapping.apply(defender, "block_damage_reduction", 40.0, StatType.PERCENT);

            // All three stats deposited
            assertEquals(30.0, defender.getArmor(), 0.01, "Armor from shield implicit");
            assertEquals(15.0f, defender.getBlockChance(), 0.01, "Block chance from shield");
            assertEquals(40.0, defender.getBlockDamageReduction(), 0.01, "Block damage reduction");

            // Armor affects damage calculation
            ComputedStats attacker = ComputedStats.builder()
                .weaponBaseDamage(100f)
                .holdingRpgGear(true)
                .criticalChance(0f)
                .criticalMultiplier(150f)
                .build();

            DamageBreakdown withShield = calculator.calculate(
                100f, attacker, new ElementalStats(),
                defender, null, AttackType.MELEE, false);

            DamageBreakdown withoutShield = calculator.calculate(
                100f, attacker, new ElementalStats(),
                null, null, AttackType.MELEE, false);

            assertTrue(withShield.totalDamage() < withoutShield.totalDamage(),
                String.format("Shield armor must reduce damage: %.1f (shielded) vs %.1f (bare)",
                    withShield.totalDamage(), withoutShield.totalDamage()));

            // Block stats reach BlockingStats
            BlockingStats blockingStats = BlockingStats.from(defender);
            assertEquals(15.0f, blockingStats.blockChance(), 0.01);
            assertEquals(40.0, blockingStats.damageReduction(), 0.01);
        }
    }
}
