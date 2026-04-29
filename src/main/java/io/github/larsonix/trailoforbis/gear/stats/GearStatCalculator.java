package io.github.larsonix.trailoforbis.gear.stats;

import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig;
import io.github.larsonix.trailoforbis.gear.equipment.EquipmentValidator;
import io.github.larsonix.trailoforbis.gear.item.ItemRegistryService;
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.model.GearModifier;
import io.github.larsonix.trailoforbis.gear.model.ArmorImplicit;
import io.github.larsonix.trailoforbis.gear.model.WeaponImplicit;
import io.github.larsonix.trailoforbis.gear.util.GearUtils;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Calculates total stat bonuses from equipped gear.
 *
 * <p>Handles:
 * <ul>
 *   <li>Reading gear data from all equipped slots</li>
 *   <li>Extracting modifier values</li>
 *   <li>Applying quality multiplier</li>
 *   <li>Summing bonuses by stat ID</li>
 *   <li>Separating flat vs percent bonuses</li>
 *   <li>Skipping broken items</li>
 *   <li>Skipping items that don't meet requirements</li>
 * </ul>
 */
public final class GearStatCalculator {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final GearBalanceConfig balanceConfig;
    private final EquipmentValidator validator;
    private final ItemRegistryService itemRegistryService;

    /**
     * Creates a GearStatCalculator.
     *
     * @param balanceConfig The gear balance configuration
     * @param validator The equipment validator (to check requirements)
     * @param itemRegistryService The item registry service for base item ID lookup (can be null)
     */
    public GearStatCalculator(
            GearBalanceConfig balanceConfig,
            EquipmentValidator validator,
            @Nullable ItemRegistryService itemRegistryService) {
        this.balanceConfig = Objects.requireNonNull(balanceConfig);
        this.validator = Objects.requireNonNull(validator);
        this.itemRegistryService = itemRegistryService;  // Can be null
    }

    /**
     * Calculates all stat bonuses for a player's equipped gear.
     *
     * @param playerId The player's UUID
     * @param inventory The player's inventory
     * @return Calculated bonuses containing flat and percent maps
     */
    public GearBonuses calculateBonuses(UUID playerId, Inventory inventory) {
        Map<String, Double> flatBonuses = new HashMap<>();
        Map<String, Double> percentBonuses = new HashMap<>();
        double weaponBaseDamage = 0.0;
        String weaponItemId = null;
        boolean isHoldingRpgGear = false;

        // Process armor
        processContainer(playerId, inventory.getArmor(), flatBonuses, percentBonuses);

        // Process active weapon and extract implicit damage + item ID
        ItemStack weapon = inventory.getItemInHand();
        WeaponResult weaponResult = processWeapon(playerId, weapon, flatBonuses, percentBonuses);
        weaponBaseDamage = weaponResult.baseDamage;
        weaponItemId = weaponResult.vanillaItemId;
        isHoldingRpgGear = weaponResult.isRpgGear;

        // Process ALL utility items (offhand — shields, etc.)
        // Must iterate the container directly, NOT use getUtilityItem().
        // getUtilityItem() only returns the "active" utility — when the player
        // isn't actively using the offhand, it returns null and stats vanish.
        // Shields should always provide stats while equipped, like armor.
        var utilContainer = inventory.getUtility();
        if (utilContainer != null) {
            short utilCap = utilContainer.getCapacity();
            for (short i = 0; i < utilCap; i++) {
                ItemStack utilItem = utilContainer.getItemStack(i);
                if (utilItem != null && !utilItem.isEmpty()) {
                    String uid = utilItem.getItemId();
                    boolean isRpg = GearUtils.isRpgGear(utilItem);
                    LOGGER.atInfo().log("[UTIL-DIAG] Slot %d: id=%s, isRpg=%s", i, uid, isRpg);
                }
            }
            processContainer(playerId, utilContainer, flatBonuses, percentBonuses);
        } else {
            LOGGER.atInfo().log("[UTIL-DIAG] Utility container is null");
        }

        return new GearBonuses(
                Collections.unmodifiableMap(flatBonuses),
                Collections.unmodifiableMap(percentBonuses),
                weaponBaseDamage,
                weaponItemId,
                isHoldingRpgGear
        );
    }

    /**
     * Result from processing a weapon, containing damage, item ID, and RPG gear flag.
     *
     * @param baseDamage The weapon's implicit damage (0 if requirements not met)
     * @param vanillaItemId The vanilla item ID for attack effectiveness lookup
     * @param isRpgGear True if the weapon is RPG-generated gear (regardless of requirements)
     */
    private record WeaponResult(double baseDamage, @Nullable String vanillaItemId, boolean isRpgGear) {
        static final WeaponResult EMPTY = new WeaponResult(0.0, null, false);
    }

    /**
     * Processes all items in a container.
     */
    private void processContainer(
            UUID playerId,
            ItemContainer container,
            Map<String, Double> flatBonuses,
            Map<String, Double> percentBonuses
    ) {
        if (container == null) {
            return;
        }

        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack item = container.getItemStack(slot);
            processItem(playerId, item, flatBonuses, percentBonuses);
        }
    }

    /**
     * Processes a single item and adds its bonuses.
     */
    private void processItem(
            UUID playerId,
            ItemStack item,
            Map<String, Double> flatBonuses,
            Map<String, Double> percentBonuses
    ) {
        if (item == null || item.isEmpty()) {
            return;
        }

        // Read gear data
        Optional<GearData> gearDataOpt = GearUtils.readGearData(item);
        if (gearDataOpt.isEmpty()) {
            return;  // Non-RPG item
        }

        GearData gearData = gearDataOpt.get();

        // Check if player still meets requirements
        // (items equipped before respec may no longer be valid)
        if (!validator.canEquip(playerId, gearData)) {
            LOGGER.atInfo().log("[UTIL-DIAG] Skipping %s - requirements not met", gearData.getItemId());
            return;  // Skip bonuses for invalid equipment
        }

        // Check if item is broken
        if (isItemBroken(item)) {
            LOGGER.atFine().log("Skipping gear bonuses - item is broken");
            return;  // Broken items provide no bonuses
        }

        // Calculate quality multiplier
        double qualityMultiplier = gearData.qualityMultiplier();

        // Process all modifiers
        processModifiers(gearData.prefixes(), qualityMultiplier, flatBonuses, percentBonuses);
        processModifiers(gearData.suffixes(), qualityMultiplier, flatBonuses, percentBonuses);

        // Process armor implicit defense (quality-independent, like weapon implicit)
        ArmorImplicit armorImplicit = gearData.armorImplicit();
        if (armorImplicit != null) {
            flatBonuses.merge(armorImplicit.defenseType().toLowerCase(), armorImplicit.rolledValue(), Double::sum);
        }

        // Process weapon implicit for non-weapon items (shields use this for block_chance).
        // Weapons handle their implicit separately in processWeapon() — this covers
        // utility/offhand items that have a weaponImplicit with a non-damage stat.
        if (gearData.hasWeaponImplicit()) {
            var implicit = gearData.implicit();
            if (implicit != null && !implicit.isPhysicalDamage() && !implicit.isSpellDamage()) {
                // Non-damage implicit (e.g., block_chance for shields) → apply as flat stat
                flatBonuses.merge(implicit.damageType().toLowerCase(), implicit.rolledValue(), Double::sum);
            }
        }
    }

    /**
     * Processes a weapon item and returns its implicit base damage and vanilla item ID.
     *
     * <p>This is separate from processItem because weapons have an implicit damage
     * stat that completely replaces vanilla weapon damage in RPG combat. The vanilla
     * item ID is also extracted for attack effectiveness calculation.
     *
     * @return A WeaponResult containing the implicit damage and vanilla item ID
     */
    private WeaponResult processWeapon(
            UUID playerId,
            ItemStack item,
            Map<String, Double> flatBonuses,
            Map<String, Double> percentBonuses
    ) {
        if (item == null || item.isEmpty()) {
            LOGGER.atFine().log("[WEAPON DEBUG] Item is null/empty, returning EMPTY");
            return WeaponResult.EMPTY;
        }

        String itemId = (item.getItem() != null) ? item.getItem().getId() : "null";
        LOGGER.atFine().log("[WEAPON DEBUG] Processing weapon: itemId=%s, player=%s", itemId, playerId);

        // Read gear data FIRST - we need baseItemId for attack effectiveness lookup
        Optional<GearData> gearDataOpt = GearUtils.readGearData(item);

        // Determine vanilla item ID for attack effectiveness lookup
        // Priority chain:
        // 1) GearData.baseItemId (stored in metadata - most reliable)
        // 2) ItemRegistry lookup (for legacy items or reconnect scenarios)
        // 3) Current item ID (works for vanilla weapons)
        String vanillaItemId = resolveVanillaItemId(item, gearDataOpt);

        if (gearDataOpt.isEmpty()) {
            // Non-RPG item (vanilla weapon) - still return the item ID for vanilla effectiveness
            LOGGER.atFine().log("[WEAPON DEBUG] No gear data found for %s, returning vanilla result", itemId);
            return new WeaponResult(0.0, vanillaItemId, false);
        }

        GearData gearData = gearDataOpt.get();
        LOGGER.atFine().log("[WEAPON DEBUG] Gear data found: level=%d, rarity=%s, implicit=%s",
                gearData.level(), gearData.rarity(), gearData.implicit());

        // Check if player still meets requirements
        // IMPORTANT: Even if requirements aren't met, mark this as RPG gear!
        // This ensures the damage system uses RPG path with 0 damage instead of vanilla damage.
        if (!validator.canEquip(playerId, gearData)) {
            LOGGER.atFine().log("[WEAPON DEBUG] Player cannot equip - requirements not met, isRpgGear=true, damage=0");
            return new WeaponResult(0.0, vanillaItemId, true);
        }

        // Check if item is broken - still mark as RPG gear but provide no damage
        if (isItemBroken(item)) {
            LOGGER.atFine().log("Skipping weapon bonuses - item is broken, isRpgGear=true, damage=0");
            return new WeaponResult(0.0, vanillaItemId, true);
        }

        // Calculate quality multiplier
        double qualityMultiplier = gearData.qualityMultiplier();

        // Process all modifiers (same as armor/utility)
        processModifiers(gearData.prefixes(), qualityMultiplier, flatBonuses, percentBonuses);
        processModifiers(gearData.suffixes(), qualityMultiplier, flatBonuses, percentBonuses);

        // Extract implicit damage if present
        WeaponImplicit implicit = gearData.implicit();
        LOGGER.atFine().log("[STAT CALC DEBUG] Reading weapon implicit: gearData.implicit()=%s, vanillaItemId=%s",
                implicit, vanillaItemId);
        if (implicit != null) {
            // Implicit damage is NOT affected by quality (per gear-balance.yml documentation)
            // Quality only affects modifiers (prefixes/suffixes), not the weapon's base damage
            double baseDamage = implicit.rolledValue();
            LOGGER.atFine().log("[STAT CALC DEBUG] Weapon implicit damage: %.1f (rolled value, quality does NOT apply)",
                    baseDamage);
            return new WeaponResult(baseDamage, vanillaItemId, true);
        }

        // Weapon without implicit (shouldn't happen for properly generated gear)
        // Still mark as RPG gear to use RPG damage path
        LOGGER.atFine().log("[STAT CALC DEBUG] Weapon has NO implicit! baseItemId=%s, level=%d, rarity=%s",
                gearData.getBaseItemId(), gearData.level(), gearData.rarity());
        return new WeaponResult(0.0, vanillaItemId, true);
    }

    /**
     * Resolves the vanilla item ID for attack effectiveness lookup.
     *
     * <p>The vanilla item ID is needed to look up the weapon's attack profile, which
     * contains the geometric mean reference damage for calculating attack effectiveness.
     *
     * <p>Priority chain:
     * <ol>
     *   <li>GearData.baseItemId - Stored in item metadata, most reliable for new items</li>
     *   <li>ItemRegistry lookup - For legacy items or reconnect scenarios where baseItemId
     *       might not be in metadata but is tracked in the registry</li>
     *   <li>Current item ID - Works for vanilla weapons (not rpg_gear_*)</li>
     * </ol>
     *
     * @param item The item stack
     * @param gearDataOpt Optional gear data from the item
     * @return The vanilla item ID, or null if not determinable
     */
    @Nullable
    private String resolveVanillaItemId(ItemStack item, Optional<GearData> gearDataOpt) {
        // Priority 1: GearData.baseItemId (stored in metadata)
        if (gearDataOpt.isPresent() && gearDataOpt.get().getBaseItemId() != null) {
            String baseItemId = gearDataOpt.get().getBaseItemId();
            LOGGER.atFine().log("Vanilla item ID from GearData: %s", baseItemId);
            return baseItemId;
        }

        // Check if we have a valid item at all
        if (item.getItem() == null) {
            return null;
        }

        String currentId = item.getItem().getId();

        // Priority 2: ItemRegistry lookup (for rpg_gear_* items without baseItemId in metadata)
        if (currentId.startsWith("rpg_gear_") && itemRegistryService != null) {
            String registeredBaseId = itemRegistryService.getBaseItemId(currentId);
            if (registeredBaseId != null) {
                LOGGER.atFine().log("Vanilla item ID from ItemRegistry: %s (custom: %s)",
                    registeredBaseId, currentId);
                return registeredBaseId;
            } else {
                LOGGER.atWarning().log(
                    "Failed to resolve vanilla item ID for %s - not in GearData or ItemRegistry",
                    currentId);
            }
        }

        // Priority 3: Use current ID (works for vanilla weapons)
        LOGGER.atFine().log("Using current item ID as vanilla ID: %s", currentId);
        return currentId;
    }

    /**
     * Processes a list of modifiers.
     */
    private void processModifiers(
            List<GearModifier> modifiers,
            double qualityMultiplier,
            Map<String, Double> flatBonuses,
            Map<String, Double> percentBonuses
    ) {
        for (GearModifier mod : modifiers) {
            // Apply quality multiplier to value
            double adjustedValue = mod.value() * qualityMultiplier;

            // Use the modifier's statType to determine flat vs percent
            if (mod.isFlat()) {
                flatBonuses.merge(mod.statId().toLowerCase(), adjustedValue, Double::sum);
            } else {
                percentBonuses.merge(mod.statId().toLowerCase(), adjustedValue, Double::sum);
            }
        }
    }

    /**
     * Checks if an item is broken (durability depleted).
     */
    private boolean isItemBroken(ItemStack item) {
        double durability = item.getDurability();
        double maxDurability = item.getMaxDurability();

        if (maxDurability <= 0) {
            return false;  // Item has no durability system
        }

        return durability <= 0;
    }

    // =========================================================================
    // RESULT CLASS
    // =========================================================================

    /**
     * Contains calculated gear bonuses separated by type.
     *
     * @param flatBonuses Flat stat bonuses from gear modifiers
     * @param percentBonuses Percent stat bonuses from gear modifiers
     * @param weaponBaseDamage Base damage from equipped weapon's implicit (0.0 if no weapon or unequippable)
     * @param weaponItemId The vanilla item ID of the equipped weapon (for attack effectiveness lookup)
     * @param isHoldingRpgGear True if the weapon is RPG-generated gear (regardless of whether requirements are met)
     */
    public record GearBonuses(
            Map<String, Double> flatBonuses,
            Map<String, Double> percentBonuses,
            double weaponBaseDamage,
            @Nullable String weaponItemId,
            boolean isHoldingRpgGear
    ) {
        /**
         * Empty bonuses instance.
         */
        public static final GearBonuses EMPTY = new GearBonuses(Map.of(), Map.of(), 0.0, null, false);

        /**
         * Checks if there are any bonuses (excluding weapon base damage).
         */
        public boolean isEmpty() {
            return flatBonuses.isEmpty() && percentBonuses.isEmpty();
        }

        /**
         * Gets a flat bonus value.
         */
        public double getFlat(String statId) {
            return flatBonuses.getOrDefault(statId.toLowerCase(), 0.0);
        }

        /**
         * Gets a percent bonus value.
         */
        public double getPercent(String statId) {
            return percentBonuses.getOrDefault(statId.toLowerCase(), 0.0);
        }

        /**
         * Checks if there is a weapon equipped with implicit damage.
         */
        public boolean hasWeaponDamage() {
            return weaponBaseDamage > 0.0;
        }

        /**
         * Checks if a vanilla weapon item ID is available.
         *
         * <p>This is used to look up the weapon's attack effectiveness profile.
         *
         * @return True if weaponItemId is not null
         */
        public boolean hasWeaponItemId() {
            return weaponItemId != null;
        }
    }
}
