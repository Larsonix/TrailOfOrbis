package io.github.larsonix.trailoforbis.maps.event;

import io.github.larsonix.trailoforbis.maps.instance.RealmInstance;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Event fired when a realm is completed (all objectives met).
 *
 * <p>At this point:
 * <ul>
 *   <li>All monsters have been killed</li>
 *   <li>The realm is transitioning to ENDING state</li>
 *   <li>Players have a grace period before closure</li>
 * </ul>
 *
 * <p>Use this event to:
 * <ul>
 *   <li>Award completion rewards</li>
 *   <li>Log completion statistics</li>
 *   <li>Show victory UI</li>
 *   <li>Spawn bonus rewards/loot</li>
 * </ul>
 */
public class RealmCompletedEvent extends RealmEvent {

    private final Duration completionTime;
    private final Set<UUID> participants;
    private final Map<UUID, Integer> playerKills;
    private final int totalKills;
    private final int bossesKilled;
    private final int elitesKilled;

    /**
     * Creates a new realm completed event.
     *
     * @param realm The completed realm instance
     */
    public RealmCompletedEvent(@Nonnull RealmInstance realm) {
        super(realm);

        // Capture completion statistics
        var tracker = realm.getCompletionTracker();
        this.completionTime = tracker.getElapsedTime();
        this.participants = tracker.getParticipants();
        this.playerKills = tracker.getAllPlayerKills();
        this.totalKills = tracker.getKilledByPlayers();
        this.bossesKilled = tracker.getBossesKilled();
        this.elitesKilled = tracker.getElitesKilled();
    }

    /**
     * @return The time it took to complete the realm
     */
    @Nonnull
    public Duration getCompletionTime() {
        return completionTime;
    }

    /**
     * @return Completion time in seconds
     */
    public long getCompletionTimeSeconds() {
        return completionTime.getSeconds();
    }

    /**
     * @return Set of all player UUIDs who participated
     */
    @Nonnull
    public Set<UUID> getParticipants() {
        return participants;
    }

    /**
     * @return Number of participants
     */
    public int getParticipantCount() {
        return participants.size();
    }

    /**
     * @return Map of player UUID to kill count
     */
    @Nonnull
    public Map<UUID, Integer> getPlayerKills() {
        return playerKills;
    }

    /**
     * Gets kills for a specific player.
     *
     * @param playerId The player UUID
     * @return The player's kill count
     */
    public int getKillsForPlayer(@Nonnull UUID playerId) {
        return playerKills.getOrDefault(playerId, 0);
    }

    /**
     * @return Total monsters killed by players
     */
    public int getTotalKills() {
        return totalKills;
    }

    /**
     * @return Number of boss monsters killed
     */
    public int getBossesKilled() {
        return bossesKilled;
    }

    /**
     * @return Number of elite monsters killed
     */
    public int getElitesKilled() {
        return elitesKilled;
    }

    /**
     * Calculates a bonus multiplier based on completion time.
     * Faster completions get higher bonuses.
     *
     * @return Bonus multiplier (1.0 = no bonus, up to 2.0 for very fast)
     */
    public float getSpeedBonus() {
        long timeout = getRealm().getTimeout().getSeconds();
        long actual = completionTime.getSeconds();

        if (timeout <= 0 || actual >= timeout) {
            return 1.0f;
        }

        // Linear scaling: 100% of timeout = 1.0x, 0% of timeout = 2.0x
        float ratio = (float) actual / timeout;
        return 2.0f - ratio;
    }
}
