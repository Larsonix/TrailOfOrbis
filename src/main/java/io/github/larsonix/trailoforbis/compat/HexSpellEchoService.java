package io.github.larsonix.trailoforbis.compat;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

// Direct Hexcode imports — this class is only loaded when Hexcode is present
import com.riprod.hexcode.core.state.execution.HexExecuter;
import com.riprod.hexcode.core.state.execution.component.HexContext;
import com.riprod.hexcode.core.state.execution.component.VolatilityTracker;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * True spell echo: re-casts the hex spell at reduced power and zero mana cost.
 *
 * <p>When echo procs (based on {@code spellEchoChance} from the skill tree),
 * this service deep-clones the original HexContext, reduces power to
 * {@code echo_damage_percent}%, sets mana cost to zero, and calls
 * {@link HexExecuter#runPostGate(HexContext, CommandBuffer)} to re-execute
 * the entire glyph chain. The player sees their spell fire again visually.
 *
 * <h3>Why runPostGate and not cast()</h3>
 * <ul>
 *   <li>{@code cast()} invokes a new HexCastEvent via buffer.invoke(), which would
 *       trigger our handler recursively → echo-of-echo infinite loop</li>
 *   <li>{@code runPostGate()} skips the event AND CastGate entirely — no recursion,
 *       no spell slot consumption, no charge check</li>
 * </ul>
 *
 * <h3>Handler ordering (verified)</h3>
 * Hexcode registers HexCastEventSystem during setup(). We register during start().
 * ECS dispatches in registration order, so by the time our handler fires, the original
 * spell has fully executed. Echo fires AFTER the original — correct visual ordering.
 */
public final class HexSpellEchoService {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** How long echo stays on cooldown after a proc. */
    private static final long ECHO_WINDOW_MS = 10_000L;

    /** Per-player echo cooldown tracking (playerUuid → last echo timestamp). */
    private static final ConcurrentHashMap<UUID, Long> lastEchoTimestamps = new ConcurrentHashMap<>();

    private HexSpellEchoService() {}

    /**
     * Rolls for spell echo and, if successful, immediately re-casts the spell.
     *
     * <p>Called from HexCastEventHandler AFTER the original spell has executed
     * (our handler fires after Hexcode's due to registration order).
     *
     * @param casterUuid   The caster's player UUID
     * @param originalCtx  The HexContext from the original cast (post-execution state)
     * @param buffer       The CommandBuffer from the current ECS tick
     * @param echoChance   From ComputedStats.getSpellEchoChance() (0-100%)
     * @param config       Spell config for echo_damage_percent and echo_cooldown_ms
     * @return true if echo fired, false otherwise
     */
    public static boolean tryFireEcho(
            @Nonnull UUID casterUuid,
            @Nonnull HexContext originalCtx,
            @Nonnull CommandBuffer<EntityStore> buffer,
            float echoChance,
            @Nonnull HexcodeSpellConfig config) {

        if (echoChance <= 0) return false;

        // Gate: echo must have non-zero power to fire (0 = disabled in config)
        float echoPct = config.getEcho_damage_percent() / 100f;
        if (echoPct <= 0f) return false;

        // Cooldown check
        long now = System.currentTimeMillis();
        long cooldownMs = config.getEcho_cooldown_ms();
        Long lastEcho = lastEchoTimestamps.get(casterUuid);
        if (lastEcho != null && (now - lastEcho) < cooldownMs) {
            return false;
        }

        // Roll
        if (Math.random() * 100.0 >= echoChance) {
            return false;
        }

        // Echo procs — clone context and re-cast
        try {
            float originalPower = originalCtx.getPowerMultiplier();

            HexContext echoCtx = HexContext.cloneState(originalCtx);
            echoCtx.setPowerMultiplier(originalPower * echoPct);
            echoCtx.setManaCost(0f);

            // Give echo its own volatility budget (proportional, not inherited from drained original)
            VolatilityTracker originalTracker = originalCtx.getVolatilityTracker();
            if (originalTracker != null) {
                float echoBudget = originalTracker.getStartingBudget() * echoPct;
                echoCtx.setVolatilityOverride(echoBudget);
            }

            // Inject runtime accessors (not codec'd, so cloneState doesn't copy them)
            echoCtx.UpdateAccessor(buffer);
            echoCtx.UpdateChunkAccessor(
                    buffer.getExternalData().getWorld().getChunkStore().getStore());

            // Fire the echo — runPostGate skips HexCastEvent and CastGate
            HexExecuter.runPostGate(echoCtx, buffer);

            lastEchoTimestamps.put(casterUuid, now);

            LOGGER.atInfo().log("[SpellEcho] Echo fired for %s at %.0f%% power (chance=%.1f%%)",
                    casterUuid.toString().substring(0, 8), echoPct * 100f, echoChance);
            return true;

        } catch (Exception e) {
            LOGGER.atWarning().log("[SpellEcho] Echo re-cast failed for %s: %s",
                    casterUuid.toString().substring(0, 8), e.getMessage());
            return false;
        }
    }

    /**
     * Removes cooldown tracking for a disconnecting player.
     */
    public static void onPlayerDisconnect(@Nonnull UUID playerId) {
        lastEchoTimestamps.remove(playerId);
    }

    /**
     * Clears all state. Called during plugin shutdown.
     */
    public static void clear() {
        lastEchoTimestamps.clear();
    }
}
