package io.github.larsonix.trailoforbis.ui.skilltree;

import com.hypixel.hytale.logger.HytaleLogger;
import io.github.larsonix.trailoforbis.sanctum.layout.ProceduralLayoutConfig;
import io.github.larsonix.trailoforbis.sanctum.layout.ProceduralLayoutGenerator;
import io.github.larsonix.trailoforbis.skilltree.config.SkillNode;
import org.yaml.snakeyaml.Yaml;

import com.hypixel.hytale.math.vector.Vector3d;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Manages skill tree node positions for the unified skill tree UI.
 *
 * <p>Positions are generated procedurally from configuration parameters
 * in skill-tree-layout.yml. The YAML file defines the layout algorithm
 * parameters rather than individual node positions.
 *
 * <p>The config is loaded from the server's config directory if available,
 * falling back to bundled resources.
 *
 * @see ProceduralLayoutGenerator
 * @see ProceduralLayoutConfig
 */
public class SkillTreeLayout {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String CONFIG_FILENAME = "skill-tree-layout.yml";

    /**
     * External config directory path. Set this before creating SkillTreeLayout
     * instances to load from server config instead of bundled resources.
     */
    @Nullable
    private static volatile Path configDirectory;

    // Canvas dimensions (for 2D UI rendering)
    private int canvasWidth = 1200;
    private int canvasHeight = 1000;
    private int centerX = 600;
    private int centerY = 500;

    // Node sizes by tier
    private int basicWidth = 44;
    private int basicHeight = 44;
    private int notableWidth = 54;
    private int notableHeight = 54;
    private int keystoneWidth = 64;
    private int keystoneHeight = 64;
    private int originWidth = 70;
    private int originHeight = 70;

    // Scaling parameters (for 3D world positioning)
    private double layoutScale = 0.07;
    private double verticalScale = 0.30;
    private double radialExpansion = 1.2;
    private double baseHeight = 65.0;

    // Position data for each node (procedurally generated)
    private final Map<String, NodePositionConfig> nodePositions = new HashMap<>();

    // Exported world positions (absolute, no scaling needed)
    // If a node is in this map, use these coordinates directly instead of procedural
    private final Map<String, Vector3d> exportedWorldPositions = new HashMap<>();
    private boolean usingExportedPositions = false;
    private static final String POSITIONS_FILENAME = "skill-tree-positions.yml";  // Simple flat format
    private static final String EXPORTED_FILENAME = "exported-node-positions.yml"; // Legacy grouped format

    /**
     * Sets the external config directory for loading layout configuration.
     * Call this before creating SkillTreeLayout instances.
     *
     * @param configDir Path to the server's config directory
     */
    public static void setConfigDirectory(@Nullable Path configDir) {
        configDirectory = configDir;
        LOGGER.at(Level.INFO).log("Layout config directory set to: %s", configDir);
    }

    /**
     * Clears the cached layout instance, forcing a reload on next access.
     * Call this after changing the config file to pick up changes.
     */
    public static void clearCache() {
        // The GalaxySpiralLayoutMapper has its own cache that also needs clearing
        LOGGER.at(Level.INFO).log("Layout cache cleared - will reload on next access");
    }

    /**
     * Creates a new SkillTreeLayout by loading config and generating positions procedurally.
     */
    public SkillTreeLayout() {
        loadConfig();
    }

    /**
     * Loads the layout configuration from the external config directory first,
     * falling back to bundled resources if not found.
     */
    @SuppressWarnings("unchecked")
    private void loadConfig() {
        Map<String, Object> data = null;

        // Try loading from external config directory first
        if (configDirectory != null) {
            Path configPath = configDirectory.resolve(CONFIG_FILENAME);
            if (Files.exists(configPath)) {
                try (InputStream input = Files.newInputStream(configPath)) {
                    Yaml yaml = new Yaml();
                    data = yaml.load(input);
                    LOGGER.at(Level.INFO).log("Loaded layout config from: %s", configPath);
                } catch (Exception e) {
                    LOGGER.at(Level.WARNING).withCause(e).log(
                        "Failed to load external config %s, trying bundled resource", configPath);
                }
            } else {
                LOGGER.at(Level.INFO).log("External config not found at %s, using bundled resource", configPath);
            }
        }

        // Fall back to bundled resource
        if (data == null) {
            try (InputStream input = getClass().getClassLoader()
                    .getResourceAsStream("config/" + CONFIG_FILENAME)) {
                if (input == null) {
                    LOGGER.at(Level.SEVERE).log("%s not found in resources, using defaults", CONFIG_FILENAME);
                    generateDefaultLayout();
                    return;
                }
                Yaml yaml = new Yaml();
                data = yaml.load(input);
                LOGGER.at(Level.INFO).log("Loaded layout config from bundled resources");
            } catch (Exception e) {
                LOGGER.at(Level.SEVERE).withCause(e).log("Error loading layout config, using defaults");
                generateDefaultLayout();
                return;
            }
        }

        // Load canvas dimensions (for 2D UI)
        loadCanvasConfig(data);

        // Load node sizes
        loadNodeSizes(data);

        // Load scaling parameters (for 3D world positioning)
        loadScalingConfig(data);

        // Try to load exported positions first (from in-game layout editor)
        boolean loadedExported = loadExportedPositions();

        // Generate procedural layout (used as fallback for missing nodes)
        generateProceduralLayout(data);

        if (loadedExported) {
            LOGGER.at(Level.INFO).log("Using %d exported world positions (procedural fallback for %d nodes)",
                exportedWorldPositions.size(), nodePositions.size());
        } else {
            LOGGER.at(Level.INFO).log("Generated %d node positions procedurally", nodePositions.size());
        }
    }

    @SuppressWarnings("unchecked")
    private void loadCanvasConfig(Map<String, Object> data) {
        if (data.containsKey("canvas")) {
            Map<String, Object> canvas = (Map<String, Object>) data.get("canvas");
            canvasWidth = getInt(canvas, "width", 1200);
            canvasHeight = getInt(canvas, "height", 1000);
            centerX = getInt(canvas, "centerX", 600);
            centerY = getInt(canvas, "centerY", 500);
        }
    }

    @SuppressWarnings("unchecked")
    private void loadNodeSizes(Map<String, Object> data) {
        if (data.containsKey("nodeSizes")) {
            Map<String, Object> sizes = (Map<String, Object>) data.get("nodeSizes");

            if (sizes.containsKey("basic")) {
                Map<String, Object> basic = (Map<String, Object>) sizes.get("basic");
                basicWidth = getInt(basic, "width", 44);
                basicHeight = getInt(basic, "height", 44);
            }
            if (sizes.containsKey("notable")) {
                Map<String, Object> notable = (Map<String, Object>) sizes.get("notable");
                notableWidth = getInt(notable, "width", 54);
                notableHeight = getInt(notable, "height", 54);
            }
            if (sizes.containsKey("keystone")) {
                Map<String, Object> keystone = (Map<String, Object>) sizes.get("keystone");
                keystoneWidth = getInt(keystone, "width", 64);
                keystoneHeight = getInt(keystone, "height", 64);
            }
            if (sizes.containsKey("origin")) {
                Map<String, Object> origin = (Map<String, Object>) sizes.get("origin");
                originWidth = getInt(origin, "width", 70);
                originHeight = getInt(origin, "height", 70);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void loadScalingConfig(Map<String, Object> data) {
        if (data.containsKey("scaling")) {
            Map<String, Object> scaling = (Map<String, Object>) data.get("scaling");
            layoutScale = getDouble(scaling, "layoutScale", 0.07);
            verticalScale = getDouble(scaling, "verticalScale", 0.30);
            radialExpansion = getDouble(scaling, "radialExpansion", 1.2);
            baseHeight = getDouble(scaling, "baseHeight", 65.0);

            LOGGER.at(Level.INFO).log("Scaling config: layoutScale=%.3f, radialExpansion=%.2f, baseHeight=%.1f",
                layoutScale, radialExpansion, baseHeight);
        }
    }

    private void generateProceduralLayout(@Nonnull Map<String, Object> data) {
        Map<String, NodePositionConfig> generated = ProceduralLayoutGenerator.generateFromYaml(data);
        nodePositions.putAll(generated);
    }

    /**
     * Attempts to load node positions from manual config or exported file.
     *
     * <p>Checks for position files in order:
     * <ol>
     *   <li>skill-tree-positions.yml - Simple flat format (preferred)</li>
     *   <li>exported-node-positions.yml - Legacy grouped format from in-game export</li>
     * </ol>
     *
     * <p>Loaded positions are absolute world coordinates that bypass
     * the procedural layout and scaling calculations.
     *
     * @return true if positions were loaded from file
     */
    @SuppressWarnings("unchecked")
    private boolean loadExportedPositions() {
        // Try disk first (external config directory)
        if (configDirectory != null) {
            // Try simple flat format first (skill-tree-positions.yml)
            Path positionsPath = configDirectory.resolve(POSITIONS_FILENAME);
            if (Files.exists(positionsPath)) {
                if (loadFlatPositions(positionsPath)) {
                    return true;
                }
            }

            // Fall back to legacy grouped format (exported-node-positions.yml)
            Path exportPath = configDirectory.resolve(EXPORTED_FILENAME);
            if (Files.exists(exportPath)) {
                if (loadGroupedPositions(exportPath)) {
                    return true;
                }
            }
        }

        // Fall back to bundled JAR resource (first install — no disk config yet)
        if (loadBundledPositions()) {
            return true;
        }

        LOGGER.at(Level.FINE).log("No custom positions file found, using procedural layout");
        return false;
    }

    /**
     * Loads positions from the bundled JAR resource (config/skill-tree-positions.yml).
     * Used on first install when no external config directory exists yet.
     */
    @SuppressWarnings("unchecked")
    private boolean loadBundledPositions() {
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream("config/" + POSITIONS_FILENAME)) {
            if (input == null) {
                return false;
            }

            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(input);
            if (data == null || !data.containsKey("nodes")) {
                return false;
            }

            Map<String, Object> nodesSection = (Map<String, Object>) data.get("nodes");
            int loadedCount = 0;

            for (Map.Entry<String, Object> entry : nodesSection.entrySet()) {
                String nodeId = entry.getKey();
                if (nodeId.startsWith("_")) continue;
                if (!(entry.getValue() instanceof Map)) continue;

                Map<String, Object> coords = (Map<String, Object>) entry.getValue();
                if (coords.containsKey("x") && coords.containsKey("y") && coords.containsKey("z")) {
                    double x = getDouble(coords, "x", 0.0);
                    double y = getDouble(coords, "y", 65.0);
                    double z = getDouble(coords, "z", 0.0);
                    exportedWorldPositions.put(nodeId, new Vector3d(x, y, z));
                    loadedCount++;
                }
            }

            if (loadedCount > 0) {
                LOGGER.at(Level.INFO).log("Loaded %d exported positions from bundled JAR resource", loadedCount);
                return true;
            }
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e).log("Failed to load bundled positions resource");
        }
        return false;
    }

    /**
     * Loads positions from flat format (node_id directly under nodes:).
     */
    @SuppressWarnings("unchecked")
    private boolean loadFlatPositions(Path path) {
        try (InputStream input = Files.newInputStream(path)) {
            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(input);

            if (data == null || !data.containsKey("nodes")) {
                LOGGER.at(Level.WARNING).log("Positions file is empty or malformed: %s", path);
                return false;
            }

            Map<String, Object> nodesSection = (Map<String, Object>) data.get("nodes");
            int loadedCount = 0;

            for (Map.Entry<String, Object> entry : nodesSection.entrySet()) {
                String nodeId = entry.getKey();
                if (nodeId.startsWith("_")) continue; // Skip metadata keys

                if (!(entry.getValue() instanceof Map)) {
                    continue;
                }

                Map<String, Object> coords = (Map<String, Object>) entry.getValue();

                // Check if this is a flat node entry (has x, y, z directly)
                if (coords.containsKey("x") && coords.containsKey("y") && coords.containsKey("z")) {
                    double x = getDouble(coords, "x", 0.0);
                    double y = getDouble(coords, "y", 65.0);
                    double z = getDouble(coords, "z", 0.0);
                    exportedWorldPositions.put(nodeId, new Vector3d(x, y, z));
                    loadedCount++;
                }
            }

            if (loadedCount > 0) {
                usingExportedPositions = true;
                LOGGER.at(Level.INFO).log("Loaded %d node positions from %s (flat format)", loadedCount, path);
                return true;
            }

        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e).log("Failed to load positions from %s", path);
        }

        return false;
    }

    /**
     * Loads positions from grouped format (nodes grouped by region).
     */
    @SuppressWarnings("unchecked")
    private boolean loadGroupedPositions(Path path) {
        try (InputStream input = Files.newInputStream(path)) {
            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(input);

            if (data == null || !data.containsKey("nodes")) {
                LOGGER.at(Level.WARNING).log("Exported positions file is empty or malformed: %s", path);
                return false;
            }

            Map<String, Object> nodesSection = (Map<String, Object>) data.get("nodes");
            int loadedCount = 0;

            // Nodes are grouped by region
            for (Map.Entry<String, Object> regionEntry : nodesSection.entrySet()) {
                if (!(regionEntry.getValue() instanceof Map)) {
                    continue;
                }

                Map<String, Object> regionNodes = (Map<String, Object>) regionEntry.getValue();
                for (Map.Entry<String, Object> nodeEntry : regionNodes.entrySet()) {
                    String nodeId = nodeEntry.getKey();
                    if (!(nodeEntry.getValue() instanceof Map)) {
                        continue;
                    }

                    Map<String, Object> coords = (Map<String, Object>) nodeEntry.getValue();
                    double x = getDouble(coords, "x", 0.0);
                    double y = getDouble(coords, "y", 65.0);
                    double z = getDouble(coords, "z", 0.0);

                    exportedWorldPositions.put(nodeId, new Vector3d(x, y, z));
                    loadedCount++;
                }
            }

            if (loadedCount > 0) {
                usingExportedPositions = true;
                LOGGER.at(Level.INFO).log("Loaded %d node positions from %s (grouped format)", loadedCount, path);
                return true;
            }

        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e).log("Failed to load positions from %s", path);
        }

        return false;
    }

    private void generateDefaultLayout() {
        Map<String, NodePositionConfig> generated = ProceduralLayoutGenerator.generateDefaultLayout();
        nodePositions.putAll(generated);
    }

    /**
     * Gets an integer value from a map with a default fallback.
     */
    private int getInt(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }

    /**
     * Gets a double value from a map with a default fallback.
     */
    private double getDouble(Map<String, Object> map, String key, double defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return defaultValue;
    }

    // ═══════════════════════════════════════════════════════════════════
    // PUBLIC ACCESSORS
    // ═══════════════════════════════════════════════════════════════════

    public int getCanvasWidth() {
        return canvasWidth;
    }

    public int getCanvasHeight() {
        return canvasHeight;
    }

    public int getCenterX() {
        return centerX;
    }

    public int getCenterY() {
        return centerY;
    }

    // ═══════════════════════════════════════════════════════════════════
    // SCALING ACCESSORS (for 3D world positioning)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets the layout scale factor for converting layout units to world blocks.
     */
    public double getLayoutScale() {
        return layoutScale;
    }

    /**
     * Gets the vertical scale factor (layout Y to world Y offset).
     */
    public double getVerticalScale() {
        return verticalScale;
    }

    /**
     * Gets the radial expansion factor.
     * Pushes all nodes further from center without changing relative spacing.
     */
    public double getRadialExpansion() {
        return radialExpansion;
    }

    /**
     * Gets the base world Y height for the origin node.
     */
    public double getBaseHeight() {
        return baseHeight;
    }

    // ═══════════════════════════════════════════════════════════════════
    // EXPORTED POSITIONS (from in-game layout editor)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Checks if we have exported world positions loaded.
     *
     * @return true if using manually positioned nodes
     */
    public boolean isUsingExportedPositions() {
        return usingExportedPositions;
    }

    /**
     * Gets the exported world position for a node, if available.
     *
     * <p>These positions are absolute world coordinates that bypass
     * the procedural layout calculations entirely.
     *
     * @param nodeId The node ID
     * @return World position, or null if not exported
     */
    @Nullable
    public Vector3d getExportedWorldPosition(String nodeId) {
        return exportedWorldPositions.get(nodeId);
    }

    /**
     * Checks if a specific node has an exported position.
     *
     * @param nodeId The node ID
     * @return true if this node has an exported world position
     */
    public boolean hasExportedPosition(String nodeId) {
        return exportedWorldPositions.containsKey(nodeId);
    }

    /**
     * Gets the position configuration for a node.
     *
     * @param nodeId The node ID from skill-tree.yml
     * @return The position config, or null if not found
     */
    @Nullable
    public NodePositionConfig getNodePosition(String nodeId) {
        return nodePositions.get(nodeId);
    }

    /**
     * Gets the absolute X position for a node on the canvas.
     *
     * @param nodeId The node ID
     * @return The absolute X coordinate, or centerX if not found
     */
    public int getAbsoluteX(String nodeId) {
        NodePositionConfig pos = nodePositions.get(nodeId);
        return pos != null ? pos.getAbsoluteX(centerX) : centerX;
    }

    /**
     * Gets the absolute Y position for a node on the canvas.
     *
     * @param nodeId The node ID
     * @return The absolute Y coordinate, or centerY if not found
     */
    public int getAbsoluteY(String nodeId) {
        NodePositionConfig pos = nodePositions.get(nodeId);
        return pos != null ? pos.getAbsoluteY(centerY) : centerY;
    }

    /**
     * Gets the width of a node based on its tier.
     *
     * @param node The skill node
     * @return The width in pixels
     */
    public int getNodeWidth(@Nonnull SkillNode node) {
        if (node.isStartNode()) return originWidth;
        if (node.isKeystone()) return keystoneWidth;
        if (node.isNotable()) return notableWidth;
        return basicWidth;
    }

    /**
     * Gets the height of a node based on its tier.
     *
     * @param node The skill node
     * @return The height in pixels
     */
    public int getNodeHeight(@Nonnull SkillNode node) {
        if (node.isStartNode()) return originHeight;
        if (node.isKeystone()) return keystoneHeight;
        if (node.isNotable()) return notableHeight;
        return basicHeight;
    }

    /**
     * Checks if a node ID has a position defined.
     */
    public boolean hasPosition(String nodeId) {
        return nodePositions.containsKey(nodeId);
    }

    /**
     * Gets the total number of positioned nodes.
     */
    public int getNodeCount() {
        return nodePositions.size();
    }

    /**
     * Gets all generated node positions.
     *
     * @return Unmodifiable map of node ID to position
     */
    @Nonnull
    public Map<String, NodePositionConfig> getAllPositions() {
        return Map.copyOf(nodePositions);
    }
}
