package io.github.larsonix.trailoforbis.skilltree.util;

import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.skilltree.model.StatType;

import javax.annotation.Nonnull;

/**
 * Resolves the computed value for a given {@link StatType} from a {@link ComputedStats} instance.
 *
 * <p>This is the read-side mirror of {@code StatsCombiner.setStatInBuilder()} — where
 * StatsCombiner writes values into a builder by StatType, this class reads them back.
 *
 * <p>Used by the node allocation feedback system to show before/after stat diffs.
 */
public final class StatValueResolver {

    private StatValueResolver() {
        // Utility class
    }

    /**
     * Resolves the current value of a stat from computed stats.
     *
     * @param stats The computed stats to read from
     * @param type The stat type to resolve
     * @return The stat value, or 0f for unmapped types
     */
    public static float resolve(@Nonnull ComputedStats stats, @Nonnull StatType type) {
        return type.resolveFrom(stats);
    }
}
