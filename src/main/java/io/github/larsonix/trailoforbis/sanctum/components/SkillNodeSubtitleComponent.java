package io.github.larsonix.trailoforbis.sanctum.components;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;

/**
 * Marker component for skill node subtitle entities.
 *
 * <p>Subtitle entities display the node description/modifier hints below the main node.
 * This component tracks ownership and links the subtitle to its parent node.
 */
public class SkillNodeSubtitleComponent implements Component<EntityStore> {

    // ==================== CODEC ====================

    public static final BuilderCodec<SkillNodeSubtitleComponent> CODEC = BuilderCodec.builder(
            SkillNodeSubtitleComponent.class, SkillNodeSubtitleComponent::new
        )
        .append(new KeyedCodec<>("NodeId", Codec.STRING),
                SkillNodeSubtitleComponent::setNodeId,
                c -> Objects.requireNonNullElse(c.getNodeId(), "")).add()
        .append(new KeyedCodec<>("OwnerPlayerId", Codec.UUID_STRING),
                SkillNodeSubtitleComponent::setOwnerPlayerId,
                c -> Objects.requireNonNullElse(c.getOwnerPlayerId(), new UUID(0L, 0L))).add()
        .build();

    // ==================== STATIC TYPE ====================

    private static ComponentType<EntityStore, SkillNodeSubtitleComponent> componentType;

    /**
     * The ID of the parent skill node (e.g., "str_1a", "origin").
     */
    private String nodeId;

    /**
     * The UUID of the player who owns this sanctum instance.
     */
    private UUID ownerPlayerId;

    public SkillNodeSubtitleComponent() {
    }

    public SkillNodeSubtitleComponent(@Nonnull String nodeId, @Nonnull UUID ownerPlayerId) {
        this.nodeId = nodeId;
        this.ownerPlayerId = ownerPlayerId;
    }

    @Nullable
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
    @Override
    public Component<EntityStore> clone() {
        SkillNodeSubtitleComponent clone = new SkillNodeSubtitleComponent();
        clone.nodeId = this.nodeId;
        clone.ownerPlayerId = this.ownerPlayerId;
        return clone;
    }

    /**
     * Gets the registered component type.
     *
     * @return The component type, or null if not yet registered
     */
    @Nullable
    public static ComponentType<EntityStore, SkillNodeSubtitleComponent> getComponentType() {
        return componentType;
    }

    /**
     * Sets the component type after registration.
     *
     * @param type The registered component type
     */
    public static void setComponentType(@Nonnull ComponentType<EntityStore, SkillNodeSubtitleComponent> type) {
        componentType = type;
    }
}
