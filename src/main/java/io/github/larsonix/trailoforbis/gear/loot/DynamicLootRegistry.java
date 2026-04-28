package io.github.larsonix.trailoforbis.gear.loot;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.ItemArmorSlot;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemArmor;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemQuality;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemWeapon;
import com.hypixel.hytale.server.core.asset.type.itemsound.config.ItemSoundSet;

import io.github.larsonix.trailoforbis.compat.HexcodeCompat;
import io.github.larsonix.trailoforbis.compat.HexcodeItemConfig;
import io.github.larsonix.trailoforbis.gear.loot.LootGenerator.EquipmentSlot;
import io.github.larsonix.trailoforbis.gear.model.ArmorMaterial;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.gear.model.WeaponType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

/**
 * Runtime registry that discovers droppable weapons and armor from Hytale's asset map.
 *
 * <p>Instead of maintaining a static list of item IDs, this registry scans
 * {@code Item.getAssetMap()} at server startup to find all items that:
 * <ul>
 *   <li>Have {@code item.getWeapon() != null} (weapons)</li>
 *   <li>Have {@code item.getArmor() != null} (armor)</li>
 * </ul>
 *
 * <p>This makes the plugin automatically compatible with any mod that properly
 * registers weapons/armor using Hytale's standard Item API, without requiring
 * configuration changes.
 *
 * <h2>Filtering</h2>
 * <p>Items are filtered out if they:
 * <ul>
 *   <li>Match a blacklist pattern</li>
 *   <li>Are from a blacklisted mod</li>
 *   <li>Have quality "Technical", "Debug", "Developer", or "Tool"</li>
 *   <li>Are stackable (maxStack > 1, indicating non-equipment items)</li>
 *   <li>Have hideFromSearch=true in their quality</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>This class is thread-safe after {@link #discoverItems()} is called.
 * The discovery method should be called once during plugin initialization.
 */
public final class DynamicLootRegistry {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /**
     * Quality IDs that indicate internal/debug items (should not drop as loot).
     */
    private static final Set<String> EXCLUDED_QUALITIES = Set.of(
            "technical", "debug", "developer", "tool", "template"
    );

    // Items grouped by equipment slot
    private final Map<EquipmentSlot, List<DiscoveredItem>> itemsBySlot;

    // Items grouped by category within each slot (for stratified selection)
    private final Map<EquipmentSlot, Map<String, List<DiscoveredItem>>> itemsByCategory;

    // Items grouped by quality → category within each slot (for rarity-filtered skin selection)
    private final Map<EquipmentSlot, Map<String, Map<String, List<DiscoveredItem>>>> itemsByQualityCategory;

    // All discovered items for statistics
    private final List<DiscoveredItem> allItems;

    // Items grouped by mod source for statistics
    private final Map<String, List<DiscoveredItem>> itemsByMod;

    // Config for filtering
    private final LootDiscoveryConfig config;

    // Hexcode item level-range config (null if Hexcode not loaded or not configured)
    @Nullable
    private final HexcodeItemConfig hexcodeItemConfig;

    // Compiled blacklist patterns for efficiency
    private final List<Pattern> blacklistPatterns;

    // Whether discovery has been performed
    private volatile boolean discovered = false;

    /**
     * Creates a DynamicLootRegistry with the given configuration.
     *
     * @param config The discovery configuration
     */
    public DynamicLootRegistry(@Nonnull LootDiscoveryConfig config) {
        this(config, null);
    }

    /**
     * Creates a DynamicLootRegistry with the given configuration and Hexcode item config.
     *
     * @param config            The discovery configuration
     * @param hexcodeItemConfig Hexcode item level-range mappings (null if Hexcode absent)
     */
    public DynamicLootRegistry(@Nonnull LootDiscoveryConfig config, @Nullable HexcodeItemConfig hexcodeItemConfig) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.hexcodeItemConfig = hexcodeItemConfig;
        this.itemsBySlot = new EnumMap<>(EquipmentSlot.class);
        this.itemsByCategory = new EnumMap<>(EquipmentSlot.class);
        this.itemsByQualityCategory = new EnumMap<>(EquipmentSlot.class);
        this.allItems = new ArrayList<>();
        this.itemsByMod = new HashMap<>();

        // Pre-compile blacklist patterns
        this.blacklistPatterns = compileBlacklistPatterns(config);

        // Initialize empty lists for all slots
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            itemsBySlot.put(slot, new ArrayList<>());
            itemsByCategory.put(slot, new HashMap<>());
        }
    }

    /**
     * Creates a DynamicLootRegistry with default configuration.
     */
    public DynamicLootRegistry() {
        this(new LootDiscoveryConfig(), null);
    }

    /**
     * Compiles blacklist patterns from config.
     */
    private static List<Pattern> compileBlacklistPatterns(LootDiscoveryConfig config) {
        List<Pattern> patterns = new ArrayList<>();

        for (String patternStr : config.getBlacklistItems()) {
            try {
                // Convert glob-style pattern to regex
                String regex = patternStr
                        .replace(".", "\\.")
                        .replace("*", ".*")
                        .replace("?", ".");
                patterns.add(Pattern.compile("^" + regex + "$", Pattern.CASE_INSENSITIVE));
            } catch (Exception e) {
                LOGGER.atWarning().log("Invalid blacklist pattern: %s - %s", patternStr, e.getMessage());
            }
        }

        return patterns;
    }

    /**
     * Scans all registered items and builds the loot pools.
     *
     * <p>This should be called once during plugin initialization, after all mods
     * have registered their items (typically in onEnable after other systems init).
     *
     * <p>Calling this multiple times will clear and rebuild the registry.
     */
    public void discoverItems() {
        if (!config.isEnabled()) {
            LOGGER.atInfo().log("Dynamic loot discovery is disabled");
            discovered = true;
            return;
        }

        // Clear existing data
        allItems.clear();
        itemsByMod.clear();
        for (List<DiscoveredItem> list : itemsBySlot.values()) {
            list.clear();
        }
        for (Map<String, List<DiscoveredItem>> map : itemsByCategory.values()) {
            map.clear();
        }
        for (Map<String, Map<String, List<DiscoveredItem>>> map : itemsByQualityCategory.values()) {
            map.clear();
        }

        LOGGER.atInfo().log("Scanning item registry for droppable equipment...");

        int scanned = 0;
        int weapons = 0;
        int armor = 0;
        int skipped = 0;

        // Scan all registered items
        for (Item item : Item.getAssetMap().getAssetMap().values()) {
            scanned++;

            // Skip null or unknown items
            if (item == null || item == Item.UNKNOWN) {
                continue;
            }

            String itemId = item.getId();
            if (itemId == null || itemId.isEmpty()) {
                continue;
            }

            // Check blacklist
            if (isBlacklisted(itemId, item)) {
                skipped++;
                continue;
            }

            // Determine equipment slot
            EquipmentSlot slot = determineSlot(item);
            if (slot == null) {
                // Not a weapon or armor
                continue;
            }

            // Get mod source
            String modSource = Item.getAssetMap().getAssetPack(itemId);
            if (modSource == null) {
                modSource = DiscoveredItem.VANILLA_SOURCE;
            }

            // Resolve Hytale quality ID for skin filtering
            String qualityId = resolveQualityId(item);

            // Create discovered item
            DiscoveredItem discoveredItem = new DiscoveredItem(itemId, slot, modSource, qualityId);

            // Add to collections
            allItems.add(discoveredItem);
            itemsBySlot.get(slot).add(discoveredItem);
            itemsByMod.computeIfAbsent(modSource, k -> new ArrayList<>()).add(discoveredItem);

            // Group by category for stratified selection.
            // Weapons: keyword-based type detection from item ID.
            // Armor: ItemSoundSet-based material detection, falling back to name parsing.
            String category;
            if (slot == EquipmentSlot.WEAPON || slot == EquipmentSlot.OFF_HAND) {
                category = WeaponType.fromItemIdOrUnknown(itemId).name();
            } else {
                category = classifyArmorMaterial(item, itemId).name();
            }
            itemsByCategory.get(slot)
                    .computeIfAbsent(category, k -> new ArrayList<>())
                    .add(discoveredItem);

            // Populate quality-category index for rarity-filtered skin selection
            itemsByQualityCategory
                    .computeIfAbsent(slot, k -> new HashMap<>())
                    .computeIfAbsent(qualityId, k -> new HashMap<>())
                    .computeIfAbsent(category, k -> new ArrayList<>())
                    .add(discoveredItem);

            if (discoveredItem.isWeapon()) {
                weapons++;
            } else {
                armor++;
            }
        }

        // Force-include Hexcode book items that lack a Weapon field in their JSON.
        // These items won't be discovered by the main scan (getWeapon() returns null)
        // but are valid spellbook equipment when Hexcode is loaded.
        int hexcodeForced = 0;
        if (HexcodeCompat.isLoaded() && hexcodeItemConfig != null && hexcodeItemConfig.isEnabled()) {
            Set<String> alreadyDiscovered = new HashSet<>();
            for (DiscoveredItem item : allItems) {
                alreadyDiscovered.add(item.itemId());
            }

            for (String bookId : hexcodeItemConfig.getBookItemIds()) {
                if (alreadyDiscovered.contains(bookId)) {
                    continue; // Already discovered via Weapon field
                }

                // Look up the item in the asset map
                Item bookItem = Item.getAssetMap().getAsset(bookId);
                if (bookItem == null || bookItem == Item.UNKNOWN) {
                    LOGGER.atFine().log("Hexcode book '%s' not found in asset map, skipping", bookId);
                    continue;
                }

                if (isBlacklisted(bookId, bookItem)) {
                    continue;
                }

                String modSource = Item.getAssetMap().getAssetPack(bookId);
                if (modSource == null) {
                    modSource = DiscoveredItem.VANILLA_SOURCE;
                }
                String qualityId = resolveQualityId(bookItem);

                // Books go in OFF_HAND — they are off-hand utility items, not main-hand weapons
                DiscoveredItem discoveredItem = new DiscoveredItem(bookId, EquipmentSlot.OFF_HAND, modSource, qualityId);
                allItems.add(discoveredItem);
                itemsBySlot.get(EquipmentSlot.OFF_HAND).add(discoveredItem);
                itemsByMod.computeIfAbsent(modSource, k -> new ArrayList<>()).add(discoveredItem);

                String category = WeaponType.fromItemIdOrUnknown(bookId).name();
                itemsByCategory.get(EquipmentSlot.OFF_HAND)
                        .computeIfAbsent(category, k -> new ArrayList<>())
                        .add(discoveredItem);

                itemsByQualityCategory
                        .computeIfAbsent(EquipmentSlot.OFF_HAND, k -> new HashMap<>())
                        .computeIfAbsent(qualityId, k -> new HashMap<>())
                        .computeIfAbsent(category, k -> new ArrayList<>())
                        .add(discoveredItem);

                weapons++;
                hexcodeForced++;
            }

            if (hexcodeForced > 0) {
                LOGGER.atInfo().log("Force-included %d Hexcode book items into loot pool", hexcodeForced);
            }
        }

        // Sort items within each slot and category for consistent ordering
        for (List<DiscoveredItem> list : itemsBySlot.values()) {
            list.sort(Comparator.comparing(DiscoveredItem::itemId));
        }
        for (Map<String, List<DiscoveredItem>> categories : itemsByCategory.values()) {
            for (List<DiscoveredItem> list : categories.values()) {
                list.sort(Comparator.comparing(DiscoveredItem::itemId));
            }
        }
        for (Map<String, Map<String, List<DiscoveredItem>>> qualityMap : itemsByQualityCategory.values()) {
            for (Map<String, List<DiscoveredItem>> categories : qualityMap.values()) {
                for (List<DiscoveredItem> list : categories.values()) {
                    list.sort(Comparator.comparing(DiscoveredItem::itemId));
                }
            }
        }

        discovered = true;

        LOGGER.atInfo().log("Discovered %d droppable items (%d weapons [%d hexcode forced], %d armor) from %d total items (%d skipped)",
                allItems.size(), weapons, hexcodeForced, armor, scanned, skipped);

        // Log per-slot counts with category breakdown
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            int count = itemsBySlot.get(slot).size();
            if (count > 0) {
                LOGGER.atInfo().log("  %s: %d items", slot.name().toLowerCase(), count);
                Map<String, List<DiscoveredItem>> categories = itemsByCategory.get(slot);
                if (categories != null) {
                    categories.entrySet().stream()
                            .sorted(Map.Entry.comparingByKey())
                            .forEach(e -> LOGGER.atInfo().log("    %s: %d skins", e.getKey(), e.getValue().size()));
                }
            }
        }

        // Log mod sources
        LOGGER.atInfo().log("Items by mod source:");
        itemsByMod.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()))
                .forEach(entry -> LOGGER.atInfo().log("  %s: %d items", entry.getKey(), entry.getValue().size()));

        // Log quality distribution for skin filtering diagnostics
        Map<String, Integer> qualityCounts = new TreeMap<>();
        for (DiscoveredItem item : allItems) {
            qualityCounts.merge(item.qualityId(), 1, Integer::sum);
        }
        LOGGER.atInfo().log("Items by quality tier (for skin filtering):");
        qualityCounts.forEach((q, c) -> LOGGER.atInfo().log("  %s: %d items", q, c));

        // Log unclassified residuals for diagnostics
        logUnclassifiedResiduals();
    }

    /**
     * Determines the equipment slot for an item.
     *
     * @param item The item to classify
     * @return The equipment slot, or null if not equipment
     */
    @Nullable
    private EquipmentSlot determineSlot(Item item) {
        // Check for weapon first (weapons take priority over armor)
        ItemWeapon weapon = item.getWeapon();
        if (weapon != null && config.isIncludeWeapons()) {
            String itemId = item.getId();
            // Shields are weapons in Hytale's system but go in OFF_HAND
            if (itemId != null && itemId.toLowerCase().contains("shield") && config.isIncludeShields()) {
                return EquipmentSlot.OFF_HAND;
            }
            // All spellbooks go in OFF_HAND — they pair with a staff in main hand for hex casting.
            // This applies to all mods: Hexcode books, vanilla spellbooks, and other mods.
            if (itemId != null && WeaponType.fromItemIdOrUnknown(itemId) == WeaponType.SPELLBOOK) {
                return EquipmentSlot.OFF_HAND;
            }
            return EquipmentSlot.WEAPON;
        }

        // Check for armor
        ItemArmor armor = item.getArmor();
        if (armor != null && config.isIncludeArmor()) {
            ItemArmorSlot armorSlot = armor.getArmorSlot();
            if (armorSlot == null) {
                return null;
            }

            return switch (armorSlot) {
                case Head -> EquipmentSlot.HEAD;
                case Chest -> EquipmentSlot.CHEST;
                case Legs -> EquipmentSlot.LEGS;
                case Hands -> EquipmentSlot.HANDS;
            };
        }

        return null;
    }

    /**
     * Checks if an item should be excluded from loot.
     *
     * @param itemId The item ID
     * @param item   The item instance
     * @return true if the item should be excluded
     */
    private boolean isBlacklisted(String itemId, Item item) {
        // Exclude RPG plugin custom items (gear, stones, maps)
        // These are dynamically registered items that must NOT re-enter the loot pool
        if (itemId.startsWith("rpg_")) {
            return true;
        }

        // Exclude Hexcode template items (JSON template parents, not actual droppable items)
        if (itemId.startsWith("Template_Hex")) {
            return true;
        }

        // Check stackable items (non-equipment)
        if (item.getMaxStack() > 1) {
            return true;
        }

        // Check quality exclusions
        int qualityIndex = item.getQualityIndex();
        if (qualityIndex > 0) {
            ItemQuality quality = ItemQuality.getAssetMap().getAsset(qualityIndex);
            if (quality != null) {
                // Check if quality is hidden from search
                if (quality.isHiddenFromSearch()) {
                    return true;
                }
                // Check excluded quality IDs
                String qualityId = quality.getId();
                if (qualityId != null && EXCLUDED_QUALITIES.contains(qualityId.toLowerCase())) {
                    return true;
                }
            }
        }

        // Check blacklist patterns
        for (Pattern pattern : blacklistPatterns) {
            if (pattern.matcher(itemId).matches()) {
                return true;
            }
        }

        // Check mod blacklist
        String modSource = Item.getAssetMap().getAssetPack(itemId);
        if (modSource != null) {
            for (String blacklistedMod : config.getBlacklistMods()) {
                // Check both full source (PackId:PackName) and just pack ID
                if (modSource.equalsIgnoreCase(blacklistedMod) ||
                    modSource.toLowerCase().startsWith(blacklistedMod.toLowerCase() + ":")) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Resolves the Hytale ItemQuality ID for an item.
     *
     * <p>Falls back to {@link DiscoveredItem#DEFAULT_QUALITY} if the item
     * has no quality assigned or the quality cannot be read.
     *
     * @param item The item to resolve quality for
     * @return The quality ID string (e.g., "Common", "Rare", "Epic")
     */
    @Nonnull
    private String resolveQualityId(@Nonnull Item item) {
        try {
            int qualityIndex = item.getQualityIndex();
            if (qualityIndex > 0) {
                ItemQuality quality = ItemQuality.getAssetMap().getAsset(qualityIndex);
                if (quality != null) {
                    String id = quality.getId();
                    if (id != null && !id.isEmpty()) {
                        return id;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.atFine().log("Quality resolution failed for %s: %s", item.getId(), e.getMessage());
        }
        return DiscoveredItem.DEFAULT_QUALITY;
    }

    /**
     * Classifies armor material using a layered approach:
     * <ol>
     *   <li>ItemSoundSet lookup (ISS_Armor_Heavy → PLATE, etc.)</li>
     *   <li>Item ID name parsing (Armor_Iron_Head → PLATE via "iron")</li>
     *   <li>Fallback to SPECIAL</li>
     * </ol>
     */
    @Nonnull
    private ArmorMaterial classifyArmorMaterial(@Nonnull Item item, @Nonnull String itemId) {
        // Layer 1: ItemSoundSet-based classification
        try {
            int soundSetIndex = item.getItemSoundSetIndex();
            ItemSoundSet soundSet = ItemSoundSet.getAssetMap().getAsset(soundSetIndex);
            if (soundSet != null) {
                Optional<ArmorMaterial> fromSoundSet = ArmorMaterial.fromSoundSetId(
                        soundSet.getId(), config.getSoundSetOverrides());
                if (fromSoundSet.isPresent()) {
                    return fromSoundSet.get();
                }
            }
        } catch (Exception e) {
            LOGGER.atFine().log("ItemSoundSet lookup failed for %s: %s", itemId, e.getMessage());
        }

        // Layer 2: Name-based classification (existing logic)
        return ArmorMaterial.fromItemIdOrSpecial(itemId);
    }

    /**
     * Logs items that ended up in UNKNOWN/SPECIAL catch-all categories
     * for diagnostic purposes. Helps identify items that need config overrides
     * or additional classification keywords.
     */
    private void logUnclassifiedResiduals() {
        List<String> unknownWeapons = new ArrayList<>();
        List<String> specialArmor = new ArrayList<>();

        for (Map.Entry<EquipmentSlot, Map<String, List<DiscoveredItem>>> slotEntry : itemsByCategory.entrySet()) {
            Map<String, List<DiscoveredItem>> categories = slotEntry.getValue();
            EquipmentSlot slot = slotEntry.getKey();

            if (slot == EquipmentSlot.WEAPON || slot == EquipmentSlot.OFF_HAND) {
                List<DiscoveredItem> unknown = categories.getOrDefault("UNKNOWN", List.of());
                for (DiscoveredItem item : unknown) {
                    unknownWeapons.add(item.itemId() + " (" + item.getModDisplayName() + ")");
                }
            } else {
                List<DiscoveredItem> special = categories.getOrDefault("SPECIAL", List.of());
                for (DiscoveredItem item : special) {
                    specialArmor.add(item.itemId() + " [" + slot.name().toLowerCase() + "] (" + item.getModDisplayName() + ")");
                }
            }
        }

        if (!unknownWeapons.isEmpty()) {
            LOGGER.atInfo().log("Unclassified weapons (UNKNOWN): %d items", unknownWeapons.size());
            // Log up to 20 examples to avoid log spam
            unknownWeapons.stream().limit(20)
                    .forEach(s -> LOGGER.atInfo().log("  %s", s));
            if (unknownWeapons.size() > 20) {
                LOGGER.atInfo().log("  ... and %d more", unknownWeapons.size() - 20);
            }
        }

        if (!specialArmor.isEmpty()) {
            LOGGER.atInfo().log("Unclassified armor (SPECIAL): %d items", specialArmor.size());
            specialArmor.stream().limit(20)
                    .forEach(s -> LOGGER.atInfo().log("  %s", s));
            if (specialArmor.size() > 20) {
                LOGGER.atInfo().log("  ... and %d more", specialArmor.size() - 20);
            }
        }

        if (unknownWeapons.isEmpty() && specialArmor.isEmpty()) {
            LOGGER.atInfo().log("All items classified successfully (0 UNKNOWN weapons, 0 SPECIAL armor)");
        }
    }

    // =========================================================================
    // ACCESSORS
    // =========================================================================

    /**
     * Gets all items for a specific equipment slot.
     *
     * @param slot The equipment slot
     * @return Unmodifiable list of discovered items for that slot
     */
    @Nonnull
    public List<DiscoveredItem> getItemsForSlot(@Nonnull EquipmentSlot slot) {
        ensureDiscovered();
        return Collections.unmodifiableList(itemsBySlot.getOrDefault(slot, List.of()));
    }

    /**
     * Gets items grouped by category for a specific slot and vanilla quality tier.
     *
     * <p>Returns a map of category name (e.g., "SWORD", "PLATE") to list of items
     * in that category at the given quality. Used by the reskin recipe generator
     * to create StructuralCrafting recipes for the Builder's Workbench.
     *
     * @param slot      The equipment slot
     * @param qualityId The vanilla quality ID (e.g., "Common", "Epic")
     * @return Unmodifiable map of category → items, empty map if none
     */
    @Nonnull
    public Map<String, List<DiscoveredItem>> getItemsByQualityAndCategory(
            @Nonnull EquipmentSlot slot, @Nonnull String qualityId) {
        ensureDiscovered();
        Map<String, Map<String, List<DiscoveredItem>>> qualityMap =
                itemsByQualityCategory.getOrDefault(slot, Map.of());
        Map<String, List<DiscoveredItem>> categories = qualityMap.getOrDefault(qualityId, Map.of());
        return Collections.unmodifiableMap(categories);
    }

    /**
     * Selects a random item for a slot using stratified two-tier selection.
     *
     * <p>Tier 1: Pick a category (weapon type or armor material) using configured weights.
     * <p>Tier 2: Pick a random skin within that category.
     *
     * <p>This prevents weapon types with many skins (e.g. swords) from dominating
     * drops over types with fewer skins (e.g. daggers).
     *
     * @param slot The equipment slot
     * @return A random item ID, or null if no items available
     */
    @Nullable
    public String selectRandomItem(@Nonnull EquipmentSlot slot) {
        ensureDiscovered();
        Map<String, List<DiscoveredItem>> categories = itemsByCategory.get(slot);
        return selectFromCategories(categories, slot);
    }

    /**
     * Selects a random item for a slot, filtered by the allowed skin qualities
     * for the given RPG rarity.
     *
     * <p>This ensures visual identity per rarity tier: Common RPG items use
     * Common-quality skins (crude, wood), Epic items use Epic-quality skins
     * (adamantite, cindercloth), etc.
     *
     * <p>Falls back to unfiltered selection if no items match the allowed qualities.
     *
     * @param slot   The equipment slot
     * @param rarity The RPG rarity determining allowed skin qualities
     * @return A random item ID matching the quality filter, or null if none available
     */
    @Nullable
    public String selectRandomItemForRarity(@Nonnull EquipmentSlot slot, @Nonnull GearRarity rarity) {
        ensureDiscovered();

        Set<String> allowedQualities = rarity.getAllowedSkinQualities();
        Map<String, Map<String, List<DiscoveredItem>>> qualityMap =
                itemsByQualityCategory.getOrDefault(slot, Map.of());

        // Merge categories from all allowed quality tiers
        Map<String, List<DiscoveredItem>> mergedCategories = new HashMap<>();
        for (String q : allowedQualities) {
            Map<String, List<DiscoveredItem>> qCategories = qualityMap.getOrDefault(q, Map.of());
            for (Map.Entry<String, List<DiscoveredItem>> entry : qCategories.entrySet()) {
                mergedCategories.computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
                        .addAll(entry.getValue());
            }
        }

        if (mergedCategories.isEmpty()) {
            LOGGER.atWarning().log(
                    "No items for slot %s with qualities %s — falling back to unfiltered",
                    slot, allowedQualities);
            return selectRandomItem(slot);
        }

        return selectFromCategories(mergedCategories, slot);
    }

    /**
     * Checks if a discovered item is eligible for a given drop level based on
     * Hexcode level-range config.
     *
     * <p>Non-Hexcode items always return true. Hexcode items are checked against
     * their configured level range. If no config is loaded, all items are eligible.
     *
     * @param itemId    The item ID to check
     * @param dropLevel The drop level (player/mob level)
     * @return true if the item can drop at this level
     */
    public boolean isItemEligibleForLevel(@Nonnull String itemId, int dropLevel) {
        if (hexcodeItemConfig == null || !hexcodeItemConfig.isEnabled()) {
            return true;
        }
        return hexcodeItemConfig.isInLevelRange(itemId, dropLevel);
    }

    /**
     * Gets the Hexcode item config, if present.
     *
     * @return The Hexcode item config, or null if not configured
     */
    @Nullable
    public HexcodeItemConfig getHexcodeItemConfig() {
        return hexcodeItemConfig;
    }

    /**
     * Stratified two-tier selection from a category map.
     *
     * <p>Tier 1: Pick a category using configured weights.
     * <p>Tier 2: Pick a random item within that category.
     *
     * @param categories Map of category name to item list
     * @param slot       The equipment slot (determines which weight config to use)
     * @return A random item ID, or null if categories is empty
     */
    @Nullable
    private String selectFromCategories(
            @Nullable Map<String, List<DiscoveredItem>> categories,
            @Nonnull EquipmentSlot slot
    ) {
        if (categories == null || categories.isEmpty()) {
            return null;
        }

        // Get config weights based on slot type
        Map<String, Double> weights = (slot == EquipmentSlot.WEAPON || slot == EquipmentSlot.OFF_HAND)
                ? config.getWeaponCategoryWeights()
                : config.getArmorCategoryWeights();

        // Build weighted pool (only non-empty categories with positive weight)
        double totalWeight = 0;
        List<Map.Entry<String, Double>> pool = new ArrayList<>();
        for (Map.Entry<String, List<DiscoveredItem>> entry : categories.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                double w = weights.getOrDefault(entry.getKey(), 1.0);
                if (w > 0) {
                    pool.add(Map.entry(entry.getKey(), w));
                    totalWeight += w;
                }
            }
        }
        if (pool.isEmpty()) {
            return null;
        }

        // Tier 1: Pick category via cumulative weight
        double roll = ThreadLocalRandom.current().nextDouble() * totalWeight;
        String selectedCategory = pool.getLast().getKey();
        double cumulative = 0;
        for (Map.Entry<String, Double> entry : pool) {
            cumulative += entry.getValue();
            if (roll < cumulative) {
                selectedCategory = entry.getKey();
                break;
            }
        }

        // Tier 2: Pick random skin within category
        List<DiscoveredItem> items = categories.get(selectedCategory);
        return items.get(ThreadLocalRandom.current().nextInt(items.size())).itemId();
    }

    /**
     * Gets all discovered items.
     *
     * @return Unmodifiable list of all discovered items
     */
    @Nonnull
    public List<DiscoveredItem> getAllItems() {
        ensureDiscovered();
        return Collections.unmodifiableList(allItems);
    }

    /**
     * Gets items grouped by mod source.
     *
     * @return Unmodifiable map of mod source to items
     */
    @Nonnull
    public Map<String, List<DiscoveredItem>> getItemsByMod() {
        ensureDiscovered();
        return Collections.unmodifiableMap(itemsByMod);
    }

    /**
     * Gets the count of items for a specific slot.
     *
     * @param slot The equipment slot
     * @return Number of items for that slot
     */
    public int getItemCount(@Nonnull EquipmentSlot slot) {
        ensureDiscovered();
        return itemsBySlot.getOrDefault(slot, List.of()).size();
    }

    /**
     * Gets the total count of all discovered items.
     *
     * @return Total item count
     */
    public int getTotalItemCount() {
        ensureDiscovered();
        return allItems.size();
    }

    /**
     * Gets the slot weights from configuration.
     *
     * @return Map of slot to weight
     */
    @Nonnull
    public Map<EquipmentSlot, Integer> getSlotWeights() {
        return config.getSlotWeights();
    }

    /**
     * Whether discovery has been performed.
     *
     * @return true if discoverItems() has been called
     */
    public boolean isDiscovered() {
        return discovered;
    }

    /**
     * Gets the configuration used by this registry.
     *
     * @return The discovery configuration
     */
    @Nonnull
    public LootDiscoveryConfig getConfig() {
        return config;
    }

    /**
     * Ensures discovery has been performed.
     *
     * @throws IllegalStateException if discovery hasn't been performed
     */
    private void ensureDiscovered() {
        if (!discovered) {
            throw new IllegalStateException("discoverItems() must be called before accessing items");
        }
    }
}
