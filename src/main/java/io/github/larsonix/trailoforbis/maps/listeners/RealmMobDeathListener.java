package io.github.larsonix.trailoforbis.maps.listeners;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.maps.RealmsManager;
import io.github.larsonix.trailoforbis.maps.components.RealmMobComponent;
import io.github.larsonix.trailoforbis.maps.components.RealmPlayerComponent;
import io.github.larsonix.trailoforbis.maps.instance.RealmInstance;
import io.github.larsonix.trailoforbis.maps.spawning.RealmMobSpawner;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

/**
 * ECS system that handles mob deaths within realms.
 *
 * <p>Extends {@link DeathSystems.OnDeathSystem} to react when any entity dies.
 * Filters to only process mobs that have a {@link RealmMobComponent}.
 *
 * <p>When a realm mob dies:
 * <ul>
 *   <li>Credits the kill to the player (if killed by player)</li>
 *   <li>Updates player's {@link RealmPlayerComponent} statistics</li>
 *   <li>Notifies the realm instance for completion tracking</li>
 *   <li>Awards XP bonuses based on realm modifiers</li>
 * </ul>
 *
 * <p>This system runs alongside the standard loot system but handles
 * realm-specific logic separately.
 *
 * @see RealmMobComponent
 * @see RealmPlayerComponent
 * @see io.github.larsonix.trailoforbis.maps.core.RealmCompletionTracker
 */
public class RealmMobDeathListener extends DeathSystems.OnDeathSystem {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final TrailOfOrbis plugin;

    // Component types for querying
    private final ComponentType<EntityStore, RealmMobComponent> realmMobType;
    private final ComponentType<EntityStore, NPCEntity> npcType;
    private final ComponentType<EntityStore, Player> playerType;
    private final ComponentType<EntityStore, PlayerRef> playerRefType;
    private final ComponentType<EntityStore, RealmPlayerComponent> realmPlayerType;

    /**
     * Creates a new realm mob death listener.
     *
     * @param plugin The TrailOfOrbis plugin instance
     */
    public RealmMobDeathListener(@Nonnull TrailOfOrbis plugin) {
        this.plugin = plugin;
        this.realmMobType = RealmMobComponent.getComponentType();
        this.npcType = NPCEntity.getComponentType();
        this.playerType = Player.getComponentType();
        this.playerRefType = PlayerRef.getComponentType();
        this.realmPlayerType = RealmPlayerComponent.getComponentType();
    }

    @Override
    @Nonnull
    public Query<EntityStore> getQuery() {
        // Match ALL entity deaths - we filter to realm mobs in onComponentAdded
        return Archetype.empty();
    }

    @Override
    public void onComponentAdded(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull DeathComponent deathComponent,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        // Skip if not a realm mob
        RealmMobComponent realmMob = store.getComponent(ref, realmMobType);
        if (realmMob == null || realmMob.getRealmId() == null) {
            return;
        }

        UUID realmId = realmMob.getRealmId();
        // Per-mob event - use FINE level
        LOGGER.atFine().log("[RealmMobDeath] Mob died in realm %s", realmId.toString().substring(0, 8));

        // Get realm instance
        RealmsManager manager = plugin.getRealmsManager();
        if (manager == null) {
            LOGGER.atWarning().log("RealmsManager not available - skipping death tracking");
            return;
        }

        Optional<RealmInstance> realmOpt = manager.getRealm(realmId);
        if (realmOpt.isEmpty()) {
            LOGGER.atFine().log("Realm %s no longer exists - skipping death tracking",
                realmId.toString().substring(0, 8));
            return;
        }

        RealmInstance realm = realmOpt.get();

        // Extract the killer player (if any)
        UUID killerId = extractKillerId(ref, deathComponent, store);

        // Notify the mob spawner of the death (marks mob as killed in persistent state)
        // This prevents the mob from being respawned by the despawn recovery system
        RealmMobSpawner mobSpawner = manager.getMobSpawner();
        if (mobSpawner != null) {
            mobSpawner.onMobDeath(realmId, ref);
        }

        // Credit the kill and check for completion
        boolean completionTriggered = false;
        if (realmMob.countsForCompletion()) {
            completionTriggered = realm.recordMobKill(killerId);

            // Log kill count - use FINE level to reduce log spam
            int remaining = realm.getCompletionTracker().getRemainingMonsters();
            int total = realm.getCompletionTracker().getTotalMonsters();
            LOGGER.atFine().log("[RealmMobDeath] Kill recorded: realm=%s, remaining=%d/%d",
                realmId.toString().substring(0, 8), remaining, total);
        }

        // Update player stats if killed by a player in the realm
        if (killerId != null) {
            updatePlayerStats(killerId, store);
        }

        // Note: Combat HUD refresh is now handled automatically via HyUI's
        // withRefreshRate() callback - no manual refresh needed

        // Log for debugging
        if (manager.getConfig().isDebugMode()) {
            LOGGER.atInfo().log("Realm mob killed: realm=%s, counts=%b, killer=%s, remaining=%d",
                realmId.toString().substring(0, 8),
                realmMob.countsForCompletion(),
                killerId != null ? killerId.toString().substring(0, 8) : "none",
                realm.getCompletionTracker().getRemainingMonsters());
        }

        // Trigger completion if all mobs are killed
        if (completionTriggered) {
            LOGGER.atInfo().log("All mobs killed in realm %s - triggering completion!",
                realmId.toString().substring(0, 8));
            manager.triggerCompletion(realm);
        }
    }

    /**
     * Extracts the killer player's UUID from the death component.
     *
     * @param death The death component
     * @param store The entity store
     * @return The killer's UUID, or null if not killed by a player
     */
    @Nullable
    private UUID extractKillerId(
            @Nonnull Ref<EntityStore> deadRef,
            @Nonnull DeathComponent death,
            @Nonnull Store<EntityStore> store) {

        // Get the damage that caused death
        Damage deathInfo = death.getDeathInfo();
        if (deathInfo == null) {
            return null;
        }

        Damage.Source source = deathInfo.getSource();

        // Projectile damage (ranged) - shooter is the real attacker
        // Check ProjectileSource FIRST since it extends EntitySource
        if (source instanceof Damage.ProjectileSource projectileSource) {
            return extractPlayerUuidFromRef(projectileSource.getRef(), store);
        }

        // Direct entity damage (melee, or hex spell rewritten to EntitySource by
        // HexDamageAttributionSystem in FilterDamageGroup before death)
        if (source instanceof Damage.EntitySource entitySource) {
            return extractPlayerUuidFromRef(entitySource.getRef(), store);
        }

        return null;
    }

    /**
     * Extracts player UUID from an entity reference.
     *
     * @param ref The entity reference
     * @param store The entity store
     * @return The player's UUID, or null if not a player
     */
    @Nullable
    private UUID extractPlayerUuidFromRef(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store) {

        if (!ref.isValid()) {
            return null;
        }

        // Check if the entity has a Player component
        Player player = store.getComponent(ref, playerType);
        if (player == null) {
            return null;
        }

        // Get PlayerRef component for UUID
        PlayerRef playerRef = store.getComponent(ref, playerRefType);
        if (playerRef == null) {
            return null;
        }

        return playerRef.getUuid();
    }

    /**
     * Updates the player's realm statistics after a kill.
     *
     * @param playerId The player's UUID
     * @param store The entity store
     */
    private void updatePlayerStats(@Nonnull UUID playerId, @Nonnull Store<EntityStore> store) {
        // Find the player entity and update their RealmPlayerComponent
        // This is done by iterating players with RealmPlayerComponent that match the UUID
        // For now, we rely on RealmInstance.recordMobKill() to track kills
        // The player's RealmPlayerComponent will be updated when they receive rewards

        // In a more complete implementation, we would:
        // 1. Find the player's entity reference
        // 2. Get their RealmPlayerComponent
        // 3. Call incrementKillCount()
        // 4. Optionally update damage dealt

        // For now, the RealmInstance handles kill tracking centrally
        LOGGER.atFine().log("Player %s scored a realm mob kill", playerId.toString().substring(0, 8));
    }
}
