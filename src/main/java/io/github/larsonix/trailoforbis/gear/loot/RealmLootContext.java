package io.github.larsonix.trailoforbis.gear.loot;

/**
 * Context data for realm loot bonuses.
 *
 * <p>Carries IIQ (Increased Item Quantity) and IIR (Increased Item Rarity)
 * bonuses from realm modifiers to the loot calculation system.
 *
 * <h2>Usage</h2>
 * <p>Created by {@link LootListener} when processing realm mob deaths,
 * passed to {@link LootCalculator#calculateLoot(java.util.UUID, LootSettings.MobType, int, com.hypixel.hytale.math.vector.Vector3d, RealmLootContext)}.
 *
 * @param itemQuantityBonus IIQ bonus percentage (e.g., 25.0 = +25% drop chance)
 * @param itemRarityBonus IIR bonus percentage (e.g., 30.0 = +30% rarity)
 *
 * @see io.github.larsonix.trailoforbis.maps.integration.RealmLootIntegration
 */
public record RealmLootContext(
    double itemQuantityBonus,
    double itemRarityBonus
) {

    /**
     * Empty context with no bonuses - used for non-realm mobs.
     */
    public static final RealmLootContext NONE = new RealmLootContext(0, 0);

    /**
     * Checks if this context has any loot bonuses.
     *
     * @return true if either IIQ or IIR bonus is positive
     */
    public boolean hasBonus() {
        return itemQuantityBonus > 0 || itemRarityBonus > 0;
    }

    /**
     * Creates a context from percentage values.
     *
     * @param iiqPercent Item quantity bonus (percentage, e.g., 25.0 = +25%)
     * @param iirPercent Item rarity bonus (percentage, e.g., 30.0 = +30%)
     * @return New RealmLootContext
     */
    public static RealmLootContext of(double iiqPercent, double iirPercent) {
        if (iiqPercent <= 0 && iirPercent <= 0) {
            return NONE;
        }
        return new RealmLootContext(iiqPercent, iirPercent);
    }

    @Override
    public String toString() {
        return String.format("RealmLootContext[IIQ=%.1f%%, IIR=%.1f%%]",
            itemQuantityBonus, itemRarityBonus);
    }
}
