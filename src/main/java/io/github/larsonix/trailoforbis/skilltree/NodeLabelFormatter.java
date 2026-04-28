package io.github.larsonix.trailoforbis.skilltree;

import io.github.larsonix.trailoforbis.skilltree.config.SkillNode;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Utility class for formatting skill tree node labels.
 *
 * <p>Provides methods to extract short, meaningful labels from full node names
 * for display on skill tree buttons. Labels are tailored to fit within button
 * constraints while remaining informative.
 *
 * <p><b>Label Strategies:</b>
 * <ul>
 *   <li><b>Basic nodes (Tier 1-3):</b> First word of name (e.g., "Brute Force I" → "Brute")</li>
 *   <li><b>Notable nodes (Tier 4):</b> First word, up to 8 chars (e.g., "Brutal Efficiency" → "Brutal")</li>
 *   <li><b>Keystone nodes (Tier 5):</b> First word, up to 10 chars (e.g., "Berserker's Fury" → "Berserker")</li>
 *   <li><b>Entry nodes:</b> "Path" (e.g., "Path of Strength" → "Path")</li>
 *   <li><b>Bridge nodes:</b> Arrow notation (e.g., "-> Dexterity" → "→Dex")</li>
 *   <li><b>Origin:</b> "Origin"</li>
 * </ul>
 */
public final class NodeLabelFormatter {

    // Maximum label lengths for different node types
    private static final int MAX_BASIC_LABEL = 6;
    private static final int MAX_NOTABLE_LABEL = 8;
    private static final int MAX_KEYSTONE_LABEL = 10;

    private NodeLabelFormatter() {
        // Utility class - no instantiation
    }

    /**
     * Extracts a short button label from a skill node.
     *
     * @param node The skill node to extract a label from
     * @return A short label suitable for button display
     */
    @Nonnull
    public static String getButtonLabel(@Nonnull SkillNode node) {
        String name = node.getName();
        if (name == null || name.isEmpty()) {
            return "?";
        }

        // Handle special cases first
        if (node.isStartNode()) {
            return "Origin";
        }

        // Handle bridge nodes (names starting with "->")
        if (name.startsWith("->") || name.startsWith("→")) {
            return formatBridgeLabel(name);
        }

        // Handle entry/path nodes
        if (name.startsWith("Path of")) {
            return "Path";
        }

        // Handle keystone nodes
        if (node.isKeystone()) {
            return extractFirstWord(name, MAX_KEYSTONE_LABEL);
        }

        // Handle notable nodes
        if (node.isNotable()) {
            return extractFirstWord(name, MAX_NOTABLE_LABEL);
        }

        // Basic nodes - first word only
        return extractFirstWord(name, MAX_BASIC_LABEL);
    }

    /**
     * Gets a formatted label including an allocation indicator.
     *
     * @param node The skill node
     * @param isAllocated Whether the node is allocated
     * @return Label with optional allocation checkmark
     */
    @Nonnull
    public static String getButtonLabelWithStatus(@Nonnull SkillNode node, boolean isAllocated) {
        String label = getButtonLabel(node);
        if (isAllocated) {
            return label;
        }
        return label;
    }

    /**
     * Formats a bridge node label (e.g., "-> Dexterity" → "→Dex").
     */
    @Nonnull
    private static String formatBridgeLabel(@Nonnull String name) {
        // Extract the target region name
        String target = name.replaceFirst("^->\\s*", "")
                           .replaceFirst("^→\\s*", "")
                           .trim();

        // Abbreviate common region names
        return switch (target.toLowerCase()) {
            case "strength" -> ">Str";
            case "dexterity" -> ">Dex";
            case "intelligence" -> ">Int";
            case "vitality" -> ">Vit";
            default -> ">" + abbreviate(target, 3);
        };
    }

    /**
     * Extracts the first word from a name, truncated to maxLength.
     *
     * <p>Handles special cases:
     * <ul>
     *   <li>Roman numerals (I, II, III) are removed</li>
     *   <li>Possessives (e.g., "Berserker's") keep the root word</li>
     * </ul>
     */
    @Nonnull
    private static String extractFirstWord(@Nonnull String name, int maxLength) {
        // Split on spaces
        String[] words = name.split("\\s+");
        if (words.length == 0) {
            return "?";
        }

        String firstWord = words[0];

        // Remove possessive suffix ('s)
        if (firstWord.endsWith("'s")) {
            firstWord = firstWord.substring(0, firstWord.length() - 2);
        }

        // Truncate if needed
        if (firstWord.length() > maxLength) {
            return firstWord.substring(0, maxLength);
        }

        return firstWord;
    }

    /**
     * Abbreviates a string to the specified length.
     */
    @Nonnull
    private static String abbreviate(@Nonnull String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

    // ═══════════════════════════════════════════════════════════════════
    // TOOLTIP FORMATTING
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Formats a node type indicator for tooltip display.
     *
     * @param node The skill node
     * @return Type indicator string (e.g., "[KEYSTONE]", "[NOTABLE]", or "")
     */
    @Nonnull
    public static String getTypeIndicator(@Nonnull SkillNode node) {
        if (node.isKeystone()) {
            return " *";  // Star for keystone
        } else if (node.isNotable()) {
            return " +";  // Diamond for notable
        }
        return "";
    }

    /**
     * Formats a status indicator for tooltip display.
     *
     * @param isAllocated Whether the node is allocated
     * @param canAllocate Whether the node can be allocated
     * @return Status string (e.g., "● ALLOCATED", "○ AVAILABLE", "◌ LOCKED")
     */
    @Nonnull
    public static String getStatusIndicator(boolean isAllocated, boolean canAllocate) {
        if (isAllocated) {
            return "● ALLOCATED";
        } else if (canAllocate) {
            return "○ AVAILABLE";
        } else {
            return "◌ LOCKED";
        }
    }

    /**
     * Formats a tier indicator for tooltip display.
     *
     * @param tier The node tier (0-5)
     * @return Tier indicator (e.g., "Tier 1", "Origin", "Keystone")
     */
    @Nonnull
    public static String getTierIndicator(int tier) {
        return switch (tier) {
            case 0 -> "Origin";
            case 1, 2, 3 -> "Tier " + tier;
            case 4 -> "Notable";
            case 5 -> "Keystone";
            default -> "Tier " + tier;
        };
    }

    // ═══════════════════════════════════════════════════════════════════
    // NAMEPLATE FORMATTING (3D Skill Tree World)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Maximum number of modifiers to display on the subtitle nameplate.
     * If a node has more than this, show "Click for details" instead.
     */
    private static final int MAX_MODIFIERS_ON_NAMEPLATE = 2;

    /**
     * Gets the primary nameplate text (node name only).
     *
     * @param node The skill node
     * @return The node name for the primary nameplate
     */
    @Nonnull
    public static String getNameplateText(@Nonnull SkillNode node) {
        String name = node.getName();
        return (name == null || name.isEmpty()) ? "?" : name;
    }

    /**
     * Gets the subtitle text for a skill node's secondary nameplate.
     *
     * <p>Returns:
     * <ul>
     *   <li><b>Origin:</b> null (no subtitle)</li>
     *   <li><b>Basic nodes with 1-2 modifiers:</b> Modifiers (e.g., "+5 HP | +3% Crit")</li>
     *   <li><b>Basic nodes with 3+ modifiers:</b> "Click for details"</li>
     *   <li><b>Notable/Keystone nodes:</b> "Click for details"</li>
     *   <li><b>Nodes with no modifiers:</b> null (no subtitle)</li>
     * </ul>
     *
     * @param node The skill node
     * @return Subtitle text, or null if no subtitle should be shown
     */
    @Nullable
    public static String getSubtitleText(@Nonnull SkillNode node) {
        // Origin nodes - no subtitle
        if (node.isStartNode()) {
            return null;
        }

        // Notable and Keystone nodes always show "Click for details"
        if (node.isNotable() || node.isKeystone()) {
            return "Click for details";
        }

        // For basic nodes, check modifier count
        var modifiers = node.getModifiers();
        if (modifiers.isEmpty()) {
            return null;
        }

        // If more than MAX_MODIFIERS_ON_NAMEPLATE, show hint instead
        if (modifiers.size() > MAX_MODIFIERS_ON_NAMEPLATE) {
            return "Click for details";
        }

        // Build subtitle from modifiers (pipe-separated)
        StringBuilder subtitle = new StringBuilder();
        for (int i = 0; i < modifiers.size(); i++) {
            if (i > 0) {
                subtitle.append(" | ");
            }
            subtitle.append(modifiers.get(i).toShortString());
        }

        return subtitle.toString();
    }
}
