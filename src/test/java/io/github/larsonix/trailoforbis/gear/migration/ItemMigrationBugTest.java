package io.github.larsonix.trailoforbis.gear.migration;

import io.github.larsonix.trailoforbis.gear.config.EquipmentStatConfig;
import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig;
import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig.ImplicitDamageConfig;
import io.github.larsonix.trailoforbis.gear.config.ModifierConfig;
import io.github.larsonix.trailoforbis.gear.config.TestConfigFactory;
import io.github.larsonix.trailoforbis.gear.generation.ImplicitDamageCalculator;
import io.github.larsonix.trailoforbis.gear.generation.ImplicitDefenseCalculator;
import io.github.larsonix.trailoforbis.gear.generation.ModifierPool;
import io.github.larsonix.trailoforbis.gear.migration.GearValidator.ImplicitStatus;
import io.github.larsonix.trailoforbis.gear.migration.GearValidator.ModifierStatus;
import io.github.larsonix.trailoforbis.gear.migration.GearValidator.ValidationResult;
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
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests proving critical bugs in the item migration system.
 *
 * <p>Each test demonstrates a specific failure mode that prevents items
 * from being properly migrated when players update the plugin.
 */
class ItemMigrationBugTest {

    private ModifierConfig modifierConfig;
    private GearBalanceConfig balanceConfig;
    private EquipmentStatConfig equipmentStatConfig;
    private GearValidator validator;
    private GearFixer fixer;

    @BeforeEach
    void setUp() {
        modifierConfig = TestConfigFactory.createDefaultModifierConfig();
        balanceConfig = TestConfigFactory.createDefaultBalanceConfig();
        equipmentStatConfig = EquipmentStatConfig.unrestricted();
        validator = new GearValidator(modifierConfig, balanceConfig, equipmentStatConfig);

        ModifierPool modifierPool = new ModifierPool(
                modifierConfig, balanceConfig, equipmentStatConfig, new Random(42));
        ImplicitDamageCalculator implicitDamageCalc = new ImplicitDamageCalculator(balanceConfig);
        ImplicitDefenseCalculator implicitDefenseCalc = new ImplicitDefenseCalculator(balanceConfig);

        fixer = new GearFixer(modifierConfig, balanceConfig, modifierPool,
                implicitDamageCalc, implicitDefenseCalc, validator);
    }

    @Nested
    @DisplayName("Bug #1: Legacy items without instanceId")
    class LegacyInstanceIdBug {

        @Test
        @DisplayName("GearFixer.fix() does NOT assign instanceId to legacy items — proves setGearData would throw")
        void fixerDoesNotAssignInstanceId() {
            // Simulate a legacy item: has no instanceId, has a REMOVED modifier
            GearModifier removedMod = GearModifier.of(
                    "ancient_power", "Ancient Power", ModifierType.PREFIX,
                    "magic", "flat", 10.0);

            GearData legacyGear = GearData.builder()
                    .level(10)
                    .rarity(GearRarity.RARE)
                    .quality(50)
                    // NO instanceId set — this is a legacy item
                    .addPrefix(removedMod)
                    .build();

            // Verify: legacy item has no instanceId
            assertNull(legacyGear.instanceId(), "Precondition: legacy gear has no instanceId");

            // Validate: the removed modifier IS detected
            ValidationResult result = validator.validate(legacyGear, EquipmentType.SWORD, WeaponType.SWORD, "weapon");
            assertTrue(result.needsMigration(), "Precondition: item needs migration");
            assertEquals(ModifierStatus.REMOVED_FROM_GAME, result.prefixStatuses().get(0));

            // Fix: GearFixer processes the item
            GearData fixedGear = fixer.fix(legacyGear, result, EquipmentType.SWORD, WeaponType.SWORD, "weapon");

            // BUG PROOF: Fixed gear STILL has no instanceId
            // GearUtils.setGearData() requires hasInstanceId() == true
            // This means migrateItem() would throw IllegalArgumentException here
            assertNull(fixedGear.instanceId(),
                    "BUG CONFIRMED: GearFixer does not assign instanceId to legacy items. " +
                    "GearUtils.setGearData() will throw IllegalArgumentException, " +
                    "and the item will be silently skipped forever.");

            // The modifier WAS replaced (fix logic works)
            assertFalse(fixedGear.prefixes().isEmpty(), "Fixer should have rolled a replacement modifier");
            assertNotEquals("ancient_power", fixedGear.prefixes().get(0).id(),
                    "Replacement should not be the removed modifier");
        }

        @Test
        @DisplayName("Legacy item with valid data also has no instanceId — stampVersion path works but fix path fails")
        void legacyItemNoInstanceIdButValid() {
            // A legacy item with all-valid modifiers
            GearModifier validMod = GearModifier.of(
                    "sharp", "Sharp", ModifierType.PREFIX, "physical_damage", "flat", 10.0);

            GearData legacyGear = GearData.builder()
                    .level(10)
                    .rarity(GearRarity.RARE)
                    .quality(50)
                    .addPrefix(validMod)
                    .build();

            assertNull(legacyGear.instanceId());

            // This item passes validation — stampVersion would be used (which works fine)
            ValidationResult result = validator.validate(legacyGear, EquipmentType.SWORD, WeaponType.SWORD, "weapon");
            assertFalse(result.needsMigration(),
                    "Valid legacy items take the stampVersion path (no instanceId required) — these work");
        }
    }

    @Nested
    @DisplayName("Implicit correctness now handled by GearGenerator (not validator)")
    class ImplicitViaGenerator {

        @Test
        @DisplayName("Validator no longer checks implicits — always returns VALID")
        void validatorAlwaysReturnsValidForImplicits() {
            // Implicit correctness is checked by ItemMigrationService using GearGenerator
            // as the source of truth. The validator only handles modifiers.
            WeaponImplicit anyImplicit = WeaponImplicit.of("physical_damage", 0, 99999, 99999);

            GearData gear = GearData.builder()
                    .level(1)
                    .rarity(GearRarity.RARE)
                    .quality(50)
                    .implicit(anyImplicit)
                    .build();

            ValidationResult result = validator.validate(gear, EquipmentType.SWORD, WeaponType.SWORD, "weapon");
            assertEquals(ImplicitStatus.VALID, result.weaponImplicitStatus(),
                    "Validator delegates implicit checks to GearGenerator via ItemMigrationService");
        }
    }

    @Nested
    @DisplayName("Gap #3: Modifier count overflow on rarity limit decrease")
    class ModifierCountOverflow {

        @Test
        @DisplayName("Item with more modifiers than rarity allows would crash GearData constructor")
        void modifierCountExceedsNewRarityLimit() {
            // COMMON rarity allows maxModifiers=1 in test config
            // If a previously-RARE item (3 mods) gets its rarity field somehow corrupted to COMMON,
            // or if we decrease RARE's max modifiers, the GearData constructor would throw.

            // Demonstrate: GearData constructor rejects modifier count > rarity max
            GearModifier mod1 = GearModifier.of("sharp", "Sharp", ModifierType.PREFIX, "physical_damage", "flat", 10.0);
            GearModifier mod2 = GearModifier.of("sturdy", "Sturdy", ModifierType.PREFIX, "armor", "flat", 5.0);

            // COMMON allows maxModifiers=1, but we try to create with 2 modifiers
            assertThrows(IllegalArgumentException.class, () -> {
                GearData.builder()
                        .level(10)
                        .rarity(GearRarity.COMMON)
                        .quality(50)
                        .addPrefix(mod1)
                        .addPrefix(mod2)
                        .build();
            }, "GearData constructor rejects modifier count exceeding rarity max. " +
               "If rarity limits decrease, GearFixer.fix() would crash here when " +
               "building the fixed GearData with preserved valid modifiers.");
        }
    }

    @Nested
    @DisplayName("Locked modifier protection")
    class LockedModifierProtection {

        @Test
        @DisplayName("Locked modifiers are never modified even if invalid")
        void lockedModifiersPreserved() {
            // A locked modifier that has been removed from the game
            GearModifier lockedRemoved = new GearModifier(
                    "ancient_power", "Ancient Power", ModifierType.PREFIX,
                    "magic", "flat", 10.0, true);

            GearData gear = GearData.builder()
                    .level(10)
                    .rarity(GearRarity.RARE)
                    .quality(50)
                    .addPrefix(lockedRemoved)
                    .build();

            ValidationResult result = validator.validate(gear, EquipmentType.SWORD, WeaponType.SWORD, "weapon");
            // Validator DOES flag it as removed
            assertEquals(ModifierStatus.REMOVED_FROM_GAME, result.prefixStatuses().get(0));
            assertTrue(result.needsMigration());

            // But fixer respects the lock
            GearData fixed = fixer.fix(gear, result, EquipmentType.SWORD, WeaponType.SWORD, "weapon");
            assertEquals(1, fixed.prefixes().size());
            assertEquals("ancient_power", fixed.prefixes().get(0).id(),
                    "Locked modifier must be preserved even if invalid");
            assertTrue(fixed.prefixes().get(0).locked());
        }
    }
}
