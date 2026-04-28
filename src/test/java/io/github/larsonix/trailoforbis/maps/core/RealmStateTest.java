package io.github.larsonix.trailoforbis.maps.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RealmState")
class RealmStateTest {

    @Nested
    @DisplayName("allowsEntry")
    class AllowsEntry {

        @Test
        @DisplayName("CREATING should not allow entry")
        void creatingNoEntry() {
            assertFalse(RealmState.CREATING.allowsEntry());
        }

        @Test
        @DisplayName("READY should allow entry")
        void readyAllowsEntry() {
            assertTrue(RealmState.READY.allowsEntry());
        }

        @Test
        @DisplayName("ACTIVE should allow entry")
        void activeAllowsEntry() {
            assertTrue(RealmState.ACTIVE.allowsEntry());
        }

        @Test
        @DisplayName("ENDING should not allow entry (prevents re-entry after victory)")
        void endingNoEntry() {
            assertFalse(RealmState.ENDING.allowsEntry());
        }

        @Test
        @DisplayName("CLOSING should not allow entry")
        void closingNoEntry() {
            assertFalse(RealmState.CLOSING.allowsEntry());
        }
    }

    @Nested
    @DisplayName("isCombatActive")
    class IsCombatActive {

        @Test
        @DisplayName("Only ACTIVE should have combat")
        void onlyActiveCombat() {
            assertFalse(RealmState.CREATING.isCombatActive());
            assertFalse(RealmState.READY.isCombatActive());
            assertTrue(RealmState.ACTIVE.isCombatActive());
            assertFalse(RealmState.ENDING.isCombatActive());
            assertFalse(RealmState.CLOSING.isCombatActive());
        }
    }

    @Nested
    @DisplayName("isPortalActive")
    class IsPortalActive {

        @Test
        @DisplayName("Portal active in READY and ACTIVE")
        void portalActiveStates() {
            assertFalse(RealmState.CREATING.isPortalActive());
            assertTrue(RealmState.READY.isPortalActive());
            assertTrue(RealmState.ACTIVE.isPortalActive());
            assertFalse(RealmState.ENDING.isPortalActive());
            assertFalse(RealmState.CLOSING.isPortalActive());
        }
    }

    @Nested
    @DisplayName("isTerminal")
    class IsTerminal {

        @Test
        @DisplayName("ENDING and CLOSING are terminal")
        void terminalStates() {
            assertFalse(RealmState.CREATING.isTerminal());
            assertFalse(RealmState.READY.isTerminal());
            assertFalse(RealmState.ACTIVE.isTerminal());
            assertTrue(RealmState.ENDING.isTerminal());
            assertTrue(RealmState.CLOSING.isTerminal());
        }
    }

    @Nested
    @DisplayName("getNextState")
    class GetNextState {

        @Test
        @DisplayName("Should follow lifecycle order")
        void lifecycleOrder() {
            assertEquals(RealmState.READY, RealmState.CREATING.getNextState());
            assertEquals(RealmState.ACTIVE, RealmState.READY.getNextState());
            assertEquals(RealmState.ENDING, RealmState.ACTIVE.getNextState());
            assertEquals(RealmState.CLOSING, RealmState.ENDING.getNextState());
            assertEquals(RealmState.CLOSING, RealmState.CLOSING.getNextState());
        }
    }

    @Nested
    @DisplayName("canTransitionTo")
    class CanTransitionTo {

        @Test
        @DisplayName("Can always transition to CLOSING")
        void alwaysCanClose() {
            assertTrue(RealmState.CREATING.canTransitionTo(RealmState.CLOSING));
            assertTrue(RealmState.READY.canTransitionTo(RealmState.CLOSING));
            assertTrue(RealmState.ACTIVE.canTransitionTo(RealmState.CLOSING));
            assertTrue(RealmState.ENDING.canTransitionTo(RealmState.CLOSING));
        }

        @Test
        @DisplayName("Cannot skip states")
        void cannotSkipStates() {
            assertFalse(RealmState.CREATING.canTransitionTo(RealmState.ACTIVE));
            assertFalse(RealmState.READY.canTransitionTo(RealmState.ENDING));
            assertFalse(RealmState.CREATING.canTransitionTo(RealmState.ENDING));
        }

        @Test
        @DisplayName("Cannot transition backwards")
        void cannotGoBackwards() {
            assertFalse(RealmState.ACTIVE.canTransitionTo(RealmState.READY));
            assertFalse(RealmState.ENDING.canTransitionTo(RealmState.ACTIVE));
        }

        @Test
        @DisplayName("CLOSING cannot transition anywhere")
        void closingCantTransition() {
            assertFalse(RealmState.CLOSING.canTransitionTo(RealmState.CREATING));
            assertFalse(RealmState.CLOSING.canTransitionTo(RealmState.READY));
            assertFalse(RealmState.CLOSING.canTransitionTo(RealmState.ACTIVE));
        }
    }
}
