package io.github.larsonix.trailoforbis.maps.core;

import com.hypixel.hytale.codec.codecs.EnumCodec;

import javax.annotation.Nonnull;

/**
 * Defines the lifecycle states for a Realm instance.
 *
 * <p>State transitions:
 * <pre>
 * CREATING → READY → ACTIVE → ENDING → CLOSING
 *              ↓        ↓
 *           CLOSING  CLOSING (on timeout/abandon)
 * </pre>
 *
 * <p>Each state determines what operations are permitted and
 * what systems are active for the realm.
 */
public enum RealmState {

    /**
     * Instance is being spawned and initialized.
     * Players cannot enter yet.
     */
    CREATING("Creating", false, false, false),

    /**
     * Instance is ready and portal is active.
     * Players can enter but combat hasn't started.
     */
    READY("Ready", true, false, true),

    /**
     * Players are inside and combat is active.
     * Monsters are spawned and completion tracking is enabled.
     */
    ACTIVE("Active", true, true, true),

    /**
     * Realm has been completed.
     * No new entry allowed — players already inside can stay until evacuation.
     */
    ENDING("Ending", false, false, false),

    /**
     * Realm is being removed.
     * Players are being teleported out, cleanup in progress.
     */
    CLOSING("Closing", false, false, false);

    private final String displayName;
    private final boolean allowsEntry;
    private final boolean isCombatActive;
    private final boolean isPortalActive;

    RealmState(
            @Nonnull String displayName,
            boolean allowsEntry,
            boolean isCombatActive,
            boolean isPortalActive) {
        this.displayName = displayName;
        this.allowsEntry = allowsEntry;
        this.isCombatActive = isCombatActive;
        this.isPortalActive = isPortalActive;
    }

    /**
     * Gets the human-readable display name.
     *
     * @return The display name (e.g., "Active", "Closing")
     */
    @Nonnull
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Checks if players can enter this realm.
     *
     * <p>Entry is allowed during READY and ACTIVE states only.
     * ENDING blocks entry to prevent re-entry after victory.
     *
     * @return true if player entry is permitted
     */
    public boolean allowsEntry() {
        return allowsEntry;
    }

    /**
     * Checks if combat is active in this state.
     *
     * <p>Only true during ACTIVE state when monsters are fighting.
     *
     * @return true if combat systems should be running
     */
    public boolean isCombatActive() {
        return isCombatActive;
    }

    /**
     * Checks if the entry portal should be active.
     *
     * <p>Portal is active during READY and ACTIVE states.
     *
     * @return true if the portal should be interactable
     */
    public boolean isPortalActive() {
        return isPortalActive;
    }

    /**
     * Checks if this is a terminal state.
     *
     * <p>Terminal states (ENDING, CLOSING) cannot transition to active states.
     *
     * @return true if this is a terminal state
     */
    public boolean isTerminal() {
        return this == ENDING || this == CLOSING;
    }

    /**
     * Checks if the realm is still running (not closed).
     *
     * @return true if the realm is in a non-terminal state
     */
    public boolean isRunning() {
        return this != CLOSING;
    }

    /**
     * Checks if completion tracking should be active.
     *
     * @return true during ACTIVE state
     */
    public boolean isTrackingCompletion() {
        return this == ACTIVE;
    }

    /**
     * Gets the next logical state in the lifecycle.
     *
     * @return The next state, or CLOSING if already terminal
     */
    @Nonnull
    public RealmState getNextState() {
        return switch (this) {
            case CREATING -> READY;
            case READY -> ACTIVE;
            case ACTIVE -> ENDING;
            case ENDING -> CLOSING;
            case CLOSING -> CLOSING;
        };
    }

    /**
     * Checks if transition to the target state is valid.
     *
     * @param target The target state
     * @return true if the transition is allowed
     */
    public boolean canTransitionTo(@Nonnull RealmState target) {
        // Can always transition to CLOSING (force close)
        if (target == CLOSING) {
            return true;
        }
        // Normal progression
        return switch (this) {
            case CREATING -> target == READY;
            case READY -> target == ACTIVE;
            case ACTIVE -> target == ENDING;
            case ENDING -> target == CLOSING;
            case CLOSING -> false;
        };
    }

    /**
     * Parse state from string (case-insensitive).
     *
     * @param name State name
     * @return The corresponding RealmState
     * @throws IllegalArgumentException if name is not recognized
     */
    @Nonnull
    public static RealmState fromString(@Nonnull String name) {
        if (name == null) {
            throw new IllegalArgumentException("State name cannot be null");
        }
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown realm state: " + name);
        }
    }

    /**
     * Codec for serialization/deserialization.
     */
    public static final EnumCodec<RealmState> CODEC = new EnumCodec<>(RealmState.class);
}
