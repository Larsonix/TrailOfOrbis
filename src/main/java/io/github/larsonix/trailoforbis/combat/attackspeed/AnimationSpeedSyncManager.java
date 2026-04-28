package io.github.larsonix.trailoforbis.combat.attackspeed;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.ItemAnimation;
import com.hypixel.hytale.protocol.ItemPlayerAnimations;
import com.hypixel.hytale.protocol.UpdateType;
import com.hypixel.hytale.protocol.packets.assets.UpdateItemPlayerAnimations;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.attributes.ComputedStats;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Manages per-player animation speed overrides to visually sync swing animations
 * with the player's {@code attackSpeedPercent} stat.
 *
 * <p><b>Protocol approach (Tier 2):</b> Clone the weapon's {@code ItemPlayerAnimations}
 * config, patch {@code ItemAnimation.speed} on combat animations, and send the modified
 * packet via {@code UpdateItemPlayerAnimations} to that player only.
 *
 * <p><b>Lifecycle:</b>
 * <ul>
 *   <li>{@link #syncAnimationSpeed(UUID)} — called when stats change (equipment/slot swap)</li>
 *   <li>{@link PlayerReadyEvent} — apply speed when player enters a world</li>
 *   <li>{@link PlayerDisconnectEvent} — clean up state</li>
 *   <li>{@link #shutdown()} — restore all players and clear state</li>
 * </ul>
 */
public class AnimationSpeedSyncManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // 27 combat animation sets (melee + ranged + magic + defensive)
    // Excluded: tools (Pickaxe, Hatchet, Shovel, Hoe, Shears, Sickle)
    //           utility (Torch, Fire_Stick, Stick, Watering_Can, Block, Item, Default, Machinima_Camera)
    static final Set<String> COMBAT_ANIMATION_SETS = Set.of(
            "Sword", "Longsword", "Axe", "Battleaxe", "Spear", "Mace",
            "Club", "Club_Flail", "Dagger", "Daggers", "Daggers_Claw",
            "Daggers_Push", "Gloves",
            "Bow", "Shortbow", "Crossbow", "Crossbow_Heavy", "Handgun",
            "Rifle", "Throwing_Knife",
            "Staff", "Wand", "Spellbook",
            "Shield"
    );

    private static final int BURST_RESTORE_COUNT = 10;
    private static final long BURST_RESTORE_INTERVAL_MS = 120L;

    private final TrailOfOrbis plugin;
    private final AnimationSpeedSyncConfig config;
    private final ConcurrentHashMap<UUID, SyncState> playerStates = new ConcurrentHashMap<>();

    /**
     * Per-player state tracking for animation speed overrides.
     */
    private static class SyncState {
        float appliedMultiplier = 1.0f;
        String lastAnimSetId;
        int burstRestoresRemaining;
        long lastBurstRestoreMs;
    }

    public AnimationSpeedSyncManager(TrailOfOrbis plugin, AnimationSpeedSyncConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    /**
     * Registers event handlers with the event registry.
     *
     * @param eventRegistry The plugin's event registry
     */
    public void register(EventRegistry eventRegistry) {
        if (!config.enabled()) {
            LOGGER.atInfo().log("Animation speed sync disabled in config");
            return;
        }

        // PlayerReadyEvent is IEvent<String> (keyed by world name) → registerGlobal
        eventRegistry.registerGlobal(
                EventPriority.NORMAL,
                PlayerReadyEvent.class,
                this::onPlayerReady
        );

        // PlayerDisconnectEvent is IBaseEvent<Void> → regular register
        eventRegistry.register(
                EventPriority.EARLY,
                PlayerDisconnectEvent.class,
                this::onPlayerDisconnect
        );

        LOGGER.atInfo().log(
                "Animation speed sync registered (scale: %.2f, range: [%.1f, %.1f])",
                config.animationSpeedScale(), config.animationMinSpeed(), config.animationMaxSpeed()
        );
    }

    // ==================== PUBLIC API ====================

    /**
     * Syncs the animation speed for a player based on their current attack speed stat.
     *
     * <p>Called from {@code EquipmentChangeListener} and {@code WeaponSlotChangeSystem}
     * after stats are recalculated.
     *
     * @param uuid The player's UUID
     */
    public void syncAnimationSpeed(UUID uuid) {
        if (!config.enabled()) {
            return;
        }

        // Get the player's attack speed stat
        ComputedStats stats = plugin.getAttributeManager().getStats(uuid);
        if (stats == null) {
            return;
        }

        float attackSpeedPercent = stats.getAttackSpeedPercent();
        float multiplier = config.calculateMultiplier(attackSpeedPercent);

        // Resolve the player's held weapon animation set
        PlayerRef playerRef = Universe.get().getPlayer(uuid);
        if (playerRef == null) {
            return;
        }

        String animSetId = resolveHeldAnimationSetId(playerRef);

        SyncState state = playerStates.computeIfAbsent(uuid, k -> new SyncState());

        // If held item is not a combat weapon → restore any previous override
        if (animSetId == null || !isCombatAnimationSet(animSetId)) {
            if (state.lastAnimSetId != null) {
                restoreAnimations(playerRef, state.lastAnimSetId);
                startBurstRestore(uuid, state.lastAnimSetId);
                state.lastAnimSetId = null;
                state.appliedMultiplier = 1.0f;
            }
            return;
        }

        // If multiplier is effectively vanilla → restore
        if (Math.abs(multiplier - 1.0f) < 0.001f) {
            if (state.lastAnimSetId != null) {
                restoreAnimations(playerRef, state.lastAnimSetId);
                startBurstRestore(uuid, state.lastAnimSetId);
                state.lastAnimSetId = null;
                state.appliedMultiplier = 1.0f;
            }
            return;
        }

        // If same multiplier and same animation set → no-op
        if (Math.abs(multiplier - state.appliedMultiplier) < 0.001f
                && animSetId.equals(state.lastAnimSetId)) {
            return;
        }

        // If switching weapons → restore previous weapon's animations first
        if (state.lastAnimSetId != null && !animSetId.equals(state.lastAnimSetId)) {
            restoreAnimations(playerRef, state.lastAnimSetId);
        }

        // Apply the speed override
        applyAnimationSpeed(playerRef, animSetId, multiplier);

        // Update state
        state.appliedMultiplier = multiplier;
        state.lastAnimSetId = animSetId;
        state.burstRestoresRemaining = 0; // cancel any pending burst restore
    }

    /**
     * Restores all players to vanilla animation speed and clears all state.
     */
    public void shutdown() {
        for (var entry : playerStates.entrySet()) {
            UUID uuid = entry.getKey();
            SyncState state = entry.getValue();
            if (state.lastAnimSetId != null) {
                PlayerRef playerRef = Universe.get().getPlayer(uuid);
                if (playerRef != null) {
                    try {
                        restoreAnimations(playerRef, state.lastAnimSetId);
                    } catch (Exception e) {
                        LOGGER.atWarning().withCause(e).log(
                                "Failed to restore animations for %s during shutdown",
                                uuid.toString().substring(0, 8)
                        );
                    }
                }
            }
        }
        playerStates.clear();
        LOGGER.atInfo().log("Animation speed sync shut down, all players restored");
    }

    // ==================== EVENT HANDLERS ====================

    private void onPlayerReady(PlayerReadyEvent event) {
        Player player = event.getPlayer();
        World world = player.getWorld();
        if (world == null) {
            return;
        }

        world.execute(() -> {
            // Re-check ref validity on world thread (player may have disconnected)
            var ref = event.getPlayerRef();
            if (ref == null || !ref.isValid()) {
                return;
            }

            var store = ref.getStore();
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (playerRef == null) {
                return;
            }
            UUID uuid = playerRef.getUuid();

            // Check if player has attack speed stat ≠ 0
            ComputedStats stats = plugin.getAttributeManager().getStats(uuid);
            if (stats == null) {
                return;
            }

            float attackSpeedPercent = stats.getAttackSpeedPercent();
            if (Math.abs(attackSpeedPercent) < 0.001f) {
                return;
            }

            // Apply animation speed for held weapon (first-time send to new connection)
            float multiplier = config.calculateMultiplier(attackSpeedPercent);
            if (Math.abs(multiplier - 1.0f) < 0.001f) {
                return;
            }

            String animSetId = resolveHeldAnimationSetId(playerRef);
            if (animSetId == null || !isCombatAnimationSet(animSetId)) {
                return;
            }

            applyAnimationSpeed(playerRef, animSetId, multiplier);

            SyncState state = playerStates.computeIfAbsent(uuid, k -> new SyncState());
            state.appliedMultiplier = multiplier;
            state.lastAnimSetId = animSetId;

            LOGGER.atFine().log("Applied animation speed %.2fx for %s on world ready (weapon: %s)",
                    multiplier, uuid.toString().substring(0, 8), animSetId);
        });
    }

    private void onPlayerDisconnect(PlayerDisconnectEvent event) {
        PlayerRef playerRef = event.getPlayerRef();
        if (playerRef == null) {
            return;
        }
        // Clean up state — no need to send restore packets (player is leaving)
        playerStates.remove(playerRef.getUuid());
    }

    // ==================== PROTOCOL METHODS ====================

    /**
     * Clones the animation config for the given set, patches attack animations,
     * and sends the modified packet to the player.
     */
    private void applyAnimationSpeed(PlayerRef playerRef, String animSetId, float multiplier) {
        try {
            var configAssetMap = com.hypixel.hytale.server.core.asset.type.itemanimation.config
                    .ItemPlayerAnimations.getAssetMap();
            var animConfig = configAssetMap.getAsset(animSetId);
            if (animConfig == null) {
                LOGGER.atFine().log("No animation config found for set: %s", animSetId);
                return;
            }

            // CRITICAL: clone() to avoid corrupting the SoftReference-cached packet
            ItemPlayerAnimations clone = animConfig.toPacket().clone();

            int patchCount = 0;
            for (var entry : clone.animations.entrySet()) {
                if (isAttackAnimation(entry.getKey(), entry.getValue())) {
                    ItemAnimation anim = entry.getValue();
                    float original = anim.speed > 0 ? anim.speed : 1.0f;
                    anim.speed = original * multiplier;
                    patchCount++;
                }
            }

            if (patchCount == 0) {
                return;
            }

            PacketHandler connection = playerRef.getPacketHandler();
            connection.write(
                    new UpdateItemPlayerAnimations(UpdateType.AddOrUpdate, Map.of(animSetId, clone))
            );

            LOGGER.atFine().log("Applied %.2fx animation speed to %d anims in set %s for %s",
                    multiplier, patchCount, animSetId, playerRef.getUuid().toString().substring(0, 8));

        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log(
                    "Failed to apply animation speed for %s (set: %s)",
                    playerRef.getUuid().toString().substring(0, 8), animSetId
            );
        }
    }

    /**
     * Sends Remove + AddOrUpdate with the vanilla baseline to restore animations.
     */
    private void restoreAnimations(PlayerRef playerRef, String animSetId) {
        try {
            var configAssetMap = com.hypixel.hytale.server.core.asset.type.itemanimation.config
                    .ItemPlayerAnimations.getAssetMap();
            var animConfig = configAssetMap.getAsset(animSetId);
            if (animConfig == null) {
                return;
            }

            ItemPlayerAnimations baseline = animConfig.toPacket().clone();
            Map<String, ItemPlayerAnimations> baselineMap = Map.of(animSetId, baseline);

            PacketHandler connection = playerRef.getPacketHandler();
            connection.write(new UpdateItemPlayerAnimations(UpdateType.Remove, baselineMap));
            connection.write(new UpdateItemPlayerAnimations(UpdateType.AddOrUpdate, baselineMap));

            LOGGER.atFine().log("Restored vanilla animations for set %s on %s",
                    animSetId, playerRef.getUuid().toString().substring(0, 8));

        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log(
                    "Failed to restore animations for %s (set: %s)",
                    playerRef.getUuid().toString().substring(0, 8), animSetId
            );
        }
    }

    /**
     * Schedules a burst of restore packets to handle world-change desync.
     * Sends 10 Remove+AddOrUpdate pairs at 120ms intervals.
     */
    private void startBurstRestore(UUID uuid, String animSetId) {
        SyncState state = playerStates.get(uuid);
        if (state == null) {
            return;
        }

        state.burstRestoresRemaining = BURST_RESTORE_COUNT;
        state.lastBurstRestoreMs = 0;
        scheduleBurstRestoreTick(uuid, animSetId);
    }

    private void scheduleBurstRestoreTick(UUID uuid, String animSetId) {
        CompletableFuture.delayedExecutor(BURST_RESTORE_INTERVAL_MS, TimeUnit.MILLISECONDS)
                .execute(() -> {
                    try {
                        SyncState state = playerStates.get(uuid);
                        if (state == null || state.burstRestoresRemaining <= 0) {
                            return;
                        }

                        // If a new override was applied, stop the burst
                        if (state.lastAnimSetId != null) {
                            state.burstRestoresRemaining = 0;
                            return;
                        }

                        PlayerRef playerRef = Universe.get().getPlayer(uuid);
                        if (playerRef == null) {
                            state.burstRestoresRemaining = 0;
                            return;
                        }

                        try {
                            restoreAnimations(playerRef, animSetId);
                        } catch (Exception e) {
                            LOGGER.atFine().log("Burst restore failed for %s, stopping", uuid.toString().substring(0, 8));
                            state.burstRestoresRemaining = 0;
                            return;
                        }

                        state.burstRestoresRemaining--;
                        state.lastBurstRestoreMs = System.currentTimeMillis();

                        // Schedule next tick if more remain
                        if (state.burstRestoresRemaining > 0) {
                            scheduleBurstRestoreTick(uuid, animSetId);
                        }
                    } catch (Exception e) {
                        LOGGER.atSevere().withCause(e).log("Async operation failed: %s", e.getMessage());
                    }
                });
    }

    // ==================== HELPERS ====================

    /**
     * Resolves the animation set ID for the player's currently held item.
     *
     * @return The animation set ID (e.g. "Sword", "Bow"), or null if no item held
     */
    private String resolveHeldAnimationSetId(PlayerRef playerRef) {
        try {
            var ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) {
                return null;
            }
            var store = ref.getStore();
            com.hypixel.hytale.server.core.entity.entities.Player player =
                    store.getComponent(ref, com.hypixel.hytale.server.core.entity.entities.Player.getComponentType());
            if (player == null) {
                return null;
            }
            Inventory inventory = player.getInventory();
            if (inventory == null) {
                return null;
            }
            ItemStack heldItem = inventory.getItemInHand();
            if (heldItem == null) {
                return null;
            }
            Item item = heldItem.getItem();
            if (item == null || item == Item.UNKNOWN) {
                return null;
            }
            return item.getPlayerAnimationsId();
        } catch (Exception e) {
            LOGGER.atFine().log("Failed to resolve held animation set: %s", e.getMessage());
            return null;
        }
    }

    private static boolean isCombatAnimationSet(String animSetId) {
        return COMBAT_ANIMATION_SETS.contains(animSetId);
    }

    /**
     * Determines if an animation entry is a combat animation that should be speed-patched.
     * Uses an exclusion-first pattern to avoid modifying movement/idle animations.
     */
    private static boolean isAttackAnimation(String name, ItemAnimation anim) {
        String lower = name.toLowerCase();

        // Exclude non-combat animations
        if (lower.contains("idle") || lower.contains("walk") || lower.contains("run")
                || lower.contains("sprint") || lower.contains("jump") || lower.contains("fall")
                || lower.contains("swim") || lower.contains("equip") || lower.contains("hold")
                || lower.contains("block") || lower.contains("climb") || lower.contains("fly")
                || lower.contains("fluid") || lower.contains("interact") || lower.contains("mantle")
                || lower.contains("slide") || lower.contains("crouch")) {
            return false;
        }

        // Match combat animations
        if (lower.contains("swing") || lower.contains("stab") || lower.contains("slash")
                || lower.contains("spin") || lower.contains("strike") || lower.contains("bash")
                || lower.contains("charged") || lower.contains("charging") || lower.contains("attack")
                || lower.contains("combo") || lower.contains("shoot") || lower.contains("cast")
                || lower.contains("lunge") || lower.contains("pounce") || lower.contains("flurry")
                || lower.contains("razor") || lower.contains("kick") || lower.contains("throw")
                || lower.contains("guard") || lower.contains("mine") || lower.contains("reload")
                || lower.contains("dash") || lower.contains("backflip")) {
            return true;
        }

        // Match by animation file path
        String path = anim.thirdPerson != null ? anim.thirdPerson.toLowerCase() : "";
        return path.contains("/attacks/") || path.contains("/attack/");
    }
}
