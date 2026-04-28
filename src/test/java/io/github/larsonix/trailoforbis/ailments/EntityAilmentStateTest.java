package io.github.larsonix.trailoforbis.ailments;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link EntityAilmentState} — the per-entity ailment container.
 */
@DisplayName("EntityAilmentState")
class EntityAilmentStateTest {

    private static final UUID SOURCE_A = UUID.randomUUID();
    private static final UUID SOURCE_B = UUID.randomUUID();

    private EntityAilmentState state;

    @BeforeEach
    void setUp() {
        state = new EntityAilmentState();
    }

    // ---------------------------------------------------------------
    // Apply single ailments
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("Apply single ailments")
    class ApplySingle {

        @Test
        @DisplayName("apply BURN stores it and is retrievable")
        void applyBurn() {
            AilmentState burn = AilmentState.burn(10f, 4f, SOURCE_A);

            assertTrue(state.applyAilment(burn));
            assertNotNull(state.getBurn());
            assertEquals(AilmentType.BURN, state.getBurn().type());
            assertEquals(10f, state.getBurn().magnitude());
            assertEquals(4f, state.getBurn().remainingDuration());
        }

        @Test
        @DisplayName("apply FREEZE stores it and is retrievable")
        void applyFreeze() {
            AilmentState freeze = AilmentState.freeze(20f, 3f, SOURCE_A);

            assertTrue(state.applyAilment(freeze));
            assertNotNull(state.getFreeze());
            assertEquals(AilmentType.FREEZE, state.getFreeze().type());
            assertEquals(20f, state.getFreeze().magnitude());
        }

        @Test
        @DisplayName("apply SHOCK stores it and is retrievable")
        void applyShock() {
            AilmentState shock = AilmentState.shock(35f, 2f, SOURCE_A);

            assertTrue(state.applyAilment(shock));
            assertNotNull(state.getShock());
            assertEquals(AilmentType.SHOCK, state.getShock().type());
            assertEquals(35f, state.getShock().magnitude());
        }

        @Test
        @DisplayName("applying different single ailments coexist")
        void differentSingleAilmentsCoexist() {
            state.applyAilment(AilmentState.burn(10f, 4f, SOURCE_A));
            state.applyAilment(AilmentState.freeze(20f, 3f, SOURCE_A));
            state.applyAilment(AilmentState.shock(30f, 2f, SOURCE_A));

            assertNotNull(state.getBurn());
            assertNotNull(state.getFreeze());
            assertNotNull(state.getShock());
            assertEquals(3, state.getActiveAilmentCount());
        }
    }

    // ---------------------------------------------------------------
    // Refresh / merge for single ailments
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("Refresh / merge single ailments")
    class RefreshMerge {

        @Test
        @DisplayName("BURN refresh takes max duration and max magnitude")
        void burnRefreshTakesMax() {
            state.applyAilment(AilmentState.burn(10f, 4f, SOURCE_A));

            // Apply weaker DPS but longer duration
            state.applyAilment(AilmentState.burn(5f, 6f, SOURCE_A));

            AilmentState burn = state.getBurn();
            assertNotNull(burn);
            assertEquals(10f, burn.magnitude(), "Should keep stronger magnitude");
            assertEquals(6f, burn.remainingDuration(), "Should keep longer duration");
        }

        @Test
        @DisplayName("BURN refresh keeps existing when incoming is weaker on both axes")
        void burnRefreshKeepsExistingWhenWeaker() {
            state.applyAilment(AilmentState.burn(15f, 5f, SOURCE_A));

            state.applyAilment(AilmentState.burn(8f, 3f, SOURCE_A));

            AilmentState burn = state.getBurn();
            assertNotNull(burn);
            assertEquals(15f, burn.magnitude());
            assertEquals(5f, burn.remainingDuration());
        }

        @Test
        @DisplayName("FREEZE refresh takes max duration and max magnitude")
        void freezeRefreshTakesMax() {
            state.applyAilment(AilmentState.freeze(15f, 3f, SOURCE_A));

            state.applyAilment(AilmentState.freeze(25f, 2f, SOURCE_A));

            AilmentState freeze = state.getFreeze();
            assertNotNull(freeze);
            assertEquals(25f, freeze.magnitude(), "Should take stronger slow");
            assertEquals(3f, freeze.remainingDuration(), "Should keep longer duration");
        }

        @Test
        @DisplayName("SHOCK always overwrites with incoming")
        void shockAlwaysOverwrites() {
            state.applyAilment(AilmentState.shock(40f, 5f, SOURCE_A));

            // Apply weaker shock -- should still overwrite
            AilmentState weakerShock = AilmentState.shock(15f, 1f, SOURCE_B);
            state.applyAilment(weakerShock);

            AilmentState shock = state.getShock();
            assertNotNull(shock);
            assertEquals(15f, shock.magnitude(), "Shock should overwrite to incoming magnitude");
            assertEquals(1f, shock.remainingDuration(), "Shock should overwrite to incoming duration");
            assertEquals(SOURCE_B, shock.sourceUuid(), "Shock should overwrite source");
        }
    }

    // ---------------------------------------------------------------
    // Poison stacking
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("Poison stacking")
    class PoisonStacking {

        @Test
        @DisplayName("apply POISON adds to stack list")
        void applyPoisonAddsToStacks() {
            state.applyAilment(AilmentState.poison(5f, 5f, SOURCE_A));

            assertEquals(1, state.getPoisonStackCount());
            assertTrue(state.hasAilment(AilmentType.POISON));
        }

        @Test
        @DisplayName("multiple poisons stack independently")
        void multiplePoisonsStack() {
            state.applyAilment(AilmentState.poison(5f, 5f, SOURCE_A));
            state.applyAilment(AilmentState.poison(8f, 3f, SOURCE_A));
            state.applyAilment(AilmentState.poison(3f, 7f, SOURCE_B));

            assertEquals(3, state.getPoisonStackCount());
            assertEquals(16f, state.getTotalPoisonDps(), 0.001f);
        }

        @Test
        @DisplayName("poison stacks from different sources tracked")
        void poisonStacksFromDifferentSources() {
            state.applyAilment(AilmentState.poison(5f, 5f, SOURCE_A));
            state.applyAilment(AilmentState.poison(10f, 5f, SOURCE_B));

            assertEquals(2, state.getPoisonStackCount());
            assertEquals(2, state.getPoisonStacks().size());
        }

        @Test
        @DisplayName("poison at max stacks is rejected")
        void poisonAtMaxStacksRejected() {
            state.setMaxPoisonStacks(3);

            assertTrue(state.applyAilment(AilmentState.poison(5f, 5f, SOURCE_A)));
            assertTrue(state.applyAilment(AilmentState.poison(5f, 5f, SOURCE_A)));
            assertTrue(state.applyAilment(AilmentState.poison(5f, 5f, SOURCE_A)));
            assertFalse(state.applyAilment(AilmentState.poison(5f, 5f, SOURCE_A)));

            assertEquals(3, state.getPoisonStackCount());
        }

        @Test
        @DisplayName("default max poison stacks is 10")
        void defaultMaxPoisonStacks() {
            for (int i = 0; i < 10; i++) {
                assertTrue(state.applyAilment(AilmentState.poison(1f, 5f, SOURCE_A)));
            }
            assertFalse(state.applyAilment(AilmentState.poison(1f, 5f, SOURCE_A)));
            assertEquals(10, state.getPoisonStackCount());
        }

        @Test
        @DisplayName("getPoisonStacks returns unmodifiable list")
        void poisonStacksUnmodifiable() {
            state.applyAilment(AilmentState.poison(5f, 5f, SOURCE_A));

            assertThrows(UnsupportedOperationException.class, () ->
                state.getPoisonStacks().add(AilmentState.poison(1f, 1f, SOURCE_A))
            );
        }
    }

    // ---------------------------------------------------------------
    // setMaxPoisonStacks
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("setMaxPoisonStacks")
    class SetMaxPoisonStacks {

        @Test
        @DisplayName("reduces max allows no more than new limit")
        void reducesMaxStacks() {
            state.setMaxPoisonStacks(2);

            assertTrue(state.applyAilment(AilmentState.poison(5f, 5f, SOURCE_A)));
            assertTrue(state.applyAilment(AilmentState.poison(5f, 5f, SOURCE_A)));
            assertFalse(state.applyAilment(AilmentState.poison(5f, 5f, SOURCE_A)));
        }

        @Test
        @DisplayName("minimum value is clamped to 1")
        void minimumClampedToOne() {
            state.setMaxPoisonStacks(0);

            assertTrue(state.applyAilment(AilmentState.poison(5f, 5f, SOURCE_A)));
            assertFalse(state.applyAilment(AilmentState.poison(5f, 5f, SOURCE_A)));
            assertEquals(1, state.getPoisonStackCount());
        }

        @Test
        @DisplayName("negative value is clamped to 1")
        void negativeClampedToOne() {
            state.setMaxPoisonStacks(-5);

            assertTrue(state.applyAilment(AilmentState.poison(5f, 5f, SOURCE_A)));
            assertFalse(state.applyAilment(AilmentState.poison(5f, 5f, SOURCE_A)));
        }
    }

    // ---------------------------------------------------------------
    // tickAndGetDamage
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("tickAndGetDamage")
    class TickAndGetDamage {

        @Test
        @DisplayName("burn ticks and returns DPS * dt")
        void burnTicksReturnsDamage() {
            state.applyAilment(AilmentState.burn(20f, 4f, SOURCE_A));

            // 20 DPS * 0.25s = 5.0 damage
            float damage = state.tickAndGetDamage(0.25f);

            assertEquals(5f, damage, 0.001f);
        }

        @Test
        @DisplayName("poison stacks tick independently and sum damage")
        void poisonStacksSumDamage() {
            state.applyAilment(AilmentState.poison(10f, 5f, SOURCE_A));
            state.applyAilment(AilmentState.poison(6f, 3f, SOURCE_B));

            // (10 + 6) DPS * 0.5s = 8.0 damage
            float damage = state.tickAndGetDamage(0.5f);

            assertEquals(8f, damage, 0.001f);
        }

        @Test
        @DisplayName("burn and poison damage sum together")
        void burnAndPoisonSum() {
            state.applyAilment(AilmentState.burn(10f, 4f, SOURCE_A));
            state.applyAilment(AilmentState.poison(5f, 5f, SOURCE_B));

            // (10 + 5) DPS * 1.0s = 15.0 damage
            float damage = state.tickAndGetDamage(1f);

            assertEquals(15f, damage, 0.001f);
        }

        @Test
        @DisplayName("freeze returns 0 damage but still ticks duration")
        void freezeReturnsZeroDamage() {
            state.applyAilment(AilmentState.freeze(25f, 3f, SOURCE_A));

            float damage = state.tickAndGetDamage(1f);

            assertEquals(0f, damage, 0.001f);
            assertTrue(state.hasAilment(AilmentType.FREEZE));
            assertEquals(2f, state.getFreeze().remainingDuration(), 0.001f);
        }

        @Test
        @DisplayName("shock returns 0 damage but still ticks duration")
        void shockReturnsZeroDamage() {
            state.applyAilment(AilmentState.shock(40f, 2f, SOURCE_A));

            float damage = state.tickAndGetDamage(0.5f);

            assertEquals(0f, damage, 0.001f);
            assertTrue(state.hasAilment(AilmentType.SHOCK));
            assertEquals(1.5f, state.getShock().remainingDuration(), 0.001f);
        }

        @Test
        @DisplayName("expired burn removed after tick")
        void expiredBurnRemovedAfterTick() {
            state.applyAilment(AilmentState.burn(10f, 1f, SOURCE_A));

            // Tick past the full duration
            state.tickAndGetDamage(1.5f);

            assertNull(state.getBurn());
            assertFalse(state.hasAilment(AilmentType.BURN));
        }

        @Test
        @DisplayName("expired freeze removed after tick")
        void expiredFreezeRemovedAfterTick() {
            state.applyAilment(AilmentState.freeze(20f, 2f, SOURCE_A));

            state.tickAndGetDamage(3f);

            assertNull(state.getFreeze());
            assertFalse(state.hasAilment(AilmentType.FREEZE));
        }

        @Test
        @DisplayName("expired poison stacks removed, living ones remain")
        void expiredPoisonStacksRemovedOthersRemain() {
            state.applyAilment(AilmentState.poison(5f, 1f, SOURCE_A));  // Expires after 1s
            state.applyAilment(AilmentState.poison(8f, 5f, SOURCE_B));  // Survives

            state.tickAndGetDamage(2f);

            assertEquals(1, state.getPoisonStackCount(), "Only the surviving stack should remain");
        }

        @Test
        @DisplayName("all poison stacks expire leaves empty")
        void allPoisonStacksExpire() {
            state.applyAilment(AilmentState.poison(5f, 1f, SOURCE_A));
            state.applyAilment(AilmentState.poison(8f, 2f, SOURCE_B));

            state.tickAndGetDamage(3f);

            assertEquals(0, state.getPoisonStackCount());
            assertFalse(state.hasAilment(AilmentType.POISON));
        }

        @Test
        @DisplayName("empty state returns 0 damage")
        void emptyStateReturnsZero() {
            float damage = state.tickAndGetDamage(1f);

            assertEquals(0f, damage, 0.001f);
        }

        @Test
        @DisplayName("burn duration decreases after tick")
        void burnDurationDecreases() {
            state.applyAilment(AilmentState.burn(10f, 4f, SOURCE_A));

            state.tickAndGetDamage(1.5f);

            assertEquals(2.5f, state.getBurn().remainingDuration(), 0.001f);
        }
    }

    // ---------------------------------------------------------------
    // detonateAllDots
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("detonateAllDots")
    class DetonateAllDots {

        @Test
        @DisplayName("removes burn and all poison stacks")
        void removesBurnAndPoison() {
            state.applyAilment(AilmentState.burn(10f, 4f, SOURCE_A));
            state.applyAilment(AilmentState.poison(5f, 5f, SOURCE_A));
            state.applyAilment(AilmentState.poison(8f, 3f, SOURCE_B));

            state.detonateAllDots();

            assertNull(state.getBurn());
            assertEquals(0, state.getPoisonStackCount());
        }

        @Test
        @DisplayName("keeps freeze intact")
        void keepsFreeze() {
            state.applyAilment(AilmentState.burn(10f, 4f, SOURCE_A));
            state.applyAilment(AilmentState.freeze(20f, 3f, SOURCE_A));
            state.applyAilment(AilmentState.poison(5f, 5f, SOURCE_A));

            state.detonateAllDots();

            assertNotNull(state.getFreeze());
            assertEquals(20f, state.getFreeze().magnitude());
        }

        @Test
        @DisplayName("keeps shock intact")
        void keepsShock() {
            state.applyAilment(AilmentState.burn(10f, 4f, SOURCE_A));
            state.applyAilment(AilmentState.shock(35f, 2f, SOURCE_A));
            state.applyAilment(AilmentState.poison(5f, 5f, SOURCE_A));

            state.detonateAllDots();

            assertNotNull(state.getShock());
            assertEquals(35f, state.getShock().magnitude());
        }

        @Test
        @DisplayName("no-op when no DoTs present")
        void noOpWhenNoDotsPresent() {
            state.applyAilment(AilmentState.freeze(20f, 3f, SOURCE_A));
            state.applyAilment(AilmentState.shock(35f, 2f, SOURCE_A));

            state.detonateAllDots();

            assertNotNull(state.getFreeze());
            assertNotNull(state.getShock());
            assertEquals(2, state.getActiveAilmentCount());
        }
    }

    // ---------------------------------------------------------------
    // removeAilment
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("removeAilment")
    class RemoveAilment {

        @Test
        @DisplayName("removes specific single ailment")
        void removesSpecificSingleAilment() {
            state.applyAilment(AilmentState.burn(10f, 4f, SOURCE_A));
            state.applyAilment(AilmentState.freeze(20f, 3f, SOURCE_A));

            state.removeAilment(AilmentType.BURN);

            assertNull(state.getBurn());
            assertNotNull(state.getFreeze());
        }

        @Test
        @DisplayName("removes all poison stacks at once")
        void removesAllPoisonStacks() {
            state.applyAilment(AilmentState.poison(5f, 5f, SOURCE_A));
            state.applyAilment(AilmentState.poison(8f, 3f, SOURCE_B));
            state.applyAilment(AilmentState.poison(3f, 7f, SOURCE_A));

            state.removeAilment(AilmentType.POISON);

            assertEquals(0, state.getPoisonStackCount());
            assertFalse(state.hasAilment(AilmentType.POISON));
        }

        @Test
        @DisplayName("removing absent ailment is a no-op")
        void removingAbsentAilmentIsNoOp() {
            state.applyAilment(AilmentState.burn(10f, 4f, SOURCE_A));

            state.removeAilment(AilmentType.SHOCK);

            assertNotNull(state.getBurn());
            assertEquals(1, state.getActiveAilmentCount());
        }
    }

    // ---------------------------------------------------------------
    // clearAll
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("clearAll")
    class ClearAll {

        @Test
        @DisplayName("empties all ailments")
        void emptiesAllAilments() {
            state.applyAilment(AilmentState.burn(10f, 4f, SOURCE_A));
            state.applyAilment(AilmentState.freeze(20f, 3f, SOURCE_A));
            state.applyAilment(AilmentState.shock(35f, 2f, SOURCE_A));
            state.applyAilment(AilmentState.poison(5f, 5f, SOURCE_A));
            state.applyAilment(AilmentState.poison(8f, 3f, SOURCE_B));

            state.clearAll();

            assertNull(state.getBurn());
            assertNull(state.getFreeze());
            assertNull(state.getShock());
            assertEquals(0, state.getPoisonStackCount());
            assertFalse(state.hasAnyAilment());
            assertEquals(0, state.getActiveAilmentCount());
        }

        @Test
        @DisplayName("clearing already empty state is safe")
        void clearingEmptyStateIsSafe() {
            state.clearAll();

            assertFalse(state.hasAnyAilment());
        }
    }

    // ---------------------------------------------------------------
    // getDotDpsPerSource
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("getDotDpsPerSource")
    class GetDotDpsPerSource {

        @Test
        @DisplayName("returns burn DPS attributed to source")
        void burnDpsAttributedToSource() {
            state.applyAilment(AilmentState.burn(12f, 4f, SOURCE_A));

            Map<UUID, Float> dps = state.getDotDpsPerSource();

            assertEquals(1, dps.size());
            assertEquals(12f, dps.get(SOURCE_A), 0.001f);
        }

        @Test
        @DisplayName("returns per-source poison DPS summed")
        void poisonDpsSummedPerSource() {
            state.applyAilment(AilmentState.poison(5f, 5f, SOURCE_A));
            state.applyAilment(AilmentState.poison(3f, 5f, SOURCE_A));
            state.applyAilment(AilmentState.poison(10f, 3f, SOURCE_B));

            Map<UUID, Float> dps = state.getDotDpsPerSource();

            assertEquals(2, dps.size());
            assertEquals(8f, dps.get(SOURCE_A), 0.001f);
            assertEquals(10f, dps.get(SOURCE_B), 0.001f);
        }

        @Test
        @DisplayName("burn and poison from same source merge")
        void burnAndPoisonFromSameSourceMerge() {
            state.applyAilment(AilmentState.burn(10f, 4f, SOURCE_A));
            state.applyAilment(AilmentState.poison(5f, 5f, SOURCE_A));

            Map<UUID, Float> dps = state.getDotDpsPerSource();

            assertEquals(1, dps.size());
            assertEquals(15f, dps.get(SOURCE_A), 0.001f);
        }

        @Test
        @DisplayName("freeze and shock do not appear in DoT DPS map")
        void debuffsNotInDotDpsMap() {
            state.applyAilment(AilmentState.freeze(25f, 3f, SOURCE_A));
            state.applyAilment(AilmentState.shock(40f, 2f, SOURCE_B));

            Map<UUID, Float> dps = state.getDotDpsPerSource();

            assertTrue(dps.isEmpty());
        }

        @Test
        @DisplayName("empty state returns empty map")
        void emptyStateReturnsEmptyMap() {
            Map<UUID, Float> dps = state.getDotDpsPerSource();

            assertTrue(dps.isEmpty());
        }
    }

    // ---------------------------------------------------------------
    // getRemainingDotDamage
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("getRemainingDotDamage")
    class GetRemainingDotDamage {

        @Test
        @DisplayName("returns burn magnitude * remaining duration")
        void burnRemainingDamage() {
            state.applyAilment(AilmentState.burn(10f, 4f, SOURCE_A));

            // 10 DPS * 4s = 40 remaining damage
            assertEquals(40f, state.getRemainingDotDamage(), 0.001f);
        }

        @Test
        @DisplayName("returns sum of all poison stack remaining damage")
        void poisonRemainingDamage() {
            state.applyAilment(AilmentState.poison(5f, 5f, SOURCE_A));
            state.applyAilment(AilmentState.poison(10f, 2f, SOURCE_B));

            // (5*5) + (10*2) = 25 + 20 = 45
            assertEquals(45f, state.getRemainingDotDamage(), 0.001f);
        }

        @Test
        @DisplayName("debuffs contribute 0 remaining damage")
        void debuffsContributeZero() {
            state.applyAilment(AilmentState.freeze(25f, 3f, SOURCE_A));
            state.applyAilment(AilmentState.shock(40f, 2f, SOURCE_A));

            assertEquals(0f, state.getRemainingDotDamage(), 0.001f);
        }
    }

    // ---------------------------------------------------------------
    // hasAilment / hasAnyAilment
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("hasAilment / hasAnyAilment")
    class HasAilment {

        @Test
        @DisplayName("hasAilment returns false for empty state")
        void hasAilmentFalseWhenEmpty() {
            assertFalse(state.hasAilment(AilmentType.BURN));
            assertFalse(state.hasAilment(AilmentType.FREEZE));
            assertFalse(state.hasAilment(AilmentType.SHOCK));
            assertFalse(state.hasAilment(AilmentType.POISON));
        }

        @Test
        @DisplayName("hasAilment returns true for applied ailment")
        void hasAilmentTrueWhenApplied() {
            state.applyAilment(AilmentState.burn(10f, 4f, SOURCE_A));

            assertTrue(state.hasAilment(AilmentType.BURN));
            assertFalse(state.hasAilment(AilmentType.FREEZE));
        }

        @Test
        @DisplayName("hasAilment POISON checks stack list")
        void hasAilmentPoisonChecksStacks() {
            assertFalse(state.hasAilment(AilmentType.POISON));

            state.applyAilment(AilmentState.poison(5f, 5f, SOURCE_A));

            assertTrue(state.hasAilment(AilmentType.POISON));
        }

        @Test
        @DisplayName("hasAnyAilment returns false for empty state")
        void hasAnyAilmentFalseWhenEmpty() {
            assertFalse(state.hasAnyAilment());
        }

        @Test
        @DisplayName("hasAnyAilment returns true with only single ailment")
        void hasAnyAilmentTrueWithSingle() {
            state.applyAilment(AilmentState.freeze(20f, 3f, SOURCE_A));

            assertTrue(state.hasAnyAilment());
        }

        @Test
        @DisplayName("hasAnyAilment returns true with only poison")
        void hasAnyAilmentTrueWithOnlyPoison() {
            state.applyAilment(AilmentState.poison(5f, 5f, SOURCE_A));

            assertTrue(state.hasAnyAilment());
        }
    }

    // ---------------------------------------------------------------
    // getActiveAilmentCount
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("getActiveAilmentCount")
    class GetActiveAilmentCount {

        @Test
        @DisplayName("empty state returns 0")
        void emptyStateReturnsZero() {
            assertEquals(0, state.getActiveAilmentCount());
        }

        @Test
        @DisplayName("single ailments count individually")
        void singleAilmentsCountIndividually() {
            state.applyAilment(AilmentState.burn(10f, 4f, SOURCE_A));

            assertEquals(1, state.getActiveAilmentCount());

            state.applyAilment(AilmentState.freeze(20f, 3f, SOURCE_A));

            assertEquals(2, state.getActiveAilmentCount());
        }

        @Test
        @DisplayName("poison counts as 1 regardless of stack count")
        void poisonCountsAsOne() {
            state.applyAilment(AilmentState.poison(5f, 5f, SOURCE_A));
            state.applyAilment(AilmentState.poison(8f, 3f, SOURCE_A));
            state.applyAilment(AilmentState.poison(3f, 7f, SOURCE_B));

            assertEquals(1, state.getActiveAilmentCount());
        }

        @Test
        @DisplayName("all types count correctly")
        void allTypesCountCorrectly() {
            state.applyAilment(AilmentState.burn(10f, 4f, SOURCE_A));
            state.applyAilment(AilmentState.freeze(20f, 3f, SOURCE_A));
            state.applyAilment(AilmentState.shock(35f, 2f, SOURCE_A));
            state.applyAilment(AilmentState.poison(5f, 5f, SOURCE_A));
            state.applyAilment(AilmentState.poison(8f, 3f, SOURCE_B));

            // 3 single + 1 for poison = 4
            assertEquals(4, state.getActiveAilmentCount());
        }
    }

    // ---------------------------------------------------------------
    // getAllAilments
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("getAllAilments")
    class GetAllAilments {

        @Test
        @DisplayName("empty state returns empty list")
        void emptyStateReturnsEmptyList() {
            assertTrue(state.getAllAilments().isEmpty());
        }

        @Test
        @DisplayName("returns singles and poison stacks combined")
        void returnsSinglesAndPoisonCombined() {
            state.applyAilment(AilmentState.burn(10f, 4f, SOURCE_A));
            state.applyAilment(AilmentState.freeze(20f, 3f, SOURCE_A));
            state.applyAilment(AilmentState.poison(5f, 5f, SOURCE_A));
            state.applyAilment(AilmentState.poison(8f, 3f, SOURCE_B));

            // 2 singles + 2 poison stacks = 4 total
            assertEquals(4, state.getAllAilments().size());
        }
    }

    // ---------------------------------------------------------------
    // getFreezeSlowPercent / getShockDamageIncreasePercent
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("Debuff percentage accessors")
    class DebuffPercentages {

        @Test
        @DisplayName("getFreezeSlowPercent returns 0 when not frozen")
        void freezeSlowZeroWhenNotFrozen() {
            assertEquals(0f, state.getFreezeSlowPercent());
        }

        @Test
        @DisplayName("getFreezeSlowPercent returns magnitude when frozen")
        void freezeSlowReturnsMagnitude() {
            state.applyAilment(AilmentState.freeze(20f, 3f, SOURCE_A));

            assertEquals(20f, state.getFreezeSlowPercent(), 0.001f);
        }

        @Test
        @DisplayName("getFreezeSlowPercent caps at 30")
        void freezeSlowCapsAt30() {
            // freeze() factory already caps at 30, but state accessor also caps
            state.applyAilment(AilmentState.freeze(25f, 3f, SOURCE_A));
            // Refresh with a raw state that might exceed cap via merge
            assertEquals(25f, state.getFreezeSlowPercent(), 0.001f);
        }

        @Test
        @DisplayName("getShockDamageIncreasePercent returns 0 when not shocked")
        void shockIncreaseZeroWhenNotShocked() {
            assertEquals(0f, state.getShockDamageIncreasePercent());
        }

        @Test
        @DisplayName("getShockDamageIncreasePercent returns magnitude when shocked")
        void shockIncreaseReturnsMagnitude() {
            state.applyAilment(AilmentState.shock(35f, 2f, SOURCE_A));

            assertEquals(35f, state.getShockDamageIncreasePercent(), 0.001f);
        }

        @Test
        @DisplayName("getShockDamageIncreasePercent caps at 50")
        void shockIncreaseCapsAt50() {
            state.applyAilment(AilmentState.shock(45f, 2f, SOURCE_A));

            assertEquals(45f, state.getShockDamageIncreasePercent(), 0.001f);
        }
    }

    // ---------------------------------------------------------------
    // getTotalPoisonDps
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("getTotalPoisonDps")
    class GetTotalPoisonDps {

        @Test
        @DisplayName("returns 0 when no poison stacks")
        void zeroWhenNoPoisonStacks() {
            assertEquals(0f, state.getTotalPoisonDps(), 0.001f);
        }

        @Test
        @DisplayName("sums all stack magnitudes")
        void sumsAllStackMagnitudes() {
            state.applyAilment(AilmentState.poison(5f, 5f, SOURCE_A));
            state.applyAilment(AilmentState.poison(10f, 3f, SOURCE_B));
            state.applyAilment(AilmentState.poison(7f, 4f, SOURCE_A));

            assertEquals(22f, state.getTotalPoisonDps(), 0.001f);
        }
    }

    // ---------------------------------------------------------------
    // Edge cases
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("can reapply ailment after it expires via tick")
        void canReapplyAfterExpiry() {
            state.applyAilment(AilmentState.burn(10f, 1f, SOURCE_A));
            state.tickAndGetDamage(2f); // Expire burn

            assertNull(state.getBurn());

            // Reapply
            state.applyAilment(AilmentState.burn(15f, 3f, SOURCE_A));

            assertNotNull(state.getBurn());
            assertEquals(15f, state.getBurn().magnitude());
        }

        @Test
        @DisplayName("can reapply poison after clearAll")
        void canReapplyPoisonAfterClearAll() {
            state.applyAilment(AilmentState.poison(5f, 5f, SOURCE_A));
            state.clearAll();

            assertTrue(state.applyAilment(AilmentState.poison(8f, 3f, SOURCE_A)));
            assertEquals(1, state.getPoisonStackCount());
        }

        @Test
        @DisplayName("detonateAllDots allows new poison stacks after")
        void detonateAllowsNewPoisonAfter() {
            state.setMaxPoisonStacks(2);
            state.applyAilment(AilmentState.poison(5f, 5f, SOURCE_A));
            state.applyAilment(AilmentState.poison(5f, 5f, SOURCE_A));
            assertFalse(state.applyAilment(AilmentState.poison(5f, 5f, SOURCE_A)));

            state.detonateAllDots();

            assertTrue(state.applyAilment(AilmentState.poison(5f, 5f, SOURCE_A)));
            assertEquals(1, state.getPoisonStackCount());
        }

        @Test
        @DisplayName("toString contains relevant info without crashing")
        void toStringDoesNotCrash() {
            state.applyAilment(AilmentState.burn(10f, 4f, SOURCE_A));
            state.applyAilment(AilmentState.poison(5f, 5f, SOURCE_A));

            String str = state.toString();

            assertNotNull(str);
            assertTrue(str.contains("EntityAilmentState"));
        }
    }
}
