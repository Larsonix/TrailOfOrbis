package io.github.larsonix.trailoforbis.maps.integration;

import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.leveling.xp.XpSource;
import io.github.larsonix.trailoforbis.maps.RealmsManager;
import io.github.larsonix.trailoforbis.maps.config.RealmsConfig;
import io.github.larsonix.trailoforbis.maps.core.RealmBiomeType;
import io.github.larsonix.trailoforbis.maps.core.RealmLayoutShape;
import io.github.larsonix.trailoforbis.maps.core.RealmLayoutSize;
import io.github.larsonix.trailoforbis.maps.core.RealmMapData;
import io.github.larsonix.trailoforbis.maps.instance.RealmInstance;
import io.github.larsonix.trailoforbis.maps.modifiers.RealmModifier;
import io.github.larsonix.trailoforbis.maps.modifiers.RealmModifierType;
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
 * Unit tests for {@link RealmLevelingIntegration}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RealmLevelingIntegration")
class RealmLevelingIntegrationTest {

    @Mock
    private RealmsManager realmsManager;

    @Mock
    private RealmInstance realmInstance;

    @Mock
    private RealmsConfig realmsConfig;

    private RealmLevelingIntegration integration;
    private UUID playerId;
    private UUID realmId;

    @BeforeEach
    void setUp() {
        // Set up config mock with defaults (0 = no limits), using lenient for nested tests
        lenient().when(realmsManager.getConfig()).thenReturn(realmsConfig);
        lenient().when(realmsConfig.getDifficultyXpScaling()).thenReturn(0.002);
        lenient().when(realmsConfig.getXpMultiplierMin()).thenReturn(0.0);
        lenient().when(realmsConfig.getXpMultiplierMax()).thenReturn(0.0);

        integration = new RealmLevelingIntegration(realmsManager);
        playerId = UUID.randomUUID();
        realmId = UUID.randomUUID();
    }

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("Should reject null realmsManager")
        void shouldRejectNullRealmsManager() {
            assertThrows(NullPointerException.class, () -> new RealmLevelingIntegration(null));
        }
    }

    @Nested
    @DisplayName("getXpMultiplier(UUID, XpSource)")
    class XpMultiplierWithSourceTests {

        @Test
        @DisplayName("Should return 1.0 when player not in realm")
        void shouldReturnOneWhenNotInRealm() {
            when(realmsManager.getPlayerRealm(playerId)).thenReturn(Optional.empty());

            double multiplier = integration.getXpMultiplier(playerId, XpSource.MOB_KILL);

            assertEquals(1.0, multiplier);
        }

        @Test
        @DisplayName("Should return 1.0 for ineligible XP source")
        void shouldReturnOneForIneligibleSource() {
            // REALM_COMPLETION is not eligible (bonus already baked in)
            double multiplier = integration.getXpMultiplier(playerId, XpSource.REALM_COMPLETION);

            assertEquals(1.0, multiplier);
            // Should not even check if player is in realm
            verifyNoInteractions(realmsManager);
        }

        @Test
        @DisplayName("Should apply XP bonus from modifiers")
        void shouldApplyXpBonusFromModifiers() {
            // EXPERIENCE_BONUS has difficulty weight 0, so only XP bonus applies
            RealmMapData mapData = createMapDataWithModifier(RealmModifierType.EXPERIENCE_BONUS, 30);
            when(realmInstance.getMapData()).thenReturn(mapData);
            when(realmsManager.getPlayerRealm(playerId)).thenReturn(Optional.of(realmInstance));

            double multiplier = integration.getXpMultiplier(playerId, XpSource.MOB_KILL);

            // 1.0 base + 0.30 bonus + (0 difficulty * 0.002) = 1.30
            assertEquals(1.30, multiplier, 0.01);
        }

        @Test
        @DisplayName("Should sum multiple XP bonus modifiers")
        void shouldSumMultipleModifiers() {
            // Include a difficulty-contributing modifier (MONSTER_HEALTH has weight 1)
            RealmMapData mapData = createMapDataWithModifiers(List.of(
                new RealmModifier(RealmModifierType.EXPERIENCE_BONUS, 20, false),
                new RealmModifier(RealmModifierType.EXPERIENCE_BONUS, 15, false),
                new RealmModifier(RealmModifierType.MONSTER_HEALTH, 50, false)  // weight 1
            ));
            when(realmInstance.getMapData()).thenReturn(mapData);
            when(realmsManager.getPlayerRealm(playerId)).thenReturn(Optional.of(realmInstance));

            double multiplier = integration.getXpMultiplier(playerId, XpSource.REALM_KILL);

            // 1.0 base + 0.35 XP bonus + (1 difficulty * 0.002) = 1.352
            assertTrue(multiplier > 1.35);
        }

        @Test
        @DisplayName("Should reject null parameters")
        void shouldRejectNullParameters() {
            assertThrows(NullPointerException.class, () -> integration.getXpMultiplier(null, XpSource.MOB_KILL));
            assertThrows(NullPointerException.class, () -> integration.getXpMultiplier(playerId, null));
        }
    }

    @Nested
    @DisplayName("getXpMultiplier(UUID)")
    class XpMultiplierWithoutSourceTests {

        @Test
        @DisplayName("Should return 1.0 when player not in realm")
        void shouldReturnOneWhenNotInRealm() {
            when(realmsManager.getPlayerRealm(playerId)).thenReturn(Optional.empty());

            double multiplier = integration.getXpMultiplier(playerId);

            assertEquals(1.0, multiplier);
        }

        @Test
        @DisplayName("Should return multiplier when in realm")
        void shouldReturnMultiplierWhenInRealm() {
            RealmMapData mapData = createMapDataWithModifier(RealmModifierType.EXPERIENCE_BONUS, 25);
            when(realmInstance.getMapData()).thenReturn(mapData);
            when(realmsManager.getPlayerRealm(playerId)).thenReturn(Optional.of(realmInstance));

            double multiplier = integration.getXpMultiplier(playerId);

            assertTrue(multiplier > 1.0);
        }
    }

    @Nested
    @DisplayName("isRealmXpSource")
    class IsRealmXpSourceTests {

        @Test
        @DisplayName("Should return true for REALM_KILL")
        void shouldReturnTrueForRealmKill() {
            assertTrue(integration.isRealmXpSource(XpSource.REALM_KILL));
        }

        @Test
        @DisplayName("Should return true for REALM_COMPLETION")
        void shouldReturnTrueForRealmCompletion() {
            assertTrue(integration.isRealmXpSource(XpSource.REALM_COMPLETION));
        }

        @Test
        @DisplayName("Should return false for MOB_KILL")
        void shouldReturnFalseForMobKill() {
            assertFalse(integration.isRealmXpSource(XpSource.MOB_KILL));
        }
    }

    @Nested
    @DisplayName("isEligibleForRealmBonus")
    class IsEligibleForRealmBonusTests {

        @Test
        @DisplayName("MOB_KILL should be eligible")
        void mobKillShouldBeEligible() {
            assertTrue(integration.isEligibleForRealmBonus(XpSource.MOB_KILL));
        }

        @Test
        @DisplayName("REALM_KILL should be eligible")
        void realmKillShouldBeEligible() {
            assertTrue(integration.isEligibleForRealmBonus(XpSource.REALM_KILL));
        }

        @Test
        @DisplayName("REALM_COMPLETION should not be eligible (bonus baked in)")
        void realmCompletionShouldNotBeEligible() {
            assertFalse(integration.isEligibleForRealmBonus(XpSource.REALM_COMPLETION));
        }
    }

    @Nested
    @DisplayName("isInRealm")
    class IsInRealmTests {

        @Test
        @DisplayName("Should return true when player in realm")
        void shouldReturnTrueWhenInRealm() {
            when(realmsManager.getPlayerRealm(playerId)).thenReturn(Optional.of(realmInstance));

            assertTrue(integration.isInRealm(playerId));
        }

        @Test
        @DisplayName("Should return false when player not in realm")
        void shouldReturnFalseWhenNotInRealm() {
            when(realmsManager.getPlayerRealm(playerId)).thenReturn(Optional.empty());

            assertFalse(integration.isInRealm(playerId));
        }
    }

    @Nested
    @DisplayName("getExperienceBonusPercent")
    class GetExperienceBonusPercentTests {

        @Test
        @DisplayName("Should return 0 when not in realm")
        void shouldReturnZeroWhenNotInRealm() {
            when(realmsManager.getPlayerRealm(playerId)).thenReturn(Optional.empty());

            int bonus = integration.getExperienceBonusPercent(playerId);

            assertEquals(0, bonus);
        }

        @Test
        @DisplayName("Should return modifier value when in realm")
        void shouldReturnModifierValueWhenInRealm() {
            RealmMapData mapData = createMapDataWithModifier(RealmModifierType.EXPERIENCE_BONUS, 35);
            when(realmInstance.getMapData()).thenReturn(mapData);
            when(realmsManager.getPlayerRealm(playerId)).thenReturn(Optional.of(realmInstance));

            int bonus = integration.getExperienceBonusPercent(playerId);

            assertEquals(35, bonus);
        }
    }

    @Nested
    @DisplayName("Multiplier bounds")
    class MultiplierBoundsTests {

        @Test
        @DisplayName("Should not exceed maximum possible multiplier with valid modifiers")
        void shouldStayWithinBoundsWithValidModifiers() {
            // Create max valid bonuses (EXPERIENCE_BONUS max is 50)
            // Multiple max modifiers to approach upper bound
            RealmMapData mapData = createMapDataWithModifiers(List.of(
                new RealmModifier(RealmModifierType.EXPERIENCE_BONUS, 50, false),  // +50%
                new RealmModifier(RealmModifierType.MONSTER_HEALTH, 150, false),   // difficulty 1
                new RealmModifier(RealmModifierType.MONSTER_DAMAGE, 100, false)    // difficulty 1
            ));
            when(realmInstance.getMapData()).thenReturn(mapData);
            when(realmsManager.getPlayerRealm(playerId)).thenReturn(Optional.of(realmInstance));

            double multiplier = integration.getXpMultiplier(playerId, XpSource.MOB_KILL);

            // Should be within valid bounds
            assertTrue(multiplier >= 0.1 && multiplier <= 5.0,
                "Multiplier should be within bounds [0.1, 5.0], was: " + multiplier);
            // Should include the XP bonus (1.0 + 0.50 + difficulty bonus)
            assertTrue(multiplier >= 1.5, "Multiplier should include XP bonus");
        }

        @Test
        @DisplayName("Multiplier with no modifiers should be 1.0")
        void shouldReturnBaseMultiplierWithNoModifiers() {
            RealmMapData mapData = createMapDataWithModifiers(List.of());
            when(realmInstance.getMapData()).thenReturn(mapData);
            when(realmsManager.getPlayerRealm(playerId)).thenReturn(Optional.of(realmInstance));

            double multiplier = integration.getXpMultiplier(playerId, XpSource.MOB_KILL);

            assertEquals(1.0, multiplier, 0.001);
        }
    }

    @Nested
    @DisplayName("getDebugInfo")
    class GetDebugInfoTests {

        @Test
        @DisplayName("Should return info for player not in realm")
        void shouldReturnInfoForPlayerNotInRealm() {
            when(realmsManager.getPlayerRealm(playerId)).thenReturn(Optional.empty());

            String debug = integration.getDebugInfo(playerId);

            assertTrue(debug.contains("inRealm=false"));
            assertTrue(debug.contains("multiplier=1.0x"));
        }

        @Test
        @DisplayName("Should return info for player in realm")
        void shouldReturnInfoForPlayerInRealm() {
            RealmMapData mapData = createMapDataWithModifier(RealmModifierType.EXPERIENCE_BONUS, 25);
            when(realmInstance.getMapData()).thenReturn(mapData);
            when(realmInstance.getRealmId()).thenReturn(realmId);
            when(realmsManager.getPlayerRealm(playerId)).thenReturn(Optional.of(realmInstance));

            String debug = integration.getDebugInfo(playerId);

            assertTrue(debug.contains("xpBonus=25%"));
            assertTrue(debug.contains("difficulty="));
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
