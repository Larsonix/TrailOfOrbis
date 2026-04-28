package io.github.larsonix.trailoforbis.lootfilter.model;

import io.github.larsonix.trailoforbis.gear.model.EquipmentType;
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Per-player loot filter state: profiles, quick filter, and active profile.
 *
 * <p>Quick filter and custom profiles are mutually exclusive:
 * <ul>
 *   <li>Setting {@code quickFilterRarity} clears {@code activeProfileId}</li>
 *   <li>Setting {@code activeProfileId} clears {@code quickFilterRarity}</li>
 * </ul>
 *
 * <p>Immutable — use {@code with*()} methods for updates.
 */
public final class PlayerFilterState {

    private final UUID playerId;
    private final List<FilterProfile> profiles;
    private final String activeProfileId;
    private final boolean filteringEnabled;
    private final GearRarity quickFilterRarity;
    private final Instant lastModified;

    private PlayerFilterState(UUID playerId, List<FilterProfile> profiles,
                              String activeProfileId, boolean filteringEnabled,
                              GearRarity quickFilterRarity, Instant lastModified) {
        this.playerId = playerId;
        this.profiles = List.copyOf(profiles);
        this.activeProfileId = activeProfileId;
        this.filteringEnabled = filteringEnabled;
        this.quickFilterRarity = quickFilterRarity;
        this.lastModified = lastModified;
    }

    // ═══════════════════════════════════════════════════════════════════
    // EVALUATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Evaluates an item against the active filter.
     * Quick filter takes priority over custom profiles.
     */
    @Nonnull
    public FilterAction evaluate(@Nonnull GearData gearData, @Nonnull EquipmentType equipmentType) {
        if (quickFilterRarity != null) {
            return gearData.rarity().ordinal() >= quickFilterRarity.ordinal()
                    ? FilterAction.ALLOW : FilterAction.BLOCK;
        }
        Optional<FilterProfile> profile = getActiveProfile();
        if (profile.isEmpty()) return FilterAction.ALLOW;
        return profile.get().evaluate(gearData, equipmentType);
    }

    /**
     * Whether this state has an active filter (quick or profile).
     */
    public boolean hasActiveFilter() {
        return quickFilterRarity != null || getActiveProfile().isPresent();
    }

    // ═══════════════════════════════════════════════════════════════════
    // QUERY
    // ═══════════════════════════════════════════════════════════════════

    @Nonnull
    public Optional<FilterProfile> getActiveProfile() {
        if (activeProfileId == null) return Optional.empty();
        return profiles.stream()
                .filter(p -> p.getId().equals(activeProfileId))
                .findFirst();
    }

    @Nonnull
    public Optional<FilterProfile> getProfileByName(@Nonnull String name) {
        return profiles.stream()
                .filter(p -> p.getName().equalsIgnoreCase(name))
                .findFirst();
    }

    @Nonnull
    public Optional<FilterProfile> getProfileById(@Nonnull String id) {
        return profiles.stream()
                .filter(p -> p.getId().equals(id))
                .findFirst();
    }

    public int getProfileCount() { return profiles.size(); }
    public boolean isUsingQuickFilter() { return quickFilterRarity != null; }

    // ═══════════════════════════════════════════════════════════════════
    // IMMUTABLE UPDATES
    // ═══════════════════════════════════════════════════════════════════

    public PlayerFilterState withFilteringEnabled(boolean enabled) {
        return new PlayerFilterState(playerId, profiles, activeProfileId, enabled,
                quickFilterRarity, Instant.now());
    }

    /** Sets active profile and clears quick filter (mutually exclusive). */
    public PlayerFilterState withActiveProfileId(@Nullable String id) {
        if (id != null && profiles.stream().noneMatch(p -> p.getId().equals(id))) {
            throw new IllegalArgumentException("No profile with id: " + id);
        }
        return new PlayerFilterState(playerId, profiles, id, filteringEnabled,
                null, Instant.now());
    }

    /** Sets quick filter rarity and clears active profile (mutually exclusive). */
    public PlayerFilterState withQuickFilterRarity(@Nullable GearRarity rarity) {
        return new PlayerFilterState(playerId, profiles, null, filteringEnabled,
                rarity, Instant.now());
    }

    public PlayerFilterState withAddedProfile(@Nonnull FilterProfile profile) {
        List<FilterProfile> newProfiles = new ArrayList<>(profiles);
        newProfiles.add(profile);
        return new PlayerFilterState(playerId, newProfiles, activeProfileId,
                filteringEnabled, quickFilterRarity, Instant.now());
    }

    public PlayerFilterState withRemovedProfile(@Nonnull String profileId) {
        List<FilterProfile> newProfiles = new ArrayList<>(profiles);
        newProfiles.removeIf(p -> p.getId().equals(profileId));
        String newActiveId = profileId.equals(activeProfileId) ? null : activeProfileId;
        return new PlayerFilterState(playerId, newProfiles, newActiveId,
                filteringEnabled, quickFilterRarity, Instant.now());
    }

    public PlayerFilterState withUpdatedProfile(@Nonnull FilterProfile updated) {
        List<FilterProfile> newProfiles = new ArrayList<>(profiles);
        for (int i = 0; i < newProfiles.size(); i++) {
            if (newProfiles.get(i).getId().equals(updated.getId())) {
                newProfiles.set(i, updated);
                return new PlayerFilterState(playerId, newProfiles, activeProfileId,
                        filteringEnabled, quickFilterRarity, Instant.now());
            }
        }
        throw new IllegalArgumentException("No profile with id: " + updated.getId());
    }

    // ═══════════════════════════════════════════════════════════════════
    // GETTERS
    // ═══════════════════════════════════════════════════════════════════

    @Nonnull public UUID getPlayerId() { return playerId; }
    @Nonnull public List<FilterProfile> getProfiles() { return profiles; }
    @Nullable public String getActiveProfileId() { return activeProfileId; }
    public boolean isFilteringEnabled() { return filteringEnabled; }
    @Nullable public GearRarity getQuickFilterRarity() { return quickFilterRarity; }
    @Nonnull public Instant getLastModified() { return lastModified; }

    // ═══════════════════════════════════════════════════════════════════
    // BUILDER
    // ═══════════════════════════════════════════════════════════════════

    public static Builder builder() { return new Builder(); }

    public Builder toBuilder() {
        return new Builder()
                .playerId(playerId)
                .profiles(new ArrayList<>(profiles))
                .activeProfileId(activeProfileId)
                .filteringEnabled(filteringEnabled)
                .quickFilterRarity(quickFilterRarity)
                .lastModified(lastModified);
    }

    public static final class Builder {
        private UUID playerId;
        private List<FilterProfile> profiles = new ArrayList<>();
        private String activeProfileId;
        private boolean filteringEnabled = false;
        private GearRarity quickFilterRarity;
        private Instant lastModified = Instant.now();

        public Builder playerId(@Nonnull UUID id) { this.playerId = id; return this; }
        public Builder profiles(@Nonnull List<FilterProfile> profiles) { this.profiles = profiles; return this; }
        public Builder activeProfileId(@Nullable String id) { this.activeProfileId = id; return this; }
        public Builder filteringEnabled(boolean enabled) { this.filteringEnabled = enabled; return this; }
        public Builder quickFilterRarity(@Nullable GearRarity rarity) { this.quickFilterRarity = rarity; return this; }
        public Builder lastModified(@Nonnull Instant lastModified) { this.lastModified = lastModified; return this; }

        @Nonnull
        public PlayerFilterState build() {
            if (playerId == null) throw new IllegalStateException("playerId is required");
            return new PlayerFilterState(playerId, profiles, activeProfileId,
                    filteringEnabled, quickFilterRarity, lastModified);
        }
    }
}
