package io.github.larsonix.trailoforbis.combat;

import io.github.larsonix.trailoforbis.combat.detection.DamageTypeClassifier;
import io.github.larsonix.trailoforbis.elemental.ElementType;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Regression tests for RPGDamageSystem to prevent damage classification bugs.
 *
 * <p><b>Critical Regression Protection:</b>
 * <ul>
 *   <li>Projectile damage must use RPG calculation path (commit 57b11e8 fix)</li>
 *   <li>ProjectileSource damage must NOT be filtered as "secondary melee"</li>
 *   <li>DOT damage must skip flat bonuses but apply resistances</li>
 *   <li>Attack type detection must work for all damage sources</li>
 * </ul>
 *
 * <p>The key bug fixed was that projectile damage was being incorrectly filtered as
 * "secondary melee damage" because it lacked a DamageSequence meta-object. The fix
 * was to explicitly check for {@code ProjectileSource} and allow it through.
 */
class RPGDamageSystemTest {

    private DamageTypeClassifier classifier;
    private MockedStatic<DamageTypeClassifier> mockedStatic;

    @BeforeEach
    void setUp() {
        classifier = new DamageTypeClassifier();
        // Mock static getDamageCause method, but call real methods for everything else
        mockedStatic = mockStatic(DamageTypeClassifier.class, CALLS_REAL_METHODS);
    }

    @AfterEach
    void tearDown() {
        mockedStatic.close();
    }

    /**
     * Helper to mock the static getDamageCause method for a specific Damage mock.
     */
    private void mockGetDamageCause(Damage damage, DamageCause cause) {
        mockedStatic.when(() -> DamageTypeClassifier.getDamageCause(damage)).thenReturn(cause);
    }

    // =========================================================================
    // PROJECTILE DAMAGE CLASSIFICATION TESTS
    // =========================================================================

    @Nested
    @DisplayName("Projectile Damage Classification")
    class ProjectileDamageTests {

        @Test
        @DisplayName("CRITICAL REGRESSION: ProjectileSource returns PROJECTILE attack type")
        void detectAttackType_ProjectileSource_ReturnsProjectile() {
            // This is the critical regression test for commit 57b11e8
            // Projectile damage MUST be classified as PROJECTILE, not MELEE or UNKNOWN
            Store<EntityStore> mockStore = mockStore();
            Damage mockDamage = mock(Damage.class);
            Damage.ProjectileSource mockProjectileSource = mock(Damage.ProjectileSource.class);

            when(mockDamage.getSource()).thenReturn(mockProjectileSource);

            AttackType result = classifier.detectAttackType(mockStore, mockDamage);

            assertEquals(AttackType.PROJECTILE, result,
                "REGRESSION: ProjectileSource MUST return PROJECTILE attack type! " +
                "This was fixed in commit 57b11e8 - projectile damage was being filtered " +
                "as secondary melee because it lacks DamageSequence.");
        }

        /**
         * Tests that EntitySource with ProjectileComponent returns PROJECTILE.
         *
         * <p>NOTE: This test is disabled because it requires the Hytale EntityModule
         * to be initialized to call ProjectileComponent.getComponentType(). The
         * critical regression test above (ProjectileSource) covers the main case.
         *
         * <p>In production, this path works because:
         * <ol>
         *   <li>ProjectileSource is checked FIRST and returns PROJECTILE immediately</li>
         *   <li>EntitySource fallback only runs for direct entity attacks (melee)</li>
         * </ol>
         *
         * @see #detectAttackType_ProjectileSource_ReturnsProjectile() for the main test
         */
        // @Test - Disabled: requires Hytale runtime for ProjectileComponent.getComponentType()
        @DisplayName("Entity with ProjectileComponent returns PROJECTILE attack type (INTEGRATION TEST)")
        void detectAttackType_ProjectileComponent_ReturnsProjectile() {
            // This test would require PowerMock or similar to mock static method
            // ProjectileComponent.getComponentType() → EntityModule.get().getProjectileComponentType()
            //
            // The critical path (ProjectileSource) is tested above and is the main regression fix.
        }

        @Test
        @DisplayName("Arrow damage uses PROJECTILE attack type")
        void detectAttackType_ArrowDamage_ReturnsProjectile() {
            Store<EntityStore> mockStore = mockStore();
            Damage mockDamage = mock(Damage.class);
            DamageCause mockCause = mock(DamageCause.class);
            Damage.ProjectileSource mockProjectileSource = mock(Damage.ProjectileSource.class);

            when(mockDamage.getSource()).thenReturn(mockProjectileSource);
            mockGetDamageCause(mockDamage, mockCause);
            when(mockCause.getId()).thenReturn("Projectile");

            AttackType result = classifier.detectAttackType(mockStore, mockDamage);

            assertEquals(AttackType.PROJECTILE, result,
                "Arrow damage with Projectile cause should be PROJECTILE");
        }

        @Test
        @DisplayName("isProjectileDamage returns true for Projectile cause")
        void isProjectileDamage_ProjectileCause_ReturnsTrue() {
            Damage mockDamage = mock(Damage.class);
            DamageCause mockCause = mock(DamageCause.class);

            mockGetDamageCause(mockDamage, mockCause);
            when(mockCause.getId()).thenReturn("Projectile");

            assertTrue(classifier.isProjectileDamage(mockDamage),
                "Damage with 'Projectile' cause should be recognized as projectile damage");
        }

        @Test
        @DisplayName("isProjectileDamage is case insensitive")
        void isProjectileDamage_CaseInsensitive() {
            Damage mockDamage = mock(Damage.class);
            DamageCause mockCause = mock(DamageCause.class);

            mockGetDamageCause(mockDamage, mockCause);
            when(mockCause.getId()).thenReturn("PROJECTILE");

            assertTrue(classifier.isProjectileDamage(mockDamage),
                "Should match 'PROJECTILE' case-insensitively");

            when(mockCause.getId()).thenReturn("projectile");
            assertTrue(classifier.isProjectileDamage(mockDamage),
                "Should match 'projectile' case-insensitively");
        }
    }

    // =========================================================================
    // MELEE VS PROJECTILE DISCRIMINATION
    // =========================================================================

    @Nested
    @DisplayName("Melee vs Projectile Discrimination")
    class MeleeProjectileDiscriminationTests {

        /**
         * Tests that EntitySource without ProjectileComponent returns MELEE.
         *
         * <p>NOTE: This test is disabled because it requires the Hytale EntityModule
         * to be initialized to call ProjectileComponent.getComponentType().
         *
         * <p>In production, EntitySource without ProjectileComponent correctly returns
         * MELEE because the component lookup returns null and there's no DamageCause
         * indicating a different attack type.
         *
         * @see MeleeProjectileDiscriminationTests#isPhysicalMeleeDamage_PhysicalCause_ReturnsTrue()
         */
        // @Test - Disabled: requires Hytale runtime for ProjectileComponent.getComponentType()
        @DisplayName("EntitySource without ProjectileComponent returns MELEE (INTEGRATION TEST)")
        void detectAttackType_MeleeEntitySource_ReturnsMelee() {
            // This test would require PowerMock or similar to mock static method
            // ProjectileComponent.getComponentType() → EntityModule.get().getProjectileComponentType()
            //
            // The melee damage detection is tested via isPhysicalMeleeDamage() below.
        }

        @Test
        @DisplayName("isPhysicalMeleeDamage returns true for Physical cause")
        void isPhysicalMeleeDamage_PhysicalCause_ReturnsTrue() {
            Damage mockDamage = mock(Damage.class);
            DamageCause mockCause = mock(DamageCause.class);

            mockGetDamageCause(mockDamage, mockCause);
            when(mockCause.getId()).thenReturn("Physical");

            assertTrue(classifier.isPhysicalMeleeDamage(mockDamage));
        }

        @Test
        @DisplayName("isPhysicalMeleeDamage returns false for Projectile cause")
        void isPhysicalMeleeDamage_ProjectileCause_ReturnsFalse() {
            Damage mockDamage = mock(Damage.class);
            DamageCause mockCause = mock(DamageCause.class);

            mockGetDamageCause(mockDamage, mockCause);
            when(mockCause.getId()).thenReturn("Projectile");

            assertFalse(classifier.isPhysicalMeleeDamage(mockDamage),
                "Projectile damage should NOT be classified as physical melee");
        }
    }

    // =========================================================================
    // DOT DAMAGE TESTS
    // =========================================================================

    @Nested
    @DisplayName("DOT Damage Classification")
    class DOTDamageTests {

        @Test
        @DisplayName("isDOTDamage returns true for burning damage")
        void isDOTDamage_Burning_ReturnsTrue() {
            Damage mockDamage = mock(Damage.class);
            DamageCause mockCause = mock(DamageCause.class);

            mockGetDamageCause(mockDamage, mockCause);
            when(mockCause.getId()).thenReturn("Burning");

            assertTrue(classifier.isDOTDamage(mockDamage));
        }

        @Test
        @DisplayName("isDOTDamage returns true for poison damage")
        void isDOTDamage_Poison_ReturnsTrue() {
            Damage mockDamage = mock(Damage.class);
            DamageCause mockCause = mock(DamageCause.class);

            mockGetDamageCause(mockDamage, mockCause);
            when(mockCause.getId()).thenReturn("Poison");

            assertTrue(classifier.isDOTDamage(mockDamage));
        }

        @Test
        @DisplayName("isDOTDamage returns true for bleed/bleeding damage")
        void isDOTDamage_Bleed_ReturnsTrue() {
            Damage mockDamage = mock(Damage.class);
            DamageCause mockCause = mock(DamageCause.class);

            mockGetDamageCause(mockDamage, mockCause);
            when(mockCause.getId()).thenReturn("Bleeding");
            assertTrue(classifier.isDOTDamage(mockDamage));

            when(mockCause.getId()).thenReturn("Bleed");
            assertTrue(classifier.isDOTDamage(mockDamage));
        }

        @Test
        @DisplayName("isDOTDamage returns false for Physical/Projectile")
        void isDOTDamage_DirectDamage_ReturnsFalse() {
            Damage mockDamage = mock(Damage.class);
            DamageCause mockCause = mock(DamageCause.class);

            mockGetDamageCause(mockDamage, mockCause);

            when(mockCause.getId()).thenReturn("Physical");
            assertFalse(classifier.isDOTDamage(mockDamage),
                "Physical melee should not be DOT");

            when(mockCause.getId()).thenReturn("Projectile");
            assertFalse(classifier.isDOTDamage(mockDamage),
                "Projectile should not be DOT");
        }

        @Test
        @DisplayName("getElementFromDOTCause returns correct element")
        void getElementFromDOTCause_ReturnsCorrectElement() {
            DamageCause fireCause = mock(DamageCause.class);
            when(fireCause.getId()).thenReturn("Burning");
            assertEquals(ElementType.FIRE, classifier.getElementFromDOTCause(fireCause));

            DamageCause coldCause = mock(DamageCause.class);
            when(coldCause.getId()).thenReturn("Freezing");
            assertEquals(ElementType.WATER, classifier.getElementFromDOTCause(coldCause));

            DamageCause lightningCause = mock(DamageCause.class);
            when(lightningCause.getId()).thenReturn("Shock");
            assertEquals(ElementType.LIGHTNING, classifier.getElementFromDOTCause(lightningCause));

            DamageCause chaosCause = mock(DamageCause.class);
            when(chaosCause.getId()).thenReturn("Poison");
            assertEquals(ElementType.VOID, classifier.getElementFromDOTCause(chaosCause));
        }

        @Test
        @DisplayName("getElementFromDOTCause returns null for physical DOT (bleed)")
        void getElementFromDOTCause_Bleed_ReturnsNull() {
            DamageCause bleedCause = mock(DamageCause.class);
            when(bleedCause.getId()).thenReturn("Bleeding");

            assertNull(classifier.getElementFromDOTCause(bleedCause),
                "Bleed should be physical DOT (null element)");
        }
    }

    // =========================================================================
    // ENVIRONMENT DAMAGE TESTS
    // =========================================================================

    @Nested
    @DisplayName("Environment Damage")
    class EnvironmentDamageTests {

        @Test
        @DisplayName("Non-EntitySource returns UNKNOWN attack type")
        void detectAttackType_EnvironmentSource_ReturnsUnknown() {
            Store<EntityStore> mockStore = mockStore();
            Damage mockDamage = mock(Damage.class);
            Damage.Source mockSource = mock(Damage.Source.class); // Not EntitySource

            when(mockDamage.getSource()).thenReturn(mockSource);

            AttackType result = classifier.detectAttackType(mockStore, mockDamage);

            assertEquals(AttackType.UNKNOWN, result,
                "Environment damage should have UNKNOWN attack type");
        }

        @Test
        @DisplayName("Fall damage is correctly identified")
        void isFallDamage_FallCause_ReturnsTrue() {
            Damage mockDamage = mock(Damage.class);
            DamageCause mockCause = mock(DamageCause.class);

            mockGetDamageCause(mockDamage, mockCause);
            when(mockCause.getId()).thenReturn("Fall");

            assertTrue(classifier.isFallDamage(mockDamage));
        }

        @Test
        @DisplayName("Fall damage check is case insensitive")
        void isFallDamage_CaseInsensitive() {
            Damage mockDamage = mock(Damage.class);
            DamageCause mockCause = mock(DamageCause.class);

            mockGetDamageCause(mockDamage, mockCause);

            when(mockCause.getId()).thenReturn("FALL");
            assertTrue(classifier.isFallDamage(mockDamage));

            when(mockCause.getId()).thenReturn("fall");
            assertTrue(classifier.isFallDamage(mockDamage));
        }
    }

    // =========================================================================
    // PHYSICAL RESISTANCE APPLICATION TESTS
    // =========================================================================

    @Nested
    @DisplayName("Physical Resistance Application")
    class PhysicalResistanceTests {

        @Test
        @DisplayName("Physical resistance applies to Physical melee damage")
        void shouldApplyPhysicalResistance_Physical_ReturnsTrue() {
            Damage mockDamage = mock(Damage.class);
            DamageCause mockCause = mock(DamageCause.class);

            mockGetDamageCause(mockDamage, mockCause);
            when(mockCause.getId()).thenReturn("Physical");
            when(mockCause.doesBypassResistances()).thenReturn(false);

            assertTrue(classifier.shouldApplyPhysicalResistance(mockDamage, false),
                "Physical melee should be reduced by physical resistance");
        }

        @Test
        @DisplayName("Physical resistance applies to Projectile when configured")
        void shouldApplyPhysicalResistance_Projectile_ConfigDependent() {
            Damage mockDamage = mock(Damage.class);
            DamageCause mockCause = mock(DamageCause.class);

            mockGetDamageCause(mockDamage, mockCause);
            when(mockCause.getId()).thenReturn("Projectile");
            when(mockCause.doesBypassResistances()).thenReturn(false);

            assertTrue(classifier.shouldApplyPhysicalResistance(mockDamage, true),
                "Projectile should be reduced when appliesToProjectiles=true");
            assertFalse(classifier.shouldApplyPhysicalResistance(mockDamage, false),
                "Projectile should NOT be reduced when appliesToProjectiles=false");
        }

        @Test
        @DisplayName("Physical resistance respects bypassResistances flag")
        void shouldApplyPhysicalResistance_BypassFlag_Respected() {
            Damage mockDamage = mock(Damage.class);
            DamageCause mockCause = mock(DamageCause.class);

            mockGetDamageCause(mockDamage, mockCause);
            when(mockCause.getId()).thenReturn("Physical");
            when(mockCause.doesBypassResistances()).thenReturn(true);

            assertFalse(classifier.shouldApplyPhysicalResistance(mockDamage, false),
                "bypassResistances flag should prevent resistance application");
        }
    }

    // =========================================================================
    // EDGE CASES
    // =========================================================================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Null cause returns false for all type checks")
        void nullCause_ReturnsFalse() {
            Damage mockDamage = mock(Damage.class);
            mockGetDamageCause(mockDamage, null);

            assertFalse(classifier.isFallDamage(mockDamage));
            assertFalse(classifier.isPhysicalMeleeDamage(mockDamage));
            assertFalse(classifier.isProjectileDamage(mockDamage));
            assertFalse(classifier.isDOTDamage(mockDamage));
        }

        @Test
        @DisplayName("Null cause ID returns false for all type checks")
        void nullCauseId_ReturnsFalse() {
            Damage mockDamage = mock(Damage.class);
            DamageCause mockCause = mock(DamageCause.class);

            mockGetDamageCause(mockDamage, mockCause);
            when(mockCause.getId()).thenReturn(null);

            assertFalse(classifier.isFallDamage(mockDamage));
            assertFalse(classifier.isPhysicalMeleeDamage(mockDamage));
            assertFalse(classifier.isProjectileDamage(mockDamage));
            assertFalse(classifier.isDOTDamage(mockDamage));
        }

        @Test
        @DisplayName("Invalid entity ref returns UNKNOWN attack type")
        void invalidEntityRef_ReturnsUnknown() {
            Store<EntityStore> mockStore = mockStore();
            Damage mockDamage = mock(Damage.class);
            Damage.EntitySource mockEntitySource = mock(Damage.EntitySource.class);
            Ref<EntityStore> mockRef = mockRef();

            when(mockDamage.getSource()).thenReturn(mockEntitySource);
            when(mockEntitySource.getRef()).thenReturn(mockRef);
            when(mockRef.isValid()).thenReturn(false); // Invalid ref

            AttackType result = classifier.detectAttackType(mockStore, mockDamage);

            assertEquals(AttackType.UNKNOWN, result,
                "Invalid entity ref should return UNKNOWN");
        }
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    @SuppressWarnings("unchecked")
    private Store<EntityStore> mockStore() {
        return mock(Store.class);
    }

    @SuppressWarnings("unchecked")
    private Ref<EntityStore> mockRef() {
        Ref<EntityStore> ref = mock(Ref.class);
        when(ref.isValid()).thenReturn(true);
        return ref;
    }
}
