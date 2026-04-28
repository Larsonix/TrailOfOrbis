package io.github.larsonix.trailoforbis.mobs.classification;

import javax.annotation.Nonnull;
import java.util.Set;

/**
 * Represents a discovered NPC role with its classification information.
 *
 * <p>This record is created during the dynamic entity discovery process and
 * contains all the information needed to classify an NPC at runtime without
 * needing to query Hytale APIs repeatedly.
 *
 * @param roleName       The role's registered name (e.g., "skeleton_pirate_captain")
 * @param roleIndex      The internal Hytale role index
 * @param classification The determined RPGMobClass for this role
 * @param modSource      Source pack name (e.g., "Hytale:Hytale" or "MyMod:Creatures")
 * @param memberGroups   Set of NPCGroup names this role belongs to
 * @param detectionMethod How the classification was determined
 */
public record DiscoveredRole(
    @Nonnull String roleName,
    int roleIndex,
    @Nonnull RPGMobClass classification,
    @Nonnull String modSource,
    @Nonnull Set<String> memberGroups,
    @Nonnull DetectionMethod detectionMethod
) {
    /**
     * Describes how a role's classification was determined.
     *
     * <p>Detection methods are listed in priority order (highest to lowest).
     * During classification, the first matching method wins.
     */
    public enum DetectionMethod {
        /**
         * Explicit override from config (overrides.bosses, overrides.elites, overrides.passive).
         * Highest priority - always takes precedence.
         */
        CONFIG_OVERRIDE,

        /**
         * NPCGroup name matched a boss/elite pattern from group_patterns config.
         * Example: Role in "MyMod/Bosses" group matches pattern "*\/Bosses".
         */
        GROUP_PATTERN,

        /**
         * Role name matched a boss/elite pattern from detection_patterns config.
         * Example: "dragon_fire_boss" matches pattern "*_boss".
         */
        NAME_PATTERN,

        /**
         * Standard LivingWorld group membership (LivingWorld/Aggressive, Neutral, Passive).
         * This is how vanilla Hytale categorizes most creatures.
         */
        GROUP_MEMBERSHIP,

        /**
         * Fallback using the role's DefaultPlayerAttitude.
         * Used when no group membership is found.
         */
        ATTITUDE_FALLBACK
    }

    /**
     * Creates a string representation for debugging.
     *
     * @return A formatted string with key details
     */
    @Override
    public String toString() {
        return String.format("DiscoveredRole[%s (%d) -> %s via %s, groups=%d]",
            roleName, roleIndex, classification, detectionMethod, memberGroups.size());
    }

    /**
     * Checks if this role should receive stat scaling and XP rewards.
     *
     * @return true if the classification is combat-relevant
     */
    public boolean isCombatRelevant() {
        return classification.isCombatRelevant();
    }

    /**
     * Checks if this role is from a mod (not vanilla Hytale).
     *
     * @return true if the mod source is not the base Hytale game
     */
    public boolean isFromMod() {
        return !modSource.startsWith("Hytale:");
    }
}
