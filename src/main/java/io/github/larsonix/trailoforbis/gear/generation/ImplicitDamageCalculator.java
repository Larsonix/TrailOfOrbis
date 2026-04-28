package io.github.larsonix.trailoforbis.gear.generation;

import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig;
import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig.ImplicitDamageConfig;
import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig.WeaponBaseRange;
import io.github.larsonix.trailoforbis.gear.model.WeaponImplicit;
import io.github.larsonix.trailoforbis.gear.model.WeaponType;

import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.Random;

/**
 * Calculates weapon implicit damage based on weapon type and item level.
 *
 * <p>Implicits are guaranteed base damage stats on weapons that:
 * <ul>
 *   <li>Scale with item level using {@link io.github.larsonix.trailoforbis.util.LevelScaling}</li>
 *   <li>Are NOT affected by gear quality</li>
 *   <li>Have a rollable range (displayed as "[153-198] Physical Damage")</li>
 *   <li>Share a unified base range — weapon type affects playstyle, not base damage</li>
 *   <li>Two-handed weapons get a flat multiplier on the unified range</li>
 *   <li>Use "physical_damage" for physical weapons, "spell_damage" for magic</li>
 * </ul>
 *
 * <p>The formula is:
 * <pre>
 * bonus = LevelScaling.getBonusPercent(level) / 100 × scaleFactor
 * scaledMin = baseMin + bonus
 * scaledMax = baseMax + bonus
 * if twoHanded: scaledMin/Max *= twoHandedMultiplier
 * rolledValue = random(scaledMin, scaledMax)
 * </pre>
 *
 * @see WeaponImplicit
 * @see ImplicitDamageConfig
 */
public final class ImplicitDamageCalculator {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Damage type for physical weapons (melee, ranged). */
    public static final String PHYSICAL_DAMAGE = "physical_damage";

    /** Damage type for magic weapons (staff, wand, spellbook). */
    public static final String SPELL_DAMAGE = "spell_damage";

    private final ImplicitDamageConfig config;

    /**
     * Creates an ImplicitDamageCalculator with the given configuration.
     *
     * @param balanceConfig The gear balance configuration
     */
    public ImplicitDamageCalculator(@Nonnull GearBalanceConfig balanceConfig) {
        Objects.requireNonNull(balanceConfig, "balanceConfig cannot be null");
        this.config = balanceConfig.implicitDamage();
    }

    /**
     * Checks if implicit damage generation is enabled.
     *
     * @return true if implicits should be generated for weapons
     */
    public boolean isEnabled() {
        return config.enabled();
    }

    /**
     * Calculates and rolls an implicit damage stat for a weapon.
     *
     * @param weaponType The type of weapon (determines 2H multiplier and damage type)
     * @param itemLevel The weapon's item level (affects scaling)
     * @param random The random source for rolling within the range
     * @return A new WeaponImplicit with calculated range and rolled value
     */
    @Nonnull
    public WeaponImplicit calculate(
            @Nonnull WeaponType weaponType,
            int itemLevel,
            @Nonnull Random random
    ) {
        Objects.requireNonNull(weaponType, "weaponType cannot be null");
        Objects.requireNonNull(random, "random cannot be null");

        // 1. Get unified scaled range (handles 2H multiplier internally)
        WeaponBaseRange scaledRange = config.calculateRange(weaponType, itemLevel);

        // 2. Determine damage type based on weapon category
        String damageType = weaponType.isMagic() ? SPELL_DAMAGE : PHYSICAL_DAMAGE;

        // 3. Roll value within scaled range
        WeaponImplicit implicit = WeaponImplicit.roll(
                damageType,
                scaledRange.min(),
                scaledRange.max(),
                random
        );

        LOGGER.atFine().log(
                "Calculated implicit for %s at level %d: %s (range: %.1f-%.1f, 2H=%s)",
                weaponType, itemLevel, implicit, scaledRange.min(), scaledRange.max(),
                weaponType.isTwoHanded()
        );

        return implicit;
    }

    /**
     * Rerolls an existing implicit's value within its stored range.
     *
     * <p>This is used by the calibration stone to reroll the damage
     * value without changing the range or damage type.
     *
     * @param existing The existing implicit to reroll
     * @param random The random source for the new roll
     * @return A new WeaponImplicit with the same range but a new rolled value
     */
    @Nonnull
    public WeaponImplicit reroll(@Nonnull WeaponImplicit existing, @Nonnull Random random) {
        Objects.requireNonNull(existing, "existing cannot be null");
        Objects.requireNonNull(random, "random cannot be null");

        WeaponImplicit rerolled = existing.withRerolledValue(random);

        LOGGER.atFine().log(
                "Rerolled implicit: %.1f → %.1f (range: %.1f-%.1f)",
                existing.rolledValue(), rerolled.rolledValue(),
                existing.minValue(), existing.maxValue()
        );

        return rerolled;
    }

    /**
     * Checks if a weapon type should have an implicit.
     *
     * <p>Only stat-eligible weapons get implicits. Shields (offhand)
     * and thrown consumables (bomb, dart, kunai) are excluded.
     *
     * @param weaponType The weapon type to check
     * @return true if this weapon type should have an implicit
     */
    public boolean shouldHaveImplicit(@Nonnull WeaponType weaponType) {
        Objects.requireNonNull(weaponType, "weaponType cannot be null");
        return weaponType.isStatEligible();
    }
}
