package io.github.larsonix.trailoforbis.lootfilter;

import com.hypixel.hytale.logger.HytaleLogger;
import io.github.larsonix.trailoforbis.database.DataManager;
import io.github.larsonix.trailoforbis.gear.model.EquipmentType;
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.lootfilter.config.LootFilterConfig;
import io.github.larsonix.trailoforbis.lootfilter.model.FilterAction;
import io.github.larsonix.trailoforbis.lootfilter.model.FilterProfile;
import io.github.larsonix.trailoforbis.lootfilter.model.ModifierFilterCategory;
import io.github.larsonix.trailoforbis.lootfilter.model.PlayerFilterState;
import io.github.larsonix.trailoforbis.lootfilter.repository.LootFilterRepository;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Central coordinator for the loot filter system.
 *
 * <p>Handles lifecycle, player join/disconnect, evaluation, quick filter,
 * profile CRUD, presets, and modifier category mapping.
 *
 * <p><b>Fail-open design</b>: If any error occurs during evaluation,
 * returns {@link FilterAction#ALLOW}. Never accidentally blocks pickup due to bugs.
 */
public final class LootFilterManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final LootFilterRepository repository;
    private final LootFilterConfig config;
    private final Map<String, ModifierFilterCategory> modifierCategoryMap;
    private final List<FilterProfile> presetProfiles;

    public LootFilterManager(@Nonnull DataManager dataManager, @Nonnull LootFilterConfig config) {
        this.repository = new LootFilterRepository(dataManager);
        this.config = config;
        this.modifierCategoryMap = buildModifierCategoryMap();
        this.presetProfiles = config.getPresets().stream()
                .map(LootFilterConfig.PresetConfig::toProfile)
                .toList();
    }

    // ═══════════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════

    public void initialize() {
        LOGGER.atInfo().log("Loot filter system initialized (%d presets, %d category overrides)",
                presetProfiles.size(), config.getCategoryOverrides().size());
    }

    public void shutdown() {
        repository.saveAll();
        repository.clearCache();
        LOGGER.atInfo().log("Loot filter system shut down");
    }

    // ═══════════════════════════════════════════════════════════════════
    // PLAYER LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════

    public void onPlayerJoin(@Nonnull UUID playerId) {
        repository.getOrCreate(playerId);
        LOGGER.atFine().log("Loaded loot filter state for %s", playerId.toString().substring(0, 8));
    }

    public void onPlayerDisconnect(@Nonnull UUID playerId) {
        PlayerFilterState state = repository.getOrCreate(playerId);
        repository.save(state);
        repository.evict(playerId);
    }

    // ═══════════════════════════════════════════════════════════════════
    // FAST-PATH EVALUATION (called on every RPG gear pickup)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Quick check: is filtering active for this player?
     */
    public boolean isFilteringEnabled(@Nonnull UUID playerId) {
        if (!config.isEnabled()) return false;
        PlayerFilterState state = repository.getOrCreate(playerId);
        return state.isFilteringEnabled() && state.hasActiveFilter();
    }

    /**
     * Full evaluation. Only call if {@link #isFilteringEnabled} returned true.
     * Fail-open: returns ALLOW on any error.
     */
    @Nonnull
    public FilterAction evaluate(@Nonnull UUID playerId, @Nonnull GearData gearData,
                                 @Nonnull EquipmentType equipmentType) {
        try {
            PlayerFilterState state = repository.getOrCreate(playerId);
            return state.evaluate(gearData, equipmentType);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Filter evaluation error for %s — allowing pickup",
                    playerId.toString().substring(0, 8));
            return FilterAction.ALLOW;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // QUICK FILTER
    // ═══════════════════════════════════════════════════════════════════

    public void setQuickFilter(@Nonnull UUID playerId, @Nonnull GearRarity minRarity) {
        PlayerFilterState state = repository.getOrCreate(playerId)
                .withQuickFilterRarity(minRarity)
                .withFilteringEnabled(true);
        repository.save(state);
    }

    public void clearQuickFilter(@Nonnull UUID playerId) {
        PlayerFilterState state = repository.getOrCreate(playerId)
                .withQuickFilterRarity(null);
        repository.save(state);
    }

    // ═══════════════════════════════════════════════════════════════════
    // FILTERING TOGGLE
    // ═══════════════════════════════════════════════════════════════════

    public void toggleFiltering(@Nonnull UUID playerId) {
        PlayerFilterState state = repository.getOrCreate(playerId);
        repository.save(state.withFilteringEnabled(!state.isFilteringEnabled()));
    }

    public void setFilteringEnabled(@Nonnull UUID playerId, boolean enabled) {
        repository.save(repository.getOrCreate(playerId).withFilteringEnabled(enabled));
    }

    // ═══════════════════════════════════════════════════════════════════
    // PROFILE MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════

    @Nonnull
    public PlayerFilterState getState(@Nonnull UUID playerId) {
        return repository.getOrCreate(playerId);
    }

    public void saveState(@Nonnull PlayerFilterState state) {
        repository.save(state);
    }

    public void setActiveProfile(@Nonnull UUID playerId, @Nonnull String profileId) {
        PlayerFilterState state = repository.getOrCreate(playerId)
                .withActiveProfileId(profileId)
                .withFilteringEnabled(true);
        repository.save(state);
    }

    public void createProfile(@Nonnull UUID playerId, @Nonnull String name) {
        PlayerFilterState state = repository.getOrCreate(playerId);
        if (state.getProfileCount() >= config.getMaxProfilesPerPlayer()) {
            throw new IllegalStateException("Maximum profiles reached (" + config.getMaxProfilesPerPlayer() + ")");
        }
        FilterProfile profile = FilterProfile.builder()
                .name(name)
                .defaultAction(config.getDefaults().getDefaultAction())
                .build();
        repository.save(state.withAddedProfile(profile));
    }

    public void createProfileFromPreset(@Nonnull UUID playerId, @Nonnull String presetName) {
        PlayerFilterState state = repository.getOrCreate(playerId);
        if (state.getProfileCount() >= config.getMaxProfilesPerPlayer()) {
            throw new IllegalStateException("Maximum profiles reached (" + config.getMaxProfilesPerPlayer() + ")");
        }
        FilterProfile preset = getPreset(presetName)
                .orElseThrow(() -> new IllegalArgumentException("Unknown preset: " + presetName));
        // Create a fresh copy with a new UUID
        FilterProfile copy = preset.toBuilder().build();
        repository.save(state.withAddedProfile(copy));
    }

    public void deleteProfile(@Nonnull UUID playerId, @Nonnull String profileId) {
        repository.save(repository.getOrCreate(playerId).withRemovedProfile(profileId));
    }

    public void saveProfile(@Nonnull UUID playerId, @Nonnull FilterProfile updated) {
        if (updated.getRules().size() > config.getMaxRulesPerProfile()) {
            throw new IllegalStateException("Maximum rules reached (" + config.getMaxRulesPerProfile() + ")");
        }
        for (var rule : updated.getRules()) {
            if (rule.conditions().size() > config.getMaxConditionsPerRule()) {
                throw new IllegalStateException("Maximum conditions reached (" + config.getMaxConditionsPerRule() + ")");
            }
        }
        repository.save(repository.getOrCreate(playerId).withUpdatedProfile(updated));
    }

    // ═══════════════════════════════════════════════════════════════════
    // PRESETS
    // ═══════════════════════════════════════════════════════════════════

    @Nonnull
    public List<String> getPresetNames() {
        return presetProfiles.stream().map(FilterProfile::getName).toList();
    }

    @Nonnull
    public Optional<FilterProfile> getPreset(@Nonnull String name) {
        return presetProfiles.stream()
                .filter(p -> p.getName().equalsIgnoreCase(name))
                .findFirst();
    }

    // ═══════════════════════════════════════════════════════════════════
    // CONFIG + MODIFIER CATEGORIES
    // ═══════════════════════════════════════════════════════════════════

    @Nonnull
    public LootFilterConfig getConfig() { return config; }

    public boolean isSystemEnabled() { return config.isEnabled(); }

    @Nonnull
    public Map<String, ModifierFilterCategory> getModifierCategoryMap() {
        return modifierCategoryMap;
    }

    @Nullable
    public ModifierFilterCategory getModifierCategory(@Nonnull String modifierId) {
        return modifierCategoryMap.get(modifierId);
    }

    /**
     * Builds the modifier → category map using stat-based auto-derivation
     * plus config overrides.
     */
    private Map<String, ModifierFilterCategory> buildModifierCategoryMap() {
        Map<String, ModifierFilterCategory> map = new LinkedHashMap<>();

        // Config overrides (takes priority — applied after auto-derivation would go here)
        for (var entry : config.getCategoryOverrides().entrySet()) {
            try {
                map.put(entry.getKey(), ModifierFilterCategory.valueOf(entry.getValue().toUpperCase()));
            } catch (IllegalArgumentException e) {
                LOGGER.atWarning().log("Invalid modifier category override: %s → %s",
                        entry.getKey(), entry.getValue());
            }
        }

        return map;
    }
}
