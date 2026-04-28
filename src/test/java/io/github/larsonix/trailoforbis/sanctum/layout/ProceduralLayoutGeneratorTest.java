package io.github.larsonix.trailoforbis.sanctum.layout;

import io.github.larsonix.trailoforbis.ui.skilltree.NodePositionConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the procedural skill tree layout generation.
 */
class ProceduralLayoutGeneratorTest {

    @Test
    void testDefaultLayoutGeneratesOrigin() {
        Map<String, NodePositionConfig> positions = ProceduralLayoutGenerator.generateDefaultLayout();

        assertTrue(positions.containsKey("origin"), "Should contain origin node");
        NodePositionConfig origin = positions.get("origin");
        assertEquals(0, origin.relativeX());
        assertEquals(-1, origin.relativeY(), "Origin at y=64 (baseHeight 65 - 1)");
        assertEquals(0, origin.relativeZ());
    }

    @Test
    void testDefaultLayoutGeneratesEntryNodes() {
        Map<String, NodePositionConfig> positions = ProceduralLayoutGenerator.generateDefaultLayout();

        // Entry nodes for all 6 elemental arms
        assertTrue(positions.containsKey("fire_entry"), "Should contain fire_entry");
        assertTrue(positions.containsKey("water_entry"), "Should contain water_entry");
        assertTrue(positions.containsKey("lightning_entry"), "Should contain lightning_entry");
        assertTrue(positions.containsKey("earth_entry"), "Should contain earth_entry");
        assertTrue(positions.containsKey("void_entry"), "Should contain void_entry");
        assertTrue(positions.containsKey("wind_entry"), "Should contain wind_entry");
    }

    @Test
    void testDefaultLayoutGeneratesClusterNodes() {
        Map<String, NodePositionConfig> positions = ProceduralLayoutGenerator.generateDefaultLayout();

        // Fire arm clusters (6 nodes each)
        String[] fireClusters = {"fury", "inferno", "bloodlust", "berserker"};
        String[] suffixes = {"_1", "_2", "_branch", "_3", "_4", "_notable"};

        for (String cluster : fireClusters) {
            for (String suffix : suffixes) {
                String nodeId = "fire_" + cluster + suffix;
                assertTrue(positions.containsKey(nodeId),
                    "Should contain " + nodeId);
            }
        }
    }

    @Test
    void testDefaultLayoutGeneratesSynergyNodes() {
        Map<String, NodePositionConfig> positions = ProceduralLayoutGenerator.generateDefaultLayout();

        // Synergy nodes for fire arm (4 synergy nodes + 1 hub)
        assertTrue(positions.containsKey("fire_synergy_1"), "Should contain fire_synergy_1");
        assertTrue(positions.containsKey("fire_synergy_2"), "Should contain fire_synergy_2");
        assertTrue(positions.containsKey("fire_synergy_3"), "Should contain fire_synergy_3");
        assertTrue(positions.containsKey("fire_synergy_4"), "Should contain fire_synergy_4");
        assertTrue(positions.containsKey("fire_synergy_hub"), "Should contain fire_synergy_hub");
    }

    @Test
    void testDefaultLayoutGeneratesKeystoneNodes() {
        Map<String, NodePositionConfig> positions = ProceduralLayoutGenerator.generateDefaultLayout();

        // Keystone nodes for all 6 elemental arms
        String[] arms = {"fire", "water", "lightning", "earth", "void", "wind"};

        for (String arm : arms) {
            assertTrue(positions.containsKey(arm + "_keystone_1"),
                "Should contain " + arm + "_keystone_1");
            assertTrue(positions.containsKey(arm + "_keystone_2"),
                "Should contain " + arm + "_keystone_2");
        }
    }

    @Test
    void testDefaultLayoutGeneratesBridgeNodes() {
        Map<String, NodePositionConfig> positions = ProceduralLayoutGenerator.generateDefaultLayout();

        // Bridge nodes between adjacent arms
        assertTrue(positions.containsKey("bridge_fire_lightning_1"), "Should contain bridge_fire_lightning_1");
        assertTrue(positions.containsKey("bridge_fire_lightning_2"), "Should contain bridge_fire_lightning_2");
        assertTrue(positions.containsKey("bridge_fire_lightning_3"), "Should contain bridge_fire_lightning_3");

        assertTrue(positions.containsKey("bridge_lightning_water_1"), "Should contain bridge_lightning_water_1");
    }

    @Test
    void testArmDirectionsConfigured() {
        ProceduralLayoutConfig config = new ProceduralLayoutConfig();

        // Verify all 6 elemental arms have unique 3D cardinal directions
        LayoutMath.Direction3D dir1 = config.getArmDirection(1); // FIRE: +X
        LayoutMath.Direction3D dir2 = config.getArmDirection(2); // WATER: -X
        LayoutMath.Direction3D dir3 = config.getArmDirection(3); // LIGHTNING: +Z
        LayoutMath.Direction3D dir4 = config.getArmDirection(4); // EARTH: -Z
        LayoutMath.Direction3D dir5 = config.getArmDirection(5); // VOID: +Y
        LayoutMath.Direction3D dir6 = config.getArmDirection(6); // WIND: -Y

        // Verify expected cardinal directions
        assertEquals(LayoutMath.Direction3D.PLUS_X, dir1, "Arm 1 (FIRE) should be +X");
        assertEquals(LayoutMath.Direction3D.MINUS_X, dir2, "Arm 2 (WATER) should be -X");
        assertEquals(LayoutMath.Direction3D.PLUS_Z, dir3, "Arm 3 (LIGHTNING) should be +Z");
        assertEquals(LayoutMath.Direction3D.MINUS_Z, dir4, "Arm 4 (EARTH) should be -Z");
        assertEquals(LayoutMath.Direction3D.PLUS_Y, dir5, "Arm 5 (VOID) should be +Y");
        assertEquals(LayoutMath.Direction3D.MINUS_Y, dir6, "Arm 6 (WIND) should be -Y");
    }

    @Test
    void testDirection3DPerpendiculars() {
        // Test that perpendicular directions are correctly computed
        LayoutMath.Direction3D[] xPerps = LayoutMath.Direction3D.PLUS_X.getPerpendicularDirections();
        assertEquals(2, xPerps.length, "Should have 2 perpendicular directions");
        assertEquals(LayoutMath.Direction3D.PLUS_Y, xPerps[0], "X arm should have Y as first perpendicular");
        assertEquals(LayoutMath.Direction3D.PLUS_Z, xPerps[1], "X arm should have Z as second perpendicular");

        LayoutMath.Direction3D[] yPerps = LayoutMath.Direction3D.PLUS_Y.getPerpendicularDirections();
        assertEquals(LayoutMath.Direction3D.PLUS_X, yPerps[0], "Y arm should have X as first perpendicular");
        assertEquals(LayoutMath.Direction3D.PLUS_Z, yPerps[1], "Y arm should have Z as second perpendicular");

        LayoutMath.Direction3D[] zPerps = LayoutMath.Direction3D.PLUS_Z.getPerpendicularDirections();
        assertEquals(LayoutMath.Direction3D.PLUS_X, zPerps[0], "Z arm should have X as first perpendicular");
        assertEquals(LayoutMath.Direction3D.PLUS_Y, zPerps[1], "Z arm should have Y as second perpendicular");
    }

    @Test
    void testProjectAlongDirection3D() {
        // Test projection along +X with perpendicular offsets
        NodePositionConfig pos = LayoutMath.projectAlongDirection3D(
            "test", LayoutMath.Direction3D.PLUS_X, 100, 20, 10);
        // +X direction: along=100 on X, perp1=Y(20), perp2=Z(10)
        assertEquals(100, pos.relativeX(), "Along +X should set X coordinate");
        assertEquals(20, pos.relativeY(), "Perp1 (Y) should be 20");
        assertEquals(10, pos.relativeZ(), "Perp2 (Z) should be 10");

        // Test projection along +Y
        NodePositionConfig posY = LayoutMath.projectAlongDirection3D(
            "test", LayoutMath.Direction3D.PLUS_Y, 100, 20, 10);
        // +Y direction: along=100 on Y, perp1=X(20), perp2=Z(10)
        assertEquals(20, posY.relativeX(), "Perp1 (X) should be 20");
        assertEquals(100, posY.relativeY(), "Along +Y should set Y coordinate");
        assertEquals(10, posY.relativeZ(), "Perp2 (Z) should be 10");
    }

    @Test
    void testPolarToCartesianOrigin() {
        NodePositionConfig pos = LayoutMath.polarToCartesian("test", 0, 0, 0);
        assertEquals(0, pos.relativeX());
        assertEquals(0, pos.relativeY());
        assertEquals(0, pos.relativeZ());
    }

    @Test
    void testPolarToCartesianAtZeroDegrees() {
        NodePositionConfig pos = LayoutMath.polarToCartesian("test", 100, 0, 0);
        assertEquals(100, pos.relativeX());
        assertEquals(0, pos.relativeY());
    }

    @Test
    void testPolarToCartesianAt90Degrees() {
        NodePositionConfig pos = LayoutMath.polarToCartesian("test", 100, 90, 0);
        assertEquals(0, pos.relativeX());
        assertEquals(100, pos.relativeY());
    }

    @Test
    void testLayoutNodeCountReasonable() {
        Map<String, NodePositionConfig> positions = ProceduralLayoutGenerator.generateDefaultLayout();

        // Should have a reasonable number of nodes:
        // - 1 origin
        // - 14 entry nodes (6 elemental + 8 octant)
        // - 14 arms × 4 clusters × 6 nodes = 336 cluster nodes
        // - 14 arms × 5 synergy nodes = 70 synergy nodes
        // - 14 arms × 2 keystones = 28 keystone nodes
        // - bridges between adjacent arms
        // Total: ~485 nodes (6 elemental arms + 8 octant arms + bridges + origin)

        int count = positions.size();
        assertTrue(count >= 400, "Should generate at least 400 nodes, got " + count);
        assertTrue(count <= 550, "Should generate at most 550 nodes, got " + count);
    }

    @Test
    void testClusterTemplateGeneratesCorrectNodeCount() {
        ClusterTemplate template = new ClusterTemplate();
        List<NodePositionConfig> nodes = template.generateClusterNodes(
            "test",
            100, 100,
            45.0,
            0,
            ClusterTemplate.ClusterSide.LEFT,
            30,
            -1  // descend
        );

        assertEquals(6, nodes.size(), "Cluster should have 6 nodes");
    }

    @Test
    void testClusterTemplateNodeIds() {
        ClusterTemplate template = new ClusterTemplate();
        List<NodePositionConfig> nodes = template.generateClusterNodes(
            "fire_fury",
            0, 0,
            0,
            0,
            ClusterTemplate.ClusterSide.LEFT,
            0,
            -1  // descend
        );

        assertTrue(nodes.stream().anyMatch(n -> n.nodeId().equals("fire_fury_1")));
        assertTrue(nodes.stream().anyMatch(n -> n.nodeId().equals("fire_fury_2")));
        assertTrue(nodes.stream().anyMatch(n -> n.nodeId().equals("fire_fury_branch")));
        assertTrue(nodes.stream().anyMatch(n -> n.nodeId().equals("fire_fury_3")));
        assertTrue(nodes.stream().anyMatch(n -> n.nodeId().equals("fire_fury_4")));
        assertTrue(nodes.stream().anyMatch(n -> n.nodeId().equals("fire_fury_notable")));
    }

    @Test
    void testInterpolateAngle() {
        // Simple case
        assertEquals(45.0, LayoutMath.interpolateAngle(0, 90, 0.5), 0.001);

        // Wrap-around case (350 to 10 should go through 0)
        double interpolated = LayoutMath.interpolateAngle(350, 10, 0.5);
        assertEquals(0.0, interpolated, 0.001, "Should interpolate through 0");
    }

    @Test
    void testNormalizeAngle() {
        assertEquals(45.0, LayoutMath.normalizeAngle(45), 0.001);
        assertEquals(0.0, LayoutMath.normalizeAngle(360), 0.001);
        assertEquals(270.0, LayoutMath.normalizeAngle(-90), 0.001);
        assertEquals(90.0, LayoutMath.normalizeAngle(450), 0.001);
    }

    // ═══════════════════════════════════════════════════════════════════
    // Diagonal perpendicular vectors
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void testDiagonalPerpendicularsAreHorizontal() {
        // All 8 diagonal directions
        LayoutMath.Direction3D[] diagonals = {
            LayoutMath.Direction3D.PLUS_X_PLUS_Y_PLUS_Z,
            LayoutMath.Direction3D.PLUS_X_PLUS_Y_MINUS_Z,
            LayoutMath.Direction3D.PLUS_X_MINUS_Y_PLUS_Z,
            LayoutMath.Direction3D.PLUS_X_MINUS_Y_MINUS_Z,
            LayoutMath.Direction3D.MINUS_X_PLUS_Y_PLUS_Z,
            LayoutMath.Direction3D.MINUS_X_PLUS_Y_MINUS_Z,
            LayoutMath.Direction3D.MINUS_X_MINUS_Y_PLUS_Z,
            LayoutMath.Direction3D.MINUS_X_MINUS_Y_MINUS_Z
        };

        for (LayoutMath.Direction3D dir : diagonals) {
            LayoutMath.Direction3D[] perps = dir.getPerpendicularDirections();

            // perp0 must be purely horizontal (Y = 0) for level left/right spreading
            assertEquals(0, perps[0].y(),
                String.format("perp0 of (%d,%d,%d) should have Y=0 for horizontal spread",
                    dir.x(), dir.y(), dir.z()));

            // Orthogonality checks: dot products must be zero
            int dotDirPerp0 = dir.x() * perps[0].x() + dir.y() * perps[0].y() + dir.z() * perps[0].z();
            int dotDirPerp1 = dir.x() * perps[1].x() + dir.y() * perps[1].y() + dir.z() * perps[1].z();
            int dotPerp0Perp1 = perps[0].x() * perps[1].x() + perps[0].y() * perps[1].y() + perps[0].z() * perps[1].z();

            assertEquals(0, dotDirPerp0,
                String.format("dot(dir, perp0) must be 0 for direction (%d,%d,%d)", dir.x(), dir.y(), dir.z()));
            assertEquals(0, dotDirPerp1,
                String.format("dot(dir, perp1) must be 0 for direction (%d,%d,%d)", dir.x(), dir.y(), dir.z()));
            assertEquals(0, dotPerp0Perp1,
                String.format("dot(perp0, perp1) must be 0 for direction (%d,%d,%d)", dir.x(), dir.y(), dir.z()));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Octant arm way symmetry
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void testOctantWaysStartAtSameDistance() {
        Map<String, NodePositionConfig> positions = ProceduralLayoutGenerator.generateDefaultLayout();

        // Each octant arm has 4 clusters: 0&1 paired (LEFT/RIGHT), 2&3 paired (LEFT/RIGHT)
        // Cluster 0's _1 node and cluster 1's _1 node should be equidistant from origin
        String[][] octantArmPairs = {
            {"havoc_carnage_1", "havoc_frenzy_1"},         // Pair 0
            {"havoc_ruin_1", "havoc_mayhem_1"},             // Pair 1
            {"juggernaut_conquest_1", "juggernaut_dominion_1"},
            {"striker_quicksilver_1", "striker_precision_1"},
            {"warden_garrison_1", "warden_outrider_1"},
            {"warlock_hex_1", "warlock_ritual_1"},
            {"lich_grasp_1", "lich_crypt_1"},
            {"tempest_squall_1", "tempest_tailwind_1"},
            {"sentinel_aegis_1", "sentinel_vigilance_1"},
        };

        for (String[] pair : octantArmPairs) {
            NodePositionConfig left = positions.get(pair[0]);
            NodePositionConfig right = positions.get(pair[1]);
            assertNotNull(left, "Should contain " + pair[0]);
            assertNotNull(right, "Should contain " + pair[1]);

            double distLeft = Math.sqrt(
                left.relativeX() * left.relativeX() +
                left.relativeY() * left.relativeY() +
                left.relativeZ() * left.relativeZ());
            double distRight = Math.sqrt(
                right.relativeX() * right.relativeX() +
                right.relativeY() * right.relativeY() +
                right.relativeZ() * right.relativeZ());

            assertEquals(distLeft, distRight, 1.0,
                String.format("Paired clusters %s and %s should be equidistant from origin (%.1f vs %.1f)",
                    pair[0], pair[1], distLeft, distRight));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Octant intra-cluster spacing matches cardinal
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void testOctantIntraClusterSpacingMatchesCardinal() {
        Map<String, NodePositionConfig> positions = ProceduralLayoutGenerator.generateDefaultLayout();

        // Measure cardinal arm per-axis distance between _1 and _notable
        // Fire arm (+X direction): all offset goes into X axis only
        NodePositionConfig fireNode1 = positions.get("fire_fury_1");
        NodePositionConfig fireNotable = positions.get("fire_fury_notable");
        assertNotNull(fireNode1, "Should contain fire_fury_1");
        assertNotNull(fireNotable, "Should contain fire_fury_notable");

        // For +X cardinal, the along-arm difference is purely in X
        double cardinalPerAxisDiff = Math.abs(fireNotable.relativeX() - fireNode1.relativeX());

        // Measure octant arm per-axis distance between _1 and _notable
        // Havoc arm (1,1,1): each axis diff should match cardinal's per-axis diff
        NodePositionConfig havocNode1 = positions.get("havoc_carnage_1");
        NodePositionConfig havocNotable = positions.get("havoc_carnage_notable");
        assertNotNull(havocNode1, "Should contain havoc_carnage_1");
        assertNotNull(havocNotable, "Should contain havoc_carnage_notable");

        double octantDiffX = Math.abs(havocNotable.relativeX() - havocNode1.relativeX());
        double octantDiffY = Math.abs(havocNotable.relativeY() - havocNode1.relativeY());
        double octantDiffZ = Math.abs(havocNotable.relativeZ() - havocNode1.relativeZ());

        // Each octant per-axis diff should match cardinal per-axis diff (±15%)
        // This confirms the compensation makes intra-cluster spacing consistent
        double tolerance = cardinalPerAxisDiff * 0.15;
        assertEquals(cardinalPerAxisDiff, octantDiffX, tolerance,
            String.format("Octant X per-axis diff (%.1f) should match cardinal (%.1f)", octantDiffX, cardinalPerAxisDiff));
        assertEquals(cardinalPerAxisDiff, octantDiffY, tolerance,
            String.format("Octant Y per-axis diff (%.1f) should match cardinal (%.1f)", octantDiffY, cardinalPerAxisDiff));
        assertEquals(cardinalPerAxisDiff, octantDiffZ, tolerance,
            String.format("Octant Z per-axis diff (%.1f) should match cardinal (%.1f)", octantDiffZ, cardinalPerAxisDiff));
    }

    @Test
    void testOctantBranchNodesHave3DDepth() {
        Map<String, NodePositionConfig> positions = ProceduralLayoutGenerator.generateDefaultLayout();
        ProceduralLayoutConfig config = new ProceduralLayoutConfig();

        String[] octantArms = {"havoc", "juggernaut", "striker", "warden", "warlock", "lich", "tempest", "sentinel"};

        for (String arm : octantArms) {
            List<String> clusterNames = config.getArmClusters().get(arm);
            for (String cluster : clusterNames) {
                NodePositionConfig node3 = positions.get(arm + "_" + cluster + "_3");
                NodePositionConfig node4 = positions.get(arm + "_" + cluster + "_4");
                assertNotNull(node3, "Should contain " + arm + "_" + cluster + "_3");
                assertNotNull(node4, "Should contain " + arm + "_" + cluster + "_4");

                // _3 and _4 must differ on at least 2 axes (true 3D diamond, not flat)
                int diffX = Math.abs(node3.relativeX() - node4.relativeX());
                int diffY = Math.abs(node3.relativeY() - node4.relativeY());
                int diffZ = Math.abs(node3.relativeZ() - node4.relativeZ());
                int axesWithDiff = (diffX > 0 ? 1 : 0) + (diffY > 0 ? 1 : 0) + (diffZ > 0 ? 1 : 0);

                assertTrue(axesWithDiff >= 2,
                    String.format("%s_%s: _3 and _4 should differ on ≥2 axes for 3D depth, " +
                        "but only differ on %d (dx=%d, dy=%d, dz=%d)",
                        arm, cluster, axesWithDiff, diffX, diffY, diffZ));
            }
        }
    }

    @Test
    void testOctantClustersDoNotOverlap() {
        Map<String, NodePositionConfig> positions = ProceduralLayoutGenerator.generateDefaultLayout();
        ProceduralLayoutConfig config = new ProceduralLayoutConfig();

        String[] octantArms = {"havoc", "juggernaut", "striker", "warden", "warlock", "lich", "tempest", "sentinel"};
        int[] armIndices = {7, 8, 9, 10, 11, 12, 13, 14};

        for (int a = 0; a < octantArms.length; a++) {
            String arm = octantArms[a];
            LayoutMath.Direction3D dir = config.getArmDirection(armIndices[a]);
            List<String> clusterNames = config.getArmClusters().get(arm);

            // Pair 0 notable (cluster 0 or 1, whichever is further)
            double maxPair0Notable = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < 2; i++) {
                NodePositionConfig notable = positions.get(arm + "_" + clusterNames.get(i) + "_notable");
                double proj = notable.relativeX() * dir.x() + notable.relativeY() * dir.y() + notable.relativeZ() * dir.z();
                maxPair0Notable = Math.max(maxPair0Notable, proj);
            }

            // Pair 1 start (cluster 2 or 3, whichever is closer)
            double minPair1Start = Double.POSITIVE_INFINITY;
            for (int i = 2; i < 4; i++) {
                NodePositionConfig node1 = positions.get(arm + "_" + clusterNames.get(i) + "_1");
                double proj = node1.relativeX() * dir.x() + node1.relativeY() * dir.y() + node1.relativeZ() * dir.z();
                minPair1Start = Math.min(minPair1Start, proj);
            }

            assertTrue(minPair1Start > maxPair0Notable,
                String.format("%s: pair 1 start proj (%.1f) must exceed pair 0 notable proj (%.1f)",
                    arm, minPair1Start, maxPair0Notable));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Node ordering: notables before hub before keystones
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void testOctantNodeOrdering() {
        Map<String, NodePositionConfig> positions = ProceduralLayoutGenerator.generateDefaultLayout();
        ProceduralLayoutConfig config = new ProceduralLayoutConfig();

        String[] octantArms = {"havoc", "juggernaut", "striker", "warden", "warlock", "lich", "tempest", "sentinel"};
        int[] armIndices = {7, 8, 9, 10, 11, 12, 13, 14};

        for (int a = 0; a < octantArms.length; a++) {
            String arm = octantArms[a];
            LayoutMath.Direction3D dir = config.getArmDirection(armIndices[a]);

            // Get all 4 notable positions and project onto arm direction
            List<String> clusterNames = config.getArmClusters().get(arm);
            double maxNotableProj = Double.NEGATIVE_INFINITY;
            for (String cluster : clusterNames) {
                NodePositionConfig notable = positions.get(arm + "_" + cluster + "_notable");
                assertNotNull(notable, "Should contain " + arm + "_" + cluster + "_notable");
                double proj = notable.relativeX() * dir.x() + notable.relativeY() * dir.y() + notable.relativeZ() * dir.z();
                maxNotableProj = Math.max(maxNotableProj, proj);
            }

            // Hub must project further than all notables
            NodePositionConfig hub = positions.get(arm + "_synergy_hub");
            assertNotNull(hub, "Should contain " + arm + "_synergy_hub");
            double hubProj = hub.relativeX() * dir.x() + hub.relativeY() * dir.y() + hub.relativeZ() * dir.z();
            assertTrue(hubProj > maxNotableProj,
                String.format("%s hub projection (%.1f) should exceed max notable projection (%.1f)",
                    arm, hubProj, maxNotableProj));

            // Both keystones must project further than the hub
            for (int k = 1; k <= 2; k++) {
                NodePositionConfig ks = positions.get(arm + "_keystone_" + k);
                assertNotNull(ks, "Should contain " + arm + "_keystone_" + k);
                double ksProj = ks.relativeX() * dir.x() + ks.relativeY() * dir.y() + ks.relativeZ() * dir.z();
                assertTrue(ksProj > hubProj,
                    String.format("%s keystone_%d projection (%.1f) should exceed hub projection (%.1f)",
                        arm, k, ksProj, hubProj));
            }
        }
    }

}
