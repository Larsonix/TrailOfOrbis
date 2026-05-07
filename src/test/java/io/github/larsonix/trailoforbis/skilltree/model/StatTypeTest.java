package io.github.larsonix.trailoforbis.skilltree.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link StatType} display name fields and {@link StatModifier} formatting.
 *
 * <p>Ensures all stat types have valid display names and that the formatting
 * pipeline produces clean, readable output.
 */
@DisplayName("StatType Display Names")
class StatTypeTest {

    @Nested
    @DisplayName("Enum field validation")
    class EnumFieldValidation {

        @Test
        @DisplayName("Every StatType has a non-empty shortName")
        void allShortNamesNonEmpty() {
            for (StatType stat : StatType.values()) {
                assertNotNull(stat.getShortName(),
                    stat.name() + " has null shortName");
                assertFalse(stat.getShortName().isEmpty(),
                    stat.name() + " has empty shortName");
            }
        }

        @Test
        @DisplayName("Every StatType has a non-empty displayName")
        void allDisplayNamesNonEmpty() {
            for (StatType stat : StatType.values()) {
                assertNotNull(stat.getDisplayName(),
                    stat.name() + " has null displayName");
                assertFalse(stat.getDisplayName().isEmpty(),
                    stat.name() + " has empty displayName");
            }
        }

        @Test
        @DisplayName("No shortName contains 'percent' (case-insensitive)")
        void noShortNameContainsPercent() {
            for (StatType stat : StatType.values()) {
                assertFalse(stat.getShortName().toLowerCase().contains("percent"),
                    stat.name() + " shortName contains 'percent': " + stat.getShortName());
            }
        }

        @Test
        @DisplayName("No displayName contains 'Percent' (case-insensitive)")
        void noDisplayNameContainsPercent() {
            for (StatType stat : StatType.values()) {
                assertFalse(stat.getDisplayName().toLowerCase().contains("percent"),
                    stat.name() + " displayName contains 'percent': " + stat.getDisplayName());
            }
        }
    }

    @Nested
    @DisplayName("Specific stat name mappings")
    class SpecificMappings {

        @Test
        @DisplayName("MAX_HEALTH_PERCENT short name is 'Max Health'")
        void maxHealthPercentShortName() {
            assertEquals("Max Health", StatType.MAX_HEALTH_PERCENT.getShortName());
        }

        @Test
        @DisplayName("MAX_HEALTH_PERCENT display name is 'Max Health'")
        void maxHealthPercentDisplayName() {
            assertEquals("Max Health", StatType.MAX_HEALTH_PERCENT.getDisplayName());
        }

        @Test
        @DisplayName("PROJECTILE_SPEED_PERCENT short name is 'Projectile Speed'")
        void projectileSpeedPercentShortName() {
            assertEquals("Projectile Speed", StatType.PROJECTILE_SPEED_PERCENT.getShortName());
        }

        @Test
        @DisplayName("Flat and percent variants share the same display name")
        void flatAndPercentShareDisplayName() {
            assertEquals(StatType.MAX_HEALTH.getDisplayName(),
                StatType.MAX_HEALTH_PERCENT.getDisplayName());
            assertEquals(StatType.PHYSICAL_DAMAGE.getDisplayName(),
                StatType.PHYSICAL_DAMAGE_PERCENT.getDisplayName());
            assertEquals(StatType.FIRE_DAMAGE.getDisplayName(),
                StatType.FIRE_DAMAGE_PERCENT.getDisplayName());
        }

        @Test
        @DisplayName("Flat and percent variants share the same short name")
        void flatAndPercentShareShortName() {
            assertEquals(StatType.MAX_HEALTH.getShortName(),
                StatType.MAX_HEALTH_PERCENT.getShortName());
            assertEquals(StatType.ARMOR.getShortName(),
                StatType.ARMOR_PERCENT.getShortName());
        }

        @Test
        @DisplayName("Critical Multiplier is properly named (not 'Crit Damage')")
        void criticalMultiplierNaming() {
            assertEquals("Crit Multiplier", StatType.CRITICAL_MULTIPLIER.getShortName());
            assertEquals("Critical Multiplier", StatType.CRITICAL_MULTIPLIER.getDisplayName());
        }

        @Test
        @DisplayName("Energy Shield uses full name, not 'ES'")
        void energyShieldNaming() {
            assertEquals("Energy Shield", StatType.ENERGY_SHIELD.getShortName());
            assertEquals("Energy Shield", StatType.ENERGY_SHIELD.getDisplayName());
        }

        @Test
        @DisplayName("Lightning stats use full 'Lightning' not 'Light'")
        void lightningFullName() {
            assertTrue(StatType.LIGHTNING_DAMAGE.getShortName().startsWith("Lightning"));
            assertTrue(StatType.LIGHTNING_RESISTANCE.getShortName().startsWith("Lightning"));
        }
    }

    @Nested
    @DisplayName("getDisplayNameFor (string lookup)")
    class DisplayNameForLookup {

        @Test
        @DisplayName("Resolves known enum names to display names")
        void resolvesKnownNames() {
            assertEquals("Max Health", StatType.getDisplayNameFor("MAX_HEALTH_PERCENT"));
            assertEquals("Physical Damage", StatType.getDisplayNameFor("PHYSICAL_DAMAGE"));
            assertEquals("Critical Chance", StatType.getDisplayNameFor("CRITICAL_CHANCE"));
        }

        @Test
        @DisplayName("Falls back to title case for unknown names")
        void fallsBackForUnknownNames() {
            assertEquals("Some Future Stat", StatType.getDisplayNameFor("SOME_FUTURE_STAT"));
        }

        @Test
        @DisplayName("Strips PERCENT suffix in fallback")
        void stripsPercentInFallback() {
            assertEquals("Unknown Stat", StatType.getDisplayNameFor("UNKNOWN_STAT_PERCENT"));
        }

        @Test
        @DisplayName("Strips MULTIPLIER suffix in fallback")
        void stripsMultiplierInFallback() {
            assertEquals("Unknown Stat", StatType.getDisplayNameFor("UNKNOWN_STAT_MULTIPLIER"));
        }
    }

    @Nested
    @DisplayName("StatModifier.toShortString() formatting")
    class ToShortStringFormatting {

        @Test
        @DisplayName("MAX_HEALTH_PERCENT PERCENT shows '+5% Max Health'")
        void maxHealthPercentPercent() {
            var mod = new StatModifier(StatType.MAX_HEALTH_PERCENT, 5, ModifierType.PERCENT);
            assertEquals("+5% Max Health", mod.toShortString());
        }

        @Test
        @DisplayName("PROJECTILE_SPEED_PERCENT PERCENT shows '+3% Projectile Speed'")
        void projectileSpeedPercentPercent() {
            var mod = new StatModifier(StatType.PROJECTILE_SPEED_PERCENT, 3, ModifierType.PERCENT);
            assertEquals("+3% Projectile Speed", mod.toShortString());
        }

        @Test
        @DisplayName("PHYSICAL_DAMAGE FLAT shows '+10 Physical Damage'")
        void physicalDamageFlat() {
            var mod = new StatModifier(StatType.PHYSICAL_DAMAGE, 10, ModifierType.FLAT);
            assertEquals("+10 Physical Damage", mod.toShortString());
        }

        @Test
        @DisplayName("PHYSICAL_DAMAGE_PERCENT PERCENT shows '+5% Physical Damage'")
        void physicalDamagePercent() {
            var mod = new StatModifier(StatType.PHYSICAL_DAMAGE_PERCENT, 5, ModifierType.PERCENT);
            assertEquals("+5% Physical Damage", mod.toShortString());
        }

        @Test
        @DisplayName("FIRE_DAMAGE_MULTIPLIER MULTIPLIER shows '50% more Fire Damage'")
        void fireDamageMultiplier() {
            var mod = new StatModifier(StatType.FIRE_DAMAGE_MULTIPLIER, 50, ModifierType.MULTIPLIER);
            assertEquals("50% more Fire Damage", mod.toShortString());
        }

        @Test
        @DisplayName("CRITICAL_CHANCE FLAT shows '+5% Crit Chance'")
        void criticalChanceFlat() {
            var mod = new StatModifier(StatType.CRITICAL_CHANCE, 5, ModifierType.FLAT);
            assertEquals("+5% Crit Chance", mod.toShortString());
        }

        @Test
        @DisplayName("Negative values omit the + prefix")
        void negativeValues() {
            var mod = new StatModifier(StatType.DAMAGE_TAKEN_PERCENT, -10, ModifierType.FLAT);
            assertEquals("-10 Damage Taken", mod.toShortString());
        }

        @Test
        @DisplayName("Decimal values show one decimal place")
        void decimalValues() {
            var mod = new StatModifier(StatType.CRITICAL_MULTIPLIER, 1.5f, ModifierType.FLAT);
            assertEquals("+1.5% Crit Multiplier", mod.toShortString());
        }
    }

    @Nested
    @DisplayName("StatModifier.toDisplayString() formatting")
    class ToDisplayStringFormatting {

        @Test
        @DisplayName("Uses displayName instead of shortName")
        void usesDisplayName() {
            var mod = new StatModifier(StatType.CRITICAL_MULTIPLIER, 5, ModifierType.FLAT);
            assertEquals("+5% Critical Multiplier", mod.toDisplayString());
        }

        @Test
        @DisplayName("Damage percent shows full name")
        void damagePercentFullName() {
            var mod = new StatModifier(StatType.PHYSICAL_DAMAGE_PERCENT, 10, ModifierType.PERCENT);
            assertEquals("+10% Physical Damage", mod.toDisplayString());
        }

        @Test
        @DisplayName("Energy shield uses full display name")
        void energyShieldDisplayName() {
            var mod = new StatModifier(StatType.ENERGY_SHIELD, 50, ModifierType.FLAT);
            assertEquals("+50 Energy Shield", mod.toDisplayString());
        }
    }
}
