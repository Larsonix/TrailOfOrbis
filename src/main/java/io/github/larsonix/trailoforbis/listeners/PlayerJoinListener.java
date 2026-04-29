package io.github.larsonix.trailoforbis.listeners;

import com.hypixel.hytale.builtin.instances.InstancesPlugin;
import com.hypixel.hytale.logger.HytaleLogger;
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
import io.github.larsonix.trailoforbis.sanctum.SkillSanctumManager;
import io.github.larsonix.trailoforbis.sanctum.interactions.SkillNodeInteraction;
import io.github.larsonix.trailoforbis.maps.RealmsManager;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
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
     * Tracks UUIDs currently being processed in onPlayerDisconnect to prevent
     * duplicate save operations. When a player exists in multiple worlds (e.g.,
     * realm instance), PlayerDisconnectEvent fires once per world.
     */
    private static final Set<UUID> processingDisconnects = ConcurrentHashMap.newKeySet();

    /**
     * Tracks players who just connected (PlayerConnectEvent). Consumed by the first
     * PlayerReadyEvent to detect login into an instance world (reconnect after disconnect).
     * Normal gameplay world transitions don't set this flag.
     */
    private static final Set<UUID> freshlyConnected = ConcurrentHashMap.newKeySet();

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

        // Clear any stale disconnect-processing guard from previous session.
        // The guard stays active for the entire disconnect flow (sync cleanup + async save)
        // and only needs clearing when the player reconnects.
        processingDisconnects.remove(uuid);

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

        // Cache vanilla stats and apply ECS operations
        Ref<EntityStore> entityRef = playerRef.getReference();
        if (entityRef != null && entityRef.isValid()) {
            Store<EntityStore> store = entityRef.getStore();
            // Only cache on true first login - not on world transitions.
            // The cache is cleared in onPlayerDisconnect, so it will be empty for new sessions.
            if (!VanillaStatCache.hasCached(uuid)) {
                VanillaStatCache.cacheOnJoin(uuid, store, entityRef);
            }
            attributeService.recalculateStats(uuid);
            applyEcsStats(playerRef, uuid, store, repo);
            LOGGER.at(Level.INFO).log("Stats applied for %s", username);

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

            cleanupSanctumInteractions(uuid, store, entityRef);
            tryPlaceSpawnGateways(world);

            // Defer XP bar HUD creation to the NEXT tick after PlayerReadyEvent.
            // MultipleHUD.setCustomHud() calls show() TWICE — the second call generates
            // Set-only commands (hasBuilt=true) referencing elements from the first call's
            // Append. If both packets arrive during JoinWorld processing on the client,
            // the Set can reference elements not yet created. Deferring by one tick ensures
            // the client has fully processed the JoinWorld before receiving HUD packets.
            TrailOfOrbis rpgInstance = TrailOfOrbis.getInstanceOrNull();
            if (rpgInstance != null && rpgInstance.getXpBarHudManager() != null) {
                final UUID deferredUuid = uuid;
                final PlayerRef deferredRef = playerRef;
                world.execute(() -> {
                    if (!deferredRef.isValid()) return;
                    Store<EntityStore> deferredStore = world.getEntityStore().getStore();
                    rpgInstance.getXpBarHudManager().showHud(deferredUuid, deferredRef, deferredStore);
                });
            }

            // Initialize loot filter state for this player
            if (rpgInstance != null && rpgInstance.getLootFilterManager() != null) {
                rpgInstance.getLootFilterManager().onPlayerJoin(uuid);
            }

            if (rpgInstance != null && rpgInstance.getStoneTooltipSyncService() != null) {
                rpgInstance.getStoneTooltipSyncService().syncToPlayer(playerRef);
            }

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

            // Sync colored combat text templates to player
            if (rpgInstance != null && rpgInstance.getCombatTextColorManager() != null) {
                rpgInstance.getCombatTextColorManager().onPlayerReady(playerRef);
            }

            // Note: Vanilla gems/crystals are NOT hidden from creative — they're legitimate
            // decorative items. Only our custom _Light variants are hidden via Variant:true
            // in their asset JSON definitions.

            // DIAGNOSTIC: Verify hex asset registrations + test particle rendering
            if (io.github.larsonix.trailoforbis.compat.HexcodeCompat.isLoaded()) {
                LOGGER.atInfo().log("[HexAssetDiag] Running post-join hex asset verification for %s", username);
                io.github.larsonix.trailoforbis.compat.HexcodeCompat.diagCheckHexAsset("Hexstaff_Basic_Crude");
                ItemStack mainHand = InventoryComponent.getItemInHand(store, entityRef);
                if (mainHand != null && !mainHand.isEmpty() && mainHand.getItem() != null) {
                    String mainHandId = mainHand.getItem().getId();
                    LOGGER.atInfo().log("[HexAssetDiag] Main hand item: '%s'", mainHandId);
                    if (mainHandId.startsWith("rpg_gear_")) {
                        io.github.larsonix.trailoforbis.compat.HexcodeCompat.diagCheckHexAsset(mainHandId);
                    }
                }

                // Particle test removed — confirmed client has particle systems loaded
            }
        } else {
            LOGGER.at(Level.INFO).log("Warning: Entity not available for %s, caching will be deferred", username);
            attributeService.recalculateStats(uuid);
        }

        } catch (Exception e) {
            LOGGER.at(Level.SEVERE).withCause(e).log("Failed to initialize player data for %s", username);
        } finally {
            joiningPlayers.remove(uuid);
        }
        });
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

        // Skip realm instance worlds - we only want gateways in the main overworld
        String worldName = world.getName();
        if (worldName != null && worldName.toLowerCase().contains("realm")) {
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
        UUID uuid = event.getPlayerRef().getUuid();

        // Clear joining guard in case disconnect fires during join (e.g., timeout)
        joiningPlayers.remove(uuid);

        // Guard against duplicate disconnect events (fires once per world when
        // player exists in multiple worlds, e.g., realm instance + overworld)
        if (!processingDisconnects.add(uuid)) {
            LOGGER.atFine().log("Skipping duplicate disconnect for %s", uuid);
            return;
        }

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

        // Clean up energy shield state to prevent memory leak
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

        // Clean up combat trackers to prevent memory leaks
        if (rpg != null && rpg.getConsecutiveHitTracker() != null) {
            rpg.getConsecutiveHitTracker().cleanup(uuid);
        }
        if (rpg != null && rpg.getAilmentImmunityTracker() != null) {
            rpg.getAilmentImmunityTracker().cleanup(uuid);
        }

        // Clean up stone tooltip sync tracking
        if (rpg != null && rpg.getStoneTooltipSyncService() != null) {
            rpg.getStoneTooltipSyncService().onPlayerDisconnect(uuid);
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
        }

        // Save and evict loot filter state
        if (rpg != null && rpg.getLootFilterManager() != null) {
            rpg.getLootFilterManager().onPlayerDisconnect(uuid);
        }

        // Clean up inventory detection tracker
        if (rpg != null && rpg.getInventoryDetectionManager() != null) {
            rpg.getInventoryDetectionManager().removeTracker(uuid);
        }

        // Clean up XP bar HUD
        if (rpg != null && rpg.getXpBarHudManager() != null) {
            rpg.getXpBarHudManager().removeHud(uuid);
        }

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
                  // Note: We intentionally do NOT remove from processingDisconnects here.
                  // The guard stays active until the player reconnects (cleared in onPlayerConnect).
                  // This prevents duplicate saves during server shutdown where disconnect events
                  // fire sequentially (the second arrives after the first save completes).
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
