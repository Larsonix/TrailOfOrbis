package io.github.larsonix.trailoforbis.gear.config;

import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig.*;
import io.github.larsonix.trailoforbis.gear.config.ModifierConfig.*;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Factory for creating gear configurations in tests.
 *
 * <p>Since GearBalanceConfig and ModifierConfig have package-private constructors,
 * this factory class (in the same package) provides public methods for tests
 * to create config instances.
 */
public final class TestConfigFactory {

    private TestConfigFactory() {
        // Utility class
    }

    // =========================================================================
    // GEAR BALANCE CONFIG
    // =========================================================================

    /**
     * Creates a GearBalanceConfig with the given parameters.
     */
    public static GearBalanceConfig createBalanceConfig(
            double gearPowerRatio,
            Map<String, Double> slotWeights,
            double levelScalingFactor,
            Map<GearRarity, RarityConfig> rarityConfigs,
            QualityConfig quality,
            AttributeRequirementsConfig attributeRequirements,
            ModifierScalingConfig modifierScaling,
            LootConfig loot,
            StoneDropConfig stoneDrops
    ) {
        return new GearBalanceConfig(
            gearPowerRatio,
            slotWeights,
            levelScalingFactor,
            rarityConfigs,
            quality,
            attributeRequirements,
            modifierScaling,
            loot,
            stoneDrops,
            ExponentialScalingConfig.DISABLED,  // Tests use disabled for backward compat
            ImplicitDamageConfig.DISABLED,       // Tests use disabled for backward compat
            ImplicitDefenseConfig.DISABLED,      // Tests use disabled for backward compat
            VanillaWeaponProfilesConfig.DISABLED, // Tests use disabled for backward compat
            LevelBlendingConfig.DISABLED          // Tests use disabled for backward compat
        );
    }

    /**
     * Creates a default test balance config with reasonable defaults.
     */
    public static GearBalanceConfig createDefaultBalanceConfig() {
        Map<GearRarity, RarityConfig> rarityConfigs = new EnumMap<>(GearRarity.class);
        rarityConfigs.put(GearRarity.COMMON, new RarityConfig(0.3, 1, 0, 1, 0, 1, 64.0));
        rarityConfigs.put(GearRarity.UNCOMMON, new RarityConfig(0.5, 2, 0, 1, 0, 2, 16.0));
        rarityConfigs.put(GearRarity.RARE, new RarityConfig(0.8, 3, 1, 2, 1, 2, 4.0));
        rarityConfigs.put(GearRarity.EPIC, new RarityConfig(1.2, 4, 1, 2, 1, 2, 1.0));
        rarityConfigs.put(GearRarity.LEGENDARY, new RarityConfig(1.7, 4, 2, 2, 2, 2, 0.25));
        rarityConfigs.put(GearRarity.MYTHIC, new RarityConfig(2.3, 4, 2, 2, 2, 2, 0.0625, 0.75));
        rarityConfigs.put(GearRarity.UNIQUE, new RarityConfig(2.8, 6, 2, 3, 2, 3, 0.016, 0.80));

        return createBalanceConfigWithRarities(rarityConfigs);
    }

    /**
     * Creates a balance config with custom rarity configs.
     */
    public static GearBalanceConfig createBalanceConfigWithRarities(
            Map<GearRarity, RarityConfig> rarityConfigs
    ) {
        Map<String, Double> slotWeights = Map.of(
            "weapon", 0.25, "chest", 0.20, "legs", 0.15,
            "head", 0.15, "hands", 0.10, "feet", 0.10, "shield", 0.05
        );

        QualityDropDistribution dist = new QualityDropDistribution(
            0.15, 0.25, 0.10, 0.30, 0.195, 0.005
        );
        QualityConfig quality = new QualityConfig(50, 1, 100, 101, dist);

        Map<GearRarity, Double> attrMults = new EnumMap<>(GearRarity.class);
        for (GearRarity r : GearRarity.values()) {
            attrMults.put(r, 1.0);
        }

        return new GearBalanceConfig(
            0.5,  // gearPowerRatio
            slotWeights,
            0.02, // levelScalingFactor
            rarityConfigs,
            quality,
            new AttributeRequirementsConfig(0.5, 10, attrMults),
            new ModifierScalingConfig(0.01, 0.3, Map.of("common", 100, "rare", 10)),
            new LootConfig(0.5, 0.01, new DistanceScalingConfig(true, 100, 2.0), Map.of()),
            new StoneDropConfig(0.1, Map.of("quality_stone", 50, "modifier_stone", 30)),
            ExponentialScalingConfig.DISABLED,   // Tests use disabled for backward compat
            ImplicitDamageConfig.DISABLED,       // Tests use disabled for backward compat
            ImplicitDefenseConfig.DISABLED,      // Tests use disabled for backward compat
            VanillaWeaponProfilesConfig.DISABLED, // Tests use disabled for backward compat
            LevelBlendingConfig.DISABLED          // Tests use disabled for backward compat
        );
    }

    /**
     * Creates a balance config with zero weight for a specific rarity.
     */
    public static GearBalanceConfig createConfigWithZeroWeight(GearRarity zeroRarity) {
        Map<GearRarity, RarityConfig> rarityConfigs = new EnumMap<>(GearRarity.class);
        rarityConfigs.put(GearRarity.COMMON, new RarityConfig(0.3, 1, 0, 1, 0, 1,
            zeroRarity == GearRarity.COMMON ? 0.0 : 64.0));
        rarityConfigs.put(GearRarity.UNCOMMON, new RarityConfig(0.5, 2, 0, 1, 0, 2,
            zeroRarity == GearRarity.UNCOMMON ? 0.0 : 16.0));
        rarityConfigs.put(GearRarity.RARE, new RarityConfig(0.8, 3, 1, 2, 1, 2,
            zeroRarity == GearRarity.RARE ? 0.0 : 4.0));
        rarityConfigs.put(GearRarity.EPIC, new RarityConfig(1.2, 4, 1, 2, 1, 2,
            zeroRarity == GearRarity.EPIC ? 0.0 : 1.0));
        rarityConfigs.put(GearRarity.LEGENDARY, new RarityConfig(1.7, 4, 2, 2, 2, 2,
            zeroRarity == GearRarity.LEGENDARY ? 0.0 : 0.25));
        rarityConfigs.put(GearRarity.MYTHIC, new RarityConfig(2.3, 4, 2, 2, 2, 2,
            zeroRarity == GearRarity.MYTHIC ? 0.0 : 0.0625, 0.75));
        rarityConfigs.put(GearRarity.UNIQUE, new RarityConfig(2.8, 6, 2, 3, 2, 3,
            zeroRarity == GearRarity.UNIQUE ? 0.0 : 0.016, 0.80));

        return createBalanceConfigWithRarities(rarityConfigs);
    }

    /**
     * Creates a balance config with only one rarity having weight.
     */
    public static GearBalanceConfig createSingleRarityConfig(GearRarity onlyRarity) {
        Map<GearRarity, RarityConfig> rarityConfigs = new EnumMap<>(GearRarity.class);
        for (GearRarity rarity : GearRarity.values()) {
            double weight = (rarity == onlyRarity) ? 100.0 : 0.0;
            if (rarity == GearRarity.MYTHIC) {
                rarityConfigs.put(rarity, new RarityConfig(2.3, 4, 2, 2, 2, 2, weight, 0.75));
            } else if (rarity == GearRarity.UNIQUE) {
                rarityConfigs.put(rarity, new RarityConfig(2.8, 6, 2, 3, 2, 3, weight, 0.80));
            } else {
                rarityConfigs.put(rarity, new RarityConfig(1.2, 4, 1, 2, 1, 2, weight));
            }
        }
        return createBalanceConfigWithRarities(rarityConfigs);
    }

    // =========================================================================
    // MODIFIER CONFIG
    // =========================================================================

    /**
     * Creates a ModifierConfig with the given prefix and suffix maps.
     */
    public static ModifierConfig createModifierConfig(
            Map<String, ModifierDefinition> prefixes,
            Map<String, ModifierDefinition> suffixes
    ) {
        return new ModifierConfig(prefixes, suffixes);
    }

    /**
     * Creates an empty ModifierConfig.
     */
    public static ModifierConfig createEmptyModifierConfig() {
        return new ModifierConfig(Map.of(), Map.of());
    }

    /**
     * Creates a default test modifier config with some basic modifiers.
     */
    public static ModifierConfig createDefaultModifierConfig() {
        Map<String, ModifierDefinition> prefixes = Map.of(
            "sharp", new ModifierDefinition(
                "sharp", "Sharp", "physical_damage",
                StatType.FLAT, 5.0, 15.0, 0.5, 100,
                null, null
            ),
            "sturdy", new ModifierDefinition(
                "sturdy", "Sturdy", "armor",
                StatType.FLAT, 3.0, 10.0, 0.3, 80,
                null, null
            ),
            "quick", new ModifierDefinition(
                "quick", "Quick", "attack_speed",
                StatType.PERCENT, 2.0, 8.0, 0.2, 60,
                null, Set.of("weapon")
            )
        );

        Map<String, ModifierDefinition> suffixes = Map.of(
            "of_might", new ModifierDefinition(
                "of_might", "of Might", "strength",
                StatType.FLAT, 2.0, 8.0, 0.2, 100,
                null, null
            ),
            "of_the_bear", new ModifierDefinition(
                "of_the_bear", "of the Bear", "health",
                StatType.FLAT, 10.0, 30.0, 1.0, 80,
                null, null
            ),
            "of_swiftness", new ModifierDefinition(
                "of_swiftness", "of Swiftness", "movement_speed",
                StatType.PERCENT, 3.0, 10.0, 0.3, 60,
                null, Set.of("feet")
            )
        );

        return new ModifierConfig(prefixes, suffixes);
    }

    /**
     * Creates a modifier config with prefixes only allowed on specific slots.
     */
    public static ModifierConfig createSlotRestrictedModifierConfig(String slot) {
        Map<String, ModifierDefinition> prefixes = Map.of(
            "slot_mod", new ModifierDefinition(
                "slot_mod", "Slot Specific", "damage",
                StatType.FLAT, 5.0, 10.0, 0.5, 100,
                null, Set.of(slot)
            )
        );

        Map<String, ModifierDefinition> suffixes = Map.of(
            "generic_suffix", new ModifierDefinition(
                "generic_suffix", "Generic", "armor",
                StatType.FLAT, 3.0, 8.0, 0.3, 100,
                null, null
            )
        );

        return new ModifierConfig(prefixes, suffixes);
    }
}
