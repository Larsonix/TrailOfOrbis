package io.github.larsonix.trailoforbis.maps.listeners;

import com.hypixel.hytale.builtin.portals.PortalsPlugin;
import com.hypixel.hytale.builtin.portals.components.PortalDevice;
import com.hypixel.hytale.builtin.portals.components.PortalDeviceConfig;
import com.hypixel.hytale.builtin.portals.ui.PortalDeviceActivePage;
import com.hypixel.hytale.builtin.portals.ui.PortalDeviceSummonPage;
import com.hypixel.hytale.builtin.portals.utils.BlockTypeUtils;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.maps.RealmsManager;
import io.github.larsonix.trailoforbis.maps.gateway.GatewayUpgradeManager;
import io.github.larsonix.trailoforbis.maps.gateway.GatewayUpgradePage;
import io.github.larsonix.trailoforbis.maps.ui.RealmMapSummonPage;
import io.github.larsonix.trailoforbis.util.MessageColors;
import io.github.larsonix.trailoforbis.maps.api.RealmsService.ValidationResult;
import io.github.larsonix.trailoforbis.maps.core.RealmMapData;
import io.github.larsonix.trailoforbis.maps.items.RealmMapUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

/**
 * Custom page supplier for Portal_Device blocks that handles both:
 * <ul>
 *   <li>Realm maps - activates our custom realm system</li>
 *   <li>Vanilla PortalKey items (Fragment Keys) - delegates to vanilla behavior</li>
 * </ul>
 *
 * <p>This supplier is registered to REPLACE the vanilla "PortalDevice" page,
 * allowing realm maps to work alongside vanilla fragment keys.
 *
 * @see RealmMapUtils
 * @see RealmsManager
 */
public class RealmPortalDevicePageSupplier implements OpenCustomUIInteraction.CustomPageSupplier {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /**
     * Codec for deserialization from block JSON config.
     * Includes the PortalDeviceConfig that vanilla Portal_Device uses.
     */
    public static final BuilderCodec<RealmPortalDevicePageSupplier> CODEC = BuilderCodec.builder(
            RealmPortalDevicePageSupplier.class,
            RealmPortalDevicePageSupplier::new
        )
        .appendInherited(
            new KeyedCodec<>("Config", PortalDeviceConfig.CODEC),
            (supplier, o) -> supplier.config = o,
            supplier -> supplier.config,
            (supplier, parent) -> supplier.config = parent.config
        )
        .documentation("The portal device's config.")
        .add()
        .build();

    // Message colors (from shared MessageColors)
    private static final String COLOR_ERROR = MessageColors.ERROR;
    private static final String COLOR_SUCCESS = MessageColors.SUCCESS;
    private static final String COLOR_PREFIX = MessageColors.DARK_PURPLE;

    private PortalDeviceConfig config;

    public RealmPortalDevicePageSupplier() {
        // Default constructor for codec
    }

    @Nullable
    @Override
    public CustomUIPage tryCreate(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull ComponentAccessor<EntityStore> store,
            @Nonnull PlayerRef playerRef,
            @Nonnull InteractionContext context) {

        // DEBUG: Log that our custom page supplier is being called
        LOGGER.atFine().log("[DEBUG] RealmPortalDevicePageSupplier.tryCreate() called for player %s",
            playerRef.getUuid());

        BlockPosition targetBlock = context.getTargetBlock();
        if (targetBlock == null) {
            return null;
        }

        Player playerComponent = store.getComponent(ref, Player.getComponentType());
        if (playerComponent == null) {
            return null;
        }

        ItemStack inHand = playerComponent.getInventory().getItemInHand();
        World world = store.getExternalData().getWorld();

        // Check if held item is a realm map — show the portal summon UI
        if (inHand != null && !inHand.isEmpty() && RealmMapUtils.isRealmMap(inHand)) {
            LOGGER.atInfo().log("Player %s using realm map on Portal_Device at %d,%d,%d",
                playerRef.getUuid(), targetBlock.x, targetBlock.y, targetBlock.z);

            return createRealmMapPage(playerRef, playerComponent, inHand, world, targetBlock);
        }

        // Not a realm map - delegate to vanilla Portal_Device behavior
        return handleVanillaPortalDevice(ref, store, playerRef, playerComponent, inHand, world, targetBlock);
    }

    /**
     * Creates a RealmMapSummonPage that shows realm map details in the vanilla portal UI.
     * Pre-validates the map and player state; returns null with error message if invalid.
     */
    @Nullable
    private CustomUIPage createRealmMapPage(
            @Nonnull PlayerRef playerRef,
            @Nonnull Player player,
            @Nonnull ItemStack mapItem,
            @Nonnull World world,
            @Nonnull BlockPosition targetBlock) {

        TrailOfOrbis plugin = TrailOfOrbis.getInstanceOrNull();
        if (plugin == null || plugin.getRealmsManager() == null) {
            sendError(playerRef, "Realms system is not available !");
            return null;
        }

        RealmsManager realmsManager = plugin.getRealmsManager();
        UUID playerId = playerRef.getUuid();
        Vector3i portalPosition = new Vector3i(targetBlock.x, targetBlock.y, targetBlock.z);

        if (realmsManager.isPlayerInRealm(playerId)) {
            sendError(playerRef, "You are already in a realm !");
            return null;
        }

        Optional<RealmMapData> mapDataOpt = RealmMapUtils.readMapData(mapItem);
        if (mapDataOpt.isEmpty()) {
            sendError(playerRef, "Invalid realm map !");
            return null;
        }

        RealmMapData mapData = mapDataOpt.get();

        ValidationResult validation = realmsManager.validateMapData(mapData);
        if (!validation.valid()) {
            sendError(playerRef, validation.errorMessage() != null ? validation.errorMessage() : "Invalid map");
            return null;
        }

        if (realmsManager.getPortalManager().isPortalActive(world, portalPosition)) {
            sendError(playerRef, "This portal is already active ! Wait for it to close.");
            return null;
        }

        // Check gateway tier level gate — ALL Portal_Devices are gateways (tier 0 by default)
        GatewayUpgradeManager upgradeManager = realmsManager.getGatewayUpgradeManager();
        if (upgradeManager != null) {
            UUID worldUuid = world.getWorldConfig().getUuid();
            int maxLevel = upgradeManager.getMaxRealmLevel(worldUuid,
                targetBlock.x, targetBlock.y, targetBlock.z);
            if (mapData.level() > maxLevel) {
                int currentTier = upgradeManager.getGatewayTier(worldUuid,
                    targetBlock.x, targetBlock.y, targetBlock.z);
                var currentConfig = upgradeManager.getConfig().getTier(currentTier);
                var nextConfig = upgradeManager.getConfig().getNextTier(currentTier);
                String currentName = currentConfig != null ? currentConfig.name() : "Unknown";
                String nextName = nextConfig != null ? nextConfig.name() : "higher tier";
                sendError(playerRef, String.format(
                    "This %s cannot channel maps above level %d. Upgrade to %s !",
                    currentName, maxLevel, nextName));
                return null;
            }
        }

        Inventory inventory = player.getInventory();
        byte activeSlot = (inventory != null) ? inventory.getActiveHotbarSlot() : Inventory.INACTIVE_SLOT_INDEX;

        return new RealmMapSummonPage(playerRef, config, mapData, activeSlot, portalPosition);
    }

    /**
     * Handles Portal_Device behavior for non-realm-map items.
     *
     * <p>Flow (in order):
     * <ol>
     *   <li>Active realm destination → show realm info</li>
     *   <li>Active vanilla destination → vanilla PortalDeviceActivePage</li>
     *   <li>Invalid destination → reset portal</li>
     *   <li>Idle + empty-handed → Gateway Upgrade UI (all portals are gateways)</li>
     *   <li>Idle + holding item → vanilla PortalDeviceSummonPage (Fragment Keys work here)</li>
     * </ol>
     */
    @Nullable
    private CustomUIPage handleVanillaPortalDevice(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull ComponentAccessor<EntityStore> store,
            @Nonnull PlayerRef playerRef,
            @Nonnull Player playerComponent,
            @Nullable ItemStack inHand,
            @Nonnull World world,
            @Nonnull BlockPosition targetBlock) {

        if (config == null) {
            LOGGER.atWarning().log("PortalDeviceConfig is null, cannot process vanilla portal");
            return null;
        }

        BlockType blockType = world.getBlockType(targetBlock.x, targetBlock.y, targetBlock.z);

        // Validate block states
        for (String blockStateKey : config.getBlockStates()) {
            BlockType blockState = BlockTypeUtils.getBlockForState(blockType, blockStateKey);
            if (blockState == null) {
                playerRef.sendMessage(Message.translation("server.portals.device.blockStateMisconfigured").param("state", blockStateKey));
                return null;
            }
        }

        BlockType onBlock = BlockTypeUtils.getBlockForState(blockType, config.getOnState());
        ChunkStore chunkStore = world.getChunkStore();
        Ref<ChunkStore> blockRef = BlockModule.getBlockEntity(world, targetBlock.x, targetBlock.y, targetBlock.z);

        if (blockRef == null) {
            playerRef.sendMessage(Message.translation("server.portals.device.blockEntityMisconfigured"));
            return null;
        }

        PortalDevice existingDevice = chunkStore.getStore().getComponent(blockRef, PortalsPlugin.getInstance().getPortalDeviceComponentType());
        World destinationWorld = existingDevice == null ? null : existingDevice.getDestinationWorld();

        // If portal has invalid destination, reset it
        if (existingDevice != null && blockType == onBlock && !isPortalWorldValid(destinationWorld)) {
            world.setBlockInteractionState(new Vector3i(targetBlock.x, targetBlock.y, targetBlock.z), blockType, config.getOffState());
            playerRef.sendMessage(Message.translation("server.portals.device.adjusted").color(MessageColors.ERROR));
            return null;
        }

        // If portal already has a valid destination, check if it's a REALM portal
        if (existingDevice != null && destinationWorld != null) {
            // Check if this is our realm portal
            TrailOfOrbis plugin = TrailOfOrbis.getInstanceOrNull();
            if (plugin != null && plugin.getRealmsManager() != null) {
                Vector3i portalPos = new Vector3i(targetBlock.x, targetBlock.y, targetBlock.z);
                var realmIdOpt = plugin.getRealmsManager().getPortalManager()
                    .getRealmForPortal(world, portalPos);
                if (realmIdOpt.isPresent()) {
                    var realmOpt = plugin.getRealmsManager().getRealm(realmIdOpt.get());
                    if (realmOpt.isPresent()) {
                        var realm = realmOpt.get();
                        var mapData = realm.getMapData();
                        // Show realm info using the summon UI template
                        return new io.github.larsonix.trailoforbis.maps.ui.RealmMapSummonPage(
                            playerRef, config, mapData, (byte) -1, portalPos);
                    }
                }
            }
            // Fall back to vanilla for non-realm portals
            return new PortalDeviceActivePage(playerRef, config, blockRef);
        }

        // Create device component if needed
        if (existingDevice == null) {
            existingDevice = new PortalDevice(config, blockType.getId());
            chunkStore.getStore().putComponent(blockRef, PortalsPlugin.getInstance().getPortalDeviceComponentType(), existingDevice);
        }

        // Portal is idle (no active destination).
        // Empty-handed → show Gateway Upgrade UI for ANY Portal_Device
        // Holding an item → let vanilla handle (Fragment Keys, errors for wrong items)
        if (inHand == null || inHand.isEmpty()) {
            TrailOfOrbis plugin = TrailOfOrbis.getInstanceOrNull();
            if (plugin != null && plugin.getRealmsManager() != null) {
                GatewayUpgradeManager upgradeManager = plugin.getRealmsManager().getGatewayUpgradeManager();
                if (upgradeManager != null) {
                    UUID worldUuid = world.getWorldConfig().getUuid();
                    int currentTier = upgradeManager.getGatewayTier(worldUuid,
                        targetBlock.x, targetBlock.y, targetBlock.z);
                    LOGGER.atFine().log("Empty-handed on idle portal — showing gateway upgrade UI at (%d,%d,%d) tier %d",
                        targetBlock.x, targetBlock.y, targetBlock.z, currentTier);

                    Store<EntityStore> entityStore = ref.getStore();
                    GatewayUpgradePage page = new GatewayUpgradePage(
                        playerRef, worldUuid,
                        targetBlock.x, targetBlock.y, targetBlock.z,
                        currentTier);
                    page.open(entityStore);
                    return null;
                }
            }
        }

        // Holding an item — vanilla summon page validates Fragment Keys
        return new PortalDeviceSummonPage(playerRef, config, blockRef, inHand);
    }

    /**
     * Checks if a portal world is valid.
     */
    private static boolean isPortalWorldValid(@Nullable World world) {
        if (world == null) {
            return false;
        }
        try {
            Store<EntityStore> store = world.getEntityStore().getStore();
            var portalWorld = store.getResource(PortalsPlugin.getInstance().getPortalResourceType());
            return portalWorld.exists();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Sends an error message to a player.
     */
    private void sendError(@Nonnull PlayerRef playerRef, @Nonnull String message) {
        Message msg = Message.raw("[Realms] ").color(COLOR_PREFIX)
            .insert(Message.raw(message).color(COLOR_ERROR));
        playerRef.sendMessage(msg);
    }

    /**
     * Sends a success message to a player.
     */
    private void sendSuccess(@Nonnull PlayerRef playerRef, @Nonnull String message) {
        Message msg = Message.raw("[Realms] ").color(COLOR_PREFIX)
            .insert(Message.raw(message).color(COLOR_SUCCESS));
        playerRef.sendMessage(msg);
    }
}
