package io.github.larsonix.trailoforbis.ui.attributes;

import au.ellie.hyui.builders.ContainerBuilder;
import au.ellie.hyui.builders.GroupBuilder;
import au.ellie.hyui.builders.HyUIAnchor;
import au.ellie.hyui.builders.LabelBuilder;
import au.ellie.hyui.builders.PageBuilder;
import au.ellie.hyui.events.UIContext;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.api.ServiceRegistry;
import io.github.larsonix.trailoforbis.api.services.AttributeService;
import io.github.larsonix.trailoforbis.api.services.ConfigService;
import io.github.larsonix.trailoforbis.attributes.AttributeType;
import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.config.RPGConfig;
import io.github.larsonix.trailoforbis.database.models.PlayerData;
import io.github.larsonix.trailoforbis.maps.RealmsManager;
import io.github.larsonix.trailoforbis.sanctum.SkillSanctumManager;
import io.github.larsonix.trailoforbis.systems.StatsApplicationSystem;
import io.github.larsonix.trailoforbis.ui.RPGStyles;
import io.github.larsonix.trailoforbis.ui.stats.StatsPage;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * HyUI-based attribute allocation page.
 *
 * <p>Allows players to allocate attribute points with a pending changes
 * workflow. Uses HYUIML for layout with dynamic updates via ctx.updatePage().
 *
 * <p>Features:
 * <ul>
 *   <li>All 6 elements: FIRE, WATER, LIGHTNING, EARTH, WIND, VOID</li>
 *   <li>Pending changes pattern with Save/Cancel workflow</li>
 *   <li>-/+1/+5 allocation buttons</li>
 *   <li>Delta display showing pending allocations</li>
 *   <li>Navigation footer with Close/Stats/Skill Tree buttons</li>
 * </ul>
 */
public class AttributePage {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // ═══════════════════════════════════════════════════════════════════
    // LAYOUT CONSTANTS
    // ═══════════════════════════════════════════════════════════════════

    private static final int CONTAINER_WIDTH = 700;
    private static final int CONTAINER_HEIGHT = 531;  // 6 rows × 60px + header 57 + pending 48 + margin
    private static final int PENDING_ROW_HEIGHT = 48; // 42px row + 6px breathing room
    private static final int UI_TOP_OFFSET = 210;     // Lowered to accommodate taller 6-element container
    private static final int ROW_HEIGHT = 60;         // Fits 38px name line + description
    private static final int BUTTON_SIZE = 32;        // Reduced by 20%
    private static final int ROW_GAP = 8;             // Reduced to compensate for taller rows
    private static final int NAV_BUTTON_WIDTH = 210;
    private static final int NAV_BUTTON_HEIGHT = 53;

    private final TrailOfOrbis plugin;
    private final PlayerRef player;
    private final Map<AttributeType, Integer> pendingChanges = new EnumMap<>(AttributeType.class);

    // Store reference for applying stats after save
    private Store<EntityStore> store;

    /**
     * Creates a new attribute page for a player.
     *
     * @param plugin The plugin instance
     * @param player The player to show the page for
     */
    public AttributePage(@Nonnull TrailOfOrbis plugin, @Nonnull PlayerRef player) {
        this.plugin = plugin;
        this.player = player;

        // Initialize pending changes to 0
        for (AttributeType type : AttributeType.values()) {
            pendingChanges.put(type, 0);
        }
    }

    /**
     * Opens the attribute page for the player.
     *
     * @param store The entity store
     */
    public void open(@Nonnull Store<EntityStore> store) {
        this.store = store;

        try {
            // Get player data
            AttributeService attributeService = ServiceRegistry.require(AttributeService.class);
            ConfigService configService = ServiceRegistry.require(ConfigService.class);
            UUID uuid = player.getUuid();

            Optional<PlayerData> dataOpt = attributeService.getPlayerDataRepository().get(uuid);
            if (dataOpt.isEmpty()) {
                LOGGER.at(Level.WARNING).log("Cannot open attribute page - no player data for %s", player.getUsername());
                return;
            }

            PlayerData data = dataOpt.get();
            RPGConfig.AttributeConfig attrs = configService.getRPGConfig().getAttributes();

            // Build HTML
            String html = buildHtml(data, attrs);

            // Build page
            PageBuilder builder = PageBuilder.pageForPlayer(player)
                .withLifetime(CustomPageLifetime.CanDismiss)
                .fromHtml(html);

            // Register event handlers
            registerEventHandlers(builder, data, attrs);

            // Open the page
            builder.open(store);

            LOGGER.at(Level.INFO).log("Opened attribute page for %s", player.getUsername());

        } catch (Exception e) {
            LOGGER.at(Level.SEVERE).withCause(e).log("Failed to open attribute page for %s", player.getUsername());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // EVENT HANDLERS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Registers all button event handlers.
     */
    private void registerEventHandlers(
        @Nonnull PageBuilder builder,
        @Nonnull PlayerData initialData,
        @Nonnull RPGConfig.AttributeConfig attrs
    ) {
        // Close button - use HyUI's page.close() method
        builder.addEventListener("close-btn", CustomUIEventBindingType.Activating,
            (data, ctx) -> ctx.getPage().ifPresent(page -> page.close()));

        // Stats button - direct page navigation
        // Don't close current page - opening a new page auto-replaces it
        builder.addEventListener("stats-btn", CustomUIEventBindingType.Activating,
            (data, ctx) -> {
                // Get ref from the captured player
                Ref<EntityStore> ref = player.getReference();
                if (ref == null || !ref.isValid()) return;

                // Get store from ref (not from world directly) - ensures consistency
                Store<EntityStore> freshStore = ref.getStore();
                World world = freshStore.getExternalData().getWorld();

                world.execute(() -> {
                    // Re-get playerRef from store to ensure consistency
                    PlayerRef freshPlayer = freshStore.getComponent(ref, PlayerRef.getComponentType());
                    if (freshPlayer != null) {
                        new StatsPage(plugin, freshPlayer).open(freshStore);
                    }
                });
            });

        // Skill Tree button - toggles 3D Skill Sanctum (enter/exit)
        builder.addEventListener("skilltree-btn", CustomUIEventBindingType.Activating,
            (data, ctx) -> {
                ctx.getPage().ifPresent(page -> page.close());

                // Block access while in a combat realm
                RealmsManager realmsManager = plugin.getRealmsManager();
                if (realmsManager != null && realmsManager.isPlayerInCombatRealm(player)) {
                    player.sendMessage(com.hypixel.hytale.server.core.Message.raw("Cannot access Skill Sanctum while in a combat realm !").color(MessageColors.ERROR));
                    return;
                }

                SkillSanctumManager sanctum = plugin.getSkillSanctumManager();
                if (sanctum != null && sanctum.isEnabled()) {
                    UUID playerId = player.getUuid();
                    if (sanctum.hasActiveSanctum(playerId)) {
                        // Already in sanctum - exit to overworld
                        sanctum.closeSanctum(playerId);
                    } else {
                        // Not in sanctum - enter it
                        sanctum.openSanctum(player);
                    }
                }
            });

        // Save button
        builder.addEventListener("save-btn", CustomUIEventBindingType.Activating,
            (data, ctx) -> {
                handleSave();
                refreshPage(ctx, attrs);
            });

        // Cancel button
        builder.addEventListener("cancel-btn", CustomUIEventBindingType.Activating,
            (data, ctx) -> {
                handleCancel();
                refreshPage(ctx, attrs);
            });

        // Attribute buttons for each attribute type
        for (AttributeType type : AttributeType.values()) {
            String prefix = type.name().toLowerCase().substring(0, 3);

            // Minus button (undo pending only)
            builder.addEventListener(prefix + "-sub", CustomUIEventBindingType.Activating,
                (data, ctx) -> {
                    handleSubtract(type);
                    refreshPage(ctx, attrs);
                });

            // Plus 1 button
            builder.addEventListener(prefix + "-add1", CustomUIEventBindingType.Activating,
                (data, ctx) -> {
                    handleAdd(type, 1);
                    refreshPage(ctx, attrs);
                });

            // Plus 5 button
            builder.addEventListener(prefix + "-add5", CustomUIEventBindingType.Activating,
                (data, ctx) -> {
                    handleAdd(type, 5);
                    refreshPage(ctx, attrs);
                });
        }
    }

    /**
     * Handles subtract button click - only undoes pending allocations.
     */
    private void handleSubtract(@Nonnull AttributeType type) {
        int pending = pendingChanges.get(type);
        if (pending > 0) {
            // Undo a pending (unsaved) allocation — free
            pendingChanges.put(type, pending - 1);
            return;
        }

        // Go into negative pending — will cost 1 refund point per negative point on Save
        UUID uuid = player.getUuid();
        AttributeService service = ServiceRegistry.require(AttributeService.class);
        Optional<PlayerData> dataOpt = service.getPlayerDataRepository().get(uuid);
        if (dataOpt.isEmpty()) return;

        PlayerData data = dataOpt.get();
        int currentAllocated = type.getValue(data);
        int negativePending = -pending; // how many negative already pending
        if (negativePending >= currentAllocated) return; // can't go below 0

        // Check if player has enough refund points for total pending negative changes
        int totalNegativePending = getTotalNegativePending();
        int availableRefundPoints = service.getAttributeRefundPoints(uuid);
        if (totalNegativePending + 1 > availableRefundPoints) {
            player.sendMessage(com.hypixel.hytale.server.core.Message.raw("No attribute refund points remaining !").color("#FF5555"));
            return;
        }

        pendingChanges.put(type, pending - 1);
    }

    /**
     * Handles add button click.
     */
    private void handleAdd(@Nonnull AttributeType type, int amount) {
        AttributeService attributeService = ServiceRegistry.require(AttributeService.class);
        UUID uuid = player.getUuid();
        Optional<PlayerData> dataOpt = attributeService.getPlayerDataRepository().get(uuid);
        if (dataOpt.isEmpty()) return;

        int available = dataOpt.get().getUnallocatedPoints();
        int totalPending = getTotalPendingPoints();
        int remaining = available - totalPending;

        int toAdd = Math.min(amount, remaining);
        if (toAdd > 0) {
            pendingChanges.put(type, pendingChanges.get(type) + toAdd);
        }
    }

    /**
     * Applies all pending changes to the database.
     */
    private void handleSave() {
        boolean hasChanges = pendingChanges.values().stream().anyMatch(v -> v != 0);
        if (!hasChanges) return;

        AttributeService attributeService = ServiceRegistry.require(AttributeService.class);
        UUID uuid = player.getUuid();

        // Apply all pending changes
        for (Map.Entry<AttributeType, Integer> entry : pendingChanges.entrySet()) {
            int amount = entry.getValue();
            if (amount > 0) {
                // Positive: allocate points (free, costs unallocated points)
                for (int i = 0; i < amount; i++) {
                    attributeService.allocateAttribute(uuid, entry.getKey());
                }
            } else if (amount < 0) {
                // Negative: deallocate points (costs 1 refund point per point)
                int toRemove = -amount;
                for (int i = 0; i < toRemove; i++) {
                    if (attributeService.getAttributeRefundPoints(uuid) <= 0) break;
                    attributeService.modifyAttributeRefundPoints(uuid, -1);
                    attributeService.modifyAttribute(uuid, entry.getKey(), -1);
                    attributeService.modifyUnallocatedPoints(uuid, 1);
                }
            }
        }

        // Clear pending changes
        for (AttributeType type : AttributeType.values()) {
            pendingChanges.put(type, 0);
        }

        // Apply updated stats to ECS components
        Optional<PlayerData> updatedDataOpt = attributeService.getPlayerDataRepository().get(uuid);
        if (updatedDataOpt.isPresent()) {
            ComputedStats stats = updatedDataOpt.get().getComputedStats();
            if (stats != null && store != null) {
                StatsApplicationSystem.applyAllStatsAndSync(
                    player, store, player.getReference(), stats,
                    attributeService.getPlayerDataRepository(), uuid
                );
            }
        }

        LOGGER.at(Level.INFO).log("Saved attribute changes for %s", player.getUsername());
    }

    /**
     * Discards all pending changes.
     */
    private void handleCancel() {
        for (AttributeType type : AttributeType.values()) {
            pendingChanges.put(type, 0);
        }
    }

    /**
     * Refreshes the page UI after state changes.
     */
    private void refreshPage(@Nonnull UIContext ctx, @Nonnull RPGConfig.AttributeConfig attrs) {
        AttributeService attributeService = ServiceRegistry.require(AttributeService.class);
        UUID uuid = player.getUuid();
        Optional<PlayerData> dataOpt = attributeService.getPlayerDataRepository().get(uuid);
        if (dataOpt.isEmpty()) return;

        PlayerData data = dataOpt.get();
        int totalPositivePending = getTotalPendingPoints();
        int totalNegativePending = getTotalNegativePending();
        // Positive pending costs unallocated, negative pending returns unallocated
        int remaining = data.getUnallocatedPoints() - totalPositivePending + totalNegativePending;

        // Update points display
        ctx.getById("points-label", LabelBuilder.class).ifPresent(label -> {
            label.withText(remaining + " Unallocated");
        });

        // Update refund points display (accounts for pending negative changes)
        int availableRefundPoints = data.getAttributeRefundPoints() - totalNegativePending;
        ctx.getById("refund-label", LabelBuilder.class).ifPresent(label -> {
            label.withText(availableRefundPoints + " Refundable");
        });

        // Update each attribute row
        for (AttributeType type : AttributeType.values()) {
            String prefix = type.name().toLowerCase().substring(0, 3);
            int baseValue = type.getValue(data);
            int pending = pendingChanges.get(type);

            // Update value label
            ctx.getById(prefix + "-value", LabelBuilder.class).ifPresent(label -> {
                label.withText(String.valueOf(baseValue));
            });

            // Update delta label (green for positive, red for negative)
            ctx.getById(prefix + "-delta", LabelBuilder.class).ifPresent(label -> {
                if (pending > 0) {
                    label.withTextSpans(List.of(Message.raw("+" + pending).color("#55FF55")));
                    label.withVisible(true);
                } else if (pending < 0) {
                    label.withTextSpans(List.of(Message.raw(String.valueOf(pending)).color("#FF5555")));
                    label.withVisible(true);
                } else {
                    label.withVisible(false);
                }
            });

            // Expand/collapse delta wrapper (0px when no pending, 45px when active)
            ctx.getById(prefix + "-delta-wrap", GroupBuilder.class).ifPresent(wrapper -> {
                wrapper.withAnchor(new HyUIAnchor().setWidth(pending != 0 ? 45 : 0));
            });
        }

        // Update save/cancel visibility (any pending change, positive or negative)
        boolean hasPending = pendingChanges.values().stream().anyMatch(v -> v != 0);
        ctx.getById("pending-row", GroupBuilder.class).ifPresent(group -> {
            group.withVisible(hasPending);
            group.withFlexWeight(hasPending ? 0 : 0); // Keep 0 flex weight either way
        });

        // Update container height based on pending state
        // Note: decorated-container maps to ContainerBuilder, not GroupBuilder
        ctx.getById("main-container", ContainerBuilder.class).ifPresent(container -> {
            int height = hasPending ? CONTAINER_HEIGHT + PENDING_ROW_HEIGHT : CONTAINER_HEIGHT;
            container.withAnchor(new HyUIAnchor().setWidth(CONTAINER_WIDTH).setHeight(height));
        });

        // Rebuild page
        ctx.updatePage(true);
    }

    private int getTotalPendingPoints() {
        // Only count positive pending (allocations that cost unallocated points)
        return pendingChanges.values().stream().filter(v -> v > 0).mapToInt(Integer::intValue).sum();
    }

    /**
     * Gets the total number of negative pending changes (deallocations that cost refund points).
     */
    private int getTotalNegativePending() {
        return pendingChanges.values().stream().filter(v -> v < 0).mapToInt(v -> -v).sum();
    }

    // ═══════════════════════════════════════════════════════════════════
    // HTML BUILDING
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Builds the complete HTML for the attribute page.
     */
    @Nonnull
    private String buildHtml(@Nonnull PlayerData data, @Nonnull RPGConfig.AttributeConfig attrs) {
        StringBuilder sb = new StringBuilder();

        int remaining = data.getUnallocatedPoints();
        int refundPoints = data.getAttributeRefundPoints();

        // Page overlay with fixed top position (prevents vertical re-centering on height change)
        sb.append("<div class=\"page-overlay\">\n");
        sb.append("  <div style=\"layout-mode: Center; anchor-horizontal: 0; anchor-top: ").append(UI_TOP_OFFSET).append(";\">\n");
        sb.append("    <div style=\"layout-mode: Top;\">\n");

        // Decorated container (id for dynamic height adjustment)
        sb.append("      <div id=\"main-container\" class=\"decorated-container\" data-hyui-title=\"Attribute Allocation\" ");
        sb.append("style=\"anchor-width: ").append(CONTAINER_WIDTH).append("; anchor-height: ").append(CONTAINER_HEIGHT).append(";\">\n");
        sb.append("        <div class=\"container-contents\" style=\"layout-mode: Top;\">\n");

        // Header with unallocated + refund points
        sb.append(buildHeader(remaining, refundPoints));

        // Attribute rows
        for (AttributeType type : AttributeType.values()) {
            sb.append(buildAttributeRow(type, data, attrs));
        }

        // Pending changes row (initially hidden if no pending)
        sb.append(buildPendingRow());

        // Footer spacer
        sb.append("          <div style=\"flex-weight: 1;\"></div>\n");

        sb.append("        </div>\n");
        sb.append("      </div>\n");

        // Navigation buttons below container
        sb.append(buildNavigationFooter());

        sb.append("    </div>\n");
        sb.append("  </div>\n");
        sb.append("</div>\n");

        return sb.toString();
    }

    /**
     * Builds the header section with unallocated points display.
     */
    @Nonnull
    private String buildHeader(int remaining, int refundPoints) {
        StringBuilder sb = new StringBuilder();

        sb.append("          <div style=\"anchor-height: 6;\"></div>\n");

        // Header row - using Padding for vertical centering
        sb.append("          <div style=\"layout-mode: Left; anchor-height: 34; anchor-horizontal: 0;\" data-hyui-style=\"Padding: (Horizontal: 20; Top: 6)\">\n");
        sb.append("            <div style=\"flex-weight: 1;\"><p style=\"font-size: 22; color: ").append(RPGStyles.TITLE_GOLD).append(";\">Allocate Your Points</p></div>\n");
        sb.append("            <div><p id=\"points-label\" style=\"font-size: 14; color: ")
          .append(RPGStyles.TEXT_SECONDARY)
          .append(";\">").append(remaining).append(" Unallocated</p></div>\n");
        sb.append("            <div><p style=\"font-size: 14; color: ").append(RPGStyles.TEXT_MUTED).append(";\"> | </p></div>\n");
        sb.append("            <div><p id=\"refund-label\" style=\"font-size: 14; color: ")
          .append(RPGStyles.TEXT_MUTED)
          .append(";\">").append(refundPoints).append(" Refundable</p></div>\n");
        sb.append("          </div>\n");

        // Divider
        sb.append("          <div style=\"anchor-height: 1; anchor-horizontal: 0; background-color: #333344;\"></div>\n");
        sb.append("          <div style=\"anchor-height: 16;\"></div>\n");

        return sb.toString();
    }

    /**
     * Builds a single attribute row with name, value, delta, and buttons.
     */
    @Nonnull
    private String buildAttributeRow(
        @Nonnull AttributeType type,
        @Nonnull PlayerData data,
        @Nonnull RPGConfig.AttributeConfig attrs
    ) {
        StringBuilder sb = new StringBuilder();

        String prefix = type.name().toLowerCase().substring(0, 3);
        int value = type.getValue(data);
        String color = type.getHexColor();
        String bonusText = getBonusText(type, attrs);

        // Row container - horizontal layout: color bar on left, content on right
        sb.append("          <div style=\"layout-mode: Left; anchor-height: ").append(ROW_HEIGHT).append("; anchor-horizontal: 0;\" data-hyui-style=\"Padding: (Horizontal: 16; Vertical: 6)\">\n");

        // Color bar - fixed height to stay consistent as row grows
        sb.append("            <div style=\"anchor-width: 4; anchor-top: 0; anchor-height: 39; background-color: ").append(color).append(";\"></div>\n");
        sb.append("            <div style=\"anchor-width: 10;\"></div>\n");

        // Right side: vertical stack with name row and description
        sb.append("            <div style=\"layout-mode: Top; flex-weight: 1;\">\n");

        // Top line: Name, Spacer, Buttons, Value (sized to fit 32px buttons)
        sb.append("              <div style=\"layout-mode: Left; anchor-height: 38; anchor-horizontal: 0;\">\n");

        // Attribute name - large and bold (32px font needs ~240px width)
        sb.append("                <div style=\"anchor-width: 200; layout-mode: Left;\"><p style=\"font-size: 26; color: ").append(color).append(";\">").append(type.getDisplayName()).append("</p></div>\n");

        // Spacer
        sb.append("                <div style=\"flex-weight: 1;\"></div>\n");

        // Minus button (vertically centered)
        sb.append("                <div style=\"layout-mode: MiddleCenter; anchor-vertical: 0; anchor-width: ").append(BUTTON_SIZE + 4).append(";\"><button id=\"").append(prefix).append("-sub\" class=\"secondary-button\" style=\"anchor-width: ").append(BUTTON_SIZE + 4).append("; anchor-height: ").append(BUTTON_SIZE).append(";\">-</button></div>\n");
        sb.append("                <div style=\"anchor-width: 6;\"></div>\n");

        // Value display (vertically centered)
        sb.append("                <div style=\"layout-mode: MiddleCenter; anchor-vertical: 0; anchor-width: 59;\">");
        sb.append("<p id=\"").append(prefix).append("-value\" style=\"font-size: 18; color: ").append(RPGStyles.STAT_VALUE).append(";\">").append(value).append("</p>");
        sb.append("</div>\n");

        // Delta display (collapsed by default, expands to 45px when pending > 0)
        sb.append("                <div id=\"").append(prefix).append("-delta-wrap\" style=\"anchor-width: 0; layout-mode: Left;\"><p id=\"").append(prefix).append("-delta\" style=\"font-size: 14; color: ").append(RPGStyles.POSITIVE).append("; display: none;\"></p></div>\n");

        // Plus buttons (vertically centered)
        sb.append("                <div style=\"layout-mode: MiddleCenter; anchor-vertical: 0; anchor-width: ").append(BUTTON_SIZE + 4).append(";\"><button id=\"").append(prefix).append("-add1\" class=\"secondary-button\" style=\"anchor-width: ").append(BUTTON_SIZE + 4).append("; anchor-height: ").append(BUTTON_SIZE).append(";\">+</button></div>\n");
        sb.append("                <div style=\"anchor-width: 6;\"></div>\n");
        // +5 button - using small-secondary-button for less padding (60px wide)
        sb.append("                <div style=\"layout-mode: MiddleCenter; anchor-vertical: 0; anchor-width: 60;\"><button id=\"").append(prefix).append("-add5\" class=\"small-secondary-button\" style=\"anchor-width: 60; anchor-height: 32;\">+5</button></div>\n");

        sb.append("              </div>\n");

        // Bottom line: Bonus description (to the right of the color bar)
        sb.append("              <div style=\"layout-mode: Left; anchor-height: 10; anchor-horizontal: 0;\">\n");
        sb.append("                <p style=\"font-size: 11; color: ").append(RPGStyles.TEXT_MUTED).append(";\">").append(bonusText).append("</p>\n");
        sb.append("              </div>\n");

        sb.append("            </div>\n");  // End right side column

        sb.append("          </div>\n");

        // Gap between rows
        sb.append("          <div style=\"anchor-height: ").append(ROW_GAP).append(";\"></div>\n");

        return sb.toString();
    }

    /**
     * Builds the pending changes row with Save/Cancel buttons.
     */
    @Nonnull
    private String buildPendingRow() {
        StringBuilder sb = new StringBuilder();

        // Initially hidden (no pending changes)
        sb.append("          <div id=\"pending-row\" style=\"layout-mode: Left; anchor-height: 42; anchor-horizontal: 0; display: none;\" data-hyui-style=\"Padding: (Horizontal: 20; Vertical: 6)\">\n");

        // Pending label
        sb.append("            <div style=\"flex-weight: 1; layout-mode: Left;\"><p style=\"font-size: 13; color: ").append(RPGStyles.TITLE_GOLD).append(";\">Unsaved changes</p></div>\n");

        // Save button
        sb.append("            <button id=\"save-btn\" class=\"secondary-button\" style=\"anchor-width: 100; anchor-height: 36;\">Save</button>\n");
        sb.append("            <div style=\"anchor-width: 10;\"></div>\n");

        // Cancel button
        sb.append("            <button id=\"cancel-btn\" class=\"secondary-button\" style=\"anchor-width: 120; anchor-height: 36;\">Cancel</button>\n");

        sb.append("          </div>\n");

        return sb.toString();
    }

    /**
     * Builds the navigation footer with Close, Stats, and Skill Tree buttons.
     */
    @Nonnull
    private String buildNavigationFooter() {
        StringBuilder sb = new StringBuilder();

        sb.append("      <div style=\"layout-mode: Center; anchor-height: 70; anchor-top: 10; anchor-horizontal: 0;\">\n");
        sb.append("        <div style=\"layout-mode: Left;\">\n");
        sb.append("          <button id=\"close-btn\" class=\"secondary-button\" style=\"anchor-width: ").append(NAV_BUTTON_WIDTH).append("; anchor-height: ").append(NAV_BUTTON_HEIGHT).append(";\">Close</button>\n");
        sb.append("          <div style=\"anchor-width: 20;\"></div>\n");
        sb.append("          <button id=\"stats-btn\" class=\"secondary-button\" style=\"anchor-width: ").append(NAV_BUTTON_WIDTH).append("; anchor-height: ").append(NAV_BUTTON_HEIGHT).append(";\">Stats</button>\n");
        sb.append("          <div style=\"anchor-width: 20;\"></div>\n");
        sb.append("          <button id=\"skilltree-btn\" class=\"secondary-button\" style=\"anchor-width: ").append(NAV_BUTTON_WIDTH).append("; anchor-height: ").append(NAV_BUTTON_HEIGHT).append(";\">Skill Tree</button>\n");
        sb.append("        </div>\n");
        sb.append("      </div>\n");

        return sb.toString();
    }

    /**
     * Gets the bonus description text for an elemental attribute type.
     */
    @Nonnull
    private String getBonusText(@Nonnull AttributeType type, @Nonnull RPGConfig.AttributeConfig attrs) {
        return switch (type) {
            case FIRE -> {
                var g = attrs.getFireGrants();
                yield String.format("+%.1f%% Phys | +%.1f%% Charged Atk | +%.1f%% Crit Mult | +%.1f%% Burn | +%.1f%% Ignite",
                    g.getPhysicalDamagePercent(), g.getChargedAttackDamagePercent(), g.getCriticalMultiplier(),
                    g.getBurnDamagePercent(), g.getIgniteChance());
            }
            case WATER -> {
                var g = attrs.getWaterGrants();
                yield String.format("+%.1f%% Spell | +%.1f Mana | +%.0f Barrier | +%.2f Mana/s | +%.1f%% Freeze",
                    g.getSpellDamagePercent(), g.getMaxMana(), g.getEnergyShield(),
                    g.getManaRegen(), g.getFreezeChance());
            }
            case LIGHTNING -> {
                var g = attrs.getLightningGrants();
                yield String.format("+%.1f%% Atk Speed | +%.2f%% Move | +%.1f%% Crit | +%.1f Stam/s | +%.1f%% Shock",
                    g.getAttackSpeedPercent(), g.getMoveSpeedPercent(), g.getCritChance(),
                    g.getStaminaRegen(), g.getShockChance());
            }
            case EARTH -> {
                var g = attrs.getEarthGrants();
                yield String.format("+%.1f%% Max HP | +%.0f Armor | +%.1f HP/s | +%.1f%% Block | +%.1f%% KB Resist",
                    g.getMaxHealthPercent(), g.getArmor(), g.getHealthRegen(),
                    g.getBlockChance(), g.getKnockbackResistance());
            }
            case WIND -> {
                var g = attrs.getWindGrants();
                yield String.format("+%.0f Eva | +%.0f Acc | +%.1f%% Proj Dmg | +%.2f%% Jump | +%.1f%% Proj Speed",
                    g.getEvasion(), g.getAccuracy(), g.getProjectileDamagePercent(),
                    g.getJumpForcePercent(), g.getProjectileSpeedPercent());
            }
            case VOID -> {
                var g = attrs.getVoidGrants();
                yield String.format("+%.1f%% Life Steal | +%.2f%% True Dmg | +%.1f%% DoT | +%.1f Mana/Kill | +%.1f%% Duration",
                    g.getLifeSteal(), g.getPercentHitAsTrueDamage(), g.getDotDamagePercent(),
                    g.getManaOnKill(), g.getStatusEffectDuration());
            }
        };
    }
}
