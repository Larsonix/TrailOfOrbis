package io.github.larsonix.trailoforbis.gear;

import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.api.ServiceRegistry;
import io.github.larsonix.trailoforbis.attributes.AttributeManager;
import io.github.larsonix.trailoforbis.attributes.AttributeType;
import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig;
import io.github.larsonix.trailoforbis.gear.config.GearConfigLoader;
import io.github.larsonix.trailoforbis.gear.config.ModifierConfig;
import io.github.larsonix.trailoforbis.gear.conversion.VanillaItemConverter;
import io.github.larsonix.trailoforbis.gear.core.GearCoreModule;
import io.github.larsonix.trailoforbis.gear.systems.CraftingConversionSystem;
import io.github.larsonix.trailoforbis.gear.equipment.EquipmentListener;
import io.github.larsonix.trailoforbis.gear.equipment.EquipmentValidator;
import io.github.larsonix.trailoforbis.gear.equipment.EquipmentValidator.ValidationResult;
import io.github.larsonix.trailoforbis.gear.generation.GearGenerator;
import io.github.larsonix.trailoforbis.gear.migration.ItemMigrationService;
import io.github.larsonix.trailoforbis.gear.loot.DropLevelBlender;
import io.github.larsonix.trailoforbis.gear.loot.DynamicLootRegistry;
import io.github.larsonix.trailoforbis.gear.loot.LootGenerator;
import io.github.larsonix.trailoforbis.gear.loot.LootListener;
import io.github.larsonix.trailoforbis.gear.loot.RarityBonusCalculator;
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.gear.reskin.ReskinCraftInterceptor;
import io.github.larsonix.trailoforbis.gear.reskin.ReskinDataPreserver;
import io.github.larsonix.trailoforbis.gear.stats.GearBonusProvider;
import io.github.larsonix.trailoforbis.gear.stats.GearStatApplier;
import io.github.larsonix.trailoforbis.gear.stats.GearStatCalculator;
import io.github.larsonix.trailoforbis.gear.stats.GearStatCalculator.GearBonuses;
import io.github.larsonix.trailoforbis.gear.tooltip.ItemNameFormatter;
import io.github.larsonix.trailoforbis.gear.tooltip.RichTooltipFormatter;
import io.github.larsonix.trailoforbis.gear.item.CustomItemData;
import io.github.larsonix.trailoforbis.gear.item.CustomItemSyncService;
import io.github.larsonix.trailoforbis.gear.item.ItemDefinitionBuilder;
import io.github.larsonix.trailoforbis.gear.item.ItemDisplayNameService;
import io.github.larsonix.trailoforbis.gear.item.ItemRegistryService;
import io.github.larsonix.trailoforbis.gear.item.ItemSyncService;
import io.github.larsonix.trailoforbis.gear.item.ItemWorldSyncService;
import io.github.larsonix.trailoforbis.gear.item.TranslationSyncService;
import io.github.larsonix.trailoforbis.gear.sync.ItemSyncCoordinator;
import io.github.larsonix.trailoforbis.gear.notification.PickupNotificationService;
import io.github.larsonix.trailoforbis.gear.util.GearUtils;
import io.github.larsonix.trailoforbis.gear.vanilla.VanillaWeaponDiscovery;
import io.github.larsonix.trailoforbis.gear.vanilla.VanillaWeaponProfile;
import io.github.larsonix.trailoforbis.loot.container.ContainerLootSystem;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;

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

    // Core module (generation, validation, stats, tooltips, migration, vanilla weapons)
    private GearCoreModule core;

    // Shared infrastructure (used by core, sync, conversion — owned here)
    private ItemRegistryService itemRegistryService;

    // Item sync module (definitions, translations, sync services, coordinator, broadcast, resync)
    private io.github.larsonix.trailoforbis.gear.sync.GearItemSyncManager itemSyncManager;

    // Loot module
    private io.github.larsonix.trailoforbis.gear.loot.GearLootManager lootManager;

    // Conversion module (vanilla conversion, container loot, crafting preview, reskin)
    private io.github.larsonix.trailoforbis.gear.conversion.GearConversionManager conversionManager;

    // Requirement bypass (Creative mode)
    private final Set<UUID> requirementBypassPlayers = ConcurrentHashMap.newKeySet();

    // State
    private boolean initialized = false;

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
        conversionManager.registerDeferredSystems();
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

        // Shutdown in reverse initialization order

        // Conversion (last to init, depends on core + sync + loot)
        if (conversionManager != null) {
            conversionManager.shutdown();
            conversionManager = null;
        }

        // Loot (depends on core)
        if (lootManager != null) {
            lootManager.shutdown();
            lootManager = null;
        }

        // Item sync (depends on core)
        if (itemSyncManager != null) {
            itemSyncManager.shutdown();
            itemSyncManager = null;
        }

        // Core (depends on item registry)
        if (core != null) {
            core.shutdown();
            core = null;
        }

        // Item registry service (shared infrastructure, last to go)
        if (itemRegistryService != null) {
            itemRegistryService.shutdown();
            itemRegistryService = null;
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
            // Shutdown old sub-managers before re-creating (reverse init order)
            if (conversionManager != null) { conversionManager.shutdown(); conversionManager = null; }
            if (lootManager != null) { lootManager.shutdown(); lootManager = null; }
            if (itemSyncManager != null) { itemSyncManager.shutdown(); itemSyncManager = null; }
            if (core != null) { core.shutdown(); core = null; }
            // ItemRegistryService is reused (not recreated)

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

        // Item registry service - shared infrastructure, must be initialized before everything
        if (itemRegistryService == null) {
            itemRegistryService = new ItemRegistryService();
        }
        itemRegistryService.initialize(plugin.getDataManager());

        // Initialize core module (generation, validation, stats, tooltips, migration)
        boolean bypassEnabled = plugin.getConfigManager().getRPGConfig().isCreativeModeBypassRequirements();
        core = new GearCoreModule();
        core.initialize(balanceConfig, modifierConfig, configLoader,
                attributeManager, itemRegistryService,
                bypassEnabled ? requirementBypassPlayers::contains : uuid -> false,
                plugin);

        // Initialize item sync module
        itemSyncManager = new io.github.larsonix.trailoforbis.gear.sync.GearItemSyncManager();
        itemSyncManager.initialize(core, modifierConfig, itemRegistryService, attributeManager, plugin);

        // Wire active-item supplier for demotion sweep safety scan
        // This ensures items held by online players are never demoted from the asset map
        var syncService = itemSyncManager.getItemSyncService();
        if (syncService != null) {
            itemRegistryService.setActiveItemsSupplier(
                () -> syncService.getPlayerCache().getAllTrackedItemIds());
        }
    }

    private void initializeLootSystem() {
        // Initialize loot module
        lootManager = new io.github.larsonix.trailoforbis.gear.loot.GearLootManager();
        lootManager.initialize(balanceConfig, core.getGearGenerator(), plugin);

        // Initialize conversion module (reskin, vanilla conversion, crafting preview, timed craft)
        conversionManager = new io.github.larsonix.trailoforbis.gear.conversion.GearConversionManager();
        conversionManager.initialize(core, itemSyncManager, lootManager,
                itemRegistryService, balanceConfig, plugin);
    }

    @Nullable
    public io.github.larsonix.trailoforbis.gear.systems.TimedCraftConversionHandler getTimedCraftHandler() {
        return conversionManager != null ? conversionManager.getTimedCraftHandler() : null;
    }

    @Nullable
    public io.github.larsonix.trailoforbis.gear.tooltip.CraftingPreviewService getCraftingPreviewService() {
        return conversionManager != null ? conversionManager.getCraftingPreviewService() : null;
    }

    @Nullable
    public io.github.larsonix.trailoforbis.gear.systems.CraftingBenchPreviewSystem getCraftingBenchPreviewSystem() {
        return conversionManager != null ? conversionManager.getCraftingBenchPreviewSystem() : null;
    }

    public ReskinDataPreserver getReskinDataPreserver() {
        return conversionManager != null ? conversionManager.getReskinDataPreserver() : null;
    }

    @Nullable
    public ReskinCraftInterceptor getReskinCraftInterceptor() {
        return conversionManager != null ? conversionManager.getReskinCraftInterceptor() : null;
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
        return core.getGearGenerator().generate(baseItem, itemLevel, slot);
    }

    @Override
    @Nonnull
    public ItemStack generateGear(@Nonnull ItemStack baseItem, int itemLevel, @Nonnull String slot, @Nonnull GearRarity rarity) {
        ensureInitialized();
        return core.getGearGenerator().generate(baseItem, itemLevel, slot, rarity);
    }

    @Override
    @Nonnull
    public ItemStack generateGear(@Nonnull ItemStack baseItem, int itemLevel, @Nonnull String slot, double rarityBonus) {
        ensureInitialized();
        return core.getGearGenerator().generate(baseItem, itemLevel, slot, rarityBonus);
    }

    @Override
    @Nonnull
    public GearData generateGearData(int itemLevel, @Nonnull String slot, @Nonnull GearRarity rarity) {
        ensureInitialized();
        return core.getGearGenerator().generateData(itemLevel, slot, rarity);
    }

    // =========================================================================
    // GEARSERVICE IMPLEMENTATION - VALIDATION
    // =========================================================================

    @Override
    public boolean canEquip(@Nonnull UUID playerId, @Nullable ItemStack item) {
        ensureInitialized();
        return core.getEquipmentValidator().canEquip(playerId, item);
    }

    @Override
    @Nonnull
    public ValidationResult checkRequirements(@Nonnull UUID playerId, @Nullable ItemStack item) {
        ensureInitialized();
        return core.getEquipmentValidator().checkRequirements(playerId, item);
    }

    @Override
    @Nonnull
    public Map<AttributeType, Integer> getRequirements(@Nullable ItemStack item) {
        ensureInitialized();
        return core.getEquipmentValidator().getRequirements(item);
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
        return core.getStatCalculator().calculateBonuses(playerId, inventory);
    }

    // =========================================================================
    // GEARSERVICE IMPLEMENTATION - RICH TOOLTIP (MESSAGE API)
    // =========================================================================

    @Override
    @Nonnull
    public Message buildRichTooltip(@Nonnull GearData gearData) {
        ensureInitialized();
        return core.getRichTooltipFormatter().build(gearData);
    }

    @Override
    @Nonnull
    public Message buildRichTooltip(@Nonnull GearData gearData, @Nonnull UUID playerId) {
        ensureInitialized();
        return core.getRichTooltipFormatter().build(gearData, playerId);
    }

    @Override
    @Nonnull
    public Message buildItemName(@Nonnull String baseItemName, @Nonnull GearData gearData) {
        ensureInitialized();
        return core.getItemNameFormatter().buildItemName(baseItemName, gearData);
    }

    // =========================================================================
    // ACCESSORS
    // =========================================================================

    public GearGenerator getGearGenerator() {
        ensureInitialized();
        return core.getGearGenerator();
    }

    public EquipmentValidator getEquipmentValidator() {
        ensureInitialized();
        return core.getEquipmentValidator();
    }

    public EquipmentListener getEquipmentListener() {
        ensureInitialized();
        return core.getEquipmentListener();
    }

    public GearStatCalculator getStatCalculator() {
        ensureInitialized();
        return core.getStatCalculator();
    }

    public GearStatApplier getStatApplier() {
        ensureInitialized();
        return core.getStatApplier();
    }

    public VanillaWeaponDiscovery getVanillaWeaponDiscovery() {
        ensureInitialized();
        return core.getVanillaWeaponDiscovery();
    }

    @Nullable
    public VanillaWeaponProfile getVanillaWeaponProfile(@Nullable String itemId) {
        return core.getVanillaWeaponProfile(itemId);
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
        return itemSyncManager.getItemDefinitionBuilder();
    }

    public TranslationSyncService getTranslationSyncService() {
        ensureInitialized();
        return itemSyncManager.getTranslationSyncService();
    }

    public ItemSyncService getItemSyncService() {
        ensureInitialized();
        return itemSyncManager.getItemSyncService();
    }

    public CustomItemSyncService getCustomItemSyncService() {
        ensureInitialized();
        return itemSyncManager.getCustomItemSyncService();
    }

    @Nullable
    public ItemSyncCoordinator getSyncCoordinator() {
        return itemSyncManager != null ? itemSyncManager.getSyncCoordinator() : null;
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
        return itemSyncManager.getItemWorldSyncService();
    }

    public void setEquipmentBroadcastSystem(
            @Nonnull io.github.larsonix.trailoforbis.gear.systems.EquipmentDefinitionBroadcastSystem system) {
        itemSyncManager.setEquipmentBroadcastSystem(system);
    }

    @Nullable
    public io.github.larsonix.trailoforbis.gear.systems.EquipmentDefinitionBroadcastSystem getEquipmentBroadcastSystem() {
        return itemSyncManager != null ? itemSyncManager.getEquipmentBroadcastSystem() : null;
    }

    @Nullable
    public CraftingConversionSystem getCraftingConversionSystem() {
        return conversionManager != null ? conversionManager.getCraftingConversionSystem() : null;
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
        return itemSyncManager.getPickupNotificationService();
    }

    public LootListener getLootListener() {
        ensureInitialized();
        return lootManager.getLootListener();
    }

    public LootGenerator getLootGenerator() {
        ensureInitialized();
        return lootManager.getLootGenerator();
    }

    public RarityBonusCalculator getRarityBonusCalculator() {
        ensureInitialized();
        return lootManager.getRarityBonusCalculator();
    }

    public DropLevelBlender getDropLevelBlender() {
        ensureInitialized();
        return lootManager.getDropLevelBlender();
    }

    public DynamicLootRegistry getDynamicLootRegistry() {
        ensureInitialized();
        return lootManager.getDynamicLootRegistry();
    }

    /**
     * Gets the vanilla item converter for converting vanilla weapons/armor to RPG gear.
     *
     * @return The vanilla item converter, or null if conversion is disabled
     */
    @Nullable
    public VanillaItemConverter getVanillaItemConverter() {
        return conversionManager != null ? conversionManager.getVanillaItemConverter() : null;
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
        return conversionManager != null ? conversionManager.getContainerLootSystem() : null;
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

    public ItemMigrationService getItemMigrationService() {
        ensureInitialized();
        return core.getItemMigrationService();
    }

    public boolean isInitialized() {
        return initialized;
    }

    public RichTooltipFormatter getRichTooltipFormatter() {
        ensureInitialized();
        return core.getRichTooltipFormatter();
    }

    public ItemNameFormatter getItemNameFormatter() {
        ensureInitialized();
        return core.getItemNameFormatter();
    }

    public ItemDisplayNameService getItemDisplayNameService() {
        ensureInitialized();
        return core.getItemDisplayNameService();
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
        return core.createGearBonusProvider();
    }

    // =========================================================================
    // ITEM REGISTRATION (Reconnect Support)
    // =========================================================================

    public int ensureItemsRegistered(@Nonnull Collection<ItemStack> items) {
        return itemSyncManager.ensureItemsRegistered(items);
    }

    public int ensureCustomItemsRegistered(@Nonnull Collection<ItemStack> items) {
        return itemSyncManager.ensureCustomItemsRegistered(items);
    }

    public void resyncPlayerGear(@Nonnull UUID playerId) {
        itemSyncManager.resyncPlayerGear(playerId);
    }

    public boolean resyncCustomItem(@Nonnull PlayerRef playerRef, @Nonnull CustomItemData customData) {
        return itemSyncManager.resyncCustomItem(playerRef, customData);
    }

    public boolean resyncGearItem(
            @Nonnull PlayerRef playerRef,
            @Nonnull ItemStack itemStack,
            @Nonnull GearData gearData) {
        return itemSyncManager.resyncGearItem(playerRef, itemStack, gearData);
    }

    // =========================================================================
    // INVENTORY HELPERS
    // =========================================================================

    /**
     * Collects all items from an inventory.
     *
     * @deprecated Use {@link GearUtils#collectAllInventoryItems(Inventory)} instead.
     */
    @Deprecated
    @Nonnull
    public static List<ItemStack> collectAllInventoryItems(@Nonnull Inventory inventory) {
        return GearUtils.collectAllInventoryItems(inventory);
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
