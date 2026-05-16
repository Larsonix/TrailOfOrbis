package io.github.larsonix.trailoforbis.combat;

import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.elemental.ElementType;
import io.github.larsonix.trailoforbis.elemental.ElementalStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the spell damage code path through RPGDamageCalculator.
 *
 * <p>Spells follow a distinct pipeline from physical attacks:
 * <ul>
 *   <li>Base damage placed into the spell element bucket (not physical)</li>
 *   <li>Flat spellDamage added to the spell element (not physicalDamage)</li>
 *   <li>Conversion moves FROM the spell element TO other elements (self-conversion skipped)</li>
 *   <li>spellDamagePercent + projectileDamagePercent (hex) + damagePercent scale the spell element</li>
 * </ul>
 *
 * <p><b>Critical:</b> {@code calculateWithForcedCrit()} always passes null for spellElement,
 * so all spell tests MUST use the 12-parameter {@code calculate()} overload.
 */
class SpellDamagePipelineTest {

    private RPGDamageCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new RPGDamageCalculator();
    }

    /** Helper: spell calculation with no crit, no defenses, no conditionals. */
    private DamageBreakdown spellCalc(float base, ComputedStats stats, ElementalStats elemStats,
                                       ComputedStats defStats, ElementalStats defElem,
                                       ElementType element, boolean projectileSpell) {
        return calculator.calculate(base, stats, elemStats, defStats, defElem,
            AttackType.SPELL, false, 1.0f, false, element, 1, projectileSpell);
    }

    // ==================== Base Damage Routing ====================

    @Nested
    @DisplayName("Spell Base Damage Routing")
    class BaseDamageRouting {

        @Test
        @DisplayName("Base damage placed into spell element slot, not physical")
        void spellBaseDamage_placedInSpellElementSlot() {
            ComputedStats stats = ComputedStats.builder()
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            DamageBreakdown result = spellCalc(100f, stats, new ElementalStats(),
                null, null, ElementType.FIRE, false);

            assertEquals(0f, result.physicalDamage(), 0.1f,
                "Spell should produce 0 physical damage");
            assertEquals(100f, result.getElementalDamage(ElementType.FIRE), 0.1f,
                "Spell should place 100 base damage into FIRE slot");
            assertEquals(100f, result.totalDamage(), 0.1f);
        }

        @Test
        @DisplayName("Each element routes base damage to correct slot")
        void eachElement_routesCorrectly() {
            ComputedStats stats = ComputedStats.builder()
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            for (ElementType element : ElementType.values()) {
                DamageBreakdown result = spellCalc(100f, stats, new ElementalStats(),
                    null, null, element, false);

                assertEquals(100f, result.getElementalDamage(element), 0.1f,
                    element.name() + " spell should have 100 " + element.name() + " damage");
                assertEquals(0f, result.physicalDamage(), 0.1f,
                    element.name() + " spell should have 0 physical damage");

                for (ElementType other : ElementType.values()) {
                    if (other != element) {
                        assertEquals(0f, result.getElementalDamage(other), 0.001f,
                            other.name() + " must be 0 for " + element.name() + " spell");
                    }
                }
            }
        }
    }

    // ==================== Flat Spell Damage ====================

    @Nested
    @DisplayName("Flat Spell Damage (Step 1)")
    class FlatSpellDamage {

        @Test
        @DisplayName("spellDamage stat added to spell element slot")
        void spellFlatDamage_addedToSpellElement() {
            ComputedStats stats = ComputedStats.builder()
                .spellDamage(20f)
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            DamageBreakdown result = spellCalc(100f, stats, new ElementalStats(),
                null, null, ElementType.FIRE, false);

            assertEquals(120f, result.getElementalDamage(ElementType.FIRE), 0.1f,
                "100 base + 20 flat spell = 120 fire");
            assertEquals(0f, result.physicalDamage(), 0.001f);
        }

        @Test
        @DisplayName("physicalDamage stat NOT added for spells")
        void physicalDamage_notAddedForSpells() {
            ComputedStats stats = ComputedStats.builder()
                .physicalDamage(50f)
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            DamageBreakdown result = spellCalc(100f, stats, new ElementalStats(),
                null, null, ElementType.WATER, false);

            // physicalDamage should be ignored for spells — only spellDamage adds flat
            assertEquals(100f, result.getElementalDamage(ElementType.WATER), 0.1f,
                "Spells should NOT add physicalDamage flat");
            assertEquals(0f, result.physicalDamage(), 0.001f);
        }

        @Test
        @DisplayName("DOT spell skips flat spellDamage")
        void dotSpell_skipsFlat() {
            ComputedStats stats = ComputedStats.builder()
                .spellDamage(50f)
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            DamageBreakdown result = calculator.calculate(100f, stats, new ElementalStats(),
                null, null, AttackType.SPELL, true, // isDOT = true
                1.0f, false, ElementType.FIRE, 1, false);

            // DOT skips flat damage entirely
            assertEquals(100f, result.totalDamage(), 0.5f,
                "DOT spell should not add flat spellDamage");
        }
    }

    // ==================== Spell Damage Percent (Step 4) ====================

    @Nested
    @DisplayName("Spell Damage Percent Scaling")
    class SpellDamagePercentScaling {

        @Test
        @DisplayName("spellDamagePercent scales spell element")
        void spellDamagePercent_appliedToSpellElement() {
            ComputedStats stats = ComputedStats.builder()
                .spellDamagePercent(50f) // +50%
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            DamageBreakdown result = spellCalc(100f, stats, new ElementalStats(),
                null, null, ElementType.WATER, false);

            assertEquals(150f, result.getElementalDamage(ElementType.WATER), 0.1f,
                "100 * (1 + 50/100) = 150 water");
        }

        @Test
        @DisplayName("damagePercent also scales spells (additive with spellDmg%)")
        void spellWithGlobalDamagePercent() {
            ComputedStats stats = ComputedStats.builder()
                .spellDamagePercent(30f)
                .damagePercent(20f) // Global additive
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            DamageBreakdown result = spellCalc(100f, stats, new ElementalStats(),
                null, null, ElementType.LIGHTNING, false);

            // spellPct + dmgPct = 30 + 20 = 50% total
            assertEquals(150f, result.getElementalDamage(ElementType.LIGHTNING), 0.1f,
                "100 * (1 + 50/100) = 150 lightning");
        }

        @Test
        @DisplayName("projectileSpell=true applies projectileDamagePercent")
        void projectileSpell_appliesProjectileDamagePercent() {
            ComputedStats stats = ComputedStats.builder()
                .projectileDamagePercent(30f)
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            DamageBreakdown result = spellCalc(100f, stats, new ElementalStats(),
                null, null, ElementType.FIRE, true); // projectileSpell = true

            // projPct(30) + spellPct(0) + dmgPct(0) = 30%
            assertEquals(130f, result.getElementalDamage(ElementType.FIRE), 0.1f,
                "Hex spell: 100 * (1 + 30/100) = 130 fire");
        }

        @Test
        @DisplayName("projectileSpell=false ignores projectileDamagePercent")
        void nonProjectileSpell_ignoresProjectileDamagePercent() {
            ComputedStats stats = ComputedStats.builder()
                .projectileDamagePercent(30f)
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            DamageBreakdown result = spellCalc(100f, stats, new ElementalStats(),
                null, null, ElementType.FIRE, false); // projectileSpell = false

            assertEquals(100f, result.getElementalDamage(ElementType.FIRE), 0.1f,
                "Non-hex spell should NOT include projectileDamagePercent");
        }
    }

    // ==================== Spell Conversion (Step 3) ====================

    @Nested
    @DisplayName("Spell Conversion")
    class SpellConversion {

        @Test
        @DisplayName("Fire spell + 50% void conversion = 50 fire + 50 void")
        void spellConversion_fireToVoid() {
            ComputedStats stats = ComputedStats.builder()
                .voidConversion(50f)
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            DamageBreakdown result = spellCalc(100f, stats, new ElementalStats(),
                null, null, ElementType.FIRE, false);

            assertEquals(50f, result.getElementalDamage(ElementType.FIRE), 0.1f,
                "50% of fire should remain");
            assertEquals(50f, result.getElementalDamage(ElementType.VOID), 0.1f,
                "50% should convert to void");
            assertEquals(100f, result.totalDamage(), 0.1f,
                "Total should be preserved");
        }

        @Test
        @DisplayName("Self-conversion is ignored (fire→fire has no effect)")
        void spellConversion_selfElementIgnored() {
            ComputedStats stats = ComputedStats.builder()
                .fireConversion(100f) // Would convert physical→fire, but we're already fire
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            DamageBreakdown result = spellCalc(100f, stats, new ElementalStats(),
                null, null, ElementType.FIRE, false);

            // Fire spell can't convert to itself — fireConversion targets physical→fire
            // For spells, conversion is from spell element to OTHER elements
            assertEquals(100f, result.getElementalDamage(ElementType.FIRE), 0.1f,
                "Self-conversion should have no effect, fire stays 100");
        }

        @Test
        @DisplayName("Overcapped spell conversion scales proportionally")
        void spellConversion_cappedAt100() {
            ComputedStats stats = ComputedStats.builder()
                .voidConversion(80f)
                .waterConversion(80f)  // Total 160% exceeds 100%
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            DamageBreakdown result = spellCalc(100f, stats, new ElementalStats(),
                null, null, ElementType.FIRE, false);

            // 160% capped to 100%: each gets 80/160 * 100% = 50%
            float voidDmg = result.getElementalDamage(ElementType.VOID);
            float waterDmg = result.getElementalDamage(ElementType.WATER);
            float fireDmg = result.getElementalDamage(ElementType.FIRE);

            assertEquals(0f, fireDmg, 0.5f,
                "All fire should be converted (100% total)");
            assertEquals(50f, voidDmg, 1f,
                "Void should get 50% (80/160)");
            assertEquals(50f, waterDmg, 1f,
                "Water should get 50% (80/160)");
            assertEquals(100f, result.totalDamage(), 1f,
                "Total damage preserved");
        }

        @Test
        @DisplayName("Multi-element spell conversion distributes correctly")
        void spellConversion_multiElement() {
            ComputedStats stats = ComputedStats.builder()
                .voidConversion(30f)
                .waterConversion(30f)
                .lightningConversion(30f) // Total 90% from fire
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            DamageBreakdown result = spellCalc(100f, stats, new ElementalStats(),
                null, null, ElementType.FIRE, false);

            assertEquals(10f, result.getElementalDamage(ElementType.FIRE), 0.1f,
                "10% fire should remain (100% - 90%)");
            assertEquals(30f, result.getElementalDamage(ElementType.VOID), 0.1f);
            assertEquals(30f, result.getElementalDamage(ElementType.WATER), 0.1f);
            assertEquals(30f, result.getElementalDamage(ElementType.LIGHTNING), 0.1f);
            assertEquals(100f, result.totalDamage(), 0.1f);
        }
    }

    // ==================== Spell Penetration ====================

    @Nested
    @DisplayName("Spell Penetration")
    class SpellPenetrationTests {

        @Test
        @DisplayName("spellPenetration adds to element pen for SPELL attacks")
        void spellPenetration_addedToElementPen_forSpells() {
            ComputedStats attackerStats = ComputedStats.builder()
                .spellPenetration(20f)
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            ElementalStats attackerElemental = new ElementalStats();
            attackerElemental.setPenetration(ElementType.FIRE, 10.0); // 10% fire pen

            ComputedStats defenderStats = ComputedStats.builder().build();
            ElementalStats defenderElemental = new ElementalStats();
            defenderElemental.setResistance(ElementType.FIRE, 75.0); // Max resist

            DamageBreakdown result = spellCalc(100f, attackerStats, attackerElemental,
                defenderStats, defenderElemental, ElementType.FIRE, false);

            // Effective resist = max(-200, min(75, 75 - (10 + 20))) = min(75, 45) = 45%
            // Fire damage = 100 * (1 - 45/100) = 55
            assertEquals(55f, result.getElementalDamage(ElementType.FIRE), 1f,
                "75% resist - 30% pen (10 fire + 20 spell) = 45% effective → 55 fire damage");
        }

        @Test
        @DisplayName("spellPenetration ignored for MELEE attacks")
        void spellPenetration_ignoredForMelee() {
            ComputedStats attackerStats = ComputedStats.builder()
                .spellPenetration(50f) // Should be ignored for melee
                .criticalChance(0f)
                .criticalMultiplier(100f)
                .build();

            ElementalStats attackerElemental = new ElementalStats();
            attackerElemental.setFlatDamage(ElementType.FIRE, 100.0);

            ComputedStats defenderStats = ComputedStats.builder().build();
            ElementalStats defenderElemental = new ElementalStats();
            defenderElemental.setResistance(ElementType.FIRE, 50.0);

            DamageBreakdown result = calculator.calculateWithForcedCrit(
                0f, attackerStats, attackerElemental, defenderStats, defenderElemental,
                AttackType.MELEE, false, 1);

            // spellPen should NOT apply — only element-specific pen
            // Effective resist = min(75, 50 - 0) = 50%
            // Fire: 100 * (1 - 50/100) = 50
            assertEquals(50f, result.getElementalDamage(ElementType.FIRE), 1f,
                "spellPen should not apply to melee attacks");
        }
    }

    // ==================== Spell + Crit ====================

    @Nested
    @DisplayName("Spell Critical Strike")
    class SpellCrit {

        @Test
        @DisplayName("Crit applies uniformly to spell element damage")
        void spell_critAppliesUniformly() {
            ComputedStats stats = ComputedStats.builder()
                .criticalChance(100f)
                .criticalMultiplier(200f) // 2.0x
                .build();

            DamageBreakdown result = spellCalc(100f, stats, new ElementalStats(),
                null, null, ElementType.FIRE, false);

            assertEquals(200f, result.getElementalDamage(ElementType.FIRE), 0.1f,
                "100 fire * 2.0x crit = 200 fire");
            assertTrue(result.wasCritical());
            assertEquals(2.0f, result.critMultiplier(), 0.01f);
        }
    }
}
