package io.github.larsonix.trailoforbis.commands.tooadmin.entity;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.mobs.MobScalingManager;
import io.github.larsonix.trailoforbis.mobs.classification.DiscoveredRole;
import io.github.larsonix.trailoforbis.mobs.classification.DynamicEntityRegistry;
import io.github.larsonix.trailoforbis.mobs.classification.RPGMobClass;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;
import java.util.Comparator;
import java.util.Map;

/**
 * Shows statistics about discovered NPC roles.
 *
 * <p>Usage: /tooadmin entity stats
 *
 * <p>Displays:
 * <ul>
 *   <li>Total discovered roles</li>
 *   <li>Roles per classification (BOSS, ELITE, HOSTILE, PASSIVE)</li>
 *   <li>Roles per detection method</li>
 *   <li>Roles per mod source</li>
 * </ul>
 */
public class TooAdminEntityStatsCommand extends AbstractPlayerCommand {

    private final TrailOfOrbis plugin;

    public TooAdminEntityStatsCommand(TrailOfOrbis plugin) {
        super("stats", "Show discovered entity statistics");
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
        MobScalingManager scalingManager = plugin.getMobScalingManager();
        if (scalingManager == null) {
            sender.sendMessage(Message.raw("Mob scaling system not initialized !")
                    .color(MessageColors.ERROR));
            return;
        }

        DynamicEntityRegistry registry = scalingManager.getDynamicEntityRegistry();
        if (registry == null) {
            sender.sendMessage(Message.raw("Entity discovery is disabled or not initialized !")
                    .color(MessageColors.ERROR));
            sender.sendMessage(Message.raw("Enable discovery.enabled in entity-discovery.yml")
                    .color(MessageColors.GRAY));
            return;
        }

        DynamicEntityRegistry.DiscoveryStatistics stats = registry.getStatistics();

        // Header
        sender.sendMessage(Message.raw("=== Entity Discovery Stats ===").color(MessageColors.GOLD));
        sender.sendMessage(Message.raw(String.format("Discovered %d NPC roles", stats.totalDiscovered()))
                .color(MessageColors.INFO));
        sender.sendMessage(Message.empty());

        // By classification
        sender.sendMessage(Message.raw("By Classification:").color(MessageColors.GOLD));
        for (RPGMobClass cls : RPGMobClass.values()) {
            int count = stats.countByClass().getOrDefault(cls, 0);
            String colorCode = switch (cls) {
                case BOSS -> MessageColors.DARK_PURPLE;
                case ELITE -> MessageColors.PURPLE;
                case HOSTILE -> MessageColors.ERROR;
                case MINOR -> MessageColors.WARNING;
                case PASSIVE -> MessageColors.SUCCESS;
            };
            sender.sendMessage(Message.raw(String.format("  %s : %d roles", cls.name(), count))
                    .color(colorCode));
        }
        sender.sendMessage(Message.empty());

        // By detection method
        sender.sendMessage(Message.raw("By Detection Method :").color(MessageColors.GOLD));
        for (DiscoveredRole.DetectionMethod method : DiscoveredRole.DetectionMethod.values()) {
            int count = stats.countByMethod().getOrDefault(method, 0);
            if (count > 0) {
                sender.sendMessage(Message.raw(String.format("  %s : %d", method.name(), count))
                        .color(MessageColors.INFO));
            }
        }
        sender.sendMessage(Message.empty());

        // By source (sorted by count descending)
        sender.sendMessage(Message.raw("By Source :").color(MessageColors.GOLD));
        stats.countBySource().entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue).reversed())
                .forEach(entry -> {
                    String source = entry.getKey();
                    int count = entry.getValue();
                    String displayName = formatSourceName(source);
                    boolean isVanilla = source.startsWith("Hytale:");

                    sender.sendMessage(Message.raw(String.format("  %s : %d roles", displayName, count))
                            .color(isVanilla ? MessageColors.SUCCESS : MessageColors.INFO));
                });
    }

    /**
     * Formats a mod source name for display.
     */
    private String formatSourceName(String source) {
        int colonIndex = source.indexOf(':');
        if (colonIndex > 0 && colonIndex < source.length() - 1) {
            String packId = source.substring(0, colonIndex);
            String packName = source.substring(colonIndex + 1);
            if (packId.equals(packName)) {
                return packName;
            }
            return packName + " (" + packId + ")";
        }
        return source;
    }
}
