package io.github.larsonix.trailoforbis.config.migration;

import com.hypixel.hytale.logger.HytaleLogger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;

/**
 * Syncs config files from the JAR to disk on plugin updates.
 *
 * <p>On every boot:
 * <ul>
 *   <li><b>Version changed</b> (JAR update): back up all existing configs,
 *       then overwrite every registered file from the JAR template.</li>
 *   <li><b>Same version</b> (normal restart): only create missing files
 *       from JAR templates (preserves player edits).</li>
 *   <li><b>No stored version</b> (fresh install): create all files from
 *       JAR templates.</li>
 * </ul>
 *
 * <p>This runs BEFORE normal config loading. After sync completes, files on
 * disk match the current JAR version and existing loaders work unchanged.
 */
public final class ConfigMigrationService {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final Path configDir;
    private final Path versionFile;
    private final String pluginVersion;
    private final ConfigBackupService backupService;

    /**
     * Creates a new config sync service.
     *
     * @param configDir     the config directory on disk
     * @param pluginVersion the current plugin version from manifest
     */
    public ConfigMigrationService(Path configDir, String pluginVersion) {
        this.configDir = configDir;
        this.versionFile = configDir.resolve(ConfigVersionRegistry.LAST_SYNCED_VERSION_FILE);
        this.pluginVersion = pluginVersion;
        this.backupService = new ConfigBackupService(configDir);
    }

    /**
     * Syncs all registered config files.
     *
     * <p>If the plugin version changed since last sync, all configs are
     * overwritten from JAR templates (with backup). Otherwise, only
     * missing files are created.
     */
    public void migrateAll() {
        String lastSynced = readLastSyncedVersion();
        boolean isUpdate = lastSynced != null && !lastSynced.equals(pluginVersion);
        boolean isFreshInstall = lastSynced == null;

        if (isUpdate) {
            LOGGER.at(Level.INFO).log("Plugin update detected: %s → %s — syncing all configs", lastSynced, pluginVersion);
            syncAllFromJar(true);
        } else if (isFreshInstall) {
            LOGGER.at(Level.INFO).log("Fresh install (v%s) — creating all config files", pluginVersion);
            syncAllFromJar(false);
        } else {
            // Same version — only fill in missing files
            int created = fillMissingFiles();
            if (created > 0) {
                LOGGER.at(Level.INFO).log("Restored %d missing config file(s)", created);
            }
        }

        writeLastSyncedVersion();
        backupService.cleanOldBackups();
    }

    /**
     * Overwrites all registered config files from JAR templates.
     *
     * @param backup whether to back up existing files first (true for updates, false for fresh install)
     */
    private void syncAllFromJar(boolean backup) {
        int synced = 0;
        int failed = 0;

        for (String filename : ConfigVersionRegistry.getAllConfigFiles()) {
            try {
                String templateText = loadBundledTemplateText(filename);
                if (templateText == null) {
                    LOGGER.at(Level.WARNING).log("No JAR template for %s — skipping", filename);
                    failed++;
                    continue;
                }

                Path filePath = configDir.resolve(filename);

                // Back up existing file before overwriting
                if (backup && Files.exists(filePath)) {
                    backupService.backup(filePath, 0);
                }

                // Ensure parent dirs exist (for potential subdirectory configs)
                Files.createDirectories(filePath.getParent());

                // Write JAR template to disk
                Files.writeString(filePath, templateText, StandardCharsets.UTF_8);
                synced++;
            } catch (Exception e) {
                failed++;
                LOGGER.at(Level.SEVERE).withCause(e).log("Failed to sync config: %s", filename);
            }
        }

        if (backup) {
            LOGGER.at(Level.INFO).log("Config sync complete: %d files synced, %d failed (backup saved)", synced, failed);
        } else {
            LOGGER.at(Level.INFO).log("Config sync complete: %d files created", synced);
        }
    }

    /**
     * Creates any registered config files that are missing from disk.
     * Called on normal restart (same version) to handle deleted files.
     *
     * @return number of files created
     */
    private int fillMissingFiles() {
        int created = 0;
        for (String filename : ConfigVersionRegistry.getAllConfigFiles()) {
            Path filePath = configDir.resolve(filename);
            if (!Files.exists(filePath)) {
                try {
                    String templateText = loadBundledTemplateText(filename);
                    if (templateText != null) {
                        Files.createDirectories(filePath.getParent());
                        Files.writeString(filePath, templateText, StandardCharsets.UTF_8);
                        created++;
                        LOGGER.at(Level.INFO).log("Created missing config: %s", filename);
                    }
                } catch (Exception e) {
                    LOGGER.at(Level.WARNING).withCause(e).log("Failed to create missing config: %s", filename);
                }
            }
        }
        return created;
    }

    // ═══════════════════════════════════════════════════════════════════
    // VERSION FILE
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Reads the last synced plugin version from disk.
     *
     * @return the version string, or null if not found (fresh install)
     */
    private String readLastSyncedVersion() {
        if (!Files.exists(versionFile)) {
            return null;
        }
        try {
            String content = Files.readString(versionFile, StandardCharsets.UTF_8).trim();
            return content.isEmpty() ? null : content;
        } catch (IOException e) {
            LOGGER.at(Level.WARNING).withCause(e).log("Failed to read %s — treating as fresh install",
                ConfigVersionRegistry.LAST_SYNCED_VERSION_FILE);
            return null;
        }
    }

    /**
     * Writes the current plugin version as the last synced version.
     */
    private void writeLastSyncedVersion() {
        try {
            Files.createDirectories(versionFile.getParent());
            Files.writeString(versionFile, pluginVersion, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.at(Level.WARNING).withCause(e).log("Failed to write %s",
                ConfigVersionRegistry.LAST_SYNCED_VERSION_FILE);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // JAR TEMPLATE LOADING
    // ═══════════════════════════════════════════════════════════════════

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
}
