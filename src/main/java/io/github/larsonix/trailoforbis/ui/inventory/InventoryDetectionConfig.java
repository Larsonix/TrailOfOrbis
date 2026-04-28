package io.github.larsonix.trailoforbis.ui.inventory;

import javax.annotation.Nonnull;
import java.util.Map;

/**
 * Configuration for inventory detection heuristics.
 *
 * <p>Inventory detection works by analyzing player packets to detect when
 * the inventory screen is likely open. Since Hytale doesn't send an
 * "OpenInventory" packet, we use behavioral signals:
 *
 * <ul>
 *   <li>Camera freeze: lookOrientation stops changing</li>
 *   <li>UI click detection: MouseInteraction with no worldInteraction</li>
 *   <li>Activity tracking: Distinguish inventory from AFK/idle states</li>
 * </ul>
 *
 * <p>This config allows tuning detection sensitivity vs false positives.
 *
 * @see InventoryDetectionManager
 * @see InventoryStateTracker
 */
public class InventoryDetectionConfig {

    // ═══════════════════════════════════════════════════════════════════
    // FIELDS
    // ═══════════════════════════════════════════════════════════════════

    private boolean enabled = true;

    // Optimistic show (fast detection)
    private int optimisticFreezePackets = 2;
    private long optimisticConfirmMs = 150;

    // Confirmed detection
    private int confirmedFreezePackets = 5;

    // Multi-signal confirmation
    private long worldInteractionStaleMs = 500;

    // General settings
    private float lookDeltaEpsilon = 0.001f;
    private long minActivityBeforeDetectMs = 500;
    private long cooldownAfterCloseMs = 300;

    // Close detection (prevents false closes from camera jitter)
    private long closeImmunityMs = 500;
    private int closeMovementPackets = 3;

    // Debug
    private boolean debug = false;

    // ═══════════════════════════════════════════════════════════════════
    // GETTERS
    // ═══════════════════════════════════════════════════════════════════

    public boolean isEnabled() {
        return enabled;
    }

    public int getOptimisticFreezePackets() {
        return optimisticFreezePackets;
    }

    public long getOptimisticConfirmMs() {
        return optimisticConfirmMs;
    }

    public int getConfirmedFreezePackets() {
        return confirmedFreezePackets;
    }

    public long getWorldInteractionStaleMs() {
        return worldInteractionStaleMs;
    }

    public float getLookDeltaEpsilon() {
        return lookDeltaEpsilon;
    }

    public long getMinActivityBeforeDetectMs() {
        return minActivityBeforeDetectMs;
    }

    public long getCooldownAfterCloseMs() {
        return cooldownAfterCloseMs;
    }

    public long getCloseImmunityMs() {
        return closeImmunityMs;
    }

    public int getCloseMovementPackets() {
        return closeMovementPackets;
    }

    public boolean isDebug() {
        return debug;
    }

    // ═══════════════════════════════════════════════════════════════════
    // LOADING
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Loads configuration from a YAML map.
     *
     * @param yaml The YAML configuration map
     * @return The loaded configuration
     */
    @Nonnull
    public static InventoryDetectionConfig fromYaml(@Nonnull Map<String, Object> yaml) {
        InventoryDetectionConfig config = new InventoryDetectionConfig();

        config.enabled = getBoolean(yaml, "enabled", true);
        config.optimisticFreezePackets = getInt(yaml, "optimistic_freeze_packets", 2);
        config.optimisticConfirmMs = getLong(yaml, "optimistic_confirm_ms", 150);
        config.confirmedFreezePackets = getInt(yaml, "confirmed_freeze_packets", 5);
        config.worldInteractionStaleMs = getLong(yaml, "world_interaction_stale_ms", 500);
        config.lookDeltaEpsilon = getFloat(yaml, "look_delta_epsilon", 0.001f);
        config.minActivityBeforeDetectMs = getLong(yaml, "min_activity_before_detect_ms", 500);
        config.cooldownAfterCloseMs = getLong(yaml, "cooldown_after_close_ms", 300);
        config.closeImmunityMs = getLong(yaml, "close_immunity_ms", 500);
        config.closeMovementPackets = getInt(yaml, "close_movement_packets", 3);
        config.debug = getBoolean(yaml, "debug", false);

        return config;
    }

    /**
     * Creates a default configuration.
     *
     * @return The default configuration
     */
    @Nonnull
    public static InventoryDetectionConfig defaults() {
        return new InventoryDetectionConfig();
    }

    // ═══════════════════════════════════════════════════════════════════
    // YAML HELPERS
    // ═══════════════════════════════════════════════════════════════════

    private static boolean getBoolean(Map<String, Object> yaml, String key, boolean defaultValue) {
        Object value = yaml.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return defaultValue;
    }

    private static int getInt(Map<String, Object> yaml, String key, int defaultValue) {
        Object value = yaml.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }

    private static long getLong(Map<String, Object> yaml, String key, long defaultValue) {
        Object value = yaml.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return defaultValue;
    }

    private static float getFloat(Map<String, Object> yaml, String key, float defaultValue) {
        Object value = yaml.get(key);
        if (value instanceof Number) {
            return ((Number) value).floatValue();
        }
        return defaultValue;
    }
}
