package io.github.larsonix.trailoforbis.mobs.classification;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration for the Mob Classification System.
 *
 * <p>Defines XP/stat multipliers for each {@link RPGMobClass} and
 * override lists for elites and bosses.
 *
 * <p><b>YAML Structure (mob-classification.yml):</b>
 * <pre>
 * xp_multipliers:
 *   PASSIVE: 0.1
 *   HOSTILE: 1.0
 *   ELITE: 1.5
 *   BOSS: 5.0
 *
 * stat_multipliers:
 *   PASSIVE: 0.1
 *   HOSTILE: 1.0
 *   ELITE: 1.5
 *   BOSS: 3.0
 *
 * elites:
 *   - "skeleton_pirate_captain"
 *   - "trork_chieftain"
 *
 * bosses:
 *   - "dragon_fire"
 *   - "yeti"
 * </pre>
 */
public class MobClassificationConfig {

    private boolean enabled = true;

    // ==================== Multipliers ====================

    /** XP multipliers for each class (relative to standard mob XP) */
    private Map<RPGMobClass, Double> xpMultipliers = new EnumMap<>(RPGMobClass.class);

    /** Stat multipliers for each class (applied to calculated stats) */
    private Map<RPGMobClass, Double> statMultipliers = new EnumMap<>(RPGMobClass.class);

    // ==================== Override Lists ====================

    /** Role names that should be classified as ELITE (case-insensitive, stored lowercase) */
    private List<String> elites = new ArrayList<>();

    /** Role names that should be classified as BOSS (case-insensitive, stored lowercase) */
    private List<String> bosses = new ArrayList<>();

    // ==================== Constructor ====================

    public MobClassificationConfig() {
        initializeDefaults();
    }

    /**
     * Initializes default values.
     * These are used if YAML file doesn't exist or fields are missing.
     */
    private void initializeDefaults() {
        // XP Multipliers (5-tier system: PASSIVE < MINOR < HOSTILE < ELITE < BOSS)
        xpMultipliers.put(RPGMobClass.PASSIVE, 0.1);  // 10% of hostile XP for passive mobs
        xpMultipliers.put(RPGMobClass.MINOR, 0.5);    // 50% XP for small hostiles (larvae, foxes)
        xpMultipliers.put(RPGMobClass.HOSTILE, 1.0);
        xpMultipliers.put(RPGMobClass.ELITE, 1.5);
        xpMultipliers.put(RPGMobClass.BOSS, 5.0);

        // Stat Multipliers
        statMultipliers.put(RPGMobClass.PASSIVE, 0.1);
        statMultipliers.put(RPGMobClass.MINOR, 1.0);  // Normal stats - they're still combat mobs
        statMultipliers.put(RPGMobClass.HOSTILE, 1.0);
        statMultipliers.put(RPGMobClass.ELITE, 1.5);
        statMultipliers.put(RPGMobClass.BOSS, 3.0);
    }

    // ==================== YAML Snake_Case Setters ====================
    // These methods are called by SnakeYAML when parsing the config file.

    /**
     * YAML setter for 'xp_multipliers' key.
     */
    @SuppressWarnings("unchecked")
    public void setXp_multipliers(Map<?, ?> multipliers) {
        if (multipliers != null && !multipliers.isEmpty()) {
            this.xpMultipliers = new EnumMap<>(RPGMobClass.class);
            for (Map.Entry<?, ?> entry : multipliers.entrySet()) {
                RPGMobClass mobClass = parseEnumKey(entry.getKey());
                Double value = parseDoubleValue(entry.getValue());
                if (mobClass != null && value != null) {
                    this.xpMultipliers.put(mobClass, value);
                }
            }
        }
    }

    /**
     * YAML setter for 'stat_multipliers' key.
     */
    @SuppressWarnings("unchecked")
    public void setStat_multipliers(Map<?, ?> multipliers) {
        if (multipliers != null && !multipliers.isEmpty()) {
            this.statMultipliers = new EnumMap<>(RPGMobClass.class);
            for (Map.Entry<?, ?> entry : multipliers.entrySet()) {
                RPGMobClass mobClass = parseEnumKey(entry.getKey());
                Double value = parseDoubleValue(entry.getValue());
                if (mobClass != null && value != null) {
                    this.statMultipliers.put(mobClass, value);
                }
            }
        }
    }

    /**
     * YAML setter for 'elites' list.
     * Stores role names in lowercase for case-insensitive matching.
     */
    public void setElites(List<String> elitesList) {
        this.elites = new ArrayList<>();
        if (elitesList != null) {
            for (String name : elitesList) {
                if (name != null && !name.isBlank()) {
                    String normalized = name.toLowerCase().trim();
                    if (!this.elites.contains(normalized)) {
                        this.elites.add(normalized);
                    }
                }
            }
        }
    }

    /**
     * YAML setter for 'bosses' list.
     * Stores role names in lowercase for case-insensitive matching.
     */
    public void setBosses(List<String> bossesList) {
        this.bosses = new ArrayList<>();
        if (bossesList != null) {
            for (String name : bossesList) {
                if (name != null && !name.isBlank()) {
                    String normalized = name.toLowerCase().trim();
                    if (!this.bosses.contains(normalized)) {
                        this.bosses.add(normalized);
                    }
                }
            }
        }
    }

    // ==================== Helper Methods ====================

    private RPGMobClass parseEnumKey(Object key) {
        if (key instanceof RPGMobClass) {
            return (RPGMobClass) key;
        }
        if (key instanceof String) {
            try {
                return RPGMobClass.valueOf(((String) key).toUpperCase());
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return null;
    }

    private Double parseDoubleValue(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    // ==================== Standard Getters/Setters ====================

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Nonnull
    public Map<RPGMobClass, Double> getXpMultipliers() {
        return xpMultipliers;
    }

    public void setXpMultipliers(Map<RPGMobClass, Double> xpMultipliers) {
        if (xpMultipliers != null) {
            this.xpMultipliers = new EnumMap<>(RPGMobClass.class);
            this.xpMultipliers.putAll(xpMultipliers);
        }
    }

    @Nonnull
    public Map<RPGMobClass, Double> getStatMultipliers() {
        return statMultipliers;
    }

    public void setStatMultipliers(Map<RPGMobClass, Double> statMultipliers) {
        if (statMultipliers != null) {
            this.statMultipliers = new EnumMap<>(RPGMobClass.class);
            this.statMultipliers.putAll(statMultipliers);
        }
    }

    @Nonnull
    public List<String> getElites() {
        return elites;
    }

    @Nonnull
    public List<String> getBosses() {
        return bosses;
    }

    // ==================== Convenience Methods ====================

    /**
     * Checks if a role name is configured as an elite.
     *
     * @param roleName The role name (case-insensitive)
     * @return true if the role is in the elites list
     */
    public boolean isElite(String roleName) {
        if (roleName == null) return false;
        return elites.contains(roleName.toLowerCase().trim());
    }

    /**
     * Checks if a role name is configured as a boss.
     *
     * @param roleName The role name (case-insensitive)
     * @return true if the role is in the bosses list
     */
    public boolean isBoss(String roleName) {
        if (roleName == null) return false;
        return bosses.contains(roleName.toLowerCase().trim());
    }

    /**
     * Gets the XP multiplier for a specific class.
     *
     * @param mobClass The mob classification
     * @return XP multiplier (defaults to 1.0 if not configured)
     */
    public double getXpMultiplier(@Nonnull RPGMobClass mobClass) {
        return xpMultipliers.getOrDefault(mobClass, 1.0);
    }

    /**
     * Gets the Stat multiplier for a specific class.
     *
     * @param mobClass The mob classification
     * @return Stat multiplier (defaults to 1.0 if not configured)
     */
    public double getStatMultiplier(@Nonnull RPGMobClass mobClass) {
        return statMultipliers.getOrDefault(mobClass, 1.0);
    }

    /**
     * Returns a diagnostic summary of loaded configuration.
     *
     * @return Multi-line string with config summary
     */
    @Nonnull
    public String getDiagnostics() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== MobClassificationConfig ===\n");
        sb.append("Enabled: ").append(enabled).append("\n");
        sb.append("\n[XP Multipliers]\n");
        xpMultipliers.forEach((k, v) -> sb.append("  ").append(k).append(": ").append(v).append("\n"));
        sb.append("\n[Stat Multipliers]\n");
        statMultipliers.forEach((k, v) -> sb.append("  ").append(k).append(": ").append(v).append("\n"));
        sb.append("\n[Elites] (").append(elites.size()).append(" entries)\n");
        elites.forEach(e -> sb.append("  - ").append(e).append("\n"));
        sb.append("\n[Bosses] (").append(bosses.size()).append(" entries)\n");
        bosses.forEach(b -> sb.append("  - ").append(b).append("\n"));
        return sb.toString();
    }
}
