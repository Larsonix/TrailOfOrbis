package io.github.larsonix.trailoforbis.loot.container;

import io.github.larsonix.trailoforbis.gear.loot.RealmLootContext;

import javax.annotation.Nonnull;

/**
 * Context data for container loot generation, carrying zone-level information
 * and realm bonuses through the container loot pipeline.
 *
 * <p>This mirrors how {@link io.github.larsonix.trailoforbis.gear.loot.LootCalculator}
 * uses mob level + {@link RealmLootContext} for mob drops. Without this context,
 * container loot would always generate at the player's level regardless of
 * where the chest is located.
 *
 * <h2>Source Level Determination</h2>
 * <ul>
 *   <li><b>Realm containers</b>: {@code RealmInstance.getLevel() + DROP_LEVEL_BONUS}</li>
 *   <li><b>Overworld containers</b>: Distance-based level from
 *       {@code DistanceBonusCalculator.estimateLevelFromDistance()}</li>
 *   <li><b>Admin/API calls</b>: Falls back to player level as source</li>
 * </ul>
 *
 * @param sourceLevel      Zone-derived level (realm level or distance-based level)
 * @param playerLevel      Player's character level (for blending target)
 * @param realmLootContext  IIQ/IIR bonuses from realm modifiers ({@link RealmLootContext#NONE} for overworld)
 * @param qualityBonus     GEAR_QUALITY_BONUS from realm modifiers (0 for overworld)
 *
 * @see ContainerLootInterceptor
 * @see ContainerLootGenerator
 */
public record ContainerLootContext(
    int sourceLevel,
    int playerLevel,
    @Nonnull RealmLootContext realmLootContext,
    int qualityBonus
) {

    /**
     * Creates a context for overworld containers (no realm bonuses).
     *
     * @param sourceLevel Distance-based level from chest position
     * @param playerLevel Player's character level
     * @return Overworld context with no realm bonuses
     */
    @Nonnull
    public static ContainerLootContext overworld(int sourceLevel, int playerLevel) {
        return new ContainerLootContext(sourceLevel, playerLevel, RealmLootContext.NONE, 0);
    }

    /**
     * Creates a context for realm containers with full modifier support.
     *
     * @param sourceLevel      Realm level + DROP_LEVEL_BONUS
     * @param playerLevel      Player's character level
     * @param realmLootContext  IIQ/IIR bonuses from realm modifiers
     * @param qualityBonus     GEAR_QUALITY_BONUS from realm modifiers
     * @return Realm context with all bonuses
     */
    @Nonnull
    public static ContainerLootContext realm(int sourceLevel, int playerLevel,
                                             @Nonnull RealmLootContext realmLootContext,
                                             int qualityBonus) {
        return new ContainerLootContext(sourceLevel, playerLevel, realmLootContext, qualityBonus);
    }

    @Override
    public String toString() {
        return String.format("ContainerLootContext[src=%d, player=%d, %s, quality+%d]",
            sourceLevel, playerLevel, realmLootContext, qualityBonus);
    }
}
