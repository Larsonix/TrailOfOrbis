package io.github.larsonix.trailoforbis.gear.core;

import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.attributes.AttributeManager;
import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig;
import io.github.larsonix.trailoforbis.gear.config.GearConfigLoader;
import io.github.larsonix.trailoforbis.gear.config.ModifierConfig;
import io.github.larsonix.trailoforbis.gear.equipment.EquipmentFeedback;
import io.github.larsonix.trailoforbis.gear.equipment.EquipmentListener;
import io.github.larsonix.trailoforbis.gear.equipment.EquipmentValidator;
import io.github.larsonix.trailoforbis.gear.equipment.RequirementCalculator;
import io.github.larsonix.trailoforbis.gear.generation.GearGenerator;
import io.github.larsonix.trailoforbis.gear.item.ItemDisplayNameService;
import io.github.larsonix.trailoforbis.gear.item.ItemRegistryService;
import io.github.larsonix.trailoforbis.gear.migration.ItemMigrationService;
import io.github.larsonix.trailoforbis.gear.stats.GearBonusProvider;
import io.github.larsonix.trailoforbis.gear.stats.GearStatApplier;
import io.github.larsonix.trailoforbis.gear.stats.GearStatCalculator;
import io.github.larsonix.trailoforbis.gear.tooltip.ItemNameFormatter;
import io.github.larsonix.trailoforbis.gear.tooltip.RichTooltipFormatter;
import io.github.larsonix.trailoforbis.gear.tooltip.TooltipConfig;
import io.github.larsonix.trailoforbis.gear.vanilla.VanillaWeaponDiscovery;
import io.github.larsonix.trailoforbis.gear.vanilla.VanillaWeaponProfile;

import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.logging.Level;

/**
 * Core gear module: generation, validation, stats, tooltips, migration, and vanilla weapon discovery.
 *
 * <p>This is the domain logic of the gear system. It does not own item sync,
 * loot, or conversion — those live in separate sub-managers.
 *
 * <p>Owned by {@link io.github.larsonix.trailoforbis.gear.GearManager} (facade).
 */
public final class GearCoreModule {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // Generation
    private GearGenerator gearGenerator;

    // Validation
    private RequirementCalculator requirementCalculator;
    private EquipmentValidator equipmentValidator;
    private EquipmentListener equipmentListener;

    // Stats
    private GearStatCalculator statCalculator;
    private GearStatApplier statApplier;

    // Tooltips
    private TooltipConfig tooltipConfig;
    private RichTooltipFormatter richTooltipFormatter;
    private ItemNameFormatter itemNameFormatter;
    private ItemDisplayNameService itemDisplayNameService;

    // Vanilla weapon profiles
    private VanillaWeaponDiscovery vanillaWeaponDiscovery;

    // Migration
    private ItemMigrationService itemMigrationService;

    /**
     * Initializes all core gear components.
     *
     * <p>Must be called after ItemRegistryService is initialized.
     *
     * @param balanceConfig gear balance tuning
     * @param modifierConfig stat modifier definitions
     * @param configLoader config loader (provides EquipmentStatConfig and TooltipConfig)
     * @param attributeManager attribute manager (for validation and tooltip coloring)
     * @param itemRegistryService shared item registry (for server-side validation)
     * @param bypassPredicate predicate for Creative mode requirement bypass
     * @param plugin plugin instance (for config access)
     */
    public void initialize(
            @Nonnull GearBalanceConfig balanceConfig,
            @Nonnull ModifierConfig modifierConfig,
            @Nonnull GearConfigLoader configLoader,
            @Nonnull AttributeManager attributeManager,
            @Nonnull ItemRegistryService itemRegistryService,
            @Nonnull Predicate<UUID> bypassPredicate,
            @Nonnull TrailOfOrbis plugin) {

        var equipmentStatConfig = configLoader.getEquipmentStatConfig();

        // Gear generation
        gearGenerator = new GearGenerator(balanceConfig, modifierConfig,
                equipmentStatConfig, itemRegistryService);

        // Item migration (validates + fixes stale gear on player login)
        itemMigrationService = new ItemMigrationService(
                modifierConfig, balanceConfig, equipmentStatConfig);
        itemMigrationService.setItemRegistryService(itemRegistryService);
        itemMigrationService.setGearGenerator(gearGenerator);

        // Requirements
        requirementCalculator = new RequirementCalculator(balanceConfig, modifierConfig);

        // Equipment validation
        equipmentValidator = new EquipmentValidator(
                requirementCalculator, attributeManager, bypassPredicate);

        // Equipment listener (armor slot filters + requirement validation)
        equipmentListener = new EquipmentListener(equipmentValidator, new EquipmentFeedback());

        // Stat calculation
        statCalculator = new GearStatCalculator(balanceConfig, equipmentValidator, itemRegistryService);

        // Stat application
        statApplier = new GearStatApplier();

        // Vanilla weapon discovery
        vanillaWeaponDiscovery = new VanillaWeaponDiscovery(balanceConfig.vanillaWeaponProfiles());
        vanillaWeaponDiscovery.discoverAll();

        // Rich tooltip formatting (Message API)
        tooltipConfig = configLoader.getTooltipConfig();
        richTooltipFormatter = new RichTooltipFormatter(
                modifierConfig, balanceConfig, requirementCalculator, attributeManager, tooltipConfig);
        itemNameFormatter = new ItemNameFormatter(
                modifierConfig,
                tooltipConfig.includePrefix(),
                tooltipConfig.includeSuffix(),
                tooltipConfig.boldThreshold());

        // Item display name service
        itemDisplayNameService = new ItemDisplayNameService(modifierConfig);

        LOGGER.at(Level.INFO).log("GearCoreModule initialized");
    }

    /**
     * Clears all component references for GC.
     */
    public void shutdown() {
        gearGenerator = null;
        requirementCalculator = null;
        equipmentValidator = null;
        equipmentListener = null;
        statCalculator = null;
        statApplier = null;
        tooltipConfig = null;
        richTooltipFormatter = null;
        itemNameFormatter = null;
        itemDisplayNameService = null;
        vanillaWeaponDiscovery = null;
        itemMigrationService = null;
    }

    /**
     * Creates a GearBonusProvider wired to this module's stat calculator/applier.
     */
    @Nonnull
    public GearBonusProvider createGearBonusProvider() {
        return new GearBonusProvider(statCalculator, statApplier);
    }

    // =========================================================================
    // ACCESSORS
    // =========================================================================

    @Nonnull
    public GearGenerator getGearGenerator() {
        return gearGenerator;
    }

    @Nonnull
    public RequirementCalculator getRequirementCalculator() {
        return requirementCalculator;
    }

    @Nonnull
    public EquipmentValidator getEquipmentValidator() {
        return equipmentValidator;
    }

    @Nonnull
    public EquipmentListener getEquipmentListener() {
        return equipmentListener;
    }

    @Nonnull
    public GearStatCalculator getStatCalculator() {
        return statCalculator;
    }

    @Nonnull
    public GearStatApplier getStatApplier() {
        return statApplier;
    }

    @Nonnull
    public TooltipConfig getTooltipConfig() {
        return tooltipConfig;
    }

    @Nonnull
    public RichTooltipFormatter getRichTooltipFormatter() {
        return richTooltipFormatter;
    }

    @Nonnull
    public ItemNameFormatter getItemNameFormatter() {
        return itemNameFormatter;
    }

    @Nonnull
    public ItemDisplayNameService getItemDisplayNameService() {
        return itemDisplayNameService;
    }

    @Nonnull
    public VanillaWeaponDiscovery getVanillaWeaponDiscovery() {
        return vanillaWeaponDiscovery;
    }

    @Nullable
    public VanillaWeaponProfile getVanillaWeaponProfile(@Nullable String itemId) {
        if (itemId == null || vanillaWeaponDiscovery == null) {
            return null;
        }
        return vanillaWeaponDiscovery.getProfile(itemId);
    }

    @Nonnull
    public ItemMigrationService getItemMigrationService() {
        return itemMigrationService;
    }
}
