package io.github.larsonix.trailoforbis.combat.effects.impl;

import com.hypixel.hytale.logger.HytaleLogger;
import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.combat.EnergyShieldTracker;
import io.github.larsonix.trailoforbis.combat.effects.CombatEffectContext;
import io.github.larsonix.trailoforbis.combat.effects.KeystoneCombatEffect;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Water-Earth Bridge: Glacial Fortress.
 * +3% Block Chance (stat modifier). While blocking, ES regen is not interrupted by hits.
 *
 * <p>Implementation: Toggles the {@code blockProtectsESRegen} flag on
 * {@link EnergyShieldTracker} when this effect activates/deactivates.
 * The tracker skips resetting the regen delay timer when the flag is set.
 */
public class GlacialFortressEffect extends KeystoneCombatEffect {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final EnergyShieldTracker shieldTracker;

    public GlacialFortressEffect(@Nonnull EnergyShieldTracker shieldTracker) {
        super("bridge_water_earth_3");
        this.shieldTracker = shieldTracker;
    }

    @Nonnull @Override public String getId() { return "glacial_fortress"; }

    @Override
    public void onStatsRecalculated(@Nonnull UUID playerId, @Nonnull ComputedStats newStats) {
        // Enable ES regen protection while this effect is active
        shieldTracker.setBlockProtectsESRegen(playerId, true);
        LOGGER.atFine().log("Glacial Fortress: ES regen protection enabled for %s", playerId);
    }

    @Override
    public void cleanup(@Nonnull UUID playerId) {
        shieldTracker.setBlockProtectsESRegen(playerId, false);
        LOGGER.atFine().log("Glacial Fortress: ES regen protection disabled for %s", playerId);
    }
}
