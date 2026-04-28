package io.github.larsonix.trailoforbis.gems.item;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import io.github.larsonix.trailoforbis.gear.item.CustomItemInstanceId;
import io.github.larsonix.trailoforbis.gear.item.ItemRegistryService;
import io.github.larsonix.trailoforbis.gems.config.GemRegistry;
import io.github.larsonix.trailoforbis.gems.model.GemData;
import io.github.larsonix.trailoforbis.gems.model.GemDefinition;
import io.github.larsonix.trailoforbis.gems.model.GemType;
import io.github.larsonix.trailoforbis.gems.util.GemUtils;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class GemItemFactory {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private final GemRegistry registry;
    private final ItemRegistryService itemRegistryService;

    public GemItemFactory(@Nonnull GemRegistry registry, @Nonnull ItemRegistryService itemRegistryService) {
        this.registry = Objects.requireNonNull(registry);
        this.itemRegistryService = Objects.requireNonNull(itemRegistryService);
    }

    @Nullable
    public GemItemResult createGemItem(@Nonnull String gemId, int level, int quality) {
        Optional<GemDefinition> defOpt = this.registry.getDefinition(gemId);
        if (defOpt.isEmpty()) {
            LOGGER.atWarning().log("Unknown gem ID: %s", gemId);
            return null;
        }
        GemDefinition definition = defOpt.get();
        GemData gemData = new GemData(gemId, level, quality, 0L, definition.gemType());
        CustomItemInstanceId instanceId = CustomItemInstanceId.Generator.generateGem();
        String baseItemId = gemData.gemType() == GemType.ACTIVE ? "RPG_Gem_Active" : "RPG_Gem_Support";
        String customItemId = instanceId.toItemId();
        if (this.itemRegistryService != null && this.itemRegistryService.isInitialized()) {
            Item baseItem = (Item) Item.getAssetMap().getAsset(baseItemId);
            if (baseItem != null && baseItem != Item.UNKNOWN) {
                if (!this.itemRegistryService.isRegistered(customItemId)) {
                    this.itemRegistryService.createAndRegisterSync(baseItem, customItemId);
                    LOGGER.atFine().log("Registered custom gem item: %s (base: %s)", customItemId, baseItemId);
                }
            } else {
                LOGGER.atWarning().log("Base gem item not found in asset map: %s. Ensure the asset pack is deployed with RPG_Gem_Active.json and RPG_Gem_Support.json.", baseItemId);
            }
        } else {
            LOGGER.atWarning().log("ItemRegistryService not available \u2014 gem item may show as Invalid Item");
        }
        ItemStack itemStack = new ItemStack(customItemId, 1);
        itemStack = GemUtils.writeGemData(itemStack, gemData);
        GemItemData itemData = new GemItemData(instanceId, definition, gemData);
        LOGGER.atFine().log("Created gem item: %s (Lv%d Q%d) -> %s", gemId, level, quality, customItemId);
        return new GemItemResult(itemStack, itemData);
    }

    public record GemItemResult(@Nonnull ItemStack itemStack, @Nonnull GemItemData itemData) {
    }
}
