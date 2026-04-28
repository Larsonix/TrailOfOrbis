package io.github.larsonix.trailoforbis.commands.admin;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.attributes.AttributeType;
import io.github.larsonix.trailoforbis.database.models.PlayerData;
import io.github.larsonix.trailoforbis.database.repository.PlayerDataRepository;

import javax.annotation.Nonnull;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Shared utility methods for RPGAdmin subcommands.
 */
public final class AdminCommandHelper {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /**
     * Date formatter for displaying timestamps in inspect command.
     */
    public static final DateTimeFormatter DATE_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private AdminCommandHelper() {
        // Utility class - prevent instantiation
    }

    /**
     * Gets the current value of a specific attribute from player data.
     *
     * @param data The player data
     * @param type The attribute type
     * @return The attribute value
     */
    public static int getAttributeValue(PlayerData data, AttributeType type) {
        return switch (type) {
            case FIRE -> data.getFire();
            case WATER -> data.getWater();
            case LIGHTNING -> data.getLightning();
            case EARTH -> data.getEarth();
            case WIND -> data.getWind();
            case VOID -> data.getVoidAttr();
        };
    }

    /**
     * Logs admin action for audit trail.
     *
     * @param plugin The plugin instance
     * @param admin The admin player executing the command
     * @param action The action performed (e.g., "POINTS ADD", "ATTRIBUTE STRENGTH")
     * @param target The target player's username
     * @param operation The operation performed (e.g., "add", "remove", "set")
     * @param value The value used in the operation
     */
    public static void logAdminAction(
        TrailOfOrbis plugin,
        PlayerRef admin,
        String action,
        String target,
        String operation,
        int value
    ) {
        plugin.getLogger().atInfo().log(
            "[RPGAdmin] %s performed %s on %s: %s %d",
            admin.getUsername(), action, target, operation, value
        );
    }

    /**
     * Logs admin action for audit trail (string value variant).
     *
     * @param plugin The plugin instance
     * @param admin The admin player executing the command
     * @param action The action performed
     * @param target The target player's username
     * @param details Additional details about the action
     */
    public static void logAdminAction(
        TrailOfOrbis plugin,
        PlayerRef admin,
        String action,
        String target,
        String details
    ) {
        plugin.getLogger().atInfo().log(
            "[RPGAdmin] %s performed %s on %s: %s",
            admin.getUsername(), action, target, details
        );
    }

    /**
     * Resolves a player's UUID from their username using the player data repository.
     *
     * @param username The player's username (case-insensitive)
     * @param repo The player data repository to search
     * @return Optional containing the UUID if found, empty otherwise
     */
    @Nonnull
    public static Optional<UUID> resolvePlayerUuid(
        @Nonnull String username,
        @Nonnull PlayerDataRepository repo
    ) {
        // 1. Try online player first (exact or case-insensitive match)
        try {
            for (World world : Universe.get().getWorlds().values()) {
                for (PlayerRef ref : world.getPlayerRefs()) {
                    if (ref.getUsername().equalsIgnoreCase(username)) {
                        return Optional.of(ref.getUuid());
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.at(Level.FINE).withCause(e).log(
                "Error searching online players for UUID of '%s', falling back to database lookup", username);
        }

        // 2. Fallback to DB lookup
        return repo.getByUsername(username).map(PlayerData::getUuid);
    }

    /**
     * Resolves a player's data from their username.
     *
     * @param username The player's username (case-insensitive)
     * @param repo The player data repository to search
     * @return Optional containing the PlayerData if found, empty otherwise
     */
    @Nonnull
    public static Optional<PlayerData> resolvePlayer(
        @Nonnull String username,
        @Nonnull PlayerDataRepository repo
    ) {
        // 1. Try online player first to get UUID
        try {
            for (World world : Universe.get().getWorlds().values()) {
                for (PlayerRef ref : world.getPlayerRefs()) {
                    if (ref.getUsername().equalsIgnoreCase(username)) {
                        return repo.get(ref.getUuid());
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.at(Level.FINE).withCause(e).log(
                "Error searching online players for data of '%s', falling back to database lookup", username);
        }

        // 2. Fallback to DB lookup
        return repo.getByUsername(username);
    }
}
