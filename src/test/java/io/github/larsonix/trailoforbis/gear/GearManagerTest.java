package io.github.larsonix.trailoforbis.gear;

import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.api.ServiceRegistry;
import io.github.larsonix.trailoforbis.attributes.AttributeManager;
import io.github.larsonix.trailoforbis.attributes.AttributeType;
import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig;
import io.github.larsonix.trailoforbis.gear.config.ModifierConfig;
import io.github.larsonix.trailoforbis.gear.config.TestConfigFactory;
import io.github.larsonix.trailoforbis.gear.equipment.EquipmentValidator;
import io.github.larsonix.trailoforbis.gear.generation.GearGenerator;
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.gear.stats.GearStatCalculator;
import io.github.larsonix.trailoforbis.gear.util.GearUtils;

import com.hypixel.hytale.server.core.inventory.ItemStack;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for GearManager - 20 test cases.
 */
@ExtendWith(MockitoExtension.class)
class GearManagerTest {

    @Mock
    private TrailOfOrbis plugin;

    @Mock
    private AttributeManager attributeManager;

    @Mock
    private ItemStack mockItem;

    @BeforeEach
    void setUp() {
        // Register mock AttributeManager in ServiceRegistry
        ServiceRegistry.register(AttributeManager.class, attributeManager);
    }

    @AfterEach
    void tearDown() {
        ServiceRegistry.clear();
    }

    // =========================================================================
    // CONSTRUCTOR TESTS
    // =========================================================================

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("constructor accepts valid plugin")
        void constructor_AcceptsValidPlugin() {
            assertDoesNotThrow(() -> new GearManager(plugin));
        }

        @Test
        @DisplayName("constructor accepts plugin and path")
        void constructor_AcceptsPluginAndPath() {
            Path configDir = Path.of("config");
            assertDoesNotThrow(() -> new GearManager(plugin, configDir));
        }

        @Test
        @DisplayName("constructor throws for null plugin")
        void constructor_ThrowsForNullPlugin() {
            assertThrows(NullPointerException.class, () -> new GearManager(null));
        }

        @Test
        @DisplayName("constructor throws for null config path")
        void constructor_ThrowsForNullPath() {
            assertThrows(NullPointerException.class, () -> new GearManager(plugin, null));
        }
    }

    // =========================================================================
    // LIFECYCLE TESTS
    // =========================================================================

    @Nested
    @DisplayName("Lifecycle Tests")
    class LifecycleTests {

        @Test
        @DisplayName("isInitialized returns false before initialize")
        void isInitialized_FalseBeforeInit() {
            GearManager manager = new GearManager(plugin);
            assertFalse(manager.isInitialized());
        }

        @Test
        @DisplayName("shutdown can be called on uninitialized manager")
        void shutdown_SafeOnUninitialized() {
            GearManager manager = new GearManager(plugin);
            assertDoesNotThrow(manager::shutdown);
        }

        @Test
        @DisplayName("shutdown sets initialized to false")
        void shutdown_SetsInitializedFalse() {
            GearManager manager = new GearManager(plugin);
            // Cannot test full init without real files, but we can verify shutdown logic
            manager.shutdown();
            assertFalse(manager.isInitialized());
        }
    }

    // =========================================================================
    // INTERFACE IMPLEMENTATION TESTS
    // =========================================================================

    @Nested
    @DisplayName("GearService Implementation Tests")
    class GearServiceImplementationTests {

        @Test
        @DisplayName("GearManager implements GearService")
        void gearManager_ImplementsGearService() {
            GearManager manager = new GearManager(plugin);
            assertTrue(manager instanceof GearService);
        }

        @Test
        @DisplayName("isGear delegates to GearUtils for null")
        void isGear_DelegatesToGearUtils_Null() {
            GearManager manager = new GearManager(plugin);
            assertFalse(manager.isGear(null));
        }

        @Test
        @DisplayName("getGearData returns empty for null")
        void getGearData_ReturnsEmptyForNull() {
            GearManager manager = new GearManager(plugin);
            assertTrue(manager.getGearData(null).isEmpty());
        }

        @Test
        @DisplayName("setGearData throws for null item")
        void setGearData_ThrowsForNullItem() {
            GearManager manager = new GearManager(plugin);
            GearData data = GearData.builder().level(1).rarity(GearRarity.COMMON).build();
            assertThrows(NullPointerException.class, () -> manager.setGearData(null, data));
        }

        @Test
        @DisplayName("setGearData throws for null gearData")
        void setGearData_ThrowsForNullGearData() {
            GearManager manager = new GearManager(plugin);
            assertThrows(NullPointerException.class, () -> manager.setGearData(mockItem, null));
        }

        @Test
        @DisplayName("removeGearData throws for null item")
        void removeGearData_ThrowsForNullItem() {
            GearManager manager = new GearManager(plugin);
            assertThrows(NullPointerException.class, () -> manager.removeGearData(null));
        }
    }

    // =========================================================================
    // UNINITIALIZED STATE TESTS
    // =========================================================================

    @Nested
    @DisplayName("Uninitialized State Tests")
    class UninitializedStateTests {

        @Test
        @DisplayName("generateGear throws when not initialized")
        void generateGear_ThrowsWhenNotInitialized() {
            GearManager manager = new GearManager(plugin);
            assertThrows(IllegalStateException.class,
                    () -> manager.generateGear(mockItem, 50, "weapon"));
        }

        @Test
        @DisplayName("canEquip throws when not initialized")
        void canEquip_ThrowsWhenNotInitialized() {
            GearManager manager = new GearManager(plugin);
            assertThrows(IllegalStateException.class,
                    () -> manager.canEquip(UUID.randomUUID(), mockItem));
        }

        @Test
        @DisplayName("getGearGenerator throws when not initialized")
        void getGearGenerator_ThrowsWhenNotInitialized() {
            GearManager manager = new GearManager(plugin);
            assertThrows(IllegalStateException.class, manager::getGearGenerator);
        }

        @Test
        @DisplayName("getEquipmentValidator throws when not initialized")
        void getEquipmentValidator_ThrowsWhenNotInitialized() {
            GearManager manager = new GearManager(plugin);
            assertThrows(IllegalStateException.class, manager::getEquipmentValidator);
        }

        @Test
        @DisplayName("getStatCalculator throws when not initialized")
        void getStatCalculator_ThrowsWhenNotInitialized() {
            GearManager manager = new GearManager(plugin);
            assertThrows(IllegalStateException.class, manager::getStatCalculator);
        }

    }

    // =========================================================================
    // EXCEPTION TESTS
    // =========================================================================

    @Nested
    @DisplayName("Exception Tests")
    class ExceptionTests {

        @Test
        @DisplayName("GearInitializationException has message")
        void gearInitializationException_HasMessage() {
            GearManager.GearInitializationException ex =
                    new GearManager.GearInitializationException("test message");
            assertEquals("test message", ex.getMessage());
        }

        @Test
        @DisplayName("GearInitializationException has cause")
        void gearInitializationException_HasCause() {
            RuntimeException cause = new RuntimeException("cause");
            GearManager.GearInitializationException ex =
                    new GearManager.GearInitializationException("test message", cause);
            assertEquals(cause, ex.getCause());
        }
    }
}
