package io.github.larsonix.trailoforbis.gear.stats;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.util.PlayerWorldCache;
import io.github.larsonix.trailoforbis.gear.stats.GearStatCalculator.GearBonuses;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Provides gear bonus calculation and application for the stat pipeline.
 *
 * <p>This class bridges the gear system with the attribute system by:
 * <ul>
 *   <li>Looking up player inventory from UUID</li>
 *   <li>Calculating gear bonuses via {@link GearStatCalculator}</li>
 *   <li>Applying bonuses to {@link ComputedStats} via {@link GearStatApplier}</li>
 * </ul>
 *
 * <p>Thread Safety: This class is thread-safe as long as the underlying
 * calculator and applier are thread-safe (which they are by design).
 */
public final class GearBonusProvider {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final GearStatCalculator calculator;
    private final GearStatApplier applier;

    /**
     * Creates a GearBonusProvider.
     */
    public GearBonusProvider(
            @Nonnull GearStatCalculator calculator,
            @Nonnull GearStatApplier applier
    ) {
        this.calculator = Objects.requireNonNull(calculator, "calculator cannot be null");
        this.applier = Objects.requireNonNull(applier, "applier cannot be null");
    }

    /**
     * Applies gear bonuses to the given ComputedStats.
     *
     * <p>This is the main integration point called from AttributeManager.
     * It looks up the player's inventory, calculates gear bonuses, and
     * applies them to the stats object.
     *
     * @param stats mutated in place
     * @return true if bonuses were applied, false if player not found or no bonuses
     */
    public boolean applyGearBonuses(@Nonnull UUID playerId, @Nonnull ComputedStats stats) {
        Objects.requireNonNull(playerId, "playerId cannot be null");
        Objects.requireNonNull(stats, "stats cannot be null");

        // Get player inventory
        Inventory inventory = getPlayerInventory(playerId);
        if (inventory == null) {
            LOGGER.atFine().log("Cannot apply gear bonuses - player inventory not found: %s", playerId);
            return false;
        }

        // Calculate bonuses
        GearBonuses bonuses = calculator.calculateBonuses(playerId, inventory);

        // ALWAYS apply bonuses - even when modifier maps are empty, weapon data must be applied.
        // This ensures:
        // - isHoldingRpgGear: routes to RPG damage path (even with 0 weapon damage)
        // - weaponBaseDamage: 0 for unequippable weapons (requirements not met)
        // - weaponItemId: for attack profile lookup
        //
        // When requirements aren't met: baseDamage=0 but character stats still add damage
        // via STEP 1 of RPGDamageCalculator (flatPhys + flatMelee added to base).
        applier.apply(stats, bonuses);

        boolean hasModifiers = !bonuses.isEmpty();
        boolean hasWeapon = bonuses.hasWeaponDamage() || bonuses.isHoldingRpgGear();

        if (hasModifiers || hasWeapon) {
            LOGGER.atFine().log("Applied gear bonuses for player %s: %d flat, %d percent, weapon: %s (rpg=%s, dmg=%.1f)",
                    playerId, bonuses.flatBonuses().size(), bonuses.percentBonuses().size(),
                    bonuses.weaponItemId(), bonuses.isHoldingRpgGear(), bonuses.weaponBaseDamage());
        }

        return hasModifiers || hasWeapon;
    }

    /**
     * Calculates gear bonuses for a player without applying them.
     *
     * <p>Useful for UI display or debugging.
     *
     * @return empty bonuses if player not found
     */
    @Nonnull
    public GearBonuses calculateBonuses(@Nonnull UUID playerId) {
        Objects.requireNonNull(playerId, "playerId cannot be null");

        Inventory inventory = getPlayerInventory(playerId);
        if (inventory == null) {
            return GearBonuses.EMPTY;
        }

        return calculator.calculateBonuses(playerId, inventory);
    }

    /**
     * Gets the bonus summary string for a player.
     *
     * <p>Useful for debugging and logging.
     *
     * @return summary string of gear bonuses
     */
    @Nonnull
    public String getBonusSummary(@Nonnull UUID playerId) {
        GearBonuses bonuses = calculateBonuses(playerId);
        return applier.createSummary(bonuses);
    }

    /**
     * Gets a player's inventory by UUID.
     *
     * <p>Iterates through all worlds to find the player.
     *
     * @return null if not found
     */
    @Nullable
    private Inventory getPlayerInventory(@Nonnull UUID playerId) {
        try {
            PlayerRef ref = PlayerWorldCache.findPlayerRef(playerId);
            if (ref == null) {
                LOGGER.atFine().log("Player %s not found in any world", playerId);
                return null;
            }
            Ref<EntityStore> entityRef = ref.getReference();
            if (entityRef == null || !entityRef.isValid()) {
                LOGGER.atWarning().log("Player %s found but entity ref invalid", playerId);
                return null;
            }
            Player player = entityRef.getStore().getComponent(
                    entityRef, Player.getComponentType());
            if (player == null) {
                LOGGER.atWarning().log("Player %s found but Player component missing", playerId);
                return null;
            }
            return player.getInventory();
        } catch (IllegalStateException e) {
            // Cross-thread Store access (e.g. admin command triggering stat recalc from
            // wrong world thread). Gear bonuses will be applied on next proper recalc.
            LOGGER.atFine().log(
                    "Skipping gear bonuses for %s - cross-thread access (will apply on next recalc)",
                    playerId);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log(
                    "Error getting inventory for player %s", playerId);
        }
        return null;
    }

    /**
     * Gets the underlying stat calculator.
     */
    @Nonnull
    public GearStatCalculator getCalculator() {
        return calculator;
    }

    /**
     * Gets the underlying stat applier.
     */
    @Nonnull
    public GearStatApplier getApplier() {
        return applier;
    }
}
