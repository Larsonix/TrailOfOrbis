package io.github.larsonix.trailoforbis.attributes.breakdown;

import io.github.larsonix.trailoforbis.attributes.AttributeCalculator;
import io.github.larsonix.trailoforbis.attributes.BaseStats;
import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.config.ConfigManager;
import io.github.larsonix.trailoforbis.config.RPGConfig;
import io.github.larsonix.trailoforbis.database.DataManager;
import io.github.larsonix.trailoforbis.database.models.PlayerData;
import io.github.larsonix.trailoforbis.database.repository.PlayerDataRepository;
import io.github.larsonix.trailoforbis.systems.StatProvider;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link StatBreakdownCalculator}.
 *
 * <p>Uses a real H2 database (same pattern as AttributeManagerTest) with
 * equipment armor disabled to avoid static Hytale API calls.
 * SkillTree, Gear, and Conditionals are tested via null providers
 * (those integrations require ServiceRegistry/mocking beyond unit scope).
 */
public class StatBreakdownCalculatorTest {

    @TempDir
    Path tempDir;

    private DataManager dataManager;
    private PlayerDataRepository repository;
    private StatBreakdownCalculator calculator;
    private RPGConfig config;

    @BeforeEach
    void setUp() {
        config = new RPGConfig();
        // Disable equipment armor to avoid static Hytale API calls in tests
        config.getArmor().setIncludeEquipmentArmor(false);

        ConfigManager configManager = new ConfigManager(tempDir, config);
        dataManager = new DataManager(tempDir, config);
        assertTrue(dataManager.initialize(), "DataManager must initialize for tests");

        repository = new PlayerDataRepository(dataManager);
        AttributeCalculator attrCalc = new AttributeCalculator(config);
        StatProvider testProvider = playerId -> BaseStats.defaults();

        // No gear, no conditionals for unit tests
        calculator = new StatBreakdownCalculator(
            attrCalc, configManager, testProvider, repository, null, null
        );
    }

    @AfterEach
    void tearDown() {
        if (dataManager != null) {
            dataManager.shutdown();
        }
    }

    // ==================== Base Snapshot Tests ====================

    @Test
    @DisplayName("Returns null for unknown player")
    void testNullForUnknownPlayer() {
        assertNull(calculator.calculate(UUID.randomUUID()));
    }

    @Test
    @DisplayName("Zero attributes: base matches vanilla defaults")
    void testZeroAttributes_baseMatchesVanilla() {
        UUID playerId = UUID.randomUUID();
        repository.create(playerId, "TestPlayer", 0);
        // All elements default to 0

        StatBreakdownResult result = calculator.calculate(playerId);
        assertNotNull(result);

        ComputedStats base = result.base();
        assertEquals(100f, base.getMaxHealth(), 0.01f, "Base health should be 100");
        assertEquals(0f, base.getMaxMana(), 0.01f, "Base mana should be 0");
        assertEquals(5f, base.getCriticalChance(), 0.01f, "Base crit chance should be 5%");
        assertEquals(150f, base.getCriticalMultiplier(), 0.01f, "Base crit multiplier should be 150%");
        assertEquals(10f, base.getAccuracy(), 0.01f, "Base accuracy should be 10");
        assertEquals(0f, base.getArmor(), 0.01f, "Base armor should be 0 (equipment disabled)");
    }

    @Test
    @DisplayName("Zero attributes: all snapshots are identical")
    void testZeroAttributes_allSnapshotsIdentical() {
        UUID playerId = UUID.randomUUID();
        repository.create(playerId, "TestPlayer", 0);

        StatBreakdownResult result = calculator.calculate(playerId);
        assertNotNull(result);

        // With zero attributes and no skill tree/gear/conditionals,
        // all snapshots should produce identical stats
        assertEquals(result.base().getMaxHealth(), result.afterAttributes().getMaxHealth(), 0.01f);
        assertEquals(result.base().getMaxHealth(), result.afterSkillTree().getMaxHealth(), 0.01f);
        assertEquals(result.base().getMaxHealth(), result.afterGear().getMaxHealth(), 0.01f);
        assertEquals(result.base().getMaxHealth(), result.afterConditionals().getMaxHealth(), 0.01f);
    }

    // ==================== Attribute Delta Tests ====================

    @Test
    @DisplayName("Known attributes: delta matches config grants for Fire")
    void testKnownAttributes_fireDelta() {
        UUID playerId = UUID.randomUUID();
        repository.create(playerId, "TestPlayer", 0);

        // Set fire to 50 points
        PlayerData data = repository.get(playerId).orElseThrow()
            .toBuilder().fire(50).build();
        repository.save(data);

        StatBreakdownResult result = calculator.calculate(playerId);
        assertNotNull(result);

        // Fire grants: physicalDamagePercent, chargedAttackDamagePercent, critMultiplier, burnDamagePercent, igniteChance
        float firePhysDmgPctGrant = config.getAttributes().getFireGrants().getPhysicalDamagePercent();
        float expectedPhysDmgPct = 50 * firePhysDmgPctGrant;

        // Base should have 0 physicalDamagePercent (zero attributes)
        assertEquals(0f, result.base().getPhysicalDamagePercent(), 0.01f);
        // After attributes should have the grant
        assertEquals(expectedPhysDmgPct, result.afterAttributes().getPhysicalDamagePercent(), 0.01f);
        // Delta = afterAttributes - base
        float delta = result.afterAttributes().getPhysicalDamagePercent() - result.base().getPhysicalDamagePercent();
        assertEquals(expectedPhysDmgPct, delta, 0.01f, "Fire delta should match 50 * grant");
    }

    @Test
    @DisplayName("Known attributes: delta matches config grants for Earth")
    void testKnownAttributes_earthDelta() {
        UUID playerId = UUID.randomUUID();
        repository.create(playerId, "TestPlayer", 0);

        PlayerData data = repository.get(playerId).orElseThrow()
            .toBuilder().earth(30).build();
        repository.save(data);

        StatBreakdownResult result = calculator.calculate(playerId);
        assertNotNull(result);

        float earthArmorGrant = config.getAttributes().getEarthGrants().getArmor();
        float expectedArmor = 30 * earthArmorGrant;

        assertEquals(0f, result.base().getArmor(), 0.01f, "Base armor should be 0");
        assertEquals(expectedArmor, result.afterAttributes().getArmor(), 0.01f);
    }

    @Test
    @DisplayName("Known attributes: delta matches config grants for Water (mana)")
    void testKnownAttributes_waterMana() {
        UUID playerId = UUID.randomUUID();
        repository.create(playerId, "TestPlayer", 0);

        PlayerData data = repository.get(playerId).orElseThrow()
            .toBuilder().water(20).build();
        repository.save(data);

        StatBreakdownResult result = calculator.calculate(playerId);
        assertNotNull(result);

        float waterManaGrant = config.getAttributes().getWaterGrants().getMaxMana();
        float expectedMana = BaseStats.defaults().getMaxMana() + (20 * waterManaGrant);

        // Base mana = vanilla base (0) + 0 * grant = 0
        assertEquals(BaseStats.defaults().getMaxMana(), result.base().getMaxMana(), 0.01f);
        assertEquals(expectedMana, result.afterAttributes().getMaxMana(), 0.01f);
    }

    @Test
    @DisplayName("Known attributes: crit chance uses base + lightning grant")
    void testKnownAttributes_lightningCritChance() {
        UUID playerId = UUID.randomUUID();
        repository.create(playerId, "TestPlayer", 0);

        PlayerData data = repository.get(playerId).orElseThrow()
            .toBuilder().lightning(40).build();
        repository.save(data);

        StatBreakdownResult result = calculator.calculate(playerId);
        assertNotNull(result);

        float lightningCritGrant = config.getAttributes().getLightningGrants().getCritChance();
        // Base crit = BASE_CRIT_CHANCE (5%) with zero lightning
        assertEquals(AttributeCalculator.BASE_CRIT_CHANCE, result.base().getCriticalChance(), 0.01f);
        // After attributes: BASE + 40 * grant
        float expected = AttributeCalculator.BASE_CRIT_CHANCE + (40 * lightningCritGrant);
        assertEquals(expected, result.afterAttributes().getCriticalChance(), 0.01f);
    }

    // ==================== No Skill Tree / Gear / Conditionals Tests ====================

    @Test
    @DisplayName("No skill tree: afterSkillTree equals afterAttributes")
    void testNoSkillTree_unchanged() {
        UUID playerId = UUID.randomUUID();
        repository.create(playerId, "TestPlayer", 0);

        PlayerData data = repository.get(playerId).orElseThrow()
            .toBuilder().fire(10).earth(20).build();
        repository.save(data);

        StatBreakdownResult result = calculator.calculate(playerId);
        assertNotNull(result);

        // Without ServiceRegistry having a SkillTreeService, afterSkillTree == afterAttributes
        assertEquals(result.afterAttributes(), result.afterSkillTree(),
            "afterSkillTree should equal afterAttributes when no skill tree service");
    }

    @Test
    @DisplayName("No gear: afterGear equals afterSkillTree (values)")
    void testNoGear_unchanged() {
        UUID playerId = UUID.randomUUID();
        repository.create(playerId, "TestPlayer", 0);

        PlayerData data = repository.get(playerId).orElseThrow()
            .toBuilder().fire(10).build();
        repository.save(data);

        StatBreakdownResult result = calculator.calculate(playerId);
        assertNotNull(result);

        // No gear provider → afterGear should be a copy of afterSkillTree with same values
        assertEquals(result.afterSkillTree().getMaxHealth(), result.afterGear().getMaxHealth(), 0.01f);
        assertEquals(result.afterSkillTree().getArmor(), result.afterGear().getArmor(), 0.01f);
        assertEquals(result.afterSkillTree().getCriticalChance(), result.afterGear().getCriticalChance(), 0.01f);
    }

    @Test
    @DisplayName("No conditionals: afterConditionals equals afterGear")
    void testNoConditionals_unchanged() {
        UUID playerId = UUID.randomUUID();
        repository.create(playerId, "TestPlayer", 0);

        StatBreakdownResult result = calculator.calculate(playerId);
        assertNotNull(result);

        // afterGear is a .copy() so it's a different object, but afterConditionals
        // references afterGear directly when condSystem is null
        assertEquals(result.afterGear().getMaxHealth(), result.afterConditionals().getMaxHealth(), 0.01f);
        assertEquals(result.afterGear().getCriticalChance(), result.afterConditionals().getCriticalChance(), 0.01f);
    }

    // ==================== Copy Safety Tests ====================

    @Test
    @DisplayName("Gear step deep copies afterSkillTree (mutation isolation)")
    void testGearDoesNotMutateAfterSkillTree() {
        UUID playerId = UUID.randomUUID();
        repository.create(playerId, "TestPlayer", 0);

        PlayerData data = repository.get(playerId).orElseThrow()
            .toBuilder().earth(25).build();
        repository.save(data);

        StatBreakdownResult result = calculator.calculate(playerId);
        assertNotNull(result);

        // afterGear is a copy of afterSkillTree — mutating one shouldn't affect the other
        // (Even though no gear provider is set, the copy still happens)
        assertNotSame(result.afterSkillTree(), result.afterGear(),
            "afterGear should be a deep copy, not the same object as afterSkillTree");
    }

    // ==================== Multi-Element Tests ====================

    @Test
    @DisplayName("Multiple elements: each contributes independently")
    void testMultipleElements_independentContributions() {
        UUID playerId = UUID.randomUUID();
        repository.create(playerId, "TestPlayer", 0);

        PlayerData data = repository.get(playerId).orElseThrow()
            .toBuilder().fire(20).earth(15).lightning(10).build();
        repository.save(data);

        StatBreakdownResult result = calculator.calculate(playerId);
        assertNotNull(result);

        // Fire → physicalDamagePercent
        float firePdp = config.getAttributes().getFireGrants().getPhysicalDamagePercent();
        assertEquals(20 * firePdp, result.afterAttributes().getPhysicalDamagePercent(), 0.01f);

        // Earth → armor
        float earthArmor = config.getAttributes().getEarthGrants().getArmor();
        assertEquals(15 * earthArmor, result.afterAttributes().getArmor(), 0.01f);

        // Lightning → attack speed
        float lightningAs = config.getAttributes().getLightningGrants().getAttackSpeedPercent();
        assertEquals(10 * lightningAs, result.afterAttributes().getAttackSpeedPercent(), 0.01f);

        // All should be 0 in base (zero attributes)
        assertEquals(0f, result.base().getPhysicalDamagePercent(), 0.01f);
        assertEquals(0f, result.base().getArmor(), 0.01f);
        assertEquals(0f, result.base().getAttackSpeedPercent(), 0.01f);
    }
}
