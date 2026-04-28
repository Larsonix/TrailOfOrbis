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
 * <p>HexCastEvent is dispatched as a WorldEventSystem event (world-level, not entity-level).
 * Hexcode's own {@code HexCastDiagnosticListener} uses this exact pattern:
 * {@code extends WorldEventSystem<EntityStore, HexCastEvent>}.
 *
 * <p>We use raw types to avoid compile-time dependency on HexCastEvent.
 * The event class is loaded via reflection and passed to the constructor.
 * The caster ref is extracted via reflection on {@code event.getWielderRef()}.
 *
 * <p>The caster ref is stored in a ThreadLocal (valid for instant spells on the
 * single-threaded world tick) and a persistent UUID map (for construct damage fallback).
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class HexCastEventInterceptor extends WorldEventSystem {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** ThreadLocal storing the caster ref from the most recent HexCastEvent. */
    private static final ThreadLocal<Ref<EntityStore>> CURRENT_CASTER = new ThreadLocal<>();

    /** Volatile caster ref visible across ALL threads (handles cross-thread ECS execution). */
    private static volatile Ref<EntityStore> lastGlobalCaster;
    private static volatile long lastGlobalCasterTimestamp;

    /** Persistent caster map for construct damage fallback (UUID → record). */
    private static final ConcurrentHashMap<UUID, CasterRecord> RECENT_CASTERS = new ConcurrentHashMap<>();

    /** How long a caster record stays valid (30 seconds covers most constructs). */
    private static final long CASTER_RECORD_TTL_MS = 30_000L;

    /** Cached reflection method: HexCastEvent.getWielderRef() */
    private static volatile Method getWielderRefMethod;

    /**
     * Creates the interceptor for HexCastEvent.
     *
     * @param eventClass The HexCastEvent class loaded via reflection
     */
    public HexCastEventInterceptor(@Nonnull Class eventClass) {
        super(eventClass);

        // Cache the getWielderRef method
        try {
            getWielderRefMethod = eventClass.getMethod("getWielderRef");
        } catch (NoSuchMethodException e) {
            LOGGER.atWarning().log("[HexCast] HexCastEvent.getWielderRef() not found");
        }
    }

    @Override
    public void handle(@Nonnull Store store, @Nonnull CommandBuffer buffer, @Nonnull EcsEvent event) {
        LOGGER.atInfo().log("[HexCastInterceptor] handle() invoked, event=%s", event.getClass().getSimpleName());
        if (getWielderRefMethod == null) {
            LOGGER.atInfo().log("[HexCastInterceptor] getWielderRefMethod is NULL — aborting");
            return;
        }

        try {
            // Extract caster ref from the event via reflection
            Object wielderRefObj = getWielderRefMethod.invoke(event);
            if (!(wielderRefObj instanceof Ref<?> ref) || !ref.isValid()) {
                return;
            }

            Ref<EntityStore> casterRef = (Ref<EntityStore>) ref;

            // Store in ThreadLocal for instant spell damage (same tick, same thread)
            CURRENT_CASTER.set(casterRef);

            // Store in volatile field for cross-thread visibility (handles ECS thread boundaries)
            lastGlobalCaster = casterRef;
            lastGlobalCasterTimestamp = System.currentTimeMillis();

            // Extract player UUID for persistent tracking (construct damage fallback)
            try {
                Object playerRefObj = store.getComponent(casterRef,
                        com.hypixel.hytale.server.core.universe.PlayerRef.getComponentType());
                if (playerRefObj instanceof com.hypixel.hytale.server.core.universe.PlayerRef playerRef) {
                    RECENT_CASTERS.put(playerRef.getUuid(),
                            new CasterRecord(casterRef, System.currentTimeMillis()));
                    LOGGER.atInfo().log("[HexCast] Captured caster UUID=%s from HexCastEvent", playerRef.getUuid());
                } else {
                    LOGGER.atInfo().log("[HexCast] Captured caster ref (no UUID) from HexCastEvent");
                }
            } catch (Exception e) {
                LOGGER.atInfo().log("[HexCast] UUID extraction failed: %s — volatile fallback active", e.getMessage());
            }

        } catch (Exception e) {
            LOGGER.atInfo().log("[HexCastInterceptor] EXCEPTION in handle(): %s", e.getMessage());
        }
    }

    /**
     * Returns the caster ref from the most recent HexCastEvent on this thread.
     */
    @Nullable
    public static Ref<EntityStore> getLastCaster() {
        return CURRENT_CASTER.get();
    }

    /**
     * Returns the most recent caster ref from any thread (volatile field).
     * Falls back across thread boundaries where ThreadLocal is invisible.
     */
    @Nullable
    public static Ref<EntityStore> getLastCasterGlobal() {
        if (System.currentTimeMillis() - lastGlobalCasterTimestamp > CASTER_RECORD_TTL_MS) {
            return null;
        }
        Ref<EntityStore> ref = lastGlobalCaster;
        return (ref != null && ref.isValid()) ? ref : null;
    }

    /**
     * Finds a recent caster from the persistent map (construct damage fallback).
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

    /** Clears the ThreadLocal. */
    public static void clearCurrentCaster() {
        CURRENT_CASTER.remove();
    }

    private record CasterRecord(Ref<EntityStore> ref, long timestamp) {}
}
