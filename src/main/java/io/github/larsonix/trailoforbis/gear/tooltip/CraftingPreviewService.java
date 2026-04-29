package io.github.larsonix.trailoforbis.gear.tooltip;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.ItemBase;
import com.hypixel.hytale.protocol.ItemTranslationProperties;
import com.hypixel.hytale.protocol.UpdateType;
import com.hypixel.hytale.protocol.packets.assets.UpdateItems;
import com.hypixel.hytale.protocol.packets.assets.UpdateTranslations;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig;
import io.github.larsonix.trailoforbis.gear.conversion.ItemClassifier;
import io.github.larsonix.trailoforbis.gear.conversion.MaterialTierMapper;
import io.github.larsonix.trailoforbis.gear.conversion.VanillaConversionConfig;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.mobs.calculator.DistanceBonusCalculator;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * Appends RPG crafting preview info to vanilla weapon/armor tooltips.
 *
 * <p>For every item that our crafting conversion system would convert to RPG gear,
 * this service appends a preview section showing level range, max rarity, and
 * modifier count. The original vanilla description (including any lock/unlock text)
 * is preserved — we append, never replace.
 *
 * <p>Uses the same {@code UpdateItems} + {@code UpdateTranslations} packet
 * infrastructure as our RPG gear tooltip system. Descriptions are stored under
 * custom {@code rpg.crafting.*} keys to bypass Hytale's static
 * {@code server.items.*} translation lookup.
 *
 * <p>Items are filtered through {@link ItemClassifier} to ensure only items
 * we actually convert get preview text. Modded items, ammunition, and
 * blacklisted items are left untouched.
 */
public class CraftingPreviewService {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String CRAFTING_KEY_PREFIX = "rpg.crafting.";
    private static final String CRAFTING_KEY_SUFFIX = ".description";

    private final MaterialTierMapper materialMapper;
    private final DistanceBonusCalculator distanceCalculator;
    private final VanillaConversionConfig conversionConfig;
    private final ItemClassifier itemClassifier;
    private final GearBalanceConfig balanceConfig;

    private Map<String, ItemBase> cachedDefinitions;
    private Map<String, String> cachedTranslations;

    public CraftingPreviewService(
            @Nonnull MaterialTierMapper materialMapper,
            @Nonnull DistanceBonusCalculator distanceCalculator,
            @Nonnull VanillaConversionConfig conversionConfig,
            @Nonnull ItemClassifier itemClassifier,
            @Nonnull GearBalanceConfig balanceConfig) {
        this.materialMapper = materialMapper;
        this.distanceCalculator = distanceCalculator;
        this.conversionConfig = conversionConfig;
        this.itemClassifier = itemClassifier;
        this.balanceConfig = balanceConfig;
    }

    /**
     * Builds cached preview data for all convertible vanilla weapons/armor.
     * Call once at startup after all items are loaded.
     */
    public void initialize() {
        cachedDefinitions = new HashMap<>();
        cachedTranslations = new HashMap<>();

        int count = 0;
        int skipped = 0;

        for (Item item : Item.getAssetMap().getAssetMap().values()) {
            if (item == null || item == Item.UNKNOWN) continue;

            String itemId = item.getId();
            if (itemId == null || itemId.isEmpty()) continue;

            // Use the same classification as our crafting conversion pipeline
            if (item.getWeapon() == null && item.getArmor() == null) continue;
            if (item.getMaxStack() > 1) continue; // ammunition

            // Check whitelist/blacklist from config
            if (!itemClassifier.isAllowedByConfig(itemId)) {
                skipped++;
                continue;
            }

            // Compute level range from material distance
            VanillaConversionConfig.DistanceRange distRange = materialMapper.getDistanceRange(itemId);
            int minLevel = Math.max(1, distanceCalculator.estimateLevelFromDistance(distRange.getMin()));
            int maxLevel = Math.max(minLevel, distanceCalculator.estimateLevelFromDistance(distRange.getMax()));

            // Apply crafting multiplier
            double multiplier = conversionConfig.getCraftingLevelMultiplier();
            minLevel = Math.max(1, (int) (minLevel * multiplier));
            maxLevel = Math.max(minLevel, (int) (maxLevel * multiplier));

            // Get max rarity and modifier count range
            GearRarity maxRarity = materialMapper.getMaxRarity(itemId);
            int minMods = 1;
            int maxMods = balanceConfig.rarityConfig(maxRarity).maxModifiers();

            String previewDesc = buildPreviewDescription(minLevel, maxLevel, maxRarity, minMods, maxMods);

            // Clone vanilla item, change description key to our custom one, strip stats
            try {
                ItemBase definition = item.toPacket().clone();

                String descKey = CRAFTING_KEY_PREFIX + itemId + CRAFTING_KEY_SUFFIX;
                if (definition.translationProperties == null) {
                    definition.translationProperties = new ItemTranslationProperties(
                            "server.items." + itemId + ".name", descKey);
                } else {
                    definition.translationProperties = new ItemTranslationProperties(
                            definition.translationProperties.name, descKey);
                }

                stripVanillaStats(definition);

                cachedDefinitions.put(itemId, definition);
                cachedTranslations.put(descKey, previewDesc);
                count++;
            } catch (Exception e) {
                LOGGER.atFine().log("Failed to create preview for %s: %s", itemId, e.getMessage());
            }
        }

        LOGGER.atInfo().log("CraftingPreviewService initialized: %d items with RPG preview (%d skipped by config)",
                count, skipped);
    }

    /**
     * Sends cached preview overrides to a player.
     * Must be called after PlayerReady when the client is fully loaded.
     */
    public void syncToPlayer(@Nonnull PlayerRef playerRef) {
        if (cachedTranslations == null || cachedTranslations.isEmpty()) {
            return;
        }

        try {
            // Send UpdateItems to strip vanilla stats (armor/weapon modifiers).
            // translationProperties are left UNCHANGED to avoid breaking RPG
            // variant items that inherit the base item's description key.
            if (cachedDefinitions != null && !cachedDefinitions.isEmpty()) {
                UpdateItems itemPacket = new UpdateItems();
                itemPacket.type = UpdateType.AddOrUpdate;
                itemPacket.items = new HashMap<>(cachedDefinitions);
                itemPacket.updateModels = false;
                itemPacket.updateIcons = false;
                playerRef.getPacketHandler().writeNoCache(itemPacket);
            }

            // Send UpdateTranslations to override vanilla description values
            // with our crafting preview text.
            UpdateTranslations transPacket = new UpdateTranslations();
            transPacket.type = UpdateType.AddOrUpdate;
            transPacket.translations = new HashMap<>(cachedTranslations);
            playerRef.getPacketHandler().writeNoCache(transPacket);

            LOGGER.atFine().log("Sent crafting preview tooltips to %s (%d items, %d translations)",
                    playerRef.getUuid().toString().substring(0, 8),
                    cachedDefinitions != null ? cachedDefinitions.size() : 0,
                    cachedTranslations.size());
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to send crafting preview to %s",
                    playerRef.getUuid());
        }
    }

    /**
     * Rebuilds cached data after config reload.
     */
    public void reload() {
        initialize();
    }

    /**
     * Resyncs preview data to all online players after a config reload.
     */
    public void resyncToAll(@Nonnull Iterable<PlayerRef> onlinePlayers) {
        for (PlayerRef playerRef : onlinePlayers) {
            if (playerRef.isValid()) {
                syncToPlayer(playerRef);
            }
        }
    }

    public boolean isInitialized() {
        return cachedDefinitions != null && !cachedDefinitions.isEmpty();
    }

    /**
     * Strips vanilla stat displays from the cloned ItemBase.
     * Our mod replaces all stats with RPG stats on craft, so showing
     * vanilla "Physical Resistance: +6%" etc. is misleading.
     */
    private void stripVanillaStats(@Nonnull ItemBase definition) {
        // Armor: zero out resistance/enhancement maps (keep armorSlot for slot detection)
        if (definition.armor != null) {
            definition.armor.statModifiers = null;
            definition.armor.baseDamageResistance = 0;
            definition.armor.damageResistance = null;
            definition.armor.damageEnhancement = null;
            definition.armor.damageClassEnhancement = null;
        }
        // Weapon: zero out stat modifiers (keep entityStatsToClear for equipment behavior)
        if (definition.weapon != null) {
            definition.weapon.statModifiers = null;
        }
    }

    /**
     * Builds a formatted RPG crafting preview using Hytale's native markup.
     * Uses colors from {@link TooltipStyles} to match our RPG gear tooltips.
     */
    private String buildPreviewDescription(
            int minLevel, int maxLevel,
            @Nonnull GearRarity maxRarity,
            int minMods, int maxMods) {

        StringBuilder desc = new StringBuilder();

        // Header
        desc.append(colored("Crafts as RPG Gear", "#FFD700", true));
        desc.append("\n\n");

        // Level range — white values
        String levelText = (maxLevel > minLevel)
                ? minLevel + " - " + maxLevel
                : String.valueOf(minLevel);
        desc.append(colored("Level   ", TooltipStyles.LABEL_GRAY, false));
        desc.append(colored(levelText, TooltipStyles.VALUE_WHITE, false));
        desc.append("\n");

        // Rarity — colored in the rarity's own color
        desc.append(colored("Rarity  ", TooltipStyles.LABEL_GRAY, false));
        desc.append(colored("up to ", TooltipStyles.LABEL_GRAY, false));
        desc.append(colored(maxRarity.getHytaleQualityId(), maxRarity.getHexColor(), true));
        desc.append("\n");

        // Modifier count — white values
        String modsText = minMods + " - " + maxMods + " random";
        desc.append(colored("Mods    ", TooltipStyles.LABEL_GRAY, false));
        desc.append(colored(modsText, TooltipStyles.VALUE_WHITE, false));
        desc.append("\n");

        // Quality — white
        desc.append(colored("Quality ", TooltipStyles.LABEL_GRAY, false));
        desc.append(colored("rolled on craft", TooltipStyles.VALUE_WHITE, false));
        desc.append("\n\n");

        // Footer hint — subtle gray italic
        desc.append("<i>");
        desc.append(colored("Rarity, modifiers, and quality are randomized each time you craft.", TooltipStyles.SEPARATOR, false));
        desc.append("</i>");

        return desc.toString();
    }

    private static String colored(@Nonnull String text, @Nonnull String hexColor, boolean bold) {
        StringBuilder sb = new StringBuilder();
        sb.append("<color is=\"").append(hexColor).append("\">");
        if (bold) sb.append("<b>");
        sb.append(text);
        if (bold) sb.append("</b>");
        sb.append("</color>");
        return sb.toString();
    }
}
