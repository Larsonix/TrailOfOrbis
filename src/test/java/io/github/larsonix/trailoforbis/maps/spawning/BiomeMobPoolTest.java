package io.github.larsonix.trailoforbis.maps.spawning;

import io.github.larsonix.trailoforbis.maps.core.RealmBiomeType;
import io.github.larsonix.trailoforbis.maps.spawning.WeightedMob.MobClassification;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link BiomeMobPool}.
 *
 * <p>Note: Elite is now a spawn-time modifier, not a separate mob pool.
 * The pool only contains normal and boss mobs. Any mob can roll to become
 * elite at spawn time based on the elite chance settings.
 */
@DisplayName("BiomeMobPool Tests")
class BiomeMobPoolTest {

    private BiomeMobPool forestPool;
    private Random random;

    @BeforeEach
    void setUp() {
        forestPool = BiomeMobPool.builder(RealmBiomeType.FOREST)
            .addNormal("trork_warrior", 30)
            .addNormal("trork_archer", 20)
            .addNormal("wolf", 15)
            .addNormal("trork_captain", 10)  // Former elite, now in normal pool
            .addNormal("dire_wolf", 5)       // Former elite, now in normal pool
            .addBoss("trork_chieftain", 5)
            .build();

        random = new Random(42); // Fixed seed for reproducibility
    }

    // ═══════════════════════════════════════════════════════════════════
    // BUILDER TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Builder creates pool with correct biome")
    void builderSetsBiome() {
        assertEquals(RealmBiomeType.FOREST, forestPool.getBiome());
    }

    @Test
    @DisplayName("Builder correctly categorizes mobs by classification")
    void builderCategorizesMobs() {
        // All 5 mobs are now in normal pool (former elite mobs merged)
        assertEquals(5, forestPool.getNormalMobs().size());
        assertEquals(1, forestPool.getBossMobs().size());
    }

    @Test
    @DisplayName("Builder calculates total weights correctly")
    void builderCalculatesWeights() {
        // Normal: 30+20+15+10+5 = 80
        assertEquals(80, forestPool.getNormalTotalWeight());
        assertEquals(5, forestPool.getBossTotalWeight());
    }

    @Test
    @DisplayName("Builder with level restrictions creates proper mobs")
    void builderWithLevelRestrictions() {
        BiomeMobPool pool = BiomeMobPool.builder(RealmBiomeType.VOLCANO)
            .addNormal("fire_imp", 20, 1, 25)
            .addNormal("fire_elemental", 20, 25, 75)
            .addNormal("infernal", 20, 75, -1)
            .build();

        List<WeightedMob> mobs = pool.getNormalMobs();
        assertEquals(3, mobs.size());

        // Check level restrictions
        assertTrue(mobs.get(0).canSpawnAtLevel(10));
        assertFalse(mobs.get(0).canSpawnAtLevel(50));

        assertTrue(mobs.get(1).canSpawnAtLevel(50));
        assertFalse(mobs.get(1).canSpawnAtLevel(10));

        assertTrue(mobs.get(2).canSpawnAtLevel(100));
        assertFalse(mobs.get(2).canSpawnAtLevel(50));
    }

    @Test
    @DisplayName("add() method routes by classification")
    void addMethodRoutesByClassification() {
        BiomeMobPool pool = BiomeMobPool.builder(RealmBiomeType.FOREST)
            .add(WeightedMob.normal("mob1", 10))
            .add(WeightedMob.boss("mob2", 10))
            .build();

        assertEquals(1, pool.getNormalMobs().size());
        assertEquals(1, pool.getBossMobs().size());
    }

    // ═══════════════════════════════════════════════════════════════════
    // FACTORY METHOD TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("empty() creates empty pool")
    void emptyCreatesEmptyPool() {
        BiomeMobPool empty = BiomeMobPool.empty(RealmBiomeType.DESERT);

        assertEquals(RealmBiomeType.DESERT, empty.getBiome());
        assertTrue(empty.isEmpty());
        assertEquals(0, empty.getTotalMobCount());
        assertFalse(empty.hasNormalMobs());
        assertFalse(empty.hasBossMobs());
    }

    // ═══════════════════════════════════════════════════════════════════
    // SELECTION TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("selectNormalMob returns mob from normal pool")
    void selectNormalMobReturnsNormalMob() {
        WeightedMob selected = forestPool.selectNormalMob(random);

        assertNotNull(selected);
        assertEquals(MobClassification.NORMAL, selected.classification());
        assertTrue(forestPool.getNormalMobs().contains(selected));
    }

    @Test
    @DisplayName("selectBossMob returns mob from boss pool")
    void selectBossMobReturnsBossMob() {
        WeightedMob selected = forestPool.selectBossMob(random);

        assertNotNull(selected);
        assertEquals(MobClassification.BOSS, selected.classification());
        assertTrue(forestPool.getBossMobs().contains(selected));
    }

    @Test
    @DisplayName("Selection from empty pool returns null")
    void selectFromEmptyReturnsNull() {
        BiomeMobPool empty = BiomeMobPool.empty(RealmBiomeType.DESERT);

        assertNull(empty.selectNormalMob(random));
        assertNull(empty.selectBossMob(random));
    }

    @Test
    @DisplayName("selectMob uses classification parameter correctly")
    void selectMobByClassification() {
        WeightedMob normal = forestPool.selectMob(MobClassification.NORMAL, random);
        WeightedMob boss = forestPool.selectMob(MobClassification.BOSS, random);

        assertEquals(MobClassification.NORMAL, normal.classification());
        assertEquals(MobClassification.BOSS, boss.classification());
    }

    @Test
    @DisplayName("Weight distribution affects selection probability")
    void weightDistributionAffectsSelection() {
        // Create a pool with uneven weights
        BiomeMobPool pool = BiomeMobPool.builder(RealmBiomeType.FOREST)
            .addNormal("heavy_weight", 90)
            .addNormal("light_weight", 10)
            .build();

        // Run many selections and count
        Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < 1000; i++) {
            WeightedMob mob = pool.selectNormalMob(new Random());
            counts.merge(mob.mobTypeId(), 1, Integer::sum);
        }

        // Heavy weight should be selected significantly more often
        assertTrue(counts.get("heavy_weight") > counts.get("light_weight") * 5,
            "Heavy weight mob should be selected ~9x more often");
    }

    // ═══════════════════════════════════════════════════════════════════
    // LEVEL-RESTRICTED SELECTION TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("selectNormalMobForLevel filters by level")
    void selectNormalMobForLevelFilters() {
        BiomeMobPool pool = BiomeMobPool.builder(RealmBiomeType.VOLCANO)
            .addNormal("fire_imp", 20, 1, 25)
            .addNormal("fire_elemental", 20, 25, 75)
            .addNormal("infernal", 20, 75, -1)
            .build();

        // Level 10: Only fire_imp is eligible
        for (int i = 0; i < 10; i++) {
            WeightedMob selected = pool.selectNormalMobForLevel(10, new Random());
            assertEquals("fire_imp", selected.mobTypeId());
        }

        // Level 50: Only fire_elemental is eligible
        for (int i = 0; i < 10; i++) {
            WeightedMob selected = pool.selectNormalMobForLevel(50, new Random());
            assertEquals("fire_elemental", selected.mobTypeId());
        }

        // Level 100: Only infernal is eligible
        for (int i = 0; i < 10; i++) {
            WeightedMob selected = pool.selectNormalMobForLevel(100, new Random());
            assertEquals("infernal", selected.mobTypeId());
        }
    }

    @Test
    @DisplayName("selectMobForLevel returns null when no mobs qualify")
    void selectMobForLevelReturnsNullWhenNoneQualify() {
        BiomeMobPool pool = BiomeMobPool.builder(RealmBiomeType.VOLCANO)
            .addNormal("fire_elemental", 20, 50, 100)
            .build();

        assertNull(pool.selectNormalMobForLevel(10, random));
        assertNull(pool.selectNormalMobForLevel(200, random));
    }

    // ═══════════════════════════════════════════════════════════════════
    // QUERY METHOD TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getTotalMobCount returns correct total")
    void getTotalMobCount() {
        // 5 normal + 1 boss = 6 total
        assertEquals(6, forestPool.getTotalMobCount());
    }

    @Test
    @DisplayName("getAllMobTypeIds returns all unique IDs")
    void getAllMobTypeIds() {
        Set<String> ids = forestPool.getAllMobTypeIds();

        assertEquals(6, ids.size());
        assertTrue(ids.contains("trork_warrior"));
        assertTrue(ids.contains("trork_archer"));
        assertTrue(ids.contains("wolf"));
        assertTrue(ids.contains("trork_captain"));
        assertTrue(ids.contains("dire_wolf"));
        assertTrue(ids.contains("trork_chieftain"));
    }

    @Test
    @DisplayName("getMobsForLevel returns only eligible mobs")
    void getMobsForLevelReturnsEligible() {
        BiomeMobPool pool = BiomeMobPool.builder(RealmBiomeType.VOLCANO)
            .addNormal("fire_imp", 20, 1, 25)
            .addNormal("fire_elemental", 20, 25, 75)
            .addNormal("infernal_knight", 10, 50, -1)  // Former elite, now normal
            .addBoss("magma_lord", 5)
            .build();

        // Level 10: fire_imp + magma_lord (no level restriction)
        List<WeightedMob> level10 = pool.getMobsForLevel(10);
        assertEquals(2, level10.size());

        // Level 60: fire_elemental + infernal_knight + magma_lord
        List<WeightedMob> level60 = pool.getMobsForLevel(60);
        assertEquals(3, level60.size());
    }

    @Test
    @DisplayName("hasXxxMobs methods return correct boolean")
    void hasMobsMethods() {
        assertTrue(forestPool.hasNormalMobs());
        assertTrue(forestPool.hasBossMobs());
        assertFalse(forestPool.isEmpty());

        BiomeMobPool onlyNormal = BiomeMobPool.builder(RealmBiomeType.FOREST)
            .addNormal("trork", 10)
            .build();

        assertTrue(onlyNormal.hasNormalMobs());
        assertFalse(onlyNormal.hasBossMobs());
    }

    // ═══════════════════════════════════════════════════════════════════
    // IMMUTABILITY TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Mob lists are immutable")
    void mobListsAreImmutable() {
        assertThrows(UnsupportedOperationException.class, () ->
            forestPool.getNormalMobs().add(WeightedMob.normal("hacker", 100)));
    }

    // ═══════════════════════════════════════════════════════════════════
    // OBJECT METHOD TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("equals and hashCode work correctly")
    void equalsAndHashCode() {
        BiomeMobPool pool1 = BiomeMobPool.builder(RealmBiomeType.FOREST)
            .addNormal("trork", 10)
            .build();

        BiomeMobPool pool2 = BiomeMobPool.builder(RealmBiomeType.FOREST)
            .addNormal("trork", 10)
            .build();

        BiomeMobPool pool3 = BiomeMobPool.builder(RealmBiomeType.DESERT)
            .addNormal("trork", 10)
            .build();

        assertEquals(pool1, pool2);
        assertEquals(pool1.hashCode(), pool2.hashCode());
        assertNotEquals(pool1, pool3);
    }

    @Test
    @DisplayName("toString provides useful information")
    void toStringFormat() {
        String str = forestPool.toString();

        assertTrue(str.contains("FOREST"));
        assertTrue(str.contains("5 normal"));
        assertTrue(str.contains("1 boss"));
    }
}
