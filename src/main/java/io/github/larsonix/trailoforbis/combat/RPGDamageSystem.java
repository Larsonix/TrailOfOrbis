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
import io.github.larsonix.trailoforbis.compat.HexCastEventInterceptor;
import io.github.larsonix.trailoforbis.compat.HexcodeCompat;
import io.github.larsonix.trailoforbis.compat.HexcodeSpellConfig;
import io.github.larsonix.trailoforbis.config.RPGConfig;
import io.github.larsonix.trailoforbis.mobs.classification.RPGMobClass;
import io.github.larsonix.trailoforbis.mobs.component.MobScalingComponent;
import io.github.larsonix.trailoforbis.mobs.model.MobStats;
import io.github.larsonix.trailoforbis.mobs.stats.MobStatPoolConfig;
import com.hypixel.hytale.server.core.modules.entity.component.Invulnerable;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;

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
import io.github.larsonix.trailoforbis.maps.RealmsManager;
import io.github.larsonix.trailoforbis.maps.components.RealmMobComponent;
import io.github.larsonix.trailoforbis.maps.instance.RealmInstance;
import io.github.larsonix.trailoforbis.maps.modifiers.RealmModifierType;
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

    /** MetaKey for hex spell element (non-null means this damage is a spell). */
    public static final MetaKey<ElementType> HEX_SPELL_ELEMENT = Damage.META_REGISTRY.registerMetaObject(d -> null);

    /** MetaKey for hex spell base damage (before weapon implicit is added). */
    public static final MetaKey<Float> HEX_SPELL_BASE_DAMAGE = Damage.META_REGISTRY.registerMetaObject(d -> null);

    /** MetaKey for hex spell display name (for death recap). */
    public static final MetaKey<String> HEX_SPELL_DISPLAY_NAME = Damage.META_REGISTRY.registerMetaObject(d -> null);

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
    private volatile CombatRequirementNotifier requirementNotifier;

    public RPGDamageSystem(@Nonnull TrailOfOrbis plugin) {
        this.plugin = plugin;
    }

    // ==================== Lazy Initialization ====================

    private CombatCalculator getCalculator() {
        if (calculator == null) {
            synchronized (this) {
                if (calculator == null) {
                    var armorConfig = plugin.getConfigManager().getRPGConfig().getArmor();
                    calculator = new CombatCalculator(armorConfig.getMaxReduction(), armorConfig.getLevelScale(), armorConfig.getBaseConstant());
                    calculator.setMinArmorEffectiveness(armorConfig.getArmorPenetrationFloor());
                    LOGGER.at(Level.INFO).log("CombatCalculator initialized (levelScale=%.1f, baseConstant=%.1f, pen floor: %.0f%%)",
                        armorConfig.getLevelScale(), armorConfig.getBaseConstant(), armorConfig.getArmorPenetrationFloor() * 100);
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
                        localResolver, classifier, plugin.getDeathRecapTracker(),
                        plugin.getMobScalingComponentType(),
                        playerId -> plugin.getLevelingManager() != null
                            ? plugin.getLevelingManager().getLevel(playerId) : 1);
                    // Requirement notification on attack (lazy — GearManager may not be ready at system creation)
                    if (plugin.getGearManager() != null && plugin.getGearManager().getEquipmentValidator() != null) {
                        requirementNotifier = new CombatRequirementNotifier(plugin.getGearManager().getEquipmentValidator());
                    }
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
        // Resolve caster and rewrite source so spells flow through the main RPG pipeline.
        // Must be checked BEFORE generic environmental damage so spells use RPG scaling.
        if (damage.getSource() instanceof Damage.EnvironmentSource envSource) {
            String sourceType = envSource.getType();
            if (HexcodeSpellConfig.isHexSpellSource(sourceType)) {
                HexcodeSpellConfig spellConfig = plugin.getConfigManager().getHexcodeSpellConfig();
                if (spellConfig == null || !spellConfig.isEnabled()) {
                    handleEnvironmentalDamage(index, archetypeChunk, store, commandBuffer, damage);
                    return;
                }

                // Resolve caster via ThreadLocal (same synchronous invoke chain) or recent caster fallback
                Ref<EntityStore> casterRef = HexCastEventInterceptor.getFreshCaster();
                if (casterRef == null || !casterRef.isValid()) {
                    casterRef = HexCastEventInterceptor.findRecentCaster(store);
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
                            ItemStack weapon = casterEntity.getInventory().getItemInHand();
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

                LOGGER.atFine().log("[SpellDmg] %s: routing through main pipeline, element=%s, hexBase=%.1f",
                    sourceType, element != null ? element.name() : "NONE", vanillaDamage);

                // Fall through to the main damage pipeline (no return)
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
            handleAvoidedDamage(index, archetypeChunk, store, defenderRef, damage, avoidance.get(), ctx.avoidanceStats);
            return;
        }

        LOGGER.at(Level.FINE).log("[DmgPipeline] ──── AVOIDANCE CHECK ────");
        LOGGER.at(Level.FINE).log("[DmgPipeline] Result: HIT");

        // ========== PHASE 4: SINGLE CALCULATOR CALL ==========
        ctx.trace = getRPGCalculator().calculateTraced(
            ctx.rpgBaseDamage, ctx.attackerStats, ctx.attackerElemental, ctx.defenderStats, ctx.defenderElemental,
            ctx.attackType, ctx.conditionalMultiplier, ctx.conditionalDetail, ctx.attackTypeMultiplier,
            ctx.spellElement, ctx.attackerLevel, ctx.isHexSpell);
        ctx.trace.setAttackerBreakdown(null); // will be populated later if player
        ctx.breakdown = ctx.trace.breakdown();

        // ========== PHASE 4.5: POPULATE TRACE CONTEXT ==========
        populateTraceContext(store, damage, ctx);

        // ========== PHASE 5: POST-CALC ADJUSTMENTS ==========
        applyPostCalcModifications(store, commandBuffer, damage, ctx);

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

        // Spell-specific (null for non-spell attacks)
        @Nullable ElementType spellElement;
        // True if this is a Hexcode spell (EnvironmentSource origin) — uses uncancelled finalize path
        // Also determines projectile scaling: hex spells always benefit from projectileDamagePercent
        // because they require a delivery glyph (Beam/Projectile) to reach the target.
        boolean isHexSpell;

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
        ctx.attackerStats = statsResolver.getAttackerStats(store, damage);
        ctx.defenderStats = statsResolver.getDefenderStats(index, archetypeChunk, store);
        ctx.attackerElemental = statsResolver.getAttackerElementalStats(store, damage);
        ctx.defenderElemental = statsResolver.getDefenderElementalStats(index, archetypeChunk, store, ctx.defenderStats);
        ctx.defenderHealthPercent = entityResolver.getDefenderHealthPercent(index, archetypeChunk, store);
        ctx.defenderUuid = entityResolver.getDefenderUuid(index, archetypeChunk, store);

        // Check if this is a hex spell routed through the main pipeline.
        // Gate on HEX_SPELL_BASE_DAMAGE (always set for hex spells), not element
        // (element can be null for unmapped/default spell types).
        Float hexBaseMeta = damage.getIfPresentMetaObject(HEX_SPELL_BASE_DAMAGE);
        boolean isSpellDamage = (hexBaseMeta != null);

        if (isSpellDamage) {
            // ---- Hex spell damage path ----
            // Base = hex spell base (scaled) + weapon implicit (staff scales spell power)
            ElementType spellElementMeta = damage.getIfPresentMetaObject(HEX_SPELL_ELEMENT);
            ctx.attackType = AttackType.SPELL;
            ctx.isHexSpell = true;
            ctx.spellElement = spellElementMeta; // May be null for unmapped spells (physical magic)
            ctx.rpgWeaponDamage = (ctx.attackerStats != null) ? ctx.attackerStats.getWeaponBaseDamage() : 0f;
            ctx.hasRpgWeapon = (ctx.attackerStats != null) && ctx.attackerStats.isHoldingRpgGear();

            // Apply spell_base_multiplier to hex base damage to prevent double-scaling
            // with our spell stats pipeline. Weapon implicit stays at full value.
            float scaledHexBase = hexBaseMeta;
            HexcodeSpellConfig spellCfg = plugin.getConfigManager().getHexcodeSpellConfig();
            if (spellCfg != null) {
                scaledHexBase = Math.max(0f, hexBaseMeta * spellCfg.getSpell_base_multiplier());
            }
            ctx.rpgBaseDamage = scaledHexBase + ctx.rpgWeaponDamage;
            ctx.attackTypeMultiplier = 1.0f; // No light/heavy differentiation for spells

            LOGGER.at(Level.FINE).log("[DmgPipeline] HEX SPELL damage: element=%s, hexBase=%.1f (×%.2f=%.1f) + weaponImplicit=%.1f = %.1f",
                spellElementMeta != null ? spellElementMeta.name() : "NONE",
                hexBaseMeta, spellCfg != null ? spellCfg.getSpell_base_multiplier() : 1.0f,
                scaledHexBase, ctx.rpgWeaponDamage, ctx.rpgBaseDamage);
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
                ctx.rpgBaseDamage = getWeaponBaseDamage(damage, ctx.attackerStats, ctx.vanillaDamage, store);
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
                    ctx.spellElement = fixedElement != null ? fixedElement : resolveDominantSpellElement(ctx.attackerElemental);
                    ctx.attackTypeMultiplier = 1.0f; // No light/heavy differentiation for spells
                    LOGGER.at(Level.FINE).log("[DmgPipeline] Magic weapon override: %s → SPELL (element=%s, fixed=%s)",
                        weaponItemId, ctx.spellElement != null ? ctx.spellElement.name() : "NONE",
                        fixedElement != null);
                }
            }
        }

        // Resolve attacker level for armor formula (needed BEFORE calculator runs)
        ctx.attackerLevel = resolveAttackerLevel(store, damage);

        // Log attack type
        LOGGER.at(Level.FINE).log("[DmgPipeline] Attack type: %s, attackerLevel: %d", ctx.attackType, ctx.attackerLevel);

        // Log attacker/defender stats (FINE-level)
        logAttackerStats(ctx.attackerStats, ctx.attackerElemental, ctx.hasRpgWeapon, ctx.rpgWeaponDamage);
        logDefenderStats(ctx.defenderStats, ctx.defenderElemental);

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
            int vulnPercent = getRealmPlayerVulnerability(ctx.defenderUuid);
            if (vulnPercent > 0) {
                ctx.rpgDamage *= (1.0f + vulnPercent / 100.0f);
            }
        }

        // Realm ARMORED_MONSTERS — mob takes reduced physical damage (extra armor)
        if (ctx.rpgDamage > 0 && ctx.defenderUuid == null) {
            // defenderUuid is null for mobs (only set for player defenders)
            int armorBonus = getRealmArmoredMonstersBonus(store, ctx.defenderRef);
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
            float elementalBonus = calculateRealmElementalBonusDamage(
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

                    // Sync ECS components — remove burn/poison components since DOTs were detonated
                    if (ctx.defenderRef != null && ctx.defenderRef.isValid()) {
                        if (io.github.larsonix.trailoforbis.ailments.component.RpgBurnComponent.TYPE != null) {
                            commandBuffer.removeComponent(ctx.defenderRef,
                                io.github.larsonix.trailoforbis.ailments.component.RpgBurnComponent.TYPE);
                        }
                        if (io.github.larsonix.trailoforbis.ailments.component.RpgPoisonComponent.TYPE != null) {
                            commandBuffer.removeComponent(ctx.defenderRef,
                                io.github.larsonix.trailoforbis.ailments.component.RpgPoisonComponent.TYPE);
                        }
                    }

                    LOGGER.at(Level.FINE).log("DoT detonation: %.1f remaining DoT × %.0f%% = %.1f burst damage",
                        remainingDot, burstPct, burstDamage);
                }
            }
        }

        // SPELL_ECHO_CHANCE: spell attacks have X% chance to repeat for 50% as Void damage
        // Gates on AttackType.SPELL (not DamageType.MAGIC) so all spell elements can echo
        if (ctx.attackerStats != null && ctx.attackerStats.getSpellEchoChance() > 0
                && ctx.attackType == AttackType.SPELL) {
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
                ctx.attackerElemental, ctx.attackerStats, ctx.maxHealth, ctx.defenderStats, commandBuffer);
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
                healAmount *= getRealmHealingMultiplier(ctx.defenderUuid);
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

            // Resolve realm REDUCED_HEALING multiplier for the attacker (once per damage event)
            UUID attackerUuid = entityResolver.getAttackerPlayerUuid(store, damage);
            float realmHealMult = getRealmHealingMultiplier(attackerUuid);

            // Life Leech - always heals from damage dealt
            float lifeLeech = ctx.attackerStats.getLifeLeech();
            if (lifeLeech > 0) {
                float healAmount = ctx.rpgDamage * (lifeLeech / 100f);
                if (healthRecoveryPct != 0) {
                    healAmount *= (1.0f + healthRecoveryPct / 100.0f);
                }
                healAmount *= realmHealMult;
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
                healAmount *= realmHealMult;
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

    /** Mapping of elemental modifier types to their element. */
    private static final java.util.Map<RealmModifierType, ElementType> ELEMENTAL_MODIFIERS = java.util.Map.of(
        RealmModifierType.MONSTERS_EXTRA_FIRE, ElementType.FIRE,
        RealmModifierType.MONSTERS_EXTRA_WATER, ElementType.WATER,
        RealmModifierType.MONSTERS_EXTRA_LIGHTNING, ElementType.LIGHTNING,
        RealmModifierType.MONSTERS_EXTRA_EARTH, ElementType.EARTH,
        RealmModifierType.MONSTERS_EXTRA_WIND, ElementType.WIND,
        RealmModifierType.MONSTERS_EXTRA_VOID, ElementType.VOID
    );

    /**
     * Calculates bonus elemental damage from realm MONSTERS_EXTRA_[ELEMENT] modifiers.
     *
     * <p>When a realm mob attacks a player, each elemental modifier adds bonus damage
     * of that element, reduced by the player's resistance.
     *
     * @param store The entity store
     * @param damage The damage event (to resolve attacker's realm)
     * @param baseDamage The base damage dealt (before elemental bonus)
     * @param defenderElemental The defender's elemental stats (for resistance)
     * @return Total bonus elemental damage to add
     */
    private float calculateRealmElementalBonusDamage(
            @Nonnull Store<EntityStore> store,
            @Nonnull Damage damage,
            float baseDamage,
            @Nonnull ElementalStats defenderElemental) {

        // Resolve the attacker's realm (mob → realm)
        Ref<EntityStore> attackerRef = entityResolver.getAttackerRef(store, damage);
        if (attackerRef == null) {
            return 0f;
        }
        RealmMobComponent realmMob = store.getComponent(attackerRef, RealmMobComponent.getComponentType());
        if (realmMob == null || realmMob.getRealmId() == null) {
            return 0f;
        }
        TrailOfOrbis rpg = TrailOfOrbis.getInstanceOrNull();
        if (rpg == null) {
            return 0f;
        }
        RealmsManager rm = rpg.getRealmsManager();
        if (rm == null) {
            return 0f;
        }
        Optional<RealmInstance> realmOpt = rm.getRealm(realmMob.getRealmId());
        if (realmOpt.isEmpty()) {
            return 0f;
        }

        // Check each elemental modifier
        float totalBonus = 0f;
        var mapData = realmOpt.get().getMapData();
        for (var entry : ELEMENTAL_MODIFIERS.entrySet()) {
            int modValue = mapData.getModifierValue(entry.getKey());
            if (modValue > 0) {
                ElementType element = entry.getValue();
                float rawBonus = baseDamage * (modValue / 100.0f);
                // Reduce by player's resistance to this element
                double resistance = defenderElemental.getResistance(element);
                float afterResist = rawBonus * (1.0f - (float) (resistance / 100.0));
                if (afterResist > 0) {
                    totalBonus += afterResist;
                }
            }
        }
        return totalBonus;
    }

    /**
     * Returns the ARMORED_MONSTERS modifier value for a realm mob defender.
     *
     * @param store The entity store
     * @param defenderRef The defender entity reference (mob)
     * @return Armor bonus percentage (0 if not a realm mob or no modifier)
     */
    private int getRealmArmoredMonstersBonus(@Nonnull Store<EntityStore> store, @Nullable Ref<EntityStore> defenderRef) {
        if (defenderRef == null || !defenderRef.isValid()) {
            return 0;
        }
        RealmMobComponent realmMob = store.getComponent(defenderRef, RealmMobComponent.getComponentType());
        if (realmMob == null || realmMob.getRealmId() == null) {
            return 0;
        }
        TrailOfOrbis rpg = TrailOfOrbis.getInstanceOrNull();
        if (rpg == null) {
            return 0;
        }
        RealmsManager rm = rpg.getRealmsManager();
        if (rm == null) {
            return 0;
        }
        Optional<RealmInstance> realmOpt = rm.getRealm(realmMob.getRealmId());
        if (realmOpt.isEmpty()) {
            return 0;
        }
        return realmOpt.get().getMapData().getModifierValue(RealmModifierType.ARMORED_MONSTERS);
    }

    /**
     * Returns the PLAYER_VULNERABILITY modifier value for a player in a realm.
     *
     * @param defenderUuid The player UUID being damaged
     * @return Vulnerability percentage (0 if not in realm or no modifier)
     */
    private int getRealmPlayerVulnerability(@Nonnull UUID defenderUuid) {
        TrailOfOrbis rpg = TrailOfOrbis.getInstanceOrNull();
        if (rpg == null) {
            return 0;
        }
        RealmsManager rm = rpg.getRealmsManager();
        if (rm == null) {
            return 0;
        }
        Optional<RealmInstance> realmOpt = rm.getPlayerRealm(defenderUuid);
        if (realmOpt.isEmpty()) {
            return 0;
        }
        return realmOpt.get().getMapData().getModifierValue(RealmModifierType.PLAYER_VULNERABILITY);
    }

    /**
     * Returns the healing multiplier from the REDUCED_HEALING realm modifier.
     *
     * <p>If the healer is inside a realm with REDUCED_HEALING, returns a value
     * less than 1.0 (e.g., 0.6 for 40% reduced healing). Returns 1.0 if not
     * in a realm or no modifier is present.
     *
     * @param healerUuid The UUID of the player receiving healing (may be null)
     * @return Healing multiplier in range (0, 1.0]
     */
    private float getRealmHealingMultiplier(@Nullable UUID healerUuid) {
        if (healerUuid == null) {
            return 1.0f;
        }
        TrailOfOrbis rpg = TrailOfOrbis.getInstanceOrNull();
        if (rpg == null) {
            return 1.0f;
        }
        RealmsManager rm = rpg.getRealmsManager();
        if (rm == null) {
            return 1.0f;
        }
        Optional<RealmInstance> realmOpt = rm.getPlayerRealm(healerUuid);
        if (realmOpt.isEmpty()) {
            return 1.0f;
        }
        int reduction = realmOpt.get().getMapData().getModifierValue(RealmModifierType.REDUCED_HEALING);
        return reduction > 0 ? 1.0f - (reduction / 100.0f) : 1.0f;
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

        if (ctx.attackType == AttackType.SPELL && ctx.isHexSpell) {
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
                triggerHandler.fireOnKillTrigger(store, damage);
            } else {
                triggerHandler.fireWhenHitTrigger(index, archetypeChunk, store);
            }

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
        DamageCause damageCause = DamageTypeClassifier.getDamageCause(damage);
        ElementType dotElement = classifier.getElementFromDOTCause(damageCause);

        int dotAttackerLevel = resolveAttackerLevel(store, damage);
        DamageBreakdown breakdown = getRPGCalculator().calculateDOT(
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

        damage.putMetaObject(DAMAGE_TYPE, breakdown.damageType());
        damage.putMetaObject(WAS_CRITICAL, false);
        damage.putMetaObject(RPG_DAMAGE_VALUE, rpgDamage);
        damage.putMetaObject(ATTACK_TYPE, AttackType.UNKNOWN);
        damage.setAmount(0);

        indicatorService.sendDamageIndicatorsVisualOnly(store, defenderRef, damage, rpgDamage, breakdown, false);
        damage.putMetaObject(INDICATORS_SENT, true);

        // SHIELD_REGEN_ON_DOT: restore energy shield to the DOT applicator
        processShieldRegenOnDot(damage, rpgDamage, store);

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

    /**
     * Processes SHIELD_REGEN_ON_DOT: restores energy shield to the DOT applicator
     * proportional to the DOT damage dealt.
     *
     * <p>Only fires if the source is an EntitySource (player who applied the DOT),
     * and that player has the shieldRegenOnDot stat.
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

    // handleSpellDamage REMOVED — spell damage now routes through the main pipeline.
    // Hex spells are detected early in handle(), metadata is set on the damage event,
    // source is rewritten to EntitySource, and gatherCombatInputs populates the
    // DamageContext with spell-specific inputs (AttackType.SPELL + spellElement).
    // The full RPG calculator pipeline now handles stat scaling, avoidance, crit,
    // recovery, and all other phases for spells.

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
     * Determines the dominant spell element for a magic weapon attack.
     *
     * <p>Uses the attacker's elemental stats to find which element has the highest
     * flat damage (indicating the strongest elemental investment). Falls back to
     * WATER (the spell attribute) if no elemental damage exists.
     *
     * @param elemental The attacker's elemental stats (may be null)
     * @return The dominant element, or WATER as fallback
     */
    @Nonnull
    private ElementType resolveDominantSpellElement(@Nullable ElementalStats elemental) {
        if (elemental == null) {
            return ElementType.WATER;
        }
        ElementType best = null;
        double bestVal = 0;
        for (ElementType type : ElementType.values()) {
            double flat = elemental.getFlatDamage(type);
            if (flat > bestVal) {
                bestVal = flat;
                best = type;
            }
        }
        return best != null ? best : ElementType.WATER;
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

        // Send avoidance indicators (floating text to attacker + defender)
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
        // Only the actively selected utility item counts — utility slots work like hotbar.
        // Check the BASE item ID because RPG gear shields have custom IDs (rpg_gear_xxx).
        Player player = store.getComponent(ctx.defenderRef, Player.getComponentType());
        boolean hasShield = false;
        if (player != null && player.getInventory() != null) {
            ItemStack activeUtility = player.getInventory().getUtilityItem();
            if (!ItemStack.isEmpty(activeUtility)) {
                String baseId = io.github.larsonix.trailoforbis.gear.util.GearUtils.getBaseItemId(activeUtility);
                String checkId = (baseId != null) ? baseId : activeUtility.getItemId();
                if (checkId != null && checkId.toLowerCase().contains("shield")) {
                    hasShield = true;
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

        // Calculate synergy progress for live display (null if not a synergy node)
        var synergyProgress = skillTreeManager.getSynergyProgress(playerId, node);

        SkillNodeDetailHud detailHud = new SkillNodeDetailHud(
            pluginInstance, playerRef, node, nodeState, availablePoints, canDeallocate, hudManager, synergyProgress);
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
