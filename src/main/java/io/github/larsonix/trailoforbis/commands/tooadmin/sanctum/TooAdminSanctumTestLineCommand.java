package io.github.larsonix.trailoforbis.commands.tooadmin.sanctum;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.protocol.packets.buildertools.BuilderToolLaserPointer;
import com.hypixel.hytale.server.core.modules.debug.DebugUtils;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import io.github.larsonix.trailoforbis.sanctum.SanctumBlockPlacer;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Temporary test command for verifying DisplayDebug line rendering.
 *
 * <p>Usage: /tooadmin sanctum testline [clear|stress|laser|blocks]
 *
 * <p>Draws debug lines near the player to verify visual quality.
 * Use 'laser' to draw BuilderToolLaserPointer beams for comparison.
 * Use 'laser stress N [split P]' to test beam pool limits.
 * Use 'blocks [N] [clear]' to place colored glowing blocks (proof of concept).
 */
public final class TooAdminSanctumTestLineCommand extends AbstractPlayerCommand {

    /** Tracks block positions placed by the 'blocks' subcommand for cleanup. */
    private static final List<int[]> placedTestBlocks = Collections.synchronizedList(new ArrayList<>());

    /** Block placer instance for the 'blocks' subcommand. */
    private static final SanctumBlockPlacer blockPlacer = new SanctumBlockPlacer();

    public TooAdminSanctumTestLineCommand() {
        super("testline", "Test DisplayDebug line rendering");
        this.addAliases("tl");
        this.setAllowsExtraArguments(true);
    }

    @Override
    protected void execute(
        @Nonnull CommandContext context,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull PlayerRef sender,
        @Nonnull World world
    ) {
        // Get player position
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            sender.sendMessage(Message.raw("Could not get player position.").color(MessageColors.ERROR));
            return;
        }

        Vector3d pos = transform.getPosition();
        String input = context.getInputString().toLowerCase();

        // Parse subcommand — check "blocks" first, then "laser stress" before plain "stress" or "laser"
        if (input.contains("blocks")) {
            handleBlocksSubcommand(world, pos, sender, input);
            return;
        }

        if (input.contains("clear")) {
            DebugUtils.clear(world);
            sender.sendMessage(Message.raw("Cleared all debug shapes.").color(MessageColors.SUCCESS));
            return;
        }

        if (input.contains("laser") && input.contains("stress")) {
            drawLaserStressTest(world, pos, sender, input);
            return;
        }

        if (input.contains("stress")) {
            drawStressTest(world, pos, sender);
            return;
        }

        if (input.contains("laser")) {
            drawLaserSampleLines(store, ref, world, pos, sender);
            return;
        }

        // Default: draw 5 sample lines with different colors/thicknesses + dashed line
        drawSampleLines(world, pos, sender);
    }

    /**
     * Draws N laser beams in a radial pattern to stress-test beam pool limits.
     *
     * <p>Usage:
     * <ul>
     *   <li>{@code laser stress 60} — 60 beams, all with playerNetworkId=0 (single pool)</li>
     *   <li>{@code laser stress 60 split 3} — 60 beams, 20 per pool (IDs 1,2,3)</li>
     * </ul>
     *
     * <p>If "split" mode shows all beams while single-pool mode caps at ~50,
     * it confirms the client pools beams per-entity ID.
     */
    private void drawLaserStressTest(
            @Nonnull World world,
            @Nonnull Vector3d pos,
            @Nonnull PlayerRef sender,
            @Nonnull String input) {

        // Parse beam count (first number after "stress")
        int beamCount = 60; // default
        int poolCount = 0;  // 0 = single pool (all ID 0)

        String[] parts = input.split("\\s+");
        for (int i = 0; i < parts.length; i++) {
            if ("stress".equals(parts[i]) && i + 1 < parts.length) {
                try { beamCount = Integer.parseInt(parts[i + 1]); } catch (NumberFormatException ignored) {}
            }
            if ("split".equals(parts[i]) && i + 1 < parts.length) {
                try { poolCount = Integer.parseInt(parts[i + 1]); } catch (NumberFormatException ignored) {}
            }
        }

        beamCount = Math.max(1, Math.min(beamCount, 1000));
        poolCount = Math.max(0, Math.min(poolCount, 1000));

        double x = pos.x;
        double y = pos.y + 2;
        double z = pos.z + 3;
        double radius = 30.0;
        int durationMs = 30000;

        int[] colorPalette = {0xFF4455, 0x55CCEE, 0xFFD700, 0xBB77DD, 0x55DD55, 0xFF7855};

        for (int i = 0; i < beamCount; i++) {
            double angle = (2.0 * Math.PI * i) / beamCount;
            float endX = (float) (x + radius * Math.cos(angle));
            float endZ = (float) (z + radius * Math.sin(angle));
            float endY = (float) (y + (i % 10) * 0.3);

            int color = colorPalette[i % colorPalette.length];

            // Pool assignment: 0 = all use ID 0, >0 = round-robin across IDs 1..poolCount
            int netId = poolCount > 0 ? (i % poolCount) + 1 : 0;

            sendLaserLine(world, netId,
                (float) x, (float) y, (float) z,
                endX, endY, endZ,
                color, durationMs);
        }

        String mode = poolCount > 0
            ? String.format("split across %d pools (IDs 1-%d)", poolCount, poolCount)
            : "single pool (ID 0)";
        sender.sendMessage(Message.empty()
            .insert(Message.raw("[TestLine] ").color(MessageColors.GOLD))
            .insert(Message.raw(String.format("Drew %d LASER stress beams, %s, lasting 30s.",
                beamCount, mode)).color(MessageColors.WHITE)));
    }

    /**
     * Draws 5 sample lines with different visual parameters.
     */
    private void drawSampleLines(@Nonnull World world, @Nonnull Vector3d pos, @Nonnull PlayerRef sender) {
        double x = pos.x;
        double y = pos.y + 2;
        double z = pos.z + 3;
        float duration = 30.0f;

        // Line 1: Red, thin (0.04)
        DebugUtils.addLine(world,
            new Vector3d(x, y, z),
            new Vector3d(x + 10, y, z),
            DebugUtils.COLOR_RED, 0.04, duration, DebugUtils.FLAG_NONE);

        // Line 2: Cyan, medium (0.08)
        DebugUtils.addLine(world,
            new Vector3d(x, y, z + 2),
            new Vector3d(x + 10, y, z + 2),
            new Vector3f(0.33f, 0.8f, 0.93f), 0.08, duration, DebugUtils.FLAG_NONE);

        // Line 3: Gold, thick (0.15)
        DebugUtils.addLine(world,
            new Vector3d(x, y, z + 4),
            new Vector3d(x + 10, y, z + 4),
            new Vector3f(1.0f, 0.84f, 0.0f), 0.15, duration, DebugUtils.FLAG_NONE);

        // Line 4: Purple, diagonal
        DebugUtils.addLine(world,
            new Vector3d(x, y, z + 6),
            new Vector3d(x + 10, y + 5, z + 6),
            new Vector3f(0.73f, 0.47f, 0.87f), 0.08, duration, DebugUtils.FLAG_NONE);

        // Line 5: Green, fade=true (for comparison)
        DebugUtils.addLine(world,
            new Vector3d(x, y, z + 8),
            new Vector3d(x + 10, y, z + 8),
            DebugUtils.COLOR_LIME, 0.08, duration, DebugUtils.FLAG_FADE);

        // Dashed line: Gray, alternating segments with gaps
        drawDashedLine(world,
            new Vector3d(x, y, z + 10),
            new Vector3d(x + 10, y, z + 10),
            DebugUtils.COLOR_GRAY, 0.04, duration, 2.0, 1.0);

        sender.sendMessage(Message.empty()
            .insert(Message.raw("[TestLine] ").color(MessageColors.GOLD))
            .insert(Message.raw("Drew 6 lines (5 solid + 1 dashed) lasting 30s. Use 'clear' to remove.").color(MessageColors.WHITE)));
    }

    /**
     * Draws 500 lines simultaneously to stress-test the client.
     */
    private void drawStressTest(@Nonnull World world, @Nonnull Vector3d pos, @Nonnull PlayerRef sender) {
        double x = pos.x;
        double y = pos.y + 5;
        double z = pos.z + 5;
        float duration = 15.0f;

        int lineCount = 500;
        double radius = 50.0;

        for (int i = 0; i < lineCount; i++) {
            // Radial pattern from center
            double angle = (2.0 * Math.PI * i) / lineCount;
            double endX = x + radius * Math.cos(angle);
            double endZ = z + radius * Math.sin(angle);
            double endY = y + (i % 20) - 10; // slight Y variation

            Vector3f color = DebugUtils.INDEXED_COLORS[i % DebugUtils.INDEXED_COLORS.length];

            DebugUtils.addLine(world,
                new Vector3d(x, y, z),
                new Vector3d(endX, endY, endZ),
                color, 0.06, duration, DebugUtils.FLAG_NONE);
        }

        sender.sendMessage(Message.empty()
            .insert(Message.raw("[TestLine] ").color(MessageColors.GOLD))
            .insert(Message.raw("Drew " + lineCount + " stress-test lines lasting 15s. Use 'clear' to remove.").color(MessageColors.WHITE)));
    }

    /**
     * Draws sample lines using BuilderToolLaserPointer packets for visual comparison.
     */
    private void drawLaserSampleLines(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull World world,
            @Nonnull Vector3d pos,
            @Nonnull PlayerRef sender) {

        // Get player's network ID for the laser packet
        NetworkId networkIdComp = store.getComponent(ref, NetworkId.getComponentType());
        int playerNetId = networkIdComp != null ? networkIdComp.getId() : 0;

        double x = pos.x;
        double y = pos.y + 2;
        double z = pos.z + 3;
        int durationMs = 30000;

        // Line 1: Red
        sendLaserLine(world, playerNetId,
            (float) x, (float) y, (float) z,
            (float) (x + 10), (float) y, (float) z,
            0xFF4455, durationMs);

        // Line 2: Cyan
        sendLaserLine(world, playerNetId,
            (float) x, (float) y, (float) (z + 2),
            (float) (x + 10), (float) y, (float) (z + 2),
            0x55CCEE, durationMs);

        // Line 3: Gold
        sendLaserLine(world, playerNetId,
            (float) x, (float) y, (float) (z + 4),
            (float) (x + 10), (float) y, (float) (z + 4),
            0xFFD700, durationMs);

        // Line 4: Purple, diagonal
        sendLaserLine(world, playerNetId,
            (float) x, (float) y, (float) (z + 6),
            (float) (x + 10), (float) (y + 5), (float) (z + 6),
            0xBB77DD, durationMs);

        // Line 5: Green
        sendLaserLine(world, playerNetId,
            (float) x, (float) y, (float) (z + 8),
            (float) (x + 10), (float) y, (float) (z + 8),
            0x55DD55, durationMs);

        // Line 6: Dim gray dashed (segments with gaps)
        drawLaserDashedLine(world, playerNetId,
            x, y, z + 10,
            x + 10, y, z + 10,
            0x666666, durationMs, 2.0, 1.0);

        sender.sendMessage(Message.empty()
            .insert(Message.raw("[TestLine] ").color(MessageColors.GOLD))
            .insert(Message.raw("Drew 6 LASER lines (5 solid + 1 dashed) lasting 30s.").color(MessageColors.WHITE)));
    }

    /**
     * Sends a single LaserPointer beam to all players in the world.
     */
    private void sendLaserLine(@Nonnull World world, int playerNetId,
                               float startX, float startY, float startZ,
                               float endX, float endY, float endZ,
                               int color, int durationMs) {
        BuilderToolLaserPointer packet = new BuilderToolLaserPointer(
            playerNetId, startX, startY, startZ, endX, endY, endZ, color, durationMs);

        for (PlayerRef playerRef : world.getPlayerRefs()) {
            playerRef.getPacketHandler().write(packet);
        }
    }

    /**
     * Draws a dashed laser line using short segments with gaps.
     */
    private void drawLaserDashedLine(@Nonnull World world, int playerNetId,
                                     double startX, double startY, double startZ,
                                     double endX, double endY, double endZ,
                                     int color, int durationMs,
                                     double dashLength, double gapLength) {
        double dx = endX - startX;
        double dy = endY - startY;
        double dz = endZ - startZ;
        double totalLength = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (totalLength < 0.001) return;

        double ndx = dx / totalLength;
        double ndy = dy / totalLength;
        double ndz = dz / totalLength;
        double cursor = 0;
        boolean drawing = true;

        while (cursor < totalLength) {
            if (drawing) {
                double segEnd = Math.min(cursor + dashLength, totalLength);
                sendLaserLine(world, playerNetId,
                    (float) (startX + ndx * cursor), (float) (startY + ndy * cursor), (float) (startZ + ndz * cursor),
                    (float) (startX + ndx * segEnd), (float) (startY + ndy * segEnd), (float) (startZ + ndz * segEnd),
                    color, durationMs);
                cursor = segEnd;
            } else {
                cursor += gapLength;
            }
            drawing = !drawing;
        }
    }

    /**
     * Draws a dashed line by creating multiple short segments with gaps.
     */
    private void drawDashedLine(
            @Nonnull World world,
            @Nonnull Vector3d start, @Nonnull Vector3d end,
            @Nonnull Vector3f color, double thickness,
            float duration, double dashLength, double gapLength) {

        double dx = end.x - start.x;
        double dy = end.y - start.y;
        double dz = end.z - start.z;
        double totalLength = Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (totalLength < 0.001) return;

        // Normalize direction
        double ndx = dx / totalLength;
        double ndy = dy / totalLength;
        double ndz = dz / totalLength;

        double cursor = 0;
        boolean drawing = true;

        while (cursor < totalLength) {
            if (drawing) {
                double segEnd = Math.min(cursor + dashLength, totalLength);
                DebugUtils.addLine(world,
                    new Vector3d(start.x + ndx * cursor, start.y + ndy * cursor, start.z + ndz * cursor),
                    new Vector3d(start.x + ndx * segEnd, start.y + ndy * segEnd, start.z + ndz * segEnd),
                    color, thickness, duration, DebugUtils.FLAG_NONE);
                cursor = segEnd;
            } else {
                cursor += gapLength;
            }
            drawing = !drawing;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // BLOCKS SUBCOMMAND (Proof of Concept)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Handles the 'blocks' subcommand for placing colored glowing block lines.
     *
     * <p>Usage:
     * <ul>
     *   <li>{@code blocks} — places a 10-block sample line</li>
     *   <li>{@code blocks 50} — places a 50-block long line</li>
     *   <li>{@code blocks clear} — removes previously placed test blocks</li>
     * </ul>
     */
    private void handleBlocksSubcommand(
            @Nonnull World world,
            @Nonnull Vector3d pos,
            @Nonnull PlayerRef sender,
            @Nonnull String input) {

        // Handle clear
        if (input.contains("clear")) {
            if (placedTestBlocks.isEmpty()) {
                sender.sendMessage(Message.empty()
                    .insert(Message.raw("[TestLine] ").color(MessageColors.GOLD))
                    .insert(Message.raw("No test blocks to clear.").color(MessageColors.WHITE)));
                return;
            }

            world.execute(() -> {
                int removed = blockPlacer.removeBlocks(world, placedTestBlocks);
                placedTestBlocks.clear();
                sender.sendMessage(Message.empty()
                    .insert(Message.raw("[TestLine] ").color(MessageColors.GOLD))
                    .insert(Message.raw(String.format("Cleared %d test blocks.", removed))
                        .color(MessageColors.SUCCESS)));
            });
            return;
        }

        // Parse block count (default 10)
        int blockCount = 10;
        String[] parts = input.split("\\s+");
        for (int i = 0; i < parts.length; i++) {
            if ("blocks".equals(parts[i]) && i + 1 < parts.length) {
                try {
                    blockCount = Integer.parseInt(parts[i + 1]);
                } catch (NumberFormatException ignored) {}
            }
        }
        blockCount = Math.max(1, Math.min(blockCount, 500));

        // Resolve the test block type (use Connection_Fire as a colorful default)
        BlockType blockType = blockPlacer.resolveBlockType("Connection_Fire");
        if (blockType == null) {
            sender.sendMessage(Message.empty()
                .insert(Message.raw("[TestLine] ").color(MessageColors.GOLD))
                .insert(Message.raw("Connection_Fire block type not found! Is the asset pack deployed?")
                    .color(MessageColors.ERROR)));
            return;
        }

        // Compute Bresenham line from player position forward (+X direction)
        Vector3d start = new Vector3d(pos.x + 2, pos.y + 1, pos.z);
        Vector3d end = new Vector3d(pos.x + 2 + blockCount, pos.y + 1, pos.z);
        List<int[]> positions = SanctumBlockPlacer.computeBresenhamLine(start, end);

        final int expectedCount = positions.size();

        world.execute(() -> {
            int placed = blockPlacer.placeBlocks(world, positions, blockType);

            // Track for cleanup
            synchronized (placedTestBlocks) {
                placedTestBlocks.addAll(positions);
            }

            sender.sendMessage(Message.empty()
                .insert(Message.raw("[TestLine] ").color(MessageColors.GOLD))
                .insert(Message.raw(String.format("Placed %d/%d glowing blocks (Connection_Fire). Use 'blocks clear' to remove.",
                    placed, expectedCount)).color(MessageColors.WHITE)));
        });
    }
}
