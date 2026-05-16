package io.github.larsonix.trailoforbis.listeners;

import com.hypixel.hytale.builtin.instances.InstancesPlugin;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import io.github.larsonix.trailoforbis.util.MessageColors;
import java.util.logging.Level;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.api.ServiceRegistry;
import io.github.larsonix.trailoforbis.api.services.AttributeService;
import io.github.larsonix.trailoforbis.api.services.ConfigService;
import io.github.larsonix.trailoforbis.api.services.SkillTreeMapService;
import io.github.larsonix.trailoforbis.api.services.SkillTreeService;
import io.github.larsonix.trailoforbis.api.services.UIService;
import io.github.larsonix.trailoforbis.gear.GearManager;
import io.github.larsonix.trailoforbis.gear.GearService;
import io.github.larsonix.trailoforbis.gear.item.CustomItemData;
import io.github.larsonix.trailoforbis.gear.item.CustomItemInstanceId;
import io.github.larsonix.trailoforbis.gear.item.CustomItemSyncService;
import io.github.larsonix.trailoforbis.gear.item.ItemSyncService;
import io.github.larsonix.trailoforbis.gear.util.GearUtils;
import io.github.larsonix.trailoforbis.gems.GemManager;
import io.github.larsonix.trailoforbis.gems.item.GemItemData;
import io.github.larsonix.trailoforbis.gems.model.GemData;
import io.github.larsonix.trailoforbis.gems.model.GemDefinition;
import io.github.larsonix.trailoforbis.gems.util.GemUtils;
import io.github.larsonix.trailoforbis.maps.items.RealmMapUtils;
import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.database.models.PlayerData;
import io.github.larsonix.trailoforbis.database.repository.PlayerDataRepository;
import io.github.larsonix.trailoforbis.leveling.api.LevelingService;
import io.github.larsonix.trailoforbis.leveling.core.LevelingManager;
import io.github.larsonix.trailoforbis.systems.StatsApplicationSystem;
import io.github.larsonix.trailoforbis.systems.VanillaStatCache;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.modules.interaction.Interactions;
import io.github.larsonix.trailoforbis.skilltree.SkillTreeManager;
import io.github.larsonix.trailoforbis.sanctum.SkillSanctumManager;
import io.github.larsonix.trailoforbis.sanctum.interactions.SkillNodeInteraction;
import io.github.larsonix.trailoforbis.maps.RealmsManager;
import io.github.larsonix.trailoforbis.maps.instance.RealmPortalManager;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Handles player join and quit events for RPG data management.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Load or create player data on join</li>
 *   <li>Recalculate stats (ComputedStats is transient)</li>
 *   <li>Save player data asynchronously on quit</li>
 * </ul>
 */
public class PlayerJoinListener {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /**
     * Tracks the active session for each player by storing the PlayerRef from
     * their most recent PlayerConnectEvent.
     *
     * <p>During duplicate logins, Hytale fires multiple PlayerDisconnectEvents
     * for the old connection (kick + delayed QUIC stream close up to 25+ seconds
     * later). By comparing the event's PlayerRef with the stored one using object
     * identity, we detect stale disconnects and skip destructive cleanup that
     * would corrupt the active session's data.
     */
    private static final Map<UUID, PlayerRef> activeSessions = new ConcurrentHashMap<>();

    /**
     * Prevents double-processing of disconnect events from the same session.
     *
     * <p>During duplicate login kicks, Hytale fires PlayerDisconnectEvent TWICE
     * for the old session (kick + "Player removed from world") BEFORE the new
     * session's PlayerConnectEvent stores its ref in activeSessions. The existing
     * stale check (activeRef != eventPlayerRef) can't catch these because both
     * events use the same PlayerRef from the old session, and activeSessions is
     * empty after the first disconnect removes it.
     *
     * <p>This set tracks UUIDs that have already had their disconnect processed.
     * The first disconnect adds the UUID; the second is rejected. Cleared when
     * the player reconnects (onPlayerConnect).
     */
    private static final Set<UUID> processedDisconnects = ConcurrentHashMap.newKeySet();

    /**
     * Tracks players who just connected (PlayerConnectEvent). Consumed by the first
     * PlayerReadyEvent to detect login into an instance world (reconnect after disconnect).
     * Normal gameplay world transitions don't set this flag.
     */
    private static final Set<UUID> freshlyConnected = ConcurrentHashMap.newKeySet();

    /** Tracks players who have already had item integrity check this session (prevents re-run on world transitions). */
    private static final Set<UUID> migratedThisSession = ConcurrentHashMap.newKeySet();

    /**
     * Tracks UUIDs currently being processed in onPlayerReady to prevent
     * equipment change events from triggering stat recalculation before
     * player data and ComputedStats are fully initialized.
     *
     * <p>Hytale syncs equipment on world entry, firing LivingEntityInventoryChangeEvent
     * before the world.execute() callback in onPlayerReady has created ComputedStats.
     * EquipmentChangeListener checks this set and silently skips during join.
     */
    private static final Set<UUID> joiningPlayers = ConcurrentHashMap.newKeySet();

    /**
     * Tracks which world each player was last fully initialized in.
     *
     * <p>Hytale sends TWO ClientReady packets during instance world transitions.
     * Each triggers PlayerReadyEvent, which would run the full initialization twice.
     * On remote connections, the second ClientReady can arrive 30+ seconds later,
     * causing stat/item packets to arrive while the client is re-processing
     * OnWorldJoined → NullReferenceException crash.
     *
     * <p>By tracking the last-initialized world, we skip the redundant second init.
     * Cleared on DrainPlayerFromWorldEvent (so the next world gets full init) and
     * on disconnect.
     */
    private static final Map<UUID, String> lastInitializedWorld = new ConcurrentHashMap<>();

    /**
     * Checks if a player is currently in the process of joining.
     *
     * <p>Used by {@link EquipmentChangeListener} to skip equipment change events
     * that fire before player data is fully initialized.
     *
     * @param playerId The player's UUID
     * @return true if the player is mid-join
     */
    public static boolean isJoining(UUID playerId) {
        return joiningPlayers.contains(playerId);
    }

    /**
     * Checks if a PlayerDisconnectEvent belongs to a stale session.
     *
     * <p>During duplicate logins, Hytale fires PlayerDisconnectEvent multiple times
     * for the old connection: once on kick, and again when the QUIC stream closes
     * (25+ seconds later). The delayed event would destroy the new session's cached
     * data (level, attributes, gear sync) if not detected.
     *
     * <p>This method compares the event's PlayerRef with the most recent
     * PlayerConnectEvent's PlayerRef using object identity. Different objects
     * mean different sessions — the event belongs to a stale session.
     *
     * <p>Other disconnect handlers (RealmExitListener, AnimationSpeedSyncManager)
     * should call this at the top of their handlers to skip stale cleanups.
     *
     * @param event The disconnect event to check
     * @return true if the event belongs to a stale session and should be ignored
     */
    public static boolean isStaleDisconnect(@Nonnull PlayerDisconnectEvent event) {
        PlayerRef eventRef = event.getPlayerRef();
        if (eventRef == null) return false;
        UUID uuid = eventRef.getUuid();

        // Check 1: Different session (delayed QUIC close from old connection)
        PlayerRef activeRef = activeSessions.get(uuid);
        if (activeRef != null && activeRef != eventRef) return true;

        // Check 2: Same session but already processed (double disconnect from same kick).
        // During duplicate login kicks, Hytale fires two disconnects for the old session
        // before the new session's PlayerConnectEvent runs. The first disconnect clears
        // activeSessions, so the activeRef check above misses the second disconnect.
        if (processedDisconnects.contains(uuid)) return true;

        return false;
    }

    /**
     * Clears the per-world initialization dedup tracker for a player.
     * Called from DrainPlayerFromWorldEvent (so the next world gets full init)
     * and from onPlayerDisconnect.
     */
    public static void clearLastInitializedWorld(UUID playerId) {
        lastInitializedWorld.remove(playerId);
    }

    /**
     * Handles early player connect event - registers items BEFORE inventory is sent.
     *
     * <p>This method runs BEFORE the first tick, which means it runs BEFORE
     * Hytale's PlayerSendInventorySystem sends the inventory to the client.
     * This is critical because:
     * <ol>
     *   <li>Client receives inventory with custom item IDs (rpg_gear_xxx)</li>
     *   <li>If items aren't registered, client shows "?" (unknown item)</li>
     *   <li>By registering here, items are ready before inventory is sent</li>
     * </ol>
     *
     * <p>Event order: PlayerConnectEvent → [first tick] → PlayerReadyEvent
     *
     * @param event The player connect event
     */
    public static void onPlayerConnect(PlayerConnectEvent event) {
        PlayerRef playerRef = event.getPlayerRef();
        UUID uuid = playerRef.getUuid();
        Holder<EntityStore> holder = event.getHolder();

        // Register this as the active session. Any future disconnect event with a
        // different PlayerRef will be detected as stale and skipped.
        activeSessions.put(uuid, playerRef);

        // Allow this session's future disconnect to be processed (clear old session's flag)
        processedDisconnects.remove(uuid);

        // Clear any stale joining guard (shouldn't happen, but safety)
        joiningPlayers.remove(uuid);

        // Mark as freshly connected — consumed by first PlayerReadyEvent to detect
        // login into an orphaned instance world (sanctum/realm after disconnect)
        freshlyConnected.add(uuid);

        // Get Player component to access inventory
        Player player = holder.getComponent(Player.getComponentType());
        if (player == null) {
            // This is expected - player not in store yet at PlayerConnectEvent time
            // RPGItemPreSyncSystem will handle sync in onEntityAdded instead
            LOGGER.atFine().log("[PlayerConnectEvent] Player component NULL for %s (expected - RPGItemPreSyncSystem will handle)", uuid);
            return;
        }

        if (player.getInventory() == null) {
            LOGGER.atFine().log("[PlayerConnectEvent] Inventory NULL for %s", uuid);
            return;
        }

        // Item sync is handled by RPGItemPreSyncSystem (ECS, before sendInventory).
        // No backup path needed — the coordinator handles post-join flush via onPlayerReady.
        LOGGER.atFine().log("[PlayerConnectEvent] Player %s connected (item sync via RPGItemPreSyncSystem)", uuid);
    }

    /**
     * Handles player ready event - loads or creates player data.
     *
     * @param event The player ready event
     */
    public static void onPlayerReady(PlayerReadyEvent event) {
        Optional<AttributeService> attributeServiceOpt = ServiceRegistry.get(AttributeService.class);
        if (attributeServiceOpt.isEmpty()) {
            return; // Plugin not fully initialized
        }

        AttributeService attributeService = attributeServiceOpt.get();

        Player player = event.getPlayer();
        World world = player.getWorld();
        if (world == null) {
            return;
        }

        // Detect reconnect into an instance world (sanctum/realm).
        // PlayerConnectEvent marks the player in freshlyConnected. If their FIRST
        // PlayerReadyEvent is in an instance world, they reconnected after disconnect/restart
        // and we need to exit them before they fall into the void.
        // Normal gameplay teleports (entering sanctum/realm) don't set freshlyConnected
        // because the player is already past their initial PlayerReadyEvent.
        String worldName = world.getName();
        UUID connectCheckId = player.getUuid();
        if (freshlyConnected.remove(connectCheckId)
                && worldName != null && worldName.startsWith("instance-")) {
            world.execute(() -> {
                Ref<EntityStore> ref = player.getReference();
                if (ref == null || !ref.isValid()) return;
                Store<EntityStore> store = ref.getStore();
                PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
                if (pr != null) {
                    LOGGER.at(Level.INFO).log("Player %s logged in to instance world '%s' - exiting to return point",
                        pr.getUsername(), worldName);
                    InstancesPlugin.exitInstance(ref, store);
                }
            });
            return; // Skip RPG init — will run when they arrive in the return world
        }

        world.execute(() -> {
        // Re-check entity validity on world thread (player may have disconnected)
        Ref<EntityStore> playerEntityRef = player.getReference();
        if (playerEntityRef == null || !playerEntityRef.isValid()) {
            return;
        }
        Store<EntityStore> playerStore = playerEntityRef.getStore();
        PlayerRef playerRef = playerStore.getComponent(playerEntityRef, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }
        UUID uuid = playerRef.getUuid();
        String username = playerRef.getUsername();

        // Deduplicate: skip if already initialized in this world.
        // Hytale fires two ClientReady packets per world transition. On remote
        // connections the second can arrive 30+ seconds later, causing stat/item
        // packets to crash the client during its second OnWorldJoined processing.
        String worldId = world.getName();
        if (worldId != null && worldId.equals(lastInitializedWorld.get(uuid))) {
            LOGGER.at(Level.INFO).log("Skipping redundant onPlayerReady for %s (already initialized in %s)",
                username, worldId);
            return;
        }
        lastInitializedWorld.put(uuid, worldId);

        // Mark player as joining to suppress equipment change events during initialization.
        joiningPlayers.add(uuid);

        try {
        PlayerDataRepository repo = attributeService.getPlayerDataRepository();

        // Invalidate any pending async saves from a previous session.
        // This prevents stale data from overwriting newer data if the player
        // disconnected and reconnected quickly (within the 30-second save timeout).
        repo.incrementSaveVersion(uuid);

        // Load or create player data
        Optional<PlayerData> existing = repo.get(uuid);
        boolean isNewPlayer = existing.isEmpty();
        if (isNewPlayer) {
            // Get starting points from config
            int startingPoints = ServiceRegistry.get(ConfigService.class)
                .map(cfg -> cfg.getRPGConfig().getAttributes().getStartingPoints())
                .orElse(0);

            try {
                repo.create(uuid, username, startingPoints);
                LOGGER.at(Level.INFO).log("Created new player data for %s with %d starting points", username, startingPoints);
            } catch (Exception e) {
                LOGGER.at(Level.SEVERE).withCause(e).log("CRITICAL: Failed to create RPG profile for %s", username);
                return;
            }
        } else {
            LOGGER.at(Level.INFO).log("Loaded player data for %s", username);
        }

        // Initialize guide system for this player
        TrailOfOrbis rpgForGuide = TrailOfOrbis.getInstanceOrNull();
        if (rpgForGuide != null && rpgForGuide.getGuideManager() != null) {
            rpgForGuide.getGuideManager().onPlayerJoin(uuid);
            if (isNewPlayer) {
                rpgForGuide.getGuideManager().tryShow(uuid, io.github.larsonix.trailoforbis.guide.GuideMilestone.WELCOME);
            }
            // Start proximity polling for Ancient Gateway guide (fires when player walks near a portal)
            rpgForGuide.getGuideManager().startGatewayProximityCheck(uuid);
        }

        // Load player leveling data
        ServiceRegistry.get(LevelingService.class)
            .filter(svc -> svc instanceof LevelingManager)
            .map(svc -> (LevelingManager) svc)
            .ifPresent(mgr -> mgr.loadPlayer(uuid));

        // Item integrity check: only once per connection (not on world transitions)
        if (migratedThisSession.add(uuid)) {
            TrailOfOrbis rpgForMigration = TrailOfOrbis.getInstanceOrNull();
            if (rpgForMigration != null && rpgForMigration.getGearManager() != null
                    && rpgForMigration.getGearManager().isInitialized()) {
                var integrityResult = rpgForMigration.getGearManager().getItemMigrationService()
                        .migratePlayerGear(player, uuid);
                if (integrityResult.total() > 0) {
                    playerRef.sendMessage(Message.empty()
                            .insert(Message.raw("[Trail of Orbis] ").color(MessageColors.GRAY))
                            .insert(Message.raw("Updated ").color(MessageColors.WHITE))
                            .insert(Message.raw(String.valueOf(integrityResult.total())).color(MessageColors.WARNING))
                            .insert(Message.raw(" item(s) to the latest version.").color(MessageColors.WHITE)));
                }
            }

            // Skill tree migration: auto-reset if allocated nodes no longer exist in current tree
            if (rpgForMigration != null && rpgForMigration.getSkillTreeManager() != null) {
                int orphaned = rpgForMigration.getSkillTreeManager().migrateOrphanedNodes(uuid);
                if (orphaned > 0) {
                    playerRef.sendMessage(Message.empty()
                            .insert(Message.raw("[Trail of Orbis] ").color(MessageColors.GRAY))
                            .insert(Message.raw("Skill tree has been reworked! Your ").color(MessageColors.WHITE))
                            .insert(Message.raw(String.valueOf(orphaned)).color(MessageColors.WARNING))
                            .insert(Message.raw(" allocated node(s) were reset and all points refunded.").color(MessageColors.WHITE)));
                }
            }

            // XP curve migration notification
            ServiceRegistry.get(LevelingService.class)
                .filter(svc -> svc instanceof LevelingManager)
                .map(svc -> (LevelingManager) svc)
                .ifPresent(mgr -> {
                    if (mgr.wasXpAdjusted(uuid)) {
                        int level = mgr.getLevel(uuid);
                        playerRef.sendMessage(Message.empty()
                            .insert(Message.raw("[Trail of Orbis] ").color(MessageColors.GRAY))
                            .insert(Message.raw("Your level has been preserved through a balance update (Level ").color(MessageColors.WHITE))
                            .insert(Message.raw(String.valueOf(level)).color(MessageColors.WARNING))
                            .insert(Message.raw(").").color(MessageColors.WHITE)));
                    }
                });
        }

        // Cache vanilla stats and apply ECS operations
        Ref<EntityStore> entityRef = playerRef.getReference();
        if (entityRef != null && entityRef.isValid()) {
            Store<EntityStore> store = entityRef.getStore();
            // Only cache on true first login - not on world transitions.
            // The cache is cleared in onPlayerDisconnect, so it will be empty for new sessions.
            if (!VanillaStatCache.hasCached(uuid)) {
                VanillaStatCache.cacheOnJoin(uuid, store, entityRef);
            }

            // Recalculate ComputedStats — pure computation, no packets, safe to run immediately.
            // ComputedStats must be ready before the coordinator's 200ms item flush (tooltip colors).
            attributeService.recalculateStats(uuid);

            // Detect world transition: the coordinator suppresses the player during
            // DrainPlayerFromWorldEvent. If suppressed, we're entering a new world.
            boolean isWorldTransition = ServiceRegistry.get(GearService.class)
                .filter(svc -> svc instanceof GearManager)
                .map(svc -> (GearManager) svc)
                .map(mgr -> mgr.getSyncCoordinator())
                .map(coord -> coord.isPlayerSuppressed(uuid))
                .orElse(false);

            if (isWorldTransition) {
                // DEFER stat application for world transitions.
                // During transitions, the client needs ~200ms after OnWorldJoined to
                // initialize entity renderers and stat bar UI components. If EntityStatUpdate
                // packets arrive during this window, the client's rendering code accesses
                // a null stat bar component → NullReferenceException at 0x421beb.
                //
                // applyEcsStats() modifies EntityStatMap (removeModifier + putModifier +
                // setStatValue × 3 resource stats), which the entity tracker sends as
                // EntityStatUpdate packets on the next world tick. By deferring 300ms,
                // these packets arrive AFTER the client is fully initialized.
                //
                // recalculateStats() already ran above — ComputedStats is correct for
                // the coordinator's 200ms tooltip flush. Only the ECS write is deferred.
                final UUID deferredUuid = uuid;
                final World deferredWorld = world;
                CompletableFuture.delayedExecutor(300, TimeUnit.MILLISECONDS).execute(() -> {
                    deferredWorld.execute(() -> {
                        // Fresh refs — player may have disconnected during the 300ms delay
                        PlayerRef freshRef = com.hypixel.hytale.server.core.universe.Universe.get().getPlayer(deferredUuid);
                        if (freshRef == null) return;
                        Ref<EntityStore> freshEntityRef = freshRef.getReference();
                        if (freshEntityRef == null || !freshEntityRef.isValid()) return;
                        Store<EntityStore> freshStore = freshEntityRef.getStore();

                        applyEcsStats(freshRef, deferredUuid, freshStore, repo);
                        LOGGER.atInfo().log("Deferred stats applied for %s (world transition, 300ms delay)",
                            freshRef.getUsername());
                    });
                });
            } else {
                // First connect — no old world to tear down, safe to apply immediately.
                applyEcsStats(playerRef, uuid, store, repo);
                LOGGER.at(Level.INFO).log("Stats applied for %s", username);
            }

            initializeGameModeBypass(uuid, player);

            // Notify coordinator: stats are applied, schedule post-join flush.
            // This replaces the old initializeItemSync which did a full resync.
            // The coordinator's flush only sends items whose tooltip actually changed.
            ServiceRegistry.get(GearService.class)
                .filter(svc -> svc instanceof GearManager)
                .map(svc -> (GearManager) svc)
                .ifPresent(mgr -> {
                    var coordinator = mgr.getSyncCoordinator();
                    if (coordinator != null) {
                        coordinator.onPlayerReady(uuid, playerRef, player, world);
                    }
                });

            // Broadcast RPG gear definitions to/from other players.
            // This ensures armor renders correctly on remote clients by sending
            // UpdateItems definitions before the Equipment packet arrives.
            ServiceRegistry.get(GearService.class)
                .filter(svc -> svc instanceof GearManager)
                .map(svc -> (GearManager) svc)
                .ifPresent(mgr -> {
                    var broadcast = mgr.getEquipmentBroadcastSystem();
                    if (broadcast != null) {
                        broadcast.syncOnPlayerJoin(playerRef, player, world);
                    }
                });

            cleanupSanctumInteractions(uuid, store, entityRef);
            tryPlaceSpawnGateways(world);

            // Persistent HUD restoration (XP bar, energy shield) is handled by
            // HudLifecycleManager's LATE-priority PlayerReadyEvent handler.
            // It runs after this EARLY handler completes initialization, uses fresh
            // refs, and includes a 1-second safety-net timer for reliability.

            TrailOfOrbis rpgInstance = TrailOfOrbis.getInstanceOrNull();

            // Initialize loot filter state for this player
            if (rpgInstance != null && rpgInstance.getLootFilterManager() != null) {
                rpgInstance.getLootFilterManager().onPlayerJoin(uuid);
            }

            if (rpgInstance != null && rpgInstance.getStoneTooltipSyncService() != null) {
                rpgInstance.getStoneTooltipSyncService().syncToPlayer(playerRef);
            }

            // Set PartyPro HUD level display (slot 1, replaces distance)
            ServiceRegistry.get(io.github.larsonix.trailoforbis.compat.party.PartyIntegrationManager.class)
                .ifPresent(party -> {
                    ServiceRegistry.get(LevelingService.class).ifPresent(leveling -> {
                        party.updateHudLevel(uuid, leveling.getLevel(uuid));
                    });
                });

            // Crafting preview tooltips are sent by ItemSyncCoordinator during the
            // join flush — right before RPG items, so RPG tooltips always win.

            // Install gem slot filters on player's inventory
            ServiceRegistry.get(GemManager.class).ifPresent(gm -> {
                if (player.getInventory() != null) {
                    gm.installGemFilters(player.getInventory(), playerRef);
                }
            });

            // Install armor slot validation filters (armor type + RPG requirements)
            if (rpgInstance != null && rpgInstance.getGearManager() != null
                    && rpgInstance.getGearManager().getEquipmentListener() != null
                    && player.getInventory() != null) {
                rpgInstance.getGearManager().getEquipmentListener()
                    .setupArmorValidation(player.getInventory(), playerRef);
            }

            // Combat text templates are applied on-demand during combat hits
            // (no pre-sync needed — avoids maxId race with asset pipeline)

            // Note: Vanilla gems/crystals are NOT hidden from creative — they're legitimate
            // decorative items. Only our custom _Light variants are hidden via Variant:true
            // in their asset JSON definitions.

            // DIAGNOSTIC: Verify hex asset registrations + test particle rendering
            if (io.github.larsonix.trailoforbis.compat.HexcodeCompat.isLoaded()) {
                LOGGER.atInfo().log("[HexAssetDiag] Running post-join hex asset verification for %s", username);
                io.github.larsonix.trailoforbis.compat.HexcodeCompat.diagCheckHexAsset("Hexstaff_Basic_Crude");
                // Bypass InventoryComponent.getItemInHand() — it delegates to Inventory.getItemInHand()
                // which returns tool items when _usingToolsItem is set.
                Player joinPlayer = store.getComponent(entityRef, Player.getComponentType());
                ItemStack mainHand = (joinPlayer != null && joinPlayer.getInventory() != null)
                    ? joinPlayer.getInventory().getActiveHotbarItem() : null;
                if (mainHand != null && !mainHand.isEmpty() && mainHand.getItem() != null) {
                    String mainHandId = mainHand.getItem().getId();
                    LOGGER.atInfo().log("[HexAssetDiag] Main hand item: '%s'", mainHandId);
                    if (mainHandId.startsWith("rpg_gear_")) {
                        io.github.larsonix.trailoforbis.compat.HexcodeCompat.diagCheckHexAsset(mainHandId);
                    }
                }

                // Particle test removed — confirmed client has particle systems loaded
            }

            // DIAGNOSTIC: Log item counts on join to detect inventory loss across transitions
            if (player.getInventory() != null) {
                int itemCount = io.github.larsonix.trailoforbis.gear.util.GearUtils
                    .collectAllInventoryItems(player.getInventory()).size();
                LOGGER.atInfo().log("[INVENTORY-DIAG] Player %s joined with %d total items", username, itemCount);
            }
        } else {
            LOGGER.at(Level.INFO).log("Warning: Entity not available for %s, caching will be deferred", username);
            attributeService.recalculateStats(uuid);
        }

            // Reactivate portals that were suppressed during sanctum exit.
            // Delayed by 3s after PlayerReadyEvent to ensure the client's chunk pipeline
            // has fully stabilized before allowing portal CollisionEnter interactions.
            schedulePortalUnsuppression(uuid, world);

        } catch (Exception e) {
            LOGGER.at(Level.SEVERE).withCause(e).log("Failed to initialize player data for %s", username);
        } finally {
            joiningPlayers.remove(uuid);
        }
        });
    }

    /**
     * Schedules reactivation of portals suppressed during sanctum exit.
     * Runs 3 seconds after PlayerReadyEvent on the world thread.
     */
    private static void schedulePortalUnsuppression(@Nonnull UUID playerId, @Nonnull World world) {
        TrailOfOrbis rpg = TrailOfOrbis.getInstanceOrNull();
        if (rpg == null || rpg.getRealmsManager() == null) {
            return;
        }

        RealmPortalManager portalManager = rpg.getRealmsManager().getPortalManager();
        if (!portalManager.hasSuppressedPortals(playerId)) {
            return;
        }

        CompletableFuture.delayedExecutor(3, TimeUnit.SECONDS)
                .execute(() -> world.execute(() -> portalManager.unsuppressPortals(world, playerId)));
    }

    /**
     * Applies computed stats to ECS components for the player.
     *
     * <p>This wires computed stats to actual gameplay systems:
     * <ul>
     *   <li>maxHealth/maxMana via EntityStatMap modifiers</li>
     *   <li>movementSpeedPercent via MovementSettings</li>
     * </ul>
     *
     * <p><b>IMPORTANT:</b> Uses applyAllStatsAndSync to ensure ComputedStats
     * reflects actual ECS HP, including bonuses from LevelingCore.
     *
     * @param playerRef The player reference
     * @param uuid The player's UUID
     * @param store The entity store
     * @param repo The player data repository
     */
    private static void applyEcsStats(
        PlayerRef playerRef,
        UUID uuid,
        Store<EntityStore> store,
        PlayerDataRepository repo
    ) {
        // Get entity reference
        Ref<EntityStore> entityRef = playerRef.getReference();
        if (entityRef == null || !entityRef.isValid()) {
            LOGGER.at(Level.SEVERE).log("Warning: Cannot apply ECS stats - entity not in world");
            return;
        }

        // Get player's computed stats
        Optional<PlayerData> dataOpt = repo.get(uuid);
        if (dataOpt.isEmpty()) {
            return;
        }

        ComputedStats stats = dataOpt.get().getComputedStats();
        if (stats == null) {
            return;
        }

        // Apply stats to ECS AND sync cache with actual ECS HP
        StatsApplicationSystem.applyAllStatsAndSync(playerRef, store, entityRef, stats, repo, uuid);
    }

    /**
     * Checks the player's initial game mode and adds a requirement bypass if Creative.
     *
     * <p>This handles the case where a player joins (or reconnects) already in Creative mode.
     * The {@code GameModeChangeSystem} handles subsequent mode switches during the session.
     *
     * @param uuid The player's UUID
     * @param player The player entity
     */
    private static void initializeGameModeBypass(UUID uuid, Player player) {
        try {
            if (player.getGameMode() == GameMode.Creative) {
                ServiceRegistry.get(GearService.class).ifPresent(svc -> {
                    svc.addRequirementBypass(uuid);
                    LOGGER.at(Level.INFO).log("Player %s joined in Creative mode - gear requirements bypassed", uuid);
                });
            }
        } catch (Exception e) {
            LOGGER.at(Level.FINE).withCause(e).log("Could not check game mode for %s", uuid);
        }
    }

    /**
     * Cleans up lingering Skill Sanctum Interactions component from player.
     *
     * <p><b>Root Cause:</b> When a player enters a Skill Sanctum, an {@link Interactions}
     * component is added to them with {@code InteractionType.Use → *AllocateSkillNode}.
     * This is necessary for F-key interaction with skill nodes in the sanctum.
     *
     * <p><b>The Bug:</b> When the player leaves the sanctum, the cleanup in
     * {@code SkillSanctumInstance.removePlayerInteractions()} fails because the
     * player's entity in the sanctum world is already invalid (player has been
     * drained/teleported). The Interactions component persists because:
     * <ol>
     *   <li>Hytale's {@code InteractionContext.getRootInteractionId()} checks the
     *       PLAYER's Interactions component FIRST, before the target block or held item</li>
     *   <li>When the player is in the overworld and presses F on a Portal_Device with
     *       a map, the stale *AllocateSkillNode interaction intercepts the Use interaction</li>
     *   <li>This causes map activation to fail silently (SkillNodeInteraction.firstRun()
     *       is called but finds no valid target)</li>
     * </ol>
     *
     * <p><b>The Fix:</b> On PlayerReadyEvent (when player is fully loaded in any world),
     * check if they have a sanctum Interactions component AND are NOT in a sanctum.
     * If so, remove the component to restore normal interaction behavior.
     *
     * @param uuid The player's UUID
     * @param store The entity store
     * @param entityRef The player's entity reference
     */
    private static void cleanupSanctumInteractions(
            UUID uuid,
            Store<EntityStore> store,
            Ref<EntityStore> entityRef) {

        // Check if player is currently in a sanctum - if so, they SHOULD have the component
        TrailOfOrbis rpg = TrailOfOrbis.getInstanceOrNull();
        if (rpg == null) {
            return;
        }

        SkillSanctumManager sanctumManager = rpg.getSkillSanctumManager();
        if (sanctumManager != null && sanctumManager.hasActiveSanctum(uuid)) {
            // Player is in a sanctum, don't remove the component
            return;
        }

        // Check if player has the sanctum Interactions component
        Interactions interactions = store.getComponent(entityRef, Interactions.getComponentType());
        if (interactions == null) {
            return; // No Interactions component at all
        }

        // Check if the Use interaction is the sanctum skill node interaction
        String useInteractionId = interactions.getInteractionId(InteractionType.Use);
        if (SkillNodeInteraction.DEFAULT_ID.equals(useInteractionId)) {
            // Player has the sanctum interaction but isn't in a sanctum - clean it up
            store.removeComponent(entityRef, Interactions.getComponentType());
            LOGGER.at(Level.INFO).log(
                "Cleaned up stale sanctum Interactions component from player %s " +
                "(was routing Use → %s)", uuid, useInteractionId);
        }
    }

    /**
     * Attempts to place spawn gateways in a world if not already placed.
     *
     * <p>Spawn gateways are a ring of Portal_Device blocks around the world spawn
     * that help new players discover the realm map system. This is idempotent -
     * gateways are only placed once per world (tracked in database).
     *
     * @param world The world to potentially place gateways in
     */
    private static void tryPlaceSpawnGateways(World world) {
        if (world == null) {
            return;
        }

        // Skip all instance worlds — gateways belong only in the overworld
        String worldName = world.getName();
        if (worldName != null && worldName.startsWith("instance-")) {
            return;
        }

        // Check if RealmsManager is initialized
        if (!RealmsManager.isInitialized()) {
            return;
        }

        // Trigger gateway placement (async, idempotent)
        RealmsManager.get().ensureSpawnGatewaysExist(world)
            .thenAccept(placed -> {
                if (placed) {
                    LOGGER.at(Level.INFO).log("Spawn gateways placed in world %s",
                        world.getWorldConfig().getUuid().toString().substring(0, 8));
                }
            })
            .exceptionally(ex -> {
                LOGGER.at(Level.WARNING).withCause(ex).log(
                    "Failed to place spawn gateways in world %s", world.getName());
                return null;
            });
    }

    /**
     * Handles player disconnect event - saves player data asynchronously.
     *
     * @param event The player disconnect event
     */
    public static void onPlayerDisconnect(PlayerDisconnectEvent event) {
        Optional<AttributeService> attributeServiceOpt = ServiceRegistry.get(AttributeService.class);
        Optional<UIService> uiServiceOpt = ServiceRegistry.get(UIService.class);

        // PlayerDisconnectEvent extends PlayerRefEvent, so we get PlayerRef from it
        PlayerRef eventPlayerRef = event.getPlayerRef();
        UUID uuid = eventPlayerRef.getUuid();

        // Guard 1: Prevent double-processing from the same session.
        // During duplicate login kicks, Hytale fires PlayerDisconnectEvent TWICE for the
        // old session (kick + "Player removed from world") BEFORE the new session's
        // PlayerConnectEvent stores its ref in activeSessions. The stale check below can't
        // catch these because both events use the same PlayerRef, and activeSessions is
        // empty after the first disconnect removes it.
        if (!processedDisconnects.add(uuid)) {
            LOGGER.atInfo().log("Skipping duplicate disconnect for %s (already processed this session)", uuid);
            return;
        }

        // Guard 2: Skip ALL cleanup if this disconnect belongs to a stale session
        // (e.g., delayed QUIC close from a duplicate login kick 25s ago).
        // During duplicate logins, onPlayerConnect stores the NEW session's PlayerRef in
        // activeSessions. If this event's PlayerRef is a different object, it belongs to
        // the old connection and must not destroy the active session's cached state.
        PlayerRef activeRef = activeSessions.get(uuid);
        if (activeRef != null && activeRef != eventPlayerRef) {
            LOGGER.atInfo().log("Skipping stale disconnect for %s (old session cleanup, active session exists)", uuid);
            return;
        }

        // Atomic removal: only remove if WE are the active session.
        // ConcurrentHashMap.remove(key, value) checks value identity before removing.
        // If a new onPlayerConnect raced between our get() and this remove(), the stored
        // PlayerRef has changed and this returns false — we skip cleanup.
        if (activeRef != null && !activeSessions.remove(uuid, eventPlayerRef)) {
            LOGGER.atInfo().log("Skipping stale disconnect for %s (session replaced during cleanup)", uuid);
            return;
        }

        // From here: this IS the active session disconnecting. Run full cleanup.

        // DIAGNOSTIC: Log item counts on disconnect to detect inventory loss
        try {
            Ref<EntityStore> diagRef = eventPlayerRef.getReference();
            if (diagRef != null && diagRef.isValid()) {
                Store<EntityStore> diagStore = diagRef.getStore();
                Player diagPlayer = diagStore.getComponent(diagRef, Player.getComponentType());
                if (diagPlayer != null && diagPlayer.getInventory() != null) {
                    int itemCount = io.github.larsonix.trailoforbis.gear.util.GearUtils
                        .collectAllInventoryItems(diagPlayer.getInventory()).size();
                    LOGGER.atInfo().log("[INVENTORY-DIAG] Player %s disconnecting with %d total items", uuid, itemCount);
                }
            }
        } catch (Exception diagEx) {
            LOGGER.atFine().log("[INVENTORY-DIAG] Could not count items for %s on disconnect: %s", uuid, diagEx.getMessage());
        }

        joiningPlayers.remove(uuid);
        lastInitializedWorld.remove(uuid);
        migratedThisSession.remove(uuid);

        // Clean up UI state
        uiServiceOpt.ifPresent(ui -> ui.onPlayerDisconnect(uuid));

        // Clean up guide system state
        TrailOfOrbis rpgForGuide = TrailOfOrbis.getInstanceOrNull();
        if (rpgForGuide != null && rpgForGuide.getGuideManager() != null) {
            rpgForGuide.getGuideManager().onPlayerDisconnect(uuid);
        }

        // Clean up Vuetale UI state (loot filter page)
        io.github.larsonix.trailoforbis.lootfilter.bridge.VuetaleIntegration.onPlayerDisconnect(uuid);

        // Clean up item sync state and requirement bypass
        ServiceRegistry.get(GearService.class)
            .ifPresent(svc -> {
                svc.removeRequirementBypass(uuid);
                if (svc instanceof GearManager mgr) {
                    // Clean up coordinator state (cancel pending flushes, clear dirty flags)
                    var coordinator = mgr.getSyncCoordinator();
                    if (coordinator != null) {
                        coordinator.onPlayerDisconnect(uuid);
                    }
                    ItemSyncService syncService = mgr.getItemSyncService();
                    if (syncService != null) {
                        syncService.onPlayerDisconnect(uuid);
                    }
                    // Clean up equipment broadcast tracking (viewer cache + gear state)
                    var broadcast = mgr.getEquipmentBroadcastSystem();
                    if (broadcast != null) {
                        broadcast.onPlayerDisconnect(uuid);
                    }
                    // Crafting preview has no per-player state to clean up
                    // (definitions are sent globally, not tracked per-player)

                    // Clean up pending craft conversions to prevent memory leak
                    var craftConversion = mgr.getCraftingConversionSystem();
                    if (craftConversion != null) {
                        craftConversion.onPlayerDisconnect(uuid);
                    }

                    // Clean up world drop sync cache to prevent memory leak
                    var worldSyncService = mgr.getItemWorldSyncService();
                    if (worldSyncService != null) {
                        worldSyncService.onPlayerDisconnect(uuid);
                    }

                    // Clean up custom item sync tracking to prevent memory leak
                    var customItemSync = mgr.getCustomItemSyncService();
                    if (customItemSync != null) {
                        customItemSync.onPlayerDisconnect(uuid);
                    }
                }
            });

        // Clean up skill tree map state
        ServiceRegistry.get(SkillTreeMapService.class)
            .ifPresent(mapService -> mapService.onPlayerDisconnect(uuid));

        // Clean up per-player locks in SkillTreeManager to prevent memory leak
        ServiceRegistry.get(SkillTreeService.class)
            .ifPresent(skillTreeService -> skillTreeService.cleanupPlayer(uuid));

        // Unload player leveling data (saves + evicts from cache)
        ServiceRegistry.get(LevelingService.class)
            .filter(svc -> svc instanceof LevelingManager)
            .map(svc -> (LevelingManager) svc)
            .ifPresent(mgr -> mgr.unloadPlayer(uuid));

        // Clean up regeneration system timestamp to prevent memory leak
        TrailOfOrbis rpg = TrailOfOrbis.getInstanceOrNull();
        if (rpg != null && rpg.getRegenerationSystem() != null) {
            rpg.getRegenerationSystem().cleanupPlayer(uuid);
        }

        // Clean up hotbar slot tracking to prevent memory leak
        if (rpg != null && rpg.getHotbarSlotTrackingSystem() != null) {
            rpg.getHotbarSlotTrackingSystem().onPlayerDisconnect(uuid);
        }

        // Clean up attack speed cooldown tracking
        if (rpg != null && rpg.getInteractionTimeShiftSystem() != null) {
            rpg.getInteractionTimeShiftSystem().onPlayerDisconnect(uuid);
        }

        // Clean up HUD toggle state (player always starts with HUDs visible on next join)
        if (rpg != null && rpg.getHudToggleService() != null) {
            rpg.getHudToggleService().onDisconnect(uuid);
        }

        // Clean up persistent HUDs (XP bar, energy shield) via lifecycle manager
        if (rpg != null && rpg.getHudLifecycleManager() != null) {
            rpg.getHudLifecycleManager().onPlayerDisconnect(uuid);
        }
        if (rpg != null && rpg.getEnergyShieldTracker() != null) {
            rpg.getEnergyShieldTracker().cleanupPlayer(uuid);
        }

        // Clear cached vanilla stats to prevent memory leak
        VanillaStatCache.clear(uuid);

        // Clean up death recap data to prevent memory leak
        if (rpg != null && rpg.getDeathRecapTracker() != null) {
            rpg.getDeathRecapTracker().onPlayerDisconnect(uuid);
        }

        // Clean up ailment state to prevent memory leak
        if (rpg != null && rpg.getAilmentTracker() != null) {
            rpg.getAilmentTracker().cleanup(uuid);
        }

        // Clean up ailment effects (Freeze slow)
        if (rpg != null && rpg.getAilmentEffectManager() != null) {
            rpg.getAilmentEffectManager().cleanup(uuid);
        }

        // Clean up combat effect registry state
        if (rpg != null && rpg.getCombatEffectRegistry() != null) {
            rpg.getCombatEffectRegistry().cleanup(uuid);
        }

        // Clean up combat trackers to prevent memory leaks
        if (rpg != null && rpg.getConsecutiveHitTracker() != null) {
            rpg.getConsecutiveHitTracker().cleanup(uuid);
        }
        if (rpg != null && rpg.getAilmentImmunityTracker() != null) {
            rpg.getAilmentImmunityTracker().cleanup(uuid);
        }

        // Clean up combat requirement notification cooldowns
        if (rpg != null && rpg.getCombatRequirementNotifier() != null) {
            rpg.getCombatRequirementNotifier().cleanupPlayer(uuid);
        }

        // Clean up conditional trigger system per-player state (effect trackers + node cache)
        if (rpg != null && rpg.getConditionalTriggerSystem() != null) {
            rpg.getConditionalTriggerSystem().removePlayer(uuid);
        }

        // Clean up container sync tracking to prevent memory leak
        if (rpg != null && rpg.getContainerSyncTickSystem() != null) {
            rpg.getContainerSyncTickSystem().cleanupPlayer(uuid);
        }

        // Clean up stone tooltip sync tracking
        if (rpg != null && rpg.getStoneTooltipSyncService() != null) {
            rpg.getStoneTooltipSyncService().onPlayerDisconnect(uuid);
        }

        // Clean up chat item link viewer cache
        if (rpg != null && rpg.getChatItemLinkHandler() != null) {
            rpg.getChatItemLinkHandler().onPlayerDisconnect(uuid);
        }

        // Clear PartyPro HUD custom text
        ServiceRegistry.get(io.github.larsonix.trailoforbis.compat.party.PartyIntegrationManager.class)
            .ifPresent(party -> party.clearHud(uuid));

        // Clean up combat text color dedup cache
        if (rpg != null && rpg.getCombatTextColorManager() != null) {
            rpg.getCombatTextColorManager().removePlayer(uuid);
        }

        // Clean up reskin cache to prevent memory leak
        ServiceRegistry.get(GearService.class)
            .filter(svc -> svc instanceof GearManager)
            .map(svc -> (GearManager) svc)
            .ifPresent(mgr -> {
                if (mgr.getReskinDataPreserver() != null) {
                    mgr.getReskinDataPreserver().onPlayerDisconnect(uuid);
                }
            });

        // Clean up Skill Sanctum instance if player was in one
        // Use teleport=false since player is disconnecting (entity ref is already invalid)
        if (rpg != null && rpg.getSkillSanctumManager() != null) {
            rpg.getSkillSanctumManager().closeSanctum(uuid, false);
            // Also clean up if this player was a visitor in someone else's sanctum
            rpg.getSkillSanctumManager().removeVisitorFromAnySanctum(uuid);
        }

        // Save and evict loot filter state
        if (rpg != null && rpg.getLootFilterManager() != null) {
            rpg.getLootFilterManager().onPlayerDisconnect(uuid);
        }

        // Clean up inventory detection tracker
        if (rpg != null && rpg.getInventoryDetectionManager() != null) {
            rpg.getInventoryDetectionManager().removeTracker(uuid);
        }

        // Clean up realm HUDs (combat, victory, defeat) to prevent memory leak
        if (rpg != null && rpg.getRealmsManager() != null) {
            rpg.getRealmsManager().getHudManager().discardAllHudsForPlayer(uuid);
        }

        // Clean up Hexcode compat state (cast records, echo cooldowns, casting aura)
        // Guard: Hex classes import Hexcode — loading without Hexcode causes NoClassDefFoundError.
        if (io.github.larsonix.trailoforbis.compat.HexcodeCompat.isLoaded()) {
            io.github.larsonix.trailoforbis.compat.HexCastStateStore.onPlayerDisconnect(uuid);
            io.github.larsonix.trailoforbis.compat.HexSpellEchoService.onPlayerDisconnect(uuid);
            io.github.larsonix.trailoforbis.compat.CastingAuraInjector.onPlayerDisconnect(uuid);
        }

        // Clean up admin clipboard to prevent memory leak
        io.github.larsonix.trailoforbis.commands.tooadmin.clipboard.CopyPasteClipboard
            .getInstance().clearAll(uuid);

        // Note: XP bar + energy shield HUD removal handled above via hudLifecycleManager

        // Clean up per-player locks in AttributeManager to prevent memory leak
        attributeServiceOpt.ifPresent(attributeService -> attributeService.cleanupPlayer(uuid));

        // Save asynchronously to not block server
        if (attributeServiceOpt.isEmpty()) {
            return;
        }

        attributeServiceOpt.ifPresent(attributeService -> {
            PlayerDataRepository repo = attributeService.getPlayerDataRepository();

            // Capture save version BEFORE taking snapshot.
            // If player reconnects before async save completes, the version will
            // be incremented and the save will be skipped to prevent data loss.
            long saveVersion = repo.getSaveVersion(uuid);

            // Snapshot data BEFORE async - captures exact state at disconnect time
            Optional<PlayerData> snapshot = repo.get(uuid);

            // Only proceed if we have data to save
            if (snapshot.isEmpty()) {
                return;
            }

            PlayerData dataToSave = snapshot.get();

            // Evict from cache after taking snapshot to prevent unbounded cache growth
            // Data will be re-cached on next login from database
            repo.evict(uuid);

            CompletableFuture.runAsync(() -> {
                try {
                    // Use versioned save to prevent race condition with quick reconnect.
                    // If player reconnected before this executes, the save is skipped.
                    boolean saved = repo.saveWithVersion(dataToSave, saveVersion);
                    if (saved) {
                        LOGGER.at(Level.INFO).log("Saved player data on quit: %s", uuid);
                    }
                    // Note: If save was skipped, the log is already in saveWithVersion
                } catch (Exception e) {
                    LOGGER.at(Level.SEVERE).withCause(e).log("Failed to save player data for %s", uuid);
                }
                // Note: We intentionally do NOT clean up saveVersion here.
                // The entry is small (~40 bytes) and must persist until the player
                // reconnects to properly invalidate any still-pending saves.
            }).orTimeout(30, TimeUnit.SECONDS)
              .exceptionally(e -> {
                  LOGGER.at(Level.SEVERE).log("Async save timed out for %s", uuid);
                  return null;
              })
              .whenComplete((v, ex) -> {
                  // No cleanup needed here. The activeSessions entry was already removed
                  // at the top of onPlayerDisconnect. The saveVersion entry persists until
                  // the player reconnects (incrementSaveVersion in onPlayerReady).
              });
        });
    }

    /**
     * Marks all inventory components as dirty to force a client resend.
     *
     * <p>Replaces the removed {@code Inventory.markChanged()} by marking each
     * {@link InventoryComponent} subtype dirty via ECS component access.
     *
     * @param player The player entity (must be called on the world thread)
     */
    private static void markAllInventoryDirty(@Nonnull Player player) {
        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) return;
        Store<EntityStore> store = ref.getStore();

        InventoryComponent.Hotbar hotbar = store.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
        if (hotbar != null) hotbar.markDirty();

        InventoryComponent.Storage storage = store.getComponent(ref, InventoryComponent.Storage.getComponentType());
        if (storage != null) storage.markDirty();

        InventoryComponent.Armor armor = store.getComponent(ref, InventoryComponent.Armor.getComponentType());
        if (armor != null) armor.markDirty();

        InventoryComponent.Utility utility = store.getComponent(ref, InventoryComponent.Utility.getComponentType());
        if (utility != null) utility.markDirty();
    }

}
