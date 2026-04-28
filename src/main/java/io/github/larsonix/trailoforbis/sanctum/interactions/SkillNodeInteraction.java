package io.github.larsonix.trailoforbis.sanctum.interactions;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.sanctum.SkillSanctumInstance;
import io.github.larsonix.trailoforbis.sanctum.SkillSanctumManager;
import io.github.larsonix.trailoforbis.sanctum.components.SkillNodeComponent;
import io.github.larsonix.trailoforbis.skilltree.NodeState;
import io.github.larsonix.trailoforbis.skilltree.SkillTreeManager;
import io.github.larsonix.trailoforbis.skilltree.SkillTreeRegion;
import io.github.larsonix.trailoforbis.skilltree.config.SkillNode;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Interaction handler for skill node orbs in the Skill Sanctum.
 *
 * <p>When a player presses F on an AVAILABLE skill node orb, this interaction:
 * <ol>
 *   <li>Validates the player owns the sanctum</li>
 *   <li>Validates the node is in AVAILABLE state</li>
 *   <li>Calls SkillTreeManager to allocate the node</li>
 *   <li>Updates the node's visual state to ALLOCATED</li>
 *   <li>Updates adjacent nodes (LOCKED → AVAILABLE)</li>
 * </ol>
 *
 * @see SkillNodeComponent
 * @see SkillSanctumInstance
 */
public class SkillNodeInteraction extends SimpleInstantInteraction {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /**
     * Codec for serialization/registration.
     */
    public static final BuilderCodec<SkillNodeInteraction> CODEC = BuilderCodec.builder(
            SkillNodeInteraction.class, SkillNodeInteraction::new, SimpleInstantInteraction.CODEC
        )
        .documentation("Allocates a skill node when player interacts with it.")
        .build();

    /**
     * Default interaction ID (asterisk prefix indicates built-in).
     */
    public static final String DEFAULT_ID = "*AllocateSkillNode";

    /**
     * Default root interaction for the interaction chain.
     */
    public static final RootInteraction DEFAULT_ROOT = new RootInteraction(DEFAULT_ID, DEFAULT_ID);

    // ═══════════════════════════════════════════════════════════════════
    // CONSTRUCTORS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Constructor with ID (used by codec).
     *
     * @param id The interaction ID
     */
    public SkillNodeInteraction(String id) {
        super(id);
    }

    /**
     * Default constructor (used by codec).
     */
    protected SkillNodeInteraction() {
    }

    // ═══════════════════════════════════════════════════════════════════
    // INTERACTION LOGIC
    // ═══════════════════════════════════════════════════════════════════

    @Override
    protected void firstRun(
            @Nonnull InteractionType type,
            @Nonnull InteractionContext context,
            @Nonnull CooldownHandler cooldownHandler) {

        // DEBUG: Log that the interaction was triggered
        LOGGER.atInfo().log("=== SkillNodeInteraction.firstRun() TRIGGERED === type=%s", type);

        Ref<EntityStore> playerRef = context.getEntity();
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();

        // Get player component
        Player playerComponent = commandBuffer.getComponent(playerRef, Player.getComponentType());
        if (playerComponent == null) {
            LOGGER.atWarning().log("SkillNodeInteraction requires a Player but entity was: %s", playerRef);
            context.getState().state = InteractionState.Failed;
            return;
        }

        // Get target entity (the skill node orb)
        Ref<EntityStore> targetRef = context.getTargetEntity();
        if (targetRef == null || !targetRef.isValid()) {
            LOGGER.atFine().log("No valid target entity for skill node interaction");
            context.getState().state = InteractionState.Failed;
            return;
        }

        // Get SkillNodeComponent from target
        ComponentType<EntityStore, SkillNodeComponent> nodeComponentType = TrailOfOrbis.getInstance().getSkillNodeComponentType();
        if (nodeComponentType == null) {
            LOGGER.atWarning().log("SkillNodeComponent type not registered");
            context.getState().state = InteractionState.Failed;
            return;
        }

        SkillNodeComponent nodeComponent = commandBuffer.getComponent(targetRef, nodeComponentType);
        if (nodeComponent == null) {
            LOGGER.atFine().log("Target entity has no SkillNodeComponent");
            context.getState().state = InteractionState.Failed;
            return;
        }

        // Validate player owns this sanctum
        // Use PlayerRef.getUuid() instead of deprecated Entity.getUuid()
        PlayerRef playerRefComponent = commandBuffer.getComponent(playerRef, PlayerRef.getComponentType());
        if (playerRefComponent == null) {
            LOGGER.atWarning().log("Player entity missing PlayerRef component");
            context.getState().state = InteractionState.Failed;
            return;
        }
        UUID playerId = playerRefComponent.getUuid();
        UUID nodeOwnerId = nodeComponent.getOwnerPlayerId();

        if (!playerId.equals(nodeOwnerId)) {
            playerComponent.sendMessage(Message.raw("You cannot interact with another player's skill nodes.").color(MessageColors.ERROR));
            context.getState().state = InteractionState.Failed;
            return;
        }

        // Get skill tree manager first (needed for both allocation and deallocation)
        String nodeId = nodeComponent.getNodeId();
        SkillTreeManager skillTreeManager = TrailOfOrbis.getInstance().getSkillTreeManager();
        if (skillTreeManager == null) {
            LOGGER.atWarning().log("SkillTreeManager not available");
            context.getState().state = InteractionState.Failed;
            return;
        }

        // Get node state and handle based on current state
        NodeState nodeState = nodeComponent.getState();
        String regionColor = getRegionColor(nodeComponent.getRegion());
        String nodeName = formatNodeName(nodeId, nodeComponent, skillTreeManager);

        // ═══════════════════════════════════════════════════════════════════
        // HANDLE LOCKED NODES
        // ═══════════════════════════════════════════════════════════════════
        if (nodeState == NodeState.LOCKED) {
            playerComponent.sendMessage(Message.empty()
                .insert(Message.raw("This node is ").color(MessageColors.GRAY))
                .insert(Message.raw("locked").color(MessageColors.WARNING))
                .insert(Message.raw(". Allocate adjacent nodes first. ").color(MessageColors.GRAY))
                .insert(Message.raw("(Click to inspect)").color(MessageColors.INFO)));
            context.getState().state = InteractionState.Failed;
            return;
        }

        // ═══════════════════════════════════════════════════════════════════
        // HANDLE ALLOCATED NODES - DEALLOCATION
        // ═══════════════════════════════════════════════════════════════════
        if (nodeState == NodeState.ALLOCATED) {
            // Check connectivity first (leaf node check via BFS)
            if (!skillTreeManager.canDeallocate(playerId, nodeId)) {
                playerComponent.sendMessage(Message.empty()
                    .insert(Message.raw("Cannot unallocate ").color(MessageColors.GRAY))
                    .insert(Message.raw(nodeName).color(regionColor))
                    .insert(Message.raw(" - it would disconnect other nodes.").color(MessageColors.GRAY)));
                context.getState().state = InteractionState.Failed;
                return;
            }

            // Check refund points
            var skillTreeData = skillTreeManager.getSkillTreeData(playerId);
            if (skillTreeData.getSkillRefundPoints() <= 0) {
                playerComponent.sendMessage(Message.empty()
                    .insert(Message.raw("Cannot unallocate ").color(MessageColors.GRAY))
                    .insert(Message.raw(nodeName).color(regionColor))
                    .insert(Message.raw(" - no refund points remaining.").color(MessageColors.GRAY)));
                context.getState().state = InteractionState.Failed;
                return;
            }

            // Get node cost for refund message before deallocating
            int refundedPoints = skillTreeManager.getNode(nodeId)
                .map(node -> node.getCost())
                .orElse(1);

            // Perform deallocation (deducts 1 refund point, refunds skill points)
            boolean deallocated = skillTreeManager.deallocateNode(playerId, nodeId);
            if (deallocated) {
                // Update visual state to AVAILABLE
                nodeComponent.setState(NodeState.AVAILABLE);

                // Update sanctum visuals (adjacent nodes may become LOCKED)
                SkillSanctumManager sanctumManager = TrailOfOrbis.getInstance().getSkillSanctumManager();
                if (sanctumManager != null) {
                    SkillSanctumInstance instance = sanctumManager.getSanctumInstance(playerId);
                    if (instance != null) {
                        instance.onNodeDeallocated(nodeId);
                    }
                }

                LOGGER.atInfo().log("Player %s deallocated skill node %s (refunded %d points)",
                    playerId.toString().substring(0, 8), nodeId, refundedPoints);
                return;
            }

            context.getState().state = InteractionState.Failed;
            return;
        }

        // ═══════════════════════════════════════════════════════════════════
        // HANDLE AVAILABLE NODES - ALLOCATION
        // ═══════════════════════════════════════════════════════════════════
        // Check skill points available
        if (!skillTreeManager.canAllocate(playerId, nodeId)) {
            int availablePoints = skillTreeManager.getAvailablePoints(playerId);
            int nodeCost = skillTreeManager.getNode(nodeId)
                .map(node -> node.getCost())
                .orElse(1);

            if (availablePoints <= 0) {
                playerComponent.sendMessage(Message.raw("No skill points available !").color(MessageColors.ERROR));
            } else if (availablePoints < nodeCost) {
                // Dynamic message showing cost vs available
                playerComponent.sendMessage(Message.empty()
                    .insert(Message.raw("Not enough skill points! ").color(MessageColors.ERROR))
                    .insert(Message.raw("Need " + nodeCost).color(MessageColors.WARNING))
                    .insert(Message.raw(", have ").color(MessageColors.GRAY))
                    .insert(Message.raw(String.valueOf(availablePoints)).color(MessageColors.WHITE)));
            } else {
                // Other allocation failure (shouldn't reach here for AVAILABLE nodes, but fallback)
                playerComponent.sendMessage(Message.raw("Cannot allocate this node.").color(MessageColors.ERROR));
            }
            context.getState().state = InteractionState.Failed;
            return;
        }

        // Perform allocation
        boolean allocated = skillTreeManager.allocateNode(playerId, nodeId);
        if (!allocated) {
            playerComponent.sendMessage(Message.raw("Failed to allocate node.").color(MessageColors.ERROR));
            context.getState().state = InteractionState.Failed;
            return;
        }

        // Update node state to ALLOCATED
        nodeComponent.setState(NodeState.ALLOCATED);

        // Log the allocation
        LOGGER.atInfo().log("Player %s allocated skill node %s",
            playerId.toString().substring(0, 8), nodeId);

        // Update sanctum instance (triggers adjacent node updates)
        SkillSanctumManager sanctumManager = TrailOfOrbis.getInstance().getSkillSanctumManager();
        if (sanctumManager != null) {
            SkillSanctumInstance instance = sanctumManager.getSanctumInstance(playerId);
            if (instance != null) {
                instance.onNodeAllocated(nodeId);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets the hex color for a skill tree region.
     */
    private String getRegionColor(SkillTreeRegion region) {
        if (region == null) {
            return MessageColors.WHITE;
        }
        return switch (region) {
            case CORE -> MessageColors.GOLD;         // Gold (Origin)
            case FIRE -> MessageColors.ERROR;        // Orange-Red (Fire)
            case WATER -> MessageColors.INFO;        // Cyan (Water)
            case LIGHTNING -> MessageColors.WARNING; // Yellow (Lightning)
            case EARTH -> MessageColors.SUCCESS;     // Green-ish (Earth)
            case VOID -> MessageColors.DARK_PURPLE;  // Purple (Void)
            case WIND -> MessageColors.BLUE;         // Green (Wind)
            // Octant arms use their exact theme color hex
            case HAVOC -> "#FF4422";
            case JUGGERNAUT -> "#CC6633";
            case STRIKER -> "#FFAA22";
            case WARDEN -> "#88AA44";
            case WARLOCK -> "#9944CC";
            case LICH -> "#6677AA";
            case TEMPEST -> "#44BBAA";
            case SENTINEL -> "#77AA88";
        };
    }

    /**
     * Formats a node name for display, using the configured display name from skill tree config.
     *
     * @param nodeId The node's unique identifier
     * @param component The node's ECS component (for type flags)
     * @param skillTreeManager The skill tree manager (for display name lookup)
     * @return Formatted display name with type prefix if applicable
     */
    private String formatNodeName(String nodeId, SkillNodeComponent component, SkillTreeManager skillTreeManager) {
        // Look up the display name from config, falling back to formatted ID if not found
        String displayName = skillTreeManager.getNode(nodeId)
            .map(SkillNode::getName)
            .orElseGet(() -> capitalizeNodeId(nodeId));

        if (component.isKeystone()) {
            return "Keystone : " + displayName;
        } else if (component.isNotable()) {
            return "Notable : " + displayName;
        } else if (component.isOrigin()) {
            return "Origin";
        } else {
            return displayName;
        }
    }

    /**
     * Capitalizes a node ID for display (e.g., "might_1_2" → "Might 1 2").
     */
    private String capitalizeNodeId(String nodeId) {
        if (nodeId == null || nodeId.isEmpty()) {
            return "Unknown";
        }
        String[] parts = nodeId.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) {
                    sb.append(part.substring(1));
                }
            }
        }
        return sb.toString();
    }

    @Nonnull
    @Override
    public String toString() {
        return "SkillNodeInteraction{} " + super.toString();
    }
}
