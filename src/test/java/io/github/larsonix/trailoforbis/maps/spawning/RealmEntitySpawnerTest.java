package io.github.larsonix.trailoforbis.maps.spawning;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.maps.core.RealmBiomeType;
import io.github.larsonix.trailoforbis.maps.core.RealmLayoutSize;
import io.github.larsonix.trailoforbis.maps.core.RealmMapData;
import io.github.larsonix.trailoforbis.maps.modifiers.RealmModifier;
import io.github.larsonix.trailoforbis.maps.modifiers.RealmModifierType;
import io.github.larsonix.trailoforbis.maps.spawning.RealmEntitySpawner.SpawnRequest;
import io.github.larsonix.trailoforbis.maps.spawning.RealmEntitySpawner.SpawnResult;
import io.github.larsonix.trailoforbis.maps.spawning.WeightedMob.MobClassification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link RealmEntitySpawner}.
 *
 * <p>These tests focus on the testable parts of RealmEntitySpawner:
 * <ul>
 *   <li>SpawnRequest record creation and factory methods</li>
 *   <li>SpawnResult record validation</li>
 *   <li>Request validation logic</li>
 * </ul>
 *
 * <p>Note: Actual spawning via NPCPlugin cannot be unit tested without
 * the Hytale server runtime, so these tests focus on the data structures
 * and validation logic.
 */
@DisplayName("RealmEntitySpawner Tests")
@ExtendWith(MockitoExtension.class)
class RealmEntitySpawnerTest {

    @Mock
    private Ref<EntityStore> entityRef;

    private UUID realmId;
    private Vector3d position;
    private RealmMapData mapData;
    private RealmMapData mapDataWithModifiers;

    @BeforeEach
    void setUp() {
        realmId = UUID.randomUUID();
        position = new Vector3d(10.5, 64.0, -20.3);
        mapData = RealmMapData.create(50, GearRarity.RARE, RealmBiomeType.FOREST, RealmLayoutSize.MEDIUM);

        // Create map data with modifiers for stat calculation tests
        mapDataWithModifiers = RealmMapData.builder()
            .level(50)
            .rarity(GearRarity.RARE)
            .biome(RealmBiomeType.FOREST)
            .size(RealmLayoutSize.MEDIUM)
            .addPrefix(new RealmModifier(RealmModifierType.MONSTER_HEALTH, 30, false))    // +30% health
            .addPrefix(new RealmModifier(RealmModifierType.MONSTER_DAMAGE, 20, false))    // +20% damage
            .addPrefix(new RealmModifier(RealmModifierType.MONSTER_SPEED, 10, false))     // +10% speed
            .identified(true)
            .build();
    }

    // ═══════════════════════════════════════════════════════════════════
    // SPAWN REQUEST TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("SpawnRequest Creation")
    class SpawnRequestCreationTests {

        @Test
        @DisplayName("initial creates non-reinforcement request")
        void initial_createsNonReinforcementRequest() {
            SpawnRequest request = SpawnRequest.initial(
                realmId, "trork_warrior", MobClassification.NORMAL,
                position, false, mapData, 1);

            assertEquals(realmId, request.realmId());
            assertEquals("trork_warrior", request.mobTypeId());
            assertEquals(MobClassification.NORMAL, request.classification());
            assertEquals(position, request.position());
            assertEquals(0, request.waveNumber());
            assertFalse(request.isReinforcement());
            assertFalse(request.isElite());
            assertEquals(mapData, request.mapData());
            assertEquals(1, request.playerCount());
        }

        @Test
        @DisplayName("initial with elite flag creates elite request")
        void initial_withElite_createsEliteRequest() {
            SpawnRequest request = SpawnRequest.initial(
                realmId, "trork_warrior", MobClassification.NORMAL,
                position, true, mapData, 1);

            assertTrue(request.isElite());
            assertFalse(request.isReinforcement());
        }

        @Test
        @DisplayName("reinforcement creates reinforcement request")
        void reinforcement_createsReinforcementRequest() {
            SpawnRequest request = SpawnRequest.reinforcement(
                realmId, "trork_archer", MobClassification.NORMAL,
                position, 3, false, mapData, 2);

            assertEquals(realmId, request.realmId());
            assertEquals("trork_archer", request.mobTypeId());
            assertEquals(MobClassification.NORMAL, request.classification());
            assertEquals(position, request.position());
            assertEquals(3, request.waveNumber());
            assertTrue(request.isReinforcement());
            assertFalse(request.isElite());
            assertEquals(2, request.playerCount());
        }

        @Test
        @DisplayName("reinforcement with elite creates elite reinforcement")
        void reinforcement_withElite_createsEliteReinforcement() {
            SpawnRequest request = SpawnRequest.reinforcement(
                realmId, "trork_warrior", MobClassification.NORMAL,
                position, 2, true, mapData, 1);

            assertTrue(request.isElite());
            assertTrue(request.isReinforcement());
            assertEquals(2, request.waveNumber());
        }

        @Test
        @DisplayName("boss classification preserved in request")
        void bossClassification_preservedInRequest() {
            SpawnRequest request = SpawnRequest.initial(
                realmId, "trork_chieftain", MobClassification.BOSS,
                position, false, mapData, 1);

            assertEquals(MobClassification.BOSS, request.classification());
        }

        @Test
        @DisplayName("position is preserved exactly")
        void position_preservedExactly() {
            Vector3d precisePos = new Vector3d(123.456, 78.901, -234.567);
            SpawnRequest request = SpawnRequest.initial(
                realmId, "mob", MobClassification.NORMAL,
                precisePos, false, mapData, 1);

            assertEquals(123.456, request.position().x, 0.0001);
            assertEquals(78.901, request.position().y, 0.0001);
            assertEquals(-234.567, request.position().z, 0.0001);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // SPAWN RESULT TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("SpawnResult")
    class SpawnResultTests {

        @Test
        @DisplayName("success creates successful result with entity ref")
        void success_createsSuccessfulResult() {
            SpawnResult result = SpawnResult.success(
                entityRef, "trork_warrior", MobClassification.NORMAL);

            assertTrue(result.success());
            assertEquals(entityRef, result.entityRef());
            assertEquals("trork_warrior", result.mobTypeId());
            assertEquals(MobClassification.NORMAL, result.classification());
            assertNull(result.error());
        }

        @Test
        @DisplayName("failed creates failure result with error message")
        void failed_createsFailureResult() {
            SpawnResult result = SpawnResult.failed("Unknown NPC type: invalid_mob");

            assertFalse(result.success());
            assertNull(result.entityRef());
            assertNull(result.mobTypeId());
            assertNull(result.classification());
            assertEquals("Unknown NPC type: invalid_mob", result.error());
        }

        @Test
        @DisplayName("getEntityUuid returns null for failed result")
        void getEntityUuid_failedResult_returnsNull() {
            SpawnResult result = SpawnResult.failed("Error");

            assertNull(result.getEntityUuid());
        }

        @Test
        @DisplayName("getEntityUuid returns null for successful result (needs UUIDComponent)")
        void getEntityUuid_successfulResult_returnsNull() {
            // The getEntityUuid method currently returns null because
            // it would need to access UUIDComponent from the entity store
            SpawnResult result = SpawnResult.success(
                entityRef, "mob", MobClassification.NORMAL);

            // Currently always returns null - see implementation note
            assertNull(result.getEntityUuid());
        }

        @Test
        @DisplayName("success preserves boss classification")
        void success_preservesBossClassification() {
            SpawnResult result = SpawnResult.success(
                entityRef, "trork_chieftain", MobClassification.BOSS);

            assertEquals(MobClassification.BOSS, result.classification());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // MAP DATA MODIFIER TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Map Data Modifier Effects")
    class MapDataModifierTests {

        @Test
        @DisplayName("mapData with health modifier increases expected health")
        void mapData_withHealthModifier_affectsMultiplier() {
            // The mapData with MONSTER_HEALTH +30% should be passed to spawner
            SpawnRequest request = SpawnRequest.initial(
                realmId, "trork_warrior", MobClassification.NORMAL,
                position, false, mapDataWithModifiers, 1);

            // Verify the map data is preserved (spawner will use it for stat calc)
            assertEquals(1.3f, request.mapData().getMonsterHealthMultiplier(), 0.001);
        }

        @Test
        @DisplayName("mapData with damage modifier increases expected damage")
        void mapData_withDamageModifier_affectsMultiplier() {
            SpawnRequest request = SpawnRequest.initial(
                realmId, "trork_warrior", MobClassification.NORMAL,
                position, false, mapDataWithModifiers, 1);

            assertEquals(1.2f, request.mapData().getMonsterDamageMultiplier(), 0.001);
        }

        @Test
        @DisplayName("player count affects spawn request")
        void playerCount_affectsSpawnRequest() {
            SpawnRequest singlePlayer = SpawnRequest.initial(
                realmId, "mob", MobClassification.NORMAL, position, false, mapData, 1);

            SpawnRequest multiPlayer = SpawnRequest.initial(
                realmId, "mob", MobClassification.NORMAL, position, false, mapData, 4);

            assertEquals(1, singlePlayer.playerCount());
            assertEquals(4, multiPlayer.playerCount());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // CLASSIFICATION COMBINATIONS TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Classification and Elite Combinations")
    class ClassificationCombinationsTests {

        @ParameterizedTest
        @CsvSource({
            "NORMAL, false, false",  // Normal mob, not elite
            "NORMAL, true, false",   // Normal mob, elite
            "BOSS, false, false",    // Boss, not elite
            "BOSS, true, false",     // Boss, elite (elite boss!)
            "NORMAL, false, true",   // Normal reinforcement
            "BOSS, false, true"      // Boss reinforcement
        })
        @DisplayName("All classification/elite/reinforcement combinations are valid")
        void allCombinations_areValid(MobClassification classification, boolean isElite, boolean isReinforcement) {
            SpawnRequest request;
            if (isReinforcement) {
                request = SpawnRequest.reinforcement(
                    realmId, "test_mob", classification, position, 2, isElite, mapData, 1);
            } else {
                request = SpawnRequest.initial(
                    realmId, "test_mob", classification, position, isElite, mapData, 1);
            }

            assertEquals(classification, request.classification());
            assertEquals(isElite, request.isElite());
            assertEquals(isReinforcement, request.isReinforcement());
        }

        @Test
        @DisplayName("Elite boss is a valid combination")
        void eliteBoss_isValidCombination() {
            SpawnRequest request = SpawnRequest.initial(
                realmId, "trork_chieftain", MobClassification.BOSS,
                position, true, mapData, 1);

            assertTrue(request.isElite());
            assertEquals(MobClassification.BOSS, request.classification());
            // Elite bosses get both elite AND boss multipliers
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // LEVEL AND MAP DATA TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Level and MapData")
    class LevelAndMapDataTests {

        @Test
        @DisplayName("mapData level is accessible from request")
        void mapData_levelAccessible() {
            RealmMapData highLevel = RealmMapData.create(
                100, GearRarity.LEGENDARY, RealmBiomeType.VOLCANO, RealmLayoutSize.LARGE);

            SpawnRequest request = SpawnRequest.initial(
                realmId, "fire_elemental", MobClassification.NORMAL,
                position, false, highLevel, 1);

            assertEquals(100, request.mapData().level());
        }

        @Test
        @DisplayName("mapData biome is accessible from request")
        void mapData_biomeAccessible() {
            SpawnRequest request = SpawnRequest.initial(
                realmId, "trork", MobClassification.NORMAL,
                position, false, mapData, 1);

            assertEquals(RealmBiomeType.FOREST, request.mapData().biome());
        }

        @Test
        @DisplayName("mapData size is accessible from request")
        void mapData_sizeAccessible() {
            SpawnRequest request = SpawnRequest.initial(
                realmId, "trork", MobClassification.NORMAL,
                position, false, mapData, 1);

            assertEquals(RealmLayoutSize.MEDIUM, request.mapData().size());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // EQUALITY AND IDENTITY TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Record Equality")
    class RecordEqualityTests {

        @Test
        @DisplayName("SpawnRequest equals works correctly")
        void spawnRequest_equalsWorks() {
            SpawnRequest request1 = SpawnRequest.initial(
                realmId, "trork", MobClassification.NORMAL, position, false, mapData, 1);

            SpawnRequest request2 = SpawnRequest.initial(
                realmId, "trork", MobClassification.NORMAL, position, false, mapData, 1);

            SpawnRequest differentMob = SpawnRequest.initial(
                realmId, "wolf", MobClassification.NORMAL, position, false, mapData, 1);

            assertEquals(request1, request2);
            assertNotEquals(request1, differentMob);
        }

        @Test
        @DisplayName("SpawnResult equals works correctly")
        void spawnResult_equalsWorks() {
            SpawnResult result1 = SpawnResult.failed("Error A");
            SpawnResult result2 = SpawnResult.failed("Error A");
            SpawnResult result3 = SpawnResult.failed("Error B");

            assertEquals(result1, result2);
            assertNotEquals(result1, result3);
        }
    }
}
