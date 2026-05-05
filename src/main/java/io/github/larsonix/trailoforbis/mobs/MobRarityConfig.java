package io.github.larsonix.trailoforbis.mobs;

import javax.annotation.Nonnull;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuration for mob rarity tier multipliers.
 *
 * <p>Loaded from {@code mob-rarity.yml}. Defines Normal/Elite/Boss multipliers
 * for HP, damage, armor, evasion, speed, XP, IIQ, IIR, and ailment effectiveness.
 *
 * <p>Also supports realm-specific overrides for tuning endgame content
 * differently from overworld encounters.
 */
public class MobRarityConfig {

    private Map<String, TierEntry> tiers = new LinkedHashMap<>();
    private Map<String, Map<String, Object>> realm_overrides = new LinkedHashMap<>();
    private EliteSpawnEntry elite_spawn = new EliteSpawnEntry();

    // ==================== YAML Setters ====================

    public void setTiers(Map<String, TierEntry> tiers) {
        if (tiers != null) {
            this.tiers = tiers;
        }
    }

    @SuppressWarnings("unchecked")
    public void setRealm_overrides(Map<String, Map<String, Object>> raw) {
        this.realm_overrides = raw != null ? raw : new LinkedHashMap<>();
    }

    public void setElite_spawn(EliteSpawnEntry entry) {
        if (entry != null) {
            this.elite_spawn = entry;
        }
    }

    // ==================== Accessors ====================

    /**
     * Gets the tier multipliers for a specific rarity.
     *
     * @param tier "normal", "elite", or "boss"
     * @return The tier entry, or a default 1.0× entry if not found
     */
    @Nonnull
    public TierEntry getTier(@Nonnull String tier) {
        TierEntry entry = tiers.get(tier.toLowerCase());
        return entry != null ? entry : new TierEntry();
    }

    /**
     * Gets realm-specific override for a tier's stat.
     *
     * @param tier The rarity tier
     * @param stat The stat to check (e.g., "hp")
     * @return The overridden multiplier, or -1 if no override
     */
    public double getRealmOverride(@Nonnull String tier, @Nonnull String stat) {
        Map<String, Object> overrides = realm_overrides.get(tier.toLowerCase());
        if (overrides == null) return -1;
        Object val = overrides.get(stat);
        return val instanceof Number n ? n.doubleValue() : -1;
    }

    @Nonnull
    public EliteSpawnEntry getElite_spawn() {
        return elite_spawn;
    }

    // Keep camelCase getter for Java code convenience
    @Nonnull
    public EliteSpawnEntry getEliteSpawn() {
        return elite_spawn;
    }

    // ==================== Factory ====================

    @Nonnull
    public static MobRarityConfig createDefaults() {
        return new MobRarityConfig();
    }

    // ==================== Nested Data Classes ====================

    /**
     * Multiplier values for a single rarity tier.
     * Proper JavaBean — SnakeYAML constructs directly via setters.
     */
    public static class TierEntry {
        private double hp = 1.0;
        private double damage = 1.0;
        private double armor = 1.0;
        private double evasion = 1.0;
        private double speed = 1.0;
        private double xp = 1.0;
        private double iiq = 1.0;
        private double iir = 1.0;
        private double ailmentEffectiveness = 1.0;

        public double getHp() { return hp; }
        public double getDamage() { return damage; }
        public double getArmor() { return armor; }
        public double getEvasion() { return evasion; }
        public double getSpeed() { return speed; }
        public double getXp() { return xp; }
        public double getIiq() { return iiq; }
        public double getIir() { return iir; }
        public double getAilmentEffectiveness() { return ailmentEffectiveness; }

        // SnakeYAML setters (snake_case matches YAML keys)
        public void setHp(double v) { this.hp = v; }
        public void setDamage(double v) { this.damage = v; }
        public void setArmor(double v) { this.armor = v; }
        public void setEvasion(double v) { this.evasion = v; }
        public void setSpeed(double v) { this.speed = v; }
        public void setXp(double v) { this.xp = v; }
        public void setIiq(double v) { this.iiq = v; }
        public void setIir(double v) { this.iir = v; }
        public void setAilment_effectiveness(double v) { this.ailmentEffectiveness = v; }
    }

    /**
     * Elite spawn chance configuration.
     * Proper JavaBean — SnakeYAML constructs directly via setters.
     */
    public static class EliteSpawnEntry {
        private double baseChance = 0.05;
        private double chancePerLevel = 0.0001;
        private double maxChance = 0.25;

        public double getBaseChance() { return baseChance; }
        public double getChancePerLevel() { return chancePerLevel; }
        public double getMaxChance() { return maxChance; }

        // SnakeYAML setters
        public void setBase_chance(double v) { this.baseChance = v; }
        public void setChance_per_level(double v) { this.chancePerLevel = v; }
        public void setMax_chance(double v) { this.maxChance = v; }
    }
}
