package io.github.larsonix.trailoforbis.combat.environmental;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.combat.AttackType;
import io.github.larsonix.trailoforbis.combat.DamageBreakdown;
import io.github.larsonix.trailoforbis.combat.DamageType;
import io.github.larsonix.trailoforbis.combat.RPGDamageCalculator;
import io.github.larsonix.trailoforbis.combat.RPGDamageSystem;
import io.github.larsonix.trailoforbis.combat.deathrecap.CombatSnapshot;
import io.github.larsonix.trailoforbis.combat.deathrecap.DeathRecapRecorder;
import io.github.larsonix.trailoforbis.combat.detection.DamageTypeClassifier;
import io.github.larsonix.trailoforbis.combat.effects.CombatEffectContext;
import io.github.larsonix.trailoforbis.combat.effects.CombatEffectRegistry;
import io.github.larsonix.trailoforbis.combat.indicators.CombatIndicatorService;
import io.github.larsonix.trailoforbis.combat.recovery.CombatRecoveryProcessor;
import io.github.larsonix.trailoforbis.combat.resolution.CombatEntityResolver;
import io.github.larsonix.trailoforbis.combat.resolution.CombatStatsResolver;
import io.github.larsonix.trailoforbis.elemental.ElementType;
import io.github.larsonix.trailoforbis.elemental.ElementalCalculator;
import io.github.larsonix.trailoforbis.elemental.ElementalStats;
import io.github.larsonix.trailoforbis.mobs.component.MobScalingComponent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.ToIntBiFunction;
import java.util.logging.Level;

/**
 * Handles DOT (burn/poison) and environmental (lava/drowning/suffocation) damage.
 *
 * <p>These are independent damage pipelines that early-return from Phase 1 of the main
 * RPG damage pipeline. DOT damage uses elemental resistance with shock amplification;
 * environmental damage uses max HP percentage-based scaling.
 *
 * <p>Extracted from RPGDamageSystem to isolate these alternate pipelines from the main
 * 7-phase combat orchestrator.
 */
public class EnvironmentalDamageProcessor {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final TrailOfOrbis plugin;
    private final CombatStatsResolver statsResolver;
    private final DamageTypeClassifier classifier;
    private final CombatEntityResolver entityResolver;
    private final CombatIndicatorService indicatorService;
    private final DeathRecapRecorder deathRecapRecorder;
    private final CombatRecoveryProcessor recoveryProcessor;
    private final RPGDamageCalculator rpgCalculator;

    /** Callback to mark a mob as damaged (alert AI, update leash). Called on all non-lethal/lethal hits. */
    private final BiConsumer<Ref<EntityStore>, Store<EntityStore>> markMobDamaged;
    /** Callback to fire modifier death triggers (on lethal hits only). */
    private final BiConsumer<Ref<EntityStore>, Store<EntityStore>> fireModifierDeathTriggers;
    /** Resolves attacker level from store+damage (for armor formula in DOT). */
    private final ToIntBiFunction<Store<EntityStore>, Damage> resolveAttackerLevel;

    @Nullable
    private volatile CombatEffectRegistry combatEffectRegistry;

    public EnvironmentalDamageProcessor(
        @Nonnull TrailOfOrbis plugin,
        @Nonnull CombatStatsResolver statsResolver,
        @Nonnull DamageTypeClassifier classifier,
        @Nonnull CombatEntityResolver entityResolver,
        @Nonnull CombatIndicatorService indicatorService,
        @Nonnull DeathRecapRecorder deathRecapRecorder,
        @Nonnull CombatRecoveryProcessor recoveryProcessor,
        @Nonnull RPGDamageCalculator rpgCalculator,
        @Nonnull BiConsumer<Ref<EntityStore>, Store<EntityStore>> markMobDamaged,
        @Nonnull BiConsumer<Ref<EntityStore>, Store<EntityStore>> fireModifierDeathTriggers,
        @Nonnull ToIntBiFunction<Store<EntityStore>, Damage> resolveAttackerLevel
    ) {
        this.plugin = plugin;
        this.statsResolver = statsResolver;
        this.classifier = classifier;
        this.entityResolver = entityResolver;
        this.indicatorService = indicatorService;
        this.deathRecapRecorder = deathRecapRecorder;
        this.recoveryProcessor = recoveryProcessor;
        this.rpgCalculator = rpgCalculator;
        this.markMobDamaged = markMobDamaged;
        this.fireModifierDeathTriggers = fireModifierDeathTriggers;
        this.resolveAttackerLevel = resolveAttackerLevel;
    }

    public void setCombatEffectRegistry(@Nullable CombatEffectRegistry registry) {
        this.combatEffectRegistry = registry;
    }

    // ==================== DOT Handling ====================

    public void handleDOTDamage(
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Damage damage
    ) {
        float baseDamage = damage.getAmount();
        if (baseDamage <= 0 || damage.isCancelled()) {
            return;
        }

        Ref<EntityStore> defenderRef = archetypeChunk.getReferenceTo(index);
        if (defenderRef == null || !defenderRef.isValid()) {
            return;
        }

        EntityStatMap statMap = store.getComponent(defenderRef, EntityStatMap.getComponentType());
        if (statMap == null) {
            return;
        }

        int healthIndex = DefaultEntityStatTypes.getHealth();
        EntityStatValue healthStat = statMap.get(healthIndex);
        if (healthStat == null) {
            return;
        }
        float healthBefore = healthStat.get();

        ComputedStats defenderStats = statsResolver.getDefenderStats(index, archetypeChunk, store);
        ElementalStats defenderElemental = statsResolver.getDefenderElementalStats(index, archetypeChunk, store, defenderStats);
        DamageCause damageCause = DamageTypeClassifier.getDamageCause(damage);
        ElementType dotElement = classifier.getElementFromDOTCause(damageCause);

        int dotAttackerLevel = resolveAttackerLevel.applyAsInt(store, damage);
        DamageBreakdown breakdown = rpgCalculator.calculateDOT(
            baseDamage, defenderStats, defenderElemental, dotElement, dotAttackerLevel);

        float rpgDamage = breakdown.totalDamage();

        // Apply shock amplification: if target is shocked, DOT damage is increased
        UUID defenderUuid = entityResolver.getDefenderUuid(index, archetypeChunk, store);
        if (defenderUuid != null && plugin.getAilmentTracker() != null) {
            float shockAmp = plugin.getAilmentTracker().getShockDamageIncreasePercent(defenderUuid);
            if (shockAmp > 0) {
                float shockMult = 1f + shockAmp / 100f;
                rpgDamage *= shockMult;
                LOGGER.at(Level.FINE).log("DOT shock amp: %.1f%% -> damage %.1f -> %.1f",
                    shockAmp, breakdown.totalDamage(), rpgDamage);
            }
        }

        damage.putMetaObject(RPGDamageSystem.DAMAGE_TYPE, breakdown.damageType());
        damage.putMetaObject(RPGDamageSystem.WAS_CRITICAL, false);
        damage.putMetaObject(RPGDamageSystem.RPG_DAMAGE_VALUE, rpgDamage);
        damage.putMetaObject(RPGDamageSystem.ATTACK_TYPE, AttackType.UNKNOWN);
        damage.setAmount(0);

        // Only show combat text if damage is positive — fully resisted DOTs (0.0) are silent.
        // DOT uses sendCombatTextOnly() — floating numbers only, NO red screen flash.
        // Continuous ticking damage should not trigger the directional vignette every 0.5s.
        if (rpgDamage > 0f) {
            indicatorService.sendCombatTextOnly(store, defenderRef, damage, rpgDamage, breakdown, false);
        }
        damage.putMetaObject(RPGDamageSystem.INDICATORS_SENT, true);

        // Record DOT damage to death recap (so DOT kills appear in death recap timeline)
        if (rpgDamage > 0f && deathRecapRecorder.isAvailable()) {
            PlayerRef defenderPlayer = store.getComponent(defenderRef, PlayerRef.getComponentType());
            if (defenderPlayer != null) {
                // Resolve attacker info — uses the DOT source entity if still valid
                DeathRecapRecorder.AttackerInfo sourceInfo = deathRecapRecorder.getAttackerInfo(store, damage);

                // Build a display name that identifies this as a DOT hit
                String ailmentLabel = dotElement != null
                    ? DamageType.fromElement(dotElement).name().charAt(0)
                        + DamageType.fromElement(dotElement).name().substring(1).toLowerCase() + " DOT"
                    : "DOT";
                String displayName;
                if (!"Unknown".equals(sourceInfo.name()) && !"environment".equals(sourceInfo.type())) {
                    displayName = ailmentLabel + " (" + sourceInfo.name() + ")";
                } else {
                    displayName = ailmentLabel;
                }

                DamageType dotDamageType = DamageType.fromElement(dotElement);

                // Calculate effective resistance for display (uses canonical formula with cap + floor)
                float resistReduction = 0f;
                if (dotElement != null && defenderElemental != null) {
                    resistReduction = (float) ElementalCalculator.getEffectiveResistance(
                        defenderElemental.getResistance(dotElement));
                }

                CombatSnapshot dotSnapshot = CombatSnapshot.forDOT(
                    displayName, "dot", sourceInfo.level(), sourceInfo.mobClass(),
                    rpgDamage, baseDamage, dotDamageType, resistReduction,
                    healthStat.getMax(), healthBefore);
                plugin.getDeathRecapTracker().recordDamage(defenderPlayer.getUuid(), dotSnapshot);

                // Send DOT detail chat to defender if they have combat detail enabled
                float shockAmp = 0f;
                if (defenderUuid != null && plugin.getAilmentTracker() != null) {
                    shockAmp = plugin.getAilmentTracker().getShockDamageIncreasePercent(defenderUuid);
                }
                indicatorService.sendDOTDetailChat(store, defenderRef,
                    ailmentLabel, dotElement, sourceInfo.name(),
                    baseDamage, shockAmp, resistReduction, rpgDamage);
            }
        }

        // SHIELD_REGEN_ON_DOT: restore energy shield to the DOT applicator
        processShieldRegenOnDot(damage, rpgDamage, store);

        // Combat Effect Hooks: DoT recovery (burn leech, DoT heal, etc.)
        if (combatEffectRegistry != null && rpgDamage > 0) {
            UUID attackerUuid = entityResolver.getAttackerPlayerUuid(store, damage);
            if (attackerUuid != null) {
                // Build minimal context for DoT recovery
                Ref<EntityStore> atkRef = (damage.getSource() instanceof Damage.EntitySource es) ? es.getRef() : null;
                CombatEffectContext dotCtx = new CombatEffectContext(
                    attackerUuid, defenderUuid, null, defenderStats,
                    null, defenderElemental, breakdown, null,
                    AttackType.UNKNOWN, dotElement, rpgDamage, baseDamage,
                    healthBefore, healthStat.getMax(), false,
                    damage, store, defenderRef, atkRef, commandBuffer, -1f
                );
                float dotRecovery = combatEffectRegistry.runOnRecovery(attackerUuid, dotCtx);
                if (dotRecovery > 0) {
                    // Apply Health Recovery multiplier from DOT applicator's stats
                    ComputedStats attackerStats = plugin.getAttributeManager() != null
                        ? plugin.getAttributeManager().getStats(attackerUuid) : null;
                    if (attackerStats != null) {
                        float recoveryPct = attackerStats.getHealthRecoveryPercent();
                        if (recoveryPct != 0) {
                            dotRecovery *= (1.0f + recoveryPct / 100.0f);
                        }
                    }
                    recoveryProcessor.applyLifeLeech(store, damage, dotRecovery);
                }
            }
        }

        float newHealth = Math.max(0f, healthBefore - rpgDamage);
        if (newHealth <= 0) {
            // Set isDying flag IMMEDIATELY to prevent race condition with regen/refresh systems
            ComponentType<EntityStore, MobScalingComponent> scalingType = plugin.getMobScalingComponentType();
            if (scalingType != null) {
                MobScalingComponent scaling = store.getComponent(defenderRef, scalingType);
                if (scaling != null) {
                    scaling.setDying(true);
                }
            }
            markMobDamaged.accept(defenderRef, store);
            fireModifierDeathTriggers.accept(defenderRef, store);

            damage.setAmount(rpgDamage);
            LOGGER.at(Level.FINE).log("Lethal DOT: base=%.1f -> rpg=%.1f", baseDamage, rpgDamage);
            return;
        }

        damage.setCancelled(true);
        statMap.subtractStatValue(healthIndex, healthBefore - newHealth);
        markMobDamaged.accept(defenderRef, store);
        LOGGER.at(Level.FINE).log("DOT: base=%.1f -> rpg=%.1f, health: %.1f -> %.1f",
            baseDamage, rpgDamage, healthBefore, newHealth);
    }

    /**
     * Processes SHIELD_REGEN_ON_DOT: restores energy shield to the DOT applicator
     * proportional to the DOT damage dealt.
     */
    private void processShieldRegenOnDot(@Nonnull Damage damage, float dotDamage, @Nonnull Store<EntityStore> store) {
        if (dotDamage <= 0) return;
        if (!(damage.getSource() instanceof Damage.EntitySource entitySource)) return;

        Ref<EntityStore> sourceRef = entitySource.getRef();
        if (sourceRef == null || !sourceRef.isValid()) return;

        PlayerRef sourcePlayer = store.getComponent(sourceRef, PlayerRef.getComponentType());
        if (sourcePlayer == null) return;

        UUID sourceUuid = sourcePlayer.getUuid();
        ComputedStats sourceStats = plugin.getAttributeManager() != null
            ? plugin.getAttributeManager().getStats(sourceUuid) : null;
        if (sourceStats == null) return;

        float regenPct = sourceStats.getShieldRegenOnDot();
        if (regenPct <= 0) return;

        float shieldRestore = dotDamage * (regenPct / 100f);
        if (shieldRestore <= 0) return;

        var shieldTracker = plugin.getEnergyShieldTracker();
        if (shieldTracker == null) return;

        float maxShield = sourceStats.getEnergyShield();
        shieldTracker.addShield(sourceUuid, shieldRestore, maxShield);

        // Notify shield HUD of change
        if (plugin.getEnergyShieldHudManager() != null) {
            plugin.getEnergyShieldHudManager().notifyShieldChanged(sourceUuid);
        }

        LOGGER.at(Level.FINE).log("SHIELD_REGEN_ON_DOT: %.2f shield to %s (%.0f%% of %.2f DOT damage)",
            shieldRestore, sourceUuid.toString().substring(0, 8), regenPct, dotDamage);
    }

    // ==================== Environmental Damage Handling ====================

    /**
     * Handles HP-based environmental damage (lava, drowning, suffocation, etc.).
     *
     * <p>Environmental damage is converted from fixed values to max HP percentage-based
     * damage. This ensures hazards scale with player progression.
     */
    public void handleEnvironmentalDamage(
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Damage damage
    ) {
        float vanillaDamage = damage.getAmount();
        if (vanillaDamage <= 0 || damage.isCancelled()) {
            return;
        }

        Ref<EntityStore> defenderRef = archetypeChunk.getReferenceTo(index);
        if (defenderRef == null || !defenderRef.isValid()) {
            return;
        }

        EntityStatMap statMap = store.getComponent(defenderRef, EntityStatMap.getComponentType());
        if (statMap == null) {
            return;
        }

        int healthIndex = DefaultEntityStatTypes.getHealth();
        EntityStatValue healthStat = statMap.get(healthIndex);
        if (healthStat == null) {
            return;
        }
        float healthBefore = healthStat.get();
        float maxHealth = healthStat.getMax();

        // Get HP% from config based on damage cause
        DamageCause cause = DamageTypeClassifier.getDamageCause(damage);
        String causeId = cause != null ? cause.getId() : "environment";
        var envConfig = plugin.getConfigManager().getRPGConfig().getCombat().getEnvironmentalDamage();
        float hpPercent = envConfig.getHpPercentForCause(causeId);

        float rpgDamage;
        if (hpPercent > 0) {
            // Use HP-based damage with minimum 1 damage floor
            rpgDamage = Math.max(1f, maxHealth * (hpPercent / 100f));
            LOGGER.at(Level.FINE).log("Environmental damage '%s': %.1f%% of %.0f HP = %.1f",
                causeId, hpPercent, maxHealth, rpgDamage);
        } else {
            // Fallback to vanilla fixed damage (config value = 0)
            rpgDamage = vanillaDamage;
            LOGGER.at(Level.FINE).log("Environmental damage '%s': using vanilla %.1f",
                causeId, vanillaDamage);
        }

        // Apply fire resistance for fire/lava damage
        ElementType envElement = getElementForEnvironmentalCause(causeId);
        float resistanceReduction = 0f;
        if (envElement != null) {
            // Get defender's elemental stats for resistance
            ComputedStats defenderStats = statsResolver.getDefenderStats(index, archetypeChunk, store);
            ElementalStats defenderElemental = statsResolver.getDefenderElementalStats(
                index, archetypeChunk, store, defenderStats);
            if (defenderElemental != null) {
                float rawResist = (float) defenderElemental.getResistance(envElement);
                float effectiveResist = (float) ElementalCalculator.getEffectiveResistance(rawResist);
                resistanceReduction = effectiveResist;
                rpgDamage *= (1f - effectiveResist / 100f);
                rpgDamage = Math.max(1f, rpgDamage); // Maintain minimum 1 damage
                LOGGER.at(Level.FINE).log("Applied %s resistance: raw=%.1f%%, effective=%.1f%% -> damage=%.1f",
                    envElement.name(), rawResist, effectiveResist, rpgDamage);
            }
        }

        // Apply damage metadata — use correct elemental DamageType instead of hardcoded MAGIC
        DamageType damageType = DamageType.fromElement(envElement);
        damage.putMetaObject(RPGDamageSystem.DAMAGE_TYPE, damageType);
        damage.putMetaObject(RPGDamageSystem.WAS_CRITICAL, false);
        damage.putMetaObject(RPGDamageSystem.RPG_DAMAGE_VALUE, rpgDamage);
        damage.putMetaObject(RPGDamageSystem.ATTACK_TYPE, AttackType.UNKNOWN);
        damage.setAmount(0);

        // Create a simple breakdown for indicators
        DamageBreakdown breakdown = DamageBreakdown.simple(rpgDamage, damageType);

        // Send damage indicators
        indicatorService.sendDamageIndicatorsVisualOnly(store, defenderRef, damage, rpgDamage, breakdown, false);
        damage.putMetaObject(RPGDamageSystem.INDICATORS_SENT, true);

        // Record environmental damage to death recap (so fall/lava kills appear in death recap)
        if (rpgDamage > 0f && deathRecapRecorder.isAvailable()) {
            PlayerRef defenderPlayer = store.getComponent(defenderRef, PlayerRef.getComponentType());
            if (defenderPlayer != null) {
                String causeName = classifier.formatDamageCause(cause);
                CombatSnapshot envSnapshot = CombatSnapshot.forEnvironment(
                    causeName, rpgDamage, damageType, maxHealth, healthBefore);
                plugin.getDeathRecapTracker().recordDamage(defenderPlayer.getUuid(), envSnapshot);

                // Send environmental detail chat to defender if they have combat detail enabled
                indicatorService.sendEnvironmentalDetailChat(
                    store, defenderRef, causeName, rpgDamage, resistanceReduction, envElement);
            }
        }

        // Apply damage
        float newHealth = Math.max(0f, healthBefore - rpgDamage);
        if (newHealth <= 0) {
            // Lethal damage - let vanilla handle death
            damage.setAmount(rpgDamage);
            statMap.subtractStatValue(healthIndex, healthBefore);

            // Set isDying flag IMMEDIATELY to prevent race condition with regen/refresh systems
            ComponentType<EntityStore, MobScalingComponent> scalingType = plugin.getMobScalingComponentType();
            if (scalingType != null) {
                MobScalingComponent scaling = store.getComponent(defenderRef, scalingType);
                if (scaling != null) {
                    scaling.setDying(true);
                }
            }
            markMobDamaged.accept(defenderRef, store);
            fireModifierDeathTriggers.accept(defenderRef, store);

            DeathComponent.tryAddComponent(commandBuffer, defenderRef, damage);
            LOGGER.at(Level.FINE).log("Lethal environmental damage '%s': %.1f (%.1f%% HP)",
                causeId, rpgDamage, hpPercent);
            return;
        }

        // Non-lethal damage - cancel vanilla and apply manually
        damage.setCancelled(true);
        statMap.subtractStatValue(healthIndex, healthBefore - newHealth);
        markMobDamaged.accept(defenderRef, store);
        LOGGER.at(Level.FINE).log("Environmental damage '%s': %.1f, health: %.1f -> %.1f",
            causeId, rpgDamage, healthBefore, newHealth);
    }

    /**
     * Maps environmental damage causes to element types for resistance application.
     */
    @Nullable
    private ElementType getElementForEnvironmentalCause(@Nullable String causeId) {
        if (causeId == null) {
            return null;
        }
        String lower = causeId.toLowerCase();
        if (lower.contains("fire") || lower.contains("burn") || lower.contains("lava")) {
            return ElementType.FIRE;
        }
        // Drowning, suffocation, void, etc. have no elemental resistance
        return null;
    }
}
