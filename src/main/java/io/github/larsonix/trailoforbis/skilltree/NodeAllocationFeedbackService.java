package io.github.larsonix.trailoforbis.skilltree;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.util.EventTitleUtil;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.skilltree.config.NodeAllocationFeedbackConfig;
import io.github.larsonix.trailoforbis.skilltree.config.SkillNode;
import io.github.larsonix.trailoforbis.util.MessageColors;
import io.github.larsonix.trailoforbis.util.PlayerWorldCache;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Provides rich, tiered feedback when players allocate or deallocate skill nodes.
 *
 * <p>Mirrors {@code LevelUpCelebrationService}: banner, sound, chat breakdown with
 * before/after stat diffs, and toast notifications. Feedback intensity is tiered
 * by node type (BASIC, NOTABLE, KEYSTONE).
 *
 * <p>Banner is intentionally skipped for BASIC nodes to avoid spam when players
 * allocate many basic nodes in rapid succession.
 */
public final class NodeAllocationFeedbackService {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Node tier classification for feedback intensity. */
    enum NodeTier { BASIC, NOTABLE, KEYSTONE }

    // ── Config ──────────────────────────────────────────────────────
    private final NodeAllocationFeedbackConfig config;

    // ── Cached sound indexes (resolved once at startup) ─────────────
    private final int soundBasic;
    private final int soundNotable;
    private final int soundKeystone;

    /**
     * Creates a new feedback service.
     *
     * <p>Resolves sound indexes eagerly so playback is allocation-free.
     *
     * @param config The feedback config from skill-tree.yml
     */
    public NodeAllocationFeedbackService(@Nonnull NodeAllocationFeedbackConfig config) {
        this.config = config;

        NodeAllocationFeedbackConfig.SoundConfig sound = config.getSound();
        this.soundBasic = resolveSoundIndex(sound.getBasic(), "basic");
        this.soundNotable = resolveSoundIndex(sound.getNotable(), "notable");
        this.soundKeystone = resolveSoundIndex(sound.getKeystone(), "keystone");
    }

    // ═══════════════════════════════════════════════════════════════
    // PUBLIC API — event listener entry points
    // ═══════════════════════════════════════════════════════════════

    /**
     * Called when a player allocates a node. Sends tiered feedback.
     */
    public void onNodeAllocated(@Nonnull UUID playerId, @Nonnull SkillNode node,
                                 int pointsRemaining,
                                 @Nullable ComputedStats statsBefore,
                                 @Nullable ComputedStats statsAfter) {
        PlayerRef playerRef = PlayerWorldCache.findPlayerRef(playerId);
        if (playerRef == null) return;

        NodeTier tier = classifyTier(node);
        String themeColor = node.getSkillTreeRegion().getThemeColor();

        // Banner (skip for BASIC — too spammy)
        if (config.getBanner().isEnabled() && tier != NodeTier.BASIC) {
            showBanner(playerRef, node, tier, themeColor, false);
        }

        if (config.getSound().isEnabled()) {
            playSound(playerRef, tier);
        }

        if (config.getToast().isEnabled()) {
            sendToast(playerRef, node, false, pointsRemaining);
        }

        LOGGER.atFine().log("Allocation feedback sent: %s -> %s (%s)",
                playerId.toString().substring(0, 8), node.getName(), tier);
    }

    /**
     * Called when a player deallocates a node. Sends reversed feedback.
     */
    public void onNodeDeallocated(@Nonnull UUID playerId, @Nonnull SkillNode node,
                                   int pointsRefunded, int pointsRemaining,
                                   int refundPointsRemaining,
                                   @Nullable ComputedStats statsBefore,
                                   @Nullable ComputedStats statsAfter) {
        PlayerRef playerRef = PlayerWorldCache.findPlayerRef(playerId);
        if (playerRef == null) return;

        NodeTier tier = classifyTier(node);
        String themeColor = node.getSkillTreeRegion().getThemeColor();

        // Banner (skip for BASIC)
        if (config.getBanner().isEnabled() && tier != NodeTier.BASIC) {
            showBanner(playerRef, node, tier, themeColor, true);
        }

        if (config.getSound().isEnabled()) {
            playSound(playerRef, tier);
        }

        if (config.getToast().isEnabled()) {
            sendToast(playerRef, node, true, refundPointsRemaining);
        }

        LOGGER.atFine().log("Deallocation feedback sent: %s -> %s (%s)",
                playerId.toString().substring(0, 8), node.getName(), tier);
    }

    // ═══════════════════════════════════════════════════════════════
    // TIER CLASSIFICATION
    // ═══════════════════════════════════════════════════════════════

    private NodeTier classifyTier(SkillNode node) {
        if (node.isKeystone()) return NodeTier.KEYSTONE;
        if (node.isNotable()) return NodeTier.NOTABLE;
        return NodeTier.BASIC;
    }

    // ═══════════════════════════════════════════════════════════════
    // BANNER
    // ═══════════════════════════════════════════════════════════════

    private void showBanner(PlayerRef playerRef, SkillNode node, NodeTier tier,
                            String themeColor, boolean isDeallocation) {
        NodeAllocationFeedbackConfig.BannerTiming timing = getBannerTiming(tier);
        boolean isMajor = (tier == NodeTier.KEYSTONE);

        String verb = isDeallocation ? "Removed" : "Allocated";
        Message title = Message.raw(node.getName()).color(themeColor).bold(true);
        Message subtitle = Message.raw(verb).color(isDeallocation ? MessageColors.ERROR : MessageColors.SUCCESS);

        try {
            EventTitleUtil.showEventTitleToPlayer(
                    playerRef, title, subtitle, isMajor,
                    null,
                    timing.getDuration(),
                    timing.getFadeIn(),
                    timing.getFadeOut()
            );
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to show node allocation banner");
        }
    }

    private NodeAllocationFeedbackConfig.BannerTiming getBannerTiming(NodeTier tier) {
        NodeAllocationFeedbackConfig.BannerConfig banner = config.getBanner();
        return switch (tier) {
            case KEYSTONE -> banner.getKeystone();
            case NOTABLE -> banner.getNotable();
            case BASIC -> banner.getBasic();
        };
    }

    // ═══════════════════════════════════════════════════════════════
    // SOUND
    // ═══════════════════════════════════════════════════════════════

    private void playSound(PlayerRef playerRef, NodeTier tier) {
        int soundIndex = switch (tier) {
            case KEYSTONE -> soundKeystone;
            case NOTABLE -> soundNotable;
            case BASIC -> soundBasic;
        };

        if (soundIndex == 0) return;

        try {
            SoundUtil.playSoundEvent2dToPlayer(playerRef, soundIndex, SoundCategory.UI);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to play node allocation sound");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // TOAST NOTIFICATION
    // ═══════════════════════════════════════════════════════════════

    private void sendToast(PlayerRef playerRef, SkillNode node, boolean isDeallocation,
                           int pointsRemaining) {
        try {
            String verb = isDeallocation ? "Removed" : "Allocated";
            Message title = Message.raw(verb + ": " + node.getName())
                    .color(node.getSkillTreeRegion().getThemeColor());

            String pointsLabel = isDeallocation ? "Refund points" : "Skill points";
            Message subtitle = Message.raw(pointsLabel + ": " + pointsRemaining)
                    .color(MessageColors.GRAY);

            NotificationUtil.sendNotification(
                    playerRef.getPacketHandler(),
                    title,
                    subtitle,
                    isDeallocation ? NotificationStyle.Warning : NotificationStyle.Success
            );
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Node allocation toast failed");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // UTILITIES
    // ═══════════════════════════════════════════════════════════════

    private static int resolveSoundIndex(String soundId, String tierName) {
        if (soundId == null || soundId.isBlank()) {
            LOGGER.atWarning().log("No sound configured for %s tier node allocation", tierName);
            return 0;
        }
        int index = SoundEvent.getAssetMap().getIndex(soundId);
        if (index == 0) {
            LOGGER.atWarning().log("Sound '%s' not found for %s tier — sound will be skipped", soundId, tierName);
        } else {
            LOGGER.atFine().log("Resolved %s node sound: %s → index %d", tierName, soundId, index);
        }
        return index;
    }
}
