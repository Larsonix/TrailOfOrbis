package io.github.larsonix.trailoforbis.combat.format;

import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.attributes.breakdown.StatBreakdownResult;
import io.github.larsonix.trailoforbis.combat.AttackType;
import io.github.larsonix.trailoforbis.combat.DamageBreakdown;
import io.github.larsonix.trailoforbis.combat.DamageTrace;
import io.github.larsonix.trailoforbis.combat.ailments.CombatAilmentApplicator;
import io.github.larsonix.trailoforbis.combat.avoidance.AvoidanceProcessor;
import io.github.larsonix.trailoforbis.combat.modifiers.ConditionalResult;
import io.github.larsonix.trailoforbis.elemental.ElementType;
import io.github.larsonix.trailoforbis.util.MessageColors;
import io.github.larsonix.trailoforbis.util.NumberFormatter;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.function.Function;

import static io.github.larsonix.trailoforbis.combat.format.CombatFormatConstants.*;

/**
 * Stateless section renderers for combat feedback messages.
 *
 * <p>Each method renders one logical section of the damage pipeline display,
 * appending lines to a {@link MessageBuilder}. Shared by both the combat
 * detail formatter (attacker/defender views) and the death recap formatter.
 */
public final class SectionRenderer {

    private SectionRenderer() {}

    // ==================== Summary Header ====================

    /**
     * Renders the compact summary header at the top of damage dealt view.
     * Shows weapon, attack info, total as % of HP, and damage composition.
     */
    public static void renderSummaryHeader(@Nonnull MessageBuilder mb, @Nonnull DamageTrace t) {
        // Weapon and attack info
        String weapon = t.weaponItemId() != null ? t.weaponItemId() : "Unarmed";
        mb.text("Weapon: " + weapon, MessageColors.WHITE);
        if (t.attackTypeMultiplier() != 1.0f) {
            mb.text(" | " + t.attackType().name().toLowerCase() + " " + NumberFormatter.multiplier(t.attackTypeMultiplier()), MessageColors.INFO);
        }
        if (t.attackSpeedPercent() != 0) {
            mb.text(" | AtkSpd: " + NumberFormatter.signedPercent(t.attackSpeedPercent()), MessageColors.GRAY);
        }
        mb.line("", MessageColors.GRAY);

        // Total and % of HP
        float total = t.effectiveFinalDamage();
        if (t.defenderMaxHealth() > 0) {
            float pctOfHp = (total / t.defenderMaxHealth()) * 100f;
            mb.line("Total: " + NumberFormatter.smallFlat(total) + " | " + NumberFormatter.percent(pctOfHp) + " of target HP (" + NumberFormatter.flat(t.defenderMaxHealth()) + ")", MessageColors.SUCCESS);
        }

        // Damage composition
        DamageBreakdown bd = t.breakdown();
        float phys = bd.physicalDamage();
        float trueDmg = bd.trueDamage();
        if (total > 0) {
            StringBuilder comp = new StringBuilder("  ");
            if (phys > 0) comp.append("Physical: ").append(NumberFormatter.smallFlat(phys)).append(" (").append(NumberFormatter.percent(phys / total * 100f)).append(")  ");
            for (ElementType elem : ElementType.values()) {
                float elemDmg = bd.getElementalDamage(elem);
                if (elemDmg > 0) comp.append(elem.getDisplayName()).append(": ").append(NumberFormatter.smallFlat(elemDmg)).append(" (").append(NumberFormatter.percent(elemDmg / total * 100f)).append(")  ");
            }
            if (trueDmg > 0) comp.append("True: ").append(NumberFormatter.smallFlat(trueDmg)).append(" (").append(NumberFormatter.percent(trueDmg / total * 100f)).append(")");
            mb.line(comp.toString().trim(), MessageColors.GRAY);
        }

        // Crit indicator
        if (t.wasCritical()) {
            String tierLabel = t.critTier() > 1 ? " T" + t.critTier() : "";
            mb.line("CRIT" + tierLabel + " " + NumberFormatter.multiplier(t.critMultiplierApplied()), MessageColors.GOLD);
        }

        mb.separator();
    }

    // ==================== Pipeline Steps ====================

    /** Step 1: Base damage from weapon. */
    public static void renderBaseDamage(@Nonnull MessageBuilder mb, @Nonnull DamageTrace t) {
        mb.section("Base Damage");
        mb.line("Weapon Base: " + NumberFormatter.smallFlat(t.weaponBaseDamage()), MessageColors.WHITE);

        float atkMult = t.attackTypeMultiplier();
        if (atkMult != 1.0f && t.referenceDamage() > 0) {
            String atkName = t.attackName() != null ? t.attackName() : "(unknown)";
            float rawRatio = t.vanillaDamage() / t.referenceDamage();
            if (Math.abs(rawRatio - atkMult) > 0.01f) {
                mb.line("Attack: " + atkName + " (" + NumberFormatter.multiplier(rawRatio) + " raw, clamped to " + NumberFormatter.multiplier(atkMult) + ")", MessageColors.GRAY);
            } else {
                mb.line("Attack: " + atkName + " (" + NumberFormatter.multiplier(atkMult) + ")", MessageColors.GRAY);
            }
        } else if (atkMult != 1.0f) {
            mb.line("Attack: " + t.attackType().name().toLowerCase() + " (" + NumberFormatter.multiplier(atkMult) + ")", MessageColors.GRAY);
        }

        renderSnapshot(mb, t.weaponBaseDamage(), null, primaryChannelShort(t));
    }

    /** Step 2: Flat physical damage from stats (skipped for spells — they use spell damage). */
    public static void renderFlatPhysical(@Nonnull MessageBuilder mb, @Nonnull DamageTrace t, @Nullable StatBreakdownResult bkd) {
        if (t.flatPhysFromStats() == 0 && t.flatMelee() == 0) return;

        String label = primaryChannelLabel(t);
        mb.section("Flat " + label);
        if (t.flatPhysFromStats() != 0) {
            String src = statSources(bkd, ComputedStats::getPhysicalDamage);
            mb.line("+ Physical Damage: " + NumberFormatter.signed(t.flatPhysFromStats()) + src, MessageColors.WHITE);
        }
        if (t.flatMelee() != 0) {
            String src = statSources(bkd, ComputedStats::getMeleeDamage);
            mb.line("+ Melee Damage: " + NumberFormatter.signed(t.flatMelee()) + src, MessageColors.WHITE);
        }
        mb.line("= " + label + ": " + NumberFormatter.smallFlat(t.physAfterFlat()), MessageColors.WHITE);
        renderSnapshot(mb, t.physAfterFlat(), null, primaryChannelShort(t));
    }

    /** Step 3: Flat elemental damage from gear. */
    public static void renderFlatElemental(@Nonnull MessageBuilder mb, @Nonnull DamageTrace t) {
        boolean hasAny = false;
        for (ElementType elem : ElementType.values()) {
            if (t.flatElementalAdded().getOrDefault(elem, 0f) > 0) { hasAny = true; break; }
        }
        if (!hasAny) return;

        mb.section("Flat Elemental");
        for (ElementType elem : ElementType.values()) {
            float flat = t.flatElementalAdded().getOrDefault(elem, 0f);
            if (flat > 0) {
                mb.line("+ " + elem.getDisplayName() + ": +" + NumberFormatter.smallFlat(flat), getElementColor(elem));
            }
        }

        if (t.attackType() == AttackType.SPELL) {
            // For spells: merge spell base (physAfterFlat) with flat elemental into one map
            // so the snapshot shows combined totals instead of duplicating the spell element
            ElementType spellElem = t.breakdown().getPrimaryElement();
            EnumMap<ElementType, Float> merged = new EnumMap<>(ElementType.class);
            for (ElementType elem : ElementType.values()) {
                float flat = t.flatElementalAdded().getOrDefault(elem, 0f);
                if (elem == spellElem) {
                    merged.put(elem, t.physAfterFlat() + flat); // base + flat
                } else if (flat > 0) {
                    merged.put(elem, flat);
                }
            }
            renderSnapshot(mb, 0f, merged, "Phys");
        } else {
            renderSnapshot(mb, t.physAfterFlat(), t.flatElementalAdded(), primaryChannelShort(t));
        }
    }

    /** Step 4: Physical to elemental conversion. */
    public static void renderConversion(@Nonnull MessageBuilder mb, @Nonnull DamageTrace t) {
        boolean hasConversion = false;
        for (ElementType elem : ElementType.values()) {
            if (t.conversionPercents().getOrDefault(elem, 0f) > 0) { hasConversion = true; break; }
        }
        if (!hasConversion) return;

        mb.section("Conversion");
        for (ElementType elem : ElementType.values()) {
            float pct = t.conversionPercents().getOrDefault(elem, 0f);
            if (pct > 0) {
                float converted = t.convertedAmounts().getOrDefault(elem, 0f);
                mb.line(elem.getDisplayName() + ": " + NumberFormatter.percent(pct) + " of " + NumberFormatter.smallFlat(t.physBeforeConversion()) + " = " + NumberFormatter.smallFlat(converted) + " phys->" + elem.getDisplayName().toLowerCase(), getElementColor(elem));
            }
        }
        if (t.scaleFactor() != 1.0f) {
            mb.line("Scale Factor: " + NumberFormatter.multiplier(t.scaleFactor()) + " (total conversion > 100%)", MessageColors.GRAY);
        }
        String convLabel = primaryChannelLabel(t);
        mb.line(convLabel + ": " + NumberFormatter.smallFlat(t.physBeforeConversion()) + " -> " + NumberFormatter.smallFlat(t.physAfterConversion()), MessageColors.WHITE);
        renderSnapshot(mb, t.physAfterConversion(), buildElemMapPreMods(t), primaryChannelShort(t));
    }

    /** Step 5: % increased damage (additive). Spell-aware: uses spell element name and correct values. */
    public static void renderPercentIncreased(@Nonnull MessageBuilder mb, @Nonnull DamageTrace t, @Nullable StatBreakdownResult bkd) {
        if (t.totalIncreasedPercent() == 0) return;

        mb.section("% Increased");
        StringBuilder components = new StringBuilder();
        if (t.physDmgPercent() != 0) {
            components.append("Physical Damage: ").append(NumberFormatter.signedPercent(t.physDmgPercent()));
        }
        if (t.attackTypePercent() != 0) {
            if (!components.isEmpty()) components.append(" + ");
            components.append(attackTypeBonusLabel(t.attackType())).append(": ").append(NumberFormatter.signedPercent(t.attackTypePercent()));
        }
        if (t.globalDmgPercent() != 0) {
            if (!components.isEmpty()) components.append(" + ");
            components.append("Global Damage: ").append(NumberFormatter.signedPercent(t.globalDmgPercent()));
        }
        mb.line(components + " = " + NumberFormatter.signedPercent(t.totalIncreasedPercent()), MessageColors.INFO);

        boolean isSpell = t.attackType() == AttackType.SPELL;
        String incLabel = primaryChannelLabel(t);

        if (isSpell) {
            // For spells: the "phys*" trace fields hold the spell element's value.
            // physAfterFlat = spell element after Step 1 (base only, before flat elemental from gear).
            // physAfterIncreased = spell element after Step 4 (after % increased was applied).
            // But flat elemental from Step 2 was added between steps 1 and 4.
            // The correct "before" is physAfterFlat + flatElementalAdded[spellElement].
            ElementType spellElem = t.breakdown().getPrimaryElement();
            float spellBase = t.physAfterFlat(); // post-Step-1 base
            float flatElem = t.flatElementalAdded().getOrDefault(spellElem, 0f);
            float before = spellBase + flatElem; // true total before % increased
            float mult = 1f + t.totalIncreasedPercent() / 100f;
            float after = before * mult;
            mb.line(incLabel + ": " + NumberFormatter.smallFlat(before) + " x " + NumberFormatter.multiplier(mult) + " = " + NumberFormatter.smallFlat(after), MessageColors.WHITE);
            // Snapshot: show the spell element value at this point
            EnumMap<ElementType, Float> spellMap = new EnumMap<>(ElementType.class);
            spellMap.put(spellElem, after);
            // Also show secondary elements (flat from gear, not yet modified)
            for (ElementType elem : ElementType.values()) {
                if (elem == spellElem) continue;
                float sec = t.flatElementalAdded().getOrDefault(elem, 0f);
                if (sec > 0) spellMap.put(elem, sec);
            }
            renderSnapshot(mb, 0f, spellMap, "Phys");
        } else {
            float physBefore = t.physAfterConversion() > 0 ? t.physAfterConversion() : t.physAfterFlat();
            mb.line(incLabel + ": " + NumberFormatter.smallFlat(physBefore) + " x " + NumberFormatter.multiplier(1f + t.totalIncreasedPercent() / 100f) + " = " + NumberFormatter.smallFlat(t.physAfterIncreased()), MessageColors.WHITE);
            renderSnapshot(mb, t.physAfterIncreased(), buildElemMapPreMods(t), primaryChannelShort(t));
        }
    }

    /**
     * Step 6: Per-element % increased and % more modifiers.
     * FIX #9: Only shows elements that have actual modifiers (skip inc==0 && more==0).
     */
    public static void renderElementalMods(@Nonnull MessageBuilder mb, @Nonnull DamageTrace t) {
        boolean hasAny = false;
        for (ElementType elem : ElementType.values()) {
            float inc = t.elemPercentInc().getOrDefault(elem, 0f);
            float more = t.elemPercentMore().getOrDefault(elem, 0f);
            // FIX #9: Only show elements with actual modifiers
            if (inc != 0 || more != 0) { hasAny = true; break; }
        }
        if (!hasAny) return;

        mb.section("Elemental Modifiers");
        for (ElementType elem : ElementType.values()) {
            float inc = t.elemPercentInc().getOrDefault(elem, 0f);
            float more = t.elemPercentMore().getOrDefault(elem, 0f);
            float after = t.elemAfterMods().getOrDefault(elem, 0f);

            // FIX #9: Skip elements with zero modifiers
            if (inc == 0 && more == 0) continue;

            // Back-calculate pre-mod value from post-mod and modifiers
            float incMult = 1f + inc / 100f;
            float moreMult = 1f + more / 100f;
            float preMod = (incMult > 0 && moreMult > 0) ? after / (incMult * moreMult) : after;

            // FIX #1: Player-friendly labels
            mb.line(elem.getDisplayName() + ": " + NumberFormatter.smallFlat(preMod) + " x (1" + NumberFormatter.signedPercent(inc) + " increased) x (1" + NumberFormatter.signedPercent(more) + " more) = " + NumberFormatter.smallFlat(after), getElementColor(elem));
        }
        // For spells: physAfterIncreased holds the spell element's value, and elemAfterMods
        // also contains the spell element's value. Use 0 for phys to avoid double-counting.
        if (t.attackType() == AttackType.SPELL) {
            renderSnapshot(mb, 0f, t.elemAfterMods(), "Phys");
        } else {
            renderSnapshot(mb, t.physAfterIncreased(), t.elemAfterMods());
        }
    }

    /** Step 7: Global % more multipliers (multiplicative). */
    public static void renderMoreMultipliers(@Nonnull MessageBuilder mb, @Nonnull DamageTrace t) {
        if (t.allDamagePercent() == 0 && t.damageMultiplier() == 0) return;

        mb.section("% More Multipliers");
        // FIX #1: Player-friendly labels
        if (t.allDamagePercent() != 0) {
            mb.line("All Damage: " + NumberFormatter.signedPercent(t.allDamagePercent()) + " -> " + NumberFormatter.multiplier(1f + t.allDamagePercent() / 100f), MessageColors.INFO);
        }
        if (t.damageMultiplier() != 0) {
            mb.line("Damage Multiplier: " + NumberFormatter.signedPercent(t.damageMultiplier()) + " -> " + NumberFormatter.multiplier(1f + t.damageMultiplier() / 100f), MessageColors.INFO);
        }
        if (t.attackType() == AttackType.SPELL) {
            // For spells: physAfterMore holds the spell element's value — skip the
            // separate "Physical After" line to avoid confusion, show only per-element
            for (ElementType elem : ElementType.values()) {
                float afterMore = t.elemAfterMore().getOrDefault(elem, 0f);
                if (afterMore > 0) {
                    float beforeMore = t.elemAfterMods().getOrDefault(elem, 0f);
                    if (Math.abs(afterMore - beforeMore) > 0.01f) {
                        mb.line(elem.getDisplayName() + " After: " + NumberFormatter.smallFlat(beforeMore) + " -> " + NumberFormatter.smallFlat(afterMore), getElementColor(elem));
                    }
                }
            }
            renderSnapshot(mb, 0f, t.elemAfterMore(), "Phys");
        } else {
            String moreLabel = primaryChannelLabel(t);
            mb.line(moreLabel + " After: " + NumberFormatter.smallFlat(t.physAfterMore()), MessageColors.WHITE);

            for (ElementType elem : ElementType.values()) {
                float afterMore = t.elemAfterMore().getOrDefault(elem, 0f);
                if (afterMore > 0) {
                    float beforeMore = t.elemAfterMods().getOrDefault(elem, 0f);
                    if (Math.abs(afterMore - beforeMore) > 0.01f) {
                        mb.line(elem.getDisplayName() + " After: " + NumberFormatter.smallFlat(beforeMore) + " -> " + NumberFormatter.smallFlat(afterMore), getElementColor(elem));
                    }
                }
            }
            renderSnapshot(mb, t.physAfterMore(), t.elemAfterMore(), primaryChannelShort(t));
        }
    }

    /**
     * Step 8: Conditional multipliers (realm, execute, vs frozen, etc.)
     * FIX #2: Uses NumberFormatter.multiplier() instead of %.4f.
     */
    public static void renderConditionals(@Nonnull MessageBuilder mb, @Nonnull DamageTrace t) {
        ConditionalResult cond = t.conditionals();
        float atkMult = t.attackTypeMultiplier();
        if (!cond.hasAny() && Math.abs(atkMult - 1.0f) < 0.001f) return;

        mb.section("Conditional Multipliers");

        if (atkMult != 1.0f) {
            String atkLabel = t.attackName() != null ? t.attackName() : t.attackType().name().toLowerCase();
            mb.line("Attack Type: " + NumberFormatter.multiplier(atkMult) + " (" + atkLabel + ")", MessageColors.INFO);
        }
        if (cond.realm() != 1.0f) {
            mb.line("Realm Damage: " + NumberFormatter.multiplier(cond.realm()), MessageColors.INFO);
        }
        if (cond.execute() != 1.0f) {
            mb.line("Execute (" + NumberFormatter.signedPercent((cond.execute() - 1f) * 100f) + "): " + NumberFormatter.multiplier(cond.execute()), MessageColors.ORANGE);
        }
        if (cond.vsFrozen() != 1.0f) {
            mb.line("vs Frozen (" + NumberFormatter.signedPercent((cond.vsFrozen() - 1f) * 100f) + "): " + NumberFormatter.multiplier(cond.vsFrozen()), COLOR_WATER);
        }
        if (cond.vsShocked() != 1.0f) {
            mb.line("vs Shocked (" + NumberFormatter.signedPercent((cond.vsShocked() - 1f) * 100f) + "): " + NumberFormatter.multiplier(cond.vsShocked()), COLOR_LIGHTNING);
        }
        if (cond.lowLife() != 1.0f) {
            mb.line("Low Life (" + NumberFormatter.signedPercent((cond.lowLife() - 1f) * 100f) + "): " + NumberFormatter.multiplier(cond.lowLife()), MessageColors.ERROR);
        }
        if (cond.consecutive() != 1.0f) {
            mb.line("Consecutive (" + NumberFormatter.signedPercent((cond.consecutive() - 1f) * 100f) + "): " + NumberFormatter.multiplier(cond.consecutive()), MessageColors.WHITE);
        }

        // FIX #2: Use NumberFormatter.multiplier() instead of %.4f
        float fullCombined = cond.combined() * atkMult;
        mb.line("Combined: " + NumberFormatter.multiplier(fullCombined), MessageColors.WHITE);

        // Per-type damage impact
        if (t.attackType() == AttackType.SPELL) {
            // For spells: physAfterMore/physAfterConditionals hold the spell element value
            // Show only per-element lines to avoid double-counting
            for (ElementType elem : ElementType.values()) {
                float elemBefore = t.elemAfterMore().getOrDefault(elem, 0f);
                float elemAfter = t.elemAfterConditionals().getOrDefault(elem, 0f);
                if (elemBefore > 0.05f) {
                    mb.line(elem.getDisplayName() + ": " + NumberFormatter.smallFlat(elemBefore) + " x " + NumberFormatter.multiplier(fullCombined) + " = " + NumberFormatter.smallFlat(elemAfter), getElementColor(elem));
                }
            }
            renderSnapshot(mb, 0f, t.elemAfterConditionals(), "Phys");
        } else {
            String condLabel = primaryChannelLabel(t);
            float physBefore = t.physAfterMore();
            float physAfter = t.physAfterConditionals();
            if (physBefore > 0.05f) {
                mb.line(condLabel + ": " + NumberFormatter.smallFlat(physBefore) + " x " + NumberFormatter.multiplier(fullCombined) + " = " + NumberFormatter.smallFlat(physAfter), MessageColors.WHITE);
            }
            for (ElementType elem : ElementType.values()) {
                float elemBefore = t.elemAfterMore().getOrDefault(elem, 0f);
                float elemAfter = t.elemAfterConditionals().getOrDefault(elem, 0f);
                if (elemBefore > 0.05f) {
                    mb.line(elem.getDisplayName() + ": " + NumberFormatter.smallFlat(elemBefore) + " x " + NumberFormatter.multiplier(fullCombined) + " = " + NumberFormatter.smallFlat(elemAfter), getElementColor(elem));
                }
            }
            renderSnapshot(mb, t.physAfterConditionals(), t.elemAfterConditionals(), primaryChannelShort(t));
        }
    }

    /** Step 9: Critical strike roll and multiplier. */
    public static void renderCritSection(@Nonnull MessageBuilder mb, @Nonnull DamageTrace t, @Nullable StatBreakdownResult bkd) {
        mb.section("Critical Strike");

        String chanceSrc = statSources(bkd, ComputedStats::getCriticalChance);
        mb.line("Crit Chance: " + NumberFormatter.percent(t.critChance()) + chanceSrc, MessageColors.INFO);

        if (t.wasCritical()) {
            String multSrc = statSources(bkd, cs -> cs.getCriticalMultiplier() / 100f);
            String rollLabel = t.critTier() > 1 ? "Roll: CRIT T" + t.critTier() + "!" : "Roll: CRIT!";
            mb.line(rollLabel, MessageColors.GOLD);
            if (t.critReductionPercent() > 0) {
                mb.line("Raw Crit Multiplier: " + NumberFormatter.multiplier(t.critMultiplierBeforeReduction()) + multSrc, MessageColors.GOLD);
                mb.line("Crit Damage Reduction: " + NumberFormatter.percent(t.critReductionPercent()), MessageColors.SUCCESS);
                mb.line("Effective Multiplier: " + NumberFormatter.multiplier(t.critMultiplierApplied()), MessageColors.GOLD);
            } else {
                mb.line("Crit Multiplier: " + NumberFormatter.multiplier(t.critMultiplierApplied()) + multSrc, MessageColors.GOLD);
            }

            if (t.attackType() == AttackType.SPELL) {
                // For spells: show only per-element crit lines, no "Physical" line
                for (ElementType elem : ElementType.values()) {
                    float elemBefore = t.elemAfterConditionals().getOrDefault(elem, 0f);
                    float elemAfter = t.elemAfterCrit().getOrDefault(elem, 0f);
                    if (elemBefore > 0.05f) {
                        mb.line(elem.getDisplayName() + ": " + NumberFormatter.smallFlat(elemBefore) + " x " + NumberFormatter.multiplier(t.critMultiplierApplied()) + " = " + NumberFormatter.smallFlat(elemAfter), getElementColor(elem));
                    }
                }
            } else {
                String critLabel = primaryChannelLabel(t);
                float physBefore = t.physAfterConditionals();
                float physAfter = t.physAfterCrit();
                if (physBefore > 0.05f) {
                    mb.line(critLabel + ": " + NumberFormatter.smallFlat(physBefore) + " x " + NumberFormatter.multiplier(t.critMultiplierApplied()) + " = " + NumberFormatter.smallFlat(physAfter), MessageColors.WHITE);
                }
                for (ElementType elem : ElementType.values()) {
                    float elemBefore = t.elemAfterConditionals().getOrDefault(elem, 0f);
                    float elemAfter = t.elemAfterCrit().getOrDefault(elem, 0f);
                    if (elemBefore > 0.05f) {
                        mb.line(elem.getDisplayName() + ": " + NumberFormatter.smallFlat(elemBefore) + " x " + NumberFormatter.multiplier(t.critMultiplierApplied()) + " = " + NumberFormatter.smallFlat(elemAfter), getElementColor(elem));
                    }
                }
            }
        } else {
            mb.line("Roll: No crit", MessageColors.GRAY);
        }
        if (t.attackType() == AttackType.SPELL) {
            renderSnapshot(mb, 0f, t.elemAfterCrit(), "Phys");
        } else {
            renderSnapshot(mb, t.physAfterCrit(), t.elemAfterCrit(), primaryChannelShort(t));
        }
    }

    /**
     * Step 10: Defenses (armor, physical resistance, elemental resistances).
     * FIX #3: Removes raw formula display.
     * FIX #8: Uses direct DamageTrace values instead of back-calculation.
     */
    public static void renderDefenses(@Nonnull MessageBuilder mb, @Nonnull DamageTrace t) {
        mb.section("Defenses");

        // Armor chain — FIX #8: display stored values directly, no back-calc
        float armor = t.defenderArmor();
        float armorPct = t.armorPercent();
        float pen = t.armorPenPercent();
        float eff = t.effectiveArmor();
        float redPct = t.armorReductionPercent();

        if (armor > 0 || eff > 0) {
            StringBuilder armorLine = new StringBuilder("Armor: " + NumberFormatter.flat(armor));
            if (armorPct > 0) armorLine.append(" x (1+").append(NumberFormatter.percent(armorPct)).append(")");
            if (pen > 0) armorLine.append(" -> ").append(NumberFormatter.percent(pen)).append(" pen");
            armorLine.append(" -> ").append(NumberFormatter.flat(eff)).append(" eff");
            mb.line(armorLine.toString(), MessageColors.INFO);
            // FIX #3: Show result instead of raw formula
            mb.line("Armor Reduction: -" + NumberFormatter.percent(redPct) + " physical", MessageColors.INFO);
        }

        if (t.attackType() != AttackType.SPELL) {
            // Physical channel: before -> after armor -> after resist
            String defLabel = primaryChannelLabel(t);
            mb.line(defLabel + ": " + NumberFormatter.smallFlat(t.physBeforeArmor()) + " x " + NumberFormatter.percent(100f - redPct) + " = " + NumberFormatter.smallFlat(t.physAfterArmor()), MessageColors.WHITE);

            // Physical resistance
            float physResist = t.physResistPercent();
            if (physResist > 0) {
                mb.line("Physical Resistance: " + NumberFormatter.percent(physResist) + " -> " + NumberFormatter.smallFlat(t.physAfterArmor()) + " x " + NumberFormatter.percent(100f - physResist) + " = " + NumberFormatter.smallFlat(t.physAfterResist()), MessageColors.INFO);
            }
        }
        // For spells: physBeforeArmor/physAfterArmor hold the spell element value,
        // but armor doesn't apply to spell elemental damage — the element resistance
        // is shown in the per-element section below. Skip the "Physical" line entirely.

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
                StringBuilder line = new StringBuilder(elem.getDisplayName() + ": " + NumberFormatter.percent(raw));
                if (elemPen > 0) line.append(" - ").append(NumberFormatter.percent(elemPen)).append(" pen = ").append(NumberFormatter.percent(effResist)).append(" eff");
                line.append(" -> ").append(NumberFormatter.smallFlat(before)).append(" x ").append(NumberFormatter.percent(100f - effResist)).append(" = ").append(NumberFormatter.smallFlat(after));
                mb.line(line.toString(), color);
            } else {
                mb.line(elem.getDisplayName() + ": 0% -> " + NumberFormatter.smallFlat(after), color);
            }
        }
        if (t.attackType() == AttackType.SPELL) {
            renderSnapshot(mb, 0f, t.elemAfterResist(), "Phys");
        } else {
            renderSnapshot(mb, t.physAfterResist(), t.elemAfterResist(), primaryChannelShort(t));
        }
    }

    /** Step 11: True damage (bypasses all defenses). */
    public static void renderTrueDamage(@Nonnull MessageBuilder mb, @Nonnull DamageTrace t) {
        if (t.trueDamage() <= 0) return;
        mb.section("True Damage");
        mb.line("True: +" + NumberFormatter.smallFlat(t.trueDamage()) + " (bypasses defenses)", MessageColors.PURPLE);
    }

    /**
     * Post-calc modifications: energy shield, parry, MoM, shock, unarmed, blocking.
     * Used by both dealt and received views.
     *
     * @param defenderPerspective true = defender is viewing (nullify=good/green), false = attacker (nullify=bad/red)
     */
    public static void renderPostCalc(@Nonnull MessageBuilder mb, @Nonnull DamageTrace t, boolean defenderPerspective) {
        boolean hasAny = t.shieldAbsorbed() > 0 || t.wasParried()
            || t.defenderCritNullifyChance() > 0 || t.manaAbsorbed() > 0
            || t.shockBonusPercent() > 0 || t.damageTakenModifier() != 0
            || t.unarmedMultiplier() != 0 || t.wasActiveBlocking();
        if (!hasAny) return;

        mb.section("Post-Calc Modifications");

        // Crit nullify — green for defender (good), red for attacker (bad)
        if (t.defenderCritNullifyChance() > 0) {
            String result = t.critWasNullified() ? "NULLIFIED" : "passed";
            String color = t.critWasNullified()
                ? (defenderPerspective ? MessageColors.SUCCESS : MessageColors.ERROR)
                : MessageColors.GRAY;
            mb.line("Crit Nullify: " + NumberFormatter.percent(t.defenderCritNullifyChance()) + " chance -> " + result, color);
        }

        // Parry
        if (t.parryChance() > 0 || t.wasParried()) {
            if (t.wasParried()) {
                mb.line("Parry: " + NumberFormatter.percent(t.parryChance()) + " chance -> PARRIED (taking " + NumberFormatter.percent(t.parryReductionMult() * 100f) + " damage, after: " + NumberFormatter.smallFlat(t.damageAfterParry()) + ")", MessageColors.INFO);
            } else {
                mb.line("Parry: " + NumberFormatter.percent(t.parryChance()) + " chance -> no", MessageColors.GRAY);
            }
        }

        // Energy shield
        if (t.shieldAbsorbed() > 0 || t.energyShieldCapacity() > 0) {
            float shieldAfter = Math.max(0f, t.energyShieldBefore() - t.shieldAbsorbed());
            mb.line("Shield: " + NumberFormatter.flat(t.energyShieldBefore()) + "/" + NumberFormatter.flat(t.energyShieldCapacity()) + " -> " + NumberFormatter.flat(shieldAfter) + "/" + NumberFormatter.flat(t.energyShieldCapacity()) + " (absorbed " + NumberFormatter.smallFlat(t.shieldAbsorbed()) + ")", MessageColors.LIGHT_BLUE);
        }

        // Mind over Matter
        if (t.manaAbsorbed() > 0) {
            mb.line("Mana Buffer: " + NumberFormatter.percent(t.manaBufferPercent()) + " -> " + NumberFormatter.smallFlat(t.manaAbsorbed()) + " mana absorbed", MessageColors.INFO);
        }

        // Shock amplification — FIX #1: Player-friendly label
        if (t.shockBonusPercent() > 0) {
            float afterShock = t.damageBeforeShock() * (1f + t.shockBonusPercent() / 100f);
            mb.line("Shock Amplification: +" + NumberFormatter.percent(t.shockBonusPercent()) + " -> " + NumberFormatter.smallFlat(t.damageBeforeShock()) + " x " + NumberFormatter.multiplier(1f + t.shockBonusPercent() / 100f) + " = " + NumberFormatter.smallFlat(afterShock), COLOR_LIGHTNING);
        }

        // Damage taken modifier — FIX #1: Player-friendly label
        if (t.damageTakenModifier() != 0) {
            mb.line("Damage Taken Modifier: " + NumberFormatter.multiplier(1f + t.damageTakenModifier() / 100f) + " (" + NumberFormatter.signedPercent(t.damageTakenModifier()) + " damage taken)", MessageColors.GRAY);
        }

        // Unarmed penalty
        if (t.unarmedMultiplier() != 0) {
            mb.line("Unarmed: " + NumberFormatter.multiplier(t.unarmedMultiplier()) + " (" + NumberFormatter.smallFlat(t.damageBeforeUnarmed()) + " -> " + NumberFormatter.smallFlat(t.damageBeforeUnarmed() * t.unarmedMultiplier()) + ")", MessageColors.GRAY);
        }

        // Active blocking reduction
        if (t.wasActiveBlocking()) {
            String blockType = t.isShieldBlock() ? "Shield Block" : "Weapon Block";
            mb.line(blockType + ": -" + NumberFormatter.percent(t.blockReductionPercent()) + " (" + NumberFormatter.smallFlat(t.damageBeforeBlock()) + " -> " + NumberFormatter.smallFlat(t.damageAfterBlock()) + ")", MessageColors.INFO);
        }
    }

    /**
     * Target/defender avoidance stats context.
     * @param attackerPerspective true = "Target Avoidance (all passed)", false = "Your Avoidance (all failed)"
     */
    public static void renderAvoidanceContext(@Nonnull MessageBuilder mb, @Nonnull DamageTrace t, boolean attackerPerspective) {
        AvoidanceProcessor.AvoidanceDetail av = t.avoidanceStats();
        if (av == null) {
            // FIX #13: Explicit note instead of silent skip
            if (attackerPerspective) {
                mb.section("Target Avoidance");
                mb.line("(not available for this target)", MUTED);
            }
            return;
        }

        if (av.dodgeChance() <= 0 && av.evasion() <= 0 && av.passiveBlockChance() <= 0
            && av.activeBlockChance() <= 0 && av.parryChance() <= 0) return;

        String title = attackerPerspective ? "Target Avoidance (all passed)" : "Your Avoidance (all failed)";
        String verb = attackerPerspective ? "passed" : "failed";
        String hitVerb = attackerPerspective ? "hit" : "they hit";
        mb.section(title);

        if (av.dodgeChance() > 0) {
            mb.line("Dodge: " + NumberFormatter.percent(av.dodgeChance()) + " -> " + verb, MessageColors.GRAY);
        }
        if (av.evasion() > 0 || av.accuracy() > 0) {
            String accLabel = attackerPerspective ? "your " + NumberFormatter.flat(av.accuracy()) + " acc" : NumberFormatter.flat(av.accuracy()) + " acc";
            mb.line("Evasion: " + NumberFormatter.flat(av.evasion()) + " vs " + accLabel + " -> " + NumberFormatter.percent(av.hitChance()) + " hit -> " + hitVerb, MessageColors.GRAY);
        }
        if (av.wasActiveBlock() && av.activeBlockChance() > 0) {
            mb.line("Perfect Block: " + NumberFormatter.percent(av.activeBlockChance()) + " -> " + verb, MessageColors.GRAY);
        }
        if (av.parryChance() > 0) {
            mb.line("Parry: " + NumberFormatter.percent(av.parryChance()) + " -> " + verb, MessageColors.GRAY);
        }
    }

    /**
     * Ailment application results per element.
     * FIX #11: Shows roll vs threshold comparison.
     */
    public static void renderAilments(@Nonnull MessageBuilder mb, @Nonnull DamageTrace t) {
        CombatAilmentApplicator.AilmentSummary summary = t.ailmentSummary();
        if (summary == null || summary.attempts().isEmpty()) return;

        mb.section("Ailment Applications");
        for (CombatAilmentApplicator.AilmentAttempt attempt : summary.attempts()) {
            String elemColor = getElementColor(attempt.element());
            String ailmentName = attempt.ailmentName() != null ? attempt.ailmentName() : "?";

            if (attempt.applied()) {
                // FIX #11: Show roll vs threshold clearly
                mb.text(attempt.element().getDisplayName() + " (" + NumberFormatter.smallFlat(attempt.elementalDamage()) + " dmg): " + ailmentName + " " + NumberFormatter.percent(attempt.applicationChance()) + " -> APPLIED", elemColor);
                mb.line(" (rolled " + NumberFormatter.smallFlat(attempt.roll()) + " < " + NumberFormatter.smallFlat(attempt.applicationChance()) + " threshold) -- " + NumberFormatter.smallFlat(attempt.magnitude()) + " mag, " + NumberFormatter.time(attempt.durationSeconds()), MessageColors.GRAY);
            } else {
                // FIX #11: Explain why it failed
                mb.line(attempt.element().getDisplayName() + " (" + NumberFormatter.smallFlat(attempt.elementalDamage()) + " dmg): " + ailmentName + " " + NumberFormatter.percent(attempt.applicationChance()) + " -> FAILED (rolled " + NumberFormatter.smallFlat(attempt.roll()) + " > " + NumberFormatter.smallFlat(attempt.applicationChance()) + " threshold)", MessageColors.GRAY);
            }
        }
    }

    /** Recovery section: leech and steal. */
    public static void renderRecovery(@Nonnull MessageBuilder mb, @Nonnull DamageTrace t) {
        if (t.lifeLeechAmount() <= 0 && t.lifeStealAmount() <= 0
            && t.manaLeechAmount() <= 0 && t.manaStealAmount() <= 0) return;

        mb.section("Recovery");
        if (t.lifeLeechAmount() > 0) {
            mb.line("Life Leech: " + NumberFormatter.percent(t.lifeLeechPercent()) + " x " + NumberFormatter.smallFlat(t.breakdown().totalDamage()) + " = " + NumberFormatter.smallFlat(t.lifeLeechAmount()) + " HP", MessageColors.SUCCESS);
        }
        if (t.lifeStealAmount() > 0) {
            mb.line("Life Steal: " + NumberFormatter.percent(t.lifeStealPercent()) + " x " + NumberFormatter.smallFlat(t.breakdown().totalDamage()) + " = " + NumberFormatter.smallFlat(t.lifeStealAmount()) + " HP", MessageColors.SUCCESS);
        }
        if (t.manaLeechAmount() > 0) {
            mb.line("Mana Leech: " + NumberFormatter.percent(t.manaLeechPercent()) + " x " + NumberFormatter.smallFlat(t.breakdown().totalDamage()) + " = " + NumberFormatter.smallFlat(t.manaLeechAmount()) + " mana", MessageColors.INFO);
        }
        if (t.manaStealAmount() > 0) {
            mb.line("Mana Steal: " + NumberFormatter.percent(t.manaStealPercent()) + " x " + NumberFormatter.smallFlat(t.breakdown().totalDamage()) + " = " + NumberFormatter.smallFlat(t.manaStealAmount()) + " mana", MessageColors.INFO);
        }
    }

    /** Thorns section: reflected damage. */
    public static void renderThorns(@Nonnull MessageBuilder mb, @Nonnull DamageTrace t) {
        if (t.totalThornsReturned() <= 0) return;

        mb.section("Thorns Returned");
        if (t.thornsDamageFlat() > 0) {
            mb.line("Flat: " + NumberFormatter.flat(t.thornsDamageFlat()) + " x (1+" + NumberFormatter.percent(t.thornsDamagePercent()) + ") = " + NumberFormatter.smallFlat(t.thornsDamageFlat() * (1f + t.thornsDamagePercent() / 100f)), MessageColors.ORANGE);
        }
        if (t.reflectDamagePercent() > 0) {
            mb.line("Reflect: " + NumberFormatter.percent(t.reflectDamagePercent()) + " x " + NumberFormatter.smallFlat(t.breakdown().totalDamage()) + " = " + NumberFormatter.smallFlat(t.breakdown().totalDamage() * t.reflectDamagePercent() / 100f), MessageColors.ORANGE);
        }
        mb.line("Total: " + NumberFormatter.smallFlat(t.totalThornsReturned()) + " back to you", MessageColors.ERROR);
    }

    // ==================== Helpers ====================

    /**
     * Returns the display label for the primary damage channel.
     *
     * <p>For spells, the "phys" trace fields actually hold the spell element's values
     * (the calculator stores them as {@code dist.getElemental(spellElement)}).
     * This method returns the correct label: "Fire" for fire spells, "Physical" for melee.
     */
    @Nonnull
    static String primaryChannelLabel(@Nonnull DamageTrace t) {
        if (t.attackType() == AttackType.SPELL) {
            // For spells, the primary channel is the spell element
            DamageBreakdown bd = t.breakdown();
            if (bd.hasElementalDamage() || bd.getTotalElementalDamage() > 0) {
                ElementType primary = bd.getPrimaryElement();
                return primary.getDisplayName();
            }
        }
        return "Physical";
    }

    /**
     * Returns the short display label for snapshot lines.
     */
    @Nonnull
    static String primaryChannelShort(@Nonnull DamageTrace t) {
        if (t.attackType() == AttackType.SPELL) {
            DamageBreakdown bd = t.breakdown();
            if (bd.hasElementalDamage() || bd.getTotalElementalDamage() > 0) {
                ElementType primary = bd.getPrimaryElement();
                return primary.getDisplayName();
            }
        }
        return "Phys";
    }

    /**
     * Renders a damage snapshot line showing the full damage state at a pipeline step.
     * Uses the correct primary channel label (element name for spells, "Phys" for physical).
     */
    static void renderSnapshot(@Nonnull MessageBuilder mb, float phys, @Nullable EnumMap<ElementType, Float> elemental) {
        renderSnapshot(mb, phys, elemental, "Phys");
    }

    /**
     * Renders a damage snapshot with a custom primary label.
     */
    static void renderSnapshot(@Nonnull MessageBuilder mb, float phys, @Nullable EnumMap<ElementType, Float> elemental, @Nonnull String primaryLabel) {
        StringBuilder sb = new StringBuilder("-> " + primaryLabel + ": " + NumberFormatter.smallFlat(phys));
        float total = phys;
        if (elemental != null) {
            for (ElementType elem : ElementType.values()) {
                float val = elemental.getOrDefault(elem, 0f);
                if (val > 0.05f) {
                    sb.append(" | ").append(elem.getDisplayName()).append(": ").append(NumberFormatter.smallFlat(val));
                    total += val;
                }
            }
        }
        if (total != phys) {
            sb.append(" | Total: ").append(NumberFormatter.smallFlat(total));
        }
        mb.line(sb.toString(), MUTED);
    }

    /**
     * Renders a snapshot for spell attacks where the primary channel IS the spell element.
     * Shows phys as 0 and places the spell element value in the elemental map.
     * Also includes any secondary elemental damage from flat elemental or conversion.
     */
    static void renderSpellSnapshot(@Nonnull MessageBuilder mb, @Nonnull DamageTrace t, float spellElemValue) {
        DamageBreakdown bd = t.breakdown();
        EnumMap<ElementType, Float> elemMap = new EnumMap<>(ElementType.class);
        ElementType primaryElem = bd.getPrimaryElement();

        // Place the spell element's current value
        elemMap.put(primaryElem, spellElemValue);

        // Add any secondary elemental damage (flat elemental from gear for other elements)
        for (ElementType elem : ElementType.values()) {
            if (elem == primaryElem) continue;
            float flat = t.flatElementalAdded().getOrDefault(elem, 0f);
            float converted = t.convertedAmounts().getOrDefault(elem, 0f);
            float val = flat + converted;
            if (val > 0) elemMap.put(elem, val);
        }

        renderSnapshot(mb, 0f, elemMap, "Phys");
    }

    /**
     * Builds an elemental map at a pre-mods pipeline step (flat + converted amounts).
     */
    @Nonnull
    static EnumMap<ElementType, Float> buildElemMapPreMods(@Nonnull DamageTrace t) {
        EnumMap<ElementType, Float> map = new EnumMap<>(ElementType.class);
        for (ElementType elem : ElementType.values()) {
            float flat = t.flatElementalAdded().getOrDefault(elem, 0f);
            float converted = t.convertedAmounts().getOrDefault(elem, 0f);
            map.put(elem, flat + converted);
        }
        return map;
    }

    /**
     * Formats per-source stat attribution from a StatBreakdownResult.
     * Returns empty string if breakdown is null or all deltas are zero.
     */
    @Nonnull
    static String statSources(@Nullable StatBreakdownResult bkd, @Nonnull Function<ComputedStats, Float> getter) {
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
}
