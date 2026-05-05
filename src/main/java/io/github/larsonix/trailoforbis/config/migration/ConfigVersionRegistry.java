package io.github.larsonix.trailoforbis.config.migration;

import java.util.List;

/**
 * Centralized registry of current config file versions.
 *
 * <p>Bump the version constant here whenever a config file's structure changes
 * (new keys, removed keys, renamed keys, type changes). The migration system
 * compares file-on-disk version against these constants to determine if
 * migration is needed.
 *
 * <p>Adding a new key with a Java default does NOT require a version bump
 * (SnakeYAML handles missing keys). Bump only when:
 * <ul>
 *   <li>You want the new key to appear in the user's file with comments</li>
 *   <li>A Map/List config gained new entries that must be deployed</li>
 *   <li>Keys were renamed or restructured</li>
 *   <li>A key's type changed</li>
 * </ul>
 */
public final class ConfigVersionRegistry {

    private ConfigVersionRegistry() {}

    /** Current config schema version. Bump when ANY config structure changes. */
    public static final int CURRENT_VERSION = 1;

    /** Filename for the version tracking sidecar (lives in config dir, not inside configs). */
    public static final String VERSIONS_FILE = ".versions.yml";

    /**
     * All config filenames managed by the migration system.
     * Order doesn't matter — each is migrated independently.
     */
    public static List<String> getAllConfigFiles() {
        return List.of(
            "config.yml",
            "ailments.yml",
            "combat-text.yml",
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
            "mob-rarity.yml",
            "mob-resistances.yml",
            "mob-scaling.yml",
            "mob-spawn.yml",
            "mob-stat-pool.yml",
            "party.yml",
            "realm-mobs.yml",
            "realm-modifiers.yml",
            "realms.yml",
            "skill-sanctum.yml",
            "skill-tree-hexcode.yml",
            "skill-tree-layout.yml",
            "skill-tree-positions.yml",
            "skill-tree.yml",
            "tooltip.yml",
            "vanilla-conversion.yml"
        );
    }
}
