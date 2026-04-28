package io.github.larsonix.trailoforbis.leveling;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.util.EventTitleUtil;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import io.github.larsonix.trailoforbis.leveling.config.LevelingConfig;
import io.github.larsonix.trailoforbis.util.EmoteCelebrationHelper;
import io.github.larsonix.trailoforbis.util.MessageColors;
import io.github.larsonix.trailoforbis.util.PlayerWorldCache;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Handles level-up celebrations: fullscreen banners, sound effects,
 * rich chat breakdowns, and toast notifications.
 *
 * <p>Each level-up is classified into a milestone tier (Normal, Minor, Major, Huge)
 * which controls the intensity of the feedback. Sound indexes are cached at
 * construction time for zero-allocation playback.
 */
public final class LevelUpCelebrationService {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Milestone tiers, from least to most dramatic. */
    enum MilestoneTier { NORMAL, MINOR, MAJOR, HUGE }

    // ── Config ──────────────────────────────────────────────────────
    private final LevelingConfig.CelebrationConfig config;

    // ── Cached sound indexes (resolved once at startup) ─────────────
    private final int soundNormal;
    private final int soundMinor;
    private final int soundMajor;
    private final int soundHuge;

    // ── Emote celebration helper ─────────────────────────────────────
    private final EmoteCelebrationHelper emoteHelper;

    /**
     * Creates a new celebration service.
     *
     * <p>Resolves sound indexes eagerly so level-up playback is allocation-free.
     * Invalid sound IDs log a warning but don't prevent construction.
     *
     * @param config The celebration config from leveling.yml
     */
    public LevelUpCelebrationService(@Nonnull LevelingConfig.CelebrationConfig config) {
        this.config = config;

        // Resolve sound indexes at startup
        LevelingConfig.SoundConfig sound = config.getSound();
        this.soundNormal = resolveSoundIndex(sound.getNormal(), "normal");
        this.soundMinor = resolveSoundIndex(sound.getMinor(), "minor");
        this.soundMajor = resolveSoundIndex(sound.getMajor(), "major");
        this.soundHuge = resolveSoundIndex(sound.getHuge(), "huge");

        // Resolve emote at startup
        this.emoteHelper = new EmoteCelebrationHelper(
            config.getEmote().getEmoteId(), "level-up");
    }

    /**
     * Celebrates a player leveling up with banner, sound, chat breakdown, and toast.
     *
     * <p>All feedback is sent directly to the player via packets.
     * Safe to call from any thread (packet writes are thread-safe).
     *
     * @param playerId           The player's UUID
     * @param oldLevel           Previous level
     * @param newLevel           New level achieved
     * @param attrPointsGranted  Attribute points granted this level-up
     * @param skillPointsGranted Skill points granted this level-up
     * @param totalAttrAvailable Total unspent attribute points after granting
     * @param totalSkillAvailable Total unspent skill points after granting
     */
    public void celebrate(@Nonnull UUID playerId, int oldLevel, int newLevel,
                          int attrPointsGranted, int skillPointsGranted,
                          int totalAttrAvailable, int totalSkillAvailable) {
        PlayerRef playerRef = PlayerWorldCache.findPlayerRef(playerId);
        if (playerRef == null) {
            LOGGER.atWarning().log("Player %s not found — skipping level-up celebration", playerId);
            return;
        }

        MilestoneTier tier = classifyMilestone(newLevel);

        if (config.getBanner().isEnabled()) {
            showBanner(playerRef, newLevel, tier);
        }

        if (config.getSound().isEnabled()) {
            playSound(playerRef, tier);
        }

        if (config.getChat().isEnabled()) {
            sendChatBreakdown(playerRef, oldLevel, newLevel,
                    attrPointsGranted, skillPointsGranted,
                    totalAttrAvailable, totalSkillAvailable, tier);
        }

        // Toast notification (always — lightweight, stacks with banner)
        sendToast(playerRef, newLevel, attrPointsGranted, skillPointsGranted);

        // Emote celebration (if configured)
        if (emoteHelper.isEnabled()) {
            emoteHelper.playEmote(playerRef);
        }

        LOGGER.atFine().log("Level-up celebration sent to %s: Lv%d → Lv%d (%s)",
                playerId.toString().substring(0, 8), oldLevel, newLevel, tier);
    }

    // ═══════════════════════════════════════════════════════════════
    // MILESTONE DETECTION
    // ═══════════════════════════════════════════════════════════════

    private MilestoneTier classifyMilestone(int level) {
        LevelingConfig.MilestoneConfig ms = config.getMilestones();
        if (ms.getHuge() > 0 && level % ms.getHuge() == 0) return MilestoneTier.HUGE;
        if (ms.getMajor() > 0 && level % ms.getMajor() == 0) return MilestoneTier.MAJOR;
        if (ms.getMinor() > 0 && level % ms.getMinor() == 0) return MilestoneTier.MINOR;
        return MilestoneTier.NORMAL;
    }

    // ═══════════════════════════════════════════════════════════════
    // BANNER
    // ═══════════════════════════════════════════════════════════════

    private void showBanner(PlayerRef playerRef, int newLevel, MilestoneTier tier) {
        LevelingConfig.BannerTiming timing = getBannerTiming(tier);
        boolean isMajor = (tier == MilestoneTier.MAJOR || tier == MilestoneTier.HUGE);

        // Primary title: "LEVEL 50" in gold
        Message title = Message.raw("LEVEL " + newLevel).color(MessageColors.LEVEL_UP).bold(true);

        // Subtitle varies by tier
        Message subtitle = buildBannerSubtitle(tier);

        try {
            EventTitleUtil.showEventTitleToPlayer(
                    playerRef, title, subtitle, isMajor,
                    null,
                    timing.getDuration(),
                    timing.getFadeIn(),
                    timing.getFadeOut()
            );
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to show level-up banner");
        }
    }

    private Message buildBannerSubtitle(MilestoneTier tier) {
        return switch (tier) {
            case HUGE -> Message.raw("MAJOR MILESTONE!").color("#FF55FF").bold(true);
            case MAJOR -> Message.raw("Milestone Reached!").color(MessageColors.SUCCESS);
            case MINOR -> Message.raw("Keep going!").color(MessageColors.INFO);
            case NORMAL -> Message.raw("").color(MessageColors.WHITE);
        };
    }

    private LevelingConfig.BannerTiming getBannerTiming(MilestoneTier tier) {
        LevelingConfig.BannerConfig banner = config.getBanner();
        return switch (tier) {
            case HUGE -> banner.getHuge();
            case MAJOR -> banner.getMajor();
            case MINOR -> banner.getMinor();
            case NORMAL -> banner.getNormal();
        };
    }

    // ═══════════════════════════════════════════════════════════════
    // SOUND
    // ═══════════════════════════════════════════════════════════════

    private void playSound(PlayerRef playerRef, MilestoneTier tier) {
        int soundIndex = switch (tier) {
            case HUGE -> soundHuge;
            case MAJOR -> soundMajor;
            case MINOR -> soundMinor;
            case NORMAL -> soundNormal;
        };

        if (soundIndex == 0) return; // Sound not resolved — already warned at startup

        try {
            SoundUtil.playSoundEvent2dToPlayer(playerRef, soundIndex, SoundCategory.UI);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to play level-up sound");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // CHAT BREAKDOWN
    // ═══════════════════════════════════════════════════════════════

    private void sendChatBreakdown(PlayerRef playerRef, int oldLevel, int newLevel,
                                   int attrPoints, int skillPoints,
                                   int totalAttr, int totalSkill,
                                   MilestoneTier tier) {
        int levelsGained = newLevel - oldLevel;
        boolean showTotals = config.getChat().isShowTotals();
        boolean showBorders = config.getChat().isShowBorders();

        Message msg = Message.empty();

        // Header border
        if (showBorders) {
            msg = msg.insert(Message.raw("======= LEVEL UP =======\n").color(MessageColors.LEVEL_UP));
        }

        // Level line: "  Level 49 → 50"
        msg = msg
                .insert(Message.raw("  Level ").color(MessageColors.WHITE))
                .insert(Message.raw(String.valueOf(oldLevel)).color(MessageColors.GRAY))
                .insert(Message.raw(" > ").color(MessageColors.WHITE))
                .insert(Message.raw(String.valueOf(newLevel)).color(MessageColors.LEVEL_UP).bold(true));

        if (levelsGained > 1) {
            msg = msg.insert(Message.raw(" (+" + levelsGained + " levels!)").color(MessageColors.WARNING));
        }
        msg = msg.insert(Message.raw("\n").color(MessageColors.WHITE));

        // Attribute points line
        if (attrPoints > 0) {
            msg = msg
                    .insert(Message.raw("  +" + attrPoints).color(MessageColors.SUCCESS))
                    .insert(Message.raw(" Attribute Point" + (attrPoints != 1 ? "s" : "")).color(MessageColors.WHITE));
            if (showTotals) {
                msg = msg.insert(Message.raw(" (" + totalAttr + " available)").color(MessageColors.GRAY));
            }
            msg = msg.insert(Message.raw("\n").color(MessageColors.WHITE));
        }

        // Skill points line
        if (skillPoints > 0) {
            msg = msg
                    .insert(Message.raw("  +" + skillPoints).color(MessageColors.SUCCESS))
                    .insert(Message.raw(" Skill Point" + (skillPoints != 1 ? "s" : "")).color(MessageColors.WHITE));
            if (showTotals) {
                msg = msg.insert(Message.raw(" (" + totalSkill + " available)").color(MessageColors.GRAY));
            }
            msg = msg.insert(Message.raw("\n").color(MessageColors.WHITE));
        }

        // Milestone line
        if (tier == MilestoneTier.HUGE) {
            msg = msg.insert(Message.raw("  * MAJOR MILESTONE *\n").color("#FF55FF").bold(true));
        } else if (tier == MilestoneTier.MAJOR) {
            msg = msg.insert(Message.raw("  * Milestone Reached!\n").color(MessageColors.SUCCESS));
        }

        // Footer border
        if (showBorders) {
            msg = msg.insert(Message.raw("=========================").color(MessageColors.LEVEL_UP));
        }

        playerRef.sendMessage(msg);
    }

    // ═══════════════════════════════════════════════════════════════
    // TOAST NOTIFICATION
    // ═══════════════════════════════════════════════════════════════

    private void sendToast(PlayerRef playerRef, int newLevel,
                           int attrPoints, int skillPoints) {
        try {
            Message title = Message.raw("Level Up!").color(MessageColors.LEVEL_UP);

            Message subtitle = Message.empty()
                    .insert(Message.raw("Level ").color(MessageColors.WHITE))
                    .insert(Message.raw(String.valueOf(newLevel)).color(MessageColors.LEVEL_UP));

            if (attrPoints > 0 || skillPoints > 0) {
                StringBuilder gains = new StringBuilder(" (");
                if (attrPoints > 0) {
                    gains.append("+").append(attrPoints).append(" Attr");
                }
                if (attrPoints > 0 && skillPoints > 0) {
                    gains.append(", ");
                }
                if (skillPoints > 0) {
                    gains.append("+").append(skillPoints).append(" Skill");
                }
                gains.append(")");
                subtitle = subtitle.insert(Message.raw(gains.toString()).color(MessageColors.GRAY));
            }

            NotificationUtil.sendNotification(
                    playerRef.getPacketHandler(),
                    title,
                    subtitle,
                    NotificationStyle.Success
            );
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Toast notification failed");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // SOUND RESOLUTION
    // ═══════════════════════════════════════════════════════════════

    private static int resolveSoundIndex(String soundId, String tierName) {
        if (soundId == null || soundId.isBlank()) {
            LOGGER.atWarning().log("No sound configured for %s tier level-up", tierName);
            return 0;
        }
        int index = SoundEvent.getAssetMap().getIndex(soundId);
        if (index == 0) {
            LOGGER.atWarning().log("Sound '%s' not found for %s tier level-up — sound will be skipped", soundId, tierName);
        } else {
            LOGGER.atFine().log("Resolved %s level-up sound: %s → index %d", tierName, soundId, index);
        }
        return index;
    }
}
