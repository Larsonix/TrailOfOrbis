package io.github.larsonix.trailoforbis.maps.integration;

import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.maps.RealmsManager;
import io.github.larsonix.trailoforbis.maps.core.RealmBiomeType;
import io.github.larsonix.trailoforbis.maps.core.RealmLayoutShape;
import io.github.larsonix.trailoforbis.maps.core.RealmLayoutSize;
import io.github.larsonix.trailoforbis.maps.core.RealmMapData;
import io.github.larsonix.trailoforbis.maps.instance.RealmInstance;
import io.github.larsonix.trailoforbis.maps.modifiers.RealmModifier;
import io.github.larsonix.trailoforbis.maps.modifiers.RealmModifierType;
import io.github.larsonix.trailoforbis.maps.reward.RealmRewardResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RealmLootIntegration}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RealmLootIntegration")
class RealmLootIntegrationTest {

    @Mock
    private RealmsManager realmsManager;

    @Mock
    private RealmInstance realmInstance;

    private RealmLootIntegration integration;
    private UUID playerId;
    private UUID realmId;

    @BeforeEach
    void setUp() {
        integration = new RealmLootIntegration(realmsManager);
        playerId = UUID.randomUUID();
        realmId = UUID.randomUUID();
    }

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("Should reject null realmsManager")
        void shouldRejectNullRealmsManager() {
            assertThrows(NullPointerException.class, () -> new RealmLootIntegration(null));
        }
    }

    @Nested
    @DisplayName("getItemQuantityBonus")
    class ItemQuantityBonusTests {

        @Test
        @DisplayName("Should return 0 when player not in realm")
        void shouldReturnZeroWhenNotInRealm() {
            when(realmsManager.getPlayerRealm(playerId)).thenReturn(Optional.empty());

            double bonus = integration.getItemQuantityBonus(playerId);

            assertEquals(0.0, bonus);
        }

        @Test
        @DisplayName("Should return modifier value when in realm")
        void shouldReturnModifierValueWhenInRealm() {
            RealmMapData mapData = createMapDataWithModifier(RealmModifierType.ITEM_QUANTITY, 25);
            when(realmInstance.getMapData()).thenReturn(mapData);
            when(realmsManager.getPlayerRealm(playerId)).thenReturn(Optional.of(realmInstance));

            double bonus = integration.getItemQuantityBonus(playerId);

            assertEquals(25.0, bonus);
        }

        @Test
        @DisplayName("Should sum multiple IIQ modifiers")
        void shouldSumMultipleModifiers() {
            RealmMapData mapData = createMapDataWithModifiers(List.of(
                new RealmModifier(RealmModifierType.ITEM_QUANTITY, 15, false),
                new RealmModifier(RealmModifierType.ITEM_QUANTITY, 10, false)
            ));
            when(realmInstance.getMapData()).thenReturn(mapData);
            when(realmsManager.getPlayerRealm(playerId)).thenReturn(Optional.of(realmInstance));

            double bonus = integration.getItemQuantityBonus(playerId);

            assertEquals(25.0, bonus);
        }

        @Test
        @DisplayName("Should reject null playerId")
        void shouldRejectNullPlayerId() {
            assertThrows(NullPointerException.class, () -> integration.getItemQuantityBonus(null));
        }
    }

    @Nested
    @DisplayName("getItemRarityBonus")
    class ItemRarityBonusTests {

        @Test
        @DisplayName("Should return 0 when player not in realm")
        void shouldReturnZeroWhenNotInRealm() {
            when(realmsManager.getPlayerRealm(playerId)).thenReturn(Optional.empty());

            double bonus = integration.getItemRarityBonus(playerId);

            assertEquals(0.0, bonus);
        }

        @Test
        @DisplayName("Should return modifier value when in realm")
        void shouldReturnModifierValueWhenInRealm() {
            RealmMapData mapData = createMapDataWithModifier(RealmModifierType.ITEM_RARITY, 30);
            when(realmInstance.getMapData()).thenReturn(mapData);
            when(realmsManager.getPlayerRealm(playerId)).thenReturn(Optional.of(realmInstance));

            double bonus = integration.getItemRarityBonus(playerId);

            assertEquals(30.0, bonus);
        }
    }

    @Nested
    @DisplayName("storeCompletionBonuses")
    class StoreCompletionBonusesTests {

        @Test
        @DisplayName("Should store bonuses from reward result")
        void shouldStoreBonusesFromRewardResult() {
            when(realmsManager.getPlayerRealm(playerId)).thenReturn(Optional.empty());

            // Store completion bonuses (1.25 multiplier = 25% bonus)
            RealmRewardResult result = new RealmRewardResult(
                playerId,
                1.0,   // xpMultiplier
                1.25,  // itemQuantityMultiplier
                1.30,  // itemRarityMultiplier
                1.0,   // stoneMultiplier
                1.0,   // mapMultiplier
                100,   // performanceScore
                50.0   // contributionPercent
            );

            integration.storeCompletionBonuses(playerId, result);

            // Should now return stored bonuses
            assertEquals(25.0, integration.getItemQuantityBonus(playerId), 0.001);
            assertEquals(30.0, integration.getItemRarityBonus(playerId), 0.001);
        }

        @Test
        @DisplayName("Should not store if no rewards")
        void shouldNotStoreIfNoRewards() {
            RealmRewardResult failedResult = RealmRewardResult.failed(playerId);
            when(realmsManager.getPlayerRealm(playerId)).thenReturn(Optional.empty());

            integration.storeCompletionBonuses(playerId, failedResult);

            assertEquals(0.0, integration.getItemQuantityBonus(playerId));
        }

        @Test
        @DisplayName("Should reject null parameters")
        void shouldRejectNullParameters() {
            RealmRewardResult result = new RealmRewardResult(playerId, 1, 1.25, 1.30, 1, 1, 100, 50);
            assertThrows(NullPointerException.class, () -> integration.storeCompletionBonuses(null, result));
            assertThrows(NullPointerException.class, () -> integration.storeCompletionBonuses(playerId, null));
        }
    }

    @Nested
    @DisplayName("clearCompletionBonuses")
    class ClearCompletionBonusesTests {

        @Test
        @DisplayName("Should clear stored bonuses")
        void shouldClearStoredBonuses() {
            when(realmsManager.getPlayerRealm(playerId)).thenReturn(Optional.empty());

            // Store some bonuses
            RealmRewardResult result = new RealmRewardResult(playerId, 1, 1.25, 1.30, 1, 1, 100, 50);
            integration.storeCompletionBonuses(playerId, result);

            // Verify they exist
            assertTrue(integration.getItemQuantityBonus(playerId) > 0);

            // Clear them
            integration.clearCompletionBonuses(playerId);

            // Verify they're gone
            assertEquals(0.0, integration.getItemQuantityBonus(playerId));
        }
    }

    @Nested
    @DisplayName("hasLootBonuses")
    class HasLootBonusesTests {

        @Test
        @DisplayName("Should return false when no bonuses")
        void shouldReturnFalseWhenNoBonuses() {
            when(realmsManager.getPlayerRealm(playerId)).thenReturn(Optional.empty());

            assertFalse(integration.hasLootBonuses(playerId));
        }

        @Test
        @DisplayName("Should return true when in realm with modifiers")
        void shouldReturnTrueWhenInRealmWithModifiers() {
            RealmMapData mapData = createMapDataWithModifier(RealmModifierType.ITEM_QUANTITY, 25);
            when(realmInstance.getMapData()).thenReturn(mapData);
            when(realmsManager.getPlayerRealm(playerId)).thenReturn(Optional.of(realmInstance));

            assertTrue(integration.hasLootBonuses(playerId));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════

    private RealmMapData createMapDataWithModifier(RealmModifierType type, int value) {
        return createMapDataWithModifiers(List.of(new RealmModifier(type, value, false)));
    }

    private RealmMapData createMapDataWithModifiers(List<RealmModifier> modifiers) {
        // Split modifiers into prefixes and suffixes based on their type
        List<RealmModifier> prefixes = modifiers.stream()
            .filter(RealmModifier::isPrefix)
            .toList();
        List<RealmModifier> suffixes = modifiers.stream()
            .filter(RealmModifier::isSuffix)
            .toList();

        return new RealmMapData(
            10,  // level
            GearRarity.RARE,
            50,  // quality
            RealmBiomeType.FOREST,
            RealmLayoutSize.MEDIUM,
            RealmLayoutShape.CIRCULAR,
            prefixes,
            suffixes,
            false,  // corrupted
            true,   // identified
            null    // instanceId
        );
    }
}
