package io.github.larsonix.trailoforbis.maps.templates;

import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import io.github.larsonix.trailoforbis.maps.core.RealmBiomeType;
import io.github.larsonix.trailoforbis.maps.core.RealmLayoutSize;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link RealmTemplate}.
 */
@DisplayName("RealmTemplate")
class RealmTemplateTest {

    private static final String TEMPLATE_NAME = "Realm_Forest_medium";
    private static final RealmBiomeType BIOME = RealmBiomeType.FOREST;
    private static final RealmLayoutSize SIZE = RealmLayoutSize.MEDIUM;
    private static final Transform DEFAULT_SPAWN = new Transform(
        new Vector3d(0, 64, 0),
        new Vector3f(0, 0, 0)
    );
    private static final Transform DEFAULT_EXIT = new Transform(
        new Vector3d(5, 64, 0),
        new Vector3f(0, 0, 0)
    );

    @Nested
    @DisplayName("Builder")
    class Builder {

        @Test
        @DisplayName("Should create template with minimum required fields")
        void shouldCreateTemplateWithMinimumFields() {
            RealmTemplate template = RealmTemplate.builder(TEMPLATE_NAME, BIOME, SIZE)
                .spawnLocation(DEFAULT_SPAWN)
                .estimatedMonsters(10)
                .build();

            assertEquals(TEMPLATE_NAME, template.templateName());
            assertEquals(BIOME, template.biome());
            assertEquals(SIZE, template.size());
            assertEquals(DEFAULT_SPAWN, template.playerSpawn());
            assertEquals(10, template.estimatedMonsters());
        }

        @Test
        @DisplayName("Should set optional exit portal location")
        void shouldSetOptionalExitPortalLocation() {
            RealmTemplate template = RealmTemplate.builder(TEMPLATE_NAME, BIOME, SIZE)
                .spawnLocation(DEFAULT_SPAWN)
                .exitPortalLocation(DEFAULT_EXIT)
                .estimatedMonsters(10)
                .build();

            assertEquals(DEFAULT_EXIT, template.exitPortalLocation());
        }

        @Test
        @DisplayName("Should use spawn as default exit location if not set")
        void shouldUseSpawnAsDefaultExitLocation() {
            RealmTemplate template = RealmTemplate.builder(TEMPLATE_NAME, BIOME, SIZE)
                .spawnLocation(DEFAULT_SPAWN)
                .estimatedMonsters(10)
                .build();

            assertEquals(DEFAULT_SPAWN, template.exitPortalLocation());
        }

        @Test
        @DisplayName("Should set spawn points")
        void shouldSetSpawnPoints() {
            Vector3d pos = new Vector3d(10, 64, 10);
            List<MonsterSpawnPoint> spawnPoints = List.of(
                MonsterSpawnPoint.normal(pos, 5),
                MonsterSpawnPoint.elite(pos),
                MonsterSpawnPoint.boss(pos)
            );

            RealmTemplate template = RealmTemplate.builder(TEMPLATE_NAME, BIOME, SIZE)
                .spawnLocation(DEFAULT_SPAWN)
                .spawnPoints(spawnPoints)
                .estimatedMonsters(10)
                .build();

            assertEquals(3, template.spawnPoints().size());
        }

        @Test
        @DisplayName("Should set multiple spawn points via list")
        void shouldSetMultipleSpawnPointsViaList() {
            Vector3d pos = new Vector3d(10, 64, 10);

            // Build a mutable list then pass it
            List<MonsterSpawnPoint> points = new ArrayList<>();
            points.add(MonsterSpawnPoint.normal(pos, 5));
            points.add(MonsterSpawnPoint.elite(pos));

            RealmTemplate template = RealmTemplate.builder(TEMPLATE_NAME, BIOME, SIZE)
                .spawnLocation(DEFAULT_SPAWN)
                .spawnPoints(points)
                .estimatedMonsters(10)
                .build();

            assertEquals(2, template.spawnPoints().size());
        }

        @Test
        @DisplayName("Should require non-null template name at build time")
        void shouldRequireNonNullTemplateName() {
            // Builder accepts null templateName, but record validation throws at build()
            assertThrows(NullPointerException.class, () ->
                RealmTemplate.builder(null, BIOME, SIZE)
                    .spawnLocation(DEFAULT_SPAWN)
                    .build());
        }

        @Test
        @DisplayName("Should require non-null biome at build time")
        void shouldRequireNonNullBiome() {
            // Builder accepts null biome, but record validation throws at build()
            assertThrows(NullPointerException.class, () ->
                RealmTemplate.builder(TEMPLATE_NAME, null, SIZE)
                    .spawnLocation(DEFAULT_SPAWN)
                    .build());
        }

        @Test
        @DisplayName("Should require non-null size at builder creation")
        void shouldRequireNonNullSize() {
            // Size is used immediately in builder constructor (calculateMonsterCount)
            assertThrows(NullPointerException.class, () ->
                RealmTemplate.builder(TEMPLATE_NAME, BIOME, null));
        }
    }

    @Nested
    @DisplayName("Computed properties")
    class ComputedProperties {

        private RealmTemplate template;

        @BeforeEach
        void setUp() {
            Vector3d pos = new Vector3d(10, 64, 10);
            List<MonsterSpawnPoint> spawnPoints = List.of(
                MonsterSpawnPoint.normal(pos, 5),
                MonsterSpawnPoint.normal(pos, 3),
                MonsterSpawnPoint.elite(pos),
                MonsterSpawnPoint.elite(pos),
                MonsterSpawnPoint.boss(pos),
                MonsterSpawnPoint.pack(pos, 4)
            );

            template = RealmTemplate.builder(TEMPLATE_NAME, BIOME, SIZE)
                .spawnLocation(DEFAULT_SPAWN)
                .spawnPoints(spawnPoints)
                .estimatedMonsters(20)
                .build();
        }

        @Test
        @DisplayName("Should count boss spawn points")
        void shouldCountBossSpawnPoints() {
            assertEquals(1, template.getBossSpawnCount());
        }

        @Test
        @DisplayName("Should count elite spawn points")
        void shouldCountEliteSpawnPoints() {
            assertEquals(2, template.getEliteSpawnCount());
        }

        @Test
        @DisplayName("Should calculate total spawn capacity")
        void shouldCalculateTotalSpawnCapacity() {
            // normal(5) + normal(3) + elite(1) + elite(1) + boss(1) + pack(4) = 15
            assertEquals(15, template.getTotalSpawnCapacity());
        }

        @Test
        @DisplayName("Should return 0 for empty spawn points")
        void shouldReturnZeroForEmptySpawnPoints() {
            RealmTemplate emptyTemplate = RealmTemplate.builder(TEMPLATE_NAME, BIOME, SIZE)
                .spawnLocation(DEFAULT_SPAWN)
                .estimatedMonsters(10)
                .build();

            assertEquals(0, emptyTemplate.getBossSpawnCount());
            assertEquals(0, emptyTemplate.getEliteSpawnCount());
            assertEquals(0, emptyTemplate.getTotalSpawnCapacity());
        }
    }

    @Nested
    @DisplayName("Record constructor")
    class RecordConstructor {

        @Test
        @DisplayName("Should create template with all fields")
        void shouldCreateTemplateWithAllFields() {
            Vector3d pos = new Vector3d(10, 64, 10);
            List<MonsterSpawnPoint> spawnPoints = List.of(
                MonsterSpawnPoint.normal(pos, 5)
            );

            RealmTemplate template = new RealmTemplate(
                TEMPLATE_NAME,
                BIOME,
                SIZE,
                10,
                spawnPoints,
                DEFAULT_SPAWN,
                DEFAULT_EXIT
            );

            assertEquals(TEMPLATE_NAME, template.templateName());
            assertEquals(BIOME, template.biome());
            assertEquals(SIZE, template.size());
            assertEquals(10, template.estimatedMonsters());
            assertEquals(1, template.spawnPoints().size());
            assertEquals(DEFAULT_SPAWN, template.playerSpawn());
            assertEquals(DEFAULT_EXIT, template.exitPortalLocation());
        }

        @Test
        @DisplayName("Should require non-null spawn points")
        void shouldRequireNonNullSpawnPoints() {
            // RealmTemplate constructor validates that spawn points are non-null
            assertThrows(NullPointerException.class, () ->
                new RealmTemplate(
                    TEMPLATE_NAME,
                    BIOME,
                    SIZE,
                    10,
                    null,
                    DEFAULT_SPAWN,
                    DEFAULT_EXIT
                ));
        }

        @Test
        @DisplayName("Should make spawn points list immutable")
        void shouldMakeSpawnPointsListImmutable() {
            Vector3d pos = new Vector3d(10, 64, 10);
            List<MonsterSpawnPoint> spawnPoints = List.of(
                MonsterSpawnPoint.normal(pos, 5)
            );

            RealmTemplate template = RealmTemplate.builder(TEMPLATE_NAME, BIOME, SIZE)
                .spawnLocation(DEFAULT_SPAWN)
                .spawnPoints(spawnPoints)
                .estimatedMonsters(10)
                .build();

            assertThrows(UnsupportedOperationException.class, () ->
                template.spawnPoints().add(MonsterSpawnPoint.boss(pos)));
        }
    }

    @Nested
    @DisplayName("Spawn point filtering")
    class SpawnPointFiltering {

        private RealmTemplate template;

        @BeforeEach
        void setUp() {
            Vector3d pos1 = new Vector3d(10, 64, 10);
            Vector3d pos2 = new Vector3d(20, 64, 20);
            Vector3d pos3 = new Vector3d(30, 64, 30);

            List<MonsterSpawnPoint> spawnPoints = List.of(
                MonsterSpawnPoint.normal(pos1, 5),
                MonsterSpawnPoint.elite(pos2),
                MonsterSpawnPoint.boss(pos3),
                MonsterSpawnPoint.pack(pos1, 4),
                MonsterSpawnPoint.normal(pos2, 3)
            );

            template = RealmTemplate.builder(TEMPLATE_NAME, BIOME, SIZE)
                .spawnLocation(DEFAULT_SPAWN)
                .spawnPoints(spawnPoints)
                .estimatedMonsters(20)
                .build();
        }

        @Test
        @DisplayName("Should filter spawn points by type")
        void shouldFilterSpawnPointsByType() {
            long normalCount = template.spawnPoints().stream()
                .filter(MonsterSpawnPoint::isNormalSpawn)
                .count();
            assertEquals(2, normalCount);

            long eliteCount = template.spawnPoints().stream()
                .filter(MonsterSpawnPoint::isEliteSpawn)
                .count();
            assertEquals(1, eliteCount);

            long bossCount = template.spawnPoints().stream()
                .filter(MonsterSpawnPoint::isBossSpawn)
                .count();
            assertEquals(1, bossCount);

            long packCount = template.spawnPoints().stream()
                .filter(MonsterSpawnPoint::isPackSpawn)
                .count();
            assertEquals(1, packCount);
        }

        @Test
        @DisplayName("Should get spawn points by type using helper method")
        void shouldGetSpawnPointsByTypeUsingHelperMethod() {
            List<MonsterSpawnPoint> normalSpawns =
                template.getSpawnPointsByType(MonsterSpawnPoint.SpawnType.NORMAL);
            assertEquals(2, normalSpawns.size());

            List<MonsterSpawnPoint> eliteSpawns =
                template.getSpawnPointsByType(MonsterSpawnPoint.SpawnType.ELITE);
            assertEquals(1, eliteSpawns.size());

            List<MonsterSpawnPoint> bossSpawns =
                template.getSpawnPointsByType(MonsterSpawnPoint.SpawnType.BOSS);
            assertEquals(1, bossSpawns.size());

            List<MonsterSpawnPoint> packSpawns =
                template.getSpawnPointsByType(MonsterSpawnPoint.SpawnType.PACK);
            assertEquals(1, packSpawns.size());
        }
    }

    @Nested
    @DisplayName("Copy methods")
    class CopyMethods {

        @Test
        @DisplayName("withSpawnPoints should create new template with different spawn points")
        void withSpawnPointsShouldCreateNewTemplate() {
            RealmTemplate original = RealmTemplate.builder(TEMPLATE_NAME, BIOME, SIZE)
                .spawnLocation(DEFAULT_SPAWN)
                .estimatedMonsters(10)
                .build();

            Vector3d pos = new Vector3d(10, 64, 10);
            List<MonsterSpawnPoint> newPoints = List.of(
                MonsterSpawnPoint.boss(pos)
            );

            RealmTemplate updated = original.withSpawnPoints(newPoints);

            assertNotSame(original, updated);
            assertEquals(0, original.spawnPoints().size());
            assertEquals(1, updated.spawnPoints().size());
            assertEquals(original.templateName(), updated.templateName());
        }

        @Test
        @DisplayName("withEstimatedMonsters should create new template with different count")
        void withEstimatedMonstersShouldCreateNewTemplate() {
            RealmTemplate original = RealmTemplate.builder(TEMPLATE_NAME, BIOME, SIZE)
                .spawnLocation(DEFAULT_SPAWN)
                .estimatedMonsters(10)
                .build();

            RealmTemplate updated = original.withEstimatedMonsters(50);

            assertNotSame(original, updated);
            assertEquals(10, original.estimatedMonsters());
            assertEquals(50, updated.estimatedMonsters());
            assertEquals(original.templateName(), updated.templateName());
        }
    }

    @Nested
    @DisplayName("Utility methods")
    class UtilityMethods {

        @Test
        @DisplayName("hasSpawnPoints should return true when spawn points exist")
        void hasSpawnPointsShouldReturnTrueWhenSpawnPointsExist() {
            Vector3d pos = new Vector3d(10, 64, 10);
            RealmTemplate template = RealmTemplate.builder(TEMPLATE_NAME, BIOME, SIZE)
                .spawnLocation(DEFAULT_SPAWN)
                .spawnPoints(List.of(MonsterSpawnPoint.normal(pos, 5)))
                .estimatedMonsters(10)
                .build();

            assertTrue(template.hasSpawnPoints());
        }

        @Test
        @DisplayName("hasSpawnPoints should return false when no spawn points")
        void hasSpawnPointsShouldReturnFalseWhenNoSpawnPoints() {
            RealmTemplate template = RealmTemplate.builder(TEMPLATE_NAME, BIOME, SIZE)
                .spawnLocation(DEFAULT_SPAWN)
                .estimatedMonsters(10)
                .build();

            assertFalse(template.hasSpawnPoints());
        }

        @Test
        @DisplayName("getStandardTemplateName should return base name for medium (no suffix)")
        void getStandardTemplateNameShouldReturnCorrectName() {
            // MEDIUM uses base template name without size suffix
            String name = RealmTemplate.getStandardTemplateName(
                RealmBiomeType.FOREST,
                RealmLayoutSize.MEDIUM
            );

            assertEquals("Realm_Forest", name);
        }
    }
}
