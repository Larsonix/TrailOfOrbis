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
import io.github.larsonix.trailoforbis.mobs.classification.DynamicEntityRegistry;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;

/**
 * Rescans the NPC registry for roles.
 *
 * <p>Usage: /tooadmin entity rescan
 *
 * <p>This is useful after:
 * <ul>
 *   <li>Hot-loading mods that add new NPC roles</li>
 *   <li>Changing entity-discovery.yml configuration</li>
 *   <li>Debugging classification issues</li>
 * </ul>
 */
public class TooAdminEntityRescanCommand extends AbstractPlayerCommand {

    private final TrailOfOrbis plugin;

    public TooAdminEntityRescanCommand(TrailOfOrbis plugin) {
        super("rescan", "Rescan NPC registry for roles");
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
            sender.sendMessage(Message.raw("Entity discovery is disabled !")
                    .color(MessageColors.ERROR));
            sender.sendMessage(Message.raw("Enable discovery.enabled in entity-discovery.yml")
                    .color(MessageColors.GRAY));
            return;
        }

        sender.sendMessage(Message.raw("Rescanning NPC registry...")
                .color(MessageColors.INFO));

        try {
            int discovered = registry.discoverRoles();

            DynamicEntityRegistry.DiscoveryStatistics stats = registry.getStatistics();

            sender.sendMessage(Message.raw(String.format("Rescan complete : %d roles discovered", discovered))
                    .color(MessageColors.SUCCESS));

            sender.sendMessage(Message.raw(String.format("  BOSS=%d, ELITE=%d, HOSTILE=%d, PASSIVE=%d",
                    stats.countByClass().getOrDefault(io.github.larsonix.trailoforbis.mobs.classification.RPGMobClass.BOSS, 0),
                    stats.countByClass().getOrDefault(io.github.larsonix.trailoforbis.mobs.classification.RPGMobClass.ELITE, 0),
                    stats.countByClass().getOrDefault(io.github.larsonix.trailoforbis.mobs.classification.RPGMobClass.HOSTILE, 0),
                    stats.countByClass().getOrDefault(io.github.larsonix.trailoforbis.mobs.classification.RPGMobClass.PASSIVE, 0)))
                    .color(MessageColors.GRAY));

        } catch (Exception e) {
            sender.sendMessage(Message.raw("Rescan failed : " + e.getMessage())
                    .color(MessageColors.ERROR));
        }
    }
}
