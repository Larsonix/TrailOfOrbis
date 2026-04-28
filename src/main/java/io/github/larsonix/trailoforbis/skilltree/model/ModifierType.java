package io.github.larsonix.trailoforbis.skilltree.model;

/**
 * PoE-style modifier types with different calculation behaviors.
 *
 * <p>Calculation order: Final = (Base + Flat) × (1 + Sum(Percent)/100) × Product(1 + Multiplier/100)
 *
 * <p>Additional modifiers for elemental mechanics:
 * <ul>
 *   <li>PENETRATION: Reduces enemy resistance (applied at damage calculation time)</li>
 *   <li>CONVERSION: Converts percentage of source damage to target element</li>
 *   <li>STATUS_CHANCE: Chance to apply elemental status effect</li>
 *   <li>STATUS_DURATION: Duration multiplier for status effects</li>
 * </ul>
 */
public enum ModifierType {
    /**
     * Added to base value before percentages.
     * Example: +10 to maximum health
     */
    FLAT,

    /**
     * Additive percentage increase (stacks additively with other PERCENT modifiers).
     * Example: 5% increased physical damage
     */
    PERCENT,

    /**
     * Multiplicative modifier (applied after FLAT and PERCENT).
     * Example: 20% more critical damage
     * Each MULTIPLIER is applied separately: base × 1.2 × 1.15 × ...
     */
    MULTIPLIER,

    /**
     * Penetration - ignores enemy resistance during damage calculation.
     * Example: Fire hits ignore 15% Fire Resistance
     * Applied at damage calculation time, not in stat combination.
     */
    PENETRATION,

    /**
     * Conversion - converts percentage of source damage to target element.
     * Example: 50% of Physical Damage converted to Fire
     * Must be handled during damage calculation.
     */
    CONVERSION,

    /**
     * Status effect chance - chance to apply elemental status.
     * Example: 25% chance to Freeze
     */
    STATUS_CHANCE,

    /**
     * Status effect duration - multiplier for status duration.
     * Example: 50% increased Freeze duration
     */
    STATUS_DURATION
}
