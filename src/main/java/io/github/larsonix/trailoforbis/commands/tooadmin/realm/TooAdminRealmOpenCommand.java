package io.github.larsonix.trailoforbis.commands.tooadmin.realm;

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
import io.github.larsonix.trailoforbis.commands.admin.AdminCommandHelper;
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.maps.RealmsManager;
import io.github.larsonix.trailoforbis.maps.core.RealmBiomeType;
import io.github.larsonix.trailoforbis.maps.core.RealmLayoutSize;
import io.github.larsonix.trailoforbis.maps.core.RealmMapData;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Opens a test realm with specified parameters.
 *
 * <p>Usage: /tooadmin realm open &lt;biome&gt; &lt;size&gt; &lt;level&gt;
 */
public final class TooAdminRealmOpenCommand extends AbstractPlayerCommand {

    private final TrailOfOrbis plugin;
    private final RequiredArg<String> biomeArg;
    private final RequiredArg<String> sizeArg;
    private final RequiredArg<Integer> levelArg;

    public TooAdminRealmOpenCommand(TrailOfOrbis plugin) {
        super("open", "Open a test realm");
        this.plugin = plugin;

        biomeArg = this.withRequiredArg("biome", "Biome type (forest, desert, volcano, etc.)", ArgTypes.STRING);
        sizeArg = this.withRequiredArg("size", "Size (small, medium, large, massive)", ArgTypes.STRING);
        levelArg = this.withRequiredArg("level", "Realm level (1-" + GearData.MAX_LEVEL + ")", ArgTypes.INTEGER);
    }

    @Override
    protected void execute(
        @Nonnull CommandContext context,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull PlayerRef sender,
        @Nonnull World world
    ) {
        RealmsManager realmsManager = plugin.getRealmsManager();
        if (realmsManager == null) {
            sender.sendMessage(Message.raw("Realms system is not enabled !").color(MessageColors.ERROR));
            return;
        }

        String biomeStr = biomeArg.get(context);
        String sizeStr = sizeArg.get(context);
        Integer level = levelArg.get(context);

        // Parse biome
        RealmBiomeType biome;
        try {
            biome = RealmBiomeType.fromString(biomeStr);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Message.empty()
                .insert(Message.raw("Invalid biome : ").color(MessageColors.ERROR))
                .insert(Message.raw(biomeStr).color(MessageColors.WHITE)));
            sender.sendMessage(Message.empty()
                .insert(Message.raw("Valid : ").color(MessageColors.GRAY))
                .insert(Message.raw(getBiomeList()).color(MessageColors.WHITE)));
            return;
        }

        // Parse size
        RealmLayoutSize size;
        try {
            size = RealmLayoutSize.fromString(sizeStr);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Message.empty()
                .insert(Message.raw("Invalid size : ").color(MessageColors.ERROR))
                .insert(Message.raw(sizeStr).color(MessageColors.WHITE)));
            sender.sendMessage(Message.empty()
                .insert(Message.raw("Valid : ").color(MessageColors.GRAY))
                .insert(Message.raw(getSizeList()).color(MessageColors.WHITE)));
            return;
        }

        // Validate level
        int realmLevel = Math.max(1, Math.min(level, GearData.MAX_LEVEL));

        // Check if player is already in a realm
        if (realmsManager.isPlayerInRealm(sender.getUuid())) {
            sender.sendMessage(Message.raw("You are already in a realm! Use /tooadmin realm exit first.").color(MessageColors.ERROR));
            return;
        }

        // Check if we can create more realms
        if (!realmsManager.canCreateMoreRealms()) {
            sender.sendMessage(Message.raw("Maximum concurrent realms reached !").color(MessageColors.ERROR));
            return;
        }

        // Create map data
        RealmMapData mapData = RealmMapData.builder()
            .biome(biome)
            .size(size)
            .level(realmLevel)
            .rarity(GearRarity.COMMON)
            .quality(50)
            .identified(true)
            .build();

        // Validate
        var validation = realmsManager.validateMapData(mapData);
        if (!validation.valid()) {
            sender.sendMessage(Message.empty()
                .insert(Message.raw("Cannot create realm : ").color(MessageColors.ERROR))
                .insert(Message.raw(validation.errorMessage()).color(MessageColors.WHITE)));
            return;
        }

        // Send feedback
        sender.sendMessage(Message.empty()
            .insert(Message.raw("[TooAdmin] ").color(MessageColors.GOLD))
            .insert(Message.raw("Creating realm...").color(MessageColors.WHITE)));

        sender.sendMessage(Message.empty()
            .insert(Message.raw("  Biome : ").color(MessageColors.GRAY))
            .insert(Message.raw(biome.getDisplayName()).color(MessageColors.WHITE)));
        sender.sendMessage(Message.empty()
            .insert(Message.raw("  Size : ").color(MessageColors.GRAY))
            .insert(Message.raw(size.getDisplayName()).color(MessageColors.WHITE)));
        sender.sendMessage(Message.empty()
            .insert(Message.raw("  Level : ").color(MessageColors.GRAY))
            .insert(Message.raw(String.valueOf(realmLevel)).color(MessageColors.WHITE)));

        // Open the realm
        final RealmBiomeType finalBiome = biome;
        final RealmLayoutSize finalSize = size;
        final int finalLevel = realmLevel;

        realmsManager.openRealm(mapData, sender.getUuid(), world.getWorldConfig().getUuid())
            .thenAccept(realm -> {
                sender.sendMessage(Message.empty()
                    .insert(Message.raw("[TooAdmin] ").color(MessageColors.GOLD))
                    .insert(Message.raw("Realm created! ID : ").color(MessageColors.SUCCESS))
                    .insert(Message.raw(realm.getRealmId().toString().substring(0, 8)).color(MessageColors.WHITE)));

                // Auto-enter the realm
                sender.sendMessage(Message.raw("Teleporting you into the realm...").color(MessageColors.GRAY));

                realmsManager.enterRealm(sender.getUuid(), realm.getRealmId())
                    .thenAccept(success -> {
                        if (success) {
                            sender.sendMessage(Message.empty()
                                .insert(Message.raw("[TooAdmin] ").color(MessageColors.GOLD))
                                .insert(Message.raw("Entered realm !").color(MessageColors.SUCCESS)));
                        } else {
                            sender.sendMessage(Message.empty()
                                .insert(Message.raw("[TooAdmin] ").color(MessageColors.GOLD))
                                .insert(Message.raw("Failed to enter realm automatically.").color(MessageColors.ERROR)));
                        }
                    });

                AdminCommandHelper.logAdminAction(plugin, sender, "REALM OPEN",
                    sender.getUsername(), finalBiome + " " + finalSize, finalLevel);
            })
            .exceptionally(ex -> {
                sender.sendMessage(Message.empty()
                    .insert(Message.raw("[TooAdmin] ").color(MessageColors.GOLD))
                    .insert(Message.raw("Failed to create realm : ").color(MessageColors.ERROR))
                    .insert(Message.raw(ex.getMessage()).color(MessageColors.WHITE)));
                return null;
            });
    }

    private String getBiomeList() {
        return Arrays.stream(RealmBiomeType.values())
            .map(b -> b.name().toLowerCase())
            .collect(Collectors.joining(", "));
    }

    private String getSizeList() {
        return Arrays.stream(RealmLayoutSize.values())
            .map(s -> s.name().toLowerCase())
            .collect(Collectors.joining(", "));
    }
}
