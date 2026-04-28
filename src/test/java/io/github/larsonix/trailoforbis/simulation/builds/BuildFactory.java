package io.github.larsonix.trailoforbis.simulation.builds;

import io.github.larsonix.trailoforbis.config.RPGConfig;
import io.github.larsonix.trailoforbis.database.models.PlayerData;
import io.github.larsonix.trailoforbis.gear.stats.GearStatCalculator.GearBonuses;
import io.github.larsonix.trailoforbis.simulation.gear.VirtualGearFactory;
import io.github.larsonix.trailoforbis.skilltree.SkillTreeRegion;
import io.github.larsonix.trailoforbis.skilltree.config.SkillNode;
import io.github.larsonix.trailoforbis.skilltree.config.SkillTreeConfig;
import io.github.larsonix.trailoforbis.skilltree.model.SkillTreeData;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * Creates virtual player builds at any level for simulation.
 *
 * <p>Generates PlayerData (element attributes), SkillTreeData (allocated nodes),
 * and GearBonuses (equipment stats) for a given BuildArchetype at a given level.
 */
public final class BuildFactory {

    private final SkillTreeConfig skillTreeConfig;
    private final VirtualGearFactory gearFactory;

    /** Cached node lists per region, sorted by tier for allocation order. */
    private final Map<SkillTreeRegion, List<String>> nodesByRegion;

    public BuildFactory(@Nonnull SkillTreeConfig skillTreeConfig,
                        @Nonnull VirtualGearFactory gearFactory) {
        this.skillTreeConfig = Objects.requireNonNull(skillTreeConfig);
        this.gearFactory = Objects.requireNonNull(gearFactory);
        this.nodesByRegion = buildRegionNodeMap();
    }

    /**
     * Creates a complete virtual build at the given level.
     *
     * @param archetype The build archetype defining element distribution and skill regions
     * @param level     Player level (determines points available and gear level)
     * @return Complete build ready for StatPipeline.compute()
     */
    @Nonnull
    public VirtualBuild create(@Nonnull BuildArchetype archetype, int level) {
        PlayerData playerData = createPlayerData(archetype, level);
        SkillTreeData skillTreeData = createSkillTreeData(archetype, level);
        GearBonuses gearBonuses = gearFactory.generateForLevel(level);
        return new VirtualBuild(archetype, level, playerData, skillTreeData, gearBonuses);
    }

    /**
     * Creates a build without gear (for isolating attribute + skill tree contribution).
     */
    @Nonnull
    public VirtualBuild createWithoutGear(@Nonnull BuildArchetype archetype, int level) {
        PlayerData playerData = createPlayerData(archetype, level);
        SkillTreeData skillTreeData = createSkillTreeData(archetype, level);
        return new VirtualBuild(archetype, level, playerData, skillTreeData, GearBonuses.EMPTY);
    }

    // =========================================================================
    // Player Data — distribute level points across elements
    // =========================================================================

    private PlayerData createPlayerData(BuildArchetype archetype, int level) {
        int fire = archetype.getElementPoints("fire", level);
        int water = archetype.getElementPoints("water", level);
        int lightning = archetype.getElementPoints("lightning", level);
        int earth = archetype.getElementPoints("earth", level);
        int wind = archetype.getElementPoints("wind", level);
        int voidAttr = archetype.getElementPoints("void", level);

        // Distribute any remainder to the first element with a non-zero ratio
        int allocated = fire + water + lightning + earth + wind + voidAttr;
        int remainder = level - allocated;
        if (remainder > 0) {
            // Give remainder to first priority element
            String first = archetype.getElementRatios().keySet().iterator().next();
            switch (first) {
                case "fire" -> fire += remainder;
                case "water" -> water += remainder;
                case "lightning" -> lightning += remainder;
                case "earth" -> earth += remainder;
                case "wind" -> wind += remainder;
                case "void" -> voidAttr += remainder;
            }
        }

        return PlayerData.builder()
                .uuid(UUID.randomUUID())
                .username("SimPlayer")
                .fire(fire)
                .water(water)
                .lightning(lightning)
                .earth(earth)
                .wind(wind)
                .voidAttr(voidAttr)
                .build();
    }

    // =========================================================================
    // Skill Tree — allocate nodes along primary regions
    // =========================================================================

    private SkillTreeData createSkillTreeData(BuildArchetype archetype, int level) {
        Set<String> allocated = new LinkedHashSet<>();
        allocated.add("origin"); // Always allocated, free

        int pointsRemaining = level; // 1 skill point per level

        // Target regions for this archetype
        Set<SkillTreeRegion> targetRegions = Set.of(archetype.getPrimaryRegions());

        // BFS from origin, only allocating reachable + region-appropriate nodes.
        // Matches SkillTreeManager.canAllocate() connectivity check:
        // "node.getConnections().stream().anyMatch(data.getAllocatedNodes()::contains)"
        boolean changed = true;
        while (changed && pointsRemaining > 0) {
            changed = false;

            // Find all nodes adjacent to currently allocated nodes
            List<String> candidates = new ArrayList<>();
            for (String allocatedId : allocated) {
                SkillNode allocatedNode = skillTreeConfig.getNode(allocatedId);
                if (allocatedNode == null) continue;
                for (String connId : allocatedNode.getConnections()) {
                    if (!allocated.contains(connId)) {
                        candidates.add(connId);
                    }
                }
            }

            // Sort candidates: prefer target regions, then by tier (lower = closer to center)
            candidates.sort((a, b) -> {
                SkillNode na = skillTreeConfig.getNode(a);
                SkillNode nb = skillTreeConfig.getNode(b);
                if (na == null || nb == null) return 0;

                // Prefer nodes in target regions
                boolean aTarget = targetRegions.contains(na.getSkillTreeRegion());
                boolean bTarget = targetRegions.contains(nb.getSkillTreeRegion());
                if (aTarget != bTarget) return aTarget ? -1 : 1;

                // Within same priority, prefer lower tier (closer to origin)
                return Integer.compare(na.getTier(), nb.getTier());
            });

            // Deduplicate
            candidates = new ArrayList<>(new LinkedHashSet<>(candidates));

            for (String candidateId : candidates) {
                if (pointsRemaining <= 0) break;
                SkillNode node = skillTreeConfig.getNode(candidateId);
                if (node == null) continue;

                int cost = node.getCost();
                if (cost > pointsRemaining) continue;

                // Only allocate nodes in target regions (or CORE for pathing)
                SkillTreeRegion region = node.getSkillTreeRegion();
                if (region != SkillTreeRegion.CORE && !targetRegions.contains(region)) continue;

                allocated.add(candidateId);
                pointsRemaining -= cost;
                changed = true;
            }
        }

        return SkillTreeData.builder()
                .uuid(UUID.randomUUID())
                .allocatedNodes(allocated)
                .skillPoints(pointsRemaining)
                .totalPointsEarned(level)
                .build();
    }

    // =========================================================================
    // Region Node Map — sort nodes by tier for ordered allocation
    // =========================================================================

    private Map<SkillTreeRegion, List<String>> buildRegionNodeMap() {
        Map<SkillTreeRegion, List<String>> map = new EnumMap<>(SkillTreeRegion.class);
        Map<String, SkillNode> allNodes = skillTreeConfig.getNodes();

        for (Map.Entry<String, SkillNode> entry : allNodes.entrySet()) {
            String nodeId = entry.getKey();
            SkillNode node = entry.getValue();
            if ("origin".equals(nodeId)) continue;

            SkillTreeRegion region = node.getSkillTreeRegion();
            if (region == null) continue;

            map.computeIfAbsent(region, k -> new ArrayList<>()).add(nodeId);
        }

        // Sort each region's nodes by tier (lower tier = closer to center = allocate first)
        for (List<String> nodes : map.values()) {
            nodes.sort(Comparator.comparingInt(id -> {
                SkillNode n = skillTreeConfig.getNode(id);
                return n != null ? n.getTier() : Integer.MAX_VALUE;
            }));
        }

        return map;
    }

    // =========================================================================
    // Virtual Build record
    // =========================================================================

    /**
     * A complete virtual player build at a specific level.
     */
    public record VirtualBuild(
            BuildArchetype archetype,
            int level,
            PlayerData playerData,
            SkillTreeData skillTreeData,
            GearBonuses gearBonuses
    ) {}
}
