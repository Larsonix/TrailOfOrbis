package io.github.larsonix.trailoforbis.maps.templates;

import com.hypixel.hytale.math.vector.Vector3d;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link MonsterSpawnPoint}.
 */
@DisplayName("MonsterSpawnPoint")
class MonsterSpawnPointTest {

    private static final Vector3d TEST_POSITION = new Vector3d(10, 64, 20);

    @Nested
    @DisplayName("Constructor validation")
    class ConstructorValidation {

        @Test
        @DisplayName("Should require non-null position")
        void shouldRequireNonNullPosition() {
            assertThrows(NullPointerException.class, () ->
                new MonsterSpawnPoint(null, 5.0f, MonsterSpawnPoint.SpawnType.NORMAL, 3));
        }

        @Test
        @DisplayName("Should require non-null spawn type")
        void shouldRequireNonNullSpawnType() {
            assertThrows(NullPointerException.class, () ->
                new MonsterSpawnPoint(TEST_POSITION, 5.0f, null, 3));
        }

        @Test
        @DisplayName("Should clamp negative radius to 0")
        void shouldClampNegativeRadius() {
            MonsterSpawnPoint point = new MonsterSpawnPoint(TEST_POSITION, -5.0f, MonsterSpawnPoint.SpawnType.NORMAL, 3);
            assertEquals(0, point.radius());
        }

        @Test
        @DisplayName("Should clamp zero maxCount to 1")
        void shouldClampZeroMaxCount() {
            MonsterSpawnPoint point = new MonsterSpawnPoint(TEST_POSITION, 5.0f, MonsterSpawnPoint.SpawnType.NORMAL, 0);
            assertEquals(1, point.maxCount());
        }

        @Test
        @DisplayName("Should clamp negative maxCount to 1")
        void shouldClampNegativeMaxCount() {
            MonsterSpawnPoint point = new MonsterSpawnPoint(TEST_POSITION, 5.0f, MonsterSpawnPoint.SpawnType.NORMAL, -5);
            assertEquals(1, point.maxCount());
        }
    }

    @Nested
    @DisplayName("Factory methods")
    class FactoryMethods {

        @Test
        @DisplayName("normal() should create NORMAL spawn point with default radius")
        void normalShouldCreateNormalSpawnPoint() {
            MonsterSpawnPoint point = MonsterSpawnPoint.normal(TEST_POSITION, 5);

            assertEquals(TEST_POSITION, point.position());
            assertEquals(5.0f, point.radius());
            assertEquals(MonsterSpawnPoint.SpawnType.NORMAL, point.type());
            assertEquals(5, point.maxCount());
        }

        @Test
        @DisplayName("elite() should create single ELITE spawn point")
        void eliteShouldCreateEliteSpawnPoint() {
            MonsterSpawnPoint point = MonsterSpawnPoint.elite(TEST_POSITION);

            assertEquals(TEST_POSITION, point.position());
            assertEquals(3.0f, point.radius());
            assertEquals(MonsterSpawnPoint.SpawnType.ELITE, point.type());
            assertEquals(1, point.maxCount());
        }

        @Test
        @DisplayName("boss() should create single BOSS spawn point")
        void bossShouldCreateBossSpawnPoint() {
            MonsterSpawnPoint point = MonsterSpawnPoint.boss(TEST_POSITION);

            assertEquals(TEST_POSITION, point.position());
            assertEquals(2.0f, point.radius());
            assertEquals(MonsterSpawnPoint.SpawnType.BOSS, point.type());
            assertEquals(1, point.maxCount());
        }

        @Test
        @DisplayName("pack() should create PACK spawn point with custom size")
        void packShouldCreatePackSpawnPoint() {
            MonsterSpawnPoint point = MonsterSpawnPoint.pack(TEST_POSITION, 8);

            assertEquals(TEST_POSITION, point.position());
            assertEquals(8.0f, point.radius());
            assertEquals(MonsterSpawnPoint.SpawnType.PACK, point.type());
            assertEquals(8, point.maxCount());
        }
    }

    @Nested
    @DisplayName("Type checks")
    class TypeChecks {

        @Test
        @DisplayName("isBossSpawn() should return true for BOSS type")
        void isBossSpawnShouldReturnTrueForBoss() {
            MonsterSpawnPoint point = MonsterSpawnPoint.boss(TEST_POSITION);
            assertTrue(point.isBossSpawn());
            assertFalse(point.isEliteSpawn());
            assertFalse(point.isNormalSpawn());
            assertFalse(point.isPackSpawn());
        }

        @Test
        @DisplayName("isEliteSpawn() should return true for ELITE type")
        void isEliteSpawnShouldReturnTrueForElite() {
            MonsterSpawnPoint point = MonsterSpawnPoint.elite(TEST_POSITION);
            assertTrue(point.isEliteSpawn());
            assertFalse(point.isBossSpawn());
            assertFalse(point.isNormalSpawn());
            assertFalse(point.isPackSpawn());
        }

        @Test
        @DisplayName("isNormalSpawn() should return true for NORMAL type")
        void isNormalSpawnShouldReturnTrueForNormal() {
            MonsterSpawnPoint point = MonsterSpawnPoint.normal(TEST_POSITION, 3);
            assertTrue(point.isNormalSpawn());
            assertFalse(point.isBossSpawn());
            assertFalse(point.isEliteSpawn());
            assertFalse(point.isPackSpawn());
        }

        @Test
        @DisplayName("isPackSpawn() should return true for PACK type")
        void isPackSpawnShouldReturnTrueForPack() {
            MonsterSpawnPoint point = MonsterSpawnPoint.pack(TEST_POSITION, 5);
            assertTrue(point.isPackSpawn());
            assertFalse(point.isBossSpawn());
            assertFalse(point.isEliteSpawn());
            assertFalse(point.isNormalSpawn());
        }
    }

    @Nested
    @DisplayName("Reward multipliers")
    class RewardMultipliers {

        @Test
        @DisplayName("NORMAL type should have 1.0 multiplier")
        void normalShouldHaveOneMultiplier() {
            MonsterSpawnPoint point = MonsterSpawnPoint.normal(TEST_POSITION, 3);
            assertEquals(1.0f, point.getRewardMultiplier());
        }

        @Test
        @DisplayName("ELITE type should have 2.0 multiplier")
        void eliteShouldHaveTwoMultiplier() {
            MonsterSpawnPoint point = MonsterSpawnPoint.elite(TEST_POSITION);
            assertEquals(2.0f, point.getRewardMultiplier());
        }

        @Test
        @DisplayName("BOSS type should have 5.0 multiplier")
        void bossShouldHaveFiveMultiplier() {
            MonsterSpawnPoint point = MonsterSpawnPoint.boss(TEST_POSITION);
            assertEquals(5.0f, point.getRewardMultiplier());
        }

        @Test
        @DisplayName("PACK type should have 0.5 multiplier")
        void packShouldHaveHalfMultiplier() {
            MonsterSpawnPoint point = MonsterSpawnPoint.pack(TEST_POSITION, 5);
            assertEquals(0.5f, point.getRewardMultiplier());
        }
    }

    @Nested
    @DisplayName("Immutable copy methods")
    class ImmutableCopyMethods {

        @Test
        @DisplayName("withPosition() should return new instance with updated position")
        void withPositionShouldReturnNewInstance() {
            MonsterSpawnPoint original = MonsterSpawnPoint.normal(TEST_POSITION, 3);
            Vector3d newPosition = new Vector3d(100, 128, 200);

            MonsterSpawnPoint updated = original.withPosition(newPosition);

            assertNotSame(original, updated);
            assertEquals(newPosition, updated.position());
            assertEquals(original.radius(), updated.radius());
            assertEquals(original.type(), updated.type());
            assertEquals(original.maxCount(), updated.maxCount());
        }

        @Test
        @DisplayName("withMaxCount() should return new instance with updated count")
        void withMaxCountShouldReturnNewInstance() {
            MonsterSpawnPoint original = MonsterSpawnPoint.normal(TEST_POSITION, 3);

            MonsterSpawnPoint updated = original.withMaxCount(10);

            assertNotSame(original, updated);
            assertEquals(original.position(), updated.position());
            assertEquals(original.radius(), updated.radius());
            assertEquals(original.type(), updated.type());
            assertEquals(10, updated.maxCount());
        }
    }

    @Nested
    @DisplayName("SpawnType enum")
    class SpawnTypeEnum {

        @Test
        @DisplayName("Each type should have correct display name")
        void eachTypeShouldHaveCorrectDisplayName() {
            assertEquals("Normal", MonsterSpawnPoint.SpawnType.NORMAL.getDisplayName());
            assertEquals("Elite", MonsterSpawnPoint.SpawnType.ELITE.getDisplayName());
            assertEquals("Boss", MonsterSpawnPoint.SpawnType.BOSS.getDisplayName());
            assertEquals("Pack", MonsterSpawnPoint.SpawnType.PACK.getDisplayName());
        }

        @Test
        @DisplayName("Each type should have correct reward multiplier")
        void eachTypeShouldHaveCorrectRewardMultiplier() {
            assertEquals(1.0f, MonsterSpawnPoint.SpawnType.NORMAL.getRewardMultiplier());
            assertEquals(2.0f, MonsterSpawnPoint.SpawnType.ELITE.getRewardMultiplier());
            assertEquals(5.0f, MonsterSpawnPoint.SpawnType.BOSS.getRewardMultiplier());
            assertEquals(0.5f, MonsterSpawnPoint.SpawnType.PACK.getRewardMultiplier());
        }
    }
}
