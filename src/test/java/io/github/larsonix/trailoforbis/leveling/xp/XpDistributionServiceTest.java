package io.github.larsonix.trailoforbis.leveling.xp;

import io.github.larsonix.trailoforbis.compat.party.PartyConfig;
import io.github.larsonix.trailoforbis.compat.party.PartyIntegrationManager;
import io.github.larsonix.trailoforbis.leveling.api.LevelingService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.annotation.Nonnull;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for XpDistributionService — the XP sharing pipeline for
 * party-based and proximity-based XP distribution.
 *
 * <p>Uses the package-private {@code distribute(killer, rawXp, modifier, recipients, isPartyMode)}
 * overload to bypass PlayerWorldCache and party resolution (which require a live Hytale server).
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Solo fast-path (1 recipient)</li>
 *   <li>Equal distribution mode</li>
 *   <li>Killer bonus distribution mode</li>
 *   <li>Anti-boosting group max level</li>
 *   <li>Per-player modifier application</li>
 *   <li>XpSource resolution (MOB_KILL, PARTY_SHARE, NEARBY_SHARE)</li>
 *   <li>Edge cases (zero XP, minimum clamping, large parties)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("XpDistributionService")
class XpDistributionServiceTest {

    @Mock
    private LevelingService levelingService;

    private PartyConfig partyConfig;
    private XpDistributionService service;

    private final UUID killer = UUID.randomUUID();
    private final UUID member1 = UUID.randomUUID();
    private final UUID member2 = UUID.randomUUID();
    private final UUID member3 = UUID.randomUUID();

    /** Identity modifier — returns raw share unchanged. */
    private static final XpRecipientModifier IDENTITY = new XpRecipientModifier() {
        @Override
        public long apply(@Nonnull UUID recipientUuid, long rawShare) {
            return rawShare;
        }

        @Override
        public long apply(@Nonnull UUID recipientUuid, long rawShare, int groupMaxLevel) {
            return rawShare;
        }
    };

    @BeforeEach
    void setUp() {
        partyConfig = new PartyConfig();
        partyConfig.getXpSharing().setEnabled(true);
        partyConfig.getXpSharing().setMode("equal");
        partyConfig.getAntiBoosting().setEnabled(false);

        service = new XpDistributionService(levelingService, partyConfig, null);
    }

    // ═══════════════════════════════════════════════════════════════════
    // SOLO FAST-PATH
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Solo Fast-Path")
    class SoloFastPath {

        @Test
        @DisplayName("Solo player should get full XP as MOB_KILL")
        void soloPlayer_getsFullXpAsMobKill() {
            service.distribute(killer, 100, IDENTITY, List.of(killer), false);

            verify(levelingService).addXp(killer, 100, XpSource.MOB_KILL);
            verifyNoMoreInteractions(levelingService);
        }

        @Test
        @DisplayName("Solo player modifier should be applied")
        void soloPlayer_modifierApplied() {
            XpRecipientModifier halver = (uuid, raw) -> raw / 2;

            service.distribute(killer, 100, halver, List.of(killer), false);

            verify(levelingService).addXp(killer, 50, XpSource.MOB_KILL);
        }

        @Test
        @DisplayName("Solo player modifier returning 0 should clamp to 1")
        void soloPlayer_modifierReturning0_clampsTo1() {
            XpRecipientModifier zeroer = (uuid, raw) -> 0;

            service.distribute(killer, 100, zeroer, List.of(killer), false);

            verify(levelingService).addXp(killer, 1, XpSource.MOB_KILL);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // EQUAL DISTRIBUTION MODE
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Equal Distribution Mode")
    class EqualDistribution {

        @Test
        @DisplayName("Two players should split evenly with correct sources")
        void twoPlayers_splitEvenly() {
            // Default multiplier for 2 players = 1.2
            List<UUID> recipients = List.of(killer, member1);

            service.distribute(killer, 100, IDENTITY, recipients, true);

            // Pool = 100 * 1.2 = 120. Share = 120 / 2 = 60
            verify(levelingService).addXp(killer, 60, XpSource.MOB_KILL);
            verify(levelingService).addXp(member1, 60, XpSource.PARTY_SHARE);
        }

        @Test
        @DisplayName("Three players in proximity mode should use NEARBY_SHARE source")
        void threePlayers_proximityMode_usesNearbyShareSource() {
            List<UUID> recipients = List.of(killer, member1, member2);

            // isPartyMode = false → proximity
            service.distribute(killer, 100, IDENTITY, recipients, false);

            // Multiplier for 3 = 1.35. Pool = 135. Share = 135/3 = 45
            verify(levelingService).addXp(killer, 45, XpSource.MOB_KILL);
            verify(levelingService).addXp(member1, 45, XpSource.NEARBY_SHARE);
            verify(levelingService).addXp(member2, 45, XpSource.NEARBY_SHARE);
        }

        @Test
        @DisplayName("Pool too small for party → each gets minimum 1")
        void poolTooSmall_eachGetsMinimum1() {
            // 5 players, raw XP = 1, no size multiplier defined for 5+ → default 1.0
            // Actually partyConfig has default multipliers: "5" -> 1.5
            // Pool = 1 * 1.5 = 2 (rounded). Share = 2/5 = 0. Clamped to 1.
            List<UUID> recipients = List.of(killer, member1, member2, member3, UUID.randomUUID());

            service.distribute(killer, 1, IDENTITY, recipients, true);

            // Each should get at least 1
            ArgumentCaptor<Long> xpCaptor = ArgumentCaptor.forClass(Long.class);
            verify(levelingService, times(5)).addXp(any(UUID.class), xpCaptor.capture(), any(XpSource.class));
            for (long xp : xpCaptor.getAllValues()) {
                assertTrue(xp >= 1, "Every player must get at least 1 XP");
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // KILLER BONUS DISTRIBUTION MODE
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Killer Bonus Distribution Mode")
    class KillerBonusDistribution {

        @BeforeEach
        void setKillerBonusMode() {
            partyConfig.getXpSharing().setMode("killer_bonus");
            service = new XpDistributionService(levelingService, partyConfig, null);
        }

        @Test
        @DisplayName("Killer gets full rawXp, others get pool share")
        void killerGetsFullRawXp_othersGetPoolShare() {
            List<UUID> recipients = List.of(killer, member1, member2);

            service.distribute(killer, 100, IDENTITY, recipients, true);

            // Killer gets full 100 raw XP
            verify(levelingService).addXp(killer, 100, XpSource.MOB_KILL);
            // Others get pool/count: pool = 100 * 1.35 = 135. Share = 135/3 = 45
            verify(levelingService).addXp(member1, 45, XpSource.PARTY_SHARE);
            verify(levelingService).addXp(member2, 45, XpSource.PARTY_SHARE);
        }

        @Test
        @DisplayName("Killer should not be double-granted")
        void killerNotDoubleGranted() {
            List<UUID> recipients = List.of(killer, member1);

            service.distribute(killer, 100, IDENTITY, recipients, true);

            // Killer should appear exactly once
            verify(levelingService, times(1)).addXp(eq(killer), anyLong(), eq(XpSource.MOB_KILL));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // ANTI-BOOSTING
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Anti-Boosting")
    class AntiBoosting {

        @Test
        @DisplayName("When enabled, should pass groupMaxLevel to modifier")
        void enabled_shouldPassGroupMaxLevel() {
            partyConfig.getAntiBoosting().setEnabled(true);
            partyConfig.getAntiBoosting().setUsePartyMaxLevel(true);
            service = new XpDistributionService(levelingService, partyConfig, null);

            // Set up levels: killer=10, member1=20, member2=30
            when(levelingService.getLevel(killer)).thenReturn(10);
            when(levelingService.getLevel(member1)).thenReturn(20);
            when(levelingService.getLevel(member2)).thenReturn(30);

            List<Integer> capturedMaxLevels = new ArrayList<>();
            XpRecipientModifier capturingModifier = new XpRecipientModifier() {
                @Override
                public long apply(@Nonnull UUID recipientUuid, long rawShare) {
                    return rawShare;
                }
                @Override
                public long apply(@Nonnull UUID recipientUuid, long rawShare, int groupMaxLevel) {
                    capturedMaxLevels.add(groupMaxLevel);
                    return rawShare;
                }
            };

            List<UUID> recipients = List.of(killer, member1, member2);
            service.distribute(killer, 100, capturingModifier, recipients, true);

            // All 3 recipients should receive groupMaxLevel = 30
            assertEquals(3, capturedMaxLevels.size());
            for (int maxLevel : capturedMaxLevels) {
                assertEquals(30, maxLevel, "Group max level should be 30");
            }
        }

        @Test
        @DisplayName("When disabled, should pass -1 to modifier")
        void disabled_shouldPassMinus1() {
            partyConfig.getAntiBoosting().setEnabled(false);

            List<Integer> capturedMaxLevels = new ArrayList<>();
            XpRecipientModifier capturingModifier = new XpRecipientModifier() {
                @Override
                public long apply(@Nonnull UUID recipientUuid, long rawShare) {
                    return rawShare;
                }
                @Override
                public long apply(@Nonnull UUID recipientUuid, long rawShare, int groupMaxLevel) {
                    capturedMaxLevels.add(groupMaxLevel);
                    return rawShare;
                }
            };

            List<UUID> recipients = List.of(killer, member1);
            service.distribute(killer, 100, capturingModifier, recipients, true);

            for (int maxLevel : capturedMaxLevels) {
                assertEquals(-1, maxLevel, "Group max level should be -1 when anti-boosting disabled");
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // MODIFIER INTEGRATION
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Modifier Integration")
    class ModifierIntegration {

        @Test
        @DisplayName("Modifier applied per-recipient, not once globally")
        void modifierAppliedPerRecipient() {
            Map<UUID, Long> customReturns = Map.of(
                killer, 50L,
                member1, 75L
            );
            XpRecipientModifier perPlayerModifier = (uuid, raw) ->
                customReturns.getOrDefault(uuid, raw);

            List<UUID> recipients = List.of(killer, member1);
            service.distribute(killer, 100, perPlayerModifier, recipients, true);

            // Pool = 100 * 1.2 = 120. Base share = 60.
            // But modifier overrides: killer→50, member1→75
            verify(levelingService).addXp(killer, 50, XpSource.MOB_KILL);
            verify(levelingService).addXp(member1, 75, XpSource.PARTY_SHARE);
        }

        @Test
        @DisplayName("Modifier returning 0 clamped to 1 per player")
        void modifierReturning0_clampedTo1() {
            XpRecipientModifier zeroModifier = (uuid, raw) -> 0;

            List<UUID> recipients = List.of(killer, member1);
            service.distribute(killer, 100, zeroModifier, recipients, true);

            verify(levelingService).addXp(killer, 1, XpSource.MOB_KILL);
            verify(levelingService).addXp(member1, 1, XpSource.PARTY_SHARE);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // SOURCE RESOLUTION
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Source Resolution")
    class SourceResolution {

        @Test
        @DisplayName("Killer always gets MOB_KILL source")
        void killerAlwaysGetsMobKill() {
            service.distribute(killer, 100, IDENTITY, List.of(killer, member1), true);
            verify(levelingService).addXp(eq(killer), anyLong(), eq(XpSource.MOB_KILL));

            // Also in killer_bonus mode
            partyConfig.getXpSharing().setMode("killer_bonus");
            service = new XpDistributionService(levelingService, partyConfig, null);
            service.distribute(killer, 100, IDENTITY, List.of(killer, member1), true);
            verify(levelingService, times(2)).addXp(eq(killer), anyLong(), eq(XpSource.MOB_KILL));
        }

        @Test
        @DisplayName("Party member gets PARTY_SHARE source")
        void partyMember_getsPartyShare() {
            service.distribute(killer, 100, IDENTITY, List.of(killer, member1), true);
            verify(levelingService).addXp(eq(member1), anyLong(), eq(XpSource.PARTY_SHARE));
        }

        @Test
        @DisplayName("Proximity player gets NEARBY_SHARE source")
        void proximityPlayer_getsNearbyShare() {
            service.distribute(killer, 100, IDENTITY, List.of(killer, member1), false);
            verify(levelingService).addXp(eq(member1), anyLong(), eq(XpSource.NEARBY_SHARE));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // EDGE CASES
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Empty recipient list should grant to killer as solo")
        void emptyRecipients_grantToKillerAsSolo() {
            service.distribute(killer, 100, IDENTITY, List.of(), false);

            // Empty list → size <= 1 → solo path grants to killer
            // Actually, recipients.size() = 0 which is <= 1, so solo fires
            verify(levelingService).addXp(killer, 100, XpSource.MOB_KILL);
        }

        @Test
        @DisplayName("Large party should not overflow")
        void largeParty_shouldNotOverflow() {
            List<UUID> bigParty = new ArrayList<>();
            bigParty.add(killer);
            for (int i = 0; i < 19; i++) {
                bigParty.add(UUID.randomUUID());
            }

            // No multiplier defined for 20 → defaults to 1.0
            assertDoesNotThrow(() ->
                service.distribute(killer, 1_000_000, IDENTITY, bigParty, true));

            // All 20 players should get addXp called
            verify(levelingService, times(20)).addXp(any(UUID.class), anyLong(), any(XpSource.class));
        }
    }
}
