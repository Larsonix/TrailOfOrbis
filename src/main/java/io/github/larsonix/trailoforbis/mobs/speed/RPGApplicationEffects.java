package io.github.larsonix.trailoforbis.mobs.speed;

import com.hypixel.hytale.protocol.Color;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.ApplicationEffects;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelParticle;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Custom ApplicationEffects subclass that allows programmatic configuration
 * of all visual/audio/movement effect fields.
 *
 * <p>Hytale's ApplicationEffects has protected fields, so we extend it to gain access.
 * Supports a fluent builder pattern for creating fully-configured effects.
 *
 * <p>Accessible fields from ApplicationEffects:
 * <ul>
 *   <li>{@code entityBottomTint} / {@code entityTopTint} - Entity color tints</li>
 *   <li>{@code particles} / {@code firstPersonParticles} - Particle effects</li>
 *   <li>{@code screenEffect} - Screen overlay effect path</li>
 *   <li>{@code horizontalSpeedMultiplier} - Movement speed modifier</li>
 *   <li>{@code soundEventIdLocal} / {@code soundEventIdWorld} - Sound events</li>
 *   <li>{@code modelVFXId} - Model VFX identifier</li>
 * </ul>
 */
public class RPGApplicationEffects extends ApplicationEffects {

    /**
     * Creates application effects with a specific speed multiplier.
     *
     * @param speedMultiplier The horizontal speed multiplier (1.0 = normal, 1.5 = 50% faster)
     */
    public RPGApplicationEffects(float speedMultiplier) {
        super();
        this.horizontalSpeedMultiplier = speedMultiplier;
    }

    /**
     * Creates a new RPGApplicationEffects with default values (no modifications).
     *
     * @return New instance with speed multiplier 1.0
     */
    @Nonnull
    public static RPGApplicationEffects create() {
        return new RPGApplicationEffects(1.0f);
    }

    /**
     * Sets the horizontal speed multiplier.
     *
     * @param multiplier Speed multiplier (1.0 = normal)
     */
    public void setHorizontalSpeedMultiplier(float multiplier) {
        this.horizontalSpeedMultiplier = multiplier;
    }

    /**
     * Sets entity tint colors (gradient from bottom to top).
     *
     * @param bottom Bottom tint color
     * @param top    Top tint color
     * @return this for chaining
     */
    @Nonnull
    public RPGApplicationEffects withTint(@Nullable Color bottom, @Nullable Color top) {
        this.entityBottomTint = bottom;
        this.entityTopTint = top;
        return this;
    }

    /**
     * Sets particle effects attached to the entity.
     *
     * @param particles Particle definitions (SystemId is the key field)
     * @return this for chaining
     */
    @Nonnull
    public RPGApplicationEffects withParticles(@Nonnull ModelParticle... particles) {
        this.particles = particles;
        return this;
    }

    /**
     * Sets the screen overlay effect (rendered on the affected player's screen).
     *
     * @param screenEffectPath Path to the screen effect (e.g., "ScreenEffects/Fire.png")
     * @return this for chaining
     */
    @Nonnull
    public RPGApplicationEffects withScreenEffect(@Nullable String screenEffectPath) {
        this.screenEffect = screenEffectPath;
        return this;
    }

    /**
     * Sets the model VFX applied to the entity.
     *
     * @param vfxId Model VFX identifier (e.g., "Burn", "Freeze")
     * @return this for chaining
     */
    @Nonnull
    public RPGApplicationEffects withModelVFX(@Nullable String vfxId) {
        this.modelVFXId = vfxId;
        return this;
    }

    /**
     * Sets sound events played while the effect is active.
     *
     * @param worldSound Sound event ID heard by nearby players (must be mono), or null
     * @param localSound Sound event ID heard only by the affected player, or null
     * @return this for chaining
     */
    @Nonnull
    public RPGApplicationEffects withSounds(@Nullable String worldSound, @Nullable String localSound) {
        this.soundEventIdWorld = worldSound;
        this.soundEventIdLocal = localSound;
        return this;
    }

    /**
     * Sets the speed multiplier (fluent variant).
     *
     * @param multiplier Speed multiplier (1.0 = normal, 0.7 = 30% slow)
     * @return this for chaining
     */
    @Nonnull
    public RPGApplicationEffects withSpeed(float multiplier) {
        this.horizontalSpeedMultiplier = multiplier;
        return this;
    }

    /**
     * Creates a Color from a hex string (e.g., "#cf2302" or "cf2302").
     *
     * <p>Hytale's Color uses byte fields (range -128 to 127 in Java).
     * Hex values 0x80-0xFF will naturally overflow to negative byte values,
     * which is the correct representation.
     *
     * @param hex Hex color string with or without '#' prefix
     * @return Color instance, or null if parsing fails
     */
    @Nullable
    public static Color colorFromHex(@Nonnull String hex) {
        String clean = hex.startsWith("#") ? hex.substring(1) : hex;
        if (clean.length() != 6) {
            return null;
        }
        int r = Integer.parseInt(clean.substring(0, 2), 16);
        int g = Integer.parseInt(clean.substring(2, 4), 16);
        int b = Integer.parseInt(clean.substring(4, 6), 16);
        return new Color((byte) r, (byte) g, (byte) b);
    }

    /**
     * Resolves sound event string IDs to internal indices.
     *
     * <p>Must be called after setting sound IDs and after the asset store is loaded,
     * otherwise sound events won't play. This delegates to the parent class's
     * {@code processConfig()} which looks up indices from SoundEvent's asset map.
     */
    public void resolveSoundIndices() {
        processConfig();
    }

    /**
     * Creates a simple ModelParticle with just a system ID.
     *
     * <p>Uses EntityPart.Self as target (particle spawns on the affected entity).
     *
     * @param systemId Particle system ID (e.g., "Effect_Fire", "Effect_Poison")
     * @return ModelParticle instance
     */
    @Nonnull
    public static ModelParticle particle(@Nonnull String systemId) {
        ModelParticle p = new ModelParticle();
        p.setSystemId(systemId);
        return p;
    }
}
