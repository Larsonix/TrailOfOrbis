package io.github.larsonix.trailoforbis.leveling.xp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link XpSource}.
 *
 * <p>Tests XP source classification (gain vs loss), enum values,
 * and consistency of the source categorization.
 */
@DisplayName("XpSource")
class XpSourceTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // Enum Values Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Enum Values")
    class EnumValuesTests {

        @Test
        @DisplayName("MOB_KILL is a valid source")
        void mobKill_isValidSource() {
            assertNotNull(XpSource.MOB_KILL);
            assertEquals("MOB_KILL", XpSource.MOB_KILL.name());
        }

        @Test
        @DisplayName("QUEST_COMPLETE is a valid source")
        void questComplete_isValidSource() {
            assertNotNull(XpSource.QUEST_COMPLETE);
        }

        @Test
        @DisplayName("DEATH_PENALTY is a valid source")
        void deathPenalty_isValidSource() {
            assertNotNull(XpSource.DEATH_PENALTY);
        }

        @Test
        @DisplayName("All enum values are unique")
        void allValues_areUnique() {
            XpSource[] values = XpSource.values();
            for (int i = 0; i < values.length; i++) {
                for (int j = i + 1; j < values.length; j++) {
                    assertNotEquals(values[i], values[j]);
                }
            }
        }

        @Test
        @DisplayName("valueOf returns correct enum")
        void valueOf_returnsCorrectEnum() {
            assertEquals(XpSource.MOB_KILL, XpSource.valueOf("MOB_KILL"));
            assertEquals(XpSource.DEATH_PENALTY, XpSource.valueOf("DEATH_PENALTY"));
        }

        @Test
        @DisplayName("valueOf throws for invalid name")
        void valueOf_throwsForInvalidName() {
            assertThrows(IllegalArgumentException.class, () -> {
                XpSource.valueOf("INVALID_SOURCE");
            });
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Gain Source Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Gain Source Classification")
    class GainSourceTests {

        @Test
        @DisplayName("MOB_KILL is a gain source")
        void mobKill_isGainSource() {
            assertTrue(XpSource.MOB_KILL.isGainSource());
        }

        @Test
        @DisplayName("QUEST_COMPLETE is a gain source")
        void questComplete_isGainSource() {
            assertTrue(XpSource.QUEST_COMPLETE.isGainSource());
        }

        @Test
        @DisplayName("DEATH_PENALTY is NOT a gain source")
        void deathPenalty_isNotGainSource() {
            assertFalse(XpSource.DEATH_PENALTY.isGainSource());
        }

        @ParameterizedTest
        @EnumSource(value = XpSource.class, names = {"DEATH_PENALTY"}, mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("All sources except DEATH_PENALTY are gain sources")
        void allExceptDeathPenalty_areGainSources(XpSource source) {
            assertTrue(source.isGainSource(),
                source.name() + " should be a gain source");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Loss Source Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Loss Source Classification")
    class LossSourceTests {

        @Test
        @DisplayName("DEATH_PENALTY is a loss source")
        void deathPenalty_isLossSource() {
            assertTrue(XpSource.DEATH_PENALTY.isLossSource());
        }

        @Test
        @DisplayName("MOB_KILL is NOT a loss source")
        void mobKill_isNotLossSource() {
            assertFalse(XpSource.MOB_KILL.isLossSource());
        }

        @Test
        @DisplayName("QUEST_COMPLETE is NOT a loss source")
        void questComplete_isNotLossSource() {
            assertFalse(XpSource.QUEST_COMPLETE.isLossSource());
        }

        @ParameterizedTest
        @EnumSource(value = XpSource.class, names = {"DEATH_PENALTY"}, mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("All sources except DEATH_PENALTY are not loss sources")
        void allExceptDeathPenalty_areNotLossSources(XpSource source) {
            assertFalse(source.isLossSource(),
                source.name() + " should not be a loss source");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Classification Consistency Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Classification Consistency")
    class ClassificationConsistencyTests {

        @ParameterizedTest
        @EnumSource(XpSource.class)
        @DisplayName("Every source is either gain OR loss (mutually exclusive)")
        void everySource_isEitherGainOrLoss(XpSource source) {
            boolean isGain = source.isGainSource();
            boolean isLoss = source.isLossSource();

            // XOR - exactly one should be true
            assertTrue(isGain != isLoss,
                source.name() + " should be either gain or loss, not both or neither");
        }

        @ParameterizedTest
        @EnumSource(XpSource.class)
        @DisplayName("Gain and loss sources are mutually exclusive")
        void gainAndLoss_areMutuallyExclusive(XpSource source) {
            // A source cannot be both gain and loss at the same time
            assertFalse(source.isGainSource() && source.isLossSource(),
                source.name() + " cannot be both a gain and loss source");
        }

        @Test
        @DisplayName("Only DEATH_PENALTY is a loss source")
        void onlyDeathPenalty_isLossSource() {
            int lossSourceCount = 0;
            for (XpSource source : XpSource.values()) {
                if (source.isLossSource()) {
                    lossSourceCount++;
                    assertEquals(XpSource.DEATH_PENALTY, source,
                        "Only DEATH_PENALTY should be a loss source");
                }
            }
            assertEquals(1, lossSourceCount, "Should be exactly one loss source");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Common XP Sources Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Common XP Sources")
    class CommonXpSourcesTests {

        @Test
        @DisplayName("Combat sources are gain sources")
        void combatSources_areGainSources() {
            assertTrue(XpSource.MOB_KILL.isGainSource());
        }

        @Test
        @DisplayName("Progression sources are gain sources")
        void progressionSources_areGainSources() {
            assertTrue(XpSource.QUEST_COMPLETE.isGainSource());
        }

        @Test
        @DisplayName("Penalty sources are loss sources")
        void penaltySources_areLossSources() {
            assertTrue(XpSource.DEATH_PENALTY.isLossSource());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Edge Cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Enum values count is correct")
        void enumValuesCount_isCorrect() {
            // This ensures the test stays updated when new sources are added
            assertTrue(XpSource.values().length >= 2,
                "Should have at least MOB_KILL and DEATH_PENALTY");
        }

        @Test
        @DisplayName("All sources have valid ordinal")
        void allSources_haveValidOrdinal() {
            for (XpSource source : XpSource.values()) {
                assertTrue(source.ordinal() >= 0);
            }
        }

        @Test
        @DisplayName("Ordinals are sequential")
        void ordinals_areSequential() {
            XpSource[] values = XpSource.values();
            for (int i = 0; i < values.length; i++) {
                assertEquals(i, values[i].ordinal());
            }
        }
    }
}
