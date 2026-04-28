package io.github.larsonix.trailoforbis.maps.spawning;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.maps.spawning.WeightedMob.MobClassification;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;

/**
 * Persistent state for a mob spawned in a realm instance.
 *
 * <p>This class tracks the full lifecycle of a realm mob, including:
 * <ul>
 *   <li>Original spawn position for respawning</li>
 *   <li>Mob type and classification for recreation</li>
 *   <li>Current alive/dead status</li>
 *   <li>Transient entity reference (may become invalid on despawn)</li>
 * </ul>
 *
 * <h2>Despawn Recovery</h2>
 * <p>When the entity ref becomes invalid but the mob should still be alive,
 * the spawner can use the stored metadata to respawn the mob at its original
 * position. This prevents mob "loss" due to chunk unloading, engine cleanup,
 * or other factors that might despawn entities.
 *
 * <h2>Thread Safety</h2>
 * <p>This class is designed to be accessed from multiple threads. The entityRef
 * is volatile for visibility, but operations should be coordinated at the
 * SpawnState level.
 *
 * @see RealmMobSpawner
 * @see RealmMobSpawner.SpawnState
 */
public class RealmMobState {

    // ═══════════════════════════════════════════════════════════════════
    // IDENTITY
    // ═══════════════════════════════════════════════════════════════════

    /** Unique identifier for this mob (generated at spawn time). */
    private final UUID mobId;

    /** The NPC type identifier (e.g., "trork_warrior"). */
    private final String mobTypeId;

    /** The mob classification (NORMAL, ELITE, BOSS). */
    private final MobClassification classification;

    // ═══════════════════════════════════════════════════════════════════
    // SPAWN DATA
    // ═══════════════════════════════════════════════════════════════════

    /** The original spawn position (used for respawning). */
    private final Vector3d spawnPosition;

    /** The wave number this mob spawned in (0 = initial wave). */
    private final int waveNumber;

    /** Whether this was a reinforcement spawn. */
    private final boolean isReinforcement;

    /** Whether this mob is elite (spawn-time modifier). */
    private final boolean isElite;

    // ═══════════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════════

    /** Whether this mob should be alive (not killed by players). */
    private volatile boolean isAlive;

    /** Current entity reference (null if despawned/dead, or not yet spawned). */
    private volatile Ref<EntityStore> entityRef;

    /** Number of times this mob has been respawned after despawning. */
    private volatile int respawnCount;

    // ═══════════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Creates a new mob state.
     *
     * @param mobId The unique mob identifier
     * @param mobTypeId The NPC type identifier
     * @param classification The mob classification
     * @param spawnPosition The spawn position
     * @param waveNumber The wave number
     * @param isReinforcement Whether this is a reinforcement spawn
     * @param isElite Whether this mob is elite (spawn-time modifier)
     */
    public RealmMobState(
            @Nonnull UUID mobId,
            @Nonnull String mobTypeId,
            @Nonnull MobClassification classification,
            @Nonnull Vector3d spawnPosition,
            int waveNumber,
            boolean isReinforcement,
            boolean isElite) {
        this.mobId = Objects.requireNonNull(mobId, "mobId cannot be null");
        this.mobTypeId = Objects.requireNonNull(mobTypeId, "mobTypeId cannot be null");
        this.classification = Objects.requireNonNull(classification, "classification cannot be null");
        this.spawnPosition = Objects.requireNonNull(spawnPosition, "spawnPosition cannot be null");
        this.waveNumber = waveNumber;
        this.isReinforcement = isReinforcement;
        this.isElite = isElite;
        this.isAlive = true;
        this.respawnCount = 0;
    }

    // ═══════════════════════════════════════════════════════════════════
    // FACTORY METHODS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Creates a mob state for an initial spawn.
     *
     * @param mobTypeId The NPC type identifier
     * @param classification The mob classification
     * @param spawnPosition The spawn position
     * @param isElite Whether this mob is elite
     * @return New mob state
     */
    @Nonnull
    public static RealmMobState forInitialSpawn(
            @Nonnull String mobTypeId,
            @Nonnull MobClassification classification,
            @Nonnull Vector3d spawnPosition,
            boolean isElite) {
        return new RealmMobState(
            UUID.randomUUID(),
            mobTypeId,
            classification,
            spawnPosition,
            0,
            false,
            isElite
        );
    }

    /**
     * Creates a mob state for a reinforcement spawn.
     *
     * @param mobTypeId The NPC type identifier
     * @param classification The mob classification
     * @param spawnPosition The spawn position
     * @param waveNumber The wave number
     * @param isElite Whether this mob is elite
     * @return New mob state
     */
    @Nonnull
    public static RealmMobState forReinforcement(
            @Nonnull String mobTypeId,
            @Nonnull MobClassification classification,
            @Nonnull Vector3d spawnPosition,
            int waveNumber,
            boolean isElite) {
        return new RealmMobState(
            UUID.randomUUID(),
            mobTypeId,
            classification,
            spawnPosition,
            waveNumber,
            true,
            isElite
        );
    }

    // ═══════════════════════════════════════════════════════════════════
    // ENTITY REFERENCE MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Sets the entity reference after spawning.
     *
     * @param entityRef The entity reference
     */
    public void setEntityRef(@Nullable Ref<EntityStore> entityRef) {
        this.entityRef = entityRef;
    }

    /**
     * Gets the current entity reference.
     *
     * @return The entity reference, or null if not spawned/despawned
     */
    @Nullable
    public Ref<EntityStore> getEntityRef() {
        return entityRef;
    }

    /**
     * Checks if the entity reference is currently valid.
     *
     * <p>Returns false if:
     * <ul>
     *   <li>entityRef is null (never spawned or cleared)</li>
     *   <li>entityRef.isValid() returns false (entity despawned)</li>
     * </ul>
     *
     * @return true if the entity exists and is valid
     */
    public boolean hasValidEntityRef() {
        Ref<EntityStore> ref = entityRef;
        return ref != null && ref.isValid();
    }

    /**
     * Checks if this mob needs respawning.
     *
     * <p>A mob needs respawning if:
     * <ul>
     *   <li>It should be alive (not killed)</li>
     *   <li>Its entity ref is null or invalid (despawned)</li>
     * </ul>
     *
     * @return true if the mob should be respawned
     */
    public boolean needsRespawn() {
        return isAlive && !hasValidEntityRef();
    }

    // ═══════════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Marks this mob as killed (died via combat).
     *
     * <p>Once killed, the mob will not be respawned.
     */
    public void markKilled() {
        this.isAlive = false;
        this.entityRef = null;
    }

    /**
     * Records that this mob was respawned.
     *
     * @param newEntityRef The new entity reference after respawning
     */
    public void recordRespawn(@Nullable Ref<EntityStore> newEntityRef) {
        this.entityRef = newEntityRef;
        this.respawnCount++;
    }

    /**
     * Checks if this mob is alive (not killed by players).
     *
     * @return true if alive
     */
    public boolean isAlive() {
        return isAlive;
    }

    /**
     * Gets the number of times this mob has been respawned.
     *
     * @return Respawn count
     */
    public int getRespawnCount() {
        return respawnCount;
    }

    // ═══════════════════════════════════════════════════════════════════
    // GETTERS
    // ═══════════════════════════════════════════════════════════════════

    /** @return The unique mob identifier */
    @Nonnull
    public UUID getMobId() {
        return mobId;
    }

    /** @return The NPC type identifier */
    @Nonnull
    public String getMobTypeId() {
        return mobTypeId;
    }

    /** @return The mob classification */
    @Nonnull
    public MobClassification getClassification() {
        return classification;
    }

    /** @return The original spawn position */
    @Nonnull
    public Vector3d getSpawnPosition() {
        return spawnPosition;
    }

    /** @return The wave number (0 = initial wave) */
    public int getWaveNumber() {
        return waveNumber;
    }

    /** @return Whether this was a reinforcement spawn */
    public boolean isReinforcement() {
        return isReinforcement;
    }

    /** @return Whether this mob is elite (spawn-time modifier) */
    public boolean isElite() {
        return isElite;
    }

    // ═══════════════════════════════════════════════════════════════════
    // UTILITY
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RealmMobState that = (RealmMobState) o;
        return mobId.equals(that.mobId);
    }

    @Override
    public int hashCode() {
        return mobId.hashCode();
    }

    @Override
    public String toString() {
        return String.format("RealmMobState[id=%s, type=%s, class=%s, elite=%b, alive=%b, hasRef=%b, respawns=%d]",
            mobId.toString().substring(0, 8),
            mobTypeId,
            classification,
            isElite,
            isAlive,
            hasValidEntityRef(),
            respawnCount);
    }
}
