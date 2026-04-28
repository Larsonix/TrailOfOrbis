package io.github.larsonix.trailoforbis.skilltree.synergy;

import io.github.larsonix.trailoforbis.skilltree.SkillTreeRegion;
import io.github.larsonix.trailoforbis.skilltree.config.SkillNode;
import io.github.larsonix.trailoforbis.skilltree.config.SkillTreeConfig;
import io.github.larsonix.trailoforbis.skilltree.model.ModifierType;
import io.github.larsonix.trailoforbis.skilltree.model.StatModifier;
import io.github.larsonix.trailoforbis.skilltree.model.StatType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SynergyNodeCalculator}.
 */
@DisplayName("SynergyNodeCalculator")
class SynergyNodeCalculatorTest {

    private SkillTreeConfig config;
    private SynergyNodeCalculator calculator;

    @BeforeEach
    void setUp() {
        config = new SkillTreeConfig();
        calculator = new SynergyNodeCalculator(config);
    }

    // ═══════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════

    private SkillNode createNode(String id, String region) {
        SkillNode node = new SkillNode();
        node.setId(id);
        node.setRegion(region);
        return node;
    }

    private SkillNode createNodeWithModifier(String id, String region, StatType stat, float value, ModifierType type) {
        SkillNode node = createNode(id, region);
        node.setModifiers(List.of(new StatModifier(stat, value, type)));
        return node;
    }

    private SkillNode createSynergyNode(String id, String region, SynergyConfig synergy) {
        SkillNode node = createNode(id, region);
        node.setSynergy(synergy);
        return node;
    }

    private SynergyConfig createElementalSynergy(String element, int perCount, String stat, double value, double cap) {
        SynergyConfig synergy = new SynergyConfig();
        synergy.setType(SynergyType.ELEMENTAL_COUNT);
        synergy.setElement(element);
        synergy.setPerCount(perCount);

        SynergyConfig.SynergyBonus bonus = new SynergyConfig.SynergyBonus();
        bonus.setStat(stat);
        bonus.setValue(value);
        bonus.setModifierType("PERCENT");
        synergy.setBonus(bonus);

        synergy.setCap(cap);
        return synergy;
    }

    private void setupConfig(Map<String, SkillNode> nodes) {
        config.setNodes(nodes);
        calculator = new SynergyNodeCalculator(config);
    }

    // ═══════════════════════════════════════════════════════════════════
    // CONSTRUCTOR TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("Should accept valid config")
        void shouldAcceptValidConfig() {
            assertDoesNotThrow(() -> new SynergyNodeCalculator(config));
        }

        @Test
        @DisplayName("Should reject null config")
        void shouldRejectNullConfig() {
            assertThrows(NullPointerException.class, () -> new SynergyNodeCalculator(null));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // ELEMENTAL COUNT TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("ELEMENTAL_COUNT Synergy")
    class ElementalCountTests {

        @Test
        @DisplayName("Should calculate bonus based on nodes in same element")
        void shouldCalculateBonusForSameElement() {
            // Setup: 1 synergy node + 6 fire nodes = 7 fire nodes total
            // Synergy: Per 3 FIRE nodes, +3% Fire Damage
            // Expected: 7 / 3 = 2 increments = +6% Fire Damage
            Map<String, SkillNode> nodes = new HashMap<>();

            // Add 6 regular fire nodes
            for (int i = 1; i <= 6; i++) {
                nodes.put("fire_" + i, createNode("fire_" + i, "FIRE"));
            }

            // Add 1 synergy node
            SynergyConfig synergy = createElementalSynergy("FIRE", 3, "FIRE_DAMAGE_PERCENT", 3.0, 30.0);
            nodes.put("fire_synergy", createSynergyNode("fire_synergy", "FIRE", synergy));

            setupConfig(nodes);

            // Allocate all nodes
            Set<String> allocated = new HashSet<>(nodes.keySet());

            List<StatModifier> bonuses = calculator.calculateSynergyBonuses(allocated);

            assertEquals(1, bonuses.size());
            StatModifier bonus = bonuses.get(0);
            assertEquals(StatType.FIRE_DAMAGE_PERCENT, bonus.getStat());
            assertEquals(6.0f, bonus.getValue(), 0.001f); // 7 nodes / 3 = 2 increments * 3 = 6
            assertEquals(ModifierType.PERCENT, bonus.getType());
        }

        @Test
        @DisplayName("Should apply cap to bonus")
        void shouldApplyCapToBonus() {
            // Setup: 30 fire nodes
            // Synergy: Per 3 FIRE nodes, +5% Fire Damage, cap 15%
            // Expected: 30 / 3 = 10 increments = 50%, but capped to 15%
            Map<String, SkillNode> nodes = new HashMap<>();

            for (int i = 1; i <= 29; i++) {
                nodes.put("fire_" + i, createNode("fire_" + i, "FIRE"));
            }

            SynergyConfig synergy = createElementalSynergy("FIRE", 3, "FIRE_DAMAGE_PERCENT", 5.0, 15.0);
            nodes.put("fire_synergy", createSynergyNode("fire_synergy", "FIRE", synergy));

            setupConfig(nodes);

            Set<String> allocated = new HashSet<>(nodes.keySet());
            List<StatModifier> bonuses = calculator.calculateSynergyBonuses(allocated);

            assertEquals(1, bonuses.size());
            assertEquals(15.0f, bonuses.get(0).getValue(), 0.001f); // Capped at 15
        }

        @Test
        @DisplayName("Should return no bonus when count is below perCount")
        void shouldReturnNoBonusWhenCountBelowPerCount() {
            // Setup: 2 fire nodes
            // Synergy: Per 3 FIRE nodes, +3% Fire Damage
            // Expected: 2 / 3 = 0 increments = no bonus
            Map<String, SkillNode> nodes = new HashMap<>();
            nodes.put("fire_1", createNode("fire_1", "FIRE"));

            SynergyConfig synergy = createElementalSynergy("FIRE", 3, "FIRE_DAMAGE_PERCENT", 3.0, 30.0);
            nodes.put("fire_synergy", createSynergyNode("fire_synergy", "FIRE", synergy));

            setupConfig(nodes);

            Set<String> allocated = new HashSet<>(nodes.keySet());
            List<StatModifier> bonuses = calculator.calculateSynergyBonuses(allocated);

            assertTrue(bonuses.isEmpty());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // TOTAL COUNT TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("TOTAL_COUNT Synergy")
    class TotalCountTests {

        @Test
        @DisplayName("Should calculate bonus based on total allocated nodes")
        void shouldCalculateBonusForTotalCount() {
            // Setup: 10 nodes across different regions
            // Synergy: Per 5 total nodes, +50 max health
            // Expected: 10 / 5 = 2 increments = +100 max health
            Map<String, SkillNode> nodes = new HashMap<>();

            // Mix of regions
            nodes.put("fire_1", createNode("fire_1", "FIRE"));
            nodes.put("fire_2", createNode("fire_2", "FIRE"));
            nodes.put("water_1", createNode("water_1", "WATER"));
            nodes.put("water_2", createNode("water_2", "WATER"));
            nodes.put("earth_1", createNode("earth_1", "EARTH"));
            nodes.put("earth_2", createNode("earth_2", "EARTH"));
            nodes.put("void_1", createNode("void_1", "VOID"));
            nodes.put("void_2", createNode("void_2", "VOID"));
            nodes.put("wind_1", createNode("wind_1", "WIND"));

            // Synergy node for total count
            SynergyConfig synergy = new SynergyConfig();
            synergy.setType(SynergyType.TOTAL_COUNT);
            synergy.setPerCount(5);

            SynergyConfig.SynergyBonus bonus = new SynergyConfig.SynergyBonus();
            bonus.setStat("MAX_HEALTH");
            bonus.setValue(50.0);
            bonus.setModifierType("FLAT");
            synergy.setBonus(bonus);
            synergy.setCap(0); // No cap

            nodes.put("core_synergy", createSynergyNode("core_synergy", "CORE", synergy));

            setupConfig(nodes);

            Set<String> allocated = new HashSet<>(nodes.keySet());
            List<StatModifier> bonuses = calculator.calculateSynergyBonuses(allocated);

            assertEquals(1, bonuses.size());
            StatModifier result = bonuses.get(0);
            assertEquals(StatType.MAX_HEALTH, result.getStat());
            assertEquals(100.0f, result.getValue(), 0.001f); // 10 nodes / 5 = 2 * 50 = 100
            assertEquals(ModifierType.FLAT, result.getType());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // TIER COUNT TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("TIER_COUNT Synergy")
    class TierCountTests {

        @Test
        @DisplayName("Should calculate bonus based on notable count")
        void shouldCalculateBonusForNotables() {
            // Setup: 4 notables
            // Synergy: Per 2 notables, +5% spell damage
            // Expected: 4 / 2 = 2 increments = +10% spell damage
            Map<String, SkillNode> nodes = new HashMap<>();

            for (int i = 1; i <= 4; i++) {
                SkillNode notable = createNode("notable_" + i, "FIRE");
                notable.setNotable(true);
                nodes.put("notable_" + i, notable);
            }

            // Synergy node for notable count
            SynergyConfig synergy = new SynergyConfig();
            synergy.setType(SynergyType.TIER_COUNT);
            synergy.setTier("NOTABLE");
            synergy.setPerCount(2);

            SynergyConfig.SynergyBonus bonus = new SynergyConfig.SynergyBonus();
            bonus.setStat("SPELL_DAMAGE_PERCENT");
            bonus.setValue(5.0);
            bonus.setModifierType("PERCENT");
            synergy.setBonus(bonus);

            nodes.put("core_synergy", createSynergyNode("core_synergy", "CORE", synergy));

            setupConfig(nodes);

            Set<String> allocated = new HashSet<>(nodes.keySet());
            List<StatModifier> bonuses = calculator.calculateSynergyBonuses(allocated);

            assertEquals(1, bonuses.size());
            assertEquals(10.0f, bonuses.get(0).getValue(), 0.001f);
        }

        @Test
        @DisplayName("Should count keystones when tier is KEYSTONE")
        void shouldCountKeystones() {
            Map<String, SkillNode> nodes = new HashMap<>();

            // 2 keystones
            for (int i = 1; i <= 2; i++) {
                SkillNode keystone = createNode("keystone_" + i, "FIRE");
                keystone.setKeystone(true);
                nodes.put("keystone_" + i, keystone);
            }

            // 3 notables (should not be counted)
            for (int i = 1; i <= 3; i++) {
                SkillNode notable = createNode("notable_" + i, "FIRE");
                notable.setNotable(true);
                nodes.put("notable_" + i, notable);
            }

            // Synergy: Per 1 keystone, +10% critical multiplier
            SynergyConfig synergy = new SynergyConfig();
            synergy.setType(SynergyType.TIER_COUNT);
            synergy.setTier("KEYSTONE");
            synergy.setPerCount(1);

            SynergyConfig.SynergyBonus bonus = new SynergyConfig.SynergyBonus();
            bonus.setStat("CRITICAL_MULTIPLIER");
            bonus.setValue(10.0);
            bonus.setModifierType("FLAT");
            synergy.setBonus(bonus);

            nodes.put("core_synergy", createSynergyNode("core_synergy", "CORE", synergy));

            setupConfig(nodes);

            Set<String> allocated = new HashSet<>(nodes.keySet());
            List<StatModifier> bonuses = calculator.calculateSynergyBonuses(allocated);

            assertEquals(1, bonuses.size());
            assertEquals(20.0f, bonuses.get(0).getValue(), 0.001f); // 2 keystones * 10 = 20
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // BRANCH COUNT TESTS
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("BRANCH_COUNT Synergy")
    class BranchCountTests {

        private SynergyConfig createBranchSynergy(int perCount, String stat, double value, String modType, double cap) {
            SynergyConfig synergy = new SynergyConfig();
            synergy.setType(SynergyType.BRANCH_COUNT);
            synergy.setPerCount(perCount);

            SynergyConfig.SynergyBonus bonus = new SynergyConfig.SynergyBonus();
            bonus.setStat(stat);
            bonus.setValue(value);
            bonus.setModifierType(modType);
            synergy.setBonus(bonus);

            synergy.setCap(cap);
            return synergy;
        }

        @Test
        @DisplayName("Should count only own region nodes")
        void shouldCountOnlyOwnRegionNodes() {
            // Setup: 1 HAVOC synergy + 6 HAVOC basic nodes = 7 HAVOC nodes
            // Plus 10 FIRE nodes (adjacent — should NOT be counted)
            // Synergy: Per 3 HAVOC nodes, +2% Crit Multiplier
            // Expected: 7 / 3 = 2 increments = +4%
            Map<String, SkillNode> nodes = new HashMap<>();

            for (int i = 1; i <= 6; i++) {
                nodes.put("havoc_" + i, createNode("havoc_" + i, "HAVOC"));
            }

            // Adjacent elemental nodes — should NOT count
            for (int i = 1; i <= 10; i++) {
                nodes.put("fire_" + i, createNode("fire_" + i, "FIRE"));
            }

            SynergyConfig synergy = createBranchSynergy(3, "CRITICAL_MULTIPLIER", 2.0, "FLAT", 20.0);
            nodes.put("havoc_synergy", createSynergyNode("havoc_synergy", "HAVOC", synergy));

            setupConfig(nodes);

            Set<String> allocated = new HashSet<>(nodes.keySet());
            List<StatModifier> bonuses = calculator.calculateSynergyBonuses(allocated);

            assertEquals(1, bonuses.size());
            StatModifier bonus = bonuses.get(0);
            assertEquals(StatType.CRITICAL_MULTIPLIER, bonus.getStat());
            // 7 HAVOC nodes / 3 = 2 increments * 2.0 = 4.0 (NOT counting 10 FIRE nodes)
            assertEquals(4.0f, bonus.getValue(), 0.001f);
            assertEquals(ModifierType.FLAT, bonus.getType());
        }

        @Test
        @DisplayName("Should not count adjacent region nodes")
        void shouldNotCountAdjacentRegionNodes() {
            // Setup: 1 HAVOC synergy + 2 HAVOC basic = 3 HAVOC nodes
            // Plus 20 nodes in FIRE, VOID, LIGHTNING (HAVOC's adjacent arms)
            // Synergy: Per 3, +2% Crit Multiplier
            // Expected: 3 / 3 = 1 increment = +2% (adjacent nodes ignored)
            Map<String, SkillNode> nodes = new HashMap<>();

            nodes.put("havoc_1", createNode("havoc_1", "HAVOC"));
            nodes.put("havoc_2", createNode("havoc_2", "HAVOC"));

            // Fill adjacent regions with many nodes
            for (int i = 1; i <= 7; i++) {
                nodes.put("fire_" + i, createNode("fire_" + i, "FIRE"));
            }
            for (int i = 1; i <= 7; i++) {
                nodes.put("void_" + i, createNode("void_" + i, "VOID"));
            }
            for (int i = 1; i <= 6; i++) {
                nodes.put("lightning_" + i, createNode("lightning_" + i, "LIGHTNING"));
            }

            SynergyConfig synergy = createBranchSynergy(3, "CRITICAL_MULTIPLIER", 2.0, "FLAT", 20.0);
            nodes.put("havoc_synergy", createSynergyNode("havoc_synergy", "HAVOC", synergy));

            setupConfig(nodes);

            Set<String> allocated = new HashSet<>(nodes.keySet());
            List<StatModifier> bonuses = calculator.calculateSynergyBonuses(allocated);

            assertEquals(1, bonuses.size());
            // Only 3 HAVOC nodes (2 basic + 1 synergy), not 23 total
            assertEquals(2.0f, bonuses.get(0).getValue(), 0.001f); // 3/3 = 1 * 2.0
        }

        @Test
        @DisplayName("Should apply cap correctly")
        void shouldApplyCapCorrectly() {
            // Setup: 30 HAVOC nodes
            // Synergy: Per 3, +2% Crit Multiplier, cap 10%
            // Expected: 30/3 = 10 * 2.0 = 20%, but capped at 10%
            Map<String, SkillNode> nodes = new HashMap<>();

            for (int i = 1; i <= 29; i++) {
                nodes.put("havoc_" + i, createNode("havoc_" + i, "HAVOC"));
            }

            SynergyConfig synergy = createBranchSynergy(3, "CRITICAL_MULTIPLIER", 2.0, "FLAT", 10.0);
            nodes.put("havoc_synergy", createSynergyNode("havoc_synergy", "HAVOC", synergy));

            setupConfig(nodes);

            Set<String> allocated = new HashSet<>(nodes.keySet());
            List<StatModifier> bonuses = calculator.calculateSynergyBonuses(allocated);

            assertEquals(1, bonuses.size());
            assertEquals(10.0f, bonuses.get(0).getValue(), 0.001f); // Capped at 10
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // EDGE CASES
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should return empty list for empty allocation")
        void shouldReturnEmptyForEmptyAllocation() {
            List<StatModifier> bonuses = calculator.calculateSynergyBonuses(Collections.emptySet());
            assertTrue(bonuses.isEmpty());
        }

        @Test
        @DisplayName("Should handle nodes without synergy")
        void shouldHandleNodesWithoutSynergy() {
            Map<String, SkillNode> nodes = new HashMap<>();
            nodes.put("fire_1", createNode("fire_1", "FIRE"));
            nodes.put("fire_2", createNode("fire_2", "FIRE"));
            setupConfig(nodes);

            Set<String> allocated = new HashSet<>(nodes.keySet());
            List<StatModifier> bonuses = calculator.calculateSynergyBonuses(allocated);

            assertTrue(bonuses.isEmpty());
        }

        @Test
        @DisplayName("Should handle synergy node with null bonus")
        void shouldHandleSynergyWithNullBonus() {
            Map<String, SkillNode> nodes = new HashMap<>();

            SynergyConfig synergy = new SynergyConfig();
            synergy.setType(SynergyType.ELEMENTAL_COUNT);
            synergy.setElement("FIRE");
            synergy.setPerCount(1);
            synergy.setBonus(null); // No bonus defined

            nodes.put("fire_synergy", createSynergyNode("fire_synergy", "FIRE", synergy));

            setupConfig(nodes);

            Set<String> allocated = new HashSet<>(nodes.keySet());
            List<StatModifier> bonuses = calculator.calculateSynergyBonuses(allocated);

            assertTrue(bonuses.isEmpty());
        }

        @Test
        @DisplayName("Should handle multiple synergy nodes")
        void shouldHandleMultipleSynergyNodes() {
            Map<String, SkillNode> nodes = new HashMap<>();

            // 6 fire nodes
            for (int i = 1; i <= 6; i++) {
                nodes.put("fire_" + i, createNode("fire_" + i, "FIRE"));
            }

            // Synergy 1: Per 3 FIRE, +3% fire damage
            SynergyConfig synergy1 = createElementalSynergy("FIRE", 3, "FIRE_DAMAGE_PERCENT", 3.0, 30.0);
            nodes.put("fire_synergy_1", createSynergyNode("fire_synergy_1", "FIRE", synergy1));

            // Synergy 2: Per 2 FIRE, +5% attack speed
            SynergyConfig synergy2 = new SynergyConfig();
            synergy2.setType(SynergyType.ELEMENTAL_COUNT);
            synergy2.setElement("FIRE");
            synergy2.setPerCount(2);

            SynergyConfig.SynergyBonus bonus2 = new SynergyConfig.SynergyBonus();
            bonus2.setStat("ATTACK_SPEED_PERCENT");
            bonus2.setValue(5.0);
            bonus2.setModifierType("PERCENT");
            synergy2.setBonus(bonus2);

            nodes.put("fire_synergy_2", createSynergyNode("fire_synergy_2", "FIRE", synergy2));

            setupConfig(nodes);

            Set<String> allocated = new HashSet<>(nodes.keySet());
            List<StatModifier> bonuses = calculator.calculateSynergyBonuses(allocated);

            assertEquals(2, bonuses.size());

            // Find each bonus
            StatModifier fireDmgBonus = bonuses.stream()
                .filter(b -> b.getStat() == StatType.FIRE_DAMAGE_PERCENT)
                .findFirst()
                .orElse(null);
            StatModifier atkSpdBonus = bonuses.stream()
                .filter(b -> b.getStat() == StatType.ATTACK_SPEED_PERCENT)
                .findFirst()
                .orElse(null);

            assertNotNull(fireDmgBonus);
            assertNotNull(atkSpdBonus);
            assertEquals(6.0f, fireDmgBonus.getValue(), 0.001f); // 8 nodes / 3 = 2 * 3 = 6
            assertEquals(20.0f, atkSpdBonus.getValue(), 0.001f); // 8 nodes / 2 = 4 * 5 = 20
        }
    }
}
