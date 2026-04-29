package io.github.larsonix.trailoforbis.compat;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.EcsEvent;
import com.hypixel.hytale.component.system.WorldEventSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Intercepts Hexcode's HexCastEvent to capture the caster's entity reference.
 *
 * <p>The caster ref is stored with a nanosecond timestamp in a ThreadLocal.
 * Since {@code commandBuffer.invoke()} is synchronous, direct glyph damage
 * fires within microseconds of the HexCastEvent on the same thread. The
 * timestamp lets {@link HexDamageAttributionSystem} distinguish fresh casts
 * (Tier 1 — direct damage) from stale ones (projectile/construct — Tier 2/3).
 *
 * <p>A persistent UUID map with 30-second TTL provides the last-resort fallback.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class HexCastEventInterceptor extends WorldEventSystem {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /**
     * Maximum age (nanoseconds) for a ThreadLocal caster to be considered "fresh".
     * Direct glyph damage fires within microseconds of HexCastEvent (synchronous invoke).
     * 5ms is generous — covers any scheduling jitter without accepting stale values.
     */
    static final long FRESH_THRESHOLD_NANOS = 5_000_000L;

    /** Timestamped ThreadLocal storing the caster ref from the most recent HexCastEvent. */
    private static final ThreadLocal<TimestampedCaster> CURRENT_CASTER = new ThreadLocal<>();

    /** Persistent caster map for last-resort fallback (UUID → record). */
    private static final ConcurrentHashMap<UUID, CasterRecord> RECENT_CASTERS = new ConcurrentHashMap<>();

    /** How long a caster record stays valid (30 seconds covers most constructs). */
    private static final long CASTER_RECORD_TTL_MS = 30_000L;

    /** Cached reflection method: HexCastEvent.getWielderRef() */
    private static volatile Method getWielderRefMethod;

    public HexCastEventInterceptor(@Nonnull Class eventClass) {
        super(eventClass);
        try {
            getWielderRefMethod = eventClass.getMethod("getWielderRef");
        } catch (NoSuchMethodException e) {
            LOGGER.atWarning().log("[HexCast] HexCastEvent.getWielderRef() not found");
        }
    }

    @Override
    public void handle(@Nonnull Store store, @Nonnull CommandBuffer buffer, @Nonnull EcsEvent event) {
        if (getWielderRefMethod == null) {
            return;
        }

        try {
            Object wielderRefObj = getWielderRefMethod.invoke(event);
            if (!(wielderRefObj instanceof Ref<?> ref) || !ref.isValid()) {
                return;
            }

            Ref<EntityStore> casterRef = (Ref<EntityStore>) ref;

            // Timestamped ThreadLocal — only valid for direct glyph damage (same tick)
            CURRENT_CASTER.set(new TimestampedCaster(casterRef, System.nanoTime()));

            // Persistent UUID map — fallback for construct damage (long TTL)
            try {
                Object playerRefObj = store.getComponent(casterRef,
                        com.hypixel.hytale.server.core.universe.PlayerRef.getComponentType());
                if (playerRefObj instanceof com.hypixel.hytale.server.core.universe.PlayerRef playerRef) {
                    RECENT_CASTERS.put(playerRef.getUuid(),
                            new CasterRecord(casterRef, System.currentTimeMillis()));
                    LOGGER.atFine().log("[HexCast] Captured caster %s",
                            playerRef.getUuid().toString().substring(0, 8));
                }
            } catch (Exception e) {
                LOGGER.atFine().log("[HexCast] UUID extraction failed: %s", e.getMessage());
            }

        } catch (Exception e) {
            LOGGER.atWarning().log("[HexCast] Exception in handle(): %s", e.getMessage());
        }
    }

    /**
     * Returns the caster ref if the HexCastEvent fired recently on this thread
     * (within {@link #FRESH_THRESHOLD_NANOS}). Returns null if stale or absent.
     *
     * <p>This is Tier 1 attribution — valid only for direct glyph damage
     * that fires synchronously within the same invoke() call as the cast.
     */
    @Nullable
    public static Ref<EntityStore> getFreshCaster() {
        TimestampedCaster tc = CURRENT_CASTER.get();
        if (tc == null) {
            return null;
        }
        if (System.nanoTime() - tc.nanos > FRESH_THRESHOLD_NANOS) {
            return null; // Stale — from a previous cast, not the current damage
        }
        Ref<EntityStore> ref = tc.ref;
        return (ref != null && ref.isValid()) ? ref : null;
    }

    /**
     * Clears the ThreadLocal after successful Tier 1 consumption.
     * Only called when the caster was identified and damage was rewritten.
     */
    public static void consumeCaster() {
        CURRENT_CASTER.remove();
    }

    /**
     * Finds a recent caster from the persistent map (last-resort fallback).
     */
    @Nullable
    public static Ref<EntityStore> findRecentCaster(@Nonnull Store<EntityStore> store) {
        long now = System.currentTimeMillis();
        CasterRecord best = null;

        var iterator = RECENT_CASTERS.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            CasterRecord record = entry.getValue();

            if (now - record.timestamp > CASTER_RECORD_TTL_MS) {
                iterator.remove();
                continue;
            }

            Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(entry.getKey());
            if (ref != null && ref.isValid()) {
                if (best == null || record.timestamp > best.timestamp) {
                    best = record;
                }
            }
        }

        return best != null ? best.ref : null;
    }

    /** @deprecated Use {@link #getFreshCaster()} instead — includes staleness check. */
    @Deprecated
    @Nullable
    public static Ref<EntityStore> getLastCaster() {
        return getFreshCaster();
    }

    /** @deprecated No longer needed — staleness is handled by timestamp. */
    @Deprecated
    public static void clearCurrentCaster() {
        CURRENT_CASTER.remove();
    }

    record TimestampedCaster(Ref<EntityStore> ref, long nanos) {}
    private record CasterRecord(Ref<EntityStore> ref, long timestamp) {}
}
