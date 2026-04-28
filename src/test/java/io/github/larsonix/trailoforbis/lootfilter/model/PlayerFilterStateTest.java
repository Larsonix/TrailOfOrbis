package io.github.larsonix.trailoforbis.lootfilter.model;

import io.github.larsonix.trailoforbis.gear.model.EquipmentType;
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link PlayerFilterState} — mutual exclusivity, evaluation, immutable updates.
 */
class PlayerFilterStateTest {

    private static final UUID PLAYER_ID = UUID.randomUUID();
    private static final EquipmentType SWORD = EquipmentType.SWORD;

    private static GearData gear(GearRarity rarity) {
        return GearData.builder().level(10).rarity(rarity).quality(50).build();
    }

    private FilterProfile testProfile(String id) {
        return FilterProfile.builder()
                .id(id)
                .name("Profile " + id)
                .defaultAction(FilterAction.ALLOW)
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════
    // MUTUAL EXCLUSIVITY
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Quick filter / profile mutual exclusivity")
    class MutualExclusivityTests {

        @Test
        @DisplayName("setting quickFilterRarity clears activeProfileId")
        void quickFilterClearsProfile() {
            var profile = testProfile("p1");
            var state = PlayerFilterState.builder()
                    .playerId(PLAYER_ID)
                    .profiles(List.of(profile))
                    .activeProfileId("p1")
                    .build();

            var updated = state.withQuickFilterRarity(GearRarity.RARE);
            assertEquals(GearRarity.RARE, updated.getQuickFilterRarity());
            assertNull(updated.getActiveProfileId());
        }

        @Test
        @DisplayName("setting activeProfileId clears quickFilterRarity")
        void profileClearsQuickFilter() {
            var profile = testProfile("p1");
            var state = PlayerFilterState.builder()
                    .playerId(PLAYER_ID)
                    .profiles(List.of(profile))
                    .quickFilterRarity(GearRarity.EPIC)
                    .build();

            var updated = state.withActiveProfileId("p1");
            assertEquals("p1", updated.getActiveProfileId());
            assertNull(updated.getQuickFilterRarity());
        }

        @Test
        @DisplayName("setting activeProfileId to null clears only the profile")
        void nullProfileClearsProfile() {
            var profile = testProfile("p1");
            var state = PlayerFilterState.builder()
                    .playerId(PLAYER_ID)
                    .profiles(List.of(profile))
                    .activeProfileId("p1")
                    .build();

            var updated = state.withActiveProfileId(null);
            assertNull(updated.getActiveProfileId());
        }

        @Test
        @DisplayName("setting quickFilterRarity to null clears only the quick filter")
        void nullQuickFilterClearsQuickFilter() {
            var state = PlayerFilterState.builder()
                    .playerId(PLAYER_ID)
                    .quickFilterRarity(GearRarity.RARE)
                    .build();

            var updated = state.withQuickFilterRarity(null);
            assertNull(updated.getQuickFilterRarity());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // EVALUATION
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("evaluate()")
    class EvaluateTests {

        @Test
        @DisplayName("quick filter allows items at or above rarity")
        void quickFilterAllowsAbove() {
            var state = PlayerFilterState.builder()
                    .playerId(PLAYER_ID)
                    .filteringEnabled(true)
                    .quickFilterRarity(GearRarity.RARE)
                    .build();

            assertEquals(FilterAction.ALLOW, state.evaluate(gear(GearRarity.RARE), SWORD));
            assertEquals(FilterAction.ALLOW, state.evaluate(gear(GearRarity.EPIC), SWORD));
        }

        @Test
        @DisplayName("quick filter blocks items below rarity")
        void quickFilterBlocksBelow() {
            var state = PlayerFilterState.builder()
                    .playerId(PLAYER_ID)
                    .filteringEnabled(true)
                    .quickFilterRarity(GearRarity.RARE)
                    .build();

            assertEquals(FilterAction.BLOCK, state.evaluate(gear(GearRarity.COMMON), SWORD));
            assertEquals(FilterAction.BLOCK, state.evaluate(gear(GearRarity.UNCOMMON), SWORD));
        }

        @Test
        @DisplayName("quick filter takes priority over profile")
        void quickFilterPriority() {
            // Build a profile that would BLOCK everything
            var blockProfile = FilterProfile.builder()
                    .id("blocker")
                    .defaultAction(FilterAction.BLOCK)
                    .build();

            var state = PlayerFilterState.builder()
                    .playerId(PLAYER_ID)
                    .profiles(List.of(blockProfile))
                    .quickFilterRarity(GearRarity.COMMON)
                    .build();

            // Quick filter allows COMMON+, profile would block — quick filter wins
            assertEquals(FilterAction.ALLOW, state.evaluate(gear(GearRarity.RARE), SWORD));
        }

        @Test
        @DisplayName("profile evaluation used when no quick filter")
        void profileEvaluationUsed() {
            var profile = FilterProfile.builder()
                    .id("p1")
                    .defaultAction(FilterAction.BLOCK)
                    .build();

            var state = PlayerFilterState.builder()
                    .playerId(PLAYER_ID)
                    .profiles(List.of(profile))
                    .activeProfileId("p1")
                    .build();

            assertEquals(FilterAction.BLOCK, state.evaluate(gear(GearRarity.RARE), SWORD));
        }

        @Test
        @DisplayName("no active filter defaults to ALLOW")
        void noActiveFilterDefaults() {
            var state = PlayerFilterState.builder()
                    .playerId(PLAYER_ID)
                    .build();

            assertEquals(FilterAction.ALLOW, state.evaluate(gear(GearRarity.COMMON), SWORD));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // QUERY
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Query methods")
    class QueryTests {

        @Test
        @DisplayName("hasActiveFilter returns true with quick filter")
        void hasActiveWithQuickFilter() {
            var state = PlayerFilterState.builder()
                    .playerId(PLAYER_ID)
                    .quickFilterRarity(GearRarity.RARE)
                    .build();
            assertTrue(state.hasActiveFilter());
        }

        @Test
        @DisplayName("hasActiveFilter returns true with active profile")
        void hasActiveWithProfile() {
            var profile = testProfile("p1");
            var state = PlayerFilterState.builder()
                    .playerId(PLAYER_ID)
                    .profiles(List.of(profile))
                    .activeProfileId("p1")
                    .build();
            assertTrue(state.hasActiveFilter());
        }

        @Test
        @DisplayName("hasActiveFilter returns false with nothing active")
        void hasActiveWithNothing() {
            var state = PlayerFilterState.builder()
                    .playerId(PLAYER_ID)
                    .build();
            assertFalse(state.hasActiveFilter());
        }

        @Test
        @DisplayName("isUsingQuickFilter")
        void isUsingQuickFilter() {
            var state = PlayerFilterState.builder()
                    .playerId(PLAYER_ID)
                    .quickFilterRarity(GearRarity.EPIC)
                    .build();
            assertTrue(state.isUsingQuickFilter());
        }

        @Test
        @DisplayName("getProfileByName is case-insensitive")
        void getProfileByNameCaseInsensitive() {
            var profile = testProfile("p1");
            var state = PlayerFilterState.builder()
                    .playerId(PLAYER_ID)
                    .profiles(List.of(profile))
                    .build();

            assertTrue(state.getProfileByName("Profile p1").isPresent());
            assertTrue(state.getProfileByName("profile p1").isPresent());
            assertTrue(state.getProfileByName("PROFILE P1").isPresent());
        }

        @Test
        @DisplayName("getProfileById returns exact match")
        void getProfileById() {
            var profile = testProfile("my-id");
            var state = PlayerFilterState.builder()
                    .playerId(PLAYER_ID)
                    .profiles(List.of(profile))
                    .build();

            assertTrue(state.getProfileById("my-id").isPresent());
            assertFalse(state.getProfileById("other").isPresent());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // PROFILE MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Profile management")
    class ProfileManagementTests {

        private PlayerFilterState state;

        @BeforeEach
        void setUp() {
            state = PlayerFilterState.builder()
                    .playerId(PLAYER_ID)
                    .profiles(List.of(testProfile("p1"), testProfile("p2")))
                    .activeProfileId("p1")
                    .build();
        }

        @Test
        @DisplayName("withAddedProfile adds to list")
        void addProfile() {
            var updated = state.withAddedProfile(testProfile("p3"));
            assertEquals(3, updated.getProfileCount());
        }

        @Test
        @DisplayName("withRemovedProfile removes by ID")
        void removeProfile() {
            var updated = state.withRemovedProfile("p2");
            assertEquals(1, updated.getProfileCount());
            assertFalse(updated.getProfileById("p2").isPresent());
        }

        @Test
        @DisplayName("removing active profile clears activeProfileId")
        void removeActiveProfileClearsId() {
            var updated = state.withRemovedProfile("p1");
            assertNull(updated.getActiveProfileId());
        }

        @Test
        @DisplayName("removing non-active profile preserves activeProfileId")
        void removeNonActivePreservesId() {
            var updated = state.withRemovedProfile("p2");
            assertEquals("p1", updated.getActiveProfileId());
        }

        @Test
        @DisplayName("withUpdatedProfile replaces by ID")
        void updateProfile() {
            var updated = state.withUpdatedProfile(
                    FilterProfile.builder().id("p1").name("Updated").build());
            assertEquals("Updated", updated.getProfileById("p1").get().getName());
        }

        @Test
        @DisplayName("withUpdatedProfile throws for unknown ID")
        void updateProfileUnknownId() {
            assertThrows(IllegalArgumentException.class, () ->
                    state.withUpdatedProfile(testProfile("unknown")));
        }

        @Test
        @DisplayName("withActiveProfileId throws for unknown profile ID")
        void activeProfileUnknownId() {
            assertThrows(IllegalArgumentException.class, () ->
                    state.withActiveProfileId("unknown"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // BUILDER
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("builder requires playerId")
        void requiresPlayerId() {
            assertThrows(IllegalStateException.class, () ->
                    PlayerFilterState.builder().build());
        }

        @Test
        @DisplayName("builder defaults filteringEnabled to false")
        void defaultsFilteringDisabled() {
            var state = PlayerFilterState.builder().playerId(PLAYER_ID).build();
            assertFalse(state.isFilteringEnabled());
        }

        @Test
        @DisplayName("builder defaults empty profiles")
        void defaultsEmptyProfiles() {
            var state = PlayerFilterState.builder().playerId(PLAYER_ID).build();
            assertEquals(0, state.getProfileCount());
        }

        @Test
        @DisplayName("toBuilder preserves all fields")
        void toBuilderPreserves() {
            var original = PlayerFilterState.builder()
                    .playerId(PLAYER_ID)
                    .profiles(List.of(testProfile("p1")))
                    .activeProfileId("p1")
                    .filteringEnabled(true)
                    .build();

            var copy = original.toBuilder().build();
            assertEquals(original.getPlayerId(), copy.getPlayerId());
            assertEquals(original.getActiveProfileId(), copy.getActiveProfileId());
            assertEquals(original.isFilteringEnabled(), copy.isFilteringEnabled());
            assertEquals(original.getProfileCount(), copy.getProfileCount());
        }
    }
}
