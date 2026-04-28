package io.github.larsonix.trailoforbis.lootfilter.model;

import io.github.larsonix.trailoforbis.gear.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link FilterRule} — AND logic, enable/disable, describe methods.
 */
class FilterRuleTest {

    private static final EquipmentType SWORD = EquipmentType.SWORD;

    private static GearData gear(GearRarity rarity, int level) {
        return GearData.builder().level(level).rarity(rarity).quality(50).build();
    }

    @Nested
    @DisplayName("matches()")
    class MatchesTests {

        @Test
        @DisplayName("rule with no conditions matches everything (when enabled)")
        void emptyConditionsMatchesAll() {
            var rule = new FilterRule("All Items", true, FilterAction.ALLOW, List.of());
            assertTrue(rule.matches(gear(GearRarity.COMMON, 1), SWORD));
        }

        @Test
        @DisplayName("rule with single matching condition returns true")
        void singleMatchingCondition() {
            var rule = new FilterRule("Rare+", true, FilterAction.ALLOW, List.of(
                    new FilterCondition.MinRarity(GearRarity.RARE)));
            assertTrue(rule.matches(gear(GearRarity.EPIC, 10), SWORD));
        }

        @Test
        @DisplayName("rule with single non-matching condition returns false")
        void singleNonMatchingCondition() {
            var rule = new FilterRule("Rare+", true, FilterAction.ALLOW, List.of(
                    new FilterCondition.MinRarity(GearRarity.RARE)));
            assertFalse(rule.matches(gear(GearRarity.COMMON, 10), SWORD));
        }

        @Test
        @DisplayName("multiple conditions are AND'd — all must match")
        void multipleConditionsAnded() {
            var rule = new FilterRule("Rare+ High Level", true, FilterAction.ALLOW, List.of(
                    new FilterCondition.MinRarity(GearRarity.RARE),
                    new FilterCondition.ItemLevelRange(20, 100)));

            // Both match
            assertTrue(rule.matches(gear(GearRarity.EPIC, 50), SWORD));
            // Rarity matches, level doesn't
            assertFalse(rule.matches(gear(GearRarity.EPIC, 5), SWORD));
            // Level matches, rarity doesn't
            assertFalse(rule.matches(gear(GearRarity.COMMON, 50), SWORD));
        }

        @Test
        @DisplayName("disabled rules never match")
        void disabledRuleNeverMatches() {
            var rule = new FilterRule("Disabled", false, FilterAction.ALLOW, List.of());
            assertFalse(rule.matches(gear(GearRarity.MYTHIC, 100), SWORD));
        }
    }

    @Nested
    @DisplayName("Construction defaults")
    class ConstructionTests {

        @Test
        @DisplayName("blank name defaults to 'New Rule'")
        void blankNameDefaults() {
            var rule = new FilterRule("", true, FilterAction.ALLOW, List.of());
            assertEquals("New Rule", rule.name());
        }

        @Test
        @DisplayName("null action defaults to ALLOW")
        void nullActionDefaults() {
            var rule = new FilterRule("Test", true, null, List.of());
            assertEquals(FilterAction.ALLOW, rule.action());
        }

        @Test
        @DisplayName("null conditions defaults to empty list")
        void nullConditionsDefaults() {
            var rule = new FilterRule("Test", true, FilterAction.BLOCK, null);
            assertTrue(rule.conditions().isEmpty());
        }

        @Test
        @DisplayName("conditions list is immutable")
        void conditionsImmutable() {
            var rule = new FilterRule("Test", true, FilterAction.ALLOW, List.of(
                    new FilterCondition.MinRarity(GearRarity.RARE)));
            assertThrows(UnsupportedOperationException.class, () ->
                    rule.conditions().add(new FilterCondition.MinRarity(GearRarity.EPIC)));
        }
    }

    @Nested
    @DisplayName("describeSummary()")
    class DescribeSummaryTests {

        @Test
        @DisplayName("empty conditions shows 'Everything'")
        void emptyConditionsShowsEverything() {
            var rule = new FilterRule("All", true, FilterAction.BLOCK, List.of());
            assertEquals("Everything > BLOCK", rule.describeSummary());
        }

        @Test
        @DisplayName("single condition shows condition description")
        void singleCondition() {
            var rule = new FilterRule("Rare+", true, FilterAction.ALLOW, List.of(
                    new FilterCondition.MinRarity(GearRarity.RARE)));
            String summary = rule.describeSummary();
            assertTrue(summary.contains("RARE"));
            assertTrue(summary.endsWith("> ALLOW"));
        }
    }

    @Nested
    @DisplayName("describeMatch()")
    class DescribeMatchTests {

        @Test
        @DisplayName("produces pass/fail lines per condition")
        void producesPerConditionLines() {
            var rule = new FilterRule("Test", true, FilterAction.ALLOW, List.of(
                    new FilterCondition.MinRarity(GearRarity.RARE),
                    new FilterCondition.ItemLevelRange(20, 100)));

            var result = rule.describeMatch(gear(GearRarity.EPIC, 5), SWORD);
            assertEquals(2, result.size());
            assertTrue(result.get(0).startsWith("[x]")); // rarity passes
            assertTrue(result.get(1).startsWith("[!]")); // level fails
        }
    }

    @Nested
    @DisplayName("withEnabled()")
    class WithEnabledTests {

        @Test
        @DisplayName("creates copy with changed enabled state")
        void changesEnabledState() {
            var rule = new FilterRule("Test", true, FilterAction.ALLOW, List.of());
            var disabled = rule.withEnabled(false);
            assertTrue(rule.enabled());
            assertFalse(disabled.enabled());
            assertEquals(rule.name(), disabled.name());
            assertEquals(rule.action(), disabled.action());
        }
    }
}
