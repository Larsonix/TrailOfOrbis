package io.github.larsonix.trailoforbis.gear.generation;

import io.github.larsonix.trailoforbis.attributes.AttributeType;
import io.github.larsonix.trailoforbis.compat.HexcodeCompat;
import io.github.larsonix.trailoforbis.compat.HexcodeMetadataInjector;
import io.github.larsonix.trailoforbis.elemental.ElementType;
import io.github.larsonix.trailoforbis.gear.config.EquipmentStatConfig;
import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig;
import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig.RarityConfig;
import io.github.larsonix.trailoforbis.gear.config.ModifierConfig;
import io.github.larsonix.trailoforbis.gear.conversion.EquipmentTypeResolver;
import io.github.larsonix.trailoforbis.gear.instance.GearInstanceId;
import io.github.larsonix.trailoforbis.gear.instance.GearInstanceIdGenerator;
import io.github.larsonix.trailoforbis.gear.item.ItemRegistryService;
import io.github.larsonix.trailoforbis.gear.model.EquipmentType;
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.model.GearModifier;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.gear.model.ArmorImplicit;
import io.github.larsonix.trailoforbis.gear.model.ArmorMaterial;
import io.github.larsonix.trailoforbis.gear.model.EquipmentType.ArmorSlot;
import io.github.larsonix.trailoforbis.gear.model.WeaponImplicit;
import io.github.larsonix.trailoforbis.gear.model.WeaponType;
import io.github.larsonix.trailoforbis.gear.util.GearUtils;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Generates random RPG gear with rarity, quality, and modifiers.
 *
 * <p>This is the main entry point for gear generation. It orchestrates:
 * <ul>
 *   <li>{@link RarityRoller} - Determines gear rarity</li>
 *   <li>{@link QualityRoller} - Determines gear quality</li>
 *   <li>{@link ModifierPool} - Selects and values modifiers</li>
 *   <li>{@link GearUtils} - Writes data to ItemStack</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 *
 * <p>This class's thread safety depends on the provided Random instance:
 * <ul>
 *   <li><b>Default constructor</b>: Uses {@link ThreadLocalRandom} and is
 *       fully thread-safe for concurrent generation from multiple threads</li>
 *   <li><b>Custom Random</b>: Thread-safe only if the provided Random is
 *       thread-safe (e.g., {@link ThreadLocalRandom}, or synchronized access)</li>
 * </ul>
 *
 * <p>For server environments with concurrent loot generation (mob deaths,
 * chest opens), always use the default constructor or provide a thread-safe
 * Random implementation.
 *
 * @see ThreadLocalRandom
 */
public final class GearGenerator {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final GearBalanceConfig balanceConfig;
    private final ModifierConfig modifierConfig;
    private final EquipmentStatConfig equipmentStatConfig;
    private final Random random;
    private final ItemRegistryService itemRegistry;

    // Sub-components
    private final RarityRoller rarityRoller;
    private final QualityRoller qualityRoller;
    private final ModifierPool modifierPool;
    private final EquipmentTypeResolver equipmentTypeResolver;
    private final ImplicitDamageCalculator implicitCalculator;
    private final ImplicitDefenseCalculator implicitDefenseCalculator;

    /**
     * Creates a GearGenerator with the given configs and item registry.
     *
     * @param balanceConfig The gear balance configuration
     * @param modifierConfig The modifier configuration
     * @param equipmentStatConfig The equipment stat restriction config
     * @param itemRegistry The item registry service for server-side item registration
     * @param random The random number generator (shared across sub-components)
     */
    public GearGenerator(
            GearBalanceConfig balanceConfig,
            ModifierConfig modifierConfig,
            EquipmentStatConfig equipmentStatConfig,
            ItemRegistryService itemRegistry,
            Random random
    ) {
        this.balanceConfig = Objects.requireNonNull(balanceConfig);
        this.modifierConfig = Objects.requireNonNull(modifierConfig);
        this.equipmentStatConfig = Objects.requireNonNull(equipmentStatConfig);
        this.itemRegistry = Objects.requireNonNull(itemRegistry);
        this.random = Objects.requireNonNull(random);

        // Initialize sub-components with shared random
        this.rarityRoller = new RarityRoller(balanceConfig, random);
        this.qualityRoller = new QualityRoller(balanceConfig.quality(), random);
        this.modifierPool = new ModifierPool(modifierConfig, balanceConfig, equipmentStatConfig, random);
        this.equipmentTypeResolver = new EquipmentTypeResolver();
        this.implicitCalculator = new ImplicitDamageCalculator(balanceConfig);
        this.implicitDefenseCalculator = new ImplicitDefenseCalculator(balanceConfig);
    }

    /**
     * Creates a GearGenerator with ThreadLocalRandom for thread safety.
     *
     * <p>This is the recommended constructor for server environments where
     * gear generation may occur concurrently from multiple threads (e.g.,
     * mob death events, chest opening, etc.).
     *
     * @param balanceConfig The gear balance configuration
     * @param modifierConfig The modifier configuration
     * @param equipmentStatConfig The equipment stat restriction config
     * @param itemRegistry The item registry service for server-side item registration
     */
    public GearGenerator(
            GearBalanceConfig balanceConfig,
            ModifierConfig modifierConfig,
            EquipmentStatConfig equipmentStatConfig,
            ItemRegistryService itemRegistry
    ) {
        this(balanceConfig, modifierConfig, equipmentStatConfig, itemRegistry, ThreadLocalRandom.current());
    }

    // =========================================================================
    // MAIN GENERATION METHODS
    // =========================================================================

    /**
     * Generates gear with random rarity.
     *
     * @param baseItem The base item to apply gear data to
     * @param itemLevel The item level (affects stats and requirements)
     * @param slot The gear slot (for modifier filtering)
     * @return New ItemStack with gear data applied
     */
    public ItemStack generate(ItemStack baseItem, int itemLevel, String slot) {
        return generate(baseItem, itemLevel, slot, 0.0);
    }

    /**
     * Generates gear with random rarity and rarity bonus.
     *
     * @param baseItem The base item to apply gear data to
     * @param itemLevel The item level
     * @param slot The gear slot
     * @param rarityBonus Bonus affecting rarity roll (0.0 = no bonus)
     * @return New ItemStack with gear data applied
     */
    public ItemStack generate(ItemStack baseItem, int itemLevel, String slot, double rarityBonus) {
        // Roll rarity
        GearRarity rarity = rarityRoller.roll(rarityBonus);

        return generate(baseItem, itemLevel, slot, rarity);
    }

    /**
     * Generates gear with a specific rarity.
     *
     * @param baseItem The base item to apply gear data to
     * @param itemLevel The item level
     * @param slot The gear slot
     * @param rarity The forced rarity
     * @return New ItemStack with gear data applied
     */
    public ItemStack generate(ItemStack baseItem, int itemLevel, String slot, GearRarity rarity) {
        return generate(baseItem, itemLevel, slot, rarity, null);
    }

    /**
     * Generates gear with a specific rarity and equipment type.
     *
     * <p>Resolves the item's identity (equipment type, weapon type, element)
     * then delegates to {@link #generateFromCategory} — the single generation engine.
     *
     * <p>Element rolling uses configurable weights from {@code gear-balance.yml}:
     * physical weapons have a configurable chance of getting elemental damage
     * (default 70% physical / 30% elemental, matching loot-discovery.yml defaults).
     * Magic weapons always get elemental damage.
     *
     * @param baseItem The base item to apply gear data to
     * @param itemLevel The item level
     * @param slot The gear slot
     * @param rarity The forced rarity
     * @param equipmentType The equipment type for stat filtering (nullable = auto-detect)
     * @return New ItemStack with gear data applied
     */
    public ItemStack generate(
            ItemStack baseItem,
            int itemLevel,
            String slot,
            GearRarity rarity,
            @Nullable EquipmentType equipmentType
    ) {
        Objects.requireNonNull(baseItem, "baseItem cannot be null");
        Objects.requireNonNull(slot, "slot cannot be null");
        Objects.requireNonNull(rarity, "rarity cannot be null");

        if (itemLevel < GearData.MIN_LEVEL || itemLevel > GearData.MAX_LEVEL) {
            throw new IllegalArgumentException(
                "itemLevel must be between " + GearData.MIN_LEVEL + " and " + GearData.MAX_LEVEL);
        }

        // Resolve the base item ID for weapon type detection
        // If the item already has a baseItemId stored (regeneration), use that
        String baseItemId = GearUtils.getBaseItemId(baseItem);
        if (baseItemId == null) {
            baseItemId = baseItem.getItemId();
        }

        // Thrown consumables (bombs, darts, kunai) should not receive RPG stats.
        // Shields ARE stat-eligible — they get block_chance via generateFromCategory().
        if ("weapon".equalsIgnoreCase(slot)) {
            WeaponType resolved = WeaponType.fromItemIdOrUnknown(baseItemId);
            if (resolved.isThrown()) {
                LOGGER.atFine().log("Skipping RPG stat generation for thrown weapon: %s", baseItemId);
                return baseItem;
            }
        }

        // Auto-detect equipment type from item ID if not provided
        EquipmentType effectiveEquipmentType = equipmentType;
        if (effectiveEquipmentType == null) {
            effectiveEquipmentType = equipmentTypeResolver.resolve(baseItem.getItemId(), slot);
            if (effectiveEquipmentType != null) {
                LOGGER.atFine().log("Auto-detected equipment type: %s → %s",
                        baseItem.getItemId(), effectiveEquipmentType);
            }
        }

        // Resolve weapon type and roll element using balance config weights
        WeaponType weaponType = "weapon".equalsIgnoreCase(slot)
                ? WeaponType.fromItemIdOrUnknown(baseItemId)
                : null;
        ElementType element = (weaponType != null)
                ? implicitCalculator.rollElement(weaponType, random)
                : null;

        // Delegate to the unified generation engine
        return generateFromCategory(
                baseItem, itemLevel, slot, rarity,
                effectiveEquipmentType, weaponType, element);
    }

    /**
     * Registers a custom item in the server-side asset map.
     *
     * <p>This is critical for item usability - without registration, the server
     * won't recognize the custom item ID and will reject all interactions.
     *
     * <p>Uses SYNCHRONOUS registration to ensure:
     * <ol>
     *   <li>The item is registered in the asset map before this method returns</li>
     *   <li>The database save completes before the item can be used/dropped</li>
     *   <li>Thread-safe visibility to all threads via StampedLock</li>
     * </ol>
     *
     * @param baseItem The original base item
     * @param gearData The gear data containing the custom item ID
     */
    private void registerCustomItem(ItemStack baseItem, GearData gearData) {
        if (!itemRegistry.isInitialized()) {
            LOGGER.atWarning().log("ItemRegistryService not initialized, skipping item registration");
            return;
        }

        String customItemId = gearData.getItemId();
        if (customItemId == null) {
            LOGGER.atWarning().log("GearData has no item ID, skipping registration");
            return;
        }

        // Skip if already registered (e.g., regenerated item)
        if (itemRegistry.isRegistered(customItemId)) {
            return;
        }

        // Get the base Item asset
        Item baseItemAsset = baseItem.getItem();
        if (baseItemAsset == null || baseItemAsset == Item.UNKNOWN) {
            LOGGER.atWarning().log("Base item not found for %s, skipping registration", baseItem.getItemId());
            return;
        }

        // Create and register the custom item SYNCHRONOUSLY.
        // Pass the RPG rarity so the reskin ResourceType matches the correct
        // workbench recipe group (e.g., MYTHIC → Epic+Legendary skins).
        itemRegistry.createAndRegisterSync(baseItemAsset, customItemId, gearData.rarity());
    }

    // =========================================================================
    // IMPLICIT-DRIVEN GENERATION (new pipeline)
    // =========================================================================

    /**
     * Generates gear from a pre-resolved category implicit roll.
     *
     * <p>This is the entry point for the implicit-driven loot pipeline.
     * Unlike {@link #generate(ItemStack, int, String, GearRarity)} which
     * derives weapon/armor type FROM the base item, this method receives
     * all identity information pre-resolved from the implicit roll.
     *
     * <p>Pipeline: implicit roll → equipmentType + element → this method
     * → quality + modifiers + implicits → apply to skin ItemStack.
     *
     * @param skinItem      The base ItemStack to use as visual skin
     * @param itemLevel     The item level
     * @param slot          The gear slot string ("weapon", "head", "chest", etc.)
     * @param rarity        The rolled rarity
     * @param equipmentType The resolved equipment type (from implicit)
     * @param weaponType    The weapon type (nullable — for weapons only)
     * @param element       The damage element (nullable — null = physical)
     * @return Final ItemStack with gear data applied, or null on failure
     */
    @Nullable
    public ItemStack generateFromCategory(
            @Nonnull ItemStack skinItem,
            int itemLevel,
            @Nonnull String slot,
            @Nonnull GearRarity rarity,
            @Nonnull EquipmentType equipmentType,
            @Nullable WeaponType weaponType,
            @Nullable ElementType element
    ) {
        Objects.requireNonNull(skinItem, "skinItem cannot be null");
        Objects.requireNonNull(slot, "slot cannot be null");
        Objects.requireNonNull(rarity, "rarity cannot be null");
        Objects.requireNonNull(equipmentType, "equipmentType cannot be null");

        if (itemLevel < GearData.MIN_LEVEL || itemLevel > GearData.MAX_LEVEL) {
            throw new IllegalArgumentException(
                    "itemLevel must be between " + GearData.MIN_LEVEL + " and " + GearData.MAX_LEVEL);
        }

        // Roll quality
        int quality = qualityRoller.roll();

        // Calculate modifier distribution
        RarityConfig rarityConfig = balanceConfig.rarityConfig(rarity);
        int[] distribution = calculateModifierDistribution(rarityConfig);
        int prefixCount = distribution[0];
        int suffixCount = distribution[1];

        // Set element affinity for elemental weapons (biases modifier selection toward matching element)
        if (element != null) {
            AttributeType affinity = AttributeType.fromString(element.name());
            modifierPool.setElementAffinity(affinity);
        }

        // Roll modifiers with equipment type filtering
        List<GearModifier> prefixes;
        List<GearModifier> suffixes;
        try {
            prefixes = modifierPool.rollPrefixes(
                    prefixCount, itemLevel, slot, rarity, equipmentType);
            suffixes = modifierPool.rollSuffixes(
                    suffixCount, itemLevel, slot, rarity, equipmentType);
        } finally {
            modifierPool.clearElementAffinity();
        }

        // Generate unique instance ID
        GearInstanceId instanceId = GearInstanceIdGenerator.generate();

        // Generate implicits using the pre-resolved identity.
        // Unlike generateImplicitsWithElement (which infers from slot string + itemId),
        // this directly uses the resolved weaponType and equipmentType from the implicit roll.
        String skinItemId = skinItem.getItemId();
        WeaponImplicit implicit = null;
        ArmorImplicit armorImplicit = null;

        if (weaponType != null && weaponType != WeaponType.SHIELD
                && implicitCalculator.isEnabled()
                && implicitCalculator.shouldHaveImplicit(weaponType)) {
            // Weapon with weapon-style implicit (damage, mana_regen, etc.)
            if (element != null) {
                implicit = implicitCalculator.calculateWithElement(weaponType, itemLevel, element, random);
            } else {
                implicit = implicitCalculator.calculate(weaponType, itemLevel, random);
            }
        } else if (equipmentType.isOffhand() && implicitDefenseCalculator.isEnabled()) {
            // Shield — gets block_chance via defense implicit
            armorImplicit = implicitDefenseCalculator.calculateShield(itemLevel, random);
        } else if (equipmentType.isArmor() && implicitDefenseCalculator.isEnabled()
                && implicitDefenseCalculator.shouldHaveImplicit(equipmentType)) {
            // Armor with defense implicit (armor, evasion, ES, health)
            ArmorMaterial material = equipmentType.getArmorMaterial();
            ArmorSlot armorSlot = equipmentType.getArmorSlot();
            if (material != null && armorSlot != null) {
                armorImplicit = implicitDefenseCalculator.calculate(material, armorSlot, itemLevel, random);
            }
        }

        // Build GearData with skin's itemId as the base
        GearData gearData = GearData.builder()
                .instanceId(instanceId)
                .level(itemLevel)
                .rarity(rarity)
                .quality(quality)
                .prefixes(prefixes)
                .suffixes(suffixes)
                .implicit(implicit)
                .armorImplicit(armorImplicit)
                .baseItemId(skinItemId)
                .build();

        // Register custom item
        registerCustomItem(skinItem, gearData);

        // Inject Hexcode metadata if applicable
        ItemStack itemForGear = injectHexMetadataIfApplicable(skinItem, skinItemId, rarity);

        // Write gear data to ItemStack
        ItemStack result = GearUtils.setGearData(itemForGear, gearData);

        int modCount = prefixes.size() + suffixes.size();
        LOGGER.atFine().log("Generated %s %s gear from category (level %d, Q%d, %d mods, element=%s)",
                rarity, slot, itemLevel, quality, modCount,
                element != null ? element.name() : "none");

        return result;
    }

    /**
     * Generates gear data without applying to an ItemStack.
     *
     * <p>Useful for testing or preview functionality.
     *
     */
    public GearData generateData(int itemLevel, String slot, GearRarity rarity) {
        return generateData(itemLevel, slot, rarity, null);
    }

    /**
     * Generates gear data with equipment type filtering.
     *
     * <p>Useful for testing or preview functionality.
     *
     * @param equipmentType for stat filtering (nullable)
     */
    public GearData generateData(
            int itemLevel,
            String slot,
            GearRarity rarity,
            @Nullable EquipmentType equipmentType
    ) {
        return generateData(itemLevel, slot, rarity, equipmentType, null);
    }

    /**
     * Generates gear data with equipment type filtering and optional item ID.
     *
     * <p>Useful for testing or preview functionality. The itemId is used
     * to determine weapon type for implicit damage calculation.
     *
     * @param equipmentType for stat filtering (nullable)
     * @param itemId for weapon type detection (nullable)
     */
    public GearData generateData(
            int itemLevel,
            String slot,
            GearRarity rarity,
            @Nullable EquipmentType equipmentType,
            @Nullable String itemId
    ) {
        // Thrown consumables should not receive RPG stats
        if ("weapon".equalsIgnoreCase(slot) && itemId != null) {
            WeaponType resolved = WeaponType.fromItemIdOrUnknown(itemId);
            if (!resolved.isStatEligible()) {
                throw new IllegalArgumentException(
                        "Thrown weapon '" + itemId + "' is not eligible for RPG stat generation");
            }
        }

        int quality = qualityRoller.roll();

        // Resolve weapon type and roll element using balance config weights
        WeaponType weaponType = "weapon".equalsIgnoreCase(slot) && itemId != null
                ? WeaponType.fromItemIdOrUnknown(itemId)
                : null;
        ElementType element = (weaponType != null)
                ? implicitCalculator.rollElement(weaponType, random)
                : null;

        // Set element affinity for modifier biasing
        if (element != null) {
            AttributeType affinity = AttributeType.fromString(element.name());
            modifierPool.setElementAffinity(affinity);
        }

        // Calculate modifier distribution to exactly hit max_modifiers
        RarityConfig rarityConfig = balanceConfig.rarityConfig(rarity);
        int[] distribution = calculateModifierDistribution(rarityConfig);
        int prefixCount = distribution[0];
        int suffixCount = distribution[1];

        List<GearModifier> prefixes;
        List<GearModifier> suffixes;
        try {
            prefixes = modifierPool.rollPrefixes(
                    prefixCount, itemLevel, slot, rarity, equipmentType);
            suffixes = modifierPool.rollSuffixes(
                    suffixCount, itemLevel, slot, rarity, equipmentType);
        } finally {
            modifierPool.clearElementAffinity();
        }

        // Generate unique instance ID
        GearInstanceId instanceId = GearInstanceIdGenerator.generate();

        // Generate weapon and armor implicits (same logic as generateFromCategory)
        WeaponImplicit implicit = null;
        ArmorImplicit armorImplicit = null;

        if (weaponType != null && weaponType != WeaponType.SHIELD
                && implicitCalculator.isEnabled()
                && implicitCalculator.shouldHaveImplicit(weaponType)) {
            if (element != null) {
                implicit = implicitCalculator.calculateWithElement(weaponType, itemLevel, element, random);
            } else {
                implicit = implicitCalculator.calculate(weaponType, itemLevel, random);
            }
        } else if (equipmentType != null && equipmentType.isOffhand()
                && implicitDefenseCalculator.isEnabled()) {
            armorImplicit = implicitDefenseCalculator.calculateShield(itemLevel, random);
        } else if (equipmentType != null && equipmentType.isArmor()
                && implicitDefenseCalculator.isEnabled()
                && implicitDefenseCalculator.shouldHaveImplicit(equipmentType)) {
            ArmorMaterial material = equipmentType.getArmorMaterial();
            ArmorSlot armorSlot = equipmentType.getArmorSlot();
            if (material != null && armorSlot != null) {
                armorImplicit = implicitDefenseCalculator.calculate(material, armorSlot, itemLevel, random);
            }
        }

        return GearData.builder()
                .instanceId(instanceId)
                .level(itemLevel)
                .rarity(rarity)
                .quality(quality)
                .prefixes(prefixes)
                .suffixes(suffixes)
                .implicit(implicit)
                .armorImplicit(armorImplicit)
                .baseItemId(itemId)
                .build();
    }

    // =========================================================================
    // IMPLICIT EXPECTATION (for migration — source of truth)
    // =========================================================================

    /**
     * Describes what implicits an item SHOULD have based on current generation rules.
     * Used by the migration system to compare existing items against the source of truth.
     */
    public final class ImplicitExpectation {
        private final boolean shouldHaveWeapon;
        private final boolean shouldHaveArmor;
        private final @Nullable WeaponType weaponType;
        private final @Nullable EquipmentType equipmentType;
        private final int itemLevel;

        private ImplicitExpectation(boolean shouldHaveWeapon, boolean shouldHaveArmor,
                                    @Nullable WeaponType weaponType,
                                    @Nullable EquipmentType equipmentType, int itemLevel) {
            this.shouldHaveWeapon = shouldHaveWeapon;
            this.shouldHaveArmor = shouldHaveArmor;
            this.weaponType = weaponType;
            this.equipmentType = equipmentType;
            this.itemLevel = itemLevel;
        }

        public boolean shouldHaveWeapon() { return shouldHaveWeapon; }
        public boolean shouldHaveArmor() { return shouldHaveArmor; }

        /** Checks if the given damage type is valid for this weapon type. */
        public boolean isWeaponTypeValid(@Nullable String damageType) {
            if (!shouldHaveWeapon || weaponType == null || damageType == null) return false;
            return ImplicitDamageCalculator.getValidDamageTypes(weaponType).contains(damageType);
        }

        /** Checks if the given defense type is valid for this armor type. */
        public boolean isArmorTypeValid(@Nullable String defenseType) {
            if (!shouldHaveArmor || equipmentType == null || defenseType == null) return false;
            // The correct defense type is determined by armor material
            ArmorMaterial material = equipmentType.getArmorMaterial();
            if (material == null && equipmentType.isOffhand()) return "block_chance".equals(defenseType);
            if (material == null) return false;
            String expectedStat = balanceConfig.implicitDefense().getStatForMaterial(material);
            return expectedStat != null && expectedStat.equals(defenseType);
        }

        /** Generates the correct weapon implicit for this item. */
        @Nullable
        public WeaponImplicit generateWeapon(@Nonnull Random rng) {
            if (!shouldHaveWeapon || weaponType == null) return null;
            return implicitCalculator.calculate(weaponType, itemLevel, rng);
        }

        /** Generates the correct armor implicit for this item. */
        @Nullable
        public ArmorImplicit generateArmor(@Nonnull Random rng) {
            if (!shouldHaveArmor || equipmentType == null) return null;
            if (equipmentType.isOffhand()) {
                return implicitDefenseCalculator.calculateShield(itemLevel, rng);
            }
            ArmorMaterial material = equipmentType.getArmorMaterial();
            EquipmentType.ArmorSlot armorSlot = equipmentType.getArmorSlot();
            if (material == null || armorSlot == null) return null;
            return implicitDefenseCalculator.calculate(material, armorSlot, itemLevel, rng);
        }
    }

    /**
     * Returns what implicits an item SHOULD have based on current generation rules.
     *
     * <p>This is the source of truth for the migration system. Instead of duplicating
     * generation logic in validation rules, migration calls this and compares.
     *
     * @param slot The gear slot ("weapon", "head", "chest", "legs", "hands")
     * @param baseItemId The original item ID for weapon type detection
     * @param equipmentType The resolved equipment type (nullable)
     * @param itemLevel The item's level
     * @return An ImplicitExpectation describing what the item should have
     */
    @Nonnull
    public ImplicitExpectation getExpectedImplicits(
            @Nullable String slot,
            @Nullable String baseItemId,
            @Nullable EquipmentType equipmentType,
            int itemLevel) {

        // Weapon implicit: only for weapon slot with a known weapon type
        boolean shouldHaveWeapon = false;
        WeaponType weaponType = null;
        if ("weapon".equalsIgnoreCase(slot) && implicitCalculator.isEnabled() && baseItemId != null) {
            weaponType = WeaponType.fromItemIdOrUnknown(baseItemId);
            if (weaponType != WeaponType.UNKNOWN && implicitCalculator.shouldHaveImplicit(weaponType)) {
                shouldHaveWeapon = true;
            }
        }

        // Armor implicit: only for armor/shield with known equipment type
        boolean shouldHaveArmor = false;
        if (implicitDefenseCalculator.isEnabled() && equipmentType != null
                && implicitDefenseCalculator.shouldHaveImplicit(equipmentType)) {
            shouldHaveArmor = true;
        }

        return new ImplicitExpectation(shouldHaveWeapon, shouldHaveArmor, weaponType, equipmentType, itemLevel);
    }

    // =========================================================================
    // HEXCODE INTEGRATION
    // =========================================================================

    /**
     * Injects Hexcode metadata onto the base ItemStack if applicable.
     *
     * <p>Only injects when Hexcode is loaded and the base item is a magic weapon
     * (staff, wand, or spellbook). Must be called BEFORE {@code GearUtils.setGearData()}
     * so the hex metadata survives the ItemStack reconstruction.
     *
     * @param baseItem The base ItemStack
     * @param baseItemId The original item ID for weapon type detection
     * @param rarity The RPG rarity (for spellbook capacity scaling)
     * @return ItemStack with hex metadata, or unchanged if not applicable
     */
    ItemStack injectHexMetadataIfApplicable(ItemStack baseItem, @Nullable String baseItemId, GearRarity rarity) {
        if (!HexcodeCompat.isLoaded()) {
            return baseItem;
        }
        return HexcodeMetadataInjector.injectIfApplicable(baseItem, baseItemId, rarity);
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    /**
     * Rolls a count between min and max (inclusive).
     */
    private int rollCount(int min, int max) {
        if (min == max) {
            return min;
        }
        return min + random.nextInt(max - min + 1);
    }

    /**
     * Calculates a prefix/suffix distribution that targets max_modifiers.
     *
     * <p>Instead of rolling prefix and suffix counts independently (which can
     * result in fewer modifiers than desired), this method calculates a valid
     * prefix count such that prefixCount + suffixCount = max_modifiers, while
     * respecting the configured max bounds (min bounds are "soft" and may be
     * violated if necessary to stay under max_modifiers).
     *
     * <p>The algorithm:
     * <ol>
     *   <li>Calculate the target: min(max_modifiers, maxPrefixes + maxSuffixes)</li>
     *   <li>Find a valid prefix range that allows hitting the target</li>
     *   <li>Roll within that range and set suffixes to fill remaining</li>
     *   <li>Clamp suffix count to maxSuffixes (never exceed configured max)</li>
     * </ol>
     *
     * @param config The rarity configuration with prefix/suffix ranges and max_modifiers
     * @return int[2] where [0] = prefixCount, [1] = suffixCount, sum ≤ max_modifiers
     */
    private int[] calculateModifierDistribution(RarityConfig config) {
        // Target is max_modifiers, capped by what's achievable with max bounds
        int maxAchievable = config.maxPrefixes() + config.maxSuffixes();
        int target = Math.min(config.maxModifiers(), maxAchievable);

        // Calculate valid prefix range
        // We want: prefixCount + suffixCount = target
        // Where: 0 <= prefixCount <= maxPrefixes
        //        0 <= suffixCount <= maxSuffixes
        //
        // So: prefixCount >= target - maxSuffixes (to leave room for suffixes)
        //     prefixCount <= target (can't have more prefixes than target)
        //     prefixCount <= maxPrefixes (config hard cap)
        int minValidPrefixes = Math.max(0, target - config.maxSuffixes());
        int maxValidPrefixes = Math.min(target, config.maxPrefixes());

        // Ensure valid range
        maxValidPrefixes = Math.max(minValidPrefixes, maxValidPrefixes);

        // Roll prefix count within the valid range
        int prefixCount = rollCount(minValidPrefixes, maxValidPrefixes);

        // Suffix count fills the remainder, clamped to configured max
        int suffixCount = Math.min(target - prefixCount, config.maxSuffixes());
        suffixCount = Math.max(0, suffixCount);

        return new int[] { prefixCount, suffixCount };
    }

    // =========================================================================
    // ACCESSORS
    // =========================================================================

    public RarityRoller getRarityRoller() {
        return rarityRoller;
    }

    public QualityRoller getQualityRoller() {
        return qualityRoller;
    }

    public ModifierPool getModifierPool() {
        return modifierPool;
    }

    public ImplicitDamageCalculator getImplicitCalculator() {
        return implicitCalculator;
    }

    public ImplicitDefenseCalculator getImplicitDefenseCalculator() {
        return implicitDefenseCalculator;
    }

    // =========================================================================
    // BUILDER FOR CUSTOMIZED GENERATION
    // =========================================================================

    /**
     * Creates a builder for customized gear generation.
     *
     * <p>Use this when you need fine-grained control over the generation
     * process, such as forcing specific modifiers or quality.
     */
    public GenerationBuilder builder() {
        return new GenerationBuilder(this);
    }

    /**
     * Builder for customized gear generation.
     */
    public static class GenerationBuilder {
        private final GearGenerator generator;

        private Integer itemLevel;
        private String slot;
        private GearRarity rarity;
        private Integer quality;
        private Double rarityBonus;
        private List<GearModifier> forcedPrefixes;
        private List<GearModifier> forcedSuffixes;
        private EquipmentType equipmentType;
        private String itemId;

        GenerationBuilder(GearGenerator generator) {
            this.generator = generator;
        }

        public GenerationBuilder level(int level) {
            this.itemLevel = level;
            return this;
        }

        public GenerationBuilder slot(String slot) {
            this.slot = slot;
            return this;
        }

        public GenerationBuilder rarity(GearRarity rarity) {
            this.rarity = rarity;
            return this;
        }

        public GenerationBuilder quality(int quality) {
            this.quality = quality;
            return this;
        }

        public GenerationBuilder rarityBonus(double bonus) {
            this.rarityBonus = bonus;
            return this;
        }

        public GenerationBuilder forcePrefixes(List<GearModifier> prefixes) {
            this.forcedPrefixes = prefixes;
            return this;
        }

        public GenerationBuilder forceSuffixes(List<GearModifier> suffixes) {
            this.forcedSuffixes = suffixes;
            return this;
        }

        /**
         * Sets the equipment type for stat filtering.
         *
         * <p>If not set, equipment type will be auto-detected from the item ID
         * when using {@link #build(ItemStack)}.
         *
         * @param equipmentType The equipment type
         * @return This builder
         */
        public GenerationBuilder equipmentType(EquipmentType equipmentType) {
            this.equipmentType = equipmentType;
            return this;
        }

        /**
         * Sets the item ID for equipment type auto-detection.
         *
         * <p>Only needed if you're using {@link #buildData()} directly
         * and want auto-detection. When using {@link #build(ItemStack)},
         * the item ID is extracted automatically from the ItemStack.
         *
         * @param itemId The Hytale item ID
         * @return This builder
         */
        public GenerationBuilder itemId(String itemId) {
            this.itemId = itemId;
            return this;
        }

        /**
         * Builds the GearData with the specified parameters.
         *
         * @return The generated GearData
         */
        public GearData buildData() {
            if (itemLevel == null || slot == null) {
                throw new IllegalStateException("level and slot are required");
            }

            // Thrown consumables should not receive RPG stats
            if ("weapon".equalsIgnoreCase(slot) && itemId != null) {
                WeaponType resolved = WeaponType.fromItemIdOrUnknown(itemId);
                if (!resolved.isStatEligible()) {
                    throw new IllegalArgumentException(
                            "Thrown weapon '" + itemId + "' is not eligible for RPG stat generation");
                }
            }

            // Determine rarity
            GearRarity finalRarity = rarity != null
                    ? rarity
                    : generator.rarityRoller.roll(rarityBonus != null ? rarityBonus : 0.0);

            // Determine quality
            int finalQuality = quality != null
                    ? quality
                    : generator.qualityRoller.roll();

            // Resolve equipment type if not explicitly set but item ID is available
            EquipmentType resolvedEquipmentType = equipmentType;
            if (resolvedEquipmentType == null && itemId != null) {
                resolvedEquipmentType = generator.equipmentTypeResolver.resolve(itemId, slot);
            }

            // Resolve weapon type and roll element using balance config weights
            WeaponType weaponType = "weapon".equalsIgnoreCase(slot) && itemId != null
                    ? WeaponType.fromItemIdOrUnknown(itemId)
                    : null;
            ElementType element = (weaponType != null)
                    ? generator.implicitCalculator.rollElement(weaponType, generator.random)
                    : null;

            // Set element affinity for modifier biasing
            if (element != null) {
                AttributeType affinity = AttributeType.fromString(element.name());
                generator.modifierPool.setElementAffinity(affinity);
            }

            // Determine modifiers (with element affinity active if applicable)
            RarityConfig rarityConfig = generator.balanceConfig.rarityConfig(finalRarity);
            List<GearModifier> prefixes;
            List<GearModifier> suffixes;
            try {
                if (forcedPrefixes != null && forcedSuffixes != null) {
                    // Both forced - use as-is
                    prefixes = forcedPrefixes;
                    suffixes = forcedSuffixes;
                } else if (forcedPrefixes != null) {
                    // Prefixes forced, roll remaining suffixes
                    prefixes = forcedPrefixes;
                    int suffixCount = rarityConfig.maxModifiers() - prefixes.size();
                    suffixCount = Math.max(0, Math.min(suffixCount, rarityConfig.maxSuffixes()));
                    suffixes = generator.modifierPool.rollSuffixes(
                            suffixCount, itemLevel, slot, finalRarity, resolvedEquipmentType);
                } else if (forcedSuffixes != null) {
                    // Suffixes forced, roll remaining prefixes
                    suffixes = forcedSuffixes;
                    int prefixCount = rarityConfig.maxModifiers() - suffixes.size();
                    prefixCount = Math.max(0, Math.min(prefixCount, rarityConfig.maxPrefixes()));
                    prefixes = generator.modifierPool.rollPrefixes(
                            prefixCount, itemLevel, slot, finalRarity, resolvedEquipmentType);
                } else {
                    // Neither forced - use distribution to exactly hit max_modifiers
                    int[] distribution = generator.calculateModifierDistribution(rarityConfig);
                    int prefixCount = distribution[0];
                    int suffixCount = distribution[1];

                    prefixes = generator.modifierPool.rollPrefixes(
                            prefixCount, itemLevel, slot, finalRarity, resolvedEquipmentType);
                    suffixes = generator.modifierPool.rollSuffixes(
                            suffixCount, itemLevel, slot, finalRarity, resolvedEquipmentType);
                }
            } finally {
                generator.modifierPool.clearElementAffinity();
            }

            // Generate unique instance ID
            GearInstanceId instanceId = GearInstanceIdGenerator.generate();

            // Generate weapon and armor implicits (same logic as generateFromCategory)
            WeaponImplicit implicit = null;
            ArmorImplicit armorImplicit = null;

            if (weaponType != null && weaponType != WeaponType.SHIELD
                    && generator.implicitCalculator.isEnabled()
                    && generator.implicitCalculator.shouldHaveImplicit(weaponType)) {
                if (element != null) {
                    implicit = generator.implicitCalculator.calculateWithElement(
                            weaponType, itemLevel, element, generator.random);
                } else {
                    implicit = generator.implicitCalculator.calculate(weaponType, itemLevel, generator.random);
                }
            } else if (resolvedEquipmentType != null && resolvedEquipmentType.isOffhand()
                    && generator.implicitDefenseCalculator.isEnabled()) {
                armorImplicit = generator.implicitDefenseCalculator.calculateShield(itemLevel, generator.random);
            } else if (resolvedEquipmentType != null && resolvedEquipmentType.isArmor()
                    && generator.implicitDefenseCalculator.isEnabled()
                    && generator.implicitDefenseCalculator.shouldHaveImplicit(resolvedEquipmentType)) {
                ArmorMaterial material = resolvedEquipmentType.getArmorMaterial();
                EquipmentType.ArmorSlot armorSlot = resolvedEquipmentType.getArmorSlot();
                if (material != null && armorSlot != null) {
                    armorImplicit = generator.implicitDefenseCalculator.calculate(
                            material, armorSlot, itemLevel, generator.random);
                }
            }

            return GearData.builder()
                    .instanceId(instanceId)
                    .level(itemLevel)
                    .rarity(finalRarity)
                    .quality(finalQuality)
                    .prefixes(prefixes)
                    .suffixes(suffixes)
                    .implicit(implicit)
                    .armorImplicit(armorImplicit)
                    .baseItemId(itemId)
                    .build();
        }

        /**
         * Builds and applies to an ItemStack.
         *
         * <p>Also registers the custom item in the server-side asset map.
         * Registration happens BEFORE setting gear data to ensure the custom
         * item ID is recognized when the ItemStack is created.
         *
         * <p>If equipment type was not explicitly set, it will be auto-detected
         * from the item's ID for appropriate stat filtering.
         *
         * <p>When regenerating existing gear (rpg_gear_* ID), the baseItemId stored
         * in metadata is used for weapon type detection instead of the custom ID.
         */
        public ItemStack build(ItemStack baseItem) {
            // Auto-extract item ID for equipment type detection if not already set
            // First check for stored baseItemId (for regeneration of existing gear)
            // Then fall back to the item's current ID
            if (itemId == null) {
                String storedBaseId = GearUtils.getBaseItemId(baseItem);
                itemId = (storedBaseId != null) ? storedBaseId : baseItem.getItemId();
            }

            GearData data = buildData();

            // CRITICAL: Register BEFORE setting gear data (same as main generate method)
            generator.registerCustomItem(baseItem, data);

            // Inject Hexcode metadata BEFORE setGearData
            ItemStack itemForGear = generator.injectHexMetadataIfApplicable(baseItem, itemId, data.rarity());

            ItemStack result = GearUtils.setGearData(itemForGear, data);

            return result;
        }
    }
}
