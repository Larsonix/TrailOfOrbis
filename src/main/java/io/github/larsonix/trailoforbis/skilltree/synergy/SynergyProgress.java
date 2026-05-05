package io.github.larsonix.trailoforbis.skilltree.synergy;

/**
 * Immutable snapshot of a synergy node's current progress for display purposes.
 *
 * <p>Contains all the data needed to render a progress line like:
 * "15 nodes → +10% | Next at 18"
 *
 * @param currentCount    Number of relevant nodes currently allocated (e.g., 15 Fire nodes)
 * @param perCount        Nodes needed per bonus increment (e.g., 3)
 * @param increments      Number of complete increments earned (count / perCount)
 * @param currentBonus    Total bonus value currently granted (increments * bonusValue, capped)
 * @param cap             Maximum bonus value (0 = uncapped)
 * @param capped          Whether the bonus has reached its cap
 * @param nextThreshold   Count needed for the next increment (0 if capped or uncapped with no meaningful next)
 * @param countLabel      Human-readable label for what's being counted (e.g., "Fire nodes", "total nodes")
 */
public record SynergyProgress(
    int currentCount,
    int perCount,
    int increments,
    double currentBonus,
    double cap,
    boolean capped,
    int nextThreshold,
    String countLabel
) {

    /**
     * Whether the player has any progress at all (at least one relevant node allocated).
     */
    public boolean hasProgress() {
        return currentCount > 0;
    }

    /**
     * Whether at least one full increment has been earned.
     */
    public boolean hasBonus() {
        return increments > 0;
    }
}
