package io.github.larsonix.trailoforbis.gems.socket;

import au.ellie.hyui.builders.PageBuilder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gems.model.GemData;
import io.github.larsonix.trailoforbis.gems.model.GemType;
import javax.annotation.Nonnull;

public final class GemSocketPage {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private final PlayerRef playerRef;
    private final GearData gearData;
    private final GemData gemToSocket;
    private final short gearSlot;
    private final ItemContainer gearContainer;

    public GemSocketPage(@Nonnull PlayerRef playerRef, @Nonnull GearData gearData, @Nonnull GemData gemToSocket, short gearSlot, @Nonnull ItemContainer gearContainer) {
        this.playerRef = playerRef;
        this.gearData = gearData;
        this.gemToSocket = gemToSocket;
        this.gearSlot = gearSlot;
        this.gearContainer = gearContainer;
    }

    public void open(@Nonnull Store<EntityStore> store) {
        String html = this.buildHtml();
        PageBuilder builder = (PageBuilder) PageBuilder.pageForPlayer(this.playerRef).withLifetime(CustomPageLifetime.CanDismiss).fromHtml(html);
        builder.addEventListener("slot-active", CustomUIEventBindingType.Activating, (data, ctx) -> {
            this.handleSlotClick(0, store);
            ctx.getPage().ifPresent(page -> page.close());
        });
        for (int i = 1; i <= 3; ++i) {
            int slotIndex = i;
            builder.addEventListener("slot-support-" + i, CustomUIEventBindingType.Activating, (data, ctx) -> {
                this.handleSlotClick(slotIndex, store);
                ctx.getPage().ifPresent(page -> page.close());
            });
        }
        builder.open(store);
    }

    private void handleSlotClick(int gemSlotIndex, Store<EntityStore> store) {
        Ref ref = this.playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }
        World world = ((EntityStore) store.getExternalData()).getWorld();
        world.execute(() -> {
            Player player = (Player) store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                return;
            }
            Inventory inventory = player.getInventory();
            if (inventory == null) {
                return;
            }
            GemSocketService service = new GemSocketService();
            boolean success = gemSlotIndex == 0 ? service.socketActiveGem(this.playerRef, inventory, this.gearContainer, this.gearSlot, this.gemToSocket) : service.socketSupportGem(this.playerRef, inventory, this.gearContainer, this.gearSlot, this.gemToSocket);
            if (success) {
                this.playerRef.sendMessage(Message.empty().insert(Message.raw("Gem socketed!").color("#55FF55")));
            }
        });
    }

    private String buildHtml() {
        StringBuilder html = new StringBuilder();
        html.append("<div class=\"page-overlay\">\n    <div class=\"decorated-container\" data-hyui-title=\"Socket Gem\"\n         style=\"anchor-width: 350; anchor-height: 250;\">\n        <div class=\"container-contents\">\n            <div style=\"layout-mode: Top; anchor-horizontal: 0;\">\n");
        String gemLabel = this.gemToSocket.gemType() == GemType.ACTIVE ? "Active Gem" : "Support Gem";
        html.append(String.format("                <div style=\"margin-top: -20;\">\n                    <p style=\"color: #FFFFFF; font-size: 14;\">Socketing: %s (Lv%d Q%d)</p>\n                </div>\n                <div style=\"margin-top: 5;\">\n                    <p style=\"color: #888888; font-size: 11;\">Select a slot:</p>\n                </div>\n", this.gemToSocket.gemId(), this.gemToSocket.level(), this.gemToSocket.quality()));
        String activeLabel = this.gearData.hasActiveGem() ? "Active: " + this.gearData.activeGem().gemId() : "Active: [Empty]";
        String activeColor = this.gearData.hasActiveGem() ? "#FFD700" : "#55FF55";
        html.append(String.format("                <div style=\"margin-top: 8;\">\n                    <button id=\"slot-active\" class=\"secondary-button\"\n                            style=\"anchor-width: 280; anchor-height: 28; background-color: #1a1a3e; color: %s; font-size: 12;\">\n                        %s\n                    </button>\n                </div>\n", activeColor, activeLabel));
        for (int i = 1; i <= 3; ++i) {
            String color;
            String label;
            boolean occupied;
            boolean unlocked = i <= this.gearData.supportSlotCount();
            boolean bl = occupied = i - 1 < this.gearData.supportGems().size();
            if (!unlocked) {
                label = "Support " + i + ": [Locked]";
                color = "#555555";
            } else if (occupied) {
                GemData existing = this.gearData.supportGems().get(i - 1);
                label = "Support " + i + ": " + existing.gemId();
                color = "#3498DB";
            } else {
                label = "Support " + i + ": [Empty]";
                color = "#55FF55";
            }
            String buttonClass = unlocked ? "secondary-button" : "small-tertiary-button";
            html.append(String.format("                <div style=\"margin-top: 4;\">\n                    <button id=\"slot-support-%d\" class=\"%s\"\n                            style=\"anchor-width: 280; anchor-height: 28; background-color: #1a1a3e; color: %s; font-size: 12;\">\n                        %s\n                    </button>\n                </div>\n", i, buttonClass, color, label));
        }
        html.append("            </div>\n        </div>\n    </div>\n</div>\n");
        return html.toString();
    }
}
