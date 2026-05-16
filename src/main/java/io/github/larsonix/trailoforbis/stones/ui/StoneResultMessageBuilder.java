package io.github.larsonix.trailoforbis.stones.ui;

import com.hypixel.hytale.server.core.Message;

import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.model.GearModifier;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.gear.tooltip.TooltipStyles;
import io.github.larsonix.trailoforbis.maps.core.RealmMapData;
import io.github.larsonix.trailoforbis.stones.ItemModifier;
import io.github.larsonix.trailoforbis.stones.ModifiableItem;
import io.github.larsonix.trailoforbis.stones.StoneType;
import io.github.larsonix.trailoforbis.ui.RPGStyles;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds rich chat messages showing what a stone changed on an item.
 *
 * <p>Computes a before/after diff of the {@link ModifiableItem} and formats
 * each change as a colored {@link Message} line. The caller provides the
 * stone type (for the header prefix) and both the before and after item states.
 *
 * <p>Modifier values are quality-adjusted to match the tooltip display:
 * {@code displayValue = rawValue * qualityMultiplier}. This ensures the
 * numbers in chat feedback match exactly what the player sees on the item.
 *
 * <p>All formatting uses the project's standard color constants from
 * {@link RPGStyles} and {@link TooltipStyles}.
 */
public final class StoneResultMessageBuilder {

    // Colors for diff display
    private static final String COLOR_ADDED = "#55FF55";     // Green — new/improved
    private static final String COLOR_REMOVED = "#FF5555";   // Red — removed/downgraded
    private static final String COLOR_LABEL = "#888888";     // Gray — labels
    private static final String COLOR_VALUE = "#FFFFFF";     // White — values
    private static final String COLOR_ARROW = "#888888";     // Gray — arrows
    private static final String COLOR_RARITY_UP = "#FFD700"; // Gold — rarity upgrade

    /**
     * Display name overrides for stats whose auto-formatted name is misleading.
     *
     * <p>Must match {@code RichTooltipFormatter.STAT_DISPLAY_OVERRIDES} exactly
     * so chat feedback uses the same stat names as item tooltips.
     */
    private static final Map<String, String> STAT_DISPLAY_OVERRIDES = Map.of(
        "stamina_regen_start_delay", "Stamina Recovery Speed",
        "energy_shield_regen_delay", "ES Regen Delay"
    );

    /**
     * Notification content for toast display.
     *
     * @param primary Stone name in rarity color (bold)
     * @param secondary All diff changes joined by newlines
     */
    public record NotificationContent(@Nonnull Message primary, @Nonnull Message secondary) {}

    // ═══════════════════════════════════════════════════════════════════
    // NOTIFICATION BUILDERS (toast display)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Builds compact notification content for a successful stone application.
     *
     * <p>Primary: stone display name in rarity color, bold.
     * Secondary: all diff lines joined by newlines. Falls back to the legacy
     * message if no diff could be computed.
     *
     * @param stoneType The stone that was applied
     * @param before The item state before the stone
     * @param after The item state after the stone (null falls back to legacy message)
     * @param legacyMessage Fallback message if diff can't be computed
     * @return Notification content ready for NotificationUtil
     */
    @Nonnull
    public static NotificationContent buildNotification(
            @Nonnull StoneType stoneType,
            @Nonnull ModifiableItem before,
            @Nullable ModifiableItem after,
            @Nonnull String legacyMessage) {

        Message primary = Message.raw(stoneType.getDisplayName())
            .color(stoneType.getHexColor())
            .bold(true);

        if (after == null) {
            return new NotificationContent(primary, Message.raw(legacyMessage).color(COLOR_VALUE));
        }

        List<Message> lines = computeDiffLines(before, after);

        if (lines.isEmpty()) {
            return new NotificationContent(primary, Message.raw(legacyMessage).color(COLOR_VALUE));
        }

        Message secondary = lines.get(0);
        for (int i = 1; i < lines.size(); i++) {
            secondary = secondary.insert(Message.raw("\n")).insert(lines.get(i));
        }

        return new NotificationContent(primary, secondary);
    }

    /**
     * Builds compact notification content for a failed stone application.
     *
     * <p>Primary: stone display name in rarity color, bold.
     * Secondary: failure reason in red.
     *
     * @param stoneType The stone that failed
     * @param failureMessage Why it failed
     * @return Notification content ready for NotificationUtil
     */
    @Nonnull
    public static NotificationContent buildFailureNotification(
            @Nonnull StoneType stoneType,
            @Nonnull String failureMessage) {

        Message primary = Message.raw(stoneType.getDisplayName())
            .color(stoneType.getHexColor())
            .bold(true);
        Message secondary = Message.raw(failureMessage).color(COLOR_REMOVED);

        return new NotificationContent(primary, secondary);
    }

    // ═══════════════════════════════════════════════════════════════════
    // CHAT MESSAGE BUILDER (legacy — kept for chat log detail if needed)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Builds a rich message describing what a stone changed.
     *
     * @param stoneType The stone that was applied
     * @param before The item state before the stone
     * @param after The item state after the stone (null falls back to legacy message)
     * @param legacyMessage Fallback message if diff can't be computed
     * @return Rich formatted Message ready to send to the player
     */
    @Nonnull
    public static Message build(
            @Nonnull StoneType stoneType,
            @Nonnull ModifiableItem before,
            @Nullable ModifiableItem after,
            @Nonnull String legacyMessage) {

        if (after == null) {
            return buildHeader(stoneType).insert(Message.raw(legacyMessage).color(COLOR_ADDED));
        }

        List<Message> lines = computeDiffLines(before, after);

        if (lines.isEmpty()) {
            // No detectable changes — fall back to legacy
            return buildHeader(stoneType).insert(Message.raw(legacyMessage).color(COLOR_ADDED));
        }

        Message result = buildHeader(stoneType);
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                result = result.insert(Message.raw("\n    "));
            }
            result = result.insert(lines.get(i));
        }
        return result;
    }

    /**
     * Builds the "[StoneName] " header prefix with stone color.
     */
    @Nonnull
    private static Message buildHeader(@Nonnull StoneType stoneType) {
        return Message.raw("[").color(RPGStyles.TITLE_GOLD)
            .insert(Message.raw(stoneType.getDisplayName()).color(stoneType.getHexColor()))
            .insert(Message.raw("] ").color(RPGStyles.TITLE_GOLD));
    }

    // ═══════════════════════════════════════════════════════════════════
    // DIFF COMPUTATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Computes all diff lines between before and after states.
     * Returns an ordered list of Message lines describing each change.
     */
    @Nonnull
    private static List<Message> computeDiffLines(
            @Nonnull ModifiableItem before,
            @Nonnull ModifiableItem after) {

        List<Message> lines = new ArrayList<>();

        // Quality multiplier for modifier value display.
        // Gear: quality/100 + 0.5 (range 0.51-1.51). Maps: 1.0 (no quality scaling on modifiers).
        // Uses the AFTER state so values match the updated tooltip.
        double qualityMultiplier = (after instanceof GearData gearAfter)
            ? gearAfter.qualityMultiplier()
            : 1.0;

        // 1. Corruption change (show first — it's the most dramatic)
        if (!before.corrupted() && after.corrupted()) {
            lines.add(Message.raw("Corrupted !").color(COLOR_REMOVED).bold(true));
        }

        // 2. Rarity change
        if (before.rarity() != after.rarity()) {
            lines.add(buildRarityChange(before.rarity(), after.rarity()));
        }

        // 3. Identification change
        if (!before.isIdentified() && after.isIdentified()) {
            int modCount = after.modifiers().size();
            lines.add(Message.raw("Identified ! " + modCount + " modifiers revealed").color(COLOR_ADDED));
        }

        // 4. Level change
        if (before.level() != after.level()) {
            lines.add(buildValueChange("Level", String.valueOf(before.level()), String.valueOf(after.level())));
        }

        // 5. Quality change
        if (before.quality() != after.quality()) {
            String afterStr = after.quality() == 101
                ? "101% (Perfect)"
                : after.quality() + "%";
            lines.add(buildValueChange("Quality", before.quality() + "%", afterStr));
        }

        // 6. Map-specific: biome change
        if (before instanceof RealmMapData beforeMap && after instanceof RealmMapData afterMap) {
            if (beforeMap.biome() != afterMap.biome()) {
                lines.add(buildValueChange("Biome",
                    beforeMap.biome().getDisplayName(),
                    afterMap.biome().getDisplayName()));
            }

            // 7. Map-specific: Fortune's Compass bonus
            if (beforeMap.fortunesCompassBonus() != afterMap.fortunesCompassBonus()) {
                lines.add(buildValueChange("Item Quantity",
                    "+" + beforeMap.fortunesCompassBonus() + "%",
                    "+" + afterMap.fortunesCompassBonus() + "%"));
            }
        }

        // 8. Gear-specific: implicit damage change (no quality adjustment — implicits are fixed base stats)
        if (before instanceof GearData beforeGear && after instanceof GearData afterGear) {
            if (beforeGear.hasWeaponImplicit() && afterGear.hasWeaponImplicit()) {
                int oldVal = beforeGear.implicit().rolledValueAsInt();
                int newVal = afterGear.implicit().rolledValueAsInt();
                if (oldVal != newVal) {
                    String statName = afterGear.implicit().damageTypeDisplayName();
                    lines.add(buildValueChange(statName,
                        String.valueOf(oldVal), String.valueOf(newVal)));
                }
            }
            if (beforeGear.hasArmorImplicit() && afterGear.hasArmorImplicit()) {
                int oldVal = beforeGear.armorImplicit().rolledValueAsInt();
                int newVal = afterGear.armorImplicit().rolledValueAsInt();
                if (oldVal != newVal) {
                    String statName = afterGear.armorImplicit().defenseTypeDisplayName();
                    lines.add(buildValueChange(statName,
                        String.valueOf(oldVal), String.valueOf(newVal)));
                }
            }
        }

        // 9. Modifier diff (added, removed, value changes, lock changes)
        computeModifierDiff(before.modifiers(), after.modifiers(), qualityMultiplier, lines);

        return lines;
    }

    // ═══════════════════════════════════════════════════════════════════
    // MODIFIER DIFF
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Computes modifier-level diffs: added, removed, value changed, lock changed.
     *
     * <p>Matching strategy: match by modifier ID. If an ID exists in both before
     * and after, check for value/lock changes. If only in before, it was removed.
     * If only in after, it was added.
     *
     * @param qualityMultiplier Quality adjustment for display values (gear: 0.51-1.51, maps: 1.0)
     */
    private static void computeModifierDiff(
            @Nonnull List<? extends ItemModifier> beforeMods,
            @Nonnull List<? extends ItemModifier> afterMods,
            double qualityMultiplier,
            @Nonnull List<Message> lines) {

        // Index after mods by ID for O(1) lookup
        Map<String, ItemModifier> afterById = new LinkedHashMap<>();
        for (ItemModifier mod : afterMods) {
            afterById.put(mod.id(), mod);
        }

        // Track which after mods were matched (remaining = added)
        Map<String, ItemModifier> unmatchedAfter = new LinkedHashMap<>(afterById);

        // Compare before -> after
        for (ItemModifier beforeMod : beforeMods) {
            ItemModifier afterMod = afterById.get(beforeMod.id());

            if (afterMod == null) {
                // Modifier was removed
                lines.add(buildModifierRemoved(beforeMod, qualityMultiplier));
            } else {
                unmatchedAfter.remove(beforeMod.id());

                // Check value change (raw comparison — any real change should be reported)
                if (Math.abs(beforeMod.getValue() - afterMod.getValue()) > 0.001) {
                    lines.add(buildModifierValueChange(beforeMod, afterMod, qualityMultiplier));
                }

                // Check lock state change
                if (beforeMod.isLocked() != afterMod.isLocked()) {
                    if (afterMod.isLocked()) {
                        lines.add(buildModifierLocked(afterMod, qualityMultiplier));
                    } else {
                        lines.add(buildModifierUnlocked(afterMod, qualityMultiplier));
                    }
                }
            }
        }

        // Remaining unmatched after mods = newly added
        for (ItemModifier addedMod : unmatchedAfter.values()) {
            lines.add(buildModifierAdded(addedMod, qualityMultiplier));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // MESSAGE BUILDERS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Builds "OldRarity -> NewRarity" with rarity colors.
     */
    @Nonnull
    private static Message buildRarityChange(@Nonnull GearRarity from, @Nonnull GearRarity to) {
        return Message.raw(from.getHytaleQualityId()).color(TooltipStyles.getRarityColor(from))
            .insert(Message.raw(" -> ").color(COLOR_ARROW))
            .insert(Message.raw(to.getHytaleQualityId()).color(TooltipStyles.getRarityColor(to)));
    }

    /**
     * Builds "Label : oldValue -> newValue".
     */
    @Nonnull
    private static Message buildValueChange(
            @Nonnull String label,
            @Nonnull String oldValue,
            @Nonnull String newValue) {
        return Message.raw(label + " : ").color(COLOR_LABEL)
            .insert(Message.raw(oldValue).color(COLOR_VALUE))
            .insert(Message.raw(" -> ").color(COLOR_ARROW))
            .insert(Message.raw(newValue).color(COLOR_VALUE));
    }

    /**
     * Builds "Added : +X.X StatName" in green.
     */
    @Nonnull
    private static Message buildModifierAdded(@Nonnull ItemModifier mod, double qualityMultiplier) {
        return Message.raw("Added : ").color(COLOR_LABEL)
            .insert(Message.raw(formatModifierValue(mod, qualityMultiplier)).color(COLOR_ADDED));
    }

    /**
     * Builds "Removed : +X.X StatName" in red.
     */
    @Nonnull
    private static Message buildModifierRemoved(@Nonnull ItemModifier mod, double qualityMultiplier) {
        return Message.raw("Removed : ").color(COLOR_LABEL)
            .insert(Message.raw(formatModifierValue(mod, qualityMultiplier)).color(COLOR_REMOVED));
    }

    /**
     * Builds "+oldValue StatName -> +newValue StatName" showing value reroll.
     */
    @Nonnull
    private static Message buildModifierValueChange(
            @Nonnull ItemModifier before,
            @Nonnull ItemModifier after,
            double qualityMultiplier) {
        return Message.raw(formatModifierValue(before, qualityMultiplier)).color(COLOR_VALUE)
            .insert(Message.raw(" -> ").color(COLOR_ARROW))
            .insert(Message.raw(formatModifierValue(after, qualityMultiplier)).color(COLOR_ADDED));
    }

    /**
     * Builds "Locked : +X.X StatName" in teal.
     */
    @Nonnull
    private static Message buildModifierLocked(@Nonnull ItemModifier mod, double qualityMultiplier) {
        return Message.raw("Locked : ").color(COLOR_LABEL)
            .insert(Message.raw(formatModifierValue(mod, qualityMultiplier)).color(TooltipStyles.LOCKED_MODIFIER));
    }

    /**
     * Builds "Unlocked : +X.X StatName" in green.
     */
    @Nonnull
    private static Message buildModifierUnlocked(@Nonnull ItemModifier mod, double qualityMultiplier) {
        return Message.raw("Unlocked : ").color(COLOR_LABEL)
            .insert(Message.raw(formatModifierValue(mod, qualityMultiplier)).color(COLOR_ADDED));
    }

    // ═══════════════════════════════════════════════════════════════════
    // FORMATTING
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Formats a modifier as "+X.X StatName" or "+X.X% StatName" with quality adjustment.
     *
     * <p>The displayed value matches the item tooltip exactly:
     * {@code displayValue = rawValue * qualityMultiplier}. For gear modifiers,
     * the stat name is derived from {@code statId} (matching the tooltip's format)
     * rather than the thematic display name (e.g., "Physical Damage" not "Sharp").
     *
     * <p>Formatting mirrors {@code RichTooltipFormatter.formatModifierValue()} to
     * ensure identical number display between chat feedback and item tooltips.
     *
     * @param mod The modifier to format
     * @param qualityMultiplier Quality adjustment (gear: 0.51-1.51, maps: 1.0)
     */
    @Nonnull
    private static String formatModifierValue(@Nonnull ItemModifier mod, double qualityMultiplier) {
        double value = mod.getValue() * qualityMultiplier;
        String sign = value >= 0 ? "+" : "";
        String displayName = getModifierDisplayName(mod);

        if (mod.isPercent()) {
            // Percent modifier: "+10.5% Attack Speed"
            // Matches RichTooltipFormatter: integer for >=100, one decimal otherwise
            if (Math.abs(value) >= 100) {
                return sign + Math.round(value) + "% " + displayName;
            }
            return sign + String.format("%.1f", value) + "% " + displayName;
        } else {
            // Flat modifier: "+25 Physical Damage"
            // Matches RichTooltipFormatter: integer if close to round, one decimal otherwise
            if (Math.abs(value - Math.round(value)) < 0.05) {
                return sign + Math.round(value) + " " + displayName;
            }
            return sign + String.format("%.1f", value) + " " + displayName;
        }
    }

    /**
     * Gets the display name for a modifier, matching the tooltip's format.
     *
     * <p>For gear modifiers: uses statId converted to Title Case (e.g., "physical_damage"
     * -> "Physical Damage"), with overrides for misleading names. Strips "_percent" suffix
     * from percent modifiers to avoid redundancy like "+2.4% Physical Damage Percent".
     *
     * <p>For other modifiers (realm maps): uses the modifier's own display name as-is,
     * since map modifier display names are already player-facing labels.
     */
    @Nonnull
    private static String getModifierDisplayName(@Nonnull ItemModifier mod) {
        if (mod instanceof GearModifier gearMod) {
            String statId = gearMod.statId();

            // Check display overrides first (matches RichTooltipFormatter)
            String override = STAT_DISPLAY_OVERRIDES.get(statId.toLowerCase());
            if (override != null) {
                return override;
            }

            // Strip "_percent" suffix for percent modifiers (avoids "Physical Damage Percent")
            if (gearMod.isPercent() && statId.endsWith("_percent")) {
                statId = statId.substring(0, statId.length() - "_percent".length());
            }

            return formatStatId(statId);
        }

        return mod.displayName();
    }

    /**
     * Formats a stat ID as a human-readable display name.
     *
     * <p>Converts snake_case to Title Case: "physical_damage" -> "Physical Damage".
     * Matches the same logic in {@code RichTooltipFormatter.formatStatId()}.
     */
    @Nonnull
    private static String formatStatId(@Nonnull String statId) {
        String[] parts = statId.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!sb.isEmpty()) sb.append(" ");
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) {
                    sb.append(part.substring(1).toLowerCase());
                }
            }
        }
        return sb.toString();
    }

    private StoneResultMessageBuilder() {
        throw new UnsupportedOperationException("Utility class");
    }
}
