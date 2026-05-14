package io.github.larsonix.trailoforbis.mobs.modifiers;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link MobModifierComponent} serialization and resolution.
 *
 * <p>Critical for production: saved entities must survive world reload.
 * If modifier names change, the component must gracefully drop unknown
 * modifiers instead of crashing. Forward compatibility is essential for
 * a published mod — players update and their existing world must load.
 */
@DisplayName("MobModifierComponent")
class MobModifierComponentTest {

    @Nested
    @DisplayName("Serialization Roundtrip")
    class SerializationRoundtrip {

        @Test
        @DisplayName("Single modifier roundtrips through string")
        void singleModifier_roundtrip() {
            MobModifierComponent comp = new MobModifierComponent();
            comp.setModifiers(List.of(ModifierType.BLAZING));

            assertEquals("BLAZING", comp.getModifierString());

            // Simulate deserialize: clear transient, resolve from string
            MobModifierComponent loaded = new MobModifierComponent();
            loaded.setModifierString("BLAZING");
            loaded.resolveModifiers();

            assertEquals(1, loaded.modifierCount());
            assertTrue(loaded.hasModifier(ModifierType.BLAZING));
        }

        @Test
        @DisplayName("Multiple modifiers roundtrip through comma-separated string")
        void multipleModifiers_roundtrip() {
            MobModifierComponent comp = new MobModifierComponent();
            comp.setModifiers(List.of(ModifierType.BLAZING, ModifierType.ENRAGED, ModifierType.VOLATILE));

            assertEquals("BLAZING,ENRAGED,VOLATILE", comp.getModifierString());

            MobModifierComponent loaded = new MobModifierComponent();
            loaded.setModifierString("BLAZING,ENRAGED,VOLATILE");
            loaded.resolveModifiers();

            assertEquals(3, loaded.modifierCount());
            assertTrue(loaded.hasModifier(ModifierType.BLAZING));
            assertTrue(loaded.hasModifier(ModifierType.ENRAGED));
            assertTrue(loaded.hasModifier(ModifierType.VOLATILE));
        }

        @Test
        @DisplayName("Empty string resolves to empty list")
        void emptyString_resolvesToEmpty() {
            MobModifierComponent comp = new MobModifierComponent();
            comp.setModifierString("");
            comp.resolveModifiers();

            assertEquals(0, comp.modifierCount());
            assertTrue(comp.getModifiers().isEmpty());
        }

        @Test
        @DisplayName("Null string resolves to empty list")
        void nullString_resolvesToEmpty() {
            MobModifierComponent comp = new MobModifierComponent();
            comp.setModifierString(null);
            comp.resolveModifiers();

            assertEquals(0, comp.modifierCount());
        }
    }

    @Nested
    @DisplayName("Forward Compatibility")
    class ForwardCompatibility {

        @Test
        @DisplayName("Unknown modifier names are silently dropped")
        void unknownModifiers_silentlyDropped() {
            MobModifierComponent comp = new MobModifierComponent();
            comp.setModifierString("BLAZING,FUTURE_MOD_THAT_DOESNT_EXIST,ENRAGED");
            comp.resolveModifiers();

            assertEquals(2, comp.modifierCount(), "Should have 2 valid modifiers (unknown dropped)");
            assertTrue(comp.hasModifier(ModifierType.BLAZING));
            assertTrue(comp.hasModifier(ModifierType.ENRAGED));
        }

        @Test
        @DisplayName("All unknown names result in empty list (not crash)")
        void allUnknown_emptyNotCrash() {
            MobModifierComponent comp = new MobModifierComponent();
            comp.setModifierString("DELETED_MOD_A,DELETED_MOD_B");
            comp.resolveModifiers();

            assertEquals(0, comp.modifierCount());
            assertFalse(comp.hasAnyTickable());
            assertFalse(comp.hasAnyDeathTrigger());
        }

        @Test
        @DisplayName("Whitespace in names is trimmed")
        void whitespace_isTrimmed() {
            MobModifierComponent comp = new MobModifierComponent();
            comp.setModifierString(" BLAZING , ENRAGED ");
            comp.resolveModifiers();

            assertEquals(2, comp.modifierCount());
            assertTrue(comp.hasModifier(ModifierType.BLAZING));
            assertTrue(comp.hasModifier(ModifierType.ENRAGED));
        }
    }

    @Nested
    @DisplayName("Query Methods")
    class QueryMethods {

        @Test
        @DisplayName("hasAnyTickable returns true when behavioral modifier present")
        void hasAnyTickable_withBehavioral() {
            MobModifierComponent comp = new MobModifierComponent();
            comp.setModifiers(List.of(ModifierType.ENRAGED));
            assertTrue(comp.hasAnyTickable());
        }

        @Test
        @DisplayName("hasAnyTickable returns false for pure stat modifiers")
        void hasAnyTickable_pureStatOnly() {
            MobModifierComponent comp = new MobModifierComponent();
            comp.setModifiers(List.of(ModifierType.HARDENED));
            assertFalse(comp.hasAnyTickable());
        }

        @Test
        @DisplayName("hasAnyDeathTrigger returns true for Volatile")
        void hasAnyDeathTrigger_volatile() {
            MobModifierComponent comp = new MobModifierComponent();
            comp.setModifiers(List.of(ModifierType.VOLATILE));
            assertTrue(comp.hasAnyDeathTrigger());
        }

        @Test
        @DisplayName("hasAnyDeathTrigger returns false for non-death modifiers")
        void hasAnyDeathTrigger_nonDeath() {
            MobModifierComponent comp = new MobModifierComponent();
            comp.setModifiers(List.of(ModifierType.HARDENED, ModifierType.SWIFT));
            assertFalse(comp.hasAnyDeathTrigger());
        }
    }

    @Nested
    @DisplayName("Threshold Flags")
    class ThresholdFlags {

        @Test
        @DisplayName("Enrage flag defaults to false")
        void enrageDefault() {
            MobModifierComponent comp = new MobModifierComponent();
            assertFalse(comp.isEnrageTriggered());
        }

        @Test
        @DisplayName("Enrage flag can be set and read")
        void enrageSetGet() {
            MobModifierComponent comp = new MobModifierComponent();
            comp.setEnrageTriggered(true);
            assertTrue(comp.isEnrageTriggered());
        }

        @Test
        @DisplayName("Summon thresholds default to false")
        void summonThresholdsDefault() {
            MobModifierComponent comp = new MobModifierComponent();
            assertFalse(comp.isSummonThreshold60Triggered());
            assertFalse(comp.isSummonThreshold30Triggered());
        }

        @Test
        @DisplayName("Damage timestamp starts at 0 (allows immediate regen)")
        void damageTimestamp_startsAtZero() {
            MobModifierComponent comp = new MobModifierComponent();
            assertEquals(0, comp.getLastDamageTimestamp());
        }

        @Test
        @DisplayName("markDamaged sets timestamp to current time")
        void markDamaged_setsTimestamp() {
            MobModifierComponent comp = new MobModifierComponent();
            long before = System.currentTimeMillis();
            comp.markDamaged();
            long after = System.currentTimeMillis();
            assertTrue(comp.getLastDamageTimestamp() >= before);
            assertTrue(comp.getLastDamageTimestamp() <= after);
        }
    }
}
