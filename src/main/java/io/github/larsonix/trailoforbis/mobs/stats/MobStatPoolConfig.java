package io.github.larsonix.trailoforbis.mobs.stats;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * Configuration for the Mob Stat Pool system.
 *
 * <p>Controls how stat points are generated and distributed for scaled mobs.
 * Supports loading from YAML with snake_case keys.
 *
 * <p><b>YAML Structure (mob-stat-pool.yml):</b>
 * <pre>
 * points_per_level: 15.0
 * distance_bonus_per_block: 0.3
 * boss_pool_multiplier: 1.5
 * elite_pool_multiplier: 1.25
 * dirichlet_precision: 1.0    # NOT YET WIRED — see field Javadoc
 *
 * stats:
 *   max_health:
 *     factor: 0.88
 *     base_value: 100.0
 *     min_value: 1.0
 *     max_value: 100000.0
 *     alpha_weight: 1.0
 *   ...
 *
 * archetypes:
 *   warrior: [2.5, 2.5, 2.0, ...]
 *   ...
 * </pre>
 */
public class MobStatPoolConfig {

    // ==================== Pool Generation ====================

    private double pointsPerLevel = 15.0;
    private double distanceBonusPerBlock = 0.3;
    private double bossPoolMultiplier = 1.5;
    private double elitePoolMultiplier = 1.25;
    /**
     * Global concentration parameter for the Dirichlet distribution.
     *
     * <p><b>NOT YET WIRED.</b> This field loads from YAML and validates, but
     * {@link DirichletDistributor} does not read it. The actual distribution
     * variance is controlled solely by per-stat {@code alphaWeight} values.
     *
     * <p>When wired, this would multiply all alpha weights:
     * {@code effective_alpha = dirichletPrecision × base_alpha}.
     * Lower values → more extreme stat specialization per mob.
     * Higher values → more uniform stat distribution.
     */
    private double dirichletPrecision = 1.0;

    // ==================== Progressive Scaling ====================
    // Makes low-level mobs weaker, ramping up to full power at soft cap level

    private boolean progressiveScalingEnabled = true;
    private int progressiveScalingSoftCapLevel = 20;
    private double progressiveScalingMinFactor = 0.3;

    // ==================== Stat Configurations ====================

    private Map<MobStatType, StatConfig> statConfigs;

    // ==================== Mob Archetypes ====================

    // Named differently from YAML key "archetypes" to prevent SnakeYAML from
    // trying to set the field directly (it can't convert List<Number> to double[])
    private Map<String, double[]> archetypeWeights;

    // ==================== Constructor ====================

    public MobStatPoolConfig() {
        initializeDefaults();
    }

    /**
     * Initializes default stat configurations.
     * These match the values in mob-stat-pool.yml.
     */
    private void initializeDefaults() {
        statConfigs = new EnumMap<>(MobStatType.class);

        // Initialize all stats with defaults from MobStatType enum
        for (MobStatType stat : MobStatType.values()) {
            statConfigs.put(stat, StatConfig.fromMobStatType(stat));
        }

        // Apply custom conversion factors (matching YAML defaults)
        statConfigs.get(MobStatType.MAX_HEALTH).setFactor(0.88);
        statConfigs.get(MobStatType.PHYSICAL_DAMAGE).setFactor(0.067);
        statConfigs.get(MobStatType.ARMOR).setFactor(0.282);
        statConfigs.get(MobStatType.MOVE_SPEED).setFactor(0.0022);
        statConfigs.get(MobStatType.ATTACK_SPEED).setFactor(0.001);
        statConfigs.get(MobStatType.ATTACK_RANGE).setFactor(0.1);
        statConfigs.get(MobStatType.ATTACK_COOLDOWN).setFactor(-0.05);
        statConfigs.get(MobStatType.CRITICAL_CHANCE).setFactor(0.11);
        statConfigs.get(MobStatType.CRITICAL_MULTIPLIER).setFactor(0.44);
        statConfigs.get(MobStatType.DODGE_CHANCE).setFactor(0.11);
        statConfigs.get(MobStatType.BLOCK_CHANCE).setFactor(0.08);
        statConfigs.get(MobStatType.PARRY_CHANCE).setFactor(0.06);
        statConfigs.get(MobStatType.LIFE_STEAL).setFactor(0.066);
        statConfigs.get(MobStatType.HEALTH_REGEN).setFactor(0.044);
        statConfigs.get(MobStatType.FIRE_DAMAGE).setFactor(0.2);
        statConfigs.get(MobStatType.WATER_DAMAGE).setFactor(0.2);
        statConfigs.get(MobStatType.LIGHTNING_DAMAGE).setFactor(0.2);
        statConfigs.get(MobStatType.EARTH_DAMAGE).setFactor(0.2);
        statConfigs.get(MobStatType.WIND_DAMAGE).setFactor(0.2);
        statConfigs.get(MobStatType.VOID_DAMAGE).setFactor(0.2);
        statConfigs.get(MobStatType.FIRE_RESISTANCE).setFactor(0.5);
        statConfigs.get(MobStatType.WATER_RESISTANCE).setFactor(0.5);
        statConfigs.get(MobStatType.LIGHTNING_RESISTANCE).setFactor(0.5);
        statConfigs.get(MobStatType.EARTH_RESISTANCE).setFactor(0.5);
        statConfigs.get(MobStatType.WIND_RESISTANCE).setFactor(0.5);
        statConfigs.get(MobStatType.VOID_RESISTANCE).setFactor(0.5);
        statConfigs.get(MobStatType.FIRE_PENETRATION).setFactor(0.3);
        statConfigs.get(MobStatType.WATER_PENETRATION).setFactor(0.3);
        statConfigs.get(MobStatType.LIGHTNING_PENETRATION).setFactor(0.3);
        statConfigs.get(MobStatType.EARTH_PENETRATION).setFactor(0.3);
        statConfigs.get(MobStatType.WIND_PENETRATION).setFactor(0.3);
        statConfigs.get(MobStatType.VOID_PENETRATION).setFactor(0.3);
        // Elemental Increased Damage (additive % bonus)
        statConfigs.get(MobStatType.FIRE_INCREASED_DAMAGE).setFactor(0.5);
        statConfigs.get(MobStatType.WATER_INCREASED_DAMAGE).setFactor(0.5);
        statConfigs.get(MobStatType.LIGHTNING_INCREASED_DAMAGE).setFactor(0.5);
        statConfigs.get(MobStatType.EARTH_INCREASED_DAMAGE).setFactor(0.5);
        statConfigs.get(MobStatType.WIND_INCREASED_DAMAGE).setFactor(0.5);
        statConfigs.get(MobStatType.VOID_INCREASED_DAMAGE).setFactor(0.5);
        // Elemental More Damage (multiplicative % bonus)
        statConfigs.get(MobStatType.FIRE_MORE_DAMAGE).setFactor(0.25);
        statConfigs.get(MobStatType.WATER_MORE_DAMAGE).setFactor(0.25);
        statConfigs.get(MobStatType.LIGHTNING_MORE_DAMAGE).setFactor(0.25);
        statConfigs.get(MobStatType.EARTH_MORE_DAMAGE).setFactor(0.25);
        statConfigs.get(MobStatType.WIND_MORE_DAMAGE).setFactor(0.25);
        statConfigs.get(MobStatType.VOID_MORE_DAMAGE).setFactor(0.25);
        statConfigs.get(MobStatType.AGGRO_RANGE).setFactor(0.1);
        statConfigs.get(MobStatType.REACTION_DELAY).setFactor(-0.1);
        statConfigs.get(MobStatType.CHARGE_TIME).setFactor(-0.1);
        statConfigs.get(MobStatType.CHARGE_DISTANCE).setFactor(0.1);
        statConfigs.get(MobStatType.ARMOR_PENETRATION).setFactor(0.176);
        statConfigs.get(MobStatType.TRUE_DAMAGE).setFactor(0.1);
        statConfigs.get(MobStatType.ACCURACY).setFactor(0.5);
        statConfigs.get(MobStatType.KNOCKBACK_RESISTANCE).setFactor(0.25);

        // Set base values for stats that have non-zero defaults
        statConfigs.get(MobStatType.CRITICAL_CHANCE).setBaseValue(5.0);
        statConfigs.get(MobStatType.CRITICAL_MULTIPLIER).setBaseValue(150.0);
        statConfigs.get(MobStatType.MOVE_SPEED).setBaseValue(1.0);
        statConfigs.get(MobStatType.ATTACK_SPEED).setBaseValue(1.0);
        statConfigs.get(MobStatType.ACCURACY).setBaseValue(100.0);

        // Initialize default archetypes (52 weights per archetype)
        // Order: MAX_HEALTH, PHYSICAL_DAMAGE, ARMOR, MOVE_SPEED, ATTACK_SPEED, ATTACK_RANGE,
        //        ATTACK_COOLDOWN, CRITICAL_CHANCE, CRITICAL_MULTIPLIER, DODGE_CHANCE,
        //        BLOCK_CHANCE, PARRY_CHANCE, LIFE_STEAL, HEALTH_REGEN,
        //        FIRE_DAMAGE, WATER_DAMAGE, LIGHTNING_DAMAGE, EARTH_DAMAGE, WIND_DAMAGE, VOID_DAMAGE,
        //        FIRE_RESISTANCE, WATER_RESISTANCE, LIGHTNING_RESISTANCE, EARTH_RESISTANCE, WIND_RESISTANCE, VOID_RESISTANCE,
        //        FIRE_PENETRATION, WATER_PENETRATION, LIGHTNING_PENETRATION, EARTH_PENETRATION, WIND_PENETRATION, VOID_PENETRATION,
        //        FIRE_INCREASED_DAMAGE, WATER_INCREASED_DAMAGE, LIGHTNING_INCREASED_DAMAGE, EARTH_INCREASED_DAMAGE, WIND_INCREASED_DAMAGE, VOID_INCREASED_DAMAGE,
        //        FIRE_MORE_DAMAGE, WATER_MORE_DAMAGE, LIGHTNING_MORE_DAMAGE, EARTH_MORE_DAMAGE, WIND_MORE_DAMAGE, VOID_MORE_DAMAGE,
        //        AGGRO_RANGE, REACTION_DELAY, CHARGE_TIME, CHARGE_DISTANCE,
        //        ARMOR_PENETRATION, TRUE_DAMAGE, ACCURACY, KNOCKBACK_RESISTANCE
        archetypeWeights = new HashMap<>();
        archetypeWeights.put("warrior", new double[]{
            2.5, 2.5, 2.0, 0.8, 0.8, 0.8, 1.5, 1.5, 1.0, 1.0,
            1.0, 1.0, 1.0, 1.0, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, // damage: fire/water/lightning/earth/wind/void
            0.5, 0.5, 0.5, 0.5, 0.5, 0.5, // resistance: fire/water/lightning/earth/wind/void
            0.3, 0.3, 0.3, 0.3, 0.3, 0.3, // penetration: fire/water/lightning/earth/wind/void
            0.3, 0.3, 0.3, 0.3, 0.3, 0.3, // increased damage (low - physical focus)
            0.3, 0.3, 0.3, 0.3, 0.3, 0.3, // more damage (low - physical focus)
            0.5, 0.8, 1.0, 1.0,
            1.0, 1.0, 1.5, 2.0
        });
        archetypeWeights.put("ranger", new double[]{
            1.0, 1.5, 0.8, 2.5, 2.0, 1.0, 1.0, 1.0, 1.0, 1.0,
            1.0, 1.0, 1.0, 1.0, 0.5, 0.5, 0.5, 0.5, 1.0, 0.5, // damage: wind affinity
            0.5, 0.5, 0.5, 0.5, 0.8, 0.5, // resistance: wind affinity
            0.3, 0.3, 0.3, 0.3, 0.5, 0.3, // penetration: wind affinity
            0.3, 0.3, 0.3, 0.3, 0.5, 0.3, // increased damage: wind affinity
            0.3, 0.3, 0.3, 0.3, 0.5, 0.3, // more damage: wind affinity
            1.5, 1.5, 0.8, 1.0,
            1.0, 1.0, 2.0, 0.5
        });
        archetypeWeights.put("mage", new double[]{
            1.0, 1.0, 0.5, 0.8, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0,
            0.5, 0.5, 0.5, 1.0, 2.5, 2.5, 2.5, 2.5, 2.5, 2.5, // damage: all elements
            1.0, 1.0, 1.0, 1.0, 1.0, 1.0, // resistance: all elements
            2.0, 2.0, 2.0, 2.0, 2.0, 2.0, // penetration: all elements
            2.5, 2.5, 2.5, 2.5, 2.5, 2.5, // increased damage (high - elemental focus)
            1.5, 1.5, 1.5, 1.5, 1.5, 1.5, // more damage (medium-high - elemental focus)
            0.5, 0.8, 1.0, 0.5,
            1.0, 1.0, 1.0, 0.3
        });
        archetypeWeights.put("tank", new double[]{
            3.0, 1.5, 3.0, 0.5, 0.5, 1.0, 2.0, 2.0, 1.0, 1.0,
            2.0, 2.0, 2.0, 1.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, // damage: all low
            2.0, 2.0, 2.0, 2.0, 2.0, 2.0, // resistance: all high
            0.3, 0.3, 0.3, 0.3, 0.3, 0.3, // penetration: all low
            0.3, 0.3, 0.3, 0.3, 0.3, 0.3, // increased damage (low - defensive focus)
            0.3, 0.3, 0.3, 0.3, 0.3, 0.3, // more damage (low - defensive focus)
            0.5, 0.5, 1.5, 0.5,
            1.0, 1.0, 1.0, 3.0
        });
        archetypeWeights.put("assassin", new double[]{
            0.8, 2.5, 0.5, 2.0, 1.5, 1.0, 1.0, 1.0, 2.5, 2.0,
            2.0, 1.0, 1.0, 1.5, 0.5, 0.5, 0.5, 0.5, 0.8, 0.5, // damage: slight wind affinity
            0.5, 0.5, 0.5, 0.5, 0.5, 0.5, // resistance: all low
            0.5, 0.5, 0.5, 0.5, 0.5, 0.5, // penetration: all medium
            0.5, 0.5, 0.5, 0.5, 0.5, 0.5, // increased damage (medium - balanced)
            0.5, 0.5, 0.5, 0.5, 0.5, 0.5, // more damage (medium - balanced)
            1.0, 1.0, 0.5, 2.0,
            1.0, 1.0, 2.5, 0.3
        });

        // === ELEMENTAL SPECIALISTS ===

        // Pyromancer - Fire specialist
        archetypeWeights.put("pyromancer", new double[]{
            0.8, 0.3, 0.3, 1.0, 1.2, 1.5, 1.0, 1.5, 2.0, 0.8,
            0.3, 0.3, 0.5, 0.8, 3.5, 0.0, 0.5, 0.3, 0.3, 0.5, // fire focus, low earth/wind
            3.0, 0.0, 0.5, 0.3, 0.3, 0.5, // fire resistance high
            3.0, 0.0, 0.3, 0.3, 0.3, 0.3, // fire penetration high
            3.0, 0.0, 0.3, 0.3, 0.3, 0.3, // fire increased only
            2.5, 0.0, 0.3, 0.3, 0.3, 0.3, // fire more only
            1.0, 1.0, 1.5, 0.5,
            0.5, 0.5, 1.5, 0.3
        });

        // Frost Warden - Water/Cold tank
        archetypeWeights.put("frost_warden", new double[]{
            2.5, 1.0, 2.5, 0.6, 0.7, 1.0, 1.5, 0.8, 1.0, 0.5,
            2.0, 1.5, 0.5, 1.5, 0.0, 3.0, 0.3, 0.3, 0.3, 0.3, // water focus
            0.0, 3.0, 1.0, 0.5, 0.5, 1.0, // water resistance high
            0.0, 2.5, 0.3, 0.3, 0.3, 0.3, // water penetration high
            0.0, 2.5, 0.3, 0.3, 0.3, 0.3, // water increased only
            0.0, 2.0, 0.3, 0.3, 0.3, 0.3, // water more only
            0.8, 0.5, 2.0, 0.5,
            0.8, 0.5, 1.0, 2.5
        });

        // Storm Caller - Lightning speed specialist (with wind affinity)
        archetypeWeights.put("storm_caller", new double[]{
            1.0, 0.5, 0.5, 2.0, 3.0, 2.0, 0.5, 2.0, 1.5, 1.5,
            0.5, 0.5, 0.5, 0.8, 0.3, 0.3, 3.5, 0.3, 2.0, 0.3, // lightning + wind damage
            0.5, 0.5, 3.0, 0.3, 1.5, 0.5, // lightning + wind resistance
            0.3, 0.3, 3.0, 0.3, 1.5, 0.3, // lightning + wind penetration
            0.3, 0.3, 3.0, 0.3, 1.5, 0.3, // lightning + wind increased
            0.3, 0.3, 2.5, 0.3, 1.2, 0.3, // lightning + wind more
            2.0, 2.5, 0.3, 1.5,
            0.5, 0.5, 2.0, 0.5
        });

        // Void Weaver - Void specialist
        archetypeWeights.put("void_weaver", new double[]{
            1.2, 0.3, 0.5, 1.0, 1.0, 2.0, 1.0, 1.5, 2.0, 1.5,
            0.3, 0.3, 1.5, 0.5, 0.3, 0.3, 0.3, 0.3, 0.3, 3.5, // void damage focus
            0.5, 0.5, 0.5, 0.3, 0.3, 3.0, // void resistance focus
            0.3, 0.3, 0.3, 0.3, 0.3, 3.0, // void penetration focus
            0.3, 0.3, 0.3, 0.3, 0.3, 3.0, // void increased only
            0.3, 0.3, 0.3, 0.3, 0.3, 2.5, // void more only
            1.5, 1.0, 1.0, 1.0,
            0.5, 2.5, 1.2, 1.0
        });

        // === COMBAT SPECIALISTS ===

        // Berserker - Rage damage, life steal sustain
        archetypeWeights.put("berserker", new double[]{
            1.5, 3.5, 0.3, 2.0, 2.5, 0.8, 0.5, 2.0, 2.0, 0.3,
            0.3, 0.3, 3.0, 0.5, 0.8, 0.3, 0.3, 0.3, 0.3, 0.8, // fire and void affinity
            0.3, 0.3, 0.3, 0.3, 0.3, 0.3, // low elemental resistance
            0.5, 0.3, 0.3, 0.3, 0.3, 0.5, // some fire/void penetration
            0.5, 0.3, 0.3, 0.3, 0.3, 0.5, // some fire/void increased
            0.5, 0.3, 0.3, 0.3, 0.3, 0.5, // some fire/void more
            2.0, 0.3, 0.3, 2.5,
            2.0, 1.0, 1.5, 0.5
        });

        // Juggernaut - Unstoppable charger (earth affinity for tankiness)
        archetypeWeights.put("juggernaut", new double[]{
            3.0, 2.0, 2.5, 1.5, 0.5, 1.0, 2.0, 0.5, 1.0, 0.3,
            1.5, 0.5, 0.5, 1.0, 0.5, 0.5, 0.5, 0.8, 0.3, 0.5, // slight earth damage
            1.5, 1.5, 1.5, 2.0, 0.8, 1.5, // earth resistance high
            0.5, 0.5, 0.5, 0.5, 0.3, 0.5, // penetration low
            0.5, 0.5, 0.5, 0.5, 0.3, 0.5, // increased damage low
            0.5, 0.5, 0.5, 0.5, 0.3, 0.5, // more damage low
            1.5, 0.5, 0.3, 3.5,
            2.5, 1.5, 0.8, 3.5
        });

        // Duelist - Technical fighter with parry/dodge (wind affinity for speed)
        archetypeWeights.put("duelist", new double[]{
            1.0, 1.5, 1.0, 1.5, 1.8, 1.2, 0.8, 2.5, 2.0, 2.5,
            1.5, 3.0, 0.5, 0.8, 0.3, 0.3, 0.3, 0.3, 0.8, 0.3, // slight wind damage
            0.8, 0.8, 0.8, 0.5, 1.0, 0.8, // wind resistance medium
            0.5, 0.5, 0.5, 0.3, 0.8, 0.5, // wind penetration medium
            0.5, 0.5, 0.5, 0.3, 0.8, 0.5, // wind increased medium
            0.5, 0.5, 0.5, 0.3, 0.8, 0.5, // wind more medium
            1.2, 2.0, 0.5, 1.5,
            1.5, 0.5, 3.0, 1.0
        });

        // Ravager - Armor penetration specialist
        archetypeWeights.put("ravager", new double[]{
            1.5, 2.0, 0.8, 1.8, 2.0, 1.0, 0.8, 1.5, 1.5, 1.0,
            0.5, 0.5, 1.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 1.0, // void affinity
            0.5, 0.5, 0.5, 0.5, 0.5, 1.0, // void resistance
            1.0, 1.0, 1.0, 0.8, 0.8, 1.5, // all penetration, void high
            0.8, 0.8, 0.8, 0.5, 0.5, 1.0, // void increased
            0.8, 0.8, 0.8, 0.5, 0.5, 1.0, // void more
            1.5, 1.5, 0.5, 1.5,
            3.5, 3.0, 1.8, 0.8
        });

        // === HYBRID/SPECIAL ARCHETYPES ===

        // Spellblade - Physical + elemental hybrid (all elements balanced)
        archetypeWeights.put("spellblade", new double[]{
            1.5, 2.0, 1.5, 1.2, 1.5, 1.0, 1.0, 1.5, 1.5, 1.0,
            1.0, 1.0, 0.8, 0.8, 1.5, 1.5, 1.5, 1.2, 1.2, 1.0, // all elements balanced
            1.0, 1.0, 1.0, 1.0, 1.0, 0.8, // all resistance balanced
            1.2, 1.2, 1.2, 1.0, 1.0, 0.8, // all penetration balanced
            1.5, 1.5, 1.5, 1.2, 1.2, 0.8, // all increased balanced
            1.2, 1.2, 1.2, 1.0, 1.0, 0.8, // all more balanced
            1.0, 1.2, 0.8, 1.2,
            1.5, 0.8, 1.5, 1.2
        });

        // Necromancer - Water/Void life drain
        archetypeWeights.put("necromancer", new double[]{
            1.0, 0.5, 0.5, 0.8, 0.8, 2.0, 1.5, 1.0, 1.5, 1.0,
            0.3, 0.3, 3.5, 2.5, 0.3, 2.0, 0.3, 0.3, 0.3, 2.5, // water and void damage
            0.5, 2.0, 0.5, 0.3, 0.3, 2.5, // water and void resistance
            0.3, 1.5, 0.3, 0.3, 0.3, 2.0, // water and void penetration
            0.3, 1.5, 0.3, 0.3, 0.3, 2.0, // water and void increased
            0.3, 1.2, 0.3, 0.3, 0.3, 1.5, // water and void more
            1.5, 0.8, 1.5, 0.5,
            0.5, 1.5, 1.2, 0.5
        });

        // Elemental - Pure elemental being, all elements equally
        archetypeWeights.put("elemental", new double[]{
            1.5, 0.0, 0.5, 1.5, 1.5, 1.5, 1.0, 1.0, 1.5, 1.5,
            0.3, 0.3, 0.5, 1.5, 2.0, 2.0, 2.0, 2.0, 2.0, 2.0, // all elemental damage
            2.0, 2.0, 2.0, 2.0, 2.0, 2.0, // all elemental resistance
            1.5, 1.5, 1.5, 1.5, 1.5, 1.5, // all elemental penetration
            2.0, 2.0, 2.0, 2.0, 2.0, 2.0, // all elemental increased
            1.5, 1.5, 1.5, 1.5, 1.5, 1.5, // all elemental more
            1.5, 1.2, 1.0, 1.0,
            0.3, 1.0, 1.5, 1.0
        });

        // Shade - Shadow assassin with void
        archetypeWeights.put("shade", new double[]{
            0.6, 1.5, 0.3, 2.5, 2.0, 1.0, 0.5, 2.5, 2.5, 3.0,
            0.3, 0.3, 1.0, 0.3, 0.3, 0.5, 0.3, 0.3, 0.3, 2.5, // void damage
            0.3, 1.0, 0.3, 0.3, 0.3, 2.5, // void resistance
            0.3, 0.5, 0.3, 0.3, 0.3, 2.5, // void penetration
            0.3, 0.5, 0.3, 0.3, 0.3, 2.5, // void increased
            0.3, 0.5, 0.3, 0.3, 0.3, 2.0, // void more
            2.5, 2.5, 0.3, 2.0,
            1.0, 1.5, 2.5, 0.3
        });

        // Guardian - Ultimate blocker/defender (earth affinity)
        archetypeWeights.put("guardian", new double[]{
            3.5, 1.0, 3.5, 0.5, 0.5, 1.5, 2.0, 0.5, 1.0, 0.5,
            3.5, 2.5, 0.3, 2.0, 0.3, 0.3, 0.3, 0.5, 0.3, 0.3, // slight earth damage
            2.5, 2.5, 2.5, 3.0, 2.0, 2.5, // all resistance high, earth highest
            0.3, 0.3, 0.3, 0.3, 0.3, 0.3, // low penetration
            0.3, 0.3, 0.3, 0.3, 0.3, 0.3, // low increased
            0.3, 0.3, 0.3, 0.3, 0.3, 0.3, // low more
            1.0, 0.5, 2.5, 0.3,
            0.5, 0.3, 0.8, 3.5
        });

        // Executioner - Slow but devastating one-shot
        archetypeWeights.put("executioner", new double[]{
            2.0, 3.5, 1.5, 0.5, 0.3, 1.5, 3.0, 1.0, 3.0, 0.3,
            0.5, 0.5, 0.5, 1.0, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, // balanced elemental damage
            1.0, 1.0, 1.0, 1.0, 1.0, 1.0, // all resistance medium
            0.5, 0.5, 0.5, 0.5, 0.5, 0.5, // all penetration medium
            0.5, 0.5, 0.5, 0.5, 0.5, 0.5, // all increased medium
            0.5, 0.5, 0.5, 0.5, 0.5, 0.5, // all more medium
            1.0, 0.3, 3.0, 0.5,
            3.0, 2.5, 1.0, 2.5
        });
    }

    // ==================== Factory ====================

    /**
     * Creates a config with default values.
     * @return New config instance with defaults
     */
    @Nonnull
    public static MobStatPoolConfig createDefaults() {
        return new MobStatPoolConfig();
    }

    // ==================== YAML Snake_Case Setters ====================

    /**
     * YAML setter for 'points_per_level'.
     */
    public void setPoints_per_level(double value) {
        this.pointsPerLevel = value;
    }

    /**
     * YAML setter for 'distance_bonus_per_block'.
     */
    public void setDistance_bonus_per_block(double value) {
        this.distanceBonusPerBlock = value;
    }

    /**
     * YAML setter for 'boss_pool_multiplier'.
     */
    public void setBoss_pool_multiplier(double value) {
        this.bossPoolMultiplier = value;
    }

    /**
     * YAML setter for 'elite_pool_multiplier'.
     */
    public void setElite_pool_multiplier(double value) {
        this.elitePoolMultiplier = value;
    }

    /**
     * YAML setter for 'dirichlet_precision'.
     */
    public void setDirichlet_precision(double value) {
        this.dirichletPrecision = value;
    }

    /**
     * YAML setter for 'progressive_scaling' section.
     * Parses nested progressive scaling configuration from YAML.
     */
    public void setProgressive_scaling(Map<String, Object> scalingMap) {
        if (scalingMap == null || scalingMap.isEmpty()) {
            return;
        }

        if (scalingMap.containsKey("enabled")) {
            Object val = scalingMap.get("enabled");
            this.progressiveScalingEnabled = val instanceof Boolean ? (Boolean) val : Boolean.parseBoolean(val.toString());
        }
        if (scalingMap.containsKey("soft_cap_level")) {
            this.progressiveScalingSoftCapLevel = toInt(scalingMap.get("soft_cap_level"));
        }
        if (scalingMap.containsKey("min_scaling_factor")) {
            this.progressiveScalingMinFactor = toDouble(scalingMap.get("min_scaling_factor"));
        }
    }

    /**
     * YAML setter for 'stats' map.
     * Parses nested stat configurations from YAML.
     */
    public void setStats(Map<String, Map<String, Object>> statsMap) {
        if (statsMap == null || statsMap.isEmpty()) {
            return;
        }

        for (Map.Entry<String, Map<String, Object>> entry : statsMap.entrySet()) {
            String statName = entry.getKey();
            Map<String, Object> statData = entry.getValue();

            MobStatType statType = findStatType(statName);
            if (statType == null) {
                continue; // Skip unknown stats
            }

            StatConfig config = statConfigs.computeIfAbsent(statType, StatConfig::fromMobStatType);
            applyStatConfigFromYaml(config, statData);
        }
    }

    /**
     * YAML setter for 'archetypes' map.
     * Parses archetype weight arrays from YAML.
     *
     * <p>Note: SnakeYAML may provide List<Object> containing mixed Integer/Double types,
     * so we accept Object and convert each value to double.
     */
    @SuppressWarnings("unchecked")
    public void setArchetypes(Map<String, List<Object>> archetypesMap) {
        if (archetypesMap == null || archetypesMap.isEmpty()) {
            return;
        }

        this.archetypeWeights = new HashMap<>();
        for (Map.Entry<String, List<Object>> entry : archetypesMap.entrySet()) {
            String name = entry.getKey().toLowerCase();
            List<Object> weights = entry.getValue();

            if (weights != null && !weights.isEmpty()) {
                double[] arr = new double[weights.size()];
                for (int i = 0; i < weights.size(); i++) {
                    Object val = weights.get(i);
                    if (val instanceof Number) {
                        arr[i] = ((Number) val).doubleValue();
                    } else if (val instanceof String) {
                        try {
                            arr[i] = Double.parseDouble((String) val);
                        } catch (NumberFormatException e) {
                            arr[i] = 0.0;
                        }
                    } else {
                        arr[i] = 0.0;
                    }
                }
                this.archetypeWeights.put(name, arr);
            }
        }
    }

    // ==================== Helper Methods ====================

    /**
     * Finds a MobStatType by its config key (snake_case).
     */
    private MobStatType findStatType(String name) {
        String normalized = name.toLowerCase().replace("-", "_");
        for (MobStatType type : MobStatType.values()) {
            if (type.configKey.equalsIgnoreCase(normalized) ||
                type.configKey.replace("_", "").equalsIgnoreCase(normalized.replace("_", "")) ||
                type.name().equalsIgnoreCase(normalized.replace("_", ""))) {
                return type;
            }
        }
        return null;
    }

    /**
     * Applies YAML stat configuration to a StatConfig object.
     */
    private void applyStatConfigFromYaml(StatConfig config, Map<String, Object> data) {
        if (data.containsKey("factor")) {
            config.setFactor(toDouble(data.get("factor")));
        }
        if (data.containsKey("base_value")) {
            config.setBaseValue(toDouble(data.get("base_value")));
        }
        if (data.containsKey("min_value")) {
            config.setMinValue(toDouble(data.get("min_value")));
        }
        if (data.containsKey("max_value")) {
            config.setMaxValue(toDouble(data.get("max_value")));
        }
        if (data.containsKey("alpha_weight")) {
            config.setAlphaWeight(toDouble(data.get("alpha_weight")));
        }
    }

    /**
     * Safely converts an Object to double.
     */
    private double toDouble(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                return 0.0;
            }
        }
        return 0.0;
    }

    /**
     * Safely converts an Object to int.
     */
    private int toInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    // ==================== Standard Getters/Setters ====================

    public double getPointsPerLevel() {
        return pointsPerLevel;
    }

    public void setPointsPerLevel(double pointsPerLevel) {
        this.pointsPerLevel = pointsPerLevel;
    }

    public double getDistanceBonusPerBlock() {
        return distanceBonusPerBlock;
    }

    public void setDistanceBonusPerBlock(double distanceBonusPerBlock) {
        this.distanceBonusPerBlock = distanceBonusPerBlock;
    }

    public double getBossPoolMultiplier() {
        return bossPoolMultiplier;
    }

    public void setBossPoolMultiplier(double bossPoolMultiplier) {
        this.bossPoolMultiplier = bossPoolMultiplier;
    }

    public double getElitePoolMultiplier() {
        return elitePoolMultiplier;
    }

    public void setElitePoolMultiplier(double elitePoolMultiplier) {
        this.elitePoolMultiplier = elitePoolMultiplier;
    }

    public double getDirichletPrecision() {
        return dirichletPrecision;
    }

    public void setDirichletPrecision(double dirichletPrecision) {
        this.dirichletPrecision = dirichletPrecision;
    }

    // ==================== Progressive Scaling Getters/Setters ====================

    public boolean isProgressiveScalingEnabled() {
        return progressiveScalingEnabled;
    }

    public void setProgressiveScalingEnabled(boolean enabled) {
        this.progressiveScalingEnabled = enabled;
    }

    public int getProgressiveScalingSoftCapLevel() {
        return progressiveScalingSoftCapLevel;
    }

    public void setProgressiveScalingSoftCapLevel(int level) {
        this.progressiveScalingSoftCapLevel = level;
    }

    public double getProgressiveScalingMinFactor() {
        return progressiveScalingMinFactor;
    }

    public void setProgressiveScalingMinFactor(double factor) {
        this.progressiveScalingMinFactor = factor;
    }

    /**
     * Calculates the progressive scaling factor for a given mob level.
     *
     * <p>This creates a smooth difficulty curve where low-level mobs are weaker
     * and gradually ramp up to full power at the soft cap level.
     *
     * <p>Formula: {@code factor = minFactor + (1 - minFactor) * (level / softCapLevel)}
     * <br>Capped at 1.0 once level reaches or exceeds soft cap.
     *
     * <p>Example with defaults (minFactor=0.3, softCap=20):
     * <ul>
     *   <li>Level 1: 0.3 + 0.7 * (1/20) = 0.335 (33.5% power)</li>
     *   <li>Level 5: 0.3 + 0.7 * (5/20) = 0.475 (47.5% power)</li>
     *   <li>Level 10: 0.3 + 0.7 * (10/20) = 0.65 (65% power)</li>
     *   <li>Level 20+: 1.0 (100% power)</li>
     * </ul>
     *
     * @param mobLevel The mob's level
     * @return Scaling factor between minFactor and 1.0
     */
    public double calculateScalingFactor(int mobLevel) {
        if (!progressiveScalingEnabled) {
            return 1.0;
        }

        if (mobLevel >= progressiveScalingSoftCapLevel) {
            return 1.0;
        }

        // Linear interpolation from minFactor to 1.0
        double progress = (double) mobLevel / progressiveScalingSoftCapLevel;
        return progressiveScalingMinFactor + (1.0 - progressiveScalingMinFactor) * progress;
    }

    @Nonnull
    public Map<MobStatType, StatConfig> getStatConfigs() {
        return statConfigs;
    }

    public void setStatConfigs(Map<MobStatType, StatConfig> statConfigs) {
        this.statConfigs = statConfigs;
    }

    @Nonnull
    public StatConfig getStatConfig(@Nonnull MobStatType type) {
        return statConfigs.getOrDefault(type, StatConfig.DEFAULT);
    }

    public double getAlphaWeight(@Nonnull MobStatType type) {
        StatConfig config = statConfigs.get(type);
        if (config != null) {
            return config.getAlphaWeight();
        }
        return type.alphaWeight;
    }

    /**
     * Returns all archetype weight maps.
     * Note: Named to avoid JavaBean property pattern matching with setArchetypes()
     * which would cause SnakeYAML type inference issues.
     */
    @Nonnull
    public Map<String, double[]> getAllArchetypes() {
        return archetypeWeights;
    }

    /**
     * Gets archetype weights by name.
     * @param archetype Archetype name (e.g., "warrior", "mage")
     * @return Weight array, or null if not found
     */
    public double[] getArchetypeWeights(String archetype) {
        if (archetype == null) return null;
        return archetypeWeights.get(archetype.toLowerCase());
    }

    // ==================== Legacy Compatibility ====================

    /**
     * @deprecated Use {@link #getArchetypeWeights(String)} instead
     */
    @Deprecated
    public Map<String, double[]> getMobTypeWeights() {
        return archetypeWeights;
    }

    /**
     * @deprecated Use {@link #getArchetypeWeights(String)} instead
     */
    @Deprecated
    public double[] getMobTypeWeights(String mobType) {
        return getArchetypeWeights(mobType);
    }

    /**
     * @deprecated Use {@link #getStatConfig(MobStatType)} with getAlphaWeight() instead
     */
    @Deprecated
    public Map<MobStatType, Double> getStatAlphaWeights() {
        Map<MobStatType, Double> weights = new EnumMap<>(MobStatType.class);
        for (Map.Entry<MobStatType, StatConfig> entry : statConfigs.entrySet()) {
            weights.put(entry.getKey(), entry.getValue().getAlphaWeight());
        }
        return weights;
    }

    /**
     * @deprecated Use {@link #setStats(Map)} via YAML loading instead
     */
    @Deprecated
    public void setStatAlphaWeights(Map<MobStatType, Double> statAlphaWeights) {
        if (statAlphaWeights != null) {
            for (Map.Entry<MobStatType, Double> entry : statAlphaWeights.entrySet()) {
                StatConfig config = statConfigs.get(entry.getKey());
                if (config != null) {
                    config.setAlphaWeight(entry.getValue());
                }
            }
        }
    }

    /**
     * @deprecated Use {@link #setArchetypes(Map)} via YAML loading instead
     */
    @Deprecated
    public void setMobTypeWeights(Map<String, double[]> mobTypeWeights) {
        this.archetypeWeights = mobTypeWeights;
    }

    // ==================== Validation ====================

    /**
     * Validates the configuration values.
     *
     * @throws ConfigValidationException if validation fails
     */
    public void validate() throws ConfigValidationException {
        if (pointsPerLevel < 0) {
            throw new ConfigValidationException("points_per_level must be >= 0");
        }
        if (distanceBonusPerBlock < 0) {
            throw new ConfigValidationException("distance_bonus_per_block must be >= 0");
        }
        if (bossPoolMultiplier < 1.0) {
            throw new ConfigValidationException("boss_pool_multiplier must be >= 1.0");
        }
        if (elitePoolMultiplier < 1.0) {
            throw new ConfigValidationException("elite_pool_multiplier must be >= 1.0");
        }
        if (dirichletPrecision <= 0) {
            throw new ConfigValidationException("dirichlet_precision must be > 0");
        }

        // Validate stat configs
        for (Map.Entry<MobStatType, StatConfig> entry : statConfigs.entrySet()) {
            StatConfig config = entry.getValue();
            if (config.getMinValue() > config.getMaxValue()) {
                throw new ConfigValidationException(
                    "stats." + entry.getKey().configKey + ": min_value > max_value");
            }
            if (config.getAlphaWeight() < 0) {
                throw new ConfigValidationException(
                    "stats." + entry.getKey().configKey + ": alpha_weight must be >= 0");
            }
        }

        // Validate archetypes have correct number of weights
        int expectedWeights = MobStatType.values().length;
        for (Map.Entry<String, double[]> entry : archetypeWeights.entrySet()) {
            if (entry.getValue().length != expectedWeights) {
                throw new ConfigValidationException(
                    "archetypes." + entry.getKey() + ": expected " + expectedWeights +
                    " weights, got " + entry.getValue().length);
            }
        }
    }

    /**
     * Exception thrown when config validation fails.
     */
    public static class ConfigValidationException extends Exception {
        public ConfigValidationException(String message) {
            super(message);
        }
    }

    // ==================== Diagnostics ====================

    /**
     * Returns a diagnostic summary of the configuration.
     *
     * @return Multi-line string with config summary
     */
    @Nonnull
    public String getDiagnostics() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== MobStatPoolConfig ===\n");
        sb.append("\n[Progressive Scaling]\n");
        sb.append("  enabled: ").append(progressiveScalingEnabled).append("\n");
        sb.append("  soft_cap_level: ").append(progressiveScalingSoftCapLevel).append("\n");
        sb.append("  min_scaling_factor: ").append(progressiveScalingMinFactor).append("\n");
        if (progressiveScalingEnabled) {
            sb.append("  Example factors: Lv1=").append(String.format("%.0f%%", calculateScalingFactor(1) * 100));
            sb.append(", Lv5=").append(String.format("%.0f%%", calculateScalingFactor(5) * 100));
            sb.append(", Lv10=").append(String.format("%.0f%%", calculateScalingFactor(10) * 100));
            sb.append(", Lv20=").append(String.format("%.0f%%", calculateScalingFactor(20) * 100)).append("\n");
        }
        sb.append("\n[Pool Generation]\n");
        sb.append("  points_per_level: ").append(pointsPerLevel).append("\n");
        sb.append("  distance_bonus_per_block: ").append(distanceBonusPerBlock).append("\n");
        sb.append("  boss_pool_multiplier: ").append(bossPoolMultiplier).append("\n");
        sb.append("  elite_pool_multiplier: ").append(elitePoolMultiplier).append("\n");
        sb.append("  dirichlet_precision: ").append(dirichletPrecision).append("\n");

        sb.append("\n[Stat Configs] (").append(statConfigs.size()).append(" stats)\n");
        for (MobStatType type : MobStatType.values()) {
            StatConfig config = statConfigs.get(type);
            if (config != null) {
                sb.append(String.format("  %s: factor=%.4f, base=%.1f, range=[%.1f-%.1f], alpha=%.2f\n",
                    type.configKey, config.getFactor(), config.getBaseValue(),
                    config.getMinValue(), config.getMaxValue(), config.getAlphaWeight()));
            }
        }

        sb.append("\n[Archetypes] (").append(archetypeWeights.size()).append(" defined)\n");
        for (String name : archetypeWeights.keySet()) {
            sb.append("  ").append(name).append(": ").append(archetypeWeights.get(name).length).append(" weights\n");
        }

        return sb.toString();
    }
}
