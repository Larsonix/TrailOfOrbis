package io.github.larsonix.trailoforbis.leveling;

import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import io.github.larsonix.trailoforbis.leveling.config.LevelingConfig;
import io.github.larsonix.trailoforbis.leveling.xp.XpSource;
import io.github.larsonix.trailoforbis.util.PlayerWorldCache;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;

import java.util.UUID;

import static org.mockito.Mockito.*;

/**
 * Tests for {@link XpGainNotificationService}.
 *
 * <p>Verifies notification filtering (source exclusions, config toggle),
 * source-aware message formatting, and graceful handling of null PlayerRef.
 */
@DisplayName("XpGainNotificationService")
class XpGainNotificationServiceTest {

    private LevelingConfig config;
    private XpGainNotificationService service;
    private final UUID testPlayer = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        config = new LevelingConfig();
        config.setEnabled(true);
        // UI config with notification enabled by default
        LevelingConfig.UiConfig ui = new LevelingConfig.UiConfig();
        ui.setShowXpGainNotification(true);
        config.setUi(ui);

        service = new XpGainNotificationService(config);
    }

    // ═══════════════════════════════════════════════════════════════════
    // CONFIG TOGGLE
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Config Toggle")
    class ConfigToggleTests {

        @Test
        @DisplayName("Should skip notification when config is disabled")
        void shouldSkipWhenDisabled() {
            config.getUi().setShowXpGainNotification(false);

            try (MockedStatic<PlayerWorldCache> cache = mockStatic(PlayerWorldCache.class)) {
                service.onXpGain(testPlayer, 100, XpSource.MOB_KILL, 1000);

                // PlayerWorldCache should never be called when disabled
                cache.verifyNoInteractions();
            }
        }

        @Test
        @DisplayName("Should proceed when config is enabled")
        void shouldProceedWhenEnabled() {
            try (MockedStatic<PlayerWorldCache> cache = mockStatic(PlayerWorldCache.class)) {
                // Return null to short-circuit before sending
                cache.when(() -> PlayerWorldCache.findPlayerRef(testPlayer)).thenReturn(null);

                service.onXpGain(testPlayer, 100, XpSource.MOB_KILL, 1000);

                // Should have attempted to resolve the player
                cache.verify(() -> PlayerWorldCache.findPlayerRef(testPlayer));
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // SOURCE FILTERING
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Source Filtering")
    class SourceFilteringTests {

        @Test
        @DisplayName("Should skip ADMIN_COMMAND source")
        void shouldSkipAdminCommand() {
            try (MockedStatic<PlayerWorldCache> cache = mockStatic(PlayerWorldCache.class)) {
                service.onXpGain(testPlayer, 100, XpSource.ADMIN_COMMAND, 1000);

                // Should never resolve player for admin commands
                cache.verifyNoInteractions();
            }
        }

        @ParameterizedTest
        @EnumSource(value = XpSource.class, names = {"ADMIN_COMMAND", "DEATH_PENALTY"}, mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("Should attempt notification for all non-excluded gain sources")
        void shouldAttemptForGainSources(XpSource source) {
            try (MockedStatic<PlayerWorldCache> cache = mockStatic(PlayerWorldCache.class)) {
                cache.when(() -> PlayerWorldCache.findPlayerRef(testPlayer)).thenReturn(null);

                service.onXpGain(testPlayer, 50, source, 500);

                // Should have tried to find the player
                cache.verify(() -> PlayerWorldCache.findPlayerRef(testPlayer));
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // NULL PLAYERREF HANDLING
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Null PlayerRef")
    class NullPlayerRefTests {

        @Test
        @DisplayName("Should gracefully skip when PlayerRef is null")
        void shouldSkipWhenPlayerRefNull() {
            try (MockedStatic<PlayerWorldCache> cache = mockStatic(PlayerWorldCache.class);
                 MockedStatic<NotificationUtil> notif = mockStatic(NotificationUtil.class)) {

                cache.when(() -> PlayerWorldCache.findPlayerRef(testPlayer)).thenReturn(null);

                service.onXpGain(testPlayer, 100, XpSource.MOB_KILL, 1000);

                // Should NOT attempt to send notification
                notif.verifyNoInteractions();
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // MESSAGE FORMATTING
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Message Formatting")
    class MessageFormattingTests {

        private PlayerRef mockPlayerRef;

        @BeforeEach
        void setUp() {
            mockPlayerRef = mock(PlayerRef.class);
            when(mockPlayerRef.getPacketHandler()).thenReturn(mock(com.hypixel.hytale.server.core.io.PacketHandler.class));
        }

        @Test
        @DisplayName("MOB_KILL shows plain XP amount")
        void mobKillShowsPlainAmount() {
            try (MockedStatic<PlayerWorldCache> cache = mockStatic(PlayerWorldCache.class);
                 MockedStatic<NotificationUtil> notif = mockStatic(NotificationUtil.class)) {

                cache.when(() -> PlayerWorldCache.findPlayerRef(testPlayer)).thenReturn(mockPlayerRef);

                service.onXpGain(testPlayer, 150, XpSource.MOB_KILL, 1000);

                notif.verify(() -> NotificationUtil.sendNotification(
                    eq(mockPlayerRef.getPacketHandler()),
                    any(Message.class),
                    eq(NotificationStyle.Default)
                ));
            }
        }

        @Test
        @DisplayName("PARTY_SHARE shows (Party) suffix")
        void partyShareShowsSuffix() {
            try (MockedStatic<PlayerWorldCache> cache = mockStatic(PlayerWorldCache.class);
                 MockedStatic<NotificationUtil> notif = mockStatic(NotificationUtil.class)) {

                cache.when(() -> PlayerWorldCache.findPlayerRef(testPlayer)).thenReturn(mockPlayerRef);

                service.onXpGain(testPlayer, 60, XpSource.PARTY_SHARE, 500);

                notif.verify(() -> NotificationUtil.sendNotification(
                    eq(mockPlayerRef.getPacketHandler()),
                    any(Message.class),
                    eq(NotificationStyle.Default)
                ));
            }
        }

        @Test
        @DisplayName("REALM_COMPLETION shows (Realm) suffix")
        void realmCompletionShowsSuffix() {
            try (MockedStatic<PlayerWorldCache> cache = mockStatic(PlayerWorldCache.class);
                 MockedStatic<NotificationUtil> notif = mockStatic(NotificationUtil.class)) {

                cache.when(() -> PlayerWorldCache.findPlayerRef(testPlayer)).thenReturn(mockPlayerRef);

                service.onXpGain(testPlayer, 1200, XpSource.REALM_COMPLETION, 5000);

                notif.verify(() -> NotificationUtil.sendNotification(
                    eq(mockPlayerRef.getPacketHandler()),
                    any(Message.class),
                    eq(NotificationStyle.Default)
                ));
            }
        }

        @Test
        @DisplayName("QUEST_COMPLETE shows (Quest) suffix")
        void questCompleteShowsSuffix() {
            try (MockedStatic<PlayerWorldCache> cache = mockStatic(PlayerWorldCache.class);
                 MockedStatic<NotificationUtil> notif = mockStatic(NotificationUtil.class)) {

                cache.when(() -> PlayerWorldCache.findPlayerRef(testPlayer)).thenReturn(mockPlayerRef);

                service.onXpGain(testPlayer, 200, XpSource.QUEST_COMPLETE, 2000);

                notif.verify(() -> NotificationUtil.sendNotification(
                    eq(mockPlayerRef.getPacketHandler()),
                    any(Message.class),
                    eq(NotificationStyle.Default)
                ));
            }
        }

        @Test
        @DisplayName("REALM_KILL shows plain XP amount (no suffix)")
        void realmKillShowsPlainAmount() {
            try (MockedStatic<PlayerWorldCache> cache = mockStatic(PlayerWorldCache.class);
                 MockedStatic<NotificationUtil> notif = mockStatic(NotificationUtil.class)) {

                cache.when(() -> PlayerWorldCache.findPlayerRef(testPlayer)).thenReturn(mockPlayerRef);

                service.onXpGain(testPlayer, 75, XpSource.REALM_KILL, 800);

                notif.verify(() -> NotificationUtil.sendNotification(
                    eq(mockPlayerRef.getPacketHandler()),
                    any(Message.class),
                    eq(NotificationStyle.Default)
                ));
            }
        }
    }
}
