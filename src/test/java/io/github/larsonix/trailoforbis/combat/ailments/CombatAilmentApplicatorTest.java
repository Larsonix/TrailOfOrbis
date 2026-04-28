package io.github.larsonix.trailoforbis.combat.ailments;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.ailments.AilmentCalculator;
import io.github.larsonix.trailoforbis.ailments.AilmentTracker;
import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.combat.resolution.CombatEntityResolver;
import io.github.larsonix.trailoforbis.elemental.ElementType;
import io.github.larsonix.trailoforbis.elemental.ElementalStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CombatAilmentApplicator.
 *
 * <p>Tests ailment application from elemental damage:
 * <ul>
 *   <li>Guard clauses for null inputs</li>
 *   <li>Status effect chance rolling</li>
 *   <li>Element-to-ailment mapping</li>
 *   <li>Ailment tracker integration</li>
 * </ul>
 */
public class CombatAilmentApplicatorTest {

    @Mock
    private CombatEntityResolver entityResolver;

    @Mock
    private AilmentTracker ailmentTracker;

    @Mock
    private AilmentCalculator ailmentCalculator;

    @Mock
    private Store<EntityStore> store;

    @Mock
    private Damage damage;

    @Mock
    private ArchetypeChunk<EntityStore> archetypeChunk;

    @Mock
    private ElementalStats attackerElemental;

    @Mock
    private ComputedStats attackerStats;

    private CombatAilmentApplicator applicator;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        applicator = new CombatAilmentApplicator(entityResolver, ailmentTracker, ailmentCalculator);
    }

    // ==================== Constructor Tests ====================

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("Creates applicator with all dependencies")
        void constructor_allDependencies_succeeds() {
            CombatAilmentApplicator a = new CombatAilmentApplicator(
                entityResolver, ailmentTracker, ailmentCalculator);
            assertNotNull(a);
            assertTrue(a.isAvailable());
        }

        @Test
        @DisplayName("Creates applicator with null tracker")
        void constructor_nullTracker_succeeds() {
            CombatAilmentApplicator a = new CombatAilmentApplicator(
                entityResolver, null, ailmentCalculator);
            assertNotNull(a);
            assertFalse(a.isAvailable());
        }

        @Test
        @DisplayName("Creates applicator with null calculator")
        void constructor_nullCalculator_succeeds() {
            CombatAilmentApplicator a = new CombatAilmentApplicator(
                entityResolver, ailmentTracker, null);
            assertNotNull(a);
            assertFalse(a.isAvailable());
        }

        @Test
        @DisplayName("Creates applicator with both null")
        void constructor_bothNull_succeeds() {
            CombatAilmentApplicator a = new CombatAilmentApplicator(
                entityResolver, null, null);
            assertNotNull(a);
            assertFalse(a.isAvailable());
        }
    }

    // ==================== isAvailable Tests ====================

    @Nested
    @DisplayName("isAvailable")
    class IsAvailableTests {

        @Test
        @DisplayName("Returns true when both tracker and calculator present")
        void isAvailable_bothPresent_returnsTrue() {
            assertTrue(applicator.isAvailable());
        }

        @Test
        @DisplayName("Returns false when tracker is null")
        void isAvailable_nullTracker_returnsFalse() {
            CombatAilmentApplicator a = new CombatAilmentApplicator(
                entityResolver, null, ailmentCalculator);
            assertFalse(a.isAvailable());
        }

        @Test
        @DisplayName("Returns false when calculator is null")
        void isAvailable_nullCalculator_returnsFalse() {
            CombatAilmentApplicator a = new CombatAilmentApplicator(
                entityResolver, ailmentTracker, null);
            assertFalse(a.isAvailable());
        }
    }

    // ==================== tryApplyAilments Guard Clauses ====================

    @Nested
    @DisplayName("tryApplyAilments Guard Clauses")
    class TryApplyAilmentsGuardTests {

        @Test
        @DisplayName("Null elemental stats exits early")
        void tryApplyAilments_nullElemental_exitsEarly() {
            applicator.tryApplyAilments(0, archetypeChunk, store, damage, null, attackerStats, 100f);

            verifyNoInteractions(ailmentTracker);
            verifyNoInteractions(ailmentCalculator);
        }

        @Test
        @DisplayName("Unavailable system exits early")
        void tryApplyAilments_unavailable_exitsEarly() {
            CombatAilmentApplicator unavailable = new CombatAilmentApplicator(
                entityResolver, null, null);

            unavailable.tryApplyAilments(0, archetypeChunk, store, damage, attackerElemental, attackerStats, 100f);

            verifyNoInteractions(entityResolver);
        }

        @Test
        @DisplayName("Null defender UUID exits early")
        void tryApplyAilments_nullDefender_exitsEarly() {
            when(entityResolver.getDefenderUuid(anyInt(), any(), any())).thenReturn(null);

            applicator.tryApplyAilments(0, archetypeChunk, store, damage, attackerElemental, attackerStats, 100f);

            verify(entityResolver).getDefenderUuid(0, archetypeChunk, store);
            verifyNoInteractions(ailmentCalculator);
        }

        @Test
        @DisplayName("No elemental damage skips application")
        void tryApplyAilments_noElementalDamage_skipsApplication() {
            UUID defenderUuid = UUID.randomUUID();
            UUID attackerUuid = UUID.randomUUID();
            when(entityResolver.getDefenderUuid(anyInt(), any(), any())).thenReturn(defenderUuid);
            when(entityResolver.getAttackerUuid(any(), any())).thenReturn(attackerUuid);

            // All elements have 0 damage
            for (ElementType element : ElementType.values()) {
                when(attackerElemental.getFlatDamage(element)).thenReturn(0.0);
            }

            applicator.tryApplyAilments(0, archetypeChunk, store, damage, attackerElemental, attackerStats, 100f);

            // Calculator should not be called for any element
            verifyNoInteractions(ailmentCalculator);
        }
    }

    // ==================== tryApplyAilments Behavior ====================

    @Nested
    @DisplayName("tryApplyAilments Behavior")
    class TryApplyAilmentsBehaviorTests {

        private UUID defenderUuid;
        private UUID attackerUuid;

        @BeforeEach
        void setUpMocks() {
            defenderUuid = UUID.randomUUID();
            attackerUuid = UUID.randomUUID();
            when(entityResolver.getDefenderUuid(anyInt(), any(), any())).thenReturn(defenderUuid);
            when(entityResolver.getAttackerUuid(any(), any())).thenReturn(attackerUuid);
        }

        @Test
        @DisplayName("Calls calculator for element with damage")
        void tryApplyAilments_elementWithDamage_callsCalculator() {
            // Set up fire damage
            when(attackerElemental.getFlatDamage(ElementType.FIRE)).thenReturn(50.0);
            for (ElementType element : ElementType.values()) {
                if (element != ElementType.FIRE) {
                    when(attackerElemental.getFlatDamage(element)).thenReturn(0.0);
                }
            }

            // Calculator returns unsuccessful application
            when(ailmentCalculator.tryApplyAilment(any(), anyFloat(), any(), anyFloat(), any()))
                .thenReturn(AilmentCalculator.AilmentApplicationResult.notApplied());

            applicator.tryApplyAilments(0, archetypeChunk, store, damage, attackerElemental, attackerStats, 100f);

            verify(ailmentCalculator).tryApplyAilment(
                eq(ElementType.FIRE),
                eq(50f),
                eq(attackerStats),
                eq(100f),
                eq(attackerUuid)
            );
        }

        @Test
        @DisplayName("Uses fallback UUID for null attacker")
        void tryApplyAilments_nullAttacker_usesFallbackUuid() {
            when(entityResolver.getAttackerUuid(any(), any())).thenReturn(null);

            when(attackerElemental.getFlatDamage(ElementType.FIRE)).thenReturn(50.0);
            for (ElementType element : ElementType.values()) {
                if (element != ElementType.FIRE) {
                    when(attackerElemental.getFlatDamage(element)).thenReturn(0.0);
                }
            }

            when(ailmentCalculator.tryApplyAilment(any(), anyFloat(), any(), anyFloat(), any()))
                .thenReturn(AilmentCalculator.AilmentApplicationResult.notApplied());

            applicator.tryApplyAilments(0, archetypeChunk, store, damage, attackerElemental, attackerStats, 100f);

            // Should use fallback UUID(0,0)
            verify(ailmentCalculator).tryApplyAilment(
                any(),
                anyFloat(),
                any(),
                anyFloat(),
                eq(new UUID(0, 0))
            );
        }
    }

    // ==================== createMinimalStatsForAilment ====================

    @Nested
    @DisplayName("createMinimalStatsForAilment")
    class CreateMinimalStatsTests {

        @Test
        @DisplayName("Returns non-null stats")
        void createMinimalStatsForAilment_returnsNonNull() {
            ComputedStats stats = applicator.createMinimalStatsForAilment();
            assertNotNull(stats);
        }

        @Test
        @DisplayName("Returns stats with zero defaults")
        void createMinimalStatsForAilment_hasZeroDefaults() {
            ComputedStats stats = applicator.createMinimalStatsForAilment();

            // All stats should be 0 (default for new ComputedStats)
            assertEquals(0f, stats.getStatusEffectChance(), 0.001f);
            assertEquals(0f, stats.getStatusEffectDuration(), 0.001f);
        }
    }

    // ==================== Edge Cases ====================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Null attacker stats uses minimal stats")
        void tryApplyAilments_nullAttackerStats_usesMinimal() {
            UUID defenderUuid = UUID.randomUUID();
            UUID attackerUuid = UUID.randomUUID();
            when(entityResolver.getDefenderUuid(anyInt(), any(), any())).thenReturn(defenderUuid);
            when(entityResolver.getAttackerUuid(any(), any())).thenReturn(attackerUuid);

            when(attackerElemental.getFlatDamage(ElementType.FIRE)).thenReturn(50.0);
            for (ElementType element : ElementType.values()) {
                if (element != ElementType.FIRE) {
                    when(attackerElemental.getFlatDamage(element)).thenReturn(0.0);
                }
            }

            when(ailmentCalculator.tryApplyAilment(any(), anyFloat(), any(), anyFloat(), any()))
                .thenReturn(AilmentCalculator.AilmentApplicationResult.notApplied());

            // Pass null for attacker stats
            applicator.tryApplyAilments(0, archetypeChunk, store, damage, attackerElemental, null, 100f);

            // Should still call calculator (with minimal stats)
            verify(ailmentCalculator).tryApplyAilment(
                eq(ElementType.FIRE),
                eq(50f),
                any(ComputedStats.class),  // Will be minimal stats
                eq(100f),
                eq(attackerUuid)
            );
        }

        @Test
        @DisplayName("Negative elemental damage is skipped")
        void tryApplyAilments_negativeDamage_skipped() {
            UUID defenderUuid = UUID.randomUUID();
            when(entityResolver.getDefenderUuid(anyInt(), any(), any())).thenReturn(defenderUuid);
            when(entityResolver.getAttackerUuid(any(), any())).thenReturn(UUID.randomUUID());

            // All elements have negative damage
            for (ElementType element : ElementType.values()) {
                when(attackerElemental.getFlatDamage(element)).thenReturn(-10.0);
            }

            applicator.tryApplyAilments(0, archetypeChunk, store, damage, attackerElemental, attackerStats, 100f);

            verifyNoInteractions(ailmentCalculator);
        }

        @Test
        @DisplayName("Zero max health is handled")
        void tryApplyAilments_zeroMaxHealth_handled() {
            UUID defenderUuid = UUID.randomUUID();
            when(entityResolver.getDefenderUuid(anyInt(), any(), any())).thenReturn(defenderUuid);
            when(entityResolver.getAttackerUuid(any(), any())).thenReturn(UUID.randomUUID());

            when(attackerElemental.getFlatDamage(ElementType.FIRE)).thenReturn(50.0);
            for (ElementType element : ElementType.values()) {
                if (element != ElementType.FIRE) {
                    when(attackerElemental.getFlatDamage(element)).thenReturn(0.0);
                }
            }

            when(ailmentCalculator.tryApplyAilment(any(), anyFloat(), any(), anyFloat(), any()))
                .thenReturn(AilmentCalculator.AilmentApplicationResult.notApplied());

            // Pass 0 for max health
            assertDoesNotThrow(() ->
                applicator.tryApplyAilments(0, archetypeChunk, store, damage, attackerElemental, attackerStats, 0f)
            );
        }
    }
}
