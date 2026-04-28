package io.github.larsonix.trailoforbis.combat.deathrecap;

import io.github.larsonix.trailoforbis.combat.DamageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link DeathRecapTracker}.
 *
 * <p>Tests the combat snapshot tracking for the death recap system.
 * Verifies recording, retrieval, cleanup, and thread-safe operations.
 */
@DisplayName("DeathRecapTracker")
class DeathRecapTrackerTest {

    private DeathRecapConfig config;
    private DeathRecapTracker tracker;

    @BeforeEach
    void setUp() {
        config = new DeathRecapConfig();
        tracker = new DeathRecapTracker(config);
    }

    /**
     * Helper to create a simple combat snapshot for testing.
     */
    private CombatSnapshot createSnapshot(String attackerName, float damage) {
        float healthBefore = 100f;
        float healthAfter = healthBefore - damage;
        return new CombatSnapshot(
            System.currentTimeMillis(),
            attackerName,
            "mob",
            10,
            null,
            damage, 0, 0, false, 1.0f, damage,
            0, 0, 0, 0, damage,
            null, null, 0,
            null, null,  // defenderRawResistances, attackerPenetration
            damage,
            DamageType.PHYSICAL,
            false,
            100, healthBefore, healthAfter, 0
        );
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Record Damage Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Record Damage")
    class RecordDamageTests {

        @Test
        @DisplayName("Records damage for player")
        void recordsDamage_forPlayer() {
            UUID playerId = UUID.randomUUID();
            CombatSnapshot snapshot = createSnapshot("Zombie", 50);

            tracker.recordDamage(playerId, snapshot);

            CombatSnapshot result = tracker.peekLastDamage(playerId);
            assertNotNull(result);
            assertEquals("Zombie", result.attackerName());
            assertEquals(50, result.finalDamage());
        }

        @Test
        @DisplayName("Overwrites previous damage record")
        void overwritesPreviousDamageRecord() {
            UUID playerId = UUID.randomUUID();
            CombatSnapshot first = createSnapshot("Zombie", 30);
            CombatSnapshot second = createSnapshot("Skeleton", 50);

            tracker.recordDamage(playerId, first);
            tracker.recordDamage(playerId, second);

            CombatSnapshot result = tracker.peekLastDamage(playerId);
            assertEquals("Skeleton", result.attackerName());
            assertEquals(50, result.finalDamage());
        }

        @Test
        @DisplayName("Records damage for multiple players")
        void recordsDamage_forMultiplePlayers() {
            UUID player1 = UUID.randomUUID();
            UUID player2 = UUID.randomUUID();
            CombatSnapshot snapshot1 = createSnapshot("Zombie", 30);
            CombatSnapshot snapshot2 = createSnapshot("Creeper", 100);

            tracker.recordDamage(player1, snapshot1);
            tracker.recordDamage(player2, snapshot2);

            assertEquals("Zombie", tracker.peekLastDamage(player1).attackerName());
            assertEquals("Creeper", tracker.peekLastDamage(player2).attackerName());
        }

        @Test
        @DisplayName("Does not record when disabled")
        void doesNotRecord_whenDisabled() {
            config.setEnabled(false);
            UUID playerId = UUID.randomUUID();
            CombatSnapshot snapshot = createSnapshot("Zombie", 50);

            tracker.recordDamage(playerId, snapshot);

            assertNull(tracker.peekLastDamage(playerId));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Get Killing Blow Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Get Killing Blow")
    class GetKillingBlowTests {

        @Test
        @DisplayName("Returns snapshot and removes it")
        void returnsSnapshot_andRemovesIt() {
            UUID playerId = UUID.randomUUID();
            CombatSnapshot snapshot = createSnapshot("Dragon", 200);
            tracker.recordDamage(playerId, snapshot);

            // First call returns the snapshot
            Optional<CombatSnapshot> result = tracker.getKillingBlow(playerId);
            assertTrue(result.isPresent());
            assertEquals("Dragon", result.get().attackerName());

            // Second call returns empty (was removed)
            Optional<CombatSnapshot> secondCall = tracker.getKillingBlow(playerId);
            assertTrue(secondCall.isEmpty());
        }

        @Test
        @DisplayName("Returns empty for unknown player")
        void returnsEmpty_forUnknownPlayer() {
            UUID playerId = UUID.randomUUID();

            Optional<CombatSnapshot> result = tracker.getKillingBlow(playerId);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Removes snapshot even when returning it")
        void removesSnapshot_evenWhenReturning() {
            UUID playerId = UUID.randomUUID();
            CombatSnapshot snapshot = createSnapshot("Boss", 500);
            tracker.recordDamage(playerId, snapshot);

            tracker.getKillingBlow(playerId);

            assertNull(tracker.peekLastDamage(playerId));
            assertEquals(0, tracker.getTrackedPlayerCount());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Peek Last Damage Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Peek Last Damage")
    class PeekLastDamageTests {

        @Test
        @DisplayName("Returns snapshot without removing it")
        void returnsSnapshot_withoutRemovingIt() {
            UUID playerId = UUID.randomUUID();
            CombatSnapshot snapshot = createSnapshot("Spider", 25);
            tracker.recordDamage(playerId, snapshot);

            // Multiple peeks should all return the same snapshot
            CombatSnapshot first = tracker.peekLastDamage(playerId);
            CombatSnapshot second = tracker.peekLastDamage(playerId);
            CombatSnapshot third = tracker.peekLastDamage(playerId);

            assertNotNull(first);
            assertNotNull(second);
            assertNotNull(third);
            assertEquals(first, second);
            assertEquals(second, third);
        }

        @Test
        @DisplayName("Returns null for unknown player")
        void returnsNull_forUnknownPlayer() {
            UUID playerId = UUID.randomUUID();

            CombatSnapshot result = tracker.peekLastDamage(playerId);

            assertNull(result);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Player Disconnect Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Player Disconnect")
    class PlayerDisconnectTests {

        @Test
        @DisplayName("Removes player data on disconnect")
        void removesPlayerData_onDisconnect() {
            UUID playerId = UUID.randomUUID();
            CombatSnapshot snapshot = createSnapshot("Witch", 40);
            tracker.recordDamage(playerId, snapshot);

            tracker.onPlayerDisconnect(playerId);

            assertNull(tracker.peekLastDamage(playerId));
        }

        @Test
        @DisplayName("Does not affect other players on disconnect")
        void doesNotAffectOtherPlayers_onDisconnect() {
            UUID player1 = UUID.randomUUID();
            UUID player2 = UUID.randomUUID();
            tracker.recordDamage(player1, createSnapshot("Zombie", 30));
            tracker.recordDamage(player2, createSnapshot("Skeleton", 40));

            tracker.onPlayerDisconnect(player1);

            assertNull(tracker.peekLastDamage(player1));
            assertNotNull(tracker.peekLastDamage(player2));
        }

        @Test
        @DisplayName("Safe to call for non-tracked player")
        void safeToCall_forNonTrackedPlayer() {
            UUID playerId = UUID.randomUUID();

            // Should not throw
            assertDoesNotThrow(() -> tracker.onPlayerDisconnect(playerId));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Clear All Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Clear All")
    class ClearAllTests {

        @Test
        @DisplayName("Removes all tracked data")
        void removesAllTrackedData() {
            tracker.recordDamage(UUID.randomUUID(), createSnapshot("A", 10));
            tracker.recordDamage(UUID.randomUUID(), createSnapshot("B", 20));
            tracker.recordDamage(UUID.randomUUID(), createSnapshot("C", 30));

            assertEquals(3, tracker.getTrackedPlayerCount());

            tracker.clear();

            assertEquals(0, tracker.getTrackedPlayerCount());
        }

        @Test
        @DisplayName("Safe to call when empty")
        void safeToCall_whenEmpty() {
            assertDoesNotThrow(() -> tracker.clear());
            assertEquals(0, tracker.getTrackedPlayerCount());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Config Access Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Config Access")
    class ConfigAccessTests {

        @Test
        @DisplayName("Returns injected config")
        void returnsInjectedConfig() {
            DeathRecapConfig result = tracker.getConfig();
            assertSame(config, result);
        }

        @Test
        @DisplayName("Config changes affect behavior")
        void configChanges_affectBehavior() {
            UUID playerId = UUID.randomUUID();

            // Record while enabled
            config.setEnabled(true);
            tracker.recordDamage(playerId, createSnapshot("A", 10));
            assertNotNull(tracker.peekLastDamage(playerId));

            // Clear and disable
            tracker.clear();
            config.setEnabled(false);

            // Should not record while disabled
            tracker.recordDamage(playerId, createSnapshot("B", 20));
            assertNull(tracker.peekLastDamage(playerId));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Tracked Player Count Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Tracked Player Count")
    class TrackedPlayerCountTests {

        @Test
        @DisplayName("Starts at zero")
        void startsAtZero() {
            assertEquals(0, tracker.getTrackedPlayerCount());
        }

        @Test
        @DisplayName("Increases when players are tracked")
        void increases_whenPlayersAreTracked() {
            tracker.recordDamage(UUID.randomUUID(), createSnapshot("A", 10));
            assertEquals(1, tracker.getTrackedPlayerCount());

            tracker.recordDamage(UUID.randomUUID(), createSnapshot("B", 20));
            assertEquals(2, tracker.getTrackedPlayerCount());
        }

        @Test
        @DisplayName("Does not increase for same player")
        void doesNotIncrease_forSamePlayer() {
            UUID playerId = UUID.randomUUID();

            tracker.recordDamage(playerId, createSnapshot("A", 10));
            tracker.recordDamage(playerId, createSnapshot("B", 20));
            tracker.recordDamage(playerId, createSnapshot("C", 30));

            assertEquals(1, tracker.getTrackedPlayerCount());
        }

        @Test
        @DisplayName("Decreases when killing blow retrieved")
        void decreases_whenKillingBlowRetrieved() {
            UUID playerId = UUID.randomUUID();
            tracker.recordDamage(playerId, createSnapshot("A", 10));

            assertEquals(1, tracker.getTrackedPlayerCount());

            tracker.getKillingBlow(playerId);

            assertEquals(0, tracker.getTrackedPlayerCount());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Combat Snapshot Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("CombatSnapshot")
    class CombatSnapshotTests {

        @Test
        @DisplayName("forEnvironment creates correct snapshot")
        void forEnvironment_createsCorrectSnapshot() {
            CombatSnapshot snapshot = CombatSnapshot.forEnvironment(
                "Fall Damage", 25.0f, DamageType.PHYSICAL, 100, 50
            );

            assertEquals("Fall Damage", snapshot.attackerName());
            assertEquals("environment", snapshot.attackerType());
            assertEquals(0, snapshot.attackerLevel());
            assertNull(snapshot.attackerClass());
            assertEquals(25.0f, snapshot.finalDamage());
            assertEquals(DamageType.PHYSICAL, snapshot.damageType());
        }

        @Test
        @DisplayName("hasElementalDamage returns false when no elemental damage")
        void hasElementalDamage_returnsFalse_whenNoElementalDamage() {
            CombatSnapshot snapshot = createSnapshot("Test", 50);

            assertFalse(snapshot.hasElementalDamage());
        }

        @Test
        @DisplayName("getCompactSummary formats correctly")
        void getCompactSummary_formatsCorrectly() {
            CombatSnapshot snapshot = createSnapshot("Zombie", 100);

            String summary = snapshot.getCompactSummary();

            assertTrue(summary.contains("100"));
            assertTrue(summary.contains("dmg"));
            assertTrue(summary.contains("base"));
        }

        @Test
        @DisplayName("getCompactSummary shows CRIT for critical hits")
        void getCompactSummary_showsCrit_forCriticalHits() {
            CombatSnapshot snapshot = new CombatSnapshot(
                System.currentTimeMillis(),
                "Assassin", "mob", 20, null,
                50, 10, 20, true, 2.0f, 120,  // wasCritical = true
                30, 20, 10, 25, 90,
                null, null, 0,
                null, null,  // defenderRawResistances, attackerPenetration
                90,
                DamageType.PHYSICAL,
                false,
                100, 100, 10, 5  // maxHealth, healthBefore, healthAfter, evasion
            );

            String summary = snapshot.getCompactSummary();

            assertTrue(summary.contains("CRIT"));
        }
    }
}
