package io.github.larsonix.trailoforbis.skilltree.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SkillTreeData}.
 *
 * <p>Tests immutable builder pattern, node allocation, respec,
 * skill points management, and persistence state.
 */
@DisplayName("SkillTreeData")
class SkillTreeDataTest {

    private UUID playerId;
    private SkillTreeData baseData;

    @BeforeEach
    void setUp() {
        playerId = UUID.randomUUID();
        baseData = SkillTreeData.builder()
            .uuid(playerId)
            .skillPoints(10)
            .totalPointsEarned(10)
            .build();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Builder Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("Creates data with default values")
        void createsDataWithDefaults() {
            SkillTreeData data = SkillTreeData.builder()
                .uuid(playerId)
                .build();

            assertEquals(playerId, data.getUuid());
            assertEquals(0, data.getSkillPoints());
            assertEquals(0, data.getTotalPointsEarned());
            assertEquals(0, data.getRespecs());
            assertTrue(data.getAllocatedNodes().isEmpty());
            assertNotNull(data.getLastModified());
        }

        @Test
        @DisplayName("Creates data with custom values")
        void createsDataWithCustomValues() {
            Instant now = Instant.now();
            Set<String> nodes = Set.of("node1", "node2");

            SkillTreeData data = SkillTreeData.builder()
                .uuid(playerId)
                .skillPoints(15)
                .totalPointsEarned(20)
                .respecs(2)
                .allocatedNodes(nodes)
                .lastModified(now)
                .build();

            assertEquals(playerId, data.getUuid());
            assertEquals(15, data.getSkillPoints());
            assertEquals(20, data.getTotalPointsEarned());
            assertEquals(2, data.getRespecs());
            assertEquals(2, data.getAllocatedNodes().size());
            assertEquals(now, data.getLastModified());
        }

        @Test
        @DisplayName("toBuilder preserves all values")
        void toBuilder_preservesAllValues() {
            SkillTreeData original = SkillTreeData.builder()
                .uuid(playerId)
                .skillPoints(15)
                .totalPointsEarned(20)
                .respecs(3)
                .allocatedNodes(Set.of("a", "b", "c"))
                .build();

            SkillTreeData copy = original.toBuilder().build();

            assertEquals(original.getUuid(), copy.getUuid());
            assertEquals(original.getSkillPoints(), copy.getSkillPoints());
            assertEquals(original.getTotalPointsEarned(), copy.getTotalPointsEarned());
            assertEquals(original.getRespecs(), copy.getRespecs());
            assertEquals(original.getAllocatedNodes(), copy.getAllocatedNodes());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Immutability Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Immutability")
    class ImmutabilityTests {

        @Test
        @DisplayName("getAllocatedNodes returns immutable set")
        void getAllocatedNodes_returnsImmutableSet() {
            SkillTreeData data = baseData.withAllocatedNode("node1", 1);

            assertThrows(UnsupportedOperationException.class, () -> {
                data.getAllocatedNodes().add("hacked_node");
            });
        }

        @Test
        @DisplayName("withAllocatedNode returns new instance")
        void withAllocatedNode_returnsNewInstance() {
            SkillTreeData modified = baseData.withAllocatedNode("node1", 2);

            assertNotSame(baseData, modified);
            assertFalse(baseData.getAllocatedNodes().contains("node1"));
            assertTrue(modified.getAllocatedNodes().contains("node1"));
        }

        @Test
        @DisplayName("withRespec returns new instance")
        void withRespec_returnsNewInstance() {
            SkillTreeData allocated = baseData.withAllocatedNode("node1", 2);
            SkillTreeData respeced = allocated.withRespec();

            assertNotSame(allocated, respeced);
            // Origin is preserved after respec (it's the graph root with no cost/modifiers)
            assertEquals(Set.of("origin"), respeced.getAllocatedNodes());
        }

        @Test
        @DisplayName("Original data unchanged after mutations")
        void originalData_unchangedAfterMutations() {
            int originalPoints = baseData.getSkillPoints();
            Set<String> originalNodes = Set.copyOf(baseData.getAllocatedNodes());

            // Perform mutations
            baseData.withAllocatedNode("x", 1);
            baseData.withRespec();
            baseData.withAddedSkillPoints(5);

            // Original should be unchanged
            assertEquals(originalPoints, baseData.getSkillPoints());
            assertEquals(originalNodes, baseData.getAllocatedNodes());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Node Allocation Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Node Allocation")
    class NodeAllocationTests {

        @Test
        @DisplayName("withAllocatedNode adds node to set")
        void withAllocatedNode_addsNodeToSet() {
            SkillTreeData modified = baseData.withAllocatedNode("str_1", 1);

            assertTrue(modified.getAllocatedNodes().contains("str_1"));
            assertEquals(1, modified.getAllocatedNodes().size());
        }

        @Test
        @DisplayName("withAllocatedNode deducts skill points")
        void withAllocatedNode_deductsSkillPoints() {
            SkillTreeData modified = baseData.withAllocatedNode("str_1", 2);

            assertEquals(8, modified.getSkillPoints()); // 10 - 2
        }

        @Test
        @DisplayName("Multiple allocations accumulate")
        void multipleAllocations_accumulate() {
            SkillTreeData step1 = baseData.withAllocatedNode("node1", 1);
            SkillTreeData step2 = step1.withAllocatedNode("node2", 2);
            SkillTreeData step3 = step2.withAllocatedNode("node3", 3);

            assertEquals(3, step3.getAllocatedNodes().size());
            assertTrue(step3.getAllocatedNodes().containsAll(Set.of("node1", "node2", "node3")));
            assertEquals(4, step3.getSkillPoints()); // 10 - 1 - 2 - 3
        }

        @Test
        @DisplayName("withAllocatedNode updates lastModified")
        void withAllocatedNode_updatesLastModified() throws InterruptedException {
            Instant before = baseData.getLastModified();
            Thread.sleep(10); // Ensure time passes

            SkillTreeData modified = baseData.withAllocatedNode("node1", 1);

            assertTrue(modified.getLastModified().isAfter(before) ||
                       modified.getLastModified().equals(before));
        }

        @Test
        @DisplayName("Duplicate node allocation still works")
        void duplicateNodeAllocation_stillWorks() {
            SkillTreeData step1 = baseData.withAllocatedNode("node1", 1);
            SkillTreeData step2 = step1.withAllocatedNode("node1", 1); // Same node again

            // Set doesn't duplicate
            assertEquals(1, step2.getAllocatedNodes().size());
            // But points are still deducted (caller responsible for checking)
            assertEquals(8, step2.getSkillPoints());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Respec Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Respec")
    class RespecTests {

        @Test
        @DisplayName("withRespec clears all allocated nodes except origin")
        void withRespec_clearsAllocatedNodes() {
            SkillTreeData allocated = baseData
                .withAllocatedNode("origin", 0)  // Origin is always allocated
                .withAllocatedNode("node1", 1)
                .withAllocatedNode("node2", 2)
                .withAllocatedNode("node3", 3);

            SkillTreeData respeced = allocated.withRespec();

            // Origin is preserved (it's the graph root with no cost/modifiers)
            assertEquals(Set.of("origin"), respeced.getAllocatedNodes());
        }

        @Test
        @DisplayName("withRespec restores all skill points")
        void withRespec_restoresAllSkillPoints() {
            SkillTreeData allocated = baseData
                .withAllocatedNode("node1", 1)
                .withAllocatedNode("node2", 2);

            assertEquals(7, allocated.getSkillPoints());

            SkillTreeData respeced = allocated.withRespec();

            assertEquals(10, respeced.getSkillPoints()); // totalPointsEarned
        }

        @Test
        @DisplayName("withRespec increments respec counter")
        void withRespec_incrementsRespecCounter() {
            assertEquals(0, baseData.getRespecs());

            SkillTreeData respec1 = baseData.withRespec();
            assertEquals(1, respec1.getRespecs());

            SkillTreeData respec2 = respec1.withRespec();
            assertEquals(2, respec2.getRespecs());
        }

        @Test
        @DisplayName("withRespec updates lastModified")
        void withRespec_updatesLastModified() throws InterruptedException {
            Instant before = baseData.getLastModified();
            Thread.sleep(10);

            SkillTreeData respeced = baseData.withRespec();

            assertTrue(respeced.getLastModified().isAfter(before) ||
                       respeced.getLastModified().equals(before));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Points Management Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Points Management")
    class PointsManagementTests {

        @Test
        @DisplayName("withAddedSkillPoints increases skill points")
        void withAddedSkillPoints_increasesSkillPoints() {
            SkillTreeData modified = baseData.withAddedSkillPoints(5);

            assertEquals(15, modified.getSkillPoints());
            assertEquals(15, modified.getTotalPointsEarned());
        }

        @Test
        @DisplayName("withAddedSkillPoints accumulates correctly")
        void withAddedSkillPoints_accumulatesCorrectly() {
            SkillTreeData step1 = baseData.withAddedSkillPoints(3);
            SkillTreeData step2 = step1.withAddedSkillPoints(7);

            assertEquals(20, step2.getSkillPoints());
            assertEquals(20, step2.getTotalPointsEarned());
        }

        @Test
        @DisplayName("Points added after allocation are available")
        void pointsAdded_afterAllocation_areAvailable() {
            SkillTreeData allocated = baseData.withAllocatedNode("node1", 5);
            assertEquals(5, allocated.getSkillPoints());

            SkillTreeData withBonus = allocated.withAddedSkillPoints(3);
            assertEquals(8, withBonus.getSkillPoints());
        }

        @Test
        @DisplayName("Zero points added is a no-op for skill points")
        void zeroPointsAdded_isNoOpForSkillPoints() {
            SkillTreeData modified = baseData.withAddedSkillPoints(0);

            assertEquals(baseData.getSkillPoints(), modified.getSkillPoints());
        }

        @Test
        @DisplayName("withSkillPoints sets exact value")
        void withSkillPoints_setsExactValue() {
            SkillTreeData modified = baseData.withSkillPoints(99);

            assertEquals(99, modified.getSkillPoints());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Node Removal Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Node Removal")
    class NodeRemovalTests {

        @Test
        @DisplayName("withRemovedNode removes from set")
        void withRemovedNode_removesFromSet() {
            SkillTreeData allocated = baseData.withAllocatedNode("node1", 1);
            assertTrue(allocated.getAllocatedNodes().contains("node1"));

            SkillTreeData removed = allocated.withRemovedNode("node1");
            assertFalse(removed.getAllocatedNodes().contains("node1"));
        }

        @Test
        @DisplayName("withRemovedNode does not refund points")
        void withRemovedNode_doesNotRefundPoints() {
            SkillTreeData allocated = baseData.withAllocatedNode("node1", 3);
            assertEquals(7, allocated.getSkillPoints());

            SkillTreeData removed = allocated.withRemovedNode("node1");
            assertEquals(7, removed.getSkillPoints()); // No refund
        }

        @Test
        @DisplayName("Removing non-existent node is safe")
        void removingNonExistentNode_isSafe() {
            SkillTreeData removed = baseData.withRemovedNode("non_existent");

            assertNotNull(removed);
            assertEquals(baseData.getSkillPoints(), removed.getSkillPoints());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Edge Cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Allocating with more cost than available points")
        void allocating_moreThanAvailable() {
            SkillTreeData modified = baseData.withAllocatedNode("expensive", 100);

            // Points go negative (caller should prevent this)
            assertEquals(-90, modified.getSkillPoints());
            assertTrue(modified.getAllocatedNodes().contains("expensive"));
        }

        @Test
        @DisplayName("Fresh player data")
        void freshPlayerData() {
            SkillTreeData fresh = SkillTreeData.builder()
                .uuid(playerId)
                .skillPoints(0)
                .totalPointsEarned(0)
                .build();

            assertEquals(0, fresh.getSkillPoints());
            assertEquals(0, fresh.getAllocatedNodes().size());
        }

        @Test
        @DisplayName("Data with negative initial points")
        void dataWithNegativeInitialPoints() {
            SkillTreeData data = SkillTreeData.builder()
                .uuid(playerId)
                .skillPoints(-5)
                .build();

            assertEquals(-5, data.getSkillPoints());
        }

        @Test
        @DisplayName("Respec from clean state preserves origin")
        void respecFromCleanState() {
            SkillTreeData respeced = baseData.withRespec();

            // Origin is always preserved after respec (graph root with no cost/modifiers)
            assertEquals(Set.of("origin"), respeced.getAllocatedNodes());
            assertEquals(10, respeced.getSkillPoints());
            assertEquals(1, respeced.getRespecs());
        }

        @Test
        @DisplayName("Large number of nodes")
        void largeNumberOfNodes() {
            SkillTreeData data = baseData.withAddedSkillPoints(1000);

            for (int i = 0; i < 100; i++) {
                data = data.withAllocatedNode("node_" + i, 1);
            }

            assertEquals(100, data.getAllocatedNodes().size());
            assertEquals(910, data.getSkillPoints()); // 1010 - 100
        }

        @Test
        @DisplayName("Null uuid throws exception")
        void nullUuid_throwsException() {
            assertThrows(NullPointerException.class, () -> {
                SkillTreeData.builder().build();
            });
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Equality Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Equality")
    class EqualityTests {

        @Test
        @DisplayName("Same UUID creates equal instances")
        void sameUuid_createsEqualInstances() {
            SkillTreeData data1 = SkillTreeData.builder()
                .uuid(playerId)
                .skillPoints(10)
                .build();

            SkillTreeData data2 = SkillTreeData.builder()
                .uuid(playerId)
                .skillPoints(99) // Different points but same UUID
                .build();

            // Equality is based on UUID only
            assertEquals(data1, data2);
            assertEquals(data1.hashCode(), data2.hashCode());
        }

        @Test
        @DisplayName("Different UUID creates unequal instances")
        void differentUuid_createsUnequalInstances() {
            SkillTreeData data1 = SkillTreeData.builder()
                .uuid(playerId)
                .build();

            SkillTreeData data2 = SkillTreeData.builder()
                .uuid(UUID.randomUUID())
                .build();

            assertNotEquals(data1, data2);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // toString Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("toString")
    class ToStringTests {

        @Test
        @DisplayName("toString contains relevant info")
        void toString_containsRelevantInfo() {
            SkillTreeData data = baseData.withAllocatedNode("node1", 1);

            String str = data.toString();

            assertTrue(str.contains("SkillTreeData"));
            assertTrue(str.contains(playerId.toString().substring(0, 8))); // Contains UUID prefix
            assertTrue(str.contains("nodes=1"));
            assertTrue(str.contains("points=9"));
        }
    }
}
