package io.github.larsonix.trailoforbis.combat.indicators.color;

import com.hypixel.hytale.protocol.Color;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration for the colored combat text system.
 *
 * <p>Loaded from {@code combat-text.yml} via manual parsing because the nested
 * element/avoidance profile structure doesn't map cleanly to SnakeYAML POJOs.
 *
 * <p>Profiles are hierarchical: element profiles inherit from {@code defaults},
 * and individual variants (normal/crit) can override specific fields.
 */
public class CombatTextColorConfig {

    private boolean enabled;
    private ProfileSettings defaults;
    private Map<String, CombatTextProfile> profiles;

    private CombatTextColorConfig(
        boolean enabled,
        @Nonnull ProfileSettings defaults,
        @Nonnull Map<String, CombatTextProfile> profiles
    ) {
        this.enabled = enabled;
        this.defaults = defaults;
        this.profiles = profiles;
    }

    /**
     * Parses configuration from a raw YAML map.
     *
     * @param yaml The parsed YAML as a generic map (may be null for empty file)
     * @return Parsed config, or a disabled default if input is null/invalid
     */
    @Nonnull
    @SuppressWarnings("unchecked")
    public static CombatTextColorConfig fromYaml(@Nullable Map<String, Object> yaml) {
        if (yaml == null) {
            return createDefault();
        }

        boolean enabled = getBoolean(yaml, "enabled", true);

        // Parse defaults
        Map<String, Object> defaultsYaml = getMap(yaml, "defaults");
        ProfileSettings defaults = parseProfileSettings(defaultsYaml, HARDCODED_DEFAULTS);

        // Parse element profiles
        Map<String, CombatTextProfile> profiles = new LinkedHashMap<>();
        Map<String, Object> elementsYaml = getMap(yaml, "elements");
        if (elementsYaml != null) {
            for (var entry : elementsYaml.entrySet()) {
                String element = entry.getKey();
                Map<String, Object> elementYaml = asMap(entry.getValue());
                if (elementYaml == null) continue;

                // Parse normal variant
                Map<String, Object> normalYaml = getMap(elementYaml, "normal");
                if (normalYaml != null) {
                    String id = element + "_normal";
                    profiles.put(id, buildProfile(id, normalYaml, defaults));
                }

                // Parse crit variant
                Map<String, Object> critYaml = getMap(elementYaml, "crit");
                if (critYaml != null) {
                    String id = element + "_crit";
                    profiles.put(id, buildProfile(id, critYaml, defaults));
                }
            }
        }

        // Parse avoidance profiles
        Map<String, Object> avoidanceYaml = getMap(yaml, "avoidance");
        if (avoidanceYaml != null) {
            for (var entry : avoidanceYaml.entrySet()) {
                String type = entry.getKey(); // dodged, blocked, parried, missed
                Map<String, Object> typeYaml = asMap(entry.getValue());
                if (typeYaml != null) {
                    profiles.put(type, buildProfile(type, typeYaml, defaults));
                }
            }
        }

        return new CombatTextColorConfig(enabled, defaults, profiles);
    }

    /** Creates a default (disabled) configuration. */
    @Nonnull
    public static CombatTextColorConfig createDefault() {
        return new CombatTextColorConfig(false, HARDCODED_DEFAULTS, Map.of());
    }

    // ── Accessors ────────────────────────────────────────────────────────

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Gets all parsed profiles keyed by ID.
     * Keys: "fire_normal", "fire_crit", "physical_normal", "dodged", etc.
     */
    @Nonnull
    public Map<String, CombatTextProfile> getProfiles() {
        return profiles;
    }

    @Nonnull
    public ProfileSettings getDefaults() {
        return defaults;
    }

    // ── Internal parsing ─────────────────────────────────────────────────

    private static CombatTextProfile buildProfile(
        @Nonnull String id,
        @Nonnull Map<String, Object> yaml,
        @Nonnull ProfileSettings defaults
    ) {
        ProfileSettings settings = parseProfileSettings(yaml, defaults);
        return CombatTextProfile.unregistered(
            id,
            settings.color,
            settings.fontSize,
            settings.duration,
            settings.hitAngleStrength,
            settings.animations
        );
    }

    private static ProfileSettings parseProfileSettings(
        @Nullable Map<String, Object> yaml,
        @Nonnull ProfileSettings fallback
    ) {
        if (yaml == null) return fallback;

        String colorHex = getString(yaml, "color", null);
        Color color = colorHex != null ? parseColor(colorHex) : fallback.color;
        float fontSize = getFloat(yaml, "fontSize", fallback.fontSize);
        float duration = getFloat(yaml, "duration", fallback.duration);
        float hitAngleStrength = getFloat(yaml, "hitAngleStrength", fallback.hitAngleStrength);

        // Parse animations if present, otherwise inherit from fallback
        CombatTextAnimation[] animations = fallback.animations;
        List<Object> animList = getList(yaml, "animations");
        if (animList != null) {
            animations = parseAnimations(animList);
        }

        return new ProfileSettings(color, fontSize, duration, hitAngleStrength, animations);
    }

    private static CombatTextAnimation[] parseAnimations(@Nonnull List<Object> list) {
        List<CombatTextAnimation> result = new ArrayList<>();
        for (Object item : list) {
            Map<String, Object> anim = asMap(item);
            if (anim == null) continue;

            String typeStr = getString(anim, "type", "SCALE");
            CombatTextAnimation.AnimationType type;
            try {
                type = CombatTextAnimation.AnimationType.valueOf(typeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                continue; // Skip invalid animation types
            }

            float startAt = getFloat(anim, "startAt", 0f);
            float endAt = getFloat(anim, "endAt", 1f);
            float startScale = getFloat(anim, "startScale", 1f);
            float endScale = getFloat(anim, "endScale", 0.5f);
            float offsetX = getFloat(anim, "positionOffsetX", 0f);
            float offsetY = getFloat(anim, "positionOffsetY", 0f);
            float startOpacity = getFloat(anim, "startOpacity", 1f);
            float endOpacity = getFloat(anim, "endOpacity", 0f);

            result.add(new CombatTextAnimation(type, startAt, endAt, startScale, endScale, offsetX, offsetY, startOpacity, endOpacity));
        }
        return result.toArray(CombatTextAnimation[]::new);
    }

    @Nonnull
    static Color parseColor(@Nonnull String hex) {
        hex = hex.startsWith("#") ? hex.substring(1) : hex;
        if (hex.length() != 6) {
            return new Color((byte) 0xFF, (byte) 0xFF, (byte) 0xFF); // white fallback
        }
        try {
            int r = Integer.parseInt(hex.substring(0, 2), 16);
            int g = Integer.parseInt(hex.substring(2, 4), 16);
            int b = Integer.parseInt(hex.substring(4, 6), 16);
            return new Color((byte) r, (byte) g, (byte) b);
        } catch (NumberFormatException e) {
            return new Color((byte) 0xFF, (byte) 0xFF, (byte) 0xFF); // white fallback
        }
    }

    // ── YAML helpers ─────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    @Nullable
    private static Map<String, Object> getMap(@Nonnull Map<String, Object> yaml, @Nonnull String key) {
        Object val = yaml.get(key);
        return val instanceof Map ? (Map<String, Object>) val : null;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static Map<String, Object> asMap(@Nullable Object val) {
        return val instanceof Map ? (Map<String, Object>) val : null;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static List<Object> getList(@Nonnull Map<String, Object> yaml, @Nonnull String key) {
        Object val = yaml.get(key);
        return val instanceof List ? (List<Object>) val : null;
    }

    @Nullable
    private static String getString(@Nonnull Map<String, Object> yaml, @Nonnull String key, @Nullable String defaultValue) {
        Object val = yaml.get(key);
        return val instanceof String s ? s : defaultValue;
    }

    private static boolean getBoolean(@Nonnull Map<String, Object> yaml, @Nonnull String key, boolean defaultValue) {
        Object val = yaml.get(key);
        return val instanceof Boolean b ? b : defaultValue;
    }

    private static float getFloat(@Nonnull Map<String, Object> yaml, @Nonnull String key, float defaultValue) {
        Object val = yaml.get(key);
        if (val instanceof Number n) return n.floatValue();
        return defaultValue;
    }

    // ── Internal types ───────────────────────────────────────────────────

    /**
     * Intermediate settings holder used during parsing for default inheritance.
     */
    record ProfileSettings(
        @Nonnull Color color,
        float fontSize,
        float duration,
        float hitAngleStrength,
        @Nonnull CombatTextAnimation[] animations
    ) {}

    /** Hardcoded defaults matching vanilla CombatText behavior. */
    static final ProfileSettings HARDCODED_DEFAULTS = new ProfileSettings(
        new Color((byte) 0xFF, (byte) 0xFF, (byte) 0xFF), // white
        68.0f,
        0.4f,
        2.0f,
        new CombatTextAnimation[] {
            CombatTextAnimation.scale(0.0f, 1.0f, 1.0f, 0.5f),
            CombatTextAnimation.position(0.0f, 1.0f, 0f, -80f),
            CombatTextAnimation.opacity(0.5f, 1.0f, 1.0f, 0.0f)
        }
    );
}
