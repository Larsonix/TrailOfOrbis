package io.github.larsonix.trailoforbis.combat.attackspeed;

import io.github.larsonix.trailoforbis.combat.attackspeed.config.WeaponSpeedProfile;

import javax.annotation.Nonnull;

/**
 * Resolves per-frame attack speed multipliers from player stats and weapon profile.
 *
 * <p><b>Formula (6-stat model):</b>
 * <pre>
 * // Chain speed: how fast the swing completes
 * chainPercent = attackSpeed% × atkW + castSpeed% × castW + projAtkSpd% × projW + combo
 * chainMult = 1.0 + (chainPercent × globalScale / 100)
 *
 * // Cooldown speed: how fast the inter-attack gap expires
 * // Each weapon's "primary speed stat" reduces the WHOLE attack cycle (swing + gap)
 * cdPercent = attackSpeed% × atkW + castSpeed% × castW + projAtkSpd% × projW + cooldownRecovery% × cdW
 * cdMult = 1.0 + (cdPercent × globalScale / 100)
 *
 * // Animation: derived from chain speed, never outruns it
 * animMult = 1.0 + (chainMult - 1.0) × profileAnimScale   // ratio of chain speed change
 * animMult = min(animMult, chainMult)                       // hard cap: never faster than server
 * </pre>
 *
 * <p>Each weapon profile defines weights (0.0–1.3) per stat, creating distinct identities:
 * daggers benefit heavily from attack speed + combo; battleaxes from cooldown recovery;
 * staves from cast speed; bows from projectile attack speed.
 */
public final class AttackSpeedResolver {

    private AttackSpeedResolver() {}

    /**
     * Resolves attack speed multipliers from the full stat set.
     *
     * @param attackSpeedPercent Melee swing speed stat
     * @param cooldownRecoveryPercent Cooldown gap reduction stat (dedicated gap-only)
     * @param comboSpeedBonus Per-combo-hit acceleration stat
     * @param projectileAttackSpeedPercent Ranged draw/fire speed stat
     * @param castSpeedPercent Magic weapon cast speed stat
     * @param comboStage Current combo stage (1 = first hit, 2+ = bonus)
     * @param profile The weapon's speed profile (weights + limits)
     * @param globalScale Overall dampening factor (config.yml, typically 0.5)
     * @param globalAnimMin Global animation speed floor
     * @param globalAnimMax Global animation speed ceiling
     * @return Resolved multipliers for chain, cooldown, and animation
     */
    @Nonnull
    public static AttackSpeedSnapshot resolve(
            float attackSpeedPercent,
            float cooldownRecoveryPercent,
            float comboSpeedBonus,
            float projectileAttackSpeedPercent,
            float castSpeedPercent,
            int comboStage,
            @Nonnull WeaponSpeedProfile profile,
            float globalScale,
            float globalAnimMin,
            float globalAnimMax
    ) {
        WeaponSpeedProfile.SpeedLimits limits = profile.getSpeedLimits();

        // Stat weights — how much each stat type benefits this weapon
        float atkWeight = profile.getStatWeight("attackSpeedPercent", 1.0f);
        float cdWeight = profile.getStatWeight("cooldownRecoveryPercent", 1.0f);
        float castWeight = profile.getStatWeight("castSpeedPercent", 0.0f);
        float projWeight = profile.getStatWeight("projectileAttackSpeedPercent", 0.0f);
        float comboWeight = profile.getStatWeight("comboSpeedBonus", 0.0f);

        // Base speed contribution from each weapon class's "primary speed stat"
        // Melee weapons: attackSpeedPercent × atkWeight
        // Magic weapons: castSpeedPercent × castWeight (atkWeight is 0)
        // Ranged weapons: projectileAttackSpeedPercent × projWeight
        float primaryPercent = attackSpeedPercent * atkWeight
                + castSpeedPercent * castWeight
                + projectileAttackSpeedPercent * projWeight;

        // Chain speed: primary stat contribution + combo bonus
        float chainPercent = primaryPercent;

        // Combo bonus: universal base (everyone) + stat amplification (invested builds)
        // Formula: (stage - 1) × (baseCombo + comboStat × comboWeight)
        // Stage 1 = 0 bonus, Stage 2 = 1× bonus, Stage 3 = 2× bonus
        if (comboStage > 1 && profile.getComboStages() > 1) {
            float comboHits = comboStage - 1;
            float baseCombo = profile.getBaseComboPercent();
            float statCombo = comboSpeedBonus * comboWeight;
            chainPercent += comboHits * (baseCombo + statCombo);
        }

        // Cooldown speed: primary speed stat reduces the WHOLE attack cycle (swing + gap),
        // plus dedicated cooldown recovery stat for gap-only acceleration.
        // This ensures staff users (castSpeed) and ranged users (projAtkSpeed) also
        // benefit from shorter cooldowns, not just melee (attackSpeed).
        float cdPercent = primaryPercent
                + cooldownRecoveryPercent * cdWeight;

        // Early exit if nothing contributes
        if (Math.abs(chainPercent) < 0.001f && Math.abs(cdPercent) < 0.001f) {
            return AttackSpeedSnapshot.IDENTITY;
        }

        float chainMult = 1.0f + (chainPercent * globalScale / 100.0f);
        float cdMult = 1.0f + (cdPercent * globalScale / 100.0f);

        // Clamp to profile-specific limits (chain + cooldown)
        chainMult = clamp(chainMult, limits.getMinMultiplier(), limits.getMaxMultiplier());
        cdMult = clamp(cdMult, limits.getMinMultiplier(), limits.getMaxMultiplier());

        // Animation derives from chain speed, dampened by profile's animationSpeedScale.
        // animScale acts as a ratio of the chain speed change shown visually:
        //   1.0 = animation exactly matches server timing (recommended)
        //   0.8 = animation shows 80% of the speed change (slightly behind server)
        // CRITICAL: animation must NEVER outrun chain speed. If damage lands after
        // the swing looks complete, it feels like a "delay on hit."
        float animScale = limits.getAnimationSpeedScale();
        float animMult = 1.0f + (chainMult - 1.0f) * animScale;
        animMult = Math.min(animMult, chainMult); // hard cap: never faster than server

        // Clamp animation to global limits (server-wide visual bounds)
        animMult = clamp(animMult, globalAnimMin, globalAnimMax);

        return new AttackSpeedSnapshot(chainMult, cdMult, animMult);
    }

    /**
     * Convenience overload for when only attack speed percent is available
     * (used by AnimationSpeedSyncManager which doesn't have full stats access).
     */
    @Nonnull
    public static AttackSpeedSnapshot resolve(
            float attackSpeedPercent,
            @Nonnull WeaponSpeedProfile profile,
            float globalScale,
            float globalAnimMin,
            float globalAnimMax
    ) {
        return resolve(attackSpeedPercent, 0f, 0f, 0f, 0f, 1,
                profile, globalScale, globalAnimMin, globalAnimMax);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
