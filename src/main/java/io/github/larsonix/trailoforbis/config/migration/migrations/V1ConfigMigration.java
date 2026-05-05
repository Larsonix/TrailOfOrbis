package io.github.larsonix.trailoforbis.config.migration.migrations;

import io.github.larsonix.trailoforbis.config.migration.ConfigMigration;

import java.util.Map;

/**
 * Initial migration: v0 → v1.
 *
 * <p>This is the baseline migration that runs for all existing users upgrading
 * to the version that introduces the migration system. It performs no structural
 * changes — the deep merge will handle adding new keys and updating comments.
 *
 * <p>The primary purpose is to establish the version stamp so future migrations
 * can target specific version ranges.
 */
public final class V1ConfigMigration implements ConfigMigration {

    @Override
    public int fromVersion() {
        return 0;
    }

    @Override
    public int toVersion() {
        return 1;
    }

    @Override
    public String targetFile() {
        return "*"; // Applies to all config files
    }

    @Override
    public void migrate(Map<String, Object> data) {
        // No structural changes needed for v1.
        // The deep merge will:
        // - Add any new keys with defaults
        // - Preserve all user values
        // - Update comments via template-preserving write
        // - Stamp config_version: 1
    }
}
