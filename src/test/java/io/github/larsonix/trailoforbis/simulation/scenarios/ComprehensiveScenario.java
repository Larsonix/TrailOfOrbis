package io.github.larsonix.trailoforbis.simulation.scenarios;

import io.github.larsonix.trailoforbis.attributes.BaseStats;
import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.combat.AttackType;
import io.github.larsonix.trailoforbis.combat.DamageBreakdown;
import io.github.larsonix.trailoforbis.combat.RPGDamageCalculator;
import io.github.larsonix.trailoforbis.leveling.config.LevelingConfig;
import io.github.larsonix.trailoforbis.leveling.formula.EffortBasedFormula;
import io.github.larsonix.trailoforbis.leveling.formula.EffortCurve;
import io.github.larsonix.trailoforbis.leveling.formula.LevelFormula;
import io.github.larsonix.trailoforbis.leveling.formula.MobXpEstimator;
import io.github.larsonix.trailoforbis.mobs.stats.MobStatGenerator;
import io.github.larsonix.trailoforbis.mobs.stats.MobStatPoolConfig;
import io.github.larsonix.trailoforbis.mobs.stats.MobStatProfile;
import io.github.larsonix.trailoforbis.simulation.builds.BuildArchetype;
import io.github.larsonix.trailoforbis.simulation.builds.BuildFactory;
import io.github.larsonix.trailoforbis.simulation.builds.BuildFactory.VirtualBuild;
import io.github.larsonix.trailoforbis.simulation.core.CombatSimulator;
import io.github.larsonix.trailoforbis.simulation.core.CombatSimulator.CombatResult;
import io.github.larsonix.trailoforbis.simulation.core.MobHpFormula;
import io.github.larsonix.trailoforbis.simulation.core.StatPipeline;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Comprehensive balance analysis — every build × every level 1-100.
 *
 * <p>Outputs 6 CSV files covering all dimensions:
 * <ol>
 *   <li>stat_progression — full stat dump per build per level</li>
 *   <li>mob_progression — mob stats per level for PASSIVE/HOSTILE/ELITE/BOSS</li>
 *   <li>combat_matrix — every build vs HOSTILE/ELITE/BOSS at every level</li>
 *   <li>defense_layers — armor/block/evasion/regen contribution breakdown</li>
 *   <li>xp_economy — XP per kill, kills to level, DG vs OW at every level</li>
 *   <li>balance_flags — automatic flags for potential balance issues</li>
 * </ol>
 */
public final class ComprehensiveScenario {

    private static final int MAX_LEVEL = 100;
    private static final int COMBAT_ITERATIONS = 200;
    private static final long BASE_SEED = 42L;

    /** Number of different mob Dirichlet seeds to average per level.
     * Smooths out variance from single-mob spikes. In-game, players fight many different mobs. */
    private static final int MOB_SAMPLES = 20;

    /** Classification stat multipliers from mob-classification.yml */
    private static final double PASSIVE_MULT = 0.1;
    private static final double HOSTILE_MULT = 1.0;
    private static final double ELITE_MULT = 1.5;
    private static final double BOSS_MULT = 3.0;

    /** Classification XP multipliers */
    private static final double PASSIVE_XP = 0.1;
    private static final double HOSTILE_XP = 1.0;
    private static final double ELITE_XP = 1.5;
    private static final double BOSS_XP = 5.0;
    private static final double GLOBAL_XP_MULT = 1.2;
    private static final double XP_PER_MOB_LEVEL = 5.0;
    private static final double POOL_MULTIPLIER = 0.1;

    private final StatPipeline pipeline;
    private final BuildFactory buildFactory;
    private final List<BuildArchetype> archetypes;
    private final CombatSimulator combatSim;
    private final MobStatGenerator mobGenerator;
    private final MobHpFormula hpFormula;
    private final MobStatPoolConfig mobConfig;
    private final LevelingConfig levelingConfig;

    public ComprehensiveScenario(
            @Nonnull StatPipeline pipeline,
            @Nonnull BuildFactory buildFactory,
            @Nonnull List<BuildArchetype> archetypes,
            @Nonnull MobStatPoolConfig mobConfig,
            @Nonnull LevelingConfig levelingConfig,
            @Nonnull io.github.larsonix.trailoforbis.simulation.core.AvoidanceModel avoidanceModel) {
        this.pipeline = pipeline;
        this.buildFactory = buildFactory;
        this.archetypes = archetypes;
        this.combatSim = new CombatSimulator(avoidanceModel);
        this.mobGenerator = new MobStatGenerator(mobConfig);
        this.hpFormula = new MobHpFormula(mobConfig);
        this.mobConfig = mobConfig;
        this.levelingConfig = levelingConfig;
    }

    public void run(@Nonnull Path outputDir) throws IOException {
        Files.createDirectories(outputDir);

        System.out.println("  [1/6] Generating stat progressions...");
        writeStatProgression(outputDir);

        System.out.println("  [2/6] Generating mob progressions...");
        writeMobProgression(outputDir);

        System.out.println("  [3/6] Running combat matrix (all builds × all mob types × levels 1-100)...");
        writeCombatMatrix(outputDir);

        System.out.println("  [4/6] Analyzing defense layers...");
        writeDefenseLayers(outputDir);

        System.out.println("  [5/6] Computing XP economy...");
        writeXpEconomy(outputDir);

        System.out.println("  [6/6] Scanning for balance flags...");
        writeBalanceFlags(outputDir);
    }

    // =========================================================================
    // 1. STAT PROGRESSION — every stat for every build at every level
    // =========================================================================

    private void writeStatProgression(Path outputDir) throws IOException {
        Path csv = outputDir.resolve("stat_progression.csv");
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(csv))) {
            w.println("level,build,"
                    + "max_hp,max_mana,max_stamina,"
                    + "phys_dmg,spell_dmg,weapon_base_dmg,"
                    + "crit_chance,crit_mult,"
                    + "attack_speed_pct,move_speed_pct,"
                    + "armor,armor_pct,evasion,energy_shield,"
                    + "block_chance,parry_chance,"
                    + "health_regen,mana_regen,stamina_regen,"
                    + "life_steal,true_dmg_pct,"
                    + "fire_dmg,water_dmg,lightning_dmg,earth_dmg,wind_dmg,void_dmg,"
                    + "fire_resist,water_resist,lightning_resist,earth_resist,wind_resist,void_resist,"
                    + "phys_resist,knockback_resist,"
                    + "accuracy,armor_pen,"
                    + "all_dmg_pct,melee_dmg_pct,proj_dmg_pct,"
                    + "fire_conv,water_conv,lightning_conv,earth_conv,wind_conv,void_conv");

            for (int level = 1; level <= MAX_LEVEL; level++) {
                for (BuildArchetype arch : archetypes) {
                    VirtualBuild build = buildFactory.create(arch, level);
                    ComputedStats s = pipeline.compute(
                            build.playerData(), BaseStats.defaults(),
                            build.skillTreeData(), build.gearBonuses());

                    w.printf("%d,%s,"
                            + "%.2f,%.2f,%.2f,"
                            + "%.2f,%.2f,%.2f,"
                            + "%.2f,%.2f,"
                            + "%.2f,%.2f,"
                            + "%.2f,%.2f,%.2f,%.2f,"
                            + "%.2f,%.2f,"
                            + "%.4f,%.4f,%.4f,"
                            + "%.4f,%.4f,"
                            + "%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,"
                            + "%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,"
                            + "%.2f,%.2f,"
                            + "%.2f,%.2f,"
                            + "%.2f,%.2f,%.2f,"
                            + "%.2f,%.2f,%.2f,%.2f,%.2f,%.2f%n",
                            level, arch.name(),
                            s.getMaxHealth(), s.getMaxMana(), s.getMaxStamina(),
                            s.getPhysicalDamage(), s.getSpellDamage(), s.getWeaponBaseDamage(),
                            s.getCriticalChance(), s.getCriticalMultiplier(),
                            s.getAttackSpeedPercent(), s.getMovementSpeedPercent(),
                            s.getArmor(), s.getArmorPercent(), s.getEvasion(), s.getEnergyShield(),
                            s.getPassiveBlockChance(), s.getParryChance(),
                            s.getHealthRegen(), s.getManaRegen(), s.getStaminaRegen(),
                            s.getLifeSteal(), s.getPercentHitAsTrueDamage(),
                            s.getFireDamage(), s.getWaterDamage(), s.getLightningDamage(),
                            s.getEarthDamage(), s.getWindDamage(), s.getVoidDamage(),
                            s.getFireResistance(), s.getWaterResistance(), s.getLightningResistance(),
                            s.getEarthResistance(), s.getWindResistance(), s.getVoidResistance(),
                            s.getPhysicalResistance(), s.getKnockbackResistance(),
                            s.getAccuracy(), s.getArmorPenetration(),
                            s.getAllDamagePercent(), s.getMeleeDamagePercent(), s.getProjectileDamagePercent(),
                            s.getFireConversion(), s.getWaterConversion(), s.getLightningConversion(),
                            s.getEarthConversion(), s.getWindConversion(), s.getVoidConversion());
                }
            }
        }
        System.out.printf("    → %s (%d builds × %d levels)%n", csv.getFileName(), archetypes.size(), MAX_LEVEL);
    }

    // =========================================================================
    // 2. MOB PROGRESSION — stats for each classification at every level
    // =========================================================================

    private void writeMobProgression(Path outputDir) throws IOException {
        Path csv = outputDir.resolve("mob_progression.csv");
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(csv))) {
            w.println("level,classification,stat_mult,total_pool,"
                    + "rpg_hp,actual_ingame_hp,phys_dmg,armor,health_regen,"
                    + "crit_chance,crit_mult,dodge_chance,move_speed,"
                    + "life_steal,armor_pen,block_chance,parry_chance");

            String[] classes = {"PASSIVE", "HOSTILE", "ELITE", "BOSS"};
            double[] mults = {PASSIVE_MULT, HOSTILE_MULT, ELITE_MULT, BOSS_MULT};

            for (int level = 1; level <= MAX_LEVEL; level++) {
                for (int c = 0; c < classes.length; c++) {
                    MobStatProfile p;
                    if (mults[c] == 1.0) {
                        p = mobGenerator.generate(level, 0, BASE_SEED + level);
                    } else {
                        p = mobGenerator.generateSpecial(level, 0, mults[c], BASE_SEED + level);
                    }

                    boolean isBoss = mults[c] == BOSS_MULT;
                    double actualHP = hpFormula.calculateActualHP(p, isBoss);

                    w.printf("%d,%s,%.1f,%.1f,"
                            + "%.2f,%.2f,%.2f,%.2f,%.4f,"
                            + "%.2f,%.2f,%.4f,%.4f,"
                            + "%.4f,%.4f,%.4f,%.4f%n",
                            level, classes[c], mults[c], p.totalPool(),
                            p.maxHealth(), actualHP, p.physicalDamage(), p.armor(), p.healthRegen(),
                            p.criticalChance(), p.criticalMultiplier(), p.dodgeChance(), p.moveSpeed(),
                            p.lifeSteal(), p.armorPenetration(), p.blockChance(), p.parryChance());
                }
            }
        }
        System.out.printf("    → %s (4 classifications × %d levels)%n", csv.getFileName(), MAX_LEVEL);
    }

    // =========================================================================
    // 3. COMBAT MATRIX — every build vs HOSTILE/ELITE/BOSS at every level
    // =========================================================================

    private void writeCombatMatrix(Path outputDir) throws IOException {
        Path csv = outputDir.resolve("combat_matrix.csv");
        RPGDamageCalculator calc = new RPGDamageCalculator();

        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(csv))) {
            w.println("level,build,mob_class,mob_mult,"
                    + "player_dps,mob_dps,mob_hp,player_hp,"
                    + "ttk,ttd,survivability,player_wins,"
                    + "avg_player_hit,avg_mob_hit,player_crit_rate,"
                    + "avoid_rate,dodge_rate,evasion_rate,block_rate,effective_mob_dps_after_regen");

            String[] classes = {"HOSTILE", "ELITE", "BOSS"};
            double[] mults = {HOSTILE_MULT, ELITE_MULT, BOSS_MULT};

            for (int level = 1; level <= MAX_LEVEL; level++) {
                for (BuildArchetype arch : archetypes) {
                    VirtualBuild build = buildFactory.create(arch, level);
                    ComputedStats playerStats = pipeline.compute(
                            build.playerData(), BaseStats.defaults(),
                            build.skillTreeData(), build.gearBonuses());

                    for (int c = 0; c < classes.length; c++) {
                        boolean isBoss = mults[c] == BOSS_MULT;
                        boolean isElite = mults[c] == ELITE_MULT;

                        // Average across MOB_SAMPLES different Dirichlet seeds
                        // In-game players fight many different mobs — single seed creates false spikes
                        double sumDPS = 0, sumMobDPS = 0, sumMobHP = 0, sumTTK = 0, sumTTD = 0;
                        double sumSurv = 0, sumAvoid = 0, sumDodge = 0, sumEva = 0, sumBlock = 0;
                        double sumPlayerHit = 0, sumMobHit = 0, sumCrit = 0;
                        int winsCount = 0;

                        for (int s = 0; s < MOB_SAMPLES; s++) {
                            long seed = BASE_SEED + level * 1000L + s;
                            MobStatProfile mobProfile;
                            if (mults[c] == 1.0) {
                                mobProfile = mobGenerator.generate(level, 0, seed);
                            } else {
                                mobProfile = mobGenerator.generateSpecial(level, 0, mults[c], seed);
                            }
                            ComputedStats mobSt = hpFormula.toActualCombatStats(mobProfile, isBoss, isElite);
                            double concurrent = CombatSimulator.estimateConcurrentMobs(level);
                            CombatResult r = combatSim.simulate(playerStats, mobSt, COMBAT_ITERATIONS, concurrent);

                            sumDPS += r.playerDPS();
                            sumMobDPS += r.mobDPS();
                            sumMobHP += r.mobHP();
                            sumTTK += r.ttk();
                            // Cap TTD to prevent infinity from distorting average
                            sumTTD += Math.min(r.ttd(), 3600.0);
                            sumSurv += Math.min(r.survivability(), 100.0);
                            sumAvoid += r.playerAvoidRate();
                            sumDodge += r.dodgeRate();
                            sumEva += r.evasionRate();
                            sumBlock += r.blockRate();
                            sumPlayerHit += r.avgPlayerHit();
                            sumMobHit += r.avgMobHit();
                            sumCrit += r.critRate();
                            if (r.playerWins()) winsCount++;
                        }

                        double n = MOB_SAMPLES;
                        double avgSurv = sumSurv / n;
                        double avgMobDPS = sumMobDPS / n;
                        double effectiveMobDPS = Math.max(0, avgMobDPS - playerStats.getHealthRegen());

                        w.printf("%d,%s,%s,%.1f,"
                                + "%.2f,%.2f,%.2f,%.2f,"
                                + "%.2f,%.2f,%.4f,%s,"
                                + "%.2f,%.2f,%.4f,"
                                + "%.4f,%.4f,%.4f,%.4f,%.2f%n",
                                level, arch.name(), classes[c], mults[c],
                                sumDPS/n, avgMobDPS, sumMobHP/n, (double)playerStats.getMaxHealth(),
                                sumTTK/n, sumTTD/n, avgSurv,
                                winsCount > MOB_SAMPLES/2 ? "YES" : "NO",
                                sumPlayerHit/n, sumMobHit/n, sumCrit/n,
                                sumAvoid/n, sumDodge/n, sumEva/n, sumBlock/n, effectiveMobDPS);
                    }
                }
            }
        }
        System.out.printf("    → %s (%d builds × 3 mob types × %d levels)%n",
                csv.getFileName(), archetypes.size(), MAX_LEVEL);
    }

    // =========================================================================
    // 4. DEFENSE LAYERS — contribution of each defense mechanism
    // =========================================================================

    private void writeDefenseLayers(Path outputDir) throws IOException {
        Path csv = outputDir.resolve("defense_layers.csv");
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(csv))) {
            w.println("level,build,"
                    + "raw_hp,armor,armor_reduction_pct,armor_ehp_mult,"
                    + "block_chance,block_ehp_mult,"
                    + "evasion,dodge_chance,"
                    + "health_regen_per_sec,regen_ehp_at_10s,"
                    + "energy_shield,phys_resist,"
                    + "total_ehp,ehp_vs_raw_hp_ratio");

            for (int level = 1; level <= MAX_LEVEL; level++) {
                // Get hostile mob damage for armor calculation
                MobStatProfile mobProfile = mobGenerator.generate(level, 0, BASE_SEED + level);
                float mobDmg = (float) mobProfile.physicalDamage();

                for (BuildArchetype arch : archetypes) {
                    VirtualBuild build = buildFactory.create(arch, level);
                    ComputedStats s = pipeline.compute(
                            build.playerData(), BaseStats.defaults(),
                            build.skillTreeData(), build.gearBonuses());

                    float armor = s.getArmor();
                    float rawHP = s.getMaxHealth();

                    // Armor reduction: armor / (armor + damage × 10), capped at 90%
                    float armorReduction = 0;
                    if (armor > 0 && mobDmg > 0) {
                        armorReduction = Math.min(0.9f, armor / (armor + mobDmg * 10f));
                    }
                    double armorEhpMult = 1.0 / Math.max(0.1, 1.0 - armorReduction);

                    // Block: passive block chance reduces expected incoming damage
                    float blockChance = s.getPassiveBlockChance();
                    double blockEhpMult = 1.0 / Math.max(0.01, 1.0 - blockChance / 100.0);

                    // Regen: effective bonus HP over a 10-second fight
                    double regenEhp = s.getHealthRegen() * 10.0;

                    // Total EHP
                    double totalEhp = (rawHP + regenEhp) * armorEhpMult * blockEhpMult;
                    double ehpRatio = totalEhp / Math.max(1, rawHP);

                    w.printf("%d,%s,"
                            + "%.2f,%.2f,%.4f,%.4f,"
                            + "%.2f,%.4f,"
                            + "%.2f,%.2f,"
                            + "%.4f,%.2f,"
                            + "%.2f,%.2f,"
                            + "%.2f,%.4f%n",
                            level, arch.name(),
                            rawHP, armor, armorReduction, armorEhpMult,
                            blockChance, blockEhpMult,
                            s.getEvasion(), s.getDodgeChance(),
                            s.getHealthRegen(), regenEhp,
                            s.getEnergyShield(), s.getPhysicalResistance(),
                            totalEhp, ehpRatio);
                }
            }
        }
        System.out.printf("    → %s%n", csv.getFileName());
    }

    // =========================================================================
    // 5. XP ECONOMY — every level 1-100
    // =========================================================================

    private void writeXpEconomy(Path outputDir) throws IOException {
        Path csv = outputDir.resolve("xp_economy_detailed.csv");
        LevelFormula formula = buildLevelFormula();

        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(csv))) {
            w.println("level,xp_to_next,cumulative_xp,"
                    + "hostile_xp,elite_xp,boss_xp,"
                    + "kills_hostile,kills_elite,kills_boss,"
                    + "est_minutes_hostile,est_minutes_elite,"
                    + "mob_pool,mob_hp_hostile,mob_hp_elite,mob_hp_boss");

            for (int level = 1; level <= MAX_LEVEL; level++) {
                long xpToNext = formula.getXpBetweenLevels(level);
                long cumulativeXp = formula.getXpForLevel(level);

                // Mob stats at this level
                MobStatProfile hostile = mobGenerator.generate(level, 0, BASE_SEED + level);
                MobStatProfile elite = mobGenerator.generateSpecial(level, 0, ELITE_MULT, BASE_SEED + level);
                MobStatProfile boss = mobGenerator.generateSpecial(level, 0, BOSS_MULT, BASE_SEED + level);

                double pool = hostile.totalPool();

                // XP per kill
                double hostileXp = Math.max(1, Math.ceil((level * XP_PER_MOB_LEVEL + pool * POOL_MULTIPLIER) * HOSTILE_XP * GLOBAL_XP_MULT));
                double eliteXp = Math.max(1, Math.ceil((level * XP_PER_MOB_LEVEL + pool * POOL_MULTIPLIER) * ELITE_XP * GLOBAL_XP_MULT));
                double bossXp = Math.max(1, Math.ceil((level * XP_PER_MOB_LEVEL + pool * POOL_MULTIPLIER) * BOSS_XP * GLOBAL_XP_MULT));

                double killsHostile = xpToNext / Math.max(1, hostileXp);
                double killsElite = xpToNext / Math.max(1, eliteXp);
                double killsBoss = xpToNext / Math.max(1, bossXp);

                // Estimated time (assume 4s per kill for hostile, 6s for elite)
                double minHostile = (killsHostile * 4.0) / 60.0;
                double minElite = (killsElite * 6.0) / 60.0;

                w.printf("%d,%d,%d,"
                        + "%.0f,%.0f,%.0f,"
                        + "%.0f,%.0f,%.0f,"
                        + "%.1f,%.1f,"
                        + "%.1f,%.2f,%.2f,%.2f%n",
                        level, xpToNext, cumulativeXp,
                        hostileXp, eliteXp, bossXp,
                        killsHostile, killsElite, killsBoss,
                        minHostile, minElite,
                        pool,
                        hpFormula.calculateActualHP(hostile, false),
                        hpFormula.calculateActualHP(elite, false),
                        hpFormula.calculateActualHP(boss, true));
            }
        }
        System.out.printf("    → %s%n", csv.getFileName());
    }

    // =========================================================================
    // 6. BALANCE FLAGS — automatic issue detection
    // =========================================================================

    private void writeBalanceFlags(Path outputDir) throws IOException {
        Path csv = outputDir.resolve("balance_flags.csv");
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(csv))) {
            w.println("level,flag_type,severity,build,details");

            for (int level = 1; level <= MAX_LEVEL; level++) {
                double bestSurv = 0, worstSurv = Double.MAX_VALUE;
                String bestBuild = "", worstBuild = "";

                for (BuildArchetype arch : archetypes) {
                    VirtualBuild build = buildFactory.create(arch, level);
                    ComputedStats playerStats = pipeline.compute(
                            build.playerData(), BaseStats.defaults(),
                            build.skillTreeData(), build.gearBonuses());

                    // Average across MOB_SAMPLES mobs for stable results
                    double sumHostileSurv = 0, sumHostileTTK = 0, sumHostileTTD = 0;
                    double sumHostileMobDPS = 0, sumEliteSurv = 0;
                    int hostileWins = 0, eliteWins = 0;

                    for (int s = 0; s < MOB_SAMPLES; s++) {
                        long seed = BASE_SEED + level * 1000L + s;

                        MobStatProfile hostileP = mobGenerator.generate(level, 0, seed);
                        ComputedStats hostileS = hpFormula.toActualCombatStats(hostileP, false, false);
                        double concurrent = CombatSimulator.estimateConcurrentMobs(level);
                        CombatResult rH = combatSim.simulate(playerStats, hostileS, COMBAT_ITERATIONS, concurrent);
                        sumHostileSurv += Math.min(rH.survivability(), 100.0);
                        sumHostileTTK += rH.ttk();
                        sumHostileTTD += Math.min(rH.ttd(), 3600.0);
                        sumHostileMobDPS += rH.mobDPS();
                        if (rH.playerWins()) hostileWins++;

                        MobStatProfile eliteP = mobGenerator.generateSpecial(level, 0, ELITE_MULT, seed);
                        ComputedStats eliteS = hpFormula.toActualCombatStats(eliteP, false, true);
                        CombatResult rE = combatSim.simulate(playerStats, eliteS, COMBAT_ITERATIONS, concurrent);
                        sumEliteSurv += Math.min(rE.survivability(), 100.0);
                        if (rE.playerWins()) eliteWins++;
                    }

                    double surv = sumHostileSurv / MOB_SAMPLES;
                    double avgTTK = sumHostileTTK / MOB_SAMPLES;
                    double avgTTD = sumHostileTTD / MOB_SAMPLES;
                    double avgMobDPS = sumHostileMobDPS / MOB_SAMPLES;
                    double avgEliteSurv = sumEliteSurv / MOB_SAMPLES;
                    if (surv > bestSurv) { bestSurv = surv; bestBuild = arch.name(); }
                    if (surv < worstSurv) { worstSurv = surv; worstBuild = arch.name(); }

                    // Flag: Build can't kill hostile mob (avg TTK > 30s)
                    if (avgTTK > 30) {
                        w.printf("%d,SLOW_KILL,HIGH,%s,TTK=%.1fs vs HOSTILE%n", level, arch.name(), avgTTK);
                    }

                    // Flag: Build dies to hostile mob (majority of samples lose)
                    if (hostileWins < MOB_SAMPLES / 2) {
                        w.printf("%d,DIES_TO_HOSTILE,CRITICAL,%s,survivability=%.2f (wins %d/%d)%n",
                                level, arch.name(), surv, hostileWins, MOB_SAMPLES);
                    }

                    // Flag: Build is invincible (avg TTD > 120s vs hostile)
                    // 60s = very tanky but killable. 120s = functionally invincible.
                    if (avgTTD > 120) {
                        w.printf("%d,INVINCIBLE,HIGH,%s,TTD=%.1fs vs HOSTILE (effectively unkillable)%n",
                                level, arch.name(), avgTTD);
                    }

                    // Flag: Build can't kill elite (majority of samples lose)
                    if (eliteWins < MOB_SAMPLES / 2) {
                        w.printf("%d,DIES_TO_ELITE,MEDIUM,%s,survivability=%.2f vs ELITE (wins %d/%d)%n",
                                level, arch.name(), avgEliteSurv, eliteWins, MOB_SAMPLES);
                    }

                    // Flag: Block chance > 50%
                    if (playerStats.getPassiveBlockChance() > 50) {
                        w.printf("%d,HIGH_BLOCK,MEDIUM,%s,block_chance=%.1f%%%n",
                                level, arch.name(), playerStats.getPassiveBlockChance());
                    }

                    // Flag: Health regen > avg mob DPS (infinite sustain)
                    if (playerStats.getHealthRegen() > avgMobDPS && avgMobDPS > 0) {
                        w.printf("%d,OUTREGEN,HIGH,%s,regen=%.1f/s > avg_mob_dps=%.1f/s%n",
                                level, arch.name(), playerStats.getHealthRegen(), avgMobDPS);
                    }
                }

                // Flag: Survivability gap between best and worst build > 5x
                if (bestSurv > 0 && worstSurv > 0) {
                    double gap = bestSurv / worstSurv;
                    if (gap > 5) {
                        w.printf("%d,BALANCE_GAP,HIGH,ALL,best=%s(%.1f) worst=%s(%.1f) gap=%.1fx%n",
                                level, bestBuild, bestSurv, worstBuild, worstSurv, gap);
                    }
                }
            }
        }
        System.out.printf("    → %s%n", csv.getFileName());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private LevelFormula buildLevelFormula() {
        LevelingConfig.FormulaConfig formulaConfig = levelingConfig.getFormula();
        LevelingConfig.EffortConfig effort = formulaConfig.getEffort();

        EffortCurve curve = new EffortCurve(
                effort.getBaseMobs(), effort.getTargetMobs(), effort.getTargetLevel());

        MobXpEstimator estimator = new MobXpEstimator(
                levelingConfig.getXpGain().getXpPerMobLevel(),
                levelingConfig.getXpGain().getPoolMultiplier(),
                mobConfig.getPointsPerLevel(),
                mobConfig.isProgressiveScalingEnabled(),
                mobConfig.getProgressiveScalingSoftCapLevel(),
                mobConfig.getProgressiveScalingMinFactor());

        return new EffortBasedFormula(curve, estimator, formulaConfig.getMaxLevel());
    }
}
