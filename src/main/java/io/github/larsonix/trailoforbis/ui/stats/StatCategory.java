package io.github.larsonix.trailoforbis.ui.stats;

import javax.annotation.Nonnull;

/**
 * Categories for grouping stats in the stats page.
 *
 * <p>Each category has an ID (used for tab selection), display name (shown in UI),
 * color for visual distinction, and preferred container height based on content size.
 */
public enum StatCategory {

    /**
     * Quick summary: Level, XP, total points, key stats.
     * ~12 rows of content.
     */
    OVERVIEW("overview", "Overview", "#ffd700", 850, 130, true),

    /**
     * Primary elements: FIRE, WATER, LIGHTNING, EARTH, WIND, VOID with contribution breakdowns.
     * ~12 rows of content (6 elements × 2 lines each).
     */
    ATTRIBUTES("attributes", "Attributes", "#96a9be", 610, 140, false),

    /**
     * Resource pools: Health, Mana, Stamina, Oxygen, Signature Energy + regens.
     * ~20 rows of content.
     */
    RESOURCES("resources", "Base", "#4caf50", 950, 80, true),

    /**
     * Offensive stats: Physical/Spell damage, Crit, Attack speed, Leech, Penetration, Elemental.
     * Zero-hiding makes this tab significantly shorter for most builds.
     */
    OFFENSE("offense", "Offense", "#f44336", 950, 110, true),

    /**
     * Defensive stats: Survivability, Avoidance, Resistances, Block, Damage reduction.
     * Zero-hiding makes this tab significantly shorter for most builds.
     */
    DEFENSE("defense", "Defense", "#2196f3", 950, 110, true),

    /**
     * Movement stats: Move speed, Sprint, Jump, Climb, Walk, Crouch, Run.
     * ~10 rows of content.
     */
    MOVEMENT("movement", "Movement", "#ff9800", 577, 140, false);

    private final String id;
    private final String displayName;
    private final String color;
    private final int preferredHeight;
    private final int tabWidth;
    private final boolean needsScrolling;

    StatCategory(@Nonnull String id, @Nonnull String displayName, @Nonnull String color, int preferredHeight, int tabWidth, boolean needsScrolling) {
        this.id = id;
        this.displayName = displayName;
        this.color = color;
        this.preferredHeight = preferredHeight;
        this.tabWidth = tabWidth;
        this.needsScrolling = needsScrolling;
    }

    /**
     * Gets the unique ID for this category (used for tab selection).
     *
     * @return The category ID
     */
    @Nonnull
    public String getId() {
        return id;
    }

    /**
     * Gets the display name for this category (shown in UI tabs).
     *
     * @return The display name
     */
    @Nonnull
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Gets the color for this category (hex color code).
     *
     * @return The hex color code
     */
    @Nonnull
    public String getColor() {
        return color;
    }

    /**
     * Gets the preferred container height for this category's content.
     *
     * <p>Different categories have varying amounts of content, so the container
     * dynamically resizes to fit the content without excessive empty space or
     * requiring scrolling.
     *
     * @return The preferred height in pixels
     */
    public int getPreferredHeight() {
        return preferredHeight;
    }

    /**
     * Gets the tab button width for this category.
     *
     * @return The tab width in pixels
     */
    public int getTabWidth() {
        return tabWidth;
    }

    /**
     * Checks if this category's content requires scrolling.
     *
     * <p>Categories with extensive content (like Offense and Defense) need
     * scrolling. Categories that don't need scrolling use a regular Top
     * layout which extends to the full container width without reserving
     * space for a scrollbar.
     *
     * @return true if the category needs TopScrolling layout, false for Top layout
     */
    public boolean needsScrolling() {
        return needsScrolling;
    }

    /**
     * Finds a category by its ID.
     *
     * @param id The category ID to find
     * @return The matching category, or null if not found
     */
    public static StatCategory fromId(@Nonnull String id) {
        for (StatCategory category : values()) {
            if (category.id.equals(id)) {
                return category;
            }
        }
        return null;
    }
}
