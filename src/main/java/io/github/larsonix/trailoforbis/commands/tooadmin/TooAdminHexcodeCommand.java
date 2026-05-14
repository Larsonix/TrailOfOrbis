package io.github.larsonix.trailoforbis.commands.tooadmin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.compat.HexcodeCompatManager;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;

/**
 * Admin command to display Hexcode integration status.
 *
 * <p>Usage: /tooadmin hexcode
 *
 * <p>Shows:
 * <ul>
 *   <li>Detection status and version</li>
 *   <li>Component resolution status</li>
 *   <li>Registry sizes (constructs, projectiles)</li>
 *   <li>Attribution tier hit counts (T1/T2/T3/T4)</li>
 * </ul>
 */
public class TooAdminHexcodeCommand extends AbstractPlayerCommand {

    public TooAdminHexcodeCommand() {
        super("hexcode", "Show Hexcode integration status");
    }

    @Override
    protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef sender,
            @Nonnull World world
    ) {
        try {
            HexcodeCompatManager manager = HexcodeCompatManager.get();
            String diagnostics = manager.getDiagnostics();

            // Send header
            sender.sendMessage(Message.empty()
                    .insert(Message.raw("[TooAdmin] ").color(MessageColors.GOLD))
                    .insert(Message.raw("Hexcode Integration Status").color(MessageColors.WHITE)));

            // Send each line of diagnostics
            for (String line : diagnostics.split("\n")) {
                if (line.isBlank()) continue;
                String color = MessageColors.GRAY;
                if (line.contains("ACTIVE") || line.contains("OK")) color = MessageColors.SUCCESS;
                if (line.contains("NOT LOADED") || line.contains("MISSING")) color = MessageColors.ERROR;
                if (line.contains("===")) color = MessageColors.GOLD;
                sender.sendMessage(Message.raw("  " + line).color(color));
            }
        } catch (IllegalStateException e) {
            sender.sendMessage(Message.empty()
                    .insert(Message.raw("[TooAdmin] ").color(MessageColors.GOLD))
                    .insert(Message.raw("HexcodeCompatManager not initialized").color(MessageColors.ERROR)));
        }
    }
}
