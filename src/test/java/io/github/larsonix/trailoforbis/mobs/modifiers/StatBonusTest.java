package io.github.larsonix.trailoforbis.mobs.modifiers;

import io.github.larsonix.trailoforbis.elemental.ElementType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link StatBonus} value object and its factory methods.
 *
 * <p>Critical for production: if StatBonus math is wrong, modifier effects
 * are miscalculated. A Hardened mob with wrong armor bonus is either too
 * easy or unkillable. Elemental damage bonuses feed into the combat pipeline.
 */
@DisplayName("StatBonus")
class StatBonusTest {

    @Nested
    @DisplayName("Factory Methods")
    class FactoryMethods {

        @Test
        @DisplayName("armor() creates bonus with only armor percent")
        void armor_onlyArmor() {
            StatBonus bonus = StatBonus.armor(0.50);
            assertEquals(0.50, bonus.armorPercent(), 0.001);
            assertEquals(0, bonus.maxHpPercent());
            assertEquals(0, bonus.damagePercent());
            assertTrue(bonus.hasEffect());
        }

        @Test
        @DisplayName("elementDamage() creates bonus with element map + ailment chance")
        void elementDamage_hasElementAndAilment() {
            StatBonus bonus = StatBonus.elementDamage(ElementType.FIRE, 0.30, 0.20);
            assertEquals(0.30, bonus.getElementDamage(ElementType.FIRE), 0.001);
            assertEquals(0.0, bonus.getElementDamage(ElementType.WATER));
            assertEquals(0.20, bonus.ailmentChanceBonus(), 0.001);
            assertTrue(bonus.hasEffect());
        }

        @Test
        @DisplayName("EMPTY has no effect")
        void empty_noEffect() {
            assertFalse(StatBonus.EMPTY.hasEffect());
            assertEquals(0, StatBonus.EMPTY.armorPercent());
            assertEquals(0, StatBonus.EMPTY.getElementDamage(ElementType.FIRE));
        }
    }

    @Nested
    @DisplayName("Immutability")
    class Immutability {

        @Test
        @DisplayName("Element damage map is unmodifiable")
        void elementDamageMap_unmodifiable() {
            StatBonus bonus = StatBonus.elementDamage(ElementType.FIRE, 0.30, 0.20);
            assertThrows(UnsupportedOperationException.class,
                () -> bonus.elementDamagePercent().put(ElementType.WATER, 0.5));
        }
    }

    @Nested
    @DisplayName("Modifier Enum StatBonus Values")
    class ModifierEnumValues {

        @Test
        @DisplayName("Hardened has 50% armor bonus")
        void hardened_50armor() {
            assertEquals(0.50, ModifierType.HARDENED.getStatBonus().armorPercent(), 0.001);
        }

        @Test
        @DisplayName("Vigorous has 50% HP bonus")
        void vigorous_50hp() {
            assertEquals(0.50, ModifierType.VIGOROUS.getStatBonus().maxHpPercent(), 0.001);
        }

        @Test
        @DisplayName("Fierce has 35% damage bonus")
        void fierce_35damage() {
            assertEquals(0.35, ModifierType.FIERCE.getStatBonus().damagePercent(), 0.001);
        }

        @Test
        @DisplayName("Blazing has 30% fire damage + 20% ailment chance")
        void blazing_30fire_20ailment() {
            StatBonus bonus = ModifierType.BLAZING.getStatBonus();
            assertEquals(0.30, bonus.getElementDamage(ElementType.FIRE), 0.001);
            assertEquals(0.20, bonus.ailmentChanceBonus(), 0.001);
        }

        @Test
        @DisplayName("Warding has 50% elemental resistance")
        void warding_50resist() {
            assertEquals(0.50, ModifierType.WARDING.getStatBonus().elementalResistPercent(), 0.001);
        }

        @Test
        @DisplayName("Evasive has 25% dodge chance")
        void evasive_25dodge() {
            assertEquals(0.25, ModifierType.EVASIVE.getStatBonus().evasionChance(), 0.001);
        }

        @Test
        @DisplayName("Reflective has 15% reflect")
        void reflective_15reflect() {
            assertEquals(0.15, ModifierType.REFLECTIVE.getStatBonus().reflectPercent(), 0.001);
        }

        @Test
        @DisplayName("Resolute has 100% knockback resist")
        void resolute_100kb() {
            assertEquals(1.0, ModifierType.RESOLUTE.getStatBonus().knockbackResist(), 0.001);
        }
    }
}
