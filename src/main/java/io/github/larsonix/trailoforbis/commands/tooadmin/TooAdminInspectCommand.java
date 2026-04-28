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
import io.github.larsonix.trailoforbis.attributes.AttributeType;
import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.commands.admin.AdminCommandHelper;
import io.github.larsonix.trailoforbis.database.models.PlayerData;
import io.github.larsonix.trailoforbis.database.repository.PlayerDataRepository;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;
import java.util.Optional;

/**
 * Admin command to inspect a player's full RPG profile.
 *
 * <p>Usage: /tooadmin inspect &lt;player&gt;
 */
public final class TooAdminInspectCommand extends AbstractPlayerCommand {
    @SuppressWarnings("unused")
    private final TrailOfOrbis plugin;
    private final RequiredArg<String> targetArg;

    public TooAdminInspectCommand(TrailOfOrbis plugin) {
        super("inspect", "Inspect player RPG profile");
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

        AttributeService service = ServiceRegistry.get(AttributeService.class).orElse(null);
        if (service == null) {
            sender.sendMessage(Message.raw("AttributeService not available !").color(MessageColors.ERROR));
            return;
        }

        PlayerDataRepository repo = service.getPlayerDataRepository();
        Optional<PlayerData> targetData = repo.getByUsername(targetName);

        if (targetData.isEmpty()) {
            sender.sendMessage(Message.raw("Player not found : " + targetName).color(MessageColors.ERROR));
            return;
        }

        PlayerData data = targetData.get();
        ComputedStats stats = service.getStats(data.getUuid());

        // Header
        sender.sendMessage(Message.empty()
            .insert(Message.raw("=== ").color(MessageColors.GOLD))
            .insert(Message.raw(data.getUsername()).color(MessageColors.WHITE))
            .insert(Message.raw(" RPG Profile ===").color(MessageColors.GOLD)));

        // UUID and timestamps
        sender.sendMessage(Message.empty()
            .insert(Message.raw("UUID : ").color(MessageColors.GRAY))
            .insert(Message.raw(data.getUuid().toString()).color(MessageColors.WHITE)));

        sender.sendMessage(Message.empty()
            .insert(Message.raw("Created : ").color(MessageColors.GRAY))
            .insert(Message.raw(AdminCommandHelper.DATE_FORMAT.format(data.getCreatedAt())).color(MessageColors.WHITE)));

        sender.sendMessage(Message.empty()
            .insert(Message.raw("Last Seen : ").color(MessageColors.GRAY))
            .insert(Message.raw(AdminCommandHelper.DATE_FORMAT.format(data.getLastSeen())).color(MessageColors.WHITE)));

        // Attributes
        sender.sendMessage(Message.raw("--- Elements ---").color(MessageColors.GOLD));

        sender.sendMessage(Message.empty()
            .insert(Message.raw("  Fire : ").color(MessageColors.GRAY))
            .insert(Message.raw(String.valueOf(data.getFire())).color(AttributeType.FIRE.getHexColor())));

        sender.sendMessage(Message.empty()
            .insert(Message.raw("  Water : ").color(MessageColors.GRAY))
            .insert(Message.raw(String.valueOf(data.getWater())).color(AttributeType.WATER.getHexColor())));

        sender.sendMessage(Message.empty()
            .insert(Message.raw("  Lightning : ").color(MessageColors.GRAY))
            .insert(Message.raw(String.valueOf(data.getLightning())).color(AttributeType.LIGHTNING.getHexColor())));

        sender.sendMessage(Message.empty()
            .insert(Message.raw("  Earth : ").color(MessageColors.GRAY))
            .insert(Message.raw(String.valueOf(data.getEarth())).color(AttributeType.EARTH.getHexColor())));

        sender.sendMessage(Message.empty()
            .insert(Message.raw("  Wind : ").color(MessageColors.GRAY))
            .insert(Message.raw(String.valueOf(data.getWind())).color(AttributeType.WIND.getHexColor())));

        sender.sendMessage(Message.empty()
            .insert(Message.raw("  Void : ").color(MessageColors.GRAY))
            .insert(Message.raw(String.valueOf(data.getVoidAttr())).color(AttributeType.VOID.getHexColor())));

        sender.sendMessage(Message.empty()
            .insert(Message.raw("  Unallocated : ").color(MessageColors.GRAY))
            .insert(Message.raw(String.valueOf(data.getUnallocatedPoints())).color(MessageColors.WHITE)));

        // Computed Stats (if available)
        if (stats != null) {
            sender.sendMessage(Message.raw("--- Computed Stats ---").color(MessageColors.GOLD));

            sender.sendMessage(Message.empty()
                .insert(Message.raw("  Max Health : ").color(MessageColors.GRAY))
                .insert(Message.raw(String.format("%.0f", stats.getMaxHealth())).color(MessageColors.ERROR)));

            sender.sendMessage(Message.empty()
                .insert(Message.raw("  Max Mana : ").color(MessageColors.GRAY))
                .insert(Message.raw(String.format("%.0f", stats.getMaxMana())).color(MessageColors.BLUE)));

            sender.sendMessage(Message.empty()
                .insert(Message.raw("  Phys Damage : ").color(MessageColors.GRAY))
                .insert(Message.raw(String.format("+%.0f%%", stats.getPhysicalDamagePercent())).color(MessageColors.WHITE)));

            sender.sendMessage(Message.empty()
                .insert(Message.raw("  Spell Damage : ").color(MessageColors.GRAY))
                .insert(Message.raw(String.format("+%.0f%%", stats.getSpellDamagePercent())).color(MessageColors.WHITE)));

            sender.sendMessage(Message.empty()
                .insert(Message.raw("  Crit Chance : ").color(MessageColors.GRAY))
                .insert(Message.raw(String.format("%.1f%%", stats.getCriticalChance())).color(MessageColors.WARNING)));

            sender.sendMessage(Message.empty()
                .insert(Message.raw("  Move Speed : ").color(MessageColors.GRAY))
                .insert(Message.raw(String.format("+%.0f%%", stats.getMovementSpeedPercent())).color(MessageColors.INFO)));

            sender.sendMessage(Message.empty()
                .insert(Message.raw("  Armor : ").color(MessageColors.GRAY))
                .insert(Message.raw(String.format("%.0f", stats.getArmor())).color(MessageColors.WHITE)));
        }

        sender.sendMessage(Message.raw("--- End Profile ---").color(MessageColors.GOLD));
    }
}
