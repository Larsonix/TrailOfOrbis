package io.github.larsonix.trailoforbis.combat.format;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.logger.HytaleLogger;
import io.github.larsonix.trailoforbis.api.ServiceRegistry;
import io.github.larsonix.trailoforbis.api.services.ConfigService;
import io.github.larsonix.trailoforbis.combat.AttackType;
import io.github.larsonix.trailoforbis.combat.avoidance.AvoidanceProcessor;
import io.github.larsonix.trailoforbis.combat.deathrecap.CombatSnapshot;
import io.github.larsonix.trailoforbis.combat.deathrecap.DeathRecapConfig;
import io.github.larsonix.trailoforbis.config.RPGConfig;
import io.github.larsonix.trailoforbis.elemental.ElementType;
import io.github.larsonix.trailoforbis.elemental.ElementalCalculator;
import io.github.larsonix.trailoforbis.mobs.classification.RPGMobClass;
import io.github.larsonix.trailoforbis.mobs.stats.MobStatFactory;
import io.github.larsonix.trailoforbis.mobs.stats.MobStatPoolConfig;
import io.github.larsonix.trailoforbis.util.MessageColors;
import io.github.larsonix.trailoforbis.util.NumberFormatter;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

import static io.github.larsonix.trailoforbis.combat.format.CombatFormatConstants.*;

/**
 * Formats death recap messages for display in chat.
 *
 * <p>Supports two display modes:
 * <ul>
 *   <li><b>Full mode</b>: Multi-line detailed breakdown with damage timeline</li>
 *   <li><b>Compact mode</b>: Single-line summary with key information</li>
 * </ul>
 *
 * <p>Uses {@link CombatSnapshot} data (NOT {@link io.github.larsonix.trailoforbis.combat.DamageTrace}).
 * Displays attack type, shield absorption, true damage, block/parry status, and
 * correctly labels DOT and environmental damage in the timeline.
 */
public final class DeathRecapFormatter {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private DeathRecapFormatter() {}

    // ==================== Public API ====================

    /**
     * Formats a death recap message based on the config display mode.
     */
    @Nonnull
    public static Message format(@Nonnull CombatSnapshot snapshot, @Nonnull DeathRecapConfig config) {
        return format(snapshot, null, config);
    }

    /**
     * Formats a death recap message with optional damage chain history.
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
     * Example: [Death] Killed by [Elite] Trork Warrior (Lv27) - 79 dmg (CRIT! 15 base -38% armor +12 elem)
     */
    @Nonnull
    public static Message formatCompact(@Nonnull CombatSnapshot snapshot) {
        StringBuilder sb = new StringBuilder();
        sb.append("[Death] Killed by ");
        sb.append(formatAttackerName(snapshot));
        sb.append(" - ");
        sb.append(snapshot.getCompactSummary());
        return Message.raw(sb.toString()).color(MessageColors.ERROR);
    }

    /**
     * Formats a full multi-line death recap.
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
        MessageBuilder mb = new MessageBuilder();

        // Header
        mb.header("DEATH RECAP", MessageColors.ERROR);

        // Killed by line
        mb.text("Killed by: ", MessageColors.GRAY);
        mb.append(formatAttackerNameColored(snapshot));
        mb.line("", MessageColors.GRAY);

        // Damage breakdown section
        if (config.isShowDamageBreakdown() && !snapshot.wasDodged() && !snapshot.wasEvaded()) {
            mb.section("Damage Breakdown");
            appendDamageBreakdown(mb, snapshot);
        }

        // Elemental damage section
        if (config.isShowElementalDamage() && snapshot.hasElementalDamage()) {
            mb.section("Elemental Damage");
            appendElementalBreakdown(mb, snapshot);
        }

        // Damage chain section (timeline of recent hits)
        if (config.isShowDamageChain() && history != null && history.size() > 1) {
            mb.section("Damage Timeline (last " + history.size() + " hits)");
            appendDamageChain(mb, history);
        }

        // Defensive stats section
        if (config.isShowDefensiveStats()) {
            mb.section("Your Defenses");
            appendDefensiveStats(mb, snapshot);
        }

        // Elemental resistances section
        if (config.isShowDefensiveStats() && snapshot.defenderRawResistances() != null) {
            mb.section("Your Resistances");
            appendResistancesSection(mb, snapshot);
        }

        // Footer
        mb.footer(MessageColors.ERROR);

        return mb.build();
    }

    // ==================== Public Name Formatters ====================

    /**
     * Formats attacker name as a plain string.
     * Used by DeathMessageBuilder and external callers.
     */
    @Nonnull
    public static String formatAttackerName(@Nonnull CombatSnapshot snapshot) {
        return CombatFormatConstants.formatAttackerName(snapshot);
    }

    /**
     * Formats attacker name with colored Message components.
     */
    @Nonnull
    public static Message formatAttackerNameColored(@Nonnull CombatSnapshot snapshot) {
        return CombatFormatConstants.formatAttackerNameColored(snapshot);
    }

    // ==================== Private Section Builders ====================

    /**
     * Damage breakdown: type -> base -> crit -> armor -> elemental -> shield -> true -> block/parry -> final.
     */
    private static void appendDamageBreakdown(@Nonnull MessageBuilder mb, @Nonnull CombatSnapshot snapshot) {
        // Attack type context
        String typeLabel = formatAttackTypeLabel(snapshot);
        if (typeLabel != null) {
            mb.line("Type: " + typeLabel, MessageColors.INFO);
        }

        // Base damage
        mb.line("Base Damage: " + NumberFormatter.smallFlat(snapshot.baseDamage()), MessageColors.WHITE);

        // Critical hit
        if (snapshot.wasCritical()) {
            String tierLabel = snapshot.critTier() > 1 ? " T" + snapshot.critTier() : "";
            mb.line("* CRITICAL" + tierLabel + ": " + NumberFormatter.multiplier(snapshot.critMultiplier()) + " (" + NumberFormatter.smallFlat(snapshot.damageAfterAttackerBonuses()) + ")", MessageColors.GOLD);
        }

        // Armor reduction
        if (snapshot.reductionPercent() > 0) {
            float redFraction = snapshot.reductionPercent() / 100f;
            float armorReduced = snapshot.damageAfterArmor() * redFraction / (1f - redFraction);
            StringBuilder armorLine = new StringBuilder("- Armor (" + NumberFormatter.flat(snapshot.defenderArmor()) + "): -" + NumberFormatter.percent(snapshot.reductionPercent()) + " (-" + NumberFormatter.smallFlat(armorReduced) + ")");
            if (snapshot.armorPenetration() > 0) {
                armorLine.append(" [").append(NumberFormatter.percent(snapshot.armorPenetration())).append(" pen]");
            }
            mb.line(armorLine.toString(), MessageColors.ERROR);
        }

        // Total elemental
        if (snapshot.totalElementalDamage() > 0) {
            mb.line("+ Elemental: " + NumberFormatter.signed(snapshot.totalElementalDamage()), MessageColors.PURPLE);
        }

        // True damage
        if (snapshot.trueDamage() > 0) {
            mb.line("+ True: " + NumberFormatter.signed(snapshot.trueDamage()) + " (bypasses defenses)", MessageColors.PURPLE);
        }

        // Energy shield absorption
        if (snapshot.shieldAbsorbed() > 0) {
            mb.line("- Shield Absorbed: " + NumberFormatter.smallFlat(snapshot.shieldAbsorbed()), MessageColors.LIGHT_BLUE);
        }

        // Block/parry indicators
        if (snapshot.wasBlocked()) {
            mb.line("* BLOCKED (reduced damage)", MessageColors.INFO);
        }
        if (snapshot.wasParried()) {
            mb.line("* PARRIED (reduced damage)", MessageColors.INFO);
        }

        // Final
        mb.separator();
        mb.line("Final: " + NumberFormatter.smallFlat(snapshot.finalDamage()) + " / " + NumberFormatter.flat(snapshot.defenderHealthBefore()) + " HP", MessageColors.ERROR);
    }

    /**
     * Elemental damage breakdown per element with resistance info.
     */
    private static void appendElementalBreakdown(@Nonnull MessageBuilder mb, @Nonnull CombatSnapshot snapshot) {
        Map<ElementType, Float> elementalDamage = snapshot.elementalDamage();
        Map<ElementType, Float> elementalResist = snapshot.elementalResist();

        if (elementalDamage == null) return;

        for (Map.Entry<ElementType, Float> entry : elementalDamage.entrySet()) {
            ElementType element = entry.getKey();
            float finalDamage = entry.getValue();
            if (finalDamage <= 0) continue;

            float effectiveResist = 0;
            if (elementalResist != null && elementalResist.containsKey(element)) {
                effectiveResist = elementalResist.get(element);
            }

            String color = getElementColor(element);
            String resistText;

            if (effectiveResist > 0) {
                float damageMultiplier = 1f - effectiveResist / 100f;
                float originalDamage = (damageMultiplier > 0) ? finalDamage / damageMultiplier : finalDamage;
                float blockedDamage = originalDamage - finalDamage;
                resistText = "(" + NumberFormatter.percent(effectiveResist) + " resisted, blocked " + NumberFormatter.smallFlat(blockedDamage) + ")";
            } else if (effectiveResist < 0) {
                float damageMultiplier = 1f + Math.abs(effectiveResist) / 100f;
                float originalDamage = finalDamage / damageMultiplier;
                float extraDamage = finalDamage - originalDamage;
                resistText = "(" + NumberFormatter.percent(Math.abs(effectiveResist)) + " vulnerable, " + NumberFormatter.signed(extraDamage) + " extra)";
            } else {
                resistText = "(0% resisted)";
            }

            mb.line(element.getDisplayName() + ": " + NumberFormatter.signed(finalDamage) + " " + resistText, color);
        }
    }

    /**
     * Damage chain timeline: shows recent hits in chronological order (killing blow last).
     * Uses contextual labels: DOT entries show "Burn DOT", environmental shows cause name.
     */
    private static void appendDamageChain(@Nonnull MessageBuilder mb, @Nonnull List<CombatSnapshot> history) {
        long now = System.currentTimeMillis();

        // Display oldest first (history is most-recent-first), so reverse
        for (int i = history.size() - 1; i >= 0; i--) {
            CombatSnapshot snap = history.get(i);
            int displayNum = history.size() - i;

            // Build contextual type label
            String typeName;
            if ("dot".equals(snap.attackerType())) {
                // DOT entries: use attacker name directly (already formatted as "Burn DOT (Trork Warrior)")
                typeName = snap.attackerName();
            } else if ("environment".equals(snap.attackerType())) {
                // Environmental: use the cause name directly
                typeName = snap.attackerName();
            } else {
                // Normal hits: show damage type with proper capitalization
                typeName = snap.damageType().name().charAt(0)
                    + snap.damageType().name().substring(1).toLowerCase();
            }

            String critMark = snap.wasCritical()
                ? (snap.critTier() > 1 ? " CRIT T" + snap.critTier() + "!" : " CRIT!")
                : "";

            // For DOT and environmental, the source info is already in typeName
            String sourceInfo;
            if ("dot".equals(snap.attackerType()) || "environment".equals(snap.attackerType())) {
                sourceInfo = "";
            } else {
                sourceInfo = " from " + CombatFormatConstants.formatAttackerName(snap);
            }

            String timeLabel;
            if (i == 0) {
                timeLabel = "KILLING BLOW";
            } else {
                float secondsAgo = (now - snap.timestamp()) / 1000f;
                timeLabel = NumberFormatter.time(secondsAgo) + " ago";
            }

            // Use element color for DOT/elemental damage, gold for crit, gray otherwise
            String color;
            if (snap.wasCritical()) {
                color = MessageColors.GOLD;
            } else if ("dot".equals(snap.attackerType())) {
                color = getTimelineColorForDamageType(snap.damageType());
            } else {
                color = MessageColors.GRAY;
            }

            mb.line("  " + displayNum + ". " + typeName + ": " + NumberFormatter.flat(snap.finalDamage()) + " dmg" + sourceInfo + critMark + "  [" + timeLabel + "]", color);
        }
    }

    /** Defensive stats section: armor, dodge, and evasion. */
    private static void appendDefensiveStats(@Nonnull MessageBuilder mb, @Nonnull CombatSnapshot snapshot) {
        if (snapshot.defenderArmor() > 0) {
            mb.line("Armor: " + NumberFormatter.flat(snapshot.defenderArmor()) + " (blocked " + NumberFormatter.percent(snapshot.reductionPercent()) + ")", MessageColors.INFO);
        } else {
            mb.line("Armor: 0 (no protection)", MessageColors.GRAY);
        }

        // Dodge chance (flat %)
        if (snapshot.wasDodged()) {
            mb.line("Dodge: triggered! (flat dodge chance)", MessageColors.SUCCESS);
        }

        // Evasion rating (PoE-style vs accuracy)
        if (snapshot.defenderEvasion() > 0) {
            String chanceDetail = computeEvasionChance(snapshot.defenderEvasion(), snapshot.attackerLevel());
            if (snapshot.wasEvaded()) {
                String evadeText = (chanceDetail != null)
                    ? "Evasion: " + NumberFormatter.flat(snapshot.defenderEvasion()) + " (" + chanceDetail + " -- evaded!)"
                    : "Evasion: " + NumberFormatter.flat(snapshot.defenderEvasion()) + " (evaded!)";
                mb.line(evadeText, MessageColors.SUCCESS);
            } else {
                String missText = (chanceDetail != null)
                    ? "Evasion: " + NumberFormatter.flat(snapshot.defenderEvasion()) + " (" + chanceDetail + " -- didn't proc)"
                    : "Evasion: " + NumberFormatter.flat(snapshot.defenderEvasion()) + " (didn't proc)";
                mb.line(missText, MessageColors.GRAY);
            }
        }
    }

    /** Elemental resistances section: all 6 elements. Uses ElementalCalculator for consistent formula. */
    private static void appendResistancesSection(@Nonnull MessageBuilder mb, @Nonnull CombatSnapshot snapshot) {
        Map<ElementType, Float> rawResistances = snapshot.defenderRawResistances();
        Map<ElementType, Float> penetration = snapshot.attackerPenetration();

        if (rawResistances == null) return;

        for (ElementType element : ElementType.values()) {
            float rawResist = rawResistances.getOrDefault(element, 0f);
            float pen = (penetration != null) ? penetration.getOrDefault(element, 0f) : 0f;
            float effectiveResist = (float) ElementalCalculator.getEffectiveResistance(rawResist, pen);

            String color = getElementColor(element);

            if (pen > 0) {
                // Show penetration breakdown — handles positive, zero, and negative effective
                String effLabel = effectiveResist < 0 ? " vulnerable" : " effective";
                mb.line(element.getDisplayName() + ": " + NumberFormatter.percent(rawResist) + " (" + NumberFormatter.percent(pen) + " penetrated > " + NumberFormatter.percent(effectiveResist) + effLabel + ")", color);
            } else if (rawResist < 0) {
                mb.line(element.getDisplayName() + ": " + NumberFormatter.percent(rawResist) + " (vulnerable)", color);
            } else if (rawResist == 0) {
                mb.line(element.getDisplayName() + ": 0%", color);
            } else if (rawResist > ElementalCalculator.RESISTANCE_CAP) {
                mb.line(element.getDisplayName() + ": " + NumberFormatter.percent(rawResist) + " (capped at " + NumberFormatter.percent((float) ElementalCalculator.RESISTANCE_CAP) + ")", color);
            } else {
                mb.line(element.getDisplayName() + ": " + NumberFormatter.percent(rawResist), color);
            }
        }
    }

    // ==================== Helpers ====================

    /**
     * Returns a human-readable attack type label for the death recap breakdown.
     * Returns null for UNKNOWN (DOT/environmental — type is implicit from context).
     */
    @Nullable
    private static String formatAttackTypeLabel(@Nonnull CombatSnapshot snapshot) {
        AttackType type = snapshot.attackType();
        if (type == AttackType.UNKNOWN) {
            // DOT/environmental — type is already clear from attacker name
            if ("dot".equals(snapshot.attackerType())) return "DOT (Damage over Time)";
            if ("environment".equals(snapshot.attackerType())) return "Environmental";
            return null;
        }
        return switch (type) {
            case MELEE -> "Melee";
            case PROJECTILE -> "Projectile";
            case AREA -> "Area of Effect";
            case SPELL -> "Spell";
            default -> null;
        };
    }

    /**
     * Returns an element-appropriate color for damage type in timeline entries.
     */
    @Nonnull
    private static String getTimelineColorForDamageType(@Nonnull io.github.larsonix.trailoforbis.combat.DamageType type) {
        return switch (type) {
            case FIRE -> COLOR_FIRE;
            case WATER -> COLOR_WATER;
            case LIGHTNING -> COLOR_LIGHTNING;
            case EARTH -> COLOR_EARTH;
            case WIND -> COLOR_WIND;
            case VOID -> COLOR_VOID;
            case MAGIC -> MessageColors.PURPLE;
            case PHYSICAL -> MessageColors.WHITE;
        };
    }

    /**
     * Computes the evasion chance percentage against the attacker's level.
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

            float refAccuracy = (float) MobStatFactory.getReferenceAccuracy(poolConfig, attackerLevel);
            float hitChance = AvoidanceProcessor.calculateHitChance(
                evasionConfig, refAccuracy, evasion);
            float evadeChance = (1f - hitChance) * 100f;
            return NumberFormatter.percent(evadeChance) + " vs Lv." + attackerLevel;
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to compute evasion chance for death recap");
            return null;
        }
    }
}
