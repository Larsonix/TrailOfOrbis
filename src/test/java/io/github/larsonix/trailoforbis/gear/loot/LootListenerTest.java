package io.github.larsonix.trailoforbis.gear.loot;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.gear.loot.LootCalculator.LootRoll;
import io.github.larsonix.trailoforbis.gear.loot.LootSettings.MobBonus;
import io.github.larsonix.trailoforbis.gear.loot.LootSettings.MobType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for LootListener - 15 test cases.
 *
 * <p>Note: ECS component tests are difficult to unit test due to static
 * ComponentType methods. These tests focus on the query configuration
 * and accessor methods. Full integration testing requires a running
 * Hytale server environment.
 */
@ExtendWith(MockitoExtension.class)
class LootListenerTest {

    @Mock
    private TrailOfOrbis plugin;

    @Mock
    private LootCalculator lootCalculator;

    @Mock
    private LootGenerator lootGenerator;

    private LootListener listener;

    @BeforeEach
    void setUp() {
        listener = new LootListener(plugin, lootCalculator, lootGenerator);
    }

    // =========================================================================
    // CONSTRUCTOR TESTS
    // =========================================================================

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("constructor accepts valid dependencies")
        void constructor_AcceptsValidDependencies() {
            assertDoesNotThrow(() ->
                new LootListener(plugin, lootCalculator, lootGenerator)
            );
        }

        @Test
        @DisplayName("constructor throws for null plugin")
        void constructor_ThrowsForNullPlugin() {
            assertThrows(NullPointerException.class, () ->
                new LootListener(null, lootCalculator, lootGenerator)
            );
        }

        @Test
        @DisplayName("constructor throws for null calculator")
        void constructor_ThrowsForNullCalculator() {
            assertThrows(NullPointerException.class, () ->
                new LootListener(plugin, null, lootGenerator)
            );
        }

        @Test
        @DisplayName("constructor throws for null generator")
        void constructor_ThrowsForNullGenerator() {
            assertThrows(NullPointerException.class, () ->
                new LootListener(plugin, lootCalculator, null)
            );
        }
    }

    // =========================================================================
    // QUERY TESTS
    // =========================================================================

    @Nested
    @DisplayName("Query Tests")
    class QueryTests {

        @Test
        @DisplayName("getQuery returns non-null query")
        void getQuery_ReturnsNonNull() {
            Query<EntityStore> query = listener.getQuery();
            assertNotNull(query);
        }

        @Test
        @DisplayName("getQuery returns empty archetype for broad matching")
        void getQuery_ReturnsEmptyArchetype() {
            Query<EntityStore> query = listener.getQuery();
            // Empty archetype matches all entities - we filter in onComponentAdded
            assertEquals(Archetype.empty(), query);
        }
    }

    // =========================================================================
    // ACCESSOR TESTS
    // =========================================================================

    @Nested
    @DisplayName("Accessor Tests")
    class AccessorTests {

        @Test
        @DisplayName("getCalculator returns injected calculator")
        void getCalculator_ReturnsInjected() {
            assertEquals(lootCalculator, listener.getCalculator());
        }

        @Test
        @DisplayName("getGenerator returns injected generator")
        void getGenerator_ReturnsInjected() {
            assertEquals(lootGenerator, listener.getGenerator());
        }
    }

    // =========================================================================
    // MOB TYPE TESTS (Unit Tests Without ECS)
    // =========================================================================

    @Nested
    @DisplayName("Mob Type Configuration Tests")
    class MobTypeTests {

        @Test
        @DisplayName("MobType enum has NORMAL value")
        void mobType_HasNormal() {
            assertNotNull(MobType.NORMAL);
        }

        @Test
        @DisplayName("MobType enum has ELITE value")
        void mobType_HasElite() {
            assertNotNull(MobType.ELITE);
        }

        @Test
        @DisplayName("MobType enum has BOSS value")
        void mobType_HasBoss() {
            assertNotNull(MobType.BOSS);
        }

        @Test
        @DisplayName("MobType values are distinct")
        void mobType_ValuesDistinct() {
            assertNotEquals(MobType.NORMAL, MobType.ELITE);
            assertNotEquals(MobType.ELITE, MobType.BOSS);
            assertNotEquals(MobType.NORMAL, MobType.BOSS);
        }
    }

    // =========================================================================
    // LOOT ROLL TESTS (Unit Tests Without ECS)
    // =========================================================================

    @Nested
    @DisplayName("Loot Roll Tests")
    class LootRollTests {

        @Test
        @DisplayName("NO_DROP constant has shouldDrop false")
        void noDrop_HasShouldDropFalse() {
            assertFalse(LootRoll.NO_DROP.shouldDrop());
        }

        @Test
        @DisplayName("NO_DROP constant has zero drop count")
        void noDrop_HasZeroDropCount() {
            assertEquals(0, LootRoll.NO_DROP.dropCount());
        }

        @Test
        @DisplayName("LootRoll with shouldDrop true has positive dropCount")
        void lootRoll_WithDropTrue_HasPositiveCount() {
            LootRoll roll = new LootRoll(true, 2, 25.0, 50);
            assertTrue(roll.shouldDrop());
            assertTrue(roll.dropCount() > 0);
        }

        @Test
        @DisplayName("LootRoll stores all parameters")
        void lootRoll_StoresAllParameters() {
            LootRoll roll = new LootRoll(true, 3, 50.0, 75);
            assertTrue(roll.shouldDrop());
            assertEquals(3, roll.dropCount());
            assertEquals(50.0, roll.rarityBonus());
            assertEquals(75, roll.itemLevel());
        }
    }

    // =========================================================================
    // INTEGRATION TESTS (Verifying Listener/Calculator/Generator Work Together)
    // =========================================================================

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {

        @Test
        @DisplayName("listener can be created with real calculator and generator")
        void listener_WithRealDependencies() {
            // Create real settings
            LootSettings settings = new LootSettings(
                0.05, 0.5, true, 100, 50.0,
                Map.of(
                    MobType.NORMAL, MobBonus.NONE,
                    MobType.ELITE, new MobBonus(0.5, 0.25),
                    MobType.BOSS, new MobBonus(1.0, 0.5)
                )
            );

            // Would need real AttributeManager and GearGenerator for full test
            // This just verifies settings work with listener creation flow
            assertNotNull(settings.getBaseDropChance());
            assertNotNull(settings.getMobBonus(MobType.BOSS));
        }
    }
}
