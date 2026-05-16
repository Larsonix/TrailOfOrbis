package io.github.larsonix.trailoforbis.combat.attackspeed.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Root config for {@code weapon-profiles.yml}.
 *
 * <p>SnakeYAML-compatible wrapper — maps to:
 * <pre>
 * profiles:
 *   sword:
 *     weaponTypes: [SWORD]
 *     ...
 *   dagger:
 *     weaponTypes: [DAGGER, CLAWS]
 *     ...
 * </pre>
 */
public class WeaponProfilesConfig {

    private Map<String, WeaponSpeedProfile> profiles = new LinkedHashMap<>();

    public Map<String, WeaponSpeedProfile> getProfiles() {
        return profiles;
    }

    public void setProfiles(Map<String, WeaponSpeedProfile> profiles) {
        this.profiles = profiles != null ? profiles : new LinkedHashMap<>();
    }

    /**
     * Creates a default config with all weapon profiles pre-populated.
     */
    public static WeaponProfilesConfig createDefaults() {
        WeaponProfilesConfig config = new WeaponProfilesConfig();
        config.profiles.put("default", WeaponSpeedProfile.createDefault());
        return config;
    }
}
