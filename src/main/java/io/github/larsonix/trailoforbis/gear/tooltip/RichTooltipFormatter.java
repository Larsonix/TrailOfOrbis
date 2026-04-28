package io.github.larsonix.trailoforbis.gear.tooltip;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import io.github.larsonix.trailoforbis.api.ServiceRegistry;
import io.github.larsonix.trailoforbis.attributes.AttributeManager;
import io.github.larsonix.trailoforbis.attributes.AttributeType;
import io.github.larsonix.trailoforbis.compat.HexcodeCompat;
import io.github.larsonix.trailoforbis.compat.HexcodeMetadataInjector;
import io.github.larsonix.trailoforbis.compat.HexcodeMetadataInjector.HexBookData;
import io.github.larsonix.trailoforbis.compat.HexcodeMetadataInjector.HexStaffData;
import io.github.larsonix.trailoforbis.gear.config.ModifierConfig;
import io.github.larsonix.trailoforbis.gear.model.WeaponType;
import io.github.larsonix.trailoforbis.gear.equipment.RequirementCalculator;
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.model.GearModifier;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.gear.model.ArmorImplicit;
import io.github.larsonix.trailoforbis.gear.model.WeaponImplicit;
import io.github.larsonix.trailoforbis.gems.model.GemData;
import io.github.larsonix.trailoforbis.leveling.api.LevelingService;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

/**
 * Builds rich text tooltips for gear using Hytale's Message API.
 *
 * <p>Creates multi-section tooltips with:
 * <ul>
 *   <li>Rarity badge (colored and bold)</li>
 *   <li>Item level display</li>
 *   <li>Quality rating with tier colors</li>
 *   <li>Stat modifiers (green positive, red negative)</li>
 *   <li>Attribute requirements (color-coded by met/unmet)</li>
 * </ul>
 *
 * <p>All text is styled using hex colors for native Hytale rendering.
 */
public final class RichTooltipFormatter {

    private final ModifierConfig modifierConfig;
    private final RequirementCalculator requirementCalculator;
    private final AttributeManager attributeManager;
    private final TooltipConfig tooltipConfig;

    /**
     * Creates a RichTooltipFormatter with default configuration.
     *
     * @param modifierConfig The modifier configuration
     * @param requirementCalculator The requirement calculator
     * @param attributeManager The attribute manager
     */
    public RichTooltipFormatter(
            @Nonnull ModifierConfig modifierConfig,
            @Nonnull RequirementCalculator requirementCalculator,
            @Nonnull AttributeManager attributeManager) {
        this(modifierConfig, requirementCalculator, attributeManager, TooltipConfig.defaults());
    }

    /**
     * Creates a RichTooltipFormatter with custom configuration.
     *
     * @param modifierConfig The modifier configuration
     * @param requirementCalculator The requirement calculator
     * @param attributeManager The attribute manager
     * @param tooltipConfig The tooltip configuration
     */
    public RichTooltipFormatter(
            @Nonnull ModifierConfig modifierConfig,
            @Nonnull RequirementCalculator requirementCalculator,
            @Nonnull AttributeManager attributeManager,
            @Nonnull TooltipConfig tooltipConfig) {
        this.modifierConfig = Objects.requireNonNull(modifierConfig, "modifierConfig cannot be null");
        this.requirementCalculator = Objects.requireNonNull(requirementCalculator, "requirementCalculator cannot be null");
        this.attributeManager = Objects.requireNonNull(attributeManager, "attributeManager cannot be null");
        this.tooltipConfig = Objects.requireNonNull(tooltipConfig, "tooltipConfig cannot be null");
    }

    // =========================================================================
    // MAIN BUILD METHODS
    // =========================================================================

    /**
     * Builds a complete tooltip for gear without player context.
     *
     * <p>Requirements will not be color-coded (shown in neutral gray).
     *
     * @param gearData The gear data to format
     * @return Complete tooltip as a Message
     */
    @Nonnull
    public Message build(@Nonnull GearData gearData) {
        return build(gearData, null);
    }

    /**
     * Builds a complete tooltip for gear with player context.
     *
     * <p>Requirements will be color-coded based on whether the player meets them.
     *
     * @param gearData The gear data to format
     * @param playerId The player viewing the tooltip (may be null)
     * @return Complete tooltip as a Message
     */
    @Nonnull
    public Message build(@Nonnull GearData gearData, @Nullable UUID playerId) {
        return build(gearData, playerId, null);
    }

    /**
     * Builds a complete tooltip for gear with player context and item reference.
     *
     * <p>When an ItemStack is provided and Hexcode is loaded, hex-specific sections
     * (casting info for staffs/wands, spellbook capacity) are included between
     * the modifiers and gem sections.
     *
     * @param gearData The gear data to format
     * @param playerId The player viewing the tooltip (may be null)
     * @param itemStack The source ItemStack for reading hex metadata (may be null)
     * @return Complete tooltip as a Message
     */
    @Nonnull
    public Message build(@Nonnull GearData gearData, @Nullable UUID playerId, @Nullable ItemStack itemStack) {
        Objects.requireNonNull(gearData, "gearData cannot be null");

        Message tooltip = Message.empty();

        // Section 1: Rarity badge
        if (tooltipConfig.showRarityBadge()) {
            tooltip = tooltip.insert(buildRarityBadge(gearData.rarity()));
        }

        // Section 2: Item level (color-coded based on player level requirement)
        if (tooltipConfig.showItemLevel()) {
            tooltip = tooltip.insert(buildItemLevel(gearData.level(), playerId));
        }

        // Section 3: Weapon implicit damage (between level and quality)
        if (gearData.hasWeaponImplicit()) {
            tooltip = tooltip.insert(buildImplicitSection(gearData.implicit()));
        }

        // Section 3b: Armor implicit defense (between level and quality)
        if (gearData.hasArmorImplicit()) {
            tooltip = tooltip.insert(buildArmorImplicitSection(gearData.armorImplicit()));
        }

        // Section 4: Quality rating
        if (tooltipConfig.showQuality()) {
            tooltip = tooltip.insert(buildQualitySection(gearData.quality()));
        }

        // Section 5: Modifiers (prefixes + suffixes)
        if (tooltipConfig.showModifiers() && hasModifiers(gearData)) {
            tooltip = tooltip.insert(buildSeparator());
            tooltip = tooltip.insert(buildModifiersSection(gearData));
        }

        // Section 5b: Hex casting/spellbook info (after modifiers, before gems)
        if (tooltipConfig.showHexInfo() && HexcodeCompat.isLoaded()) {
            Message hexSection = buildHexSectionFromGearData(gearData, itemStack);
            if (hexSection != null) {
                tooltip = tooltip.insert(buildSeparator());
                tooltip = tooltip.insert(hexSection);
            }
        }

        // Section 6: Gem sockets (if any gems or slots exist)
        if (gearData.hasActiveGem() || !gearData.supportGems().isEmpty()) {
            tooltip = tooltip.insert(buildSeparator());
            tooltip = tooltip.insert(buildGemSection(gearData));
        }

        // Section 7: Requirements
        if (tooltipConfig.showRequirements()) {
            Map<AttributeType, Integer> requirements = requirementCalculator.calculateRequirements(gearData);
            if (!requirements.isEmpty()) {
                tooltip = tooltip.insert(buildRequirementsSection(requirements, playerId));
            }
        }

        return tooltip;
    }

    // =========================================================================
    // SECTION BUILDERS
    // =========================================================================

    /**
     * Builds the rarity badge section.
     *
     * <p>Format: "[LEGENDARY]" in rarity color, bold
     */
    @Nonnull
    public Message buildRarityBadge(@Nonnull GearRarity rarity) {
        String badgeText = "[" + rarity.getHytaleQualityId().toUpperCase() + "]";
        return Message.raw(badgeText)
                .color(TooltipStyles.getRarityColor(rarity))
                .bold(true);
    }

    /**
     * Builds the item level section without player context.
     *
     * <p>Format: "Item Level: 45" (gray label, white value)
     *
     * @param level The item level
     * @return The formatted message
     */
    @Nonnull
    public Message buildItemLevel(int level) {
        return buildItemLevel(level, null);
    }

    /**
     * Builds the item level section with player context for requirement coloring.
     *
     * <p>Format: "Item Level: 45" where the value is:
     * <ul>
     *   <li>White if player meets the level requirement (or no player context)</li>
     *   <li>Red if player is below the required level</li>
     * </ul>
     *
     * @param itemLevel The item's level requirement
     * @param playerId The player viewing the tooltip (may be null)
     * @return The formatted message
     */
    @Nonnull
    public Message buildItemLevel(int itemLevel, @Nullable UUID playerId) {
        boolean levelMet = isLevelRequirementMet(itemLevel, playerId);
        String valueColor = levelMet ? TooltipStyles.VALUE_WHITE : TooltipStyles.REQUIREMENT_UNMET;

        return Message.raw("\nItem Level : ")
                .color(TooltipStyles.LABEL_GRAY)
                .insert(Message.raw(String.valueOf(itemLevel))
                        .color(valueColor));
    }

    /**
     * Checks if a player meets the level requirement for an item.
     *
     * @param itemLevel The item's level requirement
     * @param playerId The player's UUID (may be null)
     * @return true if player meets the requirement (or if we can't check)
     */
    private boolean isLevelRequirementMet(int itemLevel, @Nullable UUID playerId) {
        if (playerId == null) {
            return true; // No player context, assume met
        }

        // Use ServiceRegistry for deferred lookup (LevelingService may init after GearManager)
        return ServiceRegistry.get(LevelingService.class)
                .map(levelingService -> levelingService.getLevel(playerId) >= itemLevel)
                .orElse(true); // Service unavailable, assume met
    }

    /**
     * Builds the weapon implicit damage section.
     *
     * <p>Format: "175 Physical Damage [153-198]"
     * - Rolled value in green (positive stat color)
     * - Stat name in white
     * - Range in gray brackets
     *
     * @param implicit The weapon implicit damage
     */
    @Nonnull
    public Message buildImplicitSection(@Nonnull WeaponImplicit implicit) {
        // Format: "175 Physical Damage [153-198]"
        int rolledValue = implicit.rolledValueAsInt();
        int minValue = implicit.minValueAsInt();
        int maxValue = implicit.maxValueAsInt();
        String damageTypeName = implicit.damageTypeDisplayName();

        // Build: value in green, stat name in white, range in gray
        return Message.raw("\n" + rolledValue + " ")
                .color(TooltipStyles.POSITIVE_MODIFIER)
                .insert(Message.raw(damageTypeName + " ")
                        .color(TooltipStyles.VALUE_WHITE))
                .insert(Message.raw("[" + minValue + "-" + maxValue + "]")
                        .color(TooltipStyles.LABEL_GRAY));
    }

    /**
     * Builds the armor implicit defense section.
     *
     * <p>Format: "85 Armor [72-98]"
     * - Rolled value in green (positive stat color)
     * - Stat name in white
     * - Range in gray brackets
     *
     * @param implicit The armor implicit defense
     */
    @Nonnull
    public Message buildArmorImplicitSection(@Nonnull ArmorImplicit implicit) {
        int rolledValue = implicit.rolledValueAsInt();
        int minValue = implicit.minValueAsInt();
        int maxValue = implicit.maxValueAsInt();
        String defenseTypeName = implicit.defenseTypeDisplayName();

        return Message.raw("\n" + rolledValue + " ")
                .color(TooltipStyles.POSITIVE_MODIFIER)
                .insert(Message.raw(defenseTypeName + " ")
                        .color(TooltipStyles.VALUE_WHITE))
                .insert(Message.raw("[" + minValue + "-" + maxValue + "]")
                        .color(TooltipStyles.LABEL_GRAY));
    }

    /**
     * Builds the quality section.
     *
     * <p>Format: "Quality: Excellent (92%)" (gray label, tier-colored value)
     */
    @Nonnull
    public Message buildQualitySection(int quality) {
        String qualityName = TooltipStyles.getQualityName(quality);
        String qualityColor = TooltipStyles.getQualityColor(quality);

        return Message.raw("\nQuality : ")
                .color(TooltipStyles.LABEL_GRAY)
                .insert(Message.raw(qualityName + " (" + quality + "%)")
                        .color(qualityColor));
    }

    /**
     * Builds the modifiers section (prefixes, separator, suffixes).
     *
     * <p>A dark gray dashed separator always divides the prefix zone (above)
     * from the suffix zone (below). This acts as a fixed landmark so players
     * can instantly distinguish modifier types even when only one group is present.
     *
     * <p>Modifier values are quality-adjusted to show actual effective values.
     */
    @Nonnull
    public Message buildModifiersSection(@Nonnull GearData gearData) {
        Message section = Message.empty();
        double qualityMult = gearData.qualityMultiplier();

        // Prefix zone
        for (GearModifier mod : gearData.prefixes()) {
            section = section.insert(buildModifierLine(mod, qualityMult));
        }

        // Separator between prefix and suffix zones
        section = section.insert(buildModifierGroupSeparator());

        // Suffix zone
        for (GearModifier mod : gearData.suffixes()) {
            section = section.insert(buildModifierLine(mod, qualityMult));
        }

        return section;
    }

    /**
     * Builds a dark gray dashed separator between prefix and suffix zones.
     */
    @Nonnull
    private Message buildModifierGroupSeparator() {
        return Message.raw("\n--------")
                .color(TooltipStyles.SEPARATOR);
    }

    /**
     * Builds a single modifier line with quality adjustment and split coloring.
     *
     * <p>Format: "+25.5 Physical Damage" where the value part (+25.5) is colored
     * green/red/teal and the stat name (Physical Damage) is white.
     *
     * <p>Locked modifiers use teal for the value and append " [Locked]" in gray italic.
     *
     * @param modifier The modifier to format
     * @param qualityMultiplier The quality multiplier (quality / 50.0)
     */
    @Nonnull
    public Message buildModifierLine(@Nonnull GearModifier modifier, double qualityMultiplier) {
        double adjustedValue = modifier.value() * qualityMultiplier;
        String sign = adjustedValue >= 0 ? "+" : "";
        String valueStr = formatModifierValue(modifier, qualityMultiplier);
        String displayName = getModifierDisplayName(modifier);
        String valueColor = TooltipStyles.getModifierValueColor(adjustedValue, modifier.locked());

        // Value part in green/red/teal, stat name in white
        Message line = Message.raw("\n" + sign + valueStr + " ")
                .color(valueColor)
                .insert(Message.raw(displayName)
                        .color(TooltipStyles.VALUE_WHITE));

        // Append locked indicator
        if (modifier.locked()) {
            line = line.insert(Message.raw(" [Locked]")
                    .color(TooltipStyles.LABEL_GRAY)
                    .italic(true));
        }

        return line;
    }

    /**
     * Builds the requirements section.
     *
     * <p>Format:
     * <pre>
     * Requirements:
     * ✓ Strength: 30  (green)
     * ✗ Dexterity: 25 (red)
     * </pre>
     */
    @Nonnull
    public Message buildRequirementsSection(
            @Nonnull Map<AttributeType, Integer> requirements,
            @Nullable UUID playerId) {

        Message section = Message.raw("\n\nRequirements :")
                .color(TooltipStyles.LABEL_GRAY);

        // Get player attributes if available
        Map<AttributeType, Integer> playerAttrs = getPlayerAttributes(playerId);

        for (Map.Entry<AttributeType, Integer> req : requirements.entrySet()) {
            section = section.insert(buildRequirementLine(
                    req.getKey(), req.getValue(), playerAttrs));
        }

        return section;
    }

    /**
     * Builds a single requirement line.
     *
     * <p>Format: "Strength: 30" colored green if met, red if not.
     * Color alone indicates requirement status (Hytale doesn't support ✓/✗ characters).
     */
    @Nonnull
    private Message buildRequirementLine(
            @Nonnull AttributeType attribute,
            int required,
            @Nonnull Map<AttributeType, Integer> playerAttrs) {

        int playerValue = playerAttrs.getOrDefault(attribute, 0);
        boolean met = playerValue >= required;

        // Color alone indicates met/unmet status (green=met, red=unmet)
        // Hytale's font doesn't support Unicode checkmarks (✓/✗ render as "?")
        String color = TooltipStyles.getRequirementColor(met);
        String attrName = attribute.getDisplayName();

        return Message.raw("\n" + attrName + " : " + required)
                .color(color);
    }

    /**
     * Builds a separator line.
     */
    @Nonnull
    private Message buildSeparator() {
        return Message.raw("\n");
    }

    /**
     * Builds the gem socket section showing active and support gem slots.
     */
    @Nonnull
    private Message buildGemSection(@Nonnull GearData gearData) {
        Message section = Message.raw("\nGems:").color("#888888");

        if (gearData.hasActiveGem()) {
            GemData active = gearData.activeGem();
            section = section.insert(Message.raw("\n  Active: ").color("#888888")
                .insert(Message.raw(active.gemId() + " Lv" + active.level()).color("#FFFFFF")));
        } else {
            section = section.insert(Message.raw("\n  Active: [Empty]").color("#888888"));
        }

        for (int i = 0; i < gearData.supportSlotCount(); i++) {
            if (i < gearData.supportGems().size()) {
                GemData support = gearData.supportGems().get(i);
                section = section.insert(Message.raw("\n  Support " + (i + 1) + ": ").color("#888888")
                    .insert(Message.raw(support.gemId() + " Lv" + support.level()).color("#3498DB")));
            } else {
                section = section.insert(Message.raw("\n  Support " + (i + 1) + ": [Empty]").color("#888888"));
            }
        }

        return section;
    }

    // =========================================================================
    // HEX CASTING SECTIONS
    // =========================================================================

    /**
     * Builds the appropriate hex section based on item metadata.
     *
     * <p>Returns a casting section for staffs/wands (HexStaff metadata),
     * a spellbook section for books (HexBook metadata), or null if neither.
     */
    /**
     * Builds hex section from GearData (weapon type detection) with optional ItemStack
     * metadata fallback.
     *
     * <p>Determines whether the item is a staff/wand or spellbook from the base item ID,
     * then builds the appropriate hex section. If an ItemStack is available, reads actual
     * metadata values; otherwise uses defaults.
     */
    @Nullable
    private Message buildHexSectionFromGearData(@Nonnull GearData gearData, @Nullable ItemStack itemStack) {
        String baseItemId = gearData.baseItemId();
        if (baseItemId == null) {
            return null;
        }

        WeaponType weaponType = WeaponType.fromItemIdOrUnknown(baseItemId);
        if (!weaponType.isMagic()) {
            return null; // Only magic weapons get hex tooltip sections
        }

        if (weaponType == WeaponType.STAFF || weaponType == WeaponType.WAND) {
            // Try reading from ItemStack metadata first, fall back to defaults
            HexStaffData staffData = (itemStack != null)
                ? HexcodeMetadataInjector.readHexStaffData(itemStack)
                : null;
            return buildHexCastingSection(staffData != null ? staffData : HexStaffData.DEFAULT);
        }

        if (weaponType == WeaponType.SPELLBOOK) {
            HexBookData bookData = (itemStack != null)
                ? HexcodeMetadataInjector.readHexBookData(itemStack)
                : null;
            int capacity = (bookData != null) ? bookData.maxCapacity()
                : HexcodeMetadataInjector.getBookCapacityForRarity(gearData.rarity());
            return buildHexSpellbookSection(new HexBookData(capacity, ""));
        }

        return null;
    }

    @Nullable
    private Message buildHexSection(@Nonnull ItemStack itemStack) {
        HexStaffData staffData = HexcodeMetadataInjector.readHexStaffData(itemStack);
        if (staffData != null) {
            return buildHexCastingSection(staffData);
        }

        HexBookData bookData = HexcodeMetadataInjector.readHexBookData(itemStack);
        if (bookData != null) {
            return buildHexSpellbookSection(bookData);
        }

        return null;
    }

    /**
     * Builds the hex casting section for staffs and wands.
     *
     * <p>Format:
     * <pre>
     * Hex Casting
     *   Cast Style: Ring
     *   Pair with Spellbook for stored hex access
     * </pre>
     */
    @Nonnull
    public Message buildHexCastingSection(@Nonnull HexStaffData staffData) {
        String styleName = Character.toUpperCase(staffData.styleId().charAt(0))
                + staffData.styleId().substring(1);

        return Message.raw("\nHex Casting")
                .color(TooltipStyles.HEX_SECTION)
                .insert(Message.raw("\n  Cast Style : ")
                        .color(TooltipStyles.LABEL_GRAY)
                        .insert(Message.raw(styleName)
                                .color(TooltipStyles.VALUE_WHITE)))
                .insert(Message.raw("\n  Pair with Spellbook for stored hex access")
                        .color(TooltipStyles.LABEL_GRAY));
    }

    /**
     * Builds the hex spellbook section.
     *
     * <p>Format:
     * <pre>
     * Hex Spellbook
     *   Spell Capacity: 8
     *   Hold in off-hand with a staff to cast
     * </pre>
     */
    @Nonnull
    public Message buildHexSpellbookSection(@Nonnull HexBookData bookData) {
        return Message.raw("\nHex Spellbook")
                .color(TooltipStyles.HEX_SECTION)
                .insert(Message.raw("\n  Spell Capacity : ")
                        .color(TooltipStyles.LABEL_GRAY)
                        .insert(Message.raw(String.valueOf(bookData.maxCapacity()))
                                .color(TooltipStyles.VALUE_WHITE)))
                .insert(Message.raw("\n  Hold in off-hand with a staff to cast")
                        .color(TooltipStyles.LABEL_GRAY));
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    /**
     * Checks if gear has any modifiers.
     */
    private boolean hasModifiers(@Nonnull GearData gearData) {
        return !gearData.prefixes().isEmpty() || !gearData.suffixes().isEmpty();
    }

    /**
     * Gets the display name for a modifier.
     *
     * <p>Shows the actual stat name (e.g., "Physical Damage") rather than
     * the modifier's thematic name (e.g., "Heavy") for clarity.
     */
    @Nonnull
    private String getModifierDisplayName(@Nonnull GearModifier modifier) {
        String statId = modifier.statId();

        // For percent modifiers, strip the "_percent" suffix if present
        // to avoid redundancy like "+2.4% Physical Damage Percent"
        if (!modifier.isFlat() && statId.endsWith("_percent")) {
            statId = statId.substring(0, statId.length() - "_percent".length());
        }

        return formatStatId(statId);
    }

    /**
     * Formats a modifier value (handles flat vs percent) with quality adjustment.
     *
     * <p>The displayed value is quality-adjusted to match the actual stat bonus
     * the player receives. This ensures "What You See Is What You Get" (WYSIWYG).
     *
     * @param qualityMultiplier quality / 50.0
     */
    @Nonnull
    private String formatModifierValue(@Nonnull GearModifier modifier, double qualityMultiplier) {
        // Apply quality multiplier to show actual effective value
        double value = modifier.value() * qualityMultiplier;

        if (modifier.isFlat()) {
            // Flat values: integer or one decimal
            if (Math.abs(value - Math.round(value)) < 0.05) {
                return String.valueOf((int) Math.round(value));
            }
            return String.format("%.1f", value);
        } else {
            // Percent values: show with %
            // For values >= 100%, show as integer (cleaner display)
            if (Math.abs(value) >= 100) {
                return String.format("%d%%", Math.round(value));
            }
            return String.format("%.1f%%", value);
        }
    }

    /**
     * Formats a stat ID as a display name.
     * "physical_damage" → "Physical Damage"
     */
    @Nonnull
    private String formatStatId(@Nonnull String statId) {
        String[] parts = statId.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!sb.isEmpty()) sb.append(" ");
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) {
                    sb.append(part.substring(1).toLowerCase());
                }
            }
        }
        return sb.toString();
    }

    /**
     * Gets player attributes, or empty map if player not found.
     */
    @Nonnull
    private Map<AttributeType, Integer> getPlayerAttributes(@Nullable UUID playerId) {
        if (playerId == null) {
            return Map.of();
        }
        return attributeManager.getPlayerAttributes(playerId);
    }

    // =========================================================================
    // COMPACT FORMATTING
    // =========================================================================

    /**
     * Builds a compact single-line summary of gear.
     *
     * <p>Format: "[EPIC] Lvl 50 (75%)" in rarity color
     *
     * @param gearData The gear data
     * @return Compact summary as a Message
     */
    @Nonnull
    public Message buildCompact(@Nonnull GearData gearData) {
        Objects.requireNonNull(gearData, "gearData cannot be null");

        String text = "[" + gearData.rarity().getHytaleQualityId() + "] " +
                "Lvl " + gearData.level() + " (" + gearData.quality() + "%)";

        return Message.raw(text)
                .color(TooltipStyles.getRarityColor(gearData.rarity()));
    }

    /**
     * Builds just the modifiers as a standalone Message.
     *
     * <p>Useful for pickup notifications or partial tooltips.
     *
     * @param gearData The gear data
     * @return Modifiers as a Message (empty if no modifiers)
     */
    @Nonnull
    public Message buildModifiersOnly(@Nonnull GearData gearData) {
        Objects.requireNonNull(gearData, "gearData cannot be null");

        if (!hasModifiers(gearData)) {
            return Message.empty();
        }

        return buildModifiersSection(gearData);
    }
}
