package io.github.larsonix.trailoforbis.combat;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks per-player energy shield state for the combat system.
 *
 * <p>Energy shield is a damage buffer that absorbs incoming damage before HP.
 * The shield has a maximum value derived from {@link io.github.larsonix.trailoforbis.attributes.ComputedStats#getEnergyShield()},
 * and regenerates after a configurable delay since the last hit absorbed by the shield.
 *
 * <p><b>Damage Priority:</b> Evasion → Block → Parry → <b>Shield Absorption</b> → Armor → HP
 *
 * <p>Thread-safe: uses ConcurrentHashMap for per-player state.
 */
public class EnergyShieldTracker {

    /**
     * Per-player shield state.
     *
     * @param currentShield Current shield HP remaining
     * @param lastHitTimeMs System.currentTimeMillis() when shield last absorbed damage (0 = never hit)
     */
    public record ShieldState(float currentShield, long lastHitTimeMs) {
        /** Creates initial state with full shield. */
        public static ShieldState full(float maxShield) {
            return new ShieldState(maxShield, 0L);
        }

        /** Returns a new state after absorbing damage. */
        public ShieldState afterAbsorb(float absorbed) {
            return new ShieldState(currentShield - absorbed, System.currentTimeMillis());
        }

        /** Returns a new state after regenerating. */
        public ShieldState afterRegen(float newCurrent) {
            return new ShieldState(newCurrent, lastHitTimeMs);
        }
    }

    private final ConcurrentHashMap<UUID, ShieldState> shields = new ConcurrentHashMap<>();

    /** Delay in milliseconds before shield starts regenerating after being hit. */
    private final long regenDelayMs;

    /**
     * Creates a new energy shield tracker.
     *
     * @param regenDelayMs Delay in milliseconds before shield regenerates after taking a hit
     */
    public EnergyShieldTracker(long regenDelayMs) {
        this.regenDelayMs = regenDelayMs;
    }

    /**
     * @return Shield state, or null if player has no shield tracking
     */
    @javax.annotation.Nullable
    public ShieldState getState(@Nonnull UUID uuid) {
        return shields.get(uuid);
    }

    /**
     * Absorbs damage into the shield, returning the amount NOT absorbed (passed through to armor/HP).
     *
     * <p>If the player has no shield state or shield is depleted, returns the full damage amount.
     *
     * @param uuid The player's UUID
     * @param damage The incoming damage to absorb
     * @return The remaining damage after shield absorption (0 if fully absorbed)
     */
    public float absorbDamage(@Nonnull UUID uuid, float damage) {
        // Use atomic compute to prevent any theoretical read-modify-write race.
        // While ECS guarantees single-threaded world access per entity today,
        // this is the correct ConcurrentHashMap idiom and future-proofs against
        // any threading model changes.
        float[] remaining = {damage};
        shields.computeIfPresent(uuid, (key, state) -> {
            if (state.currentShield() <= 0) {
                return state;
            }
            float absorbed = Math.min(damage, state.currentShield());
            remaining[0] = damage - absorbed;
            return state.afterAbsorb(absorbed);
        });
        return remaining[0];
    }

    /**
     * Gets the amount that was absorbed by the last call context.
     * Convenience: call before and after absorbDamage to get the absorbed amount.
     */
    public float getAbsorbedAmount(@Nonnull UUID uuid, float damageBefore, float damageAfter) {
        return damageBefore - damageAfter;
    }

    /**
     * Updates the shield for regeneration tick.
     *
     * <p>Only regenerates if enough time has passed since the last hit.
     * Shield regenerates at a rate proportional to max shield per second.
     *
     * @param uuid The player's UUID
     * @param maxShield The player's maximum energy shield from ComputedStats
     * @param regenPerSecond Shield regen amount per second (typically a fraction of maxShield)
     * @param dt Delta time in seconds
     */
    public void tickRegen(@Nonnull UUID uuid, float maxShield, float regenPerSecond, float dt) {
        if (maxShield <= 0 || regenPerSecond <= 0) {
            shields.remove(uuid);
            return;
        }

        shields.compute(uuid, (key, state) -> {
            if (state == null) {
                return ShieldState.full(maxShield);
            }

            float current = Math.min(state.currentShield(), maxShield);

            if (current >= maxShield) {
                return current != state.currentShield()
                    ? new ShieldState(maxShield, state.lastHitTimeMs())
                    : state;
            }

            long timeSinceHit = System.currentTimeMillis() - state.lastHitTimeMs();
            if (state.lastHitTimeMs() > 0 && timeSinceHit < regenDelayMs) {
                return state;
            }

            float newShield = Math.min(current + regenPerSecond * dt, maxShield);
            return state.afterRegen(newShield);
        });
    }

    /**
     * Directly adds shield to a player, capped at their maximum.
     *
     * <p>Used by the SHIELD_REGEN_ON_DOT mechanic to restore shield
     * when the player's DoTs deal damage.
     *
     * @param uuid      The player's UUID
     * @param amount    The shield amount to add
     * @param maxShield The player's maximum energy shield
     */
    public void addShield(@Nonnull UUID uuid, float amount, float maxShield) {
        if (amount <= 0 || maxShield <= 0) {
            return;
        }

        shields.compute(uuid, (key, state) -> {
            if (state == null) {
                return new ShieldState(Math.min(amount, maxShield), 0L);
            }
            float newShield = Math.min(state.currentShield() + amount, maxShield);
            return new ShieldState(newShield, state.lastHitTimeMs());
        });
    }

    /**
     * Cleans up shield state when a player disconnects.
     */
    public void cleanupPlayer(@Nonnull UUID uuid) {
        shields.remove(uuid);
    }

    public long getRegenDelayMs() {
        return regenDelayMs;
    }
}
