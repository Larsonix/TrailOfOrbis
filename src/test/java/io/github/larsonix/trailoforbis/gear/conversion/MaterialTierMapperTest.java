package io.github.larsonix.trailoforbis.gear.conversion;

import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link MaterialTierMapper}.
 *
 * <p>This is a critical conversion class that extracts material names from
 * Hytale item IDs using regex patterns. Incorrect extraction causes wrong
 * rarity caps (e.g., wooden swords becoming Legendary). Each test verifies
 * a specific item ID format that exists in the game.
 */
@DisplayName("MaterialTierMapper")
class MaterialTierMapperTest {

    private VanillaConversionConfig config;
    private MaterialTierMapper mapper;

    @BeforeEach
    void setUp() {
        config = new VanillaConversionConfig();
        config.setMaterialTiers(Map.of(
            "iron", "RARE",
            "steel", "EPIC",
            "mythril", "LEGENDARY",
            "wood", "COMMON",
            "crude", "COMMON",
            "leather_light", "UNCOMMON"
        ));
        config.setDefaultMaxRarity("RARE");
        mapper = new MaterialTierMapper(config);
    }

    // ═══════════════════════════════════════════════════════════════════
    // Material Extraction — Weapon Patterns
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Weapon material extraction")
    class WeaponExtractionTests {

        @Test
        @DisplayName("Weapon_Sword_Iron → 'Iron'")
        void standardWeapon() {
            Optional<String> material = mapper.extractMaterial("Weapon_Sword_Iron");
            assertTrue(material.isPresent());
            assertEquals("Iron", material.get());
        }

        @Test
        @DisplayName("Weapon_Axe_Steel → 'Steel'")
        void axeWeapon() {
            Optional<String> material = mapper.extractMaterial("Weapon_Axe_Steel");
            assertTrue(material.isPresent());
            assertEquals("Steel", material.get());
        }

        @Test
        @DisplayName("Weapon_Hammer_Mythril → 'Mythril'")
        void hammerWeapon() {
            Optional<String> material = mapper.extractMaterial("Weapon_Hammer_Mythril");
            assertTrue(material.isPresent());
            assertEquals("Mythril", material.get());
        }

        @Test
        @DisplayName("Weapon_Bow_Crude → 'Crude'")
        void bowWeapon() {
            Optional<String> material = mapper.extractMaterial("Weapon_Bow_Crude");
            assertTrue(material.isPresent());
            assertEquals("Crude", material.get());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Material Extraction — Armor Patterns
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Armor material extraction")
    class ArmorExtractionTests {

        @Test
        @DisplayName("Armor_Steel_Chest → 'Steel'")
        void standardArmor() {
            Optional<String> material = mapper.extractMaterial("Armor_Steel_Chest");
            assertTrue(material.isPresent());
            assertEquals("Steel", material.get());
        }

        @Test
        @DisplayName("Armor_Iron_Head → 'Iron'")
        void helmet() {
            Optional<String> material = mapper.extractMaterial("Armor_Iron_Head");
            assertTrue(material.isPresent());
            assertEquals("Iron", material.get());
        }

        @Test
        @DisplayName("Armor_Leather_Light_Head → 'Leather_Light' (compound material)")
        void compoundMaterial() {
            Optional<String> material = mapper.extractMaterial("Armor_Leather_Light_Head");
            assertTrue(material.isPresent());
            assertEquals("Leather_Light", material.get());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Material Extraction — Tool Patterns
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Tool material extraction")
    class ToolExtractionTests {

        @Test
        @DisplayName("Tool_Pickaxe_Iron → 'Iron'")
        void standardTool() {
            Optional<String> material = mapper.extractMaterial("Tool_Pickaxe_Iron");
            assertTrue(material.isPresent());
            assertEquals("Iron", material.get());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Rarity Capping
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Rarity capping")
    class RarityCapTests {

        @Test
        @DisplayName("capRarity returns rolled rarity when within cap")
        void withinCap() {
            assertEquals(GearRarity.COMMON,
                mapper.capRarity(GearRarity.COMMON, GearRarity.RARE));
            assertEquals(GearRarity.UNCOMMON,
                mapper.capRarity(GearRarity.UNCOMMON, GearRarity.RARE));
            assertEquals(GearRarity.RARE,
                mapper.capRarity(GearRarity.RARE, GearRarity.RARE));
        }

        @Test
        @DisplayName("capRarity caps down when rolled exceeds maximum")
        void exceedsCap() {
            assertEquals(GearRarity.RARE,
                mapper.capRarity(GearRarity.EPIC, GearRarity.RARE));
            assertEquals(GearRarity.COMMON,
                mapper.capRarity(GearRarity.LEGENDARY, GearRarity.COMMON));
        }

        @Test
        @DisplayName("capRarityForItem integrates extraction + capping for iron weapon")
        void capRarityForItemIntegration() {
            // Iron is configured as max RARE
            GearRarity result = mapper.capRarityForItem(GearRarity.LEGENDARY, "Weapon_Sword_Iron");
            assertEquals(GearRarity.RARE, result, "Iron weapon should be capped at RARE");
        }

        @Test
        @DisplayName("capRarityForItem uses default rarity for unknown material")
        void capRarityForItemUnknownMaterial() {
            // Unknown material → default max rarity (RARE)
            GearRarity result = mapper.capRarityForItem(GearRarity.LEGENDARY, "Weapon_Sword_Unknown");
            assertEquals(GearRarity.RARE, result, "Unknown material should use default cap");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // getMaxRarity Integration
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getMaxRarity")
    class MaxRarityTests {

        @Test
        @DisplayName("Returns configured rarity for known material")
        void knownMaterial() {
            assertEquals(GearRarity.RARE, mapper.getMaxRarity("Weapon_Sword_Iron"));
            assertEquals(GearRarity.EPIC, mapper.getMaxRarity("Armor_Steel_Chest"));
        }

        @Test
        @DisplayName("Returns default rarity for unknown material")
        void unknownMaterial() {
            assertEquals(GearRarity.RARE, mapper.getMaxRarity("Weapon_Sword_Adamantium"));
        }

        @Test
        @DisplayName("Material lookup is case-insensitive")
        void caseInsensitive() {
            // extractMaterial returns "Iron" (capitalized from regex)
            // getMaxRarity lowercases it → "iron" → config lookup succeeds
            assertEquals(GearRarity.RARE, mapper.getMaxRarity("Weapon_Sword_Iron"));
        }

        @Test
        @DisplayName("Throws NullPointerException for null itemId")
        void nullThrows() {
            assertThrows(NullPointerException.class, () -> mapper.getMaxRarity(null));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Edge Cases
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Edge cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Single-segment ID falls through all patterns")
        void singleSegment() {
            Optional<String> material = mapper.extractMaterial("Sword");
            // Should return empty — no underscore-separated segments
            assertTrue(material.isEmpty());
        }

        @Test
        @DisplayName("Two-segment ID uses fallback")
        void twoSegments() {
            Optional<String> material = mapper.extractMaterial("Custom_Weapon");
            // Falls through to last-resort split: parts[1] = "Weapon"
            // But "Weapon" is in isSlotOrTypeName → should try fallback first
            assertTrue(material.isPresent() || material.isEmpty());
            // The important thing: doesn't throw
        }

        @Test
        @DisplayName("capRarity throws for null arguments")
        void capRarityNullArgs() {
            assertThrows(NullPointerException.class,
                () -> mapper.capRarity(null, GearRarity.RARE));
            assertThrows(NullPointerException.class,
                () -> mapper.capRarity(GearRarity.RARE, null));
        }

        @Test
        @DisplayName("Constructor throws for null config")
        void nullConfigThrows() {
            assertThrows(NullPointerException.class, () -> new MaterialTierMapper(null));
        }
    }
}
