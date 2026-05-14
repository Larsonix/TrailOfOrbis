package io.github.larsonix.trailoforbis.gear.tooltip;

import com.hypixel.hytale.protocol.ItemBase;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig;
import io.github.larsonix.trailoforbis.gear.conversion.ItemClassifier;
import io.github.larsonix.trailoforbis.gear.conversion.MaterialTierMapper;
import io.github.larsonix.trailoforbis.gear.conversion.VanillaConversionConfig;
import io.github.larsonix.trailoforbis.mobs.calculator.DistanceBonusCalculator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link CraftingPreviewService} bench session lifecycle.
 *
 * <p>Covers the full state machine: open, close, idempotency, world transitions,
 * disconnects, close callback tracking, and interaction with the
 * {@code PocketCraftingPreviewWindow} direct-close pattern.
 *
 * <p>Packet sending is mocked — these tests verify state transitions and
 * call ordering, not network behavior.
 */
@DisplayName("CraftingPreviewService — Session Lifecycle")
class CraftingPreviewServiceSessionTest {

    private CraftingPreviewService service;
    private PlayerRef playerRef;
    private PacketHandler packetHandler;

    private static final UUID PLAYER_A = UUID.randomUUID();
    private static final UUID PLAYER_B = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        // Create service with mocked dependencies (only used by initialize(), which we bypass)
        service = new CraftingPreviewService(
                mock(MaterialTierMapper.class),
                mock(DistanceBonusCalculator.class),
                mock(VanillaConversionConfig.class),
                mock(ItemClassifier.class),
                mock(GearBalanceConfig.class));

        // Bypass initialize() by directly setting internal state to pass isInitialized()
        forceInitialized(service);

        // Mock player ref and packet handler
        packetHandler = mock(PacketHandler.class);
        playerRef = mock(PlayerRef.class);
        when(playerRef.getUuid()).thenReturn(PLAYER_A);
        when(playerRef.getPacketHandler()).thenReturn(packetHandler);
    }

    // ═══════════════════════════════════════════════════════════════════
    // SESSION OPEN
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("onBenchOpen")
    class OnBenchOpen {

        @Test
        @DisplayName("creates active session and sends definitions")
        void createsSession() {
            boolean sent = service.onBenchOpen(PLAYER_A, playerRef, 10);

            assertTrue(sent);
            assertTrue(service.isActiveBenchSession(PLAYER_A));
            // sendDefinitions sends UpdateItems (cachedDefinitions is non-empty)
            // sendTranslations may not send if itemPreviews is empty (internal detail)
            verify(packetHandler, atLeast(1)).writeNoCache(any());
        }

        @Test
        @DisplayName("returns false and sends nothing when not initialized")
        void notInitialized() throws Exception {
            CraftingPreviewService uninitService = new CraftingPreviewService(
                    mock(MaterialTierMapper.class), mock(DistanceBonusCalculator.class),
                    mock(VanillaConversionConfig.class), mock(ItemClassifier.class),
                    mock(GearBalanceConfig.class));
            // Don't call forceInitialized — leave cachedDefinitions null

            boolean sent = uninitService.onBenchOpen(PLAYER_A, playerRef, 10);

            assertFalse(sent);
            assertFalse(uninitService.isActiveBenchSession(PLAYER_A));
            verifyNoInteractions(packetHandler);
        }

        @Test
        @DisplayName("is idempotent — second open returns false")
        void idempotent() {
            service.onBenchOpen(PLAYER_A, playerRef, 10);
            reset(packetHandler);

            boolean secondOpen = service.onBenchOpen(PLAYER_A, playerRef, 15);

            assertFalse(secondOpen);
            verifyNoInteractions(packetHandler);
        }

        @Test
        @DisplayName("different players get independent sessions")
        void independentSessions() {
            PlayerRef playerRefB = mock(PlayerRef.class);
            PacketHandler packetHandlerB = mock(PacketHandler.class);
            when(playerRefB.getUuid()).thenReturn(PLAYER_B);
            when(playerRefB.getPacketHandler()).thenReturn(packetHandlerB);

            service.onBenchOpen(PLAYER_A, playerRef, 10);
            service.onBenchOpen(PLAYER_B, playerRefB, 20);

            assertTrue(service.isActiveBenchSession(PLAYER_A));
            assertTrue(service.isActiveBenchSession(PLAYER_B));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // SESSION CLOSE
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("onBenchClose")
    class OnBenchClose {

        @Test
        @DisplayName("clears session and restores vanilla definitions")
        void clearsSession() {
            service.onBenchOpen(PLAYER_A, playerRef, 10);
            reset(packetHandler);

            service.onBenchClose(PLAYER_A, playerRef);

            assertFalse(service.isActiveBenchSession(PLAYER_A));
            // restoreVanillaDefinitions sends one UpdateItems packet
            verify(packetHandler, times(1)).writeNoCache(any());
        }

        @Test
        @DisplayName("is idempotent — second close sends no packets")
        void idempotent() {
            service.onBenchOpen(PLAYER_A, playerRef, 10);
            service.onBenchClose(PLAYER_A, playerRef);
            reset(packetHandler);

            service.onBenchClose(PLAYER_A, playerRef);

            verifyNoInteractions(packetHandler);
            assertFalse(service.isActiveBenchSession(PLAYER_A));
        }

        @Test
        @DisplayName("clears close callback and reskin state")
        void clearsAllState() {
            service.onBenchOpen(PLAYER_A, playerRef, 10);
            service.markCloseCallbackRegistered(PLAYER_A);
            service.markReskinActive(PLAYER_A);

            service.onBenchClose(PLAYER_A, playerRef);

            assertFalse(service.isActiveBenchSession(PLAYER_A));
            assertFalse(service.needsCloseCallback(PLAYER_A));
            assertFalse(service.isReskinActive(PLAYER_A));
        }

        @Test
        @DisplayName("does not affect other players")
        void doesNotAffectOthers() {
            PlayerRef playerRefB = mock(PlayerRef.class);
            when(playerRefB.getUuid()).thenReturn(PLAYER_B);
            when(playerRefB.getPacketHandler()).thenReturn(mock(PacketHandler.class));

            service.onBenchOpen(PLAYER_A, playerRef, 10);
            service.onBenchOpen(PLAYER_B, playerRefB, 20);

            service.onBenchClose(PLAYER_A, playerRef);

            assertFalse(service.isActiveBenchSession(PLAYER_A));
            assertTrue(service.isActiveBenchSession(PLAYER_B));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // CLOSE CALLBACK TRACKING
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Close Callback Tracking")
    class CloseCallbackTracking {

        @Test
        @DisplayName("new session needs close callback")
        void newSessionNeedsCallback() {
            service.onBenchOpen(PLAYER_A, playerRef, 10);

            assertTrue(service.needsCloseCallback(PLAYER_A));
        }

        @Test
        @DisplayName("marking callback registered clears the need")
        void markingClearsNeed() {
            service.onBenchOpen(PLAYER_A, playerRef, 10);
            service.markCloseCallbackRegistered(PLAYER_A);

            assertFalse(service.needsCloseCallback(PLAYER_A));
        }

        @Test
        @DisplayName("no session means no callback needed")
        void noSessionNoCallback() {
            assertFalse(service.needsCloseCallback(PLAYER_A));
        }

        @Test
        @DisplayName("PocketCrafting pattern: markCloseCallbackRegistered prevents Phase 2 redundancy")
        void pocketCraftingPattern() {
            // Simulates PocketCraftingPreviewWindow.onOpen0():
            // 1. onBenchOpen → creates session
            // 2. markCloseCallbackRegistered → tells Phase 2 we handle close directly
            service.onBenchOpen(PLAYER_A, playerRef, 10);
            service.markCloseCallbackRegistered(PLAYER_A);

            // Phase 2 (CraftingBenchPreviewSystem.onInventoryChange) checks needsCloseCallback
            // and should NOT register another callback
            assertFalse(service.needsCloseCallback(PLAYER_A));
            assertTrue(service.isActiveBenchSession(PLAYER_A));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // WORLD TRANSITION
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("onWorldTransition")
    class WorldTransition {

        @Test
        @DisplayName("clears session without sending packets")
        void clearsWithoutPackets() {
            service.onBenchOpen(PLAYER_A, playerRef, 10);
            service.markCloseCallbackRegistered(PLAYER_A);
            service.markReskinActive(PLAYER_A);
            reset(packetHandler);

            service.onWorldTransition(PLAYER_A);

            assertFalse(service.isActiveBenchSession(PLAYER_A));
            assertFalse(service.needsCloseCallback(PLAYER_A));
            assertFalse(service.isReskinActive(PLAYER_A));
            verifyNoInteractions(packetHandler);
        }

        @Test
        @DisplayName("subsequent onBenchClose is a no-op (zero packets)")
        void subsequentCloseIsNoOp() {
            // Simulates world transition flow:
            // 1. DrainPlayerFromWorldEvent → onWorldTransition() clears session
            // 2. resetManagers() → closeAllWindows() → onClose0() → onBenchClose()
            service.onBenchOpen(PLAYER_A, playerRef, 10);
            reset(packetHandler);

            service.onWorldTransition(PLAYER_A);
            service.onBenchClose(PLAYER_A, playerRef);

            verifyNoInteractions(packetHandler);
        }

        @Test
        @DisplayName("player can open new session after transition")
        void canReopenAfterTransition() {
            service.onBenchOpen(PLAYER_A, playerRef, 10);
            service.onWorldTransition(PLAYER_A);
            reset(packetHandler);

            boolean sent = service.onBenchOpen(PLAYER_A, playerRef, 15);

            assertTrue(sent);
            assertTrue(service.isActiveBenchSession(PLAYER_A));
        }

        @Test
        @DisplayName("does not affect other players")
        void doesNotAffectOthers() {
            PlayerRef playerRefB = mock(PlayerRef.class);
            when(playerRefB.getUuid()).thenReturn(PLAYER_B);
            when(playerRefB.getPacketHandler()).thenReturn(mock(PacketHandler.class));

            service.onBenchOpen(PLAYER_A, playerRef, 10);
            service.onBenchOpen(PLAYER_B, playerRefB, 20);

            service.onWorldTransition(PLAYER_A);

            assertFalse(service.isActiveBenchSession(PLAYER_A));
            assertTrue(service.isActiveBenchSession(PLAYER_B));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // DISCONNECT
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("onPlayerDisconnect")
    class PlayerDisconnect {

        @Test
        @DisplayName("clears all state without sending packets")
        void clearsWithoutPackets() {
            service.onBenchOpen(PLAYER_A, playerRef, 10);
            service.markCloseCallbackRegistered(PLAYER_A);
            service.markReskinActive(PLAYER_A);
            reset(packetHandler);

            service.onPlayerDisconnect(PLAYER_A);

            assertFalse(service.isActiveBenchSession(PLAYER_A));
            assertFalse(service.needsCloseCallback(PLAYER_A));
            assertFalse(service.isReskinActive(PLAYER_A));
            verifyNoInteractions(packetHandler);
        }

        @Test
        @DisplayName("subsequent onBenchClose is a no-op")
        void subsequentCloseIsNoOp() {
            service.onBenchOpen(PLAYER_A, playerRef, 10);
            reset(packetHandler);

            service.onPlayerDisconnect(PLAYER_A);
            service.onBenchClose(PLAYER_A, playerRef);

            verifyNoInteractions(packetHandler);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // FULL LIFECYCLE SCENARIOS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Full Lifecycle Scenarios")
    class FullLifecycle {

        @Test
        @DisplayName("Pocket Crafting: open → close → reopen cycle")
        void pocketCraftingCycle() {
            // Open
            assertTrue(service.onBenchOpen(PLAYER_A, playerRef, 10));
            service.markCloseCallbackRegistered(PLAYER_A);
            assertTrue(service.isActiveBenchSession(PLAYER_A));
            assertFalse(service.needsCloseCallback(PLAYER_A));

            // Close (via onClose0)
            service.onBenchClose(PLAYER_A, playerRef);
            assertFalse(service.isActiveBenchSession(PLAYER_A));

            // Reopen
            assertTrue(service.onBenchOpen(PLAYER_A, playerRef, 12));
            assertTrue(service.isActiveBenchSession(PLAYER_A));
            assertTrue(service.needsCloseCallback(PLAYER_A)); // needs re-marking
        }

        @Test
        @DisplayName("Block bench (Phase 1): open → Phase 2 callback → close")
        void blockBenchPhase1And2() {
            // Phase 1: UseBlockEvent.Pre creates session
            assertTrue(service.onBenchOpen(PLAYER_A, playerRef, 10));
            assertTrue(service.needsCloseCallback(PLAYER_A));

            // Phase 2: InventoryChangeEvent registers callback
            service.markCloseCallbackRegistered(PLAYER_A);
            assertFalse(service.needsCloseCallback(PLAYER_A));

            // Close via registered callback
            service.onBenchClose(PLAYER_A, playerRef);
            assertFalse(service.isActiveBenchSession(PLAYER_A));
        }

        @Test
        @DisplayName("World transition mid-session: no packets, clean state")
        void worldTransitionMidSession() {
            service.onBenchOpen(PLAYER_A, playerRef, 10);
            service.markCloseCallbackRegistered(PLAYER_A);
            service.markReskinActive(PLAYER_A);
            reset(packetHandler);

            // World transition clears everything silently
            service.onWorldTransition(PLAYER_A);

            // Window.closeAllWindows() fires onClose0() after transition
            service.onBenchClose(PLAYER_A, playerRef);

            // Zero packets sent total
            verifyNoInteractions(packetHandler);

            // All state clean
            assertFalse(service.isActiveBenchSession(PLAYER_A));
            assertFalse(service.needsCloseCallback(PLAYER_A));
            assertFalse(service.isReskinActive(PLAYER_A));
        }

        @Test
        @DisplayName("Level-up during active session updates translations")
        void levelUpDuringSession() {
            service.onBenchOpen(PLAYER_A, playerRef, 10);

            boolean updated = service.updateTranslationsForActiveSession(PLAYER_A, playerRef, 15);

            assertTrue(updated);
            // After update, calling again with same level should be a no-op (confirms state changed)
            assertFalse(service.updateTranslationsForActiveSession(PLAYER_A, playerRef, 15));
        }

        @Test
        @DisplayName("Level-up with same level is a no-op")
        void levelUpSameLevel() {
            service.onBenchOpen(PLAYER_A, playerRef, 10);
            reset(packetHandler);

            boolean updated = service.updateTranslationsForActiveSession(PLAYER_A, playerRef, 10);

            assertFalse(updated);
            verifyNoInteractions(packetHandler);
        }

        @Test
        @DisplayName("Level-up with no active session is a no-op")
        void levelUpNoSession() {
            boolean updated = service.updateTranslationsForActiveSession(PLAYER_A, playerRef, 10);

            assertFalse(updated);
            verifyNoInteractions(packetHandler);
        }

        @Test
        @DisplayName("Two players: independent open/close/transition cycles")
        void twoPlayersIndependent() {
            PlayerRef playerRefB = mock(PlayerRef.class);
            PacketHandler packetHandlerB = mock(PacketHandler.class);
            when(playerRefB.getUuid()).thenReturn(PLAYER_B);
            when(playerRefB.getPacketHandler()).thenReturn(packetHandlerB);

            // A opens, B opens
            service.onBenchOpen(PLAYER_A, playerRef, 10);
            service.onBenchOpen(PLAYER_B, playerRefB, 20);

            // A transitions, B stays
            service.onWorldTransition(PLAYER_A);
            assertFalse(service.isActiveBenchSession(PLAYER_A));
            assertTrue(service.isActiveBenchSession(PLAYER_B));

            // A reopens at new level
            assertTrue(service.onBenchOpen(PLAYER_A, playerRef, 15));

            // B closes normally
            service.onBenchClose(PLAYER_B, playerRefB);
            assertTrue(service.isActiveBenchSession(PLAYER_A));
            assertFalse(service.isActiveBenchSession(PLAYER_B));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // TEST HELPERS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Sets internal state so {@link CraftingPreviewService#isInitialized()} returns true
     * without calling {@code initialize()}, which requires the full Hytale asset system.
     */
    private static void forceInitialized(CraftingPreviewService service) throws Exception {
        setField(service, "cachedDefinitions", Map.of("dummy_item", new ItemBase()));
        setField(service, "originalDefinitions", Map.of("dummy_item", new ItemBase()));
        setField(service, "itemPreviews", new HashMap<>());
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
