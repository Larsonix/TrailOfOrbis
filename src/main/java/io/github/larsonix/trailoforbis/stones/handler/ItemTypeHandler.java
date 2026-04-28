package io.github.larsonix.trailoforbis.stones.handler;

import io.github.larsonix.trailoforbis.stones.ItemTargetType;
import io.github.larsonix.trailoforbis.stones.ModifiableItem;
import io.github.larsonix.trailoforbis.stones.StoneActionResult;

import javax.annotation.Nonnull;
import java.util.Random;

/**
 * Strategy interface for type-specific stone operations.
 *
 * <p>Each {@link ModifiableItem} type gets its own handler that encapsulates
 * roller-delegated modifier operations where the roller APIs differ
 * fundamentally between item types.
 *
 * <p>This separates the "what operation" (determined by the stone type in
 * {@link io.github.larsonix.trailoforbis.stones.StoneActionRegistry}) from
 * the "how to execute it on this item type" (determined by the handler).
 *
 * <p>Registered handlers:
 * <ul>
 *   <li>{@link GearStoneHandler} — wraps {@code GearModifierRoller}</li>
 *   <li>{@link RealmMapStoneHandler} — wraps {@code RealmModifierRoller}</li>
 * </ul>
 *
 * @param <T> The concrete ModifiableItem type this handler supports
 * @see GearStoneHandler
 * @see RealmMapStoneHandler
 */
public interface ItemTypeHandler<T extends ModifiableItem> {

    // ═══════════════════════════════════════════════════════════════════
    // MODIFIER ROLLER OPERATIONS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Rerolls all unlocked modifier values (keeps types).
     */
    @Nonnull
    StoneActionResult rerollValues(@Nonnull T item, @Nonnull Random random);

    /**
     * Rerolls one random unlocked modifier's value.
     */
    @Nonnull
    StoneActionResult rerollOneValue(@Nonnull T item, @Nonnull Random random);

    /**
     * Rerolls all unlocked modifier types and values.
     */
    @Nonnull
    StoneActionResult rerollTypes(@Nonnull T item, @Nonnull Random random);

    /**
     * Adds one random modifier to an empty slot.
     */
    @Nonnull
    StoneActionResult addModifier(@Nonnull T item, @Nonnull Random random);

    /**
     * Removes one random unlocked modifier.
     */
    @Nonnull
    StoneActionResult removeModifier(@Nonnull T item, @Nonnull Random random);

    /**
     * Removes all unlocked modifiers.
     */
    @Nonnull
    StoneActionResult clearUnlockedModifiers(@Nonnull T item);

    /**
     * Removes one unlocked modifier and adds a new one (atomic swap).
     */
    @Nonnull
    StoneActionResult transmute(@Nonnull T item, @Nonnull Random random);

    /**
     * Fills all remaining modifier slots with random modifiers.
     */
    @Nonnull
    StoneActionResult fillModifiers(@Nonnull T item, @Nonnull Random random);

    /**
     * Locks a modifier at the given combined index.
     *
     * @param combinedIndex The index in the combined modifiers list
     */
    @Nonnull
    StoneActionResult lockModifier(@Nonnull T item, int combinedIndex);

    /**
     * Unlocks a modifier at the given combined index.
     *
     * @param combinedIndex The index in the combined modifiers list
     */
    @Nonnull
    StoneActionResult unlockModifier(@Nonnull T item, int combinedIndex);

    // ═══════════════════════════════════════════════════════════════════
    // COMPLEX MULTI-OUTCOME OPERATIONS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Applies corruption with weighted random outcomes.
     */
    @Nonnull
    StoneActionResult corrupt(@Nonnull T item, @Nonnull Random random);

    // ═══════════════════════════════════════════════════════════════════
    // TYPE-SPECIFIC OPERATIONS (with defaults)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Rerolls weapon implicit damage value.
     *
     * <p>Gear-only operation. Default returns {@code invalidTarget(GEAR_ONLY)}.
     */
    @Nonnull
    default StoneActionResult rerollImplicit(@Nonnull T item, @Nonnull Random random) {
        return StoneActionResult.invalidTarget(ItemTargetType.GEAR_ONLY);
    }

    /**
     * Identifies an unidentified item (reveals modifiers).
     *
     * <p>Map-only operation. Default returns {@code invalidTarget(MAP_ONLY)}.
     */
    @Nonnull
    default StoneActionResult identify(@Nonnull T item) {
        return StoneActionResult.invalidTarget(ItemTargetType.MAP_ONLY);
    }

    /**
     * Changes the item's level and recalculates level-dependent properties.
     *
     * <p>For gear: recalculates implicit damage/defense ranges for the new level.
     * For maps: just changes the level number (no implicits to recalculate).
     *
     * @param item The item to modify
     * @param newLevel The new level
     * @param random The random source for re-rolling implicit values
     * @return Result with the level-adjusted item
     */
    @Nonnull
    default StoneActionResult changeLevel(@Nonnull T item, int newLevel, @Nonnull Random random) {
        return StoneActionResult.success(item.withLevel(newLevel),
            "Level changed to " + newLevel + ".");
    }

    /**
     * Changes the item's biome to a random different one.
     *
     * <p>Map-only operation. Default returns {@code invalidTarget(MAP_ONLY)}.
     */
    @Nonnull
    default StoneActionResult changeBiome(@Nonnull T item, @Nonnull Random random) {
        return StoneActionResult.invalidTarget(ItemTargetType.MAP_ONLY);
    }
}
