package io.github.larsonix.trailoforbis.stones.ui;

import au.ellie.hyui.builders.PageBuilder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.gear.GearManager;
import io.github.larsonix.trailoforbis.gear.item.ItemDisplayNameService;
import io.github.larsonix.trailoforbis.stones.ItemModifier;
import io.github.larsonix.trailoforbis.stones.ModifiableItem;
import io.github.larsonix.trailoforbis.stones.ModifiableItemIO;
import io.github.larsonix.trailoforbis.stones.StoneType;
import io.github.larsonix.trailoforbis.ui.RPGStyles;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * HyUI-based modifier selector page for lock/unlock stones.
 *
 * <p>Used by WARDENS_SEAL (lock) and WARDENS_KEY (unlock) stones.
 * Shows a list of eligible modifiers and applies the stone when one is clicked.
 *
 * <p>Uses the transparent button overlay pattern for clickable modifier rows:
 * each row has text content (bottom layer) with a transparent
 * {@code secondary-button} overlay (top layer) for click events and hover feedback.
 *
 * @see StonePickerPage
 * @see StoneApplicationService
 */
public class ModifierSelectorPage {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final int MAX_MODIFIERS = 12;

    // Layout constants
    private static final int CONTAINER_WIDTH = 500;
    private static final int ROW_HEIGHT = 40;
    private static final int ROW_MARGIN = 3;
    private static final int LIST_HEIGHT = 250;

    // Colors
    private static final String BG_ROW = "#2a2a2a";
    private static final String BG_OVERLAY = "#ffffff02";
    private static final String COLOR_LOCK_ICON = "#ffaa00";
    private static final String COLOR_UNLOCK_ICON = "#888888";
    private static final String COLOR_TYPE_LABEL = "#888888";
    private static final String COLOR_VALUE_POSITIVE = "#aaffaa";
    private static final String COLOR_VALUE_NEGATIVE = "#ffaaaa";

    // ═══════════════════════════════════════════════════════════════════
    // ELIGIBLE MODIFIER RECORD
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Represents a modifier eligible for the stone action.
     *
     * @param modifier The actual modifier
     * @param originalIndex The index in the item's modifier list
     * @param typeLabel "PREFIX", "SUFFIX", or "MOD" for display
     */
    private record EligibleModifier(
        @Nonnull ItemModifier modifier,
        int originalIndex,
        @Nonnull String typeLabel
    ) {}

    // ═══════════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════════

    private final TrailOfOrbis plugin;
    private final PlayerRef player;
    private final StoneType stoneType;
    private final ItemStack stoneItem;
    private final short stoneSlot;
    private final ContainerType stoneContainer;
    private final ItemStack targetItem;
    private final ModifiableItem targetData;
    private final short targetSlot;
    private final ContainerType targetContainer;
    private final StoneApplicationService applicationService;
    private final ItemDisplayNameService displayNameService;

    private List<EligibleModifier> eligibleModifiers = new ArrayList<>();

    // ═══════════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Creates a new modifier selector page.
     *
     * @param plugin The main plugin instance
     * @param player The player opening the page
     * @param stoneType The type of stone (WARDENS_SEAL or WARDENS_KEY)
     * @param stoneItem The stone item stack
     * @param stoneSlot The slot containing the stone
     * @param stoneContainer Which container the stone is in
     * @param targetItem The target item stack
     * @param targetData The target item's data
     * @param targetSlot The slot containing the target
     * @param targetContainer Which container the target is in
     * @param applicationService Service for applying stones
     */
    public ModifierSelectorPage(
            @Nonnull TrailOfOrbis plugin,
            @Nonnull PlayerRef player,
            @Nonnull StoneType stoneType,
            @Nonnull ItemStack stoneItem,
            short stoneSlot,
            @Nonnull ContainerType stoneContainer,
            @Nonnull ItemStack targetItem,
            @Nonnull ModifiableItem targetData,
            short targetSlot,
            @Nonnull ContainerType targetContainer,
            @Nonnull StoneApplicationService applicationService) {

        this.plugin = plugin;
        this.player = player;
        this.stoneType = stoneType;
        this.stoneItem = stoneItem;
        this.stoneSlot = stoneSlot;
        this.stoneContainer = stoneContainer;
        this.targetItem = targetItem;
        this.targetData = targetData;
        this.targetSlot = targetSlot;
        this.targetContainer = targetContainer;
        this.applicationService = applicationService;

        GearManager gearManager = plugin.getGearManager();
        this.displayNameService = gearManager.getItemDisplayNameService();
    }

    // ═══════════════════════════════════════════════════════════════════
    // PAGE OPEN
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Opens the modifier selector page for the player.
     *
     * <p>Finds eligible modifiers, builds the HYUIML HTML, registers
     * all event listeners, and opens the page.
     *
     * @param store The entity store
     */
    public void open(@Nonnull Store<EntityStore> store) {
        try {
            // Find eligible modifiers
            eligibleModifiers = findEligibleModifiers();

            // Build HTML and open page
            String html = buildHtml();

            PageBuilder builder = PageBuilder.pageForPlayer(player)
                .withLifetime(CustomPageLifetime.CanDismiss)
                .fromHtml(html);

            registerEvents(builder, store);

            builder.open(store);
            plugin.getUIManager().trackOpenPage(player.getUuid(), "modifier_selector");

            LOGGER.atFine().log("Opened modifier selector for %s using %s (%d eligible modifiers)",
                player.getUsername(), stoneType.getDisplayName(), eligibleModifiers.size());

        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to open modifier selector for %s", player.getUsername());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // HTML GENERATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Builds the full HYUIML for the modifier selector page.
     */
    private String buildHtml() {
        StringBuilder html = new StringBuilder(4096);

        html.append("""
            <div class="page-overlay">
                <div class="decorated-container" data-hyui-title="%s Stone">
                    <div class="container-contents" style="layout-mode: Top; anchor-width: %d;">
            """.formatted(escapeHtml(stoneType.getDisplayName()), CONTAINER_WIDTH));

        // Header section
        buildHeader(html);

        // Divider
        buildDivider(html);

        // Modifier list or no-modifiers message
        if (eligibleModifiers.isEmpty()) {
            buildNoModifiersMessage(html);
        } else {
            buildModifierList(html);
        }

        // Divider
        buildDivider(html);

        // Hint text
        buildHint(html);

        // Button row
        buildButtonRow(html);

        html.append("""
                    </div>
                </div>
            </div>
            """);

        return html.toString();
    }

    /**
     * Builds the header section with stone name, item name, and instruction.
     */
    private void buildHeader(StringBuilder html) {
        String instruction = switch (stoneType) {
            case WARDENS_SEAL -> "SELECT MODIFIER TO LOCK :";
            case WARDENS_KEY -> "SELECT MODIFIER TO UNLOCK :";
            default -> "SELECT MODIFIER :";
        };

        String itemName = displayNameService.getDisplayName(targetData, targetItem);

        html.append("""
                        <div style="layout-mode: Top; anchor-horizontal: 0; margin-bottom: 4;">
                            <p style="color: %s; font-size: 14;">%s</p>
                            <p style="color: %s; font-size: 12;">%s</p>
                            <div style="anchor-height: 6;"></div>
                            <p style="color: %s; font-size: 11;">%s</p>
                        </div>
            """.formatted(
                stoneType.getHexColor(),
                escapeHtml(stoneType.getDisplayName()),
                RPGStyles.TEXT_SECONDARY,
                escapeHtml(itemName),
                RPGStyles.TEXT_GRAY,
                instruction
            ));
    }

    /**
     * Builds the scrollable modifier list.
     */
    private void buildModifierList(StringBuilder html) {
        html.append("""
                        <div style="layout-mode: TopScrolling; anchor-horizontal: 0; anchor-height: %d; margin-top: 4;">
            """.formatted(LIST_HEIGHT));

        int count = Math.min(eligibleModifiers.size(), MAX_MODIFIERS);
        for (int i = 0; i < count; i++) {
            buildModifierRow(html, i, eligibleModifiers.get(i));
        }

        html.append("""
                        </div>
            """);
    }

    /**
     * Builds a single modifier row with the overlay pattern.
     *
     * <p>Uses layout-mode: Full parent with text content (bottom layer) and
     * transparent secondary-button overlay (top layer) for click + hover.
     */
    private void buildModifierRow(StringBuilder html, int index, EligibleModifier eligible) {
        ItemModifier mod = eligible.modifier();
        String lockIcon = mod.isLocked() ? "\uD83D\uDD12" : "\uD83D\uDD13";
        String lockColor = mod.isLocked() ? COLOR_LOCK_ICON : COLOR_UNLOCK_ICON;
        String valueStr = formatModifierValue(mod);
        String valueColor = getValueColor(mod.getValue());

        html.append("""
                            <div style="layout-mode: Full; anchor-horizontal: 0; anchor-height: %d; margin-top: %d;">
                                <div style="anchor-horizontal: 0; anchor-vertical: 0; background-color: %s;">
                                    <div style="layout-mode: Left; anchor-horizontal: 0; anchor-vertical: 0; margin-left: 8; margin-right: 8;">
                                        <div style="anchor-width: 28;"><p style="font-size: 14; color: %s;">%s</p></div>
                                        <div style="anchor-width: 60;"><p style="font-size: 10; color: %s;">%s</p></div>
                                        <div style="flex-weight: 1;"><p style="font-size: 12; color: %s;">%s</p></div>
                                        <div style="anchor-width: 70;"><p style="font-size: 11; color: %s;">%s</p></div>
                                    </div>
                                </div>
                                <button id="mod-%d" class="secondary-button"
                                        style="anchor-horizontal: 0; anchor-vertical: 0; background-color: %s;">
                                </button>
                            </div>
            """.formatted(
                ROW_HEIGHT, ROW_MARGIN,
                BG_ROW,
                lockColor, lockIcon,
                COLOR_TYPE_LABEL, eligible.typeLabel(),
                RPGStyles.TEXT_PRIMARY, escapeHtml(mod.displayName()),
                valueColor, escapeHtml(valueStr),
                index,
                BG_OVERLAY
            ));
    }

    /**
     * Builds the no-modifiers message.
     */
    private void buildNoModifiersMessage(StringBuilder html) {
        String message = switch (stoneType) {
            case WARDENS_SEAL -> "No unlocked modifiers to lock";
            case WARDENS_KEY -> "No locked modifiers to unlock";
            default -> "No eligible modifiers";
        };

        html.append("""
                        <div style="layout-mode: Center; anchor-horizontal: 0; anchor-height: 60; margin-top: 8;">
                            <p style="color: %s; font-size: 12;">%s</p>
                        </div>
            """.formatted(RPGStyles.TEXT_MUTED, message));
    }

    /**
     * Builds the hint text section.
     */
    private void buildHint(StringBuilder html) {
        String hint = switch (stoneType) {
            case WARDENS_SEAL -> "Locked modifiers cannot be rerolled";
            case WARDENS_KEY -> "Unlocking allows the modifier to be rerolled";
            default -> "Click a modifier to apply the stone";
        };

        html.append("""
                        <div style="layout-mode: Center; anchor-horizontal: 0; margin-top: 4; margin-bottom: 8;">
                            <p style="color: %s; font-size: 10;">%s</p>
                        </div>
            """.formatted(RPGStyles.TEXT_MUTED, hint));
    }

    /**
     * Builds the bottom button row with Back and Cancel buttons.
     */
    private void buildButtonRow(StringBuilder html) {
        html.append("""
                        <div style="layout-mode: Left; anchor-horizontal: 0; anchor-height: 36; margin-top: 4;">
                            <button id="back-btn" class="secondary-button"
                                    style="anchor-width: 100; anchor-height: 36; margin-right: 8; background-color: #2a2a4a; color: %s; font-size: 12;">
                                Back
                            </button>
                            <div style="flex-weight: 1;"></div>
                            <button id="cancel-btn" class="secondary-button"
                                    style="anchor-width: 100; anchor-height: 36; background-color: #4a2a2a; color: %s; font-size: 12;">
                                Cancel
                            </button>
                        </div>
            """.formatted(RPGStyles.TEXT_PRIMARY, RPGStyles.TEXT_PRIMARY));
    }

    /**
     * Builds a horizontal divider.
     */
    private void buildDivider(StringBuilder html) {
        html.append("""
                        <div style="anchor-height: 1; anchor-horizontal: 0; background-color: %s; margin-top: 8; margin-bottom: 8;"></div>
            """.formatted(RPGStyles.DARK_GRAY));
    }

    // ═══════════════════════════════════════════════════════════════════
    // EVENT REGISTRATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Registers event listeners on the page builder.
     */
    private void registerEvents(PageBuilder builder, Store<EntityStore> store) {
        // Modifier row clicks — direct apply with captured index
        int count = Math.min(eligibleModifiers.size(), MAX_MODIFIERS);
        for (int i = 0; i < count; i++) {
            final int index = i;
            builder.addEventListener("mod-" + i, CustomUIEventBindingType.Activating,
                (data, ctx) -> {
                    ctx.getPage().ifPresent(page -> page.close());
                    handleModifierSelection(index);
                });
        }

        // Back button — reopen StonePickerPage
        builder.addEventListener("back-btn", CustomUIEventBindingType.Activating,
            (data, ctx) -> handleBack());

        // Cancel button — close page
        builder.addEventListener("cancel-btn", CustomUIEventBindingType.Activating,
            (data, ctx) -> {
                ctx.getPage().ifPresent(page -> page.close());
                plugin.getUIManager().closePage(player.getUuid());
            });
    }

    // ═══════════════════════════════════════════════════════════════════
    // EVENT HANDLERS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Handles modifier selection — applies the stone immediately.
     */
    private void handleModifierSelection(int index) {
        if (index < 0 || index >= eligibleModifiers.size()) {
            LOGGER.atWarning().log("Invalid modifier selection index: %d", index);
            return;
        }

        EligibleModifier selected = eligibleModifiers.get(index);
        int originalIndex = selected.originalIndex();

        LOGGER.atFine().log("Modifier selected: %s at original index %d",
            selected.modifier().displayName(), originalIndex);

        // Get fresh player ref and entity
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

        // Apply the stone
        // TODO: Pass modifier index to application service when StoneActionContext is implemented
        StoneApplicationService.ApplicationResult result = applicationService.apply(
            inventory,
            stoneType,
            stoneSlot,
            stoneContainer,
            targetItem,
            targetData,
            targetSlot,
            targetContainer
        );

        // Send feedback message
        if (result.success()) {
            sendSuccessMessage(result.message());
            resyncModifiedItem(result.updatedTargetItem());
        } else {
            sendErrorMessage(result.message());
        }

        plugin.getUIManager().closePage(player.getUuid());
    }

    /**
     * Handles Back button — returns to item picker.
     */
    private void handleBack() {
        plugin.getUIManager().closePage(player.getUuid());

        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) return;

        Store<EntityStore> freshStore = ref.getStore();

        plugin.getUIManager().openStonePicker(
            player,
            freshStore,
            ref,
            stoneType,
            stoneItem,
            stoneSlot,
            stoneContainer
        );
    }

    // ═══════════════════════════════════════════════════════════════════
    // MODIFIER HELPERS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Finds modifiers eligible for the current stone action.
     */
    @Nonnull
    private List<EligibleModifier> findEligibleModifiers() {
        List<EligibleModifier> eligible = new ArrayList<>();
        List<? extends ItemModifier> modifiers = targetData.modifiers();

        for (int i = 0; i < modifiers.size(); i++) {
            ItemModifier mod = modifiers.get(i);

            boolean isEligible = switch (stoneType) {
                case WARDENS_SEAL -> !mod.isLocked();
                case WARDENS_KEY -> mod.isLocked();
                default -> true;
            };

            if (isEligible) {
                String typeLabel = getModifierTypeLabel(mod);
                eligible.add(new EligibleModifier(mod, i, typeLabel));
            }
        }

        return eligible;
    }

    /**
     * Gets the type label for a modifier (PREFIX, SUFFIX, or MOD for maps).
     */
    @Nonnull
    private String getModifierTypeLabel(@Nonnull ItemModifier mod) {
        return mod.typeLabel();
    }

    /**
     * Formats a modifier value for display.
     */
    @Nonnull
    private String formatModifierValue(@Nonnull ItemModifier mod) {
        double value = mod.getValue();
        String sign = value >= 0 ? "+" : "";

        if (mod.isPercent()) {
            if (value == Math.floor(value)) {
                return String.format("%s%.0f%%", sign, value);
            }
            return String.format("%s%.1f%%", sign, value);
        }

        if (value == Math.floor(value)) {
            return String.format("%s%.0f", sign, value);
        }
        return String.format("%s%.1f", sign, value);
    }

    /**
     * Gets the color for a modifier value (green for positive, red for negative).
     */
    @Nonnull
    private String getValueColor(double value) {
        return value >= 0 ? COLOR_VALUE_POSITIVE : COLOR_VALUE_NEGATIVE;
    }

    // ═══════════════════════════════════════════════════════════════════
    // ITEM RESYNC
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Resyncs a modified item to the client after stone application.
     */
    private void resyncModifiedItem(@Nullable ItemStack updatedItem) {
        var gearManager = plugin.getGearManager();
        if (gearManager == null) {
            return;
        }
        ModifiableItemIO.resync(player, updatedItem, targetData, gearManager);
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
