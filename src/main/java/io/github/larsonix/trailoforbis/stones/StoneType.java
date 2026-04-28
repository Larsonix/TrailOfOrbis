package io.github.larsonix.trailoforbis.stones;

import com.hypixel.hytale.codec.codecs.EnumCodec;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Defines all currency stone types used to modify items.
 *
 * <p>Stones are consumable items that modify gear or realm maps in various ways.
 * Named after Hytale lore elements (Gaia, Varyn, Alterverse, etc.).
 *
 * <p>Stone categories:
 * <ul>
 *   <li><b>Reroll stones:</b> Gaia's Calibration, Ember of Tuning, Alterverse Shard, Orbisian Blessing</li>
 *   <li><b>Enhancement stones:</b> Gaia's Gift, Cartographer's Polish, Spark of Potential, Core of Ascension, Heart of Legends</li>
 *   <li><b>Removal stones:</b> Purging Ember, Erosion Shard, Transmutation Crystal</li>
 *   <li><b>Lock stones:</b> Warden's Seal, Warden's Key</li>
 *   <li><b>Level stones:</b> Fortune's Compass, Alterverse Key, Threshold Stone</li>
 *   <li><b>Special stones:</b> Varyn's Touch (corruption), Gaia's Perfection, Lorekeeper's Scroll, Genesis Stone</li>
 * </ul>
 *
 * <p>Stone rarity uses {@link GearRarity} for consistent drop weighting across
 * the entire loot system. This enables:
 * <ul>
 *   <li>Single source of truth for rarity weights</li>
 *   <li>Integration with player Item Rarity (IIR) bonuses</li>
 *   <li>Consistent visual styling (colors, tooltips)</li>
 *   <li>Config-driven balancing via gear-balance.yml</li>
 * </ul>
 *
 * @see StoneAction
 * @see ModifiableItem
 * @see GearRarity
 */
public enum StoneType {

    // ═══════════════════════════════════════════════════════════════════
    // REROLL STONES
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gaia's Calibration - Rerolls modifier values within their ranges.
     *
     * <p>Does not change modifier types, only their numeric values.
     * Cannot be used on corrupted items.
     */
    GAIAS_CALIBRATION(
        "Gaia's Calibration",
        "Randomises the numeric values of all modifiers",
        ItemTargetType.BOTH,
        GearRarity.RARE,
        false
    ),

    /**
     * Ember of Tuning - Rerolls ONE random modifier's value.
     *
     * <p>More targeted than Gaia's Calibration - only affects one modifier.
     * Useful for fine-tuning specific modifiers.
     * Cannot be used on corrupted items.
     */
    EMBER_OF_TUNING(
        "Ember of Tuning",
        "Randomises the value of one random modifier",
        ItemTargetType.BOTH,
        GearRarity.COMMON,
        false
    ),

    /**
     * Alterverse Shard - Rerolls all unlocked modifiers completely.
     *
     * <p>Removes all unlocked modifiers and rolls new random ones.
     * Locked modifiers are preserved.
     * Cannot be used on corrupted items.
     */
    ALTERVERSE_SHARD(
        "Alterverse Shard",
        "Rerolls all unlocked modifiers with new random modifiers",
        ItemTargetType.BOTH,
        GearRarity.RARE,
        false
    ),

    /**
     * Orbisian Blessing - Rerolls the item's quality.
     *
     * <p>Randomly changes quality within 1-100 range.
     * Cannot roll perfect quality (101).
     * Cannot be used on corrupted items.
     */
    ORBISIAN_BLESSING(
        "Orbisian Blessing",
        "Randomises the quality of an item",
        ItemTargetType.BOTH,
        GearRarity.UNCOMMON,
        false
    ),

    /**
     * Ethereal Calibration - Rerolls a weapon's implicit damage value.
     *
     * <p>Rerolls the implicit (base) damage value within its range.
     * Does not change the damage range itself, only the rolled value.
     * Only works on weapons that have an implicit damage stat.
     * Cannot be used on corrupted items.
     */
    ETHEREAL_CALIBRATION(
        "Ethereal Calibration",
        "Rerolls the implicit damage value of a weapon",
        ItemTargetType.GEAR_ONLY,
        GearRarity.RARE,
        false
    ),

    // ═══════════════════════════════════════════════════════════════════
    // ENHANCEMENT STONES
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gaia's Gift - Adds a new random modifier.
     *
     * <p>Adds one random modifier to an item that has room for more.
     * Cannot be used if item is at max modifiers.
     * Cannot be used on corrupted items.
     */
    GAIAS_GIFT(
        "Gaia's Gift",
        "Adds a new random modifier to an item",
        ItemTargetType.BOTH,
        GearRarity.UNCOMMON,
        false
    ),

    /**
     * Spark of Potential - Upgrades Common to Uncommon.
     *
     * <p>Transforms a Common (white) item into an Uncommon (green) item.
     * Also adds 1 random modifier as part of the upgrade.
     * Only works on Common rarity items.
     * Cannot be used on corrupted items.
     */
    SPARK_OF_POTENTIAL(
        "Spark of Potential",
        "Upgrades Common to Uncommon and adds 1 modifier",
        ItemTargetType.BOTH,
        GearRarity.UNCOMMON,
        false
    ),

    /**
     * Core of Ascension - Upgrades Uncommon to Rare.
     *
     * <p>Transforms an Uncommon (green) item into a Rare (blue) item.
     * Also adds 1 random modifier as part of the upgrade.
     * Only works on Uncommon rarity items.
     * Cannot be used on corrupted items.
     */
    CORE_OF_ASCENSION(
        "Core of Ascension",
        "Upgrades Uncommon to Rare and adds 1 modifier",
        ItemTargetType.BOTH,
        GearRarity.RARE,
        false
    ),

    /**
     * Heart of Legends - Upgrades Rare to Epic.
     *
     * <p>Transforms a Rare (blue) item into an Epic (purple) item.
     * Also adds 1 random modifier as part of the upgrade.
     * Only works on Rare rarity items.
     * Cannot be used on corrupted items.
     */
    HEART_OF_LEGENDS(
        "Heart of Legends",
        "Upgrades Rare to Epic and adds 1 modifier",
        ItemTargetType.BOTH,
        GearRarity.EPIC,
        false
    ),

    /**
     * Crown of Transcendence - Upgrades Epic to Legendary.
     *
     * <p>Transforms an Epic (purple) item into a Legendary (orange) item.
     * Also adds 1 random modifier as part of the upgrade.
     * Only works on Epic rarity items.
     * Cannot be used on corrupted items.
     */
    CROWN_OF_TRANSCENDENCE(
        "Crown of Transcendence",
        "Upgrades Epic to Legendary and adds 1 modifier",
        ItemTargetType.BOTH,
        GearRarity.LEGENDARY,
        false
    ),

    /**
     * Cartographer's Polish - Improves map quality.
     *
     * <p>Increases quality by 5% (1-5% for identified items).
     * Maximum quality is 100 (101 requires Gaia's Perfection).
     * Cannot be used on corrupted items.
     * Only works on maps.
     */
    CARTOGRAPHERS_POLISH(
        "Cartographer's Polish",
        "Improves the quality of a map",
        ItemTargetType.MAP_ONLY,
        GearRarity.UNCOMMON,
        false
    ),

    // ═══════════════════════════════════════════════════════════════════
    // REMOVAL STONES
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Purging Ember - Removes all unlocked modifiers.
     *
     * <p>Removes all modifiers that aren't locked.
     * Locked modifiers are preserved.
     * Cannot be used on corrupted items.
     */
    PURGING_EMBER(
        "Purging Ember",
        "Removes all unlocked modifiers from an item",
        ItemTargetType.BOTH,
        GearRarity.RARE,
        false
    ),

    /**
     * Erosion Shard - Removes one random unlocked modifier.
     *
     * <p>Removes exactly one random unlocked modifier.
     * Locked modifiers cannot be removed.
     * Cannot be used on corrupted items.
     */
    EROSION_SHARD(
        "Erosion Shard",
        "Removes a random unlocked modifier from an item",
        ItemTargetType.BOTH,
        GearRarity.RARE,
        false
    ),

    /**
     * Transmutation Crystal - Atomic modifier swap.
     *
     * <p>Removes one random unlocked modifier and immediately adds a new one.
     * The operation is atomic - both happen together.
     * Locked modifiers cannot be removed.
     * Cannot be used on corrupted items.
     */
    TRANSMUTATION_CRYSTAL(
        "Transmutation Crystal",
        "Removes one modifier and adds a new one",
        ItemTargetType.BOTH,
        GearRarity.RARE,
        false
    ),

    // ═══════════════════════════════════════════════════════════════════
    // LOCK STONES
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Warden's Seal - Locks a random modifier.
     *
     * <p>Locks one random unlocked modifier, protecting it from rerolls.
     * Locked modifiers persist through Alterverse Shard and Gaia's Calibration.
     * Cannot be used on corrupted items.
     */
    WARDENS_SEAL(
        "Warden's Seal",
        "Locks a random modifier, preventing it from being changed",
        ItemTargetType.BOTH,
        GearRarity.LEGENDARY,
        false
    ),

    /**
     * Warden's Key - Unlocks a random locked modifier.
     *
     * <p>Removes the lock from one random locked modifier.
     * Cannot be used on corrupted items.
     */
    WARDENS_KEY(
        "Warden's Key",
        "Unlocks a random locked modifier",
        ItemTargetType.BOTH,
        GearRarity.MYTHIC,
        false
    ),

    // ═══════════════════════════════════════════════════════════════════
    // LEVEL STONES
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Fortune's Compass - Adds Item Quantity bonus to maps.
     *
     * <p>Increases Item Quantity (IQ) by +5%, up to a maximum of 20%.
     * Higher IQ means more items drop from realm mobs.
     * Only works on maps.
     * Cannot be used on corrupted items.
     */
    FORTUNES_COMPASS(
        "Fortune's Compass",
        "Adds +5% Item Quantity to a map (max 20%)",
        ItemTargetType.MAP_ONLY,
        GearRarity.UNCOMMON,
        false
    ),

    /**
     * Alterverse Key - Changes map biome randomly.
     *
     * <p>Rerolls the biome type of a realm map.
     * Different biomes have different mob types and aesthetics.
     * Only works on maps.
     * Cannot be used on corrupted items.
     */
    ALTERVERSE_KEY(
        "Alterverse Key",
        "Changes the biome of a map randomly",
        ItemTargetType.MAP_ONLY,
        GearRarity.EPIC,
        false
    ),

    /**
     * Threshold Stone - Rerolls level within ±3.
     *
     * <p>On maps: Rerolls the map's level within ±3 of current level.
     * On gear: Rerolls the item's level requirement within ±3.
     * Cannot go below level 1.
     * Cannot be used on corrupted items.
     */
    THRESHOLD_STONE(
        "Threshold Stone",
        "Rerolls the level within ±3",
        ItemTargetType.BOTH,
        GearRarity.COMMON,
        false
    ),

    // ═══════════════════════════════════════════════════════════════════
    // SPECIAL STONES
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Varyn's Touch - Corrupts an item with unpredictable results.
     *
     * <p>Corrupts the item and may:
     * <ul>
     *   <li>Do nothing (item is just corrupted)</li>
     *   <li>Reroll all modifiers</li>
     *   <li>Add a special corruption modifier</li>
     *   <li>Upgrade rarity (rare outcome)</li>
     * </ul>
     * Can be used on any item, including already-corrupted ones.
     * Corrupted items cannot be modified by other stones.
     */
    VARYNS_TOUCH(
        "Varyn's Touch",
        "Corrupts an item with unpredictable results",
        ItemTargetType.BOTH,
        GearRarity.EPIC,
        true
    ),

    /**
     * Gaia's Perfection - Guarantees perfect quality.
     *
     * <p>Sets item quality to 101 (perfect).
     * Ultra-rare stone.
     * Cannot be used on corrupted items.
     */
    GAIAS_PERFECTION(
        "Gaia's Perfection",
        "Sets an item's quality to perfect (101)",
        ItemTargetType.BOTH,
        GearRarity.MYTHIC,
        false
    ),

    /**
     * Lorekeeper's Scroll - Identifies an item.
     *
     * <p>Reveals hidden modifiers on unidentified items.
     * Maps drop unidentified and must be identified to see modifiers.
     */
    LOREKEEPERS_SCROLL(
        "Lorekeeper's Scroll",
        "Identifies an unidentified item",
        ItemTargetType.BOTH,
        GearRarity.COMMON,
        true
    ),

    /**
     * Genesis Stone - Fills all remaining modifier slots.
     *
     * <p>Adds random modifiers until all slots are filled.
     * Works on any item that has room for more modifiers.
     * Cannot be used on corrupted items.
     */
    GENESIS_STONE(
        "Genesis Stone",
        "Fills all remaining modifier slots",
        ItemTargetType.BOTH,
        GearRarity.EPIC,
        false
    ),

    // ═══════════════════════════════════════════════════════════════════
    // REFUND STONES (right-click to consume, grants refund points)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Orb of Unlearning - Grants 1 skill tree refund point.
     *
     * <p>Right-click to consume. Does not open the stone picker.
     * Refund points are used to deallocate skill tree nodes.
     */
    ORB_OF_UNLEARNING(
        "Orb of Unlearning",
        "Grants 1 skill tree refund point",
        ItemTargetType.BOTH,
        GearRarity.COMMON,
        true
    ),

    /**
     * Orb of Realignment - Grants 1 attribute refund point.
     *
     * <p>Right-click to consume. Does not open the stone picker.
     * Refund points are used to remove allocated attribute points.
     */
    ORB_OF_REALIGNMENT(
        "Orb of Realignment",
        "Grants 1 attribute refund point",
        ItemTargetType.BOTH,
        GearRarity.COMMON,
        true
    );

    private final String displayName;
    private final String description;
    private final ItemTargetType targetType;
    private final GearRarity rarity;
    private final boolean worksOnCorrupted;
    private final String nativeItemId;

    /**
     * Cached map of stones grouped by rarity for efficient lookup.
     * Lazily initialized on first access.
     */
    private static volatile Map<GearRarity, List<StoneType>> stonesByRarity;

    StoneType(
            @Nonnull String displayName,
            @Nonnull String description,
            @Nonnull ItemTargetType targetType,
            @Nonnull GearRarity rarity,
            boolean worksOnCorrupted) {
        this.displayName = displayName;
        this.description = description;
        this.targetType = targetType;
        this.rarity = rarity;
        this.worksOnCorrupted = worksOnCorrupted;
        this.nativeItemId = buildNativeItemId(this.name());
    }

    /**
     * Builds the native Hytale item ID from the enum name.
     *
     * <p>Converts {@code GAIAS_CALIBRATION} to {@code RPG_Stone_Gaias_Calibration}.
     *
     * @param enumName The enum constant name
     * @return The native item ID matching the JSON filename (without extension)
     */
    private static String buildNativeItemId(String enumName) {
        String[] parts = enumName.split("_");
        StringBuilder sb = new StringBuilder("RPG_Stone");
        for (String part : parts) {
            sb.append('_').append(part.charAt(0)).append(part.substring(1).toLowerCase());
        }
        return sb.toString();
    }

    /**
     * Gets the display name of this stone.
     *
     * @return Human-readable name
     */
    @Nonnull
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Gets the description of what this stone does.
     *
     * @return Description text
     */
    @Nonnull
    public String getDescription() {
        return description;
    }

    /**
     * Gets the item target type for this stone.
     *
     * @return Which items this stone can target
     */
    @Nonnull
    public ItemTargetType getTargetType() {
        return targetType;
    }

    /**
     * Gets the rarity tier of this stone.
     *
     * <p>Uses {@link GearRarity} for consistent drop weighting across
     * gear, maps, and stones. This enables IIR (Item Rarity) bonuses
     * to affect stone drops.
     *
     * @return Stone rarity using the unified GearRarity system
     */
    @Nonnull
    public GearRarity getRarity() {
        return rarity;
    }

    /**
     * Gets the hex color for this stone based on its rarity.
     *
     * <p>Delegates to {@link GearRarity#getHexColor()} for consistent
     * visual styling across the entire loot system.
     *
     * @return Hex color string (e.g., "#2770B7" for Rare)
     */
    @Nonnull
    public String getHexColor() {
        return rarity.getHexColor();
    }

    /**
     * Checks if this stone is a refund stone (consumed on right-click, grants refund points).
     * Refund stones skip the stone picker UI.
     */
    public boolean isRefundStone() {
        return this == ORB_OF_UNLEARNING || this == ORB_OF_REALIGNMENT;
    }

    /**
     * Gets the native Hytale item ID for this stone type.
     *
     * <p>This corresponds to the JSON filename in
     * {@code Server/Item/Items/RPG/Stones/}. For example,
     * {@code GAIAS_CALIBRATION} returns {@code "RPG_Stone_Gaias_Calibration"}.
     *
     * @return Native item ID
     */
    @Nonnull
    public String getNativeItemId() {
        return nativeItemId;
    }

    /**
     * Gets all stones of a specific rarity.
     *
     * <p>Results are cached for performance. Thread-safe via double-checked locking.
     *
     * @param rarity The rarity to filter by
     * @return Unmodifiable list of stones with that rarity (may be empty)
     */
    @Nonnull
    public static List<StoneType> getByRarity(@Nonnull GearRarity rarity) {
        if (stonesByRarity == null) {
            synchronized (StoneType.class) {
                if (stonesByRarity == null) {
                    stonesByRarity = buildStonesByRarityMap();
                }
            }
        }
        return stonesByRarity.getOrDefault(rarity, Collections.emptyList());
    }

    /**
     * Builds the stones-by-rarity lookup map.
     */
    private static Map<GearRarity, List<StoneType>> buildStonesByRarityMap() {
        Map<GearRarity, List<StoneType>> map = new EnumMap<>(GearRarity.class);

        for (StoneType stone : values()) {
            map.computeIfAbsent(stone.rarity, k -> new ArrayList<>()).add(stone);
        }

        // Make all lists unmodifiable
        Map<GearRarity, List<StoneType>> immutableMap = new EnumMap<>(GearRarity.class);
        for (var entry : map.entrySet()) {
            immutableMap.put(entry.getKey(), Collections.unmodifiableList(entry.getValue()));
        }

        return Collections.unmodifiableMap(immutableMap);
    }

    /**
     * Gets all rarities that have at least one stone.
     *
     * <p>Useful for drop systems to know which rarities are valid for stones.
     *
     * @return Unmodifiable set of rarities with stones
     */
    @Nonnull
    public static java.util.Set<GearRarity> getAvailableRarities() {
        if (stonesByRarity == null) {
            getByRarity(GearRarity.COMMON); // Trigger initialization
        }
        return stonesByRarity.keySet();
    }

    /**
     * Gets the translation key for this stone's description.
     *
     * <p>Format: {@code rpg.stone.gaias_calibration.desc}
     * Matches the key used in the native item JSON's TranslationProperties.Description.
     *
     * @return Translation key string
     */
    @Nonnull
    public String getDescriptionTranslationKey() {
        return "rpg.stone." + name().toLowerCase() + ".desc";
    }

    /**
     * Checks if this stone works on corrupted items.
     *
     * @return true if usable on corrupted items
     */
    public boolean worksOnCorrupted() {
        return worksOnCorrupted;
    }

    /**
     * Checks if this stone can be used on a given item.
     *
     * @param item The item to check
     * @return true if the stone can be used
     */
    public boolean canUseOn(@Nonnull ModifiableItem item) {
        // Check target type
        if (!targetType.isValidTarget(item)) {
            return false;
        }

        // Check corruption
        if (item.corrupted() && !worksOnCorrupted) {
            return false;
        }

        return true;
    }

    /**
     * Codec for serialization/deserialization.
     */
    public static final EnumCodec<StoneType> CODEC = new EnumCodec<>(StoneType.class);
}
