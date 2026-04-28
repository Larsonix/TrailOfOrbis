package io.github.larsonix.trailoforbis.maps.instance;

import com.hypixel.hytale.math.vector.Vector3i;
import io.github.larsonix.trailoforbis.maps.instance.RealmPortalManager.PortalPosition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link RealmPortalManager}.
 *
 * <p>These tests focus on the testable tracking logic:
 * <ul>
 *   <li>Portal timeout configuration</li>
 *   <li>PortalPosition record validation</li>
 *   <li>Cleanup operations</li>
 * </ul>
 *
 * <p>Note: Tests involving World objects cannot be unit tested because
 * WorldConfig has static initializers that require the Hytale runtime.
 * Portal tracking tests that use World are covered by integration tests.
 */
@DisplayName("RealmPortalManager Tests")
class RealmPortalManagerTest {

    private RealmPortalManager portalManager;
    private UUID worldUuid;
    private Vector3i position;

    @BeforeEach
    void setUp() {
        portalManager = new RealmPortalManager();
        worldUuid = UUID.randomUUID();
        position = new Vector3i(100, 64, -200);
    }

    // ═══════════════════════════════════════════════════════════════════
    // TIMEOUT CONFIGURATION TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Timeout Configuration")
    class TimeoutConfigurationTests {

        @Test
        @DisplayName("default timeout is 120 seconds")
        void defaultTimeout_is120Seconds() {
            assertEquals(120, portalManager.getPortalEntryTimeoutSeconds());
        }

        @Test
        @DisplayName("setPortalEntryTimeoutSeconds changes timeout")
        void setPortalEntryTimeoutSeconds_changesTimeout() {
            portalManager.setPortalEntryTimeoutSeconds(300);

            assertEquals(300, portalManager.getPortalEntryTimeoutSeconds());
        }

        @Test
        @DisplayName("setPortalEntryTimeoutSeconds enforces minimum of 10 seconds")
        void setPortalEntryTimeoutSeconds_enforcesMinimum() {
            portalManager.setPortalEntryTimeoutSeconds(5);

            assertEquals(10, portalManager.getPortalEntryTimeoutSeconds());
        }

        @Test
        @DisplayName("setPortalEntryTimeoutSeconds accepts exactly 10 seconds")
        void setPortalEntryTimeoutSeconds_acceptsExactlyTen() {
            portalManager.setPortalEntryTimeoutSeconds(10);

            assertEquals(10, portalManager.getPortalEntryTimeoutSeconds());
        }

        @Test
        @DisplayName("setPortalEntryTimeoutSeconds accepts large values")
        void setPortalEntryTimeoutSeconds_acceptsLargeValues() {
            portalManager.setPortalEntryTimeoutSeconds(3600);

            assertEquals(3600, portalManager.getPortalEntryTimeoutSeconds());
        }

        @Test
        @DisplayName("setPortalEntryTimeoutSeconds with negative value enforces minimum")
        void setPortalEntryTimeoutSeconds_negativeValue_enforcesMinimum() {
            portalManager.setPortalEntryTimeoutSeconds(-100);

            assertEquals(10, portalManager.getPortalEntryTimeoutSeconds());
        }

        @Test
        @DisplayName("setPortalEntryTimeoutSeconds with zero enforces minimum")
        void setPortalEntryTimeoutSeconds_zero_enforcesMinimum() {
            portalManager.setPortalEntryTimeoutSeconds(0);

            assertEquals(10, portalManager.getPortalEntryTimeoutSeconds());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // CLEANUP TESTS (without World)
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Cleanup Operations")
    class CleanupTests {

        @Test
        @DisplayName("clearTracking on empty manager doesn't throw")
        void clearTracking_emptyManager_doesNotThrow() {
            assertDoesNotThrow(() -> portalManager.clearTracking());
        }

        @Test
        @DisplayName("processPortalTimeouts returns 0 when no portals exist")
        void processPortalTimeouts_noPortals_returnsZero() {
            int removed = portalManager.processPortalTimeouts();

            assertEquals(0, removed);
        }

        @Test
        @DisplayName("getPortalCount returns 0 for new manager")
        void getPortalCount_newManager_returnsZero() {
            assertEquals(0, portalManager.getPortalCount());
        }

        @Test
        @DisplayName("getPortalsForRealm returns empty for unknown realm")
        void getPortalsForRealm_unknownRealm_returnsEmpty() {
            assertTrue(portalManager.getPortalsForRealm(UUID.randomUUID()).isEmpty());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // PORTAL POSITION RECORD TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("PortalPosition Record")
    class PortalPositionTests {

        @Test
        @DisplayName("PortalPosition stores world UUID and position")
        void portalPosition_storesData() {
            PortalPosition portal = new PortalPosition(worldUuid, position);

            assertEquals(worldUuid, portal.worldUuid());
            assertEquals(position, portal.position());
        }

        @Test
        @DisplayName("toKey creates unique string key")
        void toKey_createsUniqueKey() {
            PortalPosition portal = new PortalPosition(worldUuid, position);

            String key = portal.toKey();

            assertTrue(key.contains(worldUuid.toString()));
            assertTrue(key.contains("100")); // x
            assertTrue(key.contains("64"));  // y
            assertTrue(key.contains("-200")); // z
        }

        @Test
        @DisplayName("toKey format is worldUuid:x:y:z")
        void toKey_hasCorrectFormat() {
            PortalPosition portal = new PortalPosition(worldUuid, position);

            String key = portal.toKey();
            String expected = worldUuid + ":100:64:-200";

            assertEquals(expected, key);
        }

        @Test
        @DisplayName("PortalPosition rejects null world UUID")
        void portalPosition_rejectsNullWorldUuid() {
            assertThrows(NullPointerException.class, () ->
                new PortalPosition(null, position));
        }

        @Test
        @DisplayName("PortalPosition rejects null position")
        void portalPosition_rejectsNullPosition() {
            assertThrows(NullPointerException.class, () ->
                new PortalPosition(worldUuid, null));
        }

        @Test
        @DisplayName("PortalPosition equals works correctly")
        void portalPosition_equalsWorks() {
            PortalPosition portal1 = new PortalPosition(worldUuid, position);
            PortalPosition portal2 = new PortalPosition(worldUuid, position);
            PortalPosition portal3 = new PortalPosition(worldUuid, new Vector3i(0, 0, 0));

            assertEquals(portal1, portal2);
            assertNotEquals(portal1, portal3);
        }

        @Test
        @DisplayName("PortalPosition equals with different world UUID")
        void portalPosition_differentWorldUuid_notEquals() {
            PortalPosition portal1 = new PortalPosition(worldUuid, position);
            PortalPosition portal2 = new PortalPosition(UUID.randomUUID(), position);

            assertNotEquals(portal1, portal2);
        }

        @Test
        @DisplayName("PortalPosition hashCode is consistent with equals")
        void portalPosition_hashCodeConsistent() {
            PortalPosition portal1 = new PortalPosition(worldUuid, position);
            PortalPosition portal2 = new PortalPosition(worldUuid, position);

            assertEquals(portal1.hashCode(), portal2.hashCode());
        }

        @Test
        @DisplayName("PortalPosition hashCode differs for different positions")
        void portalPosition_hashCodeDiffers() {
            PortalPosition portal1 = new PortalPosition(worldUuid, position);
            PortalPosition portal2 = new PortalPosition(worldUuid, new Vector3i(999, 999, 999));

            // Note: Hash codes CAN collide, but for very different values they typically won't
            assertNotEquals(portal1.hashCode(), portal2.hashCode());
        }

        @Test
        @DisplayName("PortalPosition with extreme coordinates")
        void portalPosition_extremeCoordinates() {
            Vector3i extremePos = new Vector3i(Integer.MAX_VALUE, Integer.MIN_VALUE, 0);
            PortalPosition portal = new PortalPosition(worldUuid, extremePos);

            assertEquals(extremePos, portal.position());
            String key = portal.toKey();
            assertTrue(key.contains(String.valueOf(Integer.MAX_VALUE)));
            assertTrue(key.contains(String.valueOf(Integer.MIN_VALUE)));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // MANAGER INSTANCE TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Manager Instance")
    class ManagerInstanceTests {

        @Test
        @DisplayName("New manager has zero portals")
        void newManager_hasZeroPortals() {
            RealmPortalManager manager = new RealmPortalManager();
            assertEquals(0, manager.getPortalCount());
        }

        @Test
        @DisplayName("New manager has default timeout")
        void newManager_hasDefaultTimeout() {
            RealmPortalManager manager = new RealmPortalManager();
            assertEquals(120, manager.getPortalEntryTimeoutSeconds());
        }

        @Test
        @DisplayName("Multiple managers are independent")
        void multipleManagers_areIndependent() {
            RealmPortalManager manager1 = new RealmPortalManager();
            RealmPortalManager manager2 = new RealmPortalManager();

            manager1.setPortalEntryTimeoutSeconds(300);

            assertEquals(300, manager1.getPortalEntryTimeoutSeconds());
            assertEquals(120, manager2.getPortalEntryTimeoutSeconds());
        }
    }
}
