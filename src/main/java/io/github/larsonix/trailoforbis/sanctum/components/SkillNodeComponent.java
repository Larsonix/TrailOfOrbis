package io.github.larsonix.trailoforbis.sanctum.components;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.skilltree.NodeState;
import io.github.larsonix.trailoforbis.skilltree.SkillTreeRegion;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;

/**
 * ECS component marking an entity as a skill node orb in a Skill Sanctum.
 *
 * <p>Attached to floating orb entities spawned by {@code SkillSanctumNodeSpawner} to:
 * <ul>
 *   <li>Track which skill tree node this orb represents</li>
 *   <li>Identify which player's sanctum this orb belongs to</li>
 *   <li>Store the current visual state (LOCKED, AVAILABLE, ALLOCATED)</li>
 *   <li>Track the skill tree region for color theming</li>
 * </ul>
 *
 * <p>Used by:
 * <ul>
 *   <li>{@code SkillNodeInteraction} - To handle F-key allocation</li>
 *   <li>{@code SkillSanctumConnectionRenderer} - To determine beam colors</li>
 *   <li>{@code SkillSanctumInstance} - To track all nodes in a sanctum</li>
 * </ul>
 *
 * @see io.github.larsonix.trailoforbis.sanctum.SkillSanctumManager
 */
public class SkillNodeComponent implements Component<EntityStore> {

    // ═══════════════════════════════════════════════════════════════════
    // CODEC
    // ═══════════════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    public static final BuilderCodec<SkillNodeComponent> CODEC = BuilderCodec.builder(
            SkillNodeComponent.class, SkillNodeComponent::new
        )
        .append(new KeyedCodec<>("NodeId", Codec.STRING),
                SkillNodeComponent::setNodeId, SkillNodeComponent::getNodeId).add()
        .append(new KeyedCodec<>("OwnerPlayerId", Codec.UUID_STRING),
                SkillNodeComponent::setOwnerPlayerId,
                c -> Objects.requireNonNullElse(c.getOwnerPlayerId(), new UUID(0L, 0L))).add()
        .append(new KeyedCodec<>("Region", (Codec<SkillTreeRegion>) (Codec<?>) new EnumCodec<>(SkillTreeRegion.class)),
                SkillNodeComponent::setRegion, SkillNodeComponent::getRegion).add()
        .append(new KeyedCodec<>("State", (Codec<NodeState>) (Codec<?>) new EnumCodec<>(NodeState.class)),
                SkillNodeComponent::setState, SkillNodeComponent::getState).add()
        .append(new KeyedCodec<>("IsOrigin", Codec.BOOLEAN),
                SkillNodeComponent::setOrigin, SkillNodeComponent::isOrigin).add()
        .append(new KeyedCodec<>("IsKeystone", Codec.BOOLEAN),
                SkillNodeComponent::setKeystone, SkillNodeComponent::isKeystone).add()
        .append(new KeyedCodec<>("IsNotable", Codec.BOOLEAN),
                SkillNodeComponent::setNotable, SkillNodeComponent::isNotable).add()
        .build();

    // ═══════════════════════════════════════════════════════════════════
    // STATIC TYPE REFERENCE
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Static reference to the component type - set during plugin init.
     * Allows {@link #getComponentType()} to work in async contexts.
     */
    @Nullable
    public static ComponentType<EntityStore, SkillNodeComponent> TYPE = null;

    // ═══════════════════════════════════════════════════════════════════
    // FIELDS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * The skill tree node ID this orb represents.
     * Corresponds to the node ID in skill-tree.yml (e.g., "str_1a", "origin").
     */
    private String nodeId;

    /**
     * The UUID of the player who owns this sanctum instance.
     * Used to validate interactions and track ownership.
     */
    private UUID ownerPlayerId;

    /**
     * The skill tree region this node belongs to.
     * Determines the color theme for the orb.
     */
    private SkillTreeRegion region;

    /**
     * The current visual state of this node.
     */
    private NodeState state;

    /**
     * Whether this node is the origin (starting) node.
     * The origin is always allocated and has special visuals.
     */
    private boolean isOrigin;

    /**
     * Whether this node is a keystone (powerful, limited choice).
     */
    private boolean isKeystone;

    /**
     * Whether this node is a notable (significant passive).
     */
    private boolean isNotable;

    // ═══════════════════════════════════════════════════════════════════
    // CONSTRUCTORS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Default constructor - required for component registration.
     */
    public SkillNodeComponent() {
        this.nodeId = "";
        this.ownerPlayerId = null;
        this.region = SkillTreeRegion.CORE;
        this.state = NodeState.LOCKED;
        this.isOrigin = false;
        this.isKeystone = false;
        this.isNotable = false;
    }

    /**
     * Copy constructor - required for {@link #clone()}.
     */
    private SkillNodeComponent(@Nonnull SkillNodeComponent other) {
        this.nodeId = other.nodeId;
        this.ownerPlayerId = other.ownerPlayerId;
        this.region = other.region;
        this.state = other.state;
        this.isOrigin = other.isOrigin;
        this.isKeystone = other.isKeystone;
        this.isNotable = other.isNotable;
    }

    // ═══════════════════════════════════════════════════════════════════
    // STATIC ACCESSOR
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets the component type from the static reference.
     *
     * @return The registered component type
     * @throws IllegalStateException if component not yet registered
     */
    @Nonnull
    public static ComponentType<EntityStore, SkillNodeComponent> getComponentType() {
        if (TYPE == null) {
            throw new IllegalStateException("SkillNodeComponent not yet registered");
        }
        return TYPE;
    }

    // ═══════════════════════════════════════════════════════════════════
    // GETTERS AND SETTERS
    // ═══════════════════════════════════════════════════════════════════

    @Nonnull
    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(@Nonnull String nodeId) {
        this.nodeId = nodeId;
    }

    @Nullable
    public UUID getOwnerPlayerId() {
        return ownerPlayerId;
    }

    public void setOwnerPlayerId(@Nonnull UUID ownerPlayerId) {
        this.ownerPlayerId = ownerPlayerId;
    }

    @Nonnull
    public SkillTreeRegion getRegion() {
        return region;
    }

    public void setRegion(@Nonnull SkillTreeRegion region) {
        this.region = region;
    }

    @Nonnull
    public NodeState getState() {
        return state;
    }

    public void setState(@Nonnull NodeState state) {
        this.state = state;
    }

    public boolean isOrigin() {
        return isOrigin;
    }

    public void setOrigin(boolean origin) {
        this.isOrigin = origin;
    }

    public boolean isKeystone() {
        return isKeystone;
    }

    public void setKeystone(boolean keystone) {
        this.isKeystone = keystone;
    }

    public boolean isNotable() {
        return isNotable;
    }

    public void setNotable(boolean notable) {
        this.isNotable = notable;
    }

    // ═══════════════════════════════════════════════════════════════════
    // CONVENIENCE METHODS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Checks if this node is interactable (can be allocated).
     *
     * @return true if state is AVAILABLE
     */
    public boolean isInteractable() {
        return state == NodeState.AVAILABLE;
    }

    /**
     * Checks if this node has been allocated.
     *
     * @return true if state is ALLOCATED
     */
    public boolean isAllocated() {
        return state == NodeState.ALLOCATED;
    }

    /**
     * Checks if this node belongs to a specific player.
     *
     * @param playerId The player UUID to check
     * @return true if this node belongs to the specified player
     */
    public boolean belongsToPlayer(@Nonnull UUID playerId) {
        return ownerPlayerId != null && ownerPlayerId.equals(playerId);
    }

    /**
     * Gets the theme color for this node based on its region.
     *
     * @return Hex color string (e.g., "#ff4444")
     */
    @Nonnull
    public String getThemeColor() {
        return region.getThemeColor();
    }

    /**
     * Gets the current light intensity based on state.
     *
     * @return Light intensity multiplier (0.0 to 1.0)
     */
    public float getLightIntensity() {
        return state.getLightIntensity();
    }

    // ═══════════════════════════════════════════════════════════════════
    // COMPONENT INTERFACE
    // ═══════════════════════════════════════════════════════════════════

    @Override
    @Nonnull
    public Component<EntityStore> clone() {
        return new SkillNodeComponent(this);
    }

    @Override
    public String toString() {
        return String.format(
            "SkillNodeComponent{node='%s', owner=%s, region=%s, state=%s}",
            nodeId,
            ownerPlayerId != null ? ownerPlayerId.toString().substring(0, 8) : "null",
            region,
            state
        );
    }
}
