package io.github.larsonix.trailoforbis.gear.migration;

/**
 * Centralized registry for item schema version.
 *
 * <p>Bump {@link #CURRENT_VERSION} whenever gear generation rules change
 * in a way that invalidates existing items:
 * <ul>
 *   <li>Implicit damage/defense types changed for a weapon/armor category</li>
 *   <li>Modifier IDs renamed or removed from config</li>
 *   <li>Modifier allowed-slots or equipment-type restrictions changed</li>
 *   <li>Value scaling formula changed significantly</li>
 * </ul>
 *
 * <p>Adding a new modifier to config does NOT require a version bump
 * (existing items simply can't roll it yet — no invalid state).
 */
public final class ItemVersionRegistry {

    private ItemVersionRegistry() {}

    /**
     * Current item schema version. Bump when gear generation rules change.
     *
     * <p>Items with {@code RPG:Version < CURRENT_VERSION} (or missing) will be
     * validated and fixed on next player login.
     */
    public static final int CURRENT_VERSION = 7;
}
