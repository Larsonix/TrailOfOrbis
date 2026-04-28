package io.github.larsonix.trailoforbis.compat.party;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Solo-mode fallback when no party mod is installed.
 *
 * <p>Returns safe defaults for every query — the player is always
 * treated as a solo player. All mutation methods are no-ops.
 */
public final class NoOpPartyBridge implements PartyBridge {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public boolean isInParty(@Nonnull UUID playerId) {
        return false;
    }

    @Override
    public boolean areInSameParty(@Nonnull UUID player1, @Nonnull UUID player2) {
        return false;
    }

    @Override
    public Optional<UUID> getPartyId(@Nonnull UUID playerId) {
        return Optional.empty();
    }

    @Nonnull
    @Override
    public List<UUID> getPartyMembers(@Nonnull UUID playerId) {
        return List.of(playerId);
    }

    @Nonnull
    @Override
    public List<UUID> getOnlinePartyMembers(@Nonnull UUID playerId) {
        return List.of(playerId);
    }

    @Override
    public Optional<UUID> getPartyLeader(@Nonnull UUID playerId) {
        return Optional.empty();
    }

    @Override
    public int getPartySize(@Nonnull UUID playerId) {
        return 1;
    }

    @Override
    public boolean isPvpEnabledInParty(@Nonnull UUID playerId) {
        return true; // No party = PvP unrestricted
    }

    @Override
    public void setCustomText(@Nonnull UUID playerId, String text1, String text2) {
        // No-op
    }

    @Override
    public void clearCustomText(@Nonnull UUID playerId) {
        // No-op
    }

    @Override
    public void registerEventListener(@Nonnull PartyChangeListener listener) {
        // No-op
    }

    @Override
    public void unregisterEventListener(@Nonnull PartyChangeListener listener) {
        // No-op
    }
}
