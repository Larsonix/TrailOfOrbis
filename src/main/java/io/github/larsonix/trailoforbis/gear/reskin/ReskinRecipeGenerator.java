package io.github.larsonix.trailoforbis.gear.reskin;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.BenchRequirement;
import com.hypixel.hytale.protocol.BenchType;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;

import io.github.larsonix.trailoforbis.gear.loot.DiscoveredItem;
import io.github.larsonix.trailoforbis.gear.loot.DynamicLootRegistry;
import io.github.larsonix.trailoforbis.gear.loot.LootGenerator.EquipmentSlot;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Generates StructuralCrafting recipes for the Builder's Workbench at startup.
 *
 * <p>Grouping strategy:
 * <ul>
 *   <li><b>Armor</b> (head, chest, legs, hands): grouped by (slot, quality).
 *       A player with any Rare helmet sees ALL Rare helmets as options.</li>
 *   <li><b>Weapons</b> (weapon, off_hand): grouped by (slot, quality, weapon type).
 *       A player with Rare daggers sees only other Rare daggers, not swords.</li>
 * </ul>
 *
 * <p>Recipes are registered via {@code CraftingRecipe.getAssetStore().loadAssets()},
 * which auto-triggers {@code BenchRecipeRegistry} registration through the engine's
 * {@code LoadedAssetsEvent} pipeline.
 */
public final class ReskinRecipeGenerator {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Recipe ID prefix — used by ReskinDataPreserver to identify reskin recipes */
    public static final String RECIPE_ID_PREFIX = "rpg_reskin_";

    /** Pack key for AssetStore registration */
    private static final String ASSET_PACK_KEY = "TrailOfOrbis:Reskin";

    /** The Builder's Workbench bench ID */
    private static final String BUILDERS_BENCH_ID = "Builders";

    /** Category required for recipes to be selectable in the Builder's Workbench UI */
    private static final String[] BENCH_CATEGORIES = new String[]{ "WoodPlanks" };

    /** Cap per group — must stay well under 64 (the StructuralCrafting option slot limit)
     *  to leave room for any other recipes that match the same input. */
    private static final int MAX_RECIPES_PER_GROUP = 50;

    private static final Field RECIPE_ID_FIELD;

    static {
        try {
            RECIPE_ID_FIELD = CraftingRecipe.class.getDeclaredField("id");
            RECIPE_ID_FIELD.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("Cannot access CraftingRecipe.id field", e);
        }
    }

    private final DynamicLootRegistry lootRegistry;
    private final ReskinResourceTypeRegistry resourceTypeRegistry;

    public ReskinRecipeGenerator(@Nonnull DynamicLootRegistry lootRegistry,
                                 @Nonnull ReskinResourceTypeRegistry resourceTypeRegistry) {
        this.lootRegistry = Objects.requireNonNull(lootRegistry);
        this.resourceTypeRegistry = Objects.requireNonNull(resourceTypeRegistry);
    }

    /**
     * Generates and registers all reskin recipes.
     *
     * <p>Must be called after {@link DynamicLootRegistry#discoverItems()}.
     *
     * @return Total number of recipes registered
     */
    public int generate() {
        List<CraftingRecipe> allRecipes = new ArrayList<>();
        int groupCount = 0;

        String[] qualityTiers = {"Common", "Uncommon", "Rare", "Epic", "Legendary", "Mythic"};

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            boolean isWeaponSlot = (slot == EquipmentSlot.WEAPON || slot == EquipmentSlot.OFF_HAND);

            for (String qualityId : qualityTiers) {
                Map<String, List<DiscoveredItem>> categories =
                        lootRegistry.getItemsByQualityAndCategory(slot, qualityId);

                if (isWeaponSlot) {
                    // WEAPONS: group by (slot, quality, weapon type) — daggers stay with daggers
                    for (Map.Entry<String, List<DiscoveredItem>> entry : categories.entrySet()) {
                        String category = entry.getKey();
                        List<DiscoveredItem> items = entry.getValue();
                        if (items.size() < 2) {
                            continue;
                        }
                        if (items.size() > MAX_RECIPES_PER_GROUP) {
                            LOGGER.atWarning().log("Reskin group %s/%s/%s has %d items, capping at %d",
                                    slot, qualityId, category, items.size(), MAX_RECIPES_PER_GROUP);
                            items = items.subList(0, MAX_RECIPES_PER_GROUP);
                        }
                        String resourceTypeId = resourceTypeRegistry.register(slot, qualityId, category);
                        allRecipes.addAll(createRecipesForGroup(resourceTypeId, items));
                        groupCount++;
                    }
                } else {
                    // ARMOR: group by (slot, quality) — all Rare helmets together
                    List<DiscoveredItem> allItemsInGroup = new ArrayList<>();
                    for (List<DiscoveredItem> categoryItems : categories.values()) {
                        allItemsInGroup.addAll(categoryItems);
                    }
                    if (allItemsInGroup.size() < 2) {
                        continue;
                    }
                    if (allItemsInGroup.size() > MAX_RECIPES_PER_GROUP) {
                        LOGGER.atWarning().log("Reskin group %s/%s has %d items, capping at %d",
                                slot, qualityId, allItemsInGroup.size(), MAX_RECIPES_PER_GROUP);
                        allItemsInGroup = allItemsInGroup.subList(0, MAX_RECIPES_PER_GROUP);
                    }
                    String resourceTypeId = resourceTypeRegistry.register(slot, qualityId);
                    allRecipes.addAll(createRecipesForGroup(resourceTypeId, allItemsInGroup));
                    groupCount++;
                }
            }
        }

        if (allRecipes.isEmpty()) {
            LOGGER.atInfo().log("No reskin recipes generated (no groups with 2+ items)");
            return 0;
        }

        // Register all recipes via AssetStore — triggers LoadedAssetsEvent → BenchRecipeRegistry
        try {
            CraftingRecipe.getAssetStore().loadAssets(ASSET_PACK_KEY, allRecipes);
            LOGGER.atInfo().log("Registered %d reskin recipes across %d groups",
                    allRecipes.size(), groupCount);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to register reskin recipes");
            return 0;
        }

        return allRecipes.size();
    }

    /**
     * Creates recipes for all items in a (slot, quality, category) group.
     */
    @Nonnull
    private List<CraftingRecipe> createRecipesForGroup(
            @Nonnull String resourceTypeId,
            @Nonnull List<DiscoveredItem> items) {

        List<CraftingRecipe> recipes = new ArrayList<>(items.size());

        BenchRequirement benchReq = new BenchRequirement(
                BenchType.StructuralCrafting, BUILDERS_BENCH_ID, BENCH_CATEGORIES, 0);
        BenchRequirement[] benchReqs = new BenchRequirement[]{ benchReq };

        // Input: any item with the reskin ResourceType
        MaterialQuantity input = new MaterialQuantity(
                null, resourceTypeId, null, 1, null);
        MaterialQuantity[] inputs = new MaterialQuantity[]{ input };

        for (DiscoveredItem target : items) {
            // Output: the specific target item
            MaterialQuantity output = new MaterialQuantity(
                    target.itemId(), null, null, 1, null);
            MaterialQuantity[] outputs = new MaterialQuantity[]{ output };

            CraftingRecipe recipe = new CraftingRecipe(
                    inputs,         // input
                    output,         // primaryOutput
                    outputs,        // outputs
                    1,              // outputQuantity
                    benchReqs,      // benchRequirement
                    0f,             // timeSeconds (instant)
                    false,          // knowledgeRequired
                    1               // requiredMemoriesLevel
            );

            // Set the recipe ID via reflection
            String recipeId = RECIPE_ID_PREFIX + target.itemId();
            try {
                RECIPE_ID_FIELD.set(recipe, recipeId);
            } catch (IllegalAccessException e) {
                LOGGER.atWarning().withCause(e).log(
                        "Failed to set recipe ID for %s", target.itemId());
                continue;
            }

            recipes.add(recipe);
        }

        return recipes;
    }
}
