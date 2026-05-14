package io.github.larsonix.trailoforbis.loot.consumable;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * Configuration model for the consumable loot system.
 *
 * <p>Loaded from {@code consumable-loot.yml} via SnakeYAML.
 * Defines curated food and potion item pools per rarity tier,
 * container drop settings, and exclusion lists.
 */
public final class ConsumableLootConfig {

    private boolean enabled = true;
    private Map<String, TierList> foodTiers = new LinkedHashMap<>();
    private Map<String, TierList> potionTiers = new LinkedHashMap<>();
    private List<String> excludedItems = new ArrayList<>();
    private ContainerDrops containerDrops = new ContainerDrops();

    // =========================================================================
    // GETTERS
    // =========================================================================

    public boolean isEnabled() {
        return enabled;
    }

    @Nonnull
    public Map<String, TierList> getFoodTiers() {
        return foodTiers;
    }

    @Nonnull
    public Map<String, TierList> getPotionTiers() {
        return potionTiers;
    }

    @Nonnull
    public List<String> getExcludedItems() {
        return excludedItems;
    }

    @Nonnull
    public ContainerDrops getContainerDrops() {
        return containerDrops;
    }

    // =========================================================================
    // SETTERS (SnakeYAML)
    // =========================================================================

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setFood_tiers(Map<String, TierList> foodTiers) {
        this.foodTiers = foodTiers != null ? foodTiers : new LinkedHashMap<>();
    }

    public void setPotion_tiers(Map<String, TierList> potionTiers) {
        this.potionTiers = potionTiers != null ? potionTiers : new LinkedHashMap<>();
    }

    public void setExcluded_items(List<String> excludedItems) {
        this.excludedItems = excludedItems != null ? excludedItems : new ArrayList<>();
    }

    public void setContainer_drops(ContainerDrops containerDrops) {
        this.containerDrops = containerDrops != null ? containerDrops : new ContainerDrops();
    }

    // =========================================================================
    // TIER LIST
    // =========================================================================

    /**
     * A list of consumable items for one rarity tier.
     */
    public static final class TierList {
        private int minLevel = 1;
        private List<ItemEntry> items = new ArrayList<>();

        public int getMinLevel() {
            return minLevel;
        }

        @Nonnull
        public List<ItemEntry> getItems() {
            return items;
        }

        public void setMin_level(int minLevel) {
            this.minLevel = Math.max(1, minLevel);
        }

        public void setItems(List<ItemEntry> items) {
            this.items = items != null ? items : new ArrayList<>();
        }
    }

    /**
     * A single item entry in a tier.
     */
    public static final class ItemEntry {
        private String id = "";
        private List<Integer> stack = List.of(1, 1);

        @Nonnull
        public String getId() {
            return id;
        }

        public int getMinStack() {
            return stack.size() > 0 ? Math.max(1, stack.get(0)) : 1;
        }

        public int getMaxStack() {
            return stack.size() > 1 ? Math.max(getMinStack(), stack.get(1)) : getMinStack();
        }

        public void setId(String id) {
            this.id = id != null ? id : "";
        }

        public void setStack(List<Integer> stack) {
            this.stack = stack != null ? stack : List.of(1, 1);
        }
    }

    // =========================================================================
    // CONTAINER DROPS
    // =========================================================================

    /**
     * Container-specific drop settings for consumables.
     */
    public static final class ContainerDrops {
        private boolean enabled = true;
        private double baseChance = 0.40;
        private int maxPerContainer = 2;
        private double foodWeight = 0.5;
        private double potionWeight = 0.5;

        public boolean isEnabled() {
            return enabled;
        }

        public double getBaseChance() {
            return baseChance;
        }

        public int getMaxPerContainer() {
            return maxPerContainer;
        }

        public double getFoodWeight() {
            return foodWeight;
        }

        public double getPotionWeight() {
            return potionWeight;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public void setBase_chance(double baseChance) {
            this.baseChance = baseChance;
        }

        public void setMax_per_container(int maxPerContainer) {
            this.maxPerContainer = Math.max(0, maxPerContainer);
        }

        public void setFood_weight(double foodWeight) {
            this.foodWeight = foodWeight;
        }

        public void setPotion_weight(double potionWeight) {
            this.potionWeight = potionWeight;
        }
    }

    // =========================================================================
    // FACTORY
    // =========================================================================

    @Nonnull
    public static ConsumableLootConfig createDefaults() {
        return new ConsumableLootConfig();
    }
}
