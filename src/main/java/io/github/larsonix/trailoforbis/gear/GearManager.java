package io.github.larsonix.trailoforbis.gear;

import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.api.ServiceRegistry;
import io.github.larsonix.trailoforbis.attributes.AttributeManager;
import io.github.larsonix.trailoforbis.attributes.AttributeType;
import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig;
import io.github.larsonix.trailoforbis.gear.config.GearConfigLoader;
import io.github.larsonix.trailoforbis.gear.config.ModifierConfig;
import io.github.larsonix.trailoforbis.gear.conversion.ChestLootConversionListener;
import io.github.larsonix.trailoforbis.gear.conversion.VanillaConversionConfig;
import io.github.larsonix.trailoforbis.gear.systems.CraftingConversionSystem;
import io.github.larsonix.trailoforbis.gear.conversion.VanillaItemConverter;
import io.github.larsonix.trailoforbis.gear.equipment.EquipmentFeedback;
import io.github.larsonix.trailoforbis.gear.equipment.EquipmentListener;
import io.github.larsonix.trailoforbis.gear.equipment.EquipmentValidator;
import io.github.larsonix.trailoforbis.gear.equipment.EquipmentValidator.ValidationResult;
import io.github.larsonix.trailoforbis.gear.equipment.RequirementCalculator;
import io.github.larsonix.trailoforbis.gear.generation.GearGenerator;
import io.github.larsonix.trailoforbis.gear.loot.DropLevelBlender;
import io.github.larsonix.trailoforbis.gear.loot.DynamicLootRegistry;
import io.github.larsonix.trailoforbis.gear.loot.LootCalculator;
import io.github.larsonix.trailoforbis.gear.loot.LootDiscoveryConfig;
import io.github.larsonix.trailoforbis.gear.loot.LootGenerator;
import io.github.larsonix.trailoforbis.gear.loot.LootListener;
import io.github.larsonix.trailoforbis.gear.loot.LootSettings;
import io.github.larsonix.trailoforbis.gear.loot.RarityBonusCalculator;
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.gear.reskin.ReskinDataPreserver;
import io.github.larsonix.trailoforbis.gear.reskin.ReskinRecipeGenerator;
import io.github.larsonix.trailoforbis.gear.reskin.ReskinResourceTypeRegistry;
import io.github.larsonix.trailoforbis.gear.stats.GearBonusProvider;
import io.github.larsonix.trailoforbis.gear.stats.GearStatApplier;
import io.github.larsonix.trailoforbis.gear.stats.GearStatCalculator;
import io.github.larsonix.trailoforbis.gear.stats.GearStatCalculator.GearBonuses;
import io.github.larsonix.trailoforbis.gear.tooltip.ItemNameFormatter;
import io.github.larsonix.trailoforbis.gear.tooltip.RichTooltipFormatter;
import io.github.larsonix.trailoforbis.gear.tooltip.TooltipConfig;
import io.github.larsonix.trailoforbis.gear.item.CustomItemData;
import io.github.larsonix.trailoforbis.gear.item.CustomItemDefinitionBuilder;
import io.github.larsonix.trailoforbis.gear.item.CustomItemSyncService;
import io.github.larsonix.trailoforbis.gear.item.ItemDefinitionBuilder;
import io.github.larsonix.trailoforbis.gear.item.ItemDisplayNameService;
import io.github.larsonix.trailoforbis.gear.item.ItemRegistryService;
import io.github.larsonix.trailoforbis.gear.item.ItemSyncConfig;
import io.github.larsonix.trailoforbis.gear.item.ItemSyncService;
import io.github.larsonix.trailoforbis.gear.item.ItemWorldSyncService;
import io.github.larsonix.trailoforbis.gear.item.TranslationSyncService;
import io.github.larsonix.trailoforbis.gear.sync.ItemSyncCoordinator;
import io.github.larsonix.trailoforbis.gear.notification.PickupNotificationService;
import io.github.larsonix.trailoforbis.gear.util.GearUtils;
import io.github.larsonix.trailoforbis.gear.vanilla.VanillaWeaponDiscovery;
import io.github.larsonix.trailoforbis.gear.vanilla.VanillaWeaponProfile;
import io.github.larsonix.trailoforbis.leveling.api.LevelingService;
import io.github.larsonix.trailoforbis.loot.container.ContainerLootConfig;
import io.github.larsonix.trailoforbis.loot.container.ContainerLootSystem;
import io.github.larsonix.trailoforbis.maps.core.RealmMapData;
import io.github.larsonix.trailoforbis.maps.items.RealmMapGenerator;
import io.github.larsonix.trailoforbis.maps.items.RealmMapUtils;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import io.github.larsonix.trailoforbis.util.PlayerWorldCache;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Main orchestrator for the RPG Gear System.
 *
 * <p>Manages initialization, lifecycle, and coordination of all gear subsystems:
 * <ul>
 *   <li>Config loading</li>
 *   <li>Gear generation</li>
 *   <li>Equipment validation</li>
 *   <li>Stat calculation</li>
 *   <li>Tooltip formatting</li>
 *   <li>Loot generation</li>
 * </ul>
 *
 * <p>Implements {@link GearService} as the public API.
 */
public final class GearManager implements GearService {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final TrailOfOrbis plugin;
    private final Path configDirectory;

    // Config
    private GearConfigLoader configLoader;
    private GearBalanceConfig balanceConfig;
    private ModifierConfig modifierConfig;

    // Core components
    private GearGenerator gearGenerator;
    private RequirementCalculator requirementCalculator;
    private EquipmentValidator equipmentValidator;
    private EquipmentListener equipmentListener;
    private GearStatCalculator statCalculator;
    private GearStatApplier statApplier;

    // Rich tooltip components (Message API)
    private TooltipConfig tooltipConfig;
    private RichTooltipFormatter richTooltipFormatter;
    private ItemNameFormatter itemNameFormatter;

    // Item display name service (centralized naming)
    private ItemDisplayNameService itemDisplayNameService;

    // Item sync system
    private ItemRegistryService itemRegistryService;
    private ItemDefinitionBuilder itemDefinitionBuilder;
    private TranslationSyncService translationSyncService;
    private ItemSyncService itemSyncService;

    // Custom item sync (maps, stones)
    private CustomItemDefinitionBuilder customItemDefinitionBuilder;
    private CustomItemSyncService customItemSyncService;

    // World-level item sync (for pickup notification fix)
    private ItemWorldSyncService itemWorldSyncService;

    // Sync coordinator (coalesces dirty-marking into minimal packet bursts)
    private ItemSyncCoordinator syncCoordinator;

    // Unified pickup notification service
    private PickupNotificationService pickupNotificationService;

    // Loot system
    private LootSettings lootSettings;
    private RarityBonusCalculator rarityBonusCalculator;
    private LootCalculator lootCalculator;
    private LootGenerator lootGenerator;
    private LootListener lootListener;
    private DynamicLootRegistry dynamicLootRegistry;
    private DropLevelBlender dropLevelBlender;

    // Vanilla item conversion system
    private VanillaItemConverter vanillaItemConverter;
    private CraftingConversionSystem craftingConversionSystem;
    private ChestLootConversionListener chestLootConversionListener;

    // Container loot replacement system
    private ContainerLootSystem containerLootSystem;

    // Vanilla weapon attack profiles (for attack effectiveness calculation)
    private VanillaWeaponDiscovery vanillaWeaponDiscovery;

    // Reskin system (Builder's Workbench skin changing with RPG data preservation)
    private ReskinResourceTypeRegistry reskinResourceTypeRegistry;
    private ReskinDataPreserver reskinDataPreserver;

    // Requirement bypass (Creative mode)
    private final Set<UUID> requirementBypassPlayers = ConcurrentHashMap.newKeySet();

    // State
    private boolean initialized = false;
    private boolean craftingConversionPending = false;  // Deferred until LevelingService available

    /**
     * Creates a GearManager.
     *
     * @param plugin The plugin instance
     * @param configDirectory The directory containing gear config files
     */
    public GearManager(@Nonnull TrailOfOrbis plugin, @Nonnull Path configDirectory) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.configDirectory = Objects.requireNonNull(configDirectory, "configDirectory cannot be null");
    }

    /**
     * Creates a GearManager with default config directory.
     *
     * @param plugin The plugin instance
     */
    public GearManager(@Nonnull TrailOfOrbis plugin) {
        this(plugin, Path.of("config"));
    }

    // =========================================================================
    // LIFECYCLE
    // =========================================================================

    /**
     * Initializes the gear system.
     *
     * <p>Performs:
     * <ol>
     *   <li>Load configs from files</li>
     *   <li>Initialize all subsystems</li>
     *   <li>Register with ServiceRegistry</li>
     * </ol>
     *
     * @throws GearInitializationException if initialization fails
     */
    public void initialize() {
        if (initialized) {
            LOGGER.at(Level.WARNING).log("GearManager already initialized");
            return;
        }

        LOGGER.at(Level.INFO).log("Initializing Gear System...");

        try {
            // Load configs
            loadConfigs();

            // Initialize core components
            initializeCoreComponents();

            // Initialize loot system
            initializeLootSystem();

            // Register as service
            ServiceRegistry.register(GearService.class, this);

            initialized = true;
            LOGGER.at(Level.INFO).log("Gear System initialized successfully");

        } catch (Exception e) {
            throw new GearInitializationException("Failed to initialize gear system", e);
        }
    }

    /**
     * Registers deferred systems that require services not available at initial init time.
     *
     * <p>This method should be called after all core services (LevelingService, RealmsManager)
     * are initialized. It completes the registration of:
     * <ul>
     *   <li>CraftingConversionSystem (requires LevelingService)</li>
     * </ul>
     *
     * <p>Note: RealmMapGenerator uses lazy lookup in ContainerLootGenerator, so it doesn't
     * need explicit deferred registration.
     */
    public void registerDeferredSystems() {
        if (!initialized) {
            LOGGER.at(Level.WARNING).log("Cannot register deferred systems - GearManager not initialized");
            return;
        }

        // Register crafting conversion system if it was deferred
        if (craftingConversionPending && vanillaItemConverter != null) {
            ServiceRegistry.get(LevelingService.class).ifPresentOrElse(
                levelingService -> {
                    craftingConversionSystem = new CraftingConversionSystem(
                        vanillaItemConverter,
                        levelingService
                    );
                    plugin.getEntityStoreRegistry().registerSystem(craftingConversionSystem);
                    craftingConversionPending = false;
                    LOGGER.at(Level.INFO).log("Registered deferred crafting conversion ECS system");
                },
                () -> LOGGER.at(Level.WARNING).log(
                    "LevelingService still not available - crafting conversion will not work")
            );
        }
    }

    /**
     * Shuts down the gear system.
     *
     * <p>Cleans up resources and unregisters from ServiceRegistry.
     */
    public void shutdown() {
        if (!initialized) {
            return;
        }

        LOGGER.at(Level.INFO).log("Shutting down Gear System...");

        // Unregister service
        ServiceRegistry.unregister(GearService.class);

        // Clear bypass set
        requirementBypassPlayers.clear();

        // Shutdown sync coordinator
        if (syncCoordinator != null) {
            syncCoordinator.shutdown();
            syncCoordinator = null;
        }

        // Shutdown item registry service (cleans up all registered custom items)
        if (itemRegistryService != null) {
            itemRegistryService.shutdown();
            itemRegistryService = null;
        }

        // Shutdown item world sync service
        if (itemWorldSyncService != null) {
            itemWorldSyncService.shutdown();
            itemWorldSyncService = null;
        }

        // Clear references (allow GC)
        gearGenerator = null;
        requirementCalculator = null;
        equipmentValidator = null;
        statCalculator = null;
        statApplier = null;
        tooltipConfig = null;
        richTooltipFormatter = null;
        itemNameFormatter = null;
        itemDisplayNameService = null;
        itemDefinitionBuilder = null;
        translationSyncService = null;
        itemSyncService = null;
        customItemDefinitionBuilder = null;
        customItemSyncService = null;
        pickupNotificationService = null;
        lootSettings = null;
        lootCalculator = null;
        lootGenerator = null;
        lootListener = null;
        dynamicLootRegistry = null;
        vanillaItemConverter = null;
        craftingConversionSystem = null;
        chestLootConversionListener = null;
        if (containerLootSystem != null) {
            containerLootSystem.shutdown();
            containerLootSystem = null;
        }
        balanceConfig = null;
        modifierConfig = null;
        configLoader = null;

        initialized = false;
        LOGGER.at(Level.INFO).log("Gear System shut down");
    }

    /**
     * Reloads configuration from files.
     *
     * <p>Updates all subsystems with new configuration.
     *
     * @throws GearInitializationException if reload fails
     */
    public void reload() {
        if (!initialized) {
            throw new IllegalStateException("GearManager not initialized");
        }

        LOGGER.at(Level.INFO).log("Reloading Gear System configuration...");

        try {
            // Reload configs
            loadConfigs();

            // Reinitialize components with new configs
            initializeCoreComponents();
            initializeLootSystem();

            LOGGER.at(Level.INFO).log("Gear System configuration reloaded");

        } catch (Exception e) {
            throw new GearInitializationException("Failed to reload gear configuration", e);
        }
    }

    // =========================================================================
    // INITIALIZATION HELPERS
    // =========================================================================

    private void loadConfigs() {
        configLoader = new GearConfigLoader(configDirectory);
        configLoader.loadAll();
        balanceConfig = configLoader.getBalanceConfig();
        modifierConfig = configLoader.getModifierConfig();
    }

    private void initializeCoreComponents() {
        // Get AttributeManager from ServiceRegistry
        AttributeManager attributeManager = ServiceRegistry.get(AttributeManager.class)
                .orElseThrow(() -> new GearInitializationException(
                        "AttributeManager not registered. Ensure AttributeManager is initialized before GearManager."));

        // Item registry service - must be initialized before GearGenerator
        // This allows custom item IDs to be recognized by Hytale's server-side validation
        // Persistence enabled: loads cached item registrations from DB on startup
        if (itemRegistryService == null) {
            itemRegistryService = new ItemRegistryService();
        }
        // Initialize with DataManager for database persistence of item registrations
        // This ensures items don't show as "?" after server restarts
        itemRegistryService.initialize(plugin.getDataManager());

        // Gear generation (now with item registry for server-side validation)
        gearGenerator = new GearGenerator(balanceConfig, modifierConfig,
                configLoader.getEquipmentStatConfig(), itemRegistryService);

        // Requirements
        requirementCalculator = new RequirementCalculator(balanceConfig, modifierConfig);

        // Equipment validation (with Creative mode bypass predicate)
        boolean bypassEnabled = plugin.getConfigManager().getRPGConfig().isCreativeModeBypassRequirements();
        equipmentValidator = new EquipmentValidator(
                requirementCalculator, attributeManager,
                bypassEnabled ? requirementBypassPlayers::contains : uuid -> false);

        // Equipment listener (armor slot filters + requirement validation)
        equipmentListener = new EquipmentListener(equipmentValidator, new EquipmentFeedback());

        // Stat calculation (with ItemRegistryService for vanilla item ID lookup)
        statCalculator = new GearStatCalculator(balanceConfig, equipmentValidator, itemRegistryService);

        // Stat application
        statApplier = new GearStatApplier();

        // Vanilla weapon discovery - enumerates all weapon attacks from Hytale's asset map
        // This enables attack effectiveness calculation using geometric mean reference
        vanillaWeaponDiscovery = new VanillaWeaponDiscovery(balanceConfig.vanillaWeaponProfiles());
        vanillaWeaponDiscovery.discoverAll();

        // Rich tooltip formatting (Message API)
        tooltipConfig = configLoader.getTooltipConfig();
        richTooltipFormatter = new RichTooltipFormatter(
                modifierConfig, requirementCalculator, attributeManager, tooltipConfig);
        itemNameFormatter = new ItemNameFormatter(
                modifierConfig,
                tooltipConfig.includePrefix(),
                tooltipConfig.includeSuffix(),
                tooltipConfig.boldThreshold());

        // Item display name service (centralized naming for UIs and notifications)
        itemDisplayNameService = new ItemDisplayNameService(modifierConfig);

        // Item sync system
        // IMPORTANT: Pass itemDisplayNameService for consistent naming in native pickup UI
        itemDefinitionBuilder = new ItemDefinitionBuilder(
                modifierConfig, richTooltipFormatter, itemNameFormatter, itemDisplayNameService);
        translationSyncService = new TranslationSyncService();
        itemSyncService = new ItemSyncService(
                itemDefinitionBuilder, translationSyncService, ItemSyncConfig.defaults());

        // Wire stats version provider so tooltips update when player stats change
        itemSyncService.setStatsVersionProvider(attributeManager::getStatsVersion);

        // Register tooltip refresh callback on AttributeManager
        // This triggers tooltip resync whenever stats are recalculated
        registerTooltipRefreshCallback(attributeManager);

        // Custom item sync (maps, stones)
        // Pass ItemRegistryService so custom items are registered server-side when synced
        // This ensures drop/move/use operations work for stones and realm maps
        // IMPORTANT: Pass itemDisplayNameService for consistent naming in native pickup UI
        customItemDefinitionBuilder = new CustomItemDefinitionBuilder(itemDisplayNameService);
        customItemSyncService = new CustomItemSyncService(
                customItemDefinitionBuilder, translationSyncService, itemRegistryService);

        // Sync coordinator — coalesces dirty-marking into minimal packet bursts.
        // Created after both sync services so it can delegate to them.
        syncCoordinator = new ItemSyncCoordinator(itemSyncService);

        // Suppress sync during world transitions to prevent packet flood
        // when client is processing JoinWorldPacket.
        plugin.getEventRegistry().registerGlobal(
            com.hypixel.hytale.event.EventPriority.NORMAL,
            com.hypixel.hytale.server.core.event.events.player.DrainPlayerFromWorldEvent.class,
            event -> {
                try {
                    var uuidComp = event.getHolder().getComponent(
                        com.hypixel.hytale.server.core.entity.UUIDComponent.getComponentType());
                    if (uuidComp != null) {
                        syncCoordinator.suppressPlayer(uuidComp.getUuid());
                    }
                } catch (Exception e) {
                    // Holder may be invalid during edge cases — suppress silently
                }
            });

        // Unified pickup notification service
        // Handles both item sync and chat notifications for all item types
        pickupNotificationService = new PickupNotificationService(
                itemDisplayNameService, itemSyncService, customItemSyncService, richTooltipFormatter);

        // World-level item sync (for pickup notification fix)
        // This syncs custom items to ALL nearby players when items spawn,
        // ensuring clients have definitions before notifyPickupItem() fires
        // CRITICAL: Pass itemRegistryService for server-side registration
        itemWorldSyncService = new ItemWorldSyncService(
            itemSyncService, customItemSyncService, itemRegistryService);

        // Register level-up listener for tooltip resync
        // When a player levels up, requirement colors may change (red -> green)
        registerLevelUpListener();
    }

    /**
     * Registers a listener to resync gear tooltips when players level up.
     *
     * <p>This ensures requirement text colors update in real-time when a player
     * gains levels (e.g., red "Requires Level 10" turns green when they reach level 10).
     */
    private void registerLevelUpListener() {
        ServiceRegistry.get(LevelingService.class).ifPresent(levelingSvc -> {
            levelingSvc.registerLevelUpListener((playerId, newLevel, oldLevel, totalXp) -> {
                // Mark stats dirty — coordinator will flush once, only changed tooltips
                if (syncCoordinator != null) {
                    syncCoordinator.markStatsDirty(playerId);
                }
            });
            LOGGER.at(Level.INFO).log("Registered gear tooltip resync listener for level-ups");
        });
    }

    /**
     * Registers the tooltip refresh callback on AttributeManager.
     *
     * <p>This callback is invoked after every stat recalculation, triggering
     * a tooltip resync for the player. This ensures requirement colors (green/red)
     * update in real-time when players:
     * <ul>
     *   <li>Allocate attribute points</li>
     *   <li>Respec attributes</li>
     *   <li>Allocate skill tree nodes that affect stats</li>
     *   <li>Equip/unequip gear that affects stats</li>
     * </ul>
     *
     * @param attributeManager The attribute manager to register with
     */
    private void registerTooltipRefreshCallback(@Nonnull AttributeManager attributeManager) {
        attributeManager.setTooltipRefreshCallback(playerId -> {
            // Mark stats dirty — coordinator coalesces into one flush per event burst
            if (syncCoordinator != null) {
                syncCoordinator.markStatsDirty(playerId);
            }
        });
        LOGGER.at(Level.INFO).log("Registered tooltip refresh callback - gear requirements will update on stat changes");
    }

    private void initializeLootSystem() {
        // Get AttributeManager
        AttributeManager attributeManager = ServiceRegistry.require(AttributeManager.class);

        // Loot settings
        lootSettings = new LootSettings(balanceConfig);

        // Drop level blender
        dropLevelBlender = new DropLevelBlender(balanceConfig.levelBlending());

        // Shared rarity bonus calculator (WIND → rarity %)
        rarityBonusCalculator = new RarityBonusCalculator(attributeManager, lootSettings.getLuckToRarityPercent());

        // Loot calculator
        lootCalculator = new LootCalculator(lootSettings, rarityBonusCalculator, dropLevelBlender);

        // Initialize dynamic loot registry (with Hexcode item config when Hexcode is loaded)
        LootDiscoveryConfig discoveryConfig = plugin.getConfigManager().getLootDiscoveryConfig();
        io.github.larsonix.trailoforbis.compat.HexcodeItemConfig hexcodeItemConfig =
                io.github.larsonix.trailoforbis.compat.HexcodeCompat.isLoaded()
                        ? plugin.getConfigManager().getHexcodeItemConfig()
                        : null;
        dynamicLootRegistry = new DynamicLootRegistry(discoveryConfig, hexcodeItemConfig);

        // Discover items from Hytale's registry (this scans all mods)
        dynamicLootRegistry.discoverItems();

        // Initialize reskin system — generates StructuralCrafting recipes for Builder's Workbench
        initializeReskinSystem();

        // Loot generator using dynamic registry
        lootGenerator = new LootGenerator(gearGenerator, dynamicLootRegistry);

        // Loot listener
        lootListener = new LootListener(plugin, lootCalculator, lootGenerator);

        // Initialize vanilla item conversion system
        initializeVanillaConversion();
    }

    /**
     * Initializes the vanilla item conversion system.
     *
     * <p>Creates the VanillaItemConverter and registers conversion listeners
     * for crafting, chest loot, and mob drops.
     */
    private void initializeVanillaConversion() {
        // Get vanilla conversion config from ConfigManager
        VanillaConversionConfig conversionConfig = plugin.getConfigManager().getVanillaConversionConfig();

        if (!conversionConfig.isEnabled()) {
            LOGGER.at(Level.INFO).log("Vanilla item conversion is disabled");
            return;
        }

        // Create vanilla item converter
        vanillaItemConverter = new VanillaItemConverter(
            conversionConfig,
            gearGenerator,
            gearGenerator.getRarityRoller()
        );

        // Register ECS system for crafting conversion (may be deferred if LevelingService not yet available)
        if (vanillaItemConverter.isSourceEnabled(VanillaItemConverter.AcquisitionSource.CRAFTING)) {
            ServiceRegistry.get(io.github.larsonix.trailoforbis.leveling.api.LevelingService.class)
                .ifPresentOrElse(
                    levelingService -> {
                        // Create and register ECS event system for crafting conversion
                        craftingConversionSystem = new CraftingConversionSystem(
                            vanillaItemConverter,
                            levelingService
                        );
                        plugin.getEntityStoreRegistry().registerSystem(craftingConversionSystem);
                        LOGGER.at(Level.INFO).log("Registered crafting conversion ECS system");
                    },
                    () -> {
                        // LevelingService not yet available - mark for deferred registration
                        // This will be completed when registerDeferredSystems() is called
                        craftingConversionPending = true;
                        LOGGER.at(Level.INFO).log(
                            "Crafting conversion deferred (LevelingService will be available later)");
                    }
                );
        }

        // Create chest loot conversion listener
        chestLootConversionListener = new ChestLootConversionListener(
            vanillaItemConverter,
            plugin.getConfigManager().getMobScalingConfig()
        );
        chestLootConversionListener.register(plugin.getEventRegistry());

        // Initialize container loot system
        initializeContainerLootSystem(conversionConfig);

        LOGGER.at(Level.INFO).log("Vanilla item conversion initialized: mob_drops=%s, chest_loot=%s, crafting=%s",
            conversionConfig.getSources().isMobDrops() ? "enabled" : "disabled",
            conversionConfig.getSources().isChestLoot() ? "enabled" : "disabled",
            conversionConfig.getSources().isCrafting() ? "enabled" : "disabled"
        );
    }

    /**
     * Initializes the gear reskin system for the Builder's Workbench.
     *
     * <p>Generates StructuralCrafting recipes for all (slot, quality, category) groups
     * with 2+ items, and creates a {@link ReskinDataPreserver} for RPG data preservation
     * during workbench crafting.
     */
    private void initializeReskinSystem() {
        reskinResourceTypeRegistry = new ReskinResourceTypeRegistry();

        ReskinRecipeGenerator recipeGenerator = new ReskinRecipeGenerator(
                dynamicLootRegistry, reskinResourceTypeRegistry);
        int recipeCount = recipeGenerator.generate();

        // Wire the registry into ItemRegistryService so new RPG items get reskin ResourceTypes
        itemRegistryService.setReskinRegistry(reskinResourceTypeRegistry);

        // Create the data preserver (handler registered later in TrailOfOrbis.registerEcsSystems)
        reskinDataPreserver = new ReskinDataPreserver(itemRegistryService, itemSyncService);

        LOGGER.at(Level.INFO).log("Reskin system initialized: %d recipes, %d groups",
                recipeCount, reskinResourceTypeRegistry.size());
    }

    /**
     * Gets the reskin data preserver for handler registration.
     *
     * @return The preserver, or null if not initialized
     */
    public ReskinDataPreserver getReskinDataPreserver() {
        return reskinDataPreserver;
    }

    /**
     * Initializes the container loot replacement system.
     *
     * <p>This system replaces vanilla weapons/armor in containers with RPG gear.
     * The actual interception is handled by {@link ContainerLootInterceptor},
     * which is an ECS system registered separately in {@code TrailOfOrbis.setup()}.
     *
     * @param conversionConfig The vanilla conversion config for item classification
     */
    private void initializeContainerLootSystem(@Nonnull VanillaConversionConfig conversionConfig) {
        // Load container loot config from ConfigManager
        ContainerLootConfig containerLootConfig = plugin.getConfigManager().getContainerLootConfig();

        if (!containerLootConfig.isEnabled()) {
            LOGGER.at(Level.INFO).log("Container loot replacement system is disabled");
            return;
        }

        // Get realm map generator from RealmsManager (if available)
        RealmMapGenerator mapGenerator = null;
        try {
            var realmsManager = plugin.getRealmsManager();
            if (realmsManager != null) {
                mapGenerator = realmsManager.getMapGenerator();
            }
        } catch (Exception e) {
            LOGGER.at(Level.FINE).log("RealmsManager not available for container loot system");
        }

        if (mapGenerator == null) {
            // RealmMapGenerator not yet available - ContainerLootGenerator uses lazy lookup
            // at generation time, so this is fine. Map drops will work once RealmsManager initializes.
            LOGGER.at(Level.INFO).log(
                "RealmMapGenerator deferred (RealmsManager will be available later)");
        }

        // Create container loot system (coordinator only — ECS interceptor registered in TrailOfOrbis.setup())
        containerLootSystem = new ContainerLootSystem(
            containerLootConfig,
            lootGenerator,
            mapGenerator,
            conversionConfig,
            dropLevelBlender,
            rarityBonusCalculator
        );

        LOGGER.at(Level.INFO).log("Container loot replacement system initialized");
    }

    // =========================================================================
    // GEARSERVICE IMPLEMENTATION - DATA ACCESS
    // =========================================================================

    @Override
    public boolean isGear(@Nullable ItemStack item) {
        return GearUtils.isRpgGear(item);
    }

    @Override
    @Nonnull
    public Optional<GearData> getGearData(@Nullable ItemStack item) {
        return GearUtils.readGearData(item);
    }

    @Override
    @Nonnull
    public ItemStack setGearData(@Nonnull ItemStack item, @Nonnull GearData gearData) {
        return GearUtils.setGearData(item, gearData);
    }

    @Override
    @Nonnull
    public ItemStack removeGearData(@Nonnull ItemStack item) {
        return GearUtils.removeGearData(item);
    }

    // =========================================================================
    // GEARSERVICE IMPLEMENTATION - GENERATION
    // =========================================================================

    @Override
    @Nonnull
    public ItemStack generateGear(@Nonnull ItemStack baseItem, int itemLevel, @Nonnull String slot) {
        ensureInitialized();
        return gearGenerator.generate(baseItem, itemLevel, slot);
    }

    @Override
    @Nonnull
    public ItemStack generateGear(@Nonnull ItemStack baseItem, int itemLevel, @Nonnull String slot, @Nonnull GearRarity rarity) {
        ensureInitialized();
        return gearGenerator.generate(baseItem, itemLevel, slot, rarity);
    }

    @Override
    @Nonnull
    public ItemStack generateGear(@Nonnull ItemStack baseItem, int itemLevel, @Nonnull String slot, double rarityBonus) {
        ensureInitialized();
        return gearGenerator.generate(baseItem, itemLevel, slot, rarityBonus);
    }

    @Override
    @Nonnull
    public GearData generateGearData(int itemLevel, @Nonnull String slot, @Nonnull GearRarity rarity) {
        ensureInitialized();
        return gearGenerator.generateData(itemLevel, slot, rarity);
    }

    // =========================================================================
    // GEARSERVICE IMPLEMENTATION - VALIDATION
    // =========================================================================

    @Override
    public boolean canEquip(@Nonnull UUID playerId, @Nullable ItemStack item) {
        ensureInitialized();
        return equipmentValidator.canEquip(playerId, item);
    }

    @Override
    @Nonnull
    public ValidationResult checkRequirements(@Nonnull UUID playerId, @Nullable ItemStack item) {
        ensureInitialized();
        return equipmentValidator.checkRequirements(playerId, item);
    }

    @Override
    @Nonnull
    public Map<AttributeType, Integer> getRequirements(@Nullable ItemStack item) {
        ensureInitialized();
        return equipmentValidator.getRequirements(item);
    }

    // =========================================================================
    // GEARSERVICE IMPLEMENTATION - REQUIREMENT BYPASS
    // =========================================================================

    @Override
    public void addRequirementBypass(@Nonnull UUID playerId) {
        Objects.requireNonNull(playerId, "playerId cannot be null");
        if (requirementBypassPlayers.add(playerId)) {
            LOGGER.at(Level.INFO).log("Added requirement bypass for player %s", playerId);
        }
    }

    @Override
    public void removeRequirementBypass(@Nonnull UUID playerId) {
        Objects.requireNonNull(playerId, "playerId cannot be null");
        if (requirementBypassPlayers.remove(playerId)) {
            LOGGER.at(Level.INFO).log("Removed requirement bypass for player %s", playerId);
        }
    }

    /**
     * Checks if a player currently has a requirement bypass.
     *
     * @param playerId The player's UUID
     * @return true if the player bypasses gear requirements
     */
    public boolean hasRequirementBypass(@Nonnull UUID playerId) {
        return requirementBypassPlayers.contains(playerId);
    }

    // =========================================================================
    // GEARSERVICE IMPLEMENTATION - STATS
    // =========================================================================

    @Override
    @Nonnull
    public GearBonuses calculateGearBonuses(@Nonnull UUID playerId, @Nonnull Inventory inventory) {
        ensureInitialized();
        return statCalculator.calculateBonuses(playerId, inventory);
    }

    // =========================================================================
    // GEARSERVICE IMPLEMENTATION - RICH TOOLTIP (MESSAGE API)
    // =========================================================================

    @Override
    @Nonnull
    public Message buildRichTooltip(@Nonnull GearData gearData) {
        ensureInitialized();
        return richTooltipFormatter.build(gearData);
    }

    @Override
    @Nonnull
    public Message buildRichTooltip(@Nonnull GearData gearData, @Nonnull UUID playerId) {
        ensureInitialized();
        return richTooltipFormatter.build(gearData, playerId);
    }

    @Override
    @Nonnull
    public Message buildItemName(@Nonnull String baseItemName, @Nonnull GearData gearData) {
        ensureInitialized();
        return itemNameFormatter.buildItemName(baseItemName, gearData);
    }

    // =========================================================================
    // ACCESSORS
    // =========================================================================

    public GearGenerator getGearGenerator() {
        ensureInitialized();
        return gearGenerator;
    }

    public EquipmentValidator getEquipmentValidator() {
        ensureInitialized();
        return equipmentValidator;
    }

    public EquipmentListener getEquipmentListener() {
        ensureInitialized();
        return equipmentListener;
    }

    public GearStatCalculator getStatCalculator() {
        ensureInitialized();
        return statCalculator;
    }

    public GearStatApplier getStatApplier() {
        ensureInitialized();
        return statApplier;
    }

    /**
     * Gets the vanilla weapon discovery service.
     *
     * <p>This service enumerates all weapon attacks from Hytale's asset map
     * and provides pre-computed effectiveness multipliers using geometric mean.
     *
     * @return The vanilla weapon discovery service
     */
    public VanillaWeaponDiscovery getVanillaWeaponDiscovery() {
        ensureInitialized();
        return vanillaWeaponDiscovery;
    }

    /**
     * Gets the vanilla weapon profile for an item ID.
     *
     * <p>Convenience method that delegates to {@link VanillaWeaponDiscovery#getProfile(String)}.
     *
     * @param itemId The Hytale item ID (e.g., "Weapon_Daggers_Iron")
     * @return The weapon profile, or null if not found
     */
    @Nullable
    public VanillaWeaponProfile getVanillaWeaponProfile(@Nullable String itemId) {
        if (itemId == null || vanillaWeaponDiscovery == null) {
            return null;
        }
        return vanillaWeaponDiscovery.getProfile(itemId);
    }

    /**
     * Gets the item registry service.
     *
     * <p>This service manages custom item registration in Hytale's server-side
     * asset map, which is required for interaction validation.
     *
     * @return The item registry service
     */
    public ItemRegistryService getItemRegistryService() {
        ensureInitialized();
        return itemRegistryService;
    }

    public ItemDefinitionBuilder getItemDefinitionBuilder() {
        ensureInitialized();
        return itemDefinitionBuilder;
    }

    public TranslationSyncService getTranslationSyncService() {
        ensureInitialized();
        return translationSyncService;
    }

    public ItemSyncService getItemSyncService() {
        ensureInitialized();
        return itemSyncService;
    }

    public CustomItemSyncService getCustomItemSyncService() {
        ensureInitialized();
        return customItemSyncService;
    }

    @Nullable
    public ItemSyncCoordinator getSyncCoordinator() {
        return syncCoordinator;
    }

    /**
     * Gets the item world sync service.
     *
     * <p>This service syncs custom item definitions to nearby players when
     * items spawn in the world. It fixes the pickup notification issue where
     * Hytale's built-in {@code notifyPickupItem()} fires before listeners
     * can sync custom item definitions.
     *
     * @return The item world sync service
     */
    public ItemWorldSyncService getItemWorldSyncService() {
        ensureInitialized();
        return itemWorldSyncService;
    }

    /**
     * Gets the unified pickup notification service.
     *
     * <p>This service handles all pickup notifications for gear, maps, and stones.
     * It coordinates item sync and chat notifications in a unified way.
     *
     * @return The pickup notification service
     */
    public PickupNotificationService getPickupNotificationService() {
        ensureInitialized();
        return pickupNotificationService;
    }

    public LootListener getLootListener() {
        ensureInitialized();
        return lootListener;
    }

    /**
     * Gets the loot generator for creating gear drops.
     *
     * <p>This is used by the victory reward system to generate
     * gear items when players complete realms.
     *
     * @return The loot generator
     */
    public LootGenerator getLootGenerator() {
        ensureInitialized();
        return lootGenerator;
    }

    public RarityBonusCalculator getRarityBonusCalculator() {
        ensureInitialized();
        return rarityBonusCalculator;
    }

    public DropLevelBlender getDropLevelBlender() {
        ensureInitialized();
        return dropLevelBlender;
    }

    /**
     * Gets the dynamic loot registry.
     *
     * <p>This registry automatically discovers droppable weapons and armor
     * from Hytale's item registry, making the plugin compatible with any mod
     * that properly registers equipment using the Item API.
     *
     * @return The dynamic loot registry
     */
    public DynamicLootRegistry getDynamicLootRegistry() {
        ensureInitialized();
        return dynamicLootRegistry;
    }

    /**
     * Gets the vanilla item converter for converting vanilla weapons/armor to RPG gear.
     *
     * @return The vanilla item converter, or null if conversion is disabled
     */
    @Nullable
    public VanillaItemConverter getVanillaItemConverter() {
        return vanillaItemConverter;
    }

    /**
     * Gets the container loot replacement system.
     *
     * <p>This system replaces vanilla weapons/armor in containers with RPG gear,
     * stones, and realm maps.
     *
     * @return The container loot system, or null if disabled
     */
    @Nullable
    public ContainerLootSystem getContainerLootSystem() {
        return containerLootSystem;
    }

    public GearBalanceConfig getBalanceConfig() {
        ensureInitialized();
        return balanceConfig;
    }

    public ModifierConfig getModifierConfig() {
        ensureInitialized();
        return modifierConfig;
    }

    /**
     * Gets the equipment stat restriction config.
     *
     * <p>This config defines which stats can appear on which equipment types
     * (e.g., daggers get crit chance, staves get magic damage).
     *
     * @return The equipment stat config
     */
    public io.github.larsonix.trailoforbis.gear.config.EquipmentStatConfig getEquipmentStatConfig() {
        ensureInitialized();
        return configLoader.getEquipmentStatConfig();
    }

    public boolean isInitialized() {
        return initialized;
    }

    public RichTooltipFormatter getRichTooltipFormatter() {
        ensureInitialized();
        return richTooltipFormatter;
    }

    public ItemNameFormatter getItemNameFormatter() {
        ensureInitialized();
        return itemNameFormatter;
    }

    /**
     * Gets the item display name service for centralized item naming.
     *
     * <p>This service provides consistent display names across all plugin systems:
     * UIs, notifications, tooltips, and chat messages.
     *
     * @return The item display name service
     */
    public ItemDisplayNameService getItemDisplayNameService() {
        ensureInitialized();
        return itemDisplayNameService;
    }

    /**
     * Creates a GearBonusProvider using this manager's components.
     *
     * <p>The caller is responsible for wiring this to their AttributeManager.
     *
     * @return A new GearBonusProvider
     */
    public GearBonusProvider createGearBonusProvider() {
        ensureInitialized();
        return new GearBonusProvider(statCalculator, statApplier);
    }

    // =========================================================================
    // ITEM REGISTRATION (Reconnect Support)
    // =========================================================================

    /**
     * Ensures all gear items in the collection are registered in the server's asset map.
     *
     * <p>Custom gear items use unique item IDs (e.g., "rpg_gear_xxx") that must be registered
     * in Hytale's internal asset map for server-side validation to work. This registration
     * normally happens during gear generation, but is lost when:
     * <ul>
     *   <li>The server restarts</li>
     *   <li>The player reconnects (items exist in inventory but registration is memory-only)</li>
     * </ul>
     *
     * <p>Call this on player reconnect, before syncing items to clients.
     *
     * <p><b>Thread Safety:</b> Uses synchronous registration with write locks to ensure
     * items are immediately visible to Hytale's validation system.
     *
     * @param items Collection of items to check and register
     * @return Number of items re-registered
     */
    public int ensureItemsRegistered(@Nonnull Collection<ItemStack> items) {
        Objects.requireNonNull(items, "items cannot be null");

        if (itemRegistryService == null || !itemRegistryService.isInitialized()) {
            LOGGER.at(Level.WARNING).log("ItemRegistryService not available for item registration");
            return 0;
        }

        int registered = 0;
        for (ItemStack item : items) {
            if (item == null || item.isEmpty()) {
                continue;
            }

            Optional<GearData> gearDataOpt = GearUtils.readGearData(item);
            if (gearDataOpt.isEmpty() || !gearDataOpt.get().hasInstanceId()) {
                continue;
            }

            GearData gearData = gearDataOpt.get();
            String customId = gearData.getItemId();

            // Check if already registered in our service
            if (itemRegistryService.isRegistered(customId)) {
                continue;
            }

            // IMPROVED: Try multiple sources for base item ID
            // 1. First try metadata (most reliable if present)
            String baseItemId = GearUtils.getBaseItemId(item);

            // 2. If not in metadata, check our registry's in-memory cache (from DB)
            if (baseItemId == null) {
                baseItemId = itemRegistryService.getBaseItemId(customId);
            }

            // 3. If still not found, we can't re-register
            if (baseItemId == null) {
                LOGGER.at(Level.WARNING).log(
                    "Cannot re-register %s - no base item ID in metadata or registry cache",
                    customId);
                continue;
            }

            // Get base item from Hytale's asset map
            Item baseItem = Item.getAssetMap().getAsset(baseItemId);
            if (baseItem == null || baseItem == Item.UNKNOWN) {
                LOGGER.at(Level.WARNING).log(
                    "Cannot re-register %s - base item '%s' not in Hytale asset map",
                    customId, baseItemId);
                continue;
            }

            // Re-register in server's asset map SYNCHRONOUSLY
            // This ensures the item is immediately visible to Hytale's validation system
            // (ItemModule.exists() will return true) before the player can interact with it
            itemRegistryService.createAndRegisterSync(baseItem, customId);
            registered++;
            LOGGER.at(Level.FINE).log("Re-registered custom item: %s (base: %s)", customId, baseItemId);
        }

        return registered;
    }

    /**
     * Ensures all custom items (stones, maps) in the collection are registered in the server's asset map.
     *
     * <p>Custom items use unique item IDs (e.g., "rpg_stone_xxx", "rpg_map_xxx") that must be
     * registered in Hytale's internal asset map for server-side validation to work. This registration
     * normally happens during item creation, but is lost when:
     * <ul>
     *   <li>The server restarts</li>
     *   <li>The player reconnects (items exist in inventory but registration is memory-only)</li>
     * </ul>
     *
     * <p>This method also repairs items with missing instanceId by recovering it from the
     * ItemStack's item ID (e.g., "rpg_stone_xxx" → instanceId).
     *
     * <p>Call this on player reconnect, before syncing items to clients.
     *
     * <p><b>Thread Safety:</b> Uses synchronous registration with write locks to ensure
     * items are immediately visible to Hytale's validation system.
     *
     * @param items Collection of items to check and register
     * @return Number of items re-registered
     */
    public int ensureCustomItemsRegistered(@Nonnull Collection<ItemStack> items) {
        Objects.requireNonNull(items, "items cannot be null");

        if (itemRegistryService == null || !itemRegistryService.isInitialized()) {
            LOGGER.at(Level.WARNING).log("ItemRegistryService not available for custom item registration");
            return 0;
        }

        int registered = 0;

        for (ItemStack item : items) {
            if (item == null || item.isEmpty()) {
                continue;
            }

            // Stones are now native Hytale items — no custom registration needed

            // Try to register realm map (use fallback detection for reconnect recovery)
            if (RealmMapUtils.isMapAnyMethod(item)) {
                if (registerMapIfNeeded(item)) {
                    registered++;
                }
            }
        }

        return registered;
    }

    /**
     * Registers a realm map item in the asset map if not already registered.
     *
     * @param item The realm map item
     * @return true if the item was registered, false if already registered or failed
     */
    private boolean registerMapIfNeeded(@Nonnull ItemStack item) {
        // Use fallback method to recover data from backup keys when BSON fails
        Optional<RealmMapData> mapDataOpt = RealmMapUtils.readMapDataWithFallback(item);
        if (mapDataOpt.isEmpty()) {
            return false;
        }

        // readMapDataWithFallback already repairs instanceId if needed
        RealmMapData mapData = mapDataOpt.get();

        if (!mapData.hasInstanceId()) {
            // Map uses vanilla item type - no custom registration needed
            return false;
        }

        String customId = mapData.getItemId();

        // Check if already registered
        if (itemRegistryService.isRegistered(customId)) {
            return false;
        }

        // Get base item from Hytale's asset map
        String baseItemId = mapData.getBaseItemId();
        Item baseItem = Item.getAssetMap().getAsset(baseItemId);

        if (baseItem == null || baseItem == Item.UNKNOWN) {
            LOGGER.at(Level.WARNING).log(
                "Cannot register map %s - base item '%s' not in Hytale asset map",
                customId, baseItemId);
            return false;
        }

        // Register in server's asset map SYNCHRONOUSLY
        itemRegistryService.createAndRegisterSync(baseItem, customId);
        LOGGER.at(Level.FINE).log("Registered map: %s (base: %s, biome: %s)",
            customId, baseItemId, mapData.biome().name());

        return true;
    }

    // =========================================================================
    // TOOLTIP RESYNC (Real-Time Updates)
    // =========================================================================

    /**
     * Resync all gear tooltips for a player.
     *
     * <p>Call this when player stats or level change to update requirement colors.
     * This clears the player's item cache and re-syncs all gear items, causing
     * tooltips to be rebuilt with the player's current stats.
     *
     * @param playerId The player's UUID
     */
    public void resyncPlayerGear(@Nonnull UUID playerId) {
        Objects.requireNonNull(playerId, "playerId cannot be null");

        // Delegate to coordinator — marks all gear dirty, flushes on next tick.
        // No cache clearing, no immediate packets. Hash dedup filters naturally.
        if (syncCoordinator != null) {
            syncCoordinator.markStatsDirty(playerId);
        }
    }

    /**
     * Resync a custom item (map, stone) after modification.
     *
     * <p>Call this after a stone modifies a map (e.g., identification, reroll)
     * to update the client's view of the item.
     *
     * @param playerRef The player reference
     * @param customData The updated custom item data
     * @return true if the item was resynced
     */
    public boolean resyncCustomItem(@Nonnull PlayerRef playerRef, @Nonnull CustomItemData customData) {
        Objects.requireNonNull(playerRef, "playerRef cannot be null");
        Objects.requireNonNull(customData, "customData cannot be null");

        if (customItemSyncService == null) {
            LOGGER.at(Level.WARNING).log("CustomItemSyncService not available for resync");
            return false;
        }

        boolean resynced = customItemSyncService.invalidateAndResync(playerRef, customData);
        if (resynced) {
            LOGGER.at(Level.FINE).log("Resynced custom item %s for player %s",
                customData.getItemId(), playerRef.getUuid());
        }
        return resynced;
    }

    /**
     * Resync a gear item after modification.
     *
     * <p>Call this after a stone modifies gear (e.g., reroll modifiers)
     * to update the client's view of the item.
     *
     * @param playerRef The player reference
     * @param itemStack The item stack
     * @param gearData The updated gear data
     * @return true if the item was resynced
     */
    public boolean resyncGearItem(
            @Nonnull PlayerRef playerRef,
            @Nonnull ItemStack itemStack,
            @Nonnull GearData gearData) {
        Objects.requireNonNull(playerRef, "playerRef cannot be null");
        Objects.requireNonNull(itemStack, "itemStack cannot be null");
        Objects.requireNonNull(gearData, "gearData cannot be null");

        if (itemSyncService == null || !itemSyncService.isEnabled()) {
            LOGGER.at(Level.WARNING).log("ItemSyncService not available for resync");
            return false;
        }

        if (!gearData.hasInstanceId()) {
            LOGGER.at(Level.FINE).log("Gear has no instanceId, cannot resync");
            return false;
        }

        // Clear the cached hash to force resync
        String itemId = gearData.getItemId();
        itemSyncService.getPlayerCache().removeItem(playerRef.getUuid(), itemId);

        // Also clear translation so it gets re-registered with updated content
        if (gearData.instanceId() != null) {
            translationSyncService.unregisterTranslation(
                playerRef.getUuid(), gearData.instanceId().toCompactString());
        }

        // Resync the item
        boolean resynced = itemSyncService.syncItem(playerRef, itemStack, gearData);
        if (resynced) {
            LOGGER.at(Level.FINE).log("Resynced gear item %s for player %s",
                itemId, playerRef.getUuid());
        }
        return resynced;
    }

    // =========================================================================
    // INVENTORY HELPERS
    // =========================================================================

    /**
     * Collects all items from an inventory.
     *
     * @param inventory The inventory to collect items from
     * @return List of all items (non-null, non-empty items only)
     */
    @Nonnull
    public static List<ItemStack> collectAllInventoryItems(@Nonnull Inventory inventory) {
        Objects.requireNonNull(inventory, "inventory cannot be null");

        List<ItemStack> items = new ArrayList<>();
        collectFromContainer(inventory.getArmor(), items);
        collectFromContainer(inventory.getHotbar(), items);
        collectFromContainer(inventory.getStorage(), items);  // Main inventory grid
        collectFromContainer(inventory.getBackpack(), items);
        collectFromContainer(inventory.getUtility(), items);
        return items;
    }

    private static void collectFromContainer(@Nullable ItemContainer container, @Nonnull List<ItemStack> items) {
        if (container == null) {
            return;
        }
        for (short i = 0; i < container.getCapacity(); i++) {
            ItemStack item = container.getItemStack(i);
            if (item != null && !item.isEmpty()) {
                items.add(item);
            }
        }
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private void ensureInitialized() {
        if (!initialized) {
            throw new IllegalStateException("GearManager not initialized. Call initialize() first.");
        }
    }

    // =========================================================================
    // EXCEPTION
    // =========================================================================

    public static class GearInitializationException extends RuntimeException {
        public GearInitializationException(String message) {
            super(message);
        }

        public GearInitializationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
