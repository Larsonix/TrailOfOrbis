package io.github.larsonix.trailoforbis.combat;

import com.hypixel.hytale.logger.HytaleLogger;
import java.util.logging.Level;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.damage.DamageDataComponent;
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
import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.elemental.ElementType;
import io.github.larsonix.trailoforbis.elemental.ElementalStats;
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
import io.github.larsonix.trailoforbis.compat.HexCastEventInterceptor;
import io.github.larsonix.trailoforbis.compat.HexcodeCompat;
import io.github.larsonix.trailoforbis.compat.HexcodeSpellConfig;
import io.github.larsonix.trailoforbis.config.RPGConfig;
import io.github.larsonix.trailoforbis.mobs.classification.RPGMobClass;
import io.github.larsonix.trailoforbis.mobs.component.MobScalingComponent;
import io.github.larsonix.trailoforbis.mobs.model.MobStats;
import io.github.larsonix.trailoforbis.mobs.stats.MobStatPoolConfig;

import io.github.larsonix.trailoforbis.attributes.breakdown.StatBreakdownResult;
import io.github.larsonix.trailoforbis.combat.ailments.CombatAilmentApplicator;
import io.github.larsonix.trailoforbis.combat.ailments.CombatAilmentApplicator.AilmentSummary;
import io.github.larsonix.trailoforbis.combat.avoidance.AvoidanceProcessor;
import io.github.larsonix.trailoforbis.combat.deathrecap.CombatSnapshot;
import io.github.larsonix.trailoforbis.combat.deathrecap.DeathRecapRecorder;
import io.github.larsonix.trailoforbis.combat.detection.DamageTypeClassifier;
import io.github.larsonix.trailoforbis.combat.durability.DurabilityHandler;
import io.github.larsonix.trailoforbis.combat.indicators.CombatIndicatorService;
import io.github.larsonix.trailoforbis.combat.indicators.CombatIndicatorService.CombatTextParams;
import io.github.larsonix.trailoforbis.combat.modifiers.ConditionalMultiplierCalculator;
import io.github.larsonix.trailoforbis.combat.modifiers.ConditionalResult;
import io.github.larsonix.trailoforbis.combat.modifiers.DamageModifierProcessor;
import io.github.larsonix.trailoforbis.combat.tracking.ConsecutiveHitTracker;
import io.github.larsonix.trailoforbis.combat.recovery.CombatRecoveryProcessor;
import io.github.larsonix.trailoforbis.combat.resolution.CombatEntityResolver;
import io.github.larsonix.trailoforbis.combat.resolution.CombatStatsResolver;
import io.github.larsonix.trailoforbis.combat.triggers.CombatTriggerHandler;
import io.github.larsonix.trailoforbis.maps.components.RealmMobComponent;
import io.github.larsonix.trailoforbis.sanctum.components.SkillNodeComponent;
import io.github.larsonix.trailoforbis.skilltree.NodeState;
import io.github.larsonix.trailoforbis.sanctum.ui.SkillNodeDetailHud;
import io.github.larsonix.trailoforbis.sanctum.ui.SkillNodeHudManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import io.github.larsonix.trailoforbis.skilltree.SkillTreeManager;
import io.github.larsonix.trailoforbis.skilltree.config.SkillNode;

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

    // ── Active Blocking Reduction ──
    // Deterministic damage reduction when holding block (right-click).
    // Separate from BLOCK_CHANCE (perfect block = full avoidance in avoidance pipeline).

    /** Base damage reduction when weapon-blocking (right-click with weapon). */
    private static final float WEAPON_BLOCK_REDUCTION = 0.33f;
    /** Base damage reduction when shield-blocking (shield in utility slot). */
    private static final float SHIELD_BLOCK_REDUCTION = 0.66f;
    /** Maximum total reduction for weapon blocking (base + BLOCK_DAMAGE_REDUCTION stat). */
    private static final float WEAPON_BLOCK_CAP = 0.80f;
    /** Maximum total reduction for shield blocking (base + BLOCK_DAMAGE_REDUCTION stat). */
    private static final float SHIELD_BLOCK_CAP = 0.95f;

    // ==================== Dependencies ====================

    private final TrailOfOrbis plugin;
    private volatile CombatCalculator calculator;
    private volatile RPGDamageCalculator rpgCalculator;

    // Processor classes (lazily initialized)
    private volatile CombatEntityResolver entityResolver;
    private volatile CombatStatsResolver statsResolver;
    private volatile DamageTypeClassifier classifier;
    private volatile AvoidanceProcessor avoidanceProcessor;
    private volatile ConditionalMultiplierCalculator multiplierCalculator;
    private volatile DamageModifierProcessor modifierProcessor;
    private volatile CombatIndicatorService indicatorService;
    private volatile CombatFeedbackService feedbackService;
    private volatile CombatRecoveryProcessor recoveryProcessor;
    private volatile DurabilityHandler durabilityHandler;
    private volatile CombatTriggerHandler triggerHandler;
    private volatile CombatAilmentApplicator ailmentApplicator;
    private volatile DeathRecapRecorder deathRecapRecorder;

    public RPGDamageSystem(@Nonnull TrailOfOrbis plugin) {
        this.plugin = plugin;
    }

    // ==================== Lazy Initialization ====================

    private CombatCalculator getCalculator() {
        if (calculator == null) {
            synchronized (this) {
                if (calculator == null) {
                    var armorConfig = plugin.getConfigManager().getRPGConfig().getArmor();
                    calculator = new CombatCalculator(armorConfig.getMaxReduction(), armorConfig.getFormulaDivisor());
                    calculator.setMinArmorEffectiveness(armorConfig.getArmorPenetrationFloor());
                    LOGGER.at(Level.INFO).log("CombatCalculator initialized (armor pen floor: %.0f%%)",
                        armorConfig.getArmorPenetrationFloor() * 100);
                }
            }
        }
        return calculator;
    }

    private RPGDamageCalculator getRPGCalculator() {
        if (rpgCalculator == null) {
            synchronized (this) {
                if (rpgCalculator == null) {
                    rpgCalculator = new RPGDamageCalculator(getCalculator());
                    LOGGER.at(Level.INFO).log("RPGDamageCalculator initialized");
                }
            }
        }
        return rpgCalculator;
    }

    private void ensureProcessorsInitialized() {
        if (entityResolver == null) {
            synchronized (this) {
                if (entityResolver == null) {
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
                    indicatorService = new CombatIndicatorService(localResolver, plugin, plugin.getCombatTextColorManager());
                    feedbackService = new CombatFeedbackService();
                    feedbackService.init();
                    recoveryProcessor = new CombatRecoveryProcessor(localResolver);
                    durabilityHandler = new DurabilityHandler();
                    triggerHandler = new CombatTriggerHandler(localResolver, plugin.getConditionalTriggerSystem());
                    ailmentApplicator = new CombatAilmentApplicator(
                        localResolver, plugin.getAilmentTracker(), plugin.getAilmentCalculator(),
                        plugin.getAilmentImmunityTracker(), plugin.getAilmentEffectManager());
                    deathRecapRecorder = new DeathRecapRecorder(
                        localResolver, classifier, plugin.getDeathRecapTracker(),
                        plugin.getMobScalingComponentType(),
                        playerId -> plugin.getLevelingManager() != null
                            ? plugin.getLevelingManager().getLevel(playerId) : 1);
                    LOGGER.at(Level.INFO).log("Combat processors initialized");
                    entityResolver = localResolver; // LAST — volatile sentinel for DCL
                }
            }
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
        ensureProcessorsInitialized();

        // Diagnostic: log ALL damage source types to trace hex spell routing
        if (damage.getSource() instanceof Damage.EnvironmentSource env) {
            LOGGER.atInfo().log("[RPG-DIAG] EnvironmentSource damage: type=%s amount=%.1f cancelled=%s",
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
            handleDOTDamage(index, archetypeChunk, store, commandBuffer, damage);
            return;
        }

        // Handle Hexcode spell damage (EnvironmentSource with "hex_" prefix)
        // Must be checked BEFORE generic environmental damage so spells use RPG scaling
        if (damage.getSource() instanceof Damage.EnvironmentSource envSource) {
            String sourceType = envSource.getType();
            if (HexcodeSpellConfig.isHexSpellSource(sourceType)) {
                handleSpellDamage(index, archetypeChunk, store, commandBuffer, damage, sourceType);
                return;
            }
        }

        // Handle HP-based environmental damage (lava, drowning, etc.)
        if (classifier.isEnvironmentalDamage(damage)) {
            handleEnvironmentalDamage(index, archetypeChunk, store, commandBuffer, damage);
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

        if (handleSkillNodeInspection(store, defenderRef, damage)) {
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

        // ========== PHASE 3: PRE-CALC AVOIDANCE ==========
        AvoidanceProcessor.AvoidanceCheckResult avoidanceCheck = avoidanceProcessor.checkAvoidanceDetailed(
            store, defenderRef, damage, ctx.defenderStats, ctx.attackerStats, ctx.conditionalMultiplier, ctx.rpgBaseDamage);
        Optional<AvoidanceProcessor.AvoidanceResult> avoidance = avoidanceCheck.avoidance();
        ctx.avoidanceStats = avoidanceCheck.stats();

        if (avoidance.isPresent()) {
            LOGGER.at(Level.FINE).log("[DmgPipeline] ──── AVOIDANCE CHECK ────");
            LOGGER.at(Level.FINE).log("[DmgPipeline] Result: %s", avoidance.get().reason().name());
            LOGGER.at(Level.FINE).log("[DmgPipeline] ════════════ DAMAGE EVENT END ════════════");
            handleAvoidedDamage(index, archetypeChunk, store, defenderRef, damage, avoidance.get(), ctx.avoidanceStats);
            return;
        }

        LOGGER.at(Level.FINE).log("[DmgPipeline] ──── AVOIDANCE CHECK ────");
        LOGGER.at(Level.FINE).log("[DmgPipeline] Result: HIT");

        // ========== PHASE 4: SINGLE CALCULATOR CALL ==========
        ctx.trace = getRPGCalculator().calculateTraced(
            ctx.rpgBaseDamage, ctx.attackerStats, ctx.attackerElemental, ctx.defenderStats, ctx.defenderElemental,
            ctx.attackType, ctx.conditionalMultiplier, ctx.conditionalDetail, ctx.attackTypeMultiplier);
        ctx.trace.setAttackerBreakdown(null); // will be populated later if player
        ctx.breakdown = ctx.trace.breakdown();

        // ========== PHASE 4.5: POPULATE TRACE CONTEXT ==========
        populateTraceContext(store, damage, ctx);

        // ========== PHASE 5: POST-CALC ADJUSTMENTS ==========
        applyPostCalcModifications(store, damage, ctx);

        // ========== PHASE 5.5: ACTIVE BLOCKING REDUCTION ==========
        // Must run BEFORE Phase 6 (indicators) so damage numbers reflect the reduced amount.
        applyActiveBlockingReduction(store, ctx);

        // ========== PHASE 6: METADATA & TRIGGERS ==========
        emitCombatFeedback(index, archetypeChunk, store, commandBuffer, damage, ctx);

        // ========== PHASE 7: RECOVERY & THORNS ==========
        processRecoveryAndThorns(store, damage, ctx);

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

        // Conditional multipliers
        ConditionalResult conditionalDetail;
        float conditionalMultiplier;

        // Avoidance
        AvoidanceProcessor.AvoidanceDetail avoidanceStats;

        // Calculator output
        DamageTrace trace;
        DamageBreakdown breakdown;

        // Post-calc state
        float rpgDamage;
        boolean wasParried;
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
        ctx.attackType = classifier.detectAttackType(store, damage);
        ctx.attackerStats = statsResolver.getAttackerStats(store, damage);
        ctx.defenderStats = statsResolver.getDefenderStats(index, archetypeChunk, store);
        ctx.attackerElemental = statsResolver.getAttackerElementalStats(store, damage);
        ctx.defenderElemental = statsResolver.getDefenderElementalStats(index, archetypeChunk, store, ctx.defenderStats);
        ctx.defenderHealthPercent = entityResolver.getDefenderHealthPercent(index, archetypeChunk, store);
        ctx.defenderUuid = entityResolver.getDefenderUuid(index, archetypeChunk, store);

        // Log attack type
        LOGGER.at(Level.FINE).log("[DmgPipeline] Attack type: %s", ctx.attackType);
        LOGGER.at(Level.FINE).log("[DmgPipeline] Is DOT: false");

        // Get RPG base damage and determine if using RPG weapon
        // CRITICAL: Use isHoldingRpgGear() to detect RPG weapons, NOT weaponBaseDamage > 0!
        // This ensures unequippable RPG weapons (which have damage=0) still use the RPG path.
        ctx.rpgWeaponDamage = (ctx.attackerStats != null) ? ctx.attackerStats.getWeaponBaseDamage() : 0f;
        ctx.hasRpgWeapon = (ctx.attackerStats != null) && ctx.attackerStats.isHoldingRpgGear();

        // Log attacker/defender stats (FINE-level)
        logAttackerStats(ctx.attackerStats, ctx.attackerElemental, ctx.hasRpgWeapon, ctx.rpgWeaponDamage);
        logDefenderStats(ctx.defenderStats, ctx.defenderElemental);

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
            ctx.rpgBaseDamage = getWeaponBaseDamage(damage, ctx.attackerStats, ctx.vanillaDamage, store);
            ctx.attackTypeMultiplier = 1.0f;
        }

        // Calculate conditional multipliers with full detail for traced output
        ctx.conditionalDetail = multiplierCalculator.calculateDetailed(
            store, damage, ctx.attackerStats, ctx.defenderHealthPercent, ctx.defenderUuid, ctx.defenderRef);
        ctx.conditionalMultiplier = ctx.conditionalDetail.combined();

        damage.putMetaObject(ATTACK_TYPE, ctx.attackType);
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

        // Attacker level
        if (ctx.attackerStats != null) {
            // Try player level first
            Ref<EntityStore> traceAttRef = entityResolver.getAttackerRef(store, damage);
            if (traceAttRef != null) {
                PlayerRef attackerPlayerRef = store.getComponent(traceAttRef, PlayerRef.getComponentType());
                if (attackerPlayerRef != null && attackerPlayerRef.isValid() && plugin.getLevelingManager() != null) {
                    ctx.trace.setAttackerLevel(plugin.getLevelingManager().getLevel(attackerPlayerRef.getUuid()));
                } else {
                    // Try mob level
                    ComponentType<EntityStore, MobScalingComponent> scalingType = plugin.getMobScalingComponentType();
                    if (scalingType != null) {
                        MobScalingComponent scaling = store.getComponent(traceAttRef, scalingType);
                        if (scaling != null) ctx.trace.setAttackerLevel(scaling.getMobLevel());
                    }
                }
            }
        }
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
        @Nonnull Damage damage,
        @Nonnull DamageContext ctx
    ) {
        // Critical immunity recalculation
        boolean critNullified = false;
        float critNullifyChance = ctx.defenderStats != null ? ctx.defenderStats.getCritNullifyChance() : 0f;
        if (ctx.breakdown.wasCritical() && ctx.defenderStats != null && critNullifyChance >= 100f) {
            ctx.breakdown = getRPGCalculator().calculateWithForcedCrit(
                ctx.rpgBaseDamage, ctx.attackerStats, ctx.attackerElemental, ctx.defenderStats, ctx.defenderElemental,
                ctx.attackType, false);
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

        // Unarmed damage penalty — players without weapons deal reduced damage
        if (!ctx.hasRpgWeapon && ctx.attackerStats != null && !ctx.attackerStats.isMobStats()) {
            float unarmedMult = plugin.getConfigManager().getRPGConfig().getCombat().getUnarmedDamageMultiplier();
            if (unarmedMult != 1.0f) {
                ctx.trace.setUnarmedPenalty(unarmedMult, ctx.rpgDamage);
                ctx.rpgDamage *= unarmedMult;
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

        // DETONATE_DOT_ON_CRIT: crits on DoT targets burst remaining DoT as true damage
        if (ctx.breakdown.wasCritical() && ctx.attackerStats != null && ctx.attackerStats.getDetonateDotOnCrit() > 0
                && ctx.defenderUuid != null) {
            var ailmentTracker = plugin.getAilmentTracker();
            if (ailmentTracker != null) {
                float remainingDot = ailmentTracker.getRemainingDotDamage(ctx.defenderUuid);
                if (remainingDot > 0) {
                    float burstPct = ctx.attackerStats.getDetonateDotOnCrit();
                    float burstDamage = remainingDot * (burstPct / 100f);
                    ctx.rpgDamage += burstDamage;
                    ailmentTracker.detonateAllDots(ctx.defenderUuid);
                    LOGGER.at(Level.FINE).log("DoT detonation: %.1f remaining DoT × %.0f%% = %.1f burst damage",
                        remainingDot, burstPct, burstDamage);
                }
            }
        }

        // SPELL_ECHO_CHANCE: magic damage has X% chance to repeat for 50% as Void damage
        if (ctx.attackerStats != null && ctx.attackerStats.getSpellEchoChance() > 0
                && ctx.breakdown.damageType() == DamageType.MAGIC) {
            float echoPct = ctx.attackerStats.getSpellEchoChance();
            if (ThreadLocalRandom.current().nextFloat() * 100f < echoPct) {
                float echoDamage = ctx.rpgDamage * 0.5f;
                ctx.rpgDamage += echoDamage;
                LOGGER.at(Level.FINE).log("Spell Echo: %.0f%% chance proc — %.1f echo damage (50%% of %.1f)",
                    echoPct, echoDamage, ctx.rpgDamage - echoDamage);
            }
        }

        // Log damage breakdown (FINE-level)
        logDamageBreakdown(ctx.breakdown, ctx.rpgBaseDamage, ctx.attackTypeMultiplier, ctx.conditionalMultiplier,
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

        // Apply ailments BEFORE indicators so the trace has ailment data for display
        if (ctx.attackerElemental != null && ctx.attackerElemental.hasAnyElementalDamage()) {
            AilmentSummary ailmentSummary = ailmentApplicator.tryApplyAilments(index, archetypeChunk, store, damage,
                ctx.attackerElemental, ctx.attackerStats, ctx.maxHealth, ctx.defenderStats);
            ctx.trace.setAilmentSummary(ailmentSummary);
        }

        // Get target info for attacker's combat log (shows who they hit)
        DeathRecapRecorder.AttackerInfo targetInfo = deathRecapRecorder.getTargetInfo(store, ctx.defenderRef);

        // Send indicators with full trace for detailed chat logs
        indicatorService.sendDamageIndicators(store, ctx.defenderRef, damage, ctx.rpgDamage, ctx.breakdown, ctx.wasParried,
            targetInfo, ctx.trace);
        damage.putMetaObject(INDICATORS_SENT, true);

        // Spawn impact particles and sounds (restores vanilla on-hit feedback + RPG effects)
        feedbackService.onDamageDealt(store, ctx.defenderRef, ctx.breakdown, ctx.rpgDamage);

        // Record damage for death recap
        deathRecapRecorder.recordDamage(index, archetypeChunk, store, damage, ctx.breakdown, ctx.rpgBaseDamage,
            ctx.defenderStats, ctx.attackerStats, ctx.maxHealth, ctx.healthBefore);

        // Send damage received log to defender (shows them what hit them)
        DeathRecapRecorder.AttackerInfo attackerInfo = deathRecapRecorder.getAttackerInfo(store, damage);

        // Extract raw elemental resistances for combat log display
        Map<ElementType, Float> defenderRawResistances = extractRawResistances(ctx.defenderElemental);
        Map<ElementType, Float> attackerElemPenetration = extractPenetration(ctx.attackerElemental);

        CombatSnapshot snapshot = CombatSnapshot.fromBreakdown(
            ctx.breakdown, attackerInfo.name(), attackerInfo.type(), attackerInfo.level(), attackerInfo.mobClass(),
            ctx.rpgBaseDamage, ctx.maxHealth, ctx.healthBefore,
            ctx.defenderStats != null ? ctx.defenderStats.getEvasion() : 0f,
            ctx.defenderStats != null ? ctx.defenderStats.getArmor() : 0f,
            ctx.attackerStats != null ? ctx.attackerStats.getArmorPenetration() : 0f,
            defenderRawResistances, attackerElemPenetration);
        indicatorService.sendDamageReceivedLog(store, ctx.defenderRef, snapshot, ctx.trace);

        durabilityHandler.handleDurability(index, archetypeChunk, store, commandBuffer, damage);

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
                if (healAmount > 0) {
                    recoveryProcessor.applyBlockHeal(index, archetypeChunk, store, healAmount);
                }
            }

            float counterPct = ctx.defenderStats.getBlockCounterDamage();
            if (counterPct > 0 && preBlockDamage > 0) {
                float counterDamage = preBlockDamage * (counterPct / 100f);
                applyBlockCounterDamage(store, damage, counterDamage);
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
        @Nonnull Damage damage,
        @Nonnull DamageContext ctx
    ) {
        // Leech = gain resources from YOUR damage output (always works)
        // Steal = take resources FROM enemy (enemy loses what you gain)
        if (ctx.attackerStats != null && ctx.rpgDamage > 0) {
            float healthRecoveryPct = ctx.attackerStats.getHealthRecoveryPercent();
            boolean didHealHealth = false;

            // Life Leech - always heals from damage dealt
            float lifeLeech = ctx.attackerStats.getLifeLeech();
            if (lifeLeech > 0) {
                float healAmount = ctx.rpgDamage * (lifeLeech / 100f);
                if (healthRecoveryPct != 0) {
                    healAmount *= (1.0f + healthRecoveryPct / 100.0f);
                }
                ctx.trace.setLifeLeech(lifeLeech, healAmount);
                recoveryProcessor.applyLifeLeech(store, damage, healAmount);
                didHealHealth = true;
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
                ctx.trace.setLifeSteal(lifeSteal, healAmount);
                recoveryProcessor.applyLifeSteal(store, damage, healAmount);
                didHealHealth = true;
            }

            // Mana Steal - attacker gains AND enemy loses mana (only if enemy has mana)
            float manaSteal = ctx.attackerStats.getManaSteal();
            if (manaSteal > 0) {
                float manaAmount = ctx.rpgDamage * (manaSteal / 100f);
                ctx.trace.setManaSteal(manaSteal, manaAmount);
                recoveryProcessor.applyManaSteal(store, damage, manaAmount, ctx.defenderRef);
            }

            // Recovery feedback (heal particles + sound) for attacker if any HP was recovered
            if (didHealHealth) {
                Ref<EntityStore> attackerRef = entityResolver.getAttackerRef(store, damage);
                if (attackerRef != null) {
                    feedbackService.onRecovery(store, attackerRef);
                }
            }
        }

        // Thorns Application
        // After damage is dealt, if defender has thorns/reflect stats, deal damage back to attacker.
        // Formula: thornsDamage x (1 + thornsDamagePercent/100) + damageTaken x (reflectDamagePercent/100)
        // Thorns is non-lethal (attacker left at 1 HP minimum).
        if (ctx.defenderStats != null && ctx.rpgDamage > 0) {
            float thornsDamage = ctx.defenderStats.getThornsDamage();
            float reflectPercent = ctx.defenderStats.getReflectDamagePercent();

            if (thornsDamage > 0 || reflectPercent > 0) {
                float thornsPercent = ctx.defenderStats.getThornsDamagePercent();
                float flatThorns = thornsDamage * (1f + thornsPercent / 100f);
                float reflected = ctx.rpgDamage * (reflectPercent / 100f);
                ctx.trace.setThorns(thornsDamage, thornsPercent, reflectPercent, flatThorns + reflected);
                recoveryProcessor.applyThornsDamage(store, damage, ctx.defenderStats, ctx.rpgDamage);
            }

            // Block heal/counter handled in emitCombatFeedback (Phase 6) which has index/archetypeChunk
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
        // Fetch per-source stat breakdowns for trace (only for players, ~1ms cost)
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

        // Vanilla's SequenceModifier skips EntityStatsOnHit processing when damage.amount == 0.
        // Since we zero the amount to prevent double-application, we must manually invoke
        // the on-hit stat gains (SignatureEnergy, etc.) for the attacker.
        processEntityStatsOnHitManually(store, commandBuffer, damage);

        applyFinalDamage(store, commandBuffer, damage, ctx.statMap, ctx.healthIndex, ctx.healthBefore, ctx.rpgDamage, index, archetypeChunk);
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

    // ==================== DOT Handling ====================

    private void handleDOTDamage(
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
        ElementType dotElement = classifier.getElementFromDOTCause(DamageTypeClassifier.getDamageCause(damage));

        DamageBreakdown breakdown = getRPGCalculator().calculateDOT(
            baseDamage, defenderStats, defenderElemental, dotElement);

        float rpgDamage = breakdown.totalDamage();

        damage.putMetaObject(DAMAGE_TYPE, breakdown.damageType());
        damage.putMetaObject(WAS_CRITICAL, false);
        damage.putMetaObject(RPG_DAMAGE_VALUE, rpgDamage);
        damage.putMetaObject(ATTACK_TYPE, AttackType.UNKNOWN);
        damage.setAmount(0);

        indicatorService.sendDamageIndicatorsVisualOnly(store, defenderRef, damage, rpgDamage, breakdown, false);
        damage.putMetaObject(INDICATORS_SENT, true);

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

            damage.setAmount(rpgDamage);
            LOGGER.at(Level.FINE).log("Lethal DOT: base=%.1f -> rpg=%.1f", baseDamage, rpgDamage);
            return;
        }

        damage.setCancelled(true);
        statMap.subtractStatValue(healthIndex, healthBefore - newHealth);
        LOGGER.at(Level.FINE).log("DOT: base=%.1f -> rpg=%.1f, health: %.1f -> %.1f",
            baseDamage, rpgDamage, healthBefore, newHealth);
    }

    // ==================== Spell Damage Handling (Hexcode) ====================

    /**
     * Handles Hexcode spell damage routed through {@code EnvironmentSource("hex_xxx")}.
     *
     * <p>Unlike environmental damage (which scales as % of max HP), spell damage uses
     * the Hexcode-supplied damage amount and applies RPG resistances:
     * <ol>
     *   <li>Resolve damage type and element from config mapping</li>
     *   <li>Apply defender's elemental resistance (with spell penetration if caster known)</li>
     *   <li>Apply caster's spell damage scaling (when caster UUID becomes available)</li>
     *   <li>Record in death recap with mapped spell display name</li>
     *   <li>Emit damage indicators with element-colored text</li>
     *   <li>Trigger ailments from the mapped element</li>
     * </ol>
     *
     * <p><b>Important:</b> This does NOT cancel the damage event. The damage amount is
     * modified in-place so Hexcode's own FilterDamageGroup systems (Erode, Fortify)
     * can still process it downstream.
     *
     * @param index The entity index in the archetype chunk
     * @param archetypeChunk The archetype chunk containing the defender
     * @param store The entity store
     * @param commandBuffer The command buffer for entity modifications
     * @param damage The damage event
     * @param sourceType The EnvironmentSource type string (e.g., "hex_bolt")
     */
    private void handleSpellDamage(
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Damage damage,
        @Nonnull String sourceType
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
        float maxHealth = healthStat.getMax();

        // Resolve spell config
        HexcodeSpellConfig spellConfig = plugin.getConfigManager().getHexcodeSpellConfig();
        if (!spellConfig.isEnabled()) {
            // Spell integration disabled — fall through to environmental damage
            handleEnvironmentalDamage(index, archetypeChunk, store, commandBuffer, damage);
            return;
        }

        DamageType damageType = spellConfig.getDamageType(sourceType);
        ElementType element = spellConfig.getElement(sourceType);
        String displayName = spellConfig.getDisplayName(sourceType);
        float rpgDamage = baseDamage;

        // ---- Find the spell caster ----
        // Use fresh ThreadLocal (same synchronous invoke chain) or recent caster fallback.
        // Note: HexDamageAttributionSystem (FilterDamageGroup) handles source rewriting
        // for kill attribution. This caster lookup is for RPG stat scaling (magic power).
        Ref<EntityStore> casterRef = HexCastEventInterceptor.getFreshCaster();
        String casterSource = "fresh";
        if (casterRef == null || !casterRef.isValid()) {
            casterRef = HexCastEventInterceptor.findRecentCaster(store);
            casterSource = "recent_map";
        }

        LOGGER.atFine().log("[SpellDmg] %s: caster=%s (via %s), base=%.1f",
            sourceType,
            casterRef != null ? "FOUND" : "NULL",
            casterSource,
            baseDamage);

        // Rewrite damage source from EnvironmentSource to EntitySource so death-time
        // systems (XP, loot, realm kills, stone drops, map drops) attribute the kill to the caster
        if (casterRef != null && casterRef.isValid()) {
            damage.setSource(new Damage.EntitySource(casterRef));
        }

        ComputedStats casterStats = null;
        if (casterRef != null) {
            PlayerRef casterPlayer = store.getComponent(casterRef, PlayerRef.getComponentType());
            if (casterPlayer != null) {
                casterStats = plugin.getAttributeManager().getStats(casterPlayer.getUuid());
            }
        }

        // ---- Apply defender's elemental resistance ----
        float resistanceReduction = 0f;
        ComputedStats defenderStats = statsResolver.getDefenderStats(index, archetypeChunk, store);
        ElementalStats defenderElemental = statsResolver.getDefenderElementalStats(
            index, archetypeChunk, store, defenderStats);

        if (element != null && defenderElemental != null) {
            float resistance = (float) defenderElemental.getResistance(element);
            float resistanceCap = spellConfig.getMax_resistance_cap();
            resistance = Math.min(resistance, resistanceCap);

            if (resistance > 0) {
                resistanceReduction = resistance;
                rpgDamage *= (1f - resistance / 100f);
                rpgDamage = Math.max(1f, rpgDamage);
            }
        }

        // ---- Set damage metadata for downstream systems ----
        damage.putMetaObject(DAMAGE_TYPE, damageType);
        damage.putMetaObject(WAS_CRITICAL, false);
        damage.putMetaObject(RPG_DAMAGE_VALUE, rpgDamage);
        damage.putMetaObject(ATTACK_TYPE, AttackType.AREA); // Spells are area-effect

        // Build breakdown for indicators
        DamageBreakdown breakdown;
        if (element != null) {
            // Create elemental breakdown with damage in the correct element slot
            EnumMap<ElementType, Float> elemMap = new EnumMap<>(ElementType.class);
            elemMap.put(element, rpgDamage);
            EnumMap<ElementType, Float> preDefElem = new EnumMap<>(ElementType.class);
            preDefElem.put(element, baseDamage);
            breakdown = DamageBreakdown.builder()
                .physicalDamage(0f)
                .elementalDamage(elemMap)
                .trueDamage(0f)
                .preDefenseDamage(baseDamage)
                .preDefenseElemental(preDefElem)
                .wasCritical(false)
                .critMultiplier(1.0f)
                .armorReduction(0f)
                .resistanceReduction(element, resistanceReduction)
                .damageType(damageType)
                .attackType(AttackType.AREA)
                .build();
        } else {
            breakdown = DamageBreakdown.simple(rpgDamage, damageType);
        }

        // ---- Set the damage cause index for correct damage text color ----
        // Must be set BEFORE indicators so screen flash uses the correct cause
        int causeIndex = damageType.getCauseIndex(false);
        if (causeIndex != Integer.MIN_VALUE) {
            damage.setDamageCauseIndex(causeIndex);
        }

        // ---- Send damage indicators ----
        // If we found the caster, use their EntityViewer for floating combat text
        // (same mechanism as regular melee/ranged combat). Otherwise screen flash only.
        DamageCause spellCause = DamageTypeClassifier.getDamageCause(damage);
        if (casterRef != null) {
            // Send indicators as if caster attacked defender — shows floating text to caster
            indicatorService.sendDefenderIndicator(store, defenderRef, casterRef, rpgDamage, spellCause);
            indicatorService.sendAttackerCombatText(store, defenderRef, casterRef, rpgDamage,
                new CombatTextParams(false, false, false, false, false), null, breakdown);
        } else {
            // Fallback: player-only screen flash (no floating text without caster)
            indicatorService.sendSpellDamageIndicator(store, defenderRef, rpgDamage, spellCause);
        }
        damage.putMetaObject(INDICATORS_SENT, true);

        // ---- Record death recap (spell display name as source) ----
        int casterLevel = 0; // Level resolution requires LevelingService — use 0 for spell recap
        DeathRecapRecorder.AttackerInfo spellAttackerInfo = new DeathRecapRecorder.AttackerInfo(
            displayName, "spell", casterLevel, null);
        deathRecapRecorder.recordDamageWithAttacker(index, archetypeChunk, store, spellAttackerInfo,
            breakdown, baseDamage, defenderStats, null, maxHealth, healthBefore);

        // ---- Trigger ailments from spell element ----
        if (element != null && rpgDamage > 0 && ailmentApplicator.isAvailable()) {
            ElementalStats syntheticAttackerElemental = new ElementalStats();
            syntheticAttackerElemental.setFlatDamage(element, rpgDamage);

            AilmentSummary ailmentSummary = ailmentApplicator.tryApplyAilments(
                index, archetypeChunk, store, damage,
                syntheticAttackerElemental, casterStats, maxHealth, defenderStats);

            if (!ailmentSummary.attempts().isEmpty()) {
                LOGGER.at(Level.FINE).log("[SpellDmg] Ailment attempts: %d for element %s",
                    ailmentSummary.attempts().size(), element.name());
            }
        }

        // ---- Modify damage amount for downstream Hexcode systems (Erode, Fortify) ----
        // Don't cancel — let FilterDamageGroup process. Set amount to RPG-adjusted value.
        damage.setAmount(rpgDamage);

        LOGGER.at(Level.FINE).log("[SpellDmg] %s (%s): %.1f → %.1f (resist=%.1f%%), health: %.1f/%.1f",
            displayName, sourceType, baseDamage, rpgDamage, resistanceReduction, healthBefore, maxHealth);
    }

    // ==================== Environmental Damage Handling ====================

    /**
     * Handles HP-based environmental damage (lava, drowning, suffocation, etc.).
     *
     * <p>Environmental damage is converted from fixed values to max HP percentage-based
     * damage. This ensures hazards scale with player progression:
     * <ul>
     *   <li>Early game (100 HP): 5% lava = 5 damage per tick</li>
     *   <li>Late game (5000 HP): 5% lava = 250 damage per tick</li>
     * </ul>
     *
     * <p>Fire resistance applies to fire/lava damage (capped at 75%).
     *
     * @param index The entity index in the archetype chunk
     * @param archetypeChunk The archetype chunk containing the entity
     * @param store The entity store
     * @param commandBuffer The command buffer for entity modifications
     * @param damage The damage event
     */
    private void handleEnvironmentalDamage(
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
                float resistance = (float) defenderElemental.getResistance(envElement);
                // Cap resistance at 75%
                resistance = Math.min(resistance, 75f);
                if (resistance > 0) {
                    resistanceReduction = resistance;
                    rpgDamage *= (1f - resistance / 100f);
                    rpgDamage = Math.max(1f, rpgDamage); // Maintain minimum 1 damage
                    LOGGER.at(Level.FINE).log("Applied %s resistance: %.1f%% → damage reduced to %.1f",
                        envElement.name(), resistance, rpgDamage);
                }
            }
        }

        // Apply damage metadata
        DamageType damageType = envElement == ElementType.FIRE ? DamageType.MAGIC : DamageType.PHYSICAL;
        damage.putMetaObject(DAMAGE_TYPE, damageType);
        damage.putMetaObject(WAS_CRITICAL, false);
        damage.putMetaObject(RPG_DAMAGE_VALUE, rpgDamage);
        damage.putMetaObject(ATTACK_TYPE, AttackType.UNKNOWN);
        damage.setAmount(0);

        // Create a simple breakdown for indicators
        DamageBreakdown breakdown = DamageBreakdown.simple(rpgDamage, damageType);

        // Send damage indicators
        indicatorService.sendDamageIndicatorsVisualOnly(store, defenderRef, damage, rpgDamage, breakdown, false);
        damage.putMetaObject(INDICATORS_SENT, true);

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

            DeathComponent.tryAddComponent(commandBuffer, defenderRef, damage);
            LOGGER.at(Level.FINE).log("Lethal environmental damage '%s': %.1f (%.1f%% HP)",
                causeId, rpgDamage, hpPercent);
            return;
        }

        // Non-lethal damage - cancel vanilla and apply manually
        damage.setCancelled(true);
        statMap.subtractStatValue(healthIndex, healthBefore - newHealth);
        LOGGER.at(Level.FINE).log("Environmental damage '%s': %.1f, health: %.1f -> %.1f",
            causeId, rpgDamage, healthBefore, newHealth);
    }

    /**
     * Maps environmental damage causes to element types for resistance application.
     *
     * <p>Currently only fire/lava maps to FIRE resistance. Other environmental
     * hazards (drowning, suffocation) are considered non-elemental.
     *
     * @param causeId The damage cause ID
     * @return The element type, or null for non-elemental damage
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

    /**
     * Gets the base damage for non-RPG weapon attacks (vanilla weapons, unarmed, mobs).
     *
     * <p>This method is called when the attacker does NOT have an RPG weapon equipped.
     * For RPG weapons, the implicit damage is used directly in the main handle method.
     *
     * <p>Fallback order:
     * <ul>
     *   <li>Environment damage (fall, drowning): Uses vanilla amount</li>
     *   <li>Mob attacks: Uses weighted RPG formula (same as mob HP)</li>
     *   <li>Vanilla weapons: Uses vanilla calculated damage (preserves attack differentiation)</li>
     *   <li>Truly unarmed (fists, no weapon): Uses configured unarmed base damage</li>
     * </ul>
     *
     * @param damage The damage event
     * @param attackerStats The attacker's computed stats (may be null)
     * @param vanillaDamage The vanilla-calculated damage amount
     * @param store The entity store for component lookup
     * @return The base damage to use for calculations
     */
    private float getWeaponBaseDamage(
        @Nonnull Damage damage,
        @Nullable ComputedStats attackerStats,
        float vanillaDamage,
        @Nonnull Store<EntityStore> store
    ) {
        // Environment damage (fall, drowning, etc.) - keep vanilla value
        if (!(damage.getSource() instanceof Damage.EntitySource entitySource)) {
            return vanillaDamage;
        }

        // Check if attacker is a scaled mob - apply weighted RPG damage formula
        Ref<EntityStore> attackerRef = entitySource.getRef();
        if (attackerRef != null && attackerRef.isValid()) {
            float weightedMobDamage = calculateWeightedMobDamage(attackerRef, vanillaDamage, store);
            if (weightedMobDamage > 0) {
                return weightedMobDamage;
            }
        }

        // Check if there's a damage sequence (indicates weapon attack, not fists)
        DamageCalculatorSystems.DamageSequence damageSequence =
            damage.getIfPresentMetaObject(DamageCalculatorSystems.DAMAGE_SEQUENCE);

        if (damageSequence != null) {
            // Vanilla weapon attack - use vanilla damage directly
            // This preserves all attack differentiation (swing types, backstabs, signatures)
            return vanillaDamage;
        }

        // Truly unarmed (no damage sequence = fist punch or mob attack)
        RPGConfig.CombatConfig combatConfig = plugin.getConfigManager().getRPGConfig().getCombat();
        float unarmedBase = combatConfig.getUnarmedBaseDamage();
        LOGGER.at(Level.FINE).log("Unarmed attack, using base: %.1f", unarmedBase);
        return unarmedBase;
    }

    /**
     * Calculates weighted mob damage using the same formula as mob HP.
     *
     * <p><b>Weighted RPG Formula:</b> Creates parallel progression between mob HP and damage.
     * <pre>
     * weight = √(vanillaDamage / 10)
     * effectiveRpg = max(rpgTargetDmg × progressiveScale, 5 × bossMultiplier)
     * finalDamage = effectiveRpg × weight
     * </pre>
     *
     * <p>This formula:
     * <ul>
     *   <li>Uses mob's RPG physical_damage stat as base</li>
     *   <li>Compresses vanilla damage range via square root weight</li>
     *   <li>Scales with progressive factor (17.8% at Lv1 → 100% at Lv30+)</li>
     *   <li>Ensures bosses/elites deal meaningful damage</li>
     * </ul>
     *
     * @param attackerRef The attacker entity reference
     * @param vanillaDamage The vanilla base damage from Hytale attack
     * @param store The entity store
     * @return Weighted RPG damage, or -1 if not a scaled mob
     */
    private float calculateWeightedMobDamage(
        @Nonnull Ref<EntityStore> attackerRef,
        float vanillaDamage,
        @Nonnull Store<EntityStore> store
    ) {
        ComponentType<EntityStore, MobScalingComponent> scalingType = plugin.getMobScalingComponentType();
        if (scalingType == null) {
            return -1f;
        }

        MobScalingComponent scaling = store.getComponent(attackerRef, scalingType);
        if (scaling == null) {
            return -1f;
        }

        MobStats mobStats = scaling.getStats();
        if (mobStats == null) {
            return -1f;
        }

        // Get RPG target damage from mob stat pool (with global balance multiplier)
        double balanceMult = plugin.getMobScalingManager() != null
            ? plugin.getMobScalingManager().getConfig().getBalanceMultipliers().getPhysicalDamage()
            : 1.0;
        double rpgTargetDmg = mobStats.physicalDamage() * balanceMult;
        int mobLevel = scaling.getMobLevel();
        RPGMobClass classification = scaling.getClassification();

        // Get progressive scaling factor (0.15 → 1.0 based on level)
        MobStatPoolConfig poolConfig = plugin.getMobScalingManager() != null
            ? plugin.getMobScalingManager().getStatPoolConfig()
            : null;
        double progressiveScale = poolConfig != null
            ? poolConfig.calculateScalingFactor(mobLevel)
            : 1.0;

        // Boss/Elite damage multiplier (parallel to HP multiplier)
        double classMultiplier = switch (classification) {
            case BOSS -> 2.0;
            case ELITE -> 1.5;
            default -> 1.0;
        };

        // Weight: compresses vanilla damage range via square root
        // vanillaDamage 10 → weight 1.0
        // vanillaDamage 40 → weight 2.0
        // vanillaDamage 5 → weight 0.71
        float effectiveVanilla = Math.max(vanillaDamage, 5f);
        double weight = Math.sqrt(effectiveVanilla / 10.0);

        // Effective RPG: scales with level, has a floor to prevent 0 damage
        double minDamage = 5.0 * classMultiplier;
        double effectiveRpg = Math.max(rpgTargetDmg * progressiveScale * classMultiplier, minDamage);

        // Final damage: weighted RPG value
        double finalDamage = effectiveRpg * weight;

        LOGGER.at(Level.FINE).log(
            "[MobDamage] Weighted formula: vanilla=%.1f, rpgTarget=%.1f, scale=%.2f, class=%s(×%.1f), weight=%.2f, final=%.1f",
            vanillaDamage, rpgTargetDmg, progressiveScale, classification, classMultiplier, weight, finalDamage);

        return (float) finalDamage;
    }

    private void handleAvoidedDamage(
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> defenderRef,
        @Nonnull Damage damage,
        @Nonnull AvoidanceProcessor.AvoidanceResult result,
        @Nonnull AvoidanceProcessor.AvoidanceDetail avoidanceStats
    ) {
        // Set avoidance metadata
        switch (result.reason()) {
            case DODGED -> damage.putMetaObject(WAS_DODGED, true);
            case BLOCKED -> damage.putMetaObject(WAS_BLOCKED, true);
            case PARRIED -> damage.putMetaObject(WAS_PARRIED, true);
            case MISSED -> damage.putMetaObject(WAS_MISSED, true);
        }
        damage.putMetaObject(RPG_DAMAGE_VALUE, 0f);
        damage.setCancelled(true);

        // Send avoidance indicators (floating text)
        indicatorService.sendAvoidanceIndicators(store, defenderRef, damage, result.reason());
        damage.putMetaObject(INDICATORS_SENT, true);

        // Spawn avoidance particles and sounds (shield bash, parry reflect)
        feedbackService.onDamageAvoided(store, defenderRef, result.reason());

        // Send avoidance log to defender's chat (shows them they avoided damage)
        DeathRecapRecorder.AttackerInfo attackerInfo = deathRecapRecorder.getAttackerInfo(store, damage);
        indicatorService.sendAvoidanceLog(store, defenderRef, result.reason(), attackerInfo, result.estimatedDamage(), avoidanceStats);

        // Fire appropriate trigger
        switch (result.reason()) {
            case DODGED -> triggerHandler.fireOnEvadeTrigger(index, archetypeChunk, store);
            case BLOCKED -> {
                triggerHandler.fireOnBlockTrigger(index, archetypeChunk, store);
                // Block heal - heals based on blocked damage amount
                ComputedStats defenderStats = statsResolver.getDefenderStats(index, archetypeChunk, store);
                if (defenderStats != null) {
                    float blockHealPct = defenderStats.getBlockHealPercent();
                    if (blockHealPct > 0 && result.estimatedDamage() > 0) {
                        float healAmount = result.estimatedDamage() * (blockHealPct / 100f);
                        // Apply global health recovery multiplier to block heal
                        float healthRecoveryPct = defenderStats.getHealthRecoveryPercent();
                        if (healthRecoveryPct != 0) {
                            healAmount *= (1.0f + healthRecoveryPct / 100.0f);
                        }
                        recoveryProcessor.applyBlockHeal(index, archetypeChunk, store, healAmount);
                    }

                    // Block counter damage - reflect % of blocked damage back to attacker
                    float counterPct = defenderStats.getBlockCounterDamage();
                    if (counterPct > 0 && result.estimatedDamage() > 0) {
                        float counterDamage = result.estimatedDamage() * (counterPct / 100f);
                        applyBlockCounterDamage(store, damage, counterDamage);
                    }
                }
            }
            default -> {} // PARRIED, MISSED - no special handling
        }
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

        LOGGER.at(Level.FINE).log("Damage: %.1f %s%s", rpgDamage, damageType, wasCrit ? " (CRIT)" : "");
    }

    /**
     * Applies block counter damage to the attacker.
     *
     * <p>When a defender has the BLOCK_COUNTER_DAMAGE stat, a percentage of the
     * blocked damage is reflected back as physical damage to the attacker.
     */
    private void applyBlockCounterDamage(
        @Nonnull Store<EntityStore> store,
        @Nonnull Damage damage,
        float counterDamage
    ) {
        if (!(damage.getSource() instanceof Damage.EntitySource entitySource)) {
            return;
        }

        Ref<EntityStore> attackerRef = entitySource.getRef();
        if (attackerRef == null || !attackerRef.isValid()) {
            return;
        }

        Ref<EntityStore> trueAttackerRef = entityResolver.resolveTrueAttacker(store, attackerRef);
        if (trueAttackerRef == null) {
            return;
        }

        EntityStatMap attackerStatMap = store.getComponent(trueAttackerRef, EntityStatMap.getComponentType());
        if (attackerStatMap == null) {
            return;
        }

        EntityStatValue attackerHealth = attackerStatMap.get(DefaultEntityStatTypes.getHealth());
        if (attackerHealth == null || attackerHealth.get() <= 0) {
            return;
        }

        float healthBefore = attackerHealth.get();
        float newHealth = Math.max(0f, healthBefore - counterDamage);
        attackerStatMap.subtractStatValue(attackerHealth.getIndex(), counterDamage);

        LOGGER.at(Level.FINE).log("Block counter: reflected %.1f damage to attacker (%.1f → %.1f HP)",
            counterDamage, healthBefore, newHealth);
    }

    /**
     * Phase 5.5: Applies deterministic active blocking damage reduction.
     *
     * <p>When the defender is holding block (right-click) but didn't get a perfect
     * block (handled earlier in the avoidance pipeline), they receive a base
     * reduction that scales with gear:
     * <ul>
     *   <li>Weapon blocking: 33% base, capped at 80%</li>
     *   <li>Shield blocking: 66% base, capped at 95%</li>
     *   <li>BLOCK_DAMAGE_REDUCTION stat from gear adds to the base</li>
     * </ul>
     *
     * <p>Runs BEFORE Phase 6 (indicators/combat log) so all feedback shows the
     * correctly reduced damage amount. Tracks the reduction in DamageTrace for
     * {@code /too combat detail}.
     */
    private void applyActiveBlockingReduction(
        @Nonnull Store<EntityStore> store,
        @Nonnull DamageContext ctx
    ) {
        // Check if defender is actively blocking
        DamageDataComponent damageData = store.getComponent(ctx.defenderRef, DamageDataComponent.getComponentType());
        if (damageData == null || damageData.getCurrentWielding() == null) {
            return;
        }

        // Only apply to players
        PlayerRef playerRef = store.getComponent(ctx.defenderRef, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }

        // Determine shield vs weapon blocking.
        // Shields are Weapon-type items equipped in the offhand (utility container).
        // Must iterate the container directly — getUtilityItem() only returns the
        // "active" utility and flickers to null when the player isn't using the offhand.
        // Check the BASE item ID because RPG gear shields have custom IDs (rpg_gear_xxx).
        Player player = store.getComponent(ctx.defenderRef, Player.getComponentType());
        boolean hasShield = false;
        if (player != null && player.getInventory() != null) {
            var utility = player.getInventory().getUtility();
            if (utility != null) {
                for (short i = 0; i < utility.getCapacity(); i++) {
                    ItemStack item = utility.getItemStack(i);
                    if (!ItemStack.isEmpty(item)) {
                        String baseId = io.github.larsonix.trailoforbis.gear.util.GearUtils.getBaseItemId(item);
                        String checkId = (baseId != null) ? baseId : item.getItemId();
                        if (checkId != null && checkId.toLowerCase().contains("shield")) {
                            hasShield = true;
                            break;
                        }
                    }
                }
            }
        }

        float baseReduction = hasShield ? SHIELD_BLOCK_REDUCTION : WEAPON_BLOCK_REDUCTION;
        float cap = hasShield ? SHIELD_BLOCK_CAP : WEAPON_BLOCK_CAP;

        // Add BLOCK_DAMAGE_REDUCTION from gear, capped
        float statBonus = (ctx.defenderStats != null)
            ? ctx.defenderStats.getBlockDamageReduction() / 100f
            : 0f;
        float totalReduction = Math.min(baseReduction + statBonus, cap);

        float preDamage = ctx.rpgDamage;
        ctx.rpgDamage = preDamage * (1.0f - totalReduction);

        // Track in DamageTrace for /too combat detail
        ctx.trace.setActiveBlocking(hasShield, totalReduction * 100f, preDamage, ctx.rpgDamage);

        LOGGER.atFine().log("Active block: %s %.0f%% (base %.0f%% + gear %.0f%%), damage: %.1f → %.1f",
            hasShield ? "SHIELD" : "WEAPON",
            totalReduction * 100, baseReduction * 100, statBonus * 100,
            preDamage, ctx.rpgDamage);
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

                DeathComponent.tryAddComponent(commandBuffer, defenderRef, damage);
            }

            LOGGER.at(Level.FINE).log("Lethal RPG damage: %.1f, triggering death directly", rpgDamage);
            triggerHandler.fireOnKillTrigger(store, damage);
            return;
        }

        // Non-lethal damage - subtract RPG calculated damage from health
        statMap.subtractStatValue(healthIndex, healthBefore - newHealth);
        LOGGER.at(Level.FINE).log("Damage applied: rpg=%.1f, health: %.1f -> %.1f", rpgDamage, healthBefore, newHealth);

        triggerHandler.fireWhenHitTrigger(index, archetypeChunk, store);
    }

    // ==================== Skill Node Inspection ====================

    private boolean handleSkillNodeInspection(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> defenderRef,
        @Nonnull Damage damage
    ) {
        TrailOfOrbis pluginInstance = TrailOfOrbis.getInstance();
        if (pluginInstance == null) {
            return false;
        }

        ComponentType<EntityStore, SkillNodeComponent> nodeComponentType = pluginInstance.getSkillNodeComponentType();
        if (nodeComponentType == null) {
            return false;
        }

        SkillNodeComponent nodeComponent = store.getComponent(defenderRef, nodeComponentType);
        if (nodeComponent == null) {
            return false;
        }

        damage.setCancelled(true);

        if (!(damage.getSource() instanceof Damage.EntitySource entitySource)) {
            return true;
        }

        Ref<EntityStore> attackerRef = entitySource.getRef();
        if (attackerRef == null || !attackerRef.isValid()) {
            return true;
        }

        Player playerComponent = store.getComponent(attackerRef, Player.getComponentType());
        if (playerComponent == null) {
            return true;
        }

        PlayerRef playerRef = store.getComponent(attackerRef, PlayerRef.getComponentType());
        if (playerRef == null || !playerRef.isValid()) {
            return true;
        }

        UUID playerId = playerRef.getUuid();
        UUID nodeOwnerId = nodeComponent.getOwnerPlayerId();

        if (!playerId.equals(nodeOwnerId)) {
            return true;
        }

        String nodeId = nodeComponent.getNodeId();
        SkillTreeManager skillTreeManager = pluginInstance.getSkillTreeManager();
        if (skillTreeManager == null) {
            return true;
        }

        Optional<SkillNode> nodeOpt = skillTreeManager.getNode(nodeId);
        if (nodeOpt.isEmpty()) {
            return true;
        }

        SkillNodeHudManager hudManager = pluginInstance.getSkillSanctumManager().getSkillNodeHudManager();
        String currentNodeId = hudManager.getActiveNodeId(playerId);
        if (nodeId.equals(currentNodeId)) {
            hudManager.removeHud(playerId);
            return true;
        }

        // Skip if a HUD was just opened — prevents client crash from rapid
        // add/hide/remove CustomUI command bursts during multi-hit swings
        if (hudManager.isOnCooldown(playerId)) {
            return true;
        }

        SkillNode node = nodeOpt.get();
        NodeState nodeState = nodeComponent.getState();
        int availablePoints = skillTreeManager.getAvailablePoints(playerId);

        // Check if this node can be deallocated (connectivity + refund points)
        boolean canDeallocate = (nodeState == NodeState.ALLOCATED)
            && skillTreeManager.canDeallocate(playerId, nodeId)
            && skillTreeManager.getSkillTreeData(playerId).getSkillRefundPoints() > 0;

        SkillNodeDetailHud detailHud = new SkillNodeDetailHud(
            pluginInstance, playerRef, node, nodeState, availablePoints, canDeallocate, hudManager);
        detailHud.show();

        LOGGER.atInfo().log("Opened skill node detail HUD for player=%s, node=%s",
            playerId.toString().substring(0, 8), nodeId);

        return true;
    }

    // ==================== Debug Logging Helpers ====================

    private void logAttackerStats(
        @Nullable ComputedStats stats,
        @Nullable ElementalStats elemental,
        boolean hasRpgWeapon,
        float rpgWeaponDamage
    ) {
        LOGGER.at(Level.FINE).log("[DmgPipeline] ──── ATTACKER STATS ────");
        if (stats == null) {
            LOGGER.at(Level.FINE).log("[DmgPipeline] (no stats - environment/mob damage)");
            return;
        }

        String weaponId = stats.getWeaponItemId() != null ? stats.getWeaponItemId() : "(none)";
        LOGGER.at(Level.FINE).log("[DmgPipeline] Weapon: %s | RPG: %s | BaseDmg: %.1f",
            weaponId, hasRpgWeapon, rpgWeaponDamage);
        LOGGER.at(Level.FINE).log("[DmgPipeline] Physical: flat=%.1f, %%=%.1f",
            stats.getPhysicalDamage(), stats.getPhysicalDamagePercent());
        LOGGER.at(Level.FINE).log("[DmgPipeline] Melee: flat=%.1f, %%=%.1f",
            stats.getMeleeDamage(), stats.getMeleeDamagePercent());
        LOGGER.at(Level.FINE).log("[DmgPipeline] Crit: %.1f%% / x%.2f",
            stats.getCriticalChance(), stats.getCriticalMultiplier() / 100f);
        LOGGER.at(Level.FINE).log("[DmgPipeline] Armor Pen: %.1f%% | True Dmg: %.1f",
            stats.getArmorPenetration(), stats.getTrueDamage());
        LOGGER.at(Level.FINE).log("[DmgPipeline] All Dmg%%: %.1f | Multiplier: %.1f",
            stats.getAllDamagePercent(), stats.getDamageMultiplier());
        LOGGER.at(Level.FINE).log("[DmgPipeline] Life Steal: %.1f%% | Mana Leech: %.1f%%",
            stats.getLifeSteal(), stats.getManaLeech());

        // Elemental stats from attacker's elemental
        if (elemental != null) {
            LOGGER.at(Level.FINE).log("[DmgPipeline] Elemental - Fire: %.1f+%.1f%% | Cold: %.1f+%.1f%% | Lightning: %.1f+%.1f%% | Chaos: %.1f+%.1f%%",
                elemental.getFlatDamage(ElementType.FIRE), elemental.getPercentDamage(ElementType.FIRE),
                elemental.getFlatDamage(ElementType.WATER), elemental.getPercentDamage(ElementType.WATER),
                elemental.getFlatDamage(ElementType.LIGHTNING), elemental.getPercentDamage(ElementType.LIGHTNING),
                elemental.getFlatDamage(ElementType.VOID), elemental.getPercentDamage(ElementType.VOID));
        }

        // Conversions
        float fireConv = stats.getFireConversion();
        float coldConv = stats.getWaterConversion();
        float lightConv = stats.getLightningConversion();
        float chaosConv = stats.getVoidConversion();
        if (fireConv > 0 || coldConv > 0 || lightConv > 0 || chaosConv > 0) {
            LOGGER.at(Level.FINE).log("[DmgPipeline] Conversions - Fire: %.1f%% | Cold: %.1f%% | Lightning: %.1f%% | Chaos: %.1f%%",
                fireConv, coldConv, lightConv, chaosConv);
        }
    }

    private void logDefenderStats(
        @Nullable ComputedStats stats,
        @Nullable ElementalStats elemental
    ) {
        LOGGER.at(Level.FINE).log("[DmgPipeline] ──── DEFENDER STATS ────");
        if (stats == null) {
            LOGGER.at(Level.FINE).log("[DmgPipeline] (no stats)");
            return;
        }

        LOGGER.at(Level.FINE).log("[DmgPipeline] Armor: %.1f | Phys Resist: %.1f%%",
            stats.getArmor(), stats.getPhysicalResistance());
        LOGGER.at(Level.FINE).log("[DmgPipeline] Evasion: %.1f | Dodge: %.1f%% | Passive Block: %.1f%%",
            stats.getEvasion(), stats.getDodgeChance(), stats.getPassiveBlockChance());
        LOGGER.at(Level.FINE).log("[DmgPipeline] Crit Nullify: %.1f%%", stats.getCritNullifyChance());
        LOGGER.at(Level.FINE).log("[DmgPipeline] Energy Shield: %.1f", stats.getEnergyShield());

        // Elemental resistances
        if (elemental != null) {
            LOGGER.at(Level.FINE).log("[DmgPipeline] Elemental Resist - Fire: %.1f%% | Cold: %.1f%% | Lightning: %.1f%% | Chaos: %.1f%%",
                elemental.getResistance(ElementType.FIRE),
                elemental.getResistance(ElementType.WATER),
                elemental.getResistance(ElementType.LIGHTNING),
                elemental.getResistance(ElementType.VOID));
        }
    }

    private void logDamageBreakdown(
        @Nonnull DamageBreakdown breakdown,
        float rpgBaseDamage,
        float attackTypeMultiplier,
        float conditionalMultiplier,
        @Nullable ComputedStats attackerStats,
        @Nullable ComputedStats defenderStats,
        @Nullable ElementalStats attackerElemental,
        @Nullable ElementalStats defenderElemental,
        float finalDamage
    ) {
        LOGGER.at(Level.FINE).log("[DmgPipeline] ──── DAMAGE BREAKDOWN ────");

        // Base calculation
        float rawCondMult = conditionalMultiplier / attackTypeMultiplier;
        LOGGER.at(Level.FINE).log("[DmgPipeline] Base Input: %.1f × attackMult(%.2f) × condMult(%.2f)",
            rpgBaseDamage, attackTypeMultiplier, rawCondMult);

        // Physical damage with armor
        float armorReduct = breakdown.armorReduction();
        LOGGER.at(Level.FINE).log("[DmgPipeline] Physical: %.1f (after armor: -%.1f%%)",
            breakdown.physicalDamage(), armorReduct);

        // Elemental damage with resistances
        for (ElementType elem : ElementType.values()) {
            float elemDmg = breakdown.getElementalDamage(elem);
            float elemResist = breakdown.getResistanceReduction(elem);
            if (elemDmg > 0 || elemResist > 0) {
                LOGGER.at(Level.FINE).log("[DmgPipeline] %s: %.1f (resist: -%.1f%%)",
                    elem.name(), elemDmg, elemResist);
            }
        }

        // True damage
        if (breakdown.trueDamage() > 0) {
            LOGGER.at(Level.FINE).log("[DmgPipeline] True: %.1f", breakdown.trueDamage());
        }

        // Critical
        String critStr = breakdown.wasCritical()
            ? String.format("YES x%.2f", breakdown.critMultiplier())
            : "NO";
        LOGGER.at(Level.FINE).log("[DmgPipeline] Critical: %s", critStr);

        // Final summary
        LOGGER.at(Level.FINE).log("[DmgPipeline] ═══════════════════════════════════════════");
        LOGGER.at(Level.FINE).log("[DmgPipeline] FINAL DAMAGE: %.1f (%s)", finalDamage, breakdown.damageType());
        LOGGER.at(Level.FINE).log("[DmgPipeline] ════════════ DAMAGE EVENT END ════════════");
    }

    // ==================== Combat Log Helper Methods ====================

    /**
     * Extracts raw elemental resistances from defender's elemental stats for combat log display.
     *
     * @param elemental The defender's elemental stats (may be null)
     * @return Map of raw resistance values per element, or null if no stats
     */
    @Nullable
    private Map<ElementType, Float> extractRawResistances(@Nullable ElementalStats elemental) {
        if (elemental == null) {
            return null;
        }

        EnumMap<ElementType, Float> result = new EnumMap<>(ElementType.class);
        boolean hasAny = false;
        for (ElementType type : ElementType.values()) {
            float resistance = (float) elemental.getResistance(type);
            result.put(type, resistance);
            if (resistance != 0f) {
                hasAny = true;
            }
        }
        return hasAny ? result : null;
    }

    /**
     * Extracts elemental penetration values from attacker's elemental stats for combat log display.
     *
     * @param elemental The attacker's elemental stats (may be null)
     * @return Map of penetration values per element, or null if no penetration
     */
    @Nullable
    private Map<ElementType, Float> extractPenetration(@Nullable ElementalStats elemental) {
        if (elemental == null) {
            return null;
        }

        EnumMap<ElementType, Float> result = new EnumMap<>(ElementType.class);
        boolean hasAny = false;
        for (ElementType type : ElementType.values()) {
            float penetration = (float) elemental.getPenetration(type);
            result.put(type, penetration);
            if (penetration > 0f) {
                hasAny = true;
            }
        }
        return hasAny ? result : null;
    }
}
