package io.github.larsonix.trailoforbis.mobs.profile;

import io.github.larsonix.trailoforbis.elemental.ElementType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

/**
 * Configuration for mob resistance profiles.
 *
 * <p>Loaded from {@code mob-resistances.yml}. Provides three layers of
 * resistance resolution:
 * <ol>
 *   <li>Element-based profiles (universal default)</li>
 *   <li>Faction profiles (optional overlay for known factions)</li>
 *   <li>Per-role overrides (highest priority)</li>
 * </ol>
 *
 * @see ResistanceProfileResolver
 */
public class MobResistanceConfig {

    // Layer 1: Element → resistance profile
    private Map<String, ElementProfileEntry> element_profiles = new LinkedHashMap<>();

    // Layer 2: Faction → resistance profile with detection rules
    private Map<String, FactionProfileEntry> faction_profiles = new LinkedHashMap<>();

    // Layer 3: Role name → explicit resistance overrides
    private Map<String, Map<String, Object>> overrides = new LinkedHashMap<>();

    // Global settings
    private SettingsEntry settings = new SettingsEntry();

    // ==================== YAML Setters ====================

    public void setElement_profiles(Map<String, Map<String, Object>> raw) {
        if (raw == null) return;
        element_profiles = new LinkedHashMap<>();
        for (var entry : raw.entrySet()) {
            element_profiles.put(entry.getKey().toUpperCase(), ElementProfileEntry.fromMap(entry.getValue()));
        }
    }

    public void setFaction_profiles(Map<String, Map<String, Object>> raw) {
        if (raw == null) return;
        faction_profiles = new LinkedHashMap<>();
        for (var entry : raw.entrySet()) {
            faction_profiles.put(entry.getKey().toLowerCase(), FactionProfileEntry.fromMap(entry.getValue()));
        }
    }

    public void setOverrides(Map<String, Map<String, Object>> overrides) {
        this.overrides = overrides != null ? overrides : new LinkedHashMap<>();
    }

    public void setSettings(SettingsEntry settings) {
        if (settings != null) {
            this.settings = settings;
        }
    }

    // ==================== Accessors ====================

    /**
     * Gets the element-based resistance profile for a detected element.
     *
     * @param element The detected element (or null for PHYSICAL default)
     * @return The profile, or null if not configured
     */
    @Nullable
    public ResistanceProfile getElementProfile(@Nullable ElementType element) {
        String key = element != null ? element.name() : "PHYSICAL";
        ElementProfileEntry entry = element_profiles.get(key);
        return entry != null ? entry.toProfile() : null;
    }

    /**
     * Tries to match a faction from NPC groups and/or role name keywords.
     *
     * @param roleName  The mob's role name (nullable)
     * @param npcGroups The mob's NPC groups
     * @return The faction profile, or null if no faction matched
     */
    @Nullable
    public ResistanceProfile getFactionProfile(@Nullable String roleName, @Nonnull Set<String> npcGroups) {
        for (var entry : faction_profiles.entrySet()) {
            FactionProfileEntry faction = entry.getValue();
            if (faction.matches(roleName, npcGroups)) {
                return faction.toProfile();
            }
        }
        return null;
    }

    /**
     * Gets a per-role override profile.
     *
     * @param roleName The mob's role name
     * @return The override profile, or null if not overridden
     */
    @Nullable
    public ResistanceProfile getOverrideProfile(@Nullable String roleName) {
        if (roleName == null) return null;
        Map<String, Object> raw = overrides.get(roleName.toLowerCase());
        if (raw == null) return null;
        return parseResistanceMap(raw, Collections.emptyMap(), false);
    }

    @Nonnull
    public SettingsEntry getSettings() {
        return settings;
    }

    // ==================== Factory ====================

    @Nonnull
    public static MobResistanceConfig createDefaults() {
        return new MobResistanceConfig();
    }

    // ==================== Parsing Helpers ====================

    @Nonnull
    private static ResistanceProfile parseResistanceMap(
            @Nonnull Map<String, Object> resistances,
            @Nonnull Map<String, Double> ailmentBonuses,
            boolean poisonImmune) {
        Map<ElementType, Double> elemResist = new EnumMap<>(ElementType.class);
        double physResist = 0.0;

        for (var entry : resistances.entrySet()) {
            String key = entry.getKey().toLowerCase();
            double value = toDouble(entry.getValue());

            switch (key) {
                case "physical", "physical_resistance" -> physResist = value;
                case "fire" -> elemResist.put(ElementType.FIRE, value);
                case "water" -> elemResist.put(ElementType.WATER, value);
                case "lightning" -> elemResist.put(ElementType.LIGHTNING, value);
                case "earth" -> elemResist.put(ElementType.EARTH, value);
                case "wind" -> elemResist.put(ElementType.WIND, value);
                case "void" -> elemResist.put(ElementType.VOID, value);
                default -> {} // ignore unknown keys
            }
        }

        return new ResistanceProfile(elemResist, physResist, ailmentBonuses, poisonImmune);
    }

    private static double toDouble(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        if (value instanceof String s) {
            try { return Double.parseDouble(s); } catch (NumberFormatException e) { return 0.0; }
        }
        return 0.0;
    }

    // ==================== Nested Data Classes ====================

    /** Element-based profile entry from YAML. */
    public static class ElementProfileEntry {
        private Map<String, Object> resistances = new LinkedHashMap<>();
        private Map<String, Object> ailment_bonuses = new LinkedHashMap<>();

        @Nonnull
        public ResistanceProfile toProfile() {
            Map<String, Double> ailments = new LinkedHashMap<>();
            for (var entry : ailment_bonuses.entrySet()) {
                ailments.put(entry.getKey(), toDouble(entry.getValue()));
            }
            return parseResistanceMap(resistances, ailments, false);
        }

        @Nonnull
        static ElementProfileEntry fromMap(@Nonnull Map<String, Object> raw) {
            ElementProfileEntry entry = new ElementProfileEntry();
            Object r = raw.get("resistances");
            if (r instanceof Map<?, ?> m) {
                @SuppressWarnings("unchecked")
                Map<String, Object> resistMap = (Map<String, Object>) m;
                entry.resistances = resistMap;
            }
            Object a = raw.get("ailment_bonuses");
            if (a instanceof Map<?, ?> m) {
                @SuppressWarnings("unchecked")
                Map<String, Object> ailmentMap = (Map<String, Object>) m;
                entry.ailment_bonuses = ailmentMap;
            }
            return entry;
        }
    }

    /** Faction profile entry with detection rules. */
    public static class FactionProfileEntry {
        private List<String> detectionGroups = new ArrayList<>();
        private List<String> detectionKeywords = new ArrayList<>();
        private Map<String, Object> resistances = new LinkedHashMap<>();
        private double physicalResistance = 0.0;
        private Map<String, Object> ailment_bonuses = new LinkedHashMap<>();

        public boolean matches(@Nullable String roleName, @Nonnull Set<String> npcGroups) {
            // Check group detection
            for (String group : detectionGroups) {
                for (String npcGroup : npcGroups) {
                    if (npcGroup.contains(group)) {
                        return true;
                    }
                }
            }
            // Check keyword detection (e.g., Undead: "skeleton_warrior" contains "skeleton")
            if (roleName != null && !detectionKeywords.isEmpty()) {
                String lower = roleName.toLowerCase();
                for (String keyword : detectionKeywords) {
                    if (lower.contains(keyword.toLowerCase())) {
                        return true;
                    }
                }
            }
            return false;
        }

        @Nonnull
        public ResistanceProfile toProfile() {
            Map<String, Double> ailments = new LinkedHashMap<>();
            boolean poisonImmune = false;

            for (var entry : ailment_bonuses.entrySet()) {
                Object val = entry.getValue();
                if ("immune".equals(val) || "true".equals(val)) {
                    if (entry.getKey().equals("poison")) {
                        poisonImmune = true;
                    }
                } else {
                    ailments.put(entry.getKey(), toDouble(val));
                }
            }

            Map<String, Object> fullResist = new LinkedHashMap<>(resistances);
            if (physicalResistance != 0.0) {
                fullResist.put("physical", physicalResistance);
            }

            return parseResistanceMap(fullResist, ailments, poisonImmune);
        }

        @Nonnull
        @SuppressWarnings("unchecked")
        static FactionProfileEntry fromMap(@Nonnull Map<String, Object> raw) {
            FactionProfileEntry entry = new FactionProfileEntry();

            // Parse detection rules
            Object detection = raw.get("detection");
            if (detection instanceof Map<?, ?> d) {
                Object groups = d.get("groups");
                if (groups instanceof List<?> list) {
                    entry.detectionGroups = list.stream().map(Object::toString).toList();
                }
                Object keywords = d.get("keywords");
                if (keywords instanceof List<?> list) {
                    entry.detectionKeywords = list.stream().map(Object::toString).toList();
                }
            }

            // Parse resistances
            Object r = raw.get("resistances");
            if (r instanceof Map<?, ?> m) {
                entry.resistances = (Map<String, Object>) m;
            }

            // Parse physical resistance
            Object phys = raw.get("physical_resistance");
            if (phys != null) {
                entry.physicalResistance = toDouble(phys);
            }

            // Parse ailment bonuses
            Object a = raw.get("ailment_bonuses");
            if (a instanceof Map<?, ?> m) {
                entry.ailment_bonuses = (Map<String, Object>) m;
            }

            return entry;
        }
    }

    /** Global resistance settings — proper JavaBean for SnakeYAML direct construction. */
    public static class SettingsEntry {
        private double maxResistance = 75.0;
        private double minResistance = -25.0;
        private double penetrationFloor = 0.0;

        public double getMaxResistance() { return maxResistance; }
        public double getMinResistance() { return minResistance; }
        public double getPenetrationFloor() { return penetrationFloor; }

        // SnakeYAML snake_case setters
        public void setMax_resistance(double v) { this.maxResistance = v; }
        public void setMin_resistance(double v) { this.minResistance = v; }
        public void setPenetration_floor(double v) { this.penetrationFloor = v; }
    }
}
