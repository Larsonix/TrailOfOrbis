package io.github.larsonix.trailoforbis.maps.completion.interactions;

import com.hypixel.hytale.builtin.portals.interactions.EnterPortalInteraction;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Fixed version of {@link EnterPortalInteraction} for Portal_Device CollisionEnter.
 *
 * <p>Vanilla {@code EnterPortalInteraction.simulateInteractWithBlock()} is empty,
 * but {@code interactWithBlock()} sets {@link InteractionState#Failed} when a
 * non-player entity (mob/NPC) enters the collision zone. This mismatch causes
 * the simulation and server operation counters to desync, producing:
 * <pre>
 * IllegalStateException: Simulation and server tick are not in sync (operation position).
 * Root: **Portal_Device_State_Definitions_Active_Interactions_CollisionEnter
 * Counter: 2 vs 1
 * </pre>
 *
 * <p>The fix: override {@code simulateInteractWithBlock()} to set {@code Failed},
 * mirroring the server-side behavior. This is the same fix applied to
 * {@link RealmVictoryPortalInteraction} for victory portals.
 *
 * <p>All server-side teleport logic is inherited unchanged from {@code EnterPortalInteraction}.
 *
 * @see EnterPortalInteraction
 * @see RealmVictoryPortalInteraction
 */
public class RealmEntryPortalInteraction extends EnterPortalInteraction {

    @Nonnull
    public static final BuilderCodec<RealmEntryPortalInteraction> CODEC = BuilderCodec.builder(
            RealmEntryPortalInteraction.class,
            RealmEntryPortalInteraction::new,
            EnterPortalInteraction.CODEC
        )
        .documentation("Fixed EnterPortalInteraction with proper simulation-side failure for non-player entities.")
        .build();

    @Override
    protected void simulateInteractWithBlock(
            @Nonnull InteractionType type,
            @Nonnull InteractionContext context,
            @Nullable ItemStack itemInHand,
            @Nonnull World world,
            @Nonnull Vector3i targetBlock) {
        // Mirror the server-side failure path for non-player entities.
        // Without this, the simulation counter doesn't advance on failure,
        // causing "Counter: 2 vs 1" desync crash in InteractionManager.
        context.getState().state = InteractionState.Failed;
    }

    @Nonnull
    @Override
    public String toString() {
        return "RealmEntryPortalInteraction{} " + super.toString();
    }
}
