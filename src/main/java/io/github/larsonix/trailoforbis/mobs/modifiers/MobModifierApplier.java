package io.github.larsonix.trailoforbis.mobs.modifiers;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.entity.component.EntityScaleComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.mobs.classification.RPGMobClass;
import io.github.larsonix.trailoforbis.mobs.model.MobStats;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.logging.Level;

/**
 * Applies modifier effects to a mob entity at spawn time.
 *
 * <p>Responsible for three things:
 * <ol>
 *   <li><b>Stats</b>: Calls {@link MobStats#withModifiers(List)} to bake stat bonuses into the immutable record</li>
 *   <li><b>Visuals</b>: Applies per-modifier EntityEffects (tint, VFX, particles) via {@link MobModifierEffectRegistry}</li>
 *   <li><b>Scale</b>: Sets {@link EntityScaleComponent} based on mob tier (Elite 1.15x, Boss 1.3x, Elite Boss 1.4x)</li>
 * </ol>
 *
 * <p>Nameplate text is handled by MobScalingSystem using {@link #formatNameplate(int, RPGMobClass, List, MobModifierConfig)}.
 */
public class MobModifierApplier {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final MobModifierConfig config;
    private final MobModifierEffectRegistry effectRegistry;

    public MobModifierApplier(
            @Nonnull MobModifierConfig config,
            @Nonnull MobModifierEffectRegistry effectRegistry) {
        this.config = config;
        this.effectRegistry = effectRegistry;
    }

    /**
     * Applies modifier stat bonuses to a MobStats record.
     *
     * @param stats     Base stats (already has tier multiplier applied)
     * @param modifiers Active modifiers
     * @return New MobStats with modifier bonuses applied
     */
    @Nonnull
    public MobStats applyStats(@Nonnull MobStats stats, @Nonnull List<ModifierType> modifiers) {
        if (modifiers.isEmpty()) return stats;
        return stats.withModifiers(modifiers);
    }

    /**
     * Applies visual effects and scale to the entity.
     *
     * <p>Uses the Holder for addComponent (scale), and entityRef + store for
     * EffectControllerComponent-based visual effects (tint, VFX).
     *
     * @param holder    Entity holder (for component access)
     * @param modifiers Active modifiers
     * @param tier      Rarity tier name: "elite", "boss", or "elite_boss"
     * @param entityRef Entity reference (from NPCEntity.getReference())
     * @param store     Entity store (as ComponentAccessor)
     */
    public void applyVisuals(
            @Nonnull Holder<EntityStore> holder,
            @Nonnull List<ModifierType> modifiers,
            @Nonnull String tier,
            @Nonnull Ref<EntityStore> entityRef,
            @Nonnull Store<EntityStore> store) {

        // 1. Apply EntityScaleComponent for tier-based size
        double scale = config.getVisuals().getScaleForTier(tier);
        if (scale > 1.0) {
            holder.addComponent(
                EntityScaleComponent.getComponentType(),
                new EntityScaleComponent((float) scale)
            );
            LOGGER.atFine().log("Applied scale %.2f for tier %s", scale, tier);
        }

        // 2. Apply per-modifier visual effects (tint + VFX)
        if (entityRef.isValid()) {
            for (ModifierType mod : modifiers) {
                boolean applied = effectRegistry.applyEffect(entityRef, mod, store);
                if (!applied) {
                    LOGGER.atFine().log("Could not apply visual effect for modifier: %s", mod.name());
                }
            }
        }
    }

    /**
     * Formats nameplate text including modifier names.
     *
     * <p>Format: "{prefix}{ModName} Lv{level}" for single modifier,
     * "{prefix}{ModName} Lv{level} the {ModName}" for two modifiers, etc.
     *
     * @param level          Mob level
     * @param classification Mob classification
     * @param modifiers      Active modifiers
     * @param modConfig      Config for nameplate prefixes
     * @return Formatted nameplate text
     */
    @Nonnull
    public static String formatNameplate(
            int level,
            @Nonnull RPGMobClass classification,
            @Nonnull List<ModifierType> modifiers,
            @Nonnull MobModifierConfig modConfig) {

        if (modifiers.isEmpty()) {
            // No modifiers — use existing format
            return formatBasicNameplate(level, classification);
        }

        StringBuilder sb = new StringBuilder();

        // Tier prefix (stars)
        String tier = getTierKey(classification, modifiers.size());
        String prefix = modConfig.getVisuals().getNameplatePrefixForTier(tier);
        sb.append(prefix);

        // First modifier as prefix
        sb.append(modifiers.get(0).getDisplayName()).append(' ');

        // Level
        sb.append("Lv").append(level);

        // Additional modifiers as suffixes
        if (modifiers.size() >= 2) {
            sb.append(" the ").append(modifiers.get(1).getDisplayName());
        }
        if (modifiers.size() >= 3) {
            sb.append(' ').append(modifiers.get(2).getDisplayName());
        }

        return sb.toString();
    }

    /**
     * Formats a basic nameplate without modifiers (fallback).
     */
    @Nonnull
    private static String formatBasicNameplate(int level, @Nonnull RPGMobClass classification) {
        StringBuilder sb = new StringBuilder();
        if (classification == RPGMobClass.ELITE) sb.append("[Elite] ");
        else if (classification == RPGMobClass.BOSS) sb.append("[Boss] ");
        sb.append("Lv").append(level);
        return sb.toString();
    }

    /**
     * Determines the tier key based on classification and modifier count.
     */
    @Nonnull
    private static String getTierKey(@Nonnull RPGMobClass classification, int modifierCount) {
        if (classification == RPGMobClass.BOSS && modifierCount >= 3) return "elite_boss";
        if (classification == RPGMobClass.BOSS) return "boss";
        return "elite";
    }
}
