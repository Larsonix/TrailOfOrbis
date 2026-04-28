package io.github.larsonix.trailoforbis.combat;

import com.hypixel.hytale.logger.HytaleLogger;
import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.elemental.ElementType;
import io.github.larsonix.trailoforbis.elemental.ElementalCalculator;
import io.github.larsonix.trailoforbis.elemental.ElementalStats;

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

    /** Armor formula divisor (PoE standard) */
    private static final float ARMOR_FORMULA_DIVISOR = 10.0f;

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
            defenderStats, defenderElemental, attackType, isDOT, 1.0f);
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
        float conditionalMultiplier
    ) {
        return calculate(baseDamage, attackerStats, attackerElemental, defenderStats,
            defenderElemental, attackType, isDOT, conditionalMultiplier, false);
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
        boolean traceEnabled
    ) {
        // Initialize damage distribution
        DamageDistribution dist = new DamageDistribution();
        dist.setPhysical(baseDamage);

        // Track defense breakdown for death recap
        float armorReduction = 0f;
        EnumMap<ElementType, Float> resistanceReductions = new EnumMap<>(ElementType.class);
        for (ElementType type : ElementType.values()) {
            resistanceReductions.put(type, 0f);
        }

        // Crit result (will be determined during calculation)
        boolean wasCritical = false;
        float critMultiplier = 1.0f;

        if (attackerStats != null) {
            // ==== STEP 1: Add flat physical damage (skip for DOT) ====
            float beforeFlat = dist.getPhysical();
            float flatPhys = 0f, flatMelee = 0f;
            if (!isDOT) {
                flatPhys = attackerStats.getPhysicalDamage();
                flatMelee = (attackType == AttackType.MELEE) ? attackerStats.getMeleeDamage() : 0f;
                applyFlatDamage(dist, attackerStats, attackType);
            }
            if (traceEnabled) {
                LOGGER.at(Level.FINE).log("[CALC] Step 1 - Flat physical: base=%.1f + flatPhys=%.1f + flatMelee=%.1f = %.1f",
                    beforeFlat, flatPhys, flatMelee, dist.getPhysical());
            }

            // ==== STEP 2: Add flat elemental damage (skip for DOT) ====
            // Added EARLY so it benefits from elemental modifiers and crit
            if (attackerElemental != null && !isDOT) {
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
            // Conversion happens EARLY so converted damage benefits from elemental modifiers
            float physBeforeConv = dist.getPhysical();
            float fireConv = attackerStats.getFireConversion();
            float waterConv = attackerStats.getWaterConversion();
            float lightConv = attackerStats.getLightningConversion();
            float earthConv = attackerStats.getEarthConversion();
            float windConv = attackerStats.getWindConversion();
            float voidConv = attackerStats.getVoidConversion();
            applyConversion(dist, attackerStats);
            if (traceEnabled && (fireConv > 0 || waterConv > 0 || lightConv > 0 || earthConv > 0 || windConv > 0 || voidConv > 0)) {
                LOGGER.at(Level.FINE).log("[CALC] Step 3 - Conversion: Fire=%.1f%%, Water=%.1f%%, Lightning=%.1f%%, Earth=%.1f%%, Wind=%.1f%%, Void=%.1f%%",
                    fireConv, waterConv, lightConv, earthConv, windConv, voidConv);
                LOGGER.at(Level.FINE).log("[CALC]          Phys: %.1f → %.1f | Fire: %.1f | Water: %.1f | Light: %.1f | Earth: %.1f | Wind: %.1f | Void: %.1f",
                    physBeforeConv, dist.getPhysical(),
                    dist.getElemental(ElementType.FIRE), dist.getElemental(ElementType.WATER),
                    dist.getElemental(ElementType.LIGHTNING), dist.getElemental(ElementType.EARTH),
                    dist.getElemental(ElementType.WIND), dist.getElemental(ElementType.VOID));
            }

            // ==== STEP 4: Apply % increased to physical ====
            float beforePercent = dist.getPhysical();
            float physPct = attackerStats.getPhysicalDamagePercent();
            float meleePct = (attackType == AttackType.MELEE) ? attackerStats.getMeleeDamagePercent() : 0f;
            float projPct = (attackType == AttackType.PROJECTILE) ? attackerStats.getProjectileDamagePercent() : 0f;
            float dmgPct = attackerStats.getDamagePercent();
            float totalPct = physPct + meleePct + projPct + dmgPct;
            applyPercentIncreased(dist, attackerStats, attackType);
            if (traceEnabled) {
                LOGGER.at(Level.FINE).log("[CALC] Step 4 - %% Increased phys: physDmg%%=%.1f + melee%%=%.1f + dmg%%=%.1f = +%.1f%% → %.1f",
                    physPct, meleePct + projPct, dmgPct, totalPct, dist.getPhysical());
            }

            // ==== STEP 5: Apply elemental % modifiers ====
            // Now includes converted damage, so fire conversion benefits from +fire% damage
            if (attackerElemental != null) {
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
            }

            // ==== STEP 6: Apply % more multipliers (to all damage) ====
            float allDmgPct = attackerStats.getAllDamagePercent();
            float dmgMult = attackerStats.getDamageMultiplier();
            applyMoreMultipliers(dist, attackerStats);
            if (traceEnabled) {
                LOGGER.at(Level.FINE).log("[CALC] Step 6 - %% More: allDmg%%=%.1f×, multiplier=%.1f× → phys=%.1f, totalElem=%.1f",
                    1f + allDmgPct / 100f, 1f + dmgMult / 100f, dist.getPhysical(), dist.getTotalElemental());
            }

            // ==== STEP 7: Apply conditional multiplier ====
            // Combines realm damage, execute, vs frozen/shocked, low life bonuses
            if (conditionalMultiplier != 1.0f) {
                dist.applyMultiplier(conditionalMultiplier);
            }
            if (traceEnabled) {
                LOGGER.at(Level.FINE).log("[CALC] Step 7 - Conditional: ×%.2f → phys=%.1f, totalElem=%.1f",
                    conditionalMultiplier, dist.getPhysical(), dist.getTotalElemental());
            }

            // ==== STEP 8: Roll crit ONCE - applies to ALL damage (skip for DOT) ====
            // Critical strike now happens AFTER all scaling, so it multiplies BOTH
            // physical AND elemental damage equally (including flat elemental)
            if (!isDOT) {
                float critChance = attackerStats.getCriticalChance();
                CritResult crit = rollCrit(attackerStats);
                wasCritical = crit.wasCritical();
                if (wasCritical) {
                    critMultiplier = crit.multiplier();
                    dist.applyMultiplier(critMultiplier);
                }
                if (traceEnabled) {
                    LOGGER.at(Level.FINE).log("[CALC] Step 8 - Crit roll: chance=%.1f%%, result=%s ×%.2f → phys=%.1f, totalElem=%.1f",
                        critChance, wasCritical ? "CRIT" : "NO CRIT", wasCritical ? critMultiplier : 1f,
                        dist.getPhysical(), dist.getTotalElemental());
                }
            }
        }

        // ==== STEP 9: Apply defenses per type ====
        // Capture pre-defense values AFTER crit but BEFORE defenses (for accurate combat log)
        float physBefore = dist.getPhysical();
        float preDefenseTotal = physBefore;
        EnumMap<ElementType, Float> preDefenseElemental = new EnumMap<>(ElementType.class);
        for (ElementType type : ElementType.values()) {
            float elemBefore = dist.getElemental(type);
            preDefenseElemental.put(type, elemBefore);
            preDefenseTotal += elemBefore;
        }

        armorReduction = applyDefenses(
            dist, defenderStats, defenderElemental,
            attackerStats, attackerElemental, resistanceReductions
        );

        if (traceEnabled) {
            LOGGER.at(Level.FINE).log("[CALC] Step 9 - Defenses:");
            float armor = defenderStats != null ? defenderStats.getArmor() : 0f;
            float armorPen = attackerStats != null ? attackerStats.getArmorPenetration() : 0f;
            LOGGER.at(Level.FINE).log("[CALC]   Armor: %.1f, pen=%.1f%%, reduction=%.1f%%, phys %.1f→%.1f",
                armor, armorPen, armorReduction, physBefore, dist.getPhysical());
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
        // Added LAST so it's not affected by any defenses
        if (attackerStats != null) {
            float trueDmg = attackerStats.getTrueDamage();
            if (trueDmg > 0) {
                dist.addTrueDamage(trueDmg);
                preDefenseTotal += trueDmg; // Include in pre-defense total for combat log
                if (traceEnabled) {
                    LOGGER.at(Level.FINE).log("[CALC] Step 10 - True damage: +%.1f (bypasses defenses)", trueDmg);
                }
            }
        }

        // Determine primary damage type for indicator color
        DamageType damageType = dist.getPrimaryDamageType();

        // Calculate total for logging
        float total = dist.getPhysical() + dist.getTrueDamage();
        for (ElementType type : ElementType.values()) {
            total += dist.getElemental(type);
        }

        if (traceEnabled) {
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
        float attackTypeMultiplier
    ) {
        DamageTrace.Builder tb = DamageTrace.builder();
        tb.weaponBaseDamage(baseDamage);
        tb.attackType(attackType);
        tb.attackTypeMultiplier(attackTypeMultiplier);
        tb.isMobStats(attackerStats != null && attackerStats.isMobStats());
        tb.conditionals(conditionalResult != null ? conditionalResult : ConditionalResult.NONE);

        // Initialize damage distribution
        DamageDistribution dist = new DamageDistribution();
        dist.setPhysical(baseDamage);

        // Track defense breakdown
        float armorReduction = 0f;
        EnumMap<ElementType, Float> resistanceReductions = new EnumMap<>(ElementType.class);
        for (ElementType type : ElementType.values()) {
            resistanceReductions.put(type, 0f);
        }

        boolean wasCritical = false;
        float critMultiplier = 1.0f;

        if (attackerStats != null) {
            // ==== STEP 1: Flat physical damage ====
            float beforeFlat = dist.getPhysical();
            tb.physBeforeFlat(beforeFlat);
            float flatPhys = attackerStats.getPhysicalDamage();
            float flatMelee = (attackType == AttackType.MELEE) ? attackerStats.getMeleeDamage() : 0f;
            tb.flatPhysFromStats(flatPhys);
            tb.flatMelee(flatMelee);
            applyFlatDamage(dist, attackerStats, attackType);
            tb.physAfterFlat(dist.getPhysical());

            // ==== STEP 2: Flat elemental damage ====
            if (attackerElemental != null) {
                for (ElementType type : ElementType.values()) {
                    float flat = (float) attackerElemental.getFlatDamage(type);
                    if (flat > 0) {
                        tb.flatElemental(type, flat);
                    }
                }
                addFlatElemental(dist, attackerElemental);
            }

            // ==== STEP 3: Damage conversion ====
            float physBeforeConv = dist.getPhysical();
            tb.physBeforeConversion(physBeforeConv);
            float fireConv = attackerStats.getFireConversion();
            float waterConv = attackerStats.getWaterConversion();
            float lightConv = attackerStats.getLightningConversion();
            float earthConv = attackerStats.getEarthConversion();
            float windConv = attackerStats.getWindConversion();
            float voidConv = attackerStats.getVoidConversion();
            float totalConv = fireConv + waterConv + lightConv + earthConv + windConv + voidConv;
            float scale = totalConv > 100f ? 100f / totalConv : 1f;
            tb.scaleFactor(scale);
            tb.conversionPercent(ElementType.FIRE, fireConv);
            tb.conversionPercent(ElementType.WATER, waterConv);
            tb.conversionPercent(ElementType.LIGHTNING, lightConv);
            tb.conversionPercent(ElementType.EARTH, earthConv);
            tb.conversionPercent(ElementType.WIND, windConv);
            tb.conversionPercent(ElementType.VOID, voidConv);

            // Capture pre-conversion elemental for each type to calculate converted amounts
            EnumMap<ElementType, Float> elemBefore = new EnumMap<>(ElementType.class);
            for (ElementType type : ElementType.values()) {
                elemBefore.put(type, dist.getElemental(type));
            }
            applyConversion(dist, attackerStats);
            tb.physAfterConversion(dist.getPhysical());
            for (ElementType type : ElementType.values()) {
                float converted = dist.getElemental(type) - elemBefore.get(type);
                if (converted > 0) {
                    tb.convertedAmount(type, converted);
                }
            }

            // ==== STEP 4: % Increased physical ====
            float physPct = attackerStats.getPhysicalDamagePercent();
            float atkTypePct = switch (attackType) {
                case MELEE -> attackerStats.getMeleeDamagePercent();
                case PROJECTILE -> attackerStats.getProjectileDamagePercent();
                case AREA, UNKNOWN -> 0f;
            };
            float dmgPct = attackerStats.getDamagePercent();
            float totalPct = physPct + atkTypePct + dmgPct;
            tb.physDmgPercent(physPct);
            tb.attackTypePercent(atkTypePct);
            tb.globalDmgPercent(dmgPct);
            tb.totalIncreasedPercent(totalPct);
            applyPercentIncreased(dist, attackerStats, attackType);
            tb.physAfterIncreased(dist.getPhysical());

            // ==== STEP 5: Elemental modifiers ====
            if (attackerElemental != null) {
                for (ElementType type : ElementType.values()) {
                    float pctInc = (float) attackerElemental.getPercentDamage(type);
                    float pctMore = (float) attackerElemental.getMultiplierDamage(type);
                    tb.elemPercentInc(type, pctInc);
                    tb.elemPercentMore(type, pctMore);
                }
                applyElementalModifiers(dist, attackerElemental);
                for (ElementType type : ElementType.values()) {
                    tb.elemAfterMod(type, dist.getElemental(type));
                }
            }

            // ==== STEP 6: % More multipliers ====
            float allDmgPct = attackerStats.getAllDamagePercent();
            float dmgMult = attackerStats.getDamageMultiplier();
            tb.allDamagePercent(allDmgPct);
            tb.damageMultiplier(dmgMult);
            applyMoreMultipliers(dist, attackerStats);
            tb.physAfterMore(dist.getPhysical());
            for (ElementType type : ElementType.values()) {
                tb.elemAfterMore(type, dist.getElemental(type));
            }

            // ==== STEP 7: Attack type multiplier + Conditional multipliers ====
            if (attackTypeMultiplier != 1.0f) {
                dist.applyMultiplier(attackTypeMultiplier);
            }
            if (conditionalMultiplier != 1.0f) {
                dist.applyMultiplier(conditionalMultiplier);
            }
            // Capture after both attack type and conditionals (always)
            tb.physAfterConditionals(dist.getPhysical());
            for (ElementType type : ElementType.values()) {
                tb.elemAfterConditional(type, dist.getElemental(type));
            }

            // ==== STEP 8: Critical strike ====
            float critCh = attackerStats.getCriticalChance();
            float critMultRaw = attackerStats.getCriticalMultiplier();
            tb.critChance(critCh);
            tb.critMultiplierRaw(critMultRaw);
            CritResult crit = rollCrit(attackerStats);
            wasCritical = crit.wasCritical();
            tb.wasCritical(wasCritical);
            if (wasCritical) {
                critMultiplier = crit.multiplier();
                dist.applyMultiplier(critMultiplier);
                tb.critMultiplierApplied(critMultiplier);
            }
            // Capture after crit (always, even if no crit)
            tb.physAfterCrit(dist.getPhysical());
            for (ElementType type : ElementType.values()) {
                tb.elemAfterCrit(type, dist.getElemental(type));
            }
        }

        // ==== STEP 9: Defenses ====
        float physBefore = dist.getPhysical();
        float preDefenseTotal = physBefore;
        EnumMap<ElementType, Float> preDefenseElemental = new EnumMap<>(ElementType.class);
        for (ElementType type : ElementType.values()) {
            float elemBefore = dist.getElemental(type);
            preDefenseElemental.put(type, elemBefore);
            preDefenseTotal += elemBefore;
            tb.elemBeforeResist(type, elemBefore);
        }
        tb.physBeforeArmor(physBefore);

        // Apply defenses and capture detailed armor result
        armorReduction = applyDefensesTraced(
            dist, defenderStats, defenderElemental,
            attackerStats, attackerElemental, resistanceReductions, tb
        );

        // ==== STEP 10: True damage ====
        if (attackerStats != null) {
            float trueDmg = attackerStats.getTrueDamage();
            if (trueDmg > 0) {
                dist.addTrueDamage(trueDmg);
                preDefenseTotal += trueDmg;
            }
            tb.trueDamage(trueDmg);
        }

        // Determine primary damage type
        DamageType damageType = dist.getPrimaryDamageType();

        // Build the DamageBreakdown
        DamageBreakdown breakdown = DamageBreakdown.builder()
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

        tb.breakdown(breakdown);
        return tb.build();
    }

    /**
     * Applies defenses with detailed trace capture.
     *
     * <p>Same logic as {@link #applyDefenses} but also populates the trace builder
     * with armor details, physical resistance, and per-element resistance values.
     *
     * @return Armor reduction percentage for death recap
     */
    private float applyDefensesTraced(
        @Nonnull DamageDistribution dist,
        @Nullable ComputedStats defenderStats,
        @Nullable ElementalStats defenderElemental,
        @Nullable ComputedStats attackerStats,
        @Nullable ElementalStats attackerElemental,
        @Nonnull EnumMap<ElementType, Float> resistanceReductions,
        @Nonnull DamageTrace.Builder tb
    ) {
        float armorReduction = 0f;

        if (defenderStats == null) {
            return armorReduction;
        }

        // Apply armor to physical damage
        float physDamage = dist.getPhysical();
        if (physDamage > 0) {
            float armorPen = attackerStats != null ? attackerStats.getArmorPenetration() : 0f;
            tb.armorPenPercent(armorPen);

            CombatCalculator.ArmorResult armorResult = combatCalculator.calculateDefenderReduction(
                physDamage, defenderStats, armorPen
            );
            dist.setPhysical(armorResult.finalDamage());
            armorReduction = armorResult.reductionPercent();

            tb.defenderArmor(armorResult.armorValue());
            tb.armorPercent(defenderStats.getArmorPercent());
            tb.effectiveArmor(armorResult.effectiveArmor());
            tb.armorReductionPercent(armorReduction);
            tb.physAfterArmor(armorResult.finalDamage());

            // Physical resistance after armor
            float physResist = defenderStats.getPhysicalResistance();
            tb.physResistPercent(physResist);
            if (physResist > 0 && dist.getPhysical() > 0) {
                float cappedResist = Math.min(physResist, 75f); // MAX_RESISTANCE_CAP
                dist.applyPhysicalMultiplier(1f - cappedResist / 100f);
            }
            tb.physAfterResist(dist.getPhysical());
        }

        // Apply elemental resistances
        ElementalStats defElem = defenderElemental != null ? defenderElemental : new ElementalStats();
        for (ElementType type : ElementType.values()) {
            float elemDamage = dist.getElemental(type);
            double rawResist = defElem.getResistance(type);
            double penetration = attackerElemental != null ? attackerElemental.getPenetration(type) : 0;

            tb.defenderRawResist(type, (float) rawResist);
            tb.attackerPen(type, (float) penetration);

            if (elemDamage <= 0 || Float.isNaN(elemDamage)) {
                tb.effectiveResist(type, 0f);
                tb.elemAfterResist(type, 0f);
                continue;
            }

            double effectiveResist = ElementalCalculator.getEffectiveResistance(rawResist, penetration);
            float finalDamage = (float) (elemDamage * (1.0 - effectiveResist / 100.0));

            dist.setElemental(type, finalDamage);
            resistanceReductions.put(type, (float) effectiveResist);

            tb.effectiveResist(type, (float) effectiveResist);
            tb.elemAfterResist(type, finalDamage);
        }

        return armorReduction;
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
        @Nullable ElementType damageElement
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
        float armorReduction = applyDefenses(dist, defenderStats, defenderElemental, null, null, resistanceReductions);

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
        @Nonnull AttackType attackType
    ) {
        // Skip flat damage for mobs - their damage is already calculated via weighted formula
        // and uses vanilla attack damage as base. Adding flat damage would cause stacking.
        if (stats.isMobStats()) {
            LOGGER.at(Level.FINE).log("Skipping flat damage for mob attacker (already in base damage)");
            return;
        }

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

        // Spell damage would be added here if we have spell attacks
        // Currently handled as elemental flat damage
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
        totalPercent += switch (attackType) {
            case MELEE -> stats.getMeleeDamagePercent();
            case PROJECTILE -> stats.getProjectileDamagePercent();
            case AREA, UNKNOWN -> 0f;
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
     * @return Armor reduction percentage for death recap
     */
    private float applyDefenses(
        @Nonnull DamageDistribution dist,
        @Nullable ComputedStats defenderStats,
        @Nullable ElementalStats defenderElemental,
        @Nullable ComputedStats attackerStats,
        @Nullable ElementalStats attackerElemental,
        @Nonnull EnumMap<ElementType, Float> resistanceReductions
    ) {
        float armorReduction = 0f;

        if (defenderStats == null) {
            return armorReduction;
        }

        // Apply armor to physical damage
        float physDamage = dist.getPhysical();
        if (physDamage > 0) {
            float armorPen = attackerStats != null ? attackerStats.getArmorPenetration() : 0f;
            CombatCalculator.ArmorResult armorResult = combatCalculator.calculateDefenderReduction(
                physDamage, defenderStats, armorPen
            );
            dist.setPhysical(armorResult.finalDamage());
            armorReduction = armorResult.reductionPercent();

            // Apply physical resistance after armor
            float physResist = defenderStats.getPhysicalResistance();
            if (physResist > 0 && dist.getPhysical() > 0) {
                float cappedResist = Math.min(physResist, MAX_RESISTANCE_CAP);
                dist.applyPhysicalMultiplier(1f - cappedResist / 100f);
            }
        }

        // Apply elemental resistances
        ElementalStats defElem = defenderElemental != null ? defenderElemental : new ElementalStats();
        for (ElementType type : ElementType.values()) {
            float elemDamage = dist.getElemental(type);
            if (elemDamage <= 0 || Float.isNaN(elemDamage)) continue;

            double resistance = defElem.getResistance(type);
            double penetration = attackerElemental != null ? attackerElemental.getPenetration(type) : 0;

            // Use ElementalCalculator for proper resistance application
            double effectiveResist = ElementalCalculator.getEffectiveResistance(resistance, penetration);
            float finalDamage = (float) (elemDamage * (1.0 - effectiveResist / 100.0));

            dist.setElemental(type, finalDamage);
            resistanceReductions.put(type, (float) effectiveResist);
        }

        // True damage is not affected by defenses (already in dist.trueDamage)

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
        boolean forceCrit
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
            // STEP 1: Flat physical damage
            applyFlatDamage(dist, attackerStats, attackType);

            // STEP 2: Flat elemental damage (added early so it benefits from modifiers + crit)
            if (attackerElemental != null) {
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
            attackerStats, attackerElemental, resistanceReductions);

        // STEP 10: True damage added AFTER defenses (bypasses them)
        if (attackerStats != null) {
            float trueDmg = attackerStats.getTrueDamage();
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
