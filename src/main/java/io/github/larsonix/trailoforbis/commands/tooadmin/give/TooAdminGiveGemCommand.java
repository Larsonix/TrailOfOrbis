package io.github.larsonix.trailoforbis.commands.tooadmin.give;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgumentType;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.api.ServiceRegistry;
import io.github.larsonix.trailoforbis.gear.item.CustomItemSyncService;
import io.github.larsonix.trailoforbis.gems.GemManager;
import io.github.larsonix.trailoforbis.gems.config.GemRegistry;
import io.github.larsonix.trailoforbis.gems.item.GemItemFactory;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

public final class TooAdminGiveGemCommand
extends AbstractPlayerCommand {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private final RequiredArg<String> gemIdArg;
    private final RequiredArg<Integer> levelArg;
    private final RequiredArg<Integer> qualityArg;

    public TooAdminGiveGemCommand() {
        super("gem", "Give a skill gem to yourself");
        this.addAliases(new String[]{"g"});
        this.gemIdArg = this.withRequiredArg("gemId", "Gem ID (e.g., fireball, chain)", (ArgumentType)ArgTypes.STRING);
        this.levelArg = this.withRequiredArg("level", "Gem level (1-1000)", (ArgumentType)ArgTypes.INTEGER);
        this.qualityArg = this.withRequiredArg("quality", "Quality (1-100)", (ArgumentType)ArgTypes.INTEGER);
    }

    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef sender, @Nonnull World world) {
        Optional<GemManager> managerOpt = ServiceRegistry.get(GemManager.class);
        if (managerOpt.isEmpty() || !managerOpt.get().isInitialized()) {
            sender.sendMessage(Message.raw("Gem system not initialized.").color("#FF5555"));
            return;
        }
        GemManager gemManager = managerOpt.get();
        GemRegistry registry = gemManager.getRegistry();
        GemItemFactory factory = gemManager.getItemFactory();
        CustomItemSyncService syncService = gemManager.getSyncService();
        if (registry == null || factory == null) {
            sender.sendMessage(Message.raw("Gem system not available.").color("#FF5555"));
            return;
        }
        String gemId = (String)this.gemIdArg.get(context);
        if (!registry.contains(gemId)) {
            sender.sendMessage(Message.raw("Unknown gem: " + gemId).color("#FF5555"));
            String available = registry.getAllIds().stream().sorted().collect(Collectors.joining(", "));
            sender.sendMessage(Message.raw("Available: " + available).color("#AAAAAA"));
            return;
        }
        int level = (Integer)this.levelArg.get(context);
        if (level < 1 || level > 1000) {
            sender.sendMessage(Message.raw("Level must be between 1 and 1000!").color("#FF5555"));
            return;
        }
        int quality = (Integer)this.qualityArg.get(context);
        if (quality < 1 || quality > 100) {
            sender.sendMessage(Message.raw("Quality must be between 1 and 100!").color("#FF5555"));
            return;
        }
        GemItemFactory.GemItemResult result = factory.createGemItem(gemId, level, quality);
        if (result == null) {
            sender.sendMessage(Message.raw("Failed to create gem item. Check server log.").color("#FF5555"));
            return;
        }
        if (syncService != null) {
            try {
                syncService.syncItem(sender, result.itemData());
                LOGGER.atFine().log("Synced gem %s definition to player %s", result.itemData().getItemId(), sender.getUuid());
            }
            catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Failed to sync gem definition to player");
            }
        } else {
            LOGGER.atWarning().log("CustomItemSyncService not available \u2014 gem tooltip will not display");
        }
        boolean added = this.addToInventory(sender, store, ref, result.itemStack());
        if (!added) {
            sender.sendMessage(Message.raw("Inventory full!").color("#FF5555"));
            return;
        }
        String gemName = registry.getDefinition(gemId).map(d -> d.name()).orElse(gemId);
        sender.sendMessage(Message.empty().insert(Message.raw("[TooAdmin] ").color("#FFAA00")).insert(Message.raw("Gave ").color("#55FF55")).insert(Message.raw(gemName).color("#FFFFFF")).insert(Message.raw(" Lv" + level + " Q" + quality).color("#AAAAAA")));
    }

    private boolean addToInventory(PlayerRef player, Store<EntityStore> store, Ref<EntityStore> ref, ItemStack item) {
        Player playerEntity = (Player)store.getComponent(ref, Player.getComponentType());
        if (playerEntity == null) {
            return false;
        }
        Inventory inventory = playerEntity.getInventory();
        if (inventory == null) {
            return false;
        }
        ItemStackTransaction transaction = inventory.getHotbar().addItemStack(item);
        if (transaction.succeeded()) {
            return true;
        }
        transaction = inventory.getBackpack().addItemStack(item);
        return transaction.succeeded();
    }
}
