package io.github.larsonix.trailoforbis.skilltree.conditional;

import com.hypixel.hytale.logger.HytaleLogger;
import io.github.larsonix.trailoforbis.skilltree.config.SkillNode;
import io.github.larsonix.trailoforbis.skilltree.config.SkillTreeConfig;
import io.github.larsonix.trailoforbis.skilltree.model.SkillTreeData;
import io.github.larsonix.trailoforbis.skilltree.model.StatModifier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central system for managing conditional effect triggers across all players.
 *
 * <p>This system:
 * <ul>
 *   <li>Maintains per-player effect trackers</li>
 *   <li>Processes trigger events (kill, crit, hit, etc.)</li>
 *   <li>Checks threshold conditions (low life, full mana, etc.)</li>
 *   <li>Provides active conditional modifiers for stat calculation</li>
 * </ul>
 *
 * <p>Usage in combat:
 * <pre>
 * // On enemy kill
 * triggerSystem.onKill(playerId, skillTreeData);
 *
 * // On critical hit
 * triggerSystem.onCrit(playerId, skillTreeData);
 *
 * // Get active bonuses for stat calculation
 * List&lt;StatModifier&gt; bonuses = triggerSystem.getActiveModifiers(playerId);
 * </pre>
 *
 * @see ConditionalEffectTracker
 * @see ConditionalTrigger
 */
public class ConditionalTriggerSystem {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /**
     * Interval for cleanup task (5 seconds).
     */
    private static final long CLEANUP_INTERVAL_MS = 5000;

    private final SkillTreeConfig skillTreeConfig;

    /**
     * Per-player effect trackers.
     */
    private final Map<UUID, ConditionalEffectTracker> trackers = new ConcurrentHashMap<>();

    /**
     * Cache of conditional nodes per player (invalidated when allocation changes).
     */
    private final Map<UUID, List<ConditionalNodeInfo>> conditionalNodeCache = new ConcurrentHashMap<>();

    /**
     * Last cleanup time.
     */
    private long lastCleanupTime = 0;

    /**
     * Creates a new trigger system.
     *
     * @param skillTreeConfig The skill tree configuration
     */
    public ConditionalTriggerSystem(@Nonnull SkillTreeConfig skillTreeConfig) {
        this.skillTreeConfig = Objects.requireNonNull(skillTreeConfig, "skillTreeConfig cannot be null");
    }

    // ═══════════════════════════════════════════════════════════════════
    // TRIGGER EVENTS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Triggers ON_KILL effects for a player.
     *
     * @param playerId      The player's UUID
     * @param skillTreeData The player's skill tree data
     */
    public void onKill(@Nonnull UUID playerId, @Nonnull SkillTreeData skillTreeData) {
        triggerEffects(playerId, skillTreeData, ConditionalTrigger.ON_KILL);
    }

    /**
     * Triggers ON_CRIT effects for a player.
     *
     * @param playerId      The player's UUID
     * @param skillTreeData The player's skill tree data
     */
    public void onCrit(@Nonnull UUID playerId, @Nonnull SkillTreeData skillTreeData) {
        triggerEffects(playerId, skillTreeData, ConditionalTrigger.ON_CRIT);
    }

    /**
     * Triggers WHEN_HIT effects for a player.
     *
     * @param playerId      The player's UUID
     * @param skillTreeData The player's skill tree data
     */
    public void onHit(@Nonnull UUID playerId, @Nonnull SkillTreeData skillTreeData) {
        triggerEffects(playerId, skillTreeData, ConditionalTrigger.WHEN_HIT);
    }

    /**
     * Triggers ON_SKILL_USE effects for a player.
     *
     * @param playerId      The player's UUID
     * @param skillTreeData The player's skill tree data
     */
    public void onSkillUse(@Nonnull UUID playerId, @Nonnull SkillTreeData skillTreeData) {
        triggerEffects(playerId, skillTreeData, ConditionalTrigger.ON_SKILL_USE);
    }

    /**
     * Triggers ON_BLOCK effects for a player.
     *
     * @param playerId      The player's UUID
     * @param skillTreeData The player's skill tree data
     */
    public void onBlock(@Nonnull UUID playerId, @Nonnull SkillTreeData skillTreeData) {
        triggerEffects(playerId, skillTreeData, ConditionalTrigger.ON_BLOCK);
    }

    /**
     * Triggers ON_EVADE effects for a player.
     *
     * @param playerId      The player's UUID
     * @param skillTreeData The player's skill tree data
     */
    public void onEvade(@Nonnull UUID playerId, @Nonnull SkillTreeData skillTreeData) {
        triggerEffects(playerId, skillTreeData, ConditionalTrigger.ON_EVADE);
    }

    /**
     * Triggers ON_INFLICT_STATUS effects for a player.
     *
     * @param playerId      The player's UUID
     * @param skillTreeData The player's skill tree data
     */
    public void onInflictStatus(@Nonnull UUID playerId, @Nonnull SkillTreeData skillTreeData) {
        triggerEffects(playerId, skillTreeData, ConditionalTrigger.ON_INFLICT_STATUS);
    }

    /**
     * Triggers effects for a specific trigger type.
     */
    private void triggerEffects(
        @Nonnull UUID playerId,
        @Nonnull SkillTreeData skillTreeData,
        @Nonnull ConditionalTrigger triggerType
    ) {
        long currentTime = System.currentTimeMillis();

        // Run periodic cleanup
        maybeCleanup(currentTime);

        // Get or create tracker
        ConditionalEffectTracker tracker = getOrCreateTracker(playerId);

        // Get conditional nodes for this player
        List<ConditionalNodeInfo> nodes = getConditionalNodes(playerId, skillTreeData);

        // Activate matching effects
        for (ConditionalNodeInfo nodeInfo : nodes) {
            if (nodeInfo.config.getTrigger() == triggerType) {
                tracker.activateEffect(nodeInfo.nodeId, nodeInfo.config, currentTime);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // THRESHOLD CONDITIONS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Updates threshold-based effects based on current health/mana percentages.
     *
     * @param playerId       The player's UUID
     * @param skillTreeData  The player's skill tree data
     * @param healthPercent  Current health as percentage (0.0 to 1.0)
     * @param manaPercent    Current mana as percentage (0.0 to 1.0)
     */
    public void updateThresholds(
        @Nonnull UUID playerId,
        @Nonnull SkillTreeData skillTreeData,
        double healthPercent,
        double manaPercent
    ) {
        long currentTime = System.currentTimeMillis();
        ConditionalEffectTracker tracker = getOrCreateTracker(playerId);
        List<ConditionalNodeInfo> nodes = getConditionalNodes(playerId, skillTreeData);

        for (ConditionalNodeInfo nodeInfo : nodes) {
            ConditionalTrigger trigger = nodeInfo.config.getTrigger();
            boolean shouldBeActive = false;

            switch (trigger) {
                case LOW_LIFE -> shouldBeActive = healthPercent <= nodeInfo.config.getThreshold();
                case FULL_LIFE -> shouldBeActive = healthPercent >= 1.0;
                case LOW_MANA -> shouldBeActive = manaPercent <= nodeInfo.config.getThreshold();
                case FULL_MANA -> shouldBeActive = manaPercent >= 1.0;
                default -> {
                    // Non-threshold trigger, skip
                    continue;
                }
            }

            if (shouldBeActive) {
                tracker.activateEffect(nodeInfo.nodeId, nodeInfo.config, currentTime);
            } else {
                tracker.deactivatePersistentEffect(nodeInfo.nodeId);
            }
        }
    }

    /**
     * Updates movement-based effects.
     *
     * @param playerId      The player's UUID
     * @param skillTreeData The player's skill tree data
     * @param isMoving      Whether the player is currently moving
     */
    public void updateMovement(
        @Nonnull UUID playerId,
        @Nonnull SkillTreeData skillTreeData,
        boolean isMoving
    ) {
        long currentTime = System.currentTimeMillis();
        ConditionalEffectTracker tracker = getOrCreateTracker(playerId);
        List<ConditionalNodeInfo> nodes = getConditionalNodes(playerId, skillTreeData);

        for (ConditionalNodeInfo nodeInfo : nodes) {
            ConditionalTrigger trigger = nodeInfo.config.getTrigger();

            if (trigger == ConditionalTrigger.WHILE_MOVING) {
                if (isMoving) {
                    tracker.activateEffect(nodeInfo.nodeId, nodeInfo.config, currentTime);
                } else {
                    tracker.deactivatePersistentEffect(nodeInfo.nodeId);
                }
            } else if (trigger == ConditionalTrigger.WHILE_STATIONARY) {
                if (!isMoving) {
                    tracker.activateEffect(nodeInfo.nodeId, nodeInfo.config, currentTime);
                } else {
                    tracker.deactivatePersistentEffect(nodeInfo.nodeId);
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // CONSUME EFFECTS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Consumes CONSUME_ON_HIT effects when player attacks.
     *
     * @param playerId The player's UUID
     * @return List of consumed effect configs
     */
    @Nonnull
    public List<ConditionalConfig> consumeOnHit(@Nonnull UUID playerId) {
        ConditionalEffectTracker tracker = trackers.get(playerId);
        if (tracker == null) {
            return Collections.emptyList();
        }
        return tracker.consumeOnHit(System.currentTimeMillis());
    }

    /**
     * Consumes CONSUME_ON_SKILL effects when player uses a skill.
     *
     * @param playerId The player's UUID
     * @return List of consumed effect configs
     */
    @Nonnull
    public List<ConditionalConfig> consumeOnSkill(@Nonnull UUID playerId) {
        ConditionalEffectTracker tracker = trackers.get(playerId);
        if (tracker == null) {
            return Collections.emptyList();
        }
        return tracker.consumeOnSkill(System.currentTimeMillis());
    }

    // ═══════════════════════════════════════════════════════════════════
    // QUERYING
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets all active conditional modifiers for a player.
     *
     * @param playerId The player's UUID
     * @return List of active stat modifiers
     */
    @Nonnull
    public List<StatModifier> getActiveModifiers(@Nonnull UUID playerId) {
        ConditionalEffectTracker tracker = trackers.get(playerId);
        if (tracker == null) {
            return Collections.emptyList();
        }
        return tracker.getActiveModifiers(System.currentTimeMillis());
    }

    /**
     * Gets the tracker for a player.
     *
     * @param playerId The player's UUID
     * @return The tracker, or null if not exists
     */
    @Nullable
    public ConditionalEffectTracker getTracker(@Nonnull UUID playerId) {
        return trackers.get(playerId);
    }

    /**
     * Gets the number of active effects for a player.
     */
    public int getActiveEffectCount(@Nonnull UUID playerId) {
        ConditionalEffectTracker tracker = trackers.get(playerId);
        if (tracker == null) {
            return 0;
        }
        return tracker.getActiveEffectCount(System.currentTimeMillis());
    }

    // ═══════════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Called when a player allocates or deallocates nodes.
     * Invalidates the conditional node cache for the player.
     *
     * @param playerId The player's UUID
     */
    public void onAllocationChanged(@Nonnull UUID playerId) {
        conditionalNodeCache.remove(playerId);
        LOGGER.atFine().log("Invalidated conditional node cache for player %s", playerId);
    }

    /**
     * Removes a player from the system (e.g., on disconnect).
     *
     * @param playerId The player's UUID
     */
    public void removePlayer(@Nonnull UUID playerId) {
        trackers.remove(playerId);
        conditionalNodeCache.remove(playerId);
        LOGGER.atFine().log("Removed player %s from conditional trigger system", playerId);
    }

    /**
     * Clears all active effects for a player (e.g., on death).
     *
     * @param playerId The player's UUID
     */
    public void clearPlayerEffects(@Nonnull UUID playerId) {
        ConditionalEffectTracker tracker = trackers.get(playerId);
        if (tracker != null) {
            tracker.clearAll();
        }
    }

    /**
     * Shuts down the system, clearing all data.
     */
    public void shutdown() {
        trackers.clear();
        conditionalNodeCache.clear();
        LOGGER.atInfo().log("Conditional trigger system shut down");
    }

    // ═══════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets or creates a tracker for a player.
     */
    @Nonnull
    private ConditionalEffectTracker getOrCreateTracker(@Nonnull UUID playerId) {
        return trackers.computeIfAbsent(playerId, ConditionalEffectTracker::new);
    }

    /**
     * Gets conditional nodes for a player (with caching).
     */
    @Nonnull
    private List<ConditionalNodeInfo> getConditionalNodes(
        @Nonnull UUID playerId,
        @Nonnull SkillTreeData skillTreeData
    ) {
        return conditionalNodeCache.computeIfAbsent(playerId, id -> {
            List<ConditionalNodeInfo> nodes = new ArrayList<>();

            for (String nodeId : skillTreeData.getAllocatedNodes()) {
                SkillNode node = skillTreeConfig.getNode(nodeId);
                if (node != null && node.hasConditional()) {
                    ConditionalConfig config = node.getConditional();
                    if (config != null) {
                        nodes.add(new ConditionalNodeInfo(nodeId, config));
                    }
                }
            }

            LOGGER.atFine().log("Cached %d conditional nodes for player %s", nodes.size(), playerId);
            return nodes;
        });
    }

    /**
     * Runs cleanup if enough time has passed.
     */
    private void maybeCleanup(long currentTime) {
        if (currentTime - lastCleanupTime > CLEANUP_INTERVAL_MS) {
            lastCleanupTime = currentTime;
            int totalRemoved = 0;

            for (ConditionalEffectTracker tracker : trackers.values()) {
                totalRemoved += tracker.cleanup(currentTime);
            }

            if (totalRemoved > 0) {
                LOGGER.atFine().log("Cleanup removed %d expired effects", totalRemoved);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // INNER CLASSES
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Cached info about a conditional node.
     */
    private record ConditionalNodeInfo(String nodeId, ConditionalConfig config) {}
}
