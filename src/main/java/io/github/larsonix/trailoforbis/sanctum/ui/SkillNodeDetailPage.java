package io.github.larsonix.trailoforbis.sanctum.ui;

import au.ellie.hyui.builders.PageBuilder;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
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

/**
 * HyUI page displaying detailed information about a skill node.
 *
 * <p>Shown when a player right-clicks any skill node in the Skill Sanctum.
 * Displays:
 * <ul>
 *   <li>Header with node name (colored by region) and type badge</li>
 *   <li>Node description</li>
 *   <li>Stat bonuses (green formatting)</li>
 *   <li>Drawbacks (red formatting, keystones only)</li>
 *   <li>Synergy info (scaling description + current bonus)</li>
 *   <li>Conditional info (trigger type and effects)</li>
 *   <li>Current state and point cost</li>
 *   <li>Action hint for AVAILABLE nodes</li>
 *   <li>Close button</li>
 * </ul>
 *
 * @see io.github.larsonix.trailoforbis.sanctum.interactions.SkillNodeInspectInteraction
 */
public class SkillNodeDetailPage {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // ═══════════════════════════════════════════════════════════════════
    // CONSTANTS - LAYOUT SIZING
    // ═══════════════════════════════════════════════════════════════════

    private static final int CONTAINER_WIDTH = 340;          // Inner content width
    private static final int CHARS_PER_LINE = 45;            // Approx chars per line at font-size 13
    private static final int LINE_HEIGHT = 18;               // Height per text line
    private static final int SECTION_SPACING = 12;           // Spacing between sections
    private static final int CONTAINER_PADDING = 60;         // Container chrome (title bar, borders)

    // ═══════════════════════════════════════════════════════════════════
    // FIELDS
    // ═══════════════════════════════════════════════════════════════════

    private final TrailOfOrbis plugin;
    private final PlayerRef player;
    private final SkillNode node;
    private final NodeState nodeState;
    private final int availablePoints;

    // ═══════════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Creates a new skill node detail page.
     *
     * @param plugin          The main plugin instance
     * @param player          The player viewing the page
     * @param node            The skill node to display
     * @param nodeState       The current state of the node (LOCKED/AVAILABLE/ALLOCATED)
     * @param availablePoints The player's available skill points
     */
    public SkillNodeDetailPage(
            @Nonnull TrailOfOrbis plugin,
            @Nonnull PlayerRef player,
            @Nonnull SkillNode node,
            @Nonnull NodeState nodeState,
            int availablePoints) {
        this.plugin = plugin;
        this.player = player;
        this.node = node;
        this.nodeState = nodeState;
        this.availablePoints = availablePoints;
    }

    // ═══════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Opens the detail page for the player.
     */
    public void open() {
        try {
            // Get the entity store from the player's world
            World world = Universe.get().getWorld(player.getWorldUuid());
            if (world == null) {
                LOGGER.atWarning().log("Cannot open detail page - player world not found");
                return;
            }

            Store<EntityStore> store = world.getEntityStore().getStore();
            String html = buildPageHtml();

            PageBuilder.pageForPlayer(player)
                .withLifetime(CustomPageLifetime.CanDismiss)
                .fromHtml(html)
                .addEventListener("close-btn", CustomUIEventBindingType.Activating, (data, ctx) -> {
                    // Page closes automatically when button is clicked
                })
                .open(store);

            LOGGER.atFine().log("Opened skill node detail page for %s, node=%s",
                player.getUuid().toString().substring(0, 8), node.getId());

        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to open skill node detail page");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // HTML GENERATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Builds the complete HTML for the page using HYUIML.
     */
    private String buildPageHtml() {
        SkillTreeRegion region = node.getSkillTreeRegion();
        String regionColor = region.getThemeColor();

        StringBuilder content = new StringBuilder();

        // Header with name and type badge
        content.append(buildHeader(regionColor));

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

        // Action hint for AVAILABLE nodes
        if (nodeState == NodeState.AVAILABLE && availablePoints >= node.getCost()) {
            content.append(buildActionHint());
        }

        // Close button
        content.append(buildCloseButton());

        // Wrap in page structure
        return wrapInPageStructure(content.toString());
    }

    /**
     * Wraps content in the standard page overlay structure.
     * Container size is calculated dynamically based on actual content.
     */
    private String wrapInPageStructure(String content) {
        int height = calculateContentHeight();
        return """
            <div class="page-overlay">
                <div class="decorated-container" data-hyui-title="%s" style="anchor-width: %d; anchor-height: %d;">
                    <div class="container-contents" style="layout-mode: Top;">
                        %s
                    </div>
                </div>
            </div>
            """.formatted(escapeHtml(node.getName()), CONTAINER_WIDTH + 60, height, content);
    }

    /**
     * Calculates the required container height based on actual content.
     * This provides dynamic sizing so the UI fits the content properly.
     */
    private int calculateContentHeight() {
        int height = CONTAINER_PADDING; // Start with container chrome

        // Header: type badge row + spacer
        height += 24 + SECTION_SPACING;

        // Description (only for flavor-only nodes)
        if (shouldShowDescription()) {
            String description = node.getDescription();
            int lines = Math.max(1, (description.length() + CHARS_PER_LINE - 1) / CHARS_PER_LINE);
            height += lines * LINE_HEIGHT + SECTION_SPACING;
        }

        // Stat bonuses section
        if (!node.getModifiers().isEmpty()) {
            // Section header + spacing + one line per modifier + section spacing
            height += 16 + 4 + (node.getModifiers().size() * LINE_HEIGHT) + SECTION_SPACING;
        }

        // Drawbacks section (keystones)
        if (node.hasDrawbacks()) {
            height += 16 + 4 + (node.getDrawbacks().size() * LINE_HEIGHT) + SECTION_SPACING;
        }

        // Synergy section (compact box)
        if (node.hasSynergy()) {
            height += 38 + SECTION_SPACING;
        }

        // Conditional section (compact box)
        if (node.hasConditional()) {
            height += 38 + SECTION_SPACING;
        }

        // Status section: spacer + row + spacer
        height += 8 + 24 + 8;

        // Action hint (only for available nodes with enough points)
        if (nodeState == NodeState.AVAILABLE && availablePoints >= node.getCost()) {
            height += 24;
        }

        // Close button: spacer + button + bottom margin
        height += 8 + 32 + 8;

        return height;
    }

    /**
     * Builds the header with type badge.
     */
    private String buildHeader(String regionColor) {
        String typeBadge = getTypeBadge();
        String typeBadgeColor = getTypeBadgeColor();

        // Show type badge and region
        return """
            <div style="layout-mode: Left; anchor-height: 24; anchor-horizontal: 0;">
                <div style="background-color: %s(0.3); anchor-height: 22; anchor-width: 80; layout-mode: MiddleCenter;">
                    <p style="font-size: 11; color: %s;">%s</p>
                </div>
                <div style="anchor-width: 8;"></div>
                <p style="font-size: 12; color: %s;">%s</p>
            </div>
            <div style="anchor-height: 12;"></div>
            """.formatted(typeBadgeColor, typeBadgeColor, typeBadge, regionColor, node.getSkillTreeRegion().getDisplayName());
    }

    /**
     * Builds the description section.
     */
    private String buildDescription() {
        return """
            <div style="anchor-horizontal: 0;">
                <p style="font-size: 13; color: %s; white-space: wrap;">%s</p>
            </div>
            <div style="anchor-height: 12;"></div>
            """.formatted(RPGStyles.TEXT_GRAY, escapeHtml(node.getDescription()));
    }

    /**
     * Builds a modifiers section (bonuses or drawbacks).
     */
    private String buildModifiersSection(String title, List<StatModifier> modifiers, String color) {
        StringBuilder lines = new StringBuilder();
        for (StatModifier mod : modifiers) {
            String formatted = mod.toShortString();
            lines.append("""
                <p style="font-size: 13; color: %s;">%s</p>
                """.formatted(color, escapeHtml(formatted)));
        }

        return """
            <div style="anchor-horizontal: 0; layout-mode: Top;">
                <p style="font-size: 11; color: %s;">%s :</p>
                <div style="anchor-height: 4;"></div>
                %s
            </div>
            <div style="anchor-height: 12;"></div>
            """.formatted(RPGStyles.TEXT_GRAY, title, lines.toString());
    }

    /**
     * Builds the synergy section for synergy nodes.
     */
    private String buildSynergySection(SynergyConfig synergy) {
        String description = buildSynergyDescription(synergy, node);
        String capText = synergy.hasCap()
            ? String.format(" (capped at %.0f%%)", synergy.getCap())
            : "";

        return """
            <div style="anchor-horizontal: 0; background-color: #2a2a4a; layout-mode: Top; padding: 8;">
                <p style="font-size: 11; color: %s;">Synergy :</p>
                <div style="anchor-height: 4;"></div>
                <p style="font-size: 13; color: %s; white-space: wrap;">%s%s</p>
            </div>
            <div style="anchor-height: 12;"></div>
            """.formatted(RPGStyles.TEXT_INFO, RPGStyles.TEXT_PRIMARY, escapeHtml(description), capText);
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
                "+%.0f%% %s per %d %s nodes allocated",
                value, statName, perCount, synergy.getElementRegion().getDisplayName()
            );
            case STAT_COUNT -> String.format(
                "+%.0f%% %s per %d nodes with %s",
                value, statName, perCount, formatStatName(synergy.getStatType())
            );
            case BRANCH_COUNT -> String.format(
                "+%.0f%% %s per %d %s nodes allocated",
                value, statName, perCount, node.getSkillTreeRegion().getDisplayName()
            );
            case TIER_COUNT -> String.format(
                "+%.0f%% %s per %d %s allocated",
                value, statName, perCount, synergy.getTier() != null ? synergy.getTier().toLowerCase() + "s" : "notable nodes"
            );
            case TOTAL_COUNT -> String.format(
                "+%.0f%% %s per %d total nodes allocated",
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
            : "(while active)";

        StringBuilder effects = new StringBuilder();
        for (ConditionalConfig.ConditionalEffect effect : conditional.getEffects()) {
            String statName = formatStatName(effect.getStat());
            effects.append(String.format("+%.0f%% %s, ", effect.getValue(), statName));
        }
        // Remove trailing comma
        if (effects.length() > 2) {
            effects.setLength(effects.length() - 2);
        }

        return """
            <div style="anchor-horizontal: 0; background-color: #4a2a2a; layout-mode: Top; padding: 8;">
                <p style="font-size: 11; color: %s;">Conditional :</p>
                <div style="anchor-height: 4;"></div>
                <p style="font-size: 13; color: %s;">%s : %s %s</p>
            </div>
            <div style="anchor-height: 12;"></div>
            """.formatted(RPGStyles.TEXT_WARNING, RPGStyles.TEXT_PRIMARY, trigger, escapeHtml(effects.toString()), duration);
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

        return """
            <div style="anchor-height: 8;"></div>
            <div style="layout-mode: Left; anchor-horizontal: 0; anchor-height: 24;">
                <p style="font-size: 12; color: %s;">Status : </p>
                <p style="font-size: 12; color: %s; font-weight: bold;">%s</p>
                <div style="flex-weight: 1;"></div>
                <p style="font-size: 12; color: %s;">Cost : %d point%s</p>
            </div>
            <div style="anchor-height: 8;"></div>
            """.formatted(RPGStyles.TEXT_GRAY, stateColor, stateText, costColor, cost, cost == 1 ? "" : "s");
    }

    /**
     * Builds the action hint for available nodes.
     */
    private String buildActionHint() {
        return """
            <div style="anchor-horizontal: 0; background-color: #2a4a2a; layout-mode: MiddleCenter; padding: 8;">
                <p style="font-size: 13; color: %s; horizontal-align: center;">Press F to allocate this node</p>
            </div>
            <div style="anchor-height: 8;"></div>
            """.formatted(RPGStyles.POSITIVE);
    }

    /**
     * Builds the close button.
     */
    private String buildCloseButton() {
        return """
            <div style="anchor-height: 8;"></div>
            <div style="layout-mode: Center; anchor-horizontal: 0;">
                <button class="secondary-button" id="close-btn" style="anchor-width: 100; anchor-height: 32;">Close</button>
            </div>
            """;
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

    /**
     * Gets the type badge text based on node type.
     */
    private String getTypeBadge() {
        if (node.isKeystone()) return "KEYSTONE";
        if (node.isNotable()) return "NOTABLE";
        if (node.isStartNode() || "origin".equals(node.getId())) return "ORIGIN";
        return "BASIC";
    }

    /**
     * Gets the type badge color based on node type.
     */
    private String getTypeBadgeColor() {
        if (node.isKeystone()) return RPGStyles.TITLE_GOLD;
        if (node.isNotable()) return RPGStyles.TEXT_INFO;
        if (node.isStartNode() || "origin".equals(node.getId())) return RPGStyles.TITLE_GOLD;
        return RPGStyles.TEXT_GRAY;
    }

    /**
     * Formats a stat name for display using centralized StatType names.
     */
    private String formatStatName(String stat) {
        if (stat == null || stat.isEmpty()) return "???";
        return StatType.getDisplayNameFor(stat);
    }

    /**
     * Formats a conditional trigger for display.
     */
    private String formatTrigger(ConditionalTrigger trigger) {
        if (trigger == null) return "Unknown";
        return switch (trigger) {
            case ON_KILL -> "On Kill";
            case ON_CRIT -> "On Critical Hit";
            case WHEN_HIT -> "When Hit";
            case LOW_LIFE -> "Low Life";
            case FULL_LIFE -> "Full Life";
            case FULL_MANA -> "Full Mana";
            case LOW_MANA -> "Low Mana";
            case ON_SKILL_USE -> "On Skill Use";
            case ON_BLOCK -> "On Block";
            case ON_EVADE -> "On Evade";
            case WHILE_MOVING -> "While Moving";
            case WHILE_STATIONARY -> "While Stationary";
            case WHILE_BUFFED -> "While Buffed";
            case ON_INFLICT_STATUS -> "On Inflict Status";
        };
    }

    /**
     * Escapes HTML special characters.
     */
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }
}
