package io.github.larsonix.trailoforbis.combat.detection;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.combat.AttackType;
import io.github.larsonix.trailoforbis.combat.deathrecap.DeathRecapFormatter;
import io.github.larsonix.trailoforbis.elemental.ElementType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Classifies damage events by type, cause, and element.
 *
 * <p>This class extracts damage classification logic from RPGDamageSystem,
 * providing methods to detect:
 * <ul>
 *   <li>Attack type (melee, projectile, area)</li>
 *   <li>Damage cause (fall, physical, projectile, DOT)</li>
 *   <li>Element type from DOT causes</li>
 * </ul>
 */
public class DamageTypeClassifier {

    /**
     * Gets the DamageCause from a Damage object using the non-deprecated API.
     *
     * <p>This method replaces direct calls to the deprecated {@code Damage.getCause()},
     * using {@code getDamageCauseIndex()} and the asset map lookup instead.
     *
     * @param damage The damage event
     * @return The damage cause, or null if not found
     */
    @Nullable
    public static DamageCause getDamageCause(@Nonnull Damage damage) {
        return DamageCause.getAssetMap().getAsset(damage.getDamageCauseIndex());
    }

    /**
     * Detects the attack type based on damage source and cause.
     *
     * <p>Detection logic:
     * <ol>
     *   <li>If damage source is ProjectileSource → PROJECTILE (most reliable)</li>
     *   <li>If damage source entity has ProjectileComponent → PROJECTILE (fallback)</li>
     *   <li>If damage cause is explosion-related → AREA</li>
     *   <li>Otherwise → MELEE (direct entity attack)</li>
     * </ol>
     *
     * @param store The entity store
     * @param damage The damage event
     * @return The detected attack type
     */
    @Nonnull
    public AttackType detectAttackType(@Nonnull Store<EntityStore> store, @Nonnull Damage damage) {
        // Environment damage (fall, lava, etc.) has no attack type bonus
        if (!(damage.getSource() instanceof Damage.EntitySource entitySource)) {
            return AttackType.UNKNOWN;
        }

        // Check if this is projectile damage via source type first (most reliable)
        // ProjectileSource stores both shooter ref and projectile ref
        if (damage.getSource() instanceof Damage.ProjectileSource) {
            return AttackType.PROJECTILE;
        }

        Ref<EntityStore> sourceRef = entitySource.getRef();
        if (sourceRef == null || !sourceRef.isValid()) {
            return AttackType.UNKNOWN;
        }

        // Check if damage source entity has ProjectileComponent (fallback for non-ProjectileSource cases)
        ProjectileComponent projectile = store.getComponent(sourceRef, ProjectileComponent.getComponentType());
        if (projectile != null) {
            return AttackType.PROJECTILE;
        }

        // Check damage cause for attack type indicators (uses AttackType keyword matching)
        DamageCause cause = getDamageCause(damage);
        if (cause != null) {
            AttackType fromCause = AttackType.fromCauseId(cause.getId());
            if (fromCause != AttackType.UNKNOWN) {
                return fromCause;
            }
        }

        // Default: direct melee attack
        return AttackType.MELEE;
    }

    /**
     * Checks if the damage is fall damage.
     *
     * <p>Uses string ID comparison rather than object reference equality for robustness.
     * The static {@code DamageCause.FALL} field may be null early in the server lifecycle
     * before {@code EntityModule.start()} populates it from assets.
     *
     * @param damage The damage event to check
     * @return true if this is fall damage
     */
    public boolean isFallDamage(@Nonnull Damage damage) {
        DamageCause cause = getDamageCause(damage);
        if (cause == null) {
            return false;
        }
        String id = cause.getId();
        return id != null && id.equalsIgnoreCase("Fall");
    }

    /**
     * Checks if damage is physical melee damage.
     *
     * <p>Uses string ID comparison for robustness since {@code DamageCause.PHYSICAL}
     * static field may be null early in the server lifecycle.
     *
     * @param damage The damage event to check
     * @return true if this is physical melee damage
     */
    public boolean isPhysicalMeleeDamage(@Nonnull Damage damage) {
        DamageCause cause = getDamageCause(damage);
        if (cause == null) {
            return false;
        }
        String id = cause.getId();
        return id != null && id.equalsIgnoreCase("Physical");
    }

    /**
     * Checks if damage is projectile damage.
     *
     * <p>Uses string ID comparison for robustness since {@code DamageCause.PROJECTILE}
     * static field may be null early in the server lifecycle.
     *
     * @param damage The damage event to check
     * @return true if this is projectile damage
     */
    public boolean isProjectileDamage(@Nonnull Damage damage) {
        DamageCause cause = getDamageCause(damage);
        if (cause == null) {
            return false;
        }
        String id = cause.getId();
        return id != null && id.equalsIgnoreCase("Projectile");
    }

    /**
     * Checks if damage is environmental (non-entity source).
     *
     * <p>Environmental damage includes lava, drowning, suffocation, void, etc.
     * These are converted to HP%-based damage for proper scaling with progression.
     *
     * @param damage The damage event to check
     * @return true if this is environmental damage
     */
    public boolean isEnvironmentalDamage(@Nonnull Damage damage) {
        // Check if this is a non-entity source (pure environmental)
        if (damage.getSource() instanceof Damage.EnvironmentSource) {
            return true;
        }

        // If not an EntitySource, check the cause for environmental indicators
        if (!(damage.getSource() instanceof Damage.EntitySource)) {
            return isEnvironmentalCause(getDamageCause(damage));
        }

        return false;
    }

    /**
     * Checks if the damage cause is an environmental type.
     *
     * <p>This method checks the cause ID for known environmental damage patterns.
     *
     * @param cause The damage cause to check
     * @return true if this is an environmental cause
     */
    public boolean isEnvironmentalCause(@Nullable DamageCause cause) {
        if (cause == null) {
            return false;
        }
        String id = cause.getId();
        if (id == null) {
            return false;
        }
        String lower = id.toLowerCase();
        return lower.contains("fire") ||
               lower.contains("burn") ||
               lower.contains("lava") ||
               lower.contains("drown") ||
               lower.contains("suffoc") ||
               lower.contains("poison") ||
               lower.contains("environment") ||
               lower.contains("void") ||
               lower.contains("outofworld") ||
               lower.contains("out_of_world");
    }

    /**
     * Checks if damage is from a damage-over-time effect (DOT).
     *
     * <p>DOT damage includes burning, poison, bleeding, freezing, etc.
     * These damages are handled differently:
     * <ul>
     *   <li>No flat damage bonuses (only base DOT amount)</li>
     *   <li>No critical strikes</li>
     *   <li>Still affected by resistances</li>
     * </ul>
     *
     * @param damage The damage event to check
     * @return true if this is DOT damage
     */
    public boolean isDOTDamage(@Nonnull Damage damage) {
        DamageCause cause = getDamageCause(damage);
        if (cause == null) {
            return false;
        }
        String id = cause.getId();
        if (id == null) {
            return false;
        }
        // Check for common DOT cause IDs (case-insensitive)
        String idLower = id.toLowerCase();
        return idLower.contains("burning") ||
               idLower.contains("poison") ||
               idLower.contains("bleeding") ||
               idLower.contains("bleed") ||
               idLower.contains("freezing") ||
               idLower.contains("frost") ||
               idLower.contains("fire") ||   // Fire damage over time
               idLower.contains("shock") ||  // Shock DOT
               idLower.contains("dot") ||
               idLower.contains("over_time");
    }

    /**
     * Checks if physical resistance should apply to this damage.
     *
     * <p>Physical resistance applies to:
     * <ul>
     *   <li>Physical melee damage (always)</li>
     *   <li>Projectile damage (configurable via physicalResistance.appliesToProjectiles)</li>
     * </ul>
     *
     * <p>Does NOT apply to fall, drowning, suffocation, environment, or other non-physical damage.
     *
     * @param damage The damage event to check
     * @param appliesToProjectiles Whether projectiles should be affected
     * @return true if physical resistance should reduce this damage
     */
    public boolean shouldApplyPhysicalResistance(@Nonnull Damage damage, boolean appliesToProjectiles) {
        DamageCause cause = getDamageCause(damage);
        if (cause == null) {
            return false;
        }

        // Respect the bypassResistances flag from DamageCause asset
        if (cause.doesBypassResistances()) {
            return false;
        }

        // Check damage type
        if (isPhysicalMeleeDamage(damage)) {
            return true;
        }
        if (appliesToProjectiles && isProjectileDamage(damage)) {
            return true;
        }
        return false;
    }

    /**
     * Determines the element type from a DOT damage cause.
     *
     * @param cause The damage cause to check
     * @return The element type, or null for physical DOT (bleed)
     */
    @Nullable
    public ElementType getElementFromDOTCause(@Nullable DamageCause cause) {
        if (cause == null) {
            return null;
        }
        String id = cause.getId();
        if (id == null) {
            return null;
        }
        String idLower = id.toLowerCase();

        if (idLower.contains("burning") || idLower.contains("fire")) {
            return ElementType.FIRE;
        }
        if (idLower.contains("freezing") || idLower.contains("frost") || idLower.contains("cold")) {
            return ElementType.WATER;
        }
        if (idLower.contains("shock") || idLower.contains("lightning") || idLower.contains("electr")) {
            return ElementType.LIGHTNING;
        }
        if (idLower.contains("poison") || idLower.contains("chaos")) {
            return ElementType.VOID;
        }
        // Physical DOT (bleed, etc.)
        return null;
    }

    /**
     * Formats a damage cause into a display name.
     *
     * @param cause The damage cause to format
     * @return Human-readable name for the damage cause
     */
    @Nonnull
    public String formatDamageCause(@Nullable DamageCause cause) {
        if (cause == null) {
            return "Unknown";
        }

        String id = cause.getId();
        if (id == null || id.isEmpty()) {
            return "Unknown";
        }

        // Common causes
        return switch (id.toLowerCase()) {
            case "fall" -> "Fall Damage";
            case "lava" -> "Lava";
            case "fire" -> "Fire";
            case "drowning" -> "Drowning";
            case "void" -> "The Void";
            case "suffocation" -> "Suffocation";
            case "starvation" -> "Starvation";
            case "poison" -> "Poison";
            case "wither" -> "Wither";
            case "explosion" -> "Explosion";
            case "thorns" -> "Thorns";
            default -> DeathRecapFormatter.formatMobName(id); // Format as title case
        };
    }
}
