package io.github.larsonix.trailoforbis.gear.loot;

import com.hypixel.hytale.logger.HytaleLogger;

import io.github.larsonix.trailoforbis.elemental.ElementType;
import io.github.larsonix.trailoforbis.gear.loot.LootCategory.ImplicitEntry;
import io.github.larsonix.trailoforbis.gear.loot.LootCategory.ImplicitRoll;
import io.github.larsonix.trailoforbis.gear.loot.LootCategory.SuperCategory;
import io.github.larsonix.trailoforbis.gear.model.ArmorMaterial;
import io.github.larsonix.trailoforbis.gear.model.EquipmentType;
import io.github.larsonix.trailoforbis.gear.model.EquipmentType.ArmorSlot;
import io.github.larsonix.trailoforbis.gear.model.WeaponType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

/**
 * Configuration and roller for the implicit-driven loot category system.
 *
 * <p>Manages the three-tier selection pipeline:
 * <ol>
 *   <li>Super-category: WEAPON / ARMOR / OFFHAND (weighted)</li>
 *   <li>Category: specific type within super (weighted)</li>
 *   <li>Implicit: rolled from category's pool (weighted)</li>
 * </ol>
 *
 * <p>Built from the {@code categories} section of {@code loot-discovery.yml}.
 * If no categories are configured, hardcoded defaults are used.
 */
public final class LootCategoryConfig {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // Super-category weights
    private final Map<SuperCategory, Double> superWeights;

    // Categories grouped by super-category
    private final Map<SuperCategory, List<LootCategory>> categoriesBySuper;

    // All categories for diagnostics
    private final List<LootCategory> allCategories;

    // Implicit → ArmorMaterial mapping (for skin lookup)
    private final Map<String, ArmorMaterial> implicitMaterialMap;

    // Implicit → ElementType mapping (for weapon element resolution)
    private static final Map<String, ElementType> ELEMENT_MAP;

    static {
        Map<String, ElementType> map = new HashMap<>();
        for (ElementType e : ElementType.values()) {
            map.put(e.name().toLowerCase(), e);
        }
        ELEMENT_MAP = Collections.unmodifiableMap(map);
    }

    private LootCategoryConfig(
            Map<SuperCategory, Double> superWeights,
            Map<SuperCategory, List<LootCategory>> categoriesBySuper,
            List<LootCategory> allCategories,
            Map<String, ArmorMaterial> implicitMaterialMap
    ) {
        this.superWeights = Collections.unmodifiableMap(superWeights);
        this.categoriesBySuper = Collections.unmodifiableMap(categoriesBySuper);
        this.allCategories = Collections.unmodifiableList(allCategories);
        this.implicitMaterialMap = Collections.unmodifiableMap(implicitMaterialMap);
    }

    // =========================================================================
    // THREE-TIER ROLLER
    // =========================================================================

    /**
     * Rolls a super-category using configured weights.
     */
    @Nonnull
    public SuperCategory rollSuperCategory(@Nonnull Random random) {
        double total = 0;
        for (double w : superWeights.values()) total += w;
        if (total <= 0) return SuperCategory.WEAPON;

        double roll = random.nextDouble() * total;
        double cumulative = 0;
        for (Map.Entry<SuperCategory, Double> entry : superWeights.entrySet()) {
            cumulative += entry.getValue();
            if (roll < cumulative) return entry.getKey();
        }
        return SuperCategory.WEAPON;
    }

    /**
     * Rolls a category within a super-category using configured weights.
     */
    @Nullable
    public LootCategory rollCategory(@Nonnull SuperCategory superCat, @Nonnull Random random) {
        List<LootCategory> pool = categoriesBySuper.getOrDefault(superCat, List.of());
        if (pool.isEmpty()) return null;

        double total = 0;
        for (LootCategory c : pool) total += c.weight();
        if (total <= 0) return pool.getFirst();

        double roll = random.nextDouble() * total;
        double cumulative = 0;
        for (LootCategory c : pool) {
            cumulative += c.weight();
            if (roll < cumulative) return c;
        }
        return pool.getLast();
    }

    /**
     * Rolls an implicit from a category's pool and resolves the full item identity.
     *
     * <p>This is THE identity-defining moment in the loot pipeline. The implicit
     * determines:
     * <ul>
     *   <li>For armor: which material (plate/leather/cloth) → which skins + which mods</li>
     *   <li>For weapons: which damage element (physical/fire/etc.) → implicit damage type</li>
     * </ul>
     *
     * @return The resolved implicit roll with all identity information, or null if pool is empty
     */
    @Nullable
    public ImplicitRoll rollImplicit(@Nonnull LootCategory category, @Nonnull Random random) {
        List<ImplicitEntry> pool = category.implicitPool();
        if (pool.isEmpty()) return null;

        // Roll from the implicit pool
        double total = 0;
        for (ImplicitEntry e : pool) total += e.weight();
        if (total <= 0) return null;

        double roll = random.nextDouble() * total;
        double cumulative = 0;
        ImplicitEntry selected = pool.getLast();
        for (ImplicitEntry e : pool) {
            cumulative += e.weight();
            if (roll < cumulative) {
                selected = e;
                break;
            }
        }

        // Resolve the full item identity from the implicit
        return resolveImplicit(selected, category);
    }

    /**
     * Resolves the equipment type, skin material, and weapon type from an implicit roll.
     */
    @Nonnull
    private ImplicitRoll resolveImplicit(@Nonnull ImplicitEntry entry, @Nonnull LootCategory category) {
        if (category.isArmor()) {
            return resolveArmorImplicit(entry, category);
        } else {
            return resolveWeaponImplicit(entry, category);
        }
    }

    private ImplicitRoll resolveArmorImplicit(ImplicitEntry entry, LootCategory category) {
        // Determine armor material from implicit type
        ArmorMaterial material = entry.resolvedMaterial();
        if (material == null) {
            material = implicitMaterialMap.getOrDefault(entry.implicitType(), ArmorMaterial.PLATE);
        }

        // Resolve EquipmentType from (material, slot)
        ArmorSlot armorSlot = category.armorSlot();
        if (armorSlot == null) {
            armorSlot = ArmorSlot.fromSlotName(category.slotString()).orElse(ArmorSlot.CHEST);
        }
        EquipmentType equipType = EquipmentType.resolve(null, material, armorSlot);

        return new ImplicitRoll(entry, equipType, material, null, null, category.slotString());
    }

    private ImplicitRoll resolveWeaponImplicit(ImplicitEntry entry, LootCategory category) {
        WeaponType weaponType = category.weaponType();
        if (weaponType == null) weaponType = WeaponType.UNKNOWN;

        // Resolve element from implicit type
        ElementType element = entry.resolvedElement();
        if (element == null && !entry.isPhysical()) {
            element = ELEMENT_MAP.get(entry.implicitType().toLowerCase());
        }

        // Resolve EquipmentType from weapon type
        EquipmentType equipType = EquipmentType.resolve(weaponType, null, null);

        return new ImplicitRoll(entry, equipType, null, weaponType, element, category.slotString());
    }

    // =========================================================================
    // ACCESSORS
    // =========================================================================

    public List<LootCategory> getAllCategories() {
        return allCategories;
    }

    public Map<SuperCategory, Double> getSuperWeights() {
        return superWeights;
    }

    public Map<SuperCategory, List<LootCategory>> getCategoriesBySuper() {
        return categoriesBySuper;
    }

    public Map<String, ArmorMaterial> getImplicitMaterialMap() {
        return implicitMaterialMap;
    }

    // =========================================================================
    // BUILDER — parses from YAML maps
    // =========================================================================

    /**
     * Builds a LootCategoryConfig from raw YAML maps.
     *
     * @param superWeightsMap  The super_category_weights section (e.g., {weapon: 40, armor: 45, offhand: 15})
     * @param categoriesMap    The categories section (map of id → {super, weight, implicits})
     * @param materialMap      The implicit_material_map section (e.g., {armor: PLATE, evasion: LEATHER})
     * @return A fully resolved config
     */
    @Nonnull
    @SuppressWarnings("unchecked")
    public static LootCategoryConfig fromYaml(
            @Nullable Map<String, Object> superWeightsMap,
            @Nullable Map<String, Object> categoriesMap,
            @Nullable Map<String, String> materialMap
    ) {
        // Parse super-category weights
        Map<SuperCategory, Double> superWeights = new EnumMap<>(SuperCategory.class);
        if (superWeightsMap != null) {
            for (Map.Entry<String, Object> entry : superWeightsMap.entrySet()) {
                try {
                    SuperCategory sc = SuperCategory.valueOf(entry.getKey().toUpperCase());
                    superWeights.put(sc, ((Number) entry.getValue()).doubleValue());
                } catch (Exception e) {
                    LOGGER.atWarning().log("Unknown super-category: %s", entry.getKey());
                }
            }
        }
        // Defaults if missing
        superWeights.putIfAbsent(SuperCategory.WEAPON, 40.0);
        superWeights.putIfAbsent(SuperCategory.ARMOR, 45.0);
        superWeights.putIfAbsent(SuperCategory.OFFHAND, 15.0);

        // Parse implicit → material mapping
        Map<String, ArmorMaterial> implicitMaterials = new HashMap<>();
        if (materialMap != null) {
            for (Map.Entry<String, String> entry : materialMap.entrySet()) {
                try {
                    ArmorMaterial mat = ArmorMaterial.valueOf(entry.getValue().toUpperCase());
                    implicitMaterials.put(entry.getKey().toLowerCase(), mat);
                } catch (IllegalArgumentException e) {
                    // Special entries like SHIELD, SPELLBOOK are not ArmorMaterial — skip
                    LOGGER.atFine().log("Non-armor implicit material mapping: %s → %s",
                            entry.getKey(), entry.getValue());
                }
            }
        }
        // Defaults
        implicitMaterials.putIfAbsent("armor", ArmorMaterial.PLATE);
        implicitMaterials.putIfAbsent("evasion", ArmorMaterial.LEATHER);
        implicitMaterials.putIfAbsent("energy_shield", ArmorMaterial.CLOTH);
        implicitMaterials.putIfAbsent("max_health", ArmorMaterial.PLATE);

        // Parse categories
        List<LootCategory> allCategories = new ArrayList<>();
        Map<SuperCategory, List<LootCategory>> bySuper = new EnumMap<>(SuperCategory.class);
        for (SuperCategory sc : SuperCategory.values()) {
            bySuper.put(sc, new ArrayList<>());
        }

        if (categoriesMap != null && !categoriesMap.isEmpty()) {
            for (Map.Entry<String, Object> entry : categoriesMap.entrySet()) {
                String categoryId = entry.getKey().toLowerCase();
                if (!(entry.getValue() instanceof Map)) {
                    LOGGER.atWarning().log("Invalid category config for '%s' — expected map", categoryId);
                    continue;
                }
                Map<String, Object> catMap = (Map<String, Object>) entry.getValue();

                // Parse super-category
                String superStr = (String) catMap.getOrDefault("super", "weapon");
                SuperCategory superCat;
                try {
                    superCat = SuperCategory.valueOf(superStr.toUpperCase());
                } catch (IllegalArgumentException e) {
                    LOGGER.atWarning().log("Unknown super-category '%s' for category '%s', defaulting to WEAPON",
                            superStr, categoryId);
                    superCat = SuperCategory.WEAPON;
                }

                // Parse weight
                double weight = catMap.containsKey("weight")
                        ? ((Number) catMap.get("weight")).doubleValue()
                        : 1.0;

                // Derive slot string
                String slotString = deriveSlotString(categoryId, superCat);

                // Parse implicits
                List<ImplicitEntry> implicitPool = new ArrayList<>();
                Object implicitsObj = catMap.get("implicits");
                if (implicitsObj instanceof Map) {
                    Map<String, Object> implicitMap = (Map<String, Object>) implicitsObj;
                    for (Map.Entry<String, Object> ie : implicitMap.entrySet()) {
                        String implicitType = ie.getKey().toLowerCase();
                        double implicitWeight = ((Number) ie.getValue()).doubleValue();

                        // Resolve material for armor implicits
                        ArmorMaterial resolvedMat = implicitMaterials.get(implicitType);

                        // Resolve element for weapon implicits
                        ElementType resolvedElement = ELEMENT_MAP.get(implicitType);

                        implicitPool.add(new ImplicitEntry(
                                implicitType, implicitWeight, resolvedMat, resolvedElement));
                    }
                }

                if (implicitPool.isEmpty()) {
                    LOGGER.atWarning().log("Category '%s' has no implicits — using physical default", categoryId);
                    implicitPool.add(ImplicitEntry.physical(100));
                }

                LootCategory cat = new LootCategory(categoryId, superCat, slotString, weight, implicitPool);
                allCategories.add(cat);
                bySuper.get(superCat).add(cat);
            }
        }

        // If no categories were configured, use defaults
        if (allCategories.isEmpty()) {
            LOGGER.atInfo().log("No loot categories configured — generating defaults");
            LootCategoryConfig defaults = createDefaults();
            return defaults;
        }

        LootCategoryConfig config = new LootCategoryConfig(superWeights, bySuper, allCategories, implicitMaterials);
        config.logSummary();
        return config;
    }

    /**
     * Derives the slot string from category id and super-category.
     */
    private static String deriveSlotString(String categoryId, SuperCategory superCat) {
        return switch (superCat) {
            case WEAPON -> "weapon";
            case OFFHAND -> "shield"; // GearGenerator expects "shield" for all offhand
            case ARMOR -> switch (categoryId) {
                case "head" -> "head";
                case "chest" -> "chest";
                case "legs" -> "legs";
                case "hands" -> "hands";
                default -> categoryId; // fallback
            };
        };
    }

    private void logSummary() {
        LOGGER.atInfo().log("Loot category config loaded: %d categories", allCategories.size());
        for (SuperCategory sc : SuperCategory.values()) {
            List<LootCategory> cats = categoriesBySuper.getOrDefault(sc, List.of());
            if (!cats.isEmpty()) {
                double totalWeight = cats.stream().mapToDouble(LootCategory::weight).sum();
                LOGGER.atInfo().log("  %s (weight=%.0f): %d categories (total cat weight=%.0f)",
                        sc, superWeights.getOrDefault(sc, 0.0), cats.size(), totalWeight);
                for (LootCategory c : cats) {
                    LOGGER.atInfo().log("    %s: weight=%.0f, implicits=%d",
                            c.id(), c.weight(), c.implicitPool().size());
                }
            }
        }
    }

    // =========================================================================
    // DEFAULTS
    // =========================================================================

    /**
     * Creates a default config with standard RPG weapon/armor categories.
     */
    @Nonnull
    public static LootCategoryConfig createDefaults() {
        Map<SuperCategory, Double> superWeights = new EnumMap<>(SuperCategory.class);
        superWeights.put(SuperCategory.WEAPON, 40.0);
        superWeights.put(SuperCategory.ARMOR, 45.0);
        superWeights.put(SuperCategory.OFFHAND, 15.0);

        Map<String, ArmorMaterial> implicitMaterials = new HashMap<>();
        implicitMaterials.put("armor", ArmorMaterial.PLATE);
        implicitMaterials.put("evasion", ArmorMaterial.LEATHER);
        implicitMaterials.put("energy_shield", ArmorMaterial.CLOTH);
        implicitMaterials.put("max_health", ArmorMaterial.PLATE);

        // Standard weapon implicit pool: physical dominant + small elemental chance
        List<ImplicitEntry> physicalWeaponPool = List.of(
                ImplicitEntry.physical(70),
                ImplicitEntry.elemental("fire", 5, ElementType.FIRE),
                ImplicitEntry.elemental("water", 5, ElementType.WATER),
                ImplicitEntry.elemental("lightning", 5, ElementType.LIGHTNING),
                ImplicitEntry.elemental("earth", 5, ElementType.EARTH),
                ImplicitEntry.elemental("wind", 5, ElementType.WIND),
                ImplicitEntry.elemental("void", 5, ElementType.VOID)
        );

        // Magic weapon pool: elemental only, no physical
        List<ImplicitEntry> magicWeaponPool = List.of(
                ImplicitEntry.elemental("fire", 17, ElementType.FIRE),
                ImplicitEntry.elemental("water", 17, ElementType.WATER),
                ImplicitEntry.elemental("lightning", 17, ElementType.LIGHTNING),
                ImplicitEntry.elemental("earth", 17, ElementType.EARTH),
                ImplicitEntry.elemental("wind", 16, ElementType.WIND),
                ImplicitEntry.elemental("void", 16, ElementType.VOID)
        );

        // Armor implicit pool: equal chance for all defense types
        List<ImplicitEntry> armorPool = List.of(
                ImplicitEntry.armor("armor", 30, ArmorMaterial.PLATE),
                ImplicitEntry.armor("evasion", 30, ArmorMaterial.LEATHER),
                ImplicitEntry.armor("energy_shield", 25, ArmorMaterial.CLOTH),
                ImplicitEntry.armor("max_health", 15, ArmorMaterial.PLATE)
        );

        List<LootCategory> all = new ArrayList<>();
        Map<SuperCategory, List<LootCategory>> bySuper = new EnumMap<>(SuperCategory.class);
        bySuper.put(SuperCategory.WEAPON, new ArrayList<>());
        bySuper.put(SuperCategory.ARMOR, new ArrayList<>());
        bySuper.put(SuperCategory.OFFHAND, new ArrayList<>());

        // Weapons
        addCategory(all, bySuper, "sword", SuperCategory.WEAPON, "weapon", 10, physicalWeaponPool);
        addCategory(all, bySuper, "axe", SuperCategory.WEAPON, "weapon", 8, physicalWeaponPool);
        addCategory(all, bySuper, "mace", SuperCategory.WEAPON, "weapon", 6, physicalWeaponPool);
        addCategory(all, bySuper, "dagger", SuperCategory.WEAPON, "weapon", 8, physicalWeaponPool);
        addCategory(all, bySuper, "longsword", SuperCategory.WEAPON, "weapon", 5, physicalWeaponPool);
        addCategory(all, bySuper, "battleaxe", SuperCategory.WEAPON, "weapon", 4, physicalWeaponPool);
        addCategory(all, bySuper, "spear", SuperCategory.WEAPON, "weapon", 5, physicalWeaponPool);
        addCategory(all, bySuper, "shortbow", SuperCategory.WEAPON, "weapon", 7, physicalWeaponPool);
        addCategory(all, bySuper, "crossbow", SuperCategory.WEAPON, "weapon", 4, physicalWeaponPool);
        addCategory(all, bySuper, "staff", SuperCategory.WEAPON, "weapon", 6, magicWeaponPool);
        addCategory(all, bySuper, "wand", SuperCategory.WEAPON, "weapon", 4, magicWeaponPool);

        // Armor
        addCategory(all, bySuper, "head", SuperCategory.ARMOR, "head", 20, armorPool);
        addCategory(all, bySuper, "chest", SuperCategory.ARMOR, "chest", 30, armorPool);
        addCategory(all, bySuper, "legs", SuperCategory.ARMOR, "legs", 25, armorPool);
        addCategory(all, bySuper, "hands", SuperCategory.ARMOR, "hands", 25, armorPool);

        // Offhand
        addCategory(all, bySuper, "shield", SuperCategory.OFFHAND, "shield", 60,
                List.of(ImplicitEntry.fixed("block_chance")));
        addCategory(all, bySuper, "spellbook", SuperCategory.OFFHAND, "shield", 40,
                List.of(ImplicitEntry.fixed("mana_regen")));

        LootCategoryConfig config = new LootCategoryConfig(superWeights, bySuper, all, implicitMaterials);
        LOGGER.atInfo().log("Created default loot category config with %d categories", all.size());
        return config;
    }

    private static void addCategory(
            List<LootCategory> all,
            Map<SuperCategory, List<LootCategory>> bySuper,
            String id, SuperCategory superCat, String slot, double weight,
            List<ImplicitEntry> implicits
    ) {
        LootCategory cat = new LootCategory(id, superCat, slot, weight, implicits);
        all.add(cat);
        bySuper.get(superCat).add(cat);
    }
}
