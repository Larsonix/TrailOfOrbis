package io.github.larsonix.trailoforbis.combat.deathrecap;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.logger.HytaleLogger;
import io.github.larsonix.trailoforbis.api.ServiceRegistry;
import io.github.larsonix.trailoforbis.api.services.ConfigService;
import io.github.larsonix.trailoforbis.combat.avoidance.AvoidanceProcessor;
import io.github.larsonix.trailoforbis.config.RPGConfig;
import io.github.larsonix.trailoforbis.elemental.ElementType;
import io.github.larsonix.trailoforbis.mobs.classification.RPGMobClass;
import io.github.larsonix.trailoforbis.mobs.stats.MobStatGenerator;
import io.github.larsonix.trailoforbis.mobs.stats.MobStatPoolConfig;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Formats death recap messages for display in chat.
 *
 * <p>Supports two display modes:
 * <ul>
 *   <li><b>Full mode</b>: Multi-line detailed breakdown of all damage calculations</li>
 *   <li><b>Compact mode</b>: Single-line summary with key information</li>
 * </ul>
 */
public final class DeathRecapFormatter {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private DeathRecapFormatter() {
        // Utility class
    }

    /**
     * Formats a death recap message based on the config display mode.
     *
     * @param snapshot The combat snapshot from the killing blow
     * @param config The death recap configuration
     * @return Formatted message to send to the player
     */
    @Nonnull
    public static Message format(@Nonnull CombatSnapshot snapshot, @Nonnull DeathRecapConfig config) {
        return format(snapshot, null, config);
    }

    /**
     * Formats a death recap message with optional damage chain history.
     *
     * @param snapshot The combat snapshot from the killing blow
     * @param history The full damage history (most recent first), or null for no chain
     * @param config The death recap configuration
     * @return Formatted message to send to the player
     */
    @Nonnull
    public static Message format(
        @Nonnull CombatSnapshot snapshot,
        @Nullable List<CombatSnapshot> history,
        @Nonnull DeathRecapConfig config
    ) {
        if (config.isCompactMode()) {
            return formatCompact(snapshot);
        }
        return formatFull(snapshot, history, config);
    }

    /**
     * Formats a compact single-line death recap.
     *
     * <p>Example: [Death] Killed by [Elite] Trork Warrior (Lv27) - 79 dmg (CRIT! 15 base +8 flat -38% armor)
     */
    @Nonnull
    public static Message formatCompact(@Nonnull CombatSnapshot snapshot) {
        StringBuilder sb = new StringBuilder();

        // Build the compact message
        sb.append("[Death] Killed by ");
        sb.append(formatAttackerName(snapshot));
        sb.append(" - ");
        sb.append(snapshot.getCompactSummary());

        return Message.raw(sb.toString()).color(MessageColors.ERROR);
    }

    /**
     * Formats a full multi-line death recap with complete breakdown.
     */
    @Nonnull
    public static Message formatFull(@Nonnull CombatSnapshot snapshot, @Nonnull DeathRecapConfig config) {
        return formatFull(snapshot, null, config);
    }

    /**
     * Formats a full multi-line death recap with optional damage chain.
     */
    @Nonnull
    public static Message formatFull(
        @Nonnull CombatSnapshot snapshot,
        @Nullable List<CombatSnapshot> history,
        @Nonnull DeathRecapConfig config
    ) {
        Message message = Message.empty();

        // Header
        message = message.insert(Message.raw("\n").color(MessageColors.GRAY));
        message = message.insert(Message.raw("======= DEATH RECAP =======\n").color(MessageColors.ERROR));

        // Killed by line
        message = message.insert(Message.raw("Killed by : ").color(MessageColors.GRAY));
        message = message.insert(formatAttackerNameColored(snapshot));
        message = message.insert(Message.raw("\n").color(MessageColors.GRAY));

        // Damage breakdown section
        if (config.isShowDamageBreakdown() && !snapshot.wasDodged()) {
            message = message.insert(Message.raw("\n").color(MessageColors.GRAY));
            message = message.insert(Message.raw("-- Damage Breakdown --\n").color(MessageColors.WARNING));
            message = appendDamageBreakdown(message, snapshot);
        }

        // Elemental damage section
        if (config.isShowElementalDamage() && snapshot.hasElementalDamage()) {
            message = message.insert(Message.raw("\n").color(MessageColors.GRAY));
            message = message.insert(Message.raw("-- Elemental Damage --\n").color(MessageColors.WARNING));
            message = appendElementalBreakdown(message, snapshot);
        }

        // Damage chain section (timeline of recent hits)
        if (config.isShowDamageChain() && history != null && history.size() > 1) {
            message = message.insert(Message.raw("\n").color(MessageColors.GRAY));
            message = message.insert(Message.raw(
                String.format("-- Damage Timeline (last %d hits) --\n", history.size())).color(MessageColors.WARNING));
            message = appendDamageChain(message, history);
        }

        // Defensive stats section
        if (config.isShowDefensiveStats()) {
            message = message.insert(Message.raw("\n").color(MessageColors.GRAY));
            message = message.insert(Message.raw("-- Your Defenses --\n").color(MessageColors.WARNING));
            message = appendDefensiveStats(message, snapshot);
        }

        // Elemental resistances section (show if defender had any raw resistances)
        if (config.isShowDefensiveStats() && snapshot.defenderRawResistances() != null) {
            message = message.insert(Message.raw("\n").color(MessageColors.GRAY));
            message = message.insert(Message.raw("-- Your Resistances --\n").color(MessageColors.WARNING));
            message = appendResistancesSection(message, snapshot);
        }

        // Footer
        message = message.insert(Message.raw("===========================\n").color(MessageColors.ERROR));

        return message;
    }

    /** Appends the damage breakdown section. */
    @Nonnull
    private static Message appendDamageBreakdown(@Nonnull Message message, @Nonnull CombatSnapshot snapshot) {
        // Base damage
        message = message.insert(Message.raw(String.format("Base Damage :    %.1f\n", snapshot.baseDamage())).color(MessageColors.WHITE));

        // Flat bonus
        if (snapshot.flatBonus() > 0) {
            message = message.insert(Message.raw(String.format("+ Flat Bonus :   +%.1f\n", snapshot.flatBonus())).color(MessageColors.SUCCESS));
        }

        // Percent bonus
        if (snapshot.percentBonus() > 0) {
            float percentAdd = snapshot.baseDamage() * (snapshot.percentBonus() / 100f);
            message = message.insert(Message.raw(String.format("+ %% Bonus :      +%.0f%% (+%.1f)\n", snapshot.percentBonus(), percentAdd)).color(MessageColors.SUCCESS));
        }

        // Critical hit
        if (snapshot.wasCritical()) {
            message = message.insert(Message.raw(String.format("* CRITICAL :     x%.1f (%.1f)\n", snapshot.critMultiplier(), snapshot.damageAfterAttackerBonuses())).color(MessageColors.GOLD));
        }

        // Armor reduction (back-calculate physical-only blocked amount from exact post-armor value)
        if (snapshot.reductionPercent() > 0) {
            float redFraction = snapshot.reductionPercent() / 100f;
            float armorReduced = snapshot.damageAfterArmor() * redFraction / (1f - redFraction);
            if (snapshot.armorPenetration() > 0) {
                message = message.insert(Message.raw(String.format("- Armor (%.0f) :  -%.0f%% (-%.1f) [%.0f%% pen]\n",
                    snapshot.defenderArmor(), snapshot.reductionPercent(), armorReduced, snapshot.armorPenetration())).color(MessageColors.ERROR));
            } else {
                message = message.insert(Message.raw(String.format("- Armor (%.0f) :  -%.0f%% (-%.1f)\n",
                    snapshot.defenderArmor(), snapshot.reductionPercent(), armorReduced)).color(MessageColors.ERROR));
            }
        }

        // Total elemental (summary line)
        if (snapshot.totalElementalDamage() > 0) {
            message = message.insert(Message.raw(String.format("+ Elemental :    +%.1f\n", snapshot.totalElementalDamage())).color(MessageColors.PURPLE));
        }

        // Separator and final
        message = message.insert(Message.raw("--------------------\n").color(MessageColors.GRAY));
        message = message.insert(Message.raw(String.format("Final :          %.1f / %.1f HP\n",
            snapshot.finalDamage(), snapshot.defenderHealthBefore())).color(MessageColors.ERROR));

        return message;
    }

    /**
     * Appends the damage chain timeline section.
     *
     * <p>Shows recent hits in reverse chronological order (oldest first, killing blow last),
     * with relative timestamps and attacker info:
     * <pre>
     *   1. Physical: 23.0 dmg from Trork Warrior        [3.2s ago]
     *   2. Fire: 15.0 dmg from [Elite] Fire Trork        [2.1s ago]
     *   3. Physical: 45.0 dmg from Trork Warrior CRIT!   [KILLING BLOW]
     * </pre>
     */
    @Nonnull
    private static Message appendDamageChain(@Nonnull Message message, @Nonnull List<CombatSnapshot> history) {
        long now = System.currentTimeMillis();

        // Display oldest first (history is most-recent-first), so reverse
        for (int i = history.size() - 1; i >= 0; i--) {
            CombatSnapshot snap = history.get(i);
            int displayNum = history.size() - i;

            String typeName = snap.damageType().name().charAt(0)
                + snap.damageType().name().substring(1).toLowerCase();
            String critMark = snap.wasCritical() ? " CRIT!" : "";
            String attacker = formatAttackerName(snap);

            String timeLabel;
            if (i == 0) {
                // Most recent = killing blow
                timeLabel = "KILLING BLOW";
            } else {
                float secondsAgo = (now - snap.timestamp()) / 1000f;
                timeLabel = String.format("%.1fs ago", secondsAgo);
            }

            String color = snap.wasCritical() ? MessageColors.GOLD : MessageColors.GRAY;
            String line = String.format("  %d. %s: %.0f dmg from %s%s  [%s]\n",
                displayNum, typeName, snap.finalDamage(), attacker, critMark, timeLabel);
            message = message.insert(Message.raw(line).color(color));
        }

        return message;
    }

    /**
     * Appends elemental damage breakdown to the message.
     *
     * <p>Shows each element's damage with resistance calculation breakdown:
     * <ul>
     *   <li>Positive resistance: "Fire: +10.0 (25% resisted, blocked 3.3)"</li>
     *   <li>Zero resistance: "Chaos: +8.0 (0% resisted)"</li>
     *   <li>Negative resistance: "Lightning: +12.0 (-20% vulnerable, +2.0 extra)"</li>
     * </ul>
     *
     * <p>The damage value shown is FINAL (after resistance). We back-calculate
     * the blocked/extra amount for display.
     */
    @Nonnull
    private static Message appendElementalBreakdown(@Nonnull Message message, @Nonnull CombatSnapshot snapshot) {
        Map<ElementType, Float> elementalDamage = snapshot.elementalDamage();
        Map<ElementType, Float> elementalResist = snapshot.elementalResist();

        if (elementalDamage == null) {
            return message;
        }

        for (Map.Entry<ElementType, Float> entry : elementalDamage.entrySet()) {
            ElementType element = entry.getKey();
            float finalDamage = entry.getValue();

            if (finalDamage <= 0) continue;

            // Get effective resistance used in calculation (after penetration)
            float effectiveResist = 0;
            if (elementalResist != null && elementalResist.containsKey(element)) {
                effectiveResist = elementalResist.get(element);
            }

            String color = getElementColor(element);
            String line = formatElementalDamageLine(element, finalDamage, effectiveResist);
            message = message.insert(Message.raw(line).color(color));
        }

        return message;
    }

    /**
     * Formats a single elemental damage line with resistance breakdown.
     *
     * <p>Calculates blocked/extra damage by back-calculating from final damage:
     * <ul>
     *   <li>Positive resist: original = final / (1 - resist/100), blocked = original - final</li>
     *   <li>Negative resist: original = final / (1 + |resist|/100), extra = final - original</li>
     * </ul>
     */
    @Nonnull
    private static String formatElementalDamageLine(@Nonnull ElementType element, float finalDamage, float effectiveResist) {
        String resistText;

        if (effectiveResist > 0) {
            // Positive resistance: player resisted damage
            // Formula: finalDamage = originalDamage * (1 - resist/100)
            // So: originalDamage = finalDamage / (1 - resist/100)
            float damageMultiplier = 1f - effectiveResist / 100f;
            float originalDamage = (damageMultiplier > 0) ? finalDamage / damageMultiplier : finalDamage;
            float blockedDamage = originalDamage - finalDamage;
            resistText = String.format("(%.0f%% resisted, blocked %.1f)", effectiveResist, blockedDamage);
        } else if (effectiveResist < 0) {
            // Negative resistance (vulnerability): player took extra damage
            // Formula: finalDamage = originalDamage * (1 + |resist|/100)
            float damageMultiplier = 1f + Math.abs(effectiveResist) / 100f;
            float originalDamage = finalDamage / damageMultiplier;
            float extraDamage = finalDamage - originalDamage;
            resistText = String.format("(%.0f%% vulnerable, +%.1f extra)", Math.abs(effectiveResist), extraDamage);
        } else {
            // Zero resistance
            resistText = "(0% resisted)";
        }

        return String.format("%s : +%.1f %s\n", element.getDisplayName(), finalDamage, resistText);
    }

    /** Appends defensive stats section. */
    @Nonnull
    private static Message appendDefensiveStats(@Nonnull Message message, @Nonnull CombatSnapshot snapshot) {
        // Armor
        if (snapshot.defenderArmor() > 0) {
            message = message.insert(Message.raw(String.format("Armor : %.0f (blocked %.0f%%)\n",
                snapshot.defenderArmor(), snapshot.reductionPercent())).color(MessageColors.INFO));
        } else {
            message = message.insert(Message.raw("Armor : 0 (no protection)\n").color(MessageColors.GRAY));
        }

        // Evasion (raw rating + computed chance vs attacker level)
        if (snapshot.defenderEvasion() > 0) {
            String chanceDetail = computeEvasionChance(snapshot.defenderEvasion(), snapshot.attackerLevel());
            if (snapshot.wasDodged()) {
                String dodgeText = (chanceDetail != null)
                    ? String.format("Evasion : %.0f (%s — dodged !)\n", snapshot.defenderEvasion(), chanceDetail)
                    : String.format("Evasion : %.0f (dodged !)\n", snapshot.defenderEvasion());
                message = message.insert(Message.raw(dodgeText).color(MessageColors.SUCCESS));
            } else {
                String missText = (chanceDetail != null)
                    ? String.format("Evasion : %.0f (%s — didn't proc)\n", snapshot.defenderEvasion(), chanceDetail)
                    : String.format("Evasion : %.0f (didn't proc)\n", snapshot.defenderEvasion());
                message = message.insert(Message.raw(missText).color(MessageColors.GRAY));
            }
        }

        return message;
    }

    /**
     * Appends the "Your Resistances" section showing all elemental resistances.
     *
     * <p>Shows all 4 element resistances regardless of whether they received damage,
     * including penetration info when the attacker had any.
     *
     * <p>Examples:
     * <ul>
     *   <li>Positive: "Fire: 25%"</li>
     *   <li>With penetration: "Chaos: 50% (30% penetrated → 20% effective)"</li>
     *   <li>Negative: "Lightning: -20% (vulnerable)"</li>
     *   <li>Zero: "Cold: 0%"</li>
     * </ul>
     */
    @Nonnull
    private static Message appendResistancesSection(@Nonnull Message message, @Nonnull CombatSnapshot snapshot) {
        Map<ElementType, Float> rawResistances = snapshot.defenderRawResistances();
        Map<ElementType, Float> penetration = snapshot.attackerPenetration();

        if (rawResistances == null) {
            return message;
        }

        for (ElementType element : ElementType.values()) {
            float rawResist = rawResistances.getOrDefault(element, 0f);
            float pen = (penetration != null) ? penetration.getOrDefault(element, 0f) : 0f;

            String color = getElementColor(element);
            String line = formatResistanceLine(element, rawResist, pen);
            message = message.insert(Message.raw(line).color(color));
        }

        return message;
    }

    /**
     * Formats a single resistance line for the "Your Resistances" section.
     *
     * @param element The element type
     * @param rawResist The defender's raw resistance value
     * @param penetration The attacker's penetration for this element
     * @return Formatted line like "Fire: 25%" or "Chaos: 50% (30% penetrated → 20% effective)"
     */
    @Nonnull
    private static String formatResistanceLine(@Nonnull ElementType element, float rawResist, float penetration) {
        String elementName = element.getDisplayName();

        if (rawResist < 0) {
            // Negative resistance = vulnerability
            return String.format("%s : %.0f%% (vulnerable)\n", elementName, rawResist);
        } else if (penetration > 0 && rawResist > 0) {
            // Has penetration that matters (and positive resistance to penetrate)
            float effectiveResist = Math.max(0, rawResist - penetration);
            return String.format("%s : %.0f%% (%.0f%% penetrated > %.0f%% effective)\n",
                elementName, rawResist, penetration, effectiveResist);
        } else if (rawResist == 0) {
            // Zero resistance
            return String.format("%s : 0%%\n", elementName);
        } else {
            // Positive resistance, no penetration
            return String.format("%s : %.0f%%\n", elementName, rawResist);
        }
    }

    /**
     * Computes the evasion chance percentage against the attacker's level.
     *
     * <p>Uses the same PoE-style hit chance formula as the stats page, but
     * with the attacker's actual level (from the combat snapshot) instead of
     * the player's own level.
     *
     * @param evasion The defender's evasion rating
     * @param attackerLevel The attacker's level
     * @return A detail string like "23.5% vs Lv.27", or null if config unavailable
     */
    @Nullable
    private static String computeEvasionChance(float evasion, int attackerLevel) {
        if (evasion <= 0 || attackerLevel < 1) return null;
        try {
            ConfigService configService = ServiceRegistry.require(ConfigService.class);
            RPGConfig rpgConfig = configService.getRPGConfig();
            if (rpgConfig == null) return null;

            RPGConfig.CombatConfig.EvasionConfig evasionConfig =
                    rpgConfig.getCombat().getEvasion();
            MobStatPoolConfig poolConfig = configService.getMobStatPoolConfig();
            if (poolConfig == null) return null;

            float refAccuracy = (float) new MobStatGenerator(poolConfig)
                    .getBaseStats(attackerLevel).accuracy();
            float hitChance = AvoidanceProcessor.calculateHitChance(
                    evasionConfig, refAccuracy, evasion);
            float evadeChance = (1f - hitChance) * 100f;
            return String.format("%.1f%% vs Lv.%d", evadeChance, attackerLevel);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to compute evasion chance for death recap");
            return null;
        }
    }

    /**
     * Formats the attacker name with mob class prefix and level.
     *
     * <p>Examples:
     * <ul>
     *   <li>Player: "PlayerName (Lv50)"</li>
     *   <li>Normal mob: "Trork Warrior (Lv27)"</li>
     *   <li>Elite mob: "[Elite] Trork Warrior (Lv27)"</li>
     *   <li>Environment: "Fall Damage"</li>
     * </ul>
     */
    @Nonnull
    public static String formatAttackerName(@Nonnull CombatSnapshot snapshot) {
        if ("environment".equals(snapshot.attackerType())) {
            return snapshot.attackerName();
        }

        StringBuilder sb = new StringBuilder();

        // Add class prefix for special mobs
        if (snapshot.attackerClass() != null && snapshot.attackerClass() != RPGMobClass.HOSTILE) {
            sb.append("[").append(formatClassName(snapshot.attackerClass())).append("] ");
        }

        // Add name
        sb.append(snapshot.attackerName());

        // Add level
        if (snapshot.attackerLevel() > 0) {
            sb.append(" (Lv").append(snapshot.attackerLevel()).append(")");
        }

        return sb.toString();
    }

    /** Formats the attacker name with colored Message components. */
    @Nonnull
    public static Message formatAttackerNameColored(@Nonnull CombatSnapshot snapshot) {
        if ("environment".equals(snapshot.attackerType())) {
            return Message.raw(snapshot.attackerName()).color(MessageColors.ORANGE);
        }

        Message message = Message.empty();

        // Add class prefix for special mobs
        if (snapshot.attackerClass() != null && snapshot.attackerClass() != RPGMobClass.HOSTILE) {
            String classColor = getClassColor(snapshot.attackerClass());
            message = message.insert(Message.raw("[" + formatClassName(snapshot.attackerClass()) + "] ").color(classColor));
        }

        // Add name
        String nameColor = "player".equals(snapshot.attackerType()) ? MessageColors.INFO : MessageColors.WHITE;
        message = message.insert(Message.raw(snapshot.attackerName()).color(nameColor));

        // Add level
        if (snapshot.attackerLevel() > 0) {
            message = message.insert(Message.raw(" (Lv" + snapshot.attackerLevel() + ")").color(MessageColors.GRAY));
        }

        return message;
    }

    /**
     * Formats a mob role name into display name.
     *
     * <p>Example: "trork_warrior" -> "Trork Warrior"
     */
    @Nonnull
    public static String formatMobName(@Nullable String roleName) {
        if (roleName == null || roleName.isEmpty()) {
            return "Unknown Entity";
        }

        return Arrays.stream(roleName.split("_"))
            .map(word -> {
                if (word.isEmpty()) return "";
                return Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase();
            })
            .collect(Collectors.joining(" "));
    }

    @Nonnull
    private static String formatClassName(@Nonnull RPGMobClass mobClass) {
        return switch (mobClass) {
            case BOSS -> "Boss";
            case ELITE -> "Elite";
            case HOSTILE -> "Hostile";
            case MINOR -> "Minor";
            case PASSIVE -> "Passive";
        };
    }

    @Nonnull
    private static String getClassColor(@Nonnull RPGMobClass mobClass) {
        return switch (mobClass) {
            case BOSS -> MessageColors.DARK_PURPLE;
            case ELITE -> MessageColors.GOLD;
            case HOSTILE -> MessageColors.ERROR;
            case MINOR -> MessageColors.WARNING;  // Yellow for minor mobs
            case PASSIVE -> MessageColors.GRAY;
        };
    }

    @Nonnull
    private static String getElementColor(@Nonnull ElementType element) {
        return switch (element) {
            case FIRE -> MessageColors.ORANGE;
            case WATER -> MessageColors.LIGHT_BLUE;
            case LIGHTNING -> MessageColors.WARNING;
            case EARTH -> MessageColors.SUCCESS;   // Green for earth
            case WIND -> MessageColors.WHITE;      // White for wind
            case VOID -> MessageColors.DARK_PURPLE;
        };
    }
}
