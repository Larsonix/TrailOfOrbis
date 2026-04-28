package io.github.larsonix.trailoforbis.gear.item;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.UpdateType;
import com.hypixel.hytale.protocol.packets.assets.UpdateTranslations;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages dynamic translation registration for custom gear items.
 *
 * <p>Hytale's item system uses translation keys for names and descriptions.
 * This service registers custom translations per-player via the UpdateTranslations packet,
 * allowing each gear instance to have unique display text.
 *
 * <h2>Translation Key Format</h2>
 * <pre>
 * rpg.gear.{instanceId}.name        → "Sharp Iron Sword"
 * rpg.gear.{instanceId}.description → "Item Level: 45\nQuality: Excellent..."
 * </pre>
 *
 * <h2>Usage Flow</h2>
 * <pre>
 * 1. ItemSyncService detects new gear
 * 2. TranslationSyncService.registerTranslations() sends UpdateTranslations packet
 * 3. ItemSyncService sends UpdateItems packet (referencing the translation keys)
 * 4. Client displays item with custom name/tooltip
 * </pre>
 *
 * <h2>Thread Safety</h2>
 * <p>All public methods are thread-safe.
 */
public final class TranslationSyncService {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /**
     * Prefix for all RPG translation keys.
     *
     * <p>IMPORTANT: Must use a custom prefix like "rpg.gear." for dynamic translations.
     * Hytale's "server.items." prefix is reserved for built-in items loaded from
     * language files at startup. Using "server.items." causes the client to look
     * in those static files instead of runtime-registered UpdateTranslations packets.
     */
    private static final String KEY_PREFIX = "rpg.gear.";

    /** Suffix for name translation keys */
    private static final String KEY_NAME_SUFFIX = ".name";

    /** Suffix for description translation keys */
    private static final String KEY_DESC_SUFFIX = ".description";

    /**
     * Per-player tracking of registered translation item IDs.
     * Map: playerId → Set of registered itemIds
     */
    private final Map<UUID, Set<String>> playerTranslations = new ConcurrentHashMap<>();

    // =========================================================================
    // TRANSLATION KEY UTILITIES
    // =========================================================================

    /**
     * Gets the translation key for an item's name.
     *
     * <p>Uses custom format: {@code rpg.gear.{instanceId}.name}
     *
     * @param instanceId The compact instance ID (e.g., "1706123456789_42")
     * @return Translation key like "rpg.gear.1706123456789_42.name"
     */
    @Nonnull
    public static String getNameKey(@Nonnull String instanceId) {
        Objects.requireNonNull(instanceId, "instanceId cannot be null");
        return KEY_PREFIX + instanceId + KEY_NAME_SUFFIX;
    }

    /**
     * Gets the translation key for an item's description.
     *
     * <p>Uses custom format: {@code rpg.gear.{instanceId}.description}
     *
     * @param instanceId The compact instance ID (e.g., "1706123456789_42")
     * @return Translation key like "rpg.gear.1706123456789_42.description"
     */
    @Nonnull
    public static String getDescriptionKey(@Nonnull String instanceId) {
        Objects.requireNonNull(instanceId, "instanceId cannot be null");
        return KEY_PREFIX + instanceId + KEY_DESC_SUFFIX;
    }

    // =========================================================================
    // PLAYER LIFECYCLE
    // =========================================================================

    /**
     * Initializes translation state for a player.
     *
     * <p>Should be called when a player connects.
     *
     * @param playerId The player's UUID
     */
    public void onPlayerConnect(@Nonnull UUID playerId) {
        Objects.requireNonNull(playerId, "playerId cannot be null");
        playerTranslations.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet());
        LOGGER.atFine().log("Initialized translation sync for player %s", playerId);
    }

    /**
     * Cleans up translation state for a player.
     *
     * <p>Should be called when a player disconnects.
     *
     * @param playerId The player's UUID
     */
    public void onPlayerDisconnect(@Nonnull UUID playerId) {
        Objects.requireNonNull(playerId, "playerId cannot be null");
        playerTranslations.remove(playerId);
        LOGGER.atFine().log("Cleaned up translation sync for player %s", playerId);
    }

    // =========================================================================
    // REGISTRATION
    // =========================================================================

    /**
     * Checks if translations are already registered for a gear instance.
     *
     * @param playerId The player's UUID
     * @param instanceId The compact instance ID
     * @return true if translations are registered
     */
    public boolean isRegistered(@Nonnull UUID playerId, @Nonnull String instanceId) {
        Objects.requireNonNull(playerId, "playerId cannot be null");
        Objects.requireNonNull(instanceId, "instanceId cannot be null");

        Set<String> registered = playerTranslations.get(playerId);
        return registered != null && registered.contains(instanceId);
    }

    /**
     * Registers translations for an item with a player.
     *
     * <p>Sends an UpdateTranslations packet to the player with the name and
     * description text for the item.
     *
     * @param playerRef The player reference
     * @param instanceId The compact instance ID (e.g., "1706123456789_42")
     * @param nameText The display name text
     * @param descriptionText The tooltip description text
     * @return true if registration was sent, false if already registered
     */
    public boolean registerTranslations(
            @Nonnull PlayerRef playerRef,
            @Nonnull String instanceId,
            @Nonnull String nameText,
            @Nonnull String descriptionText) {
        Objects.requireNonNull(playerRef, "playerRef cannot be null");
        Objects.requireNonNull(instanceId, "instanceId cannot be null");
        Objects.requireNonNull(nameText, "nameText cannot be null");
        Objects.requireNonNull(descriptionText, "descriptionText cannot be null");

        UUID playerId = playerRef.getUuid();

        // Check if already registered
        Set<String> registered = playerTranslations.computeIfAbsent(
                playerId, k -> ConcurrentHashMap.newKeySet());

        if (registered.contains(instanceId)) {
            LOGGER.atFine().log("Translations already registered for %s (player %s)",
                    instanceId, playerId);
            return false;
        }

        // Build translation entries
        Map<String, String> translations = new HashMap<>();
        String nameKey = getNameKey(instanceId);
        String descKey = getDescriptionKey(instanceId);
        translations.put(nameKey, nameText);
        translations.put(descKey, descriptionText);

        // DEBUG: Log what we're about to register
        LOGGER.atFine().log("[DEBUG] Registering translations for %s: nameKey=%s, descKey=%s",
                instanceId, nameKey, descKey);
        LOGGER.atFine().log("[DEBUG] Translation values: name='%s', desc='%s...'",
                nameText, descriptionText.substring(0, Math.min(50, descriptionText.length())));

        // Send packet
        sendTranslationPacket(playerRef, translations);

        // Mark as registered
        registered.add(instanceId);

        // DEBUG: Confirm registration
        LOGGER.atFine().log("[DEBUG] Successfully sent UpdateTranslations for %s to player %s",
                instanceId, playerId);

        return true;
    }

    /**
     * Registers multiple translations at once (for batch sync).
     *
     * @param playerRef The player reference
     * @param translationEntries Map of instanceId → TranslationEntry
     * @return Number of new translations registered
     */
    public int registerTranslationsBatch(
            @Nonnull PlayerRef playerRef,
            @Nonnull Map<String, TranslationEntry> translationEntries) {
        Objects.requireNonNull(playerRef, "playerRef cannot be null");
        Objects.requireNonNull(translationEntries, "translationEntries cannot be null");

        if (translationEntries.isEmpty()) {
            return 0;
        }

        UUID playerId = playerRef.getUuid();
        Set<String> registered = playerTranslations.computeIfAbsent(
                playerId, k -> ConcurrentHashMap.newKeySet());

        // Filter out already-registered entries
        Map<String, String> newTranslations = new HashMap<>();
        int count = 0;

        for (Map.Entry<String, TranslationEntry> entry : translationEntries.entrySet()) {
            String instanceId = entry.getKey();
            if (!registered.contains(instanceId)) {
                TranslationEntry te = entry.getValue();
                newTranslations.put(getNameKey(instanceId), te.name());
                newTranslations.put(getDescriptionKey(instanceId), te.description());
                registered.add(instanceId);
                count++;
            }
        }

        if (!newTranslations.isEmpty()) {
            sendTranslationPacket(playerRef, newTranslations);
            LOGGER.atFine().log("Batch registered %d translations to player %s", count, playerId);
        }

        return count;
    }

    /**
     * Unregisters translations for a gear instance.
     *
     * <p>Called when an item is removed from a player's inventory.
     * Note: This only removes from tracking - the client may still have the
     * translation cached until the next full sync.
     *
     * @param playerId The player's UUID
     * @param instanceId The gear instance ID
     */
    public void unregisterTranslation(@Nonnull UUID playerId, @Nonnull String instanceId) {
        Objects.requireNonNull(playerId, "playerId cannot be null");
        Objects.requireNonNull(instanceId, "instanceId cannot be null");

        Set<String> registered = playerTranslations.get(playerId);
        if (registered != null) {
            registered.remove(instanceId);
        }
    }

    /**
     * Clears all translation tracking for a player.
     *
     * <p>Use this when forcing a complete resync of all gear items.
     * This ensures all translations will be re-sent on the next sync.
     *
     * <p>Note: This only clears the server-side tracking. The client
     * may still have cached translations until they are overwritten
     * by the resync.
     *
     * @param playerId The player's UUID
     */
    public void clearPlayerTranslations(@Nonnull UUID playerId) {
        Objects.requireNonNull(playerId, "playerId cannot be null");

        Set<String> registered = playerTranslations.get(playerId);
        if (registered != null) {
            int count = registered.size();
            registered.clear();
            LOGGER.atFine().log("Cleared %d translation entries for player %s", count, playerId);
        }
    }

    // =========================================================================
    // PACKET SENDING
    // =========================================================================

    /**
     * Sends an UpdateTranslations packet to a player.
     */
    private void sendTranslationPacket(
            @Nonnull PlayerRef playerRef,
            @Nonnull Map<String, String> translations) {
        try {
            UpdateTranslations packet = new UpdateTranslations();
            packet.type = UpdateType.AddOrUpdate;
            packet.translations = new HashMap<>(translations);

            playerRef.getPacketHandler().writeNoCache(packet);

            LOGGER.atFine().log("Sent UpdateTranslations packet with %d entries",
                    translations.size());
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log(
                    "Failed to send UpdateTranslations packet to player %s",
                    playerRef.getUuid());
        }
    }

    // =========================================================================
    // STATISTICS
    // =========================================================================

    /**
     * Gets the number of translations registered for a player.
     *
     * @param playerId The player's UUID
     * @return Number of registered translations
     */
    public int getRegisteredCount(@Nonnull UUID playerId) {
        Set<String> registered = playerTranslations.get(playerId);
        return registered != null ? registered.size() : 0;
    }

    /**
     * Gets the total number of tracked players.
     *
     * @return Number of players
     */
    public int getPlayerCount() {
        return playerTranslations.size();
    }

    // =========================================================================
    // INNER CLASSES
    // =========================================================================

    /**
     * Holds translation text for a gear item.
     */
    public record TranslationEntry(
            @Nonnull String name,
            @Nonnull String description
    ) {
        public TranslationEntry {
            Objects.requireNonNull(name, "name cannot be null");
            Objects.requireNonNull(description, "description cannot be null");
        }
    }
}
