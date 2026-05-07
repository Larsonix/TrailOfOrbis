package io.github.larsonix.trailoforbis.combat;

import com.hypixel.hytale.logger.HytaleLogger;
import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.elemental.ElementType;
import io.github.larsonix.trailoforbis.elemental.ElementalCalculator;
import io.github.larsonix.trailoforbis.elemental.ElementalStats;
import io.github.larsonix.trailoforbis.gear.model.WeaponType;

import io.github.larsonix.trailoforbis.combat.modifiers.ConditionalResult;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Central damage calculation engine for the RPG system.
 *
 * <p>This class implements the PoE-inspired damage formula in the correct order:
 * <ol>
 *   <li><b>Base damage</b> from weapon/attack</li>
 *   <li><b>Flat physical</b> (additive): physicalDamage, meleeDamage</li>
 *   <li><b>Flat elemental</b> (additive): per-element flat damage from gear</li>
 *   <li><b>Damage conversion</b> (physical → elemental, capped at 100%)</li>
 *   <li><b>% Increased physical</b> (sum all, apply once): physDmgPercent + meleeDmgPercent + dmgPercent</li>
 *   <li><b>Elemental modifiers</b> (% increased and % more per element) - includes converted damage</li>
 *   <li><b>% More</b> (multiplicative chain): allDamagePercent, damageMultiplier</li>
 *   <li><b>Conditional multipliers</b>: realm damage, execute bonus, etc.</li>
 *   <li><b>Critical strike</b> (one roll, applies to ALL damage types)</li>
 *   <li><b>Defenses</b>: armor (physical), resistance (per element)</li>
 *   <li><b>True damage</b> (added last, bypasses all defenses)</li>
 * </ol>
 *
 * <p><b>Key insight:</b> Crit is applied AFTER all damage scaling so it multiplies both
 * physical and elemental damage equally. Conversion happens BEFORE elemental modifiers
 * so converted damage benefits from the target element's % bonuses.
 *
 * <p><b>Key difference from the old system:</b> This calculator is called ONCE per attack,
 * not per damage type. The old system processed each Hytale damage event independently,
 * causing double-counting of bonuses.
 *
 * <p>Thread-safety: Methods are thread-safe as long as stats objects aren't modified
 * during calculation. Uses ThreadLocalRandom for crit rolls.
 */
public class RPGDamageCalculator {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Maximum resistance cap (75%) - prevents immunity */
    private static final float MAX_RESISTANCE_CAP = 75f;

    /** Maximum armor damage reduction (90%) */
    private static final float MAX_ARMOR_REDUCTION = 0.9f;

    private final CombatCalculator combatCalculator;

    public RPGDamageCalculator() {
        this.combatCalculator = new CombatCalculator();
    }

    public RPGDamageCalculator(@Nonnull CombatCalculator combatCalculator) {
        this.combatCalculator = combatCalculator;
    }

    /**
     * Calculates damage for a complete attack.
     *
     * <p>This method should be called ONCE per attack (on the primary damage event),
     * not once per damage type.
     *
     * @param baseDamage The base damage from the weapon/attack
     * @param attackerStats The attacker's computed stats (null for environment damage)
     * @param attackerElemental The attacker's elemental stats (null if none)
     * @param defenderStats The defender's computed stats (null for no defenses)
     * @param defenderElemental The defender's elemental stats (null if none)
     * @param attackType The type of attack (melee, projectile, area)
     * @param isDOT Whether this is damage-over-time (skips flat, crit)
     * @return Complete damage breakdown
     */
    @Nonnull
    public DamageBreakdown calculate(
        float baseDamage,
        @Nullable ComputedStats attackerStats,
        @Nullable ElementalStats attackerElemental,
        @Nullable ComputedStats defenderStats,
        @Nullable ElementalStats defenderElemental,
        @Nonnull AttackType attackType,
        boolean isDOT
    ) {
        return calculate(baseDamage, attackerStats, attackerElemental,
            defenderStats, defenderElemental, attackType, isDOT, 1.0f, false, null, 1, false);
    }

    /**
     * Calculates damage for a complete attack with conditional multipliers.
     *
     * <p>This method should be called ONCE per attack (on the primary damage event),
     * not once per damage type.
     *
     * <p>The conditional multiplier combines multiple situational bonuses:
     * <ul>
     *   <li>Realm damage multiplier (MONSTER_DAMAGE modifier)</li>
     *   <li>Execute bonus (vs low HP targets)</li>
     *   <li>Damage vs Frozen</li>
     *   <li>Damage vs Shocked</li>
     *   <li>Damage at Low Life (attacker HP ≤ 35%)</li>
     * </ul>
     *
     * @param baseDamage The base damage from the weapon/attack
     * @param attackerStats The attacker's computed stats (null for environment damage)
     * @param attackerElemental The attacker's elemental stats (null if none)
     * @param defenderStats The defender's computed stats (null for no defenses)
     * @param defenderElemental The defender's elemental stats (null if none)
     * @param attackType The type of attack (melee, projectile, area)
     * @param isDOT Whether this is damage-over-time (skips flat, crit)
     * @param conditionalMultiplier Combined situational multiplier (applied after % more, before crit)
     * @return Complete damage breakdown
     */
    @Nonnull
    public DamageBreakdown calculate(
        float baseDamage,
        @Nullable ComputedStats attackerStats,
        @Nullable ElementalStats attackerElemental,
        @Nullable ComputedStats defenderStats,
        @Nullable ElementalStats defenderElemental,
        @Nonnull AttackType attackType,
        boolean isDOT,
        float conditionalMultiplier,
        int attackerLevel
    ) {
        return calculate(baseDamage, attackerStats, attackerElemental, defenderStats,
            defenderElemental, attackType, isDOT, conditionalMultiplier, false, null, attackerLevel, false);
    }

    /**
     * Calculates damage for a complete attack with conditional multipliers and optional trace logging.
     *
     * @param baseDamage The base damage from the weapon/attack
     * @param attackerStats The attacker's computed stats (null for environment damage)
     * @param attackerElemental The attacker's elemental stats (null if none)
     * @param defenderStats The defender's computed stats (null for no defenses)
     * @param defenderElemental The defender's elemental stats (null if none)
     * @param attackType The type of attack (melee, projectile, area)
     * @param isDOT Whether this is damage-over-time (skips flat, crit)
     * @param conditionalMultiplier Combined situational multiplier (applied after % more, before crit)
     * @param traceEnabled Whether to log detailed calculation steps for debugging
     * @param spellElement For spell attacks: the element to place base damage into (null for physical attacks)
     * @param projectileSpell Whether this spell benefits from projectileDamagePercent (hex spells always do)
     * @return Complete damage breakdown
     */
    @Nonnull
    public DamageBreakdown calculate(
        float baseDamage,
        @Nullable ComputedStats attackerStats,
        @Nullable ElementalStats attackerElemental,
        @Nullable ComputedStats defenderStats,
        @Nullable ElementalStats defenderElemental,
        @Nonnull AttackType attackType,
        boolean isDOT,
        float conditionalMultiplier,
        boolean traceEnabled,
        @Nullable ElementType spellElement,
        int attackerLevel,
        boolean projectileSpell
    ) {
        return calculatePipeline(baseDamage, attackerStats, attackerElemental,
            defenderStats, defenderElemental, attackType, isDOT, conditionalMultiplier,
            traceEnabled, spellElement, attackerLevel, projectileSpell,
            null, 1.0f);
    }

    /**
     * Unified 10-phase damage pipeline. Both {@link #calculate} and {@link #calculateTraced}
     * delegate here. When {@code tb} is non-null, intermediate values are recorded for death recap.
     * When {@code traceEnabled} is true, debug logging is emitted to console.
     *
     * @param tb Optional trace builder for death recap (null = no recording)
     * @param attackTypeMultiplier Vanilla weapon profile multiplier (1.0 if none)
     */
    @Nonnull
    private DamageBreakdown calculatePipeline(
        float baseDamage,
        @Nullable ComputedStats attackerStats,
        @Nullable ElementalStats attackerElemental,
        @Nullable ComputedStats defenderStats,
        @Nullable ElementalStats defenderElemental,
        @Nonnull AttackType attackType,
        boolean isDOT,
        float conditionalMultiplier,
        boolean traceEnabled,
        @Nullable ElementType spellElement,
        int attackerLevel,
        boolean projectileSpell,
        @Nullable DamageTrace.Builder tb,
        float attackTypeMultiplier
    ) {
        boolean isSpell = (attackType == AttackType.SPELL && spellElement != null);

        // Initialize damage distribution — elemental weapons place base into the element slot.
        // This applies to both magic weapons (spells) and elemental physical weapons (fire sword, etc.).
        // For elemental melee: isSpell stays false, so spell-specific scaling doesn't apply.
        DamageDistribution dist = new DamageDistribution();
        if (spellElement != null) {
            dist.setElemental(spellElement, baseDamage);
        } else {
            dist.setPhysical(baseDamage);
        }

        // Track defense breakdown for death recap
        float armorReduction = 0f;
        EnumMap<ElementType, Float> resistanceReductions = new EnumMap<>(ElementType.class);
        for (ElementType type : ElementType.values()) {
            resistanceReductions.put(type, 0f);
        }

        boolean wasCritical = false;
        float critMultiplier = 1.0f;

        if (attackerStats != null) {
            // ==== STEP 1: Add flat damage (skip for DOT) ====
            float beforeFlat = isSpell ? dist.getElemental(spellElement) : dist.getPhysical();
            float flatPhys = 0f, flatMelee = 0f, flatSpell = 0f;
            if (!isDOT) {
                if (isSpell) {
                    flatSpell = attackerStats.getSpellDamage();
                } else {
                    flatPhys = attackerStats.getPhysicalDamage();
                    flatMelee = (attackType == AttackType.MELEE) ? attackerStats.getMeleeDamage() : 0f;
                }
                applyFlatDamage(dist, attackerStats, attackType, spellElement);
            }
            if (tb != null) {
                tb.physBeforeFlat(beforeFlat);
                tb.flatPhysFromStats(flatPhys);
                tb.flatMelee(flatMelee);
                tb.physAfterFlat(isSpell ? dist.getElemental(spellElement) : dist.getPhysical());
            }
            if (traceEnabled) {
                if (isSpell) {
                    LOGGER.at(Level.FINE).log("[CALC] Step 1 - Flat spell (%s): base=%.1f + flatSpell=%.1f = %.1f",
                        spellElement.name(), beforeFlat, flatSpell, dist.getElemental(spellElement));
                } else {
                    LOGGER.at(Level.FINE).log("[CALC] Step 1 - Flat physical: base=%.1f + flatPhys=%.1f + flatMelee=%.1f = %.1f",
                        beforeFlat, flatPhys, flatMelee, dist.getPhysical());
                }
            }

            // ==== STEP 2: Add flat elemental damage (skip for DOT and mobs) ====
            // Mob damage budget comes entirely from the pool via calculateWeightedMobDamage().
            // Flat elemental on top would add damage outside the pool budget, breaking the curve.
            if (attackerElemental != null && !isDOT && !attackerStats.isMobStats()) {
                if (tb != null) {
                    for (ElementType type : ElementType.values()) {
                        float flat = (float) attackerElemental.getFlatDamage(type);
                        if (flat > 0) tb.flatElemental(type, flat);
                    }
                }
                addFlatElemental(dist, attackerElemental);
                if (traceEnabled) {
                    float fireFlat = (float) attackerElemental.getFlatDamage(ElementType.FIRE);
                    float waterFlat = (float) attackerElemental.getFlatDamage(ElementType.WATER);
                    float lightFlat = (float) attackerElemental.getFlatDamage(ElementType.LIGHTNING);
                    float voidFlat = (float) attackerElemental.getFlatDamage(ElementType.VOID);
                    if (fireFlat > 0 || waterFlat > 0 || lightFlat > 0 || voidFlat > 0) {
                        LOGGER.at(Level.FINE).log("[CALC] Step 2 - Flat elemental: Fire+%.1f, Water+%.1f, Lightning+%.1f, Void+%.1f",
                            fireFlat, waterFlat, lightFlat, voidFlat);
                    }
                }
            }

            // ==== STEP 3: Apply damage conversion (phys -> elemental) ====
            if (!isSpell) {
                float physBeforeConv = dist.getPhysical();
                float fireConv = attackerStats.getFireConversion();
                float waterConv = attackerStats.getWaterConversion();
                float lightConv = attackerStats.getLightningConversion();
                float earthConv = attackerStats.getEarthConversion();
                float windConv = attackerStats.getWindConversion();
                float voidConv = attackerStats.getVoidConversion();
                if (tb != null) {
                    float totalConv = fireConv + waterConv + lightConv + earthConv + windConv + voidConv;
                    tb.physBeforeConversion(physBeforeConv);
                    tb.scaleFactor(totalConv > 100f ? 100f / totalConv : 1f);
                    tb.conversionPercent(ElementType.FIRE, fireConv);
                    tb.conversionPercent(ElementType.WATER, waterConv);
                    tb.conversionPercent(ElementType.LIGHTNING, lightConv);
                    tb.conversionPercent(ElementType.EARTH, earthConv);
                    tb.conversionPercent(ElementType.WIND, windConv);
                    tb.conversionPercent(ElementType.VOID, voidConv);
                    // Snapshot pre-conversion elemental to compute per-element converted amounts
                    EnumMap<ElementType, Float> elemBefore = new EnumMap<>(ElementType.class);
                    for (ElementType type : ElementType.values()) {
                        elemBefore.put(type, dist.getElemental(type));
                    }
                    applyConversion(dist, attackerStats);
                    tb.physAfterConversion(dist.getPhysical());
                    for (ElementType type : ElementType.values()) {
                        float converted = dist.getElemental(type) - elemBefore.get(type);
                        if (converted > 0) tb.convertedAmount(type, converted);
                    }
                } else {
                    applyConversion(dist, attackerStats);
                }
                if (traceEnabled && (fireConv > 0 || waterConv > 0 || lightConv > 0 || earthConv > 0 || windConv > 0 || voidConv > 0)) {
                    LOGGER.at(Level.FINE).log("[CALC] Step 3 - Conversion: Fire=%.1f%%, Water=%.1f%%, Lightning=%.1f%%, Earth=%.1f%%, Wind=%.1f%%, Void=%.1f%%",
                        fireConv, waterConv, lightConv, earthConv, windConv, voidConv);
                    LOGGER.at(Level.FINE).log("[CALC]          Phys: %.1f → %.1f | Fire: %.1f | Water: %.1f | Light: %.1f | Earth: %.1f | Wind: %.1f | Void: %.1f",
                        physBeforeConv, dist.getPhysical(),
                        dist.getElemental(ElementType.FIRE), dist.getElemental(ElementType.WATER),
                        dist.getElemental(ElementType.LIGHTNING), dist.getElemental(ElementType.EARTH),
                        dist.getElemental(ElementType.WIND), dist.getElemental(ElementType.VOID));
                }
            } else {
                // For spells: convert from the spell's base element to target elements
                // e.g., Fire spell + 50% Void Conversion → 50% of fire damage becomes void
                if (spellElement != null) {
                    applySpellConversion(dist, attackerStats, spellElement);
                }
                if (tb != null) { tb.physBeforeConversion(0f); tb.physAfterConversion(0f); }
                if (traceEnabled) {
                    float totalConv = attackerStats.getFireConversion() + attackerStats.getWaterConversion()
                        + attackerStats.getLightningConversion() + attackerStats.getEarthConversion()
                        + attackerStats.getWindConversion() + attackerStats.getVoidConversion();
                    if (totalConv > 0) {
                        LOGGER.at(Level.FINE).log("[CALC] Step 3 - Spell conversion from %s: total=%.1f%%", spellElement, totalConv);
                    } else {
                        LOGGER.at(Level.FINE).log("[CALC] Step 3 - Conversion: SKIPPED (no conversion stats)");
                    }
                }
            }

            // ==== STEP 4: Apply % increased ====
            if (isSpell) {
                float spellPct = attackerStats.getSpellDamagePercent();
                float dmgPct = attackerStats.getDamagePercent();
                float projPct = projectileSpell ? attackerStats.getProjectileDamagePercent() : 0f;
                float totalPct = spellPct + projPct + dmgPct;
                if (tb != null) {
                    tb.physDmgPercent(0f);
                    tb.attackTypePercent(spellPct + projPct);
                    tb.globalDmgPercent(dmgPct);
                    tb.totalIncreasedPercent(totalPct);
                }
                if (totalPct != 0f) {
                    dist.applyElementalPercentIncrease(spellElement, totalPct);
                }
                if (tb != null) tb.physAfterIncreased(dist.getElemental(spellElement));
                if (traceEnabled) {
                    LOGGER.at(Level.FINE).log("[CALC] Step 4 - %% Increased spell (%s): spellDmg%%=%.1f + proj%%=%.1f + dmg%%=%.1f = +%.1f%% → %.1f",
                        spellElement.name(), spellPct, projPct, dmgPct, totalPct, dist.getElemental(spellElement));
                }
            } else {
                float physPct = attackerStats.getPhysicalDamagePercent();
                float atkTypePct = switch (attackType) {
                    case MELEE -> attackerStats.getMeleeDamagePercent();
                    case PROJECTILE -> attackerStats.getProjectileDamagePercent();
                    case AREA, SPELL, UNKNOWN -> 0f;
                };
                float dmgPct = attackerStats.getDamagePercent();
                float totalPct = physPct + atkTypePct + dmgPct;
                if (tb != null) {
                    tb.physDmgPercent(physPct);
                    tb.attackTypePercent(atkTypePct);
                    tb.globalDmgPercent(dmgPct);
                    tb.totalIncreasedPercent(totalPct);
                }
                // Scale physical slot: phys% + attackType% + dmg%
                applyPercentIncreased(dist, attackerStats, attackType);
                if (tb != null) tb.physAfterIncreased(dist.getPhysical());

                // Elemental melee/ranged: also scale the element slot with attackType% + dmg%.
                // physicalDamagePercent is NOT included — it only scales physical damage.
                // meleeDamagePercent scales ALL melee damage (physical and elemental).
                if (spellElement != null) {
                    float elemPct = atkTypePct + dmgPct;
                    if (elemPct != 0f) {
                        dist.applyElementalPercentIncrease(spellElement, elemPct);
                    }
                }
                if (traceEnabled) {
                    if (spellElement != null) {
                        float elemPct = atkTypePct + dmgPct;
                        LOGGER.at(Level.FINE).log("[CALC] Step 4 - %% Increased elemental melee (%s): atkType%%=%.1f + dmg%%=%.1f = +%.1f%% → %.1f (phys: %.1f)",
                            spellElement.name(), atkTypePct, dmgPct, elemPct, dist.getElemental(spellElement), dist.getPhysical());
                    } else {
                        LOGGER.at(Level.FINE).log("[CALC] Step 4 - %% Increased phys: physDmg%%=%.1f + melee%%=%.1f + dmg%%=%.1f = +%.1f%% → %.1f",
                            physPct, atkTypePct, dmgPct, totalPct, dist.getPhysical());
                    }
                }
            }

            // ==== STEP 5: Apply elemental % modifiers ====
            if (attackerElemental != null) {
                if (tb != null) {
                    for (ElementType type : ElementType.values()) {
                        tb.elemPercentInc(type, (float) attackerElemental.getPercentDamage(type));
                        tb.elemPercentMore(type, (float) attackerElemental.getMultiplierDamage(type));
                    }
                }
                if (traceEnabled) {
                    float firePct = (float) attackerElemental.getPercentDamage(ElementType.FIRE);
                    float waterPct = (float) attackerElemental.getPercentDamage(ElementType.WATER);
                    float lightPct = (float) attackerElemental.getPercentDamage(ElementType.LIGHTNING);
                    float voidPct = (float) attackerElemental.getPercentDamage(ElementType.VOID);
                    float fireMult = (float) attackerElemental.getMultiplierDamage(ElementType.FIRE);
                    float waterMult = (float) attackerElemental.getMultiplierDamage(ElementType.WATER);
                    float lightMult = (float) attackerElemental.getMultiplierDamage(ElementType.LIGHTNING);
                    float voidMult = (float) attackerElemental.getMultiplierDamage(ElementType.VOID);
                    if (firePct != 0 || waterPct != 0 || lightPct != 0 || voidPct != 0 ||
                        fireMult != 0 || waterMult != 0 || lightMult != 0 || voidMult != 0) {
                        LOGGER.at(Level.FINE).log("[CALC] Step 5 - Elemental %%: Fire ×%.2f, Water ×%.2f, Lightning ×%.2f, Void ×%.2f",
                            1f + firePct/100f + fireMult/100f, 1f + waterPct/100f + waterMult/100f,
                            1f + lightPct/100f + lightMult/100f, 1f + voidPct/100f + voidMult/100f);
                    }
                }
                applyElementalModifiers(dist, attackerElemental);
                if (tb != null) {
                    for (ElementType type : ElementType.values()) {
                        tb.elemAfterMod(type, dist.getElemental(type));
                    }
                }
            }

            // ==== STEP 6: Apply % more multipliers (to all damage) ====
            float allDmgPct = attackerStats.getAllDamagePercent();
            float dmgMult = attackerStats.getDamageMultiplier();
            if (tb != null) { tb.allDamagePercent(allDmgPct); tb.damageMultiplier(dmgMult); }
            applyMoreMultipliers(dist, attackerStats);
            if (tb != null) {
                tb.physAfterMore(dist.getPhysical());
                for (ElementType type : ElementType.values()) {
                    tb.elemAfterMore(type, dist.getElemental(type));
                }
            }
            if (traceEnabled) {
                LOGGER.at(Level.FINE).log("[CALC] Step 6 - %% More: allDmg%%=%.1f×, multiplier=%.1f× → phys=%.1f, totalElem=%.1f",
                    1f + allDmgPct / 100f, 1f + dmgMult / 100f, dist.getPhysical(), dist.getTotalElemental());
            }

            // ==== STEP 7: Apply attack type multiplier + conditional multiplier ====
            if (attackTypeMultiplier != 1.0f) {
                dist.applyMultiplier(attackTypeMultiplier);
            }
            if (conditionalMultiplier != 1.0f) {
                dist.applyMultiplier(conditionalMultiplier);
            }
            if (tb != null) {
                tb.physAfterConditionals(dist.getPhysical());
                for (ElementType type : ElementType.values()) {
                    tb.elemAfterConditional(type, dist.getElemental(type));
                }
            }
            if (traceEnabled) {
                LOGGER.at(Level.FINE).log("[CALC] Step 7 - Conditional: ×%.2f → phys=%.1f, totalElem=%.1f",
                    conditionalMultiplier, dist.getPhysical(), dist.getTotalElemental());
            }

            // ==== STEP 8: Roll crit ONCE - applies to ALL damage (skip for DOT) ====
            if (!isDOT) {
                float critCh = attackerStats.getCriticalChance();
                float critMultRaw = attackerStats.getCriticalMultiplier();
                if (tb != null) { tb.critChance(critCh); tb.critMultiplierRaw(critMultRaw); }
                CritResult crit = rollCrit(attackerStats);
                wasCritical = crit.wasCritical();
                if (tb != null) tb.wasCritical(wasCritical);
                if (wasCritical) {
                    critMultiplier = crit.multiplier();
                    dist.applyMultiplier(critMultiplier);
                    if (tb != null) tb.critMultiplierApplied(critMultiplier);
                }
                if (tb != null) {
                    tb.physAfterCrit(dist.getPhysical());
                    for (ElementType type : ElementType.values()) {
                        tb.elemAfterCrit(type, dist.getElemental(type));
                    }
                }
                if (traceEnabled) {
                    LOGGER.at(Level.FINE).log("[CALC] Step 8 - Crit roll: chance=%.1f%%, result=%s ×%.2f → phys=%.1f, totalElem=%.1f",
                        critCh, wasCritical ? "CRIT" : "NO CRIT", wasCritical ? critMultiplier : 1f,
                        dist.getPhysical(), dist.getTotalElemental());
                }
            }
        }

        // ==== STEP 9: Apply defenses per type ====
        float physBefore = dist.getPhysical();
        float preDefenseTotal = physBefore;
        EnumMap<ElementType, Float> preDefenseElemental = new EnumMap<>(ElementType.class);
        for (ElementType type : ElementType.values()) {
            float elemBefore = dist.getElemental(type);
            preDefenseElemental.put(type, elemBefore);
            preDefenseTotal += elemBefore;
            if (tb != null) tb.elemBeforeResist(type, elemBefore);
        }
        if (tb != null) tb.physBeforeArmor(physBefore);

        armorReduction = applyDefenses(
            dist, defenderStats, defenderElemental,
            attackerStats, attackerElemental, resistanceReductions, attackType, attackerLevel, tb
        );

        if (traceEnabled) {
            LOGGER.at(Level.FINE).log("[CALC] Step 9 - Defenses:");
            float armor = defenderStats != null ? defenderStats.getArmor() : 0f;
            float armorPen = attackerStats != null ? attackerStats.getArmorPenetration() : 0f;
            LOGGER.at(Level.FINE).log("[CALC]   Armor: %.1f, pen=%.1f%%, reduction=%.1f%%, phys %.1f→%.1f (vs Lv%d)",
                armor, armorPen, armorReduction, physBefore, dist.getPhysical(), attackerLevel);
            float physResist = defenderStats != null ? defenderStats.getPhysicalResistance() : 0f;
            LOGGER.at(Level.FINE).log("[CALC]   Phys Resist: %.1f%%", physResist);
            LOGGER.at(Level.FINE).log("[CALC]   Fire Resist: %.1f%%, fire %.1f→%.1f",
                resistanceReductions.getOrDefault(ElementType.FIRE, 0f), preDefenseElemental.getOrDefault(ElementType.FIRE, 0f), dist.getElemental(ElementType.FIRE));
            LOGGER.at(Level.FINE).log("[CALC]   Water Resist: %.1f%%, water %.1f→%.1f",
                resistanceReductions.getOrDefault(ElementType.WATER, 0f), preDefenseElemental.getOrDefault(ElementType.WATER, 0f), dist.getElemental(ElementType.WATER));
            LOGGER.at(Level.FINE).log("[CALC]   Lightning Resist: %.1f%%, lightning %.1f→%.1f",
                resistanceReductions.getOrDefault(ElementType.LIGHTNING, 0f), preDefenseElemental.getOrDefault(ElementType.LIGHTNING, 0f), dist.getElemental(ElementType.LIGHTNING));
            LOGGER.at(Level.FINE).log("[CALC]   Void Resist: %.1f%%, void %.1f→%.1f",
                resistanceReductions.getOrDefault(ElementType.VOID, 0f), preDefenseElemental.getOrDefault(ElementType.VOID, 0f), dist.getElemental(ElementType.VOID));
        }

        // ==== STEP 10: Add true damage (bypasses all defenses) ====
        if (attackerStats != null) {
            float trueDmg = attackerStats.getTrueDamage();

            // percentHitAsTrueDamage: X% of total post-defense damage added as true damage
            float pctAsTrueRate = attackerStats.getPercentHitAsTrueDamage();
            if (pctAsTrueRate > 0) {
                float postDefenseTotal = dist.getPhysical() + dist.getTotalElemental();
                trueDmg += postDefenseTotal * pctAsTrueRate / 100f;
            }

            // voidToTrueDamagePercent: X% of Void damage dealt is added as true damage
            float voidTrueRate = attackerStats.getVoidToTrueDamagePercent();
            if (voidTrueRate > 0) {
                float voidDamage = dist.getElemental(ElementType.VOID);
                trueDmg += voidDamage * voidTrueRate / 100f;
            }

            if (trueDmg > 0) {
                dist.addTrueDamage(trueDmg);
                preDefenseTotal += trueDmg;
            }
            if (tb != null) tb.trueDamage(trueDmg);
            if (traceEnabled && trueDmg > 0) {
                LOGGER.at(Level.FINE).log("[CALC] Step 10 - True damage: +%.1f (flat=%.1f, pctHit=%.1f%%, voidTrue=%.1f%%)",
                    trueDmg, attackerStats.getTrueDamage(), pctAsTrueRate, voidTrueRate);
            }
        }

        // Determine primary damage type for indicator color
        DamageType damageType = dist.getPrimaryDamageType();

        if (traceEnabled) {
            float total = dist.getPhysical() + dist.getTrueDamage();
            for (ElementType type : ElementType.values()) total += dist.getElemental(type);
            LOGGER.at(Level.FINE).log("[CALC] ════ CALC RESULT: total=%.1f, type=%s, crit=%s ════",
                total, damageType, wasCritical);
        }

        // Build the result
        return DamageBreakdown.builder()
            .physicalDamage(dist.getPhysical())
            .elementalDamage(new EnumMap<>(dist.getElementalMap()))
            .trueDamage(dist.getTrueDamage())
            .preDefenseDamage(preDefenseTotal)
            .preDefenseElemental(preDefenseElemental)
            .wasCritical(wasCritical)
            .critMultiplier(wasCritical ? critMultiplier : 1.0f)
            .armorReduction(armorReduction)
            .resistanceReduction(ElementType.FIRE, resistanceReductions.getOrDefault(ElementType.FIRE, 0f))
            .resistanceReduction(ElementType.WATER, resistanceReductions.getOrDefault(ElementType.WATER, 0f))
            .resistanceReduction(ElementType.LIGHTNING, resistanceReductions.getOrDefault(ElementType.LIGHTNING, 0f))
            .resistanceReduction(ElementType.EARTH, resistanceReductions.getOrDefault(ElementType.EARTH, 0f))
            .resistanceReduction(ElementType.WIND, resistanceReductions.getOrDefault(ElementType.WIND, 0f))
            .resistanceReduction(ElementType.VOID, resistanceReductions.getOrDefault(ElementType.VOID, 0f))
            .damageType(damageType)
            .attackType(attackType)
            .build();
    }

    /**
     * Calculates damage with full trace of all intermediate values.
     *
     * <p>Same pipeline as {@link #calculate}, but populates a {@link DamageTrace.Builder}
     * at each step capturing every intermediate value. Only called when combat detail
     * mode is enabled ({@code traceEnabled=true}).
     *
     * @param baseDamage Base damage from weapon/attack
     * @param attackerStats Attacker's computed stats (null for environment)
     * @param attackerElemental Attacker's elemental stats (null if none)
     * @param defenderStats Defender's computed stats (null for no defenses)
     * @param defenderElemental Defender's elemental stats (null if none)
     * @param attackType Attack type (melee, projectile, area)
     * @param conditionalMultiplier Combined situational multiplier (without attack type)
     * @param conditionalResult Detailed conditional breakdown (null to use NONE)
     * @param attackTypeMultiplier Attack type multiplier from vanilla weapon profile (1.0 if none)
     * @return DamageTrace with full intermediate values and the final DamageBreakdown
     */
    @Nonnull
    public DamageTrace calculateTraced(
        float baseDamage,
        @Nullable ComputedStats attackerStats,
        @Nullable ElementalStats attackerElemental,
        @Nullable ComputedStats defenderStats,
        @Nullable ElementalStats defenderElemental,
        @Nonnull AttackType attackType,
        float conditionalMultiplier,
        @Nullable ConditionalResult conditionalResult,
        float attackTypeMultiplier,
        @Nullable ElementType spellElement,
        int attackerLevel,
        boolean projectileSpell
    ) {
        DamageTrace.Builder tb = DamageTrace.builder();
        tb.weaponBaseDamage(baseDamage);
        tb.attackType(attackType);
        tb.attackTypeMultiplier(attackTypeMultiplier);
        tb.isMobStats(attackerStats != null && attackerStats.isMobStats());
        tb.conditionals(conditionalResult != null ? conditionalResult : ConditionalResult.NONE);

        DamageBreakdown breakdown = calculatePipeline(baseDamage, attackerStats, attackerElemental,
            defenderStats, defenderElemental, attackType, false, conditionalMultiplier,
            false, spellElement, attackerLevel, projectileSpell,
            tb, attackTypeMultiplier);

        tb.breakdown(breakdown);
        return tb.build();
    }

    /**
     * Simplified calculate for DOT damage (no flat, no crit).
     *
     * @param baseDamage The base DOT damage
     * @param defenderStats The defender's stats
     * @param defenderElemental The defender's elemental stats
     * @param damageElement The element of the DOT (null for physical)
     * @return Damage breakdown
     */
    @Nonnull
    public DamageBreakdown calculateDOT(
        float baseDamage,
        @Nullable ComputedStats defenderStats,
        @Nullable ElementalStats defenderElemental,
        @Nullable ElementType damageElement,
        int attackerLevel
    ) {
        DamageDistribution dist = new DamageDistribution();

        if (damageElement != null) {
            // Elemental DOT
            dist.setElemental(damageElement, baseDamage);
        } else {
            // Physical DOT (like bleed)
            dist.setPhysical(baseDamage);
        }

        // Capture pre-defense values
        float preDefenseTotal = baseDamage;
        EnumMap<ElementType, Float> preDefenseElemental = new EnumMap<>(ElementType.class);
        for (ElementType type : ElementType.values()) {
            preDefenseElemental.put(type, dist.getElemental(type));
        }

        // Apply defenses
        EnumMap<ElementType, Float> resistanceReductions = new EnumMap<>(ElementType.class);
        float armorReduction = applyDefenses(dist, defenderStats, defenderElemental, null, null, resistanceReductions, AttackType.UNKNOWN, attackerLevel, null);

        DamageType damageType = DamageType.fromElement(damageElement);

        return DamageBreakdown.builder()
            .physicalDamage(dist.getPhysical())
            .elementalDamage(new EnumMap<>(dist.getElementalMap()))
            .trueDamage(0f)
            .preDefenseDamage(preDefenseTotal)
            .preDefenseElemental(preDefenseElemental)
            .wasCritical(false)
            .critMultiplier(1.0f)
            .armorReduction(armorReduction)
            .damageType(damageType)
            .attackType(AttackType.UNKNOWN)
            .build();
    }

    // ==================== Average Damage Estimation ====================

    /**
     * Estimates average damage per hit for display purposes (Stats page, tooltips).
     *
     * <p>Runs the EXACT same pipeline as {@link #calculate} (steps 1-7) but:
     * <ul>
     *   <li>Uses expected crit (probability × bonus) instead of RNG rolling</li>
     *   <li>Passes null defender (no defenses applied — shows raw output)</li>
     *   <li>Uses no conditional multiplier (situational, not static)</li>
     *   <li>Includes true damage (added after the pipeline)</li>
     * </ul>
     *
     * <p>This method exists so display code never reimplements the damage formula.
     * If the pipeline changes, this method automatically reflects the change.
     *
     * @param stats The player's computed stats
     * @return Average damage estimate with breakdown details
     */
    @Nonnull
    public DamageEstimate estimateAverageDamage(@Nonnull ComputedStats stats) {
        float baseDamage = stats.getWeaponBaseDamage();
        ElementalStats elemental = stats.toElementalStats();

        // Detect weapon type from equipped item for accurate attack type
        WeaponType weaponType = WeaponType.fromItemIdOrUnknown(stats.getWeaponItemId());
        boolean isSpell = weaponType.isMagic();
        boolean isProjectile = weaponType.isRanged();
        AttackType attackType = isSpell ? AttackType.SPELL
            : isProjectile ? AttackType.PROJECTILE
            : AttackType.MELEE;

        // Resolve element: spells use fixed element or dominant attribute,
        // non-magic elemental weapons (fire sword, etc.) use their stored element.
        ElementType spellElement = null;
        if (isSpell) {
            ElementType fixedElement = stats.getWeaponSpellElement();
            spellElement = fixedElement != null ? fixedElement : findDominantSpellElement(elemental);
        } else {
            spellElement = stats.getWeaponSpellElement();
        }

        // Create damage distribution — elemental weapons place base into element slot
        DamageDistribution dist = new DamageDistribution();
        if (spellElement != null) {
            dist.setElemental(spellElement, baseDamage);
        } else {
            dist.setPhysical(baseDamage);
        }

        // Step 1: Flat damage — applyFlatDamage handles spell/melee/projectile internally
        // For spells: adds getSpellDamage() flat to spell element slot
        // For melee: adds physicalDamage + meleeDamage
        // For projectile: adds physicalDamage only (no meleeDamage)
        applyFlatDamage(dist, stats, attackType, spellElement);

        // Step 2: Flat elemental
        if (elemental.hasAnyElementalDamage()) {
            addFlatElemental(dist, elemental);
        }

        // Step 3: Conversion (physical → elemental, or spell element → target element)
        if (!isSpell) {
            applyConversion(dist, stats);
        } else if (spellElement != null) {
            applySpellConversion(dist, stats, spellElement);
        }

        // Step 4: % Increased
        if (isSpell) {
            float spellPct = stats.getSpellDamagePercent();
            float dmgPct = stats.getDamagePercent();
            float totalPct = spellPct + dmgPct;
            if (totalPct != 0f) {
                dist.applyElementalPercentIncrease(spellElement, totalPct);
            }
        } else {
            applyPercentIncreased(dist, stats, attackType);
            // Elemental melee/ranged: also scale element slot with attackType% + dmg%
            if (spellElement != null) {
                float atkTypePct = switch (attackType) {
                    case MELEE -> stats.getMeleeDamagePercent();
                    case PROJECTILE -> stats.getProjectileDamagePercent();
                    case AREA, SPELL, UNKNOWN -> 0f;
                };
                float elemPct = atkTypePct + stats.getDamagePercent();
                if (elemPct != 0f) {
                    dist.applyElementalPercentIncrease(spellElement, elemPct);
                }
            }
        }

        // Step 5: Elemental modifiers (per-element % inc + % more)
        applyElementalModifiers(dist, elemental);

        // Step 6: % More multipliers
        applyMoreMultipliers(dist, stats);

        // No step 7 (conditionals — situational, not included in average)

        // Capture pre-crit total (physical + elemental, no true damage yet)
        float preCritTotal = dist.getPhysical() + dist.getTotalElemental();

        // Step 8: Expected crit (replaces RNG roll)
        float critChance = stats.getCriticalChance();
        float critMultRaw = stats.getCriticalMultiplier();
        float expectedCritMult;
        if (critChance >= 100f) {
            // Guaranteed crit — use full multiplier
            expectedCritMult = Math.max(1f, critMultRaw / 100f);
        } else if (critChance > 0) {
            expectedCritMult = 1f + (critChance / 100f) * (Math.max(100f, critMultRaw) / 100f - 1f);
        } else {
            expectedCritMult = 1f;
        }
        float afterCrit = preCritTotal * expectedCritMult;

        // Step 10: True damage (bypasses everything, added last)
        float trueDamage = stats.getTrueDamage();
        // percentHitAsTrueDamage: X% of total damage added as true
        float pctAsTrueRate = stats.getPercentHitAsTrueDamage();
        if (pctAsTrueRate > 0) {
            trueDamage += afterCrit * pctAsTrueRate / 100f;
        }
        // voidToTrueDamagePercent: X% of Void damage added as true
        float voidTrueRate = stats.getVoidToTrueDamagePercent();
        if (voidTrueRate > 0) {
            trueDamage += dist.getElemental(ElementType.VOID) * expectedCritMult * voidTrueRate / 100f;
        }
        float totalAverage = afterCrit + trueDamage;

        // Breakdown for tooltip display — matches what applyFlatDamage() actually does
        float flatPhys = isSpell ? 0f : stats.getPhysicalDamage();
        float flatMelee = (attackType == AttackType.MELEE) ? stats.getMeleeDamage() : 0f;
        float flatSpell = isSpell ? stats.getSpellDamage() : 0f;
        float flatElemental = 0f;
        for (ElementType type : ElementType.values()) {
            flatElemental += (float) elemental.getFlatDamage(type);
        }
        float baseTotal = baseDamage + flatPhys + flatMelee + flatSpell + flatElemental;

        // % Increased pool — matches applyPercentIncreased() logic exactly
        float totalIncreasedPct;
        if (isSpell) {
            totalIncreasedPct = stats.getSpellDamagePercent() + stats.getDamagePercent();
        } else {
            totalIncreasedPct = stats.getPhysicalDamagePercent()
                + switch (attackType) {
                    case MELEE -> stats.getMeleeDamagePercent();
                    case PROJECTILE -> stats.getProjectileDamagePercent();
                    default -> 0f;
                }
                + stats.getDamagePercent();
        }
        float increasedMult = 1f + totalIncreasedPct / 100f;

        // % More
        float allDmgPct = stats.getAllDamagePercent();
        float dmgMult = stats.getDamageMultiplier();
        float moreMult = (1f + allDmgPct / 100f) * (1f + dmgMult / 100f);

        // Conversion total
        float totalConvPct = stats.getFireConversion() + stats.getWaterConversion()
            + stats.getLightningConversion() + stats.getEarthConversion()
            + stats.getWindConversion() + stats.getVoidConversion();

        return new DamageEstimate(
            totalAverage,
            baseDamage,
            flatPhys,
            flatMelee,
            flatSpell,
            flatElemental,
            baseTotal,
            totalIncreasedPct,
            increasedMult,
            allDmgPct,
            dmgMult,
            moreMult,
            critChance,
            critMultRaw,
            expectedCritMult,
            totalConvPct,
            dist.getTotalElemental(),
            trueDamage,
            isSpell
        );
    }

    /**
     * Finds the dominant spell element from ElementalStats (highest flat damage).
     * Falls back to WATER (the spell attribute) if no flat elemental damage exists,
     * then to FIRE as last resort.
     */
    @Nonnull
    private ElementType findDominantSpellElement(@Nonnull ElementalStats elemental) {
        ElementType best = null;
        double bestVal = 0;
        for (ElementType type : ElementType.values()) {
            double flat = elemental.getFlatDamage(type);
            if (flat > bestVal) {
                bestVal = flat;
                best = type;
            }
        }
        // Fallback: WATER is the spell attribute, so most spell builds are water-aligned
        return best != null ? best : ElementType.WATER;
    }

    /**
     * Immutable result of {@link #estimateAverageDamage} for display/tooltip use.
     */
    public record DamageEstimate(
        float avgDamagePerHit,
        float weaponBase,
        float flatPhysical,
        float flatMelee,
        float flatSpell,
        float flatElemental,
        float baseTotal,
        float totalIncreasedPct,
        float increasedMult,
        float allDamagePct,
        float damageMultiplier,
        float moreMult,
        float critChance,
        float critMultRaw,
        float expectedCritMult,
        float conversionPct,
        float elementalAfterMods,
        float trueDamage,
        boolean isSpell
    ) {
        public static final DamageEstimate ZERO = new DamageEstimate(
            0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 100f, 1f,
            0f, 0f, 0f, false
        );
    }

    // ==================== Internal Calculation Steps ====================

    /**
     * STEP 1: Apply flat physical damage bonuses.
     *
     * <p>Adds flat physical damage from STR, melee damage for melee attacks.
     * Flat elemental damage is handled separately in addFlatElemental().
     *
     * <p><b>IMPORTANT:</b> For mob stats (isMobStats() == true), we skip flat damage
     * addition entirely. Mob damage is calculated via the weighted RPG formula in
     * MobScalingSystem and uses vanilla attack damage as the base. Adding flat damage
     * on top would cause damage stacking (vanilla + RPG).
     */
    private void applyFlatDamage(
        @Nonnull DamageDistribution dist,
        @Nonnull ComputedStats stats,
        @Nonnull AttackType attackType,
        @Nullable ElementType spellElement
    ) {
        // Skip flat damage for mobs - their damage is already calculated via weighted formula
        // and uses vanilla attack damage as base. Adding flat damage would cause stacking.
        if (stats.isMobStats()) {
            LOGGER.at(Level.FINE).log("Skipping flat damage for mob attacker (already in base damage)");
            return;
        }

        if (attackType == AttackType.SPELL && spellElement != null) {
            // Flat spell damage — added to the spell's element slot
            float flatSpell = stats.getSpellDamage();
            if (flatSpell > 0) {
                dist.addElemental(spellElement, flatSpell);
            }
        } else {
            // Flat physical damage (from STR)
            float flatPhys = stats.getPhysicalDamage();
            if (flatPhys > 0) {
                dist.addPhysical(flatPhys);
            }

            // Flat melee damage (only for melee attacks)
            if (attackType == AttackType.MELEE) {
                float flatMelee = stats.getMeleeDamage();
                if (flatMelee > 0) {
                    dist.addPhysical(flatMelee);
                }
            }
        }
    }

    /**
     * STEP 4: Apply percent increased modifiers to physical (additive within category).
     *
     * <p>Sums all applicable % increased modifiers and applies once:
     * {@code damage * (1 + SUM(%) / 100)}
     */
    private void applyPercentIncreased(
        @Nonnull DamageDistribution dist,
        @Nonnull ComputedStats stats,
        @Nonnull AttackType attackType
    ) {
        // Sum all additive % increased modifiers
        float totalPercent = 0f;

        // Physical damage percent
        totalPercent += stats.getPhysicalDamagePercent();

        // Attack type-specific percent
        // Note: SPELL is handled separately in calculate() — never reaches here
        totalPercent += switch (attackType) {
            case MELEE -> stats.getMeleeDamagePercent();
            case PROJECTILE -> stats.getProjectileDamagePercent();
            case AREA, SPELL, UNKNOWN -> 0f;
        };

        // Global damage percent (allDamagePercent is separate multiplier layer in old code)
        // Adding it here for simplicity, but could be moved to MORE step
        totalPercent += stats.getDamagePercent();

        // Apply the combined increase
        if (totalPercent != 0f) {
            dist.applyPhysicalPercentIncrease(totalPercent);
        }
    }

    /**
     * STEP 6: Apply percent more modifiers (multiplicative chain).
     *
     * <p>Each % more modifier is applied separately as a multiplier to ALL damage types:
     * {@code damage * (1 + more1) * (1 + more2) * ...}
     */
    private void applyMoreMultipliers(
        @Nonnull DamageDistribution dist,
        @Nonnull ComputedStats stats
    ) {
        // All damage percent (global multiplier)
        float allDmgPct = stats.getAllDamagePercent();
        if (allDmgPct != 0f) {
            dist.applyMultiplier(1f + allDmgPct / 100f);
        }

        // Damage multiplier (separate multiplicative layer)
        float dmgMult = stats.getDamageMultiplier();
        if (dmgMult != 0f) {
            dist.applyMultiplier(1f + dmgMult / 100f);
        }
    }

    /**
     * STEP 8: Roll critical strike.
     *
     * <p>Rolls once for the entire attack. The crit multiplier is applied to ALL
     * damage types (physical + elemental) equally. Returns the crit result.
     */
    @Nonnull
    private CritResult rollCrit(@Nonnull ComputedStats stats) {
        float critChance = stats.getCriticalChance();
        if (critChance <= 0) {
            return CritResult.NO_CRIT;
        }

        boolean wasCrit = ThreadLocalRandom.current().nextFloat() * 100f < critChance;
        if (!wasCrit) {
            return CritResult.NO_CRIT;
        }

        float critMult = stats.getCriticalMultiplier() / 100f; // Convert 150 -> 1.5
        return new CritResult(true, critMult);
    }

    /**
     * STEP 3: Apply damage conversion (physical to elemental).
     *
     * <p>Converts a percentage of physical damage to elemental. Total conversion
     * is capped at 100%. Conversion happens EARLY so converted damage benefits
     * from elemental % modifiers.
     */
    private void applyConversion(
        @Nonnull DamageDistribution dist,
        @Nonnull ComputedStats stats
    ) {
        float firePct = stats.getFireConversion();
        float waterPct = stats.getWaterConversion();
        float lightningPct = stats.getLightningConversion();
        float earthPct = stats.getEarthConversion();
        float windPct = stats.getWindConversion();
        float voidPct = stats.getVoidConversion();
        float totalConversion = firePct + waterPct + lightningPct + earthPct + windPct + voidPct;

        if (totalConversion <= 0) {
            return;
        }

        // Cap total conversion at 100%
        float scale = totalConversion > 100f ? 100f / totalConversion : 1f;

        // Store original physical value - ALL conversions are calculated from this
        float originalPhysical = dist.getPhysical();

        // Calculate and add each elemental conversion (from original physical, not current)
        float totalConverted = 0f;

        if (firePct > 0) {
            float effectivePct = firePct * scale;
            float converted = originalPhysical * effectivePct / 100f;
            dist.addElemental(ElementType.FIRE, converted);
            totalConverted += converted;
        }
        if (waterPct > 0) {
            float effectivePct = waterPct * scale;
            float converted = originalPhysical * effectivePct / 100f;
            dist.addElemental(ElementType.WATER, converted);
            totalConverted += converted;
        }
        if (lightningPct > 0) {
            float effectivePct = lightningPct * scale;
            float converted = originalPhysical * effectivePct / 100f;
            dist.addElemental(ElementType.LIGHTNING, converted);
            totalConverted += converted;
        }
        if (earthPct > 0) {
            float effectivePct = earthPct * scale;
            float converted = originalPhysical * effectivePct / 100f;
            dist.addElemental(ElementType.EARTH, converted);
            totalConverted += converted;
        }
        if (windPct > 0) {
            float effectivePct = windPct * scale;
            float converted = originalPhysical * effectivePct / 100f;
            dist.addElemental(ElementType.WIND, converted);
            totalConverted += converted;
        }
        if (voidPct > 0) {
            float effectivePct = voidPct * scale;
            float converted = originalPhysical * effectivePct / 100f;
            dist.addElemental(ElementType.VOID, converted);
            totalConverted += converted;
        }

        // Subtract total converted from physical
        dist.setPhysical(originalPhysical - totalConverted);
    }

    /**
     * STEP 3 (spell variant): Apply damage conversion from spell's base element.
     *
     * <p>For spells, there is no physical damage to convert. Instead, we convert
     * from the spell's base element to other elements. For example, a Fire spell
     * with 50% Void Conversion converts 50% of fire damage to void.
     *
     * <p>Conversion INTO the spell's own element is ignored (can't convert fire→fire).
     * Only elements that differ from the source are eligible targets.
     */
    private void applySpellConversion(
        @Nonnull DamageDistribution dist,
        @Nonnull ComputedStats stats,
        @Nonnull ElementType sourceElement
    ) {
        // Collect all conversion percentages that point to a DIFFERENT element
        float totalConversion = 0f;
        EnumMap<ElementType, Float> conversionPcts = new EnumMap<>(ElementType.class);

        for (ElementType target : ElementType.values()) {
            if (target == sourceElement) continue; // Can't convert to own element
            float pct = getConversionForElement(stats, target);
            if (pct > 0) {
                conversionPcts.put(target, pct);
                totalConversion += pct;
            }
        }

        if (totalConversion <= 0) return;

        // Cap at 100%
        float scale = totalConversion > 100f ? 100f / totalConversion : 1f;

        // Convert from the spell's base element
        float originalAmount = dist.getElemental(sourceElement);
        float totalConverted = 0f;

        for (var entry : conversionPcts.entrySet()) {
            float effectivePct = entry.getValue() * scale;
            float converted = originalAmount * effectivePct / 100f;
            dist.addElemental(entry.getKey(), converted);
            totalConverted += converted;
        }

        // Subtract converted amount from source element
        dist.setElemental(sourceElement, originalAmount - totalConverted);
    }

    /**
     * Gets the conversion percentage for a specific target element from stats.
     */
    private float getConversionForElement(@Nonnull ComputedStats stats, @Nonnull ElementType element) {
        return switch (element) {
            case FIRE -> stats.getFireConversion();
            case WATER -> stats.getWaterConversion();
            case LIGHTNING -> stats.getLightningConversion();
            case EARTH -> stats.getEarthConversion();
            case WIND -> stats.getWindConversion();
            case VOID -> stats.getVoidConversion();
        };
    }

    /**
     * STEP 2: Add flat elemental damage.
     *
     * <p>Adds flat elemental damage from gear/skills. This is added EARLY
     * (before conversion) so it benefits from elemental modifiers AND crit.
     */
    private void addFlatElemental(
        @Nonnull DamageDistribution dist,
        @Nonnull ElementalStats elemental
    ) {
        for (ElementType type : ElementType.values()) {
            double flat = elemental.getFlatDamage(type);
            if (flat > 0) {
                dist.addElemental(type, (float) flat);
            }
        }
    }

    /**
     * STEP 5: Apply elemental percent modifiers.
     *
     * <p>Applies element-specific % increased and % more to each element.
     * This now includes converted damage, so fire conversion benefits from +fire%.
     */
    private void applyElementalModifiers(
        @Nonnull DamageDistribution dist,
        @Nonnull ElementalStats elemental
    ) {
        for (ElementType type : ElementType.values()) {
            float current = dist.getElemental(type);
            if (current <= 0) continue;

            // % increased (additive)
            double pctInc = elemental.getPercentDamage(type);
            if (pctInc != 0) {
                dist.applyElementalPercentIncrease(type, (float) pctInc);
            }

            // % more (multiplicative)
            double pctMore = elemental.getMultiplierDamage(type);
            if (pctMore != 0) {
                dist.applyElementalMultiplier(type, 1f + (float) pctMore / 100f);
            }
        }
    }

    /**
     * STEP 9: Apply defenses per damage type.
     *
     * <p>Applies armor to physical, resistances to elemental. True damage is added
     * separately AFTER this step and bypasses all defenses.
     *
     * @param tb Optional trace builder for death recap detail (null = no recording)
     * @return Armor reduction percentage for death recap
     */
    private float applyDefenses(
        @Nonnull DamageDistribution dist,
        @Nullable ComputedStats defenderStats,
        @Nullable ElementalStats defenderElemental,
        @Nullable ComputedStats attackerStats,
        @Nullable ElementalStats attackerElemental,
        @Nonnull EnumMap<ElementType, Float> resistanceReductions,
        @Nonnull AttackType attackType,
        int attackerLevel,
        @Nullable DamageTrace.Builder tb
    ) {
        float armorReduction = 0f;

        if (defenderStats == null) {
            return armorReduction;
        }

        // Apply armor to physical damage
        float physDamage = dist.getPhysical();
        if (physDamage > 0) {
            float armorPen = attackerStats != null ? attackerStats.getArmorPenetration() : 0f;
            if (tb != null) tb.armorPenPercent(armorPen);

            CombatCalculator.ArmorResult armorResult = combatCalculator.calculateDefenderReduction(
                physDamage, defenderStats, armorPen, attackerLevel
            );
            dist.setPhysical(armorResult.finalDamage());
            armorReduction = armorResult.reductionPercent();

            if (tb != null) {
                tb.defenderArmor(armorResult.armorValue());
                tb.armorPercent(defenderStats.getArmorPercent());
                tb.effectiveArmor(armorResult.effectiveArmor());
                tb.armorReductionPercent(armorReduction);
                tb.physAfterArmor(armorResult.finalDamage());
            }

            // Apply physical resistance after armor
            float physResist = defenderStats.getPhysicalResistance();
            if (tb != null) tb.physResistPercent(physResist);
            if (physResist > 0 && dist.getPhysical() > 0) {
                float cappedResist = Math.min(physResist, MAX_RESISTANCE_CAP);
                dist.applyPhysicalMultiplier(1f - cappedResist / 100f);
            }
            if (tb != null) tb.physAfterResist(dist.getPhysical());
        }

        // Spell penetration: additional penetration for all elements on spell attacks
        float spellPen = (attackType == AttackType.SPELL && attackerStats != null)
            ? attackerStats.getSpellPenetration() : 0f;

        // Apply elemental resistances
        ElementalStats defElem = defenderElemental != null ? defenderElemental : new ElementalStats();
        for (ElementType type : ElementType.values()) {
            float elemDamage = dist.getElemental(type);
            double rawResist = defElem.getResistance(type);
            double penetration = (attackerElemental != null ? attackerElemental.getPenetration(type) : 0) + spellPen;

            if (tb != null) {
                tb.defenderRawResist(type, (float) rawResist);
                tb.attackerPen(type, (float) penetration);
            }

            if (elemDamage <= 0 || Float.isNaN(elemDamage)) {
                if (tb != null) {
                    tb.effectiveResist(type, 0f);
                    tb.elemAfterResist(type, 0f);
                }
                continue;
            }

            double effectiveResist = ElementalCalculator.getEffectiveResistance(rawResist, penetration);
            float finalDamage = (float) (elemDamage * (1.0 - effectiveResist / 100.0));

            dist.setElemental(type, finalDamage);
            resistanceReductions.put(type, (float) effectiveResist);

            if (tb != null) {
                tb.effectiveResist(type, (float) effectiveResist);
                tb.elemAfterResist(type, finalDamage);
            }
        }

        return armorReduction;
    }

    /**
     * Calculates damage with forced crit result (for testing).
     *
     * @param baseDamage The base damage
     * @param attackerStats The attacker's stats
     * @param attackerElemental The attacker's elemental stats
     * @param defenderStats The defender's stats
     * @param defenderElemental The defender's elemental stats
     * @param attackType The attack type
     * @param forceCrit true to force crit, false to force non-crit
     * @return Damage breakdown
     */
    @Nonnull
    public DamageBreakdown calculateWithForcedCrit(
        float baseDamage,
        @Nullable ComputedStats attackerStats,
        @Nullable ElementalStats attackerElemental,
        @Nullable ComputedStats defenderStats,
        @Nullable ElementalStats defenderElemental,
        @Nonnull AttackType attackType,
        boolean forceCrit,
        int attackerLevel
    ) {
        // Initialize damage distribution
        DamageDistribution dist = new DamageDistribution();
        dist.setPhysical(baseDamage);

        // Track defense breakdown
        float armorReduction = 0f;
        EnumMap<ElementType, Float> resistanceReductions = new EnumMap<>(ElementType.class);

        boolean wasCritical = false;
        float critMultiplier = 1.0f;

        if (attackerStats != null) {
            // STEP 1: Flat physical damage (test helper — no spell support)
            applyFlatDamage(dist, attackerStats, attackType, null);

            // STEP 2: Flat elemental damage (skip for mobs — pool budget only)
            if (attackerElemental != null && !attackerStats.isMobStats()) {
                addFlatElemental(dist, attackerElemental);
            }

            // STEP 3: Conversion (early so converted damage benefits from elemental modifiers)
            applyConversion(dist, attackerStats);

            // STEP 4: % increased physical
            applyPercentIncreased(dist, attackerStats, attackType);

            // STEP 5: Elemental % modifiers (now includes converted damage)
            if (attackerElemental != null) {
                applyElementalModifiers(dist, attackerElemental);
            }

            // STEP 6: % more multipliers
            applyMoreMultipliers(dist, attackerStats);

            // STEP 7: (No conditional multiplier in this method)

            // STEP 8: Forced crit - applies to ALL damage types
            if (forceCrit) {
                wasCritical = true;
                critMultiplier = attackerStats.getCriticalMultiplier() / 100f;
                dist.applyMultiplier(critMultiplier);
            }
        }

        // STEP 9: Capture pre-defense values AFTER crit, BEFORE defenses
        float physBefore = dist.getPhysical();
        float preDefenseTotal = physBefore;
        EnumMap<ElementType, Float> preDefenseElemental = new EnumMap<>(ElementType.class);
        for (ElementType type : ElementType.values()) {
            float elemBefore = dist.getElemental(type);
            preDefenseElemental.put(type, elemBefore);
            preDefenseTotal += elemBefore;
        }

        armorReduction = applyDefenses(dist, defenderStats, defenderElemental,
            attackerStats, attackerElemental, resistanceReductions, attackType, attackerLevel, null);

        // STEP 10: True damage added AFTER defenses (bypasses them)
        if (attackerStats != null) {
            float trueDmg = attackerStats.getTrueDamage();

            float pctAsTrueRate = attackerStats.getPercentHitAsTrueDamage();
            if (pctAsTrueRate > 0) {
                float postDefenseTotal = dist.getPhysical() + dist.getTotalElemental();
                trueDmg += postDefenseTotal * pctAsTrueRate / 100f;
            }

            float voidTrueRate = attackerStats.getVoidToTrueDamagePercent();
            if (voidTrueRate > 0) {
                trueDmg += dist.getElemental(ElementType.VOID) * voidTrueRate / 100f;
            }

            if (trueDmg > 0) {
                dist.addTrueDamage(trueDmg);
                preDefenseTotal += trueDmg;
            }
        }

        DamageType damageType = dist.getPrimaryDamageType();

        return DamageBreakdown.builder()
            .physicalDamage(dist.getPhysical())
            .elementalDamage(new EnumMap<>(dist.getElementalMap()))
            .trueDamage(dist.getTrueDamage())
            .preDefenseDamage(preDefenseTotal)
            .preDefenseElemental(preDefenseElemental)
            .wasCritical(wasCritical)
            .critMultiplier(wasCritical ? critMultiplier : 1.0f)
            .armorReduction(armorReduction)
            .damageType(damageType)
            .attackType(attackType)
            .build();
    }

    // ==================== Helper Types ====================

    /**
     * Result of a critical strike roll.
     */
    public record CritResult(boolean wasCritical, float multiplier) {
        /** No crit result */
        public static final CritResult NO_CRIT = new CritResult(false, 1.0f);
    }
}
