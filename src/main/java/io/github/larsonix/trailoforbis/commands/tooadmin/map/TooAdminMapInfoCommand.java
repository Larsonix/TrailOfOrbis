package io.github.larsonix.trailoforbis.commands.tooadmin.map;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.commands.tooadmin.HeldItemHelper;
import io.github.larsonix.trailoforbis.maps.core.RealmMapData;
import io.github.larsonix.trailoforbis.maps.items.RealmMapUtils;
import io.github.larsonix.trailoforbis.maps.modifiers.RealmModifier;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;
import java.util.Optional;

/**
 * Shows information about the held realm map's data.
 *
 * <p>Usage: /tooadmin map info
 *
 * <p>Displays all map properties including:
 * <ul>
 *   <li>Level, rarity, quality</li>
 *   <li>Biome, size, shape</li>
 *   <li>Prefixes (difficulty modifiers)</li>
 *   <li>Suffixes (reward modifiers)</li>
 *   <li>Corruption and identification state</li>
 * </ul>
 */
public class TooAdminMapInfoCommand extends AbstractPlayerCommand {

    private final TrailOfOrbis plugin;

    public TooAdminMapInfoCommand(TrailOfOrbis plugin) {
        super("info", "Show held map's data");
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
        ItemStack heldItem = HeldItemHelper.getHeldItem(sender, store, ref);
        if (heldItem == null || heldItem.isEmpty()) {
            sender.sendMessage(Message.raw("You must hold an item !").color(MessageColors.ERROR));
            return;
        }

        Optional<RealmMapData> mapDataOpt = RealmMapUtils.readMapData(heldItem);
        if (mapDataOpt.isEmpty()) {
            sender.sendMessage(Message.raw("This item is not a realm map.").color(MessageColors.ERROR));
            return;
        }

        RealmMapData mapData = mapDataOpt.get();

        // Header
        sender.sendMessage(Message.raw("=== Realm Map Info ===").color(MessageColors.GOLD));

        // Basic stats
        sender.sendMessage(Message.empty()
            .insert(Message.raw("Level : ").color(MessageColors.GRAY))
            .insert(Message.raw(String.valueOf(mapData.level())).color(MessageColors.WHITE)));

        sender.sendMessage(Message.empty()
            .insert(Message.raw("Rarity : ").color(MessageColors.GRAY))
            .insert(Message.raw(mapData.rarity().name()).color(mapData.rarity().getHexColor())));

        sender.sendMessage(Message.empty()
            .insert(Message.raw("Quality : ").color(MessageColors.GRAY))
            .insert(Message.raw(mapData.quality() + "%").color(MessageColors.WHITE))
            .insert(Message.raw(mapData.isPerfectQuality() ? " (PERFECT)" : "").color(MessageColors.GOLD)));

        // Layout info
        sender.sendMessage(Message.empty()
            .insert(Message.raw("Biome : ").color(MessageColors.GRAY))
            .insert(Message.raw(mapData.biome().getDisplayName()).color(MessageColors.WHITE)));

        sender.sendMessage(Message.empty()
            .insert(Message.raw("Size : ").color(MessageColors.GRAY))
            .insert(Message.raw(mapData.size().getDisplayName()).color(MessageColors.WHITE)));

        sender.sendMessage(Message.empty()
            .insert(Message.raw("Shape : ").color(MessageColors.GRAY))
            .insert(Message.raw(mapData.shape().name()).color(MessageColors.WHITE)));

        // Status flags
        sender.sendMessage(Message.empty()
            .insert(Message.raw("Identified : ").color(MessageColors.GRAY))
            .insert(Message.raw(mapData.identified() ? "Yes" : "No").color(mapData.identified() ? MessageColors.SUCCESS : MessageColors.ERROR)));

        sender.sendMessage(Message.empty()
            .insert(Message.raw("Corrupted : ").color(MessageColors.GRAY))
            .insert(Message.raw(mapData.corrupted() ? "Yes" : "No").color(mapData.corrupted() ? MessageColors.ERROR : MessageColors.SUCCESS)));

        // Prefixes (difficulty modifiers)
        if (!mapData.prefixes().isEmpty()) {
            sender.sendMessage(Message.raw("Prefixes (" + mapData.prefixes().size() + ") :").color(MessageColors.GRAY));
            for (RealmModifier mod : mapData.prefixes()) {
                sender.sendMessage(Message.empty()
                    .insert(Message.raw("  - ").color(MessageColors.GRAY))
                    .insert(Message.raw(mod.displayName()).color(MessageColors.WHITE))
                    .insert(Message.raw(" [" + mod.type().name() + " : " + mod.value() + "]").color(MessageColors.GRAY)));
            }
        } else {
            sender.sendMessage(Message.raw("Prefixes : (none)").color(MessageColors.GRAY));
        }

        // Suffixes (reward modifiers)
        if (!mapData.suffixes().isEmpty()) {
            sender.sendMessage(Message.raw("Suffixes (" + mapData.suffixes().size() + ") :").color(MessageColors.GRAY));
            for (RealmModifier mod : mapData.suffixes()) {
                sender.sendMessage(Message.empty()
                    .insert(Message.raw("  - ").color(MessageColors.GRAY))
                    .insert(Message.raw(mod.displayName()).color(MessageColors.WHITE))
                    .insert(Message.raw(" [" + mod.type().name() + " : " + mod.value() + "]").color(MessageColors.GRAY)));
            }
        } else {
            sender.sendMessage(Message.raw("Suffixes : (none)").color(MessageColors.GRAY));
        }

        // Instance ID
        if (mapData.instanceId() != null) {
            sender.sendMessage(Message.empty()
                .insert(Message.raw("Instance ID : ").color(MessageColors.GRAY))
                .insert(Message.raw(mapData.instanceId().toItemId()).color(MessageColors.GRAY)));
        }
    }

}
