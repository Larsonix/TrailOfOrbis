package io.github.larsonix.trailoforbis.mobs.profile;

import com.hypixel.hytale.logger.HytaleLogger;
import io.github.larsonix.trailoforbis.elemental.ElementType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Set;
import java.util.logging.Level;

/**
 * Resolves resistance profiles for mobs using a 3-layer priority system.
 *
 * <p><b>Resolution Priority:</b>
 * <ol>
 *   <li><b>Config override</b> → explicit per-role-name profile (highest priority)</li>
 *   <li><b>Faction profile</b> → NPCGroup-based faction detection</li>
 *   <li><b>Element profile</b> → detected element determines resistances</li>
 *   <li><b>NEUTRAL fallback</b> → 0% all resistances (universal safety)</li>
 * </ol>
 *
 * <p>Follows the same pattern as {@code MobElementResolver}: single-responsibility
 * resolver, config in constructor, never returns null.
 *
 * @see MobResistanceConfig
 * @see ResistanceProfile
 */
public class ResistanceProfileResolver {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final MobResistanceConfig config;

    /**
     * Creates a new resistance profile resolver.
     *
     * @param config The resistance configuration
     */
    public ResistanceProfileResolver(@Nonnull MobResistanceConfig config) {
        this.config = config;
    }

    /**
     * Resolves the resistance profile for a mob.
     *
     * <p>Never returns null — always falls back to {@link ResistanceProfile#NEUTRAL}.
     *
     * @param roleName        The mob's role name (nullable)
     * @param npcGroups       The NPC groups this mob belongs to (never null, may be empty)
     * @param detectedElement The mob's detected element from MobElementResolver (nullable = physical)
     * @return The resolved resistance profile, never null
     */
    @Nonnull
    public ResistanceProfile resolve(
            @Nullable String roleName,
            @Nonnull Set<String> npcGroups,
            @Nullable ElementType detectedElement) {

        // Layer 3: Explicit per-role override (highest priority)
        ResistanceProfile override = config.getOverrideProfile(roleName);
        if (override != null) {
            LOGGER.at(Level.FINE).log("Resistance for '%s': config override", roleName);
            return override;
        }

        // Layer 2: Faction profile (replaces element-based)
        ResistanceProfile faction = config.getFactionProfile(roleName, npcGroups);
        if (faction != null) {
            LOGGER.at(Level.FINE).log("Resistance for '%s': faction profile", roleName);
            return faction;
        }

        // Layer 1: Element-based profile (universal default)
        ResistanceProfile element = config.getElementProfile(detectedElement);
        if (element != null) {
            LOGGER.at(Level.FINE).log("Resistance for '%s': element profile (%s)",
                roleName, detectedElement != null ? detectedElement.name() : "PHYSICAL");
            return element;
        }

        // Absolute fallback: neutral (0% all)
        LOGGER.at(Level.FINE).log("Resistance for '%s': NEUTRAL fallback", roleName);
        return ResistanceProfile.NEUTRAL;
    }

    /**
     * Gets the underlying configuration.
     */
    @Nonnull
    public MobResistanceConfig getConfig() {
        return config;
    }
}
