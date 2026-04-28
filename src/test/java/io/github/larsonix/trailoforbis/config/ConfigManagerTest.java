package io.github.larsonix.trailoforbis.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ConfigManager.
 *
 * <p>Tests loading of the elemental attribute system configuration.
 */
public class ConfigManagerTest {
    @TempDir
    Path tempDir;

    private ConfigManager configManager;

    @BeforeEach
    public void setUp() {
        configManager = new ConfigManager(tempDir);
    }

    @AfterEach
    public void tearDown() {
        configManager = null;
    }

    @Test
    public void testDefaultConfigLoads() {
        // Test that default config loads successfully
        assertTrue(configManager.loadConfigs(), "Config should load successfully");

        // Verify config is not null
        assertNotNull(configManager.getConfig(), "Config should not be null");
        assertNotNull(configManager.getRPGConfig(), "RPGConfig should not be null");
    }

    @Test
    public void testDefaultDatabaseConfig() {
        // Load config
        assertTrue(configManager.loadConfigs());

        // Verify database defaults
        RPGConfig.DatabaseConfig dbConfig = configManager.getConfig().getDatabase();
        assertNotNull(dbConfig, "Database config should not be null");
        assertEquals("H2", dbConfig.getType(), "Default database type should be H2");
        assertEquals("localhost", dbConfig.getHost());
        assertEquals(3306, dbConfig.getPort());
        assertEquals("trailoforbis", dbConfig.getDatabase());
        assertEquals(30, dbConfig.getPoolSize());
    }

    @Test
    public void testElementalAttributeGrants() {
        // Load config
        assertTrue(configManager.loadConfigs());

        // Verify elemental attribute conversion defaults
        RPGConfig.AttributeConfig attrConfig = configManager.getConfig().getAttributes();
        assertNotNull(attrConfig, "Attribute config should not be null");

        // Test FIRE grants (glass cannon)
        RPGConfig.AttributeConfig.FireGrants fireGrants = attrConfig.getFireGrants();
        assertNotNull(fireGrants);
        assertEquals(0.4f, fireGrants.getPhysicalDamagePercent(), 0.001f);
        assertEquals(0.3f, fireGrants.getChargedAttackDamagePercent(), 0.001f);
        assertEquals(0.6f, fireGrants.getCriticalMultiplier(), 0.001f);
        assertEquals(0.4f, fireGrants.getBurnDamagePercent(), 0.001f);
        assertEquals(0.1f, fireGrants.getIgniteChance(), 0.001f);

        // Test WATER grants (arcane mage)
        RPGConfig.AttributeConfig.WaterGrants waterGrants = attrConfig.getWaterGrants();
        assertNotNull(waterGrants);
        assertEquals(0.5f, waterGrants.getSpellDamagePercent(), 0.001f);
        assertEquals(1.5f, waterGrants.getMaxMana(), 0.001f);
        assertEquals(2.0f, waterGrants.getEnergyShield(), 0.001f);
        assertEquals(0.15f, waterGrants.getManaRegen(), 0.001f);
        assertEquals(0.1f, waterGrants.getFreezeChance(), 0.001f);

        // Test LIGHTNING grants (storm blitz)
        RPGConfig.AttributeConfig.LightningGrants lightningGrants = attrConfig.getLightningGrants();
        assertNotNull(lightningGrants);
        assertEquals(0.3f, lightningGrants.getAttackSpeedPercent(), 0.001f);
        assertEquals(0.15f, lightningGrants.getMoveSpeedPercent(), 0.001f);
        assertEquals(0.1f, lightningGrants.getCritChance(), 0.001f);
        assertEquals(0.1f, lightningGrants.getStaminaRegen(), 0.001f);
        assertEquals(0.1f, lightningGrants.getShockChance(), 0.001f);

        // Test EARTH grants (iron fortress)
        RPGConfig.AttributeConfig.EarthGrants earthGrants = attrConfig.getEarthGrants();
        assertNotNull(earthGrants);
        assertEquals(0.5f, earthGrants.getMaxHealthPercent(), 0.001f);
        assertEquals(5.0f, earthGrants.getArmor(), 0.001f);
        assertEquals(0.2f, earthGrants.getHealthRegen(), 0.001f);
        assertEquals(0.2f, earthGrants.getPassiveBlockChance(), 0.001f);
        assertEquals(0.3f, earthGrants.getKnockbackResistance(), 0.001f);

        // Test WIND grants (ghost ranger)
        RPGConfig.AttributeConfig.WindGrants windGrants = attrConfig.getWindGrants();
        assertNotNull(windGrants);
        assertEquals(5.0f, windGrants.getEvasion(), 0.001f);
        assertEquals(3.0f, windGrants.getAccuracy(), 0.001f);
        assertEquals(0.5f, windGrants.getProjectileDamagePercent(), 0.001f);
        assertEquals(0.15f, windGrants.getJumpForcePercent(), 0.001f);
        assertEquals(0.3f, windGrants.getProjectileSpeedPercent(), 0.001f);

        // Test VOID grants (life devourer)
        RPGConfig.AttributeConfig.VoidGrants voidGrants = attrConfig.getVoidGrants();
        assertNotNull(voidGrants);
        assertEquals(0.1f, voidGrants.getLifeSteal(), 0.001f);
        assertEquals(0.05f, voidGrants.getPercentHitAsTrueDamage(), 0.001f);
        assertEquals(0.3f, voidGrants.getDotDamagePercent(), 0.001f);
        assertEquals(0.5f, voidGrants.getManaOnKill(), 0.001f);
        assertEquals(0.3f, voidGrants.getStatusEffectDuration(), 0.001f);
    }

    @Test
    public void testConfigFileCreation() throws Exception {
        // Load config (should create default file)
        assertTrue(configManager.loadConfigs());

        // Verify config file was created in the config/ subfolder
        Path configFile = tempDir.resolve("config").resolve("config.yml");
        assertTrue(Files.exists(configFile), "Config file should be created in config/ subfolder");
        assertTrue(Files.size(configFile) > 0, "Config file should not be empty");
    }

    @Test
    public void testReloadConfigs() {
        // Load initial config
        assertTrue(configManager.loadConfigs());
        RPGConfig firstLoad = configManager.getConfig();
        assertNotNull(firstLoad);

        // Reload configs
        assertTrue(configManager.reloadConfigs(), "Reload should succeed");

        // Verify config is still accessible
        RPGConfig secondLoad = configManager.getConfig();
        assertNotNull(secondLoad);

        // Both should have same values (since we didn't modify the file)
        assertEquals(firstLoad.getDatabase().getType(), secondLoad.getDatabase().getType());
    }

    @Test
    public void testGeneralSettings() {
        // Load config
        assertTrue(configManager.loadConfigs());

        RPGConfig config = configManager.getConfig();
        assertNotNull(config);

        // Test general settings defaults
        assertFalse(config.isDebugMode(), "Debug mode should be false by default");
        assertEquals("en_US", config.getLanguage(), "Default language should be en_US");
    }

    @Test
    public void testAttributePointSettings() {
        // Load config
        assertTrue(configManager.loadConfigs());

        RPGConfig.AttributeConfig attrConfig = configManager.getConfig().getAttributes();
        assertNotNull(attrConfig);

        // Test attribute point settings
        assertEquals(0, attrConfig.getStartingPoints(), "Starting points should be 0");
        assertEquals(1, attrConfig.getPointsPerLevel(), "Points per level should be 1");
    }
}
