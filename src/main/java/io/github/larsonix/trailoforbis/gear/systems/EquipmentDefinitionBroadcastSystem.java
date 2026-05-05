package io.github.larsonix.trailoforbis.gear.systems;

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
import com.hypixel.hytale.protocol.ItemBase;
import com.hypixel.hytale.protocol.UpdateType;
import com.hypixel.hytale.protocol.packets.assets.UpdateItems;
import com.hypixel.hytale.server.core.entity.EntityUtils;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems;
import com.hypixel.hytale.server.core.modules.entity.tracker.LegacyEntityTrackerSystems;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.gear.item.ItemDefinitionBuilder;
import io.github.larsonix.trailoforbis.gear.item.TranslationSyncService;
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.util.GearUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Broadcasts RPG gear item definitions to nearby players so armor renders
 * correctly on remote clients.
 *
 * <h2>Problem</h2>
 * <p>Hytale's {@link LegacyEntityTrackerSystems.LegacyEquipment} sends
 * {@code Equipment} packets containing item ID strings ({@code rpg_gear_*})
 * to all viewers. But {@code UpdateItems} definitions for those IDs are only
 * sent to the owning player. Remote clients can't resolve the IDs and the
 * armor renders as invisible.
 *
 * <h2>Solution</h2>
 * <p>This system runs in {@code QUEUE_UPDATE_GROUP} BEFORE
 * {@code LegacyEquipment}. It detects two cases:
 * <ol>
 *   <li><b>New viewer:</b> A player just entered view range
 *       ({@code newlyVisibleTo} is not empty) — send definitions to them.</li>
 *   <li><b>Equipment changed:</b> The observed player's equipped RPG gear
 *       changed since last tick — send updated definitions to all viewers.</li>
 * </ol>
 *
 * <p>Since {@code UpdateItems} is sent immediately via
 * {@code writeNoCache()}, and {@code LegacyEquipment} only queues updates
 * for later delivery by {@code SendPackets}, TCP ordering guarantees the
 * definitions arrive before the Equipment packet that references them.
 *
 * <h2>Performance</h2>
 * <p>Runs every tick but early-exits when:
 * <ul>
 *   <li>Entity is not a Player (NPC/mob — no RPG gear)</li>
 *   <li>No new viewers AND equipment hasn't changed</li>
 *   <li>Player has no RPG gear equipped</li>
 * </ul>
 * <p>Per-viewer dedup prevents redundant sends: each viewer tracks which
 * item IDs they've already received.
 */
public final class EquipmentDefinitionBroadcastSystem extends EntityTickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final ComponentType<EntityStore, EntityTrackerSystems.Visible> visibleType;
    private final ComponentType<EntityStore, PlayerRef> playerRefType;
    @Nonnull
    private final Query<EntityStore> query;

    private final ItemDefinitionBuilder definitionBuilder;
    private final TranslationSyncService translationService;

    /**
     * Per observed player: the set of rpg_gear_* IDs they had equipped last tick.
     * Used to detect equipment changes without consuming Hytale's own flag.
     */
    private final ConcurrentHashMap<UUID, Set<String>> lastKnownGear = new ConcurrentHashMap<>();

    /**
     * Per viewer: the set of rpg_gear_* IDs whose definitions have been sent.
     * Prevents redundant UpdateItems packets.
     */
    private final ConcurrentHashMap<UUID, Set<String>> viewerSentCache = new ConcurrentHashMap<>();

    public EquipmentDefinitionBroadcastSystem(
            @Nonnull ItemDefinitionBuilder definitionBuilder,
            @Nonnull TranslationSyncService translationService) {
        this.definitionBuilder = definitionBuilder;
        this.translationService = translationService;
        this.visibleType = EntityTrackerSystems.Visible.getComponentType();
        this.playerRefType = PlayerRef.getComponentType();
        this.query = Query.and(visibleType, playerRefType);
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return query;
    }

    /**
     * Run in the same group as LegacyEquipment (QUEUE_UPDATE_GROUP)
     * to guarantee we're in the same tick phase.
     */
    @Nullable
    @Override
    public com.hypixel.hytale.component.SystemGroup<EntityStore> getGroup() {
        return EntityTrackerSystems.QUEUE_UPDATE_GROUP;
    }

    /**
     * Run BEFORE Hytale's LegacyEquipment system so UpdateItems arrives
     * before the Equipment packet that references the item IDs.
     */
    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return Set.of(
            new SystemDependency<>(Order.BEFORE, LegacyEntityTrackerSystems.LegacyEquipment.class)
        );
    }

    @Override
    public void tick(
            float dt,
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        // Only process Player entities (not NPCs/mobs).
        // Use instanceof directly — avoids unsafe cast to LivingEntity.
        if (!(EntityUtils.getEntity(index, archetypeChunk) instanceof Player player)) {
            return;
        }

        EntityTrackerSystems.Visible visible = archetypeChunk.getComponent(index, visibleType);
        if (visible == null) {
            return;
        }

        PlayerRef observedPlayerRef = archetypeChunk.getComponent(index, playerRefType);
        if (observedPlayerRef == null) {
            return;
        }
        UUID observedId = observedPlayerRef.getUuid();

        // Collect currently equipped RPG gear
        List<EquippedGear> equippedGear = collectEquippedRpgGear(player);
        Set<String> currentIds = new HashSet<>(equippedGear.size());
        for (EquippedGear gear : equippedGear) {
            currentIds.add(gear.itemId);
        }

        // Detect equipment change since last tick
        Set<String> previousIds = lastKnownGear.put(observedId, currentIds);
        boolean gearChanged = previousIds == null || !previousIds.equals(currentIds);

        // Determine which viewers need the definitions
        Map<Ref<EntityStore>, EntityTrackerSystems.EntityViewer> targetViewers;
        if (gearChanged && !visible.visibleTo.isEmpty()) {
            // Equipment changed — broadcast to ALL current viewers
            targetViewers = visible.visibleTo;
        } else if (!visible.newlyVisibleTo.isEmpty()) {
            // New viewers appeared — send only to them
            targetViewers = visible.newlyVisibleTo;
        } else {
            // Nothing to do
            return;
        }

        if (equippedGear.isEmpty() || targetViewers.isEmpty()) {
            return;
        }

        // Build definitions once, reuse for each viewer
        Map<String, ItemBase> definitions = buildDefinitions(equippedGear);
        if (definitions.isEmpty()) {
            return;
        }

        int viewersSynced = 0;
        for (Map.Entry<Ref<EntityStore>, EntityTrackerSystems.EntityViewer> entry : targetViewers.entrySet()) {
            Ref<EntityStore> viewerRef = entry.getKey();
            if (!viewerRef.isValid()) continue;

            // Get the viewer's PlayerRef (skip non-player viewers)
            PlayerRef viewerPlayerRef = store.getComponent(viewerRef, playerRefType);
            if (viewerPlayerRef == null) continue;

            UUID viewerId = viewerPlayerRef.getUuid();
            // Don't send to the observed player themselves (they already have it)
            if (viewerId.equals(observedId)) continue;

            // Per-viewer dedup
            Set<String> alreadySent = viewerSentCache.computeIfAbsent(
                    viewerId, k -> ConcurrentHashMap.newKeySet());

            // Filter to only items this viewer hasn't received (or all if gear changed)
            Map<String, ItemBase> toSend;
            List<EquippedGear> gearToSync;
            if (gearChanged) {
                toSend = definitions;
                gearToSync = equippedGear;
                alreadySent.addAll(definitions.keySet());
            } else {
                toSend = new HashMap<>();
                gearToSync = new ArrayList<>();
                for (EquippedGear equipped : equippedGear) {
                    ItemBase def = definitions.get(equipped.itemId);
                    if (def != null && alreadySent.add(equipped.itemId)) {
                        toSend.put(equipped.itemId, def);
                        gearToSync.add(equipped);
                    }
                }
                if (toSend.isEmpty()) continue;
            }

            // Send translations first (so keys exist when definition arrives)
            registerTranslationsForViewer(viewerPlayerRef, gearToSync);
            // Then send item definitions
            sendDefinitions(viewerPlayerRef, toSend);
            viewersSynced++;
        }

        if (viewersSynced > 0) {
            LOGGER.atFine().log("Broadcast %d gear definition(s) for %s to %d viewer(s)",
                    definitions.size(), observedId.toString().substring(0, 8), viewersSynced);
        }
    }

    // =========================================================================
    // GEAR COLLECTION
    // =========================================================================

    /**
     * Collects all RPG gear items from a player's equipment slots
     * (armor, active hotbar, active utility).
     */
    private List<EquippedGear> collectEquippedRpgGear(@Nonnull Player player) {
        @SuppressWarnings("deprecation")
        Inventory inventory = player.getInventory();
        if (inventory == null) return List.of();

        List<EquippedGear> result = new ArrayList<>(6);

        // Armor slots
        ItemContainer armor = inventory.getArmor();
        if (armor != null) {
            for (short i = 0; i < armor.getCapacity(); i++) {
                ItemStack item = armor.getItemStack(i);
                addIfRpgGear(item, result);
            }
        }

        // Active hotbar item (right hand)
        ItemStack handItem = inventory.getItemInHand();
        addIfRpgGear(handItem, result);

        // Active utility item (left hand)
        ItemStack utilityItem = inventory.getUtilityItem();
        addIfRpgGear(utilityItem, result);

        return result;
    }

    private void addIfRpgGear(@Nullable ItemStack item, @Nonnull List<EquippedGear> result) {
        if (item == null || item.isEmpty()) return;
        Optional<GearData> gearOpt = GearUtils.readGearData(item);
        if (gearOpt.isPresent() && gearOpt.get().hasInstanceId()) {
            GearData gear = gearOpt.get();
            result.add(new EquippedGear(gear.getItemId(), item, gear));
        }
    }

    // =========================================================================
    // DEFINITION BUILDING
    // =========================================================================

    private Map<String, ItemBase> buildDefinitions(@Nonnull List<EquippedGear> gear) {
        Map<String, ItemBase> defs = new HashMap<>(gear.size());
        for (EquippedGear equipped : gear) {
            ItemBase def = definitionBuilder.build(equipped.itemStack, equipped.gearData);
            if (def != null) {
                defs.put(equipped.itemId, def);
            }
        }
        return defs;
    }

    // =========================================================================
    // PACKET SENDING
    // =========================================================================

    /**
     * Registers translations for gear items with a viewer player.
     *
     * <p>The viewer doesn't need stat-accurate tooltips — they just need
     * the translation keys to exist so the ItemBase definition resolves
     * without errors. Uses null playerId for generic (non-stat-specific) text.
     */
    private void registerTranslationsForViewer(
            @Nonnull PlayerRef viewerRef,
            @Nonnull List<EquippedGear> gear) {
        for (EquippedGear equipped : gear) {
            String compactId = equipped.gearData.instanceId().toCompactString();

            ItemDefinitionBuilder.TranslationContent tc =
                    definitionBuilder.buildTranslationContent(
                            equipped.itemStack, equipped.gearData, null);
            if (tc != null) {
                translationService.registerTranslations(
                        viewerRef, compactId, tc.name(), tc.description());
            }
        }
    }

    private void sendDefinitions(
            @Nonnull PlayerRef viewerRef,
            @Nonnull Map<String, ItemBase> definitions) {
        if (definitions.isEmpty()) return;

        UpdateItems packet = new UpdateItems();
        packet.type = UpdateType.AddOrUpdate;
        packet.items = definitions;
        packet.updateModels = false;
        packet.updateIcons = false;

        try {
            viewerRef.getPacketHandler().writeNoCache(packet);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log(
                    "Failed to send armor definitions to viewer %s",
                    viewerRef.getUuid().toString().substring(0, 8));
        }
    }

    // =========================================================================
    // JOIN SYNC (called from event handler, not ECS tick)
    // =========================================================================

    /**
     * Syncs RPG gear definitions between the joining player and all other
     * players in the same world.
     *
     * <p>Handles the {@code sendPlayerSelf} timing gap: when a player first
     * appears to other clients, Hytale sends the Equipment packet immediately
     * (before the ECS tick where this system runs). This method is called from
     * the join event handler to ensure definitions arrive first.
     *
     * <p>Two-way sync:
     * <ol>
     *   <li>Send the joining player's RPG gear to all other players</li>
     *   <li>Send all other players' RPG gear to the joining player</li>
     * </ol>
     *
     * @param joiningPlayer The player who just joined
     * @param store The entity store
     */
    public void syncOnPlayerJoin(
            @Nonnull PlayerRef joiningPlayer,
            @Nonnull Player joiningEntity,
            @Nonnull World world) {

        UUID joiningId = joiningPlayer.getUuid();
        Store<EntityStore> store = world.getEntityStore().getStore();

        // Collect joining player's equipped RPG gear
        List<EquippedGear> joiningGear = collectEquippedRpgGear(joiningEntity);
        Map<String, ItemBase> joiningDefs = buildDefinitions(joiningGear);

        // Find all other players in the world
        List<PlayerRef> otherPlayers = new ArrayList<>();
        for (PlayerRef ref : world.getPlayerRefs()) {
            if (!ref.getUuid().equals(joiningId)) {
                otherPlayers.add(ref);
            }
        }

        if (otherPlayers.isEmpty()) {
            return;
        }

        int totalSynced = 0;

        // Direction 1: Send joining player's gear TO each other player
        if (!joiningDefs.isEmpty()) {
            for (PlayerRef otherPlayer : otherPlayers) {
                registerTranslationsForViewer(otherPlayer, joiningGear);
                sendDefinitions(otherPlayer, joiningDefs);
                totalSynced++;
            }
        }

        // Direction 2: Send each other player's gear TO the joining player
        for (PlayerRef otherPlayer : otherPlayers) {
            Ref<EntityStore> otherRef = otherPlayer.getReference();
            if (otherRef == null || !otherRef.isValid()) continue;

            com.hypixel.hytale.server.core.entity.Entity otherEntity =
                    EntityUtils.getEntity(otherRef, store);
            if (!(otherEntity instanceof Player otherPlayerEntity)) continue;

            List<EquippedGear> otherGear = collectEquippedRpgGear(otherPlayerEntity);
            if (otherGear.isEmpty()) continue;

            Map<String, ItemBase> otherDefs = buildDefinitions(otherGear);
            if (otherDefs.isEmpty()) continue;

            registerTranslationsForViewer(joiningPlayer, otherGear);
            sendDefinitions(joiningPlayer, otherDefs);
            totalSynced++;
        }

        if (totalSynced > 0) {
            LOGGER.atInfo().log("Join sync: exchanged gear definitions for %s with %d other player(s)",
                    joiningId.toString().substring(0, 8), otherPlayers.size());
        }
    }

    // =========================================================================
    // LIFECYCLE
    // =========================================================================

    /**
     * Clean up tracking state for a disconnecting player.
     * Call from GearManager shutdown or disconnect handler.
     */
    public void onPlayerDisconnect(@Nonnull UUID playerId) {
        lastKnownGear.remove(playerId);
        viewerSentCache.remove(playerId);
    }

    /**
     * Clears all tracking state. Called on plugin shutdown.
     */
    public void shutdown() {
        lastKnownGear.clear();
        viewerSentCache.clear();
    }

    // =========================================================================
    // INNER TYPES
    // =========================================================================

    private record EquippedGear(
            @Nonnull String itemId,
            @Nonnull ItemStack itemStack,
            @Nonnull GearData gearData) {}
}
