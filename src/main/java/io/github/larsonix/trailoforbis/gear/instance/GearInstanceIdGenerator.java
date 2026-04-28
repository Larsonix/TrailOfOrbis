package io.github.larsonix.trailoforbis.gear.instance;

import javax.annotation.Nonnull;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe generator for unique gear instance IDs.
 *
 * <p>Uses timestamp + atomic counter to ensure uniqueness even across:
 * <ul>
 *   <li>Concurrent gear generation on multiple threads</li>
 *   <li>High-frequency generation (thousands per second)</li>
 *   <li>Server restarts (different timestamps)</li>
 * </ul>
 *
 * <p>The algorithm:
 * <ol>
 *   <li>Get current timestamp in milliseconds</li>
 *   <li>If same as last timestamp, increment counter</li>
 *   <li>If different, reset counter to 0</li>
 *   <li>Return GearInstanceId(timestamp, counter)</li>
 * </ol>
 *
 * <p>This approach guarantees uniqueness:
 * <ul>
 *   <li>Within same millisecond: counter differentiates</li>
 *   <li>Across milliseconds: timestamp differentiates</li>
 *   <li>Across server restarts: new timestamp epoch</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>All operations are thread-safe via synchronized access and AtomicLong.
 * The generate() method can be called concurrently from any thread.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * GearInstanceId id = GearInstanceIdGenerator.generate();
 * String itemId = id.toItemId(); // "rpg_gear_1706123456789_0"
 * }</pre>
 */
public final class GearInstanceIdGenerator {

    /**
     * Atomic counter for same-millisecond disambiguation.
     * Reset when timestamp changes.
     */
    private static final AtomicLong COUNTER = new AtomicLong(0);

    /**
     * Last timestamp used for generation.
     * Volatile for visibility across threads (actual sync in generate()).
     */
    private static volatile long lastTimestamp = 0;

    /**
     * Lock object for synchronized generation.
     */
    private static final Object LOCK = new Object();

    /**
     * Generates a new unique GearInstanceId.
     *
     * <p>Thread-safe: can be called concurrently from any thread.
     *
     * @return A new unique GearInstanceId
     */
    @Nonnull
    public static GearInstanceId generate() {
        synchronized (LOCK) {
            long timestamp = System.currentTimeMillis();
            long counter;

            if (timestamp == lastTimestamp) {
                // Same millisecond - increment counter
                counter = COUNTER.incrementAndGet();
            } else {
                // New millisecond - reset counter
                lastTimestamp = timestamp;
                COUNTER.set(0);
                counter = 0;
            }

            return new GearInstanceId(timestamp, counter);
        }
    }

    /**
     * Generates multiple unique GearInstanceIds efficiently.
     *
     * <p>More efficient than calling generate() multiple times
     * as it holds the lock for the entire batch.
     *
     * @param count Number of IDs to generate
     * @return Array of unique GearInstanceIds
     * @throws IllegalArgumentException if count is negative
     */
    @Nonnull
    public static GearInstanceId[] generateBatch(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("Count cannot be negative: " + count);
        }
        if (count == 0) {
            return new GearInstanceId[0];
        }

        GearInstanceId[] ids = new GearInstanceId[count];

        synchronized (LOCK) {
            for (int i = 0; i < count; i++) {
                long timestamp = System.currentTimeMillis();
                long counter;

                if (timestamp == lastTimestamp) {
                    counter = COUNTER.incrementAndGet();
                } else {
                    lastTimestamp = timestamp;
                    COUNTER.set(0);
                    counter = 0;
                }

                ids[i] = new GearInstanceId(timestamp, counter);
            }
        }

        return ids;
    }

    /**
     * Returns the current counter value (for debugging/monitoring).
     *
     * <p>Note: This is a snapshot and may be stale immediately after reading.
     *
     * @return Current counter value
     */
    public static long getCurrentCounter() {
        return COUNTER.get();
    }

    /**
     * Returns the last timestamp used (for debugging/monitoring).
     *
     * <p>Note: This is a snapshot and may be stale immediately after reading.
     *
     * @return Last timestamp in milliseconds
     */
    public static long getLastTimestamp() {
        return lastTimestamp;
    }

    // Prevent instantiation
    private GearInstanceIdGenerator() {
        throw new UnsupportedOperationException("Utility class");
    }
}
