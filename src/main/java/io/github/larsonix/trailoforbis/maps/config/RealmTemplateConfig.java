package io.github.larsonix.trailoforbis.maps.config;

import com.hypixel.hytale.logger.HytaleLogger;
import io.github.larsonix.trailoforbis.maps.core.RealmBiomeType;
import org.yaml.snakeyaml.Yaml;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

/**
 * Configuration for realm template spawn/exit locations per biome.
 *
 * <p>Loaded from {@code realm-templates.yml}. Falls back to defaults (Y=64)
 * for any missing biome or key.
 */
public class RealmTemplateConfig {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final double DEFAULT_SPAWN_Y = 64.0;
    private static final double DEFAULT_EXIT_OFFSET_X = 5.0;
    private static final double DEFAULT_EXIT_OFFSET_Z = 0.0;

    /**
     * Per-biome template configuration.
     */
    public record BiomeTemplate(
            double spawnY,
            double exitOffsetX,
            double exitOffsetZ) {

        public static final BiomeTemplate DEFAULT = new BiomeTemplate(
            DEFAULT_SPAWN_Y, DEFAULT_EXIT_OFFSET_X, DEFAULT_EXIT_OFFSET_Z);
    }

    private final Map<RealmBiomeType, BiomeTemplate> templates = new EnumMap<>(RealmBiomeType.class);

    /**
     * Gets the template config for a biome, falling back to defaults.
     */
    @Nonnull
    public BiomeTemplate getTemplate(@Nonnull RealmBiomeType biome) {
        return templates.getOrDefault(biome, BiomeTemplate.DEFAULT);
    }

    /**
     * Gets the spawn Y for a biome.
     */
    public double getSpawnY(@Nonnull RealmBiomeType biome) {
        return getTemplate(biome).spawnY();
    }

    /**
     * Loads template config from YAML file. Falls back to defaults for missing data.
     *
     * @param configDir The config directory
     * @return The loaded config
     */
    @Nonnull
    @SuppressWarnings("unchecked")
    public static RealmTemplateConfig load(@Nonnull Path configDir) {
        RealmTemplateConfig config = new RealmTemplateConfig();
        Path file = configDir.resolve("realm-templates.yml");

        if (!Files.exists(file)) {
            LOGGER.atInfo().log("realm-templates.yml not found — using default values");
            return config;
        }

        try (InputStream is = Files.newInputStream(file)) {
            Yaml yaml = new Yaml();
            Map<String, Object> root = yaml.load(is);
            if (root == null) {
                LOGGER.atWarning().log("realm-templates.yml is empty — using defaults");
                return config;
            }

            Object templatesObj = root.get("templates");
            if (!(templatesObj instanceof Map<?, ?> templatesMap)) {
                return config;
            }

            for (Map.Entry<?, ?> entry : templatesMap.entrySet()) {
                String biomeKey = entry.getKey().toString().toUpperCase();
                RealmBiomeType biome;
                try {
                    biome = RealmBiomeType.valueOf(biomeKey);
                } catch (IllegalArgumentException e) {
                    LOGGER.atWarning().log("Unknown biome in realm-templates.yml: %s", biomeKey);
                    continue;
                }

                if (entry.getValue() instanceof Map<?, ?> biomeMap) {
                    double spawnY = getDouble(biomeMap, "spawn-y", DEFAULT_SPAWN_Y);
                    double exitX = getDouble(biomeMap, "exit-offset-x", DEFAULT_EXIT_OFFSET_X);
                    double exitZ = getDouble(biomeMap, "exit-offset-z", DEFAULT_EXIT_OFFSET_Z);
                    config.templates.put(biome, new BiomeTemplate(spawnY, exitX, exitZ));
                }
            }

            LOGGER.atInfo().log("Loaded realm template config for %d biomes", config.templates.size());
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to load realm-templates.yml — using defaults");
        }

        return config;
    }

    private static double getDouble(Map<?, ?> map, String key, double defaultValue) {
        Object val = map.get(key);
        if (val instanceof Number num) {
            return num.doubleValue();
        }
        return defaultValue;
    }
}
