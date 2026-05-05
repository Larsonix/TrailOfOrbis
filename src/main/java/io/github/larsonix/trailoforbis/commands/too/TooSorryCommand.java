package io.github.larsonix.trailoforbis.commands.too;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.commands.base.OpenPlayerCommand;
import io.github.larsonix.trailoforbis.guide.GuideManager;
import io.github.larsonix.trailoforbis.guide.GuideMilestone;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Apology command for players who skipped the guide system.
 *
 * <p>Two-stage interaction:
 * <ol>
 *   <li>{@code /too sorry} — guilt trip, remembers the player tried</li>
 *   <li>{@code /too sorry iamreallysorryiloveyourguide} — accepts the apology,
 *       resets all milestones so guides show again, immediately triggers WELCOME</li>
 * </ol>
 *
 * <p>If the player skips guides again after being forgiven, they are permanently
 * banned from the guide system. No more apologies accepted.
 */
public final class TooSorryCommand extends OpenPlayerCommand {

    private static final String MAGIC_WORD = "iamreallysorryiloveyourguide";

    private final TrailOfOrbis plugin;

    /** Tracks players who have said sorry once this session (cleared on success or disconnect). */
    private final Map<UUID, Boolean> hasApologizedOnce = new ConcurrentHashMap<>();

    public TooSorryCommand(TrailOfOrbis plugin) {
        super("sorry", "Apologize for skipping the guides");
        this.plugin = plugin;
    }

    @Override
    protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef player,
            @Nonnull World world) {

        GuideManager guideManager = plugin.getGuideManager();
        if (guideManager == null) {
            player.sendMessage(Message.raw("Guide system is not available.").color("#FF6666"));
            return;
        }

        UUID playerId = player.getUuid();

        // Permanently banned — no more chances
        if (guideManager.isGuidePermanentlyBanned(playerId)) {
            player.sendMessage(
                Message.empty()
                    .insert(Message.raw("[Guide] ").color("#FFD700").bold(true))
                    .insert(Message.raw("No. You had your chance. You apologized, I forgave you, "
                        + "and you skipped everything ").color("#FF6666"))
                    .insert(Message.raw("again").color("#FF4444").bold(true))
                    .insert(Message.raw(". We're done here.").color("#FF6666"))
            );
            return;
        }

        // Check if the player actually skipped guides (WELCOME milestone is completed)
        if (!guideManager.isCompleted(playerId, "welcome")) {
            player.sendMessage(
                Message.empty()
                    .insert(Message.raw("[Guide] ").color("#FFD700").bold(true))
                    .insert(Message.raw("You haven't skipped any guides! Nothing to apologize for.").color("#D0DCEA"))
            );
            return;
        }

        String input = context.getInputString().trim();

        // Stage 2: Player provides the magic word
        if (input.equalsIgnoreCase(MAGIC_WORD)) {
            if (!hasApologizedOnce.containsKey(playerId)) {
                // They tried the magic word without apologizing first
                player.sendMessage(
                    Message.empty()
                        .insert(Message.raw("[Guide] ").color("#FFD700").bold(true))
                        .insert(Message.raw("You can't just skip to the password. Say you're sorry first.").color("#FF9999"))
                );
                return;
            }

            // Accept the apology — reset all milestones
            guideManager.resetAllMilestones(playerId);
            guideManager.markForgiven(playerId);
            hasApologizedOnce.remove(playerId);

            player.sendMessage(
                Message.empty()
                    .insert(Message.raw("[Guide] ").color("#FFD700").bold(true))
                    .insert(Message.raw("...fine. I forgive you. ").color("#AAFFAA"))
                    .insert(Message.raw("Your guides have been re-enabled. ").color("#D0DCEA"))
                    .insert(Message.raw("Don't skip them this time.").color("#FFD700"))
            );

            // Immediately trigger the WELCOME guide so they see it right away
            guideManager.tryShow(playerId, GuideMilestone.WELCOME);
            return;
        }

        // Stage 1: First apology attempt (or wrong magic word)
        if (!input.isEmpty()) {
            // They typed something but it's wrong
            player.sendMessage(
                Message.empty()
                    .insert(Message.raw("[Guide] ").color("#FFD700").bold(true))
                    .insert(Message.raw("That's... not even close. Try again.").color("#FF9999"))
            );
            return;
        }

        // Empty input — the initial "/too sorry"
        hasApologizedOnce.put(playerId, true);

        player.sendMessage(
            Message.empty()
                .insert(Message.raw("[Guide] ").color("#FFD700").bold(true))
                .insert(Message.raw("I don't feel like you're sorry. ").color("#FF9999"))
                .insert(Message.raw("My guide took a lot of time and you disregarded it. I'm sad.").color("#D0DCEA"))
        );
        player.sendMessage(
            Message.empty()
                .insert(Message.raw("[Guide] ").color("#FFD700").bold(true))
                .insert(Message.raw("If you're ").color("#D0DCEA"))
                .insert(Message.raw("really").color("#FFAA00").bold(true))
                .insert(Message.raw(" sorry, type: ").color("#D0DCEA"))
                .insert(Message.raw("/too sorry iamreallysorryiloveyourguide").color("#55FFFF"))
        );
    }

    /**
     * Called on player disconnect to clean up transient state.
     */
    public void onPlayerDisconnect(@Nonnull UUID playerId) {
        hasApologizedOnce.remove(playerId);
    }
}
