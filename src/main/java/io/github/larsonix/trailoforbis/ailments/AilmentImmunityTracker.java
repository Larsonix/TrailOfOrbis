package io.github.larsonix.trailoforbis.ailments;

import io.github.larsonix.trailoforbis.elemental.ElementType;

import javax.annotation.Nonnull;
import java.util.EnumMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks temporary element immunity windows for the IMMUNITY_ON_AILMENT octant keystone stat.
 *
 * <p>When a player with this stat is hit by an ailment, they gain temporary immunity
 * to that element's ailment for a short window (default 5 seconds). If already immune
 * when hit again, the ailment application is blocked.
 *
 * <p>Element mapping follows {@link AilmentType#forElement(ElementType)}:
 * Burn → Fire, Freeze → Water, Shock → Lightning, Poison → Void.
 *
 * <p>Thread-safe via ConcurrentHashMap.
 */
public class AilmentImmunityTracker {

    /** Default immunity window duration in milliseconds. */
    private static final long DEFAULT_IMMUNITY_MS = 5_000L;

    private final ConcurrentHashMap<UUID, EnumMap<ElementType, Long>> immunities = new ConcurrentHashMap<>();

    private final long immunityDurationMs;

    /**
     * Creates a tracker with the default 5-second immunity window.
     */
    public AilmentImmunityTracker() {
        this(DEFAULT_IMMUNITY_MS);
    }

    /**
     * Creates a tracker with a custom immunity window.
     *
     * @param immunityDurationMs Immunity duration in milliseconds
     */
    public AilmentImmunityTracker(long immunityDurationMs) {
        this.immunityDurationMs = immunityDurationMs;
    }

    /**
     * @return true if the entity is immune (within the immunity window)
     */
    public boolean isImmune(@Nonnull UUID entityUuid, @Nonnull ElementType elementType) {
        EnumMap<ElementType, Long> map = immunities.get(entityUuid);
        if (map == null) {
            return false;
        }

        Long grantedAt = map.get(elementType);
        if (grantedAt == null) {
            return false;
        }

        if (System.currentTimeMillis() - grantedAt > immunityDurationMs) {
            map.remove(elementType);
            return false;
        }

        return true;
    }

    /** Grants immunity to a specific element type for the configured duration. */
    public void grantImmunity(@Nonnull UUID entityUuid, @Nonnull ElementType elementType) {
        immunities.computeIfAbsent(entityUuid, k -> new EnumMap<>(ElementType.class))
                .put(elementType, System.currentTimeMillis());
    }

    /** Cleans up immunity state for an entity (on disconnect/death). */
    public void cleanup(@Nonnull UUID entityUuid) {
        immunities.remove(entityUuid);
    }

    /**
     * Gets the immunity duration in milliseconds.
     */
    public long getImmunityDurationMs() {
        return immunityDurationMs;
    }

    /**
     * Gets the number of tracked entities.
     */
    public int getTrackedCount() {
        return immunities.size();
    }
}
