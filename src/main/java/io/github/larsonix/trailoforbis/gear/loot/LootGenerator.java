package io.github.larsonix.trailoforbis.gear.loot;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.inventory.ItemStack;

import io.github.larsonix.trailoforbis.gear.generation.GearGenerator;
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.gear.util.GearUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Generates RPG gear items for loot drops.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Select appropriate base item type (weapon/armor)</li>
 *   <li>Delegate to GearGenerator for stat generation</li>
 *   <li>Apply gear metadata to ItemStack</li>
 * </ul>
 *
 * <h2>Item Discovery</h2>
 * <p>Uses {@link DynamicLootRegistry} to automatically discover weapons/armor
 * from Hytale's item registry, making the plugin compatible with any mod
 * that properly registers weapons/armor.
 *
 * <h2>Thread Safety</h2>
 * <p>This class is thread-safe when constructed with the default constructor
 * (uses ThreadLocalRandom). The underlying GearGenerator is also thread-safe.
 */
public final class LootGenerator {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /**
     * Equipment slot types that can drop as loot.
     *
     * <p>Matches Hytale's armor slots: Head, Chest, Legs, Hands (no feet slot).
     */
    public enum EquipmentSlot {
        WEAPON,
        HEAD,
        CHEST,
        LEGS,
        HANDS,
        OFF_HAND
    }

    private final DynamicLootRegistry dynamicRegistry;

    // Slot weights for random selection
    private final Map<EquipmentSlot, Integer> slotWeights;

    private final GearGenerator gearGenerator;
    private final Random random;

    // =========================================================================
    // CONSTRUCTORS - DYNAMIC REGISTRY (PREFERRED)
    // =========================================================================

    /**
     * Creates a LootGenerator using dynamic item discovery.
     *
     * <p>This is the preferred constructor for production use. It automatically
     * discovers droppable items from Hytale's item registry, making the plugin
     * compatible with any mod that properly registers weapons/armor.
     *
     * @param gearGenerator The gear generation system
     * @param registry      The dynamic loot registry (must have discoverItems() called)
     */
    public LootGenerator(@Nonnull GearGenerator gearGenerator, @Nonnull DynamicLootRegistry registry) {
        this(gearGenerator, registry, ThreadLocalRandom.current());
    }

    /**
     * Creates a LootGenerator using dynamic item discovery with custom random.
     *
     * @param gearGenerator The gear generation system
     * @param registry      The dynamic loot registry
     * @param random        The random number generator (for testing)
     */
    public LootGenerator(@Nonnull GearGenerator gearGenerator, @Nonnull DynamicLootRegistry registry, @Nonnull Random random) {
        this.gearGenerator = Objects.requireNonNull(gearGenerator, "gearGenerator cannot be null");
        this.dynamicRegistry = Objects.requireNonNull(registry, "registry cannot be null");
        this.random = Objects.requireNonNull(random, "random cannot be null");
        this.slotWeights = registry.getSlotWeights();
    }

    // =========================================================================
    // DROP GENERATION — RARITY-FIRST PIPELINE
    // =========================================================================
    //
    // The loot table is existence-gated: only (slot, category, rarity) combos
    // with real Hytale skins can be rolled. The pipeline is:
    //
    //   1. RARITY   → Roll independently (geometric weights + bonuses)
    //   2. SLOT     → Roll from slots that have items at this rarity
    //   3. CATEGORY → Roll from categories that have items at this (rarity, slot)
    //   4. SKIN     → Pick random from matching (rarity, slot, category) skins
    //   5. GENERATE → GearGenerator creates stats
    //
    // Every step is constrained to what exists. No invalid combinations.

    /**
     * Generates loot drops based on a loot roll result.
     *
     * @param lootRoll The calculated loot parameters
     * @return List of generated item drops (may be empty)
     */
    public List<ItemStack> generateDrops(LootCalculator.LootRoll lootRoll) {
        if (!lootRoll.shouldDrop()) {
            return List.of();
        }

        List<ItemStack> drops = new ArrayList<>();

        for (int i = 0; i < lootRoll.dropCount(); i++) {
            ItemStack drop = generateSingleDrop(lootRoll.itemLevel(), lootRoll.rarityBonus());
            if (drop != null) {
                drops.add(drop);
            }
        }

        LOGGER.atInfo().log("Generated %d loot drops at level %d with %.1f%% rarity bonus",
                drops.size(), lootRoll.itemLevel(), lootRoll.rarityBonus());

        return drops;
    }

    /**
     * Generates a single gear drop using the rarity-first pipeline.
     *
     * <p>Pipeline:
     * <ol>
     *   <li>Roll rarity (independent, geometric 4× weights + bonus)</li>
     *   <li>Select slot constrained to what has items at this rarity</li>
     *   <li>Select category constrained to what has items at this rarity + slot</li>
     *   <li>Select skin from the exact (slot, category, rarity) match</li>
     *   <li>Generate gear with the pre-rolled rarity</li>
     * </ol>
     *
     * <p>Only combinations that have a real Hytale skin can be rolled.
     * If a rarity has no items in any slot, this returns null.
     *
     * @param itemLevel The level of the gear
     * @param rarityBonus The rarity bonus to apply (percentage, e.g. 25.0 for +25%)
     * @return Generated ItemStack with gear data, or null on failure
     */
    public ItemStack generateSingleDrop(int itemLevel, double rarityBonus) {
        if (!dynamicRegistry.isDiscovered()) {
            LOGGER.atWarning().log("DynamicLootRegistry not yet discovered — cannot generate drop");
            return null;
        }
        return generateSingleDropDynamic(itemLevel, rarityBonus);
    }

    /**
     * Rarity-first pipeline using the availability matrix.
     */
    private ItemStack generateSingleDropDynamic(int itemLevel, double rarityBonus) {
        // 1. Roll RARITY — constrained to rarities that actually have items
        double decimalBonus = rarityBonus / 100.0;
        Set<GearRarity> availableRarities = dynamicRegistry.getAvailableRarities();
        if (availableRarities.isEmpty()) {
            LOGGER.atWarning().log("No rarities have items — cannot generate drop");
            return null;
        }
        GearRarity rarity = gearGenerator.getRarityRoller().roll(decimalBonus, availableRarities);

        // 2. Select SLOT from slots that have items at this rarity
        EquipmentSlot slot = selectSlotForRarity(rarity);
        if (slot == null) {
            LOGGER.atWarning().log("No slots available at rarity %s — skipping drop", rarity);
            return null;
        }

        // 3. Select CATEGORY from categories with items at this (rarity, slot)
        String category = selectCategoryForRaritySlot(rarity, slot);
        if (category == null) {
            LOGGER.atWarning().log("No categories for slot %s at rarity %s — skipping drop", slot, rarity);
            return null;
        }

        // 4. Select SKIN from the exact (slot, category, rarity) match
        String baseItemId = dynamicRegistry.selectSkin(slot, category, rarity);
        if (baseItemId == null) {
            LOGGER.atWarning().log("No skin for %s/%s at %s — skipping drop", slot, category, rarity);
            return null;
        }

        // 5. Create base item and generate gear
        ItemStack baseItem = createBaseItem(baseItemId);
        if (baseItem == null) {
            LOGGER.atWarning().log("Failed to create base item: %s", baseItemId);
            return null;
        }

        String slotString = mapSlotToString(slot);
        ItemStack gearItem = gearGenerator.generate(baseItem, itemLevel, slotString, rarity);

        // Log result
        Optional<GearData> gearData = GearUtils.readGearData(gearItem);
        if (gearData.isPresent()) {
            GearData data = gearData.get();
            int modCount = data.prefixes().size() + data.suffixes().size();
            LOGGER.atInfo().log("Generated RPG gear: %s %s [%s] (Lv%d, Q%d, %d mods)",
                    data.rarity(), baseItemId, category, data.level(), data.quality(), modCount);
        } else {
            LOGGER.atWarning().log("FAILED to apply gear data to %s - item has no RPG metadata!", baseItemId);
        }

        return gearItem;
    }

    // =========================================================================
    // CONSTRAINED SELECTION (rarity-first pipeline)
    // =========================================================================

    /**
     * Selects a random slot from slots that have items at the given rarity.
     * Weights from config are applied, but only to available slots.
     *
     * @param rarity The rolled rarity
     * @return A random available slot, or null if none exist
     */
    @Nullable
    private EquipmentSlot selectSlotForRarity(GearRarity rarity) {
        Set<EquipmentSlot> available = dynamicRegistry.getAvailableSlotsForRarity(rarity);
        if (available.isEmpty()) return null;

        // Build weighted pool from available slots only
        int totalWeight = 0;
        List<Map.Entry<EquipmentSlot, Integer>> pool = new ArrayList<>();
        for (Map.Entry<EquipmentSlot, Integer> entry : slotWeights.entrySet()) {
            if (available.contains(entry.getKey()) && entry.getValue() > 0) {
                pool.add(entry);
                totalWeight += entry.getValue();
            }
        }
        if (pool.isEmpty()) return null;

        int roll = random.nextInt(totalWeight);
        int cumulative = 0;
        for (Map.Entry<EquipmentSlot, Integer> entry : pool) {
            cumulative += entry.getValue();
            if (roll < cumulative) {
                return entry.getKey();
            }
        }
        return pool.getLast().getKey();
    }

    /**
     * Selects a random category from categories that have items at the given (rarity, slot).
     * Uses configured category weights (weapon or armor weights), only for available categories.
     *
     * @param rarity The rolled rarity
     * @param slot   The selected slot
     * @return A random available category name, or null if none exist
     */
    @Nullable
    private String selectCategoryForRaritySlot(GearRarity rarity, EquipmentSlot slot) {
        Set<String> available = dynamicRegistry.getAvailableCategoriesForRaritySlot(rarity, slot);
        if (available.isEmpty()) return null;

        Map<String, Double> weights = dynamicRegistry.getCategoryWeights(slot);

        // Build weighted pool — only categories that EXIST, not influenced by skin count
        double totalWeight = 0;
        List<Map.Entry<String, Double>> pool = new ArrayList<>();
        for (String cat : available) {
            double w = weights.getOrDefault(cat, 1.0);
            if (w > 0) {
                pool.add(Map.entry(cat, w));
                totalWeight += w;
            }
        }
        if (pool.isEmpty()) return null;

        double roll = random.nextDouble() * totalWeight;
        double cumulative = 0;
        for (Map.Entry<String, Double> entry : pool) {
            cumulative += entry.getValue();
            if (roll < cumulative) {
                return entry.getKey();
            }
        }
        return pool.getLast().getKey();
    }

    /**
     * Maps equipment slot to gear generator slot string.
     *
     * @param slot The equipment slot
     * @return The slot string for GearGenerator
     */
    private String mapSlotToString(EquipmentSlot slot) {
        return switch (slot) {
            case WEAPON -> "weapon";
            case HEAD -> "head";
            case CHEST -> "chest";
            case LEGS -> "legs";
            case HANDS -> "hands";
            case OFF_HAND -> "shield";
        };
    }

    /**
     * Creates a base ItemStack from an item ID.
     *
     * @param itemId The item ID (e.g., "Weapon_Sword_Iron")
     * @return The created ItemStack, or null on failure
     */
    ItemStack createBaseItem(String itemId) {
        try {
            // Use Hytale's ItemStack constructor
            return new ItemStack(itemId, 1);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to create item: %s", itemId);
            return null;
        }
    }

    // =========================================================================
    // BUILDER FOR CUSTOMIZATION
    // =========================================================================

    /**
     * Creates a builder for customized loot generation.
     *
     * @return A new drop builder
     */
    public DropBuilder drop() {
        return new DropBuilder();
    }

    /**
     * Builder for customized loot generation.
     *
     * <p>Uses the rarity-first pipeline: rarity → slot → category → skin.
     * Forced values bypass their respective selection step. If rarity is forced,
     * slot and category are constrained to what exists at that rarity.
     */
    public class DropBuilder {
        private int itemLevel = 1;
        private double rarityBonus = 0;
        private EquipmentSlot forcedSlot = null;
        private String forcedBaseItem = null;
        private GearRarity forcedRarity = null;

        /**
         * Sets the item level.
         *
         * @param level The item level (minimum 1)
         * @return This builder
         */
        public DropBuilder level(int level) {
            this.itemLevel = Math.max(1, level);
            return this;
        }

        /**
         * Sets the rarity bonus (percentage).
         *
         * @param bonus The rarity bonus percentage
         * @return This builder
         */
        public DropBuilder rarityBonus(double bonus) {
            this.rarityBonus = bonus;
            return this;
        }

        /**
         * Forces a specific equipment slot.
         *
         * @param slot The equipment slot
         * @return This builder
         */
        public DropBuilder slot(EquipmentSlot slot) {
            this.forcedSlot = slot;
            return this;
        }

        /**
         * Forces a specific base item (bypasses all selection — slot, category, skin).
         *
         * @param itemId The item ID
         * @return This builder
         */
        public DropBuilder baseItem(String itemId) {
            this.forcedBaseItem = itemId;
            return this;
        }

        /**
         * Forces a specific rarity.
         *
         * @param rarity The gear rarity
         * @return This builder
         */
        public DropBuilder rarity(GearRarity rarity) {
            this.forcedRarity = rarity;
            return this;
        }

        /**
         * Builds and returns the generated item using the rarity-first pipeline.
         *
         * <p>When base item is forced, skips the entire selection pipeline.
         * When only rarity is forced, slot and category are constrained to
         * what exists at that rarity.
         *
         * @return The generated ItemStack, or null on failure
         */
        public ItemStack build() {
            if (forcedBaseItem != null) {
                return buildWithForcedBaseItem();
            }
            return buildDynamic();
        }

        private ItemStack buildDynamic() {
            // 1. Determine rarity — constrained to available
            GearRarity effectiveRarity;
            if (forcedRarity != null) {
                effectiveRarity = forcedRarity;
            } else {
                Set<GearRarity> availableRarities = dynamicRegistry.getAvailableRarities();
                if (availableRarities.isEmpty()) return null;
                double decimalBonus = rarityBonus / 100.0;
                effectiveRarity = gearGenerator.getRarityRoller().roll(decimalBonus, availableRarities);
            }

            // 2. Select slot (forced or constrained by rarity)
            EquipmentSlot slot;
            if (forcedSlot != null) {
                slot = forcedSlot;
            } else {
                slot = selectSlotForRarity(effectiveRarity);
                if (slot == null) {
                    LOGGER.atWarning().log("DropBuilder: no slots at rarity %s", effectiveRarity);
                    return null;
                }
            }

            // 3. Select category (constrained by rarity + slot)
            String category = selectCategoryForRaritySlot(effectiveRarity, slot);
            if (category == null) {
                LOGGER.atWarning().log("DropBuilder: no categories for %s at %s", slot, effectiveRarity);
                return null;
            }

            // 4. Select skin
            String baseItemId = dynamicRegistry.selectSkin(slot, category, effectiveRarity);
            if (baseItemId == null) {
                LOGGER.atWarning().log("DropBuilder: no skin for %s/%s at %s", slot, category, effectiveRarity);
                return null;
            }

            // 5. Generate
            ItemStack baseItem = createBaseItem(baseItemId);
            if (baseItem == null) return null;

            String slotString = mapSlotToString(slot);
            return gearGenerator.generate(baseItem, itemLevel, slotString, effectiveRarity);
        }

        private ItemStack buildWithForcedBaseItem() {
            EquipmentSlot slot = forcedSlot != null ? forcedSlot : EquipmentSlot.WEAPON;

            GearRarity effectiveRarity;
            if (forcedRarity != null) {
                effectiveRarity = forcedRarity;
            } else {
                double decimalBonus = rarityBonus / 100.0;
                effectiveRarity = gearGenerator.getRarityRoller().roll(decimalBonus);
            }

            ItemStack baseItem = createBaseItem(forcedBaseItem);
            if (baseItem == null) return null;

            String slotString = mapSlotToString(slot);
            return gearGenerator.generate(baseItem, itemLevel, slotString, effectiveRarity);
        }
    }

    // =========================================================================
    // ACCESSORS
    // =========================================================================

    /**
     * Gets the gear generator.
     *
     * @return The gear generator
     */
    public GearGenerator getGearGenerator() {
        return gearGenerator;
    }

    /**
     * Gets the dynamic loot registry.
     */
    @Nonnull
    public DynamicLootRegistry getDynamicRegistry() {
        return dynamicRegistry;
    }

    /**
     * Gets the slot weights map.
     *
     * @return Unmodifiable view of slot weights
     */
    public Map<EquipmentSlot, Integer> getSlotWeights() {
        return Collections.unmodifiableMap(slotWeights);
    }
}
