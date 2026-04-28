package io.github.larsonix.trailoforbis.skilltree.map;

import com.hypixel.hytale.protocol.Direction;
import com.hypixel.hytale.protocol.FormattedMessage;
import com.hypixel.hytale.protocol.Position;
import com.hypixel.hytale.protocol.Transform;
import com.hypixel.hytale.protocol.packets.worldmap.MapChunk;
import com.hypixel.hytale.protocol.packets.worldmap.MapImage;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import io.github.larsonix.trailoforbis.api.services.SkillTreeService;
import io.github.larsonix.trailoforbis.config.ConfigManager;
import io.github.larsonix.trailoforbis.skilltree.config.SkillNode;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * Renders skill tree as map chunks and markers.
 *
 * <p>Generates:
 * <ul>
 *   <li>{@link MapChunk} array - pixel data for background and connections</li>
 *   <li>{@link MapMarker} array - clickable nodes with context menus</li>
 * </ul>
 *
 * <p>Uses existing {@code SkillNode.positionX/Y} for node coordinates.
 */
public class SkillTreeMapRenderer {

    /** Scale multiplier for node positions - smaller scale = more compact tree */
    private static final double POSITION_SCALE = 3.0;

    private final SkillTreeService skillTreeService;
    private final ConfigManager configManager;

    /**
     * Creates a new SkillTreeMapRenderer.
     *
     * @param skillTreeService Service for node data and allocation state
     * @param configManager Config manager for settings
     */
    public SkillTreeMapRenderer(
        @Nonnull SkillTreeService skillTreeService,
        @Nonnull ConfigManager configManager
    ) {
        this.skillTreeService = Objects.requireNonNull(skillTreeService);
        this.configManager = Objects.requireNonNull(configManager);
    }

    /**
     * Generate map chunks for the skill tree.
     */
    public MapChunk[] generateChunks(@Nonnull UUID playerId) {
        Set<String> allocated = skillTreeService.getAllocatedNodes(playerId);

        // Calculate bounds of skill tree
        Bounds bounds = calculateSkillTreeBounds();

        // Determine which chunks we need
        int chunkSize = MapColorPalette.CHUNK_SIZE;
        int minChunkX = bounds.minX / chunkSize - 1;
        int maxChunkX = bounds.maxX / chunkSize + 1;
        int minChunkZ = bounds.minZ / chunkSize - 1;
        int maxChunkZ = bounds.maxZ / chunkSize + 1;

        List<MapChunk> chunks = new ArrayList<>();

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                MapChunk chunk = generateChunk(cx, cz, allocated, playerId);
                chunks.add(chunk);
            }
        }

        return chunks.toArray(new MapChunk[0]);
    }

    /**
     * Generate a single map chunk.
     */
    private MapChunk generateChunk(int chunkX, int chunkZ, Set<String> allocated, UUID playerId) {
        int chunkSize = MapColorPalette.CHUNK_SIZE;
        int[] pixels = new int[chunkSize * chunkSize];

        // Fill background
        Arrays.fill(pixels, MapColorPalette.BACKGROUND);

        // Calculate world coordinates for this chunk
        int worldMinX = chunkX * chunkSize;
        int worldMinZ = chunkZ * chunkSize;

        // Draw ONLY connections that pass through this chunk
        // Node circles are handled by markers (MapMarker) for clickability
        for (SkillNode node : skillTreeService.getAllNodes()) {
            for (String connId : node.getConnections()) {
                Optional<SkillNode> connOpt = skillTreeService.getNode(connId);
                if (connOpt.isPresent()) {
                    SkillNode conn = connOpt.get();
                    // Note: Using offsets as absolute coords for chunk drawing (chunks disabled)
                    drawConnection(pixels, chunkSize, worldMinX, worldMinZ,
                                   (int) getNodeOffsetX(node), (int) getNodeOffsetZ(node),
                                   (int) getNodeOffsetX(conn), (int) getNodeOffsetZ(conn),
                                   MapColorPalette.CONNECTION);
                }
            }
        }

        // NOTE: Node drawing removed - markers handle node visualization
        // This keeps chunks lightweight (lines only) while markers provide
        // clickable nodes with proper icons and hover text

        // Create chunk object with palette-encoded image
        MapChunk chunk = new MapChunk();
        chunk.chunkX = chunkX;
        chunk.chunkZ = chunkZ;
        chunk.image = encodeMapImage(chunkSize, chunkSize, pixels);

        return chunk;
    }

    /**
     * Generate markers for clickable skill nodes.
     *
     * @param playerId The player's UUID
     * @param playerX Player's X coordinate (markers positioned relative to this)
     * @param playerZ Player's Z coordinate (markers positioned relative to this)
     */
    public MapMarker[] generateMarkers(@Nonnull UUID playerId, double playerX, double playerZ) {
        Set<String> allocated = skillTreeService.getAllocatedNodes(playerId);

        List<MapMarker> markers = new ArrayList<>();

        for (SkillNode node : skillTreeService.getAllNodes()) {
            MapMarker marker = createNodeMarker(node, allocated, playerId, playerX, playerZ);
            markers.add(marker);
        }

        return markers.toArray(new MapMarker[0]);
    }

    /**
     * Create a marker for a skill node.
     *
     * @param node The skill node
     * @param allocated Set of allocated node IDs
     * @param playerId Player's UUID
     * @param playerX Player's X coordinate (markers offset from this)
     * @param playerZ Player's Z coordinate (markers offset from this)
     */
    private MapMarker createNodeMarker(SkillNode node, Set<String> allocated, UUID playerId,
                                       double playerX, double playerZ) {
        MapMarker marker = new MapMarker();

        // ID format: "node_<nodeId>"
        marker.id = "node_" + node.getId();

        // Display name (shown on hover) — FormattedMessage with rawText
        FormattedMessage nameMsg = new FormattedMessage();
        nameMsg.rawText = node.getName();
        marker.name = nameMsg;

        // Icon based on state - using existing Hytale icons
        marker.markerImage = getMarkerIcon(node, allocated, playerId);

        // Position RELATIVE to player - this makes the skill tree appear centered on map
        // The map is always centered on player position, so we offset markers from player
        double offsetX = getNodeOffsetX(node);
        double offsetZ = getNodeOffsetZ(node);
        marker.transform = new Transform(
            new Position(playerX + offsetX, 0.0, playerZ + offsetZ),
            new Direction(0.0f, 0.0f, 0.0f)  // yaw, pitch, roll - must not be null
        );

        // Context menus NOT supported on map markers - all Hytale providers pass null
        // Users must use /skilltree allocate/deallocate/info commands instead
        marker.contextMenuItems = null;

        return marker;
    }

    // ========== Drawing Utilities ==========

    /**
     * Draw a line between two nodes (connection) using Bresenham's algorithm.
     */
    private void drawConnection(int[] pixels, int chunkSize, int worldMinX, int worldMinZ,
                                 int x1, int z1, int x2, int z2, int color) {
        // Convert to local chunk coordinates
        int localX1 = x1 - worldMinX;
        int localZ1 = z1 - worldMinZ;
        int localX2 = x2 - worldMinX;
        int localZ2 = z2 - worldMinZ;

        // Bresenham's line algorithm
        int dx = Math.abs(localX2 - localX1);
        int dz = Math.abs(localZ2 - localZ1);
        int sx = localX1 < localX2 ? 1 : -1;
        int sz = localZ1 < localZ2 ? 1 : -1;
        int err = dx - dz;

        int thickness = MapColorPalette.CONNECTION_WIDTH / 2;

        while (true) {
            // Draw thick line by drawing multiple pixels
            for (int tx = -thickness; tx <= thickness; tx++) {
                for (int tz = -thickness; tz <= thickness; tz++) {
                    int px = localX1 + tx;
                    int pz = localZ1 + tz;

                    if (px >= 0 && px < chunkSize && pz >= 0 && pz < chunkSize) {
                        pixels[pz * chunkSize + px] = color;
                    }
                }
            }

            if (localX1 == localX2 && localZ1 == localZ2) break;

            int e2 = 2 * err;
            if (e2 > -dz) {
                err -= dz;
                localX1 += sx;
            }
            if (e2 < dx) {
                err += dx;
                localZ1 += sz;
            }
        }
    }

    /**
     * Draw a node circle.
     */
    private void drawNode(int[] pixels, int chunkSize, int centerX, int centerZ,
                          int radius, int color, boolean isKeystone) {
        // Draw filled circle
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz <= radius * radius) {
                    int px = centerX + dx;
                    int pz = centerZ + dz;

                    if (px >= 0 && px < chunkSize && pz >= 0 && pz < chunkSize) {
                        pixels[pz * chunkSize + px] = color;
                    }
                }
            }
        }

        // Draw border
        int borderColor = isKeystone ? MapColorPalette.BORDER_KEYSTONE : MapColorPalette.BORDER_NORMAL;
        for (int angle = 0; angle < 360; angle++) {
            double rad = Math.toRadians(angle);
            int px = centerX + (int)(radius * Math.cos(rad));
            int pz = centerZ + (int)(radius * Math.sin(rad));

            if (px >= 0 && px < chunkSize && pz >= 0 && pz < chunkSize) {
                pixels[pz * chunkSize + px] = borderColor;
            }
        }
    }

    // ========== Layout Utilities ==========

    /**
     * Get X offset for a node (based on path and position).
     * This offset is added to player position to get final marker position.
     */
    private double getNodeOffsetX(SkillNode node) {
        String path = node.getPath();
        // Reduced offsets for more compact tree that fits on map view
        int pathOffset = switch (path) {
            case "str" -> -50;   // Reduced from -300
            case "dex" -> 50;    // Reduced from 100
            case "int" -> 50;    // Reduced from 100
            case "vit" -> -50;   // Reduced from -300
            case "bridge" -> 0;
            default -> 0;  // origin
        };
        int baseX = pathOffset + node.getLayoutX() * MapColorPalette.NODE_SPACING / 100;
        return baseX * POSITION_SCALE;
    }

    /**
     * Get Z offset for a node (based on tier and positionY).
     * This offset is added to player position to get final marker position.
     */
    private double getNodeOffsetZ(SkillNode node) {
        int baseZ = (int)(node.getPositionY() * MapColorPalette.NODE_SPACING / 50) +
                    node.getTier() * MapColorPalette.NODE_SPACING;
        return baseZ * POSITION_SCALE;
    }

    /**
     * Get node color based on state.
     */
    private int getNodeColor(SkillNode node, Set<String> allocated, UUID playerId) {
        if (allocated.contains(node.getId())) {
            if (node.isKeystone()) {
                return MapColorPalette.KEYSTONE_ALLOCATED;
            } else if (node.isNotable()) {
                return MapColorPalette.NOTABLE_ALLOCATED;
            }
            return MapColorPalette.NODE_ALLOCATED;
        } else if (skillTreeService.canAllocate(playerId, node.getId())) {
            return MapColorPalette.NODE_AVAILABLE;
        } else {
            return MapColorPalette.NODE_LOCKED;
        }
    }

    /**
     * Get marker icon based on node state (uses existing Hytale icons).
     */
    private String getMarkerIcon(SkillNode node, Set<String> allocated, UUID playerId) {
        boolean isAllocated = allocated.contains(node.getId());

        if (node.isStartNode()) {
            return "Spawn.png";  // Origin node
        } else if (node.isKeystone()) {
            return isAllocated ? "Warp.png" : "Death.png";
        } else if (node.isNotable()) {
            return isAllocated ? "Prefab.png" : "Death.png";
        } else if (isAllocated) {
            return "Portal.png";  // Allocated normal node
        } else if (skillTreeService.canAllocate(playerId, node.getId())) {
            return "Home.png";  // Available to allocate
        } else {
            return "Death.png";  // Locked
        }
    }

    /**
     * Calculate bounds of entire skill tree.
     */
    private Bounds calculateSkillTreeBounds() {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        int maxRadius = MapColorPalette.KEYSTONE_RADIUS;  // Use largest node size for padding

        for (SkillNode node : skillTreeService.getAllNodes()) {
            int x = (int) getNodeOffsetX(node);
            int z = (int) getNodeOffsetZ(node);
            minX = Math.min(minX, x - maxRadius);
            maxX = Math.max(maxX, x + maxRadius);
            minZ = Math.min(minZ, z - maxRadius);
            maxZ = Math.max(maxZ, z + maxRadius);
        }

        // Handle empty tree case
        if (minX == Integer.MAX_VALUE) {
            return new Bounds(-256, 256, -256, 256);
        }

        return new Bounds(minX, maxX, minZ, maxZ);
    }

    private record Bounds(int minX, int maxX, int minZ, int maxZ) {}

    /**
     * Encodes raw ARGB pixel data into a palette-based MapImage.
     *
     * <p>MapImage uses a palette + packed indices format: unique colors are stored
     * in a palette array, and each pixel is an index into that palette, packed
     * at the minimum required bit depth.
     *
     * @param width  Image width
     * @param height Image height
     * @param pixels Raw ARGB pixel array (width * height)
     * @return Encoded MapImage
     */
    @Nonnull
    private static MapImage encodeMapImage(int width, int height, @Nonnull int[] pixels) {
        // Build palette: unique colors in order of first appearance
        Map<Integer, Integer> colorToIndex = new LinkedHashMap<>();
        for (int pixel : pixels) {
            colorToIndex.putIfAbsent(pixel, colorToIndex.size());
        }

        int[] palette = new int[colorToIndex.size()];
        for (var entry : colorToIndex.entrySet()) {
            palette[entry.getValue()] = entry.getKey();
        }

        // Calculate bits per index (minimum to represent palette size)
        int paletteSize = palette.length;
        byte bitsPerIndex;
        if (paletteSize <= 1) {
            bitsPerIndex = 1;
        } else {
            bitsPerIndex = (byte) (32 - Integer.numberOfLeadingZeros(paletteSize - 1));
        }

        // Pack indices into byte array
        int totalBits = pixels.length * bitsPerIndex;
        int totalBytes = (totalBits + 7) / 8;
        byte[] packedIndices = new byte[totalBytes];

        int bitPos = 0;
        for (int pixel : pixels) {
            int index = colorToIndex.get(pixel);
            // Write bitsPerIndex bits at bitPos
            for (int b = 0; b < bitsPerIndex; b++) {
                if ((index & (1 << b)) != 0) {
                    int byteIndex = bitPos / 8;
                    int bitOffset = bitPos % 8;
                    packedIndices[byteIndex] |= (byte) (1 << bitOffset);
                }
                bitPos++;
            }
        }

        MapImage image = new MapImage();
        image.width = width;
        image.height = height;
        image.palette = palette;
        image.bitsPerIndex = bitsPerIndex;
        image.packedIndices = packedIndices;
        return image;
    }
}
