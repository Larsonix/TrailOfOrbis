package io.github.larsonix.trailoforbis.stones;

import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.maps.config.RealmModifierConfig;
import io.github.larsonix.trailoforbis.maps.core.RealmBiomeType;
import io.github.larsonix.trailoforbis.maps.core.RealmLayoutShape;
import io.github.larsonix.trailoforbis.maps.core.RealmLayoutSize;
import io.github.larsonix.trailoforbis.maps.core.RealmMapData;
import io.github.larsonix.trailoforbis.maps.modifiers.RealmModifier;
import io.github.larsonix.trailoforbis.maps.modifiers.RealmModifierType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Edge case tests for stone actions.
 *
 * <p>Tests boundary conditions and edge cases that aren't covered
 * by the main StoneActionRegistryTest:
 * <ul>
 *   <li>Rarity upgrade at maximum tier (Mythic can't go higher)</li>
 *   <li>Reroll with all modifiers locked</li>
 *   <li>Quality enhancement at perfect quality</li>
 *   <li>Level boundaries</li>
 * </ul>
 */
@DisplayName("Stone Action Edge Cases")
class StoneActionEdgeCasesTest {

    private StoneActionRegistry registry;
    private Random random;

    @BeforeEach
    void setUp() {
        registry = new StoneActionRegistry(new RealmModifierConfig());
        random = new Random(42);
    }

    // ═══════════════════════════════════════════════════════════════════
    // RARITY UPGRADE EDGE CASES
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Rarity Upgrade Limits")
    class RarityUpgradeLimitTests {

        @Test
        @DisplayName("Spark of Potential should fail on non-Common items")
        void sparkOfPotential_nonCommon_fails() {
            // Arrange - Uncommon item (Spark of Potential is Common → Uncommon)
            RealmMapData map = createMapWithRarity(GearRarity.UNCOMMON);

            // Act
            StoneActionResult result = registry.execute(StoneType.SPARK_OF_POTENTIAL, map, random);

            // Assert
            assertFalse(result.success());
            assertFalse(result.stoneConsumed());
        }

        @Test
        @DisplayName("Heart of Legends should succeed on Rare items")
        void heartOfLegends_rareItem_succeeds() {
            // Arrange - Rare item (Heart of Legends is Rare → Epic)
            RealmMapData map = createMapWithRarity(GearRarity.RARE);

            // Act
            StoneActionResult result = registry.execute(StoneType.HEART_OF_LEGENDS, map, random);

            // Assert
            assertTrue(result.success());
            RealmMapData modified = (RealmMapData) result.modifiedItem();
            assertEquals(GearRarity.EPIC, modified.rarity());
        }

        @Test
        @DisplayName("Heart of Legends should fail on Epic items (wrong rarity)")
        void heartOfLegends_epicItem_fails() {
            // Arrange - Epic item (Heart of Legends requires Rare)
            RealmMapData map = createMapWithRarity(GearRarity.EPIC);

            // Act
            StoneActionResult result = registry.execute(StoneType.HEART_OF_LEGENDS, map, random);

            // Assert
            assertFalse(result.success());
        }

        @Test
        @DisplayName("Mythic rarity has no upgrade path")
        void mythicRarity_hasNoNextTier() {
            // This tests GearRarity.getNextTier() which underlies upgrade logic
            assertTrue(GearRarity.MYTHIC.getNextTier().isEmpty());
        }

        @Test
        @DisplayName("Unique rarity cannot be upgraded to")
        void uniqueRarity_cannotBeUpgradedTo() {
            // Unique is special - can't upgrade to it
            assertTrue(GearRarity.LEGENDARY.getNextTier().isPresent());
            assertEquals(GearRarity.MYTHIC, GearRarity.LEGENDARY.getNextTier().get());

            // And Mythic can't upgrade further
            assertTrue(GearRarity.MYTHIC.getNextTier().isEmpty());
        }

        @Test
        @DisplayName("Varyn's Touch corruption cannot upgrade past Mythic")
        void varynsTouchCorruption_mythicStaysMythic() {
            // Arrange - Mythic item
            RealmMapData map = createMapWithRarity(GearRarity.MYTHIC);

            // Act - run multiple times since corruption has random outcomes
            boolean seenUpgradeAttempt = false;
            for (int i = 0; i < 50; i++) {
                Random r = new Random(i * 1000);
                StoneActionResult result = registry.execute(StoneType.VARYNS_TOUCH, map, r);

                assertTrue(result.success()); // Corruption always succeeds
                RealmMapData modified = (RealmMapData) result.modifiedItem();

                // Rarity should never exceed MYTHIC
                assertTrue(modified.rarity().ordinal() <= GearRarity.MYTHIC.ordinal(),
                    "Rarity should not exceed MYTHIC");

                if (modified.rarity() == GearRarity.MYTHIC) {
                    seenUpgradeAttempt = true;
                }
            }

            assertTrue(seenUpgradeAttempt, "Should have tested Mythic items");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // LOCKED MODIFIER EDGE CASES
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Locked Modifier Preservation")
    class LockedModifierTests {

        @Test
        @DisplayName("Reroll modifiers should fail when all are locked")
        void rerollModifiers_allModifiersLocked_noChanges() {
            // Arrange - All modifiers locked
            RealmMapData map = new RealmMapData(
                50, GearRarity.RARE, 50, RealmBiomeType.FOREST,
                RealmLayoutSize.MEDIUM, RealmLayoutShape.CIRCULAR,
                List.of(new RealmModifier(RealmModifierType.MONSTER_DAMAGE, 30, true)), // locked
                List.of(new RealmModifier(RealmModifierType.ITEM_QUANTITY, 20, true)),  // locked
                false, true, null
            );

            // Act - Try to reroll values
            StoneActionResult result = registry.execute(StoneType.GAIAS_CALIBRATION, map, random);

            // Assert - Should fail since no unlocked modifiers
            assertFalse(result.success());
            assertTrue(result.message().contains("unlocked") ||
                       result.message().contains("No unlocked"),
                "Message should indicate no unlocked modifiers: " + result.message());
        }

        @Test
        @DisplayName("Alterverse Shard should fail when all modifiers locked")
        void alterverseShard_allLocked_fails() {
            // Arrange
            RealmMapData map = new RealmMapData(
                50, GearRarity.RARE, 50, RealmBiomeType.FOREST,
                RealmLayoutSize.MEDIUM, RealmLayoutShape.CIRCULAR,
                List.of(new RealmModifier(RealmModifierType.MONSTER_DAMAGE, 30, true)), // locked
                List.of(new RealmModifier(RealmModifierType.ITEM_QUANTITY, 20, true)),  // locked
                false, true, null
            );

            // Act
            StoneActionResult result = registry.execute(StoneType.ALTERVERSE_SHARD, map, random);

            // Assert
            assertFalse(result.success());
        }

        @Test
        @DisplayName("Reroll should preserve locked modifiers while changing unlocked")
        void rerollModifiers_lockedModifiers_preservesLocked() {
            // Arrange - One locked, one unlocked
            RealmMapData map = new RealmMapData(
                50, GearRarity.RARE, 50, RealmBiomeType.FOREST,
                RealmLayoutSize.MEDIUM, RealmLayoutShape.CIRCULAR,
                List.of(new RealmModifier(RealmModifierType.MONSTER_DAMAGE, 30, true)), // locked prefix
                List.of(RealmModifier.of(RealmModifierType.ITEM_QUANTITY, 20)),          // unlocked suffix
                false, true, null
            );

            // Act
            StoneActionResult result = registry.execute(StoneType.GAIAS_CALIBRATION, map, random);

            // Assert
            assertTrue(result.success());
            RealmMapData modified = (RealmMapData) result.modifiedItem();

            // Locked modifier should keep original value
            RealmModifier lockedMod = modified.prefixes().stream()
                .filter(m -> m.type() == RealmModifierType.MONSTER_DAMAGE)
                .findFirst()
                .orElseThrow();
            assertEquals(30, lockedMod.value());
            assertTrue(lockedMod.locked());

            // Unlocked modifier's value may have changed
            assertTrue(modified.suffixes().stream()
                .anyMatch(m -> m.type() == RealmModifierType.ITEM_QUANTITY && !m.locked()));
        }

        @Test
        @DisplayName("Ember of Tuning should only target unlocked modifiers")
        void emberOfTuning_mixedLocked_onlyRerollsUnlocked() {
            // Arrange - Multiple locked, one unlocked
            RealmMapData map = new RealmMapData(
                50, GearRarity.EPIC, 50, RealmBiomeType.FOREST,
                RealmLayoutSize.MEDIUM, RealmLayoutShape.CIRCULAR,
                List.of(
                    new RealmModifier(RealmModifierType.MONSTER_DAMAGE, 30, true),
                    new RealmModifier(RealmModifierType.MONSTER_HEALTH, 40, true)
                ), // locked prefixes
                List.of(RealmModifier.of(RealmModifierType.ITEM_QUANTITY, 20)), // unlocked suffix
                false, true, null
            );

            int originalLockedValue1 = 30;
            int originalLockedValue2 = 40;

            // Act - run multiple times
            for (int i = 0; i < 10; i++) {
                Random r = new Random(i * 1000);
                StoneActionResult result = registry.execute(StoneType.EMBER_OF_TUNING, map, r);

                assertTrue(result.success());
                RealmMapData modified = (RealmMapData) result.modifiedItem();

                // Locked prefixes should always keep their values
                assertEquals(originalLockedValue1,
                    modified.prefixes().get(0).value(),
                    "First locked prefix should keep value");
                assertEquals(originalLockedValue2,
                    modified.prefixes().get(1).value(),
                    "Second locked prefix should keep value");
            }
        }

        @Test
        @DisplayName("Purging Ember with all locked should fail")
        void purgingEmber_allLocked_fails() {
            // Arrange
            RealmMapData map = new RealmMapData(
                50, GearRarity.RARE, 50, RealmBiomeType.FOREST,
                RealmLayoutSize.MEDIUM, RealmLayoutShape.CIRCULAR,
                List.of(new RealmModifier(RealmModifierType.MONSTER_DAMAGE, 30, true)),
                List.of(new RealmModifier(RealmModifierType.ITEM_QUANTITY, 20, true)),
                false, true, null
            );

            // Act
            StoneActionResult result = registry.execute(StoneType.PURGING_EMBER, map, random);

            // Assert - Should fail since nothing can be removed
            assertFalse(result.success());
        }

        @Test
        @DisplayName("Erosion Shard with all locked should fail")
        void erosionShard_allLocked_fails() {
            // Arrange
            RealmMapData map = new RealmMapData(
                50, GearRarity.RARE, 50, RealmBiomeType.FOREST,
                RealmLayoutSize.MEDIUM, RealmLayoutShape.CIRCULAR,
                List.of(new RealmModifier(RealmModifierType.MONSTER_DAMAGE, 30, true)),
                List.of(),
                false, true, null
            );

            // Act
            StoneActionResult result = registry.execute(StoneType.EROSION_SHARD, map, random);

            // Assert
            assertFalse(result.success());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // QUALITY EDGE CASES
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Quality Enhancement Limits")
    class QualityLimitTests {

        @Test
        @DisplayName("Gaia's Perfection should fail on already perfect quality")
        void gaiasPerfection_alreadyPerfect_fails() {
            // Arrange - Perfect quality (101)
            RealmMapData map = new RealmMapData(
                50, GearRarity.RARE, 101, RealmBiomeType.FOREST,
                RealmLayoutSize.MEDIUM, RealmLayoutShape.CIRCULAR,
                List.of(RealmModifier.of(RealmModifierType.MONSTER_DAMAGE, 30)),
                List.of(),
                false, true, null
            );

            // Act
            StoneActionResult result = registry.execute(StoneType.GAIAS_PERFECTION, map, random);

            // Assert
            assertFalse(result.success());
            assertTrue(result.message().toLowerCase().contains("quality") ||
                       result.message().toLowerCase().contains("perfect") ||
                       result.message().toLowerCase().contains("max"),
                "Should indicate quality is already max: " + result.message());
        }

        @Test
        @DisplayName("Orbisian Blessing should fail on perfect quality")
        void orbisianBlessing_perfectQuality_fails() {
            // Arrange - Perfect quality
            RealmMapData map = new RealmMapData(
                50, GearRarity.RARE, 101, RealmBiomeType.FOREST,
                RealmLayoutSize.MEDIUM, RealmLayoutShape.CIRCULAR,
                List.of(RealmModifier.of(RealmModifierType.MONSTER_DAMAGE, 30)),
                List.of(),
                false, true, null
            );

            // Act
            StoneActionResult result = registry.execute(StoneType.ORBISIAN_BLESSING, map, random);

            // Assert
            assertFalse(result.success());
        }

        @Test
        @DisplayName("Cartographer's Polish should fail at max quality")
        void cartographersPolish_maxQuality_fails() {
            // Arrange - Quality at 100 (not perfect but max for Polish)
            RealmMapData map = new RealmMapData(
                50, GearRarity.RARE, 100, RealmBiomeType.FOREST,
                RealmLayoutSize.MEDIUM, RealmLayoutShape.CIRCULAR,
                List.of(RealmModifier.of(RealmModifierType.MONSTER_DAMAGE, 30)),
                List.of(),
                false, true, null
            );

            // Act
            StoneActionResult result = registry.execute(StoneType.CARTOGRAPHERS_POLISH, map, random);

            // Assert
            assertFalse(result.success());
        }

        @Test
        @DisplayName("Cartographer's Polish should improve quality below 100")
        void cartographersPolish_belowMax_succeeds() {
            // Arrange
            RealmMapData map = new RealmMapData(
                50, GearRarity.RARE, 95, RealmBiomeType.FOREST,
                RealmLayoutSize.MEDIUM, RealmLayoutShape.CIRCULAR,
                List.of(RealmModifier.of(RealmModifierType.MONSTER_DAMAGE, 30)),
                List.of(),
                false, true, null
            );

            // Act
            StoneActionResult result = registry.execute(StoneType.CARTOGRAPHERS_POLISH, map, random);

            // Assert
            assertTrue(result.success());
            RealmMapData modified = (RealmMapData) result.modifiedItem();
            assertTrue(modified.quality() > 95);
            assertTrue(modified.quality() <= 100);
        }

        @Test
        @DisplayName("Quality reroll should stay in 1-100 range")
        void orbisianBlessing_rerollsWithin1To100() {
            // Arrange
            RealmMapData map = new RealmMapData(
                50, GearRarity.RARE, 50, RealmBiomeType.FOREST,
                RealmLayoutSize.MEDIUM, RealmLayoutShape.CIRCULAR,
                List.of(RealmModifier.of(RealmModifierType.MONSTER_DAMAGE, 30)),
                List.of(),
                false, true, null
            );

            // Act - run many times
            for (int i = 0; i < 100; i++) {
                Random r = new Random(i);
                StoneActionResult result = registry.execute(StoneType.ORBISIAN_BLESSING, map, r);

                assertTrue(result.success());
                RealmMapData modified = (RealmMapData) result.modifiedItem();
                assertTrue(modified.quality() >= 1 && modified.quality() <= 100,
                    "Quality should be 1-100, got: " + modified.quality());
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // CORRUPTED ITEM EDGE CASES
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Corrupted Item Handling")
    class CorruptedItemTests {

        @Test
        @DisplayName("Most stones should fail on corrupted items")
        void mostStones_corruptedItem_fails() {
            // Arrange
            RealmMapData corruptedMap = new RealmMapData(
                50, GearRarity.RARE, 50, RealmBiomeType.FOREST,
                RealmLayoutSize.MEDIUM, RealmLayoutShape.CIRCULAR,
                List.of(RealmModifier.of(RealmModifierType.MONSTER_DAMAGE, 30)),
                List.of(),
                true, // corrupted!
                true, null
            );

            // Act & Assert - These stones should fail on corrupted items
            StoneType[] shouldFailOnCorrupted = {
                StoneType.GAIAS_CALIBRATION,
                StoneType.ALTERVERSE_SHARD,
                StoneType.GAIAS_GIFT,
                StoneType.ORBISIAN_BLESSING,
            };

            for (StoneType type : shouldFailOnCorrupted) {
                if (!type.worksOnCorrupted()) {
                    StoneActionResult result = registry.execute(type, corruptedMap, random);
                    assertFalse(result.success(),
                        type + " should fail on corrupted item");
                }
            }
        }

        @Test
        @DisplayName("Varyn's Touch should fail on already corrupted items")
        void varynsTouchOnCorrupted_fails() {
            // Arrange
            RealmMapData corruptedMap = new RealmMapData(
                50, GearRarity.RARE, 50, RealmBiomeType.FOREST,
                RealmLayoutSize.MEDIUM, RealmLayoutShape.CIRCULAR,
                List.of(RealmModifier.of(RealmModifierType.MONSTER_DAMAGE, 30)),
                List.of(),
                true, // already corrupted
                true, null
            );

            // Act
            StoneActionResult result = registry.execute(StoneType.VARYNS_TOUCH, corruptedMap, random);

            // Assert
            assertFalse(result.success());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // IDENTIFICATION EDGE CASES
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Identification Edge Cases")
    class IdentificationTests {

        @Test
        @DisplayName("Lorekeeper's Scroll should fail on identified items")
        void lorekeepersScroll_alreadyIdentified_fails() {
            // Arrange
            RealmMapData map = new RealmMapData(
                50, GearRarity.RARE, 50, RealmBiomeType.FOREST,
                RealmLayoutSize.MEDIUM, RealmLayoutShape.CIRCULAR,
                List.of(RealmModifier.of(RealmModifierType.MONSTER_DAMAGE, 30)),
                List.of(),
                false,
                true, // already identified
                null
            );

            // Act
            StoneActionResult result = registry.execute(StoneType.LOREKEEPERS_SCROLL, map, random);

            // Assert
            assertFalse(result.success());
        }

        @Test
        @DisplayName("Lorekeeper's Scroll should succeed on unidentified items")
        void lorekeepersScroll_unidentified_succeeds() {
            // Arrange
            RealmMapData map = new RealmMapData(
                50, GearRarity.RARE, 50, RealmBiomeType.FOREST,
                RealmLayoutSize.MEDIUM, RealmLayoutShape.CIRCULAR,
                List.of(RealmModifier.of(RealmModifierType.MONSTER_DAMAGE, 30)),
                List.of(),
                false,
                false, // unidentified
                null
            );

            // Act
            StoneActionResult result = registry.execute(StoneType.LOREKEEPERS_SCROLL, map, random);

            // Assert
            assertTrue(result.success());
            RealmMapData modified = (RealmMapData) result.modifiedItem();
            assertTrue(modified.isIdentified());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // GENESIS STONE EDGE CASES
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Genesis Stone Edge Cases")
    class GenesisStoneTests {

        @Test
        @DisplayName("Genesis Stone should fail when item is at max modifiers")
        void genesisStone_atMaxModifiers_fails() {
            // Arrange - COMMON allows 1 modifier, fill it
            RealmMapData map = new RealmMapData(
                50, GearRarity.COMMON, 50, RealmBiomeType.FOREST,
                RealmLayoutSize.MEDIUM, RealmLayoutShape.CIRCULAR,
                List.of(RealmModifier.of(RealmModifierType.MONSTER_DAMAGE, 30)),
                List.of(),
                false, true, null
            );

            // Act
            StoneActionResult result = registry.execute(StoneType.GENESIS_STONE, map, random);

            // Assert
            assertFalse(result.success());
        }

        @Test
        @DisplayName("Genesis Stone should fill all remaining slots on empty item")
        void genesisStone_emptyItem_fillsAllSlots() {
            // Arrange - Empty modifiers, RARE allows 3
            RealmMapData map = new RealmMapData(
                50, GearRarity.RARE, 50, RealmBiomeType.FOREST,
                RealmLayoutSize.MEDIUM, RealmLayoutShape.CIRCULAR,
                List.of(), // no prefixes
                List.of(), // no suffixes
                false, true, null
            );

            // Act
            StoneActionResult result = registry.execute(StoneType.GENESIS_STONE, map, random);

            // Assert
            assertTrue(result.success());
            RealmMapData modified = (RealmMapData) result.modifiedItem();
            assertEquals(3, modified.modifiers().size(),
                "Genesis Stone should fill all 3 slots for RARE rarity");
        }

        @Test
        @DisplayName("Genesis Stone should fill remaining slots on partially filled item")
        void genesisStone_partialItem_fillsRemainingSlots() {
            // Arrange - RARE allows 3, start with 1
            RealmMapData map = new RealmMapData(
                50, GearRarity.RARE, 50, RealmBiomeType.FOREST,
                RealmLayoutSize.MEDIUM, RealmLayoutShape.CIRCULAR,
                List.of(RealmModifier.of(RealmModifierType.MONSTER_DAMAGE, 30)),
                List.of(),
                false, true, null
            );

            // Act
            StoneActionResult result = registry.execute(StoneType.GENESIS_STONE, map, random);

            // Assert
            assertTrue(result.success());
            RealmMapData modified = (RealmMapData) result.modifiedItem();
            assertEquals(3, modified.modifiers().size(),
                "Genesis Stone should fill to max (3 for RARE)");
            // Original modifier should still be there
            assertTrue(modified.modifiers().stream()
                .anyMatch(m -> m.type() == RealmModifierType.MONSTER_DAMAGE));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Creates a test map with specified rarity and minimal modifiers.
     */
    private RealmMapData createMapWithRarity(GearRarity rarity) {
        // Start with minimal modifiers to allow upgrade stones to add more
        return new RealmMapData(
            50, rarity, 50, RealmBiomeType.FOREST,
            RealmLayoutSize.MEDIUM, RealmLayoutShape.CIRCULAR,
            List.of(RealmModifier.of(RealmModifierType.MONSTER_DAMAGE, 30)),
            List.of(),
            false, true, null
        );
    }
}
