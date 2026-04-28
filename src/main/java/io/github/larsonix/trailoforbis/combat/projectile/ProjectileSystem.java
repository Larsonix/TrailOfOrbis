package io.github.larsonix.trailoforbis.combat.projectile;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.HolderSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.asset.type.projectile.config.Projectile;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.physics.SimplePhysicsProvider;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.attributes.AttributeManager;
import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.compat.HytaleAPICompat;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;

import javax.annotation.Nonnull;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * ECS HolderSystem that modifies projectile physics based on player stats.
 *
 * <p>When a projectile is spawned by a player, this system:
 * <ol>
 *   <li>Gets the shooter's UUID from ProjectileComponent</li>
 *   <li>Retrieves the player's ComputedStats</li>
 *   <li>Applies speed modifier to projectile velocity</li>
 *   <li>Applies gravity modifier to projectile physics</li>
 * </ol>
 *
 * <h2>Stat Effects</h2>
 * <ul>
 *   <li>{@code projectileSpeedPercent} - Modifies velocity magnitude
 *       (e.g., +50% = 1.5x speed, -25% = 0.75x speed)</li>
 *   <li>{@code projectileGravityPercent} - Modifies gravity
 *       (e.g., -50% = half gravity = floats longer, +100% = 2x gravity = drops faster)</li>
 * </ul>
 *
 * <h2>Configuration</h2>
 * The system uses {@link ProjectileConfig} to clamp values within safe bounds:
 * <ul>
 *   <li>Speed: min -50% to max +200% (0.5x to 3x base speed)</li>
 *   <li>Gravity: min -90% to max +100% (0.1x to 2x base gravity)</li>
 * </ul>
 *
 * <h2>Deprecation Note</h2>
 * <p>Uses deprecated {@link SimplePhysicsProvider} and {@link Projectile} classes because
 * Hytale's {@link ProjectileComponent} API still returns these types. No replacement
 * has been provided yet (not marked {@code forRemoval=true}).
 *
 * @see ProjectileConfig
 * @see HytaleAPICompat#getProjectileCreator(ProjectileComponent)
 */
@SuppressWarnings("deprecation") // SimplePhysicsProvider and Projectile - Hytale hasn't provided replacements
public class ProjectileSystem extends HolderSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final TrailOfOrbis plugin;
    private final ComponentType<EntityStore, ProjectileComponent> projectileType;
    private final ComponentType<EntityStore, BoundingBox> boundingBoxType;

    public ProjectileSystem(@Nonnull TrailOfOrbis plugin) {
        this.plugin = plugin;
        this.projectileType = ProjectileComponent.getComponentType();
        this.boundingBoxType = BoundingBox.getComponentType();
    }

    @Override
    @Nonnull
    public Query<EntityStore> getQuery() {
        return Archetype.of(projectileType, boundingBoxType);
    }

    @Override
    public void onEntityAdd(@Nonnull Holder<EntityStore> holder, @Nonnull AddReason reason,
                            @Nonnull Store<EntityStore> store) {
        // Only process on SPAWN (not LOAD - don't reprocess loaded projectiles)
        if (reason != AddReason.SPAWN) {
            return;
        }

        // Check if system is enabled
        ProjectileConfig config = plugin.getProjectileConfig();
        if (config == null || !config.isEnabled()) {
            return;
        }

        // Get ProjectileComponent
        ProjectileComponent projectileComp = holder.getComponent(projectileType);
        if (projectileComp == null) {
            return;
        }

        // Get shooter UUID using HytaleAPICompat (handles reflection safely)
        Optional<UUID> shooterOpt = HytaleAPICompat.getProjectileCreator(projectileComp);
        if (shooterOpt.isEmpty()) {
            // No shooter info available (NPC projectile, or API unavailable)
            return;
        }

        UUID shooterId = shooterOpt.get();

        // Only apply stat-based modifiers to player projectiles
        PlayerRef shooterPlayer = Universe.get().getPlayer(shooterId);
        if (shooterPlayer == null) {
            return; // NPC projectile — no RPG stat modifiers
        }

        // Get player's computed stats
        AttributeManager attrManager = plugin.getAttributeManager();
        if (attrManager == null) {
            return;
        }

        ComputedStats stats = attrManager.getStats(shooterId);
        if (stats == null) {
            // Not a player, or player stats not loaded
            return;
        }

        // Get physics provider
        SimplePhysicsProvider physics = projectileComp.getSimplePhysicsProvider();
        if (physics == null) {
            return;
        }

        // Get BoundingBox (required for setGravity)
        BoundingBox boundingBox = holder.getComponent(boundingBoxType);
        if (boundingBox == null) {
            return;
        }

        // Apply speed modifier
        float speedPercent = stats.getProjectileSpeedPercent();
        if (Math.abs(speedPercent) > 0.01f) {
            applySpeedModifier(physics, speedPercent, config);
        }

        // Apply gravity modifier
        float gravityPercent = stats.getProjectileGravityPercent();
        if (Math.abs(gravityPercent) > 0.01f) {
            applyGravityModifier(projectileComp, physics, boundingBox, gravityPercent, config);
        }

        // Log at FINE level to avoid spam
        if (Math.abs(speedPercent) > 0.01f || Math.abs(gravityPercent) > 0.01f) {
            LOGGER.at(Level.FINE).log("[ProjectileSystem] Modified projectile for %s: speed=%.1f%%, gravity=%.1f%%",
                shooterId.toString().substring(0, 8), speedPercent, gravityPercent);
        }
    }

    @Override
    public void onEntityRemoved(@Nonnull Holder<EntityStore> holder, @Nonnull RemoveReason reason,
                                @Nonnull Store<EntityStore> store) {
        // No cleanup needed - projectile physics are one-time modifications
    }

    /**
     * Applies speed modifier to projectile velocity.
     *
     * <p>Scales the current velocity vector by the calculated multiplier.
     * The multiplier is clamped by config bounds to prevent extreme values.
     *
     * @param physics The projectile's physics provider
     * @param speedPercent The speed bonus percentage from player stats
     * @param config Configuration for clamping
     */
    private void applySpeedModifier(@Nonnull SimplePhysicsProvider physics,
                                    float speedPercent,
                                    @Nonnull ProjectileConfig config) {
        Vector3d velocity = physics.getVelocity();

        // Guard: skip if velocity not yet initialized (race with physics engine on charged attacks)
        // Multiplying (0,0,0) by any multiplier = (0,0,0) → frozen projectile
        if (Math.abs(velocity.x) < 0.00001 && Math.abs(velocity.y) < 0.00001 && Math.abs(velocity.z) < 0.00001) {
            LOGGER.atWarning().log("[ProjectileSystem] Skipping speed modifier (%.1f%%) — velocity uninitialized (0,0,0)",
                speedPercent);
            return;
        }

        float multiplier = config.clampSpeedMultiplier(speedPercent);
        Vector3d scaledVelocity = new Vector3d(
            velocity.x * multiplier,
            velocity.y * multiplier,
            velocity.z * multiplier
        );

        physics.setVelocity(scaledVelocity);
    }

    /**
     * Applies gravity modifier to projectile physics.
     *
     * <p>Gets the base gravity from the projectile asset and scales it.
     * Negative gravity percent = floats longer, positive = drops faster.
     *
     * @param projectileComp The projectile component (for getting base gravity)
     * @param physics The projectile's physics provider
     * @param boundingBox The entity's bounding box (required by setGravity)
     * @param gravityPercent The gravity bonus percentage from player stats
     * @param config Configuration for clamping
     */
    private void applyGravityModifier(@Nonnull ProjectileComponent projectileComp,
                                      @Nonnull SimplePhysicsProvider physics,
                                      @Nonnull BoundingBox boundingBox,
                                      float gravityPercent,
                                      @Nonnull ProjectileConfig config) {
        // Get base gravity from projectile asset
        Projectile projectileAsset = projectileComp.getProjectile();
        if (projectileAsset == null) {
            return;
        }

        double baseGravity = projectileAsset.getGravity();

        // Guard: skip if base gravity is zero (uninitialized or intentionally zero-gravity projectile)
        if (Math.abs(baseGravity) < 0.00001) {
            LOGGER.atFine().log("[ProjectileSystem] Skipping gravity modifier — base gravity is 0.0");
            return;
        }

        float multiplier = config.clampGravityMultiplier(gravityPercent);
        double modifiedGravity = baseGravity * multiplier;

        physics.setGravity(modifiedGravity, boundingBox);
    }
}
