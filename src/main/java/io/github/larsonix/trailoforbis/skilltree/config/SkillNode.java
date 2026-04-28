package io.github.larsonix.trailoforbis.skilltree.config;

import io.github.larsonix.trailoforbis.skilltree.SkillTreeRegion;
import io.github.larsonix.trailoforbis.skilltree.conditional.ConditionalConfig;
import io.github.larsonix.trailoforbis.skilltree.conversion.ConversionEffect;
import io.github.larsonix.trailoforbis.skilltree.conversion.DamageElement;
import io.github.larsonix.trailoforbis.skilltree.model.StatModifier;
import io.github.larsonix.trailoforbis.skilltree.synergy.SynergyConfig;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Configuration model for a single skill tree node.
 * Loaded from YAML, never persisted to database.
 *
 * <p>Node types:
 * <ul>
 *   <li><b>Basic</b>: Small passives with simple stat bonuses</li>
 *   <li><b>Notable</b>: Mid-tier nodes with multi-stat combinations or minor conditionals</li>
 *   <li><b>Synergy</b>: Nodes with bonuses that scale based on other allocations</li>
 *   <li><b>Keystone</b>: Powerful transformative nodes with significant tradeoffs</li>
 * </ul>
 *
 * <p>New fields for the Galaxy Skill Tree:
 * <ul>
 *   <li>{@link #synergy} - Scaling effects based on other allocations</li>
 *   <li>{@link #conditional} - Trigger-based effects (on-kill, on-crit, etc.)</li>
 *   <li>{@link #drawbacks} - Negative modifiers for keystone tradeoffs</li>
 * </ul>
 */
public class SkillNode {
    private String id;
    private String name;
    private String description;
    private int tier = 1;
    private String path = "origin";  // str, dex, int, vit, origin (legacy)
    private float positionX;
    private float positionY;
    private List<String> connections = new ArrayList<>();
    private List<StatModifier> modifiers = new ArrayList<>();
    private boolean startNode = false;
    private boolean keystone = false;
    private boolean notable = false;
    private String region;  // CORE, FIRE, WATER, LIGHTNING, EARTH, VOID, WIND

    // New fields for Galaxy Skill Tree
    @Nullable
    private SynergyConfig synergy;  // Scaling bonuses based on other allocations

    @Nullable
    private ConditionalConfig conditional;  // Trigger-based effects

    @Nullable
    private List<StatModifier> drawbacks;  // Negative modifiers for keystones

    @Nullable
    private List<ConversionConfig> conversions;  // Element-to-element conversion effects

    @Nullable
    private List<SynergyConfig> additionalSynergies;  // Overlay synergy bonuses (e.g., Hexcode)

    // Default constructor for YAML
    public SkillNode() {
    }

    // Getters
    @Nonnull
    public String getId() {
        return id != null ? id : "";
    }

    @Nonnull
    public String getName() {
        return name != null ? name : getId();
    }

    @Nonnull
    public String getDescription() {
        return description != null ? description : "";
    }

    public int getTier() {
        return tier;
    }

    @Nonnull
    public String getPath() {
        return path != null ? path : "origin";
    }

    public float getPositionX() {
        return positionX;
    }

    /**
     * Gets horizontal layout offset (alias for positionX as int).
     * Used by map renderer for node positioning within path.
     */
    public int getLayoutX() {
        return (int) positionX;
    }

    public float getPositionY() {
        return positionY;
    }

    @Nonnull
    public List<String> getConnections() {
        return connections != null ? connections : Collections.emptyList();
    }

    @Nonnull
    public List<StatModifier> getModifiers() {
        return modifiers != null ? modifiers : Collections.emptyList();
    }

    public boolean isStartNode() {
        return startNode;
    }

    public boolean isKeystone() {
        return keystone;
    }

    public boolean isNotable() {
        return notable;
    }

    /**
     * Gets the skill point cost to allocate this node.
     * Cost is based on node type:
     * - Basic (neither notable nor keystone): 1 point
     * - Notable: 2 points
     * - Keystone: 3 points
     *
     * @return The point cost for this node
     */
    public int getCost() {
        if (keystone) return 3;
        if (notable) return 2;
        return 1;
    }

    /**
     * Gets the raw region string (for YAML serialization).
     * This getter matches the setter type for SnakeYAML compatibility.
     */
    public String getRegion() {
        return region;
    }

    /**
     * Gets the region enum this node belongs to.
     * If not specified, derives from path field (legacy support).
     *
     * <p>The region field should use arm names (FIRE, WATER, LIGHTNING, EARTH, VOID, WIND).
     * Legacy path names (str, dex, int, vit) are mapped to new arms via
     * {@link SkillTreeRegion#fromString(String)}.
     */
    @Nonnull
    public SkillTreeRegion getSkillTreeRegion() {
        if (region != null && !region.isBlank()) {
            return SkillTreeRegion.fromString(region);
        }
        // Derive from legacy path if region not specified
        // SkillTreeRegion.fromString handles legacy -> new arm mapping
        if (path != null && !path.isBlank()) {
            return SkillTreeRegion.fromString(path);
        }
        return SkillTreeRegion.CORE;
    }

    // Setters for YAML deserialization
    public void setId(String id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setTier(int tier) {
        this.tier = tier;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public void setPositionX(float positionX) {
        this.positionX = positionX;
    }

    public void setPositionY(float positionY) {
        this.positionY = positionY;
    }

    public void setConnections(List<String> connections) {
        this.connections = connections;
    }

    public void setModifiers(List<StatModifier> modifiers) {
        this.modifiers = modifiers;
    }

    public void setStartNode(boolean startNode) {
        this.startNode = startNode;
    }

    public void setKeystone(boolean keystone) {
        this.keystone = keystone;
    }

    public void setNotable(boolean notable) {
        this.notable = notable;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    // ═══════════════════════════════════════════════════════════════════
    // NEW GALAXY SKILL TREE FIELDS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets the synergy configuration for this node.
     * Returns null if this is not a synergy node.
     */
    @Nullable
    public SynergyConfig getSynergy() {
        return synergy;
    }

    /**
     * Checks if this node has synergy scaling.
     */
    public boolean hasSynergy() {
        return synergy != null;
    }

    public void setSynergy(SynergyConfig synergy) {
        this.synergy = synergy;
    }

    /**
     * Gets the conditional configuration for this node.
     * Returns null if this node has no conditional effects.
     */
    @Nullable
    public ConditionalConfig getConditional() {
        return conditional;
    }

    /**
     * Checks if this node has conditional effects.
     */
    public boolean hasConditional() {
        return conditional != null;
    }

    public void setConditional(ConditionalConfig conditional) {
        this.conditional = conditional;
    }

    /**
     * Gets the drawback modifiers for this node (negative effects for keystones).
     * Returns empty list if none.
     */
    @Nonnull
    public List<StatModifier> getDrawbacks() {
        return drawbacks != null ? drawbacks : Collections.emptyList();
    }

    /**
     * Checks if this node has drawbacks.
     */
    public boolean hasDrawbacks() {
        return drawbacks != null && !drawbacks.isEmpty();
    }

    public void setDrawbacks(List<StatModifier> drawbacks) {
        this.drawbacks = drawbacks;
    }

    // Alias for YAML (drawback: instead of drawbacks:)
    public void setDrawback(List<StatModifier> drawbacks) {
        this.drawbacks = drawbacks;
    }

    /**
     * Gets the conversion effects for this node.
     * Returns empty list if none.
     */
    @Nonnull
    public List<ConversionEffect> getConversions() {
        if (conversions == null || conversions.isEmpty()) {
            return Collections.emptyList();
        }
        List<ConversionEffect> result = new ArrayList<>();
        for (ConversionConfig config : conversions) {
            ConversionEffect effect = config.toEffect();
            if (effect != null && effect.isValid()) {
                result.add(effect);
            }
        }
        return result;
    }

    /**
     * Checks if this node has conversions.
     */
    public boolean hasConversions() {
        return conversions != null && !conversions.isEmpty();
    }

    public void setConversions(List<ConversionConfig> conversions) {
        this.conversions = conversions;
    }

    /**
     * Gets additional synergy configs (from overlays like Hexcode).
     * Returns empty list if none.
     */
    @Nonnull
    public List<SynergyConfig> getAdditionalSynergies() {
        return additionalSynergies != null ? additionalSynergies : Collections.emptyList();
    }

    /**
     * Adds an additional synergy config (used by overlay loaders).
     */
    public void addAdditionalSynergy(@Nonnull SynergyConfig synergy) {
        if (additionalSynergies == null) {
            additionalSynergies = new ArrayList<>();
        }
        additionalSynergies.add(synergy);
    }

    /**
     * Checks if this is a synergy node (tier 5 with synergy config).
     */
    public boolean isSynergyNode() {
        return tier == 5 && synergy != null;
    }

    // ═══════════════════════════════════════════════════════════════════
    // YAML HELPER CLASS FOR CONVERSIONS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * YAML configuration model for damage conversion effects.
     *
     * <p>Example YAML:
     * <pre>
     * conversions:
     *   - source: WATER
     *     target: FIRE
     *     percent: 30.0
     * </pre>
     */
    public static class ConversionConfig {
        private String source;
        private String target;
        private float percent;

        public ConversionConfig() {}

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public String getTarget() {
            return target;
        }

        public void setTarget(String target) {
            this.target = target;
        }

        public float getPercent() {
            return percent;
        }

        public void setPercent(float percent) {
            this.percent = percent;
        }

        /**
         * Converts this YAML config to a ConversionEffect.
         *
         * @return The conversion effect, or null if invalid
         */
        @Nullable
        public ConversionEffect toEffect() {
            DamageElement sourceElem = DamageElement.fromString(source);
            DamageElement targetElem = DamageElement.fromString(target);
            if (sourceElem == null || targetElem == null || percent <= 0) {
                return null;
            }
            return new ConversionEffect(sourceElem, targetElem, percent);
        }
    }

    @Override
    public String toString() {
        return String.format("SkillNode{id='%s', name='%s', tier=%d, region=%s, connections=%d, synergy=%b, conditional=%b}",
            id, name, tier, region, connections != null ? connections.size() : 0,
            synergy != null, conditional != null);
    }
}
