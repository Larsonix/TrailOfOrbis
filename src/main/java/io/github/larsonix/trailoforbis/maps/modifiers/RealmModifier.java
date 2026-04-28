package io.github.larsonix.trailoforbis.maps.modifiers;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.logger.HytaleLogger;
import io.github.larsonix.trailoforbis.stones.ItemModifier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Represents a single modifier on a Realm Map.
 *
 * <p>Implements {@link ItemModifier} to enable shared stone functionality
 * with gear modifiers.
 *
 * <p>Example modifiers:
 * <ul>
 *   <li>"Monsters deal 40% increased Damage" (MONSTER_DAMAGE, 40)</li>
 *   <li>"+25% increased Item Rarity" (ITEM_RARITY, 25)</li>
 *   <li>"Life Regeneration is disabled" (NO_REGENERATION, 1)</li>
 * </ul>
 *
 * @param type The modifier type
 * @param value The modifier value (interpretation depends on type)
 * @param locked Whether this modifier is protected from rerolling
 */
public record RealmModifier(
    @Nonnull RealmModifierType type,
    int value,
    boolean locked
) implements ItemModifier {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /**
     * Compact constructor for validation.
     */
    public RealmModifier {
        Objects.requireNonNull(type, "type cannot be null");
    }

    /**
     * Creates an unlocked modifier with the given type and value.
     *
     * @param type The modifier type
     * @param value The value
     * @return A new unlocked RealmModifier
     */
    @Nonnull
    public static RealmModifier of(@Nonnull RealmModifierType type, int value) {
        return new RealmModifier(type, value, false);
    }

    // ═══════════════════════════════════════════════════════════════════
    // ItemModifier IMPLEMENTATION
    // ═══════════════════════════════════════════════════════════════════

    @Override
    @Nonnull
    public String id() {
        return type.name();
    }

    @Override
    @Nonnull
    public String displayName() {
        return type.getDisplayName();
    }

    @Override
    public double getValue() {
        return value;
    }

    @Override
    public boolean isLocked() {
        return locked;
    }

    @Override
    @Nonnull
    public ItemModifier withLocked(boolean newLocked) {
        return new RealmModifier(type, value, newLocked);
    }

    @Override
    @Nonnull
    public ItemModifier withValue(double newValue) {
        return new RealmModifier(type, (int) newValue, locked);
    }

    @Override
    @Nonnull
    public String formatForTooltip() {
        String base = type.formatValue(value);
        if (locked) {
            return "🔒 " + base;
        }
        return base;
    }

    // ═══════════════════════════════════════════════════════════════════
    // REALM-SPECIFIC METHODS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets the modifier category.
     *
     * @return The category (REWARD, MONSTER, ENVIRONMENT, SPECIAL)
     */
    @Nonnull
    public RealmModifierType.Category getCategory() {
        return type.getCategory();
    }

    /**
     * Gets the difficulty weight contributed by this modifier.
     *
     * <p>Higher values indicate more difficult modifiers.
     *
     * @return Difficulty weight (0-3)
     */
    public int getDifficultyWeight() {
        return type.getDifficultyWeight();
    }

    /**
     * Checks if this is a reward modifier.
     *
     * @return true if this modifier improves rewards
     */
    public boolean isRewardModifier() {
        return type.isRewardModifier();
    }

    /**
     * Checks if this modifier increases difficulty.
     *
     * @return true if this modifier makes the realm harder
     */
    public boolean increasesDifficulty() {
        return type.increasesDifficulty();
    }

    /**
     * Checks if this modifier is a prefix (difficulty modifier).
     *
     * @return true if this is a prefix modifier
     */
    public boolean isPrefix() {
        return type.isPrefix();
    }

    /**
     * Checks if this modifier is a suffix (reward modifier).
     *
     * @return true if this is a suffix modifier
     */
    public boolean isSuffix() {
        return type.isSuffix();
    }

    /**
     * Gets the percentage of the value range this modifier occupies.
     *
     * <p>Used to calculate modifier quality (how good the roll was).
     * 0% = minimum roll, 100% = maximum roll.
     *
     * @return Value as percentage of range (0.0 to 1.0)
     */
    public float getValuePercentile() {
        int range = type.getMaxValue() - type.getMinValue();
        if (range == 0) {
            return 1.0f;
        }
        float percentile = (float) (value - type.getMinValue()) / range;
        return Math.max(0.0f, Math.min(percentile, 1.0f));
    }

    /**
     * Creates a copy with a new locked state.
     *
     * @param locked The new locked state
     * @return New modifier with updated locked state
     */
    @Nonnull
    public RealmModifier withLockedState(boolean locked) {
        return new RealmModifier(type, value, locked);
    }

    /**
     * Creates a copy with a new value.
     *
     * @param newValue The new value
     * @return New modifier with updated value
     */
    @Nonnull
    public RealmModifier withNewValue(int newValue) {
        return new RealmModifier(type, newValue, locked);
    }

    // ═══════════════════════════════════════════════════════════════════
    // CODEC
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Codec for serialization/deserialization.
     *
     * <p>Returns null for modifiers with removed types (e.g., DAMAGE_OVER_TIME).
     * The list codec in {@link io.github.larsonix.trailoforbis.maps.codec.RealmCodecs}
     * filters these nulls out during decoding.
     */
    public static final Codec<RealmModifier> CODEC = new Codec<>() {
        private static final String KEY_TYPE = "type";
        private static final String KEY_VALUE = "value";
        private static final String KEY_LOCKED = "locked";

        @Override
        @Nullable
        public RealmModifier decode(@Nonnull org.bson.BsonValue encoded, @Nonnull com.hypixel.hytale.codec.ExtraInfo extraInfo) {
            if (!(encoded instanceof org.bson.BsonDocument doc)) {
                throw new IllegalArgumentException("Expected BsonDocument");
            }
            String typeStr = doc.getString(KEY_TYPE).getValue();
            RealmModifierType type = RealmModifierType.tryFromString(typeStr);
            if (type == null) {
                LOGGER.atWarning().log("Skipping removed modifier type: %s (old saved data)", typeStr);
                return null;
            }
            int value = doc.getInt32(KEY_VALUE).getValue();
            boolean locked = doc.containsKey(KEY_LOCKED) && doc.getBoolean(KEY_LOCKED).getValue();
            return new RealmModifier(type, value, locked);
        }

        @Override
        @Nonnull
        public org.bson.BsonValue encode(@Nonnull RealmModifier modifier, @Nonnull com.hypixel.hytale.codec.ExtraInfo extraInfo) {
            org.bson.BsonDocument doc = new org.bson.BsonDocument();
            doc.put(KEY_TYPE, new org.bson.BsonString(modifier.type().name()));
            doc.put(KEY_VALUE, new org.bson.BsonInt32(modifier.value()));
            doc.put(KEY_LOCKED, new org.bson.BsonBoolean(modifier.locked()));
            return doc;
        }

        @Override
        @Nonnull
        public com.hypixel.hytale.codec.schema.config.Schema toSchema(@Nonnull com.hypixel.hytale.codec.schema.SchemaContext context) {
            return new com.hypixel.hytale.codec.schema.config.ObjectSchema();
        }
    };
}
