package io.github.larsonix.trailoforbis.simulation.gear;

import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig;
import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig.ImplicitDamageConfig;
import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig.ImplicitDefenseConfig;
import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig.RarityConfig;
import io.github.larsonix.trailoforbis.gear.config.ModifierConfig;
import io.github.larsonix.trailoforbis.gear.config.ModifierConfig.ModifierDefinition;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.gear.stats.GearStatCalculator.GearBonuses;
import io.github.larsonix.trailoforbis.util.LevelScaling;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * Generates level-appropriate gear bonuses for simulation using REAL config values.
 *
 * <p>Reads weapon implicit damage from {@link ImplicitDamageConfig} (gear-balance.yml)
 * and armor implicit defense from {@link ImplicitDefenseConfig} — no hardcoded values.
 *
 * <p>Uses MEDIAN rolls (roll factor 0.5) for deterministic balance analysis.
 *
 * <p>Equipment slots simulated: weapon, chest, legs, head, hands (5 pieces).
 * Assumes PLATE armor (most common) and one-handed SWORD (baseline weapon).
 */
public final class VirtualGearFactory {

    private static final String[] ARMOR_SLOTS = {"chest", "legs", "head", "hands"};

    /** Slot multipliers for armor implicit — from ImplicitDefenseConfig.slotMultipliers (gear-balance.yml). */
    private static final Map<String, Double> DEFAULT_SLOT_MULTS = Map.of(
            "chest", 1.0, "legs", 0.7, "head", 0.6, "hands", 0.5
    );

    private final GearBalanceConfig balanceConfig;
    private final ModifierConfig modifierConfig;
    private final ImplicitDamageConfig implicitDmgConfig;
    private final ImplicitDefenseConfig implicitDefConfig;

    public VirtualGearFactory(@Nonnull GearBalanceConfig balanceConfig,
                              @Nonnull ModifierConfig modifierConfig) {
        this.balanceConfig = Objects.requireNonNull(balanceConfig);
        this.modifierConfig = Objects.requireNonNull(modifierConfig);
        this.implicitDmgConfig = balanceConfig.implicitDamage();
        this.implicitDefConfig = balanceConfig.implicitDefense();
    }

    /**
     * Generates median gear bonuses for a player at the given level.
     *
     * <p>Uses real config values from gear-balance.yml:
     * <ul>
     *   <li>Weapon implicit: baseMin/baseMax/scaleFactor from ImplicitDamageConfig</li>
     *   <li>Armor implicit: PLATE material stats from ImplicitDefenseConfig</li>
     *   <li>Modifiers: real ModifierDefinition.calculateValue() with median roll</li>
     * </ul>
     */
    @Nonnull
    public GearBonuses generateForLevel(int level) {
        Map<String, Double> flatBonuses = new HashMap<>();
        Map<String, Double> percentBonuses = new HashMap<>();

        GearRarity rarity = getMedianRarity(level);
        RarityConfig rarityConfig = balanceConfig.rarityConfig(rarity);
        double qualityMult = 1.0; // Quality 50 → 0.5 + 50/100 = 1.0

        // Weapon implicit damage (from ImplicitDamageConfig)
        double weaponBaseDamage = calculateWeaponImplicit(level);

        // Armor implicit defense (from ImplicitDefenseConfig, PLATE material)
        for (String slot : ARMOR_SLOTS) {
            addImplicitDefense(flatBonuses, slot, level);
        }

        // Modifiers for all slots (from ModifierConfig)
        for (String slot : ARMOR_SLOTS) {
            addSlotModifiers(flatBonuses, percentBonuses, slot, level, rarity, rarityConfig, qualityMult);
        }
        addSlotModifiers(flatBonuses, percentBonuses, "weapon", level, rarity, rarityConfig, qualityMult);

        return new GearBonuses(
                Collections.unmodifiableMap(flatBonuses),
                Collections.unmodifiableMap(percentBonuses),
                weaponBaseDamage,
                "generic_sword",
                true
        );
    }

    // =========================================================================
    // Weapon Implicit — reads from ImplicitDamageConfig
    // =========================================================================

    private double calculateWeaponImplicit(int level) {
        if (!implicitDmgConfig.enabled()) return 5.0; // fallback

        // From gear-balance.yml implicit_damage section (verified values):
        // base_min: 3.0, base_max: 7.0, scale_factor: 100.0, two_handed_multiplier: 1.5
        double baseMin = implicitDmgConfig.baseMin();
        double baseMax = implicitDmgConfig.baseMax();
        double scaleFactor = implicitDmgConfig.scaleFactor();

        double bonusPercent = LevelScaling.getBonusPercent(level);
        double bonus = bonusPercent / 100.0 * scaleFactor;

        double scaledMin = baseMin + bonus;
        double scaledMax = baseMax + bonus;

        // Median roll (one-handed weapon — no twoHandedMultiplier)
        return (scaledMin + scaledMax) / 2.0;
    }

    // =========================================================================
    // Armor Implicit — reads from ImplicitDefenseConfig PLATE material
    // =========================================================================

    private void addImplicitDefense(Map<String, Double> flatBonuses, String slot, int level) {
        if (!implicitDefConfig.enabled()) return;

        // Use PLATE material config (stat: "armor", from gear-balance.yml)
        // Verified values: base_min: 5.0, base_max: 12.0, scale_factor: 80.0
        var plateMaterial = io.github.larsonix.trailoforbis.gear.model.ArmorMaterial.PLATE;
        var materialConfig = implicitDefConfig.materials().get(plateMaterial);
        if (materialConfig == null) return;

        String statName = materialConfig.stat();
        double baseMin = materialConfig.baseMin();
        double baseMax = materialConfig.baseMax();
        double scaleFactor = materialConfig.scaleFactor();

        // Slot multiplier from config (chest=1.0, legs=0.7, head=0.6, hands=0.5)
        var armorSlot = switch (slot) {
            case "chest" -> io.github.larsonix.trailoforbis.gear.model.EquipmentType.ArmorSlot.CHEST;
            case "legs" -> io.github.larsonix.trailoforbis.gear.model.EquipmentType.ArmorSlot.LEGS;
            case "head" -> io.github.larsonix.trailoforbis.gear.model.EquipmentType.ArmorSlot.HEAD;
            case "hands" -> io.github.larsonix.trailoforbis.gear.model.EquipmentType.ArmorSlot.HANDS;
            default -> null;
        };
        double slotMult = armorSlot != null
                ? implicitDefConfig.slotMultipliers().getOrDefault(armorSlot, DEFAULT_SLOT_MULTS.getOrDefault(slot, 0.5))
                : DEFAULT_SLOT_MULTS.getOrDefault(slot, 0.5);

        double bonusPercent = LevelScaling.getBonusPercent(level);
        double bonus = bonusPercent / 100.0 * scaleFactor;

        double median = ((baseMin + baseMax) / 2.0 + bonus) * slotMult;
        flatBonuses.merge(statName, median, Double::sum);
    }

    // =========================================================================
    // Modifier Generation — uses real ModifierConfig
    // =========================================================================

    private void addSlotModifiers(
            Map<String, Double> flatBonuses,
            Map<String, Double> percentBonuses,
            String slot, int level,
            GearRarity rarity, RarityConfig rarityConfig,
            double qualityMult) {

        int maxMods = rarity.getMaxModifiers();
        int numPrefixes = Math.min(maxMods / 2 + (maxMods % 2 == 1 ? 1 : 0), maxMods);
        int numSuffixes = maxMods - numPrefixes;

        List<ModifierDefinition> slotPrefixes = modifierConfig.prefixesForSlot(slot);
        List<ModifierDefinition> slotSuffixes = modifierConfig.suffixesForSlot(slot);

        addTopModifiers(flatBonuses, percentBonuses, slotPrefixes,
                numPrefixes, level, rarityConfig, qualityMult);
        addTopModifiers(flatBonuses, percentBonuses, slotSuffixes,
                numSuffixes, level, rarityConfig, qualityMult);
    }

    private void addTopModifiers(
            Map<String, Double> flatBonuses,
            Map<String, Double> percentBonuses,
            List<ModifierDefinition> candidates,
            int count, int level,
            RarityConfig rarityConfig,
            double qualityMult) {

        List<ModifierDefinition> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator.comparingInt(ModifierDefinition::weight).reversed());

        int added = 0;
        Set<String> usedStats = new HashSet<>();

        for (ModifierDefinition def : sorted) {
            if (added >= count) break;
            if (usedStats.contains(def.stat())) continue;
            usedStats.add(def.stat());

            double value = def.calculateValue(level, 0.5); // median roll
            value *= qualityMult;
            value *= rarityConfig.statMultiplier();

            if (def.statType() == ModifierConfig.StatType.FLAT) {
                flatBonuses.merge(def.stat(), value, Double::sum);
            } else {
                percentBonuses.merge(def.stat(), value, Double::sum);
            }
            added++;
        }
    }

    // =========================================================================
    // Rarity Selection — based on actual drop rate math
    // =========================================================================

    /** Number of equipment slots to fill. */
    private static final int SLOT_COUNT = 5;

    /** Base gear drop chance per mob kill (from gear-balance.yml: 0.08 = 8%). */
    private static final double BASE_DROP_CHANCE = 0.08;

    /** Effort curve exponent: ln(150/3) / ln(100) ≈ 0.849. From leveling.yml. */
    private static final double EFFORT_EXPONENT = Math.log(150.0 / 3.0) / Math.log(100.0);

    /** Rarity weights from GearRarity enum (verified from source). Total = 85.3285. */
    private static final double TOTAL_RARITY_WEIGHT =
            64.0 + 16.0 + 4.0 + 1.0 + 0.25 + 0.0625 + 0.016;

    /**
     * Determines the best rarity a player can realistically equip at the given level,
     * based on cumulative kills × drop rate × rarity weights.
     *
     * <p>A player needs at least SLOT_COUNT (5) items of a rarity to fill all slots.
     * This method finds the highest rarity where the expected drop count >= 5.
     *
     * <p>Kill count from effort curve: mobsPerLevel(L) = 3 × max(1, L)^0.849.
     * Drop rate: 8% base. Rarity probability: weight / totalWeight.
     */
    private GearRarity getMedianRarity(int level) {
        double cumulativeKills = 0;
        for (int l = 1; l <= level; l++) {
            cumulativeKills += 3.0 * Math.pow(Math.max(1, l), EFFORT_EXPONENT);
        }
        double totalDrops = cumulativeKills * BASE_DROP_CHANCE;

        // Check each rarity from best to worst — return highest with >= SLOT_COUNT expected items
        GearRarity[] rarities = {GearRarity.MYTHIC, GearRarity.LEGENDARY, GearRarity.EPIC,
                                  GearRarity.RARE, GearRarity.UNCOMMON, GearRarity.COMMON};
        double[] weights = {0.0625, 0.25, 1.0, 4.0, 16.0, 64.0};

        for (int i = 0; i < rarities.length; i++) {
            double prob = weights[i] / TOTAL_RARITY_WEIGHT;
            double expected = totalDrops * prob;
            if (expected >= SLOT_COUNT) {
                return rarities[i];
            }
        }
        return GearRarity.COMMON;
    }
}
