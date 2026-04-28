package io.github.larsonix.trailoforbis.gear.loot;

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
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;

import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.api.ServiceRegistry;
import io.github.larsonix.trailoforbis.gear.GearManager;
import io.github.larsonix.trailoforbis.gear.GearService;
import io.github.larsonix.trailoforbis.gear.item.ItemWorldSyncService;
import io.github.larsonix.trailoforbis.gear.loot.LootCalculator.LootRoll;
import io.github.larsonix.trailoforbis.gear.loot.LootSettings.MobType;
import io.github.larsonix.trailoforbis.leveling.api.LevelingService;
import io.github.larsonix.trailoforbis.mobs.component.MobScalingComponent;
import io.github.larsonix.trailoforbis.maps.RealmsManager;
import io.github.larsonix.trailoforbis.maps.components.RealmMobComponent;
import io.github.larsonix.trailoforbis.maps.config.RealmsConfig;
import io.github.larsonix.trailoforbis.maps.instance.RealmInstance;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * ECS system that generates RPG gear loot drops on mob death.
 *
 * <p>Extends {@link DeathSystems.OnDeathSystem} to react when any entity dies.
 * Filters to only process mob deaths caused by players.
 *
 * <p>Loot generation considers:
 * <ul>
 *   <li>Base drop chance from config</li>
 *   <li>Player WIND attribute (Ghost archetype = fortune/loot bonus)</li>
 *   <li>Distance from world spawn</li>
 *   <li>Mob type (normal/elite/boss)</li>
 * </ul>
 *
 * <p>This system runs AFTER vanilla loot drops to avoid interference.
 */
public class LootListener extends DeathSystems.OnDeathSystem {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final TrailOfOrbis plugin;
    private final LootCalculator calculator;
    private final LootGenerator generator;

    /**
     * Creates a new loot listener system.
     *
     * @param plugin The plugin instance
     * @param calculator The loot calculator
     * @param generator The loot generator
     */
    public LootListener(
            @Nonnull TrailOfOrbis plugin,
            @Nonnull LootCalculator calculator,
            @Nonnull LootGenerator generator) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.calculator = Objects.requireNonNull(calculator, "calculator cannot be null");
        this.generator = Objects.requireNonNull(generator, "generator cannot be null");
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

        // Skip player deaths
        Player playerComponent = store.getComponent(ref, Player.getComponentType());
        if (playerComponent != null) {
            return;
        }

        // Get the attacker (must be a player for loot drops)
        KillerInfo killerInfo = extractKillerInfo(deathComponent, store);
        if (killerInfo == null) {
            LOGGER.atFine().log("No player killer found - skipping loot");
            return;
        }

        // Get mob info
        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
        if (npc == null) {
            LOGGER.atFine().log("Dead entity is not an NPC - skipping loot");
            return;
        }

        // Determine mob type (normal/elite/boss)
        MobType mobType = determineMobType(npc, store, ref);

        // Get mob level (prefers realm level for realm mobs)
        int mobLevel = getMobLevel(store, ref);

        // Get death position
        Vector3d deathPosition = getEntityPosition(store, ref);

        // Get player level for drop level blending
        int playerLevel = getPlayerLevel(killerInfo.playerId());

        // Extract realm context for loot bonuses
        RealmLootContext realmContext = extractRealmContext(store, ref, killerInfo.playerId());

        // Calculate loot with realm context
        LootRoll lootRoll = calculator.calculateLoot(
                killerInfo.playerId(),
                mobType,
                mobLevel,
                playerLevel,
                deathPosition,
                realmContext
        );

        if (!lootRoll.shouldDrop()) {
            LOGGER.atFine().log("No loot drop for %s death", getRoleName(npc));
            return;
        }

        // Generate drops
        List<ItemStack> drops = generator.generateDrops(lootRoll);

        if (drops.isEmpty()) {
            LOGGER.atFine().log("Generated 0 drops (generation failed)");
            return;
        }

        // Sync item definitions to ALL nearby players BEFORE spawning
        // This ensures any client picking up the item has the definition when notifyPickupItem() fires
        syncItemsToNearbyPlayers(store, deathPosition, drops);

        // Spawn items in world
        int spawnedCount = spawnDrops(store, commandBuffer, drops, deathPosition);

        if (spawnedCount > 0) {
            LOGGER.atInfo().log("Spawned %d gear drop(s) for %s kill by %s (level %d, rarity bonus %.1f%%)",
                    spawnedCount, getRoleName(npc), killerInfo.playerId(),
                    lootRoll.itemLevel(), lootRoll.rarityBonus());
        } else {
            LOGGER.atWarning().log("Failed to spawn any gear drops for %s kill (generated %d items)",
                    getRoleName(npc), drops.size());
        }
    }

    // =========================================================================
    // KILLER EXTRACTION
    // =========================================================================

    /**
     * Information about who killed the mob.
     */
    private record KillerInfo(UUID playerId, PlayerRef playerRef) {}

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
        // Check ProjectileSource FIRST since it extends EntitySource
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

        // Check if the entity has a Player component
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return null;
        }

        // Get PlayerRef component for UUID
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return null;
        }

        return new KillerInfo(playerRef.getUuid(), playerRef);
    }

    // =========================================================================
    // MOB INFO
    // =========================================================================

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

        // First check MobScalingComponent for classification
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
     * Gets the mob's level, preferring realm level for realm mobs.
     *
     * <p>For realm mobs, the level is fetched from the realm's map data to ensure
     * loot is generated at the correct item level. For non-realm mobs, falls back
     * to MobScalingComponent.
     *
     * @param store The entity store
     * @param ref The entity reference
     * @return The mob's level for loot calculation
     */
    private int getMobLevel(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        // Check for realm mob first - use realm level for consistent loot
        RealmMobComponent realmMob = store.getComponent(ref, RealmMobComponent.getComponentType());
        if (realmMob != null && realmMob.getRealmId() != null) {
            // Use the component's realm level (reduced for minions) instead of live realm level
            return realmMob.getRealmLevel();
        }

        // Fallback to MobScalingComponent
        MobScalingComponent scaling = store.getComponent(ref, MobScalingComponent.getComponentType());
        return scaling != null ? scaling.getMobLevel() : 1;
    }

    /**
     * Extracts realm loot context for a killed mob.
     *
     * <p>If the mob is a realm mob, extracts IIQ and IIR bonuses from the realm's
     * modifiers. Otherwise, returns {@link RealmLootContext#NONE}.
     *
     * @param store The entity store
     * @param ref The entity reference
     * @param playerId The player who killed the mob
     * @return Realm loot context with bonuses
     */
    @Nonnull
    private RealmLootContext extractRealmContext(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UUID playerId) {

        // Check for realm mob
        RealmMobComponent realmMob = store.getComponent(ref, RealmMobComponent.getComponentType());
        if (realmMob == null || realmMob.getRealmId() == null) {
            return RealmLootContext.NONE;
        }

        RealmsManager realmsManager = plugin.getRealmsManager();
        if (realmsManager == null) {
            return RealmLootContext.NONE;
        }

        Optional<RealmInstance> realmOpt = realmsManager.getRealm(realmMob.getRealmId());
        if (realmOpt.isEmpty()) {
            return RealmLootContext.NONE;
        }

        // Get IIQ and IIR from realm map data
        RealmInstance realm = realmOpt.get();
        double iiqBonus = realm.getMapData().getTotalItemQuantity();
        double iirBonus = realm.getMapData().getTotalItemRarity();

        // Add base realm IIQ bonus from config (applies to ALL realm mobs)
        RealmsConfig realmsConfig = realmsManager.getConfig();
        iiqBonus += realmsConfig.getBaseRealmIiqBonus();

        LOGGER.atFine().log("Realm loot context for player %s: IIQ=%.1f%%, IIR=%.1f%%",
            playerId.toString().substring(0, 8), iiqBonus, iirBonus);

        return RealmLootContext.of(iiqBonus, iirBonus);
    }

    /**
     * Gets the NPC's role name.
     */
    private String getRoleName(@Nonnull NPCEntity npc) {
        Role role = npc.getRole();
        return role != null ? role.getRoleName() : "unknown";
    }

    // =========================================================================
    // ITEM SYNC
    // =========================================================================

    /**
     * Syncs item definitions to ALL nearby players before items are spawned.
     *
     * <p>This is CRITICAL for the custom tooltip and pickup notification system
     * to work. All clients within range must have the custom item definition
     * BEFORE the item entity appears, otherwise:
     * <ul>
     *   <li>Pickup notification shows raw translation key instead of item name</li>
     *   <li>Rarity color is missing from notification</li>
     *   <li>Item appears as "?" in inventory</li>
     * </ul>
     *
     * <p>The fix is to sync to ALL nearby players, not just the killer, because
     * anyone might pick up the loot.
     *
     * @param store The entity store for player lookups
     * @param position The position where items will spawn
     * @param drops The items to sync
     */
    private void syncItemsToNearbyPlayers(
            @Nonnull Store<EntityStore> store,
            @Nonnull Vector3d position,
            @Nonnull List<ItemStack> drops) {

        // Get ItemWorldSyncService from GearManager
        Optional<GearService> gearServiceOpt = ServiceRegistry.get(GearService.class);
        if (gearServiceOpt.isEmpty()) {
            LOGGER.atFine().log("GearService not available, skipping item sync");
            return;
        }

        GearService gearService = gearServiceOpt.get();
        if (!(gearService instanceof GearManager gearManager)) {
            LOGGER.atFine().log("GearService is not GearManager, skipping item sync");
            return;
        }

        ItemWorldSyncService worldSyncService = gearManager.getItemWorldSyncService();
        if (worldSyncService == null) {
            LOGGER.atFine().log("ItemWorldSyncService not available, skipping item sync");
            return;
        }

        // Sync all drops to all nearby players
        int synced = worldSyncService.syncItemsToNearbyPlayers(store, position, drops);

        if (synced > 0) {
            LOGGER.atFine().log("Pre-synced %d item(s) to nearby players at (%.0f, %.0f, %.0f)",
                synced, position.x, position.y, position.z);
        }
    }

    // =========================================================================
    // ITEM SPAWNING
    // =========================================================================

    /**
     * Gets the position of an entity from TransformComponent.
     */
    private Vector3d getEntityPosition(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform != null) {
            return transform.getPosition();
        }
        // Fallback to origin
        return new Vector3d(0, 64, 0);
    }

    /**
     * Spawns dropped items in the world at the specified position.
     *
     * <p>Creates item entity holders via {@link ItemComponent#generateItemDrop} and
     * adds them to the world via the command buffer.
     *
     * @param store The entity store (used as ComponentAccessor)
     * @param commandBuffer The command buffer for adding entities
     * @param drops The items to spawn
     * @param position Where to spawn items
     * @return The number of items successfully spawned
     */
    private int spawnDrops(
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull List<ItemStack> drops,
            @Nonnull Vector3d position) {

        if (drops.isEmpty()) {
            return 0;
        }

        List<Holder<EntityStore>> holders = new ArrayList<>();
        Vector3f rotation = new Vector3f(0, 0, 0);

        for (ItemStack item : drops) {
            // Spawn with slight random horizontal velocity and upward bounce
            float vx = (float) (ThreadLocalRandom.current().nextDouble() * 2 - 1) * 2f;
            float vy = 3.25f;  // Standard upward bounce
            float vz = (float) (ThreadLocalRandom.current().nextDouble() * 2 - 1) * 2f;

            // Create item entity holder
            Holder<EntityStore> holder = ItemComponent.generateItemDrop(
                    store, item, position, rotation, vx, vy, vz);

            if (holder != null) {
                holders.add(holder);
            } else {
                LOGGER.atWarning().log("Failed to create item drop holder for: %s", item);
            }
        }

        // Add all created items to the world
        if (!holders.isEmpty()) {
            for (Holder<EntityStore> holder : holders) {
                commandBuffer.addEntity(holder, AddReason.SPAWN);
            }

            LOGGER.atFine().log("Added %d item drops to world at (%.1f, %.1f, %.1f)",
                    holders.size(), position.x, position.y, position.z);
        }

        return holders.size();
    }

    // =========================================================================
    // PLAYER LEVEL
    // =========================================================================

    /**
     * Gets the player's level from the leveling service.
     *
     * @param playerId The player's UUID
     * @return The player's level, or 1 if service unavailable
     */
    private int getPlayerLevel(@Nonnull UUID playerId) {
        Optional<LevelingService> serviceOpt = ServiceRegistry.get(LevelingService.class);
        if (serviceOpt.isPresent()) {
            return serviceOpt.get().getLevel(playerId);
        }
        return 1;
    }

    // =========================================================================
    // ACCESSORS (for testing)
    // =========================================================================

    /**
     * Gets the loot calculator.
     *
     * @return The loot calculator
     */
    public LootCalculator getCalculator() {
        return calculator;
    }

    /**
     * Gets the loot generator.
     *
     * @return The loot generator
     */
    public LootGenerator getGenerator() {
        return generator;
    }
}
