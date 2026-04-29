package io.github.larsonix.trailoforbis.gear.listener;

import io.github.larsonix.trailoforbis.gear.sync.ItemSyncCoordinator;

import com.hypixel.hytale.event.EventRegistry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for GearEquipmentListener.
 *
 * <p>The listener's main behavior (calling syncCoordinator.markEquipmentDirty)
 * requires a full ECS environment to test (Player, InventoryChangeEvent, etc.)
 * which is only feasible in integration tests. These unit tests cover
 * construction, registration, and cleanup.
 */
@ExtendWith(MockitoExtension.class)
class GearEquipmentListenerTest {

    @Mock
    private ItemSyncCoordinator syncCoordinator;

    @Mock
    private EventRegistry eventRegistry;

    private GearEquipmentListener listener;

    @BeforeEach
    void setUp() {
        listener = new GearEquipmentListener(syncCoordinator);
    }

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("constructor accepts sync coordinator")
        void constructor_AcceptsSyncCoordinator() {
            assertDoesNotThrow(() -> new GearEquipmentListener(syncCoordinator));
        }

        @Test
        @DisplayName("constructor accepts null sync coordinator")
        void constructor_AcceptsNullSyncCoordinator() {
            assertDoesNotThrow(() -> new GearEquipmentListener(null));
        }
    }

    @Nested
    @DisplayName("Registration Tests")
    class RegistrationTests {

        @Test
        @DisplayName("register accepts valid registry")
        void register_AcceptsValidRegistry() {
            assertDoesNotThrow(() -> listener.register(eventRegistry));
        }
    }

    @Nested
    @DisplayName("Cleanup Tests")
    class CleanupTests {

        @Test
        @DisplayName("shutdown does not throw")
        void shutdown_DoesNotThrow() {
            assertDoesNotThrow(() -> listener.shutdown());
        }

        @Test
        @DisplayName("removePlayer does not throw for valid UUID")
        void removePlayer_DoesNotThrow() {
            assertDoesNotThrow(() -> listener.removePlayer(UUID.randomUUID()));
        }
    }
}
