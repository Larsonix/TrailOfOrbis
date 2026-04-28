package io.github.larsonix.trailoforbis.maps.components;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.TrailOfOrbis;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;

/**
 * ECS component marking a mob as belonging to a Realm instance.
 *
 * <p>Attached to mobs spawned by the {@code RealmMobSpawner} to:
 * <ul>
 *   <li>Track which realm the mob belongs to</li>
 *   <li>Determine if the mob counts towards completion</li>
 *   <li>Store any realm-specific stat modifiers</li>
 * </ul>
 *
 * <p>Used by:
 * <ul>
 *   <li>{@code RealmMobDeathListener} - To track kills for completion</li>
 *   <li>{@code RealmModifierSystem} - To apply realm modifiers to mob stats</li>
 *   <li>{@code RealmCompletionTracker} - To count remaining mobs</li>
 * </ul>
 *
 * @see io.github.larsonix.trailoforbis.maps.spawning.RealmMobSpawner
 * @see io.github.larsonix.trailoforbis.maps.core.RealmCompletionTracker
 */
public class RealmMobComponent implements Component<EntityStore> {

    // ═══════════════════════════════════════════════════════════════════
    // CODEC
    // ═══════════════════════════════════════════════════════════════════

    public static final BuilderCodec<RealmMobComponent> CODEC = BuilderCodec.builder(
            RealmMobComponent.class, RealmMobComponent::new
        )
        .append(new KeyedCodec<>("RealmId", Codec.UUID_STRING),
                RealmMobComponent::setRealmId,
                c -> Objects.requireNonNullElse(c.getRealmId(), new UUID(0L, 0L))).add()
        .append(new KeyedCodec<>("CountsForCompletion", Codec.BOOLEAN),
                RealmMobComponent::setCountsForCompletion, RealmMobComponent::countsForCompletion).add()
        .append(new KeyedCodec<>("WaveNumber", Codec.INTEGER),
                RealmMobComponent::setWaveNumber, RealmMobComponent::getWaveNumber).add()
        .append(new KeyedCodec<>("IsReinforcement", Codec.BOOLEAN),
                RealmMobComponent::setReinforcement, RealmMobComponent::isReinforcement).add()
        .append(new KeyedCodec<>("DamageMultiplier", Codec.FLOAT),
                RealmMobComponent::setDamageMultiplier, RealmMobComponent::getDamageMultiplier).add()
        .append(new KeyedCodec<>("HealthMultiplier", Codec.FLOAT),
                RealmMobComponent::setHealthMultiplier, RealmMobComponent::getHealthMultiplier).add()
        .append(new KeyedCodec<>("SpeedMultiplier", Codec.FLOAT),
                RealmMobComponent::setSpeedMultiplier, RealmMobComponent::getSpeedMultiplier).add()
        .append(new KeyedCodec<>("AttackSpeedMultiplier", Codec.FLOAT),
                RealmMobComponent::setAttackSpeedMultiplier, RealmMobComponent::getAttackSpeedMultiplier).add()
        .append(new KeyedCodec<>("RealmLevel", Codec.INTEGER),
                RealmMobComponent::setRealmLevel, RealmMobComponent::getRealmLevel).add()
        .append(new KeyedCodec<>("IsBoss", Codec.BOOLEAN),
                RealmMobComponent::setBoss, RealmMobComponent::isBoss).add()
        .append(new KeyedCodec<>("IsElite", Codec.BOOLEAN),
                RealmMobComponent::setElite, RealmMobComponent::isElite).add()
        .build();

    // ═══════════════════════════════════════════════════════════════════
    // STATIC TYPE REFERENCE
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Static reference to the component type - set during plugin init.
     * Allows {@link #getComponentType()} to work in async contexts.
     */
    @Nullable
    public static ComponentType<EntityStore, RealmMobComponent> TYPE = null;

    // ═══════════════════════════════════════════════════════════════════
    // FIELDS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * The unique ID of the realm instance this mob belongs to.
     */
    private UUID realmId;

    /**
     * Whether this mob counts towards realm completion.
     * Some mobs (adds, respawns) may not count.
     */
    private boolean countsForCompletion;

    /**
     * The wave number this mob was spawned in (0 = initial spawn).
     */
    private int waveNumber;

    /**
     * Whether this mob was spawned as a reinforcement.
     */
    private boolean isReinforcement;

    /**
     * Damage multiplier from realm modifiers.
     */
    private float damageMultiplier;

    /**
     * Health multiplier from realm modifiers.
     */
    private float healthMultiplier;

    /**
     * Speed multiplier from realm modifiers.
     */
    private float speedMultiplier;

    /**
     * Attack speed multiplier from realm modifiers.
     * Values > 1.0 = faster attacks (lower cooldown).
     * Values < 1.0 = slower attacks (higher cooldown).
     */
    private float attackSpeedMultiplier;

    /**
     * The realm's level at the time this mob was spawned.
     * Used by MobScalingSystem to determine stat scaling.
     */
    private int realmLevel;

    /**
     * Whether this mob was spawned from a BOSS pool in realm-mobs.yml.
     * This is the authoritative classification for realm mobs — the entity
     * discovery system (overworld patterns) is bypassed when this is set.
     */
    private boolean isBoss;

    /**
     * Whether this mob spawned as elite (spawn-time modifier).
     * Elite is independent of classification - both normal and boss mobs can be elite.
     * Elite mobs receive stat multipliers: 2.0x stats, 1.5x health, 3.0x XP.
     */
    private boolean isElite;

    // ═══════════════════════════════════════════════════════════════════
    // CONSTRUCTORS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Default constructor - required for component registration.
     */
    public RealmMobComponent() {
        this.realmId = null;
        this.countsForCompletion = true;
        this.waveNumber = 0;
        this.isReinforcement = false;
        this.damageMultiplier = 1.0f;
        this.healthMultiplier = 1.0f;
        this.speedMultiplier = 1.0f;
        this.attackSpeedMultiplier = 1.0f;
        this.realmLevel = 1;
        this.isBoss = false;
        this.isElite = false;
    }

    /**
     * Copy constructor - required for {@link #clone()}.
     */
    private RealmMobComponent(@Nonnull RealmMobComponent other) {
        this.realmId = other.realmId;
        this.countsForCompletion = other.countsForCompletion;
        this.waveNumber = other.waveNumber;
        this.isReinforcement = other.isReinforcement;
        this.damageMultiplier = other.damageMultiplier;
        this.healthMultiplier = other.healthMultiplier;
        this.speedMultiplier = other.speedMultiplier;
        this.attackSpeedMultiplier = other.attackSpeedMultiplier;
        this.realmLevel = other.realmLevel;
        this.isBoss = other.isBoss;
        this.isElite = other.isElite;
    }

    // ═══════════════════════════════════════════════════════════════════
    // STATIC ACCESSOR
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets the component type from the plugin registry.
     *
     * @return The registered component type
     * @throws IllegalStateException if component not yet registered
     */
    @Nonnull
    public static ComponentType<EntityStore, RealmMobComponent> getComponentType() {
        if (TYPE != null) {
            return TYPE;
        }
        return TrailOfOrbis.getInstance().getRealmMobComponentType();
    }

    // ═══════════════════════════════════════════════════════════════════
    // GETTERS AND SETTERS
    // ═══════════════════════════════════════════════════════════════════

    @Nullable
    public UUID getRealmId() {
        return realmId;
    }

    public void setRealmId(@Nonnull UUID realmId) {
        this.realmId = realmId;
    }

    public boolean countsForCompletion() {
        return countsForCompletion;
    }

    public void setCountsForCompletion(boolean countsForCompletion) {
        this.countsForCompletion = countsForCompletion;
    }

    public int getWaveNumber() {
        return waveNumber;
    }

    public void setWaveNumber(int waveNumber) {
        this.waveNumber = waveNumber;
    }

    public boolean isReinforcement() {
        return isReinforcement;
    }

    public void setReinforcement(boolean reinforcement) {
        this.isReinforcement = reinforcement;
    }

    public float getDamageMultiplier() {
        return damageMultiplier;
    }

    public void setDamageMultiplier(float damageMultiplier) {
        this.damageMultiplier = damageMultiplier;
    }

    public float getHealthMultiplier() {
        return healthMultiplier;
    }

    public void setHealthMultiplier(float healthMultiplier) {
        this.healthMultiplier = healthMultiplier;
    }

    public float getSpeedMultiplier() {
        return speedMultiplier;
    }

    public void setSpeedMultiplier(float speedMultiplier) {
        this.speedMultiplier = speedMultiplier;
    }

    /**
     * Gets the attack speed multiplier from realm modifiers.
     *
     * @return Attack speed multiplier (> 1.0 = faster attacks)
     */
    public float getAttackSpeedMultiplier() {
        return attackSpeedMultiplier;
    }

    /**
     * Sets the attack speed multiplier from realm modifiers.
     *
     * @param attackSpeedMultiplier Attack speed multiplier (> 1.0 = faster attacks)
     */
    public void setAttackSpeedMultiplier(float attackSpeedMultiplier) {
        this.attackSpeedMultiplier = attackSpeedMultiplier;
    }

    /**
     * Gets the realm level at the time this mob was spawned.
     *
     * @return The realm level (1+)
     */
    public int getRealmLevel() {
        return realmLevel;
    }

    /**
     * Sets the realm level for this mob.
     *
     * @param realmLevel The realm level (1+)
     */
    public void setRealmLevel(int realmLevel) {
        this.realmLevel = realmLevel;
    }

    /**
     * Checks if this mob was spawned from a BOSS pool in realm-mobs.yml.
     *
     * <p>When true, the mob scaling system uses {@link io.github.larsonix.trailoforbis.mobs.classification.RPGMobClass#BOSS}
     * directly instead of running entity discovery patterns. This prevents overworld
     * name-based patterns (e.g., "Golem_*") from incorrectly promoting regular-pool mobs.
     *
     * @return true if this mob was from the boss pool
     */
    public boolean isBoss() {
        return isBoss;
    }

    /**
     * Sets whether this mob was from a BOSS pool.
     *
     * @param boss true if the mob was spawned from a boss pool
     */
    public void setBoss(boolean boss) {
        this.isBoss = boss;
    }

    /**
     * Checks if this mob spawned as elite.
     *
     * <p>Elite is a spawn-time modifier independent of classification.
     * Both normal and boss mobs can be elite. Elite mobs receive:
     * <ul>
     *   <li>2.0x stat multiplier</li>
     *   <li>1.5x health multiplier</li>
     *   <li>3.0x XP multiplier</li>
     * </ul>
     *
     * @return true if this mob is elite
     */
    public boolean isElite() {
        return isElite;
    }

    /**
     * Sets whether this mob is elite.
     *
     * @param elite true if the mob should be marked as elite
     */
    public void setElite(boolean elite) {
        this.isElite = elite;
    }

    // ═══════════════════════════════════════════════════════════════════
    // CONVENIENCE METHODS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Checks if this mob belongs to a specific realm.
     *
     * @param otherRealmId The realm ID to check
     * @return true if this mob belongs to the specified realm
     */
    public boolean belongsToRealm(@Nonnull UUID otherRealmId) {
        return realmId != null && realmId.equals(otherRealmId);
    }

    /**
     * Checks if this mob is from the initial spawn wave.
     *
     * @return true if wave number is 0
     */
    public boolean isInitialSpawn() {
        return waveNumber == 0;
    }

    /**
     * Checks if any stat modifiers are applied.
     *
     * @return true if any multiplier differs from 1.0
     */
    public boolean hasModifiers() {
        return damageMultiplier != 1.0f ||
               healthMultiplier != 1.0f ||
               speedMultiplier != 1.0f ||
               attackSpeedMultiplier != 1.0f;
    }

    // ═══════════════════════════════════════════════════════════════════
    // COMPONENT INTERFACE
    // ═══════════════════════════════════════════════════════════════════

    @Override
    @Nonnull
    public Component<EntityStore> clone() {
        return new RealmMobComponent(this);
    }

    @Override
    public String toString() {
        return String.format(
            "RealmMobComponent{realm=%s, level=%d, wave=%d, boss=%b, elite=%b, counts=%b, dmg=%.1fx, hp=%.1fx, atkSpd=%.1fx}",
            realmId != null ? realmId.toString().substring(0, 8) : "null",
            realmLevel,
            waveNumber,
            isBoss,
            isElite,
            countsForCompletion,
            damageMultiplier,
            healthMultiplier,
            attackSpeedMultiplier
        );
    }
}
