package io.github.larsonix.trailoforbis.combat.blocking;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.damage.DamageDataComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.WieldingInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.attributes.ComputedStats;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Optional;
import java.util.logging.Level;

/**
 * Processes active blocking mechanics (shield blocking).
 *
 * <p>Active blocking occurs when a player holds a shield and blocks (right-click hold).
 * This is distinct from passive blocking which is a random proc independent of player action.
 *
 * <p>Integration point: Called from {@code AvoidanceProcessor} BEFORE passive block checks.
 * If active blocking is detected and succeeds, the damage is reduced according to RPG stats.
 *
 * <p>Stat usage:
 * <ul>
 *   <li>{@code BLOCK_CHANCE} - % chance for the active block to succeed (0-100)</li>
 *   <li>{@code BLOCK_DAMAGE_REDUCTION} - % damage reduced when block succeeds</li>
 *   <li>{@code STAMINA_DRAIN_REDUCTION} - % reduction in stamina cost when blocking</li>
 * </ul>
 *
 * <p>Flow:
 * <ol>
 *   <li>Check if defender has {@code DamageDataComponent.getCurrentWielding()} != null</li>
 *   <li>If blocking, extract {@code BlockingStats} from player's {@code ComputedStats}</li>
 *   <li>Roll against {@code blockChance} to determine if block succeeds</li>
 *   <li>If successful, calculate damage reduction and stamina cost</li>
 * </ol>
 */
public class BlockingProcessor {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Default stamina cost multiplier when no StaminaCost config exists on shield. */
    private static final float DEFAULT_STAMINA_COST_MULTIPLIER = 0.1f;

    /**
     * Checks if the defender is actively blocking and processes the block attempt.
     *
     * <p>This method should be called early in the damage pipeline, before passive
     * block checks. If the player is actively blocking with a shield and the block
     * roll succeeds, damage will be reduced according to their stats.
     *
     * @param store The entity store for component access
     * @param defenderRef The defender's entity reference
     * @param defenderStats The defender's computed stats (may be null)
     * @param incomingDamage The raw damage before block reduction (for stamina calculation)
     * @return Optional containing BlockResult if blocking was attempted, empty if not blocking
     */
    @Nonnull
    public Optional<BlockResult> checkActiveBlock(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> defenderRef,
            @Nullable ComputedStats defenderStats,
            float incomingDamage
    ) {
        // 1. Check if defender is actively blocking via DamageDataComponent
        DamageDataComponent damageData = store.getComponent(defenderRef, DamageDataComponent.getComponentType());
        if (damageData == null) {
            return Optional.empty();
        }

        WieldingInteraction wielding = damageData.getCurrentWielding();
        if (wielding == null) {
            // Not holding a shield or not in blocking stance
            return Optional.empty();
        }

        // Defender IS actively blocking
        if (defenderStats == null) {
            // No RPG stats = let vanilla handle the block
            LOGGER.at(Level.FINE).log("Active block detected but no RPG stats - deferring to vanilla");
            return Optional.empty();
        }

        // 2. Get blocking stats from ComputedStats
        BlockingStats blockingStats = BlockingStats.from(defenderStats);

        // 3. Roll BLOCK_CHANCE for perfect block (full damage avoidance).
        // If the roll fails, the player still gets the deterministic base reduction
        // (33% weapon / 66% shield) applied later in the damage pipeline.
        if (!blockingStats.rollBlock()) {
            LOGGER.at(Level.FINE).log("Perfect block roll failed (chance: %.1f%%) — base reduction will apply",
                blockingStats.blockChance());
            return Optional.of(BlockResult.FAILED_ROLL);
        }

        // 4. Perfect block — full avoidance (100% damage reduction)
        float staminaCost = calculateStaminaCost(store, defenderRef, wielding, incomingDamage, blockingStats);

        LOGGER.at(Level.FINE).log("PERFECT BLOCK — full avoidance, stamina cost: %.1f",
            staminaCost);

        return Optional.of(BlockResult.success(1.0f, staminaCost));
    }

    /**
     * Calculates stamina cost for blocking, applying our stat reduction.
     *
     * <p>When the shield has a {@code StaminaCost} config and the defender has an
     * {@code EntityStatMap}, delegates to Hytale's native
     * {@link WieldingInteraction.StaminaCost#computeStaminaAmountToConsume} which
     * handles both {@code MAX_HEALTH_PERCENTAGE} and {@code DAMAGE} cost types.
     *
     * <p>Falls back to a flat 10% of blocked damage when config or stat map is unavailable.
     *
     * @param store The entity store for component access
     * @param defenderRef The defender's entity reference
     * @param wielding The wielding interaction (shield being used)
     * @param damage The raw damage being blocked
     * @param stats The player's blocking stats
     * @return The final stamina cost after reduction
     */
    private float calculateStaminaCost(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> defenderRef,
            @Nonnull WieldingInteraction wielding,
            float damage,
            @Nonnull BlockingStats stats
    ) {
        float baseCost;

        WieldingInteraction.StaminaCost staminaConfig = wielding.getStaminaCost();
        if (staminaConfig != null) {
            // Use Hytale's native formula if we can get the EntityStatMap
            EntityStatMap statMap = store.getComponent(defenderRef, EntityStatMap.getComponentType());
            if (statMap != null) {
                baseCost = staminaConfig.computeStaminaAmountToConsume(damage, statMap);
            } else {
                baseCost = damage * DEFAULT_STAMINA_COST_MULTIPLIER;
            }
        } else {
            baseCost = damage * DEFAULT_STAMINA_COST_MULTIPLIER;
        }

        // Apply our stamina drain reduction stat (capped at 75%)
        float reduction = stats.getEffectiveStaminaReduction();
        return baseCost * (1.0f - reduction);
    }
}
