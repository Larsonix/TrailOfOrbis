package io.github.larsonix.trailoforbis.combat.ailments;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.ailments.AilmentCalculator;
import io.github.larsonix.trailoforbis.ailments.AilmentEffectManager;
import io.github.larsonix.trailoforbis.ailments.AilmentImmunityTracker;
import io.github.larsonix.trailoforbis.ailments.AilmentTracker;
import io.github.larsonix.trailoforbis.ailments.AilmentType;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.combat.resolution.CombatEntityResolver;
import io.github.larsonix.trailoforbis.elemental.ElementType;
import io.github.larsonix.trailoforbis.elemental.ElementalStats;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
            attackerElemental, attackerStats, defenderMaxHealth, null);
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
        @Nullable ComputedStats defenderStats
    ) {
        if (attackerElemental == null) {
            return AilmentSummary.EMPTY;
        }

        if (ailmentTracker == null || ailmentCalculator == null) {
            return AilmentSummary.EMPTY;
        }

        // Get defender UUID (only players for now)
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
            float elementalDamage = (float) attackerElemental.getFlatDamage(element);
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
                    AilmentType type = result.ailmentState().type();
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
}
