package io.github.larsonix.trailoforbis.compat;

import com.hypixel.hytale.logger.HytaleLogger;
import io.github.larsonix.trailoforbis.skilltree.config.SkillNode;
import io.github.larsonix.trailoforbis.skilltree.config.SkillTreeConfig;
import io.github.larsonix.trailoforbis.skilltree.model.ModifierType;
import io.github.larsonix.trailoforbis.skilltree.model.StatModifier;
import io.github.larsonix.trailoforbis.skilltree.model.StatType;
import io.github.larsonix.trailoforbis.skilltree.synergy.SynergyConfig;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import org.yaml.snakeyaml.Yaml;

/**
 * Loads and applies the Hexcode skill tree overlay when Hexcode is detected.
 *
 * <p>The overlay appends Hexcode-relevant modifiers (volatility, magic power,
 * draw accuracy, cast speed, magic charges) to existing magic nodes in the
 * skill tree. It never removes or replaces base modifiers — only adds.
 */
public final class HexcodeSkillTreeOverlay {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String OVERLAY_FILE = "config/skill-tree-hexcode.yml";

    private HexcodeSkillTreeOverlay() {}

    /**
     * Loads the overlay YAML and applies it to the given skill tree config.
     *
     * <p>For each entry in the overlay:
     * <ul>
     *   <li>Finds the matching node by ID in the loaded tree</li>
     *   <li>Appends modifiers to the node's existing modifier list</li>
     *   <li>If description is present, replaces the node's description</li>
     *   <li>If synergy bonuses are present, adds them as additional synergies</li>
     * </ul>
     *
     * @param treeConfig The loaded base skill tree config to modify in-place
     * @param dataFolder The plugin data folder (for config file resolution)
     * @param resourceCopier Callback to copy default config from resources if missing
     * @return Number of nodes modified, or -1 if overlay loading failed
     */
    public static int apply(@Nonnull SkillTreeConfig treeConfig, @Nonnull Path dataFolder,
                             @Nonnull ResourceCopier resourceCopier) {
        Path overlayPath = dataFolder.resolve(OVERLAY_FILE);

        // Copy default from resources if not present
        if (!Files.exists(overlayPath)) {
            try {
                resourceCopier.copy(OVERLAY_FILE, overlayPath);
            } catch (IOException e) {
                LOGGER.at(Level.WARNING).log("Failed to copy default %s: %s", OVERLAY_FILE, e.getMessage());
                return -1;
            }
        }

        // Load overlay YAML
        OverlayConfig overlayConfig;
        try (InputStream is = Files.newInputStream(overlayPath)) {
            Yaml yaml = new Yaml();
            overlayConfig = yaml.loadAs(is, OverlayConfig.class);
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).log("Failed to load %s: %s", OVERLAY_FILE, e.getMessage());
            return -1;
        }

        if (overlayConfig == null || overlayConfig.getOverlay() == null || overlayConfig.getOverlay().isEmpty()) {
            LOGGER.at(Level.INFO).log("Hexcode skill tree overlay is empty — no nodes modified");
            return 0;
        }

        // Apply overlay entries to the tree
        int modified = 0;
        for (Map.Entry<String, NodeOverlay> entry : overlayConfig.getOverlay().entrySet()) {
            String nodeId = entry.getKey();
            NodeOverlay overlay = entry.getValue();
            if (overlay == null) continue;

            SkillNode node = treeConfig.getNode(nodeId);
            if (node == null) {
                LOGGER.at(Level.WARNING).log("Hexcode overlay: node '%s' not found in base tree — skipping", nodeId);
                continue;
            }

            boolean nodeModified = applyNodeOverlay(node, nodeId, overlay);
            if (nodeModified) {
                modified++;
            }
        }

        LOGGER.at(Level.INFO).log("Hexcode skill tree overlay applied: %d nodes modified", modified);
        return modified;
    }

    /**
     * Applies a single overlay entry to a node.
     */
    private static boolean applyNodeOverlay(@Nonnull SkillNode node, @Nonnull String nodeId,
                                             @Nonnull NodeOverlay overlay) {
        boolean changed = false;

        // Append regular modifiers
        if (overlay.getAppendModifiers() != null) {
            for (ModifierEntry mod : overlay.getAppendModifiers()) {
                StatModifier resolved = resolveModifier(mod, nodeId);
                if (resolved != null) {
                    node.getModifiers().add(resolved);
                    changed = true;
                }
            }
        }

        // Append synergy modifiers (additional scaling bonuses)
        if (overlay.getAppendSynergyModifiers() != null) {
            for (SynergyOverlay synergyOverlay : overlay.getAppendSynergyModifiers()) {
                SynergyConfig synergyConfig = resolveSynergyConfig(synergyOverlay, node, nodeId);
                if (synergyConfig != null) {
                    node.addAdditionalSynergy(synergyConfig);
                    changed = true;
                }
            }
        }

        // Override description
        if (overlay.getDescription() != null && !overlay.getDescription().isBlank()) {
            node.setDescription(overlay.getDescription());
            changed = true;
        }

        return changed;
    }

    /**
     * Resolves a YAML modifier entry to a StatModifier.
     */
    @Nullable
    private static StatModifier resolveModifier(@Nonnull ModifierEntry mod, @Nonnull String nodeId) {
        if (mod.getStat() == null || mod.getType() == null) {
            LOGGER.at(Level.WARNING).log("Hexcode overlay: invalid modifier in node '%s' — missing stat or type", nodeId);
            return null;
        }

        StatType statType;
        try {
            statType = StatType.valueOf(mod.getStat().toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            LOGGER.at(Level.WARNING).log("Hexcode overlay: unknown stat '%s' in node '%s'", mod.getStat(), nodeId);
            return null;
        }

        ModifierType modType;
        try {
            modType = ModifierType.valueOf(mod.getType().toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            LOGGER.at(Level.WARNING).log("Hexcode overlay: unknown modifier type '%s' in node '%s'", mod.getType(), nodeId);
            return null;
        }

        return new StatModifier(statType, mod.getValue(), modType);
    }

    /**
     * Resolves a synergy overlay entry to a SynergyConfig.
     * Inherits type/element/per_count from the node's existing synergy if not specified.
     */
    @Nullable
    private static SynergyConfig resolveSynergyConfig(@Nonnull SynergyOverlay synergyOverlay,
                                                        @Nonnull SkillNode node, @Nonnull String nodeId) {
        if (synergyOverlay.getStat() == null) {
            LOGGER.at(Level.WARNING).log("Hexcode overlay: synergy missing stat in node '%s'", nodeId);
            return null;
        }

        SynergyConfig config = new SynergyConfig();

        // Inherit trigger parameters from the node's existing synergy
        SynergyConfig existing = node.getSynergy();
        if (existing != null) {
            config.setType(existing.getType());
            config.setElement(existing.getElement());
            config.setPerCount(existing.getPerCount());
        }

        // Override with explicit values if specified
        if (synergyOverlay.getType() != null) {
            config.setType(synergyOverlay.getType());
        }
        if (synergyOverlay.getElement() != null) {
            config.setElement(synergyOverlay.getElement());
        }
        if (synergyOverlay.getPerCount() > 0) {
            config.setPerCount(synergyOverlay.getPerCount());
        }
        if (synergyOverlay.getCap() > 0) {
            config.setCap(synergyOverlay.getCap());
        }

        // Build the bonus
        SynergyConfig.SynergyBonus bonus = new SynergyConfig.SynergyBonus();
        bonus.setStat(synergyOverlay.getStat().toUpperCase().trim());
        bonus.setValue(synergyOverlay.getValue());
        bonus.setModifierType(synergyOverlay.getModifierType() != null
                ? synergyOverlay.getModifierType().toUpperCase().trim() : "FLAT");
        config.setBonus(bonus);

        return config;
    }

    // ═══════════════════════════════════════════════════════════════════
    // YAML CONFIG MODEL
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Root overlay config loaded from skill-tree-hexcode.yml.
     */
    public static class OverlayConfig {
        private Map<String, NodeOverlay> overlay;

        public Map<String, NodeOverlay> getOverlay() { return overlay; }
        public void setOverlay(Map<String, NodeOverlay> overlay) { this.overlay = overlay; }
    }

    /**
     * Overlay for a single node.
     */
    public static class NodeOverlay {
        private List<ModifierEntry> append_modifiers;
        private List<SynergyOverlay> append_synergy_modifiers;
        private String description;

        public List<ModifierEntry> getAppendModifiers() { return append_modifiers; }
        public void setAppend_modifiers(List<ModifierEntry> mods) { this.append_modifiers = mods; }

        public List<SynergyOverlay> getAppendSynergyModifiers() { return append_synergy_modifiers; }
        public void setAppend_synergy_modifiers(List<SynergyOverlay> mods) { this.append_synergy_modifiers = mods; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    /**
     * A single modifier entry in the overlay.
     */
    public static class ModifierEntry {
        private String stat;
        private float value;
        private String type;

        public String getStat() { return stat; }
        public void setStat(String stat) { this.stat = stat; }

        public float getValue() { return value; }
        public void setValue(float value) { this.value = value; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
    }

    /**
     * A synergy bonus entry in the overlay.
     * Inherits type/element/per_count from the node's existing synergy if not specified.
     */
    public static class SynergyOverlay {
        private String stat;
        private double value;
        private String modifierType;

        // Optional overrides — inherit from existing synergy if not set
        private io.github.larsonix.trailoforbis.skilltree.synergy.SynergyType type;
        private String element;
        private int perCount;
        private double cap;

        public String getStat() { return stat; }
        public void setStat(String stat) { this.stat = stat; }

        public double getValue() { return value; }
        public void setValue(double value) { this.value = value; }

        public String getModifierType() { return modifierType; }
        public void setModifierType(String modifierType) { this.modifierType = modifierType; }
        public void setModifier_type(String modifierType) { this.modifierType = modifierType; }

        public io.github.larsonix.trailoforbis.skilltree.synergy.SynergyType getType() { return type; }
        public void setType(io.github.larsonix.trailoforbis.skilltree.synergy.SynergyType type) { this.type = type; }

        public String getElement() { return element; }
        public void setElement(String element) { this.element = element; }

        public int getPerCount() { return perCount; }
        public void setPerCount(int perCount) { this.perCount = perCount; }
        public void setPer_count(int perCount) { this.perCount = perCount; }

        public double getCap() { return cap; }
        public void setCap(double cap) { this.cap = cap; }
    }

    /**
     * Functional interface for copying resources from the JAR to the data folder.
     */
    @FunctionalInterface
    public interface ResourceCopier {
        void copy(String resourcePath, Path targetPath) throws IOException;
    }
}
