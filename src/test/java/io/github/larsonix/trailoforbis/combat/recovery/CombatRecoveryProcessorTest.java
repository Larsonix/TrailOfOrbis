package io.github.larsonix.trailoforbis.combat.recovery;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.combat.resolution.CombatEntityResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CombatRecoveryProcessor.
 *
 * <p>Tests combat recovery mechanics:
 * <ul>
 *   <li>Life steal healing for attackers</li>
 *   <li>Mana leech restoration</li>
 *   <li>Reflected damage application</li>
 *   <li>Block heal for defenders</li>
 * </ul>
 */
public class CombatRecoveryProcessorTest {

    @Mock
    private CombatEntityResolver entityResolver;

    @Mock
    private Store<EntityStore> store;

    @Mock
    private Ref<EntityStore> attackerRef;

    @Mock
    private Ref<EntityStore> defenderRef;

    @Mock
    private ArchetypeChunk<EntityStore> archetypeChunk;

    @Mock
    private Damage damage;

    @Mock
    private Damage.EntitySource entitySource;

    @Mock
    private EntityStatMap attackerStatMap;

    @Mock
    private EntityStatMap defenderStatMap;

    @Mock
    private EntityStatValue healthStat;

    @Mock
    private EntityStatValue manaStat;

    private CombatRecoveryProcessor processor;

    // Mock for DefaultEntityStatTypes static methods
    private static final int MOCK_HEALTH_INDEX = 0;
    private static final int MOCK_MANA_INDEX = 1;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Setup entity source
        when(damage.getSource()).thenReturn(entitySource);
        when(entitySource.getRef()).thenReturn(attackerRef);
        when(attackerRef.isValid()).thenReturn(true);

        // Setup entity resolver
        when(entityResolver.resolveTrueAttacker(any(), any())).thenReturn(attackerRef);

        processor = new CombatRecoveryProcessor(entityResolver);
    }

    // ==================== Life Steal Tests ====================

    @Nested
    @DisplayName("Life Steal")
    class LifeStealTests {

        @Test
        @DisplayName("Zero heal amount does nothing")
        void applyLifeSteal_zeroHeal_doesNothing() {
            processor.applyLifeSteal(store, damage, 0f);
            // No interactions should occur
            verify(store, never()).getComponent(any(), any());
        }

        @Test
        @DisplayName("Negative heal amount does nothing")
        void applyLifeSteal_negativeHeal_doesNothing() {
            processor.applyLifeSteal(store, damage, -10f);
            verify(store, never()).getComponent(any(), any());
        }

        @Test
        @DisplayName("Non-entity damage source does nothing")
        void applyLifeSteal_nonEntitySource_doesNothing() {
            // Mock non-entity damage source
            Damage nonEntityDamage = mock(Damage.class);
            when(nonEntityDamage.getSource()).thenReturn(mock(Damage.Source.class));

            processor.applyLifeSteal(store, nonEntityDamage, 50f);
            verify(entityResolver, never()).resolveTrueAttacker(any(), any());
        }

        @Test
        @DisplayName("Invalid attacker ref does nothing")
        void applyLifeSteal_invalidRef_doesNothing() {
            when(attackerRef.isValid()).thenReturn(false);

            processor.applyLifeSteal(store, damage, 50f);
            verify(entityResolver, never()).resolveTrueAttacker(any(), any());
        }

        @Test
        @DisplayName("Null resolved attacker does nothing")
        void applyLifeSteal_nullResolvedAttacker_doesNothing() {
            when(entityResolver.resolveTrueAttacker(any(), any())).thenReturn(null);

            processor.applyLifeSteal(store, damage, 50f);
            verify(store, never()).getComponent(any(), any());
        }
    }

    // ==================== Mana Leech Tests ====================

    @Nested
    @DisplayName("Mana Leech")
    class ManaLeechTests {

        @Test
        @DisplayName("Zero mana amount does nothing")
        void applyManaLeech_zeroMana_doesNothing() {
            processor.applyManaLeech(store, damage, 0f);
            verify(store, never()).getComponent(any(), any());
        }

        @Test
        @DisplayName("Negative mana amount does nothing")
        void applyManaLeech_negativeMana_doesNothing() {
            processor.applyManaLeech(store, damage, -10f);
            verify(store, never()).getComponent(any(), any());
        }

        @Test
        @DisplayName("Non-entity damage source does nothing")
        void applyManaLeech_nonEntitySource_doesNothing() {
            Damage nonEntityDamage = mock(Damage.class);
            when(nonEntityDamage.getSource()).thenReturn(mock(Damage.Source.class));

            processor.applyManaLeech(store, nonEntityDamage, 50f);
            verify(entityResolver, never()).resolveTrueAttacker(any(), any());
        }

        @Test
        @DisplayName("Invalid attacker ref does nothing")
        void applyManaLeech_invalidRef_doesNothing() {
            when(attackerRef.isValid()).thenReturn(false);

            processor.applyManaLeech(store, damage, 50f);
            verify(entityResolver, never()).resolveTrueAttacker(any(), any());
        }
    }

    // ==================== Reflected Damage Tests ====================

    @Nested
    @DisplayName("Reflected Damage")
    class ReflectedDamageTests {

        @Test
        @DisplayName("Zero reflected damage does nothing")
        void applyReflectedDamage_zeroDamage_doesNothing() {
            processor.applyReflectedDamage(store, entitySource, 0f, 1f);
            verify(entityResolver, never()).resolveTrueAttacker(any(), any());
        }

        @Test
        @DisplayName("Negative reflected damage does nothing")
        void applyReflectedDamage_negativeDamage_doesNothing() {
            processor.applyReflectedDamage(store, entitySource, -10f, 1f);
            verify(entityResolver, never()).resolveTrueAttacker(any(), any());
        }

        @Test
        @DisplayName("Invalid attacker ref does nothing")
        void applyReflectedDamage_invalidRef_doesNothing() {
            when(attackerRef.isValid()).thenReturn(false);

            processor.applyReflectedDamage(store, entitySource, 50f, 1f);
            verify(entityResolver, never()).resolveTrueAttacker(any(), any());
        }

        @Test
        @DisplayName("Null entity source ref does nothing")
        void applyReflectedDamage_nullSourceRef_doesNothing() {
            Damage.EntitySource nullRefSource = mock(Damage.EntitySource.class);
            when(nullRefSource.getRef()).thenReturn(null);

            processor.applyReflectedDamage(store, nullRefSource, 50f, 1f);
            verify(entityResolver, never()).resolveTrueAttacker(any(), any());
        }
    }

    // ==================== Block Heal Tests ====================

    @Nested
    @DisplayName("Block Heal")
    class BlockHealTests {

        @Test
        @DisplayName("Zero heal amount does nothing")
        void applyBlockHeal_zeroHeal_doesNothing() {
            processor.applyBlockHeal(0, archetypeChunk, store, 0f);
            verify(archetypeChunk, never()).getReferenceTo(anyInt());
        }

        @Test
        @DisplayName("Negative heal amount does nothing")
        void applyBlockHeal_negativeHeal_doesNothing() {
            processor.applyBlockHeal(0, archetypeChunk, store, -10f);
            verify(archetypeChunk, never()).getReferenceTo(anyInt());
        }

        @Test
        @DisplayName("Null defender ref does nothing")
        void applyBlockHeal_nullRef_doesNothing() {
            when(archetypeChunk.getReferenceTo(0)).thenReturn(null);

            processor.applyBlockHeal(0, archetypeChunk, store, 50f);
            verify(store, never()).getComponent(any(), any());
        }

        @Test
        @DisplayName("Invalid defender ref does nothing")
        void applyBlockHeal_invalidRef_doesNothing() {
            when(archetypeChunk.getReferenceTo(0)).thenReturn(defenderRef);
            when(defenderRef.isValid()).thenReturn(false);

            processor.applyBlockHeal(0, archetypeChunk, store, 50f);
            verify(store, never()).getComponent(any(), any());
        }
    }

    // ==================== Recovery Scenarios ====================
    //
    // NOTE: Full integration tests for stat map interactions require complex
    // mocking of Hytale's static ComponentType system, which is not practical
    // in unit tests. The early guard clauses (tested above) provide the main
    // coverage. Integration tests with a real server would cover the rest.

    @Nested
    @DisplayName("Recovery Scenarios")
    class RecoveryScenarios {

        @Test
        @DisplayName("Life steal with valid positive amount reaches resolver")
        void lifeSteal_validAmount_reachesResolver() {
            // Have resolver return null so we don't need to mock the deep chain
            // The test verifies that early guards passed (since resolver was called)
            when(entityResolver.resolveTrueAttacker(any(), any())).thenReturn(null);

            processor.applyLifeSteal(store, damage, 50f);

            // Verify the resolver was invoked (meaning early guards passed)
            verify(entityResolver).resolveTrueAttacker(store, attackerRef);
        }

        @Test
        @DisplayName("Mana leech with valid positive amount reaches resolver")
        void manaLeech_validAmount_reachesResolver() {
            when(entityResolver.resolveTrueAttacker(any(), any())).thenReturn(null);

            processor.applyManaLeech(store, damage, 50f);

            verify(entityResolver).resolveTrueAttacker(store, attackerRef);
        }

        @Test
        @DisplayName("Block heal with null ref from chunk does nothing")
        void blockHeal_nullRefFromChunk_doesNothing() {
            // Return null from the archetype chunk - method should exit early
            when(archetypeChunk.getReferenceTo(0)).thenReturn(null);

            processor.applyBlockHeal(0, archetypeChunk, store, 50f);

            verify(archetypeChunk).getReferenceTo(0);
            verify(store, never()).getComponent(any(), any());
        }
    }

    // ==================== Edge Cases ====================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Very large heal amount passes early guards")
        void lifeSteal_veryLargeAmount_passesGuards() {
            // Have resolver return null to avoid deep chain
            when(entityResolver.resolveTrueAttacker(any(), any())).thenReturn(null);

            processor.applyLifeSteal(store, damage, Float.MAX_VALUE);

            verify(entityResolver).resolveTrueAttacker(store, attackerRef);
        }

        @Test
        @DisplayName("Very small positive heal amount passes guards")
        void lifeSteal_verySmallAmount_passesGuards() {
            when(entityResolver.resolveTrueAttacker(any(), any())).thenReturn(null);

            processor.applyLifeSteal(store, damage, 0.0001f);

            verify(entityResolver).resolveTrueAttacker(store, attackerRef);
        }

        @Test
        @DisplayName("Reflected damage with min HP parameter works")
        void reflectedDamage_minHpParameter_accepted() {
            when(entityResolver.resolveTrueAttacker(any(), any())).thenReturn(null);

            processor.applyReflectedDamage(store, entitySource, 1000f, 1f);

            verify(entityResolver).resolveTrueAttacker(store, attackerRef);
        }
    }
}
