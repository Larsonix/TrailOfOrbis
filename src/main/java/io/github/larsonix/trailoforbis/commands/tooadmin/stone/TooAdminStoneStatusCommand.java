package io.github.larsonix.trailoforbis.commands.tooadmin.stone;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.maps.config.RealmsConfig;
import io.github.larsonix.trailoforbis.stones.StoneDropListener;
import io.github.larsonix.trailoforbis.stones.StoneType;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;

/**
 * Shows the stone drop system status and configuration.
 *
 * <p>Usage: /tooadmin stone status
 *
 * <p>Displays:
 * <ul>
 *   <li>Whether StoneDropListener is registered and operational</li>
 *   <li>Current drop config values (base chance, per-level, multipliers)</li>
 *   <li>Stone type count per rarity tier</li>
 * </ul>
 */
public class TooAdminStoneStatusCommand extends AbstractPlayerCommand {

    private final TrailOfOrbis plugin;

    public TooAdminStoneStatusCommand(TrailOfOrbis plugin) {
        super("status", "Show stone drop system status");
        this.plugin = plugin;
    }

    @Override
    protected void execute(
        @Nonnull CommandContext context,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull PlayerRef sender,
        @Nonnull World world
    ) {
        sender.sendMessage(Message.raw("=== Stone Drop System Status ===").color(MessageColors.GOLD));

        // Check listener registration
        StoneDropListener listener = plugin.getStoneDropListener();
        if (listener == null) {
            sender.sendMessage(Message.raw("StoneDropListener : NOT REGISTERED").color(MessageColors.ERROR));
            sender.sendMessage(Message.raw("Stone drops are completely disabled !").color(MessageColors.ERROR));
            return;
        }

        boolean operational = listener.isOperational();
        sender.sendMessage(Message.empty()
            .insert(Message.raw("StoneDropListener : ").color(MessageColors.GRAY))
            .insert(Message.raw(operational ? "OPERATIONAL" : "WAITING (deps not ready)")
                .color(operational ? MessageColors.SUCCESS : MessageColors.WARNING)));

        // Show config values
        RealmsConfig config = listener.resolveConfig();
        if (config != null) {
            sender.sendMessage(Message.raw("-- Drop Config --").color(MessageColors.GOLD));
            sender.sendMessage(Message.empty()
                .insert(Message.raw("  Base chance : ").color(MessageColors.GRAY))
                .insert(Message.raw(String.format("%.1f%%", config.getBaseStoneDropChance() * 100))
                    .color(MessageColors.WHITE)));
            sender.sendMessage(Message.empty()
                .insert(Message.raw("  Per-level bonus : ").color(MessageColors.GRAY))
                .insert(Message.raw(String.format("+%.2f%%", config.getStoneDropChancePerLevel() * 100))
                    .color(MessageColors.WHITE)));
            sender.sendMessage(Message.empty()
                .insert(Message.raw("  Elite multiplier : ").color(MessageColors.GRAY))
                .insert(Message.raw(String.format("%.1fx", config.getEliteStoneDropMultiplier()))
                    .color(MessageColors.WHITE)));
            sender.sendMessage(Message.empty()
                .insert(Message.raw("  Boss multiplier : ").color(MessageColors.GRAY))
                .insert(Message.raw(String.format("%.1fx", config.getBossStoneDropMultiplier()))
                    .color(MessageColors.WHITE)));
        } else {
            sender.sendMessage(Message.empty()
                .insert(Message.raw("  Config : ").color(MessageColors.GRAY))
                .insert(Message.raw("NOT AVAILABLE (RealmsManager not initialized)")
                    .color(MessageColors.ERROR)));
        }

        // Show rarity roller status
        sender.sendMessage(Message.empty()
            .insert(Message.raw("  RarityRoller : ").color(MessageColors.GRAY))
            .insert(Message.raw(listener.resolveRarityRoller() != null ? "Ready" : "NOT AVAILABLE")
                .color(listener.resolveRarityRoller() != null ? MessageColors.SUCCESS : MessageColors.ERROR)));

        // Show stone types per rarity
        sender.sendMessage(Message.raw("-- Stone Types by Rarity --").color(MessageColors.GOLD));
        for (GearRarity rarity : StoneType.getAvailableRarities()) {
            int count = StoneType.getByRarity(rarity).size();
            sender.sendMessage(Message.empty()
                .insert(Message.raw("  " + rarity.name() + " : ").color(rarity.getHexColor()))
                .insert(Message.raw(count + " types").color(MessageColors.WHITE)));
        }

        int totalStones = StoneType.values().length;
        sender.sendMessage(Message.empty()
            .insert(Message.raw("  Total : ").color(MessageColors.GRAY))
            .insert(Message.raw(totalStones + " stone types").color(MessageColors.WHITE)));
    }
}
