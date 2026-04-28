package io.github.larsonix.trailoforbis.lootfilter.model;

import io.github.larsonix.trailoforbis.gear.model.EquipmentType;
import io.github.larsonix.trailoforbis.gear.model.GearData;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.stream.Collectors;

/**
 * An ordered filter rule with AND logic between conditions.
 *
 * <p>If all conditions match (or there are no conditions), the rule matches.
 * Disabled rules are skipped during evaluation (they don't match anything).
 *
 * @param name Human-readable name (e.g., "Good leather")
 * @param enabled Whether this rule is active during evaluation
 * @param action What to do when this rule matches (ALLOW or BLOCK)
 * @param conditions AND'd conditions — all must match for the rule to match
 */
public record FilterRule(
        @Nonnull String name,
        boolean enabled,
        @Nonnull FilterAction action,
        @Nonnull List<FilterCondition> conditions
) {
    public FilterRule {
        if (name == null || name.isBlank()) name = "New Rule";
        if (action == null) action = FilterAction.ALLOW;
        conditions = conditions != null ? List.copyOf(conditions) : List.of();
    }

    /**
     * Returns true if this rule matches the given item.
     * Disabled rules never match.
     */
    public boolean matches(@Nonnull GearData gearData, @Nonnull EquipmentType equipmentType) {
        if (!enabled) return false;
        return conditions.stream().allMatch(c -> c.matches(gearData, equipmentType));
    }

    /**
     * One-line summary for list views: "conditions → ACTION".
     */
    public String describeSummary() {
        String condText = conditions.isEmpty()
                ? "Everything"
                : conditions.stream().map(FilterCondition::describe)
                    .collect(Collectors.joining(", "));
        return condText + " > " + action.name();
    }

    /**
     * Per-condition pass/fail breakdown for /lf test output.
     */
    public List<String> describeMatch(@Nonnull GearData gearData, @Nonnull EquipmentType equipmentType) {
        return conditions.stream().map(c -> {
            boolean pass = c.matches(gearData, equipmentType);
            return (pass ? "[x] " : "[!] ") + c.describe();
        }).toList();
    }

    /**
     * Creates a copy with a different enabled state.
     */
    public FilterRule withEnabled(boolean newEnabled) {
        return new FilterRule(name, newEnabled, action, conditions);
    }
}
