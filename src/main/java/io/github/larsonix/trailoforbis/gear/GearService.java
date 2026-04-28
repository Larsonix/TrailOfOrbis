package io.github.larsonix.trailoforbis.gear;

import io.github.larsonix.trailoforbis.attributes.AttributeType;
import io.github.larsonix.trailoforbis.gear.equipment.EquipmentValidator.ValidationResult;
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.gear.stats.GearStatCalculator.GearBonuses;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Public API interface for the RPG Gear System.
 *
 * <p>Provides a unified entry point for external code to interact with
 * the gear system without coupling to internal implementations.
 *
 * <p>Access via {@code ServiceRegistry.get(GearService.class)}.
 *
 * <h2>Key Operations</h2>
 * <ul>
 *   <li>Gear generation</li>
 *   <li>Gear data reading</li>
 *   <li>Equipment validation</li>
 *   <li>Stat calculation</li>
 *   <li>Tooltip formatting</li>
 * </ul>
 */
public interface GearService {

    // =========================================================================
    // GEAR DATA ACCESS
    // =========================================================================

    /**
     * Checks if an item is RPG gear.
     *
     * @param item The item to check (may be null)
     * @return true if the item has RPG gear data
     */
    boolean isGear(@javax.annotation.Nullable ItemStack item);

    /**
     * Reads gear data from an item.
     *
     * @param item The item to read from (may be null)
     * @return The gear data, or empty if not gear or corrupted
     */
    @Nonnull
    Optional<GearData> getGearData(@javax.annotation.Nullable ItemStack item);

    /**
     * Sets gear data on an item.
     *
     * <p>Returns a new ItemStack with the gear data applied.
     *
     * @param item The base item (must not be null)
     * @param gearData The gear data to apply (must not be null)
     * @return New ItemStack with gear data
     * @throws NullPointerException if item or gearData is null
     */
    @Nonnull
    ItemStack setGearData(@Nonnull ItemStack item, @Nonnull GearData gearData);

    /**
     * Removes gear data from an item.
     *
     * @param item The item to cleanse (must not be null)
     * @return New ItemStack without gear data
     */
    @Nonnull
    ItemStack removeGearData(@Nonnull ItemStack item);

    // =========================================================================
    // GEAR GENERATION
    // =========================================================================

    /**
     * Generates random gear.
     *
     * @param baseItem The base item to apply gear data to
     * @param itemLevel The item level (1-100)
     * @param slot The gear slot (e.g., "weapon", "head", "chest")
     * @return New ItemStack with random gear data
     */
    @Nonnull
    ItemStack generateGear(@Nonnull ItemStack baseItem, int itemLevel, @Nonnull String slot);

    /**
     * Generates gear with a specific rarity.
     *
     * @param baseItem The base item to apply gear data to
     * @param itemLevel The item level (1-100)
     * @param slot The gear slot
     * @param rarity The forced rarity
     * @return New ItemStack with gear data at specified rarity
     */
    @Nonnull
    ItemStack generateGear(@Nonnull ItemStack baseItem, int itemLevel, @Nonnull String slot, @Nonnull GearRarity rarity);

    /**
     * Generates gear with rarity bonus.
     *
     * @param baseItem The base item to apply gear data to
     * @param itemLevel The item level (1-100)
     * @param slot The gear slot
     * @param rarityBonus Bonus to rarity roll (0.0 = no bonus)
     * @return New ItemStack with gear data
     */
    @Nonnull
    ItemStack generateGear(@Nonnull ItemStack baseItem, int itemLevel, @Nonnull String slot, double rarityBonus);

    /**
     * Generates just the GearData (without applying to item).
     *
     * @param itemLevel 1-100
     */
    @Nonnull
    GearData generateGearData(int itemLevel, @Nonnull String slot, @Nonnull GearRarity rarity);

    // =========================================================================
    // EQUIPMENT VALIDATION
    // =========================================================================

    /**
     * Checks if a player can equip an item.
     *
     * @param playerId The player's UUID
     * @param item The item to check
     * @return true if the player meets all requirements
     */
    boolean canEquip(@Nonnull UUID playerId, @javax.annotation.Nullable ItemStack item);

    /**
     * Gets detailed requirement validation result.
     *
     * @param playerId The player's UUID
     * @param item The item to check
     * @return Detailed validation result with requirement statuses
     */
    @Nonnull
    ValidationResult checkRequirements(@Nonnull UUID playerId, @javax.annotation.Nullable ItemStack item);

    /**
     * Gets the requirements for an item.
     *
     * @param item The item to check
     * @return Map of attribute requirements, empty if none
     */
    @Nonnull
    Map<AttributeType, Integer> getRequirements(@javax.annotation.Nullable ItemStack item);

    // =========================================================================
    // REQUIREMENT BYPASS (Creative Mode)
    // =========================================================================

    /**
     * Adds a requirement bypass for a player.
     *
     * <p>While bypassed, the player can equip any gear regardless of level
     * or attribute requirements. Used for Creative mode players.
     *
     * @param playerId The player's UUID
     */
    void addRequirementBypass(@Nonnull UUID playerId);

    /**
     * Removes a requirement bypass for a player.
     *
     * <p>The player will once again be subject to gear requirement checks.
     *
     * @param playerId The player's UUID
     */
    void removeRequirementBypass(@Nonnull UUID playerId);

    // =========================================================================
    // STAT CALCULATION
    // =========================================================================

    /**
     * Calculates total gear bonuses for a player's equipped items.
     *
     * @param playerId The player's UUID
     * @param inventory The player's inventory
     * @return Calculated bonuses (flat and percent)
     */
    @Nonnull
    GearBonuses calculateGearBonuses(@Nonnull UUID playerId, @Nonnull Inventory inventory);

    // =========================================================================
    // RICH TOOLTIP FORMATTING (MESSAGE API)
    // =========================================================================

    /**
     * Builds a rich tooltip for gear using Hytale's native Message API.
     *
     * <p>Returns a fully styled Message with:
     * <ul>
     *   <li>Rarity badge (colored, bold)</li>
     *   <li>Item level</li>
     *   <li>Quality rating (tier-colored)</li>
     *   <li>Stat modifiers (green/red)</li>
     *   <li>Requirements (neutral gray, no player context)</li>
     * </ul>
     *
     * @param gearData The gear data to format
     * @return Styled tooltip as a Message
     */
    @Nonnull
    Message buildRichTooltip(@Nonnull GearData gearData);

    /**
     * Builds a rich tooltip with player context.
     *
     * <p>Requirements are color-coded based on whether the player meets them:
     * <ul>
     *   <li>Green (✓) - requirement met</li>
     *   <li>Red (✗) - requirement not met</li>
     * </ul>
     *
     * @param gearData The gear data to format
     * @param playerId The player viewing the tooltip
     * @return Styled tooltip as a Message
     */
    @Nonnull
    Message buildRichTooltip(@Nonnull GearData gearData, @Nonnull UUID playerId);

    /**
     * Builds a styled item name using Hytale's native Message API.
     *
     * <p>The name includes:
     * <ul>
     *   <li>First prefix (if present): e.g., "Sharp"</li>
     *   <li>Base item name: e.g., "Iron Sword"</li>
     *   <li>First suffix (if present): e.g., "of the Bear"</li>
     *   <li>Rarity coloring</li>
     *   <li>Bold styling for Epic+ items</li>
     * </ul>
     *
     * <p>Example: "Sharp Iron Sword of the Bear" (gold, bold)
     *
     * @param baseItemName The base item name (e.g., "Iron Sword")
     * @param gearData The gear data containing modifiers and rarity
     * @return Styled item name as a Message
     */
    @Nonnull
    Message buildItemName(@Nonnull String baseItemName, @Nonnull GearData gearData);
}
