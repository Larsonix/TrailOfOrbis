package io.github.larsonix.trailoforbis.ui.inventory;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.Direction;
import com.hypixel.hytale.protocol.MouseButtonState;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.player.ClientMovement;
import com.hypixel.hytale.protocol.packets.player.MouseInteraction;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.io.adapter.PacketFilter;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.ui.inventory.InventoryStateTracker.DetectionState;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages inventory detection via packet analysis.
 *
 * <p>This manager uses packet analysis to detect when players open their
 * inventory screen, since Hytale doesn't send an explicit "OpenInventory" packet.
 *
 * <p><b>Note:</b> The stats HUD feature was removed because HyUI HUDs do not
 * support button click events (internal events field is never initialized for
 * HUD contexts, causing NPE in ButtonBuilder). This detection logic is kept
 * for potential future use (keybinds, chat hints, etc.).
 *
 * <h2>Detection Signals</h2>
 * <ol>
 *   <li><b>Camera freeze</b>: lookOrientation in ClientMovement stops changing</li>
 *   <li><b>No world interaction</b>: MouseInteraction has no worldInteraction (UI clicks)</li>
 *   <li><b>Activity tracking</b>: Player was recently looking around (not AFK)</li>
 * </ol>
 *
 * <h2>State Machine</h2>
 * <pre>
 * INACTIVE → (2 frozen packets + was active) → OPTIMISTIC_SHOW
 * INACTIVE → (UI click detected) → CONFIRMED (instant)
 * OPTIMISTIC_SHOW → (look changes within 150ms) → INACTIVE (quick dismiss)
 * OPTIMISTIC_SHOW → (5 frozen + no worldInteraction) → CONFIRMED
 * CONFIRMED → (lookOrientation changes OR worldInteraction) → INACTIVE
 * </pre>
 *
 * @see InventoryStateTracker
 */
public class InventoryDetectionManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // ═══════════════════════════════════════════════════════════════════
    // FIELDS
    // ═══════════════════════════════════════════════════════════════════

    private final TrailOfOrbis plugin;
    private final InventoryDetectionConfig config;

    /** Per-player state trackers */
    private final Map<UUID, InventoryStateTracker> trackers = new ConcurrentHashMap<>();

    /** Packet filter handle for cleanup */
    @Nullable
    private PacketFilter packetFilter;

    // ═══════════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════════

    public InventoryDetectionManager(@Nonnull TrailOfOrbis plugin,
                                     @Nonnull InventoryDetectionConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    // ═══════════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Enables inventory detection by registering packet watchers.
     */
    public void onEnable() {
        if (!config.isEnabled()) {
            LOGGER.atInfo().log("Inventory detection disabled in config");
            return;
        }

        // Register inbound packet watcher
        packetFilter = PacketAdapters.registerInbound(this::onPacket);
        LOGGER.atFine().log("Inventory detection enabled (optimisticFreeze=%d, epsilon=%.6f)",
            config.getOptimisticFreezePackets(),
            config.getLookDeltaEpsilon());
    }

    /**
     * Disables inventory detection and cleans up.
     */
    public void onDisable() {
        // Deregister packet filter
        if (packetFilter != null) {
            try {
                PacketAdapters.deregisterInbound(packetFilter);
            } catch (IllegalArgumentException e) {
                // Already deregistered, ignore
            }
            packetFilter = null;
        }

        // Clear trackers
        trackers.clear();

        LOGGER.atFine().log("Inventory detection disabled");
    }

    // ═══════════════════════════════════════════════════════════════════
    // PACKET HANDLING
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Handles incoming packets from players.
     */
    private void onPacket(@Nonnull PlayerRef player, @Nonnull Packet packet) {
        if (packet instanceof ClientMovement cm) {
            handleMovement(player, cm);
        } else if (packet instanceof MouseInteraction mi) {
            handleMouseInteraction(player, mi);
        }
    }

    /**
     * Processes ClientMovement packets for look orientation tracking.
     */
    private void handleMovement(@Nonnull PlayerRef player, @Nonnull ClientMovement packet) {
        UUID uuid = player.getUuid();
        InventoryStateTracker tracker = getOrCreateTracker(uuid);

        // Skip if player is in a state where frozen look is expected
        if (InventoryStateTracker.isIgnoredState(packet.movementStates)) {
            tracker.resetFreezeCount();
            return;
        }

        // Check look orientation change
        Direction current = packet.lookOrientation;
        Direction previous = tracker.getLastLookOrientation();

        if (current != null && previous != null) {
            boolean frozen = isLookFrozen(previous, current);
            if (frozen) {
                tracker.incrementFreezeCount();
                tracker.resetMovementCount();
            } else {
                tracker.resetFreezeCount();
                tracker.incrementMovementCount();
                tracker.markActivity();
            }
        } else if (current != null) {
            // First packet, just record
            tracker.markActivity();
        }

        tracker.setLastLookOrientation(current);
        updateDetectionState(player, tracker);
    }

    /**
     * Processes MouseInteraction packets for world/UI click detection.
     */
    private void handleMouseInteraction(@Nonnull PlayerRef player, @Nonnull MouseInteraction packet) {
        UUID uuid = player.getUuid();
        InventoryStateTracker tracker = getOrCreateTracker(uuid);

        // Track world interaction presence (false positive mitigation)
        if (packet.worldInteraction != null) {
            // Player clicked/hovered on world - NOT in inventory
            tracker.markWorldInteraction();

            // If we thought they were in inventory, they're not
            if (tracker.isInventoryDetected()) {
                closeInventory(player, tracker);
            }
        } else if (packet.screenPoint != null) {
            // Click on UI (screenPoint present, worldInteraction absent)
            tracker.clearWorldInteraction();

            // INSTANT DETECTION: UI click with mouse button pressed
            boolean hasButton = packet.mouseButton != null && packet.mouseButton.state == MouseButtonState.Pressed;
            boolean wasActive = tracker.wasRecentlyActive(config.getMinActivityBeforeDetectMs());
            boolean inCooldown = tracker.isInCooldown(config.getCooldownAfterCloseMs());
            boolean isInactive = tracker.getState() == DetectionState.INACTIVE;

            if (hasButton && wasActive && !inCooldown && isInactive) {
                // Definitive proof player is in some UI
                tracker.setState(DetectionState.CONFIRMED);
                onInventoryDetected(player);
            }
        }
    }

    /**
     * Checks if look orientation is effectively frozen.
     */
    private boolean isLookFrozen(@Nonnull Direction prev, @Nonnull Direction curr) {
        float epsilon = config.getLookDeltaEpsilon();
        return Math.abs(prev.yaw - curr.yaw) < epsilon
            && Math.abs(prev.pitch - curr.pitch) < epsilon;
    }

    // ═══════════════════════════════════════════════════════════════════
    // STATE MACHINE
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Updates detection state based on current tracker state.
     */
    private void updateDetectionState(@Nonnull PlayerRef player,
                                      @Nonnull InventoryStateTracker tracker) {
        DetectionState state = tracker.getState();
        long now = System.currentTimeMillis();

        switch (state) {
            case INACTIVE -> {
                // OPTIMISTIC SHOW: Quick trigger for instant feel
                int freezeCount = tracker.getFreezeCount();
                boolean wasActive = tracker.wasRecentlyActive(config.getMinActivityBeforeDetectMs());
                boolean inCooldown = tracker.isInCooldown(config.getCooldownAfterCloseMs());

                if (freezeCount >= config.getOptimisticFreezePackets()
                    && wasActive
                    && !inCooldown) {

                    tracker.setState(DetectionState.OPTIMISTIC_SHOW);
                    tracker.setOptimisticShowTime(now);
                    onInventoryDetected(player);
                }
            }

            case OPTIMISTIC_SHOW -> {
                long elapsed = now - tracker.getOptimisticShowTime();

                // QUICK DISMISS: Movement within confirm window = false positive
                if (tracker.getFreezeCount() == 0 && elapsed < config.getOptimisticConfirmMs()) {
                    closeInventoryWithCooldown(player, tracker);
                    return;
                }

                // CONFIRM: Still frozen after confirm window + no worldInteraction
                if (elapsed >= config.getOptimisticConfirmMs()
                    && tracker.getFreezeCount() >= config.getConfirmedFreezePackets()
                    && !tracker.hasRecentWorldInteraction(config.getWorldInteractionStaleMs())) {

                    tracker.setState(DetectionState.CONFIRMED);
                    tracker.setConfirmationTime(now);
                    tracker.resetMovementCount();
                }
            }

            case CONFIRMED -> {
                long confirmedDuration = now - tracker.getConfirmationTime();

                // IMMUNITY: Don't close within first N ms of confirmation
                if (confirmedDuration < config.getCloseImmunityMs()) {
                    return;
                }

                // CLOSE: Require consecutive movement packets (filters jitter)
                if (tracker.getMovementCount() >= config.getCloseMovementPackets()) {
                    closeInventory(player, tracker);
                }
            }
        }
    }

    /**
     * Closes detected inventory state.
     */
    private void closeInventory(@Nonnull PlayerRef player,
                                @Nonnull InventoryStateTracker tracker) {
        tracker.setState(DetectionState.INACTIVE);
        tracker.resetMovementCount();
        onInventoryClosed(player);
    }

    /**
     * Closes detected inventory state with cooldown (for false positives).
     */
    private void closeInventoryWithCooldown(@Nonnull PlayerRef player,
                                            @Nonnull InventoryStateTracker tracker) {
        tracker.setState(DetectionState.INACTIVE);
        tracker.resetMovementCount();
        tracker.setLastCloseTime(System.currentTimeMillis());
        onInventoryClosed(player);
    }

    // ═══════════════════════════════════════════════════════════════════
    // DETECTION CALLBACKS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Called when inventory is detected as open.
     *
     * <p>Currently a no-op. Future use cases:
     * <ul>
     *   <li>Show a keybind hint in chat</li>
     *   <li>Trigger a keybind-based stats page open</li>
     *   <li>Analytics/telemetry</li>
     * </ul>
     */
    @SuppressWarnings("unused")
    private void onInventoryDetected(@Nonnull PlayerRef player) {
        // No-op: HUD feature removed (HyUI HUDs don't support button events)
    }

    /**
     * Called when inventory is detected as closed.
     */
    @SuppressWarnings("unused")
    private void onInventoryClosed(@Nonnull PlayerRef player) {
        // No-op: HUD feature removed
    }

    // ═══════════════════════════════════════════════════════════════════
    // TRACKER MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets or creates a tracker for a player.
     */
    @Nonnull
    private InventoryStateTracker getOrCreateTracker(@Nonnull UUID uuid) {
        return trackers.computeIfAbsent(uuid, k -> new InventoryStateTracker());
    }

    /**
     * Removes a player's tracker (call on disconnect).
     */
    public void removeTracker(@Nonnull UUID uuid) {
        trackers.remove(uuid);
    }

    // ═══════════════════════════════════════════════════════════════════
    // GETTERS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets the configuration.
     */
    @Nonnull
    public InventoryDetectionConfig getConfig() {
        return config;
    }

    /**
     * Checks if a player is detected as having inventory open.
     */
    public boolean isInventoryDetected(@Nonnull UUID uuid) {
        InventoryStateTracker tracker = trackers.get(uuid);
        return tracker != null && tracker.isInventoryDetected();
    }
}
