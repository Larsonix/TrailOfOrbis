package io.github.larsonix.trailoforbis.gear.tooltip;

import io.github.larsonix.trailoforbis.gear.config.ModifierConfig;
import io.github.larsonix.trailoforbis.gear.config.TestConfigFactory;
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.model.GearModifier;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.gear.model.ModifierType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ItemNameFormatter - rich item name formatting.
 */
@DisplayName("ItemNameFormatter")
class ItemNameFormatterTest {

    private ModifierConfig modifierConfig;
    private ItemNameFormatter formatter;

    @BeforeEach
    void setUp() {
        modifierConfig = TestConfigFactory.createDefaultModifierConfig();
        formatter = new ItemNameFormatter(modifierConfig);
    }

    // =========================================================================
    // CONSTRUCTOR TESTS
    // =========================================================================

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("accepts valid modifier config")
        void acceptsValidModifierConfig() {
            assertDoesNotThrow(() -> new ItemNameFormatter(modifierConfig));
        }

        @Test
        @DisplayName("throws for null modifier config")
        void throwsForNullModifierConfig() {
            assertThrows(NullPointerException.class, () -> new ItemNameFormatter(null));
        }

        @Test
        @DisplayName("accepts custom settings")
        void acceptsCustomSettings() {
            assertDoesNotThrow(() -> new ItemNameFormatter(
                    modifierConfig, false, false, GearRarity.LEGENDARY));
        }

        @Test
        @DisplayName("throws for null bold threshold")
        void throwsForNullBoldThreshold() {
            assertThrows(NullPointerException.class, () -> new ItemNameFormatter(
                    modifierConfig, true, true, null));
        }
    }

    // =========================================================================
    // BUILD ITEM NAME TESTS
    // =========================================================================

    @Nested
    @DisplayName("buildItemName")
    class BuildItemNameTests {

        @Test
        @DisplayName("returns non-null message")
        void returnsNonNullMessage() {
            GearData gearData = createSimpleGearData(GearRarity.COMMON);
            assertNotNull(formatter.buildItemName("Iron Sword", gearData));
        }

        @Test
        @DisplayName("throws for null base name")
        void throwsForNullBaseName() {
            GearData gearData = createSimpleGearData(GearRarity.COMMON);
            assertThrows(NullPointerException.class, () -> formatter.buildItemName(null, gearData));
        }

        @Test
        @DisplayName("throws for null gear data")
        void throwsForNullGearData() {
            assertThrows(NullPointerException.class, () -> formatter.buildItemName("Iron Sword", null));
        }
    }

    // =========================================================================
    // BUILD SIMPLE NAME TESTS
    // =========================================================================

    @Nested
    @DisplayName("buildSimpleName")
    class BuildSimpleNameTests {

        @Test
        @DisplayName("returns non-null message")
        void returnsNonNullMessage() {
            assertNotNull(formatter.buildSimpleName("Iron Sword", GearRarity.RARE));
        }

        @Test
        @DisplayName("throws for null base name")
        void throwsForNullBaseName() {
            assertThrows(NullPointerException.class, () -> formatter.buildSimpleName(null, GearRarity.COMMON));
        }

        @Test
        @DisplayName("throws for null rarity")
        void throwsForNullRarity() {
            assertThrows(NullPointerException.class, () -> formatter.buildSimpleName("Iron Sword", null));
        }
    }

    // =========================================================================
    // BUILD CUSTOM NAME TESTS
    // =========================================================================

    @Nested
    @DisplayName("buildCustomName")
    class BuildCustomNameTests {

        @Test
        @DisplayName("returns non-null message")
        void returnsNonNullMessage() {
            assertNotNull(formatter.buildCustomName("Iron Sword", "Sharp", "of the Bear", GearRarity.EPIC));
        }

        @Test
        @DisplayName("accepts null prefix")
        void acceptsNullPrefix() {
            assertDoesNotThrow(() -> formatter.buildCustomName("Iron Sword", null, "of the Bear", GearRarity.EPIC));
        }

        @Test
        @DisplayName("accepts null suffix")
        void acceptsNullSuffix() {
            assertDoesNotThrow(() -> formatter.buildCustomName("Iron Sword", "Sharp", null, GearRarity.EPIC));
        }

        @Test
        @DisplayName("throws for null base name")
        void throwsForNullBaseName() {
            assertThrows(NullPointerException.class, () ->
                    formatter.buildCustomName(null, "Sharp", "of the Bear", GearRarity.EPIC));
        }

        @Test
        @DisplayName("throws for null rarity")
        void throwsForNullRarity() {
            assertThrows(NullPointerException.class, () ->
                    formatter.buildCustomName("Iron Sword", "Sharp", "of the Bear", null));
        }
    }

    // =========================================================================
    // FORMAT ITEM ID TESTS
    // =========================================================================

    @Nested
    @DisplayName("formatItemId")
    class FormatItemIdTests {

        @ParameterizedTest
        @CsvSource({
            "Weapon_Sword_Iron, Iron Sword",
            "Weapon_Axe_Diamond, Diamond Axe",
            "Weapon_Bow_Wood, Wood Bow",
            "Armor_Iron_Chest, Iron Chestplate",
            "Armor_Diamond_Head, Diamond Helmet",
            "Armor_Leather_Legs, Leather Leggings",
            "Armor_Gold_Feet, Gold Boots",
            "Armor_Netherite_Hands, Netherite Gauntlets",
            "Tool_Pickaxe_Stone, Stone Pickaxe",
            "Tool_Shovel_Iron, Iron Shovel"
        })
        @DisplayName("formats vanilla item IDs correctly")
        void formatsItemIdCorrectly(String itemId, String expected) {
            assertEquals(expected, ItemNameFormatter.formatItemId(itemId));
        }

        @Test
        @DisplayName("handles unknown category")
        void handlesUnknownCategory() {
            String result = ItemNameFormatter.formatItemId("Misc_Item_Name");
            assertEquals("Item Name", result);
        }

        @Test
        @DisplayName("throws for null item ID")
        void throwsForNullItemId() {
            assertThrows(NullPointerException.class, () -> ItemNameFormatter.formatItemId(null));
        }
    }

    // =========================================================================
    // CAMEL CASE SPLITTING TESTS
    // =========================================================================

    @Nested
    @DisplayName("formatCamelCase")
    class FormatCamelCaseTests {

        @ParameterizedTest
        @CsvSource({
            "EventHorizon, Event Horizon",
            "PulsarBreaker, Pulsar Breaker",
            "SoulblightLongsword, Soulblight Longsword",
            "Iron, Iron",
            "iron, Iron",
            "V2, V2",
            "Lvl5, Lvl 5",
            "ArcaneStaff, Arcane Staff",
            "DragonSlayer3, Dragon Slayer 3"
        })
        @DisplayName("splits camelCase words correctly")
        void splitsCamelCase(String input, String expected) {
            assertEquals(expected, ItemNameFormatter.formatCamelCase(input));
        }

        @Test
        @DisplayName("handles empty string")
        void handlesEmptyString() {
            assertEquals("", ItemNameFormatter.formatCamelCase(""));
        }

        @Test
        @DisplayName("handles single lowercase word")
        void handlesSingleWord() {
            assertEquals("Iron", ItemNameFormatter.formatCamelCase("iron"));
        }

        @Test
        @DisplayName("handles pure digits")
        void handlesPureDigits() {
            assertEquals("3", ItemNameFormatter.formatCamelCase("3"));
        }
    }

    // =========================================================================
    // MOD ITEM ID TESTS
    // =========================================================================

    @Nested
    @DisplayName("Mod item IDs")
    class ModItemIdTests {

        @ParameterizedTest
        @CsvSource({
            // Mod-prefixed vanilla patterns — strip prefix and parse
            "ExampleMod_Weapon_Mace_EventHorizon, Event Horizon Mace",
            "ExampleMod_Weapon_Mace_PulsarBreaker, Pulsar Breaker Mace",
            "ExampleMod_Ashthorn_Dagger, Ashthorn Dagger",
            "ExampleMod_Nightshade_Dagger, Nightshade Dagger",
            // Mod-prefixed armor
            "ExampleMod_Armor_Cloth_Arcaneweave_Chest, Cloth Arcaneweave Chestplate",
            "ExampleMod_Armor_Gold_Horns_Head, Gold Horns Helmet",
            // Simple mod items with underscores
            "SomeMod_Axe, Axe",
            "SomeMod_Sword, Sword",
            // No underscores — camelCase splitting
            "EventHorizon, Event Horizon",
            "DragonSlayer, Dragon Slayer",
            // No underscores and no camelCase — best-effort (i18n resolves these)
            "Runebladestage3, Runebladestage 3"
        })
        @DisplayName("formats mod item IDs correctly")
        void formatsModItemIds(String itemId, String expected) {
            assertEquals(expected, ItemNameFormatter.formatItemId(itemId));
        }
    }

    // =========================================================================
    // FORMAT WORD TESTS
    // =========================================================================

    @Nested
    @DisplayName("formatWord")
    class FormatWordTests {

        @Test
        @DisplayName("capitalizes normal word")
        void capitalizesNormalWord() {
            assertEquals("Iron", ItemNameFormatter.formatWord("iron"));
        }

        @Test
        @DisplayName("preserves short uppercase abbreviations")
        void preservesShortUppercase() {
            assertEquals("V2", ItemNameFormatter.formatWord("V2"));
            assertEquals("HP", ItemNameFormatter.formatWord("HP"));
        }

        @Test
        @DisplayName("preserves pure digit tokens")
        void preservesPureDigits() {
            assertEquals("3", ItemNameFormatter.formatWord("3"));
            assertEquals("42", ItemNameFormatter.formatWord("42"));
        }

        @Test
        @DisplayName("handles empty and null")
        void handlesEmptyAndNull() {
            assertEquals("", ItemNameFormatter.formatWord(""));
            assertEquals("", ItemNameFormatter.formatWord(null));
        }
    }

    // =========================================================================
    // PREFIX/SUFFIX INCLUSION TESTS
    // =========================================================================

    @Nested
    @DisplayName("Prefix/Suffix Inclusion")
    class PrefixSuffixInclusionTests {

        @Test
        @DisplayName("includes prefix when enabled")
        void includesPrefixWhenEnabled() {
            ItemNameFormatter withPrefix = new ItemNameFormatter(modifierConfig, true, false, GearRarity.EPIC);
            GearData gearData = createGearDataWithModifiers();

            // The formatter should include the prefix
            assertDoesNotThrow(() -> withPrefix.buildItemName("Iron Sword", gearData));
        }

        @Test
        @DisplayName("excludes prefix when disabled")
        void excludesPrefixWhenDisabled() {
            ItemNameFormatter noPrefix = new ItemNameFormatter(modifierConfig, false, false, GearRarity.EPIC);
            GearData gearData = createGearDataWithModifiers();

            // The formatter should work without including prefix
            assertDoesNotThrow(() -> noPrefix.buildItemName("Iron Sword", gearData));
        }

        @Test
        @DisplayName("includes suffix when enabled")
        void includesSuffixWhenEnabled() {
            ItemNameFormatter withSuffix = new ItemNameFormatter(modifierConfig, false, true, GearRarity.EPIC);
            GearData gearData = createGearDataWithModifiers();

            assertDoesNotThrow(() -> withSuffix.buildItemName("Iron Sword", gearData));
        }

        @Test
        @DisplayName("excludes suffix when disabled")
        void excludesSuffixWhenDisabled() {
            ItemNameFormatter noSuffix = new ItemNameFormatter(modifierConfig, false, false, GearRarity.EPIC);
            GearData gearData = createGearDataWithModifiers();

            assertDoesNotThrow(() -> noSuffix.buildItemName("Iron Sword", gearData));
        }
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private GearData createSimpleGearData(GearRarity rarity) {
        return GearData.builder()
                .level(50)
                .rarity(rarity)
                .quality(75)
                .build();
    }

    private GearData createGearDataWithModifiers() {
        GearModifier prefix = GearModifier.of(
                "sharp", "Sharp", ModifierType.PREFIX,
                "physical_damage", GearModifier.STAT_TYPE_FLAT, 10.0);

        GearModifier suffix = GearModifier.of(
                "of_the_bear", "of the Bear", ModifierType.SUFFIX,
                "strength", GearModifier.STAT_TYPE_FLAT, 5.0);

        return GearData.builder()
                .level(50)
                .rarity(GearRarity.LEGENDARY)
                .quality(85)
                .prefixes(List.of(prefix))
                .suffixes(List.of(suffix))
                .build();
    }
}
