package io.github.larsonix.trailoforbis.combat.blocking;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.damage.DamageDataComponent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.WieldingInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link BlockingProcessor}.
 */
@ExtendWith(MockitoExtension.class)
class BlockingProcessorTest {

    @Mock
    private Store<EntityStore> store;

    @Mock
    private Ref<EntityStore> defenderRef;

    @Mock
    private DamageDataComponent damageDataComponent;

    @Mock
    private WieldingInteraction wieldingInteraction;

    @Mock
    private ComponentType<EntityStore, DamageDataComponent> componentType;

    private MockedStatic<DamageDataComponent> mockedDamageDataComponent;

    private BlockingProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new BlockingProcessor();

        // Mock the static getComponentType() method
        mockedDamageDataComponent = mockStatic(DamageDataComponent.class);
        mockedDamageDataComponent.when(DamageDataComponent::getComponentType).thenReturn(componentType);
    }

    @AfterEach
    void tearDown() {
        if (mockedDamageDataComponent != null) {
            mockedDamageDataComponent.close();
        }
    }

    @Test
    @DisplayName("Returns empty when DamageDataComponent is null")
    void testBlockFailure_whenNoDamageDataComponent() {
        when(store.getComponent(defenderRef, componentType)).thenReturn(null);

        ComputedStats stats = createBlockingStats(100f, 50f, 0f);

        Optional<BlockResult> result = processor.checkActiveBlock(store, defenderRef, stats, 100f);

        assertTrue(result.isEmpty(), "Should return empty when DamageDataComponent is null");
    }

    @Test
    @DisplayName("Returns empty when not blocking (no wielding)")
    void testBlockFailure_whenNotBlocking() {
        when(store.getComponent(defenderRef, componentType)).thenReturn(damageDataComponent);
        when(damageDataComponent.getCurrentWielding()).thenReturn(null);

        ComputedStats stats = createBlockingStats(100f, 50f, 0f);

        Optional<BlockResult> result = processor.checkActiveBlock(store, defenderRef, stats, 100f);

        assertTrue(result.isEmpty(), "Should return empty when not actively blocking");
    }

    @Test
    @DisplayName("Returns empty when no defender stats (vanilla handling)")
    void testBlockFailure_whenNoDefenderStats() {
        when(store.getComponent(defenderRef, componentType)).thenReturn(damageDataComponent);
        when(damageDataComponent.getCurrentWielding()).thenReturn(wieldingInteraction);

        Optional<BlockResult> result = processor.checkActiveBlock(store, defenderRef, null, 100f);

        assertTrue(result.isEmpty(), "Should return empty for vanilla handling when no stats");
    }

    @Test
    @DisplayName("Block success reduces damage by efficiency stat")
    void testBlockSuccess_reducesDamageByEfficiency() {
        when(store.getComponent(defenderRef, componentType)).thenReturn(damageDataComponent);
        when(damageDataComponent.getCurrentWielding()).thenReturn(wieldingInteraction);
        when(wieldingInteraction.getStaminaCost()).thenReturn(null);

        // 100% block chance, 70% damage reduction
        ComputedStats stats = createBlockingStats(100f, 70f, 0f);

        Optional<BlockResult> result = processor.checkActiveBlock(store, defenderRef, stats, 100f);

        assertTrue(result.isPresent(), "Should return a result when blocking");
        BlockResult blockResult = result.get();
        assertTrue(blockResult.blocked(), "Block should succeed");
        assertEquals(0.7f, blockResult.damageReduction(), 0.001f, "Damage reduction should be 70%");
        assertFalse(blockResult.fullBlock(), "Should not be a full block at 70% reduction");
    }

    @Test
    @DisplayName("Full block (100% reduction) sets fullBlock flag")
    void testBlockSuccess_fullBlock() {
        when(store.getComponent(defenderRef, componentType)).thenReturn(damageDataComponent);
        when(damageDataComponent.getCurrentWielding()).thenReturn(wieldingInteraction);
        when(wieldingInteraction.getStaminaCost()).thenReturn(null);

        // 100% block chance, 100% damage reduction
        ComputedStats stats = createBlockingStats(100f, 100f, 0f);

        Optional<BlockResult> result = processor.checkActiveBlock(store, defenderRef, stats, 100f);

        assertTrue(result.isPresent());
        BlockResult blockResult = result.get();
        assertTrue(blockResult.blocked());
        assertEquals(1.0f, blockResult.damageReduction(), 0.001f);
        assertTrue(blockResult.fullBlock(), "Should be a full block at 100% reduction");
    }

    @Test
    @DisplayName("Stamina drain reduction reduces stamina cost")
    void testStaminaDrainReduction_reducesStaminaCost() {
        when(store.getComponent(defenderRef, componentType)).thenReturn(damageDataComponent);
        when(damageDataComponent.getCurrentWielding()).thenReturn(wieldingInteraction);
        when(wieldingInteraction.getStaminaCost()).thenReturn(null);

        // 100% block chance, 50% damage reduction, 50% stamina reduction
        ComputedStats stats = createBlockingStats(100f, 50f, 50f);

        Optional<BlockResult> result = processor.checkActiveBlock(store, defenderRef, stats, 100f);

        assertTrue(result.isPresent());
        BlockResult blockResult = result.get();
        assertTrue(blockResult.blocked());

        // Base stamina cost = 100 * 0.1 = 10
        // With 50% reduction = 10 * 0.5 = 5
        assertEquals(5f, blockResult.staminaCost(), 0.001f, "Stamina cost should be reduced by 50%");
    }

    @Test
    @DisplayName("Stamina drain reduction capped at 75%")
    void testStaminaDrainReduction_cappedAt75Percent() {
        when(store.getComponent(defenderRef, componentType)).thenReturn(damageDataComponent);
        when(damageDataComponent.getCurrentWielding()).thenReturn(wieldingInteraction);
        when(wieldingInteraction.getStaminaCost()).thenReturn(null);

        // 100% block chance, 50% damage reduction, 100% stamina reduction (should cap at 75%)
        ComputedStats stats = createBlockingStats(100f, 50f, 100f);

        Optional<BlockResult> result = processor.checkActiveBlock(store, defenderRef, stats, 100f);

        assertTrue(result.isPresent());
        BlockResult blockResult = result.get();

        // Base stamina cost = 100 * 0.1 = 10
        // With 75% reduction (capped) = 10 * 0.25 = 2.5
        assertEquals(2.5f, blockResult.staminaCost(), 0.001f, "Stamina reduction should be capped at 75%");
    }

    @Test
    @DisplayName("Block chance roll at 0% always returns FAILED_ROLL")
    void testBlockChance_rollsCorrectlyAt0Percent() {
        when(store.getComponent(defenderRef, componentType)).thenReturn(damageDataComponent);
        when(damageDataComponent.getCurrentWielding()).thenReturn(wieldingInteraction);

        // 0% block chance
        ComputedStats stats = createBlockingStats(0f, 70f, 0f);

        // Run multiple times to ensure consistency
        for (int i = 0; i < 10; i++) {
            Optional<BlockResult> result = processor.checkActiveBlock(store, defenderRef, stats, 100f);

            assertTrue(result.isPresent(), "Should return a result even on failed roll");
            BlockResult blockResult = result.get();
            assertFalse(blockResult.blocked(), "Block should always fail at 0% chance");
            assertSame(BlockResult.FAILED_ROLL, blockResult, "Should return FAILED_ROLL constant");
        }
    }

    @Test
    @DisplayName("Block chance roll at 100% always succeeds")
    void testBlockChance_rollsCorrectlyAt100Percent() {
        when(store.getComponent(defenderRef, componentType)).thenReturn(damageDataComponent);
        when(damageDataComponent.getCurrentWielding()).thenReturn(wieldingInteraction);
        when(wieldingInteraction.getStaminaCost()).thenReturn(null);

        // 100% block chance
        ComputedStats stats = createBlockingStats(100f, 70f, 0f);

        // Run multiple times to ensure consistency
        for (int i = 0; i < 10; i++) {
            Optional<BlockResult> result = processor.checkActiveBlock(store, defenderRef, stats, 100f);

            assertTrue(result.isPresent());
            BlockResult blockResult = result.get();
            assertTrue(blockResult.blocked(), "Block should always succeed at 100% chance");
        }
    }

    /**
     * Helper to create ComputedStats with blocking stats.
     */
    private ComputedStats createBlockingStats(float blockChance, float damageReduction, float staminaDrainReduction) {
        return ComputedStats.builder()
            .blockChance(blockChance)
            .blockDamageReduction(damageReduction)
            .staminaDrainReduction(staminaDrainReduction)
            .build();
    }
}
