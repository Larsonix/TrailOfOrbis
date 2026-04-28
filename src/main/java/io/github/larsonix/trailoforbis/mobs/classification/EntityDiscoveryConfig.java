package io.github.larsonix.trailoforbis.mobs.classification;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Configuration for dynamic entity discovery and classification.
 *
 * <p>This replaces the static elite/boss lists in mob-classification.yml with a
 * dynamic system that automatically discovers and classifies NPCs from any mod.
 *
 * <p><b>Detection Priority (highest to lowest):</b>
 * <ol>
 *   <li>Config overrides (explicit boss/elite/passive lists)</li>
 *   <li>Group patterns (NPCGroup names matching boss/elite patterns)</li>
 *   <li>Name patterns (role names matching boss/elite patterns)</li>
 *   <li>LivingWorld groups (standard Hytale categorization)</li>
 *   <li>Attitude fallback (DefaultPlayerAttitude)</li>
 * </ol>
 *
 * <p>YAML structure: entity-discovery.yml
 */
public class EntityDiscoveryConfig {

    // =========================================================================
    // DISCOVERY SETTINGS
    // =========================================================================

    private Discovery discovery = new Discovery();

    // =========================================================================
    // DETECTION PATTERNS
    // =========================================================================

    private DetectionPatterns detection_patterns = new DetectionPatterns();
    private GroupPatterns group_patterns = new GroupPatterns();

    // =========================================================================
    // OVERRIDES (backwards compatible with mob-classification.yml)
    // =========================================================================

    private Overrides overrides = new Overrides();

    // =========================================================================
    // BLACKLIST
    // =========================================================================

    private Blacklist blacklist = new Blacklist();

    // =========================================================================
    // CLASSIFICATION GROUPS (direct NPCGroup names for hostile/passive detection)
    // =========================================================================

    private ClassificationGroups classification_groups = new ClassificationGroups();

    /**
     * Default constructor for YAML deserialization.
     */
    public EntityDiscoveryConfig() {
        // Defaults are set in inner classes
    }

    // =========================================================================
    // GETTERS
    // =========================================================================

    @Nonnull
    public Discovery getDiscovery() {
        return discovery != null ? discovery : new Discovery();
    }

    @Nonnull
    public DetectionPatterns getDetection_patterns() {
        return detection_patterns != null ? detection_patterns : new DetectionPatterns();
    }

    @Nonnull
    public GroupPatterns getGroup_patterns() {
        return group_patterns != null ? group_patterns : new GroupPatterns();
    }

    @Nonnull
    public Overrides getOverrides() {
        return overrides != null ? overrides : new Overrides();
    }

    @Nonnull
    public Blacklist getBlacklist() {
        return blacklist != null ? blacklist : new Blacklist();
    }

    @Nullable
    public ClassificationGroups getClassification_groups() {
        return classification_groups;
    }

    // =========================================================================
    // SETTERS (for YAML deserialization)
    // =========================================================================

    public void setDiscovery(Discovery discovery) {
        this.discovery = discovery;
    }

    public void setDetection_patterns(DetectionPatterns detection_patterns) {
        this.detection_patterns = detection_patterns;
    }

    public void setGroup_patterns(GroupPatterns group_patterns) {
        this.group_patterns = group_patterns;
    }

    public void setOverrides(Overrides overrides) {
        this.overrides = overrides;
    }

    public void setBlacklist(Blacklist blacklist) {
        this.blacklist = blacklist;
    }

    public void setClassification_groups(ClassificationGroups classification_groups) {
        this.classification_groups = classification_groups;
    }

    // =========================================================================
    // VALIDATION
    // =========================================================================

    /**
     * Validates the configuration.
     *
     * @throws ConfigValidationException if invalid
     */
    public void validate() throws ConfigValidationException {
        // Discovery settings validation
        if (discovery == null) {
            discovery = new Discovery();
        }

        // Ensure pattern lists are not null
        if (detection_patterns == null) {
            detection_patterns = new DetectionPatterns();
        }
        if (group_patterns == null) {
            group_patterns = new GroupPatterns();
        }
        if (overrides == null) {
            overrides = new Overrides();
        }
        if (blacklist == null) {
            blacklist = new Blacklist();
        }
    }

    /**
     * Exception thrown when config validation fails.
     */
    public static class ConfigValidationException extends Exception {
        public ConfigValidationException(String message) {
            super(message);
        }
    }

    // =========================================================================
    // PATTERN MATCHING HELPER
    // =========================================================================

    /**
     * Matches a value against a wildcard pattern.
     *
     * <p>Supports:
     * <ul>
     *   <li>{@code *} - matches any characters (including none)</li>
     *   <li>{@code ?} - matches exactly one character</li>
     * </ul>
     *
     * @param value   The value to check (e.g., "dragon_fire_boss")
     * @param pattern The pattern to match (e.g., "*_boss", "*Dragon*")
     * @return true if the value matches the pattern (case-insensitive)
     */
    public static boolean matchesPattern(@Nullable String value, @Nullable String pattern) {
        if (value == null || pattern == null) {
            return false;
        }

        // Convert wildcard pattern to regex
        String regex = pattern
            .replace(".", "\\.")   // Escape dots
            .replace("*", ".*")    // * matches any characters
            .replace("?", ".");    // ? matches single character

        try {
            return Pattern.compile("(?i)" + regex).matcher(value).matches();
        } catch (Exception e) {
            // Invalid regex pattern, fall back to simple contains check
            return value.toLowerCase().contains(pattern.toLowerCase().replace("*", "").replace("?", ""));
        }
    }

    /**
     * Checks if a value matches any pattern in a list.
     *
     * @param value    The value to check
     * @param patterns List of wildcard patterns
     * @return true if any pattern matches
     */
    public static boolean matchesAnyPattern(@Nullable String value, @Nonnull List<String> patterns) {
        if (value == null || patterns.isEmpty()) {
            return false;
        }
        for (String pattern : patterns) {
            if (matchesPattern(value, pattern)) {
                return true;
            }
        }
        return false;
    }

    // =========================================================================
    // INNER CLASSES
    // =========================================================================

    /**
     * Discovery master settings.
     */
    public static class Discovery {
        private boolean enabled = true;
        private boolean detect_by_name = true;
        private boolean detect_by_group = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isDetect_by_name() {
            return detect_by_name;
        }

        public void setDetect_by_name(boolean detect_by_name) {
            this.detect_by_name = detect_by_name;
        }

        public boolean isDetect_by_group() {
            return detect_by_group;
        }

        public void setDetect_by_group(boolean detect_by_group) {
            this.detect_by_group = detect_by_group;
        }
    }

    /**
     * Role name patterns for boss/elite/minor detection.
     */
    public static class DetectionPatterns {
        private List<String> boss = new ArrayList<>();
        private List<String> elite = new ArrayList<>();
        private List<String> minor = new ArrayList<>();

        public DetectionPatterns() {
            initializeDefaults();
        }

        private void initializeDefaults() {
            // Common boss patterns
            if (boss.isEmpty()) {
                boss.add("*_Boss");
                boss.add("*_boss");
                boss.add("Boss_*");
                boss.add("*Dragon*");
                boss.add("*Lord*");
                boss.add("*King*");
                boss.add("*Queen*");
                boss.add("*Titan*");
                boss.add("*Ancient*");
                boss.add("*Broodmother*");
            }

            // Common elite patterns
            if (elite.isEmpty()) {
                elite.add("*_Captain*");
                elite.add("*_Chieftain*");
                elite.add("*_Elite*");
                elite.add("*_Champion*");
                elite.add("*_Veteran*");
                elite.add("*_Alpha*");
                elite.add("*_Leader*");
                elite.add("*_Warlord*");
                elite.add("*Berserker*");
                elite.add("*Ogre*");
            }

            // Minor patterns - empty by default, use explicit overrides instead
            // (patterns like "*_larva" would be too broad)
        }

        @Nonnull
        public List<String> getBoss() {
            return boss != null ? boss : List.of();
        }

        public void setBoss(List<String> boss) {
            this.boss = boss != null ? boss : new ArrayList<>();
        }

        @Nonnull
        public List<String> getElite() {
            return elite != null ? elite : List.of();
        }

        public void setElite(List<String> elite) {
            this.elite = elite != null ? elite : new ArrayList<>();
        }

        @Nonnull
        public List<String> getMinor() {
            return minor != null ? minor : List.of();
        }

        public void setMinor(List<String> minor) {
            this.minor = minor != null ? minor : new ArrayList<>();
        }
    }

    /**
     * NPCGroup name patterns for boss/elite detection.
     */
    public static class GroupPatterns {
        private List<String> boss = new ArrayList<>();
        private List<String> elite = new ArrayList<>();

        public GroupPatterns() {
            initializeDefaults();
        }

        private void initializeDefaults() {
            // Common boss group patterns
            if (boss.isEmpty()) {
                boss.add("*/Bosses");
                boss.add("*/Boss");
                boss.add("Bosses/*");
            }

            // Common elite group patterns
            if (elite.isEmpty()) {
                elite.add("*/Elites");
                elite.add("*/Elite");
                elite.add("*/Veterans");
                elite.add("*/Minibosses");
                elite.add("Elites/*");
            }
        }

        @Nonnull
        public List<String> getBoss() {
            return boss != null ? boss : List.of();
        }

        public void setBoss(List<String> boss) {
            this.boss = boss != null ? boss : new ArrayList<>();
        }

        @Nonnull
        public List<String> getElite() {
            return elite != null ? elite : List.of();
        }

        public void setElite(List<String> elite) {
            this.elite = elite != null ? elite : new ArrayList<>();
        }
    }

    /**
     * Explicit role overrides (backwards compatible with mob-classification.yml).
     */
    public static class Overrides {
        private List<String> bosses = new ArrayList<>();
        private List<String> elites = new ArrayList<>();
        private List<String> hostiles = new ArrayList<>();
        private List<String> minors = new ArrayList<>();
        private List<String> passive = new ArrayList<>();

        public Overrides() {
            initializeDefaults();
        }

        private void initializeDefaults() {
            // Migrated from mob-classification.yml
            if (bosses.isEmpty()) {
                bosses.add("dragon_fire");
                bosses.add("dragon_frost");
                bosses.add("yeti");
                bosses.add("goblin_duke");
                bosses.add("scarak_broodmother");
            }

            if (elites.isEmpty()) {
                elites.add("skeleton_pirate_captain");
                elites.add("skeleton_sand_assassin");
                elites.add("trork_chieftain");
                elites.add("outlander_berserker");
                elites.add("outlander_priest");
                elites.add("goblin_ogre");
                elites.add("feran_sharptooth");
                elites.add("emberwulf");
                elites.add("fen_stalker");
            }

            // Default hostiles overrides - mobs that fall through group detection
            // but are real combat enemies (e.g., Wraith has no group membership)
            if (hostiles.isEmpty()) {
                hostiles.add("Wraith");         // Dangerous undead, no group membership
                hostiles.add("Wraith_Lantern"); // Variant with lantern
            }

            // Default minor mobs - small hostiles that give reduced XP
            if (minors.isEmpty()) {
                minors.add("larva_void");   // Small void creature
                minors.add("larva_silk");   // Small cave creature
                minors.add("fox");          // Small predator, flees often
            }

            // Default passive overrides - mobs that are in hostile groups but
            // should give reduced XP (easy to farm, not real combat threats)
            if (passive.isEmpty()) {
                passive.add("Horse_Skeleton");         // Undead mount, easy to farm
                passive.add("Horse_Skeleton_Armored"); // Armored variant
            }
        }

        @Nonnull
        public List<String> getBosses() {
            return bosses != null ? bosses : List.of();
        }

        public void setBosses(List<String> bosses) {
            this.bosses = bosses != null ? bosses : new ArrayList<>();
        }

        @Nonnull
        public List<String> getElites() {
            return elites != null ? elites : List.of();
        }

        public void setElites(List<String> elites) {
            this.elites = elites != null ? elites : new ArrayList<>();
        }

        @Nonnull
        public List<String> getHostiles() {
            return hostiles != null ? hostiles : List.of();
        }

        public void setHostiles(List<String> hostiles) {
            this.hostiles = hostiles != null ? hostiles : new ArrayList<>();
        }

        @Nonnull
        public List<String> getMinors() {
            return minors != null ? minors : List.of();
        }

        public void setMinors(List<String> minors) {
            this.minors = minors != null ? minors : new ArrayList<>();
        }

        @Nonnull
        public List<String> getPassive() {
            return passive != null ? passive : List.of();
        }

        public void setPassive(List<String> passive) {
            this.passive = passive != null ? passive : new ArrayList<>();
        }

        /**
         * Checks if a role name is in the bosses override list.
         */
        public boolean isBoss(String roleName) {
            if (roleName == null) return false;
            String normalized = roleName.toLowerCase().trim();
            return bosses.stream().anyMatch(b -> b.toLowerCase().equals(normalized));
        }

        /**
         * Checks if a role name is in the elites override list.
         */
        public boolean isElite(String roleName) {
            if (roleName == null) return false;
            String normalized = roleName.toLowerCase().trim();
            return elites.stream().anyMatch(e -> e.toLowerCase().equals(normalized));
        }

        /**
         * Checks if a role name is in the hostiles override list.
         */
        public boolean isHostile(String roleName) {
            if (roleName == null) return false;
            String normalized = roleName.toLowerCase().trim();
            return hostiles.stream().anyMatch(h -> h.toLowerCase().equals(normalized));
        }

        /**
         * Checks if a role name is in the minors override list.
         */
        public boolean isMinor(String roleName) {
            if (roleName == null) return false;
            String normalized = roleName.toLowerCase().trim();
            return minors.stream().anyMatch(m -> m.toLowerCase().equals(normalized));
        }

        /**
         * Checks if a role name is in the passive override list.
         */
        public boolean isPassive(String roleName) {
            if (roleName == null) return false;
            String normalized = roleName.toLowerCase().trim();
            return passive.stream().anyMatch(p -> p.toLowerCase().equals(normalized));
        }
    }

    /**
     * Blacklist for excluding roles and mods.
     */
    public static class Blacklist {
        private List<String> roles = new ArrayList<>();
        private List<String> mods = new ArrayList<>();

        public Blacklist() {
            initializeDefaults();
        }

        private void initializeDefaults() {
            if (roles.isEmpty()) {
                roles.add("*_Template_*");
                roles.add("*_Debug_*");
                roles.add("*_Test_*");
                roles.add("*_Abstract_*");
            }
            // No default mod blacklist
        }

        @Nonnull
        public List<String> getRoles() {
            return roles != null ? roles : List.of();
        }

        public void setRoles(List<String> roles) {
            this.roles = roles != null ? roles : new ArrayList<>();
        }

        @Nonnull
        public List<String> getMods() {
            return mods != null ? mods : List.of();
        }

        public void setMods(List<String> mods) {
            this.mods = mods != null ? mods : new ArrayList<>();
        }

        /**
         * Checks if a role should be blacklisted.
         */
        public boolean isBlacklisted(String roleName, String modSource) {
            // Check role patterns
            if (EntityDiscoveryConfig.matchesAnyPattern(roleName, roles)) {
                return true;
            }
            // Check mod blacklist
            if (modSource != null && !mods.isEmpty()) {
                for (String mod : mods) {
                    if (modSource.toLowerCase().contains(mod.toLowerCase())) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    /**
     * Direct NPCGroup names for hostile/minor/passive classification.
     *
     * <p>This allows overriding the default group lists for servers with custom mods
     * that use different group naming conventions.
     *
     * <p>Why direct groups instead of LivingWorld meta-groups?
     * The TagSetPlugin.hasTag() lookup for meta-groups like "LivingWorld/Aggressive"
     * fails silently in some configurations. Using direct group names (Trork, Goblin, etc.)
     * is more reliable because we already enumerate all group memberships via
     * NPCGroup.getAssetMap().
     */
    public static class ClassificationGroups {
        private List<String> hostile = new ArrayList<>();
        private List<String> minor = new ArrayList<>();
        private List<String> passive = new ArrayList<>();

        /**
         * Gets the list of NPCGroup names that indicate hostile mobs.
         *
         * <p>When empty, defaults to:
         * Trork, Goblin, Skeleton, Zombie, Void, Vermin,
         * Predators, PredatorsBig, Scarak, Outlander, Undead
         */
        @Nonnull
        public List<String> getHostile() {
            return hostile != null ? hostile : List.of();
        }

        public void setHostile(List<String> hostile) {
            this.hostile = hostile != null ? hostile : new ArrayList<>();
        }

        /**
         * Gets the list of NPCGroup names that indicate minor mobs (reduced XP hostiles).
         *
         * <p>Empty by default - minor mobs are typically identified by explicit overrides
         * rather than group membership, since "small creature" isn't a standard Hytale group.
         */
        @Nonnull
        public List<String> getMinor() {
            return minor != null ? minor : List.of();
        }

        public void setMinor(List<String> minor) {
            this.minor = minor != null ? minor : new ArrayList<>();
        }

        /**
         * Gets the list of NPCGroup names that indicate passive mobs.
         *
         * <p>When empty, defaults to:
         * Prey, PreyBig, Critters, Birds, Aquatic
         */
        @Nonnull
        public List<String> getPassive() {
            return passive != null ? passive : List.of();
        }

        public void setPassive(List<String> passive) {
            this.passive = passive != null ? passive : new ArrayList<>();
        }
    }

    // =========================================================================
    // FACTORY METHODS
    // =========================================================================

    /**
     * Creates a default configuration.
     */
    public static EntityDiscoveryConfig createDefaults() {
        return new EntityDiscoveryConfig();
    }
}
