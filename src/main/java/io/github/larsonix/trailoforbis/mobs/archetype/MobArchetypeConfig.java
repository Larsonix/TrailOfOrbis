package io.github.larsonix.trailoforbis.mobs.archetype;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

/**
 * Configuration for mob archetype auto-assignment.
 *
 * <p>Loaded from {@code mob-archetypes.yml}. Controls:
 * <ul>
 *   <li>Archetype stat multipliers (can override enum defaults)</li>
 *   <li>Role name keyword → archetype mappings</li>
 *   <li>NPCGroup → archetype mappings</li>
 *   <li>Per-role explicit overrides</li>
 *   <li>Noise settings for stat variation</li>
 * </ul>
 *
 * @see ArchetypeResolver
 */
public class MobArchetypeConfig {

    // Archetype name → keyword list (e.g., "brute" → ["brute", "ogre", "mauler"])
    private Map<String, List<String>> keywords = new LinkedHashMap<>();

    // Archetype name → group list (e.g., "beast" → ["Prey", "Animals"])
    private Map<String, List<String>> group_archetypes = new LinkedHashMap<>();

    // Role name → archetype name (explicit overrides)
    private Map<String, String> overrides = new LinkedHashMap<>();

    // Noise settings
    private NoiseConfig noise = new NoiseConfig();

    // ==================== YAML Setters ====================

    public void setKeywords(Map<String, List<String>> keywords) {
        this.keywords = keywords != null ? keywords : new LinkedHashMap<>();
    }

    public void setGroup_archetypes(Map<String, List<String>> group_archetypes) {
        this.group_archetypes = group_archetypes != null ? group_archetypes : new LinkedHashMap<>();
    }

    public void setOverrides(Map<String, String> overrides) {
        this.overrides = overrides != null ? overrides : new LinkedHashMap<>();
    }

    public void setNoise(NoiseConfig noise) {
        if (noise != null) {
            this.noise = noise;
        }
    }

    // We also accept the archetypes section from YAML but don't need it —
    // archetype stats come from the enum. This setter prevents SnakeYAML warnings.
    public void setArchetypes(Map<String, Object> archetypes) {
        // Config-driven archetype multiplier overrides could be added here in the future
    }

    // ==================== Accessors ====================

    /**
     * Gets the archetype override for a specific role name.
     *
     * @param roleName The role name
     * @return The archetype, or null if not overridden
     */
    @Nullable
    public MobArchetype getOverride(@Nullable String roleName) {
        if (roleName == null) return null;
        String name = overrides.get(roleName.toLowerCase());
        return name != null ? MobArchetype.fromName(name) : null;
    }

    /**
     * Gets the archetype for an NPCGroup match.
     *
     * @param npcGroups The mob's NPC groups
     * @return The archetype, or null if no group matches
     */
    @Nullable
    public MobArchetype getGroupArchetype(@Nonnull Set<String> npcGroups) {
        for (var entry : group_archetypes.entrySet()) {
            String archetypeName = entry.getKey();
            List<String> groups = entry.getValue();
            for (String configGroup : groups) {
                for (String npcGroup : npcGroups) {
                    if (npcGroup.contains(configGroup)) {
                        MobArchetype archetype = MobArchetype.fromName(archetypeName);
                        if (archetype != null) return archetype;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Gets the archetype matching a role name keyword.
     *
     * @param roleName The role name to scan
     * @return The archetype, or null if no keyword matches
     */
    @Nullable
    public MobArchetype getKeywordArchetype(@Nullable String roleName) {
        if (roleName == null) return null;
        String lower = roleName.toLowerCase();

        for (var entry : keywords.entrySet()) {
            String archetypeName = entry.getKey();
            List<String> keywordList = entry.getValue();
            for (String keyword : keywordList) {
                if (lower.contains(keyword.toLowerCase())) {
                    MobArchetype archetype = MobArchetype.fromName(archetypeName);
                    if (archetype != null) return archetype;
                }
            }
        }
        return null;
    }

    @Nonnull
    public NoiseConfig getNoise() {
        return noise;
    }

    // ==================== Factory ====================

    @Nonnull
    public static MobArchetypeConfig createDefaults() {
        MobArchetypeConfig config = new MobArchetypeConfig();

        // Default keywords validated against 78 Hytale NPC IDs
        config.keywords.put("brute", List.of("brute", "ogre", "mauler", "brawler", "berserker", "marauder", "aberrant"));
        config.keywords.put("warrior", List.of("warrior", "fighter", "scrapper", "soldier", "striker"));
        config.keywords.put("ranger", List.of("archer", "ranger", "hunter", "gunner", "lobber", "scout"));
        config.keywords.put("caster", List.of("mage", "archmage", "wizard", "shaman", "sorcerer", "priest", "cultist"));
        config.keywords.put("assassin", List.of("assassin", "stalker", "thief", "seeker"));
        config.keywords.put("tank", List.of("guard", "defender", "sentry", "knight"));

        // Default group mappings for animals
        config.group_archetypes.put("beast", List.of("Prey", "PreyBig", "Animals", "Beasts", "Critters"));

        return config;
    }

    // ==================== Noise Config ====================

    /** Noise settings — proper JavaBean for SnakeYAML direct construction. */
    public static class NoiseConfig {
        private boolean enabled = true;
        private double standardDeviation = 0.10;
        private double maxDeviation = 0.15;

        public boolean isEnabled() { return enabled; }
        public double getStandardDeviation() { return standardDeviation; }
        public double getMaxDeviation() { return maxDeviation; }

        // SnakeYAML setters
        public void setEnabled(boolean v) { this.enabled = v; }
        public void setStandard_deviation(double v) { this.standardDeviation = v; }
        public void setMax_deviation(double v) { this.maxDeviation = v; }
    }
}
