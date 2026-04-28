package io.github.larsonix.trailoforbis.compat;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.ModelParticle;
import com.hypixel.hytale.protocol.Vector3f;
import com.hypixel.hytale.protocol.packets.entities.SpawnModelParticles;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sends {@code SpawnModelParticles} packets to the Casting_Anchor entity
 * when a player enters CASTING mode with an RPG staff.
 *
 * <p>The Casting_Anchor entity's Model IS created with particles by Hexcode's
 * RootSpawner, but the entity tracker's model sync doesn't reliably deliver
 * particles to the client for dynamically spawned entities. This injector
 * bypasses that by sending an explicit {@code SpawnModelParticles} packet
 * directly to the Casting_Anchor's network ID.
 *
 * <p>Called from the world tick via polling — checks hex state each tick
 * and sends particles once on CASTING entry.
 */
public final class CastingAuraInjector {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // Reflection handles (resolved once)
    private static volatile boolean initialized = false;
    @Nullable private static ComponentType<EntityStore, ?> hexcasterComponentType;
    @Nullable private static Method getStateMethod;
    @Nullable private static Object castingState; // HexState.CASTING
    @Nullable private static Method getCastingCompMethod; // buffer.getComponent(ref, HexcasterCastingComponent.type)
    @Nullable private static ComponentType<EntityStore, ?> castingCompType;
    @Nullable private static Method getCastingRootRefMethod;

    // Per-player tracking: true = already sent particles for current CASTING session
    private static final Map<UUID, Boolean> sentParticles = new ConcurrentHashMap<>();

    // Default aura particles (from Hexstaff_Basic_Crude)
    private static final ModelParticle[] DEFAULT_AURA;

    static {
        ModelParticle mist = new ModelParticle();
        mist.systemId = "Hexstaff_Generic_Mist";
        mist.scale = 0.9f;
        mist.positionOffset = new Vector3f(0, -1.5f, 0);

        ModelParticle sparks = new ModelParticle();
        sparks.systemId = "Hexstaff_Sparks_Fast";
        sparks.scale = 0.7f;
        sparks.positionOffset = new Vector3f(0, -0.8f, 0);

        DEFAULT_AURA = new ModelParticle[]{mist, sparks};
    }

    private CastingAuraInjector() {}

    /**
     * Initialize reflection handles. Call once after HexcodeCompat.initialize().
     */
    public static void initialize() {
        if (initialized || !HexcodeCompat.isLoaded()) return;

        try {
            // HexcasterComponent
            Class<?> hexcasterClass = Class.forName(
                    "com.riprod.hexcode.core.common.hexcaster.component.HexcasterComponent");
            Method getCompType = hexcasterClass.getMethod("getComponentType");
            @SuppressWarnings("unchecked")
            ComponentType<EntityStore, ?> compType =
                    (ComponentType<EntityStore, ?>) getCompType.invoke(null);
            hexcasterComponentType = compType;
            getStateMethod = hexcasterClass.getMethod("getState");

            // HexState.CASTING (ordinal 1)
            Class<?> hexStateClass = Class.forName("com.riprod.hexcode.state.HexState");
            Object[] states = hexStateClass.getEnumConstants();
            if (states != null && states.length > 1) {
                castingState = states[1]; // CASTING
            }

            // HexcasterCastingComponent
            Class<?> castingCompClass = Class.forName(
                    "com.riprod.hexcode.core.state.casting.component.HexcasterCastingComponent");
            Method getCastingType = castingCompClass.getMethod("getComponentType");
            @SuppressWarnings("unchecked")
            ComponentType<EntityStore, ?> cType =
                    (ComponentType<EntityStore, ?>) getCastingType.invoke(null);
            castingCompType = cType;
            getCastingRootRefMethod = castingCompClass.getMethod("getCastingRootRef");

            initialized = true;
            LOGGER.atInfo().log("[CastingAuraInjector] Initialized — will inject particles for RPG casting");

        } catch (Exception e) {
            LOGGER.atWarning().log("[CastingAuraInjector] Failed to initialize: %s", e.getMessage());
            initialized = true; // Don't retry
        }
    }

    /**
     * Call every tick for each player. Checks hex state and sends particles
     * to the Casting_Anchor entity on CASTING entry.
     *
     * @param store The entity store
     * @param playerRef The player's entity ref
     * @param playerUuid The player's UUID
     * @param playerPacketRef The PlayerRef for sending packets
     */
    public static void tick(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> playerRef,
            @Nonnull UUID playerUuid,
            @Nonnull PlayerRef playerPacketRef) {

        if (!initialized || hexcasterComponentType == null || castingState == null) return;

        try {
            // Check hex state
            Object hexcaster = store.getComponent(playerRef, hexcasterComponentType);
            if (hexcaster == null) return;

            Object currentState = getStateMethod.invoke(hexcaster);
            if (currentState != castingState) {
                // Not casting — clear tracking so next CASTING entry triggers particles
                sentParticles.remove(playerUuid);
                return;
            }

            // Player IS in CASTING state
            if (sentParticles.putIfAbsent(playerUuid, Boolean.TRUE) != null) {
                return; // Already sent particles for this session
            }

            // Check if main hand is an RPG item (skip for vanilla Hexcode items that work fine)
            ItemStack mainHand = InventoryComponent.getItemInHand(store, playerRef);
            if (mainHand == null || mainHand.isEmpty() || mainHand.getItem() == null) return;
            String itemId = mainHand.getItem().getId();
            if (!itemId.startsWith("rpg_gear_")) return;

            // Get the CastingAnchor entity ref from HexcasterCastingComponent
            if (castingCompType == null || getCastingRootRefMethod == null) return;
            Object castingComp = store.getComponent(playerRef, castingCompType);
            if (castingComp == null) return;

            Object rootRefObj = getCastingRootRefMethod.invoke(castingComp);
            if (!(rootRefObj instanceof Ref<?> rootRef) || !rootRef.isValid()) return;

            @SuppressWarnings("unchecked")
            Ref<EntityStore> castingRootRef = (Ref<EntityStore>) rootRef;

            // Get the Casting_Anchor entity's NetworkId
            NetworkId networkId = store.getComponent(castingRootRef, NetworkId.getComponentType());
            if (networkId == null) return;

            // Resolve particles from the HexStaffAsset if possible, else use defaults
            ModelParticle[] particles = resolveParticles(itemId);

            // Send SpawnModelParticles to the player
            SpawnModelParticles packet = new SpawnModelParticles(networkId.getId(), particles);
            playerPacketRef.getPacketHandler().write(packet);

            LOGGER.atInfo().log("[CastingAuraInjector] Sent aura particles to Casting_Anchor (netId=%d) for RPG item '%s'",
                    networkId.getId(), itemId);

        } catch (Exception e) {
            // Silent — don't spam logs every tick
        }
    }

    /**
     * Resolves aura particles for an RPG item from its registered HexStaffAsset.
     * Falls back to default particles if resolution fails.
     */
    @Nonnull
    private static ModelParticle[] resolveParticles(@Nonnull String itemId) {
        try {
            // Try to get the HexStaffAsset and its CastingAuraParticles
            Class<?> hexStaffAssetClass = Class.forName(
                    "com.riprod.hexcode.core.common.hexstaff.component.HexStaffAsset");
            Method getAssetMap = hexStaffAssetClass.getMethod("getAssetMap");
            Object assetMap = getAssetMap.invoke(null);
            Method getAsset = assetMap.getClass().getMethod("getAsset", Object.class);
            Object asset = getAsset.invoke(assetMap, itemId);

            if (asset != null) {
                Method getParticles = asset.getClass().getMethod("getCastingAuraParticles");
                Object configParticles = getParticles.invoke(asset);
                if (configParticles instanceof Object[] arr && arr.length > 0) {
                    // Convert config ModelParticle[] to protocol ModelParticle[]
                    ModelParticle[] result = new ModelParticle[arr.length];
                    for (int i = 0; i < arr.length; i++) {
                        Method toPacket = arr[i].getClass().getMethod("toPacket");
                        result[i] = (ModelParticle) toPacket.invoke(arr[i]);
                    }
                    return result;
                }
            }
        } catch (Exception e) {
            // Fall through to defaults
        }
        return DEFAULT_AURA;
    }

    /** Clean up tracking for a disconnected player. */
    public static void onPlayerDisconnect(@Nonnull UUID uuid) {
        sentParticles.remove(uuid);
    }
}
