package io.github.larsonix.trailoforbis.maps.spawning;

import io.github.larsonix.trailoforbis.maps.spawning.WeightedMob.MobClassification;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link WeightedMob}.
 *
 * <p>Note: Elite is now a spawn-time modifier, not a classification.
 * The MobClassification enum only contains NORMAL and BOSS. Elite status
 * is tracked separately via the isElite flag on spawned mobs.
 */
@DisplayName("WeightedMob Tests")
class WeightedMobTest {

    // ═══════════════════════════════════════════════════════════════════
    // FACTORY METHOD TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("normal() creates mob with NORMAL classification")
    void normalCreatesNormalClassification() {
        WeightedMob mob = WeightedMob.normal("trork_warrior", 30);

        assertEquals("trork_warrior", mob.mobTypeId());
        assertEquals(30, mob.weight());
        assertEquals(MobClassification.NORMAL, mob.classification());
        assertEquals(1, mob.minLevel());
        assertEquals(WeightedMob.NO_LEVEL_CAP, mob.maxLevel());
        assertTrue(mob.isNormal());
        assertFalse(mob.isBoss());
    }

    @Test
    @DisplayName("boss() creates mob with BOSS classification")
    void bossBossClassification() {
        WeightedMob mob = WeightedMob.boss("trork_chieftain", 5);

        assertEquals("trork_chieftain", mob.mobTypeId());
        assertEquals(5, mob.weight());
        assertEquals(MobClassification.BOSS, mob.classification());
        assertTrue(mob.isBoss());
    }

    @Test
    @DisplayName("Factory methods with level range create proper restrictions")
    void factoryMethodsWithLevelRange() {
        WeightedMob mob = WeightedMob.normal("fire_elemental", 20, 50, 100);

        assertEquals(50, mob.minLevel());
        assertEquals(100, mob.maxLevel());
        assertTrue(mob.hasLevelRestriction());
    }

    @Test
    @DisplayName("of() infers BOSS classification from naming")
    void ofInfersBossFromNaming() {
        WeightedMob mob = WeightedMob.of("void_emperor", 10);
        assertEquals(MobClassification.BOSS, mob.classification());

        mob = WeightedMob.of("magma_lord", 10);
        assertEquals(MobClassification.BOSS, mob.classification());

        mob = WeightedMob.of("ice_king", 10);
        assertEquals(MobClassification.BOSS, mob.classification());
    }

    @Test
    @DisplayName("of() defaults to NORMAL for non-boss names")
    void ofDefaultsToNormal() {
        // Captain, giant, champion names now go to NORMAL (elite is a spawn-time modifier)
        WeightedMob mob = WeightedMob.of("trork_warrior", 10);
        assertEquals(MobClassification.NORMAL, mob.classification());

        mob = WeightedMob.of("trork_captain", 10);
        assertEquals(MobClassification.NORMAL, mob.classification());

        mob = WeightedMob.of("frost_giant", 10);
        assertEquals(MobClassification.NORMAL, mob.classification());
    }

    // ═══════════════════════════════════════════════════════════════════
    // VALIDATION TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Constructor rejects null mobTypeId")
    void rejectsNullMobTypeId() {
        assertThrows(NullPointerException.class, () ->
            new WeightedMob(null, 10, MobClassification.NORMAL, 1, -1));
    }

    @Test
    @DisplayName("Constructor rejects blank mobTypeId")
    void rejectsBlankMobTypeId() {
        assertThrows(IllegalArgumentException.class, () ->
            WeightedMob.normal("   ", 10));
    }

    @Test
    @DisplayName("Constructor rejects zero weight")
    void rejectsZeroWeight() {
        assertThrows(IllegalArgumentException.class, () ->
            WeightedMob.normal("trork", 0));
    }

    @Test
    @DisplayName("Constructor rejects negative weight")
    void rejectsNegativeWeight() {
        assertThrows(IllegalArgumentException.class, () ->
            WeightedMob.normal("trork", -5));
    }

    @Test
    @DisplayName("Constructor rejects negative minLevel")
    void rejectsNegativeMinLevel() {
        assertThrows(IllegalArgumentException.class, () ->
            WeightedMob.normal("trork", 10, -1, 100));
    }

    @Test
    @DisplayName("Constructor rejects maxLevel less than minLevel")
    void rejectsInvalidLevelRange() {
        assertThrows(IllegalArgumentException.class, () ->
            WeightedMob.normal("trork", 10, 50, 25));
    }

    // ═══════════════════════════════════════════════════════════════════
    // LEVEL RESTRICTION TESTS
    // ═══════════════════════════════════════════════════════════════════

    @ParameterizedTest
    @CsvSource({
        "1, true",    // At minimum level
        "50, true",   // In range
        "100, true",  // At maximum level
        "0, false",   // Below minimum (minLevel is 1 by default)
        "101, true"   // Above maximum (no cap)
    })
    @DisplayName("canSpawnAtLevel with no level cap")
    void canSpawnAtLevelNoCap(int level, boolean expected) {
        WeightedMob mob = WeightedMob.normal("trork", 10);
        assertEquals(expected, mob.canSpawnAtLevel(level));
    }

    @ParameterizedTest
    @CsvSource({
        "50, true",   // At minimum level
        "75, true",   // In range
        "100, true",  // At maximum level
        "49, false",  // Below minimum
        "101, false"  // Above maximum
    })
    @DisplayName("canSpawnAtLevel with level restrictions")
    void canSpawnAtLevelWithRestrictions(int level, boolean expected) {
        WeightedMob mob = WeightedMob.normal("fire_elemental", 10, 50, 100);
        assertEquals(expected, mob.canSpawnAtLevel(level));
    }

    @Test
    @DisplayName("hasLevelRestriction detects restrictions correctly")
    void hasLevelRestrictionDetection() {
        // No restriction (default minLevel=1, no cap)
        WeightedMob noRestriction = WeightedMob.normal("trork", 10);
        assertFalse(noRestriction.hasLevelRestriction());

        // Has minimum level restriction
        WeightedMob minRestriction = WeightedMob.normal("fire_elemental", 10, 50, -1);
        assertTrue(minRestriction.hasLevelRestriction());

        // Has maximum level restriction
        WeightedMob maxRestriction = WeightedMob.normal("weak_mob", 10, 1, 25);
        assertTrue(maxRestriction.hasLevelRestriction());
    }

    // ═══════════════════════════════════════════════════════════════════
    // CLASSIFICATION MULTIPLIER TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("NORMAL classification has baseline multipliers")
    void normalClassificationMultipliers() {
        WeightedMob mob = WeightedMob.normal("trork", 10);
        assertEquals(1.0f, mob.getStatMultiplier());
        assertEquals(1.0f, mob.getXpMultiplier());
    }

    @Test
    @DisplayName("BOSS classification has highest multipliers")
    void bossClassificationMultipliers() {
        WeightedMob mob = WeightedMob.boss("trork_chieftain", 10);
        assertEquals(5.0f, mob.getStatMultiplier());
        assertEquals(10.0f, mob.getXpMultiplier());
    }

    @Test
    @DisplayName("Elite multiplier constants are defined correctly")
    void eliteMultiplierConstants() {
        // Elite is now a spawn-time modifier, not a classification
        // These static constants are used when applying elite status
        assertEquals(2.0f, WeightedMob.ELITE_STAT_MULTIPLIER);
        assertEquals(1.5f, WeightedMob.ELITE_HEALTH_MULTIPLIER);
        assertEquals(3.0f, WeightedMob.ELITE_XP_MULTIPLIER);
    }

    // ═══════════════════════════════════════════════════════════════════
    // TRANSFORMATION TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("withWeight creates copy with new weight")
    void withWeightCreatesNewCopy() {
        WeightedMob original = WeightedMob.normal("trork", 10);
        WeightedMob modified = original.withWeight(50);

        assertEquals(10, original.weight());
        assertEquals(50, modified.weight());
        assertEquals(original.mobTypeId(), modified.mobTypeId());
        assertEquals(original.classification(), modified.classification());
    }

    @Test
    @DisplayName("withLevelRange creates copy with new levels")
    void withLevelRangeCreatesNewCopy() {
        WeightedMob original = WeightedMob.normal("trork", 10);
        WeightedMob modified = original.withLevelRange(25, 75);

        assertEquals(1, original.minLevel());
        assertEquals(WeightedMob.NO_LEVEL_CAP, original.maxLevel());
        assertEquals(25, modified.minLevel());
        assertEquals(75, modified.maxLevel());
    }

    @Test
    @DisplayName("withClassification creates copy with new classification")
    void withClassificationCreatesNewCopy() {
        WeightedMob original = WeightedMob.normal("trork", 10);
        WeightedMob modified = original.withClassification(MobClassification.BOSS);

        assertEquals(MobClassification.NORMAL, original.classification());
        assertEquals(MobClassification.BOSS, modified.classification());
    }

    // ═══════════════════════════════════════════════════════════════════
    // TOSTRING TEST
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("toString formats correctly")
    void toStringFormat() {
        WeightedMob mob = WeightedMob.normal("trork_warrior", 30, 10, 50);
        String str = mob.toString();

        assertTrue(str.contains("trork_warrior"));
        assertTrue(str.contains("30"));
        assertTrue(str.contains("NORMAL"));
        assertTrue(str.contains("L10-50"));
    }

    @Test
    @DisplayName("toString handles unlimited max level")
    void toStringUnlimitedMaxLevel() {
        WeightedMob mob = WeightedMob.normal("trork_warrior", 30);
        String str = mob.toString();

        assertTrue(str.contains("L1+"));
    }
}
