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
 * ECS component marking a player as being inside a Realm instance.
 *
 * <p>Attached to players when they enter a realm to:
 * <ul>
 *   <li>Track which realm they're in</li>
 *   <li>Store entry time for timer calculations</li>
 *   <li>Track their personal statistics</li>
 *   <li>Apply realm-specific effects or restrictions</li>
 * </ul>
 *
 * <p>Used by:
 * <ul>
 *   <li>{@code RealmTeleportHandler} - Attaches on entry, removes on exit</li>
 *   <li>{@code RealmTimerSystem} - Calculates remaining time</li>
 *   <li>{@code RealmCompletedEvent} - Calculates rewards based on stats</li>
 * </ul>
 *
 * @see io.github.larsonix.trailoforbis.maps.instance.RealmTeleportHandler
 */
public class RealmPlayerComponent implements Component<EntityStore> {

    // ═══════════════════════════════════════════════════════════════════
    // CODEC
    // ═══════════════════════════════════════════════════════════════════

    public static final BuilderCodec<RealmPlayerComponent> CODEC = BuilderCodec.builder(
            RealmPlayerComponent.class, RealmPlayerComponent::new
        )
        .append(new KeyedCodec<>("RealmId", Codec.UUID_STRING),
                RealmPlayerComponent::setRealmId,
                c -> Objects.requireNonNullElse(c.getRealmId(), new UUID(0L, 0L))).add()
        .append(new KeyedCodec<>("EntryTimeMs", Codec.LONG),
                RealmPlayerComponent::setEntryTimeMs, RealmPlayerComponent::getEntryTimeMs).add()
        .append(new KeyedCodec<>("KillCount", Codec.INTEGER),
                (c, v) -> { for (int i = 0; i < v; i++) c.incrementKillCount(); },
                RealmPlayerComponent::getKillCount).add()
        .append(new KeyedCodec<>("DamageDealt", Codec.DOUBLE),
                (c, v) -> c.addDamageDealt(v), RealmPlayerComponent::getDamageDealt).add()
        .append(new KeyedCodec<>("DamageTaken", Codec.DOUBLE),
                (c, v) -> c.addDamageTaken(v), RealmPlayerComponent::getDamageTaken).add()
        .append(new KeyedCodec<>("DeathCount", Codec.INTEGER),
                (c, v) -> { for (int i = 0; i < v; i++) c.incrementDeathCount(); },
                RealmPlayerComponent::getDeathCount).add()
        .append(new KeyedCodec<>("HasCompleted", Codec.BOOLEAN),
                RealmPlayerComponent::setCompleted, RealmPlayerComponent::hasCompleted).add()
        .append(new KeyedCodec<>("CanRespawn", Codec.BOOLEAN),
                RealmPlayerComponent::setCanRespawn, RealmPlayerComponent::canRespawn).add()
        .append(new KeyedCodec<>("IsAlive", Codec.BOOLEAN),
                RealmPlayerComponent::setAlive, RealmPlayerComponent::isAlive).add()
        .build();

    // ═══════════════════════════════════════════════════════════════════
    // STATIC TYPE REFERENCE
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Static reference to the component type - set during plugin init.
     * Allows {@link #getComponentType()} to work in async contexts.
     */
    @Nullable
    public static ComponentType<EntityStore, RealmPlayerComponent> TYPE = null;

    // ═══════════════════════════════════════════════════════════════════
    // FIELDS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * The unique ID of the realm instance the player is in.
     */
    private UUID realmId;

    /**
     * System timestamp when the player entered the realm.
     */
    private long entryTimeMs;

    /**
     * Number of mobs killed by this player in the current realm.
     */
    private int killCount;

    /**
     * Total damage dealt by this player in the current realm.
     */
    private double damageDealt;

    /**
     * Total damage taken by this player in the current realm.
     */
    private double damageTaken;

    /**
     * Number of deaths in the current realm.
     */
    private int deathCount;

    /**
     * Whether the player has completed the realm (for rewards).
     */
    private boolean hasCompleted;

    /**
     * Whether the player can respawn in the realm.
     * Some modifiers may disable respawning.
     */
    private boolean canRespawn;

    /**
     * Whether the player is currently alive in the realm.
     * Used to track "all players dead" condition.
     */
    private boolean isAlive;

    /**
     * System timestamp when the defeat phase started (timer expired, showing defeat HUD).
     * Zero means not in defeat phase. Transient — not persisted via codec.
     */
    private long defeatPhaseStartMs;

    // ═══════════════════════════════════════════════════════════════════
    // CONSTRUCTORS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Default constructor - required for component registration.
     */
    public RealmPlayerComponent() {
        this.realmId = null;
        this.entryTimeMs = 0;
        this.killCount = 0;
        this.damageDealt = 0.0;
        this.damageTaken = 0.0;
        this.deathCount = 0;
        this.hasCompleted = false;
        this.canRespawn = true;
        this.isAlive = true;
        this.defeatPhaseStartMs = 0;
    }

    /**
     * Copy constructor - required for {@link #clone()}.
     */
    private RealmPlayerComponent(@Nonnull RealmPlayerComponent other) {
        this.realmId = other.realmId;
        this.entryTimeMs = other.entryTimeMs;
        this.killCount = other.killCount;
        this.damageDealt = other.damageDealt;
        this.damageTaken = other.damageTaken;
        this.deathCount = other.deathCount;
        this.hasCompleted = other.hasCompleted;
        this.canRespawn = other.canRespawn;
        this.isAlive = other.isAlive;
        this.defeatPhaseStartMs = other.defeatPhaseStartMs;
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
    public static ComponentType<EntityStore, RealmPlayerComponent> getComponentType() {
        if (TYPE != null) {
            return TYPE;
        }
        return TrailOfOrbis.getInstance().getRealmPlayerComponentType();
    }

    // ═══════════════════════════════════════════════════════════════════
    // INITIALIZATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Initializes the component when a player enters a realm.
     *
     * @param realmId The realm instance ID
     * @throws NullPointerException if realmId is null
     */
    public void initialize(@Nonnull UUID realmId) {
        java.util.Objects.requireNonNull(realmId, "realmId cannot be null");
        this.realmId = realmId;
        this.entryTimeMs = System.currentTimeMillis();
        this.killCount = 0;
        this.damageDealt = 0.0;
        this.damageTaken = 0.0;
        this.deathCount = 0;
        this.hasCompleted = false;
        this.canRespawn = true;
        this.isAlive = true;
        this.defeatPhaseStartMs = 0;
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

    public long getEntryTimeMs() {
        return entryTimeMs;
    }

    public void setEntryTimeMs(long entryTimeMs) {
        this.entryTimeMs = entryTimeMs;
    }

    public int getKillCount() {
        return killCount;
    }

    public void incrementKillCount() {
        this.killCount++;
    }

    public double getDamageDealt() {
        return damageDealt;
    }

    public void addDamageDealt(double damage) {
        this.damageDealt += damage;
    }

    public double getDamageTaken() {
        return damageTaken;
    }

    public void addDamageTaken(double damage) {
        this.damageTaken += damage;
    }

    public int getDeathCount() {
        return deathCount;
    }

    public void incrementDeathCount() {
        this.deathCount++;
    }

    public boolean hasCompleted() {
        return hasCompleted;
    }

    public void setCompleted(boolean completed) {
        this.hasCompleted = completed;
    }

    public boolean canRespawn() {
        return canRespawn;
    }

    public void setCanRespawn(boolean canRespawn) {
        this.canRespawn = canRespawn;
    }

    public boolean isAlive() {
        return isAlive;
    }

    public void setAlive(boolean alive) {
        this.isAlive = alive;
    }

    /**
     * Marks the player as dead (for all-players-dead tracking).
     * Call this when the player dies.
     */
    public void markDead() {
        this.isAlive = false;
    }

    /**
     * Marks the player as alive (for respawn scenarios).
     * Call this when the player respawns.
     */
    public void markAlive() {
        this.isAlive = true;
    }

    // ═══════════════════════════════════════════════════════════════════
    // DEFEAT PHASE
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Starts the defeat phase — timer expired, showing defeat HUD.
     * The player will be teleported out after the defeat phase duration.
     */
    public void startDefeatPhase() {
        this.defeatPhaseStartMs = System.currentTimeMillis();
    }

    /**
     * @return true if this player is in the defeat phase (timer expired, awaiting teleport)
     */
    public boolean isInDefeatPhase() {
        return defeatPhaseStartMs > 0;
    }

    /**
     * Gets how many seconds have elapsed since the defeat phase started.
     *
     * @return Elapsed seconds in defeat phase, or 0 if not in defeat phase
     */
    public int getDefeatPhaseElapsedSeconds() {
        if (defeatPhaseStartMs == 0) {
            return 0;
        }
        return (int) ((System.currentTimeMillis() - defeatPhaseStartMs) / 1000);
    }

    // ═══════════════════════════════════════════════════════════════════
    // CONVENIENCE METHODS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Checks if this player is in a specific realm.
     *
     * @param otherRealmId The realm ID to check
     * @return true if player is in the specified realm
     */
    public boolean isInRealm(@Nonnull UUID otherRealmId) {
        return realmId != null && realmId.equals(otherRealmId);
    }

    /**
     * Gets the time spent in the realm in seconds.
     *
     * @return Elapsed seconds since entry
     */
    public int getElapsedSeconds() {
        if (entryTimeMs == 0) {
            return 0;
        }
        return (int) ((System.currentTimeMillis() - entryTimeMs) / 1000);
    }

    /**
     * Gets the time spent in the realm in milliseconds.
     *
     * @return Elapsed milliseconds since entry
     */
    public long getElapsedMs() {
        if (entryTimeMs == 0) {
            return 0;
        }
        return System.currentTimeMillis() - entryTimeMs;
    }

    /**
     * Calculates a performance score based on kills, damage, and deaths.
     *
     * <p>Higher score = better performance. Deaths reduce the score.
     *
     * @return Performance score (0-100 scale typical)
     */
    public int calculatePerformanceScore() {
        // Base score from kills and damage
        double score = killCount * 10 + damageDealt / 100.0;

        // Reduce by deaths
        score -= deathCount * 15;

        // Bonus for no deaths
        if (deathCount == 0 && killCount > 0) {
            score *= 1.25;
        }

        return Math.max(0, (int) score);
    }

    /**
     * Checks if this player is currently in a realm.
     *
     * @return true if realmId is set
     */
    public boolean isInRealm() {
        return realmId != null;
    }

    // ═══════════════════════════════════════════════════════════════════
    // COMPONENT INTERFACE
    // ═══════════════════════════════════════════════════════════════════

    @Override
    @Nonnull
    public Component<EntityStore> clone() {
        return new RealmPlayerComponent(this);
    }

    @Override
    public String toString() {
        return String.format(
            "RealmPlayerComponent{realm=%s, kills=%d, dmg=%.0f, deaths=%d, time=%ds, alive=%s}",
            realmId != null ? realmId.toString().substring(0, 8) : "null",
            killCount,
            damageDealt,
            deathCount,
            getElapsedSeconds(),
            isAlive
        );
    }
}
