package io.github.larsonix.trailoforbis.combat.attackspeed;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.InteractionCooldown;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionChain;
import com.hypixel.hytale.server.core.entity.InteractionEntry;
import com.hypixel.hytale.server.core.entity.InteractionManager;
import com.hypixel.hytale.server.core.modules.interaction.InteractionModule;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.modules.interaction.system.InteractionSystems;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.combat.attackspeed.api.AttackSpeedApi;
import io.github.larsonix.trailoforbis.combat.attackspeed.combo.ComboTracker;
import io.github.larsonix.trailoforbis.combat.attackspeed.config.WeaponProfileRegistry;
import io.github.larsonix.trailoforbis.combat.attackspeed.config.WeaponSpeedProfile;
import io.github.larsonix.trailoforbis.gear.model.WeaponType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side attack speed system that makes attacks genuinely faster by
 * accelerating three synchronized layers:
 *
 * <ol>
 *   <li><b>Chain operation acceleration</b> — advances {@link InteractionEntry}
 *       timestamps each tick via {@link InteractionEntry#setTimestamp(long, float)},
 *       making damage windows, particles, and recovery complete proportionally faster.
 *       Uses the public {@link InteractionEntry#setTimestamp(long, float)} API.</li>
 *   <li><b>Selective cooldown acceleration</b> — calls {@link CooldownHandler.Cooldown#tick(float)}
 *       with extra delta time on the specific cooldown that gates the player's Primary
 *       attack, preventing the cooldown from becoming the bottleneck after chains
 *       complete faster.</li>
 *   <li><b>Visual animation sync</b> (external) — {@link AnimationSpeedSyncManager}
 *       sends per-player animation speed packets so the client-side swing animation
 *       matches the server-side operation speed.</li>
 * </ol>
 *
 * <p>Multipliers are resolved per-weapon-type via {@link AttackSpeedResolver} and
 * {@link WeaponProfileRegistry}. Different weapons respond differently to the same
 * stat investment (e.g., daggers benefit more from raw speed, battleaxes from cooldown).
 *
 * <p>Runs AFTER {@link InteractionSystems.TickInteractionManagerSystem} each tick.
 *
 * <p><b>Why not {@code setTimeShift()}?</b> Hytale's {@code doTickChain()} resets
 * {@code chain.timeShift} to 0 every tick after the first run, making it useless for
 * ongoing speed modification and causing 60+ desync packets/sec.
 *
 * <p><b>Formula (weapon-profile-aware):</b>
 * <pre>
 * snapshot = AttackSpeedResolver.resolve(attackSpeedPercent, weaponProfile, config)
 * chainExtra = min(dt * (snapshot.chainMultiplier - 1.0), MAX_EXTRA_PER_TICK)
 * cdExtra    = min(dt * (snapshot.cooldownMultiplier - 1.0), MAX_EXTRA_PER_TICK)
 * </pre>
 *
 * @see InteractionEntry#setTimestamp(long, float)
 * @see CooldownHandler.Cooldown#tick(float)
 * @see AnimationSpeedSyncManager
 */
public class InteractionTimeShiftSystem extends EntityTickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /**
     * Maximum extra time advancement per tick (seconds). Prevents runaway acceleration
     * during lag spikes where dt could be very large. At 60fps with 3x multiplier,
     * normal extraSeconds ≈ 0.033s — well under this cap.
     */
    private static final float MAX_EXTRA_PER_TICK = 0.1f;

    /** Absolute floor for any multiplier after override application (prevents frozen attacks). */
    private static final float ABSOLUTE_MIN_MULTIPLIER = 0.2f;
    /** Absolute ceiling for any multiplier after override application. */
    private static final float ABSOLUTE_MAX_MULTIPLIER = 5.0f;

    private final TrailOfOrbis plugin;
    private final AnimationSpeedSyncConfig syncConfig;
    private final WeaponProfileRegistry profileRegistry;
    private final ComboTracker comboTracker = new ComboTracker();
    private final AttackSpeedApi attackSpeedApi = new AttackSpeedApi();

    private final ComponentType<EntityStore, InteractionManager> interactionManagerType;
    private final ComponentType<EntityStore, PlayerRef> playerRefType;
    private final ComponentType<EntityStore, Player> playerType;

    @Nullable
    private Query<EntityStore> query;

    private final Set<Dependency<EntityStore>> dependencies;

    // ---- Cooldown acceleration state ----

    /**
     * Cached reflection field for {@code InteractionManager.cooldownHandler}.
     * The only reflection point in this system — all other APIs are public.
     */
    private static volatile Field cooldownHandlerField;
    private static volatile boolean reflectionInitialized;
    private static volatile boolean reflectionAvailable;

    /**
     * Per-player cache of the cooldown ID used by their Primary attack interaction.
     * Learned by observing active Primary chains; persists between attacks so we can
     * continue accelerating the cooldown during the inter-attack gap.
     */
    private final ConcurrentHashMap<UUID, String> primaryAttackCooldownIds = new ConcurrentHashMap<>();

    public InteractionTimeShiftSystem(
            @Nonnull TrailOfOrbis plugin,
            @Nonnull AnimationSpeedSyncConfig syncConfig,
            @Nonnull WeaponProfileRegistry profileRegistry
    ) {
        this.plugin = plugin;
        this.syncConfig = syncConfig;
        this.profileRegistry = profileRegistry;
        this.interactionManagerType = InteractionModule.get().getInteractionManagerComponent();
        this.playerRefType = PlayerRef.getComponentType();
        this.playerType = Player.getComponentType();
        this.dependencies = Set.of(
                new SystemDependency<>(Order.AFTER, InteractionSystems.TickInteractionManagerSystem.class)
        );
        initializeReflection();
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return dependencies;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        if (query == null) {
            query = Archetype.of(interactionManagerType, playerRefType, playerType);
        }
        return query;
    }

    @Override
    public void tick(
            float dt,
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        Ref<EntityStore> entityRef = archetypeChunk.getReferenceTo(index);
        if (entityRef == null || !entityRef.isValid()) {
            return;
        }

        PlayerRef playerRef = store.getComponent(entityRef, playerRefType);
        if (playerRef == null) {
            return;
        }
        UUID uuid = playerRef.getUuid();

        ComputedStats stats = plugin.getAttributeManager().getStats(uuid);
        if (stats == null) {
            return;
        }

        InteractionManager manager = store.getComponent(entityRef, interactionManagerType);
        if (manager == null) {
            return;
        }

        // Resolve weapon type and profile from held item
        Player player = archetypeChunk.getComponent(index, playerType);
        WeaponType weaponType = resolveHeldWeaponType(player);
        WeaponSpeedProfile profile = profileRegistry.getProfile(weaponType);

        // Detect active Primary chain for combo tracking
        int primaryChainId = findActivePrimaryChainId(manager);

        // Update combo tracker — detects new attacks, handles timeout/weapon-switch resets
        int comboStage = comboTracker.update(uuid, primaryChainId, weaponType, profile);

        // Compute per-phase multipliers from all 6 stats × weapon profile × combo stage
        AttackSpeedSnapshot snapshot = AttackSpeedResolver.resolve(
                stats.getAttackSpeedPercent(),
                stats.getCooldownRecoveryPercent(),
                stats.getComboSpeedBonus(),
                stats.getProjectileAttackSpeedPercent(),
                stats.getCastSpeedPercent(),
                comboStage,
                profile,
                syncConfig.animationSpeedScale(),
                syncConfig.animationMinSpeed(),
                syncConfig.animationMaxSpeed()
        );

        // Apply temporary override from API (Hexcode spell buffs, realm modifiers, etc.)
        // Override is multiplicative on top of stat-derived values, then re-clamped
        // to absolute bounds. Slow debuffs (override < 1.0) correctly produce negative
        // extra seconds, which shifts timestamps forward (less elapsed time = slower attacks).
        float overrideMult = attackSpeedApi.getActiveMultiplier(uuid);
        if (Math.abs(overrideMult - 1.0f) > 0.001f) {
            snapshot = new AttackSpeedSnapshot(
                    clamp(snapshot.chainMultiplier() * overrideMult, ABSOLUTE_MIN_MULTIPLIER, ABSOLUTE_MAX_MULTIPLIER),
                    clamp(snapshot.cooldownMultiplier() * overrideMult, ABSOLUTE_MIN_MULTIPLIER, ABSOLUTE_MAX_MULTIPLIER),
                    clamp(snapshot.animationMultiplier() * overrideMult, syncConfig.animationMinSpeed(), syncConfig.animationMaxSpeed())
            );
        }

        if (snapshot.isIdentity()) {
            return;
        }

        // Chain acceleration uses chainMultiplier (weapon-weighted swing speed + combo bonus)
        // Symmetric cap: positive = faster, negative = slower (slow debuffs)
        float chainExtra = clamp(dt * (snapshot.chainMultiplier() - 1.0f), -MAX_EXTRA_PER_TICK, MAX_EXTRA_PER_TICK);
        accelerateActiveChains(manager, uuid, chainExtra);

        // Cooldown acceleration uses cooldownMultiplier (weapon-weighted gap reduction)
        if (reflectionAvailable) {
            float cdExtra = clamp(dt * (snapshot.cooldownMultiplier() - 1.0f), -MAX_EXTRA_PER_TICK, MAX_EXTRA_PER_TICK);
            if (Math.abs(cdExtra) > 0.0001f) {
                accelerateAttackCooldown(manager, uuid, cdExtra);
            }
        }
    }

    // ==================== CHAIN ACCELERATION ====================

    /**
     * Accelerates active Primary/Secondary attack chains by advancing their
     * current {@link InteractionEntry} timestamp, and tracks cooldown IDs
     * from Primary chains for inter-attack cooldown acceleration.
     *
     * <p>Mechanism:
     * {@link InteractionEntry#setTimestamp(long, float)} shifts the entry's
     * timestamp backwards, making {@code getTimeInSeconds()} return inflated
     * elapsed times. This makes all operations (damage, particles, recovery)
     * complete proportionally faster.
     *
     * <p>All APIs used here are public — no reflection needed.
     */
    private void accelerateActiveChains(InteractionManager manager, UUID uuid, float extraSeconds) {
        for (InteractionChain chain : manager.getChains().values()) {
            InteractionType type = chain.getType();
            if (type != InteractionType.Primary && type != InteractionType.Secondary) {
                continue;
            }

            if (chain.getServerState() != InteractionState.NotFinished) {
                continue;
            }

            // Always track cooldown IDs from Primary chains (even for ammo weapons —
            // cooldown acceleration between volleys is safe and desirable)
            if (type == InteractionType.Primary) {
                String cooldownId = extractCooldownId(chain);
                if (cooldownId != null) {
                    primaryAttackCooldownIds.put(uuid, cooldownId);
                }
            }

            // Safety: never accelerate chain timestamps on ammo-reload weapons,
            // regardless of profile config. This is a hard engine constraint —
            // ammo-based reload uses Simple+Repeat chains where timestamp acceleration
            // causes permanent desync (infinite reload loop).
            if (isAmmoReloadChain(chain)) {
                continue;
            }

            // Skip if chain acceleration is effectively zero (e.g., crossbow atkWeight=0)
            if (Math.abs(extraSeconds) < 0.0001f) {
                continue;
            }

            // Advance entry timestamp to accelerate chain operations
            InteractionEntry entry = resolveEntry(chain);
            if (entry == null) {
                continue;
            }

            long ts = entry.getTimestamp();
            if (ts <= 0L) {
                continue; // Entry not yet initialized — let vanilla handle first tick
            }

            entry.setTimestamp(ts, extraSeconds);
        }
    }

    /**
     * Detects interaction chains from weapons with ammo-based reload mechanics.
     *
     * <p>Crossbow (and future ammo weapons like Crossbow_Heavy, Handgun, Rifle) use
     * a {@code Repeat:-1} loop of {@code Simple} interactions for reload. These read
     * from {@code InteractionEntry.timestamp} (not {@code simulationTimestamp}), so
     * our timestamp advancement breaks their iteration timing. The stat modifier that
     * increments ammo desyncs from the accelerated iterations, causing reload to never
     * complete.
     *
     * <p>Bows use {@code ChargingInteraction} which reads {@code simulationTimestamp}
     * — unaffected by our acceleration and excluded from this check.
     *
     * @return true if this chain belongs to a weapon with ammo reload mechanics
     */
    private static boolean isAmmoReloadChain(InteractionChain chain) {
        try {
            RootInteraction root = chain.getInitialRootInteraction();
            if (root == null) {
                return false;
            }
            String id = root.getId();
            if (id == null) {
                return false;
            }
            // Root IDs follow pattern: Root_Weapon_<Type>_<Action>
            // e.g. Root_Weapon_Crossbow_Primary_Signature
            return id.contains("Crossbow") || id.contains("Handgun") || id.contains("Rifle");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Resolves the current {@link InteractionEntry} from a chain.
     *
     * <p>Follows AAF's resilient lookup pattern: tries the current operation index,
     * then searches ±4 positions to handle cases where the exact entry doesn't exist
     * yet or was already consumed.
     */
    @Nullable
    private static InteractionEntry resolveEntry(InteractionChain chain) {
        int base = chain.getOperationIndex();
        InteractionEntry entry = chain.getInteraction(base);
        if (entry != null) {
            return entry;
        }
        for (int delta = 1; delta <= 4; delta++) {
            entry = chain.getInteraction(base - delta);
            if (entry != null) {
                return entry;
            }
            entry = chain.getInteraction(base + delta);
            if (entry != null) {
                return entry;
            }
        }
        return null;
    }

    // ==================== COOLDOWN ACCELERATION ====================

    /**
     * Selectively accelerates the player's Primary attack cooldown by calling
     * {@code cooldown.tick(extraSeconds)} on the specific cooldown entry.
     *
     * <p>This ensures that after a chain completes faster (due to entry timestamp
     * advancement), the cooldown doesn't become the new bottleneck. Only the
     * identified Primary attack cooldown is affected — other cooldowns (Secondary,
     * utility, casting) are untouched.
     */
    private void accelerateAttackCooldown(InteractionManager manager, UUID uuid, float extraSeconds) {
        String cooldownId = primaryAttackCooldownIds.get(uuid);
        if (cooldownId == null) {
            return;
        }

        try {
            CooldownHandler handler = (CooldownHandler) cooldownHandlerField.get(manager);
            if (handler == null) {
                return;
            }

            CooldownHandler.Cooldown cooldown = handler.getCooldown(cooldownId);
            if (cooldown == null) {
                return;
            }

            cooldown.tick(extraSeconds);

        } catch (IllegalAccessException e) {
            LOGGER.atWarning().withCause(e).log("Failed to access cooldownHandler field");
            reflectionAvailable = false;
        }
    }

    // ==================== HELPERS ====================

    /**
     * Extracts the cooldown ID from an interaction chain, using the same resolution
     * logic as Hytale's {@code InteractionChain.onCompletion()}.
     */
    @Nullable
    private static String extractCooldownId(InteractionChain chain) {
        try {
            RootInteraction root = chain.getInitialRootInteraction();
            if (root == null) {
                return null;
            }
            String cooldownId = root.getId();
            InteractionCooldown cooldownConfig = root.getCooldown();
            if (cooldownConfig != null && cooldownConfig.cooldownId != null) {
                cooldownId = cooldownConfig.cooldownId;
            }
            return cooldownId;
        } catch (Exception e) {
            LOGGER.atFine().log("Failed to extract cooldown ID from chain: %s", e.getMessage());
            return null;
        }
    }

    /**
     * Resolves the weapon type for the player's currently held item.
     *
     * @param player The player entity (from ECS query), or null
     * @return The weapon type, or UNKNOWN if not resolvable
     */
    @Nonnull
    private static WeaponType resolveHeldWeaponType(@Nullable Player player) {
        if (player == null) {
            return WeaponType.UNKNOWN;
        }
        try {
            Inventory inventory = player.getInventory();
            if (inventory == null) {
                return WeaponType.UNKNOWN;
            }
            ItemStack heldItem = inventory.getActiveHotbarItem();
            if (heldItem == null) {
                return WeaponType.UNKNOWN;
            }
            com.hypixel.hytale.server.core.asset.type.item.config.Item item = heldItem.getItem();
            if (item == null || item == com.hypixel.hytale.server.core.asset.type.item.config.Item.UNKNOWN) {
                return WeaponType.UNKNOWN;
            }
            return WeaponType.fromItemIdOrUnknown(item.getId());
        } catch (Exception e) {
            return WeaponType.UNKNOWN;
        }
    }

    /**
     * Finds the chainId of the active Primary interaction chain, or -1 if none active.
     * Used for combo detection — each new Primary attack gets a new chainId.
     */
    private static int findActivePrimaryChainId(InteractionManager manager) {
        for (InteractionChain chain : manager.getChains().values()) {
            if (chain.getType() == InteractionType.Primary
                    && chain.getServerState() == InteractionState.NotFinished) {
                return chain.getChainId();
            }
        }
        return -1;
    }

    /**
     * Returns the public API for external mods to push temporary speed overrides.
     */
    @Nonnull
    public AttackSpeedApi getAttackSpeedApi() {
        return attackSpeedApi;
    }

    /**
     * Removes tracked state for a disconnected player.
     */
    public void onPlayerDisconnect(UUID uuid) {
        primaryAttackCooldownIds.remove(uuid);
        comboTracker.clear(uuid);
        attackSpeedApi.onPlayerDisconnect(uuid);
    }

    /**
     * Initializes reflection for the {@code InteractionManager.cooldownHandler} field.
     */
    private static synchronized void initializeReflection() {
        if (reflectionInitialized) {
            return;
        }
        reflectionInitialized = true;

        try {
            cooldownHandlerField = InteractionManager.class.getDeclaredField("cooldownHandler");
            cooldownHandlerField.setAccessible(true);
            reflectionAvailable = true;
            LOGGER.atInfo().log("Attack speed cooldown acceleration: reflection initialized");
        } catch (NoSuchFieldException e) {
            LOGGER.atWarning().log(
                    "Cooldown acceleration unavailable — field 'cooldownHandler' not found. "
                    + "Chain acceleration still active. This may be caused by a Hytale update."
            );
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Cooldown acceleration reflection failed");
        }
    }

    /**
     * @return true if cooldown acceleration is available (reflection succeeded)
     */
    public static boolean isCooldownAccelerationAvailable() {
        return reflectionAvailable;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
