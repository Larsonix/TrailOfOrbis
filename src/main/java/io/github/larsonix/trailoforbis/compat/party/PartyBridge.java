package io.github.larsonix.trailoforbis.compat.party;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Abstraction over external party mod APIs.
 *
 * <p>All party queries go through this interface. The active implementation
 * is either {@link PartyProReflectionBridge} (when PartyPro is detected)
 * or {@link NoOpPartyBridge} (solo mode fallback).
 *
 * <p>Callsites never need to check {@link #isAvailable()} before calling
 * other methods — the NoOp bridge returns safe defaults for every query.
 */
public interface PartyBridge {

    /** Whether a compatible party mod is loaded and operational. */
    boolean isAvailable();

    /** Whether the player is currently in any party. */
    boolean isInParty(@Nonnull UUID playerId);

    /** Whether two players are members of the same party. */
    boolean areInSameParty(@Nonnull UUID player1, @Nonnull UUID player2);

    /** Gets the party ID for the player's current party. */
    Optional<UUID> getPartyId(@Nonnull UUID playerId);

    /** Gets ALL members of the player's party (including leader). */
    @Nonnull
    List<UUID> getPartyMembers(@Nonnull UUID playerId);

    /** Gets online members of the player's party (including leader if online). */
    @Nonnull
    List<UUID> getOnlinePartyMembers(@Nonnull UUID playerId);

    /** Gets the leader of the player's party. */
    Optional<UUID> getPartyLeader(@Nonnull UUID playerId);

    /** Gets the total size of the player's party (including leader). */
    int getPartySize(@Nonnull UUID playerId);

    /** Whether PvP is enabled between members of the player's party. */
    boolean isPvpEnabledInParty(@Nonnull UUID playerId);

    /** Sets custom text fields displayed in the party HUD for a player. */
    void setCustomText(@Nonnull UUID playerId, String text1, String text2);

    /** Clears custom text fields for a player. */
    void clearCustomText(@Nonnull UUID playerId);

    /** Registers a listener for party lifecycle events. */
    void registerEventListener(@Nonnull PartyChangeListener listener);

    /** Unregisters a previously registered listener. */
    void unregisterEventListener(@Nonnull PartyChangeListener listener);
}
