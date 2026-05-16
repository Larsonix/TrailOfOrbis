package io.github.larsonix.trailoforbis.compat;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single source of truth for hex cast metadata, indexed by executionId (v0.7.0+).
 *
 * <p>Centralizes state that was previously scattered across 4 maps and 3 ThreadLocals
 * in the old HexCastEventInterceptor (now split into handler + store). Two access layers:
 * <ul>
 *   <li><b>ThreadLocal fast path</b> — microsecond-fresh, for direct glyph damage
 *       that fires synchronously within the same invoke() chain as the cast</li>
 *   <li><b>Persistent map</b> — executionId-keyed, for construct/projectile damage
 *       on later ticks, with deterministic attribution even when entity refs go stale</li>
 * </ul>
 *
 * <p>Thread safety: ThreadLocal is thread-confined. ConcurrentHashMap for all
 * cross-thread state. No synchronization needed.
 */
public final class HexCastStateStore {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Maximum age (nanoseconds) for a ThreadLocal cast to be considered "fresh". */
    static final long FRESH_THRESHOLD_NANOS = 5_000_000L; // 5ms

    /** Maximum age for execution records (matches construct max age). */
    private static final long EXECUTION_TTL_MS = 300_000L; // 5 minutes

    /** Stricter TTL for "most recent caster" fallback. */
    private static final long RECENT_CASTER_TTL_MS = 30_000L; // 30 seconds

    /**
     * Immutable snapshot of a single cast's metadata.
     * Created once during HexCastEvent, consumed by the damage pipeline.
     *
     * @param volatilityMax Player's max Volatility stat (from EntityStatMap via StatMapBridge).
     *                      CastGate sets each cast's startingBudget = volMax - cumulativeDecay.
     *                      The ratio startingBudget/volatilityMax gives the across-cast damage scaling.
     */
    public record CastRecord(
            @Nonnull UUID casterUuid,
            @Nonnull Ref<EntityStore> casterRef,
            float basePower,
            @Nullable com.riprod.hexcode.core.state.execution.component.VolatilityTracker volatilityTracker,
            float volatilityMax,
            long timestampMs,
            long timestampNanos
    ) {}

    // ── Active Tracker (set by CostScaledGlyphWrapper during glyph execution) ──

    /**
     * Live snapshot of the VolatilityTracker during glyph execution.
     * Set BEFORE delegate.execute() by the glyph wrapper, so RPGDamageSystem
     * can read live remainingBudget for within-cast damage scaling.
     *
     * @param tracker Live VolatilityTracker reference (mutable, reads reflect current state)
     * @param volatilityMax Player's max Volatility stat for across-cast ratio computation
     */
    public record ActiveTrackerSnapshot(
            @Nonnull com.riprod.hexcode.core.state.execution.component.VolatilityTracker tracker,
            float volatilityMax
    ) {}

    private static final ThreadLocal<ActiveTrackerSnapshot> ACTIVE_TRACKER = new ThreadLocal<>();

    /**
     * Sets the active VolatilityTracker for the current glyph execution.
     * Called by {@link io.github.larsonix.trailoforbis.compat.glyph.CostScaledGlyphWrapper}
     * immediately before delegating to the original glyph's execute().
     */
    public static void setActiveTracker(
            @Nonnull com.riprod.hexcode.core.state.execution.component.VolatilityTracker tracker,
            float volatilityMax) {
        ACTIVE_TRACKER.set(new ActiveTrackerSnapshot(tracker, volatilityMax));
    }

    /** Clears the active tracker. Called in a finally block after glyph execution. */
    public static void clearActiveTracker() {
        ACTIVE_TRACKER.remove();
    }

    /**
     * Returns the within-cast volatility ratio: remainingBudget / startingBudget.
     *
     * <p>Reads the LIVE VolatilityTracker set by the glyph wrapper during execution.
     * After {@code consumeVolatility()} drains cost for the current glyph,
     * {@code remainingBudget} reflects the state AFTER this glyph's cost but BEFORE
     * subsequent glyphs execute.
     *
     * <p>Returns 1.0 if no active tracker is set (e.g., construct damage on later ticks).
     */
    public static float getWithinCastVolatilityRatio() {
        ActiveTrackerSnapshot snapshot = ACTIVE_TRACKER.get();
        if (snapshot == null) return 1.0f;
        float starting = snapshot.tracker.getStartingBudget();
        if (starting <= 0) return 1.0f;
        float remaining = snapshot.tracker.getRemainingBudget();
        return Math.max(0f, Math.min(1f, remaining / starting));
    }

    // ── ThreadLocal Fast Path (Tier 1) ──

    private static final ThreadLocal<CastRecord> CURRENT_CAST = new ThreadLocal<>();

    /**
     * Stores a CastRecord on the current thread. Called from HexCastEventHandler
     * during the synchronous HexCastEvent handling.
     */
    public static void setCurrentCast(@Nonnull CastRecord record) {
        CURRENT_CAST.set(record);
    }

    /**
     * Returns the CastRecord if set on this thread within the last 5ms.
     * Returns null if stale or absent. This is Tier 1 attribution — valid only
     * for direct glyph damage in the same synchronous invoke() chain.
     */
    @Nullable
    public static CastRecord getFreshCast() {
        CastRecord record = CURRENT_CAST.get();
        if (record == null) return null;
        if (System.nanoTime() - record.timestampNanos > FRESH_THRESHOLD_NANOS) {
            return null;
        }
        Ref<EntityStore> ref = record.casterRef;
        return (ref.isValid()) ? record : null;
    }

    /**
     * Clears the ThreadLocal after successful Tier 1 consumption.
     * Prevents the same snapshot from being reused for a later damage event.
     */
    public static void consumeCurrentCast() {
        CURRENT_CAST.remove();
    }

    /**
     * Returns the across-cast volatility ratio: startingBudget / volatilityMax.
     *
     * <p>Hexcode's CastGate sets each cast's budget as {@code volMax - cumulativeDecay},
     * where decay accumulates via {@code castDecayRate} (0.05 for RPG staffs). As the
     * player casts repeatedly, startingBudget shrinks and damage scales down.
     *
     * <p>Tries two sources in order:
     * <ol>
     *   <li>{@code ACTIVE_TRACKER} — set by the glyph wrapper during synchronous glyph
     *       execution. Available for direct glyph damage (Bolt, Gust, etc.).</li>
     *   <li>{@code CURRENT_CAST} — set by HexCastEventHandler after execution completes.
     *       Available for construct damage on later ticks.</li>
     * </ol>
     *
     * <p>Redrawing the spell resets cumulativeDecay → budget=volMax → ratio=1.0.
     */
    public static float getVolatilityRatio() {
        // Primary: active tracker (set during glyph execution, before CURRENT_CAST exists)
        ActiveTrackerSnapshot snapshot = ACTIVE_TRACKER.get();
        if (snapshot != null) {
            float volMax = snapshot.volatilityMax;
            if (volMax <= 0) return 1.0f;
            float startingBudget = snapshot.tracker.getStartingBudget();
            return Math.max(0f, Math.min(1f, startingBudget / volMax));
        }
        // Fallback: CastRecord (set after execution, for construct damage on later ticks)
        CastRecord record = CURRENT_CAST.get();
        if (record == null || record.volatilityTracker == null) return 1.0f;
        float volMax = record.volatilityMax;
        if (volMax <= 0) return 1.0f;
        float startingBudget = record.volatilityTracker.getStartingBudget();
        return Math.max(0f, Math.min(1f, startingBudget / volMax));
    }

    /**
     * Returns the base hex power from the current thread's cast.
     * Used for direct glyph damage normalization.
     */
    public static float getCurrentBasePower() {
        CastRecord record = CURRENT_CAST.get();
        return record != null ? record.basePower : 1.0f;
    }

    // ── Persistent Path (executionId-indexed) ──

    /** Primary store: executionId → full cast metadata. */
    private static final ConcurrentHashMap<UUID, CastRecord> casts = new ConcurrentHashMap<>();

    /** Secondary index: playerUuid → most recent executionId. */
    private static final ConcurrentHashMap<UUID, UUID> recentExecution = new ConcurrentHashMap<>();

    /**
     * Stores a CastRecord by executionId. Also updates the player's "most recent"
     * secondary index. Called from HexCastEventHandler after resolving caster UUID.
     */
    public static void putCast(@Nonnull UUID executionId, @Nonnull CastRecord record) {
        casts.put(executionId, record);
        recentExecution.put(record.casterUuid, executionId);
    }

    /**
     * Returns the caster UUID for a given executionId, or null if expired/unknown.
     * Deterministic attribution — no proximity needed.
     *
     * <p>Called by HexEntityRegistry when construct/projectile entity refs are stale
     * but their executionId was captured at spawn time.
     */
    @Nullable
    public static UUID getCasterForExecution(@Nullable UUID executionId) {
        if (executionId == null) return null;
        CastRecord record = casts.get(executionId);
        if (record == null) return null;
        if (System.currentTimeMillis() - record.timestampMs > EXECUTION_TTL_MS) {
            casts.remove(executionId);
            return null;
        }
        return record.casterUuid;
    }

    /**
     * Returns the full CastRecord for a given executionId, or null if expired.
     * Provides all metadata (basePower, volatility) beyond just the UUID.
     */
    @Nullable
    public static CastRecord getCast(@Nullable UUID executionId) {
        if (executionId == null) return null;
        CastRecord record = casts.get(executionId);
        if (record == null) return null;
        if (System.currentTimeMillis() - record.timestampMs > EXECUTION_TTL_MS) {
            casts.remove(executionId);
            return null;
        }
        return record;
    }

    /**
     * Returns the base hex power for a specific player from their most recent cast.
     * Uses the secondary index to find the record.
     * Used for construct damage where the ThreadLocal is stale.
     */
    public static float getBasePowerForPlayer(@Nonnull UUID playerUuid) {
        UUID execId = recentExecution.get(playerUuid);
        if (execId == null) return 1.0f;
        CastRecord record = casts.get(execId);
        if (record == null) return 1.0f;
        if (System.currentTimeMillis() - record.timestampMs > EXECUTION_TTL_MS) {
            casts.remove(execId);
            recentExecution.remove(playerUuid, execId);
            return 1.0f;
        }
        return record.basePower;
    }

    // ── Fallback Path (most recent caster) ──

    /**
     * Finds the most recent valid caster ref from the persistent store.
     * Returns the newest record with a valid ref in the given store (30s TTL).
     * Last-resort fallback for damage attribution.
     */
    @Nullable
    public static Ref<EntityStore> findRecentCaster(@Nonnull Store<EntityStore> store) {
        long now = System.currentTimeMillis();
        CastRecord best = null;

        var it = casts.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            CastRecord record = entry.getValue();

            if (now - record.timestampMs > RECENT_CASTER_TTL_MS) {
                continue; // Expired for recent-caster purposes (but may still be valid for executionId lookup)
            }

            Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(record.casterUuid);
            if (ref != null && ref.isValid() && ref.getStore() == store) {
                if (best == null || record.timestampMs > best.timestampMs) {
                    best = record;
                }
            }
        }

        return best != null ? best.casterRef : null;
    }

    // ── Lifecycle ──

    /**
     * Removes all state for a disconnecting player.
     */
    public static void onPlayerDisconnect(@Nonnull UUID playerId) {
        UUID execId = recentExecution.remove(playerId);
        // Remove all execution records for this player
        casts.entrySet().removeIf(e -> playerId.equals(e.getValue().casterUuid));
        LOGGER.atFine().log("[HexStateStore] Cleaned up state for player %s", playerId.toString().substring(0, 8));
    }

    /**
     * Clears all state. Called during plugin shutdown.
     */
    public static void clear() {
        ACTIVE_TRACKER.remove();
        CURRENT_CAST.remove();
        casts.clear();
        recentExecution.clear();
    }

    /** Returns the total number of stored execution records (for diagnostics). */
    public static int getCastCount() {
        return casts.size();
    }

    private HexCastStateStore() {}
}
