package io.github.larsonix.trailoforbis.combat.attackspeed;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionChain;
import com.hypixel.hytale.server.core.entity.InteractionManager;
import com.hypixel.hytale.server.core.modules.interaction.InteractionModule;
import com.hypixel.hytale.server.core.modules.interaction.system.InteractionSystems;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.attributes.ComputedStats;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Set;
import java.util.UUID;

/**
 * Tier 1 attack speed: shifts server-side interaction chain timing so damage windows
 * complete faster, matching the visual speed from Tier 2 animation sync.
 *
 * <p>Runs AFTER {@link InteractionSystems.TickInteractionManagerSystem} each tick.
 * For each player entity with active Primary/Secondary attack chains, applies
 * {@link InteractionChain#setTimeShift(float)} based on the player's attack speed stat.
 *
 * <p><b>Formula:</b>
 * <pre>
 * multiplier = AnimationSpeedSyncConfig.calculateMultiplier(attackSpeedPercent)
 * timeShift  = multiplier - 1.0
 * </pre>
 *
 * <p>With default scale 0.5, a player with +100% attack speed gets timeShift = 0.5,
 * meaning damage windows complete 1.5× faster.
 *
 * @see AnimationSpeedSyncConfig#calculateMultiplier(float)
 * @see InteractionChain#setTimeShift(float)
 */
public class InteractionTimeShiftSystem extends EntityTickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final TrailOfOrbis plugin;
    private final AnimationSpeedSyncConfig syncConfig;

    private final ComponentType<EntityStore, InteractionManager> interactionManagerType;
    private final ComponentType<EntityStore, PlayerRef> playerRefType;

    @Nullable
    private Query<EntityStore> query;

    private final Set<Dependency<EntityStore>> dependencies;

    public InteractionTimeShiftSystem(@Nonnull TrailOfOrbis plugin, @Nonnull AnimationSpeedSyncConfig syncConfig) {
        this.plugin = plugin;
        this.syncConfig = syncConfig;
        this.interactionManagerType = InteractionModule.get().getInteractionManagerComponent();
        this.playerRefType = PlayerRef.getComponentType();
        this.dependencies = Set.of(
                new SystemDependency<>(Order.AFTER, InteractionSystems.TickInteractionManagerSystem.class)
        );
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return dependencies;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        if (query == null) {
            query = Archetype.of(interactionManagerType, playerRefType);
        }
        return query;
    }

    @Override
    public void tick(
            float dt,
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        Ref<EntityStore> entityRef = archetypeChunk.getReferenceTo(index);
        if (entityRef == null || !entityRef.isValid()) {
            return;
        }

        // Get the player's UUID
        PlayerRef playerRef = store.getComponent(entityRef, playerRefType);
        if (playerRef == null) {
            return;
        }
        UUID uuid = playerRef.getUuid();

        // Look up attack speed stat
        ComputedStats stats = plugin.getAttributeManager().getStats(uuid);
        if (stats == null) {
            return;
        }

        float attackSpeedPercent = stats.getAttackSpeedPercent();
        if (Math.abs(attackSpeedPercent) < 0.001f) {
            return;
        }

        float multiplier = syncConfig.calculateMultiplier(attackSpeedPercent);
        float timeShift = multiplier - 1.0f;

        if (Math.abs(timeShift) < 0.001f) {
            return;
        }

        // Get the interaction manager and iterate active chains
        InteractionManager manager = store.getComponent(entityRef, interactionManagerType);
        if (manager == null) {
            return;
        }

        for (InteractionChain chain : manager.getChains().values()) {
            // Only affect Primary (left-click) and Secondary (right-click) attack chains
            InteractionType type = chain.getType();
            if (type != InteractionType.Primary && type != InteractionType.Secondary) {
                continue;
            }

            // Only shift chains that are still in progress
            if (chain.getServerState() != InteractionState.NotFinished) {
                continue;
            }

            // Avoid redundant flagDesync packets if timeShift hasn't changed
            if (Math.abs(chain.getTimeShift() - timeShift) < 0.001f) {
                continue;
            }

            chain.setTimeShift(timeShift);
            chain.flagDesync();
        }
    }
}
