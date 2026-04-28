package io.github.larsonix.trailoforbis.compat.party;

import java.util.List;
import java.util.UUID;

/**
 * Callback interface for party lifecycle events.
 *
 * <p>Implementations receive notifications when party membership changes,
 * regardless of which party mod is providing the data.
 */
public interface PartyChangeListener {
    default void onMemberJoined(UUID partyId, UUID playerId) {}
    default void onMemberLeft(UUID partyId, UUID playerId) {}
    default void onPartyDisbanded(UUID partyId, List<UUID> formerMembers) {}
    default void onLeaderChanged(UUID partyId, UUID oldLeader, UUID newLeader) {}
    default void onPvpSettingChanged(UUID partyId, boolean pvpEnabled) {}
}
