package io.github.larsonix.trailoforbis.leveling.xp;

import io.github.larsonix.trailoforbis.leveling.api.LevelingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link SimplePartyXpSharing}.
 *
 * <p>Tests the XP distribution formula across party members:
 * <ul>
 *   <li>Disabled sharing → killer gets 100%</li>
 *   <li>Empty/solo party → killer gets 100%</li>
 *   <li>Multi-player party → equal split</li>
 *   <li>Killer not in party list → still receives share</li>
 *   <li>XpSource differentiation: MOB_KILL for killer, PARTY_SHARE for others</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SimplePartyXpSharing")
class SimplePartyXpSharingTest {

    @Mock
    private LevelingService levelingService;

    private SimplePartyXpSharing sharingEnabled;
    private SimplePartyXpSharing sharingDisabled;

    private final UUID killerId = UUID.randomUUID();
    private final UUID member1 = UUID.randomUUID();
    private final UUID member2 = UUID.randomUUID();
    private final UUID member3 = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        sharingEnabled = new SimplePartyXpSharing(levelingService, true);
        sharingDisabled = new SimplePartyXpSharing(levelingService, false);
    }

    // ═══════════════════════════════════════════════════════════════════
    // CONSTRUCTOR / GETTERS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Constructor and Getters")
    class ConstructorTests {

        @Test
        @DisplayName("isEnabled returns true when constructed with enabled=true")
        void isEnabled_returnsTrue_whenEnabled() {
            assertTrue(sharingEnabled.isEnabled());
        }

        @Test
        @DisplayName("isEnabled returns false when constructed with enabled=false")
        void isEnabled_returnsFalse_whenDisabled() {
            assertFalse(sharingDisabled.isEnabled());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // DISABLED SHARING
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("When Disabled")
    class DisabledTests {

        @Test
        @DisplayName("distributeXp gives killer 100% XP when sharing is disabled")
        void distributeXp_killerGetsFullXp_whenDisabled() {
            Set<UUID> partyMembers = Set.of(killerId, member1, member2);

            sharingDisabled.distributeXp(killerId, partyMembers, 100L);

            // Killer should receive full 100 XP with MOB_KILL source
            verify(levelingService).addXp(eq(killerId), eq(100L), eq(XpSource.MOB_KILL));
            verifyNoMoreInteractions(levelingService);
        }

        @Test
        @DisplayName("distributeXp gives killer 100% XP when party is empty")
        void distributeXp_killerGetsFullXp_whenPartyEmpty() {
            sharingEnabled.distributeXp(killerId, Collections.emptySet(), 100L);

            verify(levelingService).addXp(eq(killerId), eq(100L), eq(XpSource.MOB_KILL));
            verifyNoMoreInteractions(levelingService);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // SOLO PLAYER
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Solo Player")
    class SoloPlayerTests {

        @Test
        @DisplayName("distributeXp gives killer 100% XP when party has only killer")
        void distributeXp_killerGetsFullXp_whenSoloParty() {
            Set<UUID> partyMembers = Set.of(killerId);

            sharingEnabled.distributeXp(killerId, partyMembers, 100L);

            verify(levelingService).addXp(eq(killerId), eq(100L), eq(XpSource.MOB_KILL));
            verifyNoMoreInteractions(levelingService);
        }

        @Test
        @DisplayName("grantSoloXp gives full XP to killer")
        void grantSoloXp_givesFullXp() {
            sharingEnabled.grantSoloXp(killerId, 150L);

            verify(levelingService).addXp(eq(killerId), eq(150L), eq(XpSource.MOB_KILL));
            verifyNoMoreInteractions(levelingService);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // TWO-PLAYER PARTY
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Two-Player Party")
    class TwoPlayerPartyTests {

        @Test
        @DisplayName("distributeXp splits 100 XP evenly: 50 each for 2 players")
        void distributeXp_splits100Evenly_for2Players() {
            Set<UUID> partyMembers = Set.of(killerId, member1);

            sharingEnabled.distributeXp(killerId, partyMembers, 100L);

            // Each member gets 50 XP
            verify(levelingService).addXp(eq(killerId), eq(50L), eq(XpSource.MOB_KILL));
            verify(levelingService).addXp(eq(member1), eq(50L), eq(XpSource.PARTY_SHARE));
            verifyNoMoreInteractions(levelingService);
        }

        @Test
        @DisplayName("distributeXp handles odd XP amounts (rounds down)")
        void distributeXp_handlesOddAmounts() {
            Set<UUID> partyMembers = Set.of(killerId, member1);

            // 101 / 2 = 50 (rounded down, then capped at minimum 1)
            sharingEnabled.distributeXp(killerId, partyMembers, 101L);

            verify(levelingService).addXp(eq(killerId), eq(50L), eq(XpSource.MOB_KILL));
            verify(levelingService).addXp(eq(member1), eq(50L), eq(XpSource.PARTY_SHARE));
            verifyNoMoreInteractions(levelingService);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // FOUR-PLAYER PARTY
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Four-Player Party")
    class FourPlayerPartyTests {

        @Test
        @DisplayName("distributeXp splits 100 XP into 25 each for 4 players")
        void distributeXp_splits100Into25Each_for4Players() {
            Set<UUID> partyMembers = Set.of(killerId, member1, member2, member3);

            sharingEnabled.distributeXp(killerId, partyMembers, 100L);

            // 100 / 4 = 25 each
            verify(levelingService).addXp(eq(killerId), eq(25L), eq(XpSource.MOB_KILL));
            verify(levelingService).addXp(eq(member1), eq(25L), eq(XpSource.PARTY_SHARE));
            verify(levelingService).addXp(eq(member2), eq(25L), eq(XpSource.PARTY_SHARE));
            verify(levelingService).addXp(eq(member3), eq(25L), eq(XpSource.PARTY_SHARE));
            verifyNoMoreInteractions(levelingService);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // KILLER NOT IN PARTY LIST
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Killer Not In Party List")
    class KillerNotInPartyTests {

        @Test
        @DisplayName("distributeXp includes killer when not in party list")
        void distributeXp_includesKiller_whenNotInPartyList() {
            // Party has 2 members, but killer is NOT in the list
            Set<UUID> partyMembers = Set.of(member1, member2);

            sharingEnabled.distributeXp(killerId, partyMembers, 90L);

            // Party size becomes 3 (2 members + killer)
            // 90 / 3 = 30 each
            verify(levelingService).addXp(eq(member1), eq(30L), eq(XpSource.PARTY_SHARE));
            verify(levelingService).addXp(eq(member2), eq(30L), eq(XpSource.PARTY_SHARE));
            verify(levelingService).addXp(eq(killerId), eq(30L), eq(XpSource.MOB_KILL));
            verifyNoMoreInteractions(levelingService);
        }

        @Test
        @DisplayName("distributeXp treats single-member party (without killer) as solo")
        void distributeXp_soloWhenOnlyMemberWithoutKiller() {
            // Party has 1 member, but killer is NOT that member
            // partySize = 1 + 1 (killer) = 2
            Set<UUID> partyMembers = Set.of(member1);

            sharingEnabled.distributeXp(killerId, partyMembers, 100L);

            // 100 / 2 = 50 each
            verify(levelingService).addXp(eq(member1), eq(50L), eq(XpSource.PARTY_SHARE));
            verify(levelingService).addXp(eq(killerId), eq(50L), eq(XpSource.MOB_KILL));
            verifyNoMoreInteractions(levelingService);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // XP SOURCE DIFFERENTIATION
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("XpSource Differentiation")
    class XpSourceTests {

        @Test
        @DisplayName("killer receives XP with MOB_KILL source")
        void killer_receivesXp_withMobKillSource() {
            Set<UUID> partyMembers = Set.of(killerId, member1);

            sharingEnabled.distributeXp(killerId, partyMembers, 100L);

            verify(levelingService).addXp(eq(killerId), any(Long.class), eq(XpSource.MOB_KILL));
        }

        @Test
        @DisplayName("party members receive XP with PARTY_SHARE source")
        void partyMembers_receiveXp_withPartyShareSource() {
            Set<UUID> partyMembers = Set.of(killerId, member1, member2);

            sharingEnabled.distributeXp(killerId, partyMembers, 99L);

            verify(levelingService).addXp(eq(member1), any(Long.class), eq(XpSource.PARTY_SHARE));
            verify(levelingService).addXp(eq(member2), any(Long.class), eq(XpSource.PARTY_SHARE));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // MINIMUM XP PER MEMBER
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Minimum XP Per Member")
    class MinimumXpTests {

        @Test
        @DisplayName("distributeXp ensures minimum 1 XP per member")
        void distributeXp_ensuresMinimum1XpPerMember() {
            // Large party, small XP: 5 XP / 4 players = 1 each (minimum)
            Set<UUID> partyMembers = Set.of(killerId, member1, member2, member3);

            sharingEnabled.distributeXp(killerId, partyMembers, 5L);

            // 5 / 4 = 1 (minimum)
            verify(levelingService).addXp(eq(killerId), eq(1L), eq(XpSource.MOB_KILL));
            verify(levelingService).addXp(eq(member1), eq(1L), eq(XpSource.PARTY_SHARE));
            verify(levelingService).addXp(eq(member2), eq(1L), eq(XpSource.PARTY_SHARE));
            verify(levelingService).addXp(eq(member3), eq(1L), eq(XpSource.PARTY_SHARE));
        }

        @Test
        @DisplayName("distributeXp gives at least 1 XP even when total is very small")
        void distributeXp_givesAtLeast1Xp_whenTotalVerySmall() {
            // 1 XP total for 2 players -> 1 / 2 = 0, but minimum is 1
            Set<UUID> partyMembers = Set.of(killerId, member1);

            sharingEnabled.distributeXp(killerId, partyMembers, 1L);

            // Math.max(1, 1/2) = Math.max(1, 0) = 1
            verify(levelingService).addXp(eq(killerId), eq(1L), eq(XpSource.MOB_KILL));
            verify(levelingService).addXp(eq(member1), eq(1L), eq(XpSource.PARTY_SHARE));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // EDGE CASES
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("distributeXp handles zero XP (no-op)")
        void distributeXp_handlesZeroXp() {
            Set<UUID> partyMembers = Set.of(killerId, member1);

            // 0 / 2 = 0, but minimum 1 per member
            sharingEnabled.distributeXp(killerId, partyMembers, 0L);

            // With minimum 1, each still gets 1 XP
            verify(levelingService).addXp(eq(killerId), eq(1L), eq(XpSource.MOB_KILL));
            verify(levelingService).addXp(eq(member1), eq(1L), eq(XpSource.PARTY_SHARE));
        }

        @Test
        @DisplayName("distributeXp handles large party")
        void distributeXp_handlesLargeParty() {
            Set<UUID> partyMembers = new HashSet<>();
            partyMembers.add(killerId);
            for (int i = 0; i < 19; i++) {
                partyMembers.add(UUID.randomUUID());
            }

            // 20 players, 100 XP -> 5 each
            sharingEnabled.distributeXp(killerId, partyMembers, 100L);

            // Verify killer gets their share
            verify(levelingService).addXp(eq(killerId), eq(5L), eq(XpSource.MOB_KILL));
            // Verify exactly 20 addXp calls total
            verify(levelingService, times(20)).addXp(any(UUID.class), eq(5L), any(XpSource.class));
        }

        @Test
        @DisplayName("distributeXp handles large XP amount")
        void distributeXp_handlesLargeXp() {
            Set<UUID> partyMembers = Set.of(killerId, member1);

            // Large XP: 1 billion / 2 = 500 million each
            sharingEnabled.distributeXp(killerId, partyMembers, 1_000_000_000L);

            verify(levelingService).addXp(eq(killerId), eq(500_000_000L), eq(XpSource.MOB_KILL));
            verify(levelingService).addXp(eq(member1), eq(500_000_000L), eq(XpSource.PARTY_SHARE));
        }
    }
}
