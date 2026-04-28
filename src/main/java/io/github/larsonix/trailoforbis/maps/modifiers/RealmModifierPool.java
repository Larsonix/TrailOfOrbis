package io.github.larsonix.trailoforbis.maps.modifiers;

import io.github.larsonix.trailoforbis.maps.config.RealmModifierConfig;
import io.github.larsonix.trailoforbis.maps.config.RealmModifierConfig.ModifierSettings;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

/**
 * Weighted pool for selecting realm modifiers.
 *
 * <p>Supports two-tier selection:
 * <ol>
 *   <li>Category selection (PREFIX or SUFFIX)</li>
 *   <li>Modifier selection within the chosen category</li>
 * </ol>
 *
 * <p>The pool respects:
 * <ul>
 *   <li>Individual modifier weights from configuration</li>
 *   <li>Category weights for balancing modifier types</li>
 *   <li>Enabled/disabled state per modifier</li>
 *   <li>Incompatibility rules between modifiers</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * RealmModifierPool pool = new RealmModifierPool(config);
 *
 * // Select random modifier
 * RealmModifierType type = pool.selectRandom(random);
 *
 * // Select avoiding already-chosen types
 * Set<RealmModifierType> excluded = Set.of(MONSTER_DAMAGE);
 * type = pool.selectRandom(random, excluded);
 * }</pre>
 *
 * @see RealmModifierConfig
 * @see RealmModifierRoller
 */
public class RealmModifierPool {

    private final RealmModifierConfig config;

    /** Cached enabled modifiers by category. */
    private final Map<RealmModifierType.Category, List<RealmModifierType>> modifiersByCategory;

    /** Total weight per category. */
    private final Map<RealmModifierType.Category, Double> categoryTotalWeights;

    // ═══════════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Creates a new modifier pool from configuration.
     *
     * @param config The modifier configuration
     */
    public RealmModifierPool(@Nonnull RealmModifierConfig config) {
        this.config = Objects.requireNonNull(config, "Config cannot be null");
        this.modifiersByCategory = new EnumMap<>(RealmModifierType.Category.class);
        this.categoryTotalWeights = new EnumMap<>(RealmModifierType.Category.class);

        buildPool();
    }

    /**
     * Rebuilds the pool from current configuration.
     *
     * <p>Call this if configuration changes at runtime.
     */
    public void rebuild() {
        modifiersByCategory.clear();
        categoryTotalWeights.clear();
        buildPool();
    }

    private void buildPool() {
        for (RealmModifierType.Category category : RealmModifierType.Category.values()) {
            List<RealmModifierType> enabledModifiers = new ArrayList<>();
            double categoryWeight = 0;

            for (RealmModifierType type : RealmModifierType.byCategory(category)) {
                ModifierSettings settings = config.getModifierSettings(type);
                if (settings.isEnabled()) {
                    enabledModifiers.add(type);
                    categoryWeight += config.getWeight(type);
                }
            }

            modifiersByCategory.put(category, Collections.unmodifiableList(enabledModifiers));
            categoryTotalWeights.put(category, categoryWeight);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // SELECTION METHODS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Selects a random modifier type using weighted two-tier selection.
     *
     * @param random Random source
     * @return A randomly selected modifier type, or null if none available
     */
    @Nullable
    public RealmModifierType selectRandom(@Nonnull Random random) {
        return selectRandom(random, Collections.emptySet(), Collections.emptySet());
    }

    /**
     * Selects a random modifier type, excluding certain types.
     *
     * @param random Random source
     * @param excludedTypes Types to exclude from selection
     * @return A randomly selected modifier type, or null if none available
     */
    @Nullable
    public RealmModifierType selectRandom(
            @Nonnull Random random,
            @Nonnull Set<RealmModifierType> excludedTypes) {
        return selectRandom(random, excludedTypes, Collections.emptySet());
    }

    /**
     * Selects a random modifier type with full filtering.
     *
     * @param random Random source
     * @param excludedTypes Types to exclude from selection
     * @param incompatibleWith Types whose incompatibilities should be respected
     * @return A randomly selected modifier type, or null if none available
     */
    @Nullable
    public RealmModifierType selectRandom(
            @Nonnull Random random,
            @Nonnull Set<RealmModifierType> excludedTypes,
            @Nonnull Set<RealmModifierType> incompatibleWith) {

        // Build effective exclusion set including incompatibilities
        Set<RealmModifierType> effectiveExclusions = buildEffectiveExclusions(
            excludedTypes, incompatibleWith);

        // First select a category
        RealmModifierType.Category category = selectCategory(random, effectiveExclusions);
        if (category == null) {
            return null;
        }

        // Then select a modifier within that category
        return selectFromCategory(random, category, effectiveExclusions);
    }

    /**
     * Selects a random modifier from a specific category.
     *
     * @param random Random source
     * @param category The category to select from
     * @return A randomly selected modifier type, or null if none available
     */
    @Nullable
    public RealmModifierType selectFromCategory(
            @Nonnull Random random,
            @Nonnull RealmModifierType.Category category) {
        return selectFromCategory(random, category, Collections.emptySet());
    }

    /**
     * Selects a random modifier from a specific category, excluding certain types.
     *
     * @param random Random source
     * @param category The category to select from
     * @param excludedTypes Types to exclude
     * @return A randomly selected modifier type, or null if none available
     */
    @Nullable
    public RealmModifierType selectFromCategory(
            @Nonnull Random random,
            @Nonnull RealmModifierType.Category category,
            @Nonnull Set<RealmModifierType> excludedTypes) {

        List<RealmModifierType> available = modifiersByCategory.get(category);
        if (available == null || available.isEmpty()) {
            return null;
        }

        // Filter available modifiers
        List<RealmModifierType> candidates = available.stream()
            .filter(t -> !excludedTypes.contains(t))
            .toList();

        if (candidates.isEmpty()) {
            return null;
        }

        // Calculate total weight for candidates
        double totalWeight = 0;
        for (RealmModifierType type : candidates) {
            totalWeight += config.getWeight(type);
        }

        if (totalWeight <= 0) {
            // Equal weights fallback
            return candidates.get(random.nextInt(candidates.size()));
        }

        // Weighted selection
        double roll = random.nextDouble() * totalWeight;
        double cumulative = 0;

        for (RealmModifierType type : candidates) {
            cumulative += config.getWeight(type);
            if (roll < cumulative) {
                return type;
            }
        }

        // Fallback (should not happen)
        return candidates.getLast();
    }

    /**
     * Selects a random category using configured weights.
     *
     * @param random Random source
     * @param excludedTypes Types to consider when filtering categories
     * @return A randomly selected category, or null if none available
     */
    @Nullable
    private RealmModifierType.Category selectCategory(
            @Nonnull Random random,
            @Nonnull Set<RealmModifierType> excludedTypes) {

        // Build category weights considering exclusions
        Map<RealmModifierType.Category, Double> effectiveWeights = new EnumMap<>(
            RealmModifierType.Category.class);
        double effectiveTotalWeight = 0;

        for (RealmModifierType.Category category : RealmModifierType.Category.values()) {
            List<RealmModifierType> available = modifiersByCategory.get(category);
            if (available == null || available.isEmpty()) {
                continue;
            }

            // Check if category has any non-excluded modifiers
            boolean hasAvailable = available.stream()
                .anyMatch(t -> !excludedTypes.contains(t));

            if (hasAvailable) {
                double weight = categoryTotalWeights.getOrDefault(category, 0.0);
                effectiveWeights.put(category, weight);
                effectiveTotalWeight += weight;
            }
        }

        if (effectiveTotalWeight <= 0 || effectiveWeights.isEmpty()) {
            return null;
        }

        // Weighted selection
        double roll = random.nextDouble() * effectiveTotalWeight;
        double cumulative = 0;

        for (Map.Entry<RealmModifierType.Category, Double> entry : effectiveWeights.entrySet()) {
            cumulative += entry.getValue();
            if (roll < cumulative) {
                return entry.getKey();
            }
        }

        // Fallback
        return effectiveWeights.keySet().iterator().next();
    }

    /**
     * Builds the effective exclusion set including incompatibilities.
     */
    private Set<RealmModifierType> buildEffectiveExclusions(
            @Nonnull Set<RealmModifierType> directExclusions,
            @Nonnull Set<RealmModifierType> incompatibleWith) {

        if (directExclusions.isEmpty() && incompatibleWith.isEmpty()) {
            return Collections.emptySet();
        }

        Set<RealmModifierType> result = new HashSet<>(directExclusions);

        // Add incompatibilities from existing modifiers
        for (RealmModifierType existing : incompatibleWith) {
            ModifierSettings settings = config.getModifierSettings(existing);
            result.addAll(settings.getIncompatibleWith());
        }

        return result;
    }

    // ═══════════════════════════════════════════════════════════════════
    // QUERY METHODS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets all enabled modifiers in a category.
     *
     * @param category The category
     * @return Immutable list of enabled modifiers
     */
    @Nonnull
    public List<RealmModifierType> getEnabledModifiers(
            @Nonnull RealmModifierType.Category category) {
        return modifiersByCategory.getOrDefault(category, Collections.emptyList());
    }

    /**
     * Gets all enabled modifiers across all categories.
     *
     * @return List of all enabled modifier types
     */
    @Nonnull
    public List<RealmModifierType> getAllEnabledModifiers() {
        List<RealmModifierType> all = new ArrayList<>();
        for (List<RealmModifierType> list : modifiersByCategory.values()) {
            all.addAll(list);
        }
        return Collections.unmodifiableList(all);
    }

    /**
     * Gets the weight for a specific modifier type.
     *
     * @param type The modifier type
     * @return The configured weight
     */
    public double getWeight(@Nonnull RealmModifierType type) {
        return config.getWeight(type);
    }

    /**
     * Gets the total weight for a category.
     *
     * @param category The category
     * @return Total weight of enabled modifiers in category
     */
    public double getCategoryTotalWeight(@Nonnull RealmModifierType.Category category) {
        return categoryTotalWeights.getOrDefault(category, 0.0);
    }

    /**
     * Checks if a modifier type is enabled in the pool.
     *
     * @param type The modifier type to check
     * @return true if enabled
     */
    public boolean isEnabled(@Nonnull RealmModifierType type) {
        List<RealmModifierType> categoryList = modifiersByCategory.get(type.getCategory());
        return categoryList != null && categoryList.contains(type);
    }

    /**
     * Gets the number of enabled modifiers.
     *
     * @return Total count of enabled modifiers
     */
    public int getEnabledCount() {
        return modifiersByCategory.values().stream()
            .mapToInt(List::size)
            .sum();
    }

    /**
     * Gets the configuration used by this pool.
     *
     * @return The modifier configuration
     */
    @Nonnull
    public RealmModifierConfig getConfig() {
        return config;
    }
}
