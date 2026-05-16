package io.github.larsonix.trailoforbis.maps;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.RemovalBehavior;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.mobs.speed.RPGApplicationEffects;
import io.github.larsonix.trailoforbis.mobs.speed.RPGEntityEffect;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Grants temporary invincibility to players entering realms until they move.
 *
 * <p>Uses {@link EffectControllerComponent#setInvulnerable(boolean)} to toggle
 * the invincibility flag on the player's existing effect controller. This is a
 * simple property mutation — no archetype migration, no ECS component add/remove.
 * Hytale's vanilla damage pipeline checks {@code isInvulnerable()} in
 * {@code DamageSystems} before processing any damage.
 *
 * <p>Protection is revoked when the player takes their first voluntary movement
 * (WASD), or after a hard timeout as a safety net against permanent god mode.
 *
 * <p>Movement is detected by polling the player's {@link TransformComponent}
 * position via a scheduled timer (every 200ms) that dispatches checks to the
 * world thread. Looking around, opening inventory, or vertical jitter from
 * standing still do NOT count as movement.
 *
 * <h2>Lifecycle</h2>
 * <pre>
 * PlayerReadyEvent (realm) → grant()
 *   ↓
 * ScheduledExecutorService (every 200ms):
 *   → world.execute() → read position → moved? → revoke + cancel timer
 *   → timeout exceeded? → revoke + cancel timer
 *   → neither → wait for next poll
 *   ↓
 * DrainPlayerFromWorldEvent / disconnect → cleanup() + cancel timer
 * </pre>
 *
 * <h2>Double-ClientReady Protection</h2>
 * <p>Hytale fires TWO {@code ClientReady} packets per world transition. On
 * remote connections, the second arrives 30+ seconds later. Each triggers
 * {@code PlayerReadyEvent} → {@code grant()}. Without protection, the second
 * grant re-enables invincibility after the player has already moved and had
 * their protection revoked. The {@code grantedDuringVisit} set tracks all
 * players who have been granted protection during this realm visit, preventing
 * re-grants even after revocation.
 *
 * <h2>Why EffectControllerComponent instead of Invulnerable component?</h2>
 * <p>Adding/removing the {@code Invulnerable} ECS component causes archetype
 * migration — the entity is physically moved between ArchetypeChunks. A second
 * removal attempt (from the verification retry, or removeComponent on a
 * component that's already gone) can corrupt the entity's internal chunk index,
 * causing {@code IndexOutOfBoundsException} in every Hytale subsystem that
 * accesses the player entity, crashing the entire realm world thread.
 * {@code setInvulnerable(boolean)} mutates a field on an existing component —
 * zero archetype changes, zero corruption risk. This is the same pattern used
 * by Hytale's own Creative mode.
 *
 * <h2>Why Not Recursive world.execute()?</h2>
 * <p>An earlier implementation used recursive {@code world.execute()} for polling.
 * This created an infinite loop within a single tick because Hytale's task queue
 * processes tasks added during the current processing pass — the re-schedule ran
 * immediately, never yielding for the player's position to update between ticks.
 */
public class RealmSpawnProtection {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /**
     * Minimum horizontal distance (blocks) to count as voluntary movement.
     * A single WASD step covers ~0.3 blocks; 0.5 avoids float jitter.
     */
    private static final double MOVEMENT_THRESHOLD = 0.5;

    /**
     * Hard timeout in milliseconds. Protection is revoked after this duration
     * regardless of movement, preventing permanent invincibility exploits.
     */
    private static final long TIMEOUT_MS = 60_000; // 60 seconds

    /** Polling interval for movement checks. */
    private static final long CHECK_INTERVAL_MS = 200;

    /** Visual-only EntityEffect ID for spawn shield icon. */
    private static final String SPAWN_SHIELD_EFFECT_ID = "rpg_spawn_shield";

    /** Active protections keyed by player UUID. */
    private final ConcurrentHashMap<UUID, ProtectionEntry> protectedPlayers = new ConcurrentHashMap<>();

    /**
     * Players who have been granted protection during their current realm visit.
     *
     * <p>Unlike {@link #protectedPlayers} (which is cleared on revoke), this set
     * persists until the player leaves the world via {@link #cleanup(UUID)}. This
     * prevents the double-ClientReady re-grant: Hytale fires two ClientReady packets
     * per world transition, and the second can arrive 30+ seconds after the first.
     * By that time the player has moved and protection was revoked — but without
     * this set, the second ClientReady would re-grant invincibility.
     */
    private final Set<UUID> grantedDuringVisit = ConcurrentHashMap.newKeySet();

    /** Scheduled polling tasks per player. */
    private final ConcurrentHashMap<UUID, ScheduledFuture<?>> pollingTasks = new ConcurrentHashMap<>();

    /** Daemon scheduler for position polling. Separate from world thread. */
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "RealmSpawnProtection-Poll");
        t.setDaemon(true);
        return t;
    });

    /**
     * Immutable snapshot of a player's spawn protection state.
     *
     * @param spawnX Horizontal spawn position (X)
     * @param spawnZ Horizontal spawn position (Z)
     * @param grantedAtMs System.currentTimeMillis() when protection was granted
     */
    private record ProtectionEntry(double spawnX, double spawnZ, long grantedAtMs) {}

    // ═══════════════════════════════════════════════════════════════════
    // EFFECT REGISTRATION (must be called during plugin init)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Creates and registers the spawn shield visual effect with the asset store.
     *
     * <p>Must be called during plugin initialization (never during a world tick).
     * The effect is visual-only: icon + buff frame, no stat/speed/tint changes.
     */
    public void registerEffect() {
        RPGEntityEffect effect = new RPGEntityEffect(SPAWN_SHIELD_EFFECT_ID);
        RPGApplicationEffects emptyApp = RPGApplicationEffects.create();
        effect.setApplicationEffects(emptyApp);
        effect.setStatusEffectIcon("Icons/ItemsGenerated/Ingredient_Motes_Light.png");
        effect.setDebuff(false);
        effect.setInfinite(false);
        effect.setDuration(60f);
        effect.setOverlapBehavior(OverlapBehavior.OVERWRITE);
        effect.setRemovalBehavior(RemovalBehavior.COMPLETE);

        EntityEffect.getAssetStore().loadAssets("trailoforbis", List.of(effect));
        LOGGER.atInfo().log("Registered spawn shield visual effect: %s", SPAWN_SHIELD_EFFECT_ID);
    }

    // ═══════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════��═══════════════════════════���═══════════════════

    /**
     * Grants spawn protection to a player entering a realm.
     *
     * <p>Sets {@code EffectControllerComponent.setInvulnerable(true)} on the
     * player entity, records their spawn position, and starts a position polling
     * timer on a separate scheduler thread.
     *
     * <p>Must be called when the player is already in the world (after
     * {@code PlayerReadyEvent}).
     *
     * @param playerId The player's UUID
     * @param world The realm world the player entered
     */
    public void grant(@Nonnull UUID playerId, @Nonnull World world) {
        // Prevent re-grant from duplicate ClientReady packets.
        // grantedDuringVisit catches already-revoked protection (player moved, but
        // second ClientReady fires 30+s later on remote connections).
        // protectedPlayers catches still-active protection (hasn't moved yet).
        if (grantedDuringVisit.contains(playerId)) {
            LOGGER.atInfo().log("Spawn protection already granted this visit for %s (post-revoke duplicate ClientReady), skipping",
                playerId.toString().substring(0, 8));
            return;
        }
        if (protectedPlayers.containsKey(playerId)) {
            LOGGER.atInfo().log("Spawn protection still active for %s, skipping grant",
                playerId.toString().substring(0, 8));
            return;
        }

        world.execute(() -> {
            try {
                PlayerRef freshRef = Universe.get().getPlayer(playerId);
                if (freshRef == null) {
                    LOGGER.atWarning().log("Spawn protection grant: player %s not found in Universe",
                        playerId.toString().substring(0, 8));
                    return;
                }

                Ref<EntityStore> entityRef = freshRef.getReference();
                if (entityRef == null || !entityRef.isValid()) {
                    LOGGER.atWarning().log("Spawn protection grant: entity ref invalid for %s",
                        playerId.toString().substring(0, 8));
                    return;
                }

                Store<EntityStore> store = world.getEntityStore().getStore();

                // Read the player's ACTUAL position — don't assume (0,0)
                double spawnX = 0.0;
                double spawnZ = 0.0;
                TransformComponent transform = store.getComponent(entityRef, TransformComponent.getComponentType());
                if (transform != null) {
                    Vector3d pos = transform.getPosition();
                    spawnX = pos.getX();
                    spawnZ = pos.getZ();
                }

                // Set invulnerable via EffectControllerComponent — simple property
                // mutation, no archetype migration, no ECS corruption risk.
                EffectControllerComponent effectController = store.getComponent(
                    entityRef, EffectControllerComponent.getComponentType());
                if (effectController == null) {
                    LOGGER.atWarning().log("Spawn protection grant: no EffectControllerComponent on %s",
                        playerId.toString().substring(0, 8));
                    return;
                }
                effectController.setInvulnerable(true);

                // Apply visual-only spawn shield icon (buff frame + countdown ring)
                applyShieldIcon(entityRef, effectController, store);

                // Record protection state and mark as granted this visit
                ProtectionEntry entry = new ProtectionEntry(spawnX, spawnZ, System.currentTimeMillis());
                protectedPlayers.put(playerId, entry);
                grantedDuringVisit.add(playerId);

                LOGGER.atInfo().log("Spawn protection GRANTED for player %s at (%.1f, %.1f) — invincible until movement or %ds timeout",
                    playerId.toString().substring(0, 8), spawnX, spawnZ, TIMEOUT_MS / 1000);

                // Start polling timer on the scheduler thread (NOT recursive world.execute)
                startPolling(playerId, world);
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Failed to grant spawn protection for %s",
                    playerId.toString().substring(0, 8));
                protectedPlayers.remove(playerId);
                // Keep grantedDuringVisit entry to prevent retry loops from
                // subsequent ClientReady events hitting the same failure.
            }
        });
    }

    /**
     * Checks if a player currently has spawn protection.
     *
     * @param playerId The player's UUID
     * @return true if the player is currently protected
     */
    public boolean isProtected(@Nonnull UUID playerId) {
        return protectedPlayers.containsKey(playerId);
    }

    /**
     * Cleans up protection for a player leaving the realm world.
     *
     * <p>Called from {@code DrainPlayerFromWorldEvent} and disconnect handlers.
     * Clears the invulnerability flag if the player was still protected, then
     * removes all tracking state.
     *
     * @param playerId The player's UUID
     * @param world The realm world (may be null if unavailable during disconnect)
     */
    public void cleanup(@Nonnull UUID playerId, @Nonnull World world) {
        ProtectionEntry removed = protectedPlayers.remove(playerId);
        boolean wasGranted = grantedDuringVisit.remove(playerId);
        cancelPolling(playerId);

        // Best-effort: clear the invulnerability flag if protection was still active.
        // During DrainPlayerFromWorldEvent the entity is still valid — safe to access.
        if (removed != null) {
            try {
                PlayerRef freshRef = Universe.get().getPlayer(playerId);
                if (freshRef != null) {
                    Ref<EntityStore> entityRef = freshRef.getReference();
                    if (entityRef != null && entityRef.isValid()) {
                        Store<EntityStore> store = world.getEntityStore().getStore();
                        EffectControllerComponent effectController = store.getComponent(
                            entityRef, EffectControllerComponent.getComponentType());
                        if (effectController != null) {
                            effectController.setInvulnerable(false);
                            removeShieldIcon(entityRef, effectController, store);
                        }
                    }
                }
            } catch (Exception e) {
                // Entity may be in transition — flag will be cleared when entity is destroyed
                LOGGER.atFine().log("Could not clear invulnerability flag for %s during cleanup: %s",
                    playerId.toString().substring(0, 8), e.getMessage());
            }
        }

        if (removed != null || wasGranted) {
            LOGGER.atFine().log("Spawn protection cleaned up for %s (world exit/disconnect) — was active: %s, was granted: %s",
                playerId.toString().substring(0, 8), removed != null, wasGranted);
        }
    }

    /**
     * Cleans up protection without world access (disconnect fallback).
     *
     * <p>Called when no world reference is available (e.g., player disconnected
     * while not in any world). Only clears tracking maps — the entity's
     * EffectController state is irrelevant since the entity is being destroyed.
     *
     * @param playerId The player's UUID
     */
    public void cleanup(@Nonnull UUID playerId) {
        protectedPlayers.remove(playerId);
        grantedDuringVisit.remove(playerId);
        cancelPolling(playerId);
    }

    /**
     * Shuts down all active protections. Called on plugin disable.
     */
    public void shutdown() {
        int activeCount = protectedPlayers.size();
        int visitCount = grantedDuringVisit.size();
        protectedPlayers.clear();
        grantedDuringVisit.clear();
        for (ScheduledFuture<?> task : pollingTasks.values()) {
            task.cancel(false);
        }
        pollingTasks.clear();
        scheduler.shutdown();
        if (activeCount > 0 || visitCount > 0) {
            LOGGER.atInfo().log("Spawn protection shutdown — cleared %d active, %d visit-tracked protections",
                activeCount, visitCount);
        }
    }

    // ════════════════════════════════════════════════════════════��══════
    // POSITION POLLING (scheduler thread → world thread dispatch)
    // ═══════════════��═════════════════════════��═════════════════════════

    /**
     * Starts a periodic polling timer that checks the player's position.
     *
     * <p>The timer runs on a {@link ScheduledExecutorService} (daemon thread),
     * dispatching each check to the world thread via {@code world.execute()}.
     * This avoids the infinite-loop bug from recursive {@code world.execute()}.
     */
    private void startPolling(@Nonnull UUID playerId, @Nonnull World world) {
        ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(() -> {
            try {
                dispatchCheck(playerId, world);
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Spawn protection poll error for %s",
                    playerId.toString().substring(0, 8));
            }
        }, CHECK_INTERVAL_MS, CHECK_INTERVAL_MS, TimeUnit.MILLISECONDS);
        pollingTasks.put(playerId, task);
    }

    /**
     * Cancels the polling timer for a player.
     */
    private void cancelPolling(@Nonnull UUID playerId) {
        ScheduledFuture<?> task = pollingTasks.remove(playerId);
        if (task != null) {
            task.cancel(false);
        }
    }

    /**
     * Dispatches a single position check to the world thread.
     * Called by the scheduler thread every {@value CHECK_INTERVAL_MS}ms.
     */
    private void dispatchCheck(@Nonnull UUID playerId, @Nonnull World world) {
        // Quick bail-outs on scheduler thread (no world thread dispatch needed)
        ProtectionEntry entry = protectedPlayers.get(playerId);
        if (entry == null) {
            cancelPolling(playerId);
            return;
        }

        if (!world.isAlive()) {
            protectedPlayers.remove(playerId);
            cancelPolling(playerId);
            return;
        }

        // Check timeout on scheduler thread (no world access needed)
        long elapsed = System.currentTimeMillis() - entry.grantedAtMs();
        if (elapsed >= TIMEOUT_MS) {
            // Revoke on world thread
            world.execute(() -> revoke(playerId, world, "timeout (" + (TIMEOUT_MS / 1000) + "s)"));
            cancelPolling(playerId);
            return;
        }

        // Position check requires world thread access
        world.execute(() -> {
            // Re-check entry (might have been cleaned up between schedule and execution)
            if (!protectedPlayers.containsKey(playerId)) {
                return;
            }

            PlayerRef freshRef = Universe.get().getPlayer(playerId);
            if (freshRef == null) {
                protectedPlayers.remove(playerId);
                cancelPolling(playerId);
                return;
            }

            Ref<EntityStore> entityRef = freshRef.getReference();
            if (entityRef == null || !entityRef.isValid()) {
                return; // Entity not ready — wait for next poll
            }

            Store<EntityStore> store = world.getEntityStore().getStore();
            TransformComponent transform = store.getComponent(entityRef, TransformComponent.getComponentType());
            if (transform == null) {
                return; // Transform not ready — wait for next poll
            }

            Vector3d pos = transform.getPosition();
            double dx = pos.getX() - entry.spawnX();
            double dz = pos.getZ() - entry.spawnZ();
            double horizontalDistSq = dx * dx + dz * dz;

            if (horizontalDistSq > MOVEMENT_THRESHOLD * MOVEMENT_THRESHOLD) {
                revoke(playerId, world, String.format("movement (%.2f blocks)", Math.sqrt(horizontalDistSq)));
                cancelPolling(playerId);
            }
        });
    }

    /**
     * Revokes spawn protection: clears the invulnerability flag and untracks.
     *
     * <p>Must be called on the world thread.
     */
    private void revoke(@Nonnull UUID playerId, @Nonnull World world, @Nonnull String reason) {
        ProtectionEntry removed = protectedPlayers.remove(playerId);
        if (removed == null) {
            return; // Already revoked
        }

        // Clear invulnerability flag — simple property mutation, always safe
        boolean flagCleared = false;
        try {
            PlayerRef freshRef = Universe.get().getPlayer(playerId);
            if (freshRef != null) {
                Ref<EntityStore> entityRef = freshRef.getReference();
                if (entityRef != null && entityRef.isValid()) {
                    Store<EntityStore> store = world.getEntityStore().getStore();
                    EffectControllerComponent effectController = store.getComponent(
                        entityRef, EffectControllerComponent.getComponentType());
                    if (effectController != null) {
                        effectController.setInvulnerable(false);
                        removeShieldIcon(entityRef, effectController, store);
                        flagCleared = true;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to clear invulnerability flag for %s",
                playerId.toString().substring(0, 8));
        }

        long durationMs = System.currentTimeMillis() - removed.grantedAtMs();
        LOGGER.atInfo().log("Spawn protection REVOKED for player %s — reason: %s (after %.1fs, flag cleared: %s)",
            playerId.toString().substring(0, 8), reason, durationMs / 1000.0, flagCleared);
    }

    // ═══════════════════════════════════════════════════════════════════
    // SPAWN SHIELD ICON HELPERS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Applies the spawn shield visual icon to the player.
     * Visual-only — does NOT grant invulnerability (that's done separately).
     */
    private void applyShieldIcon(@Nonnull Ref<EntityStore> entityRef,
                                 @Nonnull EffectControllerComponent effectController,
                                 @Nonnull Store<EntityStore> store) {
        int idx = EntityEffect.getAssetMap().getIndex(SPAWN_SHIELD_EFFECT_ID);
        if (idx == Integer.MIN_VALUE) return;
        EntityEffect effect = EntityEffect.getAssetMap().getAsset(SPAWN_SHIELD_EFFECT_ID);
        if (effect == null) return;
        effectController.addEffect(entityRef, effect, 60f, OverlapBehavior.OVERWRITE, store);
    }

    /**
     * Removes the spawn shield visual icon from the player.
     */
    private void removeShieldIcon(@Nonnull Ref<EntityStore> entityRef,
                                  @Nonnull EffectControllerComponent effectController,
                                  @Nonnull Store<EntityStore> store) {
        int idx = EntityEffect.getAssetMap().getIndex(SPAWN_SHIELD_EFFECT_ID);
        if (idx == Integer.MIN_VALUE) return;
        effectController.removeEffect(entityRef, idx, store);
    }
}
