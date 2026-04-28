package io.github.larsonix.trailoforbis.combat.triggers;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.combat.resolution.CombatEntityResolver;
import io.github.larsonix.trailoforbis.skilltree.conditional.ConditionalTriggerSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CombatTriggerHandler.
 *
 * <p>Tests skill tree trigger firing during combat events:
 * <ul>
 *   <li>ON_KILL trigger for attackers</li>
 *   <li>ON_CRIT trigger for critical hits</li>
 *   <li>WHEN_HIT trigger for defenders</li>
 *   <li>ON_EVADE trigger for successful evasion</li>
 *   <li>ON_BLOCK trigger for successful blocks</li>
 * </ul>
 */
public class CombatTriggerHandlerTest {

    @Mock
    private CombatEntityResolver entityResolver;

    @Mock
    private ConditionalTriggerSystem triggerSystem;

    @Mock
    private Store<EntityStore> store;

    @Mock
    private Damage damage;

    @Mock
    private ArchetypeChunk<EntityStore> archetypeChunk;

    private CombatTriggerHandler handler;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        handler = new CombatTriggerHandler(entityResolver, triggerSystem);
    }

    // ==================== Constructor Tests ====================

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("Creates handler with trigger system")
        void constructor_withTriggerSystem_succeeds() {
            CombatTriggerHandler h = new CombatTriggerHandler(entityResolver, triggerSystem);
            assertNotNull(h);
        }

        @Test
        @DisplayName("Creates handler with null trigger system")
        void constructor_nullTriggerSystem_succeeds() {
            CombatTriggerHandler h = new CombatTriggerHandler(entityResolver, null);
            assertNotNull(h);
        }
    }

    // ==================== ON_KILL Trigger Tests ====================

    @Nested
    @DisplayName("ON_KILL Trigger")
    class OnKillTriggerTests {

        @Test
        @DisplayName("No trigger when attacker UUID is null")
        void fireOnKillTrigger_nullAttacker_noTrigger() {
            when(entityResolver.getAttackerPlayerUuid(any(), any())).thenReturn(null);

            handler.fireOnKillTrigger(store, damage);

            // Trigger system should not be invoked
            verifyNoInteractions(triggerSystem);
        }

        @Test
        @DisplayName("Calls resolver to get attacker UUID")
        void fireOnKillTrigger_callsResolver() {
            UUID attackerUuid = UUID.randomUUID();
            when(entityResolver.getAttackerPlayerUuid(store, damage)).thenReturn(attackerUuid);

            handler.fireOnKillTrigger(store, damage);

            verify(entityResolver).getAttackerPlayerUuid(store, damage);
        }
    }

    // ==================== ON_CRIT Trigger Tests ====================

    @Nested
    @DisplayName("ON_CRIT Trigger")
    class OnCritTriggerTests {

        @Test
        @DisplayName("No trigger when attacker UUID is null")
        void fireOnCritTrigger_nullAttacker_noTrigger() {
            when(entityResolver.getAttackerPlayerUuid(any(), any())).thenReturn(null);

            handler.fireOnCritTrigger(store, damage);

            verifyNoInteractions(triggerSystem);
        }

        @Test
        @DisplayName("Calls resolver to get attacker UUID")
        void fireOnCritTrigger_callsResolver() {
            UUID attackerUuid = UUID.randomUUID();
            when(entityResolver.getAttackerPlayerUuid(store, damage)).thenReturn(attackerUuid);

            handler.fireOnCritTrigger(store, damage);

            verify(entityResolver).getAttackerPlayerUuid(store, damage);
        }
    }

    // ==================== WHEN_HIT Trigger Tests ====================

    @Nested
    @DisplayName("WHEN_HIT Trigger")
    class WhenHitTriggerTests {

        @Test
        @DisplayName("No trigger when defender UUID is null")
        void fireWhenHitTrigger_nullDefender_noTrigger() {
            when(entityResolver.getDefenderUuid(anyInt(), any(), any())).thenReturn(null);

            handler.fireWhenHitTrigger(0, archetypeChunk, store);

            verifyNoInteractions(triggerSystem);
        }

        @Test
        @DisplayName("Calls resolver to get defender UUID")
        void fireWhenHitTrigger_callsResolver() {
            UUID defenderUuid = UUID.randomUUID();
            when(entityResolver.getDefenderUuid(0, archetypeChunk, store)).thenReturn(defenderUuid);

            handler.fireWhenHitTrigger(0, archetypeChunk, store);

            verify(entityResolver).getDefenderUuid(0, archetypeChunk, store);
        }
    }

    // ==================== ON_EVADE Trigger Tests ====================

    @Nested
    @DisplayName("ON_EVADE Trigger")
    class OnEvadeTriggerTests {

        @Test
        @DisplayName("No trigger when defender UUID is null")
        void fireOnEvadeTrigger_nullDefender_noTrigger() {
            when(entityResolver.getDefenderUuid(anyInt(), any(), any())).thenReturn(null);

            handler.fireOnEvadeTrigger(0, archetypeChunk, store);

            verifyNoInteractions(triggerSystem);
        }

        @Test
        @DisplayName("Calls resolver to get defender UUID")
        void fireOnEvadeTrigger_callsResolver() {
            UUID defenderUuid = UUID.randomUUID();
            when(entityResolver.getDefenderUuid(0, archetypeChunk, store)).thenReturn(defenderUuid);

            handler.fireOnEvadeTrigger(0, archetypeChunk, store);

            verify(entityResolver).getDefenderUuid(0, archetypeChunk, store);
        }
    }

    // ==================== ON_BLOCK Trigger Tests ====================

    @Nested
    @DisplayName("ON_BLOCK Trigger")
    class OnBlockTriggerTests {

        @Test
        @DisplayName("No trigger when defender UUID is null")
        void fireOnBlockTrigger_nullDefender_noTrigger() {
            when(entityResolver.getDefenderUuid(anyInt(), any(), any())).thenReturn(null);

            handler.fireOnBlockTrigger(0, archetypeChunk, store);

            verifyNoInteractions(triggerSystem);
        }

        @Test
        @DisplayName("Calls resolver to get defender UUID")
        void fireOnBlockTrigger_callsResolver() {
            UUID defenderUuid = UUID.randomUUID();
            when(entityResolver.getDefenderUuid(0, archetypeChunk, store)).thenReturn(defenderUuid);

            handler.fireOnBlockTrigger(0, archetypeChunk, store);

            verify(entityResolver).getDefenderUuid(0, archetypeChunk, store);
        }
    }

    // ==================== Null Trigger System Tests ====================

    @Nested
    @DisplayName("Null Trigger System")
    class NullTriggerSystemTests {

        private CombatTriggerHandler handlerWithNullSystem;

        @BeforeEach
        void setUp() {
            handlerWithNullSystem = new CombatTriggerHandler(entityResolver, null);
        }

        @Test
        @DisplayName("ON_KILL does not throw with null system")
        void fireOnKillTrigger_nullSystem_noException() {
            UUID attackerUuid = UUID.randomUUID();
            when(entityResolver.getAttackerPlayerUuid(any(), any())).thenReturn(attackerUuid);

            assertDoesNotThrow(() ->
                handlerWithNullSystem.fireOnKillTrigger(store, damage)
            );
        }

        @Test
        @DisplayName("ON_CRIT does not throw with null system")
        void fireOnCritTrigger_nullSystem_noException() {
            UUID attackerUuid = UUID.randomUUID();
            when(entityResolver.getAttackerPlayerUuid(any(), any())).thenReturn(attackerUuid);

            assertDoesNotThrow(() ->
                handlerWithNullSystem.fireOnCritTrigger(store, damage)
            );
        }

        @Test
        @DisplayName("WHEN_HIT does not throw with null system")
        void fireWhenHitTrigger_nullSystem_noException() {
            UUID defenderUuid = UUID.randomUUID();
            when(entityResolver.getDefenderUuid(anyInt(), any(), any())).thenReturn(defenderUuid);

            assertDoesNotThrow(() ->
                handlerWithNullSystem.fireWhenHitTrigger(0, archetypeChunk, store)
            );
        }

        @Test
        @DisplayName("ON_EVADE does not throw with null system")
        void fireOnEvadeTrigger_nullSystem_noException() {
            UUID defenderUuid = UUID.randomUUID();
            when(entityResolver.getDefenderUuid(anyInt(), any(), any())).thenReturn(defenderUuid);

            assertDoesNotThrow(() ->
                handlerWithNullSystem.fireOnEvadeTrigger(0, archetypeChunk, store)
            );
        }

        @Test
        @DisplayName("ON_BLOCK does not throw with null system")
        void fireOnBlockTrigger_nullSystem_noException() {
            UUID defenderUuid = UUID.randomUUID();
            when(entityResolver.getDefenderUuid(anyInt(), any(), any())).thenReturn(defenderUuid);

            assertDoesNotThrow(() ->
                handlerWithNullSystem.fireOnBlockTrigger(0, archetypeChunk, store)
            );
        }
    }

    // ==================== Skill Tree Data Tests ====================

    @Nested
    @DisplayName("Skill Tree Data")
    class SkillTreeDataTests {

        @Test
        @DisplayName("getSkillTreeData returns null for null UUID")
        void getSkillTreeData_nullUuid_returnsNull() {
            assertNull(handler.getSkillTreeData(null));
        }

        @Test
        @DisplayName("getSkillTreeData handles missing service gracefully")
        void getSkillTreeData_missingService_returnsNull() {
            // ServiceRegistry returns empty optional when service not registered
            UUID uuid = UUID.randomUUID();

            // Should not throw, should return null
            assertDoesNotThrow(() -> handler.getSkillTreeData(uuid));
        }
    }
}
