package io.github.larsonix.trailoforbis.gear.loot;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;

import io.github.larsonix.trailoforbis.gear.generation.GearGenerator;
import io.github.larsonix.trailoforbis.gear.loot.LootCategory.ImplicitRoll;
import io.github.larsonix.trailoforbis.gear.loot.LootCategory.SuperCategory;
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.gear.model.WeaponType;
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
    private final LootCategoryConfig categoryConfig;

    // Slot weights for random selection (legacy — kept for DropBuilder.slot() forced path)
    private final Map<EquipmentSlot, Integer> slotWeights;

    private final GearGenerator gearGenerator;
    private final Random random;

    // =========================================================================
    // CONSTRUCTORS
    // =========================================================================

    /**
     * Creates a LootGenerator using the implicit-driven category pipeline.
     *
     * @param gearGenerator  The gear generation system
     * @param registry       The dynamic loot registry (for skin selection)
     * @param categoryConfig The loot category configuration (for 3-tier rolling)
     */
    public LootGenerator(
            @Nonnull GearGenerator gearGenerator,
            @Nonnull DynamicLootRegistry registry,
            @Nonnull LootCategoryConfig categoryConfig
    ) {
        this(gearGenerator, registry, categoryConfig, ThreadLocalRandom.current());
    }

    /**
     * Creates a LootGenerator with custom random.
     *
     * @param gearGenerator  The gear generation system
     * @param registry       The dynamic loot registry
     * @param categoryConfig The loot category configuration
     * @param random         The random number generator (for testing)
     */
    public LootGenerator(
            @Nonnull GearGenerator gearGenerator,
            @Nonnull DynamicLootRegistry registry,
            @Nonnull LootCategoryConfig categoryConfig,
            @Nonnull Random random
    ) {
        this.gearGenerator = Objects.requireNonNull(gearGenerator, "gearGenerator cannot be null");
        this.dynamicRegistry = Objects.requireNonNull(registry, "registry cannot be null");
        this.categoryConfig = Objects.requireNonNull(categoryConfig, "categoryConfig cannot be null");
        this.random = Objects.requireNonNull(random, "random cannot be null");
        this.slotWeights = registry.getSlotWeights();
    }

    /**
     * Legacy constructor — creates a default category config.
     * Used by tests and backward-compatible callers.
     */
    public LootGenerator(@Nonnull GearGenerator gearGenerator, @Nonnull DynamicLootRegistry registry) {
        this(gearGenerator, registry, LootCategoryConfig.createDefaults());
    }

    /**
     * Legacy constructor with custom random.
     */
    public LootGenerator(@Nonnull GearGenerator gearGenerator, @Nonnull DynamicLootRegistry registry, @Nonnull Random random) {
        this(gearGenerator, registry, LootCategoryConfig.createDefaults(), random);
    }

    // =========================================================================
    // DROP GENERATION — IMPLICIT-DRIVEN PIPELINE
    // =========================================================================
    //
    // Three-tier selection where the implicit roll defines item identity:
    //
    //   1. RARITY         → Roll independently (geometric weights + bonuses)
    //   2. SUPER-CATEGORY → WEAPON / ARMOR / OFFHAND (weighted)
    //   3. CATEGORY       → Specific type within super (sword, chest, shield, etc.)
    //   4. IMPLICIT       → Random from category pool — THE identity moment
    //                        Armor: armor/evasion/ES/health → determines material + skin
    //                        Weapon: physical/fire/water/etc. → determines damage element
    //   5. SKIN           → Matched from DynamicLootRegistry using resolved identity
    //   6. GENERATE       → GearGenerator creates stats with pre-resolved types

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
     * Generates a single gear drop using the implicit-driven pipeline.
     *
     * <p>Pipeline:
     * <ol>
     *   <li>Roll rarity (unconstrained, geometric weights + bonus)</li>
     *   <li>Roll super-category → category → implicit (3-tier selection)</li>
     *   <li>Resolve identity: EquipmentType + ArmorMaterial/WeaponType + Element</li>
     *   <li>Select skin from DynamicLootRegistry (with quality-tier fallback)</li>
     *   <li>Generate gear with pre-resolved identity via GearGenerator</li>
     * </ol>
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
     * Implicit-driven pipeline: rarity → super-category → category → implicit → skin.
     *
     * <p>The implicit roll defines the item's identity (what defense/damage type),
     * determines the EquipmentType for modifier filtering, and drives skin selection.
     */
    private ItemStack generateSingleDropDynamic(int itemLevel, double rarityBonus) {
        // 1. Roll RARITY — unconstrained (no longer gated by skin availability)
        double decimalBonus = rarityBonus / 100.0;
        GearRarity rarity = gearGenerator.getRarityRoller().roll(decimalBonus);

        // 2. Roll SUPER-CATEGORY (weapon / armor / offhand)
        SuperCategory superCat = categoryConfig.rollSuperCategory(random);

        // 3. Roll CATEGORY within the super (e.g., sword, chest, shield)
        LootCategory category = categoryConfig.rollCategory(superCat, random);
        if (category == null) {
            LOGGER.atWarning().log("No categories in super-category %s — skipping drop", superCat);
            return null;
        }

        // 4. Roll IMPLICIT — THE identity-defining moment
        ImplicitRoll implicitRoll = categoryConfig.rollImplicit(category, random);
        if (implicitRoll == null) {
            LOGGER.atWarning().log("No implicits for category %s — skipping drop", category.id());
            return null;
        }

        // 5. Select SKIN based on resolved identity + rarity (with fallback)
        String skinItemId = selectSkinFromImplicit(implicitRoll, rarity);
        if (skinItemId == null) {
            LOGGER.atWarning().log("No skin for %s at %s — skipping drop", category.id(), rarity);
            return null;
        }

        // 6. Create base item from skin
        ItemStack skinItem = createBaseItem(skinItemId);
        if (skinItem == null) {
            LOGGER.atWarning().log("Failed to create skin item: %s", skinItemId);
            return null;
        }

        // 7. Generate gear using the pre-resolved identity
        ItemStack gearItem = gearGenerator.generateFromCategory(
                skinItem, itemLevel, implicitRoll.slotString(), rarity,
                implicitRoll.equipmentType(),
                implicitRoll.skinWeaponType(),
                implicitRoll.element()
        );

        // 8. Log result
        if (gearItem != null) {
            Optional<GearData> gearData = GearUtils.readGearData(gearItem);
            if (gearData.isPresent()) {
                GearData data = gearData.get();
                int modCount = data.prefixes().size() + data.suffixes().size();
                LOGGER.atInfo().log("Generated RPG gear: %s %s [%s→%s] (Lv%d, Q%d, %d mods, element=%s)",
                        data.rarity(), skinItemId, category.id(),
                        implicitRoll.entry().implicitType(),
                        data.level(), data.quality(), modCount,
                        implicitRoll.element() != null ? implicitRoll.element().name() : "none");
            } else {
                LOGGER.atWarning().log("FAILED to apply gear data to %s - item has no RPG metadata!", skinItemId);
            }
        }

        return gearItem;
    }

    /**
     * Selects a skin item ID based on the implicit roll's resolved identity.
     *
     * <p>For armor: looks up by (slot, ArmorMaterial, rarity).
     * For weapons/offhand: looks up by (WeaponType, rarity).
     */
    @Nullable
    private String selectSkinFromImplicit(@Nonnull ImplicitRoll roll, @Nonnull GearRarity rarity) {
        if (roll.skinMaterial() != null) {
            // Armor — look up by material + slot
            EquipmentSlot slot = mapStringToSlot(roll.slotString());
            return dynamicRegistry.selectSkinForMaterial(slot, roll.skinMaterial(), rarity);
        } else if (roll.skinWeaponType() != null) {
            // Weapon/offhand — look up by weapon type
            return dynamicRegistry.selectSkinForWeaponType(roll.skinWeaponType(), rarity);
        }
        LOGGER.atWarning().log("ImplicitRoll has no skin material or weapon type: %s", roll);
        return null;
    }

    /**
     * Maps a slot string to EquipmentSlot enum.
     */
    private static EquipmentSlot mapStringToSlot(String slotString) {
        return switch (slotString.toLowerCase()) {
            case "weapon" -> EquipmentSlot.WEAPON;
            case "head" -> EquipmentSlot.HEAD;
            case "chest" -> EquipmentSlot.CHEST;
            case "legs" -> EquipmentSlot.LEGS;
            case "hands" -> EquipmentSlot.HANDS;
            case "shield", "off_hand" -> EquipmentSlot.OFF_HAND;
            default -> EquipmentSlot.WEAPON;
        };
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
            // Validate item exists in the asset map before creating ItemStack.
            // DynamicLootRegistry discovers items at startup, but some may have
            // broken client rendering (missing models, icons, etc.). Items that
            // don't resolve to a valid Item would show as "?" on the client.
            Item item = Item.getAssetMap().getAsset(itemId);
            if (item == null || item == Item.UNKNOWN) {
                LOGGER.atWarning().log("Skin item '%s' not in asset map — skipping", itemId);
                return null;
            }
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
     * <p>Uses the implicit-driven pipeline: rarity → super-category → category → implicit → skin.
     * Forced values bypass their respective selection step. When slot is forced,
     * falls back to the legacy skin-based selection path.
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
            // 1. Determine rarity — unconstrained
            GearRarity effectiveRarity;
            if (forcedRarity != null) {
                effectiveRarity = forcedRarity;
            } else {
                double decimalBonus = rarityBonus / 100.0;
                effectiveRarity = gearGenerator.getRarityRoller().roll(decimalBonus);
            }

            // If slot is forced, use the legacy skin-based path
            if (forcedSlot != null) {
                return buildWithForcedSlot(effectiveRarity);
            }

            // 2. Roll super-category → category → implicit (new pipeline)
            SuperCategory superCat = categoryConfig.rollSuperCategory(random);
            LootCategory category = categoryConfig.rollCategory(superCat, random);
            if (category == null) return null;

            ImplicitRoll implicitRoll = categoryConfig.rollImplicit(category, random);
            if (implicitRoll == null) return null;

            // 3. Select skin
            String skinItemId = selectSkinFromImplicit(implicitRoll, effectiveRarity);
            if (skinItemId == null) return null;

            ItemStack skinItem = createBaseItem(skinItemId);
            if (skinItem == null) return null;

            // 4. Generate with resolved identity
            return gearGenerator.generateFromCategory(
                    skinItem, itemLevel, implicitRoll.slotString(), effectiveRarity,
                    implicitRoll.equipmentType(),
                    implicitRoll.skinWeaponType(),
                    implicitRoll.element()
            );
        }

        /**
         * Legacy path for when slot is forced — uses old selectSkin from the slot's availability.
         */
        private ItemStack buildWithForcedSlot(GearRarity effectiveRarity) {
            Set<String> availableCats = dynamicRegistry.getAvailableCategoriesForRaritySlot(
                    effectiveRarity, forcedSlot);
            if (availableCats.isEmpty()) {
                LOGGER.atWarning().log("DropBuilder: no categories for forced slot %s at %s",
                        forcedSlot, effectiveRarity);
                return null;
            }
            // Pick random category from available
            String category = availableCats.stream()
                    .skip(random.nextInt(availableCats.size()))
                    .findFirst().orElse(null);
            if (category == null) return null;

            String baseItemId = dynamicRegistry.selectSkin(forcedSlot, category, effectiveRarity);
            if (baseItemId == null) return null;

            ItemStack baseItem = createBaseItem(baseItemId);
            if (baseItem == null) return null;

            String slotString = mapSlotToString(forcedSlot);
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
     * Gets the loot category configuration.
     */
    @Nonnull
    public LootCategoryConfig getCategoryConfig() {
        return categoryConfig;
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
