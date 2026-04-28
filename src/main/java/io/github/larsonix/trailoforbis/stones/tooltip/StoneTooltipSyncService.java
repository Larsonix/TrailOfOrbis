package io.github.larsonix.trailoforbis.stones.tooltip;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.UpdateType;
import com.hypixel.hytale.protocol.packets.assets.UpdateTranslations;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import io.github.larsonix.trailoforbis.stones.StoneType;
import io.github.larsonix.trailoforbis.util.MessageSerializer;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sends rich stone description translations to players on connect.
 *
 * <p>Stone items use native Hytale item JSONs with translation keys in their
 * {@code TranslationProperties.Description} field. This service pre-builds
 * all 22 stone descriptions as Hytale-formatted text and sends them via
 * {@code UpdateTranslations} packets when a player is ready.
 *
 * <p>Since stone descriptions are static (every "Gaia's Calibration" is identical),
 * the translations are built once at construction time and reused for all players.
 *
 * <h2>Thread Safety</h2>
 * <p>All public methods are thread-safe. The pre-built translations map is
 * immutable after construction. The synced players set uses ConcurrentHashMap.
 */
public final class StoneTooltipSyncService {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Pre-built translations for all stone types (key → formatted text). */
    private final Map<String, String> stoneTranslations;

    /** Tracks which players have already received translations. */
    private final Set<UUID> syncedPlayers = ConcurrentHashMap.newKeySet();

    /**
     * Creates the service and pre-builds all stone description translations.
     */
    public StoneTooltipSyncService() {
        StoneTooltipBuilder tooltipBuilder = new StoneTooltipBuilder();
        Map<String, String> translations = new HashMap<>();

        for (StoneType type : StoneType.values()) {
            Message descTooltip = tooltipBuilder.buildDescription(type);
            String formattedText = MessageSerializer.toFormattedText(descTooltip);
            String key = type.getDescriptionTranslationKey();
            translations.put(key, formattedText);
        }

        this.stoneTranslations = Map.copyOf(translations);
        LOGGER.atInfo().log("Pre-built %d stone tooltip translations", stoneTranslations.size());
    }

    /**
     * Sends stone tooltip translations to a player if not already sent.
     *
     * <p>Should be called on {@code PlayerReadyEvent}. Skips if translations
     * were already sent (e.g., on world transition where PlayerReadyEvent
     * fires again).
     *
     * @param playerRef The player to send translations to
     */
    public void syncToPlayer(@Nonnull PlayerRef playerRef) {
        UUID uuid = playerRef.getUuid();
        if (!syncedPlayers.add(uuid)) {
            return; // Already sent
        }

        try {
            UpdateTranslations packet = new UpdateTranslations();
            packet.type = UpdateType.AddOrUpdate;
            packet.translations = new HashMap<>(stoneTranslations);
            playerRef.getPacketHandler().writeNoCache(packet);

            LOGGER.atFine().log("Sent %d stone translations to player %s",
                    stoneTranslations.size(), uuid);
        } catch (Exception e) {
            // Remove from synced so it retries on next world entry
            syncedPlayers.remove(uuid);
            LOGGER.atWarning().withCause(e).log(
                    "Failed to send stone translations to player %s", uuid);
        }
    }

    /**
     * Cleans up tracking state for a disconnected player.
     *
     * @param playerId The player's UUID
     */
    public void onPlayerDisconnect(@Nonnull UUID playerId) {
        syncedPlayers.remove(playerId);
    }
}
