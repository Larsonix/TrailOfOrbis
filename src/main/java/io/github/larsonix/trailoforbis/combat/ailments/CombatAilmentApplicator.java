package io.github.larsonix.trailoforbis.combat.ailments;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.ailments.AilmentCalculator;
import io.github.larsonix.trailoforbis.ailments.AilmentEffectManager;
import io.github.larsonix.trailoforbis.ailments.AilmentImmunityTracker;
import io.github.larsonix.trailoforbis.ailments.AilmentState;
import io.github.larsonix.trailoforbis.ailments.AilmentTracker;
import io.github.larsonix.trailoforbis.ailments.AilmentType;
import io.github.larsonix.trailoforbis.ailments.component.RpgBurnComponent;
import io.github.larsonix.trailoforbis.ailments.component.RpgPoisonComponent;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.combat.resolution.CombatEntityResolver;
import io.github.larsonix.trailoforbis.elemental.ElementType;
import io.github.larsonix.trailoforbis.elemental.ElementalStats;
import io.github.larsonix.trailoforbis.maps.RealmsManager;
import io.github.larsonix.trailoforbis.maps.components.RealmMobComponent;
import io.github.larsonix.trailoforbis.maps.instance.RealmInstance;
import io.github.larsonix.trailoforbis.maps.modifiers.RealmModifierType;
import com.hypixel.hytale.server.core.universe.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Applies ailments from elemental damage during combat.
 *
 * <p>This class extracts ailment application logic from RPGDamageSystem,
 * handling:
 * <ul>
 *   <li>Rolling for ailment application based on status effect chance</li>
 *   <li>Creating ailment states for each element type</li>
 *   <li>Applying ailments via AilmentTracker</li>
 * </ul>
 */
public class CombatAilmentApplicator {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final CombatEntityResolver entityResolver;
    private final AilmentTracker ailmentTracker;
    private final AilmentCalculator ailmentCalculator;
    private final AilmentImmunityTracker immunityTracker;
    private final AilmentEffectManager ailmentEffectManager;

    /**
     * Summary of all ailment application attempts during a single hit.
     *
     * @param attempts Individual attempt results per element
     */
    public record AilmentSummary(@Nonnull List<AilmentAttempt> attempts) {
        /** Empty summary (no attempts made). */
        public static final AilmentSummary EMPTY = new AilmentSummary(Collections.emptyList());
    }

    /**
     * Result of a single ailment application attempt for one element.
     *
     * @param element           The element that triggered the ailment check
     * @param elementalDamage   The elemental damage dealt
     * @param applicationChance Final application chance after all bonuses
     * @param roll              The random roll value (0-100)
     * @param applied           Whether the ailment was successfully applied
     * @param ailmentName       Display name of the ailment (e.g. "Burning"), null if no ailment for element
     * @param magnitude         Ailment magnitude (DPS, slow%, +dmg%)
     * @param durationSeconds   Ailment duration in seconds
     */
    public record AilmentAttempt(
        @Nonnull ElementType element,
        float elementalDamage,
        float applicationChance,
        float roll,
        boolean applied,
        @Nullable String ailmentName,
        float magnitude,
        float durationSeconds
    ) {}

    /**
     * Creates a new CombatAilmentApplicator.
     *
     * @param entityResolver The entity resolver for UUID lookups
     * @param ailmentTracker The ailment tracker (may be null)
     * @param ailmentCalculator The ailment calculator (may be null)
     */
    public CombatAilmentApplicator(
        @Nonnull CombatEntityResolver entityResolver,
        @Nullable AilmentTracker ailmentTracker,
        @Nullable AilmentCalculator ailmentCalculator
    ) {
        this(entityResolver, ailmentTracker, ailmentCalculator, null, null);
    }

    /**
     * Creates a new CombatAilmentApplicator with immunity tracking.
     *
     * @param entityResolver    The entity resolver for UUID lookups
     * @param ailmentTracker    The ailment tracker (may be null)
     * @param ailmentCalculator The ailment calculator (may be null)
     * @param immunityTracker   The ailment immunity tracker (may be null, enables IMMUNITY_ON_AILMENT)
     */
    public CombatAilmentApplicator(
        @Nonnull CombatEntityResolver entityResolver,
        @Nullable AilmentTracker ailmentTracker,
        @Nullable AilmentCalculator ailmentCalculator,
        @Nullable AilmentImmunityTracker immunityTracker
    ) {
        this(entityResolver, ailmentTracker, ailmentCalculator, immunityTracker, null);
    }

    /**
     * Creates a new CombatAilmentApplicator with immunity tracking and visual effects.
     *
     * @param entityResolver       The entity resolver for UUID lookups
     * @param ailmentTracker       The ailment tracker (may be null)
     * @param ailmentCalculator    The ailment calculator (may be null)
     * @param immunityTracker      The ailment immunity tracker (may be null)
     * @param ailmentEffectManager The ailment effect manager for visual feedback (may be null)
     */
    public CombatAilmentApplicator(
        @Nonnull CombatEntityResolver entityResolver,
        @Nullable AilmentTracker ailmentTracker,
        @Nullable AilmentCalculator ailmentCalculator,
        @Nullable AilmentImmunityTracker immunityTracker,
        @Nullable AilmentEffectManager ailmentEffectManager
    ) {
        this.entityResolver = entityResolver;
        this.ailmentTracker = ailmentTracker;
        this.ailmentCalculator = ailmentCalculator;
        this.immunityTracker = immunityTracker;
        this.ailmentEffectManager = ailmentEffectManager;
    }

    /**
     * Attempts to apply ailments from elemental damage.
     *
     * <p>For each element type with damage, rolls for ailment application based on
     * statusEffectChance. If successful, creates and applies the appropriate ailment.
     *
     * <p>If the defender has the IMMUNITY_ON_AILMENT stat and an {@link AilmentImmunityTracker}
     * is available, ailments that the defender is currently immune to will be blocked,
     * and successfully applied ailments will grant a temporary immunity window.
     *
     * @param index The entity index in the archetype chunk
     * @param archetypeChunk The archetype chunk containing the defender
     * @param store The entity store
     * @param damage The damage event
     * @param attackerElemental Attacker's elemental stats (damage sources)
     * @param attackerStats Attacker's computed stats (for statusEffectChance)
     * @param defenderMaxHealth Defender's maximum health
     * @return Summary of all ailment application attempts
     */
    @Nonnull
    public AilmentSummary tryApplyAilments(
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull Damage damage,
        @Nullable ElementalStats attackerElemental,
        @Nullable ComputedStats attackerStats,
        float defenderMaxHealth
    ) {
        return tryApplyAilments(index, archetypeChunk, store, damage,
            attackerElemental, attackerStats, defenderMaxHealth, null, null);
    }

    /**
     * Attempts to apply ailments from elemental damage with optional immunity check.
     *
     * @param index The entity index in the archetype chunk
     * @param archetypeChunk The archetype chunk containing the defender
     * @param store The entity store
     * @param damage The damage event
     * @param attackerElemental Attacker's elemental stats (damage sources)
     * @param attackerStats Attacker's computed stats (for statusEffectChance)
     * @param defenderMaxHealth Defender's maximum health
     * @param defenderStats Defender's computed stats (for IMMUNITY_ON_AILMENT, may be null)
     * @return Summary of all ailment application attempts
     */
    @Nonnull
    public AilmentSummary tryApplyAilments(
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull Damage damage,
        @Nullable ElementalStats attackerElemental,
        @Nullable ComputedStats attackerStats,
        float defenderMaxHealth,
        @Nullable ComputedStats defenderStats,
        @Nullable CommandBuffer<EntityStore> commandBuffer
    ) {
        return tryApplyAilments(index, archetypeChunk, store, damage,
            attackerElemental, attackerStats, defenderMaxHealth, defenderStats, commandBuffer, null);
    }

    /**
     * Attempts to apply ailments from elemental damage, using actual hit damage values.
     *
     * @param hitElementalDamage Per-element damage from the actual hit (pre-defense).
     *        When provided, ailment magnitude scales from the real hit damage instead of
     *        the attacker's flat elemental stat. This is the PoE model: a 50-damage fire
     *        hit produces a meaningful burn, not a 5-flat-fire-stat burn.
     */
    @Nonnull
    public AilmentSummary tryApplyAilments(
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull Damage damage,
        @Nullable ElementalStats attackerElemental,
        @Nullable ComputedStats attackerStats,
        float defenderMaxHealth,
        @Nullable ComputedStats defenderStats,
        @Nullable CommandBuffer<EntityStore> commandBuffer,
        @Nullable EnumMap<ElementType, Float> hitElementalDamage
    ) {
        if (attackerElemental == null) {
            return AilmentSummary.EMPTY;
        }

        if (ailmentTracker == null || ailmentCalculator == null) {
            return AilmentSummary.EMPTY;
        }

        // Check realm AILMENT_IMMUNE_MONSTERS — mobs in this realm cannot be ailmented
        Ref<EntityStore> ailmentTarget = archetypeChunk.getReferenceTo(index);
        if (ailmentTarget != null && isDefenderAilmentImmune(store, ailmentTarget)) {
            return AilmentSummary.EMPTY;
        }

        // Get defender UUID (players only — NPC ailment tracking not implemented)
        UUID defenderUuid = entityResolver.getDefenderUuid(index, archetypeChunk, store);
        if (defenderUuid == null) {
            return AilmentSummary.EMPTY;
        }

        // Get attacker UUID for source tracking
        UUID attackerUuid = entityResolver.getAttackerUuid(store, damage);
        if (attackerUuid == null) {
            attackerUuid = new UUID(0, 0); // Fallback UUID for unknown sources
        }

        // Check if defender has IMMUNITY_ON_AILMENT
        boolean hasImmunityStat = immunityTracker != null && defenderStats != null
                && defenderStats.getImmunityOnAilment() > 0;

        List<AilmentAttempt> attempts = new ArrayList<>();

        // Try to apply ailment for each element
        for (ElementType element : ElementType.values()) {
            // Use actual hit damage when available, fall back to flat stat
            float elementalDamage = hitElementalDamage != null
                ? hitElementalDamage.getOrDefault(element, 0f)
                : (float) attackerElemental.getFlatDamage(element);
            if (elementalDamage <= 0) {
                continue;
            }

            // IMMUNITY_ON_AILMENT: skip if defender is currently immune to this element
            if (hasImmunityStat && immunityTracker.isImmune(defenderUuid, element)) {
                AilmentType ailmentType = AilmentType.forElement(element);
                LOGGER.at(Level.FINE).log("Ailment blocked by immunity: %s on %s",
                    element.getDisplayName(), defenderUuid.toString().substring(0, 8));
                attempts.add(new AilmentAttempt(
                    element, elementalDamage, 0f, 0f, false,
                    ailmentType != null ? ailmentType.getDisplayName() : null, 0f, 0f));
                continue;
            }

            // Use default stats if attacker stats not available
            ComputedStats effectiveStats = attackerStats;
            if (effectiveStats == null) {
                effectiveStats = createMinimalStatsForAilment();
            }

            // Try to apply the ailment
            AilmentCalculator.AilmentApplicationResult result = ailmentCalculator.tryApplyAilment(
                element,
                elementalDamage,
                effectiveStats,
                defenderMaxHealth,
                attackerUuid
            );

            AilmentType ailmentType = AilmentType.forElement(element);

            if (result.applied() && result.ailmentState() != null) {
                boolean applied = ailmentTracker.applyAilment(defenderUuid, result.ailmentState());

                if (applied) {
                    AilmentState appliedState = result.ailmentState();
                    AilmentType type = appliedState.type();

                    // Add/update ECS component for DOT-type ailments (drives tick systems)
                    if (type == AilmentType.BURN || type == AilmentType.POISON) {
                        Ref<EntityStore> defenderRef = archetypeChunk.getReferenceTo(index);
                        if (defenderRef != null && defenderRef.isValid()) {
                            addDotComponent(defenderRef, store, appliedState);
                        }
                    }
                    LOGGER.at(Level.FINE).log("Applied %s to %s (%.1f %s dmg -> %.1f magnitude, %.1fs)",
                        type.getDisplayName(),
                        defenderUuid.toString().substring(0, 8),
                        elementalDamage,
                        element.getDisplayName(),
                        result.ailmentState().magnitude(),
                        result.ailmentState().remainingDuration());

                    // Trigger guide milestone for first ailment (only fires for players, ignored for mobs)
                    TrailOfOrbis rpgGuide = TrailOfOrbis.getInstanceOrNull();
                    if (rpgGuide != null && rpgGuide.getGuideManager() != null) {
                        io.github.larsonix.trailoforbis.guide.GuideMilestone ailmentMilestone = switch (type) {
                            case BURN -> io.github.larsonix.trailoforbis.guide.GuideMilestone.FIRST_AILMENT_BURN;
                            case FREEZE -> io.github.larsonix.trailoforbis.guide.GuideMilestone.FIRST_AILMENT_FREEZE;
                            case SHOCK -> io.github.larsonix.trailoforbis.guide.GuideMilestone.FIRST_AILMENT_SHOCK;
                            case POISON -> io.github.larsonix.trailoforbis.guide.GuideMilestone.FIRST_AILMENT_POISON;
                        };
                        rpgGuide.getGuideManager().tryShow(defenderUuid, ailmentMilestone);
                    }

                    // Apply visual effects via native EntityEffect system
                    if (ailmentEffectManager != null && ailmentEffectManager.isInitialized()) {
                        Ref<EntityStore> defenderRef = archetypeChunk.getReferenceTo(index);
                        if (defenderRef != null && defenderRef.isValid()) {
                            ailmentEffectManager.applyAilmentVisual(
                                    defenderRef, defenderUuid, result.ailmentState(), store);
                        }
                    }

                    // IMMUNITY_ON_AILMENT: grant temporary immunity for this element
                    if (hasImmunityStat) {
                        immunityTracker.grantImmunity(defenderUuid, element);
                        LOGGER.at(Level.FINE).log("Granted %s ailment immunity to %s",
                            element.getDisplayName(), defenderUuid.toString().substring(0, 8));
                    }
                }

                attempts.add(new AilmentAttempt(
                    element, elementalDamage, result.applicationChance(), result.roll(),
                    true, ailmentType != null ? ailmentType.getDisplayName() : null,
                    result.ailmentState().magnitude(), result.ailmentState().remainingDuration()));
            } else {
                // Attempt failed or no ailment type for this element
                attempts.add(new AilmentAttempt(
                    element, elementalDamage, result.applicationChance(), result.roll(),
                    false, ailmentType != null ? ailmentType.getDisplayName() : null,
                    0f, 0f));
            }
        }

        return new AilmentSummary(attempts);
    }

    /**
     * Creates minimal computed stats for ailment calculation when attacker stats are unavailable.
     * Used for mobs without player-style stats.
     *
     * @return Minimal stats with 0 for status effect bonuses
     */
    @Nonnull
    public ComputedStats createMinimalStatsForAilment() {
        // Return stats with zeros - ailment will use base chance only
        // ComputedStats default constructor initializes all values to 0
        return new ComputedStats();
    }

    /**
     * @return true if ailment tracker and calculator are initialized
     */
    public boolean isAvailable() {
        return ailmentTracker != null && ailmentCalculator != null;
    }

    /**
     * Checks if the defender mob is in a realm with AILMENT_IMMUNE_MONSTERS.
     */
    private boolean isDefenderAilmentImmune(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> defenderRef) {
        RealmMobComponent realmMob = store.getComponent(defenderRef, RealmMobComponent.getComponentType());
        if (realmMob == null || realmMob.getRealmId() == null) {
            return false;
        }
        TrailOfOrbis rpg = TrailOfOrbis.getInstanceOrNull();
        if (rpg == null) {
            return false;
        }
        RealmsManager rm = rpg.getRealmsManager();
        if (rm == null) {
            return false;
        }
        Optional<RealmInstance> realmOpt = rm.getRealm(realmMob.getRealmId());
        return realmOpt.isPresent()
            && realmOpt.get().getMapData().hasModifier(RealmModifierType.AILMENT_IMMUNE_MONSTERS);
    }

    /**
     * Adds or updates the ECS DOT component on the defender entity.
     *
     * <p>For Burn: creates or refreshes {@link RpgBurnComponent}.
     * For Poison: creates or adds stack to {@link RpgPoisonComponent}.
     *
     * <p><b>Deferral strategy:</b> New component additions use {@code world.execute()} instead of
     * {@code commandBuffer.addComponent()} because the command buffer's queued lambdas can be
     * consumed mid-tick by Hytale's HitAnimation handler (via {@code forEachChunk → consume}),
     * which crashes with {@code IllegalStateException: Store is currently processing} when
     * damage originates from an ECS tick (e.g., Hexcode spell constructs). In-place mutations
     * on existing components are safe (no archetype change).
     */
    private void addDotComponent(
            @Nonnull Ref<EntityStore> defenderRef,
            @Nonnull Store<EntityStore> store,
            @Nonnull AilmentState ailmentState) {

        if (ailmentState.type() == AilmentType.BURN) {
            if (RpgBurnComponent.TYPE == null) return;

            RpgBurnComponent existing = store.getComponent(defenderRef, RpgBurnComponent.TYPE);
            if (existing != null) {
                // Refresh existing burn (non-stacking: takes stronger DPS)
                // In-place mutation — safe, no archetype change
                existing.refresh(ailmentState.magnitude(), ailmentState.remainingDuration(), ailmentState.sourceUuid());
            } else {
                // New burn — defer to world.execute to avoid Store processing lock
                RpgBurnComponent burn = new RpgBurnComponent(
                    ailmentState.magnitude(), ailmentState.remainingDuration(), ailmentState.sourceUuid());
                World world = store.getExternalData().getWorld();
                world.execute(() -> {
                    if (!defenderRef.isValid()) return;
                    // Re-check: another hit in the same tick may have added the component
                    RpgBurnComponent raceBurn = store.getComponent(defenderRef, RpgBurnComponent.TYPE);
                    if (raceBurn != null) {
                        raceBurn.refresh(burn.getDps(), burn.getRemainingDuration(), burn.getSourceUuid());
                    } else {
                        store.addComponent(defenderRef, RpgBurnComponent.TYPE, burn);
                    }
                });
            }
        } else if (ailmentState.type() == AilmentType.POISON) {
            if (RpgPoisonComponent.TYPE == null) return;

            RpgPoisonComponent existing = store.getComponent(defenderRef, RpgPoisonComponent.TYPE);
            if (existing != null) {
                // Add stack to existing poison
                // In-place mutation — safe, no archetype change
                existing.addStack(
                    ailmentState.magnitude(), ailmentState.remainingDuration(),
                    ailmentState.sourceUuid(), 10);
            } else {
                // New poison — defer to world.execute to avoid Store processing lock
                RpgPoisonComponent poison = new RpgPoisonComponent();
                poison.addStack(
                    ailmentState.magnitude(), ailmentState.remainingDuration(),
                    ailmentState.sourceUuid(), 10);
                World world = store.getExternalData().getWorld();
                world.execute(() -> {
                    if (!defenderRef.isValid()) return;
                    // Re-check: another hit in the same tick may have added the component
                    RpgPoisonComponent racePoison = store.getComponent(defenderRef, RpgPoisonComponent.TYPE);
                    if (racePoison != null) {
                        racePoison.addStack(
                            poison.getTotalDps(), ailmentState.remainingDuration(),
                            poison.getPrimarySourceUuid(), 10);
                    } else {
                        store.addComponent(defenderRef, RpgPoisonComponent.TYPE, poison);
                    }
                });
            }
        }
    }
}
