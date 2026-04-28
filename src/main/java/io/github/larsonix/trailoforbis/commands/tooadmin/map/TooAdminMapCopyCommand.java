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
import io.github.larsonix.trailoforbis.commands.tooadmin.clipboard.CopyPasteClipboard;
import io.github.larsonix.trailoforbis.maps.core.RealmMapData;
import io.github.larsonix.trailoforbis.maps.items.RealmMapUtils;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;
import java.util.Optional;

/**
 * Copies the held realm map's data to the clipboard.
 *
 * <p>Usage: /tooadmin map copy
 *
 * <p>Copies all map properties (level, rarity, quality, biome, size, shape,
 * prefixes, suffixes, corrupted, identified) to the player's clipboard.
 * The instance ID is NOT copied - the target item keeps its own instance ID.
 *
 * <p>Use {@code /tooadmin map paste} to apply the copied data to another map.
 *
 * @see TooAdminMapPasteCommand
 * @see CopyPasteClipboard
 */
public class TooAdminMapCopyCommand extends AbstractPlayerCommand {

    private final TrailOfOrbis plugin;

    public TooAdminMapCopyCommand(TrailOfOrbis plugin) {
        super("copy", "Copy held map's data to clipboard");
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

        // Copy to clipboard
        CopyPasteClipboard.getInstance().copyMap(sender.getUuid(), mapData);

        // Success message with details
        sender.sendMessage(Message.empty()
            .insert(Message.raw("[TooAdmin] ").color(MessageColors.GOLD))
            .insert(Message.raw("Copied map to clipboard : ").color(MessageColors.SUCCESS))
            .insert(Message.raw("Lv" + mapData.level() + " ").color(MessageColors.WHITE))
            .insert(Message.raw(mapData.rarity().name()).color(mapData.rarity().getHexColor()))
            .insert(Message.raw(" " + mapData.biome().getDisplayName()).color(MessageColors.WHITE))
            .insert(Message.raw(" (Q" + mapData.quality()).color(MessageColors.GRAY))
            .insert(Message.raw(", " + mapData.modifierCount() + " mods").color(MessageColors.GRAY))
            .insert(Message.raw(mapData.corrupted() ? ", corrupted" : "").color(MessageColors.ERROR))
            .insert(Message.raw(")").color(MessageColors.GRAY)));
    }

}
