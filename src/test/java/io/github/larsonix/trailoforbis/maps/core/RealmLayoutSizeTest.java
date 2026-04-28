package io.github.larsonix.trailoforbis.maps.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RealmLayoutSize")
class RealmLayoutSizeTest {

    @Nested
    @DisplayName("calculateMonsterCount")
    class CalculateMonsterCount {

        @Test
        @DisplayName("Should return base count at level 1")
        void baseCountAtLevel1() {
            // MEDIUM: base=25, multiplier=1.0, level multiplier at 1 = 1.02
            int count = RealmLayoutSize.MEDIUM.calculateMonsterCount(1);
            assertEquals(26, count); // 25 * 1.0 * 1.02 = 25.5 ≈ 26
        }

        @Test
        @DisplayName("Should scale with level")
        void scalesWithLevel() {
            // At level 100: multiplier = 1.0 + (100 * 0.02) = 3.0
            int count = RealmLayoutSize.MEDIUM.calculateMonsterCount(100);
            assertEquals(75, count); // 25 * 1.0 * 3.0 = 75
        }

        @Test
        @DisplayName("Small should have fewer monsters")
        void smallHasFewerMonsters() {
            int small = RealmLayoutSize.SMALL.calculateMonsterCount(50);
            int medium = RealmLayoutSize.MEDIUM.calculateMonsterCount(50);
            assertTrue(small < medium);
        }

        @Test
        @DisplayName("Massive should have most monsters")
        void massiveHasMostMonsters() {
            int large = RealmLayoutSize.LARGE.calculateMonsterCount(50);
            int massive = RealmLayoutSize.MASSIVE.calculateMonsterCount(50);
            assertTrue(massive > large);
        }
    }

    @Nested
    @DisplayName("getRewardMultiplier")
    class GetRewardMultiplier {

        @Test
        @DisplayName("Small should have 1.0x rewards")
        void smallRewards() {
            assertEquals(1.0f, RealmLayoutSize.SMALL.getRewardMultiplier());
        }

        @Test
        @DisplayName("Massive should have 4.0x rewards")
        void massiveRewards() {
            assertEquals(4.0f, RealmLayoutSize.MASSIVE.getRewardMultiplier());
        }

        @Test
        @DisplayName("Rewards should increase with size")
        void rewardsIncreaseWithSize() {
            float small = RealmLayoutSize.SMALL.getRewardMultiplier();
            float medium = RealmLayoutSize.MEDIUM.getRewardMultiplier();
            float large = RealmLayoutSize.LARGE.getRewardMultiplier();
            float massive = RealmLayoutSize.MASSIVE.getRewardMultiplier();

            assertTrue(small < medium);
            assertTrue(medium < large);
            assertTrue(large < massive);
        }
    }

    @Nested
    @DisplayName("getNextSize")
    class GetNextSize {

        @Test
        @DisplayName("Small should have Medium as next")
        void smallNextIsMedium() {
            assertTrue(RealmLayoutSize.SMALL.getNextSize().isPresent());
            assertEquals(RealmLayoutSize.MEDIUM, RealmLayoutSize.SMALL.getNextSize().get());
        }

        @Test
        @DisplayName("Massive should have no next")
        void massiveHasNoNext() {
            assertTrue(RealmLayoutSize.MASSIVE.getNextSize().isEmpty());
        }
    }

    @Nested
    @DisplayName("getPreviousSize")
    class GetPreviousSize {

        @Test
        @DisplayName("Medium should have Small as previous")
        void mediumPrevIsSmall() {
            assertTrue(RealmLayoutSize.MEDIUM.getPreviousSize().isPresent());
            assertEquals(RealmLayoutSize.SMALL, RealmLayoutSize.MEDIUM.getPreviousSize().get());
        }

        @Test
        @DisplayName("Small should have no previous")
        void smallHasNoPrevious() {
            assertTrue(RealmLayoutSize.SMALL.getPreviousSize().isEmpty());
        }
    }

    @Nested
    @DisplayName("randomWeighted")
    class RandomWeighted {

        @Test
        @DisplayName("Should return valid size")
        void returnsValidSize() {
            Random random = new Random(42);
            for (int i = 0; i < 100; i++) {
                RealmLayoutSize size = RealmLayoutSize.randomWeighted(random);
                assertNotNull(size);
            }
        }

        @Test
        @DisplayName("Small should be most common")
        void smallMostCommon() {
            Random random = new Random(42);
            int smallCount = 0;
            int total = 1000;

            for (int i = 0; i < total; i++) {
                if (RealmLayoutSize.randomWeighted(random) == RealmLayoutSize.SMALL) {
                    smallCount++;
                }
            }

            // Small has 40% weight, should be roughly 400 out of 1000
            assertTrue(smallCount > 300 && smallCount < 500,
                "Expected ~40% SMALL, got " + (smallCount / 10.0) + "%");
        }
    }

    @Nested
    @DisplayName("fromString")
    class FromString {

        @Test
        @DisplayName("Should parse valid sizes")
        void parseValid() {
            assertEquals(RealmLayoutSize.SMALL, RealmLayoutSize.fromString("small"));
            assertEquals(RealmLayoutSize.MEDIUM, RealmLayoutSize.fromString("MEDIUM"));
            assertEquals(RealmLayoutSize.LARGE, RealmLayoutSize.fromString("Large"));
            assertEquals(RealmLayoutSize.MASSIVE, RealmLayoutSize.fromString("massive"));
        }

        @Test
        @DisplayName("Should throw on invalid")
        void throwsOnInvalid() {
            assertThrows(IllegalArgumentException.class,
                () -> RealmLayoutSize.fromString("huge"));
        }
    }

    @Nested
    @DisplayName("timeouts")
    class Timeouts {

        @Test
        @DisplayName("Small should have shortest timeout")
        void smallShortestTimeout() {
            assertEquals(300, RealmLayoutSize.SMALL.getBaseTimeoutSeconds());
        }

        @Test
        @DisplayName("Massive should have longest timeout")
        void massiveLongestTimeout() {
            assertEquals(1200, RealmLayoutSize.MASSIVE.getBaseTimeoutSeconds());
        }
    }

    @Nested
    @DisplayName("guaranteedBosses")
    class GuaranteedBosses {

        @Test
        @DisplayName("Small should have no guaranteed bosses")
        void smallNoBosses() {
            assertEquals(0, RealmLayoutSize.SMALL.getGuaranteedBosses());
        }

        @Test
        @DisplayName("Medium and Large should have 1 guaranteed boss")
        void mediumLargeOneBoss() {
            assertEquals(1, RealmLayoutSize.MEDIUM.getGuaranteedBosses());
            assertEquals(1, RealmLayoutSize.LARGE.getGuaranteedBosses());
        }

        @Test
        @DisplayName("Massive should have 2 guaranteed bosses")
        void massiveTwoBosses() {
            assertEquals(2, RealmLayoutSize.MASSIVE.getGuaranteedBosses());
        }
    }
}
