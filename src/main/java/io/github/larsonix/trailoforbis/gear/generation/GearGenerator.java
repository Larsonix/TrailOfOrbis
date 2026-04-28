package io.github.larsonix.trailoforbis.gear.generation;

import io.github.larsonix.trailoforbis.compat.HexcodeCompat;
import io.github.larsonix.trailoforbis.compat.HexcodeMetadataInjector;
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

    private record ImplicitResult(@Nullable WeaponImplicit weapon, @Nullable ArmorImplicit armor) {}

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
     * <p>The equipment type determines which stat modifiers can roll:
     * <ul>
     *   <li><b>Daggers</b>: Crit, speed, no % physical damage</li>
     *   <li><b>Staves</b>: Spell damage, no physical damage</li>
     *   <li><b>Plate armor</b>: Health, armor, stability</li>
     *   <li><b>Leather armor</b>: Evasion, speed, stamina</li>
     * </ul>
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

        // Thrown consumables (bombs, darts, kunai) should not receive RPG stats
        if ("weapon".equalsIgnoreCase(slot)) {
            String resolvedId = GearUtils.getBaseItemId(baseItem);
            if (resolvedId == null) {
                resolvedId = baseItem.getItemId();
            }
            WeaponType resolved = WeaponType.fromItemIdOrUnknown(resolvedId);
            if (!resolved.isStatEligible()) {
                LOGGER.atFine().log("Skipping RPG stat generation for thrown weapon: %s", resolvedId);
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

        // Roll quality
        int quality = qualityRoller.roll();

        // Calculate modifier distribution to exactly hit max_modifiers
        RarityConfig rarityConfig = balanceConfig.rarityConfig(rarity);
        int[] distribution = calculateModifierDistribution(rarityConfig);
        int prefixCount = distribution[0];
        int suffixCount = distribution[1];

        // Roll modifiers with equipment type filtering
        List<GearModifier> prefixes = modifierPool.rollPrefixes(
                prefixCount, itemLevel, slot, rarity, effectiveEquipmentType);
        List<GearModifier> suffixes = modifierPool.rollSuffixes(
                suffixCount, itemLevel, slot, rarity, effectiveEquipmentType);

        // Generate unique instance ID
        GearInstanceId instanceId = GearInstanceIdGenerator.generate();

        // Determine the original base item ID for weapon type detection
        // If the item already has a baseItemId stored (regeneration), use that
        // Otherwise, use the item's current ID
        String baseItemIdForWeaponType = GearUtils.getBaseItemId(baseItem);
        if (baseItemIdForWeaponType == null) {
            baseItemIdForWeaponType = baseItem.getItemId();
        }

        // Generate weapon and armor implicits
        ImplicitResult implicits = generateImplicits(slot, baseItemIdForWeaponType, effectiveEquipmentType, itemLevel, random);
        WeaponImplicit implicit = implicits.weapon();
        ArmorImplicit armorImplicit = implicits.armor();

        // Build GearData with baseItemId for future regeneration
        GearData gearData = GearData.builder()
                .instanceId(instanceId)
                .level(itemLevel)
                .rarity(rarity)
                .quality(quality)
                .prefixes(prefixes)
                .suffixes(suffixes)
                .implicit(implicit)
                .armorImplicit(armorImplicit)
                .baseItemId(baseItemIdForWeaponType)
                .build();

        // CRITICAL: Register custom item BEFORE setting gear data
        // This ensures the item ID is recognized by Hytale's asset map before
        // the ItemStack gets the custom itemId. Uses sync registration to guarantee
        // the item is registered before we continue.
        registerCustomItem(baseItem, gearData);

        // Inject Hexcode metadata BEFORE setGearData — the 5-arg ItemStack constructor
        // copies the full BsonDocument, so hex metadata injected here survives
        ItemStack itemForGear = injectHexMetadataIfApplicable(baseItem, baseItemIdForWeaponType, rarity);

        // Write gear data to ItemStack (this changes the itemId to custom ID)
        // The registration above ensures Hytale will recognize this custom ID
        ItemStack result = GearUtils.setGearData(itemForGear, gearData);

        LOGGER.atFine().log("Generated %s %s gear (level %d, quality %d, %d mods)",
                rarity, slot, itemLevel, quality, prefixes.size() + suffixes.size());

        return result;
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

        // Create and register the custom item SYNCHRONOUSLY
        // This ensures the item is fully registered before being returned to callers
        itemRegistry.createAndRegisterSync(baseItemAsset, customItemId);
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

        // Calculate modifier distribution to exactly hit max_modifiers
        RarityConfig rarityConfig = balanceConfig.rarityConfig(rarity);
        int[] distribution = calculateModifierDistribution(rarityConfig);
        int prefixCount = distribution[0];
        int suffixCount = distribution[1];

        List<GearModifier> prefixes = modifierPool.rollPrefixes(
                prefixCount, itemLevel, slot, rarity, equipmentType);
        List<GearModifier> suffixes = modifierPool.rollSuffixes(
                suffixCount, itemLevel, slot, rarity, equipmentType);

        // Generate unique instance ID
        GearInstanceId instanceId = GearInstanceIdGenerator.generate();

        // Generate weapon and armor implicits
        ImplicitResult implicits = generateImplicits(slot, itemId, equipmentType, itemLevel, random);
        WeaponImplicit implicit = implicits.weapon();
        ArmorImplicit armorImplicit = implicits.armor();

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
    // IMPLICIT GENERATION
    // =========================================================================

    private ImplicitResult generateImplicits(
            @Nullable String slot,
            @Nullable String itemId,
            @Nullable EquipmentType equipmentType,
            int itemLevel,
            Random random) {

        // Weapon implicit
        WeaponImplicit weapon = null;
        if ("weapon".equalsIgnoreCase(slot) && implicitCalculator.isEnabled() && itemId != null) {
            WeaponType weaponType = WeaponType.fromItemIdOrUnknown(itemId);
            LOGGER.atFine().log("[IMPLICIT DEBUG] slot=%s, itemId=%s, weaponType=%s, shouldHaveImplicit=%s",
                    slot, itemId, weaponType, implicitCalculator.shouldHaveImplicit(weaponType));
            if (implicitCalculator.shouldHaveImplicit(weaponType)) {
                weapon = implicitCalculator.calculate(weaponType, itemLevel, random);
                LOGGER.atFine().log("[IMPLICIT DEBUG] Generated implicit: %s", weapon);
            }
        } else {
            LOGGER.atFine().log("[IMPLICIT DEBUG] Skipped weapon implicit: slot=%s, enabled=%s",
                    slot, implicitCalculator.isEnabled());
        }

        // Armor implicit
        ArmorImplicit armor = null;
        if (implicitDefenseCalculator.isEnabled() && equipmentType != null
                && implicitDefenseCalculator.shouldHaveImplicit(equipmentType)) {
            if (equipmentType.isOffhand()) {
                armor = implicitDefenseCalculator.calculateShield(itemLevel, random);
            } else {
                ArmorMaterial material = equipmentType.getArmorMaterial();
                ArmorSlot armorSlot = equipmentType.getArmorSlot();
                if (material != null && armorSlot != null) {
                    armor = implicitDefenseCalculator.calculate(material, armorSlot, itemLevel, random);
                }
            }
            LOGGER.atFine().log("[IMPLICIT DEBUG] Generated armor implicit: %s for %s",
                    armor, equipmentType);
        }

        return new ImplicitResult(weapon, armor);
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

            // Determine modifiers
            RarityConfig rarityConfig = generator.balanceConfig.rarityConfig(finalRarity);
            List<GearModifier> prefixes;
            List<GearModifier> suffixes;

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

            // Generate unique instance ID
            GearInstanceId instanceId = GearInstanceIdGenerator.generate();

            // Generate weapon and armor implicits
            ImplicitResult implicits = generator.generateImplicits(slot, itemId, resolvedEquipmentType, itemLevel, generator.random);
            WeaponImplicit implicit = implicits.weapon();
            ArmorImplicit armorImplicit = implicits.armor();

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
