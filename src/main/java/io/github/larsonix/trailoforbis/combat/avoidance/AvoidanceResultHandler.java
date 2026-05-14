package io.github.larsonix.trailoforbis.combat.avoidance;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.damage.DamageDataComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.combat.AttackType;
import io.github.larsonix.trailoforbis.combat.CombatFeedbackService;
import io.github.larsonix.trailoforbis.combat.RPGDamageSystem;
import io.github.larsonix.trailoforbis.combat.deathrecap.DeathRecapRecorder;
import io.github.larsonix.trailoforbis.combat.effects.CombatEffectContext;
import io.github.larsonix.trailoforbis.combat.effects.CombatEffectRegistry;
import io.github.larsonix.trailoforbis.combat.indicators.CombatIndicatorService;
import io.github.larsonix.trailoforbis.combat.recovery.CombatRecoveryProcessor;
import io.github.larsonix.trailoforbis.combat.resolution.CombatEntityResolver;
import io.github.larsonix.trailoforbis.combat.resolution.CombatStatsResolver;
import io.github.larsonix.trailoforbis.combat.triggers.CombatTriggerHandler;
import io.github.larsonix.trailoforbis.gear.util.GearUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Handles the result of avoidance checks (dodge, block, parry, miss) and active
 * blocking damage reduction.
 *
 * <p>Complements {@link AvoidanceProcessor} which handles the CHECK (roll dice).
 * This handler processes the RESULT — sets metadata, sends indicators/particles,
 * fires triggers, applies block heal/counter, and handles active blocking reduction.
 *
 * <p>Extracted from RPGDamageSystem Phases 3 (avoidance result) and 5.5 (active blocking).
 */
public class AvoidanceResultHandler {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // ── Active Blocking Reduction Constants ──
    /** Base damage reduction when weapon-blocking (right-click with weapon). */
    private static final float WEAPON_BLOCK_REDUCTION = 0.33f;
    /** Base damage reduction when shield-blocking (shield in utility slot). */
    private static final float SHIELD_BLOCK_REDUCTION = 0.66f;
    /** Maximum total reduction for weapon blocking (base + BLOCK_DAMAGE_REDUCTION stat). */
    private static final float WEAPON_BLOCK_CAP = 0.80f;
    /** Maximum total reduction for shield blocking (base + BLOCK_DAMAGE_REDUCTION stat). */
    private static final float SHIELD_BLOCK_CAP = 0.95f;

    private final CombatEntityResolver entityResolver;
    private final CombatStatsResolver statsResolver;
    private final CombatIndicatorService indicatorService;
    private final CombatFeedbackService feedbackService;
    private final CombatRecoveryProcessor recoveryProcessor;
    private final DeathRecapRecorder deathRecapRecorder;
    private final CombatTriggerHandler triggerHandler;

    @Nullable
    private volatile CombatEffectRegistry combatEffectRegistry;

    public AvoidanceResultHandler(
        @Nonnull CombatEntityResolver entityResolver,
        @Nonnull CombatStatsResolver statsResolver,
        @Nonnull CombatIndicatorService indicatorService,
        @Nonnull CombatFeedbackService feedbackService,
        @Nonnull CombatRecoveryProcessor recoveryProcessor,
        @Nonnull DeathRecapRecorder deathRecapRecorder,
        @Nonnull CombatTriggerHandler triggerHandler
    ) {
        this.entityResolver = entityResolver;
        this.statsResolver = statsResolver;
        this.indicatorService = indicatorService;
        this.feedbackService = feedbackService;
        this.recoveryProcessor = recoveryProcessor;
        this.deathRecapRecorder = deathRecapRecorder;
        this.triggerHandler = triggerHandler;
    }

    public void setCombatEffectRegistry(@Nullable CombatEffectRegistry registry) {
        this.combatEffectRegistry = registry;
    }

    /**
     * Handles fully-avoided damage (dodge/block/parry/miss).
     *
     * <p>Sets metadata, sends indicators, fires triggers, handles block heal/counter,
     * and runs combat effect hooks for avoidance.
     */
    public void handleAvoidedDamage(
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
            case DODGED, EVADED -> damage.putMetaObject(RPGDamageSystem.WAS_DODGED, true);
            case BLOCKED -> damage.putMetaObject(RPGDamageSystem.WAS_BLOCKED, true);
            case PARRIED -> damage.putMetaObject(RPGDamageSystem.WAS_PARRIED, true);
            case MISSED -> damage.putMetaObject(RPGDamageSystem.WAS_MISSED, true);
        }
        damage.putMetaObject(RPGDamageSystem.RPG_DAMAGE_VALUE, 0f);
        damage.setCancelled(true);

        // Send avoidance indicators (floating text to attacker + defender)
        indicatorService.sendAvoidanceIndicators(store, defenderRef, damage, result.reason());
        damage.putMetaObject(RPGDamageSystem.INDICATORS_SENT, true);

        // Spawn avoidance particles and sounds (shield bash, parry reflect)
        feedbackService.onDamageAvoided(store, defenderRef, result.reason());

        // Send avoidance log to defender's chat (shows them they avoided damage)
        DeathRecapRecorder.AttackerInfo attackerInfo = deathRecapRecorder.getAttackerInfo(store, damage);
        indicatorService.sendAvoidanceLog(store, defenderRef, result.reason(), attackerInfo, result.estimatedDamage(), avoidanceStats);

        // Fire appropriate trigger
        switch (result.reason()) {
            case DODGED, EVADED -> triggerHandler.fireOnEvadeTrigger(index, archetypeChunk, store);
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

        // ── Combat Effect Hooks: Avoidance ──
        if (combatEffectRegistry != null) {
            UUID attackerUuid = entityResolver.getAttackerPlayerUuid(store, damage);
            UUID defenderUuid = entityResolver.getDefenderUuid(index, archetypeChunk, store);
            if (defenderUuid != null) {
                Ref<EntityStore> atkRef = (damage.getSource() instanceof Damage.EntitySource es) ? es.getRef() : null;
                CombatEffectContext avoidCtx = new CombatEffectContext(
                    attackerUuid, defenderUuid, null, null, null, null,
                    null, null, AttackType.UNKNOWN, null,
                    0f, result.estimatedDamage(), 0f, 0f, false,
                    damage, store, defenderRef, atkRef, null, -1f
                );
                combatEffectRegistry.runOnAvoidance(attackerUuid, defenderUuid, result.reason(), avoidCtx);
            }
        }
    }

    /**
     * Applies block counter damage to the attacker.
     *
     * <p>When a defender has the BLOCK_COUNTER_DAMAGE stat, a percentage of the
     * blocked damage is reflected back as physical damage to the attacker.
     */
    public void applyBlockCounterDamage(
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

        LOGGER.at(Level.FINE).log("Block counter: reflected %.1f damage to attacker (%.1f -> %.1f HP)",
            counterDamage, healthBefore, newHealth);
    }

    /**
     * Result of applying active blocking reduction. Returned to the caller
     * so it can update the pipeline context.
     *
     * @param newDamage The damage after blocking reduction
     * @param wasBlocking Whether active blocking was applied
     * @param hasShield Whether a shield was used (vs weapon)
     * @param reductionPercent Total reduction percentage applied
     * @param preDamage Damage before the reduction
     */
    public record BlockingResult(float newDamage, boolean wasBlocking, boolean hasShield,
                                  float reductionPercent, float preDamage) {}

    /**
     * Phase 5.5: Applies deterministic active blocking damage reduction.
     *
     * <p>When the defender is holding block (right-click) but didn't get a perfect
     * block (handled in avoidance pipeline), they receive a base reduction that
     * scales with gear. Returns a result so the caller can update DamageContext
     * and DamageTrace.
     *
     * @param store The entity store
     * @param defenderRef The defender entity reference
     * @param defenderStats The defender's computed stats (nullable)
     * @param currentDamage Current rpgDamage value
     * @return Blocking result with new damage and metadata
     */
    public BlockingResult applyActiveBlockingReduction(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> defenderRef,
        @Nullable ComputedStats defenderStats,
        float currentDamage
    ) {
        // Check if defender is actively blocking (works for both players and mobs)
        DamageDataComponent damageData = store.getComponent(defenderRef, DamageDataComponent.getComponentType());
        if (damageData == null || damageData.getCurrentWielding() == null) {
            return new BlockingResult(currentDamage, false, false, 0f, currentDamage);
        }

        // Determine shield vs weapon blocking
        boolean hasShield = detectShield(store, defenderRef);

        float baseReduction = hasShield ? SHIELD_BLOCK_REDUCTION : WEAPON_BLOCK_REDUCTION;
        float cap = hasShield ? SHIELD_BLOCK_CAP : WEAPON_BLOCK_CAP;

        // Add BLOCK_DAMAGE_REDUCTION from gear, capped
        float statBonus = (defenderStats != null)
            ? defenderStats.getBlockDamageReduction() / 100f
            : 0f;
        float totalReduction = Math.min(baseReduction + statBonus, cap);

        float newDamage = currentDamage * (1.0f - totalReduction);

        LOGGER.atFine().log("Active block: %s %.0f%% (base %.0f%% + gear %.0f%%), damage: %.1f -> %.1f",
            hasShield ? "SHIELD" : "WEAPON",
            totalReduction * 100, baseReduction * 100, statBonus * 100,
            currentDamage, newDamage);

        return new BlockingResult(newDamage, true, hasShield, totalReduction * 100f, currentDamage);
    }

    /**
     * Detects whether the blocking entity is using a shield (vs weapon blocking).
     *
     * <p>For players, checks the active utility item for "shield" in the item ID.
     * For mobs/NPCs, defaults to weapon block (Player component is unavailable).
     */
    private boolean detectShield(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> defenderRef) {
        Player player = store.getComponent(defenderRef, Player.getComponentType());
        if (player == null || player.getInventory() == null) {
            return false;
        }
        ItemStack activeUtility = player.getInventory().getUtilityItem();
        if (ItemStack.isEmpty(activeUtility)) {
            return false;
        }
        String baseId = GearUtils.getBaseItemId(activeUtility);
        String checkId = (baseId != null) ? baseId : activeUtility.getItemId();
        return checkId != null && checkId.toLowerCase().contains("shield");
    }
}
