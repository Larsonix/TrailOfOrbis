package io.github.larsonix.trailoforbis.gear.migration;

import io.github.larsonix.trailoforbis.gear.model.GearData;

import javax.annotation.Nonnull;

/**
 * A version-specific item migration transform.
 *
 * <p>Implementations handle data shape changes between specific versions:
 * stat ID renames, field restructuring, enum value changes, etc.
 *
 * <p>Migrations are executed in order before validation/fixing.
 * They transform data to the new schema; validation then ensures values are correct.
 *
 * <h3>Example: Renaming a stat</h3>
 * <pre>{@code
 * public class V2ItemMigration implements ItemMigration {
 *     public int fromVersion() { return 1; }
 *     public int toVersion() { return 2; }
 *     public GearData migrate(GearData gear) {
 *         // Rename "spell_power" → "spell_damage" in modifiers
 *         ...
 *     }
 * }
 * }</pre>
 *
 * @see ItemMigrationService
 * @see ItemVersionRegistry
 */
public interface ItemMigration {

    /**
     * The version this migration upgrades FROM.
     */
    int fromVersion();

    /**
     * The version this migration upgrades TO.
     */
    int toVersion();

    /**
     * Transforms gear data from the old schema to the new schema.
     *
     * <p>This method must NOT validate or fix values — only transform structure.
     * Validation and value fixing happens after all migrations are applied.
     *
     * @param gear The gear data in the old schema
     * @return Transformed gear data in the new schema
     */
    @Nonnull
    GearData migrate(@Nonnull GearData gear);
}
