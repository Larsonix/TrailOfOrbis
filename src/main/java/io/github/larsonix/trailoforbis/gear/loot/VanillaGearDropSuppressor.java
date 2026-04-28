package io.github.larsonix.trailoforbis.gear.loot;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.asset.type.gameplay.DeathConfig;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.item.ItemModule;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.systems.NPCDamageSystems;

import io.github.larsonix.trailoforbis.config.ConfigManager;
import io.github.larsonix.trailoforbis.maps.components.RealmMobComponent;
import io.github.larsonix.trailoforbis.mobs.classification.RPGMobClass;
import io.github.larsonix.trailoforbis.mobs.component.MobScalingComponent;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Suppresses vanilla weapon/armor drops from classified combat mobs while
 * allowing material drops (fabric scraps, bones, leather, etc.) to pass through.
 *
 * <p>Runs BEFORE {@link NPCDamageSystems.DropDeathItems}. For HOSTILE, ELITE,
 * and BOSS mobs, sets {@code itemsLossMode} to {@link DeathConfig.ItemsLossMode#NONE}
 * so that {@code DropDeathItems} skips entirely, then replicates its drop logic
 * with filtering — only spawning non-gear items.
 *
 * <p>Death animations, sounds, corpse removal, and RPG loot drops
 * (via {@link LootListener}) are unaffected — they run as separate systems.
 * PASSIVE mob drops are completely untouched.
 */
public class VanillaGearDropSuppressor extends DeathSystems.OnDeathSystem {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Matches the same query as {@link NPCDamageSystems.DropDeathItems}. */
    @Nonnull
    private static final Query<EntityStore> QUERY = Query.and(
            NPCEntity.getComponentType(),
            TransformComponent.getComponentType(),
            HeadRotation.getComponentType(),
            Query.not(Player.getComponentType())
    );

    private final ConfigManager configManager;

    public VanillaGearDropSuppressor(@Nonnull ConfigManager configManager) {
        this.configManager = configManager;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return QUERY;
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return Set.of(
                new SystemDependency<>(Order.BEFORE, NPCDamageSystems.DropDeathItems.class)
        );
    }

    @Override
    public void onComponentAdded(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull DeathComponent deathComponent,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        if (!configManager.getRPGConfig().isSuppressVanillaGearDrops()) {
            return;
        }

        // Only suppress for combat-relevant mobs (HOSTILE/ELITE/BOSS)
        MobScalingComponent scaling = store.getComponent(ref, MobScalingComponent.getComponentType());
        if (scaling == null) {
            return;
        }

        RPGMobClass classification = scaling.getClassification();
        if (!classification.isCombatRelevant()) {
            return;
        }

        // Prevent DropDeathItems from running for this entity
        deathComponent.setItemsLossMode(DeathConfig.ItemsLossMode.NONE);

        NPCEntity npc = commandBuffer.getComponent(ref, NPCEntity.getComponentType());
        if (npc == null) {
            return;
        }

        Role role = npc.getRole();
        if (role == null) {
            return;
        }

        // Realm mobs: suppress ALL vanilla drops (drop list), only keep picked-up items
        RealmMobComponent realmMob = store.getComponent(ref, RealmMobComponent.getComponentType());
        if (realmMob != null) {
            if (role.isPickupDropOnDeath()) {
                List<ItemStack> pickedUpItems = npc.getInventory().getStorage().dropAllItemStacks()
                        .stream().filter(item -> !ItemStack.isEmpty(item)).toList();
                if (!pickedUpItems.isEmpty()) {
                    TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
                    HeadRotation headRotation = store.getComponent(ref, HeadRotation.getComponentType());
                    assert transform != null;
                    assert headRotation != null;
                    Vector3d position = transform.getPosition();
                    Vector3f rotation = headRotation.getRotation();
                    Vector3d dropPosition = position.clone().add(0.0, 1.0, 0.0);
                    Holder<EntityStore>[] drops = ItemComponent.generateItemDrops(
                            store, pickedUpItems, dropPosition, rotation.clone());
                    commandBuffer.addEntities(drops, AddReason.SPAWN);
                }
            }
            LOGGER.atFine().log("Suppressed ALL vanilla drops for realm %s mob (dropList: %s)",
                    classification, role.getDropListId());
            return;
        }

        // Collect items that should still drop (non-gear only)
        List<ItemStack> filteredItems = new ArrayList<>();

        // Handle inventory drops (items the NPC picked up during its lifetime)
        if (role.isPickupDropOnDeath()) {
            for (ItemStack item : npc.getInventory().getStorage().dropAllItemStacks()) {
                if (!isGear(item)) {
                    filteredItems.add(item);
                }
            }
        }

        // Handle drop list items (configured loot table)
        String dropListId = role.getDropListId();
        if (dropListId != null) {
            ItemModule itemModule = ItemModule.get();
            if (itemModule.isEnabled()) {
                List<ItemStack> randomItems = itemModule.getRandomItemDrops(dropListId);
                for (ItemStack item : randomItems) {
                    if (!isGear(item)) {
                        filteredItems.add(item);
                    }
                }
            }
        }

        // Spawn the filtered (non-gear) items, replicating DropDeathItems' spawn logic
        if (!filteredItems.isEmpty()) {
            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            HeadRotation headRotation = store.getComponent(ref, HeadRotation.getComponentType());

            assert transform != null;
            assert headRotation != null;

            Vector3d position = transform.getPosition();
            Vector3f rotation = headRotation.getRotation();
            Vector3d dropPosition = position.clone().add(0.0, 1.0, 0.0);

            Holder<EntityStore>[] drops = ItemComponent.generateItemDrops(
                    store, filteredItems, dropPosition, rotation.clone());
            commandBuffer.addEntities(drops, AddReason.SPAWN);
        }

        LOGGER.atFine().log("Filtered vanilla gear drops for %s mob (dropList: %s, kept %d items)",
                classification, role.getDropListId(), filteredItems.size());
    }

    /**
     * Returns {@code true} if the item is a weapon or armor piece (gear that
     * should be suppressed from vanilla drops).
     */
    private static boolean isGear(@Nonnull ItemStack itemStack) {
        if (ItemStack.isEmpty(itemStack)) {
            return false;
        }
        Item item = itemStack.getItem();
        if (item == null) {
            return false;
        }
        return item.getWeapon() != null || item.getArmor() != null;
    }
}
