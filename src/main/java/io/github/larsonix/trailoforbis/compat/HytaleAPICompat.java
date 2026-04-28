package io.github.larsonix.trailoforbis.compat;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Compatibility layer for fragile Hytale API access.
 *
 * <p>This class centralizes all reflection-based and potentially fragile API calls,
 * providing safe fallbacks and clear logging when APIs change or are unavailable.
 *
 * <p><b>Why this exists:</b> Some game features require accessing private fields
 * or APIs that may change between Hytale versions. By centralizing these accesses:
 * <ul>
 *   <li>Failures are detected at startup, not during gameplay</li>
 *   <li>Safe Optional-returning methods prevent NPEs</li>
 *   <li>Updates only require changes in one place</li>
 *   <li>Clear logging helps diagnose version compatibility issues</li>
 * </ul>
 *
 * <p><b>Usage:</b>
 * <pre>
 * // Initialize at plugin startup
 * HytaleAPICompat.initialize();
 *
 * // Safe projectile creator access
 * Optional&lt;UUID&gt; creator = HytaleAPICompat.getProjectileCreator(projectileComponent);
 *
 * // Safe damage cause lookup (asset names use PascalCase)
 * int index = HytaleAPICompat.getDamageCauseIndex("Rpg_Physical_Crit");
 * </pre>
 */
public final class HytaleAPICompat {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // ==================== Feature Flags ====================

    private static boolean initialized = false;
    private static boolean hasProjectileCreatorField = false;

    // ==================== Reflection Caches ====================

    private static Field projectileCreatorField;

    // Cache for damage cause indices (asset name -> index)
    private static final ConcurrentHashMap<String, Integer> damageCauseCache = new ConcurrentHashMap<>();

    // ==================== Initialization ====================

    private HytaleAPICompat() {
        // Utility class - no instantiation
    }

    /**
     * Initializes the compatibility layer.
     *
     * <p>Call this once during plugin startup. This method:
     * <ul>
     *   <li>Checks for availability of reflection targets</li>
     *   <li>Caches Field objects for performance</li>
     *   <li>Logs warnings for any unavailable features</li>
     * </ul>
     *
     * <p>Safe to call multiple times - subsequent calls are no-ops.
     */
    public static void initialize() {
        if (initialized) {
            return;
        }

        LOGGER.at(Level.INFO).log("Initializing Hytale API compatibility layer...");
        int available = 0;
        int unavailable = 0;

        // Check ProjectileComponent.creatorUuid field
        try {
            projectileCreatorField = ProjectileComponent.class.getDeclaredField("creatorUuid");
            projectileCreatorField.setAccessible(true);
            hasProjectileCreatorField = true;
            available++;
            LOGGER.at(Level.INFO).log("  [OK] ProjectileComponent.creatorUuid - projectile ownership tracking enabled");
        } catch (NoSuchFieldException e) {
            hasProjectileCreatorField = false;
            unavailable++;
            LOGGER.at(Level.WARNING).log("  [MISSING] ProjectileComponent.creatorUuid - projectile damage will use fallback attribution");
        } catch (SecurityException e) {
            hasProjectileCreatorField = false;
            unavailable++;
            LOGGER.at(Level.WARNING).log("  [BLOCKED] ProjectileComponent.creatorUuid - security manager blocked access");
        }

        initialized = true;

        if (unavailable > 0) {
            LOGGER.at(Level.WARNING).log("API compatibility: %d features available, %d unavailable (some functionality may be limited)",
                available, unavailable);
        } else {
            LOGGER.at(Level.INFO).log("API compatibility: all %d features available", available);
        }
    }

    /**
     * Checks if the compatibility layer has been initialized.
     *
     * @return true if {@link #initialize()} has been called
     */
    public static boolean isInitialized() {
        return initialized;
    }

    // ==================== Projectile API ====================

    /**
     * Checks if projectile creator tracking is available.
     *
     * @return true if {@link #getProjectileCreator} can return values
     */
    public static boolean hasProjectileCreatorSupport() {
        return hasProjectileCreatorField;
    }

    /**
     * Gets the creator UUID from a ProjectileComponent.
     *
     * <p>This accesses a private field via reflection. If the field is unavailable
     * (API changed), returns empty Optional instead of throwing.
     *
     * @param projectile The projectile component to query
     * @return Optional containing creator UUID, or empty if unavailable/null
     */
    @Nonnull
    public static Optional<UUID> getProjectileCreator(@Nonnull ProjectileComponent projectile) {
        if (!hasProjectileCreatorField || projectileCreatorField == null) {
            return Optional.empty();
        }

        try {
            UUID creator = (UUID) projectileCreatorField.get(projectile);
            return Optional.ofNullable(creator);
        } catch (IllegalAccessException e) {
            LOGGER.at(Level.SEVERE).log("Failed to access ProjectileComponent.creatorUuid: %s", e.getMessage());
            return Optional.empty();
        } catch (ClassCastException e) {
            LOGGER.at(Level.SEVERE).log("ProjectileComponent.creatorUuid is not a UUID: %s", e.getMessage());
            // Disable future attempts
            hasProjectileCreatorField = false;
            return Optional.empty();
        }
    }

    // ==================== DamageCause API ====================

    /**
     * Value returned when a damage cause is not found.
     */
    public static final int DAMAGE_CAUSE_NOT_FOUND = Integer.MIN_VALUE;

    /**
     * Gets the index for a damage cause asset by name.
     *
     * <p>Results are cached for performance. Returns {@link #DAMAGE_CAUSE_NOT_FOUND}
     * if the damage cause doesn't exist in the current game version.
     *
     * <p><b>Note:</b> Asset names are derived from file names and use PascalCase
     * (e.g., {@code Rpg_Physical_Crit} for {@code Rpg_Physical_Crit.json}).
     *
     * @param assetName The damage cause asset name (e.g., "Rpg_Physical_Crit")
     * @return The damage cause index, or {@link #DAMAGE_CAUSE_NOT_FOUND} if not found
     */
    public static int getDamageCauseIndex(@Nonnull String assetName) {
        return damageCauseCache.computeIfAbsent(assetName, name -> {
            try {
                int index = DamageCause.getAssetMap().getIndex(name);
                if (index != Integer.MIN_VALUE) {
                    LOGGER.at(Level.FINE).log("Resolved damage cause '%s' -> index %d", name, index);
                }
                return index;
            } catch (Exception e) {
                LOGGER.at(Level.WARNING).log("Failed to resolve damage cause '%s': %s", name, e.getMessage());
                return DAMAGE_CAUSE_NOT_FOUND;
            }
        });
    }

    /**
     * Checks if a damage cause asset exists.
     *
     * @param assetName The damage cause asset name
     * @return true if the damage cause exists and can be used
     */
    public static boolean hasDamageCause(@Nonnull String assetName) {
        return getDamageCauseIndex(assetName) != DAMAGE_CAUSE_NOT_FOUND;
    }

    /**
     * Clears the damage cause cache.
     *
     * <p>Call this if damage cause assets are reloaded at runtime.
     */
    public static void clearDamageCauseCache() {
        damageCauseCache.clear();
    }

    // ==================== Diagnostics ====================

    /**
     * Returns a diagnostic summary of API compatibility status.
     *
     * @return Multi-line string describing available/unavailable features
     */
    @Nonnull
    public static String getDiagnostics() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== HytaleAPICompat Diagnostics ===\n");
        sb.append("Initialized: ").append(initialized).append("\n");
        sb.append("\n[Reflection Features]\n");
        sb.append("  ProjectileComponent.creatorUuid: ").append(hasProjectileCreatorField ? "AVAILABLE" : "UNAVAILABLE").append("\n");
        sb.append("\n[Cached DamageCauses]\n");
        if (damageCauseCache.isEmpty()) {
            sb.append("  (none cached yet)\n");
        } else {
            damageCauseCache.forEach((name, index) -> {
                sb.append("  ").append(name).append(" -> ");
                sb.append(index != DAMAGE_CAUSE_NOT_FOUND ? "index " + index : "NOT FOUND");
                sb.append("\n");
            });
        }
        return sb.toString();
    }
}
