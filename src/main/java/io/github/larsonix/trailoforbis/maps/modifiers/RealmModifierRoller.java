package io.github.larsonix.trailoforbis.maps.modifiers;

import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.maps.config.RealmModifierConfig;
import io.github.larsonix.trailoforbis.maps.config.RealmModifierConfig.ModifierSettings;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates realm modifiers for map items.
 *
 * <p>Uses {@link RealmModifierPool} for weighted selection and
 * {@link RealmModifierConfig} for value ranges and modifier counts.
 *
 * <p>Features:
 * <ul>
 *   <li>Rarity-based modifier count</li>
 *   <li>Weighted random selection without duplicates</li>
 *   <li>Configurable value ranges per modifier</li>
 *   <li>Incompatibility rule enforcement</li>
 *   <li>Difficulty calculation</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * RealmModifierRoller roller = new RealmModifierRoller(pool);
 *
 * // Generate modifiers for an epic map at level 50
 * List<RealmModifier> mods = roller.rollModifiers(GearRarity.EPIC, random, 50);
 *
 * // Calculate total difficulty
 * int difficulty = roller.calculateDifficulty(mods);
 * }</pre>
 *
 * @see RealmModifierPool
 * @see RealmModifier
 */
public class RealmModifierRoller {

    private final RealmModifierPool pool;
    private final RealmModifierConfig config;

    // ═══════════════════════════════════════════════════════════════════
    // RESULT TYPE
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Result of rolling modifiers, split into prefixes and suffixes.
     *
     * <p>Prefixes are difficulty modifiers (MONSTER, ENVIRONMENT, SPECIAL categories).
     * Suffixes are reward modifiers (REWARD category).
     *
     * @param prefixes Difficulty modifiers (immutable)
     * @param suffixes Reward modifiers (immutable)
     */
    public record RollResult(
        @Nonnull List<RealmModifier> prefixes,
        @Nonnull List<RealmModifier> suffixes
    ) {
        /**
         * Creates an immutable RollResult.
         */
        public RollResult {
            prefixes = List.copyOf(prefixes);
            suffixes = List.copyOf(suffixes);
        }

        /**
         * Creates an empty RollResult with no modifiers.
         *
         * @return Empty RollResult
         */
        @Nonnull
        public static RollResult empty() {
            return new RollResult(List.of(), List.of());
        }

        /**
         * Returns all modifiers as a combined list (prefixes first, then suffixes).
         *
         * @return Unmodifiable combined list
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
         * Gets the total modifier count.
         *
         * @return Combined count of prefixes and suffixes
         */
        public int totalCount() {
            return prefixes.size() + suffixes.size();
        }

        /**
         * Checks if this result has no modifiers.
         *
         * @return true if both prefixes and suffixes are empty
         */
        public boolean isEmpty() {
            return prefixes.isEmpty() && suffixes.isEmpty();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Creates a new modifier roller.
     *
     * @param pool The modifier pool for weighted selection
     */
    public RealmModifierRoller(@Nonnull RealmModifierPool pool) {
        this.pool = Objects.requireNonNull(pool, "Pool cannot be null");
        this.config = pool.getConfig();
    }

    /**
     * Creates a new modifier roller from configuration.
     *
     * @param config The modifier configuration
     */
    public RealmModifierRoller(@Nonnull RealmModifierConfig config) {
        this(new RealmModifierPool(config));
    }

    // ═══════════════════════════════════════════════════════════════════
    // ROLLING METHODS
    // ═══════════════════════════════════════════════════════════════════

    // ═══════════════════════════════════════════════════════════════════
    // SPLIT ROLLING METHODS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Rolls modifiers split into prefixes and suffixes, with counts
     * determined independently by rarity (like gear).
     *
     * @param rarity The map rarity (determines prefix/suffix count)
     * @param random Random source
     * @param level The map level (for scaling modifier values)
     * @return RollResult with separate prefix and suffix lists
     */
    @Nonnull
    public RollResult rollModifiersSplit(
            @Nonnull GearRarity rarity,
            @Nonnull Random random,
            int level) {

        RealmModifierConfig.ModifierCounts counts = config.rollModifierCounts(rarity, random);

        List<RealmModifier> prefixes = rollFromCategory(
            RealmModifierType.Category.PREFIX, counts.prefixCount(), random, level);
        List<RealmModifier> suffixes = rollFromCategory(
            RealmModifierType.Category.SUFFIX, counts.suffixCount(), random, level);

        return new RollResult(prefixes, suffixes);
    }

    /**
     * Rolls modifiers from a specific category pool.
     *
     * @param category The category to roll from
     * @param count Number of modifiers to roll
     * @param random Random source
     * @param level The map level (for scaling modifier values)
     * @return List of rolled modifiers from that category
     */
    @Nonnull
    private List<RealmModifier> rollFromCategory(
            @Nonnull RealmModifierType.Category category,
            int count,
            @Nonnull Random random,
            int level) {

        if (count <= 0) {
            return List.of();
        }

        List<RealmModifier> result = new ArrayList<>();
        Set<RealmModifierType> usedTypes = new HashSet<>();

        for (int i = 0; i < count; i++) {
            RealmModifierType type = pool.selectFromCategory(random, category, usedTypes);
            if (type == null) {
                break;
            }

            result.add(rollModifier(type, random, level));
            usedTypes.add(type);
        }

        return result;
    }

    /**
     * Fills remaining modifier slots based on rarity config and existing modifiers.
     *
     * <p>Used by "fill modifiers" stones to add modifiers up to the
     * configured max prefix/suffix counts for the item's rarity.
     *
     * @param rarity The item rarity (determines max prefix/suffix counts)
     * @param existingPrefixes Current prefix types to exclude
     * @param existingSuffixes Current suffix types to exclude
     * @param random Random source
     * @param level The map level
     * @return RollResult with only the newly rolled modifiers
     */
    @Nonnull
    public RollResult fillModifierSlots(
            @Nonnull GearRarity rarity,
            @Nonnull List<RealmModifier> existingPrefixes,
            @Nonnull List<RealmModifier> existingSuffixes,
            @Nonnull Random random,
            int level) {

        int maxTotal = config.getMaxModifiers(rarity);
        int maxPrefixes = config.getPrefixCountRange(rarity).max();
        int maxSuffixes = config.getSuffixCountRange(rarity).max();

        int currentTotal = existingPrefixes.size() + existingSuffixes.size();
        int totalSlotsOpen = Math.max(0, maxTotal - currentTotal);
        int prefixSlotsOpen = Math.max(0, maxPrefixes - existingPrefixes.size());
        int suffixSlotsOpen = Math.max(0, maxSuffixes - existingSuffixes.size());

        // Exclude existing types
        Set<RealmModifierType> usedPrefixTypes = new HashSet<>();
        existingPrefixes.forEach(m -> usedPrefixTypes.add(m.type()));

        Set<RealmModifierType> usedSuffixTypes = new HashSet<>();
        existingSuffixes.forEach(m -> usedSuffixTypes.add(m.type()));

        int added = 0;

        List<RealmModifier> newPrefixes = new ArrayList<>();
        for (int i = 0; i < prefixSlotsOpen && added < totalSlotsOpen; i++) {
            RealmModifierType type = pool.selectFromCategory(random,
                RealmModifierType.Category.PREFIX, usedPrefixTypes);
            if (type == null) break;
            newPrefixes.add(rollModifier(type, random, level));
            usedPrefixTypes.add(type);
            added++;
        }

        List<RealmModifier> newSuffixes = new ArrayList<>();
        for (int i = 0; i < suffixSlotsOpen && added < totalSlotsOpen; i++) {
            RealmModifierType type = pool.selectFromCategory(random,
                RealmModifierType.Category.SUFFIX, usedSuffixTypes);
            if (type == null) break;
            newSuffixes.add(rollModifier(type, random, level));
            usedSuffixTypes.add(type);
            added++;
        }

        return new RollResult(newPrefixes, newSuffixes);
    }

    /**
     * Rolls a single modifier with a level-scaled random value.
     *
     * @param type The modifier type
     * @param random Random source
     * @param level The map level (for scaling modifier values)
     * @return A new modifier with rolled value
     */
    @Nonnull
    public RealmModifier rollModifier(
            @Nonnull RealmModifierType type,
            @Nonnull Random random,
            int level) {

        ModifierSettings settings = config.getModifierSettings(type);
        int value = settings.rollValue(random, level);
        return new RealmModifier(type, value, false);
    }

    /**
     * Re-rolls all unlocked modifier values on an existing set.
     *
     * <p>This is used by Divine Orb-like stones.
     *
     * @param modifiers Existing modifiers
     * @param random Random source
     * @param level The map level (for scaling modifier values)
     * @return New list with rerolled values (types unchanged)
     * @deprecated Use {@link #rerollValuesSplit(List, List, Random, int)} for explicit prefix/suffix handling
     */
    @Deprecated
    @Nonnull
    public List<RealmModifier> rerollValues(
            @Nonnull List<RealmModifier> modifiers,
            @Nonnull Random random,
            int level) {

        return modifiers.stream()
            .map(mod -> {
                if (mod.locked()) {
                    return mod;
                }
                return rollModifier(mod.type(), random, level);
            })
            .toList();
    }

    /**
     * Re-rolls all unlocked modifier values, preserving prefix/suffix split.
     *
     * @param prefixes Existing prefix modifiers
     * @param suffixes Existing suffix modifiers
     * @param random Random source
     * @param level The map level (for scaling modifier values)
     * @return RollResult with rerolled values (types unchanged)
     */
    @Nonnull
    public RollResult rerollValuesSplit(
            @Nonnull List<RealmModifier> prefixes,
            @Nonnull List<RealmModifier> suffixes,
            @Nonnull Random random,
            int level) {

        List<RealmModifier> newPrefixes = prefixes.stream()
            .map(mod -> mod.locked() ? mod : rollModifier(mod.type(), random, level))
            .toList();

        List<RealmModifier> newSuffixes = suffixes.stream()
            .map(mod -> mod.locked() ? mod : rollModifier(mod.type(), random, level))
            .toList();

        return new RollResult(newPrefixes, newSuffixes);
    }

    /**
     * Re-rolls unlocked modifier types (keeps same count).
     *
     * <p>This is used by Chaos Orb-like stones.
     *
     * @param modifiers Existing modifiers
     * @param random Random source
     * @param level The map level (for scaling modifier values)
     * @return New list with rerolled types and values
     * @deprecated Use {@link #rerollTypesSplit(List, List, Random, int)} for explicit prefix/suffix handling
     */
    @Deprecated
    @Nonnull
    public List<RealmModifier> rerollTypes(
            @Nonnull List<RealmModifier> modifiers,
            @Nonnull Random random,
            int level) {

        // Separate locked and unlocked
        List<RealmModifier> locked = modifiers.stream()
            .filter(RealmModifier::locked)
            .toList();

        int unlockedCount = modifiers.size() - locked.size();

        if (unlockedCount <= 0) {
            return modifiers;
        }

        // Build exclusions from locked modifiers
        Set<RealmModifierType> exclusions = locked.stream()
            .map(RealmModifier::type)
            .collect(Collectors.toSet());

        // Roll new modifiers
        List<RealmModifier> newModifiers = new ArrayList<>(locked);

        for (int i = 0; i < unlockedCount; i++) {
            RealmModifierType type = pool.selectRandom(random, exclusions, exclusions);
            if (type == null) {
                break;
            }
            newModifiers.add(rollModifier(type, random, level));
            exclusions.add(type);
        }

        return Collections.unmodifiableList(newModifiers);
    }

    /**
     * Re-rolls unlocked modifier types, maintaining prefix/suffix counts.
     *
     * <p>Locked modifiers are preserved. Unlocked prefixes are replaced with new
     * prefix types, and unlocked suffixes are replaced with new suffix types.
     *
     * @param prefixes Existing prefix modifiers
     * @param suffixes Existing suffix modifiers
     * @param random Random source
     * @param level The map level (for scaling modifier values)
     * @return RollResult with rerolled types and values
     */
    @Nonnull
    public RollResult rerollTypesSplit(
            @Nonnull List<RealmModifier> prefixes,
            @Nonnull List<RealmModifier> suffixes,
            @Nonnull Random random,
            int level) {

        // Collect locked modifiers
        List<RealmModifier> lockedPrefixes = prefixes.stream()
            .filter(RealmModifier::locked)
            .toList();
        List<RealmModifier> lockedSuffixes = suffixes.stream()
            .filter(RealmModifier::locked)
            .toList();

        int unlockedPrefixCount = prefixes.size() - lockedPrefixes.size();
        int unlockedSuffixCount = suffixes.size() - lockedSuffixes.size();

        // Build exclusions from all locked modifiers
        Set<RealmModifierType> exclusions = new HashSet<>();
        lockedPrefixes.forEach(m -> exclusions.add(m.type()));
        lockedSuffixes.forEach(m -> exclusions.add(m.type()));

        // Roll new prefixes (force prefix types only)
        List<RealmModifier> newPrefixes = new ArrayList<>(lockedPrefixes);
        for (int i = 0; i < unlockedPrefixCount; i++) {
            RealmModifierType type = selectPrefixType(random, exclusions);
            if (type == null) {
                break;
            }
            newPrefixes.add(rollModifier(type, random, level));
            exclusions.add(type);
        }

        // Roll new suffixes (force suffix types only)
        List<RealmModifier> newSuffixes = new ArrayList<>(lockedSuffixes);
        for (int i = 0; i < unlockedSuffixCount; i++) {
            RealmModifierType type = selectSuffixType(random, exclusions);
            if (type == null) {
                break;
            }
            newSuffixes.add(rollModifier(type, random, level));
            exclusions.add(type);
        }

        return new RollResult(newPrefixes, newSuffixes);
    }

    /**
     * Selects a random prefix type from the prefix pool.
     */
    @javax.annotation.Nullable
    private RealmModifierType selectPrefixType(
            @Nonnull Random random,
            @Nonnull Set<RealmModifierType> exclusions) {

        return pool.selectFromCategory(random, RealmModifierType.Category.PREFIX, exclusions);
    }

    /**
     * Selects a random suffix type from the suffix pool.
     */
    @javax.annotation.Nullable
    private RealmModifierType selectSuffixType(
            @Nonnull Random random,
            @Nonnull Set<RealmModifierType> exclusions) {

        return pool.selectFromCategory(random, RealmModifierType.Category.SUFFIX, exclusions);
    }

    /**
     * Adds a random modifier to an existing set, preserving prefix/suffix split.
     *
     * <p>The new modifier will be added to the appropriate list based on its type.
     *
     * @param prefixes Existing prefix modifiers
     * @param suffixes Existing suffix modifiers
     * @param maxModifiers Maximum allowed modifiers (combined)
     * @param random Random source
     * @param level The map level (for scaling modifier values)
     * @return RollResult with added modifier, or original if at max
     */
    @Nonnull
    public RollResult addModifierSplit(
            @Nonnull List<RealmModifier> prefixes,
            @Nonnull List<RealmModifier> suffixes,
            int maxModifiers,
            @Nonnull Random random,
            int level) {

        int total = prefixes.size() + suffixes.size();
        if (total >= maxModifiers) {
            return new RollResult(prefixes, suffixes);
        }

        Set<RealmModifierType> existingTypes = new HashSet<>();
        prefixes.forEach(m -> existingTypes.add(m.type()));
        suffixes.forEach(m -> existingTypes.add(m.type()));

        RealmModifierType type = pool.selectRandom(random, existingTypes, existingTypes);
        if (type == null) {
            return new RollResult(prefixes, suffixes);
        }

        RealmModifier newMod = rollModifier(type, random, level);

        if (type.isPrefix()) {
            List<RealmModifier> newPrefixes = new ArrayList<>(prefixes);
            newPrefixes.add(newMod);
            return new RollResult(newPrefixes, suffixes);
        } else {
            List<RealmModifier> newSuffixes = new ArrayList<>(suffixes);
            newSuffixes.add(newMod);
            return new RollResult(prefixes, newSuffixes);
        }
    }

    /**
     * Adds a specific prefix modifier.
     *
     * @param prefixes Existing prefix modifiers
     * @param suffixes Existing suffix modifiers
     * @param maxModifiers Maximum allowed modifiers (combined)
     * @param random Random source
     * @param level The map level (for scaling modifier values)
     * @return RollResult with added prefix, or original if at max or no prefixes available
     */
    @Nonnull
    public RollResult addPrefixSplit(
            @Nonnull List<RealmModifier> prefixes,
            @Nonnull List<RealmModifier> suffixes,
            int maxModifiers,
            @Nonnull Random random,
            int level) {

        int total = prefixes.size() + suffixes.size();
        if (total >= maxModifiers) {
            return new RollResult(prefixes, suffixes);
        }

        Set<RealmModifierType> existingTypes = new HashSet<>();
        prefixes.forEach(m -> existingTypes.add(m.type()));
        suffixes.forEach(m -> existingTypes.add(m.type()));

        RealmModifierType type = selectPrefixType(random, existingTypes);
        if (type == null) {
            return new RollResult(prefixes, suffixes);
        }

        List<RealmModifier> newPrefixes = new ArrayList<>(prefixes);
        newPrefixes.add(rollModifier(type, random, level));
        return new RollResult(newPrefixes, suffixes);
    }

    /**
     * Adds a specific suffix modifier.
     *
     * @param prefixes Existing prefix modifiers
     * @param suffixes Existing suffix modifiers
     * @param maxModifiers Maximum allowed modifiers (combined)
     * @param random Random source
     * @param level The map level (for scaling modifier values)
     * @return RollResult with added suffix, or original if at max or no suffixes available
     */
    @Nonnull
    public RollResult addSuffixSplit(
            @Nonnull List<RealmModifier> prefixes,
            @Nonnull List<RealmModifier> suffixes,
            int maxModifiers,
            @Nonnull Random random,
            int level) {

        int total = prefixes.size() + suffixes.size();
        if (total >= maxModifiers) {
            return new RollResult(prefixes, suffixes);
        }

        Set<RealmModifierType> existingTypes = new HashSet<>();
        prefixes.forEach(m -> existingTypes.add(m.type()));
        suffixes.forEach(m -> existingTypes.add(m.type()));

        RealmModifierType type = selectSuffixType(random, existingTypes);
        if (type == null) {
            return new RollResult(prefixes, suffixes);
        }

        List<RealmModifier> newSuffixes = new ArrayList<>(suffixes);
        newSuffixes.add(rollModifier(type, random, level));
        return new RollResult(prefixes, newSuffixes);
    }

    /**
     * Removes a random unlocked modifier.
     *
     * <p>This is used by Orb of Scouring-like stones.
     *
     * @param modifiers Existing modifiers
     * @param random Random source
     * @return New list with one unlocked modifier removed
     * @deprecated Use {@link #removeModifierSplit(List, List, Random)} for explicit prefix/suffix handling
     */
    @Deprecated
    @Nonnull
    public List<RealmModifier> removeModifier(
            @Nonnull List<RealmModifier> modifiers,
            @Nonnull Random random) {

        List<RealmModifier> unlocked = modifiers.stream()
            .filter(m -> !m.locked())
            .toList();

        if (unlocked.isEmpty()) {
            return modifiers;
        }

        // Pick random unlocked modifier to remove
        RealmModifier toRemove = unlocked.get(random.nextInt(unlocked.size()));

        return modifiers.stream()
            .filter(m -> m != toRemove)
            .toList();
    }

    /**
     * Removes a random unlocked modifier, preserving prefix/suffix split.
     *
     * @param prefixes Existing prefix modifiers
     * @param suffixes Existing suffix modifiers
     * @param random Random source
     * @return RollResult with one unlocked modifier removed
     */
    @Nonnull
    public RollResult removeModifierSplit(
            @Nonnull List<RealmModifier> prefixes,
            @Nonnull List<RealmModifier> suffixes,
            @Nonnull Random random) {

        // Collect all unlocked modifiers with their source list info
        List<RealmModifier> unlockedPrefixes = prefixes.stream()
            .filter(m -> !m.locked())
            .toList();
        List<RealmModifier> unlockedSuffixes = suffixes.stream()
            .filter(m -> !m.locked())
            .toList();

        int totalUnlocked = unlockedPrefixes.size() + unlockedSuffixes.size();
        if (totalUnlocked == 0) {
            return new RollResult(prefixes, suffixes);
        }

        // Pick random unlocked modifier
        int pick = random.nextInt(totalUnlocked);

        if (pick < unlockedPrefixes.size()) {
            // Remove from prefixes
            RealmModifier toRemove = unlockedPrefixes.get(pick);
            List<RealmModifier> newPrefixes = prefixes.stream()
                .filter(m -> m != toRemove)
                .toList();
            return new RollResult(newPrefixes, suffixes);
        } else {
            // Remove from suffixes
            RealmModifier toRemove = unlockedSuffixes.get(pick - unlockedPrefixes.size());
            List<RealmModifier> newSuffixes = suffixes.stream()
                .filter(m -> m != toRemove)
                .toList();
            return new RollResult(prefixes, newSuffixes);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // DIFFICULTY CALCULATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Calculates the total difficulty rating of modifiers.
     *
     * <p>Used to display map difficulty and calculate rewards.
     *
     * @param modifiers The modifiers to evaluate
     * @return Total difficulty value (0 = no modifiers, higher = harder)
     */
    public int calculateDifficulty(@Nonnull List<RealmModifier> modifiers) {
        return modifiers.stream()
            .filter(RealmModifier::increasesDifficulty)
            .mapToInt(this::calculateModifierDifficulty)
            .sum();
    }

    /**
     * Calculates difficulty contribution of a single modifier.
     *
     * <p>Takes into account both the type's weight and the rolled value.
     *
     * @param modifier The modifier to evaluate
     * @return Difficulty contribution
     */
    public int calculateModifierDifficulty(@Nonnull RealmModifier modifier) {
        if (!modifier.increasesDifficulty()) {
            return 0;
        }

        int baseWeight = modifier.getDifficultyWeight();
        float valuePercent = modifier.getValuePercentile();

        // Scale from base weight to double at max value
        return Math.round(baseWeight * (1 + valuePercent));
    }

    /**
     * Calculates a reward multiplier based on difficulty modifiers.
     *
     * <p>Higher difficulty = higher rewards.
     *
     * @param modifiers The modifiers to evaluate
     * @return Reward multiplier (1.0 = no bonus, 1.5 = +50% rewards)
     */
    public float calculateRewardMultiplier(@Nonnull List<RealmModifier> modifiers) {
        // Base multiplier
        float multiplier = 1.0f;

        // Add bonuses from reward modifiers
        for (RealmModifier mod : modifiers) {
            if (mod.isRewardModifier()) {
                // Direct reward modifiers add their value as percentage
                switch (mod.type()) {
                    case ITEM_QUANTITY, ITEM_RARITY, EXPERIENCE_BONUS, STONE_DROP_BONUS ->
                        multiplier += mod.value() / 100.0f;
                    default -> { }
                }
            }
        }

        // Difficulty modifiers also increase rewards slightly
        int difficulty = calculateDifficulty(modifiers);
        multiplier += difficulty * 0.02f; // +2% per difficulty point

        return multiplier;
    }

    /**
     * Gets the item quantity bonus from modifiers.
     *
     * @param modifiers The modifiers to check
     * @return Item quantity bonus percentage (e.g., 25 = +25%)
     */
    public int getItemQuantityBonus(@Nonnull List<RealmModifier> modifiers) {
        return modifiers.stream()
            .filter(m -> m.type() == RealmModifierType.ITEM_QUANTITY)
            .mapToInt(RealmModifier::value)
            .sum();
    }

    /**
     * Gets the item rarity bonus from modifiers.
     *
     * @param modifiers The modifiers to check
     * @return Item rarity bonus percentage (e.g., 30 = +30%)
     */
    public int getItemRarityBonus(@Nonnull List<RealmModifier> modifiers) {
        return modifiers.stream()
            .filter(m -> m.type() == RealmModifierType.ITEM_RARITY)
            .mapToInt(RealmModifier::value)
            .sum();
    }

    /**
     * Gets the experience bonus from modifiers.
     *
     * @param modifiers The modifiers to check
     * @return Experience bonus percentage
     */
    public int getExperienceBonus(@Nonnull List<RealmModifier> modifiers) {
        return modifiers.stream()
            .filter(m -> m.type() == RealmModifierType.EXPERIENCE_BONUS)
            .mapToInt(RealmModifier::value)
            .sum();
    }

    // ═══════════════════════════════════════════════════════════════════
    // UTILITY
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets the modifier pool used by this roller.
     *
     * @return The modifier pool
     */
    @Nonnull
    public RealmModifierPool getPool() {
        return pool;
    }

    /**
     * Gets the configuration.
     *
     * @return The modifier configuration
     */
    @Nonnull
    public RealmModifierConfig getConfig() {
        return config;
    }
}
