package io.github.larsonix.trailoforbis.compat;

import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

/**
 * Config-driven mapping from Hexcode item IDs to RPG level ranges.
 *
 * <p>When Hexcode is detected, this config determines which Hexcode staff/book
 * items can drop at which player/mob levels. Items outside the level range
 * for a given drop are excluded from the loot pool for that drop.
 *
 * <p>Designed for SnakeYAML deserialization from hexcode-items.yml.
 */
public class HexcodeItemConfig {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private boolean enabled = true;

    private Map<String, LevelRange> staff_level_map = new LinkedHashMap<>();
    private Map<String, LevelRange> book_level_map = new LinkedHashMap<>();

    public HexcodeItemConfig() {
        initializeDefaults();
    }

    private void initializeDefaults() {
        if (staff_level_map.isEmpty()) {
            // Basic staffs — tier progression matching material curve
            staff_level_map.put("Hexstaff_Basic_Crude", new LevelRange(1, 10));
            staff_level_map.put("Hexstaff_Basic_Copper", new LevelRange(5, 15));
            staff_level_map.put("Hexstaff_Basic_Bronze", new LevelRange(10, 20));
            staff_level_map.put("Hexstaff_Basic_Iron", new LevelRange(15, 30));
            staff_level_map.put("Hexstaff_Basic_Thorium", new LevelRange(25, 40));
            staff_level_map.put("Hexstaff_Basic_Cobalt", new LevelRange(35, 50));
            staff_level_map.put("Hexstaff_Basic_Adamantite", new LevelRange(45, 60));
            staff_level_map.put("Hexstaff_Basic_Mithril", new LevelRange(55, 70));
            staff_level_map.put("Hexstaff_Basic_Onyxium", new LevelRange(65, 80));
            // Special staffs — endgame
            staff_level_map.put("Hexstaff_Special_Arcane", new LevelRange(70, 100));
            staff_level_map.put("Hexstaff_Special_Astral", new LevelRange(70, 100));
            staff_level_map.put("Hexstaff_Special_Fire", new LevelRange(70, 100));
            staff_level_map.put("Hexstaff_Special_Ice", new LevelRange(70, 100));
        }

        if (book_level_map.isEmpty()) {
            book_level_map.put("Hex_Book", new LevelRange(1, 50));
            book_level_map.put("Fire_Hexbook", new LevelRange(15, 60));
            book_level_map.put("Ice_Hexbook", new LevelRange(15, 60));
            book_level_map.put("Life_Hexbook", new LevelRange(15, 60));
            book_level_map.put("Void_Hexbook", new LevelRange(30, 80));
            book_level_map.put("Arcane_Hexbook", new LevelRange(50, 100));
        }
    }

    /**
     * Whether Hexcode item integration is enabled.
     * When false, Hexcode items are not added to loot pools even if detected.
     */
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Gets the level range for a Hexcode item (staff or book).
     *
     * @param itemId The item ID (e.g., "Hexstaff_Basic_Iron")
     * @return The level range, or null if not configured
     */
    @Nullable
    public LevelRange getLevelRange(@Nonnull String itemId) {
        LevelRange range = staff_level_map.get(itemId);
        if (range != null) {
            return range;
        }
        return book_level_map.get(itemId);
    }

    /**
     * Checks if a given item is within the level range for a drop level.
     *
     * @param itemId    The Hexcode item ID
     * @param dropLevel The level to check against
     * @return true if the item's configured range includes dropLevel,
     *         or true if the item has no configured range (unconfigured items always eligible)
     */
    public boolean isInLevelRange(@Nonnull String itemId, int dropLevel) {
        LevelRange range = getLevelRange(itemId);
        if (range == null) {
            return true; // Unconfigured items are always eligible
        }
        return dropLevel >= range.min && dropLevel <= range.max;
    }

    /**
     * Gets all configured staff item IDs.
     */
    @Nonnull
    public Set<String> getStaffItemIds() {
        return Collections.unmodifiableSet(staff_level_map.keySet());
    }

    /**
     * Gets all configured book item IDs.
     */
    @Nonnull
    public Set<String> getBookItemIds() {
        return Collections.unmodifiableSet(book_level_map.keySet());
    }

    /**
     * Gets all configured Hexcode item IDs (staffs + books).
     */
    @Nonnull
    public Set<String> getAllItemIds() {
        Set<String> all = new LinkedHashSet<>(staff_level_map.keySet());
        all.addAll(book_level_map.keySet());
        return Collections.unmodifiableSet(all);
    }

    // YAML setters
    public Map<String, LevelRange> getStaff_level_map() {
        return staff_level_map;
    }

    public void setStaff_level_map(Map<String, LevelRange> staff_level_map) {
        this.staff_level_map = staff_level_map;
    }

    public Map<String, LevelRange> getBook_level_map() {
        return book_level_map;
    }

    public void setBook_level_map(Map<String, LevelRange> book_level_map) {
        this.book_level_map = book_level_map;
    }

    /**
     * Validates the configuration.
     */
    public void validate() {
        for (Map.Entry<String, LevelRange> entry : staff_level_map.entrySet()) {
            validateRange("staff_level_map." + entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, LevelRange> entry : book_level_map.entrySet()) {
            validateRange("book_level_map." + entry.getKey(), entry.getValue());
        }
    }

    private void validateRange(String path, LevelRange range) {
        if (range == null) {
            LOGGER.atWarning().log("Hexcode config: %s has null range, will be ignored", path);
            return;
        }
        if (range.min < 0) {
            LOGGER.atWarning().log("Hexcode config: %s.min is negative (%d), clamping to 0", path, range.min);
            range.min = 0;
        }
        if (range.max < range.min) {
            LOGGER.atWarning().log("Hexcode config: %s.max (%d) < min (%d), swapping", path, range.max, range.min);
            int tmp = range.max;
            range.max = range.min;
            range.min = tmp;
        }
    }

    /**
     * A level range (min to max inclusive).
     * Designed for SnakeYAML deserialization.
     */
    public static class LevelRange {
        private int min;
        private int max;

        public LevelRange() {}

        public LevelRange(int min, int max) {
            this.min = min;
            this.max = max;
        }

        public int getMin() {
            return min;
        }

        public void setMin(int min) {
            this.min = min;
        }

        public int getMax() {
            return max;
        }

        public void setMax(int max) {
            this.max = max;
        }

        @Override
        public String toString() {
            return min + "-" + max;
        }
    }
}
