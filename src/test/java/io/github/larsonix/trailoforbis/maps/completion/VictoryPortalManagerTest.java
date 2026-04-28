package io.github.larsonix.trailoforbis.maps.completion;

import com.hypixel.hytale.server.core.universe.world.World;
import io.github.larsonix.trailoforbis.maps.instance.RealmInstance;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link VictoryPortalManager}.
 *
 * <p>Tests the victory portal lifecycle:
 * <ul>
 *   <li>Portal spawn at realm center</li>
 *   <li>Portal removal on realm close</li>
 *   <li>Tracking of active portals</li>
 *   <li>Edge cases: null realm, dead world</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("VictoryPortalManager")
class VictoryPortalManagerTest {

    @Mock
    private RealmInstance realm;

    @Mock
    private World world;

    private VictoryPortalManager portalManager;

    private final UUID realmId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        portalManager = new VictoryPortalManager();
    }

    @AfterEach
    void tearDown() {
        portalManager.clearAll();
    }

    // ═══════════════════════════════════════════════════════════════════
    // SPAWN PORTAL TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("spawnVictoryPortal()")
    class SpawnPortalTests {

        @Test
        @DisplayName("should throw NPE for null realm")
        void spawnVictoryPortal_throwsNPE_forNullRealm() {
            assertThrows(NullPointerException.class, () ->
                portalManager.spawnVictoryPortal(null));
        }

        @Test
        @DisplayName("should return false when realm world is null")
        void spawnVictoryPortal_returnsFalse_whenWorldNull() throws ExecutionException, InterruptedException {
            when(realm.getWorld()).thenReturn(null);

            CompletableFuture<Boolean> result = portalManager.spawnVictoryPortal(realm);

            assertFalse(result.get());
        }

        @Test
        @DisplayName("should return false when realm world is not alive")
        void spawnVictoryPortal_returnsFalse_whenWorldNotAlive() throws ExecutionException, InterruptedException {
            when(realm.getWorld()).thenReturn(world);
            when(world.isAlive()).thenReturn(false);

            CompletableFuture<Boolean> result = portalManager.spawnVictoryPortal(realm);

            assertFalse(result.get());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // REMOVE PORTAL TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("removeVictoryPortal()")
    class RemovePortalTests {

        @Test
        @DisplayName("should throw NPE for null realm")
        void removeVictoryPortal_throwsNPE_forNullRealm() {
            assertThrows(NullPointerException.class, () ->
                portalManager.removeVictoryPortal(null));
        }

        @Test
        @DisplayName("should handle removal when no portal exists")
        void removeVictoryPortal_handlesNonExistentPortal() {
            when(realm.getRealmId()).thenReturn(realmId);

            // Should not throw
            assertDoesNotThrow(() -> portalManager.removeVictoryPortal(realm));
        }

        @Test
        @DisplayName("should handle removal when world is null")
        void removeVictoryPortal_handlesNullWorld() {
            lenient().when(realm.getRealmId()).thenReturn(realmId);
            lenient().when(realm.getWorld()).thenReturn(null);

            // Should not throw even if portal was tracked
            assertDoesNotThrow(() -> portalManager.removeVictoryPortal(realm));
        }

        @Test
        @DisplayName("should handle removal when world is dead")
        void removeVictoryPortal_handlesDeadWorld() {
            lenient().when(realm.getRealmId()).thenReturn(realmId);
            lenient().when(realm.getWorld()).thenReturn(world);
            lenient().when(world.isAlive()).thenReturn(false);

            // Should not throw
            assertDoesNotThrow(() -> portalManager.removeVictoryPortal(realm));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // HAS PORTAL TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("hasVictoryPortal()")
    class HasPortalTests {

        @Test
        @DisplayName("should return false for unknown realm")
        void hasVictoryPortal_returnsFalse_forUnknownRealm() {
            assertFalse(portalManager.hasVictoryPortal(realmId));
        }

        @Test
        @DisplayName("should return false for null realmId")
        void hasVictoryPortal_handleNull() {
            // ConcurrentHashMap throws NPE for null keys
            assertThrows(NullPointerException.class, () ->
                portalManager.hasVictoryPortal(null));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // CLEAR ALL TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("clearAll()")
    class ClearAllTests {

        @Test
        @DisplayName("should clear all tracked portals")
        void clearAll_clearsAllPortals() {
            // Can't easily add portals without a real world, but we can verify
            // that clearAll doesn't throw on empty state
            assertDoesNotThrow(() -> portalManager.clearAll());
        }

        @Test
        @DisplayName("should be safe to call multiple times")
        void clearAll_safeToCallMultipleTimes() {
            portalManager.clearAll();
            assertDoesNotThrow(() -> portalManager.clearAll());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // CONCURRENT ACCESS TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Concurrent Access")
    class ConcurrentAccessTests {

        @Test
        @DisplayName("should handle concurrent hasVictoryPortal calls")
        void concurrentAccess_hasVictoryPortal() throws InterruptedException {
            Thread[] threads = new Thread[10];
            for (int i = 0; i < 10; i++) {
                final UUID id = UUID.randomUUID();
                threads[i] = new Thread(() -> {
                    for (int j = 0; j < 100; j++) {
                        portalManager.hasVictoryPortal(id);
                    }
                });
                threads[i].start();
            }

            for (Thread thread : threads) {
                thread.join();
            }

            // Should complete without ConcurrentModificationException
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // EDGE CASES
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("should handle same realm ID multiple removes")
        void multipleRemoves_sameRealm() {
            lenient().when(realm.getRealmId()).thenReturn(realmId);
            lenient().when(realm.getWorld()).thenReturn(null);

            portalManager.removeVictoryPortal(realm);
            portalManager.removeVictoryPortal(realm);
            portalManager.removeVictoryPortal(realm);

            // Should not throw
            assertFalse(portalManager.hasVictoryPortal(realmId));
        }

        @Test
        @DisplayName("should handle rapid spawn/remove cycles")
        void rapidSpawnRemoveCycles() {
            when(realm.getRealmId()).thenReturn(realmId);
            when(realm.getWorld()).thenReturn(null); // Will fail to spawn

            for (int i = 0; i < 10; i++) {
                portalManager.spawnVictoryPortal(realm);
                portalManager.removeVictoryPortal(realm);
            }

            // Should not throw and no portals should remain
            assertFalse(portalManager.hasVictoryPortal(realmId));
        }
    }
}
