package io.github.larsonix.trailoforbis.maps.gateway;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Configuration for the Ancient Gateway upgrade system.
 *
 * <p>Defines upgrade tiers that gate which realm map levels a Portal_Device
 * can channel. Each tier requires specific materials to unlock and raises
 * the maximum realm level the gateway can open.
 *
 * <p>Loaded from the {@code gateway-upgrades} section of {@code realms.yml}.
 *
 * @see GatewayUpgradeManager
 */
public class GatewayUpgradeConfig {

    private boolean enabled = true;
    private final List<GatewayTier> tiers = new ArrayList<>();

    // ═══════════════════════════════════════════════════════════════════
    // TIER DEFINITION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * A single gateway upgrade tier.
     *
     * @param name Display name (e.g., "Iron Gateway")
     * @param maxRealmLevel Maximum realm map level this tier can channel (-1 = unlimited)
     * @param materials Materials required to upgrade TO this tier
     */
    public record GatewayTier(
            @Nonnull String name,
            int maxRealmLevel,
            @Nonnull List<TierMaterial> materials) {

        public GatewayTier {
            Objects.requireNonNull(name, "name cannot be null");
            Objects.requireNonNull(materials, "materials cannot be null");
            materials = List.copyOf(materials);
        }

        /**
         * @return true if this tier has no level cap (final tier)
         */
        public boolean isUnlimited() {
            return maxRealmLevel < 0;
        }
    }

    /**
     * A single material requirement for a tier upgrade.
     *
     * @param itemId Hytale item ID (e.g., "Ingredient_Bar_Iron")
     * @param count Number required
     */
    public record TierMaterial(
            @Nonnull String itemId,
            int count) {

        public TierMaterial {
            Objects.requireNonNull(itemId, "itemId cannot be null");
            if (count <= 0) {
                throw new IllegalArgumentException("count must be positive: " + count);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // ACCESSORS
    // ═══════════════════════════════════════════════════════════════════

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * @return Unmodifiable list of all tiers (index 0 = base tier, no materials)
     */
    @Nonnull
    public List<GatewayTier> getTiers() {
        return Collections.unmodifiableList(tiers);
    }

    /**
     * @return The tier at the given index, or null if out of range
     */
    public GatewayTier getTier(int tierIndex) {
        if (tierIndex < 0 || tierIndex >= tiers.size()) {
            return null;
        }
        return tiers.get(tierIndex);
    }

    /**
     * @return The next tier after the given index, or null if already at max
     */
    public GatewayTier getNextTier(int currentTierIndex) {
        return getTier(currentTierIndex + 1);
    }

    /**
     * @return Total number of tiers (including base tier 0)
     */
    public int getTierCount() {
        return tiers.size();
    }

    /**
     * @return The maximum tier index (highest upgrade level)
     */
    public int getMaxTierIndex() {
        return Math.max(0, tiers.size() - 1);
    }

    /**
     * Gets the max realm level for a given tier index.
     *
     * @param tierIndex The tier index
     * @return Max realm level, or Integer.MAX_VALUE if unlimited or invalid
     */
    public int getMaxRealmLevel(int tierIndex) {
        GatewayTier tier = getTier(tierIndex);
        if (tier == null || tier.isUnlimited()) {
            return Integer.MAX_VALUE;
        }
        return tier.maxRealmLevel();
    }

    public void addTier(@Nonnull GatewayTier tier) {
        Objects.requireNonNull(tier, "tier cannot be null");
        tiers.add(tier);
    }

    // ═══════════════════════════════════════════════════════════════════
    // DEFAULTS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Creates a config with the default tier progression.
     * Used when no gateway-upgrades section exists in realms.yml.
     */
    @Nonnull
    public static GatewayUpgradeConfig createDefault() {
        GatewayUpgradeConfig config = new GatewayUpgradeConfig();

        // Tier 0: Copper Gateway (base, free at spawn)
        config.addTier(new GatewayTier("Copper Gateway", 10, List.of()));

        // Tier 1: Iron Gateway
        config.addTier(new GatewayTier("Iron Gateway", 20, List.of(
            new TierMaterial("Ingredient_Bar_Iron", 15),
            new TierMaterial("Ingredient_Life_Essence", 5)
        )));

        // Tier 2: Gold Gateway
        config.addTier(new GatewayTier("Gold Gateway", 30, List.of(
            new TierMaterial("Ingredient_Bar_Gold", 12),
            new TierMaterial("Ingredient_Life_Essence", 8)
        )));

        // Tier 3: Cobalt Gateway
        config.addTier(new GatewayTier("Cobalt Gateway", 45, List.of(
            new TierMaterial("Ingredient_Bar_Cobalt", 10),
            new TierMaterial("Ingredient_Void_Essence", 10)
        )));

        // Tier 4: Thorium Gateway
        config.addTier(new GatewayTier("Thorium Gateway", 60, List.of(
            new TierMaterial("Ingredient_Bar_Thorium", 10),
            new TierMaterial("Ingredient_Life_Essence", 10),
            new TierMaterial("Ingredient_Void_Essence", 10)
        )));

        // Tier 5: Mithril Gateway
        config.addTier(new GatewayTier("Mithril Gateway", 80, List.of(
            new TierMaterial("Ingredient_Bar_Mithril", 8),
            new TierMaterial("Ingredient_Void_Essence", 15)
        )));

        // Tier 6: Adamantite Gateway (unlimited)
        config.addTier(new GatewayTier("Adamantite Gateway", -1, List.of(
            new TierMaterial("Ingredient_Bar_Adamantite", 5),
            new TierMaterial("Ingredient_Voidheart", 5)
        )));

        return config;
    }
}
