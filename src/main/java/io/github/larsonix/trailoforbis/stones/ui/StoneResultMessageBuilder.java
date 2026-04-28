package io.github.larsonix.trailoforbis.stones.ui;

import com.hypixel.hytale.server.core.Message;

import io.github.larsonix.trailoforbis.gear.model.GearData;
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

        // 8. Gear-specific: implicit damage change
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
        computeModifierDiff(before.modifiers(), after.modifiers(), lines);

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
     */
    private static void computeModifierDiff(
            @Nonnull List<? extends ItemModifier> beforeMods,
            @Nonnull List<? extends ItemModifier> afterMods,
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
                lines.add(buildModifierRemoved(beforeMod));
            } else {
                unmatchedAfter.remove(beforeMod.id());

                // Check value change
                if (Math.abs(beforeMod.getValue() - afterMod.getValue()) > 0.001) {
                    lines.add(buildModifierValueChange(beforeMod, afterMod));
                }

                // Check lock state change
                if (beforeMod.isLocked() != afterMod.isLocked()) {
                    if (afterMod.isLocked()) {
                        lines.add(buildModifierLocked(afterMod));
                    } else {
                        lines.add(buildModifierUnlocked(afterMod));
                    }
                }
            }
        }

        // Remaining unmatched after mods = newly added
        for (ItemModifier addedMod : unmatchedAfter.values()) {
            lines.add(buildModifierAdded(addedMod));
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
    private static Message buildModifierAdded(@Nonnull ItemModifier mod) {
        return Message.raw("Added : ").color(COLOR_LABEL)
            .insert(Message.raw(formatModifierValue(mod)).color(COLOR_ADDED));
    }

    /**
     * Builds "Removed : +X.X StatName" in red.
     */
    @Nonnull
    private static Message buildModifierRemoved(@Nonnull ItemModifier mod) {
        return Message.raw("Removed : ").color(COLOR_LABEL)
            .insert(Message.raw(formatModifierValue(mod)).color(COLOR_REMOVED));
    }

    /**
     * Builds "+oldValue StatName -> +newValue StatName" showing value reroll.
     */
    @Nonnull
    private static Message buildModifierValueChange(
            @Nonnull ItemModifier before,
            @Nonnull ItemModifier after) {
        return Message.raw(formatModifierValue(before)).color(COLOR_VALUE)
            .insert(Message.raw(" -> ").color(COLOR_ARROW))
            .insert(Message.raw(formatModifierValue(after)).color(COLOR_ADDED));
    }

    /**
     * Builds "Locked : +X.X StatName" in teal.
     */
    @Nonnull
    private static Message buildModifierLocked(@Nonnull ItemModifier mod) {
        return Message.raw("Locked : ").color(COLOR_LABEL)
            .insert(Message.raw(formatModifierValue(mod)).color(TooltipStyles.LOCKED_MODIFIER));
    }

    /**
     * Builds "Unlocked : +X.X StatName" in green.
     */
    @Nonnull
    private static Message buildModifierUnlocked(@Nonnull ItemModifier mod) {
        return Message.raw("Unlocked : ").color(COLOR_LABEL)
            .insert(Message.raw(formatModifierValue(mod)).color(COLOR_ADDED));
    }

    // ═══════════════════════════════════════════════════════════════════
    // FORMATTING
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Formats a modifier as "+X.X StatName" or "+X.X% StatName".
     *
     * <p>Uses the modifier's own {@link ItemModifier#formatForTooltip()} if
     * available, otherwise builds from value + displayName.
     */
    @Nonnull
    private static String formatModifierValue(@Nonnull ItemModifier mod) {
        double value = mod.getValue();
        String sign = value >= 0 ? "+" : "";
        String displayName = mod.displayName();

        if (mod.isPercent()) {
            // Percent modifier: "+10.5% Attack Speed"
            if (Math.abs(value) >= 100 || Math.abs(value - Math.round(value)) < 0.05) {
                return sign + Math.round(value) + "% " + displayName;
            }
            return sign + String.format("%.1f", value) + "% " + displayName;
        } else {
            // Flat modifier: "+25 Physical Damage"
            if (Math.abs(value - Math.round(value)) < 0.05) {
                return sign + Math.round(value) + " " + displayName;
            }
            return sign + String.format("%.1f", value) + " " + displayName;
        }
    }

    private StoneResultMessageBuilder() {
        throw new UnsupportedOperationException("Utility class");
    }
}
