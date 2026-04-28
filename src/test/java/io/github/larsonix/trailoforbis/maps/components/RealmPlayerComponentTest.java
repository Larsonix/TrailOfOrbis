package io.github.larsonix.trailoforbis.maps.components;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link RealmPlayerComponent}.
 */
@DisplayName("RealmPlayerComponent")
class RealmPlayerComponentTest {

    private RealmPlayerComponent component;

    @BeforeEach
    void setUp() {
        component = new RealmPlayerComponent();
    }

    // ═══════════════════════════════════════════════════════════════════
    // CONSTRUCTION
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("default constructor initializes with null realmId")
        void defaultConstructorInitializesWithNullRealmId() {
            assertNull(component.getRealmId());
        }

        @Test
        @DisplayName("default constructor initializes with zero entry time")
        void defaultConstructorInitializesWithZeroEntryTime() {
            assertEquals(0, component.getEntryTimeMs());
        }

        @Test
        @DisplayName("default constructor initializes with zero kill count")
        void defaultConstructorInitializesWithZeroKillCount() {
            assertEquals(0, component.getKillCount());
        }

        @Test
        @DisplayName("default constructor initializes with zero damage dealt")
        void defaultConstructorInitializesWithZeroDamageDealt() {
            assertEquals(0.0, component.getDamageDealt(), 0.001);
        }

        @Test
        @DisplayName("default constructor initializes with zero damage taken")
        void defaultConstructorInitializesWithZeroDamageTaken() {
            assertEquals(0.0, component.getDamageTaken(), 0.001);
        }

        @Test
        @DisplayName("default constructor initializes with zero death count")
        void defaultConstructorInitializesWithZeroDeathCount() {
            assertEquals(0, component.getDeathCount());
        }

        @Test
        @DisplayName("default constructor initializes hasCompleted to false")
        void defaultConstructorInitializesHasCompletedToFalse() {
            assertFalse(component.hasCompleted());
        }

        @Test
        @DisplayName("default constructor initializes canRespawn to true")
        void defaultConstructorInitializesCanRespawnToTrue() {
            assertTrue(component.canRespawn());
        }

        @Test
        @DisplayName("default constructor initializes isAlive to true")
        void defaultConstructorInitializesIsAliveToTrue() {
            assertTrue(component.isAlive());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // INITIALIZATION
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Initialization")
    class Initialization {

        @Test
        @DisplayName("initialize sets realm ID")
        void initializeSetsRealmId() {
            UUID realmId = UUID.randomUUID();
            component.initialize(realmId);

            assertEquals(realmId, component.getRealmId());
        }

        @Test
        @DisplayName("initialize sets entry time to current time")
        void initializeSetsEntryTime() {
            long beforeInit = System.currentTimeMillis();
            component.initialize(UUID.randomUUID());
            long afterInit = System.currentTimeMillis();

            assertTrue(component.getEntryTimeMs() >= beforeInit);
            assertTrue(component.getEntryTimeMs() <= afterInit);
        }

        @Test
        @DisplayName("initialize resets stats to zero")
        void initializeResetsStatsToZero() {
            // Pre-set some values
            component.incrementKillCount();
            component.addDamageDealt(100);
            component.addDamageTaken(50);
            component.incrementDeathCount();

            // Initialize
            component.initialize(UUID.randomUUID());

            assertEquals(0, component.getKillCount());
            assertEquals(0.0, component.getDamageDealt(), 0.001);
            assertEquals(0.0, component.getDamageTaken(), 0.001);
            assertEquals(0, component.getDeathCount());
        }

        @Test
        @DisplayName("initialize resets hasCompleted to false")
        void initializeResetsHasCompletedToFalse() {
            component.setCompleted(true);
            component.initialize(UUID.randomUUID());

            assertFalse(component.hasCompleted());
        }

        @Test
        @DisplayName("initialize resets canRespawn to true")
        void initializeResetsCanRespawnToTrue() {
            component.setCanRespawn(false);
            component.initialize(UUID.randomUUID());

            assertTrue(component.canRespawn());
        }

        @Test
        @DisplayName("initialize resets isAlive to true")
        void initializeResetsIsAliveToTrue() {
            component.markDead();
            component.initialize(UUID.randomUUID());

            assertTrue(component.isAlive());
        }

        @Test
        @DisplayName("initialize throws on null realm ID")
        void initializeThrowsOnNullRealmId() {
            assertThrows(NullPointerException.class, () ->
                    component.initialize(null));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // ALIVE STATUS TRACKING
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Alive Status Tracking")
    class AliveStatusTracking {

        @Test
        @DisplayName("markDead sets isAlive to false")
        void markDeadSetsIsAliveToFalse() {
            component.markDead();

            assertFalse(component.isAlive());
        }

        @Test
        @DisplayName("markAlive sets isAlive to true")
        void markAliveSetsIsAliveToTrue() {
            component.markDead();
            component.markAlive();

            assertTrue(component.isAlive());
        }

        @Test
        @DisplayName("setAlive(true) marks player as alive")
        void setAliveTrueMarksPlayerAsAlive() {
            component.markDead();
            component.setAlive(true);

            assertTrue(component.isAlive());
        }

        @Test
        @DisplayName("setAlive(false) marks player as dead")
        void setAliveFalseMarksPlayerAsDead() {
            component.setAlive(false);

            assertFalse(component.isAlive());
        }

        @Test
        @DisplayName("multiple markDead calls are idempotent")
        void multipleMarkDeadCallsAreIdempotent() {
            component.markDead();
            component.markDead();
            component.markDead();

            assertFalse(component.isAlive());
        }

        @Test
        @DisplayName("multiple markAlive calls are idempotent")
        void multipleMarkAliveCallsAreIdempotent() {
            component.markDead();
            component.markAlive();
            component.markAlive();
            component.markAlive();

            assertTrue(component.isAlive());
        }

        @Test
        @DisplayName("death-respawn cycle works correctly")
        void deathRespawnCycleWorksCorrectly() {
            assertTrue(component.isAlive(), "Should start alive");

            component.markDead();
            assertFalse(component.isAlive(), "Should be dead after markDead");

            component.markAlive();
            assertTrue(component.isAlive(), "Should be alive after markAlive");

            component.markDead();
            assertFalse(component.isAlive(), "Should be dead again");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // DEATH COUNT TRACKING
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Death Count Tracking")
    class DeathCountTracking {

        @Test
        @DisplayName("incrementDeathCount increases count by one")
        void incrementDeathCountIncreasesCountByOne() {
            component.incrementDeathCount();
            assertEquals(1, component.getDeathCount());

            component.incrementDeathCount();
            assertEquals(2, component.getDeathCount());

            component.incrementDeathCount();
            assertEquals(3, component.getDeathCount());
        }

        @Test
        @DisplayName("death count and alive status are independent")
        void deathCountAndAliveStatusAreIndependent() {
            // Death count doesn't affect alive status directly
            component.incrementDeathCount();
            assertTrue(component.isAlive(), "incrementDeathCount should not change alive status");

            // And vice versa
            component.markDead();
            assertEquals(1, component.getDeathCount(), "markDead should not change death count");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // KILL TRACKING
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Kill Tracking")
    class KillTracking {

        @Test
        @DisplayName("recordKill increases count by one")
        void recordKillIncreasesCountByOne() {
            component.incrementKillCount();
            assertEquals(1, component.getKillCount());

            component.incrementKillCount();
            assertEquals(2, component.getKillCount());
        }

        @Test
        @DisplayName("many kills are tracked correctly")
        void manyKillsAreTrackedCorrectly() {
            for (int i = 0; i < 100; i++) {
                component.incrementKillCount();
            }
            assertEquals(100, component.getKillCount());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // DAMAGE TRACKING
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Damage Tracking")
    class DamageTracking {

        @Test
        @DisplayName("recordDamageDealt accumulates damage")
        void recordDamageDealtAccumulatesDamage() {
            component.addDamageDealt(50.0);
            assertEquals(50.0, component.getDamageDealt(), 0.001);

            component.addDamageDealt(30.5);
            assertEquals(80.5, component.getDamageDealt(), 0.001);
        }

        @Test
        @DisplayName("recordDamageTaken accumulates damage")
        void recordDamageTakenAccumulatesDamage() {
            component.addDamageTaken(25.0);
            assertEquals(25.0, component.getDamageTaken(), 0.001);

            component.addDamageTaken(15.5);
            assertEquals(40.5, component.getDamageTaken(), 0.001);
        }

        @Test
        @DisplayName("handles zero damage")
        void handlesZeroDamage() {
            component.addDamageDealt(0.0);
            component.addDamageTaken(0.0);

            assertEquals(0.0, component.getDamageDealt(), 0.001);
            assertEquals(0.0, component.getDamageTaken(), 0.001);
        }

        @Test
        @DisplayName("handles very large damage values")
        void handlesVeryLargeDamageValues() {
            component.addDamageDealt(1_000_000.0);
            component.addDamageTaken(500_000.0);

            assertEquals(1_000_000.0, component.getDamageDealt(), 0.001);
            assertEquals(500_000.0, component.getDamageTaken(), 0.001);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // ELAPSED TIME
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Elapsed Time")
    class ElapsedTime {

        @Test
        @DisplayName("getElapsedSeconds returns 0 before initialization")
        void getElapsedSecondsReturnsZeroBeforeInit() {
            assertEquals(0, component.getElapsedSeconds());
        }

        @Test
        @DisplayName("getElapsedSeconds returns positive after initialization")
        void getElapsedSecondsReturnsPositiveAfterInit() throws InterruptedException {
            component.initialize(UUID.randomUUID());
            // Small delay to ensure elapsed time > 0
            Thread.sleep(10);
            assertTrue(component.getElapsedSeconds() >= 0);
        }

        @Test
        @DisplayName("getElapsedMs returns 0 before initialization")
        void getElapsedMsReturnsZeroBeforeInit() {
            assertEquals(0, component.getElapsedMs());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // COMPLETION STATUS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Completion Status")
    class CompletionStatus {

        @Test
        @DisplayName("setCompleted(true) marks player as completed")
        void setCompletedTrueMarksPlayerAsCompleted() {
            component.setCompleted(true);
            assertTrue(component.hasCompleted());
        }

        @Test
        @DisplayName("setCompleted(false) marks player as not completed")
        void setCompletedFalseMarksPlayerAsNotCompleted() {
            component.setCompleted(true);
            component.setCompleted(false);
            assertFalse(component.hasCompleted());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // RESPAWN STATUS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Respawn Status")
    class RespawnStatus {

        @Test
        @DisplayName("setCanRespawn(false) disables respawning")
        void setCanRespawnFalseDisablesRespawning() {
            component.setCanRespawn(false);
            assertFalse(component.canRespawn());
        }

        @Test
        @DisplayName("setCanRespawn(true) enables respawning")
        void setCanRespawnTrueEnablesRespawning() {
            component.setCanRespawn(false);
            component.setCanRespawn(true);
            assertTrue(component.canRespawn());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // CLONE
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Clone")
    class Clone {

        @Test
        @DisplayName("clone creates independent copy")
        void cloneCreatesIndependentCopy() {
            UUID realmId = UUID.randomUUID();
            component.initialize(realmId);
            component.incrementKillCount();
            component.incrementKillCount();
            component.addDamageDealt(100);
            component.addDamageTaken(50);
            component.incrementDeathCount();
            component.setCompleted(true);
            component.setCanRespawn(false);
            component.markDead();

            // clone() returns Component<EntityStore>, cast to check values
            RealmPlayerComponent cloned = (RealmPlayerComponent) component.clone();

            // Verify all values are copied
            assertEquals(realmId, cloned.getRealmId());
            assertEquals(2, cloned.getKillCount());
            assertEquals(100.0, cloned.getDamageDealt(), 0.001);
            assertEquals(50.0, cloned.getDamageTaken(), 0.001);
            assertEquals(1, cloned.getDeathCount());
            assertTrue(cloned.hasCompleted());
            assertFalse(cloned.canRespawn());
            assertFalse(cloned.isAlive());
        }

        @Test
        @DisplayName("clone is independent of original")
        void cloneIsIndependentOfOriginal() {
            component.initialize(UUID.randomUUID());
            RealmPlayerComponent cloned = (RealmPlayerComponent) component.clone();

            // Modify original
            component.incrementKillCount();
            component.markDead();

            // Clone should be unchanged
            assertEquals(0, cloned.getKillCount());
            assertTrue(cloned.isAlive());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // TO STRING
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("toString")
    class ToString {

        @Test
        @DisplayName("toString contains realm ID")
        void toStringContainsRealmId() {
            UUID realmId = UUID.randomUUID();
            component.initialize(realmId);

            String str = component.toString();
            assertTrue(str.contains(realmId.toString().substring(0, 8)));
        }

        @Test
        @DisplayName("toString contains alive status")
        void toStringContainsAliveStatus() {
            component.initialize(UUID.randomUUID());

            String str = component.toString();
            assertTrue(str.contains("alive=true"));

            component.markDead();
            str = component.toString();
            assertTrue(str.contains("alive=false"));
        }

        @Test
        @DisplayName("toString contains kills and damage")
        void toStringContainsKillsAndDamage() {
            component.initialize(UUID.randomUUID());
            component.incrementKillCount();
            component.incrementKillCount();
            component.addDamageDealt(150);

            String str = component.toString();
            assertTrue(str.contains("kills=2"));
            assertTrue(str.contains("dmg=150"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // REALM MEMBERSHIP
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Realm Membership")
    class RealmMembership {

        @Test
        @DisplayName("isInRealm returns false when realmId is null")
        void isInRealmReturnsFalseWhenRealmIdIsNull() {
            assertFalse(component.isInRealm());
        }

        @Test
        @DisplayName("isInRealm returns true when realmId is set")
        void isInRealmReturnsTrueWhenRealmIdIsSet() {
            component.initialize(UUID.randomUUID());
            assertTrue(component.isInRealm());
        }

        @Test
        @DisplayName("isInRealm(UUID) returns true for matching realm")
        void isInRealmWithUuidReturnsTrueForMatchingRealm() {
            UUID realmId = UUID.randomUUID();
            component.initialize(realmId);

            assertTrue(component.isInRealm(realmId));
        }

        @Test
        @DisplayName("isInRealm(UUID) returns false for different realm")
        void isInRealmWithUuidReturnsFalseForDifferentRealm() {
            component.initialize(UUID.randomUUID());

            assertFalse(component.isInRealm(UUID.randomUUID()));
        }

        @Test
        @DisplayName("isInRealm(UUID) returns false when not in any realm")
        void isInRealmWithUuidReturnsFalseWhenNotInAnyRealm() {
            assertFalse(component.isInRealm(UUID.randomUUID()));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // PERFORMANCE SCORE
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Performance Score")
    class PerformanceScore {

        @Test
        @DisplayName("returns 0 with no activity")
        void returnsZeroWithNoActivity() {
            component.initialize(UUID.randomUUID());
            assertEquals(0, component.calculatePerformanceScore());
        }

        @Test
        @DisplayName("increases with kills")
        void increasesWithKills() {
            component.initialize(UUID.randomUUID());
            component.incrementKillCount();
            component.incrementKillCount();

            // 2 kills * 10 = 20, with no-death bonus * 1.25 = 25
            assertEquals(25, component.calculatePerformanceScore());
        }

        @Test
        @DisplayName("increases with damage dealt")
        void increasesWithDamageDealt() {
            component.initialize(UUID.randomUUID());
            component.incrementKillCount(); // Need at least one kill for no-death bonus
            component.addDamageDealt(1000);

            // 1 kill * 10 + 1000 damage / 100 = 10 + 10 = 20
            // With no-death bonus: 20 * 1.25 = 25
            assertEquals(25, component.calculatePerformanceScore());
        }

        @Test
        @DisplayName("decreases with deaths")
        void decreasesWithDeaths() {
            component.initialize(UUID.randomUUID());
            component.incrementKillCount();
            component.incrementKillCount();
            component.incrementKillCount();
            component.incrementDeathCount();

            // 3 kills * 10 = 30, -1 death * 15 = 15, no-death bonus lost
            assertEquals(15, component.calculatePerformanceScore());
        }

        @Test
        @DisplayName("never goes negative")
        void neverGoesNegative() {
            component.initialize(UUID.randomUUID());
            component.incrementDeathCount();
            component.incrementDeathCount();
            component.incrementDeathCount();

            // Many deaths with no kills should cap at 0
            assertEquals(0, component.calculatePerformanceScore());
        }
    }
}
