package io.github.larsonix.trailoforbis.mobs.modifiers;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.config.ConfigManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.logging.Level;

/**
 * Central manager for the mob modifier system.
 *
 * <p>Lifecycle:
 * <ol>
 *   <li>Constructor stores dependencies (no I/O)</li>
 *   <li>{@link #initialize()} loads config, creates sub-components</li>
 *   <li>{@link #shutdown()} cleans up resources</li>
 * </ol>
 *
 * <p>Initialized at Phase 6.6 (after MobScalingManager at 6.5).
 * Implements {@link MobModifierService} for cross-system access.
 */
public class MobModifierManager implements MobModifierService {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final TrailOfOrbis plugin;
    private final ConfigManager configManager;

    // Sub-components
    private MobModifierConfig config;
    private MobModifierRoller roller;
    private MobModifierEffectRegistry effectRegistry;
    private MobModifierApplier applier;
    private MobModifierDeathHandler deathHandler;
    private boolean initialized = false;

    // Pending explosions from Volatile death trigger (processed by tick system)
    private final java.util.concurrent.ConcurrentLinkedQueue<MobModifierDeathHandler.PendingExplosion> pendingExplosions =
        new java.util.concurrent.ConcurrentLinkedQueue<>();

    // Active area damage zones (Blazing trail, Venomous cloud) — processed by tick system
    private final java.util.concurrent.ConcurrentLinkedQueue<AreaDamageZone> areaDamageZones =
        new java.util.concurrent.ConcurrentLinkedQueue<>();

    // Mobs currently buffed by Pack Leader (ref → damage bonus %). Refreshed each tick.
    // Used by ConditionalMultiplierCalculator to apply damage bonus during combat.
    private final java.util.concurrent.ConcurrentHashMap<Integer, Float> packLeaderBuffedMobs =
        new java.util.concurrent.ConcurrentHashMap<>();

    public MobModifierManager(@Nonnull TrailOfOrbis plugin, @Nonnull ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    /**
     * Initializes the modifier system.
     * Called during plugin start() at Phase 6.6.
     *
     * @return true if initialization succeeded
     */
    public boolean initialize() {
        try {
            config = configManager.getMobModifierConfig();
            if (config == null) {
                config = MobModifierConfig.createDefaults();
            }
            config.validate();

            if (!config.isEnabled()) {
                LOGGER.at(Level.INFO).log("MobModifierManager disabled in config");
                initialized = true;
                return true;
            }

            roller = new MobModifierRoller(config);

            effectRegistry = new MobModifierEffectRegistry(plugin, config);
            effectRegistry.initialize();

            applier = new MobModifierApplier(config, effectRegistry);
            deathHandler = new MobModifierDeathHandler(plugin, config);

            initialized = true;
            LOGGER.at(Level.INFO).log("MobModifierManager initialized (%d modifier types)",
                ModifierType.values().length);
            return true;
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("MobModifierManager initialization failed");
            return false;
        }
    }

    public void shutdown() {
        if (effectRegistry != null) {
            effectRegistry.shutdown();
        }
        LOGGER.at(Level.INFO).log("MobModifierManager shutting down");
        initialized = false;
    }

    // ==================== Public API ====================

    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Whether the modifier system is fully active.
     */
    @Override
    public boolean isEnabled() {
        return initialized && config != null && config.isEnabled();
    }

    @Nonnull
    public MobModifierConfig getConfig() {
        return config != null ? config : MobModifierConfig.createDefaults();
    }

    @Nonnull
    public MobModifierRoller getRoller() {
        return roller;
    }

    @Nonnull
    public MobModifierApplier getApplier() {
        return applier;
    }

    @Nonnull
    public MobModifierEffectRegistry getEffectRegistry() {
        return effectRegistry;
    }

    @Nonnull
    public MobModifierDeathHandler getDeathHandler() {
        return deathHandler;
    }

    public void queuePendingExplosion(@Nonnull MobModifierDeathHandler.PendingExplosion explosion) {
        pendingExplosions.add(explosion);
    }

    @Nonnull
    public java.util.concurrent.ConcurrentLinkedQueue<MobModifierDeathHandler.PendingExplosion> getPendingExplosions() {
        return pendingExplosions;
    }

    // ==================== Area Damage Zones ====================

    public void addAreaDamageZone(@Nonnull AreaDamageZone zone) {
        areaDamageZones.add(zone);
    }

    @Nonnull
    public java.util.concurrent.ConcurrentLinkedQueue<AreaDamageZone> getAreaDamageZones() {
        return areaDamageZones;
    }

    // ==================== Pack Leader Buff Tracking ====================

    /**
     * Marks a mob as buffed by Pack Leader for the current tick.
     * Key is the entity's hash code (stable per ECS tick, not across ticks).
     * Cleared each tick cycle by the tick system.
     */
    public void markPackLeaderBuffed(int entityHash, float damageBonus) {
        packLeaderBuffedMobs.put(entityHash, damageBonus);
    }

    public float getPackLeaderDamageBonus(int entityHash) {
        Float bonus = packLeaderBuffedMobs.get(entityHash);
        return bonus != null ? bonus : 0f;
    }

    public void clearPackLeaderBuffs() {
        packLeaderBuffedMobs.clear();
    }

    /**
     * Area damage zone for Blazing trail and Venomous cloud.
     * Deals damage to players within radius each tick until expiry.
     */
    public record AreaDamageZone(
        @Nonnull com.hypixel.hytale.math.vector.Vector3d position,
        float damagePerSecond,
        double radius,
        long expiryTimeMs
    ) {
        public boolean isExpired() {
            return System.currentTimeMillis() >= expiryTimeMs;
        }
    }

    // ==================== Service Interface ====================

    @Override
    @Nonnull
    public List<ModifierType> getModifiers(@Nonnull Ref<EntityStore> mobRef, @Nonnull Store<EntityStore> store) {
        MobModifierComponent comp = getComponent(mobRef, store);
        return comp != null ? comp.getModifiers() : List.of();
    }

    @Override
    public boolean hasModifier(@Nonnull Ref<EntityStore> mobRef, @Nonnull Store<EntityStore> store, @Nonnull ModifierType type) {
        MobModifierComponent comp = getComponent(mobRef, store);
        return comp != null && comp.hasModifier(type);
    }

    @Override
    public int getModifierCount(@Nonnull Ref<EntityStore> mobRef, @Nonnull Store<EntityStore> store) {
        MobModifierComponent comp = getComponent(mobRef, store);
        return comp != null ? comp.modifierCount() : 0;
    }

    @Override
    @Nullable
    public MobModifierComponent getComponent(@Nonnull Ref<EntityStore> mobRef, @Nonnull Store<EntityStore> store) {
        if (!mobRef.isValid()) return null;
        return store.getComponent(mobRef, MobModifierComponent.getComponentType());
    }
}
