package io.github.larsonix.trailoforbis.maps.ui;

import com.hypixel.hytale.builtin.portals.PortalsPlugin;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.builtin.portals.components.PortalDevice;
import com.hypixel.hytale.builtin.portals.components.PortalDeviceConfig;
import com.hypixel.hytale.builtin.portals.utils.BlockTypeUtils;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.gear.GearManager;
import io.github.larsonix.trailoforbis.gear.sync.ItemSyncCoordinator;
import io.github.larsonix.trailoforbis.maps.RealmsManager;
import io.github.larsonix.trailoforbis.maps.api.RealmsService.ValidationResult;
import io.github.larsonix.trailoforbis.maps.core.RealmMapData;
import io.github.larsonix.trailoforbis.maps.instance.RealmInstance;
import io.github.larsonix.trailoforbis.maps.items.RealmMapUtils;
import io.github.larsonix.trailoforbis.maps.modifiers.RealmModifier;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Custom UI page that shows realm map details using the native PortalDeviceSummon.ui template.
 *
 * <p>This page reuses the vanilla portal summoning UI but populates it with realm map data
 * instead of fragment/portal key data. When the player clicks "Open Realm", we create the
 * realm instance, consume the map, and activate the portal device.
 *
 * <p>Layout mapping (vanilla element -> our data):
 * <ul>
 *   <li>{@code #Title0} -> Rarity + Biome name (e.g. "Legendary Forest Realm")</li>
 *   <li>{@code #FlavorLabel} -> Quality, level, and size summary</li>
 *   <li>{@code #ExplorationTimeText} -> Realm time limit</li>
 *   <li>{@code #ObjectivesList} -> Difficulty modifiers (prefixes)</li>
 *   <li>{@code #TipsList} -> Reward modifiers (suffixes)</li>
 *   <li>{@code #Pills} -> Rarity, biome, and size badges</li>
 *   <li>{@code #SummonButton} -> "Open Realm" action</li>
 * </ul>
 */
public class RealmMapSummonPage extends InteractiveCustomUIPage<RealmMapSummonPage.Data> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // Colors matching RealmMapTooltipBuilder
    private static final String COLOR_DIFFICULTY = "#FF6666";
    private static final String COLOR_REWARD = "#FFD700";
    private static final String COLOR_QUALITY = "#AAFFAA";
    private static final String COLOR_MUTED = "#778292";
    private static final String COLOR_TEXT = "#dee2ef";

    // Pill badge colors
    private static final String PILL_BIOME_COLOR = "#2a6496";
    private static final String PILL_SIZE_COLOR = "#3a7a3a";

    private final PortalDeviceConfig portalConfig;
    private final RealmMapData mapData;
    private final byte hotbarSlot;
    private final Vector3i portalPosition;

    public RealmMapSummonPage(
            @Nonnull PlayerRef playerRef,
            @Nonnull PortalDeviceConfig portalConfig,
            @Nonnull RealmMapData mapData,
            byte hotbarSlot,
            @Nonnull Vector3i portalPosition) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, Data.CODEC);
        this.portalConfig = portalConfig;
        this.mapData = mapData;
        this.hotbarSlot = hotbarSlot;
        this.portalPosition = portalPosition;
    }

    // =========================================================================
    // BUILD — Populate the native template with realm map data
    // =========================================================================

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder cmd,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store) {

        cmd.append("Pages/PortalDeviceSummon.ui");

        // Title: "[Rarity] [Biome] Realm" or "[Rarity] Realm (Unidentified)"
        String title = mapData.identified()
                ? capitalize(mapData.rarity().name()) + " " + mapData.biome().getDisplayName() + " Realm"
                : capitalize(mapData.rarity().name()) + " Realm (Unidentified)";
        cmd.set("#Title0.TextSpans", Message.raw(title).color(mapData.rarity().getHexColor()));

        // Artwork: use default for now (vanilla DefaultArtwork.png stays)
        // TODO: Per-biome artwork when assets are available

        // Hide WIP notice — not relevant for realms
        // The WIP notice is a direct child without an ID in the .ui, so we can't hide it directly.
        // Instead, we'll overwrite its label text to be empty.

        // Time limit (per-map: based on size multiplier + BONUS_TIME/REDUCED_TIME modifiers)
        RealmsManager rm = getRealmsManager();
        int timeLimitSeconds = rm != null
                ? mapData.computeTimeoutSeconds(rm.getConfig())
                : mapData.computeTimeoutSeconds();
        int timeLimitMinutes = timeLimitSeconds / 60;
        cmd.set("#ExplorationTimeText.TextSpans",
                Message.raw(timeLimitMinutes + " minutes to clear").color(COLOR_TEXT));

        // No void invasion in realms
        cmd.set("#BreachTimeBullet.Visible", false);

        // Quality multiplier scales all modifier values (same formula as tooltip)
        double qualityMult = mapData.qualityMultiplier();

        // Difficulty modifiers — populate vanilla #ObjectivesList with prefixes
        // Only show modifiers if the map is identified
        if (mapData.identified()) {
            List<RealmModifier> prefixes = mapData.prefixes();
            cmd.set("#Objectives.Visible", !prefixes.isEmpty());
            for (int i = 0; i < prefixes.size(); i++) {
                cmd.append("#ObjectivesList", "Pages/Portals/BulletPoint.ui");
                cmd.set("#ObjectivesList[" + i + "] #Label.TextSpans",
                        Message.raw(formatModifier(prefixes.get(i), qualityMult)).color(COLOR_DIFFICULTY));
            }

            // Reward modifiers — populate vanilla #TipsList with suffixes
            List<RealmModifier> suffixes = mapData.suffixes();
            cmd.set("#Tips.Visible", !suffixes.isEmpty());
            for (int i = 0; i < suffixes.size(); i++) {
                cmd.append("#TipsList", "Pages/Portals/BulletPoint.ui");
                cmd.set("#TipsList[" + i + "] #Label.TextSpans",
                        Message.raw(formatModifier(suffixes.get(i), qualityMult)).color(COLOR_REWARD));
            }
        } else {
            cmd.set("#Objectives.Visible", false);
            cmd.set("#Tips.Visible", false);
        }

        // Pill badges: Rarity + Biome + Size (hide biome/size when unidentified)
        addPill(cmd, 0, capitalize(mapData.rarity().name()), mapData.rarity().getHexColor());
        if (mapData.identified()) {
            addPill(cmd, 1, mapData.biome().getDisplayName(), PILL_BIOME_COLOR);
            addPill(cmd, 2, mapData.size().getDisplayName(), PILL_SIZE_COLOR);
        } else {
            addPill(cmd, 1, "???", PILL_BIOME_COLOR);
            addPill(cmd, 2, "???", PILL_SIZE_COLOR);
        }

        // Flavor text: Level + Quality + IIQ summary
        Message flavor = Message.empty()
                .insert(Message.raw("Level " + mapData.level()).color(COLOR_TEXT))
                .insert(Message.raw("  |  ").color(COLOR_MUTED))
                .insert(Message.raw("Quality: " + mapData.quality() + "%").color(COLOR_QUALITY));
        if (mapData.fortunesCompassBonus() > 0) {
            flavor.insert(Message.raw("  |  ").color(COLOR_MUTED))
                  .insert(Message.raw("+" + mapData.fortunesCompassBonus() + "% IIQ").color("#7CF2A7"));
        }
        if (mapData.corrupted()) {
            flavor.insert(Message.raw("  |  ").color(COLOR_MUTED))
                  .insert(Message.raw("Corrupted").color("#FF4444"));
        }
        cmd.set("#FlavorLabel.TextSpans", flavor);

        // Event bindings for the summon button
        // Note: button text stays as vanilla "Summon Portal" — native TextButton doesn't support
        // server-side .Text override (crashes client)
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SummonButton",
                EventData.of("Action", "SummonActivated"), false);
        events.addEventBinding(CustomUIEventBindingType.MouseEntered, "#SummonButton",
                EventData.of("Action", "SummonMouseEntered"), false);
        events.addEventBinding(CustomUIEventBindingType.MouseExited, "#SummonButton",
                EventData.of("Action", "SummonMouseExited"), false);
    }

    private void addPill(UICommandBuilder cmd, int index, String label, String color) {
        String child = "#Pills[" + index + "]";
        cmd.append("#Pills", "Pages/Portals/Pill.ui");
        cmd.set(child + ".Background.Color", color);
        cmd.set(child + " #Label.TextSpans", Message.raw(label));
    }

    // =========================================================================
    // HANDLE EVENTS — Summon button click + hover effects
    // =========================================================================

    @Override
    public void handleDataEvent(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nonnull Data data) {

        if ("SummonMouseEntered".equals(data.action)) {
            UICommandBuilder cmd = new UICommandBuilder();
            cmd.set("#Vignette.Visible", true);
            sendUpdate(cmd, null, false);
            return;
        }
        if ("SummonMouseExited".equals(data.action)) {
            UICommandBuilder cmd = new UICommandBuilder();
            cmd.set("#Vignette.Visible", false);
            sendUpdate(cmd, null, false);
            return;
        }

        // "SummonActivated" — open the realm
        Player playerComponent = store.getComponent(ref, Player.getComponentType());
        if (playerComponent == null) return;

        // Close the UI immediately
        playerComponent.getPageManager().setPage(ref, store, Page.None);

        // Validate the map is still in hand (skip if hotbarSlot is invalid, e.g. portal
        // with an existing realm destination that doesn't require a map in hand)
        Inventory inventory = playerComponent.getInventory();
        if (hotbarSlot >= 0) {
            ItemContainer hotbar = inventory.getHotbar();
            ItemStack currentItem = hotbar.getItemStack(hotbarSlot);
            if (currentItem == null || currentItem.isEmpty() || !RealmMapUtils.isRealmMap(currentItem)) {
                sendError("Map is no longer in your hotbar !");
                return;
            }
        }

        RealmsManager realmsManager = getRealmsManager();
        if (realmsManager == null) {
            sendError("Realms system is not available !");
            return;
        }

        UUID playerId = playerRef.getUuid();

        // Re-validate before committing
        if (realmsManager.isPlayerInRealm(playerId)) {
            sendError("You are already in a realm !");
            return;
        }

        ValidationResult validation = realmsManager.validateMapData(mapData);
        if (!validation.valid()) {
            sendError(validation.errorMessage() != null ? validation.errorMessage() : "Invalid map");
            return;
        }

        World originWorld = store.getExternalData().getWorld();
        UUID worldUuid = originWorld.getWorldConfig().getUuid();

        // Get return position
        double returnX = 0, returnY = 64, returnZ = 0;
        var transform = playerRef.getTransform();
        if (transform != null) {
            var pos = transform.getPosition();
            returnX = pos.x;
            returnY = pos.y;
            returnZ = pos.z;
        }

        LOGGER.atInfo().log("Player %s opening realm via portal UI: level=%d, biome=%s, rarity=%s",
                playerId, mapData.level(), mapData.biome(), mapData.rarity());

        // Consume the map NOW, while we're on the source world thread with no
        // teleport in progress. This ensures the inventory UpdateItems packet reaches
        // the client and is fully processed many ticks before any JoinWorld packet.
        // If we consumed during onRealmCreated (same tick as the teleport), the packets
        // would arrive in the same network flush, and the client would crash with
        // NullReferenceException during ItemAnimations + world transition.
        if (hotbarSlot >= 0) {
            ItemContainer hotbar = inventory.getHotbar();
            if (hotbar != null) {
                hotbar.removeItemStackFromSlot(hotbarSlot, 1);
                LOGGER.atFine().log("Consumed realm map from hotbar slot %d", hotbarSlot);
            }
        }

        final double fReturnX = returnX, fReturnY = returnY, fReturnZ = returnZ;
        realmsManager.openRealm(mapData, playerId, worldUuid, fReturnX, fReturnY, fReturnZ)
                .thenAccept(realm -> onRealmCreated(realmsManager, originWorld, realm))
                .exceptionally(ex -> {
                    LOGGER.atSevere().withCause(ex).log(
                            "Failed to open realm for player %s — map was already consumed", playerId);
                    sendError("Failed to open realm. Your map was consumed - please contact an admin.");
                    return null;
                });
    }

    // =========================================================================
    // POST-SUMMON — Activate portal, let the player walk through it
    // =========================================================================

    private void onRealmCreated(RealmsManager realmsManager, World sourceWorld, RealmInstance realm) {
        UUID playerId = playerRef.getUuid();

        LOGGER.atInfo().log("Realm %s created via portal UI, activating portal at %s",
                realm.getRealmId(), portalPosition);

        // Suppress item sync BEFORE activating the portal. Between portal activation
        // and DrainPlayerFromWorldEvent (~1s gap), any equipment change (hotbar switch,
        // stat recalc) would fire item sync packets that arrive on the client in the
        // same network frame as JoinWorldPacket → NullReferenceException. The map
        // consumption was already handled earlier, but OTHER inventory changes (like a
        // hotbar switch while stepping onto the portal) are not covered.
        // DrainPlayerFromWorldEvent will call suppressPlayer again (idempotent).
        // onPlayerReady will unsuppress + flush in the new world.
        GearManager gearManager = TrailOfOrbis.getInstance().getGearManager();
        ItemSyncCoordinator syncCoordinator = gearManager != null ? gearManager.getSyncCoordinator() : null;
        if (syncCoordinator != null) {
            syncCoordinator.suppressPlayer(playerId);
        }

        sourceWorld.execute(() -> {
            activatePortalDevice(sourceWorld, realm);

            realmsManager.getPortalManager().trackPortalRealm(sourceWorld, portalPosition, realm);

            sendSuccess("Portal activated ! Step through to enter the realm.");

            LOGGER.atInfo().log("Portal at %s activated for realm %s by player %s",
                    portalPosition, realm.getRealmId(), playerId);

            // Safety net: if the player walks away and never enters the portal,
            // unsuppress after 10s so their item sync resumes normally.
            // If they DO teleport, DrainPlayerFromWorldEvent → onPlayerReady handles it.
            if (syncCoordinator != null) {
                HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
                    if (syncCoordinator.isPlayerSuppressed(playerId)) {
                        LOGGER.atInfo().log("Safety unsuppress for %s — portal not entered within 10s",
                                playerId.toString().substring(0, 8));
                        syncCoordinator.safetyUnsuppress(playerId);
                    }
                }, 10, TimeUnit.SECONDS);
            }
        });
    }

    private void activatePortalDevice(World world, RealmInstance realm) {
        try {
            Ref<ChunkStore> blockEntity = BlockModule.getBlockEntity(
                    world, portalPosition.x, portalPosition.y, portalPosition.z);
            if (blockEntity == null) {
                LOGGER.atWarning().log("No block entity at portal position %s", portalPosition);
                return;
            }

            Store<ChunkStore> chunkStore = world.getChunkStore().getStore();
            PortalDevice portalDevice = chunkStore.getComponent(
                    blockEntity, PortalsPlugin.getInstance().getPortalDeviceComponentType());

            if (portalDevice == null) {
                BlockType blockType = world.getBlockType(portalPosition.x, portalPosition.y, portalPosition.z);
                String blockTypeId = blockType != null ? blockType.getId() : "Portal_Device";
                portalDevice = new PortalDevice(portalConfig, blockTypeId);
                chunkStore.putComponent(blockEntity,
                        PortalsPlugin.getInstance().getPortalDeviceComponentType(), portalDevice);
            }

            World realmWorld = realm.getWorld();
            if (realmWorld == null) {
                LOGGER.atWarning().log("Realm world not ready for realm %s", realm.getRealmId());
                return;
            }

            portalDevice.setDestinationWorld(realmWorld);

            BlockType currentType = world.getBlockType(portalPosition.x, portalPosition.y, portalPosition.z);
            if (currentType != null) {
                BlockType onState = BlockTypeUtils.getBlockForState(currentType, portalConfig.getOnState());
                if (onState != null) {
                    world.setBlockInteractionState(portalPosition, currentType, portalConfig.getOnState());
                }
            }
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Error activating portal device at %s", portalPosition);
        }
    }

    // =========================================================================
    // UTILITIES
    // =========================================================================

    @Nullable
    private RealmsManager getRealmsManager() {
        TrailOfOrbis plugin = TrailOfOrbis.getInstanceOrNull();
        return plugin != null ? plugin.getRealmsManager() : null;
    }

    private void sendError(String message) {
        playerRef.sendMessage(
                Message.raw("[Realms] ").color(MessageColors.DARK_PURPLE)
                        .insert(Message.raw(message).color(MessageColors.ERROR)));
    }

    private void sendSuccess(String message) {
        playerRef.sendMessage(
                Message.raw("[Realms] ").color(MessageColors.DARK_PURPLE)
                        .insert(Message.raw(message).color(MessageColors.SUCCESS)));
    }

    /**
     * Formats a modifier with quality-adjusted value, matching the tooltip display.
     * Same formula as RealmMapTooltipBuilder.buildModifierLine().
     */
    private static String formatModifier(RealmModifier mod, double qualityMult) {
        String text = mod.type().formatValue(mod.value(), qualityMult);
        if (mod.locked()) {
            text += " [Locked]";
        }
        return text;
    }

    private static String capitalize(String enumName) {
        if (enumName == null || enumName.isEmpty()) return enumName;
        return enumName.charAt(0) + enumName.substring(1).toLowerCase();
    }

    // =========================================================================
    // DATA — Event data from UI button clicks
    // =========================================================================

    protected static class Data {
        public static final BuilderCodec<Data> CODEC = BuilderCodec.builder(Data.class, Data::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (d, s) -> d.action = s, d -> d.action)
                .add()
                .build();

        String action;
    }
}
