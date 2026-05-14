package io.github.larsonix.trailoforbis.combat;

import com.hypixel.hytale.logger.HytaleLogger;
import java.util.logging.Level;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage.ProjectileSource;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.mobs.modifiers.MobModifierComponent;
import io.github.larsonix.trailoforbis.mobs.modifiers.MobModifierManager;
import io.github.larsonix.trailoforbis.mobs.modifiers.ModifierType;
import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.elemental.ElementType;
import io.github.larsonix.trailoforbis.elemental.ElementalStats;
import io.github.larsonix.trailoforbis.gear.model.WeaponType;
import io.github.larsonix.trailoforbis.gear.vanilla.VanillaAttackInfo;
import io.github.larsonix.trailoforbis.gear.vanilla.VanillaWeaponProfile;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.dependency.SystemGroupDependency;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCalculatorSystems;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.DamageEntityInteraction;
import com.hypixel.hytale.server.core.meta.MetaKey;
import io.github.larsonix.trailoforbis.gear.equipment.EquipmentValidator;
import io.github.larsonix.trailoforbis.compat.HexCastStateStore;
import io.github.larsonix.trailoforbis.compat.HexDamageAttributor;
import io.github.larsonix.trailoforbis.compat.HexSpellNormalizer;
import io.github.larsonix.trailoforbis.compat.HexcodeCompat;
import io.github.larsonix.trailoforbis.compat.HexcodeSpellConfig;
import io.github.larsonix.trailoforbis.compat.HexcodeSpellConfig;
import io.github.larsonix.trailoforbis.config.RPGConfig;
import io.github.larsonix.trailoforbis.mobs.component.MobScalingComponent;
import com.hypixel.hytale.server.core.modules.entity.component.Invulnerable;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;

import io.github.larsonix.trailoforbis.attributes.breakdown.StatBreakdownResult;
import io.github.larsonix.trailoforbis.combat.ailments.CombatAilmentApplicator;
import io.github.larsonix.trailoforbis.combat.ailments.CombatAilmentApplicator.AilmentSummary;
import io.github.larsonix.trailoforbis.combat.avoidance.AvoidanceProcessor;
import io.github.larsonix.trailoforbis.combat.avoidance.AvoidanceResultHandler;
import io.github.larsonix.trailoforbis.combat.deathrecap.CombatSnapshot;
import io.github.larsonix.trailoforbis.combat.deathrecap.DeathRecapRecorder;
import io.github.larsonix.trailoforbis.combat.detection.DamageTypeClassifier;
import io.github.larsonix.trailoforbis.combat.durability.DurabilityHandler;
import io.github.larsonix.trailoforbis.combat.effects.CombatEffectContext;
import io.github.larsonix.trailoforbis.combat.effects.CombatEffectRegistry;
import io.github.larsonix.trailoforbis.combat.environmental.EnvironmentalDamageProcessor;
import io.github.larsonix.trailoforbis.combat.format.CombatDebugFormatter;
import io.github.larsonix.trailoforbis.combat.format.CombatDebugLogger;
import io.github.larsonix.trailoforbis.combat.indicators.CombatIndicatorService;
import io.github.larsonix.trailoforbis.combat.indicators.CombatIndicatorService.CombatTextParams;
import io.github.larsonix.trailoforbis.combat.modifiers.ConditionalMultiplierCalculator;
import io.github.larsonix.trailoforbis.combat.modifiers.ConditionalResult;
import io.github.larsonix.trailoforbis.combat.modifiers.DamageModifierProcessor;
import io.github.larsonix.trailoforbis.combat.tracking.ConsecutiveHitTracker;
import io.github.larsonix.trailoforbis.combat.realm.RealmCombatModifierProcessor;
import io.github.larsonix.trailoforbis.combat.recovery.CombatRecoveryProcessor;
import io.github.larsonix.trailoforbis.combat.resolution.CombatEntityResolver;
import io.github.larsonix.trailoforbis.combat.resolution.CombatStatsResolver;
import io.github.larsonix.trailoforbis.combat.resolution.MobDamageResolver;
import io.github.larsonix.trailoforbis.combat.triggers.CombatTriggerHandler;
import io.github.larsonix.trailoforbis.util.PlayerWorldCache;
import com.hypixel.hytale.server.core.Message;
import io.github.larsonix.trailoforbis.maps.components.RealmMobComponent;
import io.github.larsonix.trailoforbis.sanctum.SkillNodeInspectionHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * ECS-based damage system orchestrator that applies RPG stats by canceling vanilla
 * damage and applying directly to health.
 *
 * <p>This class orchestrates the 7-phase damage pipeline by delegating to specialized
 * processor classes for each concern:
 * <ul>
 *   <li>{@link CombatEntityResolver} - Entity/UUID resolution</li>
 *   <li>{@link CombatStatsResolver} - Stats retrieval</li>
 *   <li>{@link DamageTypeClassifier} - Damage type detection</li>
 *   <li>{@link AvoidanceProcessor} - Dodge, evasion, block checks</li>
 *   <li>{@link ConditionalMultiplierCalculator} - Conditional damage multipliers</li>
 *   <li>{@link DamageModifierProcessor} - Post-calc modifications</li>
 *   <li>{@link CombatIndicatorService} - UI feedback</li>
 *   <li>{@link CombatFeedbackService} - Impact particles and sounds</li>
 *   <li>{@link CombatRecoveryProcessor} - Lifesteal, mana leech</li>
 *   <li>{@link DurabilityHandler} - Weapon/armor durability</li>
 *   <li>{@link CombatTriggerHandler} - Skill tree triggers</li>
 *   <li>{@link CombatAilmentApplicator} - Ailment application</li>
 *   <li>{@link DeathRecapRecorder} - Death recap recording</li>
 *   <li>{@link MobDamageResolver} - Mob/vanilla damage resolution</li>
 *   <li>{@link AvoidanceResultHandler} - Avoidance result handling + active blocking</li>
 *   <li>{@link RealmCombatModifierProcessor} - Realm modifier lookups</li>
 *   <li>{@link EnvironmentalDamageProcessor} - DOT + environmental pipelines</li>
 * </ul>
 */
public class RPGDamageSystem extends DamageEventSystem {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // ==================== MetaKey Definitions ====================

    /** MetaKey for storing the damage type. */
    public static final MetaKey<DamageType> DAMAGE_TYPE = Damage.META_REGISTRY.registerMetaObject(d -> DamageType.PHYSICAL);

    /** MetaKey for storing whether damage was a critical hit. */
    public static final MetaKey<Boolean> WAS_CRITICAL = Damage.META_REGISTRY.registerMetaObject(d -> Boolean.FALSE);

    /** MetaKey for storing whether an attack was dodged (evaded). */
    public static final MetaKey<Boolean> WAS_DODGED = Damage.META_REGISTRY.registerMetaObject(d -> Boolean.FALSE);

    /** MetaKey for storing whether an attack was blocked. */
    public static final MetaKey<Boolean> WAS_BLOCKED = Damage.META_REGISTRY.registerMetaObject(d -> Boolean.FALSE);

    /** MetaKey for storing whether an attack was parried. */
    public static final MetaKey<Boolean> WAS_PARRIED = Damage.META_REGISTRY.registerMetaObject(d -> Boolean.FALSE);

    /** MetaKey for storing whether an attack missed (failed accuracy check). */
    public static final MetaKey<Boolean> WAS_MISSED = Damage.META_REGISTRY.registerMetaObject(d -> Boolean.FALSE);

    /** MetaKey for storing the amount of damage absorbed by energy shield. */
    public static final MetaKey<Float> SHIELD_ABSORBED = Damage.META_REGISTRY.registerMetaObject(d -> 0f);

    /** MetaKey for storing the attack type for damage indicators. */
    public static final MetaKey<AttackType> ATTACK_TYPE = Damage.META_REGISTRY.registerMetaObject(d -> AttackType.UNKNOWN);

    /** MetaKey for storing the calculated RPG damage value. */
    public static final MetaKey<Float> RPG_DAMAGE_VALUE = Damage.META_REGISTRY.registerMetaObject(d -> null);

    /** MetaKey for marking that damage indicators have been sent. */
    public static final MetaKey<Boolean> INDICATORS_SENT = Damage.META_REGISTRY.registerMetaObject(d -> Boolean.FALSE);

    /** MetaKey for hex spell element (non-null means this damage is a spell). */
    public static final MetaKey<ElementType> HEX_SPELL_ELEMENT = Damage.META_REGISTRY.registerMetaObject(d -> null);

    /** MetaKey for hex spell base damage (before weapon implicit is added). */
    public static final MetaKey<Float> HEX_SPELL_BASE_DAMAGE = Damage.META_REGISTRY.registerMetaObject(d -> null);

    /** MetaKey for hex spell display name (for death recap). */
    public static final MetaKey<String> HEX_SPELL_DISPLAY_NAME = Damage.META_REGISTRY.registerMetaObject(d -> null);

    /** MetaKey for hex spell source type ID (e.g., "hex_bolt") for per-glyph multiplier lookup. */
    public static final MetaKey<String> HEX_SPELL_SOURCE_TYPE = Damage.META_REGISTRY.registerMetaObject(d -> null);

    // ==================== Dependencies ====================

    private final TrailOfOrbis plugin;
    private CombatCalculator calculator;
    private RPGDamageCalculator rpgCalculator;

    // Processor classes (eagerly initialized via initialize(), fallback lazy init)
    private CombatEntityResolver entityResolver;
    private CombatStatsResolver statsResolver;
    private DamageTypeClassifier classifier;
    private AvoidanceProcessor avoidanceProcessor;
    private ConditionalMultiplierCalculator multiplierCalculator;
    private DamageModifierProcessor modifierProcessor;
    private CombatIndicatorService indicatorService;
    private CombatFeedbackService feedbackService;
    private CombatRecoveryProcessor recoveryProcessor;
    private DurabilityHandler durabilityHandler;
    private CombatTriggerHandler triggerHandler;
    private CombatAilmentApplicator ailmentApplicator;
    private DeathRecapRecorder deathRecapRecorder;
    private CombatRequirementNotifier requirementNotifier;
    private MobDamageResolver mobDamageResolver;
    private SkillNodeInspectionHandler skillNodeInspectionHandler;
    private RealmCombatModifierProcessor realmModifierProcessor;
    private EnvironmentalDamageProcessor environmentalProcessor;
    private AvoidanceResultHandler avoidanceResultHandler;

    /** Combat effect registry for keystone behavioral effects. */
    @Nullable
    private volatile CombatEffectRegistry combatEffectRegistry;

    /** Re-entrancy guard: prevents recursive damage pipeline execution (e.g., combat effects firing damage). */
    private boolean processingDamage = false;

    // ── Attacker context cache for AoE performance ──
    // For Hexcode area spells hitting 40+ targets, all damage events share the same attacker.
    // Cache attacker-side data on first hit, reuse for subsequent targets in the same burst.
    // Keyed by attacker player UUID (not ref identity — ref identity is fragile across source rewrites).
    @Nullable
    private io.github.larsonix.trailoforbis.combat.context.AttackerContext cachedAttackerCtx;
    @Nullable
    private UUID cachedAttackerUuid;

    public RPGDamageSystem(@Nonnull TrailOfOrbis plugin) {
        this.plugin = plugin;
    }

    /**
     * Sets the combat effect registry. Called from TrailOfOrbis after registry initialization.
     */
    public void setCombatEffectRegistry(@Nullable CombatEffectRegistry registry) {
        this.combatEffectRegistry = registry;
        if (realmModifierProcessor != null) {
            realmModifierProcessor.setCombatEffectRegistry(registry);
        }
        if (environmentalProcessor != null) {
            environmentalProcessor.setCombatEffectRegistry(registry);
        }
        if (avoidanceResultHandler != null) {
            avoidanceResultHandler.setCombatEffectRegistry(registry);
        }
    }

    // ==================== Initialization ====================

    /**
     * Eagerly initializes all combat processors and calculators.
     *
     * <p>Called from TrailOfOrbis.onEnable() after all managers are ready.
     * This eliminates the synchronized DCL blocks that caused contention
     * when Hexcode AoE spells fired dozens of damage events simultaneously.
     */
    public void initialize() {
        initCalculators();
        initProcessors();
    }

    private void initCalculators() {
        if (calculator == null) {
            var armorConfig = plugin.getConfigManager().getRPGConfig().getArmor();
            calculator = new CombatCalculator(armorConfig.getMaxReduction(), armorConfig.getLevelScale(), armorConfig.getBaseConstant());
            calculator.setMinArmorEffectiveness(armorConfig.getArmorPenetrationFloor());
            LOGGER.at(Level.INFO).log("CombatCalculator initialized (levelScale=%.1f, baseConstant=%.1f, pen floor: %.0f%%)",
                armorConfig.getLevelScale(), armorConfig.getBaseConstant(), armorConfig.getArmorPenetrationFloor() * 100);
        }
        if (rpgCalculator == null) {
            rpgCalculator = new RPGDamageCalculator(getCalculator());
            LOGGER.at(Level.INFO).log("RPGDamageCalculator initialized");
        }
    }

    private void initProcessors() {
        if (entityResolver != null) return; // Already initialized

        CombatEntityResolver localResolver = new CombatEntityResolver();
        statsResolver = new CombatStatsResolver(localResolver, plugin.getMobScalingComponentType());
        classifier = new DamageTypeClassifier();
        avoidanceProcessor = new AvoidanceProcessor(plugin.getConfigManager());
        multiplierCalculator = new ConditionalMultiplierCalculator(
            localResolver, plugin.getAilmentTracker(), plugin.getConsecutiveHitTracker());
        // Apply Shock amplification cap when Hexcode is present
        if (HexcodeCompat.isLoaded()) {
            float maxAmp = plugin.getConfigManager().getHexcodeSpellConfig().getMax_damage_amplification();
            multiplierCalculator.setMaxShockAmplification(maxAmp);
            LOGGER.at(Level.INFO).log("Hexcode detected — Shock amplification cap set to %.2f", maxAmp);
        }
        modifierProcessor = new DamageModifierProcessor(
            localResolver, plugin.getConfigManager(),
            plugin.getEnergyShieldTracker(), plugin.getAilmentTracker());
        indicatorService = new CombatIndicatorService(localResolver, plugin, plugin.getCombatTextColorManager(), plugin.getCombatFeedbackGhostManager());
        feedbackService = new CombatFeedbackService();
        feedbackService.init();
        recoveryProcessor = new CombatRecoveryProcessor(localResolver);
        durabilityHandler = new DurabilityHandler();
        triggerHandler = new CombatTriggerHandler(localResolver, plugin.getConditionalTriggerSystem());
        ailmentApplicator = new CombatAilmentApplicator(
            localResolver, plugin.getAilmentTracker(), plugin.getAilmentCalculator(),
            plugin.getAilmentImmunityTracker(), plugin.getAilmentEffectManager());
        deathRecapRecorder = new DeathRecapRecorder(
            localResolver, classifier, plugin::getDeathRecapTracker,
            plugin.getMobScalingComponentType(),
            playerId -> plugin.getLevelingManager() != null
                ? plugin.getLevelingManager().getLevel(playerId) : 1);
        mobDamageResolver = new MobDamageResolver(plugin);
        skillNodeInspectionHandler = new SkillNodeInspectionHandler(plugin);
        realmModifierProcessor = new RealmCombatModifierProcessor(localResolver);
        avoidanceResultHandler = new AvoidanceResultHandler(
            localResolver, statsResolver, indicatorService, feedbackService,
            recoveryProcessor, deathRecapRecorder, triggerHandler);
        environmentalProcessor = new EnvironmentalDamageProcessor(
            plugin, statsResolver, classifier, localResolver, indicatorService,
            deathRecapRecorder, recoveryProcessor, rpgCalculator,
            this::markMobDamaged, this::fireModifierDeathTriggers, this::resolveAttackerLevel);
        // Requirement notification on attack
        if (plugin.getGearManager() != null && plugin.getGearManager().getEquipmentValidator() != null) {
            requirementNotifier = new CombatRequirementNotifier(plugin.getGearManager().getEquipmentValidator());
        }
        LOGGER.at(Level.INFO).log("Combat processors initialized");
        entityResolver = localResolver;
    }

    private CombatCalculator getCalculator() {
        return calculator;
    }

    private RPGDamageCalculator getRPGCalculator() {
        return rpgCalculator;
    }

    /**
     * Returns the combat requirement notifier for disconnect cleanup.
     */
    @Nullable
    public CombatRequirementNotifier getRequirementNotifier() {
        return requirementNotifier;
    }

    /** Fallback lazy init — only used if initialize() wasn't called (shouldn't happen). */
    private void ensureProcessorsInitialized() {
        if (entityResolver == null) {
            LOGGER.atWarning().log("RPGDamageSystem processors not eagerly initialized — falling back to lazy init");
            initialize();
        }
    }

    // ==================== System Configuration ====================

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        // Run AFTER GatherDamageGroup collects damage info, but BEFORE FilterDamageGroup
        // applies vanilla armor reduction. This ensures we process damage using
        // RPG-only stats without interference from vanilla armor.
        return Set.of(
            new SystemGroupDependency<>(Order.AFTER, DamageModule.get().getGatherDamageGroup()),
            new SystemGroupDependency<>(Order.BEFORE, DamageModule.get().getFilterDamageGroup()),
            new SystemDependency<>(Order.BEFORE, DamageSystems.ApplyDamage.class)
        );
    }

    // ==================== Main Handle Method ====================

    @Override
    public void handle(
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Damage damage
    ) {
        // Re-entrancy guard: blocks recursive damage from combat effects
        // (e.g., keystone effect → executeDamage → handle re-entered).
        //
        // DOT ticks (burn, poison) are EXEMPT: they fire from independent ECS tick
        // systems (RpgBurnTickSystem, RpgPoisonTickSystem), not as re-entrant calls
        // from within our pipeline. Even if they arrive while processingDamage is true
        // (unlikely in single-threaded ECS, but possible via world.execute callbacks),
        // they must still be processed — blocking them silently kills DOT damage.
        if (processingDamage && !isDOTSource(damage)) {
            LOGGER.atWarning().log("[RPGDamageSystem] Re-entrant non-DOT damage detected — skipping RPG pipeline");
            return;
        }
        boolean wasProcessing = processingDamage;
        processingDamage = true;
        try {
        handleInner(index, archetypeChunk, store, commandBuffer, damage);
        } finally {
            processingDamage = wasProcessing;
        }
    }

    /**
     * Identifies DOT damage by its DamageCause (not source type).
     *
     * <p>DOT damage uses {@code EntitySource} (the player who applied the burn/poison)
     * or {@code NULL_SOURCE} (if the applicator left the world) — never {@code EnvironmentSource}.
     * The reliable identifier is the DamageCause: "Rpg_Burn_Dot" or "Rpg_Poison_Dot",
     * set by RpgBurnTickSystem and RpgPoisonTickSystem respectively.
     */
    private static boolean isDOTSource(@Nonnull Damage damage) {
        DamageCause cause = DamageTypeClassifier.getDamageCause(damage);
        if (cause == null) return false;
        return DamageTypeClassifier.isRpgDotCause(cause.getId());
    }

    /** Inner handle method — contains the actual damage pipeline logic. */
    private void handleInner(
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Damage damage
    ) {
        ensureProcessorsInitialized();

        // Skip invulnerable entities (Creative mode, god mode, spawn protection, etc.).
        // Our system runs BEFORE FilterDamageGroup where Hytale normally checks this.
        // Without this guard, we'd calculate RPG damage and apply it via
        // statMap.subtractStatValue() — completely bypassing Hytale's invulnerability.
        // Check both sources: Invulnerable component (Creative, /invulnerable command)
        // and EffectControllerComponent flag (spawn protection, external mod effects).
        if (archetypeChunk.getArchetype().contains(Invulnerable.getComponentType())) {
            return;
        }
        EffectControllerComponent effectController = archetypeChunk.getComponent(
            index, EffectControllerComponent.getComponentType());
        if (effectController != null && effectController.isInvulnerable()) {
            return;
        }

        // Diagnostic: log EnvironmentSource damage (FINE level — not in production logs)
        if (damage.getSource() instanceof Damage.EnvironmentSource env) {
            LOGGER.atFine().log("[RPG-DIAG] EnvironmentSource damage: type=%s amount=%.1f cancelled=%s",
                env.getType(), damage.getAmount(), damage.isCancelled());
        }

        // ========== PHASE 1: EARLY SETUP ==========
        DamageCalculatorSystems.DamageSequence damageSequence =
            damage.getIfPresentMetaObject(DamageCalculatorSystems.DAMAGE_SEQUENCE);
        boolean isDOT = classifier.isDOTDamage(damage);

        if (damageSequence == null && !isDOT) {
            // Only cancel secondary MELEE damage events, NOT projectile damage
            // ProjectileSource extends EntitySource but is legitimate damage without DamageSequence
            // (Projectiles deal damage directly via DamageSystems.executeDamage, bypassing DamageSequence)
            if (damage.getSource() instanceof Damage.EntitySource &&
                !(damage.getSource() instanceof ProjectileSource)) {
                LOGGER.at(Level.FINE).log("Secondary melee damage event cancelled");
                damage.setCancelled(true);
                damage.setAmount(0);
                return;
            }
        }

        // Log projectile damage flow for debugging
        if (damage.getSource() instanceof ProjectileSource) {
            LOGGER.at(Level.FINE).log("Processing projectile damage: cause=%s, amount=%.1f",
                DamageTypeClassifier.getDamageCause(damage) != null ? DamageTypeClassifier.getDamageCause(damage).getId() : "null",
                damage.getAmount());
        }

        // Check vanilla damage amount to skip already-processed or invalid events
        // Note: We don't use this value for calculations - RPG base damage is computed later
        float vanillaDamage = damage.getAmount();
        if (vanillaDamage <= 0 || damage.isCancelled()) {
            return;
        }

        // Guide popup damage suppression: if target is a player with a guide popup open,
        // zero out the damage so they can read it safely. The popup has a 60-second safety
        // valve that auto-clears, so this can never be permanent.
        if (plugin.getGuideManager() != null) {
            Ref<EntityStore> guideRef = archetypeChunk.getReferenceTo(index);
            if (guideRef != null) {
                PlayerRef defenderForGuide = store.getComponent(guideRef, PlayerRef.getComponentType());
                if (defenderForGuide != null && plugin.getGuideManager().shouldSuppressDamage(defenderForGuide.getUuid())) {
                    damage.setAmount(0);
                    damage.setCancelled(true);
                    return;
                }
            }
        }

        if (isDOT) {
            environmentalProcessor.handleDOTDamage(index, archetypeChunk, store, commandBuffer, damage);
            return;
        }

        // Handle Hexcode spell damage (EnvironmentSource with "hex_" prefix)
        // Resolve caster and rewrite source so spells flow through the main RPG pipeline.
        // Must be checked BEFORE generic environmental damage so spells use RPG scaling.
        if (damage.getSource() instanceof Damage.EnvironmentSource envSource) {
            String sourceType = envSource.getType();
            if (HexcodeSpellConfig.isHexSpellSource(sourceType)) {
                HexcodeSpellConfig spellConfig = plugin.getConfigManager().getHexcodeSpellConfig();
                if (spellConfig == null || !spellConfig.isEnabled()) {
                    environmentalProcessor.handleEnvironmentalDamage(index, archetypeChunk, store, commandBuffer, damage);
                    return;
                }

                // Resolve caster via ThreadLocal (same synchronous invoke chain) or recent caster fallback
                HexCastStateStore.CastRecord freshCast = HexCastStateStore.getFreshCast();
                Ref<EntityStore> casterRef = freshCast != null ? freshCast.casterRef() : null;
                if (casterRef == null || !casterRef.isValid()) {
                    casterRef = HexCastStateStore.findRecentCaster(store);
                }

                // Rewrite source to EntitySource for attacker resolution in the main pipeline.
                // Must happen BEFORE defender resolution so kill attribution works.
                if (casterRef != null && casterRef.isValid()) {
                    damage.setSource(new Damage.EntitySource(casterRef));
                }

                // Check weapon requirements — if caster can't use their staff, cancel spell damage
                if (casterRef != null && casterRef.isValid() && requirementNotifier != null) {
                    PlayerRef casterPlayerRef = store.getComponent(casterRef, PlayerRef.getComponentType());
                    if (casterPlayerRef != null) {
                        UUID casterUuid = casterPlayerRef.getUuid();
                        Player casterEntity = store.getComponent(casterRef, Player.getComponentType());
                        if (casterEntity != null && casterEntity.getInventory() != null) {
                            ItemStack weapon = casterEntity.getInventory().getActiveHotbarItem();
                            EquipmentValidator eqValidator = plugin.getGearManager() != null
                                ? plugin.getGearManager().getEquipmentValidator() : null;
                            if (eqValidator != null && !ItemStack.isEmpty(weapon)
                                    && !eqValidator.canEquip(casterUuid, weapon)) {
                                requirementNotifier.checkAndNotify(casterUuid, casterEntity.getInventory());
                                damage.setAmount(0);
                                damage.setCancelled(true);
                                LOGGER.atFine().log("[SpellDmg] %s: cancelled — weapon requirements not met for %s",
                                    sourceType, casterUuid.toString().substring(0, 8));
                                return;
                            }
                        }
                    }
                }

                // Store spell metadata for the main pipeline to pick up in gatherCombatInputs
                ElementType element = spellConfig.getElement(sourceType);
                damage.putMetaObject(HEX_SPELL_ELEMENT, element);
                damage.putMetaObject(HEX_SPELL_BASE_DAMAGE, vanillaDamage);
                damage.putMetaObject(HEX_SPELL_DISPLAY_NAME, spellConfig.getDisplayName(sourceType));
                damage.putMetaObject(HEX_SPELL_SOURCE_TYPE, sourceType);

                LOGGER.atFine().log("[SpellDmg] %s: routing through main pipeline, element=%s, hexBase=%.1f",
                    sourceType, element != null ? element.name() : "NONE", vanillaDamage);

                // Fall through to the main damage pipeline (no return)
            }
        }

        // Handle HP-based environmental damage (lava, drowning, etc.)
        if (classifier.isEnvironmentalDamage(damage)) {
            environmentalProcessor.handleEnvironmentalDamage(index, archetypeChunk, store, commandBuffer, damage);
            return;
        }

        Ref<EntityStore> defenderRef = archetypeChunk.getReferenceTo(index);
        if (defenderRef == null || !defenderRef.isValid()) {
            return;
        }

        // Cancel mob-vs-mob damage in realms — realm mobs should only fight players
        if (isRealmMobVsMob(store, defenderRef, damage)) {
            damage.setCancelled(true);
            damage.setAmount(0);
            return;
        }

        // Cancel damage between party members (unless party has PvP enabled)
        if (isPartyPvpBlocked(store, defenderRef, damage)) {
            damage.setCancelled(true);
            damage.setAmount(0);
            return;
        }

        if (skillNodeInspectionHandler.handleSkillNodeInspection(store, defenderRef, damage)) {
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

        // Log damage event start (FINE-level, only visible if logging configured)
        {
            float healthPercent = maxHealth > 0 ? (healthBefore / maxHealth) * 100f : 0f;
            LOGGER.at(Level.FINE).log("[DmgPipeline] ════════════ DAMAGE EVENT START ════════════");
            LOGGER.at(Level.FINE).log("[DmgPipeline] Defender: entity at index %d", index);
            LOGGER.at(Level.FINE).log("[DmgPipeline] Health: %.1f/%.1f (%.1f%%)", healthBefore, maxHealth, healthPercent);
            LOGGER.at(Level.FINE).log("[DmgPipeline] Vanilla damage input: %.1f", vanillaDamage);
        }

        // ========== PHASE 2: GATHER INPUTS ==========
        DamageContext ctx = new DamageContext();
        ctx.defenderRef = defenderRef;
        ctx.statMap = statMap;
        ctx.healthIndex = healthIndex;
        ctx.healthBefore = healthBefore;
        ctx.maxHealth = maxHealth;
        ctx.vanillaDamage = vanillaDamage;

        gatherCombatInputs(index, archetypeChunk, store, damage, ctx);
        // ========== PHASE 2.5: REQUIREMENT NOTIFICATION ==========
        // Check if the attacker is a player with unmet equipment requirements
        if (requirementNotifier != null) {
            UUID attackerUuid = entityResolver.getAttackerPlayerUuid(store, damage);
            if (attackerUuid != null) {
                Ref<EntityStore> attackerRef = entityResolver.getAttackerRef(store, damage);
                if (attackerRef != null && attackerRef.isValid()) {
                    Player attackerPlayer = store.getComponent(attackerRef, Player.getComponentType());
                    if (attackerPlayer != null && attackerPlayer.getInventory() != null) {
                        requirementNotifier.checkAndNotify(attackerUuid, attackerPlayer.getInventory());
                    }
                }
            }
        }

        // ========== PHASE 3: PRE-CALC AVOIDANCE ==========
        AvoidanceProcessor.AvoidanceCheckResult avoidanceCheck = avoidanceProcessor.checkAvoidanceDetailed(
            store, defenderRef, damage, ctx.defenderStats, ctx.attackerStats, ctx.conditionalMultiplier, ctx.rpgBaseDamage);
        Optional<AvoidanceProcessor.AvoidanceResult> avoidance = avoidanceCheck.avoidance();
        ctx.avoidanceStats = avoidanceCheck.stats();

        if (avoidance.isPresent()) {
            LOGGER.at(Level.FINE).log("[DmgPipeline] ──── AVOIDANCE CHECK ────");
            LOGGER.at(Level.FINE).log("[DmgPipeline] Result: %s", avoidance.get().reason().name());
            LOGGER.at(Level.FINE).log("[DmgPipeline] ════════════ DAMAGE EVENT END ════════════");
            avoidanceResultHandler.handleAvoidedDamage(index, archetypeChunk, store, defenderRef, damage, avoidance.get(), ctx.avoidanceStats);
            return;
        }

        LOGGER.at(Level.FINE).log("[DmgPipeline] ──── AVOIDANCE CHECK ────");
        LOGGER.at(Level.FINE).log("[DmgPipeline] Result: HIT");

        // ========== PHASE 4: SINGLE CALCULATOR CALL ==========
        ctx.trace = getRPGCalculator().calculateTraced(
            ctx.rpgBaseDamage, ctx.attackerStats, ctx.attackerElemental, ctx.defenderStats, ctx.defenderElemental,
            ctx.attackType, ctx.conditionalMultiplier, ctx.conditionalDetail, ctx.attackTypeMultiplier,
            ctx.spellElement, ctx.attackerLevel, ctx.isHexSpell, ctx.hexBakedElement);
        ctx.trace.setAttackerBreakdown(null); // will be populated later if player
        ctx.breakdown = ctx.trace.breakdown();

        // ========== PHASE 4.5: POPULATE TRACE CONTEXT ==========
        // Only populate expensive trace context (weapon profiles, derivation) when
        // someone has combat detail mode on. The trace builder still records phases
        // during calculation, but post-calc enrichment is skipped for AoE performance.
        boolean detailMode = (ctx.attackerUuid != null && plugin.isCombatDetailEnabled(ctx.attackerUuid))
            || (ctx.defenderUuid != null && plugin.isCombatDetailEnabled(ctx.defenderUuid));
        if (detailMode) {
            populateTraceContext(store, damage, ctx);
        }

        // ── COMBAT DEBUG: Full context dump to chat + server log ──
        if (ctx.attackerUuid != null && plugin.isCombatDebugEnabled(ctx.attackerUuid)) {
            try {
                // Send formatted message to player chat
                Message debugMsg = CombatDebugFormatter.format(
                    ctx.attackerUuid, ctx.attackerStats, ctx.attackerElemental,
                    ctx.breakdown, ctx.attackType != null ? ctx.attackType : AttackType.UNKNOWN,
                    ctx.spellElement, ctx.rpgBaseDamage, ctx.attackTypeMultiplier,
                    ctx.conditionalMultiplier
                );
                PlayerRef debugRef = PlayerWorldCache.findPlayerRef(ctx.attackerUuid);
                if (debugRef != null) debugRef.sendMessage(debugMsg);

                // Also log plain-text version to server log (so we can read it remotely)
                String logDump = CombatDebugFormatter.formatForLog(
                    ctx.attackerUuid, ctx.attackerStats, ctx.attackerElemental,
                    ctx.breakdown, ctx.attackType != null ? ctx.attackType : AttackType.UNKNOWN,
                    ctx.spellElement, ctx.rpgBaseDamage, ctx.attackTypeMultiplier,
                    ctx.conditionalMultiplier
                );
                LOGGER.atInfo().log("[COMBAT-DEBUG]\n%s", logDump);
            } catch (Exception e) {
                LOGGER.at(Level.WARNING).log("[CombatDebug] Failed to send debug dump: %s", e.getMessage());
            }
        }

        // ========== PHASE 5: POST-CALC ADJUSTMENTS ==========
        applyPostCalcModifications(store, commandBuffer, damage, ctx);

        // ========== PHASE 5.5: ACTIVE BLOCKING REDUCTION ==========
        // Must run BEFORE Phase 6 (indicators) so damage numbers reflect the reduced amount.
        {
            var blockResult = avoidanceResultHandler.applyActiveBlockingReduction(
                store, ctx.defenderRef, ctx.defenderStats, ctx.rpgDamage);
            if (blockResult.wasBlocking()) {
                ctx.rpgDamage = blockResult.newDamage();
                ctx.trace.setActiveBlocking(blockResult.hasShield(), blockResult.reductionPercent(),
                    blockResult.preDamage(), blockResult.newDamage());
                // Combat Effect Hooks: Block
                float blockedDamage = blockResult.preDamage() - blockResult.newDamage();
                if (combatEffectRegistry != null && blockedDamage > 0 && ctx.defenderUuid != null) {
                    CombatEffectContext blockCtx = ctx.toEffectContext(damage, store, commandBuffer);
                    combatEffectRegistry.runOnBlock(ctx.defenderUuid, blockedDamage, ctx.rpgDamage, blockCtx);
                }
            }
        }

        // ========== PHASE 6: METADATA & TRIGGERS ==========
        emitCombatFeedback(index, archetypeChunk, store, commandBuffer, damage, ctx);

        // ========== PHASE 7: RECOVERY & THORNS ==========
        processRecoveryAndThorns(store, commandBuffer, damage, ctx);

        // ========== PHASE 7.9: FINALIZE & APPLY ==========
        finalizeAndApply(index, archetypeChunk, store, commandBuffer, damage, ctx);
    }

    // ==================== Damage Pipeline Context ====================

    /**
     * Mutable context object that flows between damage pipeline phase methods.
     *
     * <p>Holds all intermediate state computed during the pipeline: entity references,
     * stats, damage values, trace/breakdown objects, and boolean flags. Created at the
     * start of the pipeline and passed to each phase method in sequence.
     */
    private static class DamageContext {
        // Entity references
        Ref<EntityStore> defenderRef;
        @Nullable UUID defenderUuid;
        @Nullable UUID attackerUuid;

        // Health state
        EntityStatMap statMap;
        int healthIndex;
        float healthBefore;
        float maxHealth;

        // Vanilla input
        float vanillaDamage;

        // Attack classification
        AttackType attackType;
        @Nullable ComputedStats attackerStats;
        @Nullable ComputedStats defenderStats;
        @Nullable ElementalStats attackerElemental;
        @Nullable ElementalStats defenderElemental;
        float defenderHealthPercent;

        // RPG damage inputs
        float rpgWeaponDamage;
        boolean hasRpgWeapon;
        float rpgBaseDamage;
        float attackTypeMultiplier;
        @Nullable VanillaWeaponProfile traceWeaponProfile;
        float traceFallbackRef;

        // Spell-specific (null for non-spell attacks)
        @Nullable ElementType spellElement;
        // True if this is a Hexcode spell (EnvironmentSource origin) — uses uncancelled finalize path
        // Also determines projectile scaling: hex spells always benefit from projectileDamagePercent
        // because they require a delivery glyph (Beam/Projectile) to reach the target.
        boolean isHexSpell;
        // When non-null, Step 2 skips this element's flat damage (already baked into hex base).
        // Step 1 (flat spell) is also skipped. Non-main elements still enter the pipeline.
        @Nullable ElementType hexBakedElement;

        // Conditional multipliers
        ConditionalResult conditionalDetail;
        float conditionalMultiplier;

        // Avoidance
        AvoidanceProcessor.AvoidanceDetail avoidanceStats;

        // Calculator output
        DamageTrace trace;
        DamageBreakdown breakdown;

        // Attacker level (for armor formula)
        int attackerLevel = 1;

        // Post-calc state
        float rpgDamage;
        boolean wasParried;

        /** Builds a CombatEffectContext from this DamageContext for the effect registry. */
        CombatEffectContext toEffectContext(Damage damage, Store<EntityStore> store,
                                            @Nullable CommandBuffer<EntityStore> commandBuffer) {
            // Resolve attacker ref and health for effects that need it
            Ref<EntityStore> atkRef = null;
            float atkHealthPct = -1f;
            try {
                if (attackerUuid != null) {
                    atkRef = (damage.getSource() instanceof Damage.EntitySource es) ? es.getRef() : null;
                    if (atkRef != null && atkRef.isValid()) {
                        var atkStatMap = store.getComponent(atkRef, EntityStatMap.getComponentType());
                        if (atkStatMap != null) {
                            int hpIdx = DefaultEntityStatTypes.getHealth();
                            EntityStatValue hpStat = atkStatMap.get(hpIdx);
                            if (hpStat != null) {
                                atkHealthPct = hpStat.getMax() > 0 ? hpStat.get() / hpStat.getMax() : 1f;
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
                // Best-effort — some damage sources may not have valid refs
            }
            return new CombatEffectContext(
                attackerUuid, defenderUuid, attackerStats, defenderStats,
                attackerElemental, defenderElemental, breakdown, trace,
                attackType != null ? attackType : AttackType.UNKNOWN,
                spellElement, rpgDamage, rpgBaseDamage,
                healthBefore, maxHealth,
                breakdown != null && breakdown.wasCritical(),
                damage, store, defenderRef, atkRef, commandBuffer, atkHealthPct
            );
        }
    }

    // ==================== Damage Pipeline Phase Methods ====================

    /**
     * Phase 2: Gathers all combat inputs — attack type, stats, base damage, multipliers.
     *
     * <p>Populates the context with attacker/defender stats, elemental stats,
     * RPG base damage, attack type multiplier, and conditional multipliers.
     */
    private void gatherCombatInputs(
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull Damage damage,
        @Nonnull DamageContext ctx
    ) {
        // ── Attacker cache: reuse attacker-side data for AoE (same attacker, many targets) ──
        // Cache hit requires: same UUID AND same stats version. The stats version increments
        // on every recalculateStats() call. This ensures weapon switches (which trigger
        // recalculation) invalidate the cache, while AoE hits within the same tick reuse it.
        UUID resolvedAttackerUuid = entityResolver.getAttackerPlayerUuid(store, damage);
        long currentStatsVersion = (resolvedAttackerUuid != null && plugin.getAttributeManager() != null)
            ? plugin.getAttributeManager().getStatsVersion(resolvedAttackerUuid) : 0L;
        boolean cacheHit = (cachedAttackerCtx != null
            && resolvedAttackerUuid != null
            && resolvedAttackerUuid.equals(cachedAttackerUuid)
            && cachedAttackerCtx.statsVersion() == currentStatsVersion);

        if (cacheHit) {
            // Reuse cached attacker data — avoids 6-8 redundant lookups per target
            ctx.attackerStats = cachedAttackerCtx.attackerStats();
            ctx.attackerElemental = cachedAttackerCtx.attackerElemental();
            ctx.attackerUuid = resolvedAttackerUuid;
        } else {
            // Cache miss — resolve fresh
            ctx.attackerStats = statsResolver.getAttackerStats(store, damage);
            ctx.attackerElemental = statsResolver.getAttackerElementalStats(store, damage);
            ctx.attackerUuid = resolvedAttackerUuid;
        }

        // ── Weapon staleness detector + self-healing ──
        // HotbarSlotTrackingSystem tracks weapon identity per-tick and triggers recalculation
        // on any change. If a stale weapon reaches combat despite that, this detector:
        //   1. Invalidates the AoE attacker cache (so this hit doesn't propagate stale data)
        //   2. Re-resolves stats fresh from AttributeService for the CURRENT attack
        //   3. Defers a full recalculateStats() to the next tick (can't do it inline in ECS)
        if (ctx.attackerStats != null && resolvedAttackerUuid != null
                && !(damage.getSource() instanceof Damage.EnvironmentSource)) {
            try {
                PlayerRef validationRef = PlayerWorldCache.findPlayerRef(resolvedAttackerUuid);
                if (validationRef != null) {
                    Ref<EntityStore> entityRef = validationRef.getReference();
                    if (entityRef != null && entityRef.isValid()) {
                        Player player = store.getComponent(entityRef, Player.getComponentType());
                        if (player != null && player.getInventory() != null) {
                            ItemStack liveWeapon = player.getInventory().getActiveHotbarItem();
                            String liveRawId = (liveWeapon != null && liveWeapon.getItem() != null)
                                ? liveWeapon.getItem().getId() : null;
                            String cachedRawId = ctx.attackerStats.getWeaponRawItemId();

                            if (!java.util.Objects.equals(liveRawId, cachedRawId)) {
                                LOGGER.atWarning().log(
                                    "[DmgPipeline] WEAPON STALE: live=%s cached=%s — self-healing: defer recalc + invalidate cache",
                                    liveRawId, cachedRawId);

                                // Invalidate AoE cache so no more targets use stale data
                                cachedAttackerCtx = null;
                                cachedAttackerUuid = null;

                                // Defer a full recalculation to the next tick
                                // (can't call recalculateStats inline during ECS damage processing)
                                final UUID healUuid = resolvedAttackerUuid;
                                var world = PlayerWorldCache.getPlayerWorld(healUuid).orElse(null);
                                if (world != null && world.isAlive()) {
                                    world.execute(() -> {
                                        try {
                                            io.github.larsonix.trailoforbis.api.ServiceRegistry
                                                    .get(io.github.larsonix.trailoforbis.api.services.AttributeService.class)
                                                    .ifPresent(svc -> {
                                                        ComputedStats healed = svc.recalculateStats(healUuid);
                                                        if (healed != null) {
                                                            LOGGER.atInfo().log(
                                                                    "[DmgPipeline] Self-healed weapon stats for %s: weapon=%s",
                                                                    healUuid.toString().substring(0, 8),
                                                                    healed.getWeaponRawItemId());
                                                        }
                                                    });
                                        } catch (Exception e) {
                                            LOGGER.atWarning().withCause(e).log(
                                                    "[DmgPipeline] Self-heal recalc failed for %s", healUuid);
                                        }
                                    });
                                }
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
                // Best-effort detection — non-fatal
            }
        }

        // Defender is always per-target (never cached)
        ctx.defenderStats = statsResolver.getDefenderStats(index, archetypeChunk, store);
        ctx.defenderElemental = statsResolver.getDefenderElementalStats(index, archetypeChunk, store, ctx.defenderStats);
        ctx.defenderHealthPercent = entityResolver.getDefenderHealthPercent(index, archetypeChunk, store);
        ctx.defenderUuid = entityResolver.getDefenderUuid(index, archetypeChunk, store);

        // Check if this is a hex spell routed through the main pipeline.
        // Gate on HEX_SPELL_BASE_DAMAGE (always set for hex spells), not element
        // (element can be null for unmapped/default spell types).
        Float hexBaseMeta = damage.getIfPresentMetaObject(HEX_SPELL_BASE_DAMAGE);
        boolean isSpellDamage = (hexBaseMeta != null);

        if (isSpellDamage) {
            // ── Hex spell damage: per-glyph normalization via HexSpellNormalizer ──
            ElementType spellElementMeta = damage.getIfPresentMetaObject(HEX_SPELL_ELEMENT);
            ctx.attackType = AttackType.SPELL;
            ctx.isHexSpell = true;
            ctx.spellElement = spellElementMeta;
            ctx.rpgWeaponDamage = (ctx.attackerStats != null) ? ctx.attackerStats.getWeaponBaseDamage() : 0f;
            ctx.hasRpgWeapon = (ctx.attackerStats != null) && ctx.attackerStats.isHoldingRpgGear();
            ctx.attackTypeMultiplier = 1.0f;

            String spellSourceType = damage.getIfPresentMetaObject(HEX_SPELL_SOURCE_TYPE);

            // Resolve caster's base magic power (pre-echo)
            boolean isConstruct = HexDamageAttributor.isConstructSource(spellSourceType);
            float hexMagicPower;
            if (isConstruct && ctx.attackerUuid != null) {
                hexMagicPower = HexCastStateStore.getBasePowerForPlayer(ctx.attackerUuid);
            } else {
                hexMagicPower = HexCastStateStore.getCurrentBasePower();
            }

            // Compute RPG power from player stats
            float weaponBase = ctx.rpgWeaponDamage;
            float flatSpell = (ctx.attackerStats != null) ? ctx.attackerStats.getSpellDamage() : 0f;
            float flatElemental = 0f;
            if (spellElementMeta != null && ctx.attackerElemental != null) {
                flatElemental = (float) ctx.attackerElemental.getFlatDamage(spellElementMeta);
            }
            float ourRPGPower = Math.max(1f, weaponBase + flatSpell + flatElemental);

            // Per-glyph normalization: config-driven profile handles LINEAR/PHYSICS/FIXED
            HexcodeSpellConfig hexCfg = plugin.getConfigManager().getHexcodeSpellConfig();
            float slotDefault = (spellSourceType != null)
                    ? HexcodeCompat.getGlyphBasePower(spellSourceType) : 0f;

            if (hexCfg != null && spellSourceType != null && hexMagicPower > 0 && ctx.attackerStats != null) {
                HexcodeSpellConfig.SpellBalanceProfile profile =
                        hexCfg.getSpellBalance(spellSourceType, slotDefault);
                float glyphMultiplier = HexSpellNormalizer.normalize(
                        spellSourceType, hexBaseMeta, hexMagicPower, profile);
                ctx.rpgBaseDamage = ourRPGPower * glyphMultiplier;
            } else {
                // Fallback: unmapped glyph, no hex power, or mob caster
                ctx.rpgBaseDamage = hexBaseMeta;
            }
            ctx.hexBakedElement = spellElementMeta;
        } else {
            // ---- Normal melee/projectile/area path ----
            ctx.attackType = classifier.detectAttackType(store, damage);

            // Get RPG base damage and determine if using RPG weapon
            // CRITICAL: Use isHoldingRpgGear() to detect RPG weapons, NOT weaponBaseDamage > 0!
            // This ensures unequippable RPG weapons (which have damage=0) still use the RPG path.
            ctx.rpgWeaponDamage = (ctx.attackerStats != null) ? ctx.attackerStats.getWeaponBaseDamage() : 0f;
            ctx.hasRpgWeapon = (ctx.attackerStats != null) && ctx.attackerStats.isHoldingRpgGear();

            if (ctx.hasRpgWeapon) {
                // RPG weapon: use implicit damage as base, apply attack type multiplier
                ctx.rpgBaseDamage = ctx.rpgWeaponDamage;

                // Try to get attack type multiplier from vanilla weapon profile (geometric mean reference)
                // Only if vanilla weapon profiles are enabled in config
                boolean useVanillaProfiles = plugin.getGearManager() != null
                    && plugin.getGearManager().getBalanceConfig() != null
                    && plugin.getGearManager().getBalanceConfig().vanillaWeaponProfiles().enabled();

                String weaponItemId = (ctx.attackerStats != null) ? ctx.attackerStats.getWeaponItemId() : null;
                VanillaWeaponProfile weaponProfile = null;
                if (useVanillaProfiles && weaponItemId != null) {
                    weaponProfile = plugin.getGearManager().getVanillaWeaponProfile(weaponItemId);
                }

                if (!useVanillaProfiles) {
                    // Profiles disabled - no attack type differentiation, use RPG damage directly
                    ctx.attackTypeMultiplier = 1.0f;
                } else if (ctx.attackType == AttackType.PROJECTILE) {
                    // Projectiles have no per-attack differentiation (no light/heavy/backstab).
                    // Vanilla damage is already balanced by fire rate. Always 1.0.
                    ctx.attackTypeMultiplier = 1.0f;
                } else if (weaponProfile != null) {
                    // Use pre-computed multiplier from vanilla weapon profile
                    // This preserves vanilla attack differentiation (basic ~0.2x, signature ~2x, backstab ~4x)
                    float rawMult = weaponProfile.getAttackTypeMultiplier(ctx.vanillaDamage);
                    ctx.attackTypeMultiplier = plugin.getGearManager().getBalanceConfig()
                        .vanillaWeaponProfiles().clampMultiplier(rawMult);
                    ctx.traceWeaponProfile = weaponProfile;
                } else {
                    // Profiles enabled but this weapon doesn't have one - use fallback
                    float fallbackRef = (float) plugin.getGearManager().getBalanceConfig()
                        .vanillaWeaponProfiles().fallbackReferenceDamage();
                    ctx.attackTypeMultiplier = plugin.getGearManager().getBalanceConfig()
                        .vanillaWeaponProfiles().clampMultiplier(ctx.vanillaDamage / fallbackRef);
                    ctx.traceFallbackRef = fallbackRef;
                    // Log at WARNING only when profiles are enabled but lookup failed
                    LOGGER.at(Level.WARNING).log(
                        "Profile NOT FOUND: '%s', using fallback: vanilla=%.1f / ref=%.1f = %.2fx",
                        weaponItemId, ctx.vanillaDamage, fallbackRef, ctx.attackTypeMultiplier);
                }
            } else {
                // Vanilla weapon or unarmed: use vanilla damage directly, no attack type adjustment
                // (vanilla damage already includes attack type differentiation)
                ctx.rpgBaseDamage = mobDamageResolver.getWeaponBaseDamage(damage, ctx.attackerStats, ctx.vanillaDamage, store);
                ctx.attackTypeMultiplier = 1.0f;
            }

            // Magic weapon override: staff/wand melee attacks should use spell damage scaling.
            // Hytale fires staff attacks as "Physical" melee (EntitySource + DamageSequence),
            // but the player built into spellDamage/spellDamagePercent expecting spell scaling.
            if (ctx.hasRpgWeapon && ctx.attackerStats != null) {
                String weaponItemId = ctx.attackerStats.getWeaponItemId();
                WeaponType weaponType = WeaponType.fromItemIdOrUnknown(weaponItemId);
                if (weaponType.isMagic()) {
                    ctx.attackType = AttackType.SPELL;
                    // Use the weapon's fixed element if available, otherwise fall back to
                    // player's dominant attribute element (for legacy "spell_damage" staves)
                    ElementType fixedElement = ctx.attackerStats.getWeaponSpellElement();
                    ctx.spellElement = fixedElement != null ? fixedElement : mobDamageResolver.resolveDominantSpellElement(ctx.attackerElemental);
                    ctx.attackTypeMultiplier = 1.0f; // No light/heavy differentiation for spells
                    LOGGER.at(Level.FINE).log("[DmgPipeline] Magic weapon override: %s → SPELL (element=%s, fixed=%s)",
                        weaponItemId, ctx.spellElement != null ? ctx.spellElement.name() : "NONE",
                        fixedElement != null);
                }
            }
        }

        // Elemental physical weapon: base damage goes into element slot, attack type stays as-is.
        // This is separate from the magic weapon override above — swords/axes/bows with
        // elemental implicits (from loot discovery) keep MELEE/PROJECTILE attack type but
        // place their base damage in the element slot so it's mitigated by resistance, not armor.
        if (ctx.hasRpgWeapon && ctx.attackerStats != null && ctx.spellElement == null) {
            ElementType weaponElement = ctx.attackerStats.getWeaponSpellElement();
            if (weaponElement != null) {
                ctx.spellElement = weaponElement;
                LOGGER.at(Level.FINE).log("[DmgPipeline] Elemental weapon override: element=%s, attackType=%s",
                    weaponElement.name(), ctx.attackType);
            }
        }

        // Resolve attacker level: use cache or resolve fresh
        if (cacheHit) {
            ctx.attackerLevel = cachedAttackerCtx.attackerLevel();
        } else {
            ctx.attackerLevel = resolveAttackerLevel(store, damage);

            // Update attacker cache for subsequent targets in this AoE burst.
            // Only cache player attackers (mob attackers have per-entity stats, no sharing benefit).
            if (resolvedAttackerUuid != null) {
                Ref<EntityStore> currentAttackerRef = entityResolver.getAttackerRef(store, damage);
                cachedAttackerUuid = resolvedAttackerUuid;
                cachedAttackerCtx = new io.github.larsonix.trailoforbis.combat.context.AttackerContext(
                    currentAttackerRef, resolvedAttackerUuid,
                    ctx.attackerStats, ctx.attackerElemental, ctx.attackerLevel,
                    ctx.hasRpgWeapon, ctx.rpgWeaponDamage,
                    ctx.attackerStats != null ? ctx.attackerStats.getWeaponItemId() : null,
                    currentStatsVersion
                );
            }
        }

        // Log attack type
        LOGGER.at(Level.FINE).log("[DmgPipeline] Attack type: %s, attackerLevel: %d", ctx.attackType, ctx.attackerLevel);

        // Log attacker/defender stats (FINE-level)
        CombatDebugLogger.logAttackerStats(ctx.attackerStats, ctx.attackerElemental, ctx.hasRpgWeapon, ctx.rpgWeaponDamage);
        CombatDebugLogger.logDefenderStats(ctx.defenderStats, ctx.defenderElemental);

        // Calculate conditional multipliers with full detail for traced output
        ctx.conditionalDetail = multiplierCalculator.calculateDetailed(
            store, damage, ctx.attackerStats, ctx.defenderHealthPercent, ctx.defenderUuid, ctx.defenderRef);
        ctx.conditionalMultiplier = ctx.conditionalDetail.combined();

        damage.putMetaObject(ATTACK_TYPE, ctx.attackType);
    }

    /**
     * Resolves the attacker's level for the armor formula.
     * Tries player level first, then mob level from MobScalingComponent, falls back to 1.
     */
    private int resolveAttackerLevel(@Nonnull Store<EntityStore> store, @Nonnull Damage damage) {
        Ref<EntityStore> attackerRef = entityResolver.getAttackerRef(store, damage);
        if (attackerRef == null) {
            return 1;
        }

        // Try player level first
        PlayerRef attackerPlayerRef = store.getComponent(attackerRef, PlayerRef.getComponentType());
        if (attackerPlayerRef != null && attackerPlayerRef.isValid() && plugin.getLevelingManager() != null) {
            return plugin.getLevelingManager().getLevel(attackerPlayerRef.getUuid());
        }

        // Try mob level from MobScalingComponent
        ComponentType<EntityStore, MobScalingComponent> scalingType = plugin.getMobScalingComponentType();
        if (scalingType != null) {
            MobScalingComponent scaling = store.getComponent(attackerRef, scalingType);
            if (scaling != null) {
                return scaling.getMobLevel();
            }
        }

        return 1;
    }

    /**
     * Phase 4.5: Populates trace context fields — weapon profile derivation, attacker/defender levels.
     *
     * <p>Pure trace enrichment. No side effects on the damage calculation flow.
     */
    private void populateTraceContext(
        @Nonnull Store<EntityStore> store,
        @Nonnull Damage damage,
        @Nonnull DamageContext ctx
    ) {
        // Context fields
        String weaponItemId = (ctx.attackerStats != null) ? ctx.attackerStats.getWeaponItemId() : null;
        ctx.trace.setWeaponItemId(weaponItemId);
        ctx.trace.setDefenderMaxHealth(ctx.maxHealth);
        ctx.trace.setAttackSpeedPercent(ctx.attackerStats != null ? ctx.attackerStats.getAttackSpeedPercent() : 0f);
        ctx.trace.setAvoidanceStats(ctx.avoidanceStats);

        // Attack type derivation context
        if (ctx.traceWeaponProfile != null) {
            String atkName = ctx.traceWeaponProfile.attacks().stream()
                .filter(a -> Math.abs(a.damage() - ctx.vanillaDamage) < 0.01f)
                .map(VanillaAttackInfo::attackName)
                .findFirst().orElse(null);
            ctx.trace.setAttackTypeDerivation(ctx.vanillaDamage, ctx.traceWeaponProfile.referenceDamage(),
                ctx.traceWeaponProfile.minDamage(), ctx.traceWeaponProfile.maxDamage(), atkName);
        } else if (ctx.hasRpgWeapon && ctx.traceFallbackRef > 0) {
            ctx.trace.setAttackTypeDerivation(ctx.vanillaDamage, ctx.traceFallbackRef, 0f, 0f, null);
        }

        // Attacker level (already resolved in gatherCombatInputs for armor formula)
        ctx.trace.setAttackerLevel(ctx.attackerLevel);
        // Defender level
        if (ctx.defenderUuid != null && plugin.getLevelingManager() != null) {
            ctx.trace.setDefenderLevel(plugin.getLevelingManager().getLevel(ctx.defenderUuid));
        } else {
            ComponentType<EntityStore, MobScalingComponent> scalingType = plugin.getMobScalingComponentType();
            if (scalingType != null) {
                MobScalingComponent scaling = store.getComponent(ctx.defenderRef, scalingType);
                if (scaling != null) ctx.trace.setDefenderLevel(scaling.getMobLevel());
            }
        }
    }

    /**
     * Phase 5: Applies post-calculation modifications to the damage value.
     *
     * <p>Handles crit nullification, parry/shield/mana buffer/shock amplification,
     * unarmed penalty, DoT detonation on crit, and spell echo chance.
     * Mutates ctx.breakdown, ctx.rpgDamage, and ctx.wasParried.
     */
    private void applyPostCalcModifications(
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Damage damage,
        @Nonnull DamageContext ctx
    ) {
        // Critical immunity recalculation
        boolean critNullified = false;
        float critNullifyChance = ctx.defenderStats != null ? ctx.defenderStats.getCritNullifyChance() : 0f;
        if (ctx.breakdown.wasCritical() && ctx.defenderStats != null && critNullifyChance >= 100f) {
            ctx.breakdown = getRPGCalculator().calculateWithForcedCrit(
                ctx.rpgBaseDamage, ctx.attackerStats, ctx.attackerElemental, ctx.defenderStats, ctx.defenderElemental,
                ctx.attackType, false, ctx.attackerLevel);
            critNullified = true;
        }
        ctx.trace.setCritNullify(critNullifyChance, critNullified);

        if (ctx.breakdown.wasCritical()) {
            triggerHandler.fireOnCritTrigger(store, damage);
        }

        // Capture defense context
        if (ctx.defenderStats != null) {
            ctx.trace.setParryChance(ctx.defenderStats.getParryChance());
            ctx.trace.setDamageTakenModifier(ctx.defenderStats.getDamageTakenPercent());
        }
        if (ctx.attackerStats != null) {
            ctx.trace.setHealthRecoveryPercent(ctx.attackerStats.getHealthRecoveryPercent());
        }

        DamageModifierProcessor.ModificationResult modResult = modifierProcessor.applyModifications(
            ctx.breakdown, ctx.defenderStats, store, ctx.defenderRef, damage, ctx.defenderUuid);

        ctx.rpgDamage = modResult.finalDamage();
        ctx.breakdown = modResult.breakdown();
        ctx.wasParried = modResult.wasParried();

        // ── Combat Effect Hooks: Post-Calculation ──
        // Runs after calculator + defense modifications (parry, shield, shock) but before
        // realm modifiers and post-modification effects. Used by keystones that modify
        // the player's own damage (BerserkersRage, Rampage, SkyPiercer, BladeDance).
        if (combatEffectRegistry != null && ctx.attackerUuid != null) {
            CombatEffectContext effectCtx = ctx.toEffectContext(damage, store, commandBuffer);
            ctx.rpgDamage = combatEffectRegistry.runPostCalculation(ctx.attackerUuid, effectCtx);
        }

        // Unarmed damage penalty — players without weapons deal reduced damage (not for spells)
        if (!ctx.hasRpgWeapon && ctx.attackType != AttackType.SPELL
                && ctx.attackerStats != null && !ctx.attackerStats.isMobStats()) {
            float unarmedMult = plugin.getConfigManager().getRPGConfig().getCombat().getUnarmedDamageMultiplier();
            if (unarmedMult != 1.0f) {
                ctx.trace.setUnarmedPenalty(unarmedMult, ctx.rpgDamage);
                ctx.rpgDamage *= unarmedMult;
            }
        }

        // Realm PLAYER_VULNERABILITY — player takes increased damage
        if (ctx.defenderUuid != null && ctx.rpgDamage > 0) {
            int vulnPercent = realmModifierProcessor.getRealmPlayerVulnerability(ctx.defenderUuid);
            if (vulnPercent > 0) {
                ctx.rpgDamage *= (1.0f + vulnPercent / 100.0f);
            }
        }

        // Realm ARMORED_MONSTERS — mob takes reduced physical damage (extra armor)
        if (ctx.rpgDamage > 0 && ctx.defenderUuid == null) {
            // defenderUuid is null for mobs (only set for player defenders)
            int armorBonus = realmModifierProcessor.getRealmArmoredMonstersBonus(store, ctx.defenderRef);
            if (armorBonus > 0) {
                // Reduce damage as if mob had extra armor (physical attacks only)
                // Formula: 40% armor bonus = 20% less damage. Capped at 75% max reduction.
                if (ctx.attackType == AttackType.MELEE || ctx.attackType == AttackType.PROJECTILE) {
                    float reduction = Math.min(0.75f, armorBonus / 200.0f);
                    ctx.rpgDamage *= (1.0f - reduction);
                }
            }
        }

        // Realm MONSTERS_EXTRA_[ELEMENT] — mob deals bonus elemental damage to player
        if (ctx.defenderUuid != null && ctx.rpgDamage > 0 && ctx.defenderElemental != null) {
            float elementalBonus = realmModifierProcessor.calculateRealmElementalBonusDamage(
                store, damage, ctx.rpgDamage, ctx.defenderElemental);
            if (elementalBonus > 0) {
                ctx.rpgDamage += elementalBonus;
            }
        }

        // Populate trace with post-calc modifications
        ctx.trace.setShieldAbsorbed(ctx.breakdown.shieldAbsorbed());
        if (ctx.wasParried) {
            ctx.trace.setParried(true, modResult.parryReductionMult(), ctx.rpgDamage);
        }

        // Populate trace with ModificationResult detail
        if (ctx.defenderStats != null) {
            ctx.trace.setEnergyShieldDetail(ctx.defenderStats.getEnergyShield(), modResult.energyShieldBefore());
        }
        ctx.trace.setManaBuffer(modResult.manaBufferPercent(), modResult.manaAbsorbed());
        ctx.trace.setShockAmplification(modResult.shockBonusPercent(), modResult.damageBeforeShock());

        // ── Combat Effect Hooks: Post-Modifications ──
        // ChainDetonation (DoT burst on crit) and SpellEcho (spell repeat as Void)
        // are now handled by CombatEffect implementations registered in the registry.
        if (combatEffectRegistry != null) {
            CombatEffectContext effectCtx = ctx.toEffectContext(damage, store, commandBuffer);
            ctx.rpgDamage = combatEffectRegistry.runPostModifications(ctx.attackerUuid, effectCtx);
        }

        // Log damage breakdown (FINE-level)
        CombatDebugLogger.logDamageBreakdown(ctx.breakdown, ctx.rpgBaseDamage, ctx.attackTypeMultiplier, ctx.conditionalMultiplier,
            ctx.attackerStats, ctx.defenderStats, ctx.attackerElemental, ctx.defenderElemental, ctx.rpgDamage);
    }

    /**
     * Phase 6: Emits all combat feedback — metadata, stamina drain, ailments, indicators,
     * particles, death recap, combat logs, durability.
     */
    private void emitCombatFeedback(
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Damage damage,
        @Nonnull DamageContext ctx
    ) {
        setDamageMetadata(damage, ctx.breakdown, ctx.rpgDamage, ctx.wasParried);

        // Apply native stamina drain reduction via Hytale's STAMINA_DRAIN_MULTIPLIER
        if (ctx.defenderStats != null) {
            float staminaDrainReduction = ctx.defenderStats.getStaminaDrainReduction();
            if (staminaDrainReduction > 0) {
                float capped = Math.min(staminaDrainReduction, 75f);
                float ourMultiplier = 1.0f - (capped / 100f);
                // Combine with any existing multiplier (e.g. from attacker weapon effects)
                Float existing = damage.getIfPresentMetaObject(Damage.STAMINA_DRAIN_MULTIPLIER);
                float combined = (existing != null ? existing : 1.0f) * ourMultiplier;
                damage.putMetaObject(Damage.STAMINA_DRAIN_MULTIPLIER, combined);
            }
        }

        // Apply ailments BEFORE indicators so the trace has ailment data for display.
        // Gate on the ACTUAL hit damage (preDefenseElemental), not the player's flat elemental
        // stat sheet. Elemental weapons place base damage in the element slot and conversion
        // moves physical→elemental — neither appears in ElementalStats.flatDamage, so the old
        // hasAnyElementalDamage() gate blocked ailments for elemental weapons and conversion builds.
        EnumMap<ElementType, Float> preDefElem = ctx.breakdown.preDefenseElemental();
        boolean hitDealtElemental = false;
        for (float v : preDefElem.values()) {
            if (v > 0) { hitDealtElemental = true; break; }
        }
        if (hitDealtElemental) {
            ElementalStats elemForAilment = ctx.attackerElemental != null
                ? ctx.attackerElemental : new ElementalStats();
            AilmentSummary ailmentSummary = ailmentApplicator.tryApplyAilments(index, archetypeChunk, store, damage,
                elemForAilment, ctx.attackerStats, ctx.maxHealth, ctx.defenderStats, commandBuffer,
                preDefElem);
            ctx.trace.setAilmentSummary(ailmentSummary);
        }

        // Set damage cause index for spell element-colored screen flash.
        // Must be set BEFORE indicators so the flash uses the correct color.
        if (ctx.attackType == AttackType.SPELL) {
            int causeIndex = ctx.breakdown.damageType().getCauseIndex(ctx.breakdown.wasCritical());
            if (causeIndex != Integer.MIN_VALUE) {
                damage.setDamageCauseIndex(causeIndex);
            }
        }

        // Get target info for attacker's combat log (shows who they hit)
        DeathRecapRecorder.AttackerInfo targetInfo = deathRecapRecorder.getTargetInfo(store, ctx.defenderRef);

        // Send indicators with full trace for detailed chat logs
        indicatorService.sendDamageIndicators(store, ctx.defenderRef, damage, ctx.rpgDamage, ctx.breakdown, ctx.wasParried,
            targetInfo, ctx.trace);
        damage.putMetaObject(INDICATORS_SENT, true);

        // Spawn impact particles and sounds (restores vanilla on-hit feedback + RPG effects)
        feedbackService.onDamageDealt(store, ctx.defenderRef, ctx.breakdown, ctx.rpgDamage);

        // Record damage for death recap — spells use the spell display name, not caster entity name
        if (ctx.attackType == AttackType.SPELL) {
            String spellDisplayName = damage.getIfPresentMetaObject(HEX_SPELL_DISPLAY_NAME);
            if (spellDisplayName == null) spellDisplayName = "Hex Spell";
            DeathRecapRecorder.AttackerInfo spellAttackerInfo = new DeathRecapRecorder.AttackerInfo(
                spellDisplayName, "spell", 0, null);
            deathRecapRecorder.recordDamageWithAttacker(index, archetypeChunk, store, spellAttackerInfo,
                ctx.breakdown, ctx.rpgBaseDamage, ctx.defenderStats, ctx.attackerStats, ctx.maxHealth, ctx.healthBefore);
        } else {
            deathRecapRecorder.recordDamage(index, archetypeChunk, store, damage, ctx.breakdown, ctx.rpgBaseDamage,
                ctx.defenderStats, ctx.attackerStats, ctx.maxHealth, ctx.healthBefore);
        }

        // Send damage received log to defender (shows them what hit them)
        DeathRecapRecorder.AttackerInfo attackerInfo = deathRecapRecorder.getAttackerInfo(store, damage);

        // Extract raw elemental resistances for combat log display
        Map<ElementType, Float> defenderRawResistances = CombatDebugLogger.extractRawResistances(ctx.defenderElemental);
        Map<ElementType, Float> attackerElemPenetration = CombatDebugLogger.extractPenetration(ctx.attackerElemental);

        CombatSnapshot snapshot = CombatSnapshot.fromBreakdown(
            ctx.breakdown, attackerInfo.name(), attackerInfo.type(), attackerInfo.level(), attackerInfo.mobClass(),
            ctx.rpgBaseDamage, ctx.maxHealth, ctx.healthBefore,
            ctx.defenderStats != null ? ctx.defenderStats.getEvasion() : 0f,
            ctx.defenderStats != null ? ctx.defenderStats.getArmor() : 0f,
            ctx.attackerStats != null ? ctx.attackerStats.getArmorPenetration() : 0f,
            defenderRawResistances, attackerElemPenetration);
        indicatorService.sendDamageReceivedLog(store, ctx.defenderRef, snapshot, ctx.trace);

        // Spells don't damage the caster's weapon (no physical contact)
        if (ctx.attackType != AttackType.SPELL) {
            durabilityHandler.handleDurability(index, archetypeChunk, store, commandBuffer, damage);
        }

        // Block heal/counter — uses PRE-reduction damage (rewards the block action).
        // Runs in Phase 6 because applyBlockHeal needs index + archetypeChunk.
        if (ctx.trace.wasActiveBlocking() && ctx.defenderStats != null) {
            float preBlockDamage = ctx.trace.damageBeforeBlock();

            float blockHealPct = ctx.defenderStats.getBlockHealPercent();
            if (blockHealPct > 0 && preBlockDamage > 0) {
                float healAmount = preBlockDamage * (blockHealPct / 100f);
                float recoveryMult = ctx.defenderStats.getHealthRecoveryPercent();
                if (recoveryMult != 0) {
                    healAmount *= (1f + recoveryMult / 100f);
                }
                // Apply realm REDUCED_HEALING modifier (defender is the healer)
                healAmount *= realmModifierProcessor.getRealmHealingMultiplier(ctx.defenderUuid);
                if (healAmount > 0) {
                    recoveryProcessor.applyBlockHeal(index, archetypeChunk, store, healAmount);
                }
            }

            float counterPct = ctx.defenderStats.getBlockCounterDamage();
            if (counterPct > 0 && preBlockDamage > 0) {
                float counterDamage = preBlockDamage * (counterPct / 100f);
                avoidanceResultHandler.applyBlockCounterDamage(store, damage, counterDamage);
            }

            // Fire block trigger for conditional effects
            triggerHandler.fireOnBlockTrigger(index, archetypeChunk, store);

            // Mark for death recap
            damage.putMetaObject(WAS_BLOCKED, true);
        }
    }

    /**
     * Phase 7: Processes recovery (life/mana leech and steal) and thorns reflection.
     */
    private void processRecoveryAndThorns(
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Damage damage,
        @Nonnull DamageContext ctx
    ) {
        // Set hinted attacker for recovery processor (avoids 6 redundant resolveTrueAttacker calls)
        Ref<EntityStore> attackerRef = entityResolver.getAttackerRef(store, damage);
        recoveryProcessor.setHintedAttacker(attackerRef);
        try {
        processRecoveryAndThornsInner(store, commandBuffer, damage, ctx);
        } finally {
            recoveryProcessor.clearHintedAttacker();
        }
    }

    private void processRecoveryAndThornsInner(
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Damage damage,
        @Nonnull DamageContext ctx
    ) {
        // Leech = gain resources from YOUR damage output (always works)
        // Steal = take resources FROM enemy (enemy loses what you gain)
        if (ctx.attackerStats != null && ctx.rpgDamage > 0) {
            float healthRecoveryPct = ctx.attackerStats.getHealthRecoveryPercent();
            boolean didHealHealth = false;

            // Use pre-resolved attacker UUID from context (avoids redundant resolution)
            float realmHealMult = realmModifierProcessor.getRealmHealingMultiplier(ctx.attackerUuid);

            // Berserker's Rage: life steal capped at 50% max HP
            float leechCap = realmModifierProcessor.getBerserkersRageLeechCap(ctx.attackerUuid, store, damage);

            // Life Leech - always heals from damage dealt
            float lifeLeech = ctx.attackerStats.getLifeLeech();
            if (lifeLeech > 0) {
                float healAmount = ctx.rpgDamage * (lifeLeech / 100f);
                if (healthRecoveryPct != 0) {
                    healAmount *= (1.0f + healthRecoveryPct / 100.0f);
                }
                healAmount *= realmHealMult;
                healAmount = Math.min(healAmount, leechCap);
                ctx.trace.setLifeLeech(lifeLeech, healAmount);
                if (healAmount > 0) {
                    recoveryProcessor.applyLifeLeech(store, damage, healAmount);
                    didHealHealth = true;
                }
            }

            // Mana Leech - always restores mana from damage dealt
            float manaLeech = ctx.attackerStats.getManaLeech();
            if (manaLeech > 0) {
                float manaAmount = ctx.rpgDamage * (manaLeech / 100f);
                ctx.trace.setManaLeech(manaLeech, manaAmount);
                recoveryProcessor.applyManaLeech(store, damage, manaAmount);
            }

            // Life Steal - heals AND enemy takes bonus damage (bonus damage handled in calc)
            float lifeSteal = ctx.attackerStats.getLifeSteal();
            if (lifeSteal > 0) {
                float healAmount = ctx.rpgDamage * (lifeSteal / 100f);
                if (healthRecoveryPct != 0) {
                    healAmount *= (1.0f + healthRecoveryPct / 100.0f);
                }
                healAmount *= realmHealMult;
                healAmount = Math.min(healAmount, leechCap);
                ctx.trace.setLifeSteal(lifeSteal, healAmount);
                if (healAmount > 0) {
                    recoveryProcessor.applyLifeSteal(store, damage, healAmount);
                    didHealHealth = true;
                }
            }

            // Mana Steal - attacker gains AND enemy loses mana (only if enemy has mana)
            float manaSteal = ctx.attackerStats.getManaSteal();
            if (manaSteal > 0) {
                float manaAmount = ctx.rpgDamage * (manaSteal / 100f);
                ctx.trace.setManaSteal(manaSteal, manaAmount);
                recoveryProcessor.applyManaSteal(store, damage, manaAmount, ctx.defenderRef);
            }

            // Recovery feedback for attacker if any HP was recovered
            if (didHealHealth) {
                Ref<EntityStore> attackerRef = entityResolver.getAttackerRef(store, damage);
                if (attackerRef != null) {
                    feedbackService.onRecovery(store, attackerRef);

                    // Show total HP recovered as self-floating text (no sound)
                    float totalHeal = ctx.trace.lifeLeechAmount() + ctx.trace.lifeStealAmount();
                    if (totalHeal > 0) {
                        indicatorService.sendRecoveryCombatText(store, attackerRef, totalHeal);
                    }
                }
            }
        }

        // Thorns Application
        // After damage is dealt, if defender has thorns/reflect stats, deal damage back to attacker.
        // Formula: thornsDamage x (1 + thornsDamagePercent/100) + damageTaken x (reflectDamagePercent/100)
        // Thorns is lethal — attacker can be killed by thorns damage.
        if (ctx.defenderStats != null && ctx.rpgDamage > 0) {
            float thornsDamage = ctx.defenderStats.getThornsDamage();
            float reflectPercent = ctx.defenderStats.getReflectDamagePercent();

            // Add Reflective mob modifier reflect% if defender is a modified mob
            if (ctx.defenderRef != null && ctx.defenderRef.isValid()) {
                ComponentType<EntityStore, MobModifierComponent> modType = plugin.getMobModifierComponentType();
                if (modType != null) {
                    MobModifierComponent mobMods = store.getComponent(ctx.defenderRef, modType);
                    if (mobMods != null && mobMods.hasModifier(ModifierType.REFLECTIVE)) {
                        float modReflect = (float) (ModifierType.REFLECTIVE.getStatBonus().reflectPercent() * 100);
                        reflectPercent += modReflect;
                    }
                }
            }

            if (thornsDamage > 0 || reflectPercent > 0) {
                float thornsPercent = ctx.defenderStats.getThornsDamagePercent();
                float flatThorns = thornsDamage * (1f + thornsPercent / 100f);
                float reflected = ctx.rpgDamage * (reflectPercent / 100f);
                ctx.trace.setThorns(thornsDamage, thornsPercent, reflectPercent, flatThorns + reflected);
                Ref<EntityStore> killedAttacker = recoveryProcessor.applyThornsDamage(
                    store, damage, ctx.defenderStats, ctx.rpgDamage);

                // Thorns was lethal — trigger death on the attacker
                if (killedAttacker != null && killedAttacker.isValid()) {
                    ComponentType<EntityStore, MobScalingComponent> scalingType = plugin.getMobScalingComponentType();
                    if (scalingType != null) {
                        MobScalingComponent scaling = store.getComponent(killedAttacker, scalingType);
                        if (scaling != null) {
                            scaling.setDying(true);
                        }
                    }
                    markMobDamaged(killedAttacker, store);
                    DeathComponent.tryAddComponent(commandBuffer, killedAttacker, damage);
                    LOGGER.atInfo().log("Thorns killed attacker — DeathComponent added");
                }
            }

            // Block heal/counter handled in emitCombatFeedback (Phase 6) which has index/archetypeChunk
        }

        // ── Combat Effect Hooks: Recovery ──
        if (combatEffectRegistry != null && ctx.rpgDamage > 0) {
            CombatEffectContext effectCtx = ctx.toEffectContext(damage, store, null);
            float extraRecovery = combatEffectRegistry.runOnRecovery(ctx.attackerUuid, effectCtx);
            if (extraRecovery > 0 && ctx.attackerStats != null) {
                // Apply Health Recovery multiplier (consistent with leech/steal paths)
                float recoveryPct = ctx.attackerStats.getHealthRecoveryPercent();
                if (recoveryPct != 0) {
                    extraRecovery *= (1.0f + recoveryPct / 100.0f);
                }
                recoveryProcessor.applyLifeLeech(store, damage, extraRecovery);
            }
        }
    }

    /**
     * Phase 7.9: Stat breakdown attribution, EntityStatsOnHit processing, and final damage application.
     */
    private void finalizeAndApply(
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Damage damage,
        @Nonnull DamageContext ctx
    ) {
        // Fetch per-source stat breakdowns for trace — only when combat detail mode is on.
        // This costs ~1ms per player and is only used for the /combatdetail chat breakdown.
        // For AoE (40+ hits), skipping this saves ~40-80ms.
        boolean needsBreakdown = (ctx.attackerUuid != null && plugin.isCombatDetailEnabled(ctx.attackerUuid))
            || (ctx.defenderUuid != null && plugin.isCombatDetailEnabled(ctx.defenderUuid));
        if (needsBreakdown) {
            Ref<EntityStore> traceAttackerRef = entityResolver.getAttackerRef(store, damage);
            if (traceAttackerRef != null) {
                PlayerRef attackerPlayer = store.getComponent(traceAttackerRef, PlayerRef.getComponentType());
                if (attackerPlayer != null && attackerPlayer.isValid()) {
                    ctx.trace.setAttackerBreakdown(
                        plugin.getAttributeManager().getStatBreakdown(attackerPlayer.getUuid()));
                }
            }
            PlayerRef defenderPlayer = store.getComponent(ctx.defenderRef, PlayerRef.getComponentType());
            if (defenderPlayer != null && defenderPlayer.isValid()) {
                ctx.trace.setDefenderBreakdown(
                    plugin.getAttributeManager().getStatBreakdown(defenderPlayer.getUuid()));
            }
        }

        if (ctx.attackType == AttackType.SPELL && ctx.isHexSpell) {
            // ── Two-Layer Volatility Damage Scaling ──
            // Layer 1 (across-cast): startingBudget / volatilityMax — penalizes repeated casting
            //   without redrawing. Exponent from config (default 1.5).
            // Layer 2 (within-cast): remainingBudget / startingBudget — penalizes later glyphs
            //   in a multi-glyph chain. Linear by default (exponent 1.0).
            // Construct damage (Glaciate, Ensnare, Phase) fires on later ticks without the
            // active tracker — both ratios return 1.0, which is correct (construct balance
            // is mana-based via CostScaledGlyphWrapper).
            HexcodeSpellConfig hexCfg = plugin.getConfigManager().getHexcodeSpellConfig();

            // Layer 1: across-cast decay
            float acrossCastRatio = HexCastStateStore.getVolatilityRatio();
            float acrossMult = 1.0f;
            if (acrossCastRatio < 1.0f) {
                float acrossExp = hexCfg != null ? hexCfg.getVolatilityRatioExponent() : 1.5f;
                acrossMult = (float) Math.pow(acrossCastRatio, acrossExp);
            }

            // Layer 2: within-cast depletion
            float withinMult = 1.0f;
            if (hexCfg == null || hexCfg.isWithinCastVolatilityScaling()) {
                float withinCastRatio = HexCastStateStore.getWithinCastVolatilityRatio();
                if (withinCastRatio < 1.0f) {
                    float withinExp = hexCfg != null ? hexCfg.getWithinCastExponent() : 1.0f;
                    withinMult = (withinExp == 1.0f)
                            ? withinCastRatio
                            : (float) Math.pow(withinCastRatio, withinExp);
                }
            }

            float combinedMult = acrossMult * withinMult;
            if (combinedMult < 1.0f) {
                ctx.rpgDamage *= combinedMult;
                LOGGER.atFine().log("[SpellDmg] Volatility scaling: across=%.2f within=%.2f → combined=%.3f → damage=%.0f",
                        acrossCastRatio, HexCastStateStore.getWithinCastVolatilityRatio(),
                        combinedMult, ctx.rpgDamage);
            }

            // Hex spells: set amount to RPG value and let Hytale's pipeline apply health subtraction.
            // This preserves downstream Hexcode systems (Erode, Fortify in FilterDamageGroup)
            // that process the damage event after us. Do NOT cancel the event.
            // No manual EntityStatsOnHit needed — amount is non-zero so vanilla handles it,
            // and spells have no DamageSequence anyway (EnvironmentSource origin).
            damage.setAmount(ctx.rpgDamage);

            // Set isDying flag on lethal spell damage — same race condition as melee:
            // MobRegenerationSystem and MobLevelRefreshSystem run before CommandBuffer commits
            // the DeathComponent, so without this flag they'd heal/refresh a dying mob.
            if (ctx.rpgDamage >= ctx.healthBefore) {
                ComponentType<EntityStore, MobScalingComponent> scalingType = plugin.getMobScalingComponentType();
                if (scalingType != null) {
                    MobScalingComponent scaling = store.getComponent(ctx.defenderRef, scalingType);
                    if (scaling != null) {
                        scaling.setDying(true);
                    }
                }
                markMobDamaged(ctx.defenderRef, store);
                fireModifierDeathTriggers(ctx.defenderRef, store);
                triggerHandler.fireOnKillTrigger(store, damage);
                // ── Combat Effect Hooks: On Kill ──
                if (combatEffectRegistry != null && ctx.attackerUuid != null) {
                    float overkill = ctx.rpgDamage - ctx.healthBefore;
                    CombatEffectContext effectCtx = ctx.toEffectContext(damage, store, commandBuffer);
                    combatEffectRegistry.runOnKill(ctx.attackerUuid, ctx.defenderUuid != null ? ctx.defenderUuid : new UUID(0,0), overkill, effectCtx);
                }
            } else {
                triggerHandler.fireWhenHitTrigger(index, archetypeChunk, store);
            }
            markMobDamaged(ctx.defenderRef, store);

            LOGGER.at(Level.FINE).log("[SpellDmg] Final: rpg=%.1f, passing to Hytale pipeline (uncancelled)",
                ctx.rpgDamage);
        } else {
            // Melee/projectile/area/staff-spell: cancel vanilla and apply damage directly.
            // Staff spells use this path (not the hex path above) because they originate as
            // EntitySource with DamageSequence — if left uncancelled, FilterDamageGroup would
            // double-apply armor reduction on top of our RPG defenses.
            // Vanilla's SequenceModifier skips EntityStatsOnHit processing when damage.amount == 0.
            // Since we zero the amount to prevent double-application, we must manually invoke
            // the on-hit stat gains (SignatureEnergy, etc.) for the attacker.
            processEntityStatsOnHitManually(store, commandBuffer, damage);

            applyFinalDamage(store, commandBuffer, damage, ctx.statMap, ctx.healthIndex, ctx.healthBefore, ctx.rpgDamage, index, archetypeChunk);

            // ── Combat Effect Hooks: On Kill (melee/projectile/staff-spell) ──
            if (combatEffectRegistry != null && ctx.attackerUuid != null && ctx.rpgDamage >= ctx.healthBefore) {
                float overkill = ctx.rpgDamage - ctx.healthBefore;
                CombatEffectContext effectCtx = ctx.toEffectContext(damage, store, commandBuffer);
                combatEffectRegistry.runOnKill(ctx.attackerUuid,
                    ctx.defenderUuid != null ? ctx.defenderUuid : new UUID(0, 0),
                    overkill, effectCtx);
            }
        }
    }

    // ==================== Entity Stats On Hit ====================

    /**
     * Manually processes EntityStatsOnHit for the attacker (e.g., SignatureEnergy gain).
     *
     * <p><b>Why this is needed:</b> Our system sets {@code damage.setAmount(0)} to prevent
     * vanilla's {@code DamageSystems.ApplyDamage} from double-applying damage. However,
     * vanilla's {@code SequenceModifier} (which handles EntityStatsOnHit) guards with
     * {@code if (!(damage.getAmount() <= 0.0F))} — so it skips processing when amount is 0.
     *
     * <p>This method replicates the relevant part of {@code SequenceModifier.handle()}:
     * incrementing the sequential hit counter and calling
     * {@code processEntityStatsOnHit(hits, attackerStatMap)} for each on-hit entry.
     *
     * @param store The entity store
     * @param commandBuffer The command buffer (used to get attacker's EntityStatMap)
     * @param damage The damage event
     */
    private void processEntityStatsOnHitManually(
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Damage damage
    ) {
        DamageCalculatorSystems.DamageSequence damageSequence =
            damage.getIfPresentMetaObject(DamageCalculatorSystems.DAMAGE_SEQUENCE);
        if (damageSequence == null) {
            return;
        }

        // Increment the hit counter (same as SequenceModifier does)
        damageSequence.addSequentialHit();

        DamageEntityInteraction.EntityStatOnHit[] entityStatsOnHit = damageSequence.getEntityStatOnHit();
        if (entityStatsOnHit == null) {
            return;
        }

        if (!(damage.getSource() instanceof Damage.EntitySource entitySource)) {
            return;
        }

        Ref<EntityStore> sourceRef = entitySource.getRef();
        if (sourceRef == null || !sourceRef.isValid()) {
            return;
        }

        EntityStatMap sourceStatMap = commandBuffer.getComponent(sourceRef, EntityStatMap.getComponentType());
        if (sourceStatMap == null) {
            return;
        }

        int hits = damageSequence.getSequentialHits();
        for (DamageEntityInteraction.EntityStatOnHit statOnHit : entityStatsOnHit) {
            statOnHit.processEntityStatsOnHit(hits, sourceStatMap);
        }

        LOGGER.at(Level.FINE).log("Processed EntityStatsOnHit manually: %d entries, %d hits",
            entityStatsOnHit.length, hits);
    }

    // ==================== Helper Methods ====================

    /**
     * Checks if both attacker and defender are realm mobs.
     * Realm mobs should never damage each other — only target players.
     *
     * @param store The entity store
     * @param defenderRef The defender entity reference
     * @param damage The damage event
     * @return true if this is realm mob-vs-mob damage that should be cancelled
     */
    private boolean isRealmMobVsMob(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> defenderRef,
        @Nonnull Damage damage
    ) {
        RealmMobComponent defenderRealm = store.getComponent(defenderRef, RealmMobComponent.getComponentType());
        if (defenderRealm == null) {
            return false;
        }

        // Defender is a realm mob — check if attacker is too
        Ref<EntityStore> attackerRef = entityResolver.getAttackerRef(store, damage);
        if (attackerRef == null || !attackerRef.isValid()) {
            return false;
        }

        RealmMobComponent attackerRealm = store.getComponent(attackerRef, RealmMobComponent.getComponentType());
        return attackerRealm != null;
    }

    /**
     * Checks if damage between two players should be blocked by party PvP protection.
     * Returns false if either entity is not a player, or if no party mod is active.
     */
    private boolean isPartyPvpBlocked(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> defenderRef,
        @Nonnull Damage damage
    ) {
        // Defender must be a player
        com.hypixel.hytale.server.core.universe.PlayerRef defenderPlayerRef =
            store.getComponent(defenderRef, com.hypixel.hytale.server.core.universe.PlayerRef.getComponentType());
        if (defenderPlayerRef == null) {
            return false;
        }

        // Attacker must be a player
        Ref<EntityStore> attackerRef = entityResolver.getAttackerRef(store, damage);
        if (attackerRef == null || !attackerRef.isValid()) {
            return false;
        }
        com.hypixel.hytale.server.core.universe.PlayerRef attackerPlayerRef =
            store.getComponent(attackerRef, com.hypixel.hytale.server.core.universe.PlayerRef.getComponentType());
        if (attackerPlayerRef == null) {
            return false;
        }

        // Check party integration
        java.util.Optional<io.github.larsonix.trailoforbis.compat.party.PartyIntegrationManager> partyOpt =
            io.github.larsonix.trailoforbis.api.ServiceRegistry.get(
                io.github.larsonix.trailoforbis.compat.party.PartyIntegrationManager.class);
        if (partyOpt.isEmpty()) {
            return false;
        }

        return partyOpt.get().shouldBlockPvp(attackerPlayerRef.getUuid(), defenderPlayerRef.getUuid());
    }


    private void setDamageMetadata(
        @Nonnull Damage damage,
        @Nonnull DamageBreakdown breakdown,
        float rpgDamage,
        boolean wasParried
    ) {
        DamageType damageType = breakdown.damageType();
        boolean wasCrit = breakdown.wasCritical();

        damage.setAmount(0);
        damage.putMetaObject(DAMAGE_TYPE, damageType);
        damage.putMetaObject(WAS_CRITICAL, wasCrit);
        damage.putMetaObject(RPG_DAMAGE_VALUE, rpgDamage);
        if (wasParried) {
            damage.putMetaObject(WAS_PARRIED, true);
        }

        int causeIndex = damageType.getCauseIndex(wasCrit);
        if (causeIndex != Integer.MIN_VALUE) {
            damage.setDamageCauseIndex(causeIndex);
        }

        String critLabel = !wasCrit ? "" : breakdown.critTier() > 1
            ? " (CRIT T" + breakdown.critTier() + ")" : " (CRIT)";
        LOGGER.at(Level.FINE).log("Damage: %.1f %s%s", rpgDamage, damageType, critLabel);
    }


    private void applyFinalDamage(
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Damage damage,
        @Nonnull EntityStatMap statMap,
        int healthIndex,
        float healthBefore,
        float rpgDamage,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk
    ) {
        float newHealth = Math.max(0f, healthBefore - rpgDamage);

        // ALWAYS cancel vanilla damage processing - RPG system handles everything
        damage.setCancelled(true);

        if (newHealth <= 0) {
            // Lethal damage - drain health and trigger death directly
            statMap.subtractStatValue(healthIndex, healthBefore);

            // Add death component with our damage as the cause
            // This triggers Hytale's death processing (respawn menu, drops, etc.)
            Ref<EntityStore> defenderRef = archetypeChunk.getReferenceTo(index);
            if (defenderRef != null && defenderRef.isValid()) {
                // CRITICAL: Set isDying flag IMMEDIATELY to prevent race condition
                // The DeathComponent is added via CommandBuffer (deferred), but other systems
                // (MobRegenerationSystem, MobLevelRefreshSystem) run before the commit.
                // This flag provides an immediate death signal.
                ComponentType<EntityStore, MobScalingComponent> scalingType = plugin.getMobScalingComponentType();
                if (scalingType != null) {
                    MobScalingComponent scaling = store.getComponent(defenderRef, scalingType);
                    if (scaling != null) {
                        scaling.setDying(true);
                    }
                }
                markMobDamaged(defenderRef, store);
                fireModifierDeathTriggers(defenderRef, store);

                DeathComponent.tryAddComponent(commandBuffer, defenderRef, damage);
            }

            LOGGER.at(Level.FINE).log("Lethal RPG damage: %.1f, triggering death directly", rpgDamage);
            triggerHandler.fireOnKillTrigger(store, damage);
            return;
        }

        // Non-lethal damage - subtract RPG calculated damage from health
        statMap.subtractStatValue(healthIndex, healthBefore - newHealth);
        LOGGER.at(Level.FINE).log("Damage applied: rpg=%.1f, health: %.1f -> %.1f", rpgDamage, healthBefore, newHealth);

        // Mark mob as damaged for modifier tracking (Regenerating idle timer)
        Ref<EntityStore> defenderRefNonLethal = archetypeChunk.getReferenceTo(index);
        if (defenderRefNonLethal != null) {
            markMobDamaged(defenderRefNonLethal, store);
        }

        triggerHandler.fireWhenHitTrigger(index, archetypeChunk, store);
    }

    // ==================== Modifier Damage Tracking ====================

    /**
     * Marks a mob as recently damaged for the modifier system.
     *
     * <p>Called on EVERY damage path (lethal and non-lethal) so that
     * the Regenerating modifier's idle timer resets correctly.
     * Without this, Regenerating mobs would always heal because the
     * lastDamageTimestamp would never be set.
     *
     * @param defenderRef The entity that took damage
     * @param store       The entity store
     */
    private void markMobDamaged(@Nonnull Ref<EntityStore> defenderRef, @Nonnull Store<EntityStore> store) {
        ComponentType<EntityStore, MobModifierComponent> modType = plugin.getMobModifierComponentType();
        if (modType == null) return;
        MobModifierComponent modComp = store.getComponent(defenderRef, modType);
        if (modComp != null) {
            modComp.markDamaged();
        }
    }

    /**
     * Fires death triggers for modified mobs.
     *
     * <p>Called when a modified mob's isDying flag is set. Delegates to
     * {@link MobModifierDeathHandler} which queues effects (Volatile explosion,
     * Summoner cleanup) without modifying the store during damage processing.
     */
    private void fireModifierDeathTriggers(
            @Nonnull Ref<EntityStore> defenderRef,
            @Nonnull Store<EntityStore> store) {
        MobModifierManager modManager = plugin.getMobModifierManager();
        if (modManager == null || !modManager.isEnabled()) return;

        ComponentType<EntityStore, MobModifierComponent> modType = plugin.getMobModifierComponentType();
        if (modType == null) return;

        MobModifierComponent modComp = store.getComponent(defenderRef, modType);
        if (modComp == null || !modComp.hasAnyDeathTrigger()) return;

        ComponentType<EntityStore, MobScalingComponent> scalingType = plugin.getMobScalingComponentType();
        MobScalingComponent scaling = scalingType != null ? store.getComponent(defenderRef, scalingType) : null;
        if (scaling == null) return;

        modManager.getDeathHandler().onMobDeath(defenderRef, modComp, scaling, store);
    }






}
