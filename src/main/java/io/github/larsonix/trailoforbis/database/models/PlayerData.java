package io.github.larsonix.trailoforbis.database.models;

import io.github.larsonix.trailoforbis.attributes.ComputedStats;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable player data model with builder pattern.
 *
 * <p>Use {@link #builder()} or {@code withX()} methods to create modified copies.
 * This class is thread-safe due to immutability.
 *
 * <p>The elemental attribute system uses 6 elements instead of traditional RPG attributes:
 * <ul>
 *   <li>FIRE - Glass cannon: high damage, negative HP</li>
 *   <li>WATER - Glacier tank: health, regen, barrier</li>
 *   <li>LIGHTNING - Storm: attack speed, move speed, crits</li>
 *   <li>EARTH - Mountain: armor, block, thorns</li>
 *   <li>WIND - Ghost: evasion, accuracy, projectiles</li>
 *   <li>VOID - Dark bargain: life steal, low-life bonuses</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>
 * PlayerData player = PlayerData.builder()
 *     .uuid(playerId)
 *     .username("Steve")
 *     .fire(10)
 *     .water(5)
 *     .build();
 *
 * // Create modified copy
 * PlayerData updated = player.withFire(15);
 * </pre>
 */
public final class PlayerData {
    private final UUID uuid;
    private final String username;
    private final int fire;
    private final int water;
    private final int lightning;
    private final int earth;
    private final int wind;
    private final int voidAttr;  // "void" is a Java reserved keyword
    private final int unallocatedPoints;
    private final int attributeRefundPoints;
    private final int attributeRespecs;
    private final Instant createdAt;
    private final Instant lastSeen;

    // Transient: not persisted to database, recalculated on load
    private final ComputedStats computedStats;

    private PlayerData(Builder builder) {
        this.uuid = Objects.requireNonNull(builder.uuid, "uuid cannot be null");
        this.username = Objects.requireNonNull(builder.username, "username cannot be null");
        this.fire = builder.fire;
        this.water = builder.water;
        this.lightning = builder.lightning;
        this.earth = builder.earth;
        this.wind = builder.wind;
        this.voidAttr = builder.voidAttr;
        this.unallocatedPoints = builder.unallocatedPoints;
        this.attributeRefundPoints = builder.attributeRefundPoints;
        this.attributeRespecs = builder.attributeRespecs;
        this.createdAt = builder.createdAt != null ? builder.createdAt : Instant.now();
        this.lastSeen = builder.lastSeen != null ? builder.lastSeen : Instant.now();
        this.computedStats = builder.computedStats;
    }

    // ==================== Getters ====================

    @Nonnull
    public UUID getUuid() {
        return uuid;
    }

    @Nonnull
    public String getUsername() {
        return username;
    }

    public int getFire() {
        return fire;
    }

    public int getWater() {
        return water;
    }

    public int getLightning() {
        return lightning;
    }

    public int getEarth() {
        return earth;
    }

    public int getWind() {
        return wind;
    }

    public int getVoidAttr() {
        return voidAttr;
    }

    public int getUnallocatedPoints() {
        return unallocatedPoints;
    }

    public int getAttributeRefundPoints() {
        return attributeRefundPoints;
    }

    public int getAttributeRespecs() {
        return attributeRespecs;
    }

    @Nonnull
    public Instant getCreatedAt() {
        return createdAt;
    }

    @Nonnull
    public Instant getLastSeen() {
        return lastSeen;
    }

    /** Transient (not persisted); recalculated from attributes when needed. */
    @Nullable
    public ComputedStats getComputedStats() {
        return computedStats;
    }

    public int getTotalAllocatedPoints() {
        return fire + water + lightning + earth + wind + voidAttr;
    }

    // ==================== With Methods (Immutable Updates) ====================

    public PlayerData withUsername(String username) {
        return toBuilder().username(username).build();
    }

    public PlayerData withFire(int fire) {
        return toBuilder().fire(fire).build();
    }

    public PlayerData withWater(int water) {
        return toBuilder().water(water).build();
    }

    public PlayerData withLightning(int lightning) {
        return toBuilder().lightning(lightning).build();
    }

    public PlayerData withEarth(int earth) {
        return toBuilder().earth(earth).build();
    }

    public PlayerData withWind(int wind) {
        return toBuilder().wind(wind).build();
    }

    public PlayerData withVoidAttr(int voidAttr) {
        return toBuilder().voidAttr(voidAttr).build();
    }

    public PlayerData withUnallocatedPoints(int unallocatedPoints) {
        return toBuilder().unallocatedPoints(unallocatedPoints).build();
    }

    public PlayerData withAttributeRefundPoints(int attributeRefundPoints) {
        return toBuilder().attributeRefundPoints(attributeRefundPoints).build();
    }

    public PlayerData withAttributeRespecs(int attributeRespecs) {
        return toBuilder().attributeRespecs(attributeRespecs).build();
    }

    public PlayerData withLastSeen(Instant lastSeen) {
        return toBuilder().lastSeen(lastSeen).build();
    }

    public PlayerData withComputedStats(ComputedStats computedStats) {
        return toBuilder().computedStats(computedStats).build();
    }

    // ==================== Builder ====================

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
            .uuid(uuid)
            .username(username)
            .fire(fire)
            .water(water)
            .lightning(lightning)
            .earth(earth)
            .wind(wind)
            .voidAttr(voidAttr)
            .unallocatedPoints(unallocatedPoints)
            .attributeRefundPoints(attributeRefundPoints)
            .attributeRespecs(attributeRespecs)
            .createdAt(createdAt)
            .lastSeen(lastSeen)
            .computedStats(computedStats);
    }

    public static final class Builder {
        private UUID uuid;
        private String username;
        private int fire = 0;
        private int water = 0;
        private int lightning = 0;
        private int earth = 0;
        private int wind = 0;
        private int voidAttr = 0;
        private int unallocatedPoints = 0;
        private int attributeRefundPoints = 10;
        private int attributeRespecs = 0;
        private Instant createdAt;
        private Instant lastSeen;
        private ComputedStats computedStats;

        private Builder() {
        }

        public Builder uuid(UUID uuid) {
            this.uuid = uuid;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder fire(int fire) {
            this.fire = fire;
            return this;
        }

        public Builder water(int water) {
            this.water = water;
            return this;
        }

        public Builder lightning(int lightning) {
            this.lightning = lightning;
            return this;
        }

        public Builder earth(int earth) {
            this.earth = earth;
            return this;
        }

        public Builder wind(int wind) {
            this.wind = wind;
            return this;
        }

        public Builder voidAttr(int voidAttr) {
            this.voidAttr = voidAttr;
            return this;
        }

        public Builder unallocatedPoints(int unallocatedPoints) {
            this.unallocatedPoints = unallocatedPoints;
            return this;
        }

        public Builder attributeRefundPoints(int attributeRefundPoints) {
            this.attributeRefundPoints = attributeRefundPoints;
            return this;
        }

        public Builder attributeRespecs(int attributeRespecs) {
            this.attributeRespecs = attributeRespecs;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder lastSeen(Instant lastSeen) {
            this.lastSeen = lastSeen;
            return this;
        }

        public Builder computedStats(ComputedStats computedStats) {
            this.computedStats = computedStats;
            return this;
        }

        public PlayerData build() {
            return new PlayerData(this);
        }
    }

    // ==================== Object Methods ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlayerData that = (PlayerData) o;
        return uuid.equals(that.uuid);
    }

    @Override
    public int hashCode() {
        return uuid.hashCode();
    }

    @Override
    public String toString() {
        return String.format(
            "PlayerData{uuid=%s, username='%s', fire=%d, water=%d, lightning=%d, earth=%d, wind=%d, void=%d, unalloc=%d}",
            uuid, username, fire, water, lightning, earth, wind, voidAttr, unallocatedPoints
        );
    }
}
