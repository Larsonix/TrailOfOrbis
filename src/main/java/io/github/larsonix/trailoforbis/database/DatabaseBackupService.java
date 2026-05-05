package io.github.larsonix.trailoforbis.database;

import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.logging.Level;
import java.util.stream.Stream;

/**
 * Backs up the H2 database file before any schema modifications.
 *
 * <p>Called BEFORE DataManager.initialize() on server startup, ensuring
 * a clean copy exists in case schema migrations corrupt the database.
 *
 * <p>Structure mirrors ConfigBackupService:
 * <pre>
 * db-backups/
 *   2026-05-05_10-49-00/
 *     trailoforbis.mv.db.bak
 *   2026-05-10_14-30-00/
 *     trailoforbis.mv.db.bak
 * </pre>
 *
 * <p>Old backups beyond the retention period are automatically cleaned.
 */
public final class DatabaseBackupService {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final DateTimeFormatter DIR_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss").withZone(ZoneId.systemDefault());

    private static final int RETENTION_DAYS = 30;
    private static final String DB_FILENAME = "trailoforbis.mv.db";

    private final Path dataFolder;
    private final Path backupDir;

    public DatabaseBackupService(@Nonnull Path dataFolder) {
        this.dataFolder = dataFolder;
        this.backupDir = dataFolder.resolve("db-backups");
    }

    /**
     * Creates a backup of the database file if it exists.
     *
     * <p>Must be called BEFORE DataManager.initialize() to capture
     * the database in its pre-migration state.
     *
     * @return true if backup was created (or no DB exists yet), false on error
     */
    public boolean backup() {
        Path dbFile = dataFolder.resolve(DB_FILENAME);

        if (!Files.exists(dbFile)) {
            LOGGER.at(Level.FINE).log("No database file to backup (fresh install)");
            return true;
        }

        try {
            // Create timestamped backup directory
            String timestamp = DIR_FORMAT.format(Instant.now());
            Path runDir = backupDir.resolve(timestamp);
            Files.createDirectories(runDir);

            // Copy database file
            Path backupPath = runDir.resolve(DB_FILENAME + ".bak");
            Files.copy(dbFile, backupPath, StandardCopyOption.REPLACE_EXISTING);

            long sizeKb = Files.size(dbFile) / 1024;
            LOGGER.at(Level.INFO).log("Database backup created: %s (%d KB)", backupPath.getFileName(), sizeKb);

            // Cleanup old backups
            cleanOldBackups();

            return true;
        } catch (IOException e) {
            LOGGER.at(Level.WARNING).withCause(e).log(
                    "Failed to backup database (server will continue, but no safety net for this startup)");
            return false;
        }
    }

    /**
     * Removes backup directories older than the retention period.
     */
    private void cleanOldBackups() {
        if (!Files.exists(backupDir)) return;

        Instant cutoff = Instant.now().minus(RETENTION_DAYS, ChronoUnit.DAYS);

        try (Stream<Path> dirs = Files.list(backupDir)) {
            dirs.filter(Files::isDirectory)
                    .filter(dir -> {
                        try {
                            return Files.getLastModifiedTime(dir).toInstant().isBefore(cutoff);
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .forEach(this::deleteDirectory);
        } catch (IOException e) {
            LOGGER.at(Level.FINE).log("Could not list backup dirs for cleanup: %s", e.getMessage());
        }
    }

    private void deleteDirectory(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            // Best effort
                        }
                    });
            LOGGER.at(Level.FINE).log("Cleaned old DB backup: %s", dir.getFileName());
        } catch (IOException e) {
            // Best effort
        }
    }
}
