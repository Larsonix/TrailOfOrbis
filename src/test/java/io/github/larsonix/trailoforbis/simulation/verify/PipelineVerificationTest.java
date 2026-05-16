package io.github.larsonix.trailoforbis.simulation.verify;

import io.github.larsonix.trailoforbis.attributes.AttributeCalculator;
import io.github.larsonix.trailoforbis.attributes.BaseStats;
import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.combat.AttackType;
import io.github.larsonix.trailoforbis.combat.DamageBreakdown;
import io.github.larsonix.trailoforbis.combat.DamageType;
import io.github.larsonix.trailoforbis.combat.RPGDamageCalculator;
import io.github.larsonix.trailoforbis.config.ConfigManager;
import io.github.larsonix.trailoforbis.config.RPGConfig;
import io.github.larsonix.trailoforbis.database.models.PlayerData;
import io.github.larsonix.trailoforbis.elemental.ElementType;
import io.github.larsonix.trailoforbis.elemental.ElementalStats;
import io.github.larsonix.trailoforbis.gear.stats.GearStatApplier;
import io.github.larsonix.trailoforbis.gear.stats.GearStatCalculator.GearBonuses;
import io.github.larsonix.trailoforbis.simulation.core.StatPipeline;
import io.github.larsonix.trailoforbis.simulation.verify.ReferenceCalculator.MeleeInput;
import io.github.larsonix.trailoforbis.simulation.verify.ReferenceCalculator.DamageResult;
import io.github.larsonix.trailoforbis.skilltree.calculation.AggregatedModifiers;
import io.github.larsonix.trailoforbis.skilltree.calculation.SkillTreeStatAggregator;
import io.github.larsonix.trailoforbis.skilltree.calculation.StatsCombiner;
import io.github.larsonix.trailoforbis.skilltree.config.SkillTreeConfig;
import io.github.larsonix.trailoforbis.skilltree.model.SkillTreeData;

import org.junit.jupiter.api.*;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static io.github.larsonix.trailoforbis.simulation.verify.ReferenceCalculator.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Pipeline verification tests: production code vs independent reference math.
 *
 * <p>Each test constructs a scenario, runs BOTH the production pipeline AND
 * the reference calculator, and asserts they agree. If they disagree, one has a bug.
 *
 * <p>Tests are organized by pipeline stage:
 * A. Attributes → Stats
 * B. Skill Tree → Stats
 * C. Gear → Stats
 * D. Full stat pipeline
 * E. Damage calculator
 * F. Full integration (build → hit → verify)
 */
class PipelineVerificationTest {

    private static RPGConfig rpgConfig;
    private static SkillTreeConfig skillTreeConfig;
    private static AttributeCalculator attributeCalculator;
    private static RPGDamageCalculator damageCalculator;

    @BeforeAll
    static void loadConfigs() throws Exception {
        Path configDir = Path.of("src", "main", "resources");
        Assumptions.assumeTrue(Files.isDirectory(configDir),
            "Config directory not found — run from project root");

        ConfigManager cm = new ConfigManager(configDir);
        cm.loadConfigs();
        rpgConfig = cm.getConfig();
        attributeCalculator = new AttributeCalculator(rpgConfig);
        damageCalculator = new RPGDamageCalculator();

        // Load skill tree
        Path treePath = configDir.resolve("config").resolve("skill-tree.yml");
        if (Files.exists(treePath)) {
            LoaderOptions options = new LoaderOptions();
            Constructor ctor = new Constructor(SkillTreeConfig.class, options);
            Yaml yaml = new Yaml(ctor);
            try (InputStream in = Files.newInputStream(treePath)) {
                skillTreeConfig = yaml.loadAs(in, SkillTreeConfig.class);
            }
        }
        if (skillTreeConfig == null) skillTreeConfig = new SkillTreeConfig();
    }

    // =========================================================================
    // A. ATTRIBUTE → STAT VERIFICATION
    // =========================================================================

    @Nested
    @DisplayName("A. Attributes → Stats")
    class AttributeTests {

        @Test
        @DisplayName("Fire=20 → physDmgPct=8.0%, critMult=162%")
        void fire20() {
            ComputedStats stats = statsFromAttributes(20, 0, 0, 0, 0, 0);

            float expectedPhysPct = expectedPhysDmgPercent(20); // 20 × 0.4 = 8.0
            float expectedCritMult = expectedCritMultiplier(20); // 150 + 20 × 0.6 = 162

            assertEquals(expectedPhysPct, stats.getPhysicalDamagePercent(), 0.01f,
                "Fire=20 → physDmgPct");
            assertEquals(expectedCritMult, stats.getCriticalMultiplier(), 0.01f,
                "Fire=20 → critMult");
        }

        @Test
        @DisplayName("Earth=30 → armor=150, maxHealthPct=15%")
        void earth30() {
            ComputedStats stats = statsFromAttributes(0, 0, 0, 30, 0, 0);

            assertEquals(expectedArmor(30), stats.getArmor(), 0.01f,
                "Earth=30 → armor (no equipment)");
            assertEquals(30 * EARTH_MAX_HP_PCT, stats.getMaxHealthPercent(), 0.01f,
                "Earth=30 → maxHealthPct");
        }

        @Test
        @DisplayName("Lightning=15 → critChance=6.5%, atkSpd=4.5%")
        void lightning15() {
            ComputedStats stats = statsFromAttributes(0, 0, 15, 0, 0, 0);

            assertEquals(expectedCritChance(15), stats.getCriticalChance(), 0.01f,
                "Lightning=15 → critChance");
            assertEquals(15 * LIGHTNING_ATK_SPD_PCT, stats.getAttackSpeedPercent(), 0.01f,
                "Lightning=15 → atkSpd");
        }

        @Test
        @DisplayName("Wind=10 → evasion=50, accuracy=40, projDmg=5%")
        void wind10() {
            ComputedStats stats = statsFromAttributes(0, 0, 0, 0, 10, 0);

            assertEquals(expectedEvasion(10), stats.getEvasion(), 0.01f);
            assertEquals(expectedAccuracy(10), stats.getAccuracy(), 0.01f);
            assertEquals(10 * WIND_PROJ_DMG_PCT, stats.getProjectileDamagePercent(), 0.01f);
        }

        @Test
        @DisplayName("Water=10 → spellDmgPct=5%, maxMana=base+15")
        void water10() {
            ComputedStats stats = statsFromAttributes(0, 10, 0, 0, 0, 0);

            assertEquals(10 * WATER_SPELL_DMG_PCT, stats.getSpellDamagePercent(), 0.01f);
            // maxMana = baseStats.maxMana + 10 * 1.5
            // BaseStats.defaults() maxMana is whatever the config says
            float expectedMana = BaseStats.defaults().getMaxMana() + 10 * WATER_MAX_MANA;
            assertEquals(expectedMana, stats.getMaxMana(), 0.1f);
        }

        @Test
        @DisplayName("Void=20 → lifeSteal=4%, trueDmgPct=2%")
        void void20() {
            ComputedStats stats = statsFromAttributes(0, 0, 0, 0, 0, 20);

            assertEquals(20 * VOID_LIFE_STEAL, stats.getLifeSteal(), 0.01f);
            assertEquals(20 * VOID_TRUE_DMG_PCT, stats.getPercentHitAsTrueDamage(), 0.01f);
        }

        @Test
        @DisplayName("All=0 → base defaults only")
        void allZero() {
            ComputedStats stats = statsFromAttributes(0, 0, 0, 0, 0, 0);

            assertEquals(BASE_CRIT_CHANCE, stats.getCriticalChance(), 0.01f);
            assertEquals(BASE_CRIT_MULTIPLIER, stats.getCriticalMultiplier(), 0.01f);
            assertEquals(BASE_ACCURACY, stats.getAccuracy(), 0.01f);
            assertEquals(0f, stats.getPhysicalDamagePercent(), 0.01f);
            assertEquals(0f, stats.getArmor(), 0.01f);
            assertEquals(0f, stats.getEvasion(), 0.01f);
        }

        @Test
        @DisplayName("Mixed Fire=20 Earth=15 Lightning=10 → combined stats")
        void mixedBuild() {
            ComputedStats stats = statsFromAttributes(20, 0, 10, 15, 0, 0);

            assertEquals(expectedPhysDmgPercent(20), stats.getPhysicalDamagePercent(), 0.01f);
            assertEquals(expectedCritMultiplier(20), stats.getCriticalMultiplier(), 0.01f);
            assertEquals(expectedCritChance(10), stats.getCriticalChance(), 0.01f);
            assertEquals(expectedArmor(15), stats.getArmor(), 0.01f);
        }
    }

    // =========================================================================
    // B. SKILL TREE → STAT VERIFICATION
    // =========================================================================

    @Nested
    @DisplayName("B. Skill Tree → Stats")
    class SkillTreeTests {

        @Test
        @DisplayName("fire_entry adds +5% physDmgPct")
        void fireEntry() {
            ComputedStats base = statsFromAttributes(0, 0, 0, 0, 0, 0);
            ComputedStats withTree = applySkillTree(base, "origin", "fire_entry");

            float diff = withTree.getPhysicalDamagePercent() - base.getPhysicalDamagePercent();
            assertEquals(5.0f, diff, 0.1f,
                "fire_entry should add +5% physDmgPct, got diff=" + diff);
        }

        @Test
        @DisplayName("fire_fury_2 adds +6 critMult")
        void fireFury2() {
            ComputedStats base = statsFromAttributes(0, 0, 0, 0, 0, 0);
            // Need to allocate path: origin → fire_entry → fire_fury_1 → fire_fury_2
            ComputedStats withTree = applySkillTree(base,
                "origin", "fire_entry", "fire_fury_1", "fire_fury_2");

            // fire_entry = +5% physDmg, fire_fury_1 = +6% physDmg, fire_fury_2 = +6 critMult
            float critDiff = withTree.getCriticalMultiplier() - base.getCriticalMultiplier();
            assertTrue(critDiff >= 5.9f && critDiff <= 6.1f,
                "fire_fury_2 should add +6 critMult, got diff=" + critDiff);
        }

        @Test
        @DisplayName("No allocated nodes → stats unchanged")
        void noNodes() {
            ComputedStats base = statsFromAttributes(10, 10, 10, 10, 10, 10);
            ComputedStats withEmptyTree = applySkillTree(base); // no nodes

            assertEquals(base.getPhysicalDamagePercent(), withEmptyTree.getPhysicalDamagePercent(), 0.001f);
            assertEquals(base.getCriticalMultiplier(), withEmptyTree.getCriticalMultiplier(), 0.001f);
            assertEquals(base.getCriticalChance(), withEmptyTree.getCriticalChance(), 0.001f);
        }

        @Test
        @DisplayName("Multiple nodes stack additively")
        void multipleNodesStack() {
            ComputedStats base = statsFromAttributes(0, 0, 0, 0, 0, 0);
            // fire_entry = +5% physDmg, fire_fury_1 = +6% physDmg → total +11%
            ComputedStats withTree = applySkillTree(base,
                "origin", "fire_entry", "fire_fury_1");

            float diff = withTree.getPhysicalDamagePercent() - base.getPhysicalDamagePercent();
            assertEquals(11.0f, diff, 0.1f,
                "fire_entry + fire_fury_1 should add +11% physDmg, got diff=" + diff);
        }

        @Test
        @DisplayName("Respec (origin only) → stats = base")
        void respecOriginOnly() {
            ComputedStats base = statsFromAttributes(10, 10, 10, 10, 10, 10);
            ComputedStats respecced = applySkillTree(base, "origin");

            // Origin has no modifiers, so stats should equal base
            assertEquals(base.getPhysicalDamagePercent(), respecced.getPhysicalDamagePercent(), 0.001f);
            assertEquals(base.getCriticalMultiplier(), respecced.getCriticalMultiplier(), 0.001f);
        }
    }

    // =========================================================================
    // C. GEAR → STAT VERIFICATION
    // =========================================================================

    @Nested
    @DisplayName("C. Gear → Stats")
    class GearTests {

        @Test
        @DisplayName("Fire sword sets weaponSpellElement=FIRE and weaponBase=70")
        void fireSwordGear() {
            ComputedStats stats = ComputedStats.builder().build();
            GearBonuses gear = new GearBonuses(
                Map.of(), Map.of(), 70.0, "Weapon_Sword_Iron", null, true, ElementType.FIRE
            );

            new GearStatApplier().apply(stats, gear);

            assertEquals(ElementType.FIRE, stats.getWeaponSpellElement());
            assertEquals(70.0, stats.getWeaponBaseDamage(), 0.01);
            assertTrue(stats.isHoldingRpgGear());
        }

        @Test
        @DisplayName("Flat physical_damage modifier increases physicalDamage stat")
        void flatPhysMod() {
            ComputedStats stats = ComputedStats.builder().build();
            GearBonuses gear = new GearBonuses(
                Map.of("physical_damage", 15.0), Map.of(), 0.0, null, null, false, null
            );

            new GearStatApplier().apply(stats, gear);

            assertEquals(15.0f, stats.getPhysicalDamage(), 0.01f);
        }

        @Test
        @DisplayName("Percent crit_chance modifier increases critChance")
        void percentCritMod() {
            ComputedStats stats = ComputedStats.builder()
                .criticalChance(5.0f) // base
                .build();
            GearBonuses gear = new GearBonuses(
                Map.of(), Map.of("crit_chance", 3.0), 0.0, null, null, false, null
            );

            new GearStatApplier().apply(stats, gear);

            assertEquals(8.0f, stats.getCriticalChance(), 0.01f);
        }

        @Test
        @DisplayName("No gear → stats unchanged")
        void noGear() {
            ComputedStats before = ComputedStats.builder()
                .physicalDamage(10f)
                .criticalChance(5f)
                .build();
            float physBefore = before.getPhysicalDamage();

            new GearStatApplier().apply(before, GearBonuses.EMPTY);

            assertEquals(physBefore, before.getPhysicalDamage(), 0.001f);
        }

        @Test
        @DisplayName("Flat fire_damage from gear flows to toElementalStats()")
        void flatFireFromGear() {
            ComputedStats stats = ComputedStats.builder().build();
            GearBonuses gear = new GearBonuses(
                Map.of("fire_damage", 25.0), Map.of(), 0.0, null, null, false, null
            );

            new GearStatApplier().apply(stats, gear);

            assertEquals(25.0, stats.toElementalStats().getFlatDamage(ElementType.FIRE), 0.01);
            assertEquals(0.0, stats.toElementalStats().getFlatDamage(ElementType.VOID), 0.01);
        }
    }

    // =========================================================================
    // D. FULL STAT PIPELINE
    // =========================================================================

    @Nested
    @DisplayName("D. Full Stat Pipeline")
    class FullPipelineTests {

        @Test
        @DisplayName("Fire warrior Lv20: attributes + tree + gear → combined stats")
        void fireWarriorLv20() {
            StatPipeline pipeline = new StatPipeline(rpgConfig, skillTreeConfig);

            PlayerData player = PlayerData.builder()
                .uuid(UUID.randomUUID()).username("Test")
                .fire(20).earth(10)
                .build();

            SkillTreeData tree = SkillTreeData.builder()
                .uuid(player.getUuid())
                .allocatedNodes(Set.of("origin", "fire_entry", "fire_fury_1"))
                .build();

            GearBonuses gear = new GearBonuses(
                Map.of("physical_damage", 10.0),
                Map.of("crit_chance", 2.0),
                70.0, "Weapon_Sword_Iron", null, true, ElementType.FIRE
            );

            ComputedStats stats = pipeline.compute(player, BaseStats.defaults(), tree, gear);

            // physDmgPct: 20 × 0.4 = 8% (attrs) + 11% (tree: fire_entry+fire_fury_1) = 19%
            assertEquals(19.0f, stats.getPhysicalDamagePercent(), 0.5f,
                "physDmgPct = attrs(8) + tree(11)");

            // critChance: 5% base + 2% gear = 7%
            assertEquals(7.0f, stats.getCriticalChance(), 0.5f);

            // armor: 10 × 3.5 = 35 (from earth attrs)
            assertEquals(35.0f, stats.getArmor(), 0.5f);

            // weapon element
            assertEquals(ElementType.FIRE, stats.getWeaponSpellElement());
        }

        @Test
        @DisplayName("Level 1 vs Level 50: more attributes → more stats")
        void levelScaling() {
            StatPipeline pipeline = new StatPipeline(rpgConfig, skillTreeConfig);

            // Level 1 player (1 point in fire)
            PlayerData lv1 = PlayerData.builder()
                .uuid(UUID.randomUUID()).username("Lv1")
                .fire(1).build();
            ComputedStats stats1 = pipeline.computeWithoutGear(lv1, BaseStats.defaults(),
                SkillTreeData.builder().uuid(lv1.getUuid()).build());

            // Level 50 player (50 points in fire)
            PlayerData lv50 = PlayerData.builder()
                .uuid(UUID.randomUUID()).username("Lv50")
                .fire(50).build();
            ComputedStats stats50 = pipeline.computeWithoutGear(lv50, BaseStats.defaults(),
                SkillTreeData.builder().uuid(lv50.getUuid()).build());

            assertTrue(stats50.getPhysicalDamagePercent() > stats1.getPhysicalDamagePercent(),
                "Lv50 fire must have more physDmgPct than Lv1");
            assertTrue(stats50.getCriticalMultiplier() > stats1.getCriticalMultiplier(),
                "Lv50 fire must have more critMult than Lv1");
        }
    }

    // =========================================================================
    // E. DAMAGE CALCULATOR — PRODUCTION vs REFERENCE
    // =========================================================================

    @Nested
    @DisplayName("E. Damage Calculator vs Reference")
    class DamageCalculatorTests {

        @Test
        @DisplayName("Fire sword 100 base, no bonuses → Fire=100, Phys=0, Void=0")
        void fireSword_noBonuses() {
            ComputedStats atk = minimalAttacker();
            DamageBreakdown prod = damageCalculator.calculate(
                100f, atk, new ElementalStats(), null, null,
                AttackType.MELEE, false, 1.0f, false, ElementType.FIRE, 1, false);

            MeleeInput ref = new MeleeInput();
            ref.baseDamage = 100f;
            ref.weaponElement = ElementType.FIRE;
            DamageResult refResult = calculateMeleeDamage(ref);

            assertDamageMatch(prod, refResult, "fire sword no bonuses");
        }

        @Test
        @DisplayName("physDmgPct only scales physical slot, NOT element slot")
        void physDmgPct_doesNotScaleElement() {
            ComputedStats atk = ComputedStats.builder()
                .physicalDamagePercent(50f)
                .criticalChance(0f).criticalMultiplier(150f).build();

            DamageBreakdown prod = damageCalculator.calculate(
                100f, atk, new ElementalStats(), null, null,
                AttackType.MELEE, false, 1.0f, false, ElementType.FIRE, 1, false);

            // Fire base = 100, physDmgPct only applies to physical (which is 0)
            assertEquals(100f, prod.getElementalDamage(ElementType.FIRE), 0.5f,
                "Fire must be 100 (physDmgPct doesn't scale element slot)");
            assertEquals(0f, prod.physicalDamage(), 0.01f,
                "Physical must be 0 (no flat phys, even with +50% physDmgPct)");
        }

        @Test
        @DisplayName("meleeDmgPct scales BOTH physical and element slot for melee")
        void meleeDmgPct_scalesBoth() {
            ComputedStats atk = ComputedStats.builder()
                .meleeDamagePercent(50f)
                .criticalChance(0f).criticalMultiplier(150f).build();

            DamageBreakdown prod = damageCalculator.calculate(
                100f, atk, new ElementalStats(), null, null,
                AttackType.MELEE, false, 1.0f, false, ElementType.FIRE, 1, false);

            MeleeInput ref = new MeleeInput();
            ref.baseDamage = 100f;
            ref.weaponElement = ElementType.FIRE;
            ref.meleeDmgPercent = 50f;
            DamageResult refResult = calculateMeleeDamage(ref);

            // Fire = 100 × (1 + 50/100) = 150
            assertDamageMatch(prod, refResult, "melee% scales fire slot");
            assertEquals(150f, prod.getElementalDamage(ElementType.FIRE), 0.5f);
        }

        @Test
        @DisplayName("Physical sword 100, +50% physDmg → 150 physical")
        void physSword_physDmgPct() {
            ComputedStats atk = ComputedStats.builder()
                .physicalDamagePercent(50f)
                .criticalChance(0f).criticalMultiplier(150f).build();

            DamageBreakdown prod = damageCalculator.calculate(
                100f, atk, new ElementalStats(), null, null,
                AttackType.MELEE, false, 1.0f, false, null, 1, false);

            MeleeInput ref = new MeleeInput();
            ref.baseDamage = 100f;
            ref.physDmgPercent = 50f;
            DamageResult refResult = calculateMeleeDamage(ref);

            assertDamageMatch(prod, refResult, "phys sword +50%");
            assertEquals(150f, prod.physicalDamage(), 0.5f);
        }

        @Test
        @DisplayName("50% fire conversion: 100 phys → 50 phys + 50 fire")
        void fireConversion50() {
            ComputedStats atk = ComputedStats.builder()
                .fireConversion(50f)
                .criticalChance(0f).criticalMultiplier(150f).build();

            DamageBreakdown prod = damageCalculator.calculate(
                100f, atk, new ElementalStats(), null, null,
                AttackType.MELEE, false, 1.0f, false, null, 1, false);

            MeleeInput ref = new MeleeInput();
            ref.baseDamage = 100f;
            ref.conversionPercent.put(ElementType.FIRE, 50f);
            DamageResult refResult = calculateMeleeDamage(ref);

            assertDamageMatch(prod, refResult, "50% fire conversion");
            assertEquals(50f, prod.physicalDamage(), 0.5f);
            assertEquals(50f, prod.getElementalDamage(ElementType.FIRE), 0.5f);
        }

        @Test
        @DisplayName("Fire sword + void conversion → conversion does NOT touch fire base")
        void fireSword_voidConversion() {
            ComputedStats atk = ComputedStats.builder()
                .voidConversion(50f)
                .criticalChance(0f).criticalMultiplier(150f).build();

            DamageBreakdown prod = damageCalculator.calculate(
                100f, atk, new ElementalStats(), null, null,
                AttackType.MELEE, false, 1.0f, false, ElementType.FIRE, 1, false);

            MeleeInput ref = new MeleeInput();
            ref.baseDamage = 100f;
            ref.weaponElement = ElementType.FIRE;
            ref.conversionPercent.put(ElementType.VOID, 50f);
            DamageResult refResult = calculateMeleeDamage(ref);

            assertDamageMatch(prod, refResult, "fire sword + void conv");
            // Fire = 100 (untouched), Void = 0 (no physical to convert)
            assertEquals(100f, prod.getElementalDamage(ElementType.FIRE), 0.5f);
            assertEquals(0f, prod.getElementalDamage(ElementType.VOID), 0.01f);
        }

        @Test
        @DisplayName("Flat elemental from gear adds to correct slot")
        void flatElementalFromGear() {
            ElementalStats elemStats = new ElementalStats();
            elemStats.setFlatDamage(ElementType.FIRE, 20);

            DamageBreakdown prod = damageCalculator.calculate(
                100f, minimalAttacker(), elemStats, null, null,
                AttackType.MELEE, false, 1.0f, false, null, 1, false);

            MeleeInput ref = new MeleeInput();
            ref.baseDamage = 100f;
            ref.flatElemental.put(ElementType.FIRE, 20f);
            DamageResult refResult = calculateMeleeDamage(ref);

            assertDamageMatch(prod, refResult, "flat fire from gear");
            assertEquals(100f, prod.physicalDamage(), 0.5f, "Physical stays 100");
            assertEquals(20f, prod.getElementalDamage(ElementType.FIRE), 0.5f, "Fire = 20 from gear");
        }

        @Test
        @DisplayName("Armor defense: 150 armor vs 100 phys at Lv20")
        void armorDefense() {
            ComputedStats atk = minimalAttacker();
            ComputedStats def = ComputedStats.builder().armor(150f).build();

            DamageBreakdown prod = damageCalculator.calculate(
                100f, atk, new ElementalStats(), def, null,
                AttackType.MELEE, false, 1.0f, false, null, 20, false);

            float expectedReduction = calculateArmorReduction(150f, 20);
            // reduction = 150 / (150 + 9*20 + 50) = 150/380 ≈ 0.3947
            float expectedPhys = 100f * (1f - expectedReduction);

            assertEquals(expectedPhys, prod.physicalDamage(), 1.0f,
                "Armor reduction " + expectedReduction + " → phys=" + expectedPhys);
        }

        @Test
        @DisplayName("Forced crit: 100 base × 150% = 150")
        void forcedCrit() {
            DamageBreakdown prod = damageCalculator.calculateWithForcedCrit(
                100f, minimalAttacker(), new ElementalStats(), null, null,
                AttackType.MELEE, true, 1);

            assertTrue(prod.wasCritical());
            assertEquals(150f, prod.physicalDamage(), 0.5f,
                "100 × 1.5 (150% crit mult) = 150");
        }

        @Test
        @DisplayName("Elemental resistance: 100 fire vs 50% resist → 50")
        void elementalResistance() {
            ElementalStats defElem = new ElementalStats();
            defElem.setResistance(ElementType.FIRE, 50);

            DamageBreakdown prod = damageCalculator.calculate(
                100f, minimalAttacker(), new ElementalStats(),
                ComputedStats.builder().build(), defElem,
                AttackType.MELEE, false, 1.0f, false, ElementType.FIRE, 1, false);

            assertEquals(50f, prod.getElementalDamage(ElementType.FIRE), 0.5f,
                "100 fire × (1 - 50/100) = 50");
        }

        @Test
        @DisplayName("% More multipliers chain multiplicatively")
        void moreMultipliersChain() {
            ComputedStats atk = ComputedStats.builder()
                .allDamagePercent(20f)   // × 1.2
                .damageMultiplier(30f)   // × 1.3
                .criticalChance(0f).criticalMultiplier(150f).build();

            DamageBreakdown prod = damageCalculator.calculate(
                100f, atk, new ElementalStats(), null, null,
                AttackType.MELEE, false, 1.0f, false, null, 1, false);

            // 100 × 1.2 × 1.3 = 156
            assertEquals(156f, prod.physicalDamage(), 1.0f,
                "100 × 1.2 × 1.3 = 156");
        }
    }

    // =========================================================================
    // F. FULL INTEGRATION
    // =========================================================================

    @Nested
    @DisplayName("F. Full Integration: Build → Hit → Verify")
    class FullIntegrationTests {

        @Test
        @DisplayName("Fire warrior hits unarmored mob: fire damage > 0, void = 0")
        void fireWarriorVsUnarmored() {
            StatPipeline pipeline = new StatPipeline(rpgConfig, skillTreeConfig);

            PlayerData player = PlayerData.builder()
                .uuid(UUID.randomUUID()).username("FireWarrior")
                .fire(20).earth(10).build();

            SkillTreeData tree = SkillTreeData.builder()
                .uuid(player.getUuid())
                .allocatedNodes(Set.of("origin", "fire_entry"))
                .build();

            GearBonuses gear = new GearBonuses(
                Map.of(), Map.of(), 70.0, "Weapon_Sword_Iron", null, true, ElementType.FIRE
            );

            ComputedStats stats = pipeline.compute(player, BaseStats.defaults(), tree, gear);
            ElementalStats elemental = stats.toElementalStats();

            DamageBreakdown result = damageCalculator.calculate(
                (float) gear.weaponBaseDamage(), stats, elemental, null, null,
                AttackType.MELEE, false, 1.0f, false, ElementType.FIRE, 20, false);

            assertEquals(DamageType.FIRE, result.damageType(),
                "Fire warrior must deal FIRE damage");
            assertTrue(result.getElementalDamage(ElementType.FIRE) > 60,
                "Fire damage must be substantial: " + result.getElementalDamage(ElementType.FIRE));
            assertEquals(0f, result.getElementalDamage(ElementType.VOID), 0.01f,
                "Void must be zero");
        }

        @Test
        @DisplayName("Respec test: full tree vs empty tree → damage differs")
        void respecChangesDamage() {
            StatPipeline pipeline = new StatPipeline(rpgConfig, skillTreeConfig);

            PlayerData player = PlayerData.builder()
                .uuid(UUID.randomUUID()).username("Respec")
                .fire(20).build();

            GearBonuses gear = new GearBonuses(
                Map.of(), Map.of(), 100.0, "Weapon_Sword_Iron", null, true, null
            );

            // With tree (fire_entry + fire_fury_1 = +11% physDmg)
            SkillTreeData fullTree = SkillTreeData.builder()
                .uuid(player.getUuid())
                .allocatedNodes(Set.of("origin", "fire_entry", "fire_fury_1"))
                .build();
            ComputedStats withTree = pipeline.compute(player, BaseStats.defaults(), fullTree, gear);

            // Without tree
            SkillTreeData emptyTree = SkillTreeData.builder()
                .uuid(player.getUuid()).build();
            ComputedStats withoutTree = pipeline.compute(player, BaseStats.defaults(), emptyTree, gear);

            // Both deal physical (no element) — use isDOT=true to skip crit RNG (deterministic comparison)
            DamageBreakdown dmgWithTree = damageCalculator.calculate(
                100f, withTree, withTree.toElementalStats(), null, null,
                AttackType.MELEE, true, 1.0f, false, null, 20, false);
            DamageBreakdown dmgWithoutTree = damageCalculator.calculate(
                100f, withoutTree, withoutTree.toElementalStats(), null, null,
                AttackType.MELEE, true, 1.0f, false, null, 20, false);

            assertTrue(dmgWithTree.physicalDamage() > dmgWithoutTree.physicalDamage(),
                "Tree bonuses must increase damage. With=" + dmgWithTree.physicalDamage()
                + " Without=" + dmgWithoutTree.physicalDamage());
        }

        @Test
        @DisplayName("Earth tank vs armored mob: armor reduces physical damage")
        void earthTankVsArmored() {
            StatPipeline pipeline = new StatPipeline(rpgConfig, skillTreeConfig);

            PlayerData player = PlayerData.builder()
                .uuid(UUID.randomUUID()).username("EarthTank")
                .earth(20).fire(10).build();

            SkillTreeData tree = SkillTreeData.builder()
                .uuid(player.getUuid()).build();

            GearBonuses gear = new GearBonuses(
                Map.of(), Map.of(), 80.0, "Weapon_Mace_Iron", null, true, null
            );

            ComputedStats playerStats = pipeline.compute(player, BaseStats.defaults(), tree, gear);

            // Mob with armor
            ComputedStats mob = ComputedStats.builder().armor(100f).build();

            DamageBreakdown vsArmor = damageCalculator.calculate(
                80f, playerStats, playerStats.toElementalStats(), mob, null,
                AttackType.MELEE, false, 1.0f, false, null, 20, false);

            DamageBreakdown vsNoArmor = damageCalculator.calculate(
                80f, playerStats, playerStats.toElementalStats(), null, null,
                AttackType.MELEE, false, 1.0f, false, null, 20, false);

            assertTrue(vsArmor.physicalDamage() < vsNoArmor.physicalDamage(),
                "Armor must reduce damage. With armor=" + vsArmor.physicalDamage()
                + " No armor=" + vsNoArmor.physicalDamage());
        }
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    /** Creates ComputedStats from attribute points using the REAL AttributeCalculator. */
    private ComputedStats statsFromAttributes(int fire, int water, int lightning, int earth, int wind, int voidAttr) {
        PlayerData data = PlayerData.builder()
            .uuid(UUID.randomUUID()).username("Test")
            .fire(fire).water(water).lightning(lightning)
            .earth(earth).wind(wind).voidAttr(voidAttr)
            .build();
        return attributeCalculator.calculateStats(data, BaseStats.defaults());
    }

    /** Applies skill tree modifiers to base stats using REAL aggregator + combiner. */
    private ComputedStats applySkillTree(ComputedStats base, String... nodeIds) {
        SkillTreeStatAggregator aggregator = new SkillTreeStatAggregator(skillTreeConfig);
        StatsCombiner combiner = new StatsCombiner();

        SkillTreeData treeData = SkillTreeData.builder()
            .uuid(UUID.randomUUID())
            .allocatedNodes(new LinkedHashSet<>(Arrays.asList(nodeIds)))
            .build();

        AggregatedModifiers modifiers = aggregator.aggregate(treeData);
        return combiner.combine(base, modifiers);
    }

    /** Minimal attacker stats (no crit, no bonuses). */
    private static ComputedStats minimalAttacker() {
        return ComputedStats.builder()
            .criticalChance(0f)
            .criticalMultiplier(150f)
            .build();
    }

    /** Asserts that production DamageBreakdown matches reference DamageResult within tolerance. */
    private static void assertDamageMatch(DamageBreakdown prod, DamageResult ref, String context) {
        assertEquals(ref.physical(), prod.physicalDamage(), 1.0f,
            context + " — physical mismatch");
        for (ElementType type : ElementType.values()) {
            assertEquals(ref.getElemental(type), prod.getElementalDamage(type), 1.0f,
                context + " — " + type.name() + " mismatch");
        }
        assertEquals(ref.trueDamage(), prod.trueDamage(), 1.0f,
            context + " — trueDmg mismatch");
    }
}
