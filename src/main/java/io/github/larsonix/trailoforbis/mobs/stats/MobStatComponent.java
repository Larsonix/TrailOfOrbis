package io.github.larsonix.trailoforbis.mobs.stats;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.TrailOfOrbis;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * ECS component attached to mob entities that stores RPG scaling data.
 *
 * <p>Holds the mob's level, distance-based bonus, special multiplier (for elite/boss tiers),
 * and the generated {@link MobStatProfile} containing all computed combat stats.
 */
public class MobStatComponent implements Component<EntityStore> {

    // ==================== CODEC ====================
    // Serializes scaling inputs only — MobStatProfile is recalculated on load.

    public static final BuilderCodec<MobStatComponent> CODEC = BuilderCodec.builder(
            MobStatComponent.class, MobStatComponent::new
        )
        .append(new KeyedCodec<>("MobLevel", Codec.INTEGER),
                MobStatComponent::setMobLevel, MobStatComponent::getMobLevel).add()
        .append(new KeyedCodec<>("DistanceLevel", Codec.INTEGER),
                MobStatComponent::setDistanceLevel, MobStatComponent::getDistanceLevel).add()
        .append(new KeyedCodec<>("DistanceBonus", Codec.DOUBLE),
                MobStatComponent::setDistanceBonus, MobStatComponent::getDistanceBonus).add()
        .append(new KeyedCodec<>("SpecialMultiplier", Codec.DOUBLE),
                MobStatComponent::setSpecialMultiplier, MobStatComponent::getSpecialMultiplier).add()
        .append(new KeyedCodec<>("DynamicallyScaled", Codec.BOOLEAN),
                MobStatComponent::setDynamicallyScaled, MobStatComponent::isDynamicallyScaled).add()
        .build();

    // ==================== FIELDS ====================

    private int mobLevel;
    private int distanceLevel;
    private double distanceBonus;
    private double specialMultiplier;
    private boolean dynamicallyScaled;
    @Nullable
    private MobStatProfile stats;

    public MobStatComponent() {
        this.mobLevel = 1;
        this.distanceLevel = 0;
        this.distanceBonus = 0.0;
        this.specialMultiplier = 1.0;
        this.dynamicallyScaled = false;
        this.stats = MobStatProfile.UNSCALED;
    }

    private MobStatComponent(@Nonnull MobStatComponent other) {
        this.mobLevel = other.mobLevel;
        this.distanceLevel = other.distanceLevel;
        this.distanceBonus = other.distanceBonus;
        this.specialMultiplier = other.specialMultiplier;
        this.dynamicallyScaled = other.dynamicallyScaled;
        this.stats = other.stats;
    }

    @Nonnull
    public static ComponentType<EntityStore, MobStatComponent> getComponentType() {
        return TrailOfOrbis.getInstance().getMobStatComponentType();
    }

    public int getMobLevel() {
        return mobLevel;
    }

    public void setMobLevel(int mobLevel) {
        this.mobLevel = mobLevel;
    }

    public int getDistanceLevel() {
        return distanceLevel;
    }

    public void setDistanceLevel(int distanceLevel) {
        this.distanceLevel = distanceLevel;
    }

    public double getDistanceBonus() {
        return distanceBonus;
    }

    public void setDistanceBonus(double distanceBonus) {
        this.distanceBonus = distanceBonus;
    }

    public double getSpecialMultiplier() {
        return specialMultiplier;
    }

    public void setSpecialMultiplier(double specialMultiplier) {
        this.specialMultiplier = specialMultiplier;
    }

    public boolean isDynamicallyScaled() {
        return dynamicallyScaled;
    }

    public void setDynamicallyScaled(boolean dynamicallyScaled) {
        this.dynamicallyScaled = dynamicallyScaled;
    }

    @Nonnull
    public MobStatProfile getStats() {
        return stats != null ? stats : MobStatProfile.UNSCALED;
    }

    public void setStats(@Nonnull MobStatProfile stats) {
        this.stats = stats;
    }

    public boolean isScaled() {
        return mobLevel > 1 || distanceBonus > 0 || specialMultiplier != 1.0;
    }

    public boolean isBoss() {
        return specialMultiplier >= 2.5;
    }

    public boolean isElite() {
        return specialMultiplier >= 1.4 && specialMultiplier < 2.5;
    }

    @Nonnull
    public String getTierName() {
        if (isBoss()) return "Boss";
        if (isElite()) return "Elite";
        return "Normal";
    }

    public int getEffectivePower() {
        return stats != null ? stats.getEffectivePower() : mobLevel;
    }

    @Override
    @Nonnull
    public Component<EntityStore> clone() {
        return new MobStatComponent(this);
    }

    @Override
    public String toString() {
        return String.format(
                "MobStatComponent{lv=%d, dist=%.1f, mult=%.1f, tier=%s, stats=%s}",
                mobLevel, distanceBonus, specialMultiplier, getTierName(), stats
        );
    }
}
