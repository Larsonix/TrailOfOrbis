package io.github.larsonix.trailoforbis.maps.modifiers;

import com.hypixel.hytale.codec.codecs.EnumCodec;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;

/**
 * Defines all modifier types that can appear on Realm Maps.
 *
 * <p>Modifiers are divided into two categories:
 * <ul>
 *   <li><b>PREFIX (Difficulty):</b> Make monsters stronger or add hazards</li>
 *   <li><b>SUFFIX (Reward):</b> Increase player rewards (IIQ, IIR, XP, etc.)</li>
 * </ul>
 *
 * <p>Higher difficulty modifiers grant better rewards. The difficultyWeight
 * contributes to the overall difficulty rating displayed on the map.
 */
public enum RealmModifierType {

    // ═══════════════════════════════════════════════════════════════════
    // PREFIX MODIFIERS (difficulty — increases challenge)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Monsters deal increased damage.
     * Value: percentage increase (e.g., 40 = +40% damage)
     */
    MONSTER_DAMAGE(
        "Monster Damage",
        Category.PREFIX,
        1,
        10, 100
    ),

    /**
     * Monsters have increased health.
     * Value: percentage increase (e.g., 50 = +50% health)
     */
    MONSTER_HEALTH(
        "Monster Life",
        Category.PREFIX,
        1,
        10, 150
    ),

    /**
     * Monsters move faster.
     * Value: percentage increase (e.g., 20 = +20% speed)
     */
    MONSTER_SPEED(
        "Monster Speed",
        Category.PREFIX,
        1,
        5, 40
    ),

    /**
     * Monsters attack more frequently.
     * Value: percentage increase (e.g., 25 = +25% attack speed)
     */
    MONSTER_ATTACK_SPEED(
        "Monster Attack Speed",
        Category.PREFIX,
        1,
        10, 50
    ),

    /**
     * Realm has a time limit reduction.
     * Value: percentage reduction to timeout
     */
    REDUCED_TIME(
        "Reduced Time",
        Category.PREFIX,
        2,
        10, 40
    ),

    /**
     * Healing effectiveness is reduced.
     * Value: percentage reduction (e.g., 40 = 40% less healing)
     * Note: Display-only for now (effect deferred)
     */
    REDUCED_HEALING(
        "Reduced Healing",
        Category.PREFIX,
        1,
        20, 60
    ),

    /**
     * Life regeneration is disabled.
     * Value: ignored (binary effect)
     * Note: Display-only for now (effect deferred)
     */
    NO_REGENERATION(
        "No Regeneration",
        Category.PREFIX,
        3,
        1, 1
    ),

    // ═══════════════════════════════════════════════════════════════════
    // SUFFIX MODIFIERS (reward — improves player rewards)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Increases quantity of item drops.
     * Value: percentage increase (e.g., 20 = +20% IIQ)
     */
    ITEM_QUANTITY(
        "Item Quantity",
        Category.SUFFIX,
        0,
        5, 50
    ),

    /**
     * Increases rarity of item drops.
     * Value: percentage increase (e.g., 30 = +30% IIR)
     */
    ITEM_RARITY(
        "Item Rarity",
        Category.SUFFIX,
        0,
        5, 40
    ),

    /**
     * Increases experience gained from kills.
     * Value: percentage increase (e.g., 25 = +25% XP)
     * Note: Display-only for now (effect deferred)
     */
    EXPERIENCE_BONUS(
        "Experience",
        Category.SUFFIX,
        0,
        10, 50
    ),

    /**
     * Increases stone drop chance.
     * Value: percentage increase (e.g., 15 = +15% stone drops)
     * Note: Display-only for now (effect deferred)
     */
    STONE_DROP_BONUS(
        "Stone Drops",
        Category.SUFFIX,
        0,
        5, 30
    ),

    /**
     * More monsters spawn in the realm.
     * Value: percentage increase (e.g., 30 = +30% monster count)
     * Note: Moved from prefix to suffix — more monsters = more loot.
     */
    EXTRA_MONSTERS(
        "Monster Count",
        Category.SUFFIX,
        0,
        10, 50
    ),

    /**
     * Higher chance for elite monster spawns.
     * Value: percentage increase to elite chance
     * Note: Moved from prefix to suffix — more elites = better drops.
     */
    ELITE_CHANCE(
        "Elite Chance",
        Category.SUFFIX,
        0,
        5, 30
    );

    private final String displayName;
    private final Category category;
    private final int difficultyWeight;
    private final int minValue;
    private final int maxValue;

    /**
     * Modifier categories: PREFIX (difficulty) or SUFFIX (reward).
     */
    public enum Category {
        /** Difficulty modifiers (prefixes) */
        PREFIX("Prefix", true),
        /** Reward modifiers (suffixes) */
        SUFFIX("Suffix", false);

        private final String displayName;
        private final boolean increasesDifficulty;

        Category(String displayName, boolean increasesDifficulty) {
            this.displayName = displayName;
            this.increasesDifficulty = increasesDifficulty;
        }

        public String getDisplayName() {
            return displayName;
        }

        public boolean increasesDifficulty() {
            return increasesDifficulty;
        }
    }

    RealmModifierType(
            @Nonnull String displayName,
            @Nonnull Category category,
            int difficultyWeight,
            int minValue,
            int maxValue) {
        this.displayName = displayName;
        this.category = category;
        this.difficultyWeight = difficultyWeight;
        this.minValue = minValue;
        this.maxValue = maxValue;
    }

    /**
     * Gets the human-readable display name.
     *
     * @return The display name (e.g., "Monster Damage", "Item Quantity")
     */
    @Nonnull
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Formats the modifier with its value for gear-style display.
     *
     * <p>Binary modifiers (like NO_REGENERATION) display without a value.
     *
     * @param value The modifier value
     * @return Formatted string (e.g., "+40% Monster Damage")
     */
    @Nonnull
    public String formatValue(int value) {
        if (isBinary()) {
            return displayName;
        }
        return "+" + value + "% " + displayName;
    }

    /**
     * Gets the category of this modifier.
     *
     * @return The category (PREFIX or SUFFIX)
     */
    @Nonnull
    public Category getCategory() {
        return category;
    }

    /**
     * Gets the difficulty weight contribution.
     *
     * <p>Higher weight means the modifier contributes more to
     * the map's overall difficulty rating.
     *
     * @return Difficulty weight (0-3)
     */
    public int getDifficultyWeight() {
        return difficultyWeight;
    }

    /**
     * Gets the minimum rollable value for this modifier.
     *
     * @return Minimum value
     */
    public int getMinValue() {
        return minValue;
    }

    /**
     * Gets the maximum rollable value for this modifier.
     *
     * @return Maximum value
     */
    public int getMaxValue() {
        return maxValue;
    }

    /**
     * Checks if this is a binary modifier (value doesn't matter).
     *
     * @return true if minValue equals maxValue
     */
    public boolean isBinary() {
        return minValue == maxValue;
    }

    /**
     * Checks if this modifier increases difficulty.
     *
     * @return true if this modifier makes the realm harder
     */
    public boolean increasesDifficulty() {
        return category.increasesDifficulty();
    }

    /**
     * Checks if this modifier improves rewards.
     *
     * @return true if this is a SUFFIX (reward) modifier
     */
    public boolean isRewardModifier() {
        return category == Category.SUFFIX;
    }

    /**
     * Checks if this modifier is a prefix (difficulty modifier).
     *
     * <p>Prefixes include MONSTER, ENVIRONMENT, and SPECIAL categories.
     * These modifiers increase difficulty and appear first in the modifier list.
     *
     * @return true if this is a prefix modifier
     */
    public boolean isPrefix() {
        return !isRewardModifier();
    }

    /**
     * Checks if this modifier is a suffix (reward modifier).
     *
     * <p>Suffixes are REWARD category modifiers.
     * These modifiers improve rewards and appear after prefixes in the modifier list.
     *
     * @return true if this is a suffix modifier
     */
    public boolean isSuffix() {
        return isRewardModifier();
    }

    /**
     * Gets all modifiers in a specific category.
     *
     * @param category The category to filter by
     * @return List of modifiers in that category
     */
    @Nonnull
    public static List<RealmModifierType> byCategory(@Nonnull Category category) {
        return Arrays.stream(values())
            .filter(m -> m.category == category)
            .toList();
    }

    /**
     * Gets all modifiers that increase difficulty.
     *
     * @return List of difficulty-increasing modifiers
     */
    @Nonnull
    public static List<RealmModifierType> getDifficultyModifiers() {
        return Arrays.stream(values())
            .filter(RealmModifierType::increasesDifficulty)
            .toList();
    }

    /**
     * Gets all reward modifiers (suffixes).
     *
     * @return List of reward modifiers
     */
    @Nonnull
    public static List<RealmModifierType> getRewardModifiers() {
        return byCategory(Category.SUFFIX);
    }

    /**
     * Gets all prefix modifier types (difficulty modifiers).
     *
     * <p>This is an alias for {@link #getDifficultyModifiers()}.
     *
     * @return List of prefix modifier types
     */
    @Nonnull
    public static List<RealmModifierType> getPrefixTypes() {
        return getDifficultyModifiers();
    }

    /**
     * Gets all suffix modifier types (reward modifiers).
     *
     * <p>This is an alias for {@link #getRewardModifiers()}.
     *
     * @return List of suffix modifier types
     */
    @Nonnull
    public static List<RealmModifierType> getSuffixTypes() {
        return getRewardModifiers();
    }

    /**
     * Parse modifier type from string (case-insensitive).
     *
     * @param name Modifier type name
     * @return The corresponding RealmModifierType
     * @throws IllegalArgumentException if name is not recognized
     */
    @Nonnull
    public static RealmModifierType fromString(@Nonnull String name) {
        if (name == null) {
            throw new IllegalArgumentException("Modifier type name cannot be null");
        }
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown modifier type: " + name);
        }
    }

    /**
     * Attempts to parse a modifier type from string, returning null if not found.
     *
     * <p>Used for codec migration: old saved maps may contain modifier types
     * that have been removed (e.g., DAMAGE_OVER_TIME, REFLECT_DAMAGE).
     *
     * @param name The modifier type name
     * @return The modifier type, or null if not recognized
     */
    @Nullable
    public static RealmModifierType tryFromString(@Nonnull String name) {
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Codec for serialization/deserialization.
     */
    public static final EnumCodec<RealmModifierType> CODEC = new EnumCodec<>(RealmModifierType.class);
}
