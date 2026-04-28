package io.github.larsonix.trailoforbis.mobs.spawn.weight;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.mobs.classification.RPGMobClass;

import javax.annotation.Nonnull;

/**
 * Interface for calculating spawn weight multipliers.
 *
 * <p>Implementations modify the effective spawn rate based on various factors
 * such as mob classification, player level, distance, etc.
 *
 * <p>Multiple modifiers can be chained together, with their multipliers combined.
 */
@FunctionalInterface
public interface SpawnWeightModifier {

    /**
     * Calculates a spawn weight multiplier for a mob.
     *
     * @param roleIndex The NPC role index
     * @param mobClass  The RPG mob classification
     * @param position  The spawn position
     * @param store     The entity store for querying nearby entities
     * @return Weight multiplier (1.0 = unchanged, >1 = more spawns, <1 = fewer spawns)
     */
    double getWeightMultiplier(
        int roleIndex,
        @Nonnull RPGMobClass mobClass,
        @Nonnull Vector3d position,
        @Nonnull Store<EntityStore> store
    );
}
