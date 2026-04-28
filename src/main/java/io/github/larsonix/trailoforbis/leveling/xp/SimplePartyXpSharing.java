package io.github.larsonix.trailoforbis.leveling.xp;

import io.github.larsonix.trailoforbis.leveling.api.LevelingService;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.UUID;

/**
 * Handles XP distribution among party members.
 *
 * <p>When enabled, XP from mob kills is split among nearby party members.
 * This integrates with the SimpleParty mod if available.
 *
 * <p>Distribution formula:
 * <pre>
 * xpPerMember = totalXp / partySize
 * </pre>
 *
 * <p>Each member receives an equal share. The killer does NOT receive extra XP.
 */
public class SimplePartyXpSharing {

    private final LevelingService levelingService;
    private final boolean enabled;

    /**
     * Creates a new party XP sharing handler.
     *
     * @param levelingService The leveling service
     * @param enabled Whether party sharing is enabled
     */
    public SimplePartyXpSharing(
        @Nonnull LevelingService levelingService,
        boolean enabled
    ) {
        this.levelingService = levelingService;
        this.enabled = enabled;
    }

    /**
     * Distributes XP among party members.
     *
     * <p>If party sharing is disabled or the party is empty/solo,
     * the killer receives full XP.
     *
     * @param killerId The player who got the kill
     * @param partyMembers UUIDs of party members (including killer)
     * @param totalXp The total XP to distribute
     */
    public void distributeXp(
        @Nonnull UUID killerId,
        @Nonnull Collection<UUID> partyMembers,
        long totalXp
    ) {
        if (!enabled || partyMembers.isEmpty()) {
            // Party sharing disabled - killer gets all XP
            levelingService.addXp(killerId, totalXp, XpSource.MOB_KILL);
            return;
        }

        // Include killer if not already in party members
        int partySize = partyMembers.contains(killerId)
            ? partyMembers.size()
            : partyMembers.size() + 1;

        if (partySize <= 1) {
            // Solo player - no sharing
            levelingService.addXp(killerId, totalXp, XpSource.MOB_KILL);
            return;
        }

        // Calculate XP per member (minimum 1)
        long xpPerMember = Math.max(1, totalXp / partySize);

        // Distribute to party members
        for (UUID memberId : partyMembers) {
            if (memberId.equals(killerId)) {
                // Killer gets XP with MOB_KILL source
                levelingService.addXp(memberId, xpPerMember, XpSource.MOB_KILL);
            } else {
                // Party members get XP with PARTY_SHARE source
                levelingService.addXp(memberId, xpPerMember, XpSource.PARTY_SHARE);
            }
        }

        // If killer wasn't in party members list, add their XP
        if (!partyMembers.contains(killerId)) {
            levelingService.addXp(killerId, xpPerMember, XpSource.MOB_KILL);
        }
    }

    /**
     * Distributes XP to a solo player (no party).
     *
     * @param killerId The player who got the kill
     * @param totalXp The XP to grant
     */
    public void grantSoloXp(@Nonnull UUID killerId, long totalXp) {
        levelingService.addXp(killerId, totalXp, XpSource.MOB_KILL);
    }

    /** @return true if enabled */
    public boolean isEnabled() {
        return enabled;
    }
}
