package io.github.larsonix.trailoforbis.config.migration;

import com.hypixel.hytale.logger.HytaleLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.logging.Level;
import java.util.stream.Stream;

/**
 * Manages config file backups before migration.
 *
 * <p>Backups are organized in timestamped subdirectories — one per migration run:
 * <pre>
 * config/backups/
 *   2026-05-05_10-36-30/     ← all configs backed up during this run
 *     ailments.yml.v0.bak
 *     config.yml.v0.bak
 *     ...
 *   2026-05-10_14-30-00/     ← next version upgrade
 *     ailments.yml.v1.bak
 *     ...
 * </pre>
 *
 * <p>Old backup directories beyond the retention period are automatically cleaned.
 */
public final class ConfigBackupService {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final DateTimeFormatter DIR_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss").withZone(ZoneId.systemDefault());

    /** Backups older than this many days are removed on cleanup. */
    private static final int RETENTION_DAYS = 30;

    private final Path backupDir;

    /** Lazily created subdirectory for this migration run (shared across all files). */
    private Path currentRunDir;

    public ConfigBackupService(Path configDir) {
        this.backupDir = configDir.resolve("backups");
    }

    /**
     * Creates a backup of the given config file.
     *
     * <p>All backups within a single migration run go into the same timestamped directory.
     *
     * @param configFile the file to back up
     * @param oldVersion the version being migrated from (used in filename)
     * @return the backup path, or null if backup failed
     */
    public Path backup(Path configFile, int oldVersion) {
        try {
            Path runDir = getOrCreateRunDir();

            String backupName = configFile.getFileName().toString()
                + ".v" + oldVersion
                + ".bak";

            Path backupPath = runDir.resolve(backupName);
            Files.copy(configFile, backupPath, StandardCopyOption.REPLACE_EXISTING);

            LOGGER.at(Level.FINE).log("Backed up: %s → %s", configFile.getFileName(), backupName);
            return backupPath;
        } catch (IOException e) {
            LOGGER.at(Level.WARNING).withCause(e).log(
                "Failed to backup config %s (migration will proceed anyway)",
                configFile.getFileName());
            return null;
        }
    }

    /**
     * Gets or creates the subdirectory for this migration run.
     * All files backed up during the same migrateAll() call share one directory.
     */
    private Path getOrCreateRunDir() throws IOException {
        if (currentRunDir == null) {
            String timestamp = DIR_FORMAT.format(Instant.now());
            currentRunDir = backupDir.resolve(timestamp);
            Files.createDirectories(currentRunDir);
        }
        return currentRunDir;
    }

    /**
     * Removes backup directories older than the retention period.
     * Called once per migration run.
     */
    public void cleanOldBackups() {
        if (!Files.isDirectory(backupDir)) {
            return;
        }

        Instant cutoff = Instant.now().minusSeconds(RETENTION_DAYS * 86400L);
        final int[] cleaned = {0};

        try (Stream<Path> dirs = Files.list(backupDir)) {
            var oldDirs = dirs
                .filter(Files::isDirectory)
                .filter(p -> {
                    try {
                        return Files.getLastModifiedTime(p).toInstant().isBefore(cutoff);
                    } catch (IOException e) {
                        return false;
                    }
                })
                .toList();

            for (Path oldDir : oldDirs) {
                deleteDirectory(oldDir);
                cleaned[0]++;
            }
        } catch (IOException e) {
            LOGGER.at(Level.FINE).withCause(e).log("Failed to clean old backups");
        }

        // Also clean legacy flat .bak files (from before directory-based backups)
        try (Stream<Path> files = Files.list(backupDir)) {
            files.filter(p -> !Files.isDirectory(p) && p.toString().endsWith(".bak"))
                .filter(p -> {
                    try {
                        return Files.getLastModifiedTime(p).toInstant().isBefore(cutoff);
                    } catch (IOException e) {
                        return false;
                    }
                })
                .forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                        cleaned[0]++;
                    } catch (IOException ignored) {}
                });
        } catch (IOException ignored) {}

        if (cleaned[0] > 0) {
            LOGGER.at(Level.FINE).log("Cleaned %d old backup(s) beyond %d-day retention", cleaned[0], RETENTION_DAYS);
        }
    }

    /**
     * Recursively deletes a directory and all its contents.
     */
    private static void deleteDirectory(Path dir) throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                .forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {}
                });
        }
    }
}
