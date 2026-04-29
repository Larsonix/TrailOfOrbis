package io.github.larsonix.trailoforbis.maps.templates;

import com.hypixel.hytale.math.vector.Transform;
import io.github.larsonix.trailoforbis.maps.core.RealmBiomeType;
import io.github.larsonix.trailoforbis.maps.core.RealmLayoutSize;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents a realm instance template with all metadata needed for instantiation.
 *
 * <p>Templates define the static structure of a realm including:
 * <ul>
 *   <li>The asset name for instance spawning</li>
 *   <li>Biome and size classification</li>
 *   <li>Pre-defined spawn points for monsters</li>
 *   <li>Player spawn and exit portal locations</li>
 * </ul>
 *
 * <p>Templates are loaded from configuration and validated at startup.
 *
 * @param templateName The asset name used by InstancesPlugin.spawnInstance()
 * @param biome The biome type this template represents
 * @param size The layout size this template represents
 * @param estimatedMonsters Base estimate of monster count for this template
 * @param spawnPoints Pre-defined spawn points in this template
 * @param playerSpawn The location where players spawn when entering
 * @param exitPortalLocation The location where the exit portal is placed
 */
public record RealmTemplate(
    @Nonnull String templateName,
    @Nonnull RealmBiomeType biome,
    @Nonnull RealmLayoutSize size,
    int estimatedMonsters,
    @Nonnull List<MonsterSpawnPoint> spawnPoints,
    @Nonnull Transform playerSpawn,
    @Nonnull Transform exitPortalLocation
) {

    // === VALIDATION ===

    /**
     * Compact constructor with validation.
     */
    public RealmTemplate {
        Objects.requireNonNull(templateName, "Template name cannot be null");
        Objects.requireNonNull(biome, "Biome cannot be null");
        Objects.requireNonNull(size, "Size cannot be null");
        Objects.requireNonNull(spawnPoints, "Spawn points cannot be null");
        Objects.requireNonNull(playerSpawn, "Player spawn cannot be null");
        Objects.requireNonNull(exitPortalLocation, "Exit portal location cannot be null");

        if (templateName.isBlank()) {
            throw new IllegalArgumentException("Template name cannot be blank");
        }

        if (estimatedMonsters < 0) {
            estimatedMonsters = 0;
        }

        // Make spawn points immutable
        spawnPoints = List.copyOf(spawnPoints);
    }

    // === FACTORY METHODS ===

    /**
     * Creates a template with default spawn configuration.
     *
     * @param templateName The asset name
     * @param biome The biome type
     * @param size The layout size
     * @param playerSpawn The player spawn location
     * @param exitPortal The exit portal location
     * @return A new RealmTemplate
     */
    public static RealmTemplate create(
            String templateName,
            RealmBiomeType biome,
            RealmLayoutSize size,
            Transform playerSpawn,
            Transform exitPortal) {
        return new RealmTemplate(
            templateName,
            biome,
            size,
            size.calculateMonsterCount(1), // Base estimate at level 1
            Collections.emptyList(),
            playerSpawn,
            exitPortal
        );
    }

    /**
     * Creates a builder for constructing templates.
     *
     * @param templateName The asset name (required)
     * @param biome The biome type (required)
     * @param size The layout size (required)
     * @return A new Builder instance
     */
    public static Builder builder(String templateName, RealmBiomeType biome, RealmLayoutSize size) {
        return new Builder(templateName, biome, size);
    }

    // === COMPUTED PROPERTIES ===

    /**
     * Gets the number of boss spawn points in this template.
     *
     * @return Count of boss spawn points
     */
    public int getBossSpawnCount() {
        return (int) spawnPoints.stream()
            .filter(MonsterSpawnPoint::isBossSpawn)
            .count();
    }

    /**
     * Gets the number of elite spawn points in this template.
     *
     * @return Count of elite spawn points
     */
    public int getEliteSpawnCount() {
        return (int) spawnPoints.stream()
            .filter(MonsterSpawnPoint::isEliteSpawn)
            .count();
    }

    /**
     * Gets the total spawn capacity across all spawn points.
     *
     * @return Total of all maxCount values
     */
    public int getTotalSpawnCapacity() {
        return spawnPoints.stream()
            .mapToInt(MonsterSpawnPoint::maxCount)
            .sum();
    }

    /**
     * Gets spawn points filtered by type.
     *
     * @param type The spawn type to filter by
     * @return List of spawn points matching the type
     */
    public List<MonsterSpawnPoint> getSpawnPointsByType(MonsterSpawnPoint.SpawnType type) {
        return spawnPoints.stream()
            .filter(sp -> sp.type() == type)
            .toList();
    }

    /**
     * @return Whether this template has pre-defined spawn points
     */
    public boolean hasSpawnPoints() {
        return !spawnPoints.isEmpty();
    }

    /**
     * Gets the standard template name for a biome/size combination.
     *
     * @param biome The biome type
     * @param size The layout size
     * @return Standard template name (e.g., "realm_forest_medium")
     */
    public static String getStandardTemplateName(RealmBiomeType biome, RealmLayoutSize size) {
        return biome.getTemplateName(size);
    }

    // === BUILDER ===

    /**
     * Builder for constructing RealmTemplate instances.
     */
    public static final class Builder {
        private final String templateName;
        private final RealmBiomeType biome;
        private final RealmLayoutSize size;
        private int estimatedMonsters;
        private List<MonsterSpawnPoint> spawnPoints = Collections.emptyList();
        private Transform playerSpawn;
        private Transform exitPortalLocation;

        private Builder(String templateName, RealmBiomeType biome, RealmLayoutSize size) {
            this.templateName = templateName;
            this.biome = biome;
            this.size = size;
            this.estimatedMonsters = size.calculateMonsterCount(1);
        }

        public Builder estimatedMonsters(int estimatedMonsters) {
            this.estimatedMonsters = estimatedMonsters;
            return this;
        }

        public Builder spawnPoints(List<MonsterSpawnPoint> spawnPoints) {
            this.spawnPoints = spawnPoints;
            return this;
        }

        public Builder playerSpawn(Transform playerSpawn) {
            this.playerSpawn = playerSpawn;
            return this;
        }

        public Builder exitPortalLocation(Transform exitPortalLocation) {
            this.exitPortalLocation = exitPortalLocation;
            return this;
        }

        /**
         * Sets both player spawn and exit portal to the same location.
         *
         * @param location The spawn/portal location
         * @return This builder
         */
        public Builder spawnLocation(Transform location) {
            this.playerSpawn = location;
            this.exitPortalLocation = location;
            return this;
        }

        public RealmTemplate build() {
            if (playerSpawn == null) {
                throw new IllegalStateException("Player spawn location must be set");
            }
            if (exitPortalLocation == null) {
                throw new IllegalStateException("Exit portal location must be set");
            }
            return new RealmTemplate(
                templateName,
                biome,
                size,
                estimatedMonsters,
                spawnPoints,
                playerSpawn,
                exitPortalLocation
            );
        }
    }

    // === COPY METHODS ===

    /**
     * Creates a copy with different spawn points.
     *
     * @param newSpawnPoints The new spawn points
     * @return A new RealmTemplate with updated spawn points
     */
    public RealmTemplate withSpawnPoints(List<MonsterSpawnPoint> newSpawnPoints) {
        return new RealmTemplate(
            templateName,
            biome,
            size,
            estimatedMonsters,
            newSpawnPoints,
            playerSpawn,
            exitPortalLocation
        );
    }

    /**
     * Creates a copy with a different estimated monster count.
     *
     * @param newEstimate The new estimate
     * @return A new RealmTemplate with updated estimate
     */
    public RealmTemplate withEstimatedMonsters(int newEstimate) {
        return new RealmTemplate(
            templateName,
            biome,
            size,
            newEstimate,
            spawnPoints,
            playerSpawn,
            exitPortalLocation
        );
    }
}
