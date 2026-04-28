package io.github.larsonix.trailoforbis.combat.durability;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.random.RandomExtra;
import com.hypixel.hytale.server.core.entity.Entity;
import com.hypixel.hytale.server.core.entity.EntityUtils;
import com.hypixel.hytale.server.core.entity.LivingEntity;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import io.github.larsonix.trailoforbis.combat.detection.DamageTypeClassifier;
import io.github.larsonix.trailoforbis.gear.util.GearUtils;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import it.unimi.dsi.fastutil.shorts.ShortArrayList;

import javax.annotation.Nonnull;

/**
 * Handles durability loss for weapons and armor during combat.
 *
 * <p>This class extracts durability logic from RPGDamageSystem, replicating
 * vanilla behavior for:
 * <ul>
 *   <li>Attacker weapon durability (DamageSystems.DamageAttackerTool)</li>
 *   <li>Defender armor durability (DamageSystems.DamageArmor)</li>
 * </ul>
 *
 * <p>Since RPG damage cancels vanilla damage processing, vanilla durability
 * systems don't run. This class ensures weapons and armor still degrade.
 *
 * <h2>Deprecation Note</h2>
 * <p>Uses deprecated {@link EntityUtils#getEntity} because Entity/LivingEntity are stored
 * as dynamic ECS components (not static ComponentTypes). Hytale's own codebase continues
 * to use EntityUtils extensively. No replacement API has been provided yet.
 */
@SuppressWarnings("deprecation") // EntityUtils.getEntity() - Hytale hasn't provided replacement
public class DurabilityHandler {

    /**
     * Handles durability loss for both attacker weapon and defender armor.
     *
     * @param index The entity index in the archetype chunk
     * @param archetypeChunk The archetype chunk containing the defender
     * @param store The entity store
     * @param commandBuffer The command buffer for deferred operations
     * @param damage The damage event
     */
    public void handleDurability(
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Damage damage
    ) {
        // Only process if damage cause allows durability loss
        DamageCause cause = DamageTypeClassifier.getDamageCause(damage);
        if (cause == null || !cause.isDurabilityLoss()) {
            return;
        }

        // Handle attacker weapon durability
        handleAttackerWeaponDurability(store, commandBuffer, damage);

        // Handle defender armor durability
        handleDefenderArmorDurability(index, archetypeChunk, commandBuffer, damage);
    }

    /**
     * Reduces durability of the attacker's weapon (item in hand).
     *
     * <p>Mirrors DamageSystems.DamageAttackerTool behavior.
     *
     * @param store The entity store
     * @param commandBuffer The command buffer
     * @param damage The damage event
     */
    public void handleAttackerWeaponDurability(
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Damage damage
    ) {
        // Must be from an entity source
        if (!(damage.getSource() instanceof Damage.EntitySource entitySource)) {
            return;
        }

        Ref<EntityStore> attackerRef = entitySource.getRef();
        if (attackerRef == null || !attackerRef.isValid()) {
            return;
        }

        // Get attacker entity
        Entity attackerEntity = EntityUtils.getEntity(attackerRef, commandBuffer);
        if (!(attackerEntity instanceof LivingEntity attackerLiving)) {
            return;
        }

        // Get inventory and active hotbar slot
        Inventory inventory = attackerLiving.getInventory();
        if (inventory == null) {
            return;
        }

        byte activeSlot = inventory.getActiveHotbarSlot();
        if (activeSlot == -1) {
            return;
        }

        ItemStack itemInHand = inventory.getItemInHand();
        if (itemInHand == null || itemInHand.isEmpty() || itemInHand.isBroken()) {
            return;
        }

        // RPG gear is permanent — skip durability loss
        if (GearUtils.isRpgGear(itemInHand)) {
            return;
        }

        // Reduce weapon durability (inventoryId -1 = hotbar)
        attackerLiving.decreaseItemStackDurability(
            attackerRef,
            itemInHand,
            -1,
            activeSlot,
            commandBuffer
        );
    }

    /**
     * Reduces durability of a random non-broken armor piece on the defender.
     *
     * <p>Mirrors DamageSystems.DamageArmor behavior.
     *
     * @param index The entity index in the archetype chunk
     * @param archetypeChunk The archetype chunk containing the defender
     * @param commandBuffer The command buffer
     * @param damage The damage event
     */
    public void handleDefenderArmorDurability(
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Damage damage
    ) {
        Ref<EntityStore> defenderRef = archetypeChunk.getReferenceTo(index);
        if (defenderRef == null || !defenderRef.isValid()) {
            return;
        }

        // Get defender entity
        Entity defenderEntity = EntityUtils.getEntity(index, archetypeChunk);
        if (!(defenderEntity instanceof LivingEntity defenderLiving)) {
            return;
        }

        // Get armor inventory
        Inventory inventory = defenderLiving.getInventory();
        if (inventory == null) {
            return;
        }

        ItemContainer armor = inventory.getArmor();
        if (armor == null) {
            return;
        }

        // Collect all non-broken armor slots
        ShortArrayList validArmorSlots = new ShortArrayList();
        armor.forEachWithMeta((slot, itemStack, slots) -> {
            if (itemStack != null && !itemStack.isEmpty() && !itemStack.isBroken()) {
                slots.add(slot);
            }
        }, validArmorSlots);

        // If no valid armor, nothing to degrade
        if (validArmorSlots.isEmpty()) {
            return;
        }

        // Pick a random armor piece to damage
        short randomSlot = validArmorSlots.getShort(RandomExtra.randomRange(validArmorSlots.size()));
        ItemStack armorPiece = armor.getItemStack(randomSlot);

        // RPG gear is permanent — skip durability loss
        if (GearUtils.isRpgGear(armorPiece)) {
            return;
        }

        // Reduce armor durability (inventoryId -3 = armor)
        defenderLiving.decreaseItemStackDurability(
            defenderRef,
            armorPiece,
            -3,
            randomSlot,
            commandBuffer
        );
    }
}
