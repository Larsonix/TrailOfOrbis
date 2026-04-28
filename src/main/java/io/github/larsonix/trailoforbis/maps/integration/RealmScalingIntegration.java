package io.github.larsonix.trailoforbis.maps.integration;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.maps.RealmsManager;
import io.github.larsonix.trailoforbis.maps.components.RealmMobComponent;
import io.github.larsonix.trailoforbis.maps.core.RealmMapData;
import io.github.larsonix.trailoforbis.maps.instance.RealmInstance;
import io.github.larsonix.trailoforbis.maps.modifiers.RealmModifier;
import io.github.larsonix.trailoforbis.maps.modifiers.RealmModifierType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Integrates realm mechanics with the mob scaling system.
 *
 * <p>This class ensures that mobs inside realms use the realm's level
 * instead of the normal player-based scaling, and applies realm modifiers
 * to mob stats.
 *
 * <h2>Integration Points</h2>
 * <ul>
 *   <li>{@link #getMobLevelOverride(Ref, ComponentAccessor)} - Called by MobScalingSystem</li>
 *   <li>{@link #getHealthMultiplier(UUID)} - Called for mob health scaling</li>
 *   <li>{@link #getDamageMultiplier(UUID)} - Called for mob damage scaling</li>
 *   <li>{@link #getSpeedMultiplier(UUID)} - Called for mob speed scaling</li>
 * </ul>
 *
 * <h2>Level Override Logic</h2>
 * <ol>
 *   <li>If mob has {@link RealmMobComponent}, use realm level</li>
 *   <li>If mob is in a realm world but no component, use realm level</li>
 *   <li>Otherwise, return null to use normal scaling</li>
 * </ol>
 *
 * <h2>Usage in MobScalingSystem</h2>
 * <pre>{@code
 * // In MobScalingSystem.calculateMobLevel():
 * Integer override = realmScalingIntegration.getMobLevelOverride(mobRef, accessor);
 * if (override != null) {
 *     return override;
 * }
 * // ... normal player-based calculation
 * }</pre>
 *
 * @see io.github.larsonix.trailoforbis.mobs.MobScalingManager
 * @see RealmMobComponent
 */
public class RealmScalingIntegration {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final RealmsManager realmsManager;

    // ═══════════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Creates a new realm scaling integration.
     *
     * @param realmsManager The realms manager for checking realm data
     */
    public RealmScalingIntegration(@Nonnull RealmsManager realmsManager) {
        this.realmsManager = Objects.requireNonNull(realmsManager, "realmsManager cannot be null");
    }

    // ═══════════════════════════════════════════════════════════════════
    // LEVEL OVERRIDE
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets the level override for a mob if it's in a realm.
     *
     * <p>This is called by the mob scaling system to determine if the
     * normal player-based level calculation should be bypassed.
     *
     * @param mobRef The mob entity reference
     * @param accessor Component accessor for reading components
     * @return The realm level to use, or null if normal scaling should apply
     */
    @Nullable
    public Integer getMobLevelOverride(
            @Nonnull Ref<EntityStore> mobRef,
            @Nonnull ComponentAccessor<EntityStore> accessor) {

        Objects.requireNonNull(mobRef, "mobRef cannot be null");
        Objects.requireNonNull(accessor, "accessor cannot be null");

        if (!mobRef.isValid()) {
            return null;
        }

        // Check for RealmMobComponent
        RealmMobComponent realmMob = accessor.getComponent(mobRef, RealmMobComponent.getComponentType());
        if (realmMob == null || realmMob.getRealmId() == null) {
            return null;
        }

        // Get realm data
        UUID realmId = realmMob.getRealmId();
        Optional<RealmInstance> realmOpt = realmsManager.getRealm(realmId);
        if (realmOpt.isEmpty()) {
            // Realm no longer exists - mob shouldn't exist either
            LOGGER.atFine().log("Mob belongs to non-existent realm %s",
                realmId.toString().substring(0, 8));
            return null;
        }

        RealmInstance realm = realmOpt.get();
        int level = realm.getMapData().level();

        LOGGER.atFine().log("Mob level override: realm=%s, level=%d",
            realmId.toString().substring(0, 8), level);

        return level;
    }

    /**
     * Gets the level for a specific realm.
     *
     * @param realmId The realm UUID
     * @return The realm level, or 1 if realm not found
     */
    public int getRealmLevel(@Nonnull UUID realmId) {
        Optional<RealmInstance> realmOpt = realmsManager.getRealm(realmId);
        return realmOpt.map(realm -> realm.getMapData().level()).orElse(1);
    }

    // ═══════════════════════════════════════════════════════════════════
    // STAT MULTIPLIERS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets the health multiplier for mobs in a realm.
     *
     * <p>Based on MONSTER_HEALTH modifier.
     *
     * @param realmId The realm UUID
     * @return Health multiplier (1.0 = no change)
     */
    public float getHealthMultiplier(@Nonnull UUID realmId) {
        return getStatMultiplier(realmId, RealmModifierType.MONSTER_HEALTH);
    }

    /**
     * Gets the damage multiplier for mobs in a realm.
     *
     * <p>Based on MONSTER_DAMAGE modifier.
     *
     * @param realmId The realm UUID
     * @return Damage multiplier (1.0 = no change)
     */
    public float getDamageMultiplier(@Nonnull UUID realmId) {
        return getStatMultiplier(realmId, RealmModifierType.MONSTER_DAMAGE);
    }

    /**
     * Gets the speed multiplier for mobs in a realm.
     *
     * <p>Based on MONSTER_SPEED modifier.
     *
     * @param realmId The realm UUID
     * @return Speed multiplier (1.0 = no change)
     */
    public float getSpeedMultiplier(@Nonnull UUID realmId) {
        return getStatMultiplier(realmId, RealmModifierType.MONSTER_SPEED);
    }

    /**
     * Gets a stat multiplier from realm modifiers.
     */
    private float getStatMultiplier(@Nonnull UUID realmId, @Nonnull RealmModifierType type) {
        Optional<RealmInstance> realmOpt = realmsManager.getRealm(realmId);
        if (realmOpt.isEmpty()) {
            return 1.0f;
        }

        RealmMapData mapData = realmOpt.get().getMapData();
        int bonusPercent = mapData.modifiers().stream()
            .filter(m -> m.type() == type)
            .mapToInt(RealmModifier::value)
            .sum();

        return 1.0f + (bonusPercent / 100.0f);
    }

    // ═══════════════════════════════════════════════════════════════════
    // COMBINED MODIFIERS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets all mob stat modifiers for a realm.
     *
     * @param realmId The realm UUID
     * @return Mob modifiers record with all multipliers
     */
    @Nonnull
    public MobModifiers getMobModifiers(@Nonnull UUID realmId) {
        Optional<RealmInstance> realmOpt = realmsManager.getRealm(realmId);
        if (realmOpt.isEmpty()) {
            return MobModifiers.NONE;
        }

        RealmMapData mapData = realmOpt.get().getMapData();

        float health = 1.0f;
        float damage = 1.0f;
        float speed = 1.0f;

        for (RealmModifier modifier : mapData.modifiers()) {
            float bonus = modifier.value() / 100.0f;
            switch (modifier.type()) {
                case MONSTER_HEALTH -> health += bonus;
                case MONSTER_DAMAGE -> damage += bonus;
                case MONSTER_SPEED -> speed += bonus;
                default -> { }
            }
        }

        return new MobModifiers(
            mapData.level(),
            health,
            damage,
            speed
        );
    }

    /**
     * Checks if an entity is a realm mob.
     *
     * @param mobRef The entity reference
     * @param accessor Component accessor
     * @return true if entity has RealmMobComponent with valid realm ID
     */
    public boolean isRealmMob(
            @Nonnull Ref<EntityStore> mobRef,
            @Nonnull ComponentAccessor<EntityStore> accessor) {

        if (!mobRef.isValid()) {
            return false;
        }

        RealmMobComponent realmMob = accessor.getComponent(mobRef, RealmMobComponent.getComponentType());
        return realmMob != null && realmMob.getRealmId() != null;
    }

    /**
     * Gets the realm ID for a mob, if it's a realm mob.
     *
     * @param mobRef The entity reference
     * @param accessor Component accessor
     * @return The realm UUID, or null if not a realm mob
     */
    @Nullable
    public UUID getMobRealmId(
            @Nonnull Ref<EntityStore> mobRef,
            @Nonnull ComponentAccessor<EntityStore> accessor) {

        if (!mobRef.isValid()) {
            return null;
        }

        RealmMobComponent realmMob = accessor.getComponent(mobRef, RealmMobComponent.getComponentType());
        return realmMob != null ? realmMob.getRealmId() : null;
    }

    // ═══════════════════════════════════════════════════════════════════
    // UTILITY
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets debug information for a realm's mob scaling.
     *
     * @param realmId The realm UUID
     * @return Debug string
     */
    @Nonnull
    public String getDebugInfo(@Nonnull UUID realmId) {
        MobModifiers mods = getMobModifiers(realmId);
        return String.format(
            "RealmScaling[realm=%s, level=%d, health=%.2fx, damage=%.2fx, speed=%.2fx]",
            realmId.toString().substring(0, 8),
            mods.level(),
            mods.healthMultiplier(),
            mods.damageMultiplier(),
            mods.speedMultiplier()
        );
    }

    // ═══════════════════════════════════════════════════════════════════
    // NESTED CLASSES
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Combined mob modifiers for a realm.
     *
     * @param level The fixed mob level
     * @param healthMultiplier Health multiplier
     * @param damageMultiplier Damage multiplier
     * @param speedMultiplier Speed multiplier
     */
    public record MobModifiers(
        int level,
        float healthMultiplier,
        float damageMultiplier,
        float speedMultiplier
    ) {
        /**
         * Default modifiers (no changes).
         */
        public static final MobModifiers NONE = new MobModifiers(1, 1.0f, 1.0f, 1.0f);

        /**
         * Checks if any modifiers are active.
         *
         * @return true if any multiplier differs from 1.0
         */
        public boolean hasModifiers() {
            return healthMultiplier != 1.0f ||
                   damageMultiplier != 1.0f ||
                   speedMultiplier != 1.0f;
        }
    }
}
