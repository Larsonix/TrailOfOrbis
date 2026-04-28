package io.github.larsonix.trailoforbis.combat.indicators.color;

import com.hypixel.hytale.protocol.Color;
import io.github.larsonix.trailoforbis.combat.AttackType;
import io.github.larsonix.trailoforbis.combat.DamageBreakdown;
import io.github.larsonix.trailoforbis.combat.DamageType;
import io.github.larsonix.trailoforbis.combat.indicators.CombatIndicatorService.CombatTextParams;
import io.github.larsonix.trailoforbis.elemental.ElementType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link CombatTextProfileResolver} — the pure logic that maps
 * damage events to visual combat text profiles.
 */
class CombatTextProfileResolverTest {

    private static final Color WHITE = new Color((byte) 0xFF, (byte) 0xFF, (byte) 0xFF);
    private static final Color RED = new Color((byte) 0xFF, (byte) 0x44, (byte) 0x44);
    private static final Color ORANGE = new Color((byte) 0xFF, (byte) 0x66, (byte) 0x00);
    private static final Color FIRE_CRIT = new Color((byte) 0xFF, (byte) 0x22, (byte) 0x00);
    private static final Color BLUE = new Color((byte) 0x44, (byte) 0xCC, (byte) 0xFF);
    private static final Color YELLOW = new Color((byte) 0xFF, (byte) 0xEE, (byte) 0x00);
    private static final Color PURPLE = new Color((byte) 0xAA, (byte) 0x44, (byte) 0xFF);
    private static final Color MAGIC_BLUE = new Color((byte) 0x44, (byte) 0xAA, (byte) 0xFF);
    private static final Color GRAY = new Color((byte) 0xAA, (byte) 0xAA, (byte) 0xAA);
    private static final Color GOLD = new Color((byte) 0xFF, (byte) 0xD7, (byte) 0x00);
    private static final Color DARK_GRAY = new Color((byte) 0x88, (byte) 0x88, (byte) 0x88);

    private static final CombatTextAnimation[] DEFAULT_ANIM = new CombatTextAnimation[0];

    private CombatTextProfileResolver resolver;
    private Map<String, CombatTextProfile> profiles;

    @BeforeEach
    void setUp() {
        profiles = new LinkedHashMap<>();

        // Physical
        profiles.put("physical_normal", profile("physical_normal", WHITE));
        profiles.put("physical_crit", profile("physical_crit", RED));

        // Elements
        profiles.put("fire_normal", profile("fire_normal", ORANGE));
        profiles.put("fire_crit", profile("fire_crit", FIRE_CRIT));
        profiles.put("water_normal", profile("water_normal", BLUE));
        profiles.put("water_crit", profile("water_crit", BLUE));
        profiles.put("lightning_normal", profile("lightning_normal", YELLOW));
        profiles.put("lightning_crit", profile("lightning_crit", YELLOW));
        profiles.put("void_normal", profile("void_normal", PURPLE));
        profiles.put("void_crit", profile("void_crit", PURPLE));

        // Magic
        profiles.put("magic_normal", profile("magic_normal", MAGIC_BLUE));
        profiles.put("magic_crit", profile("magic_crit", MAGIC_BLUE));

        // Avoidance
        profiles.put("dodged", profile("dodged", GRAY));
        profiles.put("blocked", profile("blocked", GRAY));
        profiles.put("parried", profile("parried", GOLD));
        profiles.put("missed", profile("missed", DARK_GRAY));

        resolver = new CombatTextProfileResolver(profiles);
    }

    // ── Physical damage ──────────────────────────────────────────────────

    @Test
    void physicalNormalDamage_resolvesPhysicalNormal() {
        DamageBreakdown breakdown = DamageBreakdown.builder()
            .physicalDamage(100f)
            .damageType(DamageType.PHYSICAL)
            .build();

        CombatTextProfile result = resolver.resolve(breakdown, CombatTextParams.forDamage(false));

        assertNotNull(result);
        assertEquals("physical_normal", result.id());
        assertEquals(WHITE, result.color());
    }

    @Test
    void physicalCritDamage_resolvesPhysicalCrit() {
        DamageBreakdown breakdown = DamageBreakdown.builder()
            .physicalDamage(200f)
            .wasCritical(true)
            .damageType(DamageType.PHYSICAL)
            .build();

        CombatTextProfile result = resolver.resolve(breakdown, CombatTextParams.forDamage(true));

        assertNotNull(result);
        assertEquals("physical_crit", result.id());
        assertEquals(RED, result.color());
    }

    // ── Elemental damage ─────────────────────────────────────────────────

    @Test
    void fireNormalDamage_resolvesFireNormal() {
        DamageBreakdown breakdown = DamageBreakdown.builder()
            .elementalDamage(ElementType.FIRE, 80f)
            .damageType(DamageType.FIRE)
            .build();

        CombatTextProfile result = resolver.resolve(breakdown, CombatTextParams.forDamage(false));

        assertNotNull(result);
        assertEquals("fire_normal", result.id());
        assertEquals(ORANGE, result.color());
    }

    @Test
    void fireCritDamage_resolvesFireCrit() {
        DamageBreakdown breakdown = DamageBreakdown.builder()
            .elementalDamage(ElementType.FIRE, 80f)
            .wasCritical(true)
            .damageType(DamageType.FIRE)
            .build();

        CombatTextProfile result = resolver.resolve(breakdown, CombatTextParams.forDamage(true));

        assertNotNull(result);
        assertEquals("fire_crit", result.id());
    }

    @Test
    void waterDamage_resolvesWater() {
        DamageBreakdown breakdown = DamageBreakdown.builder()
            .elementalDamage(ElementType.WATER, 60f)
            .damageType(DamageType.WATER)
            .build();

        CombatTextProfile result = resolver.resolve(breakdown, CombatTextParams.forDamage(false));

        assertNotNull(result);
        assertEquals("water_normal", result.id());
    }

    @Test
    void lightningDamage_resolvesLightning() {
        DamageBreakdown breakdown = DamageBreakdown.builder()
            .elementalDamage(ElementType.LIGHTNING, 50f)
            .damageType(DamageType.LIGHTNING)
            .build();

        CombatTextProfile result = resolver.resolve(breakdown, CombatTextParams.forDamage(false));

        assertNotNull(result);
        assertEquals("lightning_normal", result.id());
    }

    @Test
    void voidDamage_resolvesVoid() {
        DamageBreakdown breakdown = DamageBreakdown.builder()
            .elementalDamage(ElementType.VOID, 40f)
            .damageType(DamageType.VOID)
            .build();

        CombatTextProfile result = resolver.resolve(breakdown, CombatTextParams.forDamage(false));

        assertNotNull(result);
        assertEquals("void_normal", result.id());
    }

    // ── Most prominent damage source wins ──────────────────────────────

    @Test
    void mixedElementalDamage_resolvesPrimaryElement() {
        DamageBreakdown breakdown = DamageBreakdown.builder()
            .elementalDamage(ElementType.FIRE, 50f)
            .elementalDamage(ElementType.WATER, 30f)
            .damageType(DamageType.FIRE)
            .build();

        CombatTextProfile result = resolver.resolve(breakdown, CombatTextParams.forDamage(false));

        assertNotNull(result);
        assertEquals("fire_normal", result.id(), "Should resolve to primary element (fire > water)");
    }

    @Test
    void physicalDominant_resolvesToPhysical() {
        // 7 physical + 5 void → physical is most prominent → white
        DamageBreakdown breakdown = DamageBreakdown.builder()
            .physicalDamage(7f)
            .elementalDamage(ElementType.VOID, 5f)
            .damageType(DamageType.PHYSICAL)
            .build();

        CombatTextProfile result = resolver.resolve(breakdown, CombatTextParams.forDamage(false));

        assertNotNull(result);
        assertEquals("physical_normal", result.id(), "Physical > void → should be white (physical)");
    }

    @Test
    void elementalDominant_resolvesToElement() {
        // 50 lightning + 30 physical → lightning is most prominent → yellow
        DamageBreakdown breakdown = DamageBreakdown.builder()
            .physicalDamage(30f)
            .elementalDamage(ElementType.LIGHTNING, 50f)
            .damageType(DamageType.LIGHTNING)
            .build();

        CombatTextProfile result = resolver.resolve(breakdown, CombatTextParams.forDamage(false));

        assertNotNull(result);
        assertEquals("lightning_normal", result.id(), "Lightning > physical → should be yellow (lightning)");
    }

    @Test
    void physicalTied_resolvesToPhysical() {
        // Equal damage → physical wins as default
        DamageBreakdown breakdown = DamageBreakdown.builder()
            .physicalDamage(50f)
            .elementalDamage(ElementType.FIRE, 50f)
            .damageType(DamageType.FIRE)
            .build();

        CombatTextProfile result = resolver.resolve(breakdown, CombatTextParams.forDamage(false));

        assertNotNull(result);
        assertEquals("physical_normal", result.id(), "Tied damage → physical wins as default");
    }

    @Test
    void physicalDominantCrit_resolvesToPhysicalCrit() {
        // 100 physical + 30 void, crit → should be RED (physical_crit), not purple
        DamageBreakdown breakdown = DamageBreakdown.builder()
            .physicalDamage(100f)
            .elementalDamage(ElementType.VOID, 30f)
            .wasCritical(true)
            .damageType(DamageType.PHYSICAL)
            .build();

        CombatTextProfile result = resolver.resolve(breakdown, CombatTextParams.forDamage(true));

        assertNotNull(result);
        assertEquals("physical_crit", result.id(), "Physical dominant crit → should be red (physical_crit)");
    }

    @Test
    void elementalDominantCrit_resolvesToElementalCrit() {
        // 30 physical + 100 fire, crit → should be fire_crit
        DamageBreakdown breakdown = DamageBreakdown.builder()
            .physicalDamage(30f)
            .elementalDamage(ElementType.FIRE, 100f)
            .wasCritical(true)
            .damageType(DamageType.FIRE)
            .build();

        CombatTextProfile result = resolver.resolve(breakdown, CombatTextParams.forDamage(true));

        assertNotNull(result);
        assertEquals("fire_crit", result.id(), "Fire dominant crit → should be fire_crit");
    }

    @Test
    void multipleElementsHighestWins() {
        // 10 phys + 20 fire + 50 water + 5 void → water is most prominent
        DamageBreakdown breakdown = DamageBreakdown.builder()
            .physicalDamage(10f)
            .elementalDamage(ElementType.FIRE, 20f)
            .elementalDamage(ElementType.WATER, 50f)
            .elementalDamage(ElementType.VOID, 5f)
            .damageType(DamageType.WATER)
            .build();

        CombatTextProfile result = resolver.resolve(breakdown, CombatTextParams.forDamage(false));

        assertNotNull(result);
        assertEquals("water_normal", result.id(), "Water is highest across all sources → blue");
    }

    // ── Magic damage ─────────────────────────────────────────────────────

    @Test
    void magicDamage_resolvesMagic() {
        DamageBreakdown breakdown = DamageBreakdown.builder()
            .physicalDamage(100f)
            .damageType(DamageType.MAGIC)
            .build();

        CombatTextProfile result = resolver.resolve(breakdown, CombatTextParams.forDamage(false));

        assertNotNull(result);
        assertEquals("magic_normal", result.id());
    }

    // ── Avoidance (highest priority) ─────────────────────────────────────

    @Test
    void dodged_resolvesDodgedProfile() {
        CombatTextProfile result = resolver.resolve(
            DamageBreakdown.avoided(DamageBreakdown.AvoidanceReason.DODGED, AttackType.MELEE),
            CombatTextParams.forAvoidance(DamageBreakdown.AvoidanceReason.DODGED));

        assertNotNull(result);
        assertEquals("dodged", result.id());
    }

    @Test
    void blocked_resolvesBlockedProfile() {
        CombatTextProfile result = resolver.resolve(
            DamageBreakdown.avoided(DamageBreakdown.AvoidanceReason.BLOCKED, AttackType.MELEE),
            CombatTextParams.forAvoidance(DamageBreakdown.AvoidanceReason.BLOCKED));

        assertNotNull(result);
        assertEquals("blocked", result.id());
    }

    @Test
    void parried_resolvesParriedProfile() {
        CombatTextProfile result = resolver.resolve(
            DamageBreakdown.avoided(DamageBreakdown.AvoidanceReason.PARRIED, AttackType.MELEE),
            CombatTextParams.forAvoidance(DamageBreakdown.AvoidanceReason.PARRIED));

        assertNotNull(result);
        assertEquals("parried", result.id());
    }

    @Test
    void missed_resolvesMissedProfile() {
        CombatTextProfile result = resolver.resolve(
            DamageBreakdown.avoided(DamageBreakdown.AvoidanceReason.MISSED, AttackType.MELEE),
            CombatTextParams.forAvoidance(DamageBreakdown.AvoidanceReason.MISSED));

        assertNotNull(result);
        assertEquals("missed", result.id());
    }

    // ── Fallback chain ───────────────────────────────────────────────────

    @Test
    void missingCritVariant_fallsBackToNormal() {
        // Remove earth crit profile
        profiles.remove("earth_crit");
        profiles.put("earth_normal", profile("earth_normal", ORANGE));
        resolver = new CombatTextProfileResolver(profiles);

        DamageBreakdown breakdown = DamageBreakdown.builder()
            .elementalDamage(ElementType.EARTH, 50f)
            .wasCritical(true)
            .damageType(DamageType.EARTH)
            .build();

        CombatTextProfile result = resolver.resolve(breakdown, CombatTextParams.forDamage(true));

        assertNotNull(result);
        assertEquals("earth_normal", result.id(), "Missing earth_crit should fall back to earth_normal");
    }

    @Test
    void missingElementProfile_fallsBackToPhysical() {
        // Remove all wind profiles
        profiles.remove("wind_normal");
        profiles.remove("wind_crit");
        resolver = new CombatTextProfileResolver(profiles);

        DamageBreakdown breakdown = DamageBreakdown.builder()
            .elementalDamage(ElementType.WIND, 50f)
            .damageType(DamageType.WIND)
            .build();

        CombatTextProfile result = resolver.resolve(breakdown, CombatTextParams.forDamage(false));

        assertNotNull(result);
        assertEquals("physical_normal", result.id(), "Missing wind profiles should fall back to physical_normal");
    }

    @Test
    void nullBreakdown_fallsBackToPhysicalNormal() {
        CombatTextProfile result = resolver.resolve(null, CombatTextParams.forDamage(false));

        assertNotNull(result);
        assertEquals("physical_normal", result.id());
    }

    @Test
    void emptyProfiles_returnsNull() {
        resolver = new CombatTextProfileResolver(Map.of());

        DamageBreakdown breakdown = DamageBreakdown.builder()
            .physicalDamage(100f)
            .damageType(DamageType.PHYSICAL)
            .build();

        CombatTextProfile result = resolver.resolve(breakdown, CombatTextParams.forDamage(false));

        assertNull(result, "Empty profile map should return null (vanilla fallback)");
    }

    // ── DOT / simple damage ──────────────────────────────────────────────

    @Test
    void simpleDamage_resolvesFromDamageType() {
        DamageBreakdown breakdown = DamageBreakdown.simple(50f, DamageType.PHYSICAL);

        CombatTextProfile result = resolver.resolve(breakdown, CombatTextParams.forDamage(false));

        assertNotNull(result);
        assertEquals("physical_normal", result.id());
    }

    // ── Helper ───────────────────────────────────────────────────────────

    private static CombatTextProfile profile(String id, Color color) {
        return CombatTextProfile.unregistered(id, color, 68f, 0.4f, 2.0f, DEFAULT_ANIM);
    }
}
