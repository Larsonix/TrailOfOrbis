package io.github.larsonix.trailoforbis.mobs.elemental;

import io.github.larsonix.trailoforbis.elemental.ElementType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

/**
 * Configuration for mob elemental affinity detection.
 *
 * <p>This config controls how mobs gain elemental damage types based on
 * their NPCGroup membership and role name keywords. The design philosophy
 * is that most mobs should deal physical damage only, with elemental damage
 * being thematic and predictable (fire mage → FIRE, void creature → VOID).
 *
 * <p>Loaded from {@code mob-elements.yml}.
 *
 * @see MobElementResolver
 */
public class MobElementConfig {

    // NPCGroup name → ElementType (e.g., "Void" → VOID)
    private Map<String, String> group_elements = new LinkedHashMap<>();

    // ElementType name → list of keywords (e.g., "FIRE" → ["fire", "flame", ...])
    private Map<String, List<String>> keywords = new LinkedHashMap<>();

    // Role name → ElementType name (explicit overrides)
    private Map<String, String> overrides = new LinkedHashMap<>();

    // Blacklist configuration
    private BlacklistConfig blacklist = new BlacklistConfig();

    // ==================== Getters and Setters ====================

    @Nonnull
    public Map<String, String> getGroup_elements() {
        return group_elements;
    }

    public void setGroup_elements(Map<String, String> group_elements) {
        this.group_elements = group_elements != null ? group_elements : new LinkedHashMap<>();
    }

    @Nonnull
    public Map<String, List<String>> getKeywords() {
        return keywords;
    }

    public void setKeywords(Map<String, List<String>> keywords) {
        this.keywords = keywords != null ? keywords : new LinkedHashMap<>();
    }

    @Nonnull
    public Map<String, String> getOverrides() {
        return overrides;
    }

    public void setOverrides(Map<String, String> overrides) {
        this.overrides = overrides != null ? overrides : new LinkedHashMap<>();
    }

    @Nonnull
    public BlacklistConfig getBlacklist() {
        return blacklist;
    }

    public void setBlacklist(BlacklistConfig blacklist) {
        this.blacklist = blacklist != null ? blacklist : new BlacklistConfig();
    }

    // ==================== Convenience Methods ====================

    /**
     * Gets the element type for an NPCGroup, if mapped.
     *
     * @param groupName The NPCGroup name (e.g., "Void")
     * @return The element type, or null if not mapped
     */
    @Nullable
    public ElementType getGroupElement(@Nullable String groupName) {
        if (groupName == null || group_elements == null) {
            return null;
        }
        String elementName = group_elements.get(groupName);
        return parseElementType(elementName);
    }

    /**
     * Gets the element type for an explicit override, if mapped.
     *
     * @param roleName The role name (case-insensitive)
     * @return The element type, or null if not overridden
     */
    @Nullable
    public ElementType getOverrideElement(@Nullable String roleName) {
        if (roleName == null || overrides == null || overrides.isEmpty()) {
            return null;
        }
        // Check both exact match and lowercase
        String elementName = overrides.get(roleName);
        if (elementName == null) {
            elementName = overrides.get(roleName.toLowerCase());
        }
        return parseElementType(elementName);
    }

    /**
     * Gets the list of keywords for an element type.
     *
     * @param element The element type
     * @return List of keywords (may be empty, never null)
     */
    @Nonnull
    public List<String> getKeywordsForElement(@Nonnull ElementType element) {
        if (keywords == null) {
            return Collections.emptyList();
        }
        List<String> list = keywords.get(element.name());
        return list != null ? list : Collections.emptyList();
    }

    /**
     * Checks if a role is blacklisted (should always be physical).
     *
     * @param roleName The role name
     * @param groups   The NPC groups this role belongs to
     * @return true if blacklisted
     */
    public boolean isBlacklisted(@Nullable String roleName, @Nonnull Set<String> groups) {
        return blacklist.isBlacklisted(roleName, groups);
    }

    /**
     * Parses an element type from a string name.
     *
     * @param elementName The element name (e.g., "FIRE", "VOID")
     * @return The element type, or null if invalid
     */
    @Nullable
    private ElementType parseElementType(@Nullable String elementName) {
        if (elementName == null || elementName.isEmpty()) {
            return null;
        }
        try {
            return ElementType.valueOf(elementName.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Creates a config with default values.
     *
     * @return Default config instance
     */
    @Nonnull
    public static MobElementConfig createDefaults() {
        MobElementConfig config = new MobElementConfig();

        // Default group elements
        config.group_elements = new LinkedHashMap<>();
        config.group_elements.put("Void", "VOID");

        // Default keywords
        config.keywords = new LinkedHashMap<>();
        config.keywords.put("FIRE", Arrays.asList(
            "fire", "flame", "inferno", "magma", "lava", "ember", "blaze", "pyro", "burn", "scorch"
        ));
        config.keywords.put("WATER", Arrays.asList(
            "ice", "frost", "frozen", "glacier", "arctic", "snow", "winter", "chill", "cryo"
        ));
        config.keywords.put("LIGHTNING", Arrays.asList(
            "lightning", "thunder", "shock", "electric", "volt", "spark", "electro"
        ));
        config.keywords.put("EARTH", Arrays.asList(
            "stone", "rock", "earth", "crystal", "golem", "boulder", "mineral", "cave"
        ));
        config.keywords.put("WIND", Arrays.asList(
            "wind", "gust", "tornado", "tempest", "cyclone", "breeze", "gale", "air"
        ));
        config.keywords.put("VOID", Arrays.asList(
            "void", "shadow", "dark", "corrupt", "abyss", "blight"
        ));

        // Default overrides - empty
        config.overrides = new LinkedHashMap<>();

        // Default blacklist
        config.blacklist = BlacklistConfig.createDefaults();

        return config;
    }

    // ==================== Nested Config Classes ====================

    /**
     * Blacklist configuration for roles/groups that should never be elemental.
     */
    public static class BlacklistConfig {
        private List<String> roles = new ArrayList<>();
        private List<String> groups = new ArrayList<>();

        // Cached sets for O(1) lookup
        private transient Set<String> roleSet;
        private transient Set<String> groupSet;

        @Nonnull
        public List<String> getRoles() {
            return roles;
        }

        public void setRoles(List<String> roles) {
            this.roles = roles != null ? roles : new ArrayList<>();
            this.roleSet = null; // Invalidate cache
        }

        @Nonnull
        public List<String> getGroups() {
            return groups;
        }

        public void setGroups(List<String> groups) {
            this.groups = groups != null ? groups : new ArrayList<>();
            this.groupSet = null; // Invalidate cache
        }

        /**
         * Checks if a role/group combination is blacklisted.
         *
         * @param roleName The role name (nullable)
         * @param groups   The NPC groups this role belongs to
         * @return true if blacklisted
         */
        public boolean isBlacklisted(@Nullable String roleName, @Nonnull Set<String> groups) {
            // Build cached sets on first access
            if (roleSet == null) {
                roleSet = roles != null ? new HashSet<>(roles) : Collections.emptySet();
            }
            if (groupSet == null) {
                groupSet = this.groups != null ? new HashSet<>(this.groups) : Collections.emptySet();
            }

            // Check role blacklist
            if (roleName != null && (roleSet.contains(roleName) || roleSet.contains(roleName.toLowerCase()))) {
                return true;
            }

            // Check group blacklist (with suffix matching for hierarchical paths)
            for (String group : groups) {
                if (groupSet.contains(group)) {
                    return true;
                }
                // Handle hierarchical paths like "Intelligent/Neutral/Livestock"
                for (String blacklisted : groupSet) {
                    if (group.endsWith("/" + blacklisted)) {
                        return true;
                    }
                }
            }

            return false;
        }

        /**
         * Creates default blacklist configuration.
         *
         * @return Default blacklist
         */
        @Nonnull
        public static BlacklistConfig createDefaults() {
            BlacklistConfig config = new BlacklistConfig();
            config.roles = new ArrayList<>();
            config.groups = Arrays.asList(
                "Prey", "PreyBig", "Critters", "Birds", "Aquatic"
            );
            return config;
        }
    }
}
