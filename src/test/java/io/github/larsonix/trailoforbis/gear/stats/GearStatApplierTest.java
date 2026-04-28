package io.github.larsonix.trailoforbis.gear.stats;

import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.gear.stats.GearStatCalculator.GearBonuses;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for GearStatApplier.
 */
class GearStatApplierTest {

    private GearStatApplier applier;

    @BeforeEach
    void setUp() {
        applier = new GearStatApplier();
    }

    @Test
    @DisplayName("apply - empty bonuses makes no changes")
    void apply_EmptyBonuses_NoChanges() {
        ComputedStats stats = new ComputedStats();
        stats.setMaxHealth(100);
        double originalHealth = stats.getMaxHealth();

        applier.apply(stats, GearBonuses.EMPTY);

        assertEquals(originalHealth, stats.getMaxHealth(), 0.01);
    }

    @Test
    @DisplayName("apply - flat bonuses applied first, then percent")
    void apply_FlatBonusesFirst() {
        ComputedStats stats = new ComputedStats();
        stats.setMaxHealth(100);

        GearBonuses bonuses = new GearBonuses(
            Map.of("max_health", 50.0),  // Flat +50
            Map.of("max_health", 50.0),  // Percent +50%
            0.0,  // No weapon base damage
            null, // No weapon item ID
            false // Not RPG gear
        );

        applier.apply(stats, bonuses);

        // Flat first: 100 + 50 = 150
        // Then percent: 150 * 1.5 = 225
        assertEquals(225, stats.getMaxHealth(), 0.01);
    }

    @Test
    @DisplayName("apply - multiple flat bonuses")
    void apply_MultipleFlatBonuses() {
        ComputedStats stats = new ComputedStats();
        stats.setMaxHealth(100);
        stats.setMaxMana(50);

        GearBonuses bonuses = new GearBonuses(
            Map.of("max_health", 50.0, "max_mana", 25.0),
            Map.of(),
            0.0,  // No weapon base damage
            null, // No weapon item ID
            false // Not RPG gear
        );

        applier.apply(stats, bonuses);

        assertEquals(150, stats.getMaxHealth(), 0.01);
        assertEquals(75, stats.getMaxMana(), 0.01);
    }

    @Test
    @DisplayName("apply - null stats throws exception")
    void apply_NullStats_ThrowsException() {
        assertThrows(NullPointerException.class, () ->
            applier.apply(null, GearBonuses.EMPTY));
    }

    @Test
    @DisplayName("apply - null bonuses throws exception")
    void apply_NullBonuses_ThrowsException() {
        assertThrows(NullPointerException.class, () ->
            applier.apply(new ComputedStats(), null));
    }

    @Test
    @DisplayName("createSummary - empty bonuses returns 'No gear bonuses'")
    void createSummary_EmptyBonuses_ReturnsNoBonus() {
        String summary = applier.createSummary(GearBonuses.EMPTY);
        assertEquals("No gear bonuses", summary);
    }

    @Test
    @DisplayName("createSummary - with bonuses lists all")
    void createSummary_WithBonuses_ListsAll() {
        GearBonuses bonuses = new GearBonuses(
            Map.of("physical_damage", 10.0),
            Map.of("crit_chance", 5.0),
            0.0,
            null,
            false
        );

        String summary = applier.createSummary(bonuses);

        assertTrue(summary.contains("physical_damage"));
        assertTrue(summary.contains("crit_chance"));
    }

    @Test
    @DisplayName("apply - only flat bonuses applied correctly")
    void apply_OnlyFlatBonuses() {
        ComputedStats stats = new ComputedStats();
        stats.setPhysicalDamage(10);

        GearBonuses bonuses = new GearBonuses(
            Map.of("physical_damage", 15.0),
            Map.of(),
            0.0,
            null,
            false
        );

        applier.apply(stats, bonuses);

        assertEquals(25, stats.getPhysicalDamage(), 0.01);
    }

    @Test
    @DisplayName("apply - only percent bonuses applied correctly")
    void apply_OnlyPercentBonuses() {
        ComputedStats stats = new ComputedStats();
        stats.setCritChance(5);

        GearBonuses bonuses = new GearBonuses(
            Map.of(),
            Map.of("crit_chance", 10.0),
            0.0,
            null,
            false
        );

        applier.apply(stats, bonuses);

        assertEquals(15, stats.getCriticalChance(), 0.01);
    }

    @Test
    @DisplayName("apply - multiple percent bonuses stack additively")
    void apply_MultiplePercentBonuses_StackAdditively() {
        ComputedStats stats = new ComputedStats();
        stats.setFireResistance(10);
        stats.setWaterResistance(20);

        GearBonuses bonuses = new GearBonuses(
            Map.of(),
            Map.of("fire_resistance", 5.0, "water_resistance", 10.0),
            0.0,
            null,
            false
        );

        applier.apply(stats, bonuses);

        assertEquals(15, stats.getFireResistance(), 0.01);
        assertEquals(30, stats.getWaterResistance(), 0.01);
    }

    @Test
    @DisplayName("apply - weapon base damage is set on stats")
    void apply_WeaponBaseDamage_SetOnStats() {
        ComputedStats stats = new ComputedStats();

        GearBonuses bonuses = new GearBonuses(
            Map.of(),
            Map.of(),
            150.0,  // Weapon deals 150 base damage
            null,   // No weapon item ID
            true    // Is RPG gear
        );

        applier.apply(stats, bonuses);

        assertEquals(150.0, stats.getWeaponBaseDamage(), 0.01);
    }

    @Test
    @DisplayName("apply - weapon base damage set even when no other bonuses")
    void apply_WeaponBaseDamage_EvenWithEmptyBonuses() {
        ComputedStats stats = new ComputedStats();
        stats.setWeaponBaseDamage(0);  // Explicitly start at 0

        GearBonuses bonuses = new GearBonuses(
            Map.of(),
            Map.of(),
            200.0,  // Weapon deals 200 base damage
            null,   // No weapon item ID
            true    // Is RPG gear
        );

        applier.apply(stats, bonuses);

        assertEquals(200.0, stats.getWeaponBaseDamage(), 0.01);
    }

    // =========================================================================
    // REGRESSION TESTS FOR EMPTY MODIFIER BUGS
    // =========================================================================

    /**
     * CRITICAL REGRESSION TEST for commit d0c6523.
     *
     * <p>The bug was that weapons with empty modifier lists (no prefixes/suffixes)
     * were not applying their implicit damage. The fix ensures that:
     * <ul>
     *   <li>Weapon base damage is ALWAYS set, even with no modifiers</li>
     *   <li>isHoldingRpgGear flag is ALWAYS set correctly</li>
     *   <li>The damage path doesn't skip RPG calculation just because modifiers are empty</li>
     * </ul>
     */
    @Test
    @DisplayName("REGRESSION: Weapon with empty modifiers still applies implicit damage (commit d0c6523)")
    void apply_EmptyModifiers_StillAppliesImplicitDamage() {
        ComputedStats stats = new ComputedStats();

        // Simulate a weapon with implicit damage but NO modifiers
        // This is the exact scenario that was broken before the fix
        GearBonuses bonuses = new GearBonuses(
            Map.of(),  // Empty flat bonuses (no prefixes)
            Map.of(),  // Empty percent bonuses (no suffixes)
            150.0,     // Implicit damage from weapon type + level scaling
            "Weapon_Sword_Iron",  // Weapon item ID for profile lookup
            true       // IS RPG gear (critical flag!)
        );

        applier.apply(stats, bonuses);

        assertEquals(150.0, stats.getWeaponBaseDamage(), 0.01,
            "REGRESSION: Weapon base damage must be set even with empty modifier list!");
        assertEquals("Weapon_Sword_Iron", stats.getWeaponItemId(),
            "REGRESSION: Weapon item ID must be set for attack effectiveness!");
        assertTrue(stats.isHoldingRpgGear(),
            "REGRESSION: isHoldingRpgGear flag must be true for RPG damage path!");
    }

    @Test
    @DisplayName("REGRESSION: isHoldingRpgGear flag set correctly regardless of modifiers")
    void apply_EmptyModifiers_RpgGearFlagSetCorrectly() {
        ComputedStats stats = new ComputedStats();

        // RPG weapon with no modifiers
        GearBonuses rpgWeapon = new GearBonuses(
            Map.of(), Map.of(), 100.0, "Weapon_Dagger_Steel", true
        );

        // Vanilla weapon (not RPG gear)
        GearBonuses vanillaWeapon = new GearBonuses(
            Map.of(), Map.of(), 0.0, null, false
        );

        applier.apply(stats, rpgWeapon);
        assertTrue(stats.isHoldingRpgGear(),
            "RPG weapon (even with 0 modifiers) must set isHoldingRpgGear=true");

        applier.apply(stats, vanillaWeapon);
        assertFalse(stats.isHoldingRpgGear(),
            "Vanilla weapon must set isHoldingRpgGear=false");
    }

    @Test
    @DisplayName("REGRESSION: Armor with empty modifier list preserves base stats")
    void apply_ArmorEmptyModifiers_PreservesBaseStats() {
        ComputedStats stats = new ComputedStats();
        stats.setArmor(100);  // Base armor from attributes

        // Simulate armor piece with no modifiers (Common rarity with min roll)
        GearBonuses emptyArmor = new GearBonuses(
            Map.of(),  // No flat bonuses
            Map.of(),  // No percent bonuses
            0.0,       // Not a weapon
            null,      // Not a weapon
            false      // Not RPG gear (armor has different handling)
        );

        applier.apply(stats, emptyArmor);

        assertEquals(100, stats.getArmor(), 0.01,
            "REGRESSION: Empty modifier armor must not corrupt base armor value!");
    }

    @Test
    @DisplayName("REGRESSION: Weapon item ID preserved even with empty bonuses")
    void apply_EmptyBonuses_WeaponItemIdPreserved() {
        ComputedStats stats = new ComputedStats();

        GearBonuses bonuses = new GearBonuses(
            Map.of(),
            Map.of(),
            75.0,  // Implicit damage
            "Weapon_Staff_Void",  // Important for vanilla profile lookup
            true
        );

        applier.apply(stats, bonuses);

        assertEquals("Weapon_Staff_Void", stats.getWeaponItemId(),
            "REGRESSION: Weapon item ID must be set for attack type multiplier lookup!");
    }

    @Test
    @DisplayName("Weapon with only implicit (no modifiers) still works in damage calculation")
    void apply_OnlyImplicit_DamageCalculationWorks() {
        ComputedStats stats = new ComputedStats();

        // Level 100 weapon with implicit damage only (no rolled modifiers)
        // This represents: Common rarity, rolled 0 prefixes, 0 suffixes
        GearBonuses implicitOnlyWeapon = new GearBonuses(
            Map.of(),  // Empty - no flat modifiers
            Map.of(),  // Empty - no percent modifiers
            250.0,     // Implicit damage scaled for level 100
            "Weapon_Longsword_Obsidian",
            true
        );

        applier.apply(stats, implicitOnlyWeapon);

        // Verify all damage-relevant fields are set
        assertEquals(250.0, stats.getWeaponBaseDamage(), 0.01,
            "Implicit damage should be weapon base damage");
        assertNotNull(stats.getWeaponItemId(),
            "Weapon item ID should be set");
        assertTrue(stats.isHoldingRpgGear(),
            "Should be flagged as RPG gear");

        // These should be unchanged (0) since we have no modifiers
        assertEquals(0, stats.getPhysicalDamage(), 0.01,
            "No flat physical damage bonus");
        assertEquals(0, stats.getPhysicalDamagePercent(), 0.01,
            "No percent physical damage bonus");
    }
}
