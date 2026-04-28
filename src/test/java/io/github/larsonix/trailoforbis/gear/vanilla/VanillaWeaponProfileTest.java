package io.github.larsonix.trailoforbis.gear.vanilla;

import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig.FamilyAttackProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for VanillaWeaponProfile with config-driven family attack profiles.
 *
 * <p>The key invariant: all weapons in the same family get identical multipliers
 * regardless of their individual vanilla damage values. This is achieved by mapping
 * sorted non-backstab attacks to the family's config-defined multiplier curve via
 * linear interpolation.
 */
class VanillaWeaponProfileTest {

    /** Standard 6-point test profile matching FamilyAttackProfile.DEFAULT */
    private static final FamilyAttackProfile DEFAULT_PROFILE =
        new FamilyAttackProfile(List.of(0.5, 0.65, 0.8, 1.0, 1.5, 2.0), 3.0);

    /** Daggers profile: 8-point curve + 3.5× backstab */
    private static final FamilyAttackProfile DAGGERS_PROFILE =
        new FamilyAttackProfile(List.of(0.3, 0.4, 0.5, 0.6, 0.8, 1.2, 1.8, 2.2), 3.5);

    // =========================================================================
    // PROFILE CREATION TESTS
    // =========================================================================

    @Nested
    @DisplayName("Profile Creation")
    class ProfileCreationTests {

        @Test
        @DisplayName("Config multipliers are assigned to sorted normal attacks")
        void create_AssignsConfigMultipliers() {
            List<VanillaAttackInfo> attacks = List.of(
                VanillaAttackInfo.normal("Heavy", 20f),
                VanillaAttackInfo.normal("Basic", 5f),
                VanillaAttackInfo.normal("Signature", 40f)
            );

            // 3 attacks mapped to 6-point config → interpolated
            FamilyAttackProfile profile3 = new FamilyAttackProfile(
                List.of(0.5, 1.0, 2.0), 3.0);

            VanillaWeaponProfile wp = VanillaWeaponProfile.create(
                "Weapon_Test", "Test", attacks, profile3);

            // Sorted by damage: 5f → config[0]=0.5, 20f → config[1]=1.0, 40f → config[2]=2.0
            assertEquals(0.5f, wp.getAttackTypeMultiplier(5f), 0.001f);
            assertEquals(1.0f, wp.getAttackTypeMultiplier(20f), 0.001f);
            assertEquals(2.0f, wp.getAttackTypeMultiplier(40f), 0.001f);
        }

        @Test
        @DisplayName("Empty attack list returns safe defaults")
        void create_EmptyAttackList_SafeDefaults() {
            VanillaWeaponProfile profile = VanillaWeaponProfile.create(
                "Weapon_Empty", "Empty", List.of(), DEFAULT_PROFILE);

            assertEquals(0f, profile.minDamage());
            assertEquals(0f, profile.maxDamage());
            assertEquals(1f, profile.referenceDamage());
            assertTrue(profile.damageToEffectiveness().isEmpty());
            assertFalse(profile.hasAttacks());
        }

        @Test
        @DisplayName("Single attack gets midpoint of config curve")
        void create_SingleAttack_GetsMidpoint() {
            List<VanillaAttackInfo> attacks = List.of(
                VanillaAttackInfo.normal("OnlyAttack", 25f)
            );

            // 6-point config [0.5, 0.65, 0.8, 1.0, 1.5, 2.0]
            // Midpoint: pos = (6-1)/2 = 2.5 → between index 2 (0.8) and 3 (1.0)
            // Interpolated: 0.8 * 0.5 + 1.0 * 0.5 = 0.9
            VanillaWeaponProfile profile = VanillaWeaponProfile.create(
                "Weapon_Single", "Single", attacks, DEFAULT_PROFILE);

            assertEquals(0.9f, profile.getAttackTypeMultiplier(25f), 0.001f);
        }

        @Test
        @DisplayName("Reference damage is still geometric mean (for fallback path)")
        void create_StillComputesGeometricMean() {
            List<VanillaAttackInfo> attacks = List.of(
                VanillaAttackInfo.normal("Basic", 4f),
                VanillaAttackInfo.normal("Heavy", 100f)
            );

            VanillaWeaponProfile profile = VanillaWeaponProfile.create(
                "Weapon_Test", "Test", attacks, DEFAULT_PROFILE);

            // √(4 × 100) = 20
            assertEquals(20f, profile.referenceDamage(), 0.01f);
        }
    }

    // =========================================================================
    // BACKSTAB CLASSIFICATION TESTS
    // =========================================================================

    @Nested
    @DisplayName("Backstab Classification")
    class BackstabClassificationTests {

        @Test
        @DisplayName("Backstab attacks get fixed backstabMultiplier")
        void backstab_GetsFixedMultiplier() {
            List<VanillaAttackInfo> attacks = List.of(
                VanillaAttackInfo.normal("Swing", 5f),
                VanillaAttackInfo.backstab("Stab", 59f, 180f, 60f)
            );

            VanillaWeaponProfile profile = VanillaWeaponProfile.create(
                "Weapon_Daggers_Iron", "Daggers", attacks, DAGGERS_PROFILE);

            // Backstab damage 59f → fixed backstab multiplier 3.5
            assertEquals(3.5f, profile.getAttackTypeMultiplier(59f), 0.001f);
        }

        @Test
        @DisplayName("All backstab attacks get the same multiplier")
        void allBackstabs_SameMultiplier() {
            List<VanillaAttackInfo> attacks = List.of(
                VanillaAttackInfo.normal("Basic", 3f),
                VanillaAttackInfo.backstab("Backstab1", 50f, 180f, 60f),
                VanillaAttackInfo.backstab("Backstab2", 70f, 150f, 45f)
            );

            VanillaWeaponProfile profile = VanillaWeaponProfile.create(
                "Weapon_Test", "Test", attacks, DEFAULT_PROFILE);

            assertEquals(3.0f, profile.getAttackTypeMultiplier(50f), 0.001f);
            assertEquals(3.0f, profile.getAttackTypeMultiplier(70f), 0.001f);
        }

        @Test
        @DisplayName("All backstabs: no normal attacks, map only has backstab entries")
        void allBackstabs_NoCrash() {
            List<VanillaAttackInfo> attacks = List.of(
                VanillaAttackInfo.backstab("Back1", 30f, 180f, 60f),
                VanillaAttackInfo.backstab("Back2", 60f, 180f, 60f)
            );

            VanillaWeaponProfile profile = VanillaWeaponProfile.create(
                "Weapon_AllBack", "Test", attacks, DEFAULT_PROFILE);

            assertTrue(profile.hasAttacks());
            assertEquals(3.0f, profile.getAttackTypeMultiplier(30f), 0.001f);
            assertEquals(3.0f, profile.getAttackTypeMultiplier(60f), 0.001f);
        }
    }

    // =========================================================================
    // INTERPOLATION TESTS
    // =========================================================================

    @Nested
    @DisplayName("Interpolation")
    class InterpolationTests {

        @Test
        @DisplayName("Exact match (N=M): each attack maps to one config entry")
        void exactMatch_N_equals_M() {
            List<Double> config = List.of(0.5, 1.0, 2.0);
            List<Double> result = VanillaWeaponProfile.interpolateMultipliers(config, 3);

            assertEquals(3, result.size());
            assertEquals(0.5, result.get(0), 0.001);
            assertEquals(1.0, result.get(1), 0.001);
            assertEquals(2.0, result.get(2), 0.001);
        }

        @Test
        @DisplayName("Upscale (N>M): more attacks than config entries")
        void upscale_N_greater_M() {
            List<Double> config = List.of(0.5, 2.0);
            List<Double> result = VanillaWeaponProfile.interpolateMultipliers(config, 4);

            // 4 targets over 2 config entries: [0.5, 1.0, 1.5, 2.0]
            assertEquals(4, result.size());
            assertEquals(0.5, result.get(0), 0.001);   // endpoint preserved
            assertEquals(1.0, result.get(1), 0.001);   // interpolated
            assertEquals(1.5, result.get(2), 0.001);   // interpolated
            assertEquals(2.0, result.get(3), 0.001);   // endpoint preserved
        }

        @Test
        @DisplayName("Downscale (N<M): fewer attacks than config entries")
        void downscale_N_less_M() {
            List<Double> config = List.of(0.3, 0.5, 0.8, 1.0, 1.5, 2.0);
            List<Double> result = VanillaWeaponProfile.interpolateMultipliers(config, 3);

            // 3 targets over 6 config entries:
            // i=0: t=0.0, pos=0.0 → config[0]=0.3
            // i=1: t=0.5, pos=2.5 → between config[2]=0.8 and config[3]=1.0 → 0.9
            // i=2: t=1.0, pos=5.0 → config[5]=2.0
            assertEquals(3, result.size());
            assertEquals(0.3, result.get(0), 0.001);   // first endpoint
            assertEquals(0.9, result.get(1), 0.001);   // interpolated midpoint
            assertEquals(2.0, result.get(2), 0.001);   // last endpoint
        }

        @Test
        @DisplayName("Single config value broadcast to all targets")
        void singleConfig_BroadcastToAll() {
            List<Double> config = List.of(1.5);
            List<Double> result = VanillaWeaponProfile.interpolateMultipliers(config, 4);

            assertEquals(4, result.size());
            for (double v : result) {
                assertEquals(1.5, v, 0.001);
            }
        }

        @Test
        @DisplayName("Single target gets midpoint of config array")
        void singleTarget_GetsMidpoint() {
            List<Double> config = List.of(0.5, 0.65, 0.8, 1.0, 1.5, 2.0);
            List<Double> result = VanillaWeaponProfile.interpolateMultipliers(config, 1);

            // Midpoint: pos=(6-1)/2=2.5 → between config[2]=0.8 and config[3]=1.0
            // 0.8 * 0.5 + 1.0 * 0.5 = 0.9
            assertEquals(1, result.size());
            assertEquals(0.9, result.getFirst(), 0.001);
        }

        @Test
        @DisplayName("Zero targets returns empty list")
        void zeroTargets_EmptyList() {
            List<Double> config = List.of(0.5, 1.0, 2.0);
            List<Double> result = VanillaWeaponProfile.interpolateMultipliers(config, 0);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Endpoints are always preserved exactly")
        void endpoints_PreservedExactly() {
            List<Double> config = List.of(0.3, 0.7, 1.1, 1.8, 2.5);
            for (int n = 2; n <= 10; n++) {
                List<Double> result = VanillaWeaponProfile.interpolateMultipliers(config, n);
                assertEquals(0.3, result.getFirst(), 0.0001, "First endpoint for N=" + n);
                assertEquals(2.5, result.getLast(), 0.0001, "Last endpoint for N=" + n);
            }
        }
    }

    // =========================================================================
    // FAMILY CONSISTENCY TESTS (CORE REGRESSION)
    // =========================================================================

    @Nested
    @DisplayName("Family Consistency")
    class FamilyConsistencyTests {

        @Test
        @DisplayName("CORE: Two daggers with different vanilla damage get IDENTICAL multipliers")
        void sameFamilyDifferentDamage_IdenticalMultipliers() {
            // Iron Daggers: low damage values
            List<VanillaAttackInfo> ironAttacks = List.of(
                VanillaAttackInfo.normal("Swing_Left", 3f),
                VanillaAttackInfo.normal("Swing_Right", 5f),
                VanillaAttackInfo.normal("Stab", 12f),
                VanillaAttackInfo.backstab("Backstab", 59f, 180f, 60f)
            );

            // Crude Daggers: different damage values but same attack count
            List<VanillaAttackInfo> crudeAttacks = List.of(
                VanillaAttackInfo.normal("Swing_Left", 2f),
                VanillaAttackInfo.normal("Swing_Right", 4f),
                VanillaAttackInfo.normal("Stab", 8f),
                VanillaAttackInfo.backstab("Backstab", 42f, 180f, 60f)
            );

            VanillaWeaponProfile iron = VanillaWeaponProfile.create(
                "Weapon_Daggers_Iron", "Daggers", ironAttacks, DAGGERS_PROFILE);
            VanillaWeaponProfile crude = VanillaWeaponProfile.create(
                "Weapon_Daggers_Crude", "Daggers", crudeAttacks, DAGGERS_PROFILE);

            // Normal attacks: both have 3 normals → same interpolated multipliers
            // (sorted by damage, then mapped to the same 8-point curve)
            Map<Float, Float> ironEffs = iron.damageToEffectiveness();
            Map<Float, Float> crudeEffs = crude.damageToEffectiveness();

            // Get the multiplier VALUES (not the damage keys)
            // Iron normals sorted: 3f, 5f, 12f → indices 0,1,2 in interpolation
            // Crude normals sorted: 2f, 4f, 8f → indices 0,1,2 in interpolation
            // Both get the SAME interpolated values from the SAME config curve
            assertEquals(ironEffs.get(3f), crudeEffs.get(2f), 0.001f,
                "Weakest normal attack multiplier must match across weapons");
            assertEquals(ironEffs.get(5f), crudeEffs.get(4f), 0.001f,
                "Middle normal attack multiplier must match across weapons");
            assertEquals(ironEffs.get(12f), crudeEffs.get(8f), 0.001f,
                "Strongest normal attack multiplier must match across weapons");

            // Backstab multiplier is always the fixed value
            assertEquals(3.5f, ironEffs.get(59f), 0.001f);
            assertEquals(3.5f, crudeEffs.get(42f), 0.001f);
        }

        @Test
        @DisplayName("High-tier and low-tier weapons in same family: identical multiplier values")
        void highTierAndLowTier_IdenticalMultiplierValues() {
            // Iron Sword: 2 normal attacks
            List<VanillaAttackInfo> ironAttacks = List.of(
                VanillaAttackInfo.normal("Swing", 16f),
                VanillaAttackInfo.normal("Heavy", 48f)
            );

            // Obsidian Sword: 2 normal attacks, 10× damage
            List<VanillaAttackInfo> obsidianAttacks = List.of(
                VanillaAttackInfo.normal("Swing", 160f),
                VanillaAttackInfo.normal("Heavy", 480f)
            );

            FamilyAttackProfile swordProfile = new FamilyAttackProfile(
                List.of(0.5, 0.6, 0.8, 1.0, 1.5, 2.0), 3.0);

            VanillaWeaponProfile iron = VanillaWeaponProfile.create(
                "Weapon_Sword_Iron", "Sword", ironAttacks, swordProfile);
            VanillaWeaponProfile obsidian = VanillaWeaponProfile.create(
                "Weapon_Sword_Obsidian", "Sword", obsidianAttacks, swordProfile);

            // Both have 2 normals mapped to same curve → IDENTICAL values
            assertEquals(iron.getAttackTypeMultiplier(16f),
                obsidian.getAttackTypeMultiplier(160f), 0.001f,
                "Low attack: same multiplier regardless of damage magnitude");
            assertEquals(iron.getAttackTypeMultiplier(48f),
                obsidian.getAttackTypeMultiplier(480f), 0.001f,
                "High attack: same multiplier regardless of damage magnitude");
        }
    }

    // =========================================================================
    // EFFECTIVENESS RANGE + UTILITIES
    // =========================================================================

    @Nested
    @DisplayName("Effectiveness Range")
    class EffectivenessRangeTests {

        @Test
        @DisplayName("getEffectivenessRangeString reads actual min/max from effectiveness map")
        void rangeString_ReadsFromMap() {
            List<VanillaAttackInfo> attacks = List.of(
                VanillaAttackInfo.normal("Basic", 3f),
                VanillaAttackInfo.backstab("Backstab", 59f, 180f, 60f)
            );

            VanillaWeaponProfile profile = VanillaWeaponProfile.create(
                "Weapon_Daggers_Iron", "Daggers", attacks, DAGGERS_PROFILE);

            String rangeString = profile.getEffectivenessRangeString();
            assertTrue(rangeString.contains("×"));
            assertTrue(rangeString.contains(" - "));
            // With 1 normal + 1 backstab using daggers profile:
            // Normal: single attack → midpoint of 8-entry curve
            // Backstab: 3.5
            assertTrue(rangeString.contains("3.50×"), "Should contain backstab multiplier");
        }

        @Test
        @DisplayName("hasAttacks() returns correct boolean")
        void hasAttacks_ReturnsCorrectBoolean() {
            VanillaWeaponProfile emptyProfile = VanillaWeaponProfile.create(
                "Empty", "Empty", List.of(), DEFAULT_PROFILE);
            assertFalse(emptyProfile.hasAttacks());

            VanillaWeaponProfile filledProfile = VanillaWeaponProfile.create(
                "Filled", "Filled",
                List.of(VanillaAttackInfo.normal("Attack", 10f)),
                DEFAULT_PROFILE);
            assertTrue(filledProfile.hasAttacks());
        }

        @Test
        @DisplayName("Unknown damage falls back to vanillaDamage / referenceDamage")
        void unknownDamage_FallsBackToCalculation() {
            List<VanillaAttackInfo> attacks = List.of(
                VanillaAttackInfo.normal("Attack", 16f)
            );

            VanillaWeaponProfile profile = VanillaWeaponProfile.create(
                "Weapon_Test", "Test", attacks, DEFAULT_PROFILE);

            // 32f is not in the effectiveness map
            float unknownDamage = 32f;
            assertFalse(profile.damageToEffectiveness().containsKey(unknownDamage));

            // Falls back: 32 / referenceDamage
            assertEquals(32f / profile.referenceDamage(),
                profile.getAttackTypeMultiplier(32f), 0.001f);
        }

        @Test
        @DisplayName("Zero reference damage returns 1.0x safely")
        void zeroReference_ReturnsSafeDefault() {
            VanillaWeaponProfile brokenProfile = new VanillaWeaponProfile(
                "Broken", "Broken", List.of(), 0f, 0f, 0f, Map.of());

            assertEquals(1.0f, brokenProfile.getAttackTypeMultiplier(50f));
        }
    }

    // =========================================================================
    // EDGE CASES
    // =========================================================================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Zero damage attack handled gracefully")
        void zeroDamageAttack_HandledGracefully() {
            List<VanillaAttackInfo> attacks = List.of(
                VanillaAttackInfo.normal("NoDamage", 0f),
                VanillaAttackInfo.normal("FullDamage", 50f)
            );

            VanillaWeaponProfile profile = VanillaWeaponProfile.create(
                "Weapon_EdgeCase", "EdgeCase", attacks, DEFAULT_PROFILE);

            assertTrue(profile.referenceDamage() > 0, "Reference should never be zero");
            assertDoesNotThrow(() -> profile.getAttackTypeMultiplier(0f));
            assertDoesNotThrow(() -> profile.getAttackTypeMultiplier(50f));
        }

        @Test
        @DisplayName("Duplicate damage values: last write wins in map")
        void duplicateDamage_LastWriteWins() {
            // Two attacks with same damage → same map key
            List<VanillaAttackInfo> attacks = List.of(
                VanillaAttackInfo.normal("Swing_Left", 10f),
                VanillaAttackInfo.normal("Swing_Right", 10f)
            );

            VanillaWeaponProfile profile = VanillaWeaponProfile.create(
                "Weapon_Test", "Test", attacks, DEFAULT_PROFILE);

            // Both have 10f damage → only one map entry (last wins)
            assertTrue(profile.damageToEffectiveness().containsKey(10f));
        }

        @Test
        @DisplayName("Bow: all normal attacks, no backstab entries")
        void bow_AllNormalAttacks() {
            FamilyAttackProfile bowProfile = new FamilyAttackProfile(
                List.of(0.4, 0.6, 0.8, 1.0, 1.5, 2.0), 1.0);

            List<VanillaAttackInfo> attacks = List.of(
                VanillaAttackInfo.normal("Quick_Shot", 8f),
                VanillaAttackInfo.normal("Charged_Shot", 24f)
            );

            VanillaWeaponProfile profile = VanillaWeaponProfile.create(
                "Weapon_Bow_Iron", "Bow", attacks, bowProfile);

            // 2 normals, 0 backstabs → endpoints of bow config
            assertEquals(0.4f, profile.getAttackTypeMultiplier(8f), 0.001f);
            assertEquals(2.0f, profile.getAttackTypeMultiplier(24f), 0.001f);
        }
    }
}
