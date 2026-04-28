package io.github.larsonix.trailoforbis.sanctum;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import io.github.larsonix.trailoforbis.skilltree.SkillTreeRegion;
import io.github.larsonix.trailoforbis.skilltree.config.SkillNode;
import io.github.larsonix.trailoforbis.ui.skilltree.NodePositionConfig;
import io.github.larsonix.trailoforbis.ui.skilltree.SkillTreeLayout;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Maps skill tree nodes to 3D world coordinates.
 *
 * <p>Node positions are generated procedurally by {@link io.github.larsonix.trailoforbis.sanctum.layout.ProceduralLayoutGenerator}
 * based on configuration in {@code skill-tree-layout.yml}. The layout follows a 14-arm
 * galaxy pattern (6 elemental + 8 octant) with:
 * <ul>
 *   <li>Origin at center (0, 0, 0)</li>
 *   <li>Core nodes chaining from origin</li>
 *   <li>6 elemental arms along cardinal 3D directions</li>
 *   <li>8 octant arms along diagonal 3D directions (cube corners)</li>
 *   <li>Each arm: entry node → 4 clusters → synergy nodes → keystones</li>
 *   <li>Bridge nodes connecting adjacent arms</li>
 * </ul>
 *
 * <h2>Coordinate Conversion</h2>
 * <p>Layout coordinates (from procedural generator) are converted to world coordinates:
 * <ul>
 *   <li>Layout X → World X (scaled by {@link #LAYOUT_SCALE} × {@link #RADIAL_EXPANSION})</li>
 *   <li>Layout Y → World Z (scaled by {@link #LAYOUT_SCALE} × {@link #RADIAL_EXPANSION})</li>
 *   <li>Layout Z → World Y offset (scaled by {@link #LAYOUT_VERTICAL_SCALE})</li>
 * </ul>
 *
 * <h2>Coordinate System</h2>
 * <ul>
 *   <li>X: Horizontal (East-West)</li>
 *   <li>Y: Vertical (Up-Down)</li>
 *   <li>Z: Horizontal (North-South)</li>
 * </ul>
 *
 * @see SkillTreeRegion
 * @see SkillSanctumNodeSpawner
 * @see io.github.larsonix.trailoforbis.sanctum.layout.ProceduralLayoutGenerator
 */
public class GalaxySpiralLayoutMapper {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // ═══════════════════════════════════════════════════════════════════
    // LAYOUT-BASED POSITIONING (procedurally generated from skill-tree-layout.yml)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Scale factor for converting layout units to world blocks.
     * Reduced to bring parallel cluster branches closer together (~24 blocks apart).
     */
    public static final double LAYOUT_SCALE = 0.07;

    /**
     * Vertical scale factor (layout Z to world Y offset).
     * Increased to make better use of vertical space.
     */
    public static final double LAYOUT_VERTICAL_SCALE = 0.30;

    /**
     * Radial expansion factor applied after initial scaling.
     * Pushes all nodes further from center without changing relative cluster spacing.
     * 1.0 = no expansion, 1.2 = 20% further from center.
     */
    public static final double RADIAL_EXPANSION = 1.2;

    // Note: With 3D cardinal direction layout, region offsets are no longer needed.
    // Each arm extends in its own direction, providing natural separation.

    /**
     * Cached layout instance for position lookups.
     */
    @Nullable
    private static volatile SkillTreeLayout layoutInstance;

    /**
     * Gets or creates the layout instance.
     */
    @Nonnull
    private static SkillTreeLayout getLayout() {
        if (layoutInstance == null) {
            synchronized (GalaxySpiralLayoutMapper.class) {
                if (layoutInstance == null) {
                    layoutInstance = new SkillTreeLayout();
                    LOGGER.at(Level.INFO).log("Loaded skill tree layout with %d positions",
                        layoutInstance.getNodeCount());
                }
            }
        }
        return layoutInstance;
    }

    /**
     * Clears the cached layout, forcing a reload on next access.
     * Call this after modifying skill-tree-layout.yml to pick up changes.
     */
    public static void clearLayoutCache() {
        synchronized (GalaxySpiralLayoutMapper.class) {
            layoutInstance = null;
            LOGGER.at(Level.INFO).log("Layout cache cleared - will reload on next access");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // SPIRAL CONSTANTS (kept for bounds/fallback calculations)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Inner radius of the spiral (blocks from center).
     * Core nodes are placed within this radius.
     */
    public static final double INNER_RADIUS = 8.0;

    /**
     * Outer radius of the spiral (blocks from center).
     * Keystone nodes are placed near this radius.
     */
    public static final double OUTER_RADIUS = 80.0;

    /**
     * Number of full rotations each arm makes from inner to outer radius.
     */
    public static final double SPIRAL_TURNS = 1.5;

    /**
     * Controls how quickly the spiral expands.
     * Values < 1.0 make the spiral tighter near the center.
     */
    public static final double SPIRAL_TIGHTNESS = 0.8;

    /**
     * Base height (Y coordinate) for the skill tree plane.
     */
    public static final double BASE_HEIGHT = 65.0;

    /**
     * Visual Y offset for nodes to center them relative to ropes/chains.
     * Nodes are raised slightly so their visual center aligns with connections.
     */
    public static final double NODE_VISUAL_Y_OFFSET = 0.25;

    /**
     * Vertical amplitude of the helical wave (blocks up/down from base).
     */
    public static final double VERTICAL_AMPLITUDE = 25.0;

    /**
     * Number of vertical waves along each arm (creates helical effect).
     */
    public static final double VERTICAL_WAVES = 2.0;

    /**
     * Number of arms in the galaxy.
     */
    public static final int ARM_COUNT = 14;

    /**
     * Number of nodes per arm (excluding core).
     */
    public static final int NODES_PER_ARM = 42;

    // ═══════════════════════════════════════════════════════════════════
    // PLAYER SPAWN CONSTANTS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Player spawn height - above the galaxy looking down.
     */
    public static final double PLAYER_SPAWN_Y = 125.0;

    /**
     * Player spawn Z offset - behind the center.
     */
    public static final double PLAYER_SPAWN_Z = -50.0;

    /**
     * Player spawn pitch - angled down to view the galaxy.
     */
    public static final double PLAYER_SPAWN_PITCH = -30.0;

    // ═══════════════════════════════════════════════════════════════════
    // CORE CLUSTER CONSTANTS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Radius of the core cluster (Fibonacci sphere distribution).
     */
    public static final double CORE_RADIUS = 6.0;

    /**
     * Height offset for core cluster center.
     */
    public static final double CORE_HEIGHT_OFFSET = 5.0;

    // ═══════════════════════════════════════════════════════════════════
    // NODE INDEX CACHE (for even distribution along arms)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Maps node ID → sequential index within its arm (0..N-1).
     * Used to spread nodes evenly along each spiral arm.
     */
    private static final Map<String, Integer> nodeArmIndices = new ConcurrentHashMap<>();

    /**
     * Maps arm index (1-6) → total number of nodes in that arm.
     * Used to calculate progress as index/totalNodes.
     */
    private static final Map<Integer, Integer> armNodeCounts = new ConcurrentHashMap<>();

    /**
     * Flag to track if node indices have been initialized.
     */
    private static volatile boolean indexCacheInitialized = false;

    /**
     * Initializes the node index cache for even distribution along spiral arms.
     *
     * <p>This method must be called before spawning nodes to ensure they are
     * evenly distributed along each arm. It groups nodes by arm, sorts them
     * by tier then ID for deterministic ordering, and assigns sequential
     * indices 0..N-1 to each node in the arm.
     *
     * @param allNodes Collection of all skill nodes
     */
    public static synchronized void initializeNodeIndices(@Nonnull Collection<SkillNode> allNodes) {
        // Clear existing cache
        nodeArmIndices.clear();
        armNodeCounts.clear();

        // Group nodes by arm (excluding core nodes which use Fibonacci sphere)
        Map<Integer, List<SkillNode>> nodesByArm = new HashMap<>();
        for (SkillNode node : allNodes) {
            SkillTreeRegion region = node.getSkillTreeRegion();
            if (region.isCore()) {
                continue; // Core nodes use separate positioning
            }
            if (node.isStartNode() || "origin".equals(node.getId())) {
                continue; // Origin is always at center
            }
            int armIndex = region.getArmIndex();
            nodesByArm.computeIfAbsent(armIndex, k -> new ArrayList<>()).add(node);
        }

        // Sort each arm's nodes by tier, then by ID for deterministic ordering
        // Place keystones last (at tips of arms)
        for (Map.Entry<Integer, List<SkillNode>> entry : nodesByArm.entrySet()) {
            int armIndex = entry.getKey();
            List<SkillNode> armNodes = entry.getValue();

            // Sort: non-keystones by tier ascending, then keystones at end, then by ID
            armNodes.sort((a, b) -> {
                // Keystones go last
                if (a.isKeystone() != b.isKeystone()) {
                    return a.isKeystone() ? 1 : -1;
                }
                // Then by tier
                int tierCmp = Integer.compare(a.getTier(), b.getTier());
                if (tierCmp != 0) {
                    return tierCmp;
                }
                // Then by ID for determinism
                return a.getId().compareTo(b.getId());
            });

            // Assign sequential index to each node
            for (int i = 0; i < armNodes.size(); i++) {
                nodeArmIndices.put(armNodes.get(i).getId(), i);
            }
            armNodeCounts.put(armIndex, armNodes.size());
        }

        indexCacheInitialized = true;
    }

    /**
     * Calculates the progress (0.0-1.0) for a node along its arm based on index.
     *
     * @param node     The skill node
     * @param armIndex The arm index (1-6)
     * @return Progress value between MIN_PROGRESS and MAX_PROGRESS
     */
    private static double calculateNodeProgress(@Nonnull SkillNode node, int armIndex) {
        // Progress range: nodes should span from near-center to near-tip
        final double MIN_PROGRESS = 0.08;
        final double MAX_PROGRESS = 0.98;

        // Get node's index within its arm
        Integer nodeIndex = nodeArmIndices.get(node.getId());
        if (nodeIndex == null) {
            // Fallback to old tier-based logic if not in cache
            return tierToProgress(node.getTier(), node.isKeystone(), node.isNotable());
        }

        // Get total nodes in this arm
        int totalNodes = armNodeCounts.getOrDefault(armIndex, NODES_PER_ARM);
        if (totalNodes <= 1) {
            return (MIN_PROGRESS + MAX_PROGRESS) / 2.0; // Center of range
        }

        // Map index to progress: index 0 → MIN_PROGRESS, index (total-1) → MAX_PROGRESS
        double progress = MIN_PROGRESS + (MAX_PROGRESS - MIN_PROGRESS) * nodeIndex / (totalNodes - 1);

        return progress;
    }

    // ═══════════════════════════════════════════════════════════════════
    // MAIN API
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Calculates the world position for a node based on its arm and progress.
     *
     * @param armIndex     The arm index (1-6) or 0 for core
     * @param nodeProgress Progress along the arm (0.0 = inner, 1.0 = outer)
     * @return World coordinates as Vector3d
     */
    @Nonnull
    public static Vector3d calculateSpiralPosition(int armIndex, double nodeProgress) {
        if (armIndex == 0 || nodeProgress <= 0.0) {
            // Core node - at center
            return new Vector3d(0, BASE_HEIGHT + CORE_HEIGHT_OFFSET, 0);
        }

        // Clamp progress to valid range
        nodeProgress = Math.max(0.0, Math.min(1.0, nodeProgress));

        // Calculate angle (theta) for this position
        // Each arm starts at a different angle (evenly distributed)
        // Then rotates outward based on progress
        double baseAngle = (2.0 * Math.PI * (armIndex - 1)) / ARM_COUNT;
        double spiralAngle = nodeProgress * SPIRAL_TURNS * 2.0 * Math.PI;
        double theta = baseAngle + spiralAngle;

        // Calculate radius using power curve for tighter inner spiral
        double radius = INNER_RADIUS + (OUTER_RADIUS - INNER_RADIUS) * Math.pow(nodeProgress, SPIRAL_TIGHTNESS);

        // Calculate X/Z from polar coordinates
        double x = radius * Math.cos(theta);
        double z = radius * Math.sin(theta);

        // Calculate Y with helical wave
        double y = BASE_HEIGHT + VERTICAL_AMPLITUDE * Math.sin(nodeProgress * VERTICAL_WAVES * 2.0 * Math.PI);

        return new Vector3d(x, y, z);
    }

    /**
     * Calculates the world position for a skill node.
     *
     * <p>Uses procedurally generated positions from {@link SkillTreeLayout}:
     * <ul>
     *   <li>Layout X → World X (scaled by {@link #LAYOUT_SCALE} × {@link #RADIAL_EXPANSION})</li>
     *   <li>Layout Y → World Z (scaled by {@link #LAYOUT_SCALE} × {@link #RADIAL_EXPANSION})</li>
     *   <li>Layout Z → World Y offset (scaled by {@link #LAYOUT_VERTICAL_SCALE})</li>
     * </ul>
     *
     * <p>Falls back to spiral positioning if node not found in layout.
     *
     * @param node The skill node
     * @return World coordinates for the node
     */
    @Nonnull
    public static Vector3d toWorldPosition(@Nonnull SkillNode node) {
        String nodeId = node.getId();
        SkillTreeLayout layout = getLayout();

        // Check for exported world position first (from in-game layout editor)
        // These are absolute coordinates that bypass procedural generation
        Vector3d exportedPos = layout.getExportedWorldPosition(nodeId);
        if (exportedPos != null) {
            LOGGER.at(Level.FINE).log("Node '%s' using exported position: (%.1f, %.1f, %.1f)",
                nodeId, exportedPos.x, exportedPos.y, exportedPos.z);
            return exportedPos;
        }

        // Try to get position from procedural layout
        NodePositionConfig posConfig = layout.getNodePosition(nodeId);

        if (posConfig != null) {
            // Use layout-based position (3D cardinal direction system)
            // Layout coordinates map directly to world coordinates:
            //   Layout X → World X (East-West)
            //   Layout Y → World Y (Up-Down) - VOID goes up, WATER goes down
            //   Layout Z → World Z (North-South)

            // Get scaling values from layout config (not hardcoded constants)
            double scale = layout.getLayoutScale();
            double expansion = layout.getRadialExpansion();
            double baseY = layout.getBaseHeight();

            // Scale all coordinates uniformly
            double worldX = posConfig.relativeX() * scale * expansion;
            double worldY = baseY + (posConfig.relativeY() * scale * expansion);
            double worldZ = posConfig.relativeZ() * scale * expansion;

            LOGGER.at(Level.FINE).log("Node '%s' using 3D layout: layout(%d,%d,%d) -> world(%.1f,%.1f,%.1f) [scale=%.3f, expansion=%.2f]",
                nodeId, posConfig.relativeX(), posConfig.relativeY(), posConfig.relativeZ(),
                worldX, worldY, worldZ, scale, expansion);

            return new Vector3d(worldX, worldY, worldZ);
        }

        // Fallback to spiral positioning for nodes not in layout
        LOGGER.at(Level.WARNING).log("Node '%s' NOT FOUND in layout (layout has %d positions), using spiral fallback",
            nodeId, layout.getNodeCount());

        SkillTreeRegion region = node.getSkillTreeRegion();

        // Origin node - always at center
        if (node.isStartNode() || "origin".equals(nodeId)) {
            return new Vector3d(0, BASE_HEIGHT + CORE_HEIGHT_OFFSET, 0);
        }

        // Core region nodes - use Fibonacci sphere distribution
        if (region.isCore()) {
            return calculateCorePosition(node);
        }

        // Arm nodes - calculate spiral position based on index (or tier as fallback)
        int armIndex = region.getArmIndex();
        double progress;

        if (indexCacheInitialized) {
            // Use index-based progress for even distribution
            progress = calculateNodeProgress(node, armIndex);
        } else {
            // Fallback to tier-based if not initialized
            progress = tierToProgress(node.getTier(), node.isKeystone(), node.isNotable());
            // Add slight offset based on node ID hash for variety
            progress += getNodeOffset(nodeId);
        }

        return calculateSpiralPosition(armIndex, progress);
    }

    /**
     * Converts a tier to a progress value along the spiral arm.
     */
    private static double tierToProgress(int tier, boolean isKeystone, boolean isNotable) {
        if (isKeystone) {
            return 0.95; // Keystones at the tips
        }

        // Map tiers to progress ranges
        return switch (tier) {
            case 0 -> 0.0;  // Origin
            case 1 -> 0.15; // Inner passives
            case 2 -> 0.30; // Mid-inner passives
            case 3 -> 0.45; // Bridge/travel nodes
            case 4 -> isNotable ? 0.65 : 0.55; // Notable or mid nodes
            case 5 -> 0.80; // Synergy nodes
            case 6 -> 0.95; // Keystones
            default -> 0.50;
        };
    }

    /**
     * Gets a small offset for variety based on node ID.
     * Returns a value between -0.02 and 0.02.
     */
    private static double getNodeOffset(String nodeId) {
        int hash = nodeId.hashCode();
        return ((hash % 100) / 100.0) * 0.04 - 0.02;
    }

    /**
     * Calculates position for a core region node using Fibonacci sphere distribution.
     */
    @Nonnull
    private static Vector3d calculateCorePosition(@Nonnull SkillNode node) {
        // Use node ID hash to determine position index
        int index = Math.abs(node.getId().hashCode() % 20); // Up to 20 core nodes
        int totalPoints = 20;

        // Fibonacci sphere algorithm for even distribution
        double phi = Math.acos(1 - 2.0 * (index + 0.5) / totalPoints);
        double theta = Math.PI * (1 + Math.sqrt(5)) * index;

        double x = CORE_RADIUS * Math.sin(phi) * Math.cos(theta);
        double y = BASE_HEIGHT + CORE_HEIGHT_OFFSET + CORE_RADIUS * Math.cos(phi);
        double z = CORE_RADIUS * Math.sin(phi) * Math.sin(theta);

        return new Vector3d(x, y, z);
    }

    // ═══════════════════════════════════════════════════════════════════
    // BRIDGE POSITIONS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Calculates the world position for a bridge node between two arms.
     *
     * @param arm1Index     First arm index
     * @param arm2Index     Second arm index
     * @param bridgeProgress Position along the bridge (0.0 = near arm1, 1.0 = near arm2)
     * @param radialProgress Progress from center (0.0 = inner, 1.0 = outer)
     * @return World coordinates for the bridge node
     */
    @Nonnull
    public static Vector3d calculateBridgePosition(int arm1Index, int arm2Index,
                                                    double bridgeProgress, double radialProgress) {
        // Get positions on both arms at the same radial progress
        Vector3d pos1 = calculateSpiralPosition(arm1Index, radialProgress);
        Vector3d pos2 = calculateSpiralPosition(arm2Index, radialProgress);

        // Interpolate between the two positions
        return new Vector3d(
            pos1.x + (pos2.x - pos1.x) * bridgeProgress,
            pos1.y + (pos2.y - pos1.y) * bridgeProgress,
            pos1.z + (pos2.z - pos1.z) * bridgeProgress
        );
    }

    // ═══════════════════════════════════════════════════════════════════
    // PLAYER SPAWN
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets the player spawn position for the sanctum.
     *
     * <p>Players spawn above the galaxy at (0, 125, -50) looking down
     * at the center.
     *
     * @return Spawn position as Vector3d
     */
    @Nonnull
    public static Vector3d getPlayerSpawnPosition() {
        return new Vector3d(0, PLAYER_SPAWN_Y, PLAYER_SPAWN_Z);
    }

    /**
     * Gets the player spawn rotation (looking towards center and down).
     *
     * <p>Pitch = -30 degrees (looking down)
     * <p>Yaw = 0 degrees (facing +Z towards center)
     *
     * @return Spawn rotation as Vector3d (pitch, yaw, roll)
     */
    @Nonnull
    public static Vector3d getPlayerSpawnRotation() {
        return new Vector3d(PLAYER_SPAWN_PITCH, 0.0, 0.0);
    }

    // ═══════════════════════════════════════════════════════════════════
    // DISTANCE CALCULATIONS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Calculates the world distance between two nodes.
     *
     * @param node1 First node
     * @param node2 Second node
     * @return Distance in world blocks
     */
    public static double getWorldDistance(@Nonnull SkillNode node1, @Nonnull SkillNode node2) {
        Vector3d pos1 = toWorldPosition(node1);
        Vector3d pos2 = toWorldPosition(node2);
        return pos1.distanceTo(pos2);
    }

    // ═══════════════════════════════════════════════════════════════════
    // SIZE CALCULATIONS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Scale for basic/small passive nodes.
     */
    public static final float SCALE_BASIC = 2.5f;

    /**
     * Scale for notable nodes.
     */
    public static final float SCALE_NOTABLE = 4.0f;

    /**
     * Scale for keystone nodes.
     */
    public static final float SCALE_KEYSTONE = 6.0f;

    /**
     * Scale for the origin node.
     */
    public static final float SCALE_ORIGIN = 4.0f;

    /**
     * Scale for the galactic core diamond.
     */
    public static final float SCALE_CORE_DIAMOND = 10.0f;

    /**
     * Gets the scale for a node based on its type.
     *
     * @param node The skill node
     * @return Scale factor
     */
    public static float getNodeScale(@Nonnull SkillNode node) {
        if (node.isStartNode() || "origin".equals(node.getId())) {
            return SCALE_ORIGIN;
        }
        if (node.isKeystone()) {
            return SCALE_KEYSTONE;
        }
        if (node.isNotable()) {
            return SCALE_NOTABLE;
        }
        return SCALE_BASIC;
    }

    // ═══════════════════════════════════════════════════════════════════
    // BOUNDS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets the maximum world radius of the skill tree from origin.
     *
     * @return Maximum radius in world blocks
     */
    public static double getMaxTreeRadius() {
        return OUTER_RADIUS + 5.0; // Small padding
    }

    /**
     * Gets the recommended sanctum arena radius.
     *
     * @return Arena radius in world blocks
     */
    public static double getSanctumRadius() {
        return OUTER_RADIUS + 25.0;
    }

    /**
     * Gets the minimum Y coordinate (lowest point of the helix).
     */
    public static double getMinY() {
        return BASE_HEIGHT - VERTICAL_AMPLITUDE;
    }

    /**
     * Gets the maximum Y coordinate (highest point of the helix).
     */
    public static double getMaxY() {
        return BASE_HEIGHT + VERTICAL_AMPLITUDE + CORE_HEIGHT_OFFSET;
    }
}
