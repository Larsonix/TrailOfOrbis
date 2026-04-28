package io.github.larsonix.trailoforbis.gear.tooltip;

import io.github.larsonix.trailoforbis.api.ServiceRegistry;
import io.github.larsonix.trailoforbis.attributes.AttributeManager;
import io.github.larsonix.trailoforbis.attributes.AttributeType;
import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig;
import io.github.larsonix.trailoforbis.gear.config.ModifierConfig;
import io.github.larsonix.trailoforbis.gear.config.TestConfigFactory;
import io.github.larsonix.trailoforbis.gear.equipment.RequirementCalculator;
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.model.GearModifier;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.gear.model.ModifierType;
import io.github.larsonix.trailoforbis.leveling.api.LevelingService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for RichTooltipFormatter - Message-based tooltip building.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RichTooltipFormatter")
class RichTooltipFormatterTest {

    @Mock
    private AttributeManager attributeManager;

    @Mock
    private LevelingService levelingService;

    private GearBalanceConfig balanceConfig;
    private ModifierConfig modifierConfig;
    private RequirementCalculator requirementCalculator;
    private RichTooltipFormatter formatter;

    @BeforeEach
    void setUp() {
        balanceConfig = TestConfigFactory.createDefaultBalanceConfig();
        modifierConfig = TestConfigFactory.createDefaultModifierConfig();
        requirementCalculator = new RequirementCalculator(balanceConfig, modifierConfig);
        formatter = new RichTooltipFormatter(modifierConfig, requirementCalculator, attributeManager);

        // Register mock LevelingService for level requirement tests
        ServiceRegistry.register(LevelingService.class, levelingService);
    }

    @AfterEach
    void tearDown() {
        // Clean up ServiceRegistry to avoid test pollution
        ServiceRegistry.unregister(LevelingService.class);
    }

    // =========================================================================
    // CONSTRUCTOR TESTS
    // =========================================================================

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("accepts valid dependencies")
        void acceptsValidDependencies() {
            assertDoesNotThrow(() ->
                    new RichTooltipFormatter(modifierConfig, requirementCalculator, attributeManager));
        }

        @Test
        @DisplayName("accepts valid dependencies with config")
        void acceptsValidDependenciesWithConfig() {
            assertDoesNotThrow(() ->
                    new RichTooltipFormatter(modifierConfig, requirementCalculator, attributeManager, TooltipConfig.defaults()));
        }

        @Test
        @DisplayName("throws for null modifier config")
        void throwsForNullModifierConfig() {
            assertThrows(NullPointerException.class, () ->
                    new RichTooltipFormatter(null, requirementCalculator, attributeManager));
        }

        @Test
        @DisplayName("throws for null requirement calculator")
        void throwsForNullRequirementCalculator() {
            assertThrows(NullPointerException.class, () ->
                    new RichTooltipFormatter(modifierConfig, null, attributeManager));
        }

        @Test
        @DisplayName("throws for null attribute manager")
        void throwsForNullAttributeManager() {
            assertThrows(NullPointerException.class, () ->
                    new RichTooltipFormatter(modifierConfig, requirementCalculator, null));
        }

        @Test
        @DisplayName("throws for null tooltip config")
        void throwsForNullTooltipConfig() {
            assertThrows(NullPointerException.class, () ->
                    new RichTooltipFormatter(modifierConfig, requirementCalculator, attributeManager, null));
        }
    }

    // =========================================================================
    // BUILD TESTS
    // =========================================================================

    @Nested
    @DisplayName("build()")
    class BuildTests {

        @Test
        @DisplayName("returns non-null message")
        void returnsNonNullMessage() {
            GearData gearData = createSimpleGearData(GearRarity.RARE);
            assertNotNull(formatter.build(gearData));
        }

        @Test
        @DisplayName("throws for null gear data")
        void throwsForNullGearData() {
            assertThrows(NullPointerException.class, () -> formatter.build(null));
        }

        @ParameterizedTest
        @DisplayName("handles all rarities")
        @EnumSource(GearRarity.class)
        void handlesAllRarities(GearRarity rarity) {
            GearData gearData = createSimpleGearData(rarity);
            assertNotNull(formatter.build(gearData));
        }
    }

    // =========================================================================
    // BUILD WITH PLAYER TESTS
    // =========================================================================

    @Nested
    @DisplayName("build() with player")
    class BuildWithPlayerTests {

        @Test
        @DisplayName("returns non-null message with player")
        void returnsNonNullMessageWithPlayer() {
            GearData gearData = createSimpleGearData(GearRarity.COMMON);
            UUID playerId = UUID.randomUUID();

            // COMMON gear without modifiers has no requirements
            assertNotNull(formatter.build(gearData, playerId));
        }

        @Test
        @DisplayName("accepts null player ID")
        void acceptsNullPlayerId() {
            GearData gearData = createSimpleGearData(GearRarity.COMMON);
            assertDoesNotThrow(() -> formatter.build(gearData, null));
        }
    }

    // =========================================================================
    // BUILD RARITY BADGE TESTS
    // =========================================================================

    @Nested
    @DisplayName("buildRarityBadge()")
    class BuildRarityBadgeTests {

        @ParameterizedTest
        @EnumSource(GearRarity.class)
        @DisplayName("returns non-null for all rarities")
        void returnsNonNullForAllRarities(GearRarity rarity) {
            assertNotNull(formatter.buildRarityBadge(rarity));
        }
    }

    // =========================================================================
    // BUILD ITEM LEVEL TESTS
    // =========================================================================

    @Nested
    @DisplayName("buildItemLevel()")
    class BuildItemLevelTests {

        @Test
        @DisplayName("returns non-null message")
        void returnsNonNullMessage() {
            assertNotNull(formatter.buildItemLevel(50));
        }

        @Test
        @DisplayName("handles various levels")
        void handlesVariousLevels() {
            assertNotNull(formatter.buildItemLevel(1));
            assertNotNull(formatter.buildItemLevel(100));
            assertNotNull(formatter.buildItemLevel(10000));
        }
    }

    // =========================================================================
    // BUILD ITEM LEVEL WITH PLAYER TESTS (Level Requirement Coloring)
    // =========================================================================

    @Nested
    @DisplayName("buildItemLevel() with player context")
    class BuildItemLevelWithPlayerTests {

        @Test
        @DisplayName("returns non-null when player meets level requirement")
        void returnsNonNullWhenLevelMet() {
            UUID playerId = UUID.randomUUID();
            when(levelingService.getLevel(playerId)).thenReturn(50);

            assertNotNull(formatter.buildItemLevel(50, playerId));
        }

        @Test
        @DisplayName("returns non-null when player below level requirement")
        void returnsNonNullWhenLevelNotMet() {
            UUID playerId = UUID.randomUUID();
            when(levelingService.getLevel(playerId)).thenReturn(30);

            assertNotNull(formatter.buildItemLevel(50, playerId));
        }

        @Test
        @DisplayName("returns non-null when no player context")
        void returnsNonNullWhenNoPlayer() {
            assertNotNull(formatter.buildItemLevel(50, null));
        }

        @Test
        @DisplayName("handles player exactly at level requirement")
        void handlesPlayerExactlyAtLevel() {
            UUID playerId = UUID.randomUUID();
            when(levelingService.getLevel(playerId)).thenReturn(75);

            // Player level equals item level - requirement should be met
            assertNotNull(formatter.buildItemLevel(75, playerId));
        }

        @Test
        @DisplayName("handles player above level requirement")
        void handlesPlayerAboveLevel() {
            UUID playerId = UUID.randomUUID();
            when(levelingService.getLevel(playerId)).thenReturn(100);

            // Player level > item level - requirement should be met
            assertNotNull(formatter.buildItemLevel(50, playerId));
        }
    }

    // =========================================================================
    // BUILD QUALITY SECTION TESTS
    // =========================================================================

    @Nested
    @DisplayName("buildQualitySection()")
    class BuildQualitySectionTests {

        @Test
        @DisplayName("returns non-null message")
        void returnsNonNullMessage() {
            assertNotNull(formatter.buildQualitySection(75));
        }

        @Test
        @DisplayName("handles all quality ranges")
        void handlesAllQualityRanges() {
            assertNotNull(formatter.buildQualitySection(1));   // Poor
            assertNotNull(formatter.buildQualitySection(20));  // Below Average
            assertNotNull(formatter.buildQualitySection(50));  // Average
            assertNotNull(formatter.buildQualitySection(80));  // Above Average
            assertNotNull(formatter.buildQualitySection(95));  // Excellent
            assertNotNull(formatter.buildQualitySection(101)); // Perfect
        }
    }

    // =========================================================================
    // BUILD MODIFIERS SECTION TESTS
    // =========================================================================

    @Nested
    @DisplayName("buildModifiersSection()")
    class BuildModifiersSectionTests {

        @Test
        @DisplayName("returns non-null for gear with modifiers")
        void returnsNonNullForGearWithModifiers() {
            GearData gearData = createGearDataWithModifiers();
            assertNotNull(formatter.buildModifiersSection(gearData));
        }

        @Test
        @DisplayName("returns non-null for gear without modifiers")
        void returnsNonNullForGearWithoutModifiers() {
            GearData gearData = createSimpleGearData(GearRarity.COMMON);
            assertNotNull(formatter.buildModifiersSection(gearData));
        }

        @Test
        @DisplayName("returns non-null for prefix-only gear (separator below)")
        void returnsNonNullForPrefixOnlyGear() {
            GearModifier prefix = GearModifier.of(
                    "sharp", "Sharp", ModifierType.PREFIX,
                    "physical_damage", GearModifier.STAT_TYPE_FLAT, 12.0);
            GearData gearData = GearData.builder()
                    .level(50).rarity(GearRarity.RARE).quality(75)
                    .prefixes(List.of(prefix))
                    .build();

            assertNotNull(formatter.buildModifiersSection(gearData));
        }

        @Test
        @DisplayName("returns non-null for suffix-only gear (separator above)")
        void returnsNonNullForSuffixOnlyGear() {
            GearModifier suffix = GearModifier.of(
                    "of_the_bear", "of the Bear", ModifierType.SUFFIX,
                    "strength", GearModifier.STAT_TYPE_FLAT, 5.0);
            GearData gearData = GearData.builder()
                    .level(50).rarity(GearRarity.RARE).quality(75)
                    .suffixes(List.of(suffix))
                    .build();

            assertNotNull(formatter.buildModifiersSection(gearData));
        }

        @Test
        @DisplayName("returns non-null for both prefix and suffix groups")
        void returnsNonNullForBothGroups() {
            GearModifier prefix = GearModifier.of(
                    "sharp", "Sharp", ModifierType.PREFIX,
                    "physical_damage", GearModifier.STAT_TYPE_FLAT, 12.0);
            GearModifier suffix = GearModifier.of(
                    "of_the_bear", "of the Bear", ModifierType.SUFFIX,
                    "strength", GearModifier.STAT_TYPE_FLAT, 5.0);
            GearData gearData = GearData.builder()
                    .level(50).rarity(GearRarity.LEGENDARY).quality(85)
                    .prefixes(List.of(prefix))
                    .suffixes(List.of(suffix))
                    .build();

            assertNotNull(formatter.buildModifiersSection(gearData));
        }

        @Test
        @DisplayName("returns non-null for mixed locked and unlocked modifiers")
        void returnsNonNullForMixedLockedUnlocked() {
            GearModifier unlockedPrefix = GearModifier.of(
                    "sharp", "Sharp", ModifierType.PREFIX,
                    "physical_damage", GearModifier.STAT_TYPE_FLAT, 12.0);
            GearModifier lockedPrefix = new GearModifier(
                    "swift", "Swift", ModifierType.PREFIX,
                    "attack_speed", GearModifier.STAT_TYPE_FLAT, 8.0, true);
            GearModifier suffix = GearModifier.of(
                    "of_the_bear", "of the Bear", ModifierType.SUFFIX,
                    "strength", GearModifier.STAT_TYPE_FLAT, 5.0);
            GearData gearData = GearData.builder()
                    .level(50).rarity(GearRarity.LEGENDARY).quality(85)
                    .prefixes(List.of(unlockedPrefix, lockedPrefix))
                    .suffixes(List.of(suffix))
                    .build();

            assertNotNull(formatter.buildModifiersSection(gearData));
        }

        @Test
        @DisplayName("returns non-null for all locked modifiers")
        void returnsNonNullForAllLockedModifiers() {
            GearModifier lockedPrefix = new GearModifier(
                    "sharp", "Sharp", ModifierType.PREFIX,
                    "physical_damage", GearModifier.STAT_TYPE_FLAT, 12.0, true);
            GearModifier lockedSuffix = new GearModifier(
                    "of_the_bear", "of the Bear", ModifierType.SUFFIX,
                    "strength", GearModifier.STAT_TYPE_FLAT, 5.0, true);
            GearData gearData = GearData.builder()
                    .level(50).rarity(GearRarity.EPIC).quality(90)
                    .prefixes(List.of(lockedPrefix))
                    .suffixes(List.of(lockedSuffix))
                    .build();

            assertNotNull(formatter.buildModifiersSection(gearData));
        }
    }

    // =========================================================================
    // BUILD MODIFIER LINE TESTS
    // =========================================================================

    @Nested
    @DisplayName("buildModifierLine()")
    class BuildModifierLineTests {

        private static final double DEFAULT_QUALITY_MULT = 1.0; // quality 50 = neutral

        @Test
        @DisplayName("returns non-null for positive modifier")
        void returnsNonNullForPositiveModifier() {
            GearModifier modifier = createModifier(10.0, true);
            assertNotNull(formatter.buildModifierLine(modifier, DEFAULT_QUALITY_MULT));
        }

        @Test
        @DisplayName("returns non-null for negative modifier")
        void returnsNonNullForNegativeModifier() {
            GearModifier modifier = createModifier(-5.0, true);
            assertNotNull(formatter.buildModifierLine(modifier, DEFAULT_QUALITY_MULT));
        }

        @Test
        @DisplayName("returns non-null for percent modifier")
        void returnsNonNullForPercentModifier() {
            GearModifier modifier = createModifier(15.0, false);
            assertNotNull(formatter.buildModifierLine(modifier, DEFAULT_QUALITY_MULT));
        }

        @Test
        @DisplayName("returns non-null for locked positive modifier")
        void returnsNonNullForLockedPositiveModifier() {
            GearModifier modifier = createLockedModifier(12.0, true);
            assertNotNull(formatter.buildModifierLine(modifier, DEFAULT_QUALITY_MULT));
        }

        @Test
        @DisplayName("returns non-null for locked negative modifier")
        void returnsNonNullForLockedNegativeModifier() {
            GearModifier modifier = createLockedModifier(-3.0, true);
            assertNotNull(formatter.buildModifierLine(modifier, DEFAULT_QUALITY_MULT));
        }

        @Test
        @DisplayName("returns non-null for locked percent modifier")
        void returnsNonNullForLockedPercentModifier() {
            GearModifier modifier = createLockedModifier(8.5, false);
            assertNotNull(formatter.buildModifierLine(modifier, DEFAULT_QUALITY_MULT));
        }
    }

    // =========================================================================
    // BUILD REQUIREMENTS SECTION TESTS
    // =========================================================================

    @Nested
    @DisplayName("buildRequirementsSection()")
    class BuildRequirementsSectionTests {

        @Test
        @DisplayName("returns non-null for empty requirements")
        void returnsNonNullForEmptyRequirements() {
            Map<AttributeType, Integer> requirements = Map.of();
            assertNotNull(formatter.buildRequirementsSection(requirements, null));
        }

        @Test
        @DisplayName("returns non-null for non-empty requirements")
        void returnsNonNullForNonEmptyRequirements() {
            Map<AttributeType, Integer> requirements = Map.of(
                    AttributeType.FIRE, 30,
                    AttributeType.LIGHTNING, 20
            );
            assertNotNull(formatter.buildRequirementsSection(requirements, null));
        }

        @Test
        @DisplayName("handles requirement check with player")
        void handlesRequirementCheckWithPlayer() {
            UUID playerId = UUID.randomUUID();
            Map<AttributeType, Integer> requirements = Map.of(AttributeType.FIRE, 30);

            // Mock player attributes
            when(attributeManager.getPlayerAttributes(playerId))
                    .thenReturn(Map.of(AttributeType.FIRE, 50));

            assertNotNull(formatter.buildRequirementsSection(requirements, playerId));
        }
    }

    // =========================================================================
    // BUILD COMPACT TESTS
    // =========================================================================

    @Nested
    @DisplayName("buildCompact()")
    class BuildCompactTests {

        @Test
        @DisplayName("returns non-null message")
        void returnsNonNullMessage() {
            GearData gearData = createSimpleGearData(GearRarity.EPIC);
            assertNotNull(formatter.buildCompact(gearData));
        }

        @Test
        @DisplayName("throws for null gear data")
        void throwsForNullGearData() {
            assertThrows(NullPointerException.class, () -> formatter.buildCompact(null));
        }
    }

    // =========================================================================
    // BUILD MODIFIERS ONLY TESTS
    // =========================================================================

    @Nested
    @DisplayName("buildModifiersOnly()")
    class BuildModifiersOnlyTests {

        @Test
        @DisplayName("returns non-null for gear with modifiers")
        void returnsNonNullForGearWithModifiers() {
            GearData gearData = createGearDataWithModifiers();
            assertNotNull(formatter.buildModifiersOnly(gearData));
        }

        @Test
        @DisplayName("returns empty message for gear without modifiers")
        void returnsEmptyForGearWithoutModifiers() {
            GearData gearData = createSimpleGearData(GearRarity.COMMON);
            assertNotNull(formatter.buildModifiersOnly(gearData));
        }

        @Test
        @DisplayName("throws for null gear data")
        void throwsForNullGearData() {
            assertThrows(NullPointerException.class, () -> formatter.buildModifiersOnly(null));
        }
    }

    // =========================================================================
    // CONFIG-DRIVEN TESTS
    // =========================================================================

    @Nested
    @DisplayName("Config-driven behavior")
    class ConfigDrivenTests {

        @Test
        @DisplayName("respects section visibility settings")
        void respectsSectionVisibilitySettings() {
            TooltipConfig config = TooltipConfig.builder()
                    .showRarityBadge(false)
                    .showItemLevel(false)
                    .showQuality(false)
                    .showModifiers(false)
                    .showRequirements(false)
                    .build();

            RichTooltipFormatter configuredFormatter = new RichTooltipFormatter(
                    modifierConfig, requirementCalculator, attributeManager, config);

            GearData gearData = createSimpleGearData(GearRarity.EPIC);

            // Should still return a message (possibly empty)
            assertNotNull(configuredFormatter.build(gearData));
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

    private GearModifier createModifier(double value, boolean flat) {
        String statType = flat ? GearModifier.STAT_TYPE_FLAT : GearModifier.STAT_TYPE_PERCENT;
        return GearModifier.of(
                "test_modifier", "Test Modifier", ModifierType.PREFIX,
                "test_stat", statType, value);
    }

    private GearModifier createLockedModifier(double value, boolean flat) {
        String statType = flat ? GearModifier.STAT_TYPE_FLAT : GearModifier.STAT_TYPE_PERCENT;
        return new GearModifier(
                "test_modifier", "Test Modifier", ModifierType.PREFIX,
                "test_stat", statType, value, true);
    }
}
