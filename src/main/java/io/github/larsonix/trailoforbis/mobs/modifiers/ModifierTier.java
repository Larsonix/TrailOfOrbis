package io.github.larsonix.trailoforbis.mobs.modifiers;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Complexity tiers for mob modifiers, gated by mob level.
 *
 * <p>Higher tiers contain more complex modifiers that require
 * tactical adaptation. Players encounter simple stat mods first,
 * then gradually face behavioral and death-trigger mods.
 */
public enum ModifierTier {
    TIER_1(1),
    TIER_2(10),
    TIER_3(25),
    TIER_4(40);

    private final int levelGate;

    ModifierTier(int levelGate) {
        this.levelGate = levelGate;
    }

    public int getLevelGate() {
        return levelGate;
    }

    /**
     * Returns the highest tier available at the given mob level.
     */
    @Nonnull
    public static ModifierTier highestForLevel(int level) {
        ModifierTier highest = TIER_1;
        for (ModifierTier tier : values()) {
            if (level >= tier.levelGate) {
                highest = tier;
            }
        }
        return highest;
    }

    @Nullable
    public static ModifierTier fromName(@Nullable String name) {
        if (name == null || name.isEmpty()) return null;
        for (ModifierTier tier : values()) {
            if (tier.name().equalsIgnoreCase(name)) return tier;
        }
        return null;
    }
}
