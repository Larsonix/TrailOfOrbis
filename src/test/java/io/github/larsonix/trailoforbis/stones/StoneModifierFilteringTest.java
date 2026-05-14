package io.github.larsonix.trailoforbis.stones;

import io.github.larsonix.trailoforbis.gear.config.EquipmentStatConfig;
import io.github.larsonix.trailoforbis.gear.config.GearBalanceConfig;
import io.github.larsonix.trailoforbis.gear.config.ModifierConfig;
import io.github.larsonix.trailoforbis.gear.config.ModifierConfig.ModifierDefinition;
import io.github.larsonix.trailoforbis.gear.config.ModifierConfig.StatType;
import io.github.larsonix.trailoforbis.gear.config.TestConfigFactory;
import io.github.larsonix.trailoforbis.gear.generation.GearModifierRoller;
import io.github.larsonix.trailoforbis.gear.generation.ModifierPool;
import io.github.larsonix.trailoforbis.gear.model.EquipmentType;
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.model.GearModifier;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.gear.model.ModifierType;
import io.github.larsonix.trailoforbis.gear.model.ArmorImplicit;
import io.github.larsonix.trailoforbis.gear.model.WeaponImplicit;
import io.github.larsonix.trailoforbis.maps.config.RealmModifierConfig;
import io.github.larsonix.trailoforbis.stones.handler.GearStoneHandler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that stone operations respect modifier pool filtering.
 *
 * <p>Verifies the two-stage filtering pipeline:
 * <ol>
 *   <li><b>Stage 1 (slot filter)</b>: Only modifiers whose {@code allowed_slots}
 *       includes the item's slot can appear</li>
 *   <li><b>Stage 2 (equipment type filter)</b>: Only modifiers in the equipment
 *       type's allowed set (from equipment-stats.yml) can appear</li>
 * </ol>
 *
 * <p>Uses a minimal but realistic modifier config with clear weapon/armor separation.
 */
@DisplayName("Stone Modifier Pool Filtering")
class StoneModifierFilteringTest {

    // Weapon-only prefixes (allowed_slots: [weapon])
    private static final String SHARP = "sharp";
    private static final String HEAVY = "heavy";
    private static final String ARCANE = "arcane";

    // Armor-only prefixes (allowed_slots: [head, chest, legs])
    private static final String IRONCLAD = "ironclad";
    private static final String FIREPROOF = "fireproof";
    private static final String RESILIENT = "resilient";

    // Weapon-only suffixes (allowed_slots: [weapon])
    private static final String VAMPIRIC = "vampiric";
    private static final String OF_PARRYING = "of_parrying";

    // Armor-only suffixes (allowed_slots: [head, chest, legs])
    private static final String OF_THE_FORTRESS = "of_the_fortress";
    private static final String OF_THE_WHALE = "of_the_whale";
    private static final String OF_VITALITY = "of_vitality";
    private static final String OF_THE_SAGE = "of_the_sage";
    private static final String OF_EVASION = "of_evasion";

    private static final Set<String> WEAPON_PREFIXES = Set.of(SHARP, HEAVY, ARCANE);
    private static final Set<String> ARMOR_PREFIXES = Set.of(IRONCLAD, FIREPROOF, RESILIENT);
    private static final Set<String> WEAPON_SUFFIXES = Set.of(VAMPIRIC, OF_PARRYING);
    private static final Set<String> ARMOR_SUFFIXES = Set.of(OF_THE_FORTRESS, OF_THE_WHALE, OF_VITALITY, OF_THE_SAGE, OF_EVASION);

    private ModifierConfig modifierConfig;
    private GearBalanceConfig balanceConfig;
    private EquipmentStatConfig equipmentStatConfig;
    private StoneActionRegistry registry;
    private Random random;

    @BeforeEach
    void setUp() {
        modifierConfig = createFilteringModifierConfig();
        balanceConfig = TestConfigFactory.createDefaultBalanceConfig();
        equipmentStatConfig = createFilteringEquipmentStatConfig();

        registry = new StoneActionRegistry(
                new RealmModifierConfig(),
                null,
                modifierConfig,
                balanceConfig,
                equipmentStatConfig);

        random = new Random(42);
    }

    // ═══════════════════════════════════════════════════════════════════
    // CONFIG FACTORIES
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Creates a modifier config with clear weapon/armor separation via allowed_slots.
     */
    private static ModifierConfig createFilteringModifierConfig() {
        Map<String, ModifierDefinition> prefixes = new LinkedHashMap<>();

        // Weapon-only prefixes
        prefixes.put(SHARP, modDef(SHARP, "Sharp", "physical_damage", StatType.FLAT, 100, Set.of("weapon")));
        prefixes.put(HEAVY, modDef(HEAVY, "Heavy", "physical_damage_percent", StatType.PERCENT, 100, Set.of("weapon")));
        prefixes.put(ARCANE, modDef(ARCANE, "Arcane", "spell_damage", StatType.FLAT, 50, Set.of("weapon")));

        // Armor-only prefixes (head, chest, legs — not weapon)
        prefixes.put(IRONCLAD, modDef(IRONCLAD, "Ironclad", "block_damage_reduction", StatType.PERCENT, 25, Set.of("head", "chest", "legs")));
        prefixes.put(FIREPROOF, modDef(FIREPROOF, "Fireproof", "fire_resistance", StatType.PERCENT, 50, Set.of("head", "chest", "legs")));
        prefixes.put(RESILIENT, modDef(RESILIENT, "Resilient", "critical_reduction", StatType.PERCENT, 25, Set.of("head", "chest", "legs")));

        Map<String, ModifierDefinition> suffixes = new LinkedHashMap<>();

        // Weapon-only suffixes
        suffixes.put(VAMPIRIC, modDef(VAMPIRIC, "Vampiric", "life_steal", StatType.PERCENT, 50, Set.of("weapon")));
        suffixes.put(OF_PARRYING, modDef(OF_PARRYING, "of Parrying", "parry_chance", StatType.PERCENT, 50, Set.of("weapon")));

        // Armor-only suffixes
        suffixes.put(OF_THE_FORTRESS, modDef(OF_THE_FORTRESS, "of the Fortress", "armor", StatType.FLAT, 100, Set.of("head", "chest", "legs")));
        suffixes.put(OF_THE_WHALE, modDef(OF_THE_WHALE, "of the Whale", "max_health", StatType.FLAT, 100, Set.of("head", "chest", "legs")));
        suffixes.put(OF_VITALITY, modDef(OF_VITALITY, "of Vitality", "health_regen", StatType.FLAT, 50, Set.of("head", "chest", "legs")));
        suffixes.put(OF_THE_SAGE, modDef(OF_THE_SAGE, "of the Sage", "max_mana", StatType.FLAT, 50, Set.of("head", "chest", "legs")));
        suffixes.put(OF_EVASION, modDef(OF_EVASION, "of Evasion", "evasion", StatType.FLAT, 50, Set.of("head", "chest", "legs")));

        return TestConfigFactory.createModifierConfig(prefixes, suffixes);
    }

    /**
     * Creates equipment stat config with distinct profiles for different equipment types.
     *
     * <p>Profiles:
     * <ul>
     *   <li>SWORD: sharp, heavy prefixes + vampiric, of_parrying suffixes</li>
     *   <li>STAFF: arcane prefix + vampiric suffix (magic-focused)</li>
     *   <li>PLATE_HEAD: ironclad prefix + of_the_fortress, of_the_whale suffixes</li>
     *   <li>CLOTH_HEAD: fireproof prefix + of_the_sage, of_vitality suffixes</li>
     *   <li>LEATHER_HEAD: resilient prefix + of_evasion, of_vitality suffixes</li>
     * </ul>
     */
    private static EquipmentStatConfig createFilteringEquipmentStatConfig() {
        return EquipmentStatConfig.builder()
                // Weapons
                .addProfile(EquipmentType.SWORD,
                        Set.of(SHARP, HEAVY),
                        Set.of(VAMPIRIC, OF_PARRYING))
                .addProfile(EquipmentType.STAFF,
                        Set.of(ARCANE),
                        Set.of(VAMPIRIC))
                // Armor — plate
                .addProfile(EquipmentType.PLATE_HEAD,
                        Set.of(IRONCLAD),
                        Set.of(OF_THE_FORTRESS, OF_THE_WHALE))
                .addProfile(EquipmentType.PLATE_CHEST,
                        Set.of(IRONCLAD),
                        Set.of(OF_THE_FORTRESS, OF_THE_WHALE, OF_VITALITY))
                // Armor — cloth
                .addProfile(EquipmentType.CLOTH_HEAD,
                        Set.of(FIREPROOF),
                        Set.of(OF_THE_SAGE, OF_VITALITY))
                // Armor — leather
                .addProfile(EquipmentType.LEATHER_HEAD,
                        Set.of(RESILIENT),
                        Set.of(OF_EVASION, OF_VITALITY))
                .build();
    }

    private static ModifierDefinition modDef(String id, String displayName, String stat,
            StatType statType, int weight, Set<String> allowedSlots) {
        return new ModifierDefinition(id, displayName, stat, statType, 5.0, 15.0, 0.5, weight, null, allowedSlots);
    }

    // ═══════════════════════════════════════════════════════════════════
    // GEAR DATA FACTORIES
    // ═══════════════════════════════════════════════════════════════════

    private GearData createPlateHelmet() {
        return GearData.builder()
                .level(50)
                .rarity(GearRarity.RARE)
                .quality(50)
                .prefixes(List.of(GearModifier.of(IRONCLAD, "Ironclad", ModifierType.PREFIX, "block_damage_reduction", "percent", 5.0)))
                .suffixes(List.of(GearModifier.of(OF_THE_FORTRESS, "of the Fortress", ModifierType.SUFFIX, "armor", "flat", 10.0)))
                .baseItemId("Armor_Iron_Head")
                .build();
    }

    private GearData createClothHelmet() {
        return GearData.builder()
                .level(50)
                .rarity(GearRarity.RARE)
                .quality(50)
                .prefixes(List.of(GearModifier.of(FIREPROOF, "Fireproof", ModifierType.PREFIX, "fire_resistance", "percent", 5.0)))
                .suffixes(List.of(GearModifier.of(OF_THE_SAGE, "of the Sage", ModifierType.SUFFIX, "max_mana", "flat", 10.0)))
                .baseItemId("Armor_Cloth_Cotton_Head")
                .build();
    }

    private GearData createSword() {
        return GearData.builder()
                .level(50)
                .rarity(GearRarity.RARE)
                .quality(50)
                .prefixes(List.of(GearModifier.of(SHARP, "Sharp", ModifierType.PREFIX, "physical_damage", "flat", 10.0)))
                .suffixes(List.of(GearModifier.of(VAMPIRIC, "Vampiric", ModifierType.SUFFIX, "life_steal", "percent", 5.0)))
                .baseItemId("Weapon_Sword_Iron")
                .build();
    }

    private GearData createLegacyArmorWithoutBaseId() {
        // Legacy item: has armor implicit but no baseItemId
        return new GearData(
                null, 50, GearRarity.RARE, 50,
                List.of(GearModifier.of(IRONCLAD, "Ironclad", ModifierType.PREFIX, "block_damage_reduction", "percent", 5.0)),
                List.of(GearModifier.of(OF_THE_FORTRESS, "of the Fortress", ModifierType.SUFFIX, "armor", "flat", 10.0)),
                false,
                null,
                ArmorImplicit.of("armor", 50.0, 80.0, 65.0),
                null, // no baseItemId — legacy item
                null, List.of(), 0
        );
    }

    private GearData createLegacyWeaponWithoutBaseId() {
        return new GearData(
                null, 50, GearRarity.RARE, 50,
                List.of(GearModifier.of(SHARP, "Sharp", ModifierType.PREFIX, "physical_damage", "flat", 10.0)),
                List.of(GearModifier.of(VAMPIRIC, "Vampiric", ModifierType.SUFFIX, "life_steal", "percent", 5.0)),
                false,
                WeaponImplicit.of("physical_damage", 100.0, 150.0, 125.0),
                null,
                null, // no baseItemId
                null, List.of(), 0
        );
    }

    // ═══════════════════════════════════════════════════════════════════
    // TESTS: MODIFIER POOL FILTERING VIA ModifierPool DIRECTLY
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("ModifierPool two-stage filtering")
    class ModifierPoolFiltering {

        private ModifierPool pool;

        @BeforeEach
        void setUp() {
            pool = new ModifierPool(modifierConfig, balanceConfig, equipmentStatConfig);
        }

        @Test
        @DisplayName("Plate helmet should only get plate-head prefixes")
        void plateHelmetPrefixes() {
            List<GearModifier> prefixes = pool.rollPrefixes(10, 50, "head", GearRarity.RARE, EquipmentType.PLATE_HEAD);
            Set<String> ids = prefixes.stream().map(GearModifier::id).collect(Collectors.toSet());

            // Should only contain ironclad (the only plate_head prefix)
            assertTrue(ids.stream().allMatch(id -> id.equals(IRONCLAD)),
                    "Plate helmet prefixes should only be ironclad, got: " + ids);
            assertFalse(ids.contains(SHARP), "Plate helmet must NOT get weapon prefix 'sharp'");
            assertFalse(ids.contains(ARCANE), "Plate helmet must NOT get weapon prefix 'arcane'");
        }

        @Test
        @DisplayName("Plate helmet should only get plate-head suffixes")
        void plateHelmetSuffixes() {
            List<GearModifier> suffixes = pool.rollSuffixes(10, 50, "head", GearRarity.RARE, EquipmentType.PLATE_HEAD);
            Set<String> ids = suffixes.stream().map(GearModifier::id).collect(Collectors.toSet());

            Set<String> allowed = Set.of(OF_THE_FORTRESS, OF_THE_WHALE);
            assertTrue(allowed.containsAll(ids),
                    "Plate helmet suffixes should be " + allowed + ", got: " + ids);
            assertFalse(ids.contains(VAMPIRIC), "Plate helmet must NOT get weapon suffix 'vampiric'");
            assertFalse(ids.contains(OF_THE_SAGE), "Plate helmet must NOT get cloth suffix 'of_the_sage'");
        }

        @Test
        @DisplayName("Cloth helmet should only get cloth-head prefixes")
        void clothHelmetPrefixes() {
            List<GearModifier> prefixes = pool.rollPrefixes(10, 50, "head", GearRarity.RARE, EquipmentType.CLOTH_HEAD);
            Set<String> ids = prefixes.stream().map(GearModifier::id).collect(Collectors.toSet());

            assertTrue(ids.stream().allMatch(id -> id.equals(FIREPROOF)),
                    "Cloth helmet prefixes should only be fireproof, got: " + ids);
        }

        @Test
        @DisplayName("Sword should only get sword weapon prefixes")
        void swordPrefixes() {
            List<GearModifier> prefixes = pool.rollPrefixes(10, 50, "weapon", GearRarity.RARE, EquipmentType.SWORD);
            Set<String> ids = prefixes.stream().map(GearModifier::id).collect(Collectors.toSet());

            Set<String> allowed = Set.of(SHARP, HEAVY);
            assertTrue(allowed.containsAll(ids),
                    "Sword prefixes should be " + allowed + ", got: " + ids);
            assertFalse(ids.contains(ARCANE), "Sword must NOT get staff prefix 'arcane'");
            assertFalse(ids.contains(IRONCLAD), "Sword must NOT get armor prefix 'ironclad'");
        }

        @Test
        @DisplayName("Staff should only get magic prefixes")
        void staffPrefixes() {
            List<GearModifier> prefixes = pool.rollPrefixes(10, 50, "weapon", GearRarity.RARE, EquipmentType.STAFF);
            Set<String> ids = prefixes.stream().map(GearModifier::id).collect(Collectors.toSet());

            assertTrue(ids.stream().allMatch(id -> id.equals(ARCANE)),
                    "Staff prefixes should only be arcane, got: " + ids);
            assertFalse(ids.contains(SHARP), "Staff must NOT get physical prefix 'sharp'");
        }

        @Test
        @DisplayName("Weapon-slot modifiers cannot appear on armor slots")
        void weaponModsCannotAppearOnArmorSlots() {
            // Even without equipment type filtering, slot filter should block weapon mods on head
            List<GearModifier> prefixes = pool.rollPrefixes(10, 50, "head", GearRarity.RARE, null);
            Set<String> ids = prefixes.stream().map(GearModifier::id).collect(Collectors.toSet());

            // With null equipment type, ALL head-slot prefixes pass, but weapon-only prefixes don't
            assertFalse(ids.contains(SHARP), "Weapon-only prefix 'sharp' must not appear on 'head' slot");
            assertFalse(ids.contains(HEAVY), "Weapon-only prefix 'heavy' must not appear on 'head' slot");
            assertFalse(ids.contains(ARCANE), "Weapon-only prefix 'arcane' must not appear on 'head' slot");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // TESTS: STONE REROLL INTEGRATION
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Stone reroll respects filtering")
    class StoneRerollFiltering {

        @Test
        @DisplayName("Alterverse Shard on plate helmet produces only plate-head modifiers")
        void alterverseShardOnPlateHelmet() {
            GearData helmet = createPlateHelmet();

            for (int i = 0; i < 50; i++) {
                StoneActionResult result = registry.execute(StoneType.ALTERVERSE_SHARD, helmet, new Random(i));
                assertTrue(result.success(), "Alterverse Shard should succeed on helmet");

                GearData modified = (GearData) result.modifiedItem();
                assertNotNull(modified);

                // Check all prefixes are armor-appropriate (head slot)
                for (GearModifier prefix : modified.prefixes()) {
                    assertTrue(ARMOR_PREFIXES.contains(prefix.id()),
                            "Plate helmet got weapon prefix '" + prefix.id() + "' on iteration " + i);
                }

                // Check all suffixes are armor-appropriate (head slot)
                for (GearModifier suffix : modified.suffixes()) {
                    assertTrue(ARMOR_SUFFIXES.contains(suffix.id()),
                            "Plate helmet got weapon suffix '" + suffix.id() + "' on iteration " + i);
                }
            }
        }

        @Test
        @DisplayName("Alterverse Shard on sword produces only weapon modifiers")
        void alterverseShardOnSword() {
            GearData sword = createSword();

            for (int i = 0; i < 50; i++) {
                StoneActionResult result = registry.execute(StoneType.ALTERVERSE_SHARD, sword, new Random(i));
                assertTrue(result.success(), "Alterverse Shard should succeed on sword");

                GearData modified = (GearData) result.modifiedItem();
                assertNotNull(modified);

                for (GearModifier prefix : modified.prefixes()) {
                    assertTrue(WEAPON_PREFIXES.contains(prefix.id()),
                            "Sword got armor prefix '" + prefix.id() + "' on iteration " + i);
                }

                for (GearModifier suffix : modified.suffixes()) {
                    assertTrue(WEAPON_SUFFIXES.contains(suffix.id()),
                            "Sword got armor suffix '" + suffix.id() + "' on iteration " + i);
                }
            }
        }

        @Test
        @DisplayName("Gaia's Gift on plate helmet adds only plate-head modifiers")
        void gaiasGiftOnPlateHelmet() {
            // Create helmet with room for more modifiers
            GearData helmet = GearData.builder()
                    .level(50)
                    .rarity(GearRarity.EPIC) // allows 4 modifiers
                    .quality(50)
                    .prefixes(List.of(GearModifier.of(IRONCLAD, "Ironclad", ModifierType.PREFIX, "block_damage_reduction", "percent", 5.0)))
                    .suffixes(List.of())
                    .baseItemId("Armor_Iron_Head")
                    .build();

            for (int i = 0; i < 50; i++) {
                StoneActionResult result = registry.execute(StoneType.GAIAS_GIFT, helmet, new Random(i));
                if (!result.success()) continue; // May fail if no compatible mods — that's OK

                GearData modified = (GearData) result.modifiedItem();
                for (GearModifier mod : modified.allModifiers()) {
                    boolean isArmorMod = ARMOR_PREFIXES.contains(mod.id()) || ARMOR_SUFFIXES.contains(mod.id());
                    assertTrue(isArmorMod,
                            "Plate helmet got non-armor modifier '" + mod.id() + "' on iteration " + i);
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // TESTS: LEGACY ITEM FALLBACK
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Legacy items without baseItemId")
    class LegacyItemFallback {

        @Test
        @DisplayName("Legacy armor (null baseItemId + armor implicit) should NOT get weapon modifiers")
        void legacyArmorDoesNotGetWeaponMods() {
            GearData legacyArmor = createLegacyArmorWithoutBaseId();

            for (int i = 0; i < 50; i++) {
                StoneActionResult result = registry.execute(StoneType.ALTERVERSE_SHARD, legacyArmor, new Random(i));
                assertTrue(result.success(), "Alterverse Shard should succeed on legacy armor");

                GearData modified = (GearData) result.modifiedItem();
                assertNotNull(modified);

                // CRITICAL: No weapon modifiers on armor, even without baseItemId
                for (GearModifier prefix : modified.prefixes()) {
                    assertFalse(WEAPON_PREFIXES.contains(prefix.id()),
                            "Legacy armor got WEAPON prefix '" + prefix.id() + "' — fallback is broken! Iteration " + i);
                }
                for (GearModifier suffix : modified.suffixes()) {
                    assertFalse(WEAPON_SUFFIXES.contains(suffix.id()),
                            "Legacy armor got WEAPON suffix '" + suffix.id() + "' — fallback is broken! Iteration " + i);
                }
            }
        }

        @Test
        @DisplayName("Legacy weapon (null baseItemId + weapon implicit) should get weapon modifiers")
        void legacyWeaponGetsWeaponMods() {
            GearData legacyWeapon = createLegacyWeaponWithoutBaseId();

            for (int i = 0; i < 50; i++) {
                StoneActionResult result = registry.execute(StoneType.ALTERVERSE_SHARD, legacyWeapon, new Random(i));
                assertTrue(result.success(), "Alterverse Shard should succeed on legacy weapon");

                GearData modified = (GearData) result.modifiedItem();
                assertNotNull(modified);

                // Legacy weapon with weapon implicit should still get weapon mods
                for (GearModifier prefix : modified.prefixes()) {
                    assertFalse(ARMOR_PREFIXES.contains(prefix.id()),
                            "Legacy weapon got ARMOR prefix '" + prefix.id() + "' on iteration " + i);
                }
            }
        }
    }
}
