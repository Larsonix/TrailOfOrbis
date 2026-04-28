package io.github.larsonix.trailoforbis.combat.avoidance;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.combat.DamageBreakdown;
import io.github.larsonix.trailoforbis.combat.blocking.BlockResult;
import io.github.larsonix.trailoforbis.combat.blocking.BlockingProcessor;
import io.github.larsonix.trailoforbis.config.ConfigManager;
import io.github.larsonix.trailoforbis.config.RPGConfig;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Processes damage avoidance mechanics (dodge, evasion, block).
 *
 * <p>This class extracts avoidance logic from RPGDamageSystem, providing:
 * <ul>
 *   <li>Flat dodge chance checks</li>
 *   <li>PoE-inspired evasion formula</li>
 *   <li>Block chance checks</li>
 *   <li>Damage estimation for block heal</li>
 * </ul>
 */
public class AvoidanceProcessor {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final ConfigManager configManager;
    private final BlockingProcessor blockingProcessor;

    /**
     * Detailed avoidance stats captured during avoidance evaluation.
     *
     * <p>Always populated (even on hits) so the combat log can show
     * what avoidance the defender had and why the attack connected.
     *
     * @param dodgeChance       Flat dodge % from stats
     * @param evasion           Defender evasion rating
     * @param accuracy          Attacker accuracy rating
     * @param hitChance         Calculated hit chance (PoE formula)
     * @param passiveBlockChance Passive block % from stats
     * @param wasActiveBlock    Whether defender was holding shield
     * @param activeBlockChance Active block % (0 if not blocking)
     * @param blockDamageReduction Block damage reduction % (0.0-1.0)
     * @param blockStaminaCost  Stamina consumed by block
     * @param parryChance       Parry % from defender stats
     */
    public record AvoidanceDetail(
        float dodgeChance,
        float evasion,
        float accuracy,
        float hitChance,
        float passiveBlockChance,
        boolean wasActiveBlock,
        float activeBlockChance,
        float blockDamageReduction,
        float blockStaminaCost,
        float parryChance
    ) {}

    /**
     * Combined result of avoidance checks: the avoidance outcome plus
     * full stats detail (always present, even on hits).
     *
     * @param avoidance The avoidance result (empty if attack connected)
     * @param stats     Detailed avoidance stats (always populated)
     */
    public record AvoidanceCheckResult(
        @Nonnull Optional<AvoidanceResult> avoidance,
        @Nonnull AvoidanceDetail stats
    ) {}

    /**
     * Result of an avoidance check.
     *
     * @param avoided Whether the attack was avoided
     * @param reason The reason for avoidance (if avoided)
     * @param estimatedDamage The estimated damage that would have been dealt (for block heal)
     */
    public record AvoidanceResult(
        boolean avoided,
        @Nonnull DamageBreakdown.AvoidanceReason reason,
        float estimatedDamage
    ) {
        public static AvoidanceResult notAvoided() {
            return new AvoidanceResult(false, DamageBreakdown.AvoidanceReason.MISSED, 0f);
        }

        public static AvoidanceResult dodged() {
            return new AvoidanceResult(true, DamageBreakdown.AvoidanceReason.DODGED, 0f);
        }

        public static AvoidanceResult blocked(float estimatedDamage) {
            return new AvoidanceResult(true, DamageBreakdown.AvoidanceReason.BLOCKED, estimatedDamage);
        }
    }

    /**
     * Creates a new AvoidanceProcessor.
     *
     * @param configManager The config manager
     */
    public AvoidanceProcessor(@Nonnull ConfigManager configManager) {
        this.configManager = configManager;
        this.blockingProcessor = new BlockingProcessor();
    }

    private RPGConfig.CombatConfig.EvasionConfig getEvasionConfig() {
        return configManager.getRPGConfig().getCombat().getEvasion();
    }

    /**
     * Checks all avoidance mechanics in order: dodge, evasion, active block, passive block.
     *
     * @param damage The damage event
     * @param defenderStats The defender's computed stats (may be null)
     * @param attackerStats The attacker's computed stats (may be null)
     * @param conditionalMultiplier The pre-calculated conditional multiplier
     * @param baseDamage The base damage amount
     * @return An AvoidanceResult if damage was avoided, empty otherwise
     */
    @Nonnull
    public Optional<AvoidanceResult> checkAvoidance(
        @Nonnull Damage damage,
        @Nullable ComputedStats defenderStats,
        @Nullable ComputedStats attackerStats,
        float conditionalMultiplier,
        float baseDamage
    ) {
        // Delegate to new signature without store/ref (backward compatible)
        return checkAvoidance(null, null, damage, defenderStats, attackerStats, conditionalMultiplier, baseDamage);
    }

    /**
     * Checks all avoidance mechanics in order: dodge, evasion, active block, passive block.
     *
     * <p>This overload accepts store and defenderRef for active blocking detection,
     * which requires access to the defender's DamageDataComponent.
     *
     * @param store The entity store for component access (nullable for backward compat)
     * @param defenderRef The defender's entity reference (nullable for backward compat)
     * @param damage The damage event
     * @param defenderStats The defender's computed stats (may be null)
     * @param attackerStats The attacker's computed stats (may be null)
     * @param conditionalMultiplier The pre-calculated conditional multiplier
     * @param baseDamage The base damage amount
     * @return An AvoidanceResult if damage was avoided, empty otherwise
     */
    @Nonnull
    public Optional<AvoidanceResult> checkAvoidance(
        @Nullable Store<EntityStore> store,
        @Nullable Ref<EntityStore> defenderRef,
        @Nonnull Damage damage,
        @Nullable ComputedStats defenderStats,
        @Nullable ComputedStats attackerStats,
        float conditionalMultiplier,
        float baseDamage
    ) {
        return checkAvoidanceDetailed(store, defenderRef, damage, defenderStats, attackerStats,
            conditionalMultiplier, baseDamage).avoidance();
    }

    /**
     * Checks all avoidance mechanics and returns detailed stats alongside the result.
     *
     * <p>Unlike {@link #checkAvoidance}, this method always returns an {@link AvoidanceDetail}
     * with the defender's avoidance stats, even when the attack connects. This allows the
     * combat log to display what avoidance the defender had and why the attack connected.
     *
     * @param store The entity store for component access (nullable for backward compat)
     * @param defenderRef The defender's entity reference (nullable for backward compat)
     * @param damage The damage event
     * @param defenderStats The defender's computed stats (may be null)
     * @param attackerStats The attacker's computed stats (may be null)
     * @param conditionalMultiplier The pre-calculated conditional multiplier
     * @param baseDamage The base damage amount
     * @return An AvoidanceCheckResult with both the outcome and full avoidance stats
     */
    @Nonnull
    public AvoidanceCheckResult checkAvoidanceDetailed(
        @Nullable Store<EntityStore> store,
        @Nullable Ref<EntityStore> defenderRef,
        @Nonnull Damage damage,
        @Nullable ComputedStats defenderStats,
        @Nullable ComputedStats attackerStats,
        float conditionalMultiplier,
        float baseDamage
    ) {
        AvoidanceDetail emptyStats = new AvoidanceDetail(0, 0, 0, 1f, 0, false, 0, 0, 0, 0);

        // Only check avoidance for entity-source damage
        if (!(damage.getSource() instanceof Damage.EntitySource)) {
            return new AvoidanceCheckResult(Optional.empty(), emptyStats);
        }

        if (defenderStats == null) {
            return new AvoidanceCheckResult(Optional.empty(), emptyStats);
        }

        // Gather all stats for the detail record
        float dodgeChance = defenderStats.getDodgeChance();
        float evasion = defenderStats.getEvasion();
        float accuracy = (attackerStats != null) ? attackerStats.getAccuracy() : 100f;
        float hitChance = evasion > 0 ? calculateHitChance(accuracy, evasion) : 1.0f;
        float passiveBlockChance = defenderStats.getPassiveBlockChance();
        float parryChance = defenderStats.getParryChance();

        // Active block stats (populated below if applicable)
        boolean wasActiveBlock = false;
        float activeBlockChance = 0f;
        float blockDamageReduction = 0f;
        float blockStaminaCost = 0f;

        // 1. Flat dodge chance
        if (checkDodge(defenderStats)) {
            LOGGER.at(Level.FINE).log("Dodge: %.1f%% chance — attack fully avoided", dodgeChance);
            AvoidanceDetail stats = new AvoidanceDetail(dodgeChance, evasion, accuracy, hitChance,
                passiveBlockChance, false, 0, 0, 0, parryChance);
            return new AvoidanceCheckResult(Optional.of(AvoidanceResult.dodged()), stats);
        }

        // 2. Evasion check (PoE-inspired formula)
        if (evasion > 0) {
            if (checkEvasion(evasion, accuracy)) {
                LOGGER.at(Level.FINE).log("Dodged: %.1f evasion vs %.1f accuracy",
                    evasion, accuracy);
                AvoidanceDetail stats = new AvoidanceDetail(dodgeChance, evasion, accuracy, hitChance,
                    passiveBlockChance, false, 0, 0, 0, parryChance);
                return new AvoidanceCheckResult(Optional.of(AvoidanceResult.dodged()), stats);
            }
        }

        // 3a. Active block check (player holding shield)
        if (store != null && defenderRef != null) {
            Optional<BlockResult> activeBlock = blockingProcessor.checkActiveBlock(
                store, defenderRef, defenderStats, baseDamage);
            if (activeBlock.isPresent()) {
                BlockResult result = activeBlock.get();
                wasActiveBlock = true;
                activeBlockChance = defenderStats.getBlockChance();
                if (result.blocked()) {
                    blockDamageReduction = result.damageReduction();
                    blockStaminaCost = result.staminaCost();
                    float estimatedDamage = estimateBlockedDamage(baseDamage, attackerStats, conditionalMultiplier);
                    float reducedDamage = estimatedDamage * (1.0f - result.damageReduction());
                    LOGGER.at(Level.FINE).log("Active blocked: %.1f%% damage reduced, stamina cost: %.1f",
                        result.damageReduction() * 100, result.staminaCost());
                    AvoidanceDetail stats = new AvoidanceDetail(dodgeChance, evasion, accuracy, hitChance,
                        passiveBlockChance, true, activeBlockChance, blockDamageReduction, blockStaminaCost, parryChance);
                    return new AvoidanceCheckResult(Optional.of(AvoidanceResult.blocked(reducedDamage)), stats);
                }
                // Block roll failed - continue to passive block check
            }
        }

        // 3b. Passive block check (random proc, no shield needed)
        if (checkPassiveBlock(defenderStats)) {
            float estimatedDamage = estimateBlockedDamage(baseDamage, attackerStats, conditionalMultiplier);
            LOGGER.at(Level.FINE).log("Passive blocked: %.1f%% chance", passiveBlockChance);
            AvoidanceDetail stats = new AvoidanceDetail(dodgeChance, evasion, accuracy, hitChance,
                passiveBlockChance, wasActiveBlock, activeBlockChance, blockDamageReduction, blockStaminaCost, parryChance);
            return new AvoidanceCheckResult(Optional.of(AvoidanceResult.blocked(estimatedDamage)), stats);
        }

        // Hit connected — still return full stats
        AvoidanceDetail stats = new AvoidanceDetail(dodgeChance, evasion, accuracy, hitChance,
            passiveBlockChance, wasActiveBlock, activeBlockChance, blockDamageReduction, blockStaminaCost, parryChance);
        return new AvoidanceCheckResult(Optional.empty(), stats);
    }

    /**
     * Checks if the attack is dodged via flat dodge chance.
     *
     * @param defenderStats The defender's stats
     * @return true if the attack is dodged
     */
    public boolean checkDodge(@Nullable ComputedStats defenderStats) {
        if (defenderStats == null) {
            return false;
        }

        float dodgeChance = defenderStats.getDodgeChance();
        if (dodgeChance > 0 && ThreadLocalRandom.current().nextFloat() * 100f < dodgeChance) {
            return true;
        }
        return false;
    }

    /**
     * Checks if the attack is evaded via evasion vs accuracy calculation.
     *
     * @param evasion The defender's evasion rating
     * @param accuracy The attacker's accuracy rating
     * @return true if the attack is evaded
     */
    public boolean checkEvasion(float evasion, float accuracy) {
        if (evasion <= 0) {
            return false;
        }

        float chanceToHit = calculateHitChance(accuracy, evasion);
        return ThreadLocalRandom.current().nextFloat() > chanceToHit;
    }

    /**
     * Checks if the attack is passively blocked via passive block chance.
     * This is a random proc, distinct from active shield blocking.
     *
     * @param defenderStats The defender's stats
     * @return true if the attack is passively blocked
     */
    public boolean checkPassiveBlock(@Nullable ComputedStats defenderStats) {
        if (defenderStats == null) {
            return false;
        }

        float passiveBlockChance = defenderStats.getPassiveBlockChance();
        if (passiveBlockChance > 0 && ThreadLocalRandom.current().nextFloat() * 100f < passiveBlockChance) {
            return true;
        }
        return false;
    }

    /**
     * Calculates hit chance using Path of Exile-inspired formula.
     *
     * <p><b>Formula:</b>
     * <pre>
     * ChanceToHit = hitChanceConstant × Accuracy / (Accuracy + (Evasion × evasionScalingFactor)^evasionExponent)
     * </pre>
     *
     * <p>This formula has the following properties:
     * <ul>
     *   <li>Minimum hit chance: configurable (default 5%)</li>
     *   <li>Maximum hit chance: configurable (default 100%)</li>
     *   <li>Evasion has diminishing returns at high values via exponent</li>
     *   <li>Accuracy and evasion scale against each other naturally</li>
     * </ul>
     *
     * @param accuracy The attacker's accuracy rating
     * @param evasion The defender's evasion rating
     * @return The chance to hit as a decimal (minHitChance to maxHitChance)
     */
    public float calculateHitChance(float accuracy, float evasion) {
        return calculateHitChance(getEvasionConfig(), accuracy, evasion);
    }

    /**
     * Calculates hit chance using the PoE-inspired formula with explicit config.
     *
     * <p>Static overload for use by display contexts (e.g., stats page) that have
     * an {@link RPGConfig.CombatConfig.EvasionConfig} but no AvoidanceProcessor instance.
     *
     * @param evasionConfig The evasion configuration
     * @param accuracy The attacker's accuracy rating
     * @param evasion The defender's evasion rating
     * @return The chance to hit as a decimal (minHitChance to maxHitChance)
     */
    public static float calculateHitChance(
            @Nonnull RPGConfig.CombatConfig.EvasionConfig evasionConfig,
            float accuracy,
            float evasion) {
        float minHitChance = evasionConfig.getMinHitChance();
        float maxHitChance = evasionConfig.getMaxHitChance();
        float scalingFactor = evasionConfig.getEvasionScalingFactor();
        float exponent = evasionConfig.getEvasionExponent();
        float hitChanceConstant = evasionConfig.getHitChanceConstant();

        // Validate config - use safe defaults if invalid
        if (scalingFactor <= 0) {
            LOGGER.at(Level.WARNING).log("Invalid evasionScalingFactor: %.2f, using 0.2", scalingFactor);
            scalingFactor = 0.2f;
        }
        if (exponent <= 0 || exponent > 2) {
            LOGGER.at(Level.WARNING).log("Invalid evasionExponent: %.2f, using 0.9", exponent);
            exponent = 0.9f;
        }

        // Handle edge cases
        if (accuracy <= 0) {
            return minHitChance;
        }
        if (evasion <= 0) {
            return maxHitChance;
        }

        // PoE-style formula with configurable parameters
        double scaledEvasion = Math.pow(evasion * scalingFactor, exponent);
        double uncappedHitChance = (hitChanceConstant * accuracy) / (accuracy + scaledEvasion);

        // Clamp between configured min and max
        return (float) Math.min(maxHitChance, Math.max(minHitChance, uncappedHitChance));
    }

    /**
     * Quick damage estimate for block heal calculation.
     *
     * <p>Provides an approximation of what damage would have been dealt:
     * {@code baseDamage * (1 + dmgPercent/100) * conditionalMultiplier}
     *
     * @param baseDamage The base damage amount
     * @param attackerStats The attacker's stats (may be null)
     * @param conditionalMultiplier The pre-calculated conditional multiplier
     * @return Estimated damage for block heal calculation
     */
    public float estimateBlockedDamage(
        float baseDamage,
        @Nullable ComputedStats attackerStats,
        float conditionalMultiplier
    ) {
        float damage = baseDamage;

        if (attackerStats != null) {
            // Add flat damage
            damage += attackerStats.getPhysicalDamage();

            // Apply percent bonus (positive or negative)
            float percentBonus = attackerStats.getPhysicalDamagePercent() + attackerStats.getDamagePercent();
            if (percentBonus != 0) {
                damage *= (1f + percentBonus / 100f);
                // Clamp to non-negative (extreme debuffs could go negative)
                damage = Math.max(0f, damage);
            }
        }

        // Apply conditional multiplier
        return damage * conditionalMultiplier;
    }
}
