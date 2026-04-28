package io.github.larsonix.trailoforbis.gems.util;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import io.github.larsonix.trailoforbis.gems.codec.GemCodecs;
import io.github.larsonix.trailoforbis.gems.model.GemData;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class GemUtils {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    public static final String KEY_PREFIX = "RPG:Gem:";
    public static final String KEY_IS_GEM = "RPG:Gem:IsGem";
    public static final String KEY_GEM_DATA = "RPG:Gem:Data";

    private GemUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static boolean isGem(@Nullable ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return false;
        }
        Boolean isGem = itemStack.getFromMetadataOrNull(KEY_IS_GEM, Codec.BOOLEAN);
        return Boolean.TRUE.equals(isGem);
    }

    @Nonnull
    public static Optional<GemData> readGemData(@Nullable ItemStack itemStack) {
        if (!GemUtils.isGem(itemStack)) {
            return Optional.empty();
        }
        try {
            GemData data = itemStack.getFromMetadataOrNull(KEY_GEM_DATA, GemCodecs.GEM_DATA_CODEC);
            return Optional.ofNullable(data);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to read gem data from item");
            return Optional.empty();
        }
    }

    @Nonnull
    public static ItemStack writeGemData(@Nonnull ItemStack itemStack, @Nonnull GemData gemData) {
        Objects.requireNonNull(itemStack, "itemStack");
        Objects.requireNonNull(gemData, "gemData");
        return itemStack
                .withMetadata(KEY_IS_GEM, Codec.BOOLEAN, true)
                .withMetadata(KEY_GEM_DATA, GemCodecs.GEM_DATA_CODEC, gemData);
    }
}
