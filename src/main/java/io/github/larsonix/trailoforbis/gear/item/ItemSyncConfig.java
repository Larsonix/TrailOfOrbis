package io.github.larsonix.trailoforbis.gear.item;

import javax.annotation.Nonnull;

/**
 * Configuration for the item sync service.
 *
 * @param enabled Whether item syncing is enabled
 * @param maxItemsPerPacket Maximum items to include in a single UpdateItems packet
 * @param batchDelayMs Delay in milliseconds between batched sends
 */
public record ItemSyncConfig(
    boolean enabled,
    int maxItemsPerPacket,
    int batchDelayMs
) {

    /** Default maximum items per packet */
    public static final int DEFAULT_MAX_ITEMS_PER_PACKET = 50;

    /** Default batch delay in milliseconds */
    public static final int DEFAULT_BATCH_DELAY_MS = 50;

    /**
     * Compact constructor with validation.
     */
    public ItemSyncConfig {
        if (maxItemsPerPacket <= 0) {
            throw new IllegalArgumentException("maxItemsPerPacket must be positive");
        }
        if (batchDelayMs < 0) {
            throw new IllegalArgumentException("batchDelayMs cannot be negative");
        }
    }

    /**
     * Creates the default configuration.
     *
     * @return Default ItemSyncConfig
     */
    @Nonnull
    public static ItemSyncConfig defaults() {
        return new ItemSyncConfig(
            true,
            DEFAULT_MAX_ITEMS_PER_PACKET,
            DEFAULT_BATCH_DELAY_MS
        );
    }

    /**
     * Creates a disabled configuration.
     *
     * @return Disabled ItemSyncConfig
     */
    @Nonnull
    public static ItemSyncConfig disabled() {
        return new ItemSyncConfig(
            false,
            DEFAULT_MAX_ITEMS_PER_PACKET,
            DEFAULT_BATCH_DELAY_MS
        );
    }

    /**
     * Creates a config builder.
     *
     * @return New builder instance
     */
    @Nonnull
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for ItemSyncConfig.
     */
    public static final class Builder {
        private boolean enabled = true;
        private int maxItemsPerPacket = DEFAULT_MAX_ITEMS_PER_PACKET;
        private int batchDelayMs = DEFAULT_BATCH_DELAY_MS;

        private Builder() {}

        @Nonnull
        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        @Nonnull
        public Builder maxItemsPerPacket(int maxItemsPerPacket) {
            this.maxItemsPerPacket = maxItemsPerPacket;
            return this;
        }

        @Nonnull
        public Builder batchDelayMs(int batchDelayMs) {
            this.batchDelayMs = batchDelayMs;
            return this;
        }

        @Nonnull
        public ItemSyncConfig build() {
            return new ItemSyncConfig(enabled, maxItemsPerPacket, batchDelayMs);
        }
    }
}
