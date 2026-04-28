package io.github.larsonix.trailoforbis.gear.config;

import io.github.larsonix.trailoforbis.gear.model.EquipmentType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Configuration for equipment-based stat restrictions.
 *
 * <p>Defines which modifiers are allowed on each equipment type:
 * <ul>
 *   <li><b>Weapons</b>: Offensive prefixes, weapon-specific suffixes</li>
 *   <li><b>Armor</b>: Material-based suffixes, slot-specific bonuses</li>
 *   <li><b>Shields</b>: Defensive suffixes only</li>
 * </ul>
 *
 * <p>Loaded from {@code equipment-stats.yml}.
 *
 * @see EquipmentType
 */
public final class EquipmentStatConfig {

    private final Map<EquipmentType, EquipmentModifierProfile> profiles;

    EquipmentStatConfig(@Nonnull Map<EquipmentType, EquipmentModifierProfile> profiles) {
        // EnumMap constructor throws if the source map is empty, so handle that case
        if (profiles.isEmpty()) {
            this.profiles = new EnumMap<>(EquipmentType.class);
        } else {
            this.profiles = new EnumMap<>(profiles);
        }
    }

    /**
     * Gets the modifier profile for an equipment type.
     *
     * @return The modifier profile, or an empty profile if not configured
     */
    @Nonnull
    public EquipmentModifierProfile getProfile(@Nullable EquipmentType equipmentType) {
        if (equipmentType == null) {
            return EquipmentModifierProfile.UNRESTRICTED;
        }
        return profiles.getOrDefault(equipmentType, EquipmentModifierProfile.UNRESTRICTED);
    }

    /**
     * Gets the allowed prefix IDs for an equipment type.
     *
     * @return Set of allowed prefix IDs (empty = all allowed)
     */
    @Nonnull
    public Set<String> getAllowedPrefixes(@Nullable EquipmentType equipmentType) {
        return getProfile(equipmentType).allowedPrefixes();
    }

    /**
     * Gets the allowed suffix IDs for an equipment type.
     *
     * @return Set of allowed suffix IDs (empty = all allowed)
     */
    @Nonnull
    public Set<String> getAllowedSuffixes(@Nullable EquipmentType equipmentType) {
        return getProfile(equipmentType).allowedSuffixes();
    }

    /**
     * Checks if a prefix is allowed on an equipment type.
     *
     * @return true if allowed (or if no restrictions configured)
     */
    public boolean isPrefixAllowed(@Nullable EquipmentType equipmentType, @Nonnull String prefixId) {
        EquipmentModifierProfile profile = getProfile(equipmentType);
        if (!profile.hasPrefixRestrictions()) {
            return true; // No restrictions = all allowed
        }
        return profile.allowedPrefixes().contains(prefixId.toLowerCase());
    }

    /**
     * Checks if a suffix is allowed on an equipment type.
     *
     * @return true if allowed (or if no restrictions configured)
     */
    public boolean isSuffixAllowed(@Nullable EquipmentType equipmentType, @Nonnull String suffixId) {
        EquipmentModifierProfile profile = getProfile(equipmentType);
        if (!profile.hasSuffixRestrictions()) {
            return true; // No restrictions = all allowed
        }
        return profile.allowedSuffixes().contains(suffixId.toLowerCase());
    }

    public int size() {
        return profiles.size();
    }

    /**
     * Modifier profile for a single equipment type.
     *
     * @param allowedPrefixes empty = no restrictions
     * @param allowedSuffixes empty = no restrictions
     */
    public record EquipmentModifierProfile(
            @Nonnull Set<String> allowedPrefixes,
            @Nonnull Set<String> allowedSuffixes
    ) {
        /** Profile with no restrictions (all modifiers allowed). */
        public static final EquipmentModifierProfile UNRESTRICTED =
                new EquipmentModifierProfile(Set.of(), Set.of());

        public EquipmentModifierProfile {
            allowedPrefixes = Set.copyOf(allowedPrefixes);
            allowedSuffixes = Set.copyOf(allowedSuffixes);
        }

        public boolean hasPrefixRestrictions() {
            return !allowedPrefixes.isEmpty();
        }

        public boolean hasSuffixRestrictions() {
            return !allowedSuffixes.isEmpty();
        }

        public boolean hasRestrictions() {
            return hasPrefixRestrictions() || hasSuffixRestrictions();
        }
    }

    public static class Builder {
        private final Map<EquipmentType, EquipmentModifierProfile> profiles = new EnumMap<>(EquipmentType.class);

        public Builder addProfile(EquipmentType type, EquipmentModifierProfile profile) {
            profiles.put(type, profile);
            return this;
        }

        public Builder addProfile(
                EquipmentType type,
                Set<String> allowedPrefixes,
                Set<String> allowedSuffixes
        ) {
            return addProfile(type, new EquipmentModifierProfile(allowedPrefixes, allowedSuffixes));
        }

        public EquipmentStatConfig build() {
            return new EquipmentStatConfig(profiles);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a config with no restrictions (all modifiers allowed everywhere).
     */
    public static EquipmentStatConfig unrestricted() {
        return new EquipmentStatConfig(Collections.emptyMap());
    }
}
