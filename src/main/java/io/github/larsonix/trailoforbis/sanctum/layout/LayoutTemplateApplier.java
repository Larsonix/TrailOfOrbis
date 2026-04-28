package io.github.larsonix.trailoforbis.sanctum.layout;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import javax.annotation.Nonnull;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Level;

/**
 * Applies a template arm's layout pattern to all 6 elemental arms.
 *
 * <p>Takes the relative positioning from one arm (e.g., WATER) and applies
 * the same pattern to all other arms, rotated appropriately for each
 * arm's direction.
 */
public class LayoutTemplateApplier {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final double BASE_HEIGHT = 65.0;

    /**
     * Arm directions in 3D space.
     */
    private enum ArmDirection {
        FIRE(1, 0, 0),       // +X (East)
        WATER(-1, 0, 0),     // -X (West)
        LIGHTNING(0, 0, 1),  // +Z (South)
        EARTH(0, 0, -1),     // -Z (North)
        VOID(0, 1, 0),       // +Y (Up)
        WIND(0, -1, 0);      // -Y (Down)

        final int x, y, z;

        ArmDirection(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        /**
         * Gets perpendicular directions for this arm.
         * Returns [perp1, perp2] where perp1 is typically the "vertical" offset
         * and perp2 is the other horizontal offset.
         */
        int[][] getPerpendiculars() {
            return switch (this) {
                case FIRE, WATER -> new int[][]{{0, 1, 0}, {0, 0, 1}};       // Y and Z
                case LIGHTNING, EARTH -> new int[][]{{0, 1, 0}, {1, 0, 0}};  // Y and X
                case VOID, WIND -> new int[][]{{1, 0, 0}, {0, 0, 1}};       // X and Z
            };
        }
    }

    /**
     * Node position relative to arm origin.
     */
    private record RelativePosition(double along, double perp1, double perp2) {}

    /**
     * Applies the water arm template to all arms and saves to YAML.
     *
     * @param exportedPath Path to the exported positions file
     * @param outputPath   Path to write the transformed positions
     * @return Number of nodes transformed
     */
    @SuppressWarnings("unchecked")
    public static int applyTemplateToAllArms(@Nonnull Path exportedPath, @Nonnull Path outputPath) throws IOException {
        // Load exported positions
        Map<String, Object> data;
        try (InputStream input = Files.newInputStream(exportedPath)) {
            Yaml yaml = new Yaml();
            data = yaml.load(input);
        }

        if (data == null || !data.containsKey("nodes")) {
            throw new IOException("Invalid exported positions file");
        }

        Map<String, Object> nodesSection = (Map<String, Object>) data.get("nodes");

        // Extract water arm nodes (template arm at -X)
        Map<String, Object> waterNodes = (Map<String, Object>) nodesSection.get("water");
        if (waterNodes == null || waterNodes.isEmpty()) {
            throw new IOException("No water nodes found in export");
        }

        // Convert water nodes to relative positions
        Map<String, RelativePosition> waterRelative = extractRelativePositions(waterNodes, ArmDirection.WATER);
        LOGGER.atInfo().log("Extracted %d water node relative positions", waterRelative.size());

        // Build new positions map with all arms
        Map<String, Map<String, Map<String, Object>>> newPositions = new LinkedHashMap<>();

        // Apply water template to all arms
        String[] armNames = {"fire", "water", "lightning", "earth", "void", "wind"};
        ArmDirection[] armDirs = {ArmDirection.FIRE, ArmDirection.WATER, ArmDirection.LIGHTNING,
                                   ArmDirection.EARTH, ArmDirection.VOID, ArmDirection.WIND};

        int totalNodes = 0;

        for (int i = 0; i < armNames.length; i++) {
            String armName = armNames[i];
            ArmDirection armDir = armDirs[i];

            Map<String, Map<String, Object>> armNodes = new LinkedHashMap<>();

            // For each water node, create equivalent node for this arm
            for (Map.Entry<String, RelativePosition> entry : waterRelative.entrySet()) {
                String waterNodeId = entry.getKey();
                RelativePosition rel = entry.getValue();

                // Convert water node ID to this arm's node ID
                String nodeId = waterNodeId.replace("water_", armName + "_");

                // Convert relative position to world position for this arm
                Vector3d worldPos = relativeToWorld(rel, armDir);

                armNodes.put(nodeId, createNodeMap(worldPos));
                totalNodes++;
            }

            newPositions.put(armName, armNodes);
        }

        // Also copy core nodes, bridges from original if they exist
        copyNonArmNodes(nodesSection, newPositions);

        // Write output
        writeYaml(newPositions, outputPath, totalNodes);

        LOGGER.atInfo().log("Applied water template to all arms: %d total nodes", totalNodes);
        return totalNodes;
    }

    /**
     * Extracts relative positions from absolute world positions.
     */
    private static Map<String, RelativePosition> extractRelativePositions(
            Map<String, Object> armNodes,
            ArmDirection direction) {

        Map<String, RelativePosition> result = new LinkedHashMap<>();
        int[][] perps = direction.getPerpendiculars();

        for (Map.Entry<String, Object> entry : armNodes.entrySet()) {
            String nodeId = entry.getKey();
            if (!(entry.getValue() instanceof Map)) continue;

            @SuppressWarnings("unchecked")
            Map<String, Object> coords = (Map<String, Object>) entry.getValue();

            double x = getDouble(coords, "x");
            double y = getDouble(coords, "y");
            double z = getDouble(coords, "z");

            // Calculate distance along arm direction
            double along = x * direction.x + y * direction.y + z * direction.z;

            // Calculate perpendicular offsets
            // For WATER (-X), perp1 is Y offset from base, perp2 is Z
            double perp1 = x * perps[0][0] + (y - BASE_HEIGHT) * perps[0][1] + z * perps[0][2];
            double perp2 = x * perps[1][0] + y * perps[1][1] + z * perps[1][2];

            // For WATER direction (-1,0,0), along = -x, so negate to get positive distance
            along = -along;  // Because WATER goes -X, distance is positive when x is negative

            result.put(nodeId, new RelativePosition(along, perp1, perp2));

            LOGGER.atFine().log("  %s: world(%.2f,%.2f,%.2f) -> relative(along=%.2f, p1=%.2f, p2=%.2f)",
                nodeId, x, y, z, along, perp1, perp2);
        }

        return result;
    }

    /**
     * Converts relative position to world position for a given arm direction.
     */
    private static Vector3d relativeToWorld(RelativePosition rel, ArmDirection direction) {
        int[][] perps = direction.getPerpendiculars();

        // Position along arm direction
        double x = rel.along * direction.x;
        double y = rel.along * direction.y;
        double z = rel.along * direction.z;

        // Add perpendicular offsets
        // perp1 is typically Y offset (vertical spread)
        x += rel.perp1 * perps[0][0];
        y += rel.perp1 * perps[0][1] + BASE_HEIGHT;  // Add base height for Y
        z += rel.perp1 * perps[0][2];

        // perp2 is the other perpendicular
        x += rel.perp2 * perps[1][0];
        y += rel.perp2 * perps[1][1];
        z += rel.perp2 * perps[1][2];

        // Adjust Y if this is VOID or WIND (their along direction IS Y)
        if (direction == ArmDirection.VOID || direction == ArmDirection.WIND) {
            // For vertical arms, base height is the starting point
            y = BASE_HEIGHT + rel.along * direction.y;
            // perp1 and perp2 go to X and Z
            x = rel.perp1;
            z = rel.perp2;
        }

        return new Vector3d(
            Math.round(x * 100.0) / 100.0,
            Math.round(y * 100.0) / 100.0,
            Math.round(z * 100.0) / 100.0
        );
    }

    /**
     * Copies non-arm nodes (core, bridges) from original export.
     */
    @SuppressWarnings("unchecked")
    private static void copyNonArmNodes(
            Map<String, Object> originalNodes,
            Map<String, Map<String, Map<String, Object>>> newPositions) {

        // Copy core nodes if present
        if (originalNodes.containsKey("core")) {
            Map<String, Object> coreNodes = (Map<String, Object>) originalNodes.get("core");
            Map<String, Map<String, Object>> coreCopy = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : coreNodes.entrySet()) {
                if (entry.getValue() instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> coords = (Map<String, Object>) entry.getValue();
                    coreCopy.put(entry.getKey(), new LinkedHashMap<>(coords));
                }
            }
            newPositions.put("core", coreCopy);
        }
    }

    private static void writeYaml(
            Map<String, Map<String, Map<String, Object>>> positions,
            Path outputPath,
            int nodeCount) throws IOException {

        Map<String, Object> yamlRoot = new LinkedHashMap<>();
        yamlRoot.put("_comment", "Generated from template - applied to all arms");
        yamlRoot.put("_generatedAt", System.currentTimeMillis());
        yamlRoot.put("_nodeCount", nodeCount);
        yamlRoot.put("nodes", positions);

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        Yaml yaml = new Yaml(options);

        try (FileWriter writer = new FileWriter(outputPath.toFile())) {
            writer.write("# ══════════════════════════════════════════════════════════════════════════════\n");
            writer.write("# SKILL TREE NODE POSITIONS (Generated from WATER template)\n");
            writer.write("# ══════════════════════════════════════════════════════════════════════════════\n");
            writer.write("#\n");
            writer.write("# This file was generated by applying the WATER arm layout to all 6 elemental arms.\n");
            writer.write("# Each arm has the same relative pattern, rotated for its direction.\n");
            writer.write("#\n");
            writer.write("# ══════════════════════════════════════════════════════════════════════════════\n\n");
            yaml.dump(yamlRoot, writer);
        }
    }

    private static Map<String, Object> createNodeMap(Vector3d pos) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("x", pos.x);
        map.put("y", pos.y);
        map.put("z", pos.z);
        return map;
    }

    private static double getDouble(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return 0.0;
    }
}
