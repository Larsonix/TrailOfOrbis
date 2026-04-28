package io.github.larsonix.trailoforbis.commands.tooadmin.loot;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.gear.loot.DiscoveredItem;
import io.github.larsonix.trailoforbis.gear.loot.DynamicLootRegistry;
import io.github.larsonix.trailoforbis.gear.loot.LootGenerator.EquipmentSlot;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Shows statistics about discovered loot items.
 *
 * <p>Usage: /tooadmin loot stats
 *
 * <p>Displays:
 * <ul>
 *   <li>Total discovered items</li>
 *   <li>Items per equipment slot</li>
 *   <li>Items per mod source</li>
 * </ul>
 */
public class TooAdminLootStatsCommand extends AbstractPlayerCommand {

    private final TrailOfOrbis plugin;

    public TooAdminLootStatsCommand(TrailOfOrbis plugin) {
        super("stats", "Show discovered loot item statistics");
        this.plugin = plugin;
    }

    @Override
    protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef sender,
            @Nonnull World world
    ) {
        DynamicLootRegistry registry = plugin.getGearManager().getDynamicLootRegistry();

        if (registry == null || !registry.isDiscovered()) {
            sender.sendMessage(Message.raw("Loot discovery not initialized !")
                    .color(MessageColors.ERROR));
            return;
        }

        int total = registry.getTotalItemCount();

        // Header
        sender.sendMessage(Message.raw("=== Loot Discovery Stats ===").color(MessageColors.GOLD));
        sender.sendMessage(Message.raw(String.format("Discovered %d droppable items", total))
                .color(MessageColors.INFO));
        sender.sendMessage(Message.empty());

        // By slot
        sender.sendMessage(Message.raw("By Slot :").color(MessageColors.GOLD));
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            int count = registry.getItemCount(slot);
            if (count > 0) {
                sender.sendMessage(Message.raw(String.format("  %s : %d items",
                        slot.name().toLowerCase(), count))
                        .color(MessageColors.INFO));
            }
        }
        sender.sendMessage(Message.empty());

        // By mod source
        sender.sendMessage(Message.raw("By Source :").color(MessageColors.GOLD));
        Map<String, List<DiscoveredItem>> itemsByMod = registry.getItemsByMod();

        // Sort by item count (descending)
        itemsByMod.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, List<DiscoveredItem>>>comparingInt(
                                e -> e.getValue().size())
                        .reversed())
                .forEach(entry -> {
                    String source = entry.getKey();
                    int count = entry.getValue().size();

                    // Format source name nicely
                    String displayName = formatSourceName(source);

                    sender.sendMessage(Message.raw(String.format("  %s : %d items",
                            displayName, count))
                            .color(entry.getKey().equals(DiscoveredItem.VANILLA_SOURCE)
                                    ? MessageColors.SUCCESS  // Highlight vanilla
                                    : MessageColors.INFO));
                });

        // Show slot weight distribution
        sender.sendMessage(Message.empty());
        sender.sendMessage(Message.raw("Slot Weights (drop chance distribution) :").color(MessageColors.GOLD));
        Map<EquipmentSlot, Integer> weights = registry.getSlotWeights();
        int totalWeight = weights.values().stream().mapToInt(Integer::intValue).sum();

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            int weight = weights.getOrDefault(slot, 0);
            if (weight > 0) {
                double pct = totalWeight > 0 ? (weight * 100.0 / totalWeight) : 0;
                sender.sendMessage(Message.raw(String.format("  %s : %d (%.0f%%)",
                        slot.name().toLowerCase(), weight, pct))
                        .color(MessageColors.GRAY));
            }
        }
    }

    /**
     * Formats a mod source name for display.
     *
     * <p>Converts "PackId:PackName" to "PackName (PackId)" for readability.
     */
    private String formatSourceName(String source) {
        int colonIndex = source.indexOf(':');
        if (colonIndex > 0 && colonIndex < source.length() - 1) {
            String packId = source.substring(0, colonIndex);
            String packName = source.substring(colonIndex + 1);
            return packName + " (" + packId + ")";
        }
        return source;
    }
}
