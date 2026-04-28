package io.github.larsonix.trailoforbis.loot.container;

import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tracks which containers have been processed for loot replacement.
 *
 * <p>Uses position-based tracking to ensure each container is only processed once.
 * After processing, a container is remembered for a configurable duration before
 * it can be re-processed (useful for respawning containers).
 *
 * <h2>Key Design Decisions</h2>
 * <ul>
 *   <li><b>Position-based</b>: Uses world name + block coordinates as the key</li>
 *   <li><b>First opener wins</b>: The first player to open sets the loot level</li>
 *   <li><b>Time-limited memory</b>: Old entries are cleaned up periodically</li>
 *   <li><b>Thread-safe</b>: Uses ConcurrentHashMap for concurrent access</li>
 * </ul>
 *
 * <h2>Memory Management</h2>
 * <p>A background task periodically removes entries older than the configured
 * memory duration. This prevents unbounded memory growth while allowing
 * container respawn mechanics to work.
 *
 * @see ContainerKey
 * @see ProcessedState
 */
public final class ContainerTracker {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // Processed containers: key -> state
    private final ConcurrentHashMap<ContainerKey, ProcessedState> processedContainers;

    // Configuration
    private final long memoryDurationMs;
    private final long cleanupIntervalMs;

    // Background cleanup
    private final ScheduledExecutorService cleanupExecutor;
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    /**
     * Creates a new container tracker with the specified settings.
     *
     * @param memoryDurationMs How long to remember processed containers (ms)
     * @param cleanupIntervalMs How often to run cleanup (ms)
     */
    public ContainerTracker(long memoryDurationMs, long cleanupIntervalMs) {
        this.memoryDurationMs = memoryDurationMs;
        this.cleanupIntervalMs = cleanupIntervalMs;
        this.processedContainers = new ConcurrentHashMap<>();

        // Start background cleanup
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ContainerTracker-Cleanup");
            t.setDaemon(true);
            return t;
        });

        cleanupExecutor.scheduleAtFixedRate(
            this::cleanup,
            cleanupIntervalMs,
            cleanupIntervalMs,
            TimeUnit.MILLISECONDS
        );

        LOGGER.atInfo().log("ContainerTracker initialized: memory=%dms, cleanup=%dms",
            memoryDurationMs, cleanupIntervalMs);
    }

    /**
     * Creates a new container tracker with config settings.
     *
     * @param config The container loot configuration
     */
    public ContainerTracker(@Nonnull ContainerLootConfig config) {
        this(
            config.getAdvanced().getContainerMemoryDurationMs(),
            config.getAdvanced().getCleanupIntervalMs()
        );
    }

    /**
     * Checks if a container has been processed.
     *
     * @param worldName The world name
     * @param x Block X coordinate
     * @param y Block Y coordinate
     * @param z Block Z coordinate
     * @return true if already processed and still in memory
     */
    public boolean isProcessed(@Nonnull String worldName, int x, int y, int z) {
        ContainerKey key = new ContainerKey(worldName, x, y, z);
        ProcessedState state = processedContainers.get(key);

        if (state == null) {
            return false;
        }

        // Check if entry has expired
        if (isExpired(state)) {
            processedContainers.remove(key);
            return false;
        }

        return true;
    }

    /**
     * Marks a container as processed.
     *
     * @param worldName The world name
     * @param x Block X coordinate
     * @param y Block Y coordinate
     * @param z Block Z coordinate
     * @param firstOpenerId UUID of the first player to open the container
     * @return true if this was the first time marking (not already processed)
     */
    public boolean markProcessed(@Nonnull String worldName, int x, int y, int z, @Nonnull UUID firstOpenerId) {
        ContainerKey key = new ContainerKey(worldName, x, y, z);
        ProcessedState newState = new ProcessedState(System.currentTimeMillis(), firstOpenerId);

        // putIfAbsent returns null if the key wasn't present
        ProcessedState existing = processedContainers.putIfAbsent(key, newState);

        if (existing == null) {
            LOGGER.atFine().log("Marked container as processed: %s at (%d, %d, %d)",
                worldName, x, y, z);
            return true;
        }

        // Already processed - check if expired
        if (isExpired(existing)) {
            // Replace expired entry
            processedContainers.put(key, newState);
            LOGGER.atFine().log("Replaced expired container entry: %s at (%d, %d, %d)",
                worldName, x, y, z);
            return true;
        }

        return false;
    }

    /**
     * Gets the processed state for a container.
     *
     * @param worldName The world name
     * @param x Block X coordinate
     * @param y Block Y coordinate
     * @param z Block Z coordinate
     * @return The processed state, or null if not processed
     */
    public ProcessedState getProcessedState(@Nonnull String worldName, int x, int y, int z) {
        ContainerKey key = new ContainerKey(worldName, x, y, z);
        ProcessedState state = processedContainers.get(key);

        if (state != null && isExpired(state)) {
            processedContainers.remove(key);
            return null;
        }

        return state;
    }

    /**
     * Removes a container from tracking (e.g., when destroyed).
     *
     * @param worldName The world name
     * @param x Block X coordinate
     * @param y Block Y coordinate
     * @param z Block Z coordinate
     */
    public void remove(@Nonnull String worldName, int x, int y, int z) {
        ContainerKey key = new ContainerKey(worldName, x, y, z);
        processedContainers.remove(key);
    }

    /**
     * Checks if a processed state has expired.
     */
    private boolean isExpired(@Nonnull ProcessedState state) {
        return System.currentTimeMillis() - state.timestamp() > memoryDurationMs;
    }

    /**
     * Runs periodic cleanup of expired entries.
     */
    private void cleanup() {
        if (shutdown.get()) {
            return;
        }

        List<ContainerKey> expired = new ArrayList<>();
        for (var entry : processedContainers.entrySet()) {
            if (isExpired(entry.getValue())) {
                expired.add(entry.getKey());
            }
        }
        int removed = 0;
        for (var key : expired) {
            processedContainers.remove(key);
            removed++;
        }

        if (removed > 0) {
            LOGGER.atFine().log("Cleaned up %d expired container entries, %d remaining",
                removed, processedContainers.size());
        }
    }

    /**
     * Clears all tracked containers.
     */
    public void clear() {
        processedContainers.clear();
        LOGGER.atInfo().log("Cleared all container tracking data");
    }

    /**
     * Gets the number of tracked containers.
     *
     * @return Number of containers currently tracked
     */
    public int size() {
        return processedContainers.size();
    }

    /**
     * Shuts down the tracker and stops background cleanup.
     */
    public void shutdown() {
        if (shutdown.compareAndSet(false, true)) {
            cleanupExecutor.shutdown();
            try {
                if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    cleanupExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                cleanupExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            processedContainers.clear();
            LOGGER.atInfo().log("ContainerTracker shut down");
        }
    }

    // =========================================================================
    // INNER CLASSES
    // =========================================================================

    /**
     * Unique key for a container based on its world position.
     *
     * @param worldName The world name
     * @param x Block X coordinate
     * @param y Block Y coordinate
     * @param z Block Z coordinate
     */
    public record ContainerKey(
        @Nonnull String worldName,
        int x,
        int y,
        int z
    ) {
        public ContainerKey {
            Objects.requireNonNull(worldName, "worldName cannot be null");
        }
    }

    /**
     * State of a processed container.
     *
     * @param timestamp When the container was processed (epoch ms)
     * @param firstOpenerId UUID of the first player to open it
     */
    public record ProcessedState(
        long timestamp,
        @Nonnull UUID firstOpenerId
    ) {
        public ProcessedState {
            Objects.requireNonNull(firstOpenerId, "firstOpenerId cannot be null");
        }

        /**
         * Gets the age of this entry in milliseconds.
         *
         * @return Age in milliseconds
         */
        public long getAgeMs() {
            return System.currentTimeMillis() - timestamp;
        }
    }
}
