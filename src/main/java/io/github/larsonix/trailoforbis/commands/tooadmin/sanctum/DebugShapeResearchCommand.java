package io.github.larsonix.trailoforbis.commands.tooadmin.sanctum;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.debug.DebugUtils;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Community research tool for empirically determining Hytale DebugUtils shape limits.
 *
 * <p>Tests three independent variables:
 * <ol>
 *   <li><b>burst</b> — Per-tick send capacity (how many addLine/addSphere/etc. calls
 *       in a single tick before the client drops packets)</li>
 *   <li><b>sustained</b> — Concurrent rendering cap (how many shapes can exist at once
 *       when sent via two-phase fill + maintenance)</li>
 *   <li><b>ramp</b> — Gradual ramp-up: start low, add shapes every N seconds, maintain all.
 *       Watch in real-time to find the exact ceiling.</li>
 * </ol>
 *
 * <p>All tests use concentric ring layouts with distinct colors per ring so missing shapes
 * are immediately visible and countable. Exhaustive per-tick logging captures every data
 * point for post-test analysis.
 *
 * <p>Usage:
 * <pre>
 *   /tooadmin sanctum research burst 500 [shape=line|sphere|cube|cylinder|cone|disc]
 *   /tooadmin sanctum research sustained 1000 [shape=line] [duration=5000] [fillrate=30] [margin=500]
 *   /tooadmin sanctum research ramp [step=100] [interval=5] [max=5000] [shape=line]
 *   /tooadmin sanctum research stop
 *   /tooadmin sanctum research status
 * </pre>
 *
 * @see DebugUtils
 */
public final class DebugShapeResearchCommand extends AbstractPlayerCommand {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // ═══════════════════════════════════════════════════════════════════
    // RING LAYOUT CONSTANTS
    // ═══════════════════════════════════════════════════════════════════

    /** Base radius for the first ring (blocks from center). */
    private static final double BASE_RADIUS = 10.0;

    /** Radius increment per ring. */
    private static final double RING_SPACING = 8.0;

    /** Length of each radial line segment (blocks). */
    private static final double LINE_LENGTH = 3.0;

    /** Default line thickness. */
    private static final double LINE_THICKNESS = 0.08;

    /** Default shape scale for non-line shapes. */
    private static final double SHAPE_SCALE = 0.3;

    /** Y offset above player for the test layout. */
    private static final double Y_OFFSET = 5.0;

    /** Colors for rings — 10 distinct, high-contrast colors. */
    private static final Vector3f[] RING_COLORS = {
        new Vector3f(1.0f, 0.27f, 0.33f),   // Red
        new Vector3f(0.33f, 0.80f, 0.93f),   // Cyan
        new Vector3f(1.0f, 0.84f, 0.0f),     // Gold
        new Vector3f(0.73f, 0.47f, 0.87f),   // Purple
        new Vector3f(0.33f, 0.87f, 0.33f),   // Green
        new Vector3f(1.0f, 0.60f, 0.20f),    // Orange
        new Vector3f(0.33f, 0.47f, 0.93f),   // Blue
        new Vector3f(1.0f, 0.47f, 0.73f),    // Pink
        new Vector3f(0.93f, 0.93f, 0.93f),   // White
        new Vector3f(1.0f, 0.93f, 0.33f),    // Yellow
    };

    // ═══════════════════════════════════════════════════════════════════
    // SHAPE TYPES
    // ═══════════════════════════════════════════════════════════════════

    private static final String SHAPE_LINE = "line";
    private static final String SHAPE_SPHERE = "sphere";
    private static final String SHAPE_CUBE = "cube";
    private static final String SHAPE_CYLINDER = "cylinder";
    private static final String SHAPE_CONE = "cone";
    private static final String SHAPE_DISC = "disc";

    // ═══════════════════════════════════════════════════════════════════
    // GLOBAL SESSION STATE (one test at a time)
    // ═══════════════════════════════════════════════════════════════════

    @Nullable
    private static volatile ResearchSession activeSession;

    @Nullable
    private static ScheduledExecutorService scheduler;

    @Nullable
    private static ScheduledFuture<?> scheduledTask;

    // ═══════════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════════

    public DebugShapeResearchCommand() {
        super("research", "DebugUtils shape limit research tool");
        this.setAllowsExtraArguments(true);
    }

    // ═══════════════════════════════════════════════════════════════════
    // COMMAND DISPATCH
    // ═══════════════════════════════════════════════════════════════════

    @Override
    protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef sender,
            @Nonnull World world) {

        String input = context.getInputString().toLowerCase().trim();

        if (input.contains("stop")) {
            handleStop(sender, world);
            return;
        }

        if (input.contains("status")) {
            handleStatus(sender);
            return;
        }

        // Get player position for layout center
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            sender.sendMessage(Message.raw("Could not get player position.").color(MessageColors.ERROR));
            return;
        }
        Vector3d pos = transform.getPosition();

        if (input.contains("burst")) {
            handleBurst(sender, world, pos, input);
        } else if (input.contains("sustained")) {
            handleSustained(sender, world, pos, input);
        } else if (input.contains("ramp")) {
            handleRamp(sender, world, pos, input);
        } else {
            sendUsage(sender);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // BURST TEST
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Sends exactly N shapes in a SINGLE tick. No maintenance, no spreading.
     * Tests the per-tick/per-frame packet capacity.
     */
    private void handleBurst(
            @Nonnull PlayerRef sender,
            @Nonnull World world,
            @Nonnull Vector3d playerPos,
            @Nonnull String input) {

        if (activeSession != null) {
            sender.sendMessage(Message.raw("A test is already running. Use 'stop' first.").color(MessageColors.ERROR));
            return;
        }

        int count = parseIntAfter(input, "burst", 500);
        count = Math.max(1, Math.min(count, 500000));
        String shapeType = parseStringAfter(input, "shape", SHAPE_LINE);
        float duration = 30.0f;

        double cx = playerPos.x;
        double cy = playerPos.y + Y_OFFSET;
        double cz = playerPos.z;

        // Generate ring layout
        List<ShapeSpec> shapes = generateRingLayout(cx, cy, cz, count);

        // Send all in one tick
        long startNanos = System.nanoTime();
        int sent = 0;
        for (ShapeSpec spec : shapes) {
            sendShape(world, spec, shapeType, duration, DebugUtils.FLAG_NO_WIREFRAME);
            sent++;
        }
        long elapsedUs = (System.nanoTime() - startNanos) / 1000;

        LOGGER.atInfo().log("[Research:Burst] Sent %d %s shapes in 1 tick (%d us). Duration=%.0fs. Center=(%.1f, %.1f, %.1f)",
            sent, shapeType, elapsedUs, duration, cx, cy, cz);

        sender.sendMessage(Message.empty()
            .insert(Message.raw("[Research:Burst] ").color(MessageColors.GOLD))
            .insert(Message.raw(String.format(
                "Sent %d %s shapes in 1 tick (%d us). Count the rings — each ring is labeled in chat. Duration: 30s.",
                sent, shapeType, elapsedUs)).color(MessageColors.WHITE)));

        // Report ring breakdown
        reportRingBreakdown(sender, count);
    }

    // ═══════════════════════════════════════════════════════════════════
    // SUSTAINED TEST
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Two-phase test: fill N shapes spread across ticks, then maintain them.
     * Tests the concurrent rendering cap independent of send rate.
     */
    private void handleSustained(
            @Nonnull PlayerRef sender,
            @Nonnull World world,
            @Nonnull Vector3d playerPos,
            @Nonnull String input) {

        if (activeSession != null) {
            sender.sendMessage(Message.raw("A test is already running. Use 'stop' first.").color(MessageColors.ERROR));
            return;
        }

        int count = parseIntAfter(input, "sustained", 1000);
        count = Math.max(10, Math.min(count, 500000));
        String shapeType = parseStringAfter(input, "shape", SHAPE_LINE);
        int durationMs = parseIntAfter(input, "duration", 5000);
        int userFillRate = parseIntAfter(input, "fillrate", 0); // 0 = auto
        int marginMs = parseIntAfter(input, "margin", 500);

        double cx = playerPos.x;
        double cy = playerPos.y + Y_OFFSET;
        double cz = playerPos.z;

        List<ShapeSpec> shapes = generateRingLayout(cx, cy, cz, count);

        // Calculate maintenance rate
        int targetCycleTicks = Math.max(1, (durationMs - marginMs) / 50);
        int maintenanceRate = Math.max(1, (int) Math.ceil((double) count / targetCycleTicks));

        // Auto-calculate fill rate: must fill ALL shapes within one duration window
        // so that no shape expires before the fill completes.
        // fillRate = ceil(count / (durationMs / 50)) + safety margin
        int durationTicks = Math.max(1, durationMs / 50);
        int minFillRate = Math.max(1, (int) Math.ceil((double) count / durationTicks));
        int fillRate;
        if (userFillRate > 0) {
            fillRate = userFillRate;
            if (userFillRate < minFillRate) {
                sender.sendMessage(Message.empty()
                    .insert(Message.raw("[Research] ").color(MessageColors.GOLD))
                    .insert(Message.raw(String.format(
                        "WARNING: fillrate=%d is too slow! Need %d/tick to fill %d shapes within %dms. Auto-using %d.",
                        userFillRate, minFillRate, count, durationMs, minFillRate)).color(MessageColors.ERROR)));
                fillRate = minFillRate;
            }
        } else {
            // Auto: use max(30, minFillRate) — fast enough to fill, but at least 30 for small counts
            fillRate = Math.max(30, minFillRate);
        }

        LOGGER.atInfo().log("[Research:Sustained] Fill rate calculation: count=%d, durationTicks=%d, minFillRate=%d, actualFillRate=%d",
            count, durationTicks, minFillRate, fillRate);

        ResearchSession session = new ResearchSession(
            "sustained", shapes, shapeType, durationMs, fillRate, maintenanceRate,
            marginMs, 0, 0, new WeakReference<>(world), new WeakReference<>(sender)
        );

        LOGGER.atInfo().log(
            "[Research:Sustained] Starting: %d %s shapes. Duration=%dms, FillRate=%d/tick, MaintenanceRate=%d/tick, Margin=%dms, CycleTime=%.1fs",
            count, shapeType, durationMs, fillRate, maintenanceRate, marginMs,
            (double) count / (maintenanceRate * 20.0));

        sender.sendMessage(Message.empty()
            .insert(Message.raw("[Research:Sustained] ").color(MessageColors.GOLD))
            .insert(Message.raw(String.format(
                "Starting %d %s shapes. Fill=%d/tick, Maintain=%d/tick, Duration=%dms. Watch for ring gaps.",
                count, shapeType, fillRate, maintenanceRate, durationMs)).color(MessageColors.WHITE)));

        reportRingBreakdown(sender, count);

        // Clear existing shapes and start
        DebugUtils.clear(world);
        startSession(session);
    }

    // ═══════════════════════════════════════════════════════════════════
    // RAMP TEST
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Starts low, adds N more shapes every M seconds, maintains all.
     * The most informative test — watch the ceiling approach in real-time.
     */
    private void handleRamp(
            @Nonnull PlayerRef sender,
            @Nonnull World world,
            @Nonnull Vector3d playerPos,
            @Nonnull String input) {

        if (activeSession != null) {
            sender.sendMessage(Message.raw("A test is already running. Use 'stop' first.").color(MessageColors.ERROR));
            return;
        }

        int step = parseIntAfter(input, "step", 100);
        int intervalSeconds = parseIntAfter(input, "interval", 5);
        int max = parseIntAfter(input, "max", 5000);
        String shapeType = parseStringAfter(input, "shape", SHAPE_LINE);
        int durationMs = parseIntAfter(input, "duration", 5000);
        int marginMs = parseIntAfter(input, "margin", 500);

        step = Math.max(10, Math.min(step, 5000));
        intervalSeconds = Math.max(2, Math.min(intervalSeconds, 60));
        max = Math.max(step, Math.min(max, 500000));

        double cx = playerPos.x;
        double cy = playerPos.y + Y_OFFSET;
        double cz = playerPos.z;

        // Start with the first batch
        List<ShapeSpec> initialShapes = generateRingLayout(cx, cy, cz, step);
        int initialMaintenanceRate = Math.max(1,
            (int) Math.ceil((double) step / Math.max(1, (durationMs - marginMs) / 50)));

        // Auto-calculate fill rate for the MAX target (worst case).
        // Use min(duration, interval) as the fill window — the interval is the actual
        // deadline because the cursor resets to 0 on each batch expansion.
        int intervalMs = intervalSeconds * 1000;
        int fillWindowMs = Math.min(durationMs, intervalMs);
        int fillWindowTicks = Math.max(1, fillWindowMs / 50);
        int minFillRate = Math.max(1, (int) Math.ceil((double) max / fillWindowTicks));
        int autoFillRate = Math.max(30, minFillRate);

        LOGGER.atInfo().log("[Research:Ramp] Fill rate: max=%d, fillWindowMs=%d (%d ticks), minFillRate=%d, autoFillRate=%d",
            max, fillWindowMs, fillWindowTicks, minFillRate, autoFillRate);

        ResearchSession session = new ResearchSession(
            "ramp", initialShapes, shapeType, durationMs, autoFillRate, initialMaintenanceRate,
            marginMs, step, intervalSeconds, new WeakReference<>(world), new WeakReference<>(sender)
        );
        session.rampStep = step;
        session.rampMax = max;
        session.rampCenterX = cx;
        session.rampCenterY = cy;
        session.rampCenterZ = cz;
        session.rampNextAddTick = intervalSeconds * 20; // ticks until next batch
        session.rampCurrentTarget = step;

        LOGGER.atInfo().log(
            "[Research:Ramp] Starting: step=%d, interval=%ds, max=%d, shape=%s, duration=%dms",
            step, intervalSeconds, max, shapeType, durationMs);

        sender.sendMessage(Message.empty()
            .insert(Message.raw("[Research:Ramp] ").color(MessageColors.GOLD))
            .insert(Message.raw(String.format(
                "Starting ramp: +%d shapes every %ds, max %d. Shape=%s. Watch for ring gaps as count climbs.",
                step, intervalSeconds, max, shapeType)).color(MessageColors.WHITE)));

        DebugUtils.clear(world);
        startSession(session);
    }

    // ═══════════════════════════════════════════════════════════════════
    // STOP / STATUS
    // ═══════════════════════════════════════════════════════════════════

    private void handleStop(@Nonnull PlayerRef sender, @Nonnull World world) {
        ResearchSession session = activeSession;
        if (session == null) {
            sender.sendMessage(Message.raw("No test is running.").color(MessageColors.WHITE));
            return;
        }

        stopSession();
        DebugUtils.clear(world);

        LOGGER.atInfo().log("[Research] Stopped. Total ticks=%d, totalSent=%d, mode=%s",
            session.tickCount, session.totalShapesSent, session.mode);

        sender.sendMessage(Message.empty()
            .insert(Message.raw("[Research] ").color(MessageColors.GOLD))
            .insert(Message.raw(String.format(
                "Stopped. Ran %d ticks (%.1fs), sent %d total shape packets. Shapes cleared.",
                session.tickCount, session.tickCount / 20.0, session.totalShapesSent))
                .color(MessageColors.WHITE)));
    }

    private void handleStatus(@Nonnull PlayerRef sender) {
        ResearchSession session = activeSession;
        if (session == null) {
            sender.sendMessage(Message.raw("No test is running.").color(MessageColors.WHITE));
            return;
        }

        int aliveEstimate = estimateAliveShapes(session);

        sender.sendMessage(Message.empty()
            .insert(Message.raw("[Research:Status] ").color(MessageColors.GOLD))
            .insert(Message.raw(String.format(
                "Mode=%s | Shapes=%d | Alive~%d | Tick=%d (%.1fs) | Rate=%d/tick | Phase=%s | Cycles=%d",
                session.mode, session.shapes.size(), aliveEstimate,
                session.tickCount, session.tickCount / 20.0,
                session.fillDone ? session.maintenanceRate : session.fillRate,
                session.fillDone ? "maintain" : "fill",
                session.cycleCount)).color(MessageColors.WHITE)));
    }

    // ═══════════════════════════════════════════════════════════════════
    // SESSION LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════

    private void startSession(@Nonnull ResearchSession session) {
        activeSession = session;

        if (scheduler == null || scheduler.isShutdown()) {
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "ResearchTicker");
                t.setDaemon(true);
                return t;
            });
        }

        scheduledTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                tickSession();
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("[Research] Tick error");
            }
        }, 50, 50, TimeUnit.MILLISECONDS);
    }

    private static void stopSession() {
        activeSession = null;
        if (scheduledTask != null) {
            scheduledTask.cancel(false);
            scheduledTask = null;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // TICK LOOP
    // ═══════════════════════════════════════════════════════════════════

    private static void tickSession() {
        ResearchSession session = activeSession;
        if (session == null) return;

        World world = session.worldRef.get();
        if (world == null || !world.isAlive()) {
            LOGGER.atWarning().log("[Research] World gone — stopping session");
            stopSession();
            return;
        }

        world.execute(() -> tickOnWorldThread(session, world));
    }

    private static void tickOnWorldThread(@Nonnull ResearchSession session, @Nonnull World world) {
        if (activeSession != session) return; // session was stopped

        session.tickCount++;
        int total = session.shapes.size();
        if (total == 0) return;

        float durationSeconds = session.durationMs / 1000.0f;
        int sentThisTick = 0;

        // ── RAMP: add more shapes on schedule ──────────────────────
        if ("ramp".equals(session.mode) && session.rampCurrentTarget < session.rampMax) {
            session.rampTickCounter++;
            if (session.rampTickCounter >= session.rampNextAddTick) {
                session.rampTickCounter = 0;
                int newTarget = Math.min(session.rampCurrentTarget + session.rampStep, session.rampMax);
                List<ShapeSpec> expanded = generateRingLayout(
                    session.rampCenterX, session.rampCenterY, session.rampCenterZ, newTarget);
                session.shapes = expanded;
                session.rampCurrentTarget = newTarget;
                total = expanded.size();

                // Recalculate maintenance rate for new count
                int targetCycleTicks = Math.max(1, (session.durationMs - session.marginMs) / 50);
                session.maintenanceRate = Math.max(1, (int) Math.ceil((double) total / targetCycleTicks));

                // Recalculate fill rate: must fill all shapes within the RAMP INTERVAL
                // (not the duration — the interval is the actual deadline because the
                // cursor resets to 0 on each batch expansion)
                int intervalMs = session.rampNextAddTick * 50;
                int fillWindowMs = Math.min(session.durationMs, intervalMs);
                int fillWindowTicks = Math.max(1, fillWindowMs / 50);
                int rampMinFillRate = Math.max(1, (int) Math.ceil((double) total / fillWindowTicks));
                session.fillRate = Math.max(30, rampMinFillRate);

                // Reset fill to send new shapes
                session.fillDone = false;
                session.sendCursor = 0;

                int ringCount = Math.min(10, (int) Math.ceil(newTarget / 10.0));
                LOGGER.atInfo().log(
                    "[Research:Ramp] Added batch → %d shapes (%d rings), fill=%d/tick, maintenance=%d/tick, cycle=%.1fs",
                    newTarget, ringCount, session.fillRate, session.maintenanceRate,
                    (double) total / (session.maintenanceRate * 20.0));

                // Notify player
                PlayerRef player = session.senderRef.get();
                if (player != null) {
                    player.sendMessage(Message.empty()
                        .insert(Message.raw("[Research:Ramp] ").color(MessageColors.GOLD))
                        .insert(Message.raw(String.format("→ %d shapes | maintain=%d/tick | rings=%d",
                            newTarget, session.maintenanceRate, ringCount)).color(MessageColors.WHITE)));
                }
            }
        }

        // ── TWO-PHASE SEND ─────────────────────────────────────────
        int rate = session.fillDone ? session.maintenanceRate : session.fillRate;

        for (int i = 0; i < rate; i++) {
            if (session.sendCursor >= total) {
                session.sendCursor = 0;
                session.cycleCount++;
                if (!session.fillDone) {
                    session.fillDone = true;
                    long fillTimeMs = session.tickCount * 50L;
                    LOGGER.atInfo().log(
                        "[Research] Fill complete: %d shapes in %d ticks (%dms). Switching to maintenance at %d/tick. CycleTime=%.1fs",
                        total, session.tickCount, fillTimeMs, session.maintenanceRate,
                        (double) total / (session.maintenanceRate * 20.0));
                    break; // switch rate on next tick
                }
            }

            ShapeSpec spec = session.shapes.get(session.sendCursor);
            session.sendCursor++;

            sendShape(world, spec, session.shapeType, durationSeconds, DebugUtils.FLAG_NO_WIREFRAME);
            sentThisTick++;
        }

        session.totalShapesSent += sentThisTick;

        // ── LOGGING ────────────────────────────────────────────────
        // Log every tick during fill, every 20 ticks (1s) during maintenance
        boolean shouldLog = !session.fillDone || (session.tickCount % 20 == 0);
        if (shouldLog && sentThisTick > 0) {
            int aliveEstimate = estimateAliveShapes(session);
            LOGGER.atInfo().log(
                "[Research:%s] tick=%d sent=%d total_target=%d alive_est=%d cycle=%d rate=%d/tick phase=%s cursor=%d/%d",
                session.mode, session.tickCount, sentThisTick, total, aliveEstimate,
                session.cycleCount, rate, session.fillDone ? "maintain" : "fill",
                session.sendCursor, total);
        }

        // ── PERIODIC PLAYER SUMMARY (every 10s) ───────────────────
        if (session.tickCount % 200 == 0) {
            PlayerRef player = session.senderRef.get();
            if (player != null) {
                int aliveEstimate = estimateAliveShapes(session);
                player.sendMessage(Message.empty()
                    .insert(Message.raw("[Research] ").color(MessageColors.GOLD))
                    .insert(Message.raw(String.format(
                        "%.0fs | target=%d | alive~%d | rate=%d/tick | cycles=%d | totalSent=%d",
                        session.tickCount / 20.0, total, aliveEstimate,
                        session.fillDone ? session.maintenanceRate : session.fillRate,
                        session.cycleCount, session.totalShapesSent)).color(MessageColors.WHITE)));
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // SHAPE SENDING
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Sends a single shape to the world based on the configured shape type.
     */
    private static void sendShape(
            @Nonnull World world,
            @Nonnull ShapeSpec spec,
            @Nonnull String shapeType,
            float durationSeconds,
            int flags) {

        switch (shapeType) {
            case SHAPE_LINE -> DebugUtils.addLine(world,
                spec.start, spec.end, spec.color, LINE_THICKNESS, durationSeconds, flags);

            case SHAPE_SPHERE -> DebugUtils.addSphere(world,
                spec.start, spec.color, SHAPE_SCALE, durationSeconds);

            case SHAPE_CUBE -> DebugUtils.addCube(world,
                spec.start, spec.color, SHAPE_SCALE, durationSeconds);

            case SHAPE_CYLINDER -> DebugUtils.addCylinder(world,
                spec.start, spec.color, SHAPE_SCALE, durationSeconds);

            case SHAPE_CONE -> DebugUtils.addCone(world,
                spec.start, spec.color, SHAPE_SCALE, durationSeconds);

            case SHAPE_DISC -> DebugUtils.addDisc(world,
                spec.start.x, spec.start.y, spec.start.z,
                SHAPE_SCALE * 2, spec.color, durationSeconds, flags);

            default -> DebugUtils.addLine(world,
                spec.start, spec.end, spec.color, LINE_THICKNESS, durationSeconds, flags);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // RING LAYOUT GENERATOR
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Generates N shapes arranged in concentric colored rings.
     *
     * <p>Up to 10 rings, each with a distinct color. Shapes are evenly distributed
     * across rings, with each shape as a short radial line segment (for line mode)
     * or a positioned point (for other shape modes).
     *
     * <p>Visual design: each ring is at a different radius, making it trivially
     * countable. If ring 7 renders fully but ring 8 has gaps, the ceiling is
     * between ring 7's count and ring 8's count.
     *
     * @param cx     Center X
     * @param cy     Center Y
     * @param cz     Center Z
     * @param count  Total number of shapes
     * @return List of shape specifications
     */
    @Nonnull
    private static List<ShapeSpec> generateRingLayout(double cx, double cy, double cz, int count) {
        int ringCount = Math.min(10, Math.max(1, (int) Math.ceil(count / 20.0)));
        // Ensure at least 20 per ring for countability, but if count < 200, use fewer rings
        if (count < ringCount * 20) {
            ringCount = Math.max(1, count / 20);
        }
        if (ringCount == 0) ringCount = 1;

        int perRing = count / ringCount;
        int remainder = count % ringCount;

        // Phase 1: generate shapes grouped by ring into separate lists
        List<List<ShapeSpec>> ringLists = new ArrayList<>(ringCount);
        for (int ring = 0; ring < ringCount; ring++) {
            int shapesThisRing = perRing + (ring < remainder ? 1 : 0);
            double radius = BASE_RADIUS + ring * RING_SPACING;
            Vector3f color = RING_COLORS[ring % RING_COLORS.length];

            List<ShapeSpec> ringShapes = new ArrayList<>(shapesThisRing);
            for (int i = 0; i < shapesThisRing; i++) {
                double angle = (2.0 * Math.PI * i) / shapesThisRing;
                double px = cx + radius * Math.cos(angle);
                double pz = cz + radius * Math.sin(angle);

                double dx = Math.cos(angle) * LINE_LENGTH;
                double dz = Math.sin(angle) * LINE_LENGTH;

                Vector3d start = new Vector3d(px, cy, pz);
                Vector3d end = new Vector3d(px + dx, cy, pz + dz);
                ringShapes.add(new ShapeSpec(start, end, color, ring));
            }
            ringLists.add(ringShapes);
        }

        // Phase 2: interleave across rings so fills hit ALL rings simultaneously.
        // Order: [R0#0, R1#0, R2#0, ..., R9#0, R0#1, R1#1, ...]
        // This prevents outer rings from going dark during ramp transitions —
        // every ring gets new shapes from the very first tick of a fill.
        List<ShapeSpec> shapes = new ArrayList<>(count);
        int maxPerRing = perRing + (remainder > 0 ? 1 : 0);
        for (int i = 0; i < maxPerRing; i++) {
            for (int ring = 0; ring < ringCount; ring++) {
                List<ShapeSpec> ringShapes = ringLists.get(ring);
                if (i < ringShapes.size()) {
                    shapes.add(ringShapes.get(i));
                }
            }
        }

        return shapes;
    }

    // ═══════════════════════════════════════════════════════════════════
    // ESTIMATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Estimates how many shapes should currently be alive on the client,
     * based on send history and duration.
     *
     * <p>This is a server-side estimate — the client may have fewer if packets
     * were dropped. The gap between this estimate and what you see is the
     * actual finding.
     */
    private static int estimateAliveShapes(@Nonnull ResearchSession session) {
        if (!session.fillDone) {
            // During fill, all sent shapes should still be alive (duration hasn't elapsed)
            return Math.min(session.sendCursor, session.shapes.size());
        }

        // During maintenance: shapes from the previous cycle are expiring while
        // new ones are sent. At steady state, exactly N shapes should be alive.
        return session.shapes.size();
    }

    // ═══════════════════════════════════════════════════════════════════
    // RING BREAKDOWN REPORTER
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Sends a chat breakdown showing how many shapes are in each ring,
     * so the observer can map visual gaps to exact counts.
     */
    private static void reportRingBreakdown(@Nonnull PlayerRef sender, int totalCount) {
        int ringCount = Math.min(10, Math.max(1, (int) Math.ceil(totalCount / 20.0)));
        if (totalCount < ringCount * 20) {
            ringCount = Math.max(1, totalCount / 20);
        }
        if (ringCount == 0) ringCount = 1;

        int perRing = totalCount / ringCount;
        int remainder = totalCount % ringCount;

        StringBuilder breakdown = new StringBuilder("Ring breakdown: ");
        int cumulative = 0;
        for (int ring = 0; ring < ringCount; ring++) {
            int shapesThisRing = perRing + (ring < remainder ? 1 : 0);
            cumulative += shapesThisRing;
            String colorName = ringColorName(ring);
            if (ring > 0) breakdown.append(" | ");
            breakdown.append(String.format("R%d(%s)=%d [cum:%d]", ring + 1, colorName, shapesThisRing, cumulative));
        }

        sender.sendMessage(Message.empty()
            .insert(Message.raw("[Research] ").color(MessageColors.GOLD))
            .insert(Message.raw(breakdown.toString()).color(MessageColors.WHITE)));
    }

    @Nonnull
    private static String ringColorName(int ringIndex) {
        return switch (ringIndex % 10) {
            case 0 -> "Red";
            case 1 -> "Cyan";
            case 2 -> "Gold";
            case 3 -> "Purple";
            case 4 -> "Green";
            case 5 -> "Orange";
            case 6 -> "Blue";
            case 7 -> "Pink";
            case 8 -> "White";
            case 9 -> "Yellow";
            default -> "?";
        };
    }

    // ═══════════════════════════════════════════════════════════════════
    // PARSING HELPERS
    // ═══════════════════════════════════════════════════════════════════

    private static int parseIntAfter(@Nonnull String input, @Nonnull String key, int defaultValue) {
        String[] parts = input.split("\\s+");
        // First try key=value format
        for (String part : parts) {
            if (part.startsWith(key + "=")) {
                try {
                    return Integer.parseInt(part.substring(key.length() + 1));
                } catch (NumberFormatException ignored) {}
            }
        }
        // Then try positional (first number after the key word)
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].equals(key) && i + 1 < parts.length) {
                try {
                    return Integer.parseInt(parts[i + 1]);
                } catch (NumberFormatException ignored) {}
            }
        }
        return defaultValue;
    }

    @Nonnull
    private static String parseStringAfter(@Nonnull String input, @Nonnull String key, @Nonnull String defaultValue) {
        String[] parts = input.split("\\s+");
        for (String part : parts) {
            if (part.startsWith(key + "=")) {
                return part.substring(key.length() + 1);
            }
        }
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].equals(key) && i + 1 < parts.length) {
                return parts[i + 1];
            }
        }
        return defaultValue;
    }

    // ═══════════════════════════════════════════════════════════════════
    // USAGE
    // ═══════════════════════════════════════════════════════════════════

    private void sendUsage(@Nonnull PlayerRef sender) {
        sender.sendMessage(Message.empty()
            .insert(Message.raw("[Research] ").color(MessageColors.GOLD))
            .insert(Message.raw("Usage:").color(MessageColors.WHITE)));
        sender.sendMessage(Message.raw("  burst <N> [shape=line]").color(MessageColors.WHITE));
        sender.sendMessage(Message.raw("    → Send N shapes in 1 tick. Tests per-frame packet limit.").color(MessageColors.GRAY));
        sender.sendMessage(Message.raw("  sustained <N> [shape=line] [duration=5000] [fillrate=30] [margin=500]").color(MessageColors.WHITE));
        sender.sendMessage(Message.raw("    → Two-phase: fill then maintain N shapes. Tests concurrent cap.").color(MessageColors.GRAY));
        sender.sendMessage(Message.raw("  ramp [step=100] [interval=5] [max=5000] [shape=line]").color(MessageColors.WHITE));
        sender.sendMessage(Message.raw("    → Add +step shapes every interval seconds. Watch ceiling approach.").color(MessageColors.GRAY));
        sender.sendMessage(Message.raw("  stop / status").color(MessageColors.WHITE));
        sender.sendMessage(Message.raw("  Shapes: line, sphere, cube, cylinder, cone, disc").color(MessageColors.GRAY));
    }

    // ═══════════════════════════════════════════════════════════════════
    // INNER CLASSES
    // ═══════════════════════════════════════════════════════════════════

    /**
     * A single shape specification for the ring layout.
     */
    private record ShapeSpec(
        Vector3d start,
        Vector3d end,
        Vector3f color,
        int ringIndex
    ) {}

    /**
     * Mutable state for a running research session.
     */
    private static final class ResearchSession {
        final String mode;
        List<ShapeSpec> shapes; // mutable for ramp mode
        final String shapeType;
        final int durationMs;
        int fillRate; // mutable for ramp mode (auto-scales with count)
        int maintenanceRate; // mutable for ramp mode
        final int marginMs;
        final WeakReference<World> worldRef;
        final WeakReference<PlayerRef> senderRef;

        // Two-phase state
        int sendCursor = 0;
        boolean fillDone = false;
        int cycleCount = 0;

        // Counters
        int tickCount = 0;
        long totalShapesSent = 0;

        // Ramp-specific
        int rampStep;
        int rampMax;
        double rampCenterX;
        double rampCenterY;
        double rampCenterZ;
        int rampNextAddTick;
        int rampTickCounter = 0;
        int rampCurrentTarget;

        ResearchSession(
                @Nonnull String mode,
                @Nonnull List<ShapeSpec> shapes,
                @Nonnull String shapeType,
                int durationMs,
                int fillRate,
                int maintenanceRate,
                int marginMs,
                int rampStep,
                int rampIntervalSeconds,
                @Nonnull WeakReference<World> worldRef,
                @Nonnull WeakReference<PlayerRef> senderRef) {
            this.mode = mode;
            this.shapes = shapes;
            this.shapeType = shapeType;
            this.durationMs = durationMs;
            this.fillRate = fillRate;
            this.maintenanceRate = maintenanceRate;
            this.marginMs = marginMs;
            this.rampStep = rampStep;
            this.rampNextAddTick = rampIntervalSeconds * 20;
            this.worldRef = worldRef;
            this.senderRef = senderRef;
        }
    }
}
