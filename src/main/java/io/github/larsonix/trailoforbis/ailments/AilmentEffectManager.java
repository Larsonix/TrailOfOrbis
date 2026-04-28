package io.github.larsonix.trailoforbis.ailments;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.logger.HytaleLogger;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.mobs.speed.RPGApplicationEffects;
import io.github.larsonix.trailoforbis.mobs.speed.RPGEntityEffect;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages ailment visual effects using Hytale's native EntityEffect system.
 *
 * <p>When an ailment is applied to an entity, this manager creates and applies
 * a native EntityEffect with the appropriate visual feedback (tints, particles,
 * screen effects, sounds, VFX). Effects are configured to match vanilla Hytale
 * status effects where applicable.
 *
 * <p><b>Supported ailments:</b>
 * <ul>
 *   <li><b>BURN</b> - Fire tints, fire particles, burn VFX, fire screen overlay, burn sounds</li>
 *   <li><b>FREEZE</b> - Ice tints, snow particles, freeze VFX, snow screen overlay, speed reduction</li>
 *   <li><b>SHOCK</b> - Electric yellow tints, lightning particles</li>
 *   <li><b>POISON</b> - Green/black tints, poison particles, poison sounds</li>
 * </ul>
 *
 * <p><b>Duration strategy:</b>
 * Native effect duration is set to match the ailment's total duration. Effects auto-expire.
 * OVERWRITE overlap behavior handles refresh for Burn/Freeze/Shock. For Poison (stacking),
 * we use OVERWRITE and set duration to the longest remaining stack.
 *
 * <p><b>Freeze speed:</b>
 * Freeze effects are cached by slow percentage (rounded to 5%) because each distinct
 * speed multiplier requires a separate EntityEffect instance.
 *
 * @see AilmentTracker
 * @see io.github.larsonix.trailoforbis.mobs.speed.MobSpeedEffectManager
 */
public class AilmentEffectManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Effect ID prefix for ailment visual effects */
    private static final String EFFECT_PREFIX = "rpg_ailment_";

    /** Effect ID prefix for freeze effects (includes slow percentage) */
    private static final String FREEZE_EFFECT_PREFIX = "rpg_ailment_freeze_";

    /** Asset pack key for registering effects */
    private static final String PACK_KEY = "trailoforbis";

    private final TrailOfOrbis plugin;

    /** Pre-created burn effect (single shared instance) */
    private RPGEntityEffect burnEffect;

    /** Pre-created shock effect (single shared instance) */
    private RPGEntityEffect shockEffect;

    /** Pre-created poison effect (single shared instance) */
    private RPGEntityEffect poisonEffect;

    /**
     * Cache of freeze effects by slow percentage.
     * Key: slow percentage rounded to nearest 5% (e.g., 5, 10, 15, 20, 25, 30)
     * Value: The freeze effect with that speed multiplier
     */
    private final ConcurrentHashMap<Integer, RPGEntityEffect> freezeEffectCache = new ConcurrentHashMap<>();

    /**
     * Tracks which ailment visuals are active per entity.
     * Key: Entity UUID
     * Value: Set of active ailment types with visuals
     */
    private final ConcurrentHashMap<UUID, EnumSet<AilmentType>> activeVisuals = new ConcurrentHashMap<>();

    /**
     * Tracks current freeze slow percentage per entity (for freeze effect lookup on removal).
     * Key: Entity UUID
     * Value: Current slow percentage being applied
     */
    private final ConcurrentHashMap<UUID, Integer> activeFreezePercent = new ConcurrentHashMap<>();

    /** Effects pending registration with asset store */
    private final List<EntityEffect> pendingEffects = new ArrayList<>();

    /** Whether the manager has been initialized */
    private boolean initialized = false;

    /** Creates a new ailment effect manager. */
    public AilmentEffectManager(@Nonnull TrailOfOrbis plugin) {
        this.plugin = plugin;
    }

    /**
     * Initializes the ailment effect system.
     *
     * <p>Creates effect definitions for all 4 ailment types and pre-creates
     * common freeze slow percentages. All effects are registered with Hytale's
     * asset store.
     */
    public void initialize() {
        if (initialized) {
            LOGGER.atWarning().log("AilmentEffectManager already initialized!");
            return;
        }

        LOGGER.atInfo().log("Initializing ailment effect system...");

        // Create effects for each ailment type
        burnEffect = createBurnEffect();
        shockEffect = createShockEffect();
        poisonEffect = createPoisonEffect();

        // Pre-create common freeze slow percentages (5% increments up to 30%)
        for (int slowPercent = 5; slowPercent <= 30; slowPercent += 5) {
            getOrCreateFreezeEffect(slowPercent);
        }

        // Register all effects with asset store
        registerPendingEffects();

        initialized = true;
        LOGGER.atInfo().log("Ailment effect system initialized: burn, shock, poison + %d freeze effects.",
                freezeEffectCache.size());
    }

    // ==================== Effect Creation ====================

    /**
     * Creates the burn visual effect (matches vanilla Burn.json).
     *
     * <p>Visuals: fire tint (#100600 bottom, #cf2302 top), fire particles,
     * burn VFX, fire screen overlay, burn sounds.
     */
    private RPGEntityEffect createBurnEffect() {
        String effectId = EFFECT_PREFIX + "burn";

        RPGApplicationEffects appEffects = RPGApplicationEffects.create()
                .withTint(
                        RPGApplicationEffects.colorFromHex("#100600"),
                        RPGApplicationEffects.colorFromHex("#cf2302"))
                .withParticles(RPGApplicationEffects.particle("Effect_Fire"))
                .withScreenEffect("ScreenEffects/Fire.png")
                .withModelVFX("Burn")
                .withSounds("SFX_Effect_Burn_World", "SFX_Effect_Burn_Local");
        appEffects.resolveSoundIndices();

        RPGEntityEffect effect = new RPGEntityEffect(effectId);
        effect.setApplicationEffects(appEffects);
        effect.setInfinite(false);
        effect.setDuration(4.0f); // Default, overridden per-application
        effect.setOverlapBehavior(OverlapBehavior.OVERWRITE);
        effect.setDebuff(true);
        effect.setStatusEffectIcon("UI/StatusEffects/Burn.png");
        effect.setDeathMessageKey("server.general.deathCause.burn");

        synchronized (pendingEffects) {
            pendingEffects.add(effect);
        }

        LOGGER.atFine().log("Created burn ailment effect: %s", effectId);
        return effect;
    }

    /**
     * Creates the shock visual effect (custom electric theme).
     *
     * <p>Visuals: electric yellow tint (#1a1200 bottom, #ffee44 top),
     * lightning sword particle effect. No screen effect or VFX (shock
     * doesn't impair vision).
     */
    private RPGEntityEffect createShockEffect() {
        String effectId = EFFECT_PREFIX + "shock";

        RPGApplicationEffects appEffects = RPGApplicationEffects.create()
                .withTint(
                        RPGApplicationEffects.colorFromHex("#1a1200"),
                        RPGApplicationEffects.colorFromHex("#ffee44"))
                .withParticles(RPGApplicationEffects.particle("Weapon/Lightning_Sword/Lightning_Sword"));

        RPGEntityEffect effect = new RPGEntityEffect(effectId);
        effect.setApplicationEffects(appEffects);
        effect.setInfinite(false);
        effect.setDuration(2.0f); // Default, overridden per-application
        effect.setOverlapBehavior(OverlapBehavior.OVERWRITE);
        effect.setDebuff(true);

        synchronized (pendingEffects) {
            pendingEffects.add(effect);
        }

        LOGGER.atFine().log("Created shock ailment effect: %s", effectId);
        return effect;
    }

    /**
     * Creates the poison visual effect (matches vanilla Poison.json visuals).
     *
     * <p>Visuals: green/black tint (#000000 bottom, #008000 top),
     * poison particles, poison sounds. No screen effect (vanilla doesn't have one).
     *
     * <p>Uses OVERWRITE overlap (not EXTEND like vanilla) because our poison
     * system manages stacks independently. The native visual is just on/off
     * while any stack exists.
     */
    private RPGEntityEffect createPoisonEffect() {
        String effectId = EFFECT_PREFIX + "poison";

        RPGApplicationEffects appEffects = RPGApplicationEffects.create()
                .withTint(
                        RPGApplicationEffects.colorFromHex("#000000"),
                        RPGApplicationEffects.colorFromHex("#008000"))
                .withParticles(RPGApplicationEffects.particle("Effect_Poison"))
                .withSounds("SFX_Effect_Poison_World", "SFX_Effect_Poison_Local");
        appEffects.resolveSoundIndices();

        RPGEntityEffect effect = new RPGEntityEffect(effectId);
        effect.setApplicationEffects(appEffects);
        effect.setInfinite(false);
        effect.setDuration(5.0f); // Default, overridden per-application
        effect.setOverlapBehavior(OverlapBehavior.OVERWRITE);
        effect.setDebuff(true);
        effect.setStatusEffectIcon("UI/StatusEffects/Poison.png");
        effect.setDeathMessageKey("server.general.deathCause.poison");

        synchronized (pendingEffects) {
            pendingEffects.add(effect);
        }

        LOGGER.atFine().log("Created poison ailment effect: %s", effectId);
        return effect;
    }

    /**
     * Creates a freeze effect with speed reduction and visuals (matches vanilla Freeze.json + Slow.json).
     *
     * <p>Visuals: ice tint (#80ecff bottom, #da72ff top), snow particles,
     * freeze VFX, snow screen overlay. Speed is set per slow percentage.
     *
     */
    private RPGEntityEffect createFreezeEffect(int slowPercent) {
        String effectId = FREEZE_EFFECT_PREFIX + slowPercent;
        float speedMultiplier = 1.0f - (slowPercent / 100.0f);

        RPGApplicationEffects appEffects = RPGApplicationEffects.create()
                .withTint(
                        RPGApplicationEffects.colorFromHex("#80ecff"),
                        RPGApplicationEffects.colorFromHex("#da72ff"))
                .withParticles(
                        RPGApplicationEffects.particle("Effect_Snow"),
                        RPGApplicationEffects.particle("Effect_Snow_Impact"))
                .withScreenEffect("ScreenEffects/Snow.png")
                .withModelVFX("Freeze")
                .withSpeed(speedMultiplier);

        RPGEntityEffect effect = new RPGEntityEffect(effectId);
        effect.setApplicationEffects(appEffects);
        effect.setInfinite(false);
        effect.setDuration(3.0f); // Default, overridden per-application
        effect.setOverlapBehavior(OverlapBehavior.OVERWRITE);

        synchronized (pendingEffects) {
            pendingEffects.add(effect);
        }

        LOGGER.atFine().log("Created freeze effect: %s (slow: %d%%, multiplier: %.2f)",
                effectId, slowPercent, speedMultiplier);
        return effect;
    }

    // ==================== Effect Application ====================

    /**
     * Applies ailment visual effects to an entity.
     *
     * <p>Called from {@link io.github.larsonix.trailoforbis.combat.ailments.CombatAilmentApplicator}
     * when an ailment is successfully applied. Creates the appropriate native EntityEffect
     * with duration matching the ailment state.
     *
     * @param ailmentState The applied ailment state (contains type, duration, magnitude)
     */
    public void applyAilmentVisual(
            @Nonnull Ref<EntityStore> entityRef,
            @Nonnull UUID entityUuid,
            @Nonnull AilmentState ailmentState,
            @Nonnull ComponentAccessor<EntityStore> accessor
    ) {
        if (!entityRef.isValid()) {
            return;
        }

        EffectControllerComponent effectController = accessor.getComponent(
                entityRef, EffectControllerComponent.getComponentType()
        );
        if (effectController == null) {
            LOGGER.atFine().log("Entity %s has no EffectControllerComponent, skipping visual.",
                    entityUuid.toString().substring(0, 8));
            return;
        }

        AilmentType type = ailmentState.type();
        float duration = ailmentState.totalDuration();

        RPGEntityEffect effect = getEffectForAilment(type, ailmentState);
        if (effect == null) {
            LOGGER.atWarning().log("No effect found for ailment type: %s", type.name());
            return;
        }

        // Ensure effect is registered in asset store
        int effectIndex = EntityEffect.getAssetMap().getIndex(effect.getId());
        if (effectIndex == Integer.MIN_VALUE) {
            registerPendingEffects();
            effectIndex = EntityEffect.getAssetMap().getIndex(effect.getId());
            if (effectIndex == Integer.MIN_VALUE) {
                LOGGER.atSevere().log("Failed to register ailment effect: %s", effect.getId());
                return;
            }
        }

        // Apply effect with explicit duration and OVERWRITE behavior
        boolean success = effectController.addEffect(
                entityRef, effect, duration, OverlapBehavior.OVERWRITE, accessor);

        if (success) {
            // Track active visuals
            activeVisuals.computeIfAbsent(entityUuid, k -> EnumSet.noneOf(AilmentType.class)).add(type);

            if (type == AilmentType.FREEZE) {
                int slowPercent = roundFreezePercent(Math.round(ailmentState.magnitude()));
                activeFreezePercent.put(entityUuid, slowPercent);
            }

            LOGGER.atFine().log("Applied %s visual to %s (%.1fs duration)",
                    type.getDisplayName(), entityUuid.toString().substring(0, 8), duration);
        }
    }

    /**
     * Removes all ailment visuals for an entity.
     *
     * <p>Called when an entity dies, disconnects, or needs all effects cleared.
     * If entity ref is available and valid, actively removes effects from the
     * EffectControllerComponent. Otherwise just clears tracking.
     *
     * @param entityRef  Entity reference (may be null or invalid)
     * @param accessor   Component accessor (may be null)
     */
    public void removeAllVisuals(
            @Nullable Ref<EntityStore> entityRef,
            @Nonnull UUID entityUuid,
            @Nullable ComponentAccessor<EntityStore> accessor
    ) {
        EnumSet<AilmentType> types = activeVisuals.remove(entityUuid);
        Integer freezePercent = activeFreezePercent.remove(entityUuid);

        if (types == null || types.isEmpty()) {
            return;
        }

        // If we have a valid entity ref and accessor, actively remove effects
        if (entityRef != null && entityRef.isValid() && accessor != null) {
            EffectControllerComponent effectController = accessor.getComponent(
                    entityRef, EffectControllerComponent.getComponentType()
            );
            if (effectController != null) {
                for (AilmentType type : types) {
                    RPGEntityEffect effect = getStoredEffect(type, freezePercent);
                    if (effect != null) {
                        int effectIndex = EntityEffect.getAssetMap().getIndex(effect.getId());
                        if (effectIndex != Integer.MIN_VALUE) {
                            effectController.removeEffect(entityRef, effectIndex, accessor);
                        }
                    }
                }
                LOGGER.atFine().log("Removed %d ailment visuals from %s",
                        types.size(), entityUuid.toString().substring(0, 8));
            }
        }
    }

    // ==================== Freeze Effect Access (Backward Compatibility) ====================

    /**
     * @param slowPercent Slow percentage (5-30)
     * @return The freeze effect, or null if creation failed
     */
    @Nullable
    public RPGEntityEffect getOrCreateFreezeEffect(int slowPercent) {
        int roundedPercent = roundFreezePercent(slowPercent);
        return freezeEffectCache.computeIfAbsent(roundedPercent, this::createFreezeEffect);
    }

    /**
     * @param slowMagnitude Slow magnitude from ailment (0-30)
     */
    @Nullable
    public RPGEntityEffect getOrCreateFreezeEffectForMagnitude(float slowMagnitude) {
        int slowPercent = Math.round(slowMagnitude);
        return getOrCreateFreezeEffect(slowPercent);
    }

    /**
     * Applies a freeze slow effect to an entity (backward-compatible method).
     *
     * @param slowPercent  Slow percentage (5-30)
     * @param durationSec  Effect duration in seconds
     * @return true if effect was applied
     */
    public boolean applyFreezeEffect(
            @Nonnull Ref<EntityStore> entityRef,
            @Nonnull UUID entityUuid,
            float slowPercent,
            float durationSec,
            @Nonnull ComponentAccessor<EntityStore> accessor
    ) {
        if (!entityRef.isValid()) {
            return false;
        }

        EffectControllerComponent effectController = accessor.getComponent(
                entityRef, EffectControllerComponent.getComponentType()
        );
        if (effectController == null) {
            LOGGER.atFine().log("Entity has no EffectControllerComponent, cannot apply freeze effect.");
            return false;
        }

        int roundedPercent = roundFreezePercent(Math.round(slowPercent));

        RPGEntityEffect effect = getOrCreateFreezeEffect(roundedPercent);
        if (effect == null) {
            LOGGER.atWarning().log("Failed to get freeze effect for slow: %d%%", roundedPercent);
            return false;
        }

        // Ensure effect is registered
        int effectIndex = EntityEffect.getAssetMap().getIndex(effect.getId());
        if (effectIndex == Integer.MIN_VALUE) {
            registerPendingEffects();
            effectIndex = EntityEffect.getAssetMap().getIndex(effect.getId());
            if (effectIndex == Integer.MIN_VALUE) {
                LOGGER.atSevere().log("Failed to register freeze effect: %s", effect.getId());
                return false;
            }
        }

        boolean success = effectController.addEffect(
                entityRef, effect, durationSec, OverlapBehavior.OVERWRITE, accessor);

        if (success) {
            activeVisuals.computeIfAbsent(entityUuid, k -> EnumSet.noneOf(AilmentType.class))
                    .add(AilmentType.FREEZE);
            activeFreezePercent.put(entityUuid, roundedPercent);
            LOGGER.atFine().log("Applied freeze slow %d%% for %.1fs to %s",
                    roundedPercent, durationSec, entityUuid.toString().substring(0, 8));
        }

        return success;
    }

    /** Removes freeze effect from an entity. */
    public void removeFreezeEffect(
            @Nonnull Ref<EntityStore> entityRef,
            @Nonnull UUID entityUuid,
            @Nonnull ComponentAccessor<EntityStore> accessor
    ) {
        Integer slowPercent = activeFreezePercent.remove(entityUuid);
        if (slowPercent == null) {
            return;
        }

        // Remove from active visuals tracking
        EnumSet<AilmentType> types = activeVisuals.get(entityUuid);
        if (types != null) {
            types.remove(AilmentType.FREEZE);
            if (types.isEmpty()) {
                activeVisuals.remove(entityUuid);
            }
        }

        if (!entityRef.isValid()) {
            return;
        }

        EffectControllerComponent effectController = accessor.getComponent(
                entityRef, EffectControllerComponent.getComponentType()
        );
        if (effectController == null) {
            return;
        }

        RPGEntityEffect effect = freezeEffectCache.get(slowPercent);
        if (effect != null) {
            int effectIndex = EntityEffect.getAssetMap().getIndex(effect.getId());
            if (effectIndex != Integer.MIN_VALUE) {
                effectController.removeEffect(entityRef, effectIndex, accessor);
                LOGGER.atFine().log("Removed freeze effect from %s", entityUuid.toString().substring(0, 8));
            }
        }
    }

    // ==================== Effect Lookup ====================

    /** Gets the appropriate effect for an ailment type and state. */
    @Nullable
    private RPGEntityEffect getEffectForAilment(@Nonnull AilmentType type, @Nonnull AilmentState ailmentState) {
        return switch (type) {
            case BURN -> burnEffect;
            case SHOCK -> shockEffect;
            case POISON -> poisonEffect;
            case FREEZE -> {
                int slowPercent = roundFreezePercent(Math.round(ailmentState.magnitude()));
                yield getOrCreateFreezeEffect(slowPercent);
            }
        };
    }

    /**
     * Gets a stored effect by ailment type (for removal).
     *
     * @param freezePercent Freeze slow percentage (only used for FREEZE type)
     */
    @Nullable
    private RPGEntityEffect getStoredEffect(@Nonnull AilmentType type, @Nullable Integer freezePercent) {
        return switch (type) {
            case BURN -> burnEffect;
            case SHOCK -> shockEffect;
            case POISON -> poisonEffect;
            case FREEZE -> freezePercent != null ? freezeEffectCache.get(freezePercent) : null;
        };
    }

    /**
     * @return Rounded percentage (5, 10, 15, 20, 25, or 30)
     */
    private int roundFreezePercent(int slowPercent) {
        int clamped = Math.max(5, Math.min(30, slowPercent));
        return ((clamped + 2) / 5) * 5;
    }

    // ==================== Registration ====================

    /**
     * Registers pending effects with Hytale's asset store.
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
            EntityEffect.getAssetStore().loadAssets(PACK_KEY, toRegister);
            LOGGER.atInfo().log("Registered %d ailment effects with asset store.", toRegister.size());
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to register ailment effects: %s", e.getMessage());
        }
    }

    // ==================== Tracking & Queries ====================

    /**
     * Cleans up tracking for an entity (on death/disconnect).
     *
     * <p>Does NOT actively remove effects from the entity -- native effects
     * auto-expire, and entity removal clears them. This just clears our
     * internal tracking maps.
     */
    public void cleanup(@Nonnull UUID entityUuid) {
        activeVisuals.remove(entityUuid);
        activeFreezePercent.remove(entityUuid);
    }

    /** @return true if freeze visual is active */
    public boolean hasFreezeEffect(@Nonnull UUID entityUuid) {
        EnumSet<AilmentType> types = activeVisuals.get(entityUuid);
        return types != null && types.contains(AilmentType.FREEZE);
    }

    /** @return Slow percentage, or 0 if not frozen */
    public int getCurrentSlowPercent(@Nonnull UUID entityUuid) {
        Integer percent = activeFreezePercent.get(entityUuid);
        return percent != null ? percent : 0;
    }

    /** @return true if any ailment visual is active */
    public boolean hasAnyVisual(@Nonnull UUID entityUuid) {
        EnumSet<AilmentType> types = activeVisuals.get(entityUuid);
        return types != null && !types.isEmpty();
    }

    /** Gets the number of entities with active ailment visuals. */
    public int getActiveEffectCount() {
        return activeVisuals.size();
    }

    /** Gets the freeze effect cache size. */
    public int getCacheSize() {
        return freezeEffectCache.size();
    }

    /** @return true if initialized */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Shuts down the manager.
     */
    public void shutdown() {
        burnEffect = null;
        shockEffect = null;
        poisonEffect = null;
        freezeEffectCache.clear();
        activeVisuals.clear();
        activeFreezePercent.clear();
        synchronized (pendingEffects) {
            pendingEffects.clear();
        }
        initialized = false;
        LOGGER.atInfo().log("Ailment effect system shut down.");
    }
}
