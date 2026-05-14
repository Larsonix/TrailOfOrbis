package io.github.larsonix.trailoforbis.mobs.modifiers;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration for the mob modifier system.
 *
 * <p>Loaded from {@code mob-modifiers.yml} via SnakeYAML.
 * All keys use snake_case in YAML, mapped to camelCase via setters.
 */
public class MobModifierConfig {

    private boolean enabled = true;

    // ==================== Modifier Count Per Tier ====================
    private Map<String, Integer> modifier_count = new LinkedHashMap<>(Map.of(
        "elite", 1,
        "boss", 2,
        "elite_boss", 3
    ));

    // ==================== Level Gates ====================
    private Map<String, Integer> level_gates = new LinkedHashMap<>(Map.of(
        "tier_1", 1,
        "tier_2", 10,
        "tier_3", 25,
        "tier_4", 40
    ));

    // ==================== Reward Scaling ====================
    private Map<Integer, RewardScaling> reward_scaling = new LinkedHashMap<>();

    // ==================== Exclusion Rules ====================
    private List<List<String>> exclusions = new ArrayList<>(List.of(
        List.of("hardened", "warding"),
        List.of("evasive", "frost_aura")
    ));

    // ==================== Visual Settings ====================
    private VisualConfig visuals = new VisualConfig();

    // ==================== Per-Modifier Settings ====================
    private Map<String, ModifierSettings> modifiers = new LinkedHashMap<>();

    // ==================== Getters ====================

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int getModifierCount(@Nonnull String tier) {
        return modifier_count.getOrDefault(tier, 0);
    }

    public void setModifier_count(Map<String, Integer> map) {
        if (map != null) this.modifier_count = map;
    }

    public int getLevelGate(@Nonnull ModifierTier tier) {
        String key = tier.name().toLowerCase();
        return level_gates.getOrDefault(key, tier.getLevelGate());
    }

    public void setLevel_gates(Map<String, Integer> map) {
        if (map != null) this.level_gates = map;
    }

    public void setReward_scaling(Map<Integer, RewardScaling> map) {
        if (map != null) this.reward_scaling = map;
    }

    @Nonnull
    public RewardScaling getRewardScaling(int modifierCount) {
        RewardScaling scaling = reward_scaling.get(modifierCount);
        if (scaling != null) return scaling;
        // Defaults
        return switch (modifierCount) {
            case 1 -> new RewardScaling(2.0, 1.5);
            case 2 -> new RewardScaling(4.0, 3.0);
            case 3 -> new RewardScaling(7.0, 5.0);
            default -> new RewardScaling(1.0, 1.0);
        };
    }

    public void setExclusions(List<List<String>> exclusions) {
        if (exclusions != null) this.exclusions = exclusions;
    }

    /**
     * Checks if two modifier types are excluded from coexisting.
     */
    public boolean isExcluded(@Nonnull ModifierType a, @Nonnull ModifierType b) {
        if (a == b) return true; // No duplicates
        String aKey = a.configKey();
        String bKey = b.configKey();
        for (List<String> pair : exclusions) {
            if (pair.size() == 2) {
                String x = pair.get(0);
                String y = pair.get(1);
                if ((x.equals(aKey) && y.equals(bKey)) || (x.equals(bKey) && y.equals(aKey))) {
                    return true;
                }
            }
        }
        return false;
    }

    @Nonnull
    public VisualConfig getVisuals() { return visuals; }
    public void setVisuals(VisualConfig visuals) {
        if (visuals != null) this.visuals = visuals;
    }

    @Nonnull
    public ModifierSettings getModifierSettings(@Nonnull ModifierType type) {
        ModifierSettings settings = modifiers.get(type.configKey());
        return settings != null ? settings : new ModifierSettings();
    }

    public void setModifiers(Map<String, ModifierSettings> modifiers) {
        if (modifiers != null) this.modifiers = modifiers;
    }

    @Nonnull
    public static MobModifierConfig createDefaults() {
        return new MobModifierConfig();
    }

    public void validate() {
        if (visuals == null) visuals = new VisualConfig();
    }

    // ==================== Inner Classes ====================

    public static class RewardScaling {
        private double iiq = 1.0;
        private double iir = 1.0;

        public RewardScaling() {}
        public RewardScaling(double iiq, double iir) {
            this.iiq = iiq;
            this.iir = iir;
        }

        public double getIiq() { return iiq; }
        public void setIiq(double iiq) { this.iiq = iiq; }
        public double getIir() { return iir; }
        public void setIir(double iir) { this.iir = iir; }
    }

    public static class VisualConfig {
        private double elite_scale = 1.15;
        private double boss_scale = 1.30;
        private double elite_boss_scale = 1.40;
        private String nameplate_prefix_elite = "\u2605 ";
        private String nameplate_prefix_boss = "\u2605\u2605 ";
        private String nameplate_prefix_elite_boss = "\u2605\u2605\u2605 ";

        public double getEliteScale() { return elite_scale; }
        public void setElite_scale(double s) { this.elite_scale = s; }
        public double getBossScale() { return boss_scale; }
        public void setBoss_scale(double s) { this.boss_scale = s; }
        public double getEliteBossScale() { return elite_boss_scale; }
        public void setElite_boss_scale(double s) { this.elite_boss_scale = s; }
        public String getNameplatePrefixElite() { return nameplate_prefix_elite; }
        public void setNameplate_prefix_elite(String s) { if (s != null) this.nameplate_prefix_elite = s; }
        public String getNameplatePrefixBoss() { return nameplate_prefix_boss; }
        public void setNameplate_prefix_boss(String s) { if (s != null) this.nameplate_prefix_boss = s; }
        public String getNameplatePrefixEliteBoss() { return nameplate_prefix_elite_boss; }
        public void setNameplate_prefix_elite_boss(String s) { if (s != null) this.nameplate_prefix_elite_boss = s; }

        public double getScaleForTier(@Nonnull String tier) {
            return switch (tier) {
                case "elite" -> elite_scale;
                case "boss" -> boss_scale;
                case "elite_boss" -> elite_boss_scale;
                default -> 1.0;
            };
        }

        public String getNameplatePrefixForTier(@Nonnull String tier) {
            return switch (tier) {
                case "elite" -> nameplate_prefix_elite;
                case "boss" -> nameplate_prefix_boss;
                case "elite_boss" -> nameplate_prefix_elite_boss;
                default -> "";
            };
        }
    }

    public static class ModifierSettings {
        // Stat bonuses (overrides enum defaults when present)
        private double armor_bonus_percent = 0;
        private double hp_bonus_percent = 0;
        private double damage_bonus_percent = 0;
        private double speed_bonus_percent = 0;
        private double knockback_multiplier = 1.0;
        private String element = null;
        private double element_damage_percent = 0;
        private double ailment_chance_bonus = 0;
        private double reflect_percent = 0;
        private double elemental_resist_bonus = 0;
        private double dodge_chance = 0;

        // Behavioral
        private double hp_threshold = 0.40;
        private double damage_bonus = 0;
        private double speed_bonus = 0;
        private double scale_increase = 0.10;
        private double heal_percent_per_second = 0.02;
        private double idle_delay_seconds = 4.0;
        private double cooldown_seconds = 8.0;
        private int frontal_arc_degrees = 90;

        // Aura
        private double aura_radius = 0;
        private double slow_percent = 0;
        private double buff_duration_seconds = 20.0;
        private double buff_radius = 15.0;

        // Summoner
        private double threshold_1_hp = 0.60;
        private double threshold_2_hp = 0.30;
        private int summon_count = 2;
        private int max_summons = 4;
        private int summon_level_offset = -5;

        // Death
        private double charge_delay_seconds = 2.0;
        private double explosion_damage_percent = 0.80;
        private double explosion_radius = 5.0;
        private double trail_damage_percent = 0.05;
        private double trail_duration_seconds = 3.0;
        private double death_cloud_duration_seconds = 5.0;
        private double death_cloud_dps_percent = 0.10;
        private double strike_cooldown_seconds = 8.0;
        private double strike_telegraph_seconds = 1.5;
        private double strike_damage_percent = 0.50;

        // Visual
        private String tint_bottom = null;
        private String tint_top = null;
        private String model_vfx = null;

        // All getters and setters
        public double getArmor_bonus_percent() { return armor_bonus_percent; }
        public void setArmor_bonus_percent(double v) { this.armor_bonus_percent = v; }
        public double getHp_bonus_percent() { return hp_bonus_percent; }
        public void setHp_bonus_percent(double v) { this.hp_bonus_percent = v; }
        public double getDamage_bonus_percent() { return damage_bonus_percent; }
        public void setDamage_bonus_percent(double v) { this.damage_bonus_percent = v; }
        public double getSpeed_bonus_percent() { return speed_bonus_percent; }
        public void setSpeed_bonus_percent(double v) { this.speed_bonus_percent = v; }
        public double getKnockback_multiplier() { return knockback_multiplier; }
        public void setKnockback_multiplier(double v) { this.knockback_multiplier = v; }
        public String getElement() { return element; }
        public void setElement(String v) { this.element = v; }
        public double getElement_damage_percent() { return element_damage_percent; }
        public void setElement_damage_percent(double v) { this.element_damage_percent = v; }
        public double getAilment_chance_bonus() { return ailment_chance_bonus; }
        public void setAilment_chance_bonus(double v) { this.ailment_chance_bonus = v; }
        public double getReflect_percent() { return reflect_percent; }
        public void setReflect_percent(double v) { this.reflect_percent = v; }
        public double getElemental_resist_bonus() { return elemental_resist_bonus; }
        public void setElemental_resist_bonus(double v) { this.elemental_resist_bonus = v; }
        public double getDodge_chance() { return dodge_chance; }
        public void setDodge_chance(double v) { this.dodge_chance = v; }
        public double getHp_threshold() { return hp_threshold; }
        public void setHp_threshold(double v) { this.hp_threshold = v; }
        public double getDamage_bonus() { return damage_bonus; }
        public void setDamage_bonus(double v) { this.damage_bonus = v; }
        public double getSpeed_bonus() { return speed_bonus; }
        public void setSpeed_bonus(double v) { this.speed_bonus = v; }
        public double getScale_increase() { return scale_increase; }
        public void setScale_increase(double v) { this.scale_increase = v; }
        public double getHeal_percent_per_second() { return heal_percent_per_second; }
        public void setHeal_percent_per_second(double v) { this.heal_percent_per_second = v; }
        public double getIdle_delay_seconds() { return idle_delay_seconds; }
        public void setIdle_delay_seconds(double v) { this.idle_delay_seconds = v; }
        public double getCooldown_seconds() { return cooldown_seconds; }
        public void setCooldown_seconds(double v) { this.cooldown_seconds = v; }
        public int getFrontal_arc_degrees() { return frontal_arc_degrees; }
        public void setFrontal_arc_degrees(int v) { this.frontal_arc_degrees = v; }
        public double getAura_radius() { return aura_radius; }
        public void setAura_radius(double v) { this.aura_radius = v; }
        public double getSlow_percent() { return slow_percent; }
        public void setSlow_percent(double v) { this.slow_percent = v; }
        public double getBuff_duration_seconds() { return buff_duration_seconds; }
        public void setBuff_duration_seconds(double v) { this.buff_duration_seconds = v; }
        public double getBuff_radius() { return buff_radius; }
        public void setBuff_radius(double v) { this.buff_radius = v; }
        public double getThreshold_1_hp() { return threshold_1_hp; }
        public void setThreshold_1_hp(double v) { this.threshold_1_hp = v; }
        public double getThreshold_2_hp() { return threshold_2_hp; }
        public void setThreshold_2_hp(double v) { this.threshold_2_hp = v; }
        public int getSummon_count() { return summon_count; }
        public void setSummon_count(int v) { this.summon_count = v; }
        public int getMax_summons() { return max_summons; }
        public void setMax_summons(int v) { this.max_summons = v; }
        public int getSummon_level_offset() { return summon_level_offset; }
        public void setSummon_level_offset(int v) { this.summon_level_offset = v; }
        public double getCharge_delay_seconds() { return charge_delay_seconds; }
        public void setCharge_delay_seconds(double v) { this.charge_delay_seconds = v; }
        public double getExplosion_damage_percent() { return explosion_damage_percent; }
        public void setExplosion_damage_percent(double v) { this.explosion_damage_percent = v; }
        public double getExplosion_radius() { return explosion_radius; }
        public void setExplosion_radius(double v) { this.explosion_radius = v; }
        public double getTrail_damage_percent() { return trail_damage_percent; }
        public void setTrail_damage_percent(double v) { this.trail_damage_percent = v; }
        public double getTrail_duration_seconds() { return trail_duration_seconds; }
        public void setTrail_duration_seconds(double v) { this.trail_duration_seconds = v; }
        public double getDeath_cloud_duration_seconds() { return death_cloud_duration_seconds; }
        public void setDeath_cloud_duration_seconds(double v) { this.death_cloud_duration_seconds = v; }
        public double getDeath_cloud_dps_percent() { return death_cloud_dps_percent; }
        public void setDeath_cloud_dps_percent(double v) { this.death_cloud_dps_percent = v; }
        public double getStrike_cooldown_seconds() { return strike_cooldown_seconds; }
        public void setStrike_cooldown_seconds(double v) { this.strike_cooldown_seconds = v; }
        public double getStrike_telegraph_seconds() { return strike_telegraph_seconds; }
        public void setStrike_telegraph_seconds(double v) { this.strike_telegraph_seconds = v; }
        public double getStrike_damage_percent() { return strike_damage_percent; }
        public void setStrike_damage_percent(double v) { this.strike_damage_percent = v; }
        @Nullable public String getTint_bottom() { return tint_bottom; }
        public void setTint_bottom(String v) { this.tint_bottom = v; }
        @Nullable public String getTint_top() { return tint_top; }
        public void setTint_top(String v) { this.tint_top = v; }
        @Nullable public String getModel_vfx() { return model_vfx; }
        public void setModel_vfx(String v) { this.model_vfx = v; }
    }
}
