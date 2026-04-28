package io.github.larsonix.trailoforbis.gear.listener;

import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig;
import io.github.larsonix.trailoforbis.gear.config.ModifierConfig;
import io.github.larsonix.trailoforbis.gear.config.TestConfigFactory;
import io.github.larsonix.trailoforbis.gear.equipment.EquipmentValidator;
import io.github.larsonix.trailoforbis.gear.stats.GearStatApplier;
import io.github.larsonix.trailoforbis.gear.stats.GearStatCalculator;

import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for GearEquipmentListener - 10 test cases.
 */
@ExtendWith(MockitoExtension.class)
class GearEquipmentListenerTest {

    @Mock
    private GearStatCalculator statCalculator;

    @Mock
    private GearStatApplier statApplier;

    @Mock
    private EventRegistry eventRegistry;

    @Mock
    private Player player;

    @Mock
    private Inventory inventory;

    private GearEquipmentListener listener;

    @BeforeEach
    void setUp() {
        listener = new GearEquipmentListener(statCalculator, statApplier);
    }

    // =========================================================================
    // CONSTRUCTOR TESTS
    // =========================================================================

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("constructor accepts valid dependencies")
        void constructor_AcceptsValidDependencies() {
            assertDoesNotThrow(() ->
                    new GearEquipmentListener(statCalculator, statApplier));
        }

        @Test
        @DisplayName("constructor throws for null statCalculator")
        void constructor_ThrowsForNullStatCalculator() {
            assertThrows(NullPointerException.class, () ->
                    new GearEquipmentListener(null, statApplier));
        }

        @Test
        @DisplayName("constructor throws for null statApplier")
        void constructor_ThrowsForNullStatApplier() {
            assertThrows(NullPointerException.class, () ->
                    new GearEquipmentListener(statCalculator, null));
        }
    }

    // =========================================================================
    // REGISTRATION TESTS
    // =========================================================================

    @Nested
    @DisplayName("Registration Tests")
    class RegistrationTests {

        @Test
        @DisplayName("register throws for null registry")
        void register_ThrowsForNullRegistry() {
            assertThrows(NullPointerException.class, () -> listener.register(null));
        }

        @Test
        @DisplayName("register accepts valid registry")
        void register_AcceptsValidRegistry() {
            assertDoesNotThrow(() -> listener.register(eventRegistry));
        }
    }

    // =========================================================================
    // DEBOUNCE TESTS
    // =========================================================================

    @Nested
    @DisplayName("Debounce Tests")
    class DebounceTests {

        @Test
        @DisplayName("debounce is enabled by default")
        void debounce_EnabledByDefault() {
            assertTrue(listener.isDebounceEnabled());
        }

        @Test
        @DisplayName("setDebounceEnabled can disable debouncing")
        void setDebounceEnabled_CanDisable() {
            listener.setDebounceEnabled(false);
            assertFalse(listener.isDebounceEnabled());
        }

        @Test
        @DisplayName("setDebounceEnabled can re-enable debouncing")
        void setDebounceEnabled_CanReEnable() {
            listener.setDebounceEnabled(false);
            listener.setDebounceEnabled(true);
            assertTrue(listener.isDebounceEnabled());
        }
    }

    // =========================================================================
    // FORCE RECALCULATION TESTS
    // =========================================================================

    @Nested
    @DisplayName("Force Recalculation Tests")
    class ForceRecalculationTests {

        @Test
        @DisplayName("forceRecalculation throws for null playerId")
        void forceRecalculation_ThrowsForNullPlayerId() {
            assertThrows(NullPointerException.class, () ->
                    listener.forceRecalculation((UUID) null, player));
        }

        @Test
        @DisplayName("forceRecalculation throws for null player")
        void forceRecalculation_ThrowsForNullPlayer() {
            assertThrows(NullPointerException.class, () ->
                    listener.forceRecalculation(UUID.randomUUID(), null));
        }
    }

    // =========================================================================
    // CLEANUP TESTS
    // =========================================================================

    @Nested
    @DisplayName("Cleanup Tests")
    class CleanupTests {

        @Test
        @DisplayName("clearPending does not throw")
        void clearPending_DoesNotThrow() {
            assertDoesNotThrow(() -> listener.clearPending());
        }

        @Test
        @DisplayName("removePlayer does not throw for valid UUID")
        void removePlayer_DoesNotThrow() {
            assertDoesNotThrow(() -> listener.removePlayer(UUID.randomUUID()));
        }
    }
}
