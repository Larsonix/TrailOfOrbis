package io.github.larsonix.trailoforbis.config.migration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for version-based config sync.
 *
 * <p>Tests the complete flow: JAR templates are synced to disk on version change,
 * missing files are restored on restart, and backups are created on update.
 */
class ConfigMigrationIntegrationTest {

    @TempDir
    Path tempDir;

    private Path configDir;

    @BeforeEach
    void setUp() throws IOException {
        configDir = tempDir.resolve("config");
        Files.createDirectories(configDir);
    }

    @Test
    void freshInstallCreatesAllFiles() {
        // No files exist, no .last-synced-version — should create files from JAR
        ConfigMigrationService service = new ConfigMigrationService(configDir, "1.0.0");
        service.migrateAll();

        // Version file should be created
        Path versionFile = configDir.resolve(".last-synced-version");
        assertTrue(Files.exists(versionFile), "Version file should be created on fresh install");

        // Check version content
        try {
            String version = Files.readString(versionFile, StandardCharsets.UTF_8).trim();
            assertEquals("1.0.0", version);
        } catch (IOException e) {
            fail("Should be able to read version file");
        }
    }

    @Test
    void versionChangeOverwritesAllConfigs() throws IOException {
        // Simulate existing install at version 1.0.0
        String customConfig = "debugMode: true\nlanguage: fr_FR\n";
        Files.writeString(configDir.resolve("config.yml"), customConfig, StandardCharsets.UTF_8);
        Files.writeString(configDir.resolve(".last-synced-version"), "1.0.0", StandardCharsets.UTF_8);

        // Update to 1.0.1
        ConfigMigrationService service = new ConfigMigrationService(configDir, "1.0.1");
        service.migrateAll();

        // config.yml should be overwritten from JAR template (user values lost)
        String result = Files.readString(configDir.resolve("config.yml"), StandardCharsets.UTF_8);
        Yaml yaml = new Yaml();
        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = yaml.loadAs(result, Map.class);

        // JAR template has debugMode: false (the default)
        assertEquals(false, parsed.get("debugMode"),
            "JAR template should overwrite user value on version change");

        // Version file updated
        String version = Files.readString(configDir.resolve(".last-synced-version"), StandardCharsets.UTF_8).trim();
        assertEquals("1.0.1", version);
    }

    @Test
    void sameVersionPreservesExistingFiles() throws IOException {
        // Write a custom config and mark as synced at current version
        String customConfig = "debugMode: true\n";
        Files.writeString(configDir.resolve("config.yml"), customConfig, StandardCharsets.UTF_8);
        Files.writeString(configDir.resolve(".last-synced-version"), "1.0.0", StandardCharsets.UTF_8);

        long beforeModified = Files.getLastModifiedTime(configDir.resolve("config.yml")).toMillis();
        try { Thread.sleep(50); } catch (InterruptedException ignored) {}

        // Restart with same version
        ConfigMigrationService service = new ConfigMigrationService(configDir, "1.0.0");
        service.migrateAll();

        // File should NOT be modified
        long afterModified = Files.getLastModifiedTime(configDir.resolve("config.yml")).toMillis();
        assertEquals(beforeModified, afterModified,
            "File should not be modified on same-version restart");

        // Content should be unchanged
        String result = Files.readString(configDir.resolve("config.yml"), StandardCharsets.UTF_8);
        assertEquals(customConfig, result, "User config should be preserved on same-version restart");
    }

    @Test
    void missingFileRestoredOnSameVersion() throws IOException {
        // Synced at 1.0.0 but config.yml is missing (deleted by user)
        Files.writeString(configDir.resolve(".last-synced-version"), "1.0.0", StandardCharsets.UTF_8);
        assertFalse(Files.exists(configDir.resolve("config.yml")));

        ConfigMigrationService service = new ConfigMigrationService(configDir, "1.0.0");
        service.migrateAll();

        // File should be restored from JAR template if available
        // (config.yml is in the JAR test resources)
        InputStream templateStream = getClass().getClassLoader()
            .getResourceAsStream("config/config.yml");
        if (templateStream != null) {
            templateStream.close();
            assertTrue(Files.exists(configDir.resolve("config.yml")),
                "Missing config should be restored from JAR on same-version restart");
        }
    }

    @Test
    void backupCreatedOnVersionChange() throws IOException {
        String oldConfig = "debugMode: true\n";
        Files.writeString(configDir.resolve("config.yml"), oldConfig, StandardCharsets.UTF_8);
        Files.writeString(configDir.resolve(".last-synced-version"), "1.0.0", StandardCharsets.UTF_8);

        // Update to 1.0.1
        ConfigMigrationService service = new ConfigMigrationService(configDir, "1.0.1");
        service.migrateAll();

        // Verify backup exists
        Path backupDir = configDir.resolve("backups");
        assertTrue(Files.isDirectory(backupDir), "Backups directory should be created on update");

        long backupCount = Files.walk(backupDir)
            .filter(p -> p.toString().contains("config.yml") && p.toString().endsWith(".bak"))
            .count();
        assertEquals(1, backupCount, "Should have exactly one backup of config.yml");
    }

    @Test
    void noBackupOnFreshInstall() {
        // Fresh install — no files to back up
        ConfigMigrationService service = new ConfigMigrationService(configDir, "1.0.0");
        service.migrateAll();

        Path backupDir = configDir.resolve("backups");
        assertFalse(Files.isDirectory(backupDir),
            "No backup directory should be created on fresh install");
    }

    @Test
    void idempotentOnSameVersion() throws IOException {
        // First run: fresh install
        ConfigMigrationService service1 = new ConfigMigrationService(configDir, "1.0.0");
        service1.migrateAll();

        // Record state
        InputStream templateStream = getClass().getClassLoader()
            .getResourceAsStream("config/config.yml");
        if (templateStream == null) return; // Skip if no template available
        templateStream.close();

        if (Files.exists(configDir.resolve("config.yml"))) {
            long firstModified = Files.getLastModifiedTime(configDir.resolve("config.yml")).toMillis();
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}

            // Second run: same version — should be no-op
            ConfigMigrationService service2 = new ConfigMigrationService(configDir, "1.0.0");
            service2.migrateAll();

            long secondModified = Files.getLastModifiedTime(configDir.resolve("config.yml")).toMillis();
            assertEquals(firstModified, secondModified,
                "File should NOT be modified on second run with same version");
        }
    }

    @Test
    void migratedFileContainsComments() throws IOException {
        // Fresh install creates file from JAR template
        ConfigMigrationService service = new ConfigMigrationService(configDir, "1.0.0");
        service.migrateAll();

        if (Files.exists(configDir.resolve("config.yml"))) {
            String content = Files.readString(configDir.resolve("config.yml"), StandardCharsets.UTF_8);
            assertTrue(content.contains("#"), "Config from JAR template should contain comments");

            // Verify valid YAML
            Yaml yaml = new Yaml();
            assertDoesNotThrow(() -> yaml.loadAs(content, Map.class));
        }
    }
}
