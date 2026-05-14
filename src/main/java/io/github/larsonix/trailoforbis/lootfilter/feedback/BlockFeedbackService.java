package io.github.larsonix.trailoforbis.lootfilter.feedback;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.maps.core.RealmMapData;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Provides player feedback when loot filter blocks item pickups.
 *
 * <p>Uses Hytale's native notification system (toast popups) instead of
 * chat messages. Notifications are coalesced — multiple blocked items within
 * a short window produce a single notification with the total count and the
 * highest rarity seen.
 *
 * <h2>Coalescing</h2>
 * <p>When an item is blocked, a flush is scheduled after a short delay
 * (500ms). All blocks within that window accumulate into a single counter.
 * The notification fires once with "Blocked 3 items" rather than three
 * separate "Blocked 1 item" messages.
 */
public final class BlockFeedbackService {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Delay before flushing accumulated blocks into a single notification. */
    private static final long COALESCE_DELAY_MS = 500;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "loot-filter-feedback");
        t.setDaemon(true);
        return t;
    });

    /** Per-player pending block state — accumulates between coalesce windows. */
    private final ConcurrentHashMap<UUID, PendingBlocks> pendingBlocks = new ConcurrentHashMap<>();

    public BlockFeedbackService() {
    }

    /**
     * Called when a pickup is blocked by the filter.
     */
    public void onItemBlocked(@Nonnull UUID playerId, @Nonnull GearData gearData,
                              @Nonnull PlayerRef playerRef) {
        PendingBlocks pending = pendingBlocks.computeIfAbsent(playerId, k -> new PendingBlocks());

        boolean wasEmpty = pending.count == 0;
        pending.count++;
        if (pending.highestRarity == null || gearData.rarity().ordinal() > pending.highestRarity.ordinal()) {
            pending.highestRarity = gearData.rarity();
        }

        // Schedule a flush only on the first block in a new window
        if (wasEmpty) {
            try {
                scheduler.schedule(() -> flush(playerId), COALESCE_DELAY_MS, TimeUnit.MILLISECONDS);
            } catch (java.util.concurrent.RejectedExecutionException e) {
                // Scheduler shut down during plugin disable
            }
        }
    }

    /**
     * Called when a map pickup is blocked by the filter.
     * Shares the same coalescing window as gear blocks.
     */
    public void onMapBlocked(@Nonnull UUID playerId, @Nonnull RealmMapData mapData,
                             @javax.annotation.Nullable PlayerRef playerRef) {
        PendingBlocks pending = pendingBlocks.computeIfAbsent(playerId, k -> new PendingBlocks());

        boolean wasEmpty = pending.count == 0;
        pending.count++;
        if (pending.highestRarity == null || mapData.rarity().ordinal() > pending.highestRarity.ordinal()) {
            pending.highestRarity = mapData.rarity();
        }

        if (wasEmpty) {
            try {
                scheduler.schedule(() -> flush(playerId), COALESCE_DELAY_MS, TimeUnit.MILLISECONDS);
            } catch (java.util.concurrent.RejectedExecutionException e) {
                // Scheduler shut down during plugin disable
            }
        }
    }

    /**
     * Flushes accumulated blocks into a single notification.
     */
    private void flush(@Nonnull UUID playerId) {
        PendingBlocks pending = pendingBlocks.remove(playerId);
        if (pending == null || pending.count == 0) return;

        PlayerRef playerRef = getPlayerRef(playerId);
        if (playerRef == null) return;

        try {
            int count = pending.count;
            GearRarity highest = pending.highestRarity;

            // Primary: "Loot Filter" in gold
            Message title = Message.raw("Loot Filter").color("#FFD700").bold(true);

            // Secondary: "Blocked 3 items (up to Rare)"
            String countText = "Blocked " + count + " item" + (count > 1 ? "s" : "");
            Message subtitle = Message.raw(countText).color("#D0DCEA");

            if (highest != null && count > 1) {
                subtitle = subtitle
                    .insert(Message.raw(" (up to ").color("#96A9BE"))
                    .insert(Message.raw(formatRarityName(highest)).color(getRarityColor(highest)))
                    .insert(Message.raw(")").color("#96A9BE"));
            } else if (highest != null) {
                subtitle = subtitle
                    .insert(Message.raw(" (").color("#96A9BE"))
                    .insert(Message.raw(formatRarityName(highest)).color(getRarityColor(highest)))
                    .insert(Message.raw(")").color("#96A9BE"));
            }

            NotificationUtil.sendNotification(
                playerRef.getPacketHandler(),
                title,
                subtitle,
                NotificationStyle.Warning
            );
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to send filter notification to %s",
                playerId.toString().substring(0, 8));
        }
    }

    /**
     * Clean up state on player disconnect.
     */
    public void onPlayerDisconnect(@Nonnull UUID playerId) {
        pendingBlocks.remove(playerId);
    }

    /**
     * Shutdown the scheduler on plugin disable.
     */
    public void shutdown() {
        scheduler.shutdownNow();
        pendingBlocks.clear();
    }

    @javax.annotation.Nullable
    private static PlayerRef getPlayerRef(@Nonnull UUID playerId) {
        try {
            return Universe.get().getPlayer(playerId);
        } catch (Exception e) {
            return null;
        }
    }

    @Nonnull
    private static String getRarityColor(@Nonnull GearRarity rarity) {
        return rarity.getHexColor();
    }

    @Nonnull
    private static String formatRarityName(@Nonnull GearRarity rarity) {
        String name = rarity.name();
        return name.charAt(0) + name.substring(1).toLowerCase();
    }

    /** Mutable accumulator for a coalesce window. */
    private static final class PendingBlocks {
        volatile int count;
        volatile GearRarity highestRarity;
    }
}
