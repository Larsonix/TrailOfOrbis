package io.github.larsonix.trailoforbis.maps.tooltip;

import io.github.larsonix.trailoforbis.maps.core.RealmMapData;
import io.github.larsonix.trailoforbis.maps.modifiers.RealmModifier;
import io.github.larsonix.trailoforbis.maps.modifiers.RealmModifierType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RealmMapTooltipBuilder")
class RealmMapTooltipBuilderTest {

    private final RealmMapTooltipBuilder builder = new RealmMapTooltipBuilder();

    @Test
    @DisplayName("buildPlainText IIQ summary shows base + compass when present")
    void buildPlainTextIiqSummaryWithCompass() {
        RealmMapData map = RealmMapData.builder()
            .quality(50)
            .fortunesCompassBonus(10)
            .identified(true)
            .addSuffix(RealmModifier.of(RealmModifierType.ITEM_QUANTITY, 20))
            .build();

        String tooltip = builder.buildPlainText(map);

        // quality 50 → multiplier 1.0, so raw 20 → adjusted 20
        assertTrue(tooltip.contains("Item Quantity: +20% + 10%"), "Expected IIQ with compass: " + tooltip);
    }

    @Test
    @DisplayName("buildPlainText IIQ summary hides compass part when not applied")
    void buildPlainTextIiqSummaryWithoutCompass() {
        RealmMapData map = RealmMapData.builder()
            .quality(50)
            .identified(true)
            .addSuffix(RealmModifier.of(RealmModifierType.ITEM_QUANTITY, 20))
            .build();

        String tooltip = builder.buildPlainText(map);

        assertTrue(tooltip.contains("Item Quantity: +20%"), "Expected IIQ without compass: " + tooltip);
        assertFalse(tooltip.contains("+ "), "Should not have compass part: " + tooltip);
    }

    @Test
    @DisplayName("buildPlainText IIQ summary shows ? for unidentified maps")
    void buildPlainTextIiqSummaryUnidentified() {
        RealmMapData map = RealmMapData.builder()
            .quality(50)
            .identified(false)
            .fortunesCompassBonus(10)
            .addSuffix(RealmModifier.of(RealmModifierType.ITEM_QUANTITY, 20))
            .build();

        String tooltip = builder.buildPlainText(map);

        assertTrue(tooltip.contains("Item Quantity: ? + 10%"), "Expected ? with compass: " + tooltip);
    }

    @Test
    @DisplayName("buildPlainText should show quality-adjusted modifier values")
    void buildPlainTextShowsQualityAdjustedValues() {
        // Quality 100 → multiplier 1.5, so raw 40 → adjusted 60
        RealmMapData map = RealmMapData.builder()
            .rarity(io.github.larsonix.trailoforbis.gear.model.GearRarity.RARE)
            .quality(100)
            .identified(true)
            .addPrefix(RealmModifier.of(RealmModifierType.MONSTER_DAMAGE, 40))
            .addSuffix(RealmModifier.of(RealmModifierType.ITEM_RARITY, 20))
            .build();

        String tooltip = builder.buildPlainText(map);

        // 40 * 1.5 = 60, 20 * 1.5 = 30
        assertTrue(tooltip.contains("+60% Monster Damage"), "Expected quality-adjusted prefix: " + tooltip);
        assertTrue(tooltip.contains("+30% Item Rarity"), "Expected quality-adjusted suffix: " + tooltip);
    }

    @Test
    @DisplayName("buildPlainText should show dashed separator between prefixes and suffixes")
    void buildPlainTextShowsSeparator() {
        RealmMapData map = RealmMapData.builder()
            .rarity(io.github.larsonix.trailoforbis.gear.model.GearRarity.RARE)
            .quality(50)
            .identified(true)
            .addPrefix(RealmModifier.of(RealmModifierType.MONSTER_DAMAGE, 40))
            .addSuffix(RealmModifier.of(RealmModifierType.ITEM_QUANTITY, 20))
            .build();

        String tooltip = builder.buildPlainText(map);

        assertTrue(tooltip.contains("--------"), "Expected dashed separator: " + tooltip);
    }

    @Test
    @DisplayName("buildPlainText should not show Difficulty/Rewards headers")
    void buildPlainTextNoSectionHeaders() {
        RealmMapData map = RealmMapData.builder()
            .rarity(io.github.larsonix.trailoforbis.gear.model.GearRarity.RARE)
            .quality(50)
            .identified(true)
            .addPrefix(RealmModifier.of(RealmModifierType.MONSTER_DAMAGE, 40))
            .addSuffix(RealmModifier.of(RealmModifierType.ITEM_QUANTITY, 20))
            .build();

        String tooltip = builder.buildPlainText(map);

        assertFalse(tooltip.contains("Difficulty"), "Should not have Difficulty header");
        assertFalse(tooltip.contains("Rewards"), "Should not have Rewards header");
    }

    @Test
    @DisplayName("buildPlainText should show [Locked] indicator on locked modifiers")
    void buildPlainTextShowsLockedIndicator() {
        RealmMapData map = RealmMapData.builder()
            .quality(50)
            .identified(true)
            .addPrefix(new RealmModifier(RealmModifierType.MONSTER_HEALTH, 50, true))
            .build();

        String tooltip = builder.buildPlainText(map);

        assertTrue(tooltip.contains("[Locked]"), "Expected [Locked] indicator: " + tooltip);
    }

    @Test
    @DisplayName("buildPlainText should show binary modifier without value")
    void buildPlainTextShowsBinaryModifier() {
        RealmMapData map = RealmMapData.builder()
            .quality(50)
            .identified(true)
            .addPrefix(RealmModifier.of(RealmModifierType.NO_REGENERATION, 1))
            .build();

        String tooltip = builder.buildPlainText(map);

        assertTrue(tooltip.contains("No Regeneration"), "Expected binary modifier name: " + tooltip);
        assertFalse(tooltip.contains("+1%"), "Binary modifier should not show +1%: " + tooltip);
    }

    @Test
    @DisplayName("buildPlainText should show (Unidentified) for unidentified maps")
    void buildPlainTextShowsUnidentified() {
        RealmMapData map = RealmMapData.builder()
            .quality(50)
            .identified(false)
            .addPrefix(RealmModifier.of(RealmModifierType.MONSTER_DAMAGE, 40))
            .build();

        String tooltip = builder.buildPlainText(map);

        assertTrue(tooltip.contains("(Unidentified)"));
        // Modifiers should not appear when unidentified
        assertFalse(tooltip.contains("Monster Damage"));
    }

    @Test
    @DisplayName("buildPlainText with low quality should reduce modifier values")
    void buildPlainTextLowQualityReducesValues() {
        // Quality 1 → multiplier 0.51, so raw 100 → adjusted 51
        RealmMapData map = RealmMapData.builder()
            .quality(1)
            .identified(true)
            .addPrefix(RealmModifier.of(RealmModifierType.MONSTER_DAMAGE, 100))
            .build();

        String tooltip = builder.buildPlainText(map);

        assertTrue(tooltip.contains("+51% Monster Damage"), "Expected quality-reduced value: " + tooltip);
    }
}
