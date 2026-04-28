package io.github.larsonix.trailoforbis.sanctum.ui;

import au.ellie.hyui.builders.HudBuilder;
import au.ellie.hyui.builders.HyUIHud;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.skilltree.NodeState;
import io.github.larsonix.trailoforbis.skilltree.SkillTreeRegion;
import io.github.larsonix.trailoforbis.skilltree.conditional.ConditionalConfig;
import io.github.larsonix.trailoforbis.skilltree.conditional.ConditionalTrigger;
import io.github.larsonix.trailoforbis.skilltree.config.SkillNode;
import io.github.larsonix.trailoforbis.skilltree.model.StatModifier;
import io.github.larsonix.trailoforbis.skilltree.model.StatType;
import io.github.larsonix.trailoforbis.skilltree.synergy.SynergyConfig;
import io.github.larsonix.trailoforbis.skilltree.synergy.SynergyType;
import io.github.larsonix.trailoforbis.ui.RPGStyles;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.UUID;

/**
 * HyUI HUD displaying detailed information about a skill node.
 *
 * <p>Unlike a Page, this HUD does NOT block player movement, allowing players
 * to continue navigating the Skill Sanctum while viewing node details.
 *
 * <p>The HUD is positioned on the right side of the screen (vertically centered) and displays:
 * <ul>
 *   <li>Node name with region color and type badge</li>
 *   <li>Node description</li>
 *   <li>Stat bonuses (green formatting)</li>
 *   <li>Drawbacks (red formatting, keystones only)</li>
 *   <li>Synergy info (scaling description)</li>
 *   <li>Conditional info (trigger type and effects)</li>
 *   <li>Current state and point cost</li>
 *   <li>Action hint for AVAILABLE nodes</li>
 *   <li>Close button</li>
 * </ul>
 *
 * <p>Managed by {@link SkillNodeHudManager} to prevent duplicate HUDs.
 *
 * @see SkillNodeHudManager
 */
public class SkillNodeDetailHud {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // ═══════════════════════════════════════════════════════════════════
    // CONSTANTS - LAYOUT
    // ═══════════════════════════════════════════════════════════════════

    // HUD is centered in the right half of the screen - no margin constants needed
    private static final int PANEL_WIDTH = 320;              // Panel width
    private static final int PANEL_PADDING = 16;             // Inner padding
    private static final int CONTAINER_CHROME = 25;          // decorated-container title bar + borders
    private static final int CHARS_PER_LINE = 45;            // Approx chars per line at font-size 12-13
    private static final int LINE_HEIGHT = 15;               // Height per text line (font-size 10-13)
    private static final int SECTION_SPACING = 10;           // Spacing between sections

    // ═══════════════════════════════════════════════════════════════════
    // FIELDS
    // ═══════════════════════════════════════════════════════════════════

    private final TrailOfOrbis plugin;
    private final PlayerRef player;
    private final SkillNode node;
    private final NodeState nodeState;
    private final int availablePoints;
    private final boolean canDeallocate;
    private final SkillNodeHudManager hudManager;

    // ═══════════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Creates a new skill node detail HUD.
     *
     * @param plugin          The main plugin instance
     * @param player          The player viewing the HUD
     * @param node            The skill node to display
     * @param nodeState       The current state of the node (LOCKED/AVAILABLE/ALLOCATED)
     * @param availablePoints The player's available skill points
     * @param canDeallocate   Whether this node can be deallocated (true for leaf nodes)
     * @param hudManager      The HUD manager for tracking this instance
     */
    public SkillNodeDetailHud(
            @Nonnull TrailOfOrbis plugin,
            @Nonnull PlayerRef player,
            @Nonnull SkillNode node,
            @Nonnull NodeState nodeState,
            int availablePoints,
            boolean canDeallocate,
            @Nonnull SkillNodeHudManager hudManager) {
        this.plugin = plugin;
        this.player = player;
        this.node = node;
        this.nodeState = nodeState;
        this.availablePoints = availablePoints;
        this.canDeallocate = canDeallocate;
        this.hudManager = hudManager;
    }

    // ═══════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Shows the detail HUD for the player.
     * Must be called from the world thread.
     */
    public void show() {
        try {
            World world = Universe.get().getWorld(player.getWorldUuid());
            if (world == null) {
                LOGGER.atWarning().log("Cannot show detail HUD - player world not found");
                return;
            }

            Store<EntityStore> store = world.getEntityStore().getStore();
            UUID playerUuid = player.getUuid();

            // Build the HUD HTML
            String html = buildHudHtml();

            // Remove existing HUD BEFORE creating new one to prevent overlapping
            // HUDs when player hits multiple nodes in same swing (rapid multi-hit)
            hudManager.removeHud(playerUuid);

            // Create and show the HUD
            HyUIHud hyuiHud = HudBuilder.hudForPlayer(player)
                .fromHtml(html)
                .show();

            // Register with manager (old HUD already removed above)
            hudManager.registerHud(playerUuid, node.getId(), hyuiHud);

            LOGGER.atFine().log("Showed skill node detail HUD for %s, node=%s",
                playerUuid.toString().substring(0, 8), node.getId());

        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to show skill node detail HUD");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // HTML GENERATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Builds the complete HTML for the HUD using HYUIML.
     */
    private String buildHudHtml() {
        StringBuilder content = new StringBuilder();

        SkillTreeRegion region = node.getSkillTreeRegion();
        String regionColor = region.getThemeColor();

        // Type badge and region (title is provided by decorated-container)
        content.append(buildTypeBadge(regionColor));

        // Description (only for flavor-only nodes like Origin and Entry)
        if (shouldShowDescription()) {
            content.append(buildDescription());
        }

        // Stat bonuses
        if (!node.getModifiers().isEmpty()) {
            content.append(buildModifiersSection("Stat Bonuses", node.getModifiers(), RPGStyles.POSITIVE));
        }

        // Drawbacks (keystones only)
        if (node.hasDrawbacks()) {
            content.append(buildModifiersSection("Drawbacks", node.getDrawbacks(), RPGStyles.NEGATIVE));
        }

        // Synergy info
        if (node.hasSynergy()) {
            content.append(buildSynergySection(node.getSynergy()));
        }

        // Conditional info
        if (node.hasConditional()) {
            content.append(buildConditionalSection(node.getConditional()));
        }

        // Status and cost
        content.append(buildStatusSection());

        // Action hint for AVAILABLE nodes (allocation)
        if (nodeState == NodeState.AVAILABLE && availablePoints >= node.getCost()) {
            content.append(buildAllocateHint());
        }

        // Action hint for ALLOCATED nodes that can be deallocated
        if (nodeState == NodeState.ALLOCATED && canDeallocate) {
            content.append(buildDeallocateHint());
        }

        // Wrap in panel structure
        return wrapInPanelStructure(content.toString());
    }

    /**
     * Wraps content in the HUD panel structure using HyUI's decorated-container.
     * Positioned in the center of the right half of the screen (both horizontally and vertically centered).
     */
    private String wrapInPanelStructure(String content) {
        int height = calculateContentHeight();
        // Use flex layout to position panel at 75% horizontally (center of right half)
        // Outer container: fills screen with horizontal layout
        // Left spacer (flex-weight: 3) + panel + right spacer (flex-weight: 1) = panel at ~75%
        // Inner vertical container centers the panel vertically
        return """
            <div style="anchor-horizontal: 0; anchor-vertical: 0; layout-mode: Left;">
                <div style="flex-weight: 3;"></div>
                <div style="anchor-width: %d; anchor-vertical: 0; layout-mode: Middle;">
                    <div class="decorated-container" data-hyui-title="%s" style="anchor-width: %d; anchor-height: %d;">
                        <div class="container-contents" style="layout-mode: Top;">
                            %s
                        </div>
                    </div>
                </div>
                <div style="flex-weight: 1;"></div>
            </div>
            """.formatted(PANEL_WIDTH, escapeHtml(node.getName()), PANEL_WIDTH, height, content);
    }

    /**
     * Calculates the required panel height based on actual content.
     */
    private int calculateContentHeight() {
        int height = PANEL_PADDING * 2; // Top and bottom padding
        height += CONTAINER_CHROME;     // decorated-container title bar + borders

        // Type badge row
        height += 22 + SECTION_SPACING;

        // Description (only for flavor-only nodes)
        if (shouldShowDescription()) {
            String description = node.getDescription();
            int lines = Math.max(1, (description.length() + CHARS_PER_LINE - 1) / CHARS_PER_LINE);
            height += lines * LINE_HEIGHT + SECTION_SPACING;
        }

        // Stat bonuses section (header font-size 10 + 4px spacing + lines font-size 12)
        if (!node.getModifiers().isEmpty()) {
            height += 10 + 4 + (node.getModifiers().size() * 14) + SECTION_SPACING;
        }

        // Drawbacks section (keystones)
        if (node.hasDrawbacks()) {
            height += 10 + 4 + (node.getDrawbacks().size() * 14) + SECTION_SPACING;
        }

        // Synergy section: box with padding(6), label(font-size 10), description(font-size 11, may wrap)
        if (node.hasSynergy()) {
            String synergyText = buildSynergyDescription(node.getSynergy(), node);
            if (node.getSynergy().hasCap()) {
                synergyText += String.format(" (cap: %.0f%%)", node.getSynergy().getCap());
            }
            // Synergy box is narrower: panel(320) - panel padding(32) - synergy padding(12) = ~276px
            // At font-size 11, roughly 50 chars per line
            int synergyCharsPerLine = 50;
            int synergyLines = Math.max(1, (synergyText.length() + synergyCharsPerLine - 1) / synergyCharsPerLine);
            // Base: 12px padding + 10px label + 12px first line = 34px, +12px per additional line
            height += 34 + ((synergyLines - 1) * 12) + SECTION_SPACING;
        }

        // Conditional section (padding 6*2 + label ~10 + text ~13)
        if (node.hasConditional()) {
            height += 30 + SECTION_SPACING;
        }

        // Status section (no spacing if action hint follows)
        boolean allocateHintFollows = nodeState == NodeState.AVAILABLE && availablePoints >= node.getCost();
        boolean deallocateHintFollows = nodeState == NodeState.ALLOCATED && canDeallocate;
        boolean actionHintFollows = allocateHintFollows || deallocateHintFollows;
        height += 24 + (actionHintFollows ? 0 : SECTION_SPACING);

        // Action hint (allocate for available, deallocate for leaf nodes)
        if (actionHintFollows) {
            height += 24;
        }

        // Small buffer for rendering variance
        height += 5;

        return height;
    }

    /**
     * Builds the type badge and region row.
     */
    private String buildTypeBadge(String regionColor) {
        String typeBadge = getTypeBadge();
        String typeBadgeColor = getTypeBadgeColor();

        return """
            <div style="layout-mode: Left; anchor-height: 22; anchor-horizontal: 0;">
                <div style="background-color: %s(0.3); anchor-height: 20; anchor-width: 70; layout-mode: MiddleCenter;">
                    <p style="font-size: 10; color: %s;">%s</p>
                </div>
                <div style="anchor-width: 8;"></div>
                <div style="anchor-height: 20; layout-mode: Middle;">
                    <p style="font-size: 11; color: %s; font-weight: bold; text-transform: uppercase;">%s</p>
                </div>
            </div>
            <div style="anchor-height: %d;"></div>
            """.formatted(typeBadgeColor, typeBadgeColor, typeBadge, regionColor,
                         node.getSkillTreeRegion().getDisplayName(), SECTION_SPACING);
    }

    /**
     * Builds the description section.
     */
    private String buildDescription() {
        return """
            <div style="anchor-horizontal: 0;">
                <p style="font-size: 12; color: %s; white-space: wrap;">%s</p>
            </div>
            <div style="anchor-height: %d;"></div>
            """.formatted(RPGStyles.TEXT_GRAY, escapeHtml(node.getDescription()), SECTION_SPACING);
    }

    /**
     * Builds a modifiers section (bonuses or drawbacks).
     */
    private String buildModifiersSection(String title, List<StatModifier> modifiers, String color) {
        StringBuilder lines = new StringBuilder();
        for (StatModifier mod : modifiers) {
            String formatted = mod.toShortString();
            lines.append("""
                <p style="font-size: 12; color: %s;">%s</p>
                """.formatted(color, escapeHtml(formatted)));
        }

        return """
            <div style="anchor-horizontal: 0; layout-mode: Top;">
                <p style="font-size: 10; color: %s;">%s :</p>
                <div style="anchor-height: 4;"></div>
                %s
            </div>
            <div style="anchor-height: %d;"></div>
            """.formatted(RPGStyles.TEXT_GRAY, title, lines.toString(), SECTION_SPACING);
    }

    /**
     * Builds the synergy section for synergy nodes.
     */
    private String buildSynergySection(SynergyConfig synergy) {
        String description = buildSynergyDescription(synergy, node);
        String capText = synergy.hasCap()
            ? String.format(" (cap: %.0f%%)", synergy.getCap())
            : "";

        return """
            <div style="anchor-horizontal: 0; background-color: #2a2a4a(0.8); layout-mode: Top; padding: 6;">
                <p style="font-size: 10; color: %s;">Synergy :</p>
                <p style="font-size: 11; color: %s; white-space: wrap;">%s%s</p>
            </div>
            <div style="anchor-height: %d;"></div>
            """.formatted(RPGStyles.TEXT_INFO, RPGStyles.TEXT_PRIMARY, escapeHtml(description), capText, SECTION_SPACING);
    }

    /**
     * Builds a human-readable synergy description.
     */
    private String buildSynergyDescription(SynergyConfig synergy, SkillNode node) {
        SynergyConfig.SynergyBonus bonus = synergy.getBonus();
        if (bonus == null) {
            return "Unknown synergy effect";
        }

        String statName = formatStatName(bonus.getStat());
        double value = bonus.getValue();
        int perCount = synergy.getPerCount();
        SynergyType type = synergy.getType();

        return switch (type) {
            case ELEMENTAL_COUNT -> String.format(
                "+%.0f%% %s per %d %s nodes",
                value, statName, perCount, synergy.getElementRegion().getDisplayName()
            );
            case STAT_COUNT -> String.format(
                "+%.0f%% %s per %d nodes with %s",
                value, statName, perCount, formatStatName(synergy.getStatType())
            );
            case BRANCH_COUNT -> String.format(
                "+%.0f%% %s per %d %s nodes",
                value, statName, perCount, node.getSkillTreeRegion().getDisplayName()
            );
            case TIER_COUNT -> String.format(
                "+%.0f%% %s per %d %s",
                value, statName, perCount, synergy.getTier() != null ? synergy.getTier().toLowerCase() + "s" : "notables"
            );
            case TOTAL_COUNT -> String.format(
                "+%.0f%% %s per %d total nodes",
                value, statName, perCount
            );
        };
    }

    /**
     * Builds the conditional section for conditional nodes.
     */
    private String buildConditionalSection(ConditionalConfig conditional) {
        String trigger = formatTrigger(conditional.getTrigger());
        String duration = conditional.isTimedEffect()
            ? String.format("for %.1fs", conditional.getDuration())
            : "";

        StringBuilder effects = new StringBuilder();
        for (ConditionalConfig.ConditionalEffect effect : conditional.getEffects()) {
            String statName = formatStatName(effect.getStat());
            effects.append(String.format("+%.0f%% %s, ", effect.getValue(), statName));
        }
        if (effects.length() > 2) {
            effects.setLength(effects.length() - 2);
        }

        return """
            <div style="anchor-horizontal: 0; background-color: #4a2a2a(0.8); layout-mode: Top; padding: 6;">
                <p style="font-size: 10; color: %s;">Conditional :</p>
                <p style="font-size: 11; color: %s;">%s : %s %s</p>
            </div>
            <div style="anchor-height: %d;"></div>
            """.formatted(RPGStyles.TEXT_WARNING, RPGStyles.TEXT_PRIMARY, trigger, escapeHtml(effects.toString()), duration, SECTION_SPACING);
    }

    /**
     * Builds the status section showing current state and cost.
     */
    private String buildStatusSection() {
        String stateText;
        String stateColor;
        switch (nodeState) {
            case LOCKED -> {
                stateText = "LOCKED";
                stateColor = RPGStyles.TEXT_GRAY;
            }
            case AVAILABLE -> {
                stateText = "AVAILABLE";
                stateColor = RPGStyles.POSITIVE;
            }
            case ALLOCATED -> {
                stateText = "ALLOCATED";
                stateColor = RPGStyles.TEXT_INFO;
            }
            default -> {
                stateText = "UNKNOWN";
                stateColor = RPGStyles.TEXT_GRAY;
            }
        }

        int cost = node.getCost();
        String costColor = (nodeState == NodeState.AVAILABLE && availablePoints >= cost)
            ? RPGStyles.POSITIVE
            : RPGStyles.TEXT_GRAY;

        // Use minimal spacing if action hint follows
        boolean allocateHintFollows = nodeState == NodeState.AVAILABLE && availablePoints >= node.getCost();
        boolean deallocateHintFollows = nodeState == NodeState.ALLOCATED && canDeallocate;
        int spacing = (allocateHintFollows || deallocateHintFollows) ? 0 : SECTION_SPACING;

        return """
            <div style="layout-mode: Left; anchor-horizontal: 0; anchor-height: 24;">
                <p style="font-size: 11; color: %s;">Status : </p>
                <p style="font-size: 11; color: %s; font-weight: bold;">%s</p>
                <div style="flex-weight: 1;"></div>
                <p style="font-size: 11; color: %s;">Cost : %d pt</p>
            </div>
            <div style="anchor-height: %d;"></div>
            """.formatted(RPGStyles.TEXT_GRAY, stateColor, stateText, costColor, cost, spacing);
    }

    /**
     * Builds the action hint for available nodes (allocation).
     */
    private String buildAllocateHint() {
        return """
            <div style="anchor-horizontal: 0; background-color: #2a4a2a(0.8); layout-mode: MiddleCenter; padding: 4 8;">
                <p style="font-size: 11; color: %s;">Press F to allocate</p>
            </div>
            """.formatted(RPGStyles.POSITIVE);
    }

    /**
     * Builds the action hint for allocated nodes that can be deallocated (leaf nodes).
     */
    private String buildDeallocateHint() {
        return """
            <div style="anchor-horizontal: 0; background-color: #4a3a2a(0.8); layout-mode: MiddleCenter; padding: 4 8;">
                <p style="font-size: 11; color: %s;">Press F to unallocate</p>
            </div>
            """.formatted(RPGStyles.TEXT_WARNING);
    }

    // ═══════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Checks if we should show the description field.
     * Only show for special nodes (Origin, Entry) that have flavor text.
     * All other nodes have descriptions that duplicate their effect sections.
     */
    private boolean shouldShowDescription() {
        // Don't show if empty
        if (node.getDescription().isEmpty()) {
            return false;
        }
        // Show for special nodes (Origin or Entry nodes)
        // These have unique flavor text that doesn't duplicate the effect sections
        return node.isStartNode() || node.getId().endsWith("_entry");
    }

    private String getTypeBadge() {
        if (node.isKeystone()) return "KEYSTONE";
        if (node.isNotable()) return "NOTABLE";
        if (node.isStartNode() || "origin".equals(node.getId())) return "ORIGIN";
        return "BASIC";
    }

    private String getTypeBadgeColor() {
        if (node.isKeystone()) return RPGStyles.TITLE_GOLD;
        if (node.isNotable()) return RPGStyles.TEXT_INFO;
        if (node.isStartNode() || "origin".equals(node.getId())) return RPGStyles.TITLE_GOLD;
        return RPGStyles.TEXT_GRAY;
    }

    private String formatStatName(String stat) {
        if (stat == null || stat.isEmpty()) return "???";
        return StatType.getDisplayNameFor(stat);
    }

    private String formatTrigger(ConditionalTrigger trigger) {
        if (trigger == null) return "Unknown";
        return switch (trigger) {
            case ON_KILL -> "On Kill";
            case ON_CRIT -> "On Crit";
            case WHEN_HIT -> "When Hit";
            case LOW_LIFE -> "Low Life";
            case FULL_LIFE -> "Full Life";
            case FULL_MANA -> "Full Mana";
            case LOW_MANA -> "Low Mana";
            case ON_SKILL_USE -> "On Skill";
            case ON_BLOCK -> "On Block";
            case ON_EVADE -> "On Evade";
            case WHILE_MOVING -> "Moving";
            case WHILE_STATIONARY -> "Stationary";
            case WHILE_BUFFED -> "Buffed";
            case ON_INFLICT_STATUS -> "Inflict Status";
        };
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }
}
