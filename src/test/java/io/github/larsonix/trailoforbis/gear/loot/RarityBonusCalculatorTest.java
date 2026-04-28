package io.github.larsonix.trailoforbis.gear.loot;

import io.github.larsonix.trailoforbis.attributes.AttributeManager;
import io.github.larsonix.trailoforbis.attributes.AttributeType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for RarityBonusCalculator.
 *
 * <p>WIND is the "fortune/luck" elemental attribute. The formula is:
 * {@code wind * luckToRarityPercent}.
 */
@ExtendWith(MockitoExtension.class)
class RarityBonusCalculatorTest {

    @Mock
    private AttributeManager attributeManager;

    private RarityBonusCalculator createCalculator(double luckToRarityPercent) {
        return new RarityBonusCalculator(attributeManager, luckToRarityPercent);
    }

    private void stubWind(UUID playerId, int windValue) {
        when(attributeManager.getPlayerAttributes(playerId))
                .thenReturn(Map.of(AttributeType.WIND, windValue));
    }

    // =========================================================================
    // BASIC FORMULA TESTS
    // =========================================================================

    @Test
    @DisplayName("WIND=0 → 0% bonus regardless of factor")
    void windZero_zeroBonusRegardlessOfFactor() {
        UUID playerId = UUID.randomUUID();
        stubWind(playerId, 0);

        var calculator = createCalculator(0.5);
        double bonus = calculator.calculatePlayerBonus(playerId);

        assertEquals(0.0, bonus);
    }

    @Test
    @DisplayName("WIND=20, factor=0.5 → 10% bonus")
    void wind20_factorHalf_tenPercentBonus() {
        UUID playerId = UUID.randomUUID();
        stubWind(playerId, 20);

        var calculator = createCalculator(0.5);
        double bonus = calculator.calculatePlayerBonus(playerId);

        assertEquals(10.0, bonus, 1e-9);
    }

    @Test
    @DisplayName("WIND=100, factor=0.5 → 50% bonus")
    void wind100_factorHalf_fiftyPercentBonus() {
        UUID playerId = UUID.randomUUID();
        stubWind(playerId, 100);

        var calculator = createCalculator(0.5);
        double bonus = calculator.calculatePlayerBonus(playerId);

        assertEquals(50.0, bonus, 1e-9);
    }

    @Test
    @DisplayName("WIND=50, factor=1.0 → 50% bonus")
    void wind50_factorOne_fiftyPercentBonus() {
        UUID playerId = UUID.randomUUID();
        stubWind(playerId, 50);

        var calculator = createCalculator(1.0);
        double bonus = calculator.calculatePlayerBonus(playerId);

        assertEquals(50.0, bonus, 1e-9);
    }

    @Test
    @DisplayName("WIND=10, factor=0.25 → 2.5% bonus")
    void wind10_factorQuarter_twoPointFivePercentBonus() {
        UUID playerId = UUID.randomUUID();
        stubWind(playerId, 10);

        var calculator = createCalculator(0.25);
        double bonus = calculator.calculatePlayerBonus(playerId);

        assertEquals(2.5, bonus, 1e-9);
    }

    // =========================================================================
    // MISSING PLAYER / EDGE CASES
    // =========================================================================

    @Test
    @DisplayName("Player with no WIND attribute → 0% bonus (getOrDefault fallback)")
    void noWindAttribute_zeroBonusFallback() {
        UUID playerId = UUID.randomUUID();
        // Return attributes map without WIND key
        when(attributeManager.getPlayerAttributes(playerId))
                .thenReturn(Map.of(AttributeType.FIRE, 30));

        var calculator = createCalculator(0.5);
        double bonus = calculator.calculatePlayerBonus(playerId);

        assertEquals(0.0, bonus);
    }

    @Test
    @DisplayName("Player with empty attributes map → 0% bonus")
    void emptyAttributes_zeroBonus() {
        UUID playerId = UUID.randomUUID();
        when(attributeManager.getPlayerAttributes(playerId))
                .thenReturn(Map.of());

        var calculator = createCalculator(0.5);
        double bonus = calculator.calculatePlayerBonus(playerId);

        assertEquals(0.0, bonus);
    }

    @Test
    @DisplayName("Factor=0 → 0% bonus regardless of WIND")
    void factorZero_zeroBonusRegardlessOfWind() {
        UUID playerId = UUID.randomUUID();
        stubWind(playerId, 100);

        var calculator = createCalculator(0.0);
        double bonus = calculator.calculatePlayerBonus(playerId);

        assertEquals(0.0, bonus);
    }

    // =========================================================================
    // DIFFERENT CONVERSION FACTORS
    // =========================================================================

    @Test
    @DisplayName("Very small factor: WIND=100, factor=0.01 → 1% bonus")
    void smallFactor_onePercentBonus() {
        UUID playerId = UUID.randomUUID();
        stubWind(playerId, 100);

        var calculator = createCalculator(0.01);
        double bonus = calculator.calculatePlayerBonus(playerId);

        assertEquals(1.0, bonus, 1e-9);
    }

    @Test
    @DisplayName("Large factor: WIND=10, factor=5.0 → 50% bonus")
    void largeFactor_fiftyPercentBonus() {
        UUID playerId = UUID.randomUUID();
        stubWind(playerId, 10);

        var calculator = createCalculator(5.0);
        double bonus = calculator.calculatePlayerBonus(playerId);

        assertEquals(50.0, bonus, 1e-9);
    }

    // =========================================================================
    // CONSTRUCTOR VALIDATION
    // =========================================================================

    @Test
    @DisplayName("Null attributeManager → NullPointerException")
    void nullAttributeManager_throwsNPE() {
        assertThrows(NullPointerException.class,
                () -> new RarityBonusCalculator(null, 0.5));
    }
}
