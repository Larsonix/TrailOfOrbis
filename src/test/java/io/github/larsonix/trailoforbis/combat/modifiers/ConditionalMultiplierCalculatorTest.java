package io.github.larsonix.trailoforbis.combat.modifiers;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.ailments.AilmentTracker;
import io.github.larsonix.trailoforbis.ailments.AilmentType;
import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.combat.resolution.CombatEntityResolver;
import io.github.larsonix.trailoforbis.maps.components.RealmMobComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ConditionalMultiplierCalculator}.
 *
 * <p>Tests conditional damage multipliers:
 * <ul>
 *   <li>Realm damage multiplier (from RealmMobComponent)</li>
 *   <li>Execute bonus (vs low HP targets)</li>
 *   <li>Damage vs Frozen/Shocked ailments</li>
 *   <li>Damage at Low Life (attacker HP)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ConditionalMultiplierCalculator")
class ConditionalMultiplierCalculatorTest {

    @Mock
    private CombatEntityResolver entityResolver;

    @Mock
    private AilmentTracker ailmentTracker;

    @Mock
    private Store<EntityStore> store;

    @Mock
    private Damage damage;

    @Mock
    private Damage.EntitySource entitySource;

    @Mock
    private Ref<EntityStore> attackerRef;

    @Mock
    private RealmMobComponent realmMobComponent;

    @Mock
    private EntityStatMap entityStatMap;

    @Mock
    private EntityStatValue healthStatValue;

    @Mock
    private ComponentType<EntityStore, RealmMobComponent> realmMobComponentType;

    private ConditionalMultiplierCalculator calculator;
    private ComputedStats attackerStats;
    private UUID defenderUuid;

    @BeforeEach
    void setUp() {
        // Set up static TYPE field so getComponentType() returns our mock
        RealmMobComponent.TYPE = realmMobComponentType;

        calculator = new ConditionalMultiplierCalculator(entityResolver, ailmentTracker);
        attackerStats = new ComputedStats();
        defenderUuid = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        // Clean up static field
        RealmMobComponent.TYPE = null;
    }

    // ═══════════════════════════════════════════════════════════════════
    // REALM DAMAGE MULTIPLIER TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getRealmDamageMultiplier")
    class RealmDamageMultiplierTests {

        @Test
        @DisplayName("Should return multiplier when attacker is a realm mob")
        void getRealmDamageMultiplier_realmMob_returnsMultiplier() {
            // Arrange
            when(damage.getSource()).thenReturn(entitySource);
            when(entitySource.getRef()).thenReturn(attackerRef);
            when(attackerRef.isValid()).thenReturn(true);
            when(entityResolver.resolveTrueAttacker(store, attackerRef)).thenReturn(attackerRef);
            // Use eq() with the mocked component type
            when(store.getComponent(eq(attackerRef), eq(realmMobComponentType))).thenReturn(realmMobComponent);
            when(realmMobComponent.getDamageMultiplier()).thenReturn(1.5f);

            // Act
            float result = calculator.getRealmDamageMultiplier(store, damage);

            // Assert
            assertEquals(1.5f, result, 0.001f);
        }

        @Test
        @DisplayName("Should return 1.0 when attacker is not a realm mob")
        void getRealmDamageMultiplier_nonRealmMob_returns1() {
            // Arrange
            when(damage.getSource()).thenReturn(entitySource);
            when(entitySource.getRef()).thenReturn(attackerRef);
            when(attackerRef.isValid()).thenReturn(true);
            when(entityResolver.resolveTrueAttacker(store, attackerRef)).thenReturn(attackerRef);
            when(store.getComponent(eq(attackerRef), eq(realmMobComponentType))).thenReturn(null);

            // Act
            float result = calculator.getRealmDamageMultiplier(store, damage);

            // Assert
            assertEquals(1.0f, result, 0.001f);
        }

        @Test
        @DisplayName("Should return 1.0 when damage source is not an entity")
        void getRealmDamageMultiplier_nonEntitySource_returns1() {
            // Arrange - damage source is not EntitySource
            when(damage.getSource()).thenReturn(mock(Damage.Source.class));

            // Act
            float result = calculator.getRealmDamageMultiplier(store, damage);

            // Assert
            assertEquals(1.0f, result, 0.001f);
        }

        @Test
        @DisplayName("Should resolve projectile to owner for realm mob check")
        void getRealmDamageMultiplier_projectileSource_resolvesToOwner() {
            // Arrange
            @SuppressWarnings("unchecked")
            Ref<EntityStore> projectileRef = mock(Ref.class);
            @SuppressWarnings("unchecked")
            Ref<EntityStore> ownerRef = mock(Ref.class);

            when(damage.getSource()).thenReturn(entitySource);
            when(entitySource.getRef()).thenReturn(projectileRef);
            when(projectileRef.isValid()).thenReturn(true);
            // Projectile resolves to its owner
            when(entityResolver.resolveTrueAttacker(store, projectileRef)).thenReturn(ownerRef);
            when(store.getComponent(eq(ownerRef), eq(realmMobComponentType))).thenReturn(realmMobComponent);
            when(realmMobComponent.getDamageMultiplier()).thenReturn(2.0f);

            // Act
            float result = calculator.getRealmDamageMultiplier(store, damage);

            // Assert
            assertEquals(2.0f, result, 0.001f);
            verify(entityResolver).resolveTrueAttacker(store, projectileRef);
        }

        @Test
        @DisplayName("Should return 1.0 when attacker ref is null")
        void getRealmDamageMultiplier_nullAttackerRef_returns1() {
            // Arrange
            when(damage.getSource()).thenReturn(entitySource);
            when(entitySource.getRef()).thenReturn(null);

            // Act
            float result = calculator.getRealmDamageMultiplier(store, damage);

            // Assert
            assertEquals(1.0f, result, 0.001f);
        }

        @Test
        @DisplayName("Should return 1.0 when attacker ref is invalid")
        void getRealmDamageMultiplier_invalidAttackerRef_returns1() {
            // Arrange
            when(damage.getSource()).thenReturn(entitySource);
            when(entitySource.getRef()).thenReturn(attackerRef);
            when(attackerRef.isValid()).thenReturn(false);

            // Act
            float result = calculator.getRealmDamageMultiplier(store, damage);

            // Assert
            assertEquals(1.0f, result, 0.001f);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // EXECUTE MULTIPLIER TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getExecuteMultiplier")
    class ExecuteMultiplierTests {

        @Test
        @DisplayName("Should apply bonus when target below 35% HP and attacker has execute stat")
        void getExecuteMultiplier_targetBelow35Percent_appliesBonus() {
            // Arrange - target at 20% HP, attacker has 50% execute bonus
            attackerStats.setExecuteDamagePercent(50.0);
            float defenderHealthPercent = 0.20f;

            // Act
            float result = calculator.getExecuteMultiplier(attackerStats, defenderHealthPercent);

            // Assert - 1.0 + (50/100) = 1.5
            assertEquals(1.5f, result, 0.001f);
        }

        @Test
        @DisplayName("Should apply bonus when target exactly at 34% HP")
        void getExecuteMultiplier_targetAt34Percent_appliesBonus() {
            // Arrange
            attackerStats.setExecuteDamagePercent(30.0);
            float defenderHealthPercent = 0.34f;

            // Act
            float result = calculator.getExecuteMultiplier(attackerStats, defenderHealthPercent);

            // Assert - 1.0 + (30/100) = 1.3
            assertEquals(1.3f, result, 0.001f);
        }

        @Test
        @DisplayName("Should not apply bonus when target above 35% HP")
        void getExecuteMultiplier_targetAbove35Percent_noBonus() {
            // Arrange
            attackerStats.setExecuteDamagePercent(50.0);
            float defenderHealthPercent = 0.36f;

            // Act
            float result = calculator.getExecuteMultiplier(attackerStats, defenderHealthPercent);

            // Assert
            assertEquals(1.0f, result, 0.001f);
        }

        @Test
        @DisplayName("Should not apply bonus when target at exactly 35% HP")
        void getExecuteMultiplier_targetAt35Percent_noBonus() {
            // Arrange - boundary condition: 35% is NOT below threshold
            attackerStats.setExecuteDamagePercent(50.0);
            float defenderHealthPercent = 0.35f;

            // Act
            float result = calculator.getExecuteMultiplier(attackerStats, defenderHealthPercent);

            // Assert
            assertEquals(1.0f, result, 0.001f);
        }

        @Test
        @DisplayName("Should not apply bonus when execute stat is zero")
        void getExecuteMultiplier_zeroExecuteStat_noBonus() {
            // Arrange
            attackerStats.setExecuteDamagePercent(0.0);
            float defenderHealthPercent = 0.10f;

            // Act
            float result = calculator.getExecuteMultiplier(attackerStats, defenderHealthPercent);

            // Assert
            assertEquals(1.0f, result, 0.001f);
        }

        @Test
        @DisplayName("Should apply bonus when target at 0% HP")
        void getExecuteMultiplier_targetAtZeroPercent_appliesBonus() {
            // Arrange
            attackerStats.setExecuteDamagePercent(25.0);
            float defenderHealthPercent = 0.0f;

            // Act
            float result = calculator.getExecuteMultiplier(attackerStats, defenderHealthPercent);

            // Assert
            assertEquals(1.25f, result, 0.001f);
        }

        @Test
        @DisplayName("Should handle negative health percent gracefully")
        void getExecuteMultiplier_negativeHealthPercent_noBonus() {
            // Arrange - negative health shouldn't happen but check boundary
            attackerStats.setExecuteDamagePercent(50.0);
            float defenderHealthPercent = -0.1f;

            // Act
            float result = calculator.getExecuteMultiplier(attackerStats, defenderHealthPercent);

            // Assert - Implementation checks >= 0 so negative returns no bonus
            assertEquals(1.0f, result, 0.001f);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // AILMENT BONUS MULTIPLIER TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getAilmentBonusMultiplier")
    class AilmentBonusMultiplierTests {

        @Test
        @DisplayName("Should apply bonus vs frozen target")
        void getAilmentBonusMultiplier_frozenTarget_appliesBonus() {
            // Arrange
            attackerStats.setDamageVsFrozenPercent(40.0);
            when(ailmentTracker.hasAilment(defenderUuid, AilmentType.FREEZE)).thenReturn(true);
            when(ailmentTracker.hasAilment(defenderUuid, AilmentType.SHOCK)).thenReturn(false);

            // Act
            float result = calculator.getAilmentBonusMultiplier(attackerStats, defenderUuid);

            // Assert - 1.0 + (40/100) = 1.4
            assertEquals(1.4f, result, 0.001f);
        }

        @Test
        @DisplayName("Should apply bonus vs shocked target")
        void getAilmentBonusMultiplier_shockedTarget_appliesBonus() {
            // Arrange
            attackerStats.setDamageVsShockedPercent(30.0);
            when(ailmentTracker.hasAilment(defenderUuid, AilmentType.FREEZE)).thenReturn(false);
            when(ailmentTracker.hasAilment(defenderUuid, AilmentType.SHOCK)).thenReturn(true);

            // Act
            float result = calculator.getAilmentBonusMultiplier(attackerStats, defenderUuid);

            // Assert - 1.0 + (30/100) = 1.3
            assertEquals(1.3f, result, 0.001f);
        }

        @Test
        @DisplayName("Should stack multipliers when target has both ailments")
        void getAilmentBonusMultiplier_bothAilments_stacksMultiplicatively() {
            // Arrange
            attackerStats.setDamageVsFrozenPercent(50.0);
            attackerStats.setDamageVsShockedPercent(25.0);
            when(ailmentTracker.hasAilment(defenderUuid, AilmentType.FREEZE)).thenReturn(true);
            when(ailmentTracker.hasAilment(defenderUuid, AilmentType.SHOCK)).thenReturn(true);

            // Act
            float result = calculator.getAilmentBonusMultiplier(attackerStats, defenderUuid);

            // Assert - (1.0 + 0.5) * (1.0 + 0.25) = 1.5 * 1.25 = 1.875
            assertEquals(1.875f, result, 0.001f);
        }

        @Test
        @DisplayName("Should return 1.0 when no ailment tracker")
        void getAilmentBonusMultiplier_nullTracker_returns1() {
            // Arrange - create calculator without ailment tracker
            ConditionalMultiplierCalculator calcNoTracker = new ConditionalMultiplierCalculator(entityResolver, null);
            attackerStats.setDamageVsFrozenPercent(50.0);

            // Act
            float result = calcNoTracker.getAilmentBonusMultiplier(attackerStats, defenderUuid);

            // Assert
            assertEquals(1.0f, result, 0.001f);
        }

        @Test
        @DisplayName("Should return 1.0 when defender UUID is null")
        void getAilmentBonusMultiplier_nullDefenderUuid_returns1() {
            // Arrange
            attackerStats.setDamageVsFrozenPercent(50.0);

            // Act
            float result = calculator.getAilmentBonusMultiplier(attackerStats, null);

            // Assert
            assertEquals(1.0f, result, 0.001f);
        }

        @Test
        @DisplayName("Should return 1.0 when target has ailment but no bonus stat")
        void getAilmentBonusMultiplier_ailmentButNoStat_returns1() {
            // Arrange
            attackerStats.setDamageVsFrozenPercent(0.0);
            attackerStats.setDamageVsShockedPercent(0.0);
            when(ailmentTracker.hasAilment(defenderUuid, AilmentType.FREEZE)).thenReturn(true);
            when(ailmentTracker.hasAilment(defenderUuid, AilmentType.SHOCK)).thenReturn(true);

            // Act
            float result = calculator.getAilmentBonusMultiplier(attackerStats, defenderUuid);

            // Assert
            assertEquals(1.0f, result, 0.001f);
        }

        @Test
        @DisplayName("Should return 1.0 when target has no ailments")
        void getAilmentBonusMultiplier_noAilments_returns1() {
            // Arrange
            attackerStats.setDamageVsFrozenPercent(50.0);
            attackerStats.setDamageVsShockedPercent(30.0);
            when(ailmentTracker.hasAilment(defenderUuid, AilmentType.FREEZE)).thenReturn(false);
            when(ailmentTracker.hasAilment(defenderUuid, AilmentType.SHOCK)).thenReturn(false);

            // Act
            float result = calculator.getAilmentBonusMultiplier(attackerStats, defenderUuid);

            // Assert
            assertEquals(1.0f, result, 0.001f);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // LOW LIFE MULTIPLIER TESTS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Tests for getLowLifeMultiplier.
     *
     * <p>Note: Tests that check EntityStatMap access are disabled because
     * EntityStatMap.getComponentType() requires Hytale's EntityStatsModule
     * singleton to be initialized, which isn't available in unit tests.
     * These would need integration testing with a running server.
     */
    @Nested
    @DisplayName("getLowLifeMultiplier")
    class LowLifeMultiplierTests {

        @Test
        @DisplayName("Should return 1.0 when damageAtLowLife stat is zero")
        void getLowLifeMultiplier_zeroDamageAtLowLifeStat_returns1() {
            // Arrange - no need to set up damage mocks as we short-circuit early
            attackerStats.setDamageAtLowLife(0.0);

            // Act
            float result = calculator.getLowLifeMultiplier(attackerStats, store, damage);

            // Assert
            assertEquals(1.0f, result, 0.001f);
        }

        @Test
        @DisplayName("Should return 1.0 when damage source is not entity")
        void getLowLifeMultiplier_nonEntitySource_returns1() {
            // Arrange
            attackerStats.setDamageAtLowLife(60.0);
            when(damage.getSource()).thenReturn(mock(Damage.Source.class));

            // Act
            float result = calculator.getLowLifeMultiplier(attackerStats, store, damage);

            // Assert
            assertEquals(1.0f, result, 0.001f);
        }

        // ─────────────────────────────────────────────────────────────────
        // DISABLED: These tests require EntityStatMap.getComponentType() which
        // calls EntityStatsModule.get() - a Hytale singleton not available in tests.
        // ─────────────────────────────────────────────────────────────────

        @Test
        @Disabled("Requires Hytale EntityStatsModule singleton (integration test only)")
        @DisplayName("Should apply bonus when attacker below 35% HP")
        void getLowLifeMultiplier_attackerBelow35Percent_appliesBonus() {
            // Would test: attacker at 30% HP with 60% damageAtLowLife -> 1.6x multiplier
        }

        @Test
        @Disabled("Requires Hytale EntityStatsModule singleton (integration test only)")
        @DisplayName("Should apply bonus when attacker exactly at 35% HP")
        void getLowLifeMultiplier_attackerAt35Percent_appliesBonus() {
            // Would test: boundary - 35% HP with 40% damageAtLowLife -> 1.4x multiplier
        }

        @Test
        @Disabled("Requires Hytale EntityStatsModule singleton (integration test only)")
        @DisplayName("Should not apply bonus when attacker above 35% HP")
        void getLowLifeMultiplier_attackerAbove35Percent_noBonus() {
            // Would test: attacker at 50% HP -> 1.0x (no bonus)
        }

        @Test
        @Disabled("Requires Hytale EntityStatsModule singleton (integration test only)")
        @DisplayName("Should return 1.0 when attacker has no EntityStatMap")
        void getLowLifeMultiplier_noStatMap_returns1() {
            // Would test: entity without stat map -> 1.0x
        }

        @Test
        @Disabled("Requires Hytale EntityStatsModule singleton (integration test only)")
        @DisplayName("Should return 1.0 when attacker max health is zero")
        void getLowLifeMultiplier_zeroMaxHealth_returns1() {
            // Would test: division by zero protection -> 1.0x
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // COMBINED CALCULATE TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("calculate (combined)")
    class CombinedCalculateTests {

        @Test
        @Disabled("Requires Hytale EntityStatsModule singleton for low-life multiplier (integration test only)")
        @DisplayName("Should combine all multipliers multiplicatively")
        void calculate_allConditions_combinesMultiplicatively() {
            // Would test: realm(1.5) * execute(1.2) * frozen(1.3) * lowLife(1.4) = 3.276x
            // Disabled because getLowLifeMultiplier calls EntityStatMap.getComponentType()
        }

        @Test
        @DisplayName("Should return only realm multiplier when attackerStats is null")
        void calculate_nullAttackerStats_onlyRealmMultiplier() {
            // Arrange
            when(damage.getSource()).thenReturn(entitySource);
            when(entitySource.getRef()).thenReturn(attackerRef);
            when(attackerRef.isValid()).thenReturn(true);
            when(entityResolver.resolveTrueAttacker(store, attackerRef)).thenReturn(attackerRef);
            when(store.getComponent(eq(attackerRef), eq(realmMobComponentType))).thenReturn(realmMobComponent);
            when(realmMobComponent.getDamageMultiplier()).thenReturn(2.0f);

            // Act
            float result = calculator.calculate(store, damage, null, 0.5f, defenderUuid, null);

            // Assert - Only realm multiplier applied
            assertEquals(2.0f, result, 0.001f);
        }

        @Test
        @DisplayName("Should return 1.0 when no conditions apply")
        void calculate_noConditions_returns1() {
            // Arrange - No realm mob, no execute, no ailments, not low life
            when(damage.getSource()).thenReturn(entitySource);
            when(entitySource.getRef()).thenReturn(attackerRef);
            when(attackerRef.isValid()).thenReturn(true);
            when(entityResolver.resolveTrueAttacker(store, attackerRef)).thenReturn(attackerRef);
            when(store.getComponent(eq(attackerRef), eq(realmMobComponentType))).thenReturn(null);

            attackerStats.setExecuteDamagePercent(0.0);
            attackerStats.setDamageVsFrozenPercent(0.0);
            attackerStats.setDamageVsShockedPercent(0.0);
            attackerStats.setDamageAtLowLife(0.0);

            when(ailmentTracker.hasAilment(defenderUuid, AilmentType.FREEZE)).thenReturn(false);
            when(ailmentTracker.hasAilment(defenderUuid, AilmentType.SHOCK)).thenReturn(false);

            // Act
            float result = calculator.calculate(store, damage, attackerStats, 0.8f, defenderUuid, null);

            // Assert
            assertEquals(1.0f, result, 0.001f);
        }

        @Test
        @DisplayName("Should handle partial conditions")
        void calculate_partialConditions_combinesApplicable() {
            // Arrange - Only execute bonus applies
            when(damage.getSource()).thenReturn(entitySource);
            when(entitySource.getRef()).thenReturn(attackerRef);
            when(attackerRef.isValid()).thenReturn(true);
            when(entityResolver.resolveTrueAttacker(store, attackerRef)).thenReturn(attackerRef);
            when(store.getComponent(eq(attackerRef), eq(realmMobComponentType))).thenReturn(null);

            // Execute: 1.25x
            attackerStats.setExecuteDamagePercent(25.0);
            float defenderHealthPercent = 0.20f;

            // No ailment bonuses
            attackerStats.setDamageVsFrozenPercent(0.0);
            attackerStats.setDamageAtLowLife(0.0);
            when(ailmentTracker.hasAilment(defenderUuid, AilmentType.FREEZE)).thenReturn(false);
            when(ailmentTracker.hasAilment(defenderUuid, AilmentType.SHOCK)).thenReturn(false);

            // Act
            float result = calculator.calculate(store, damage, attackerStats, defenderHealthPercent, defenderUuid, null);

            // Assert - Only execute bonus: 1.25x
            assertEquals(1.25f, result, 0.001f);
        }

        @Test
        @DisplayName("Should combine realm, execute, and ailment multipliers (no low-life)")
        void calculate_realmExecuteAilment_combinesWithoutLowLife() {
            // Arrange - All conditions except low-life (which requires EntityStatsModule)
            // 1. Realm damage: 1.5x
            when(damage.getSource()).thenReturn(entitySource);
            when(entitySource.getRef()).thenReturn(attackerRef);
            when(attackerRef.isValid()).thenReturn(true);
            when(entityResolver.resolveTrueAttacker(store, attackerRef)).thenReturn(attackerRef);
            when(store.getComponent(eq(attackerRef), eq(realmMobComponentType))).thenReturn(realmMobComponent);
            when(realmMobComponent.getDamageMultiplier()).thenReturn(1.5f);

            // 2. Execute bonus: 1.2x (target at 20% HP, 20% execute stat)
            attackerStats.setExecuteDamagePercent(20.0);
            float defenderHealthPercent = 0.20f;

            // 3. Ailment bonus: 1.3x (frozen)
            attackerStats.setDamageVsFrozenPercent(30.0);
            when(ailmentTracker.hasAilment(defenderUuid, AilmentType.FREEZE)).thenReturn(true);
            when(ailmentTracker.hasAilment(defenderUuid, AilmentType.SHOCK)).thenReturn(false);

            // 4. No low-life bonus (set to 0 to avoid EntityStatMap lookup)
            attackerStats.setDamageAtLowLife(0.0);

            // Act
            float result = calculator.calculate(store, damage, attackerStats, defenderHealthPercent, defenderUuid, null);

            // Assert - 1.5 * 1.2 * 1.3 = 2.34
            assertEquals(2.34f, result, 0.01f);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // CONSTRUCTOR TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("Should accept null ailment tracker")
        void constructor_nullAilmentTracker_succeeds() {
            // Act & Assert - no exception
            ConditionalMultiplierCalculator calc = new ConditionalMultiplierCalculator(entityResolver, null);
            assertNotNull(calc);
        }

        @Test
        @DisplayName("Should work with null ailment tracker for ailment checks")
        void constructor_nullAilmentTracker_ailmentChecksReturn1() {
            // Arrange
            ConditionalMultiplierCalculator calc = new ConditionalMultiplierCalculator(entityResolver, null);
            ComputedStats stats = new ComputedStats();
            stats.setDamageVsFrozenPercent(50.0);

            // Act
            float result = calc.getAilmentBonusMultiplier(stats, UUID.randomUUID());

            // Assert
            assertEquals(1.0f, result, 0.001f);
        }
    }
}
