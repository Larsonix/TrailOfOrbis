package io.github.larsonix.trailoforbis.gear.model;

import io.github.larsonix.trailoforbis.stones.ItemModifier;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Represents a single modifier (prefix or suffix) on a piece of gear.
 *
 * <p>Modifiers grant stat bonuses to the player when the item is equipped.
 * Each modifier has:
 * <ul>
 *   <li>An ID matching the config definition (e.g., "sharp", "of_the_whale")</li>
 *   <li>A type (PREFIX or SUFFIX) determining display position</li>
 *   <li>A stat ID indicating which stat it affects (e.g., "physical_damage")</li>
 *   <li>A stat type indicating how the value is applied ("flat" or "percent")</li>
 *   <li>A rolled value (the actual bonus amount)</li>
 *   <li>A locked state (protected from rerolling by stones)</li>
 * </ul>
 *
 * <p>Implements {@link ItemModifier} to enable shared stone functionality
 * with realm map modifiers (rerolling, locking, etc.).
 *
 * <p>This is an immutable record. All validation happens at construction time.
 *
 * @param id          Unique identifier matching gear-modifiers.yml key (e.g., "sharp")
 * @param displayName Human-readable name for tooltips (e.g., "Sharp")
 * @param type        Whether this is a PREFIX or SUFFIX
 * @param statId      The stat this modifier affects (e.g., "physical_damage")
 * @param statType    How the value is applied: "flat" (additive) or "percent" (multiplicative)
 * @param value       The rolled value (already calculated, includes item level scaling)
 * @param locked      Whether this modifier is protected from rerolling
 */
public record GearModifier(
    @Nonnull String id,
    @Nonnull String displayName,
    @Nonnull ModifierType type,
    @Nonnull String statId,
    @Nonnull String statType,
    double value,
    boolean locked
) implements ItemModifier {

    /** Stat type for flat/additive bonuses (e.g., +10 Physical Damage) */
    public static final String STAT_TYPE_FLAT = "flat";

    /** Stat type for percent/multiplicative bonuses (e.g., +10% Physical Damage) */
    public static final String STAT_TYPE_PERCENT = "percent";

    /**
     * Compact constructor with validation.
     */
    @SuppressWarnings("NullAway") // Record components validated in compact constructor
    public GearModifier {
        // Validate id
        Objects.requireNonNull(id, "Modifier id cannot be null");
        if (id.isBlank()) {
            throw new IllegalArgumentException("Modifier id cannot be blank");
        }

        // Validate displayName
        Objects.requireNonNull(displayName, "Modifier displayName cannot be null");
        if (displayName.isBlank()) {
            throw new IllegalArgumentException("Modifier displayName cannot be blank");
        }

        // Validate type
        Objects.requireNonNull(type, "Modifier type cannot be null");

        // Validate statId
        Objects.requireNonNull(statId, "Modifier statId cannot be null");
        if (statId.isBlank()) {
            throw new IllegalArgumentException("Modifier statId cannot be blank");
        }

        // Validate statType
        Objects.requireNonNull(statType, "Modifier statType cannot be null");
        if (!STAT_TYPE_FLAT.equals(statType) && !STAT_TYPE_PERCENT.equals(statType)) {
            throw new IllegalArgumentException(
                "Modifier statType must be '" + STAT_TYPE_FLAT + "' or '" + STAT_TYPE_PERCENT +
                "', got: " + statType
            );
        }

        // Validate value - can be negative for penalties, but warn if zero
        // Zero values are technically valid but likely indicate a bug
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException("Modifier value must be finite, got: " + value);
        }
        // locked is a primitive boolean, no validation needed
    }

    // ═══════════════════════════════════════════════════════════════════
    // FACTORY METHODS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Creates an unlocked modifier with the given parameters.
     *
     * <p>This factory method provides backwards compatibility for code
     * that was written before the locked field was added.
     *
     * @param id          Unique identifier
     * @param displayName Human-readable name
     * @param type        PREFIX or SUFFIX
     * @param statId      The stat this modifier affects
     * @param statType    "flat" or "percent"
     * @param value       The rolled value
     * @return A new unlocked GearModifier
     */
    @Nonnull
    public static GearModifier of(
            @Nonnull String id,
            @Nonnull String displayName,
            @Nonnull ModifierType type,
            @Nonnull String statId,
            @Nonnull String statType,
            double value) {
        return new GearModifier(id, displayName, type, statId, statType, value, false);
    }

    // ═══════════════════════════════════════════════════════════════════
    // ItemModifier INTERFACE IMPLEMENTATION
    // ═══════════════════════════════════════════════════════════════════

    @Override
    @Nonnull
    public String id() {
        return id;
    }

    @Override
    @Nonnull
    public String displayName() {
        return displayName;
    }

    @Override
    public double getValue() {
        return value;
    }

    @Override
    public boolean isLocked() {
        return locked;
    }

    @Override
    @Nonnull
    public ItemModifier withLocked(boolean newLocked) {
        return new GearModifier(id, displayName, type, statId, statType, value, newLocked);
    }

    @Override
    @Nonnull
    public ItemModifier withValue(double newValue) {
        return new GearModifier(id, displayName, type, statId, statType, newValue, locked);
    }

    @Override
    @Nonnull
    public String typeLabel() {
        return type.name();
    }

    @Override
    @Nonnull
    public String formatForTooltip() {
        String statDisplayName = formatStatIdAsDisplayName(statId);
        String base = formatForTooltip(statDisplayName);
        if (locked) {
            return "🔒 " + base;
        }
        return base;
    }

    // ═══════════════════════════════════════════════════════════════════
    // GEAR-SPECIFIC METHODS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Check if this modifier applies a flat (additive) bonus.
     *
     * <p>Flat bonuses are added directly to the stat value.
     * Example: +10 Physical Damage adds 10 to base damage.
     *
     * @return true if this is a flat bonus
     */
    public boolean isFlat() {
        return STAT_TYPE_FLAT.equals(statType);
    }

    /**
     * Check if this modifier applies a percent (multiplicative) bonus.
     *
     * <p>Percent bonuses multiply the stat value.
     * Example: +10% Physical Damage with 100 base = 110 total.
     *
     * @return true if this is a percent bonus
     */
    @Override
    public boolean isPercent() {
        return STAT_TYPE_PERCENT.equals(statType);
    }

    /**
     * Check if this is a prefix modifier.
     *
     * @return true if type is PREFIX
     */
    public boolean isPrefix() {
        return type == ModifierType.PREFIX;
    }

    /**
     * Check if this is a suffix modifier.
     *
     * @return true if type is SUFFIX
     */
    public boolean isSuffix() {
        return type == ModifierType.SUFFIX;
    }

    /**
     * Format the modifier value for tooltip display.
     *
     * <p>Examples:
     * <ul>
     *   <li>Flat positive: "+10 Physical Damage"</li>
     *   <li>Flat negative: "-5 Physical Damage"</li>
     *   <li>Percent positive: "+10% Physical Damage"</li>
     *   <li>Percent negative: "-5% Physical Damage"</li>
     * </ul>
     *
     * @param statDisplayName The human-readable stat name
     * @return Formatted string for tooltip
     */
    public String formatForTooltip(String statDisplayName) {
        String sign = value >= 0 ? "+" : "";

        // Format value: show decimal only if needed
        String valueStr;
        if (isPercent()) {
            // Percent values: omit decimals for >= 100%
            if (Math.abs(value) >= 100) {
                valueStr = String.format("%s%d%%", sign, Math.round(value));
            } else if (value == Math.floor(value)) {
                valueStr = String.format("%s%.0f%%", sign, value);
            } else {
                valueStr = String.format("%s%.1f%%", sign, value);
            }
        } else {
            // Flat values
            if (value == Math.floor(value)) {
                valueStr = String.format("%s%.0f", sign, value);
            } else {
                valueStr = String.format("%s%.1f", sign, value);
            }
        }

        return valueStr + " " + statDisplayName;
    }

    /**
     * Create a copy with a different value (type-safe version).
     *
     * <p>Used when rerolling modifier values (Divine Stones).
     * Returns a GearModifier instead of ItemModifier for type safety.
     *
     * @param newValue The new value
     * @return New GearModifier with updated value
     */
    @Nonnull
    public GearModifier withNewValue(double newValue) {
        return new GearModifier(id, displayName, type, statId, statType, newValue, locked);
    }

    /**
     * Create a copy with a different locked state (type-safe version).
     *
     * <p>Returns a GearModifier instead of ItemModifier for type safety.
     *
     * @param newLocked The new locked state
     * @return New GearModifier with updated locked state
     */
    @Nonnull
    public GearModifier withLockedState(boolean newLocked) {
        return new GearModifier(id, displayName, type, statId, statType, value, newLocked);
    }

    // ═══════════════════════════════════════════════════════════════════
    // UTILITY METHODS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Formats a stat ID as a human-readable display name.
     *
     * <p>Converts snake_case to Title Case:
     * "physical_damage" → "Physical Damage"
     *
     * @param statId The stat ID to format
     * @return Human-readable display name
     */
    @Nonnull
    private static String formatStatIdAsDisplayName(@Nonnull String statId) {
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

    @Override
    public String toString() {
        return String.format("GearModifier[%s '%s': %s%s %s%s]",
            type, displayName,
            value >= 0 ? "+" : "",
            isPercent() ? value + "%" : value,
            statId,
            locked ? " (LOCKED)" : ""
        );
    }
}
