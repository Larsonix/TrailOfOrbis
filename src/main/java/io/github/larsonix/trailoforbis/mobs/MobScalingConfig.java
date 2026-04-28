package io.github.larsonix.trailoforbis.mobs;

import io.github.larsonix.trailoforbis.mobs.stats.MobStatPoolConfig;
import io.github.larsonix.trailoforbis.util.LevelScaling;

import javax.annotation.Nonnull;

/**
 * Configuration for the mob scaling system.
 *
 * <p>Supports loading from standalone YAML file (mob-scaling.yml) with snake_case keys.
 *
 * <p>Mobs scale based on two factors:
 * <ul>
 *   <li><b>Distance from origin (0,0)</b>: Adds bonus stat points linearly</li>
 *   <li><b>Nearby player levels</b>: Sets base stats to match player power</li>
 * </ul>
 *
 * <p>The bonus pool is distributed using Dirichlet distribution for natural specialization.
 */
public class MobScalingConfig {

    private boolean enabled = true;

    // ==================== Sub-Configurations ====================
    private SafeZoneConfig safeZone = new SafeZoneConfig();
    private DistanceScalingConfig distanceScaling = new DistanceScalingConfig();
    private PlayerDetectionConfig playerDetection = new PlayerDetectionConfig();
    private SpawnMultiplierConfig spawnMultiplier = new SpawnMultiplierConfig();
    private DynamicRefreshConfig dynamicRefresh = new DynamicRefreshConfig();
    private ElementalConfig elemental = new ElementalConfig();
    private ExponentialScalingConfig exponentialScaling = new ExponentialScalingConfig();
    private EliteChanceConfig eliteChance = new EliteChanceConfig();
    private BalanceMultiplierConfig balanceMultipliers = new BalanceMultiplierConfig();

    // Legacy - now using standalone MobStatPoolConfig from ConfigManager
    @Deprecated
    private PoolConfig pool = new PoolConfig();
    @Deprecated
    private MobStatPoolConfig statPool = MobStatPoolConfig.createDefaults();

    // ==================== Getters and Setters ====================

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public SafeZoneConfig getSafeZone() {
        return safeZone;
    }

    public void setSafeZone(SafeZoneConfig safeZone) {
        this.safeZone = safeZone;
    }

    // YAML snake_case setter
    public void setSafe_zone(SafeZoneConfig safeZone) {
        this.safeZone = safeZone;
    }

    public DistanceScalingConfig getDistanceScaling() {
        return distanceScaling;
    }

    public void setDistanceScaling(DistanceScalingConfig distanceScaling) {
        this.distanceScaling = distanceScaling;
    }

    // YAML snake_case setter
    public void setDistance_scaling(DistanceScalingConfig distanceScaling) {
        this.distanceScaling = distanceScaling;
    }

    public PlayerDetectionConfig getPlayerDetection() {
        return playerDetection;
    }

    public void setPlayerDetection(PlayerDetectionConfig playerDetection) {
        this.playerDetection = playerDetection;
    }

    // YAML snake_case setter
    public void setPlayer_detection(PlayerDetectionConfig playerDetection) {
        this.playerDetection = playerDetection;
    }

    public SpawnMultiplierConfig getSpawnMultiplier() {
        return spawnMultiplier;
    }

    public void setSpawnMultiplier(SpawnMultiplierConfig spawnMultiplier) {
        this.spawnMultiplier = spawnMultiplier;
    }

    // YAML snake_case setter
    public void setSpawn_multiplier(SpawnMultiplierConfig spawnMultiplier) {
        this.spawnMultiplier = spawnMultiplier;
    }

    public DynamicRefreshConfig getDynamicRefresh() {
        return dynamicRefresh;
    }

    public void setDynamicRefresh(DynamicRefreshConfig dynamicRefresh) {
        this.dynamicRefresh = dynamicRefresh;
    }

    // YAML snake_case setter
    public void setDynamic_refresh(DynamicRefreshConfig dynamicRefresh) {
        this.dynamicRefresh = dynamicRefresh;
    }

    public ElementalConfig getElemental() {
        return elemental;
    }

    public void setElemental(ElementalConfig elemental) {
        this.elemental = elemental;
    }

    public ExponentialScalingConfig getExponentialScaling() {
        return exponentialScaling;
    }

    public void setExponentialScaling(ExponentialScalingConfig exponentialScaling) {
        this.exponentialScaling = exponentialScaling;
    }

    // YAML snake_case setter
    public void setExponential_scaling(ExponentialScalingConfig exponentialScaling) {
        this.exponentialScaling = exponentialScaling;
    }

    public EliteChanceConfig getEliteChance() {
        return eliteChance;
    }

    public void setEliteChance(EliteChanceConfig eliteChance) {
        this.eliteChance = eliteChance;
    }

    // YAML snake_case setter
    public void setElite_chance(EliteChanceConfig eliteChance) {
        this.eliteChance = eliteChance;
    }

    public BalanceMultiplierConfig getBalanceMultipliers() {
        return balanceMultipliers;
    }

    public void setBalanceMultipliers(BalanceMultiplierConfig balanceMultipliers) {
        this.balanceMultipliers = balanceMultipliers;
    }

    // YAML snake_case setter
    public void setBalance_multipliers(BalanceMultiplierConfig balanceMultipliers) {
        this.balanceMultipliers = balanceMultipliers;
    }

    @Deprecated
    public PoolConfig getPool() {
        return pool;
    }

    @Deprecated
    public void setPool(PoolConfig pool) {
        this.pool = pool;
    }

    @Deprecated
    public MobStatPoolConfig getStatPool() {
        return statPool;
    }

    @Deprecated
    public void setStatPool(MobStatPoolConfig statPool) {
        this.statPool = statPool;
    }

    // ==================== Validation ====================

    /**
     * Validates the configuration values.
     *
     * @throws ConfigValidationException if validation fails
     */
    public void validate() throws ConfigValidationException {
        if (safeZone.radius < 0) {
            throw new ConfigValidationException("safe_zone.radius must be >= 0");
        }
        if (safeZone.transitionEnd < safeZone.radius) {
            throw new ConfigValidationException("safe_zone.transition_end must be >= safe_zone.radius");
        }
        if (distanceScaling.scalingStart < 0) {
            throw new ConfigValidationException("distance_scaling.scaling_start must be >= 0");
        }
        if (distanceScaling.poolPerBlock < 0) {
            throw new ConfigValidationException("distance_scaling.pool_per_block must be >= 0");
        }
        if (playerDetection.detectionRadius <= 0) {
            throw new ConfigValidationException("player_detection.detection_radius must be > 0");
        }
        if (playerDetection.groupMultiplier < 1.0) {
            throw new ConfigValidationException("player_detection.group_multiplier must be >= 1.0");
        }
        if (spawnMultiplier.maxMultiplier < 1) {
            throw new ConfigValidationException("spawn_multiplier.max_multiplier must be >= 1");
        }
        if (dynamicRefresh.intervalSeconds <= 0) {
            throw new ConfigValidationException("dynamic_refresh.interval_seconds must be > 0");
        }
        if (elemental != null) {
            elemental.validate();
        }
        if (exponentialScaling != null) {
            exponentialScaling.validate();
        }
        if (eliteChance != null) {
            eliteChance.validate();
        }
        if (balanceMultipliers != null) {
            balanceMultipliers.validate();
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
        sb.append("=== MobScalingConfig ===\n");
        sb.append("enabled: ").append(enabled).append("\n");
        sb.append("\n[Safe Zone]\n");
        sb.append("  radius: ").append(safeZone.radius).append(" blocks\n");
        sb.append("  transition_end: ").append(safeZone.transitionEnd).append(" blocks\n");
        sb.append("\n[Distance Scaling]\n");
        sb.append("  scaling_start: ").append(distanceScaling.scalingStart).append(" blocks\n");
        sb.append("  pool_per_block: ").append(distanceScaling.poolPerBlock).append("\n");
        sb.append("  max_bonus_pool: ").append(distanceScaling.maxBonusPool > 0 ? distanceScaling.maxBonusPool : "unlimited").append("\n");
        sb.append("\n[Player Detection]\n");
        sb.append("  detection_radius: ").append(playerDetection.detectionRadius).append(" blocks\n");
        sb.append("  group_radius: ").append(playerDetection.groupRadius).append(" blocks\n");
        sb.append("  group_multiplier: ").append(playerDetection.groupMultiplier).append("x\n");
        sb.append("\n[Spawn Multiplier]\n");
        sb.append("  enabled: ").append(spawnMultiplier.enabled).append("\n");
        sb.append("  level_per_multiplier: ").append(spawnMultiplier.levelPerMultiplier).append("\n");
        sb.append("  max_multiplier: ").append(spawnMultiplier.maxMultiplier).append("x\n");
        sb.append("\n[Dynamic Refresh]\n");
        sb.append("  enabled: ").append(dynamicRefresh.enabled).append("\n");
        sb.append("  interval: ").append(dynamicRefresh.intervalSeconds).append("s\n");
        return sb.toString();
    }

    // ==================== Nested Config Classes ====================

    /**
     * Safe zone configuration - area near spawn with no scaling.
     */
    public static class SafeZoneConfig {
        private double radius = 200.0;
        private double transitionEnd = 200.0;

        public double getRadius() {
            return radius;
        }

        public void setRadius(double radius) {
            this.radius = radius;
        }

        public double getTransitionEnd() {
            return transitionEnd;
        }

        public void setTransitionEnd(double transitionEnd) {
            this.transitionEnd = transitionEnd;
        }

        // YAML snake_case setter
        public void setTransition_end(double transitionEnd) {
            this.transitionEnd = transitionEnd;
        }

        public double getRadiusSquared() {
            return radius * radius;
        }
    }

    /**
     * Distance-based scaling configuration.
     */
    public static class DistanceScalingConfig {
        private double scalingStart = 200.0;
        private double poolPerBlock = 0.3;
        private double maxBonusPool = 0.0;

        public double getScalingStart() {
            return scalingStart;
        }

        public void setScalingStart(double scalingStart) {
            this.scalingStart = scalingStart;
        }

        // YAML snake_case setter
        public void setScaling_start(double scalingStart) {
            this.scalingStart = scalingStart;
        }

        public double getPoolPerBlock() {
            return poolPerBlock;
        }

        public void setPoolPerBlock(double poolPerBlock) {
            this.poolPerBlock = poolPerBlock;
        }

        // YAML snake_case setter
        public void setPool_per_block(double poolPerBlock) {
            this.poolPerBlock = poolPerBlock;
        }

        public double getMaxBonusPool() {
            return maxBonusPool;
        }

        public void setMaxBonusPool(double maxBonusPool) {
            this.maxBonusPool = maxBonusPool;
        }

        // YAML snake_case setter
        public void setMax_bonus_pool(double maxBonusPool) {
            this.maxBonusPool = maxBonusPool;
        }
    }

    /**
     * Player detection configuration for level-based scaling.
     */
    public static class PlayerDetectionConfig {
        private boolean enabled = false;
        private double detectionRadius = 256.0;
        private double groupRadius = 20.0;
        private double groupMultiplier = 1.2;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public double getDetectionRadius() {
            return detectionRadius;
        }

        public void setDetectionRadius(double detectionRadius) {
            this.detectionRadius = detectionRadius;
        }

        // YAML snake_case setter
        public void setDetection_radius(double detectionRadius) {
            this.detectionRadius = detectionRadius;
        }

        public double getGroupRadius() {
            return groupRadius;
        }

        public void setGroupRadius(double groupRadius) {
            this.groupRadius = groupRadius;
        }

        // YAML snake_case setter
        public void setGroup_radius(double groupRadius) {
            this.groupRadius = groupRadius;
        }

        public double getGroupMultiplier() {
            return groupMultiplier;
        }

        public void setGroupMultiplier(double groupMultiplier) {
            this.groupMultiplier = groupMultiplier;
        }

        // YAML snake_case setter
        public void setGroup_multiplier(double groupMultiplier) {
            this.groupMultiplier = groupMultiplier;
        }

        public double getDetectionRadiusSquared() {
            return detectionRadius * detectionRadius;
        }

        public double getGroupRadiusSquared() {
            return groupRadius * groupRadius;
        }
    }

    /**
     * Configuration for level-based spawn rate multiplier.
     */
    public static class SpawnMultiplierConfig {
        private boolean enabled = true;
        private double detectionRadius = 250.0;
        private int levelPerMultiplier = 50;
        private int maxMultiplier = 10;
        private double spawnOffsetRadius = 250.0;
        private double spawnOffsetMinDistance = 50.0;
        private boolean hostileOnly = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public double getDetectionRadius() {
            return detectionRadius;
        }

        public void setDetectionRadius(double detectionRadius) {
            this.detectionRadius = detectionRadius;
        }

        // YAML snake_case setter
        public void setDetection_radius(double detectionRadius) {
            this.detectionRadius = detectionRadius;
        }

        public int getLevelPerMultiplier() {
            return levelPerMultiplier;
        }

        public void setLevelPerMultiplier(int levelPerMultiplier) {
            this.levelPerMultiplier = levelPerMultiplier;
        }

        // YAML snake_case setter
        public void setLevel_per_multiplier(int levelPerMultiplier) {
            this.levelPerMultiplier = levelPerMultiplier;
        }

        public int getMaxMultiplier() {
            return maxMultiplier;
        }

        public void setMaxMultiplier(int maxMultiplier) {
            this.maxMultiplier = maxMultiplier;
        }

        // YAML snake_case setter
        public void setMax_multiplier(int maxMultiplier) {
            this.maxMultiplier = maxMultiplier;
        }

        public double getSpawnOffsetRadius() {
            return spawnOffsetRadius;
        }

        public void setSpawnOffsetRadius(double spawnOffsetRadius) {
            this.spawnOffsetRadius = spawnOffsetRadius;
        }

        // YAML snake_case setter
        public void setSpawn_offset_radius(double spawnOffsetRadius) {
            this.spawnOffsetRadius = spawnOffsetRadius;
        }

        public double getSpawnOffsetMinDistance() {
            return spawnOffsetMinDistance;
        }

        public void setSpawnOffsetMinDistance(double spawnOffsetMinDistance) {
            this.spawnOffsetMinDistance = spawnOffsetMinDistance;
        }

        // YAML snake_case setter
        public void setSpawn_offset_min_distance(double spawnOffsetMinDistance) {
            this.spawnOffsetMinDistance = spawnOffsetMinDistance;
        }

        public boolean isHostileOnly() {
            return hostileOnly;
        }

        public void setHostileOnly(boolean hostileOnly) {
            this.hostileOnly = hostileOnly;
        }

        // YAML snake_case setter
        public void setHostile_only(boolean hostileOnly) {
            this.hostileOnly = hostileOnly;
        }

        public double getDetectionRadiusSquared() {
            return detectionRadius * detectionRadius;
        }
    }

    /**
     * Configuration for dynamic mob level refresh system.
     */
    public static class DynamicRefreshConfig {
        private boolean enabled = true;
        private double intervalSeconds = 0.5;
        private double playerProximityRadius = 256.0;
        private int levelChangeThreshold = 1;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public double getIntervalSeconds() {
            return intervalSeconds;
        }

        public void setIntervalSeconds(double intervalSeconds) {
            this.intervalSeconds = intervalSeconds;
        }

        // YAML snake_case setter
        public void setInterval_seconds(double intervalSeconds) {
            this.intervalSeconds = intervalSeconds;
        }

        public double getPlayerProximityRadius() {
            return playerProximityRadius;
        }

        public void setPlayerProximityRadius(double playerProximityRadius) {
            this.playerProximityRadius = playerProximityRadius;
        }

        // YAML snake_case setter
        public void setPlayer_proximity_radius(double playerProximityRadius) {
            this.playerProximityRadius = playerProximityRadius;
        }

        public int getLevelChangeThreshold() {
            return levelChangeThreshold;
        }

        public void setLevelChangeThreshold(int levelChangeThreshold) {
            this.levelChangeThreshold = levelChangeThreshold;
        }

        // YAML snake_case setter
        public void setLevel_change_threshold(int levelChangeThreshold) {
            this.levelChangeThreshold = levelChangeThreshold;
        }
    }

    /**
     * Legacy unified pool configuration.
     * @deprecated Use standalone MobStatPoolConfig from mob-stat-pool.yml instead
     */
    @Deprecated
    public static class PoolConfig {
        public static final int STAT_COUNT = 13;
        private double pointsPerLevel = 17.25;
        private double fixedRatio = 0.40;
        private double randomRatio = 0.60;
        private double healthFactor = 1.76;    // × 2 for tankier mobs
        private double damageFactor = 0.1675;  // ÷ 2 for sustained fights
        private double armorFactor = 0.282;
        private double speedFactor = 0.0022;
        private double critChanceFactor = 0.11;
        private double critMultiplierFactor = 0.44;
        private double dodgeChanceFactor = 0.11;
        private double lifeStealFactor = 0.066;
        private double armorPenFactor = 0.176;
        private double healthRegenFactor = 0.044;
        private double blockChanceFactor = 0.08;
        private double parryChanceFactor = 0.06;
        private double trueDamageFactor = 0.1;
        private double baseCritChance = 5.0;
        private double baseCritMultiplier = 150.0;
        private double minSpeed = 0.5;

        // All getters and setters...
        public double getPointsPerLevel() { return pointsPerLevel; }
        public void setPointsPerLevel(double v) { this.pointsPerLevel = v; }
        public double getFixedRatio() { return fixedRatio; }
        public void setFixedRatio(double v) { this.fixedRatio = v; }
        public double getRandomRatio() { return randomRatio; }
        public void setRandomRatio(double v) { this.randomRatio = v; }
        public double getHealthFactor() { return healthFactor; }
        public void setHealthFactor(double v) { this.healthFactor = v; }
        public double getDamageFactor() { return damageFactor; }
        public void setDamageFactor(double v) { this.damageFactor = v; }
        public double getArmorFactor() { return armorFactor; }
        public void setArmorFactor(double v) { this.armorFactor = v; }
        public double getSpeedFactor() { return speedFactor; }
        public void setSpeedFactor(double v) { this.speedFactor = v; }
        public double getCritChanceFactor() { return critChanceFactor; }
        public void setCritChanceFactor(double v) { this.critChanceFactor = v; }
        public double getCritMultiplierFactor() { return critMultiplierFactor; }
        public void setCritMultiplierFactor(double v) { this.critMultiplierFactor = v; }
        public double getDodgeChanceFactor() { return dodgeChanceFactor; }
        public void setDodgeChanceFactor(double v) { this.dodgeChanceFactor = v; }
        public double getLifeStealFactor() { return lifeStealFactor; }
        public void setLifeStealFactor(double v) { this.lifeStealFactor = v; }
        public double getArmorPenFactor() { return armorPenFactor; }
        public void setArmorPenFactor(double v) { this.armorPenFactor = v; }
        public double getHealthRegenFactor() { return healthRegenFactor; }
        public void setHealthRegenFactor(double v) { this.healthRegenFactor = v; }
        public double getBlockChanceFactor() { return blockChanceFactor; }
        public void setBlockChanceFactor(double v) { this.blockChanceFactor = v; }
        public double getParryChanceFactor() { return parryChanceFactor; }
        public void setParryChanceFactor(double v) { this.parryChanceFactor = v; }
        public double getTrueDamageFactor() { return trueDamageFactor; }
        public void setTrueDamageFactor(double v) { this.trueDamageFactor = v; }
        public double getBaseCritChance() { return baseCritChance; }
        public void setBaseCritChance(double v) { this.baseCritChance = v; }
        public double getBaseCritMultiplier() { return baseCritMultiplier; }
        public void setBaseCritMultiplier(double v) { this.baseCritMultiplier = v; }
        public double getMinSpeed() { return minSpeed; }
        public void setMinSpeed(double v) { this.minSpeed = v; }
    }

    /**
     * Configuration for mob elemental stats.
     *
     * <p>Controls how mobs gain elemental damage, resistances, and penetration
     * based on their level and elemental affinity (determined by role name keywords).
     *
     * <p>Mobs with elemental affinity (e.g., "fire_mage") get:
     * <ul>
     *   <li>Flat elemental damage scaling with level</li>
     *   <li>Bonus resistance to their element</li>
     *   <li>Penetration to bypass player resistances</li>
     * </ul>
     */
    public static class ElementalConfig {
        // ==================== Damage Scaling ====================

        /**
         * Flat elemental damage per mob level for affinity element.
         * Default: 2.0 (level 50 mob = 100 flat elemental damage)
         */
        private double damagePerLevel = 2.0;

        // ==================== Resistance Scaling ====================

        /**
         * Base resistance gain per mob level (all elements).
         * Default: 0.5 (level 100 mob = 50% base resistance)
         */
        private double resistancePerLevel = 0.5;

        /**
         * Maximum base resistance before affinity bonus (%).
         * Default: 50.0 (50% cap for non-affinity elements)
         */
        private double maxBaseResistance = 50.0;

        /**
         * Extra resistance for mob's primary affinity element (%).
         * Default: 15.0 (+15% to affinity element)
         */
        private double affinityResistanceBonus = 15.0;

        /**
         * Maximum resistance with affinity bonus (%).
         * Default: 70.0 (70% cap for affinity element)
         */
        private double maxAffinityResistance = 70.0;

        // ==================== Penetration Scaling ====================

        /**
         * Base penetration gain per mob level for affinity element.
         * Default: 0.3 (level 100 mob = 30% base penetration)
         */
        private double penetrationPerLevel = 0.3;

        /**
         * Maximum base penetration before affinity bonus (%).
         * Default: 30.0
         */
        private double maxBasePenetration = 30.0;

        /**
         * Extra penetration for mob's primary affinity element (%).
         * Default: 10.0
         */
        private double affinityPenetrationBonus = 10.0;

        /**
         * Maximum penetration with affinity bonus (%).
         * Default: 50.0
         */
        private double maxAffinityPenetration = 50.0;

        // ==================== Getters and Setters ====================

        public double getDamagePerLevel() {
            return damagePerLevel;
        }

        public void setDamagePerLevel(double damagePerLevel) {
            this.damagePerLevel = damagePerLevel;
        }

        // YAML snake_case setter
        public void setDamage_per_level(double damagePerLevel) {
            this.damagePerLevel = damagePerLevel;
        }

        public double getResistancePerLevel() {
            return resistancePerLevel;
        }

        public void setResistancePerLevel(double resistancePerLevel) {
            this.resistancePerLevel = resistancePerLevel;
        }

        // YAML snake_case setter
        public void setResistance_per_level(double resistancePerLevel) {
            this.resistancePerLevel = resistancePerLevel;
        }

        public double getMaxBaseResistance() {
            return maxBaseResistance;
        }

        public void setMaxBaseResistance(double maxBaseResistance) {
            this.maxBaseResistance = maxBaseResistance;
        }

        // YAML snake_case setter
        public void setMax_base_resistance(double maxBaseResistance) {
            this.maxBaseResistance = maxBaseResistance;
        }

        public double getAffinityResistanceBonus() {
            return affinityResistanceBonus;
        }

        public void setAffinityResistanceBonus(double affinityResistanceBonus) {
            this.affinityResistanceBonus = affinityResistanceBonus;
        }

        // YAML snake_case setter
        public void setAffinity_resistance_bonus(double affinityResistanceBonus) {
            this.affinityResistanceBonus = affinityResistanceBonus;
        }

        public double getMaxAffinityResistance() {
            return maxAffinityResistance;
        }

        public void setMaxAffinityResistance(double maxAffinityResistance) {
            this.maxAffinityResistance = maxAffinityResistance;
        }

        // YAML snake_case setter
        public void setMax_affinity_resistance(double maxAffinityResistance) {
            this.maxAffinityResistance = maxAffinityResistance;
        }

        public double getPenetrationPerLevel() {
            return penetrationPerLevel;
        }

        public void setPenetrationPerLevel(double penetrationPerLevel) {
            this.penetrationPerLevel = penetrationPerLevel;
        }

        // YAML snake_case setter
        public void setPenetration_per_level(double penetrationPerLevel) {
            this.penetrationPerLevel = penetrationPerLevel;
        }

        public double getMaxBasePenetration() {
            return maxBasePenetration;
        }

        public void setMaxBasePenetration(double maxBasePenetration) {
            this.maxBasePenetration = maxBasePenetration;
        }

        // YAML snake_case setter
        public void setMax_base_penetration(double maxBasePenetration) {
            this.maxBasePenetration = maxBasePenetration;
        }

        public double getAffinityPenetrationBonus() {
            return affinityPenetrationBonus;
        }

        public void setAffinityPenetrationBonus(double affinityPenetrationBonus) {
            this.affinityPenetrationBonus = affinityPenetrationBonus;
        }

        // YAML snake_case setter
        public void setAffinity_penetration_bonus(double affinityPenetrationBonus) {
            this.affinityPenetrationBonus = affinityPenetrationBonus;
        }

        public double getMaxAffinityPenetration() {
            return maxAffinityPenetration;
        }

        public void setMaxAffinityPenetration(double maxAffinityPenetration) {
            this.maxAffinityPenetration = maxAffinityPenetration;
        }

        // YAML snake_case setter
        public void setMax_affinity_penetration(double maxAffinityPenetration) {
            this.maxAffinityPenetration = maxAffinityPenetration;
        }

        /**
         * Validates elemental configuration values.
         *
         * @throws ConfigValidationException if any value is invalid
         */
        public void validate() throws ConfigValidationException {
            if (damagePerLevel < 0) {
                throw new ConfigValidationException("elemental.damage_per_level cannot be negative");
            }
            if (resistancePerLevel < 0) {
                throw new ConfigValidationException("elemental.resistance_per_level cannot be negative");
            }
            if (maxBaseResistance < 0 || maxBaseResistance > 100) {
                throw new ConfigValidationException("elemental.max_base_resistance must be between 0 and 100");
            }
            if (affinityResistanceBonus < 0) {
                throw new ConfigValidationException("elemental.affinity_resistance_bonus cannot be negative");
            }
            if (maxAffinityResistance < 0 || maxAffinityResistance > 100) {
                throw new ConfigValidationException("elemental.max_affinity_resistance must be between 0 and 100");
            }
            if (maxAffinityResistance < maxBaseResistance) {
                throw new ConfigValidationException("elemental.max_affinity_resistance must be >= max_base_resistance");
            }
            if (penetrationPerLevel < 0) {
                throw new ConfigValidationException("elemental.penetration_per_level cannot be negative");
            }
            if (maxBasePenetration < 0 || maxBasePenetration > 100) {
                throw new ConfigValidationException("elemental.max_base_penetration must be between 0 and 100");
            }
            if (affinityPenetrationBonus < 0) {
                throw new ConfigValidationException("elemental.affinity_penetration_bonus cannot be negative");
            }
            if (maxAffinityPenetration < 0 || maxAffinityPenetration > 100) {
                throw new ConfigValidationException("elemental.max_affinity_penetration must be between 0 and 100");
            }
        }
    }

    /**
     * Configuration for mob stat exponential scaling.
     *
     * <p><b>NOTE:</b> The fields below (exponent, referenceLevel, referenceMultiplier,
     * softCapLevel, softCapDivisor) are <b>not active</b>. They are remnants from before
     * the scaling system was centralized into {@link LevelScaling}. The
     * {@link #calculateMultiplier(int)} method delegates entirely to
     * {@link LevelScaling#getMultiplier(int)}, which is configured globally via
     * {@code config.yml → scaling} section.
     *
     * <p>Only {@link #enabled} is used. All other fields load and validate but have
     * no effect on gameplay. They are retained for potential future per-system
     * override capability.
     *
     * @see LevelScaling
     */
    public static class ExponentialScalingConfig {
        private boolean enabled = true;
        private double exponent = 2.0;
        private int referenceLevel = 100;
        private double referenceMultiplier = 50.0;
        private double baseMultiplier = 1.0;
        private int softCapLevel = 100;
        private double softCapDivisor = 2.0;

        // ==================== Getters ====================

        public boolean isEnabled() {
            return enabled;
        }

        public double getExponent() {
            return exponent;
        }

        public int getReferenceLevel() {
            return referenceLevel;
        }

        public double getReferenceMultiplier() {
            return referenceMultiplier;
        }

        public double getBaseMultiplier() {
            return baseMultiplier;
        }

        public int getSoftCapLevel() {
            return softCapLevel;
        }

        public double getSoftCapDivisor() {
            return softCapDivisor;
        }

        // ==================== Setters ====================

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public void setExponent(double exponent) {
            this.exponent = exponent;
        }

        public void setReferenceLevel(int referenceLevel) {
            this.referenceLevel = referenceLevel;
        }

        // YAML snake_case setter
        public void setReference_level(int referenceLevel) {
            this.referenceLevel = referenceLevel;
        }

        public void setReferenceMultiplier(double referenceMultiplier) {
            this.referenceMultiplier = referenceMultiplier;
        }

        // YAML snake_case setter
        public void setReference_multiplier(double referenceMultiplier) {
            this.referenceMultiplier = referenceMultiplier;
        }

        public void setBaseMultiplier(double baseMultiplier) {
            this.baseMultiplier = baseMultiplier;
        }

        // YAML snake_case setter
        public void setBase_multiplier(double baseMultiplier) {
            this.baseMultiplier = baseMultiplier;
        }

        public void setSoftCapLevel(int softCapLevel) {
            this.softCapLevel = softCapLevel;
        }

        // YAML snake_case setter
        public void setSoft_cap_level(int softCapLevel) {
            this.softCapLevel = softCapLevel;
        }

        public void setSoftCapDivisor(double softCapDivisor) {
            this.softCapDivisor = softCapDivisor;
        }

        // YAML snake_case setter
        public void setSoft_cap_divisor(double softCapDivisor) {
            this.softCapDivisor = softCapDivisor;
        }

        // ==================== Calculation ====================

        /**
         * Returns the scaling multiplier for a given mob level.
         *
         * <p>Delegates to {@link LevelScaling#getMultiplier(int)} — the centralized
         * curve configured in {@code config.yml → scaling}. The per-system fields
         * on this class (exponent, referenceMultiplier, etc.) are not used.
         *
         * @param level The mob level
         * @return The scaling multiplier (~1.5× at level 100 with default config)
         */
        public double calculateMultiplier(int level) {
            if (!enabled || level <= 0) {
                return 1.0;
            }
            return LevelScaling.getMultiplier(level);
        }

        /**
         * Validates the exponential scaling configuration.
         *
         * @throws ConfigValidationException if any value is invalid
         */
        public void validate() throws ConfigValidationException {
            if (exponent <= 0) {
                throw new ConfigValidationException("exponential_scaling.exponent must be positive");
            }
            if (referenceLevel <= 0) {
                throw new ConfigValidationException("exponential_scaling.reference_level must be positive");
            }
            if (referenceMultiplier < baseMultiplier) {
                throw new ConfigValidationException(
                    "exponential_scaling.reference_multiplier must be >= base_multiplier");
            }
            if (baseMultiplier <= 0) {
                throw new ConfigValidationException("exponential_scaling.base_multiplier must be positive");
            }
            if (softCapLevel <= 0) {
                throw new ConfigValidationException("exponential_scaling.soft_cap_level must be positive");
            }
            if (softCapDivisor <= 0) {
                throw new ConfigValidationException("exponential_scaling.soft_cap_divisor must be positive");
            }
        }
    }

    /**
     * Configuration for random elite chance at spawn time.
     *
     * <p>Any hostile mob has a chance to become elite when spawning.
     * The chance scales with mob level for more exciting high-level encounters.
     *
     * <p>This replaces the old deterministic elite system where specific roles
     * (like chieftains, berserkers) were always classified as ELITE.
     */
    public static class EliteChanceConfig {
        private boolean enabled = true;
        private double baseChance = 0.05;
        private double chancePerLevel = 0.0001;
        private double maxChance = 0.25;

        // ==================== Getters ====================

        public boolean isEnabled() {
            return enabled;
        }

        public double getBaseChance() {
            return baseChance;
        }

        public double getChancePerLevel() {
            return chancePerLevel;
        }

        public double getMaxChance() {
            return maxChance;
        }

        // ==================== Setters ====================

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public void setBaseChance(double baseChance) {
            this.baseChance = baseChance;
        }

        // YAML snake_case setter
        public void setBase_chance(double baseChance) {
            this.baseChance = baseChance;
        }

        public void setChancePerLevel(double chancePerLevel) {
            this.chancePerLevel = chancePerLevel;
        }

        // YAML snake_case setter
        public void setChance_per_level(double chancePerLevel) {
            this.chancePerLevel = chancePerLevel;
        }

        public void setMaxChance(double maxChance) {
            this.maxChance = maxChance;
        }

        // YAML snake_case setter
        public void setMax_chance(double maxChance) {
            this.maxChance = maxChance;
        }

        // ==================== Calculation ====================

        /**
         * Calculates the elite spawn chance for a given mob level.
         *
         * <p>Formula: min(baseChance + level * chancePerLevel, maxChance)
         *
         * @param mobLevel The mob's level
         * @return The probability (0.0 to 1.0) that this mob becomes elite
         */
        public double calculateChance(int mobLevel) {
            if (!enabled) {
                return 0.0;
            }
            double chance = baseChance + (mobLevel * chancePerLevel);
            return Math.min(chance, maxChance);
        }

        /**
         * Validates the elite chance configuration.
         *
         * @throws ConfigValidationException if any value is invalid
         */
        public void validate() throws ConfigValidationException {
            if (baseChance < 0 || baseChance > 1) {
                throw new ConfigValidationException("elite_chance.base_chance must be between 0 and 1");
            }
            if (chancePerLevel < 0) {
                throw new ConfigValidationException("elite_chance.chance_per_level cannot be negative");
            }
            if (maxChance < 0 || maxChance > 1) {
                throw new ConfigValidationException("elite_chance.max_chance must be between 0 and 1");
            }
            if (maxChance < baseChance) {
                throw new ConfigValidationException("elite_chance.max_chance must be >= base_chance");
            }
        }
    }

    // ==================== Balance Multiplier Configuration ====================

    /**
     * Global balance multipliers for quick tuning of mob power.
     *
     * <p>Applied after all other calculations as a flat multiplier.
     * 1.0 = no change, 1.2 = +20%, 0.8 = -20%.
     */
    public static class BalanceMultiplierConfig {

        private double health = 1.0;
        private double physicalDamage = 1.0;
        private double elementalDamage = 1.0;

        public double getHealth() {
            return health;
        }

        public void setHealth(double health) {
            this.health = health;
        }

        public double getPhysicalDamage() {
            return physicalDamage;
        }

        public void setPhysicalDamage(double physicalDamage) {
            this.physicalDamage = physicalDamage;
        }

        // YAML snake_case setter
        public void setPhysical_damage(double physicalDamage) {
            this.physicalDamage = physicalDamage;
        }

        public double getElementalDamage() {
            return elementalDamage;
        }

        public void setElementalDamage(double elementalDamage) {
            this.elementalDamage = elementalDamage;
        }

        // YAML snake_case setter
        public void setElemental_damage(double elementalDamage) {
            this.elementalDamage = elementalDamage;
        }

        public void validate() throws ConfigValidationException {
            if (health <= 0) {
                throw new ConfigValidationException("balance_multipliers.health must be > 0");
            }
            if (physicalDamage <= 0) {
                throw new ConfigValidationException("balance_multipliers.physical_damage must be > 0");
            }
            if (elementalDamage <= 0) {
                throw new ConfigValidationException("balance_multipliers.elemental_damage must be > 0");
            }
        }
    }
}
