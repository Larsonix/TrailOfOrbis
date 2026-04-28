package io.github.larsonix.trailoforbis.commands.tooadmin.clipboard;

import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.maps.core.RealmMapData;
import io.github.larsonix.trailoforbis.stones.StoneItemData;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton clipboard storage for copy/paste operations on RPG items.
 *
 * <p>This class stores copied item data (gear, maps, stones) per player UUID,
 * allowing players to copy stats from one item and paste them onto another.
 *
 * <p>Each item type has its own clipboard storage, so copying a gear item
 * doesn't overwrite a previously copied map.
 *
 * <h2>Thread Safety</h2>
 * <p>All operations are thread-safe using ConcurrentHashMap.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Copy gear
 * CopyPasteClipboard.getInstance().copyGear(playerUuid, gearData);
 *
 * // Paste gear
 * Optional<GearData> copied = CopyPasteClipboard.getInstance().getCopiedGear(playerUuid);
 * if (copied.isPresent()) {
 *     // Apply copied stats to target item
 * }
 *
 * // Clear clipboard
 * CopyPasteClipboard.getInstance().clearAll(playerUuid);
 * }</pre>
 *
 * @see GearData
 * @see RealmMapData
 * @see StoneItemData
 */
public final class CopyPasteClipboard {

    /** Singleton instance */
    private static final CopyPasteClipboard INSTANCE = new CopyPasteClipboard();

    /** Copied gear data per player */
    private final Map<UUID, GearData> copiedGear = new ConcurrentHashMap<>();

    /** Copied map data per player */
    private final Map<UUID, RealmMapData> copiedMaps = new ConcurrentHashMap<>();

    /** Copied stone data per player */
    private final Map<UUID, StoneItemData> copiedStones = new ConcurrentHashMap<>();

    private CopyPasteClipboard() {
        // Private constructor for singleton
    }

    /**
     * Gets the singleton instance.
     *
     * @return The clipboard instance
     */
    @Nonnull
    public static CopyPasteClipboard getInstance() {
        return INSTANCE;
    }

    // ═══════════════════════════════════════════════════════════════════
    // GEAR CLIPBOARD
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Copies gear data to the player's clipboard.
     *
     * @param playerUuid The player's UUID
     * @param gearData The gear data to copy
     */
    public void copyGear(@Nonnull UUID playerUuid, @Nonnull GearData gearData) {
        copiedGear.put(playerUuid, gearData);
    }

    /**
     * Gets the copied gear data for a player.
     *
     * @param playerUuid The player's UUID
     * @return Optional containing the copied gear, or empty if none
     */
    @Nonnull
    public Optional<GearData> getCopiedGear(@Nonnull UUID playerUuid) {
        return Optional.ofNullable(copiedGear.get(playerUuid));
    }

    /**
     * Checks if the player has copied gear data.
     *
     * @param playerUuid The player's UUID
     * @return true if gear is in clipboard
     */
    public boolean hasGear(@Nonnull UUID playerUuid) {
        return copiedGear.containsKey(playerUuid);
    }

    /**
     * Clears the player's copied gear data.
     *
     * @param playerUuid The player's UUID
     */
    public void clearGear(@Nonnull UUID playerUuid) {
        copiedGear.remove(playerUuid);
    }

    // ═══════════════════════════════════════════════════════════════════
    // MAP CLIPBOARD
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Copies map data to the player's clipboard.
     *
     * @param playerUuid The player's UUID
     * @param mapData The map data to copy
     */
    public void copyMap(@Nonnull UUID playerUuid, @Nonnull RealmMapData mapData) {
        copiedMaps.put(playerUuid, mapData);
    }

    /**
     * Gets the copied map data for a player.
     *
     * @param playerUuid The player's UUID
     * @return Optional containing the copied map, or empty if none
     */
    @Nonnull
    public Optional<RealmMapData> getCopiedMap(@Nonnull UUID playerUuid) {
        return Optional.ofNullable(copiedMaps.get(playerUuid));
    }

    /**
     * Checks if the player has copied map data.
     *
     * @param playerUuid The player's UUID
     * @return true if map is in clipboard
     */
    public boolean hasMap(@Nonnull UUID playerUuid) {
        return copiedMaps.containsKey(playerUuid);
    }

    /**
     * Clears the player's copied map data.
     *
     * @param playerUuid The player's UUID
     */
    public void clearMap(@Nonnull UUID playerUuid) {
        copiedMaps.remove(playerUuid);
    }

    // ═══════════════════════════════════════════════════════════════════
    // STONE CLIPBOARD
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Copies stone data to the player's clipboard.
     *
     * @param playerUuid The player's UUID
     * @param stoneData The stone data to copy
     */
    public void copyStone(@Nonnull UUID playerUuid, @Nonnull StoneItemData stoneData) {
        copiedStones.put(playerUuid, stoneData);
    }

    /**
     * Gets the copied stone data for a player.
     *
     * @param playerUuid The player's UUID
     * @return Optional containing the copied stone, or empty if none
     */
    @Nonnull
    public Optional<StoneItemData> getCopiedStone(@Nonnull UUID playerUuid) {
        return Optional.ofNullable(copiedStones.get(playerUuid));
    }

    /**
     * Checks if the player has copied stone data.
     *
     * @param playerUuid The player's UUID
     * @return true if stone is in clipboard
     */
    public boolean hasStone(@Nonnull UUID playerUuid) {
        return copiedStones.containsKey(playerUuid);
    }

    /**
     * Clears the player's copied stone data.
     *
     * @param playerUuid The player's UUID
     */
    public void clearStone(@Nonnull UUID playerUuid) {
        copiedStones.remove(playerUuid);
    }

    // ═══════════════════════════════════════════════════════════════════
    // BULK OPERATIONS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Clears all clipboard data for a player.
     *
     * @param playerUuid The player's UUID
     */
    public void clearAll(@Nonnull UUID playerUuid) {
        copiedGear.remove(playerUuid);
        copiedMaps.remove(playerUuid);
        copiedStones.remove(playerUuid);
    }

    /**
     * Gets a summary of what's in the player's clipboard.
     *
     * @param playerUuid The player's UUID
     * @return Summary string (e.g., "gear, map")
     */
    @Nonnull
    public String getClipboardSummary(@Nonnull UUID playerUuid) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;

        if (hasGear(playerUuid)) {
            sb.append("gear");
            first = false;
        }
        if (hasMap(playerUuid)) {
            if (!first) sb.append(", ");
            sb.append("map");
            first = false;
        }
        if (hasStone(playerUuid)) {
            if (!first) sb.append(", ");
            sb.append("stone");
        }

        return sb.length() > 0 ? sb.toString() : "empty";
    }
}
