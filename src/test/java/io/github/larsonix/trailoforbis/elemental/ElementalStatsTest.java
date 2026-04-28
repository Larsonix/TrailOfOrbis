package io.github.larsonix.trailoforbis.elemental;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ElementalStats} container class.
 *
 * <p>Validates storage and retrieval of elemental damage and resistance values.
 */
@DisplayName("ElementalStats")
class ElementalStatsTest {

    private ElementalStats stats;

    @BeforeEach
    void setUp() {
        stats = new ElementalStats();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Initialization Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Initialization")
    class InitializationTests {

        @Test
        @DisplayName("New stats have all flat damage at 0")
        void newStats_allFlatDamageIsZero() {
            for (ElementType type : ElementType.values()) {
                assertEquals(0.0, stats.getFlatDamage(type), 0.001);
            }
        }

        @Test
        @DisplayName("New stats have all percent damage at 0")
        void newStats_allPercentDamageIsZero() {
            for (ElementType type : ElementType.values()) {
                assertEquals(0.0, stats.getPercentDamage(type), 0.001);
            }
        }

        @Test
        @DisplayName("New stats have all multiplier damage at 0")
        void newStats_allMultiplierDamageIsZero() {
            for (ElementType type : ElementType.values()) {
                assertEquals(0.0, stats.getMultiplierDamage(type), 0.001);
            }
        }

        @Test
        @DisplayName("New stats have all resistance at 0")
        void newStats_allResistanceIsZero() {
            for (ElementType type : ElementType.values()) {
                assertEquals(0.0, stats.getResistance(type), 0.001);
            }
        }

        @Test
        @DisplayName("New stats have all penetration at 0")
        void newStats_allPenetrationIsZero() {
            for (ElementType type : ElementType.values()) {
                assertEquals(0.0, stats.getPenetration(type), 0.001);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Setter/Getter Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Setters and Getters")
    class SetterGetterTests {

        @Test
        @DisplayName("setFlatDamage and getFlatDamage work correctly")
        void setFlatDamage_andGetFlatDamage_workCorrectly() {
            stats.setFlatDamage(ElementType.FIRE, 100.0);
            assertEquals(100.0, stats.getFlatDamage(ElementType.FIRE), 0.001);
        }

        @Test
        @DisplayName("setPercentDamage and getPercentDamage work correctly")
        void setPercentDamage_andGetPercentDamage_workCorrectly() {
            stats.setPercentDamage(ElementType.WATER, 50.0);
            assertEquals(50.0, stats.getPercentDamage(ElementType.WATER), 0.001);
        }

        @Test
        @DisplayName("setMultiplierDamage and getMultiplierDamage work correctly")
        void setMultiplierDamage_andGetMultiplierDamage_workCorrectly() {
            stats.setMultiplierDamage(ElementType.LIGHTNING, 30.0);
            assertEquals(30.0, stats.getMultiplierDamage(ElementType.LIGHTNING), 0.001);
        }

        @Test
        @DisplayName("setResistance and getResistance work correctly")
        void setResistance_andGetResistance_workCorrectly() {
            stats.setResistance(ElementType.VOID, 60.0);
            assertEquals(60.0, stats.getResistance(ElementType.VOID), 0.001);
        }

        @Test
        @DisplayName("setPenetration and getPenetration work correctly")
        void setPenetration_andGetPenetration_workCorrectly() {
            stats.setPenetration(ElementType.FIRE, 20.0);
            assertEquals(20.0, stats.getPenetration(ElementType.FIRE), 0.001);
        }

        @Test
        @DisplayName("Setting one element doesn't affect others")
        void settingOneElement_doesNotAffectOthers() {
            stats.setFlatDamage(ElementType.FIRE, 100.0);
            assertEquals(0.0, stats.getFlatDamage(ElementType.WATER), 0.001);
            assertEquals(0.0, stats.getFlatDamage(ElementType.LIGHTNING), 0.001);
            assertEquals(0.0, stats.getFlatDamage(ElementType.VOID), 0.001);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Adder Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Adder Methods")
    class AdderTests {

        @Test
        @DisplayName("addFlatDamage accumulates values")
        void addFlatDamage_accumulatesValues() {
            stats.addFlatDamage(ElementType.FIRE, 50.0);
            stats.addFlatDamage(ElementType.FIRE, 30.0);
            assertEquals(80.0, stats.getFlatDamage(ElementType.FIRE), 0.001);
        }

        @Test
        @DisplayName("addPercentDamage accumulates values")
        void addPercentDamage_accumulatesValues() {
            stats.addPercentDamage(ElementType.WATER, 25.0);
            stats.addPercentDamage(ElementType.WATER, 15.0);
            assertEquals(40.0, stats.getPercentDamage(ElementType.WATER), 0.001);
        }

        @Test
        @DisplayName("addMultiplierDamage accumulates values")
        void addMultiplierDamage_accumulatesValues() {
            stats.addMultiplierDamage(ElementType.LIGHTNING, 10.0);
            stats.addMultiplierDamage(ElementType.LIGHTNING, 20.0);
            assertEquals(30.0, stats.getMultiplierDamage(ElementType.LIGHTNING), 0.001);
        }

        @Test
        @DisplayName("addResistance accumulates values")
        void addResistance_accumulatesValues() {
            stats.addResistance(ElementType.VOID, 30.0);
            stats.addResistance(ElementType.VOID, 20.0);
            assertEquals(50.0, stats.getResistance(ElementType.VOID), 0.001);
        }

        @Test
        @DisplayName("addPenetration accumulates values")
        void addPenetration_accumulatesValues() {
            stats.addPenetration(ElementType.FIRE, 10.0);
            stats.addPenetration(ElementType.FIRE, 5.0);
            assertEquals(15.0, stats.getPenetration(ElementType.FIRE), 0.001);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Utility Method Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Utility Methods")
    class UtilityMethodTests {

        @Test
        @DisplayName("hasAnyElementalDamage returns false when no damage set")
        void hasAnyElementalDamage_noDamage_returnsFalse() {
            assertFalse(stats.hasAnyElementalDamage());
        }

        @Test
        @DisplayName("hasAnyElementalDamage returns true when flat damage set")
        void hasAnyElementalDamage_withFlatDamage_returnsTrue() {
            stats.setFlatDamage(ElementType.FIRE, 10.0);
            assertTrue(stats.hasAnyElementalDamage());
        }

        @Test
        @DisplayName("hasAnyElementalDamage ignores percent/multiplier only")
        void hasAnyElementalDamage_onlyPercent_returnsFalse() {
            stats.setPercentDamage(ElementType.FIRE, 50.0);
            stats.setMultiplierDamage(ElementType.FIRE, 30.0);
            assertFalse(stats.hasAnyElementalDamage());
        }

        @Test
        @DisplayName("getPrimaryElement returns null when no damage")
        void getPrimaryElement_noDamage_returnsNull() {
            assertNull(stats.getPrimaryElement());
        }

        @Test
        @DisplayName("getPrimaryElement returns element with highest flat damage")
        void getPrimaryElement_returnsHighestFlatDamage() {
            stats.setFlatDamage(ElementType.FIRE, 50.0);
            stats.setFlatDamage(ElementType.WATER, 100.0);
            stats.setFlatDamage(ElementType.LIGHTNING, 75.0);
            assertEquals(ElementType.WATER, stats.getPrimaryElement());
        }

        @Test
        @DisplayName("getTotalFlatDamage returns sum of all flat damages")
        void getTotalFlatDamage_returnsSumOfAll() {
            stats.setFlatDamage(ElementType.FIRE, 50.0);
            stats.setFlatDamage(ElementType.WATER, 30.0);
            stats.setFlatDamage(ElementType.LIGHTNING, 20.0);
            assertEquals(100.0, stats.getTotalFlatDamage(), 0.001);
        }

        @Test
        @DisplayName("getAverageResistance returns average of all resistances")
        void getAverageResistance_returnsAverage() {
            stats.setResistance(ElementType.FIRE, 40.0);
            stats.setResistance(ElementType.WATER, 60.0);
            stats.setResistance(ElementType.LIGHTNING, 20.0);
            stats.setResistance(ElementType.EARTH, 30.0);
            stats.setResistance(ElementType.WIND, 10.0);
            stats.setResistance(ElementType.VOID, 0.0);
            // (40 + 60 + 20 + 30 + 10 + 0) / 6 = 160 / 6 ≈ 26.667
            assertEquals(26.667, stats.getAverageResistance(), 0.001);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Copy and Reset Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Copy and Reset")
    class CopyResetTests {

        @Test
        @DisplayName("copy creates independent copy with same values")
        void copy_createsIndependentCopy() {
            stats.setFlatDamage(ElementType.FIRE, 100.0);
            stats.setResistance(ElementType.WATER, 50.0);
            stats.setPenetration(ElementType.LIGHTNING, 20.0);

            ElementalStats copy = stats.copy();

            // Same values
            assertEquals(100.0, copy.getFlatDamage(ElementType.FIRE), 0.001);
            assertEquals(50.0, copy.getResistance(ElementType.WATER), 0.001);
            assertEquals(20.0, copy.getPenetration(ElementType.LIGHTNING), 0.001);

            // Independent - modifying copy doesn't affect original
            copy.setFlatDamage(ElementType.FIRE, 200.0);
            assertEquals(100.0, stats.getFlatDamage(ElementType.FIRE), 0.001);
        }

        @Test
        @DisplayName("reset sets all values to 0")
        void reset_setsAllValuesToZero() {
            stats.setFlatDamage(ElementType.FIRE, 100.0);
            stats.setPercentDamage(ElementType.FIRE, 50.0);
            stats.setMultiplierDamage(ElementType.FIRE, 30.0);
            stats.setResistance(ElementType.FIRE, 60.0);
            stats.setPenetration(ElementType.FIRE, 20.0);

            stats.reset();

            assertEquals(0.0, stats.getFlatDamage(ElementType.FIRE), 0.001);
            assertEquals(0.0, stats.getPercentDamage(ElementType.FIRE), 0.001);
            assertEquals(0.0, stats.getMultiplierDamage(ElementType.FIRE), 0.001);
            assertEquals(0.0, stats.getResistance(ElementType.FIRE), 0.001);
            assertEquals(0.0, stats.getPenetration(ElementType.FIRE), 0.001);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Factory Method Tests
    // ═══════════════════════════════════════════════════════════════════════════

    // ═══════════════════════════════════════════════════════════════════════════
    // Equals and HashCode Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Equals and HashCode")
    class EqualsHashCodeTests {

        @Test
        @DisplayName("Empty stats are equal")
        void emptyStats_areEqual() {
            ElementalStats other = new ElementalStats();
            assertEquals(stats, other);
            assertEquals(stats.hashCode(), other.hashCode());
        }

        @Test
        @DisplayName("Stats with same values are equal")
        void statsWithSameValues_areEqual() {
            stats.setFlatDamage(ElementType.FIRE, 100.0);
            stats.setResistance(ElementType.WATER, 50.0);

            ElementalStats other = new ElementalStats();
            other.setFlatDamage(ElementType.FIRE, 100.0);
            other.setResistance(ElementType.WATER, 50.0);

            assertEquals(stats, other);
            assertEquals(stats.hashCode(), other.hashCode());
        }

        @Test
        @DisplayName("Stats with different values are not equal")
        void statsWithDifferentValues_areNotEqual() {
            stats.setFlatDamage(ElementType.FIRE, 100.0);

            ElementalStats other = new ElementalStats();
            other.setFlatDamage(ElementType.FIRE, 50.0);

            assertNotEquals(stats, other);
        }

        @Test
        @DisplayName("Stats are not equal to null")
        void stats_notEqualToNull() {
            assertNotEquals(null, stats);
        }

        @Test
        @DisplayName("Stats are not equal to different type")
        void stats_notEqualToDifferentType() {
            assertNotEquals("not stats", stats);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ToString Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("toString")
    class ToStringTests {

        @Test
        @DisplayName("Empty stats toString contains class name")
        void emptyStats_toStringContainsClassName() {
            String result = stats.toString();
            assertTrue(result.contains("ElementalStats"));
        }

        @Test
        @DisplayName("Stats with values shows relevant info in toString")
        void statsWithValues_toStringShowsInfo() {
            stats.setFlatDamage(ElementType.FIRE, 100.0);
            stats.setResistance(ElementType.FIRE, 50.0);

            String result = stats.toString();
            assertTrue(result.contains("FIRE"));
            assertTrue(result.contains("dmg"));
            assertTrue(result.contains("resist"));
        }
    }
}
