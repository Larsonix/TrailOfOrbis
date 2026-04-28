package io.github.larsonix.trailoforbis.sanctum.layout;

import com.hypixel.hytale.logger.HytaleLogger;
import io.github.larsonix.trailoforbis.skilltree.SkillTreeRegion;
import io.github.larsonix.trailoforbis.ui.skilltree.NodePositionConfig;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.logging.Level;

/**
 * Procedurally generates skill tree node positions based on configuration.
 *
 * <p>The layout follows a 14-arm 3D star pattern (6 elemental + 8 octant):
 * <ul>
 *   <li>Origin at center (0, 0, 0)</li>
 *   <li>Core nodes in a compact cluster near origin</li>
 *   <li>6 elemental arms extending along cardinal 3D directions:
 *     <ul>
 *       <li>FIRE: +X (East)</li>
 *       <li>WATER: -X (West)</li>
 *       <li>LIGHTNING: +Z (South)</li>
 *       <li>EARTH: -Z (North)</li>
 *       <li>VOID: +Y (Up)</li>
 *       <li>WIND: -Y (Down)</li>
 *     </ul>
 *   </li>
 *   <li>8 octant arms extending along diagonal 3D directions (cube corners)</li>
 *   <li>Each arm: entry node → 4 clusters → synergy nodes → keystones</li>
 *   <li>Bridge nodes connecting adjacent arms in 3D space</li>
 * </ul>
 *
 * <p>Cluster pattern (6 nodes each):
 * <pre>
 *                        [_3]
 *                       /
 *   [_1] ─ [_2] ─ [_branch] ─ [_notable]
 *                       \
 *                        [_4]
 * </pre>
 */
public class ProceduralLayoutGenerator {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final ProceduralLayoutConfig config;

    /**
     * Creates a generator with the given configuration.
     */
    public ProceduralLayoutGenerator(@Nonnull ProceduralLayoutConfig config) {
        this.config = config;
    }

    /**
     * Generates all node positions based on the configuration.
     *
     * @return Map of node ID to position configuration
     */
    @Nonnull
    public Map<String, NodePositionConfig> generateLayout() {
        Map<String, NodePositionConfig> positions = new LinkedHashMap<>();

        // 1. Generate origin (lowered to y=64 in world, which is -1 from baseHeight 65)
        positions.put("origin", new NodePositionConfig("origin", 0, -1, 0));

        // 2. Generate core nodes (compact cluster near origin)
        generateCoreNodes(positions);

        // 3. Generate each arm along its 3D direction
        for (SkillTreeRegion region : SkillTreeRegion.getArms()) {
            generateArmNodes(positions, region);
        }

        // 4. Generate bridge nodes between adjacent arms
        generateBridgeNodes(positions);

        LOGGER.at(Level.INFO).log("Generated %d node positions procedurally (3D cardinal directions)", positions.size());
        return positions;
    }

    // ═══════════════════════════════════════════════════════════════════
    // CORE REGION
    // ═══════════════════════════════════════════════════════════════════

    private void generateCoreNodes(Map<String, NodePositionConfig> positions) {
        // Core region only has the origin node (generated separately)
        // No additional core nodes to generate
    }

    // ═══════════════════════════════════════════════════════════════════
    // ARM GENERATION (3D Cardinal Directions)
    // ═══════════════════════════════════════════════════════════════════

    private void generateArmNodes(Map<String, NodePositionConfig> positions, SkillTreeRegion region) {
        String armName = region.name().toLowerCase();
        int armIndex = region.getArmIndex();
        boolean isOctant = region.isOctant();
        LayoutMath.Direction3D direction = config.getArmDirection(armIndex);

        LOGGER.at(Level.FINE).log("Generating %s arm '%s' along direction (%d, %d, %d)",
            isOctant ? "octant" : "elemental", armName, direction.x(), direction.y(), direction.z());

        // 1. Entry node (octants start further from origin)
        int entryDist = isOctant ? config.getOctantEntryFromOrigin() : config.getEntryFromOrigin();
        generateEntryNode(positions, armName, direction, entryDist);

        // 2. Clusters (4 per arm)
        List<String> clusterNames = config.getArmClusters().getOrDefault(armName, List.of());
        for (int i = 0; i < clusterNames.size() && i < 4; i++) {
            String clusterName = clusterNames.get(i);
            ClusterTemplate.ClusterSide side = (i % 2 == 0) ? ClusterTemplate.ClusterSide.LEFT : ClusterTemplate.ClusterSide.RIGHT;
            generateCluster3D(positions, armName, clusterName, i, direction, side, isOctant);
        }

        // 3. Synergy hub nodes
        generateSynergyNodes3D(positions, armName, direction, isOctant);

        // 4. Keystone nodes
        generateKeystoneNodes3D(positions, armName, direction, isOctant);
    }

    private void generateEntryNode(Map<String, NodePositionConfig> positions, String armName,
                                    LayoutMath.Direction3D direction, int entryDistance) {
        String nodeId = armName + "_entry";
        NodePositionConfig pos = LayoutMath.projectAlongDirection(nodeId, direction, entryDistance);
        positions.put(nodeId, pos);
    }

    private void generateCluster3D(
            Map<String, NodePositionConfig> positions,
            String armName,
            String clusterName,
            int clusterIndex,
            LayoutMath.Direction3D direction,
            ClusterTemplate.ClusterSide side,
            boolean isOctant) {

        String clusterPrefix = armName + "_" + clusterName;

        // Distance along arm direction for this cluster (centralized in getClusterDistanceForArm)
        int distance = getClusterDistanceForArm(clusterIndex, isOctant);

        // Side offset perpendicular to arm direction
        double sideOffset = config.getBranchOffset();
        if (side == ClusterTemplate.ClusterSide.RIGHT) {
            sideOffset = -sideOffset;
        }

        // Generate all nodes in the cluster using the 3D template
        List<NodePositionConfig> clusterPositions = config.getClusterTemplate().generateClusterNodes3D(
            clusterPrefix,
            direction,
            distance,
            sideOffset
        );

        for (NodePositionConfig pos : clusterPositions) {
            positions.put(pos.nodeId(), pos);
        }

        LOGGER.at(Level.FINE).log("  Cluster '%s': distance=%d, side=%s, %d nodes",
            clusterPrefix, distance, side, clusterPositions.size());
    }

    private void generateSynergyNodes3D(Map<String, NodePositionConfig> positions, String armName,
                                         LayoutMath.Direction3D direction, boolean isOctant) {
        int spacing = config.getNodeSpacing();
        double dirMag = direction.magnitude();
        LayoutMath.Direction3D[] perps = direction.getPerpendicularDirections();
        double perp0Mag = perps[0].magnitude();

        // Get notable distance from template (configurable via YAML)
        double notableAlongArm = config.getClusterTemplate().getNotableDistance();
        int synergyExtension = config.getSynergyExtension();

        // Calculate positions for 4 synergy nodes
        for (int i = 0; i < 4; i++) {
            int clusterDist = getClusterDistanceForArm(i, isOctant);
            // notableAlongArm and synergyExtension are local offsets → compensate
            double synergyDist = clusterDist + (notableAlongArm + synergyExtension) * dirMag;

            // Perpendicular offset matches cluster side (alternating left/right)
            ClusterTemplate.ClusterSide side = (i % 2 == 0) ? ClusterTemplate.ClusterSide.LEFT : ClusterTemplate.ClusterSide.RIGHT;
            double perpOffset = (side == ClusterTemplate.ClusterSide.LEFT ? 1 : -1) * config.getBranchOffset() * perp0Mag;

            String nodeId = armName + "_synergy_" + (i + 1);
            NodePositionConfig pos = LayoutMath.projectAlongDirection3D(nodeId, direction, synergyDist, perpOffset, 0);
            positions.put(nodeId, pos);
        }

        // Hub is placed beyond the furthest synergy node to ensure correct node ordering
        double maxSynergyDist = 0;
        for (int i = 0; i < 4; i++) {
            double dist = getClusterDistanceForArm(i, isOctant) + (notableAlongArm + synergyExtension) * dirMag;
            maxSynergyDist = Math.max(maxSynergyDist, dist);
        }
        double hubDist = maxSynergyDist + spacing * dirMag;

        String idHub = armName + "_synergy_hub";
        NodePositionConfig hubPos = LayoutMath.projectAlongDirection(idHub, direction, hubDist);
        positions.put(idHub, hubPos);
    }

    private void generateKeystoneNodes3D(Map<String, NodePositionConfig> positions, String armName,
                                          LayoutMath.Direction3D direction, boolean isOctant) {
        int spacing = config.getNodeSpacing();
        int spread = config.getKeystoneSpread();
        double dirMag = direction.magnitude();
        LayoutMath.Direction3D[] perps = direction.getPerpendicularDirections();
        double perp0Mag = perps[0].magnitude();

        // Get notable distance from template (configurable via YAML)
        double notableAlongArm = config.getClusterTemplate().getNotableDistance();
        int synergyExtension = config.getSynergyExtension();

        double maxSynergyDist = 0;
        for (int i = 0; i < 4; i++) {
            double dist = getClusterDistanceForArm(i, isOctant) + (notableAlongArm + synergyExtension) * dirMag;
            maxSynergyDist = Math.max(maxSynergyDist, dist);
        }
        double hubDist = maxSynergyDist + spacing * dirMag;
        double keystoneDist = hubDist + spacing * 1.5 * dirMag;

        // Compensate spread for perpendicular magnitude
        double compensatedSpread = spread * perp0Mag;

        // Two keystones, spread along first perpendicular direction
        String id1 = armName + "_keystone_1";
        positions.put(id1, LayoutMath.projectAlongDirection3D(id1, direction, keystoneDist, compensatedSpread, 0));

        String id2 = armName + "_keystone_2";
        positions.put(id2, LayoutMath.projectAlongDirection3D(id2, direction, keystoneDist, -compensatedSpread, 0));
    }

    /**
     * Gets the cluster distance for a given index, accounting for octant arm offset.
     *
     * <p>Octant arms use <b>paired</b> clusters: indices 0&amp;1 share one distance
     * (opposite perpendicular sides), and indices 2&amp;3 share another. This ensures
     * both "ways" of a branch start at the same distance from the core, matching the
     * paired layout of the elemental arms.
     */
    private int getClusterDistanceForArm(int clusterIndex, boolean isOctant) {
        if (isOctant) {
            double dirMag = Math.sqrt(3);  // all octant directions have magnitude √3
            int octantClusterStart = config.getOctantEntryFromOrigin() + 30;
            int pairIndex = clusterIndex / 2;  // 0→0, 1→0, 2→1, 3→1
            return (int)(octantClusterStart + pairIndex * config.getClusterSpacing() * dirMag);
        }
        return config.getClusterDistance(clusterIndex);
    }

    // ═══════════════════════════════════════════════════════════════════
    // BRIDGE NODES (3D interpolation between arms)
    // ═══════════════════════════════════════════════════════════════════

    private void generateBridgeNodes(Map<String, NodePositionConfig> positions) {
        // Bridges connect adjacent arms in 3D space
        // Since arms are now in cardinal directions, bridges curve through 3D space

        for (Map.Entry<String, List<String>> entry : config.getBridgeNodes().entrySet()) {
            String bridgeKey = entry.getKey();  // e.g., "fire_lightning"
            List<String> suffixes = entry.getValue();

            String[] arms = bridgeKey.split("_");
            if (arms.length != 2) continue;

            SkillTreeRegion arm1 = SkillTreeRegion.fromString(arms[0]);
            SkillTreeRegion arm2 = SkillTreeRegion.fromString(arms[1]);

            if (arm1.isCore() || arm2.isCore()) continue;

            generateBridgeBetweenArms3D(positions, bridgeKey, arm1, arm2, suffixes);
        }
    }

    private void generateBridgeBetweenArms3D(
            Map<String, NodePositionConfig> positions,
            String bridgePrefix,
            SkillTreeRegion arm1,
            SkillTreeRegion arm2,
            List<String> suffixes) {

        LayoutMath.Direction3D dir1 = config.getArmDirection(arm1.getArmIndex());
        LayoutMath.Direction3D dir2 = config.getArmDirection(arm2.getArmIndex());

        // Bridge at about the level of the last cluster's notable nodes
        double notableAlongArm = config.getClusterTemplate().getNotableDistance();
        int lastClusterDist = config.getClusterDistance(3);  // Last cluster (index 3)
        int distance = (int) (lastClusterDist + notableAlongArm * 0.7);  // Slightly inward

        // Get endpoint positions on each arm
        int[] pos1 = dir1.scale(distance);
        int[] pos2 = dir2.scale(distance);

        int count = suffixes.size();
        for (int i = 0; i < count; i++) {
            String nodeId = "bridge_" + bridgePrefix + "_" + suffixes.get(i);

            // Interpolate position between the two arms
            double t = (i + 1.0) / (count + 1.0);

            // Linear interpolation with slight curve toward center
            double curveFactor = 0.8 + 0.2 * Math.sin(t * Math.PI);  // Curve inward slightly
            int x = (int) Math.round((pos1[0] + t * (pos2[0] - pos1[0])) * curveFactor);
            int y = (int) Math.round((pos1[1] + t * (pos2[1] - pos1[1])) * curveFactor);
            int z = (int) Math.round((pos1[2] + t * (pos2[2] - pos1[2])) * curveFactor);

            positions.put(nodeId, new NodePositionConfig(nodeId, x, y, z));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // STATIC CONVENIENCE
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Generates a complete layout with default configuration.
     *
     * @return Map of node ID to position configuration
     */
    @Nonnull
    public static Map<String, NodePositionConfig> generateDefaultLayout() {
        return new ProceduralLayoutGenerator(new ProceduralLayoutConfig()).generateLayout();
    }

    /**
     * Generates a complete layout from a YAML configuration map.
     *
     * @param yamlData Parsed YAML configuration
     * @return Map of node ID to position configuration
     */
    @Nonnull
    public static Map<String, NodePositionConfig> generateFromYaml(@Nonnull Map<String, Object> yamlData) {
        ProceduralLayoutConfig config = ProceduralLayoutConfig.fromYaml(yamlData);
        return new ProceduralLayoutGenerator(config).generateLayout();
    }
}
