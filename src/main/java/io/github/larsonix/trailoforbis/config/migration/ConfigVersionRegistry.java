package io.github.larsonix.trailoforbis.config.migration;

import java.util.List;

/**
 * Registry of all config files managed by the sync system.
 *
 * <p>On plugin update (version change), every file listed here is overwritten
 * from the JAR-bundled template. On normal restart (same version), only missing
 * files are created.
 */
public final class ConfigVersionRegistry {

    private ConfigVersionRegistry() {}

    /** Filename that stores the last plugin version that synced configs to disk. */
    public static final String LAST_SYNCED_VERSION_FILE = ".last-synced-version";

    /**
     * All config filenames managed by the sync system.
     * Every file listed here is overwritten from the JAR on plugin update.
     */
    public static List<String> getAllConfigFiles() {
        return List.of(
            "config.yml",
            "ailments.yml",
            "combat-text.yml",
            "consumable-loot.yml",
            "container-loot.yml",
            "death-recap.yml",
            "entity-discovery.yml",
            "equipment-stats.yml",
            "gear-balance.yml",
            "gear-modifiers.yml",
            "hexcode-items.yml",
            "hexcode-spells.yml",
            "inventory-detection.yml",
            "leveling.yml",
            "loot-discovery.yml",
            "loot-filter.yml",
            "loot-items.yml",
            "mob-archetypes.yml",
            "mob-classification.yml",
            "mob-elements.yml",
            "mob-modifiers.yml",
            "mob-rarity.yml",
            "mob-resistances.yml",
            "mob-scaling.yml",
            "mob-spawn.yml",
            "mob-stat-pool.yml",
            "party.yml",
            "realm-mobs.yml",
            "realm-modifiers.yml",
            "realm-templates.yml",
            "realms.yml",
            "skill-sanctum.yml",
            "skill-tree-hexcode.yml",
            "skill-tree-layout.yml",
            "skill-tree-positions.yml",
            "skill-tree.yml",
            "tooltip.yml",
            "vanilla-conversion.yml",
            "weapon-patching.yml"
        );
    }
}
