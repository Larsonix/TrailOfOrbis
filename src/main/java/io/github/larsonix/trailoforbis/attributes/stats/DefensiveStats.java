package io.github.larsonix.trailoforbis.attributes.stats;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Container for defensive combat stats (armor, evasion, blocking, etc.).
 *
 * <p>This class holds all stats that contribute to damage mitigation.
 * Used as part of the composed {@code ComputedStats} structure.
 *
 * <p>Thread-safe: This class is mutable but designed for single-threaded access
 * during entity processing. For concurrent access, external synchronization is needed.
 */
public final class DefensiveStats {

    // ==================== Damage Reduction ====================
    private float armor;
    private float armorPercent;
    private float evasion;
    private float energyShield;

    // ==================== Passive Defense ====================
    /**
     * Passive block chance - % chance to partially block damage without actively blocking.
     * This is PASSIVE (random proc), distinct from ACTIVE blocking (holding right-click with shield).
     */
    private float passiveBlockChance;
    private float parryChance;

    // ==================== Special Resistances ====================
    private float knockbackResistance;
    private float fallDamageReduction;

    // ==================== Critical Defense ====================
    /**
     * Crit nullify chance - % chance to turn an enemy's critical hit into a normal hit.
     * When an enemy scores a critical hit, this stat gives a chance to "nullify" the crit.
     * At 100%, all incoming crits are guaranteed to be nullified.
     */
    private float critNullifyChance;

    /**
     * % reduced critical damage taken.
     * When an enemy lands a critical hit, this reduces the bonus crit damage.
     * At 50% reduction, a 200% crit multiplier becomes effectively 150%.
     * Range: 0-75% (capped to prevent full immunity)
     */
    private float criticalReduction;

    // ==================== Active Block Modifiers ====================
    /**
     * % chance for active block to succeed when holding a shield.
     * At 100%, all blocked attacks will be successfully mitigated.
     * Range: 0-100%
     */
    private float blockChance;

    /**
     * % extra damage reduction when actively blocking (holding shield).
     * Stacks with base shield block reduction. Applied AFTER vanilla blocking.
     * Range: 0-100%
     */
    private float blockDamageReduction;

    /**
     * % reduced stamina cost when blocking damage.
     * Uses native STAMINA_DRAIN_MULTIPLIER. At 50%, blocking costs half stamina.
     * Range: 0-75% (capped to prevent free blocking)
     */
    private float staminaDrainReduction;

    // ==================== Physical Resistance ====================
    /** Physical damage resistance percentage (0-75 cap). Separate layer from armor. */
    private float physicalResistance;

    // ==================== Dodge ====================
    /** Dodge chance percentage (0-100). Separate from evasion — a flat avoidance roll. */
    private float dodgeChance;

    // ==================== Ailment Thresholds ====================
    /** Burn ailment threshold — accumulation required to trigger burn. */
    private float burnThreshold;
    /** Freeze ailment threshold — accumulation required to trigger freeze. */
    private float freezeThreshold;
    /** Shock ailment threshold — accumulation required to trigger shock. */
    private float shockThreshold;

    // ==================== Block Modifiers ====================
    /** Percentage of blocked damage converted to healing. */
    private float blockHealPercent;
    /** Block recovery rate percentage modifier. */
    private float blockRecoveryPercent;

    // ==================== Shield Modifiers ====================
    /** Shield effectiveness percentage modifier. */
    private float shieldEffectivenessPercent;

    // ==================== Recovery Modifiers ====================
    /** Health recovery percentage modifier (affects all healing). */
    private float healthRecoveryPercent;
    /** Damage taken percentage modifier (negative = less damage taken). */
    private float damageTakenPercent;
    /** Damage taken when hit (bonus/penalty per hit taken). */
    private float damageWhenHitPercent;

    // ==================== Thorns / Reflect ====================
    /**
     * Flat thorns damage — deal this much damage back to attacker when hit.
     * Applied before thornsDamagePercent bonus.
     */
    private float thornsDamage;
    /**
     * Percentage bonus to thorns damage (e.g., 50 = +50% thorns damage).
     * Formula: finalThorns = thornsDamage × (1 + thornsDamagePercent/100)
     */
    private float thornsDamagePercent;
    /**
     * Reflect percentage of damage taken back to attacker.
     * Formula: reflectedDamage = damageTaken × (reflectDamagePercent/100)
     */
    private float reflectDamagePercent;

    // ==================== Octant Keystone Stats ====================
    /** % of DoT damage you inflict that restores your energy shield. */
    private float shieldRegenOnDot;
    /** Temporary element resistance % granted when hit by that element's ailment. */
    private float immunityOnAilment;
    /** % of Evasion converted to bonus Armor. */
    private float evasionToArmor;

    /**
     * Creates a new DefensiveStats with all values initialized to 0.
     */
    public DefensiveStats() {
        // All fields default to 0
    }

    /**
     * Private constructor for builder.
     */
    private DefensiveStats(Builder builder) {
        this.armor = builder.armor;
        this.armorPercent = builder.armorPercent;
        this.evasion = builder.evasion;
        this.energyShield = builder.energyShield;
        this.passiveBlockChance = builder.passiveBlockChance;
        this.parryChance = builder.parryChance;
        this.knockbackResistance = builder.knockbackResistance;
        this.fallDamageReduction = builder.fallDamageReduction;
        this.critNullifyChance = builder.critNullifyChance;
        this.criticalReduction = builder.criticalReduction;
        this.blockChance = builder.blockChance;
        this.blockDamageReduction = builder.blockDamageReduction;
        this.staminaDrainReduction = builder.staminaDrainReduction;
        this.physicalResistance = builder.physicalResistance;
        this.dodgeChance = builder.dodgeChance;
        this.burnThreshold = builder.burnThreshold;
        this.freezeThreshold = builder.freezeThreshold;
        this.shockThreshold = builder.shockThreshold;
        this.blockHealPercent = builder.blockHealPercent;
        this.blockRecoveryPercent = builder.blockRecoveryPercent;
        this.shieldEffectivenessPercent = builder.shieldEffectivenessPercent;
        this.healthRecoveryPercent = builder.healthRecoveryPercent;
        this.damageTakenPercent = builder.damageTakenPercent;
        this.damageWhenHitPercent = builder.damageWhenHitPercent;
        this.thornsDamage = builder.thornsDamage;
        this.thornsDamagePercent = builder.thornsDamagePercent;
        this.reflectDamagePercent = builder.reflectDamagePercent;
        this.shieldRegenOnDot = builder.shieldRegenOnDot;
        this.immunityOnAilment = builder.immunityOnAilment;
        this.evasionToArmor = builder.evasionToArmor;
    }

    // ==================== Getters ====================

    public float getArmor() {
        return armor;
    }

    public float getArmorPercent() {
        return armorPercent;
    }

    public float getEvasion() {
        return evasion;
    }

    public float getEnergyShield() {
        return energyShield;
    }

    public float getPassiveBlockChance() {
        return passiveBlockChance;
    }

    public float getParryChance() {
        return parryChance;
    }

    public float getKnockbackResistance() {
        return knockbackResistance;
    }

    public float getFallDamageReduction() {
        return fallDamageReduction;
    }

    public float getCritNullifyChance() {
        return critNullifyChance;
    }

    public float getCriticalReduction() {
        return criticalReduction;
    }

    public float getBlockChance() {
        return blockChance;
    }

    public float getBlockDamageReduction() {
        return blockDamageReduction;
    }

    public float getStaminaDrainReduction() {
        return staminaDrainReduction;
    }

    public float getPhysicalResistance() {
        return physicalResistance;
    }

    public float getDodgeChance() {
        return dodgeChance;
    }

    public float getBurnThreshold() {
        return burnThreshold;
    }

    public float getFreezeThreshold() {
        return freezeThreshold;
    }

    public float getShockThreshold() {
        return shockThreshold;
    }

    public float getBlockHealPercent() {
        return blockHealPercent;
    }

    public float getBlockRecoveryPercent() {
        return blockRecoveryPercent;
    }

    public float getShieldEffectivenessPercent() {
        return shieldEffectivenessPercent;
    }

    public float getHealthRecoveryPercent() {
        return healthRecoveryPercent;
    }

    public float getDamageTakenPercent() {
        return damageTakenPercent;
    }

    public float getDamageWhenHitPercent() {
        return damageWhenHitPercent;
    }

    public float getThornsDamage() {
        return thornsDamage;
    }

    public float getThornsDamagePercent() {
        return thornsDamagePercent;
    }

    public float getReflectDamagePercent() {
        return reflectDamagePercent;
    }

    public float getShieldRegenOnDot() {
        return shieldRegenOnDot;
    }

    public float getImmunityOnAilment() {
        return immunityOnAilment;
    }

    public float getEvasionToArmor() {
        return evasionToArmor;
    }

    // ==================== Setters ====================

    public void setArmor(float armor) {
        this.armor = armor;
    }

    public void setArmorPercent(float armorPercent) {
        this.armorPercent = armorPercent;
    }

    public void setEvasion(float evasion) {
        this.evasion = evasion;
    }

    public void setEnergyShield(float energyShield) {
        this.energyShield = energyShield;
    }

    public void setPassiveBlockChance(float passiveBlockChance) {
        this.passiveBlockChance = passiveBlockChance;
    }

    public void setParryChance(float parryChance) {
        this.parryChance = parryChance;
    }

    public void setKnockbackResistance(float knockbackResistance) {
        this.knockbackResistance = knockbackResistance;
    }

    public void setFallDamageReduction(float fallDamageReduction) {
        this.fallDamageReduction = fallDamageReduction;
    }

    public void setCritNullifyChance(float critNullifyChance) {
        this.critNullifyChance = critNullifyChance;
    }

    public void setCriticalReduction(float criticalReduction) {
        this.criticalReduction = criticalReduction;
    }

    public void setBlockChance(float blockChance) {
        this.blockChance = blockChance;
    }

    public void setBlockDamageReduction(float blockDamageReduction) {
        this.blockDamageReduction = blockDamageReduction;
    }

    public void setStaminaDrainReduction(float staminaDrainReduction) {
        this.staminaDrainReduction = staminaDrainReduction;
    }

    public void setPhysicalResistance(float physicalResistance) {
        this.physicalResistance = physicalResistance;
    }

    public void setDodgeChance(float dodgeChance) {
        this.dodgeChance = dodgeChance;
    }

    public void setBurnThreshold(float burnThreshold) {
        this.burnThreshold = burnThreshold;
    }

    public void setFreezeThreshold(float freezeThreshold) {
        this.freezeThreshold = freezeThreshold;
    }

    public void setShockThreshold(float shockThreshold) {
        this.shockThreshold = shockThreshold;
    }

    public void setBlockHealPercent(float blockHealPercent) {
        this.blockHealPercent = blockHealPercent;
    }

    public void setBlockRecoveryPercent(float blockRecoveryPercent) {
        this.blockRecoveryPercent = blockRecoveryPercent;
    }

    public void setShieldEffectivenessPercent(float shieldEffectivenessPercent) {
        this.shieldEffectivenessPercent = shieldEffectivenessPercent;
    }

    public void setHealthRecoveryPercent(float healthRecoveryPercent) {
        this.healthRecoveryPercent = healthRecoveryPercent;
    }

    public void setDamageTakenPercent(float damageTakenPercent) {
        this.damageTakenPercent = damageTakenPercent;
    }

    public void setDamageWhenHitPercent(float damageWhenHitPercent) {
        this.damageWhenHitPercent = damageWhenHitPercent;
    }

    public void setThornsDamage(float thornsDamage) {
        this.thornsDamage = thornsDamage;
    }

    public void setThornsDamagePercent(float thornsDamagePercent) {
        this.thornsDamagePercent = thornsDamagePercent;
    }

    public void setReflectDamagePercent(float reflectDamagePercent) {
        this.reflectDamagePercent = reflectDamagePercent;
    }

    public void setShieldRegenOnDot(float shieldRegenOnDot) {
        this.shieldRegenOnDot = shieldRegenOnDot;
    }

    public void setImmunityOnAilment(float immunityOnAilment) {
        this.immunityOnAilment = immunityOnAilment;
    }

    public void setEvasionToArmor(float evasionToArmor) {
        this.evasionToArmor = evasionToArmor;
    }

    // ==================== Utility Methods ====================

    /** Creates a copy of this DefensiveStats. */
    @Nonnull
    public DefensiveStats copy() {
        return toBuilder().build();
    }

    /**
     * Resets all values to 0.
     */
    public void reset() {
        armor = 0;
        armorPercent = 0;
        evasion = 0;
        energyShield = 0;
        passiveBlockChance = 0;
        parryChance = 0;
        knockbackResistance = 0;
        fallDamageReduction = 0;
        critNullifyChance = 0;
        criticalReduction = 0;
        blockChance = 0;
        blockDamageReduction = 0;
        staminaDrainReduction = 0;
        physicalResistance = 0;
        dodgeChance = 0;
        burnThreshold = 0;
        freezeThreshold = 0;
        shockThreshold = 0;
        blockHealPercent = 0;
        blockRecoveryPercent = 0;
        shieldEffectivenessPercent = 0;
        healthRecoveryPercent = 0;
        damageTakenPercent = 0;
        damageWhenHitPercent = 0;
        thornsDamage = 0;
        thornsDamagePercent = 0;
        reflectDamagePercent = 0;
        shieldRegenOnDot = 0;
        immunityOnAilment = 0;
        evasionToArmor = 0;
    }

    // ==================== Builder ====================

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
            .armor(armor)
            .armorPercent(armorPercent)
            .evasion(evasion)
            .energyShield(energyShield)
            .passiveBlockChance(passiveBlockChance)
            .parryChance(parryChance)
            .knockbackResistance(knockbackResistance)
            .fallDamageReduction(fallDamageReduction)
            .critNullifyChance(critNullifyChance)
            .criticalReduction(criticalReduction)
            .blockChance(blockChance)
            .blockDamageReduction(blockDamageReduction)
            .staminaDrainReduction(staminaDrainReduction)
            .physicalResistance(physicalResistance)
            .dodgeChance(dodgeChance)
            .burnThreshold(burnThreshold)
            .freezeThreshold(freezeThreshold)
            .shockThreshold(shockThreshold)
            .blockHealPercent(blockHealPercent)
            .blockRecoveryPercent(blockRecoveryPercent)
            .shieldEffectivenessPercent(shieldEffectivenessPercent)
            .healthRecoveryPercent(healthRecoveryPercent)
            .damageTakenPercent(damageTakenPercent)
            .damageWhenHitPercent(damageWhenHitPercent)
            .thornsDamage(thornsDamage)
            .thornsDamagePercent(thornsDamagePercent)
            .reflectDamagePercent(reflectDamagePercent)
            .shieldRegenOnDot(shieldRegenOnDot)
            .immunityOnAilment(immunityOnAilment)
            .evasionToArmor(evasionToArmor);
    }

    public static final class Builder {
        private float armor;
        private float armorPercent;
        private float evasion;
        private float energyShield;
        private float passiveBlockChance;
        private float parryChance;
        private float knockbackResistance;
        private float fallDamageReduction;
        private float critNullifyChance;
        private float criticalReduction;
        private float blockChance;
        private float blockDamageReduction;
        private float staminaDrainReduction;
        private float physicalResistance;
        private float dodgeChance;
        private float burnThreshold;
        private float freezeThreshold;
        private float shockThreshold;
        private float blockHealPercent;
        private float blockRecoveryPercent;
        private float shieldEffectivenessPercent;
        private float healthRecoveryPercent;
        private float damageTakenPercent;
        private float damageWhenHitPercent;
        private float thornsDamage;
        private float thornsDamagePercent;
        private float reflectDamagePercent;
        private float shieldRegenOnDot;
        private float immunityOnAilment;
        private float evasionToArmor;

        private Builder() {}

        public Builder armor(float value) {
            this.armor = value;
            return this;
        }

        public Builder armorPercent(float value) {
            this.armorPercent = value;
            return this;
        }

        public Builder evasion(float value) {
            this.evasion = value;
            return this;
        }

        public Builder energyShield(float value) {
            this.energyShield = value;
            return this;
        }

        public Builder passiveBlockChance(float value) {
            this.passiveBlockChance = value;
            return this;
        }

        public Builder parryChance(float value) {
            this.parryChance = value;
            return this;
        }

        public Builder knockbackResistance(float value) {
            this.knockbackResistance = value;
            return this;
        }

        public Builder fallDamageReduction(float value) {
            this.fallDamageReduction = value;
            return this;
        }

        public Builder critNullifyChance(float value) {
            this.critNullifyChance = value;
            return this;
        }

        public Builder criticalReduction(float value) {
            this.criticalReduction = value;
            return this;
        }

        public Builder blockChance(float value) {
            this.blockChance = value;
            return this;
        }

        public Builder blockDamageReduction(float value) {
            this.blockDamageReduction = value;
            return this;
        }

        public Builder staminaDrainReduction(float value) {
            this.staminaDrainReduction = value;
            return this;
        }

        public Builder physicalResistance(float value) {
            this.physicalResistance = value;
            return this;
        }

        public Builder dodgeChance(float value) {
            this.dodgeChance = value;
            return this;
        }

        public Builder burnThreshold(float value) {
            this.burnThreshold = value;
            return this;
        }

        public Builder freezeThreshold(float value) {
            this.freezeThreshold = value;
            return this;
        }

        public Builder shockThreshold(float value) {
            this.shockThreshold = value;
            return this;
        }

        public Builder blockHealPercent(float value) {
            this.blockHealPercent = value;
            return this;
        }

        public Builder blockRecoveryPercent(float value) {
            this.blockRecoveryPercent = value;
            return this;
        }

        public Builder shieldEffectivenessPercent(float value) {
            this.shieldEffectivenessPercent = value;
            return this;
        }

        public Builder healthRecoveryPercent(float value) {
            this.healthRecoveryPercent = value;
            return this;
        }

        public Builder damageTakenPercent(float value) {
            this.damageTakenPercent = value;
            return this;
        }

        public Builder damageWhenHitPercent(float value) {
            this.damageWhenHitPercent = value;
            return this;
        }

        public Builder thornsDamage(float value) {
            this.thornsDamage = value;
            return this;
        }

        public Builder thornsDamagePercent(float value) {
            this.thornsDamagePercent = value;
            return this;
        }

        public Builder reflectDamagePercent(float value) {
            this.reflectDamagePercent = value;
            return this;
        }

        public Builder shieldRegenOnDot(float value) {
            this.shieldRegenOnDot = value;
            return this;
        }

        public Builder immunityOnAilment(float value) {
            this.immunityOnAilment = value;
            return this;
        }

        public Builder evasionToArmor(float value) {
            this.evasionToArmor = value;
            return this;
        }

        public DefensiveStats build() {
            return new DefensiveStats(this);
        }
    }

    // ==================== Object Methods ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DefensiveStats that = (DefensiveStats) o;
        return Float.compare(armor, that.armor) == 0
            && Float.compare(armorPercent, that.armorPercent) == 0
            && Float.compare(evasion, that.evasion) == 0
            && Float.compare(energyShield, that.energyShield) == 0
            && Float.compare(passiveBlockChance, that.passiveBlockChance) == 0
            && Float.compare(parryChance, that.parryChance) == 0
            && Float.compare(knockbackResistance, that.knockbackResistance) == 0
            && Float.compare(fallDamageReduction, that.fallDamageReduction) == 0
            && Float.compare(critNullifyChance, that.critNullifyChance) == 0
            && Float.compare(physicalResistance, that.physicalResistance) == 0
            && Float.compare(dodgeChance, that.dodgeChance) == 0
            && Float.compare(burnThreshold, that.burnThreshold) == 0
            && Float.compare(freezeThreshold, that.freezeThreshold) == 0
            && Float.compare(shockThreshold, that.shockThreshold) == 0
            && Float.compare(thornsDamage, that.thornsDamage) == 0
            && Float.compare(thornsDamagePercent, that.thornsDamagePercent) == 0
            && Float.compare(reflectDamagePercent, that.reflectDamagePercent) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(armor, armorPercent, evasion, energyShield, passiveBlockChance, critNullifyChance,
            physicalResistance, dodgeChance, thornsDamage, thornsDamagePercent, reflectDamagePercent);
    }

    @Override
    public String toString() {
        return String.format(
            "DefensiveStats{armor=%.1f(+%.0f%%), evasion=%.1f%%, shield=%.1f, passiveBlock=%.1f%%}",
            armor, armorPercent * 100, evasion * 100, energyShield, passiveBlockChance * 100
        );
    }
}
