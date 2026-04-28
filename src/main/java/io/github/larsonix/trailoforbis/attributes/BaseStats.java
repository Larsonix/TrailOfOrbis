package io.github.larsonix.trailoforbis.attributes;

/**
 * Holds the base stat values read from the vanilla game entity.
 *
 * <p>These values serve as the starting point for stat calculations
 * before any attribute bonuses or modifiers are applied.
 */
public class BaseStats {
    private final float maxHealth;
    private final float maxMana;
    private final float maxStamina;
    private final float maxOxygen;
    private final float maxSignatureEnergy;

    public BaseStats(float maxHealth, float maxMana, float maxStamina, float maxOxygen, float maxSignatureEnergy) {
        this.maxHealth = maxHealth;
        this.maxMana = maxMana;
        this.maxStamina = maxStamina;
        this.maxOxygen = maxOxygen;
        this.maxSignatureEnergy = maxSignatureEnergy;
    }

    /**
     * Returns the default base stats to use when the entity is unavailable.
     *
     * <p>Defaults:
     * <ul>
     *   <li>Health: 100</li>
     *   <li>Mana: 0</li>
     *   <li>Stamina: 10</li>
     *   <li>Oxygen: 100</li>
     *   <li>Signature Energy: 100</li>
     * </ul>
     */
    public static BaseStats defaults() {
        return new BaseStats(100.0f, 0.0f, 10.0f, 100.0f, 100.0f);
    }

    public float getMaxHealth() { return maxHealth; }
    public float getMaxMana() { return maxMana; }
    public float getMaxStamina() { return maxStamina; }
    public float getMaxOxygen() { return maxOxygen; }
    public float getMaxSignatureEnergy() { return maxSignatureEnergy; }

    @Override
    public String toString() {
        return "BaseStats{" +
                "HP=" + maxHealth +
                ", Mana=" + maxMana +
                ", Stamina=" + maxStamina +
                ", Oxy=" + maxOxygen +
                ", Sig=" + maxSignatureEnergy +
                '}';
    }
}
