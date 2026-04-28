package io.github.larsonix.trailoforbis.skilltree;

import io.github.larsonix.trailoforbis.api.ServiceRegistry;
import io.github.larsonix.trailoforbis.api.services.AttributeService;
import io.github.larsonix.trailoforbis.config.ConfigManager;
import io.github.larsonix.trailoforbis.config.RPGConfig;
import io.github.larsonix.trailoforbis.skilltree.config.SkillNode;
import io.github.larsonix.trailoforbis.skilltree.config.SkillTreeConfig;
import io.github.larsonix.trailoforbis.skilltree.config.SkillTreeSettings;
import io.github.larsonix.trailoforbis.skilltree.model.SkillTreeData;
import io.github.larsonix.trailoforbis.skilltree.repository.SkillTreeRepository;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for SkillTreeManager - 15 test cases.
 *
 * <p>Covers node allocation validation, deallocation connectivity checks,
 * point management, and graph traversal logic.
 */
class SkillTreeManagerTest {

    @Mock
    private SkillTreeRepository repository;

    @Mock
    private ConfigManager configManager;

    @Mock
    private RPGConfig rpgConfig;

    @Mock
    private AttributeService attributeService;

    private SkillTreeManager manager;
    private SkillTreeConfig treeConfig;
    private AutoCloseable mockCloseable;
    private MockedStatic<ServiceRegistry> serviceRegistryMock;

    private static final UUID PLAYER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockCloseable = MockitoAnnotations.openMocks(this);

        // Mock ServiceRegistry to return our AttributeService
        serviceRegistryMock = mockStatic(ServiceRegistry.class);
        serviceRegistryMock.when(() -> ServiceRegistry.require(AttributeService.class))
            .thenReturn(attributeService);

        // Setup config mock
        SkillTreeSettings settings = new SkillTreeSettings();
        settings.setStartingPoints(10);
        settings.setFreeRespecs(3);
        when(rpgConfig.getSkillTree()).thenReturn(settings);
        when(configManager.getRPGConfig()).thenReturn(rpgConfig);

        // Create manager
        manager = new SkillTreeManager(repository, configManager);

        // Setup default skill tree config with connected nodes
        treeConfig = createTestTreeConfig();
        manager.loadConfig(treeConfig);
    }

    @AfterEach
    void tearDown() throws Exception {
        serviceRegistryMock.close();
        mockCloseable.close();
    }

    // =========================================================================
    // CAN ALLOCATE TESTS
    // =========================================================================

    @Nested
    @DisplayName("canAllocate Validation")
    class CanAllocateTests {

        @Test
        @DisplayName("canAllocate requires start node when no nodes allocated")
        void canAllocate_noNodesAllocated_requiresStartNode() {
            SkillTreeData data = createDataWithNodes(Set.of(), 10);
            when(repository.getOrCreate(PLAYER_ID, 10)).thenReturn(data);

            // "origin" is a start node
            assertTrue(manager.canAllocate(PLAYER_ID, "origin"),
                "Should allow allocating start node when none allocated");

            // "node_a" is not a start node
            assertFalse(manager.canAllocate(PLAYER_ID, "node_a"),
                "Should not allow non-start node when none allocated");
        }

        @Test
        @DisplayName("canAllocate returns true when adjacent to allocated node")
        void canAllocate_hasAdjacentNode_returnsTrue() {
            // origin is allocated, node_a is connected to origin
            SkillTreeData data = createDataWithNodes(Set.of("origin"), 10);
            when(repository.getOrCreate(PLAYER_ID, 10)).thenReturn(data);

            assertTrue(manager.canAllocate(PLAYER_ID, "node_a"),
                "Should allow allocation when adjacent to allocated node");
        }

        @Test
        @DisplayName("canAllocate returns false when not adjacent to any allocated node")
        void canAllocate_noAdjacentNode_returnsFalse() {
            // origin is allocated, node_c is NOT connected to origin (only to node_b)
            SkillTreeData data = createDataWithNodes(Set.of("origin"), 10);
            when(repository.getOrCreate(PLAYER_ID, 10)).thenReturn(data);

            assertFalse(manager.canAllocate(PLAYER_ID, "node_c"),
                "Should not allow allocation when no adjacent allocated node");
        }

        @Test
        @DisplayName("canAllocate returns false when insufficient skill points")
        void canAllocate_insufficientPoints_returnsFalse() {
            SkillTreeData data = createDataWithNodes(Set.of("origin"), 0);
            when(repository.getOrCreate(PLAYER_ID, 10)).thenReturn(data);

            assertFalse(manager.canAllocate(PLAYER_ID, "node_a"),
                "Should not allow allocation when no skill points available");
        }

        @Test
        @DisplayName("canAllocate returns false when already allocated")
        void canAllocate_alreadyAllocated_returnsFalse() {
            SkillTreeData data = createDataWithNodes(Set.of("origin", "node_a"), 10);
            when(repository.getOrCreate(PLAYER_ID, 10)).thenReturn(data);

            assertFalse(manager.canAllocate(PLAYER_ID, "node_a"),
                "Should not allow re-allocation of already allocated node");
        }

        @Test
        @DisplayName("canAllocate returns false for non-existent node")
        void canAllocate_nonExistentNode_returnsFalse() {
            SkillTreeData data = createDataWithNodes(Set.of("origin"), 10);
            when(repository.getOrCreate(PLAYER_ID, 10)).thenReturn(data);

            assertFalse(manager.canAllocate(PLAYER_ID, "fake_node"),
                "Should not allow allocation of non-existent node");
        }

        @Test
        @DisplayName("canAllocate respects node cost")
        void canAllocate_respectsNodeCost() {
            // notable_node costs 2 points and is connected to node_b
            // Path: origin -> node_a -> node_b -> notable_node
            SkillTreeData data = createDataWithNodes(Set.of("origin", "node_a", "node_b"), 1);
            when(repository.getOrCreate(PLAYER_ID, 10)).thenReturn(data);

            // notable_node is connected to node_b and costs 2
            assertFalse(manager.canAllocate(PLAYER_ID, "notable_node"),
                "Should not allow allocation when points < node cost");

            // With enough points
            SkillTreeData dataWithPoints = createDataWithNodes(Set.of("origin", "node_a", "node_b"), 2);
            when(repository.getOrCreate(PLAYER_ID, 10)).thenReturn(dataWithPoints);

            assertTrue(manager.canAllocate(PLAYER_ID, "notable_node"),
                "Should allow allocation when points >= node cost");
        }
    }

    // =========================================================================
    // CAN DEALLOCATE TESTS
    // =========================================================================

    @Nested
    @DisplayName("canDeallocate Validation")
    class CanDeallocateTests {

        @Test
        @DisplayName("canDeallocate returns false when removal would orphan nodes")
        void canDeallocate_wouldOrphanNodes_returnsFalse() {
            // Linear chain: origin -> node_a -> node_b
            // Removing node_a would orphan node_b
            SkillTreeData data = createDataWithNodes(Set.of("origin", "node_a", "node_b"), 0);
            when(repository.getOrCreate(PLAYER_ID, 10)).thenReturn(data);

            assertFalse(manager.canDeallocate(PLAYER_ID, "node_a"),
                "Should not allow deallocation that would orphan other nodes");
        }

        @Test
        @DisplayName("canDeallocate returns true when removal keeps connectivity")
        void canDeallocate_wouldNotOrphan_returnsTrue() {
            // Remove leaf node (node_b at end of chain)
            SkillTreeData data = createDataWithNodes(Set.of("origin", "node_a", "node_b"), 0);
            when(repository.getOrCreate(PLAYER_ID, 10)).thenReturn(data);

            assertTrue(manager.canDeallocate(PLAYER_ID, "node_b"),
                "Should allow deallocation of leaf node");
        }

        @Test
        @DisplayName("canDeallocate returns false for origin node (immutable root)")
        void canDeallocate_originNode_returnsFalse() {
            SkillTreeData data = createDataWithNodes(Set.of("origin"), 10);
            when(repository.getOrCreate(PLAYER_ID, 10)).thenReturn(data);

            assertFalse(manager.canDeallocate(PLAYER_ID, "origin"),
                "Origin node can never be deallocated - it's the immutable tree root");
        }

        @Test
        @DisplayName("canDeallocate returns false for origin even with other nodes")
        void canDeallocate_originWithOtherNodes_returnsFalse() {
            SkillTreeData data = createDataWithNodes(Set.of("origin", "node_a"), 10);
            when(repository.getOrCreate(PLAYER_ID, 10)).thenReturn(data);

            assertFalse(manager.canDeallocate(PLAYER_ID, "origin"),
                "Origin node can never be deallocated regardless of other allocations");
        }

        @Test
        @DisplayName("canDeallocate returns false for non-allocated node")
        void canDeallocate_notAllocated_returnsFalse() {
            SkillTreeData data = createDataWithNodes(Set.of("origin"), 10);
            when(repository.getOrCreate(PLAYER_ID, 10)).thenReturn(data);

            assertFalse(manager.canDeallocate(PLAYER_ID, "node_a"),
                "Should not allow deallocation of non-allocated node");
        }

        @Test
        @DisplayName("canDeallocate allows removal when alternate path exists")
        void canDeallocate_alternatePathExists_returnsTrue() {
            // Diamond pattern: origin -> node_a -> node_d
            //                  origin -> node_alt -> node_d
            // Removing node_a should be OK if node_alt connects to node_d
            SkillTreeData data = createDataWithNodes(
                Set.of("origin", "node_a", "node_alt", "node_d"), 0);
            when(repository.getOrCreate(PLAYER_ID, 10)).thenReturn(data);

            assertTrue(manager.canDeallocate(PLAYER_ID, "node_a"),
                "Should allow deallocation when alternate path keeps connectivity");
        }
    }

    // =========================================================================
    // ALLOCATE NODE TESTS
    // =========================================================================

    @Nested
    @DisplayName("allocateNode Operation")
    class AllocateNodeTests {

        @Test
        @DisplayName("allocateNode deducts points from skill points")
        void allocateNode_deductsPoints() {
            SkillTreeData data = createDataWithNodes(Set.of("origin"), 10);
            when(repository.getOrCreate(PLAYER_ID, 10)).thenReturn(data);

            boolean result = manager.allocateNode(PLAYER_ID, "node_a");

            assertTrue(result);
            verify(repository).save(argThat(saved ->
                saved.getSkillPoints() == 9 && // node_a costs 1
                saved.getAllocatedNodes().contains("node_a")
            ));
        }

        @Test
        @DisplayName("allocateNode returns false when canAllocate fails")
        void allocateNode_failsWhenCannotAllocate() {
            SkillTreeData data = createDataWithNodes(Set.of("origin"), 0);
            when(repository.getOrCreate(PLAYER_ID, 10)).thenReturn(data);

            boolean result = manager.allocateNode(PLAYER_ID, "node_a");

            assertFalse(result);
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("allocateNode invalidates player stats")
        void allocateNode_invalidatesStats() {
            SkillTreeData data = createDataWithNodes(Set.of("origin"), 10);
            when(repository.getOrCreate(PLAYER_ID, 10)).thenReturn(data);

            manager.allocateNode(PLAYER_ID, "node_a");

            verify(attributeService).recalculateStats(PLAYER_ID);
        }
    }

    // =========================================================================
    // DEALLOCATE NODE TESTS
    // =========================================================================

    @Nested
    @DisplayName("deallocateNode Operation")
    class DeallocateNodeTests {

        @Test
        @DisplayName("deallocateNode refunds points based on node cost")
        void deallocateNode_refundsPoints() {
            // node_a costs 1, should refund 1
            SkillTreeData data = createDataWithNodes(Set.of("origin", "node_a"), 5);
            when(repository.getOrCreate(PLAYER_ID, 10)).thenReturn(data);

            boolean result = manager.deallocateNode(PLAYER_ID, "node_a");

            assertTrue(result);
            verify(repository).save(argThat(saved ->
                saved.getSkillPoints() == 6 && // 5 + 1 refund
                !saved.getAllocatedNodes().contains("node_a")
            ));
        }

        @Test
        @DisplayName("deallocateNode returns false when canDeallocate fails")
        void deallocateNode_failsWhenCannotDeallocate() {
            // node_a is in the middle, removing it would orphan node_b
            SkillTreeData data = createDataWithNodes(Set.of("origin", "node_a", "node_b"), 5);
            when(repository.getOrCreate(PLAYER_ID, 10)).thenReturn(data);

            boolean result = manager.deallocateNode(PLAYER_ID, "node_a");

            assertFalse(result);
            verify(repository, never()).save(any());
        }
    }

    // =========================================================================
    // ADMIN ORIGIN PROTECTION TESTS
    // =========================================================================

    @Nested
    @DisplayName("Origin Node Protection")
    class OriginNodeProtectionTests {

        @Test
        @DisplayName("adminDeallocateNode returns false for origin node")
        void adminDeallocateNode_originNode_returnsFalse() {
            SkillTreeData data = createDataWithNodes(Set.of("origin", "node_a"), 5);
            when(repository.getOrCreate(PLAYER_ID, 10)).thenReturn(data);

            boolean result = manager.adminDeallocateNode(PLAYER_ID, "origin", true);

            assertFalse(result, "Admin should not be able to deallocate origin node");
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("adminDeallocateNode works for non-origin nodes")
        void adminDeallocateNode_regularNode_works() {
            SkillTreeData data = createDataWithNodes(Set.of("origin", "node_a"), 5);
            when(repository.getOrCreate(PLAYER_ID, 10)).thenReturn(data);

            boolean result = manager.adminDeallocateNode(PLAYER_ID, "node_a", true);

            assertTrue(result, "Admin should be able to deallocate regular nodes");
            verify(repository).save(argThat(saved ->
                !saved.getAllocatedNodes().contains("node_a") &&
                saved.getAllocatedNodes().contains("origin")
            ));
        }

        @Test
        @DisplayName("ORIGIN_NODE_ID constant is 'origin'")
        void originNodeIdConstant_isOrigin() {
            assertEquals("origin", SkillTreeManager.ORIGIN_NODE_ID,
                "ORIGIN_NODE_ID constant should be 'origin'");
        }
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private SkillTreeData createDataWithNodes(Set<String> nodes, int skillPoints) {
        return SkillTreeData.builder()
            .uuid(PLAYER_ID)
            .allocatedNodes(nodes)
            .skillPoints(skillPoints)
            .totalPointsEarned(10)
            .build();
    }

    /**
     * Creates a test tree config with this structure:
     * <pre>
     *            origin (START)
     *           /      \
     *      node_a      node_alt
     *        |            |
     *      node_b         |
     *        |            |
     *   notable_node      |
     *        |            |
     *      node_c ────────'
     *        |
     *      node_d
     * </pre>
     *
     * <ul>
     *   <li>origin: start node, cost 0</li>
     *   <li>node_a, node_b, node_c, node_alt: basic nodes, cost 1</li>
     *   <li>notable_node: notable node, cost 2</li>
     *   <li>node_d: connected to both node_c and node_alt (diamond)</li>
     * </ul>
     */
    private SkillTreeConfig createTestTreeConfig() {
        Map<String, SkillNode> nodes = new HashMap<>();

        // Origin - start node
        nodes.put("origin", createNode("origin", true, 0, List.of("node_a", "node_alt")));

        // Basic nodes
        nodes.put("node_a", createNode("node_a", false, 1, List.of("origin", "node_b")));
        nodes.put("node_alt", createNode("node_alt", false, 1, List.of("origin", "node_d")));
        nodes.put("node_b", createNode("node_b", false, 1, List.of("node_a", "notable_node")));

        // Notable node (costs 2)
        nodes.put("notable_node", createNode("notable_node", false, 2, List.of("node_b", "node_c")));

        // More nodes
        nodes.put("node_c", createNode("node_c", false, 1, List.of("notable_node", "node_d")));
        nodes.put("node_d", createNode("node_d", false, 1, List.of("node_c", "node_alt")));

        SkillTreeConfig config = new SkillTreeConfig();
        config.setNodes(nodes);
        return config;
    }

    private SkillNode createNode(String id, boolean isStart, int cost, List<String> connections) {
        SkillNode node = mock(SkillNode.class);
        when(node.getId()).thenReturn(id);
        when(node.isStartNode()).thenReturn(isStart);
        when(node.getCost()).thenReturn(cost);
        when(node.getConnections()).thenReturn(connections);
        return node;
    }
}
