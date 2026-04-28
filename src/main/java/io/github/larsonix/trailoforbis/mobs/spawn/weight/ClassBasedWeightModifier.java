package io.github.larsonix.trailoforbis.mobs.spawn.weight;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.mobs.classification.RPGMobClass;
import io.github.larsonix.trailoforbis.mobs.spawn.config.MobSpawnConfig;

import javax.annotation.Nonnull;

/**
 * Spawn weight modifier based on mob classification.
 *
 * <p>Each mob class (Monster, Elite, Legendary, etc.) has a configurable
 * spawn multiplier that affects how often mobs of that class spawn.
 *
 * <p>Example configuration effects:
 * <ul>
 *   <li>MONSTER = 1.5: Monsters spawn 50% more often</li>
 *   <li>ELITE = 0.8: Elites spawn 20% less often</li>
 *   <li>MYTHIC = 0.2: Mythics spawn 80% less often (very rare)</li>
 * </ul>
 */
public class ClassBasedWeightModifier implements SpawnWeightModifier {

    private final MobSpawnConfig config;

    /**
     * Creates a new class-based weight modifier.
     *
     * @param config The mob spawn configuration
     */
    public ClassBasedWeightModifier(@Nonnull MobSpawnConfig config) {
        this.config = config;
    }

    @Override
    public double getWeightMultiplier(
            int roleIndex,
            @Nonnull RPGMobClass mobClass,
            @Nonnull Vector3d position,
            @Nonnull Store<EntityStore> store) {

        return config.getClassMultiplier(mobClass);
    }
}
