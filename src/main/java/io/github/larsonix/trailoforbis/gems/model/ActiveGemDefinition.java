package io.github.larsonix.trailoforbis.gems.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public record ActiveGemDefinition(
        @Nonnull String id,
        @Nonnull String name,
        @Nonnull String description,
        @Nonnull GemTags tags,
        @Nonnull List<String> weaponCategories,
        @Nonnull List<String> gearSlots,
        @Nonnull DamageConfig damage,
        @Nonnull CostConfig cost,
        float cooldown,
        @Nonnull CastType castType,
        @Nullable ChannelConfig channel,
        @Nullable AilmentConfig ailment,
        @Nonnull Map<String, Float> qualityBonuses,
        @Nonnull VisualsConfig visuals
) implements GemDefinition {

    public ActiveGemDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(tags, "tags");
        Objects.requireNonNull(damage, "damage");
        Objects.requireNonNull(cost, "cost");
        Objects.requireNonNull(castType, "castType");
        weaponCategories = List.copyOf(weaponCategories);
        gearSlots = List.copyOf(gearSlots);
        qualityBonuses = Map.copyOf(qualityBonuses);
    }

    @Override
    @Nonnull
    public GemType gemType() {
        return GemType.ACTIVE;
    }

    public record DamageConfig(float basePercent, @Nullable String element, float aoeRadius) {
        public static final DamageConfig NONE = new DamageConfig(0.0f, null, 0.0f);
    }

    public record CostConfig(@Nonnull String type, float amount, float staminaAmount, float manaAmount, float amountPerSecond) {
        public static final CostConfig FREE = new CostConfig("none", 0.0f, 0.0f, 0.0f, 0.0f);

        public CostConfig {
            Objects.requireNonNull(type, "cost type");
        }
    }

    public record ChannelConfig(float maxDuration, float tickRate, float movementMultiplier) {
    }

    public record AilmentConfig(@Nonnull String type, float baseChance) {
        public AilmentConfig {
            Objects.requireNonNull(type, "ailment type");
        }
    }

    public record VisualsConfig(@Nullable String projectile, @Nullable String castSound, @Nullable String impactSound, @Nullable String impactParticles) {
        public static final VisualsConfig EMPTY = new VisualsConfig(null, null, null, null);
    }
}
