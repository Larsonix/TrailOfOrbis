package io.github.larsonix.trailoforbis.lootfilter.config;

import io.github.larsonix.trailoforbis.lootfilter.model.FilterAction;
import io.github.larsonix.trailoforbis.lootfilter.model.FilterCondition;
import io.github.larsonix.trailoforbis.lootfilter.model.FilterProfile;
import io.github.larsonix.trailoforbis.lootfilter.model.FilterRule;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Configuration for the loot filter system, loaded from loot-filter.yml.
 *
 * <p>Uses SnakeYAML bean mapping — setters use snake_case matching YAML keys.
 */
public final class LootFilterConfig {

    // ═══════════════════════════════════════════════════════════════════
    // FIELDS (with defaults)
    // ═══════════════════════════════════════════════════════════════════

    private boolean enabled = true;
    private int maxProfilesPerPlayer = 10;
    private int maxRulesPerProfile = 50;
    private int maxConditionsPerRule = 50;
    private DefaultsConfig defaults = new DefaultsConfig();
    private FeedbackConfig feedback = new FeedbackConfig();
    private List<PresetConfig> presets = new ArrayList<>();
    private Map<String, String> categoryOverrides = new LinkedHashMap<>();

    // ═══════════════════════════════════════════════════════════════════
    // GETTERS
    // ═══════════════════════════════════════════════════════════════════

    public boolean isEnabled() { return enabled; }
    public int getMaxProfilesPerPlayer() { return maxProfilesPerPlayer; }
    public int getMaxRulesPerProfile() { return maxRulesPerProfile; }
    public int getMaxConditionsPerRule() { return maxConditionsPerRule; }
    public DefaultsConfig getDefaults() { return defaults; }
    public FeedbackConfig getFeedback() { return feedback; }
    public List<PresetConfig> getPresets() { return presets; }
    public Map<String, String> getCategoryOverrides() { return categoryOverrides; }

    // ═══════════════════════════════════════════════════════════════════
    // YAML SETTERS (snake_case)
    // ═══════════════════════════════════════════════════════════════════

    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setMax_profiles_per_player(int v) { this.maxProfilesPerPlayer = v; }
    public void setMax_rules_per_profile(int v) { this.maxRulesPerProfile = v; }
    public void setMax_conditions_per_rule(int v) { this.maxConditionsPerRule = v; }
    public void setDefaults(DefaultsConfig defaults) { this.defaults = defaults; }
    public void setFeedback(FeedbackConfig feedback) { this.feedback = feedback; }
    public void setPresets(List<PresetConfig> presets) { this.presets = presets; }
    public void setCategory_overrides(Map<String, String> overrides) { this.categoryOverrides = overrides; }

    // ═══════════════════════════════════════════════════════════════════
    // NESTED CONFIGS
    // ═══════════════════════════════════════════════════════════════════

    public static final class DefaultsConfig {
        private String defaultAction = "ALLOW";
        private boolean filteringEnabled = false;

        public FilterAction getDefaultAction() {
            return FilterAction.valueOf(defaultAction.toUpperCase());
        }
        public boolean isFilteringEnabled() { return filteringEnabled; }

        public void setDefault_action(String v) { this.defaultAction = v; }
        public void setFiltering_enabled(boolean v) { this.filteringEnabled = v; }
    }

    public static final class FeedbackConfig {
        private String mode = "chat";
        private String detail = "summary";
        private int summaryInterval = 5;

        public String getMode() { return mode; }
        public String getDetail() { return detail; }
        public int getSummaryInterval() { return summaryInterval; }

        public void setMode(String mode) { this.mode = mode; }
        public void setDetail(String detail) { this.detail = detail; }
        public void setSummary_interval(int v) { this.summaryInterval = v; }
    }

    public static final class PresetConfig {
        private String name = "New Preset";
        private String defaultAction = "ALLOW";
        private List<PresetRuleConfig> rules = new ArrayList<>();

        public String getName() { return name; }
        public String getDefaultAction() { return defaultAction; }
        public List<PresetRuleConfig> getRules() { return rules; }

        public void setName(String name) { this.name = name; }
        public void setDefault_action(String v) { this.defaultAction = v; }
        public void setRules(List<PresetRuleConfig> rules) { this.rules = rules; }

        /**
         * Convert this preset into a FilterProfile with a fresh UUID.
         */
        public FilterProfile toProfile() {
            FilterProfile.Builder builder = FilterProfile.builder()
                    .name(name)
                    .defaultAction(FilterAction.valueOf(defaultAction.toUpperCase()));

            for (PresetRuleConfig ruleConfig : rules) {
                builder.addRule(ruleConfig.toRule());
            }

            return builder.build();
        }
    }

    public static final class PresetRuleConfig {
        private String name = "New Rule";
        private String action = "ALLOW";
        private List<Map<String, Object>> conditions = new ArrayList<>();

        public String getName() { return name; }
        public String getAction() { return action; }
        public List<Map<String, Object>> getConditions() { return conditions; }

        public void setName(String name) { this.name = name; }
        public void setAction(String action) { this.action = action; }
        public void setConditions(List<Map<String, Object>> conditions) { this.conditions = conditions; }

        /**
         * Convert this preset rule config into a FilterRule.
         */
        public FilterRule toRule() {
            List<FilterCondition> parsedConditions = new ArrayList<>();
            for (Map<String, Object> condMap : conditions) {
                FilterCondition cond = parseCondition(condMap);
                if (cond != null) {
                    parsedConditions.add(cond);
                }
            }
            return new FilterRule(name, true, FilterAction.valueOf(action.toUpperCase()), parsedConditions);
        }

        @SuppressWarnings("unchecked")
        private FilterCondition parseCondition(Map<String, Object> map) {
            String type = (String) map.get("type");
            if (type == null) return null;

            return switch (type.toUpperCase()) {
                case "MIN_RARITY" -> new FilterCondition.MinRarity(
                        GearRarity.fromString((String) map.get("threshold")));
                case "MAX_RARITY" -> new FilterCondition.MaxRarity(
                        GearRarity.fromString((String) map.get("threshold")));
                case "EQUIPMENT_SLOT" -> new FilterCondition.EquipmentSlotCondition(
                        Set.copyOf((List<String>) map.get("slots")));
                case "ITEM_LEVEL_RANGE" -> new FilterCondition.ItemLevelRange(
                        ((Number) map.get("min")).intValue(),
                        ((Number) map.get("max")).intValue());
                case "QUALITY_RANGE" -> new FilterCondition.QualityRange(
                        ((Number) map.get("min")).intValue(),
                        ((Number) map.get("max")).intValue());
                case "MIN_MODIFIER_COUNT" -> new FilterCondition.MinModifierCount(
                        ((Number) map.get("count")).intValue());
                case "CORRUPTION_STATE" -> new FilterCondition.CorruptionStateCondition(
                        io.github.larsonix.trailoforbis.lootfilter.model.CorruptionFilter.valueOf(
                                ((String) map.get("filter")).toUpperCase()));
                default -> null;
            };
        }
    }
}
