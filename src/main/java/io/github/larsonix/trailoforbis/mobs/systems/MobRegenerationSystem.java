package io.github.larsonix.trailoforbis.mobs.systems;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.QuerySystem;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.mobs.MobScalingConfig;
import io.github.larsonix.trailoforbis.mobs.MobScalingManager;
import io.github.larsonix.trailoforbis.mobs.component.MobScalingComponent;
import io.github.larsonix.trailoforbis.mobs.model.MobStats;

import javax.annotation.Nonnull;

/**
 * Ticking system that handles health regeneration for scaled mobs.
 *
 * <p>This system processes all entities with {@link MobScalingComponent} and applies
 * smooth health regeneration based on the mob's {@code healthRegen} stat.
 *
 * <p><b>Design:</b> Uses chunk-based processing for efficiency. Regeneration is applied
 * every tick using delta time for smooth recovery instead of discrete 1-second chunks.
 *
 * <p><b>Regeneration Formula:</b>
 * <pre>
 *   regenThisTick = healthRegen × dt
 *   newHealth = min(currentHealth + regenThisTick, maxHealth)
 * </pre>
 *
 * <p>Regeneration only occurs when:
 * <ul>
 *   <li>Mob is below max health</li>
 *   <li>Mob has healthRegen > 0</li>
 *   <li>Mob scaling system is enabled</li>
 * </ul>
 */
public class MobRegenerationSystem extends TickingSystem<EntityStore>
    implements QuerySystem<EntityStore> {

    private final TrailOfOrbis plugin;

    private Archetype<EntityStore> query = null;

    private final ComponentType<EntityStore, MobScalingComponent> scalingType;
    private final ComponentType<EntityStore, EntityStatMap> statMapType;
    private final ComponentType<EntityStore, DeathComponent> deathType;

    /** Cached health stat index for performance */
    private int healthStatIndex = -1;

    public MobRegenerationSystem(@Nonnull TrailOfOrbis plugin) {
        this.plugin = plugin;
        this.scalingType = MobScalingComponent.getComponentType();
        this.statMapType = EntityStatMap.getComponentType();
        this.deathType = DeathComponent.getComponentType();
    }

    @Override
    public Query<EntityStore> getQuery() {
        if (query == null) {
            query = Archetype.of(scalingType, statMapType);
        }
        return query;
    }

    @Override
    public void tick(float dt, int tick, @Nonnull Store<EntityStore> store) {
        // Check if mob scaling is enabled
        MobScalingManager manager = plugin.getMobScalingManager();
        if (manager == null || !manager.isInitialized()) {
            return;
        }

        MobScalingConfig config = manager.getConfig();
        if (!config.isEnabled()) {
            return;
        }

        // Lazy init health stat index
        if (healthStatIndex < 0) {
            healthStatIndex = EntityStatType.getAssetMap().getIndex("Health");
        }

        // Process all chunks with scaled mobs, passing dt for smooth regen
        final float deltaTime = dt;
        store.forEachChunk(tick, (chunk, commandBuffer) -> {
            processChunk(chunk, store, deltaTime);
        });
    }

    /**
     * Processes a chunk of entities, applying health regeneration to each.
     *
     * @param chunk The archetype chunk containing entities
     * @param store The entity store
     * @param dt Delta time in seconds since last tick
     */
    private void processChunk(
        @Nonnull ArchetypeChunk<EntityStore> chunk,
        @Nonnull Store<EntityStore> store,
        float dt
    ) {
        int size = chunk.size();

        for (int i = 0; i < size; i++) {
            // Skip dead mobs - they should not regenerate
            Ref<EntityStore> mobRef = chunk.getReferenceTo(i);
            if (mobRef != null && store.getComponent(mobRef, deathType) != null) {
                continue;
            }

            MobScalingComponent scaling = chunk.getComponent(i, scalingType);
            if (scaling == null) {
                continue;
            }

            // CRITICAL: Check isDying flag to catch mobs in the race condition window
            // (health=0 but DeathComponent not yet committed via CommandBuffer)
            if (scaling.isDying()) {
                continue;
            }

            MobStats stats = scaling.getStats();
            if (stats == null) {
                continue;
            }

            double healthRegen = stats.healthRegen();
            if (healthRegen <= 0.0) {
                continue; // No regen stat
            }

            EntityStatMap statMap = chunk.getComponent(i, statMapType);
            if (statMap == null) {
                continue;
            }

            applyHealthRegeneration(statMap, healthRegen, dt);
        }
    }

    /**
     * Applies health regeneration to a mob, scaled by delta time.
     *
     * @param statMap The entity's stat map
     * @param healthRegen Amount of health to regenerate per second
     * @param dt Delta time in seconds since last tick
     */
    private void applyHealthRegeneration(
        @Nonnull EntityStatMap statMap,
        double healthRegen,
        float dt
    ) {
        EntityStatValue healthStat = statMap.get(healthStatIndex);
        if (healthStat == null) {
            return;
        }

        float currentHealth = healthStat.get();
        float maxHealth = healthStat.getMax();

        // Skip dead mobs - don't regenerate entities with 0 or negative health
        // This catches the race condition where DeathComponent hasn't been added yet
        if (currentHealth <= 0) {
            return;
        }

        // Skip if already at max health
        if (currentHealth >= maxHealth) {
            return;
        }

        // Calculate new health with smooth regen (healthRegen * dt), capped at max
        float regenThisTick = (float) (healthRegen * dt);
        float newHealth = Math.min(currentHealth + regenThisTick, maxHealth);

        // Apply the regeneration
        statMap.setStatValue(healthStatIndex, newHealth);
    }
}
