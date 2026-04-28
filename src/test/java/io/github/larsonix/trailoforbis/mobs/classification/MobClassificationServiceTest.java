package io.github.larsonix.trailoforbis.mobs.classification;

import com.hypixel.hytale.server.core.asset.type.attitude.Attitude;
import io.github.larsonix.trailoforbis.mobs.classification.provider.TagLookupProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link MobClassificationService}.
 *
 * <p>Tests the 5-class system (PASSIVE, MINOR, HOSTILE, ELITE, BOSS) with:
 * <ol>
 *   <li>Role name overrides (bosses/elites lists)</li>
 *   <li>NPCGroup meta-group checks (Aggressive, Neutral, Passive)</li>
 *   <li>Attitude fallback</li>
 * </ol>
 *
 * <p>Note: MINOR classification is handled through DynamicEntityRegistry overrides,
 * not through this service's static fallback path.
 */
@DisplayName("MobClassificationService")
@ExtendWith(MockitoExtension.class)
class MobClassificationServiceTest {

    private static final String LIVING_WORLD_AGGRESSIVE = "Aggressive";
    private static final String LIVING_WORLD_NEUTRAL = "Neutral";
    private static final String LIVING_WORLD_PASSIVE = "Passive";

    @Mock
    private TagLookupProvider tagLookupProvider;

    private MobClassificationConfig config;
    private MobClassificationService service;

    @BeforeEach
    void setUp() {
        config = new MobClassificationConfig();
        service = new MobClassificationService(config, tagLookupProvider);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Role Override Tests (Priority 1 - Highest)
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Role Override (Priority 1)")
    class RoleOverrideTests {

        @Test
        @DisplayName("Boss role returns BOSS class")
        void bossRole_returnsBoss() {
            config.setBosses(Arrays.asList("dragon_fire"));

            MobClassificationContext context = new MobClassificationContext(
                "dragon_fire", 0, Attitude.HOSTILE);

            RPGMobClass result = service.classify(context);

            assertEquals(RPGMobClass.BOSS, result);
        }

        @Test
        @DisplayName("Elite role returns ELITE class (when explicitly configured)")
        void eliteRole_returnsElite_whenConfigured() {
            // NOTE: With the random elite system, elites list is typically empty.
            // This test verifies the mechanism still works if someone adds explicit elites.
            config.setElites(Arrays.asList("test_elite_mob"));

            MobClassificationContext context = new MobClassificationContext(
                "test_elite_mob", 0, Attitude.HOSTILE);

            RPGMobClass result = service.classify(context);

            assertEquals(RPGMobClass.ELITE, result);
        }

        @Test
        @DisplayName("Boss lookup is case-insensitive")
        void bossLookup_isCaseInsensitive() {
            config.setBosses(Arrays.asList("dragon_fire"));

            MobClassificationContext context = new MobClassificationContext(
                "DRAGON_FIRE", 0, Attitude.HOSTILE);

            RPGMobClass result = service.classify(context);

            assertEquals(RPGMobClass.BOSS, result);
        }

        @Test
        @DisplayName("Elite lookup is case-insensitive (when explicitly configured)")
        void eliteLookup_isCaseInsensitive_whenConfigured() {
            // NOTE: With the random elite system, elites list is typically empty.
            // This test verifies case-insensitivity still works if someone adds explicit elites.
            config.setElites(Arrays.asList("test_elite_mob"));

            MobClassificationContext context = new MobClassificationContext(
                "TEST_ELITE_MOB", 0, Attitude.HOSTILE);

            RPGMobClass result = service.classify(context);

            assertEquals(RPGMobClass.ELITE, result);
        }

        @Test
        @DisplayName("Boss takes priority over elite")
        void boss_takesPriorityOverElite() {
            config.setBosses(Arrays.asList("yeti"));
            config.setElites(Arrays.asList("yeti")); // Same role in both lists

            MobClassificationContext context = new MobClassificationContext(
                "yeti", 0, Attitude.HOSTILE);

            RPGMobClass result = service.classify(context);

            assertEquals(RPGMobClass.BOSS, result);
        }

        @Test
        @DisplayName("Role override takes priority over LivingWorld groups")
        void roleOverride_takesPriorityOverLivingWorld() {
            // Test with BOSS override (since elite list is now empty by default)
            config.setBosses(Arrays.asList("special_boss"));
            lenient().when(tagLookupProvider.hasTag(eq(LIVING_WORLD_AGGRESSIVE), anyInt())).thenReturn(true);

            MobClassificationContext context = new MobClassificationContext(
                "special_boss", 1, Attitude.HOSTILE);

            RPGMobClass result = service.classify(context);

            assertEquals(RPGMobClass.BOSS, result);
        }

        @Test
        @DisplayName("Unknown role name falls through to LivingWorld groups")
        void unknownRoleName_fallsThroughToLivingWorld() {
            when(tagLookupProvider.hasTag(eq(LIVING_WORLD_AGGRESSIVE), eq(5))).thenReturn(true);

            MobClassificationContext context = new MobClassificationContext(
                "unknown_mob", 5, Attitude.HOSTILE);

            RPGMobClass result = service.classify(context);

            assertEquals(RPGMobClass.HOSTILE, result);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // LivingWorld Group Tests (Priority 2)
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("LivingWorld Groups (Priority 2)")
    class LivingWorldGroupTests {

        @Test
        @DisplayName("Aggressive group returns HOSTILE")
        void aggressiveGroup_returnsHostile() {
            when(tagLookupProvider.hasTag(eq(LIVING_WORLD_AGGRESSIVE), eq(10))).thenReturn(true);

            MobClassificationContext context = new MobClassificationContext(
                null, 10, Attitude.HOSTILE);

            RPGMobClass result = service.classify(context);

            assertEquals(RPGMobClass.HOSTILE, result);
        }

        @Test
        @DisplayName("Neutral group returns PASSIVE (livestock, non-aggressive wildlife)")
        void neutralGroup_returnsPassive() {
            when(tagLookupProvider.hasTag(eq(LIVING_WORLD_AGGRESSIVE), eq(10))).thenReturn(false);
            when(tagLookupProvider.hasTag(eq(LIVING_WORLD_NEUTRAL), eq(10))).thenReturn(true);

            MobClassificationContext context = new MobClassificationContext(
                null, 10, Attitude.NEUTRAL);

            RPGMobClass result = service.classify(context);

            assertEquals(RPGMobClass.PASSIVE, result);
        }

        @Test
        @DisplayName("Passive group returns PASSIVE")
        void passiveGroup_returnsPassive() {
            when(tagLookupProvider.hasTag(eq(LIVING_WORLD_AGGRESSIVE), eq(10))).thenReturn(false);
            when(tagLookupProvider.hasTag(eq(LIVING_WORLD_NEUTRAL), eq(10))).thenReturn(false);
            when(tagLookupProvider.hasTag(eq(LIVING_WORLD_PASSIVE), eq(10))).thenReturn(true);

            MobClassificationContext context = new MobClassificationContext(
                null, 10, Attitude.IGNORE);

            RPGMobClass result = service.classify(context);

            assertEquals(RPGMobClass.PASSIVE, result);
        }

        @Test
        @DisplayName("Aggressive takes priority over Neutral")
        void aggressive_takesPriorityOverNeutral() {
            when(tagLookupProvider.hasTag(eq(LIVING_WORLD_AGGRESSIVE), eq(7))).thenReturn(true);
            // Both match, but Aggressive is checked first → HOSTILE (not PASSIVE from Neutral)
            lenient().when(tagLookupProvider.hasTag(eq(LIVING_WORLD_NEUTRAL), eq(7))).thenReturn(true);

            MobClassificationContext context = new MobClassificationContext(
                null, 7, Attitude.NEUTRAL);

            RPGMobClass result = service.classify(context);

            assertEquals(RPGMobClass.HOSTILE, result);
            // Verify Neutral was not checked since Aggressive matched
            verify(tagLookupProvider, never()).hasTag(eq(LIVING_WORLD_NEUTRAL), eq(7));
        }

        @Test
        @DisplayName("No group match falls through to attitude")
        void noLivingWorldMatch_fallsThroughToAttitude() {
            when(tagLookupProvider.hasTag(eq(LIVING_WORLD_AGGRESSIVE), eq(8))).thenReturn(false);
            when(tagLookupProvider.hasTag(eq(LIVING_WORLD_NEUTRAL), eq(8))).thenReturn(false);
            when(tagLookupProvider.hasTag(eq(LIVING_WORLD_PASSIVE), eq(8))).thenReturn(false);

            MobClassificationContext context = new MobClassificationContext(
                null, 8, Attitude.HOSTILE);

            RPGMobClass result = service.classify(context);

            assertEquals(RPGMobClass.HOSTILE, result); // Attitude fallback
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Attitude Fallback Tests (Priority 3 - Lowest)
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Attitude Fallback (Priority 3)")
    class AttitudeFallbackTests {

        @BeforeEach
        void setUpNoLivingWorldMatches() {
            // None of the LivingWorld groups match
            when(tagLookupProvider.hasTag(eq(LIVING_WORLD_AGGRESSIVE), anyInt())).thenReturn(false);
            when(tagLookupProvider.hasTag(eq(LIVING_WORLD_NEUTRAL), anyInt())).thenReturn(false);
            when(tagLookupProvider.hasTag(eq(LIVING_WORLD_PASSIVE), anyInt())).thenReturn(false);
        }

        @Test
        @DisplayName("HOSTILE attitude returns HOSTILE")
        void hostileAttitude_returnsHostile() {
            MobClassificationContext context = new MobClassificationContext(
                null, 0, Attitude.HOSTILE);

            RPGMobClass result = service.classify(context);

            assertEquals(RPGMobClass.HOSTILE, result);
        }

        @Test
        @DisplayName("NEUTRAL attitude returns HOSTILE (defensive)")
        void neutralAttitude_returnsHostile() {
            MobClassificationContext context = new MobClassificationContext(
                null, 0, Attitude.NEUTRAL);

            RPGMobClass result = service.classify(context);

            assertEquals(RPGMobClass.HOSTILE, result);
        }

        @Test
        @DisplayName("FRIENDLY attitude returns PASSIVE")
        void friendlyAttitude_returnsPassive() {
            MobClassificationContext context = new MobClassificationContext(
                null, 0, Attitude.FRIENDLY);

            RPGMobClass result = service.classify(context);

            assertEquals(RPGMobClass.PASSIVE, result);
        }

        @Test
        @DisplayName("REVERED attitude returns PASSIVE")
        void reveredAttitude_returnsPassive() {
            MobClassificationContext context = new MobClassificationContext(
                null, 0, Attitude.REVERED);

            RPGMobClass result = service.classify(context);

            assertEquals(RPGMobClass.PASSIVE, result);
        }

        @Test
        @DisplayName("IGNORE attitude returns PASSIVE")
        void ignoreAttitude_returnsPassive() {
            MobClassificationContext context = new MobClassificationContext(
                null, 0, Attitude.IGNORE);

            RPGMobClass result = service.classify(context);

            assertEquals(RPGMobClass.PASSIVE, result);
        }

        @Test
        @DisplayName("Null attitude returns PASSIVE")
        void nullAttitude_returnsPassive() {
            MobClassificationContext context = new MobClassificationContext(
                null, 0, null);

            RPGMobClass result = service.classify(context);

            assertEquals(RPGMobClass.PASSIVE, result);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Dynamic Registry Priority Tests (livestock bug regression)
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Dynamic Registry Priority (livestock bug regression)")
    class DynamicRegistryPriorityTests {

        @Test
        @DisplayName("Registry lookup short-circuits static fallback path")
        void registryPassive_shortCircuitsStaticFallback() {
            // When the dynamic registry has a classification, the service uses it
            // directly without checking LivingWorld groups or attitude fallback.
            DynamicEntityRegistry mockRegistry = mock(DynamicEntityRegistry.class);
            when(mockRegistry.getDiscoveredRole("Cow")).thenReturn(
                new DiscoveredRole("Cow", 42, RPGMobClass.PASSIVE, "Hytale:Hytale",
                    Set.of("PreyBig"), DiscoveredRole.DetectionMethod.GROUP_MEMBERSHIP));

            service.setRegistry(mockRegistry);

            // Even though Cow is in the Neutral group (which would be HOSTILE via static fallback),
            // the registry classification of PASSIVE takes priority
            MobClassificationContext context = new MobClassificationContext(
                "Cow", 42, Attitude.NEUTRAL);

            RPGMobClass result = service.classify(context);

            assertEquals(RPGMobClass.PASSIVE, result);
            // Verify the tag lookup was never called (registry short-circuited)
            verify(tagLookupProvider, never()).hasTag(eq(LIVING_WORLD_NEUTRAL), anyInt());
        }

        @Test
        @DisplayName("Without registry, Neutral mobs correctly fall to PASSIVE (static fallback)")
        void withoutRegistry_neutralMobsFallToPassive() {
            // LivingWorld/Neutral contains Prey/PreyBig (livestock, deer, boars)
            // — all non-aggressive animals. Static fallback correctly maps Neutral → PASSIVE.
            when(tagLookupProvider.hasTag(eq(LIVING_WORLD_AGGRESSIVE), eq(42))).thenReturn(false);
            when(tagLookupProvider.hasTag(eq(LIVING_WORLD_NEUTRAL), eq(42))).thenReturn(true);

            MobClassificationContext context = new MobClassificationContext(
                "Cow", 42, Attitude.NEUTRAL);

            RPGMobClass result = service.classify(context);

            assertEquals(RPGMobClass.PASSIVE, result);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PASSIVE Multiplier Tests (ensures 0.1x scaling, not 0)
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("PASSIVE Multiplier Configuration")
    class PassiveMultiplierTests {

        @Test
        @DisplayName("PASSIVE XP multiplier is 0.1 (10% of hostile)")
        void passiveXpMultiplier_isTenPercent() {
            double xpMult = config.getXpMultiplier(RPGMobClass.PASSIVE);
            assertEquals(0.1, xpMult, 0.001,
                "PASSIVE XP multiplier should be 0.1 (10%) — not 0. " +
                "PASSIVE mobs must give some XP via the standard formula.");
        }

        @Test
        @DisplayName("PASSIVE stat multiplier is 0.1 (10% of hostile)")
        void passiveStatMultiplier_isTenPercent() {
            double statMult = config.getStatMultiplier(RPGMobClass.PASSIVE);
            assertEquals(0.1, statMult, 0.001,
                "PASSIVE stat multiplier should be 0.1 (10%) — not 0. " +
                "PASSIVE mobs receive minimal but non-zero scaling.");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ClassifyByName Helper Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("ClassifyByName Helper")
    class ClassifyByNameTests {

        @Test
        @DisplayName("Returns BOSS for known boss role")
        void returnsBoss_forKnownBossRole() {
            config.setBosses(Arrays.asList("dragon_fire"));

            RPGMobClass result = service.classifyByName("dragon_fire");

            assertEquals(RPGMobClass.BOSS, result);
        }

        @Test
        @DisplayName("Returns ELITE for known elite role (when explicitly configured)")
        void returnsElite_forKnownEliteRole_whenConfigured() {
            // NOTE: With the random elite system, elites list is typically empty.
            // This test verifies classifyByName still works if someone adds explicit elites.
            config.setElites(Arrays.asList("test_elite_mob"));

            RPGMobClass result = service.classifyByName("test_elite_mob");

            assertEquals(RPGMobClass.ELITE, result);
        }

        @Test
        @DisplayName("Returns null for unknown role")
        void returnsNull_forUnknownRole() {
            RPGMobClass result = service.classifyByName("unknown_mob");

            assertNull(result);
        }

        @Test
        @DisplayName("Returns null for null input")
        void returnsNull_forNullInput() {
            RPGMobClass result = service.classifyByName(null);

            assertNull(result);
        }

        @Test
        @DisplayName("Boss lookup is case-insensitive")
        void bossLookup_isCaseInsensitive() {
            config.setBosses(Arrays.asList("yeti"));

            RPGMobClass result = service.classifyByName("YETI");

            assertEquals(RPGMobClass.BOSS, result);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Config Access Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Config Access")
    class ConfigAccessTests {

        @Test
        @DisplayName("getConfig returns the injected config")
        void getConfig_returnsInjectedConfig() {
            MobClassificationConfig result = service.getConfig();

            assertSame(config, result);
        }

        @Test
        @DisplayName("Config modifications affect classification")
        void configModifications_affectClassification() {
            MobClassificationContext context = new MobClassificationContext(
                "test_mob", 0, Attitude.HOSTILE);

            // Set up no LivingWorld matches
            when(tagLookupProvider.hasTag(eq(LIVING_WORLD_AGGRESSIVE), anyInt())).thenReturn(false);
            when(tagLookupProvider.hasTag(eq(LIVING_WORLD_NEUTRAL), anyInt())).thenReturn(false);
            when(tagLookupProvider.hasTag(eq(LIVING_WORLD_PASSIVE), anyInt())).thenReturn(false);

            // Initially no override - falls through to attitude
            RPGMobClass before = service.classify(context);
            assertEquals(RPGMobClass.HOSTILE, before);

            // Add to bosses list (using BOSS since elite list is empty by default now)
            config.setBosses(Arrays.asList("test_mob"));

            RPGMobClass after = service.classify(context);
            assertEquals(RPGMobClass.BOSS, after);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Edge Cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @BeforeEach
        void setUpNoLivingWorldMatches() {
            lenient().when(tagLookupProvider.hasTag(eq(LIVING_WORLD_AGGRESSIVE), anyInt())).thenReturn(false);
            lenient().when(tagLookupProvider.hasTag(eq(LIVING_WORLD_NEUTRAL), anyInt())).thenReturn(false);
            lenient().when(tagLookupProvider.hasTag(eq(LIVING_WORLD_PASSIVE), anyInt())).thenReturn(false);
        }

        @Test
        @DisplayName("Context with all null values returns PASSIVE")
        void contextWithAllNulls_returnsPassive() {
            MobClassificationContext context = new MobClassificationContext(
                null, 0, null);

            RPGMobClass result = service.classify(context);

            assertEquals(RPGMobClass.PASSIVE, result);
        }

        @Test
        @DisplayName("Role name with mixed case is normalized")
        void roleNameWithMixedCase_isNormalized() {
            config.setBosses(Arrays.asList("dragon_king"));

            MobClassificationContext context = new MobClassificationContext(
                "DrAgOn_KiNg", 0, Attitude.HOSTILE);

            RPGMobClass result = service.classify(context);

            assertEquals(RPGMobClass.BOSS, result);
        }

        @Test
        @DisplayName("Negative roleIndex does not crash")
        void negativeRoleIndex_doesNotCrash() {
            MobClassificationContext context = new MobClassificationContext(
                null, -1, Attitude.HOSTILE);

            // Should not throw
            RPGMobClass result = service.classify(context);

            assertEquals(RPGMobClass.HOSTILE, result);
        }

        @Test
        @DisplayName("LivingWorld groups are checked in correct order")
        void livingWorldGroups_checkedInCorrectOrder() {
            when(tagLookupProvider.hasTag(eq(LIVING_WORLD_AGGRESSIVE), eq(42))).thenReturn(false);
            when(tagLookupProvider.hasTag(eq(LIVING_WORLD_NEUTRAL), eq(42))).thenReturn(false);
            when(tagLookupProvider.hasTag(eq(LIVING_WORLD_PASSIVE), eq(42))).thenReturn(false);

            MobClassificationContext context = new MobClassificationContext(
                null, 42, Attitude.HOSTILE);

            service.classify(context);

            // Verify order: Aggressive → Neutral → Passive
            var inOrder = inOrder(tagLookupProvider);
            inOrder.verify(tagLookupProvider).hasTag(LIVING_WORLD_AGGRESSIVE, 42);
            inOrder.verify(tagLookupProvider).hasTag(LIVING_WORLD_NEUTRAL, 42);
            inOrder.verify(tagLookupProvider).hasTag(LIVING_WORLD_PASSIVE, 42);
        }
    }
}
