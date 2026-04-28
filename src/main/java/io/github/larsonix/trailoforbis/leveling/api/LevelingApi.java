package io.github.larsonix.trailoforbis.leveling.api;

import io.github.larsonix.trailoforbis.api.ServiceRegistry;

import javax.annotation.Nonnull;
import java.util.Optional;

/**
 * Static entry point for accessing the leveling system.
 *
 * <p>Provides convenient static methods for accessing the {@link LevelingService}
 * without needing to interact with the ServiceRegistry directly.
 *
 * <p>Usage:
 * <pre>
 * // Check if leveling is available
 * if (LevelingApi.isAvailable()) {
 *     int level = LevelingApi.getService().getLevel(playerId);
 * }
 *
 * // Or with Optional handling
 * LevelingApi.getServiceIfPresent().ifPresent(service -> {
 *     service.addXp(playerId, 100, XpSource.MOB_KILL);
 * });
 * </pre>
 */
public final class LevelingApi {

    private LevelingApi() {
        // Static utility class - prevent instantiation
    }

    /**
     * Gets the LevelingService instance.
     *
     * <p>Throws if the leveling system is not initialized.
     * Use {@link #isAvailable()} or {@link #getServiceIfPresent()} for defensive access.
     *
     * @return The LevelingService instance
     * @throws IllegalStateException if the service is not registered
     */
    @Nonnull
    public static LevelingService getService() {
        return ServiceRegistry.require(LevelingService.class);
    }

    /**
     * Gets the LevelingService if available.
     *
     * <p>Returns empty Optional if the leveling system is disabled or not yet initialized.
     *
     * @return Optional containing the service, or empty if not available
     */
    @Nonnull
    public static Optional<LevelingService> getServiceIfPresent() {
        return ServiceRegistry.get(LevelingService.class);
    }

    /**
     * Checks if the leveling system is available.
     *
     * @return true if LevelingService is registered and ready to use
     */
    public static boolean isAvailable() {
        return ServiceRegistry.isRegistered(LevelingService.class);
    }
}
