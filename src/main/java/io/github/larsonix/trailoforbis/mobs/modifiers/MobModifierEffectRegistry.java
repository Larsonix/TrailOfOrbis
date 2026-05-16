package io.github.larsonix.trailoforbis.mobs.modifiers;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.Color;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.RemovalBehavior;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.mobs.speed.RPGApplicationEffects;
import io.github.larsonix.trailoforbis.mobs.speed.RPGEntityEffect;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Pre-creates and caches visual EntityEffects for each modifier type.
 *
 * <p>Follows the {@link io.github.larsonix.trailoforbis.mobs.speed.MobSpeedEffectManager} pattern:
 * effects are created at initialization, batch-registered with the asset store,
 * and applied to entities on spawn via {@link EffectControllerComponent}.
 *
 * <p>Each modifier gets a unique effect with its tint, ModelVFX, and (for Swift)
 * speed multiplier. Multiple effects stack on one entity — a Blazing+Enraged boss
 * has both fire tint AND red tint effects simultaneously.
 */
public class MobModifierEffectRegistry {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String EFFECT_ID_PREFIX = "rpg_mod_";
    private static final String PACK_KEY = "trailoforbis";

    // Runtime behavior effect IDs — registered here during init, looked up by MobModifierTickSystem.
    // CRITICAL: All loadAssets() calls MUST happen during plugin init, never during a world tick.
    // Calling loadAssets() from within a TickingSystem.tick() deadlocks the world thread
    // with Hytale's asset/network infrastructure (see docs/reference/client-hang-mob-modifiers.md).
    public static final String ENRAGE_EFFECT_ID = "rpg_mod_enrage_active";
    public static final String FROST_AURA_SLOW_ID = "rpg_mod_frost_aura_slow";
    public static final String FROZEN_SLOW_ID = "rpg_mod_frozen_slow";
    public static final String PACK_LEADER_SPEED_ID = "rpg_mod_pack_leader_speed";

    private final TrailOfOrbis plugin;
    private final MobModifierConfig config;
    private final Map<ModifierType, RPGEntityEffect> effectCache = new EnumMap<>(ModifierType.class);
    private final List<RPGEntityEffect> pendingEffects = new ArrayList<>();
    private boolean initialized = false;

    public MobModifierEffectRegistry(@Nonnull TrailOfOrbis plugin, @Nonnull MobModifierConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    /**
     * Creates all modifier effects and registers them with the asset store.
     * Called once during MobModifierManager.initialize().
     */
    public void initialize() {
        if (initialized) return;

        for (ModifierType type : ModifierType.values()) {
            RPGEntityEffect effect = createModifierEffect(type);
            if (effect != null) {
                effectCache.put(type, effect);
            }
        }

        createRuntimeEffects();

        registerPendingEffects();

        initialized = true;
        LOGGER.at(Level.INFO).log("MobModifierEffectRegistry initialized with %d effects", effectCache.size());
    }

    @Nullable
    private RPGEntityEffect createModifierEffect(@Nonnull ModifierType type) {
        String effectId = EFFECT_ID_PREFIX + type.configKey();

        MobModifierConfig.ModifierSettings settings = config.getModifierSettings(type);
        String tintBottomHex = settings.getTint_bottom() != null ? settings.getTint_bottom() : type.getTintBottom();
        String tintTopHex = settings.getTint_top() != null ? settings.getTint_top() : type.getTintTop();
        String modelVfx = settings.getModel_vfx() != null ? settings.getModel_vfx() : type.getModelVfxId();

        RPGApplicationEffects appEffects = RPGApplicationEffects.create();

        Color bottomColor = RPGApplicationEffects.colorFromHex(tintBottomHex);
        Color topColor = RPGApplicationEffects.colorFromHex(tintTopHex);
        if (bottomColor != null && topColor != null) {
            appEffects.withTint(bottomColor, topColor);
        }

        if (modelVfx != null && !modelVfx.isEmpty()) {
            appEffects.withModelVFX(modelVfx);
        }

        if (type == ModifierType.SWIFT) {
            float speedBonus = (float) settings.getSpeed_bonus_percent();
            if (speedBonus <= 0) speedBonus = (float) type.getStatBonus().speedPercent();
            appEffects.withSpeed(1.0f + speedBonus);
        }

        if (type == ModifierType.RESOLUTE) {
            float kbMult = (float) settings.getKnockback_multiplier();
            appEffects.withKnockback(kbMult);
        }

        RPGEntityEffect effect = new RPGEntityEffect(effectId);
        effect.setApplicationEffects(appEffects);
        effect.setInfinite(true);
        effect.setDuration(0.0f);
        effect.setName(null);
        effect.setStatusEffectIcon(null);
        effect.setDebuff(false);
        effect.setOverlapBehavior(OverlapBehavior.IGNORE);
        effect.setRemovalBehavior(RemovalBehavior.COMPLETE);

        synchronized (pendingEffects) {
            pendingEffects.add(effect);
        }

        LOGGER.atFine().log("Created modifier effect: %s (tint=%s/%s, vfx=%s)",
            effectId, tintBottomHex, tintTopHex, modelVfx);

        return effect;
    }

    /**
     * Creates runtime behavior effects used by {@link MobModifierTickSystem}:
     * enrage activation, frost/frozen slow auras, and pack leader speed buff.
     *
     * <p>These were originally created lazily on first tick, but that called
     * {@code loadAssets()} from within a {@code TickingSystem.tick()}, deadlocking
     * the world thread with Hytale's asset infrastructure.
     */
    private void createRuntimeEffects() {
        // Enrage active: permanent speed + red tint (applied once at HP threshold)
        MobModifierConfig.ModifierSettings enrageSettings = config.getModifierSettings(ModifierType.ENRAGED);
        RPGApplicationEffects enrageApp = RPGApplicationEffects.create();
        enrageApp.withSpeed(1.0f + (float) enrageSettings.getSpeed_bonus());
        enrageApp.withTint(
            RPGApplicationEffects.colorFromHex("#CC0000"),
            RPGApplicationEffects.colorFromHex("#FF2200")
        );
        addRuntimeEffect(ENRAGE_EFFECT_ID, enrageApp, true, 0.0f);

        // Frost Aura slow: short-duration, auto-expires between ticks
        MobModifierConfig.ModifierSettings frostSettings = config.getModifierSettings(ModifierType.FROST_AURA);
        RPGApplicationEffects frostApp = RPGApplicationEffects.create();
        frostApp.withSpeed(1.0f - (float) frostSettings.getSlow_percent());
        addRuntimeEffect(FROST_AURA_SLOW_ID, frostApp, false, 0.8f,
                "Icons/ItemsGenerated/Ingredient_Ice_Essence.png", true);

        // Frozen proximity slow: same pattern as frost aura
        MobModifierConfig.ModifierSettings frozenSettings = config.getModifierSettings(ModifierType.FROZEN);
        RPGApplicationEffects frozenApp = RPGApplicationEffects.create();
        frozenApp.withSpeed(1.0f - (float) frozenSettings.getSlow_percent());
        addRuntimeEffect(FROZEN_SLOW_ID, frozenApp, false, 0.8f,
                "Icons/ItemsGenerated/Ingredient_Ice_Essence.png", true);

        // Pack Leader speed buff: short-duration buff on nearby mobs
        MobModifierConfig.ModifierSettings packSettings = config.getModifierSettings(ModifierType.PACK_LEADER);
        float packSpeed = (float) packSettings.getSpeed_bonus();
        if (packSpeed > 0) {
            RPGApplicationEffects packApp = RPGApplicationEffects.create();
            packApp.withSpeed(1.0f + packSpeed);
            addRuntimeEffect(PACK_LEADER_SPEED_ID, packApp, false, 0.8f);
        }
    }

    private void addRuntimeEffect(@Nonnull String id, @Nonnull RPGApplicationEffects appEffects,
                                  boolean infinite, float duration) {
        addRuntimeEffect(id, appEffects, infinite, duration, null, false);
    }

    private void addRuntimeEffect(@Nonnull String id, @Nonnull RPGApplicationEffects appEffects,
                                  boolean infinite, float duration,
                                  @Nullable String statusEffectIcon, boolean debuff) {
        // Effects with icons use OVERWRITE so the countdown ring resets each tick
        // (prevents icon blinking off at edge of range). Effects without icons
        // use IGNORE (no network cost for invisible re-applications).
        OverlapBehavior overlap = statusEffectIcon != null ? OverlapBehavior.OVERWRITE : OverlapBehavior.IGNORE;

        RPGEntityEffect effect = new RPGEntityEffect(id);
        effect.setApplicationEffects(appEffects);
        effect.setInfinite(infinite);
        effect.setDuration(duration);
        effect.setName(null);
        effect.setStatusEffectIcon(statusEffectIcon);
        effect.setDebuff(debuff);
        effect.setOverlapBehavior(overlap);
        effect.setRemovalBehavior(RemovalBehavior.COMPLETE);

        synchronized (pendingEffects) {
            pendingEffects.add(effect);
        }
    }

    private void registerPendingEffects() {
        List<EntityEffect> toRegister;
        synchronized (pendingEffects) {
            if (pendingEffects.isEmpty()) return;
            toRegister = new ArrayList<>(pendingEffects);
            pendingEffects.clear();
        }

        try {
            EntityEffect.getAssetStore().loadAssets(PACK_KEY, toRegister);
            LOGGER.at(Level.INFO).log("Registered %d modifier effects with asset store", toRegister.size());
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to register modifier effects");
        }
    }

    public boolean applyEffect(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull ModifierType type,
            @Nonnull ComponentAccessor<EntityStore> accessor) {

        RPGEntityEffect effect = effectCache.get(type);
        if (effect == null) {
            LOGGER.atWarning().log("No effect cached for modifier: %s", type.name());
            return false;
        }

        EffectControllerComponent effectController = accessor.getComponent(
            ref, EffectControllerComponent.getComponentType()
        );
        if (effectController == null) {
            LOGGER.at(Level.WARNING).log("[MobModifier] Entity has no EffectControllerComponent for %s", type.name());
            return false;
        }

        int effectIndex = EntityEffect.getAssetMap().getIndex(effect.getId());
        boolean success = effectController.addEffect(ref, effect, accessor);
        LOGGER.at(Level.INFO).log("[MobModifier] applyEffect %s: index=%d, success=%b, activeEffects=%d",
            effect.getId(), effectIndex, success, effectController.getActiveEffects().size());
        if (success) {
            LOGGER.atFine().log("Applied modifier effect %s to entity", effect.getId());
        }
        return success;
    }

    public void shutdown() {
        effectCache.clear();
        synchronized (pendingEffects) {
            pendingEffects.clear();
        }
        initialized = false;
        LOGGER.at(Level.INFO).log("MobModifierEffectRegistry shut down");
    }

    public boolean isInitialized() {
        return initialized;
    }
}
