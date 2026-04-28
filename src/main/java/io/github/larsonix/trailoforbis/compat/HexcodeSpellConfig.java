package io.github.larsonix.trailoforbis.compat;

import com.hypixel.hytale.logger.HytaleLogger;
import io.github.larsonix.trailoforbis.combat.DamageType;
import io.github.larsonix.trailoforbis.elemental.ElementType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Config-driven mapping from Hexcode spell source IDs to RPG damage types,
 * elements, and display names.
 *
 * <p>When Hexcode spells deal damage via {@code EnvironmentSource("hex_xxx")},
 * this config determines:
 * <ul>
 *   <li>Which RPG {@link DamageType} the spell deals (for indicator colors)</li>
 *   <li>Which {@link ElementType} for resistance application and ailment triggers</li>
 *   <li>Display name for death recap</li>
 * </ul>
 *
 * <p>Designed for SnakeYAML deserialization from hexcode-spells.yml.
 */
public class HexcodeSpellConfig {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Prefix for Hexcode damage source IDs. */
    public static final String HEX_PREFIX = "hex_";

    private boolean enabled = true;

    /**
     * Maps source ID (e.g., "hex_bolt") to damage/element config.
     * The "default" key is used for unmapped hex_ sources.
     */
    private Map<String, SpellMapping> damage_type_map = new LinkedHashMap<>();

    /** Maps source ID to display name for death recap. */
    private Map<String, String> spell_display_names = new LinkedHashMap<>();

    /** Maximum resistance cap for spell damage (%). */
    private float max_resistance_cap = 75f;

    /** Realm pedestal placement config. */
    private HexcodePedestalPlacer.HexcodePedestalConfig realm_pedestal = new HexcodePedestalPlacer.HexcodePedestalConfig();

    /**
     * Maximum total damage amplification multiplier from combined Shock + Erode.
     * <p>
     * 2.0 = 200% = double damage max. Applied to the Shock conditional multiplier
     * in our pipeline. Note: Hexcode's Erode runs in a separate FilterDamageGroup
     * AFTER our system, so this cap limits our Shock contribution only. A true
     * combined cap would require a post-Erode DamageEventSystem (future work).
     */
    private float max_damage_amplification = 2.0f;

    public HexcodeSpellConfig() {
        initializeDefaults();
    }

    private void initializeDefaults() {
        if (damage_type_map.isEmpty()) {
            damage_type_map.put("hex_bolt", new SpellMapping("MAGIC", "LIGHTNING"));
            damage_type_map.put("hex_combust", new SpellMapping("FIRE", "FIRE"));
            damage_type_map.put("hex_gust", new SpellMapping("WIND", "WIND"));
            damage_type_map.put("hex_glaciate", new SpellMapping("WATER", "WATER"));
            damage_type_map.put("hex_ensnare", new SpellMapping("EARTH", "EARTH"));
            damage_type_map.put("hex_phase", new SpellMapping("MAGIC", "VOID"));
            damage_type_map.put("default", new SpellMapping("MAGIC", null));
        }

        if (spell_display_names.isEmpty()) {
            spell_display_names.put("hex_bolt", "Lightning Bolt");
            spell_display_names.put("hex_combust", "Combustion");
            spell_display_names.put("hex_gust", "Gust");
            spell_display_names.put("hex_glaciate", "Glaciate");
            spell_display_names.put("hex_ensnare", "Ensnare");
            spell_display_names.put("hex_phase", "Phase Crush");
            spell_display_names.put("default", "Hex Spell");
        }
    }

    /**
     * Checks if the given damage source ID is a Hexcode spell.
     *
     * @param sourceType The source type from {@code EnvironmentSource.getType()}
     * @return true if the source starts with "hex_"
     */
    public static boolean isHexSpellSource(@Nullable String sourceType) {
        return sourceType != null && sourceType.startsWith(HEX_PREFIX);
    }

    /**
     * Gets the RPG damage type for a spell source ID.
     *
     * @param sourceId The source ID (e.g., "hex_bolt")
     * @return The mapped DamageType, or MAGIC if unmapped
     */
    @Nonnull
    public DamageType getDamageType(@Nonnull String sourceId) {
        SpellMapping mapping = damage_type_map.get(sourceId);
        if (mapping == null) {
            mapping = damage_type_map.get("default");
        }
        if (mapping == null || mapping.type == null) {
            return DamageType.MAGIC;
        }
        try {
            return DamageType.valueOf(mapping.type.toUpperCase());
        } catch (IllegalArgumentException e) {
            LOGGER.atWarning().log("Invalid damage type '%s' for spell '%s', defaulting to MAGIC",
                mapping.type, sourceId);
            return DamageType.MAGIC;
        }
    }

    /**
     * Gets the element type for a spell source ID (for resistance and ailments).
     *
     * @param sourceId The source ID (e.g., "hex_bolt")
     * @return The mapped ElementType, or null if no element
     */
    @Nullable
    public ElementType getElement(@Nonnull String sourceId) {
        SpellMapping mapping = damage_type_map.get(sourceId);
        if (mapping == null) {
            mapping = damage_type_map.get("default");
        }
        if (mapping == null || mapping.element == null) {
            return null;
        }
        try {
            return ElementType.valueOf(mapping.element.toUpperCase());
        } catch (IllegalArgumentException e) {
            LOGGER.atWarning().log("Invalid element '%s' for spell '%s', defaulting to null",
                mapping.element, sourceId);
            return null;
        }
    }

    /**
     * Gets the display name for a spell source ID (for death recap).
     *
     * @param sourceId The source ID (e.g., "hex_bolt")
     * @return The display name, or "Hex Spell" if unmapped
     */
    @Nonnull
    public String getDisplayName(@Nonnull String sourceId) {
        String name = spell_display_names.get(sourceId);
        if (name != null) {
            return name;
        }
        name = spell_display_names.get("default");
        return name != null ? name : "Hex Spell";
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public float getMax_resistance_cap() {
        return max_resistance_cap;
    }

    public void setMax_resistance_cap(float max_resistance_cap) {
        this.max_resistance_cap = max_resistance_cap;
    }

    public float getMax_damage_amplification() {
        return max_damage_amplification;
    }

    public void setMax_damage_amplification(float max_damage_amplification) {
        this.max_damage_amplification = max_damage_amplification;
    }

    public HexcodePedestalPlacer.HexcodePedestalConfig getRealm_pedestal() {
        return realm_pedestal;
    }

    public void setRealm_pedestal(HexcodePedestalPlacer.HexcodePedestalConfig realm_pedestal) {
        this.realm_pedestal = realm_pedestal;
    }

    // YAML getters/setters for SnakeYAML
    public Map<String, SpellMapping> getDamage_type_map() {
        return damage_type_map;
    }

    public void setDamage_type_map(Map<String, SpellMapping> damage_type_map) {
        this.damage_type_map = damage_type_map;
    }

    public Map<String, String> getSpell_display_names() {
        return spell_display_names;
    }

    public void setSpell_display_names(Map<String, String> spell_display_names) {
        this.spell_display_names = spell_display_names;
    }

    /**
     * Validates the configuration.
     */
    public void validate() {
        if (max_resistance_cap < 0 || max_resistance_cap > 100) {
            LOGGER.atWarning().log("Hexcode spell config: max_resistance_cap %.1f out of range [0,100], clamping",
                max_resistance_cap);
            max_resistance_cap = Math.max(0, Math.min(100, max_resistance_cap));
        }

        if (max_damage_amplification < 1.0f) {
            LOGGER.atWarning().log("Hexcode spell config: max_damage_amplification %.2f below 1.0, clamping to 1.0",
                max_damage_amplification);
            max_damage_amplification = 1.0f;
        }

        for (Map.Entry<String, SpellMapping> entry : damage_type_map.entrySet()) {
            SpellMapping mapping = entry.getValue();
            if (mapping.type != null) {
                try {
                    DamageType.valueOf(mapping.type.toUpperCase());
                } catch (IllegalArgumentException e) {
                    LOGGER.atWarning().log("Hexcode spell config: invalid type '%s' for '%s'",
                        mapping.type, entry.getKey());
                }
            }
            if (mapping.element != null) {
                try {
                    ElementType.valueOf(mapping.element.toUpperCase());
                } catch (IllegalArgumentException e) {
                    LOGGER.atWarning().log("Hexcode spell config: invalid element '%s' for '%s'",
                        mapping.element, entry.getKey());
                }
            }
        }
    }

    /**
     * Maps a spell source ID to a damage type and element.
     * Designed for SnakeYAML deserialization.
     */
    public static class SpellMapping {
        private String type;
        private String element;

        public SpellMapping() {}

        public SpellMapping(String type, String element) {
            this.type = type;
            this.element = element;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getElement() {
            return element;
        }

        public void setElement(String element) {
            this.element = element;
        }

        @Override
        public String toString() {
            return "SpellMapping{type=" + type + ", element=" + element + "}";
        }
    }
}
