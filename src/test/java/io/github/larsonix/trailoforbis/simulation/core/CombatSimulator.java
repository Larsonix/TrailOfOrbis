package io.github.larsonix.trailoforbis.simulation.core;

import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.combat.AttackType;
import io.github.larsonix.trailoforbis.combat.DamageBreakdown;
import io.github.larsonix.trailoforbis.combat.RPGDamageCalculator;
import io.github.larsonix.trailoforbis.simulation.core.AvoidanceModel.AvoidanceResult;

import javax.annotation.Nonnull;

/**
 * Simulates combat encounters using real RPGDamageCalculator + AvoidanceModel.
 *
 * <p>Matches the in-game 7-phase pipeline:
 * <ol>
 *   <li>Phase 3: Avoidance check (dodge, evasion, passive block)</li>
 *   <li>Phase 4: RPGDamageCalculator (only if not avoided)</li>
 * </ol>
 *
 * <p>Avoidance is checked PER HIT, not as an aggregate modifier.
 * This correctly models that passive block = 100% damage negation on a hit,
 * and evasion = per-attack miss chance based on accuracy vs evasion.
 */
public final class CombatSimulator {

    /** Base attack speed: 1 hit per second. */
    private static final float BASE_ATTACK_INTERVAL = 1.0f;

    private final RPGDamageCalculator calculator;
    private final AvoidanceModel avoidance;

    public CombatSimulator() {
        this(new AvoidanceModel());
    }

    public CombatSimulator(AvoidanceModel avoidance) {
        this.calculator = new RPGDamageCalculator();
        this.avoidance = avoidance;
    }

    /**
     * Simulates a 1v1 combat encounter (backward compat).
     */
    @Nonnull
    public CombatResult simulate(
            @Nonnull ComputedStats playerStats,
            @Nonnull ComputedStats mobStats,
            int iterations) {
        return simulate(playerStats, mobStats, iterations, 1);
    }

    /**
     * Simulates a 1vN combat encounter between player and multiple mobs.
     *
     * <p>Mob incoming DPS is multiplied by {@code concurrentMobs} (fractional).
     * Player attacks one mob at a time (single-target DPS unchanged).
     * Regen and life steal remain constant — they don't scale with mob count.
     *
     * <p>Fractional mobs model average encounter size:
     * 2.5 mobs = mob DPS × 2.5 (smooth scaling, no step-function artifacts).
     *
     * @param playerStats    Player's final ComputedStats
     * @param mobStats       Single mob's ComputedStats
     * @param iterations     Attack iterations for averaging
     * @param concurrentMobs Average number of mobs attacking (fractional)
     */
    @Nonnull
    public CombatResult simulate(
            @Nonnull ComputedStats playerStats,
            @Nonnull ComputedStats mobStats,
            int iterations,
            double concurrentMobs) {

        float playerWeaponDmg = playerStats.getWeaponBaseDamage();
        float mobBaseDmg = mobStats.getPhysicalDamage();

        // =====================================================================
        // Player attacking mob — avoidance + damage per hit
        // =====================================================================
        double playerTotalDamage = 0;
        int playerCrits = 0;
        int playerHits = 0;
        int playerDodged = 0;
        int playerEvaded = 0;
        int playerBlocked = 0;

        for (int i = 0; i < iterations; i++) {
            // Phase 3: Avoidance (mob defends against player)
            AvoidanceResult avoid = avoidance.check(mobStats, playerStats);
            if (avoid != AvoidanceResult.HIT) {
                switch (avoid) {
                    case DODGED -> playerDodged++;
                    case EVADED -> playerEvaded++;
                    case BLOCKED -> playerBlocked++;
                    default -> {}
                }
                continue; // Attack fully avoided — no damage calculated
            }

            // Phase 4: Damage calculation (only on hit)
            playerHits++;
            DamageBreakdown hit = calculator.calculate(
                    playerWeaponDmg,
                    playerStats, playerStats.toElementalStats(),
                    mobStats, mobStats.toElementalStats(),
                    AttackType.MELEE, false);
            playerTotalDamage += hit.totalDamage();
            if (hit.wasCritical()) playerCrits++;
        }

        // =====================================================================
        // Mob attacking player — avoidance + damage per hit
        // =====================================================================
        double mobTotalDamage = 0;
        int mobCrits = 0;
        int mobHits = 0;
        int mobDodged = 0;
        int mobEvaded = 0;
        int mobBlocked = 0;

        for (int i = 0; i < iterations; i++) {
            // Phase 3: Avoidance (player defends against mob)
            AvoidanceResult avoid = avoidance.check(playerStats, mobStats);
            if (avoid != AvoidanceResult.HIT) {
                switch (avoid) {
                    case DODGED -> mobDodged++;
                    case EVADED -> mobEvaded++;
                    case BLOCKED -> mobBlocked++;
                    default -> {}
                }
                continue;
            }

            // Phase 4: Damage calculation (only on hit)
            mobHits++;
            DamageBreakdown hit = calculator.calculate(
                    mobBaseDmg,
                    mobStats, mobStats.toElementalStats(),
                    playerStats, playerStats.toElementalStats(),
                    AttackType.MELEE, false);
            mobTotalDamage += hit.totalDamage();
            if (hit.wasCritical()) mobCrits++;
        }

        // =====================================================================
        // Calculate metrics
        // =====================================================================

        // Average damage PER ATTACK ATTEMPT (including misses as 0)
        double avgPlayerDmgPerAttempt = playerTotalDamage / iterations;
        double avgMobDmgPerAttempt = mobTotalDamage / iterations;

        // Average damage when a hit connects (for reporting)
        double avgPlayerHitDmg = playerHits > 0 ? playerTotalDamage / playerHits : 0;
        double avgMobHitDmg = mobHits > 0 ? mobTotalDamage / mobHits : 0;

        // DPS = average damage per attempt × attacks per second
        float playerAtkSpeedMult = Math.max(0.1f, 1.0f + playerStats.getAttackSpeedPercent() / 100f);
        float mobAtkSpeedMult = Math.max(0.1f, 1.0f + mobStats.getAttackSpeedPercent() / 100f);
        // Player attacks one mob at a time (single-target DPS)
        double playerDPS = avgPlayerDmgPerAttempt * playerAtkSpeedMult / BASE_ATTACK_INTERVAL;
        // N mobs each attack independently — total incoming DPS scales by concurrentMobs
        double singleMobDPS = avgMobDmgPerAttempt * mobAtkSpeedMult / BASE_ATTACK_INTERVAL;
        double mobDPS = singleMobDPS * concurrentMobs;

        // Health pools
        double mobHP = mobStats.getMaxHealth();
        double playerHP = playerStats.getMaxHealth();

        // === LIFE STEAL HEALING (Void identity) ===
        // Formula from RPGDamageSystem:800-809:
        //   healAmount = rpgDamage × (lifeSteal / 100) × (1 + healthRecoveryPercent / 100)
        // lifeLeech uses identical formula — combine both
        float lifeSteal = playerStats.getLifeSteal();
        float lifeLeech = playerStats.getLifeLeech();
        float healthRecoveryPct = playerStats.getHealthRecoveryPercent();
        double totalStealPct = (lifeSteal + lifeLeech) / 100.0;
        double stealRecoveryMult = 1.0 + healthRecoveryPct / 100.0;
        // Healing per second = playerDPS × steal% × recovery multiplier
        double lifeStealHPS = playerDPS * totalStealPct * stealRecoveryMult;

        // === ENERGY SHIELD (Water identity) ===
        // Formula from DamageModifierProcessor:163-182:
        //   absorbed = min(damage, currentShield)
        //   remaining = damage - absorbed
        // Shield acts as extra HP pool that must be depleted first
        double energyShield = playerStats.getEnergyShield();

        // === EFFECTIVE HEALTH POOL ===
        // Total effective HP = base HP + energy shield
        double totalEffectiveHP = playerHP + energyShield;

        // === HEALTH REGEN + LIFE STEAL combined sustain ===
        double playerRegenPerSec = playerStats.getHealthRegen();
        double totalSustainPerSec = playerRegenPerSec + lifeStealHPS;

        // Effective mob DPS after all sustain
        double effectiveMobDPS = Math.max(0.001, mobDPS - totalSustainPerSec);

        // TTK / TTD
        double ttk = playerDPS > 0 ? mobHP / playerDPS : Double.MAX_VALUE;
        double ttd = totalEffectiveHP / effectiveMobDPS;

        // Survivability
        double survivability = ttd / Math.max(ttk, 0.001);

        // Avoidance rates for reporting
        double playerAvoidRate = (double)(mobDodged + mobEvaded + mobBlocked) / iterations;
        double mobAvoidRate = (double)(playerDodged + playerEvaded + playerBlocked) / iterations;

        return new CombatResult(
                avgPlayerHitDmg, playerDPS, avgMobHitDmg, mobDPS,
                mobHP, totalEffectiveHP,
                ttk, ttd, survivability,
                playerRegenPerSec, lifeStealHPS, totalSustainPerSec,
                energyShield,
                (double) playerCrits / Math.max(1, playerHits),
                playerAvoidRate,
                (double) mobDodged / iterations,
                (double) mobEvaded / iterations,
                (double) mobBlocked / iterations,
                playerStats.getEvasion(),
                playerStats.getPassiveBlockChance()
        );
    }

    /**
     * Result of a simulated combat encounter.
     */
    public record CombatResult(
            double avgPlayerHit,
            double playerDPS,
            double avgMobHit,
            double mobDPS,
            double mobHP,
            double playerHP,      // Effective HP (base HP + energy shield)
            double ttk,
            double ttd,
            double survivability,
            // Sustain breakdown
            double healthRegen,    // HP/s from regen stat
            double lifeStealHPS,   // HP/s from life steal + leech
            double totalSustain,   // Combined HP/s
            double energyShield,   // Extra HP pool from Water
            double critRate,
            // Avoidance breakdown
            double playerAvoidRate,
            double dodgeRate,
            double evasionRate,
            double blockRate,
            float evasionRating,
            float blockChance
    ) {
        public boolean playerWins() {
            return survivability > 1.0;
        }
    }

    /**
     * Estimates average concurrent mobs attacking the player at a given level.
     *
     * <p>Smooth linear ramp based on mob-scaling.yml spawn_multiplier
     * (level_per_multiplier=40) and aggro_range (base 16 blocks, scales with pool).
     *
     * <p>Formula: {@code 1.0 + (level - 1) / 50.0}
     * <ul>
     *   <li>Level 1: 1.0 mobs</li>
     *   <li>Level 25: 1.5 mobs</li>
     *   <li>Level 50: 2.0 mobs</li>
     *   <li>Level 75: 2.5 mobs</li>
     *   <li>Level 100: 3.0 mobs</li>
     * </ul>
     *
     * <p>Fractional values represent average encounter size — sometimes
     * the player fights 2 mobs, sometimes 3, averaging to 2.5.
     *
     * @param level Player/mob level
     * @return Average concurrent mobs (fractional)
     */
    public static double estimateConcurrentMobs(int level) {
        return 1.0 + (level - 1) / 50.0;
    }
}
