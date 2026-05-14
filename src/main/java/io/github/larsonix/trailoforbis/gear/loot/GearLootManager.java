package io.github.larsonix.trailoforbis.gear.loot;

import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.api.ServiceRegistry;
import io.github.larsonix.trailoforbis.attributes.AttributeManager;
import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig;
import io.github.larsonix.trailoforbis.gear.generation.GearGenerator;

import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
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

        // Loot listener
        lootListener = new LootListener(plugin, lootCalculator, lootGenerator);

        LOGGER.at(Level.INFO).log("GearLootManager initialized");
    }

    /**
     * Clears all references for GC.
     */
    public void shutdown() {
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
