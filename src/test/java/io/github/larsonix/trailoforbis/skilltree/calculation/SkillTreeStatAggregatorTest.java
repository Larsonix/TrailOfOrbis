package io.github.larsonix.trailoforbis.skilltree.calculation;

import io.github.larsonix.trailoforbis.skilltree.config.SkillNode;
import io.github.larsonix.trailoforbis.skilltree.config.SkillTreeConfig;
import io.github.larsonix.trailoforbis.skilltree.model.ModifierType;
import io.github.larsonix.trailoforbis.skilltree.model.SkillTreeData;
import io.github.larsonix.trailoforbis.skilltree.model.StatModifier;
import io.github.larsonix.trailoforbis.skilltree.model.StatType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for SkillTreeStatAggregator - aggregates modifiers from allocated skill nodes.
 */
@ExtendWith(MockitoExtension.class)
class SkillTreeStatAggregatorTest {

    @Mock
    private SkillTreeConfig config;

    private SkillTreeStatAggregator aggregator;

    // ═══════════════════════════════════════════════════════════════════
    // SETUP
    // ═══════════════════════════════════════════════════════════════════

    @BeforeEach
    void setUp() {
        aggregator = new SkillTreeStatAggregator(config);
    }

    @AfterEach
    void tearDown() {
        aggregator = null;
    }

    // ═══════════════════════════════════════════════════════════════════
    // CONSTRUCTOR TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("Should accept valid config")
        void shouldAcceptValidConfig() {
            assertDoesNotThrow(() -> new SkillTreeStatAggregator(config));
        }

        @Test
        @DisplayName("Should reject null config")
        void shouldRejectNullConfig() {
            assertThrows(NullPointerException.class, () ->
                new SkillTreeStatAggregator(null));
        }

        @Test
        @DisplayName("Should store config reference")
        void shouldStoreConfigReference() {
            assertSame(config, aggregator.getConfig());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // AGGREGATION TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("aggregate")
    class AggregateTests {

        @Test
        @DisplayName("Should return empty modifiers for player with no allocated nodes")
        void shouldReturnEmptyForNoAllocatedNodes() {
            SkillTreeData data = SkillTreeData.builder()
                .uuid(UUID.randomUUID())
                .skillPoints(0)
                .build();

            AggregatedModifiers result = aggregator.aggregate(data);

            assertFalse(result.hasModifiers(StatType.PHYSICAL_DAMAGE));
            assertEquals(0, result.getFlatSum(StatType.PHYSICAL_DAMAGE), 0.001f);
        }

        @Test
        @DisplayName("Should aggregate modifiers from allocated nodes")
        void shouldAggregateFromAllocatedNodes() {
            // Create nodes with modifiers
            SkillNode node1 = createNodeWithModifiers("node1",
                new StatModifier(StatType.PHYSICAL_DAMAGE, 5, ModifierType.FLAT),
                new StatModifier(StatType.CRITICAL_CHANCE, 1, ModifierType.FLAT));

            SkillNode node2 = createNodeWithModifiers("node2",
                new StatModifier(StatType.PHYSICAL_DAMAGE, 3, ModifierType.FLAT),
                new StatModifier(StatType.MAX_HEALTH, 10, ModifierType.FLAT));

            when(config.getNode("node1")).thenReturn(node1);
            when(config.getNode("node2")).thenReturn(node2);

            SkillTreeData data = SkillTreeData.builder()
                .uuid(UUID.randomUUID())
                .allocatedNodes(new HashSet<>(Arrays.asList("node1", "node2")))
                .skillPoints(0)
                .build();

            AggregatedModifiers result = aggregator.aggregate(data);

            // Verify PHYSICAL_DAMAGE has both modifiers: 5 + 3 = 8
            assertEquals(8, result.getFlatSum(StatType.PHYSICAL_DAMAGE), 0.001f);

            // Verify CRITICAL_CHANCE has one modifier: 1
            assertEquals(1, result.getFlatSum(StatType.CRITICAL_CHANCE), 0.001f);

            // Verify MAX_HEALTH has one modifier: 10
            assertEquals(10, result.getFlatSum(StatType.MAX_HEALTH), 0.001f);
        }

        @Test
        @DisplayName("Should sum multiple flat modifiers of same type")
        void shouldSumMultipleFlatModifiers() {
            SkillNode node1 = createNodeWithModifiers("node1",
                new StatModifier(StatType.PHYSICAL_DAMAGE, 5, ModifierType.FLAT));

            SkillNode node2 = createNodeWithModifiers("node2",
                new StatModifier(StatType.PHYSICAL_DAMAGE, 3, ModifierType.FLAT));

            SkillNode node3 = createNodeWithModifiers("node3",
                new StatModifier(StatType.PHYSICAL_DAMAGE, 7, ModifierType.FLAT));

            when(config.getNode("node1")).thenReturn(node1);
            when(config.getNode("node2")).thenReturn(node2);
            when(config.getNode("node3")).thenReturn(node3);

            SkillTreeData data = SkillTreeData.builder()
                .uuid(UUID.randomUUID())
                .allocatedNodes(new HashSet<>(Arrays.asList("node1", "node2", "node3")))
                .skillPoints(0)
                .build();

            AggregatedModifiers result = aggregator.aggregate(data);

            // 5 + 3 + 7 = 15
            assertEquals(15, result.getFlatSum(StatType.PHYSICAL_DAMAGE), 0.001f);
        }

        @Test
        @DisplayName("Should sum percentage modifiers additively")
        void shouldSumPercentageModifiers() {
            SkillNode node1 = createNodeWithModifiers("node1",
                new StatModifier(StatType.PHYSICAL_DAMAGE_PERCENT, 10, ModifierType.PERCENT));

            SkillNode node2 = createNodeWithModifiers("node2",
                new StatModifier(StatType.PHYSICAL_DAMAGE_PERCENT, 5, ModifierType.PERCENT));

            when(config.getNode("node1")).thenReturn(node1);
            when(config.getNode("node2")).thenReturn(node2);

            SkillTreeData data = SkillTreeData.builder()
                .uuid(UUID.randomUUID())
                .allocatedNodes(new HashSet<>(Arrays.asList("node1", "node2")))
                .skillPoints(0)
                .build();

            AggregatedModifiers result = aggregator.aggregate(data);

            // 10 + 5 = 15 (additive)
            assertEquals(15, result.getPercentSum(StatType.PHYSICAL_DAMAGE_PERCENT), 0.001f);
        }

        @Test
        @DisplayName("Should collect multipliers as separate list")
        void shouldCollectMultipliersSeparately() {
            SkillNode node1 = createNodeWithModifiers("node1",
                new StatModifier(StatType.PHYSICAL_DAMAGE, 20, ModifierType.MULTIPLIER));

            SkillNode node2 = createNodeWithModifiers("node2",
                new StatModifier(StatType.PHYSICAL_DAMAGE, 50, ModifierType.MULTIPLIER));

            when(config.getNode("node1")).thenReturn(node1);
            when(config.getNode("node2")).thenReturn(node2);

            SkillTreeData data = SkillTreeData.builder()
                .uuid(UUID.randomUUID())
                .allocatedNodes(new HashSet<>(Arrays.asList("node1", "node2")))
                .skillPoints(0)
                .build();

            AggregatedModifiers result = aggregator.aggregate(data);

            List<Float> multipliers = result.getMultipliers(StatType.PHYSICAL_DAMAGE);
            assertEquals(2, multipliers.size());
            assertTrue(multipliers.contains(20f));
            assertTrue(multipliers.contains(50f));
        }

        @Test
        @DisplayName("Should return empty list for stat with no multipliers")
        void shouldReturnEmptyListForNoMultipliers() {
            SkillTreeData data = SkillTreeData.builder()
                .uuid(UUID.randomUUID())
                .allocatedNodes(new HashSet<>())
                .skillPoints(0)
                .build();

            AggregatedModifiers result = aggregator.aggregate(data);

            assertTrue(result.getMultipliers(StatType.PHYSICAL_DAMAGE).isEmpty());
        }

        @Test
        @DisplayName("Should handle missing node gracefully")
        void shouldHandleMissingNode() {
            when(config.getNode("missing")).thenReturn(null);

            SkillTreeData data = SkillTreeData.builder()
                .uuid(UUID.randomUUID())
                .allocatedNodes(new HashSet<>(Collections.singletonList("missing")))
                .skillPoints(0)
                .build();

            assertDoesNotThrow(() -> aggregator.aggregate(data));
        }

        @Test
        @DisplayName("Should handle node with no modifiers")
        void shouldHandleNodeWithNoModifiers() {
            SkillNode node = new SkillNode();
            node.setId("emptyNode");
            when(config.getNode("emptyNode")).thenReturn(node);

            SkillTreeData data = SkillTreeData.builder()
                .uuid(UUID.randomUUID())
                .allocatedNodes(new HashSet<>(Collections.singletonList("emptyNode")))
                .skillPoints(0)
                .build();

            AggregatedModifiers result = aggregator.aggregate(data);

            assertFalse(result.hasModifiers(StatType.PHYSICAL_DAMAGE));
        }

        @Test
        @DisplayName("Should correctly check if stat has modifiers")
        void shouldCheckHasModifiers() {
            SkillNode node = createNodeWithModifiers("node1",
                new StatModifier(StatType.PHYSICAL_DAMAGE, 5, ModifierType.FLAT));

            when(config.getNode("node1")).thenReturn(node);

            SkillTreeData data = SkillTreeData.builder()
                .uuid(UUID.randomUUID())
                .allocatedNodes(new HashSet<>(Collections.singletonList("node1")))
                .skillPoints(0)
                .build();

            AggregatedModifiers result = aggregator.aggregate(data);

            assertTrue(result.hasModifiers(StatType.PHYSICAL_DAMAGE));
            assertFalse(result.hasModifiers(StatType.MAX_HEALTH));
        }

        @Test
        @DisplayName("Should aggregate across many nodes efficiently")
        void shouldAggregateAcrossManyNodes() {
            Set<String> allocatedNodes = new HashSet<>();
            int nodeCount = 50;

            for (int i = 0; i < nodeCount; i++) {
                String nodeId = "node" + i;
                SkillNode node = createNodeWithModifiers(nodeId,
                    new StatModifier(StatType.PHYSICAL_DAMAGE, 1, ModifierType.FLAT));
                when(config.getNode(nodeId)).thenReturn(node);
                allocatedNodes.add(nodeId);
            }

            SkillTreeData data = SkillTreeData.builder()
                .uuid(UUID.randomUUID())
                .allocatedNodes(allocatedNodes)
                .skillPoints(0)
                .build();

            AggregatedModifiers result = aggregator.aggregate(data);

            // Should have 50 modifiers summed to 50
            assertEquals(50, result.getFlatSum(StatType.PHYSICAL_DAMAGE), 0.001f);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════

    private SkillNode createNodeWithModifiers(String id, StatModifier... modifiers) {
        SkillNode node = new SkillNode();
        node.setId(id);
        node.setName("Node " + id);
        node.setTier(1);
        node.setConnections(new ArrayList<>());

        List<StatModifier> modifierList = new ArrayList<>();
        modifierList.addAll(Arrays.asList(modifiers));
        node.setModifiers(modifierList);

        return node;
    }
}
