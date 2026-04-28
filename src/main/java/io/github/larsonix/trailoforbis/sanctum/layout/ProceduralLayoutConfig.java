package io.github.larsonix.trailoforbis.sanctum.layout;

import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Configuration POJO for procedural skill tree layout generation.
 *
 * <p>Holds all parameters needed to compute node positions algorithmically:
 * <ul>
 *   <li>Scaling factors (layout to world conversion)</li>
 *   <li>Arm configuration (count, angles)</li>
 *   <li>Distance parameters (entry, cluster, synergy, keystone)</li>
 *   <li>Cluster template (6-node pattern)</li>
 *   <li>Vertical progression settings</li>
 * </ul>
 */
public class ProceduralLayoutConfig {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // ═══════════════════════════════════════════════════════════════════
    // SCALING
    // ═══════════════════════════════════════════════════════════════════

    private double layoutScale = 0.07;
    private double verticalScale = 0.30;
    private double radialExpansion = 1.2;
    private double baseHeight = 65.0;

    // ═══════════════════════════════════════════════════════════════════
    // ARM CONFIGURATION (3D Cardinal Directions)
    // ═══════════════════════════════════════════════════════════════════

    private int armCount = 14;

    /**
     * Per-arm 3D direction vectors (indexed 1-14).
     * Arms 1-6: cardinal directions (elemental arms).
     * Arms 7-14: diagonal directions (octant arms at cube corners).
     *
     * <pre>
     *         VOID (+Y up)
     *            |
     *            |
     *  WATER ----+---- FIRE
     *   (-X)    /|\    (+X)
     *          / | \
     *    EARTH   |  LIGHTNING
     *    (-Z)    |    (+Z)
     *            |
     *         WIND (-Y down)
     *
     *  Octant arms: 8 diagonal cube corners between elemental axes
     * </pre>
     */
    private Map<Integer, LayoutMath.Direction3D> armDirections = Map.ofEntries(
        Map.entry(1, LayoutMath.Direction3D.PLUS_X),                    // FIRE: +X (East)
        Map.entry(2, LayoutMath.Direction3D.MINUS_X),                   // WATER: -X (West)
        Map.entry(3, LayoutMath.Direction3D.PLUS_Z),                    // LIGHTNING: +Z (South)
        Map.entry(4, LayoutMath.Direction3D.MINUS_Z),                   // EARTH: -Z (North)
        Map.entry(5, LayoutMath.Direction3D.PLUS_Y),                    // VOID: +Y (Up)
        Map.entry(6, LayoutMath.Direction3D.MINUS_Y),                   // WIND: -Y (Down)
        Map.entry(7, LayoutMath.Direction3D.PLUS_X_PLUS_Y_PLUS_Z),     // HAVOC: +X +Y +Z
        Map.entry(8, LayoutMath.Direction3D.PLUS_X_PLUS_Y_MINUS_Z),    // JUGGERNAUT: +X +Y -Z
        Map.entry(9, LayoutMath.Direction3D.PLUS_X_MINUS_Y_PLUS_Z),    // STRIKER: +X -Y +Z
        Map.entry(10, LayoutMath.Direction3D.PLUS_X_MINUS_Y_MINUS_Z),  // WARDEN: +X -Y -Z
        Map.entry(11, LayoutMath.Direction3D.MINUS_X_PLUS_Y_PLUS_Z),   // WARLOCK: -X +Y +Z
        Map.entry(12, LayoutMath.Direction3D.MINUS_X_PLUS_Y_MINUS_Z),  // LICH: -X +Y -Z
        Map.entry(13, LayoutMath.Direction3D.MINUS_X_MINUS_Y_PLUS_Z),  // TEMPEST: -X -Y +Z
        Map.entry(14, LayoutMath.Direction3D.MINUS_X_MINUS_Y_MINUS_Z)  // SENTINEL: -X -Y -Z
    );

    // ═══════════════════════════════════════════════════════════════════
    // DISTANCES (in layout units, before scaling)
    // ═══════════════════════════════════════════════════════════════════

    private int entryFromOrigin = 70;
    private int octantEntryFromOrigin = 200;  // Octant arms start further from origin
    private int clusterStart = 100;
    private int clusterSpacing = 95;   // Compact spacing for 75-unit clusters
    private int nodeSpacing = 30;      // Tighter node spacing
    private int branchOffset = 25;     // Matches cluster perpendicular spread

    // ═══════════════════════════════════════════════════════════════════
    // END NODES (synergy & keystone)
    // ═══════════════════════════════════════════════════════════════════

    private int keystoneSpread = 30;      // Perpendicular spread for keystone pair
    private int synergyExtension = 30;    // Extension from notable to synergy nodes

    // ═══════════════════════════════════════════════════════════════════
    // CORE REGION (special handling - not on a cardinal arm)
    // ═══════════════════════════════════════════════════════════════════

    private int coreChainSpacing = 50;

    // ═══════════════════════════════════════════════════════════════════
    // CLUSTER TEMPLATE
    // ═══════════════════════════════════════════════════════════════════

    private ClusterTemplate clusterTemplate = new ClusterTemplate();

    // ═══════════════════════════════════════════════════════════════════
    // CLUSTER NAMES PER ARM (discovered from skill-tree.yml)
    // ═══════════════════════════════════════════════════════════════════

    private Map<String, List<String>> armClusters = Map.ofEntries(
        // Elemental arms (6)
        Map.entry("fire", List.of("fury", "inferno", "bloodlust", "berserker")),
        Map.entry("water", List.of("frostbite", "precision", "evasion", "shatter")),
        Map.entry("lightning", List.of("storm", "velocity", "chain", "overcharge")),
        Map.entry("earth", List.of("vitality", "regen", "fortitude", "guardian")),
        Map.entry("void", List.of("corruption", "entropy", "drain", "oblivion")),
        Map.entry("wind", List.of("flow", "arcane", "adaptation", "depth")),
        // Octant arms (8) — cluster names from design doc Section 4
        Map.entry("havoc", List.of("carnage", "frenzy", "ruin", "mayhem")),
        Map.entry("juggernaut", List.of("conquest", "dominion", "bloodforge", "tyrant")),
        Map.entry("striker", List.of("quicksilver", "precision", "ambush", "flurry")),
        Map.entry("warden", List.of("garrison", "outrider", "ironclad", "palisade")),
        Map.entry("warlock", List.of("hex", "ritual", "malice", "damnation")),
        Map.entry("lich", List.of("grasp", "crypt", "requiem", "decay")),
        Map.entry("tempest", List.of("squall", "tailwind", "cyclone", "maelstrom")),
        Map.entry("sentinel", List.of("aegis", "vigilance", "restoration", "haven"))
    );

    /**
     * Bridge nodes connect adjacent arms.
     * Format: Map from "arm1_arm2" to list of bridge suffixes.
     */
    private Map<String, List<String>> bridgeNodes = Map.ofEntries(
        Map.entry("fire_lightning", List.of("1", "2", "3")),
        Map.entry("lightning_water", List.of("1", "2", "3")),
        Map.entry("water_earth", List.of("1", "2", "3")),
        Map.entry("earth_fire", List.of("1", "2", "3")),
        Map.entry("fire_void", List.of("1", "2", "3")),
        Map.entry("lightning_void", List.of("1", "2", "3")),
        Map.entry("water_void", List.of("1", "2", "3")),
        Map.entry("earth_void", List.of("1", "2", "3")),
        Map.entry("fire_wind", List.of("1", "2", "3")),
        Map.entry("lightning_wind", List.of("1", "2", "3")),
        Map.entry("water_wind", List.of("1", "2", "3")),
        Map.entry("earth_wind", List.of("1", "2", "3"))
    );

    // ═══════════════════════════════════════════════════════════════════
    // ACCESSORS
    // ═══════════════════════════════════════════════════════════════════

    public double getLayoutScale() { return layoutScale; }
    public void setLayoutScale(double layoutScale) { this.layoutScale = layoutScale; }

    public double getVerticalScale() { return verticalScale; }
    public void setVerticalScale(double verticalScale) { this.verticalScale = verticalScale; }

    public double getRadialExpansion() { return radialExpansion; }
    public void setRadialExpansion(double radialExpansion) { this.radialExpansion = radialExpansion; }

    public double getBaseHeight() { return baseHeight; }
    public void setBaseHeight(double baseHeight) { this.baseHeight = baseHeight; }

    public int getArmCount() { return armCount; }
    public void setArmCount(int armCount) { this.armCount = armCount; }

    @Nonnull
    public Map<Integer, LayoutMath.Direction3D> getArmDirections() { return armDirections; }
    public void setArmDirections(@Nonnull Map<Integer, LayoutMath.Direction3D> armDirections) { this.armDirections = armDirections; }

    public int getEntryFromOrigin() { return entryFromOrigin; }
    public void setEntryFromOrigin(int entryFromOrigin) { this.entryFromOrigin = entryFromOrigin; }

    public int getOctantEntryFromOrigin() { return octantEntryFromOrigin; }
    public void setOctantEntryFromOrigin(int octantEntryFromOrigin) { this.octantEntryFromOrigin = octantEntryFromOrigin; }

    public int getClusterStart() { return clusterStart; }
    public void setClusterStart(int clusterStart) { this.clusterStart = clusterStart; }

    public int getClusterSpacing() { return clusterSpacing; }
    public void setClusterSpacing(int clusterSpacing) { this.clusterSpacing = clusterSpacing; }

    public int getNodeSpacing() { return nodeSpacing; }
    public void setNodeSpacing(int nodeSpacing) { this.nodeSpacing = nodeSpacing; }

    public int getBranchOffset() { return branchOffset; }
    public void setBranchOffset(int branchOffset) { this.branchOffset = branchOffset; }

    public int getKeystoneSpread() { return keystoneSpread; }
    public void setKeystoneSpread(int keystoneSpread) { this.keystoneSpread = keystoneSpread; }

    public int getSynergyExtension() { return synergyExtension; }
    public void setSynergyExtension(int synergyExtension) { this.synergyExtension = synergyExtension; }

    public int getCoreChainSpacing() { return coreChainSpacing; }
    public void setCoreChainSpacing(int coreChainSpacing) { this.coreChainSpacing = coreChainSpacing; }

    @Nonnull
    public ClusterTemplate getClusterTemplate() { return clusterTemplate; }
    public void setClusterTemplate(@Nonnull ClusterTemplate clusterTemplate) { this.clusterTemplate = clusterTemplate; }

    @Nonnull
    public Map<String, List<String>> getArmClusters() { return armClusters; }
    public void setArmClusters(@Nonnull Map<String, List<String>> armClusters) { this.armClusters = armClusters; }

    @Nonnull
    public Map<String, List<String>> getBridgeNodes() { return bridgeNodes; }
    public void setBridgeNodes(@Nonnull Map<String, List<String>> bridgeNodes) { this.bridgeNodes = bridgeNodes; }

    // ═══════════════════════════════════════════════════════════════════
    // COMPUTED VALUES
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets the 3D direction vector for a specific arm (1-6).
     *
     * @param armIndex Arm index (1-6)
     * @return Direction3D for the arm
     */
    @Nonnull
    public LayoutMath.Direction3D getArmDirection(int armIndex) {
        return armDirections.getOrDefault(armIndex, LayoutMath.Direction3D.PLUS_X);
    }

    /**
     * Gets the distance from origin for a cluster based on its index.
     *
     * @param clusterIndex Cluster index within arm (0-3)
     * @return Distance from origin
     */
    public int getClusterDistance(int clusterIndex) {
        return clusterStart + clusterIndex * clusterSpacing;
    }

    // ═══════════════════════════════════════════════════════════════════
    // YAML LOADING
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Creates a config from parsed YAML data.
     *
     * @param data Map loaded from YAML file
     * @return ProceduralLayoutConfig with values from data
     */
    @SuppressWarnings("unchecked")
    @Nonnull
    public static ProceduralLayoutConfig fromYaml(@Nonnull Map<String, Object> data) {
        ProceduralLayoutConfig config = new ProceduralLayoutConfig();

        // Load scaling section
        if (data.containsKey("scaling")) {
            Map<String, Object> scaling = (Map<String, Object>) data.get("scaling");
            config.layoutScale = getDouble(scaling, "layoutScale", config.layoutScale);
            config.verticalScale = getDouble(scaling, "verticalScale", config.verticalScale);
            config.radialExpansion = getDouble(scaling, "radialExpansion", config.radialExpansion);
            config.baseHeight = getDouble(scaling, "baseHeight", config.baseHeight);
        }

        // Load arms section
        if (data.containsKey("arms")) {
            Map<String, Object> arms = (Map<String, Object>) data.get("arms");
            config.armCount = getInt(arms, "count", config.armCount);
            // Note: arm directions are hardcoded to cardinal 3D directions
        }

        // Load distances section
        if (data.containsKey("distances")) {
            Map<String, Object> distances = (Map<String, Object>) data.get("distances");
            config.entryFromOrigin = getInt(distances, "entryFromOrigin", config.entryFromOrigin);
            config.clusterStart = getInt(distances, "clusterStart", config.clusterStart);
            config.clusterSpacing = getInt(distances, "clusterSpacing", config.clusterSpacing);
            config.nodeSpacing = getInt(distances, "nodeSpacing", config.nodeSpacing);
            config.branchOffset = getInt(distances, "branchOffset", config.branchOffset);
        }

        // Load octant section
        if (data.containsKey("octant")) {
            Map<String, Object> octant = (Map<String, Object>) data.get("octant");
            config.octantEntryFromOrigin = getInt(octant, "entryFromOrigin", config.octantEntryFromOrigin);
        }

        // Load end nodes section (synergy & keystone settings)
        if (data.containsKey("endNodes")) {
            Map<String, Object> endNodes = (Map<String, Object>) data.get("endNodes");
            config.keystoneSpread = getInt(endNodes, "keystoneSpread", config.keystoneSpread);
            config.synergyExtension = getInt(endNodes, "synergyExtension", config.synergyExtension);
        }

        // Load core section
        if (data.containsKey("core")) {
            Map<String, Object> core = (Map<String, Object>) data.get("core");
            config.coreChainSpacing = getInt(core, "chainSpacing", config.coreChainSpacing);
        }

        // Load cluster template
        if (data.containsKey("clusterTemplate")) {
            Map<String, Map<String, Object>> templateData = (Map<String, Map<String, Object>>) data.get("clusterTemplate");
            config.clusterTemplate = ClusterTemplate.fromConfig(templateData);
        }

        // Load arm clusters (override defaults if specified)
        if (data.containsKey("armClusters")) {
            Map<String, List<String>> clusters = (Map<String, List<String>>) data.get("armClusters");
            config.armClusters = clusters;
        }

        // Load bridge nodes (override defaults if specified)
        if (data.containsKey("bridgeNodes")) {
            Map<String, List<String>> bridges = (Map<String, List<String>>) data.get("bridgeNodes");
            config.bridgeNodes = bridges;
        }

        return config;
    }

    private static double getDouble(Map<String, Object> map, String key, double defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return defaultValue;
    }

    private static int getInt(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }
}
