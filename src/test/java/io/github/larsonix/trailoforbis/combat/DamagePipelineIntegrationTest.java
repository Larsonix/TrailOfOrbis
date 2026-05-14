package io.github.larsonix.trailoforbis.combat;

import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.elemental.ElementType;
import io.github.larsonix.trailoforbis.elemental.ElementalStats;
import io.github.larsonix.trailoforbis.gear.model.WeaponImplicit;
import io.github.larsonix.trailoforbis.gear.stats.GearStatApplier;
import io.github.larsonix.trailoforbis.gear.stats.GearStatCalculator.GearBonuses;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the full damage pipeline.
 *
 * <p>These tests exercise the EXACT scenarios reported as broken:
 * <ol>
 *   <li>Fire implicit sword must produce fire damage, not void</li>
 *   <li>Skill tree bonuses (physDmg%, critMult) must change damage output</li>
 *   <li>Each element implicit must produce the correct damage type</li>
 *   <li>Elemental melee weapons must route base damage to element slot</li>
 * </ol>
 *
 * <p>Each test constructs real objects (no mocks) and verifies the calculator output.
 */
class DamagePipelineIntegrationTest {

    private RPGDamageCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new RPGDamageCalculator();
    }

    // =========================================================================
    // WEAPON IMPLICIT → ELEMENT ROUTING
    // =========================================================================

    @Nested
    @DisplayName("WeaponImplicit Element Resolution")
    class WeaponImplicitElementTests {

        @Test
        @DisplayName("fire_damage implicit returns FIRE element")
        void fireImplicit_returnsFireElement() {
            WeaponImplicit implicit = WeaponImplicit.of("fire_damage", 50, 100, 75);
            assertEquals(ElementType.FIRE, implicit.getSpellElement());
        }

        @Test
        @DisplayName("void_damage implicit returns VOID element")
        void voidImplicit_returnsVoidElement() {
            WeaponImplicit implicit = WeaponImplicit.of("void_damage", 50, 100, 75);
            assertEquals(ElementType.VOID, implicit.getSpellElement());
        }

        @Test
        @DisplayName("physical_damage implicit returns null element")
        void physicalImplicit_returnsNullElement() {
            WeaponImplicit implicit = WeaponImplicit.of("physical_damage", 50, 100, 75);
            assertNull(implicit.getSpellElement());
        }

        @Test
        @DisplayName("All 6 elements map correctly")
        void allElements_mapCorrectly() {
            assertEquals(ElementType.FIRE, WeaponImplicit.of("fire_damage", 1, 1, 1).getSpellElement());
            assertEquals(ElementType.WATER, WeaponImplicit.of("water_damage", 1, 1, 1).getSpellElement());
            assertEquals(ElementType.LIGHTNING, WeaponImplicit.of("lightning_damage", 1, 1, 1).getSpellElement());
            assertEquals(ElementType.EARTH, WeaponImplicit.of("earth_damage", 1, 1, 1).getSpellElement());
            assertEquals(ElementType.WIND, WeaponImplicit.of("wind_damage", 1, 1, 1).getSpellElement());
            assertEquals(ElementType.VOID, WeaponImplicit.of("void_damage", 1, 1, 1).getSpellElement());
        }
    }

    // =========================================================================
    // FIRE SWORD → DAMAGE CALCULATOR → FIRE OUTPUT
    // =========================================================================

    @Nested
    @DisplayName("Fire Sword Damage Pipeline")
    class FireSwordTests {

        @Test
        @DisplayName("CRITICAL: Fire sword produces FIRE damage type, not VOID")
        void fireSword_producesFire_notVoid() {
            // Simulate a fire sword: 70 base fire damage, melee attack
            ComputedStats attacker = ComputedStats.builder()
                .weaponBaseDamage(70f)
                .weaponSpellElement(ElementType.FIRE)
                .holdingRpgGear(true)
                .criticalChance(0f) // No crit to keep it deterministic
                .criticalMultiplier(150f)
                .build();

            DamageBreakdown result = calculator.calculate(
                70f,                    // baseDamage
                attacker,               // attackerStats
                new ElementalStats(),   // attackerElemental (empty)
                null,                   // defenderStats (no defenses)
                null,                   // defenderElemental
                AttackType.MELEE,       // attackType
                false,                  // isDOT
                1.0f,                   // conditionalMultiplier
                false,                  // traceEnabled
                ElementType.FIRE,       // spellElement — THE KEY INPUT
                1,                      // attackerLevel
                false                   // projectileSpell
            );

            // The damage type MUST be FIRE
            assertEquals(DamageType.FIRE, result.damageType(),
                "Fire sword must produce FIRE damage type");

            // Fire damage must be > 0
            float fireDamage = result.getElementalDamage(ElementType.FIRE);
            assertTrue(fireDamage > 0,
                "Fire elemental damage must be > 0, got: " + fireDamage);

            // Void damage must be 0
            float voidDamage = result.getElementalDamage(ElementType.VOID);
            assertEquals(0f, voidDamage, 0.001f,
                "Void damage must be 0 for a fire sword, got: " + voidDamage);

            // Physical should only be flat physical from stats (which is 0 here)
            assertEquals(0f, result.physicalDamage(), 0.001f,
                "Physical damage must be 0 for elemental sword with no flat phys");
        }

        @Test
        @DisplayName("Fire sword with flat physical produces both fire AND physical")
        void fireSword_withFlatPhysical_producesBothTypes() {
            ComputedStats attacker = ComputedStats.builder()
                .weaponBaseDamage(70f)
                .weaponSpellElement(ElementType.FIRE)
                .holdingRpgGear(true)
                .physicalDamage(20f)    // Flat physical from STR/gear
                .criticalChance(0f)
                .criticalMultiplier(150f)
                .build();

            DamageBreakdown result = calculator.calculate(
                70f, attacker, new ElementalStats(), null, null,
                AttackType.MELEE, false, 1.0f, false, ElementType.FIRE, 1, false
            );

            // Fire should be the primary (70 > 20)
            assertEquals(DamageType.FIRE, result.damageType(),
                "Fire should be primary when base fire (70) > flat physical (20)");

            assertTrue(result.getElementalDamage(ElementType.FIRE) > 0,
                "Must have fire damage");
            assertTrue(result.physicalDamage() > 0,
                "Must have physical damage from flat bonus");
        }

        @Test
        @DisplayName("Fire sword: void conversion does NOT affect the fire base damage")
        void fireSword_voidConversion_doesNotConvertFireBase() {
            // Player has 50% void conversion from skill tree
            ComputedStats attacker = ComputedStats.builder()
                .weaponBaseDamage(70f)
                .weaponSpellElement(ElementType.FIRE)
                .holdingRpgGear(true)
                .voidConversion(50f)    // 50% phys→void conversion
                .criticalChance(0f)
                .criticalMultiplier(150f)
                .build();

            DamageBreakdown result = calculator.calculate(
                70f, attacker, new ElementalStats(), null, null,
                AttackType.MELEE, false, 1.0f, false, ElementType.FIRE, 1, false
            );

            // Fire base should be untouched (conversion only affects PHYSICAL slot)
            float fireDamage = result.getElementalDamage(ElementType.FIRE);
            assertEquals(70f, fireDamage, 0.5f,
                "Fire base damage must not be affected by physical→void conversion");

            // Void should be 0 (no physical to convert)
            float voidDamage = result.getElementalDamage(ElementType.VOID);
            assertEquals(0f, voidDamage, 0.001f,
                "Void damage must be 0 when there's no physical damage to convert");
        }

        @Test
        @DisplayName("Fire sword + flat phys + void conversion = fire + void (not all void)")
        void fireSword_withPhysAndVoidConversion_producesBoth() {
            ComputedStats attacker = ComputedStats.builder()
                .weaponBaseDamage(70f)
                .weaponSpellElement(ElementType.FIRE)
                .holdingRpgGear(true)
                .physicalDamage(40f)     // 40 flat physical
                .voidConversion(100f)    // 100% phys→void
                .criticalChance(0f)
                .criticalMultiplier(150f)
                .build();

            DamageBreakdown result = calculator.calculate(
                70f, attacker, new ElementalStats(), null, null,
                AttackType.MELEE, false, 1.0f, false, ElementType.FIRE, 1, false
            );

            // Fire: 70 base (untouched by conversion)
            float fireDamage = result.getElementalDamage(ElementType.FIRE);
            assertTrue(fireDamage >= 69f && fireDamage <= 71f,
                "Fire damage should be ~70 (base), got: " + fireDamage);

            // Void: 40 (converted from physical)
            float voidDamage = result.getElementalDamage(ElementType.VOID);
            assertTrue(voidDamage >= 39f && voidDamage <= 41f,
                "Void damage should be ~40 (converted physical), got: " + voidDamage);

            // Physical: 0 (all converted to void)
            assertEquals(0f, result.physicalDamage(), 0.5f,
                "Physical should be 0 after 100% void conversion");

            // Primary type should still be FIRE (70 > 40)
            assertEquals(DamageType.FIRE, result.damageType(),
                "Fire (70) should dominate over void (40)");
        }
    }

    // =========================================================================
    // ALL ELEMENTS PRODUCE CORRECT DAMAGE TYPE
    // =========================================================================

    @Nested
    @DisplayName("Element Routing for All Types")
    class AllElementRoutingTests {

        @Test
        @DisplayName("Each element implicit routes base damage to correct slot")
        void allElements_routeToCorrectSlot() {
            for (ElementType element : ElementType.values()) {
                ComputedStats attacker = ComputedStats.builder()
                    .weaponBaseDamage(100f)
                    .weaponSpellElement(element)
                    .holdingRpgGear(true)
                    .criticalChance(0f)
                    .criticalMultiplier(150f)
                    .build();

                DamageBreakdown result = calculator.calculate(
                    100f, attacker, new ElementalStats(), null, null,
                    AttackType.MELEE, false, 1.0f, false, element, 1, false
                );

                DamageType expectedType = DamageType.fromElement(element);
                assertEquals(expectedType, result.damageType(),
                    element.name() + " sword must produce " + expectedType + " damage type");

                float elemDamage = result.getElementalDamage(element);
                assertTrue(elemDamage > 0,
                    element.name() + " damage must be > 0, got: " + elemDamage);

                // Other elements must be 0
                for (ElementType other : ElementType.values()) {
                    if (other != element) {
                        assertEquals(0f, result.getElementalDamage(other), 0.001f,
                            other.name() + " must be 0 when weapon is " + element.name());
                    }
                }
            }
        }

        @Test
        @DisplayName("Physical sword produces PHYSICAL damage type")
        void physicalSword_producesPhysical() {
            ComputedStats attacker = ComputedStats.builder()
                .weaponBaseDamage(100f)
                .holdingRpgGear(true)
                .criticalChance(0f)
                .criticalMultiplier(150f)
                .build();

            DamageBreakdown result = calculator.calculate(
                100f, attacker, new ElementalStats(), null, null,
                AttackType.MELEE, false, 1.0f, false,
                null,  // No spell element = physical
                1, false
            );

            assertEquals(DamageType.PHYSICAL, result.damageType());
            assertTrue(result.physicalDamage() > 0);
        }
    }

    // =========================================================================
    // SKILL TREE BONUSES AFFECT DAMAGE OUTPUT
    // =========================================================================

    @Nested
    @DisplayName("Skill Tree Stats Affect Combat")
    class SkillTreeEffectTests {

        @Test
        @DisplayName("physicalDamagePercent increases physical damage")
        void physDmgPercent_increasesDamage() {
            // Without bonus
            ComputedStats noBonus = ComputedStats.builder()
                .weaponBaseDamage(100f)
                .holdingRpgGear(true)
                .physicalDamagePercent(0f)
                .criticalChance(0f)
                .criticalMultiplier(150f)
                .build();

            DamageBreakdown baseResult = calculator.calculate(
                100f, noBonus, new ElementalStats(), null, null,
                AttackType.MELEE, false, 1.0f, false, null, 1, false
            );

            // With 50% physical damage bonus (like allocating fire skill tree nodes)
            ComputedStats withBonus = ComputedStats.builder()
                .weaponBaseDamage(100f)
                .holdingRpgGear(true)
                .physicalDamagePercent(50f)  // +50% from skill tree
                .criticalChance(0f)
                .criticalMultiplier(150f)
                .build();

            DamageBreakdown bonusResult = calculator.calculate(
                100f, withBonus, new ElementalStats(), null, null,
                AttackType.MELEE, false, 1.0f, false, null, 1, false
            );

            assertTrue(bonusResult.physicalDamage() > baseResult.physicalDamage(),
                "50% physDmg% must increase damage. Base=" + baseResult.physicalDamage()
                + " Bonus=" + bonusResult.physicalDamage());

            // Should be approximately 1.5x
            float ratio = bonusResult.physicalDamage() / baseResult.physicalDamage();
            assertTrue(ratio > 1.45f && ratio < 1.55f,
                "Ratio should be ~1.5x, got: " + ratio);
        }

        @Test
        @DisplayName("meleeDamagePercent increases both physical AND elemental for melee")
        void meleeDmgPercent_increasesBothPhysAndElemental() {
            ElementalStats elemental = new ElementalStats();

            // Fire sword with melee damage bonus
            ComputedStats withMeleeBonus = ComputedStats.builder()
                .weaponBaseDamage(100f)
                .weaponSpellElement(ElementType.FIRE)
                .holdingRpgGear(true)
                .meleeDamagePercent(50f) // +50% melee from skill tree
                .criticalChance(0f)
                .criticalMultiplier(150f)
                .build();

            DamageBreakdown result = calculator.calculate(
                100f, withMeleeBonus, elemental, null, null,
                AttackType.MELEE, false, 1.0f, false, ElementType.FIRE, 1, false
            );

            // Fire damage should be increased by meleeDmgPercent (applied in step 4)
            // Base fire = 100, +50% melee = 150
            float fireDmg = result.getElementalDamage(ElementType.FIRE);
            assertTrue(fireDmg > 140f && fireDmg < 160f,
                "Fire damage with +50% melee should be ~150, got: " + fireDmg);
        }

        @Test
        @DisplayName("critMultiplier changes damage on crit")
        void critMultiplier_changesDamage() {
            ComputedStats lowCrit = ComputedStats.builder()
                .weaponBaseDamage(100f)
                .holdingRpgGear(true)
                .criticalChance(0f)
                .criticalMultiplier(150f) // 1.5x
                .build();

            ComputedStats highCrit = ComputedStats.builder()
                .weaponBaseDamage(100f)
                .holdingRpgGear(true)
                .criticalChance(0f)
                .criticalMultiplier(250f) // 2.5x (150 base + 100 from skill tree)
                .build();

            // Force crit for both
            DamageBreakdown lowResult = calculator.calculateWithForcedCrit(
                100f, lowCrit, new ElementalStats(), null, null,
                AttackType.MELEE, true, 1
            );
            DamageBreakdown highResult = calculator.calculateWithForcedCrit(
                100f, highCrit, new ElementalStats(), null, null,
                AttackType.MELEE, true, 1
            );

            assertTrue(highResult.physicalDamage() > lowResult.physicalDamage(),
                "Higher crit mult must produce more damage. Low=" + lowResult.physicalDamage()
                + " High=" + highResult.physicalDamage());
        }

        @Test
        @DisplayName("fireConversion converts physical to fire (not to void)")
        void fireConversion_convertsToFire() {
            ComputedStats attacker = ComputedStats.builder()
                .weaponBaseDamage(100f)
                .holdingRpgGear(true)
                .fireConversion(50f)  // 50% phys→fire from skill tree
                .criticalChance(0f)
                .criticalMultiplier(150f)
                .build();

            DamageBreakdown result = calculator.calculate(
                100f, attacker, new ElementalStats(), null, null,
                AttackType.MELEE, false, 1.0f, false, null, 1, false
            );

            // Physical: 100 - 50 = 50
            assertTrue(result.physicalDamage() >= 49f && result.physicalDamage() <= 51f,
                "Physical should be ~50 after 50% conversion, got: " + result.physicalDamage());

            // Fire: 50 (converted)
            float fireDmg = result.getElementalDamage(ElementType.FIRE);
            assertTrue(fireDmg >= 49f && fireDmg <= 51f,
                "Fire should be ~50 from conversion, got: " + fireDmg);

            // Void: 0
            assertEquals(0f, result.getElementalDamage(ElementType.VOID), 0.001f,
                "Void must be 0 when conversion is fire");
        }
    }

    // =========================================================================
    // COMPUTED STATS → ELEMENTAL STATS FLOW
    // =========================================================================

    @Nested
    @DisplayName("ComputedStats ↔ ElementalStats Integration")
    class StatsFlowTests {

        @Test
        @DisplayName("setFireDamage writes to toElementalStats().getFlatDamage(FIRE)")
        void setFireDamage_flowsToElementalStats() {
            ComputedStats stats = ComputedStats.builder().build();
            stats.setFireDamage(25.0);

            ElementalStats elemental = stats.toElementalStats();
            assertEquals(25.0, elemental.getFlatDamage(ElementType.FIRE), 0.001,
                "setFireDamage must be readable via toElementalStats()");
        }

        @Test
        @DisplayName("All element setters flow to toElementalStats()")
        void allElementSetters_flowToElementalStats() {
            ComputedStats stats = ComputedStats.builder().build();
            stats.setFireDamage(10);
            stats.setWaterDamage(20);
            stats.setLightningDamage(30);
            stats.setEarthDamage(40);
            stats.setWindDamage(50);
            stats.setVoidDamage(60);

            ElementalStats elemental = stats.toElementalStats();
            assertEquals(10.0, elemental.getFlatDamage(ElementType.FIRE), 0.001);
            assertEquals(20.0, elemental.getFlatDamage(ElementType.WATER), 0.001);
            assertEquals(30.0, elemental.getFlatDamage(ElementType.LIGHTNING), 0.001);
            assertEquals(40.0, elemental.getFlatDamage(ElementType.EARTH), 0.001);
            assertEquals(50.0, elemental.getFlatDamage(ElementType.WIND), 0.001);
            assertEquals(60.0, elemental.getFlatDamage(ElementType.VOID), 0.001);
        }

        @Test
        @DisplayName("toBuilder() preserves weaponSpellElement")
        void toBuilder_preservesWeaponElement() {
            ComputedStats original = ComputedStats.builder()
                .weaponBaseDamage(100f)
                .weaponSpellElement(ElementType.FIRE)
                .build();

            ComputedStats rebuilt = original.toBuilder().build();
            assertEquals(ElementType.FIRE, rebuilt.getWeaponSpellElement(),
                "toBuilder().build() must preserve weaponSpellElement");
        }

        @Test
        @DisplayName("toBuilder() preserves elemental damage values")
        void toBuilder_preservesElementalDamage() {
            ComputedStats original = ComputedStats.builder()
                .fireDamage(15f)
                .voidDamage(25f)
                .build();

            ComputedStats rebuilt = original.toBuilder().build();
            assertEquals(15f, rebuilt.getFireDamage(), 0.001f);
            assertEquals(25f, rebuilt.getVoidDamage(), 0.001f);
        }

        @Test
        @DisplayName("Fresh ComputedStats has null weaponSpellElement")
        void freshStats_hasNullElement() {
            ComputedStats stats = ComputedStats.builder().build();
            assertNull(stats.getWeaponSpellElement(),
                "Fresh stats must have null weaponSpellElement");
        }

        @Test
        @DisplayName("Fresh ComputedStats has zero elemental damage")
        void freshStats_hasZeroElementalDamage() {
            ComputedStats stats = ComputedStats.builder().build();
            for (ElementType elem : ElementType.values()) {
                assertEquals(0f, stats.toElementalStats().getFlatDamage(elem), 0.001f,
                    elem.name() + " flat damage must be 0 on fresh stats");
            }
        }
    }

    // =========================================================================
    // GEAR STAT APPLIER — WEAPON ELEMENT CLEARING
    // =========================================================================

    @Nested
    @DisplayName("GearStatApplier Weapon Element Clearing")
    class WeaponElementClearingTests {

        @Test
        @DisplayName("CRITICAL: Switching from fire sword to physical clears weaponSpellElement")
        void fireSwordToPhysical_clearsElement() {
            ComputedStats stats = ComputedStats.builder().build();

            // Apply fire sword gear
            GearBonuses fireGear = new GearBonuses(
                Map.of(), Map.of(), 70.0, "Weapon_Sword_Iron", null, true, ElementType.FIRE
            );
            new GearStatApplier().apply(stats, fireGear);
            assertEquals(ElementType.FIRE, stats.getWeaponSpellElement(),
                "After fire sword: element should be FIRE");

            // Switch to physical sword (null element)
            GearBonuses physGear = new GearBonuses(
                Map.of(), Map.of(), 80.0, "Weapon_Sword_Iron", null, true, null
            );
            new GearStatApplier().apply(stats, physGear);
            assertNull(stats.getWeaponSpellElement(),
                "After physical sword: element must be NULL (cleared)");
        }

        @Test
        @DisplayName("CRITICAL: Switching from fire to void updates weaponSpellElement")
        void fireSwordToVoid_updatesElement() {
            ComputedStats stats = ComputedStats.builder().build();

            // Apply fire sword
            GearBonuses fireGear = new GearBonuses(
                Map.of(), Map.of(), 70.0, "Weapon_Sword_Iron", null, true, ElementType.FIRE
            );
            new GearStatApplier().apply(stats, fireGear);
            assertEquals(ElementType.FIRE, stats.getWeaponSpellElement());

            // Switch to void sword
            GearBonuses voidGear = new GearBonuses(
                Map.of(), Map.of(), 65.0, "Weapon_Sword_Iron", null, true, ElementType.VOID
            );
            new GearStatApplier().apply(stats, voidGear);
            assertEquals(ElementType.VOID, stats.getWeaponSpellElement(),
                "After void sword: element must be VOID (updated from FIRE)");
        }

        @Test
        @DisplayName("Switching to empty hand (no gear) clears everything")
        void weaponToEmptyHand_clearsAll() {
            ComputedStats stats = ComputedStats.builder().build();

            // Apply fire sword
            GearBonuses fireGear = new GearBonuses(
                Map.of(), Map.of(), 70.0, "Weapon_Sword_Iron", null, true, ElementType.FIRE
            );
            new GearStatApplier().apply(stats, fireGear);

            // Switch to empty hand
            new GearStatApplier().apply(stats, GearBonuses.EMPTY);
            assertNull(stats.getWeaponSpellElement(),
                "Empty hand must clear weaponSpellElement");
            assertEquals(0.0, stats.getWeaponBaseDamage(), 0.01,
                "Empty hand must clear weaponBaseDamage");
            assertFalse(stats.isHoldingRpgGear(),
                "Empty hand must clear isHoldingRpgGear");
        }
    }

    // =========================================================================
    // DAMAGE ESTIMATE (Stats page / tooltip) VERIFICATION
    // =========================================================================

    @Nested
    @DisplayName("Damage Estimate Consistency")
    class DamageEstimateTests {

        @Test
        @DisplayName("Fire sword estimate shows nonzero average damage")
        void fireSwordEstimate_isNonzero() {
            ComputedStats stats = ComputedStats.builder()
                .weaponBaseDamage(70f)
                .weaponSpellElement(ElementType.FIRE)
                .weaponItemId("Weapon_Sword_Iron") // Needed for WeaponType detection
                .holdingRpgGear(true)
                .criticalChance(5f)
                .criticalMultiplier(150f)
                .build();

            RPGDamageCalculator.DamageEstimate estimate = calculator.estimateAverageDamage(stats);
            assertTrue(estimate.avgDamagePerHit() > 0,
                "Estimate must be > 0 for a fire sword, got: " + estimate.avgDamagePerHit());
            assertEquals(70f, estimate.weaponBase(), 0.001f);
        }

        @Test
        @DisplayName("Skill tree bonuses increase the estimate")
        void skillTreeBonuses_increaseEstimate() {
            ComputedStats base = ComputedStats.builder()
                .weaponBaseDamage(100f)
                .weaponItemId("Weapon_Sword_Iron")
                .holdingRpgGear(true)
                .criticalChance(5f)
                .criticalMultiplier(150f)
                .build();

            ComputedStats withTree = ComputedStats.builder()
                .weaponBaseDamage(100f)
                .weaponItemId("Weapon_Sword_Iron")
                .holdingRpgGear(true)
                .physicalDamagePercent(30f) // From fire skill tree
                .criticalChance(5f)
                .criticalMultiplier(170f)   // +20 from fire tree
                .build();

            RPGDamageCalculator.DamageEstimate baseEst = calculator.estimateAverageDamage(base);
            RPGDamageCalculator.DamageEstimate treeEst = calculator.estimateAverageDamage(withTree);

            assertTrue(treeEst.avgDamagePerHit() > baseEst.avgDamagePerHit(),
                "Skill tree bonuses must increase estimate. Base=" + baseEst.avgDamagePerHit()
                + " WithTree=" + treeEst.avgDamagePerHit());
        }
    }
}
