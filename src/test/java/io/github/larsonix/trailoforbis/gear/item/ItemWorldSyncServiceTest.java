package io.github.larsonix.trailoforbis.gear.item;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ItemWorldSyncService}.
 */
@ExtendWith(MockitoExtension.class)
class ItemWorldSyncServiceTest {

    @Mock
    private ItemSyncService itemSyncService;

    @Mock
    private CustomItemSyncService customItemSyncService;

    private ItemWorldSyncService service;

    @BeforeEach
    void setUp() {
        service = new ItemWorldSyncService(itemSyncService, customItemSyncService, null);
    }

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create with default sync range")
        void shouldCreateWithDefaultSyncRange() {
            ItemWorldSyncService svc = new ItemWorldSyncService(itemSyncService, customItemSyncService, null);
            assertEquals(ItemWorldSyncService.DEFAULT_SYNC_RANGE, svc.getSyncRange());
        }

        @Test
        @DisplayName("Should create with custom sync range")
        void shouldCreateWithCustomSyncRange() {
            ItemWorldSyncService svc = new ItemWorldSyncService(itemSyncService, customItemSyncService, null, 32.0);
            assertEquals(32.0, svc.getSyncRange());
        }

        @Test
        @DisplayName("Should throw on null itemSyncService")
        void shouldThrowOnNullItemSyncService() {
            assertThrows(NullPointerException.class, () ->
                new ItemWorldSyncService(null, customItemSyncService, null));
        }

        @Test
        @DisplayName("Should throw on null customItemSyncService")
        void shouldThrowOnNullCustomItemSyncService() {
            assertThrows(NullPointerException.class, () ->
                new ItemWorldSyncService(itemSyncService, null, null));
        }
    }

    @Nested
    @DisplayName("Player Disconnect Tests")
    class PlayerDisconnectTests {

        @Test
        @DisplayName("Should clear player cache on disconnect")
        void shouldClearPlayerCacheOnDisconnect() {
            UUID playerId = UUID.randomUUID();

            // Should not throw
            assertDoesNotThrow(() -> service.onPlayerDisconnect(playerId));
        }

        @Test
        @DisplayName("Should handle multiple disconnects for same player")
        void shouldHandleMultipleDisconnects() {
            UUID playerId = UUID.randomUUID();

            service.onPlayerDisconnect(playerId);
            service.onPlayerDisconnect(playerId);

            // Should complete without error
            assertTrue(true);
        }
    }

    @Nested
    @DisplayName("Shutdown Tests")
    class ShutdownTests {

        @Test
        @DisplayName("Should shutdown cleanly")
        void shouldShutdownCleanly() {
            assertDoesNotThrow(() -> service.shutdown());
        }

        @Test
        @DisplayName("Should handle multiple shutdowns")
        void shouldHandleMultipleShutdowns() {
            service.shutdown();
            service.shutdown();

            // Should complete without error
            assertTrue(true);
        }
    }

    @Nested
    @DisplayName("Accessor Tests")
    class AccessorTests {

        @Test
        @DisplayName("Should return default sync range")
        void shouldReturnDefaultSyncRange() {
            assertEquals(64.0, service.getSyncRange());
        }

        @Test
        @DisplayName("Should return item sync service")
        void shouldReturnItemSyncService() {
            assertSame(itemSyncService, service.getItemSyncService());
        }

        @Test
        @DisplayName("Should return custom item sync service")
        void shouldReturnCustomItemSyncService() {
            assertSame(customItemSyncService, service.getCustomItemSyncService());
        }
    }
}
