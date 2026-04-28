package io.github.larsonix.trailoforbis.skilltree.api;

import com.hypixel.hytale.logger.HytaleLogger;
import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.skilltree.config.SkillNode;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;

/**
 * Thread-safe dispatcher for skill tree events.
 *
 * <p>Uses {@link CopyOnWriteArrayList} for thread-safe iteration during event dispatch.
 * This is optimized for frequent reads (event dispatch) and infrequent writes (listener registration).
 *
 * <p>All events are dispatched synchronously on the calling thread.
 */
public class SkillTreeEventDispatcher {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final List<SkillTreeEvents.NodeAllocatedListener> nodeAllocatedListeners = new CopyOnWriteArrayList<>();
    private final List<SkillTreeEvents.NodeDeallocatedListener> nodeDeallocatedListeners = new CopyOnWriteArrayList<>();
    private final List<SkillTreeEvents.RespecListener> respecListeners = new CopyOnWriteArrayList<>();

    // ═══════════════════════════════════════════════════════════════════
    // REGISTRATION
    // ═══════════════════════════════════════════════════════════════════

    public void registerNodeAllocatedListener(@Nonnull SkillTreeEvents.NodeAllocatedListener listener) {
        if (listener != null && !nodeAllocatedListeners.contains(listener)) {
            nodeAllocatedListeners.add(listener);
        }
    }

    public void registerNodeDeallocatedListener(@Nonnull SkillTreeEvents.NodeDeallocatedListener listener) {
        if (listener != null && !nodeDeallocatedListeners.contains(listener)) {
            nodeDeallocatedListeners.add(listener);
        }
    }

    public void registerRespecListener(@Nonnull SkillTreeEvents.RespecListener listener) {
        if (listener != null && !respecListeners.contains(listener)) {
            respecListeners.add(listener);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // UNREGISTRATION
    // ═══════════════════════════════════════════════════════════════════

    public void unregisterNodeAllocatedListener(@Nonnull SkillTreeEvents.NodeAllocatedListener listener) {
        if (listener != null) {
            nodeAllocatedListeners.remove(listener);
        }
    }

    public void unregisterNodeDeallocatedListener(@Nonnull SkillTreeEvents.NodeDeallocatedListener listener) {
        if (listener != null) {
            nodeDeallocatedListeners.remove(listener);
        }
    }

    public void unregisterRespecListener(@Nonnull SkillTreeEvents.RespecListener listener) {
        if (listener != null) {
            respecListeners.remove(listener);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // EVENT DISPATCH
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Dispatches a node allocated event to all registered listeners.
     *
     * @param playerId The player's UUID
     * @param node The allocated node
     * @param pointsRemaining The player's remaining skill points
     * @param statsBefore ComputedStats snapshot before allocation
     * @param statsAfter ComputedStats snapshot after allocation
     */
    public void dispatchNodeAllocated(@Nonnull UUID playerId, @Nonnull SkillNode node,
                                       int pointsRemaining,
                                       @Nullable ComputedStats statsBefore,
                                       @Nullable ComputedStats statsAfter) {
        for (SkillTreeEvents.NodeAllocatedListener listener : nodeAllocatedListeners) {
            try {
                listener.onNodeAllocated(playerId, node, pointsRemaining, statsBefore, statsAfter);
            } catch (Exception e) {
                LOGGER.at(Level.SEVERE).withCause(e).log("Error in NodeAllocatedListener");
            }
        }
    }

    /**
     * Dispatches a node deallocated event to all registered listeners.
     *
     * @param playerId The player's UUID
     * @param node The deallocated node
     * @param pointsRefunded The number of skill points refunded
     * @param pointsRemaining The player's remaining skill points
     * @param refundPointsRemaining The player's remaining refund points
     * @param statsBefore ComputedStats snapshot before deallocation
     * @param statsAfter ComputedStats snapshot after deallocation
     */
    public void dispatchNodeDeallocated(@Nonnull UUID playerId, @Nonnull SkillNode node,
                                         int pointsRefunded, int pointsRemaining,
                                         int refundPointsRemaining,
                                         @Nullable ComputedStats statsBefore,
                                         @Nullable ComputedStats statsAfter) {
        for (SkillTreeEvents.NodeDeallocatedListener listener : nodeDeallocatedListeners) {
            try {
                listener.onNodeDeallocated(playerId, node, pointsRefunded, pointsRemaining,
                        refundPointsRemaining, statsBefore, statsAfter);
            } catch (Exception e) {
                LOGGER.at(Level.SEVERE).withCause(e).log("Error in NodeDeallocatedListener");
            }
        }
    }

    /**
     * Dispatches a respec event to all registered listeners.
     *
     * @param playerId The player's UUID
     * @param nodesCleared The set of node IDs that were deallocated
     * @param pointsRefunded The total number of points refunded
     * @param freeRespecsRemaining The number of free respecs remaining
     */
    public void dispatchRespec(@Nonnull UUID playerId, @Nonnull Set<String> nodesCleared,
                               int pointsRefunded, int freeRespecsRemaining) {
        for (SkillTreeEvents.RespecListener listener : respecListeners) {
            try {
                listener.onRespec(playerId, nodesCleared, pointsRefunded, freeRespecsRemaining);
            } catch (Exception e) {
                LOGGER.at(Level.SEVERE).withCause(e).log("Error in RespecListener");
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // UTILITIES
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Clears all registered listeners.
     *
     * <p>Called during plugin shutdown.
     */
    public void clearAll() {
        nodeAllocatedListeners.clear();
        nodeDeallocatedListeners.clear();
        respecListeners.clear();
    }

    /**
     * Returns true if any listeners are registered.
     *
     * @return true if at least one listener is registered
     */
    public boolean hasListeners() {
        return !nodeAllocatedListeners.isEmpty()
            || !nodeDeallocatedListeners.isEmpty()
            || !respecListeners.isEmpty();
    }
}
