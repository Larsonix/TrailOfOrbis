package io.github.larsonix.trailoforbis.skilltree.model;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Value object representing a single stat modification.
 * Mutable to support SnakeYAML deserialization.
 */
public class StatModifier {
    private StatType stat;
    private float value;
    private ModifierType type;

    /**
     * Default constructor for YAML deserialization.
     */
    public StatModifier() {
    }

    /**
     * Constructor for programmatic creation.
     */
    public StatModifier(@Nonnull StatType stat, float value, @Nonnull ModifierType type) {
        this.stat = Objects.requireNonNull(stat, "stat cannot be null");
        this.value = value;
        this.type = Objects.requireNonNull(type, "type cannot be null");
    }

    // Setters for YAML deserialization
    public void setStat(StatType stat) {
        this.stat = stat;
    }

    public void setValue(float value) {
        this.value = value;
    }

    public void setType(ModifierType type) {
        this.type = type;
    }

    @Nonnull
    public StatType getStat() {
        return stat;
    }

    public float getValue() {
        return value;
    }

    @Nonnull
    public ModifierType getType() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StatModifier that)) return false;
        return Float.compare(that.value, value) == 0 &&
               stat == that.stat && type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(stat, value, type);
    }

    @Override
    public String toString() {
        if (type == null || stat == null) {
            return String.format("StatModifier{stat=%s, value=%.1f, type=%s}", stat, value, type);
        }
        String prefix;
        String suffix;
        switch (type) {
            case FLAT:
                prefix = "+";
                // Check if this stat is inherently a percentage (resistances, chances, etc.)
                suffix = stat.isInherentlyPercent() ? "%" : "";
                break;
            case PERCENT:
                prefix = "+";
                suffix = "% increased";
                break;
            case MULTIPLIER:
                prefix = "";
                suffix = "% more";
                break;
            case PENETRATION:
                prefix = "+";
                suffix = "% pen";
                break;
            case CONVERSION:
                prefix = "";
                suffix = "% conv";
                break;
            case STATUS_CHANCE:
                prefix = "+";
                suffix = "% status";
                break;
            case STATUS_DURATION:
                prefix = "+";
                suffix = "% dur";
                break;
            default:
                prefix = "";
                suffix = "";
        }
        // Only use prefix for non-negative values (negative values already have the minus sign)
        String actualPrefix = (value >= 0) ? prefix : "";
        return String.format("%s%.1f%s %s", actualPrefix, value, suffix, stat.getDisplayName());
    }

    /**
     * Returns a compact string representation for UI tooltips.
     *
     * <p>Examples:
     * <ul>
     *   <li>FLAT: "+3 Phys DMG"</li>
     *   <li>PERCENT: "+5% Crit"</li>
     *   <li>MULTIPLIER: "50% more DMG"</li>
     * </ul>
     *
     * @return Short formatted string
     */
    @Nonnull
    public String toShortString() {
        if (type == null || stat == null) {
            return "???";
        }

        // Format value (remove .0 for whole numbers)
        String valueStr = value == (int) value
            ? String.valueOf((int) value)
            : String.format("%.1f", value);

        // Build prefix and suffix based on modifier type
        String prefix;
        String suffix;
        switch (type) {
            case FLAT:
                prefix = "+";
                // Check if this stat is inherently a percentage (resistances, chances, etc.)
                suffix = stat.isInherentlyPercent() ? "%" : "";
                break;
            case PERCENT:
                prefix = "+";
                suffix = "%";
                break;
            case MULTIPLIER:
                prefix = "";
                suffix = "% more";
                break;
            case PENETRATION:
                prefix = "+";
                suffix = "%";
                break;
            case CONVERSION:
                prefix = "";
                suffix = "%";
                break;
            case STATUS_CHANCE:
                prefix = "+";
                suffix = "%";
                break;
            case STATUS_DURATION:
                prefix = "+";
                suffix = "%";
                break;
            default:
                prefix = "";
                suffix = "";
        }

        // Only use prefix for non-negative values (negative values already have the minus sign)
        String actualPrefix = (value >= 0) ? prefix : "";
        return actualPrefix + valueStr + suffix + " " + stat.getShortName();
    }
}
