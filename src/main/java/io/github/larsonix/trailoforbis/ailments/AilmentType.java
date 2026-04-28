package io.github.larsonix.trailoforbis.ailments;

import io.github.larsonix.trailoforbis.elemental.ElementType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Elemental ailments that can be inflicted on entities.
 *
 * <p>Each ailment is tied to an element and has unique mechanics:
 * <ul>
 *   <li><b>BURN</b> (Fire): Damage over time, refreshes on reapplication</li>
 *   <li><b>FREEZE</b> (Cold): Movement/action speed reduction, max 30% slow</li>
 *   <li><b>SHOCK</b> (Lightning): Increased damage taken, max 50% increased</li>
 *   <li><b>POISON</b> (Chaos): Stacking DoT, up to 10 stacks</li>
 * </ul>
 *
 * <p><b>PoE2-Style Application:</b> Elemental damage has a % chance to apply
 * the corresponding ailment based on {@code baseChance + statusEffectChance}.
 */
public enum AilmentType {

    /**
     * Fire ailment - deals % of hit damage as fire DoT over duration.
     * Reapplication refreshes duration and takes the stronger magnitude.
     */
    BURN(
        ElementType.FIRE,
        "Burning",
        "§c",
        4.0f,           // Default duration (seconds)
        1,              // Max stacks (refreshes instead)
        0.5f,           // Base damage ratio (50% of hit damage over duration)
        10.0f,          // Base application chance %
        true,           // Deals damage
        false           // Is not a debuff (slow/amp)
    ),

    /**
     * Water ailment - slows movement and action speed.
     * Effect magnitude scales with hit damage relative to target's max life.
     * Capped at 30% slow. Refreshes on reapplication with stronger effect.
     */
    FREEZE(
        ElementType.WATER,
        "Chilled",
        "§b",
        3.0f,           // Default duration
        1,              // Max stacks (magnitude overwrites if stronger)
        0.0f,           // No damage
        10.0f,          // Base application chance %
        false,          // No DoT
        true            // Is a debuff
    ),

    /**
     * Lightning ailment - increases damage taken by target.
     * Effect magnitude scales with hit damage relative to target's max life.
     * Capped at 50% increased damage taken. Refreshes on reapplication.
     */
    SHOCK(
        ElementType.LIGHTNING,
        "Shocked",
        "§e",
        2.0f,           // Default duration (shorter because powerful)
        1,              // Max stacks (magnitude overwrites if stronger)
        0.0f,           // No direct damage
        10.0f,          // Base application chance %
        false,          // No DoT
        true            // Is a debuff
    ),

    /**
     * Void ailment - stacking damage over time.
     * Each application adds a new stack (up to max), each dealing independent DoT.
     * Stacks have independent durations.
     */
    POISON(
        ElementType.VOID,
        "Poisoned",
        "§5",
        5.0f,           // Default duration per stack
        10,             // Max stacks
        0.3f,           // Base damage ratio per stack (30% of hit damage)
        10.0f,          // Base application chance %
        true,           // Deals damage
        false           // Is not a debuff
    );

    private final ElementType element;
    private final String displayName;
    private final String colorCode;
    private final float defaultDuration;
    private final int maxStacks;
    private final float baseDamageRatio;
    private final float baseChance;
    private final boolean dealsDamage;
    private final boolean isDebuff;

    AilmentType(
        @Nonnull ElementType element,
        @Nonnull String displayName,
        @Nonnull String colorCode,
        float defaultDuration,
        int maxStacks,
        float baseDamageRatio,
        float baseChance,
        boolean dealsDamage,
        boolean isDebuff
    ) {
        this.element = element;
        this.displayName = displayName;
        this.colorCode = colorCode;
        this.defaultDuration = defaultDuration;
        this.maxStacks = maxStacks;
        this.baseDamageRatio = baseDamageRatio;
        this.baseChance = baseChance;
        this.dealsDamage = dealsDamage;
        this.isDebuff = isDebuff;
    }

    /** Gets the element that causes this ailment. */
    @Nonnull
    public ElementType getElement() {
        return element;
    }

    /**
     * @return Human-readable name (e.g., "Burning", "Chilled")
     */
    @Nonnull
    public String getDisplayName() {
        return displayName;
    }

    /**
     * @return Color code (e.g., "§c" for red/fire)
     */
    @Nonnull
    public String getColorCode() {
        return colorCode;
    }

    /**
     * @return Colored name like "§cBurning"
     */
    @Nonnull
    public String getColoredName() {
        return colorCode + displayName;
    }

    /**
     * @return Base duration in seconds before modifiers
     */
    public float getDefaultDuration() {
        return defaultDuration;
    }

    /**
     * @return Max stacks (1 for non-stacking ailments like Burn/Freeze/Shock)
     */
    public int getMaxStacks() {
        return maxStacks;
    }

    /**
     * @return Ratio of hit damage dealt as DoT (e.g., 0.5 = 50%)
     */
    public float getBaseDamageRatio() {
        return baseDamageRatio;
    }

    /**
     * @return Base % chance before statusEffectChance modifier
     */
    public float getBaseChance() {
        return baseChance;
    }

    /** @return true for Burn and Poison */
    public boolean dealsDamage() {
        return dealsDamage;
    }

    /** @return true for Freeze (slow) and Shock (damage amp) */
    public boolean isDebuff() {
        return isDebuff;
    }

    /** @return true only for Poison */
    public boolean stacks() {
        return maxStacks > 1;
    }

    /**
     * @return The corresponding ailment, or null if element has no ailment
     */
    @Nullable
    public static AilmentType forElement(@Nullable ElementType element) {
        if (element == null) {
            return null;
        }
        return switch (element) {
            case FIRE -> BURN;
            case WATER -> FREEZE;
            case LIGHTNING -> SHOCK;
            case VOID -> POISON;
            case EARTH, WIND -> null;  // No ailments yet for these elements
        };
    }

    /**
     * @return The ailment type, or null if not found
     */
    @Nullable
    public static AilmentType fromName(@Nullable String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        for (AilmentType type : values()) {
            if (type.name().equalsIgnoreCase(name) ||
                type.displayName.equalsIgnoreCase(name)) {
                return type;
            }
        }
        return null;
    }
}
