package io.github.larsonix.trailoforbis.mobs.systems;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.mobs.MobScalingManager;
import io.github.larsonix.trailoforbis.mobs.spawn.manager.RPGSpawnManager;

import javax.annotation.Nonnull;

/**
 * Ticking system that processes deferred spawn requests.
 *
 * <p>This system exists because spawning entities from within {@code onEntityAdd}
 * callbacks causes "Store is currently processing!" errors. Instead, spawn requests
 * are queued and processed here at the start of each tick, outside of entity processing.
 *
 * <p><b>Flow:</b>
 * <ol>
 *   <li>MobScalingSystem.onEntityAdd detects a mob spawn</li>
 *   <li>RPGSpawnManager.spawnAdditional queues the spawn request</li>
 *   <li>This system processes one spawn every {@link #TICKS_PER_SPAWN} ticks</li>
 *   <li>Additional mobs are spawned safely at a controlled rate</li>
 * </ol>
 *
 * <p><b>Rate limiting:</b> Spawns are processed at ~2 per second (1 every 10 ticks at 20 TPS).
 * This prevents lag spikes from burst spawning when a high-level player enters an area.
 */
public class DeferredSpawnSystem extends TickingSystem<EntityStore> {

    /**
     * Number of ticks between spawn processing.
     * At 20 TPS, this means ~2 spawns per second (10 ticks = 0.5 seconds).
     */
    private static final int TICKS_PER_SPAWN = 10;

    private final TrailOfOrbis plugin;
    private int tickCounter = 0;

    public DeferredSpawnSystem(@Nonnull TrailOfOrbis plugin) {
        this.plugin = plugin;
    }

    @Override
    public void tick(float dt, int tick, @Nonnull Store<EntityStore> store) {
        MobScalingManager manager = plugin.getMobScalingManager();
        if (manager == null || !manager.isInitialized()) {
            return;
        }

        RPGSpawnManager spawnManager = manager.getRPGSpawnManager();
        if (spawnManager == null || !spawnManager.isEnabled()) {
            return;
        }

        // Rate-limited spawn processing: one spawn every TICKS_PER_SPAWN ticks
        tickCounter++;
        if (tickCounter >= TICKS_PER_SPAWN) {
            tickCounter = 0;
            spawnManager.processOneSpawn(store);
        }
    }
}
