package io.github.larsonix.trailoforbis.gear.generation;

import io.github.larsonix.trailoforbis.elemental.ElementType;
import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig;
import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig.ImplicitDamageConfig;
import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig.WeaponBaseRange;
import io.github.larsonix.trailoforbis.gear.model.WeaponImplicit;
import io.github.larsonix.trailoforbis.gear.model.WeaponType;

import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

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

    /** Damage type for magic weapons (staff, wand). */
    public static final String SPELL_DAMAGE = "spell_damage";

    /** Stat type for spellbook implicit (mana regeneration, not damage). */
    public static final String MANA_REGEN = "mana_regen";

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

        // Spellbooks get mana_regen implicit instead of damage — they're support items
        if (weaponType == WeaponType.SPELLBOOK) {
            return calculateSpellbookImplicit(itemLevel, random);
        }

        // Magic weapons always roll a fixed element at generation.
        // The element is always stored on the item for seamless Hexcode install/uninstall.
        // Display and combat adapt at runtime: with Hexcode → shows "Spell Damage" and
        // element comes from hex spell; without Hexcode → shows "Fire Damage" and
        // staff melee uses the stored element.
        ElementType element = weaponType.isMagic()
                ? ElementType.values()[random.nextInt(ElementType.values().length)]
                : null;

        return calculateWithElement(weaponType, itemLevel, element, random);
    }

    /**
     * Calculates an implicit with a pre-determined element.
     *
     * <p>Used when the element is rolled before modifier generation (for element affinity).
     * Null element = physical damage.
     *
     * @param weaponType The weapon type
     * @param itemLevel Item level for scaling
     * @param element The pre-determined element (null for physical)
     * @param random Random source for value roll
     * @return The calculated implicit
     */
    @Nonnull
    public WeaponImplicit calculateWithElement(
            @Nonnull WeaponType weaponType,
            int itemLevel,
            @Nullable ElementType element,
            @Nonnull Random random
    ) {
        // 1. Get unified scaled range (handles 2H multiplier internally)
        WeaponBaseRange scaledRange = config.calculateRange(weaponType, itemLevel);

        // 2. Determine damage type
        String damageType = element != null
                ? element.getDamageTypeId()
                : PHYSICAL_DAMAGE;

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
     * Returns the set of valid damage type IDs for a given weapon type.
     *
     * <p>Derived directly from the generation logic — this IS the source of truth
     * for what implicit types a weapon can legitimately have. Used by migration
     * validation to detect invalid implicits without maintaining separate rules.
     *
     * @param weaponType The weapon type to check
     * @return Set of valid damage type strings (e.g., {"physical_damage"} or {"fire_damage", "water_damage", ...})
     */
    @Nonnull
    public static Set<String> getValidDamageTypes(@Nonnull WeaponType weaponType) {
        if (weaponType == WeaponType.SPELLBOOK) {
            return Set.of(MANA_REGEN);
        }
        if (weaponType.isMagic()) {
            // Magic weapons can have any element's damage type
            Set<String> types = new HashSet<>();
            for (ElementType element : ElementType.values()) {
                types.add(element.getDamageTypeId());
            }
            // Also accept spell_damage (legacy/Hexcode display mode)
            types.add(SPELL_DAMAGE);
            return Set.copyOf(types);
        }
        // Physical weapons: only physical_damage
        return Set.of(PHYSICAL_DAMAGE);
    }

    /**
     * Returns whether a given weapon type should have a weapon implicit at all.
     *
     * @param weaponType The weapon type
     * @return true if this weapon type gets a weapon implicit during generation
     */
    public static boolean shouldHaveWeaponImplicit(@Nonnull WeaponType weaponType) {
        // All weapons get an implicit (damage or mana_regen for spellbooks)
        // Only thrown consumables (BOMB, DART, KUNAI) are excluded — but they
        // don't reach generation at all (filtered in GearGenerator).
        return weaponType != WeaponType.UNKNOWN;
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
     * Rescales an existing implicit to a new level, preserving the roll percentile.
     *
     * <p>When a Threshold Stone changes an item's level, the implicit range shifts
     * but the quality of the original roll is preserved. A 90th-percentile sword
     * stays at the 90th percentile of the new level's range.
     *
     * <p>This operation is fully deterministic — no randomness involved.
     *
     * @param existing The current implicit to rescale
     * @param weaponType The weapon type (determines 2H multiplier and spellbook branch)
     * @param newLevel The new item level
     * @return A new WeaponImplicit at the new level's range with the same percentile
     */
    @Nonnull
    public WeaponImplicit rescaleForLevel(
            @Nonnull WeaponImplicit existing,
            @Nonnull WeaponType weaponType,
            int newLevel
    ) {
        Objects.requireNonNull(existing, "existing cannot be null");
        Objects.requireNonNull(weaponType, "weaponType cannot be null");

        // Spellbooks use a separate range calculation
        if (weaponType == WeaponType.SPELLBOOK) {
            WeaponBaseRange newRange = config.calculateSpellbookRange(newLevel);
            return existing.withPreservedPercentile(newRange.min(), newRange.max());
        }

        WeaponBaseRange newRange = config.calculateRange(weaponType, newLevel);
        WeaponImplicit rescaled = existing.withPreservedPercentile(newRange.min(), newRange.max());

        LOGGER.atFine().log(
                "Rescaled implicit for %s from level ? to %d: %.1f (%.0f%%) → %.1f (%.0f%%), range: %.1f-%.1f",
                weaponType, newLevel,
                existing.rolledValue(), existing.rollPercentile() * 100,
                rescaled.rolledValue(), rescaled.rollPercentile() * 100,
                newRange.min(), newRange.max()
        );

        return rescaled;
    }

    /**
     * Calculates the spellbook-specific mana_regen implicit.
     *
     * <p>Spellbooks are support/utility weapons — their identity is mana regeneration,
     * not damage. Uses a separate config section with values scaled for mana_regen
     * (much smaller than damage numbers).
     */
    @Nonnull
    private WeaponImplicit calculateSpellbookImplicit(int itemLevel, @Nonnull Random random) {
        WeaponBaseRange range = config.calculateSpellbookRange(itemLevel);

        WeaponImplicit implicit = WeaponImplicit.roll(
                MANA_REGEN,
                range.min(),
                range.max(),
                random
        );

        LOGGER.atFine().log(
                "Calculated spellbook implicit at level %d: %s (range: %.2f-%.2f)",
                itemLevel, implicit, range.min(), range.max()
        );

        return implicit;
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
