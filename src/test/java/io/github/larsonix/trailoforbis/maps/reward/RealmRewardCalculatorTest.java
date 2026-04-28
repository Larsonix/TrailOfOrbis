package io.github.larsonix.trailoforbis.maps.reward;

import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.maps.config.RealmsConfig;
import io.github.larsonix.trailoforbis.maps.core.RealmBiomeType;
import io.github.larsonix.trailoforbis.maps.core.RealmCompletionTracker;
import io.github.larsonix.trailoforbis.maps.core.RealmLayoutShape;
import io.github.larsonix.trailoforbis.maps.core.RealmLayoutSize;
import io.github.larsonix.trailoforbis.maps.core.RealmMapData;
import io.github.larsonix.trailoforbis.maps.instance.RealmInstance;
import io.github.larsonix.trailoforbis.maps.instance.RealmInstance.CompletionReason;
import io.github.larsonix.trailoforbis.maps.modifiers.RealmModifier;
import io.github.larsonix.trailoforbis.maps.modifiers.RealmModifierType;
import io.github.larsonix.trailoforbis.maps.templates.RealmTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for RealmRewardCalculator - 15 test cases.
 *
 * <p>Covers XP/loot multiplier calculations, timeout penalties,
 * contribution scaling, and performance scoring.
 */
class RealmRewardCalculatorTest {

    private RealmsConfig config;
    private RealmRewardCalculator calculator;

    @BeforeEach
    void setUp() {
        config = new RealmsConfig();
        // Set predictable config values
        config.setBaseXpMultiplier(1.5);
        config.setBaseLootMultiplier(1.2);
        config.setDifficultyRewardScaling(0.1);

        calculator = new RealmRewardCalculator(config);
    }

    // =========================================================================
    // COMPLETION REASON TESTS
    // =========================================================================

    @Nested
    @DisplayName("Completion Reason Handling")
    class CompletionReasonTests {

        @Test
        @DisplayName("calculate returns full rewards for COMPLETED realm")
        void calculate_completedRealm_fullRewards() {
            UUID playerId = UUID.randomUUID();
            RealmInstance realm = createMockRealm(CompletionReason.COMPLETED, playerId, 10, 10);

            RealmRewardResult result = calculator.calculate(realm, playerId);

            assertTrue(result.hasRewards());
            assertTrue(result.xpMultiplier() > 0);
            assertTrue(result.itemQuantityMultiplier() > 0);
            assertTrue(result.itemRarityMultiplier() > 0);
        }

        @Test
        @DisplayName("calculate returns half rewards for TIMEOUT realm")
        void calculate_timeoutRealm_halfRewards() {
            UUID playerId = UUID.randomUUID();
            RealmInstance completedRealm = createMockRealm(CompletionReason.COMPLETED, playerId, 10, 10);
            RealmInstance timeoutRealm = createMockRealm(CompletionReason.TIMEOUT, playerId, 10, 10);

            RealmRewardResult completedResult = calculator.calculate(completedRealm, playerId);
            RealmRewardResult timeoutResult = calculator.calculate(timeoutRealm, playerId);

            // Timeout should be ~50% of completed rewards
            assertEquals(
                completedResult.xpMultiplier() * 0.5,
                timeoutResult.xpMultiplier(),
                0.001,
                "XP multiplier should be halved on timeout"
            );
            assertEquals(
                completedResult.itemQuantityMultiplier() * 0.5,
                timeoutResult.itemQuantityMultiplier(),
                0.001,
                "IIQ multiplier should be halved on timeout"
            );
        }

        @Test
        @DisplayName("calculate returns failed result for ABANDONED realm")
        void calculate_abandonedRealm_noRewards() {
            UUID playerId = UUID.randomUUID();
            RealmInstance realm = createMockRealm(CompletionReason.ABANDONED, playerId, 10, 10);

            RealmRewardResult result = calculator.calculate(realm, playerId);

            assertFalse(result.hasRewards());
            assertEquals(0.0, result.xpMultiplier());
            assertEquals(0.0, result.itemQuantityMultiplier());
            assertEquals(0.0, result.itemRarityMultiplier());
        }

        @Test
        @DisplayName("calculate returns failed result for ERROR reason")
        void calculate_errorRealm_noRewards() {
            UUID playerId = UUID.randomUUID();
            RealmInstance realm = createMockRealm(CompletionReason.ERROR, playerId, 10, 10);

            RealmRewardResult result = calculator.calculate(realm, playerId);

            assertFalse(result.hasRewards());
        }

        @Test
        @DisplayName("calculate returns failed result for null completion reason")
        void calculate_nullCompletionReason_noRewards() {
            UUID playerId = UUID.randomUUID();
            RealmInstance realm = createMockRealm(null, playerId, 10, 10);

            RealmRewardResult result = calculator.calculate(realm, playerId);

            assertFalse(result.hasRewards());
        }
    }

    // =========================================================================
    // CONTRIBUTION TESTS
    // =========================================================================

    @Nested
    @DisplayName("Contribution Scaling")
    class ContributionTests {

        @Test
        @DisplayName("calculate returns failed result for low contribution (AFK prevention)")
        void calculate_lowContribution_noRewards() {
            UUID playerId = UUID.randomUUID();
            // Player has 0 kills out of 100 total
            RealmInstance realm = createMockRealm(CompletionReason.COMPLETED, playerId, 0, 100);

            RealmRewardResult result = calculator.calculate(realm, playerId);

            assertFalse(result.hasRewards(), "Player with <1% contribution should get no rewards");
        }

        @Test
        @DisplayName("contribution scale is 1.0 for solo player")
        void calculateContributionScale_soloPlayer_fullScale() {
            UUID playerId = UUID.randomUUID();
            // Solo player with all kills
            RealmInstance realm = createMockRealmWithParticipants(
                CompletionReason.COMPLETED, playerId, 50, 50, Set.of(playerId));

            RealmRewardResult result = calculator.calculate(realm, playerId);

            assertTrue(result.hasRewards());
            assertEquals(100.0, result.contributionPercent(), 0.01);
        }

        @Test
        @DisplayName("contribution scale is fair distribution for multiplayer")
        void calculateContributionScale_multiPlayer_fairDistribution() {
            UUID player1 = UUID.randomUUID();
            UUID player2 = UUID.randomUUID();

            // Player 1 did 75% of kills in a 2-player realm
            RealmInstance realm = createMockRealmWithParticipants(
                CompletionReason.COMPLETED, player1, 75, 100, Set.of(player1, player2));

            RealmRewardResult result = calculator.calculate(realm, player1);

            assertTrue(result.hasRewards());
            // 75% contribution in 2-player = 1.5x expected, clamped to 1.5
            assertEquals(75.0, result.contributionPercent(), 0.01);
        }

        @Test
        @DisplayName("minimum contribution threshold is respected with enough total kills")
        void calculate_minimumContributionThreshold_respected() {
            UUID playerId = UUID.randomUUID();
            // Player has exactly 1 kill out of 100 (exactly 1% - at threshold)
            RealmInstance realm = createMockRealm(CompletionReason.COMPLETED, playerId, 1, 100);

            RealmRewardResult result = calculator.calculate(realm, playerId);

            assertTrue(result.hasRewards(), "Player with exactly 1% should get rewards");
        }

        @Test
        @DisplayName("low kill threshold only applies when total kills > 10")
        void calculate_lowTotalKills_stillGetRewards() {
            UUID playerId = UUID.randomUUID();
            // Player has 0 kills out of only 5 total - threshold doesn't apply
            RealmInstance realm = createMockRealm(CompletionReason.COMPLETED, playerId, 0, 5);

            RealmRewardResult result = calculator.calculate(realm, playerId);

            assertTrue(result.hasRewards(), "Low kill threshold shouldn't apply when total kills <= 10");
        }
    }

    // =========================================================================
    // MODIFIER BONUS TESTS
    // =========================================================================

    @Nested
    @DisplayName("Modifier Bonuses")
    class ModifierBonusTests {

        @Test
        @DisplayName("IIQ modifier increases item quantity multiplier")
        void calculate_withIIQModifier_increasedLoot() {
            UUID playerId = UUID.randomUUID();
            RealmInstance baseRealm = createMockRealm(CompletionReason.COMPLETED, playerId, 50, 50);
            RealmInstance iiqRealm = createMockRealmWithModifiers(
                CompletionReason.COMPLETED, playerId, 50, 50,
                List.of(RealmModifier.of(RealmModifierType.ITEM_QUANTITY, 50)));

            RealmRewardResult baseResult = calculator.calculate(baseRealm, playerId);
            RealmRewardResult iiqResult = calculator.calculate(iiqRealm, playerId);

            assertTrue(
                iiqResult.itemQuantityMultiplier() > baseResult.itemQuantityMultiplier(),
                "IIQ modifier should increase item quantity multiplier"
            );
        }

        @Test
        @DisplayName("Fortune's Compass bonus increases item quantity multiplier")
        void calculate_withFortunesCompassBonus_increasedLoot() {
            UUID playerId = UUID.randomUUID();
            RealmInstance baseRealm = createMockRealm(CompletionReason.COMPLETED, playerId, 50, 50);

            RealmInstance compassRealm = mock(RealmInstance.class);
            RealmCompletionTracker tracker = mock(RealmCompletionTracker.class);
            RealmMapData mapData = RealmMapData.builder()
                .level(50)
                .rarity(GearRarity.RARE)
                .biome(RealmBiomeType.FOREST)
                .size(RealmLayoutSize.MEDIUM)
                .fortunesCompassBonus(20)
                .build();

            when(compassRealm.getCompletionReason()).thenReturn(CompletionReason.COMPLETED);
            when(compassRealm.getCompletionTracker()).thenReturn(tracker);
            when(compassRealm.getMapData()).thenReturn(mapData);
            when(compassRealm.getAllParticipants()).thenReturn(Set.of(playerId));
            when(tracker.getPlayerKills(playerId)).thenReturn(50);
            when(tracker.getKilledByPlayers()).thenReturn(50);
            when(tracker.getPlayerDamage(playerId)).thenReturn(1000);

            RealmRewardResult baseResult = calculator.calculate(baseRealm, playerId);
            RealmRewardResult compassResult = calculator.calculate(compassRealm, playerId);

            assertTrue(
                compassResult.itemQuantityMultiplier() > baseResult.itemQuantityMultiplier(),
                "Fortune's Compass bonus should increase item quantity multiplier"
            );
        }

        @Test
        @DisplayName("XP modifier increases XP multiplier")
        void calculate_withXPModifier_increasedXP() {
            UUID playerId = UUID.randomUUID();
            RealmInstance baseRealm = createMockRealm(CompletionReason.COMPLETED, playerId, 50, 50);
            RealmInstance xpRealm = createMockRealmWithModifiers(
                CompletionReason.COMPLETED, playerId, 50, 50,
                List.of(RealmModifier.of(RealmModifierType.EXPERIENCE_BONUS, 50)));

            RealmRewardResult baseResult = calculator.calculate(baseRealm, playerId);
            RealmRewardResult xpResult = calculator.calculate(xpRealm, playerId);

            assertTrue(
                xpResult.xpMultiplier() > baseResult.xpMultiplier(),
                "XP modifier should increase XP multiplier"
            );
        }

        @Test
        @DisplayName("IIR modifier increases item rarity multiplier")
        void calculate_withIIRModifier_increasedRarity() {
            UUID playerId = UUID.randomUUID();
            RealmInstance baseRealm = createMockRealm(CompletionReason.COMPLETED, playerId, 50, 50);
            RealmInstance iirRealm = createMockRealmWithModifiers(
                CompletionReason.COMPLETED, playerId, 50, 50,
                List.of(RealmModifier.of(RealmModifierType.ITEM_RARITY, 50)));

            RealmRewardResult baseResult = calculator.calculate(baseRealm, playerId);
            RealmRewardResult iirResult = calculator.calculate(iirRealm, playerId);

            assertTrue(
                iirResult.itemRarityMultiplier() > baseResult.itemRarityMultiplier(),
                "IIR modifier should increase item rarity multiplier"
            );
        }

        @Test
        @DisplayName("difficulty scaling increases rewards")
        void calculate_difficultyScaling_higherRewards() {
            UUID playerId = UUID.randomUUID();

            // Create realms with different difficulty ratings
            RealmInstance lowDiffRealm = createMockRealmWithDifficulty(
                CompletionReason.COMPLETED, playerId, 50, 50, 0);
            RealmInstance highDiffRealm = createMockRealmWithDifficulty(
                CompletionReason.COMPLETED, playerId, 50, 50, 5);

            RealmRewardResult lowResult = calculator.calculate(lowDiffRealm, playerId);
            RealmRewardResult highResult = calculator.calculate(highDiffRealm, playerId);

            assertTrue(
                highResult.xpMultiplier() > lowResult.xpMultiplier(),
                "Higher difficulty should increase XP multiplier"
            );
        }
    }

    // =========================================================================
    // PERFORMANCE SCORE TESTS
    // =========================================================================

    @Nested
    @DisplayName("Performance Scoring")
    class PerformanceScoreTests {

        @Test
        @DisplayName("performance score is capped at 100")
        void calculatePerformanceScore_highKills_maxScore() {
            UUID playerId = UUID.randomUUID();
            RealmInstance realm = createMockRealmWithDamage(
                CompletionReason.COMPLETED, playerId, 100, 100, 50000);

            RealmRewardResult result = calculator.calculate(realm, playerId);

            assertTrue(result.performanceScore() <= 100, "Performance score should not exceed 100");
            assertTrue(result.performanceScore() > 0, "Performance score should be positive");
        }

        @Test
        @DisplayName("performance score includes participation bonus")
        void calculatePerformanceScore_noKills_hasParticipationBonus() {
            UUID playerId = UUID.randomUUID();
            // Player participated but got 0 kills in a realm with few total kills
            RealmInstance realm = createMockRealm(CompletionReason.COMPLETED, playerId, 0, 5);

            RealmRewardResult result = calculator.calculate(realm, playerId);

            assertTrue(result.performanceScore() > 0, "Performance score should include participation bonus");
        }
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private RealmInstance createMockRealm(
            CompletionReason reason,
            UUID playerId,
            int playerKills,
            int totalKills) {
        return createMockRealmWithParticipants(reason, playerId, playerKills, totalKills, Set.of(playerId));
    }

    private RealmInstance createMockRealmWithParticipants(
            CompletionReason reason,
            UUID playerId,
            int playerKills,
            int totalKills,
            Set<UUID> participants) {

        RealmInstance realm = mock(RealmInstance.class);
        RealmCompletionTracker tracker = mock(RealmCompletionTracker.class);
        RealmMapData mapData = RealmMapData.builder()
            .level(50)
            .rarity(GearRarity.RARE)
            .biome(RealmBiomeType.FOREST)
            .size(RealmLayoutSize.MEDIUM)
            .build();

        when(realm.getCompletionReason()).thenReturn(reason);
        when(realm.getCompletionTracker()).thenReturn(tracker);
        when(realm.getMapData()).thenReturn(mapData);
        when(realm.getAllParticipants()).thenReturn(participants);

        when(tracker.getPlayerKills(playerId)).thenReturn(playerKills);
        when(tracker.getKilledByPlayers()).thenReturn(totalKills);
        when(tracker.getPlayerDamage(playerId)).thenReturn(1000);

        return realm;
    }

    private RealmInstance createMockRealmWithModifiers(
            CompletionReason reason,
            UUID playerId,
            int playerKills,
            int totalKills,
            List<RealmModifier> modifiers) {

        RealmInstance realm = mock(RealmInstance.class);
        RealmCompletionTracker tracker = mock(RealmCompletionTracker.class);

        // Split modifiers into prefixes and suffixes
        RealmMapData.Builder builder = RealmMapData.builder()
            .level(50)
            .rarity(GearRarity.RARE)
            .biome(RealmBiomeType.FOREST)
            .size(RealmLayoutSize.MEDIUM);

        for (RealmModifier mod : modifiers) {
            builder.addModifier(mod);
        }
        RealmMapData mapData = builder.build();

        when(realm.getCompletionReason()).thenReturn(reason);
        when(realm.getCompletionTracker()).thenReturn(tracker);
        when(realm.getMapData()).thenReturn(mapData);
        when(realm.getAllParticipants()).thenReturn(Set.of(playerId));

        when(tracker.getPlayerKills(playerId)).thenReturn(playerKills);
        when(tracker.getKilledByPlayers()).thenReturn(totalKills);
        when(tracker.getPlayerDamage(playerId)).thenReturn(1000);

        return realm;
    }

    private RealmInstance createMockRealmWithDifficulty(
            CompletionReason reason,
            UUID playerId,
            int playerKills,
            int totalKills,
            int difficultyRating) {

        RealmInstance realm = mock(RealmInstance.class);
        RealmCompletionTracker tracker = mock(RealmCompletionTracker.class);
        RealmMapData mapData = mock(RealmMapData.class);

        when(realm.getCompletionReason()).thenReturn(reason);
        when(realm.getCompletionTracker()).thenReturn(tracker);
        when(realm.getMapData()).thenReturn(mapData);
        when(realm.getAllParticipants()).thenReturn(Set.of(playerId));

        when(mapData.modifiers()).thenReturn(List.of());
        when(mapData.getDifficultyRating()).thenReturn(difficultyRating);

        when(tracker.getPlayerKills(playerId)).thenReturn(playerKills);
        when(tracker.getKilledByPlayers()).thenReturn(totalKills);
        when(tracker.getPlayerDamage(playerId)).thenReturn(1000);

        return realm;
    }

    private RealmInstance createMockRealmWithDamage(
            CompletionReason reason,
            UUID playerId,
            int playerKills,
            int totalKills,
            int playerDamage) {

        RealmInstance realm = mock(RealmInstance.class);
        RealmCompletionTracker tracker = mock(RealmCompletionTracker.class);
        RealmMapData mapData = RealmMapData.builder()
            .level(50)
            .rarity(GearRarity.RARE)
            .biome(RealmBiomeType.FOREST)
            .size(RealmLayoutSize.MEDIUM)
            .build();

        when(realm.getCompletionReason()).thenReturn(reason);
        when(realm.getCompletionTracker()).thenReturn(tracker);
        when(realm.getMapData()).thenReturn(mapData);
        when(realm.getAllParticipants()).thenReturn(Set.of(playerId));

        when(tracker.getPlayerKills(playerId)).thenReturn(playerKills);
        when(tracker.getKilledByPlayers()).thenReturn(totalKills);
        when(tracker.getPlayerDamage(playerId)).thenReturn(playerDamage);

        return realm;
    }
}
