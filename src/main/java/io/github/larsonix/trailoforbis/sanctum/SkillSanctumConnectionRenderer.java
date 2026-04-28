package io.github.larsonix.trailoforbis.sanctum;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.Color;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.skilltree.NodeState;
import io.github.larsonix.trailoforbis.skilltree.SkillTreeManager;
import io.github.larsonix.trailoforbis.skilltree.SkillTreeRegion;
import io.github.larsonix.trailoforbis.skilltree.config.SkillNode;
import io.github.larsonix.trailoforbis.util.OklchColorUtil;
import javax.annotation.Nonnull;
import java.util.*;

/**
 * Renders connections between skill tree nodes as DebugUtils lines.
 *
 * <h2>Architecture: Clear+Burst on state change, Two-Phase for maintenance</h2>
 *
 * <p>On <b>initial load</b>: pre-compute visuals, fill all shapes at high rate
 * (two-phase fill+maintain). On <b>state changes</b> (allocate, deallocate, respec):
 * {@link #recomputeAndBurst} clears all stale shapes and burst-sends every
 * connection in a single tick. Research confirms 260 {@code addLine()} calls in
 * one tick is trivial (&lt;1ms). Steady-state maintenance refreshes shapes
 * via round-robin before they expire.
 *
 * <p>Why clear+burst instead of incremental overlay: notable/keystone nodes
 * shift their Y position on state change (different model anchor points).
 * Old beams at old positions are fire-and-forget — they cannot be individually
 * cancelled. Without {@code clear()}, ghost beams persist for up to 5 seconds
 * at the wrong Y offset. Clear+burst eliminates this in one clean step.
 *
 * <p>Connection visual types:
 * <ul>
 *   <li><b>Allocated</b>: Both nodes ALLOCATED — full-brightness region color</li>
 *   <li><b>Frontier</b>: Both nodes reachable but not both allocated — 50% luminosity</li>
 *   <li><b>Locked</b>: One or both nodes LOCKED — dim gray</li>
 *   <li><b>Bridge</b>: Cross-arm connections — OKLCH-blended region colors</li>
 * </ul>
 *
 * <p>Each renderer is per-instance (one per player sanctum).
 *
 * @see SkillSanctumInstance
 * @see <a href="docs/reference/hytale-modding/debugutils-shape-limits.md">DebugUtils research</a>
 */
public class SkillSanctumConnectionRenderer {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // ═══════════════════════════════════════════════════════════════════
    // CONSTANTS
    // ═══════════════════════════════════════════════════════════════════

    /** Minimum connection distance (skip very short ones). */
    private static final double MIN_CONNECTION_DISTANCE = 1.0;

    /** Particle system for allocation burst effect. */
    private static final String BURST_PARTICLE_SYSTEM = "Explosion_Medium";

    /** Range for collecting nearby players (sanctum owner). */
    private static final double PLAYER_COLLECT_RANGE = 200.0;

    /** Packed color for locked (dim) connections. */
    private static final int LOCKED_COLOR = 0x333344;

    // ── Connection classification ────────────────────────────────────
    /** Both nodes LOCKED or at least one LOCKED → dim gray. */
    private static final int CONNECTION_LOCKED = 0;
    /** Both nodes reachable (ALLOCATED or AVAILABLE) but NOT both ALLOCATED → frontier. */
    private static final int CONNECTION_FRONTIER = 1;
    /** Both nodes ALLOCATED → fully owned path. */
    private static final int CONNECTION_ALLOCATED = 2;
    /**
     * OKLCH lightness multiplier for frontier beams.
     * Applied to the region color to produce a visually distinct "half-lit" beam
     * between your allocated path and the nodes you can reach next.
     */
    private static final double FRONTIER_LIGHTNESS_FACTOR = 0.5;

    // Bridge beams are now computed dynamically via OKLCH blending of the two
    // endpoint region colors — see computeBridgeColor(). No static constant needed.

    /**
     * Extra Y offset to account for item entity model hover.
     * Hytale renders dropped item models floating above the entity position.
     * Without this, beams target the entity coordinate but the orb visually
     * appears higher.
     */
    private static final double ITEM_MODEL_HOVER_Y = 0.4;

    /** Line thickness for DebugUtils.addLine() connections (in blocks). */
    private static final double LINE_THICKNESS = 0.08;

    // ═══════════════════════════════════════════════════════════════════
    // DEPENDENCIES
    // ═══════════════════════════════════════════════════════════════════

    private final SkillTreeManager skillTreeManager;
    private final UUID playerId;

    // ═══════════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════════

    /** All pre-computed connection visuals. */
    private final List<ConnectionVisual> allVisuals = new ArrayList<>();

    /** Track which connections exist (for dedup during iteration). */
    private final Set<String> createdConnections = new HashSet<>();

    // ═══════════════════════════════════════════════════════════════════
    // RENDER STATE
    // ═══════════════════════════════════════════════════════════════════

    private boolean ready = false;

    /** Round-robin cursor — advances through allVisuals, NEVER resets except on recompute. */
    private int sendCursor = 0;

    /** Whether the initial fill phase has completed (all connections sent at least once). */
    private boolean initialFillDone = false;

    /** Timestamp of the first tick call — used to delay beam sending until client is ready. */
    private long firstTickTimestamp = 0;

    /** How many times we've logged beam sends at INFO level (caps at 3). */
    private int sendLogCount = 0;

    // ═══════════════════════════════════════════════════════════════════
    // CONFIGURATION (loaded from YAML via setters)
    // ═══════════════════════════════════════════════════════════════════

    /** How long each DebugUtils shape lives on the client (ms). */
    private int beamDurationMs = 5000;

    /** Shapes per tick during initial fill phase (high rate for fast startup). */
    private int initialFillRate = 30;

    /** Margin (ms) to refresh shapes before they expire. */
    private int refreshMarginMs = 500;

    /** Delay after first tick before sending beams, to let client finish world loading. */
    private static final long CLIENT_READY_DELAY_MS = 2500;

    // ═══════════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Creates a new connection renderer for a specific player's sanctum.
     *
     * @param skillTreeManager The skill tree manager for node data
     * @param playerId         The owning player's UUID
     */
    public SkillSanctumConnectionRenderer(@Nonnull SkillTreeManager skillTreeManager, @Nonnull UUID playerId) {
        this.skillTreeManager = Objects.requireNonNull(skillTreeManager);
        this.playerId = Objects.requireNonNull(playerId);
    }

    // ═══════════════════════════════════════════════════════════════════
    // CONFIGURATION
    // ═══════════════════════════════════════════════════════════════════

    /** Sets how long each shape lives on the client (ms). */
    public void setBeamDurationMs(int ms) { this.beamDurationMs = ms; }

    /** Sets shapes per tick during initial fill phase. */
    public void setInitialFillRate(int n) { this.initialFillRate = n; }

    /** Sets margin (ms) before expiry to refresh shapes. */
    public void setRefreshMarginMs(int ms) { this.refreshMarginMs = ms; }

    /** Returns true if visuals have been computed and the renderer is ready. */
    public boolean isReady() { return ready; }

    // ═══════════════════════════════════════════════════════════════════
    // CONNECTION COMPUTATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Computes all connection visuals for the current allocation state.
     *
     * <p>Call this once during initialization. This method only computes
     * the visual state — it doesn't send any packets.
     *
     * @param allocatedNodes Set of currently allocated node IDs
     */
    public void computeConnectionVisuals(@Nonnull Set<String> allocatedNodes) {
        allVisuals.clear();
        createdConnections.clear();

        Set<String> availableNodes = calculateAvailableNodes(allocatedNodes);

        Collection<SkillNode> allNodes = skillTreeManager.getAllNodes();
        for (SkillNode node : allNodes) {
            String nodeId = node.getId();
            for (String connectedId : node.getConnections()) {
                computeConnection(node, nodeId, connectedId, allocatedNodes, availableNodes);
            }
        }

        ready = true;
        LOGGER.atInfo().log("Computed %d connection visuals for player %s",
            allVisuals.size(), playerId.toString().substring(0, 8));
    }

    /**
     * Computes a single connection visual and stores it.
     */
    private void computeConnection(
            @Nonnull SkillNode sourceNode,
            @Nonnull String sourceId,
            @Nonnull String targetId,
            @Nonnull Set<String> allocatedNodes,
            @Nonnull Set<String> availableNodes) {

        // Deduplicate: sorted key
        String connectionKey = sourceId.compareTo(targetId) < 0
            ? sourceId + ":" + targetId
            : targetId + ":" + sourceId;

        if (createdConnections.contains(connectionKey)) {
            return;
        }
        createdConnections.add(connectionKey);

        Optional<SkillNode> targetOpt = skillTreeManager.getNode(targetId);
        if (targetOpt.isEmpty()) {
            return;
        }
        SkillNode targetNode = targetOpt.get();

        // Determine node states (needed for Y offset and visual properties)
        NodeState sourceState = getNodeState(sourceId, allocatedNodes, availableNodes);
        NodeState targetState = getNodeState(targetId, allocatedNodes, availableNodes);

        // Get world positions, shifted to visual centers (match spawner Y offsets)
        Vector3d rawSourcePos = GalaxySpiralLayoutMapper.toWorldPosition(sourceNode);
        Vector3d rawTargetPos = GalaxySpiralLayoutMapper.toWorldPosition(targetNode);
        Vector3d sourcePos = new Vector3d(rawSourcePos.x, rawSourcePos.y + getNodeYOffset(sourceNode, sourceState), rawSourcePos.z);
        Vector3d targetPos = new Vector3d(rawTargetPos.x, rawTargetPos.y + getNodeYOffset(targetNode, targetState), rawTargetPos.z);

        double distance = sourcePos.distanceTo(targetPos);
        if (distance < MIN_CONNECTION_DISTANCE) {
            return;
        }

        // Determine connection type
        SkillTreeRegion sourceRegion = sourceNode.getSkillTreeRegion();
        SkillTreeRegion targetRegion = targetNode.getSkillTreeRegion();
        boolean isBridge = sourceRegion != targetRegion && !sourceRegion.isCore() && !targetRegion.isCore();

        // Classify connection into three visual tiers:
        //   ALLOCATED  = both nodes owned     → full-brightness region color
        //   FRONTIER   = reachable but unowned → 50% luminosity region color
        //   LOCKED     = unreachable           → dim gray
        int connectionClass = classifyConnection(sourceId, targetId, allocatedNodes, availableNodes);

        // Determine packed color via OKLCH color system
        int packedColor;
        if (connectionClass == CONNECTION_LOCKED) {
            packedColor = LOCKED_COLOR;
        } else {
            int baseColor;
            if (isBridge) {
                baseColor = computeBridgeColor(sourceRegion, targetRegion);
            } else {
                SkillTreeRegion region = sourceRegion.isCore() ? targetRegion : sourceRegion;
                baseColor = regionToPackedColor(region);
            }
            packedColor = (connectionClass == CONNECTION_FRONTIER)
                ? dimRegionColor(baseColor, FRONTIER_LIGHTNESS_FACTOR)
                : baseColor;
        }

        // Precompute midpoint for distance sorting
        double midX = (sourcePos.x + targetPos.x) * 0.5;
        double midY = (sourcePos.y + targetPos.y) * 0.5;
        double midZ = (sourcePos.z + targetPos.z) * 0.5;

        // Ensure start corresponds to nodeIdA (lex-smaller) and end to nodeIdB.
        // This invariant is required by the merge algorithm's getPositionForNode().
        String nodeIdA, nodeIdB;
        Vector3d start, end;
        if (sourceId.compareTo(targetId) < 0) {
            nodeIdA = sourceId;
            nodeIdB = targetId;
            start = sourcePos;
            end = targetPos;
        } else {
            nodeIdA = targetId;
            nodeIdB = sourceId;
            start = targetPos;
            end = sourcePos;
        }

        allVisuals.add(new ConnectionVisual(
            connectionKey, nodeIdA, nodeIdB, start, end, packedColor, isBridge, midX, midY, midZ
        ));
    }

    // ═══════════════════════════════════════════════════════════════════
    // COLLINEAR SEGMENT MERGING
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Cosine of the angular threshold for collinearity (15 degrees).
     * Two segments are collinear if the angle between their direction vectors < 15 degrees.
     * cos(15°) ≈ 0.9659
     */
    private static final double COLLINEAR_COS_THRESHOLD = Math.cos(Math.toRadians(15.0));

    /**
     * Merges collinear, same-color, chain-connected segments to reduce visual count.
     *
     * <p>Algorithm:
     * <ol>
     *   <li>Group all visuals by packedColor</li>
     *   <li>For each color group, build adjacency: nodeId → list of visuals</li>
     *   <li>Compute degree per node (how many same-color connections touch it)</li>
     *   <li>Walk chains from degree != 2 nodes (endpoints/branches)</li>
     *   <li>Along each chain, merge consecutive collinear segments into one</li>
     *   <li>Rebuild allVisuals with merged + unmerged segments</li>
     * </ol>
     *
     * <p>Critical: color-exact. An allocated (bright) segment never merges with
     * a locked (dim gray) segment, even if geometrically collinear.
     */
    private void mergeCollinearSegments() {
        if (allVisuals.size() < 2) return;

        // Phase 1: Group by color
        Map<Integer, List<ConnectionVisual>> byColor = new HashMap<>();
        for (ConnectionVisual v : allVisuals) {
            byColor.computeIfAbsent(v.packedColor(), k -> new ArrayList<>()).add(v);
        }

        List<ConnectionVisual> merged = new ArrayList<>();

        for (Map.Entry<Integer, List<ConnectionVisual>> entry : byColor.entrySet()) {
            List<ConnectionVisual> colorGroup = entry.getValue();
            if (colorGroup.size() < 2) {
                merged.addAll(colorGroup);
                continue;
            }

            mergeColorGroup(colorGroup, merged);
        }

        allVisuals.clear();
        allVisuals.addAll(merged);
    }

    /**
     * Merges chains within a single color group.
     */
    private void mergeColorGroup(
            @Nonnull List<ConnectionVisual> group,
            @Nonnull List<ConnectionVisual> output) {

        // Build adjacency: nodeId → visuals touching it
        Map<String, List<ConnectionVisual>> adjacency = new HashMap<>();
        for (ConnectionVisual v : group) {
            adjacency.computeIfAbsent(v.nodeIdA(), k -> new ArrayList<>()).add(v);
            adjacency.computeIfAbsent(v.nodeIdB(), k -> new ArrayList<>()).add(v);
        }

        // Track which visuals have been consumed by a merge
        Set<String> consumed = new HashSet<>();

        // Walk chains from degree != 2 nodes (endpoints and branch points)
        for (String startNode : adjacency.keySet()) {
            int degree = adjacency.get(startNode).size();
            if (degree == 2) continue; // Interior chain node — will be visited by a chain walk

            for (ConnectionVisual startVisual : adjacency.get(startNode)) {
                if (consumed.contains(startVisual.connectionKey())) continue;

                // Walk a chain from this start visual
                List<ConnectionVisual> chain = new ArrayList<>();
                chain.add(startVisual);
                consumed.add(startVisual.connectionKey());

                String currentNode = otherNode(startVisual, startNode);

                // Keep walking while the current node has exactly 2 same-color connections
                while (adjacency.containsKey(currentNode) && adjacency.get(currentNode).size() == 2) {
                    // Find the other visual (not the one we came from)
                    ConnectionVisual nextVisual = null;
                    for (ConnectionVisual candidate : adjacency.get(currentNode)) {
                        if (!consumed.contains(candidate.connectionKey())) {
                            nextVisual = candidate;
                            break;
                        }
                    }
                    if (nextVisual == null) break;

                    // Collinearity check: compare direction of the whole chain vs the candidate
                    if (!isCollinear(chain, nextVisual)) break;

                    chain.add(nextVisual);
                    consumed.add(nextVisual.connectionKey());
                    currentNode = otherNode(nextVisual, currentNode);
                }

                // Emit the chain: merge if >1 segment, otherwise emit as-is
                emitChain(chain, startNode, currentNode, output);
            }
        }

        // Handle any remaining unconsumed visuals (isolated degree-2 cycles)
        for (ConnectionVisual v : group) {
            if (!consumed.contains(v.connectionKey())) {
                output.add(v);
            }
        }
    }

    /**
     * Checks if adding a candidate segment to the chain preserves collinearity.
     *
     * <p>Compares the overall chain direction (first start → last end) against
     * the candidate's direction. Uses dot product to avoid expensive trig.
     */
    private boolean isCollinear(@Nonnull List<ConnectionVisual> chain, @Nonnull ConnectionVisual candidate) {
        // Chain direction: from the very first point to the last point
        ConnectionVisual first = chain.get(0);
        ConnectionVisual last = chain.get(chain.size() - 1);

        // The chain spans from first.start to last.end (ordered by walk direction)
        // Use the first and last visual's endpoints to compute the overall direction
        double chainDx = last.end().x - first.start().x;
        double chainDy = last.end().y - first.start().y;
        double chainDz = last.end().z - first.start().z;
        double chainLen = Math.sqrt(chainDx * chainDx + chainDy * chainDy + chainDz * chainDz);
        if (chainLen < 0.001) return true; // Degenerate — allow merge

        // Candidate direction
        double candDx = candidate.end().x - candidate.start().x;
        double candDy = candidate.end().y - candidate.start().y;
        double candDz = candidate.end().z - candidate.start().z;
        double candLen = Math.sqrt(candDx * candDx + candDy * candDy + candDz * candDz);
        if (candLen < 0.001) return true;

        // Cosine of angle (absolute value to handle reversed direction)
        double dot = (chainDx * candDx + chainDy * candDy + chainDz * candDz) / (chainLen * candLen);
        return Math.abs(dot) >= COLLINEAR_COS_THRESHOLD;
    }

    /**
     * Emits a chain as either a single merged visual or multiple originals.
     *
     * <p>For chains of 2+, produces one visual from the chain's start point
     * to its end point. For single segments, emits the original unchanged.
     *
     * <p>Endpoint resolution: each ConnectionVisual's start/end correspond to
     * sourceId/targetId from computeConnection(), but nodeIdA/B are lex-sorted.
     * We resolve the correct world positions by finding which position belongs
     * to the walk's start node and which to the end node.
     */
    private void emitChain(
            @Nonnull List<ConnectionVisual> chain,
            @Nonnull String startNodeId,
            @Nonnull String endNodeId,
            @Nonnull List<ConnectionVisual> output) {

        if (chain.size() == 1) {
            output.add(chain.get(0));
            return;
        }

        // Resolve the world position for the chain's start node from the first visual
        ConnectionVisual first = chain.get(0);
        Vector3d mergedStart = getPositionForNode(first, startNodeId);

        // Resolve the world position for the chain's end node from the last visual
        ConnectionVisual last = chain.get(chain.size() - 1);
        Vector3d mergedEnd = getPositionForNode(last, endNodeId);

        double midX = (mergedStart.x + mergedEnd.x) * 0.5;
        double midY = (mergedStart.y + mergedEnd.y) * 0.5;
        double midZ = (mergedStart.z + mergedEnd.z) * 0.5;

        String nodeA = startNodeId.compareTo(endNodeId) < 0 ? startNodeId : endNodeId;
        String nodeB = startNodeId.compareTo(endNodeId) < 0 ? endNodeId : startNodeId;

        boolean anyBridge = chain.stream().anyMatch(ConnectionVisual::isBridge);

        output.add(new ConnectionVisual(
            "merged:" + nodeA + ":" + nodeB,
            nodeA, nodeB,
            mergedStart, mergedEnd,
            first.packedColor(),
            anyBridge,
            midX, midY, midZ
        ));
    }

    /**
     * Gets the world position associated with a specific nodeId in a ConnectionVisual.
     * Invariant: start always corresponds to nodeIdA, end to nodeIdB.
     */
    @Nonnull
    private static Vector3d getPositionForNode(@Nonnull ConnectionVisual v, @Nonnull String nodeId) {
        return nodeId.equals(v.nodeIdA()) ? v.start() : v.end();
    }

    /**
     * Gets the other node ID of a connection given one endpoint.
     */
    @Nonnull
    private static String otherNode(@Nonnull ConnectionVisual v, @Nonnull String nodeId) {
        return v.nodeIdA().equals(nodeId) ? v.nodeIdB() : v.nodeIdA();
    }

    // ═══════════════════════════════════════════════════════════════════
    // PROXIMITY REFRESH (core loop)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Ticks the proximity-based beam refresh.
     *
     * <p>Checks if a refresh is needed (time elapsed + player moved),
     * sorts connections by distance to player, and sends laser packets
     * for the closest ones within the beam budget.
     *
     * @param instance The sanctum instance (for world access)
     */
    public void tickProximityRefresh(@Nonnull SkillSanctumInstance instance) {
        if (!ready || allVisuals.isEmpty()) {
            return;
        }

        // Wait for client to finish loading the world before sending shapes
        if (firstTickTimestamp == 0) {
            firstTickTimestamp = System.currentTimeMillis();
        }
        if ((System.currentTimeMillis() - firstTickTimestamp) < CLIENT_READY_DELAY_MS) {
            return;
        }

        World world = instance.getSanctumWorld();
        if (world == null || !world.isAlive()) {
            return;
        }

        int total = allVisuals.size();
        float durationSeconds = beamDurationMs / 1000.0f;

        // Calculate maintenance rate: refresh all N connections within (duration - margin).
        // Each connection gets exactly one refresh per cycle — zero duplicates on the client.
        int targetCycleTicks = Math.max(1, (beamDurationMs - refreshMarginMs) / 50);
        int maintenanceRate = Math.max(1, (int) Math.ceil((double) total / targetCycleTicks));

        // Phase 1 (initial fill): high rate to populate all connections quickly.
        // Phase 2 (steady state): auto-calculated rate to maintain exactly N shapes.
        int rate = initialFillDone ? maintenanceRate : initialFillRate;

        int sent = 0;
        for (int i = 0; i < rate; i++) {
            if (sendCursor >= total) {
                sendCursor = 0;
                if (!initialFillDone) {
                    initialFillDone = true;
                    // Switch to maintenance rate on next tick
                    break;
                }
            }

            ConnectionVisual visual = allVisuals.get(sendCursor);
            sendCursor++;

            float r = ((visual.packedColor >> 16) & 0xFF) / 255.0f;
            float g = ((visual.packedColor >> 8) & 0xFF) / 255.0f;
            float b = (visual.packedColor & 0xFF) / 255.0f;

            com.hypixel.hytale.server.core.modules.debug.DebugUtils.addLine(
                world,
                visual.start,
                visual.end,
                new com.hypixel.hytale.math.vector.Vector3f(r, g, b),
                LINE_THICKNESS,
                durationSeconds,
                com.hypixel.hytale.server.core.modules.debug.DebugUtils.FLAG_NO_WIREFRAME
            );

            sent++;
        }

        if (sent > 0 && sendLogCount < 3) {
            sendLogCount++;
            LOGGER.atInfo().log("Connection renderer: %d visuals, rate=%d/tick (%s), cycle=%.1fs, duration=%.1fs",
                total, rate, initialFillDone ? "maintenance" : "filling",
                (double) total / (rate * 20.0), durationSeconds);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // STATE CHANGES (node allocation/deallocation/respec)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Recomputes all connection visuals, clears stale shapes, and burst-sends
     * every connection in a single tick.
     *
     * <p>This is the ONLY path for state changes (allocate, deallocate, respec).
     * The clear+burst pattern eliminates ghost beams from position shifts on
     * notable/keystone nodes:
     * <ol>
     *   <li>Recompute all visuals with the new allocation state</li>
     *   <li>{@code DebugUtils.clear()} — wipe all stale shapes in one packet</li>
     *   <li>Burst-send ALL ~260 connections as {@code addLine()} calls</li>
     *   <li>Skip fill phase — go directly to maintenance mode</li>
     * </ol>
     *
     * <p>Research confirms: 260 {@code addLine()} calls in one tick is trivial
     * (&lt;1ms server-side, all shapes render). {@code clear()} once on a state
     * change is correct — the confirmed failure is {@code clear()} every tick.
     *
     * <p><b>MUST be called from the world thread.</b>
     *
     * @param world          The sanctum world (for DebugUtils calls)
     * @param allocatedNodes Updated set of allocated node IDs
     * @see <a href="docs/reference/hytale-modding/debugutils-shape-limits.md">DebugUtils research</a>
     */
    public void recomputeAndBurst(@Nonnull World world, @Nonnull Set<String> allocatedNodes) {
        computeConnectionVisuals(allocatedNodes);

        if (allVisuals.isEmpty()) {
            return;
        }

        // Wipe all stale shapes — one packet, processed before the addLine packets below
        com.hypixel.hytale.server.core.modules.debug.DebugUtils.clear(world);

        // Burst-send every connection in this tick
        float durationSeconds = beamDurationMs / 1000.0f;
        for (ConnectionVisual visual : allVisuals) {
            float r = ((visual.packedColor >> 16) & 0xFF) / 255.0f;
            float g = ((visual.packedColor >> 8) & 0xFF) / 255.0f;
            float b = (visual.packedColor & 0xFF) / 255.0f;

            com.hypixel.hytale.server.core.modules.debug.DebugUtils.addLine(
                world,
                visual.start,
                visual.end,
                new com.hypixel.hytale.math.vector.Vector3f(r, g, b),
                LINE_THICKNESS,
                durationSeconds,
                com.hypixel.hytale.server.core.modules.debug.DebugUtils.FLAG_NO_WIREFRAME
            );
        }

        // All shapes are on the client — skip fill phase, go directly to maintenance
        initialFillDone = true;
        sendCursor = 0;

        LOGGER.atInfo().log("Clear+burst: sent %d connections for player %s",
            allVisuals.size(), playerId.toString().substring(0, 8));
    }

    // ═══════════════════════════════════════════════════════════════════
    // ALLOCATION BURST (particle effect)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Spawns a burst of particles when a node is allocated.
     *
     * @param instance The sanctum instance
     * @param nodeId   The allocated node ID
     */
    public void spawnAllocationBurst(@Nonnull SkillSanctumInstance instance, @Nonnull String nodeId) {
        if (!instance.isActive()) {
            return;
        }

        World world = instance.getSanctumWorld();
        if (world == null || !world.isAlive()) {
            return;
        }

        Optional<SkillNode> nodeOpt = skillTreeManager.getNode(nodeId);
        if (nodeOpt.isEmpty()) {
            return;
        }
        SkillNode node = nodeOpt.get();

        world.execute(() -> {
            Store<EntityStore> store = world.getEntityStore().getStore();
            List<Ref<EntityStore>> playerRefs = collectPlayersNearOrigin(store);
            if (playerRefs.isEmpty()) {
                return;
            }

            Vector3d position = GalaxySpiralLayoutMapper.toWorldPosition(node);

            SkillTreeRegion region = node.getSkillTreeRegion();
            Color burstColor = getArmBurstColor(region);

            spawnBurstParticles(store, playerRefs, position, burstColor, node);
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    // CLEANUP
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Cleans up all connection state. Call when the sanctum is destroyed.
     *
     * <p>No world cleanup needed — laser beams auto-expire and the
     * instance world is destroyed when the realm closes.
     */
    public void cleanup() {
        allVisuals.clear();
        createdConnections.clear();
        ready = false;
        initialFillDone = false;
        sendCursor = 0;
        firstTickTimestamp = 0;
        sendLogCount = 0;
        LOGGER.atFine().log("Cleaned up connection renderer for player %s",
            playerId.toString().substring(0, 8));
    }

    // ═══════════════════════════════════════════════════════════════════
    // NODE STATE HELPERS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Calculates which nodes are available based on allocated nodes.
     */
    @Nonnull
    private Set<String> calculateAvailableNodes(@Nonnull Set<String> allocatedNodes) {
        Set<String> available = new HashSet<>();

        Set<String> effectiveAllocated = new HashSet<>(allocatedNodes);
        effectiveAllocated.add("origin");

        for (String allocatedId : effectiveAllocated) {
            Optional<SkillNode> nodeOpt = skillTreeManager.getNode(allocatedId);
            if (nodeOpt.isPresent()) {
                available.addAll(nodeOpt.get().getConnections());
            }
        }

        available.removeAll(effectiveAllocated);
        return available;
    }

    /**
     * Gets the state of a node based on allocation sets.
     */
    @Nonnull
    private NodeState getNodeState(
            @Nonnull String nodeId,
            @Nonnull Set<String> allocatedNodes,
            @Nonnull Set<String> availableNodes) {

        Optional<SkillNode> nodeOpt = skillTreeManager.getNode(nodeId);
        if (nodeOpt.isPresent()) {
            SkillNode node = nodeOpt.get();
            if (node.isStartNode() || "origin".equals(nodeId)) {
                return NodeState.ALLOCATED;
            }
        }

        if (allocatedNodes.contains(nodeId)) {
            return NodeState.ALLOCATED;
        }
        if (availableNodes.contains(nodeId)) {
            return NodeState.AVAILABLE;
        }
        return NodeState.LOCKED;
    }

    /**
     * Computes the Y offset for a node's visual center, matching the spawner logic.
     *
     * <p>The node spawner applies Y offsets based on node type and state to account
     * for different item model anchor points (essences, crystals, gems). Laser beam
     * endpoints must use the same offsets to connect at the orb's visual center.
     *
     * @see SkillSanctumNodeSpawner#spawnNodeEssence
     */
    private static double getNodeYOffset(@Nonnull SkillNode node, @Nonnull NodeState state) {
        // Match the spawner's position offset
        double yOffset = GalaxySpiralLayoutMapper.NODE_VISUAL_Y_OFFSET; // 0.25
        boolean isOrigin = node.isStartNode() || "origin".equals(node.getId());
        if (node.isKeystone()) {
            if (state == NodeState.ALLOCATED) {
                yOffset += -1.0;
            } else if (state == NodeState.AVAILABLE) {
                yOffset += -0.5;
            }
        } else if (node.isNotable()) {
            if (state == NodeState.ALLOCATED) {
                yOffset += -0.5;
            } else if (state == NodeState.AVAILABLE) {
                yOffset += -0.25;
            }
        } else if (!isOrigin) {
            yOffset += -0.1; // basic nodes
        }
        // Add item model hover: the 3D model floats above the entity position
        yOffset += ITEM_MODEL_HOVER_Y;
        return yOffset;
    }

    /**
     * Classifies a connection into one of three visual tiers.
     *
     * <ul>
     *   <li>{@code CONNECTION_ALLOCATED} — both nodes allocated (your built path)</li>
     *   <li>{@code CONNECTION_FRONTIER} — both nodes reachable but not both allocated
     *       (shows what you can pick next)</li>
     *   <li>{@code CONNECTION_LOCKED} — at least one node is locked (unreachable)</li>
     * </ul>
     */
    private int classifyConnection(
            @Nonnull String sourceId,
            @Nonnull String targetId,
            @Nonnull Set<String> allocatedNodes,
            @Nonnull Set<String> availableNodes) {

        NodeState sourceState = getNodeState(sourceId, allocatedNodes, availableNodes);
        NodeState targetState = getNodeState(targetId, allocatedNodes, availableNodes);

        // Both allocated → fully owned path
        if (sourceState == NodeState.ALLOCATED && targetState == NodeState.ALLOCATED) {
            return CONNECTION_ALLOCATED;
        }

        // Both active (allocated or available) but not both allocated → frontier
        boolean sourceActive = sourceState == NodeState.ALLOCATED || sourceState == NodeState.AVAILABLE;
        boolean targetActive = targetState == NodeState.ALLOCATED || targetState == NodeState.AVAILABLE;
        if (sourceActive && targetActive) {
            return CONNECTION_FRONTIER;
        }

        return CONNECTION_LOCKED;
    }

    // ═══════════════════════════════════════════════════════════════════
    // COLOR MAPPING
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Converts a region's theme color to a packed 0xRRGGBB integer
     * for the laser beam packet.
     */
    private static int regionToPackedColor(@Nonnull SkillTreeRegion region) {
        return OklchColorUtil.fromHexString(region.getThemeColor());
    }

    /**
     * Dims a region color by scaling its OKLCH lightness.
     *
     * <p>Used for frontier beams (connections to available-but-unallocated nodes).
     * OKLCH preserves the hue identity while reducing perceived brightness.
     *
     * @param packedColor Base region color (0xRRGGBB)
     * @param factor      Lightness multiplier (0.5 = half brightness)
     * @return Dimmed packed color
     */
    private static int dimRegionColor(int packedColor, double factor) {
        double[] lch = OklchColorUtil.packedRGBToOklch(packedColor);
        return OklchColorUtil.adjustLightness(packedColor, lch[0] * factor);
    }

    /**
     * Computes the bridge beam color by blending two region theme colors
     * in OKLCH space (perceptually uniform, no muddy grays).
     *
     * @param regionA First region
     * @param regionB Second region
     * @return Packed 0xRRGGBB midpoint color
     */
    private static int computeBridgeColor(@Nonnull SkillTreeRegion regionA,
                                           @Nonnull SkillTreeRegion regionB) {
        int colorA = regionToPackedColor(regionA);
        int colorB = regionToPackedColor(regionB);
        return OklchColorUtil.blendOklch(colorA, colorB, 0.5);
    }

    // ═══════════════════════════════════════════════════════════════════
    // BURST COLOR MAPPING
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets the burst particle color for an arm (as Color for ParticleUtil).
     */
    @Nonnull
    private Color getArmBurstColor(@Nonnull SkillTreeRegion region) {
        return switch (region) {
            case CORE -> new Color((byte) 255, (byte) 215, (byte) 0);
            case FIRE -> new Color((byte) 255, (byte) 119, (byte) 85);
            case WATER -> new Color((byte) 85, (byte) 204, (byte) 238);
            case LIGHTNING -> new Color((byte) 255, (byte) 238, (byte) 85);
            case EARTH -> new Color((byte) 221, (byte) 170, (byte) 85);
            case VOID -> new Color((byte) 187, (byte) 119, (byte) 221);
            case WIND -> new Color((byte) 119, (byte) 221, (byte) 119);
            case HAVOC -> new Color((byte) 255, (byte) 68, (byte) 34);
            case JUGGERNAUT -> new Color((byte) 204, (byte) 102, (byte) 51);
            case STRIKER -> new Color((byte) 255, (byte) 170, (byte) 34);
            case WARDEN -> new Color((byte) 136, (byte) 170, (byte) 68);
            case WARLOCK -> new Color((byte) 153, (byte) 68, (byte) 204);
            case LICH -> new Color((byte) 102, (byte) 119, (byte) 170);
            case TEMPEST -> new Color((byte) 68, (byte) 187, (byte) 170);
            case SENTINEL -> new Color((byte) 119, (byte) 170, (byte) 136);
        };
    }

    // ═══════════════════════════════════════════════════════════════════
    // PARTICLE BURST HELPERS
    // ═══════════════════════════════════════════════════════════════════

    private void spawnBurstParticles(
            @Nonnull Store<EntityStore> accessor,
            @Nonnull List<Ref<EntityStore>> playerRefs,
            @Nonnull Vector3d center,
            @Nonnull Color color,
            @Nonnull SkillNode node) {

        double radius = node.isKeystone() ? 3.0 : (node.isNotable() ? 1.2 : 0.5);
        int particleCount = node.isKeystone() ? 32 : (node.isNotable() ? 20 : 14);
        float scale = node.isKeystone() ? 1.0f : (node.isNotable() ? 0.4f : 0.2f);

        for (int i = 0; i < particleCount; i++) {
            double phi = Math.acos(1 - 2.0 * (i + 0.5) / particleCount);
            double theta = Math.PI * (1 + Math.sqrt(5)) * i;

            Vector3d particlePos = new Vector3d(
                center.x + radius * Math.sin(phi) * Math.cos(theta),
                center.y + radius * Math.cos(phi),
                center.z + radius * Math.sin(phi) * Math.sin(theta)
            );

            ParticleUtil.spawnParticleEffect(
                BURST_PARTICLE_SYSTEM,
                particlePos.x, particlePos.y, particlePos.z,
                0f, 0f, 0f,
                scale,
                color,
                null,
                playerRefs,
                accessor
            );
        }
    }

    @Nonnull
    private List<Ref<EntityStore>> collectPlayersNearOrigin(@Nonnull Store<EntityStore> store) {
        SpatialResource<Ref<EntityStore>, EntityStore> playerSpatialResource =
            store.getResource(EntityModule.get().getPlayerSpatialResourceType());
        List<Ref<EntityStore>> results = SpatialResource.getThreadLocalReferenceList();

        playerSpatialResource.getSpatialStructure().collect(
            new Vector3d(0, GalaxySpiralLayoutMapper.BASE_HEIGHT, 0),
            PLAYER_COLLECT_RANGE,
            results
        );

        return results;
    }

    // ═══════════════════════════════════════════════════════════════════
    // UTILITY
    // ═══════════════════════════════════════════════════════════════════

    private static double distanceSq(double ax, double ay, double az, double bx, double by, double bz) {
        double dx = ax - bx;
        double dy = ay - by;
        double dz = az - bz;
        return dx * dx + dy * dy + dz * dz;
    }

    // ═══════════════════════════════════════════════════════════════════
    // INNER CLASSES
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Pre-computed visual data for a single connection between two nodes.
     *
     * @param connectionKey Sorted connection key (e.g. "a:b")
     * @param nodeIdA       First node ID (lexicographically smaller)
     * @param nodeIdB       Second node ID (lexicographically larger)
     * @param start         Start point (offset from source node center by visual radius)
     * @param end           End point (offset from target node center by visual radius)
     * @param packedColor   Packed 0xRRGGBB color for the laser beam
     * @param isBridge      Whether this is a cross-arm bridge connection
     * @param midX          Precomputed midpoint X for distance sorting
     * @param midY          Precomputed midpoint Y for distance sorting
     * @param midZ          Precomputed midpoint Z for distance sorting
     */
    private record ConnectionVisual(
        String connectionKey,
        String nodeIdA,
        String nodeIdB,
        Vector3d start,
        Vector3d end,
        int packedColor,
        boolean isBridge,
        double midX,
        double midY,
        double midZ
    ) {}
}
