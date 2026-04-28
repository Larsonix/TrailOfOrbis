package io.github.larsonix.trailoforbis.gems.model;

import java.util.Objects;
import javax.annotation.Nonnull;

public record GemData(@Nonnull String gemId, int level, int quality, long xp, @Nonnull GemType gemType) {
    public static final int MIN_LEVEL = 1;
    public static final int MAX_LEVEL = 1000;
    public static final int MIN_QUALITY = 1;
    public static final int MAX_QUALITY = 100;

    public GemData {
        Objects.requireNonNull(gemId, "gemId cannot be null");
        Objects.requireNonNull(gemType, "gemType cannot be null");
        if (gemId.isBlank()) {
            throw new IllegalArgumentException("gemId cannot be blank");
        }
        if (level < 1 || level > 1000) {
            throw new IllegalArgumentException("level must be between 1 and 1000, got: " + level);
        }
        if (quality < 1 || quality > 100) {
            throw new IllegalArgumentException("quality must be between 1 and 100, got: " + quality);
        }
        if (xp < 0L) {
            throw new IllegalArgumentException("xp cannot be negative: " + xp);
        }
    }

    @Nonnull
    public static GemData active(@Nonnull String gemId, int level, int quality) {
        return new GemData(gemId, level, quality, 0L, GemType.ACTIVE);
    }

    @Nonnull
    public static GemData support(@Nonnull String gemId, int level, int quality) {
        return new GemData(gemId, level, quality, 0L, GemType.SUPPORT);
    }
}
