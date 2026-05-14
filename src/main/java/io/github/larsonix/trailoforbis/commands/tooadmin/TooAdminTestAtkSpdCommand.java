package io.github.larsonix.trailoforbis.commands.tooadmin;

import com.hypixel.hytale.assetstore.map.IndexedLookupTableAssetMap;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.InteractionCooldown;
import com.hypixel.hytale.protocol.UpdateType;
import com.hypixel.hytale.protocol.packets.assets.UpdateRootInteractions;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;

/**
 * Test command for attack speed research. Each mode tests a different approach
 * to solving the client-side cooldown bottleneck.
 *
 * <p>Usage: /tooadmin testatkspd &lt;mode&gt;
 *
 * <p>Modes:
 * <ul>
 *   <li>{@code bypass} — Set clickBypass=true on held weapon's RootInteraction (server-side reflection)</li>
 *   <li>{@code packet} — Send per-player UpdateRootInteractions with halved cooldown</li>
 *   <li>{@code zero} — Send per-player UpdateRootInteractions with near-zero cooldown</li>
 *   <li>{@code reset} — Restore vanilla RootInteraction for held weapon</li>
 *   <li>{@code info} — Show current RootInteraction cooldown info for held weapon</li>
 * </ul>
 *
 * <p>Hold a weapon before running. Each test modifies interaction config for
 * the weapon you're holding, not globally.
 */
public final class TooAdminTestAtkSpdCommand extends AbstractPlayerCommand {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final TrailOfOrbis plugin;
    private final RequiredArg<String> modeArg;

    public TooAdminTestAtkSpdCommand(TrailOfOrbis plugin) {
        super("testatkspd", "Attack speed research tests (hold a weapon)");
        this.plugin = plugin;
        modeArg = this.withRequiredArg("mode", "bypass|packet|zero|reset|info", ArgTypes.STRING);
    }

    @Override
    protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef sender,
            @Nonnull World world
    ) {
        String mode = modeArg.get(context).toLowerCase();

        // Resolve held item's Primary RootInteraction
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            sender.sendMessage(msg("Player component not found", MessageColors.ERROR));
            return;
        }

        Inventory inventory = player.getInventory();
        ItemStack heldItem = inventory.getActiveHotbarItem();
        if (heldItem == null || heldItem.isEmpty()) {
            sender.sendMessage(msg("Hold a weapon first!", MessageColors.ERROR));
            return;
        }

        Item item = heldItem.getItem();
        if (item == null || item == Item.UNKNOWN) {
            sender.sendMessage(msg("Unknown item", MessageColors.ERROR));
            return;
        }

        // Get the Primary interaction ID from the item
        var interactions = item.getInteractions();
        if (interactions == null || interactions.isEmpty()) {
            sender.sendMessage(msg("Item has no interactions", MessageColors.ERROR));
            return;
        }

        // Find Primary interaction
        String primaryRootId = interactions.get(com.hypixel.hytale.protocol.InteractionType.Primary);
        if (primaryRootId == null) {
            sender.sendMessage(msg("Item has no Primary interaction", MessageColors.ERROR));
            return;
        }

        // Get the server-side RootInteraction
        IndexedLookupTableAssetMap<String, RootInteraction> assetMap = RootInteraction.getAssetMap();
        RootInteraction serverRoot = assetMap.getAsset(primaryRootId);
        if (serverRoot == null) {
            sender.sendMessage(msg("RootInteraction not found: " + primaryRootId, MessageColors.ERROR));
            return;
        }

        int assetIndex = assetMap.getIndex(primaryRootId);
        InteractionCooldown cooldown = serverRoot.getCooldown();

        switch (mode) {
            case "info" -> executeInfo(sender, primaryRootId, assetIndex, cooldown, serverRoot);
            case "bypass" -> executeBypass(sender, primaryRootId, cooldown);
            case "packet" -> executePacket(sender, primaryRootId, serverRoot, assetMap, assetIndex, 0.5f);
            case "zero" -> executePacket(sender, primaryRootId, serverRoot, assetMap, assetIndex, 0.02f);
            case "reset" -> executeReset(sender, primaryRootId, serverRoot, assetMap, assetIndex);
            default -> sender.sendMessage(msg("Unknown mode: " + mode + ". Use: info, bypass, packet, zero, reset", MessageColors.ERROR));
        }
    }

    /**
     * Shows current cooldown info for the held weapon's RootInteraction.
     */
    private void executeInfo(PlayerRef sender, String rootId, int assetIndex,
                             InteractionCooldown cooldown, RootInteraction root) {
        sender.sendMessage(msg("[AtkSpd Test] Info for: " + rootId, MessageColors.GOLD));
        sender.sendMessage(msg("  Asset index: " + assetIndex, MessageColors.GRAY));

        if (cooldown == null) {
            sender.sendMessage(msg("  Cooldown: null (uses default 0.35s)", MessageColors.WARNING));
        } else {
            sender.sendMessage(msg(String.format("  Cooldown: %.3fs", cooldown.cooldown), MessageColors.INFO));
            sender.sendMessage(msg("  cooldownId: " + cooldown.cooldownId, MessageColors.GRAY));
            sender.sendMessage(msg("  clickBypass: " + cooldown.clickBypass, MessageColors.GRAY));
            sender.sendMessage(msg("  skipCooldownReset: " + cooldown.skipCooldownReset, MessageColors.GRAY));
            sender.sendMessage(msg("  interruptRecharge: " + cooldown.interruptRecharge, MessageColors.GRAY));
            if (cooldown.chargeTimes != null) {
                sender.sendMessage(msg("  chargeTimes: " + java.util.Arrays.toString(cooldown.chargeTimes), MessageColors.GRAY));
            }
        }

        var settings = root.getSettings();
        if (settings.isEmpty()) {
            sender.sendMessage(msg("  Settings: empty (no per-GameMode overrides)", MessageColors.GRAY));
        } else {
            for (var entry : settings.entrySet()) {
                var s = entry.getValue();
                sender.sendMessage(msg("  Settings[" + entry.getKey() + "]: allowSkipChainOnClick=" + s.allowSkipChainOnClick
                        + (s.cooldown != null ? ", cooldown=" + s.cooldown.cooldown : ""), MessageColors.GRAY));
            }
        }

        LOGGER.atInfo().log("[AtkSpd Test] Info for %s: cooldown=%s, index=%d",
                rootId, cooldown != null ? String.format("%.3f", cooldown.cooldown) : "null", assetIndex);
    }

    /**
     * Test 1: Set clickBypass=true directly on the server-side InteractionCooldown.
     * This is server-only — the client won't see this change.
     * Tests whether the CLIENT independently checks clickBypass from its cached data.
     */
    private void executeBypass(PlayerRef sender, String rootId, InteractionCooldown cooldown) {
        if (cooldown == null) {
            sender.sendMessage(msg("No cooldown on " + rootId + " — nothing to bypass", MessageColors.WARNING));
            return;
        }

        boolean oldValue = cooldown.clickBypass;
        cooldown.clickBypass = true;
        sender.sendMessage(msg("[AtkSpd Test] SERVER-SIDE clickBypass set to TRUE on " + rootId, MessageColors.SUCCESS));
        sender.sendMessage(msg("  Was: " + oldValue + " -> Now: true", MessageColors.GRAY));
        sender.sendMessage(msg("  Client does NOT know about this change.", MessageColors.WARNING));
        sender.sendMessage(msg("  If attacks are faster: server was the bottleneck.", MessageColors.INFO));
        sender.sendMessage(msg("  If attacks are same speed: client is the bottleneck.", MessageColors.INFO));

        LOGGER.atInfo().log("[AtkSpd Test] Set clickBypass=true on %s (was %b)", rootId, oldValue);
    }

    /**
     * Test 2/3: Send per-player UpdateRootInteractions with modified cooldown.
     * This tells the CLIENT about a new cooldown value.
     *
     * @param cooldownMultiplier 0.5 = half cooldown, 0.02 = near-zero
     */
    private void executePacket(PlayerRef sender, String rootId, RootInteraction serverRoot,
                               IndexedLookupTableAssetMap<String, RootInteraction> assetMap,
                               int assetIndex, float cooldownMultiplier) {
        // Clone the protocol packet (same pattern as AnimationSpeedSyncManager)
        com.hypixel.hytale.protocol.RootInteraction packet = serverRoot.toPacket();
        com.hypixel.hytale.protocol.RootInteraction clone = packet.clone();

        float originalCooldown;
        if (clone.cooldown != null) {
            originalCooldown = clone.cooldown.cooldown;
            clone.cooldown.cooldown = originalCooldown * cooldownMultiplier;
        } else {
            // No explicit cooldown — create one with the default (0.35s) modified
            originalCooldown = 0.35f;
            clone.cooldown = new InteractionCooldown();
            clone.cooldown.cooldown = originalCooldown * cooldownMultiplier;
        }

        float newCooldown = clone.cooldown.cooldown;

        // Send per-player packet
        Map<Integer, com.hypixel.hytale.protocol.RootInteraction> interactions = new HashMap<>();
        interactions.put(assetIndex, clone);

        PacketHandler connection = sender.getPacketHandler();
        connection.write(new UpdateRootInteractions(UpdateType.AddOrUpdate, assetMap.getNextIndex(), interactions));

        String modeName = cooldownMultiplier <= 0.05f ? "ZERO" : "HALF";
        sender.sendMessage(msg("[AtkSpd Test] Sent per-player UpdateRootInteractions (" + modeName + ")", MessageColors.SUCCESS));
        sender.sendMessage(msg(String.format("  Cooldown: %.3fs -> %.3fs (x%.2f)", originalCooldown, newCooldown, cooldownMultiplier), MessageColors.INFO));
        sender.sendMessage(msg("  Root: " + rootId + " (index " + assetIndex + ")", MessageColors.GRAY));
        sender.sendMessage(msg("  If attacks are faster: CLIENT uses packet cooldown data!", MessageColors.INFO));
        sender.sendMessage(msg("  If same speed: client ignores mid-session updates.", MessageColors.INFO));

        LOGGER.atInfo().log("[AtkSpd Test] Sent UpdateRootInteractions to %s: %s cooldown %.3f->%.3f",
                sender.getUuid().toString().substring(0, 8), rootId, originalCooldown, newCooldown);
    }

    /**
     * Reset: Send vanilla RootInteraction back to the client.
     */
    private void executeReset(PlayerRef sender, String rootId, RootInteraction serverRoot,
                              IndexedLookupTableAssetMap<String, RootInteraction> assetMap,
                              int assetIndex) {
        // Also restore clickBypass if we changed it
        InteractionCooldown cooldown = serverRoot.getCooldown();
        if (cooldown != null) {
            cooldown.clickBypass = false;
        }

        // Send vanilla packet
        com.hypixel.hytale.protocol.RootInteraction packet = serverRoot.toPacket();
        Map<Integer, com.hypixel.hytale.protocol.RootInteraction> interactions = new HashMap<>();
        interactions.put(assetIndex, packet);

        PacketHandler connection = sender.getPacketHandler();
        connection.write(new UpdateRootInteractions(UpdateType.AddOrUpdate, assetMap.getNextIndex(), interactions));

        sender.sendMessage(msg("[AtkSpd Test] Reset " + rootId + " to vanilla (server + client)", MessageColors.SUCCESS));

        LOGGER.atInfo().log("[AtkSpd Test] Reset %s to vanilla for %s",
                rootId, sender.getUuid().toString().substring(0, 8));
    }

    private static Message msg(String text, String color) {
        return Message.raw(text).color(color);
    }
}
