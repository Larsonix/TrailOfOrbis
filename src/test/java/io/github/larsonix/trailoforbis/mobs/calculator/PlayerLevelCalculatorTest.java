package io.github.larsonix.trailoforbis.mobs.calculator;

import com.hypixel.hytale.math.vector.Vector3d;
import io.github.larsonix.trailoforbis.mobs.MobScalingConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link PlayerLevelCalculator}.
 *
 * <p>Tests the effective mob level calculation based on nearby player levels,
 * group multipliers, and distance-based bonuses.
 *
 * <p>The formula is:
 * <ul>
 *   <li>Average level of nearby players</li>
 *   <li>× group multiplier (1.2^(groupedPlayers - 1))</li>
 *   <li>+ distance bonus (halved for balance)</li>
 * </ul>
 */
@DisplayName("PlayerLevelCalculator")
class PlayerLevelCalculatorTest {

    private MobScalingConfig config;
    private DistanceBonusCalculator distanceBonusCalculator;
    private PlayerLevelCalculator calculator;

    @BeforeEach
    void setUp() {
        config = new MobScalingConfig();
        // Enable player detection for tests (disabled by default in config)
        config.getPlayerDetection().setEnabled(true);
        distanceBonusCalculator = new DistanceBonusCalculator(config);
        // LevelingService is null - we use the overload with cached player data
        calculator = new PlayerLevelCalculator(null, config, distanceBonusCalculator);
    }

    /**
     * Helper to create PlayerInfo with position.
     */
    private PlayerLevelCalculator.PlayerInfo playerAt(double x, double y, double z, int level) {
        return new PlayerLevelCalculator.PlayerInfo(new Vector3d(x, y, z), level);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // No Players Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("No Players Nearby")
    class NoPlayersTests {

        @Test
        @DisplayName("Returns minimum level 1 at origin")
        void returnsMinLevel1_atOrigin() {
            List<PlayerLevelCalculator.PlayerInfo> noPlayers = List.of();
            Vector3d origin = new Vector3d(0, 64, 0);

            int level = calculator.calculateEffectiveLevel(noPlayers, origin);

            assertEquals(1, level);
        }

        @Test
        @DisplayName("Returns level based on distance when no players")
        void returnsDistanceBasedLevel_whenNoPlayers() {
            List<PlayerLevelCalculator.PlayerInfo> noPlayers = List.of();
            // At 1000 blocks from origin, distance bonus should apply
            // Distance level = 1 + (1000 - 200) / 75 = 11
            // Raw bonus = 11 - 1 = 10, halved = 5
            // Effective = 1 + 5 = 6
            Vector3d farPosition = new Vector3d(1000, 64, 0);

            int level = calculator.calculateEffectiveLevel(noPlayers, farPosition);

            assertEquals(6, level);
        }

        @Test
        @DisplayName("Always returns at least 1")
        void alwaysReturnsAtLeast1() {
            List<PlayerLevelCalculator.PlayerInfo> noPlayers = List.of();
            Vector3d safeZone = new Vector3d(25, 64, 0);

            int level = calculator.calculateEffectiveLevel(noPlayers, safeZone);

            assertTrue(level >= 1);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Single Player Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Single Player")
    class SinglePlayerTests {

        @Test
        @DisplayName("Returns player level at origin")
        void returnsPlayerLevel_atOrigin() {
            List<PlayerLevelCalculator.PlayerInfo> players = List.of(
                playerAt(0, 64, 0, 10)
            );
            Vector3d origin = new Vector3d(0, 64, 0);

            int level = calculator.calculateEffectiveLevel(players, origin);

            // Single player at origin, no group multiplier, no distance bonus
            assertEquals(10, level);
        }

        @Test
        @DisplayName("Returns player level with distance bonus")
        void returnsPlayerLevel_withDistanceBonus() {
            List<PlayerLevelCalculator.PlayerInfo> players = List.of(
                playerAt(500, 64, 0, 10)
            );
            // At 500 blocks: distance level = 1 + (500 - 200) / 75 = 5
            // Raw bonus = 5 - 1 = 4, halved = 2
            // Effective = 10 + 2 = 12
            Vector3d mobPosition = new Vector3d(500, 64, 0);

            int level = calculator.calculateEffectiveLevel(players, mobPosition);

            assertEquals(12, level);
        }

        @Test
        @DisplayName("Level 1 player at origin returns 1")
        void level1Player_atOrigin_returns1() {
            List<PlayerLevelCalculator.PlayerInfo> players = List.of(
                playerAt(0, 64, 0, 1)
            );
            Vector3d origin = new Vector3d(0, 64, 0);

            int level = calculator.calculateEffectiveLevel(players, origin);

            assertEquals(1, level);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Multiple Players - No Grouping Tests
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Multiple Players - Not Grouped")
    class MultiplePlayersNotGroupedTests {

        @Test
        @DisplayName("Returns average level when players are spread out")
        void returnsAverageLevel_whenPlayersSpreadOut() {
            // Players more than 20 blocks apart (group radius)
            List<PlayerLevelCalculator.PlayerInfo> players = List.of(
                playerAt(0, 64, 0, 10),
                playerAt(100, 64, 0, 20),   // 100 blocks apart
                playerAt(0, 64, 100, 15)    // 100 blocks apart
            );
            Vector3d origin = new Vector3d(0, 64, 0);

            int level = calculator.calculateEffectiveLevel(players, origin);

            // Average = (10 + 20 + 15) / 3 = 15
            // No group multiplier (all isolated)
            // No distance bonus at origin
            assertEquals(15, level);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Multiple Players - Grouped Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Multiple Players - Grouped")
    class MultiplePlayersGroupedTests {

        @Test
        @DisplayName("Two grouped players get 1.2x multiplier")
        void twoGroupedPlayers_get1_2xMultiplier() {
            // Players within 20 blocks of each other
            List<PlayerLevelCalculator.PlayerInfo> players = List.of(
                playerAt(0, 64, 0, 10),
                playerAt(5, 64, 5, 10)   // ~7 blocks apart
            );
            Vector3d origin = new Vector3d(0, 64, 0);

            int level = calculator.calculateEffectiveLevel(players, origin);

            // Average = 10
            // Group multiplier = 1.2^1 = 1.2 (2 players = 1 additional)
            // Effective = 10 × 1.2 = 12
            assertEquals(12, level);
        }

        @Test
        @DisplayName("Three grouped players get 1.44x multiplier")
        void threeGroupedPlayers_get1_44xMultiplier() {
            // 3 players all within 20 blocks of each other
            List<PlayerLevelCalculator.PlayerInfo> players = List.of(
                playerAt(0, 64, 0, 10),
                playerAt(5, 64, 0, 12),
                playerAt(0, 64, 5, 8)
            );
            Vector3d origin = new Vector3d(0, 64, 0);

            int level = calculator.calculateEffectiveLevel(players, origin);

            // Average = (10 + 12 + 8) / 3 = 10
            // Group multiplier = 1.2^2 = 1.44 (3 players = 2 additional)
            // Effective = 10 × 1.44 = 14.4 ≈ 14
            assertEquals(14, level);
        }

        @Test
        @DisplayName("Four grouped players get ~1.73x multiplier")
        void fourGroupedPlayers_get1_73xMultiplier() {
            // 4 players all within 20 blocks of each other
            List<PlayerLevelCalculator.PlayerInfo> players = List.of(
                playerAt(0, 64, 0, 10),
                playerAt(5, 64, 0, 10),
                playerAt(0, 64, 5, 10),
                playerAt(5, 64, 5, 10)
            );
            Vector3d origin = new Vector3d(0, 64, 0);

            int level = calculator.calculateEffectiveLevel(players, origin);

            // Average = 10
            // Group multiplier = 1.2^3 = 1.728 (4 players = 3 additional)
            // Effective = 10 × 1.728 = 17.28 ≈ 17
            assertEquals(17, level);
        }

        @Test
        @DisplayName("Example from docstring: 3 players (10, 12, 8) = 14")
        void docExample_threePlayersGrouped_returns14() {
            List<PlayerLevelCalculator.PlayerInfo> players = List.of(
                playerAt(0, 64, 0, 10),
                playerAt(5, 64, 0, 12),
                playerAt(0, 64, 5, 8)
            );
            Vector3d origin = new Vector3d(0, 64, 0);

            int level = calculator.calculateEffectiveLevel(players, origin);

            // From docstring: Average = 10, multiplier = 1.44, effective = 14
            assertEquals(14, level);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Mixed Scenarios Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Mixed Scenarios")
    class MixedScenariosTests {

        @Test
        @DisplayName("Some players grouped, some isolated")
        void someGrouped_someIsolated() {
            // 2 players grouped, 1 isolated
            List<PlayerLevelCalculator.PlayerInfo> players = List.of(
                playerAt(0, 64, 0, 10),     // Grouped with player 2
                playerAt(5, 64, 5, 10),     // Grouped with player 1
                playerAt(100, 64, 100, 10)  // Isolated
            );
            Vector3d origin = new Vector3d(0, 64, 0);

            int level = calculator.calculateEffectiveLevel(players, origin);

            // Average = 10
            // Max group = 2 players (player 1 and 2)
            // Group multiplier = 1.2^1 = 1.2
            // Effective = 10 × 1.2 = 12
            assertEquals(12, level);
        }

        @Test
        @DisplayName("High level players with distance bonus")
        void highLevelPlayers_withDistanceBonus() {
            List<PlayerLevelCalculator.PlayerInfo> players = List.of(
                playerAt(1000, 64, 0, 50),
                playerAt(1005, 64, 5, 50)  // Grouped
            );
            // At 1000 blocks: distance level = 1 + (1000-200)/75 = 11, bonus = (11-1)/2 = 5
            Vector3d mobPosition = new Vector3d(1000, 64, 0);

            int level = calculator.calculateEffectiveLevel(players, mobPosition);

            // Average = 50
            // Group multiplier = 1.2^1 = 1.2
            // Player based = 50 × 1.2 = 60
            // Distance bonus = 5
            // Effective = 60 + 5 = 65
            assertEquals(65, level);
        }

        @Test
        @DisplayName("Low level players far from origin")
        void lowLevelPlayers_farFromOrigin() {
            List<PlayerLevelCalculator.PlayerInfo> players = List.of(
                playerAt(1000, 64, 0, 5)
            );
            // At 1000 blocks: distance level = 1 + (1000-200)/75 = 11, bonus = (11-1)/2 = 5
            Vector3d mobPosition = new Vector3d(1000, 64, 0);

            int level = calculator.calculateEffectiveLevel(players, mobPosition);

            // Player level = 5
            // No group (single player)
            // Distance bonus = 5
            // Effective = 5 + 5 = 10
            assertEquals(10, level);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Edge Cases Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Empty player list returns distance-based level")
        void emptyPlayerList_returnsDistanceBasedLevel() {
            List<PlayerLevelCalculator.PlayerInfo> players = new ArrayList<>();
            Vector3d farPosition = new Vector3d(500, 64, 0);

            int level = calculator.calculateEffectiveLevel(players, farPosition);

            // Only distance bonus applies
            // Distance level = 1 + (500 - 200) / 75 = 5
            // Bonus = (5-1)/2 = 2
            // Effective = 1 + 2 = 3
            assertEquals(3, level);
        }

        @Test
        @DisplayName("Players at exact group radius boundary")
        void playersAtExactGroupRadiusBoundary() {
            // Default group radius is 20 blocks
            List<PlayerLevelCalculator.PlayerInfo> players = List.of(
                playerAt(0, 64, 0, 10),
                playerAt(20, 64, 0, 10)   // Exactly 20 blocks away (on boundary)
            );
            Vector3d origin = new Vector3d(0, 64, 0);

            int level = calculator.calculateEffectiveLevel(players, origin);

            // At exactly 20 blocks, distance squared = 400
            // Group radius squared (default 20) = 400
            // Should be considered grouped (<=)
            // Average = 10, multiplier = 1.2
            // Effective = 12
            assertEquals(12, level);
        }

        @Test
        @DisplayName("Very high levels don't overflow")
        void veryHighLevels_dontOverflow() {
            List<PlayerLevelCalculator.PlayerInfo> players = List.of(
                playerAt(0, 64, 0, 10000),
                playerAt(5, 64, 0, 10000),
                playerAt(0, 64, 5, 10000),
                playerAt(5, 64, 5, 10000)
            );
            Vector3d origin = new Vector3d(0, 64, 0);

            int level = calculator.calculateEffectiveLevel(players, origin);

            // Should not overflow
            assertTrue(level > 0);
            // Average = 10000, multiplier = 1.728
            // Effective = ~17280
            assertTrue(level > 10000);
        }

        @Test
        @DisplayName("Negative position coordinates work correctly")
        void negativePositionCoordinates_workCorrectly() {
            List<PlayerLevelCalculator.PlayerInfo> players = List.of(
                playerAt(-500, 64, -500, 20)
            );
            // Distance from origin = sqrt(500^2 + 500^2) ≈ 707 blocks
            Vector3d mobPosition = new Vector3d(-500, 64, -500);

            int level = calculator.calculateEffectiveLevel(players, mobPosition);

            // Should calculate correctly with negative coordinates
            assertTrue(level > 0);
            // Player level 20 + some distance bonus
            assertTrue(level >= 20);
        }

        @Test
        @DisplayName("Players at same position are grouped")
        void playersAtSamePosition_areGrouped() {
            List<PlayerLevelCalculator.PlayerInfo> players = List.of(
                playerAt(100, 64, 100, 10),
                playerAt(100, 64, 100, 10),
                playerAt(100, 64, 100, 10)
            );
            Vector3d mobPosition = new Vector3d(100, 64, 100);

            int level = calculator.calculateEffectiveLevel(players, mobPosition);

            // All at same spot = fully grouped
            // Average = 10, multiplier = 1.44
            // Plus some distance bonus for being at (100, 100)
            assertTrue(level >= 14);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PlayerInfo Record Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("PlayerInfo Record")
    class PlayerInfoTests {

        @Test
        @DisplayName("PlayerInfo stores position and level correctly")
        void playerInfo_storesPositionAndLevel() {
            Vector3d pos = new Vector3d(10, 20, 30);
            PlayerLevelCalculator.PlayerInfo info = new PlayerLevelCalculator.PlayerInfo(pos, 42);

            assertEquals(pos, info.position());
            assertEquals(42, info.level());
        }

        @Test
        @DisplayName("Two PlayerInfo with same values are equal")
        void twoPlayerInfo_withSameValues_areEqual() {
            Vector3d pos = new Vector3d(1, 2, 3);
            PlayerLevelCalculator.PlayerInfo info1 = new PlayerLevelCalculator.PlayerInfo(pos, 10);
            PlayerLevelCalculator.PlayerInfo info2 = new PlayerLevelCalculator.PlayerInfo(pos, 10);

            assertEquals(info1, info2);
            assertEquals(info1.hashCode(), info2.hashCode());
        }
    }
}
