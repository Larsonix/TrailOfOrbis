package io.github.larsonix.trailoforbis.sanctum;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.EntityScaleComponent;
import com.hypixel.hytale.server.core.modules.entity.component.Interactable;
import com.hypixel.hytale.server.core.modules.entity.hitboxcollision.HitboxCollision;
import com.hypixel.hytale.server.core.modules.entity.hitboxcollision.HitboxCollisionConfig;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.modules.entity.component.DisplayNameComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.component.DynamicLight;
import com.hypixel.hytale.protocol.ColorLight;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.interaction.Interactions;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.sanctum.interactions.SkillNodeInteraction;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.sanctum.components.SkillNodeComponent;
import io.github.larsonix.trailoforbis.sanctum.components.SkillNodeSubtitleComponent;
import io.github.larsonix.trailoforbis.skilltree.NodeState;
import io.github.larsonix.trailoforbis.skilltree.NodeLabelFormatter;
import io.github.larsonix.trailoforbis.skilltree.SkillTreeManager;
import io.github.larsonix.trailoforbis.skilltree.SkillTreeRegion;
import io.github.larsonix.trailoforbis.skilltree.config.SkillNode;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Spawns essence entities representing skill tree nodes in a Skill Sanctum.
 *
 * <p>This spawner handles:
 * <ul>
 *   <li>Spawning ~300 node essences at spiral galaxy positions</li>
 *   <li>Attaching {@link SkillNodeComponent} to each essence</li>
 *   <li>Setting visual appearance based on arm (colored essence) and state</li>
 *   <li>Batched spawning to prevent lag spikes</li>
 * </ul>
 *
 * <h2>Essence Types (by arm)</h2>
 * <ul>
 *   <li>CORE → Diamond Gem (gold glow)</li>
 *   <li>FIRE → Fire Essence (red glow)</li>
 *   <li>WATER → Ice Essence (cyan glow)</li>
 *   <li>LIGHTNING → Lightning Essence (yellow glow)</li>
 *   <li>EARTH → Life Essence (green glow)</li>
 *   <li>VOID → Void Essence (purple glow)</li>
 *   <li>WIND → Water Essence (teal glow)</li>
 * </ul>
 *
 * <h2>Scale (by importance)</h2>
 * <ul>
 *   <li>Origin → 4.0</li>
 *   <li>Keystone → 6.0</li>
 *   <li>Notable → 4.0</li>
 *   <li>Basic → 2.5</li>
 * </ul>
 *
 * <h2>Keystone Items</h2>
 * <p>Keystone nodes use arm-specific gem items instead of essence:
 * <ul>
 *   <li>CORE → Diamond Gem</li>
 *   <li>FIRE → Ruby Gem</li>
 *   <li>WATER → Sapphire Gem</li>
 *   <li>LIGHTNING → Topaz Gem</li>
 *   <li>EARTH → Emerald Gem</li>
 *   <li>VOID → Voidstone Gem</li>
 *   <li>WIND → Zephyr Gem</li>
 * </ul>
 *
 * @see SkillSanctumInstance
 * @see GalaxySpiralLayoutMapper
 * @see SkillNodeComponent
 */
public class SkillSanctumNodeSpawner {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // ═══════════════════════════════════════════════════════════════════
    // PICKUP DELAY
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Very high pickup delay to prevent players from picking up node essences.
     * Set to effectively infinite (1 year in seconds).
     */
    private static final float PICKUP_DELAY_INFINITE = 31536000f;

    // ═══════════════════════════════════════════════════════════════════
    // BRIGHTNESS MODIFIERS (by state)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Scale multiplier for allocated nodes (bright, prominent).
     */
    private static final float STATE_SCALE_ALLOCATED = 1.2f;

    /**
     * Scale multiplier for available nodes (normal brightness).
     */
    private static final float STATE_SCALE_AVAILABLE = 1.0f;

    /**
     * Scale multiplier for locked nodes (dimmer, smaller).
     */
    private static final float STATE_SCALE_LOCKED = 0.7f;

    // ═══════════════════════════════════════════════════════════════════
    // Y OFFSET CORRECTIONS (for allocated crystals/gems)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Additional Y offset for allocated NOTABLE nodes.
     * Medium crystals have a different visual anchor than essences.
     */
    private static final double ALLOCATED_NOTABLE_Y_OFFSET = -0.5;

    /**
     * Additional Y offset for allocated KEYSTONE nodes.
     * Gem items have a different visual anchor than essences.
     */
    private static final double ALLOCATED_KEYSTONE_Y_OFFSET = -1.0;

    /**
     * Additional Y offset for basic (minor) nodes.
     * These small essence items need slight lowering to align with ropes/chains.
     */
    private static final double BASIC_NODE_Y_OFFSET = -0.1;

    // ═══════════════════════════════════════════════════════════════════
    // SUBTITLE Y OFFSETS (above main node entity)
    // ═══════════════════════════════════════════════════════════════════

    private static final double SUBTITLE_Y_OFFSET_BASE = 0.1;
    private static final double SUBTITLE_BASIC_LOCKED_OFFSET = 0.1;
    private static final double SUBTITLE_BASIC_AVAILABLE_OFFSET = 0.4;
    private static final double SUBTITLE_BASIC_ALLOCATED_OFFSET = 0.5;
    private static final double SUBTITLE_NOTABLE_LOCKED_ADJUST = 0.6;
    private static final double SUBTITLE_NOTABLE_AVAILABLE_ADJUST = 1.0;
    private static final double SUBTITLE_NOTABLE_ALLOCATED_ADJUST = 1.2;
    private static final double SUBTITLE_KEYSTONE_LOCKED_ADJUST = 0.6;
    private static final double SUBTITLE_KEYSTONE_AVAILABLE_ADJUST = 1.5;
    private static final double SUBTITLE_KEYSTONE_ALLOCATED_ADJUST = 2.2;

    /**
     * Scale for subtitle entities — very small to be nearly invisible.
     * The entity exists primarily to hold a Nameplate component.
     */
    private static final float SUBTITLE_ENTITY_SCALE = 0.1f;

    /**
     * Item used for subtitle entities — a custom essence with all visuals suppressed.
     * The client needs an ItemComponent to create a visual entity that
     * the nameplate text can anchor to. The Light0 variant has Light: null,
     * ShowItemParticles: false, and ParticleSystemId: null — no beam, no glow.
     */
    private static final String SUBTITLE_ITEM_ID = "Ingredient_Fire_Essence_Light0";

    /**
     * Maximum spawns per tick to prevent lag spikes.
     */
    private static final int DEFAULT_MAX_SPAWNS_PER_TICK = 15;

    /**
     * Maximum node entities to spawn per tick during batch processing.
     * Matches {@link SkillSanctumConnectionRenderer#MAX_ROPE_SPAWNS_PER_TICK}
     * to prevent overwhelming the entity tracker.
     *
     * <p>With ~485 nodes, this results in ~10 batches across ~1 second,
     * giving the tracker proper per-batch sync windows for each client.
     */
    private static final int MAX_NODE_SPAWNS_PER_TICK = 50;

    // ═══════════════════════════════════════════════════════════════════
    // DEPENDENCIES
    // ═══════════════════════════════════════════════════════════════════

    private final TrailOfOrbis plugin;
    private final SkillTreeManager skillTreeManager;

    // ═══════════════════════════════════════════════════════════════════
    // CONFIGURATION
    // ═══════════════════════════════════════════════════════════════════

    private int maxSpawnsPerTick = DEFAULT_MAX_SPAWNS_PER_TICK;

    // ═══════════════════════════════════════════════════════════════════
    // PENDING SPAWN QUEUE
    // ═══════════════════════════════════════════════════════════════════

    private final Queue<PendingNodeSpawn> pendingSpawns = new ConcurrentLinkedQueue<>();

    // ═══════════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Creates a new node spawner.
     *
     * @param plugin           The main plugin instance
     * @param skillTreeManager The skill tree manager for node data
     */
    public SkillSanctumNodeSpawner(@Nonnull TrailOfOrbis plugin, @Nonnull SkillTreeManager skillTreeManager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.skillTreeManager = Objects.requireNonNull(skillTreeManager, "skillTreeManager cannot be null");
    }

    /**
     * Sets the maximum number of spawns per tick.
     *
     * @param maxSpawnsPerTick Maximum spawns (must be > 0)
     */
    public void setMaxSpawnsPerTick(int maxSpawnsPerTick) {
        if (maxSpawnsPerTick <= 0) {
            throw new IllegalArgumentException("maxSpawnsPerTick must be > 0");
        }
        this.maxSpawnsPerTick = maxSpawnsPerTick;
    }

    // ═══════════════════════════════════════════════════════════════════
    // SPAWNING
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Queues all skill tree nodes for spawning in a sanctum instance.
     *
     * <p>This method queues spawn requests for all nodes. The actual spawning
     * happens in batches when {@link #processPendingSpawns} is called.
     *
     * @param instance       The sanctum instance to spawn nodes for
     * @param allocatedNodes Set of node IDs that are already allocated
     * @return CompletableFuture that completes when all nodes are spawned
     */
    @Nonnull
    public CompletableFuture<SpawnResult> queueAllNodes(
            @Nonnull SkillSanctumInstance instance,
            @Nonnull Set<String> allocatedNodes) {

        Objects.requireNonNull(instance, "instance cannot be null");
        Objects.requireNonNull(allocatedNodes, "allocatedNodes cannot be null");

        Collection<SkillNode> allNodes = skillTreeManager.getAllNodes();
        if (allNodes.isEmpty()) {
            LOGGER.atWarning().log("No skill tree nodes configured - nothing to spawn");
            return CompletableFuture.completedFuture(new SpawnResult(0, 0, List.of()));
        }

        UUID playerId = instance.getPlayerId();
        World world = instance.getSanctumWorld();
        if (world == null) {
            LOGGER.atWarning().log("Sanctum world not available for %s", playerId);
            return CompletableFuture.failedFuture(new IllegalStateException("Sanctum world not available"));
        }

        // Initialize node indices for even distribution along spiral arms
        GalaxySpiralLayoutMapper.initializeNodeIndices(allNodes);

        // Determine which nodes are available (connected to an allocated node)
        Set<String> availableNodes = calculateAvailableNodes(allocatedNodes);

        // Queue spawn for each node
        int queued = 0;
        for (SkillNode node : allNodes) {
            NodeState state = determineNodeState(node, allocatedNodes, availableNodes);
            Vector3d position = GalaxySpiralLayoutMapper.toWorldPosition(node);

            PendingNodeSpawn spawn = new PendingNodeSpawn(
                instance,
                node,
                position,
                state
            );
            pendingSpawns.add(spawn);
            queued++;
        }

        LOGGER.atInfo().log("Queued %d node spawns for sanctum (player=%s, allocated=%d, available=%d)",
            queued, playerId, allocatedNodes.size(), availableNodes.size());

        // Return a future that will be completed when processPendingSpawns finishes
        // For now, return immediately - caller should poll processPendingSpawns
        return CompletableFuture.completedFuture(new SpawnResult(queued, 0, List.of()));
    }

    /**
     * Processes a batch of pending spawn requests for a specific instance.
     *
     * <p>Collects up to {@link #MAX_NODE_SPAWNS_PER_TICK} spawns per call, matching
     * the rope batching pattern in {@link SkillSanctumConnectionRenderer#processPendingRopeSpawns}.
     * This prevents overwhelming the entity tracker — with ~485 nodes, batching at 50
     * results in ~10 batches across ~1 second, giving the tracker proper sync windows.
     *
     * <p>Only calls {@link SkillSanctumInstance#markSpawningComplete()} on the final
     * batch (when no more pending spawns remain for this player).
     *
     * @param instance The sanctum instance to process spawns for
     * @return Number of entities in this batch
     */
    public int processPendingSpawnsForInstance(@Nonnull SkillSanctumInstance instance) {
        ComponentType<EntityStore, SkillNodeComponent> componentType = plugin.getSkillNodeComponentType();
        if (componentType == null) {
            LOGGER.atSevere().log("SkillNodeComponent type not registered");
            return 0;
        }

        if (!instance.isActive()) {
            return 0;
        }

        World world = instance.getSanctumWorld();
        if (world == null || !world.isAlive()) {
            LOGGER.atWarning().log("World not available for instance %s", instance.getPlayerId());
            return 0;
        }

        // Collect up to MAX_NODE_SPAWNS_PER_TICK spawns for this player
        List<PendingNodeSpawn> batch = new ArrayList<>();
        UUID playerId = instance.getPlayerId();

        Iterator<PendingNodeSpawn> iter = pendingSpawns.iterator();
        while (iter.hasNext() && batch.size() < MAX_NODE_SPAWNS_PER_TICK) {
            PendingNodeSpawn spawn = iter.next();
            if (spawn.instance.getPlayerId().equals(playerId)) {
                batch.add(spawn);
                iter.remove();
            }
        }

        if (batch.isEmpty()) {
            return 0;
        }

        // Check if this is the last batch BEFORE spawning (queue state is already updated)
        boolean isLastBatch = !hasPendingSpawnsForInstance(playerId);

        LOGGER.atInfo().log("Processing batch of %d node spawns for instance %s (last=%b)",
            batch.size(), playerId, isLastBatch);

        // Spawn entities on the world thread
        world.execute(() -> {
            Store<EntityStore> store = world.getEntityStore().getStore();
            int spawnedCount = 0;

            for (PendingNodeSpawn pending : batch) {
                try {
                    Ref<EntityStore> entityRef = spawnNodeEssence(store, componentType, pending);

                    if (entityRef != null && entityRef.isValid()) {
                        instance.registerNodeEntity(pending.node.getId(), entityRef);
                        spawnedCount++;

                        // Spawn lightweight subtitle entity above the main node
                        TransformComponent mainTransform = store.getComponent(entityRef, TransformComponent.getComponentType());
                        if (mainTransform != null) {
                            Ref<EntityStore> subtitleRef = spawnSubtitleEntity(
                                store, pending.node, mainTransform.getPosition(), pending.state, playerId);
                            if (subtitleRef != null && subtitleRef.isValid()) {
                                instance.registerSubtitleEntity(pending.node.getId(), subtitleRef);
                            }
                        }
                    } else {
                        LOGGER.atWarning().log("Failed to spawn node %s (ref=%s)",
                            pending.node.getId(), entityRef == null ? "null" : "invalid");
                    }
                } catch (Exception e) {
                    LOGGER.atSevere().withCause(e).log("Exception spawning node %s", pending.node.getId());
                }
            }

            LOGGER.atInfo().log("Batch spawn complete: %d/%d nodes (last=%b)",
                spawnedCount, batch.size(), isLastBatch);

            // Only mark spawning complete on the final batch
            if (isLastBatch) {
                instance.markSpawningComplete();
            }
        });

        return batch.size();
    }

    /**
     * Processes ALL pending spawn requests for a specific instance directly on the current thread.
     *
     * <p>Unlike {@link #processPendingSpawnsForInstance}, this method does NOT wrap spawning
     * in {@code world.execute()} — the caller MUST already be on the world thread. This
     * eliminates the 1-tick gap that causes "chunk not loaded" warnings when chunks are
     * freshly loaded.
     *
     * <p>Intended to be called from a chunk load completion callback (via {@code thenRun})
     * where we're already on the world thread in the same tick the chunks finished loading.
     *
     * @param instance The sanctum instance to process spawns for
     * @return Number of entities queued for spawning (actual spawn happens inline)
     */
    public int processSpawnsDirect(@Nonnull SkillSanctumInstance instance) {
        ComponentType<EntityStore, SkillNodeComponent> componentType = plugin.getSkillNodeComponentType();
        if (componentType == null) {
            LOGGER.atSevere().log("SkillNodeComponent type not registered");
            return 0;
        }

        if (!instance.isActive()) {
            return 0;
        }

        World world = instance.getSanctumWorld();
        if (world == null || !world.isAlive()) {
            LOGGER.atWarning().log("World not available for instance %s", instance.getPlayerId());
            return 0;
        }

        // Collect all pending spawns for this instance
        List<PendingNodeSpawn> batch = new ArrayList<>();
        UUID playerId = instance.getPlayerId();

        pendingSpawns.removeIf(spawn -> {
            if (spawn.instance.getPlayerId().equals(playerId)) {
                batch.add(spawn);
                return true;
            }
            return false;
        });

        if (batch.isEmpty()) {
            return 0;
        }

        LOGGER.atInfo().log("Direct-spawning batch of %d nodes for instance %s", batch.size(), playerId);

        // Spawn all entities directly — caller guarantees world thread
        Store<EntityStore> store = world.getEntityStore().getStore();
        int spawnedCount = 0;

        for (PendingNodeSpawn pending : batch) {
            try {
                Ref<EntityStore> entityRef = spawnNodeEssence(store, componentType, pending);

                if (entityRef != null && entityRef.isValid()) {
                    instance.registerNodeEntity(pending.node.getId(), entityRef);
                    spawnedCount++;

                    // Spawn lightweight subtitle entity above the main node
                    TransformComponent mainTransform = store.getComponent(entityRef, TransformComponent.getComponentType());
                    if (mainTransform != null) {
                        Ref<EntityStore> subtitleRef = spawnSubtitleEntity(
                            store, pending.node, mainTransform.getPosition(), pending.state, playerId);
                        if (subtitleRef != null && subtitleRef.isValid()) {
                            instance.registerSubtitleEntity(pending.node.getId(), subtitleRef);
                        }
                    }
                } else {
                    LOGGER.atWarning().log("Failed to spawn node %s (ref=%s)",
                        pending.node.getId(), entityRef == null ? "null" : "invalid");
                }
            } catch (Exception e) {
                LOGGER.atSevere().withCause(e).log("Exception spawning node %s", pending.node.getId());
            }
        }

        LOGGER.atInfo().log("Direct batch spawn complete: %d/%d nodes",
            spawnedCount, batch.size());

        // Mark spawning as complete AFTER all entities are registered
        instance.markSpawningComplete();

        return batch.size();
    }

    /**
     * Checks if there are pending spawns for a specific instance.
     */
    public boolean hasPendingSpawnsForInstance(@Nonnull UUID playerId) {
        return pendingSpawns.stream().anyMatch(spawn -> spawn.instance.getPlayerId().equals(playerId));
    }

    /**
     * Gets the essence/gem item ID based on node's arm, type, and state.
     *
     * <p>Uses custom items from TrailOfOrbis_Realms mod with variable light levels:
     * <ul>
     *   <li>LOCKED: _Light0 suffix (no glow)</li>
     *   <li>AVAILABLE: _Light50 suffix (medium glow)</li>
     *   <li>ALLOCATED: _Light100 suffix (full glow)</li>
     * </ul>
     *
     * @param node  The skill node
     * @param state The current node state
     * @return The item ID with appropriate light suffix
     */
    @Nonnull
    private String getItemForNode(@Nonnull SkillNode node, @Nonnull NodeState state) {
        SkillTreeRegion region = node.getSkillTreeRegion();

        // Keystones use gem items
        if (node.isKeystone()) {
            return region.getKeystoneGemItemForState(state);
        }

        // Notable nodes use medium crystals
        if (node.isNotable()) {
            return region.getMediumCrystalItemForState(state);
        }

        // Origin uses Core gem (always allocated/full glow)
        if (node.isStartNode() || "origin".equals(node.getId())) {
            return region.getKeystoneGemItemForState(NodeState.ALLOCATED);
        }

        // Basic nodes use arm-specific essence
        return region.getEssenceItemForState(state);
    }

    /**
     * Gets the scale based on node importance and state.
     *
     * <p>Base scales:
     * <ul>
     *   <li>Origin → 4.0</li>
     *   <li>Keystone → 6.0</li>
     *   <li>Notable → 4.0</li>
     *   <li>Basic → 2.5</li>
     * </ul>
     *
     * <p>State modifiers:
     * <ul>
     *   <li>ALLOCATED → ×1.2 (prominent)</li>
     *   <li>AVAILABLE → ×1.0 (normal)</li>
     *   <li>LOCKED → ×0.7 (dimmer)</li>
     * </ul>
     *
     * @param node  The skill node
     * @param state The node state
     * @return The scale factor
     */
    public static float getScaleForNode(@Nonnull SkillNode node, @Nonnull NodeState state) {
        float baseScale = GalaxySpiralLayoutMapper.getNodeScale(node);

        // Apply state modifier
        float stateModifier = switch (state) {
            case ALLOCATED -> STATE_SCALE_ALLOCATED;
            case AVAILABLE -> STATE_SCALE_AVAILABLE;
            case LOCKED -> STATE_SCALE_LOCKED;
        };

        return baseScale * stateModifier;
    }

    /**
     * Checks if there are pending spawns remaining.
     *
     * @return true if spawns are pending
     */
    public boolean hasPendingSpawns() {
        return !pendingSpawns.isEmpty();
    }

    /**
     * Gets the number of pending spawns.
     */
    public int getPendingSpawnCount() {
        return pendingSpawns.size();
    }

    /**
     * Clears all pending spawns (used when cancelling a sanctum).
     *
     * @param playerId Only clear spawns for this player's instance
     */
    public void clearPendingSpawns(@Nonnull UUID playerId) {
        pendingSpawns.removeIf(spawn -> spawn.instance.getPlayerId().equals(playerId));
    }

    // ═══════════════════════════════════════════════════════════════════
    // SUBTITLE ENTITIES
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Calculates the Y offset for a subtitle entity relative to the main node position.
     *
     * <p>The offset varies by node type (basic/notable/keystone) and state to account
     * for different model sizes — larger models (keystones) need higher offsets so the
     * subtitle text doesn't overlap with the model.
     *
     * @param node  The skill node
     * @param state The current node state
     * @return The Y offset to add above the main node position
     */
    public double calculateSubtitleYOffset(@Nonnull SkillNode node, @Nonnull NodeState state) {
        double offset = SUBTITLE_Y_OFFSET_BASE;

        if (node.isKeystone()) {
            offset += switch (state) {
                case LOCKED -> SUBTITLE_KEYSTONE_LOCKED_ADJUST;
                case AVAILABLE -> SUBTITLE_KEYSTONE_AVAILABLE_ADJUST;
                case ALLOCATED -> SUBTITLE_KEYSTONE_ALLOCATED_ADJUST;
            };
        } else if (node.isNotable()) {
            offset += switch (state) {
                case LOCKED -> SUBTITLE_NOTABLE_LOCKED_ADJUST;
                case AVAILABLE -> SUBTITLE_NOTABLE_AVAILABLE_ADJUST;
                case ALLOCATED -> SUBTITLE_NOTABLE_ALLOCATED_ADJUST;
            };
        } else {
            offset += switch (state) {
                case LOCKED -> SUBTITLE_BASIC_LOCKED_OFFSET;
                case AVAILABLE -> SUBTITLE_BASIC_AVAILABLE_OFFSET;
                case ALLOCATED -> SUBTITLE_BASIC_ALLOCATED_OFFSET;
            };
        }

        return offset;
    }

    /**
     * Spawns a subtitle entity above a main node entity.
     *
     * <p>Uses a tiny item ({@value #SUBTITLE_ITEM_ID} at {@value #SUBTITLE_ENTITY_SCALE}
     * scale) as a visual anchor — the client requires an ItemComponent to create a render object
     * that the Nameplate text attaches to. The Light0 essence variant has ShowItemParticles: false
     * (no rarity beam), Light: null (no glow), and ParticleSystemId: null.
     *
     * @param store        The entity store
     * @param node         The skill node
     * @param nodePosition The main node entity's position (already adjusted)
     * @param state        The node state (affects Y offset)
     * @param playerId     The owning player's UUID
     * @return The subtitle entity Ref, or null if the node has no subtitle text
     */
    @Nullable
    public Ref<EntityStore> spawnSubtitleEntity(
            @Nonnull Store<EntityStore> store,
            @Nonnull SkillNode node,
            @Nonnull Vector3d nodePosition,
            @Nonnull NodeState state,
            @Nonnull UUID playerId) {

        String subtitleText = NodeLabelFormatter.getSubtitleText(node);
        if (subtitleText == null) {
            return null;
        }

        try {
            Holder<EntityStore> holder = store.getRegistry().newHolder();

            // Tiny item entity — gives the client a visual to anchor the nameplate to
            ItemStack itemStack = new ItemStack(SUBTITLE_ITEM_ID, 1);
            if (!itemStack.isValid()) {
                LOGGER.atWarning().log("Invalid subtitle item %s for node %s", SUBTITLE_ITEM_ID, node.getId());
                return null;
            }
            holder.addComponent(ItemComponent.getComponentType(), new ItemComponent(
                itemStack,
                Float.MAX_VALUE,        // mergeDelay — never merge
                PICKUP_DELAY_INFINITE,  // pickupDelay — effectively infinite
                0f,                     // pickupThrottle
                false                   // removedByPlayerPickup
            ));

            // Position above the main node
            double yOffset = calculateSubtitleYOffset(node, state);
            Vector3d pos = new Vector3d(nodePosition.x, nodePosition.y + yOffset, nodePosition.z);
            holder.addComponent(TransformComponent.getComponentType(),
                new TransformComponent(pos, new Vector3f(0f, 0f, 0f)));

            // Nearly invisible scale
            holder.addComponent(EntityScaleComponent.getComponentType(),
                new EntityScaleComponent(SUBTITLE_ENTITY_SCALE));

            // Identity + network tracking
            holder.ensureComponent(UUIDComponent.getComponentType());
            holder.addComponent(NetworkId.getComponentType(),
                new NetworkId(store.getExternalData().takeNextNetworkId()));

            // The nameplate text itself
            holder.addComponent(Nameplate.getComponentType(), new Nameplate(subtitleText));

            // Interactable + empty Interactions prevents item bobbing animation
            holder.ensureComponent(Interactable.getComponentType());
            holder.putComponent(Interactions.getComponentType(), new Interactions());

            // ECS marker for forEachChunk iteration in tickNameplateVisibility
            ComponentType<EntityStore, SkillNodeSubtitleComponent> subtitleType =
                TrailOfOrbis.getInstance().getSkillNodeSubtitleComponentType();
            if (subtitleType != null) {
                holder.addComponent(subtitleType, new SkillNodeSubtitleComponent(node.getId(), playerId));
            }

            Ref<EntityStore> entityRef = store.addEntity(holder, AddReason.SPAWN);

            LOGGER.atFine().log("Spawned subtitle entity for node %s at (%.1f, %.1f, %.1f)",
                node.getId(), pos.x, pos.y, pos.z);

            return entityRef;

        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to spawn subtitle entity for node %s", node.getId());
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // INTERNAL
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Spawns a single node essence entity.
     *
     * <p>Uses arm-specific items with state-based scaling:
     * <ul>
     *   <li>Keystones use gem items (Ruby, Sapphire, etc.)</li>
     *   <li>Notables use medium crystals</li>
     *   <li>Basic nodes use essence items (Fire, Ice, etc.)</li>
     * </ul>
     *
     * <p>The entity is spawned as a static item:
     * <ul>
     *   <li>No physics/velocity - stays in place</li>
     *   <li>HitboxCollision with SoftCollision - enables targeting but prevents bobbing</li>
     *   <li>Infinite pickup delay - cannot be picked up</li>
     *   <li>No despawn - persists until cleaned up</li>
     * </ul>
     */
    @Nullable
    private Ref<EntityStore> spawnNodeEssence(
            @Nonnull Store<EntityStore> store,
            @Nonnull ComponentType<EntityStore, SkillNodeComponent> componentType,
            @Nonnull PendingNodeSpawn pending) {

        try {
            Holder<EntityStore> holder = store.getRegistry().newHolder();

            // Get item based on arm, node type, and current state (controls glow level)
            String itemId = getItemForNode(pending.node, pending.state);
            ItemStack itemStack = new ItemStack(itemId, 1);

            if (!itemStack.isValid()) {
                LOGGER.atWarning().log("Invalid item %s for node %s", itemId, pending.node.getId());
                return null;
            }

            // Create ItemComponent with infinite pickup delay (cannot be picked up)
            ItemComponent itemComponent = new ItemComponent(
                itemStack,
                Float.MAX_VALUE,        // mergeDelay - never merge
                PICKUP_DELAY_INFINITE,  // pickupDelay - effectively infinite
                0f,                     // pickupThrottle
                false                   // removedByPlayerPickup
            );
            holder.addComponent(ItemComponent.getComponentType(), itemComponent);

            // Transform with position (no rotation needed for essences)
            // Add Y offset to center nodes visually relative to ropes/chains
            double yOffset = GalaxySpiralLayoutMapper.NODE_VISUAL_Y_OFFSET;

            // NOTABLE and KEYSTONE nodes use crystals/gems which have different visual centers
            // Apply full offset for ALLOCATED, half offset for AVAILABLE
            // Basic nodes need slight lowering to align with ropes/chains
            boolean isOrigin = pending.node.isStartNode() || "origin".equals(pending.node.getId());
            if (pending.node.isKeystone()) {
                if (pending.state == NodeState.ALLOCATED) {
                    yOffset += ALLOCATED_KEYSTONE_Y_OFFSET;
                } else if (pending.state == NodeState.AVAILABLE) {
                    yOffset += ALLOCATED_KEYSTONE_Y_OFFSET * 0.5;
                }
            } else if (pending.node.isNotable()) {
                if (pending.state == NodeState.ALLOCATED) {
                    yOffset += ALLOCATED_NOTABLE_Y_OFFSET;
                } else if (pending.state == NodeState.AVAILABLE) {
                    yOffset += ALLOCATED_NOTABLE_Y_OFFSET * 0.5;
                }
            } else if (!isOrigin) {
                // Basic (minor) nodes - apply offset regardless of state
                yOffset += BASIC_NODE_Y_OFFSET;
            }

            Vector3d adjustedPosition = new Vector3d(
                pending.position.x,
                pending.position.y + yOffset,
                pending.position.z
            );
            Vector3f rotation = new Vector3f(0f, 0f, 0f);
            holder.addComponent(TransformComponent.getComponentType(),
                new TransformComponent(adjustedPosition, rotation));

            // Scale based on node importance and state
            float scale = getScaleForNode(pending.node, pending.state);
            holder.addComponent(EntityScaleComponent.getComponentType(),
                new EntityScaleComponent(scale));

            // UUID for entity tracking
            holder.ensureComponent(UUIDComponent.getComponentType());

            // NetworkId is CRITICAL for client-server interaction!
            // Without this, the client cannot send the entity ID when pressing F to interact.
            holder.addComponent(NetworkId.getComponentType(),
                new NetworkId(store.getExternalData().takeNextNetworkId()));

            // HitboxCollision allows client to target this entity with cursor for F-key interaction.
            // Without this, item entities cannot be targeted for interaction.
            HitboxCollisionConfig hitboxConfig = HitboxCollisionConfig.getAssetMap().getAsset("SoftCollision");
            if (hitboxConfig != null) {
                holder.addComponent(HitboxCollision.getComponentType(), new HitboxCollision(hitboxConfig));
            } else {
                LOGGER.atWarning().log("SoftCollision HitboxCollisionConfig not found - nodes may not be targetable");
            }

            // Add Nameplate with node name - visibility is managed by SkillSanctumInstance
            String nameplateText = NodeLabelFormatter.getNameplateText(pending.node);
            holder.addComponent(Nameplate.getComponentType(), new Nameplate(nameplateText));

            // NOTE: We intentionally do NOT add:
            // - PhysicsValues/Velocity - so it stays static (no gravity/movement)
            // - DespawnComponent - so it persists until cleanup

            // Attach skill node component and interaction
            attachSkillNodeComponent(
                holder,
                componentType,
                pending.instance.getPlayerId(),
                pending.node,
                pending.state
            );

            // Add to world
            Ref<EntityStore> entityRef = store.addEntity(holder, AddReason.SPAWN);

            // Light levels are now controlled by the item itself (_Light0, _Light50, _Light100 variants)
            // No need to override DynamicLight component - the custom items have built-in light properties

            LOGGER.atFine().log("Spawned node essence: %s at (%.1f, %.1f, %.1f) arm=%s state=%s scale=%.1f item=%s",
                pending.node.getId(),
                pending.position.x, pending.position.y, pending.position.z,
                pending.node.getSkillTreeRegion().name(),
                pending.state, scale, itemId);

            return entityRef;

        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to spawn node %s", pending.node.getId());
            return null;
        }
    }

    /**
     * Spawns a single node entity at a specific position.
     *
     * <p>This is used for respawning nodes when their state changes and the client
     * can't handle in-place item updates (e.g., crystals/gems).
     *
     * <p><b>MUST be called from the world thread!</b>
     *
     * @param instance The sanctum instance (for player ID)
     * @param store    The entity store
     * @param node     The skill node definition
     * @param position The position to spawn at
     * @param state    The node state
     * @return The new entity Ref, or null if spawn failed
     */
    @Nullable
    public Ref<EntityStore> spawnSingleNode(
            @Nonnull SkillSanctumInstance instance,
            @Nonnull Store<EntityStore> store,
            @Nonnull SkillNode node,
            @Nonnull Vector3d position,
            @Nonnull NodeState state) {

        TrailOfOrbis plugin = TrailOfOrbis.getInstance();
        ComponentType<EntityStore, SkillNodeComponent> componentType = plugin.getSkillNodeComponentType();
        if (componentType == null) {
            LOGGER.atWarning().log("Cannot spawn single node - SkillNodeComponent type not registered");
            return null;
        }

        // Create a PendingNodeSpawn to reuse existing logic
        PendingNodeSpawn pending = new PendingNodeSpawn(instance, node, position, state);
        Ref<EntityStore> entityRef = spawnNodeEssence(store, componentType, pending);

        if (entityRef != null && entityRef.isValid()) {
            // Register the new entity with the instance
            instance.registerNodeEntity(node.getId(), entityRef);
            LOGGER.atInfo().log("Spawned single node %s at (%.1f, %.1f, %.1f) with state %s",
                node.getId(), position.x, position.y, position.z, state);
        }

        return entityRef;
    }

    /**
     * Respawns a node at an exact position (no Y adjustment applied).
     *
     * <p>Use this when respawning a node that was already positioned correctly.
     * The position from the old entity is already adjusted, so we don't want to
     * adjust it again.
     *
     * <p><b>MUST be called from the world thread!</b>
     *
     * @param instance      The sanctum instance
     * @param store         The entity store
     * @param node          The skill node definition
     * @param exactPosition The exact position (already adjusted, from old entity)
     * @param state         The new node state
     * @return The new entity Ref, or null if spawn failed
     */
    @Nullable
    public Ref<EntityStore> respawnNodeAtExactPosition(
            @Nonnull SkillSanctumInstance instance,
            @Nonnull Store<EntityStore> store,
            @Nonnull SkillNode node,
            @Nonnull Vector3d exactPosition,
            @Nonnull NodeState state) {

        TrailOfOrbis plugin = TrailOfOrbis.getInstance();
        ComponentType<EntityStore, SkillNodeComponent> componentType = plugin.getSkillNodeComponentType();
        if (componentType == null) {
            LOGGER.atWarning().log("Cannot respawn node - SkillNodeComponent type not registered");
            return null;
        }

        // Spawn the node entity directly at the exact position (no adjustment)
        Ref<EntityStore> entityRef = spawnNodeEssenceAtExactPosition(store, componentType, instance, node, exactPosition, state);

        if (entityRef != null && entityRef.isValid()) {
            instance.registerNodeEntity(node.getId(), entityRef);
            LOGGER.atInfo().log("Respawned node %s at exact position (%.1f, %.1f, %.1f) with state %s",
                node.getId(), exactPosition.x, exactPosition.y, exactPosition.z, state);
        }

        return entityRef;
    }

    /**
     * Spawns a node essence entity at an exact position (no Y adjustment).
     *
     * <p>This is used for respawning nodes where the position is already correct.
     */
    @Nullable
    private Ref<EntityStore> spawnNodeEssenceAtExactPosition(
            @Nonnull Store<EntityStore> store,
            @Nonnull ComponentType<EntityStore, SkillNodeComponent> componentType,
            @Nonnull SkillSanctumInstance instance,
            @Nonnull SkillNode node,
            @Nonnull Vector3d exactPosition,
            @Nonnull NodeState state) {

        try {
            Holder<EntityStore> holder = store.getRegistry().newHolder();

            // Get item based on arm, node type, and current state
            String itemId = getItemForNode(node, state);
            ItemStack itemStack = new ItemStack(itemId, 1);

            if (!itemStack.isValid()) {
                LOGGER.atWarning().log("Invalid item %s for node %s", itemId, node.getId());
                return null;
            }

            // Create ItemComponent
            ItemComponent itemComponent = new ItemComponent(
                itemStack,
                Float.MAX_VALUE,
                PICKUP_DELAY_INFINITE,
                0f,
                false
            );
            holder.addComponent(ItemComponent.getComponentType(), itemComponent);

            // Use exact position (no adjustment)
            Vector3f rotation = new Vector3f(0f, 0f, 0f);
            holder.addComponent(TransformComponent.getComponentType(),
                new TransformComponent(exactPosition, rotation));

            // Scale based on node importance and state
            float scale = getScaleForNode(node, state);
            holder.addComponent(EntityScaleComponent.getComponentType(),
                new EntityScaleComponent(scale));

            holder.ensureComponent(UUIDComponent.getComponentType());

            holder.addComponent(NetworkId.getComponentType(),
                new NetworkId(store.getExternalData().takeNextNetworkId()));

            HitboxCollisionConfig hitboxConfig = HitboxCollisionConfig.getAssetMap().getAsset("SoftCollision");
            if (hitboxConfig != null) {
                holder.addComponent(HitboxCollision.getComponentType(), new HitboxCollision(hitboxConfig));
            }

            String nameplateText = NodeLabelFormatter.getNameplateText(node);
            holder.addComponent(Nameplate.getComponentType(), new Nameplate(nameplateText));

            attachSkillNodeComponent(holder, componentType, instance.getPlayerId(), node, state);

            Ref<EntityStore> entityRef = store.addEntity(holder, AddReason.SPAWN);

            LOGGER.atFine().log("Spawned node at exact position: %s state=%s scale=%.1f item=%s",
                node.getId(), state, scale, itemId);

            return entityRef;

        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to spawn node %s at exact position", node.getId());
            return null;
        }
    }

    /**
     * Attaches skill node components to a holder.
     *
     * <p>This adds:
     * <ul>
     *   <li>{@link SkillNodeComponent} - Tracks node ID, state, region, etc.</li>
     *   <li>{@link Interactable} - Marks entity as interactable (F-key target)</li>
     *   <li>{@link Interactions} - Maps USE_TARGET → SkillNodeInteraction</li>
     * </ul>
     */
    private void attachSkillNodeComponent(
            @Nonnull Holder<EntityStore> holder,
            @Nonnull ComponentType<EntityStore, SkillNodeComponent> componentType,
            @Nonnull UUID ownerPlayerId,
            @Nonnull SkillNode node,
            @Nonnull NodeState state) {

        // Add SkillNodeComponent
        SkillNodeComponent component = new SkillNodeComponent();
        component.setNodeId(node.getId());
        component.setOwnerPlayerId(ownerPlayerId);
        component.setRegion(node.getSkillTreeRegion());
        component.setState(state);
        component.setOrigin(node.isStartNode() || "origin".equals(node.getId()));
        component.setKeystone(node.isKeystone());
        component.setNotable(node.isNotable());
        holder.addComponent(componentType, component);

        // Ensure Interactable marker component (may already exist on item entities)
        holder.ensureComponent(Interactable.getComponentType());

        // Set Interactions component with Use (F-key) → SkillNodeInteraction
        // The interaction handles both allocation (AVAILABLE nodes) and inspection (LOCKED/ALLOCATED nodes)
        // Use putComponent to safely override any existing Interactions (e.g., item pickup)
        Interactions interactions = new Interactions();
        interactions.setInteractionId(InteractionType.Use, SkillNodeInteraction.DEFAULT_ID);
        interactions.setInteractionHint("Press F to (un)allocate | Click to see details");
        holder.putComponent(Interactions.getComponentType(), interactions);

        LOGGER.atFine().log("Attached skill components to node %s: Interactable=yes, Use=%s",
            node.getId(), SkillNodeInteraction.DEFAULT_ID);
    }

    /**
     * Determines the initial state for a node.
     */
    @Nonnull
    private NodeState determineNodeState(
            @Nonnull SkillNode node,
            @Nonnull Set<String> allocatedNodes,
            @Nonnull Set<String> availableNodes) {

        String nodeId = node.getId();

        // Origin is always allocated
        if (node.isStartNode() || "origin".equals(nodeId)) {
            return NodeState.ALLOCATED;
        }

        // Check if already allocated
        if (allocatedNodes.contains(nodeId)) {
            return NodeState.ALLOCATED;
        }

        // Check if available (connected to allocated)
        if (availableNodes.contains(nodeId)) {
            return NodeState.AVAILABLE;
        }

        // Otherwise locked
        return NodeState.LOCKED;
    }

    /**
     * Calculates which nodes are available based on allocated nodes.
     * A node is available if it's connected to an allocated node.
     *
     * <p>Origin is always treated as allocated for availability calculations,
     * even if it's missing from the database (legacy player safety net).
     */
    @Nonnull
    private Set<String> calculateAvailableNodes(@Nonnull Set<String> allocatedNodes) {
        Set<String> available = new HashSet<>();

        // Origin is always considered allocated for availability calculation
        // Safety net for legacy players who may not have origin in database
        Set<String> effectiveAllocated = new HashSet<>(allocatedNodes);
        effectiveAllocated.add("origin");

        for (String allocatedId : effectiveAllocated) {
            Optional<SkillNode> nodeOpt = skillTreeManager.getNode(allocatedId);
            if (nodeOpt.isPresent()) {
                SkillNode node = nodeOpt.get();
                // All connected nodes become available
                available.addAll(node.getConnections());
            }
        }

        // Remove already allocated nodes from available
        available.removeAll(effectiveAllocated);

        return available;
    }

    // ═══════════════════════════════════════════════════════════════════
    // STATE UPDATES
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Updates a node entity's item to reflect a new state.
     *
     * <p>When a node's state changes (e.g., LOCKED → AVAILABLE), the item
     * must be replaced with the appropriate light-level variant:
     * <ul>
     *   <li>LOCKED: _Light0 (no glow)</li>
     *   <li>AVAILABLE: _Light50 (medium glow)</li>
     *   <li>ALLOCATED: _Light100 (full glow)</li>
     * </ul>
     *
     * @param store     The entity store
     * @param entityRef The node entity reference
     * @param node      The skill node (for determining item type)
     * @param newState  The new state
     */
    public static void updateNodeItem(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> entityRef,
            @Nonnull SkillNode node,
            @Nonnull NodeState newState) {

        if (!entityRef.isValid()) {
            LOGGER.atWarning().log("Cannot update node item - entity ref is invalid");
            return;
        }

        ItemComponent itemComponent = store.getComponent(entityRef, ItemComponent.getComponentType());
        if (itemComponent == null) {
            LOGGER.atWarning().log("Cannot update node item - no ItemComponent found");
            return;
        }

        // Determine the new item ID based on node type and new state
        SkillTreeRegion region = node.getSkillTreeRegion();
        String newItemId;

        if (node.isKeystone()) {
            newItemId = region.getKeystoneGemItemForState(newState);
        } else if (node.isNotable()) {
            newItemId = region.getMediumCrystalItemForState(newState);
        } else if (node.isStartNode() || "origin".equals(node.getId())) {
            newItemId = region.getKeystoneGemItemForState(NodeState.ALLOCATED);  // Origin always full glow
        } else {
            newItemId = region.getEssenceItemForState(newState);
        }

        // Update the item - this triggers network sync via isNetworkOutdated flag
        ItemStack newItemStack = new ItemStack(newItemId, 1);
        if (!newItemStack.isValid()) {
            LOGGER.atWarning().log("Invalid item ID %s for node %s state %s",
                newItemId, node.getId(), newState);
            return;
        }

        itemComponent.setItemStack(newItemStack);

        // Update DynamicLight component to match the new item's light properties
        // The item's Light property (defined in JSON) determines actual light emission
        ColorLight itemLight = itemComponent.computeDynamicLight();
        if (itemLight != null) {
            DynamicLight dynamicLight = store.getComponent(entityRef, DynamicLight.getComponentType());
            if (dynamicLight != null) {
                // Update existing light
                dynamicLight.setColorLight(itemLight);
            } else {
                // Add new light component
                store.putComponent(entityRef, DynamicLight.getComponentType(), new DynamicLight(itemLight));
            }
        } else {
            // Item has no light - remove DynamicLight if it exists
            store.removeComponent(entityRef, DynamicLight.getComponentType());
        }

        // Update EntityScaleComponent to match the new state
        // (basic nodes change size: LOCKED=0.7x, AVAILABLE=1.0x, ALLOCATED=1.2x)
        EntityScaleComponent scaleComp = store.getComponent(entityRef, EntityScaleComponent.getComponentType());
        if (scaleComp != null) {
            float newScale = getScaleForNode(node, newState);
            scaleComp.setScale(newScale);
        }

        LOGGER.atInfo().log("Updated node %s item: %s (state: %s, itemValid: %b)",
            node.getId(), newItemId, newState, newItemStack.isValid());
    }

    // ═══════════════════════════════════════════════════════════════════
    // INNER CLASSES
    // ═══════════════════════════════════════════════════════════════════

    /**
     * A pending spawn request for a skill node orb.
     */
    private record PendingNodeSpawn(
        SkillSanctumInstance instance,
        SkillNode node,
        Vector3d position,
        NodeState state
    ) {}

    /**
     * Result of a spawn operation.
     */
    public record SpawnResult(
        int queued,
        int spawned,
        List<String> failedNodes
    ) {}

}
