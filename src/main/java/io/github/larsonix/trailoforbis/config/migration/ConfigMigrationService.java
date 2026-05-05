package io.github.larsonix.trailoforbis.config.migration;

import com.hypixel.hytale.logger.HytaleLogger;
import io.github.larsonix.trailoforbis.config.migration.migrations.V1ConfigMigration;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Level;

/**
 * Orchestrates config file migration on server startup.
 *
 * <p>For each registered config file:
 * <ol>
 *   <li>Reads the user's file from disk as a raw YAML map</li>
 *   <li>Reads the bundled template from JAR resources</li>
 *   <li>Compares {@code config_version} (missing = v0)</li>
 *   <li>If outdated: backs up, runs version-specific migrations, deep merges,
 *       writes result using template formatting</li>
 *   <li>If current: no-op</li>
 * </ol>
 *
 * <p>This runs BEFORE normal config loading. After migration completes, files on
 * disk are valid current-version configs and existing loaders work unchanged.
 */
public final class ConfigMigrationService {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final Path configDir;
    private final Path versionsFile;
    private final ConfigBackupService backupService;
    private final List<ConfigMigration> migrations;
    private final Yaml yaml = new Yaml();
    private Map<String, Object> versions; // filename → version int

    public ConfigMigrationService(Path configDir) {
        this.configDir = configDir;
        this.versionsFile = configDir.resolve(ConfigVersionRegistry.VERSIONS_FILE);
        this.backupService = new ConfigBackupService(configDir);
        this.migrations = registerMigrations();
        this.versions = loadVersionsFile();
    }

    /**
     * Migrates all registered config files.
     * Safe to call multiple times (idempotent — skips files at current version).
     */
    public void migrateAll() {
        int migrated = 0;
        int skipped = 0;
        int failed = 0;

        for (String filename : ConfigVersionRegistry.getAllConfigFiles()) {
            try {
                boolean didMigrate = migrateFile(filename);
                if (didMigrate) migrated++;
                else skipped++;
            } catch (Exception e) {
                failed++;
                LOGGER.at(Level.SEVERE).withCause(e).log(
                    "Config migration failed for %s (file will use defaults)", filename);
            }
        }

        if (migrated > 0) {
            LOGGER.at(Level.INFO).log("Config migration complete: %d migrated, %d current, %d failed",
                migrated, skipped, failed);
            saveVersionsFile();
        }

        // Clean old backups once per run
        backupService.cleanOldBackups();
    }

    /**
     * Migrates a single config file if needed.
     *
     * @return true if migration was performed, false if file was already current
     */
    private boolean migrateFile(String filename) throws IOException {
        Path filePath = configDir.resolve(filename);

        // If file doesn't exist, skip — copyDefaultIfMissing will handle it later
        if (!Files.exists(filePath)) {
            return false;
        }

        // Check tracked version
        int fileVersion = getFileVersion(filename);
        int targetVersion = ConfigVersionRegistry.CURRENT_VERSION;
        if (fileVersion >= targetVersion) {
            return false; // Already current
        }

        // Read user file as raw map
        Map<String, Object> userMap = readYamlMap(filePath);
        if (userMap == null) {
            // Corrupt/empty file — backup and let normal loading handle it
            LOGGER.at(Level.WARNING).log("Config file %s is empty/corrupt, backing up", filename);
            backupService.backup(filePath, 0);
            Files.deleteIfExists(filePath);
            return false;
        }

        LOGGER.at(Level.FINE).log("Migrating %s: v%d → v%d", filename, fileVersion, targetVersion);

        // Load bundled template
        String templateText = loadBundledTemplateText(filename);
        Map<String, Object> templateMap = loadBundledTemplateMap(filename);

        if (templateText == null || templateMap == null) {
            LOGGER.at(Level.SEVERE).log(
                "Bundled template not found for %s (build error?), skipping migration", filename);
            return false;
        }

        // Backup before modification
        backupService.backup(filePath, fileVersion);

        // Run version-specific migrations in sequence
        Map<String, Object> migratedMap = userMap;
        for (ConfigMigration migration : migrations) {
            if (shouldRunMigration(migration, filename, fileVersion, targetVersion)) {
                try {
                    migration.migrate(migratedMap);
                } catch (Exception e) {
                    LOGGER.at(Level.WARNING).withCause(e).log(
                        "Migration step %s v%d→v%d failed for %s, continuing",
                        migration.getClass().getSimpleName(),
                        migration.fromVersion(), migration.toVersion(), filename);
                }
            }
        }

        // Deep merge: user values preserved over bundled template
        Map<String, Object> merged = YamlDeepMerger.merge(templateMap, migratedMap);

        // Write result using template formatting
        TemplatePreservingWriter.write(merged, templateText, filePath);

        // Track the new version
        setFileVersion(filename, targetVersion);

        LOGGER.at(Level.FINE).log("Successfully migrated %s to v%d", filename, targetVersion);
        return true;
    }

    /**
     * Determines if a migration step should run for the given file and version range.
     */
    private boolean shouldRunMigration(ConfigMigration migration, String filename,
                                        int fileVersion, int targetVersion) {
        // Version range check
        if (migration.fromVersion() < fileVersion) return false;
        if (migration.toVersion() > targetVersion) return false;

        // File target check
        String target = migration.targetFile();
        return "*".equals(target) || filename.equals(target);
    }

    /**
     * Gets the tracked version for a config file. Returns 0 if not tracked.
     */
    private int getFileVersion(String filename) {
        Object version = versions.get(filename);
        if (version instanceof Number) {
            return ((Number) version).intValue();
        }
        return 0; // Not tracked = pre-migration (v0)
    }

    /**
     * Records the current version for a config file.
     */
    private void setFileVersion(String filename, int version) {
        versions.put(filename, version);
    }

    /**
     * Loads the versions tracking file, or returns empty map if not found.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> loadVersionsFile() {
        if (!Files.exists(versionsFile)) {
            return new LinkedHashMap<>();
        }
        try {
            String content = Files.readString(versionsFile, StandardCharsets.UTF_8);
            Object loaded = yaml.load(content);
            if (loaded instanceof Map) {
                return new LinkedHashMap<>((Map<String, Object>) loaded);
            }
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e).log("Failed to read versions file, will re-migrate");
        }
        return new LinkedHashMap<>();
    }

    /**
     * Saves the versions tracking file.
     */
    private void saveVersionsFile() {
        try {
            DumperOptions opts = new DumperOptions();
            opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            Yaml dumper = new Yaml(opts);
            Files.writeString(versionsFile, dumper.dump(versions), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.at(Level.WARNING).withCause(e).log("Failed to save versions file");
        }
    }

    /**
     * Reads a YAML file into a raw map.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> readYamlMap(Path path) {
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            if (content.isBlank()) return null;
            Object loaded = yaml.load(content);
            if (loaded instanceof Map) {
                return (Map<String, Object>) loaded;
            }
            return null;
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e).log("Failed to parse YAML: %s", path.getFileName());
            return null;
        }
    }

    /**
     * Loads the bundled template as raw text (preserving comments).
     */
    private String loadBundledTemplateText(String filename) {
        try (InputStream is = getClass().getClassLoader()
                .getResourceAsStream("config/" + filename)) {
            if (is == null) return null;
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Loads the bundled template as a parsed YAML map.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> loadBundledTemplateMap(String filename) {
        String text = loadBundledTemplateText(filename);
        if (text == null) return null;
        Object loaded = yaml.load(text);
        if (loaded instanceof Map) {
            return (Map<String, Object>) loaded;
        }
        return null;
    }

    /**
     * Registers all version-specific migrations in order.
     * Add new migrations here when config structure changes.
     */
    private static List<ConfigMigration> registerMigrations() {
        List<ConfigMigration> list = new ArrayList<>();
        list.add(new V1ConfigMigration());
        // Future migrations:
        // list.add(new V2RenameAilmentKeys());
        // list.add(new V3SplitCombatConfig());
        return Collections.unmodifiableList(list);
    }
}
