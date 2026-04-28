package io.github.larsonix.trailoforbis.combat.modifiers;

/**
 * Detailed result of conditional multiplier calculation with individual values.
 *
 * <p>Each field is a multiplier (1.0 = no bonus). The {@code combined} field
 * is the product of all individual multipliers.
 *
 * @param combined     Product of all conditional multipliers
 * @param realm        Realm damage multiplier (from MONSTER_DAMAGE modifier)
 * @param execute      Execute bonus (vs targets below 35% HP)
 * @param vsFrozen     Damage vs Frozen bonus
 * @param vsShocked    Damage vs Shocked bonus
 * @param lowLife      Damage at Low Life bonus (attacker HP &le; 35%)
 * @param consecutive  Consecutive hit bonus (stacking per hit within 2s window)
 */
public record ConditionalResult(
    float combined,
    float realm,
    float execute,
    float vsFrozen,
    float vsShocked,
    float lowLife,
    float consecutive
) {
    /** No conditionals active — all multipliers are 1.0. */
    public static final ConditionalResult NONE = new ConditionalResult(1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f);

    /**
     * Whether any conditional multiplier is active (combined != 1.0).
     */
    public boolean hasAny() {
        return combined != 1.0f;
    }
}
