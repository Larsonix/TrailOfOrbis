package io.github.larsonix.trailoforbis.attributes.stats;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Container for resource pool stats (health, mana, stamina, etc.) and their regeneration rates.
 *
 * <p>This class holds the maximum values for player resources and their per-second
 * regeneration rates. Used as part of the composed {@code ComputedStats} structure.
 *
 * <p>Thread-safe: This class is mutable but designed for single-threaded access
 * during entity processing. For concurrent access, external synchronization is needed.
 */
public final class ResourceStats {

    // ==================== Max Resource Pools ====================
    private float maxHealth;
    private float maxMana;
    private float maxStamina;
    private float maxOxygen;
    private float maxSignatureEnergy;

    // ==================== Regeneration Rates (per second) ====================
    private float healthRegen;
    private float manaRegen;
    private float staminaRegen;
    private float oxygenRegen;
    private float signatureEnergyRegen;

    // ==================== Regeneration Modifiers ====================
    /** Health regeneration bonus percentage. Multiplies healthRegen: effective = healthRegen * (1 + pct/100). */
    private float healthRegenPercent;

    // ==================== Percent Modifiers ====================
    /** Max health percentage modifier. Final = base * (1 + pct/100). */
    private float maxHealthPercent;
    /** Max mana percentage modifier. Final = base * (1 + pct/100). */
    private float maxManaPercent;
    /** Mana cost percentage modifier (positive = higher cost). */
    private float manaCostPercent;
    /** Max stamina percentage modifier. Final = base * (1 + pct/100). */
    private float maxStaminaPercent;
    /** Stamina regeneration bonus percentage. Multiplies staminaRegen: effective = staminaRegen * (1 + pct/100). */
    private float staminaRegenPercent;
    /** Stamina regen start delay reduction percentage. Accelerates delay recovery after sprinting. */
    private float staminaRegenStartDelay;

    // ==================== Signature Energy Modifiers ====================
    /** % bonus to maximum signature energy. Applied multiplicatively. */
    private float signatureEnergyMaxPercent;
    /** Flat signature energy gained when dealing damage. Triggers on each hit. Range: 0-10. */
    private float signatureEnergyPerHit;

    // ==================== On-Kill Bonuses ====================
    /** Flat mana gained when killing an enemy. Triggered by VOID element. */
    private float manaOnKill;

    /**
     * Creates a new ResourceStats with all values initialized to 0.
     */
    public ResourceStats() {
        // All fields default to 0
    }

    /**
     * Private constructor for builder.
     */
    private ResourceStats(Builder builder) {
        this.maxHealth = builder.maxHealth;
        this.maxMana = builder.maxMana;
        this.maxStamina = builder.maxStamina;
        this.maxOxygen = builder.maxOxygen;
        this.maxSignatureEnergy = builder.maxSignatureEnergy;
        this.healthRegen = builder.healthRegen;
        this.manaRegen = builder.manaRegen;
        this.staminaRegen = builder.staminaRegen;
        this.oxygenRegen = builder.oxygenRegen;
        this.signatureEnergyRegen = builder.signatureEnergyRegen;
        this.healthRegenPercent = builder.healthRegenPercent;

        this.maxHealthPercent = builder.maxHealthPercent;
        this.maxManaPercent = builder.maxManaPercent;
        this.manaCostPercent = builder.manaCostPercent;
        this.maxStaminaPercent = builder.maxStaminaPercent;
        this.staminaRegenPercent = builder.staminaRegenPercent;
        this.staminaRegenStartDelay = builder.staminaRegenStartDelay;
        this.signatureEnergyMaxPercent = builder.signatureEnergyMaxPercent;
        this.signatureEnergyPerHit = builder.signatureEnergyPerHit;
        this.manaOnKill = builder.manaOnKill;
    }

    // ==================== Getters ====================

    public float getMaxHealth() {
        return maxHealth;
    }

    public float getMaxMana() {
        return maxMana;
    }

    public float getMaxStamina() {
        return maxStamina;
    }

    public float getMaxOxygen() {
        return maxOxygen;
    }

    public float getMaxSignatureEnergy() {
        return maxSignatureEnergy;
    }

    public float getHealthRegen() {
        return healthRegen;
    }

    public float getManaRegen() {
        return manaRegen;
    }

    public float getStaminaRegen() {
        return staminaRegen;
    }

    public float getOxygenRegen() {
        return oxygenRegen;
    }

    public float getSignatureEnergyRegen() {
        return signatureEnergyRegen;
    }

    public float getHealthRegenPercent() {
        return healthRegenPercent;
    }


    public float getMaxHealthPercent() {
        return maxHealthPercent;
    }

    public float getMaxManaPercent() {
        return maxManaPercent;
    }

    public float getManaCostPercent() {
        return manaCostPercent;
    }

    public float getMaxStaminaPercent() {
        return maxStaminaPercent;
    }

    public float getStaminaRegenPercent() {
        return staminaRegenPercent;
    }

    public float getStaminaRegenStartDelay() {
        return staminaRegenStartDelay;
    }

    public float getSignatureEnergyMaxPercent() {
        return signatureEnergyMaxPercent;
    }

    public float getSignatureEnergyPerHit() {
        return signatureEnergyPerHit;
    }

    public float getManaOnKill() {
        return manaOnKill;
    }

    // ==================== Setters ====================

    public void setMaxHealth(float maxHealth) {
        this.maxHealth = maxHealth;
    }

    public void setMaxMana(float maxMana) {
        this.maxMana = maxMana;
    }

    public void setMaxStamina(float maxStamina) {
        this.maxStamina = maxStamina;
    }

    public void setMaxOxygen(float maxOxygen) {
        this.maxOxygen = maxOxygen;
    }

    public void setMaxSignatureEnergy(float maxSignatureEnergy) {
        this.maxSignatureEnergy = maxSignatureEnergy;
    }

    public void setHealthRegen(float healthRegen) {
        this.healthRegen = healthRegen;
    }

    public void setManaRegen(float manaRegen) {
        this.manaRegen = manaRegen;
    }

    public void setStaminaRegen(float staminaRegen) {
        this.staminaRegen = staminaRegen;
    }

    public void setOxygenRegen(float oxygenRegen) {
        this.oxygenRegen = oxygenRegen;
    }

    public void setSignatureEnergyRegen(float signatureEnergyRegen) {
        this.signatureEnergyRegen = signatureEnergyRegen;
    }

    public void setHealthRegenPercent(float healthRegenPercent) {
        this.healthRegenPercent = healthRegenPercent;
    }


    public void setMaxHealthPercent(float maxHealthPercent) {
        this.maxHealthPercent = maxHealthPercent;
    }

    public void setMaxManaPercent(float maxManaPercent) {
        this.maxManaPercent = maxManaPercent;
    }

    public void setManaCostPercent(float manaCostPercent) {
        this.manaCostPercent = manaCostPercent;
    }

    public void setMaxStaminaPercent(float maxStaminaPercent) {
        this.maxStaminaPercent = maxStaminaPercent;
    }

    public void setStaminaRegenPercent(float staminaRegenPercent) {
        this.staminaRegenPercent = staminaRegenPercent;
    }

    public void setStaminaRegenStartDelay(float staminaRegenStartDelay) {
        this.staminaRegenStartDelay = staminaRegenStartDelay;
    }

    public void setSignatureEnergyMaxPercent(float signatureEnergyMaxPercent) {
        this.signatureEnergyMaxPercent = signatureEnergyMaxPercent;
    }

    public void setSignatureEnergyPerHit(float signatureEnergyPerHit) {
        this.signatureEnergyPerHit = signatureEnergyPerHit;
    }

    public void setManaOnKill(float manaOnKill) {
        this.manaOnKill = manaOnKill;
    }

    // ==================== Utility Methods ====================

    /** Creates a copy of this ResourceStats. */
    @Nonnull
    public ResourceStats copy() {
        return toBuilder().build();
    }

    /**
     * Resets all values to 0.
     */
    public void reset() {
        maxHealth = 0;
        maxMana = 0;
        maxStamina = 0;
        maxOxygen = 0;
        maxSignatureEnergy = 0;
        healthRegen = 0;
        manaRegen = 0;
        staminaRegen = 0;
        oxygenRegen = 0;
        signatureEnergyRegen = 0;
        healthRegenPercent = 0;
        maxHealthPercent = 0;
        maxManaPercent = 0;
        manaCostPercent = 0;
        maxStaminaPercent = 0;
        staminaRegenPercent = 0;
        staminaRegenStartDelay = 0;
        signatureEnergyMaxPercent = 0;
        signatureEnergyPerHit = 0;
        manaOnKill = 0;
    }

    // ==================== Builder ====================

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
            .maxHealth(maxHealth)
            .maxMana(maxMana)
            .maxStamina(maxStamina)
            .maxOxygen(maxOxygen)
            .maxSignatureEnergy(maxSignatureEnergy)
            .healthRegen(healthRegen)
            .manaRegen(manaRegen)
            .staminaRegen(staminaRegen)
            .oxygenRegen(oxygenRegen)
            .signatureEnergyRegen(signatureEnergyRegen)
            .healthRegenPercent(healthRegenPercent)
            .maxHealthPercent(maxHealthPercent)
            .maxManaPercent(maxManaPercent)
            .manaCostPercent(manaCostPercent)
            .maxStaminaPercent(maxStaminaPercent)
            .staminaRegenPercent(staminaRegenPercent)
            .staminaRegenStartDelay(staminaRegenStartDelay)
            .signatureEnergyMaxPercent(signatureEnergyMaxPercent)
            .signatureEnergyPerHit(signatureEnergyPerHit)
            .manaOnKill(manaOnKill);
    }

    public static final class Builder {
        private float maxHealth;
        private float maxMana;
        private float maxStamina;
        private float maxOxygen;
        private float maxSignatureEnergy;
        private float healthRegen;
        private float manaRegen;
        private float staminaRegen;
        private float oxygenRegen;
        private float signatureEnergyRegen;
        private float healthRegenPercent;
        private float maxHealthPercent;
        private float maxManaPercent;
        private float manaCostPercent;
        private float maxStaminaPercent;
        private float staminaRegenPercent;
        private float staminaRegenStartDelay;
        private float signatureEnergyMaxPercent;
        private float signatureEnergyPerHit;
        private float manaOnKill;

        private Builder() {}

        public Builder maxHealth(float value) {
            this.maxHealth = value;
            return this;
        }

        public Builder maxMana(float value) {
            this.maxMana = value;
            return this;
        }

        public Builder maxStamina(float value) {
            this.maxStamina = value;
            return this;
        }

        public Builder maxOxygen(float value) {
            this.maxOxygen = value;
            return this;
        }

        public Builder maxSignatureEnergy(float value) {
            this.maxSignatureEnergy = value;
            return this;
        }

        public Builder healthRegen(float value) {
            this.healthRegen = value;
            return this;
        }

        public Builder manaRegen(float value) {
            this.manaRegen = value;
            return this;
        }

        public Builder staminaRegen(float value) {
            this.staminaRegen = value;
            return this;
        }

        public Builder oxygenRegen(float value) {
            this.oxygenRegen = value;
            return this;
        }

        public Builder signatureEnergyRegen(float value) {
            this.signatureEnergyRegen = value;
            return this;
        }

        public Builder healthRegenPercent(float value) {
            this.healthRegenPercent = value;
            return this;
        }


        public Builder maxHealthPercent(float value) {
            this.maxHealthPercent = value;
            return this;
        }

        public Builder maxManaPercent(float value) {
            this.maxManaPercent = value;
            return this;
        }

        public Builder manaCostPercent(float value) {
            this.manaCostPercent = value;
            return this;
        }

        public Builder maxStaminaPercent(float value) {
            this.maxStaminaPercent = value;
            return this;
        }

        public Builder staminaRegenPercent(float value) {
            this.staminaRegenPercent = value;
            return this;
        }

        public Builder staminaRegenStartDelay(float value) {
            this.staminaRegenStartDelay = value;
            return this;
        }

        public Builder signatureEnergyMaxPercent(float value) {
            this.signatureEnergyMaxPercent = value;
            return this;
        }

        public Builder signatureEnergyPerHit(float value) {
            this.signatureEnergyPerHit = value;
            return this;
        }

        public Builder manaOnKill(float value) {
            this.manaOnKill = value;
            return this;
        }

        public ResourceStats build() {
            return new ResourceStats(this);
        }
    }

    // ==================== Object Methods ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ResourceStats that = (ResourceStats) o;
        return Float.compare(maxHealth, that.maxHealth) == 0
            && Float.compare(maxMana, that.maxMana) == 0
            && Float.compare(maxStamina, that.maxStamina) == 0
            && Float.compare(maxOxygen, that.maxOxygen) == 0
            && Float.compare(maxSignatureEnergy, that.maxSignatureEnergy) == 0
            && Float.compare(healthRegen, that.healthRegen) == 0
            && Float.compare(manaRegen, that.manaRegen) == 0
            && Float.compare(staminaRegen, that.staminaRegen) == 0
            && Float.compare(oxygenRegen, that.oxygenRegen) == 0
            && Float.compare(signatureEnergyRegen, that.signatureEnergyRegen) == 0
            && Float.compare(healthRegenPercent, that.healthRegenPercent) == 0
            && Float.compare(maxStaminaPercent, that.maxStaminaPercent) == 0
            && Float.compare(staminaRegenPercent, that.staminaRegenPercent) == 0
            && Float.compare(staminaRegenStartDelay, that.staminaRegenStartDelay) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(maxHealth, maxMana, maxStamina, healthRegen, manaRegen, healthRegenPercent,
            maxStaminaPercent, staminaRegenPercent, staminaRegenStartDelay);
    }

    @Override
    public String toString() {
        return String.format(
            "ResourceStats{hp=%.1f(+%.2f/s), mana=%.1f(+%.2f/s), stamina=%.1f(+%.2f/s)}",
            maxHealth, healthRegen, maxMana, manaRegen, maxStamina, staminaRegen
        );
    }
}
