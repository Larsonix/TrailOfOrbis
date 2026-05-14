package io.github.larsonix.trailoforbis.compat;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.ModelParticle;
import com.hypixel.hytale.protocol.Vector3f;
import com.hypixel.hytale.protocol.packets.entities.SpawnModelParticles;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

// Direct Hexcode imports — this class is only loaded when Hexcode is present
// (initialized behind HexcodeCompat.isLoaded() gate in TrailOfOrbis.java)
import com.riprod.hexcode.core.common.hexcaster.component.HexcasterComponent;
import com.riprod.hexcode.state.HexState;
import com.riprod.hexcode.core.state.casting.component.HexcasterCastingComponent;
import com.riprod.hexcode.core.common.hexstaff.component.HexStaffAsset;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sends {@code SpawnModelParticles} packets to the Casting_Anchor entity
 * when a player enters CASTING mode with an RPG staff.
 *
 * <p>Uses direct Hexcode imports (compileOnly). This class is ONLY loaded when
 * Hexcode is present — its initialization is gated behind {@code HexcodeCompat.isLoaded()}.
 *
 * <p>Called from the world tick via polling — checks hex state each tick
 * and sends particles once on CASTING entry.
 */
public final class CastingAuraInjector {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static volatile boolean initialized = false;

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
     * Initialize. Call once after HexcodeCompat.initialize().
     * With direct imports, no reflection needed — just mark as ready.
     */
    public static void initialize() {
        if (initialized || !HexcodeCompat.isLoaded()) return;
        initialized = true;
        LOGGER.atInfo().log("[CastingAuraInjector] Initialized (direct API, zero reflection)");
    }

    /**
     * Call every tick for each player. Checks hex state and sends particles
     * to the Casting_Anchor entity on CASTING entry.
     */
    public static void tick(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> playerRef,
            @Nonnull UUID playerUuid,
            @Nonnull PlayerRef playerPacketRef) {

        if (!initialized) return;

        try {
            // Check hex state via direct API (no reflection)
            HexcasterComponent hexcaster = store.getComponent(playerRef, HexcasterComponent.getComponentType());
            if (hexcaster == null) return;

            HexState currentState = hexcaster.getState();
            if (currentState != HexState.CASTING) {
                // Not casting — clear tracking so next CASTING entry triggers particles
                sentParticles.remove(playerUuid);
                return;
            }

            // Player IS in CASTING state
            if (sentParticles.putIfAbsent(playerUuid, Boolean.TRUE) != null) {
                return; // Already sent particles for this session
            }

            // Check if main hand is an RPG item (skip for vanilla Hexcode items that work fine)
            // Bypass InventoryComponent.getItemInHand() — it delegates to Inventory.getItemInHand()
            // which returns tool items when _usingToolsItem is set. Read the hotbar directly.
            Player casterPlayer = store.getComponent(playerRef,
                    Player.getComponentType());
            ItemStack mainHand = (casterPlayer != null && casterPlayer.getInventory() != null)
                ? casterPlayer.getInventory().getActiveHotbarItem() : null;
            if (mainHand == null || mainHand.isEmpty() || mainHand.getItem() == null) return;
            String itemId = mainHand.getItem().getId();
            if (!itemId.startsWith("rpg_gear_")) return;

            // Get the CastingAnchor entity ref via direct API (no reflection)
            HexcasterCastingComponent castingComp = store.getComponent(playerRef,
                    HexcasterCastingComponent.getComponentType());
            if (castingComp == null) return;

            Ref<EntityStore> castingRootRef = castingComp.getCastingRootRef();
            if (castingRootRef == null || !castingRootRef.isValid()) return;

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
            // Direct API: no reflection needed for HexStaffAsset access
            HexStaffAsset asset = HexStaffAsset.getAssetMap().getAsset(itemId);
            if (asset != null) {
                com.hypixel.hytale.server.core.asset.type.model.config.ModelParticle[] configParticles =
                        asset.getCastingAuraParticles();
                if (configParticles != null && configParticles.length > 0) {
                    ModelParticle[] result = new ModelParticle[configParticles.length];
                    for (int i = 0; i < configParticles.length; i++) {
                        result[i] = configParticles[i].toPacket();
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
