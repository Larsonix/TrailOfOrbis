package io.github.larsonix.trailoforbis.mobs;

import com.hypixel.hytale.logger.HytaleLogger;
import java.util.logging.Level;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.AllNPCsLoadedEvent;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.config.ConfigManager;
import io.github.larsonix.trailoforbis.leveling.api.LevelingService;
import io.github.larsonix.trailoforbis.mobs.calculator.DistanceBonusCalculator;
import io.github.larsonix.trailoforbis.mobs.calculator.PlayerLevelCalculator;
import io.github.larsonix.trailoforbis.mobs.elemental.MobElementConfig;
import io.github.larsonix.trailoforbis.mobs.elemental.MobElementResolver;
import io.github.larsonix.trailoforbis.mobs.classification.DynamicEntityRegistry;
import io.github.larsonix.trailoforbis.mobs.classification.EntityDiscoveryConfig;
import io.github.larsonix.trailoforbis.mobs.classification.MobClassificationContext;
import io.github.larsonix.trailoforbis.mobs.classification.MobClassificationService;
import io.github.larsonix.trailoforbis.mobs.classification.RPGMobClass;
import io.github.larsonix.trailoforbis.mobs.classification.provider.HytaleTagLookupProvider;
import io.github.larsonix.trailoforbis.mobs.component.MobScalingComponent;
import io.github.larsonix.trailoforbis.mobs.model.MobStats;
import io.github.larsonix.trailoforbis.mobs.spawn.config.MobSpawnConfig;
import io.github.larsonix.trailoforbis.mobs.spawn.manager.RPGSpawnManager;
import io.github.larsonix.trailoforbis.mobs.speed.MobSpeedEffectManager;
import io.github.larsonix.trailoforbis.mobs.stats.MobStatGenerator;
import io.github.larsonix.trailoforbis.mobs.stats.MobStatPoolConfig;
import io.github.larsonix.trailoforbis.mobs.systems.MobScalingSystem;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Main manager for the mob scaling system.
 *
 * <p>Implements {@link MobScalingService} and coordinates all mob scaling components:
 * <ul>
 *   <li>{@link DistanceBonusCalculator} - Distance-based bonus pool</li>
 *   <li>{@link PlayerLevelCalculator} - Player-based mob level</li>
 *   <li>{@link MobStatGenerator} - Dirichlet-based stat distribution</li>
 *   <li>{@link MobScalingSystem} - ECS spawn interceptor</li>
 * </ul>
 *
 * <p>Register this manager in the plugin's {@code setup()} and {@code start()} methods.
 *
 * @see MobScalingService
 */
public class MobScalingManager implements MobScalingService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final TrailOfOrbis plugin;
    private final ConfigManager configManager;
    private final LevelingService levelingService;

    // Sub-components (initialized in initialize())
    private MobScalingConfig config;
    private DistanceBonusCalculator distanceCalculator;
    private PlayerLevelCalculator playerLevelCalculator;
    private MobStatGenerator statGenerator;
    private MobElementResolver elementResolver;
    private MobClassificationService classificationService;
    private DynamicEntityRegistry dynamicEntityRegistry;
    private MobScalingSystem scalingSystem;
    private MobSpeedEffectManager speedEffectManager;

    // RPG spawn manager (replaces old spawn multiplier components)
    private RPGSpawnManager rpgSpawnManager;

    // Cached component type
    private ComponentType<EntityStore, MobScalingComponent> scalingComponentType;

    private boolean initialized = false;

    /**
     * Creates a new mob scaling manager.
     *
     * @param plugin          The plugin instance
     * @param configManager   Configuration manager
     * @param levelingService LevelingService for player levels
     */
    public MobScalingManager(
            @Nonnull TrailOfOrbis plugin,
            @Nonnull ConfigManager configManager,
            @Nullable LevelingService levelingService) {

        this.plugin = plugin;
        this.configManager = configManager;
        this.levelingService = levelingService;
    }

    /**
     * Initializes the mob scaling system.
     *
     * <p>Creates all calculators and detectors. Call this in {@code start()}.
     *
     * @return true if initialization succeeded
     */
    public boolean initialize() {
        try {
            // Get config
            config = configManager.getMobScalingConfig();
            if (!config.isEnabled()) {
                LOGGER.at(Level.INFO).log("Mob scaling is disabled in config");
                initialized = true;
                return true;
            }

            // Get component type from plugin
            scalingComponentType = plugin.getMobScalingComponentType();

            // Create element resolver for elemental affinity detection
            MobElementConfig elementConfig = configManager.getMobElementConfig();
            elementResolver = elementConfig != null ? new MobElementResolver(elementConfig) : null;

            // Create calculators
            distanceCalculator = new DistanceBonusCalculator(config);
            playerLevelCalculator = new PlayerLevelCalculator(levelingService, config, distanceCalculator);
            // Use standalone MobStatPoolConfig from ConfigManager (loaded from mob-stat-pool.yml)
            statGenerator = new MobStatGenerator(configManager.getMobStatPoolConfig());

            // Create tag lookup provider (shared between classification service and registry)
            HytaleTagLookupProvider tagLookupProvider = new HytaleTagLookupProvider();

            // Create classification service
            classificationService = new MobClassificationService(
                configManager.getMobClassificationConfig(),
                tagLookupProvider
            );

            // Initialize dynamic entity registry for automatic mod compatibility
            EntityDiscoveryConfig discoveryConfig = configManager.getEntityDiscoveryConfig();
            if (discoveryConfig != null && discoveryConfig.getDiscovery().isEnabled()) {
                dynamicEntityRegistry = new DynamicEntityRegistry(discoveryConfig, tagLookupProvider);
                classificationService.setRegistry(dynamicEntityRegistry);

                // Register handler for AllNPCsLoadedEvent in case it fires later
                plugin.getEventRegistry().register(AllNPCsLoadedEvent.class, event -> {
                    runEntityDiscovery("AllNPCsLoadedEvent");
                });

                // Check if NPCs are already loaded (AllNPCsLoadedEvent is a one-shot event
                // that fires during asset loading, which may complete BEFORE our plugin registers).
                // If roles are already available, run discovery eagerly.
                var existingRoles = NPCPlugin.get() != null ? NPCPlugin.get().getRoleTemplateNames(false) : null;
                if (existingRoles != null && !existingRoles.isEmpty()) {
                    LOGGER.at(Level.INFO).log("NPCs already loaded (%d roles), running discovery eagerly", existingRoles.size());
                    runEntityDiscovery("eager (NPCs pre-loaded)");
                } else {
                    LOGGER.at(Level.INFO).log("Dynamic entity discovery scheduled (will run after NPC assets load)");
                }
            } else {
                LOGGER.at(Level.INFO).log("Dynamic entity discovery disabled, using static classification only");
            }

            // Create and initialize speed effect manager
            speedEffectManager = new MobSpeedEffectManager(plugin);
            speedEffectManager.initialize();

            // Initialize RPG spawn manager (replaces old spawn multiplier system)
            MobSpawnConfig spawnConfig = configManager.getMobSpawnConfig();
            if (spawnConfig != null && spawnConfig.isEnabled()) {
                rpgSpawnManager = new RPGSpawnManager(plugin, spawnConfig, levelingService);
                LOGGER.at(Level.INFO).log("RPG spawn manager initialized (preset: %s, level_scaling: %s)",
                    spawnConfig.getPreset(),
                    spawnConfig.getLevelScaling().isEnabled() ? "enabled" : "disabled");
            }

            LOGGER.at(Level.INFO).log("Mob scaling manager initialized");
            initialized = true;
            return true;

        } catch (Exception e) {
            LOGGER.at(Level.SEVERE).withCause(e).log("Failed to initialize mob scaling");
            return false;
        }
    }

    /**
     * Runs entity discovery and logs the results.
     *
     * @param trigger Description of what triggered the discovery (for logging)
     */
    private void runEntityDiscovery(@Nonnull String trigger) {
        if (dynamicEntityRegistry == null) return;
        int discovered = dynamicEntityRegistry.discoverRoles();
        LOGGER.at(Level.INFO).log("Dynamic entity registry populated via %s: %d roles (BOSS=%d, ELITE=%d, HOSTILE=%d, PASSIVE=%d)",
            trigger,
            discovered,
            dynamicEntityRegistry.getStatistics().countByClass().getOrDefault(RPGMobClass.BOSS, 0),
            dynamicEntityRegistry.getStatistics().countByClass().getOrDefault(RPGMobClass.ELITE, 0),
            dynamicEntityRegistry.getStatistics().countByClass().getOrDefault(RPGMobClass.HOSTILE, 0),
            dynamicEntityRegistry.getStatistics().countByClass().getOrDefault(RPGMobClass.PASSIVE, 0));
    }

    /**
     * Creates and returns the ECS system for registration.
     *
     * <p>Call this in {@code setup()} after creating the manager.
     * The system now gets its config/calculators dynamically from this manager,
     * so it can be registered before config is loaded.
     *
     * @return The MobScalingSystem to register with getEntityStoreRegistry()
     */
    @Nonnull
    public MobScalingSystem createSystem() {
        // System gets config/calculators dynamically from manager
        scalingSystem = new MobScalingSystem(plugin);
        return scalingSystem;
    }

    /**
     * Refreshes configuration after config reload.
     *
     * <p>Re-creates calculators with updated config values.
     */
    public void refreshConfig() {
        if (!initialized) return;

        config = configManager.getMobScalingConfig();

        // Refresh element resolver
        MobElementConfig elementConfig = configManager.getMobElementConfig();
        elementResolver = elementConfig != null ? new MobElementResolver(elementConfig) : null;

        distanceCalculator = new DistanceBonusCalculator(config);
        playerLevelCalculator = new PlayerLevelCalculator(levelingService, config, distanceCalculator);
        // Use standalone MobStatPoolConfig from ConfigManager (loaded from mob-stat-pool.yml)
        statGenerator = new MobStatGenerator(configManager.getMobStatPoolConfig());

        // Create tag lookup provider (shared between classification service and registry)
        HytaleTagLookupProvider tagLookupProvider = new HytaleTagLookupProvider();

        classificationService = new MobClassificationService(
            configManager.getMobClassificationConfig(),
            tagLookupProvider
        );

        // Refresh dynamic entity registry
        EntityDiscoveryConfig discoveryConfig = configManager.getEntityDiscoveryConfig();
        if (discoveryConfig != null && discoveryConfig.getDiscovery().isEnabled()) {
            dynamicEntityRegistry = new DynamicEntityRegistry(discoveryConfig, tagLookupProvider);
            int discovered = dynamicEntityRegistry.discoverRoles();
            classificationService.setRegistry(dynamicEntityRegistry);
            LOGGER.at(Level.INFO).log("Dynamic entity registry refreshed with %d roles", discovered);
        } else {
            dynamicEntityRegistry = null;
        }

        // Refresh RPG spawn manager
        MobSpawnConfig spawnConfig = configManager.getMobSpawnConfig();
        if (spawnConfig != null && spawnConfig.isEnabled()) {
            rpgSpawnManager = new RPGSpawnManager(plugin, spawnConfig, levelingService);
        } else {
            rpgSpawnManager = null;
        }

        LOGGER.at(Level.INFO).log("Mob scaling configuration refreshed");
    }

    // ==================== MobScalingService Implementation ====================

    @Override
    @Nullable
    public MobStats getMobStats(@Nonnull Ref<EntityStore> mobRef, @Nonnull ComponentAccessor<EntityStore> accessor) {
        MobScalingComponent component = getScalingComponent(mobRef, accessor);
        return component != null ? component.getStats() : null;
    }

    @Override
    @Nullable
    public ComputedStats getMobComputedStats(@Nonnull Ref<EntityStore> mobRef, @Nonnull ComponentAccessor<EntityStore> accessor) {
        MobStats stats = getMobStats(mobRef, accessor);
        return stats != null ? stats.toComputedStats() : null;
    }

    @Override
    public boolean isScaledMob(@Nonnull Ref<EntityStore> entityRef, @Nonnull ComponentAccessor<EntityStore> accessor) {
        return getScalingComponent(entityRef, accessor) != null;
    }

        @Override
        public boolean isScalableMob(@Nonnull Ref<EntityStore> entityRef, @Nonnull ComponentAccessor<EntityStore> accessor) {
            if (classificationService == null) return false;
    
            NPCEntity npc = accessor.getComponent(entityRef, NPCEntity.getComponentType());
            if (npc == null) return false;
    
            RPGMobClass cls = classificationService.classify(createContext(npc));
            return cls != RPGMobClass.PASSIVE;
        }
        @Override
    @Nullable
    public MobScalingComponent getScalingComponent(@Nonnull Ref<EntityStore> mobRef, @Nonnull ComponentAccessor<EntityStore> accessor) {
        if (!mobRef.isValid() || scalingComponentType == null) {
            return null;
        }
        return accessor.getComponent(mobRef, scalingComponentType);
    }

    @Override
    public int getMobLevel(@Nonnull Ref<EntityStore> mobRef, @Nonnull ComponentAccessor<EntityStore> accessor) {
        MobScalingComponent component = getScalingComponent(mobRef, accessor);
        return component != null ? component.getMobLevel() : 1;
    }

    @Override
    public boolean isEnabled() {
        return config != null && config.isEnabled();
    }

    // ==================== Getters for Sub-Components ====================

    @Nullable
    public DistanceBonusCalculator getDistanceCalculator() {
        return distanceCalculator;
    }

    @Nullable
    public PlayerLevelCalculator getPlayerLevelCalculator() {
        return playerLevelCalculator;
    }

    @Nullable
    public MobStatGenerator getStatGenerator() {
        return statGenerator;
    }

    /**
     * Determines mob elemental damage based on NPCGroup membership
     * and role name keywords.
     */
    @Nullable
    public MobElementResolver getElementResolver() {
        return elementResolver;
    }

    @Nonnull
    public MobClassificationContext createContext(@Nonnull NPCEntity npc) {
        return new MobClassificationContext(
            npc.getRoleName(),
            npc.getRole() != null ? npc.getRole().getRoleIndex() : -1,
            npc.getRole() != null ? npc.getRole().getWorldSupport().getDefaultPlayerAttitude() : null
        );
    }

    @Nullable
    public MobClassificationService getClassificationService() {
        return classificationService;
    }

    /**
     * Discovers and classifies all NPC roles at startup,
     * providing O(1) classification lookups.
     *
     * @return null if discovery is disabled or not initialized
     */
    @Nullable
    public DynamicEntityRegistry getDynamicEntityRegistry() {
        return dynamicEntityRegistry;
    }

    @Nullable
    public MobSpeedEffectManager getSpeedEffectManager() {
        return speedEffectManager;
    }

    /**
     * Handles class-based and level-based spawn rate modifications.
     *
     * @return null if not enabled/initialized
     */
    @Nullable
    public RPGSpawnManager getRPGSpawnManager() {
        return rpgSpawnManager;
    }

    @Nonnull
    public MobScalingConfig getConfig() {
        return config != null ? config : new MobScalingConfig();
    }

    @Nonnull
    public MobStatPoolConfig getStatPoolConfig() {
        return configManager.getMobStatPoolConfig();
    }

    /**
     * Checks if the manager is initialized.
     *
     * @return true if initialize() was called successfully
     */
    public boolean isInitialized() {
        return initialized;
    }
}
