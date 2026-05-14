package io.github.larsonix.trailoforbis.leveling.core;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable data model representing a player's leveling data.
 *
 * <p>Uses the record pattern for immutability. To modify data, use the
 * {@code with*} methods which return new instances:
 * <pre>
 * PlayerLevelData updated = data.withXp(data.xp() + gainedXp);
 * </pre>
 *
 * <p>Thread safety: Instances are immutable and safe to share across threads.
 *
 * @param uuid The player's UUID
 * @param xp The player's total XP (never negative)
 * @param storedLevel The last-known derived level (null if never recorded, used for curve migration protection)
 * @param createdAt When the record was first created
 * @param lastUpdated When the record was last modified
 */
public record PlayerLevelData(
    @Nonnull UUID uuid,
    long xp,
    @Nullable Integer storedLevel,
    @Nonnull Instant createdAt,
    @Nonnull Instant lastUpdated
) {

    /**
     * Creates a new PlayerLevelData with validated values.
     *
     * @param uuid The player's UUID
     * @param xp The player's total XP (clamped to >= 0)
     * @param storedLevel The last-known derived level (nullable)
     * @param createdAt When the record was first created
     * @param lastUpdated When the record was last modified
     */
    public PlayerLevelData {
        if (uuid == null) {
            throw new IllegalArgumentException("uuid cannot be null");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt cannot be null");
        }
        if (lastUpdated == null) {
            throw new IllegalArgumentException("lastUpdated cannot be null");
        }
        // Clamp XP to non-negative
        if (xp < 0) {
            xp = 0;
        }
    }

    /**
     * Creates a new player record with 0 XP.
     *
     * @param uuid The player's UUID
     * @return A new PlayerLevelData with 0 XP
     */
    @Nonnull
    public static PlayerLevelData createNew(@Nonnull UUID uuid) {
        Instant now = Instant.now();
        return new PlayerLevelData(uuid, 0, null, now, now);
    }

    /**
     * Creates a new player record with specified XP.
     *
     * @param uuid The player's UUID
     * @param xp The starting XP
     * @return A new PlayerLevelData
     */
    @Nonnull
    public static PlayerLevelData createWithXp(@Nonnull UUID uuid, long xp) {
        Instant now = Instant.now();
        return new PlayerLevelData(uuid, xp, null, now, now);
    }

    // ═══════════════════════════════════════════════════════════════════
    // BUILDER METHODS (return new instances)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Returns a new instance with updated XP.
     *
     * @param newXp The new XP value (clamped to >= 0)
     * @return A new PlayerLevelData with updated XP and timestamp
     */
    @Nonnull
    public PlayerLevelData withXp(long newXp) {
        return new PlayerLevelData(uuid, Math.max(0, newXp), storedLevel, createdAt, Instant.now());
    }

    /**
     * Returns a new instance with XP increased by the given amount.
     *
     * @param amount The amount to add (can be negative to remove)
     * @return A new PlayerLevelData with updated XP and timestamp
     */
    @Nonnull
    public PlayerLevelData withXpDelta(long amount) {
        return withXp(xp + amount);
    }

    /**
     * Returns a new instance with the stored level updated.
     *
     * <p>The stored level is persisted to DB and used to detect level loss
     * when the XP formula changes between versions.
     *
     * @param level The derived level to store
     * @return A new PlayerLevelData with updated stored level
     */
    @Nonnull
    public PlayerLevelData withStoredLevel(int level) {
        return new PlayerLevelData(uuid, xp, level, createdAt, Instant.now());
    }

    // ═══════════════════════════════════════════════════════════════════
    // UTILITY METHODS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Returns true if this record has been modified since creation.
     *
     * @return true if lastUpdated differs from createdAt
     */
    public boolean hasBeenModified() {
        return !lastUpdated.equals(createdAt);
    }

    @Override
    public String toString() {
        return String.format("PlayerLevelData{uuid=%s, xp=%d, storedLevel=%s, created=%s, updated=%s}",
            uuid, xp, storedLevel, createdAt, lastUpdated);
    }
}
