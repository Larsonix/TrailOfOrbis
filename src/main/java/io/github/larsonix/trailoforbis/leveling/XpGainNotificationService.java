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
 * Sends XP gain toast notifications to players via the leveling event system.
 *
 * <p>Registered as an {@link LevelingEvents.XpGainListener}, this service fires
 * for every {@code addXp()} call — including party-shared XP, quest rewards,
 * realm completions, and mob kills. Each recipient sees a toast with their
 * actual XP amount and a source-aware suffix.
 *
 * <p>This replaces the hardcoded toast in {@code XpGainSystem} which only
 * notified the killer with the pre-split amount.
 *
 * @see LevelUpCelebrationService
 */
public final class XpGainNotificationService implements LevelingEvents.XpGainListener {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final LevelingConfig config;

    public XpGainNotificationService(@Nonnull LevelingConfig config) {
        this.config = config;
    }

    @Override
    public void onXpGain(@Nonnull UUID playerId, long amount, @Nonnull XpSource source, long totalXp) {
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
            ? "+" + amount + " XP"
            : "+" + amount + " XP (" + suffix + ")";

        try {
            NotificationUtil.sendNotification(
                playerRef.getPacketHandler(),
                Message.raw(text).color(MessageColors.XP_GAIN),
                NotificationStyle.Default
            );
        } catch (Exception e) {
            LOGGER.atFine().log("Failed to send XP notification to %s", playerId.toString().substring(0, 8));
        }
    }

    private static String getSourceSuffix(@Nonnull XpSource source) {
        return switch (source) {
            case MOB_KILL, REALM_KILL -> "";
            case PARTY_SHARE -> "Party";
            case REALM_COMPLETION -> "Realm";
            case QUEST_COMPLETE, QUEST_PROGRESS -> "Quest";
            case CRAFTING -> "Crafting";
            case GATHERING -> "Gathering";
            case EXPLORATION -> "Explore";
            default -> "";
        };
    }
}
