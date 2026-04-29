package io.github.larsonix.trailoforbis.combat;

import io.github.larsonix.trailoforbis.attributes.breakdown.StatBreakdownResult;
import io.github.larsonix.trailoforbis.combat.ailments.CombatAilmentApplicator;
import io.github.larsonix.trailoforbis.combat.avoidance.AvoidanceProcessor;
import io.github.larsonix.trailoforbis.combat.modifiers.ConditionalResult;
import io.github.larsonix.trailoforbis.elemental.ElementType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.EnumMap;

/**
 * Complete trace of all intermediate values in a damage calculation.
 *
 * <p>Only created when {@code traceEnabled=true} (combat detail mode).
 * Zero allocation overhead for normal combat. Accompanies a {@link DamageBreakdown}
 * with full formula-level detail for every calculation step.
 *
 * <p>Flow: {@link RPGDamageCalculator#calculateTraced} populates steps 1-10,
 * {@link RPGDamageSystem} adds post-calc fields (parry, shield, leech, thorns,
 * stat attribution).
 */
public final class DamageTrace {

    // === Inputs ===
    private final float weaponBaseDamage;
    private final float attackTypeMultiplier;
    private final AttackType attackType;
    private final boolean isMobStats;

    // === Step 1: Flat Physical ===
    private final float physBeforeFlat;
    private final float flatPhysFromStats;
    private final float flatMelee;
    private final float physAfterFlat;

    // === Step 2: Flat Elemental ===
    private final EnumMap<ElementType, Float> flatElementalAdded;

    // === Step 3: Conversion ===
    private final float physBeforeConversion;
    private final EnumMap<ElementType, Float> conversionPercents;
    private final float scaleFactor;
    private final float physAfterConversion;
    private final EnumMap<ElementType, Float> convertedAmounts;

    // === Step 4: % Increased Physical ===
    private final float physDmgPercent;
    private final float attackTypePercent; // melee% or proj%
    private final float globalDmgPercent;
    private final float totalIncreasedPercent;
    private final float physAfterIncreased;

    // === Step 5: Elemental Modifiers ===
    private final EnumMap<ElementType, Float> elemPercentInc;
    private final EnumMap<ElementType, Float> elemPercentMore;
    private final EnumMap<ElementType, Float> elemAfterMods;

    // === Step 6: % More Multipliers ===
    private final float allDamagePercent;
    private final float damageMultiplier;
    private final float physAfterMore;
    private final EnumMap<ElementType, Float> elemAfterMore;

    // === Step 7: Conditionals ===
    private final ConditionalResult conditionals;
    private final float physAfterConditionals;
    private final EnumMap<ElementType, Float> elemAfterConditionals;

    // === Step 8: Critical Strike ===
    private final float critChance;
    private final float critMultiplierRaw;
    private final boolean wasCritical;
    private final float critMultiplierApplied;
    private final float physAfterCrit;
    private final EnumMap<ElementType, Float> elemAfterCrit;

    // === Step 9: Defenses ===
    private final float defenderArmor;
    private final float armorPercent;
    private final float armorPenPercent;
    private final float effectiveArmor;
    private final float armorReductionPercent;
    private final float physBeforeArmor;
    private final float physAfterArmor;
    private final float physResistPercent;
    private final float physAfterResist;
    private final EnumMap<ElementType, Float> defenderRawResist;
    private final EnumMap<ElementType, Float> attackerPenetration;
    private final EnumMap<ElementType, Float> effectiveResist;
    private final EnumMap<ElementType, Float> elemBeforeResist;
    private final EnumMap<ElementType, Float> elemAfterResist;

    // === Step 10: True Damage ===
    private final float trueDamage;

    // === Post-calc (set by RPGDamageSystem) ===
    private float shieldAbsorbed;
    private boolean wasParried;
    private float parryReductionMult;
    private float damageAfterParry;

    // === Leech/Thorns (set by RPGDamageSystem Phase 7) ===
    private float lifeLeechPercent;
    private float lifeLeechAmount;
    private float manaLeechPercent;
    private float manaLeechAmount;
    private float lifeStealPercent;
    private float lifeStealAmount;
    private float manaStealPercent;
    private float manaStealAmount;
    private float thornsDamageFlat;
    private float thornsDamagePercent;
    private float reflectDamagePercent;
    private float totalThornsReturned;

    // === Stat Source Attribution (nullable, only for players) ===
    @Nullable private StatBreakdownResult attackerBreakdown;
    @Nullable private StatBreakdownResult defenderBreakdown;

    // === Context (set by RPGDamageSystem Phase 2) ===
    @Nullable private String weaponItemId;
    private int attackerLevel;
    private int defenderLevel;
    private float defenderMaxHealth;
    private float attackSpeedPercent;

    // === Avoidance context (set by RPGDamageSystem Phase 3, always populated even on hits) ===
    @Nullable private AvoidanceProcessor.AvoidanceDetail avoidanceStats;

    // === Ailment results (set by RPGDamageSystem Phase 6) ===
    @Nullable private CombatAilmentApplicator.AilmentSummary ailmentSummary;

    // === Defense context (set by RPGDamageSystem Phase 5) ===
    private float defenderCritNullifyChance;
    private boolean critWasNullified;
    private float parryChance;
    private float healthRecoveryPercent;
    private float damageTakenModifier;

    // === Energy shield detail (set by RPGDamageSystem from ModificationResult) ===
    private float energyShieldCapacity;
    private float energyShieldBefore;

    // === Mind over Matter (set by RPGDamageSystem from ModificationResult) ===
    private float manaBufferPercent;
    private float manaAbsorbed;

    // === Shock amplification (set by RPGDamageSystem from ModificationResult) ===
    private float shockBonusPercent;
    private float damageBeforeShock;

    // === Active blocking reduction (set by RPGDamageSystem Phase 5.5) ===
    private boolean wasActiveBlocking;
    private boolean isShieldBlock;
    private float blockReductionPercent;   // total reduction applied (base + gear, capped)
    private float damageBeforeBlock;       // damage before blocking reduction
    private float damageAfterBlock;        // damage after blocking reduction

    // === Unarmed penalty (set by RPGDamageSystem Phase 5) ===
    private float unarmedMultiplier;      // 0 means "not unarmed" / not applied
    private float damageBeforeUnarmed;

    // === Attack type derivation (set by RPGDamageSystem Phase 4.5) ===
    private float vanillaDamage;
    private float referenceDamage;
    private float vanillaMinDamage;
    private float vanillaMaxDamage;
    @Nullable private String attackName;

    // === The final DamageBreakdown companion ===
    private final DamageBreakdown breakdown;

    private DamageTrace(Builder b) {
        this.weaponBaseDamage = b.weaponBaseDamage;
        this.attackTypeMultiplier = b.attackTypeMultiplier;
        this.attackType = b.attackType;
        this.isMobStats = b.isMobStats;
        this.physBeforeFlat = b.physBeforeFlat;
        this.flatPhysFromStats = b.flatPhysFromStats;
        this.flatMelee = b.flatMelee;
        this.physAfterFlat = b.physAfterFlat;
        this.flatElementalAdded = b.flatElementalAdded;
        this.physBeforeConversion = b.physBeforeConversion;
        this.conversionPercents = b.conversionPercents;
        this.scaleFactor = b.scaleFactor;
        this.physAfterConversion = b.physAfterConversion;
        this.convertedAmounts = b.convertedAmounts;
        this.physDmgPercent = b.physDmgPercent;
        this.attackTypePercent = b.attackTypePercent;
        this.globalDmgPercent = b.globalDmgPercent;
        this.totalIncreasedPercent = b.totalIncreasedPercent;
        this.physAfterIncreased = b.physAfterIncreased;
        this.elemPercentInc = b.elemPercentInc;
        this.elemPercentMore = b.elemPercentMore;
        this.elemAfterMods = b.elemAfterMods;
        this.allDamagePercent = b.allDamagePercent;
        this.damageMultiplier = b.damageMultiplier;
        this.physAfterMore = b.physAfterMore;
        this.elemAfterMore = b.elemAfterMore;
        this.conditionals = b.conditionals;
        this.physAfterConditionals = b.physAfterConditionals;
        this.elemAfterConditionals = b.elemAfterConditionals;
        this.critChance = b.critChance;
        this.critMultiplierRaw = b.critMultiplierRaw;
        this.wasCritical = b.wasCritical;
        this.critMultiplierApplied = b.critMultiplierApplied;
        this.physAfterCrit = b.physAfterCrit;
        this.elemAfterCrit = b.elemAfterCrit;
        this.defenderArmor = b.defenderArmor;
        this.armorPercent = b.armorPercent;
        this.armorPenPercent = b.armorPenPercent;
        this.effectiveArmor = b.effectiveArmor;
        this.armorReductionPercent = b.armorReductionPercent;
        this.physBeforeArmor = b.physBeforeArmor;
        this.physAfterArmor = b.physAfterArmor;
        this.physResistPercent = b.physResistPercent;
        this.physAfterResist = b.physAfterResist;
        this.defenderRawResist = b.defenderRawResist;
        this.attackerPenetration = b.attackerPenetration;
        this.effectiveResist = b.effectiveResist;
        this.elemBeforeResist = b.elemBeforeResist;
        this.elemAfterResist = b.elemAfterResist;
        this.trueDamage = b.trueDamage;
        this.breakdown = b.breakdown;
    }

    // ==================== Getters ====================

    public float weaponBaseDamage() { return weaponBaseDamage; }
    public float attackTypeMultiplier() { return attackTypeMultiplier; }
    public AttackType attackType() { return attackType; }
    public boolean isMobStats() { return isMobStats; }

    public float physBeforeFlat() { return physBeforeFlat; }
    public float flatPhysFromStats() { return flatPhysFromStats; }
    public float flatMelee() { return flatMelee; }
    public float physAfterFlat() { return physAfterFlat; }

    @Nonnull public EnumMap<ElementType, Float> flatElementalAdded() { return flatElementalAdded; }

    public float physBeforeConversion() { return physBeforeConversion; }
    @Nonnull public EnumMap<ElementType, Float> conversionPercents() { return conversionPercents; }
    public float scaleFactor() { return scaleFactor; }
    public float physAfterConversion() { return physAfterConversion; }
    @Nonnull public EnumMap<ElementType, Float> convertedAmounts() { return convertedAmounts; }

    public float physDmgPercent() { return physDmgPercent; }
    public float attackTypePercent() { return attackTypePercent; }
    public float globalDmgPercent() { return globalDmgPercent; }
    public float totalIncreasedPercent() { return totalIncreasedPercent; }
    public float physAfterIncreased() { return physAfterIncreased; }

    @Nonnull public EnumMap<ElementType, Float> elemPercentInc() { return elemPercentInc; }
    @Nonnull public EnumMap<ElementType, Float> elemPercentMore() { return elemPercentMore; }
    @Nonnull public EnumMap<ElementType, Float> elemAfterMods() { return elemAfterMods; }

    public float allDamagePercent() { return allDamagePercent; }
    public float damageMultiplier() { return damageMultiplier; }
    public float physAfterMore() { return physAfterMore; }
    @Nonnull public EnumMap<ElementType, Float> elemAfterMore() { return elemAfterMore; }

    @Nonnull public ConditionalResult conditionals() { return conditionals; }
    public float physAfterConditionals() { return physAfterConditionals; }
    @Nonnull public EnumMap<ElementType, Float> elemAfterConditionals() { return elemAfterConditionals; }

    public float critChance() { return critChance; }
    public float critMultiplierRaw() { return critMultiplierRaw; }
    public boolean wasCritical() { return wasCritical; }
    public float critMultiplierApplied() { return critMultiplierApplied; }
    public float physAfterCrit() { return physAfterCrit; }
    @Nonnull public EnumMap<ElementType, Float> elemAfterCrit() { return elemAfterCrit; }

    public float defenderArmor() { return defenderArmor; }
    public float armorPercent() { return armorPercent; }
    public float armorPenPercent() { return armorPenPercent; }
    public float effectiveArmor() { return effectiveArmor; }
    public float armorReductionPercent() { return armorReductionPercent; }
    public float physBeforeArmor() { return physBeforeArmor; }
    public float physAfterArmor() { return physAfterArmor; }
    public float physResistPercent() { return physResistPercent; }
    public float physAfterResist() { return physAfterResist; }
    @Nonnull public EnumMap<ElementType, Float> defenderRawResist() { return defenderRawResist; }
    @Nonnull public EnumMap<ElementType, Float> attackerPenetration() { return attackerPenetration; }
    @Nonnull public EnumMap<ElementType, Float> effectiveResist() { return effectiveResist; }
    @Nonnull public EnumMap<ElementType, Float> elemBeforeResist() { return elemBeforeResist; }
    @Nonnull public EnumMap<ElementType, Float> elemAfterResist() { return elemAfterResist; }

    public float trueDamage() { return trueDamage; }

    public float shieldAbsorbed() { return shieldAbsorbed; }
    public boolean wasParried() { return wasParried; }
    public float parryReductionMult() { return parryReductionMult; }
    public float damageAfterParry() { return damageAfterParry; }

    public float lifeLeechPercent() { return lifeLeechPercent; }
    public float lifeLeechAmount() { return lifeLeechAmount; }
    public float manaLeechPercent() { return manaLeechPercent; }
    public float manaLeechAmount() { return manaLeechAmount; }
    public float lifeStealPercent() { return lifeStealPercent; }
    public float lifeStealAmount() { return lifeStealAmount; }
    public float manaStealPercent() { return manaStealPercent; }
    public float manaStealAmount() { return manaStealAmount; }
    public float thornsDamageFlat() { return thornsDamageFlat; }
    public float thornsDamagePercent() { return thornsDamagePercent; }
    public float reflectDamagePercent() { return reflectDamagePercent; }
    public float totalThornsReturned() { return totalThornsReturned; }

    @Nullable public StatBreakdownResult attackerBreakdown() { return attackerBreakdown; }
    @Nullable public StatBreakdownResult defenderBreakdown() { return defenderBreakdown; }

    // --- Context getters ---
    @Nullable public String weaponItemId() { return weaponItemId; }
    public int attackerLevel() { return attackerLevel; }
    public int defenderLevel() { return defenderLevel; }
    public float defenderMaxHealth() { return defenderMaxHealth; }
    public float attackSpeedPercent() { return attackSpeedPercent; }

    // --- Avoidance context getters ---
    @Nullable public AvoidanceProcessor.AvoidanceDetail avoidanceStats() { return avoidanceStats; }

    // --- Ailment results getter ---
    @Nullable public CombatAilmentApplicator.AilmentSummary ailmentSummary() { return ailmentSummary; }

    // --- Defense context getters ---
    public float defenderCritNullifyChance() { return defenderCritNullifyChance; }
    public boolean critWasNullified() { return critWasNullified; }
    public float parryChance() { return parryChance; }
    public float healthRecoveryPercent() { return healthRecoveryPercent; }
    public float damageTakenModifier() { return damageTakenModifier; }

    // --- Energy shield detail getters ---
    public float energyShieldCapacity() { return energyShieldCapacity; }
    public float energyShieldBefore() { return energyShieldBefore; }

    // --- Mind over Matter getters ---
    public float manaBufferPercent() { return manaBufferPercent; }
    public float manaAbsorbed() { return manaAbsorbed; }

    // --- Shock amplification getters ---
    public float shockBonusPercent() { return shockBonusPercent; }
    public float damageBeforeShock() { return damageBeforeShock; }

    // --- Active blocking getters ---
    public boolean wasActiveBlocking() { return wasActiveBlocking; }
    public boolean isShieldBlock() { return isShieldBlock; }
    public float blockReductionPercent() { return blockReductionPercent; }
    public float damageBeforeBlock() { return damageBeforeBlock; }
    public float damageAfterBlock() { return damageAfterBlock; }

    /**
     * Returns the actual final damage after ALL post-calc modifications including blocking.
     *
     * <p>{@code breakdown.totalDamage()} includes parry, energy shield, MoM, unarmed
     * but NOT active blocking (which happens after the breakdown is finalized).
     * This method returns the true final value.
     */
    public float effectiveFinalDamage() {
        return wasActiveBlocking ? damageAfterBlock : breakdown.totalDamage();
    }

    // --- Unarmed penalty getters ---
    public float unarmedMultiplier() { return unarmedMultiplier; }
    public float damageBeforeUnarmed() { return damageBeforeUnarmed; }

    // --- Attack type derivation getters ---
    public float vanillaDamage() { return vanillaDamage; }
    public float referenceDamage() { return referenceDamage; }
    public float vanillaMinDamage() { return vanillaMinDamage; }
    public float vanillaMaxDamage() { return vanillaMaxDamage; }
    @Nullable public String attackName() { return attackName; }

    @Nonnull public DamageBreakdown breakdown() { return breakdown; }

    // ==================== Post-calc Setters (used by RPGDamageSystem) ====================

    public void setShieldAbsorbed(float amount) { this.shieldAbsorbed = amount; }
    public void setParried(boolean parried, float reductionMult, float damageAfter) {
        this.wasParried = parried;
        this.parryReductionMult = reductionMult;
        this.damageAfterParry = damageAfter;
    }

    public void setLifeLeech(float percent, float amount) {
        this.lifeLeechPercent = percent;
        this.lifeLeechAmount = amount;
    }
    public void setManaLeech(float percent, float amount) {
        this.manaLeechPercent = percent;
        this.manaLeechAmount = amount;
    }
    public void setLifeSteal(float percent, float amount) {
        this.lifeStealPercent = percent;
        this.lifeStealAmount = amount;
    }
    public void setManaSteal(float percent, float amount) {
        this.manaStealPercent = percent;
        this.manaStealAmount = amount;
    }
    public void setThorns(float flat, float damagePercent, float reflectPercent, float totalReturned) {
        this.thornsDamageFlat = flat;
        this.thornsDamagePercent = damagePercent;
        this.reflectDamagePercent = reflectPercent;
        this.totalThornsReturned = totalReturned;
    }

    public void setAttackerBreakdown(@Nullable StatBreakdownResult breakdown) {
        this.attackerBreakdown = breakdown;
    }
    public void setDefenderBreakdown(@Nullable StatBreakdownResult breakdown) {
        this.defenderBreakdown = breakdown;
    }

    // --- Context setters ---
    public void setWeaponItemId(@Nullable String id) { this.weaponItemId = id; }
    public void setAttackerLevel(int level) { this.attackerLevel = level; }
    public void setDefenderLevel(int level) { this.defenderLevel = level; }
    public void setDefenderMaxHealth(float max) { this.defenderMaxHealth = max; }
    public void setAttackSpeedPercent(float pct) { this.attackSpeedPercent = pct; }

    // --- Avoidance context setter ---
    public void setAvoidanceStats(@Nullable AvoidanceProcessor.AvoidanceDetail stats) {
        this.avoidanceStats = stats;
    }

    // --- Ailment results setter ---
    public void setAilmentSummary(@Nullable CombatAilmentApplicator.AilmentSummary summary) {
        this.ailmentSummary = summary;
    }

    // --- Defense context setters ---
    public void setCritNullify(float chance, boolean wasNullified) {
        this.defenderCritNullifyChance = chance;
        this.critWasNullified = wasNullified;
    }
    public void setParryChance(float chance) { this.parryChance = chance; }
    public void setHealthRecoveryPercent(float pct) { this.healthRecoveryPercent = pct; }
    public void setDamageTakenModifier(float mod) { this.damageTakenModifier = mod; }

    // --- Energy shield detail setter ---
    public void setEnergyShieldDetail(float capacity, float before) {
        this.energyShieldCapacity = capacity;
        this.energyShieldBefore = before;
    }

    // --- Mind over Matter setter ---
    public void setManaBuffer(float bufferPercent, float absorbed) {
        this.manaBufferPercent = bufferPercent;
        this.manaAbsorbed = absorbed;
    }

    // --- Shock amplification setter ---
    public void setShockAmplification(float bonusPercent, float damBefore) {
        this.shockBonusPercent = bonusPercent;
        this.damageBeforeShock = damBefore;
    }

    // --- Active blocking setter ---
    public void setActiveBlocking(boolean isShield, float reductionPercent, float damageBefore, float damageAfter) {
        this.wasActiveBlocking = true;
        this.isShieldBlock = isShield;
        this.blockReductionPercent = reductionPercent;
        this.damageBeforeBlock = damageBefore;
        this.damageAfterBlock = damageAfter;
    }

    // --- Unarmed penalty setter ---
    public void setUnarmedPenalty(float multiplier, float damageBefore) {
        this.unarmedMultiplier = multiplier;
        this.damageBeforeUnarmed = damageBefore;
    }

    // --- Attack type derivation setter ---
    public void setAttackTypeDerivation(float vanillaDmg, float refDmg, float minDmg, float maxDmg,
                                        @Nullable String atkName) {
        this.vanillaDamage = vanillaDmg;
        this.referenceDamage = refDmg;
        this.vanillaMinDamage = minDmg;
        this.vanillaMaxDamage = maxDmg;
        this.attackName = atkName;
    }

    // ==================== Builder ====================

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        float weaponBaseDamage;
        float attackTypeMultiplier = 1.0f;
        AttackType attackType = AttackType.UNKNOWN;
        boolean isMobStats;

        float physBeforeFlat;
        float flatPhysFromStats;
        float flatMelee;
        float physAfterFlat;

        final EnumMap<ElementType, Float> flatElementalAdded = newElemMap();

        float physBeforeConversion;
        final EnumMap<ElementType, Float> conversionPercents = newElemMap();
        float scaleFactor = 1.0f;
        float physAfterConversion;
        final EnumMap<ElementType, Float> convertedAmounts = newElemMap();

        float physDmgPercent;
        float attackTypePercent;
        float globalDmgPercent;
        float totalIncreasedPercent;
        float physAfterIncreased;

        final EnumMap<ElementType, Float> elemPercentInc = newElemMap();
        final EnumMap<ElementType, Float> elemPercentMore = newElemMap();
        final EnumMap<ElementType, Float> elemAfterMods = newElemMap();

        float allDamagePercent;
        float damageMultiplier;
        float physAfterMore;
        final EnumMap<ElementType, Float> elemAfterMore = newElemMap();

        ConditionalResult conditionals = ConditionalResult.NONE;
        float physAfterConditionals;
        final EnumMap<ElementType, Float> elemAfterConditionals = newElemMap();

        float critChance;
        float critMultiplierRaw;
        boolean wasCritical;
        float critMultiplierApplied = 1.0f;
        float physAfterCrit;
        final EnumMap<ElementType, Float> elemAfterCrit = newElemMap();

        float defenderArmor;
        float armorPercent;
        float armorPenPercent;
        float effectiveArmor;
        float armorReductionPercent;
        float physBeforeArmor;
        float physAfterArmor;
        float physResistPercent;
        float physAfterResist;
        final EnumMap<ElementType, Float> defenderRawResist = newElemMap();
        final EnumMap<ElementType, Float> attackerPenetration = newElemMap();
        final EnumMap<ElementType, Float> effectiveResist = newElemMap();
        final EnumMap<ElementType, Float> elemBeforeResist = newElemMap();
        final EnumMap<ElementType, Float> elemAfterResist = newElemMap();

        float trueDamage;

        DamageBreakdown breakdown;

        Builder() {}

        // --- Inputs ---
        public Builder weaponBaseDamage(float v) { weaponBaseDamage = v; return this; }
        public Builder attackTypeMultiplier(float v) { attackTypeMultiplier = v; return this; }
        public Builder attackType(AttackType v) { attackType = v; return this; }
        public Builder isMobStats(boolean v) { isMobStats = v; return this; }

        // --- Step 1 ---
        public Builder physBeforeFlat(float v) { physBeforeFlat = v; return this; }
        public Builder flatPhysFromStats(float v) { flatPhysFromStats = v; return this; }
        public Builder flatMelee(float v) { flatMelee = v; return this; }
        public Builder physAfterFlat(float v) { physAfterFlat = v; return this; }

        // --- Step 2 ---
        public Builder flatElemental(ElementType t, float v) { flatElementalAdded.put(t, v); return this; }

        // --- Step 3 ---
        public Builder physBeforeConversion(float v) { physBeforeConversion = v; return this; }
        public Builder conversionPercent(ElementType t, float v) { conversionPercents.put(t, v); return this; }
        public Builder scaleFactor(float v) { scaleFactor = v; return this; }
        public Builder physAfterConversion(float v) { physAfterConversion = v; return this; }
        public Builder convertedAmount(ElementType t, float v) { convertedAmounts.put(t, v); return this; }

        // --- Step 4 ---
        public Builder physDmgPercent(float v) { physDmgPercent = v; return this; }
        public Builder attackTypePercent(float v) { attackTypePercent = v; return this; }
        public Builder globalDmgPercent(float v) { globalDmgPercent = v; return this; }
        public Builder totalIncreasedPercent(float v) { totalIncreasedPercent = v; return this; }
        public Builder physAfterIncreased(float v) { physAfterIncreased = v; return this; }

        // --- Step 5 ---
        public Builder elemPercentInc(ElementType t, float v) { elemPercentInc.put(t, v); return this; }
        public Builder elemPercentMore(ElementType t, float v) { elemPercentMore.put(t, v); return this; }
        public Builder elemAfterMod(ElementType t, float v) { elemAfterMods.put(t, v); return this; }

        // --- Step 6 ---
        public Builder allDamagePercent(float v) { allDamagePercent = v; return this; }
        public Builder damageMultiplier(float v) { damageMultiplier = v; return this; }
        public Builder physAfterMore(float v) { physAfterMore = v; return this; }
        public Builder elemAfterMore(ElementType t, float v) { elemAfterMore.put(t, v); return this; }

        // --- Step 7 ---
        public Builder conditionals(ConditionalResult v) { conditionals = v; return this; }
        public Builder physAfterConditionals(float v) { physAfterConditionals = v; return this; }
        public Builder elemAfterConditional(ElementType t, float v) { elemAfterConditionals.put(t, v); return this; }

        // --- Step 8 ---
        public Builder critChance(float v) { critChance = v; return this; }
        public Builder critMultiplierRaw(float v) { critMultiplierRaw = v; return this; }
        public Builder wasCritical(boolean v) { wasCritical = v; return this; }
        public Builder critMultiplierApplied(float v) { critMultiplierApplied = v; return this; }
        public Builder physAfterCrit(float v) { physAfterCrit = v; return this; }
        public Builder elemAfterCrit(ElementType t, float v) { elemAfterCrit.put(t, v); return this; }

        // --- Step 9 ---
        public Builder defenderArmor(float v) { defenderArmor = v; return this; }
        public Builder armorPercent(float v) { armorPercent = v; return this; }
        public Builder armorPenPercent(float v) { armorPenPercent = v; return this; }
        public Builder effectiveArmor(float v) { effectiveArmor = v; return this; }
        public Builder armorReductionPercent(float v) { armorReductionPercent = v; return this; }
        public Builder physBeforeArmor(float v) { physBeforeArmor = v; return this; }
        public Builder physAfterArmor(float v) { physAfterArmor = v; return this; }
        public Builder physResistPercent(float v) { physResistPercent = v; return this; }
        public Builder physAfterResist(float v) { physAfterResist = v; return this; }
        public Builder defenderRawResist(ElementType t, float v) { defenderRawResist.put(t, v); return this; }
        public Builder attackerPen(ElementType t, float v) { attackerPenetration.put(t, v); return this; }
        public Builder effectiveResist(ElementType t, float v) { effectiveResist.put(t, v); return this; }
        public Builder elemBeforeResist(ElementType t, float v) { elemBeforeResist.put(t, v); return this; }
        public Builder elemAfterResist(ElementType t, float v) { elemAfterResist.put(t, v); return this; }

        // --- Step 10 ---
        public Builder trueDamage(float v) { trueDamage = v; return this; }

        // --- Final ---
        public Builder breakdown(DamageBreakdown v) { breakdown = v; return this; }

        public DamageTrace build() {
            return new DamageTrace(this);
        }

        private static EnumMap<ElementType, Float> newElemMap() {
            EnumMap<ElementType, Float> map = new EnumMap<>(ElementType.class);
            for (ElementType t : ElementType.values()) {
                map.put(t, 0f);
            }
            return map;
        }
    }
}
