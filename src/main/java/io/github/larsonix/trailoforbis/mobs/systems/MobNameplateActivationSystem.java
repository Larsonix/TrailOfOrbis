package io.github.larsonix.trailoforbis.mobs.systems;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.QuerySystem;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.mobs.MobScalingConfig;
import io.github.larsonix.trailoforbis.mobs.MobScalingManager;
import io.github.larsonix.trailoforbis.mobs.component.MobScalingComponent;

import javax.annotation.Nonnull;

/**
 * Ticking system that populates Nameplate text on mobs when a player gets
 * within proximity range or the mob takes damage.
 *
 * <p><b>Architecture:</b> All non-PASSIVE mobs receive an empty {@link Nameplate}
 * component at spawn time (via {@link MobScalingSystem}). This suppresses the
 * client's default health bar rendering. This system then populates the text
 * (e.g., "Lv27", "[Elite] Lv27") when a player is within range, providing a
 * deferred visual reveal without the health bar burst.
 *
 * <p><b>Activation triggers:</b>
 * <ul>
 *   <li>Player within configurable range (default 20 blocks)</li>
 *   <li>Mob has taken damage (health &lt; maxHealth) — combat indicator</li>
 * </ul>
 *
 * <p>Once text is set, it stays permanently (removed only on entity death/despawn).
 */
public class MobNameplateActivationSystem extends TickingSystem<EntityStore>
    implements QuerySystem<EntityStore> {

    private static final int SCAN_INTERVAL_TICKS = 10; // 0.5 seconds at 20 TPS

    private final TrailOfOrbis plugin;

    private Archetype<EntityStore> query = null;

    private final ComponentType<EntityStore, MobScalingComponent> scalingType;
    private final ComponentType<EntityStore, EntityStatMap> statMapType;
    private final ComponentType<EntityStore, DeathComponent> deathType;
    private final ComponentType<EntityStore, TransformComponent> transformType;
    private final ComponentType<EntityStore, Nameplate> nameplateType;

    private int healthStatIndex = -1;

    public MobNameplateActivationSystem(@Nonnull TrailOfOrbis plugin) {
        this.plugin = plugin;
        this.scalingType = MobScalingComponent.getComponentType();
        this.statMapType = EntityStatMap.getComponentType();
        this.deathType = DeathComponent.getComponentType();
        this.transformType = TransformComponent.getComponentType();
        this.nameplateType = Nameplate.getComponentType();
    }

    @Override
    public Query<EntityStore> getQuery() {
        if (query == null) {
            // Include Nameplate in query — all non-PASSIVE mobs have it from spawn.
            // PASSIVE mobs (no Nameplate) are automatically excluded.
            query = Archetype.of(scalingType, statMapType, transformType, nameplateType);
        }
        return query;
    }

    @Override
    public void tick(float dt, int tick, @Nonnull Store<EntityStore> store) {
        if (tick % SCAN_INTERVAL_TICKS != 0) {
            return;
        }

        MobScalingManager manager = plugin.getMobScalingManager();
        if (manager == null || !manager.isInitialized()) {
            return;
        }

        MobScalingConfig config = manager.getConfig();
        if (!config.isEnabled()) {
            return;
        }

        if (healthStatIndex < 0) {
            healthStatIndex = EntityStatType.getAssetMap().getIndex("Health");
        }

        double activationRange = config.getNameplateActivationRange();
        double activationRangeSq = activationRange * activationRange;

        World world = store.getExternalData().getWorld();
        if (world == null || !world.isAlive()) {
            return;
        }

        // Cache player positions for this tick to avoid repeated lookups
        var playerRefs = world.getPlayerRefs();
        if (playerRefs.isEmpty()) {
            return;
        }

        Vector3d[] playerPositions = new Vector3d[playerRefs.size()];
        int playerCount = 0;
        for (PlayerRef playerRef : playerRefs) {
            Ref<EntityStore> entityRef = playerRef.getReference();
            if (entityRef == null || !entityRef.isValid()) {
                continue;
            }
            TransformComponent transform = store.getComponent(entityRef, transformType);
            if (transform != null) {
                playerPositions[playerCount++] = transform.getPosition();
            }
        }

        if (playerCount == 0) {
            return;
        }

        final int finalPlayerCount = playerCount;
        store.forEachChunk(tick, (chunk, commandBuffer) -> {
            processChunk(chunk, commandBuffer, playerPositions, finalPlayerCount, activationRangeSq);
        });
    }

    private void processChunk(
        @Nonnull ArchetypeChunk<EntityStore> chunk,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Vector3d[] playerPositions,
        int playerCount,
        double activationRangeSq
    ) {
        int size = chunk.size();

        for (int i = 0; i < size; i++) {
            Ref<EntityStore> mobRef = chunk.getReferenceTo(i);
            if (mobRef == null) {
                continue;
            }

            // Skip dead mobs
            if (commandBuffer.getArchetype(mobRef).contains(deathType)) {
                continue;
            }

            // Skip already-activated nameplates (text is non-empty)
            Nameplate nameplate = chunk.getComponent(i, nameplateType);
            if (nameplate == null || !nameplate.getText().isEmpty()) {
                continue;
            }

            MobScalingComponent scaling = chunk.getComponent(i, scalingType);
            if (scaling == null || scaling.isDying()) {
                continue;
            }

            String text = scaling.getNameplateText();
            if (text == null || text.isEmpty()) {
                continue; // No text stored — shouldn't happen for non-PASSIVE, but guard
            }

            // Check if mob has taken damage (health < maxHealth = combat active)
            boolean damaged = false;
            EntityStatMap statMap = chunk.getComponent(i, statMapType);
            if (statMap != null && healthStatIndex >= 0) {
                EntityStatValue healthStat = statMap.get(healthStatIndex);
                if (healthStat != null && healthStat.get() < healthStat.getMax()) {
                    damaged = true;
                }
            }

            if (damaged) {
                nameplate.setText(text);
                continue;
            }

            // Check proximity to any player
            TransformComponent mobTransform = chunk.getComponent(i, transformType);
            if (mobTransform == null) {
                continue;
            }

            Vector3d mobPos = mobTransform.getPosition();
            for (int p = 0; p < playerCount; p++) {
                Vector3d playerPos = playerPositions[p];
                double dx = mobPos.getX() - playerPos.getX();
                double dz = mobPos.getZ() - playerPos.getZ();
                double distSq = dx * dx + dz * dz; // Horizontal distance only

                if (distSq <= activationRangeSq) {
                    nameplate.setText(text);
                    break;
                }
            }
        }
    }
}
