package io.github.larsonix.trailoforbis.mobs.stats;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MobStatGeneratorTest {
    private MobStatGenerator generator;
    private MobStatPoolConfig config;

    @BeforeEach
    void setUp() {
        config = MobStatPoolConfig.createDefaults();
        generator = new MobStatGenerator(config);
    }

    @Test
    void testGenerateReturnsNonNull() {
        MobStatProfile profile = generator.generate(10, 50.0, 12345L);
        assertNotNull(profile);
    }

    @Test
    void testMobLevelSetCorrectly() {
        int level = 25;
        MobStatProfile profile = generator.generate(level, 100.0, 12345L);
        assertEquals(level, profile.mobLevel());
    }

    @Test
    void testTotalPoolCalculatedCorrectly() {
        int level = 20;
        double distanceBonus = 150.0;

        // Pool uses capped effective level: (effectiveLevel * pointsPerLevel * scalingFactor + distanceBonus) * expMultiplier
        double scalingFactor = config.calculateScalingFactor(level);
        double expMultiplier = io.github.larsonix.trailoforbis.util.LevelScaling.getMultiplier(level);
        // effectiveLevel maps the capped multiplier back to a level-equivalent value
        double effectiveLevel = (expMultiplier - 1.0)
                * io.github.larsonix.trailoforbis.util.LevelScaling.getTransitionLevel() + 1.0;
        double levelPool = effectiveLevel * config.getPointsPerLevel() * scalingFactor;
        double expectedPool = (levelPool + distanceBonus) * expMultiplier;

        MobStatProfile profile = generator.generate(level, distanceBonus, 12345L);
        assertEquals(expectedPool, profile.totalPool(), 0.001);
    }

    @Test
    void testUnscaledProfileConstant() {
        MobStatProfile unscaled = MobStatProfile.UNSCALED;
        assertEquals(1, unscaled.mobLevel());
        assertEquals(0.0, unscaled.totalPool(), 0.001);
        assertEquals(50.0, unscaled.maxHealth(), 0.001);
        assertEquals(5.0, unscaled.physicalDamage(), 0.001);
        assertEquals(1.0, unscaled.moveSpeed(), 0.001);
    }

    @Test
    void testIsScaledWhenLevelGreaterThanOne() {
        MobStatProfile scaled = generator.generate(10, 0.0, 12345L);
        assertTrue(scaled.isScaled());
    }

    @Test
    void testIsScaledWhenTotalPoolGreaterThanZero() {
        MobStatProfile scaled = generator.generate(1, 100.0, 12345L);
        assertTrue(scaled.isScaled());
    }

    @Test
    void testIsNotScaledWhenLevelOneAndNoPool() {
        MobStatProfile unscaled = MobStatProfile.UNSCALED;
        assertFalse(unscaled.isScaled());
    }

    @Test
    void testBossMultiplierIncreasesStats() {
        double multiplier = 2.0;
        MobStatProfile base = generator.generate(10, 50.0, 12345L);
        MobStatProfile boss = base.withBossMultiplier(multiplier);

        assertEquals(base.mobLevel(), boss.mobLevel());
        assertEquals(base.totalPool() * multiplier, boss.totalPool(), 0.001);
        assertTrue(boss.maxHealth() > base.maxHealth());
        assertTrue(boss.physicalDamage() > base.physicalDamage());
        assertTrue(boss.healthRegen() > base.healthRegen());
    }

    @Test
    void testCriticalChanceIsWithinBounds() {
        MobStatProfile profile = generator.generate(50, 500.0, 12345L);
        assertTrue(profile.criticalChance() >= 0.0);
        assertTrue(profile.criticalChance() <= 100.0);
    }

    @Test
    void testMoveSpeedIsWithinBounds() {
        MobStatProfile profile = generator.generate(50, 500.0, 12345L);
        StatConfig speedConfig = config.getStatConfig(MobStatType.MOVE_SPEED);
        assertTrue(profile.moveSpeed() >= speedConfig.getMinValue());
        assertTrue(profile.moveSpeed() <= speedConfig.getMaxValue());
    }

    @Test
    void testToComputedStats() {
        MobStatProfile profile = generator.generate(20, 100.0, 12345L);
        assertNotNull(profile.toComputedStats());
    }

    @Test
    void testSameSeedProducesSameResults() {
        long seed = 99999L;
        MobStatProfile profile1 = generator.generate(15, 75.0, seed);
        MobStatProfile profile2 = generator.generate(15, 75.0, seed);

        assertEquals(profile1.mobLevel(), profile2.mobLevel());
        assertEquals(profile1.totalPool(), profile2.totalPool(), 0.001);
        assertEquals(profile1.maxHealth(), profile2.maxHealth(), 0.001);
        assertEquals(profile1.physicalDamage(), profile2.physicalDamage(), 0.001);
    }

    @Test
    void testDifferentSeedsProduceDifferentResults() {
        MobStatProfile profile1 = generator.generate(15, 75.0, 11111L);
        MobStatProfile profile2 = generator.generate(15, 75.0, 22222L);

        assertNotEquals(profile1.maxHealth(), profile2.maxHealth(), 0.001);
    }

    @Test
    void testGetBaseStatsIsBalanced() {
        int level = 30;
        MobStatProfile baseStats = generator.getBaseStats(level);

        assertEquals(level, baseStats.mobLevel());
        assertTrue(baseStats.isScaled());
    }

    @Test
    void testGetEffectivePower() {
        MobStatProfile profile = generator.generate(50, 300.0, 12345L);
        int power = profile.getEffectivePower();

        assertTrue(power > 0);
        assertTrue(power >= profile.mobLevel());
    }

    @Test
    void testElementalDamageExists() {
        MobStatProfile profile = generator.generate(50, 500.0, 12345L);
        assertTrue(profile.fireDamage() >= 0.0);
        assertTrue(profile.waterDamage() >= 0.0);
        assertTrue(profile.lightningDamage() >= 0.0);
        assertTrue(profile.voidDamage() >= 0.0);
    }

    @Test
    void testElementalResistanceExists() {
        MobStatProfile profile = generator.generate(50, 500.0, 12345L);
        assertTrue(profile.fireResistance() >= 0.0);
        assertTrue(profile.waterResistance() >= 0.0);
        assertTrue(profile.lightningResistance() >= 0.0);
        assertTrue(profile.voidResistance() >= 0.0);
    }

    @Test
    void testDefenseStatsExist() {
        MobStatProfile profile = generator.generate(50, 500.0, 12345L);
        assertTrue(profile.dodgeChance() >= 0.0);
        assertTrue(profile.blockChance() >= 0.0);
        assertTrue(profile.parryChance() >= 0.0);
    }

    @Test
    void testMovementStatsExist() {
        MobStatProfile profile = generator.generate(50, 500.0, 12345L);
        assertTrue(profile.moveSpeed() > 0.0);
        assertTrue(profile.attackSpeed() > 0.0);
        assertTrue(profile.attackRange() > 0.0);
        assertTrue(profile.attackCooldown() > 0.0);
    }

    @Test
    void testAIStatsExist() {
        MobStatProfile profile = generator.generate(50, 500.0, 12345L);
        assertTrue(profile.aggroRange() > 0.0);
        assertTrue(profile.reactionDelay() > 0.0);
        assertTrue(profile.chargeTime() > 0.0);
        assertTrue(profile.chargeDistance() > 0.0);
    }

    @Test
    void testSpecialStatsExist() {
        MobStatProfile profile = generator.generate(50, 500.0, 12345L);
        assertTrue(profile.armorPenetration() >= 0.0);
        assertTrue(profile.trueDamage() >= 0.0);
    }

    @Test
    void testGetTotalElementalDamage() {
        MobStatProfile profile = generator.generate(50, 500.0, 12345L);
        double total = profile.getTotalElementalDamage();
        assertEquals(profile.fireDamage() + profile.waterDamage() +
                     profile.lightningDamage() + profile.voidDamage(), total, 0.001);
    }

    @Test
    void testGetScaledDamage() {
        MobStatProfile profile = generator.generate(20, 100.0, 12345L);
        double baseDamage = 10.0;
        double scaled = profile.getScaledDamage(baseDamage);
        assertEquals(baseDamage * profile.physicalDamage(), scaled, 0.001);
    }

    @Test
    void testGetEffectiveCooldown() {
        MobStatProfile profile = generator.generate(20, 100.0, 12345L);
        double baseCooldown = 2.0;
        double effective = profile.getEffectiveCooldown(baseCooldown);
        assertEquals(baseCooldown / profile.attackSpeed(), effective, 0.001);
    }
}
