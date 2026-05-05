package io.github.larsonix.trailoforbis.mobs.archetype;

import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Set;
import java.util.logging.Level;

/**
 * Resolves combat archetype for mobs using config overrides, group detection,
 * and role name keyword scanning.
 *
 * <p><b>Resolution Priority:</b>
 * <ol>
 *   <li><b>Config override</b> → explicit per-role-name archetype (highest priority)</li>
 *   <li><b>NPCGroup detection</b> → animals/beasts → Beast archetype</li>
 *   <li><b>Role name keywords</b> → "mage" → Caster, "brute" → Brute, etc.</li>
 *   <li><b>Warrior fallback</b> → safe balanced baseline (always)</li>
 * </ol>
 *
 * <p>Follows the same pattern as {@code MobElementResolver}: single-responsibility
 * resolver, config in constructor, never returns null.
 *
 * @see MobArchetypeConfig
 * @see MobArchetype
 */
public class ArchetypeResolver {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final MobArchetypeConfig config;

    /**
     * Creates a new archetype resolver.
     *
     * @param config The archetype configuration
     */
    public ArchetypeResolver(@Nonnull MobArchetypeConfig config) {
        this.config = config;
    }

    /**
     * Resolves the combat archetype for a mob.
     *
     * <p>Never returns null — always falls back to {@link MobArchetype#WARRIOR}.
     *
     * @param roleName  The mob's role name (nullable)
     * @param npcGroups The NPC groups this mob belongs to (never null, may be empty)
     * @return The resolved archetype, never null
     */
    @Nonnull
    public MobArchetype resolve(@Nullable String roleName, @Nonnull Set<String> npcGroups) {
        // 1. Config override (highest priority)
        MobArchetype override = config.getOverride(roleName);
        if (override != null) {
            LOGGER.at(Level.FINE).log("Archetype for '%s': config override → %s", roleName, override);
            return override;
        }

        // 2. NPCGroup detection (animals/beasts)
        MobArchetype group = config.getGroupArchetype(npcGroups);
        if (group != null) {
            LOGGER.at(Level.FINE).log("Archetype for '%s': group detection → %s", roleName, group);
            return group;
        }

        // 3. Role name keywords
        MobArchetype keyword = config.getKeywordArchetype(roleName);
        if (keyword != null) {
            LOGGER.at(Level.FINE).log("Archetype for '%s': keyword match → %s", roleName, keyword);
            return keyword;
        }

        // 4. Fallback: Warrior (safe balanced baseline)
        LOGGER.at(Level.FINE).log("Archetype for '%s': WARRIOR fallback", roleName);
        return MobArchetype.WARRIOR;
    }

    /**
     * Gets the underlying configuration.
     */
    @Nonnull
    public MobArchetypeConfig getConfig() {
        return config;
    }
}
