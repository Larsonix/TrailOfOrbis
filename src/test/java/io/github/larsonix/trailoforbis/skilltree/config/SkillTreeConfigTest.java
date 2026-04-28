package io.github.larsonix.trailoforbis.skilltree.config;

import io.github.larsonix.trailoforbis.skilltree.SkillTreeRegion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SkillTreeConfig}.
 *
 * <p>Tests node management, filtering by type (start/notable/keystone),
 * region grouping, validation, and bridge node detection.
 */
@DisplayName("SkillTreeConfig")
class SkillTreeConfigTest {

    private SkillTreeConfig config;

    @BeforeEach
    void setUp() {
        config = new SkillTreeConfig();
    }

    /**
     * Creates a basic node with given properties.
     */
    private SkillNode createNode(String id, String path, int tier) {
        SkillNode node = new SkillNode();
        node.setId(id);
        node.setPath(path);
        node.setTier(tier);
        return node;
    }

    /**
     * Creates a populated config with test nodes.
     */
    private void setupTestNodes() {
        Map<String, SkillNode> nodes = new HashMap<>();

        // Start node
        SkillNode origin = createNode("origin", "origin", 0);
        origin.setStartNode(true);
        origin.setConnections(List.of("str_1", "dex_1", "int_1"));
        nodes.put("origin", origin);

        // STR nodes
        SkillNode str1 = createNode("str_1", "str", 1);
        str1.setConnections(List.of("origin", "str_2"));
        nodes.put("str_1", str1);

        SkillNode str2 = createNode("str_2", "str", 2);
        str2.setNotable(true);
        str2.setConnections(List.of("str_1", "str_3"));
        nodes.put("str_2", str2);

        SkillNode str3 = createNode("str_3", "str", 5);
        str3.setKeystone(true);
        str3.setConnections(List.of("str_2"));
        nodes.put("str_3", str3);

        // DEX nodes
        SkillNode dex1 = createNode("dex_1", "dex", 1);
        dex1.setConnections(List.of("origin", "dex_2"));
        nodes.put("dex_1", dex1);

        SkillNode dex2 = createNode("dex_2", "dex", 2);
        dex2.setConnections(List.of("dex_1"));
        nodes.put("dex_2", dex2);

        // INT nodes
        SkillNode int1 = createNode("int_1", "int", 1);
        int1.setConnections(List.of("origin"));
        nodes.put("int_1", int1);

        config.setNodes(nodes);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Basic Node Management Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Basic Node Management")
    class BasicNodeManagementTests {

        @Test
        @DisplayName("Empty config returns empty map")
        void emptyConfig_returnsEmptyMap() {
            assertNotNull(config.getNodes());
            assertTrue(config.getNodes().isEmpty());
        }

        @Test
        @DisplayName("getNode returns node by id")
        void getNode_returnsNodeById() {
            setupTestNodes();

            SkillNode node = config.getNode("str_1");

            assertNotNull(node);
            assertEquals("str_1", node.getId());
        }

        @Test
        @DisplayName("getNode returns null for unknown id")
        void getNode_returnsNullForUnknown() {
            setupTestNodes();

            SkillNode node = config.getNode("unknown_node");

            assertNull(node);
        }

        @Test
        @DisplayName("getNodeCount returns correct count")
        void getNodeCount_returnsCorrectCount() {
            setupTestNodes();

            assertEquals(7, config.getNodeCount());
        }

        @Test
        @DisplayName("getNodeCount returns 0 for empty config")
        void getNodeCount_returns0ForEmpty() {
            assertEquals(0, config.getNodeCount());
        }

        @Test
        @DisplayName("setNodes assigns id from map key")
        void setNodes_assignsIdFromMapKey() {
            Map<String, SkillNode> nodes = new HashMap<>();
            SkillNode node = new SkillNode(); // id not set
            nodes.put("my_node_id", node);

            config.setNodes(nodes);

            assertEquals("my_node_id", config.getNode("my_node_id").getId());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Node Type Filtering Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Node Type Filtering")
    class NodeTypeFilteringTests {

        @Test
        @DisplayName("getStartNodes returns only start nodes")
        void getStartNodes_returnsOnlyStartNodes() {
            setupTestNodes();

            List<SkillNode> startNodes = config.getStartNodes();

            assertEquals(1, startNodes.size());
            assertTrue(startNodes.get(0).isStartNode());
            assertEquals("origin", startNodes.get(0).getId());
        }

        @Test
        @DisplayName("getStartNodes returns empty list when none exist")
        void getStartNodes_returnsEmptyWhenNone() {
            Map<String, SkillNode> nodes = new HashMap<>();
            nodes.put("node1", createNode("node1", "str", 1));
            config.setNodes(nodes);

            List<SkillNode> startNodes = config.getStartNodes();

            assertTrue(startNodes.isEmpty());
        }

        @Test
        @DisplayName("getNotables returns only notable nodes")
        void getNotables_returnsOnlyNotables() {
            setupTestNodes();

            List<SkillNode> notables = config.getNotables();

            assertEquals(1, notables.size());
            assertTrue(notables.get(0).isNotable());
            assertEquals("str_2", notables.get(0).getId());
        }

        @Test
        @DisplayName("getKeystones returns only keystone nodes")
        void getKeystones_returnsOnlyKeystones() {
            setupTestNodes();

            List<SkillNode> keystones = config.getKeystones();

            assertEquals(1, keystones.size());
            assertTrue(keystones.get(0).isKeystone());
            assertEquals("str_3", keystones.get(0).getId());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Region Grouping Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Region Grouping")
    class RegionGroupingTests {

        // Note: Legacy path mappings apply - "str" → FIRE, "dex" → WATER, "int" → WIND

        @Test
        @DisplayName("getNodesByRegion returns nodes in region")
        void getNodesByRegion_returnsNodesInRegion() {
            setupTestNodes();

            // Nodes with path "str" are mapped to FIRE region
            List<SkillNode> fireNodes = config.getNodesByRegion(SkillTreeRegion.FIRE);

            assertEquals(3, fireNodes.size());
            assertTrue(fireNodes.stream().allMatch(n -> n.getSkillTreeRegion() == SkillTreeRegion.FIRE));
        }

        @Test
        @DisplayName("getNodesByRegion returns empty for unused region")
        void getNodesByRegion_returnsEmptyForUnusedRegion() {
            setupTestNodes();

            // VOID region has no test nodes
            List<SkillNode> voidNodes = config.getNodesByRegion(SkillTreeRegion.VOID);

            assertTrue(voidNodes.isEmpty());
        }

        @Test
        @DisplayName("getNodeCountByRegion returns correct count")
        void getNodeCountByRegion_returnsCorrectCount() {
            setupTestNodes();

            // Legacy path mappings: str→FIRE, dex→WATER, int→WIND, vit→EARTH
            assertEquals(3, config.getNodeCountByRegion(SkillTreeRegion.FIRE));    // str_1, str_2, str_3
            assertEquals(2, config.getNodeCountByRegion(SkillTreeRegion.WATER));   // dex_1, dex_2
            assertEquals(1, config.getNodeCountByRegion(SkillTreeRegion.WIND));    // int_1
            assertEquals(1, config.getNodeCountByRegion(SkillTreeRegion.CORE));    // origin
        }

        @Test
        @DisplayName("getRegionNodeCounts returns all regions")
        void getRegionNodeCounts_returnsAllRegions() {
            setupTestNodes();

            Map<SkillTreeRegion, Integer> counts = config.getRegionNodeCounts();

            assertEquals(SkillTreeRegion.values().length, counts.size());
            assertEquals(3, counts.get(SkillTreeRegion.FIRE));  // str nodes → FIRE
            assertEquals(0, counts.get(SkillTreeRegion.VOID));  // no void nodes
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Bridge Node Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Bridge Node Detection")
    class BridgeNodeTests {

        @Test
        @DisplayName("Origin node is a bridge (connects to multiple regions)")
        void originNode_isBridge() {
            setupTestNodes();

            List<SkillNode> bridges = config.getBridgeNodes();

            assertTrue(bridges.stream().anyMatch(n -> n.getId().equals("origin")));
        }

        @Test
        @DisplayName("Regular nodes within same region are not bridges")
        void regularNodes_areNotBridges() {
            setupTestNodes();

            List<SkillNode> bridges = config.getBridgeNodes();

            // str_2 connects only to str_1 and str_3 (all STR)
            assertFalse(bridges.stream().anyMatch(n -> n.getId().equals("str_2")));
        }

        @Test
        @DisplayName("Returns empty when no bridges exist")
        void returnsEmpty_whenNoBridges() {
            // Create isolated single-region config
            Map<String, SkillNode> nodes = new HashMap<>();
            SkillNode node1 = createNode("str_1", "str", 1);
            node1.setConnections(List.of("str_2"));
            nodes.put("str_1", node1);

            SkillNode node2 = createNode("str_2", "str", 2);
            node2.setConnections(List.of("str_1"));
            nodes.put("str_2", node2);

            config.setNodes(nodes);

            List<SkillNode> bridges = config.getBridgeNodes();

            assertTrue(bridges.isEmpty());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Validation Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Validation")
    class ValidationTests {

        @Test
        @DisplayName("Valid config returns empty error list")
        void validConfig_returnsEmptyErrors() {
            setupTestNodes();

            List<String> errors = config.validate();

            assertTrue(errors.isEmpty(), "Expected no errors but got: " + errors);
        }

        @Test
        @DisplayName("Empty config returns error")
        void emptyConfig_returnsError() {
            List<String> errors = config.validate();

            assertFalse(errors.isEmpty());
            assertTrue(errors.get(0).contains("No nodes defined"));
        }

        @Test
        @DisplayName("No start nodes returns error")
        void noStartNodes_returnsError() {
            Map<String, SkillNode> nodes = new HashMap<>();
            nodes.put("node1", createNode("node1", "str", 1));
            config.setNodes(nodes);

            List<String> errors = config.validate();

            assertTrue(errors.stream().anyMatch(e -> e.contains("No start nodes")));
        }

        @Test
        @DisplayName("Invalid connection reference returns error")
        void invalidConnection_returnsError() {
            Map<String, SkillNode> nodes = new HashMap<>();
            SkillNode node = createNode("node1", "str", 1);
            node.setStartNode(true);
            node.setConnections(List.of("non_existent_node"));
            nodes.put("node1", node);
            config.setNodes(nodes);

            List<String> errors = config.validate();

            assertTrue(errors.stream().anyMatch(e -> e.contains("non-existent node")));
        }

        @Test
        @DisplayName("Unidirectional connection returns error")
        void unidirectionalConnection_returnsError() {
            Map<String, SkillNode> nodes = new HashMap<>();

            SkillNode node1 = createNode("node1", "str", 1);
            node1.setStartNode(true);
            node1.setConnections(List.of("node2"));
            nodes.put("node1", node1);

            SkillNode node2 = createNode("node2", "str", 2);
            node2.setConnections(List.of()); // Missing back-connection
            nodes.put("node2", node2);

            config.setNodes(nodes);

            List<String> errors = config.validate();

            assertTrue(errors.stream().anyMatch(e -> e.contains("not bidirectional")));
        }

        @Test
        @DisplayName("Bidirectional connections pass validation")
        void bidirectionalConnections_passValidation() {
            Map<String, SkillNode> nodes = new HashMap<>();

            SkillNode node1 = createNode("node1", "str", 1);
            node1.setStartNode(true);
            node1.setConnections(List.of("node2"));
            nodes.put("node1", node1);

            SkillNode node2 = createNode("node2", "str", 2);
            node2.setConnections(List.of("node1")); // Proper bidirectional
            nodes.put("node2", node2);

            config.setNodes(nodes);

            List<String> errors = config.validate();

            // Should only have no errors
            assertTrue(errors.isEmpty(), "Errors: " + errors);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Edge Cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Null nodes map is handled safely")
        void nullNodesMap_handledSafely() {
            config.setNodes(null);

            assertNotNull(config.getNodes());
            assertTrue(config.getNodes().isEmpty());
            assertNull(config.getNode("any"));
            assertTrue(config.getStartNodes().isEmpty());
            assertEquals(0, config.getNodeCount());
        }

        @Test
        @DisplayName("Multiple start nodes are all returned")
        void multipleStartNodes_allReturned() {
            Map<String, SkillNode> nodes = new HashMap<>();

            SkillNode start1 = createNode("start1", "origin", 0);
            start1.setStartNode(true);
            nodes.put("start1", start1);

            SkillNode start2 = createNode("start2", "origin", 0);
            start2.setStartNode(true);
            nodes.put("start2", start2);

            config.setNodes(nodes);

            assertEquals(2, config.getStartNodes().size());
        }

        @Test
        @DisplayName("Node can be both notable and keystone")
        void nodeBothNotableAndKeystone() {
            Map<String, SkillNode> nodes = new HashMap<>();

            SkillNode special = createNode("special", "str", 5);
            special.setStartNode(true);
            special.setNotable(true);
            special.setKeystone(true);
            nodes.put("special", special);

            config.setNodes(nodes);

            assertEquals(1, config.getStartNodes().size());
            assertEquals(1, config.getNotables().size());
            assertEquals(1, config.getKeystones().size());
        }
    }
}
