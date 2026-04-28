package io.github.larsonix.trailoforbis.lootfilter.model;

import io.github.larsonix.trailoforbis.gear.model.EquipmentType;
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link FilterProfile} — first-match-wins evaluation, immutable updates, builder.
 */
class FilterProfileTest {

    private static final EquipmentType SWORD = EquipmentType.SWORD;

    private static GearData gear(GearRarity rarity) {
        return GearData.builder().level(10).rarity(rarity).quality(50).build();
    }

    private static FilterRule allowRule(String name, GearRarity minRarity) {
        return new FilterRule(name, true, FilterAction.ALLOW, List.of(
                new FilterCondition.MinRarity(minRarity)));
    }

    private static FilterRule blockRule(String name, GearRarity maxRarity) {
        return new FilterRule(name, true, FilterAction.BLOCK, List.of(
                new FilterCondition.MaxRarity(maxRarity)));
    }

    // ═══════════════════════════════════════════════════════════════════
    // EVALUATION
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("evaluate()")
    class EvaluateTests {

        @Test
        @DisplayName("returns default action when no rules match")
        void defaultActionWhenNoMatch() {
            var profile = FilterProfile.builder()
                    .defaultAction(FilterAction.BLOCK)
                    .addRule(allowRule("Epic+", GearRarity.EPIC))
                    .build();

            assertEquals(FilterAction.BLOCK, profile.evaluate(gear(GearRarity.COMMON), SWORD));
        }

        @Test
        @DisplayName("first matching rule wins")
        void firstMatchWins() {
            var profile = FilterProfile.builder()
                    .defaultAction(FilterAction.ALLOW)
                    .addRule(blockRule("Block Common", GearRarity.COMMON))
                    .addRule(allowRule("Allow All", GearRarity.COMMON))
                    .build();

            // COMMON item: first rule blocks (MaxRarity COMMON), second would allow
            assertEquals(FilterAction.BLOCK, profile.evaluate(gear(GearRarity.COMMON), SWORD));
        }

        @Test
        @DisplayName("disabled rules are skipped")
        void disabledRulesSkipped() {
            var disabledBlock = new FilterRule("Block All", false, FilterAction.BLOCK, List.of());
            var profile = FilterProfile.builder()
                    .defaultAction(FilterAction.ALLOW)
                    .addRule(disabledBlock)
                    .build();

            // Disabled rule skipped, falls through to default
            assertEquals(FilterAction.ALLOW, profile.evaluate(gear(GearRarity.RARE), SWORD));
        }

        @Test
        @DisplayName("profile with no rules uses default action")
        void noRulesUsesDefault() {
            var profile = FilterProfile.builder()
                    .defaultAction(FilterAction.BLOCK)
                    .build();

            assertEquals(FilterAction.BLOCK, profile.evaluate(gear(GearRarity.MYTHIC), SWORD));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // EVALUATION WITH TRACE
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("evaluateWithTrace()")
    class EvaluateWithTraceTests {

        @Test
        @DisplayName("trace shows matched rule number when rule matches")
        void traceShowsMatchedRule() {
            var profile = FilterProfile.builder()
                    .defaultAction(FilterAction.ALLOW)
                    .addRule(allowRule("Skip", GearRarity.LEGENDARY))  // won't match RARE
                    .addRule(blockRule("Match", GearRarity.RARE))       // will match RARE
                    .build();

            var trace = profile.evaluateWithTrace(gear(GearRarity.RARE), SWORD);
            assertTrue(trace.matchedByRule());
            assertEquals(2, trace.matchedRuleNumber());
            assertEquals(FilterAction.BLOCK, trace.result());
        }

        @Test
        @DisplayName("trace shows -1 when falling to default")
        void traceShowsDefaultFallthrough() {
            var profile = FilterProfile.builder()
                    .defaultAction(FilterAction.ALLOW)
                    .addRule(allowRule("Epic+", GearRarity.EPIC))
                    .build();

            var trace = profile.evaluateWithTrace(gear(GearRarity.COMMON), SWORD);
            assertFalse(trace.matchedByRule());
            assertEquals(-1, trace.matchedRuleNumber());
            assertEquals(FilterAction.ALLOW, trace.result());
        }

        @Test
        @DisplayName("trace includes condition details for each checked rule")
        void traceIncludesConditionDetails() {
            var profile = FilterProfile.builder()
                    .defaultAction(FilterAction.ALLOW)
                    .addRule(allowRule("Epic+", GearRarity.EPIC))
                    .build();

            var trace = profile.evaluateWithTrace(gear(GearRarity.RARE), SWORD);
            assertEquals(1, trace.ruleTraces().size());
            assertFalse(trace.ruleTraces().get(0).matched());
            assertFalse(trace.ruleTraces().get(0).conditionDetails().isEmpty());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // IMMUTABLE UPDATES
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Immutable update methods")
    class ImmutableUpdateTests {

        private FilterProfile base;

        @BeforeEach
        void setUp() {
            base = FilterProfile.builder()
                    .id("test-id")
                    .name("Test")
                    .defaultAction(FilterAction.ALLOW)
                    .addRule(allowRule("Rule1", GearRarity.RARE))
                    .build();
        }

        @Test
        @DisplayName("withName creates new profile with changed name")
        void withNameChangesName() {
            var updated = base.withName("New Name");
            assertEquals("New Name", updated.getName());
            assertEquals("Test", base.getName()); // original unchanged
            assertEquals(base.getId(), updated.getId());
        }

        @Test
        @DisplayName("withDefaultAction changes default")
        void withDefaultActionChanges() {
            var updated = base.withDefaultAction(FilterAction.BLOCK);
            assertEquals(FilterAction.BLOCK, updated.getDefaultAction());
            assertEquals(FilterAction.ALLOW, base.getDefaultAction());
        }

        @Test
        @DisplayName("withAddedRule appends rule")
        void withAddedRuleAppends() {
            var updated = base.withAddedRule(blockRule("Rule2", GearRarity.COMMON));
            assertEquals(2, updated.getRules().size());
            assertEquals(1, base.getRules().size());
        }

        @Test
        @DisplayName("withRemovedRule removes by index")
        void withRemovedRule() {
            var updated = base.withRemovedRule(0);
            assertTrue(updated.getRules().isEmpty());
            assertEquals(1, base.getRules().size());
        }

        @Test
        @DisplayName("withRemovedRule throws on bad index")
        void withRemovedRuleBadIndex() {
            assertThrows(IndexOutOfBoundsException.class, () -> base.withRemovedRule(5));
            assertThrows(IndexOutOfBoundsException.class, () -> base.withRemovedRule(-1));
        }

        @Test
        @DisplayName("withMovedRule reorders rules")
        void withMovedRuleReorders() {
            var twoRules = base.withAddedRule(blockRule("Rule2", GearRarity.COMMON));
            var moved = twoRules.withMovedRule(1, 0);
            assertEquals("Rule2", moved.getRules().get(0).name());
            assertEquals("Rule1", moved.getRules().get(1).name());
        }

        @Test
        @DisplayName("withUpdatedRule replaces rule at index")
        void withUpdatedRule() {
            var newRule = blockRule("Updated", GearRarity.EPIC);
            var updated = base.withUpdatedRule(0, newRule);
            assertEquals("Updated", updated.getRules().get(0).name());
            assertEquals(FilterAction.BLOCK, updated.getRules().get(0).action());
        }

        @Test
        @DisplayName("withRules replaces entire rule list")
        void withRulesReplaces() {
            var updated = base.withRules(List.of(
                    blockRule("A", GearRarity.COMMON),
                    blockRule("B", GearRarity.UNCOMMON)));
            assertEquals(2, updated.getRules().size());
            assertEquals("A", updated.getRules().get(0).name());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // BUILDER
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("builder generates UUID if not specified")
        void builderGeneratesId() {
            var profile = FilterProfile.builder().build();
            assertNotNull(profile.getId());
            assertFalse(profile.getId().isBlank());
        }

        @Test
        @DisplayName("builder uses specified ID")
        void builderUsesId() {
            var profile = FilterProfile.builder().id("custom-id").build();
            assertEquals("custom-id", profile.getId());
        }

        @Test
        @DisplayName("builder defaults name to 'New Filter'")
        void builderDefaultsName() {
            var profile = FilterProfile.builder().build();
            assertEquals("New Filter", profile.getName());
        }

        @Test
        @DisplayName("builder defaults action to ALLOW")
        void builderDefaultsAction() {
            var profile = FilterProfile.builder().build();
            assertEquals(FilterAction.ALLOW, profile.getDefaultAction());
        }

        @Test
        @DisplayName("toBuilder creates equivalent copy")
        void toBuilderCreatesEquivalent() {
            var original = FilterProfile.builder()
                    .id("id-1")
                    .name("My Filter")
                    .defaultAction(FilterAction.BLOCK)
                    .addRule(allowRule("R1", GearRarity.RARE))
                    .build();

            var copy = original.toBuilder().build();
            assertEquals(original.getId(), copy.getId());
            assertEquals(original.getName(), copy.getName());
            assertEquals(original.getDefaultAction(), copy.getDefaultAction());
            assertEquals(original.getRules().size(), copy.getRules().size());
        }

        @Test
        @DisplayName("rules list is immutable after build")
        void rulesImmutableAfterBuild() {
            var profile = FilterProfile.builder()
                    .addRule(allowRule("R1", GearRarity.RARE))
                    .build();
            assertThrows(UnsupportedOperationException.class, () ->
                    profile.getRules().add(blockRule("R2", GearRarity.COMMON)));
        }
    }
}
