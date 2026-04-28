package io.github.larsonix.trailoforbis.listeners;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryChangeEvent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.api.ServiceRegistry;
import io.github.larsonix.trailoforbis.api.services.AttributeService;
import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.combat.attackspeed.AnimationSpeedSyncManager;
import io.github.larsonix.trailoforbis.database.models.PlayerData;
import io.github.larsonix.trailoforbis.database.repository.PlayerDataRepository;
import io.github.larsonix.trailoforbis.systems.StatsApplicationSystem;

import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Listener for equipment changes that triggers stat recalculation.
 *
 * <p>When a player changes equipment (armor, hotbar, or utility), this listener:
 * <ol>
 *   <li>Detects the equipment container change</li>
 *   <li>Triggers a full stat recalculation (includes new equipment stats)</li>
 *   <li>Applies the updated stats to the player's ECS components</li>
 * </ol>
 *
 * <p>Handled containers:
 * <ul>
 *   <li>Armor - defensive equipment with armor values</li>
 *   <li>Hotbar - weapons/tools with damage and stat modifiers</li>
 *   <li>Utility - offhand items with various modifiers</li>
 * </ul>
 *
 * <p>Note: Hotbar/utility slot <b>switches</b> are handled separately by
 * {@link io.github.larsonix.trailoforbis.gear.systems.WeaponSlotChangeSystem}.
 * This listener handles item <b>additions/removals</b> in these containers.
 */
public class EquipmentChangeListener {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /**
     * Handles inventory change events to detect equipment changes.
     *
     * <p>Processes changes to equipment containers:
     * <ul>
     *   <li>Armor (section ID -3) - defensive equipment</li>
     *   <li>Hotbar (section ID -1) - weapons and tools</li>
     *   <li>Utility (section ID -5) - offhand items</li>
     * </ul>
     * Other inventory changes (storage, etc.) are ignored.
     *
     * @param event The inventory change event
     */
    public static void onInventoryChange(Player player, InventoryChangeEvent event) {

        // Get player inventory
        Inventory inventory = player.getInventory();
        if (inventory == null) {
            return;
        }

        // Check if this is an equipment container change (armor, hotbar, or utility)
        var changedContainer = event.getItemContainer();
        boolean isEquipmentContainer = changedContainer == inventory.getArmor()
                || changedContainer == inventory.getHotbar()
                || changedContainer == inventory.getUtility();
        if (!isEquipmentContainer) {
            return;
        }

        // Get player info via ECS component lookup (future-proof pattern)
        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }

        UUID uuid = playerRef.getUuid();

        // Skip equipment changes during join — Hytale syncs equipment on world entry,
        // firing this event before PlayerReadyEvent creates ComputedStats.
        // The full recalculation in onPlayerReady will pick up all equipment.
        if (PlayerJoinListener.isJoining(uuid)) {
            LOGGER.at(Level.FINE).log("Skipping equipment change for %s — join in progress", uuid);
            return;
        }

        LOGGER.at(Level.FINE).log("Equipment change detected for player %s", uuid);

        // Get attribute service
        Optional<AttributeService> attributeServiceOpt = ServiceRegistry.get(AttributeService.class);
        if (attributeServiceOpt.isEmpty()) {
            return;
        }

        AttributeService attributeService = attributeServiceOpt.get();

        // Recalculate stats (will pick up new equipment armor value).
        // NOTE: recalculateStats() internally applies to ECS via its callback
        // when stats actually change — do NOT call applyAllStatsAndSync() again
        // here, as the redundant double-apply triggers ECS dirty marking and
        // contributes to the InventoryChangeEvent feedback loop.
        ComputedStats newStats = attributeService.recalculateStats(uuid);
        if (newStats == null) {
            LOGGER.at(Level.WARNING).log("Failed to recalculate stats for %s after equipment change", uuid);
            return;
        }

        // Sync animation speed to match new attack speed stat
        ServiceRegistry.get(AnimationSpeedSyncManager.class)
                .ifPresent(m -> m.syncAnimationSpeed(uuid));

        LOGGER.at(Level.FINE).log("Recalculated stats for %s after equipment change: armor=%s, maxHealth=%s",
            uuid, newStats.getArmor(), newStats.getMaxHealth());
    }
}
