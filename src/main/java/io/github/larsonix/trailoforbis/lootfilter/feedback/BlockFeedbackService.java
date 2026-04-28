package io.github.larsonix.trailoforbis.lootfilter.feedback;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.lootfilter.config.LootFilterConfig;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Provides player feedback when loot filter blocks an item pickup.
 *
 * <p>Supports two detail modes:
 * <ul>
 *   <li><b>every</b>: Immediate per-item message</li>
 *   <li><b>summary</b>: Periodic count (e.g., "Blocked 12 items")</li>
 * </ul>
 */
public final class BlockFeedbackService {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final LootFilterConfig.FeedbackConfig feedbackConfig;
    private final ConcurrentHashMap<UUID, AtomicInteger> blockCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> lastSummaryTime = new ConcurrentHashMap<>();

    public BlockFeedbackService(@Nonnull LootFilterConfig.FeedbackConfig feedbackConfig) {
        this.feedbackConfig = feedbackConfig;
    }

    /**
     * Called when a pickup is blocked by the filter.
     */
    public void onItemBlocked(@Nonnull UUID playerId, @Nonnull GearData gearData,
                              @Nonnull PlayerRef playerRef) {
        if ("none".equals(feedbackConfig.getMode())) return;

        if ("every".equals(feedbackConfig.getDetail())) {
            String msg = formatBlockMessage(gearData);
            sendFeedback(playerRef, msg);
        } else {
            blockCounts.computeIfAbsent(playerId, k -> new AtomicInteger(0)).incrementAndGet();
            maybeSendSummary(playerId, playerRef);
        }
    }

    /**
     * Clean up state on player disconnect.
     */
    public void onPlayerDisconnect(@Nonnull UUID playerId) {
        blockCounts.remove(playerId);
        lastSummaryTime.remove(playerId);
    }

    private void maybeSendSummary(UUID playerId, PlayerRef playerRef) {
        long now = System.currentTimeMillis();
        long last = lastSummaryTime.getOrDefault(playerId, 0L);
        int intervalMs = feedbackConfig.getSummaryInterval() * 1000;

        if (now - last >= intervalMs) {
            AtomicInteger counter = blockCounts.get(playerId);
            if (counter != null) {
                int count = counter.getAndSet(0);
                if (count > 0) {
                    sendFeedback(playerRef, "[Filter] Blocked " + count + " item" + (count > 1 ? "s" : ""));
                    lastSummaryTime.put(playerId, now);
                }
            }
        }
    }

    private void sendFeedback(PlayerRef playerRef, String message) {
        try {
            playerRef.sendMessage(Message.raw(message).color(MessageColors.GRAY));
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to send filter feedback");
        }
    }

    private String formatBlockMessage(GearData gearData) {
        return "[Filter] Blocked: " + gearData.rarity().name() + " Lv" + gearData.level()
                + " (Q" + gearData.quality() + ")";
    }
}
