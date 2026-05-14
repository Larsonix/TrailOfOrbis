package io.github.larsonix.trailoforbis.compat;

import com.hypixel.hytale.logger.HytaleLogger;
import io.github.larsonix.trailoforbis.combat.DamageType;
import io.github.larsonix.trailoforbis.elemental.ElementType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    // spell_base_multiplier and glyph_damage_multipliers REMOVED.
    // RPG casting power (weapon + flat spell) is now injected directly into Hexcode's
    // setPowerMultiplier() pre-cast. Hexcode's glyph system handles differentiation.
    // Flat elemental is added per-glyph-element in our pipeline Step 2.

    /** Maximum mana cost reduction from stats (%). */
    private float max_mana_cost_reduction = 90f;

    /** Spell echo cooldown in milliseconds. */
    private long echo_cooldown_ms = 5000L;

    /** Echo damage as % of original. */
    private float echo_damage_percent = 100f;

    /**
     * Construct damage source IDs — these skip Tier 1 (ThreadLocal) attribution
     * and go straight to Tier 2 (construct registry proximity lookup).
     */
    private List<String> construct_sources = List.of("hex_ensnare", "hex_glaciate", "hex_phase");

    /**
     * Exponent for the volatility ratio damage multiplier.
     * Formula: damageMult = (remainingVolatility / startingVolatility) ^ exponent
     *
     * Higher = more aggressive diminishing returns for multi-hit chains.
     * 1.0 = linear (10% remaining = 10% damage)
     * 1.5 = moderate (10% remaining = 3.2% damage)
     * 2.0 = aggressive (10% remaining = 1% damage)
     */
    private float volatility_ratio_exponent = 1.5f;

    /**
     * Enable within-cast volatility damage scaling.
     * When true, each glyph's damage is scaled by remainingBudget/startingBudget
     * at the moment of execution — later glyphs in a chain deal less damage.
     */
    private boolean within_cast_volatility_scaling = true;

    /**
     * Exponent for within-cast volatility scaling.
     * 1.0 = linear (50% remaining = 50% damage). Default.
     * Values below 1.0 are more generous, above 1.0 more aggressive.
     */
    private float within_cast_exponent = 1.0f;

    /**
     * Per-construct physics config (LEGACY — superseded by spell_balance profiles).
     * Kept for backward compatibility; values are read into SpellBalanceProfile.physics_gravity.
     */
    private Map<String, ConstructPhysics> construct_physics = new LinkedHashMap<>();

    /** Maximum damage multiplier from construct height/slot inflation (LEGACY). */
    private float max_construct_multiplier = 5.0f;

    /**
     * Per-spell balance profiles. Each hex damage source gets its own normalization
     * strategy, power scale, multiplier cap, and cost scaling factor.
     * The "default" key is used for unknown hex_ sources.
     */
    private Map<String, SpellBalanceConfig> spell_balance = new LinkedHashMap<>();

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

        // glyph_damage_multipliers removed — Hexcode's glyph system handles differentiation

        if (construct_physics.isEmpty()) {
            construct_physics.put("hex_glaciate", new ConstructPhysics(20.0f));
            construct_physics.put("hex_ensnare", new ConstructPhysics(0f));
            construct_physics.put("hex_phase", new ConstructPhysics(0f));
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

    public float getMax_mana_cost_reduction() {
        return max_mana_cost_reduction;
    }

    public void setMax_mana_cost_reduction(float max_mana_cost_reduction) {
        this.max_mana_cost_reduction = max_mana_cost_reduction;
    }

    public long getEcho_cooldown_ms() {
        return echo_cooldown_ms;
    }

    public void setEcho_cooldown_ms(long echo_cooldown_ms) {
        this.echo_cooldown_ms = echo_cooldown_ms;
    }

    public float getEcho_damage_percent() {
        return echo_damage_percent;
    }

    public void setEcho_damage_percent(float echo_damage_percent) {
        this.echo_damage_percent = echo_damage_percent;
    }

    /** Returns the construct damage source IDs as an unmodifiable set. */
    @Nonnull
    public Set<String> getConstructSources() {
        return construct_sources != null ? Set.copyOf(construct_sources) : Set.of();
    }

    public List<String> getConstruct_sources() { return construct_sources; }
    public void setConstruct_sources(List<String> sources) { this.construct_sources = sources; }

    /** Returns the volatility ratio exponent for damage scaling. */
    public float getVolatilityRatioExponent() { return volatility_ratio_exponent; }

    public float getVolatility_ratio_exponent() { return volatility_ratio_exponent; }
    public void setVolatility_ratio_exponent(float v) { this.volatility_ratio_exponent = v; }

    /** Whether within-cast volatility scaling is enabled. */
    public boolean isWithinCastVolatilityScaling() { return within_cast_volatility_scaling; }

    /** Within-cast exponent (1.0 = linear). */
    public float getWithinCastExponent() { return within_cast_exponent; }

    // YAML getters/setters for within-cast config
    public boolean isWithin_cast_volatility_scaling() { return within_cast_volatility_scaling; }
    public void setWithin_cast_volatility_scaling(boolean v) { this.within_cast_volatility_scaling = v; }
    public float getWithin_cast_exponent() { return within_cast_exponent; }
    public void setWithin_cast_exponent(float v) { this.within_cast_exponent = v; }

    /**
     * Returns the gravity value for a construct spell source, used to reverse
     * velocity→height for proper damage scaling. Returns 0 if the source is
     * not a velocity-based construct (e.g., Ensnare uses direct damage slots).
     */
    public float getConstructGravity(@Nullable String sourceType) {
        if (sourceType == null || construct_physics == null) return 0f;
        ConstructPhysics physics = construct_physics.get(sourceType);
        return physics != null ? physics.gravity : 0f;
    }

    /** Maximum damage multiplier from construct height/slot inflation. */
    public float getMaxConstructMultiplier() { return max_construct_multiplier; }

    // YAML getters/setters for construct physics
    public Map<String, ConstructPhysics> getConstruct_physics() { return construct_physics; }
    public void setConstruct_physics(Map<String, ConstructPhysics> cp) { this.construct_physics = cp; }
    public float getMax_construct_multiplier() { return max_construct_multiplier; }
    public void setMax_construct_multiplier(float m) { this.max_construct_multiplier = m; }

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

        if (max_mana_cost_reduction < 0 || max_mana_cost_reduction > 100) {
            LOGGER.atWarning().log("Hexcode spell config: max_mana_cost_reduction %.1f out of range [0,100], clamping",
                max_mana_cost_reduction);
            max_mana_cost_reduction = Math.max(0, Math.min(100, max_mana_cost_reduction));
        }

        if (echo_cooldown_ms < 0) {
            echo_cooldown_ms = 0;
        }

        if (echo_damage_percent < 0) {
            echo_damage_percent = 0;
        }

        if (within_cast_exponent < 0) {
            LOGGER.atWarning().log("Hexcode spell config: within_cast_exponent %.2f below 0, clamping to 0.1",
                within_cast_exponent);
            within_cast_exponent = 0.1f;
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

    /**
     * Physics config for a construct spell, used to reverse velocity→height
     * for linear damage scaling. Only velocity-based constructs (Glaciate)
     * need a non-zero gravity value.
     */
    public static class ConstructPhysics {
        private float gravity = 0f;

        public ConstructPhysics() {}

        public ConstructPhysics(float gravity) {
            this.gravity = gravity;
        }

        public float getGravity() { return gravity; }
        public void setGravity(float gravity) { this.gravity = gravity; }
    }

    // ── Spell Balance Profiles ──

    /**
     * YAML-deserializable spell balance config entry.
     * Each spell type gets one of these in the spell_balance map.
     */
    public static class SpellBalanceConfig {
        private float power_scale = 0.70f;
        private float multiplier_cap = 3.0f;
        private float cost_scale = 1.0f;
        private boolean strips_magic_power = true;
        private String normalization = "linear";
        private float physics_gravity = 0f;

        public SpellBalanceConfig() {}

        public float getPower_scale() { return power_scale; }
        public void setPower_scale(float v) { this.power_scale = v; }
        public float getMultiplier_cap() { return multiplier_cap; }
        public void setMultiplier_cap(float v) { this.multiplier_cap = v; }
        public float getCost_scale() { return cost_scale; }
        public void setCost_scale(float v) { this.cost_scale = v; }
        public boolean isStrips_magic_power() { return strips_magic_power; }
        public void setStrips_magic_power(boolean v) { this.strips_magic_power = v; }
        public String getNormalization() { return normalization; }
        public void setNormalization(String v) { this.normalization = v; }
        public float getPhysics_gravity() { return physics_gravity; }
        public void setPhysics_gravity(float v) { this.physics_gravity = v; }
    }

    /**
     * Immutable balance profile used by {@link HexSpellNormalizer}.
     * Built from {@link SpellBalanceConfig} + runtime slot default from glyph cache.
     */
    public record SpellBalanceProfile(
            float power_scale,
            float multiplier_cap,
            float cost_scale,
            boolean strips_magic_power,
            HexSpellNormalizer.NormalizationType normalization,
            float physics_gravity,
            float slot_default
    ) {}

    /** Default fallback profile. */
    private static final SpellBalanceProfile DEFAULT_PROFILE = new SpellBalanceProfile(
            0.70f, 3.0f, 1.0f, true, HexSpellNormalizer.NormalizationType.LINEAR, 0f, 1f);

    /**
     * Returns the balance profile for a given hex spell source type.
     * Resolves from config spell_balance map, falls back to "default" entry,
     * then to hardcoded DEFAULT_PROFILE.
     *
     * @param sourceType The hex damage source ID (e.g., "hex_bolt")
     * @param slotDefault The runtime slot default from glyph asset cache (0 if unknown)
     */
    @Nonnull
    public SpellBalanceProfile getSpellBalance(@Nonnull String sourceType, float slotDefault) {
        SpellBalanceConfig cfg = spell_balance.get(sourceType);
        if (cfg == null) {
            cfg = spell_balance.get("default");
        }
        if (cfg == null) {
            return new SpellBalanceProfile(
                    DEFAULT_PROFILE.power_scale(), DEFAULT_PROFILE.multiplier_cap(),
                    DEFAULT_PROFILE.cost_scale(), DEFAULT_PROFILE.strips_magic_power(),
                    DEFAULT_PROFILE.normalization(), DEFAULT_PROFILE.physics_gravity(),
                    Math.max(1f, slotDefault));
        }

        HexSpellNormalizer.NormalizationType normType;
        try {
            normType = HexSpellNormalizer.NormalizationType.valueOf(cfg.normalization.toUpperCase());
        } catch (IllegalArgumentException e) {
            LOGGER.atWarning().log("Invalid normalization type '%s' for '%s', defaulting to LINEAR",
                    cfg.normalization, sourceType);
            normType = HexSpellNormalizer.NormalizationType.LINEAR;
        }

        return new SpellBalanceProfile(
                cfg.power_scale, cfg.multiplier_cap, cfg.cost_scale,
                cfg.strips_magic_power, normType, cfg.physics_gravity,
                Math.max(1f, slotDefault));
    }

    // YAML getters/setters for spell_balance
    public Map<String, SpellBalanceConfig> getSpell_balance() { return spell_balance; }
    public void setSpell_balance(Map<String, SpellBalanceConfig> v) { this.spell_balance = v; }
}
