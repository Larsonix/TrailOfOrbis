package io.github.larsonix.trailoforbis.compat.glyph;

import com.hypixel.hytale.logger.HytaleLogger;
import com.riprod.hexcode.core.common.glyphs.component.GlyphHandler;
import com.riprod.hexcode.core.common.glyphs.registry.GlyphRegistry;

/**
 * Patches Hexcode glyphs that have user-controllable slots affecting damage/magnitude
 * but don't scale their volatility cost by those values.
 *
 * <p>Uses {@link CostScaledGlyphWrapper} to wrap the original handlers, adding
 * mana cost scaling proportional to the exploitable slot value.
 * When a player wires an extreme value (like 16^5) into a damage slot, the inflated
 * mana cost drains their mana pool — or fizzles if they can't afford it.
 *
 * <p><b>Must be called AFTER Hexcode's BuiltinPlugin has registered its glyphs.</b>
 * Since our {@code start()} runs after all plugins' {@code setup()}, this is guaranteed.
 *
 * <p>Glyphs already safe (verified by audit):
 * Area, Scale, Force, Domain, Conjure, Phase, Erode, Fortify,
 * Growth (clamped), Drain (stat-capped), Ignite, Freeze, Halt, Levitate, Beam, Arc
 */
public final class HexGlyphPatcher {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private HexGlyphPatcher() {}

    /**
     * Patches all exploitable glyphs with cost-scaled wrappers.
     */
    public static void patchAll() {
        int patched = 0;

        // Bolt: POWER slot controls direct damage, default 15.0 (from Bolt.json)
        // Exploit: wire 16^5 into POWER → 1M damage for 6 volatility
        patched += patch("Bolt", "power", 15.0);

        // Gust: MAGNITUDE slot controls knockback + explosion damage, default 20.0
        // Exploit: wire extreme magnitude → massive knockback + damage
        patched += patch("Gust", "magnitude", 20.0);

        // Projectile: SPEED slot controls launch velocity, default 30.0
        // Exploit: wire extreme speed → impossible to dodge, physics abuse
        patched += patch("Projectile", "speed", 30.0);

        // Glaciate: OFFSET slot controls ice spawn height, default 5.0
        // Higher height → higher fall velocity → higher damage (damage = speed × multiplier)
        // Without scaling, players can wire extreme heights for massive damage at no cost
        patched += patch("Glaciate", "offset", 5.0);

        // Ensnare: DAMAGE slot controls per-spike damage, default 3.0
        // Hexcode's harshScale handles volatility, but mana cost must also scale
        patched += patch("Ensnare", "damage", 3.0);

        LOGGER.atInfo().log("[HexGlyphPatcher] Patched %d glyphs with mana-scaled cost", patched);
    }

    /**
     * Wraps a single glyph handler with cost scaling for a named slot.
     *
     * @param glyphId The glyph ID (e.g., "Bolt")
     * @param slotName The slot to scale cost by (e.g., "power")
     * @param defaultValue The expected default value (e.g., 5.0)
     * @return 1 if patched, 0 if glyph not found
     */
    private static int patch(String glyphId, String slotName, double defaultValue) {
        GlyphHandler original = GlyphRegistry.get(glyphId);
        if (original == null) {
            LOGGER.atWarning().log("[HexGlyphPatcher] Glyph '%s' not found in registry — skipping", glyphId);
            return 0;
        }

        // Don't double-wrap
        if (original instanceof CostScaledGlyphWrapper) {
            LOGGER.atFine().log("[HexGlyphPatcher] Glyph '%s' already wrapped — skipping", glyphId);
            return 0;
        }

        GlyphRegistry.register(new CostScaledGlyphWrapper(original, slotName, defaultValue));
        LOGGER.atInfo().log("[HexGlyphPatcher] Wrapped '%s' — slot '%s' now scales mana cost (default=%.1f)",
                glyphId, slotName, defaultValue);
        return 1;
    }
}
