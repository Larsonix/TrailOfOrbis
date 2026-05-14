package io.github.larsonix.trailoforbis.combat.resolution;

import com.hypixel.hytale.logger.HytaleLogger;
import java.util.logging.Level;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCalculatorSystems;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.config.RPGConfig;
import io.github.larsonix.trailoforbis.elemental.ElementType;
import io.github.larsonix.trailoforbis.elemental.ElementalStats;
import io.github.larsonix.trailoforbis.mobs.classification.RPGMobClass;
import io.github.larsonix.trailoforbis.mobs.component.MobScalingComponent;
import io.github.larsonix.trailoforbis.mobs.model.MobStats;
import io.github.larsonix.trailoforbis.mobs.stats.MobStatPoolConfig;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Resolves base damage values for non-RPG weapon attacks (vanilla weapons, unarmed, mobs)
 * and determines dominant spell elements for magic weapon attacks.
 *
 * <p>Extracted from RPGDamageSystem Phase 2 (gatherCombatInputs) to isolate mob-specific
 * damage formulas from the main pipeline orchestrator.
 */
public class MobDamageResolver {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final TrailOfOrbis plugin;

    public MobDamageResolver(@Nonnull TrailOfOrbis plugin) {
        this.plugin = plugin;
    }

    /**
     * Determines the dominant spell element for a magic weapon attack.
     *
     * <p>Selects the element with the highest flat damage value from the attacker's
     * elemental stats. Falls back to WATER if no elemental stats or all values are zero.
     *
     * @param elemental The attacker's elemental stats (may be null)
     * @return The dominant element, or WATER as fallback
     */
    @Nonnull
    public ElementType resolveDominantSpellElement(@Nullable ElementalStats elemental) {
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
    public float getWeaponBaseDamage(
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
     * weight = sqrt(vanillaDamage / 10)
     * effectiveRpg = max(rpgTargetDmg * progressiveScale, 5 * bossMultiplier)
     * finalDamage = effectiveRpg * weight
     * </pre>
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

        // Get progressive scaling factor (0.15 -> 1.0 based on level)
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
        // vanillaDamage 10 -> weight 1.0
        // vanillaDamage 40 -> weight 2.0
        // vanillaDamage 5  -> weight 0.71
        float effectiveVanilla = Math.max(vanillaDamage, 5f);
        double weight = Math.sqrt(effectiveVanilla / 10.0);

        // Effective RPG: scales with level, has a floor to prevent 0 damage
        double minDamage = 5.0 * classMultiplier;
        double effectiveRpg = Math.max(rpgTargetDmg * progressiveScale * classMultiplier, minDamage);

        // Final damage: weighted RPG value
        double finalDamage = effectiveRpg * weight;

        LOGGER.at(Level.FINE).log(
            "[MobDamage] Weighted formula: vanilla=%.1f, rpgTarget=%.1f, scale=%.2f, class=%s(x%.1f), weight=%.2f, final=%.1f",
            vanillaDamage, rpgTargetDmg, progressiveScale, classification, classMultiplier, weight, finalDamage);

        return (float) finalDamage;
    }
}
