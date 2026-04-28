package io.github.larsonix.trailoforbis.mobs.speed;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.TrailOfOrbis;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages speed effects for scaled mobs in the RPG system.
 *
 * <p>This manager creates and registers custom EntityEffect assets with different
 * speed multipliers. When a mob spawns with a speed modifier, the appropriate
 * effect is applied via the Hytale effect system.
 *
 * <p>Speed effects are created on-demand and cached for reuse. They use a naming
 * convention of "rpg_speed_X.XX" where X.XX is the speed multiplier (e.g., "rpg_speed_1.25").
 *
 * <h2>How It Works</h2>
 * <ol>
 *   <li>On plugin start, register base speed effects for common values</li>
 *   <li>When a mob needs a speed multiplier, get or create the appropriate effect</li>
 *   <li>Apply the effect via EffectControllerComponent</li>
 *   <li>Effect modifies NPCEntity.getCurrentHorizontalSpeedMultiplier()</li>
 * </ol>
 */
public class MobSpeedEffectManager {

    private static final String EFFECT_ID_PREFIX = "rpg_speed_";
    private static final String PACK_KEY = "trailoforbis";

    private final TrailOfOrbis plugin;

    /**
     * Cache of created speed effects by multiplier.
     * Key: speed multiplier (e.g., 1.25f)
     * Value: The created RPGEntityEffect
     */
    private final ConcurrentHashMap<Float, RPGEntityEffect> effectCache = new ConcurrentHashMap<>();

    /**
     * Effects pending registration with the asset store.
     * Cleared after each registration batch.
     */
    private final List<RPGEntityEffect> pendingEffects = new ArrayList<>();

    /**
     * Whether the manager has been initialized.
     */
    private boolean initialized = false;

    public MobSpeedEffectManager(@Nonnull TrailOfOrbis plugin) {
        this.plugin = plugin;
    }

    /**
     * Initializes the speed effect system.
     *
     * <p>Pre-creates common speed multiplier effects to avoid runtime creation.
     * Called during plugin start phase.
     */
    public void initialize() {
        if (initialized) {
            plugin.getLogger().atWarning().log("MobSpeedEffectManager already initialized!");
            return;
        }

        plugin.getLogger().atInfo().log("Initializing mob speed effect system...");

        // Pre-create common speed multipliers
        // These cover the typical range for scaled mobs
        float[] commonMultipliers = {
            0.5f, 0.6f, 0.7f, 0.75f, 0.8f, 0.9f,  // Slower
            1.0f,                                    // Normal
            1.1f, 1.15f, 1.2f, 1.25f, 1.3f, 1.4f,  // Faster
            1.5f, 1.6f, 1.75f, 2.0f, 2.5f, 3.0f    // Much faster
        };

        for (float multiplier : commonMultipliers) {
            getOrCreateEffect(multiplier);
        }

        // Register all pre-created effects
        registerPendingEffects();

        initialized = true;
        plugin.getLogger().atInfo().log("Mob speed effect system initialized with " +
            effectCache.size() + " pre-created effects.");
    }

    /**
     * Gets or creates a speed effect for the given multiplier.
     *
     * <p>Effects are cached, so calling this multiple times with the same
     * multiplier returns the same effect instance.
     *
     * @param speedMultiplier The horizontal speed multiplier (1.0 = normal)
     * @return The speed effect, or null if creation failed
     */
    @Nullable
    public RPGEntityEffect getOrCreateEffect(float speedMultiplier) {
        // Clamp to reasonable range
        float clampedMultiplier = Math.max(0.1f, Math.min(10.0f, speedMultiplier));

        // Round to 2 decimal places for consistent caching
        float roundedMultiplier = Math.round(clampedMultiplier * 100.0f) / 100.0f;

        return effectCache.computeIfAbsent(roundedMultiplier, this::createSpeedEffect);
    }

    /**
     * Creates a new speed effect for the given multiplier.
     *
     * @param speedMultiplier The speed multiplier
     * @return The created effect
     */
    private RPGEntityEffect createSpeedEffect(float speedMultiplier) {
        String effectId = EFFECT_ID_PREFIX + String.format("%.2f", speedMultiplier);

        RPGEntityEffect effect = RPGEntityEffect.createSpeedEffect(effectId, speedMultiplier);

        // Add to pending registration
        synchronized (pendingEffects) {
            pendingEffects.add(effect);
        }

        plugin.getLogger().atFine().log("Created speed effect: " + effectId + " (multiplier: " + speedMultiplier + ")");

        return effect;
    }

    /**
     * Registers all pending effects with Hytale's asset store.
     *
     * <p>This must be called after creating new effects to make them available
     * for use with EffectControllerComponent.
     */
    public void registerPendingEffects() {
        List<EntityEffect> toRegister;

        synchronized (pendingEffects) {
            if (pendingEffects.isEmpty()) {
                return;
            }

            toRegister = new ArrayList<>(pendingEffects);
            pendingEffects.clear();
        }

        try {
            // Register with Hytale's asset store
            EntityEffect.getAssetStore().loadAssets(PACK_KEY, toRegister);

            plugin.getLogger().atInfo().log("Registered " + toRegister.size() + " speed effects with asset store.");
        } catch (Exception e) {
            plugin.getLogger().atSevere().withCause(e).log("Failed to register speed effects: " + e.getMessage());
        }
    }

    /**
     * Applies a speed effect to an entity.
     *
     * @param ref              Entity reference
     * @param speedMultiplier  Speed multiplier to apply (1.0 = normal)
     * @param accessor         Component accessor
     * @return true if effect was applied successfully
     */
    public boolean applySpeedEffect(
            @Nonnull Ref<EntityStore> ref,
            float speedMultiplier,
            @Nonnull ComponentAccessor<EntityStore> accessor) {

        // Skip if normal speed
        if (Math.abs(speedMultiplier - 1.0f) < 0.01f) {
            return true; // No effect needed
        }

        // Get the effect controller component
        EffectControllerComponent effectController = accessor.getComponent(
            ref, EffectControllerComponent.getComponentType()
        );

        if (effectController == null) {
            plugin.getLogger().atFine().log("Entity has no EffectControllerComponent, cannot apply speed effect.");
            return false;
        }

        // Get or create the appropriate speed effect
        RPGEntityEffect effect = getOrCreateEffect(speedMultiplier);
        if (effect == null) {
            plugin.getLogger().atWarning().log("Failed to get speed effect for multiplier: " + speedMultiplier);
            return false;
        }

        // Check if effect is registered (has valid index in asset map)
        int effectIndex = EntityEffect.getAssetMap().getIndex(effect.getId());
        if (effectIndex == Integer.MIN_VALUE) {
            // Effect not yet registered, register it now
            plugin.getLogger().atFine().log("Speed effect not registered, registering now: " + effect.getId());
            registerPendingEffects();

            // Check again
            effectIndex = EntityEffect.getAssetMap().getIndex(effect.getId());
            if (effectIndex == Integer.MIN_VALUE) {
                plugin.getLogger().atSevere().log("Failed to register speed effect: " + effect.getId());
                return false;
            }
        }

        // Apply the effect (infinite duration)
        boolean success = effectController.addEffect(ref, effect, accessor);

        if (success) {
            plugin.getLogger().atFine().log("Applied speed effect " + effect.getId() +
                " to entity (multiplier: " + speedMultiplier + ")");
        } else {
            plugin.getLogger().atWarning().log("Failed to apply speed effect to entity.");
        }

        return success;
    }

    /**
     * Removes all RPG speed effects from an entity.
     *
     * @param ref      Entity reference
     * @param accessor Component accessor
     */
    public void removeSpeedEffects(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull ComponentAccessor<EntityStore> accessor) {

        EffectControllerComponent effectController = accessor.getComponent(
            ref, EffectControllerComponent.getComponentType()
        );

        if (effectController == null) {
            return;
        }

        // Remove all RPG speed effects
        for (RPGEntityEffect effect : effectCache.values()) {
            int effectIndex = EntityEffect.getAssetMap().getIndex(effect.getId());
            if (effectIndex != Integer.MIN_VALUE) {
                effectController.removeEffect(ref, effectIndex, accessor);
            }
        }
    }

    /**
     * Gets the number of cached speed effects.
     *
     * @return Cache size
     */
    public int getCacheSize() {
        return effectCache.size();
    }

    /**
     * Checks if the manager has been initialized.
     *
     * @return true if initialized
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Shuts down the speed effect manager.
     */
    public void shutdown() {
        effectCache.clear();
        synchronized (pendingEffects) {
            pendingEffects.clear();
        }
        initialized = false;
        plugin.getLogger().atInfo().log("Mob speed effect system shut down.");
    }
}
