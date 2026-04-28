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
 * <p>This generator supports two modes:
 * <ul>
 *   <li><b>Dynamic discovery</b> (preferred): Uses {@link DynamicLootRegistry} to
 *       automatically discover weapons/armor from Hytale's item registry</li>
 *   <li><b>Static config</b> (legacy): Uses {@link LootItemsConfig} with hardcoded
 *       item IDs from loot-items.yml</li>
 * </ul>
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

    // Dynamic registry (new system)
    @Nullable
    private final DynamicLootRegistry dynamicRegistry;

    // Static config (legacy fallback)
    @Nullable
    private final LootItemsConfig staticConfig;

    // Base item IDs per slot (from static config, only used if dynamicRegistry is null)
    private final Map<EquipmentSlot, List<String>> baseItems;

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

        // No static config when using dynamic registry
        this.staticConfig = null;
        this.baseItems = Collections.emptyMap();

        // Get slot weights from registry config
        this.slotWeights = registry.getSlotWeights();
    }

    // =========================================================================
    // CONSTRUCTORS - STATIC CONFIG (LEGACY)
    // =========================================================================

    /**
     * Creates a LootGenerator with default static config and ThreadLocalRandom.
     *
     * @param gearGenerator The gear generation system
     * @deprecated Use constructor with DynamicLootRegistry for automatic mod compatibility
     */
    @Deprecated
    public LootGenerator(GearGenerator gearGenerator) {
        this(gearGenerator, LootItemsConfig.createDefaults(), ThreadLocalRandom.current());
    }

    /**
     * Creates a LootGenerator with custom static config and ThreadLocalRandom.
     *
     * @param gearGenerator The gear generation system
     * @param config The loot items configuration
     * @deprecated Use constructor with DynamicLootRegistry for automatic mod compatibility
     */
    @Deprecated
    public LootGenerator(GearGenerator gearGenerator, LootItemsConfig config) {
        this(gearGenerator, config, ThreadLocalRandom.current());
    }

    /**
     * Creates a LootGenerator with custom random (for testing).
     *
     * @param gearGenerator The gear generation system
     * @param random The random number generator
     * @deprecated Use constructor with DynamicLootRegistry for automatic mod compatibility
     */
    @Deprecated
    public LootGenerator(GearGenerator gearGenerator, Random random) {
        this(gearGenerator, LootItemsConfig.createDefaults(), random);
    }

    /**
     * Creates a LootGenerator with custom static config and random (for testing).
     *
     * @param gearGenerator The gear generation system
     * @param config The loot items configuration
     * @param random The random number generator
     * @deprecated Use constructor with DynamicLootRegistry for automatic mod compatibility
     */
    @Deprecated
    public LootGenerator(GearGenerator gearGenerator, LootItemsConfig config, Random random) {
        this.gearGenerator = Objects.requireNonNull(gearGenerator, "gearGenerator cannot be null");
        this.staticConfig = Objects.requireNonNull(config, "config cannot be null");
        this.random = Objects.requireNonNull(random, "random cannot be null");

        // No dynamic registry when using static config
        this.dynamicRegistry = null;

        // Build slot maps from static config
        this.baseItems = buildBaseItemsMap(config);
        this.slotWeights = buildSlotWeightsMap(config);
    }

    /**
     * Builds the base items map from static config.
     */
    private static Map<EquipmentSlot, List<String>> buildBaseItemsMap(LootItemsConfig config) {
        Map<EquipmentSlot, List<String>> map = new EnumMap<>(EquipmentSlot.class);

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            String slotName = slot.name().toLowerCase();
            List<String> items = config.getItemsForSlot(slotName);
            if (!items.isEmpty()) {
                map.put(slot, items);
            }
        }

        return map;
    }

    /**
     * Builds the slot weights map from static config.
     */
    private static Map<EquipmentSlot, Integer> buildSlotWeightsMap(LootItemsConfig config) {
        Map<EquipmentSlot, Integer> map = new EnumMap<>(EquipmentSlot.class);

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            String slotName = slot.name().toLowerCase();
            int weight = config.getWeightForSlot(slotName);
            if (weight > 0) {
                map.put(slot, weight);
            }
        }

        return map;
    }

    // =========================================================================
    // DROP GENERATION
    // =========================================================================

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
     * Generates a single gear drop.
     *
     * <p>Rarity is rolled FIRST, then used to filter the base item skin pool
     * by Hytale quality tier. This ensures visual identity per rarity:
     * Common RPG items look like crude/wood weapons, Epic items look like
     * adamantite, etc.
     *
     * @param itemLevel The level of the gear
     * @param rarityBonus The rarity bonus to apply (percentage)
     * @return Generated ItemStack with gear data, or null on failure
     */
    public ItemStack generateSingleDrop(int itemLevel, double rarityBonus) {
        // 1. Select equipment slot
        EquipmentSlot slot = selectRandomSlot();

        // 2. Roll rarity FIRST — needed for quality-filtered skin selection
        double decimalBonus = rarityBonus / 100.0;
        GearRarity rarity = gearGenerator.getRarityRoller().roll(decimalBonus);

        // 3. Select base item filtered by rarity's allowed skin qualities
        String baseItemId = selectBaseItem(slot, rarity);
        if (baseItemId == null) {
            LOGGER.atWarning().log("No base items available for slot %s at rarity %s", slot, rarity);
            return null;
        }

        // 4. Create base ItemStack
        ItemStack baseItem = createBaseItem(baseItemId);
        if (baseItem == null) {
            LOGGER.atWarning().log("Failed to create base item: %s", baseItemId);
            return null;
        }

        // 5. Generate gear with the pre-rolled rarity
        String slotString = mapSlotToString(slot);
        ItemStack gearItem = gearGenerator.generate(baseItem, itemLevel, slotString, rarity);

        // Verify and log the result
        Optional<GearData> gearData = GearUtils.readGearData(gearItem);
        if (gearData.isPresent()) {
            GearData data = gearData.get();
            int modCount = data.prefixes().size() + data.suffixes().size();
            LOGGER.atInfo().log("Generated RPG gear: %s %s (Lv%d, Q%d, %d mods)",
                    data.rarity(), baseItemId, data.level(), data.quality(), modCount);
        } else {
            LOGGER.atWarning().log("FAILED to apply gear data to %s - item has no RPG metadata!", baseItemId);
        }

        return gearItem;
    }

    // =========================================================================
    // SLOT SELECTION
    // =========================================================================

    /**
     * Selects a random equipment slot based on weights.
     *
     * @return The selected equipment slot
     */
    EquipmentSlot selectRandomSlot() {
        int totalWeight = slotWeights.values().stream().mapToInt(Integer::intValue).sum();
        if (totalWeight <= 0) {
            return EquipmentSlot.WEAPON; // Fallback
        }

        int roll = random.nextInt(totalWeight);

        int cumulative = 0;
        for (Map.Entry<EquipmentSlot, Integer> entry : slotWeights.entrySet()) {
            cumulative += entry.getValue();
            if (roll < cumulative) {
                return entry.getKey();
            }
        }

        // Fallback (shouldn't happen)
        return EquipmentSlot.WEAPON;
    }

    /**
     * Selects a random base item for a slot (unfiltered).
     *
     * <p>Uses dynamic registry if available, otherwise falls back to static config.
     *
     * @param slot The equipment slot
     * @return The base item ID, or null if none available
     */
    @Nullable
    String selectBaseItem(EquipmentSlot slot) {
        // Use dynamic registry if available
        if (dynamicRegistry != null && dynamicRegistry.isDiscovered()) {
            return dynamicRegistry.selectRandomItem(slot);
        }

        // Fallback to static config
        List<String> items = baseItems.get(slot);
        if (items == null || items.isEmpty()) {
            return null;
        }
        return items.get(random.nextInt(items.size()));
    }

    /**
     * Selects a random base item for a slot, filtered by RPG rarity's allowed
     * skin qualities.
     *
     * <p>Quality filtering only applies when using the dynamic registry.
     * Static config has no quality data and falls back to unfiltered selection.
     *
     * @param slot   The equipment slot
     * @param rarity The RPG rarity determining which Hytale quality skins are allowed
     * @return The base item ID, or null if none available
     */
    @Nullable
    String selectBaseItem(EquipmentSlot slot, GearRarity rarity) {
        if (dynamicRegistry != null && dynamicRegistry.isDiscovered()) {
            return dynamicRegistry.selectRandomItemForRarity(slot, rarity);
        }
        // Static config has no quality data — fall back to unfiltered
        return selectBaseItem(slot);
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
         * Forces a specific base item.
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
         * Builds and returns the generated item.
         *
         * <p>Rarity is determined first (forced or rolled), then used to filter
         * the skin pool by Hytale quality tier before selecting a base item.
         *
         * @return The generated ItemStack, or null on failure
         */
        public ItemStack build() {
            EquipmentSlot slot = forcedSlot != null ? forcedSlot : selectRandomSlot();

            // Determine rarity first — needed for quality-filtered skin selection
            GearRarity effectiveRarity;
            if (forcedRarity != null) {
                effectiveRarity = forcedRarity;
            } else {
                double decimalBonus = rarityBonus / 100.0;
                effectiveRarity = gearGenerator.getRarityRoller().roll(decimalBonus);
            }

            // Select base item with quality filtering (skipped if baseItem is forced)
            String baseItemId = forcedBaseItem != null
                    ? forcedBaseItem
                    : selectBaseItem(slot, effectiveRarity);

            if (baseItemId == null) {
                LOGGER.atWarning().log("No base item available for slot %s at rarity %s",
                        slot, effectiveRarity);
                return null;
            }

            ItemStack baseItem = createBaseItem(baseItemId);
            if (baseItem == null) {
                return null;
            }

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
     * Gets the dynamic loot registry, if using dynamic discovery.
     *
     * @return The dynamic registry, or null if using static config
     */
    @Nullable
    public DynamicLootRegistry getDynamicRegistry() {
        return dynamicRegistry;
    }

    /**
     * Whether this generator uses dynamic item discovery.
     *
     * @return true if using DynamicLootRegistry
     */
    public boolean isDynamicMode() {
        return dynamicRegistry != null;
    }

    /**
     * Gets the base items map (for testing, static mode only).
     *
     * @return Unmodifiable view of base items
     */
    public Map<EquipmentSlot, List<String>> getBaseItems() {
        return Collections.unmodifiableMap(baseItems);
    }

    /**
     * Gets the slot weights map.
     *
     * @return Unmodifiable view of slot weights
     */
    public Map<EquipmentSlot, Integer> getSlotWeights() {
        return Collections.unmodifiableMap(slotWeights);
    }

    /**
     * Gets the static loot items config.
     *
     * @return The configuration used by this generator, or null if using dynamic registry
     * @deprecated Use getDynamicRegistry() for the preferred configuration
     */
    @Deprecated
    @Nullable
    public LootItemsConfig getConfig() {
        return staticConfig;
    }
}
