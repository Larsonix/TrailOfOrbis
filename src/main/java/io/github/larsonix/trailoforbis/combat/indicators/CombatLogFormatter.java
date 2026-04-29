package io.github.larsonix.trailoforbis.combat.indicators;

import com.hypixel.hytale.server.core.Message;
import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.attributes.breakdown.StatBreakdownResult;
import io.github.larsonix.trailoforbis.combat.DamageBreakdown;
import io.github.larsonix.trailoforbis.combat.DamageTrace;
import io.github.larsonix.trailoforbis.combat.deathrecap.CombatSnapshot;
import io.github.larsonix.trailoforbis.combat.ailments.CombatAilmentApplicator;
import io.github.larsonix.trailoforbis.combat.avoidance.AvoidanceProcessor;
import io.github.larsonix.trailoforbis.combat.modifiers.ConditionalResult;
import io.github.larsonix.trailoforbis.elemental.ElementType;
import io.github.larsonix.trailoforbis.mobs.classification.RPGMobClass;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Formats detailed combat log messages for the /too combat detail feature.
 *
 * <p>Provides rich multi-line, multi-colored output showing full damage calculation
 * breakdowns for build debugging. Two perspectives are supported:
 * <ul>
 *   <li><b>Damage Dealt:</b> Shows attacker what damage they dealt to a target</li>
 *   <li><b>Damage Received:</b> Shows defender what damage they took from an attacker</li>
 * </ul>
 *
 * <p>The format uses color-coded output with element-specific colors and
 * clear calculation breakdowns showing base damage through defenses to final damage.
 * Uses ASCII separators instead of Unicode for maximum Hytale chat compatibility.
 */
public final class CombatLogFormatter {

    // Element-specific colors matching DeathRecapFormatter
    private static final String COLOR_FIRE = MessageColors.ORANGE;       // Orange
    private static final String COLOR_WATER = MessageColors.LIGHT_BLUE;   // Light blue
    private static final String COLOR_LIGHTNING = MessageColors.WARNING; // Yellow
    private static final String COLOR_EARTH = MessageColors.SUCCESS;     // Green
    private static final String COLOR_WIND = MessageColors.WHITE;        // White
    private static final String COLOR_VOID = MessageColors.DARK_PURPLE; // Purple

    private CombatLogFormatter() {
        // Utility class - prevent instantiation
    }

    /**
     * Formats avoidance message for defender's chat.
     *
     * @param reason The avoidance reason
     * @param attackerName The attacker's display name
     * @param attackerLevel The attacker's level
     * @param attackerClass The attacker's mob class (null for players)
     * @param estimatedDamage Estimated damage that would have been dealt (for display)
     * @return Formatted message for chat
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
        Message m = Message.empty();

        String reasonStr = switch (reason) {
            case DODGED -> "DODGED";
            case BLOCKED -> "BLOCKED";
            case PARRIED -> "PARRIED";
            case MISSED -> "MISSED";
        };

        // Header
        m = m.insert(Message.raw("\n====== ATTACK AVOIDED ======\n").color(MessageColors.SUCCESS));
        m = m.insert(Message.raw("Attacker: ").color(MessageColors.GRAY));
        m = m.insert(formatEntityNameColored(attackerName, "mob", attackerLevel, attackerClass));
        m = m.insert(Message.raw("\n").color(MessageColors.GRAY));
        m = m.insert(Message.raw(String.format("Result: %s\n", reasonStr)).color(MessageColors.SUCCESS));

        // Avoidance stats detail
        m = m.insert(Message.raw("\n-- Avoidance Stats --\n").color(MessageColors.WARNING));

        if (avoidanceStats.dodgeChance() > 0) {
            m = m.insert(Message.raw(String.format("Dodge: %.1f%%\n",
                avoidanceStats.dodgeChance())).color(
                reason == DamageBreakdown.AvoidanceReason.DODGED ? MessageColors.SUCCESS : MessageColors.GRAY));
        }
        if (avoidanceStats.evasion() > 0 || avoidanceStats.accuracy() > 0) {
            m = m.insert(Message.raw(String.format("Evasion: %.0f vs Accuracy: %.0f\n",
                avoidanceStats.evasion(), avoidanceStats.accuracy())).color(MessageColors.INFO));
            m = m.insert(Message.raw(String.format("Hit Chance: %.1f%%\n",
                avoidanceStats.hitChance())).color(
                reason == DamageBreakdown.AvoidanceReason.MISSED ? MessageColors.SUCCESS : MessageColors.GRAY));
        }
        if (avoidanceStats.passiveBlockChance() > 0 || avoidanceStats.activeBlockChance() > 0) {
            if (avoidanceStats.wasActiveBlock()) {
                m = m.insert(Message.raw(String.format("Block: %.0f%% (passive:%.0f%% + active:%.0f%%)\n",
                    avoidanceStats.activeBlockChance(),
                    avoidanceStats.passiveBlockChance(),
                    avoidanceStats.activeBlockChance() - avoidanceStats.passiveBlockChance())).color(
                    reason == DamageBreakdown.AvoidanceReason.BLOCKED ? MessageColors.SUCCESS : MessageColors.GRAY));
                if (reason == DamageBreakdown.AvoidanceReason.BLOCKED) {
                    m = m.insert(Message.raw(String.format("Reduction: %.0f%%\n",
                        avoidanceStats.blockDamageReduction())).color(MessageColors.INFO));
                    if (avoidanceStats.blockStaminaCost() > 0) {
                        m = m.insert(Message.raw(String.format("Stamina Cost: %.0f\n",
                            avoidanceStats.blockStaminaCost())).color(MessageColors.GRAY));
                    }
                }
            } else if (avoidanceStats.passiveBlockChance() > 0) {
                m = m.insert(Message.raw(String.format("Block: %.0f%% (passive)\n",
                    avoidanceStats.passiveBlockChance())).color(
                    reason == DamageBreakdown.AvoidanceReason.BLOCKED ? MessageColors.SUCCESS : MessageColors.GRAY));
            }
        }
        if (avoidanceStats.parryChance() > 0) {
            m = m.insert(Message.raw(String.format("Parry: %.0f%%\n",
                avoidanceStats.parryChance())).color(
                reason == DamageBreakdown.AvoidanceReason.PARRIED ? MessageColors.SUCCESS : MessageColors.GRAY));
        }

        // Estimated damage
        if (estimatedDamage > 0) {
            m = m.insert(Message.raw(String.format("\nEstimated Damage Avoided: ~%.0f\n", estimatedDamage)).color(MessageColors.GRAY));
        }

        m = m.insert(Message.raw("==========================\n").color(MessageColors.SUCCESS));
        return m;
    }

    /**
     * Formats avoidance message for attacker's perspective (their attack was avoided).
     * Enhanced with target avoidance stats when available.
     *
     * @param reason The avoidance reason
     * @param targetName The target's display name
     * @param targetLevel The target's level
     * @param targetClass The target's mob class (null for players)
     * @param avoidanceStats The target's avoidance stats (null for compact format)
     * @return Formatted message for chat
     */
    @Nonnull
    public static Message formatAttackAvoided(
        @Nonnull DamageBreakdown.AvoidanceReason reason,
        @Nonnull String targetName,
        int targetLevel,
        @Nullable RPGMobClass targetClass,
        @Nullable AvoidanceProcessor.AvoidanceDetail avoidanceStats
    ) {
        Message message = Message.empty();

        String reasonStr = switch (reason) {
            case DODGED -> "DODGED";
            case BLOCKED -> "BLOCKED";
            case PARRIED -> "PARRIED";
            case MISSED -> "MISSED";
        };

        message = message.insert(Message.raw("[->] ").color(MessageColors.WARNING));
        message = message.insert(Message.raw(reasonStr + " ").color(MessageColors.WARNING));
        message = message.insert(Message.raw("by ").color(MessageColors.GRAY));
        message = message.insert(formatEntityNameColored(targetName, "mob", targetLevel, targetClass));

        // Add key avoidance stats inline
        if (avoidanceStats != null) {
            String detail = switch (reason) {
                case DODGED -> String.format(" -- Dodge:%.0f%%", avoidanceStats.dodgeChance());
                case MISSED -> String.format(" -- Evasion:%.0f", avoidanceStats.evasion());
                case BLOCKED -> avoidanceStats.wasActiveBlock()
                    ? String.format(" -- Block:%.0f%% (active)", avoidanceStats.activeBlockChance())
                    : String.format(" -- Block:%.0f%%", avoidanceStats.passiveBlockChance());
                case PARRIED -> String.format(" -- Parry:%.0f%%", avoidanceStats.parryChance());
            };
            message = message.insert(Message.raw(detail).color(MessageColors.GRAY));
        }

        return message;
    }

    // ==================== Damage Formatters ====================

    /**
     * Formats full-formula damage dealt breakdown using a DamageTrace.
     *
     * <p>Shows every calculation step with intermediate values and optional
     * per-source stat attribution. Used when {@code /too combat detail} is on.
     *
     * @param trace The complete damage trace
     * @param targetName The target's display name
     * @param targetLevel The target's level
     * @param targetClass The target's mob class (null for players)
     * @return Formatted multi-line message for chat
     */
    @Nonnull
    public static Message formatDamageDealt(
        @Nonnull DamageTrace trace,
        @Nonnull String targetName,
        int targetLevel,
        @Nullable RPGMobClass targetClass
    ) {
        StatBreakdownResult bkd = trace.attackerBreakdown();
        Message m = Message.empty();

        // Summary header
        m = m.insert(Message.raw("\n").color(MessageColors.GRAY));
        m = m.insert(Message.raw("====== DAMAGE DEALT ======\n").color(MessageColors.SUCCESS));
        m = m.insert(Message.raw("Target: ").color(MessageColors.GRAY));
        m = m.insert(formatEntityNameColored(targetName, "mob", targetLevel, targetClass));
        m = m.insert(Message.raw("\n").color(MessageColors.GRAY));
        m = appendSummaryHeader(m, trace);

        // Section builders
        m = appendTracedBase(m, trace);
        m = appendTracedFlatPhysical(m, trace, bkd);
        m = appendTracedFlatElemental(m, trace);
        m = appendTracedConversion(m, trace);
        m = appendTracedPercentIncreased(m, trace, bkd);
        m = appendTracedElementalMods(m, trace);
        m = appendTracedMoreMultipliers(m, trace);
        m = appendTracedConditionals(m, trace);
        m = appendTracedCrit(m, trace, bkd);
        m = appendTracedDefenses(m, trace);
        m = appendTracedTrueDamage(m, trace);
        m = appendTracedPostCalc(m, trace);
        m = appendTracedAvoidanceContext(m, trace);
        m = appendTracedAilments(m, trace);

        // Total (uses effectiveFinalDamage which accounts for active blocking)
        m = m.insert(Message.raw("--------------------\n").color(MessageColors.GRAY));
        m = m.insert(Message.raw(String.format("Total:     %.1f\n",
            trace.effectiveFinalDamage())).color(MessageColors.SUCCESS));

        // Recovery & Thorns
        m = appendTracedRecovery(m, trace);
        m = appendTracedThorns(m, trace);

        // Footer
        m = m.insert(Message.raw("==========================\n").color(MessageColors.SUCCESS));
        return m;
    }

    /**
     * Formats full-formula damage received breakdown using a DamageTrace.
     *
     * @param trace The complete damage trace
     * @param snapshot The combat snapshot (for HP/attacker info)
     * @return Formatted multi-line message for chat
     */
    @Nonnull
    public static Message formatDamageReceived(
        @Nonnull DamageTrace trace,
        @Nonnull CombatSnapshot snapshot
    ) {
        // Defender's traced view: reuse the standard received format but enhanced
        // with trace-level defense details when available
        StatBreakdownResult bkd = trace.defenderBreakdown();
        Message m = Message.empty();

        // Header
        m = m.insert(Message.raw("\n").color(MessageColors.GRAY));
        m = m.insert(Message.raw("====== DAMAGE TAKEN ======\n").color(MessageColors.ERROR));
        m = m.insert(Message.raw("Attacker: ").color(MessageColors.GRAY));
        m = m.insert(formatEntityNameColored(
            snapshot.attackerName(), snapshot.attackerType(),
            snapshot.attackerLevel(), snapshot.attackerClass()));
        m = m.insert(Message.raw("\n").color(MessageColors.GRAY));

        // Incoming damage summary (pre-defense)
        m = m.insert(Message.raw("\n-- Incoming Attack --\n").color(MessageColors.WARNING));
        float totalPreDefense = trace.physBeforeArmor();
        for (ElementType elem : ElementType.values()) {
            totalPreDefense += trace.elemBeforeResist().getOrDefault(elem, 0f);
        }
        m = m.insert(Message.raw(String.format("Pre-Defense Total: %.1f", totalPreDefense)).color(MessageColors.WHITE));
        if (trace.wasCritical()) {
            m = m.insert(Message.raw(String.format(" (CRIT x%.2f)", trace.critMultiplierApplied())).color(MessageColors.GOLD));
        }
        m = m.insert(Message.raw("\n").color(MessageColors.GRAY));

        // Your defenses with trace detail
        m = m.insert(Message.raw("\n-- Your Defenses --\n").color(MessageColors.WARNING));

        // Armor
        float armor = trace.defenderArmor();
        float armorPct = trace.armorPercent();
        float armorPen = trace.armorPenPercent();
        float effArmor = trace.effectiveArmor();
        float armorRedPct = trace.armorReductionPercent();

        if (armor > 0) {
            StringBuilder armorLine = new StringBuilder();
            armorLine.append(String.format("Armor: %.0f", armor));
            if (armorPct > 0) armorLine.append(String.format(" x (1+%.0f%%)", armorPct));
            if (armorPen > 0) armorLine.append(String.format(" - %.0f%% pen", armorPen));
            armorLine.append(String.format(" = %.0f eff -> -%.1f%%", effArmor, armorRedPct));
            String armorSrc = statSources(bkd, ComputedStats::getArmor);
            armorLine.append(armorSrc);
            m = m.insert(Message.raw(armorLine + "\n").color(MessageColors.INFO));
        } else {
            m = m.insert(Message.raw("Armor: 0 (no protection)\n").color(MessageColors.GRAY));
        }

        // Physical resistance
        float physResist = trace.physResistPercent();
        if (physResist > 0) {
            m = m.insert(Message.raw(String.format("Phys Resist: %.1f%%\n", physResist)).color(MessageColors.INFO));
        }

        // Elemental resistances
        for (ElementType elem : ElementType.values()) {
            float raw = trace.defenderRawResist().getOrDefault(elem, 0f);
            float pen = trace.attackerPenetration().getOrDefault(elem, 0f);
            float eff = trace.effectiveResist().getOrDefault(elem, 0f);
            String color = getElementColor(elem);
            if (raw != 0 || pen != 0) {
                if (pen > 0) {
                    m = m.insert(Message.raw(String.format("%s: %.0f%% - %.0f%% pen = %.0f%% eff\n",
                        elem.getDisplayName(), raw, pen, eff)).color(color));
                } else {
                    m = m.insert(Message.raw(String.format("%s: %.0f%%\n",
                        elem.getDisplayName(), raw)).color(color));
                }
            }
        }

        // Damage taken breakdown
        m = m.insert(Message.raw("\n-- Damage Taken --\n").color(MessageColors.WARNING));

        // Physical after armor + resist
        m = m.insert(Message.raw(String.format("Physical: %.1f -> %.1f (armor) -> %.1f (resist)\n",
            trace.physBeforeArmor(), trace.physAfterArmor(), trace.physAfterResist())).color(MessageColors.WHITE));

        // Elemental after resist
        for (ElementType elem : ElementType.values()) {
            float before = trace.elemBeforeResist().getOrDefault(elem, 0f);
            float after = trace.elemAfterResist().getOrDefault(elem, 0f);
            if (before > 0 || after > 0) {
                String color = getElementColor(elem);
                float eff = trace.effectiveResist().getOrDefault(elem, 0f);
                if (eff != 0) {
                    m = m.insert(Message.raw(String.format("%s: %.1f x %.0f%% = %.1f\n",
                        elem.getDisplayName(), before, 100f - eff, after)).color(color));
                } else {
                    m = m.insert(Message.raw(String.format("%s: %.1f\n",
                        elem.getDisplayName(), after)).color(color));
                }
            }
        }

        // True damage
        if (trace.trueDamage() > 0) {
            m = m.insert(Message.raw(String.format("True: +%.1f (bypasses defenses)\n",
                trace.trueDamage())).color(MessageColors.PURPLE));
        }

        // Post-calc modifications
        boolean hasPostCalc = trace.shieldAbsorbed() > 0 || trace.wasParried()
            || trace.defenderCritNullifyChance() > 0 || trace.manaAbsorbed() > 0
            || trace.shockBonusPercent() > 0 || trace.unarmedMultiplier() != 0
            || trace.wasActiveBlocking();
        if (hasPostCalc) {
            m = m.insert(Message.raw("\n-- Post-Calc Modifications --\n").color(MessageColors.WARNING));

            // Crit nullify
            if (trace.defenderCritNullifyChance() > 0) {
                String result = trace.critWasNullified() ? "NULLIFIED" : "passed";
                String color = trace.critWasNullified() ? MessageColors.SUCCESS : MessageColors.GRAY;
                m = m.insert(Message.raw(String.format("Crit Nullify: %.0f%% chance -> %s\n",
                    trace.defenderCritNullifyChance(), result)).color(color));
            }

            // Energy shield
            if (trace.shieldAbsorbed() > 0 || trace.energyShieldCapacity() > 0) {
                float shieldAfter = Math.max(0f, trace.energyShieldBefore() - trace.shieldAbsorbed());
                float remainPct = trace.energyShieldCapacity() > 0
                    ? (shieldAfter / trace.energyShieldCapacity()) * 100f : 0f;
                m = m.insert(Message.raw(String.format("Shield: %.0f -> %.0f (absorbed %.1f, %.0f%% remaining)\n",
                    trace.energyShieldBefore(), shieldAfter, trace.shieldAbsorbed(), remainPct)).color(MessageColors.LIGHT_BLUE));
            }

            // Parry
            if (trace.wasParried()) {
                m = m.insert(Message.raw(String.format("Parried: taking %.0f%% damage\n",
                    trace.parryReductionMult() * 100f)).color(MessageColors.INFO));
            }

            // Mind over Matter
            if (trace.manaAbsorbed() > 0) {
                m = m.insert(Message.raw(String.format("Mana Buffer: %.0f%% -> absorbed %.1f\n",
                    trace.manaBufferPercent(), trace.manaAbsorbed())).color(MessageColors.INFO));
            }

            // Shock amplification
            if (trace.shockBonusPercent() > 0) {
                m = m.insert(Message.raw(String.format("Shock Amp: +%.0f%% applied\n",
                    trace.shockBonusPercent())).color(COLOR_LIGHTNING));
            }

            // Unarmed penalty
            if (trace.unarmedMultiplier() != 0) {
                m = m.insert(Message.raw(String.format("Unarmed: x%.2f (%.1f -> %.1f)\n",
                    trace.unarmedMultiplier(), trace.damageBeforeUnarmed(),
                    trace.damageBeforeUnarmed() * trace.unarmedMultiplier())).color(MessageColors.GRAY));
            }

            // Active blocking reduction
            if (trace.wasActiveBlocking()) {
                String blockType = trace.isShieldBlock() ? "Shield Block" : "Weapon Block";
                m = m.insert(Message.raw(String.format("%s: -%.0f%% (%.1f -> %.1f)\n",
                    blockType, trace.blockReductionPercent(),
                    trace.damageBeforeBlock(), trace.damageAfterBlock())).color(MessageColors.INFO));
            }
        }

        // Your avoidance stats that FAILED (why the hit connected)
        AvoidanceProcessor.AvoidanceDetail av = trace.avoidanceStats();
        if (av != null && (av.dodgeChance() > 0 || av.evasion() > 0
            || av.passiveBlockChance() > 0 || av.parryChance() > 0)) {
            m = m.insert(Message.raw("\n-- Your Avoidance (all failed) --\n").color(MessageColors.WARNING));
            if (av.dodgeChance() > 0) {
                m = m.insert(Message.raw(String.format("Dodge: %.1f%% -> failed\n",
                    av.dodgeChance())).color(MessageColors.GRAY));
            }
            if (av.evasion() > 0 || av.accuracy() > 0) {
                m = m.insert(Message.raw(String.format("Evasion: %.0f vs %.0f acc -> %.1f%% hit -> they hit\n",
                    av.evasion(), av.accuracy(), av.hitChance())).color(MessageColors.GRAY));
            }
            if (av.wasActiveBlock() && av.activeBlockChance() > 0) {
                m = m.insert(Message.raw(String.format("Perfect Block: %.0f%% -> failed\n",
                    av.activeBlockChance())).color(MessageColors.GRAY));
            }
            if (av.parryChance() > 0) {
                m = m.insert(Message.raw(String.format("Parry: %.0f%% -> failed\n",
                    av.parryChance())).color(MessageColors.GRAY));
            }
        }

        // Total (uses effectiveFinalDamage which accounts for active blocking)
        m = m.insert(Message.raw("--------------------\n").color(MessageColors.GRAY));
        float totalTaken = trace.effectiveFinalDamage();
        float defMaxHp = trace.defenderMaxHealth() > 0 ? trace.defenderMaxHealth() : snapshot.defenderMaxHealth();
        if (defMaxHp > 0) {
            float pctOfHp = (totalTaken / defMaxHp) * 100f;
            m = m.insert(Message.raw(String.format("Total Taken: %.1f (%.1f%% of %.0f HP)\n",
                totalTaken, pctOfHp, defMaxHp)).color(MessageColors.ERROR));
        } else {
            m = m.insert(Message.raw(String.format("Total Taken: %.1f\n",
                totalTaken)).color(MessageColors.ERROR));
        }

        // HP change
        m = m.insert(Message.raw("\n").color(MessageColors.GRAY));
        float hpPct = snapshot.defenderMaxHealth() > 0
            ? (snapshot.defenderHealthAfter() / snapshot.defenderMaxHealth()) * 100f : 0f;
        m = m.insert(Message.raw(String.format("Your HP: %.0f -> %.0f (%.0f%%)\n",
            snapshot.defenderHealthBefore(), snapshot.defenderHealthAfter(), hpPct)).color(MessageColors.PINK));

        // Footer
        m = m.insert(Message.raw("==========================\n").color(MessageColors.ERROR));
        return m;
    }

    // ==================== Traced Section Builders ====================

    /**
     * Appends a damage snapshot showing the full damage state at any pipeline step.
     *
     * <p>Format: {@code -> Phys: X | Fire: Y | Total: Z}
     * Only shows non-zero elemental types. Always shows physical and total.
     */
    @Nonnull
    private static Message appendDamageSnapshot(@Nonnull Message m, float phys,
                                                 @Nullable EnumMap<ElementType, Float> elemental) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("-> Phys: %.1f", phys));
        float total = phys;
        if (elemental != null) {
            for (ElementType elem : ElementType.values()) {
                float val = elemental.getOrDefault(elem, 0f);
                if (val > 0.05f) {
                    sb.append(String.format(" | %s: %.1f", elem.getDisplayName(), val));
                    total += val;
                }
            }
        }
        if (total != phys) {
            sb.append(String.format(" | Total: %.1f", total));
        }
        sb.append("\n");
        m = m.insert(Message.raw(sb.toString()).color(MessageColors.GRAY));
        return m;
    }

    /**
     * Builds an elemental map at a given pipeline step by combining flat + converted amounts.
     * Used for steps before elemental modifiers are applied.
     */
    @Nonnull
    private static EnumMap<ElementType, Float> buildElemMapPreMods(@Nonnull DamageTrace t) {
        EnumMap<ElementType, Float> map = new EnumMap<>(ElementType.class);
        for (ElementType elem : ElementType.values()) {
            float flat = t.flatElementalAdded().getOrDefault(elem, 0f);
            float converted = t.convertedAmounts().getOrDefault(elem, 0f);
            map.put(elem, flat + converted);
        }
        return map;
    }

    @Nonnull
    private static Message appendTracedBase(@Nonnull Message m, @Nonnull DamageTrace t) {
        m = m.insert(Message.raw("\n-- Base Damage --\n").color(MessageColors.WARNING));
        m = m.insert(Message.raw(String.format("Weapon Base:      %.1f\n",
            t.weaponBaseDamage())).color(MessageColors.WHITE));

        float atkMult = t.attackTypeMultiplier();

        // Show attack info as context annotation (multiplier is applied later in Step 7)
        if (atkMult != 1.0f && t.referenceDamage() > 0) {
            String atkName = t.attackName() != null ? t.attackName() : "(unknown)";
            float rawRatio = t.vanillaDamage() / t.referenceDamage();
            if (Math.abs(rawRatio - atkMult) > 0.01f) {
                // Value was clamped — show raw->clamped
                m = m.insert(Message.raw(String.format("Attack: %s (x%.2f raw, clamped to x%.2f)\n",
                    atkName, rawRatio, atkMult))
                    .color(MessageColors.GRAY));
            } else {
                // Not clamped — show derivation
                m = m.insert(Message.raw(String.format("Attack: %s (vanilla: %.1f / ref: %.1f = x%.2f)\n",
                    atkName, t.vanillaDamage(), t.referenceDamage(), atkMult))
                    .color(MessageColors.GRAY));
            }
        } else if (atkMult != 1.0f) {
            m = m.insert(Message.raw(String.format("Attack: %s (x%.2f)\n",
                t.attackType().name().toLowerCase(), atkMult))
                .color(MessageColors.GRAY));
        }

        m = appendDamageSnapshot(m, t.weaponBaseDamage(), null);
        return m;
    }

    @Nonnull
    private static Message appendTracedFlatPhysical(@Nonnull Message m, @Nonnull DamageTrace t,
                                                     @Nullable StatBreakdownResult bkd) {
        if (t.flatPhysFromStats() == 0 && t.flatMelee() == 0) return m;

        m = m.insert(Message.raw("\n-- Flat Physical --\n").color(MessageColors.WARNING));
        if (t.flatPhysFromStats() != 0) {
            String src = statSources(bkd, ComputedStats::getPhysicalDamage);
            m = m.insert(Message.raw(String.format("+ Physical:       %+.1f%s\n",
                t.flatPhysFromStats(), src)).color(MessageColors.WHITE));
        }
        if (t.flatMelee() != 0) {
            String src = statSources(bkd, ComputedStats::getMeleeDamage);
            m = m.insert(Message.raw(String.format("+ Melee:          %+.1f%s\n",
                t.flatMelee(), src)).color(MessageColors.WHITE));
        }
        m = m.insert(Message.raw(String.format("= Physical:       %.1f\n",
            t.physAfterFlat())).color(MessageColors.WHITE));
        m = appendDamageSnapshot(m, t.physAfterFlat(), null);
        return m;
    }

    @Nonnull
    private static Message appendTracedFlatElemental(@Nonnull Message m, @Nonnull DamageTrace t) {
        boolean hasAny = false;
        for (ElementType elem : ElementType.values()) {
            if (t.flatElementalAdded().getOrDefault(elem, 0f) > 0) { hasAny = true; break; }
        }
        if (!hasAny) return m;

        m = m.insert(Message.raw("\n-- Flat Elemental --\n").color(MessageColors.WARNING));
        for (ElementType elem : ElementType.values()) {
            float flat = t.flatElementalAdded().getOrDefault(elem, 0f);
            if (flat > 0) {
                m = m.insert(Message.raw(String.format("+ %s:  +%.1f\n",
                    padRight(elem.getDisplayName(), 12), flat)).color(getElementColor(elem)));
            }
        }
        m = appendDamageSnapshot(m, t.physAfterFlat(), t.flatElementalAdded());
        return m;
    }

    @Nonnull
    private static Message appendTracedConversion(@Nonnull Message m, @Nonnull DamageTrace t) {
        boolean hasConversion = false;
        for (ElementType elem : ElementType.values()) {
            if (t.conversionPercents().getOrDefault(elem, 0f) > 0) { hasConversion = true; break; }
        }
        if (!hasConversion) return m;

        m = m.insert(Message.raw("\n-- Conversion --\n").color(MessageColors.WARNING));
        for (ElementType elem : ElementType.values()) {
            float pct = t.conversionPercents().getOrDefault(elem, 0f);
            if (pct > 0) {
                float converted = t.convertedAmounts().getOrDefault(elem, 0f);
                m = m.insert(Message.raw(String.format("%s Conv: %.0f%% of %.1f = %.1f phys->%s\n",
                    elem.getDisplayName(), pct, t.physBeforeConversion(), converted,
                    elem.getDisplayName().toLowerCase())).color(getElementColor(elem)));
            }
        }
        if (t.scaleFactor() != 1.0f) {
            m = m.insert(Message.raw(String.format("Scale Factor: %.2f (total conv > 100%%)\n",
                t.scaleFactor())).color(MessageColors.GRAY));
        }
        m = m.insert(Message.raw(String.format("Physical: %.1f -> %.1f\n",
            t.physBeforeConversion(), t.physAfterConversion())).color(MessageColors.WHITE));
        m = appendDamageSnapshot(m, t.physAfterConversion(), buildElemMapPreMods(t));
        return m;
    }

    @Nonnull
    private static Message appendTracedPercentIncreased(@Nonnull Message m, @Nonnull DamageTrace t,
                                                         @Nullable StatBreakdownResult bkd) {
        if (t.totalIncreasedPercent() == 0) return m;

        m = m.insert(Message.raw("\n-- % Increased Physical --\n").color(MessageColors.WARNING));
        StringBuilder components = new StringBuilder();
        if (t.physDmgPercent() != 0) {
            components.append(String.format("physDmg%%: %.0f", t.physDmgPercent()));
        }
        if (t.attackTypePercent() != 0) {
            if (components.length() > 0) components.append(" + ");
            components.append(String.format("type%%: %.0f", t.attackTypePercent()));
        }
        if (t.globalDmgPercent() != 0) {
            if (components.length() > 0) components.append(" + ");
            components.append(String.format("dmg%%: %.0f", t.globalDmgPercent()));
        }
        m = m.insert(Message.raw(String.format("%s = +%.0f%%\n",
            components, t.totalIncreasedPercent())).color(MessageColors.INFO));
        m = m.insert(Message.raw(String.format("Physical: %.1f x %.2f = %.1f\n",
            t.physAfterConversion() > 0 ? t.physAfterConversion() : t.physAfterFlat(),
            1f + t.totalIncreasedPercent() / 100f, t.physAfterIncreased())).color(MessageColors.WHITE));
        m = appendDamageSnapshot(m, t.physAfterIncreased(), buildElemMapPreMods(t));
        return m;
    }

    @Nonnull
    private static Message appendTracedElementalMods(@Nonnull Message m, @Nonnull DamageTrace t) {
        boolean hasAny = false;
        for (ElementType elem : ElementType.values()) {
            float inc = t.elemPercentInc().getOrDefault(elem, 0f);
            float more = t.elemPercentMore().getOrDefault(elem, 0f);
            float after = t.elemAfterMods().getOrDefault(elem, 0f);
            if (inc != 0 || more != 0 || after > 0) { hasAny = true; break; }
        }
        if (!hasAny) return m;

        m = m.insert(Message.raw("\n-- Elemental Modifiers --\n").color(MessageColors.WARNING));
        for (ElementType elem : ElementType.values()) {
            float after = t.elemAfterMods().getOrDefault(elem, 0f);
            if (after <= 0) continue;

            float inc = t.elemPercentInc().getOrDefault(elem, 0f);
            float more = t.elemPercentMore().getOrDefault(elem, 0f);
            // Get pre-mod value: after mods / modifiers = pre-mod
            float incMult = 1f + inc / 100f;
            float moreMult = 1f + more / 100f;
            float preMod = (incMult > 0 && moreMult > 0) ? after / (incMult * moreMult) : after;

            m = m.insert(Message.raw(String.format("%s: %.1f x (1%+.0f%%inc) x (1%+.0f%%more) = %.1f\n",
                padRight(elem.getDisplayName(), 10), preMod, inc, more, after)).color(getElementColor(elem)));
        }
        m = appendDamageSnapshot(m, t.physAfterIncreased(), t.elemAfterMods());
        return m;
    }

    @Nonnull
    private static Message appendTracedMoreMultipliers(@Nonnull Message m, @Nonnull DamageTrace t) {
        if (t.allDamagePercent() == 0 && t.damageMultiplier() == 0) return m;

        m = m.insert(Message.raw("\n-- % More Multipliers --\n").color(MessageColors.WARNING));
        if (t.allDamagePercent() != 0) {
            m = m.insert(Message.raw(String.format("allDmg%%: %+.0f%% -> x%.2f\n",
                t.allDamagePercent(), 1f + t.allDamagePercent() / 100f)).color(MessageColors.INFO));
        }
        if (t.damageMultiplier() != 0) {
            m = m.insert(Message.raw(String.format("dmgMult: %+.0f%% -> x%.2f\n",
                t.damageMultiplier(), 1f + t.damageMultiplier() / 100f)).color(MessageColors.INFO));
        }
        m = m.insert(Message.raw(String.format("Phys After: %.1f\n",
            t.physAfterMore())).color(MessageColors.WHITE));

        // Show elemental after-more values if any
        for (ElementType elem : ElementType.values()) {
            float afterMore = t.elemAfterMore().getOrDefault(elem, 0f);
            if (afterMore > 0) {
                float beforeMore = t.elemAfterMods().getOrDefault(elem, 0f);
                if (Math.abs(afterMore - beforeMore) > 0.01f) {
                    m = m.insert(Message.raw(String.format("%s After: %.1f -> %.1f\n",
                        elem.getDisplayName(), beforeMore, afterMore)).color(getElementColor(elem)));
                }
            }
        }
        m = appendDamageSnapshot(m, t.physAfterMore(), t.elemAfterMore());
        return m;
    }

    @Nonnull
    private static Message appendTracedConditionals(@Nonnull Message m, @Nonnull DamageTrace t) {
        ConditionalResult cond = t.conditionals();
        float atkMult = t.attackTypeMultiplier();
        if (!cond.hasAny() && Math.abs(atkMult - 1.0f) < 0.001f) return m;

        m = m.insert(Message.raw("\n-- Conditional Multipliers --\n").color(MessageColors.WARNING));

        // Attack type multiplier (shown first)
        if (atkMult != 1.0f) {
            String atkLabel = t.attackName() != null ? t.attackName() : t.attackType().name().toLowerCase();
            m = m.insert(Message.raw(String.format("Attack Type:     x%.2f (%s)\n",
                atkMult, atkLabel)).color(MessageColors.INFO));
        }
        if (cond.realm() != 1.0f) {
            m = m.insert(Message.raw(String.format("Realm Damage:    x%.2f\n",
                cond.realm())).color(MessageColors.INFO));
        }
        if (cond.execute() != 1.0f) {
            m = m.insert(Message.raw(String.format("Execute (%+.0f%%):  x%.2f\n",
                (cond.execute() - 1f) * 100f, cond.execute())).color(MessageColors.ORANGE));
        }
        if (cond.vsFrozen() != 1.0f) {
            m = m.insert(Message.raw(String.format("vs Frozen (%+.0f%%): x%.2f\n",
                (cond.vsFrozen() - 1f) * 100f, cond.vsFrozen())).color(COLOR_WATER));
        }
        if (cond.vsShocked() != 1.0f) {
            m = m.insert(Message.raw(String.format("vs Shocked (%+.0f%%): x%.2f\n",
                (cond.vsShocked() - 1f) * 100f, cond.vsShocked())).color(COLOR_LIGHTNING));
        }
        if (cond.lowLife() != 1.0f) {
            m = m.insert(Message.raw(String.format("Low Life (%+.0f%%): x%.2f\n",
                (cond.lowLife() - 1f) * 100f, cond.lowLife())).color(MessageColors.ERROR));
        }
        if (cond.consecutive() != 1.0f) {
            m = m.insert(Message.raw(String.format("Consecutive (%+.0f%%): x%.2f\n",
                (cond.consecutive() - 1f) * 100f, cond.consecutive())).color(MessageColors.WHITE));
        }
        float fullCombined = cond.combined() * atkMult;
        m = m.insert(Message.raw(String.format("Combined:        x%.4f\n",
            fullCombined)).color(MessageColors.WHITE));

        // Show resulting damage per type
        float physBefore = t.physAfterMore();
        float physAfter = t.physAfterConditionals();
        if (physBefore > 0.05f) {
            m = m.insert(Message.raw(String.format("Physical: %.1f x %.4f = %.1f\n",
                physBefore, fullCombined, physAfter)).color(MessageColors.WHITE));
        }
        for (ElementType elem : ElementType.values()) {
            float elemBefore = t.elemAfterMore().getOrDefault(elem, 0f);
            float elemAfter = t.elemAfterConditionals().getOrDefault(elem, 0f);
            if (elemBefore > 0.05f) {
                m = m.insert(Message.raw(String.format("%s: %.1f x %.4f = %.1f\n",
                    elem.getDisplayName(), elemBefore, fullCombined, elemAfter)).color(getElementColor(elem)));
            }
        }
        m = appendDamageSnapshot(m, t.physAfterConditionals(), t.elemAfterConditionals());
        return m;
    }

    @Nonnull
    private static Message appendTracedCrit(@Nonnull Message m, @Nonnull DamageTrace t,
                                             @Nullable StatBreakdownResult bkd) {
        m = m.insert(Message.raw("\n-- Critical Strike --\n").color(MessageColors.WARNING));

        String chanceSrc = statSources(bkd, ComputedStats::getCriticalChance);
        m = m.insert(Message.raw(String.format("Crit Chance: %.1f%%%s\n",
            t.critChance(), chanceSrc)).color(MessageColors.INFO));

        if (t.wasCritical()) {
            String multSrc = statSources(bkd, cs -> cs.getCriticalMultiplier() / 100f);
            m = m.insert(Message.raw("Roll: CRIT!\n").color(MessageColors.GOLD));
            m = m.insert(Message.raw(String.format("Crit Multi:  x%.2f%s\n",
                t.critMultiplierApplied(), multSrc)).color(MessageColors.GOLD));

            // Show resulting damage per type
            float physBefore = t.physAfterConditionals();
            float physAfter = t.physAfterCrit();
            if (physBefore > 0.05f) {
                m = m.insert(Message.raw(String.format("Physical: %.1f x %.2f = %.1f\n",
                    physBefore, t.critMultiplierApplied(), physAfter)).color(MessageColors.WHITE));
            }
            for (ElementType elem : ElementType.values()) {
                float elemBefore = t.elemAfterConditionals().getOrDefault(elem, 0f);
                float elemAfter = t.elemAfterCrit().getOrDefault(elem, 0f);
                if (elemBefore > 0.05f) {
                    m = m.insert(Message.raw(String.format("%s: %.1f x %.2f = %.1f\n",
                        elem.getDisplayName(), elemBefore, t.critMultiplierApplied(), elemAfter)).color(getElementColor(elem)));
                }
            }
        } else {
            m = m.insert(Message.raw("Roll: No crit\n").color(MessageColors.GRAY));
        }
        m = appendDamageSnapshot(m, t.physAfterCrit(), t.elemAfterCrit());
        return m;
    }

    @Nonnull
    private static Message appendTracedDefenses(@Nonnull Message m, @Nonnull DamageTrace t) {
        m = m.insert(Message.raw("\n-- Defenses --\n").color(MessageColors.WARNING));

        // Armor chain
        float armor = t.defenderArmor();
        float armorPct = t.armorPercent();
        float pen = t.armorPenPercent();
        float eff = t.effectiveArmor();
        float redPct = t.armorReductionPercent();

        if (armor > 0 || eff > 0) {
            StringBuilder armorLine = new StringBuilder(String.format("Armor: %.0f", armor));
            if (armorPct > 0) armorLine.append(String.format(" x (1+%.0f%%)", armorPct));
            armorLine.append(String.format(" = %.0f", eff + (pen > 0 ? eff * pen / (100f - pen) : 0)));
            if (pen > 0) armorLine.append(String.format("\nPen: %.0f%% -> %.0f eff", pen, eff));
            m = m.insert(Message.raw(armorLine + "\n").color(MessageColors.INFO));
            m = m.insert(Message.raw(String.format("Formula: %.0f / (%.0f + 10x%.1f) = %.1f%%\n",
                eff, eff, t.physBeforeArmor(), redPct)).color(MessageColors.GRAY));
        }

        // Physical: before armor -> after armor -> after resist
        m = m.insert(Message.raw(String.format("Physical: %.1f x %.1f%% = %.1f\n",
            t.physBeforeArmor(), 100f - redPct, t.physAfterArmor())).color(MessageColors.WHITE));

        // Physical resistance
        float physResist = t.physResistPercent();
        if (physResist > 0) {
            m = m.insert(Message.raw(String.format("Phys Resist: %.1f%% -> %.1f x %.1f%% = %.1f\n",
                physResist, t.physAfterArmor(), 100f - physResist, t.physAfterResist())).color(MessageColors.INFO));
        }

        // Elemental resistances
        for (ElementType elem : ElementType.values()) {
            float before = t.elemBeforeResist().getOrDefault(elem, 0f);
            if (before <= 0) continue;

            float raw = t.defenderRawResist().getOrDefault(elem, 0f);
            float elemPen = t.attackerPenetration().getOrDefault(elem, 0f);
            float effResist = t.effectiveResist().getOrDefault(elem, 0f);
            float after = t.elemAfterResist().getOrDefault(elem, 0f);
            String color = getElementColor(elem);

            if (effResist != 0) {
                StringBuilder line = new StringBuilder(String.format("%s: %.0f%%",
                    elem.getDisplayName(), raw));
                if (elemPen > 0) line.append(String.format(" - %.0f%% pen = %.0f%% eff", elemPen, effResist));
                line.append(String.format(" -> %.1f x %.0f%% = %.1f", before, 100f - effResist, after));
                m = m.insert(Message.raw(line + "\n").color(color));
            } else {
                m = m.insert(Message.raw(String.format("%s: 0%% -> %.1f\n",
                    elem.getDisplayName(), after)).color(color));
            }
        }
        m = appendDamageSnapshot(m, t.physAfterResist(), t.elemAfterResist());
        return m;
    }

    @Nonnull
    private static Message appendTracedTrueDamage(@Nonnull Message m, @Nonnull DamageTrace t) {
        if (t.trueDamage() <= 0) return m;
        m = m.insert(Message.raw("\n-- True Damage --\n").color(MessageColors.WARNING));
        m = m.insert(Message.raw(String.format("True: +%.1f (bypasses defenses)\n",
            t.trueDamage())).color(MessageColors.PURPLE));
        return m;
    }

    @Nonnull
    private static Message appendTracedPostCalc(@Nonnull Message m, @Nonnull DamageTrace t) {
        boolean hasAny = t.shieldAbsorbed() > 0 || t.wasParried()
            || t.defenderCritNullifyChance() > 0 || t.manaAbsorbed() > 0
            || t.shockBonusPercent() > 0 || t.damageTakenModifier() != 0
            || t.unarmedMultiplier() != 0 || t.wasActiveBlocking();
        if (!hasAny) return m;

        m = m.insert(Message.raw("\n-- Post-Calc Modifications --\n").color(MessageColors.WARNING));

        // Crit nullify
        if (t.defenderCritNullifyChance() > 0) {
            String result = t.critWasNullified() ? "NULLIFIED" : "passed";
            String color = t.critWasNullified() ? MessageColors.ERROR : MessageColors.GRAY;
            m = m.insert(Message.raw(String.format("Crit Nullify: %.0f%% chance -> %s\n",
                t.defenderCritNullifyChance(), result)).color(color));
        }

        // Parry
        if (t.parryChance() > 0 || t.wasParried()) {
            if (t.wasParried()) {
                m = m.insert(Message.raw(String.format("Parry: %.0f%% chance -> PARRIED (taking %.0f%% dmg, after: %.1f)\n",
                    t.parryChance(), t.parryReductionMult() * 100f, t.damageAfterParry())).color(MessageColors.INFO));
            } else {
                m = m.insert(Message.raw(String.format("Parry: %.0f%% chance -> no\n",
                    t.parryChance())).color(MessageColors.GRAY));
            }
        }

        // Energy shield
        if (t.shieldAbsorbed() > 0 || t.energyShieldCapacity() > 0) {
            float shieldAfter = t.energyShieldBefore() - t.shieldAbsorbed();
            m = m.insert(Message.raw(String.format("Shield: %.0f/%.0f -> %.0f/%.0f (absorbed %.1f)\n",
                t.energyShieldBefore(), t.energyShieldCapacity(),
                Math.max(0f, shieldAfter), t.energyShieldCapacity(),
                t.shieldAbsorbed())).color(MessageColors.LIGHT_BLUE));
        }

        // Mind over Matter
        if (t.manaAbsorbed() > 0) {
            m = m.insert(Message.raw(String.format("Mana Buffer: %.0f%% -> %.1f mana absorbed\n",
                t.manaBufferPercent(), t.manaAbsorbed())).color(MessageColors.INFO));
        }

        // Shock amplification
        if (t.shockBonusPercent() > 0) {
            float afterShock = t.damageBeforeShock() * (1f + t.shockBonusPercent() / 100f);
            m = m.insert(Message.raw(String.format("Shock Amp: +%.0f%% -> %.1f x %.2f = %.1f\n",
                t.shockBonusPercent(), t.damageBeforeShock(),
                1f + t.shockBonusPercent() / 100f, afterShock)).color(COLOR_LIGHTNING));
        }

        // Damage taken modifier
        if (t.damageTakenModifier() != 0) {
            m = m.insert(Message.raw(String.format("Dmg Taken: x%.2f (defender has %+.0f%% damage taken)\n",
                1f + t.damageTakenModifier() / 100f, t.damageTakenModifier())).color(MessageColors.GRAY));
        }

        // Unarmed penalty
        if (t.unarmedMultiplier() != 0) {
            m = m.insert(Message.raw(String.format("Unarmed: x%.2f (%.1f -> %.1f)\n",
                t.unarmedMultiplier(), t.damageBeforeUnarmed(),
                t.damageBeforeUnarmed() * t.unarmedMultiplier())).color(MessageColors.GRAY));
        }

        // Active blocking reduction
        if (t.wasActiveBlocking()) {
            String blockType = t.isShieldBlock() ? "Shield Block" : "Weapon Block";
            m = m.insert(Message.raw(String.format("%s: -%.0f%% (%.1f -> %.1f)\n",
                blockType, t.blockReductionPercent(),
                t.damageBeforeBlock(), t.damageAfterBlock())).color(MessageColors.INFO));
        }

        return m;
    }

    @Nonnull
    private static Message appendTracedRecovery(@Nonnull Message m, @Nonnull DamageTrace t) {
        if (t.lifeLeechAmount() <= 0 && t.lifeStealAmount() <= 0
            && t.manaLeechAmount() <= 0 && t.manaStealAmount() <= 0) return m;

        m = m.insert(Message.raw("\n-- Recovery --\n").color(MessageColors.WARNING));
        if (t.lifeLeechAmount() > 0) {
            m = m.insert(Message.raw(String.format("Life Leech: %.1f%% x %.1f = %.1f HP\n",
                t.lifeLeechPercent(), t.breakdown().totalDamage(), t.lifeLeechAmount())).color(MessageColors.SUCCESS));
        }
        if (t.lifeStealAmount() > 0) {
            m = m.insert(Message.raw(String.format("Life Steal: %.1f%% x %.1f = %.1f HP\n",
                t.lifeStealPercent(), t.breakdown().totalDamage(), t.lifeStealAmount())).color(MessageColors.SUCCESS));
        }
        if (t.manaLeechAmount() > 0) {
            m = m.insert(Message.raw(String.format("Mana Leech: %.1f%% x %.1f = %.1f mana\n",
                t.manaLeechPercent(), t.breakdown().totalDamage(), t.manaLeechAmount())).color(MessageColors.INFO));
        }
        if (t.manaStealAmount() > 0) {
            m = m.insert(Message.raw(String.format("Mana Steal: %.1f%% x %.1f = %.1f mana\n",
                t.manaStealPercent(), t.breakdown().totalDamage(), t.manaStealAmount())).color(MessageColors.INFO));
        }
        return m;
    }

    @Nonnull
    private static Message appendTracedThorns(@Nonnull Message m, @Nonnull DamageTrace t) {
        if (t.totalThornsReturned() <= 0) return m;

        m = m.insert(Message.raw("\n-- Thorns Returned --\n").color(MessageColors.WARNING));
        if (t.thornsDamageFlat() > 0) {
            m = m.insert(Message.raw(String.format("Flat: %.0f x (1+%.0f%%) = %.1f\n",
                t.thornsDamageFlat(), t.thornsDamagePercent(),
                t.thornsDamageFlat() * (1f + t.thornsDamagePercent() / 100f))).color(MessageColors.ORANGE));
        }
        if (t.reflectDamagePercent() > 0) {
            m = m.insert(Message.raw(String.format("Reflect: %.0f%% x %.1f = %.1f\n",
                t.reflectDamagePercent(), t.breakdown().totalDamage(),
                t.breakdown().totalDamage() * t.reflectDamagePercent() / 100f)).color(MessageColors.ORANGE));
        }
        m = m.insert(Message.raw(String.format("Total: %.1f back to you\n",
            t.totalThornsReturned())).color(MessageColors.ERROR));
        return m;
    }

    // ==================== New Traced Sections ====================

    /**
     * Appends a compact summary header at the top of damage dealt.
     * Shows weapon, attack info, total as % of HP, and damage composition.
     */
    @Nonnull
    private static Message appendSummaryHeader(@Nonnull Message m, @Nonnull DamageTrace t) {
        // Weapon and attack info
        String weapon = t.weaponItemId() != null ? t.weaponItemId() : "Unarmed";
        m = m.insert(Message.raw(String.format("Weapon: %s", weapon)).color(MessageColors.WHITE));
        if (t.attackTypeMultiplier() != 1.0f) {
            m = m.insert(Message.raw(String.format(" | %s x%.2f",
                t.attackType().name().toLowerCase(), t.attackTypeMultiplier())).color(MessageColors.INFO));
        }
        if (t.attackSpeedPercent() != 0) {
            m = m.insert(Message.raw(String.format(" | AtkSpd: %+.0f%%", t.attackSpeedPercent())).color(MessageColors.GRAY));
        }
        m = m.insert(Message.raw("\n").color(MessageColors.GRAY));

        // Total and % of HP (uses effectiveFinalDamage to account for blocking)
        float total = t.effectiveFinalDamage();
        if (t.defenderMaxHealth() > 0) {
            float pctOfHp = (total / t.defenderMaxHealth()) * 100f;
            m = m.insert(Message.raw(String.format("Total: %.1f | %.1f%% of target HP (%.0f)\n",
                total, pctOfHp, t.defenderMaxHealth())).color(MessageColors.SUCCESS));
        }

        // Damage composition
        DamageBreakdown bd = t.breakdown();
        float phys = bd.physicalDamage();
        float trueDmg = bd.trueDamage();
        if (total > 0) {
            StringBuilder comp = new StringBuilder("  ");
            if (phys > 0) comp.append(String.format("Physical: %.1f (%.0f%%)  ", phys, phys / total * 100f));
            for (ElementType elem : ElementType.values()) {
                float elemDmg = bd.getElementalDamage(elem);
                if (elemDmg > 0) comp.append(String.format("%s: %.1f (%.0f%%)  ",
                    elem.getDisplayName(), elemDmg, elemDmg / total * 100f));
            }
            if (trueDmg > 0) comp.append(String.format("True: %.1f (%.0f%%)", trueDmg, trueDmg / total * 100f));
            m = m.insert(Message.raw(comp.toString().trim() + "\n").color(MessageColors.GRAY));
        }

        // Crit indicator
        if (t.wasCritical()) {
            m = m.insert(Message.raw(String.format("CRIT x%.2f\n", t.critMultiplierApplied())).color(MessageColors.GOLD));
        }

        m = m.insert(Message.raw("----\n").color(MessageColors.GRAY));
        return m;
    }

    /**
     * Appends target's avoidance stats that FAILED (why the hit connected).
     * Only shown on damage dealt view.
     */
    @Nonnull
    private static Message appendTracedAvoidanceContext(@Nonnull Message m, @Nonnull DamageTrace t) {
        AvoidanceProcessor.AvoidanceDetail av = t.avoidanceStats();
        if (av == null) return m;

        // Only show if target had any avoidance stats
        if (av.dodgeChance() <= 0 && av.evasion() <= 0 && av.passiveBlockChance() <= 0
            && av.activeBlockChance() <= 0 && av.parryChance() <= 0) return m;

        m = m.insert(Message.raw("\n-- Target Avoidance (all passed) --\n").color(MessageColors.WARNING));

        if (av.dodgeChance() > 0) {
            m = m.insert(Message.raw(String.format("Dodge: %.1f%% -> passed\n",
                av.dodgeChance())).color(MessageColors.GRAY));
        }
        if (av.evasion() > 0 || av.accuracy() > 0) {
            m = m.insert(Message.raw(String.format("Evasion: %.0f vs your %.0f acc -> %.1f%% hit -> hit\n",
                av.evasion(), av.accuracy(), av.hitChance())).color(MessageColors.GRAY));
        }
        if (av.wasActiveBlock() && av.activeBlockChance() > 0) {
            m = m.insert(Message.raw(String.format("Perfect Block: %.0f%% -> passed\n",
                av.activeBlockChance())).color(MessageColors.GRAY));
        }
        if (av.parryChance() > 0) {
            m = m.insert(Message.raw(String.format("Parry: %.0f%% -> passed\n",
                av.parryChance())).color(MessageColors.GRAY));
        }
        return m;
    }

    /**
     * Appends ailment application results per element.
     */
    @Nonnull
    private static Message appendTracedAilments(@Nonnull Message m, @Nonnull DamageTrace t) {
        CombatAilmentApplicator.AilmentSummary summary = t.ailmentSummary();
        if (summary == null || summary.attempts().isEmpty()) return m;

        m = m.insert(Message.raw("\n-- Ailment Applications --\n").color(MessageColors.WARNING));
        for (CombatAilmentApplicator.AilmentAttempt attempt : summary.attempts()) {
            String elemColor = getElementColor(attempt.element());
            String ailmentName = attempt.ailmentName() != null ? attempt.ailmentName() : "?";

            if (attempt.applied()) {
                m = m.insert(Message.raw(String.format("%s (%.1f dmg): %s %.0f%% -> APPLIED (roll %.1f)",
                    attempt.element().getDisplayName(), attempt.elementalDamage(),
                    ailmentName, attempt.applicationChance(), attempt.roll())).color(elemColor));
                m = m.insert(Message.raw(String.format(" -- %.1f mag, %.1fs\n",
                    attempt.magnitude(), attempt.durationSeconds())).color(MessageColors.GRAY));
            } else {
                m = m.insert(Message.raw(String.format("%s (%.1f dmg): %s %.0f%% -> FAILED (roll %.1f)\n",
                    attempt.element().getDisplayName(), attempt.elementalDamage(),
                    ailmentName, attempt.applicationChance(), attempt.roll())).color(MessageColors.GRAY));
            }
        }
        return m;
    }

    // ==================== Stat Source Attribution ====================

    /**
     * Formats per-source stat attribution string from a StatBreakdownResult.
     *
     * <p>Computes deltas between pipeline stages:
     * base -> afterAttributes -> afterSkillTree -> afterGear.
     * Returns empty string if breakdown is null or all deltas are zero.
     *
     * @param bkd The stat breakdown result (may be null)
     * @param getter Function to extract the stat value from each ComputedStats snapshot
     * @return Formatted string like " (Base:5.0 + Attr:+12.0 + Gear:+15.5)" or empty
     */
    @Nonnull
    private static String statSources(
        @Nullable StatBreakdownResult bkd,
        @Nonnull Function<ComputedStats, Float> getter
    ) {
        if (bkd == null) return "";

        float base = getter.apply(bkd.base());
        float attr = getter.apply(bkd.afterAttributes()) - base;
        float tree = getter.apply(bkd.afterSkillTree()) - getter.apply(bkd.afterAttributes());
        float gear = getter.apply(bkd.afterGear()) - getter.apply(bkd.afterSkillTree());
        float cond = getter.apply(bkd.afterConditionals()) - getter.apply(bkd.afterGear());

        StringBuilder sb = new StringBuilder();
        boolean any = false;

        if (base != 0) { sb.append(String.format("Base:%.1f", base)); any = true; }
        if (Math.abs(attr) > 0.01f) {
            if (any) sb.append(" + ");
            sb.append(String.format("Attr:%+.1f", attr)); any = true;
        }
        if (Math.abs(tree) > 0.01f) {
            if (any) sb.append(" + ");
            sb.append(String.format("Tree:%+.1f", tree)); any = true;
        }
        if (Math.abs(gear) > 0.01f) {
            if (any) sb.append(" + ");
            sb.append(String.format("Gear:%+.1f", gear)); any = true;
        }
        if (Math.abs(cond) > 0.01f) {
            if (any) sb.append(" + ");
            sb.append(String.format("Cond:%+.1f", cond)); any = true;
        }

        if (!any) return "";
        return " (" + sb + ")";
    }

    /** Pads a string to the right with spaces. */
    @Nonnull
    private static String padRight(@Nonnull String s, int width) {
        if (s.length() >= width) return s;
        return s + " ".repeat(width - s.length());
    }

    // ==================== Helper Methods ====================

    /** Formats entity name with colored mob class prefix and level. */
    @Nonnull
    private static Message formatEntityNameColored(
        @Nonnull String name,
        @Nonnull String type,
        int level,
        @Nullable RPGMobClass mobClass
    ) {
        if ("environment".equals(type)) {
            return Message.raw(name).color(MessageColors.ORANGE);
        }

        Message message = Message.empty();

        // Add class prefix for special mobs
        if (mobClass != null && mobClass != RPGMobClass.HOSTILE && mobClass != RPGMobClass.PASSIVE) {
            String classColor = getClassColor(mobClass);
            message = message.insert(Message.raw("[" + formatClassName(mobClass) + "] ").color(classColor));
        }

        // Add name
        String nameColor = "player".equals(type) ? MessageColors.INFO : MessageColors.WHITE;
        message = message.insert(Message.raw(name).color(nameColor));

        // Add level
        if (level > 0) {
            message = message.insert(Message.raw(" (Lv" + level + ")").color(MessageColors.GRAY));
        }

        return message;
    }

    /**
     * Appends attack section to message for damage dealt format.
     */
    @Nonnull
    private static Message appendAttackSection(@Nonnull Message message, @Nonnull CombatSnapshot snapshot) {
        // Base damage
        message = message.insert(Message.raw(String.format("Base Damage :     %.1f\n",
            snapshot.baseDamage())).color(MessageColors.WHITE));

        // Flat bonus
        if (snapshot.flatBonus() > 0) {
            message = message.insert(Message.raw(String.format("+ Flat Bonus :    +%.1f\n",
                snapshot.flatBonus())).color(MessageColors.SUCCESS));
        }

        // Percent bonus
        if (snapshot.percentBonus() > 0) {
            float percentAdd = snapshot.baseDamage() * (snapshot.percentBonus() / 100f);
            message = message.insert(Message.raw(String.format("+ %% Bonus :       +%.0f%% (+%.1f)\n",
                snapshot.percentBonus(), percentAdd)).color(MessageColors.SUCCESS));
        }

        // Critical hit
        if (snapshot.wasCritical()) {
            message = message.insert(Message.raw(String.format("* CRIT ! :         x%.1f (%.1f)\n",
                snapshot.critMultiplier(), snapshot.damageAfterAttackerBonuses())).color(MessageColors.GOLD));
        }

        // Pre-defense total
        message = message.insert(Message.raw("--------------------\n").color(MessageColors.GRAY));
        message = message.insert(Message.raw(String.format("Pre-Defense :     %.1f\n",
            snapshot.damageAfterAttackerBonuses())).color(MessageColors.WHITE));

        return message;
    }

    /**
     * Appends defense section to message.
     */
    @Nonnull
    private static Message appendDefenseSection(@Nonnull Message message, @Nonnull CombatSnapshot snapshot) {
        // Armor
        if (snapshot.defenderArmor() > 0) {
            if (snapshot.armorPenetration() > 0) {
                message = message.insert(Message.raw(String.format("Armor : %.0f (%.0f%% pen -> %.0f eff) -> -%.0f%%\n",
                    snapshot.defenderArmor(), snapshot.armorPenetration(),
                    snapshot.effectiveArmor(), snapshot.reductionPercent())).color(MessageColors.INFO));
            } else {
                message = message.insert(Message.raw(String.format("Armor : %.0f -> -%.0f%%\n",
                    snapshot.defenderArmor(), snapshot.reductionPercent())).color(MessageColors.INFO));
            }
        }

        // Elemental resistances
        if (snapshot.defenderRawResistances() != null) {
            Map<ElementType, Float> attackerPen = snapshot.attackerPenetration();
            for (ElementType elem : ElementType.values()) {
                Float rawResist = snapshot.defenderRawResistances().get(elem);
                if (rawResist != null && rawResist != 0f) {
                    float pen = (attackerPen != null) ? attackerPen.getOrDefault(elem, 0f) : 0f;
                    String color = getElementColor(elem);

                    if (pen > 0 && rawResist > 0) {
                        float effectiveResist = Math.max(0, rawResist - pen);
                        message = message.insert(Message.raw(String.format("%s Resist : %.0f%% (%.0f%% pen -> %.0f%% eff)\n",
                            elem.getDisplayName(), rawResist, pen, effectiveResist)).color(color));
                    } else {
                        message = message.insert(Message.raw(String.format("%s Resist : %.0f%%\n",
                            elem.getDisplayName(), rawResist)).color(color));
                    }
                }
            }
        }

        return message;
    }

    /**
     * Appends damage applied section to message with full calculation details.
     */
    @Nonnull
    private static Message appendDamageApplied(@Nonnull Message message, @Nonnull CombatSnapshot snapshot) {
        // Physical damage with armor calculation
        if (snapshot.damageAfterArmor() > 0 || snapshot.reductionPercent() > 0) {
            // Back-calculate pre-armor damage from post-armor damage
            float postArmor = snapshot.damageAfterArmor();
            float reductionPct = snapshot.reductionPercent();
            float preArmor = reductionPct > 0 ? postArmor / (1f - reductionPct / 100f) : postArmor;

            if (reductionPct > 0) {
                message = message.insert(Message.raw(String.format("Physical :        %.1f (%.1f * (1 - %.0f%%) = %.1f)\n",
                    postArmor, preArmor, reductionPct, postArmor)).color(MessageColors.WHITE));
            } else {
                message = message.insert(Message.raw(String.format("Physical :        %.1f\n",
                    postArmor)).color(MessageColors.WHITE));
            }
        }

        // Elemental damage with full calculation chain
        // Uses EFFECTIVE resistance (from elementalResist) for accurate display
        if (snapshot.hasElementalDamage() && snapshot.elementalDamage() != null) {
            for (ElementType elem : ElementType.values()) {
                Float elemDmg = snapshot.elementalDamage().get(elem);
                if (elemDmg != null && elemDmg > 0) {
                    // Get effective resistance (what was actually used in calculation)
                    float effectiveResist = 0f;
                    if (snapshot.elementalResist() != null) {
                        Float r = snapshot.elementalResist().get(elem);
                        effectiveResist = r != null ? r : 0f;
                    }

                    String color = getElementColor(elem);

                    // Back-calculate pre-resist damage using effective resistance
                    float preResist;
                    if (effectiveResist > 0) {
                        preResist = elemDmg / (1f - effectiveResist / 100f);
                    } else if (effectiveResist < 0) {
                        preResist = elemDmg / (1f + Math.abs(effectiveResist) / 100f);
                    } else {
                        preResist = elemDmg;
                    }

                    if (effectiveResist != 0) {
                        message = message.insert(Message.raw(String.format(
                            "%s:            +%.1f (%.1f * (1 - %.0f%%) = %.1f)\n",
                            elem.getDisplayName(), elemDmg, preResist, effectiveResist, elemDmg)).color(color));
                    } else {
                        message = message.insert(Message.raw(String.format(
                            "%s :            +%.1f\n",
                            elem.getDisplayName(), elemDmg)).color(color));
                    }
                }
            }
        }

        // Total
        message = message.insert(Message.raw("--------------------\n").color(MessageColors.GRAY));
        message = message.insert(Message.raw(String.format("Total Dealt :     %.1f\n",
            snapshot.finalDamage())).color(MessageColors.SUCCESS));

        return message;
    }

    private static boolean hasAnyResistance(@Nonnull CombatSnapshot snapshot) {
        if (snapshot.defenderRawResistances() == null) {
            return false;
        }
        for (Float resist : snapshot.defenderRawResistances().values()) {
            if (resist != null && resist != 0f) {
                return true;
            }
        }
        return false;
    }

    @Nonnull
    private static String getElementColor(@Nonnull ElementType element) {
        return switch (element) {
            case FIRE -> COLOR_FIRE;
            case WATER -> COLOR_WATER;
            case LIGHTNING -> COLOR_LIGHTNING;
            case EARTH -> COLOR_EARTH;
            case WIND -> COLOR_WIND;
            case VOID -> COLOR_VOID;
        };
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
            case MINOR -> MessageColors.WARNING;
            case PASSIVE -> MessageColors.GRAY;
        };
    }
}
