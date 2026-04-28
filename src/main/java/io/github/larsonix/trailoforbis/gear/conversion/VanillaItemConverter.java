package io.github.larsonix.trailoforbis.gear.conversion;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import io.github.larsonix.trailoforbis.gear.generation.GearGenerator;
import io.github.larsonix.trailoforbis.gear.generation.RarityRoller;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.gear.util.GearUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.Optional;

/**
 * Converts vanilla Hytale weapons and armor into RPG gear.
 *
 * <p>This is the main service that coordinates the conversion process:
 * <ol>
 *   <li>Checks if the item is already RPG gear (skip)</li>
 *   <li>Classifies the item as weapon/armor (skip if neither)</li>
 *   <li>Checks whitelist/blacklist (skip if filtered)</li>
 *   <li>Extracts material to determine max rarity</li>
 *   <li>Rolls rarity (capped at material max)</li>
 *   <li>Generates RPG gear using GearGenerator</li>
 * </ol>
 *
 * <p>This class is thread-safe if the underlying GearGenerator is thread-safe.
 */
public final class VanillaItemConverter {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final VanillaConversionConfig config;
    private final ItemClassifier itemClassifier;
    private final MaterialTierMapper materialMapper;
    private final GearGenerator gearGenerator;
    private final RarityRoller rarityRoller;

    /**
     * Creates a new VanillaItemConverter.
     */
    public VanillaItemConverter(
            @Nonnull VanillaConversionConfig config,
            @Nonnull GearGenerator gearGenerator,
            @Nonnull RarityRoller rarityRoller) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.gearGenerator = Objects.requireNonNull(gearGenerator, "gearGenerator cannot be null");
        this.rarityRoller = Objects.requireNonNull(rarityRoller, "rarityRoller cannot be null");

        this.itemClassifier = new ItemClassifier(config);
        this.materialMapper = new MaterialTierMapper(config);
    }

    /**
     * Attempts to convert a vanilla item to RPG gear.
     *
     * @param itemStack The item to convert
     * @param level The item level to use
     * @param source The acquisition source (for rarity bonus)
     * @return The converted RPG gear, or empty if the item should not be converted
     */
    @Nonnull
    public Optional<ItemStack> convert(
            @Nullable ItemStack itemStack,
            int level,
            @Nonnull AcquisitionSource source) {

        if (itemStack == null) {
            return Optional.empty();
        }

        // 1. Skip if already RPG gear
        if (GearUtils.isRpgGear(itemStack)) {
            LOGGER.atFine().log("Skipping conversion: item '%s' is already RPG gear",
                itemStack.getItemId());
            return Optional.empty();
        }

        String itemId = itemStack.getItemId();

        // 2. Classify the item
        ItemClassifier.Classification classification = itemClassifier.classify(itemStack);
        if (!classification.isConvertible()) {
            LOGGER.atFine().log("Skipping conversion: item '%s' is not a weapon or armor", itemId);
            return Optional.empty();
        }

        // 3. Check whitelist/blacklist
        if (!itemClassifier.isAllowedByConfig(itemId)) {
            LOGGER.atFine().log("Skipping conversion: item '%s' filtered by config", itemId);
            return Optional.empty();
        }

        // 4. Get max rarity from material
        GearRarity maxRarity = materialMapper.getMaxRarity(itemId);

        // 5. Roll rarity with source-specific bonus, capped at material max
        double rarityBonus = getRarityBonus(source);
        GearRarity rolledRarity = rarityRoller.roll(rarityBonus);
        GearRarity finalRarity = materialMapper.capRarity(rolledRarity, maxRarity);

        // 6. Clamp level to config bounds
        int clampedLevel = config.getLevelCalculation().clampLevel(level);

        // 7. Generate RPG gear
        String slot = classification.slot();
        ItemStack rpgGear = gearGenerator.generate(itemStack, clampedLevel, slot, finalRarity);

        LOGGER.atInfo().log("Converted vanilla item '%s' to RPG gear: level=%d, slot=%s, rarity=%s (rolled %s, max %s)",
            itemId, clampedLevel, slot, finalRarity, rolledRarity, maxRarity);

        return Optional.of(rpgGear);
    }

    /**
     * Convenience method for mob drop conversion.
     *
     * @param itemStack The dropped item
     * @param mobLevel The mob's level
     * @return The converted RPG gear, or empty if not converted
     */
    @Nonnull
    public Optional<ItemStack> convertMobDrop(@Nullable ItemStack itemStack, int mobLevel) {
        if (!config.getSources().isMobDrops()) {
            return Optional.empty();
        }
        return convert(itemStack, mobLevel, AcquisitionSource.MOB_DROP);
    }

    /**
     * Convenience method for chest loot conversion.
     *
     * @param itemStack The chest item
     * @param estimatedLevel The estimated level based on distance from spawn
     * @return The converted RPG gear, or empty if not converted
     */
    @Nonnull
    public Optional<ItemStack> convertChestLoot(@Nullable ItemStack itemStack, int estimatedLevel) {
        if (!config.getSources().isChestLoot()) {
            return Optional.empty();
        }
        return convert(itemStack, estimatedLevel, AcquisitionSource.CHEST_LOOT);
    }

    /**
     * Convenience method for crafting conversion.
     *
     * @param itemStack The crafted item
     * @param playerLevel The player's RPG level
     * @return The converted RPG gear, or empty if not converted
     */
    @Nonnull
    public Optional<ItemStack> convertCrafted(@Nullable ItemStack itemStack, int playerLevel) {
        if (!config.getSources().isCrafting()) {
            return Optional.empty();
        }
        int adjustedLevel = playerLevel + config.getLevelCalculation().getCraftingLevelOffset();
        return convert(itemStack, adjustedLevel, AcquisitionSource.CRAFTING);
    }

    /**
     * Checks if conversion is enabled globally.
     */
    public boolean isEnabled() {
        return config.isEnabled();
    }

    /**
     * Checks if conversion for a specific source is enabled.
     *
     * @param source The acquisition source to check
     * @return true if conversion is enabled for this source
     */
    public boolean isSourceEnabled(@Nonnull AcquisitionSource source) {
        if (!config.isEnabled()) {
            return false;
        }
        return switch (source) {
            case MOB_DROP -> config.getSources().isMobDrops();
            case CHEST_LOOT -> config.getSources().isChestLoot();
            case CRAFTING -> config.getSources().isCrafting();
        };
    }

    private double getRarityBonus(@Nonnull AcquisitionSource source) {
        VanillaConversionConfig.RarityBonusConfig bonusConfig = config.getRarityBonus();
        return switch (source) {
            case MOB_DROP -> bonusConfig.getMobDrops();
            case CHEST_LOOT -> bonusConfig.getChestLoot();
            case CRAFTING -> bonusConfig.getCrafting();
        };
    }

    // ==================== Getters ====================

    @Nonnull
    public ItemClassifier getItemClassifier() {
        return itemClassifier;
    }

    @Nonnull
    public MaterialTierMapper getMaterialMapper() {
        return materialMapper;
    }

    @Nonnull
    public VanillaConversionConfig getConfig() {
        return config;
    }

    // ==================== Acquisition Source ====================

    /**
     * The source from which an item was acquired.
     *
     * <p>Different sources may have different rarity bonuses and level calculations.
     */
    public enum AcquisitionSource {
        /** Item dropped by a killed mob */
        MOB_DROP,
        /** Item found in a treasure chest */
        CHEST_LOOT,
        /** Item created through crafting */
        CRAFTING
    }
}
