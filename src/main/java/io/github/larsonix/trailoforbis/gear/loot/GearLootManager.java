package io.github.larsonix.trailoforbis.gear.loot;

import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.api.ServiceRegistry;
import io.github.larsonix.trailoforbis.attributes.AttributeManager;
import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig;
import io.github.larsonix.trailoforbis.gear.generation.GearGenerator;
import io.github.larsonix.trailoforbis.gear.item.ItemRegistryService;
import io.github.larsonix.trailoforbis.gear.item.ItemWorldSyncService;

import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.logging.Level;

/**
 * Manages the loot generation subsystem.
 *
 * <p>Owns: loot settings, rarity bonus calculation, loot calculation,
 * loot generation, dynamic item registry discovery, and drop level blending.
 *
 * <p>Owned by {@link io.github.larsonix.trailoforbis.gear.GearManager} (facade).
 */
public final class GearLootManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private LootSettings lootSettings;
    private RarityBonusCalculator rarityBonusCalculator;
    private LootCalculator lootCalculator;
    private LootGenerator lootGenerator;
    private LootListener lootListener;
    private DynamicLootRegistry dynamicLootRegistry;
    private DropLevelBlender dropLevelBlender;
    private DeferredLootPipeline lootPipeline;

    /**
     * Initializes the loot system.
     *
     * @param balanceConfig gear balance config
     * @param gearGenerator the gear generator (for loot generation)
     * @param plugin plugin instance
     */
    public void initialize(
            @Nonnull GearBalanceConfig balanceConfig,
            @Nonnull GearGenerator gearGenerator,
            @Nonnull TrailOfOrbis plugin) {
        initialize(balanceConfig, gearGenerator, plugin, null, null);
    }

    /**
     * Initializes the loot system with deferred pipeline support.
     *
     * @param balanceConfig gear balance config
     * @param gearGenerator the gear generator (for loot generation)
     * @param plugin plugin instance
     * @param itemRegistry item registry for batch registration (nullable — disables pipeline)
     * @param worldSyncService world sync service for batch item sync (nullable — disables pipeline)
     */
    public void initialize(
            @Nonnull GearBalanceConfig balanceConfig,
            @Nonnull GearGenerator gearGenerator,
            @Nonnull TrailOfOrbis plugin,
            @Nullable ItemRegistryService itemRegistry,
            @Nullable ItemWorldSyncService worldSyncService) {

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

        // Build loot category config from discovery config (implicit-driven pipeline)
        LootCategoryConfig categoryConfig = discoveryConfig.buildCategoryConfig();

        // Loot generator using dynamic registry + category pipeline
        lootGenerator = new LootGenerator(gearGenerator, dynamicLootRegistry, categoryConfig);

        // Deferred loot pipeline — moves generation off the death tick
        if (itemRegistry != null && worldSyncService != null) {
            lootPipeline = new DeferredLootPipeline(
                    lootCalculator, lootGenerator, itemRegistry, worldSyncService);
            LOGGER.at(Level.INFO).log("Deferred loot pipeline enabled");
        } else {
            LOGGER.at(Level.WARNING).log(
                    "Deferred loot pipeline disabled (missing dependencies). " +
                    "Loot generation will run synchronously on death tick.");
        }

        // Loot listener (requires pipeline)
        lootListener = new LootListener(plugin, lootCalculator, lootGenerator, lootPipeline);

        LOGGER.at(Level.INFO).log("GearLootManager initialized");
    }

    /**
     * Shuts down the loot system.
     */
    public void shutdown() {
        if (lootPipeline != null) {
            lootPipeline.shutdown();
            lootPipeline = null;
        }
        lootSettings = null;
        rarityBonusCalculator = null;
        lootCalculator = null;
        lootGenerator = null;
        lootListener = null;
        dynamicLootRegistry = null;
        dropLevelBlender = null;
    }

    // =========================================================================
    // ACCESSORS
    // =========================================================================

    @Nonnull
    public LootSettings getLootSettings() {
        return lootSettings;
    }

    @Nonnull
    public RarityBonusCalculator getRarityBonusCalculator() {
        return rarityBonusCalculator;
    }

    @Nonnull
    public LootCalculator getLootCalculator() {
        return lootCalculator;
    }

    @Nonnull
    public LootGenerator getLootGenerator() {
        return lootGenerator;
    }

    @Nonnull
    public LootListener getLootListener() {
        return lootListener;
    }

    @Nonnull
    public DynamicLootRegistry getDynamicLootRegistry() {
        return dynamicLootRegistry;
    }

    @Nonnull
    public DropLevelBlender getDropLevelBlender() {
        return dropLevelBlender;
    }
}
