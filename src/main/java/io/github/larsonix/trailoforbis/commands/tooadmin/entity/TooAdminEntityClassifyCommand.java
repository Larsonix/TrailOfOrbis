package io.github.larsonix.trailoforbis.commands.tooadmin.entity;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.mobs.MobScalingManager;
import io.github.larsonix.trailoforbis.mobs.classification.DiscoveredRole;
import io.github.larsonix.trailoforbis.mobs.classification.DynamicEntityRegistry;
import io.github.larsonix.trailoforbis.mobs.classification.MobClassificationConfig;
import io.github.larsonix.trailoforbis.mobs.classification.RPGMobClass;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;
import java.util.Set;

/**
 * Shows classification details for a specific role.
 *
 * <p>Usage: /tooadmin entity classify &lt;roleName&gt;
 *
 * <p>Displays:
 * <ul>
 *   <li>Role name and index</li>
 *   <li>Classification result</li>
 *   <li>Detection method used</li>
 *   <li>Mod source</li>
 *   <li>Group memberships</li>
 * </ul>
 */
public final class TooAdminEntityClassifyCommand extends AbstractPlayerCommand {

    private final TrailOfOrbis plugin;
    private final RequiredArg<String> roleNameArg;

    public TooAdminEntityClassifyCommand(TrailOfOrbis plugin) {
        super("classify", "Show classification details for a role");
        this.plugin = plugin;

        roleNameArg = this.withRequiredArg("roleName", "The NPC role name to classify", ArgTypes.STRING);
    }

    @Override
    protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef sender,
            @Nonnull World world
    ) {
        String roleName = roleNameArg.get(context);

        MobScalingManager scalingManager = plugin.getMobScalingManager();
        if (scalingManager == null) {
            sender.sendMessage(Message.raw("Mob scaling system not initialized !")
                    .color(MessageColors.ERROR));
            return;
        }

        DynamicEntityRegistry registry = scalingManager.getDynamicEntityRegistry();
        MobClassificationConfig config = scalingManager.getClassificationService() != null
                ? scalingManager.getClassificationService().getConfig()
                : null;

        // Header
        sender.sendMessage(Message.raw("=== Classification : " + roleName + " ===")
                .color(MessageColors.GOLD));

        // Check dynamic registry first
        if (registry != null) {
            DiscoveredRole discovered = registry.getDiscoveredRole(roleName);

            if (discovered != null) {
                // Found in registry
                String classColor = getClassificationColor(discovered.classification());

                sender.sendMessage(Message.empty()
                        .insert(Message.raw("Classification : ").color(MessageColors.GRAY))
                        .insert(Message.raw(discovered.classification().name()).color(classColor)));

                sender.sendMessage(Message.empty()
                        .insert(Message.raw("Detection : ").color(MessageColors.GRAY))
                        .insert(Message.raw(discovered.detectionMethod().name()).color(MessageColors.INFO)));

                sender.sendMessage(Message.empty()
                        .insert(Message.raw("Role Index : ").color(MessageColors.GRAY))
                        .insert(Message.raw(String.valueOf(discovered.roleIndex())).color(MessageColors.WHITE)));

                sender.sendMessage(Message.empty()
                        .insert(Message.raw("Source : ").color(MessageColors.GRAY))
                        .insert(Message.raw(discovered.modSource()).color(
                                discovered.isFromMod() ? MessageColors.INFO : MessageColors.SUCCESS)));

                // Show group memberships
                Set<String> groups = discovered.memberGroups();
                if (!groups.isEmpty()) {
                    sender.sendMessage(Message.raw("Groups (" + groups.size() + ") :").color(MessageColors.GRAY));
                    for (String group : groups) {
                        sender.sendMessage(Message.raw("  - " + group).color(MessageColors.GRAY));
                    }
                } else {
                    sender.sendMessage(Message.raw("Groups : (none)").color(MessageColors.GRAY));
                }

                // Combat relevance
                sender.sendMessage(Message.empty()
                        .insert(Message.raw("Combat Relevant : ").color(MessageColors.GRAY))
                        .insert(Message.raw(discovered.isCombatRelevant() ? "Yes" : "No")
                                .color(discovered.isCombatRelevant() ? MessageColors.SUCCESS : MessageColors.GRAY)));

            } else {
                // Not found in registry
                sender.sendMessage(Message.raw("Role not found in dynamic registry !")
                        .color(MessageColors.ERROR));

                // Fall back to static config check
                checkStaticConfig(sender, roleName, config);
            }
        } else {
            // Registry disabled, use static config
            sender.sendMessage(Message.raw("Dynamic discovery is disabled.")
                    .color(MessageColors.GRAY));
            checkStaticConfig(sender, roleName, config);
        }
    }

    /**
     * Checks static configuration for a role.
     */
    private void checkStaticConfig(PlayerRef sender, String roleName, MobClassificationConfig config) {
        if (config == null) {
            sender.sendMessage(Message.raw("Classification config not available !")
                    .color(MessageColors.ERROR));
            return;
        }

        sender.sendMessage(Message.raw("Checking static configuration...")
                .color(MessageColors.GRAY));

        if (config.isBoss(roleName)) {
            sender.sendMessage(Message.empty()
                    .insert(Message.raw("Static Override : ").color(MessageColors.GRAY))
                    .insert(Message.raw("BOSS").color(MessageColors.DARK_PURPLE)));
        } else if (config.isElite(roleName)) {
            sender.sendMessage(Message.empty()
                    .insert(Message.raw("Static Override : ").color(MessageColors.GRAY))
                    .insert(Message.raw("ELITE").color(MessageColors.PURPLE)));
        } else {
            sender.sendMessage(Message.raw("No static override found for this role.")
                    .color(MessageColors.GRAY));
            sender.sendMessage(Message.raw("Role may not exist or may use default classification.")
                    .color(MessageColors.GRAY));
        }
    }

    /**
     * Gets the display color for a classification.
     */
    private String getClassificationColor(RPGMobClass classification) {
        return switch (classification) {
            case BOSS -> MessageColors.DARK_PURPLE;
            case ELITE -> MessageColors.PURPLE;
            case HOSTILE -> MessageColors.ERROR;
            case MINOR -> MessageColors.WARNING;  // Yellow for minor mobs
            case PASSIVE -> MessageColors.SUCCESS;
        };
    }
}
