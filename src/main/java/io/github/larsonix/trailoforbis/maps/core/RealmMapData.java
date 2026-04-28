package io.github.larsonix.trailoforbis.maps.core;

import com.hypixel.hytale.codec.Codec;
import io.github.larsonix.trailoforbis.gear.item.CustomItemData;
import io.github.larsonix.trailoforbis.gear.item.CustomItemInstanceId;
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.maps.codec.RealmCodecs;
import io.github.larsonix.trailoforbis.maps.modifiers.RealmModifier;
import io.github.larsonix.trailoforbis.maps.modifiers.RealmModifierType;
import io.github.larsonix.trailoforbis.stones.ItemModifier;
import io.github.larsonix.trailoforbis.stones.ItemTargetType;
import io.github.larsonix.trailoforbis.stones.ModifiableItem;
import io.github.larsonix.trailoforbis.stones.ModifiableItemBuilder;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

/**
 * Immutable data structure representing a Realm Map item.
 *
 * <p>A Realm Map is a consumable item that opens a portal to a Realm instance.
 * Maps have:
 * <ul>
 *   <li><b>Level:</b> Determines monster levels and loot (1-1000000)</li>
 *   <li><b>Rarity:</b> Affects modifier count and value multipliers</li>
 *   <li><b>Quality:</b> Scales all modifier values via qualityMultiplier (1-101)</li>
 *   <li><b>Biome:</b> Visual theme and mob pool selection</li>
 *   <li><b>Size:</b> Arena size, monster count, and completion time</li>
 *   <li><b>Shape:</b> Arena shape (circular, rectangular, irregular)</li>
 *   <li><b>Prefixes:</b> Difficulty modifiers (MONSTER, ENVIRONMENT, SPECIAL)</li>
 *   <li><b>Suffixes:</b> Reward modifiers (REWARD category)</li>
 *   <li><b>Fortune's Compass bonus:</b> Extra IIQ bonus from Compass stone (0-20)</li>
 *   <li><b>Corrupted:</b> Whether the map has been corrupted (prevents further modification)</li>
 * </ul>
 *
 * <p>Implements {@link ModifiableItem} for stone system integration.
 *
 * @param level Map level (1-1000000)
 * @param rarity Rarity tier affecting modifier count
 * @param quality Quality value (1-101) affecting IIQ
 * @param biome The biome type for this realm
 * @param size Arena size affecting monster count and time
 * @param shape Arena shape
 * @param prefixes List of difficulty modifiers (max based on rarity, combined with suffixes)
 * @param suffixes List of reward modifiers (max based on rarity, combined with prefixes)
 * @param fortunesCompassBonus Additional IIQ bonus from Fortune's Compass (0-20)
 * @param corrupted Whether the map is corrupted
 * @param identified Whether the map has been identified (modifiers revealed)
 * @param instanceId Unique instance ID for custom item registration (may be null)
 */
public record RealmMapData(
    int level,
    @Nonnull GearRarity rarity,
    int quality,
    @Nonnull RealmBiomeType biome,
    @Nonnull RealmLayoutSize size,
    @Nonnull RealmLayoutShape shape,
    @Nonnull List<RealmModifier> prefixes,
    @Nonnull List<RealmModifier> suffixes,
    int fortunesCompassBonus,
    boolean corrupted,
    boolean identified,
    @Nullable CustomItemInstanceId instanceId
) implements ModifiableItem, CustomItemData {

    /**
     * Default base Hytale item ID for realm maps.
     * Uses custom Realm_Map item that has no built-in interactions.
     */
    public static final String DEFAULT_BASE_ITEM_ID = "Realm_Map";

    /**
     * Maximum number of modifiers for any rarity.
     */
    public static final int MAX_MODIFIERS = 6;

    /**
     * Compact constructor for validation.
     */
    public RealmMapData {
        Objects.requireNonNull(rarity, "rarity cannot be null");
        Objects.requireNonNull(biome, "biome cannot be null");
        Objects.requireNonNull(size, "size cannot be null");
        Objects.requireNonNull(shape, "shape cannot be null");
        Objects.requireNonNull(prefixes, "prefixes cannot be null");
        Objects.requireNonNull(suffixes, "suffixes cannot be null");
        // instanceId is nullable - no null check needed

        // Clamp level to valid range
        level = Math.max(1, Math.min(level, GearData.MAX_LEVEL));

        // Clamp quality to valid range (1-101)
        quality = Math.max(1, Math.min(quality, 101));
        fortunesCompassBonus = Math.max(0, Math.min(fortunesCompassBonus, 20));

        // Validate that prefixes only contain prefix types
        for (RealmModifier mod : prefixes) {
            if (!mod.isPrefix()) {
                throw new IllegalArgumentException(
                    "Prefix list contains suffix modifier: " + mod.type());
            }
        }

        // Validate that suffixes only contain suffix types
        for (RealmModifier mod : suffixes) {
            if (!mod.isSuffix()) {
                throw new IllegalArgumentException(
                    "Suffix list contains prefix modifier: " + mod.type());
            }
        }

        // Enforce max modifier count (combined total)
        int maxMods = getMaxModifiersForRarity(rarity);
        int totalMods = prefixes.size() + suffixes.size();

        if (totalMods > maxMods) {
            // Prioritize prefixes, trim suffixes first
            int toRemove = totalMods - maxMods;
            if (suffixes.size() >= toRemove) {
                suffixes = List.copyOf(suffixes.subList(0, suffixes.size() - toRemove));
                prefixes = List.copyOf(prefixes);
            } else {
                int remainingToRemove = toRemove - suffixes.size();
                suffixes = List.of();
                prefixes = List.copyOf(prefixes.subList(0, prefixes.size() - remainingToRemove));
            }
        } else {
            prefixes = List.copyOf(prefixes);
            suffixes = List.copyOf(suffixes);
        }
    }

    /**
     * Backwards-compatible constructor without Fortune's Compass bonus.
     *
     * <p>Defaults {@code fortunesCompassBonus} to {@code 0}.
     */
    public RealmMapData(
            int level,
            @Nonnull GearRarity rarity,
            int quality,
            @Nonnull RealmBiomeType biome,
            @Nonnull RealmLayoutSize size,
            @Nonnull RealmLayoutShape shape,
            @Nonnull List<RealmModifier> prefixes,
            @Nonnull List<RealmModifier> suffixes,
            boolean corrupted,
            boolean identified,
            @Nullable CustomItemInstanceId instanceId) {
        this(level, rarity, quality, biome, size, shape, prefixes, suffixes, 0, corrupted, identified, instanceId);
    }

    // ═══════════════════════════════════════════════════════════════════
    // FACTORY METHODS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Creates a new unidentified map with no modifiers.
     *
     * @param level Map level
     * @param rarity Rarity tier
     * @param biome Biome type
     * @param size Layout size
     * @return New unidentified RealmMapData
     */
    @Nonnull
    public static RealmMapData create(
            int level,
            @Nonnull GearRarity rarity,
            @Nonnull RealmBiomeType biome,
            @Nonnull RealmLayoutSize size) {
        return new RealmMapData(
            level,
            rarity,
            50, // Default quality
            biome,
            size,
            RealmLayoutShape.CIRCULAR,
            List.of(),
            List.of(),
            0,
            false,
            false,
            null  // No instance ID by default
        );
    }

    /**
     * Creates a builder for constructing RealmMapData.
     *
     * @return A new builder
     */
    @Nonnull
    public static Builder builder() {
        return new Builder();
    }

    // ═══════════════════════════════════════════════════════════════════
    // ModifiableItem IMPLEMENTATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Returns all modifiers as a combined list (prefixes first, then suffixes).
     *
     * <p>This method provides backwards compatibility for code that iterates
     * all modifiers. The order is consistent: prefixes appear first.
     *
     * @return Unmodifiable combined list of all modifiers
     */
    @Override
    @Nonnull
    public List<RealmModifier> modifiers() {
        return allModifiers();
    }

    @Override
    public int mapQuantityBonus() {
        // Quality no longer provides a separate IIQ bonus — it scales all modifier
        // values via qualityMultiplier() instead (same as gear).
        return 0;
    }

    @Override
    public int maxModifiers() {
        return getMaxModifiersForRarity(rarity);
    }

    @Override
    @Nonnull
    public ItemTargetType itemTargetType() {
        return ItemTargetType.MAP_ONLY;
    }

    @Override
    @Nonnull
    public ModifiableItemBuilder<?> toModifiableBuilder() {
        return new ModifiableBuilder(this);
    }

    // ═══════════════════════════════════════════════════════════════════
    // CustomItemData IMPLEMENTATION
    // ═══════════════════════════════════════════════════════════════════

    @Override
    @Nullable
    public CustomItemInstanceId getInstanceId() {
        return instanceId;
    }

    @Override
    @Nonnull
    public String getBaseItemId() {
        return biome.getMapItemId();
    }

    @Override
    @Nonnull
    public String getDisplayName() {
        // Format: "Biome Map" - consistent with ItemDisplayNameService
        return String.format("%s Map", biome.getDisplayName());
    }

    @Override
    @Nonnull
    public String getDescription() {
        StringBuilder desc = new StringBuilder();
        desc.append(biome.getDisplayName()).append(" - ").append(size.getDisplayName());

        if (identified && !prefixes.isEmpty()) {
            desc.append("\n\nModifiers:");
            for (RealmModifier mod : prefixes) {
                desc.append("\n  ").append(mod.displayName());
            }
        }

        if (identified && !suffixes.isEmpty()) {
            desc.append("\n\nRewards:");
            for (RealmModifier mod : suffixes) {
                desc.append("\n  ").append(mod.displayName());
            }
        }

        if (!identified) {
            desc.append("\n\n(Unidentified)");
        }

        if (corrupted) {
            desc.append("\n\n§c(Corrupted)§r");
        }

        return desc.toString();
    }

    @Override
    @Nonnull
    public CustomItemInstanceId.ItemType getItemType() {
        return CustomItemInstanceId.ItemType.MAP;
    }

    // ═══════════════════════════════════════════════════════════════════
    // MODIFIER ACCESS METHODS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Returns all modifiers as a combined list (prefixes first, then suffixes).
     *
     * <p>This provides a unified view of all modifiers for display and iteration.
     *
     * @return Unmodifiable combined list of all modifiers
     */
    @Nonnull
    public List<RealmModifier> allModifiers() {
        if (prefixes.isEmpty()) return suffixes;
        if (suffixes.isEmpty()) return prefixes;
        List<RealmModifier> all = new ArrayList<>(prefixes.size() + suffixes.size());
        all.addAll(prefixes);
        all.addAll(suffixes);
        return Collections.unmodifiableList(all);
    }

    /**
     * Gets a modifier by combined index (prefixes first, then suffixes).
     *
     * @param combinedIndex Index into the combined modifier list
     * @return The modifier at the given index
     * @throws IndexOutOfBoundsException if index is out of range
     */
    @Nonnull
    public RealmModifier getModifier(int combinedIndex) {
        if (combinedIndex < 0) {
            throw new IndexOutOfBoundsException("Index cannot be negative: " + combinedIndex);
        }
        if (combinedIndex < prefixes.size()) {
            return prefixes.get(combinedIndex);
        }
        int suffixIndex = combinedIndex - prefixes.size();
        if (suffixIndex >= suffixes.size()) {
            throw new IndexOutOfBoundsException(
                "Index " + combinedIndex + " out of range for " + modifierCount() + " modifiers");
        }
        return suffixes.get(suffixIndex);
    }

    /**
     * Gets the total modifier count (prefixes + suffixes).
     *
     * @return Total number of modifiers
     */
    public int modifierCount() {
        return prefixes.size() + suffixes.size();
    }

    /**
     * Gets the number of prefix modifiers.
     *
     * @return Number of prefix modifiers
     */
    public int prefixCount() {
        return prefixes.size();
    }

    /**
     * Gets the number of suffix modifiers.
     *
     * @return Number of suffix modifiers
     */
    public int suffixCount() {
        return suffixes.size();
    }

    // ═══════════════════════════════════════════════════════════════════
    // COMPUTED PROPERTIES
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Computes the quality multiplier for this map's modifier values.
     *
     * <p>Same formula as {@link GearData#qualityMultiplier()}:
     * {@code 0.5 + quality / 100.0}
     *
     * <ul>
     *   <li>Quality 1 → 0.51x (nearly halved)</li>
     *   <li>Quality 50 → 1.0x (neutral)</li>
     *   <li>Quality 101 → 1.51x (+51% bonus)</li>
     * </ul>
     *
     * @return Quality multiplier (0.51 to 1.51)
     */
    public double qualityMultiplier() {
        return 0.5 + quality / 100.0;
    }

    /**
     * Applies quality scaling to a raw modifier value.
     *
     * @param rawValue The raw modifier value
     * @return Quality-adjusted value, rounded to int
     */
    private int qualityAdjusted(int rawValue) {
        return (int) Math.round(rawValue * qualityMultiplier());
    }

    /**
     * Calculates the total difficulty rating from all modifiers.
     *
     * <p>Only prefix modifiers contribute to difficulty.
     * Difficulty rating uses raw values (not quality-adjusted).
     *
     * @return Sum of all prefix modifier difficulty weights
     */
    public int getDifficultyRating() {
        return prefixes.stream()
            .mapToInt(RealmModifier::getDifficultyWeight)
            .sum();
    }

    /**
     * Gets the base Item Quantity bonus from quality-adjusted modifiers.
     *
     * <p>Excludes Fortune's Compass bonus.
     *
     * @return Base IIQ percentage (quality-adjusted)
     */
    public int getBaseItemQuantity() {
        return suffixes.stream()
            .filter(m -> m.type() == RealmModifierType.ITEM_QUANTITY)
            .mapToInt(m -> qualityAdjusted(m.value()))
            .sum();
    }

    /**
     * Gets the total Item Quantity bonus from base IIQ + Fortune's Compass.
     *
     * @return Total IIQ percentage
     */
    public int getTotalItemQuantity() {
        return getBaseItemQuantity() + fortunesCompassBonus;
    }

    /**
     * Gets the total Item Rarity bonus from quality-adjusted modifiers.
     *
     * @return Total IIR percentage (quality-adjusted)
     */
    public int getTotalItemRarity() {
        return suffixes.stream()
            .filter(m -> m.type() == RealmModifierType.ITEM_RARITY)
            .mapToInt(m -> qualityAdjusted(m.value()))
            .sum();
    }

    /**
     * Gets the total Experience bonus from quality-adjusted modifiers.
     *
     * @return Total XP bonus percentage (quality-adjusted)
     */
    public int getTotalExperienceBonus() {
        return suffixes.stream()
            .filter(m -> m.type() == RealmModifierType.EXPERIENCE_BONUS)
            .mapToInt(m -> qualityAdjusted(m.value()))
            .sum();
    }

    /**
     * Gets the elite spawn chance bonus from quality-adjusted modifiers.
     *
     * @return Elite chance bonus percentage (quality-adjusted)
     */
    public int getEliteChanceBonus() {
        return suffixes.stream()
            .filter(m -> m.type() == RealmModifierType.ELITE_CHANCE)
            .mapToInt(m -> qualityAdjusted(m.value()))
            .sum();
    }

    /**
     * Gets the total monster damage multiplier (quality-adjusted).
     *
     * @return Damage multiplier (1.0 = 100%, 1.5 = 150%)
     */
    public float getMonsterDamageMultiplier() {
        int bonus = prefixes.stream()
            .filter(m -> m.type() == RealmModifierType.MONSTER_DAMAGE)
            .mapToInt(m -> qualityAdjusted(m.value()))
            .sum();
        return 1.0f + (bonus / 100.0f);
    }

    /**
     * Gets the total monster health multiplier (quality-adjusted).
     *
     * @return Health multiplier (1.0 = 100%, 1.5 = 150%)
     */
    public float getMonsterHealthMultiplier() {
        int bonus = prefixes.stream()
            .filter(m -> m.type() == RealmModifierType.MONSTER_HEALTH)
            .mapToInt(m -> qualityAdjusted(m.value()))
            .sum();
        return 1.0f + (bonus / 100.0f);
    }

    /**
     * Gets the extra monster count percentage (quality-adjusted).
     *
     * @return Extra monster percentage
     */
    public int getExtraMonsterPercent() {
        return suffixes.stream()
            .filter(m -> m.type() == RealmModifierType.EXTRA_MONSTERS)
            .mapToInt(m -> qualityAdjusted(m.value()))
            .sum();
    }

    /**
     * Calculates the expected monster count for this map.
     *
     * @return Expected monster count
     */
    public int calculateMonsterCount() {
        int base = size.calculateMonsterCount(level);
        float extraMultiplier = 1.0f + (getExtraMonsterPercent() / 100.0f);
        return Math.round(base * extraMultiplier);
    }

    /**
     * Gets the template name for instance spawning.
     *
     * @return Full template name (e.g., "realm_forest_medium")
     */
    @Nonnull
    public String getTemplateName() {
        return biome.getTemplateName(size);
    }

    /**
     * Checks if this map has a specific modifier type.
     *
     * @param type The modifier type to check for
     * @return true if the map has this modifier
     */
    public boolean hasModifier(@Nonnull RealmModifierType type) {
        if (type.isPrefix()) {
            return prefixes.stream().anyMatch(m -> m.type() == type);
        } else {
            return suffixes.stream().anyMatch(m -> m.type() == type);
        }
    }

    /**
     * Gets the quality-adjusted value of a specific modifier type, or 0 if not present.
     *
     * @param type The modifier type
     * @return The quality-adjusted modifier value, or 0
     */
    public int getModifierValue(@Nonnull RealmModifierType type) {
        List<RealmModifier> searchList = type.isPrefix() ? prefixes : suffixes;
        return searchList.stream()
            .filter(m -> m.type() == type)
            .mapToInt(m -> qualityAdjusted(m.value()))
            .findFirst()
            .orElse(0);
    }

    /**
     * Checks if this map has any reward modifiers (suffixes).
     *
     * @return true if at least one suffix is present
     */
    public boolean hasRewardModifiers() {
        return !suffixes.isEmpty();
    }

    /**
     * Checks if this map has any difficulty modifiers (prefixes).
     *
     * @return true if at least one prefix is present
     */
    public boolean hasDifficultyModifiers() {
        return !prefixes.isEmpty();
    }

    /**
     * Gets the timeout for this realm in seconds.
     *
     * <p>Accounts for any time reduction modifiers.
     *
     * @return Timeout in seconds
     */
    public int getTimeoutSeconds() {
        int base = size.getBaseTimeoutSeconds();
        int reduction = getModifierValue(RealmModifierType.REDUCED_TIME);
        return Math.max(60, base - (base * reduction / 100));
    }

    // =========================================================================
    // DYNAMIC ARENA SCALING — Computed radius and timer
    // =========================================================================

    /**
     * Computes the dynamic arena radius based on base mob count.
     *
     * <p>Only uses level-scaled mob count (NO modifier bonuses like EXTRA_MONSTERS).
     * This ensures modifier bonus mobs increase DENSITY intentionally, not arena size.
     *
     * <p>Formula: {@code radius = sqrt(baseMobCount * targetBlocksPerMob / PI)}
     * clamped to the size's min/max radius range.
     *
     * @param config Arena scaling configuration
     * @return Computed arena radius in blocks
     */
    public int computeArenaRadius(@Nonnull io.github.larsonix.trailoforbis.maps.config.RealmsConfig.ArenaScalingConfig config) {
        // Cap input mobs at 500 (same MAX_MONSTERS as the spawner).
        int baseMobCount = Math.min(500, size.calculateMonsterCount(level));

        // Sqrt-blend curve: 15 mobs → R=35, 500 mobs → R=150.
        // Formula: radius = 35 + 115 * sqrt((mobs - 15) / 485)
        // Rises: 50 mobs ≈ 66, 100 mobs ≈ 83, 200 mobs ≈ 106, 500 mobs = 150.
        double t = Math.max(0.0, (baseMobCount - 15.0) / 485.0);
        double rawRadius = 35.0 + 115.0 * Math.sqrt(t);
        return Math.max(35, (int) Math.round(rawRadius));
    }

    /**
     * Convenience overload with default config (for tests and backwards compat).
     */
    public int computeArenaRadius() {
        return computeArenaRadius(new io.github.larsonix.trailoforbis.maps.config.RealmsConfig.ArenaScalingConfig());
    }

    /**
     * Computes the dynamic timeout based on final mob count with degressive scaling.
     *
     * <p>Uses final mob count (including modifier bonuses) — more mobs = more time,
     * but with diminishing returns. High-level players with hundreds of mobs don't
     * get proportionally more time; time pressure increases.
     *
     * <p>Formula: {@code base + (finalMobCount * perMob) / (1 + finalMobCount / softCap)}
     * then applies REDUCED_TIME modifier, clamped to minimum.
     *
     * @param config Arena scaling configuration
     * @return Computed timeout in seconds
     */
    public int computeTimeoutSeconds(@Nonnull io.github.larsonix.trailoforbis.maps.config.RealmsConfig.ArenaScalingConfig config) {
        int finalMobCount = calculateMonsterCount(); // Includes modifier bonuses
        int base = config.getTimerBaseSeconds();
        int perMob = config.getTimerSecondsPerMob();
        int softCap = config.getTimerSoftCap();

        double rawTimeout = base + (finalMobCount * (double) perMob) / (1.0 + (double) finalMobCount / softCap);

        // Apply REDUCED_TIME modifier on top
        int reduction = getModifierValue(RealmModifierType.REDUCED_TIME);
        double afterReduction = rawTimeout * (1.0 - reduction / 100.0);

        return Math.max(config.getTimerMinimumSeconds(), (int) Math.round(afterReduction));
    }

    /**
     * Convenience overload with default config (for tests and backwards compat).
     */
    public int computeTimeoutSeconds() {
        return computeTimeoutSeconds(new io.github.larsonix.trailoforbis.maps.config.RealmsConfig.ArenaScalingConfig());
    }

    /**
     * Checks if the map is identified (modifiers visible).
     *
     * @return true if identified
     */
    @Override
    public boolean isIdentified() {
        return identified;
    }

    /**
     * Checks if the map has perfect quality (101).
     *
     * @return true if quality is 101
     */
    public boolean isPerfectQuality() {
        return quality >= 101;
    }

    // ═══════════════════════════════════════════════════════════════════
    // COPY METHODS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Creates a copy with the identified flag set to true.
     *
     * @return Identified copy
     */
    @Nonnull
    public RealmMapData identify() {
        if (identified) {
            return this;
        }
        return new RealmMapData(level, rarity, quality, biome, size, shape, prefixes, suffixes,
            fortunesCompassBonus, corrupted, true, instanceId);
    }

    /**
     * Creates a copy with the corrupted flag set to true.
     *
     * @return Corrupted copy
     */
    @Nonnull
    public RealmMapData corrupt() {
        if (corrupted) {
            return this;
        }
        return new RealmMapData(level, rarity, quality, biome, size, shape, prefixes, suffixes,
            fortunesCompassBonus, true, identified, instanceId);
    }

    /**
     * Creates a copy with new modifiers, auto-splitting by type.
     *
     * @param newModifiers The new modifiers (will be split into prefixes/suffixes)
     * @return Copy with updated modifiers
     */
    @Nonnull
    public RealmMapData withModifiers(@Nonnull List<RealmModifier> newModifiers) {
        List<RealmModifier> newPrefixes = new ArrayList<>();
        List<RealmModifier> newSuffixes = new ArrayList<>();
        for (RealmModifier mod : newModifiers) {
            if (mod.isPrefix()) {
                newPrefixes.add(mod);
            } else {
                newSuffixes.add(mod);
            }
        }
        return new RealmMapData(level, rarity, quality, biome, size, shape, newPrefixes, newSuffixes,
            fortunesCompassBonus, corrupted, identified, instanceId);
    }

    /**
     * Creates a copy with new prefixes.
     *
     * @param newPrefixes The new prefix modifiers
     * @return Copy with updated prefixes
     */
    @Nonnull
    public RealmMapData withPrefixes(@Nonnull List<RealmModifier> newPrefixes) {
        return new RealmMapData(level, rarity, quality, biome, size, shape, newPrefixes, suffixes,
            fortunesCompassBonus, corrupted, identified, instanceId);
    }

    /**
     * Creates a copy with new suffixes.
     *
     * @param newSuffixes The new suffix modifiers
     * @return Copy with updated suffixes
     */
    @Nonnull
    public RealmMapData withSuffixes(@Nonnull List<RealmModifier> newSuffixes) {
        return new RealmMapData(level, rarity, quality, biome, size, shape, prefixes, newSuffixes,
            fortunesCompassBonus, corrupted, identified, instanceId);
    }

    /**
     * Creates a copy with a new rarity.
     *
     * @param newRarity The new rarity
     * @return Copy with updated rarity
     */
    @Nonnull
    public RealmMapData withRarity(@Nonnull GearRarity newRarity) {
        return new RealmMapData(level, newRarity, quality, biome, size, shape, prefixes, suffixes,
            fortunesCompassBonus, corrupted, identified, instanceId);
    }

    /**
     * Creates a copy with new quality.
     *
     * @param newQuality The new quality
     * @return Copy with updated quality
     */
    @Nonnull
    public RealmMapData withQuality(int newQuality) {
        return new RealmMapData(level, rarity, newQuality, biome, size, shape, prefixes, suffixes,
            fortunesCompassBonus, corrupted, identified, instanceId);
    }

    /**
     * Creates a copy with a new level.
     *
     * @param newLevel The new level
     * @return Copy with updated level
     */
    @Nonnull
    public RealmMapData withLevel(int newLevel) {
        return new RealmMapData(newLevel, rarity, quality, biome, size, shape, prefixes, suffixes,
            fortunesCompassBonus, corrupted, identified, instanceId);
    }

    /**
     * Creates a copy with a new biome.
     *
     * @param newBiome The new biome type
     * @return Copy with updated biome
     */
    @Nonnull
    public RealmMapData withBiome(@Nonnull RealmBiomeType newBiome) {
        return new RealmMapData(level, rarity, quality, newBiome, size, shape, prefixes, suffixes,
            fortunesCompassBonus, corrupted, identified, instanceId);
    }

    /**
     * Creates a copy with a new instance ID.
     *
     * @param newInstanceId The new instance ID (may be null)
     * @return Copy with updated instance ID
     */
    @Nonnull
    public RealmMapData withInstanceId(@Nullable CustomItemInstanceId newInstanceId) {
        return new RealmMapData(level, rarity, quality, biome, size, shape, prefixes, suffixes,
            fortunesCompassBonus, corrupted, identified, newInstanceId);
    }

    /**
     * Creates a copy with a new Fortune's Compass IIQ bonus.
     *
     * @param newFortunesCompassBonus New Compass IIQ bonus (clamped to 0-20)
     * @return Copy with updated Fortune's Compass bonus
     */
    @Nonnull
    public RealmMapData withFortunesCompassBonus(int newFortunesCompassBonus) {
        return new RealmMapData(level, rarity, quality, biome, size, shape, prefixes, suffixes,
            newFortunesCompassBonus, corrupted, identified, instanceId);
    }

    // ═══════════════════════════════════════════════════════════════════
    // UTILITY METHODS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets the maximum modifiers for a given rarity.
     *
     * @param rarity The rarity tier
     * @return Maximum modifier count (1-6)
     */
    public static int getMaxModifiersForRarity(@Nonnull GearRarity rarity) {
        return switch (rarity) {
            case COMMON -> 1;
            case UNCOMMON -> 2;
            case RARE -> 3;
            case EPIC -> 4;
            case LEGENDARY -> 5;
            case MYTHIC, UNIQUE -> 6;
        };
    }

    // ═══════════════════════════════════════════════════════════════════
    // BUILDER
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Builder for constructing RealmMapData instances.
     */
    public static final class Builder {
        private int level = 1;
        private GearRarity rarity = GearRarity.COMMON;
        private int quality = 50;
        private RealmBiomeType biome = RealmBiomeType.FOREST;
        private RealmLayoutSize size = RealmLayoutSize.MEDIUM;
        private RealmLayoutShape shape = RealmLayoutShape.CIRCULAR;
        private List<RealmModifier> prefixes = new ArrayList<>();
        private List<RealmModifier> suffixes = new ArrayList<>();
        private int fortunesCompassBonus = 0;
        private boolean corrupted = false;
        private boolean identified = false;
        private CustomItemInstanceId instanceId = null;

        public Builder level(int level) {
            this.level = level;
            return this;
        }

        public Builder rarity(GearRarity rarity) {
            this.rarity = rarity;
            return this;
        }

        public Builder quality(int quality) {
            this.quality = quality;
            return this;
        }

        public Builder biome(RealmBiomeType biome) {
            this.biome = biome;
            return this;
        }

        public Builder size(RealmLayoutSize size) {
            this.size = size;
            return this;
        }

        public Builder shape(RealmLayoutShape shape) {
            this.shape = shape;
            return this;
        }

        /**
         * Sets the prefix modifiers.
         *
         * @param prefixes List of prefix modifiers
         * @return This builder
         */
        public Builder prefixes(List<RealmModifier> prefixes) {
            this.prefixes = new ArrayList<>(prefixes);
            return this;
        }

        /**
         * Sets the suffix modifiers.
         *
         * @param suffixes List of suffix modifiers
         * @return This builder
         */
        public Builder suffixes(List<RealmModifier> suffixes) {
            this.suffixes = new ArrayList<>(suffixes);
            return this;
        }

        /**
         * Adds a prefix modifier.
         *
         * @param modifier The prefix modifier to add
         * @return This builder
         */
        public Builder addPrefix(RealmModifier modifier) {
            this.prefixes.add(modifier);
            return this;
        }

        /**
         * Adds a suffix modifier.
         *
         * @param modifier The suffix modifier to add
         * @return This builder
         */
        public Builder addSuffix(RealmModifier modifier) {
            this.suffixes.add(modifier);
            return this;
        }

        /**
         * Adds a modifier, automatically routing to prefixes or suffixes based on type.
         *
         * @param modifier The modifier to add
         * @return This builder
         */
        public Builder addModifier(RealmModifier modifier) {
            if (modifier.isPrefix()) {
                this.prefixes.add(modifier);
            } else {
                this.suffixes.add(modifier);
            }
            return this;
        }

        public Builder corrupted(boolean corrupted) {
            this.corrupted = corrupted;
            return this;
        }

        public Builder identified(boolean identified) {
            this.identified = identified;
            return this;
        }

        public Builder instanceId(@Nullable CustomItemInstanceId instanceId) {
            this.instanceId = instanceId;
            return this;
        }

        public Builder fortunesCompassBonus(int fortunesCompassBonus) {
            this.fortunesCompassBonus = fortunesCompassBonus;
            return this;
        }

        public RealmMapData build() {
            return new RealmMapData(level, rarity, quality, biome, size, shape, prefixes, suffixes,
                fortunesCompassBonus, corrupted, identified, instanceId);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // MODIFIABLE BUILDER (for Stone system)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Builder implementing ModifiableItemBuilder for stone operations.
     */
    private static final class ModifiableBuilder implements ModifiableItemBuilder<ModifiableBuilder> {
        private int level;
        private GearRarity rarity;
        private int quality;
        private RealmBiomeType biome;
        private RealmLayoutSize size;
        private RealmLayoutShape shape;
        private List<RealmModifier> prefixes;
        private List<RealmModifier> suffixes;
        private int fortunesCompassBonus;
        private boolean corrupted;
        private boolean identified;
        private CustomItemInstanceId instanceId;

        ModifiableBuilder(RealmMapData source) {
            this.level = source.level;
            this.rarity = source.rarity;
            this.quality = source.quality;
            this.biome = source.biome;
            this.size = source.size;
            this.shape = source.shape;
            this.prefixes = new ArrayList<>(source.prefixes);
            this.suffixes = new ArrayList<>(source.suffixes);
            this.fortunesCompassBonus = source.fortunesCompassBonus;
            this.corrupted = source.corrupted;
            this.identified = source.identified;
            this.instanceId = source.instanceId;
        }

        @Override
        @Nonnull
        public ModifiableBuilder level(int level) {
            this.level = level;
            return this;
        }

        @Override
        @Nonnull
        public ModifiableBuilder rarity(@Nonnull GearRarity rarity) {
            this.rarity = rarity;
            return this;
        }

        @Override
        @Nonnull
        public ModifiableBuilder quality(int quality) {
            this.quality = quality;
            return this;
        }

        @Override
        @Nonnull
        public ModifiableBuilder corrupted(boolean corrupted) {
            this.corrupted = corrupted;
            return this;
        }

        @Override
        @Nonnull
        @SuppressWarnings("unchecked")
        public ModifiableBuilder modifiers(@Nonnull List<? extends ItemModifier> modifiers) {
            this.prefixes.clear();
            this.suffixes.clear();
            for (ItemModifier mod : modifiers) {
                if (mod instanceof RealmModifier rm) {
                    if (rm.isPrefix()) {
                        this.prefixes.add(rm);
                    } else {
                        this.suffixes.add(rm);
                    }
                }
            }
            return this;
        }

        @Override
        @Nonnull
        public ModifiableBuilder addModifier(@Nonnull ItemModifier modifier) {
            if (modifier instanceof RealmModifier rm) {
                if (rm.isPrefix()) {
                    this.prefixes.add(rm);
                } else {
                    this.suffixes.add(rm);
                }
            }
            return this;
        }

        @Override
        @Nonnull
        public ModifiableBuilder removeModifier(int index) {
            // Combined index: prefixes first, then suffixes
            if (index < 0) {
                return this;
            }
            if (index < prefixes.size()) {
                prefixes.remove(index);
            } else {
                int suffixIndex = index - prefixes.size();
                if (suffixIndex >= 0 && suffixIndex < suffixes.size()) {
                    suffixes.remove(suffixIndex);
                }
            }
            return this;
        }

        @Override
        @Nonnull
        public ModifiableBuilder clearModifiers() {
            this.prefixes.clear();
            this.suffixes.clear();
            return this;
        }

        @Override
        @Nonnull
        public ModifiableBuilder mapQuantityBonus(int bonus) {
            // Quality maps to bonus: bonus 0 = quality 1, bonus 10 = quality 100, bonus 20 = quality 101
            if (bonus >= 20) {
                this.quality = 101;
            } else {
                this.quality = bonus * 10;
            }
            return this;
        }

        @Override
        @Nonnull
        public ModifiableItem build() {
            return new RealmMapData(level, rarity, quality, biome, size, shape, prefixes, suffixes,
                fortunesCompassBonus, corrupted, identified, instanceId);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // CODEC
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Codec for serialization/deserialization.
     *
     * <p>Supports backwards compatibility: if reading data with old "modifiers"
     * key, it will auto-split into prefixes/suffixes. Always writes new format.
     */
    public static final Codec<RealmMapData> CODEC = new Codec<>() {
        private static final String KEY_LEVEL = "level";
        private static final String KEY_RARITY = "rarity";
        private static final String KEY_QUALITY = "quality";
        private static final String KEY_BIOME = "biome";
        private static final String KEY_SIZE = "size";
        private static final String KEY_SHAPE = "shape";
        private static final String KEY_PREFIXES = "prefixes";
        private static final String KEY_SUFFIXES = "suffixes";
        private static final String KEY_MODIFIERS = "modifiers"; // Legacy key
        private static final String KEY_FORTUNES_COMPASS_BONUS = "fortunesCompassBonus";
        private static final String KEY_CORRUPTED = "corrupted";
        private static final String KEY_IDENTIFIED = "identified";
        private static final String KEY_INSTANCE_ID = "instanceId";

        @Override
        public RealmMapData decode(@Nonnull org.bson.BsonValue encoded, @Nonnull com.hypixel.hytale.codec.ExtraInfo extraInfo) {
            if (!(encoded instanceof org.bson.BsonDocument doc)) {
                throw new IllegalArgumentException("Expected BsonDocument");
            }

            int level = doc.getInt32(KEY_LEVEL).getValue();
            GearRarity rarity = GearRarity.valueOf(doc.getString(KEY_RARITY).getValue());
            int quality = doc.getInt32(KEY_QUALITY).getValue();
            RealmBiomeType biome = RealmBiomeType.valueOf(doc.getString(KEY_BIOME).getValue());
            RealmLayoutSize size = RealmLayoutSize.valueOf(doc.getString(KEY_SIZE).getValue());
            RealmLayoutShape shape = RealmLayoutShape.valueOf(doc.getString(KEY_SHAPE).getValue());

            List<RealmModifier> prefixes;
            List<RealmModifier> suffixes;

            // New format: separate prefixes and suffixes
            if (doc.containsKey(KEY_PREFIXES)) {
                prefixes = RealmCodecs.MODIFIER_LIST_CODEC.decode(doc.get(KEY_PREFIXES), extraInfo);
                suffixes = doc.containsKey(KEY_SUFFIXES)
                    ? RealmCodecs.MODIFIER_LIST_CODEC.decode(doc.get(KEY_SUFFIXES), extraInfo)
                    : List.of();
            }
            // Legacy format: single modifiers array
            else if (doc.containsKey(KEY_MODIFIERS)) {
                List<RealmModifier> allMods = RealmCodecs.MODIFIER_LIST_CODEC.decode(
                    doc.get(KEY_MODIFIERS), extraInfo);

                // Split by type
                List<RealmModifier> splitPrefixes = new ArrayList<>();
                List<RealmModifier> splitSuffixes = new ArrayList<>();
                for (RealmModifier mod : allMods) {
                    if (mod.isPrefix()) {
                        splitPrefixes.add(mod);
                    } else {
                        splitSuffixes.add(mod);
                    }
                }
                prefixes = splitPrefixes;
                suffixes = splitSuffixes;
            } else {
                prefixes = List.of();
                suffixes = List.of();
            }

            boolean corrupted = doc.containsKey(KEY_CORRUPTED) && doc.getBoolean(KEY_CORRUPTED).getValue();
            boolean identified = doc.containsKey(KEY_IDENTIFIED) && doc.getBoolean(KEY_IDENTIFIED).getValue();
            int fortunesCompassBonus = doc.containsKey(KEY_FORTUNES_COMPASS_BONUS)
                ? doc.getInt32(KEY_FORTUNES_COMPASS_BONUS).getValue()
                : 0;

            // Parse instanceId if present
            CustomItemInstanceId instanceId = null;
            if (doc.containsKey(KEY_INSTANCE_ID) && !doc.get(KEY_INSTANCE_ID).isNull()) {
                String compactId = doc.getString(KEY_INSTANCE_ID).getValue();
                instanceId = CustomItemInstanceId.tryFromCompactString(compactId);
            }

            return new RealmMapData(level, rarity, quality, biome, size, shape, prefixes, suffixes,
                fortunesCompassBonus, corrupted, identified, instanceId);
        }

        @Override
        @Nonnull
        public org.bson.BsonValue encode(@Nonnull RealmMapData data, @Nonnull com.hypixel.hytale.codec.ExtraInfo extraInfo) {
            org.bson.BsonDocument doc = new org.bson.BsonDocument();
            doc.put(KEY_LEVEL, new org.bson.BsonInt32(data.level()));
            doc.put(KEY_RARITY, new org.bson.BsonString(data.rarity().name()));
            doc.put(KEY_QUALITY, new org.bson.BsonInt32(data.quality()));
            doc.put(KEY_BIOME, new org.bson.BsonString(data.biome().name()));
            doc.put(KEY_SIZE, new org.bson.BsonString(data.size().name()));
            doc.put(KEY_SHAPE, new org.bson.BsonString(data.shape().name()));
            // Always write new format
            doc.put(KEY_PREFIXES, RealmCodecs.MODIFIER_LIST_CODEC.encode(data.prefixes(), extraInfo));
            doc.put(KEY_SUFFIXES, RealmCodecs.MODIFIER_LIST_CODEC.encode(data.suffixes(), extraInfo));
            doc.put(KEY_FORTUNES_COMPASS_BONUS, new org.bson.BsonInt32(data.fortunesCompassBonus()));
            doc.put(KEY_CORRUPTED, new org.bson.BsonBoolean(data.corrupted()));
            doc.put(KEY_IDENTIFIED, new org.bson.BsonBoolean(data.identified()));
            // Write instanceId if present
            if (data.instanceId() != null) {
                doc.put(KEY_INSTANCE_ID, new org.bson.BsonString(data.instanceId().toCompactString()));
            }
            return doc;
        }

        @Override
        @Nonnull
        public com.hypixel.hytale.codec.schema.config.Schema toSchema(@Nonnull com.hypixel.hytale.codec.schema.SchemaContext context) {
            return new com.hypixel.hytale.codec.schema.config.ObjectSchema();
        }
    };
}
