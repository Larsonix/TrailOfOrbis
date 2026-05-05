package io.github.larsonix.trailoforbis.attributes.debug;

import com.hypixel.hytale.logger.HytaleLogger;
import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.attributes.debug.DebugStatRegistry.StatEntry;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Per-player debug stat overrides that are applied as the final step
 * of the stat calculation pipeline.
 *
 * <p>Overrides survive stat recalculations (they're reapplied each time),
 * but do NOT persist across server restarts — they are purely in-memory
 * debug tools.
 *
 * <p>Two override modes that can coexist on the same stat:
 * <ul>
 *   <li>{@link Mode#SET} — Force a stat to an exact value (ignores pipeline output)</li>
 *   <li>{@link Mode#ADD} — Add a bonus on top of whatever the pipeline produces</li>
 * </ul>
 *
 * <p>SET and ADD can be combined: {@code set armor 100} then {@code add armor 50}
 * results in armor = 150 (SET forces to 100, then ADD layers +50 on top).
 *
 * <p>Thread Safety: Uses ConcurrentHashMap for the outer map. Individual player
 * override maps are replaced atomically via copy-on-write.
 */
public final class DebugStatOverrideProvider {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Override mode. */
    public enum Mode {
        /** Force the stat to an exact value. */
        SET,
        /** Add a bonus to the pipeline-computed value. */
        ADD
    }

    /** A single stat override. */
    public record Override(String statName, float value, Mode mode) {
        /** Composite key for storage: "statName:MODE" */
        String key() {
            return statName + ":" + mode.name();
        }
    }

    /**
     * Per-player override maps.
     * Outer key = player UUID.
     * Inner key = composite "statName:MODE" (e.g., "armor:SET", "armor:ADD").
     * This allows SET and ADD to coexist for the same stat.
     */
    private final ConcurrentHashMap<UUID, Map<String, Override>> overrides = new ConcurrentHashMap<>();

    /**
     * Adds or replaces an override for a player.
     *
     * <p>SET and ADD for the same stat are stored independently — both can be active.
     * Calling {@code setOverride("armor", 100, SET)} followed by
     * {@code setOverride("armor", 50, ADD)} results in armor = 150.
     *
     * @param playerId The player's UUID
     * @param statName The stat name (must exist in DebugStatRegistry)
     * @param value The override value
     * @param mode SET or ADD
     * @return true if the stat name is valid and override was applied
     */
    public boolean setOverride(@Nonnull UUID playerId, @Nonnull String statName, float value, @Nonnull Mode mode) {
        String normalized = statName.toLowerCase().trim();
        if (!DebugStatRegistry.exists(normalized)) {
            return false;
        }

        Override entry = new Override(normalized, value, mode);

        overrides.compute(playerId, (k, existing) -> {
            Map<String, Override> copy = existing != null ? new LinkedHashMap<>(existing) : new LinkedHashMap<>();
            copy.put(entry.key(), entry);
            return copy;
        });

        LOGGER.atInfo().log("[DebugStats] Override for %s: %s %s = %.2f",
            playerId.toString().substring(0, 8), mode, normalized, value);
        return true;
    }

    /**
     * Removes all overrides (both SET and ADD) for a specific stat on a player.
     *
     * @return the number of overrides removed (0, 1, or 2)
     */
    public int removeOverride(@Nonnull UUID playerId, @Nonnull String statName) {
        String normalized = statName.toLowerCase().trim();
        int[] removed = {0};

        overrides.computeIfPresent(playerId, (k, existing) -> {
            Map<String, Override> copy = new LinkedHashMap<>(existing);
            // Remove both SET and ADD keys for this stat
            if (copy.remove(normalized + ":" + Mode.SET.name()) != null) removed[0]++;
            if (copy.remove(normalized + ":" + Mode.ADD.name()) != null) removed[0]++;
            return copy.isEmpty() ? null : copy;
        });

        if (removed[0] > 0) {
            LOGGER.atInfo().log("[DebugStats] Removed %d override(s) for %s on %s",
                removed[0], normalized, playerId.toString().substring(0, 8));
        }
        return removed[0];
    }

    /**
     * Clears all overrides for a player.
     *
     * @return the number of overrides that were cleared
     */
    public int clearOverrides(@Nonnull UUID playerId) {
        Map<String, Override> removed = overrides.remove(playerId);
        int count = removed != null ? removed.size() : 0;
        if (count > 0) {
            LOGGER.atInfo().log("[DebugStats] Cleared %d overrides for %s",
                count, playerId.toString().substring(0, 8));
        }
        return count;
    }

    /**
     * Gets all active overrides for a player, keyed by stat name.
     *
     * <p>If both SET and ADD exist for the same stat, both are returned
     * in a list.
     *
     * @return stat name → list of overrides, empty map if none
     */
    @Nonnull
    public Map<String, List<Override>> getOverridesByStatName(@Nonnull UUID playerId) {
        Map<String, Override> playerOverrides = overrides.get(playerId);
        if (playerOverrides == null || playerOverrides.isEmpty()) {
            return Map.of();
        }

        Map<String, List<Override>> byStatName = new LinkedHashMap<>();
        for (Override ovr : playerOverrides.values()) {
            byStatName.computeIfAbsent(ovr.statName(), k -> new ArrayList<>()).add(ovr);
        }
        return Collections.unmodifiableMap(byStatName);
    }

    /**
     * Returns true if the player has any active overrides.
     */
    public boolean hasOverrides(@Nonnull UUID playerId) {
        Map<String, Override> playerOverrides = overrides.get(playerId);
        return playerOverrides != null && !playerOverrides.isEmpty();
    }

    /**
     * Gets the total number of active overrides for a player.
     */
    public int getOverrideCount(@Nonnull UUID playerId) {
        Map<String, Override> playerOverrides = overrides.get(playerId);
        return playerOverrides != null ? playerOverrides.size() : 0;
    }

    /**
     * Applies all overrides for a player to their ComputedStats.
     *
     * <p>Called from {@code AttributeManager.recalculateStatsInternal()} as the
     * final step before storing stats. SET overrides are applied first, then ADD
     * overrides, so ADD correctly layers on top of SET for the same stat.
     *
     * <p>Example: SET armor=100, ADD armor=+50 → final armor = 150.
     *
     * @param playerId The player's UUID
     * @param stats The ComputedStats to modify (mutated in place)
     * @return the number of overrides applied
     */
    public int applyOverrides(@Nonnull UUID playerId, @Nonnull ComputedStats stats) {
        Map<String, Override> playerOverrides = overrides.get(playerId);
        if (playerOverrides == null || playerOverrides.isEmpty()) {
            return 0;
        }

        int applied = 0;

        // Phase 1: SET overrides (absolute values)
        for (Override override : playerOverrides.values()) {
            if (override.mode() != Mode.SET) continue;

            StatEntry entry = DebugStatRegistry.get(override.statName());
            if (entry == null) continue;

            entry.setter().accept(stats, override.value());
            applied++;
        }

        // Phase 2: ADD overrides (additive bonuses, layer on top of SET)
        for (Override override : playerOverrides.values()) {
            if (override.mode() != Mode.ADD) continue;

            StatEntry entry = DebugStatRegistry.get(override.statName());
            if (entry == null) continue;

            float current = entry.getter().apply(stats);
            entry.setter().accept(stats, current + override.value());
            applied++;
        }

        if (applied > 0) {
            LOGGER.at(Level.FINE).log("[DebugStats] Applied %d overrides for %s",
                applied, playerId.toString().substring(0, 8));
        }

        return applied;
    }

    /**
     * Cleans up overrides for a disconnected player.
     * Called from the player disconnect handler via AttributeManager.cleanupPlayer().
     */
    public void cleanupPlayer(@Nonnull UUID playerId) {
        overrides.remove(playerId);
    }
}
