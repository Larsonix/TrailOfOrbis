package io.github.larsonix.trailoforbis.commands.too.attr;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import io.github.larsonix.trailoforbis.commands.base.OpenPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.api.ServiceRegistry;
import io.github.larsonix.trailoforbis.api.services.AttributeService;
import io.github.larsonix.trailoforbis.attributes.AttributeType;
import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.database.models.PlayerData;
import io.github.larsonix.trailoforbis.systems.StatsApplicationSystem;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

/**
 * Unallocates attribute points.
 *
 * <p>Usage:
 * <ul>
 *   <li>/too attr unallocate - Unallocate ALL points</li>
 *   <li>/too attr unallocate &lt;type&gt; - Unallocate all from specific attribute</li>
 *   <li>/too attr unallocate &lt;type&gt; &lt;amount&gt; - Unallocate specific amount</li>
 * </ul>
 */
public final class TooAttrUnallocateCommand extends OpenPlayerCommand {
    private final TrailOfOrbis plugin;
    private final OptionalArg<String> attributeArg;
    private final OptionalArg<Integer> amountArg;

    public TooAttrUnallocateCommand(TrailOfOrbis plugin) {
        super("unallocate", "Unallocate your attribute points");
        this.addAliases("unalloc", "remove", "refund");
        this.plugin = plugin;

        attributeArg = this.withOptionalArg("attribute", "fire/water/lightning/earth/wind/void", ArgTypes.STRING);
        amountArg = this.withOptionalArg("amount", "Number of points", ArgTypes.INTEGER);
    }

    @Override
    protected void execute(
        @Nonnull CommandContext context,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull PlayerRef sender,
        @Nonnull World world
    ) {
        UUID playerUuid = sender.getUuid();
        String attrName = attributeArg.get(context);
        Integer amount = amountArg.get(context);

        AttributeService service = ServiceRegistry.require(AttributeService.class);

        // Branch 1: /too attr unallocate - Reset all attributes
        if (attrName == null) {
            unallocateAll(service, playerUuid, sender, store, ref);
            return;
        }

        // Parse attribute type
        AttributeType type = AttributeType.fromString(attrName);
        if (type == null) {
            sender.sendMessage(Message.raw("Invalid element ! Use : fire, water, lightning, earth, wind, void")
                .color(MessageColors.ERROR));
            return;
        }

        // Get player data to check current value
        Optional<PlayerData> dataOpt = service.getPlayerDataRepository().get(playerUuid);
        if (dataOpt.isEmpty()) {
            sender.sendMessage(Message.raw("Player data not found !")
                .color(MessageColors.ERROR));
            return;
        }

        PlayerData data = dataOpt.get();
        int currentValue = getAttributeValue(data, type);

        // Branch 2: /too attr unallocate VIT - Unallocate all from one attribute
        if (amount == null) {
            unallocateAttribute(service, playerUuid, type, currentValue, sender, store, ref);
            return;
        }

        // Branch 3: /too attr unallocate VIT 5 - Unallocate specific amount
        if (amount <= 0) {
            sender.sendMessage(Message.raw("Amount must be positive !")
                .color(MessageColors.ERROR));
            return;
        }

        // Cap amount to current value
        int toUnallocate = Math.min(amount, currentValue);
        unallocateAttribute(service, playerUuid, type, toUnallocate, sender, store, ref);
    }

    private void unallocateAll(
        AttributeService service,
        UUID uuid,
        PlayerRef sender,
        Store<EntityStore> store,
        Ref<EntityStore> ref
    ) {
        int refunded = service.resetAllAttributes(uuid);

        if (refunded == -1) {
            sender.sendMessage(Message.raw("Failed to reset attributes !")
                .color(MessageColors.ERROR));
            return;
        }

        if (refunded == -2) {
            int totalAllocated = service.getPlayerDataRepository().get(uuid)
                .map(data -> data.getTotalAllocatedPoints())
                .orElse(0);
            int respecCost = (int) Math.ceil(totalAllocated * 0.5);
            sender.sendMessage(Message.empty()
                .insert(Message.raw("Not enough refund points! ").color(MessageColors.ERROR))
                .insert(Message.raw("Need ").color(MessageColors.WARNING))
                .insert(Message.raw(String.valueOf(respecCost)).color(MessageColors.WHITE))
                .insert(Message.raw(". Use Orbs of Realignment to gain more.").color(MessageColors.WARNING)));
            return;
        }

        if (refunded == 0) {
            sender.sendMessage(Message.raw("No points to unallocate - all attributes are already at 0 !")
                .color(MessageColors.WARNING));
            return;
        }

        // Recalculate and apply ECS stats
        ComputedStats stats = service.recalculateStats(uuid);
        applyEcsStats(sender, store, ref, stats, service);

        int respecCost = (int) Math.ceil(refunded * 0.5);
        sender.sendMessage(Message.empty()
            .insert(Message.raw("Unallocated all attributes! ").color(MessageColors.SUCCESS))
            .insert(Message.raw(String.valueOf(refunded)).color(MessageColors.WHITE))
            .insert(Message.raw(" points returned (cost ").color(MessageColors.SUCCESS))
            .insert(Message.raw(String.valueOf(respecCost)).color(MessageColors.WHITE))
            .insert(Message.raw(" refund points).").color(MessageColors.SUCCESS)));

        plugin.getLogger().atInfo().log("[Unallocate] %s reset all attributes, refunded %d points, cost %d refund points",
            sender.getUsername(), refunded, respecCost);
    }

    private void unallocateAttribute(
        AttributeService service,
        UUID uuid,
        AttributeType type,
        int amount,
        PlayerRef sender,
        Store<EntityStore> store,
        Ref<EntityStore> ref
    ) {
        if (amount <= 0) {
            sender.sendMessage(Message.empty()
                .insert(Message.raw(type.getDisplayName()).color(type.getHexColor()))
                .insert(Message.raw(" is already at 0 !").color(MessageColors.ERROR)));
            return;
        }

        // Reduce attribute by amount
        boolean attrSuccess = service.modifyAttribute(uuid, type, -amount);
        if (!attrSuccess) {
            sender.sendMessage(Message.raw("Failed to reduce attribute !")
                .color(MessageColors.ERROR));
            return;
        }

        // Add points back to unallocated pool
        boolean pointsSuccess = service.modifyUnallocatedPoints(uuid, amount);
        if (!pointsSuccess) {
            // Rollback the attribute change
            service.modifyAttribute(uuid, type, amount);
            sender.sendMessage(Message.raw("Failed to refund points !")
                .color(MessageColors.ERROR));
            return;
        }

        // Recalculate and apply ECS stats
        ComputedStats stats = service.recalculateStats(uuid);
        applyEcsStats(sender, store, ref, stats, service);

        // Get new value for display
        int newValue = service.getPlayerDataRepository().get(uuid)
            .map(d -> getAttributeValue(d, type)).orElse(0);

        sender.sendMessage(Message.empty()
            .insert(Message.raw("Unallocated ").color(MessageColors.SUCCESS))
            .insert(Message.raw(String.valueOf(amount)).color(MessageColors.WHITE))
            .insert(Message.raw(" point(s) from ").color(MessageColors.SUCCESS))
            .insert(Message.raw(type.getDisplayName()).color(type.getHexColor()))
            .insert(Message.raw(String.format(" (now %d). Points returned to pool.", newValue)).color(MessageColors.SUCCESS)));

        plugin.getLogger().atInfo().log("[Unallocate] %s unallocated %d from %s (now %d)",
            sender.getUsername(), amount, type.name(), newValue);
    }

    private int getAttributeValue(PlayerData data, AttributeType type) {
        return switch (type) {
            case FIRE -> data.getFire();
            case WATER -> data.getWater();
            case LIGHTNING -> data.getLightning();
            case EARTH -> data.getEarth();
            case WIND -> data.getWind();
            case VOID -> data.getVoidAttr();
        };
    }

    private void applyEcsStats(
        @Nonnull PlayerRef playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nullable ComputedStats stats,
        @Nonnull AttributeService service
    ) {
        if (stats == null) {
            return;
        }

        UUID uuid = playerRef.getUuid();
        StatsApplicationSystem.applyAllStatsAndSync(
            playerRef, store, ref, stats,
            service.getPlayerDataRepository(), uuid
        );
    }
}
