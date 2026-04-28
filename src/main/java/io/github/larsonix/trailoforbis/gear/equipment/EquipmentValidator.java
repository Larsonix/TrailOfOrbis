package io.github.larsonix.trailoforbis.gear.equipment;

import io.github.larsonix.trailoforbis.api.ServiceRegistry;
import io.github.larsonix.trailoforbis.attributes.AttributeManager;
import io.github.larsonix.trailoforbis.attributes.AttributeType;
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.util.GearUtils;
import io.github.larsonix.trailoforbis.leveling.api.LevelingService;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.inventory.ItemStack;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Predicate;

/**
 * Validates whether a player can equip gear based on level and attribute requirements.
 *
 * <p>This class is the main entry point for equipment validation checks.
 * It combines:
 * <ul>
 *   <li>{@link LevelingService} - Provides player's current level (via ServiceRegistry)</li>
 *   <li>{@link RequirementCalculator} - Determines attribute requirements</li>
 *   <li>{@link AttributeManager} - Provides player's current attributes</li>
 * </ul>
 *
 * <p>Validation order:
 * <ol>
 *   <li>Level requirement (player level &gt;= item level)</li>
 *   <li>Attribute requirements (Strength, Intelligence, etc.)</li>
 * </ol>
 *
 * <p>If the player doesn't meet the level requirement, attribute checks are still
 * performed for complete feedback, but {@link #canEquip} returns false.
 */
public final class EquipmentValidator {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Default player level when LevelingService is unavailable */
    private static final int DEFAULT_PLAYER_LEVEL = 1;

    private final RequirementCalculator requirementCalculator;
    private final AttributeManager attributeManager;
    private final Predicate<UUID> bypassCheck;

    /**
     * Creates an EquipmentValidator.
     *
     * @param requirementCalculator The requirement calculator
     * @param attributeManager The attribute manager (for player attribute lookup)
     * @param bypassCheck Predicate that returns true if the player should bypass all requirements
     *                    (e.g., Creative mode players when the config toggle is enabled)
     */
    public EquipmentValidator(
            RequirementCalculator requirementCalculator,
            AttributeManager attributeManager,
            Predicate<UUID> bypassCheck
    ) {
        this.requirementCalculator = Objects.requireNonNull(requirementCalculator);
        this.attributeManager = Objects.requireNonNull(attributeManager);
        this.bypassCheck = Objects.requireNonNull(bypassCheck);
    }

    /**
     * Creates an EquipmentValidator with no bypass.
     *
     * @param requirementCalculator The requirement calculator
     * @param attributeManager The attribute manager (for player attribute lookup)
     */
    public EquipmentValidator(
            RequirementCalculator requirementCalculator,
            AttributeManager attributeManager
    ) {
        this(requirementCalculator, attributeManager, uuid -> false);
    }

    // =========================================================================
    // MAIN VALIDATION METHODS
    // =========================================================================

    /**
     * Checks if a player can equip an item.
     *
     * @param playerId The player's UUID
     * @param item The item to check
     * @return true if the player meets all requirements, false otherwise
     */
    public boolean canEquip(UUID playerId, ItemStack item) {
        if (item == null || item.isEmpty()) {
            return true;  // Can always "equip" nothing
        }

        if (bypassCheck.test(playerId)) {
            return true;
        }

        // Read gear data
        Optional<GearData> gearDataOpt = GearUtils.readGearData(item);
        if (gearDataOpt.isEmpty()) {
            return true;  // Non-RPG item, always allowed
        }

        return canEquip(playerId, gearDataOpt.get());
    }

    /**
     * Checks if a player can equip gear with known data.
     *
     * <p>Checks both level requirements (player level &gt;= item level) and
     * attribute requirements.
     *
     * @param playerId The player's UUID
     * @param gearData The gear data
     * @return true if the player meets all requirements (level AND attributes)
     */
    public boolean canEquip(UUID playerId, GearData gearData) {
        if (bypassCheck.test(playerId)) {
            return true;
        }

        // Check level requirement first (cheaper check)
        int playerLevel = getPlayerLevel(playerId);
        int requiredLevel = gearData.level();

        if (playerLevel < requiredLevel) {
            return false;
        }

        // Check attribute requirements
        Map<AttributeType, Integer> requirements = requirementCalculator.calculateRequirements(gearData);

        if (requirements.isEmpty()) {
            return true;
        }

        // Get player attributes
        Map<AttributeType, Integer> playerAttributes = attributeManager.getPlayerAttributes(playerId);

        // Check all requirements
        for (Map.Entry<AttributeType, Integer> req : requirements.entrySet()) {
            int playerValue = playerAttributes.getOrDefault(req.getKey(), 0);
            if (playerValue < req.getValue()) {
                return false;
            }
        }

        return true;
    }

    /**
     * Gets the player's current level from the LevelingService.
     *
     * <p>If the LevelingService is not available (e.g., during early startup or
     * if the leveling system is disabled), returns {@link #DEFAULT_PLAYER_LEVEL}.
     *
     * @param playerId The player's UUID
     * @return The player's level, or 1 if unavailable
     */
    private int getPlayerLevel(UUID playerId) {
        return ServiceRegistry.get(LevelingService.class)
                .map(service -> service.getLevel(playerId))
                .orElse(DEFAULT_PLAYER_LEVEL);
    }

    /**
     * Gets the requirements for an item.
     *
     * @param item The item to check
     * @return Map of attribute requirements, empty if none or non-RPG item
     */
    public Map<AttributeType, Integer> getRequirements(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return Map.of();
        }

        Optional<GearData> gearDataOpt = GearUtils.readGearData(item);
        if (gearDataOpt.isEmpty()) {
            return Map.of();
        }

        return requirementCalculator.calculateRequirements(gearDataOpt.get());
    }

    /**
     * Checks each requirement and returns which are met/unmet.
     *
     * @param playerId The player's UUID
     * @param item The item to check
     * @return Validation result with detailed requirement status
     */
    public ValidationResult checkRequirements(UUID playerId, ItemStack item) {
        if (item == null || item.isEmpty()) {
            return ValidationResult.NO_REQUIREMENTS;
        }

        if (bypassCheck.test(playerId)) {
            return ValidationResult.NO_REQUIREMENTS;
        }

        Optional<GearData> gearDataOpt = GearUtils.readGearData(item);
        if (gearDataOpt.isEmpty()) {
            return ValidationResult.NO_REQUIREMENTS;
        }

        return checkRequirements(playerId, gearDataOpt.get());
    }

    /**
     * Checks each requirement and returns which are met/unmet.
     *
     * <p>Includes both level and attribute requirements.
     *
     * @param playerId The player's UUID
     * @param gearData The gear data
     * @return Validation result with detailed requirement status
     */
    public ValidationResult checkRequirements(UUID playerId, GearData gearData) {
        if (bypassCheck.test(playerId)) {
            return ValidationResult.NO_REQUIREMENTS;
        }

        // Check level requirement
        int playerLevel = getPlayerLevel(playerId);
        int requiredLevel = gearData.level();
        boolean levelMet = playerLevel >= requiredLevel;
        LevelRequirementStatus levelStatus = new LevelRequirementStatus(requiredLevel, playerLevel, levelMet);

        // Check attribute requirements
        Map<AttributeType, Integer> requirements = requirementCalculator.calculateRequirements(gearData);

        if (requirements.isEmpty()) {
            // No attribute requirements, but may still have level requirement
            return new ValidationResult(levelMet, Map.of(), levelStatus);
        }

        Map<AttributeType, Integer> playerAttributes = attributeManager.getPlayerAttributes(playerId);

        Map<AttributeType, RequirementStatus> statuses = new EnumMap<>(AttributeType.class);
        boolean allAttributesMet = true;

        for (Map.Entry<AttributeType, Integer> req : requirements.entrySet()) {
            AttributeType attr = req.getKey();
            int required = req.getValue();
            int actual = playerAttributes.getOrDefault(attr, 0);
            boolean met = actual >= required;

            statuses.put(attr, new RequirementStatus(required, actual, met));

            if (!met) {
                allAttributesMet = false;
            }
        }

        // canEquip = level met AND all attributes met
        boolean canEquip = levelMet && allAttributesMet;

        return new ValidationResult(canEquip, Collections.unmodifiableMap(statuses), levelStatus);
    }

    // =========================================================================
    // RE-VALIDATION ON ATTRIBUTE CHANGE
    // =========================================================================

    /**
     * Called when player attributes change (respec, level up, debuff, etc.).
     *
     * <p>Checks all equipped gear and returns items that no longer meet requirements.
     *
     * @param playerId The player's UUID
     * @param equippedItems All currently equipped items
     * @return List of items that should be unequipped
     */
    public List<ItemStack> validateEquippedGear(UUID playerId, List<ItemStack> equippedItems) {
        if (bypassCheck.test(playerId)) {
            return List.of();
        }

        List<ItemStack> toUnequip = new ArrayList<>();

        for (ItemStack item : equippedItems) {
            if (item == null || item.isEmpty()) {
                continue;
            }

            if (!canEquip(playerId, item)) {
                toUnequip.add(item);
                LOGGER.atInfo().log("Player %s no longer meets requirements for item",
                        playerId);
            }
        }

        return toUnequip;
    }

    // =========================================================================
    // RESULT CLASSES
    // =========================================================================

    /**
     * Result of a requirement validation check.
     *
     * <p>Contains both level and attribute requirement statuses.
     */
    public record ValidationResult(
            boolean canEquip,
            Map<AttributeType, RequirementStatus> requirements,
            @Nullable LevelRequirementStatus levelRequirement
    ) {
        /**
         * Special instance for items with no requirements.
         */
        public static final ValidationResult NO_REQUIREMENTS =
                new ValidationResult(true, Map.of(), null);

        /**
         * Legacy constructor for backwards compatibility (no level requirement).
         *
         * @deprecated Use the 3-argument constructor with levelRequirement
         */
        @Deprecated
        public ValidationResult(boolean canEquip, Map<AttributeType, RequirementStatus> requirements) {
            this(canEquip, requirements, null);
        }

        public Map<AttributeType, RequirementStatus> getUnmetRequirements() {
            Map<AttributeType, RequirementStatus> unmet = new EnumMap<>(AttributeType.class);
            for (Map.Entry<AttributeType, RequirementStatus> entry : requirements.entrySet()) {
                if (!entry.getValue().met()) {
                    unmet.put(entry.getKey(), entry.getValue());
                }
            }
            return Collections.unmodifiableMap(unmet);
        }

        public Map<AttributeType, RequirementStatus> getMetRequirements() {
            Map<AttributeType, RequirementStatus> met = new EnumMap<>(AttributeType.class);
            for (Map.Entry<AttributeType, RequirementStatus> entry : requirements.entrySet()) {
                if (entry.getValue().met()) {
                    met.put(entry.getKey(), entry.getValue());
                }
            }
            return Collections.unmodifiableMap(met);
        }

        public boolean hasRequirements() {
            return !requirements.isEmpty();
        }

        public boolean hasLevelRequirement() {
            return levelRequirement != null;
        }

        public boolean isLevelRequirementMet() {
            return levelRequirement == null || levelRequirement.met();
        }

        public boolean areAttributeRequirementsMet() {
            for (RequirementStatus status : requirements.values()) {
                if (!status.met()) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * Status of a single attribute requirement.
     */
    public record RequirementStatus(
            int required,
            int actual,
            boolean met
    ) {
        /**
         * Gets the deficit (how many more points needed).
         *
         * @return Positive value if not met, 0 if met
         */
        public int deficit() {
            return met ? 0 : required - actual;
        }

        /**
         * Gets the surplus (how many extra points).
         *
         * @return Positive value if met with extra, 0 if not met
         */
        public int surplus() {
            return met ? actual - required : 0;
        }
    }

    /**
     * Status of the level requirement.
     */
    public record LevelRequirementStatus(
            int requiredLevel,
            int playerLevel,
            boolean met
    ) {
        /**
         * Gets the level deficit (how many more levels needed).
         *
         * @return Positive value if not met, 0 if met
         */
        public int deficit() {
            return met ? 0 : requiredLevel - playerLevel;
        }

        /**
         * Gets the level surplus (how many extra levels).
         *
         * @return Positive value if met with extra, 0 if not met
         */
        public int surplus() {
            return met ? playerLevel - requiredLevel : 0;
        }
    }
}
