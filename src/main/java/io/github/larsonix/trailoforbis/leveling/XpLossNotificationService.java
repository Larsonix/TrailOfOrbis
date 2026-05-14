package io.github.larsonix.trailoforbis.leveling;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import io.github.larsonix.trailoforbis.leveling.api.LevelingEvents;
import io.github.larsonix.trailoforbis.leveling.config.LevelingConfig;
import io.github.larsonix.trailoforbis.leveling.xp.XpSource;
import io.github.larsonix.trailoforbis.util.MessageColors;
import io.github.larsonix.trailoforbis.util.PlayerWorldCache;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Sends XP loss toast notifications to players via the leveling event system.
 *
 * <p>Registered as an {@link LevelingEvents.XpLossListener}, this service fires
 * for every {@code removeXp()} call — primarily death penalties. Each player
 * sees a toast with the amount lost and a source-aware suffix.
 *
 * @see XpGainNotificationService
 */
public final class XpLossNotificationService implements LevelingEvents.XpLossListener {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final LevelingConfig config;

    public XpLossNotificationService(@Nonnull LevelingConfig config) {
        this.config = config;
    }

    @Override
    public void onXpLoss(@Nonnull UUID playerId, long amount, @Nonnull XpSource source, long totalXp) {
        if (!config.getUi().isShowXpGainNotification()) {
            return;
        }

        // Admin commands already show feedback via the command response
        if (source == XpSource.ADMIN_COMMAND) {
            return;
        }

        PlayerRef playerRef = PlayerWorldCache.findPlayerRef(playerId);
        if (playerRef == null) {
            return;
        }

        String suffix = getSourceSuffix(source);
        String text = suffix.isEmpty()
            ? "-" + amount + " XP"
            : "-" + amount + " XP (" + suffix + ")";

        try {
            NotificationUtil.sendNotification(
                playerRef.getPacketHandler(),
                Message.raw(text).color(MessageColors.XP_LOSS),
                NotificationStyle.Default
            );
        } catch (Exception e) {
            LOGGER.atFine().log("Failed to send XP loss notification to %s", playerId.toString().substring(0, 8));
        }
    }

    private static String getSourceSuffix(@Nonnull XpSource source) {
        return switch (source) {
            case DEATH_PENALTY -> "Death Penalty";
            default -> "";
        };
    }
}
