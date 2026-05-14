package io.github.larsonix.trailoforbis.gear.conversion;

import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.api.ServiceRegistry;
import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig;
import io.github.larsonix.trailoforbis.gear.core.GearCoreModule;
import io.github.larsonix.trailoforbis.gear.item.ItemRegistryService;
import io.github.larsonix.trailoforbis.gear.loot.GearLootManager;
import io.github.larsonix.trailoforbis.gear.reskin.ReskinCraftInterceptor;
import io.github.larsonix.trailoforbis.gear.reskin.ReskinDataPreserver;
import io.github.larsonix.trailoforbis.gear.reskin.ReskinRecipeGenerator;
import io.github.larsonix.trailoforbis.gear.reskin.ReskinResourceTypeRegistry;
import io.github.larsonix.trailoforbis.gear.sync.GearItemSyncManager;
import io.github.larsonix.trailoforbis.gear.systems.CraftGuidePreSystem;
import io.github.larsonix.trailoforbis.gear.systems.CraftingConversionSystem;
import io.github.larsonix.trailoforbis.leveling.api.LevelingService;
import io.github.larsonix.trailoforbis.loot.consumable.ConsumableLootConfig;
import io.github.larsonix.trailoforbis.loot.consumable.ConsumableLootRegistry;
import io.github.larsonix.trailoforbis.loot.container.ContainerLootConfig;
import io.github.larsonix.trailoforbis.loot.container.ContainerLootSystem;
import io.github.larsonix.trailoforbis.maps.items.RealmMapGenerator;

import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.logging.Level;

/**
 * Manages vanilla conversion, container loot, crafting preview, timed craft, and reskin systems.
 *
 * <p>Also owns the deferred registration logic for systems that require LevelingService.
 *
 * <p>Owned by {@link io.github.larsonix.trailoforbis.gear.GearManager} (facade).
 */
public final class GearConversionManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // Vanilla conversion
    private VanillaItemConverter vanillaItemConverter;
    private CraftingConversionSystem craftingConversionSystem;
    private ChestLootConversionListener chestLootConversionListener;

    // Container loot
    private ContainerLootSystem containerLootSystem;

    // Crafting preview
    @Nullable private io.github.larsonix.trailoforbis.gear.tooltip.CraftingPreviewService craftingPreviewService;
    @Nullable private io.github.larsonix.trailoforbis.gear.systems.CraftingBenchPreviewSystem craftingBenchPreviewSystem;

    // Timed craft
    @Nullable private io.github.larsonix.trailoforbis.gear.systems.TimedCraftConversionHandler timedCraftHandler;

    // Reskin
    private ReskinResourceTypeRegistry reskinResourceTypeRegistry;
    private ReskinDataPreserver reskinDataPreserver;
    private ReskinCraftInterceptor reskinCraftInterceptor;

    // Deferred state
    private boolean craftingConversionPending = false;
    private boolean timedCraftPending = false;

    // Dependencies (kept for deferred registration)
    private TrailOfOrbis plugin;
    private GearCoreModule core;
    private GearItemSyncManager syncManager;
    private GearBalanceConfig balanceConfig;

    /**
     * Initializes all conversion, crafting, and reskin systems.
     */
    public void initialize(
            @Nonnull GearCoreModule core,
            @Nonnull GearItemSyncManager syncManager,
            @Nonnull GearLootManager lootManager,
            @Nonnull ItemRegistryService itemRegistryService,
            @Nonnull GearBalanceConfig balanceConfig,
            @Nonnull TrailOfOrbis plugin) {

        this.plugin = plugin;
        this.core = core;
        this.syncManager = syncManager;
        this.balanceConfig = balanceConfig;

        // Initialize reskin system (needs dynamicLootRegistry from loot module)
        initializeReskinSystem(lootManager, itemRegistryService);

        // Initialize vanilla conversion
        initializeVanillaConversion(lootManager, itemRegistryService);

        // Timed craft handler (needs VanillaItemConverter)
        initializeTimedCraftHandler();

        // Crafting preview (needs VanillaItemConverter)
        initializeCraftingPreview();

        // Wire crafting preview into reskin preserver
        if (craftingPreviewService != null && reskinDataPreserver != null) {
            reskinDataPreserver.setCraftingPreviewService(craftingPreviewService);
        }

        LOGGER.at(Level.INFO).log("GearConversionManager initialized");
    }

    /**
     * Registers deferred systems that require LevelingService.
     */
    public void registerDeferredSystems() {
        if (craftingConversionPending && vanillaItemConverter != null) {
            ServiceRegistry.get(LevelingService.class).ifPresentOrElse(
                levelingService -> {
                    craftingConversionSystem = new CraftingConversionSystem(
                        vanillaItemConverter,
                        levelingService,
                        createDistanceCalculator(),
                        getConversionConfig().getCraftingLevelMultiplier(),
                        syncManager.getItemSyncService()
                    );
                    if (reskinCraftInterceptor != null) {
                        plugin.getEntityStoreRegistry().registerSystem(reskinCraftInterceptor);
                        LOGGER.at(Level.INFO).log("Registered ReskinCraftInterceptor ECS system");
                    }
                    plugin.getEntityStoreRegistry().registerSystem(craftingConversionSystem);
                    plugin.getEntityStoreRegistry().registerSystem(new CraftGuidePreSystem());
                    craftingBenchPreviewSystem = new io.github.larsonix.trailoforbis.gear.systems.CraftingBenchPreviewSystem();
                    plugin.getEntityStoreRegistry().registerSystem(craftingBenchPreviewSystem);
                    LOGGER.at(Level.INFO).log("Registered CraftingBenchPreviewSystem (ECS + InventoryChange hybrid)");
                    craftingConversionPending = false;
                    LOGGER.at(Level.INFO).log("Registered deferred crafting conversion ECS system");

                    var coord = syncManager.getSyncCoordinator();
                    if (coord != null) {
                        levelingService.registerLevelUpListener((playerId, newLevel, oldLevel, totalXp) -> {
                            coord.markCraftingPreviewDirty(playerId);
                        });
                    }
                },
                () -> LOGGER.at(Level.WARNING).log(
                    "LevelingService still not available - crafting conversion will not work")
            );
        }

        if (timedCraftPending && timedCraftHandler == null) {
            initializeTimedCraftHandler();
            if (timedCraftHandler != null) {
                timedCraftPending = false;
                LOGGER.at(Level.INFO).log("Deferred timed craft handler now initialized");
            } else {
                LOGGER.at(Level.WARNING).log(
                    "LevelingService still not available - timed craft conversion will not work");
            }
        }
    }

    /**
     * Shuts down conversion systems.
     */
    public void shutdown() {
        vanillaItemConverter = null;
        craftingConversionSystem = null;
        chestLootConversionListener = null;
        if (containerLootSystem != null) {
            containerLootSystem.shutdown();
            containerLootSystem = null;
        }
        // Restore vanilla PocketCrafting window factory
        com.hypixel.hytale.server.core.entity.entities.player.windows.Window.CLIENT_REQUESTABLE_WINDOW_TYPES.put(
                com.hypixel.hytale.protocol.packets.window.WindowType.PocketCrafting,
                com.hypixel.hytale.builtin.crafting.window.FieldCraftingWindow::new);
        craftingPreviewService = null;
        craftingBenchPreviewSystem = null;
        timedCraftHandler = null;
        reskinResourceTypeRegistry = null;
        reskinDataPreserver = null;
        reskinCraftInterceptor = null;
        plugin = null;
        core = null;
        syncManager = null;
        balanceConfig = null;
    }

    // =========================================================================
    // INITIALIZATION HELPERS
    // =========================================================================

    private void initializeVanillaConversion(GearLootManager lootManager, ItemRegistryService itemRegistryService) {
        VanillaConversionConfig conversionConfig = plugin.getConfigManager().getVanillaConversionConfig();

        if (!conversionConfig.isEnabled()) {
            LOGGER.at(Level.INFO).log("Vanilla item conversion is disabled");
            return;
        }

        vanillaItemConverter = new VanillaItemConverter(
            conversionConfig,
            core.getGearGenerator(),
            core.getGearGenerator().getRarityRoller()
        );

        if (core.getItemMigrationService() != null) {
            core.getItemMigrationService().setVanillaItemConverter(vanillaItemConverter);
        }

        if (vanillaItemConverter.isSourceEnabled(VanillaItemConverter.AcquisitionSource.CRAFTING)) {
            ServiceRegistry.get(LevelingService.class)
                .ifPresentOrElse(
                    levelingService -> {
                        craftingConversionSystem = new CraftingConversionSystem(
                            vanillaItemConverter,
                            levelingService,
                            createDistanceCalculator(),
                            getConversionConfig().getCraftingLevelMultiplier(),
                            syncManager.getItemSyncService()
                        );
                        plugin.getEntityStoreRegistry().registerSystem(craftingConversionSystem);
                        plugin.getEntityStoreRegistry().registerSystem(new CraftGuidePreSystem());
                        LOGGER.at(Level.INFO).log("Registered crafting conversion ECS system (material-based levels)");
                    },
                    () -> {
                        craftingConversionPending = true;
                        LOGGER.at(Level.INFO).log(
                            "Crafting conversion deferred (LevelingService will be available later)");
                    }
                );
        }

        chestLootConversionListener = new ChestLootConversionListener(
            vanillaItemConverter,
            plugin.getConfigManager().getMobScalingConfig()
        );
        chestLootConversionListener.register(plugin.getEventRegistry());

        initializeContainerLootSystem(conversionConfig, lootManager, itemRegistryService);

        LOGGER.at(Level.INFO).log("Vanilla item conversion initialized: mob_drops=%s, chest_loot=%s, crafting=%s",
            conversionConfig.getSources().isMobDrops() ? "enabled" : "disabled",
            conversionConfig.getSources().isChestLoot() ? "enabled" : "disabled",
            conversionConfig.getSources().isCrafting() ? "enabled" : "disabled"
        );
    }

    private void initializeContainerLootSystem(
            VanillaConversionConfig conversionConfig,
            GearLootManager lootManager,
            ItemRegistryService itemRegistryService) {
        ContainerLootConfig containerLootConfig = plugin.getConfigManager().getContainerLootConfig();

        if (!containerLootConfig.isEnabled()) {
            LOGGER.at(Level.INFO).log("Container loot replacement system is disabled");
            return;
        }

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
            LOGGER.at(Level.INFO).log(
                "RealmMapGenerator deferred (RealmsManager will be available later)");
        }

        // Initialize consumable loot registry (food/potions)
        ConsumableLootRegistry consumableRegistry = null;
        try {
            ConsumableLootConfig consumableConfig = plugin.getConfigManager().getConsumableLootConfig();
            if (consumableConfig.isEnabled()) {
                consumableRegistry = new ConsumableLootRegistry(consumableConfig);
            }
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e).log("Failed to initialize consumable loot registry");
        }

        containerLootSystem = new ContainerLootSystem(
            containerLootConfig,
            lootManager.getLootGenerator(),
            mapGenerator,
            consumableRegistry,
            conversionConfig,
            lootManager.getDropLevelBlender(),
            lootManager.getRarityBonusCalculator(),
            core.getGearGenerator().getRarityRoller()
        );

        LOGGER.at(Level.INFO).log("Container loot replacement system initialized (consumables=%s)",
            consumableRegistry != null ? "enabled" : "disabled");
    }

    private void initializeTimedCraftHandler() {
        io.github.larsonix.trailoforbis.mobs.calculator.DistanceBonusCalculator distCalc = createDistanceCalculator();
        VanillaConversionConfig convConfig = getConversionConfig();
        if (distCalc == null || vanillaItemConverter == null) {
            LOGGER.at(Level.WARNING).log("Cannot initialize timed craft handler: missing distance calculator or converter");
            return;
        }
        ServiceRegistry.get(LevelingService.class).ifPresentOrElse(
            levelingService -> {
                timedCraftHandler = new io.github.larsonix.trailoforbis.gear.systems.TimedCraftConversionHandler(
                        vanillaItemConverter, distCalc, convConfig, levelingService,
                        syncManager.getItemSyncService());
                LOGGER.at(Level.INFO).log("Initialized timed craft conversion handler (BasicCrafting, DiagramCrafting, Processing)");
            },
            () -> {
                timedCraftPending = true;
                LOGGER.at(Level.INFO).log("Timed craft handler deferred (LevelingService will be available later)");
            }
        );
    }

    private void initializeCraftingPreview() {
        io.github.larsonix.trailoforbis.mobs.calculator.DistanceBonusCalculator distCalc = createDistanceCalculator();
        VanillaConversionConfig convConfig = getConversionConfig();
        if (distCalc == null || vanillaItemConverter == null) {
            LOGGER.at(Level.WARNING).log("Cannot initialize crafting preview: missing distance calculator or converter");
            return;
        }

        craftingPreviewService = new io.github.larsonix.trailoforbis.gear.tooltip.CraftingPreviewService(
                vanillaItemConverter.getMaterialMapper(),
                distCalc,
                convConfig,
                vanillaItemConverter.getItemClassifier(),
                balanceConfig
        );
        craftingPreviewService.initialize();

        // Override PocketCrafting window factory to inject RPG preview definitions.
        // PocketCrafting is client-initiated (ClientOpenWindow packet) — no UseBlockEvent fires,
        // so CraftingBenchPreviewSystem's Phase 1 is bypassed. This wrapper intercepts onOpen0()
        // to send definitions before the UpdateWindow packet reaches the client.
        com.hypixel.hytale.server.core.entity.entities.player.windows.Window.CLIENT_REQUESTABLE_WINDOW_TYPES.put(
                com.hypixel.hytale.protocol.packets.window.WindowType.PocketCrafting,
                io.github.larsonix.trailoforbis.gear.systems.PocketCraftingPreviewWindow::new);
        LOGGER.at(Level.INFO).log("Registered PocketCraftingPreviewWindow for crafting preview in Pocket Crafting");

        var coordinator = syncManager.getSyncCoordinator();
        if (coordinator != null) {
            coordinator.setCraftingPreviewService(craftingPreviewService);
        }
    }

    private void initializeReskinSystem(GearLootManager lootManager, ItemRegistryService itemRegistryService) {
        reskinResourceTypeRegistry = new ReskinResourceTypeRegistry();

        ReskinRecipeGenerator recipeGenerator = new ReskinRecipeGenerator(
                lootManager.getDynamicLootRegistry(), reskinResourceTypeRegistry);
        int recipeCount = recipeGenerator.generate();

        itemRegistryService.setReskinRegistry(reskinResourceTypeRegistry);
        int retroInjected = itemRegistryService.retroInjectReskinResourceTypes();

        reskinDataPreserver = new ReskinDataPreserver();

        reskinCraftInterceptor = new ReskinCraftInterceptor(
                reskinDataPreserver, itemRegistryService, syncManager.getItemSyncService());

        LOGGER.at(Level.INFO).log("Reskin system initialized: %d recipes, %d groups, %d cached items patched",
                recipeCount, reskinResourceTypeRegistry.size(), retroInjected);
    }

    @Nullable
    private io.github.larsonix.trailoforbis.mobs.calculator.DistanceBonusCalculator createDistanceCalculator() {
        if (plugin.getConfigManager() == null || plugin.getConfigManager().getMobScalingConfig() == null) {
            return null;
        }
        return new io.github.larsonix.trailoforbis.mobs.calculator.DistanceBonusCalculator(
                plugin.getConfigManager().getMobScalingConfig());
    }

    @Nonnull
    private VanillaConversionConfig getConversionConfig() {
        VanillaConversionConfig config = plugin.getConfigManager().getVanillaConversionConfig();
        return config != null ? config : new VanillaConversionConfig();
    }

    // =========================================================================
    // ACCESSORS
    // =========================================================================

    @Nullable
    public VanillaItemConverter getVanillaItemConverter() {
        return vanillaItemConverter;
    }

    @Nullable
    public CraftingConversionSystem getCraftingConversionSystem() {
        return craftingConversionSystem;
    }

    @Nullable
    public ContainerLootSystem getContainerLootSystem() {
        return containerLootSystem;
    }

    @Nullable
    public io.github.larsonix.trailoforbis.gear.tooltip.CraftingPreviewService getCraftingPreviewService() {
        return craftingPreviewService;
    }

    @Nullable
    public io.github.larsonix.trailoforbis.gear.systems.CraftingBenchPreviewSystem getCraftingBenchPreviewSystem() {
        return craftingBenchPreviewSystem;
    }

    @Nullable
    public io.github.larsonix.trailoforbis.gear.systems.TimedCraftConversionHandler getTimedCraftHandler() {
        return timedCraftHandler;
    }

    public ReskinDataPreserver getReskinDataPreserver() {
        return reskinDataPreserver;
    }

    @Nullable
    public ReskinCraftInterceptor getReskinCraftInterceptor() {
        return reskinCraftInterceptor;
    }
}
