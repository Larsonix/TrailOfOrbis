package io.github.larsonix.trailoforbis.stones.ui;

import au.ellie.hyui.builders.HyUIPage;
import au.ellie.hyui.builders.ItemGridBuilder;
import au.ellie.hyui.builders.LabelBuilder;
import au.ellie.hyui.builders.PageBuilder;
import au.ellie.hyui.events.SlotClickingEventData;
import au.ellie.hyui.events.SlotDoubleClickingEventData;
import au.ellie.hyui.events.UIContext;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.ui.ItemGridSlot;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.gear.GearManager;
import io.github.larsonix.trailoforbis.gear.item.ItemDisplayNameService;
import io.github.larsonix.trailoforbis.gear.item.TranslationSyncService;
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.tooltip.RichTooltipFormatter;
import io.github.larsonix.trailoforbis.gear.tooltip.TooltipStyles;
import io.github.larsonix.trailoforbis.maps.core.RealmMapData;
import io.github.larsonix.trailoforbis.maps.tooltip.RealmMapTooltipBuilder;
import io.github.larsonix.trailoforbis.stones.ModifiableItem;
import io.github.larsonix.trailoforbis.stones.ModifiableItemIO;
import io.github.larsonix.trailoforbis.stones.StoneType;
import io.github.larsonix.trailoforbis.ui.RPGStyles;
import io.github.larsonix.trailoforbis.util.MessageSerializer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HyUI-based stone picker page for selecting target items when using stones.
 *
 * <p>Uses a native Hytale {@code item-grid} with activatable slots to display
 * compatible items. Each slot shows the item's 3D model, quality-colored
 * background, and hover tooltip with name/description — all native Hytale
 * rendering. Slots are clickable without drag behavior via
 * {@link ItemGridSlot#setActivatable(boolean)}.
 *
 * <p>Features:
 * <ul>
 *   <li>Displays all compatible items from player's inventory as native item slots</li>
 *   <li>Quality-colored slot backgrounds (rarity tinting)</li>
 *   <li>Hover tooltips with item name and location info</li>
 *   <li>Single-click selection with preview panel</li>
 *   <li>Apply button to execute stone action</li>
 * </ul>
 *
 * @see StoneApplicationService
 */
public class StonePickerPage {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final int MAX_ITEMS = 50;

    // Grid layout constants
    private static final int MAX_COLUMNS = 4;
    private static final int MAX_VISIBLE_ROWS = 4;
    private static final int SLOT_SIZE = 100;  // ~33% smaller than 148
    private static final int SLOT_SPACING = 2;
    private static final int CELL_SIZE = SLOT_SIZE + SLOT_SPACING; // 102
    private static final int SLOT_ICON_SIZE = 85; // ~33% larger than default 64

    // Container sizing — base width for 4 columns, scrollbar added dynamically
    private static final int CONTAINER_CHROME = 10; // Decorated-container borders + internal padding
    private static final int CONTAINER_CHROME_HEIGHT = 60; // Title bar + border chrome
    private static final int GRID_LEFT_PADDING = 10;   // Left padding between container edge and grid
    private static final int SCROLLBAR_ALLOWANCE = 14; // Native scrollbar: Size 6 + Spacing 6 + 2px buffer
    private static final int CONTAINER_BASE_WIDTH = MAX_COLUMNS * CELL_SIZE
        + GRID_LEFT_PADDING + CONTAINER_CHROME; // 428 (no scrollbar)

    // Section heights for dynamic height calculation
    private static final int HEADER_HEIGHT = 58;     // Gold title + description stacked vertically
    private static final int DIVIDER_HEIGHT = 9;     // 1px line + 8px spacer (matches StatsPage/AttributePage)
    private static final int NO_ITEMS_HEIGHT = 60;   // Empty state message
    private static final int BUTTON_ROW_HEIGHT = 50; // Room for 36px buttons + vertical padding

    // Selection panel — fixed 3-line summary (name, info, stone effect)
    private static final int SELECTION_PANEL_HEIGHT = 80; // 3 lines × 20 + 20 padding


    // ═══════════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════════

    private final TrailOfOrbis plugin;
    private final PlayerRef player;
    private final StoneType stoneType;
    private final ItemStack stoneItem;
    private final short stoneSlot;
    private final ContainerType stoneContainer;
    private final StoneApplicationService applicationService;
    private final CompatibleItemScanner scanner;

    private List<CompatibleItemScanner.ScannedItem> compatibleItems = new ArrayList<>();
    private int selectedIndex = -1;

    // Tracks which instance IDs had their translations modified (for restore on close)
    private final List<String> modifiedInstanceIds = new ArrayList<>();

    // When true, onDismiss skips tooltip restoration (the apply path resyncs its own tooltips)
    private boolean applyInProgress = false;

    // ═══════════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Creates a new stone picker page.
     *
     * @param plugin The main plugin instance
     * @param player The player opening the page
     * @param stoneType The type of stone being used
     * @param stoneItem The stone item stack
     * @param stoneSlot The slot containing the stone
     * @param stoneContainer Which container the stone is in
     * @param applicationService Service for applying stones
     */
    public StonePickerPage(
            @Nonnull TrailOfOrbis plugin,
            @Nonnull PlayerRef player,
            @Nonnull StoneType stoneType,
            @Nonnull ItemStack stoneItem,
            short stoneSlot,
            @Nonnull ContainerType stoneContainer,
            @Nonnull StoneApplicationService applicationService) {

        this.plugin = plugin;
        this.player = player;
        this.stoneType = stoneType;
        this.stoneItem = stoneItem;
        this.stoneSlot = stoneSlot;
        this.stoneContainer = stoneContainer;
        this.applicationService = applicationService;

        GearManager gearManager = plugin.getGearManager();
        ItemDisplayNameService displayNameService = gearManager.getItemDisplayNameService();
        this.scanner = new CompatibleItemScanner(displayNameService);
    }

    // ═══════════════════════════════════════════════════════════════════
    // PAGE OPEN
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Opens the stone picker page for the player.
     *
     * <p>Scans the player's inventory for compatible items, builds the
     * HYUIML HTML with an empty item-grid, programmatically populates
     * slots, registers event listeners, and opens the page.
     *
     * @param store The entity store
     */
    public void open(@Nonnull Store<EntityStore> store) {
        try {
            Ref<EntityStore> ref = player.getReference();
            if (ref == null || !ref.isValid()) {
                LOGGER.atWarning().log("Cannot open stone picker - player ref invalid");
                return;
            }

            Player playerEntity = store.getComponent(ref, Player.getComponentType());
            if (playerEntity == null) {
                LOGGER.atWarning().log("Cannot open stone picker - Player entity not found");
                return;
            }

            Inventory inventory = playerEntity.getInventory();
            if (inventory == null) {
                LOGGER.atWarning().log("Cannot open stone picker - Inventory is null");
                return;
            }

            // Scan for compatible items
            compatibleItems = scanner.scan(inventory, stoneType, applicationService.getActionRegistry());

            // Update translations to include location info before building the page.
            // Packets are ordered — translations arrive before the page renders on client.
            updateTranslationsWithLocation();

            // Build HTML and open page
            String html = buildHtml();

            PageBuilder builder = PageBuilder.pageForPlayer(player)
                .withLifetime(CustomPageLifetime.CanDismiss)
                .fromHtml(html);

            populateGrid(builder);
            registerEvents(builder, store);

            builder.open(store);
            plugin.getUIManager().trackOpenPage(player.getUuid(), "stone_picker");

            LOGGER.atFine().log("Opened stone picker for %s using %s (%d compatible items)",
                player.getUsername(), stoneType.getDisplayName(), compatibleItems.size());

        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to open stone picker for %s", player.getUsername());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // HTML GENERATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Builds the full HYUIML for the stone picker page.
     *
     * <p>The item-grid is declared empty — slots are added programmatically
     * via {@link #populateGrid(PageBuilder)} after HTML parsing.
     */
    private String buildHtml() {
        int count = Math.min(compatibleItems.size(), MAX_ITEMS);
        boolean hasItems = !compatibleItems.isEmpty();

        // Grid dimensions — always 4 columns, width grows when scrollbar is needed
        int rows = count > 0 ? (int) Math.ceil((double) count / MAX_COLUMNS) : 0;
        int visibleRows = Math.min(rows, MAX_VISIBLE_ROWS);
        int gridHeight = visibleRows * CELL_SIZE;
        boolean showScrollbar = rows > MAX_VISIBLE_ROWS;

        int containerWidth = showScrollbar
            ? CONTAINER_BASE_WIDTH + SCROLLBAR_ALLOWANCE
            : CONTAINER_BASE_WIDTH;
        int containerHeight = calculateContainerHeight(gridHeight, hasItems);

        StringBuilder html = new StringBuilder(2048);

        html.append("""
            <div class="page-overlay">
                <div class="decorated-container" data-hyui-title="%s Stone"
                     style="anchor-width: %d; anchor-height: %d;">
                    <div class="container-contents" style="layout-mode: Top;">
            """.formatted(escapeHtml(stoneType.getDisplayName()), containerWidth, containerHeight));

        // Header section
        buildHeader(html);

        // Divider
        appendDivider(html);

        // Item grid or no-items message
        if (!hasItems) {
            buildNoItemsMessage(html);
        } else {
            buildItemGridContainer(html, rows, gridHeight, showScrollbar);
        }

        // Divider
        appendDivider(html);

        // Selection panel — always present when items exist (updated in-place on click)
        if (hasItems) {
            buildSelectionPanel(html);
            appendDivider(html);
        }

        // Button row (Apply button always present when items exist)
        buildButtonRow(html, hasItems);

        html.append("""
                    </div>
                </div>
            </div>
            """);

        return html.toString();
    }

    /**
     * Calculates the total container height based on content sections.
     * The selection panel is always included when items exist (pre-built for in-place updates).
     */
    private int calculateContainerHeight(int gridHeight, boolean hasItems) {
        int height = CONTAINER_CHROME_HEIGHT;
        height += HEADER_HEIGHT;
        height += DIVIDER_HEIGHT;
        height += hasItems ? gridHeight : NO_ITEMS_HEIGHT;
        height += DIVIDER_HEIGHT;
        if (hasItems) {
            height += SELECTION_PANEL_HEIGHT;
            height += DIVIDER_HEIGHT;
        }
        height += BUTTON_ROW_HEIGHT;
        return height;
    }

    /**
     * Builds the header section with stone name and description.
     */
    private void buildHeader(StringBuilder html) {
        html.append("""
                        <div style="layout-mode: Top; anchor-height: 58; anchor-horizontal: 0; margin-top: -4;"
                             data-hyui-style="Padding: (Horizontal: 20; Top: 6)">
                            <div><p style="font-size: 22; color: %s;">Choose an item</p></div>
                            <div><p style="font-size: 14; color: %s;">%s</p></div>
                        </div>
            """.formatted(
                RPGStyles.TITLE_GOLD,
                RPGStyles.TEXT_SECONDARY,
                escapeHtml(stoneType.getDescription())
            ));
    }

    /**
     * Builds the no-items message when no compatible items are found.
     */
    private void buildNoItemsMessage(StringBuilder html) {
        html.append("""
                        <div style="layout-mode: Center; anchor-horizontal: 0; anchor-height: 60;">
                            <p style="color: %s; font-size: 12;">No compatible items in inventory</p>
                        </div>
            """.formatted(RPGStyles.TEXT_MUTED));
    }

    /**
     * Builds the item-grid container with edge padding and optional native scrollbar.
     *
     * <p>When scrolling is needed, the grid is wrapped in a {@code TopScrolling}
     * div with {@code data-hyui-scrollbar-style} — this produces the native Hytale
     * scrollbar (same as the Stats page). The grid's own internal scrollbar is
     * always disabled. The grid's {@code anchor-height} is set to its full
     * (unclipped) height so all rows render; the wrapper clips to visible rows.
     *
     * <p>Edge padding (10px left + right) is applied via a padding wrapper div.
     *
     * @param rows Total number of grid rows (for computing full grid height)
     */
    private void buildItemGridContainer(StringBuilder html,
                                         int rows, int gridHeight,
                                         boolean showScrollbar) {
        int fullGridHeight = rows * CELL_SIZE;

        // Outer padding wrapper: left padding only, scrollbar acts as right margin
        html.append("""
                        <div style="anchor-horizontal: 0; padding-left: %d;">
            """.formatted(GRID_LEFT_PADDING));

        if (showScrollbar) {
            // TopScrolling wrapper clips to visible height and adds native scrollbar
            html.append("""
                            <div style="layout-mode: TopScrolling; anchor-height: %d; anchor-horizontal: 0;"
                                 data-hyui-scrollbar-style="Common.ui DefaultScrollbarStyle">
                """.formatted(gridHeight));
        } else {
            html.append("""
                            <div style="layout-mode: Top; anchor-height: %d; anchor-horizontal: 0;">
                """.formatted(gridHeight));
        }

        // The item-grid itself — stretches to fill parent, full height so all rows render
        html.append("""
                                <div id="item-grid" class="item-grid"
                                     data-hyui-slots-per-row="%d"
                                     data-hyui-are-items-draggable="false"
                                     data-hyui-display-item-quantity="false"
                                     data-hyui-render-item-quality-background="true"
                                     data-hyui-show-scrollbar="false"
                                     data-hyui-keep-scroll-position="true"
                                     data-hyui-style="SlotSize: %d; SlotIconSize: %d"
                                     style="anchor-horizontal: 0; anchor-height: %d;">
                                </div>
            """.formatted(
                MAX_COLUMNS,
                SLOT_SIZE,
                SLOT_ICON_SIZE,
                fullGridHeight
            ));

        // Close TopScrolling/Top wrapper + padding wrapper
        html.append("""
                            </div>
                        </div>
            """);
    }

    /**
     * Builds the selection panel with 3 pre-built labels.
     *
     * <p>Labels are created with placeholder text and updated in-place via
     * {@code ctx.getById()} + {@code LabelBuilder.withText()} when the player
     * clicks an item. This avoids rebuilding the page (which resets scroll).
     */
    private void buildSelectionPanel(StringBuilder html) {
        html.append("""
                        <div style="layout-mode: Top; anchor-horizontal: 0; anchor-height: %d;"
                             data-hyui-style="Padding: (Horizontal: 20; Vertical: 10)">
                            <div style="anchor-height: 20;"><p id="sel-name" style="font-size: 14; color: %s;">Select an item above</p></div>
                            <div style="anchor-height: 20;"><p id="sel-info" style="font-size: 12; color: %s;"> </p></div>
                            <div style="anchor-height: 20;"><p id="sel-effect" style="font-size: 12; color: %s;"> </p></div>
                        </div>
            """.formatted(SELECTION_PANEL_HEIGHT, RPGStyles.TEXT_SECONDARY, RPGStyles.TEXT_MUTED, RPGStyles.TITLE_GOLD));
    }

    /**
     * Builds the bottom button row with Cancel and optional Apply buttons.
     *
     * <p>When items exist, the Apply button is always present (event handler
     * checks if an item is actually selected before proceeding).
     */
    private void buildButtonRow(StringBuilder html, boolean hasItems) {
        html.append("""
                        <div style="layout-mode: Left; anchor-horizontal: 0; anchor-height: 50;"
                             data-hyui-style="Padding: (Horizontal: 20; Vertical: 7)">
            """);

        // Cancel on the left
        html.append("""
                            <button id="cancel-btn" class="secondary-button"
                                    style="anchor-width: 120; anchor-height: 36;">
                                Cancel
                            </button>
            """);

        if (hasItems) {
            // Apply button always present when items exist (handler checks selection)
            html.append("""
                            <div style="flex-weight: 1;"></div>
                            <button id="apply-btn" class="secondary-button"
                                    style="anchor-width: 120; anchor-height: 36;">
                                Apply
                            </button>
                """);
        }

        html.append("""
                        </div>
            """);
    }

    /**
     * Appends a horizontal divider line.
     */
    private void appendDivider(StringBuilder html) {
        html.append("""
                        <div style="anchor-height: 1; anchor-horizontal: 0; background-color: #333344;"></div>
                        <div style="anchor-height: 8;"></div>
            """);
    }

    // ═══════════════════════════════════════════════════════════════════
    // GRID POPULATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Programmatically populates the item-grid with activatable slots.
     *
     * <p>Each slot uses the item's actual ItemStack ID (custom item definition)
     * so the native Hytale tooltip renders — with full rich formatting from
     * the UpdateTranslations/UpdateItems system. Location info is appended
     * to the tooltip via {@link #updateTranslationsWithLocation()}.
     */
    private void populateGrid(@Nonnull PageBuilder builder) {
        if (compatibleItems.isEmpty()) {
            return;
        }

        builder.getById("item-grid", ItemGridBuilder.class).ifPresent(grid -> {
            int count = Math.min(compatibleItems.size(), MAX_ITEMS);
            for (int i = 0; i < count; i++) {
                CompatibleItemScanner.ScannedItem item = compatibleItems.get(i);

                ItemGridSlot slot = new ItemGridSlot(
                    new ItemStack(item.itemStack().getItemId(), 1)
                );
                slot.setActivatable(true);

                // Dim non-selected slots to highlight the chosen item
                if (selectedIndex >= 0 && i != selectedIndex) {
                    slot.setItemIncompatible(true);
                }

                grid.addSlot(slot);
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    // TOOLTIP TRANSLATION MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Updates each item's tooltip translation to include its container location.
     *
     * <p>For each compatible item, rebuilds the rich tooltip description and
     * appends the container name (e.g., "Hotbar", "Armor") at the bottom.
     * This preserves the full native tooltip with colors, modifiers, etc.
     * while adding the location context the player needs.
     *
     * <p>Sends {@code UpdateTranslations} packets before the page opens.
     * Packet ordering guarantees the client has updated text before rendering.
     */
    private void updateTranslationsWithLocation() {
        if (compatibleItems.isEmpty()) {
            return;
        }

        GearManager gearManager = plugin.getGearManager();
        if (gearManager == null) {
            return;
        }

        TranslationSyncService translationService =
            gearManager.getItemSyncService().getTranslationService();
        RichTooltipFormatter tooltipFormatter = gearManager.getRichTooltipFormatter();
        ItemDisplayNameService displayNameService = gearManager.getItemDisplayNameService();

        // Collect all translations into a single batch to avoid packet flooding.
        // Sending N individual UpdateTranslations packets in one tick crashes the client
        // (NullReferenceException in the UI layout tree when translations update mid-render).
        Map<String, TranslationSyncService.TranslationEntry> batch = new LinkedHashMap<>();

        int count = Math.min(compatibleItems.size(), MAX_ITEMS);
        for (int i = 0; i < count; i++) {
            CompatibleItemScanner.ScannedItem item = compatibleItems.get(i);
            String compactId = getCompactInstanceId(item);
            if (compactId == null) {
                continue;
            }

            // Build the full rich tooltip for this item type
            Message tooltip = buildItemTooltip(item, tooltipFormatter);
            if (tooltip == null) {
                continue;
            }

            // Append location line at the bottom
            String locationText = item.container().getDisplayName();
            Message locationLine = Message.raw("\n\n" + locationText)
                .color(TooltipStyles.LABEL_GRAY)
                .italic(true);
            Message combined = tooltip.insert(locationLine);

            // Build name text using the same service as the sync pipeline
            String nameText = buildItemNameText(item, displayNameService);
            String descText = MessageSerializer.toFormattedText(combined);

            // Unregister tracking so the batch method won't skip this entry
            translationService.unregisterTranslation(player.getUuid(), compactId);
            batch.put(compactId, new TranslationSyncService.TranslationEntry(nameText, descText));
            modifiedInstanceIds.add(compactId);
        }

        // Send all translations in ONE packet
        if (!batch.isEmpty()) {
            translationService.registerTranslationsBatch(player, batch);
        }

        LOGGER.atFine().log("Updated %d item tooltips with location info for stone picker (1 packet)",
            modifiedInstanceIds.size());
    }

    /**
     * Restores original tooltips by re-sending unmodified translations for each item.
     *
     * <p>Called on page close (cancel, apply, dismiss) to remove the appended
     * location text from item tooltips. Only resyncs the specific items that
     * were modified, avoiding a full inventory resync.
     */
    private void restoreOriginalTooltips() {
        if (modifiedInstanceIds.isEmpty()) {
            return;
        }

        try {
            GearManager gearManager = plugin.getGearManager();
            if (gearManager == null) {
                return;
            }

            TranslationSyncService translationService =
                gearManager.getItemSyncService().getTranslationService();
            RichTooltipFormatter tooltipFormatter = gearManager.getRichTooltipFormatter();
            ItemDisplayNameService displayNameService = gearManager.getItemDisplayNameService();

            // Batch all restorations into a single packet (same fix as updateTranslationsWithLocation)
            Map<String, TranslationSyncService.TranslationEntry> batch = new LinkedHashMap<>();

            int count = Math.min(compatibleItems.size(), MAX_ITEMS);
            for (int i = 0; i < count; i++) {
                CompatibleItemScanner.ScannedItem item = compatibleItems.get(i);
                String compactId = getCompactInstanceId(item);
                if (compactId == null || !modifiedInstanceIds.contains(compactId)) {
                    continue;
                }

                // Rebuild the original tooltip (without location)
                Message tooltip = buildItemTooltip(item, tooltipFormatter);
                if (tooltip == null) {
                    continue;
                }

                String nameText = buildItemNameText(item, displayNameService);
                String descText = MessageSerializer.toFormattedText(tooltip);

                translationService.unregisterTranslation(player.getUuid(), compactId);
                batch.put(compactId, new TranslationSyncService.TranslationEntry(nameText, descText));
            }

            if (!batch.isEmpty()) {
                translationService.registerTranslationsBatch(player, batch);
            }

            LOGGER.atFine().log("Restored %d/%d item tooltips after stone picker close (1 packet)",
                batch.size(), modifiedInstanceIds.size());
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to restore tooltips after stone picker close");
        } finally {
            modifiedInstanceIds.clear();
        }
    }

    /**
     * Gets the compact instance ID for a scanned item's translation keys.
     *
     * @return Compact ID (e.g., "1706123456789_42" for gear, "map_170612_42" for maps), or null
     */
    @Nullable
    private String getCompactInstanceId(@Nonnull CompatibleItemScanner.ScannedItem item) {
        ModifiableItem data = item.data();
        if (data instanceof GearData gearData && gearData.hasInstanceId()) {
            return gearData.instanceId().toCompactString();
        } else if (data instanceof RealmMapData mapData && mapData.instanceId() != null) {
            return mapData.instanceId().toCompactString();
        }
        return null;
    }

    /**
     * Builds the rich tooltip Message for an item (gear or realm map).
     */
    @Nullable
    private Message buildItemTooltip(
            @Nonnull CompatibleItemScanner.ScannedItem item,
            @Nonnull RichTooltipFormatter tooltipFormatter) {
        ModifiableItem data = item.data();
        if (data instanceof GearData gearData) {
            return tooltipFormatter.build(gearData, player.getUuid());
        } else if (data instanceof RealmMapData mapData) {
            return new RealmMapTooltipBuilder().build(mapData);
        }
        return null;
    }

    /**
     * Builds the display name text for an item (same format as the sync pipeline).
     */
    @Nonnull
    private String buildItemNameText(
            @Nonnull CompatibleItemScanner.ScannedItem item,
            @Nonnull ItemDisplayNameService displayNameService) {
        ModifiableItem data = item.data();
        if (data instanceof GearData gearData) {
            return displayNameService.getGearDisplayName(gearData, item.itemStack());
        } else if (data instanceof RealmMapData mapData) {
            return displayNameService.getMapDisplayName(mapData);
        }
        return item.displayName();
    }

    // ═══════════════════════════════════════════════════════════════════
    // TOOLTIP CLEANUP
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Clears all item-grid slots to dismiss any native Hytale hover tooltip.
     *
     * <p>The native item-grid tooltip is a separate UI element that persists
     * when the parent page closes while the tooltip is visible. Replacing all
     * slots with empty {@link ItemGridSlot}s (no item = no tooltip) before
     * closing ensures the tooltip is dismissed.
     *
     * <p>Must be called before {@code page.close()} and followed by
     * {@code ctx.updatePage(false)} so the client processes the slot clear
     * before the page is removed.
     *
     * @param ctx The UI context (from an event handler or the HyUIPage itself)
     */
    private void clearGridTooltips(@Nonnull UIContext ctx) {
        if (compatibleItems.isEmpty()) {
            return;
        }
        int count = Math.min(compatibleItems.size(), MAX_ITEMS);
        ctx.getById("item-grid", ItemGridBuilder.class).ifPresent(grid -> {
            for (int i = 0; i < count; i++) {
                grid.updateSlot(new ItemGridSlot(), i);
            }
        });
        ctx.updatePage(false);
    }

    // ═══════════════════════════════════════════════════════════════════
    // EVENT REGISTRATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Registers all event listeners on the PageBuilder.
     *
     * <p>The slot click handler uses in-place updates ({@code ctx.updatePage(false)})
     * to preserve scroll position when the player selects an item. Grid slots are
     * dimmed via {@code updateSlot()}, and selection panel labels are updated via
     * {@code LabelBuilder.withText()}.
     */
    private void registerEvents(@Nonnull PageBuilder builder, @Nonnull Store<EntityStore> store) {
        int count = Math.min(compatibleItems.size(), MAX_ITEMS);

        // Slot click on the item grid — in-place update preserves scroll
        if (!compatibleItems.isEmpty()) {
            builder.addEventListener("item-grid", CustomUIEventBindingType.SlotClicking,
                SlotClickingEventData.class, (slot, ctx) -> {
                    Integer index = slot.getSlotIndex();
                    if (index == null || index < 0 || index >= compatibleItems.size()) return;

                    selectedIndex = index;
                    CompatibleItemScanner.ScannedItem selected = compatibleItems.get(index);

                    // Update grid slots — dim non-selected items
                    ctx.getById("item-grid", ItemGridBuilder.class).ifPresent(grid -> {
                        for (int i = 0; i < count; i++) {
                            CompatibleItemScanner.ScannedItem item = compatibleItems.get(i);
                            ItemGridSlot newSlot = new ItemGridSlot(
                                new ItemStack(item.itemStack().getItemId(), 1));
                            newSlot.setActivatable(true);
                            if (i != selectedIndex) {
                                newSlot.setItemIncompatible(true);
                            }
                            grid.updateSlot(newSlot, i);
                        }
                    });

                    // Update selection panel labels
                    String rarity = selected.data().rarity().getHytaleQualityId();
                    String nameText = "[" + rarity.toUpperCase() + "] " + selected.displayName();
                    String infoText = "Level " + selected.data().level()
                        + " | Quality " + selected.data().quality() + "%"
                        + " | " + selected.container().getDisplayName();
                    String effectText = generatePreviewText(selected);

                    ctx.getById("sel-name", LabelBuilder.class)
                        .ifPresent(l -> l.withText(nameText));
                    ctx.getById("sel-info", LabelBuilder.class)
                        .ifPresent(l -> l.withText(infoText));
                    ctx.getById("sel-effect", LabelBuilder.class)
                        .ifPresent(l -> l.withText(effectText));

                    ctx.updatePage(false);
                });
        }

        // Slot double-click on the item grid — select + apply in one action
        if (!compatibleItems.isEmpty()) {
            builder.addEventListener("item-grid", CustomUIEventBindingType.SlotDoubleClicking,
                SlotDoubleClickingEventData.class, (slot, ctx) -> {
                    Integer index = slot.getSlotIndex();
                    if (index == null || index < 0 || index >= compatibleItems.size()) return;

                    selectedIndex = index;
                    applyInProgress = true;

                    // Clear all slots to dismiss the native hover tooltip before
                    // the page closes (empty slots have no tooltip to persist).
                    clearGridTooltips(ctx);

                    ctx.getPage().ifPresent(page -> page.close());
                    handleApply();
                });
        }

        // Cancel button — clear grid tooltips before closing
        builder.addEventListener("cancel-btn", CustomUIEventBindingType.Activating,
            (data, ctx) -> {
                clearGridTooltips(ctx);
                ctx.getPage().ifPresent(page -> page.close());
            });

        // Apply button — always registered when items exist, checks selection at click time
        if (!compatibleItems.isEmpty()) {
            builder.addEventListener("apply-btn", CustomUIEventBindingType.Activating,
                (data, ctx) -> {
                    if (selectedIndex < 0 || selectedIndex >= compatibleItems.size()) {
                        sendErrorMessage("Select an item first");
                        return;
                    }
                    applyInProgress = true;
                    clearGridTooltips(ctx);
                    ctx.getPage().ifPresent(page -> page.close());
                    handleApply();
                });
        }

        // onDismiss fires on ALL close paths (cancel, apply, ESC, click-outside).
        // For Escape/click-outside (forced=false, not apply): clear grid slots to
        // dismiss the native hover tooltip. Cancel/Apply/double-click already clear
        // slots before page.close(), so this is a no-op for those paths.
        // Skip tooltip restoration on apply — resyncModifiedItem handles the updated tooltip.
        builder.onDismiss((page, forced) -> {
            if (!forced && !applyInProgress) {
                // Escape or click-outside: clear grid slots to dismiss any
                // lingering native item tooltip. The page object implements
                // UIContext so we can update it even during the dismiss callback.
                clearGridTooltips(page);
            }

            if (!applyInProgress) {
                restoreOriginalTooltips();
            } else {
                modifiedInstanceIds.clear();
            }
            plugin.getUIManager().closePage(player.getUuid());
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    // APPLY HANDLING
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Handles Apply button click.
     */
    private void handleApply() {
        if (selectedIndex < 0 || selectedIndex >= compatibleItems.size()) {
            LOGGER.atWarning().log("Cannot apply - no valid selection");
            return;
        }

        CompatibleItemScanner.ScannedItem selectedItem = compatibleItems.get(selectedIndex);

        applyStoneToItem(selectedItem);
    }

    /**
     * Applies the stone to the selected item.
     */
    private void applyStoneToItem(CompatibleItemScanner.ScannedItem selectedItem) {
        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) {
            sendErrorMessage("Failed to apply stone !");
            return;
        }

        Store<EntityStore> freshStore = ref.getStore();
        Player playerEntity = freshStore.getComponent(ref, Player.getComponentType());
        if (playerEntity == null) {
            LOGGER.atWarning().log("Cannot apply stone - Player entity not found");
            sendErrorMessage("Failed to apply stone !");
            return;
        }

        Inventory inventory = playerEntity.getInventory();
        if (inventory == null) {
            LOGGER.atWarning().log("Cannot apply stone - Inventory is null");
            sendErrorMessage("Failed to apply stone !");
            return;
        }

        StoneApplicationService.ApplicationResult result = applicationService.apply(
            inventory,
            stoneType,
            stoneSlot,
            stoneContainer,
            selectedItem.itemStack(),
            selectedItem.data(),
            selectedItem.slot(),
            selectedItem.container()
        );

        if (result.success()) {
            // Build rich diff message showing exactly what changed
            Message richMessage = StoneResultMessageBuilder.build(
                stoneType, selectedItem.data(), result.modifiedData(), result.message());
            player.sendMessage(richMessage);

            // Defer tooltip resync to next tick — if sent immediately, the client
            // receives UpdateTranslations while the item slot is still "hot" (just
            // hovered/double-clicked), causing the tooltip to flash on the game screen
            // after the page closes.
            ItemStack updatedItem = result.updatedTargetItem();
            ModifiableItem originalData = selectedItem.data();
            World world = playerEntity.getWorld();
            if (world != null) {
                world.execute(() -> resyncModifiedItem(updatedItem, originalData));
            } else {
                resyncModifiedItem(updatedItem, originalData);
            }
        } else {
            sendErrorMessage(result.message());
        }

        plugin.getUIManager().closePage(player.getUuid());
    }

    // ═══════════════════════════════════════════════════════════════════
    // PREVIEW TEXT
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Generates preview text describing the stone's effect on the selected item.
     */
    private String generatePreviewText(CompatibleItemScanner.ScannedItem item) {
        int modCount = item.data().modifiers().size();

        return switch (stoneType) {
            case GAIAS_CALIBRATION -> "Rerolls all " + modCount + " modifier values";
            case EMBER_OF_TUNING -> "Rerolls one random modifier value";
            case ALTERVERSE_SHARD -> "Rerolls " + item.data().unlockedModifierCount() + " unlocked modifiers";
            case ORBISIAN_BLESSING -> "Rerolls quality (currently " + item.data().quality() + "%)";
            case GAIAS_GIFT -> "Adds a new random modifier";
            case PURGING_EMBER -> "Removes " + item.data().unlockedModifierCount() + " unlocked modifiers";
            case EROSION_SHARD -> "Removes one random unlocked modifier";
            case WARDENS_SEAL -> "Locks a random unlocked modifier";
            case WARDENS_KEY -> "Unlocks a random locked modifier";
            case TRANSMUTATION_CRYSTAL -> "Swaps one modifier for a new one";
            case THRESHOLD_STONE -> "Rerolls level within Lv" + Math.max(1, item.data().level() - 3) +
                "-" + (item.data().level() + 3);
            case CROWN_OF_TRANSCENDENCE -> "Upgrades to Legendary and adds 1 modifier";
            case GAIAS_PERFECTION -> "Sets quality to perfect (101)";
            case VARYNS_TOUCH -> "Corrupts with unpredictable effects";
            case LOREKEEPERS_SCROLL -> "Reveals hidden modifiers";
            case GENESIS_STONE -> "Fills all remaining modifier slots";
            default -> stoneType.getDescription();
        };
    }

    // ═══════════════════════════════════════════════════════════════════
    // MESSAGES
    // ═══════════════════════════════════════════════════════════════════

    private void sendSuccessMessage(String message) {
        Message msg = Message.raw("[")
            .color(RPGStyles.TITLE_GOLD)
            .insert(Message.raw(stoneType.getDisplayName()).color(stoneType.getHexColor()))
            .insert(Message.raw("] ").color(RPGStyles.TITLE_GOLD))
            .insert(Message.raw(message).color(RPGStyles.POSITIVE));

        player.sendMessage(msg);
    }

    private void sendErrorMessage(String message) {
        Message msg = Message.raw("[Stones] ").color(RPGStyles.TITLE_GOLD)
            .insert(Message.raw(message).color(RPGStyles.NEGATIVE));

        player.sendMessage(msg);
    }

    // ═══════════════════════════════════════════════════════════════════
    // ITEM RESYNC
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Resyncs a modified item to the client after stone application.
     */
    private void resyncModifiedItem(
            @Nullable ItemStack updatedItem,
            @Nonnull ModifiableItem originalData) {

        var gearManager = plugin.getGearManager();
        if (gearManager == null) {
            return;
        }
        ModifiableItemIO.resync(player, updatedItem, originalData, gearManager);
    }

    // ═══════════════════════════════════════════════════════════════════
    // UTILITIES
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Escapes HTML special characters to prevent HYUIML injection.
     */
    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }
}
