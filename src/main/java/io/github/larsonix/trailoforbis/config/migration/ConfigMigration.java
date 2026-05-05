package io.github.larsonix.trailoforbis.config.migration;

import java.util.Map;

/**
 * Interface for version-specific config transformations.
 *
 * <p>Each implementation handles migration between two specific versions
 * for a target config file (or all files with "*").
 *
 * <p>Migrations run sequentially: v0→v1, v1→v2, etc. Each mutates the
 * raw YAML map in-place before the deep merge applies new defaults.
 */
public interface ConfigMigration {

    /** Source version this migration upgrades FROM. */
    int fromVersion();

    /** Target version this migration upgrades TO. */
    int toVersion();

    /**
     * Which config file this migration applies to.
     *
     * @return filename (e.g., "config.yml") or "*" for all files
     */
    String targetFile();

    /**
     * Mutate the raw YAML map in-place.
     *
     * <p>Common operations:
     * <ul>
     *   <li>Rename: {@code data.put("newKey", data.remove("oldKey"))}</li>
     *   <li>Restructure: move values between nested maps</li>
     *   <li>Convert types: parse string to number, etc.</li>
     *   <li>Remove deprecated keys</li>
     * </ul>
     *
     * @param data the raw YAML map (mutable)
     */
    void migrate(Map<String, Object> data);
}
