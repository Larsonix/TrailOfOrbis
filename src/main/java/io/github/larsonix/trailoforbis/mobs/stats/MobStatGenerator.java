package io.github.larsonix.trailoforbis.mobs.stats;

import io.github.larsonix.trailoforbis.util.LevelScaling;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Random;

/**
 * Generates {@link MobStatProfile} instances for mobs based on level, distance bonus, and pool config.
 *
 * <p>Uses Dirichlet distribution to randomly allocate a stat pool across all {@link MobStatType}
 * values, then applies conversion factors and clamping via {@link StatConfig} to produce final stats.
 *
 * @see MobStatPoolConfig
 */
public class MobStatGenerator {
    private final MobStatPoolConfig config;
    private final DirichletDistributor distributor;

    public MobStatGenerator() {
        this.config = MobStatPoolConfig.createDefaults();
        this.distributor = new DirichletDistributor();
    }

    public MobStatGenerator(@Nonnull MobStatPoolConfig config) {
        this.config = config;
        this.distributor = new DirichletDistributor();
    }

    @Nonnull
    public MobStatProfile generate(int mobLevel, double distanceBonus, long seed) {
        Random random = new Random(seed);

        // Apply progressive scaling factor for gentler early-game difficulty
        double scalingFactor = config.calculateScalingFactor(mobLevel);

        // LevelScaling is applied TWICE here (intentional — produces the current balance):
        // 1) effectiveLevel: maps the multiplier curve back to a capped level value,
        //    creating diminishing returns on the base level contribution.
        // 2) expMultiplier: scales the total pool (including distance bonus) by the
        //    same curve, ensuring overall power follows LevelScaling progression.
        // Combined effect: pool grows as roughly LevelScaling.getMultiplier()².
        // HP gets a third application in MobScalingSystem.applyStatModifiers().
        double effectiveLevel = (LevelScaling.getMultiplier(mobLevel) - 1.0)
                * LevelScaling.getTransitionLevel() + 1.0;
        double levelPool = effectiveLevel * config.getPointsPerLevel() * scalingFactor;

        double expMultiplier = LevelScaling.getMultiplier(mobLevel);
        double totalPool = (levelPool + distanceBonus) * expMultiplier;

        Map<MobStatType, Double> shares = distributor.distribute(totalPool, config, random);

        MobStatProfile profile = applyConversionFactors(mobLevel, totalPool, shares, seed);

        return profile;
    }

    @Nonnull
    public MobStatProfile generateSpecial(int mobLevel, double distanceBonus, double specialMultiplier, long seed) {
        MobStatProfile baseProfile = generate(mobLevel, distanceBonus, seed);
        return baseProfile.withBossMultiplier(specialMultiplier);
    }

    @Nonnull
    private MobStatProfile applyConversionFactors(
            int mobLevel,
            double totalPool,
            Map<MobStatType, Double> shares,
            long seed
    ) {
        double maxHealth = finalizeStat(MobStatType.MAX_HEALTH, shares.get(MobStatType.MAX_HEALTH));
        double physicalDamage = finalizeStat(MobStatType.PHYSICAL_DAMAGE, shares.get(MobStatType.PHYSICAL_DAMAGE));
        double armor = finalizeStat(MobStatType.ARMOR, shares.get(MobStatType.ARMOR));
        double moveSpeed = finalizeStat(MobStatType.MOVE_SPEED, shares.get(MobStatType.MOVE_SPEED));
        double attackSpeed = finalizeStat(MobStatType.ATTACK_SPEED, shares.get(MobStatType.ATTACK_SPEED));
        double attackRange = finalizeStat(MobStatType.ATTACK_RANGE, shares.get(MobStatType.ATTACK_RANGE));
        double attackCooldown = finalizeStat(MobStatType.ATTACK_COOLDOWN, shares.get(MobStatType.ATTACK_COOLDOWN));
        double criticalChance = finalizeStat(MobStatType.CRITICAL_CHANCE, shares.get(MobStatType.CRITICAL_CHANCE));
        double criticalMultiplier = finalizeStat(MobStatType.CRITICAL_MULTIPLIER, shares.get(MobStatType.CRITICAL_MULTIPLIER));
        double dodgeChance = finalizeStat(MobStatType.DODGE_CHANCE, shares.get(MobStatType.DODGE_CHANCE));
        double blockChance = finalizeStat(MobStatType.BLOCK_CHANCE, shares.get(MobStatType.BLOCK_CHANCE));
        double parryChance = finalizeStat(MobStatType.PARRY_CHANCE, shares.get(MobStatType.PARRY_CHANCE));
        double lifeSteal = finalizeStat(MobStatType.LIFE_STEAL, shares.get(MobStatType.LIFE_STEAL));
        double healthRegen = finalizeStat(MobStatType.HEALTH_REGEN, shares.get(MobStatType.HEALTH_REGEN));
        double fireDamage = finalizeStat(MobStatType.FIRE_DAMAGE, shares.get(MobStatType.FIRE_DAMAGE));
        double waterDamage = finalizeStat(MobStatType.WATER_DAMAGE, shares.get(MobStatType.WATER_DAMAGE));
        double lightningDamage = finalizeStat(MobStatType.LIGHTNING_DAMAGE, shares.get(MobStatType.LIGHTNING_DAMAGE));
        double voidDamage = finalizeStat(MobStatType.VOID_DAMAGE, shares.get(MobStatType.VOID_DAMAGE));
        double fireResistance = finalizeStat(MobStatType.FIRE_RESISTANCE, shares.get(MobStatType.FIRE_RESISTANCE));
        double waterResistance = finalizeStat(MobStatType.WATER_RESISTANCE, shares.get(MobStatType.WATER_RESISTANCE));
        double lightningResistance = finalizeStat(MobStatType.LIGHTNING_RESISTANCE, shares.get(MobStatType.LIGHTNING_RESISTANCE));
        double voidResistance = finalizeStat(MobStatType.VOID_RESISTANCE, shares.get(MobStatType.VOID_RESISTANCE));
        double firePenetration = finalizeStat(MobStatType.FIRE_PENETRATION, shares.get(MobStatType.FIRE_PENETRATION));
        double waterPenetration = finalizeStat(MobStatType.WATER_PENETRATION, shares.get(MobStatType.WATER_PENETRATION));
        double lightningPenetration = finalizeStat(MobStatType.LIGHTNING_PENETRATION, shares.get(MobStatType.LIGHTNING_PENETRATION));
        double voidPenetration = finalizeStat(MobStatType.VOID_PENETRATION, shares.get(MobStatType.VOID_PENETRATION));
        double fireIncreasedDamage = finalizeStat(MobStatType.FIRE_INCREASED_DAMAGE, shares.get(MobStatType.FIRE_INCREASED_DAMAGE));
        double waterIncreasedDamage = finalizeStat(MobStatType.WATER_INCREASED_DAMAGE, shares.get(MobStatType.WATER_INCREASED_DAMAGE));
        double lightningIncreasedDamage = finalizeStat(MobStatType.LIGHTNING_INCREASED_DAMAGE, shares.get(MobStatType.LIGHTNING_INCREASED_DAMAGE));
        double voidIncreasedDamage = finalizeStat(MobStatType.VOID_INCREASED_DAMAGE, shares.get(MobStatType.VOID_INCREASED_DAMAGE));
        double fireMoreDamage = finalizeStat(MobStatType.FIRE_MORE_DAMAGE, shares.get(MobStatType.FIRE_MORE_DAMAGE));
        double waterMoreDamage = finalizeStat(MobStatType.WATER_MORE_DAMAGE, shares.get(MobStatType.WATER_MORE_DAMAGE));
        double lightningMoreDamage = finalizeStat(MobStatType.LIGHTNING_MORE_DAMAGE, shares.get(MobStatType.LIGHTNING_MORE_DAMAGE));
        double voidMoreDamage = finalizeStat(MobStatType.VOID_MORE_DAMAGE, shares.get(MobStatType.VOID_MORE_DAMAGE));
        double aggroRange = finalizeStat(MobStatType.AGGRO_RANGE, shares.get(MobStatType.AGGRO_RANGE));
        double reactionDelay = finalizeStat(MobStatType.REACTION_DELAY, shares.get(MobStatType.REACTION_DELAY));
        double chargeTime = finalizeStat(MobStatType.CHARGE_TIME, shares.get(MobStatType.CHARGE_TIME));
        double chargeDistance = finalizeStat(MobStatType.CHARGE_DISTANCE, shares.get(MobStatType.CHARGE_DISTANCE));
        double armorPenetration = finalizeStat(MobStatType.ARMOR_PENETRATION, shares.get(MobStatType.ARMOR_PENETRATION));
        double trueDamage = finalizeStat(MobStatType.TRUE_DAMAGE, shares.get(MobStatType.TRUE_DAMAGE));
        double accuracy = finalizeStat(MobStatType.ACCURACY, shares.get(MobStatType.ACCURACY));
        double knockbackResistance = finalizeStat(MobStatType.KNOCKBACK_RESISTANCE, shares.get(MobStatType.KNOCKBACK_RESISTANCE));

        return new MobStatProfile(
                mobLevel, totalPool,
                maxHealth, physicalDamage, armor,
                moveSpeed, attackSpeed, attackRange, attackCooldown,
                criticalChance, criticalMultiplier,
                dodgeChance, blockChance, parryChance,
                lifeSteal, healthRegen,
                fireDamage, waterDamage, lightningDamage, voidDamage,
                fireResistance, waterResistance, lightningResistance, voidResistance,
                firePenetration, waterPenetration, lightningPenetration, voidPenetration,
                fireIncreasedDamage, waterIncreasedDamage, lightningIncreasedDamage, voidIncreasedDamage,
                fireMoreDamage, waterMoreDamage, lightningMoreDamage, voidMoreDamage,
                aggroRange, reactionDelay, chargeTime, chargeDistance,
                armorPenetration, trueDamage,
                accuracy, knockbackResistance,
                seed
        );
    }

    private double finalizeStat(MobStatType type, double poolShare) {
        StatConfig statConfig = config.getStatConfig(type);
        return statConfig.finalize(poolShare);
    }

    @Nonnull
    public MobStatProfile getBaseStats(int mobLevel) {
        Random random = new Random(0);

        // Apply progressive scaling factor for consistency
        double scalingFactor = config.calculateScalingFactor(mobLevel);

        // Use capped effective level (same as generate())
        double effectiveLevel = (LevelScaling.getMultiplier(mobLevel) - 1.0)
                * LevelScaling.getTransitionLevel() + 1.0;
        double levelPool = effectiveLevel * config.getPointsPerLevel() * scalingFactor;

        // Apply exponential scaling to match gear progression
        double expMultiplier = LevelScaling.getMultiplier(mobLevel);
        double totalPool = levelPool * expMultiplier;

        double[] equalShares = new double[MobStatType.values().length];
        for (int i = 0; i < equalShares.length; i++) {
            equalShares[i] = totalPool / MobStatType.values().length;
        }

        Map<MobStatType, Double> shares = new java.util.EnumMap<>(MobStatType.class);
        MobStatType[] types = MobStatType.values();
        for (int i = 0; i < types.length; i++) {
            shares.put(types[i], equalShares[i]);
        }

        return applyConversionFactors(mobLevel, totalPool, shares, 0L);
    }

    @Nonnull
    public MobStatPoolConfig getConfig() {
        return config;
    }
}
