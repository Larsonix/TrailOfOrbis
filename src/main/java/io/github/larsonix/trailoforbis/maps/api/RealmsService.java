package io.github.larsonix.trailoforbis.maps.api;

import io.github.larsonix.trailoforbis.maps.core.RealmBiomeType;
import io.github.larsonix.trailoforbis.maps.core.RealmLayoutSize;
import io.github.larsonix.trailoforbis.maps.core.RealmMapData;
import io.github.larsonix.trailoforbis.maps.instance.RealmInstance;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Public API for the Realms system.
 *
 * <p>This service provides methods to:
 * <ul>
 *   <li>Open new realm instances from map items</li>
 *   <li>Query active realm instances</li>
 *   <li>Manage player participation</li>
 *   <li>Close and clean up realms</li>
 * </ul>
 *
 * <p>Obtain an instance via the ServiceRegistry:
 * <pre>{@code
 * RealmsService service = ServiceRegistry.get(RealmsService.class);
 * CompletableFuture<RealmInstance> future = service.openRealm(mapData, playerId, worldUUID);
 * }</pre>
 *
 * @see RealmInstance
 * @see RealmMapData
 */
public interface RealmsService {

    // ═══════════════════════════════════════════════════════════════════
    // REALM CREATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Opens a new realm instance from map data.
     *
     * <p>This will:
     * <ol>
     *   <li>Validate the map data</li>
     *   <li>Create the realm instance</li>
     *   <li>Spawn the backing world from template</li>
     *   <li>Fire the RealmCreatedEvent</li>
     *   <li>Transition to READY state when world loads</li>
     * </ol>
     *
     * @param mapData The map data defining the realm
     * @param ownerId The player who is opening the realm
     * @param originWorldId The UUID of the world where the portal will be
     * @return A future that completes with the realm instance
     */
    @Nonnull
    CompletableFuture<RealmInstance> openRealm(
        @Nonnull RealmMapData mapData,
        @Nonnull UUID ownerId,
        @Nonnull UUID originWorldId
    );

    /**
     * Opens a realm with a specific return location.
     *
     * @param mapData The map data defining the realm
     * @param ownerId The player who is opening the realm
     * @param originWorldId The world to return to
     * @param returnX Return X coordinate
     * @param returnY Return Y coordinate
     * @param returnZ Return Z coordinate
     * @return A future that completes with the realm instance
     */
    @Nonnull
    CompletableFuture<RealmInstance> openRealm(
        @Nonnull RealmMapData mapData,
        @Nonnull UUID ownerId,
        @Nonnull UUID originWorldId,
        double returnX, double returnY, double returnZ
    );

    // ═══════════════════════════════════════════════════════════════════
    // REALM QUERIES
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets a realm instance by ID.
     *
     * @param realmId The realm UUID
     * @return The realm instance, or empty if not found
     */
    @Nonnull
    Optional<RealmInstance> getRealm(@Nonnull UUID realmId);

    /**
     * Gets the realm a player is currently in.
     *
     * @param playerId The player UUID
     * @return The realm instance, or empty if not in a realm
     */
    @Nonnull
    Optional<RealmInstance> getPlayerRealm(@Nonnull UUID playerId);

    /**
     * Gets all active realm instances.
     *
     * @return Unmodifiable collection of active realms
     */
    @Nonnull
    Collection<RealmInstance> getActiveRealms();

    /**
     * Gets all realms owned by a specific player.
     *
     * @param ownerId The owner's UUID
     * @return Collection of realms owned by the player
     */
    @Nonnull
    Collection<RealmInstance> getRealmsOwnedBy(@Nonnull UUID ownerId);

    /**
     * Gets the count of active realm instances.
     *
     * @return Number of active realms
     */
    int getActiveRealmCount();

    // ═══════════════════════════════════════════════════════════════════
    // PLAYER MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Teleports a player into a realm.
     *
     * @param playerId The player to teleport
     * @param realmId The target realm
     * @return A future that completes when teleportation is done
     */
    @Nonnull
    CompletableFuture<Boolean> enterRealm(@Nonnull UUID playerId, @Nonnull UUID realmId);

    /**
     * Teleports a player out of their current realm.
     *
     * @param playerId The player to teleport
     * @return A future that completes when teleportation is done
     */
    @Nonnull
    CompletableFuture<Boolean> exitRealm(@Nonnull UUID playerId);

    /**
     * Checks if a player is currently in any realm.
     *
     * @param playerId The player UUID
     * @return true if the player is in a realm
     */
    boolean isPlayerInRealm(@Nonnull UUID playerId);

    /**
     * Checks if a player can enter a specific realm.
     *
     * @param playerId The player UUID
     * @param realmId The realm UUID
     * @return true if the player can enter
     */
    boolean canPlayerEnter(@Nonnull UUID playerId, @Nonnull UUID realmId);

    // ═══════════════════════════════════════════════════════════════════
    // REALM LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Closes a realm instance.
     *
     * <p>This will:
     * <ol>
     *   <li>Teleport all players out</li>
     *   <li>Fire the RealmClosedEvent</li>
     *   <li>Remove the backing world</li>
     *   <li>Clean up resources</li>
     * </ol>
     *
     * @param realmId The realm to close
     * @param reason The reason for closing
     * @return A future that completes when the realm is closed
     */
    @Nonnull
    CompletableFuture<Void> closeRealm(
        @Nonnull UUID realmId,
        @Nonnull RealmInstance.CompletionReason reason
    );

    /**
     * Forces immediate closure of a realm (emergency shutdown).
     *
     * @param realmId The realm to close
     */
    void forceCloseRealm(@Nonnull UUID realmId);

    // ═══════════════════════════════════════════════════════════════════
    // VALIDATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Checks if the realm system is enabled.
     *
     * @return true if realms are enabled
     */
    boolean isEnabled();

    /**
     * Checks if a biome is available for realm creation.
     *
     * @param biome The biome to check
     * @return true if the biome has a valid template
     */
    boolean isBiomeAvailable(@Nonnull RealmBiomeType biome);

    /**
     * Checks if a size is available for realm creation.
     *
     * @param size The size to check
     * @return true if the size is enabled
     */
    boolean isSizeAvailable(@Nonnull RealmLayoutSize size);

    /**
     * Checks if more realms can be created (under limit).
     *
     * @return true if under the max concurrent realm limit
     */
    boolean canCreateMoreRealms();

    /**
     * Validates map data for realm creation.
     *
     * @param mapData The map data to validate
     * @return Validation result
     */
    @Nonnull
    ValidationResult validateMapData(@Nonnull RealmMapData mapData);

    // ═══════════════════════════════════════════════════════════════════
    // NESTED TYPES
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Result of map data validation.
     */
    record ValidationResult(
        boolean valid,
        @Nullable String errorKey,
        @Nullable String errorMessage
    ) {
        public static final ValidationResult OK = new ValidationResult(true, null, null);

        public static ValidationResult error(String errorKey, String message) {
            return new ValidationResult(false, errorKey, message);
        }

        public static ValidationResult disabled() {
            return error("realms.disabled", "Realms system is disabled");
        }

        public static ValidationResult biomeUnavailable() {
            return error("realms.biome_unavailable", "This biome is not available");
        }

        public static ValidationResult sizeUnavailable() {
            return error("realms.size_unavailable", "This size is not available");
        }

        public static ValidationResult limitReached() {
            return error("realms.limit_reached", "Maximum concurrent realms reached");
        }

        public static ValidationResult templateMissing() {
            return error("realms.template_missing", "No template found for this configuration");
        }
    }
}
