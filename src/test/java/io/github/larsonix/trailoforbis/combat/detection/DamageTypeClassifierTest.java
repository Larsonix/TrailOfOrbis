package io.github.larsonix.trailoforbis.combat.detection;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.combat.AttackType;
import io.github.larsonix.trailoforbis.elemental.ElementType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DamageTypeClassifier.
 *
 * <p>Tests damage classification logic:
 * <ul>
 *   <li>Attack type detection (melee, projectile, area)</li>
 *   <li>Damage cause identification (fall, physical, DOT)</li>
 *   <li>Element detection from DOT causes</li>
 *   <li>Resistance applicability checks</li>
 * </ul>
 */
public class DamageTypeClassifierTest {

    @Mock
    private Store<EntityStore> store;

    @Mock
    private Damage damage;

    @Mock
    private DamageCause damageCause;

    @Mock
    private Damage.EntitySource entitySource;

    @Mock
    private Ref<EntityStore> sourceRef;

    private DamageTypeClassifier classifier;

    private MockedStatic<DamageTypeClassifier> mockedStatic;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        classifier = new DamageTypeClassifier();
        // Mock static getDamageCause method, but call real methods for everything else
        mockedStatic = mockStatic(DamageTypeClassifier.class, CALLS_REAL_METHODS);
    }

    @AfterEach
    void tearDown() {
        mockedStatic.close();
    }

    /**
     * Helper to mock the static getDamageCause method.
     */
    private void mockGetDamageCause(DamageCause cause) {
        mockedStatic.when(() -> DamageTypeClassifier.getDamageCause(damage)).thenReturn(cause);
    }

    // ==================== Attack Type Detection ====================

    @Nested
    @DisplayName("Attack Type Detection")
    class AttackTypeTests {

        @Test
        @DisplayName("Non-entity damage returns UNKNOWN")
        void detectAttackType_nonEntitySource_returnsUnknown() {
            Damage.Source nonEntitySource = mock(Damage.Source.class);
            when(damage.getSource()).thenReturn(nonEntitySource);

            AttackType result = classifier.detectAttackType(store, damage);

            assertEquals(AttackType.UNKNOWN, result);
        }

        @Test
        @DisplayName("ProjectileSource returns PROJECTILE")
        void detectAttackType_projectileSource_returnsProjectile() {
            Damage.ProjectileSource projectileSource = mock(Damage.ProjectileSource.class);
            when(damage.getSource()).thenReturn(projectileSource);

            AttackType result = classifier.detectAttackType(store, damage);

            assertEquals(AttackType.PROJECTILE, result);
        }

        @Test
        @DisplayName("Null source ref returns UNKNOWN")
        void detectAttackType_nullSourceRef_returnsUnknown() {
            when(damage.getSource()).thenReturn(entitySource);
            when(entitySource.getRef()).thenReturn(null);

            AttackType result = classifier.detectAttackType(store, damage);

            assertEquals(AttackType.UNKNOWN, result);
        }

        @Test
        @DisplayName("Invalid source ref returns UNKNOWN")
        void detectAttackType_invalidSourceRef_returnsUnknown() {
            when(damage.getSource()).thenReturn(entitySource);
            when(entitySource.getRef()).thenReturn(sourceRef);
            when(sourceRef.isValid()).thenReturn(false);

            AttackType result = classifier.detectAttackType(store, damage);

            assertEquals(AttackType.UNKNOWN, result);
        }
    }

    // ==================== Fall Damage Detection ====================

    @Nested
    @DisplayName("Fall Damage Detection")
    class FallDamageTests {

        @Test
        @DisplayName("Null cause returns false")
        void isFallDamage_nullCause_returnsFalse() {
            mockGetDamageCause(null);

            assertFalse(classifier.isFallDamage(damage));
        }

        @Test
        @DisplayName("Fall cause ID returns true")
        void isFallDamage_fallCauseId_returnsTrue() {
            mockGetDamageCause(damageCause);
            when(damageCause.getId()).thenReturn("Fall");

            assertTrue(classifier.isFallDamage(damage));
        }

        @Test
        @DisplayName("Fall cause ID case-insensitive")
        void isFallDamage_caseInsensitive_returnsTrue() {
            mockGetDamageCause(damageCause);
            when(damageCause.getId()).thenReturn("FALL");

            assertTrue(classifier.isFallDamage(damage));
        }

        @Test
        @DisplayName("Non-fall cause returns false")
        void isFallDamage_nonFallCause_returnsFalse() {
            mockGetDamageCause(damageCause);
            when(damageCause.getId()).thenReturn("Physical");

            assertFalse(classifier.isFallDamage(damage));
        }

        @Test
        @DisplayName("Null ID returns false")
        void isFallDamage_nullId_returnsFalse() {
            mockGetDamageCause(damageCause);
            when(damageCause.getId()).thenReturn(null);

            assertFalse(classifier.isFallDamage(damage));
        }
    }

    // ==================== Physical Melee Damage ====================

    @Nested
    @DisplayName("Physical Melee Damage")
    class PhysicalMeleeDamageTests {

        @Test
        @DisplayName("Null cause returns false")
        void isPhysicalMeleeDamage_nullCause_returnsFalse() {
            mockGetDamageCause(null);

            assertFalse(classifier.isPhysicalMeleeDamage(damage));
        }

        @Test
        @DisplayName("Physical cause ID returns true")
        void isPhysicalMeleeDamage_physicalId_returnsTrue() {
            mockGetDamageCause(damageCause);
            when(damageCause.getId()).thenReturn("Physical");

            assertTrue(classifier.isPhysicalMeleeDamage(damage));
        }

        @Test
        @DisplayName("Non-physical cause returns false")
        void isPhysicalMeleeDamage_nonPhysical_returnsFalse() {
            mockGetDamageCause(damageCause);
            when(damageCause.getId()).thenReturn("Fire");

            assertFalse(classifier.isPhysicalMeleeDamage(damage));
        }
    }

    // ==================== Projectile Damage ====================

    @Nested
    @DisplayName("Projectile Damage")
    class ProjectileDamageTests {

        @Test
        @DisplayName("Null cause returns false")
        void isProjectileDamage_nullCause_returnsFalse() {
            mockGetDamageCause(null);

            assertFalse(classifier.isProjectileDamage(damage));
        }

        @Test
        @DisplayName("Projectile cause ID returns true")
        void isProjectileDamage_projectileId_returnsTrue() {
            mockGetDamageCause(damageCause);
            when(damageCause.getId()).thenReturn("Projectile");

            assertTrue(classifier.isProjectileDamage(damage));
        }
    }

    // ==================== DOT Damage Detection ====================

    @Nested
    @DisplayName("DOT Damage Detection")
    class DOTDamageTests {

        @Test
        @DisplayName("Null cause returns false")
        void isDOTDamage_nullCause_returnsFalse() {
            mockGetDamageCause(null);

            assertFalse(classifier.isDOTDamage(damage));
        }

        @Test
        @DisplayName("Null ID returns false")
        void isDOTDamage_nullId_returnsFalse() {
            mockGetDamageCause(damageCause);
            when(damageCause.getId()).thenReturn(null);

            assertFalse(classifier.isDOTDamage(damage));
        }

        @Test
        @DisplayName("Burning cause returns true")
        void isDOTDamage_burning_returnsTrue() {
            mockGetDamageCause(damageCause);
            when(damageCause.getId()).thenReturn("burning");

            assertTrue(classifier.isDOTDamage(damage));
        }

        @Test
        @DisplayName("Poison cause returns true")
        void isDOTDamage_poison_returnsTrue() {
            mockGetDamageCause(damageCause);
            when(damageCause.getId()).thenReturn("poison");

            assertTrue(classifier.isDOTDamage(damage));
        }

        @Test
        @DisplayName("Bleeding cause returns true")
        void isDOTDamage_bleeding_returnsTrue() {
            mockGetDamageCause(damageCause);
            when(damageCause.getId()).thenReturn("bleeding_dot");

            assertTrue(classifier.isDOTDamage(damage));
        }

        @Test
        @DisplayName("Shock cause returns true")
        void isDOTDamage_shock_returnsTrue() {
            mockGetDamageCause(damageCause);
            when(damageCause.getId()).thenReturn("shock_damage");

            assertTrue(classifier.isDOTDamage(damage));
        }

        @Test
        @DisplayName("Physical cause returns false")
        void isDOTDamage_physical_returnsFalse() {
            mockGetDamageCause(damageCause);
            when(damageCause.getId()).thenReturn("Physical");

            assertFalse(classifier.isDOTDamage(damage));
        }
    }

    // ==================== Physical Resistance Applicability ====================

    @Nested
    @DisplayName("Physical Resistance Applicability")
    class PhysicalResistanceTests {

        @Test
        @DisplayName("Null cause returns false")
        void shouldApplyPhysicalResistance_nullCause_returnsFalse() {
            mockGetDamageCause(null);

            assertFalse(classifier.shouldApplyPhysicalResistance(damage, true));
        }

        @Test
        @DisplayName("Cause with bypassResistances returns false")
        void shouldApplyPhysicalResistance_bypassResistances_returnsFalse() {
            mockGetDamageCause(damageCause);
            when(damageCause.doesBypassResistances()).thenReturn(true);

            assertFalse(classifier.shouldApplyPhysicalResistance(damage, true));
        }

        @Test
        @DisplayName("Physical damage returns true")
        void shouldApplyPhysicalResistance_physicalDamage_returnsTrue() {
            mockGetDamageCause(damageCause);
            when(damageCause.doesBypassResistances()).thenReturn(false);
            when(damageCause.getId()).thenReturn("Physical");

            assertTrue(classifier.shouldApplyPhysicalResistance(damage, false));
        }

        @Test
        @DisplayName("Projectile with flag enabled returns true")
        void shouldApplyPhysicalResistance_projectileWithFlag_returnsTrue() {
            mockGetDamageCause(damageCause);
            when(damageCause.doesBypassResistances()).thenReturn(false);
            when(damageCause.getId()).thenReturn("Projectile");

            assertTrue(classifier.shouldApplyPhysicalResistance(damage, true));
        }

        @Test
        @DisplayName("Projectile with flag disabled returns false")
        void shouldApplyPhysicalResistance_projectileWithoutFlag_returnsFalse() {
            mockGetDamageCause(damageCause);
            when(damageCause.doesBypassResistances()).thenReturn(false);
            when(damageCause.getId()).thenReturn("Projectile");

            assertFalse(classifier.shouldApplyPhysicalResistance(damage, false));
        }
    }

    // ==================== Element from DOT Cause ====================

    @Nested
    @DisplayName("Element from DOT Cause")
    class ElementFromDOTTests {

        @Test
        @DisplayName("Null cause returns null")
        void getElementFromDOTCause_nullCause_returnsNull() {
            assertNull(classifier.getElementFromDOTCause(null));
        }

        @Test
        @DisplayName("Null ID returns null")
        void getElementFromDOTCause_nullId_returnsNull() {
            when(damageCause.getId()).thenReturn(null);

            assertNull(classifier.getElementFromDOTCause(damageCause));
        }

        @Test
        @DisplayName("Burning returns FIRE")
        void getElementFromDOTCause_burning_returnsFire() {
            when(damageCause.getId()).thenReturn("burning");

            assertEquals(ElementType.FIRE, classifier.getElementFromDOTCause(damageCause));
        }

        @Test
        @DisplayName("Fire returns FIRE")
        void getElementFromDOTCause_fire_returnsFire() {
            when(damageCause.getId()).thenReturn("fire_damage");

            assertEquals(ElementType.FIRE, classifier.getElementFromDOTCause(damageCause));
        }

        @Test
        @DisplayName("Freezing returns COLD")
        void getElementFromDOTCause_freezing_returnsCold() {
            when(damageCause.getId()).thenReturn("freezing");

            assertEquals(ElementType.WATER, classifier.getElementFromDOTCause(damageCause));
        }

        @Test
        @DisplayName("Frost returns COLD")
        void getElementFromDOTCause_frost_returnsCold() {
            when(damageCause.getId()).thenReturn("frost_dot");

            assertEquals(ElementType.WATER, classifier.getElementFromDOTCause(damageCause));
        }

        @Test
        @DisplayName("Shock returns LIGHTNING")
        void getElementFromDOTCause_shock_returnsLightning() {
            when(damageCause.getId()).thenReturn("shock");

            assertEquals(ElementType.LIGHTNING, classifier.getElementFromDOTCause(damageCause));
        }

        @Test
        @DisplayName("Lightning returns LIGHTNING")
        void getElementFromDOTCause_lightning_returnsLightning() {
            when(damageCause.getId()).thenReturn("lightning_damage");

            assertEquals(ElementType.LIGHTNING, classifier.getElementFromDOTCause(damageCause));
        }

        @Test
        @DisplayName("Poison returns CHAOS")
        void getElementFromDOTCause_poison_returnsChaos() {
            when(damageCause.getId()).thenReturn("poison");

            assertEquals(ElementType.VOID, classifier.getElementFromDOTCause(damageCause));
        }

        @Test
        @DisplayName("Physical DOT returns null")
        void getElementFromDOTCause_bleed_returnsNull() {
            when(damageCause.getId()).thenReturn("bleeding");

            // Bleeding is physical DOT, no element
            assertNull(classifier.getElementFromDOTCause(damageCause));
        }
    }

    // ==================== Damage Cause Formatting ====================

    @Nested
    @DisplayName("Damage Cause Formatting")
    class FormatDamageCauseTests {

        @Test
        @DisplayName("Null cause returns Unknown")
        void formatDamageCause_nullCause_returnsUnknown() {
            assertEquals("Unknown", classifier.formatDamageCause(null));
        }

        @Test
        @DisplayName("Null ID returns Unknown")
        void formatDamageCause_nullId_returnsUnknown() {
            when(damageCause.getId()).thenReturn(null);

            assertEquals("Unknown", classifier.formatDamageCause(damageCause));
        }

        @Test
        @DisplayName("Empty ID returns Unknown")
        void formatDamageCause_emptyId_returnsUnknown() {
            when(damageCause.getId()).thenReturn("");

            assertEquals("Unknown", classifier.formatDamageCause(damageCause));
        }

        @Test
        @DisplayName("Fall returns 'Fall Damage'")
        void formatDamageCause_fall_returnsFallDamage() {
            when(damageCause.getId()).thenReturn("fall");

            assertEquals("Fall Damage", classifier.formatDamageCause(damageCause));
        }

        @Test
        @DisplayName("Lava returns 'Lava'")
        void formatDamageCause_lava_returnsLava() {
            when(damageCause.getId()).thenReturn("lava");

            assertEquals("Lava", classifier.formatDamageCause(damageCause));
        }

        @Test
        @DisplayName("Void returns 'The Void'")
        void formatDamageCause_void_returnsTheVoid() {
            when(damageCause.getId()).thenReturn("void");

            assertEquals("The Void", classifier.formatDamageCause(damageCause));
        }

        @Test
        @DisplayName("Explosion returns 'Explosion'")
        void formatDamageCause_explosion_returnsExplosion() {
            when(damageCause.getId()).thenReturn("explosion");

            assertEquals("Explosion", classifier.formatDamageCause(damageCause));
        }
    }
}
