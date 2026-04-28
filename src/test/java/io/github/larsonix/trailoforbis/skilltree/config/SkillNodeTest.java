package io.github.larsonix.trailoforbis.skilltree.config;

import io.github.larsonix.trailoforbis.skilltree.SkillTreeRegion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SkillNode}.
 *
 * <p>Tests type-based cost calculation (Basic/Notable/Keystone), region derivation from path,
 * node type identification (start, notable, keystone), and null-safe getters.
 */
@DisplayName("SkillNode")
class SkillNodeTest {

    /**
     * Creates a basic node with given tier.
     */
    private SkillNode createNodeWithTier(int tier) {
        SkillNode node = new SkillNode();
        node.setId("test_node");
        node.setTier(tier);
        return node;
    }

    /**
     * Creates a node with given path for region derivation.
     */
    private SkillNode createNodeWithPath(String path) {
        SkillNode node = new SkillNode();
        node.setId("test_node");
        node.setPath(path);
        return node;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Cost Calculation Tests (Type-Based: Basic=1, Notable=2, Keystone=3)
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Cost Calculation")
    class CostCalculationTests {

        @Test
        @DisplayName("Basic node costs 1 point")
        void basicNode_costs1Point() {
            SkillNode node = new SkillNode();
            node.setId("basic_node");
            // Not notable, not keystone = basic
            assertEquals(1, node.getCost());
        }

        @Test
        @DisplayName("Notable node costs 2 points")
        void notableNode_costs2Points() {
            SkillNode node = new SkillNode();
            node.setId("notable_node");
            node.setNotable(true);
            assertEquals(2, node.getCost());
        }

        @Test
        @DisplayName("Keystone node costs 3 points")
        void keystoneNode_costs3Points() {
            SkillNode node = new SkillNode();
            node.setId("keystone_node");
            node.setKeystone(true);
            assertEquals(3, node.getCost());
        }

        @Test
        @DisplayName("Keystone takes precedence over notable")
        void keystone_takesPrecedenceOverNotable() {
            SkillNode node = new SkillNode();
            node.setId("keystone_notable_node");
            node.setNotable(true);
            node.setKeystone(true);
            // Keystone wins
            assertEquals(3, node.getCost());
        }

        @Test
        @DisplayName("Tier does not affect cost")
        void tier_doesNotAffectCost() {
            // Basic node at tier 5 still costs 1
            SkillNode basicAtTier5 = createNodeWithTier(5);
            assertEquals(1, basicAtTier5.getCost());

            // Notable node at tier 1 still costs 2
            SkillNode notableAtTier1 = createNodeWithTier(1);
            notableAtTier1.setNotable(true);
            assertEquals(2, notableAtTier1.getCost());

            // Keystone node at tier 2 still costs 3
            SkillNode keystoneAtTier2 = createNodeWithTier(2);
            keystoneAtTier2.setKeystone(true);
            assertEquals(3, keystoneAtTier2.getCost());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Region Derivation Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Region Derivation")
    class RegionDerivationTests {

        // Legacy path names map to new 6-arm galaxy regions:
        // str/strength → FIRE, dex/dexterity → WATER, int/intelligence → WIND, vit/vitality → EARTH

        @Test
        @DisplayName("Path 'str' returns FIRE region (legacy mapping)")
        void pathStr_returnsFIRE() {
            SkillNode node = createNodeWithPath("str");
            assertEquals(SkillTreeRegion.FIRE, node.getSkillTreeRegion());
        }

        @Test
        @DisplayName("Path 'strength' returns FIRE region (legacy mapping)")
        void pathStrength_returnsFIRE() {
            SkillNode node = createNodeWithPath("strength");
            assertEquals(SkillTreeRegion.FIRE, node.getSkillTreeRegion());
        }

        @Test
        @DisplayName("Path 'dex' returns WATER region (legacy mapping)")
        void pathDex_returnsWATER() {
            SkillNode node = createNodeWithPath("dex");
            assertEquals(SkillTreeRegion.WATER, node.getSkillTreeRegion());
        }

        @Test
        @DisplayName("Path 'dexterity' returns WATER region (legacy mapping)")
        void pathDexterity_returnsWATER() {
            SkillNode node = createNodeWithPath("dexterity");
            assertEquals(SkillTreeRegion.WATER, node.getSkillTreeRegion());
        }

        @Test
        @DisplayName("Path 'int' returns WIND region (legacy mapping)")
        void pathInt_returnsWIND() {
            SkillNode node = createNodeWithPath("int");
            assertEquals(SkillTreeRegion.WIND, node.getSkillTreeRegion());
        }

        @Test
        @DisplayName("Path 'intelligence' returns WIND region (legacy mapping)")
        void pathIntelligence_returnsWIND() {
            SkillNode node = createNodeWithPath("intelligence");
            assertEquals(SkillTreeRegion.WIND, node.getSkillTreeRegion());
        }

        @Test
        @DisplayName("Path 'vit' returns EARTH region (legacy mapping)")
        void pathVit_returnsEARTH() {
            SkillNode node = createNodeWithPath("vit");
            assertEquals(SkillTreeRegion.EARTH, node.getSkillTreeRegion());
        }

        @Test
        @DisplayName("Path 'vitality' returns EARTH region (legacy mapping)")
        void pathVitality_returnsEARTH() {
            SkillNode node = createNodeWithPath("vitality");
            assertEquals(SkillTreeRegion.EARTH, node.getSkillTreeRegion());
        }

        @Test
        @DisplayName("Path 'origin' returns CORE region")
        void pathOrigin_returnsCORE() {
            SkillNode node = createNodeWithPath("origin");
            assertEquals(SkillTreeRegion.CORE, node.getSkillTreeRegion());
        }

        @Test
        @DisplayName("Null path returns CORE region")
        void nullPath_returnsCORE() {
            SkillNode node = new SkillNode();
            assertEquals(SkillTreeRegion.CORE, node.getSkillTreeRegion());
        }

        @Test
        @DisplayName("Unknown path returns CORE region")
        void unknownPath_returnsCORE() {
            SkillNode node = createNodeWithPath("unknown_path");
            assertEquals(SkillTreeRegion.CORE, node.getSkillTreeRegion());
        }

        @Test
        @DisplayName("Explicit region overrides path")
        void explicitRegion_overridesPath() {
            SkillNode node = createNodeWithPath("str");
            node.setRegion("EARTH");
            assertEquals(SkillTreeRegion.EARTH, node.getSkillTreeRegion());
        }

        @Test
        @DisplayName("Case-insensitive path matching")
        void caseInsensitivePath() {
            SkillNode node = createNodeWithPath("STR");
            assertEquals(SkillTreeRegion.FIRE, node.getSkillTreeRegion());
        }

        @Test
        @DisplayName("New region names work directly")
        void newRegionNames_workDirectly() {
            assertEquals(SkillTreeRegion.FIRE, createNodeWithPath("fire").getSkillTreeRegion());
            assertEquals(SkillTreeRegion.WATER, createNodeWithPath("water").getSkillTreeRegion());
            assertEquals(SkillTreeRegion.LIGHTNING, createNodeWithPath("lightning").getSkillTreeRegion());
            assertEquals(SkillTreeRegion.EARTH, createNodeWithPath("earth").getSkillTreeRegion());
            assertEquals(SkillTreeRegion.VOID, createNodeWithPath("void").getSkillTreeRegion());
            assertEquals(SkillTreeRegion.WIND, createNodeWithPath("wind").getSkillTreeRegion());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Node Type Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Node Type Identification")
    class NodeTypeTests {

        @Test
        @DisplayName("Start node is identified correctly")
        void startNode_identified() {
            SkillNode node = new SkillNode();
            node.setStartNode(true);

            assertTrue(node.isStartNode());
            assertFalse(node.isNotable());
            assertFalse(node.isKeystone());
        }

        @Test
        @DisplayName("Notable node is identified correctly")
        void notableNode_identified() {
            SkillNode node = new SkillNode();
            node.setNotable(true);

            assertFalse(node.isStartNode());
            assertTrue(node.isNotable());
            assertFalse(node.isKeystone());
        }

        @Test
        @DisplayName("Keystone node is identified correctly")
        void keystoneNode_identified() {
            SkillNode node = new SkillNode();
            node.setKeystone(true);

            assertFalse(node.isStartNode());
            assertFalse(node.isNotable());
            assertTrue(node.isKeystone());
        }

        @Test
        @DisplayName("Default node has no special flags")
        void defaultNode_noSpecialFlags() {
            SkillNode node = new SkillNode();

            assertFalse(node.isStartNode());
            assertFalse(node.isNotable());
            assertFalse(node.isKeystone());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Null-Safe Getters Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Null-Safe Getters")
    class NullSafeGetterTests {

        @Test
        @DisplayName("getId returns empty string when null")
        void getId_returnsEmptyWhenNull() {
            SkillNode node = new SkillNode();
            assertEquals("", node.getId());
        }

        @Test
        @DisplayName("getName returns empty string when null")
        void getName_returnsEmptyWhenNull() {
            SkillNode node = new SkillNode();
            assertEquals("", node.getName());
        }

        @Test
        @DisplayName("getDescription returns empty string when null")
        void getDescription_returnsEmptyWhenNull() {
            SkillNode node = new SkillNode();
            assertEquals("", node.getDescription());
        }

        @Test
        @DisplayName("getConnections returns empty list when null")
        void getConnections_returnsEmptyListWhenNull() {
            SkillNode node = new SkillNode();
            assertNotNull(node.getConnections());
            assertTrue(node.getConnections().isEmpty());
        }

        @Test
        @DisplayName("getModifiers returns list")
        void getModifiers_returnsList() {
            SkillNode node = new SkillNode();
            assertNotNull(node.getModifiers());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Connections Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Connections")
    class ConnectionsTests {

        @Test
        @DisplayName("Returns set connections")
        void returnsSetConnections() {
            SkillNode node = new SkillNode();
            node.setConnections(List.of("node_a", "node_b", "node_c"));

            assertEquals(3, node.getConnections().size());
            assertTrue(node.getConnections().contains("node_a"));
            assertTrue(node.getConnections().contains("node_b"));
            assertTrue(node.getConnections().contains("node_c"));
        }

        @Test
        @DisplayName("Empty connections list is valid")
        void emptyConnections_isValid() {
            SkillNode node = new SkillNode();
            node.setConnections(List.of());

            assertNotNull(node.getConnections());
            assertTrue(node.getConnections().isEmpty());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Tier Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Tier")
    class TierTests {

        @Test
        @DisplayName("Default tier is 1")
        void defaultTier_isOne() {
            SkillNode node = new SkillNode();
            assertEquals(1, node.getTier());
        }

        @Test
        @DisplayName("Tier can be set and retrieved")
        void tier_canBeSetAndRetrieved() {
            SkillNode node = new SkillNode();
            node.setTier(5);
            assertEquals(5, node.getTier());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Edge Cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Fully configured node works correctly")
        void fullyConfiguredNode_worksCorrectly() {
            SkillNode node = new SkillNode();
            node.setId("fire_damage_1");
            node.setName("Brute Force");
            node.setDescription("Increases physical damage");
            node.setRegion("FIRE");
            node.setTier(3);
            node.setNotable(true);
            node.setConnections(List.of("fire_damage_2", "fire_speed_1"));

            assertEquals("fire_damage_1", node.getId());
            assertEquals("Brute Force", node.getName());
            assertEquals("Increases physical damage", node.getDescription());
            assertEquals(SkillTreeRegion.FIRE, node.getSkillTreeRegion());
            assertEquals(3, node.getTier());
            assertEquals(2, node.getCost()); // Notable = 2 points
            assertTrue(node.isNotable());
            assertEquals(2, node.getConnections().size());
        }

        @Test
        @DisplayName("Multiple region lookups are consistent")
        void multipleRegionLookups_areConsistent() {
            SkillNode node = createNodeWithPath("earth");

            // Call multiple times to ensure consistency
            assertEquals(SkillTreeRegion.EARTH, node.getSkillTreeRegion());
            assertEquals(SkillTreeRegion.EARTH, node.getSkillTreeRegion());
            assertEquals(SkillTreeRegion.EARTH, node.getSkillTreeRegion());
        }

        @Test
        @DisplayName("Blank region string uses path derivation")
        void blankRegion_usesPathDerivation() {
            SkillNode node = createNodeWithPath("wind");
            node.setRegion("   ");

            assertEquals(SkillTreeRegion.WIND, node.getSkillTreeRegion());
        }
    }
}
