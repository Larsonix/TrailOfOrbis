package io.github.larsonix.trailoforbis.compat.party;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.leveling.api.LevelingService;
import io.github.larsonix.trailoforbis.leveling.xp.XpDistributionService;
import io.github.larsonix.trailoforbis.leveling.xp.XpRecipientModifier;
import io.github.larsonix.trailoforbis.leveling.xp.XpSource;
import io.github.larsonix.trailoforbis.util.PlayerWorldCache;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link XpDistributionService#distribute} — the unified XP sharing pipeline.
 *
 * <p>Verifies XP distribution for solo, equal split, and killer bonus modes
 * in party mode (with PartyPro). Uses mock {@link PartyBridge} and
 * {@link LevelingService} — no Hytale runtime needed.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("XpDistributionService — Party Mode XP Distribution")
class PartyIntegrationManagerTest {

    @Mock private LevelingService levelingService;
    @Mock private PartyBridge bridge;
    @Mock private PartyIntegrationManager partyManager;
    @Mock private Store<EntityStore> store;

    private PartyConfig config;
    private XpDistributionService service;

    /** Identity modifier — passes XP through without adjustment (isolates distribution logic). */
    private static final XpRecipientModifier IDENTITY = new XpRecipientModifier() {
        @Override
        public long apply(UUID id, long xp) { return xp; }

        @Override
        public long apply(UUID id, long xp, int groupMaxLevel) { return xp; }
    };

    private static final Vector3d MOB_POS = new Vector3d(100, 50, 100);

    private final UUID killer = UUID.randomUUID();
    private final UUID member2 = UUID.randomUUID();
    private final UUID member3 = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        config = new PartyConfig();
        config.setEnabled(true);

        // Set up partyManager mock to return our bridge (lenient: not all tests use these)
        lenient().when(partyManager.isPartyModAvailable()).thenReturn(true);
        lenient().when(partyManager.getBridge()).thenReturn(bridge);

        service = new XpDistributionService(levelingService, config, partyManager);
    }

    // ═══════════════════════════════════════════════════════════════════
    // SOLO — NOT IN PARTY
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Solo (not in party)")
    class SoloTests {

        @Test
        @DisplayName("Should grant full XP to killer when not in party")
        void shouldGrantFullXpWhenNotInParty() {
            when(bridge.isInParty(killer)).thenReturn(false);

            service.distribute(killer, 100, IDENTITY, MOB_POS, store);

            verify(levelingService).addXp(killer, 100, XpSource.MOB_KILL);
            verifyNoMoreInteractions(levelingService);
        }

        @Test
        @DisplayName("Should grant full XP when XP sharing is disabled")
        void shouldGrantFullXpWhenSharingDisabled() {
            config.getXpSharing().setEnabled(false);

            service.distribute(killer, 100, IDENTITY, MOB_POS, store);

            verify(levelingService).addXp(killer, 100, XpSource.MOB_KILL);
            verify(bridge, never()).isInParty(any());
        }

        @Test
        @DisplayName("Should grant full XP when only member in world")
        void shouldGrantFullXpWhenOnlyMemberInWorld() {
            when(bridge.isInParty(killer)).thenReturn(true);
            when(bridge.getOnlinePartyMembers(killer)).thenReturn(List.of(killer, member2));

            try (MockedStatic<PlayerWorldCache> cache = mockStatic(PlayerWorldCache.class)) {
                com.hypixel.hytale.server.core.universe.world.World mockWorld = mock(com.hypixel.hytale.server.core.universe.world.World.class);
                com.hypixel.hytale.server.core.universe.world.World otherWorld = mock(com.hypixel.hytale.server.core.universe.world.World.class);
                cache.when(() -> PlayerWorldCache.getPlayerWorld(killer)).thenReturn(Optional.of(mockWorld));
                cache.when(() -> PlayerWorldCache.getPlayerWorld(member2)).thenReturn(Optional.of(otherWorld));

                service.distribute(killer, 100, IDENTITY, MOB_POS, store);

                verify(levelingService).addXp(killer, 100, XpSource.MOB_KILL);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // EQUAL SPLIT
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Equal Split Mode")
    class EqualSplitTests {

        @BeforeEach
        void setUp() {
            config.getXpSharing().setMode("equal");
        }

        @Test
        @DisplayName("Two players: each gets pool/2 with 1.2x multiplier")
        void twoPlayersEqualSplit() {
            when(bridge.isInParty(killer)).thenReturn(true);
            when(bridge.getOnlinePartyMembers(killer)).thenReturn(List.of(killer, member2));

            try (MockedStatic<PlayerWorldCache> cache = mockStatic(PlayerWorldCache.class)) {
                com.hypixel.hytale.server.core.universe.world.World world = mock(com.hypixel.hytale.server.core.universe.world.World.class);
                cache.when(() -> PlayerWorldCache.getPlayerWorld(killer)).thenReturn(Optional.of(world));
                cache.when(() -> PlayerWorldCache.getPlayerWorld(member2)).thenReturn(Optional.of(world));

                service.distribute(killer, 100, IDENTITY, MOB_POS, store);

                // Pool = 100 * 1.2 = 120, split = 120/2 = 60
                verify(levelingService).addXp(killer, 60, XpSource.MOB_KILL);
                verify(levelingService).addXp(member2, 60, XpSource.PARTY_SHARE);
                verifyNoMoreInteractions(levelingService);
            }
        }

        @Test
        @DisplayName("Three players: each gets pool/3 with 1.35x multiplier")
        void threePlayersEqualSplit() {
            when(bridge.isInParty(killer)).thenReturn(true);
            when(bridge.getOnlinePartyMembers(killer)).thenReturn(List.of(killer, member2, member3));

            try (MockedStatic<PlayerWorldCache> cache = mockStatic(PlayerWorldCache.class)) {
                com.hypixel.hytale.server.core.universe.world.World world = mock(com.hypixel.hytale.server.core.universe.world.World.class);
                cache.when(() -> PlayerWorldCache.getPlayerWorld(killer)).thenReturn(Optional.of(world));
                cache.when(() -> PlayerWorldCache.getPlayerWorld(member2)).thenReturn(Optional.of(world));
                cache.when(() -> PlayerWorldCache.getPlayerWorld(member3)).thenReturn(Optional.of(world));

                service.distribute(killer, 100, IDENTITY, MOB_POS, store);

                // Pool = 100 * 1.35 = 135, split = 135/3 = 45
                verify(levelingService).addXp(killer, 45, XpSource.MOB_KILL);
                verify(levelingService).addXp(member2, 45, XpSource.PARTY_SHARE);
                verify(levelingService).addXp(member3, 45, XpSource.PARTY_SHARE);
            }
        }

        @Test
        @DisplayName("Killer gets MOB_KILL source, members get PARTY_SHARE")
        void correctSourcePerRecipient() {
            when(bridge.isInParty(killer)).thenReturn(true);
            when(bridge.getOnlinePartyMembers(killer)).thenReturn(List.of(killer, member2));

            try (MockedStatic<PlayerWorldCache> cache = mockStatic(PlayerWorldCache.class)) {
                com.hypixel.hytale.server.core.universe.world.World world = mock(com.hypixel.hytale.server.core.universe.world.World.class);
                cache.when(() -> PlayerWorldCache.getPlayerWorld(any())).thenReturn(Optional.of(world));

                service.distribute(killer, 100, IDENTITY, MOB_POS, store);

                verify(levelingService).addXp(eq(killer), anyLong(), eq(XpSource.MOB_KILL));
                verify(levelingService).addXp(eq(member2), anyLong(), eq(XpSource.PARTY_SHARE));
            }
        }

        @Test
        @DisplayName("Minimum XP per member is 1")
        void minimumXpIsOne() {
            when(bridge.isInParty(killer)).thenReturn(true);
            when(bridge.getOnlinePartyMembers(killer)).thenReturn(List.of(killer, member2, member3));

            try (MockedStatic<PlayerWorldCache> cache = mockStatic(PlayerWorldCache.class)) {
                com.hypixel.hytale.server.core.universe.world.World world = mock(com.hypixel.hytale.server.core.universe.world.World.class);
                cache.when(() -> PlayerWorldCache.getPlayerWorld(any())).thenReturn(Optional.of(world));

                // 1 XP * 1.35 = 1.35, rounds to 1, 1/3 = 0, but max(1, ...) → 1
                service.distribute(killer, 1, IDENTITY, MOB_POS, store);

                verify(levelingService).addXp(killer, 1, XpSource.MOB_KILL);
                verify(levelingService).addXp(member2, 1, XpSource.PARTY_SHARE);
                verify(levelingService).addXp(member3, 1, XpSource.PARTY_SHARE);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // KILLER BONUS MODE
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Killer Bonus Mode")
    class KillerBonusTests {

        @BeforeEach
        void setUp() {
            config.getXpSharing().setMode("killer_bonus");
        }

        @Test
        @DisplayName("Killer gets full XP, members get bonus share")
        void killerGetsFullMembersGetBonus() {
            when(bridge.isInParty(killer)).thenReturn(true);
            when(bridge.getOnlinePartyMembers(killer)).thenReturn(List.of(killer, member2));

            try (MockedStatic<PlayerWorldCache> cache = mockStatic(PlayerWorldCache.class)) {
                com.hypixel.hytale.server.core.universe.world.World world = mock(com.hypixel.hytale.server.core.universe.world.World.class);
                cache.when(() -> PlayerWorldCache.getPlayerWorld(any())).thenReturn(Optional.of(world));

                service.distribute(killer, 100, IDENTITY, MOB_POS, store);

                // Killer gets full 100
                verify(levelingService).addXp(killer, 100, XpSource.MOB_KILL);
                // Bonus pool = 100 * 1.2 = 120, bonus per member = 120/2 = 60
                verify(levelingService).addXp(member2, 60, XpSource.PARTY_SHARE);
            }
        }

        @Test
        @DisplayName("Killer is NOT counted in bonus pool split")
        void killerNotDoubleGranted() {
            when(bridge.isInParty(killer)).thenReturn(true);
            when(bridge.getOnlinePartyMembers(killer)).thenReturn(List.of(killer, member2, member3));

            try (MockedStatic<PlayerWorldCache> cache = mockStatic(PlayerWorldCache.class)) {
                com.hypixel.hytale.server.core.universe.world.World world = mock(com.hypixel.hytale.server.core.universe.world.World.class);
                cache.when(() -> PlayerWorldCache.getPlayerWorld(any())).thenReturn(Optional.of(world));

                service.distribute(killer, 100, IDENTITY, MOB_POS, store);

                // Killer gets full XP once only
                verify(levelingService, times(1)).addXp(eq(killer), anyLong(), any());
                // Members each get bonus
                verify(levelingService).addXp(eq(member2), anyLong(), eq(XpSource.PARTY_SHARE));
                verify(levelingService).addXp(eq(member3), anyLong(), eq(XpSource.PARTY_SHARE));
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // SAME WORLD FILTER
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Same World Filter")
    class SameWorldFilterTests {

        @Test
        @DisplayName("Should exclude members in different worlds")
        void shouldExcludeDifferentWorlds() {
            when(bridge.isInParty(killer)).thenReturn(true);
            when(bridge.getOnlinePartyMembers(killer)).thenReturn(List.of(killer, member2, member3));

            try (MockedStatic<PlayerWorldCache> cache = mockStatic(PlayerWorldCache.class)) {
                com.hypixel.hytale.server.core.universe.world.World world1 = mock(com.hypixel.hytale.server.core.universe.world.World.class);
                com.hypixel.hytale.server.core.universe.world.World world2 = mock(com.hypixel.hytale.server.core.universe.world.World.class);
                cache.when(() -> PlayerWorldCache.getPlayerWorld(killer)).thenReturn(Optional.of(world1));
                cache.when(() -> PlayerWorldCache.getPlayerWorld(member2)).thenReturn(Optional.of(world1)); // same world
                cache.when(() -> PlayerWorldCache.getPlayerWorld(member3)).thenReturn(Optional.of(world2)); // different world

                service.distribute(killer, 100, IDENTITY, MOB_POS, store);

                // member3 should NOT receive XP (different world)
                verify(levelingService, never()).addXp(eq(member3), anyLong(), any());
                // killer and member2 should receive XP
                verify(levelingService).addXp(eq(killer), anyLong(), any());
                verify(levelingService).addXp(eq(member2), anyLong(), any());
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // PARTY SIZE MULTIPLIERS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Party Size Multipliers")
    class MultiplierTests {

        @Test
        @DisplayName("Default multiplier for unmapped sizes is 1.0x")
        void defaultMultiplierIsOne() {
            assertEquals(1.0, config.getXpSharing().getMultiplierForSize(10));
        }

        @Test
        @DisplayName("2-player multiplier is 1.2x")
        void twoPlayerMultiplier() {
            assertEquals(1.2, config.getXpSharing().getMultiplierForSize(2));
        }

        @Test
        @DisplayName("5-player multiplier is 1.5x")
        void fivePlayerMultiplier() {
            assertEquals(1.5, config.getXpSharing().getMultiplierForSize(5));
        }
    }
}
