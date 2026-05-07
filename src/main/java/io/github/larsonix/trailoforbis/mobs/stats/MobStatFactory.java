package io.github.larsonix.trailoforbis.mobs.stats;

import io.github.larsonix.trailoforbis.elemental.ElementType;
import io.github.larsonix.trailoforbis.elemental.ElementalStats;
import io.github.larsonix.trailoforbis.mobs.MobScalingConfig;
import io.github.larsonix.trailoforbis.mobs.archetype.ArchetypeResolver;
import io.github.larsonix.trailoforbis.mobs.archetype.MobArchetype;
import io.github.larsonix.trailoforbis.mobs.archetype.MobArchetypeConfig;
import io.github.larsonix.trailoforbis.mobs.model.MobStats;
import io.github.larsonix.trailoforbis.mobs.profile.ResistanceProfile;
import io.github.larsonix.trailoforbis.mobs.profile.ResistanceProfileResolver;
import io.github.larsonix.trailoforbis.util.LevelScaling;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Random;
import java.util.Set;

/**
 * Design-first mob stat generator using Template + Noise distribution.
 *
 * <p>Replaces {@link MobStatGenerator}'s random Dirichlet approach with intentional,
 * archetype-driven stat allocation. Every stat value traces back to a clear design
 * decision: pool generation → archetype ratios → conversion factors → noise.
 *
 * <h3>Architecture:</h3>
 * <pre>
 * totalPool(level)  →  pool × ratio × archetypeMult  →  × conversionFactor + base  →  × noise  →  final stat
 *      ↑                     ↑                              ↑                           ↑
 *   LevelScaling      design doc ratios              mob-stat-pool.yml             ±15% Gaussian
 *   (shared w/gear)   (35% HP, 25% dmg...)          (factor=0.88 for HP)          (not on resists)
 * </pre>
 *
 * <h3>Pool Ratios (Warrior baseline, before archetype multiplier):</h3>
 * <ul>
 *   <li>HP: 35% — survival budget, biggest share</li>
 *   <li>Damage: 25% — primary threat</li>
 *   <li>Armor: 15% — damage reduction</li>
 *   <li>Accuracy: 10% — hit chance vs player evasion</li>
 *   <li>Evasion: 5% — dodge chance</li>
 *   <li>Remaining 10%: split among health regen, true damage, armor pen</li>
 * </ul>
 *
 * <p>Archetype multipliers then skew these: Brute pushes HP to 49% (0.35×1.4),
 * Assassin pushes Evasion to 9% (0.05×1.8). This IS the differentiation system.
 *
 * <p>The raw stat values from this factory are intentionally larger than the old
 * Dirichlet output. The weighted HP/damage formulas in {@code MobScalingSystem}
 * and {@code RPGDamageSystem} translate raw stats into final in-game values.
 * Those formulas are recalibrated in Step 4 to produce the target kill times
 * (3-5 hits normal, 8-15 elite, 30-60s boss).
 */
public class MobStatFactory {

    private final MobStatPoolConfig poolConfig;
    private final ArchetypeResolver archetypeResolver;
    private final ResistanceProfileResolver resistanceResolver;
    private final MobArchetypeConfig archetypeConfig;

    @Nullable
    private final MobScalingConfig.ElementalConfig elementalConfig;

    // ==================== Pool Allocation Ratios (Design Doc, Section 10) ====================
    // These define what fraction of totalPool goes to each stat for a Warrior (baseline).
    // Archetype multipliers (Section 6) are applied on top.
    // Total = 1.0 — every pool point is accounted for.

    private static final double HP_RATIO = 0.35;
    private static final double DAMAGE_RATIO = 0.45;
    private static final double ARMOR_RATIO = 0.08;
    private static final double ACCURACY_RATIO = 0.04;
    private static final double EVASION_RATIO = 0.03;
    private static final double HEALTH_REGEN_RATIO = 0.02;
    private static final double TRUE_DAMAGE_RATIO = 0.015;
    private static final double ARMOR_PEN_RATIO = 0.015;
    // = 1.00 total

    public MobStatFactory(
            @Nonnull MobStatPoolConfig poolConfig,
            @Nonnull ArchetypeResolver archetypeResolver,
            @Nonnull ResistanceProfileResolver resistanceResolver,
            @Nonnull MobArchetypeConfig archetypeConfig,
            @Nullable MobScalingConfig.ElementalConfig elementalConfig) {
        this.poolConfig = poolConfig;
        this.archetypeResolver = archetypeResolver;
        this.resistanceResolver = resistanceResolver;
        this.archetypeConfig = archetypeConfig;
        this.elementalConfig = elementalConfig;
    }

    /**
     * Generates stats for a mob using Template + Noise distribution.
     *
     * @param level           Effective mob level
     * @param distanceBonus   Distance-based pool bonus
     * @param roleName        Mob's role name (for archetype/resistance resolution)
     * @param npcGroups       Mob's NPC groups
     * @param detectedElement Detected element from MobElementResolver (nullable)
     * @param seed            Random seed for noise
     * @return Immutable MobStats with all combat stats set
     */
    @Nonnull
    public MobStats generate(
            int level,
            double distanceBonus,
            @Nullable String roleName,
            @Nonnull Set<String> npcGroups,
            @Nullable ElementType detectedElement,
            long seed) {

        Random random = new Random(seed);

        // ===== 1. Pool Generation (identical formula to MobStatGenerator) =====
        double scalingFactor = poolConfig.calculateScalingFactor(level);
        double effectiveLevel = (LevelScaling.getMultiplier(level) - 1.0)
                * LevelScaling.getTransitionLevel() + 1.0;
        double levelPool = effectiveLevel * poolConfig.getPointsPerLevel() * scalingFactor;
        double expMultiplier = LevelScaling.getMultiplier(level);
        double totalPool = (levelPool + distanceBonus) * expMultiplier;

        // ===== 2. Resolve archetype and resistance profile =====
        MobArchetype archetype = archetypeResolver.resolve(roleName, npcGroups);
        ResistanceProfile resistanceProfile = resistanceResolver.resolve(roleName, npcGroups, detectedElement);

        // ===== 3. Noise config =====
        MobArchetypeConfig.NoiseConfig noiseConfig = archetypeConfig.getNoise();
        boolean noiseEnabled = noiseConfig.isEnabled();

        // ===== 4. Template distribution: pool × ratio × archetypeMult → convert → noise =====
        double hp = convertStat(MobStatType.MAX_HEALTH,
                totalPool * HP_RATIO * archetype.getHpMultiplier(),
                random, noiseEnabled, noiseConfig);
        double damage = convertStat(MobStatType.PHYSICAL_DAMAGE,
                totalPool * DAMAGE_RATIO * archetype.getDamageMultiplier(),
                random, noiseEnabled, noiseConfig);
        double armor = convertStat(MobStatType.ARMOR,
                totalPool * ARMOR_RATIO * archetype.getArmorMultiplier(),
                random, noiseEnabled, noiseConfig);
        double accuracy = convertStat(MobStatType.ACCURACY,
                totalPool * ACCURACY_RATIO,
                random, noiseEnabled, noiseConfig);
        double evasion = convertStat(MobStatType.DODGE_CHANCE,
                totalPool * EVASION_RATIO * archetype.getEvasionMultiplier(),
                random, noiseEnabled, noiseConfig);
        double healthRegen = convertStat(MobStatType.HEALTH_REGEN,
                totalPool * HEALTH_REGEN_RATIO,
                random, noiseEnabled, noiseConfig);
        double trueDamage = convertStat(MobStatType.TRUE_DAMAGE,
                totalPool * TRUE_DAMAGE_RATIO,
                random, noiseEnabled, noiseConfig);
        double armorPen = convertStat(MobStatType.ARMOR_PENETRATION,
                totalPool * ARMOR_PEN_RATIO,
                random, noiseEnabled, noiseConfig);

        // ===== 5. Fixed archetype stats (not pool-distributed) =====
        double moveSpeed = archetype.getSpeedMultiplier();
        double critChance = archetype.getCritChance();
        double critMult = archetype.getCritMultiplier();
        double lifeSteal = archetype.getLifeSteal();
        double knockbackResist = archetype.getKnockbackResistance();

        // Archetype fixed armorPen adds on top of pool-based
        armorPen = Math.max(armorPen, archetype.getArmorPenetration());

        // ===== 6. Elemental stats from resistance profile + element config =====
        // Only CASTER mobs get offensive elemental damage — warriors, archers, etc. stay physical.
        // Resistances apply to all archetypes (defensive identity is independent of offense).
        ElementalStats elementalStats = buildElementalStats(level, detectedElement, resistanceProfile, archetype);

        // ===== 7. Build MobStats (new record: no blockChance/parryChance, has ailment fields) =====
        return new MobStats(
                level, totalPool,
                // Core
                Math.max(1, hp),
                Math.max(1, damage),
                Math.max(0, armor),
                moveSpeed,
                Math.max(0, accuracy),
                // Offensive
                Math.max(0, critChance),
                Math.max(100, critMult),
                Math.max(0, armorPen),
                Math.max(0, lifeSteal),
                Math.max(0, trueDamage),
                // Defensive
                Math.max(0, evasion),
                Math.max(0, knockbackResist),
                // Sustain
                Math.max(0, healthRegen),
                // Ailment (defaults — rarity tier overrides in Step 6)
                1.0,  // ailmentThresholdMultiplier
                1.0,  // ailmentEffectiveness
                // Elemental
                elementalStats,
                // Archetype — drives damage type conversion (CASTER = elemental base)
                archetype
        );
    }

    /**
     * Converts a pool share into a final stat value.
     *
     * <p>Pipeline: poolShare × factor + baseValue → clamp(min, max) → × (1 + noise)
     *
     * <p>Uses the same {@link StatConfig} conversion factors from {@code mob-stat-pool.yml}
     * that the old generator used. The factors translate abstract pool points into
     * meaningful stat values (e.g., factor=0.88 for HP means 100 pool points → 88 HP bonus).
     */
    private double convertStat(
            @Nonnull MobStatType type,
            double poolShare,
            @Nonnull Random random,
            boolean noiseEnabled,
            @Nonnull MobArchetypeConfig.NoiseConfig noiseConfig) {

        StatConfig config = poolConfig.getStatConfig(type);
        double value = config.finalize(poolShare);

        if (noiseEnabled) {
            double noise = random.nextGaussian() * noiseConfig.getStandardDeviation();
            noise = Math.max(-noiseConfig.getMaxDeviation(), Math.min(noiseConfig.getMaxDeviation(), noise));
            value *= (1.0 + noise);
        }

        return value;
    }

    /**
     * Builds ElementalStats from resistance profile + elemental damage scaling.
     *
     * <p>Resistances: from profile (deterministic, no noise — defines identity).
     * All archetypes get resistances (defensive identity is independent of offense).
     *
     * <p>Elemental damage/penetration: ONLY for {@link MobArchetype#CASTER} mobs.
     * Warriors, archers, tanks, etc. deal physical damage only — their element
     * detection is used for resistances but not for offensive elemental stats.
     * Map modifiers (MONSTERS_EXTRA_FIRE, etc.) can still add elemental damage
     * to any mob as a realm challenge mechanic — that's handled separately in
     * {@code RPGDamageSystem.calculateRealmElementalBonusDamage()}.
     */
    @Nullable
    private ElementalStats buildElementalStats(
            int level,
            @Nullable ElementType detectedElement,
            @Nonnull ResistanceProfile profile,
            @Nonnull MobArchetype archetype) {

        boolean hasResistances = profile.hasResistances();
        // Only CASTER mobs get offensive elemental stats
        boolean isCaster = archetype == MobArchetype.CASTER;
        boolean hasOffensiveElement = isCaster && detectedElement != null && elementalConfig != null;

        if (!hasResistances && !hasOffensiveElement) {
            return null;
        }

        ElementalStats stats = new ElementalStats();

        // Resistances from profile (all 6 elements, all archetypes)
        for (ElementType element : ElementType.values()) {
            double resistance = profile.getResistance(element);
            if (resistance != 0.0) {
                stats.setResistance(element, resistance);
            }
        }

        // Elemental damage and penetration — CASTER archetype only
        if (hasOffensiveElement) {
            double flatDamage = level * elementalConfig.getDamagePerLevel();
            double penetration = Math.min(
                    level * elementalConfig.getPenetrationPerLevel(),
                    elementalConfig.getMaxBasePenetration()
            );
            penetration = Math.min(
                    penetration + elementalConfig.getAffinityPenetrationBonus(),
                    elementalConfig.getMaxAffinityPenetration()
            );

            stats.setFlatDamage(detectedElement, flatDamage);
            stats.setPenetration(detectedElement, penetration);
        }

        return stats;
    }

    // ==================== Static Helpers ====================

    /**
     * Calculates the reference mob accuracy at a given level.
     *
     * <p>Used by UI components (stats page, death recap) to calculate hit chance
     * against a "typical mob" at a given level. Uses the pool formula with
     * accuracy's share and conversion factor.
     *
     * @param poolConfig The pool config for conversion factors
     * @param level      The mob level
     * @return The accuracy value a typical mob would have at this level
     */
    public static double getReferenceAccuracy(@Nonnull MobStatPoolConfig poolConfig, int level) {
        // Same pool math as generate()
        double scalingFactor = poolConfig.calculateScalingFactor(level);
        double effectiveLevel = (LevelScaling.getMultiplier(level) - 1.0)
                * LevelScaling.getTransitionLevel() + 1.0;
        double levelPool = effectiveLevel * poolConfig.getPointsPerLevel() * scalingFactor;
        double expMultiplier = LevelScaling.getMultiplier(level);
        double totalPool = levelPool * expMultiplier;

        StatConfig accConfig = poolConfig.getStatConfig(MobStatType.ACCURACY);
        double poolShare = totalPool * ACCURACY_RATIO; // Warrior baseline
        return accConfig.finalize(poolShare);
    }

    // ==================== Accessors ====================

    @Nonnull
    public ArchetypeResolver getArchetypeResolver() {
        return archetypeResolver;
    }

    @Nonnull
    public ResistanceProfileResolver getResistanceResolver() {
        return resistanceResolver;
    }

    @Nonnull
    public MobStatPoolConfig getPoolConfig() {
        return poolConfig;
    }
}
