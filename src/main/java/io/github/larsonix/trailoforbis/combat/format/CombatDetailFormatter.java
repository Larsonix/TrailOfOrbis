package io.github.larsonix.trailoforbis.combat.format;

import com.hypixel.hytale.server.core.Message;
import io.github.larsonix.trailoforbis.attributes.breakdown.StatBreakdownResult;
import io.github.larsonix.trailoforbis.combat.AttackType;
import io.github.larsonix.trailoforbis.combat.DamageBreakdown;
import io.github.larsonix.trailoforbis.combat.DamageTrace;
import io.github.larsonix.trailoforbis.combat.DamageType;
import io.github.larsonix.trailoforbis.combat.avoidance.AvoidanceProcessor;
import io.github.larsonix.trailoforbis.combat.deathrecap.CombatSnapshot;
import io.github.larsonix.trailoforbis.mobs.classification.RPGMobClass;
import io.github.larsonix.trailoforbis.elemental.ElementType;
import io.github.larsonix.trailoforbis.util.MessageColors;
import io.github.larsonix.trailoforbis.util.NumberFormatter;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static io.github.larsonix.trailoforbis.combat.format.CombatFormatConstants.*;

/**
 * Formats detailed combat log messages for the /too combat detail feature.
 *
 * <p>Replaces the old CombatLogFormatter with cleaner, player-friendly output.
 * Two main views:
 * <ul>
 *   <li>{@link #formatDealt}: Full 10-step pipeline breakdown (attacker perspective)</li>
 *   <li>{@link #formatReceived}: Defense-focused breakdown (defender perspective)</li>
 * </ul>
 *
 * <p>Also provides avoidance message formatting for both perspectives.
 */
public final class CombatDetailFormatter {

    private CombatDetailFormatter() {}

    // ==================== Damage Dealt (Attacker View) ====================

    /**
     * Formats the full damage pipeline breakdown from the attacker's perspective.
     * Shows every calculation step with intermediate values and stat attribution.
     */
    @Nonnull
    public static Message formatDamageDealt(
        @Nonnull DamageTrace trace,
        @Nonnull String targetName,
        int targetLevel,
        @Nullable RPGMobClass targetClass
    ) {
        StatBreakdownResult bkd = trace.attackerBreakdown();
        MessageBuilder mb = new MessageBuilder();

        // Header
        mb.header("DAMAGE DEALT", MessageColors.SUCCESS);
        mb.text("Target: ", MessageColors.GRAY);
        mb.append(formatEntityNameColored(targetName, "mob", targetLevel, targetClass));
        mb.line("", MessageColors.GRAY);

        // Summary
        SectionRenderer.renderSummaryHeader(mb, trace);

        // Full pipeline (Steps 1-10)
        SectionRenderer.renderBaseDamage(mb, trace);
        SectionRenderer.renderFlatPhysical(mb, trace, bkd);
        SectionRenderer.renderFlatElemental(mb, trace);
        SectionRenderer.renderConversion(mb, trace);
        SectionRenderer.renderPercentIncreased(mb, trace, bkd);
        SectionRenderer.renderElementalMods(mb, trace);
        SectionRenderer.renderMoreMultipliers(mb, trace);
        SectionRenderer.renderConditionals(mb, trace);
        SectionRenderer.renderCritSection(mb, trace, bkd);
        SectionRenderer.renderDefenses(mb, trace);
        SectionRenderer.renderTrueDamage(mb, trace);
        SectionRenderer.renderPostCalc(mb, trace, false); // attacker perspective
        SectionRenderer.renderAvoidanceContext(mb, trace, true);
        SectionRenderer.renderAilments(mb, trace);

        // Total
        mb.separator();
        mb.line("Total: " + NumberFormatter.smallFlat(trace.effectiveFinalDamage()), MessageColors.SUCCESS);

        // Recovery & Thorns
        SectionRenderer.renderRecovery(mb, trace);
        SectionRenderer.renderThorns(mb, trace);

        // Footer
        mb.footer(MessageColors.SUCCESS);
        return mb.build();
    }

    // ==================== Damage Received (Defender View) ====================

    /**
     * Formats the damage breakdown from the defender's perspective.
     * Focuses on defenses and what the defender could improve.
     */
    @Nonnull
    public static Message formatDamageReceived(
        @Nonnull DamageTrace trace,
        @Nonnull CombatSnapshot snapshot
    ) {
        StatBreakdownResult bkd = trace.defenderBreakdown();
        MessageBuilder mb = new MessageBuilder();

        // Header
        mb.header("DAMAGE TAKEN", MessageColors.ERROR);
        mb.text("Attacker: ", MessageColors.GRAY);
        mb.append(formatAttackerNameColored(snapshot));
        mb.line("", MessageColors.GRAY);

        // Incoming damage summary (pre-defense)
        mb.section("Incoming Attack");
        float totalPreDefense = trace.physBeforeArmor();
        for (ElementType elem : ElementType.values()) {
            totalPreDefense += trace.elemBeforeResist().getOrDefault(elem, 0f);
        }
        mb.text("Pre-Defense Total: " + NumberFormatter.smallFlat(totalPreDefense), MessageColors.WHITE);
        if (trace.wasCritical()) {
            String tierLabel = trace.critTier() > 1 ? " T" + trace.critTier() : "";
            mb.text(" (CRIT" + tierLabel + " " + NumberFormatter.multiplier(trace.critMultiplierApplied()) + ")", MessageColors.GOLD);
        }
        mb.line("", MessageColors.GRAY);

        // Your defenses (detailed)
        renderReceivedDefenses(mb, trace, bkd);

        // Damage taken breakdown
        renderReceivedDamage(mb, trace);

        // Post-calc modifications
        SectionRenderer.renderPostCalc(mb, trace, true); // defender perspective

        // Your avoidance stats (all failed since this hit connected)
        SectionRenderer.renderAvoidanceContext(mb, trace, false);

        // Total
        mb.separator();
        float totalTaken = trace.effectiveFinalDamage();
        float defMaxHp = trace.defenderMaxHealth() > 0 ? trace.defenderMaxHealth() : snapshot.defenderMaxHealth();
        if (defMaxHp > 0) {
            float pctOfHp = (totalTaken / defMaxHp) * 100f;
            mb.line("Total Taken: " + NumberFormatter.smallFlat(totalTaken) + " (" + NumberFormatter.percent(pctOfHp) + " of " + NumberFormatter.flat(defMaxHp) + " HP)", MessageColors.ERROR);
        } else {
            mb.line("Total Taken: " + NumberFormatter.smallFlat(totalTaken), MessageColors.ERROR);
        }

        // HP change
        mb.blank();
        float hpPct = snapshot.defenderMaxHealth() > 0
            ? (snapshot.defenderHealthAfter() / snapshot.defenderMaxHealth()) * 100f : 0f;
        mb.line("Your HP: " + NumberFormatter.flat(snapshot.defenderHealthBefore()) + " -> " + NumberFormatter.flat(snapshot.defenderHealthAfter()) + " (" + NumberFormatter.percent(hpPct) + ")", MessageColors.PINK);

        // Footer
        mb.footer(MessageColors.ERROR);
        return mb.build();
    }

    /** Renders the "Your Defenses" section for the received view. */
    private static void renderReceivedDefenses(
        @Nonnull MessageBuilder mb, @Nonnull DamageTrace trace, @Nullable StatBreakdownResult bkd
    ) {
        mb.section("Your Defenses");

        // Armor
        float armor = trace.defenderArmor();
        float armorPct = trace.armorPercent();
        float armorPen = trace.armorPenPercent();
        float effArmor = trace.effectiveArmor();
        float armorRedPct = trace.armorReductionPercent();

        if (armor > 0) {
            StringBuilder armorLine = new StringBuilder("Armor: " + NumberFormatter.flat(armor));
            if (armorPct > 0) armorLine.append(" x (1+").append(NumberFormatter.percent(armorPct)).append(")");
            if (armorPen > 0) armorLine.append(" - ").append(NumberFormatter.percent(armorPen)).append(" pen");
            armorLine.append(" = ").append(NumberFormatter.flat(effArmor)).append(" eff -> -").append(NumberFormatter.percent(armorRedPct));
            String armorSrc = SectionRenderer.statSources(bkd, cs -> cs.getArmor());
            armorLine.append(armorSrc);
            mb.line(armorLine.toString(), MessageColors.INFO);
        } else {
            mb.line("Armor: 0 (no protection)", MessageColors.GRAY);
        }

        // Physical resistance
        float physResist = trace.physResistPercent();
        if (physResist > 0) {
            mb.line("Physical Resistance: " + NumberFormatter.percent(physResist), MessageColors.INFO);
        }

        // Elemental resistances
        for (ElementType elem : ElementType.values()) {
            float raw = trace.defenderRawResist().getOrDefault(elem, 0f);
            float pen = trace.attackerPenetration().getOrDefault(elem, 0f);
            float eff = trace.effectiveResist().getOrDefault(elem, 0f);
            String color = getElementColor(elem);
            if (raw != 0 || pen != 0) {
                if (pen > 0) {
                    mb.line(elem.getDisplayName() + ": " + NumberFormatter.percent(raw) + " - " + NumberFormatter.percent(pen) + " pen = " + NumberFormatter.percent(eff) + " eff", color);
                } else {
                    mb.line(elem.getDisplayName() + ": " + NumberFormatter.percent(raw), color);
                }
            }
        }
    }

    /** Renders the "Damage Taken" breakdown section for the received view. */
    private static void renderReceivedDamage(@Nonnull MessageBuilder mb, @Nonnull DamageTrace trace) {
        mb.section("Damage Taken");

        // Physical channel: before -> after armor -> after resist (skip for spells — no physical damage)
        if (trace.attackType() != AttackType.SPELL) {
            String label = SectionRenderer.primaryChannelLabel(trace);
            mb.line(label + ": " + NumberFormatter.smallFlat(trace.physBeforeArmor()) + " -> " + NumberFormatter.smallFlat(trace.physAfterArmor()) + " (armor) -> " + NumberFormatter.smallFlat(trace.physAfterResist()) + " (resist)", MessageColors.WHITE);
        }

        // Elemental after resist
        for (ElementType elem : ElementType.values()) {
            float before = trace.elemBeforeResist().getOrDefault(elem, 0f);
            float after = trace.elemAfterResist().getOrDefault(elem, 0f);
            if (before > 0 || after > 0) {
                String color = getElementColor(elem);
                float eff = trace.effectiveResist().getOrDefault(elem, 0f);
                if (eff != 0) {
                    mb.line(elem.getDisplayName() + ": " + NumberFormatter.smallFlat(before) + " x " + NumberFormatter.percent(100f - eff) + " = " + NumberFormatter.smallFlat(after), color);
                } else {
                    mb.line(elem.getDisplayName() + ": " + NumberFormatter.smallFlat(after), color);
                }
            }
        }

        // True damage
        if (trace.trueDamage() > 0) {
            mb.line("True: +" + NumberFormatter.smallFlat(trace.trueDamage()) + " (bypasses defenses)", MessageColors.PURPLE);
        }
    }

    // ==================== Avoidance Messages ====================

    /**
     * Formats a detailed avoidance message for the defender's chat.
     * Shown when the defender successfully dodges/blocks/parries/misses an attack.
     */
    @Nonnull
    public static Message formatAvoidance(
        @Nonnull DamageBreakdown.AvoidanceReason reason,
        @Nonnull String attackerName,
        int attackerLevel,
        @Nullable RPGMobClass attackerClass,
        float estimatedDamage,
        @Nonnull AvoidanceProcessor.AvoidanceDetail avoidanceStats
    ) {
        String reasonStr = switch (reason) {
            case DODGED -> "DODGED";
            case EVADED -> "EVADED";
            case BLOCKED -> "BLOCKED";
            case PARRIED -> "PARRIED";
            case MISSED -> "MISSED";
        };

        MessageBuilder mb = new MessageBuilder();

        mb.header("ATTACK AVOIDED", MessageColors.SUCCESS);
        mb.text("Attacker: ", MessageColors.GRAY);
        mb.append(formatEntityNameColored(attackerName, "mob", attackerLevel, attackerClass));
        mb.line("", MessageColors.GRAY);
        mb.line("Result: " + reasonStr, MessageColors.SUCCESS);

        // Avoidance stats detail
        mb.section("Avoidance Stats");

        if (avoidanceStats.dodgeChance() > 0) {
            String color = reason == DamageBreakdown.AvoidanceReason.DODGED ? MessageColors.SUCCESS : MessageColors.GRAY;
            mb.line("Dodge: " + NumberFormatter.percent(avoidanceStats.dodgeChance()), color);
        }
        if (avoidanceStats.evasion() > 0 || avoidanceStats.accuracy() > 0) {
            String evasionColor = reason == DamageBreakdown.AvoidanceReason.EVADED ? MessageColors.SUCCESS : MessageColors.INFO;
            mb.line("Evasion: " + NumberFormatter.flat(avoidanceStats.evasion()) + " vs Accuracy: " + NumberFormatter.flat(avoidanceStats.accuracy()), evasionColor);
            String hitColor = reason == DamageBreakdown.AvoidanceReason.EVADED ? MessageColors.SUCCESS : MessageColors.GRAY;
            mb.line("Hit Chance: " + NumberFormatter.percent(avoidanceStats.hitChance()), hitColor);
        }
        if (avoidanceStats.passiveBlockChance() > 0 || avoidanceStats.activeBlockChance() > 0) {
            if (avoidanceStats.wasActiveBlock()) {
                String color = reason == DamageBreakdown.AvoidanceReason.BLOCKED ? MessageColors.SUCCESS : MessageColors.GRAY;
                mb.line("Block: " + NumberFormatter.percent(avoidanceStats.activeBlockChance()) + " (passive:" + NumberFormatter.percent(avoidanceStats.passiveBlockChance()) + " + active:" + NumberFormatter.percent(avoidanceStats.activeBlockChance() - avoidanceStats.passiveBlockChance()) + ")", color);
                if (reason == DamageBreakdown.AvoidanceReason.BLOCKED) {
                    mb.line("Reduction: " + NumberFormatter.percent(avoidanceStats.blockDamageReduction()), MessageColors.INFO);
                    if (avoidanceStats.blockStaminaCost() > 0) {
                        mb.line("Stamina Cost: " + NumberFormatter.flat(avoidanceStats.blockStaminaCost()), MessageColors.GRAY);
                    }
                }
            } else if (avoidanceStats.passiveBlockChance() > 0) {
                String color = reason == DamageBreakdown.AvoidanceReason.BLOCKED ? MessageColors.SUCCESS : MessageColors.GRAY;
                mb.line("Block: " + NumberFormatter.percent(avoidanceStats.passiveBlockChance()) + " (passive)", color);
            }
        }
        if (avoidanceStats.parryChance() > 0) {
            String color = reason == DamageBreakdown.AvoidanceReason.PARRIED ? MessageColors.SUCCESS : MessageColors.GRAY;
            mb.line("Parry: " + NumberFormatter.percent(avoidanceStats.parryChance()), color);
        }

        // Estimated damage
        if (estimatedDamage > 0) {
            mb.blank();
            mb.line("Damage Avoided: " + NumberFormatter.flat(estimatedDamage), MessageColors.GRAY);
        }

        mb.footer(MessageColors.SUCCESS);
        return mb.build();
    }

    // ==================== DOT & Environmental Detail ====================

    /**
     * Formats a compact single-line message for DOT tick damage.
     *
     * <p>DOTs tick frequently (~0.25s), so this must be a single line to avoid
     * flooding the player's chat. Shows the essential information: source,
     * element, base DPS, applied modifiers, and final tick damage.
     *
     * @param ailmentName Display name (e.g., "Burn", "Poison")
     * @param element The DOT element (FIRE for burn, VOID for poison)
     * @param baseDPS The pre-resistance tick damage
     * @param shockAmpPercent Shock amplification applied (0 if none)
     * @param resistPercent Effective resistance reduction (0 if none)
     * @param finalTickDamage The actual damage dealt this tick
     * @return Single-line colored message
     */
    @Nonnull
    public static Message formatDOTDamage(
        @Nonnull String ailmentName,
        @Nullable ElementType element,
        @Nonnull String sourceName,
        float baseDPS,
        float shockAmpPercent,
        float resistPercent,
        float finalTickDamage
    ) {
        String elemColor = element != null ? getElementColor(element) : MessageColors.GRAY;
        String elemName = element != null ? element.getDisplayName() : "Physical";

        MessageBuilder mb = new MessageBuilder();
        mb.text("[DOT] ", elemColor);
        mb.text(ailmentName + " (" + elemName + ")", elemColor);
        mb.text(" from ", MessageColors.GRAY);
        mb.text(sourceName, MessageColors.WHITE);
        mb.text(" | Base: " + NumberFormatter.smallFlat(baseDPS), MessageColors.GRAY);

        if (resistPercent > 0) {
            mb.text(" | -" + NumberFormatter.percent(resistPercent) + " " + elemName + " Resist", elemColor);
        }
        if (shockAmpPercent > 0) {
            mb.text(" | +" + NumberFormatter.percent(shockAmpPercent) + " Shock", COLOR_LIGHTNING);
        }

        mb.text(" | Tick: " + NumberFormatter.smallFlat(finalTickDamage), elemColor);

        return mb.build();
    }

    /**
     * Formats a compact single-line message for environmental damage.
     *
     * @param causeName Display name (e.g., "Fall Damage", "Lava")
     * @param damage The damage dealt
     * @param resistPercent Resistance applied (0 if none)
     * @param element The element type (FIRE for lava, null for fall)
     * @return Single-line colored message
     */
    @Nonnull
    public static Message formatEnvironmentalDamage(
        @Nonnull String causeName,
        float damage,
        float resistPercent,
        @Nullable ElementType element
    ) {
        String color = element != null ? getElementColor(element) : MessageColors.ORANGE;
        String elemName = element != null ? element.getDisplayName() : "";

        MessageBuilder mb = new MessageBuilder();
        mb.text("[ENV] ", color);
        mb.text(causeName, color);
        mb.text(" | " + NumberFormatter.smallFlat(damage) + " damage", MessageColors.WHITE);

        if (resistPercent > 0 && !elemName.isEmpty()) {
            mb.text(" | -" + NumberFormatter.percent(resistPercent) + " " + elemName + " Resist", color);
        }

        return mb.build();
    }

    // ==================== Avoidance Messages ====================

    /**
     * Formats a brief avoidance message for the attacker's chat.
     * Shown when the attacker's attack is avoided by the target.
     */
    @Nonnull
    public static Message formatAttackAvoided(
        @Nonnull DamageBreakdown.AvoidanceReason reason,
        @Nonnull String targetName,
        int targetLevel,
        @Nullable RPGMobClass targetClass,
        @Nullable AvoidanceProcessor.AvoidanceDetail avoidanceStats
    ) {
        String reasonStr = switch (reason) {
            case DODGED -> "DODGED";
            case EVADED -> "EVADED";
            case BLOCKED -> "BLOCKED";
            case PARRIED -> "PARRIED";
            case MISSED -> "MISSED";
        };

        MessageBuilder mb = new MessageBuilder();
        mb.text("[->] ", MessageColors.GOLD);
        mb.text(reasonStr + " ", MessageColors.GOLD);
        mb.text("by ", MessageColors.GRAY);
        mb.append(formatEntityNameColored(targetName, "mob", targetLevel, targetClass));

        // Add key avoidance stat inline
        if (avoidanceStats != null) {
            String detail = switch (reason) {
                case DODGED -> " -- Dodge:" + NumberFormatter.percent(avoidanceStats.dodgeChance());
                case EVADED -> " -- Evasion:" + NumberFormatter.flat(avoidanceStats.evasion()) + " vs Acc:" + NumberFormatter.flat(avoidanceStats.accuracy());
                case MISSED -> " -- Evasion:" + NumberFormatter.flat(avoidanceStats.evasion());
                case BLOCKED -> avoidanceStats.wasActiveBlock()
                    ? " -- Block:" + NumberFormatter.percent(avoidanceStats.activeBlockChance()) + " (active)"
                    : " -- Block:" + NumberFormatter.percent(avoidanceStats.passiveBlockChance());
                case PARRIED -> " -- Parry:" + NumberFormatter.percent(avoidanceStats.parryChance());
            };
            mb.text(detail, MessageColors.GRAY);
        }

        return mb.build();
    }
}
