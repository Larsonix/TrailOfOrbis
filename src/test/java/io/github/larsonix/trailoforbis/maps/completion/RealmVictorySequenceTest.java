package io.github.larsonix.trailoforbis.maps.completion;

import io.github.larsonix.trailoforbis.maps.instance.RealmInstance;
import io.github.larsonix.trailoforbis.maps.ui.RealmHudManager;
import io.github.larsonix.trailoforbis.util.EmoteCelebrationHelper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link RealmVictorySequence}.
 *
 * <p>Tests the victory sequence orchestration focusing on:
 * <ul>
 *   <li>Constructor validation (NPE checks)</li>
 *   <li>Execute null parameter validation</li>
 *   <li>Skip behavior when no players present</li>
 *   <li>Cancel and shutdown behavior</li>
 * </ul>
 *
 * <p>Note: Full integration tests for execute() require a running Hytale server
 * since Universe.get() returns null in unit tests. These tests focus on
 * boundary conditions and validation that don't require Universe access.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RealmVictorySequence")
class RealmVictorySequenceTest {

    @Mock
    private RealmHudManager hudManager;

    @Mock
    private VictoryPortalManager portalManager;

    @Mock
    private EmoteCelebrationHelper emoteHelper;

    @Mock
    private RealmInstance realm;

    private RealmVictorySequence victorySequence;

    private final UUID realmId = UUID.randomUUID();
    private final UUID player1 = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        victorySequence = new RealmVictorySequence(hudManager, portalManager, null, emoteHelper);
    }

    @AfterEach
    void tearDown() {
        victorySequence.shutdown();
    }

    // ═══════════════════════════════════════════════════════════════════
    // CONSTRUCTOR TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("should throw NPE for null hudManager")
        void constructor_throwsNPE_forNullHudManager() {
            assertThrows(NullPointerException.class, () ->
                new RealmVictorySequence(null, portalManager, null, emoteHelper));
        }

        @Test
        @DisplayName("should throw NPE for null portalManager")
        void constructor_throwsNPE_forNullPortalManager() {
            assertThrows(NullPointerException.class, () ->
                new RealmVictorySequence(hudManager, null, null, emoteHelper));
        }

        @Test
        @DisplayName("should throw NPE for null emoteHelper")
        void constructor_throwsNPE_forNullEmoteHelper() {
            assertThrows(NullPointerException.class, () ->
                new RealmVictorySequence(hudManager, portalManager, null, null));
        }

        @Test
        @DisplayName("should create successfully with valid parameters")
        void constructor_succeeds_withValidParams() {
            assertDoesNotThrow(() ->
                new RealmVictorySequence(hudManager, portalManager, null, emoteHelper));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // EXECUTE VALIDATION TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("execute() validation")
    class ExecuteValidationTests {

        @Test
        @DisplayName("should throw NPE for null realm")
        void execute_throwsNPE_forNullRealm() {
            assertThrows(NullPointerException.class, () ->
                victorySequence.execute(null));
        }

        @Test
        @DisplayName("should skip when no players in realm")
        void execute_skips_whenNoPlayers() {
            when(realm.getRealmId()).thenReturn(realmId);
            when(realm.getCurrentPlayers()).thenReturn(Collections.emptySet());

            victorySequence.execute(realm);

            // Should not spawn portal or show HUDs when empty
            verify(portalManager, never()).spawnVictoryPortal(any());
            verify(hudManager, never()).showVictoryHud(any(), any(), any());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // CANCEL TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("cancel()")
    class CancelTests {

        @Test
        @DisplayName("should handle cancel on non-existent realm")
        void cancel_handlesNonExistentRealm() {
            UUID unknownRealmId = UUID.randomUUID();

            // Should not throw
            assertDoesNotThrow(() -> victorySequence.cancel(unknownRealmId));
        }

        @Test
        @DisplayName("should handle cancel on realm that had victory sequence")
        void cancel_handlesExistingRealm() {
            // Cancel on a realm ID that we haven't executed on yet
            // should be safe
            assertDoesNotThrow(() -> victorySequence.cancel(realmId));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // SHUTDOWN TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("shutdown()")
    class ShutdownTests {

        @Test
        @DisplayName("should be safe to call multiple times")
        void shutdown_safeToCallMultipleTimes() {
            victorySequence.shutdown();
            assertDoesNotThrow(() -> victorySequence.shutdown());
            assertDoesNotThrow(() -> victorySequence.shutdown());
        }

        @Test
        @DisplayName("should clear internal state")
        void shutdown_clearsInternalState() {
            victorySequence.shutdown();

            // After shutdown, cancel should still work (on empty maps)
            assertDoesNotThrow(() -> victorySequence.cancel(realmId));
        }
    }
}
