package io.github.larsonix.trailoforbis.gear.migration;

import io.github.larsonix.trailoforbis.gear.config.EquipmentStatConfig;
import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig;
import io.github.larsonix.trailoforbis.gear.config.ModifierConfig;
import io.github.larsonix.trailoforbis.gear.config.ModifierConfig.ModifierDefinition;
import io.github.larsonix.trailoforbis.gear.config.ModifierConfig.StatType;
import io.github.larsonix.trailoforbis.gear.config.TestConfigFactory;
import io.github.larsonix.trailoforbis.gear.migration.GearValidator.ImplicitStatus;
import io.github.larsonix.trailoforbis.gear.migration.GearValidator.ModifierStatus;
import io.github.larsonix.trailoforbis.gear.migration.GearValidator.ValidationResult;
import io.github.larsonix.trailoforbis.gear.model.ArmorImplicit;
import io.github.larsonix.trailoforbis.gear.model.EquipmentType;
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.model.GearModifier;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.gear.model.ModifierType;
import io.github.larsonix.trailoforbis.gear.model.WeaponImplicit;
import io.github.larsonix.trailoforbis.gear.model.WeaponType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GearValidatorTest {

    private GearValidator validator;
    private ModifierConfig modifierConfig;
    private GearBalanceConfig balanceConfig;

    @BeforeEach
    void setUp() {
        modifierConfig = TestConfigFactory.createDefaultModifierConfig();
        balanceConfig = TestConfigFactory.createDefaultBalanceConfig();
        validator = new GearValidator(modifierConfig, balanceConfig, EquipmentStatConfig.unrestricted());
    }

    @Nested
    @DisplayName("Weapon Implicit Validation")
    class WeaponImplicitTests {

        @Test
        @DisplayName("Physical weapon with physical_damage implicit is valid")
        void physicalWeaponPhysicalImplicit() {
            GearData gear = createGearWithWeaponImplicit("physical_damage");
            ValidationResult result = validator.validate(gear, EquipmentType.SWORD, WeaponType.SWORD, "weapon");
            assertEquals(ImplicitStatus.VALID, result.weaponImplicitStatus());
            assertFalse(result.needsMigration());
        }

        @Test
        @DisplayName("Magic weapon with physical_damage implicit — validator defers to GearGenerator")
        void magicWeaponPhysicalImplicit() {
            // Implicit correctness is now handled by ItemMigrationService via GearGenerator
            GearData gear = createGearWithWeaponImplicit("physical_damage");
            ValidationResult result = validator.validate(gear, EquipmentType.STAFF, WeaponType.STAFF, "weapon");
            assertEquals(ImplicitStatus.VALID, result.weaponImplicitStatus());
        }

        @Test
        @DisplayName("Magic weapon with spell_damage implicit is valid")
        void magicWeaponSpellImplicit() {
            GearData gear = createGearWithWeaponImplicit("spell_damage");
            ValidationResult result = validator.validate(gear, EquipmentType.STAFF, WeaponType.STAFF, "weapon");
            assertEquals(ImplicitStatus.VALID, result.weaponImplicitStatus());
        }

        @Test
        @DisplayName("Magic weapon with element implicit is valid")
        void magicWeaponElementImplicit() {
            GearData gear = createGearWithWeaponImplicit("fire_damage");
            ValidationResult result = validator.validate(gear, EquipmentType.STAFF, WeaponType.STAFF, "weapon");
            assertEquals(ImplicitStatus.VALID, result.weaponImplicitStatus());
        }

        @Test
        @DisplayName("Spellbook with wrong implicit — validator defers to GearGenerator")
        void spellbookWithoutManaRegen() {
            // Implicit correctness is now handled by ItemMigrationService via GearGenerator
            GearData gear = createGearWithWeaponImplicit("spell_damage");
            ValidationResult result = validator.validate(gear, EquipmentType.SPELLBOOK, WeaponType.SPELLBOOK, "weapon");
            assertEquals(ImplicitStatus.VALID, result.weaponImplicitStatus());
        }

        @Test
        @DisplayName("Spellbook with mana_regen implicit is valid")
        void spellbookWithManaRegen() {
            GearData gear = createGearWithWeaponImplicit("mana_regen");
            ValidationResult result = validator.validate(gear, EquipmentType.SPELLBOOK, WeaponType.SPELLBOOK, "weapon");
            assertEquals(ImplicitStatus.VALID, result.weaponImplicitStatus());
        }

        @Test
        @DisplayName("Gear without implicit passes validation")
        void noImplicit() {
            GearData gear = GearData.builder()
                    .level(10).rarity(GearRarity.RARE).quality(50).build();
            ValidationResult result = validator.validate(gear, EquipmentType.SWORD, WeaponType.SWORD, "weapon");
            assertEquals(ImplicitStatus.VALID, result.weaponImplicitStatus());
        }

        @Test
        @DisplayName("Unknown weapon type gets lenient validation")
        void unknownWeaponType() {
            GearData gear = createGearWithWeaponImplicit("physical_damage");
            ValidationResult result = validator.validate(gear, null, null, "weapon");
            assertEquals(ImplicitStatus.VALID, result.weaponImplicitStatus());
        }
    }

    @Nested
    @DisplayName("Modifier Validation")
    class ModifierTests {

        @Test
        @DisplayName("Known modifier passes validation")
        void knownModifierIsValid() {
            GearModifier sharp = GearModifier.of("sharp", "Sharp", ModifierType.PREFIX, "physical_damage", "flat", 10.0);
            GearData gear = GearData.builder()
                    .level(10).rarity(GearRarity.RARE).quality(50)
                    .addPrefix(sharp)
                    .build();

            ValidationResult result = validator.validate(gear, EquipmentType.SWORD, WeaponType.SWORD, "weapon");
            assertEquals(ModifierStatus.VALID, result.prefixStatuses().get(0));
            assertFalse(result.needsMigration());
        }

        @Test
        @DisplayName("Removed modifier is flagged")
        void removedModifier() {
            GearModifier removed = GearModifier.of("ancient_power", "Ancient Power", ModifierType.PREFIX, "magic", "flat", 10.0);
            GearData gear = GearData.builder()
                    .level(10).rarity(GearRarity.RARE).quality(50)
                    .addPrefix(removed)
                    .build();

            ValidationResult result = validator.validate(gear, EquipmentType.SWORD, WeaponType.SWORD, "weapon");
            assertEquals(ModifierStatus.REMOVED_FROM_GAME, result.prefixStatuses().get(0));
            assertTrue(result.needsMigration());
        }

        @Test
        @DisplayName("Modifier on wrong slot is flagged")
        void modifierOnWrongSlot() {
            // "quick" is only allowed on "weapon" slot
            GearModifier quick = GearModifier.of("quick", "Quick", ModifierType.PREFIX, "attack_speed", "percent", 5.0);
            GearData gear = GearData.builder()
                    .level(10).rarity(GearRarity.RARE).quality(50)
                    .addPrefix(quick)
                    .build();

            // Validate as chest armor — "quick" not allowed here
            ValidationResult result = validator.validate(gear, EquipmentType.SWORD, WeaponType.SWORD, "chest");
            assertEquals(ModifierStatus.INVALID_FOR_SLOT, result.prefixStatuses().get(0));
            assertTrue(result.needsMigration());
        }

        @Test
        @DisplayName("Multiple modifiers with mixed validity")
        void mixedValidityModifiers() {
            GearModifier valid = GearModifier.of("sharp", "Sharp", ModifierType.PREFIX, "physical_damage", "flat", 10.0);
            GearModifier removed = GearModifier.of("ancient_power", "Ancient Power", ModifierType.PREFIX, "magic", "flat", 10.0);
            GearData gear = GearData.builder()
                    .level(10).rarity(GearRarity.EPIC).quality(50)
                    .addPrefix(valid)
                    .addPrefix(removed)
                    .build();

            ValidationResult result = validator.validate(gear, EquipmentType.SWORD, WeaponType.SWORD, "weapon");
            assertEquals(ModifierStatus.VALID, result.prefixStatuses().get(0));
            assertEquals(ModifierStatus.REMOVED_FROM_GAME, result.prefixStatuses().get(1));
            assertTrue(result.needsMigration());
        }

        @Test
        @DisplayName("Suffix validation works independently")
        void suffixValidation() {
            GearModifier validSuffix = GearModifier.of("of_might", "of Might", ModifierType.SUFFIX, "strength", "flat", 5.0);
            GearModifier removedSuffix = GearModifier.of("of_chaos", "of Chaos", ModifierType.SUFFIX, "chaos_dmg", "flat", 10.0);
            GearData gear = GearData.builder()
                    .level(10).rarity(GearRarity.RARE).quality(50)
                    .addSuffix(validSuffix)
                    .addSuffix(removedSuffix)
                    .build();

            ValidationResult result = validator.validate(gear, EquipmentType.SWORD, WeaponType.SWORD, "weapon");
            assertEquals(ModifierStatus.VALID, result.suffixStatuses().get(0));
            assertEquals(ModifierStatus.REMOVED_FROM_GAME, result.suffixStatuses().get(1));
        }

        @Test
        @DisplayName("Empty modifier lists pass validation")
        void emptyModifiers() {
            GearData gear = GearData.builder()
                    .level(10).rarity(GearRarity.COMMON).quality(50).build();
            ValidationResult result = validator.validate(gear, EquipmentType.SWORD, WeaponType.SWORD, "weapon");
            assertTrue(result.prefixStatuses().isEmpty());
            assertTrue(result.suffixStatuses().isEmpty());
            assertFalse(result.needsMigration());
        }
    }

    @Nested
    @DisplayName("Value Range Validation")
    class ValueRangeTests {

        @Test
        @DisplayName("Value within range passes validation")
        void valueInRange() {
            // "sharp" has base [5.0, 15.0] + scalePerLevel 0.5.
            // At level 1, range ≈ [5.5, 15.5] before exp scaling.
            // Default balance config has exp scaling DISABLED (multiplier = 1.0)
            // and RARE statMultiplier = 0.8 → extendedMax = 15.5 * 0.8 = 12.4
            // Wait, that would make valid values LESS. Let me use a value that's
            // clearly in range regardless.
            GearModifier mod = GearModifier.of("sharp", "Sharp", ModifierType.PREFIX, "physical_damage", "flat", 8.0);
            GearData gear = GearData.builder()
                    .level(1).rarity(GearRarity.RARE).quality(50)
                    .addPrefix(mod)
                    .build();

            ValidationResult result = validator.validate(gear, EquipmentType.SWORD, WeaponType.SWORD, "weapon");
            assertEquals(ModifierStatus.VALID, result.prefixStatuses().get(0));
        }

        @Test
        @DisplayName("Value far above range is flagged as OUT_OF_RANGE")
        void valueFarAboveRange() {
            // sharp at level 1 can never validly be 1000
            GearModifier mod = GearModifier.of("sharp", "Sharp", ModifierType.PREFIX, "physical_damage", "flat", 1000.0);
            GearData gear = GearData.builder()
                    .level(1).rarity(GearRarity.RARE).quality(50)
                    .addPrefix(mod)
                    .build();

            ValidationResult result = validator.validate(gear, EquipmentType.SWORD, WeaponType.SWORD, "weapon");
            assertEquals(ModifierStatus.VALUES_OUT_OF_RANGE, result.prefixStatuses().get(0));
            assertTrue(result.needsMigration());
        }

        @Test
        @DisplayName("Clamping brings value to max bound")
        void clampToMax() {
            ModifierDefinition sharpDef = modifierConfig.getPrefix("sharp").orElseThrow();
            double clamped = validator.clampToValidRange(1000.0, sharpDef, 1, GearRarity.RARE);
            // Clamped value should be much less than 1000
            assertTrue(clamped < 100, "Clamped value should be within reasonable bounds, got: " + clamped);
            assertTrue(clamped > 0, "Clamped value should be positive");
        }
    }

    @Nested
    @DisplayName("Full Validation Result")
    class FullResultTests {

        @Test
        @DisplayName("Fully valid gear reports no migration needed")
        void fullyValidGear() {
            // Use level 1 where sharp range is ~[5, 15] and 10.0 is clearly in range
            GearModifier prefix = GearModifier.of("sharp", "Sharp", ModifierType.PREFIX, "physical_damage", "flat", 10.0);
            GearModifier suffix = GearModifier.of("of_might", "of Might", ModifierType.SUFFIX, "strength", "flat", 5.0);
            WeaponImplicit implicit = WeaponImplicit.of("physical_damage", 100, 200, 150);

            GearData gear = GearData.builder()
                    .level(1).rarity(GearRarity.RARE).quality(75)
                    .implicit(implicit)
                    .addPrefix(prefix)
                    .addSuffix(suffix)
                    .build();

            ValidationResult result = validator.validate(gear, EquipmentType.SWORD, WeaponType.SWORD, "weapon");
            assertFalse(result.needsMigration());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════

    private GearData createGearWithWeaponImplicit(String damageType) {
        WeaponImplicit implicit = WeaponImplicit.of(damageType, 100.0, 200.0, 150.0);
        return GearData.builder()
                .level(50)
                .rarity(GearRarity.RARE)
                .quality(75)
                .implicit(implicit)
                .build();
    }
}
