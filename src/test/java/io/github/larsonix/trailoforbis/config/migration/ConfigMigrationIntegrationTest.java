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
 * Integration test for the full config migration pipeline.
 *
 * <p>Tests the complete flow: user has an old config → migration adds new keys,
 * preserves user values, creates backup, and produces valid YAML output.
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
    void freshInstallNoMigrationNeeded() {
        // No files exist — migration should be a no-op
        ConfigMigrationService service = new ConfigMigrationService(configDir);
        service.migrateAll(); // Should not throw

        // Versions file should not be created (nothing was migrated)
        assertFalse(Files.exists(configDir.resolve(".versions.yml")));
    }

    @Test
    void existingFileGetsMigratedWithNewKeys() throws IOException {
        // Simulate a user's old config.yml that's missing some keys
        String oldConfig = """
            # My server config
            debugMode: false
            language: "en_US"

            database:
              type: "H2"
              host: "localhost"
              port: 3306
            """;
        Files.writeString(configDir.resolve("config.yml"), oldConfig, StandardCharsets.UTF_8);

        // Run migration
        ConfigMigrationService service = new ConfigMigrationService(configDir);
        service.migrateAll();

        // Verify: file should still be valid YAML
        String migrated = Files.readString(configDir.resolve("config.yml"), StandardCharsets.UTF_8);
        Yaml yaml = new Yaml();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = yaml.loadAs(migrated, Map.class);
        assertNotNull(result, "Migrated file should be valid YAML");

        // Verify: user values preserved
        assertEquals(false, result.get("debugMode"));
        assertEquals("en_US", result.get("language"));

        @SuppressWarnings("unchecked")
        Map<String, Object> db = (Map<String, Object>) result.get("database");
        assertNotNull(db);
        assertEquals("H2", db.get("type"));
        assertEquals("localhost", db.get("host"));

        // Verify: version is now tracked
        assertTrue(Files.exists(configDir.resolve(".versions.yml")));
        String versionsContent = Files.readString(configDir.resolve(".versions.yml"));
        assertTrue(versionsContent.contains("config.yml"),
            "Versions file should track config.yml");
    }

    @Test
    void backupCreatedBeforeMigration() throws IOException {
        String oldConfig = "debugMode: true\n";
        Files.writeString(configDir.resolve("config.yml"), oldConfig, StandardCharsets.UTF_8);

        ConfigMigrationService service = new ConfigMigrationService(configDir);
        service.migrateAll();

        // Verify backup exists in a timestamped subdirectory
        Path backupDir = configDir.resolve("backups");
        assertTrue(Files.isDirectory(backupDir), "Backups directory should be created");

        // Backups are now in timestamped subdirectories: backups/2026-05-05_10-36-30/config.yml.v0.bak
        long backupCount = Files.walk(backupDir)
            .filter(p -> p.toString().contains("config.yml") && p.toString().endsWith(".bak"))
            .count();
        assertEquals(1, backupCount, "Should have exactly one backup of config.yml");
    }

    @Test
    void alreadyMigratedFileIsSkipped() throws IOException {
        String config = "debugMode: false\n";
        Files.writeString(configDir.resolve("config.yml"), config, StandardCharsets.UTF_8);

        // First migration
        ConfigMigrationService service1 = new ConfigMigrationService(configDir);
        service1.migrateAll();

        long firstModified = Files.getLastModifiedTime(configDir.resolve("config.yml")).toMillis();

        // Wait a bit and run again
        try { Thread.sleep(50); } catch (InterruptedException ignored) {}

        // Second migration — should be a no-op
        ConfigMigrationService service2 = new ConfigMigrationService(configDir);
        service2.migrateAll();

        long secondModified = Files.getLastModifiedTime(configDir.resolve("config.yml")).toMillis();
        assertEquals(firstModified, secondModified,
            "File should NOT be modified on second run (already at current version)");
    }

    @Test
    void corruptFileGetsBackedUpAndDeleted() throws IOException {
        // Write invalid YAML
        Files.writeString(configDir.resolve("config.yml"), "{{{{invalid yaml!!!!",
            StandardCharsets.UTF_8);

        ConfigMigrationService service = new ConfigMigrationService(configDir);
        service.migrateAll(); // Should not throw

        // File should be deleted (so copyDefaultIfMissing will recreate it)
        assertFalse(Files.exists(configDir.resolve("config.yml")),
            "Corrupt file should be removed after backup");

        // Backup should exist
        Path backupDir = configDir.resolve("backups");
        assertTrue(Files.isDirectory(backupDir));
    }

    @Test
    void userCustomValuesPreservedAfterMigration() throws IOException {
        // User has customized several values
        String customConfig = """
            debugMode: true
            language: "fr_FR"
            suppressVanillaGearDrops: false
            creativeModeBypassRequirements: false

            database:
              type: "MySQL"
              host: "db.myserver.com"
              port: 5432
              database: "mydb"
              username: "admin"
              password: "secret123"
              poolSize: 50

            attributes:
              pointsPerLevel: 2
              startingPoints: 5
            """;
        Files.writeString(configDir.resolve("config.yml"), customConfig, StandardCharsets.UTF_8);

        ConfigMigrationService service = new ConfigMigrationService(configDir);
        service.migrateAll();

        // Parse result
        String migrated = Files.readString(configDir.resolve("config.yml"), StandardCharsets.UTF_8);
        Yaml yaml = new Yaml();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = yaml.loadAs(migrated, Map.class);

        // All user values must be preserved
        assertEquals(true, result.get("debugMode"));
        assertEquals("fr_FR", result.get("language"));
        assertEquals(false, result.get("suppressVanillaGearDrops"));
        assertEquals(false, result.get("creativeModeBypassRequirements"));

        @SuppressWarnings("unchecked")
        Map<String, Object> db = (Map<String, Object>) result.get("database");
        assertEquals("MySQL", db.get("type"));
        assertEquals("db.myserver.com", db.get("host"));
        assertEquals(5432, db.get("port"));
        assertEquals("mydb", db.get("database"));
        assertEquals("admin", db.get("username"));
        assertEquals("secret123", db.get("password"));
        assertEquals(50, db.get("poolSize"));

        @SuppressWarnings("unchecked")
        Map<String, Object> attrs = (Map<String, Object>) result.get("attributes");
        assertEquals(2, attrs.get("pointsPerLevel"));
        assertEquals(5, attrs.get("startingPoints"));
    }

    @Test
    void migratedFilePreservesComments() throws IOException {
        // User has a minimal config (will trigger migration with template merge)
        String oldConfig = "debugMode: true\n";
        Files.writeString(configDir.resolve("config.yml"), oldConfig, StandardCharsets.UTF_8);

        ConfigMigrationService service = new ConfigMigrationService(configDir);
        service.migrateAll();

        String migrated = Files.readString(configDir.resolve("config.yml"), StandardCharsets.UTF_8);

        // The bundled template for config.yml has comments — they should be preserved
        assertTrue(migrated.contains("#"), "Migrated file should contain comments from template");

        // Verify it's still valid YAML (comments don't break parsing)
        Yaml yaml = new Yaml();
        assertDoesNotThrow(() -> yaml.loadAs(migrated, Map.class));
    }

    @Test
    void realConfigFileRoundTrip() throws IOException {
        // Load the actual bundled config.yml template
        InputStream templateStream = getClass().getClassLoader()
            .getResourceAsStream("config/config.yml");
        if (templateStream == null) {
            // Skip if resource not available in test classpath
            return;
        }

        String templateContent = new String(templateStream.readAllBytes(), StandardCharsets.UTF_8);
        Files.writeString(configDir.resolve("config.yml"), templateContent, StandardCharsets.UTF_8);

        // Modify one value (simulates user customization)
        String modified = templateContent.replace("debugMode: false", "debugMode: true");
        Files.writeString(configDir.resolve("config.yml"), modified, StandardCharsets.UTF_8);

        // Run migration
        ConfigMigrationService service = new ConfigMigrationService(configDir);
        service.migrateAll();

        // Parse result
        String result = Files.readString(configDir.resolve("config.yml"), StandardCharsets.UTF_8);
        Yaml yaml = new Yaml();
        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = yaml.loadAs(result, Map.class);

        assertNotNull(parsed, "Result must be valid YAML");
        assertEquals(true, parsed.get("debugMode"), "User's modified value must be preserved");
    }
}
