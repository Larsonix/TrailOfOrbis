package io.github.larsonix.trailoforbis.config;

import io.github.larsonix.trailoforbis.combat.projectile.ProjectileConfig;
import io.github.larsonix.trailoforbis.skilltree.config.SkillTreeSettings;
import io.github.larsonix.trailoforbis.util.LevelScaling;

/**
 * Main RPG configuration.
 *
 * This class represents the root configuration for the TrailOfOrbis plugin.
 * It contains nested configuration classes for different aspects of the plugin.
 */
public class RPGConfig {

    /**
     * Validates all configuration values are within acceptable ranges.
     *
     * @throws ConfigValidationException if any value is invalid
     */
    public void validate() throws ConfigValidationException {
        // Database validation
        if (database != null) {
            database.validate();
        }

        // Attribute validation
        if (attributes != null) {
            attributes.validate();
        }

        // Combat validation
        if (combat != null) {
            combat.validate();
        }

        // Armor validation
        if (armor != null) {
            armor.validate();
        }

        // Physical resistance validation
        if (physicalResistance != null) {
            physicalResistance.validate();
        }

        // Projectile validation
        if (projectile != null) {
            projectile.validate();
        }

        // Scaling validation + apply to LevelScaling
        if (scaling != null) {
            scaling.validate();
            LevelScaling.configure(scaling.getTransitionLevel(), scaling.getMaxMultiplierRatio(), scaling.getDecayDivisor());
        }
    }

    /**
     * Exception thrown when configuration validation fails.
     */
    public static class ConfigValidationException extends RuntimeException {
        public ConfigValidationException(String message) {
            super(message);
        }
    }
    // Database settings
    private DatabaseConfig database = new DatabaseConfig();

    // Attribute settings
    private AttributeConfig attributes = new AttributeConfig();

    // Combat settings
    private CombatConfig combat = new CombatConfig();

    // Armor system settings
    private ArmorConfig armor = new ArmorConfig();

    // Physical resistance settings
    private PhysicalResistanceConfig physicalResistance = new PhysicalResistanceConfig();

    // Skill tree settings
    private SkillTreeSettings skillTree = new SkillTreeSettings();

    // Projectile stats settings
    private ProjectileConfig projectile = new ProjectileConfig();

    // Level scaling settings (shared by gear + mobs)
    private ScalingConfig scaling = new ScalingConfig();

    // General settings
    private boolean debugMode = false;
    private String language = "en_US";
    private boolean creativeModeBypassRequirements = true;
    private boolean suppressVanillaGearDrops = true;

    // Getters and setters
    public DatabaseConfig getDatabase() {
        return database;
    }

    public void setDatabase(DatabaseConfig database) {
        this.database = database;
    }

    public AttributeConfig getAttributes() {
        return attributes;
    }

    public void setAttributes(AttributeConfig attributes) {
        this.attributes = attributes;
    }

    public CombatConfig getCombat() {
        return combat;
    }

    public void setCombat(CombatConfig combat) {
        this.combat = combat;
    }

    public ArmorConfig getArmor() {
        return armor;
    }

    public void setArmor(ArmorConfig armor) {
        this.armor = armor;
    }

    public PhysicalResistanceConfig getPhysicalResistance() {
        return physicalResistance;
    }

    public void setPhysicalResistance(PhysicalResistanceConfig physicalResistance) {
        this.physicalResistance = physicalResistance;
    }

    public SkillTreeSettings getSkillTree() {
        return skillTree;
    }

    public void setSkillTree(SkillTreeSettings skillTree) {
        this.skillTree = skillTree;
    }

    public ProjectileConfig getProjectile() {
        return projectile;
    }

    public void setProjectile(ProjectileConfig projectile) {
        this.projectile = projectile;
    }

    public ScalingConfig getScaling() {
        return scaling;
    }

    public void setScaling(ScalingConfig scaling) {
        this.scaling = scaling;
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    public void setDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public boolean isCreativeModeBypassRequirements() {
        return creativeModeBypassRequirements;
    }

    public void setCreativeModeBypassRequirements(boolean creativeModeBypassRequirements) {
        this.creativeModeBypassRequirements = creativeModeBypassRequirements;
    }

    public boolean isSuppressVanillaGearDrops() {
        return suppressVanillaGearDrops;
    }

    public void setSuppressVanillaGearDrops(boolean suppressVanillaGearDrops) {
        this.suppressVanillaGearDrops = suppressVanillaGearDrops;
    }

    /**
     * Database configuration.
     */
    public static class DatabaseConfig {
        private String type = "H2"; // H2, MySQL, PostgreSQL
        private String host = "localhost";
        private int port = 3306;
        private String database = "trailoforbis";
        private String username = "root";
        private String password = "";
        private int poolSize = 30;

        // Getters and setters
        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getDatabase() {
            return database;
        }

        public void setDatabase(String database) {
            this.database = database;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public int getPoolSize() {
            return poolSize;
        }

        public void setPoolSize(int poolSize) {
            this.poolSize = poolSize;
        }

        /**
         * Validates database configuration values.
         *
         * @throws ConfigValidationException if any value is invalid
         */
        public void validate() throws ConfigValidationException {
            // Validate database type
            if (type == null || type.isBlank()) {
                throw new ConfigValidationException("database.type cannot be null or empty");
            }
            String upperType = type.toUpperCase();
            if (!upperType.equals("H2") && !upperType.equals("MYSQL") && !upperType.equals("POSTGRESQL")) {
                throw new ConfigValidationException(
                    "database.type must be H2, MySQL, or PostgreSQL, got: " + type);
            }

            // Validate pool size (1-100)
            if (poolSize < 1 || poolSize > 100) {
                throw new ConfigValidationException(
                    "database.poolSize must be between 1 and 100, got: " + poolSize);
            }

            // Validate port (1-65535)
            if (port < 1 || port > 65535) {
                throw new ConfigValidationException(
                    "database.port must be between 1 and 65535, got: " + port);
            }
        }
    }

    /**
     * Elemental attribute system configuration.
     *
     * <p>ARCHITECTURE NOTE:
     * The elemental system replaces traditional RPG attributes (STR/DEX/INT/VIT/LUCK)
     * with 6 elements that each represent a distinct playstyle archetype:
     * <ul>
     *   <li>FIRE - Glass cannon: high damage, negative HP</li>
     *   <li>WATER - Glacier tank: health, regen, barrier, slightly slower</li>
     *   <li>LIGHTNING - Storm speed: attack speed, move speed, crits</li>
     *   <li>EARTH - Mountain fortress: armor, block, thorns, stability</li>
     *   <li>WIND - Ghost evasion: dodge, accuracy, projectiles, jump</li>
     *   <li>VOID - Dark bargain: life steal, low-life bonus, HP penalty</li>
     * </ul>
     *
     * <p>Combat systems use ComputedStats, never attributes directly.
     */
    public static class AttributeConfig {
        private int pointsPerLevel = 1;
        private int startingPoints = 0;

        // What stats each element grants
        private FireGrants fireGrants = new FireGrants();
        private WaterGrants waterGrants = new WaterGrants();
        private LightningGrants lightningGrants = new LightningGrants();
        private EarthGrants earthGrants = new EarthGrants();
        private WindGrants windGrants = new WindGrants();
        private VoidGrants voidGrants = new VoidGrants();

        // Magic charges from player level (not attributes)
        private MagicChargesConfig magicCharges = new MagicChargesConfig();

        // Getters and setters
        public int getPointsPerLevel() {
            return pointsPerLevel;
        }

        public void setPointsPerLevel(int pointsPerLevel) {
            this.pointsPerLevel = pointsPerLevel;
        }

        public int getStartingPoints() {
            return startingPoints;
        }

        public void setStartingPoints(int startingPoints) {
            this.startingPoints = startingPoints;
        }

        public FireGrants getFireGrants() {
            return fireGrants;
        }

        public void setFireGrants(FireGrants fireGrants) {
            this.fireGrants = fireGrants;
        }

        public WaterGrants getWaterGrants() {
            return waterGrants;
        }

        public void setWaterGrants(WaterGrants waterGrants) {
            this.waterGrants = waterGrants;
        }

        public LightningGrants getLightningGrants() {
            return lightningGrants;
        }

        public void setLightningGrants(LightningGrants lightningGrants) {
            this.lightningGrants = lightningGrants;
        }

        public EarthGrants getEarthGrants() {
            return earthGrants;
        }

        public void setEarthGrants(EarthGrants earthGrants) {
            this.earthGrants = earthGrants;
        }

        public WindGrants getWindGrants() {
            return windGrants;
        }

        public void setWindGrants(WindGrants windGrants) {
            this.windGrants = windGrants;
        }

        public VoidGrants getVoidGrants() {
            return voidGrants;
        }

        public void setVoidGrants(VoidGrants voidGrants) {
            this.voidGrants = voidGrants;
        }

        public MagicChargesConfig getMagicCharges() {
            return magicCharges;
        }

        public void setMagicCharges(MagicChargesConfig magicCharges) {
            this.magicCharges = magicCharges;
        }

        /**
         * Validates attribute configuration values.
         *
         * @throws ConfigValidationException if any value is invalid
         */
        public void validate() throws ConfigValidationException {
            if (pointsPerLevel < 0) {
                throw new ConfigValidationException(
                    "attributes.pointsPerLevel cannot be negative, got: " + pointsPerLevel);
            }
            if (startingPoints < 0) {
                throw new ConfigValidationException(
                    "attributes.startingPoints cannot be negative, got: " + startingPoints);
            }
        }

        /**
         * Stats granted per point of FIRE (Glass Cannon).
         *
         * <p>Archetype: Raw destructive power. Fire users maximize burst damage
         * through physical hits, crits, and burn effects.
         */
        public static class FireGrants {
            private float physicalDamagePercent = 0.4f;       // +0.4% physical damage
            private float chargedAttackDamagePercent = 0.3f;   // +0.3% charged attack damage
            private float criticalMultiplier = 0.6f;         // +0.6% crit damage
            private float burnDamagePercent = 0.4f;          // +0.4% burn damage
            private float igniteChance = 0.1f;               // +0.1% ignite chance
            private float magicPower = 0.01f;                // +1% spell power per point

            public float getPhysicalDamagePercent() {
                return physicalDamagePercent;
            }

            public void setPhysicalDamagePercent(float physicalDamagePercent) {
                this.physicalDamagePercent = physicalDamagePercent;
            }

            public float getChargedAttackDamagePercent() {
                return chargedAttackDamagePercent;
            }

            public void setChargedAttackDamagePercent(float chargedAttackDamagePercent) {
                this.chargedAttackDamagePercent = chargedAttackDamagePercent;
            }

            public float getCriticalMultiplier() {
                return criticalMultiplier;
            }

            public void setCriticalMultiplier(float criticalMultiplier) {
                this.criticalMultiplier = criticalMultiplier;
            }

            public float getBurnDamagePercent() {
                return burnDamagePercent;
            }

            public void setBurnDamagePercent(float burnDamagePercent) {
                this.burnDamagePercent = burnDamagePercent;
            }

            public float getIgniteChance() {
                return igniteChance;
            }

            public void setIgniteChance(float igniteChance) {
                this.igniteChance = igniteChance;
            }

            public float getMagicPower() {
                return magicPower;
            }

            public void setMagicPower(float magicPower) {
                this.magicPower = magicPower;
            }
        }

        /**
         * Stats granted per point of WATER (Arcane Mage).
         *
         * <p>Archetype: Spell-focused caster. Water users increase spell potency,
         * mana pool, energy shield, and freezing power.
         */
        public static class WaterGrants {
            private float spellDamagePercent = 0.5f;     // +0.5% spell damage
            private float maxMana = 1.5f;                // +1.5 mana
            private float energyShield = 2.0f;           // +2.0 barrier (energy shield)
            private float manaRegen = 0.15f;             // +0.15 mana/s regen
            private float freezeChance = 0.1f;           // +0.1% freeze chance
            private float magicPower = 0.02f;            // +2% spell power per point
            private float volatilityMax = 1.5f;          // +1.5 volatility budget per point

            public float getSpellDamagePercent() {
                return spellDamagePercent;
            }

            public void setSpellDamagePercent(float spellDamagePercent) {
                this.spellDamagePercent = spellDamagePercent;
            }

            public float getMaxMana() {
                return maxMana;
            }

            public void setMaxMana(float maxMana) {
                this.maxMana = maxMana;
            }

            public float getEnergyShield() {
                return energyShield;
            }

            public void setEnergyShield(float energyShield) {
                this.energyShield = energyShield;
            }

            public float getManaRegen() {
                return manaRegen;
            }

            public void setManaRegen(float manaRegen) {
                this.manaRegen = manaRegen;
            }

            public float getFreezeChance() {
                return freezeChance;
            }

            public void setFreezeChance(float freezeChance) {
                this.freezeChance = freezeChance;
            }

            public float getMagicPower() {
                return magicPower;
            }

            public void setMagicPower(float magicPower) {
                this.magicPower = magicPower;
            }

            public float getVolatilityMax() {
                return volatilityMax;
            }

            public void setVolatilityMax(float volatilityMax) {
                this.volatilityMax = volatilityMax;
            }
        }

        /**
         * Stats granted per point of LIGHTNING (Storm Blitz).
         *
         * <p>Archetype: Speed demon. Lightning users strike fast with
         * rapid attacks, high mobility, and shocking precision.
         */
        public static class LightningGrants {
            private float attackSpeedPercent = 0.3f;     // +0.3% attack speed
            private float moveSpeedPercent = 0.15f;      // +0.15% move speed
            private float critChance = 0.1f;             // +0.1% crit chance
            private float staminaRegen = 0.1f;           // +0.1 stamina/s regen
            private float shockChance = 0.1f;            // +0.1% shock chance
            private float castSpeed = 0.01f;             // +1% cast speed per point

            public float getAttackSpeedPercent() {
                return attackSpeedPercent;
            }

            public void setAttackSpeedPercent(float attackSpeedPercent) {
                this.attackSpeedPercent = attackSpeedPercent;
            }

            public float getMoveSpeedPercent() {
                return moveSpeedPercent;
            }

            public void setMoveSpeedPercent(float moveSpeedPercent) {
                this.moveSpeedPercent = moveSpeedPercent;
            }

            public float getCritChance() {
                return critChance;
            }

            public void setCritChance(float critChance) {
                this.critChance = critChance;
            }

            public float getStaminaRegen() {
                return staminaRegen;
            }

            public void setStaminaRegen(float staminaRegen) {
                this.staminaRegen = staminaRegen;
            }

            public float getShockChance() {
                return shockChance;
            }

            public void setShockChance(float shockChance) {
                this.shockChance = shockChance;
            }

            public float getCastSpeed() {
                return castSpeed;
            }

            public void setCastSpeed(float castSpeed) {
                this.castSpeed = castSpeed;
            }
        }

        /**
         * Stats granted per point of EARTH (Iron Fortress).
         *
         * <p>Archetype: Immovable object. Earth users are heavily armored
         * defenders with high health, regen, and blocking power.
         */
        public static class EarthGrants {
            private float maxHealthPercent = 0.5f;       // +0.5% max HP
            private float armor = 5.0f;                  // +5.0 armor
            private float healthRegen = 0.2f;            // +0.2 HP/s regen
            private float blockChance = 0.2f;              // +0.2% perfect block chance per point
            private float knockbackResistance = 0.3f;    // +0.3% stability

            public float getMaxHealthPercent() {
                return maxHealthPercent;
            }

            public void setMaxHealthPercent(float maxHealthPercent) {
                this.maxHealthPercent = maxHealthPercent;
            }

            public float getArmor() {
                return armor;
            }

            public void setArmor(float armor) {
                this.armor = armor;
            }

            public float getHealthRegen() {
                return healthRegen;
            }

            public void setHealthRegen(float healthRegen) {
                this.healthRegen = healthRegen;
            }

            public float getBlockChance() {
                return blockChance;
            }

            public void setBlockChance(float blockChance) {
                this.blockChance = blockChance;
            }

            public float getKnockbackResistance() {
                return knockbackResistance;
            }

            public void setKnockbackResistance(float knockbackResistance) {
                this.knockbackResistance = knockbackResistance;
            }
        }

        /**
         * Stats granted per point of WIND (Ghost Ranger).
         *
         * <p>Archetype: Untouchable striker. Wind users avoid damage through
         * evasion and excel at ranged combat with projectile mastery.
         */
        public static class WindGrants {
            private float evasion = 5.0f;                // +5.0 evasion rating
            private float accuracy = 3.0f;               // +3.0 accuracy rating
            private float projectileDamagePercent = 0.5f; // +0.5% projectile damage
            private float jumpForcePercent = 0.15f;      // +0.15% jump height
            private float projectileSpeedPercent = 0.3f; // +0.3% projectile speed

            public float getEvasion() {
                return evasion;
            }

            public void setEvasion(float evasion) {
                this.evasion = evasion;
            }

            public float getAccuracy() {
                return accuracy;
            }

            public void setAccuracy(float accuracy) {
                this.accuracy = accuracy;
            }

            public float getProjectileDamagePercent() {
                return projectileDamagePercent;
            }

            public void setProjectileDamagePercent(float projectileDamagePercent) {
                this.projectileDamagePercent = projectileDamagePercent;
            }

            public float getJumpForcePercent() {
                return jumpForcePercent;
            }

            public void setJumpForcePercent(float jumpForcePercent) {
                this.jumpForcePercent = jumpForcePercent;
            }

            public float getProjectileSpeedPercent() {
                return projectileSpeedPercent;
            }

            public void setProjectileSpeedPercent(float projectileSpeedPercent) {
                this.projectileSpeedPercent = projectileSpeedPercent;
            }
        }

        /**
         * Stats granted per point of VOID (Life Devourer).
         *
         * <p>Archetype: Sustain through destruction. Void users drain life,
         * amplify damage over time, and extend debuff effects.
         */
        public static class VoidGrants {
            private float lifeSteal = 0.1f;              // +0.1% life steal
            private float percentHitAsTrueDamage = 0.05f; // +0.05% of hit as true damage
            private float dotDamagePercent = 0.3f;       // +0.3% damage over time
            private float manaOnKill = 0.5f;             // +0.5 mana per kill
            private float statusEffectDuration = 0.3f;   // +0.3% effect duration

            public float getLifeSteal() {
                return lifeSteal;
            }

            public void setLifeSteal(float lifeSteal) {
                this.lifeSteal = lifeSteal;
            }

            public float getPercentHitAsTrueDamage() {
                return percentHitAsTrueDamage;
            }

            public void setPercentHitAsTrueDamage(float percentHitAsTrueDamage) {
                this.percentHitAsTrueDamage = percentHitAsTrueDamage;
            }

            public float getDotDamagePercent() {
                return dotDamagePercent;
            }

            public void setDotDamagePercent(float dotDamagePercent) {
                this.dotDamagePercent = dotDamagePercent;
            }

            public float getManaOnKill() {
                return manaOnKill;
            }

            public void setManaOnKill(float manaOnKill) {
                this.manaOnKill = manaOnKill;
            }

            public float getStatusEffectDuration() {
                return statusEffectDuration;
            }

            public void setStatusEffectDuration(float statusEffectDuration) {
                this.statusEffectDuration = statusEffectDuration;
            }
        }

        /**
         * Magic charges configuration (level-based, not attribute-based).
         *
         * <p>Determines how many concurrent active spells a player can maintain.
         * Charges scale with player level: {@code base + (level / perLevels)}.
         */
        public static class MagicChargesConfig {
            private int base = 1;          // Starting charges at level 1
            private int perLevels = 25;    // +1 charge every N levels

            public int getBase() {
                return base;
            }

            public void setBase(int base) {
                this.base = base;
            }

            public int getPerLevels() {
                return perLevels;
            }

            public void setPerLevels(int perLevels) {
                this.perLevels = perLevels;
            }
        }
    }

    /**
     * Combat system configuration.
     *
     * <p>Contains all tunable combat formula parameters including evasion,
     * parry mechanics, and critical strikes.
     */
    public static class CombatConfig {
        private float critMultiplier = 2.0f;
        private EvasionConfig evasion = new EvasionConfig();
        private ParryConfig parry = new ParryConfig();
        private EnvironmentalDamageConfig environmentalDamage = new EnvironmentalDamageConfig();
        private AttackSpeedConfig attackSpeed = new AttackSpeedConfig();

        /**
         * Base damage for unarmed attacks (no weapon equipped).
         *
         * <p>This value is used when a player attacks without an RPG weapon,
         * replacing vanilla weapon damage completely.
         * Default: 5.0
         */
        private float unarmedBaseDamage = 5.0f;

        /**
         * Final multiplier applied to unarmed player attacks (no weapon equipped).
         * 1.0 = full damage, 0.45 = 45% damage. Only affects players, not mobs.
         * Default: 0.45
         */
        private float unarmedDamageMultiplier = 0.45f;

        /**
         * Minimum damage threshold (as % of max HP) to trigger the red screen flash
         * and health alert. Hits below this threshold won't send the DamageInfo packet.
         * Default: 3.0 (3% of max HP)
         */
        private float healthAlertMinThreshold = 3.0f;


        public float getCritMultiplier() {
            return critMultiplier;
        }

        public void setCritMultiplier(float critMultiplier) {
            this.critMultiplier = critMultiplier;
        }

        public EvasionConfig getEvasion() {
            return evasion;
        }

        public void setEvasion(EvasionConfig evasion) {
            this.evasion = evasion;
        }

        public ParryConfig getParry() {
            return parry;
        }

        public void setParry(ParryConfig parry) {
            this.parry = parry;
        }

        public EnvironmentalDamageConfig getEnvironmentalDamage() {
            return environmentalDamage;
        }

        public void setEnvironmentalDamage(EnvironmentalDamageConfig environmentalDamage) {
            this.environmentalDamage = environmentalDamage;
        }

        public AttackSpeedConfig getAttackSpeed() {
            return attackSpeed;
        }

        public void setAttackSpeed(AttackSpeedConfig attackSpeed) {
            this.attackSpeed = attackSpeed;
        }

        public float getUnarmedBaseDamage() {
            return unarmedBaseDamage;
        }

        public void setUnarmedBaseDamage(float unarmedBaseDamage) {
            this.unarmedBaseDamage = unarmedBaseDamage;
        }

        public float getUnarmedDamageMultiplier() {
            return unarmedDamageMultiplier;
        }

        public void setUnarmedDamageMultiplier(float unarmedDamageMultiplier) {
            this.unarmedDamageMultiplier = unarmedDamageMultiplier;
        }

        public float getHealthAlertMinThreshold() {
            return healthAlertMinThreshold;
        }

        public void setHealthAlertMinThreshold(float healthAlertMinThreshold) {
            this.healthAlertMinThreshold = healthAlertMinThreshold;
        }


        /**
         * Validates combat configuration values.
         *
         * @throws ConfigValidationException if any value is invalid
         */
        public void validate() throws ConfigValidationException {
            if (critMultiplier < 1.0f) {
                throw new ConfigValidationException(
                    "combat.critMultiplier must be >= 1.0, got: " + critMultiplier);
            }
            if (unarmedBaseDamage < 0f) {
                throw new ConfigValidationException(
                    "combat.unarmedBaseDamage cannot be negative, got: " + unarmedBaseDamage);
            }
            if (unarmedDamageMultiplier < 0f || unarmedDamageMultiplier > 10f) {
                throw new ConfigValidationException(
                    "combat.unarmedDamageMultiplier must be between 0.0 and 10.0, got: " + unarmedDamageMultiplier);
            }
            if (evasion != null) {
                evasion.validate();
            }
            if (parry != null) {
                parry.validate();
            }
            if (environmentalDamage != null) {
                environmentalDamage.validate();
            }
            if (attackSpeed != null) {
                attackSpeed.validate();
            }
        }

        /**
         * Evasion/accuracy system configuration.
         *
         * <p>Uses Path of Exile-inspired formula:
         * <pre>
         * ChanceToHit = hitChanceConstant × Accuracy / (Accuracy + (Evasion × evasionScalingFactor)^evasionExponent)
         * </pre>
         *
         * <p>The formula provides diminishing returns on evasion stacking while
         * allowing accuracy to counter it naturally.
         */
        public static class EvasionConfig {
            /**
             * Minimum chance to hit (0.0 to 1.0).
             * Attacks can never have less than this hit chance.
             * Default: 0.05 (5%)
             */
            private float minHitChance = 0.05f;

            /**
             * Maximum chance to hit (0.0 to 1.0).
             * Attacks can never have more than this hit chance.
             * Default: 1.0 (100%)
             */
            private float maxHitChance = 1.0f;

            /**
             * Scaling factor applied to evasion before the exponent.
             * Lower values make evasion less effective.
             * Default: 0.2
             */
            private float evasionScalingFactor = 0.2f;

            /**
             * Exponent applied to scaled evasion for diminishing returns.
             * Values less than 1.0 create diminishing returns at high evasion.
             * Default: 0.9
             */
            private float evasionExponent = 0.9f;

            /**
             * Constant multiplier in the hit chance formula numerator.
             * Higher values favor the attacker.
             * Default: 1.25
             */
            private float hitChanceConstant = 1.25f;

            public float getMinHitChance() {
                return minHitChance;
            }

            public void setMinHitChance(float minHitChance) {
                this.minHitChance = minHitChance;
            }

            public float getMaxHitChance() {
                return maxHitChance;
            }

            public void setMaxHitChance(float maxHitChance) {
                this.maxHitChance = maxHitChance;
            }

            public float getEvasionScalingFactor() {
                return evasionScalingFactor;
            }

            public void setEvasionScalingFactor(float evasionScalingFactor) {
                this.evasionScalingFactor = evasionScalingFactor;
            }

            public float getEvasionExponent() {
                return evasionExponent;
            }

            public void setEvasionExponent(float evasionExponent) {
                this.evasionExponent = evasionExponent;
            }

            public float getHitChanceConstant() {
                return hitChanceConstant;
            }

            public void setHitChanceConstant(float hitChanceConstant) {
                this.hitChanceConstant = hitChanceConstant;
            }

            /**
             * Validates evasion configuration values.
             *
             * @throws ConfigValidationException if any value is invalid
             */
            public void validate() throws ConfigValidationException {
                if (minHitChance < 0f || minHitChance > 1f) {
                    throw new ConfigValidationException(
                        "combat.evasion.minHitChance must be between 0.0 and 1.0, got: " + minHitChance);
                }
                if (maxHitChance < 0f || maxHitChance > 1f) {
                    throw new ConfigValidationException(
                        "combat.evasion.maxHitChance must be between 0.0 and 1.0, got: " + maxHitChance);
                }
                if (minHitChance > maxHitChance) {
                    throw new ConfigValidationException(
                        "combat.evasion.minHitChance cannot be greater than maxHitChance");
                }
                if (evasionScalingFactor <= 0f) {
                    throw new ConfigValidationException(
                        "combat.evasion.evasionScalingFactor must be positive, got: " + evasionScalingFactor);
                }
                if (evasionExponent <= 0f) {
                    throw new ConfigValidationException(
                        "combat.evasion.evasionExponent must be positive, got: " + evasionExponent);
                }
                if (hitChanceConstant <= 0f) {
                    throw new ConfigValidationException(
                        "combat.evasion.hitChanceConstant must be positive, got: " + hitChanceConstant);
                }
            }
        }

        /**
         * Parry mechanics configuration.
         *
         * <p>When a parry is successful:
         * <ul>
         *   <li>A portion of incoming damage is reflected back to the attacker</li>
         *   <li>The defender takes reduced damage</li>
         *   <li>Reflected damage cannot kill the attacker (minimum HP floor)</li>
         * </ul>
         */
        public static class ParryConfig {
            /**
             * Portion of incoming damage reflected back to attacker (0.0 to 1.0).
             * Default: 0.5 (50%)
             */
            private float reflectAmount = 0.5f;

            /**
             * Damage multiplier for the defender after parry (0.0 to 1.0).
             * Default: 0.5 (50% damage taken)
             */
            private float damageReduction = 0.5f;

            /**
             * Minimum HP the attacker is left with after reflected damage.
             * Prevents parry from killing attackers.
             * Default: 1.0
             */
            private float reflectMinAttackerHp = 1.0f;

            public float getReflectAmount() {
                return reflectAmount;
            }

            public void setReflectAmount(float reflectAmount) {
                this.reflectAmount = reflectAmount;
            }

            public float getDamageReduction() {
                return damageReduction;
            }

            public void setDamageReduction(float damageReduction) {
                this.damageReduction = damageReduction;
            }

            public float getReflectMinAttackerHp() {
                return reflectMinAttackerHp;
            }

            public void setReflectMinAttackerHp(float reflectMinAttackerHp) {
                this.reflectMinAttackerHp = reflectMinAttackerHp;
            }

            /**
             * Validates parry configuration values.
             *
             * @throws ConfigValidationException if any value is invalid
             */
            public void validate() throws ConfigValidationException {
                if (reflectAmount < 0f || reflectAmount > 1f) {
                    throw new ConfigValidationException(
                        "combat.parry.reflectAmount must be between 0.0 and 1.0, got: " + reflectAmount);
                }
                if (damageReduction < 0f || damageReduction > 1f) {
                    throw new ConfigValidationException(
                        "combat.parry.damageReduction must be between 0.0 and 1.0, got: " + damageReduction);
                }
                if (reflectMinAttackerHp < 0f) {
                    throw new ConfigValidationException(
                        "combat.parry.reflectMinAttackerHp cannot be negative, got: " + reflectMinAttackerHp);
                }
            }
        }

        /**
         * Environmental damage configuration.
         *
         * <p>Converts fixed environmental damage (lava, drowning, etc.) to max HP
         * percentage-based damage. This ensures environmental hazards scale with
         * player progression rather than becoming trivial at high HP values.
         *
         * <p>Set any value to 0 to use vanilla fixed damage for that type.
         */
        public static class EnvironmentalDamageConfig {
            /**
             * Fire/Lava damage per tick as % of max HP.
             * Default: 5.0% (kills in ~20 ticks without escape)
             */
            private float fire = 5.0f;

            /**
             * Drowning damage per tick as % of max HP.
             * Default: 3.0%
             */
            private float drowning = 3.0f;

            /**
             * Suffocation damage (stuck in blocks) as % of max HP.
             * Default: 8.0%
             */
            private float suffocation = 8.0f;

            /**
             * Poison damage per tick as % of max HP.
             * Default: 2.0%
             */
            private float poison = 2.0f;

            /**
             * Void/out-of-world damage as % of max HP.
             * Default: 100.0% (instant kill)
             */
            private float outOfWorld = 100.0f;

            /**
             * Generic environment damage (cactus, etc.) as % of max HP.
             * Default: 2.0%
             */
            private float environment = 2.0f;

            /**
             * Gets the HP percentage for a given damage cause ID.
             *
             * @param causeId The damage cause ID (e.g., "Fire", "Drowning")
             * @return The HP percentage (0 = use vanilla fixed damage)
             */
            public float getHpPercentForCause(String causeId) {
                if (causeId == null) {
                    return environment;
                }
                String lower = causeId.toLowerCase();
                if (lower.contains("fire") || lower.contains("burn") || lower.contains("lava")) {
                    return fire;
                }
                if (lower.contains("drown")) {
                    return drowning;
                }
                if (lower.contains("suffoc")) {
                    return suffocation;
                }
                if (lower.contains("poison")) {
                    return poison;
                }
                if (lower.contains("void") || lower.contains("outofworld") || lower.contains("out_of_world")) {
                    return outOfWorld;
                }
                // Generic environmental damage
                return environment;
            }

            public float getFire() {
                return fire;
            }

            public void setFire(float fire) {
                this.fire = fire;
            }

            public float getDrowning() {
                return drowning;
            }

            public void setDrowning(float drowning) {
                this.drowning = drowning;
            }

            public float getSuffocation() {
                return suffocation;
            }

            public void setSuffocation(float suffocation) {
                this.suffocation = suffocation;
            }

            public float getPoison() {
                return poison;
            }

            public void setPoison(float poison) {
                this.poison = poison;
            }

            public float getOutOfWorld() {
                return outOfWorld;
            }

            public void setOutOfWorld(float outOfWorld) {
                this.outOfWorld = outOfWorld;
            }

            public float getEnvironment() {
                return environment;
            }

            public void setEnvironment(float environment) {
                this.environment = environment;
            }

            /**
             * Validates environmental damage configuration values.
             *
             * @throws ConfigValidationException if any value is invalid
             */
            public void validate() throws ConfigValidationException {
                if (fire < 0f || fire > 100f) {
                    throw new ConfigValidationException(
                        "combat.environmentalDamage.fire must be between 0 and 100, got: " + fire);
                }
                if (drowning < 0f || drowning > 100f) {
                    throw new ConfigValidationException(
                        "combat.environmentalDamage.drowning must be between 0 and 100, got: " + drowning);
                }
                if (suffocation < 0f || suffocation > 100f) {
                    throw new ConfigValidationException(
                        "combat.environmentalDamage.suffocation must be between 0 and 100, got: " + suffocation);
                }
                if (poison < 0f || poison > 100f) {
                    throw new ConfigValidationException(
                        "combat.environmentalDamage.poison must be between 0 and 100, got: " + poison);
                }
                if (outOfWorld < 0f || outOfWorld > 100f) {
                    throw new ConfigValidationException(
                        "combat.environmentalDamage.outOfWorld must be between 0 and 100, got: " + outOfWorld);
                }
                if (environment < 0f || environment > 100f) {
                    throw new ConfigValidationException(
                        "combat.environmentalDamage.environment must be between 0 and 100, got: " + environment);
                }
            }
        }

        /**
         * Attack speed system configuration (Tier 1 + Tier 2).
         *
         * <p><b>Tier 1:</b> {@code InteractionTimeShiftSystem} shifts server-side interaction
         * chain timing so damage windows match the player's attack speed stat.
         *
         * <p><b>Tier 2:</b> {@code AnimationSpeedSyncManager} sends per-player animation speed
         * packets so the visual swing speed matches the stat.
         */
        public static class AttackSpeedConfig {
            /**
             * Whether the attack speed system is enabled.
             * If disabled, attack speed stats have no effect.
             */
            private boolean enabled = true;

            /**
             * Dampening factor for animation speed sync.
             * Controls how attack speed stat translates to visual animation speed.
             * 0.5 means +100% attack speed → 1.5× animation speed.
             * Default: 0.5
             */
            private float animationSpeedScale = 0.5f;

            /**
             * Minimum animation speed multiplier.
             * Prevents frozen-looking animations even with negative attack speed.
             * Default: 0.5 (half speed)
             */
            private float animationMinSpeed = 0.5f;

            /**
             * Maximum animation speed multiplier.
             * Prevents visually broken animations at extreme attack speed.
             * Default: 3.0 (triple speed)
             */
            private float animationMaxSpeed = 3.0f;

            /**
             * Whether Tier 1 interaction time shift is enabled.
             * Shifts server-side interaction chain timing to match attack speed stat.
             * Independent of Tier 2 animation sync.
             * Default: true
             */
            private boolean interactionTimeShiftEnabled = true;

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public float getAnimationSpeedScale() {
                return animationSpeedScale;
            }

            public void setAnimationSpeedScale(float animationSpeedScale) {
                this.animationSpeedScale = animationSpeedScale;
            }

            public float getAnimationMinSpeed() {
                return animationMinSpeed;
            }

            public void setAnimationMinSpeed(float animationMinSpeed) {
                this.animationMinSpeed = animationMinSpeed;
            }

            public float getAnimationMaxSpeed() {
                return animationMaxSpeed;
            }

            public void setAnimationMaxSpeed(float animationMaxSpeed) {
                this.animationMaxSpeed = animationMaxSpeed;
            }

            public boolean isInteractionTimeShiftEnabled() {
                return interactionTimeShiftEnabled;
            }

            public void setInteractionTimeShiftEnabled(boolean interactionTimeShiftEnabled) {
                this.interactionTimeShiftEnabled = interactionTimeShiftEnabled;
            }

            /**
             * Converts this config class to the immutable record used by the animation sync manager.
             *
             * @return AnimationSpeedSyncConfig record
             */
            public io.github.larsonix.trailoforbis.combat.attackspeed.AnimationSpeedSyncConfig toAnimationSyncConfig() {
                return new io.github.larsonix.trailoforbis.combat.attackspeed.AnimationSpeedSyncConfig(
                        enabled, animationSpeedScale, animationMinSpeed, animationMaxSpeed
                );
            }

            /**
             * Validates attack speed configuration values.
             *
             * @throws ConfigValidationException if any value is invalid
             */
            public void validate() throws ConfigValidationException {
                if (animationMinSpeed <= 0) {
                    throw new ConfigValidationException(
                        "combat.attackSpeed.animationMinSpeed must be positive, got: " + animationMinSpeed);
                }
                if (animationMaxSpeed <= animationMinSpeed) {
                    throw new ConfigValidationException(
                        "combat.attackSpeed.animationMaxSpeed must be greater than animationMinSpeed, got: "
                            + animationMaxSpeed + " (min: " + animationMinSpeed + ")");
                }
                if (animationSpeedScale < 0) {
                    throw new ConfigValidationException(
                        "combat.attackSpeed.animationSpeedScale cannot be negative, got: " + animationSpeedScale);
                }
            }
        }
    }

    /**
     * Armor system configuration.
     *
     * <p>Controls how equipment armor and VIT-based armor contribute to
     * damage reduction using the Path of Exile formula:
     * <pre>
     * reduction = armor / (armor + formulaDivisor * damage)
     * </pre>
     */
    public static class ArmorConfig {
        /**
         * Whether to include vanilla equipment armor in total armor calculation.
         * If false, only VIT-based armor is used.
         */
        private boolean includeEquipmentArmor = true;

        /**
         * Multiplier applied to equipment armor values.
         * Use this to scale vanilla armor for balance purposes.
         * Default: 1.0 (no scaling)
         */
        private float equipmentArmorMultiplier = 1.0f;

        /**
         * Maximum damage reduction percentage from armor (0.0 to 1.0).
         * Default: 0.90 (90% cap, matching PoE)
         */
        private float maxReduction = 0.90f;

        /**
         * Divisor used in the armor formula: armor / (armor + divisor * damage).
         *
         * <p>This controls how armor scales against damage:
         * <ul>
         *   <li>Lower values make armor more effective against all damage</li>
         *   <li>Higher values make armor less effective, especially vs high damage</li>
         *   <li>Default: 10.0 (matches Path of Exile formula)</li>
         * </ul>
         */
        private float formulaDivisor = 10.0f;

        /**
         * Minimum armor effectiveness after penetration (0.0 to 1.0).
         *
         * <p>This acts as a floor on how much armor penetration can reduce effective armor.
         * With a floor of 0.5 (default), even 100% armor penetration only reduces armor
         * to 50% of its value. This ensures that armor investment remains meaningful
         * even against enemies with high armor penetration.
         *
         * <p>Examples with 1000 armor and 50% floor:
         * <ul>
         *   <li>0% pen → 1000 effective armor (100%)</li>
         *   <li>25% pen → 750 effective armor (75%)</li>
         *   <li>50% pen → 500 effective armor (50% = floor)</li>
         *   <li>75% pen → 500 effective armor (capped at floor)</li>
         *   <li>100% pen → 500 effective armor (capped at floor)</li>
         * </ul>
         *
         * <p>Default: 0.5 (50%)
         */
        private float armorPenetrationFloor = 0.5f;

        public boolean isIncludeEquipmentArmor() {
            return includeEquipmentArmor;
        }

        public void setIncludeEquipmentArmor(boolean includeEquipmentArmor) {
            this.includeEquipmentArmor = includeEquipmentArmor;
        }

        public float getEquipmentArmorMultiplier() {
            return equipmentArmorMultiplier;
        }

        public void setEquipmentArmorMultiplier(float equipmentArmorMultiplier) {
            this.equipmentArmorMultiplier = equipmentArmorMultiplier;
        }

        public float getMaxReduction() {
            return maxReduction;
        }

        public void setMaxReduction(float maxReduction) {
            this.maxReduction = maxReduction;
        }

        public float getFormulaDivisor() {
            return formulaDivisor;
        }

        public void setFormulaDivisor(float formulaDivisor) {
            this.formulaDivisor = formulaDivisor;
        }

        public float getArmorPenetrationFloor() {
            return armorPenetrationFloor;
        }

        public void setArmorPenetrationFloor(float armorPenetrationFloor) {
            this.armorPenetrationFloor = armorPenetrationFloor;
        }

        /**
         * Validates armor configuration values.
         *
         * @throws ConfigValidationException if any value is invalid
         */
        public void validate() throws ConfigValidationException {
            if (equipmentArmorMultiplier < 0f) {
                throw new ConfigValidationException(
                    "armor.equipmentArmorMultiplier cannot be negative, got: " + equipmentArmorMultiplier);
            }
            if (maxReduction < 0f || maxReduction > 1f) {
                throw new ConfigValidationException(
                    "armor.maxReduction must be between 0.0 and 1.0, got: " + maxReduction);
            }
            if (formulaDivisor <= 0f) {
                throw new ConfigValidationException(
                    "armor.formulaDivisor must be positive, got: " + formulaDivisor);
            }
            if (armorPenetrationFloor < 0f || armorPenetrationFloor > 1f) {
                throw new ConfigValidationException(
                    "armor.armorPenetrationFloor must be between 0.0 and 1.0, got: " + armorPenetrationFloor);
            }
        }
    }

    /**
     * Physical resistance configuration.
     *
     * <p>Physical resistance is a separate damage reduction layer from armor.
     * It applies only to physical damage (not elemental, fall, or environmental).
     *
     * <p><b>Key Differences from Armor:</b>
     * <ul>
     *   <li>Armor uses PoE formula with diminishing returns vs high damage</li>
     *   <li>Physical resistance is a flat percentage reduction</li>
     *   <li>Both can be stacked for layered defense</li>
     * </ul>
     */
    public static class PhysicalResistanceConfig {
        /**
         * Maximum physical resistance cap (0-100).
         * Default: 75% - prevents trivializing physical damage while allowing
         * significant investment to feel meaningful.
         */
        private float maxResistance = 75.0f;

        /**
         * Whether physical resistance applies to projectile damage.
         * Some games treat projectiles separately from melee physical damage.
         * Default: true (projectiles are considered physical)
         */
        private boolean appliesToProjectiles = true;

        public float getMaxResistance() {
            return maxResistance;
        }

        public void setMaxResistance(float maxResistance) {
            this.maxResistance = maxResistance;
        }

        public boolean isAppliesToProjectiles() {
            return appliesToProjectiles;
        }

        public void setAppliesToProjectiles(boolean appliesToProjectiles) {
            this.appliesToProjectiles = appliesToProjectiles;
        }

        /**
         * Validates physical resistance configuration values.
         *
         * @throws ConfigValidationException if any value is invalid
         */
        public void validate() throws ConfigValidationException {
            if (maxResistance < 0f || maxResistance > 100f) {
                throw new ConfigValidationException(
                    "physicalResistance.maxResistance must be between 0 and 100, got: " + maxResistance);
            }
        }
    }

    /**
     * Level scaling configuration.
     *
     * <p>Controls the shared exponential + diminishing returns curve used
     * by both gear and mob scaling. Below the transition level, normal
     * power-of-log scaling applies. Above it, an asymptotic curve kicks in
     * so each additional level gives less and less benefit.
     */
    public static class ScalingConfig {
        /**
         * Level where diminishing returns begin.
         * Below this, the standard power-of-log formula applies.
         * Default: 100
         */
        private int transitionLevel = 100;

        /**
         * How much stronger the absolute ceiling is compared to the transition value.
         * With transition at ~1.5× and ratio 2.0, the ceiling is ~3.0×.
         * Default: 2.0
         */
        private double maxMultiplierRatio = 2.0;

        /**
         * Divisor for the post-transition decay rate.
         * Higher values slow the approach to ceiling, stretching meaningful
         * progression to higher levels.
         * With divisor=5 and transition=100, ~63% of ceiling gap is closed
         * after 500 additional levels (instead of 100 with divisor=1).
         * Default: 5.0
         */
        private double decayDivisor = 5.0;

        public int getTransitionLevel() {
            return transitionLevel;
        }

        public void setTransitionLevel(int transitionLevel) {
            this.transitionLevel = transitionLevel;
        }

        public double getMaxMultiplierRatio() {
            return maxMultiplierRatio;
        }

        public void setMaxMultiplierRatio(double maxMultiplierRatio) {
            this.maxMultiplierRatio = maxMultiplierRatio;
        }

        public double getDecayDivisor() {
            return decayDivisor;
        }

        public void setDecayDivisor(double decayDivisor) {
            this.decayDivisor = decayDivisor;
        }

        public void validate() throws ConfigValidationException {
            if (transitionLevel < 2) {
                throw new ConfigValidationException(
                    "scaling.transitionLevel must be >= 2, got: " + transitionLevel);
            }
            if (maxMultiplierRatio < 1.01) {
                throw new ConfigValidationException(
                    "scaling.maxMultiplierRatio must be >= 1.01, got: " + maxMultiplierRatio);
            }
            if (decayDivisor < 1.0) {
                throw new ConfigValidationException(
                    "scaling.decayDivisor must be >= 1.0, got: " + decayDivisor);
            }
        }
    }

}
