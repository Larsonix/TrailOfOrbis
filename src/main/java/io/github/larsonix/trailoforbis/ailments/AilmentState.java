package io.github.larsonix.trailoforbis.ailments;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Immutable state for a single ailment instance on an entity.
 *
 * <p>Following the EnergyShieldTracker.ShieldState pattern, this record is immutable
 * with {@code afterX()} methods that return new instances for state transitions.
 *
 * <p><b>Magnitude meaning varies by ailment type:</b>
 * <ul>
 *   <li>BURN: Damage per second (DPS)</li>
 *   <li>FREEZE: Slow percentage (0-30%)</li>
 *   <li>SHOCK: Increased damage taken percentage (0-50%)</li>
 *   <li>POISON: Damage per second per stack</li>
 * </ul>
 *
 * @param sourceUuid        UUID of the entity that applied this ailment (for death recap)
 * @param totalDuration     Original total duration (for UI progress bars)
 * @param magnitude         Effect strength (DPS for DoT, % for debuffs)
 * @param appliedAtMs       System.currentTimeMillis() when applied
 */
public record AilmentState(
    @Nonnull AilmentType type,
    @Nonnull UUID sourceUuid,
    float remainingDuration,
    float totalDuration,
    float magnitude,
    long appliedAtMs
) {

    /**
     * Creates a new Burn ailment state.
     */
    @Nonnull
    public static AilmentState burn(float dps, float durationSec, @Nonnull UUID source) {
        return new AilmentState(
            AilmentType.BURN,
            source,
            durationSec,
            durationSec,
            dps,
            System.currentTimeMillis()
        );
    }

    /**
     * Creates a new Freeze ailment state.
     *
     * @param slowPercent  Slow percentage (0-30)
     */
    @Nonnull
    public static AilmentState freeze(float slowPercent, float durationSec, @Nonnull UUID source) {
        return new AilmentState(
            AilmentType.FREEZE,
            source,
            durationSec,
            durationSec,
            Math.min(slowPercent, 30f), // Cap at 30%
            System.currentTimeMillis()
        );
    }

    /**
     * Creates a new Shock ailment state.
     *
     * @param damageIncrease  Increased damage taken percentage (0-50)
     */
    @Nonnull
    public static AilmentState shock(float damageIncrease, float durationSec, @Nonnull UUID source) {
        return new AilmentState(
            AilmentType.SHOCK,
            source,
            durationSec,
            durationSec,
            Math.min(damageIncrease, 50f), // Cap at 50%
            System.currentTimeMillis()
        );
    }

    /**
     * Creates a new Poison ailment state (single stack).
     */
    @Nonnull
    public static AilmentState poison(float dps, float durationSec, @Nonnull UUID source) {
        return new AilmentState(
            AilmentType.POISON,
            source,
            durationSec,
            durationSec,
            dps,
            System.currentTimeMillis()
        );
    }

    /**
     * Returns a new state with updated remaining duration after tick.
     */
    @Nonnull
    public AilmentState afterTick(float dt) {
        return new AilmentState(
            type,
            sourceUuid,
            Math.max(0, remainingDuration - dt),
            totalDuration,
            magnitude,
            appliedAtMs
        );
    }

    /**
     * Returns a new state with specified remaining duration.
     */
    @Nonnull
    public AilmentState withDuration(float newDuration) {
        return new AilmentState(
            type,
            sourceUuid,
            newDuration,
            totalDuration,
            magnitude,
            appliedAtMs
        );
    }

    /**
     * Returns a new state with specified magnitude.
     */
    @Nonnull
    public AilmentState withMagnitude(float newMagnitude) {
        return new AilmentState(
            type,
            sourceUuid,
            remainingDuration,
            totalDuration,
            newMagnitude,
            appliedAtMs
        );
    }

    /**
     * Returns a refreshed state with new duration and optionally stronger magnitude.
     * Used for non-stacking ailments (Burn, Freeze, Shock) on reapplication.
     *
     * <p>Takes the maximum of current and new magnitude (stronger effect wins).
     *
     * @param newDuration  New duration in seconds
     * @param newMagnitude New magnitude to compare
     */
    @Nonnull
    public AilmentState refresh(float newDuration, float newMagnitude) {
        return new AilmentState(
            type,
            sourceUuid,
            Math.max(remainingDuration, newDuration),
            Math.max(totalDuration, newDuration),
            Math.max(magnitude, newMagnitude), // Take stronger effect
            System.currentTimeMillis()
        );
    }

    /**
     * @return true if remaining duration is 0 or less
     */
    public boolean isExpired() {
        return remainingDuration <= 0;
    }

    /**
     * @return Value between 0.0 (expired) and 1.0 (full duration), for UI progress bars
     */
    public float getDurationFraction() {
        if (totalDuration <= 0) {
            return 0f;
        }
        return Math.max(0f, Math.min(1f, remainingDuration / totalDuration));
    }

    /**
     * @return Damage to apply this tick, or 0 if not a DoT ailment
     */
    public float calculateDamageThisTick(float dt) {
        if (!type.dealsDamage()) {
            return 0f;
        }
        return magnitude * dt;
    }

    /**
     * @return Milliseconds since application
     */
    public long getElapsedTimeMs() {
        return System.currentTimeMillis() - appliedAtMs;
    }

    @Override
    public String toString() {
        return String.format("%s[mag=%.1f, dur=%.1f/%.1fs, src=%s]",
            type.getDisplayName(), magnitude, remainingDuration, totalDuration,
            sourceUuid.toString().substring(0, 8));
    }
}
