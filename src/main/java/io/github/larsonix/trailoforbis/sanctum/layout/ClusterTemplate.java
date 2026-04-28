package io.github.larsonix.trailoforbis.sanctum.layout;

import io.github.larsonix.trailoforbis.ui.skilltree.NodePositionConfig;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * Defines the 6-node branching pattern for skill tree clusters.
 *
 * <p>Each cluster follows a 3D diamond pattern (viewed from arm direction):
 * <pre>
 *              [_3] (perp1+, perp2+)
 *             /
 *   [_1]─[_2]─[_branch]─[_notable]
 *             \
 *              [_4] (perp1-, perp2-)
 * </pre>
 *
 * <p>The _3 and _4 nodes spread diagonally in opposite directions on both
 * perpendicular axes, creating true 3D separation instead of a flat plane.
 *
 * <p>Local coordinates are defined as:
 * <ul>
 *   <li>alongArm: Distance along the arm direction (positive = outward from center)</li>
 *   <li>perpendicular: Distance on first perpendicular axis (perp1)</li>
 *   <li>height: Distance on second perpendicular axis (perp2)</li>
 * </ul>
 */
public class ClusterTemplate {

    /**
     * Node position within a cluster template (local coordinates).
     */
    public record NodeOffset(String suffix, double alongArm, double perpendicular, int height) {}

    // Default cluster node pattern - compact 3D diamond shape
    // _3 and _4 spread diagonally in OPPOSITE directions on both perp axes
    private static final List<NodeOffset> DEFAULT_PATTERN = List.of(
        new NodeOffset("_1",       0,   0,    0),    // First node at cluster start
        new NodeOffset("_2",       25,  0,    0),    // Second node along arm
        new NodeOffset("_branch",  45,  0,    0),    // Branch point (centered)
        new NodeOffset("_3",       60,  20,   20),   // Upper-right diagonal (perp1+, perp2+)
        new NodeOffset("_4",       60, -20,  -20),   // Lower-left diagonal (perp1-, perp2-)
        new NodeOffset("_notable", 75,  0,    0)     // Notable at end (centered)
    );

    private final List<NodeOffset> nodePattern;

    /**
     * Creates a cluster template with the default 6-node pattern.
     */
    public ClusterTemplate() {
        this.nodePattern = DEFAULT_PATTERN;
    }

    /**
     * Creates a cluster template with a custom pattern.
     *
     * @param pattern List of node offsets defining the pattern
     */
    public ClusterTemplate(@Nonnull List<NodeOffset> pattern) {
        this.nodePattern = List.copyOf(pattern);
    }

    /**
     * Gets all node offsets in this template.
     */
    @Nonnull
    public List<NodeOffset> getNodePattern() {
        return nodePattern;
    }

    /**
     * Generates node positions for a cluster at the specified location.
     *
     * @param clusterPrefix Node ID prefix (e.g., "fire_fury")
     * @param clusterCenterX X coordinate of cluster center
     * @param clusterCenterY Y coordinate of cluster center
     * @param armAngleDegrees Angle of the arm this cluster belongs to
     * @param baseZ Base Z height for the cluster
     * @param side Which side of the arm (LEFT = +perpendicular, RIGHT = -perpendicular)
     * @param sideOffset Additional perpendicular offset to alternate clusters
     * @param zDirection Vertical direction (1 = ascend, -1 = descend)
     * @return List of NodePositionConfig for all nodes in the cluster
     */
    @Nonnull
    public List<NodePositionConfig> generateClusterNodes(
            @Nonnull String clusterPrefix,
            double clusterCenterX,
            double clusterCenterY,
            double armAngleDegrees,
            int baseZ,
            ClusterSide side,
            double sideOffset,
            int zDirection) {

        List<NodePositionConfig> positions = new ArrayList<>();

        for (NodeOffset offset : nodePattern) {
            String nodeId = clusterPrefix + offset.suffix();

            // Apply side offset to perpendicular coordinate
            double adjustedPerp = offset.perpendicular();
            if (side == ClusterSide.RIGHT) {
                // Flip perpendicular for right-side clusters
                adjustedPerp = -adjustedPerp;
            }
            adjustedPerp += (side == ClusterSide.LEFT ? sideOffset : -sideOffset);

            // Template heights are negative magnitudes; multiply by -direction to get actual Z
            // Ascending (dir=+1): positive progression; Descending (dir=-1): negative progression
            int adjustedHeight = -offset.height() * zDirection;

            NodePositionConfig pos = LayoutMath.projectAlongArm(
                nodeId,
                clusterCenterX,
                clusterCenterY,
                armAngleDegrees,
                offset.alongArm(),
                adjustedPerp,
                baseZ + adjustedHeight
            );

            positions.add(pos);
        }

        return positions;
    }

    /**
     * Generates node positions for a cluster using 3D direction vectors.
     *
     * @param clusterPrefix Node ID prefix (e.g., "fire_fury")
     * @param direction The 3D direction of the arm
     * @param clusterDistance Distance from origin to cluster center
     * @param sideOffset Perpendicular offset for alternating clusters
     * @return List of NodePositionConfig for all nodes in the cluster
     */
    @Nonnull
    public List<NodePositionConfig> generateClusterNodes3D(
            @Nonnull String clusterPrefix,
            @Nonnull LayoutMath.Direction3D direction,
            double clusterDistance,
            double sideOffset) {

        List<NodePositionConfig> positions = new ArrayList<>();
        double dirMag = direction.magnitude();
        LayoutMath.Direction3D[] perps = direction.getPerpendicularDirections();
        double perp0Mag = perps[0].magnitude();
        double perp1Mag = perps[1].magnitude();

        for (NodeOffset offset : nodePattern) {
            String nodeId = clusterPrefix + offset.suffix();

            // Pre-compensate local offsets: multiply by magnitude so that
            // projectAlongDirection3D's ÷magnitude cancels out,
            // keeping intra-cluster spacing at consistent physical scale
            double totalAlongDist = clusterDistance + offset.alongArm() * dirMag;
            double perp1 = (offset.perpendicular() + sideOffset) * perp0Mag;

            // Pre-compensate height by second perpendicular magnitude (same pattern as perp0).
            // For cardinal arms: perp1Mag=1.0, no change. For octants: perp1Mag=√6.
            double perp2 = offset.height() * perp1Mag;

            NodePositionConfig pos = LayoutMath.projectAlongDirection3D(
                nodeId,
                direction,
                totalAlongDist,
                perp1,
                perp2
            );

            positions.add(pos);
        }

        return positions;
    }

    /**
     * Which side of the arm a cluster is placed.
     */
    public enum ClusterSide {
        LEFT,   // Positive perpendicular offset
        RIGHT   // Negative perpendicular offset
    }

    /**
     * Creates a template from YAML configuration.
     *
     * <p>Supported field names (tries new names first, falls back to old names):
     * <ul>
     *   <li>along / alongArm - distance along arm</li>
     *   <li>perp1 / perpendicular - first perpendicular axis</li>
     *   <li>perp2 / height - second perpendicular axis</li>
     * </ul>
     *
     * @param config Map with node offset values for each suffix
     * @return ClusterTemplate with the specified pattern
     */
    @Nonnull
    public static ClusterTemplate fromConfig(@Nonnull java.util.Map<String, java.util.Map<String, Object>> config) {
        List<NodeOffset> pattern = new ArrayList<>();

        // Expected order of suffixes
        String[] suffixes = {"_1", "_2", "_branch", "_3", "_4", "_notable"};

        for (String suffix : suffixes) {
            java.util.Map<String, Object> nodeConfig = config.get(suffix);
            if (nodeConfig != null) {
                // Support both new names (along, perp1, perp2) and old names (alongArm, perpendicular, height)
                double along = getDoubleWithFallback(nodeConfig, "along", "alongArm", 0);
                double perp1 = getDoubleWithFallback(nodeConfig, "perp1", "perpendicular", 0);
                int perp2 = getIntWithFallback(nodeConfig, "perp2", "height", 0);
                pattern.add(new NodeOffset(suffix, along, perp1, perp2));
            }
        }

        return pattern.isEmpty() ? new ClusterTemplate() : new ClusterTemplate(pattern);
    }

    /**
     * Gets the "along" value from the notable node, used to calculate synergy distances.
     */
    public double getNotableDistance() {
        return nodePattern.stream()
            .filter(n -> "_notable".equals(n.suffix()))
            .findFirst()
            .map(NodeOffset::alongArm)
            .orElse(75.0);
    }

    private static double getDouble(java.util.Map<String, Object> map, String key, double defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return defaultValue;
    }

    private static double getDoubleWithFallback(java.util.Map<String, Object> map, String key, String fallbackKey, double defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        value = map.get(fallbackKey);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return defaultValue;
    }

    private static int getInt(java.util.Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }

    private static int getIntWithFallback(java.util.Map<String, Object> map, String key, String fallbackKey, int defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        value = map.get(fallbackKey);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }
}
