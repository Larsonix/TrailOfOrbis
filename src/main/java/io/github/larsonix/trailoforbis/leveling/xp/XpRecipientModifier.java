package io.github.larsonix.trailoforbis.leveling.xp;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Per-player XP modifier applied during distribution.
 *
 * <p>Encapsulates modifiers that depend on the recipient's own context
 * (level-gap penalty, experience gain bonus) rather than the killer's.
 * Called once per recipient by {@code PartyIntegrationManager.distributeXp()}.
 */
@FunctionalInterface
public interface XpRecipientModifier {

    /**
     * Applies per-player modifiers to a raw XP share.
     *
     * @param recipientUuid The player receiving XP
     * @param rawShare The raw XP share before per-player adjustments
     * @return The final XP amount for this recipient (always >= 1)
     */
    long apply(@Nonnull UUID recipientUuid, long rawShare);

    /**
     * Applies per-player modifiers with group context for anti-boosting.
     *
     * <p>When {@code groupMaxLevel > 0}, the level-gap penalty may use the group's
     * highest level instead of the recipient's own level (if anti-boosting is enabled).
     * When {@code groupMaxLevel <= 0}, the recipient's own level is always used.
     *
     * @param recipientUuid The player receiving XP
     * @param rawShare The raw XP share before per-player adjustments
     * @param groupMaxLevel The highest level in the XP-sharing group, or -1 if not applicable
     * @return The final XP amount for this recipient (always >= 1)
     */
    default long apply(@Nonnull UUID recipientUuid, long rawShare, int groupMaxLevel) {
        return apply(recipientUuid, rawShare);
    }
}
