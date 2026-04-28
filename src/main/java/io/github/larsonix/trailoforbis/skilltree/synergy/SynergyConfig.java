package io.github.larsonix.trailoforbis.skilltree.synergy;

import io.github.larsonix.trailoforbis.skilltree.SkillTreeRegion;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Configuration for a synergy node's scaling effect.
 *
 * <p>Synergy nodes provide bonuses that scale based on other allocations:
 * <ul>
 *   <li><b>ELEMENTAL_COUNT</b>: Per X nodes in same element, gain Y bonus</li>
 *   <li><b>STAT_COUNT</b>: Per X nodes granting a specific stat type, gain Y bonus</li>
 *   <li><b>BRANCH_COUNT</b>: Per X nodes in same branch/region, gain Y bonus</li>
 *   <li><b>TIER_COUNT</b>: Per X notables/keystones allocated, gain Y bonus</li>
 * </ul>
 *
 * <h2>Example YAML:</h2>
 * <pre>
 * synergy:
 *   type: ELEMENTAL_COUNT
 *   element: FIRE
 *   per_count: 3
 *   bonus:
 *     stat: FIRE_DAMAGE_PERCENT
 *     value: 3.0
 *   cap: 30.0
 * </pre>
 *
 * @see SynergyType
 */
public class SynergyConfig {

    /**
     * The type of synergy calculation.
     */
    private SynergyType type;

    /**
     * Element to count (for ELEMENTAL_COUNT type).
     * Maps to a SkillTreeRegion arm.
     */
    @Nullable
    private String element;

    /**
     * Stat type to count (for STAT_COUNT type).
     * E.g., "PHYSICAL_DAMAGE", "MAX_HEALTH", etc.
     */
    @Nullable
    private String statType;

    /**
     * Tier to count (for TIER_COUNT type).
     * E.g., "NOTABLE", "KEYSTONE", "ANY"
     */
    @Nullable
    private String tier;

    /**
     * Number of nodes required for each bonus increment.
     */
    private int perCount = 3;

    /**
     * The bonus granted per increment.
     */
    @Nullable
    private SynergyBonus bonus;

    /**
     * Maximum total bonus (cap).
     * Set to 0 or negative for no cap.
     */
    private double cap = 0.0;

    // Default constructor for YAML
    public SynergyConfig() {
    }

    // ═══════════════════════════════════════════════════════════════════
    // GETTERS
    // ═══════════════════════════════════════════════════════════════════

    @Nonnull
    public SynergyType getType() {
        return type != null ? type : SynergyType.ELEMENTAL_COUNT;
    }

    @Nullable
    public String getElement() {
        return element;
    }

    /**
     * Gets the element as a SkillTreeRegion.
     */
    @Nonnull
    public SkillTreeRegion getElementRegion() {
        return SkillTreeRegion.fromString(element);
    }

    @Nullable
    public String getStatType() {
        return statType;
    }

    @Nullable
    public String getTier() {
        return tier;
    }

    public int getPerCount() {
        return perCount > 0 ? perCount : 1;
    }

    @Nullable
    public SynergyBonus getBonus() {
        return bonus;
    }

    public double getCap() {
        return cap;
    }

    /**
     * Checks if this synergy has a cap.
     */
    public boolean hasCap() {
        return cap > 0;
    }

    // ═══════════════════════════════════════════════════════════════════
    // SETTERS (for YAML deserialization)
    // ═══════════════════════════════════════════════════════════════════

    public void setType(SynergyType type) {
        this.type = type;
    }

    public void setElement(String element) {
        this.element = element;
    }

    public void setStatType(String statType) {
        this.statType = statType;
    }

    public void setTier(String tier) {
        this.tier = tier;
    }

    public void setPerCount(int perCount) {
        this.perCount = perCount;
    }

    public void setBonus(SynergyBonus bonus) {
        this.bonus = bonus;
    }

    public void setCap(double cap) {
        this.cap = cap;
    }

    // Alias for YAML compatibility (per_count vs perCount)
    public void setPer_count(int perCount) {
        this.perCount = perCount;
    }

    @Override
    public String toString() {
        return String.format("SynergyConfig{type=%s, element=%s, per=%d, cap=%.1f}",
            type, element, perCount, cap);
    }

    // ═══════════════════════════════════════════════════════════════════
    // INNER CLASSES
    // ═══════════════════════════════════════════════════════════════════

    /**
     * The bonus granted by a synergy per increment.
     */
    public static class SynergyBonus {
        /**
         * The stat to modify.
         */
        private String stat;

        /**
         * The value to add per increment.
         */
        private double value;

        /**
         * The modifier type (FLAT, PERCENT, MULTIPLIER).
         */
        private String modifierType = "PERCENT";

        public SynergyBonus() {
        }

        @Nonnull
        public String getStat() {
            return stat != null ? stat : "";
        }

        public double getValue() {
            return value;
        }

        @Nonnull
        public String getModifierType() {
            return modifierType != null ? modifierType : "PERCENT";
        }

        public void setStat(String stat) {
            this.stat = stat;
        }

        public void setValue(double value) {
            this.value = value;
        }

        public void setModifierType(String modifierType) {
            this.modifierType = modifierType;
        }

        // Alias for YAML (modifier_type)
        public void setModifier_type(String modifierType) {
            this.modifierType = modifierType;
        }

        @Override
        public String toString() {
            return String.format("SynergyBonus{stat=%s, value=%.1f, type=%s}",
                stat, value, modifierType);
        }
    }
}
