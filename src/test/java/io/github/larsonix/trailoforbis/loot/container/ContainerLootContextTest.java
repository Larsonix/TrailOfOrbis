package io.github.larsonix.trailoforbis.loot.container;

import io.github.larsonix.trailoforbis.gear.loot.RealmLootContext;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ContainerLootContext} — the record that carries zone-level
 * context and realm bonuses through the container loot pipeline.
 */
class ContainerLootContextTest {

    // =========================================================================
    // OVERWORLD FACTORY
    // =========================================================================

    @Nested
    @DisplayName("Overworld Context Factory")
    class OverworldFactory {

        @Test
        @DisplayName("Creates context with no realm bonuses")
        void overworld_noRealmBonuses() {
            var context = ContainerLootContext.overworld(25, 30);
            assertEquals(0.0, context.realmLootContext().itemQuantityBonus());
            assertEquals(0.0, context.realmLootContext().itemRarityBonus());
        }

        @Test
        @DisplayName("realmLootContext is NONE constant")
        void overworld_realmLootContextIsNONE() {
            var context = ContainerLootContext.overworld(10, 20);
            assertSame(RealmLootContext.NONE, context.realmLootContext(),
                "Overworld context should use RealmLootContext.NONE singleton");
        }

        @Test
        @DisplayName("qualityBonus is zero")
        void overworld_qualityBonusIsZero() {
            var context = ContainerLootContext.overworld(10, 20);
            assertEquals(0, context.qualityBonus());
        }

        @Test
        @DisplayName("Preserves source and player levels")
        void overworld_preservesLevels() {
            var context = ContainerLootContext.overworld(15, 42);
            assertEquals(15, context.sourceLevel());
            assertEquals(42, context.playerLevel());
        }
    }

    // =========================================================================
    // REALM FACTORY
    // =========================================================================

    @Nested
    @DisplayName("Realm Context Factory")
    class RealmFactory {

        @Test
        @DisplayName("Preserves all modifier values")
        void realm_preservesAllModifiers() {
            var realmCtx = new RealmLootContext(25.0, 30.0);
            var context = ContainerLootContext.realm(50, 45, realmCtx, 10);

            assertEquals(50, context.sourceLevel());
            assertEquals(45, context.playerLevel());
            assertEquals(25.0, context.realmLootContext().itemQuantityBonus());
            assertEquals(30.0, context.realmLootContext().itemRarityBonus());
            assertEquals(10, context.qualityBonus());
        }

        @Test
        @DisplayName("realmLootContext has IIQ and IIR")
        void realm_realmLootContextHasIIQAndIIR() {
            var realmCtx = new RealmLootContext(15.0, 20.0);
            var context = ContainerLootContext.realm(30, 25, realmCtx, 5);

            assertTrue(context.realmLootContext().hasBonus(),
                "Realm context with positive IIQ+IIR should report hasBonus()=true");
        }

        @Test
        @DisplayName("Quality bonus preserved")
        void realm_qualityBonusPreserved() {
            var context = ContainerLootContext.realm(10, 10, RealmLootContext.NONE, 15);
            assertEquals(15, context.qualityBonus());
        }

        @Test
        @DisplayName("Zero bonuses are valid")
        void realm_zeroBonusesAreValid() {
            var context = ContainerLootContext.realm(10, 10, RealmLootContext.NONE, 0);
            assertEquals(0, context.qualityBonus());
            assertFalse(context.realmLootContext().hasBonus());
        }
    }

    // =========================================================================
    // RECORD BEHAVIOR
    // =========================================================================

    @Nested
    @DisplayName("Record Behavior")
    class RecordBehavior {

        @Test
        @DisplayName("Equality based on all fields")
        void equality_allFields() {
            var realmCtx = new RealmLootContext(10.0, 20.0);
            var ctx1 = ContainerLootContext.realm(50, 40, realmCtx, 5);
            var ctx2 = ContainerLootContext.realm(50, 40, realmCtx, 5);

            assertEquals(ctx1, ctx2, "Same field values should be equal");
            assertEquals(ctx1.hashCode(), ctx2.hashCode());
        }

        @Test
        @DisplayName("Different source levels are not equal")
        void inequality_differentSourceLevels() {
            var ctx1 = ContainerLootContext.overworld(10, 20);
            var ctx2 = ContainerLootContext.overworld(11, 20);
            assertNotEquals(ctx1, ctx2);
        }

        @Test
        @DisplayName("toString contains key fields")
        void toString_containsKeyFields() {
            var context = ContainerLootContext.realm(50, 40,
                new RealmLootContext(10.0, 20.0), 5);
            String str = context.toString();
            assertTrue(str.contains("50"), "toString should contain source level");
            assertTrue(str.contains("40"), "toString should contain player level");
            assertTrue(str.contains("5"), "toString should contain quality bonus");
        }
    }
}
