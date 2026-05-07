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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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

    // ==================== Per-Player Bench Session State ====================

    /** Per-player active bench session. Present only while a crafting bench window is open. */
    private final Map<UUID, ActiveBenchState> activeSessions = new ConcurrentHashMap<>();

    /** Players whose bench session has a window close callback registered. */
    private final ConcurrentHashMap.KeySetView<UUID, Boolean> closeCallbackRegistered = ConcurrentHashMap.newKeySet();

    /** Players currently viewing reskin overlay translations (Builder's Workbench). */
    private final ConcurrentHashMap.KeySetView<UUID, Boolean> reskinActive = ConcurrentHashMap.newKeySet();

    private record ActiveBenchState(int lastSyncedLevel) {}

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

            // Skip our own RPG items (gear: rpg_gear_*, maps: rpg_map_*, etc.).
            // These are registered in the asset map by ItemRegistryService.loadCachedRegistrations()
            // at startup, and they have weapon/armor properties cloned from vanilla — so they'd
            // pass all subsequent filters. Giving them crafting preview text overwrites their
            // real RPG definitions on every syncToPlayer() call.
            if (itemId.startsWith("rpg_")) continue;

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
    /**
     * Rebuilds cached data after config reload.
     * Active bench sessions are not affected — they will pick up new data
     * on their next open/close cycle.
     */
    public void reload() {
        initialize();
    }

    public boolean isInitialized() {
        return cachedDefinitions != null && !cachedDefinitions.isEmpty();
    }

    // ==================== Bench Session Lifecycle ====================

    /**
     * Called when a crafting bench window opens. Sends modified definitions
     * (stripped stats, {@code rpg.crafting.*} keys) and per-player translations
     * to the player. Registers the session so the definitions can be restored
     * when the bench closes.
     *
     * <p>Idempotent — returns early if the player already has an active session.
     *
     * @param playerId The player's UUID
     * @param playerRef The player reference for packet sending
     * @param playerLevel The player's current RPG level
     * @return true if definitions were sent, false if skipped (already active or not initialized)
     */
    public boolean onBenchOpen(@Nonnull UUID playerId, @Nonnull PlayerRef playerRef, int playerLevel) {
        if (!isInitialized()) return false;
        if (activeSessions.containsKey(playerId)) return false;

        try {
            sendDefinitions(playerRef);
            sendTranslations(playerRef, playerLevel);
            activeSessions.put(playerId, new ActiveBenchState(playerLevel));

            LOGGER.atFine().log("Bench session opened for %s (level=%d, %d items)",
                    playerId.toString().substring(0, 8), playerLevel,
                    cachedDefinitions.size());
            return true;
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to open bench session for %s", playerId);
            return false;
        }
    }

    /**
     * Called when a crafting bench window closes. Sends original vanilla
     * definitions back to the player and clears the session.
     *
     * <p>Idempotent — returns early if no active session exists.
     *
     * @param playerId The player's UUID
     * @param playerRef The player reference for packet sending
     */
    public void onBenchClose(@Nonnull UUID playerId, @Nonnull PlayerRef playerRef) {
        if (activeSessions.remove(playerId) == null) return;
        closeCallbackRegistered.remove(playerId);
        reskinActive.remove(playerId);

        try {
            restoreVanillaDefinitions(playerRef);
            LOGGER.atFine().log("Bench session closed for %s — vanilla definitions restored",
                    playerId.toString().substring(0, 8));
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to close bench session for %s", playerId);
        }
    }

    /**
     * Whether the player currently has an active bench session (definitions overridden).
     */
    public boolean isActiveBenchSession(@Nonnull UUID playerId) {
        return activeSessions.containsKey(playerId);
    }

    /**
     * Whether the player has an active session that still needs a window close callback.
     * The UseBlockEvent.Post handler sends definitions immediately but can't register
     * the callback (window doesn't exist yet). The InventoryChangeEvent handler
     * registers it on the first interaction.
     */
    public boolean needsCloseCallback(@Nonnull UUID playerId) {
        return activeSessions.containsKey(playerId) && !closeCallbackRegistered.contains(playerId);
    }

    /**
     * Mark that a close callback has been registered for this player's bench session.
     */
    public void markCloseCallbackRegistered(@Nonnull UUID playerId) {
        closeCallbackRegistered.add(playerId);
    }

    /**
     * Updates translations for a player with an active bench session (e.g., after level-up).
     * Only sends if the level actually changed from the last synced value.
     *
     * @return true if translations were re-sent
     */
    public boolean updateTranslationsForActiveSession(@Nonnull UUID playerId, @Nonnull PlayerRef playerRef, int playerLevel) {
        ActiveBenchState session = activeSessions.get(playerId);
        if (session == null) return false;
        if (session.lastSyncedLevel == playerLevel) return false;

        try {
            sendTranslations(playerRef, playerLevel);
            activeSessions.put(playerId, new ActiveBenchState(playerLevel));
            LOGGER.atFine().log("Updated crafting translations for %s (level %d→%d)",
                    playerId.toString().substring(0, 8), session.lastSyncedLevel, playerLevel);
            return true;
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to update translations for %s", playerId);
            return false;
        }
    }

    /**
     * Clears the bench session without sending packets. Used during world transitions
     * where the client's asset registry is wiped by {@code JoinWorld(clearWorld=true)}.
     */
    public void onWorldTransition(@Nonnull UUID playerId) {
        activeSessions.remove(playerId);
        closeCallbackRegistered.remove(playerId);
        reskinActive.remove(playerId);
    }

    /**
     * Clears all state for a disconnecting player. No packets sent — client is gone.
     */
    public void onPlayerDisconnect(@Nonnull UUID playerId) {
        activeSessions.remove(playerId);
        closeCallbackRegistered.remove(playerId);
        reskinActive.remove(playerId);
    }

    // ==================== Reskin Overlay State ====================

    /** Mark that reskin translations are active for a player (Builder's Workbench open). */
    public void markReskinActive(@Nonnull UUID playerId) {
        reskinActive.add(playerId);
    }

    /** Mark that reskin translations are no longer active. */
    public void markReskinInactive(@Nonnull UUID playerId) {
        reskinActive.remove(playerId);
    }

    /** Whether reskin overlay translations are currently active for a player. */
    public boolean isReskinActive(@Nonnull UUID playerId) {
        return reskinActive.contains(playerId);
    }

    // ==================== Packet Sending (Internal) ====================

    /**
     * Sends modified item definitions (stripped stats, redirected description keys).
     */
    private void sendDefinitions(@Nonnull PlayerRef playerRef) {
        if (cachedDefinitions == null || cachedDefinitions.isEmpty()) return;

        UpdateItems packet = new UpdateItems();
        packet.type = UpdateType.AddOrUpdate;
        packet.items = new HashMap<>(cachedDefinitions);
        packet.updateModels = false;
        packet.updateIcons = false;
        playerRef.getPacketHandler().writeNoCache(packet);
    }

    /**
     * Sends per-player crafting translations based on level.
     */
    private void sendTranslations(@Nonnull PlayerRef playerRef, int playerLevel) {
        if (itemPreviews == null || itemPreviews.isEmpty()) return;

        Map<String, String> translations = buildTranslationsForPlayer(playerLevel);
        UpdateTranslations packet = new UpdateTranslations();
        packet.type = UpdateType.AddOrUpdate;
        packet.translations = translations;
        playerRef.getPacketHandler().writeNoCache(packet);
    }

    // ==================== Vanilla Definition Restore ====================

    /**
     * Sends original unmodified vanilla item definitions back to the player,
     * undoing the crafting preview overrides.
     *
     * <p>Called internally by {@link #onBenchClose(UUID, PlayerRef)} when a
     * crafting bench window closes. Not intended for direct use — use the
     * bench session lifecycle methods instead.
     */
    void restoreVanillaDefinitions(@Nonnull PlayerRef playerRef) {
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
