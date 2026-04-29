package io.github.larsonix.trailoforbis.sanctum;

import com.hypixel.hytale.math.vector.Vector3d;
import io.github.larsonix.trailoforbis.skilltree.SkillTreeRegion;
import io.github.larsonix.trailoforbis.skilltree.config.SkillNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for GalaxySpiralLayoutMapper with 3D direction layout.
 *
 * <p>Elemental arm directions (cardinal):
 * <ul>
 *   <li>FIRE: +X (East)</li>
 *   <li>WATER: -X (West)</li>
 *   <li>LIGHTNING: +Z (South)</li>
 *   <li>EARTH: -Z (North)</li>
 *   <li>VOID: +Y (Up)</li>
 *   <li>WIND: -Y (Down)</li>
 * </ul>
 * <p>Octant arm directions (diagonal cube corners):
 * HAVOC, JUGGERNAUT, STRIKER, WARDEN, WARLOCK, LICH, TEMPEST, SENTINEL
 */
class GalaxySpiralLayoutMapperTest {

    // Scale constants from GalaxySpiralLayoutMapper
    private static final double LAYOUT_SCALE = 0.07;
    private static final double RADIAL_EXPANSION = 1.2;
    private static final double EXPECTED_SCALE = LAYOUT_SCALE * RADIAL_EXPANSION; // 0.084
    private static final double BASE_HEIGHT = 65.0;

    @Test
    void shouldUseLayoutPositionsNotSpiral() {
        // Create a mock node for fire_entry
        SkillNode fireEntry = mock(SkillNode.class);
        when(fireEntry.getId()).thenReturn("fire_entry");
        when(fireEntry.getSkillTreeRegion()).thenReturn(SkillTreeRegion.FIRE);
        when(fireEntry.isStartNode()).thenReturn(false);
        when(fireEntry.isKeystone()).thenReturn(false);
        when(fireEntry.isNotable()).thenReturn(false);
        when(fireEntry.getTier()).thenReturn(1);

        // Get position using the mapper
        Vector3d pos = GalaxySpiralLayoutMapper.toWorldPosition(fireEntry);

        System.out.println("fire_entry world position: " + pos.x + ", " + pos.y + ", " + pos.z);

        // With bundled exported positions, fire_entry is at a specific X position.
        // With procedural fallback, it would be at 70 * 0.07 * 1.2 = 5.88.
        // Either way, fire should be on the +X axis.
        assertTrue(pos.x > 4.0, "Fire entry should have positive X (along +X), got: " + pos.x);
        assertEquals(0.0, pos.z, 0.5, "World Z should be ~0 (fire on X axis)");
        assertEquals(BASE_HEIGHT, pos.y, 1.0, "World Y should be ~65 (base height)");
    }

    @Test
    void shouldPositionOriginAtCenter() {
        SkillNode origin = mock(SkillNode.class);
        when(origin.getId()).thenReturn("origin");
        when(origin.getSkillTreeRegion()).thenReturn(SkillTreeRegion.CORE);
        when(origin.isStartNode()).thenReturn(true);

        Vector3d pos = GalaxySpiralLayoutMapper.toWorldPosition(origin);

        System.out.println("origin world position: " + pos.x + ", " + pos.y + ", " + pos.z);

        // Origin in layout is at x=0, y=0, z=0
        // worldX = 0, worldZ = 0, worldY = BASE_HEIGHT = 65
        assertEquals(0.0, pos.x, 0.01, "Origin X should be 0");
        assertEquals(0.0, pos.z, 0.01, "Origin Z should be 0");
    }

    @Test
    void shouldPlaceDifferentArmsInDifferentPositions() {
        // Fire entry (+X direction)
        SkillNode fireEntry = mock(SkillNode.class);
        when(fireEntry.getId()).thenReturn("fire_entry");
        when(fireEntry.getSkillTreeRegion()).thenReturn(SkillTreeRegion.FIRE);
        when(fireEntry.isStartNode()).thenReturn(false);

        // Water entry (-X direction, opposite to fire)
        SkillNode waterEntry = mock(SkillNode.class);
        when(waterEntry.getId()).thenReturn("water_entry");
        when(waterEntry.getSkillTreeRegion()).thenReturn(SkillTreeRegion.WATER);
        when(waterEntry.isStartNode()).thenReturn(false);

        Vector3d firePos = GalaxySpiralLayoutMapper.toWorldPosition(fireEntry);
        Vector3d waterPos = GalaxySpiralLayoutMapper.toWorldPosition(waterEntry);

        System.out.println("fire_entry: " + firePos.x + ", " + firePos.z);
        System.out.println("water_entry: " + waterPos.x + ", " + waterPos.z);

        // With 3D cardinal directions:
        // - FIRE (+X): entry at (70, 0, 0) → world X=5.88, Z=0
        // - WATER (-X): entry at (-70, 0, 0) → world X=-5.88, Z=0

        // Fire should have positive X (bundled positions or procedural)
        assertTrue(firePos.x > 4, "Fire entry should have positive X, got: " + firePos.x);

        // Water should have negative X (opposite of fire)
        assertTrue(waterPos.x < -4, "Water entry should have negative X, got: " + waterPos.x);

        // Both should have Z near 0 (on X axis)
        assertTrue(Math.abs(firePos.z) < 1, "Fire Z should be ~0, got: " + firePos.z);
        assertTrue(Math.abs(waterPos.z) < 1, "Water Z should be ~0, got: " + waterPos.z);

        // They should be clearly separated (opposite sides of X axis)
        double distance = Math.abs(firePos.x - waterPos.x);
        assertTrue(distance >= 8, "Fire and Water should be clearly separated, got: " + distance);
    }

    @Test
    void shouldGenerateUniquePositionsForAllArms() {
        // All 14 arms (6 elemental + 8 octant)
        String[] armNames = {
            "fire", "water", "lightning", "earth", "void", "wind",
            "havoc", "juggernaut", "striker", "warden", "warlock", "lich", "tempest", "sentinel"
        };
        SkillTreeRegion[] regions = {
            SkillTreeRegion.FIRE, SkillTreeRegion.WATER, SkillTreeRegion.LIGHTNING,
            SkillTreeRegion.EARTH, SkillTreeRegion.VOID, SkillTreeRegion.WIND,
            SkillTreeRegion.HAVOC, SkillTreeRegion.JUGGERNAUT, SkillTreeRegion.STRIKER,
            SkillTreeRegion.WARDEN, SkillTreeRegion.WARLOCK, SkillTreeRegion.LICH,
            SkillTreeRegion.TEMPEST, SkillTreeRegion.SENTINEL
        };

        Vector3d[] positions = new Vector3d[14];

        for (int i = 0; i < 14; i++) {
            SkillNode node = mock(SkillNode.class);
            when(node.getId()).thenReturn(armNames[i] + "_entry");
            when(node.getSkillTreeRegion()).thenReturn(regions[i]);
            when(node.isStartNode()).thenReturn(false);

            positions[i] = GalaxySpiralLayoutMapper.toWorldPosition(node);
            System.out.println(armNames[i] + "_entry: x=" + positions[i].x +
                ", y=" + positions[i].y + ", z=" + positions[i].z);
        }

        // Elemental arms extend along 6 cardinal axes:
        // FIRE(+X), WATER(-X): separated on X axis
        // LIGHTNING(+Z), EARTH(-Z): separated on Z axis
        // VOID(+Y), WIND(-Y): separated on Y axis

        // Check opposite elemental pairs are on opposite sides
        assertTrue(positions[0].x > 0 && positions[1].x < 0,
            "Fire (+X) and Water (-X) should be on opposite X sides");

        assertTrue(positions[2].z > 0 && positions[3].z < 0,
            "Lightning (+Z) and Earth (-Z) should be on opposite Z sides");

        assertTrue(positions[4].y > positions[5].y,
            "Void (+Y) should be higher than Wind (-Y)");

        // Verify no two arms (across all 14) share the same position
        for (int i = 0; i < 14; i++) {
            for (int j = i + 1; j < 14; j++) {
                double distance3D = Math.sqrt(
                    Math.pow(positions[i].x - positions[j].x, 2) +
                    Math.pow(positions[i].y - positions[j].y, 2) +
                    Math.pow(positions[i].z - positions[j].z, 2)
                );
                assertTrue(distance3D > 1,
                    "Arms " + armNames[i] + " and " + armNames[j] +
                    " should be separated, 3D distance: " + distance3D);
            }
        }
    }
}
