package io.github.larsonix.trailoforbis.commands.tooadmin.give;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.stones.StoneType;
import io.github.larsonix.trailoforbis.stones.StoneUtils;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Gives a currency stone to the player.
 *
 * <p>Usage: /tooadmin give stone &lt;type&gt;
 *
 * <p>Stone types include:
 * <ul>
 *   <li>Reroll: gaias_calibration, ember_of_tuning, alterverse_shard, orbisian_blessing</li>
 *   <li>Enhancement: gaias_gift, spark_of_potential, core_of_ascension, heart_of_legends</li>
 *   <li>Removal: purging_ember, erosion_shard, transmutation_crystal</li>
 *   <li>Lock: wardens_seal, wardens_key</li>
 *   <li>Map: fortunes_compass, alterverse_key, threshold_stone, cartographers_polish</li>
 *   <li>Special: varyns_touch, gaias_perfection, lorekeepers_scroll, genesis_stone</li>
 * </ul>
 *
 * <p>Examples:
 * <ul>
 *   <li>/tooadmin give stone gaias_calibration</li>
 *   <li>/tooadmin give stone wardens_seal</li>
 *   <li>/tooadmin give stone varyns_touch</li>
 * </ul>
 *
 * <p>Stones are created with proper custom item registration and synced to the player.
 */
public final class TooAdminGiveStoneCommand extends AbstractPlayerCommand {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final RequiredArg<String> typeArg;

    public TooAdminGiveStoneCommand() {
        super("stone", "Give a currency stone to yourself");
        this.addAliases("currency");

        typeArg = this.withRequiredArg("type", "Stone type (e.g., gaias_calibration, wardens_seal)", ArgTypes.STRING);
    }

    @Override
    protected void execute(
        @Nonnull CommandContext context,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull PlayerRef sender,
        @Nonnull World world
    ) {
        // Parse stone type
        String typeStr = typeArg.get(context);
        StoneType stoneType = parseStoneType(typeStr);
        if (stoneType == null) {
            sender.sendMessage(Message.raw("Invalid stone type : " + typeStr).color(MessageColors.ERROR));
            sender.sendMessage(Message.raw("Available types : " + getAvailableStoneTypes()).color(MessageColors.GRAY));
            return;
        }

        // Create stone item using native Hytale item ID
        ItemStack stoneItem = StoneUtils.createStoneItem(stoneType);

        // Add to player's inventory
        boolean added = addToInventory(sender, store, ref, stoneItem);
        if (!added) {
            sender.sendMessage(Message.raw("Inventory full! Could not give stone.").color(MessageColors.ERROR));
            return;
        }

        // Success message
        sender.sendMessage(Message.empty()
            .insert(Message.raw("[TooAdmin] ").color(MessageColors.GOLD))
            .insert(Message.raw("Gave ").color(MessageColors.SUCCESS))
            .insert(Message.raw(stoneType.getDisplayName()).color(stoneType.getHexColor()))
            .insert(Message.raw(" (").color(MessageColors.SUCCESS))
            .insert(Message.raw(stoneType.getRarity().getHytaleQualityId()).color(stoneType.getHexColor()))
            .insert(Message.raw(")").color(MessageColors.SUCCESS)));
    }

    private boolean addToInventory(PlayerRef player, Store<EntityStore> store, Ref<EntityStore> ref, ItemStack item) {
        Player playerEntity = store.getComponent(ref, Player.getComponentType());
        if (playerEntity == null) {
            return false;
        }
        Inventory inventory = playerEntity.getInventory();
        if (inventory == null) {
            return false;
        }

        // Try hotbar first, then backpack
        ItemStackTransaction transaction = inventory.getHotbar().addItemStack(item);
        if (transaction.succeeded()) {
            return true;
        }

        // Try backpack
        transaction = inventory.getBackpack().addItemStack(item);
        return transaction.succeeded();
    }

    private StoneType parseStoneType(String str) {
        if (str == null) return null;
        try {
            return StoneType.valueOf(str.toUpperCase());
        } catch (IllegalArgumentException e) {
            // Try partial match
            String upperStr = str.toUpperCase();
            for (StoneType type : StoneType.values()) {
                if (type.name().contains(upperStr)) {
                    return type;
                }
            }
            return null;
        }
    }

    private String getAvailableStoneTypes() {
        return Arrays.stream(StoneType.values())
            .map(st -> st.name().toLowerCase())
            .collect(Collectors.joining(", "));
    }
}
