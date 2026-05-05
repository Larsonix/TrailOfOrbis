package io.github.larsonix.trailoforbis.compat;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import com.hypixel.hytale.component.ResourceType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Reflection-based bridge to Loot4Everyone's per-player chest API.
 *
 * <p>Enables Trail of Orbis reward chests to use L4E's proven per-player
 * instancing system when it's installed, without compile-time dependency.
 *
 * <p>When L4E is absent, this class is never instantiated —
 * {@link RewardChestManager} uses its standalone system instead.
 */
public final class Loot4EveryoneBridge {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // ── Cached reflection handles ──
    private final Object l4eInstance;
    private final Object templateResourceType;   // ResourceType<ChunkStore, LootChestTemplate>
    private final Object playerLootComponentType; // ComponentType<EntityStore, PlayerLoot>

    // LootChestTemplate methods
    private final Method templateSaveMethod;      // saveTemplate(int, int, int, List<ItemStack>, String)
    private final Method templateRemoveMethod;    // removeTemplate(int, int, int)

    // PlayerLoot methods
    private final Method playerLootSetMethod;     // setInventory(int, int, int, String, List<ItemStack>)
    private final Method playerLootResetMethod;   // resetChest(int, int, int, String)

    // LootChestConfig (v1.3.6+) — nullable, graceful degradation if not available
    @Nullable private final Object configResourceType;       // ResourceType<ChunkStore, LootChestConfig>
    @Nullable private final Method configSetBreakable;       // setCanPlayerBreakLootChests(boolean)
    @Nullable private final Method configSetParticles;       // setParticlesAppear(boolean)
    @Nullable private final Method configSetParticleColor;   // setParticlesColor(String)
    @Nullable private final Method configSetMessage;         // setMessageAppear(boolean)

    /**
     * Creates the bridge by resolving L4E's API via reflection.
     *
     * @throws ReflectiveOperationException if L4E API cannot be resolved
     */
    public Loot4EveryoneBridge() throws ReflectiveOperationException {
        Class<?> l4eClass = Class.forName("org.mimstar.plugin.Loot4Everyone");
        Method getMethod = l4eClass.getMethod("get");
        l4eInstance = getMethod.invoke(null);
        if (l4eInstance == null) {
            throw new IllegalStateException("Loot4Everyone.get() returned null");
        }

        // Get resource/component types
        Method getTemplateType = l4eClass.getMethod("getlootChestTemplateResourceType");
        templateResourceType = getTemplateType.invoke(l4eInstance);

        Method getPlayerLootType = l4eClass.getMethod("getPlayerLootcomponentType");
        playerLootComponentType = getPlayerLootType.invoke(l4eInstance);

        // Resolve LootChestTemplate methods
        Class<?> templateClass = Class.forName("org.mimstar.plugin.resources.LootChestTemplate");
        templateSaveMethod = templateClass.getMethod("saveTemplate", int.class, int.class, int.class, List.class, String.class);
        templateRemoveMethod = templateClass.getMethod("removeTemplate", int.class, int.class, int.class);

        // Resolve PlayerLoot methods
        Class<?> playerLootClass = Class.forName("org.mimstar.plugin.components.PlayerLoot");
        playerLootSetMethod = playerLootClass.getMethod("setInventory", int.class, int.class, int.class, String.class, List.class);
        playerLootResetMethod = playerLootClass.getMethod("resetChest", int.class, int.class, int.class, String.class);

        // Resolve LootChestConfig (v1.3.6+) — optional, degrades gracefully
        Object resolvedConfigType = null;
        Method resolvedSetBreakable = null;
        Method resolvedSetParticles = null;
        Method resolvedSetParticleColor = null;
        Method resolvedSetMessage = null;
        try {
            Method getConfigType = l4eClass.getMethod("getLootChestConfigResourceType");
            resolvedConfigType = getConfigType.invoke(l4eInstance);

            Class<?> configClass = Class.forName("org.mimstar.plugin.resources.LootChestConfig");
            resolvedSetBreakable = configClass.getMethod("setCanPlayerBreakLootChests", boolean.class);
            resolvedSetParticles = configClass.getMethod("setParticlesAppear", boolean.class);
            resolvedSetParticleColor = configClass.getMethod("setParticlesColor", String.class);
            resolvedSetMessage = configClass.getMethod("setMessageAppear", boolean.class);
            LOGGER.at(Level.INFO).log("L4E LootChestConfig API resolved (v1.3.6+)");
        } catch (Exception e) {
            LOGGER.at(Level.INFO).log("L4E LootChestConfig API not available (pre-1.3.6) — world config disabled");
        }
        this.configResourceType = resolvedConfigType;
        this.configSetBreakable = resolvedSetBreakable;
        this.configSetParticles = resolvedSetParticles;
        this.configSetParticleColor = resolvedSetParticleColor;
        this.configSetMessage = resolvedSetMessage;

        LOGGER.at(Level.INFO).log("Loot4Everyone bridge initialized — all methods cached");
    }

    /**
     * Registers a chest position as a L4E loot template.
     *
     * <p>MUST be called on the world thread (needs ChunkStore access).
     *
     * @param world The world containing the chest
     * @param x     Chest block X
     * @param y     Chest block Y
     * @param z     Chest block Z
     * @param items Template items (used for persistent mode)
     * @return true if registration succeeded
     */
    @SuppressWarnings("unchecked")
    public boolean registerTemplate(@Nonnull World world, int x, int y, int z,
                                     @Nonnull List<ItemStack> items) {
        try {
            Store<ChunkStore> chunkStore = world.getChunkStore().getStore();
            Object template = chunkStore.getResource(
                (com.hypixel.hytale.component.ResourceType<ChunkStore, ?>) templateResourceType);
            if (template == null) {
                LOGGER.at(Level.WARNING).log("L4E LootChestTemplate resource not found in world");
                return false;
            }

            templateSaveMethod.invoke(template, x, y, z, items, "custom");
            LOGGER.at(Level.INFO).log("Registered L4E template at (%d, %d, %d)", x, y, z);
            return true;
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).log("Failed to register L4E template: %s", e.getMessage());
            return false;
        }
    }

    /**
     * Pre-populates a player's per-player loot for a chest.
     *
     * <p>MUST be called on the world thread (needs EntityStore access).
     * Items are padded to chest capacity with nulls (L4E requires exact capacity match).
     *
     * @param world      The world containing the chest
     * @param playerId   The player's UUID
     * @param x          Chest block X
     * @param y          Chest block Y
     * @param z          Chest block Z
     * @param items      The player's reward items
     * @param capacity   The chest's slot capacity (items padded to this size)
     * @return true if pre-population succeeded
     */
    @SuppressWarnings("unchecked")
    public boolean presetPlayerLoot(@Nonnull World world, @Nonnull UUID playerId,
                                     int x, int y, int z,
                                     @Nonnull List<ItemStack> items, int capacity) {
        try {
            PlayerRef playerRef = Universe.get().getPlayer(playerId);
            if (playerRef == null || !playerRef.isValid()) {
                LOGGER.at(Level.FINE).log("Player %s not online — skipping L4E loot preset",
                    playerId.toString().substring(0, 8));
                return false;
            }

            Ref<EntityStore> entityRef = playerRef.getReference();
            if (entityRef == null || !entityRef.isValid()) {
                return false;
            }

            Store<EntityStore> store = world.getEntityStore().getStore();
            Object playerLoot = store.getComponent(entityRef,
                (ComponentType<EntityStore, ?>) playerLootComponentType);
            if (playerLoot == null) {
                LOGGER.at(Level.WARNING).log("PlayerLoot component not found for player %s",
                    playerId.toString().substring(0, 8));
                return false;
            }

            // Pad items to chest capacity (L4E requires exact capacity match)
            List<ItemStack> paddedItems = new ArrayList<>(capacity);
            paddedItems.addAll(items);
            while (paddedItems.size() < capacity) {
                paddedItems.add(null);
            }

            String worldName = world.getName();
            playerLootSetMethod.invoke(playerLoot, x, y, z, worldName, paddedItems);

            LOGGER.at(Level.FINE).log("Pre-set L4E loot for player %s at (%d,%d,%d): %d items",
                playerId.toString().substring(0, 8), x, y, z, items.size());
            return true;
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).log("Failed to preset L4E loot for player %s: %s",
                playerId.toString().substring(0, 8), e.getMessage());
            return false;
        }
    }

    /**
     * Removes a L4E template registration for a chest position.
     *
     * <p>MUST be called on the world thread.
     */
    @SuppressWarnings("unchecked")
    public void removeTemplate(@Nonnull World world, int x, int y, int z) {
        try {
            Store<ChunkStore> chunkStore = world.getChunkStore().getStore();
            Object template = chunkStore.getResource(
                (com.hypixel.hytale.component.ResourceType<ChunkStore, ?>) templateResourceType);
            if (template != null) {
                templateRemoveMethod.invoke(template, x, y, z);
                LOGGER.at(Level.FINE).log("Removed L4E template at (%d, %d, %d)", x, y, z);
            }
        } catch (Exception e) {
            LOGGER.at(Level.FINE).log("Failed to remove L4E template: %s", e.getMessage());
        }
    }

    /**
     * Resets a player's L4E loot data for a specific chest.
     *
     * <p>MUST be called on the world thread.
     */
    @SuppressWarnings("unchecked")
    public void resetPlayerLoot(@Nonnull World world, @Nonnull UUID playerId,
                                 int x, int y, int z) {
        try {
            PlayerRef playerRef = Universe.get().getPlayer(playerId);
            if (playerRef == null || !playerRef.isValid()) {
                return;
            }

            Ref<EntityStore> entityRef = playerRef.getReference();
            if (entityRef == null || !entityRef.isValid()) {
                return;
            }

            Store<EntityStore> store = world.getEntityStore().getStore();
            Object playerLoot = store.getComponent(entityRef,
                (ComponentType<EntityStore, ?>) playerLootComponentType);
            if (playerLoot == null) {
                return;
            }

            String worldName = world.getName();
            playerLootResetMethod.invoke(playerLoot, x, y, z, worldName);
        } catch (Exception e) {
            LOGGER.at(Level.FINE).log("Failed to reset L4E loot for player %s: %s",
                playerId.toString().substring(0, 8), e.getMessage());
        }
    }

    /**
     * Configures L4E's per-world loot chest settings for a realm world.
     *
     * <p>Sets break protection, particle effects, and message on the world's
     * {@code LootChestConfig} resource. These apply to ALL L4E-managed chests
     * in this world (which is fine for realms — one chest per realm).
     *
     * <p>MUST be called on the world thread, AFTER L4E's StartWorldEvent handler
     * has fired (i.e., after the world has started). Otherwise L4E will overwrite
     * our settings with the default world's config.
     *
     * <p>Degrades gracefully if L4E is pre-1.3.6 (no LootChestConfig API).
     *
     * @param world          The realm world
     * @param particleColor  Hex color for chest particles (e.g. "#ffdd00ff"), or null to disable particles
     * @return true if configuration was applied
     */
    @SuppressWarnings("unchecked")
    public boolean configureRealmWorld(@Nonnull World world, @Nullable String particleColor) {
        if (configResourceType == null || configSetBreakable == null) {
            return false;
        }

        try {
            Store<ChunkStore> chunkStore = world.getChunkStore().getStore();
            Object config = chunkStore.getResource(
                (ResourceType<ChunkStore, ?>) configResourceType);
            if (config == null) {
                LOGGER.at(Level.FINE).log("LootChestConfig resource not found in world %s", world.getName());
                return false;
            }

            // Disable chest breaking in realm worlds
            configSetBreakable.invoke(config, false);

            // Configure particle effects
            if (particleColor != null && configSetParticles != null && configSetParticleColor != null) {
                configSetParticles.invoke(config, true);
                configSetParticleColor.invoke(config, particleColor);
            }

            // Enable "opened" message
            if (configSetMessage != null) {
                configSetMessage.invoke(config, true);
            }

            LOGGER.at(Level.INFO).log("Configured L4E world settings for %s (breakable=false, particles=%s)",
                world.getName(), particleColor != null ? particleColor : "disabled");
            return true;
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).log("Failed to configure L4E world settings: %s", e.getMessage());
            return false;
        }
    }

    /**
     * Whether the v1.3.6+ LootChestConfig API is available.
     */
    public boolean hasWorldConfigSupport() {
        return configResourceType != null;
    }
}
