package io.github.larsonix.trailoforbis.combat.tracking;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks consecutive hits per attacker for the CONSECUTIVE_HIT_BONUS octant keystone stat.
 *
 * <p>Each time a player lands a hit, their consecutive count increments.
 * If more than {@link #WINDOW_NANOS} passes without a hit, the count resets.
 * The multiplier scales linearly: {@code 1.0 + count × bonusPerHit / 100}.
 *
 * <p>Thread-safe via ConcurrentHashMap.compute().
 */
public class ConsecutiveHitTracker {

    /** Window in nanoseconds — hits within this window are "consecutive". */
    private static final long WINDOW_NANOS = 2_000_000_000L; // 2 seconds

    private record HitState(int count, long lastHitNanos) {}

    private final ConcurrentHashMap<UUID, HitState> states = new ConcurrentHashMap<>();

    /**
     * Records a hit for the given attacker, incrementing their consecutive count
     * if within the 2-second window, or resetting to 1 if expired.
     *
     * @param attackerUuid The attacker's UUID
     * @return The consecutive hit count after this hit (≥ 1)
     */
    public int recordHit(@Nonnull UUID attackerUuid) {
        long now = System.nanoTime();
        HitState result = states.compute(attackerUuid, (uuid, existing) -> {
            if (existing == null || (now - existing.lastHitNanos) > WINDOW_NANOS) {
                return new HitState(1, now);
            }
            return new HitState(existing.count + 1, now);
        });
        return result.count;
    }

    /**
     * Gets the current consecutive hit count for an attacker, or 0 if none/expired.
     *
     * @param attackerUuid The attacker's UUID
     * @return The current consecutive hit count (0 if none or expired)
     */
    public int getCount(@Nonnull UUID attackerUuid) {
        HitState state = states.get(attackerUuid);
        if (state == null) {
            return 0;
        }
        if ((System.nanoTime() - state.lastHitNanos) > WINDOW_NANOS) {
            states.remove(attackerUuid);
            return 0;
        }
        return state.count;
    }

    /**
     * Calculates the damage multiplier from consecutive hits.
     *
     * @param attackerUuid The attacker's UUID
     * @param bonusPerHit  The CONSECUTIVE_HIT_BONUS stat value (% per hit)
     * @return Multiplier ≥ 1.0 (e.g., count=3, bonus=5 → 1.15)
     */
    public float getMultiplier(@Nonnull UUID attackerUuid, float bonusPerHit) {
        int count = getCount(attackerUuid);
        if (count <= 0 || bonusPerHit <= 0) {
            return 1.0f;
        }
        return 1.0f + count * bonusPerHit / 100f;
    }

    public void cleanup(@Nonnull UUID uuid) {
        states.remove(uuid);
    }

    public int getTrackedCount() {
        return states.size();
    }
}
