package io.github.larsonix.trailoforbis.commands.too.attr;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import io.github.larsonix.trailoforbis.commands.base.OpenPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.api.ServiceRegistry;
import io.github.larsonix.trailoforbis.api.services.AttributeService;
import io.github.larsonix.trailoforbis.attributes.AttributeType;
import io.github.larsonix.trailoforbis.database.models.PlayerData;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;
import java.util.Optional;
import java.util.UUID;

/**
 * Allocates 1 point to an attribute.
 *
 * <p>Usage: /too attr allocate &lt;str|dex|int|vit|lck&gt;
 */
public final class TooAttrAllocateCommand extends OpenPlayerCommand {
    @SuppressWarnings("unused")
    private final TrailOfOrbis plugin;
    private final RequiredArg<String> typeArg;

    public TooAttrAllocateCommand(TrailOfOrbis plugin) {
        super("allocate", "Allocate a point to an attribute");
        this.addAliases("add", "alloc");
        this.plugin = plugin;

        typeArg = this.withRequiredArg("type", "Element: fire/water/lightning/earth/wind/void", ArgTypes.STRING);
    }

    @Override
    protected void execute(
        @Nonnull CommandContext context,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull PlayerRef player,
        @Nonnull World world
    ) {
        UUID uuid = player.getUuid();
        String attrName = typeArg.get(context);

        // Parse attribute type
        AttributeType type = AttributeType.fromString(attrName);
        if (type == null) {
            player.sendMessage(Message.raw("Invalid element ! Use : fire, water, lightning, earth, wind, or void").color(MessageColors.ERROR));
            return;
        }

        // Attempt allocation
        AttributeService attributeService = ServiceRegistry.require(AttributeService.class);
        boolean success = attributeService.allocateAttribute(uuid, type);
        if (success) {
            Optional<PlayerData> dataOpt = attributeService.getPlayerDataRepository().get(uuid);
            int newValue = dataOpt.map(d -> getAttributeValue(d, type)).orElse(0);
            player.sendMessage(Message.empty()
                .insert(Message.raw("Allocated 1 point to ").color(MessageColors.SUCCESS))
                .insert(Message.raw(type.getDisplayName()).color(type.getHexColor()))
                .insert(Message.raw(String.format(" ! (now %d)", newValue)).color(MessageColors.SUCCESS)));
        } else {
            player.sendMessage(Message.raw("Allocation failed ! Do you have unallocated points ?").color(MessageColors.ERROR));
        }
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
}
