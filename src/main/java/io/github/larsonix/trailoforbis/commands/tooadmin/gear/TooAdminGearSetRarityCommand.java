package io.github.larsonix.trailoforbis.commands.tooadmin.gear;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.commands.tooadmin.HeldItemHelper;
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.gear.util.GearUtils;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;
import java.util.Optional;

/**
 * Sets the held gear's rarity.
 *
 * <p>Usage: /tooadmin gear setrarity &lt;rarity&gt;
 */
public final class TooAdminGearSetRarityCommand extends AbstractPlayerCommand {

    private final TrailOfOrbis plugin;
    private final RequiredArg<String> rarityArg;

    public TooAdminGearSetRarityCommand(TrailOfOrbis plugin) {
        super("setrarity", "Set held gear's rarity");
        this.addAliases("rarity");
        this.plugin = plugin;

        rarityArg = this.withRequiredArg("rarity", "Rarity (common, uncommon, rare, epic, legendary, mythic)", ArgTypes.STRING);
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
        if (heldItem == null) {
            sender.sendMessage(Message.raw("You must hold an item !").color(MessageColors.ERROR));
            return;
        }

        Optional<GearData> gearDataOpt = GearUtils.readGearData(heldItem);
        if (gearDataOpt.isEmpty()) {
            sender.sendMessage(Message.raw("This item is not RPG gear.").color(MessageColors.ERROR));
            return;
        }

        String rarityStr = rarityArg.get(context);
        GearRarity rarity = parseRarity(rarityStr);
        if (rarity == null) {
            sender.sendMessage(Message.raw("Invalid rarity! Use : common, uncommon, rare, epic, legendary, mythic").color(MessageColors.ERROR));
            return;
        }

        try {
            GearData oldData = gearDataOpt.get();
            GearData newData = oldData.withRarity(rarity);
            ItemStack newItem = GearUtils.setGearData(heldItem, newData);

            HeldItemHelper.setHeldItem(sender, store, ref, newItem);

            sender.sendMessage(Message.empty()
                .insert(Message.raw("[TooAdmin] ").color(MessageColors.GOLD))
                .insert(Message.raw("Set gear rarity to ").color(MessageColors.SUCCESS))
                .insert(Message.raw(rarity.getHytaleQualityId()).color(MessageColors.WHITE)));
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Message.raw("Cannot set rarity : " + e.getMessage()).color(MessageColors.ERROR));
        }
    }

    private GearRarity parseRarity(String str) {
        if (str == null) return null;
        try {
            return GearRarity.valueOf(str.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
