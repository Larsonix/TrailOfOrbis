package io.github.larsonix.trailoforbis.mobs.elemental;

import com.hypixel.hytale.logger.HytaleLogger;
import io.github.larsonix.trailoforbis.elemental.ElementType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

/**
 * Resolves elemental affinity for mobs based on their role name and NPCGroup membership.
 *
 * <p><b>Resolution Priority:</b>
 * <ol>
 *   <li><b>Blacklist check</b> → return {@code null} if blacklisted (always physical)</li>
 *   <li><b>Explicit override</b> → return configured element for specific role name</li>
 *   <li><b>NPCGroup membership</b> → return group's element (e.g., Void → VOID)</li>
 *   <li><b>Role name keywords</b> → return matched element (e.g., "fire_mage" → FIRE)</li>
 *   <li><b>Default</b> → return {@code null} (physical only, NO RANDOM!)</li>
 * </ol>
 *
 * <p><b>Design Philosophy:</b>
 * <ul>
 *   <li>Most mobs should deal physical damage (predictable for players)</li>
 *   <li>Elemental damage should be thematic and obvious</li>
 *   <li>No random elements - players should be able to strategize</li>
 * </ul>
 *
 * @see MobElementConfig
 */
public class MobElementResolver {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final MobElementConfig config;

    /**
     * Creates a new element resolver.
     *
     * @param config The element configuration
     */
    public MobElementResolver(@Nonnull MobElementConfig config) {
        this.config = config;
    }

    /**
     * Resolves the elemental affinity for a mob.
     *
     * <p>Uses a priority-based system to determine element:
     * <ol>
     *   <li>Blacklist → null (physical only)</li>
     *   <li>Override → configured element</li>
     *   <li>NPCGroup → group's element</li>
     *   <li>Keywords → matched element</li>
     *   <li>Default → null (NO random!)</li>
     * </ol>
     *
     * @param roleName  The mob's role name (nullable)
     * @param npcGroups The NPC groups this mob belongs to (never null, may be empty)
     * @return The element type, or {@code null} for physical-only damage
     */
    @Nullable
    public ElementType resolve(@Nullable String roleName, @Nonnull Set<String> npcGroups) {
        // 1. Blacklist check - if blacklisted, always physical
        if (config.isBlacklisted(roleName, npcGroups)) {
            LOGGER.at(Level.FINE).log("Role '%s' blacklisted → physical only", roleName);
            return null;
        }

        // 2. Explicit override - highest priority after blacklist
        ElementType override = config.getOverrideElement(roleName);
        if (override != null) {
            LOGGER.at(Level.FINE).log("Role '%s' has override → %s", roleName, override);
            return override;
        }

        // 3. NPCGroup membership - most reliable detection
        ElementType groupElement = resolveByGroup(npcGroups);
        if (groupElement != null) {
            LOGGER.at(Level.FINE).log("Role '%s' in elemental group → %s", roleName, groupElement);
            return groupElement;
        }

        // 4. Role name keywords - fallback detection
        ElementType keywordElement = resolveByKeyword(roleName);
        if (keywordElement != null) {
            LOGGER.at(Level.FINE).log("Role '%s' matched keyword → %s", roleName, keywordElement);
            return keywordElement;
        }

        // 5. Default: null (physical only) - NO RANDOM!
        return null;
    }

    /**
     * Resolves element by NPCGroup membership.
     *
     * @param npcGroups The NPC groups to check
     * @return The element type, or null if no group matches
     */
    @Nullable
    private ElementType resolveByGroup(@Nonnull Set<String> npcGroups) {
        for (String group : npcGroups) {
            // Check direct match first
            ElementType element = config.getGroupElement(group);
            if (element != null) {
                return element;
            }

            // Handle hierarchical paths like "Intelligent/Aggressive/Void/VoidSpawn"
            // Check if any configured group name appears as a path component
            for (var entry : config.getGroup_elements().entrySet()) {
                String configGroup = entry.getKey();
                if (group.contains("/" + configGroup + "/") || group.endsWith("/" + configGroup)) {
                    ElementType e = config.getGroupElement(configGroup);
                    if (e != null) {
                        return e;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Resolves element by role name keyword matching.
     *
     * @param roleName The role name to check
     * @return The element type, or null if no keyword matches
     */
    @Nullable
    private ElementType resolveByKeyword(@Nullable String roleName) {
        if (roleName == null || roleName.isEmpty()) {
            return null;
        }

        String lowerName = roleName.toLowerCase();

        // Check each element's keywords
        for (ElementType element : ElementType.values()) {
            List<String> keywords = config.getKeywordsForElement(element);
            for (String keyword : keywords) {
                if (lowerName.contains(keyword.toLowerCase())) {
                    return element;
                }
            }
        }

        return null;
    }

    /**
     * Gets the underlying configuration.
     *
     * @return The element configuration
     */
    @Nonnull
    public MobElementConfig getConfig() {
        return config;
    }
}
