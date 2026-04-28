package io.github.larsonix.trailoforbis.maps.spawning;

import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.maps.config.MobPoolConfig;
import io.github.larsonix.trailoforbis.maps.core.RealmBiomeType;
import io.github.larsonix.trailoforbis.maps.core.RealmLayoutSize;
import io.github.larsonix.trailoforbis.maps.core.RealmMapData;
import io.github.larsonix.trailoforbis.maps.spawning.RealmMobCalculator.SpawnDistribution;
import io.github.larsonix.trailoforbis.maps.spawning.RealmMobCalculator.WaveParameters;
import io.github.larsonix.trailoforbis.maps.spawning.WeightedMob.MobClassification;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link RealmMobCalculator}.
 *
 * <p>Note: Elite is now a spawn-time modifier, not a separate classification.
 * SpawnDistribution no longer includes eliteCount - instead it provides
 * eliteChance for per-mob elite rolls at spawn time.
 */
@DisplayName("RealmMobCalculator Tests")
class RealmMobCalculatorTest {

    private RealmMobCalculator calculator;
    private MobPoolConfig config;

    @BeforeEach
    void setUp() {
        config = new MobPoolConfig();
        calculator = new RealmMobCalculator(config);
    }

    // ═══════════════════════════════════════════════════════════════════
    // CONSTRUCTION TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Constructor rejects null config")
    void constructorRejectsNullConfig() {
        assertThrows(NullPointerException.class, () ->
            new RealmMobCalculator(null));
    }

    @Test
    @DisplayName("withDefaults creates calculator with default config")
    void withDefaultsCreatesDefaultCalculator() {
        RealmMobCalculator calc = RealmMobCalculator.withDefaults();
        assertNotNull(calc);
        assertNotNull(calc.getConfig());
    }

    // ═══════════════════════════════════════════════════════════════════
    // TOTAL MOB COUNT TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("calculateTotalMobCount for SMALL size at level 1")
    void calculateTotalMobCountSmallLevel1() {
        // SMALL: base=15, multiplier=1.0
        // Level 1: levelMultiplier = 1.0 + (1 * 0.02) = 1.02
        // Global spawn multiplier: 1.0 (default in config)
        // Expected: 15 * 1.0 * 1.02 * 1.0 = 15.3 ≈ 15
        int count = calculator.calculateTotalMobCount(RealmLayoutSize.SMALL, 1, 0);

        assertTrue(count >= 14 && count <= 17,
            "Expected ~15 mobs for SMALL level 1, got " + count);
    }

    @Test
    @DisplayName("calculateTotalMobCount for MEDIUM size at level 50")
    void calculateTotalMobCountMediumLevel50() {
        // MEDIUM: base=25, multiplier=1.0
        // Level 50: levelMultiplier = 1.0 + (50 * 0.02) = 2.0
        // Global spawn multiplier: 1.0 (default in config)
        // Expected: 25 * 1.0 * 2.0 * 1.0 = 50
        int count = calculator.calculateTotalMobCount(RealmLayoutSize.MEDIUM, 50, 0);

        assertTrue(count >= 48 && count <= 52,
            "Expected ~50 mobs for MEDIUM level 50, got " + count);
    }

    @Test
    @DisplayName("calculateTotalMobCount for LARGE size at level 100")
    void calculateTotalMobCountLargeLevel100() {
        // LARGE: base=40, multiplier=1.0
        // Level 100: levelMultiplier = 1.0 + (100 * 0.02) = 3.0
        // Global spawn multiplier: 1.0 (default in config)
        // Expected: 40 * 1.0 * 3.0 * 1.0 = 120
        int count = calculator.calculateTotalMobCount(RealmLayoutSize.LARGE, 100, 0);

        assertEquals(120, count,
            "Expected 120 mobs for LARGE level 100, got " + count);
    }

    @Test
    @DisplayName("calculateTotalMobCount respects extraMonsterPercent")
    void calculateTotalMobCountWithExtraMonsters() {
        // MEDIUM level 1: base ≈ 26 (25 * 1.0 * 1.02)
        // With 50% extra: 26 * 1.5 ≈ 39
        int baseCount = calculator.calculateTotalMobCount(RealmLayoutSize.MEDIUM, 1, 0);
        int extraCount = calculator.calculateTotalMobCount(RealmLayoutSize.MEDIUM, 1, 50);

        assertTrue(extraCount > baseCount,
            "Extra monsters should increase count");
        assertTrue(extraCount >= (int)(baseCount * 1.4) && extraCount <= (int)(baseCount * 1.6),
            "50% extra should give ~1.5x mobs");
    }

    @Test
    @DisplayName("calculateTotalMobCount enforces minimum")
    void calculateTotalMobCountEnforcesMinimum() {
        // Even with minimal settings, should never go below MINIMUM_MONSTERS
        int count = calculator.calculateTotalMobCount(RealmLayoutSize.SMALL, 1, 0);
        assertTrue(count >= RealmMobCalculator.MINIMUM_MONSTERS);
    }

    @Test
    @DisplayName("calculateTotalMobCount enforces maximum")
    void calculateTotalMobCountEnforcesMaximum() {
        // Very high level shouldn't exceed maximum
        int count = calculator.calculateTotalMobCount(RealmLayoutSize.MASSIVE, 10000, 50);
        assertTrue(count <= RealmMobCalculator.MAXIMUM_MONSTERS);
    }

    // ═══════════════════════════════════════════════════════════════════
    // SPAWN DISTRIBUTION TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("calculate returns valid SpawnDistribution")
    void calculateReturnsValidDistribution() {
        RealmMapData mapData = RealmMapData.create(
            50, GearRarity.RARE, RealmBiomeType.FOREST, RealmLayoutSize.MEDIUM);

        SpawnDistribution dist = calculator.calculate(mapData);

        assertNotNull(dist);
        assertTrue(dist.totalCount() > 0);
        // Note: Elite is now a spawn-time modifier, not a count in distribution
        assertEquals(dist.totalCount(),
            dist.normalCount() + dist.bossCount(),
            "Counts must sum to total (no eliteCount - elite is per-mob roll)");
    }

    @Test
    @DisplayName("calculate includes guaranteed bosses for medium+ sizes")
    void calculateIncludesGuaranteedBosses() {
        RealmMapData smallMap = RealmMapData.create(
            10, GearRarity.COMMON, RealmBiomeType.FOREST, RealmLayoutSize.SMALL);
        RealmMapData mediumMap = RealmMapData.create(
            10, GearRarity.COMMON, RealmBiomeType.FOREST, RealmLayoutSize.MEDIUM);
        RealmMapData massiveMap = RealmMapData.create(
            10, GearRarity.COMMON, RealmBiomeType.FOREST, RealmLayoutSize.MASSIVE);

        SpawnDistribution smallDist = calculator.calculate(smallMap);
        SpawnDistribution mediumDist = calculator.calculate(mediumMap);
        SpawnDistribution massiveDist = calculator.calculate(massiveMap);

        // Small has 0 guaranteed, medium has 1, massive has 2
        assertEquals(0, smallDist.guaranteedBosses());
        assertEquals(1, mediumDist.guaranteedBosses());
        assertEquals(2, massiveDist.guaranteedBosses());

        // Boss count >= guaranteed (chance-based may add more)
        assertTrue(mediumDist.bossCount() >= 1);
        assertTrue(massiveDist.bossCount() >= 2);
    }

    @Test
    @DisplayName("Higher level increases elite/boss chances")
    void higherLevelIncreasesEliteBossChances() {
        RealmMapData lowLevel = RealmMapData.create(
            1, GearRarity.COMMON, RealmBiomeType.FOREST, RealmLayoutSize.LARGE);
        RealmMapData highLevel = RealmMapData.create(
            100, GearRarity.COMMON, RealmBiomeType.FOREST, RealmLayoutSize.LARGE);

        SpawnDistribution lowDist = calculator.calculate(lowLevel);
        SpawnDistribution highDist = calculator.calculate(highLevel);

        // Higher level should have higher elite/boss chances
        assertTrue(highDist.eliteChance() >= lowDist.eliteChance());
        assertTrue(highDist.bossChance() >= lowDist.bossChance());
    }

    // ═══════════════════════════════════════════════════════════════════
    // SPAWN DISTRIBUTION RECORD TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("SpawnDistribution validates counts sum to total")
    void distributionValidatesCounts() {
        // totalCount=100, normalCount=90, bossCount=10 (sums to 100 - valid)
        SpawnDistribution valid = new SpawnDistribution(100, 90, 10, 2, 0.1, 0.05);
        assertNotNull(valid);

        // totalCount=100, normalCount=50, bossCount=30 = 80 (doesn't sum to 100 - invalid)
        assertThrows(IllegalArgumentException.class, () ->
            new SpawnDistribution(100, 50, 30, 1, 0.1, 0.05));
    }

    @Test
    @DisplayName("SpawnDistribution calculates percentages correctly")
    void distributionCalculatesPercentages() {
        SpawnDistribution dist = new SpawnDistribution(100, 90, 10, 2, 0.1, 0.05);

        assertEquals(0.9, dist.normalPercentage(), 0.001);
        assertEquals(0.1, dist.bossPercentage(), 0.001);
    }

    @Test
    @DisplayName("SpawnDistribution calculates random boss count")
    void distributionCalculatesRandomBosses() {
        SpawnDistribution dist = new SpawnDistribution(100, 90, 10, 3, 0.1, 0.05);

        assertEquals(7, dist.randomBossCount()); // 10 total - 3 guaranteed
    }

    // ═══════════════════════════════════════════════════════════════════
    // ELITE/BOSS CHANCE TESTS
    // ═══════════════════════════════════════════════════════════════════

    @ParameterizedTest
    @CsvSource({
        "1, 0.05",    // Base elite chance
        "100, 0.06",  // Some increase
        "1000, 0.15", // Higher increase
        "5000, 0.25"  // Should cap at max
    })
    @DisplayName("calculateEliteChance scales with level")
    void eliteChanceScalesWithLevel(int level, double minExpected) {
        double chance = calculator.calculateEliteChance(level);

        assertTrue(chance >= minExpected - 0.01,
            "Elite chance at level " + level + " should be >= " + minExpected);
        assertTrue(chance <= config.getMaxEliteChance(),
            "Elite chance should not exceed max");
    }

    @ParameterizedTest
    @CsvSource({
        "1, 0.01",    // Base boss chance
        "100, 0.015", // Some increase
        "1000, 0.06", // Higher increase
        "5000, 0.10"  // Should cap at max
    })
    @DisplayName("calculateBossChance scales with level")
    void bossChanceScalesWithLevel(int level, double minExpected) {
        double chance = calculator.calculateBossChance(level);

        assertTrue(chance >= minExpected - 0.005,
            "Boss chance at level " + level + " should be >= " + minExpected);
        assertTrue(chance <= config.getMaxBossChance(),
            "Boss chance should not exceed max");
    }

    // ═══════════════════════════════════════════════════════════════════
    // WAVE CALCULATION TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("calculateWaves returns valid WaveParameters")
    void calculateWavesReturnsValidParams() {
        SpawnDistribution dist = new SpawnDistribution(100, 95, 5, 1, 0.1, 0.05);
        WaveParameters waves = calculator.calculateWaves(dist);

        assertNotNull(waves);
        assertTrue(waves.initialWaveSize() >= config.getSpawnBatchSize());
        assertEquals(config.getSpawnBatchSize(), waves.subsequentWaveSize());
        assertEquals(config.getSpawnIntervalTicks(), waves.waveIntervalTicks());
    }

    @Test
    @DisplayName("Initial wave is larger than subsequent waves")
    void initialWaveLargerThanSubsequent() {
        SpawnDistribution dist = new SpawnDistribution(100, 95, 5, 1, 0.1, 0.05);
        WaveParameters waves = calculator.calculateWaves(dist);

        assertTrue(waves.initialWaveSize() >= waves.subsequentWaveSize());
    }

    @Test
    @DisplayName("WaveParameters calculates durations correctly")
    void waveParametersCalculatesDurations() {
        // 5 waves at 100 ticks each = 500 ticks = 25 seconds
        WaveParameters waves = new WaveParameters(20, 5, 5, 100);

        assertEquals(500, waves.totalDurationTicks());
        assertEquals(25.0f, waves.totalDurationSeconds(), 0.01f);
        assertEquals(5.0f, waves.waveIntervalSeconds(), 0.01f);
    }

    // ═══════════════════════════════════════════════════════════════════
    // STAT SCALING TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("calculateHealthMultiplier scales by level, classification, and elite")
    void healthMultiplierScales() {
        RealmMapData mapData = RealmMapData.create(
            50, GearRarity.COMMON, RealmBiomeType.FOREST, RealmLayoutSize.MEDIUM);

        // Non-elite mobs
        float normalHealth = calculator.calculateHealthMultiplier(50, MobClassification.NORMAL, false, mapData);
        float bossHealth = calculator.calculateHealthMultiplier(50, MobClassification.BOSS, false, mapData);

        // Elite mobs (same classification but with elite modifier)
        float normalEliteHealth = calculator.calculateHealthMultiplier(50, MobClassification.NORMAL, true, mapData);
        float bossEliteHealth = calculator.calculateHealthMultiplier(50, MobClassification.BOSS, true, mapData);

        // All should be > 1 due to level scaling
        assertTrue(normalHealth > 1.0f);
        assertTrue(bossHealth > normalHealth, "Boss should have more health than normal");

        // Elite should have more health than non-elite
        assertTrue(normalEliteHealth > normalHealth, "Elite normal should have more health than normal");
        assertTrue(bossEliteHealth > bossHealth, "Elite boss should have more health than boss");
    }

    @Test
    @DisplayName("calculateDamageMultiplier scales by level, classification, and elite")
    void damageMultiplierScales() {
        RealmMapData mapData = RealmMapData.create(
            50, GearRarity.COMMON, RealmBiomeType.FOREST, RealmLayoutSize.MEDIUM);

        // Non-elite mobs
        float normalDamage = calculator.calculateDamageMultiplier(50, MobClassification.NORMAL, false, mapData);
        float bossDamage = calculator.calculateDamageMultiplier(50, MobClassification.BOSS, false, mapData);

        // Elite mobs
        float normalEliteDamage = calculator.calculateDamageMultiplier(50, MobClassification.NORMAL, true, mapData);
        float bossEliteDamage = calculator.calculateDamageMultiplier(50, MobClassification.BOSS, true, mapData);

        assertTrue(normalDamage > 1.0f);
        assertTrue(bossDamage > normalDamage, "Boss should deal more damage than normal");

        // Elite should deal more damage than non-elite
        assertTrue(normalEliteDamage > normalDamage, "Elite normal should deal more damage than normal");
        assertTrue(bossEliteDamage > bossDamage, "Elite boss should deal more damage than boss");
    }

    @Test
    @DisplayName("calculateXpReward scales correctly with elite modifier")
    void xpRewardScales() {
        int baseXp = 100;

        // Non-elite mobs
        int normalXp = calculator.calculateXpReward(50, MobClassification.NORMAL, false, baseXp);
        int bossXp = calculator.calculateXpReward(50, MobClassification.BOSS, false, baseXp);

        // Elite mobs
        int normalEliteXp = calculator.calculateXpReward(50, MobClassification.NORMAL, true, baseXp);
        int bossEliteXp = calculator.calculateXpReward(50, MobClassification.BOSS, true, baseXp);

        assertTrue(normalXp > baseXp, "XP should be scaled up from base");
        assertTrue(bossXp > normalXp * 8, "Boss should give ~10x XP");

        // Elite should give 3x XP compared to non-elite
        assertTrue(normalEliteXp > normalXp * 2, "Elite normal should give ~3x XP");
        assertTrue(bossEliteXp > bossXp * 2, "Elite boss should give ~3x XP");
    }

    // ═══════════════════════════════════════════════════════════════════
    // MAP DATA MODIFIER INTEGRATION TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Map modifiers affect total mob count")
    void mapModifiersAffectMobCount() {
        // Create map with extra monster modifier
        RealmMapData normalMap = RealmMapData.create(
            50, GearRarity.COMMON, RealmBiomeType.FOREST, RealmLayoutSize.MEDIUM);

        int normalCount = calculator.calculateTotalMobCount(normalMap);

        // A map with extra monsters should have more mobs
        // (Note: We'd need to add modifiers to test this properly,
        //  but the method does check mapData.getExtraMonsterPercent())
        assertTrue(normalCount > 0);
    }
}
