package io.github.larsonix.trailoforbis.loot.container;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import io.github.larsonix.trailoforbis.api.ServiceRegistry;
import io.github.larsonix.trailoforbis.gear.conversion.ItemClassifier;
import io.github.larsonix.trailoforbis.gear.conversion.VanillaConversionConfig;
import io.github.larsonix.trailoforbis.gear.loot.DropLevelBlender;
import io.github.larsonix.trailoforbis.gear.loot.LootGenerator;
import io.github.larsonix.trailoforbis.gear.loot.RarityBonusCalculator;
import io.github.larsonix.trailoforbis.leveling.api.LevelingService;
import io.github.larsonix.trailoforbis.maps.items.RealmMapGenerator;
import io.github.larsonix.trailoforbis.maps.reward.RewardChestManager;

import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Main coordinator for the container loot replacement system.
 *
 * <p>This class manages the overall container loot system:
 * <ul>
 *   <li>Initializes all components (config, tracker, generator, replacer)</li>
 *   <li>Creates the {@link ContainerLootInterceptor} ECS system for block interaction</li>
 *   <li>Provides entry points for container processing (admin commands, etc.)</li>
 *   <li>Handles lifecycle (enable/disable/reload)</li>
 * </ul>
 *
 * <h2>Architecture</h2>
 * <pre>
 * ContainerLootSystem (coordinator)
 *    ├── ContainerLootConfig (configuration)
 *    ├── ContainerTracker (processed container memory)
 *    ├── ContainerTierClassifier (tier detection)
 *    ├── ContainerLootGenerator (loot generation)
 *    ├── ContainerLootReplacer (replacement algorithm)
 *    └── ContainerLootInterceptor (ECS system, registered externally)
 * </pre>
 *
 * <h2>Event Flow</h2>
 * <ol>
 *   <li>{@link ContainerLootInterceptor} intercepts {@code UseBlockEvent.Pre}</li>
 *   <li>Interceptor checks if container is a reward chest or L4E-managed</li>
 *   <li>If not, interceptor delegates to this system's components for replacement</li>
 *   <li>Container is marked as processed to prevent re-processing</li>
 * </ol>
 *
 * @see ContainerLootConfig
 * @see ContainerLootReplacer
 * @see ContainerLootInterceptor
 */
public final class ContainerLootSystem {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // Components
    private final ContainerLootConfig config;
    private final ContainerTracker tracker;
    private final ContainerTierClassifier tierClassifier;
    private final ContainerLootGenerator lootGenerator;
    private final ContainerLootReplacer replacer;
    private final ItemClassifier itemClassifier;

    // State
    private boolean enabled;

    /**
     * Creates a new container loot system.
     *
     * @param config                  The container loot configuration
     * @param lootGenerator           The gear loot generator
     * @param mapGenerator            The realm map generator (nullable - map drops disabled if null)
     * @param conversionConfig        The vanilla conversion config for item classification
     * @param dropLevelBlender        The drop level blender
     * @param rarityBonusCalculator   Player WIND→rarity calculator (nullable for tests)
     */
    public ContainerLootSystem(
            @Nonnull ContainerLootConfig config,
            @Nonnull LootGenerator lootGenerator,
            @Nullable RealmMapGenerator mapGenerator,
            @Nonnull VanillaConversionConfig conversionConfig,
            @Nonnull DropLevelBlender dropLevelBlender,
            @Nullable RarityBonusCalculator rarityBonusCalculator) {

        this.config = Objects.requireNonNull(config, "config cannot be null");
        Objects.requireNonNull(lootGenerator, "lootGenerator cannot be null");
        // mapGenerator is nullable - map drops disabled if RealmsManager not available
        Objects.requireNonNull(conversionConfig, "conversionConfig cannot be null");
        Objects.requireNonNull(dropLevelBlender, "dropLevelBlender cannot be null");

        // Initialize components
        this.tracker = new ContainerTracker(config);
        this.tierClassifier = new ContainerTierClassifier(config);
        this.itemClassifier = new ItemClassifier(conversionConfig);
        this.lootGenerator = new ContainerLootGenerator(
            config, lootGenerator, mapGenerator, tierClassifier, dropLevelBlender, rarityBonusCalculator);
        this.replacer = new ContainerLootReplacer(
            config, this.lootGenerator, itemClassifier, tierClassifier);

        this.enabled = config.isEnabled();

        LOGGER.atInfo().log("ContainerLootSystem created (enabled: %s)", enabled);
    }

    // =========================================================================
    // LIFECYCLE
    // =========================================================================

    /**
     * Creates a {@link ContainerLootInterceptor} ECS system for registration.
     *
     * <p>The interceptor handles {@code UseBlockEvent.Pre} to intercept container
     * opens and replace vanilla loot with RPG gear. It must be registered with
     * {@code getEntityStoreRegistry().registerSystem()} by the caller.
     *
     * @param rewardChestManager          The reward chest manager (to skip reward chests), or null
     * @param realmsManager               The realms manager (for realm world detection), or null
     * @param processedContainerResType   The persistent resource type for tracking processed containers
     * @return The interceptor, or null if the system is disabled
     */
    @Nullable
    public ContainerLootInterceptor createInterceptor(
            @Nullable RewardChestManager rewardChestManager,
            @Nullable io.github.larsonix.trailoforbis.maps.RealmsManager realmsManager,
            @Nonnull ResourceType<ChunkStore, ProcessedContainerResource> processedContainerResType) {
        if (!enabled) {
            LOGGER.atInfo().log("Container loot system is disabled, not creating interceptor");
            return null;
        }

        return new ContainerLootInterceptor(this, rewardChestManager, realmsManager, processedContainerResType);
    }

    /**
     * Shuts down the system and releases resources.
     */
    public void shutdown() {
        tracker.shutdown();
        enabled = false;
        LOGGER.atInfo().log("ContainerLootSystem shut down");
    }

    /**
     * Reloads configuration and clears caches.
     *
     * <p>Call this after config reload.
     */
    public void reload() {
        tierClassifier.reload();
        tracker.clear();
        LOGGER.atInfo().log("ContainerLootSystem reloaded");
    }

    // =========================================================================
    // CONTAINER PROCESSING
    // =========================================================================

    /**
     * Processes a container directly, replacing loot with RPG items.
     *
     * <p>This method is intended for use when you have direct access to:
     * <ul>
     *   <li>The ItemContainer (from ItemContainerBlock)</li>
     *   <li>Block position (for tracking)</li>
     *   <li>Block type ID (for tier classification)</li>
     * </ul>
     *
     * <p>Used by admin commands and direct API calls. The normal flow goes
     * through {@link ContainerLootInterceptor} instead, which uses
     * {@link ProcessedContainerResource} for persistent tracking.
     *
     * <p><b>Note:</b> This method uses the in-memory {@link ContainerTracker}
     * (not persistent). For restart-safe tracking, use the interceptor path.
     *
     * @param container   The item container to process
     * @param playerId    The opening player's UUID (for level lookup)
     * @param worldName   The world name (for tracking)
     * @param blockX      Block X position (for tracking)
     * @param blockY      Block Y position (for tracking)
     * @param blockZ      Block Z position (for tracking)
     * @param blockTypeId The block type ID (for tier classification)
     * @return Result of the replacement, or null if skipped
     */
    @Nullable
    public ContainerLootReplacer.ReplacementResult processContainer(
            @Nonnull ItemContainer container,
            @Nonnull UUID playerId,
            @Nonnull String worldName,
            int blockX, int blockY, int blockZ,
            @Nullable String blockTypeId) {

        if (!enabled) {
            return null;
        }

        // Check if already processed
        if (tracker.isProcessed(worldName, blockX, blockY, blockZ)) {
            if (config.getAdvanced().isDebugLogging()) {
                LOGGER.atFine().log("Container at (%d, %d, %d) in %s already processed",
                    blockX, blockY, blockZ, worldName);
            }
            return null;
        }

        // Mark as processed first (prevents race conditions)
        boolean wasFirstOpener = tracker.markProcessed(worldName, blockX, blockY, blockZ, playerId);
        if (!wasFirstOpener) {
            // Another thread processed it first
            return null;
        }

        // Get player level
        int playerLevel = getPlayerLevel(playerId);

        // Classify container tier
        ContainerTier tier = tierClassifier.classify(blockTypeId);

        // Perform replacement (player rarity bonus applied via playerId)
        ContainerLootReplacer.ReplacementResult result = replacer.replace(container, playerLevel, tier, playerId);

        LOGGER.atInfo().log("Processed container at (%d, %d, %d): %s",
            blockX, blockY, blockZ, result.summary());

        return result;
    }

    // processContainerState removed — ItemContainerBlock no longer has block position/world.
    // Callers provide position and world name directly via processContainer().

    // =========================================================================
    // HELPERS
    // =========================================================================

    /**
     * Gets a player's level from the leveling service.
     *
     * @param playerId The player's UUID
     * @return The player's level, or 1 if service unavailable
     */
    private int getPlayerLevel(@Nonnull UUID playerId) {
        Optional<LevelingService> serviceOpt = ServiceRegistry.get(LevelingService.class);
        if (serviceOpt.isPresent()) {
            return serviceOpt.get().getLevel(playerId);
        }
        LOGGER.atWarning().log("LevelingService not available, using default level 1");
        return 1;
    }

    // =========================================================================
    // ACCESSORS
    // =========================================================================

    /**
     * Gets the configuration.
     */
    @Nonnull
    public ContainerLootConfig getConfig() {
        return config;
    }

    /**
     * Gets the container tracker.
     */
    @Nonnull
    public ContainerTracker getTracker() {
        return tracker;
    }

    /**
     * Gets the tier classifier.
     */
    @Nonnull
    public ContainerTierClassifier getTierClassifier() {
        return tierClassifier;
    }

    /**
     * Gets the loot generator.
     */
    @Nonnull
    public ContainerLootGenerator getLootGenerator() {
        return lootGenerator;
    }

    /**
     * Gets the replacer.
     */
    @Nonnull
    public ContainerLootReplacer getReplacer() {
        return replacer;
    }

    /**
     * Checks if the system is enabled.
     *
     * @return true if the system is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets whether the system is enabled.
     *
     * @param enabled true to enable, false to disable
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
