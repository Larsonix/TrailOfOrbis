package io.github.larsonix.trailoforbis.maps.integration;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.maps.RealmsManager;
import io.github.larsonix.trailoforbis.maps.components.RealmMobComponent;
import io.github.larsonix.trailoforbis.maps.core.RealmBiomeType;
import io.github.larsonix.trailoforbis.maps.core.RealmLayoutShape;
import io.github.larsonix.trailoforbis.maps.core.RealmLayoutSize;
import io.github.larsonix.trailoforbis.maps.core.RealmMapData;
import io.github.larsonix.trailoforbis.maps.instance.RealmInstance;
import io.github.larsonix.trailoforbis.maps.modifiers.RealmModifier;
import io.github.larsonix.trailoforbis.maps.modifiers.RealmModifierType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RealmScalingIntegration}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RealmScalingIntegration")
class RealmScalingIntegrationTest {

    @Mock
    private RealmsManager realmsManager;

    @Mock
    private RealmInstance realmInstance;

    @Mock
    private Ref<EntityStore> mobRef;

    @Mock
    private ComponentAccessor<EntityStore> accessor;

    @Mock
    private RealmMobComponent realmMobComponent;

    @Mock
    private ComponentType<EntityStore, RealmMobComponent> componentType;

    private RealmScalingIntegration integration;
    private UUID realmId;

    @BeforeEach
    void setUp() {
        // Set the static TYPE field so getComponentType() doesn't need the plugin
        RealmMobComponent.TYPE = componentType;
        integration = new RealmScalingIntegration(realmsManager);
        realmId = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        // Clean up the static field
        RealmMobComponent.TYPE = null;
    }

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("Should reject null realmsManager")
        void shouldRejectNullRealmsManager() {
            assertThrows(NullPointerException.class, () -> new RealmScalingIntegration(null));
        }
    }

    @Nested
    @DisplayName("getMobLevelOverride")
    class MobLevelOverrideTests {

        @Test
        @DisplayName("Should return null for invalid mob ref")
        void shouldReturnNullForInvalidRef() {
            when(mobRef.isValid()).thenReturn(false);

            Integer override = integration.getMobLevelOverride(mobRef, accessor);

            assertNull(override);
        }

        @Test
        @DisplayName("Should return null when mob has no RealmMobComponent")
        void shouldReturnNullWhenNoComponent() {
            when(mobRef.isValid()).thenReturn(true);
            when(accessor.getComponent(eq(mobRef), any(ComponentType.class))).thenReturn(null);

            Integer override = integration.getMobLevelOverride(mobRef, accessor);

            assertNull(override);
        }

        @Test
        @DisplayName("Should return null when component has no realm ID")
        void shouldReturnNullWhenNoRealmId() {
            when(mobRef.isValid()).thenReturn(true);
            when(accessor.getComponent(eq(mobRef), any(ComponentType.class))).thenReturn(realmMobComponent);
            when(realmMobComponent.getRealmId()).thenReturn(null);

            Integer override = integration.getMobLevelOverride(mobRef, accessor);

            assertNull(override);
        }

        @Test
        @DisplayName("Should return null when realm no longer exists")
        void shouldReturnNullWhenRealmGone() {
            when(mobRef.isValid()).thenReturn(true);
            when(accessor.getComponent(eq(mobRef), any(ComponentType.class))).thenReturn(realmMobComponent);
            when(realmMobComponent.getRealmId()).thenReturn(realmId);
            when(realmsManager.getRealm(realmId)).thenReturn(Optional.empty());

            Integer override = integration.getMobLevelOverride(mobRef, accessor);

            assertNull(override);
        }

        @Test
        @DisplayName("Should return realm level when mob is valid realm mob")
        void shouldReturnRealmLevel() {
            RealmMapData mapData = createMapDataWithLevel(15);
            when(mobRef.isValid()).thenReturn(true);
            when(accessor.getComponent(eq(mobRef), any(ComponentType.class))).thenReturn(realmMobComponent);
            when(realmMobComponent.getRealmId()).thenReturn(realmId);
            when(realmsManager.getRealm(realmId)).thenReturn(Optional.of(realmInstance));
            when(realmInstance.getMapData()).thenReturn(mapData);

            Integer override = integration.getMobLevelOverride(mobRef, accessor);

            assertEquals(15, override);
        }

        @Test
        @DisplayName("Should reject null parameters")
        void shouldRejectNullParameters() {
            assertThrows(NullPointerException.class, () -> integration.getMobLevelOverride(null, accessor));
            assertThrows(NullPointerException.class, () -> integration.getMobLevelOverride(mobRef, null));
        }
    }

    @Nested
    @DisplayName("getRealmLevel")
    class GetRealmLevelTests {

        @Test
        @DisplayName("Should return realm level when found")
        void shouldReturnRealmLevel() {
            RealmMapData mapData = createMapDataWithLevel(20);
            when(realmsManager.getRealm(realmId)).thenReturn(Optional.of(realmInstance));
            when(realmInstance.getMapData()).thenReturn(mapData);

            int level = integration.getRealmLevel(realmId);

            assertEquals(20, level);
        }

        @Test
        @DisplayName("Should return 1 when realm not found")
        void shouldReturnOneWhenNotFound() {
            when(realmsManager.getRealm(realmId)).thenReturn(Optional.empty());

            int level = integration.getRealmLevel(realmId);

            assertEquals(1, level);
        }
    }

    @Nested
    @DisplayName("getHealthMultiplier")
    class HealthMultiplierTests {

        @Test
        @DisplayName("Should return 1.0 when realm not found")
        void shouldReturnOneWhenNotFound() {
            when(realmsManager.getRealm(realmId)).thenReturn(Optional.empty());

            float multiplier = integration.getHealthMultiplier(realmId);

            assertEquals(1.0f, multiplier);
        }

        @Test
        @DisplayName("Should apply MONSTER_HEALTH modifier")
        void shouldApplyHealthModifier() {
            RealmMapData mapData = createMapDataWithModifier(RealmModifierType.MONSTER_HEALTH, 50);
            when(realmsManager.getRealm(realmId)).thenReturn(Optional.of(realmInstance));
            when(realmInstance.getMapData()).thenReturn(mapData);

            float multiplier = integration.getHealthMultiplier(realmId);

            assertEquals(1.5f, multiplier, 0.001f);
        }
    }

    @Nested
    @DisplayName("getDamageMultiplier")
    class DamageMultiplierTests {

        @Test
        @DisplayName("Should return 1.0 when realm not found")
        void shouldReturnOneWhenNotFound() {
            when(realmsManager.getRealm(realmId)).thenReturn(Optional.empty());

            float multiplier = integration.getDamageMultiplier(realmId);

            assertEquals(1.0f, multiplier);
        }

        @Test
        @DisplayName("Should apply MONSTER_DAMAGE modifier")
        void shouldApplyDamageModifier() {
            RealmMapData mapData = createMapDataWithModifier(RealmModifierType.MONSTER_DAMAGE, 30);
            when(realmsManager.getRealm(realmId)).thenReturn(Optional.of(realmInstance));
            when(realmInstance.getMapData()).thenReturn(mapData);

            float multiplier = integration.getDamageMultiplier(realmId);

            assertEquals(1.3f, multiplier, 0.001f);
        }
    }

    @Nested
    @DisplayName("getSpeedMultiplier")
    class SpeedMultiplierTests {

        @Test
        @DisplayName("Should return 1.0 when realm not found")
        void shouldReturnOneWhenNotFound() {
            when(realmsManager.getRealm(realmId)).thenReturn(Optional.empty());

            float multiplier = integration.getSpeedMultiplier(realmId);

            assertEquals(1.0f, multiplier);
        }

        @Test
        @DisplayName("Should apply MONSTER_SPEED modifier")
        void shouldApplySpeedModifier() {
            RealmMapData mapData = createMapDataWithModifier(RealmModifierType.MONSTER_SPEED, 20);
            when(realmsManager.getRealm(realmId)).thenReturn(Optional.of(realmInstance));
            when(realmInstance.getMapData()).thenReturn(mapData);

            float multiplier = integration.getSpeedMultiplier(realmId);

            assertEquals(1.2f, multiplier, 0.001f);
        }
    }

    @Nested
    @DisplayName("getMobModifiers")
    class GetMobModifiersTests {

        @Test
        @DisplayName("Should return NONE when realm not found")
        void shouldReturnNoneWhenNotFound() {
            when(realmsManager.getRealm(realmId)).thenReturn(Optional.empty());

            RealmScalingIntegration.MobModifiers mods = integration.getMobModifiers(realmId);

            assertEquals(RealmScalingIntegration.MobModifiers.NONE, mods);
        }

        @Test
        @DisplayName("Should return combined modifiers")
        void shouldReturnCombinedModifiers() {
            RealmMapData mapData = createMapDataWithModifiers(List.of(
                new RealmModifier(RealmModifierType.MONSTER_HEALTH, 40, false),
                new RealmModifier(RealmModifierType.MONSTER_DAMAGE, 25, false),
                new RealmModifier(RealmModifierType.MONSTER_SPEED, 15, false)
            ));
            when(realmsManager.getRealm(realmId)).thenReturn(Optional.of(realmInstance));
            when(realmInstance.getMapData()).thenReturn(mapData);

            RealmScalingIntegration.MobModifiers mods = integration.getMobModifiers(realmId);

            assertEquals(10, mods.level());  // from createMapDataWithModifiers
            assertEquals(1.4f, mods.healthMultiplier(), 0.001f);
            assertEquals(1.25f, mods.damageMultiplier(), 0.001f);
            assertEquals(1.15f, mods.speedMultiplier(), 0.001f);
        }

        @Test
        @DisplayName("Should sum multiple modifiers of same type")
        void shouldSumMultipleModifiers() {
            RealmMapData mapData = createMapDataWithModifiers(List.of(
                new RealmModifier(RealmModifierType.MONSTER_HEALTH, 20, false),
                new RealmModifier(RealmModifierType.MONSTER_HEALTH, 30, false)
            ));
            when(realmsManager.getRealm(realmId)).thenReturn(Optional.of(realmInstance));
            when(realmInstance.getMapData()).thenReturn(mapData);

            RealmScalingIntegration.MobModifiers mods = integration.getMobModifiers(realmId);

            assertEquals(1.5f, mods.healthMultiplier(), 0.001f);
        }
    }

    @Nested
    @DisplayName("MobModifiers.hasModifiers")
    class MobModifiersHasModifiersTests {

        @Test
        @DisplayName("NONE should not have modifiers")
        void noneShouldNotHaveModifiers() {
            assertFalse(RealmScalingIntegration.MobModifiers.NONE.hasModifiers());
        }

        @Test
        @DisplayName("Should have modifiers when health differs")
        void shouldHaveModifiersWhenHealthDiffers() {
            var mods = new RealmScalingIntegration.MobModifiers(1, 1.5f, 1.0f, 1.0f);
            assertTrue(mods.hasModifiers());
        }

        @Test
        @DisplayName("Should have modifiers when damage differs")
        void shouldHaveModifiersWhenDamageDiffers() {
            var mods = new RealmScalingIntegration.MobModifiers(1, 1.0f, 1.2f, 1.0f);
            assertTrue(mods.hasModifiers());
        }

        @Test
        @DisplayName("Should have modifiers when speed differs")
        void shouldHaveModifiersWhenSpeedDiffers() {
            var mods = new RealmScalingIntegration.MobModifiers(1, 1.0f, 1.0f, 1.1f);
            assertTrue(mods.hasModifiers());
        }
    }

    @Nested
    @DisplayName("isRealmMob")
    class IsRealmMobTests {

        @Test
        @DisplayName("Should return false for invalid ref")
        void shouldReturnFalseForInvalidRef() {
            when(mobRef.isValid()).thenReturn(false);

            assertFalse(integration.isRealmMob(mobRef, accessor));
        }

        @Test
        @DisplayName("Should return false when no component")
        void shouldReturnFalseWhenNoComponent() {
            when(mobRef.isValid()).thenReturn(true);
            when(accessor.getComponent(eq(mobRef), any(ComponentType.class))).thenReturn(null);

            assertFalse(integration.isRealmMob(mobRef, accessor));
        }

        @Test
        @DisplayName("Should return true when component has realm ID")
        void shouldReturnTrueWhenHasRealmId() {
            when(mobRef.isValid()).thenReturn(true);
            when(accessor.getComponent(eq(mobRef), any(ComponentType.class))).thenReturn(realmMobComponent);
            when(realmMobComponent.getRealmId()).thenReturn(realmId);

            assertTrue(integration.isRealmMob(mobRef, accessor));
        }
    }

    @Nested
    @DisplayName("getMobRealmId")
    class GetMobRealmIdTests {

        @Test
        @DisplayName("Should return null for invalid ref")
        void shouldReturnNullForInvalidRef() {
            when(mobRef.isValid()).thenReturn(false);

            assertNull(integration.getMobRealmId(mobRef, accessor));
        }

        @Test
        @DisplayName("Should return realm ID when component exists")
        void shouldReturnRealmId() {
            when(mobRef.isValid()).thenReturn(true);
            when(accessor.getComponent(eq(mobRef), any(ComponentType.class))).thenReturn(realmMobComponent);
            when(realmMobComponent.getRealmId()).thenReturn(realmId);

            UUID result = integration.getMobRealmId(mobRef, accessor);

            assertEquals(realmId, result);
        }
    }

    @Nested
    @DisplayName("getDebugInfo")
    class GetDebugInfoTests {

        @Test
        @DisplayName("Should return debug info")
        void shouldReturnDebugInfo() {
            RealmMapData mapData = createMapDataWithModifiers(List.of(
                new RealmModifier(RealmModifierType.MONSTER_HEALTH, 25, false)
            ));
            when(realmsManager.getRealm(realmId)).thenReturn(Optional.of(realmInstance));
            when(realmInstance.getMapData()).thenReturn(mapData);

            String debug = integration.getDebugInfo(realmId);

            assertTrue(debug.contains("RealmScaling"));
            assertTrue(debug.contains("level=10"));
            assertTrue(debug.contains("health=1.25x"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════

    private RealmMapData createMapDataWithLevel(int level) {
        return new RealmMapData(
            level,
            GearRarity.RARE,
            50,  // quality
            RealmBiomeType.FOREST,
            RealmLayoutSize.MEDIUM,
            RealmLayoutShape.CIRCULAR,
            List.of(), // prefixes
            List.of(), // suffixes
            false,  // corrupted
            true,   // identified
            null    // instanceId
        );
    }

    private RealmMapData createMapDataWithModifier(RealmModifierType type, int value) {
        return createMapDataWithModifiers(List.of(new RealmModifier(type, value, false)));
    }

    private RealmMapData createMapDataWithModifiers(List<RealmModifier> modifiers) {
        // Split modifiers into prefixes and suffixes based on their type
        List<RealmModifier> prefixes = modifiers.stream()
            .filter(RealmModifier::isPrefix)
            .toList();
        List<RealmModifier> suffixes = modifiers.stream()
            .filter(RealmModifier::isSuffix)
            .toList();

        return new RealmMapData(
            10,  // level
            GearRarity.RARE,
            50,  // quality
            RealmBiomeType.FOREST,
            RealmLayoutSize.MEDIUM,
            RealmLayoutShape.CIRCULAR,
            prefixes,
            suffixes,
            false,  // corrupted
            true,   // identified
            null    // instanceId
        );
    }
}
