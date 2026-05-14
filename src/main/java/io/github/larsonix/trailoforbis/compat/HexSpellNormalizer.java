package io.github.larsonix.trailoforbis.compat;

import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;

/**
 * Per-glyph damage normalization service. Converts raw Hexcode vanilla damage
 * into an RPG base damage multiplier using config-driven balance profiles.
 *
 * <p>Each spell type has its own normalization strategy:
 * <ul>
 *   <li><b>LINEAR</b> — strip magicPower (if applicable) → divide by slotDefault → cap</li>
 *   <li><b>PHYSICS</b> — strip magicPower → reverse v²/2g → height/slotDefault → cap</li>
 *   <li><b>FIXED</b> — ignore vanilla damage, return powerScale directly</li>
 * </ul>
 *
 * <p>Replaces the 100-line inline normalization block in RPGDamageSystem with
 * a 3-line call: {@code HexSpellNormalizer.normalize(source, vanilla, power, profile)}.
 */
public final class HexSpellNormalizer {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private HexSpellNormalizer() {}

    /**
     * Normalizes hex vanilla damage into an RPG base damage multiplier.
     *
     * @param sourceType     "hex_bolt", "hex_glaciate", etc.
     * @param vanillaDamage  Raw damage value from Hexcode's glyph/construct system
     * @param magicPower     The caster's magicPowerMultiplier at cast time
     * @param profile        Per-spell balance profile from config
     * @return Multiplier to apply to ourRPGPower for rpgBaseDamage
     */
    public static float normalize(@Nonnull String sourceType, float vanillaDamage,
                                   float magicPower,
                                   @Nonnull HexcodeSpellConfig.SpellBalanceProfile profile) {
        float result;

        switch (profile.normalization()) {
            case LINEAR -> {
                // Standard: strip magicPower (if Hexcode applied it), normalize by slot default
                float rawGlyph = profile.strips_magic_power()
                        ? vanillaDamage / Math.max(0.01f, magicPower)
                        : vanillaDamage;
                float slotMultiplier = profile.slot_default() > 0
                        ? rawGlyph / profile.slot_default()
                        : 1.0f;
                result = Math.min(profile.multiplier_cap(),
                        Math.max(0.1f, slotMultiplier)) * profile.power_scale();
            }
            case PHYSICS -> {
                // Velocity-based construct (Glaciate): reverse v²/2g → height → normalize
                float velocity = profile.strips_magic_power()
                        ? vanillaDamage / Math.max(0.01f, magicPower)
                        : vanillaDamage;
                float gravity = profile.physics_gravity() > 0 ? profile.physics_gravity() : 20.0f;
                float height = (velocity * velocity) / (2f * gravity);
                float heightMult = profile.slot_default() > 0
                        ? height / profile.slot_default()
                        : 1.0f;
                result = Math.min(profile.multiplier_cap(),
                        Math.max(0.1f, heightMult)) * profile.power_scale();
            }
            case FIXED -> {
                // Hardcoded damage (Phase): ignore vanilla value entirely
                result = profile.power_scale();
            }
            default -> {
                result = profile.power_scale();
            }
        }

        LOGGER.atFine().log("[SpellNorm] %s: vanilla=%.1f, magic=%.1f, norm=%s, scale=%.2f, cap=%.1f → mult=%.3f",
                sourceType, vanillaDamage, magicPower,
                profile.normalization(), profile.power_scale(),
                profile.multiplier_cap(), result);

        return result;
    }

    /** Normalization strategy types. */
    public enum NormalizationType {
        /** vanillaDamage / magicPower / slotDefault (Bolt, Ensnare) */
        LINEAR,
        /** v²/2g height reversal (Glaciate) */
        PHYSICS,
        /** Ignore vanilla, use powerScale directly (Phase) */
        FIXED
    }
}
