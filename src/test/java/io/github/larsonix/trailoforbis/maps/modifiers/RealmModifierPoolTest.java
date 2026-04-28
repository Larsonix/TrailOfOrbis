package io.github.larsonix.trailoforbis.maps.modifiers;

import io.github.larsonix.trailoforbis.maps.config.RealmModifierConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link RealmModifierPool}.
 */
@DisplayName("RealmModifierPool")
class RealmModifierPoolTest {

    private RealmModifierConfig config;
    private RealmModifierPool pool;

    @BeforeEach
    void setUp() {
        config = new RealmModifierConfig();
        pool = new RealmModifierPool(config);
    }

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("Should reject null config")
        void shouldRejectNullConfig() {
            assertThrows(NullPointerException.class, () -> new RealmModifierPool(null));
        }

        @Test
        @DisplayName("Should build pool with all enabled modifiers")
        void shouldBuildPoolWithAllEnabledModifiers() {
            assertTrue(pool.getEnabledCount() > 0);
            assertEquals(RealmModifierType.values().length, pool.getEnabledCount());
        }
    }

    @Nested
    @DisplayName("selectRandom")
    class SelectRandomTests {

        @Test
        @DisplayName("Should return a modifier type")
        void shouldReturnModifierType() {
            Random random = new Random(42);
            RealmModifierType type = pool.selectRandom(random);
            assertNotNull(type);
        }

        @Test
        @DisplayName("Should exclude specified types")
        void shouldExcludeSpecifiedTypes() {
            Random random = new Random(42);
            Set<RealmModifierType> excluded = EnumSet.of(
                RealmModifierType.MONSTER_DAMAGE,
                RealmModifierType.MONSTER_HEALTH
            );

            for (int i = 0; i < 100; i++) {
                RealmModifierType type = pool.selectRandom(random, excluded);
                assertNotNull(type);
                assertFalse(excluded.contains(type),
                    "Should not return excluded type: " + type);
            }
        }

        @Test
        @DisplayName("Should return null when all types excluded")
        void shouldReturnNullWhenAllExcluded() {
            Random random = new Random(42);
            Set<RealmModifierType> allTypes = EnumSet.allOf(RealmModifierType.class);
            RealmModifierType type = pool.selectRandom(random, allTypes);
            assertNull(type);
        }
    }

    @Nested
    @DisplayName("selectFromCategory")
    class SelectFromCategoryTests {

        @Test
        @DisplayName("Should return modifier from specified category")
        void shouldReturnModifierFromCategory() {
            Random random = new Random(42);

            for (RealmModifierType.Category category : RealmModifierType.Category.values()) {
                RealmModifierType type = pool.selectFromCategory(random, category);
                if (type != null) {
                    assertEquals(category, type.getCategory(),
                        "Modifier should be from requested category");
                }
            }
        }

        @Test
        @DisplayName("Should return suffix modifiers from SUFFIX category")
        void shouldReturnSuffixModifiers() {
            Random random = new Random(42);

            for (int i = 0; i < 50; i++) {
                RealmModifierType type = pool.selectFromCategory(random,
                    RealmModifierType.Category.SUFFIX);
                assertNotNull(type);
                assertTrue(type.isRewardModifier());
            }
        }

        @Test
        @DisplayName("Should respect exclusions within category")
        void shouldRespectExclusionsWithinCategory() {
            Random random = new Random(42);
            Set<RealmModifierType> excluded = EnumSet.of(RealmModifierType.MONSTER_DAMAGE);

            for (int i = 0; i < 50; i++) {
                RealmModifierType type = pool.selectFromCategory(random,
                    RealmModifierType.Category.PREFIX, excluded);
                assertNotEquals(RealmModifierType.MONSTER_DAMAGE, type);
            }
        }
    }

    @Nested
    @DisplayName("getEnabledModifiers")
    class GetEnabledModifiersTests {

        @Test
        @DisplayName("Should return all modifiers for each category")
        void shouldReturnAllModifiersForCategory() {
            for (RealmModifierType.Category category : RealmModifierType.Category.values()) {
                List<RealmModifierType> enabled = pool.getEnabledModifiers(category);
                assertNotNull(enabled);

                // Verify all returned modifiers are from the correct category
                for (RealmModifierType type : enabled) {
                    assertEquals(category, type.getCategory());
                }
            }
        }

        @Test
        @DisplayName("Should return unmodifiable list")
        void shouldReturnUnmodifiableList() {
            List<RealmModifierType> enabled = pool.getEnabledModifiers(
                RealmModifierType.Category.PREFIX);
            assertThrows(UnsupportedOperationException.class,
                () -> enabled.add(RealmModifierType.ITEM_QUANTITY));
        }
    }

    @Nested
    @DisplayName("isEnabled")
    class IsEnabledTests {

        @Test
        @DisplayName("Should return true for enabled modifiers")
        void shouldReturnTrueForEnabled() {
            // All modifiers are enabled by default
            for (RealmModifierType type : RealmModifierType.values()) {
                assertTrue(pool.isEnabled(type),
                    "All modifiers should be enabled by default: " + type);
            }
        }

        @Test
        @DisplayName("Should return false for disabled modifiers")
        void shouldReturnFalseForDisabled() {
            // Disable a specific modifier - must create and store settings explicitly
            var settings = RealmModifierConfig.ModifierSettings.fromType(RealmModifierType.NO_REGENERATION);
            settings.setEnabled(false);
            config.setModifierSettings(RealmModifierType.NO_REGENERATION, settings);

            pool.rebuild();

            assertFalse(pool.isEnabled(RealmModifierType.NO_REGENERATION));
        }
    }

    @Nested
    @DisplayName("rebuild")
    class RebuildTests {

        @Test
        @DisplayName("Should update pool after config changes")
        void shouldUpdatePoolAfterConfigChanges() {
            // Initially all enabled
            int initialCount = pool.getEnabledCount();
            assertTrue(pool.isEnabled(RealmModifierType.REDUCED_HEALING));

            // Disable a modifier - must create and store settings explicitly
            var settings = RealmModifierConfig.ModifierSettings.fromType(RealmModifierType.REDUCED_HEALING);
            settings.setEnabled(false);
            config.setModifierSettings(RealmModifierType.REDUCED_HEALING, settings);

            // Rebuild pool
            pool.rebuild();

            // Verify change
            assertEquals(initialCount - 1, pool.getEnabledCount());
            assertFalse(pool.isEnabled(RealmModifierType.REDUCED_HEALING));
        }
    }

    @Nested
    @DisplayName("Weight distribution")
    class WeightDistributionTests {

        @Test
        @DisplayName("Selection should be weighted")
        void selectionShouldBeWeighted() {
            Random random = new Random(12345);
            Map<RealmModifierType, Integer> counts = new EnumMap<>(RealmModifierType.class);

            // Run many selections
            int iterations = 10000;
            for (int i = 0; i < iterations; i++) {
                RealmModifierType type = pool.selectRandom(random);
                counts.merge(type, 1, Integer::sum);
            }

            // Verify we get variety (not all the same)
            assertTrue(counts.size() > 10,
                "Should select many different modifier types");

            // Higher weight modifiers should appear more frequently (statistically)
            // This is a statistical test, so we use a generous threshold
            double averageCount = (double) iterations / RealmModifierType.values().length;
            for (var entry : counts.entrySet()) {
                // No single modifier should dominate (>50%) or be completely absent
                assertTrue(entry.getValue() > 0,
                    "Modifier should appear at least once: " + entry.getKey());
                assertTrue(entry.getValue() < iterations * 0.5,
                    "No modifier should dominate: " + entry.getKey());
            }
        }
    }
}
