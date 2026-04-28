package io.github.larsonix.trailoforbis.mobs.component;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.mobs.classification.RPGMobClass;
import io.github.larsonix.trailoforbis.mobs.model.MobStats;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Custom ECS component to store mob scaling data.
 *
 * <p>This component is attached to hostile NPCs on spawn to store their
 * calculated stats. The {@code RPGDamageSystem} reads this component to
 * apply mob stats in combat calculations.
 *
 * <p>Data stored:
 * <ul>
 *   <li><b>mobLevel</b>: Effective level based on nearby players</li>
 *   <li><b>distanceBonus</b>: Bonus pool from distance scaling</li>
 *   <li><b>stats</b>: Full MobStats with randomized distribution</li>
 *   <li><b>classification</b>: RPG Class (Monster, Elite, Boss, etc.)</li>
 * </ul>
 *
 * <p>Pattern follows Hytale's {@code WeatherTracker} component style.
 *
 * @see MobStats
 */
public class MobScalingComponent implements Component<EntityStore> {

    // ==================== CODEC ====================
    // Serializes scaling inputs only — MobStats is recalculated from level + classification on load.

    @SuppressWarnings("unchecked")
    public static final BuilderCodec<MobScalingComponent> CODEC = BuilderCodec.builder(
            MobScalingComponent.class, MobScalingComponent::new
        )
        .append(new KeyedCodec<>("MobLevel", Codec.INTEGER),
                MobScalingComponent::setMobLevel, MobScalingComponent::getMobLevel).add()
        .append(new KeyedCodec<>("DistanceLevel", Codec.INTEGER),
                MobScalingComponent::setDistanceLevel, MobScalingComponent::getDistanceLevel).add()
        .append(new KeyedCodec<>("PlayerLevelUsed", Codec.INTEGER),
                MobScalingComponent::setPlayerLevelUsed, MobScalingComponent::getPlayerLevelUsed).add()
        .append(new KeyedCodec<>("DistanceBonus", Codec.DOUBLE),
                MobScalingComponent::setDistanceBonus, MobScalingComponent::getDistanceBonus).add()
        .append(new KeyedCodec<>("Classification", (Codec<RPGMobClass>) (Codec<?>) new EnumCodec<>(RPGMobClass.class)),
                MobScalingComponent::setClassification, MobScalingComponent::getClassification).add()
        .append(new KeyedCodec<>("RoleName", Codec.STRING),
                MobScalingComponent::setRoleName,
                c -> Objects.requireNonNullElse(c.getRoleName(), "")).add()
        .append(new KeyedCodec<>("DynamicallyScaled", Codec.BOOLEAN),
                MobScalingComponent::setDynamicallyScaled, MobScalingComponent::isDynamicallyScaled).add()
        .append(new KeyedCodec<>("VanillaHP", Codec.INTEGER),
                MobScalingComponent::setVanillaHP, MobScalingComponent::getVanillaHP).add()
        .append(new KeyedCodec<>("VanillaDamage", Codec.FLOAT),
                MobScalingComponent::setVanillaDamage, MobScalingComponent::getVanillaDamage).add()
        .append(new KeyedCodec<>("IsDying", Codec.BOOLEAN),
                MobScalingComponent::setDying, MobScalingComponent::isDying).add()
        .build();

    // ==================== Scaling Metadata ====================

    /** Effective mob level (from player average × group multiplier) */
    private int mobLevel;

    /** Distance-based level estimate (when no players nearby) */
    private int distanceLevel;

    /** The average player level used to calculate this mob's stats */
    private int playerLevelUsed;

    /** Total bonus points from distance (before distribution) */
    private double distanceBonus;

    /** RPG Classification (determines XP and stat multipliers) */
    private RPGMobClass classification;

    /** The NPC role name (e.g., "trork_warrior") for display purposes */
    @Nullable
    private String roleName;

    /** Whether this mob was dynamically scaled (neutral → hostile) */
    private boolean dynamicallyScaled;

    // ==================== Vanilla Stats (for weighted formula) ====================

    /** Vanilla Hytale HP from Role.getInitialMaxHealth() */
    private int vanillaHP;

    /** Vanilla Hytale base damage (from weapon/attack interaction) */
    private float vanillaDamage;

    /**
     * Immediate death flag set when lethal damage is dealt.
     *
     * <p>This flag is set IMMEDIATELY when lethal damage is applied, BEFORE
     * the {@link com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent}
     * is committed via CommandBuffer. It bridges the race condition gap where:
     * <ol>
     *   <li>Health is set to 0 (immediate)</li>
     *   <li>DeathComponent is added via CommandBuffer (deferred)</li>
     *   <li>Other systems (regen, level refresh) could see health=0 + DeathComponent=null</li>
     * </ol>
     *
     * <p>All systems that modify mob health should check this flag alongside DeathComponent.
     */
    private boolean isDying;

    // ==================== Computed Stats ====================

    /** The full calculated mob stats with random distribution */
    @Nullable
    private MobStats stats;

    // ==================== Constructors ====================

    /**
     * Default constructor - required for component registration.
     * Creates an unscaled mob (level 1, no bonus).
     */
    public MobScalingComponent() {
        this.mobLevel = 1;
        this.distanceLevel = 0;
        this.playerLevelUsed = 1;
        this.distanceBonus = 0.0;
        this.classification = RPGMobClass.HOSTILE; // Default to standard hostile
        this.roleName = null;
        this.dynamicallyScaled = false;
        this.vanillaHP = 100; // Default vanilla HP
        this.vanillaDamage = 10.0f; // Default vanilla damage
        this.isDying = false;
        this.stats = MobStats.UNSCALED;
    }

    /**
     * Copy constructor - required for {@link #clone()}.
     */
    private MobScalingComponent(@Nonnull MobScalingComponent other) {
        this.mobLevel = other.mobLevel;
        this.distanceLevel = other.distanceLevel;
        this.playerLevelUsed = other.playerLevelUsed;
        this.distanceBonus = other.distanceBonus;
        this.classification = other.classification;
        this.roleName = other.roleName;
        this.dynamicallyScaled = other.dynamicallyScaled;
        this.vanillaHP = other.vanillaHP;
        this.vanillaDamage = other.vanillaDamage;
        this.isDying = other.isDying;
        this.stats = other.stats; // MobStats is immutable, safe to share
    }

    // ==================== Static Accessor ====================

    /**
     * Gets the component type from the plugin registry.
     *
     * <p>This pattern (from WeatherTracker) allows easy access to the component type
     * without passing references everywhere:
     * <pre>
     * MobScalingComponent scaling = store.getComponent(ref, MobScalingComponent.getComponentType());
     * </pre>
     *
     * @return The registered component type for MobScalingComponent
     */
    @Nonnull
    public static ComponentType<EntityStore, MobScalingComponent> getComponentType() {
        return TrailOfOrbis.getInstance().getMobScalingComponentType();
    }

    // ==================== Getters and Setters ====================

    public int getMobLevel() {
        return mobLevel;
    }

    public void setMobLevel(int mobLevel) {
        this.mobLevel = mobLevel;
    }

    public int getDistanceLevel() {
        return distanceLevel;
    }

    public void setDistanceLevel(int distanceLevel) {
        this.distanceLevel = distanceLevel;
    }

    public int getPlayerLevelUsed() {
        return playerLevelUsed;
    }

    public void setPlayerLevelUsed(int playerLevelUsed) {
        this.playerLevelUsed = playerLevelUsed;
    }

    public double getDistanceBonus() {
        return distanceBonus;
    }

    public void setDistanceBonus(double distanceBonus) {
        this.distanceBonus = distanceBonus;
    }

    public RPGMobClass getClassification() {
        return classification;
    }

    public void setClassification(RPGMobClass classification) {
        this.classification = classification;
    }

    @Nullable
    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(@Nullable String roleName) {
        this.roleName = roleName;
    }

    public boolean isDynamicallyScaled() {
        return dynamicallyScaled;
    }

    public void setDynamicallyScaled(boolean dynamicallyScaled) {
        this.dynamicallyScaled = dynamicallyScaled;
    }

    /**
     * Gets the vanilla Hytale HP from Role.getInitialMaxHealth().
     *
     * <p>Used in the weighted RPG formula to compress stat ranges.
     *
     * @return Vanilla HP value (typically 40-800 range)
     */
    public int getVanillaHP() {
        return vanillaHP;
    }

    /**
     * Sets the vanilla Hytale HP from Role.getInitialMaxHealth().
     *
     * @param vanillaHP The vanilla HP value from the mob's role
     */
    public void setVanillaHP(int vanillaHP) {
        this.vanillaHP = vanillaHP;
    }

    /**
     * Gets the vanilla Hytale base damage from weapon/attack interaction.
     *
     * <p>Used in the weighted RPG formula to compress damage ranges.
     *
     * @return Vanilla damage value
     */
    public float getVanillaDamage() {
        return vanillaDamage;
    }

    /**
     * Sets the vanilla Hytale base damage.
     *
     * @param vanillaDamage The vanilla damage value from the mob's attack
     */
    public void setVanillaDamage(float vanillaDamage) {
        this.vanillaDamage = vanillaDamage;
    }

    /**
     * Checks if this mob is in the process of dying.
     *
     * <p>This flag is set immediately when lethal damage is applied, before
     * DeathComponent is committed. Use this check alongside DeathComponent
     * to catch mobs in the race condition window.
     *
     * @return true if the mob has received lethal damage and is dying
     */
    public boolean isDying() {
        return isDying;
    }

    /**
     * Marks this mob as dying. Should only be called by the damage system
     * when lethal damage is applied.
     *
     * @param dying true to mark the mob as dying
     */
    public void setDying(boolean dying) {
        this.isDying = dying;
    }

    @Nonnull
    public MobStats getStats() {
        return stats != null ? stats : MobStats.UNSCALED;
    }

    public void setStats(@Nonnull MobStats stats) {
        this.stats = stats;
    }

    // ==================== Convenience Methods ====================

    /**
     * Checks if this mob has been scaled (not just default level 1).
     *
     * @return true if the mob has any scaling applied
     */
    public boolean isScaled() {
        return mobLevel > 1 || distanceBonus > 0 || classification != RPGMobClass.PASSIVE;
    }

    /**
     * Gets the effective power level for display.
     * Combines mob level with distance bonus for a rough power estimate.
     *
     * @return Estimated power level
     */
    public int getEffectivePower() {
        return stats != null ? stats.getEffectivePower() : mobLevel;
    }

    // ==================== Component Interface ====================

    /**
     * Required by Component interface - creates a deep copy.
     *
     * @return A new MobScalingComponent with the same values
     */
    @Override
    @Nonnull
    public Component<EntityStore> clone() {
        return new MobScalingComponent(this);
    }

    // ==================== Object Methods ====================

    @Override
    public String toString() {
        return String.format(
            "MobScalingComponent{lv=%d, dist=%.1f, class=%s, stats=%s}",
            mobLevel, distanceBonus, classification, stats
        );
    }
}
