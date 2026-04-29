package io.github.larsonix.trailoforbis.maps.templates;

import com.hypixel.hytale.builtin.instances.InstancesPlugin;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import io.github.larsonix.trailoforbis.maps.config.RealmsConfig;
import io.github.larsonix.trailoforbis.maps.core.RealmBiomeType;
import io.github.larsonix.trailoforbis.maps.core.RealmLayoutSize;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for realm templates that maps biome/size combinations to instance templates.
 *
 * <p>This class manages the mapping between {@link RealmBiomeType}/{@link RealmLayoutSize}
 * combinations and the actual instance template assets. It validates template existence
 * at load time and provides caching for template metadata.
 *
 * <p>Usage:
 * <pre>{@code
 * RealmTemplateRegistry registry = new RealmTemplateRegistry();
 * registry.loadFromConfig(config);
 *
 * String templateName = registry.getTemplateName(RealmBiomeType.FOREST, RealmLayoutSize.MEDIUM);
 * RealmTemplate template = registry.getTemplate(templateName);
 * }</pre>
 *
 * @see RealmTemplate
 * @see RealmBiomeType
 * @see RealmLayoutSize
 */
public class RealmTemplateRegistry {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /**
     * Default spawn position for templates without explicit configuration.
     */
    private static final Vector3d DEFAULT_SPAWN_POSITION = new Vector3d(0, 64, 0);

    /**
     * Default rotation for spawn positions.
     */
    private static final Vector3f DEFAULT_SPAWN_ROTATION = new Vector3f(0, 0, 0);

    /**
     * Maps biome+size key to template name.
     */
    private final Map<String, String> templateNameMap = new ConcurrentHashMap<>();

    /**
     * Caches loaded template metadata.
     */
    private final Map<String, RealmTemplate> templateCache = new ConcurrentHashMap<>();

    /**
     * Set of templates that have been validated as existing.
     */
    private final Set<String> validatedTemplates = ConcurrentHashMap.newKeySet();

    /**
     * Configuration reference for template overrides.
     */
    @Nullable
    private RealmsConfig config;

    /**
     * Loads template mappings from configuration.
     *
     * <p>This will:
     * <ol>
     *   <li>Register all standard biome/size template names</li>
     *   <li>Apply any overrides from configuration</li>
     *   <li>Validate that templates exist in the asset system</li>
     * </ol>
     *
     * @param config The realm configuration
     */
    public void loadFromConfig(@Nonnull RealmsConfig config) {
        this.config = config;

        // Clear existing mappings
        templateNameMap.clear();
        templateCache.clear();
        validatedTemplates.clear();

        // Register tier-based templates for combat biomes,
        // and the old-style template for utility biomes (Skill Sanctum).
        for (RealmBiomeType biome : RealmBiomeType.values()) {
            if (biome.isUtilityBiome()) {
                // Utility biomes use fixed template name (lowercase, no tier)
                String templateName = biome.getTemplatePrefix().toLowerCase();
                String key = createKey(biome, RealmLayoutSize.MEDIUM);
                templateNameMap.put(key, templateName);
            } else {
                // Combat biomes: register all radius tiers
                for (int tier : RealmBiomeType.RADIUS_TIERS) {
                    String templateName = biome.getTemplateNameForRadius(tier);
                    // Use tier as the key suffix (not size enum)
                    templateNameMap.put(biome.name() + "_R" + tier, templateName);
                }
            }
        }

        // Apply config overrides if present
        if (config.templateOverrides() != null) {
            for (Map.Entry<String, String> override : config.templateOverrides().entrySet()) {
                templateNameMap.put(override.getKey(), override.getValue());
            }
        }

        LOGGER.atInfo().log("Loaded %d realm template mappings", templateNameMap.size());

        // Validate templates exist
        validateTemplates();
    }

    /**
     * Validates that all registered templates exist in the instance asset system.
     */
    private void validateTemplates() {
        int valid = 0;
        int missing = 0;

        for (Map.Entry<String, String> entry : templateNameMap.entrySet()) {
            String templateName = entry.getValue();
            if (doesTemplateExist(templateName)) {
                validatedTemplates.add(templateName);
                valid++;
            } else {
                LOGGER.atWarning().log("Template '%s' for key '%s' not found in assets",
                    templateName, entry.getKey());
                missing++;
            }
        }

        LOGGER.atInfo().log("Template validation complete: %d valid, %d missing", valid, missing);
    }

    /**
     * Gets the template name for a biome/size combination.
     *
     * @param biome The biome type
     * @param size The layout size
     * @return The template asset name
     * @throws IllegalArgumentException if no mapping exists
     */
    @Nonnull
    public String getTemplateName(@Nonnull RealmBiomeType biome, @Nonnull RealmLayoutSize size) {
        String key = createKey(biome, size);
        String templateName = templateNameMap.get(key);

        if (templateName == null) {
            // Fall back to standard naming
            templateName = biome.getTemplateName(size);
        }

        return templateName;
    }

    /**
     * Checks if a template exists in the instance asset system.
     *
     * @param templateName The template name to check
     * @return true if the template exists
     */
    public boolean doesTemplateExist(@Nonnull String templateName) {
        // First check cache
        if (validatedTemplates.contains(templateName)) {
            return true;
        }

        // Check with InstancesPlugin
        boolean exists = InstancesPlugin.doesInstanceAssetExist(templateName);
        if (exists) {
            validatedTemplates.add(templateName);
        }
        return exists;
    }

    /**
     * Checks if a template exists for the given biome/size combination.
     *
     * @param biome The biome type
     * @param size The layout size
     * @return true if a valid template exists
     */
    public boolean hasTemplate(@Nonnull RealmBiomeType biome, @Nonnull RealmLayoutSize size) {
        String templateName = getTemplateName(biome, size);
        return doesTemplateExist(templateName);
    }

    /**
     * Gets template metadata for a template name.
     *
     * <p>Template metadata is lazily loaded and cached.
     *
     * @param templateName The template name
     * @return The template metadata, or null if not found
     */
    @Nullable
    public RealmTemplate getTemplate(@Nonnull String templateName) {
        return templateCache.computeIfAbsent(templateName, this::loadTemplateMetadata);
    }

    /**
     * Gets template metadata for a biome/size combination.
     *
     * @param biome The biome type
     * @param size The layout size
     * @return The template metadata, or null if not found
     */
    @Nullable
    public RealmTemplate getTemplate(@Nonnull RealmBiomeType biome, @Nonnull RealmLayoutSize size) {
        String templateName = getTemplateName(biome, size);
        return getTemplate(templateName);
    }

    /**
     * Gets or creates template metadata for a biome/size combination.
     *
     * <p>If the template doesn't exist, creates a default template configuration.
     *
     * @param biome The biome type
     * @param size The layout size
     * @return The template metadata (never null)
     */
    @Nonnull
    public RealmTemplate getOrCreateTemplate(@Nonnull RealmBiomeType biome, @Nonnull RealmLayoutSize size) {
        RealmTemplate template = getTemplate(biome, size);
        if (template != null) {
            return template;
        }

        // Create a default template
        String templateName = getTemplateName(biome, size);
        Transform defaultSpawn = new Transform(DEFAULT_SPAWN_POSITION, DEFAULT_SPAWN_ROTATION);

        return RealmTemplate.builder(templateName, biome, size)
            .spawnLocation(defaultSpawn)
            .estimatedMonsters(size.calculateMonsterCount(1))
            .build();
    }

    /**
     * Gets or creates template metadata using an explicit template name (for radius tier system).
     *
     * @param templateName The tier-based template name (e.g., "realm_forest_r25")
     * @param biome The biome type (for metadata)
     * @param size The layout size (for metadata)
     * @return The template metadata (never null)
     */
    @Nonnull
    public RealmTemplate getOrCreateTemplateByName(
            @Nonnull String templateName,
            @Nonnull RealmBiomeType biome,
            @Nonnull RealmLayoutSize size) {
        RealmTemplate cached = templateCache.get(templateName);
        if (cached != null) {
            return cached;
        }

        // Generate spawn points (same as getOrCreateTemplate) — without these, mobs don't spawn.
        List<MonsterSpawnPoint> spawnPoints = generateSpawnPointsForSize(size);
        Transform defaultSpawn = new Transform(DEFAULT_SPAWN_POSITION, DEFAULT_SPAWN_ROTATION);

        RealmTemplate template = RealmTemplate.builder(templateName, biome, size)
            .spawnLocation(defaultSpawn)
            .spawnPoints(spawnPoints)
            .estimatedMonsters(size.calculateMonsterCount(1))
            .build();
        templateCache.put(templateName, template);
        return template;
    }

    /**
     * Loads template metadata from configuration or creates defaults.
     *
     * @param templateName The template name
     * @return The loaded template, or null if invalid
     */
    @Nullable
    private RealmTemplate loadTemplateMetadata(@Nonnull String templateName) {
        // Parse biome from template name (e.g., "realm_forest" or "realm_forest_medium")
        RealmBiomeType biome = parseBiomeFromTemplate(templateName);
        if (biome == null) {
            LOGGER.atWarning().log("Could not parse biome from template name: %s", templateName);
            return null;
        }

        // Parse size from template name, or default to MEDIUM if not specified
        // (biome-specific templates like "realm_forest" don't include size)
        RealmLayoutSize size = parseSizeFromTemplate(templateName);
        if (size == null) {
            size = RealmLayoutSize.MEDIUM;
        }

        // Check if template exists
        if (!doesTemplateExist(templateName)) {
            LOGGER.atWarning().log("Template does not exist: %s", templateName);
            return null;
        }

        // Load spawn points and locations from config if available
        Transform playerSpawn = getConfiguredSpawnLocation(templateName, biome, size);
        Transform exitPortal = getConfiguredExitLocation(templateName, biome, size);
        List<MonsterSpawnPoint> spawnPoints = getConfiguredSpawnPoints(templateName, biome, size);

        return new RealmTemplate(
            templateName,
            biome,
            size,
            size.calculateMonsterCount(1),
            spawnPoints,
            playerSpawn,
            exitPortal
        );
    }

    /**
     * Gets configured spawn location or generates a default.
     */
    @Nonnull
    private Transform getConfiguredSpawnLocation(String templateName, RealmBiomeType biome, RealmLayoutSize size) {
        // TODO: Load from realm-templates.yml if configured
        // Use biome-appropriate base Y: Mountains is elevated (Y=74), others at Y=64
        double spawnY = getBaseYForBiome(biome);
        return new Transform(
            new Vector3d(0, spawnY, 0),
            DEFAULT_SPAWN_ROTATION
        );
    }

    /**
     * Gets configured exit portal location or generates a default.
     */
    @Nonnull
    private Transform getConfiguredExitLocation(String templateName, RealmBiomeType biome, RealmLayoutSize size) {
        // TODO: Load from realm-templates.yml if configured
        double spawnY = getBaseYForBiome(biome);
        return new Transform(
            new Vector3d(5, spawnY, 0),
            DEFAULT_SPAWN_ROTATION
        );
    }

    /**
     * Gets configured spawn points or generates dynamic spawn points based on arena size.
     *
     * <p>Spawn points are distributed across the arena:
     * <ul>
     *   <li>Normal spawn points: Spread throughout the arena (70% of points)</li>
     *   <li>Elite spawn points: Scattered around (20% of points)</li>
     *   <li>Boss spawn points: Placed at strategic locations (10% of points)</li>
     * </ul>
     */
    @Nonnull
    private List<MonsterSpawnPoint> getConfiguredSpawnPoints(String templateName, RealmBiomeType biome, RealmLayoutSize size) {
        // TODO: Load from realm-templates.yml if configured
        // For now, generate spawn points dynamically based on arena size
        return generateSpawnPointsForSize(size);
    }

    /**
     * Generates spawn points dynamically based on arena size.
     *
     * <p>The number and distribution of spawn points scales with arena size:
     * <ul>
     *   <li>SMALL: ~10 spawn points (radius 25)</li>
     *   <li>MEDIUM: ~20 spawn points (radius 49)</li>
     *   <li>LARGE: ~35 spawn points (radius 100)</li>
     *   <li>MASSIVE: ~50 spawn points (radius 200)</li>
     * </ul>
     *
     * <p>Each size has its own biome/world structure with matching arena dimensions.
     *
     * @param size The arena size
     * @return List of generated spawn points
     */
    @Nonnull
    private List<MonsterSpawnPoint> generateSpawnPointsForSize(@Nonnull RealmLayoutSize size) {
        List<MonsterSpawnPoint> points = new ArrayList<>();

        // Arena radius comes from RealmLayoutSize and matches the biome definition.
        // Each size has its own biome asset with matching inner_radius in the CurveMapper.
        // Use 90% of the radius to provide a safety margin from walls.
        // Spawn points cover the maximum possible arena (126 blocks at 500 mobs).
        // The mob spawner clamps positions to the actual dynamic radius at runtime
        // via safeRadius = computeArenaRadius() * 0.85.
        float arenaRadius = 126.0f * 0.9f;

        int guaranteedBosses = size.getGuaranteedBosses();

        // Calculate spawn point counts based on size
        int totalPoints = switch (size) {
            case SMALL -> 10;
            case MEDIUM -> 20;
            case LARGE -> 35;
            case MASSIVE -> 50;
        };

        // Distribution: 70% normal, 20% elite, 10% boss (at least guaranteedBosses)
        int bossPoints = Math.max(guaranteedBosses, (int) (totalPoints * 0.10));
        int elitePoints = (int) (totalPoints * 0.20);
        int normalPoints = totalPoints - bossPoints - elitePoints;

        // Base Y coordinate for spawns — used as initial hint, terrain scanning in
        // RealmMobSpawner will override with actual ground height at each position.
        double baseY = 64.5;

        // Generate normal spawn points in a ring pattern
        for (int i = 0; i < normalPoints; i++) {
            double angle = (2 * Math.PI * i) / normalPoints;
            // Distribute from 30% to 90% of arena radius
            double distance = arenaRadius * (0.3 + (0.6 * (i % 3) / 2.0));
            double x = Math.cos(angle) * distance;
            double z = Math.sin(angle) * distance;

            points.add(MonsterSpawnPoint.normal(
                new Vector3d(x, baseY, z),
                3 + (i % 3) // Max count 3-5 mobs per point
            ));
        }

        // Generate elite spawn points at mid-range
        for (int i = 0; i < elitePoints; i++) {
            double angle = (2 * Math.PI * i) / elitePoints + Math.PI / elitePoints; // Offset from normal
            double distance = arenaRadius * 0.6; // 60% of radius
            double x = Math.cos(angle) * distance;
            double z = Math.sin(angle) * distance;

            points.add(MonsterSpawnPoint.elite(new Vector3d(x, baseY, z)));
        }

        // Generate boss spawn points at strategic locations
        for (int i = 0; i < bossPoints; i++) {
            double angle = (2 * Math.PI * i) / bossPoints;
            // Bosses spawn at 70-80% of radius (challenging positions)
            double distance = arenaRadius * (0.7 + (0.1 * (i % 2)));
            double x = Math.cos(angle) * distance;
            double z = Math.sin(angle) * distance;

            points.add(MonsterSpawnPoint.boss(new Vector3d(x, baseY, z)));
        }

        LOGGER.atFine().log("Generated %d spawn points for %s arena (radius=%.1f): %d normal, %d elite, %d boss",
            points.size(), size.name(), arenaRadius, normalPoints, elitePoints, bossPoints);

        return points;
    }

    /**
     * Returns the base Y coordinate for a biome's arena floor.
     *
     * <p>Most biomes use Y=64 (standard floor). Mountains was Y=74 (elevated plateau)
     * but is now a ceiling biome (floor at Y=64, ceiling at Y=95). Using the default
     * ensures mob scan maxY (64+30=94) stays below the ceiling — otherwise mobs would
     * spawn on top of the mine roof.
     */
    public static double getBaseYForBiome(@Nonnull RealmBiomeType biome) {
        return 64;
    }

    /**
     * Parses biome type from a template name.
     */
    @Nullable
    private RealmBiomeType parseBiomeFromTemplate(String templateName) {
        String lower = templateName.toLowerCase();
        for (RealmBiomeType biome : RealmBiomeType.values()) {
            if (lower.contains(biome.name().toLowerCase())) {
                return biome;
            }
        }
        return null;
    }

    /**
     * Parses layout size from a template name.
     */
    @Nullable
    private RealmLayoutSize parseSizeFromTemplate(String templateName) {
        String lower = templateName.toLowerCase();
        for (RealmLayoutSize size : RealmLayoutSize.values()) {
            if (lower.contains(size.name().toLowerCase())) {
                return size;
            }
        }
        return null;
    }

    /**
     * Creates a key for biome/size lookup.
     */
    private String createKey(RealmBiomeType biome, RealmLayoutSize size) {
        return biome.name() + "_" + size.name();
    }

    /**
     * Gets all registered template names.
     *
     * @return Unmodifiable collection of template names
     */
    @Nonnull
    public Collection<String> getAllTemplateNames() {
        return Collections.unmodifiableCollection(templateNameMap.values());
    }

    /**
     * Gets all validated template names (templates that exist in assets).
     *
     * @return Unmodifiable set of validated template names
     */
    @Nonnull
    public Set<String> getValidatedTemplates() {
        return Collections.unmodifiableSet(validatedTemplates);
    }

    /**
     * Gets the count of valid templates.
     *
     * @return Number of validated templates
     */
    public int getValidTemplateCount() {
        return validatedTemplates.size();
    }

    /**
     * Gets available biome/size combinations that have valid templates.
     *
     * @return List of available combinations
     */
    @Nonnull
    public List<BiomeSizePair> getAvailableCombinations() {
        List<BiomeSizePair> available = new ArrayList<>();

        for (RealmBiomeType biome : RealmBiomeType.values()) {
            for (RealmLayoutSize size : RealmLayoutSize.values()) {
                if (hasTemplate(biome, size)) {
                    available.add(new BiomeSizePair(biome, size));
                }
            }
        }

        return available;
    }

    /**
     * Selects a random valid biome/size combination.
     *
     * @param random The random source
     * @return A random valid combination, or null if none available
     */
    @Nullable
    public BiomeSizePair selectRandomCombination(@Nonnull Random random) {
        List<BiomeSizePair> available = getAvailableCombinations();
        if (available.isEmpty()) {
            return null;
        }
        return available.get(random.nextInt(available.size()));
    }

    /**
     * Clears all cached data.
     */
    public void clear() {
        templateNameMap.clear();
        templateCache.clear();
        validatedTemplates.clear();
        config = null;
    }

    /**
     * A pair of biome and size for iteration.
     */
    public record BiomeSizePair(RealmBiomeType biome, RealmLayoutSize size) {
        public String getTemplateName() {
            return biome.getTemplateName(size);
        }
    }
}
