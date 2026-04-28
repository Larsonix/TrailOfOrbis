package io.github.larsonix.trailoforbis.gems;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterActionType;
import com.hypixel.hytale.server.core.inventory.container.filter.SlotFilter;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.gear.item.CustomItemSyncService;
import io.github.larsonix.trailoforbis.gear.item.ItemRegistryService;
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.util.GearUtils;
import io.github.larsonix.trailoforbis.gems.config.GemConfigLoader;
import io.github.larsonix.trailoforbis.gems.config.GemRegistry;
import io.github.larsonix.trailoforbis.gems.item.GemItemFactory;
import io.github.larsonix.trailoforbis.gems.model.GemData;
import io.github.larsonix.trailoforbis.gems.socket.GemSocketPage;
import io.github.larsonix.trailoforbis.gems.util.GemUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class GemManager {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private final Path configDir;
    private final ClassLoader classLoader;
    @Nullable
    private GemRegistry registry;
    @Nullable
    private GemItemFactory itemFactory;
    @Nullable
    private CustomItemSyncService syncService;
    private boolean initialized = false;

    public GemManager(@Nonnull Path configDir, @Nonnull ClassLoader classLoader) {
        this.configDir = Objects.requireNonNull(configDir, "configDir");
        this.classLoader = Objects.requireNonNull(classLoader, "classLoader");
    }

    public void initialize(@Nonnull ItemRegistryService itemRegistryService, @Nullable CustomItemSyncService syncService) {
        LOGGER.atInfo().log("Initializing Gem System...");
        this.syncService = syncService;
        try {
            Path gemsDir = this.configDir.resolve("gems");
            this.ensureDirectoryStructure(gemsDir);
            this.copyDefaultGems(gemsDir);
            GemConfigLoader loader = new GemConfigLoader();
            this.registry = loader.loadAll(gemsDir);
            this.itemFactory = new GemItemFactory(this.registry, itemRegistryService);
            this.initialized = true;
            LOGGER.atInfo().log("Gem System initialized: %d gems loaded (%d active, %d support)", this.registry.size(), this.registry.getActiveGems().size(), this.registry.getSupportGems().size());
        }
        catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to initialize Gem System");
        }
    }

    public void shutdown() {
        if (!this.initialized) {
            return;
        }
        this.registry = null;
        this.itemFactory = null;
        this.syncService = null;
        this.initialized = false;
        LOGGER.atInfo().log("Gem System shut down");
    }

    @Nullable
    public GemRegistry getRegistry() {
        return this.registry;
    }

    @Nullable
    public GemItemFactory getItemFactory() {
        return this.itemFactory;
    }

    @Nullable
    public CustomItemSyncService getSyncService() {
        return this.syncService;
    }

    public boolean isInitialized() {
        return this.initialized;
    }

    public void installGemFilters(@Nonnull Inventory inventory, @Nonnull PlayerRef playerRef) {
        short slot;
        if (!this.initialized) {
            return;
        }
        ItemContainer armorContainer = inventory.getArmor();
        ItemContainer hotbarContainer = inventory.getHotbar();
        if (armorContainer == null || hotbarContainer == null) {
            LOGGER.atFine().log("Inventory containers not ready for player %s, skipping gem filter install", playerRef.getUuid());
            return;
        }
        for (slot = 0; slot < armorContainer.getCapacity(); slot = (short)(slot + 1)) {
            armorContainer.setSlotFilter(FilterActionType.ADD, slot, this.createGemArmorFilter(playerRef));
        }
        for (slot = 0; slot < hotbarContainer.getCapacity(); slot = (short)(slot + 1)) {
            hotbarContainer.setSlotFilter(FilterActionType.ADD, slot, this.createGemHotbarFilter(playerRef));
        }
        LOGGER.atFine().log("Gem socketing filters installed for player %s", playerRef.getUuid());
    }

    private SlotFilter createGemArmorFilter(PlayerRef playerRef) {
        return (actionType, container, slot, itemStack) -> {
            if (itemStack == null || itemStack.isEmpty()) {
                return true;
            }
            if (!GemUtils.isGem(itemStack)) {
                return true;
            }
            ItemStack currentGear = container.getItemStack(slot);
            if (currentGear != null && !currentGear.isEmpty() && GearUtils.isRpgGear(currentGear)) {
                this.dispatchSocketingPage(playerRef, currentGear, itemStack, slot, container);
            }
            return false;
        };
    }

    private SlotFilter createGemHotbarFilter(PlayerRef playerRef) {
        return (actionType, container, slot, itemStack) -> {
            if (itemStack == null || itemStack.isEmpty()) {
                return true;
            }
            if (!GemUtils.isGem(itemStack)) {
                return true;
            }
            ItemStack currentItem = container.getItemStack(slot);
            if (currentItem != null && !currentItem.isEmpty() && GearUtils.isRpgGear(currentItem)) {
                this.dispatchSocketingPage(playerRef, currentItem, itemStack, slot, container);
                return false;
            }
            return true;
        };
    }

    private void dispatchSocketingPage(PlayerRef playerRef, ItemStack targetGear, ItemStack gemItem, short targetSlot, ItemContainer gearContainer) {
        GemUtils.readGemData(gemItem).ifPresent(gemData -> {
            Ref ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) {
                return;
            }
            Store store = ref.getStore();
            World world = ((EntityStore)store.getExternalData()).getWorld();
            world.execute(() -> GearUtils.getGearData(targetGear).ifPresent(gearData -> new GemSocketPage(playerRef, (GearData)gearData, (GemData)gemData, targetSlot, gearContainer).open((Store<EntityStore>)store)));
        });
    }

    private void ensureDirectoryStructure(Path gemsDir) throws IOException {
        Files.createDirectories(gemsDir.resolve("active/magic"));
        Files.createDirectories(gemsDir.resolve("active/melee"));
        Files.createDirectories(gemsDir.resolve("active/utility"));
        Files.createDirectories(gemsDir.resolve("support"));
    }

    private void copyDefaultGems(Path gemsDir) {
        this.copyDefaultIfMissing(gemsDir, "active/magic/fireball.yml");
        this.copyDefaultIfMissing(gemsDir, "active/melee/cleave.yml");
        this.copyDefaultIfMissing(gemsDir, "support/chain.yml");
    }

    private void copyDefaultIfMissing(Path gemsDir, String relativePath) {
        Path targetPath = gemsDir.resolve(relativePath);
        if (Files.exists(targetPath)) {
            return;
        }
        String resourcePath = "config/gems/" + relativePath;
        try (InputStream resource = this.classLoader.getResourceAsStream(resourcePath)) {
            if (resource != null) {
                Files.createDirectories(targetPath.getParent());
                Files.copy(resource, targetPath);
                LOGGER.atFine().log("Copied default gem config: %s", relativePath);
            }
        }
        catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to copy default gem config: %s", relativePath);
        }
    }
}
