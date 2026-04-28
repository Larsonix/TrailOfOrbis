package io.github.larsonix.trailoforbis.commands.tooadmin.give;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
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
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.gear.generation.GearGenerator;
import io.github.larsonix.trailoforbis.gear.model.EquipmentType;
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.gear.model.WeaponType;
import io.github.larsonix.trailoforbis.gear.util.GearUtils;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Generates gear of a specified type and adds it to the player's inventory.
 *
 * <p>Usage: /tooadmin give gear &lt;type&gt; &lt;level&gt; &lt;rarity&gt; &lt;quality&gt;
 *
 * <p>All parameters are mandatory:
 * <ul>
 *   <li>type: Equipment type (sword, plate_chest, cloth_head, etc.)</li>
 *   <li>level: 1-1000000</li>
 *   <li>rarity: common, uncommon, rare, epic, legendary, mythic</li>
 *   <li>quality: 1-101 (101 = perfect)</li>
 * </ul>
 *
 * <p>Examples:
 * <ul>
 *   <li>/tooadmin give gear sword 50 epic 85</li>
 *   <li>/tooadmin give gear plate_legs 50 epic 85</li>
 *   <li>/tooadmin give gear staff 50 legendary 95</li>
 *   <li>/tooadmin give gear cloth_head 30 rare 60</li>
 * </ul>
 */
public final class TooAdminGiveGearCommand extends AbstractPlayerCommand {

    private final TrailOfOrbis plugin;
    private final RequiredArg<String> typeArg;
    private final RequiredArg<Integer> levelArg;
    private final RequiredArg<String> rarityArg;
    private final RequiredArg<Integer> qualityArg;

    public TooAdminGiveGearCommand(TrailOfOrbis plugin) {
        super("gear", "Generate gear of a given type");
        this.addAliases("weapon", "armor");
        this.plugin = plugin;

        typeArg = this.withRequiredArg("type", "Equipment type (sword, plate_chest, cloth_head, etc.)", ArgTypes.STRING);
        levelArg = this.withRequiredArg("level", "Gear level (1-" + GearData.MAX_LEVEL + ")", ArgTypes.INTEGER);
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
        // Parse equipment type
        String typeStr = typeArg.get(context);
        EquipmentType equipmentType = parseEquipmentType(typeStr);
        if (equipmentType == null) {
            sender.sendMessage(Message.raw("Invalid equipment type: " + typeStr).color(MessageColors.ERROR));
            sender.sendMessage(Message.raw("Weapons: " + getTypeNames(EquipmentType.Category.WEAPON)).color(MessageColors.GRAY));
            sender.sendMessage(Message.raw("Armor: " + getTypeNames(EquipmentType.Category.ARMOR)).color(MessageColors.GRAY));
            sender.sendMessage(Message.raw("Offhand: " + getTypeNames(EquipmentType.Category.OFFHAND)).color(MessageColors.GRAY));
            return;
        }

        // Validate level
        Integer level = levelArg.get(context);
        if (level < 1 || level > GearData.MAX_LEVEL) {
            sender.sendMessage(Message.raw("Level must be between 1 and " + GearData.MAX_LEVEL + " !").color(MessageColors.ERROR));
            return;
        }

        // Parse rarity
        String rarityStr = rarityArg.get(context);
        GearRarity rarity = parseRarity(rarityStr);
        if (rarity == null) {
            sender.sendMessage(Message.raw("Invalid rarity ! Use: common, uncommon, rare, epic, legendary, mythic").color(MessageColors.ERROR));
            return;
        }

        // Validate quality
        Integer quality = qualityArg.get(context);
        if (quality < 1 || quality > 101) {
            sender.sendMessage(Message.raw("Quality must be between 1 and 101 !").color(MessageColors.ERROR));
            return;
        }

        // Get gear generator directly (admin commands have plugin access)
        GearGenerator gearGenerator = plugin.getGearManager().getGearGenerator();

        // Resolve a default Hytale base item ID for this equipment type
        String baseItemId = getDefaultItemId(equipmentType);
        ItemStack baseItem = new ItemStack(baseItemId, 1);

        // Generate gear using the builder API with correct slot + equipment type
        ItemStack gearItem = gearGenerator.builder()
                .level(level)
                .slot(equipmentType.getSlot())
                .rarity(rarity)
                .equipmentType(equipmentType)
                .quality(quality)
                .itemId(baseItemId)
                .build(baseItem);

        // Add to player's inventory
        boolean added = addToInventory(sender, store, ref, gearItem);
        if (!added) {
            sender.sendMessage(Message.raw("Inventory full! Could not give gear.").color(MessageColors.ERROR));
            return;
        }

        // Build success message
        Optional<GearData> finalData = GearUtils.getGearData(gearItem);
        if (finalData.isPresent()) {
            GearData data = finalData.get();
            sender.sendMessage(Message.empty()
                .insert(Message.raw("[TooAdmin] ").color(MessageColors.GOLD))
                .insert(Message.raw("Gave ").color(MessageColors.SUCCESS))
                .insert(Message.raw(data.rarity().getHytaleQualityId()).color(data.rarity().getHexColor()))
                .insert(Message.raw(" " + equipmentType.getConfigKey()).color(MessageColors.WHITE))
                .insert(Message.raw(" (level ").color(MessageColors.SUCCESS))
                .insert(Message.raw(String.valueOf(level)).color(MessageColors.WHITE))
                .insert(Message.raw(", quality ").color(MessageColors.SUCCESS))
                .insert(Message.raw(String.valueOf(data.quality())).color(MessageColors.WHITE))
                .insert(Message.raw(", ").color(MessageColors.SUCCESS))
                .insert(Message.raw(String.valueOf(data.prefixes().size() + data.suffixes().size())).color(MessageColors.WHITE))
                .insert(Message.raw(" modifiers)").color(MessageColors.SUCCESS)));
        } else {
            sender.sendMessage(Message.raw("Generated gear item.").color(MessageColors.SUCCESS));
        }
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

        transaction = inventory.getBackpack().addItemStack(item);
        return transaction.succeeded();
    }

    @Nullable
    private EquipmentType parseEquipmentType(String str) {
        if (str == null) return null;
        String normalized = str.toLowerCase();

        // Exact match against config keys
        for (EquipmentType type : EquipmentType.values()) {
            if (type == EquipmentType.UNKNOWN_WEAPON || type == EquipmentType.UNKNOWN_ARMOR) {
                continue;
            }
            if (type.getConfigKey().equals(normalized)) {
                return type;
            }
        }
        return null;
    }

    private GearRarity parseRarity(String str) {
        if (str == null) return null;
        try {
            return GearRarity.valueOf(str.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Returns a default Hytale item ID for the given equipment type.
     *
     * <p>Uses the most common/generic variant per type based on actual game items.
     * Most physical weapons use Iron, magic weapons use their most basic variant,
     * and armor uses the canonical material for its class.
     */
    private String getDefaultItemId(EquipmentType equipmentType) {
        // Armor types
        if (equipmentType.isArmor()) {
            String slotCapitalized = capitalize(equipmentType.getArmorSlot().getSlotName());
            return switch (equipmentType.getArmorMaterial()) {
                case CLOTH -> "Armor_Cloth_Cotton_" + slotCapitalized;
                case LEATHER -> "Armor_Leather_Light_" + slotCapitalized;
                case PLATE -> "Armor_Iron_" + slotCapitalized;
                case WOOD -> "Armor_Wood_" + slotCapitalized;
                case SPECIAL -> "Armor_Iron_" + slotCapitalized;
            };
        }

        // Weapon/offhand types
        WeaponType weaponType = equipmentType.getWeaponType();
        if (weaponType == null) {
            return "Weapon_Sword_Iron";
        }

        return switch (weaponType) {
            // Most physical weapons have _Iron variants
            case SWORD, AXE, MACE, CLUB, LONGSWORD, BATTLEAXE, SPEAR,
                 SHORTBOW, CROSSBOW, SHIELD -> "Weapon_" + weaponType.getIdPattern() + "_Iron";
            case DAGGER -> "Weapon_" + weaponType.getIdPattern() + "_Iron";

            // These only have _Tribal variants
            case CLAWS, BLOWGUN, DART -> "Weapon_" + weaponType.getIdPattern() + "_Tribal";

            // Bomb and Kunai have no material suffix
            case BOMB -> "Weapon_Bomb";
            case KUNAI -> "Weapon_Kunai";

            // Magic weapons use their most basic variants
            case STAFF -> "Weapon_Staff_Copper";
            case WAND -> "Weapon_Wand_Wood";
            case SPELLBOOK -> "Weapon_Spellbook_Fire";

            case UNKNOWN -> "Weapon_Sword_Iron";
        };
    }

    private String getTypeNames(EquipmentType.Category category) {
        return Arrays.stream(EquipmentType.values())
                .filter(t -> t.getCategory() == category)
                .filter(t -> t != EquipmentType.UNKNOWN_WEAPON && t != EquipmentType.UNKNOWN_ARMOR)
                .map(EquipmentType::getConfigKey)
                .collect(Collectors.joining(", "));
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
