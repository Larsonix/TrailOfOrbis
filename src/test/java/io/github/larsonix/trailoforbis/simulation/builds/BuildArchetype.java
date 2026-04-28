package io.github.larsonix.trailoforbis.simulation.builds;

import io.github.larsonix.trailoforbis.skilltree.SkillTreeRegion;
import io.github.larsonix.trailoforbis.skilltree.config.SkillTreeConfig;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * Generates ALL meaningful build combinations systematically:
 * <ul>
 *   <li>6 single-element builds (pure FIRE, WATER, etc.)</li>
 *   <li>15 double-element builds (every pair of elements)</li>
 *   <li>18 element+octant builds (each octant with its connected elements)</li>
 * </ul>
 *
 * <p>Total: 39 builds covering every reachable single and double combination.
 * This ensures no broken combo is missed in balance testing.
 */
public final class BuildArchetype {

    private static final String[] ELEMENTS = {"FIRE", "WATER", "LIGHTNING", "EARTH", "WIND", "VOID"};

    private final String name;
    private final Map<String, Double> elementRatios;
    private final SkillTreeRegion[] primaryRegions;

    private BuildArchetype(String name, Map<String, Double> elementRatios, SkillTreeRegion[] primaryRegions) {
        this.name = name;
        this.elementRatios = elementRatios;
        this.primaryRegions = primaryRegions;
    }

    @Nonnull
    public String name() {
        return name;
    }

    @Nonnull
    public String getDisplayName() {
        return name;
    }

    @Nonnull
    public Map<String, Double> getElementRatios() {
        return elementRatios;
    }

    @Nonnull
    public SkillTreeRegion[] getPrimaryRegions() {
        return primaryRegions;
    }

    public int getElementPoints(String element, int totalPoints) {
        return (int) Math.floor(totalPoints * elementRatios.getOrDefault(element, 0.0));
    }

    /**
     * Generates all 39 build archetypes by scanning the skill tree for connectivity.
     *
     * @param skillTreeConfig The loaded skill tree (needed to find octant connections)
     * @return All single-element, double-element, and element+octant builds
     */
    public static List<BuildArchetype> generateAll(@Nonnull SkillTreeConfig skillTreeConfig) {
        // Build region connectivity map from actual skill tree connections
        Map<String, Set<String>> regionConnections = buildRegionConnections(skillTreeConfig);

        List<BuildArchetype> builds = new ArrayList<>();

        // 1. Single element builds (6)
        for (String elem : ELEMENTS) {
            SkillTreeRegion region = parseRegion(elem);
            if (region == null) continue;
            builds.add(new BuildArchetype(
                    "PURE_" + elem,
                    Map.of(elem.toLowerCase(), 1.0),
                    new SkillTreeRegion[]{region}
            ));
        }

        // 2. Double element builds (15)
        for (int i = 0; i < ELEMENTS.length; i++) {
            for (int j = i + 1; j < ELEMENTS.length; j++) {
                String a = ELEMENTS[i], b = ELEMENTS[j];
                SkillTreeRegion ra = parseRegion(a), rb = parseRegion(b);
                if (ra == null || rb == null) continue;
                builds.add(new BuildArchetype(
                        a + "_" + b,
                        Map.of(a.toLowerCase(), 0.5, b.toLowerCase(), 0.5),
                        new SkillTreeRegion[]{ra, rb}
                ));
            }
        }

        // 3. Element + Octant builds (only where connected in the tree)
        String[] octants = {"STRIKER", "SENTINEL", "JUGGERNAUT", "HAVOC", "TEMPEST", "WARDEN", "WARLOCK", "LICH"};
        Set<String> elementSet = Set.of(ELEMENTS);

        for (String octant : octants) {
            SkillTreeRegion octRegion = parseRegion(octant);
            if (octRegion == null) continue;

            Set<String> connectedElements = regionConnections.getOrDefault(octant, Set.of());
            for (String elem : connectedElements) {
                if (!elementSet.contains(elem)) continue;
                SkillTreeRegion elemRegion = parseRegion(elem);
                if (elemRegion == null) continue;

                builds.add(new BuildArchetype(
                        elem + "_" + octant,
                        Map.of(elem.toLowerCase(), 1.0),
                        new SkillTreeRegion[]{elemRegion, octRegion}
                ));
            }
        }

        return builds;
    }

    /**
     * Scans the skill tree to find which regions connect to each other via bridge nodes.
     */
    private static Map<String, Set<String>> buildRegionConnections(SkillTreeConfig config) {
        Map<String, Set<String>> connections = new HashMap<>();
        Map<String, ? extends io.github.larsonix.trailoforbis.skilltree.config.SkillNode> nodes = config.getNodes();

        for (var entry : nodes.entrySet()) {
            var node = entry.getValue();
            String region = node.getSkillTreeRegion() != null ? node.getSkillTreeRegion().name() : "UNKNOWN";

            for (String connId : node.getConnections()) {
                var connNode = nodes.get(connId);
                if (connNode == null) continue;
                String connRegion = connNode.getSkillTreeRegion() != null ? connNode.getSkillTreeRegion().name() : "UNKNOWN";

                if (!connRegion.equals(region)) {
                    connections.computeIfAbsent(region, k -> new HashSet<>()).add(connRegion);
                }
            }
        }
        return connections;
    }

    private static SkillTreeRegion parseRegion(String name) {
        try {
            return SkillTreeRegion.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
