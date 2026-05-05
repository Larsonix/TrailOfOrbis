package io.github.larsonix.trailoforbis.commands.tooadmin;

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
import io.github.larsonix.trailoforbis.api.ServiceRegistry;
import io.github.larsonix.trailoforbis.api.services.AttributeService;
import io.github.larsonix.trailoforbis.api.services.SkillTreeService;
import io.github.larsonix.trailoforbis.commands.admin.AdminCommandHelper;
import io.github.larsonix.trailoforbis.config.RPGConfig;
import io.github.larsonix.trailoforbis.database.models.PlayerData;
import io.github.larsonix.trailoforbis.database.repository.PlayerDataRepository;
import io.github.larsonix.trailoforbis.leveling.api.LevelingService;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;
import java.util.Optional;
import java.util.UUID;

/**
 * Admin command to fully reset a player to starting state.
 *
 * <p>Resets ALL RPG progression:
 * <ul>
 *   <li>Attributes: all elements to 0, unallocated points to config starting value</li>
 *   <li>Attribute refund points: reset to 10, respecs counter to 0</li>
 *   <li>Leveling: XP to 0 (level 1)</li>
 *   <li>Skill tree: origin only, points to config starting value, respecs/refund reset</li>
 * </ul>
 *
 * <p>Does NOT touch inventory (gear, maps, stones remain).
 *
 * <p>Usage: /tooadmin fullreset &lt;player&gt;
 */
public final class TooAdminFullResetCommand extends AbstractPlayerCommand {
    private final TrailOfOrbis plugin;
    private final RequiredArg<String> targetArg;

    public TooAdminFullResetCommand(TrailOfOrbis plugin) {
        super("fullreset", "Fully reset a player to level 1 starting state");
        this.plugin = plugin;
        targetArg = this.withRequiredArg("player", "Target player name", ArgTypes.STRING);
    }

    @Override
    protected void execute(
        @Nonnull CommandContext context,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull PlayerRef sender,
        @Nonnull World world
    ) {
        String targetName = targetArg.get(context);

        // Resolve all required services
        AttributeService attrService = ServiceRegistry.get(AttributeService.class).orElse(null);
        LevelingService levelService = ServiceRegistry.get(LevelingService.class).orElse(null);
        SkillTreeService skillService = ServiceRegistry.get(SkillTreeService.class).orElse(null);

        if (attrService == null || levelService == null || skillService == null) {
            sender.sendMessage(Message.raw("Required services not available!").color(MessageColors.ERROR));
            return;
        }

        // Resolve target player
        PlayerDataRepository repo = attrService.getPlayerDataRepository();
        Optional<PlayerData> targetData = AdminCommandHelper.resolvePlayer(targetName, repo);

        if (targetData.isEmpty()) {
            sender.sendMessage(Message.raw("Player not found: " + targetName).color(MessageColors.ERROR));
            return;
        }

        UUID targetUuid = targetData.get().getUuid();
        PlayerData data = targetData.get();

        // Capture old values for summary
        int oldLevel = levelService.getLevel(targetUuid);
        int oldAllocatedAttrs = data.getTotalAllocatedPoints();
        int oldAllocatedNodes = skillService.getAllocatedNodes(targetUuid).size() - 1; // minus origin

        // Load config starting values
        RPGConfig rpgConfig = plugin.getConfigManager().getRPGConfig();
        int attrStartingPoints = rpgConfig.getAttributes().getStartingPoints();
        int skillStartingPoints = rpgConfig.getSkillTree().getStartingPoints();

        // ── 1. Reset Attributes ──
        // Admin reset: zeros all 6 elements, no refund point cost
        attrService.resetAllAttributesAdmin(targetUuid);

        // Override unallocated points to config starting value (not the refunded total)
        attrService.setUnallocatedPoints(targetUuid, attrStartingPoints);

        // Reset refund points and respecs counter to new-player defaults
        Optional<PlayerData> afterAttrReset = repo.get(targetUuid);
        if (afterAttrReset.isPresent()) {
            PlayerData resetData = afterAttrReset.get().toBuilder()
                .attributeRefundPoints(10)
                .attributeRespecs(0)
                .build();
            repo.save(resetData);
        }

        // ── 2. Reset Leveling ──
        // XP = 0 resolves to level 1
        levelService.setXp(targetUuid, 0);

        // ── 3. Reset Skill Tree ──
        // Full reset: origin only, starting points, 0 respecs, 10 refund points
        skillService.fullReset(targetUuid, skillStartingPoints);

        // ── 4. Final stat recalculation ──
        attrService.recalculateStats(targetUuid);

        // ── Report ──
        sender.sendMessage(Message.empty()
            .insert(Message.raw("[TooAdmin] ").color(MessageColors.GOLD))
            .insert(Message.raw("Full reset completed for ").color(MessageColors.SUCCESS))
            .insert(Message.raw(targetName).color(MessageColors.WHITE))
            .insert(Message.raw(":").color(MessageColors.SUCCESS)));

        sender.sendMessage(Message.empty()
            .insert(Message.raw("  Level: ").color(MessageColors.GRAY))
            .insert(Message.raw(String.valueOf(oldLevel)).color(MessageColors.WHITE))
            .insert(Message.raw(" → ").color(MessageColors.GRAY))
            .insert(Message.raw("1").color(MessageColors.WHITE)));

        sender.sendMessage(Message.empty()
            .insert(Message.raw("  Attributes: ").color(MessageColors.GRAY))
            .insert(Message.raw(oldAllocatedAttrs + " allocated").color(MessageColors.WHITE))
            .insert(Message.raw(" → ").color(MessageColors.GRAY))
            .insert(Message.raw(attrStartingPoints + " unallocated").color(MessageColors.WHITE)));

        sender.sendMessage(Message.empty()
            .insert(Message.raw("  Skill Tree: ").color(MessageColors.GRAY))
            .insert(Message.raw(oldAllocatedNodes + " nodes").color(MessageColors.WHITE))
            .insert(Message.raw(" → ").color(MessageColors.GRAY))
            .insert(Message.raw("origin only, " + skillStartingPoints + " points").color(MessageColors.WHITE)));

        sender.sendMessage(Message.empty()
            .insert(Message.raw("  Refund points: ").color(MessageColors.GRAY))
            .insert(Message.raw("10 attr / 10 skill").color(MessageColors.WHITE))
            .insert(Message.raw(" (defaults)").color(MessageColors.GRAY)));

        AdminCommandHelper.logAdminAction(plugin, sender, "FULL_RESET", targetName,
            "Reset from level " + oldLevel + " to level 1 (attrs: " + oldAllocatedAttrs
                + ", nodes: " + oldAllocatedNodes + ")");
    }
}
