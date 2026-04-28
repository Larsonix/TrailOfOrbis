package io.github.larsonix.trailoforbis.lootfilter.model;

import io.github.larsonix.trailoforbis.gear.model.EquipmentType;
import io.github.larsonix.trailoforbis.gear.model.GearData;

import javax.annotation.Nonnull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * An immutable filter profile containing ordered rules with first-match-wins evaluation.
 *
 * <p>Use the {@link Builder} for construction and {@code with*()} methods for updates.
 * All mutation methods return new instances (immutable).
 */
public final class FilterProfile {

    private final String id;
    private final String name;
    private final FilterAction defaultAction;
    private final List<FilterRule> rules;
    private final Instant createdAt;
    private final Instant lastModified;

    private FilterProfile(String id, String name, FilterAction defaultAction,
                          List<FilterRule> rules, Instant createdAt, Instant lastModified) {
        this.id = id;
        this.name = name;
        this.defaultAction = defaultAction;
        this.rules = List.copyOf(rules);
        this.createdAt = createdAt;
        this.lastModified = lastModified;
    }

    // ═══════════════════════════════════════════════════════════════════
    // EVALUATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Evaluates the item against rules in order. First matching rule wins.
     * Falls back to {@link #defaultAction} if no rule matches.
     */
    @Nonnull
    public FilterAction evaluate(@Nonnull GearData gearData, @Nonnull EquipmentType equipmentType) {
        for (FilterRule rule : rules) {
            if (rule.matches(gearData, equipmentType)) {
                return rule.action();
            }
        }
        return defaultAction;
    }

    /**
     * Full evaluation trace for /lf test — shows which rules were checked and why.
     */
    @Nonnull
    public EvaluationTrace evaluateWithTrace(@Nonnull GearData gearData, @Nonnull EquipmentType equipmentType) {
        List<RuleTrace> traces = new ArrayList<>();
        for (int i = 0; i < rules.size(); i++) {
            FilterRule rule = rules.get(i);
            if (!rule.enabled()) continue;
            List<String> details = rule.describeMatch(gearData, equipmentType);
            boolean matched = rule.matches(gearData, equipmentType);
            traces.add(new RuleTrace(i + 1, rule.name(), matched, rule.action(), details));
            if (matched) {
                return new EvaluationTrace(traces, rule.action(), i + 1);
            }
        }
        return new EvaluationTrace(traces, defaultAction, -1);
    }

    // ═══════════════════════════════════════════════════════════════════
    // IMMUTABLE UPDATE METHODS
    // ═══════════════════════════════════════════════════════════════════

    public FilterProfile withName(@Nonnull String newName) {
        return new FilterProfile(id, newName, defaultAction, rules, createdAt, Instant.now());
    }

    public FilterProfile withDefaultAction(@Nonnull FilterAction action) {
        return new FilterProfile(id, name, action, rules, createdAt, Instant.now());
    }

    public FilterProfile withRules(@Nonnull List<FilterRule> newRules) {
        return new FilterProfile(id, name, defaultAction, newRules, createdAt, Instant.now());
    }

    public FilterProfile withAddedRule(@Nonnull FilterRule rule) {
        List<FilterRule> newRules = new ArrayList<>(rules);
        newRules.add(rule);
        return new FilterProfile(id, name, defaultAction, newRules, createdAt, Instant.now());
    }

    public FilterProfile withRemovedRule(int index) {
        if (index < 0 || index >= rules.size()) throw new IndexOutOfBoundsException(index);
        List<FilterRule> newRules = new ArrayList<>(rules);
        newRules.remove(index);
        return new FilterProfile(id, name, defaultAction, newRules, createdAt, Instant.now());
    }

    public FilterProfile withMovedRule(int from, int to) {
        if (from < 0 || from >= rules.size()) throw new IndexOutOfBoundsException(from);
        if (to < 0 || to >= rules.size()) throw new IndexOutOfBoundsException(to);
        List<FilterRule> newRules = new ArrayList<>(rules);
        FilterRule rule = newRules.remove(from);
        newRules.add(to, rule);
        return new FilterProfile(id, name, defaultAction, newRules, createdAt, Instant.now());
    }

    public FilterProfile withUpdatedRule(int index, @Nonnull FilterRule rule) {
        if (index < 0 || index >= rules.size()) throw new IndexOutOfBoundsException(index);
        List<FilterRule> newRules = new ArrayList<>(rules);
        newRules.set(index, rule);
        return new FilterProfile(id, name, defaultAction, newRules, createdAt, Instant.now());
    }

    // ═══════════════════════════════════════════════════════════════════
    // GETTERS
    // ═══════════════════════════════════════════════════════════════════

    @Nonnull public String getId() { return id; }
    @Nonnull public String getName() { return name; }
    @Nonnull public FilterAction getDefaultAction() { return defaultAction; }
    @Nonnull public List<FilterRule> getRules() { return rules; }
    @Nonnull public Instant getCreatedAt() { return createdAt; }
    @Nonnull public Instant getLastModified() { return lastModified; }

    // ═══════════════════════════════════════════════════════════════════
    // TRACE RECORDS
    // ═══════════════════════════════════════════════════════════════════

    public record EvaluationTrace(
            @Nonnull List<RuleTrace> ruleTraces,
            @Nonnull FilterAction result,
            int matchedRuleNumber
    ) {
        public boolean matchedByRule() { return matchedRuleNumber > 0; }
    }

    public record RuleTrace(
            int ruleNumber,
            @Nonnull String ruleName,
            boolean matched,
            @Nonnull FilterAction action,
            @Nonnull List<String> conditionDetails
    ) {}

    // ═══════════════════════════════════════════════════════════════════
    // BUILDER
    // ═══════════════════════════════════════════════════════════════════

    public static Builder builder() { return new Builder(); }

    public Builder toBuilder() {
        return new Builder()
                .id(id)
                .name(name)
                .defaultAction(defaultAction)
                .rules(new ArrayList<>(rules))
                .createdAt(createdAt)
                .lastModified(lastModified);
    }

    public static final class Builder {
        private String id = UUID.randomUUID().toString();
        private String name = "New Filter";
        private FilterAction defaultAction = FilterAction.ALLOW;
        private List<FilterRule> rules = new ArrayList<>();
        private Instant createdAt = Instant.now();
        private Instant lastModified = Instant.now();

        public Builder id(@Nonnull String id) { this.id = id; return this; }
        public Builder name(@Nonnull String name) { this.name = name; return this; }
        public Builder defaultAction(@Nonnull FilterAction action) { this.defaultAction = action; return this; }
        public Builder rules(@Nonnull List<FilterRule> rules) { this.rules = rules; return this; }
        public Builder addRule(@Nonnull FilterRule rule) { this.rules.add(rule); return this; }
        public Builder createdAt(@Nonnull Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder lastModified(@Nonnull Instant lastModified) { this.lastModified = lastModified; return this; }

        @Nonnull
        public FilterProfile build() {
            return new FilterProfile(id, name, defaultAction, rules, createdAt, lastModified);
        }
    }
}
