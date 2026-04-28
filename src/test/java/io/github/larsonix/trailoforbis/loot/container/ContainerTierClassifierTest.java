package io.github.larsonix.trailoforbis.loot.container;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ContainerTierClassifier} — pattern matching, caching,
 * priority ordering, and config-driven tier properties.
 */
class ContainerTierClassifierTest {

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Builds a TierConfig with the given patterns and default values.
     */
    private static ContainerLootConfig.TierConfig tierConfig(List<String> patterns) {
        var tc = new ContainerLootConfig.TierConfig();
        tc.setPatterns(patterns);
        return tc;
    }

    /**
     * Builds a TierConfig with patterns and custom values.
     */
    private static ContainerLootConfig.TierConfig tierConfig(
            List<String> patterns, double lootMultiplier, double rarityBonus,
            int minGear, int maxGear, boolean guaranteedRare,
            double mapChanceMult, double stoneChanceMult) {
        var tc = new ContainerLootConfig.TierConfig();
        tc.setPatterns(patterns);
        tc.setLoot_multiplier(lootMultiplier);
        tc.setRarity_bonus(rarityBonus);
        tc.setMin_gear_drops(minGear);
        tc.setMax_gear_drops(maxGear);
        tc.setGuaranteed_rare_or_better(guaranteedRare);
        tc.setMap_chance_multiplier(mapChanceMult);
        tc.setStone_chance_multiplier(stoneChanceMult);
        return tc;
    }

    /**
     * Creates a config with standard tier patterns for most tests.
     */
    private static ContainerLootConfig standardConfig() {
        var config = new ContainerLootConfig();
        Map<String, ContainerLootConfig.TierConfig> tiers = new LinkedHashMap<>();

        tiers.put("basic", tierConfig(List.of("Chest", "Barrel", "Crate")));
        tiers.put("dungeon", tierConfig(List.of("Dungeon_*", "*_Treasure", "*Hidden*")));
        tiers.put("boss", tierConfig(List.of("Boss_*", "*_BossReward", "Raid_Chest")));
        tiers.put("special", tierConfig(List.of("Quest_*", "Artifact_*")));

        config.setContainer_tiers(tiers);
        return config;
    }

    // =========================================================================
    // Exact Match
    // =========================================================================

    @Nested
    @DisplayName("Exact block ID matching")
    class ExactMatch {

        private ContainerTierClassifier classifier;

        @BeforeEach
        void setUp() {
            classifier = new ContainerTierClassifier(standardConfig());
        }

        @Test
        @DisplayName("Exact match returns correct tier")
        void exactMatchBasic() {
            assertEquals(ContainerTier.BASIC, classifier.classify("Chest"));
            assertEquals(ContainerTier.BASIC, classifier.classify("Barrel"));
            assertEquals(ContainerTier.BASIC, classifier.classify("Crate"));
        }

        @Test
        @DisplayName("Exact match for boss tier")
        void exactMatchBoss() {
            assertEquals(ContainerTier.BOSS, classifier.classify("Raid_Chest"));
        }
    }

    // =========================================================================
    // Wildcard Pattern Matching
    // =========================================================================

    @Nested
    @DisplayName("Wildcard pattern matching")
    class WildcardMatch {

        private ContainerTierClassifier classifier;

        @BeforeEach
        void setUp() {
            classifier = new ContainerTierClassifier(standardConfig());
        }

        @Test
        @DisplayName("Trailing wildcard: Dungeon_* matches Dungeon_Chest")
        void trailingWildcard() {
            assertEquals(ContainerTier.DUNGEON, classifier.classify("Dungeon_Chest"));
            assertEquals(ContainerTier.DUNGEON, classifier.classify("Dungeon_Barrel"));
            assertEquals(ContainerTier.DUNGEON, classifier.classify("Dungeon_X"));
        }

        @Test
        @DisplayName("Leading wildcard: *_Treasure matches Cave_Treasure")
        void leadingWildcard() {
            assertEquals(ContainerTier.DUNGEON, classifier.classify("Cave_Treasure"));
            assertEquals(ContainerTier.DUNGEON, classifier.classify("Ancient_Treasure"));
        }

        @Test
        @DisplayName("Middle wildcard: *Hidden* matches DeepHiddenVault")
        void middleWildcard() {
            assertEquals(ContainerTier.DUNGEON, classifier.classify("DeepHiddenVault"));
            assertEquals(ContainerTier.DUNGEON, classifier.classify("HiddenCache"));
            assertEquals(ContainerTier.DUNGEON, classifier.classify("TheHidden"));
        }

        @Test
        @DisplayName("Boss leading wildcard: *_BossReward matches Fire_BossReward")
        void bossLeadingWildcard() {
            assertEquals(ContainerTier.BOSS, classifier.classify("Fire_BossReward"));
            assertEquals(ContainerTier.BOSS, classifier.classify("Ice_BossReward"));
        }

        @Test
        @DisplayName("Quest and Artifact match SPECIAL tier")
        void specialWildcard() {
            assertEquals(ContainerTier.SPECIAL, classifier.classify("Quest_Reward"));
            assertEquals(ContainerTier.SPECIAL, classifier.classify("Artifact_Container"));
        }
    }

    // =========================================================================
    // Case Insensitivity
    // =========================================================================

    @Nested
    @DisplayName("Case-insensitive matching")
    class CaseInsensitive {

        private ContainerTierClassifier classifier;

        @BeforeEach
        void setUp() {
            classifier = new ContainerTierClassifier(standardConfig());
        }

        @Test
        @DisplayName("Exact match is case-insensitive")
        void exactMatchCaseInsensitive() {
            assertEquals(ContainerTier.BASIC, classifier.classify("chest"));
            assertEquals(ContainerTier.BASIC, classifier.classify("CHEST"));
            assertEquals(ContainerTier.BASIC, classifier.classify("ChEsT"));
        }

        @Test
        @DisplayName("Wildcard match is case-insensitive")
        void wildcardCaseInsensitive() {
            assertEquals(ContainerTier.DUNGEON, classifier.classify("dungeon_chest"));
            assertEquals(ContainerTier.DUNGEON, classifier.classify("DUNGEON_CHEST"));
            assertEquals(ContainerTier.BOSS, classifier.classify("boss_dragon"));
            assertEquals(ContainerTier.BOSS, classifier.classify("BOSS_DRAGON"));
        }
    }

    // =========================================================================
    // Priority Ordering
    // =========================================================================

    @Nested
    @DisplayName("Priority ordering (BOSS > SPECIAL > DUNGEON > BASIC)")
    class PriorityOrdering {

        @Test
        @DisplayName("BOSS takes priority over DUNGEON when both match")
        void bossPriorityOverDungeon() {
            var config = new ContainerLootConfig();
            Map<String, ContainerLootConfig.TierConfig> tiers = new LinkedHashMap<>();

            // Both tiers match "Boss_Dungeon_Chest"
            tiers.put("dungeon", tierConfig(List.of("*Dungeon*")));
            tiers.put("boss", tierConfig(List.of("Boss_*")));

            config.setContainer_tiers(tiers);
            var classifier = new ContainerTierClassifier(config);

            assertEquals(ContainerTier.BOSS, classifier.classify("Boss_Dungeon_Chest"));
        }

        @Test
        @DisplayName("SPECIAL takes priority over DUNGEON when both match")
        void specialPriorityOverDungeon() {
            var config = new ContainerLootConfig();
            Map<String, ContainerLootConfig.TierConfig> tiers = new LinkedHashMap<>();

            tiers.put("dungeon", tierConfig(List.of("*Chest*")));
            tiers.put("special", tierConfig(List.of("Quest_*")));

            config.setContainer_tiers(tiers);
            var classifier = new ContainerTierClassifier(config);

            assertEquals(ContainerTier.SPECIAL, classifier.classify("Quest_Chest"));
        }

        @Test
        @DisplayName("BOSS takes priority over SPECIAL when both match")
        void bossPriorityOverSpecial() {
            var config = new ContainerLootConfig();
            Map<String, ContainerLootConfig.TierConfig> tiers = new LinkedHashMap<>();

            tiers.put("special", tierConfig(List.of("*Reward*")));
            tiers.put("boss", tierConfig(List.of("Boss_*")));

            config.setContainer_tiers(tiers);
            var classifier = new ContainerTierClassifier(config);

            assertEquals(ContainerTier.BOSS, classifier.classify("Boss_Reward"));
        }
    }

    // =========================================================================
    // Unknown / Edge Cases
    // =========================================================================

    @Nested
    @DisplayName("Unknown and edge cases")
    class UnknownAndEdgeCases {

        private ContainerTierClassifier classifier;

        @BeforeEach
        void setUp() {
            classifier = new ContainerTierClassifier(standardConfig());
        }

        @Test
        @DisplayName("Unknown block ID returns BASIC")
        void unknownBlockId() {
            assertEquals(ContainerTier.BASIC, classifier.classify("SomeRandomBlock"));
            assertEquals(ContainerTier.BASIC, classifier.classify("Furnace"));
        }

        @Test
        @DisplayName("Null block ID returns BASIC")
        void nullBlockId() {
            assertEquals(ContainerTier.BASIC, classifier.classify(null));
        }

        @Test
        @DisplayName("Empty block ID returns BASIC")
        void emptyBlockId() {
            assertEquals(ContainerTier.BASIC, classifier.classify(""));
        }

        @Test
        @DisplayName("Empty config results in BASIC for all")
        void emptyConfig() {
            var emptyClassifier = new ContainerTierClassifier(new ContainerLootConfig());
            assertEquals(ContainerTier.BASIC, emptyClassifier.classify("Dungeon_Chest"));
            assertEquals(ContainerTier.BASIC, emptyClassifier.classify("Boss_Reward"));
        }
    }

    // =========================================================================
    // Cache Behavior
    // =========================================================================

    @Nested
    @DisplayName("Classification cache")
    class CacheBehavior {

        @Test
        @DisplayName("Second call returns same result (cache hit)")
        void cacheReturnsSameResult() {
            var classifier = new ContainerTierClassifier(standardConfig());
            ContainerTier first = classifier.classify("Dungeon_Chest");
            ContainerTier second = classifier.classify("Dungeon_Chest");
            assertSame(first, second);
        }

        @Test
        @DisplayName("clearCache() does not change classification results")
        void clearCacheDoesNotChangeResults() {
            var classifier = new ContainerTierClassifier(standardConfig());
            ContainerTier before = classifier.classify("Boss_Dragon");
            classifier.clearCache();
            ContainerTier after = classifier.classify("Boss_Dragon");
            assertEquals(before, after);
        }
    }

    // =========================================================================
    // Tier Properties — Defaults (no config override)
    // =========================================================================

    @Nested
    @DisplayName("Tier properties — fallback to enum defaults")
    class TierPropertyDefaults {

        private ContainerTierClassifier classifier;

        @BeforeEach
        void setUp() {
            // Empty config: no tiers configured, so getTierConfig returns null
            classifier = new ContainerTierClassifier(new ContainerLootConfig());
        }

        @Test
        @DisplayName("getLootMultiplier falls back to enum default")
        void lootMultiplierDefault() {
            assertEquals(1.0, classifier.getLootMultiplier(ContainerTier.BASIC));
            assertEquals(1.5, classifier.getLootMultiplier(ContainerTier.DUNGEON));
            assertEquals(2.0, classifier.getLootMultiplier(ContainerTier.BOSS));
            assertEquals(1.75, classifier.getLootMultiplier(ContainerTier.SPECIAL));
        }

        @Test
        @DisplayName("getRarityBonus falls back to enum default")
        void rarityBonusDefault() {
            assertEquals(0.0, classifier.getRarityBonus(ContainerTier.BASIC));
            assertEquals(0.15, classifier.getRarityBonus(ContainerTier.DUNGEON));
            assertEquals(0.30, classifier.getRarityBonus(ContainerTier.BOSS));
            assertEquals(0.25, classifier.getRarityBonus(ContainerTier.SPECIAL));
        }

        @Test
        @DisplayName("getGearDropRange falls back to [0, 2]")
        void gearDropRangeDefault() {
            assertArrayEquals(new int[]{0, 2}, classifier.getGearDropRange(ContainerTier.BASIC));
            assertArrayEquals(new int[]{0, 2}, classifier.getGearDropRange(ContainerTier.BOSS));
        }

        @Test
        @DisplayName("isGuaranteedRareOrBetter defaults to false")
        void guaranteedRareDefault() {
            assertFalse(classifier.isGuaranteedRareOrBetter(ContainerTier.BASIC));
            assertFalse(classifier.isGuaranteedRareOrBetter(ContainerTier.BOSS));
        }

        @Test
        @DisplayName("getMapChanceMultiplier defaults to 1.0")
        void mapChanceMultiplierDefault() {
            assertEquals(1.0, classifier.getMapChanceMultiplier(ContainerTier.BASIC));
            assertEquals(1.0, classifier.getMapChanceMultiplier(ContainerTier.BOSS));
        }

        @Test
        @DisplayName("getStoneChanceMultiplier defaults to 1.0")
        void stoneChanceMultiplierDefault() {
            assertEquals(1.0, classifier.getStoneChanceMultiplier(ContainerTier.BASIC));
            assertEquals(1.0, classifier.getStoneChanceMultiplier(ContainerTier.BOSS));
        }
    }

    // =========================================================================
    // Tier Properties — Config Overrides
    // =========================================================================

    @Nested
    @DisplayName("Tier properties — config overrides")
    class TierPropertyOverrides {

        private ContainerTierClassifier classifier;

        @BeforeEach
        void setUp() {
            var config = new ContainerLootConfig();
            Map<String, ContainerLootConfig.TierConfig> tiers = new LinkedHashMap<>();

            tiers.put("basic", tierConfig(
                    List.of("Chest"), 1.2, 0.05, 0, 1, false, 0.8, 0.9));
            tiers.put("dungeon", tierConfig(
                    List.of("Dungeon_*"), 2.0, 0.20, 1, 3, false, 1.5, 1.3));
            tiers.put("boss", tierConfig(
                    List.of("Boss_*"), 3.0, 0.50, 2, 5, true, 2.0, 2.5));
            tiers.put("special", tierConfig(
                    List.of("Quest_*"), 1.8, 0.30, 1, 4, true, 1.2, 1.1));

            config.setContainer_tiers(tiers);
            classifier = new ContainerTierClassifier(config);
        }

        @Test
        @DisplayName("getLootMultiplier uses config value")
        void lootMultiplierOverride() {
            assertEquals(1.2, classifier.getLootMultiplier(ContainerTier.BASIC));
            assertEquals(2.0, classifier.getLootMultiplier(ContainerTier.DUNGEON));
            assertEquals(3.0, classifier.getLootMultiplier(ContainerTier.BOSS));
            assertEquals(1.8, classifier.getLootMultiplier(ContainerTier.SPECIAL));
        }

        @Test
        @DisplayName("getRarityBonus uses config value")
        void rarityBonusOverride() {
            assertEquals(0.05, classifier.getRarityBonus(ContainerTier.BASIC));
            assertEquals(0.20, classifier.getRarityBonus(ContainerTier.DUNGEON));
            assertEquals(0.50, classifier.getRarityBonus(ContainerTier.BOSS));
            assertEquals(0.30, classifier.getRarityBonus(ContainerTier.SPECIAL));
        }

        @Test
        @DisplayName("getGearDropRange uses config min/max")
        void gearDropRangeOverride() {
            assertArrayEquals(new int[]{0, 1}, classifier.getGearDropRange(ContainerTier.BASIC));
            assertArrayEquals(new int[]{1, 3}, classifier.getGearDropRange(ContainerTier.DUNGEON));
            assertArrayEquals(new int[]{2, 5}, classifier.getGearDropRange(ContainerTier.BOSS));
            assertArrayEquals(new int[]{1, 4}, classifier.getGearDropRange(ContainerTier.SPECIAL));
        }

        @Test
        @DisplayName("isGuaranteedRareOrBetter uses config value")
        void guaranteedRareOverride() {
            assertFalse(classifier.isGuaranteedRareOrBetter(ContainerTier.BASIC));
            assertFalse(classifier.isGuaranteedRareOrBetter(ContainerTier.DUNGEON));
            assertTrue(classifier.isGuaranteedRareOrBetter(ContainerTier.BOSS));
            assertTrue(classifier.isGuaranteedRareOrBetter(ContainerTier.SPECIAL));
        }

        @Test
        @DisplayName("getMapChanceMultiplier uses config value")
        void mapChanceOverride() {
            assertEquals(0.8, classifier.getMapChanceMultiplier(ContainerTier.BASIC));
            assertEquals(1.5, classifier.getMapChanceMultiplier(ContainerTier.DUNGEON));
            assertEquals(2.0, classifier.getMapChanceMultiplier(ContainerTier.BOSS));
            assertEquals(1.2, classifier.getMapChanceMultiplier(ContainerTier.SPECIAL));
        }

        @Test
        @DisplayName("getStoneChanceMultiplier uses config value")
        void stoneChanceOverride() {
            assertEquals(0.9, classifier.getStoneChanceMultiplier(ContainerTier.BASIC));
            assertEquals(1.3, classifier.getStoneChanceMultiplier(ContainerTier.DUNGEON));
            assertEquals(2.5, classifier.getStoneChanceMultiplier(ContainerTier.BOSS));
            assertEquals(1.1, classifier.getStoneChanceMultiplier(ContainerTier.SPECIAL));
        }
    }

    // =========================================================================
    // Reload
    // =========================================================================

    @Nested
    @DisplayName("Reload behavior")
    class ReloadBehavior {

        @Test
        @DisplayName("reload() re-reads patterns from config")
        void reloadRecompilesPatterns() {
            // Start with config that has a BOSS pattern
            var config = new ContainerLootConfig();
            Map<String, ContainerLootConfig.TierConfig> tiers = new LinkedHashMap<>();
            var bossTier = tierConfig(List.of("Boss_*"));
            tiers.put("boss", bossTier);
            config.setContainer_tiers(tiers);

            var classifier = new ContainerTierClassifier(config);
            assertEquals(ContainerTier.BOSS, classifier.classify("Boss_Dragon"));

            // Modify the tier config's patterns (simulate config reload)
            bossTier.setPatterns(List.of("Dragon_*"));

            // Before reload, cached result still returns BOSS
            assertEquals(ContainerTier.BOSS, classifier.classify("Boss_Dragon"));

            // After reload, new patterns take effect
            classifier.reload();
            assertEquals(ContainerTier.BASIC, classifier.classify("Boss_Dragon"));
            assertEquals(ContainerTier.BOSS, classifier.classify("Dragon_Fire"));
        }
    }
}
