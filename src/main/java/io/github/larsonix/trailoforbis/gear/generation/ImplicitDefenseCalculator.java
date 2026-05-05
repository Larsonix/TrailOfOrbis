package io.github.larsonix.trailoforbis.gear.generation;

import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig;
import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig.DefenseBaseRange;
import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig.ImplicitDefenseConfig;
import io.github.larsonix.trailoforbis.gear.model.ArmorImplicit;
import io.github.larsonix.trailoforbis.gear.model.ArmorMaterial;
import io.github.larsonix.trailoforbis.gear.model.EquipmentType;
import io.github.larsonix.trailoforbis.gear.model.EquipmentType.ArmorSlot;

import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.Random;

/**
 * Calculates armor implicit defense stats based on armor material, slot, and item level.
 *
 * <p>Implicits are guaranteed base defense stats on armor that:
 * <ul>
 *   <li>Scale with item level using {@link io.github.larsonix.trailoforbis.util.LevelScaling}</li>
 *   <li>Are NOT affected by gear quality</li>
 *   <li>Have a rollable range (displayed as "[72-98] Armor")</li>
 *   <li>Vary by material: plate→armor, leather→evasion, cloth→energy_shield, wood→max_health</li>
 *   <li>Vary by slot: chest gets full value, hands get 50%</li>
 *   <li>Shields get block_chance instead of material-based defense</li>
 * </ul>
 *
 * <p>The formula is:
 * <pre>
 * bonus = LevelScaling.getBonusPercent(level) / 100 × scaleFactor
 * scaledMin = (baseMin + bonus) × slotMultiplier
 * scaledMax = (baseMax + bonus) × slotMultiplier
 * rolledValue = random(scaledMin, scaledMax)
 * </pre>
 *
 * @see ArmorImplicit
 * @see ImplicitDefenseConfig
 */
public final class ImplicitDefenseCalculator {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final ImplicitDefenseConfig config;

    /**
     * Creates an ImplicitDefenseCalculator with the given configuration.
     *
     * @param balanceConfig The gear balance configuration
     */
    public ImplicitDefenseCalculator(@Nonnull GearBalanceConfig balanceConfig) {
        Objects.requireNonNull(balanceConfig, "balanceConfig cannot be null");
        this.config = balanceConfig.implicitDefense();
    }

    /**
     * Checks if implicit defense generation is enabled.
     *
     * @return true if implicits should be generated for armor
     */
    public boolean isEnabled() {
        return config.enabled();
    }

    /**
     * Returns whether a given equipment type should have an armor implicit.
     *
     * <p>Derived from generation logic: only armor pieces and shields receive
     * armor implicits. Weapons (including spellbooks, which are off-hand)
     * never get armor implicits.
     *
     * @param equipmentType The equipment type to check
     * @return true if this type receives an armor implicit during generation
     */
    public static boolean shouldHaveArmorImplicit(@Nullable EquipmentType equipmentType) {
        if (equipmentType == null) return false;
        return equipmentType.isArmor() || equipmentType == EquipmentType.SHIELD;
    }

    /**
     * Calculates and rolls an implicit defense stat for an armor piece.
     *
     * @param material The armor material (determines defense type)
     * @param slot The armor slot (determines slot multiplier)
     * @param itemLevel The armor's item level (affects scaling)
     * @param random The random source for rolling within the range
     * @return A new ArmorImplicit with calculated range and rolled value, or null if material not configured
     */
    @Nullable
    public ArmorImplicit calculate(
            @Nonnull ArmorMaterial material,
            @Nonnull ArmorSlot slot,
            int itemLevel,
            @Nonnull Random random
    ) {
        Objects.requireNonNull(material, "material cannot be null");
        Objects.requireNonNull(slot, "slot cannot be null");
        Objects.requireNonNull(random, "random cannot be null");

        DefenseBaseRange range = config.calculateRange(material, slot, itemLevel);
        if (range == null) {
            LOGGER.atFine().log("No defense config for material %s, skipping implicit", material);
            return null;
        }

        ArmorImplicit implicit = ArmorImplicit.roll(
                range.stat(),
                range.min(),
                range.max(),
                random
        );

        LOGGER.atFine().log(
                "Calculated defense implicit for %s %s at level %d: %s (range: %.1f-%.1f)",
                material, slot, itemLevel, implicit, range.min(), range.max()
        );

        return implicit;
    }

    /**
     * Calculates and rolls an implicit defense stat for a shield.
     *
     * @param itemLevel The shield's item level (affects scaling)
     * @param random The random source for rolling within the range
     * @return A new ArmorImplicit with block_chance
     */
    @Nonnull
    public ArmorImplicit calculateShield(int itemLevel, @Nonnull Random random) {
        Objects.requireNonNull(random, "random cannot be null");

        DefenseBaseRange range = config.calculateShieldRange(itemLevel);

        ArmorImplicit implicit = ArmorImplicit.roll(
                range.stat(),
                range.min(),
                range.max(),
                random
        );

        LOGGER.atFine().log(
                "Calculated shield implicit at level %d: %s (range: %.1f-%.1f)",
                itemLevel, implicit, range.min(), range.max()
        );

        return implicit;
    }

    /**
     * Rerolls an existing implicit's value within its stored range.
     *
     * <p>Used by the calibration stone to reroll the defense value
     * without changing the range or defense type.
     *
     * @param existing The existing implicit to reroll
     * @param random The random source for the new roll
     * @return A new ArmorImplicit with the same range but a new rolled value
     */
    @Nonnull
    public ArmorImplicit reroll(@Nonnull ArmorImplicit existing, @Nonnull Random random) {
        Objects.requireNonNull(existing, "existing cannot be null");
        Objects.requireNonNull(random, "random cannot be null");

        ArmorImplicit rerolled = existing.withRerolledValue(random);

        LOGGER.atFine().log(
                "Rerolled defense implicit: %.1f → %.1f (range: %.1f-%.1f)",
                existing.rolledValue(), rerolled.rolledValue(),
                existing.minValue(), existing.maxValue()
        );

        return rerolled;
    }

    /**
     * Rescales an existing armor implicit to a new level, preserving the roll percentile.
     *
     * <p>When a Threshold Stone changes an item's level, the implicit range shifts
     * but the quality of the original roll is preserved. A 90th-percentile chestplate
     * stays at the 90th percentile of the new level's range.
     *
     * <p>This operation is fully deterministic — no randomness involved.
     *
     * @param existing The current implicit to rescale
     * @param material The armor material (determines defense type and base range)
     * @param slot The armor slot (determines slot multiplier)
     * @param newLevel The new item level
     * @return A new ArmorImplicit at the new level's range with the same percentile, or null if material not configured
     */
    @Nullable
    public ArmorImplicit rescaleForLevel(
            @Nonnull ArmorImplicit existing,
            @Nonnull ArmorMaterial material,
            @Nonnull ArmorSlot slot,
            int newLevel
    ) {
        Objects.requireNonNull(existing, "existing cannot be null");
        Objects.requireNonNull(material, "material cannot be null");
        Objects.requireNonNull(slot, "slot cannot be null");

        DefenseBaseRange newRange = config.calculateRange(material, slot, newLevel);
        if (newRange == null) {
            LOGGER.atFine().log("No defense config for material %s, cannot rescale", material);
            return null;
        }

        ArmorImplicit rescaled = existing.withPreservedPercentile(newRange.min(), newRange.max());

        LOGGER.atFine().log(
                "Rescaled defense implicit for %s %s from level ? to %d: %.1f (%.0f%%) → %.1f (%.0f%%), range: %.1f-%.1f",
                material, slot, newLevel,
                existing.rolledValue(), existing.rollPercentile() * 100,
                rescaled.rolledValue(), rescaled.rollPercentile() * 100,
                newRange.min(), newRange.max()
        );

        return rescaled;
    }

    /**
     * Checks if an equipment type should have a defense implicit.
     *
     * <p>Only armor pieces and shields get defense implicits.
     * Weapons do not.
     *
     * @param equipmentType The equipment type to check
     * @return true if this equipment should have a defense implicit
     */
    public boolean shouldHaveImplicit(@Nonnull EquipmentType equipmentType) {
        Objects.requireNonNull(equipmentType, "equipmentType cannot be null");
        return equipmentType.isArmor() || equipmentType.isOffhand();
    }
}
