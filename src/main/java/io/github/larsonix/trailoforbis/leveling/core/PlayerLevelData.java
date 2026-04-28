package io.github.larsonix.trailoforbis.leveling.core;

import javax.annotation.Nonnull;
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
 * @param createdAt When the record was first created
 * @param lastUpdated When the record was last modified
 */
public record PlayerLevelData(
    @Nonnull UUID uuid,
    long xp,
    @Nonnull Instant createdAt,
    @Nonnull Instant lastUpdated
) {

    /**
     * Creates a new PlayerLevelData with validated values.
     *
     * @param uuid The player's UUID
     * @param xp The player's total XP (clamped to >= 0)
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
        return new PlayerLevelData(uuid, 0, now, now);
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
        return new PlayerLevelData(uuid, xp, now, now);
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
        return new PlayerLevelData(uuid, Math.max(0, newXp), createdAt, Instant.now());
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
        return String.format("PlayerLevelData{uuid=%s, xp=%d, created=%s, updated=%s}",
            uuid, xp, createdAt, lastUpdated);
    }
}
