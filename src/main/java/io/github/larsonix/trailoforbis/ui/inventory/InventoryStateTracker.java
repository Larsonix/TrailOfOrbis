package io.github.larsonix.trailoforbis.ui.inventory;

import com.hypixel.hytale.protocol.Direction;
import com.hypixel.hytale.protocol.MovementStates;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Per-player state tracker for inventory detection.
 *
 * <p>Implements a state machine with three detection states:
 * <ul>
 *   <li>{@link DetectionState#INACTIVE} - Normal gameplay</li>
 *   <li>{@link DetectionState#OPTIMISTIC_SHOW} - HUD shown, awaiting confirmation</li>
 *   <li>{@link DetectionState#CONFIRMED} - Definitely in inventory</li>
 * </ul>
 *
 * <p>Detection is based on multiple signals:
 * <ul>
 *   <li>Camera freeze: lookOrientation stops changing</li>
 *   <li>World interaction: Absence of worldInteraction in MouseInteraction packets</li>
 *   <li>UI clicks: screenPoint present without worldInteraction</li>
 * </ul>
 *
 * @see InventoryDetectionManager
 */
public class InventoryStateTracker {

    // ═══════════════════════════════════════════════════════════════════
    // DETECTION STATE ENUM
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Detection state machine states.
     */
    public enum DetectionState {
        /** Normal gameplay - not in inventory */
        INACTIVE,
        /** HUD shown optimistically, awaiting confirmation */
        OPTIMISTIC_SHOW,
        /** Confirmed to be in inventory */
        CONFIRMED
    }

    // ═══════════════════════════════════════════════════════════════════
    // LOOK ORIENTATION TRACKING
    // ═══════════════════════════════════════════════════════════════════

    /** Previous look direction */
    @Nullable
    private Direction lastLookOrientation;

    /** Consecutive packets with no look change */
    private int freezePacketCount;

    /** Last time player was actively looking around */
    private long lastActivityTime;

    // ═══════════════════════════════════════════════════════════════════
    // DETECTION STATE
    // ═══════════════════════════════════════════════════════════════════

    /** Current detection state */
    @Nonnull
    private DetectionState state = DetectionState.INACTIVE;

    /** When optimistic show was triggered */
    private long optimisticShowTime;

    /** When inventory was last closed (for cooldown) */
    private long lastCloseTime;

    /** When CONFIRMED state was entered (for close immunity) */
    private long confirmationTime;

    /** Consecutive non-frozen packets (for close detection) */
    private int movementCount;

    // ═══════════════════════════════════════════════════════════════════
    // MULTI-SIGNAL TRACKING (MouseInteraction)
    // ═══════════════════════════════════════════════════════════════════

    /** Whether player had a recent world interaction */
    private boolean hasRecentWorldInteraction;

    /** When last world click occurred */
    private long lastWorldInteractionTime;

    // ═══════════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════════

    public InventoryStateTracker() {
        this.lastActivityTime = System.currentTimeMillis();
    }

    // ═══════════════════════════════════════════════════════════════════
    // LOOK ORIENTATION TRACKING
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets the last recorded look orientation.
     */
    @Nullable
    public Direction getLastLookOrientation() {
        return lastLookOrientation;
    }

    /**
     * Sets the last look orientation.
     */
    public void setLastLookOrientation(@Nullable Direction direction) {
        this.lastLookOrientation = direction;
    }

    /**
     * Gets the number of consecutive frozen packets.
     */
    public int getFreezeCount() {
        return freezePacketCount;
    }

    /**
     * Increments the freeze packet count.
     */
    public void incrementFreezeCount() {
        freezePacketCount++;
    }

    /**
     * Resets the freeze packet count.
     */
    public void resetFreezeCount() {
        freezePacketCount = 0;
    }

    /**
     * Marks that the player was actively looking.
     */
    public void markActivity() {
        lastActivityTime = System.currentTimeMillis();
    }

    /**
     * Checks if player was recently active (looking around).
     *
     * @param minActivityMs The required activity threshold in ms
     * @return true if player was active within the threshold
     */
    public boolean wasRecentlyActive(long minActivityMs) {
        return (System.currentTimeMillis() - lastActivityTime) < minActivityMs;
    }

    // ═══════════════════════════════════════════════════════════════════
    // DETECTION STATE
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets the current detection state.
     */
    @Nonnull
    public DetectionState getState() {
        return state;
    }

    /**
     * Sets the detection state.
     */
    public void setState(@Nonnull DetectionState state) {
        this.state = state;
    }

    /**
     * Gets the time when optimistic show was triggered.
     */
    public long getOptimisticShowTime() {
        return optimisticShowTime;
    }

    /**
     * Sets the optimistic show time.
     */
    public void setOptimisticShowTime(long time) {
        this.optimisticShowTime = time;
    }

    /**
     * Gets the time when inventory was last closed.
     */
    public long getLastCloseTime() {
        return lastCloseTime;
    }

    /**
     * Sets the last close time.
     */
    public void setLastCloseTime(long time) {
        this.lastCloseTime = time;
    }

    /**
     * Checks if in cooldown period after close.
     *
     * @param cooldownMs The cooldown duration in ms
     * @return true if in cooldown
     */
    public boolean isInCooldown(long cooldownMs) {
        return (System.currentTimeMillis() - lastCloseTime) < cooldownMs;
    }

    /**
     * Checks if inventory is detected (either optimistic or confirmed).
     */
    public boolean isInventoryDetected() {
        return state == DetectionState.OPTIMISTIC_SHOW || state == DetectionState.CONFIRMED;
    }

    // ═══════════════════════════════════════════════════════════════════
    // CLOSE IMMUNITY (Prevents jitter-based closes)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets the time when CONFIRMED state was entered.
     */
    public long getConfirmationTime() {
        return confirmationTime;
    }

    /**
     * Sets the confirmation time.
     */
    public void setConfirmationTime(long time) {
        this.confirmationTime = time;
    }

    /**
     * Gets the number of consecutive movement packets.
     */
    public int getMovementCount() {
        return movementCount;
    }

    /**
     * Increments the movement packet count.
     */
    public void incrementMovementCount() {
        movementCount++;
    }

    /**
     * Resets the movement packet count.
     */
    public void resetMovementCount() {
        movementCount = 0;
    }

    // ═══════════════════════════════════════════════════════════════════
    // MULTI-SIGNAL TRACKING
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Marks that player had a world interaction (clicked on world).
     */
    public void markWorldInteraction() {
        hasRecentWorldInteraction = true;
        lastWorldInteractionTime = System.currentTimeMillis();
    }

    /**
     * Clears the world interaction flag.
     */
    public void clearWorldInteraction() {
        hasRecentWorldInteraction = false;
    }

    /**
     * Checks if player had a recent world interaction.
     *
     * @param staleMs How long until world interaction is considered stale
     * @return true if player clicked in world recently
     */
    public boolean hasRecentWorldInteraction(long staleMs) {
        if (!hasRecentWorldInteraction) {
            return false;
        }
        return (System.currentTimeMillis() - lastWorldInteractionTime) < staleMs;
    }

    // ═══════════════════════════════════════════════════════════════════
    // MOVEMENT STATE HELPERS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Checks if movement states indicate camera should be frozen naturally.
     *
     * <p>Returns true if player is in a state where frozen camera is expected
     * (not inventory), such as idle, sleeping, sitting, gliding, or mounting.
     *
     * @param states The movement states from packet
     * @return true if camera freeze is expected due to movement state
     */
    public static boolean isIgnoredState(@Nullable MovementStates states) {
        if (states == null) {
            return false;
        }
        return states.idle || states.sleeping || states.sitting
            || states.gliding || states.mounting;
    }

    // ═══════════════════════════════════════════════════════════════════
    // RESET
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Resets the tracker to initial state.
     */
    public void reset() {
        lastLookOrientation = null;
        freezePacketCount = 0;
        state = DetectionState.INACTIVE;
        optimisticShowTime = 0;
        lastCloseTime = 0;
        confirmationTime = 0;
        movementCount = 0;
        hasRecentWorldInteraction = false;
        lastWorldInteractionTime = 0;
        lastActivityTime = System.currentTimeMillis();
    }
}
