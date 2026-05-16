package io.github.larsonix.trailoforbis.combat.attackspeed.combo;

import io.github.larsonix.trailoforbis.combat.attackspeed.config.WeaponSpeedProfile;
import io.github.larsonix.trailoforbis.gear.model.WeaponType;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks per-player attack combo stage for the attack speed pipeline.
 *
 * <p>Each successive Primary attack increments the combo stage, up to the weapon
 * profile's {@code comboStages} maximum. The combo resets on timeout, weapon switch,
 * or player disconnect.
 *
 * <p><b>Detection pattern</b> (from AAF): each Hytale {@code InteractionChain} has a
 * unique {@code getChainId()} int. A new attack creates a new chain with a new ID.
 * By comparing the current Primary chain's ID to the last one we saw, we detect
 * "new attack started" → increment combo. During cooldown gaps (no active chain),
 * the combo timer just ticks toward timeout.
 *
 * <p><b>Thread safety:</b> Uses ConcurrentHashMap for the player state map. Individual
 * state objects are mutable but only accessed from the world tick thread (ECS system).
 */
public class ComboTracker {

    private final ConcurrentHashMap<UUID, ComboState> states = new ConcurrentHashMap<>();

    /**
     * Updates combo state and returns the current combo stage.
     *
     * <p>Called once per tick per player from {@code InteractionTimeShiftSystem},
     * BEFORE the resolver computes multipliers.
     *
     * @param uuid Player UUID
     * @param activePrimaryChainId The active Primary chain's chainId, or -1 if no active chain
     * @param weaponType The player's held weapon type
     * @param profile The weapon's speed profile (contains combo config)
     * @return Current combo stage (1 = first hit / no bonus, 2+ = bonus active)
     */
    public int update(
            @Nonnull UUID uuid,
            int activePrimaryChainId,
            @Nonnull WeaponType weaponType,
            @Nonnull WeaponSpeedProfile profile
    ) {
        int maxStages = profile.getComboStages();
        if (maxStages <= 1) {
            // Weapon doesn't support combos (bows, staves, etc.)
            states.remove(uuid);
            return 1;
        }

        ComboState state = states.get(uuid);

        // No active primary chain → check timeout, return current stage
        if (activePrimaryChainId < 0) {
            if (state == null) {
                return 1;
            }
            // Check timeout
            int resetMs = profile.getComboResetMs();
            if (resetMs > 0 && System.currentTimeMillis() - state.lastHitMs > resetMs) {
                state.reset();
            }
            return state.stage;
        }

        // Active chain exists — check for new attack
        if (state == null) {
            state = new ComboState();
            state.weaponType = weaponType;
            state.lastChainId = activePrimaryChainId;
            states.put(uuid, state);
            return 1;
        }

        // Check timeout first (combo expired before new attack landed)
        int resetMs = profile.getComboResetMs();
        if (resetMs > 0 && System.currentTimeMillis() - state.lastHitMs > resetMs) {
            state.reset();
            state.weaponType = weaponType;
            state.lastChainId = activePrimaryChainId;
            return 1;
        }

        // Check weapon switch → reset combo
        if (state.weaponType != weaponType) {
            state.reset();
            state.weaponType = weaponType;
            state.lastChainId = activePrimaryChainId;
            return 1;
        }

        // Same chain as last tick → no state change
        if (state.lastChainId == activePrimaryChainId) {
            return state.stage;
        }

        // NEW CHAIN detected → advance combo stage (caps at max, no wrap)
        // ARPG feel: build momentum, maintain peak speed until you stop attacking.
        // Stage goes 1→2→3→3→3 as long as combo is sustained.
        state.lastChainId = activePrimaryChainId;
        state.lastHitMs = System.currentTimeMillis();
        state.stage = Math.min(state.stage + 1, maxStages);

        return state.stage;
    }

    /**
     * Returns the current combo stage without modifying state.
     *
     * @return Combo stage (1 if not tracking)
     */
    public int getStage(@Nonnull UUID uuid) {
        ComboState state = states.get(uuid);
        return state != null ? state.stage : 1;
    }

    /**
     * Clears all tracked state for a player.
     * Called on disconnect, death, or world transition.
     */
    public void clear(@Nonnull UUID uuid) {
        states.remove(uuid);
    }

    /**
     * Clears all state for all players. Called during plugin shutdown.
     */
    public void shutdown() {
        states.clear();
    }
}
