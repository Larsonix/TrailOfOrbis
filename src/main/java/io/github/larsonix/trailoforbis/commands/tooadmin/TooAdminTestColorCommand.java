package io.github.larsonix.trailoforbis.commands.tooadmin;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.CombatTextUpdate;
import com.hypixel.hytale.protocol.Color;
import com.hypixel.hytale.protocol.EntityUIComponent;
import com.hypixel.hytale.protocol.UpdateType;
import com.hypixel.hytale.protocol.packets.assets.UpdateEntityUIComponents;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.modules.entityui.asset.CombatTextUIComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Tests 3 hacks for colored attacker combat text (CombatTextUpdate has no color field).
 *
 * <p>Usage: /tooadmin testcolor
 *
 * <p>Fires 4 floating damage numbers on the nearest tracked entity:
 * <ul>
 *   <li>100 = Baseline (always white)</li>
 *   <li>200 = Rich text markup test (colored if markup works)</li>
 *   <li>300 = EntityUI template swap to red (colored if Hack 1 works)</li>
 *   <li>400 = Ghost entity with blue template (colored if Hack 2 works)</li>
 * </ul>
 */
public final class TooAdminTestColorCommand extends AbstractPlayerCommand {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public TooAdminTestColorCommand() {
        super("testcolor", "Test colored combat text hacks");
        this.addAliases("tc");
    }

    @Override
    protected void execute(
        @Nonnull CommandContext context,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull PlayerRef sender,
        @Nonnull World world
    ) {
        // ── Phase 0: Setup ──────────────────────────────────────────────

        // Get player's EntityViewer (what entities the player can see)
        EntityTrackerSystems.EntityViewer viewer =
            store.getComponent(ref, EntityTrackerSystems.EntityViewer.getComponentType());
        if (viewer == null) {
            sender.sendMessage(Message.raw("No EntityViewer component. Are you in a world?").color(MessageColors.ERROR));
            return;
        }

        // Get player position for distance comparison
        TransformComponent playerTransform = store.getComponent(ref, TransformComponent.getComponentType());
        if (playerTransform == null) {
            sender.sendMessage(Message.raw("Could not get player position.").color(MessageColors.ERROR));
            return;
        }
        Vector3d playerPos = playerTransform.getPosition();

        // Find nearest tracked entity (skip self)
        Ref<EntityStore> targetRef = null;
        double closestDistSq = Double.MAX_VALUE;
        for (Ref<EntityStore> visibleRef : viewer.visible) {
            if (visibleRef.equals(ref)) continue;
            TransformComponent transform = store.getComponent(visibleRef, TransformComponent.getComponentType());
            if (transform == null) continue;
            double distSq = playerPos.distanceSquaredTo(transform.getPosition());
            if (distSq < closestDistSq) {
                closestDistSq = distSq;
                targetRef = visibleRef;
            }
        }

        if (targetRef == null) {
            sender.sendMessage(Message.raw("No tracked entities nearby. Stand near a mob.").color(MessageColors.ERROR));
            return;
        }

        // Find CombatText template in the EntityUI asset map
        var serverAssetMap = com.hypixel.hytale.server.core.modules.entityui.asset.EntityUIComponent.getAssetMap();
        int foundIndex = -1;
        EntityUIComponent foundPacket = null;
        for (int i = 0; i < serverAssetMap.getNextIndex(); i++) {
            var asset = serverAssetMap.getAsset(i);
            if (asset instanceof CombatTextUIComponent) {
                foundIndex = i;
                foundPacket = asset.toPacket();
                break;
            }
        }
        if (foundIndex == -1 || foundPacket == null) {
            sender.sendMessage(Message.raw("CombatText template not found in EntityUI asset map!").color(MessageColors.ERROR));
            return;
        }
        final int combatTextIndex = foundIndex;
        final EntityUIComponent originalPacket = foundPacket;

        // Clone and create colored variants
        EntityUIComponent redPacket = originalPacket.clone();
        redPacket.combatTextColor = new Color((byte) 0xFF, (byte) 0x44, (byte) 0x44);

        EntityUIComponent bluePacket = originalPacket.clone();
        bluePacket.combatTextColor = new Color((byte) 0x44, (byte) 0xAA, (byte) 0xFF);

        int maxId = serverAssetMap.getNextIndex();

        // Spawn ghost entity at target position + 0.5Y offset (for Hack 2 test)
        TransformComponent targetTransform = store.getComponent(targetRef, TransformComponent.getComponentType());
        Vector3d targetPos = targetTransform != null ? targetTransform.getPosition() : playerPos;
        Vector3d ghostPos = new Vector3d(targetPos.x, targetPos.y + 0.5, targetPos.z);

        Ref<EntityStore> ghostRef = spawnGhostEntity(store, ghostPos);
        if (ghostRef == null) {
            sender.sendMessage(Message.raw("Failed to spawn ghost entity for Test D.").color(MessageColors.ERROR));
            return;
        }

        sender.sendMessage(Message.empty()
            .insert(Message.raw("[TestColor] ").color(MessageColors.GOLD))
            .insert(Message.raw("Starting tests... Watch for floating numbers!").color(MessageColors.WHITE)));

        LOGGER.atInfo().log("TestColor: target=%s, combatTextIndex=%d, ghostRef=%s",
            targetRef, combatTextIndex, ghostRef);

        // ── Phase 1: Tests A+B (t+500ms) — Baseline + Rich Text ────────
        final Ref<EntityStore> fTarget = targetRef;
        final int fIndex = combatTextIndex;

        delayOnWorld(world, 500, () -> {
            EntityTrackerSystems.EntityViewer v =
                store.getComponent(ref, EntityTrackerSystems.EntityViewer.getComponentType());
            if (v == null || !v.visible.contains(fTarget)) return;

            // Test A: Baseline white — proves plumbing works
            v.queueUpdate(fTarget, new CombatTextUpdate(0f, "100"));

            // Test B: Rich text markup — tests if client parses inline color tags
            v.queueUpdate(fTarget, new CombatTextUpdate(0f, "<color=#FF6600>200</color>"));

            LOGGER.atInfo().log("TestColor Phase 1: Fired Tests A (100) and B (200)");
        });

        // ── Phase 2: Test C (t+800ms) — EntityUI Template Swap (Red) ───
        delayOnWorld(world, 800, () -> {
            EntityTrackerSystems.EntityViewer v =
                store.getComponent(ref, EntityTrackerSystems.EntityViewer.getComponentType());
            if (v == null || !v.visible.contains(fTarget)) return;

            // Send red template to ONLY this player (writeNoCache = immediate delivery)
            sender.getPacketHandler().writeNoCache(
                new UpdateEntityUIComponents(UpdateType.AddOrUpdate, maxId,
                    Map.of(fIndex, redPacket)));

            // Queue combat text — flushes next tracker tick, after red template is in place
            v.queueUpdate(fTarget, new CombatTextUpdate(0f, "300"));

            LOGGER.atInfo().log("TestColor Phase 2: Sent red template + queued Test C (300)");
        });

        // ── Phase 3: Test D (t+1100ms) — Ghost Entity with Blue Template ─
        delayOnWorld(world, 1100, () -> {
            EntityTrackerSystems.EntityViewer v =
                store.getComponent(ref, EntityTrackerSystems.EntityViewer.getComponentType());
            if (v == null) return;

            // Send blue template (overwrites red — but "300" already flushed last tick)
            sender.getPacketHandler().writeNoCache(
                new UpdateEntityUIComponents(UpdateType.AddOrUpdate, maxId,
                    Map.of(fIndex, bluePacket)));

            // Queue on ghost entity (if it's being tracked by the player)
            if (v.visible.contains(ghostRef)) {
                v.queueUpdate(ghostRef, new CombatTextUpdate(0f, "400"));
                LOGGER.atInfo().log("TestColor Phase 3: Sent blue template + queued Test D (400) on ghost");
            } else {
                sender.sendMessage(Message.empty()
                    .insert(Message.raw("[TestColor] ").color(MessageColors.GOLD))
                    .insert(Message.raw("Ghost entity not tracked yet! Test D (400) skipped.").color(MessageColors.ERROR)));
                LOGGER.atInfo().log("TestColor Phase 3: Ghost not in visible set — Test D skipped");
            }
        });

        // ── Phase 4: Cleanup (t+1500ms) ─────────────────────────────────
        delayOnWorld(world, 1500, () -> {
            // Revert template back to original (white)
            sender.getPacketHandler().writeNoCache(
                new UpdateEntityUIComponents(UpdateType.AddOrUpdate, maxId,
                    Map.of(fIndex, originalPacket)));

            // Despawn ghost entity
            store.removeEntity(ghostRef, RemoveReason.REMOVE);

            // Report results
            sender.sendMessage(Message.empty()
                .insert(Message.raw("[TestColor] ").color(MessageColors.GOLD))
                .insert(Message.raw("Done! Check which numbers were colored:").color(MessageColors.WHITE)));
            sender.sendMessage(Message.raw("  100 = Baseline (should always be white)").color(MessageColors.WHITE));
            sender.sendMessage(Message.raw("  200 = Rich text (colored = markup works!)").color(MessageColors.WHITE));
            sender.sendMessage(Message.raw("  300 = Template swap (colored = Hack 1 works!)").color(MessageColors.WHITE));
            sender.sendMessage(Message.raw("  400 = Ghost entity (colored = Hack 2 works!)").color(MessageColors.WHITE));

            LOGGER.atInfo().log("TestColor Phase 4: Cleanup complete, template reverted, ghost removed");
        });
    }

    /**
     * Spawns an invisible ghost entity at the given position for CombatText testing.
     *
     * <p>Uses the ProjectileComponent shell pattern (same as text holograms) — the entity
     * is invisible but gets tracked by the entity tracker, allowing CombatTextUpdate to
     * be queued on it.
     */
    private Ref<EntityStore> spawnGhostEntity(
        @Nonnull Store<EntityStore> store,
        @Nonnull Vector3d position
    ) {
        try {
            Holder<EntityStore> holder = store.getRegistry().newHolder();

            // Projectile shell — invisible entity
            ProjectileComponent projectile = new ProjectileComponent("Projectile");
            holder.putComponent(ProjectileComponent.getComponentType(), projectile);

            // Position
            holder.addComponent(TransformComponent.getComponentType(),
                new TransformComponent(position, new Vector3f(0f, 0f, 0f)));

            // Identity + network tracking
            holder.ensureComponent(UUIDComponent.getComponentType());

            // Initialize projectile before adding NetworkId
            if (projectile.getProjectile() == null) {
                projectile.initialize();
            }

            holder.addComponent(NetworkId.getComponentType(),
                new NetworkId(store.getExternalData().takeNextNetworkId()));

            return store.addEntity(holder, AddReason.SPAWN);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to spawn ghost entity for TestColor");
            return null;
        }
    }

    /**
     * Schedules a task on the world thread after a delay.
     *
     * <p>Uses {@link CompletableFuture#delayedExecutor} since Hytale's World
     * has no {@code executeLater} method. The delay runs on the common ForkJoinPool,
     * then {@code world.execute()} dispatches back to the world thread.
     */
    private static void delayOnWorld(@Nonnull World world, long delayMs, @Nonnull Runnable task) {
        CompletableFuture.delayedExecutor(delayMs, TimeUnit.MILLISECONDS)
            .execute(() -> {
                try {
                    world.execute(task);
                } catch (Exception e) {
                    LOGGER.atSevere().withCause(e).log("Async operation failed: %s", e.getMessage());
                }
            });
    }
}
