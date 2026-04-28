package io.github.larsonix.trailoforbis.combat.blocking;

/**
 * Result of an active blocking attempt.
 *
 * <p>Active blocking occurs when a player holds a shield and blocks an incoming attack.
 * This record captures the outcome: whether the block succeeded, how much damage was
 * reduced, and the stamina cost incurred.
 *
 * <p>Key states:
 * <ul>
 *   <li>{@link #NOT_BLOCKING} - Player was not holding a shield / not blocking</li>
 *   <li>{@link #FAILED_ROLL} - Player was blocking but the block chance roll failed</li>
 *   <li>{@link #success(float, float)} - Block succeeded with damage reduction and stamina cost</li>
 * </ul>
 *
 * @param blocked Whether the block was successful
 * @param damageReduction The damage reduction as a decimal (0.0-1.0, where 1.0 = 100% blocked)
 * @param staminaCost The stamina cost after applying reduction modifiers
 * @param fullBlock Whether this was a full block (100% damage reduction)
 */
public record BlockResult(
    boolean blocked,
    float damageReduction,
    float staminaCost,
    boolean fullBlock
) {
    /** Player was not actively blocking (no shield or not holding block). */
    public static final BlockResult NOT_BLOCKING = new BlockResult(false, 0f, 0f, false);

    /** Player was blocking but the block chance roll failed. */
    public static final BlockResult FAILED_ROLL = new BlockResult(false, 0f, 0f, false);

    /**
     * Creates a successful block result.
     *
     * @param reduction The damage reduction as a decimal (0.0-1.0)
     * @param staminaCost The stamina cost after modifiers
     * @return A successful BlockResult
     */
    public static BlockResult success(float reduction, float staminaCost) {
        return new BlockResult(true, reduction, staminaCost, reduction >= 1.0f);
    }
}
