package io.github.larsonix.trailoforbis.combat.attackspeed.config;

import com.hypixel.hytale.logger.HytaleLogger;
import io.github.larsonix.trailoforbis.gear.model.WeaponType;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * Registry mapping {@link WeaponType} to {@link WeaponSpeedProfile}.
 *
 * <p>Loaded from {@code weapon-profiles.yml} during plugin initialization.
 * Profiles define how each weapon type responds to attack speed stats —
 * daggers get more benefit from raw speed, battleaxes from cooldown recovery, etc.
 *
 * <p>Also provides animation-set-based lookup for the {@code AnimationSpeedSyncManager},
 * which needs to resolve profiles by the held weapon's animation set ID (e.g. "Sword").
 */
public class WeaponProfileRegistry {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final Map<WeaponType, WeaponSpeedProfile> profilesByWeaponType;
    private final Map<String, WeaponSpeedProfile> profilesByAnimationSet;
    private final WeaponSpeedProfile defaultProfile;

    public WeaponProfileRegistry(@Nonnull WeaponProfilesConfig config) {
        this.profilesByWeaponType = new EnumMap<>(WeaponType.class);
        this.profilesByAnimationSet = new HashMap<>();

        // Extract or create the default profile
        WeaponSpeedProfile configDefault = null;

        for (Map.Entry<String, WeaponSpeedProfile> entry : config.getProfiles().entrySet()) {
            String profileId = entry.getKey();
            WeaponSpeedProfile profile = entry.getValue();

            if ("default".equalsIgnoreCase(profileId)) {
                configDefault = profile;
            }

            // Map each declared weapon type to this profile
            for (String typeName : profile.getWeaponTypes()) {
                try {
                    WeaponType type = WeaponType.valueOf(typeName.toUpperCase());
                    profilesByWeaponType.put(type, profile);
                } catch (IllegalArgumentException e) {
                    LOGGER.atWarning().log(
                            "Unknown weapon type '%s' in profile '%s' — skipped", typeName, profileId);
                }
            }

            // Map each declared animation set to this profile
            for (String animSetId : profile.getAnimationSets()) {
                profilesByAnimationSet.put(animSetId, profile);
            }
        }

        this.defaultProfile = configDefault != null ? configDefault : WeaponSpeedProfile.createDefault();

        LOGGER.atInfo().log("Weapon profile registry loaded: %d weapon types, %d animation sets, %d profiles",
                profilesByWeaponType.size(), profilesByAnimationSet.size(), config.getProfiles().size());
    }

    /**
     * Gets the speed profile for a weapon type.
     *
     * @param type The weapon type (from held item)
     * @return The matching profile, or the default profile if none configured
     */
    @Nonnull
    public WeaponSpeedProfile getProfile(@Nonnull WeaponType type) {
        return profilesByWeaponType.getOrDefault(type, defaultProfile);
    }

    /**
     * Gets the speed profile by animation set ID (e.g. "Sword", "Longsword").
     *
     * <p>Used by {@code AnimationSpeedSyncManager} which resolves weapons by
     * their animation set rather than item ID.
     *
     * @param animSetId The animation set ID
     * @return The matching profile, or the default profile if none configured
     */
    @Nonnull
    public WeaponSpeedProfile getProfileByAnimationSet(@Nonnull String animSetId) {
        return profilesByAnimationSet.getOrDefault(animSetId, defaultProfile);
    }

    /**
     * @return The default profile for unrecognized weapons
     */
    @Nonnull
    public WeaponSpeedProfile getDefaultProfile() {
        return defaultProfile;
    }
}
