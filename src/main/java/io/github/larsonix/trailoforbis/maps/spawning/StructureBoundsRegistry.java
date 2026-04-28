package io.github.larsonix.trailoforbis.maps.spawning;

import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared registry of placed structure footprints for overlap prevention.
 * <p>
 * Both {@link RealmStructurePlacer} and {@link BossStructurePlacer} register their
 * placed structures here and check before pasting new ones. This prevents
 * runtime-to-runtime structure overlaps.
 * <p>
 * Uses 2D AABB intersection on the XZ plane with a configurable safety margin.
 * The margin is added during registration so all future overlap checks
 * automatically enforce minimum spacing between structures.
 */
public class StructureBoundsRegistry {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /**
     * Safety margin added to each side of every registered structure footprint.
     * Ensures at least {@code MARGIN * 2} blocks of clearance between structures.
     * <p>
     * Value of 2 = 4 blocks between structure edges (2 on each side).
     */
    private static final int MARGIN = 2;

    /**
     * 2D footprint of a placed structure on the XZ plane.
     */
    public record PlacedBounds(int minX, int minZ, int maxX, int maxZ) {
        boolean intersects(int otherMinX, int otherMinZ, int otherMaxX, int otherMaxZ) {
            return minX <= otherMaxX && maxX >= otherMinX
                && minZ <= otherMaxZ && maxZ >= otherMinZ;
        }
    }

    private final Map<UUID, List<PlacedBounds>> realmBounds = new ConcurrentHashMap<>();

    /**
     * Check if a bounding box overlaps any previously placed structure in this realm.
     *
     * @return true if the area overlaps an existing structure (including margin)
     */
    public boolean overlaps(@Nonnull UUID realmId, int minX, int minZ, int maxX, int maxZ) {
        List<PlacedBounds> bounds = realmBounds.get(realmId);
        if (bounds == null || bounds.isEmpty()) {
            return false;
        }
        for (PlacedBounds placed : bounds) {
            if (placed.intersects(minX, minZ, maxX, maxZ)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Register a placed structure's XZ footprint with safety margin.
     * <p>
     * The margin is added here so all future overlap checks automatically
     * enforce minimum spacing. No caller needs to pad their own bounds.
     */
    public void register(@Nonnull UUID realmId, int minX, int minZ, int maxX, int maxZ) {
        realmBounds.computeIfAbsent(realmId, k -> new ArrayList<>())
            .add(new PlacedBounds(minX - MARGIN, minZ - MARGIN, maxX + MARGIN, maxZ + MARGIN));
    }

    /**
     * Pre-register a circular spawn exclusion zone as occupied bounds.
     * <p>
     * Call this before any structure placement to prevent both
     * {@link RealmStructurePlacer} and {@link BossStructurePlacer} satellites
     * from landing inside the cleared spawn area.
     * <p>
     * Uses an axis-aligned bounding box inscribing the circle. The MARGIN
     * is added automatically by {@link #register}.
     *
     * @param realmId the realm's unique ID
     * @param centerX spawn X coordinate (typically 0)
     * @param centerZ spawn Z coordinate (typically 0)
     * @param radius  exclusion radius in blocks
     */
    public void registerSpawnExclusion(@Nonnull UUID realmId, int centerX, int centerZ, int radius) {
        register(realmId, centerX - radius, centerZ - radius, centerX + radius, centerZ + radius);
        LOGGER.atFine().log("Registered spawn exclusion zone for realm %s: center=(%d,%d) R=%d",
            realmId.toString().substring(0, 8), centerX, centerZ, radius);
    }

    /**
     * Clean up bounds when a realm closes.
     */
    public void onRealmClosed(@Nonnull UUID realmId) {
        realmBounds.remove(realmId);
    }

    public void shutdown() {
        int realms = realmBounds.size();
        realmBounds.clear();
        LOGGER.atInfo().log("StructureBoundsRegistry shut down — %d realm entries cleared", realms);
    }
}
