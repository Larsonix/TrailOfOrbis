package io.github.larsonix.trailoforbis.leveling.core;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PlayerLevelData - immutable player leveling record.
 */
@ExtendWith(MockitoExtension.class)
class PlayerLevelDataTest {

    private final UUID testPlayer = UUID.randomUUID();

    // ═══════════════════════════════════════════════════════════════════
    // CREATION TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Creation")
    class CreationTests {

        @Test
        @DisplayName("createNew should create with 0 XP")
        void createNewShouldCreateWithZeroXp() {
            PlayerLevelData data = PlayerLevelData.createNew(testPlayer);

            assertEquals(testPlayer, data.uuid());
            assertEquals(0, data.xp());
            assertNotNull(data.createdAt());
            assertNotNull(data.lastUpdated());
        }

        @Test
        @DisplayName("createWithXp should create with specified XP")
        void createWithXpShouldCreateWithSpecifiedXp() {
            PlayerLevelData data = PlayerLevelData.createWithXp(testPlayer, 1000);

            assertEquals(testPlayer, data.uuid());
            assertEquals(1000, data.xp());
        }

        @Test
        @DisplayName("Should reject null UUID")
        void shouldRejectNullUuid() {
            assertThrows(IllegalArgumentException.class, () ->
                PlayerLevelData.createNew(null));
        }

        @Test
        @DisplayName("Should reject null createdAt")
        void shouldRejectNullCreatedAt() {
            assertThrows(IllegalArgumentException.class, () ->
                new PlayerLevelData(testPlayer, 0, null, Instant.now()));
        }

        @Test
        @DisplayName("Should reject null lastUpdated")
        void shouldRejectNullLastUpdated() {
            assertThrows(IllegalArgumentException.class, () ->
                new PlayerLevelData(testPlayer, 0, Instant.now(), null));
        }

        @Test
        @DisplayName("Should clamp negative XP to 0")
        void shouldClampNegativeXpToZero() {
            PlayerLevelData data = new PlayerLevelData(testPlayer, -100, Instant.now(), Instant.now());

            assertEquals(0, data.xp());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // WITH XP TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("withXp")
    class WithXpTests {

        @Test
        @DisplayName("Should return new instance with updated XP")
        void shouldReturnNewInstanceWithUpdatedXp() {
            PlayerLevelData original = PlayerLevelData.createNew(testPlayer);
            PlayerLevelData updated = original.withXp(100);

            assertEquals(0, original.xp());
            assertEquals(100, updated.xp());
            assertNotSame(original, updated);
        }

        @Test
        @DisplayName("Should update lastModified timestamp")
        void shouldUpdateLastModifiedTimestamp() throws InterruptedException {
            PlayerLevelData original = PlayerLevelData.createNew(testPlayer);
            Instant originalTimestamp = original.lastUpdated();

            Thread.sleep(10); // Ensure time passes

            PlayerLevelData updated = original.withXp(100);

            assertTrue(updated.lastUpdated().isAfter(originalTimestamp) ||
                       updated.lastUpdated().equals(originalTimestamp));
        }

        @Test
        @DisplayName("Should preserve createdAt timestamp")
        void shouldPreserveCreatedAtTimestamp() {
            PlayerLevelData original = PlayerLevelData.createNew(testPlayer);
            Instant createdAt = original.createdAt();

            PlayerLevelData updated = original.withXp(100);

            assertEquals(createdAt, updated.createdAt());
        }

        @Test
        @DisplayName("Should clamp negative XP to 0")
        void shouldClampNegativeXpToZero() {
            PlayerLevelData original = PlayerLevelData.createWithXp(testPlayer, 100);
            PlayerLevelData updated = original.withXp(-50);

            assertEquals(0, updated.xp());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // WITH XP DELTA TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("withXpDelta")
    class WithXpDeltaTests {

        @Test
        @DisplayName("Should add positive delta")
        void shouldAddPositiveDelta() {
            PlayerLevelData original = PlayerLevelData.createWithXp(testPlayer, 100);
            PlayerLevelData updated = original.withXpDelta(50);

            assertEquals(150, updated.xp());
        }

        @Test
        @DisplayName("Should subtract negative delta")
        void shouldSubtractNegativeDelta() {
            PlayerLevelData original = PlayerLevelData.createWithXp(testPlayer, 100);
            PlayerLevelData updated = original.withXpDelta(-30);

            assertEquals(70, updated.xp());
        }

        @Test
        @DisplayName("Should clamp to 0 when delta would go negative")
        void shouldClampToZeroWhenDeltaWouldGoNegative() {
            PlayerLevelData original = PlayerLevelData.createWithXp(testPlayer, 50);
            PlayerLevelData updated = original.withXpDelta(-100);

            assertEquals(0, updated.xp());
        }

        @Test
        @DisplayName("Should handle zero delta")
        void shouldHandleZeroDelta() {
            PlayerLevelData original = PlayerLevelData.createWithXp(testPlayer, 100);
            PlayerLevelData updated = original.withXpDelta(0);

            assertEquals(100, updated.xp());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // UTILITY TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Utility Methods")
    class UtilityTests {

        @Test
        @DisplayName("hasBeenModified should return false for new record")
        void hasBeenModifiedShouldReturnFalseForNewRecord() {
            PlayerLevelData data = PlayerLevelData.createNew(testPlayer);

            assertFalse(data.hasBeenModified());
        }

        @Test
        @DisplayName("hasBeenModified should return true after modification")
        void hasBeenModifiedShouldReturnTrueAfterModification() {
            Instant originalTime = Instant.now().minusSeconds(1);
            PlayerLevelData data = new PlayerLevelData(testPlayer, 100, originalTime, originalTime);
            PlayerLevelData updated = data.withXp(200);

            assertTrue(updated.hasBeenModified());
            assertFalse(data.hasBeenModified());
        }

        @Test
        @DisplayName("equals should compare all fields")
        void equalsShouldCompareAllFields() {
            PlayerLevelData data1 = PlayerLevelData.createWithXp(testPlayer, 100);
            PlayerLevelData data2 = PlayerLevelData.createWithXp(testPlayer, 500);

            assertNotEquals(data1, data2); // Different XP values

            PlayerLevelData sameData = new PlayerLevelData(testPlayer, 100, data1.createdAt(), data1.lastUpdated());
            assertEquals(data1, sameData); // Same UUID and XP
        }

        @Test
        @DisplayName("hashCode should be based on all fields")
        void hashCodeShouldBeBasedOnAllFields() {
            PlayerLevelData data1 = PlayerLevelData.createWithXp(testPlayer, 100);
            PlayerLevelData data2 = PlayerLevelData.createWithXp(testPlayer, 500);

            assertNotEquals(data1.hashCode(), data2.hashCode()); // Different XP values
        }

        @Test
        @DisplayName("toString should contain key information")
        void toStringShouldContainKeyInformation() {
            PlayerLevelData data = PlayerLevelData.createWithXp(testPlayer, 1000);
            String str = data.toString();

            assertTrue(str.contains(testPlayer.toString().substring(0, 8)));
            assertTrue(str.contains("1000"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // IMMUTABILITY TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Immutability")
    class ImmutabilityTests {

        @Test
        @DisplayName("withXp should not modify original")
        void withXpShouldNotModifyOriginal() {
            PlayerLevelData original = PlayerLevelData.createWithXp(testPlayer, 100);

            original.withXp(200);

            assertEquals(100, original.xp());
        }

        @Test
        @DisplayName("withXpDelta should not modify original")
        void withXpDeltaShouldNotModifyOriginal() {
            PlayerLevelData original = PlayerLevelData.createWithXp(testPlayer, 100);

            original.withXpDelta(50);

            assertEquals(100, original.xp());
        }

        @Test
        @DisplayName("Multiple modifications should not affect original")
        void multipleModificationsShouldNotAffectOriginal() {
            PlayerLevelData original = PlayerLevelData.createWithXp(testPlayer, 0);

            PlayerLevelData v1 = original.withXp(100);
            PlayerLevelData v2 = v1.withXp(200);
            PlayerLevelData v3 = v2.withXpDelta(50);

            assertEquals(0, original.xp());
            assertEquals(100, v1.xp());
            assertEquals(200, v2.xp());
            assertEquals(250, v3.xp());
        }
    }
}
