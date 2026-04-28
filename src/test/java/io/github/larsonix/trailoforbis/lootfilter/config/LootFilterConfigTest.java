package io.github.larsonix.trailoforbis.lootfilter.config;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that loot-filter.yml parses correctly via SnakeYAML loadAs(),
 * matching the exact loading path used by ConfigManager.
 */
class LootFilterConfigTest {

    @Test
    void yamlParsesWithoutWrapper() {
        Yaml yaml = new Yaml(new Constructor(LootFilterConfig.class, new LoaderOptions()));
        InputStream input = getClass().getClassLoader().getResourceAsStream("config/loot-filter.yml");
        assertNotNull(input, "loot-filter.yml should be on classpath");

        LootFilterConfig config = yaml.loadAs(input, LootFilterConfig.class);

        assertNotNull(config);
        assertTrue(config.isEnabled());
        assertEquals(10, config.getMaxProfilesPerPlayer());
        assertEquals(50, config.getMaxRulesPerProfile());
        assertEquals(50, config.getMaxConditionsPerRule());
    }

    @Test
    void presetsLoadCorrectly() {
        Yaml yaml = new Yaml(new Constructor(LootFilterConfig.class, new LoaderOptions()));
        InputStream input = getClass().getClassLoader().getResourceAsStream("config/loot-filter.yml");
        LootFilterConfig config = yaml.loadAs(input, LootFilterConfig.class);

        assertEquals(3, config.getPresets().size(), "Should load 3 starter presets");
        assertEquals("Block Common", config.getPresets().get(0).getName());
        assertEquals("Rare+ Only", config.getPresets().get(1).getName());
        assertEquals("Epic+ Endgame", config.getPresets().get(2).getName());
    }

    @Test
    void defaultsLoadCorrectly() {
        Yaml yaml = new Yaml(new Constructor(LootFilterConfig.class, new LoaderOptions()));
        InputStream input = getClass().getClassLoader().getResourceAsStream("config/loot-filter.yml");
        LootFilterConfig config = yaml.loadAs(input, LootFilterConfig.class);

        assertNotNull(config.getDefaults());
        assertFalse(config.getDefaults().isFilteringEnabled());
    }

    @Test
    void feedbackLoadCorrectly() {
        Yaml yaml = new Yaml(new Constructor(LootFilterConfig.class, new LoaderOptions()));
        InputStream input = getClass().getClassLoader().getResourceAsStream("config/loot-filter.yml");
        LootFilterConfig config = yaml.loadAs(input, LootFilterConfig.class);

        assertNotNull(config.getFeedback());
        assertEquals("chat", config.getFeedback().getMode());
        assertEquals("summary", config.getFeedback().getDetail());
        assertEquals(5, config.getFeedback().getSummaryInterval());
    }

    @Test
    void categoryOverridesLoadCorrectly() {
        Yaml yaml = new Yaml(new Constructor(LootFilterConfig.class, new LoaderOptions()));
        InputStream input = getClass().getClassLoader().getResourceAsStream("config/loot-filter.yml");
        LootFilterConfig config = yaml.loadAs(input, LootFilterConfig.class);

        assertEquals(3, config.getCategoryOverrides().size());
        assertEquals("RESOURCES", config.getCategoryOverrides().get("of_stability"));
        assertEquals("RESOURCES", config.getCategoryOverrides().get("of_fortitude"));
        assertEquals("MOVEMENT", config.getCategoryOverrides().get("of_the_cat"));
    }
}
