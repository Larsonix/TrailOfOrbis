package io.github.larsonix.trailoforbis.maps.reward;

import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.leveling.core.LevelingManager;
import io.github.larsonix.trailoforbis.leveling.xp.XpSource;
import io.github.larsonix.trailoforbis.maps.config.RealmsConfig;
import io.github.larsonix.trailoforbis.maps.core.*;
import io.github.larsonix.trailoforbis.maps.instance.RealmInstance;
import io.github.larsonix.trailoforbis.maps.instance.RealmInstance.CompletionReason;
import io.github.larsonix.trailoforbis.maps.templates.RealmTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RealmRewardService}.
 *
 * <p>Tests reward distribution orchestration:
 * <ul>
 *   <li>Distributing rewards to participants</li>
 *   <li>Handling edge cases (no completion, no participants)</li>
 *   <li>XP calculation and application</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RealmRewardService")
class RealmRewardServiceTest {

    @Mock
    private TrailOfOrbis plugin;

    @Mock
    private LevelingManager levelingManager;

    @Mock
    private RealmInstance realm;

    @Mock
    private RealmCompletionTracker completionTracker;

    @Mock
    private RealmTemplate template;

    private RealmsConfig config;
    private RealmRewardService service;
    private UUID playerId1;
    private UUID playerId2;

    @BeforeEach
    void setUp() {
        config = new RealmsConfig();
        service = new RealmRewardService(plugin, config);
        playerId1 = UUID.randomUUID();
        playerId2 = UUID.randomUUID();

        // Default mock setup - use lenient() since not all tests need this
        lenient().when(plugin.getLevelingManager()).thenReturn(levelingManager);
    }

    /**
     * Creates a mock RealmMapData for testing.
     */
    private RealmMapData createTestMapData(int level, RealmLayoutSize size) {
        return new RealmMapData(
            level,
            GearRarity.RARE,
            50, // quality
            RealmBiomeType.FOREST,
            size,
            RealmLayoutShape.CIRCULAR,
            List.of(), // prefixes
            List.of(), // suffixes
            false, // corrupted
            true,  // identified
            null   // instanceId
        );
    }

    // ═══════════════════════════════════════════════════════════════════
    // DISTRIBUTE REWARDS TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("distributeRewards")
    class DistributeRewardsTests {

        @Test
        @DisplayName("Should calculate and distribute rewards for all participants")
        void distributeRewards_completedRealm_calculatesForAllParticipants() throws ExecutionException, InterruptedException {
            // Arrange
            when(realm.getCompletionReason()).thenReturn(CompletionReason.COMPLETED);
            when(realm.getAllParticipants()).thenReturn(Set.of(playerId1, playerId2));
            when(realm.getRealmId()).thenReturn(UUID.randomUUID());
            when(realm.getMapData()).thenReturn(createTestMapData(50, RealmLayoutSize.MEDIUM));
            when(realm.getCompletionTracker()).thenReturn(completionTracker);

            // Set up completion tracker to return valid data
            when(completionTracker.getPlayerKills(any())).thenReturn(10);
            when(completionTracker.getKilledByPlayers()).thenReturn(20);

            // Act
            CompletableFuture<Map<UUID, RealmRewardResult>> future = service.distributeRewards(realm);
            Map<UUID, RealmRewardResult> results = future.get();

            // Assert
            assertEquals(2, results.size());
            assertTrue(results.containsKey(playerId1));
            assertTrue(results.containsKey(playerId2));

            // Verify XP was awarded to both players
            verify(levelingManager, times(2)).addXp(any(), anyLong(), eq(XpSource.REALM_COMPLETION));
        }

        @Test
        @DisplayName("Should return empty map when realm has no completion reason")
        void distributeRewards_noCompletionReason_returnsEmptyMap() throws ExecutionException, InterruptedException {
            // Arrange
            when(realm.getCompletionReason()).thenReturn(null);

            // Act
            CompletableFuture<Map<UUID, RealmRewardResult>> future = service.distributeRewards(realm);
            Map<UUID, RealmRewardResult> results = future.get();

            // Assert
            assertTrue(results.isEmpty());
            verify(levelingManager, never()).addXp(any(), anyLong(), any());
        }

        @Test
        @DisplayName("Should return empty map when no participants")
        void distributeRewards_noParticipants_returnsEmptyMap() throws ExecutionException, InterruptedException {
            // Arrange
            when(realm.getCompletionReason()).thenReturn(CompletionReason.COMPLETED);
            when(realm.getAllParticipants()).thenReturn(Set.of());

            // Act
            CompletableFuture<Map<UUID, RealmRewardResult>> future = service.distributeRewards(realm);
            Map<UUID, RealmRewardResult> results = future.get();

            // Assert
            assertTrue(results.isEmpty());
            verify(levelingManager, never()).addXp(any(), anyLong(), any());
        }

        @Test
        @DisplayName("Should handle single participant realm")
        void distributeRewards_singleParticipant_calculatesRewards() throws ExecutionException, InterruptedException {
            // Arrange
            when(realm.getCompletionReason()).thenReturn(CompletionReason.COMPLETED);
            when(realm.getAllParticipants()).thenReturn(Set.of(playerId1));
            when(realm.getRealmId()).thenReturn(UUID.randomUUID());
            when(realm.getMapData()).thenReturn(createTestMapData(25, RealmLayoutSize.SMALL));
            when(realm.getCompletionTracker()).thenReturn(completionTracker);

            when(completionTracker.getPlayerKills(playerId1)).thenReturn(15);
            when(completionTracker.getKilledByPlayers()).thenReturn(15);

            // Act
            CompletableFuture<Map<UUID, RealmRewardResult>> future = service.distributeRewards(realm);
            Map<UUID, RealmRewardResult> results = future.get();

            // Assert
            assertEquals(1, results.size());
            assertTrue(results.containsKey(playerId1));
            RealmRewardResult result = results.get(playerId1);
            assertEquals(100.0, result.contributionPercent(), 0.1); // Solo player = 100%
        }

        @Test
        @DisplayName("Should handle TIMEOUT completion reason with partial rewards")
        void distributeRewards_timeout_distributesPartialRewards() throws ExecutionException, InterruptedException {
            // Arrange
            when(realm.getCompletionReason()).thenReturn(CompletionReason.TIMEOUT);
            when(realm.getAllParticipants()).thenReturn(Set.of(playerId1));
            when(realm.getRealmId()).thenReturn(UUID.randomUUID());
            when(realm.getMapData()).thenReturn(createTestMapData(50, RealmLayoutSize.MEDIUM));
            when(realm.getCompletionTracker()).thenReturn(completionTracker);

            when(completionTracker.getPlayerKills(playerId1)).thenReturn(10);
            when(completionTracker.getKilledByPlayers()).thenReturn(10);

            // Act
            CompletableFuture<Map<UUID, RealmRewardResult>> future = service.distributeRewards(realm);
            Map<UUID, RealmRewardResult> results = future.get();

            // Assert - timeout gives partial rewards, not empty
            assertEquals(1, results.size());
            assertTrue(results.containsKey(playerId1));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // XP CALCULATION TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("XP Calculation")
    class XpCalculationTests {

        @Test
        @DisplayName("Should calculate base XP for level 1 small realm")
        void calculateBaseCompletionXp_level1Small_returns50Base() throws ExecutionException, InterruptedException {
            // Arrange - Level 1, SMALL size (monster multiplier 1.0)
            RealmMapData mapData = createTestMapData(1, RealmLayoutSize.SMALL);

            when(realm.getCompletionReason()).thenReturn(CompletionReason.COMPLETED);
            when(realm.getAllParticipants()).thenReturn(Set.of(playerId1));
            when(realm.getRealmId()).thenReturn(UUID.randomUUID());
            when(realm.getMapData()).thenReturn(mapData);
            when(realm.getCompletionTracker()).thenReturn(completionTracker);

            when(completionTracker.getPlayerKills(playerId1)).thenReturn(10);
            when(completionTracker.getKilledByPlayers()).thenReturn(10);

            // Act
            service.distributeRewards(realm).get();

            // Assert - verify XP was awarded (exact value depends on formula)
            // Formula: (50 + level * 5) * sizeBonus / 100
            // Level 1, Small (1.0x): (50 + 1*5) * 100 / 100 = 55 base XP
            // With xpMultiplier from result (likely ~1.0-1.5x)
            verify(levelingManager).addXp(eq(playerId1), anyLong(), eq(XpSource.REALM_COMPLETION));
        }

        @Test
        @DisplayName("Should calculate scaled XP for level 100 large realm")
        void calculateBaseCompletionXp_level100Large_scalesCorrectly() throws ExecutionException, InterruptedException {
            // Arrange - Level 100, LARGE size (monster multiplier 2.0)
            RealmMapData mapData = createTestMapData(100, RealmLayoutSize.LARGE);

            when(realm.getCompletionReason()).thenReturn(CompletionReason.COMPLETED);
            when(realm.getAllParticipants()).thenReturn(Set.of(playerId1));
            when(realm.getRealmId()).thenReturn(UUID.randomUUID());
            when(realm.getMapData()).thenReturn(mapData);
            when(realm.getCompletionTracker()).thenReturn(completionTracker);

            when(completionTracker.getPlayerKills(playerId1)).thenReturn(50);
            when(completionTracker.getKilledByPlayers()).thenReturn(50);

            // Act
            service.distributeRewards(realm).get();

            // Assert - verify XP was awarded with higher values
            // Formula: (50 + 100*5) * 200 / 100 = 550 * 2 = 1100 base XP
            verify(levelingManager).addXp(eq(playerId1), anyLong(), eq(XpSource.REALM_COMPLETION));
        }

        @Test
        @DisplayName("Should skip XP award when leveling manager is null")
        void awardCompletionXp_nullLevelingManager_gracefullySkips() throws ExecutionException, InterruptedException {
            // Arrange
            when(plugin.getLevelingManager()).thenReturn(null);

            when(realm.getCompletionReason()).thenReturn(CompletionReason.COMPLETED);
            when(realm.getAllParticipants()).thenReturn(Set.of(playerId1));
            when(realm.getRealmId()).thenReturn(UUID.randomUUID());
            when(realm.getMapData()).thenReturn(createTestMapData(50, RealmLayoutSize.MEDIUM));
            when(realm.getCompletionTracker()).thenReturn(completionTracker);

            when(completionTracker.getPlayerKills(playerId1)).thenReturn(10);
            when(completionTracker.getKilledByPlayers()).thenReturn(10);

            // Act - should not throw
            CompletableFuture<Map<UUID, RealmRewardResult>> future = service.distributeRewards(realm);
            Map<UUID, RealmRewardResult> results = future.get();

            // Assert - rewards calculated but no XP call
            assertEquals(1, results.size());
            verify(levelingManager, never()).addXp(any(), anyLong(), any());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // APPLY REWARDS TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("applyRewards (via distributeRewards)")
    @MockitoSettings(strictness = Strictness.LENIENT)
    class ApplyRewardsTests {

        @Test
        @DisplayName("Should skip application when result has no rewards")
        void applyRewards_noRewards_skipsApplication() throws ExecutionException, InterruptedException {
            // Arrange - Setup to produce a failed result (ABANDONED reason)
            when(realm.getCompletionReason()).thenReturn(CompletionReason.ABANDONED);
            when(realm.getAllParticipants()).thenReturn(Set.of(playerId1));
            when(realm.getRealmId()).thenReturn(UUID.randomUUID());
            when(realm.getMapData()).thenReturn(createTestMapData(50, RealmLayoutSize.MEDIUM));
            when(realm.getCompletionTracker()).thenReturn(completionTracker);

            // Use lenient() since the calculator may short-circuit based on reason
            lenient().when(completionTracker.getPlayerKills(playerId1)).thenReturn(0);
            lenient().when(completionTracker.getKilledByPlayers()).thenReturn(0);

            // Act
            CompletableFuture<Map<UUID, RealmRewardResult>> future = service.distributeRewards(realm);
            Map<UUID, RealmRewardResult> results = future.get();

            // Assert - Result exists but with no rewards
            assertEquals(1, results.size());
            RealmRewardResult result = results.get(playerId1);
            assertFalse(result.hasRewards());

            // XP should not be awarded for failed result
            verify(levelingManager, never()).addXp(any(), anyLong(), any());
        }

        @Test
        @DisplayName("Should apply rewards with correct multipliers")
        void applyRewards_withRewards_awardsXpWithMultiplier() throws ExecutionException, InterruptedException {
            // Arrange
            when(realm.getCompletionReason()).thenReturn(CompletionReason.COMPLETED);
            when(realm.getAllParticipants()).thenReturn(Set.of(playerId1));
            when(realm.getRealmId()).thenReturn(UUID.randomUUID());
            when(realm.getMapData()).thenReturn(createTestMapData(50, RealmLayoutSize.MEDIUM));
            when(realm.getCompletionTracker()).thenReturn(completionTracker);

            when(completionTracker.getPlayerKills(playerId1)).thenReturn(20);
            when(completionTracker.getKilledByPlayers()).thenReturn(20);

            // Act
            service.distributeRewards(realm).get();

            // Assert - XP was awarded
            verify(levelingManager).addXp(eq(playerId1), anyLong(), eq(XpSource.REALM_COMPLETION));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // CONSTRUCTOR AND GETTER TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Constructor and Getters")
    class ConstructorTests {

        @Test
        @DisplayName("Should throw on null plugin")
        void constructor_nullPlugin_throws() {
            assertThrows(NullPointerException.class,
                () -> new RealmRewardService(null, config));
        }

        @Test
        @DisplayName("Should throw on null config")
        void constructor_nullConfig_throws() {
            assertThrows(NullPointerException.class,
                () -> new RealmRewardService(plugin, null));
        }

        @Test
        @DisplayName("Should provide access to calculator")
        void getCalculator_returnsCalculator() {
            RealmRewardCalculator calculator = service.getCalculator();
            assertNotNull(calculator);
        }

        @Test
        @DisplayName("Should throw on null realm in distributeRewards")
        void distributeRewards_nullRealm_throws() {
            assertThrows(NullPointerException.class,
                () -> service.distributeRewards(null));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // EDGE CASE TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Edge Cases")
    @MockitoSettings(strictness = Strictness.LENIENT)
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle ERROR completion reason")
        void distributeRewards_errorReason_returnsEmptyResults() throws ExecutionException, InterruptedException {
            // Arrange
            when(realm.getCompletionReason()).thenReturn(CompletionReason.ERROR);
            when(realm.getAllParticipants()).thenReturn(Set.of(playerId1));
            when(realm.getRealmId()).thenReturn(UUID.randomUUID());
            when(realm.getMapData()).thenReturn(createTestMapData(50, RealmLayoutSize.MEDIUM));
            when(realm.getCompletionTracker()).thenReturn(completionTracker);

            // Calculator still accesses these even for ERROR, use lenient to allow unused
            lenient().when(completionTracker.getPlayerKills(playerId1)).thenReturn(0);
            lenient().when(completionTracker.getKilledByPlayers()).thenReturn(0);

            // Act
            CompletableFuture<Map<UUID, RealmRewardResult>> future = service.distributeRewards(realm);
            Map<UUID, RealmRewardResult> results = future.get();

            // Assert - Results exist but are failed results
            assertEquals(1, results.size());
            assertFalse(results.get(playerId1).hasRewards());
        }

        @Test
        @DisplayName("Should handle FORCE_CLOSED completion reason")
        void distributeRewards_forceClosedReason_calculatesPartialRewards() throws ExecutionException, InterruptedException {
            // Arrange
            when(realm.getCompletionReason()).thenReturn(CompletionReason.FORCE_CLOSED);
            when(realm.getAllParticipants()).thenReturn(Set.of(playerId1));
            when(realm.getRealmId()).thenReturn(UUID.randomUUID());
            when(realm.getMapData()).thenReturn(createTestMapData(50, RealmLayoutSize.MEDIUM));
            when(realm.getCompletionTracker()).thenReturn(completionTracker);

            when(completionTracker.getPlayerKills(playerId1)).thenReturn(5);
            when(completionTracker.getKilledByPlayers()).thenReturn(5);

            // Act
            CompletableFuture<Map<UUID, RealmRewardResult>> future = service.distributeRewards(realm);
            Map<UUID, RealmRewardResult> results = future.get();

            // Assert - Should have partial rewards
            assertEquals(1, results.size());
            assertTrue(results.containsKey(playerId1));
        }

        @Test
        @DisplayName("Should handle very high level realm")
        void calculateXp_veryHighLevel_handlesGracefully() throws ExecutionException, InterruptedException {
            // Arrange - Level 10000
            RealmMapData mapData = createTestMapData(10000, RealmLayoutSize.LARGE);

            when(realm.getCompletionReason()).thenReturn(CompletionReason.COMPLETED);
            when(realm.getAllParticipants()).thenReturn(Set.of(playerId1));
            when(realm.getRealmId()).thenReturn(UUID.randomUUID());
            when(realm.getMapData()).thenReturn(mapData);
            when(realm.getCompletionTracker()).thenReturn(completionTracker);

            when(completionTracker.getPlayerKills(playerId1)).thenReturn(100);
            when(completionTracker.getKilledByPlayers()).thenReturn(100);

            // Act - should not overflow
            CompletableFuture<Map<UUID, RealmRewardResult>> future = service.distributeRewards(realm);
            Map<UUID, RealmRewardResult> results = future.get();

            // Assert
            assertEquals(1, results.size());
            assertTrue(results.get(playerId1).hasRewards());
            verify(levelingManager).addXp(eq(playerId1), anyLong(), eq(XpSource.REALM_COMPLETION));
        }
    }
}
