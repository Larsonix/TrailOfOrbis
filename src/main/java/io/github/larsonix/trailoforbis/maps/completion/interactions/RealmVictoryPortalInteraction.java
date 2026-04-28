package io.github.larsonix.trailoforbis.maps.completion.interactions;

import com.hypixel.hytale.builtin.instances.InstancesPlugin;
import com.hypixel.hytale.builtin.portals.resources.PortalWorld;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.maps.RealmsManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Duration;
import java.util.UUID;

/**
 * Custom portal interaction for realm victory portals.
 *
 * <p>This interaction is nearly identical to Hytale's {@code ReturnPortalInteraction},
 * but with one critical addition: it removes the Victory HUD <b>before</b> calling
 * {@code InstancesPlugin.exitInstance()}.
 *
 * <h2>Why This Exists</h2>
 * <p>The built-in {@code ReturnPortalInteraction} directly calls {@code exitInstance()},
 * which triggers a teleport. By the time {@code DrainPlayerFromWorldEvent} fires,
 * HUD refresh callbacks have stopped and visual updates don't apply - the Victory HUD
 * stays visible after teleport.
 *
 * <p>By removing the HUD <b>proactively</b> (before teleport starts), we ensure
 * the removal happens while we're still on the realm world thread with active
 * HUD rendering.
 *
 * @see com.hypixel.hytale.builtin.portals.interactions.ReturnPortalInteraction
 * @see io.github.larsonix.trailoforbis.maps.ui.RealmHudManager
 */
public class RealmVictoryPortalInteraction extends SimpleBlockInteraction {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // ═══════════════════════════════════════════════════════════════════
    // CONSTANTS (mirrored from ReturnPortalInteraction)
    // ═══════════════════════════════════════════════════════════════════

    /** Minimum time player must be in world before portal activates. */
    @Nonnull
    public static final Duration MINIMUM_TIME_IN_WORLD = Duration.ofSeconds(15L);

    /** Time after which warning message is shown. */
    @Nonnull
    public static final Duration WARNING_TIME = Duration.ofSeconds(4L);

    /** Codec for registration with Interaction.CODEC. */
    @Nonnull
    public static final BuilderCodec<RealmVictoryPortalInteraction> CODEC = BuilderCodec.builder(
            RealmVictoryPortalInteraction.class,
            RealmVictoryPortalInteraction::new,
            SimpleBlockInteraction.CODEC
        )
        .documentation("Removes realm HUDs before exiting instance via victory portal.")
        .build();

    // ═══════════════════════════════════════════════════════════════════
    // MESSAGES
    // ═══════════════════════════════════════════════════════════════════

    private static final Message MESSAGE_PORTALS_ATTUNING_TO_WORLD =
        Message.translation("server.portals.attuningToWorld");

    private static final Message MESSAGE_PORTALS_DEVICE_NOT_IN_PORTAL_WORLD =
        Message.translation("server.portals.device.notInPortalWorld");

    // ═══════════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════════

    public RealmVictoryPortalInteraction() {
    }

    // ═══════════════════════════════════════════════════════════════════
    // INTERACTION LOGIC
    // ═══════════════════════════════════════════════════════════════════

    @Override
    protected void interactWithBlock(
            @Nonnull World world,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull InteractionType type,
            @Nonnull InteractionContext context,
            @Nullable ItemStack itemInHand,
            @Nonnull Vector3i targetBlock,
            @Nonnull CooldownHandler cooldownHandler) {

        Ref<EntityStore> ref = context.getEntity();
        Player playerComponent = commandBuffer.getComponent(ref, Player.getComponentType());

        if (playerComponent == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        // Check minimum time in world (same as ReturnPortalInteraction)
        long elapsedNanosInWorld = playerComponent.getSinceLastSpawnNanos();
        if (elapsedNanosInWorld < MINIMUM_TIME_IN_WORLD.toNanos()) {
            if (elapsedNanosInWorld > WARNING_TIME.toNanos()) {
                playerComponent.sendMessage(MESSAGE_PORTALS_ATTUNING_TO_WORLD);
            }
            context.getState().state = InteractionState.Failed;
            return;
        }

        // Check portal world resource exists (same as ReturnPortalInteraction)
        PortalWorld portalWorld = commandBuffer.getResource(PortalWorld.getResourceType());
        if (!portalWorld.exists()) {
            playerComponent.sendMessage(MESSAGE_PORTALS_DEVICE_NOT_IN_PORTAL_WORLD);
            context.getState().state = InteractionState.Failed;
            return;
        }

        // ═══════════════════════════════════════════════════════════════
        // CRITICAL: Remove HUDs BEFORE teleport
        // ═══════════════════════════════════════════════════════════════
        // This is the key difference from ReturnPortalInteraction.
        // We're still on the realm world thread, so sync removal works.
        // Use PlayerRef.getUuid() instead of deprecated Entity.getUuid()
        PlayerRef playerRefComponent = commandBuffer.getComponent(ref, PlayerRef.getComponentType());
        if (playerRefComponent == null) {
            LOGGER.atWarning().log("Player entity missing PlayerRef component");
            context.getState().state = InteractionState.Failed;
            return;
        }
        UUID playerId = playerRefComponent.getUuid();
        try {
            if (RealmsManager.isInitialized()) {
                RealmsManager.get().getHudManager().removeAllHudsForPlayerSync(playerId);
                LOGGER.atFine().log("Removed realm HUDs for player %s before victory portal teleport",
                    playerId.toString().substring(0, 8));
            }
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to remove HUDs for player %s before teleport",
                playerId.toString().substring(0, 8));
            // Continue with teleport even if HUD removal fails
        }

        // Delegate to standard instance exit (same as ReturnPortalInteraction)
        InstancesPlugin.exitInstance(ref, commandBuffer);
    }

    @Override
    protected void simulateInteractWithBlock(
            @Nonnull InteractionType type,
            @Nonnull InteractionContext context,
            @Nullable ItemStack itemInHand,
            @Nonnull World world,
            @Nonnull Vector3i targetBlock) {
        // Client-side simulation always fails (server handles it)
        context.getState().state = InteractionState.Failed;
    }

    @Nonnull
    @Override
    public WaitForDataFrom getWaitForDataFrom() {
        return WaitForDataFrom.Server;
    }

    @Nonnull
    @Override
    public String toString() {
        return "RealmVictoryPortalInteraction{} " + super.toString();
    }
}
