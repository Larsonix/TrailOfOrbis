package io.github.larsonix.trailoforbis.commands.tooadmin.give;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
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
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.gear.GearManager;
import io.github.larsonix.trailoforbis.gear.item.CustomItemInstanceId;
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.item.CustomItemSyncService;
import io.github.larsonix.trailoforbis.gear.item.ItemRegistryService;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.maps.RealmsManager;
import io.github.larsonix.trailoforbis.maps.core.RealmMapData;
import io.github.larsonix.trailoforbis.maps.items.RealmMapGenerator;
import io.github.larsonix.trailoforbis.maps.items.RealmMapUtils;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;

/**
 * Creates a realm map and adds it to the player's inventory.
 *
 * <p>Usage: /tooadmin give map &lt;level&gt; &lt;rarity&gt; &lt;quality&gt;
 *
 * <p>All parameters are mandatory:
 * <ul>
 *   <li>level: 1-1000000</li>
 *   <li>rarity: common, uncommon, rare, epic, legendary, mythic</li>
 *   <li>quality: 1-101 (101 = perfect)</li>
 * </ul>
 *
 * <p>Examples:
 * <ul>
 *   <li>/tooadmin give map 50 epic 85</li>
 *   <li>/tooadmin give map 500 legendary 95</li>
 *   <li>/tooadmin give map 1000 mythic 101</li>
 * </ul>
 *
 * <p>Maps are created with proper custom item registration and synced to the player.
 */
public final class TooAdminGiveMapCommand extends AbstractPlayerCommand {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final TrailOfOrbis plugin;
    private final RequiredArg<Integer> levelArg;
    private final RequiredArg<String> rarityArg;
    private final RequiredArg<Integer> qualityArg;

    public TooAdminGiveMapCommand(TrailOfOrbis plugin) {
        super("map", "Give a realm map to yourself");
        this.addAliases("realm");
        this.plugin = plugin;

        levelArg = this.withRequiredArg("level", "Map level (1-" + GearData.MAX_LEVEL + ")", ArgTypes.INTEGER);
        rarityArg = this.withRequiredArg("rarity", "Rarity: common/uncommon/rare/epic/legendary/mythic", ArgTypes.STRING);
        qualityArg = this.withRequiredArg("quality", "Quality (1-101, 101 = perfect)", ArgTypes.INTEGER);
    }

    @Override
    protected void execute(
        @Nonnull CommandContext context,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull PlayerRef sender,
        @Nonnull World world
    ) {
        // Validate level
        Integer level = levelArg.get(context);
        if (level < 1 || level > GearData.MAX_LEVEL) {
            sender.sendMessage(Message.raw("Level must be between 1 and " + GearData.MAX_LEVEL + " !").color(MessageColors.ERROR));
            return;
        }

        // Parse rarity (mandatory)
        String rarityStr = rarityArg.get(context);
        GearRarity rarity = parseRarity(rarityStr);
        if (rarity == null) {
            sender.sendMessage(Message.raw("Invalid rarity ! Use : common, uncommon, rare, epic, legendary, mythic").color(MessageColors.ERROR));
            return;
        }

        // Validate quality (mandatory)
        Integer quality = qualityArg.get(context);
        if (quality < 1 || quality > 101) {
            sender.sendMessage(Message.raw("Quality must be between 1 and 101 !").color(MessageColors.ERROR));
            return;
        }

        // Get realms manager
        RealmsManager realmsManager = plugin.getRealmsManager();
        if (realmsManager == null) {
            sender.sendMessage(Message.raw("Realms system not initialized !").color(MessageColors.ERROR));
            return;
        }

        RealmMapGenerator mapGenerator = realmsManager.getMapGenerator();
        if (mapGenerator == null) {
            sender.sendMessage(Message.raw("Map generator not available !").color(MessageColors.ERROR));
            return;
        }

        // Get gear manager for registration services
        GearManager gearManager = plugin.getGearManager();
        if (gearManager == null) {
            sender.sendMessage(Message.raw("Gear system not initialized !").color(MessageColors.ERROR));
            return;
        }

        // Generate map data with specified parameters
        RealmMapData mapData = mapGenerator.builder()
            .level(level)
            .rarity(rarity)
            .quality(quality)
            .identified(true) // Admin-given maps are identified
            .build();

        // Generate custom item instance ID for registration
        CustomItemInstanceId instanceId = CustomItemInstanceId.Generator.generateMap();
        mapData = mapData.withInstanceId(instanceId);

        // Get base and custom item IDs
        String baseItemId = mapData.getBaseItemId();
        String customItemId = instanceId.toItemId();

        // Register the custom item with ItemRegistryService
        ItemRegistryService registryService = gearManager.getItemRegistryService();
        if (registryService != null && registryService.isInitialized()) {
            Item baseItem = Item.getAssetMap().getAsset(baseItemId);
            if (baseItem != null && baseItem != Item.UNKNOWN) {
                // Register with Secondary interaction for right-click behavior
                registryService.createAndRegisterWithSecondarySync(
                    baseItem, customItemId, "RPG_RealmMap_Secondary");
                LOGGER.atFine().log("Registered custom map item: %s (base: %s)", customItemId, baseItemId);
            } else {
                LOGGER.atWarning().log("Base item not found: %s - map may show as Invalid Item", baseItemId);
            }
        } else {
            LOGGER.atWarning().log("ItemRegistryService not available");
        }

        // Sync item definition to the player
        CustomItemSyncService syncService = gearManager.getCustomItemSyncService();
        if (syncService != null) {
            try {
                syncService.syncItem(sender, mapData);
                LOGGER.atFine().log("Synced map %s definition to player %s", customItemId, sender.getUuid());
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Failed to sync map definition to player");
            }
        }

        // Create ItemStack with the CUSTOM item ID (so it uses the registered item)
        ItemStack itemStack = new ItemStack(customItemId, 1);

        // Write map data to item metadata
        ItemStack mapItem = RealmMapUtils.writeMapData(itemStack, mapData);

        // Add to player's inventory
        boolean added = addToInventory(sender, store, ref, mapItem);
        if (!added) {
            sender.sendMessage(Message.raw("Inventory full! Could not give map.").color(MessageColors.ERROR));
            return;
        }

        // Success message
        sender.sendMessage(Message.empty()
            .insert(Message.raw("[TooAdmin] ").color(MessageColors.GOLD))
            .insert(Message.raw("Gave ").color(MessageColors.SUCCESS))
            .insert(Message.raw(mapData.rarity().getHytaleQualityId()).color(mapData.rarity().getHexColor()))
            .insert(Message.raw(" realm map (level ").color(MessageColors.SUCCESS))
            .insert(Message.raw(String.valueOf(level)).color(MessageColors.WHITE))
            .insert(Message.raw(", quality ").color(MessageColors.SUCCESS))
            .insert(Message.raw(String.valueOf(mapData.quality())).color(MessageColors.WHITE))
            .insert(Message.raw(", biome ").color(MessageColors.SUCCESS))
            .insert(Message.raw(mapData.biome().getDisplayName()).color(MessageColors.WHITE))
            .insert(Message.raw(", size ").color(MessageColors.SUCCESS))
            .insert(Message.raw(mapData.size().getDisplayName()).color(MessageColors.WHITE))
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

    private GearRarity parseRarity(String str) {
        if (str == null) return null;
        try {
            return GearRarity.valueOf(str.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
