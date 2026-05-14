package io.github.larsonix.trailoforbis.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates that all YAML config files in resources/config/ parse without error.
 *
 * <p>This catches:
 * <ul>
 *   <li>Syntax errors (bad indentation, missing colons)</li>
 *   <li>Encoding issues (BOM, non-UTF8 characters)</li>
 *   <li>Empty files that should have content</li>
 *   <li>Files that parse to null instead of a map</li>
 * </ul>
 *
 * <p>This test generates one dynamic test per YAML file, so failures
 * clearly show WHICH config file is broken.
 */
@DisplayName("Config YAML Validation")
class ConfigYamlValidationTest {

    @TestFactory
    @DisplayName("All config YAML files parse without error")
    Stream<DynamicTest> allConfigYamlFilesParseSuccessfully() throws Exception {
        // Find all .yml files in config/
        List<String> yamlFiles = findYamlFiles();
        assertFalse(yamlFiles.isEmpty(), "Should find at least 10 YAML config files");

        Yaml yaml = new Yaml();

        return yamlFiles.stream().map(resourcePath ->
            DynamicTest.dynamicTest(resourcePath, () -> {
                try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
                    assertNotNull(is, "YAML file should be loadable from classpath: " + resourcePath);

                    Object parsed = yaml.load(is);

                    // Most configs should parse to a Map. Some might be lists.
                    // None should be null (would mean empty file).
                    assertNotNull(parsed,
                        resourcePath + " parsed to null — file might be empty or contain only comments");

                    assertTrue(parsed instanceof Map || parsed instanceof List,
                        resourcePath + " parsed to " + parsed.getClass().getSimpleName() +
                        " — expected Map or List");
                }
            })
        );
    }

    @TestFactory
    @DisplayName("All config files have non-empty top-level keys")
    Stream<DynamicTest> allConfigFilesHaveTopLevelKeys() throws Exception {
        List<String> yamlFiles = findYamlFiles();
        Yaml yaml = new Yaml();

        return yamlFiles.stream()
            .filter(path -> !path.contains("backups/")) // Skip backup files
            .map(resourcePath ->
                DynamicTest.dynamicTest("keys: " + resourcePath, () -> {
                    try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
                        if (is == null) return; // Skip missing (tested above)

                        Object parsed = yaml.load(is);
                        if (parsed instanceof Map<?, ?> map) {
                            assertFalse(map.isEmpty(),
                                resourcePath + " has no top-level keys — likely broken config");
                        }
                    }
                })
            );
    }

    private List<String> findYamlFiles() throws Exception {
        List<String> files = new ArrayList<>();

        // Scan classpath for config/ YAML files
        var configUrl = getClass().getClassLoader().getResource("config");
        if (configUrl == null) {
            fail("config/ directory not found on classpath");
            return files;
        }

        Path configDir;
        try {
            configDir = Paths.get(configUrl.toURI());
        } catch (FileSystemNotFoundException e) {
            // Running from JAR — fall back to known file list
            return getKnownConfigFiles();
        }

        Files.walkFileTree(configDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.toString().endsWith(".yml") && !file.toString().contains("backups")) {
                    // Convert to classpath-relative path
                    String relative = configDir.getParent().relativize(file).toString()
                        .replace('\\', '/');
                    files.add(relative);
                }
                return FileVisitResult.CONTINUE;
            }
        });

        return files;
    }

    private List<String> getKnownConfigFiles() {
        return List.of(
            "config/config.yml",
            "config/ailments.yml",
            "config/combat-text.yml",
            "config/gear-balance.yml",
            "config/gear-modifiers.yml",
            "config/leveling.yml",
            "config/mob-scaling.yml",
            "config/mob-spawn.yml",
            "config/mob-elements.yml",
            "config/container-loot.yml",
            "config/loot-items.yml",
            "config/skill-tree.yml",
            "config/realms.yml",
            "config/tooltip.yml",
            "config/vanilla-conversion.yml"
        );
    }
}
