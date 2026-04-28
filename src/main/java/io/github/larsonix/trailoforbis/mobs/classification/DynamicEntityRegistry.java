package io.github.larsonix.trailoforbis.mobs.classification;

import com.hypixel.hytale.builtin.tagset.TagSetPlugin;
import com.hypixel.hytale.builtin.tagset.config.NPCGroup;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.asset.builder.BuilderInfo;
import io.github.larsonix.trailoforbis.mobs.classification.provider.TagLookupProvider;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Dynamic registry that discovers and classifies all NPC roles at runtime.
 *
 * <p>This provides automatic mod compatibility by scanning Hytale's NPC registry
 * on startup and classifying each role using configurable patterns and Hytale's
 * built-in group system.
 *
 * <p><b>Features:</b>
 * <ul>
 *   <li>O(1) classification lookup after initial discovery</li>
 *   <li>Pattern-based boss/elite detection (name and group patterns)</li>
 *   <li>Full backwards compatibility with explicit override lists</li>
 *   <li>Statistics tracking for debugging and admin commands</li>
 *   <li>Hot-reload support via {@link #discoverRoles()}</li>
 * </ul>
 *
 * @see EntityDiscoveryConfig
 * @see DiscoveredRole
 */
public class DynamicEntityRegistry {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /**
     * Default groups that indicate hostile mobs (direct NPCGroup names).
     * These are checked against the role's actual group memberships via getGroupMemberships().
     *
     * <p>Based on Hytale's LivingWorld/Aggressive meta-group composition:
     * - Trork, Goblin, Skeleton, Zombie, Void, Vermin (enemy factions)
     * - Predators, PredatorsBig (wild hostile animals)
     * - Scarak, Outlander, Undead (other hostile groups)
     */
    private static final Set<String> DEFAULT_HOSTILE_GROUPS = Set.of(
        "Trork", "Goblin", "Skeleton", "Zombie", "Void", "Vermin",
        "Predators", "PredatorsBig", "Scarak", "Outlander", "Undead"
    );

    /**
     * Default groups that indicate passive mobs (non-combat creatures).
     *
     * <p>Based on Hytale's LivingWorld groupings:
     * - LivingWorld/Neutral: Prey, PreyBig (livestock, deer, boars — non-aggressive animals)
     * - LivingWorld/Passive: Critters, Birds, Aquatic (ambient wildlife)
     *
     * <p>Livestock (Chicken, Cow, Pig, Sheep, etc.) are included via the Prey/PreyBig groups.
     * They receive PASSIVE classification with 0.1x stat/XP multipliers but no info bar.
     */
    private static final Set<String> DEFAULT_PASSIVE_GROUPS = Set.of(
        "Prey", "PreyBig", "Critters", "Birds", "Aquatic"
    );

    private final EntityDiscoveryConfig config;
    private final TagLookupProvider tagLookupProvider;

    // Group sets for classification (loaded from config or defaults)
    private final Set<String> hostileGroups;
    private final Set<String> minorGroups;
    private final Set<String> passiveGroups;

    // Role cache: roleName (lowercase) -> DiscoveredRole
    private final Map<String, DiscoveredRole> roleCache = new ConcurrentHashMap<>();

    // Statistics
    private int totalDiscovered = 0;
    private final Map<RPGMobClass, Integer> countByClass = new EnumMap<>(RPGMobClass.class);
    private final Map<DiscoveredRole.DetectionMethod, Integer> countByMethod = new EnumMap<>(DiscoveredRole.DetectionMethod.class);
    private final Map<String, Integer> countBySource = new ConcurrentHashMap<>();

    /**
     * Creates a new dynamic entity registry.
     *
     * @param config           The discovery configuration
     * @param tagLookupProvider Provider for Hytale group membership checks
     */
    public DynamicEntityRegistry(@Nonnull EntityDiscoveryConfig config, @Nonnull TagLookupProvider tagLookupProvider) {
        this.config = config;
        this.tagLookupProvider = tagLookupProvider;

        // Load classification group sets: start with defaults, then merge config
        // This allows users to ADD custom groups without losing built-in hostile/passive detection
        EntityDiscoveryConfig.ClassificationGroups groups = config.getClassification_groups();
        this.hostileGroups = new HashSet<>(DEFAULT_HOSTILE_GROUPS);
        if (groups != null && groups.getHostile() != null) {
            this.hostileGroups.addAll(groups.getHostile());
        }
        // Minor groups are empty by default - minor mobs use explicit overrides
        this.minorGroups = new HashSet<>();
        if (groups != null && groups.getMinor() != null) {
            this.minorGroups.addAll(groups.getMinor());
        }
        this.passiveGroups = new HashSet<>(DEFAULT_PASSIVE_GROUPS);
        if (groups != null && groups.getPassive() != null) {
            this.passiveGroups.addAll(groups.getPassive());
        }

        LOGGER.at(Level.FINE).log("Initialized with %d hostile groups, %d minor groups, %d passive groups",
            hostileGroups.size(), minorGroups.size(), passiveGroups.size());
    }

    /**
     * Discovers and classifies all registered NPC roles.
     *
     * <p>This scans Hytale's NPC registry using {@code NPCPlugin.get().getRoleTemplateNames()}
     * and classifies each role according to the configured detection priority.
     *
     * <p>Call this during plugin initialization, and again if mods are hot-loaded.
     *
     * @return Number of roles discovered
     */
    public int discoverRoles() {
        // Clear previous data
        roleCache.clear();
        totalDiscovered = 0;
        countByClass.clear();
        countByMethod.clear();
        countBySource.clear();

        // Initialize counts
        for (RPGMobClass cls : RPGMobClass.values()) {
            countByClass.put(cls, 0);
        }
        for (DiscoveredRole.DetectionMethod method : DiscoveredRole.DetectionMethod.values()) {
            countByMethod.put(method, 0);
        }

        if (!config.getDiscovery().isEnabled()) {
            LOGGER.at(Level.INFO).log("Entity discovery is disabled");
            return 0;
        }

        NPCPlugin npcPlugin = NPCPlugin.get();
        if (npcPlugin == null) {
            LOGGER.at(Level.WARNING).log("NPCPlugin not available, entity discovery skipped");
            return 0;
        }

        // Get all registered role template names (spawnable = false to include all)
        List<String> roleNames = npcPlugin.getRoleTemplateNames(false);
        if (roleNames == null || roleNames.isEmpty()) {
            LOGGER.at(Level.WARNING).log("No NPC roles found in registry");
            return 0;
        }

        // Validate configured group names against Hytale's registry
        validateGroupNames();

        LOGGER.at(Level.INFO).log("Discovering %d NPC roles...", roleNames.size());

        for (String roleName : roleNames) {
            try {
                DiscoveredRole discovered = discoverRole(roleName, npcPlugin);
                if (discovered != null) {
                    roleCache.put(roleName.toLowerCase(), discovered);
                    totalDiscovered++;

                    // Update statistics
                    countByClass.merge(discovered.classification(), 1, Integer::sum);
                    countByMethod.merge(discovered.detectionMethod(), 1, Integer::sum);
                    countBySource.merge(discovered.modSource(), 1, Integer::sum);
                }
            } catch (Exception e) {
                LOGGER.at(Level.WARNING).withCause(e).log("Failed to discover role: %s", roleName);
            }
        }

        LOGGER.at(Level.INFO).log("Entity discovery complete: %d roles (BOSS=%d, ELITE=%d, HOSTILE=%d, MINOR=%d, PASSIVE=%d)",
            totalDiscovered,
            countByClass.getOrDefault(RPGMobClass.BOSS, 0),
            countByClass.getOrDefault(RPGMobClass.ELITE, 0),
            countByClass.getOrDefault(RPGMobClass.HOSTILE, 0),
            countByClass.getOrDefault(RPGMobClass.MINOR, 0),
            countByClass.getOrDefault(RPGMobClass.PASSIVE, 0));

        // Warn if critical classification buckets are empty
        if (countByClass.getOrDefault(RPGMobClass.PASSIVE, 0) == 0) {
            LOGGER.at(Level.WARNING).log(
                "Zero roles classified as PASSIVE — passive mobs may receive combat scaling! Check NPCGroup names in entity-discovery.yml");
        }
        if (countByClass.getOrDefault(RPGMobClass.HOSTILE, 0) == 0) {
            LOGGER.at(Level.WARNING).log(
                "Zero roles classified as HOSTILE — mob scaling may be completely disabled! Check NPCGroup names in entity-discovery.yml");
        }

        return totalDiscovered;
    }

    /**
     * Validates that configured group names actually exist in Hytale's NPCGroup registry.
     * Logs warnings for any phantom groups that would silently have no effect.
     */
    private void validateGroupNames() {
        if (TagSetPlugin.get() == null) {
            return;
        }
        var assetMap = NPCGroup.getAssetMap();
        if (assetMap == null) {
            return;
        }

        int hostileValid = 0, hostileTotal = hostileGroups.size();
        int passiveValid = 0, passiveTotal = passiveGroups.size();

        for (String name : hostileGroups) {
            if (assetMap.getIndex(name) >= 0) {
                hostileValid++;
            } else {
                LOGGER.at(Level.WARNING).log(
                    "NPCGroup '%s' not found in Hytale registry — classification rules referencing this group will have no effect", name);
            }
        }
        for (String name : minorGroups) {
            if (assetMap.getIndex(name) < 0) {
                LOGGER.at(Level.WARNING).log(
                    "NPCGroup '%s' not found in Hytale registry — classification rules referencing this group will have no effect", name);
            }
        }
        for (String name : passiveGroups) {
            if (assetMap.getIndex(name) >= 0) {
                passiveValid++;
            } else {
                LOGGER.at(Level.WARNING).log(
                    "NPCGroup '%s' not found in Hytale registry — classification rules referencing this group will have no effect", name);
            }
        }

        LOGGER.at(Level.INFO).log("Group validation: %d/%d hostile, %d/%d passive groups exist in registry",
            hostileValid, hostileTotal, passiveValid, passiveTotal);
    }

    /**
     * Discovers and classifies a single role.
     *
     * @param roleName  The role name to discover
     * @param npcPlugin The NPC plugin instance
     * @return The discovered role, or null if blacklisted
     */
    @Nullable
    private DiscoveredRole discoverRole(@Nonnull String roleName, @Nonnull NPCPlugin npcPlugin) {
        int roleIndex = npcPlugin.getIndex(roleName);
        if (roleIndex < 0) {
            return null;
        }

        // Get mod source from builder info path
        String modSource = getModSource(roleName, npcPlugin);

        // Check blacklist
        if (config.getBlacklist().isBlacklisted(roleName, modSource)) {
            LOGGER.at(Level.FINE).log("Role blacklisted: %s", roleName);
            return null;
        }

        // Get group memberships
        Set<String> memberGroups = getGroupMemberships(roleIndex);

        // Classify the role
        ClassificationResult result = classifyRole(roleName, roleIndex, memberGroups, npcPlugin);

        return new DiscoveredRole(
            roleName,
            roleIndex,
            result.classification,
            modSource,
            memberGroups,
            result.method
        );
    }

    /**
     * Gets the mod source identifier for a role.
     *
     * @param roleName  The role name
     * @param npcPlugin The NPC plugin instance
     * @return Mod source string (e.g., "Hytale:Hytale" or path-based identifier)
     */
    @Nonnull
    private String getModSource(@Nonnull String roleName, @Nonnull NPCPlugin npcPlugin) {
        try {
            int roleIndex = npcPlugin.getIndex(roleName);
            BuilderInfo builderInfo = npcPlugin.getRoleBuilderInfo(roleIndex);
            if (builderInfo != null && builderInfo.getPath() != null) {
                // Extract mod identifier from path
                // Typical path: mods/ModName/Server/NPC/Roles/RoleName.json
                String pathStr = builderInfo.getPath().toString();
                if (pathStr.contains("mods/") || pathStr.contains("mods\\")) {
                    String[] parts = pathStr.split("[/\\\\]");
                    for (int i = 0; i < parts.length; i++) {
                        if (parts[i].equals("mods") && i + 1 < parts.length) {
                            return parts[i + 1] + ":" + parts[i + 1];
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.at(Level.FINE).withCause(e).log("Could not determine mod source for: %s", roleName);
        }
        return "Hytale:Hytale";
    }

    /**
     * Gets all NPCGroup memberships for a role.
     *
     * @param roleIndex The role index
     * @return Set of group names this role belongs to
     */
    @Nonnull
    private Set<String> getGroupMemberships(int roleIndex) {
        Set<String> groups = new HashSet<>();

        try {
            if (TagSetPlugin.get() == null) {
                return groups;
            }

            // Iterate through all registered NPCGroups
            var assetMap = NPCGroup.getAssetMap();
            if (assetMap == null) {
                return groups;
            }

            // Get all group entries
            var groupMap = assetMap.getAssetMap();
            if (groupMap == null) {
                return groups;
            }

            for (var entry : groupMap.entrySet()) {
                String groupName = entry.getKey();
                int groupIndex = assetMap.getIndex(groupName);
                if (groupIndex >= 0) {
                    try {
                        if (TagSetPlugin.get(NPCGroup.class).tagInSet(groupIndex, roleIndex)) {
                            groups.add(groupName);
                        }
                    } catch (Exception e) {
                        // Skip groups that can't be checked
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.at(Level.FINE).withCause(e).log("Error getting group memberships for role index: %d", roleIndex);
        }

        return groups;
    }

    /**
     * Classifies a role using the configured detection priority.
     *
     * <p>Detection priority:
     * <ol>
     *   <li>Config overrides (explicit boss/elite/minor/passive lists)</li>
     *   <li>Group patterns (NPCGroup names matching patterns)</li>
     *   <li>Name patterns (role names matching patterns)</li>
     *   <li>Direct group membership (minor → hostile → passive)</li>
     *   <li>Fallback to PASSIVE for unclassified roles</li>
     * </ol>
     *
     * @param roleName     The role name
     * @param roleIndex    The role index
     * @param memberGroups Groups this role belongs to
     * @param npcPlugin    The NPC plugin instance (unused but kept for future expansion)
     * @return Classification result with class and detection method
     */
    @Nonnull
    private ClassificationResult classifyRole(
            @Nonnull String roleName,
            int roleIndex,
            @Nonnull Set<String> memberGroups,
            @Nonnull NPCPlugin npcPlugin) {

        EntityDiscoveryConfig.Overrides overrides = config.getOverrides();
        EntityDiscoveryConfig.DetectionPatterns namePatterns = config.getDetection_patterns();
        EntityDiscoveryConfig.GroupPatterns groupPatterns = config.getGroup_patterns();

        // 1. Check config overrides (highest priority)
        if (overrides.isBoss(roleName)) {
            return new ClassificationResult(RPGMobClass.BOSS, DiscoveredRole.DetectionMethod.CONFIG_OVERRIDE);
        }
        if (overrides.isElite(roleName)) {
            return new ClassificationResult(RPGMobClass.ELITE, DiscoveredRole.DetectionMethod.CONFIG_OVERRIDE);
        }
        if (overrides.isHostile(roleName)) {
            return new ClassificationResult(RPGMobClass.HOSTILE, DiscoveredRole.DetectionMethod.CONFIG_OVERRIDE);
        }
        if (overrides.isMinor(roleName)) {
            return new ClassificationResult(RPGMobClass.MINOR, DiscoveredRole.DetectionMethod.CONFIG_OVERRIDE);
        }
        if (overrides.isPassive(roleName)) {
            return new ClassificationResult(RPGMobClass.PASSIVE, DiscoveredRole.DetectionMethod.CONFIG_OVERRIDE);
        }

        // 2. Check group patterns (if enabled)
        if (config.getDiscovery().isDetect_by_group()) {
            for (String group : memberGroups) {
                if (EntityDiscoveryConfig.matchesAnyPattern(group, groupPatterns.getBoss())) {
                    return new ClassificationResult(RPGMobClass.BOSS, DiscoveredRole.DetectionMethod.GROUP_PATTERN);
                }
            }
            for (String group : memberGroups) {
                if (EntityDiscoveryConfig.matchesAnyPattern(group, groupPatterns.getElite())) {
                    return new ClassificationResult(RPGMobClass.ELITE, DiscoveredRole.DetectionMethod.GROUP_PATTERN);
                }
            }
            // Note: No group patterns for MINOR - they use explicit overrides
        }

        // 3. Check name patterns (if enabled)
        if (config.getDiscovery().isDetect_by_name()) {
            if (EntityDiscoveryConfig.matchesAnyPattern(roleName, namePatterns.getBoss())) {
                return new ClassificationResult(RPGMobClass.BOSS, DiscoveredRole.DetectionMethod.NAME_PATTERN);
            }
            if (EntityDiscoveryConfig.matchesAnyPattern(roleName, namePatterns.getElite())) {
                return new ClassificationResult(RPGMobClass.ELITE, DiscoveredRole.DetectionMethod.NAME_PATTERN);
            }
            if (EntityDiscoveryConfig.matchesAnyPattern(roleName, namePatterns.getMinor())) {
                return new ClassificationResult(RPGMobClass.MINOR, DiscoveredRole.DetectionMethod.NAME_PATTERN);
            }
        }

        // 4. Check direct group membership for classification
        // This uses the actual group names (Trork, Goblin, etc.) rather than the
        // LivingWorld/* meta-groups, which avoids lookup issues with TagSetPlugin.
        // Note: Hytale group paths can be hierarchical (e.g., "Undead/Skeleton",
        // "Intelligent/Aggressive/Trork/Trork") so we use suffix matching.

        // 4a. Check minor groups first (if configured)
        if (!minorGroups.isEmpty()) {
            for (String group : memberGroups) {
                if (matchesAnyGroup(group, minorGroups)) {
                    return new ClassificationResult(RPGMobClass.MINOR, DiscoveredRole.DetectionMethod.GROUP_MEMBERSHIP);
                }
            }
        }

        // 4b. Check hostile groups
        for (String group : memberGroups) {
            if (matchesAnyGroup(group, hostileGroups)) {
                return new ClassificationResult(RPGMobClass.HOSTILE, DiscoveredRole.DetectionMethod.GROUP_MEMBERSHIP);
            }
        }

        // 4c. Check passive groups
        for (String group : memberGroups) {
            if (matchesAnyGroup(group, passiveGroups)) {
                return new ClassificationResult(RPGMobClass.PASSIVE, DiscoveredRole.DetectionMethod.GROUP_MEMBERSHIP);
            }
        }

        // 5. Default fallback - roles not in any known group are assumed PASSIVE
        // (This covers NPCs, villagers, and other non-combat entities)
        return new ClassificationResult(RPGMobClass.PASSIVE, DiscoveredRole.DetectionMethod.ATTITUDE_FALLBACK);
    }

    // =========================================================================
    // PUBLIC LOOKUP API
    // =========================================================================

    /**
     * Gets the classification for a role (O(1) lookup).
     *
     * @param roleName The role name (case-insensitive)
     * @return The RPG classification, or null if not discovered
     */
    @Nullable
    public RPGMobClass getClassification(@Nullable String roleName) {
        if (roleName == null) return null;
        DiscoveredRole discovered = roleCache.get(roleName.toLowerCase());
        return discovered != null ? discovered.classification() : null;
    }

    /**
     * Gets the full discovered role information.
     *
     * @param roleName The role name (case-insensitive)
     * @return The discovered role, or null if not found
     */
    @Nullable
    public DiscoveredRole getDiscoveredRole(@Nullable String roleName) {
        if (roleName == null) return null;
        return roleCache.get(roleName.toLowerCase());
    }

    /**
     * Gets all discovered roles with a specific classification.
     *
     * @param classification The classification to filter by
     * @return List of discovered roles (may be empty)
     */
    @Nonnull
    public List<DiscoveredRole> getRolesByClass(@Nonnull RPGMobClass classification) {
        return roleCache.values().stream()
            .filter(r -> r.classification() == classification)
            .collect(Collectors.toList());
    }

    /**
     * Checks if a role has been discovered.
     *
     * @param roleName The role name
     * @return true if the role is in the registry
     */
    public boolean hasRole(@Nullable String roleName) {
        if (roleName == null) return false;
        return roleCache.containsKey(roleName.toLowerCase());
    }

    // =========================================================================
    // STATISTICS API
    // =========================================================================

    /**
     * Gets discovery statistics for admin display.
     *
     * @return Statistics object with counts
     */
    @Nonnull
    public DiscoveryStatistics getStatistics() {
        return new DiscoveryStatistics(
            totalDiscovered,
            new EnumMap<>(countByClass),
            new EnumMap<>(countByMethod),
            new HashMap<>(countBySource)
        );
    }

    /**
     * Statistics container for discovery results.
     */
    public record DiscoveryStatistics(
        int totalDiscovered,
        Map<RPGMobClass, Integer> countByClass,
        Map<DiscoveredRole.DetectionMethod, Integer> countByMethod,
        Map<String, Integer> countBySource
    ) {
        /**
         * Formats statistics for display.
         */
        @Nonnull
        public String format() {
            StringBuilder sb = new StringBuilder();
            sb.append("=== Entity Discovery Stats ===\n");
            sb.append("Discovered ").append(totalDiscovered).append(" NPC roles\n\n");

            sb.append("By Classification:\n");
            countByClass.forEach((cls, count) ->
                sb.append("  ").append(cls).append(": ").append(count).append(" roles\n"));

            sb.append("\nBy Detection Method:\n");
            countByMethod.forEach((method, count) ->
                sb.append("  ").append(method).append(": ").append(count).append("\n"));

            sb.append("\nBy Source:\n");
            countBySource.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .forEach(entry ->
                    sb.append("  ").append(entry.getKey()).append(": ").append(entry.getValue()).append(" roles\n"));

            return sb.toString();
        }
    }

    /**
     * Internal classification result container.
     */
    private record ClassificationResult(
        RPGMobClass classification,
        DiscoveredRole.DetectionMethod method
    ) {}

    /**
     * Checks if a full group path matches any of the short group names.
     *
     * <p>Hytale NPCGroup paths can be hierarchical (e.g., "Undead/Skeleton",
     * "Intelligent/Aggressive/Trork/Trork"). This method handles matching
     * against short names like "Skeleton" or "Trork".
     *
     * @param fullGroupPath The full group path from NPCGroup asset map
     * @param shortNames    Set of short group names to match against
     * @return true if the group path matches any short name
     */
    private boolean matchesAnyGroup(@Nonnull String fullGroupPath, @Nonnull Set<String> shortNames) {
        for (String shortName : shortNames) {
            // Exact match: "Vermin" equals "Vermin"
            if (fullGroupPath.equals(shortName)) {
                return true;
            }
            // Suffix match: "Undead/Skeleton" ends with "/Skeleton"
            if (fullGroupPath.endsWith("/" + shortName)) {
                return true;
            }
        }
        return false;
    }
}
