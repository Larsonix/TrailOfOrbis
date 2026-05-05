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
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.attributes.ComputedStats;

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
 * <p>All three layers use the same multiplier from {@link AnimationSpeedSyncConfig},
 * keeping server operations, cooldowns, and visuals in sync.
 *
 * <p>Runs AFTER {@link InteractionSystems.TickInteractionManagerSystem} each tick.
 *
 * <p><b>Why not {@code setTimeShift()}?</b> Hytale's {@code doTickChain()} resets
 * {@code chain.timeShift} to 0 every tick after the first run, making it useless for
 * ongoing speed modification and causing 60+ desync packets/sec.
 *
 * <p><b>Formula:</b>
 * <pre>
 * multiplier    = 1.0 + (attackSpeedPercent * scale / 100.0), clamped to [min, max]
 * extraSeconds  = min(dt * (multiplier - 1.0), MAX_EXTRA_PER_TICK)
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

    private final TrailOfOrbis plugin;
    private final AnimationSpeedSyncConfig syncConfig;

    private final ComponentType<EntityStore, InteractionManager> interactionManagerType;
    private final ComponentType<EntityStore, PlayerRef> playerRefType;

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

    public InteractionTimeShiftSystem(@Nonnull TrailOfOrbis plugin, @Nonnull AnimationSpeedSyncConfig syncConfig) {
        this.plugin = plugin;
        this.syncConfig = syncConfig;
        this.interactionManagerType = InteractionModule.get().getInteractionManagerComponent();
        this.playerRefType = PlayerRef.getComponentType();
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
            query = Archetype.of(interactionManagerType, playerRefType);
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

        float attackSpeedPercent = stats.getAttackSpeedPercent();
        if (Math.abs(attackSpeedPercent) < 0.001f) {
            return;
        }

        float multiplier = syncConfig.calculateMultiplier(attackSpeedPercent);
        float speedBonus = multiplier - 1.0f;

        if (Math.abs(speedBonus) < 0.001f) {
            return;
        }

        InteractionManager manager = store.getComponent(entityRef, interactionManagerType);
        if (manager == null) {
            return;
        }

        float extraSeconds = Math.min(dt * speedBonus, MAX_EXTRA_PER_TICK);

        // Phase 1: Accelerate active attack chains + track cooldown IDs
        accelerateActiveChains(manager, uuid, extraSeconds);

        // Phase 2: Accelerate the tracked Primary attack cooldown (inter-attack gap)
        if (reflectionAvailable) {
            accelerateAttackCooldown(manager, uuid, extraSeconds);
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

            // Skip ranged weapons with ammo/reload mechanics — their reload uses
            // Simple+Repeat interactions where timestamp acceleration causes desync
            // between server iteration timing and ammo stat application, resulting
            // in an infinite reload loop. Bows are unaffected (ChargingInteraction
            // reads simulationTimestamp, which we don't modify).
            if (isAmmoReloadChain(chain)) {
                primaryAttackCooldownIds.remove(uuid);
                continue;
            }

            // Track cooldown ID from Primary chains
            if (type == InteractionType.Primary) {
                String cooldownId = extractCooldownId(chain);
                if (cooldownId != null) {
                    primaryAttackCooldownIds.put(uuid, cooldownId);
                }
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
     * Removes tracked state for a disconnected player.
     */
    public void onPlayerDisconnect(UUID uuid) {
        primaryAttackCooldownIds.remove(uuid);
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
}
