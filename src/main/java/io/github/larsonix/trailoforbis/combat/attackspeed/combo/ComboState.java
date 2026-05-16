package io.github.larsonix.trailoforbis.combat.attackspeed.combo;

import io.github.larsonix.trailoforbis.gear.model.WeaponType;

import javax.annotation.Nullable;

/**
 * Mutable per-player combo tracking state.
 *
 * <p>Tracks the player's position in an attack combo chain:
 * <ul>
 *   <li>{@code stage} — current combo hit (1 = first hit, no bonus; 2+ = bonus)</li>
 *   <li>{@code lastChainId} — last seen {@code InteractionChain.getChainId()} to detect new attacks</li>
 *   <li>{@code lastHitMs} — wall-clock time of last combo hit for timeout detection</li>
 *   <li>{@code weaponType} — weapon type at last hit, to reset on weapon switch</li>
 * </ul>
 */
final class ComboState {

    int stage = 1;
    int lastChainId = -1;
    long lastHitMs;
    @Nullable WeaponType weaponType;

    ComboState() {
        this.lastHitMs = System.currentTimeMillis();
    }

    void reset() {
        stage = 1;
        lastChainId = -1;
        lastHitMs = System.currentTimeMillis();
    }
}
