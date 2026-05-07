package io.github.larsonix.trailoforbis.compat;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;

/**
 * Reflection bridge for Loot4Everyone's ECS components.
 *
 * <p>Handles two L4E integration points that the existing
 * {@link Loot4EveryoneBridge} does not cover:
 * <ul>
 *   <li><b>OpenedContainerComponent</b> — ECS component on the player entity
 *       that L4E uses as a lock to prevent opening multiple chests. If it
 *       becomes stale (world transition, disconnect), L4E cancels all future
 *       chest opens for that player.</li>
 *   <li><b>LootChestTemplate</b> — ChunkStore resource that L4E auto-populates
 *       for every container with a vanilla droplist. We remove entries after
 *       processing a container ourselves to prevent L4E from interfering on
 *       subsequent opens.</li>
 * </ul>
 *
 * <p>All methods gracefully no-op when L4E is not present or reflection fails.
 *
 * @see Loot4EveryoneBridge
 */
public final class L4EComponentBridge {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // Cached reflection handles — all immutable after construction
    @SuppressWarnings("rawtypes")
    private final ComponentType containerComponentType;
    @SuppressWarnings("rawtypes")
    private final ResourceType templateResourceType;
    private final Method templateHasMethod;    // boolean hasTemplate(int, int, int)
    private final Method templateRemoveMethod; // void removeTemplate(int, int, int)

    private L4EComponentBridge(
            @Nonnull ComponentType<?, ?> containerComponentType,
            @Nonnull ResourceType<?, ?> templateResourceType,
            @Nonnull Method templateHasMethod,
            @Nonnull Method templateRemoveMethod) {
        this.containerComponentType = containerComponentType;
        this.templateResourceType = templateResourceType;
        this.templateHasMethod = templateHasMethod;
        this.templateRemoveMethod = templateRemoveMethod;
    }

    /**
     * Attempts to create the bridge by resolving L4E's API via reflection.
     *
     * @return the bridge, or {@code null} if L4E is not present or resolution fails
     */
    @Nullable
    public static L4EComponentBridge tryCreate() {
        try {
            Class<?> l4eClass = Class.forName("org.mimstar.plugin.Loot4Everyone");
            Object l4eInstance = l4eClass.getMethod("get").invoke(null);
            if (l4eInstance == null) {
                LOGGER.atWarning().log("Loot4Everyone.get() returned null");
                return null;
            }

            // OpenedContainerComponent type
            Object containerType = l4eClass.getMethod("getContainerComponentType").invoke(l4eInstance);
            if (!(containerType instanceof ComponentType<?, ?>)) {
                LOGGER.atWarning().log("L4E getContainerComponentType() returned unexpected type");
                return null;
            }

            // LootChestTemplate resource type
            Object templateResType = l4eClass.getMethod("getlootChestTemplateResourceType").invoke(l4eInstance);
            if (!(templateResType instanceof ResourceType<?, ?>)) {
                LOGGER.atWarning().log("L4E getlootChestTemplateResourceType() returned unexpected type");
                return null;
            }

            // LootChestTemplate methods
            Class<?> templateClass = Class.forName("org.mimstar.plugin.resources.LootChestTemplate");
            Method hasMethod = templateClass.getMethod("hasTemplate", int.class, int.class, int.class);
            Method removeMethod = templateClass.getMethod("removeTemplate", int.class, int.class, int.class);

            LOGGER.atInfo().log("L4E component bridge initialized (OpenedContainerComponent + LootChestTemplate)");
            return new L4EComponentBridge(
                (ComponentType<?, ?>) containerType,
                (ResourceType<?, ?>) templateResType,
                hasMethod,
                removeMethod);

        } catch (ClassNotFoundException e) {
            // L4E not installed — expected, no warning
            return null;
        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to initialize L4E component bridge: %s", e.getMessage());
            return null;
        }
    }

    // =========================================================================
    // OpenedContainerComponent operations
    // =========================================================================

    /**
     * Removes L4E's {@code OpenedContainerComponent} from a player via Holder.
     *
     * <p>Use this in {@code DrainPlayerFromWorldEvent} and {@code PlayerDisconnectEvent}
     * handlers where a {@link Holder} is available.
     *
     * @param holder the player's entity holder
     * @return {@code true} if the component was present and removed
     */
    @SuppressWarnings("unchecked")
    public boolean tryRemoveOpenedContainer(@Nonnull Holder<EntityStore> holder) {
        try {
            return holder.tryRemoveComponent(
                (ComponentType<EntityStore, ?>) containerComponentType);
        } catch (Exception e) {
            LOGGER.atFine().log("L4E OpenedContainerComponent removal failed: %s", e.getMessage());
            return false;
        }
    }

    /**
     * Removes L4E's {@code OpenedContainerComponent} via CommandBuffer.
     *
     * <p>Use this inside ECS event handlers (like {@code UseBlockEvent.Pre})
     * where a {@link CommandBuffer} is available instead of a Holder.
     *
     * @param commandBuffer the ECS command buffer
     * @param playerRef     the player entity reference
     */
    @SuppressWarnings("unchecked")
    public void removeOpenedContainerViaCommandBuffer(
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull Ref<EntityStore> playerRef) {
        try {
            commandBuffer.removeComponent(playerRef,
                (ComponentType<EntityStore, ?>) containerComponentType);
        } catch (Exception e) {
            LOGGER.atFine().log("L4E OpenedContainerComponent commandBuffer removal failed: %s", e.getMessage());
        }
    }

    /**
     * Checks if a player has L4E's {@code OpenedContainerComponent}.
     *
     * @param store     the entity store
     * @param playerRef the player entity reference
     * @return {@code true} if the stale lock component exists
     */
    @SuppressWarnings("unchecked")
    public boolean hasOpenedContainer(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> playerRef) {
        try {
            return store.getComponent(playerRef,
                (ComponentType<EntityStore, ?>) containerComponentType) != null;
        } catch (Exception e) {
            return false;
        }
    }

    // =========================================================================
    // LootChestTemplate operations
    // =========================================================================

    /**
     * Checks if L4E has a template registered at the given position.
     *
     * @param chunkStoreStore the chunk store
     * @param x               block X
     * @param y               block Y
     * @param z               block Z
     * @return {@code true} if L4E manages this container
     */
    @SuppressWarnings("unchecked")
    public boolean hasTemplate(
            @Nonnull Store<ChunkStore> chunkStoreStore,
            int x, int y, int z) {
        try {
            Object template = chunkStoreStore.getResource(
                (ResourceType<ChunkStore, ?>) templateResourceType);
            if (template == null) return false;
            Object result = templateHasMethod.invoke(template, x, y, z);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Removes L4E's auto-registered template for a container position.
     *
     * <p>Called after our {@code ContainerLootInterceptor} processes a container
     * to prevent L4E from interfering on subsequent opens.
     *
     * @param chunkStoreStore the chunk store
     * @param x               block X
     * @param y               block Y
     * @param z               block Z
     */
    @SuppressWarnings("unchecked")
    public void removeTemplate(
            @Nonnull Store<ChunkStore> chunkStoreStore,
            int x, int y, int z) {
        try {
            Object template = chunkStoreStore.getResource(
                (ResourceType<ChunkStore, ?>) templateResourceType);
            if (template != null) {
                templateRemoveMethod.invoke(template, x, y, z);
            }
        } catch (Exception e) {
            LOGGER.atFine().log("L4E template removal failed at (%d, %d, %d): %s",
                x, y, z, e.getMessage());
        }
    }
}
