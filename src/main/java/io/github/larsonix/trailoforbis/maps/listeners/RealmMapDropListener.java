package io.github.larsonix.trailoforbis.maps.listeners;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;

import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.api.ServiceRegistry;
import io.github.larsonix.trailoforbis.gear.item.CustomItemInstanceId;
import io.github.larsonix.trailoforbis.gear.item.CustomItemSyncService;
import io.github.larsonix.trailoforbis.gear.item.ItemRegistryService;
import io.github.larsonix.trailoforbis.gear.loot.DropLevelBlender;
import io.github.larsonix.trailoforbis.leveling.api.LevelingService;
import io.github.larsonix.trailoforbis.maps.RealmsManager;
import io.github.larsonix.trailoforbis.maps.config.RealmsConfig;
import com.hypixel.hytale.server.core.universe.world.World;
import io.github.larsonix.trailoforbis.maps.core.RealmMapData;
import io.github.larsonix.trailoforbis.maps.items.RealmMapGenerator;
import io.github.larsonix.trailoforbis.maps.items.RealmMapUtils;
import io.github.larsonix.trailoforbis.mobs.component.MobScalingComponent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * ECS system that handles realm map drops from mob deaths.
 *
 * <p>Extends {@link DeathSystems.OnDeathSystem} to react when any entity dies.
 * Calculates drop chance based on:
 * <ul>
 *   <li>Base drop chance from config</li>
 *   <li>Per-level bonus</li>
 *   <li>Boss/Elite multipliers</li>
 * </ul>
 *
 * <p>When a map drops, uses {@link RealmMapGenerator} to create the map data
 * and {@link RealmMapUtils} to write it to an {@link ItemStack}.
 */
public class RealmMapDropListener extends DeathSystems.OnDeathSystem {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // Plugin reference for lazy access to RealmsManager (initialized in start())
    private final TrailOfOrbis plugin;

    /**
     * Mob type classification for drop calculations.
     */
    public enum MobType {
        NORMAL(1.0),
        ELITE(2.0),
        BOSS(5.0);

        private final double multiplier;

        MobType(double multiplier) {
            this.multiplier = multiplier;
        }

        public double getMultiplier() {
            return multiplier;
        }
    }

    /**
     * Creates a new realm map drop listener with lazy initialization.
     *
     * <p>Config and map generator are obtained lazily from RealmsManager
     * via the plugin reference. This allows the system to be registered
     * in setup() before RealmsManager is initialized in start().
     *
     * @param plugin The plugin instance for lazy access to RealmsManager
     */
    public RealmMapDropListener(@Nonnull TrailOfOrbis plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
    }

    /**
     * Gets the realms configuration lazily from RealmsManager.
     *
     * @return The configuration, or null if RealmsManager not initialized
     */
    @Nullable
    private RealmsConfig getConfig() {
        RealmsManager manager = plugin.getRealmsManager();
        return manager != null ? manager.getConfig() : null;
    }

    /**
     * Gets the map generator lazily from RealmsManager.
     *
     * @return The map generator, or null if RealmsManager not initialized
     */
    @Nullable
    private RealmMapGenerator getMapGenerator() {
        RealmsManager manager = plugin.getRealmsManager();
        return manager != null ? manager.getMapGenerator() : null;
    }

    /**
     * Gets the custom item sync service lazily from GearManager.
     *
     * @return The sync service, or null if GearManager not initialized
     */
    @Nullable
    private CustomItemSyncService getCustomItemSyncService() {
        io.github.larsonix.trailoforbis.gear.GearManager gearManager = plugin.getGearManager();
        return gearManager != null ? gearManager.getCustomItemSyncService() : null;
    }

    @Override
    @Nonnull
    public Query<EntityStore> getQuery() {
        // Match ALL entity deaths - we filter manually in onComponentAdded
        return Archetype.empty();
    }

    @Override
    public void onComponentAdded(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull DeathComponent deathComponent,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        // Check if RealmsManager is initialized (lazy initialization)
        RealmsConfig config = getConfig();
        if (config == null) {
            LOGGER.atFine().log("RealmsManager not initialized - skipping map drop");
            return;
        }

        // Skip player deaths
        Player playerComponent = store.getComponent(ref, Player.getComponentType());
        if (playerComponent != null) {
            return;
        }

        // Get the attacker (must be a player for map drops)
        KillerInfo killerInfo = extractKillerInfo(deathComponent, store);
        if (killerInfo == null) {
            LOGGER.atFine().log("No player killer found - skipping map drop");
            return;
        }

        // Get mob info
        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
        if (npc == null) {
            LOGGER.atFine().log("Dead entity is not an NPC - skipping map drop");
            return;
        }

        // Determine mob type and level
        MobType mobType = determineMobType(npc, store, ref);
        int mobLevel = getMobLevel(store, ref);

        // Detect if kill happened in a realm instance vs overworld
        boolean inRealm = isInRealmWorld(store);

        // Calculate drop chance (nerfed in overworld)
        double dropChance = calculateDropChance(config, mobLevel, mobType);
        if (!inRealm) {
            dropChance *= config.getOverworldMapDropMultiplier();
        }
        double roll = ThreadLocalRandom.current().nextDouble();

        if (roll > dropChance) {
            LOGGER.atFine().log("Map drop roll failed: %.4f > %.4f (level %d, type %s, realm=%s)",
                    roll, dropChance, mobLevel, mobType, inRealm);
            return;
        }

        // Drop succeeded! Blend mob level toward player level for map generation
        int mapLevel = mobLevel;
        io.github.larsonix.trailoforbis.gear.GearManager gearManager = plugin.getGearManager();
        if (gearManager != null) {
            DropLevelBlender blender = gearManager.getDropLevelBlender();
            if (blender.getConfig().enabled()) {
                int playerLevel = getPlayerLevel(killerInfo.playerId());
                mapLevel = blender.calculate(mobLevel, playerLevel);
            }
        }

        LOGGER.atInfo().log("Map drop triggered for %s kill (mobLevel %d, mapLevel %d, type %s, chance %.2f%%)",
                getRoleName(npc), mobLevel, mapLevel, mobType, dropChance * 100);

        // Get death position
        Vector3d deathPosition = getEntityPosition(store, ref);

        // Generate and spawn the map
        ItemStack mapItem = generateMapItem(config, mapLevel, killerInfo.playerRef());
        if (mapItem == null) {
            LOGGER.atWarning().log("Failed to generate map item");
            return;
        }

        // Spawn in world
        boolean spawned = spawnMapDrop(store, commandBuffer, mapItem, deathPosition);
        if (spawned) {
            LOGGER.atInfo().log("Spawned realm map drop at (%.1f, %.1f, %.1f)",
                    deathPosition.x, deathPosition.y, deathPosition.z);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // DROP CHANCE CALCULATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Calculates the drop chance for a realm map.
     *
     * <p>Formula: (baseChance + (level * perLevelChance)) * mobTypeMultiplier
     *
     * @param config The realms configuration
     * @param mobLevel The mob's level
     * @param mobType The mob type (normal/elite/boss)
     * @return Drop chance as a decimal (0.0 to 1.0)
     */
    public double calculateDropChance(@Nonnull RealmsConfig config, int mobLevel, @Nonnull MobType mobType) {
        double baseChance = config.getBaseMapDropChance();
        double perLevel = config.getMapDropChancePerLevel();
        double typeMultiplier = getTypeMultiplier(config, mobType);

        double chance = (baseChance + (mobLevel * perLevel)) * typeMultiplier;

        // Clamp to [0, 1]
        return Math.min(1.0, Math.max(0.0, chance));
    }

    /**
     * Gets the drop multiplier for a mob type.
     */
    private double getTypeMultiplier(@Nonnull RealmsConfig config, @Nonnull MobType mobType) {
        return switch (mobType) {
            case BOSS -> config.getBossMapDropMultiplier();
            case ELITE -> config.getEliteMapDropMultiplier();
            case NORMAL -> 1.0;
        };
    }

    // ═══════════════════════════════════════════════════════════════════
    // MAP GENERATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Generates a new realm map item with custom item registration.
     *
     * <p>This method:
     * <ol>
     *   <li>Generates map data via RealmMapGenerator</li>
     *   <li>Generates a unique instance ID</li>
     *   <li>Registers the custom item with ItemRegistryService</li>
     *   <li>Syncs the item definition to the killer player</li>
     *   <li>Creates an ItemStack with the custom item ID</li>
     * </ol>
     *
     * @param config The realms configuration
     * @param mobLevel The mob level (used for map level)
     * @param killerPlayerRef The player who killed the mob (for sync)
     * @return The generated ItemStack, or null on failure
     */
    @Nullable
    private ItemStack generateMapItem(@Nonnull RealmsConfig config, int mobLevel, @Nullable PlayerRef killerPlayerRef) {
        try {
            RealmMapGenerator mapGenerator = getMapGenerator();
            if (mapGenerator == null) {
                LOGGER.atWarning().log("MapGenerator not available");
                return null;
            }

            // Generate map data
            RealmMapData mapData = mapGenerator.generate(mobLevel);

            // Generate unique instance ID
            CustomItemInstanceId instanceId = CustomItemInstanceId.Generator.generateMap();

            // Update map data with instance ID
            mapData = mapData.withInstanceId(instanceId);

            // Get base item ID and custom item ID
            String baseItemId = mapData.getBaseItemId();
            String customItemId = instanceId.toItemId();

            // Register the custom item with ItemRegistryService (via GearManager)
            io.github.larsonix.trailoforbis.gear.GearManager gearManager = plugin.getGearManager();
            ItemRegistryService registryService = gearManager != null ? gearManager.getItemRegistryService() : null;
            if (registryService != null && registryService.isInitialized()) {
                com.hypixel.hytale.server.core.asset.type.item.config.Item baseItem =
                    com.hypixel.hytale.server.core.asset.type.item.config.Item.getAssetMap().getAsset(baseItemId);

                if (baseItem != null && baseItem != com.hypixel.hytale.server.core.asset.type.item.config.Item.UNKNOWN) {
                    // Register with Secondary interaction for right-click behavior
                    // The "RPG_RealmMap_Secondary" RootInteraction opens the realm
                    registryService.createAndRegisterWithSecondarySync(
                        baseItem, customItemId, "RPG_RealmMap_Secondary");
                    LOGGER.atFine().log("Registered custom map item: %s (base: %s, with Secondary interaction)",
                        customItemId, baseItemId);
                } else {
                    LOGGER.atWarning().log("Base item not found: %s - map will show as Invalid Item", baseItemId);
                }
            } else {
                LOGGER.atWarning().log("ItemRegistryService not available - map will show as Invalid Item");
            }

            // Sync item definition to killer player BEFORE spawning the item on the ground.
            // This ensures the player's client knows about the custom item ID and can
            // display it correctly instead of showing "?" (unknown item).
            CustomItemSyncService syncService = getCustomItemSyncService();
            if (syncService != null && killerPlayerRef != null) {
                try {
                    syncService.syncItem(killerPlayerRef, mapData);
                    LOGGER.atInfo().log("Synced map %s definition to killer %s before spawn",
                            customItemId, killerPlayerRef.getUuid());
                } catch (Exception e) {
                    LOGGER.atWarning().withCause(e).log(
                            "Failed to sync map %s to killer - item may appear as '?'", customItemId);
                }
            } else {
                LOGGER.atWarning().log("Map sync SKIPPED: syncService=%s, playerRef=%s",
                        syncService != null ? "OK" : "NULL",
                        killerPlayerRef != null ? "OK" : "NULL");
            }

            // Create ItemStack with the CUSTOM item ID (not the base item)
            ItemStack itemStack = new ItemStack(customItemId, 1);

            // Write map data to item metadata
            ItemStack mapItem = RealmMapUtils.writeMapData(itemStack, mapData);

            LOGGER.atFine().log("Generated map: level=%d, rarity=%s, biome=%s, size=%s, customId=%s",
                    mapData.level(), mapData.rarity(), mapData.biome(), mapData.size(), customItemId);

            return mapItem;
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to generate map item");
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // KILLER EXTRACTION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Information about who killed the mob.
     */
    private record KillerInfo(java.util.UUID playerId, PlayerRef playerRef) {}

    /**
     * Extracts killer information from the death component.
     *
     * @param deathComponent The death component
     * @param store The entity store
     * @return Killer info if the killer was a player, null otherwise
     */
    @Nullable
    private KillerInfo extractKillerInfo(
            @Nonnull DeathComponent deathComponent,
            @Nonnull Store<EntityStore> store) {

        Damage deathInfo = deathComponent.getDeathInfo();
        if (deathInfo == null) {
            return null;
        }

        Damage.Source source = deathInfo.getSource();

        // Projectile damage (ranged) - shooter is the real attacker
        if (source instanceof Damage.ProjectileSource projectileSource) {
            return extractPlayerFromRef(projectileSource.getRef(), store);
        }

        // Direct entity damage (melee)
        if (source instanceof Damage.EntitySource entitySource) {
            return extractPlayerFromRef(entitySource.getRef(), store);
        }

        return null;
    }

    /**
     * Extracts player info from an entity reference.
     */
    @Nullable
    private KillerInfo extractPlayerFromRef(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store) {

        if (!ref.isValid()) {
            return null;
        }

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return null;
        }

        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return null;
        }

        return new KillerInfo(playerRef.getUuid(), playerRef);
    }

    // ═══════════════════════════════════════════════════════════════════
    // WORLD DETECTION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Checks if the entity store belongs to a realm instance world.
     *
     * @param store The entity store from the death event
     * @return true if the death happened in a realm instance
     */
    private boolean isInRealmWorld(@Nonnull Store<EntityStore> store) {
        if (!RealmsManager.isInitialized()) {
            return false;
        }
        try {
            World world = store.getExternalData().getWorld();
            return RealmsManager.get().getRealmByWorld(world).isPresent();
        } catch (Exception e) {
            LOGGER.atFine().log("Could not determine realm world status: %s", e.getMessage());
            return false;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // MOB INFO
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Determines the mob type (normal/elite/boss).
     *
     * @param npc The NPC entity
     * @param store The entity store
     * @param ref The entity reference
     * @return The mob type
     */
    MobType determineMobType(
            @Nonnull NPCEntity npc,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref) {

        // Check MobScalingComponent for classification
        MobScalingComponent scaling = store.getComponent(ref, MobScalingComponent.getComponentType());
        if (scaling != null) {
            return switch (scaling.getClassification()) {
                case BOSS -> MobType.BOSS;
                case ELITE -> MobType.ELITE;
                default -> MobType.NORMAL;
            };
        }

        // Fallback: check role name for hints
        String roleName = getRoleName(npc).toLowerCase();

        if (roleName.contains("boss")) {
            return MobType.BOSS;
        }

        if (roleName.contains("elite") || roleName.contains("champion")) {
            return MobType.ELITE;
        }

        return MobType.NORMAL;
    }

    /**
     * Gets the mob's level from MobScalingComponent or defaults to 1.
     */
    private int getMobLevel(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        MobScalingComponent scaling = store.getComponent(ref, MobScalingComponent.getComponentType());
        return scaling != null ? scaling.getMobLevel() : 1;
    }

    /**
     * Gets the NPC's role name.
     */
    private String getRoleName(@Nonnull NPCEntity npc) {
        Role role = npc.getRole();
        return role != null ? role.getRoleName() : "unknown";
    }

    // ═══════════════════════════════════════════════════════════════════
    // ITEM SPAWNING
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets the position of an entity from TransformComponent.
     */
    private Vector3d getEntityPosition(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform != null) {
            return transform.getPosition();
        }
        return new Vector3d(0, 64, 0);
    }

    /**
     * Spawns a map drop in the world.
     *
     * @param store The entity store
     * @param commandBuffer The command buffer for adding entities
     * @param mapItem The map item to spawn
     * @param position Where to spawn the item
     * @return true if spawned successfully
     */
    private boolean spawnMapDrop(
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull ItemStack mapItem,
            @Nonnull Vector3d position) {

        Vector3f rotation = new Vector3f(0, 0, 0);

        // Spawn with slight random horizontal velocity and upward bounce
        float vx = (float) (ThreadLocalRandom.current().nextDouble() * 2 - 1) * 2f;
        float vy = 3.25f;  // Standard upward bounce
        float vz = (float) (ThreadLocalRandom.current().nextDouble() * 2 - 1) * 2f;

        // Create item entity holder
        Holder<EntityStore> holder = ItemComponent.generateItemDrop(
                store, mapItem, position, rotation, vx, vy, vz);

        if (holder == null) {
            LOGGER.atWarning().log("Failed to create item drop holder for map");
            return false;
        }

        // Add to world
        @SuppressWarnings("unchecked")
        Holder<EntityStore>[] holderArray = (Holder<EntityStore>[]) new Holder<?>[]{holder};
        commandBuffer.addEntities(holderArray, AddReason.SPAWN);

        return true;
    }

    // ═══════════════════════════════════════════════════════════════════
    // PLAYER LEVEL
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets the player's level from the leveling service.
     *
     * @param playerId The player's UUID
     * @return The player's level, or 1 if service unavailable
     */
    private int getPlayerLevel(@Nonnull java.util.UUID playerId) {
        java.util.Optional<LevelingService> serviceOpt = ServiceRegistry.get(LevelingService.class);
        if (serviceOpt.isPresent()) {
            return serviceOpt.get().getLevel(playerId);
        }
        return 1;
    }

    // ═══════════════════════════════════════════════════════════════════
    // ACCESSORS (for testing)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets the plugin instance.
     *
     * @return The plugin
     */
    public TrailOfOrbis getPlugin() {
        return plugin;
    }
}
