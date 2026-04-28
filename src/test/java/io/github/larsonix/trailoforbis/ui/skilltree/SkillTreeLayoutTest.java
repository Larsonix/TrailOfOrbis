package io.github.larsonix.trailoforbis.ui.skilltree;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SkillTreeLayoutTest {

    @Test
    void shouldLoadPositionsFromYaml() {
        SkillTreeLayout layout = new SkillTreeLayout();

        // Should have loaded positions (procedurally generated)
        assertTrue(layout.getNodeCount() > 0, "Layout should have generated positions");
        System.out.println("Generated " + layout.getNodeCount() + " positions procedurally");

        // Check key positions exist
        assertTrue(layout.hasPosition("origin"), "Should have origin position");
        assertTrue(layout.hasPosition("fire_entry"), "Should have fire_entry position");
        assertTrue(layout.hasPosition("water_entry"), "Should have water_entry position");

        // Verify origin is at center (y=-1 means world y=64)
        NodePositionConfig origin = layout.getNodePosition("origin");
        assertNotNull(origin, "Origin should not be null");
        assertEquals(0, origin.relativeX(), "Origin X should be 0");
        assertEquals(-1, origin.relativeY(), "Origin Y should be -1 (world y=64)");
        assertEquals(0, origin.relativeZ(), "Origin Z should be 0");

        // Verify fire_entry is along +X direction (3D cardinal directions)
        NodePositionConfig fireEntry = layout.getNodePosition("fire_entry");
        assertNotNull(fireEntry, "fire_entry should not be null");
        System.out.println("fire_entry position: x=" + fireEntry.relativeX() +
                           ", y=" + fireEntry.relativeY() +
                           ", z=" + fireEntry.relativeZ());

        // Fire arm extends along +X, so entry should be at (70, 0, 0)
        assertEquals(70, fireEntry.relativeX(), "fire_entry X should be 70 (along +X)");
        assertEquals(0, fireEntry.relativeY(), "fire_entry Y should be 0");
        assertEquals(0, fireEntry.relativeZ(), "fire_entry Z should be 0");
    }

    @Test
    void shouldReturnNullForUnknownNode() {
        SkillTreeLayout layout = new SkillTreeLayout();
        assertNull(layout.getNodePosition("nonexistent_node_12345"));
    }

    @Test
    void shouldGenerateExpectedNodeCount() {
        SkillTreeLayout layout = new SkillTreeLayout();

        // Should generate a reasonable number of nodes
        // ~485 nodes expected from procedural generation (6 elemental + 8 octant arms)
        int count = layout.getNodeCount();
        assertTrue(count >= 400, "Should generate at least 400 nodes, got " + count);
        assertTrue(count <= 550, "Should generate at most 550 nodes, got " + count);
    }

    @Test
    void shouldHaveAllArmEntryNodes() {
        SkillTreeLayout layout = new SkillTreeLayout();

        String[] arms = {"fire", "water", "lightning", "earth", "void", "wind"};
        for (String arm : arms) {
            assertTrue(layout.hasPosition(arm + "_entry"),
                "Should have " + arm + "_entry position");
        }
    }

    @Test
    void shouldHaveClusterNodes() {
        SkillTreeLayout layout = new SkillTreeLayout();

        // Fire arm should have all fury cluster nodes
        assertTrue(layout.hasPosition("fire_fury_1"), "Should have fire_fury_1");
        assertTrue(layout.hasPosition("fire_fury_2"), "Should have fire_fury_2");
        assertTrue(layout.hasPosition("fire_fury_branch"), "Should have fire_fury_branch");
        assertTrue(layout.hasPosition("fire_fury_3"), "Should have fire_fury_3");
        assertTrue(layout.hasPosition("fire_fury_4"), "Should have fire_fury_4");
        assertTrue(layout.hasPosition("fire_fury_notable"), "Should have fire_fury_notable");
    }
}
