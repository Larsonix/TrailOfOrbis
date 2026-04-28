package io.github.larsonix.trailoforbis.maps.spawning;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.modules.entity.component.EntityScaleComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import it.unimi.dsi.fastutil.Pair;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.maps.components.RealmMobComponent;
import io.github.larsonix.trailoforbis.maps.core.RealmMapData;
import io.github.larsonix.trailoforbis.maps.modifiers.RealmModifier;
import io.github.larsonix.trailoforbis.maps.modifiers.RealmModifierType;
import io.github.larsonix.trailoforbis.maps.spawning.WeightedMob.MobClassification;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Service responsible for spawning NPCs in realm instances using Hytale's NPCPlugin API.
 *
 * <p>This service handles:
 * <ul>
 *   <li>Spawning NPCs via {@link NPCPlugin#spawnEntity}</li>
 *   <li>Attaching {@link RealmMobComponent} for realm tracking (with realm level)</li>
 *   <li>Thread-safe deferred spawning via pending spawn queue</li>
 * </ul>
 *
 * <h2>Stat Application Strategy</h2>
 * <p>This spawner does NOT apply stats directly. Instead, it attaches {@link RealmMobComponent}
 * with the realm level and modifiers. The {@code MobScalingSystem} detects realm mobs and
 * handles stat calculation + application AFTER {@code BalancingInitialisationSystem} creates
 * the {@code EntityStatMap}. This ensures stats are applied at the correct time.
 *
 * <h2>Thread Safety</h2>
 * <p>Spawns cannot occur during ECS store processing. Use {@link #queueSpawn} from
 * within systems, then process the queue with {@link #processPendingSpawns} from
 * the world tick thread.
 *
 * @see RealmMobSpawner
 * @see RealmMobComponent
 */
public class RealmEntitySpawner {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // ═══════════════════════════════════════════════════════════════════════
    // DEPENDENCIES
    // ═══════════════════════════════════════════════════════════════════════

    private final TrailOfOrbis plugin;

    // ═══════════════════════════════════════════════════════════════════════
    // PENDING SPAWN QUEUE
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Thread-safe queue for spawn requests that must be deferred.
     * <p>Used when spawning from within ECS system processing to avoid
     * "Store is currently processing!" errors.
     */
    private final Queue<PendingRealmSpawn> pendingSpawns = new ConcurrentLinkedQueue<>();

    // ═══════════════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Creates a new realm entity spawner.
     *
     * @param plugin The TrailOfOrbis plugin instance
     */
    public RealmEntitySpawner(@Nonnull TrailOfOrbis plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // DIRECT SPAWNING
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Spawns an NPC directly in the realm.
     *
     * <p><b>IMPORTANT:</b> This method must be called from the world thread and
     * NOT during ECS store processing. Use {@link #queueSpawn} if calling from
     * within an ECS system.
     *
     * @param world          The world to spawn in
     * @param request        The spawn request details
     * @return The spawn result containing entity reference, or failure info
     */
    @Nonnull
    public SpawnResult spawnNow(@Nonnull World world, @Nonnull SpawnRequest request) {
        Objects.requireNonNull(world, "world cannot be null");
        Objects.requireNonNull(request, "request cannot be null");

        NPCPlugin npcPlugin = NPCPlugin.get();
        if (npcPlugin == null) {
            LOGGER.atSevere().log("NPCPlugin not available - cannot spawn realm mobs");
            return SpawnResult.failed("NPCPlugin not available");
        }

        // Validate the NPC type exists
        int roleIndex = npcPlugin.getIndex(request.mobTypeId());
        if (roleIndex < 0) {
            LOGGER.atWarning().log("Unknown NPC type: %s", request.mobTypeId());
            return SpawnResult.failed("Unknown NPC type: " + request.mobTypeId());
        }

        // Get component types
        ComponentType<EntityStore, RealmMobComponent> realmMobType = plugin.getRealmMobComponentType();
        if (realmMobType == null) {
            LOGGER.atSevere().log("RealmMobComponent type not registered");
            return SpawnResult.failed("RealmMobComponent not registered");
        }

        Store<EntityStore> store = world.getEntityStore().getStore();

        // Calculate stat multipliers from realm modifiers and player count
        StatModifiers statMods = calculateStatModifiers(request.mapData(), request.playerCount());

        try {
            // Random rotation
            float yaw = ThreadLocalRandom.current().nextFloat() * 360f;
            Vector3f rotation = new Vector3f(0f, yaw, 0f);

            // Spawn with pre-spawn callback to attach RealmMobComponent ONLY
            // MobScalingSystem will handle MobScalingComponent + stat application
            // after BalancingInitialisationSystem creates EntityStatMap
            Pair<Ref<EntityStore>, NPCEntity> result = npcPlugin.spawnEntity(
                store,
                roleIndex,
                request.position(),
                rotation,
                null, // No custom model
                (npc, holder, entityStore) -> {
                    // Pre-spawn callback: Attach RealmMobComponent with realm level
                    // DO NOT attach MobScalingComponent or apply stats here!
                    // EntityStatMap doesn't exist yet at this point.
                    attachRealmMobComponent(
                        holder,
                        realmMobType,
                        request.realmId(),
                        request.waveNumber(),
                        request.isReinforcement(),
                        request.isElite(),
                        request.classification(),
                        statMods,
                        request.mapData().level()  // Pass realm level
                    );

                    // Apply visual scale for elite/boss mobs
                    applyMobScale(holder, request.isElite(), request.classification());

                    // Leash mob to its own spawn position for natural local roaming.
                    // The NPC's default LeashDistance (20 blocks) keeps it near where it spawned.
                    // Arena boundary enforcement (in RealmMobSpawner.enforceArenaBoundaries)
                    // handles the hard containment — leash radius can't be set at runtime.
                    npc.setLeashPoint(request.position());
                },
                null // No post-spawn callback needed
            );

            if (result == null) {
                LOGGER.atWarning().log("NPCPlugin.spawnEntity returned null for %s", request.mobTypeId());
                return SpawnResult.failed("Spawn returned null");
            }

            Ref<EntityStore> entityRef = result.first();
            if (entityRef == null || !entityRef.isValid()) {
                LOGGER.atWarning().log("Invalid entity reference after spawning %s", request.mobTypeId());
                return SpawnResult.failed("Invalid entity reference");
            }

            if (isDebugMode()) {
                LOGGER.atInfo().log("[RealmSpawn] Spawned %s at (%.1f, %.1f, %.1f) for realm %s " +
                    "(hp=%.0f%%, dmg=%.0f%%, spd=%.0f%%)",
                    request.mobTypeId(),
                    request.position().x, request.position().y, request.position().z,
                    request.realmId().toString().substring(0, 8),
                    statMods.healthMultiplier * 100,
                    statMods.damageMultiplier * 100,
                    statMods.speedMultiplier * 100);
            }

            return SpawnResult.success(entityRef, request.mobTypeId(), request.classification());

        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to spawn %s in realm %s",
                request.mobTypeId(), request.realmId());
            return SpawnResult.failed("Exception: " + e.getMessage());
        }
    }

    /**
     * Spawns multiple NPCs directly.
     *
     * @param world    The world to spawn in
     * @param requests List of spawn requests
     * @return List of spawn results
     */
    @Nonnull
    public java.util.List<SpawnResult> spawnBatch(
            @Nonnull World world,
            @Nonnull java.util.List<SpawnRequest> requests) {

        return requests.stream()
            .map(request -> spawnNow(world, request))
            .toList();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // DEFERRED SPAWNING
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Queues a spawn request for later processing.
     *
     * <p>Use this method when spawning from within ECS system processing
     * (e.g., from onEntityAdd callbacks) to avoid "Store is currently processing!"
     * errors.
     *
     * @param world   The world to spawn in
     * @param request The spawn request
     */
    public void queueSpawn(@Nonnull World world, @Nonnull SpawnRequest request) {
        pendingSpawns.add(new PendingRealmSpawn(world, request));
    }

    /**
     * Queues multiple spawn requests.
     *
     * @param world    The world to spawn in
     * @param requests The spawn requests
     */
    public void queueSpawns(@Nonnull World world, @Nonnull java.util.List<SpawnRequest> requests) {
        for (SpawnRequest request : requests) {
            queueSpawn(world, request);
        }
    }

    /**
     * Processes all pending spawn requests.
     *
     * <p>Call this method from the world tick thread OUTSIDE of ECS system
     * processing (e.g., at the start of a tick).
     *
     * @return Number of successful spawns
     */
    public int processPendingSpawns() {
        if (pendingSpawns.isEmpty()) {
            return 0;
        }

        int successCount = 0;
        PendingRealmSpawn pending;

        while ((pending = pendingSpawns.poll()) != null) {
            SpawnResult result = spawnNow(pending.world(), pending.request());
            if (result.success()) {
                successCount++;
            }
        }

        if (successCount > 0 && isDebugMode()) {
            LOGGER.atInfo().log("[RealmSpawn] Processed %d pending spawns", successCount);
        }

        return successCount;
    }

    /**
     * Checks if there are pending spawns.
     *
     * @return true if pending spawns exist
     */
    public boolean hasPendingSpawns() {
        return !pendingSpawns.isEmpty();
    }

    /**
     * Clears all pending spawns without processing.
     *
     * <p>Use during shutdown or realm cleanup.
     */
    public void clearPendingSpawns() {
        pendingSpawns.clear();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // COMPONENT ATTACHMENT
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Attaches the RealmMobComponent to the entity holder during spawn.
     *
     * <p>This is the ONLY component attached during pre-spawn callback.
     * MobScalingSystem will detect this component and handle stat scaling
     * after EntityStatMap is created by BalancingInitialisationSystem.
     *
     * @param holder         The entity holder
     * @param componentType  The RealmMobComponent type
     * @param realmId        The realm instance ID
     * @param waveNumber     The wave number (0 = initial spawn)
     * @param isReinforcement Whether this is a reinforcement spawn
     * @param isElite        Whether this mob is elite (spawn-time modifier)
     * @param classification The mob classification
     * @param mods           The stat modifiers from realm config
     * @param realmLevel     The realm's level (for MobScalingSystem to use)
     */
    private void attachRealmMobComponent(
            @Nonnull Holder<EntityStore> holder,
            @Nonnull ComponentType<EntityStore, RealmMobComponent> componentType,
            @Nonnull UUID realmId,
            int waveNumber,
            boolean isReinforcement,
            boolean isElite,
            @Nonnull MobClassification classification,
            @Nonnull StatModifiers mods,
            int realmLevel) {

        RealmMobComponent component = new RealmMobComponent();
        component.setRealmId(realmId);
        component.setWaveNumber(waveNumber);
        component.setReinforcement(isReinforcement);
        component.setBoss(classification == MobClassification.BOSS);  // Authoritative classification from realm pool
        component.setElite(isElite);  // Track elite status for stat scaling and UI
        component.setCountsForCompletion(true); // All realm mobs count
        component.setRealmLevel(realmLevel);  // Critical: MobScalingSystem uses this

        // Store multipliers for combat system integration
        component.setHealthMultiplier(mods.healthMultiplier);
        component.setDamageMultiplier(mods.damageMultiplier);
        component.setSpeedMultiplier(mods.speedMultiplier);
        component.setAttackSpeedMultiplier(mods.attackSpeedMultiplier);

        holder.addComponent(componentType, component);
    }

    /**
     * Applies visual scale to elite and boss realm mobs.
     *
     * <p>Scale values are configurable in realm-mobs.yml. Elite bosses get
     * both multipliers stacked (e.g., 1.15 × 1.35 = 1.55x).
     *
     * @param holder         The entity holder (pre-spawn)
     * @param isElite        Whether this mob is elite
     * @param classification The mob classification (NORMAL or BOSS)
     */
    private void applyMobScale(
            @Nonnull Holder<EntityStore> holder,
            boolean isElite,
            @Nonnull MobClassification classification) {

        float scale = 1.0f;

        // Get configured scale values
        float eliteScale = getEliteScale();
        float bossScale = getBossScale();

        if (classification == MobClassification.BOSS) {
            scale *= bossScale;
        }
        if (isElite) {
            scale *= eliteScale;
        }

        // Only add the component if scale differs from default
        if (scale != 1.0f) {
            holder.addComponent(
                EntityScaleComponent.getComponentType(),
                new EntityScaleComponent(scale)
            );
        }
    }

    /**
     * Gets the elite scale from config, with fallback.
     */
    private float getEliteScale() {
        try {
            if (plugin.getRealmsManager() != null) {
                RealmMobSpawner spawner = plugin.getRealmsManager().getMobSpawner();
                if (spawner != null) {
                    return spawner.getCalculator().getConfig().getEliteScale();
                }
            }
        } catch (Exception e) {
            // Fall through to default
        }
        return 1.15f;
    }

    /**
     * Gets the boss scale from config, with fallback.
     */
    private float getBossScale() {
        try {
            if (plugin.getRealmsManager() != null) {
                RealmMobSpawner spawner = plugin.getRealmsManager().getMobSpawner();
                if (spawner != null) {
                    return spawner.getCalculator().getConfig().getBossScale();
                }
            }
        } catch (Exception e) {
            // Fall through to default
        }
        return 1.35f;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // STAT MODIFIER CALCULATION (for RealmMobComponent storage)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Calculates stat multipliers from realm map modifiers and player count.
     *
     * @param mapData     The realm map data containing modifiers
     * @param playerCount Number of players in the realm (for stat scaling)
     * @return Calculated stat multipliers
     */
    @Nonnull
    private StatModifiers calculateStatModifiers(@Nonnull RealmMapData mapData, int playerCount) {
        float healthMult = 1.0f;
        float damageMult = 1.0f;
        float speedMult = 1.0f;
        float attackSpeedMult = 1.0f;

        // Process all modifiers (both prefixes and suffixes)
        for (RealmModifier modifier : mapData.allModifiers()) {
            RealmModifierType type = modifier.type();
            int value = modifier.value();

            switch (type) {
                case MONSTER_HEALTH -> healthMult += value / 100f;
                case MONSTER_DAMAGE -> damageMult += value / 100f;
                case MONSTER_SPEED -> speedMult += value / 100f;
                case MONSTER_ATTACK_SPEED -> attackSpeedMult += value / 100f;
                default -> {
                    // Other modifiers don't affect mob stats directly
                }
            }
        }

        // Level scaling is handled by MobScalingSystem's stat pool (generate(realmLevel, ...)).
        // Map modifiers are the final layer — no extra level scaling here.

        // Apply player count scaling
        // Formula: multiplier^(playerCount - 1)
        // With 2 players: 1.2^1 = 1.2x stats
        // With 3 players: 1.2^2 = 1.44x stats
        float playerScaling = 1.0f;
        if (playerCount > 1) {
            float scalingMultiplier = getPlayerScalingMultiplier();
            playerScaling = (float) Math.pow(scalingMultiplier, playerCount - 1);
        }

        // Check if player scaling affects damage
        boolean scaleDamage = isPlayerScalingAffectsDamage();

        return new StatModifiers(
            healthMult * playerScaling,
            damageMult * (scaleDamage ? playerScaling : 1.0f),
            speedMult,
            attackSpeedMult
        );
    }

    /**
     * Gets the player scaling multiplier from config.
     */
    private float getPlayerScalingMultiplier() {
        try {
            if (plugin.getRealmsManager() != null) {
                // Access through mob spawner's calculator if available
                RealmMobSpawner spawner = plugin.getRealmsManager().getMobSpawner();
                if (spawner != null) {
                    return spawner.getCalculator().getConfig().getPlayerScalingMultiplier();
                }
            }
        } catch (Exception e) {
            // Fall through to default
        }
        return 1.2f; // Default
    }

    /**
     * Checks if player scaling affects damage from config.
     */
    private boolean isPlayerScalingAffectsDamage() {
        try {
            if (plugin.getRealmsManager() != null) {
                RealmMobSpawner spawner = plugin.getRealmsManager().getMobSpawner();
                if (spawner != null) {
                    return spawner.getCalculator().getConfig().isPlayerScalingAffectsDamage();
                }
            }
        } catch (Exception e) {
            // Fall through to default
        }
        return true; // Default
    }

    // ═══════════════════════════════════════════════════════════════════════
    // UTILITY
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Checks if debug mode is enabled.
     */
    private boolean isDebugMode() {
        try {
            return plugin.getRealmsManager() != null &&
                   plugin.getRealmsManager().getConfig().isDebugMode();
        } catch (Exception e) {
            return false;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // NESTED CLASSES
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Internal record for calculated stat multipliers.
     */
    private record StatModifiers(
        float healthMultiplier,
        float damageMultiplier,
        float speedMultiplier,
        float attackSpeedMultiplier
    ) {}

    /**
     * Internal record for pending spawn requests.
     */
    private record PendingRealmSpawn(
        @Nonnull World world,
        @Nonnull SpawnRequest request
    ) {}

    /**
     * Request to spawn an NPC in a realm.
     *
     * @param realmId         The realm instance ID
     * @param mobTypeId       The NPC type identifier (e.g., "trork_warrior")
     * @param classification  The mob classification (NORMAL, BOSS)
     * @param position        The spawn position
     * @param waveNumber      The wave number (0 = initial spawn)
     * @param isReinforcement Whether this is a reinforcement spawn
     * @param isElite         Whether this mob is elite (spawn-time modifier)
     * @param mapData         The realm map data for modifier calculations
     * @param playerCount     Number of players in the realm (for stat scaling)
     */
    public record SpawnRequest(
        @Nonnull UUID realmId,
        @Nonnull String mobTypeId,
        @Nonnull MobClassification classification,
        @Nonnull Vector3d position,
        int waveNumber,
        boolean isReinforcement,
        boolean isElite,
        @Nonnull RealmMapData mapData,
        int playerCount
    ) {
        /**
         * Creates a request for an initial spawn (wave 0, not reinforcement).
         */
        @Nonnull
        public static SpawnRequest initial(
                @Nonnull UUID realmId,
                @Nonnull String mobTypeId,
                @Nonnull MobClassification classification,
                @Nonnull Vector3d position,
                boolean isElite,
                @Nonnull RealmMapData mapData,
                int playerCount) {
            return new SpawnRequest(realmId, mobTypeId, classification, position, 0, false, isElite, mapData, playerCount);
        }

        /**
         * Creates a request for a reinforcement spawn.
         */
        @Nonnull
        public static SpawnRequest reinforcement(
                @Nonnull UUID realmId,
                @Nonnull String mobTypeId,
                @Nonnull MobClassification classification,
                @Nonnull Vector3d position,
                int waveNumber,
                boolean isElite,
                @Nonnull RealmMapData mapData,
                int playerCount) {
            return new SpawnRequest(realmId, mobTypeId, classification, position, waveNumber, true, isElite, mapData, playerCount);
        }
    }

    /**
     * Result of a spawn operation.
     */
    public record SpawnResult(
        boolean success,
        @Nullable Ref<EntityStore> entityRef,
        @Nullable String mobTypeId,
        @Nullable MobClassification classification,
        @Nullable String error
    ) {
        /**
         * Creates a successful spawn result.
         */
        @Nonnull
        public static SpawnResult success(
                @Nonnull Ref<EntityStore> entityRef,
                @Nonnull String mobTypeId,
                @Nonnull MobClassification classification) {
            return new SpawnResult(true, entityRef, mobTypeId, classification, null);
        }

        /**
         * Creates a failed spawn result.
         */
        @Nonnull
        public static SpawnResult failed(@Nonnull String error) {
            return new SpawnResult(false, null, null, null, error);
        }

        /**
         * Gets the entity UUID if spawn was successful.
         *
         * @return The entity UUID, or null if failed or entity has no UUID
         */
        @Nullable
        public UUID getEntityUuid() {
            if (!success || entityRef == null || !entityRef.isValid()) {
                return null;
            }
            // Note: Would need to access UUIDComponent to get actual UUID
            // For tracking purposes, we use the Ref hash as a surrogate
            return null;
        }
    }
}
