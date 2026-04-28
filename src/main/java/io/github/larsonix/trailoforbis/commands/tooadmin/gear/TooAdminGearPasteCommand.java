package io.github.larsonix.trailoforbis.commands.tooadmin.gear;

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
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.util.GearUtils;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;
import java.util.Optional;

/**
 * Pastes clipboard gear stats onto the held item.
 *
 * <p>Usage: /tooadmin gear paste
 *
 * <p>Applies all copied gear properties (level, rarity, quality, prefixes, suffixes,
 * corrupted state, implicit) to the held RPG gear item. The target item keeps its
 * own instance ID and base item ID - only the stats are copied.
 *
 * <p>Requirements:
 * <ul>
 *   <li>Must have copied gear data in clipboard (use /tooadmin gear copy)</li>
 *   <li>Must be holding an existing RPG gear item</li>
 * </ul>
 *
 * @see TooAdminGearCopyCommand
 * @see CopyPasteClipboard
 */
public class TooAdminGearPasteCommand extends AbstractPlayerCommand {

    private final TrailOfOrbis plugin;

    public TooAdminGearPasteCommand(TrailOfOrbis plugin) {
        super("paste", "Paste clipboard stats onto held gear");
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
        Optional<GearData> clipboardOpt = CopyPasteClipboard.getInstance().getCopiedGear(sender.getUuid());
        if (clipboardOpt.isEmpty()) {
            sender.sendMessage(Message.raw("Clipboard is empty! Use /tooadmin gear copy first.").color(MessageColors.ERROR));
            return;
        }

        ItemStack heldItem = HeldItemHelper.getHeldItem(sender, store, ref);
        if (heldItem == null || heldItem.isEmpty()) {
            sender.sendMessage(Message.raw("You must hold an item !").color(MessageColors.ERROR));
            return;
        }

        // Check if held item is RPG gear
        Optional<GearData> existingOpt = GearUtils.readGearData(heldItem);
        if (existingOpt.isEmpty()) {
            sender.sendMessage(Message.raw("This item is not RPG gear. Paste only works on existing RPG gear.").color(MessageColors.ERROR));
            return;
        }

        GearData existing = existingOpt.get();
        GearData clipboard = clipboardOpt.get();

        // Build new GearData preserving target's instance ID and base item ID
        GearData newData = GearData.builder()
            .instanceId(existing.instanceId())       // Keep target's instance ID
            .level(clipboard.level())
            .rarity(clipboard.rarity())
            .quality(clipboard.quality())
            .prefixes(clipboard.prefixes())
            .suffixes(clipboard.suffixes())
            .corrupted(clipboard.corrupted())
            .implicit(clipboard.implicit())
            .baseItemId(existing.baseItemId())       // Keep target's base item ID
            .build();

        // Write to item
        ItemStack newItem = GearUtils.setGearData(heldItem, newData);

        // Update held item
        HeldItemHelper.setHeldItem(sender, store, ref, newItem);

        // Success message
        sender.sendMessage(Message.empty()
            .insert(Message.raw("[TooAdmin] ").color(MessageColors.GOLD))
            .insert(Message.raw("Pasted gear stats : ").color(MessageColors.SUCCESS))
            .insert(Message.raw("Lv" + newData.level() + " ").color(MessageColors.WHITE))
            .insert(Message.raw(newData.rarity().name()).color(newData.rarity().getHexColor()))
            .insert(Message.raw(" (Q" + newData.quality()).color(MessageColors.GRAY))
            .insert(Message.raw(", " + newData.modifierCount() + " mods").color(MessageColors.GRAY))
            .insert(Message.raw(newData.corrupted() ? ", corrupted" : "").color(MessageColors.ERROR))
            .insert(Message.raw(")").color(MessageColors.GRAY)));
    }

}
