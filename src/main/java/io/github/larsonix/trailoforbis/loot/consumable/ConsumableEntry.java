package io.github.larsonix.trailoforbis.loot.consumable;

import javax.annotation.Nonnull;

/**
 * One droppable consumable item in a tier pool.
 *
 * @param itemId   Hytale item ID (e.g., "Potion_Health_Small")
 * @param minStack Minimum stack size per drop
 * @param maxStack Maximum stack size per drop
 * @param minLevel Minimum player level required for this item to drop
 */
public record ConsumableEntry(
        @Nonnull String itemId,
        int minStack,
        int maxStack,
        int minLevel
) {

    public ConsumableEntry {
        if (minStack < 1) minStack = 1;
        if (maxStack < minStack) maxStack = minStack;
        if (minLevel < 1) minLevel = 1;
    }
}
