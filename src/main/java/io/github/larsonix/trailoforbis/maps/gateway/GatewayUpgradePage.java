package io.github.larsonix.trailoforbis.maps.gateway;

import au.ellie.hyui.builders.PageBuilder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.maps.gateway.GatewayUpgradeConfig.GatewayTier;
import io.github.larsonix.trailoforbis.maps.gateway.GatewayUpgradeConfig.TierMaterial;
import io.github.larsonix.trailoforbis.maps.gateway.GatewayUpgradeManager.MaterialCheckResult;
import io.github.larsonix.trailoforbis.maps.gateway.GatewayUpgradeManager.MaterialStatus;
import io.github.larsonix.trailoforbis.ui.RPGStyles;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.UUID;

/**
 * HyUI page for the Gateway Upgrade interface.
 *
 * <p>Uses the {@code PageBuilder.fromHtml()} pattern (same as StonePickerPage)
 * to build a custom UI with item icons for materials, proper button text,
 * and a clean upgrade flow.
 *
 * <p>Opened directly from {@link io.github.larsonix.trailoforbis.maps.listeners.RealmPortalDevicePageSupplier}
 * via {@code page.open(store)}, returning {@code null} from tryCreate().
 *
 * @see GatewayUpgradeManager
 * @see GatewayUpgradeConfig
 */
public class GatewayUpgradePage {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // Layout constants
    private static final int CONTAINER_WIDTH = 380;
    private static final int MATERIAL_ROW_HEIGHT = 36;
    private static final int HEADER_HEIGHT = 70;
    private static final int DIVIDER_HEIGHT = 9;
    private static final int ARROW_SECTION_HEIGHT = 40;
    private static final int BUTTON_ROW_HEIGHT = 50;
    private static final int CONTAINER_CHROME_HEIGHT = 60;
    private static final int ICON_SIZE = 28;

    private final PlayerRef player;
    private final UUID worldUuid;
    private final int blockX, blockY, blockZ;
    private final int currentTierIndex;

    public GatewayUpgradePage(
            @Nonnull PlayerRef player,
            @Nonnull UUID worldUuid,
            int blockX, int blockY, int blockZ,
            int currentTierIndex) {

        this.player = player;
        this.worldUuid = worldUuid;
        this.blockX = blockX;
        this.blockY = blockY;
        this.blockZ = blockZ;
        this.currentTierIndex = currentTierIndex;
    }

    // ═══════════════════════════════════════════════════════════════════
    // OPEN
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Opens the gateway upgrade page for the player.
     *
     * @param store The entity store
     */
    public void open(@Nonnull Store<EntityStore> store) {
        try {
            GatewayUpgradeManager manager = getManager();
            if (manager == null) {
                LOGGER.atWarning().log("Cannot open gateway upgrade page - manager not available");
                return;
            }

            GatewayUpgradeConfig config = manager.getConfig();
            GatewayTier currentTier = config.getTier(currentTierIndex);
            GatewayTier nextTier = config.getNextTier(currentTierIndex);

            // Check materials if there's a next tier
            MaterialCheckResult check = null;
            if (nextTier != null) {
                Ref<EntityStore> ref = player.getReference();
                if (ref != null && ref.isValid()) {
                    Player playerEntity = store.getComponent(ref, Player.getComponentType());
                    if (playerEntity != null) {
                        check = manager.checkMaterials(playerEntity, worldUuid, blockX, blockY, blockZ);
                    }
                }
            }

            String html = buildHtml(config, currentTier, nextTier, check);

            PageBuilder builder = PageBuilder.pageForPlayer(player)
                .withLifetime(CustomPageLifetime.CanDismiss)
                .fromHtml(html);

            registerEvents(builder, store, manager, nextTier, check);

            builder.open(store);

            LOGGER.atFine().log("Opened gateway upgrade page for %s (tier %d)",
                player.getUuid(), currentTierIndex);

        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to open gateway upgrade page");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // HTML BUILDING
    // ═══════════════════════════════════════════════════════════════════

    private String buildHtml(
            @Nonnull GatewayUpgradeConfig config,
            GatewayTier currentTier,
            GatewayTier nextTier,
            MaterialCheckResult check) {

        String currentName = currentTier != null ? currentTier.name() : "Unknown";
        int currentMaxLevel = config.getMaxRealmLevel(currentTierIndex);
        String currentMaxText = currentMaxLevel == Integer.MAX_VALUE ? "Unlimited" : String.valueOf(currentMaxLevel);

        boolean isMaxTier = nextTier == null;
        int materialCount = isMaxTier ? 0 : nextTier.materials().size();
        int materialsHeight = materialCount * MATERIAL_ROW_HEIGHT;

        int containerHeight = CONTAINER_CHROME_HEIGHT + HEADER_HEIGHT + DIVIDER_HEIGHT;
        if (isMaxTier) {
            containerHeight += 60; // Max tier message
        } else {
            containerHeight += ARROW_SECTION_HEIGHT + DIVIDER_HEIGHT + materialsHeight + DIVIDER_HEIGHT;
        }
        containerHeight += BUTTON_ROW_HEIGHT;

        StringBuilder html = new StringBuilder(2048);

        html.append("""
            <div class="page-overlay">
                <div class="decorated-container" data-hyui-title="Ancient Gateway"
                     style="anchor-width: %d; anchor-height: %d;">
                    <div class="container-contents" style="layout-mode: Top;">
            """.formatted(CONTAINER_WIDTH, containerHeight));

        // Header: current tier
        buildHeader(html, currentName, currentMaxText);
        appendDivider(html);

        if (isMaxTier) {
            buildMaxTierMessage(html);
        } else {
            // Arrow section: Current → Next
            buildArrowSection(html, currentName, nextTier);
            appendDivider(html);

            // Material requirements with item icons
            buildMaterialRows(html, nextTier, check);
            appendDivider(html);
        }

        // Button row
        buildButtonRow(html, isMaxTier, check);

        html.append("""
                    </div>
                </div>
            </div>
            """);

        return html.toString();
    }

    private void buildHeader(StringBuilder html, String currentName, String currentMaxText) {
        html.append("""
                        <div style="layout-mode: Top; anchor-height: 70; anchor-horizontal: 0; margin-top: -4;"
                             data-hyui-style="Padding: (Horizontal: 20; Top: 6)">
                            <div><p style="font-size: 20; color: %s;">%s</p></div>
                            <div style="anchor-height: 6;"></div>
                            <div><p style="font-size: 13; color: %s;">Max Realm Level: %s</p></div>
                        </div>
            """.formatted(RPGStyles.TITLE_GOLD, escapeHtml(currentName),
                          RPGStyles.TEXT_SECONDARY, currentMaxText));
    }

    private void buildArrowSection(StringBuilder html, String currentName, GatewayTier nextTier) {
        String nextMaxText = nextTier.isUnlimited() ? "Unlimited" : String.valueOf(nextTier.maxRealmLevel());
        html.append("""
                        <div style="layout-mode: Left; anchor-horizontal: 0; anchor-height: 40;"
                             data-hyui-style="Padding: (Horizontal: 20; Vertical: 4)">
                            <div style="flex-weight: 1;">
                                <p style="font-size: 12; color: %s;">%s</p>
                            </div>
                            <div style="anchor-width: 30;">
                                <p style="font-size: 16; color: %s;">-></p>
                            </div>
                            <div style="flex-weight: 1;">
                                <p style="font-size: 12; color: %s;">%s (Lv. %s)</p>
                            </div>
                        </div>
            """.formatted(
                RPGStyles.TEXT_MUTED, escapeHtml(currentName),
                RPGStyles.TITLE_GOLD,
                "#88FF88", escapeHtml(nextTier.name()), nextMaxText));
    }

    private void buildMaterialRows(StringBuilder html, GatewayTier nextTier, MaterialCheckResult check) {
        List<TierMaterial> materials = nextTier.materials();
        List<MaterialStatus> statuses = check != null ? check.materialStatus() : List.of();

        for (int i = 0; i < materials.size(); i++) {
            TierMaterial material = materials.get(i);
            int available = i < statuses.size() ? statuses.get(i).available() : 0;
            boolean satisfied = available >= material.count();
            String color = satisfied ? "#88FF88" : "#FF6666";
            String displayName = formatItemName(material.itemId());

            html.append("""
                        <div style="layout-mode: Left; anchor-horizontal: 0; anchor-height: %d;"
                             data-hyui-style="Padding: (Horizontal: 20; Vertical: 2)">
                            <div style="anchor-width: %d; anchor-height: %d;">
                                <span class="item-icon" data-hyui-item-id="%s"
                                      style="anchor-width: %d; anchor-height: %d;"></span>
                            </div>
                            <div style="anchor-width: 8;"></div>
                            <div style="flex-weight: 1;">
                                <p style="font-size: 13; color: %s;">%s</p>
                            </div>
                            <div style="anchor-width: 80;">
                                <p style="font-size: 13; color: %s;">%d / %d</p>
                            </div>
                        </div>
                """.formatted(
                    MATERIAL_ROW_HEIGHT,
                    ICON_SIZE, ICON_SIZE,
                    escapeHtml(material.itemId()),
                    ICON_SIZE, ICON_SIZE,
                    RPGStyles.TEXT_SECONDARY, escapeHtml(displayName),
                    color, available, material.count()));
        }
    }

    private void buildMaxTierMessage(StringBuilder html) {
        html.append("""
                        <div style="layout-mode: Center; anchor-horizontal: 0; anchor-height: 60;">
                            <p style="font-size: 14; color: #00CCFF;">Maximum tier — all realm levels unlocked</p>
                        </div>
            """);
    }

    private void buildButtonRow(StringBuilder html, boolean isMaxTier, MaterialCheckResult check) {
        boolean canUpgrade = !isMaxTier && check != null && check.canUpgrade();

        html.append("""
                        <div style="layout-mode: Left; anchor-horizontal: 0; anchor-height: 50;"
                             data-hyui-style="Padding: (Horizontal: 20; Vertical: 7)">
                            <button id="close-btn" class="secondary-button"
                                    style="anchor-width: 120; anchor-height: 36;">
                                Close
                            </button>
                            <div style="flex-weight: 1;"></div>
            """);

        if (!isMaxTier) {
            String btnStyle = canUpgrade
                ? "anchor-width: 140; anchor-height: 36; background-color: #2a5a2a;"
                : "anchor-width: 140; anchor-height: 36; background-color: #3a3a3a;";
            html.append("""
                            <button id="upgrade-btn" class="secondary-button"
                                    style="%s">
                                Upgrade
                            </button>
                """.formatted(btnStyle));
        }

        html.append("""
                        </div>
            """);
    }

    private void appendDivider(StringBuilder html) {
        html.append("""
                        <div style="anchor-height: 1; anchor-horizontal: 0; background-color: #333344;"></div>
                        <div style="anchor-height: 8;"></div>
            """);
    }

    // ═══════════════════════════════════════════════════════════════════
    // EVENTS
    // ═══════════════════════════════════════════════════════════════════

    private void registerEvents(
            @Nonnull PageBuilder builder,
            @Nonnull Store<EntityStore> store,
            @Nonnull GatewayUpgradeManager manager,
            GatewayTier nextTier,
            MaterialCheckResult check) {

        // Close button
        builder.addEventListener("close-btn", CustomUIEventBindingType.Activating,
            (data, ctx) -> ctx.getPage().ifPresent(page -> page.close()));

        // Upgrade button (only if upgrade is possible)
        boolean canUpgrade = nextTier != null && check != null && check.canUpgrade();
        if (canUpgrade) {
            builder.addEventListener("upgrade-btn", CustomUIEventBindingType.Activating,
                (data, ctx) -> {
                    ctx.getPage().ifPresent(page -> page.close());
                    handleUpgrade(store, manager);
                });
        }
    }

    private void handleUpgrade(@Nonnull Store<EntityStore> store, @Nonnull GatewayUpgradeManager manager) {
        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) return;

        Player playerEntity = store.getComponent(ref, Player.getComponentType());
        if (playerEntity == null) return;

        manager.tryUpgrade(playerEntity, player, worldUuid, blockX, blockY, blockZ);
    }

    // ═══════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════

    @Nonnull
    private static String formatItemName(@Nonnull String itemId) {
        if (itemId.startsWith("Ingredient_Bar_")) {
            return itemId.substring("Ingredient_Bar_".length()) + " Bar";
        }
        if (itemId.startsWith("Ingredient_")) {
            return itemId.substring("Ingredient_".length()).replace('_', ' ');
        }
        return itemId.replace('_', ' ');
    }

    @Nonnull
    private static String escapeHtml(@Nonnull String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private GatewayUpgradeManager getManager() {
        TrailOfOrbis plugin = TrailOfOrbis.getInstanceOrNull();
        if (plugin == null || plugin.getRealmsManager() == null) return null;
        return plugin.getRealmsManager().getGatewayUpgradeManager();
    }
}
