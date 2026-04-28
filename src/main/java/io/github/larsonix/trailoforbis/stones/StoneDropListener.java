package io.github.larsonix.trailoforbis.stones;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
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
import io.github.larsonix.trailoforbis.gear.GearManager;
import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig;
import io.github.larsonix.trailoforbis.gear.generation.RarityRoller;
import io.github.larsonix.trailoforbis.gear.loot.RarityBonusCalculator;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.maps.RealmsManager;
import io.github.larsonix.trailoforbis.maps.config.RealmsConfig;
import io.github.larsonix.trailoforbis.mobs.component.MobScalingComponent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * ECS system that handles stone drops from mob deaths.
 *
 * <p>Extends {@link DeathSystems.OnDeathSystem} to react when any entity dies.
 * Uses the unified {@link GearRarity} system via {@link RarityRoller} for
 * consistent drop weighting across gear, maps, and stones.
 *
 * <h2>Drop Flow</h2>
 * <ol>
 *   <li>Player kills a mob</li>
 *   <li>Roll drop chance: base + (level × perLevel) × mobTypeMultiplier</li>
 *   <li>If drop succeeds, use {@link RarityRoller} to select rarity</li>
 *   <li>Select random stone of that rarity via {@link StoneType#getByRarity}</li>
 *   <li>Create stone item via {@link StoneUtils}</li>
 *   <li>Spawn drop at death position</li>
 * </ol>
 *
 * <h2>Rarity Integration</h2>
 * <p>By using {@link RarityRoller}, stone drops benefit from:
 * <ul>
 *   <li>Config-driven rarity weights (gear-balance.yml)</li>
 *   <li>Player Item Rarity (IIR) bonuses</li>
 *   <li>Consistent distribution with gear drops</li>
 * </ul>
 *
 * @see StoneType
 * @see StoneUtils
 * @see RarityRoller
 */
public class StoneDropListener extends DeathSystems.OnDeathSystem {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // Plugin reference for lazy access to managers (production path)
    @Nullable
    private final TrailOfOrbis plugin;

    // Direct config for test path (when plugin is null)
    @Nullable
    private final RealmsConfig directConfig;

    // Cached rarity roller (created lazily in production, eagerly in tests)
    @Nullable
    private volatile RarityRoller cachedRarityRoller;

    // Cached rarity bonus calculator (resolved lazily from GearManager)
    @Nullable
    private volatile RarityBonusCalculator cachedRarityBonusCalculator;

    private final Random random;

    /**
     * Mob type classification for drop calculations.
     * Uses same classification as map drops for consistency.
     */
    public enum MobType {
        NORMAL(1.0),
        ELITE(3.0),
        BOSS(10.0);

        private final double multiplier;

        MobType(double multiplier) {
            this.multiplier = multiplier;
        }

        public double getMultiplier() {
            return multiplier;
        }
    }

    /**
     * Creates a new stone drop listener with lazy initialization.
     *
     * <p>Config and services are obtained lazily from RealmsManager and
     * GearManager via the plugin reference. This allows the system to be
     * registered in setup() before managers are initialized in start().
     *
     * @param plugin The plugin instance for lazy access to managers
     */
    public StoneDropListener(@Nonnull TrailOfOrbis plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.directConfig = null;
        this.cachedRarityRoller = null;
        this.random = new Random();
    }

    /**
     * Creates a stone drop listener with direct config injection (for tests).
     *
     * <p>Bypasses lazy initialization and uses provided configs directly.
     * This preserves existing test behavior without requiring a mock plugin.
     *
     * @param config The realms config containing stone drop rates
     * @param gearConfig The gear balance config for rarity weights
     */
    StoneDropListener(
            @Nonnull RealmsConfig config,
            @Nonnull GearBalanceConfig gearConfig) {
        this.plugin = null;
        this.directConfig = Objects.requireNonNull(config, "config cannot be null");
        this.cachedRarityRoller = new RarityRoller(
                Objects.requireNonNull(gearConfig, "gearConfig cannot be null"));
        this.random = new Random();
    }

    @Override
    @Nonnull
    public com.hypixel.hytale.component.query.Query<EntityStore> getQuery() {
        // Match ALL entity deaths - we filter manually in onComponentAdded
        return Archetype.empty();
    }

    @Override
    public void onComponentAdded(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull DeathComponent deathComponent,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        // Check if config is available (lazy initialization)
        RealmsConfig config = resolveConfig();
        if (config == null) {
            return; // Realms system not yet initialized
        }

        RarityRoller rarityRoller = resolveRarityRoller();
        if (rarityRoller == null) {
            return; // Gear system not yet initialized
        }

        // Skip player deaths
        Player playerComponent = store.getComponent(ref, Player.getComponentType());
        if (playerComponent != null) {
            return;
        }

        // Get the attacker (must be a player for stone drops)
        KillerInfo killerInfo = extractKillerInfo(deathComponent, store);
        if (killerInfo == null) {
            LOGGER.atFine().log("No player killer found - skipping stone drop");
            return;
        }

        // Get mob info
        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
        if (npc == null) {
            LOGGER.atFine().log("Dead entity is not an NPC - skipping stone drop");
            return;
        }

        // Determine mob type and level
        MobType mobType = determineMobType(npc, store, ref);
        int mobLevel = getMobLevel(store, ref);

        // Calculate drop chance
        double dropChance = calculateDropChance(config, mobLevel, mobType);
        double roll = ThreadLocalRandom.current().nextDouble();

        if (roll > dropChance) {
            LOGGER.atFine().log("Stone drop roll failed: %.4f > %.4f (level %d, type %s)",
                    roll, dropChance, mobLevel, mobType);
            return;
        }

        // Drop succeeded! Generate a stone
        LOGGER.atInfo().log("Stone drop triggered for %s kill (level %d, type %s, chance %.2f%%)",
                getRoleName(npc), mobLevel, mobType, dropChance * 100);

        // Get player's IIR bonus (Item Rarity)
        double rarityBonus = getPlayerRarityBonus(killerInfo);

        // Roll rarity using unified system
        GearRarity stoneRarity = rollStoneRarity(rarityRoller, rarityBonus);

        // Select a stone of that rarity
        StoneType stoneType = selectStoneOfRarity(stoneRarity);
        if (stoneType == null) {
            LOGGER.atWarning().log("No stones available for rarity %s, falling back", stoneRarity);
            stoneType = StoneType.LOREKEEPERS_SCROLL; // Fallback to common
        }

        // Get death position
        Vector3d deathPosition = getEntityPosition(store, ref);

        // Create stone using native item ID
        ItemStack stoneItem = StoneUtils.createStoneItem(stoneType);
        boolean spawned = spawnStoneDrop(store, commandBuffer, stoneItem, deathPosition);

        if (spawned) {
            LOGGER.atInfo().log("Spawned %s (%s) at (%.1f, %.1f, %.1f)",
                    stoneType.getDisplayName(), stoneRarity,
                    deathPosition.x, deathPosition.y, deathPosition.z);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // DROP CHANCE CALCULATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Calculates the drop chance for a stone.
     *
     * <p>Formula: (baseChance + (level × perLevelChance)) × mobTypeMultiplier
     *
     * @param config The realms config containing stone drop rates
     * @param mobLevel The mob's level
     * @param mobType The mob type (normal/elite/boss)
     * @return Drop chance as a decimal (0.0 to 1.0)
     */
    public double calculateDropChance(@Nonnull RealmsConfig config, int mobLevel, @Nonnull MobType mobType) {
        double baseChance = config.getBaseStoneDropChance();
        double perLevel = config.getStoneDropChancePerLevel();
        double typeMultiplier = getTypeMultiplier(config, mobType);

        double chance = (baseChance + (mobLevel * perLevel)) * typeMultiplier;

        // Clamp to [0, 1]
        return Math.min(1.0, Math.max(0.0, chance));
    }

    /**
     * Gets the drop multiplier for a mob type from config.
     */
    private double getTypeMultiplier(@Nonnull RealmsConfig config, @Nonnull MobType mobType) {
        return switch (mobType) {
            case BOSS -> config.getBossStoneDropMultiplier();
            case ELITE -> config.getEliteStoneDropMultiplier();
            case NORMAL -> 1.0;
        };
    }

    // ═══════════════════════════════════════════════════════════════════
    // RARITY SELECTION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Rolls a rarity for the stone using the unified rarity system.
     *
     * <p>Uses {@link RarityRoller} which supports IIR bonuses and
     * config-driven weights. All rarities (COMMON through MYTHIC) are
     * now supported for stones.
     *
     * @param rarityRoller The rarity roller to use
     * @param rarityBonus Player's Item Rarity bonus (0.0 = none, 1.0 = +100%)
     * @return The rolled rarity
     */
    GearRarity rollStoneRarity(@Nonnull RarityRoller rarityRoller, double rarityBonus) {
        // Roll using the unified system
        GearRarity rolled = rarityRoller.roll(rarityBonus);

        // If the rarity has no stones, try adjacent rarities
        if (StoneType.getByRarity(rolled).isEmpty()) {
            // Try one tier lower
            return rolled.getPreviousTier().orElse(GearRarity.COMMON);
        }

        return rolled;
    }

    /**
     * Selects a random stone of the given rarity.
     *
     * @param rarity The target rarity
     * @return A random stone of that rarity, or null if none available
     */
    @Nullable
    StoneType selectStoneOfRarity(@Nonnull GearRarity rarity) {
        List<StoneType> stonesOfRarity = StoneType.getByRarity(rarity);

        if (stonesOfRarity.isEmpty()) {
            return null;
        }

        return stonesOfRarity.get(random.nextInt(stonesOfRarity.size()));
    }

    // ═══════════════════════════════════════════════════════════════════
    // PLAYER BONUS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets the player's Item Rarity (IIR) bonus from their WIND attribute.
     *
     * @param killerInfo The killer information
     * @return IIR bonus as percentage (e.g., 2.5 means +2.5%)
     */
    private double getPlayerRarityBonus(@Nonnull KillerInfo killerInfo) {
        RarityBonusCalculator calculator = resolveRarityBonusCalculator();
        if (calculator == null) return 0.0;
        return calculator.calculatePlayerBonus(killerInfo.playerId());
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
     * Spawns a stone drop in the world.
     *
     * @param store The entity store
     * @param commandBuffer The command buffer for adding entities
     * @param stoneItem The stone item to spawn
     * @param position Where to spawn the item
     * @return true if spawned successfully
     */
    private boolean spawnStoneDrop(
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull ItemStack stoneItem,
            @Nonnull Vector3d position) {

        Vector3f rotation = new Vector3f(0, 0, 0);

        // Spawn with slight random horizontal velocity and upward bounce
        float vx = (float) (ThreadLocalRandom.current().nextDouble() * 2 - 1) * 2f;
        float vy = 3.25f;  // Standard upward bounce
        float vz = (float) (ThreadLocalRandom.current().nextDouble() * 2 - 1) * 2f;

        // Create item entity holder
        Holder<EntityStore> holder = ItemComponent.generateItemDrop(
                store, stoneItem, position, rotation, vx, vy, vz);

        if (holder == null) {
            LOGGER.atWarning().log("Failed to create item drop holder for stone");
            return false;
        }

        // Add to world
        @SuppressWarnings("unchecked")
        Holder<EntityStore>[] holderArray = (Holder<EntityStore>[]) new Holder<?>[]{holder};
        commandBuffer.addEntities(holderArray, AddReason.SPAWN);

        return true;
    }

    // ═══════════════════════════════════════════════════════════════════
    // LAZY RESOLUTION (production path via plugin, test path via direct fields)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Resolves the realms configuration.
     *
     * <p>In production, fetches lazily from RealmsManager via plugin.
     * In tests, returns the directly-injected config.
     *
     * @return The configuration, or null if not yet available
     */
    @Nullable
    public RealmsConfig resolveConfig() {
        if (directConfig != null) {
            return directConfig;
        }
        if (plugin != null) {
            RealmsManager rm = plugin.getRealmsManager();
            return rm != null ? rm.getConfig() : null;
        }
        return null;
    }

    /**
     * Resolves the rarity roller, creating it lazily if needed.
     *
     * @return The rarity roller, or null if gear system not yet available
     */
    @Nullable
    public RarityRoller resolveRarityRoller() {
        if (cachedRarityRoller != null) {
            return cachedRarityRoller;
        }
        if (plugin != null) {
            GearManager gm = plugin.getGearManager();
            if (gm != null) {
                cachedRarityRoller = new RarityRoller(gm.getBalanceConfig());
                return cachedRarityRoller;
            }
        }
        return null;
    }

    /**
     * Resolves the rarity bonus calculator, fetching lazily from GearManager.
     *
     * @return The calculator, or null if gear system not yet available
     */
    @Nullable
    public RarityBonusCalculator resolveRarityBonusCalculator() {
        if (cachedRarityBonusCalculator != null) {
            return cachedRarityBonusCalculator;
        }
        if (plugin != null) {
            GearManager gm = plugin.getGearManager();
            if (gm != null) {
                cachedRarityBonusCalculator = gm.getRarityBonusCalculator();
                return cachedRarityBonusCalculator;
            }
        }
        return null;
    }

    /**
     * Returns whether this listener is fully operational (all dependencies available).
     *
     * @return true if config and rarity roller are both available
     */
    public boolean isOperational() {
        return resolveConfig() != null && resolveRarityRoller() != null;
    }
}
