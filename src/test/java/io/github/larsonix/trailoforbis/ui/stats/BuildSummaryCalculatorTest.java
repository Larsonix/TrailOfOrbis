package io.github.larsonix.trailoforbis.ui.stats;

import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.config.RPGConfig;
import io.github.larsonix.trailoforbis.mobs.stats.MobStatPoolConfig;
import io.github.larsonix.trailoforbis.ui.stats.BuildSummaryCalculator.BuildSummary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link BuildSummaryCalculator}.
 *
 * <p>Tests the two core derived values:
 * <ul>
 *   <li>Average Damage Per Hit — mirrors RPGDamageCalculator steps 1-8 with expected crit</li>
 *   <li>Effective HP — armor mitigation + avoidance layers</li>
 * </ul>
 */
public class BuildSummaryCalculatorTest {

    // ═══════════════════════════════════════════════════════════════════
    // AVERAGE DAMAGE PER HIT
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Average Damage Per Hit")
    class AvgDamageTests {

        @Test
        @DisplayName("No weapon → hasWeapon=false, avgDamage=0")
        void noWeapon() {
            ComputedStats stats = ComputedStats.builder()
                    .weaponBaseDamage(0f)
                    .holdingRpgGear(false)
                    .criticalChance(0f)
                    .criticalMultiplier(100f)
                    .build();

            BuildSummary summary = BuildSummaryCalculator.compute(stats, 10, null, null);

            assertFalse(summary.hasWeapon());
            assertEquals(0f, summary.avgDamagePerHit(), 0.01f);
        }

        @Test
        @DisplayName("Weapon base only: 100 base × 1.0 × 1.0 × 1.0 = 100")
        void weaponBaseOnly() {
            ComputedStats stats = ComputedStats.builder()
                    .weaponBaseDamage(100f)
                    .holdingRpgGear(true)
                    .criticalChance(0f)
                    .criticalMultiplier(100f)
                    .build();

            BuildSummary summary = BuildSummaryCalculator.compute(stats, 10, null, null);

            assertTrue(summary.hasWeapon());
            assertEquals(100f, summary.avgDamagePerHit(), 0.01f);
        }

        @Test
        @DisplayName("Base + flat physical + flat melee: 100 + 30 + 10 = 140")
        void basePlusFlatDamage() {
            ComputedStats stats = ComputedStats.builder()
                    .weaponBaseDamage(100f)
                    .holdingRpgGear(true)
                    .physicalDamage(30f)
                    .meleeDamage(10f)
                    .criticalChance(0f)
                    .criticalMultiplier(100f)
                    .build();

            BuildSummary summary = BuildSummaryCalculator.compute(stats, 10, null, null);

            assertEquals(140f, summary.avgDamagePerHit(), 0.01f);
            assertEquals(140f, summary.damageDetail().baseTotal(), 0.01f);
        }

        @Test
        @DisplayName("Full pipeline: 100 base + 20 flat + 50% increased + 10% more + 20% crit @ 200%")
        void fullPipeline() {
            // Base total: 100 + 20 = 120
            // After increased: 120 × 1.5 = 180
            // After more: 180 × 1.1 = 198
            // Expected crit: 198 × (1 + 0.2 × (2.0 - 1.0)) = 198 × 1.2 = 237.6
            ComputedStats stats = ComputedStats.builder()
                    .weaponBaseDamage(100f)
                    .holdingRpgGear(true)
                    .physicalDamage(20f)
                    .physicalDamagePercent(50f)
                    .allDamagePercent(10f)
                    .criticalChance(20f)
                    .criticalMultiplier(200f)
                    .build();

            BuildSummary summary = BuildSummaryCalculator.compute(stats, 10, null, null);

            assertEquals(237.6f, summary.avgDamagePerHit(), 0.5f);
        }

        @Test
        @DisplayName("High crit: 100% chance × 300% multiplier → ×3.0 expected")
        void highCrit() {
            // Base: 100, no modifiers
            // Expected crit: 100 × (1 + 1.0 × (3.0 - 1.0)) = 100 × 3.0 = 300
            ComputedStats stats = ComputedStats.builder()
                    .weaponBaseDamage(100f)
                    .holdingRpgGear(true)
                    .criticalChance(100f)
                    .criticalMultiplier(300f)
                    .build();

            BuildSummary summary = BuildSummaryCalculator.compute(stats, 10, null, null);

            assertEquals(300f, summary.avgDamagePerHit(), 0.5f);
        }

        @Test
        @DisplayName("% Increased stacks additively: phys 30% + melee 20% + dmg 10% = 60%")
        void increasedStacksAdditively() {
            // Base: 100
            // Increased: 100 × (1 + 60/100) = 100 × 1.6 = 160
            ComputedStats stats = ComputedStats.builder()
                    .weaponBaseDamage(100f)
                    .holdingRpgGear(true)
                    .physicalDamagePercent(30f)
                    .meleeDamagePercent(20f)
                    .damagePercent(10f)
                    .criticalChance(0f)
                    .criticalMultiplier(100f)
                    .build();

            BuildSummary summary = BuildSummaryCalculator.compute(stats, 10, null, null);

            assertEquals(160f, summary.avgDamagePerHit(), 0.5f);
            assertEquals(60f, summary.damageDetail().totalIncreasedPct(), 0.01f);
        }

        @Test
        @DisplayName("More multipliers stack multiplicatively: 20% all + 15% mult")
        void moreMultiplicative() {
            // Base: 100
            // More: 100 × (1.2) × (1.15) = 100 × 1.38 = 138
            ComputedStats stats = ComputedStats.builder()
                    .weaponBaseDamage(100f)
                    .holdingRpgGear(true)
                    .allDamagePercent(20f)
                    .damageMultiplier(15f)
                    .criticalChance(0f)
                    .criticalMultiplier(100f)
                    .build();

            BuildSummary summary = BuildSummaryCalculator.compute(stats, 10, null, null);

            assertEquals(138f, summary.avgDamagePerHit(), 0.5f);
        }

        @Test
        @DisplayName("Holding RPG gear with 0 weapon base still applies flat adds")
        void holdingRpgGearNoBase() {
            // weaponBase=0 but isHoldingRpgGear=true → still compute from flats
            // Base total: 0 + 50 = 50
            ComputedStats stats = ComputedStats.builder()
                    .weaponBaseDamage(0f)
                    .holdingRpgGear(true)
                    .physicalDamage(50f)
                    .criticalChance(0f)
                    .criticalMultiplier(100f)
                    .build();

            BuildSummary summary = BuildSummaryCalculator.compute(stats, 10, null, null);

            assertTrue(summary.hasWeapon());
            assertEquals(50f, summary.avgDamagePerHit(), 0.5f);
        }

        @Test
        @DisplayName("Zero stats → all zero but hasWeapon true if holding RPG gear")
        void zeroStatsHoldingGear() {
            ComputedStats stats = ComputedStats.builder()
                    .weaponBaseDamage(0f)
                    .holdingRpgGear(true)
                    .criticalChance(0f)
                    .criticalMultiplier(100f)
                    .build();

            BuildSummary summary = BuildSummaryCalculator.compute(stats, 10, null, null);

            assertTrue(summary.hasWeapon());
            assertEquals(0f, summary.avgDamagePerHit(), 0.01f);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // EFFECTIVE HP
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Effective HP")
    class EffectiveHPTests {

        @Test
        @DisplayName("Health only: 1000 HP → EHP = 1000")
        void healthOnly() {
            ComputedStats stats = ComputedStats.builder()
                    .maxHealth(1000f)
                    .build();

            BuildSummary summary = BuildSummaryCalculator.compute(stats, 10, null, null);

            assertEquals(1000f, summary.effectiveHP(), 1f);
            assertEquals(1000f, summary.ehpDetail().rawHP(), 0.01f);
        }

        @Test
        @DisplayName("Health + Energy Shield: 800 HP + 200 Shield → 1000 raw")
        void healthPlusShield() {
            ComputedStats stats = ComputedStats.builder()
                    .maxHealth(800f)
                    .energyShield(200f)
                    .build();

            BuildSummary summary = BuildSummaryCalculator.compute(stats, 10, null, null);

            assertEquals(1000f, summary.ehpDetail().rawHP(), 0.01f);
            assertEquals(1000f, summary.effectiveHP(), 1f); // no armor/avoidance
        }

        @Test
        @DisplayName("Armor mitigation: 1000 armor → 50% reduction → doubles EHP")
        void armorMitigation() {
            // armor / (armor + 1000) = 1000 / 2000 = 0.5
            // EHP = 1000 / (1 - 0.5) = 2000
            ComputedStats stats = ComputedStats.builder()
                    .maxHealth(1000f)
                    .armor(1000f)
                    .build();

            BuildSummary summary = BuildSummaryCalculator.compute(stats, 10, null, null);

            assertEquals(0.5f, summary.ehpDetail().armorMitigation(), 0.001f);
            assertEquals(2000f, summary.effectiveHP(), 1f);
        }

        @Test
        @DisplayName("High armor: 9000 armor → 90% reduction (cap)")
        void highArmorCap() {
            // armor / (armor + 1000) = 9000 / 10000 = 0.9 (exactly at cap)
            // EHP = 1000 / (1 - 0.9) = 10000
            ComputedStats stats = ComputedStats.builder()
                    .maxHealth(1000f)
                    .armor(9000f)
                    .build();

            BuildSummary summary = BuildSummaryCalculator.compute(stats, 10, null, null);

            assertEquals(0.9f, summary.ehpDetail().armorMitigation(), 0.001f);
            assertEquals(10000f, summary.effectiveHP(), 10f);
        }

        @Test
        @DisplayName("Extreme armor: 99000 armor → clamped at 90% reduction")
        void extremeArmorClamped() {
            // armor / (armor + 1000) = 99000 / 100000 = 0.99 → clamped to 0.9
            ComputedStats stats = ComputedStats.builder()
                    .maxHealth(1000f)
                    .armor(99000f)
                    .build();

            BuildSummary summary = BuildSummaryCalculator.compute(stats, 10, null, null);

            assertEquals(0.9f, summary.ehpDetail().armorMitigation(), 0.001f);
            assertEquals(10000f, summary.effectiveHP(), 10f);
        }

        @Test
        @DisplayName("Dodge avoidance: 50% dodge → EHP doubles")
        void dodgeAvoidance() {
            // No armor, 50% dodge
            // combinedAvoid = 0.5
            // EHP = 1000 / (1 - 0.5) = 2000
            ComputedStats stats = ComputedStats.builder()
                    .maxHealth(1000f)
                    .dodgeChance(50f) // 50%
                    .build();

            BuildSummary summary = BuildSummaryCalculator.compute(stats, 10, null, null);

            assertEquals(50f, summary.ehpDetail().dodgeChancePct(), 0.1f);
            assertEquals(2000f, summary.effectiveHP(), 10f);
        }

        @Test
        @DisplayName("Dodge clamped at 75%")
        void dodgeClamped() {
            ComputedStats stats = ComputedStats.builder()
                    .maxHealth(1000f)
                    .dodgeChance(90f) // Would be 90% but clamped to 75%
                    .build();

            BuildSummary summary = BuildSummaryCalculator.compute(stats, 10, null, null);

            assertEquals(75f, summary.ehpDetail().dodgeChancePct(), 0.1f);
        }

        @Test
        @DisplayName("Combined armor + dodge: 1000 armor (50%) + 25% dodge")
        void armorPlusDodge() {
            // Armor: 50% mitigation → rawHP 1000 → ehpFromArmor 2000
            // Dodge: 25% avoidance → EHP = 2000 / (1 - 0.25) = 2666.67
            ComputedStats stats = ComputedStats.builder()
                    .maxHealth(1000f)
                    .armor(1000f)
                    .dodgeChance(25f)
                    .build();

            BuildSummary summary = BuildSummaryCalculator.compute(stats, 10, null, null);

            assertEquals(2666.67f, summary.effectiveHP(), 5f);
        }

        @Test
        @DisplayName("Combined avoidance capped at 95%")
        void combinedAvoidanceCapped() {
            // Dodge 75% + Parry 50% (passive block removed — feeds perfect block now)
            // 1 - (1-0.75)(1-0.5) = 1 - 0.125 = 0.875 = 87.5%
            ComputedStats stats = ComputedStats.builder()
                    .maxHealth(1000f)
                    .dodgeChance(75f) // clamped to 75%
                    .parryChance(50f) // clamped to 50%
                    .build();

            BuildSummary summary = BuildSummaryCalculator.compute(stats, 10, null, null);

            assertEquals(87.5f, summary.ehpDetail().combinedAvoidPct(), 0.5f);
            // EHP = 1000 / (1 - 0.875) = 8000
            assertEquals(8000f, summary.effectiveHP(), 200f);
        }

        @Test
        @DisplayName("Parry clamped at 50%")
        void parryClamped() {
            ComputedStats stats = ComputedStats.builder()
                    .maxHealth(1000f)
                    .parryChance(80f) // Would be 80% but clamped to 50%
                    .build();

            BuildSummary summary = BuildSummaryCalculator.compute(stats, 10, null, null);

            assertEquals(50f, summary.ehpDetail().parryAvoidPct(), 0.1f);
        }

        @Test
        @DisplayName("Evasion with config: non-zero evasion produces avoidance")
        void evasionWithConfig() {
            ComputedStats stats = ComputedStats.builder()
                    .maxHealth(1000f)
                    .evasion(200f) // Some evasion
                    .build();

            // Use default configs — evasion should produce non-zero avoidance
            RPGConfig.CombatConfig.EvasionConfig evasionCfg = new RPGConfig.CombatConfig.EvasionConfig();
            MobStatPoolConfig poolConfig = new MobStatPoolConfig();

            BuildSummary summary = BuildSummaryCalculator.compute(stats, 10, evasionCfg, poolConfig);

            assertTrue(summary.ehpDetail().evasionAvoidPct() > 0,
                    "Evasion should produce positive avoidance with default config");
            assertTrue(summary.effectiveHP() > 1000f,
                    "EHP should be greater than raw HP when evasion is active");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // INTEGRATION: DAMAGE + EHP TOGETHER
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Full Build Summary")
    class FullSummaryTests {

        @Test
        @DisplayName("Complete build: weapon + modifiers + armor + dodge")
        void completeBuild() {
            ComputedStats stats = ComputedStats.builder()
                    .weaponBaseDamage(120f)
                    .holdingRpgGear(true)
                    .physicalDamage(45f)
                    .meleeDamage(12f)
                    .physicalDamagePercent(32f)
                    .allDamagePercent(12f)
                    .criticalChance(15f)
                    .criticalMultiplier(175f)
                    .maxHealth(2400f)
                    .energyShield(200f)
                    .armor(1200f)
                    .dodgeChance(10f)
                    .build();

            BuildSummary summary = BuildSummaryCalculator.compute(stats, 50, null, null);

            assertTrue(summary.hasWeapon());
            assertTrue(summary.avgDamagePerHit() > 0);
            assertTrue(summary.effectiveHP() > 2600); // Must exceed raw HP
            assertNotNull(summary.damageDetail());
            assertNotNull(summary.ehpDetail());

            // Verify breakdown detail fields are populated
            assertEquals(120f, summary.damageDetail().weaponBase(), 0.01f);
            assertEquals(45f, summary.damageDetail().flatPhysical(), 0.01f);
            assertEquals(12f, summary.damageDetail().flatMelee(), 0.01f);
            assertEquals(2400f, summary.ehpDetail().maxHealth(), 0.01f);
            assertEquals(200f, summary.ehpDetail().energyShield(), 0.01f);
            assertEquals(1200f, summary.ehpDetail().armor(), 0.01f);
        }
    }
}
