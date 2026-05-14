package io.github.larsonix.trailoforbis.loot.consumable;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.BlockFlags;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;

import io.github.larsonix.trailoforbis.gear.model.GearRarity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Registry of consumable items (food and potions) organized by rarity tier.
 *
 * <p>Loads curated item pools from {@link ConsumableLootConfig} and provides
 * level-filtered item selection. Uses the same {@link GearRarity} tiers as
 * the gear/stone/map loot systems for consistent drop weighting.
 *
 * <h2>Level Gating</h2>
 * <p>Each tier has a minimum player level. When selecting items for a player,
 * only tiers at or below the player's level are available. If a rarity roll
 * produces a tier above the player's level, the registry falls back to the
 * highest available tier.
 *
 * <h2>Thread Safety</h2>
 * <p>Immutable after construction. Uses ThreadLocalRandom for item selection.
 */
public final class ConsumableLootRegistry {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Rarity keys in config mapped to GearRarity ordinals */
    private static final Map<String, GearRarity> RARITY_MAP = Map.of(
        "common", GearRarity.COMMON,
        "uncommon", GearRarity.UNCOMMON,
        "rare", GearRarity.RARE,
        "epic", GearRarity.EPIC,
        "legendary", GearRarity.LEGENDARY
    );

    private final Map<GearRarity, List<ConsumableEntry>> foodByTier;
    private final Map<GearRarity, List<ConsumableEntry>> potionByTier;
    private final boolean enabled;

    /**
     * Creates a new registry from the given config.
     *
     * @param config The consumable loot configuration
     */
    public ConsumableLootRegistry(@Nonnull ConsumableLootConfig config) {
        this.enabled = config.isEnabled();

        Set<String> excluded = new HashSet<>(config.getExcludedItems());

        Map<GearRarity, List<ConsumableEntry>> rawFood = buildTierMap(config.getFoodTiers(), excluded, "food");
        Map<GearRarity, List<ConsumableEntry>> rawPotions = buildTierMap(config.getPotionTiers(), excluded, "potion");

        this.foodByTier = validateAndFilter(rawFood, "food");
        this.potionByTier = validateAndFilter(rawPotions, "potion");

        int totalFood = foodByTier.values().stream().mapToInt(List::size).sum();
        int totalPotions = potionByTier.values().stream().mapToInt(List::size).sum();
        LOGGER.atInfo().log("ConsumableLootRegistry loaded: %d food items, %d potion items (validated against asset map)",
            totalFood, totalPotions);
    }

    /**
     * Builds an immutable tier map from config tier lists.
     */
    @Nonnull
    private static Map<GearRarity, List<ConsumableEntry>> buildTierMap(
            @Nonnull Map<String, ConsumableLootConfig.TierList> configTiers,
            @Nonnull Set<String> excluded,
            @Nonnull String category) {

        Map<GearRarity, List<ConsumableEntry>> result = new EnumMap<>(GearRarity.class);

        for (Map.Entry<String, ConsumableLootConfig.TierList> entry : configTiers.entrySet()) {
            GearRarity rarity = RARITY_MAP.get(entry.getKey().toLowerCase());
            if (rarity == null) {
                LOGGER.atWarning().log("Unknown rarity key '%s' in %s tiers, skipping", entry.getKey(), category);
                continue;
            }

            ConsumableLootConfig.TierList tierList = entry.getValue();
            List<ConsumableEntry> entries = new ArrayList<>();

            for (ConsumableLootConfig.ItemEntry item : tierList.getItems()) {
                String itemId = item.getId();
                if (itemId.isEmpty()) continue;
                if (excluded.contains(itemId)) {
                    LOGGER.atFine().log("Excluded %s item: %s", category, itemId);
                    continue;
                }

                entries.add(new ConsumableEntry(
                    itemId,
                    item.getMinStack(),
                    item.getMaxStack(),
                    tierList.getMinLevel()
                ));
            }

            if (!entries.isEmpty()) {
                result.put(rarity, List.copyOf(entries));
                LOGGER.atFine().log("Loaded %d %s items for %s tier (minLevel=%d)",
                    entries.size(), category, rarity, tierList.getMinLevel());
            }
        }

        return Collections.unmodifiableMap(result);
    }

    /**
     * Validates each item in the tier map against the Hytale asset map.
     * Removes items that don't exist, aren't consumable, or have a block type
     * missing the {@code IsUsable} flag (broken BlockType inheritance).
     */
    @Nonnull
    private static Map<GearRarity, List<ConsumableEntry>> validateAndFilter(
            @Nonnull Map<GearRarity, List<ConsumableEntry>> tierMap,
            @Nonnull String category) {

        Item testItem;
        try {
            testItem = Item.getAssetMap().getAsset("Potion_Health");
        } catch (Exception e) {
            LOGGER.atSevere().log("Item asset map not available — %s consumable loot DISABLED (0 items). " +
                "This means the plugin initialized before Hytale loaded items.", category);
            return Collections.emptyMap();
        }

        if (testItem == null) {
            LOGGER.atSevere().log("Item asset map appears empty — %s consumable loot DISABLED (0 items). " +
                "Potion_Health not found; Hytale may not have finished loading.", category);
            return Collections.emptyMap();
        }

        Map<GearRarity, List<ConsumableEntry>> validated = new EnumMap<>(GearRarity.class);
        int removedCount = 0;

        for (Map.Entry<GearRarity, List<ConsumableEntry>> entry : tierMap.entrySet()) {
            List<ConsumableEntry> validEntries = new ArrayList<>();

            for (ConsumableEntry ce : entry.getValue()) {
                String reason = validateItem(ce.itemId());
                if (reason != null) {
                    LOGGER.atWarning().log("Removed %s item '%s' from %s tier: %s",
                        category, ce.itemId(), entry.getKey(), reason);
                    removedCount++;
                    continue;
                }
                validEntries.add(ce);
            }

            if (!validEntries.isEmpty()) {
                validated.put(entry.getKey(), List.copyOf(validEntries));
            }
        }

        if (removedCount > 0) {
            LOGGER.atWarning().log("Validation removed %d invalid %s item(s) from loot pools", removedCount, category);
        }

        return Collections.unmodifiableMap(validated);
    }

    /**
     * Validates a single item ID against the asset map.
     *
     * @return null if valid, or a reason string if the item should be excluded
     */
    @Nullable
    private static String validateItem(@Nonnull String itemId) {
        Item item = Item.getAssetMap().getAsset(itemId);

        if (item == null) {
            return "item not found in asset map";
        }

        if (!item.isConsumable()) {
            return "not marked as consumable";
        }

        // Check block type flags — items whose BlockType lost IsUsable through
        // inheritance act as placeable blocks instead of consumables
        if (item.hasBlockType()) {
            String blockId = item.getBlockId();
            if (blockId != null) {
                BlockType blockType = (BlockType) BlockType.getAssetMap().getAsset(blockId);
                if (blockType != null) {
                    BlockFlags flags = blockType.getFlags();
                    if (flags == null || !flags.isUsable) {
                        return "block type missing IsUsable flag (broken inheritance — item acts as deco block)";
                    }
                }
            }
        }

        return null;
    }

    // =========================================================================
    // QUERIES
    // =========================================================================

    /**
     * Gets food items available at the given rarity and player level.
     *
     * @param rarity      The target rarity tier
     * @param playerLevel The player's current level
     * @return List of matching entries, or empty if none qualify
     */
    @Nonnull
    public List<ConsumableEntry> getFood(@Nonnull GearRarity rarity, int playerLevel) {
        return getFiltered(foodByTier, rarity, playerLevel);
    }

    /**
     * Gets potion items available at the given rarity and player level.
     *
     * @param rarity      The target rarity tier
     * @param playerLevel The player's current level
     * @return List of matching entries, or empty if none qualify
     */
    @Nonnull
    public List<ConsumableEntry> getPotions(@Nonnull GearRarity rarity, int playerLevel) {
        return getFiltered(potionByTier, rarity, playerLevel);
    }

    /**
     * Gets items at the given rarity, falling back to lower tiers if
     * the player's level is too low for the rolled rarity.
     */
    @Nonnull
    private List<ConsumableEntry> getFiltered(
            @Nonnull Map<GearRarity, List<ConsumableEntry>> tierMap,
            @Nonnull GearRarity rarity,
            int playerLevel) {

        // Try exact rarity first
        List<ConsumableEntry> entries = tierMap.get(rarity);
        if (entries != null && !entries.isEmpty()) {
            // Check if player level qualifies (all entries in a tier share the same minLevel)
            if (entries.get(0).minLevel() <= playerLevel) {
                return entries;
            }
        }

        // Fallback: try each lower rarity until we find one the player qualifies for
        GearRarity[] rarities = GearRarity.values();
        for (int i = rarity.ordinal() - 1; i >= 0; i--) {
            entries = tierMap.get(rarities[i]);
            if (entries != null && !entries.isEmpty() && entries.get(0).minLevel() <= playerLevel) {
                return entries;
            }
        }

        return List.of();
    }

    /**
     * Creates an ItemStack from a consumable entry with a random stack size.
     *
     * @param entry  The consumable entry
     * @param random The random source
     * @return The created ItemStack
     */
    @Nonnull
    public ItemStack generateItem(@Nonnull ConsumableEntry entry, @Nonnull Random random) {
        int stackSize = entry.minStack() == entry.maxStack()
            ? entry.minStack()
            : entry.minStack() + random.nextInt(entry.maxStack() - entry.minStack() + 1);
        return new ItemStack(entry.itemId(), stackSize);
    }

    /**
     * Selects a random consumable item (food or potion) for the given parameters.
     *
     * @param rarity      The target rarity
     * @param playerLevel The player's level
     * @param foodWeight  Weight for food selection (0.0-1.0)
     * @param potionWeight Weight for potion selection (0.0-1.0)
     * @param random      The random source
     * @return A generated ItemStack, or null if no items available
     */
    @Nullable
    public ItemStack rollConsumable(
            @Nonnull GearRarity rarity,
            int playerLevel,
            double foodWeight,
            double potionWeight,
            @Nonnull Random random) {

        List<ConsumableEntry> food = getFood(rarity, playerLevel);
        List<ConsumableEntry> potions = getPotions(rarity, playerLevel);

        if (food.isEmpty() && potions.isEmpty()) {
            return null;
        }

        // Choose food vs potion by weight
        boolean chooseFood;
        if (food.isEmpty()) {
            chooseFood = false;
        } else if (potions.isEmpty()) {
            chooseFood = true;
        } else {
            double totalWeight = foodWeight + potionWeight;
            chooseFood = random.nextDouble() * totalWeight < foodWeight;
        }

        List<ConsumableEntry> pool = chooseFood ? food : potions;
        ConsumableEntry entry = pool.get(random.nextInt(pool.size()));
        return generateItem(entry, random);
    }

    // =========================================================================
    // ACCESSORS
    // =========================================================================

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Whether this registry has any items loaded.
     */
    public boolean hasItems() {
        return !foodByTier.isEmpty() || !potionByTier.isEmpty();
    }
}
