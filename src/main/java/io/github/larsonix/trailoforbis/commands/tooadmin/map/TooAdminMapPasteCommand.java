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
 * Pastes clipboard map data onto the held realm map.
 *
 * <p>Usage: /tooadmin map paste
 *
 * <p>Applies all copied map properties (level, rarity, quality, biome, size, shape,
 * prefixes, suffixes, corrupted, identified) to the held realm map. The target map
 * keeps its own instance ID - only the data is copied.
 *
 * <p>Requirements:
 * <ul>
 *   <li>Must have copied map data in clipboard (use /tooadmin map copy)</li>
 *   <li>Must be holding an existing realm map</li>
 * </ul>
 *
 * @see TooAdminMapCopyCommand
 * @see CopyPasteClipboard
 */
public class TooAdminMapPasteCommand extends AbstractPlayerCommand {

    private final TrailOfOrbis plugin;

    public TooAdminMapPasteCommand(TrailOfOrbis plugin) {
        super("paste", "Paste clipboard data onto held map");
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
        // Check clipboard first
        Optional<RealmMapData> clipboardOpt = CopyPasteClipboard.getInstance().getCopiedMap(sender.getUuid());
        if (clipboardOpt.isEmpty()) {
            sender.sendMessage(Message.raw("Clipboard is empty! Use /tooadmin map copy first.").color(MessageColors.ERROR));
            return;
        }

        ItemStack heldItem = HeldItemHelper.getHeldItem(sender, store, ref);
        if (heldItem == null || heldItem.isEmpty()) {
            sender.sendMessage(Message.raw("You must hold an item !").color(MessageColors.ERROR));
            return;
        }

        // Check if held item is a realm map
        Optional<RealmMapData> existingOpt = RealmMapUtils.readMapData(heldItem);
        if (existingOpt.isEmpty()) {
            sender.sendMessage(Message.raw("This item is not a realm map. Paste only works on existing realm maps.").color(MessageColors.ERROR));
            return;
        }

        RealmMapData existing = existingOpt.get();
        RealmMapData clipboard = clipboardOpt.get();

        // Build new RealmMapData preserving target's instance ID
        RealmMapData newData = RealmMapData.builder()
            .level(clipboard.level())
            .rarity(clipboard.rarity())
            .quality(clipboard.quality())
            .biome(clipboard.biome())
            .size(clipboard.size())
            .shape(clipboard.shape())
            .prefixes(clipboard.prefixes())
            .suffixes(clipboard.suffixes())
            .corrupted(clipboard.corrupted())
            .identified(clipboard.identified())
            .instanceId(existing.instanceId())  // Keep target's instance ID
            .build();

        // Write to item
        ItemStack newItem = RealmMapUtils.writeMapData(heldItem, newData);

        // Update held item
        HeldItemHelper.setHeldItem(sender, store, ref, newItem);

        // Success message
        sender.sendMessage(Message.empty()
            .insert(Message.raw("[TooAdmin] ").color(MessageColors.GOLD))
            .insert(Message.raw("Pasted map data : ").color(MessageColors.SUCCESS))
            .insert(Message.raw("Lv" + newData.level() + " ").color(MessageColors.WHITE))
            .insert(Message.raw(newData.rarity().name()).color(newData.rarity().getHexColor()))
            .insert(Message.raw(" " + newData.biome().getDisplayName()).color(MessageColors.WHITE))
            .insert(Message.raw(" (Q" + newData.quality()).color(MessageColors.GRAY))
            .insert(Message.raw(", " + newData.modifierCount() + " mods").color(MessageColors.GRAY))
            .insert(Message.raw(newData.corrupted() ? ", corrupted" : "").color(MessageColors.ERROR))
            .insert(Message.raw(")").color(MessageColors.GRAY)));
    }

}
