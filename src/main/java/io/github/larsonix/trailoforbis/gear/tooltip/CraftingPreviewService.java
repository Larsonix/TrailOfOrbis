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
 * this service appends a preview section showing the gear level the player would
 * get, max rarity, and modifier count. The level shown is personalized per player:
 * it reflects {@code min(playerLevel, materialCeiling)}.
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

    /** Item definitions (stripped vanilla stats, redirected description keys). Same for all players. */
    private Map<String, ItemBase> cachedDefinitions;

    /** Original unmodified vanilla definitions for restoring when window closes. */
    private Map<String, ItemBase> originalDefinitions;

    /** Per-item preview metadata. Used to compute per-player translations at sync time. */
    private Map<String, ItemPreviewData> itemPreviews;

    /** Reskin translations (static, same for all players). */
    private Map<String, String> reskinTranslations;

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
        originalDefinitions = new HashMap<>();
        itemPreviews = new HashMap<>();

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

            // Compute material level range
            VanillaConversionConfig.DistanceRange distRange = materialMapper.getDistanceRange(itemId);
            double multiplier = conversionConfig.getCraftingLevelMultiplier();
            int minLevel = Math.max(1, (int) (distanceCalculator.estimateLevelFromDistance(distRange.getMin()) * multiplier));
            int materialCeiling = Math.max(minLevel, (int) (distanceCalculator.estimateLevelFromDistance(distRange.getMax()) * multiplier));

            // Get max rarity and modifier count range
            GearRarity maxRarity = materialMapper.getMaxRarity(itemId);
            int minMods = 1;
            int maxMods = balanceConfig.rarityConfig(maxRarity).maxModifiers();

            String descKey = CRAFTING_KEY_PREFIX + itemId + CRAFTING_KEY_SUFFIX;

            // Store preview metadata for per-player translation computation
            itemPreviews.put(descKey, new ItemPreviewData(minLevel, materialCeiling, maxRarity, minMods, maxMods));

            // Clone vanilla item, change description key to our custom one, strip stats
            try {
                // Cache original unmodified definition for restore on window close
                originalDefinitions.put(itemId, item.toPacket().clone());

                ItemBase definition = item.toPacket().clone();

                if (definition.translationProperties == null) {
                    definition.translationProperties = new ItemTranslationProperties(
                            "server.items." + itemId + ".name", descKey);
                } else {
                    definition.translationProperties = new ItemTranslationProperties(
                            definition.translationProperties.name, descKey);
                }

                stripVanillaStats(definition);

                cachedDefinitions.put(itemId, definition);
                count++;
            } catch (Exception e) {
                LOGGER.atFine().log("Failed to create preview for %s: %s", itemId, e.getMessage());
            }
        }

        // Build reskin translations: same keys as item previews, but all point
        // to a single reskin description. Sent when a player opens the Builder's
        // Workbench, so recipe outputs show reskin info instead of crafting stats.
        String reskinDesc = buildReskinDescription();
        reskinTranslations = new HashMap<>(itemPreviews.size());
        for (String key : itemPreviews.keySet()) {
            reskinTranslations.put(key, reskinDesc);
        }

        LOGGER.atInfo().log("CraftingPreviewService initialized: %d items with RPG preview (%d skipped by config)",
                count, skipped);
    }

    /**
     * Sends cached preview overrides to a player with personalized level info.
     *
     * <p>The level shown on each item is {@code min(playerLevel, materialCeiling)},
     * with the material range in brackets, e.g. "Level : 3 [1 to 5]".
     *
     * @param playerRef The player to sync to
     * @param playerLevel The player's current RPG level
     */
    public void syncToPlayer(@Nonnull PlayerRef playerRef, int playerLevel) {
        if (itemPreviews == null || itemPreviews.isEmpty()) {
            return;
        }

        try {
            // Send UpdateItems to strip vanilla stats and redirect description
            // keys to rpg.crafting.* (server.items.* can't be overridden at
            // runtime). This changes the base item definition — RPG variants
            // (variant=true) will inherit unless re-sent after this packet.
            if (cachedDefinitions != null && !cachedDefinitions.isEmpty()) {
                UpdateItems itemPacket = new UpdateItems();
                itemPacket.type = UpdateType.AddOrUpdate;
                itemPacket.items = new HashMap<>(cachedDefinitions);
                itemPacket.updateModels = false;
                itemPacket.updateIcons = false;
                playerRef.getPacketHandler().writeNoCache(itemPacket);
            }

            // Compute per-player translations based on their level
            Map<String, String> translations = buildTranslationsForPlayer(playerLevel);

            UpdateTranslations transPacket = new UpdateTranslations();
            transPacket.type = UpdateType.AddOrUpdate;
            transPacket.translations = translations;
            playerRef.getPacketHandler().writeNoCache(transPacket);

            LOGGER.atFine().log("Sent crafting preview tooltips to %s (level=%d, %d items)",
                    playerRef.getUuid().toString().substring(0, 8),
                    playerLevel,
                    translations.size());
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to send crafting preview to %s",
                    playerRef.getUuid());
        }
    }

    /**
     * Backwards-compatible overload — uses level 1 as fallback.
     * Prefer {@link #syncToPlayer(PlayerRef, int)} when player level is available.
     */
    public void syncToPlayer(@Nonnull PlayerRef playerRef) {
        syncToPlayer(playerRef, 1);
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

    // ==================== Vanilla Definition Restore ====================

    /**
     * Sends original unmodified vanilla item definitions back to the player,
     * undoing the crafting preview overrides.
     *
     * <p>Used by {@code ReskinDataPreserver} when the Builder's Workbench closes
     * to restore item definitions to their pre-modified state.
     */
    public void restoreVanillaDefinitions(@Nonnull PlayerRef playerRef) {
        if (originalDefinitions == null || originalDefinitions.isEmpty()) {
            return;
        }

        try {
            UpdateItems packet = new UpdateItems();
            packet.type = UpdateType.AddOrUpdate;
            packet.items = new HashMap<>(originalDefinitions);
            packet.updateModels = false;
            packet.updateIcons = false;
            playerRef.getPacketHandler().writeNoCache(packet);

            LOGGER.atFine().log("Restored vanilla definitions for %s (%d items)",
                    playerRef.getUuid().toString().substring(0, 8),
                    originalDefinitions.size());
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to restore vanilla definitions for %s",
                    playerRef.getUuid());
        }
    }

    /**
     * Sends reskin-specific translations to a player, overriding the crafting
     * preview text for all {@code rpg.crafting.*} keys.
     *
     * <p>Called when a player opens the Builder's Workbench. The item definitions
     * still point to the same {@code rpg.crafting.*} keys, but now those keys
     * resolve to reskin text instead of crafting stats.
     */
    public void sendReskinPreview(@Nonnull PlayerRef playerRef) {
        if (reskinTranslations == null || reskinTranslations.isEmpty()) {
            return;
        }

        try {
            UpdateTranslations packet = new UpdateTranslations();
            packet.type = UpdateType.AddOrUpdate;
            packet.translations = new HashMap<>(reskinTranslations);
            playerRef.getPacketHandler().writeNoCache(packet);

            LOGGER.atFine().log("Sent reskin preview translations to %s (%d keys)",
                    playerRef.getUuid().toString().substring(0, 8),
                    reskinTranslations.size());
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to send reskin preview to %s",
                    playerRef.getUuid());
        }
    }

    /**
     * Restores crafting preview translations for a player, reversing
     * {@link #sendReskinPreview(PlayerRef)}.
     *
     * <p>Called when a player closes the Builder's Workbench.
     */
    public void restoreCraftingPreview(@Nonnull PlayerRef playerRef, int playerLevel) {
        if (itemPreviews == null || itemPreviews.isEmpty()) {
            return;
        }

        try {
            Map<String, String> translations = buildTranslationsForPlayer(playerLevel);

            UpdateTranslations packet = new UpdateTranslations();
            packet.type = UpdateType.AddOrUpdate;
            packet.translations = translations;
            playerRef.getPacketHandler().writeNoCache(packet);

            LOGGER.atFine().log("Restored crafting preview translations for %s (level=%d, %d keys)",
                    playerRef.getUuid().toString().substring(0, 8),
                    playerLevel,
                    translations.size());
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to restore crafting preview for %s",
                    playerRef.getUuid());
        }
    }

    /**
     * Backwards-compatible overload — uses level 1 as fallback.
     */
    public void restoreCraftingPreview(@Nonnull PlayerRef playerRef) {
        restoreCraftingPreview(playerRef, 1);
    }

    // ==================== Internal ====================

    /**
     * Builds per-player translation map with personalized level info.
     */
    private Map<String, String> buildTranslationsForPlayer(int playerLevel) {
        Map<String, String> translations = new HashMap<>(itemPreviews.size());
        for (Map.Entry<String, ItemPreviewData> entry : itemPreviews.entrySet()) {
            ItemPreviewData data = entry.getValue();
            int effectiveLevel = Math.max(1, Math.min(playerLevel, data.materialCeiling));
            translations.put(entry.getKey(),
                    buildPreviewDescription(effectiveLevel, data.minLevel, data.materialCeiling,
                            data.maxRarity, data.minMods, data.maxMods));
        }
        return translations;
    }

    /**
     * Strips vanilla stat displays from the cloned ItemBase.
     * Our mod replaces all stats with RPG stats on craft, so showing
     * vanilla "Physical Resistance: +6%" etc. is misleading.
     */
    private void stripVanillaStats(@Nonnull ItemBase definition) {
        // Armor: zero out all stat vectors (keep armorSlot for slot detection)
        if (definition.armor != null) {
            definition.armor.statModifiers = null;
            definition.armor.baseDamageResistance = 0;
            definition.armor.damageResistance = null;
            definition.armor.damageEnhancement = null;
            definition.armor.damageClassEnhancement = null;
        }
        // Weapon: zero out all stat vectors
        if (definition.weapon != null) {
            definition.weapon.statModifiers = null;
            definition.weapon.entityStatsToClear = null;
        }
        // Utility: zero out all stat vectors
        if (definition.utility != null) {
            definition.utility.statModifiers = null;
            definition.utility.entityStatsToClear = null;
        }
    }

    /**
     * Builds a formatted RPG crafting preview using Hytale's native markup.
     *
     * @param effectiveLevel The level the player would get (clamped to their level)
     * @param minLevel The material's minimum level
     * @param materialCeiling The material's maximum level
     */
    private String buildPreviewDescription(
            int effectiveLevel, int minLevel, int materialCeiling,
            @Nonnull GearRarity maxRarity,
            int minMods, int maxMods) {

        StringBuilder desc = new StringBuilder();

        // Header
        desc.append(colored("Crafts as RPG Gear", "#FFD700", true));
        desc.append("\n\n");

        // Level — show effective level with material range
        desc.append(colored("Level : ", TooltipStyles.LABEL_GRAY, false));
        desc.append(colored(String.valueOf(effectiveLevel), TooltipStyles.VALUE_WHITE, false));
        desc.append(colored(" [" + minLevel + " to " + materialCeiling + "]", TooltipStyles.LABEL_GRAY, false));
        desc.append("\n");

        // Rarity — colored in the rarity's own color
        desc.append(colored("Rarity : ", TooltipStyles.LABEL_GRAY, false));
        desc.append(colored("up to ", TooltipStyles.LABEL_GRAY, false));
        desc.append(colored(maxRarity.getHytaleQualityId(), maxRarity.getHexColor(), true));
        desc.append("\n");

        // Modifier count — white values
        String modsText = minMods + " to " + maxMods + " random";
        desc.append(colored("Mods : ", TooltipStyles.LABEL_GRAY, false));
        desc.append(colored(modsText, TooltipStyles.VALUE_WHITE, false));
        desc.append("\n");

        // Quality — white
        desc.append(colored("Quality : ", TooltipStyles.LABEL_GRAY, false));
        desc.append(colored("rolled on craft", TooltipStyles.VALUE_WHITE, false));
        desc.append("\n\n");

        // Footer hint — subtle gray italic
        desc.append("<i>");
        desc.append(colored("Rarity, modifiers, and quality are randomized each time you craft.", TooltipStyles.SEPARATOR, false));
        desc.append("</i>");

        return desc.toString();
    }

    /**
     * Builds a formatted reskin description using Hytale's native markup.
     * Mirrors the style of {@link #buildPreviewDescription} but communicates
     * that all gear properties are preserved — only the visual appearance changes.
     */
    private String buildReskinDescription() {
        StringBuilder desc = new StringBuilder();

        // Header
        desc.append(colored("Appearance Reskin", "#FFD700", true));
        desc.append("\n\n");

        // Preserved properties — green values to convey "safe/kept"
        String preservedColor = "#55FF55";
        desc.append(colored("Level : ", TooltipStyles.LABEL_GRAY, false));
        desc.append(colored("preserved", preservedColor, false));
        desc.append("\n");

        desc.append(colored("Rarity : ", TooltipStyles.LABEL_GRAY, false));
        desc.append(colored("preserved", preservedColor, false));
        desc.append("\n");

        desc.append(colored("Quality : ", TooltipStyles.LABEL_GRAY, false));
        desc.append(colored("preserved", preservedColor, false));
        desc.append("\n");

        desc.append(colored("Modifiers : ", TooltipStyles.LABEL_GRAY, false));
        desc.append(colored("preserved", preservedColor, false));
        desc.append("\n\n");

        // Footer
        desc.append("<i>");
        desc.append(colored("Only the visual model changes. All stats remain identical.", TooltipStyles.SEPARATOR, false));
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

    /**
     * Per-item preview metadata stored at init time.
     * Used to compute personalized translations at sync time.
     */
    private record ItemPreviewData(int minLevel, int materialCeiling, GearRarity maxRarity, int minMods, int maxMods) {}
}
