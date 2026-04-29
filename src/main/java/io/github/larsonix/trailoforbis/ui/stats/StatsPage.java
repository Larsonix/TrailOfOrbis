package io.github.larsonix.trailoforbis.ui.stats;

import au.ellie.hyui.builders.ButtonBuilder;
import au.ellie.hyui.builders.ContainerBuilder;
import au.ellie.hyui.builders.GroupBuilder;
import au.ellie.hyui.builders.HyUIAnchor;
import au.ellie.hyui.builders.PageBuilder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.api.ServiceRegistry;
import io.github.larsonix.trailoforbis.api.services.AttributeService;
import io.github.larsonix.trailoforbis.api.services.ConfigService;
import io.github.larsonix.trailoforbis.api.services.SkillTreeService;
import io.github.larsonix.trailoforbis.attributes.ComputedStats;
import io.github.larsonix.trailoforbis.attributes.breakdown.StatBreakdownResult;
import io.github.larsonix.trailoforbis.config.RPGConfig;
import io.github.larsonix.trailoforbis.database.models.PlayerData;
import io.github.larsonix.trailoforbis.leveling.api.LevelingService;
import io.github.larsonix.trailoforbis.maps.RealmsManager;
import io.github.larsonix.trailoforbis.sanctum.SkillSanctumManager;
import io.github.larsonix.trailoforbis.combat.avoidance.AvoidanceProcessor;
import io.github.larsonix.trailoforbis.mobs.stats.MobStatGenerator;
import io.github.larsonix.trailoforbis.mobs.stats.MobStatPoolConfig;
import io.github.larsonix.trailoforbis.ui.RPGStyles;
import io.github.larsonix.trailoforbis.ui.attributes.AttributePage;
import io.github.larsonix.trailoforbis.ui.stats.BuildSummaryCalculator.BuildSummary;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.ToDoubleFunction;
import java.util.logging.Level;

/**
 * HyUI-based stats page implementation.
 *
 * <p>Displays comprehensive character stats organized by category tabs.
 * Uses HYUIML with custom button-based tab navigation for a vanilla
 * Hytale-inspired design with dynamic container resizing.
 *
 * <p>Features:
 * <ul>
 *   <li>Custom tab navigation with dynamic container height per category</li>
 *   <li>Comprehensive stat display from ComputedStats</li>
 *   <li>Color-coded values (green positive, red negative)</li>
 *   <li>Scrollable content with styled scrollbar</li>
 *   <li>Container resizes to fit content without excessive empty space</li>
 * </ul>
 */
public class StatsPage {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // ═══════════════════════════════════════════════════════════════════
    // LAYOUT CONSTANTS
    // ═══════════════════════════════════════════════════════════════════

    private static final int CONTAINER_WIDTH = 800;
    private static final StatCategory DEFAULT_TAB = StatCategory.OVERVIEW;

    // Tab bar layout
    private static final int TAB_HEIGHT = 40;
    private static final int TAB_BAR_HEIGHT = 56;

    /** Max width (pixels) for stat tooltip boxes to prevent infinite stretching. */
    private static final int TOOLTIP_MAX_WIDTH = 350;

    private final TrailOfOrbis plugin;
    private final PlayerRef player;
    private final AtomicReference<StatCategory> selectedTab = new AtomicReference<>(DEFAULT_TAB);
    private Store<EntityStore> store;

    /** Collects IDs of stat rows that have tooltips, populated during HTML building. */
    private final List<String> tooltipRowIds = new ArrayList<>();

    /**
     * Creates a new stats page for a player.
     *
     * @param plugin The plugin instance
     * @param player The player to show stats for
     */
    public StatsPage(@Nonnull TrailOfOrbis plugin, @Nonnull PlayerRef player) {
        this.plugin = plugin;
        this.player = player;
    }

    /**
     * Opens the stats page for the player.
     *
     * @param store The entity store
     */
    public void open(@Nonnull Store<EntityStore> store) {
        this.store = store;

        try {
            // Get player data and stats
            AttributeService attributeService = ServiceRegistry.require(AttributeService.class);
            ConfigService configService = ServiceRegistry.require(ConfigService.class);
            UUID uuid = player.getUuid();

            Optional<PlayerData> dataOpt = attributeService.getPlayerDataRepository().get(uuid);
            if (dataOpt.isEmpty()) {
                LOGGER.at(Level.WARNING).log("Cannot open stats page - no player data for %s", player.getUsername());
                return;
            }

            PlayerData data = dataOpt.get();
            ComputedStats stats = attributeService.getStats(uuid);
            StatBreakdownResult breakdown = attributeService.getStatBreakdown(uuid);
            RPGConfig.AttributeConfig attrs = configService.getRPGConfig().getAttributes();

            // Get leveling info
            int level = 1;
            float levelProgress = 0f;
            long xp = 0L;
            long xpInLevel = 0L;
            long xpForLevel = 0L;
            LevelingService levelingService = ServiceRegistry.get(LevelingService.class).orElse(null);
            if (levelingService != null) {
                level = levelingService.getLevel(uuid);
                levelProgress = levelingService.getLevelProgress(uuid);
                xp = levelingService.getXp(uuid);

                // Calculate XP progress within current level
                int maxLevel = levelingService.getMaxLevel();
                if (level >= maxLevel) {
                    // At max level - show 0/0 to indicate max reached
                    xpInLevel = 0;
                    xpForLevel = 0;
                } else {
                    long xpForCurrentLevel = levelingService.getXpForLevel(level);
                    long xpForNextLevel = levelingService.getXpForLevel(level + 1);
                    xpInLevel = xp - xpForCurrentLevel;
                    xpForLevel = xpForNextLevel - xpForCurrentLevel;
                }
            }

            // Compute Build Summary for Overview tab
            RPGConfig.CombatConfig.EvasionConfig evasionCfg = null;
            MobStatPoolConfig poolConfig = null;
            try {
                RPGConfig rpgConfig = configService.getRPGConfig();
                if (rpgConfig != null) {
                    evasionCfg = rpgConfig.getCombat().getEvasion();
                }
                poolConfig = configService.getMobStatPoolConfig();
            } catch (Exception ignored) {
                // Non-critical — EHP will omit evasion avoidance
            }
            BuildSummary buildSummary = BuildSummaryCalculator.compute(stats, level, evasionCfg, poolConfig);

            // Build HTML with initial height from default tab
            String html = buildHtml(data, stats, breakdown, attrs, level, levelProgress, xpInLevel, xpForLevel, buildSummary);

            // Build page - ESC dismisses via CanDismiss lifetime
            PageBuilder builder = PageBuilder.pageForPlayer(player)
                .withLifetime(CustomPageLifetime.CanDismiss)
                .fromHtml(html);

            // Add close button handler - use HyUI's page.close() method
            builder.addEventListener("close-btn", CustomUIEventBindingType.Activating,
                (data2, ctx) -> ctx.getPage().ifPresent(page -> page.close()));

            // Add skill tree button handler - toggles 3D Skill Sanctum (enter/exit)
            builder.addEventListener("skilltree-btn", CustomUIEventBindingType.Activating,
                (data2, ctx) -> {
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

            // Add attributes button handler - direct page navigation
            // Don't close current page - opening a new page auto-replaces it
            builder.addEventListener("attr-btn", CustomUIEventBindingType.Activating,
                (data2, ctx) -> {
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
                            new AttributePage(plugin, freshPlayer).open(freshStore);
                        }
                    });
                });

            // Register tab click handlers for dynamic resizing
            registerTabHandlers(builder);

            // Apply tooltip style (maxWidth + wrap) to all tooltipped stat rows
            applyTooltipStyles(builder);

            // Open the page
            builder.open(store);

            LOGGER.at(Level.INFO).log("Opened stats page for %s", player.getUsername());

        } catch (Exception e) {
            LOGGER.at(Level.SEVERE).withCause(e).log("Failed to open stats page for %s", player.getUsername());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // TAB HANDLERS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Registers click handlers for all tab buttons.
     *
     * <p>Each handler:
     * <ul>
     *   <li>Updates the selected tab state</li>
     *   <li>Resizes the container to match the category's preferred height</li>
     *   <li>Updates tab button styling (highlight selected, dim others)</li>
     *   <li>Toggles content visibility (show selected, hide others)</li>
     * </ul>
     */
    private void registerTabHandlers(@Nonnull PageBuilder builder) {
        for (StatCategory category : StatCategory.values()) {
            registerTabHandler(builder, category);
        }
    }

    /**
     * Registers a click handler for a single tab button.
     */
    private void registerTabHandler(@Nonnull PageBuilder builder, @Nonnull StatCategory category) {
        String tabId = "tab-" + category.getId();

        builder.addEventListener(tabId, CustomUIEventBindingType.Activating, (data, ctx) -> {
            StatCategory previousTab = selectedTab.get();

            // Skip if already selected
            if (previousTab == category) {
                return;
            }

            selectedTab.set(category);

            // Resize container to match category's preferred height
            // Note: decorated-container maps to ContainerBuilder, not GroupBuilder
            ctx.getById("stats-container", ContainerBuilder.class).ifPresent(container -> {
                container.withAnchor(new HyUIAnchor().setWidth(CONTAINER_WIDTH).setHeight(category.getPreferredHeight()));
            });

            // Update tab indicators - show selected, hide others
            for (StatCategory cat : StatCategory.values()) {
                boolean isSelected = (cat == category);

                // Toggle indicator visibility
                ctx.getById("indicator-" + cat.getId(), GroupBuilder.class).ifPresent(indicator -> {
                    indicator.withVisible(isSelected);
                });
            }

            // Toggle content visibility - show selected, hide others
            // Must also set flex-weight to 0 for hidden elements to remove from layout
            for (StatCategory cat : StatCategory.values()) {
                boolean shouldShow = (cat == category);
                ctx.getById("content-" + cat.getId(), GroupBuilder.class).ifPresent(content -> {
                    content.withVisible(shouldShow);
                    content.withFlexWeight(shouldShow ? 1 : 0);
                });
            }

            // Rebuild page to apply all changes
            ctx.updatePage(true);
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    // TOOLTIP STYLE
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Applies a TextTooltipStyle with maxWidth to all tooltipped stat rows.
     *
     * <p>Without this, the tooltip background box stretches infinitely to the right
     * because the engine doesn't constrain width for multi-span tooltips by default.
     */
    private void applyTooltipStyles(@Nonnull PageBuilder builder) {
        if (tooltipRowIds.isEmpty()) return;

        // Set individual TextTooltipStyle sub-properties as primitives.
        // Setting the whole TextTooltipStyle object on Group elements crashes the client,
        // but setting individual sub-properties as simple values should work.
        for (String id : tooltipRowIds) {
            builder.getById(id, GroupBuilder.class).ifPresent(group -> {
                group.editElementAfter((commands, selector) -> {
                    commands.set(selector + ".TextTooltipStyle.MaxWidth", TOOLTIP_MAX_WIDTH);
                });
            });
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // HTML BUILDING
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Builds the complete HTML for the stats page.
     *
     * <p>Uses HyUI's native tab navigation system:
     * <ul>
     *   <li>{@code <nav class="tabs">} with {@code data-tabs} for tab definitions</li>
     *   <li>{@code <div class="tab-content">} with {@code data-hyui-tab-id} for content</li>
     * </ul>
     */
    @Nonnull
    private String buildHtml(
        @Nonnull PlayerData data,
        @Nonnull ComputedStats stats,
        @Nullable StatBreakdownResult breakdown,
        @Nonnull RPGConfig.AttributeConfig attrs,
        int level,
        float levelProgress,
        long xpInLevel,
        long xpForLevel,
        @Nonnull BuildSummary buildSummary
    ) {
        tooltipRowIds.clear();
        StringBuilder sb = new StringBuilder();

        // Use initial height from default tab
        int initialHeight = DEFAULT_TAB.getPreferredHeight();

        // Page overlay with centered vertical stack (container + close button)
        sb.append("<div class=\"page-overlay\">\n");
        // Wrapper to center everything vertically and horizontally
        sb.append("  <div style=\"layout-mode: Middle; anchor-horizontal: 0; anchor-vertical: 0;\">\n");
        // Vertical stack: container, then close button
        sb.append("    <div style=\"layout-mode: Top;\">\n");

        // Decorated container with ID for dynamic resizing
        sb.append("      <div id=\"stats-container\" class=\"decorated-container\" data-hyui-title=\"Character Stats\" ");
        sb.append("style=\"anchor-width: ").append(CONTAINER_WIDTH).append("; anchor-height: ").append(initialHeight).append(";\">\n");
        sb.append("        <div class=\"container-contents\" style=\"layout-mode: Top;\">\n");

        // Header with player name and level
        sb.append(buildHeader(level, levelProgress, xpInLevel, xpForLevel));

        // Native HyUI tab navigation
        sb.append(buildTabNavigation());

        // Tab content areas (all rendered, HyUI shows/hides automatically)
        sb.append(buildAllTabContents(data, stats, breakdown, attrs, level, levelProgress, xpInLevel, xpForLevel, buildSummary));

        // Footer with hint
        sb.append(buildFooter(data));

        sb.append("        </div>\n");
        sb.append("      </div>\n");

        // Navigation buttons below the container: Close (left), Attributes (center), Skill Tree (right)
        sb.append("      <div style=\"layout-mode: Center; anchor-height: 70; anchor-top: 10; anchor-horizontal: 0;\">\n");
        sb.append("        <div style=\"layout-mode: Left;\">\n");
        sb.append("          <button id=\"close-btn\" class=\"secondary-button\" style=\"anchor-width: 210; anchor-height: 53;\">Close</button>\n");
        sb.append("          <div style=\"anchor-width: 20;\"></div>\n");
        sb.append("          <button id=\"attr-btn\" class=\"secondary-button\" style=\"anchor-width: 210; anchor-height: 53;\">Attributes</button>\n");
        sb.append("          <div style=\"anchor-width: 20;\"></div>\n");
        sb.append("          <button id=\"skilltree-btn\" class=\"secondary-button\" style=\"anchor-width: 210; anchor-height: 53;\">Skill Tree</button>\n");
        sb.append("        </div>\n");
        sb.append("      </div>\n");

        sb.append("    </div>\n");
        sb.append("  </div>\n");
        sb.append("</div>\n");

        return sb.toString();
    }

    /**
     * Builds the header section showing player info.
     */
    @Nonnull
    private String buildHeader(int level, float levelProgress, long xpInLevel, long xpForLevel) {
        StringBuilder sb = new StringBuilder();

        // Spacer before header
        sb.append("      <div style=\"anchor-height: 8;\"></div>\n");

        // Header row with player name and level badge - no background, cleaner look
        sb.append("      <div style=\"layout-mode: Left; anchor-height: 40; anchor-horizontal: 0;\" data-hyui-style=\"Padding: (Horizontal: 20; Vertical: 4)\">\n");
        sb.append("        <div style=\"flex-weight: 1; layout-mode: Left;\"><p style=\"font-size: 22; color: ").append(RPGStyles.TITLE_GOLD).append(";\">").append(player.getUsername()).append("</p></div>\n");
        sb.append("        <div style=\"layout-mode: MiddleCenter;\">\n");
        sb.append("          <p style=\"font-size: 16; color: ").append(RPGStyles.TEXT_SECONDARY).append(";\">Level ").append(level).append("</p>\n");
        sb.append("        </div>\n");
        sb.append("      </div>\n");

        // XP progress line - subtle separator
        sb.append("      <div style=\"layout-mode: Left; anchor-height: 26; anchor-horizontal: 0;\" data-hyui-style=\"Padding: (Horizontal: 20; Vertical: 4)\">\n");
        String xpDisplay = xpForLevel == 0
            ? "XP : MAX"
            : String.format("XP : %,d / %,d", xpInLevel, xpForLevel);
        sb.append("        <div style=\"flex-weight: 1;\"><p style=\"font-size: 13; color: ").append(RPGStyles.TEXT_MUTED).append(";\">").append(xpDisplay).append("</p></div>\n");
        sb.append("        <div><p style=\"font-size: 13; color: ").append(RPGStyles.TEXT_MUTED).append(";\">").append(String.format("%.0f%%", levelProgress * 100)).append("</p></div>\n");
        sb.append("      </div>\n");

        // Subtle divider line
        sb.append("      <div style=\"anchor-height: 1; anchor-horizontal: 0; background-color: #333344;\"></div>\n");
        sb.append("      <div style=\"anchor-height: 6;\"></div>\n");

        return sb.toString();
    }

    /**
     * Builds the custom tab bar with buttons for each category.
     *
     * <p>Uses custom buttons instead of HyUI's native tabs to enable
     * dynamic container resizing when tabs are clicked. Each button has
     * an ID in format {@code tab-<categoryId>} for event handling.
     */
    @Nonnull
    private String buildTabNavigation() {
        StringBuilder sb = new StringBuilder();

        // Tab bar container - centered horizontal layout
        sb.append("      <div style=\"layout-mode: Center; anchor-height: ").append(TAB_BAR_HEIGHT).append("; anchor-horizontal: 0;\">\n");
        sb.append("        <div style=\"layout-mode: Left;\" data-hyui-style=\"Padding: (Horizontal: 4)\">\n");

        // Create a button + indicator for each category
        for (StatCategory cat : StatCategory.values()) {
            boolean isSelected = (cat == DEFAULT_TAB);
            String indicatorDisplay = isSelected ? "" : " display: none;";

            // Vertical wrapper for button + indicator
            sb.append("          <div style=\"layout-mode: Top;\">\n");

            // Tab button - small-secondary-button for compact text and padding
            sb.append("            <button id=\"tab-").append(cat.getId())
              .append("\" class=\"small-secondary-button\" style=\"anchor-width: ").append(cat.getTabWidth())
              .append("; anchor-height: ").append(TAB_HEIGHT)
              .append("\">").append(cat.getDisplayName()).append("</button>\n");

            // Selection indicator line (gold bar below active tab)
            sb.append("            <div id=\"indicator-").append(cat.getId())
              .append("\" style=\"anchor-width: ").append(cat.getTabWidth())
              .append("; anchor-height: 3; background-color: #ffd700;").append(indicatorDisplay).append("\"></div>\n");

            sb.append("          </div>\n");

            // Gap between tabs
            sb.append("          <div style=\"anchor-width: 5;\"></div>\n");
        }

        sb.append("        </div>\n");
        sb.append("      </div>\n");

        // Spacer after tabs
        sb.append("      <div style=\"anchor-height: 8;\"></div>\n");

        return sb.toString();
    }

    /**
     * Builds all tab content areas.
     *
     * <p>All content is rendered at once with visibility controlled manually.
     * Only the default tab is visible initially; tab click handlers toggle visibility.
     */
    @Nonnull
    private String buildAllTabContents(
        @Nonnull PlayerData data,
        @Nonnull ComputedStats stats,
        @Nullable StatBreakdownResult breakdown,
        @Nonnull RPGConfig.AttributeConfig attrs,
        int level,
        float levelProgress,
        long xpInLevel,
        long xpForLevel,
        @Nonnull BuildSummary buildSummary
    ) {
        StringBuilder sb = new StringBuilder();

        for (StatCategory category : StatCategory.values()) {
            // Only the default tab is visible initially
            // Use display: none (not visibility: hidden) to remove from layout flow
            boolean isVisible = (category == DEFAULT_TAB);
            String displayStyle = isVisible ? "" : " display: none;";

            // Use TopScrolling only for tabs that need scrolling (reserves scrollbar space)
            // Use Top for others (extends to full width without scrollbar gap)
            String layoutMode = category.needsScrolling() ? "TopScrolling" : "Top";

            // Content area with ID for visibility toggling
            String scrollbarAttr = category.needsScrolling()
                ? " data-hyui-scrollbar-style=\"Common.ui DefaultExtraSpacingScrollbarStyle\""
                : "";
            sb.append("      <div id=\"content-").append(category.getId())
              .append("\" style=\"layout-mode: ").append(layoutMode).append("; anchor-horizontal: 0; flex-weight: 1;").append(displayStyle).append("\"")
              .append(scrollbarAttr).append(">\n");

            sb.append(buildCategoryContent(category, data, stats, breakdown, attrs, level, levelProgress, xpInLevel, xpForLevel, buildSummary));

            sb.append("      </div>\n");
        }

        return sb.toString();
    }

    /**
     * Builds content for a specific category.
     */
    @Nonnull
    private String buildCategoryContent(
        @Nonnull StatCategory category,
        @Nonnull PlayerData data,
        @Nonnull ComputedStats stats,
        @Nullable StatBreakdownResult breakdown,
        @Nonnull RPGConfig.AttributeConfig attrs,
        int level,
        float levelProgress,
        long xpInLevel,
        long xpForLevel,
        @Nonnull BuildSummary buildSummary
    ) {
        return switch (category) {
            case OVERVIEW -> buildOverviewContent(data, stats, buildSummary, level, levelProgress, xpInLevel, xpForLevel);
            case ATTRIBUTES -> buildAttributesContent(data, attrs);
            case RESOURCES -> buildResourcesContent(stats, breakdown);
            case OFFENSE -> buildOffenseContent(stats, breakdown);
            case DEFENSE -> buildDefenseContent(stats, breakdown, level, buildSummary);
            case MOVEMENT -> buildMovementContent(stats, breakdown);
        };
    }

    /**
     * Builds the footer with hint about unallocated points.
     */
    @Nonnull
    private String buildFooter(@Nonnull PlayerData data) {
        StringBuilder sb = new StringBuilder();

        // Subtle divider
        sb.append("          <div style=\"anchor-height: 1; anchor-horizontal: 0; background-color: #333344;\"></div>\n");

        // Footer row with hint - centered both horizontally and vertically
        sb.append("          <div style=\"layout-mode: MiddleCenter; anchor-height: 50; anchor-horizontal: 0;\" data-hyui-style=\"Padding: (Horizontal: 20)\">\n");

        // Hint about unallocated points or usage tip
        if (data.getUnallocatedPoints() > 0) {
            sb.append("            <p style=\"font-size: 14; color: ").append(RPGStyles.POSITIVE).append(";\">");
            sb.append(data.getUnallocatedPoints()).append(" unallocated points - use /attr to allocate</p>\n");
        } else {
            sb.append("            <p style=\"font-size: 14; color: ").append(RPGStyles.TEXT_MUTED).append(";\">Use /attr to allocate attribute points</p>\n");
        }

        sb.append("          </div>\n");

        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════════════════
    // CATEGORY CONTENT BUILDERS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Builds the Overview tab content.
     */
    @Nonnull
    private String buildOverviewContent(
        @Nonnull PlayerData data,
        @Nonnull ComputedStats stats,
        @Nonnull BuildSummary summary,
        int level,
        float levelProgress,
        long xpInLevel,
        long xpForLevel
    ) {
        List<StatRow> rows = new ArrayList<>();
        Map<String, String> tooltips = new LinkedHashMap<>();

        // ── COMBAT POWER (hero section) ──
        rows.add(new StatRow("COMBAT POWER", null, null, false, true));

        // Avg Damage / Hit — always shown
        if (summary.hasWeapon()) {
            rows.add(new StatRow("Avg Damage / Hit",
                String.format("%,d", (int) summary.avgDamagePerHit()),
                null, true, false));
            tooltips.put("Avg Damage / Hit", buildDamageTooltip(summary));
        } else {
            rows.add(new StatRow("Avg Damage / Hit", "\u2014",
                "No weapon equipped", false, false));
        }

        // Effective HP — always shown
        rows.add(new StatRow("Effective HP",
            String.format("%,d", (int) summary.effectiveHP()),
            null, true, false));
        tooltips.put("Effective HP", buildEHPTooltip(summary));

        // Attack Speed — hide if 0
        float attackSpeed = stats.getAttackSpeedPercent();
        if (attackSpeed != 0) {
            rows.add(new StatRow("Attack Speed",
                RPGStyles.formatValue(attackSpeed, true),
                null, attackSpeed > 0, false));
        }

        // Crit Chance — always shown in combat power
        rows.add(new StatRow("Crit Chance",
            RPGStyles.formatValue(stats.getCriticalChance(), true),
            null, stats.getCriticalChance() > 0, false));

        // Life Steal — hide if 0
        float lifeSteal = stats.getLifeSteal();
        if (lifeSteal > 0) {
            rows.add(new StatRow("Life Steal",
                RPGStyles.formatValue(lifeSteal, true),
                null, true, false));
        }

        // ── PROGRESSION (compact) ──
        rows.add(new StatRow(null, null, null, false, false)); // Spacer
        rows.add(new StatRow("PROGRESSION", null, null, false, true));

        // Level — always shown
        String xpDetail = xpForLevel == 0
            ? "MAX"
            : String.format("%.0f%% to next", levelProgress * 100);
        rows.add(new StatRow("Level", String.valueOf(level), xpDetail, false, false));

        // Attribute Points — only if unallocated > 0
        if (data.getUnallocatedPoints() > 0) {
            rows.add(new StatRow("Attribute Points",
                String.valueOf(data.getUnallocatedPoints()),
                "available to spend", true, false));
        }

        // Skill Points — only if unallocated > 0
        SkillTreeService skillTreeService = ServiceRegistry.get(SkillTreeService.class).orElse(null);
        if (skillTreeService != null) {
            int availablePoints = skillTreeService.getAvailablePoints(player.getUuid());
            if (availablePoints > 0) {
                rows.add(new StatRow("Skill Points",
                    String.valueOf(availablePoints),
                    "available to spend", true, false));
            }
        }

        // ── RESISTANCES (compact, always shown) ──
        rows.add(new StatRow(null, null, null, false, false)); // Spacer
        rows.add(new StatRow("RESISTANCES", null, null, false, true));
        rows.add(new StatRow("Fire", RPGStyles.formatValue(stats.getFireResistance(), true), null, stats.getFireResistance() != 0, false, RPGStyles.ELEMENT_FIRE));
        rows.add(new StatRow("Water", RPGStyles.formatValue(stats.getWaterResistance(), true), null, stats.getWaterResistance() != 0, false, RPGStyles.ELEMENT_WATER));
        rows.add(new StatRow("Lightning", RPGStyles.formatValue(stats.getLightningResistance(), true), null, stats.getLightningResistance() != 0, false, RPGStyles.ELEMENT_LIGHTNING));
        rows.add(new StatRow("Earth", RPGStyles.formatValue(stats.getEarthResistance(), true), null, stats.getEarthResistance() != 0, false, RPGStyles.ELEMENT_EARTH));
        rows.add(new StatRow("Wind", RPGStyles.formatValue(stats.getWindResistance(), true), null, stats.getWindResistance() != 0, false, RPGStyles.ELEMENT_WIND));
        rows.add(new StatRow("Void", RPGStyles.formatValue(stats.getVoidResistance(), true), null, stats.getVoidResistance() != 0, false, RPGStyles.ELEMENT_VOID));

        return buildStatRows(rows, tooltips);
    }

    /**
     * Builds the tooltip for Avg Damage / Hit showing the full damage pipeline.
     */
    @Nonnull
    private String buildDamageTooltip(@Nonnull BuildSummary summary) {
        var d = summary.damageDetail();
        StringBuilder sb = new StringBuilder("<tooltip>");

        // Title
        ttSpan(sb, "Avg Damage / Hit: " + String.format("%,d", (int) d.avgDamagePerHit()), RPGStyles.TITLE_GOLD, true);
        ttSeparator(sb);

        // Weapon base
        ttSpan(sb, "Weapon Base: " + RPGStyles.formatFlat(d.weaponBase()), RPGStyles.TEXT_GRAY);

        // Flat adds (only non-zero)
        if (d.flatPhysical() > 0) {
            ttSpan(sb, "+ Flat Physical: +" + RPGStyles.formatFlat(d.flatPhysical()), RPGStyles.POSITIVE);
        }
        if (d.flatMelee() > 0) {
            ttSpan(sb, "+ Flat Melee: +" + RPGStyles.formatFlat(d.flatMelee()), RPGStyles.POSITIVE);
        }
        if (d.flatElemental() > 0) {
            ttSpan(sb, "+ Flat Elemental: +" + RPGStyles.formatFlat(d.flatElemental()), RPGStyles.POSITIVE);
        }

        // Base total
        ttSpan(sb, "= Base Total: " + RPGStyles.formatFlat(d.baseTotal()), RPGStyles.TEXT_PRIMARY);

        // % Increased (if any)
        if (d.totalIncreasedPct() != 0) {
            ttSpan(sb, "x Increased: x" + RPGStyles.formatNumber(d.increasedMult())
                + " (+" + RPGStyles.formatFlat(d.totalIncreasedPct()) + "%)", "#BB99FF");
        }

        // % More (if any)
        if (d.allDamagePct() != 0 || d.damageMultiplier() != 0) {
            StringBuilder moreLine = new StringBuilder("x More: x" + RPGStyles.formatNumber(d.moreMult()));
            if (d.allDamagePct() != 0) {
                moreLine.append(" (+").append(RPGStyles.formatFlat(d.allDamagePct())).append("% all)");
            }
            if (d.damageMultiplier() != 0) {
                moreLine.append(" (+").append(RPGStyles.formatFlat(d.damageMultiplier())).append("% more)");
            }
            ttSpan(sb, moreLine.toString(), "#BB99FF");
        }

        // Expected crit
        if (d.critChance() > 0) {
            ttSpan(sb, "x Expected Crit: x" + RPGStyles.formatNumber(d.expectedCritMult())
                + " (" + RPGStyles.formatFlat(d.critChance()) + "% x "
                + RPGStyles.formatFlat(d.critMultRaw()) + "%)", RPGStyles.TEXT_ORANGE);
        }

        // Final result
        ttSeparator(sb);
        ttSpan(sb, "= Average Hit: " + String.format("%,d", (int) d.avgDamagePerHit()), RPGStyles.TITLE_GOLD, true);

        ttClose(sb);
        return sb.toString();
    }

    /**
     * Builds the tooltip for Effective HP showing armor and avoidance layers.
     */
    @Nonnull
    private String buildEHPTooltip(@Nonnull BuildSummary summary) {
        var e = summary.ehpDetail();
        StringBuilder sb = new StringBuilder("<tooltip>");

        // Title
        ttSpan(sb, "Effective HP: " + String.format("%,d", (int) e.effectiveHP()), RPGStyles.TITLE_GOLD, true);
        ttSeparator(sb);

        // Max Health
        ttSpan(sb, "Max Health: " + String.format("%,d", (int) e.maxHealth()), "#FF8888");

        // Energy Shield (if any)
        if (e.energyShield() > 0) {
            ttSpan(sb, "+ Energy Shield: +" + String.format("%,d", (int) e.energyShield()), "#88CCFF");
        }

        // Raw HP
        ttSpan(sb, "= Raw HP: " + String.format("%,d", (int) e.rawHP()), RPGStyles.TEXT_PRIMARY);

        // Armor
        if (e.armor() > 0) {
            ttSpan(sb, "Armor: " + String.format("%,d", (int) e.armor())
                + " (" + String.format("%.1f%%", e.armorMitigation() * 100f) + " reduction)", RPGStyles.TEXT_ORANGE);
            ttSpan(sb, "= After Armor: " + String.format("%,d", (int) e.ehpFromArmor()), RPGStyles.TEXT_PRIMARY);
        }

        // Avoidance (if any)
        if (e.combinedAvoidPct() > 0) {
            StringBuilder avoidParts = new StringBuilder();
            if (e.dodgeChancePct() > 0) avoidParts.append("dodge");
            if (e.evasionAvoidPct() > 0) {
                if (!avoidParts.isEmpty()) avoidParts.append(" + ");
                avoidParts.append("evasion");
            }
            if (e.blockAvoidPct() > 0) {
                if (!avoidParts.isEmpty()) avoidParts.append(" + ");
                avoidParts.append("block");
            }
            if (e.parryAvoidPct() > 0) {
                if (!avoidParts.isEmpty()) avoidParts.append(" + ");
                avoidParts.append("parry");
            }
            ttSpan(sb, "Avoidance: " + String.format("%.1f%%", e.combinedAvoidPct())
                + " (" + avoidParts + ")", RPGStyles.POSITIVE);
        }

        // Final result
        ttSeparator(sb);
        ttSpan(sb, "= Effective HP: " + String.format("%,d", (int) e.effectiveHP()), RPGStyles.TITLE_GOLD, true);

        ttClose(sb);
        return sb.toString();
    }

    /**
     * Builds the Attributes tab content.
     */
    @Nonnull
    private String buildAttributesContent(
        @Nonnull PlayerData data,
        @Nonnull RPGConfig.AttributeConfig attrs
    ) {
        List<StatRow> rows = new ArrayList<>();
        Map<String, String> tooltips = new LinkedHashMap<>();

        rows.add(new StatRow("ELEMENTAL ATTRIBUTES", null, null, false, true));

        // FIRE - Glass cannon
        int fp = data.getFire();
        float fm = fp == 0 ? 1 : fp;
        var fg = attrs.getFireGrants();
        rows.add(new StatRow("Fire", String.valueOf(fp), null, false, false, RPGStyles.ELEMENT_FIRE));
        buildAttributeTooltip(tooltips, "Fire", fp, RPGStyles.ELEMENT_FIRE, "Glass Cannon",
            String.format("%+.1f%% Physical Damage", fm * fg.getPhysicalDamagePercent()),
            String.format("%+.1f%% Charged Attack Damage", fm * fg.getChargedAttackDamagePercent()),
            String.format("%+.1f%% Critical Multiplier", fm * fg.getCriticalMultiplier()),
            String.format("%+.1f%% Burn Damage", fm * fg.getBurnDamagePercent()),
            String.format("%+.1f%% Ignite Chance", fm * fg.getIgniteChance()));

        // WATER - Arcane mage
        int wtp = data.getWater();
        float wtm = wtp == 0 ? 1 : wtp;
        var wg = attrs.getWaterGrants();
        rows.add(new StatRow("Water", String.valueOf(wtp), null, false, false, RPGStyles.ELEMENT_WATER));
        buildAttributeTooltip(tooltips, "Water", wtp, RPGStyles.ELEMENT_WATER, "Arcane Mage",
            String.format("%+.1f%% Spell Damage", wtm * wg.getSpellDamagePercent()),
            String.format("%+.1f Max Mana", wtm * wg.getMaxMana()),
            String.format("%+.0f Energy Shield", wtm * wg.getEnergyShield()),
            String.format("%+.1f/s Mana Regen", wtm * wg.getManaRegen()),
            String.format("%+.1f%% Freeze Chance", wtm * wg.getFreezeChance()));

        // LIGHTNING - Storm blitz
        int lp = data.getLightning();
        float lm = lp == 0 ? 1 : lp;
        var lg = attrs.getLightningGrants();
        rows.add(new StatRow("Lightning", String.valueOf(lp), null, false, false, RPGStyles.ELEMENT_LIGHTNING));
        buildAttributeTooltip(tooltips, "Lightning", lp, RPGStyles.ELEMENT_LIGHTNING, "Storm Blitz",
            String.format("%+.1f%% Attack Speed", lm * lg.getAttackSpeedPercent()),
            String.format("%+.1f%% Move Speed", lm * lg.getMoveSpeedPercent()),
            String.format("%+.1f%% Critical Chance", lm * lg.getCritChance()),
            String.format("%+.1f/s Stamina Regen", lm * lg.getStaminaRegen()),
            String.format("%+.1f%% Shock Chance", lm * lg.getShockChance()));

        // EARTH - Iron fortress
        int ep = data.getEarth();
        float em = ep == 0 ? 1 : ep;
        var eg = attrs.getEarthGrants();
        rows.add(new StatRow("Earth", String.valueOf(ep), null, false, false, RPGStyles.ELEMENT_EARTH));
        buildAttributeTooltip(tooltips, "Earth", ep, RPGStyles.ELEMENT_EARTH, "Iron Fortress",
            String.format("%+.1f%% Max Health", em * eg.getMaxHealthPercent()),
            String.format("%+.0f Armor", em * eg.getArmor()),
            String.format("%+.1f/s Health Regen", em * eg.getHealthRegen()),
            String.format("%+.1f%% Perfect Block", em * eg.getBlockChance()),
            String.format("%+.1f%% Knockback Resistance", em * eg.getKnockbackResistance()));

        // WIND - Ghost ranger
        int wdp = data.getWind();
        float wdm = wdp == 0 ? 1 : wdp;
        var wdg = attrs.getWindGrants();
        rows.add(new StatRow("Wind", String.valueOf(wdp), null, false, false, RPGStyles.ELEMENT_WIND));
        buildAttributeTooltip(tooltips, "Wind", wdp, RPGStyles.ELEMENT_WIND, "Ghost Ranger",
            String.format("%+.0f Evasion", wdm * wdg.getEvasion()),
            String.format("%+.0f Accuracy", wdm * wdg.getAccuracy()),
            String.format("%+.1f%% Projectile Damage", wdm * wdg.getProjectileDamagePercent()),
            String.format("%+.1f%% Jump Force", wdm * wdg.getJumpForcePercent()),
            String.format("%+.1f%% Projectile Speed", wdm * wdg.getProjectileSpeedPercent()));

        // VOID - Life devourer
        int vp = data.getVoidAttr();
        float vm = vp == 0 ? 1 : vp;
        var vg = attrs.getVoidGrants();
        rows.add(new StatRow("Void", String.valueOf(vp), null, false, false, RPGStyles.ELEMENT_VOID));
        buildAttributeTooltip(tooltips, "Void", vp, RPGStyles.ELEMENT_VOID, "Life Devourer",
            String.format("%+.1f%% Life Steal", vm * vg.getLifeSteal()),
            String.format("%+.2f%% True Damage", vm * vg.getPercentHitAsTrueDamage()),
            String.format("%+.1f%% DoT Damage", vm * vg.getDotDamagePercent()),
            String.format("%+.1f Mana on Kill", vm * vg.getManaOnKill()),
            String.format("%+.1f%% Status Effect Duration", vm * vg.getStatusEffectDuration()));

        // Summary
        rows.add(new StatRow(null, null, null, false, false));
        rows.add(new StatRow("SUMMARY", null, null, false, true));
        rows.add(new StatRow("Total Allocated", String.valueOf(data.getTotalAllocatedPoints()), null, false, false));
        rows.add(new StatRow("Unallocated", String.valueOf(data.getUnallocatedPoints()), null, data.getUnallocatedPoints() > 0, false));

        return buildStatRows(rows, tooltips);
    }

    /**
     * Builds the Resources tab content.
     */
    @Nonnull
    private String buildResourcesContent(@Nonnull ComputedStats stats, @Nullable StatBreakdownResult breakdown) {
        List<StatRow> rows = new ArrayList<>();
        Map<String, String> tooltips = new LinkedHashMap<>();

        // Health
        rows.add(new StatRow("HEALTH", null, null, false, true));
        rows.add(new StatRow("Max Health", RPGStyles.formatFlat(stats.getMaxHealth()), formatPercent(stats.getMaxHealthPercent()), false, false));
        rows.add(new StatRow("Health Regen", formatRegen(stats.getHealthRegen()), formatPercent(stats.getHealthRegenPercent()), stats.getHealthRegen() > 0, false));
        rows.add(new StatRow("Health Recovery", RPGStyles.formatValue(stats.getHealthRecoveryPercent(), true), null, stats.getHealthRecoveryPercent() != 0, false));

        // Mana
        rows.add(new StatRow(null, null, null, false, false));
        rows.add(new StatRow("MANA", null, null, false, true));
        rows.add(new StatRow("Max Mana", RPGStyles.formatFlat(stats.getMaxMana()), formatPercent(stats.getMaxManaPercent()), false, false));
        rows.add(new StatRow("Mana Regen", formatRegen(stats.getManaRegen()), null, stats.getManaRegen() > 0, false));
        rows.add(new StatRow("Mana on Kill", RPGStyles.formatFlat(stats.getManaOnKill()), null, stats.getManaOnKill() > 0, false));
        rows.add(new StatRow("Mana Cost", RPGStyles.formatValue(stats.getManaCostPercent(), true), null, stats.getManaCostPercent() != 0, false));
        rows.add(new StatRow("Mana Cost Reduction", RPGStyles.formatValue(stats.getManaCostReduction(), true), null, stats.getManaCostReduction() > 0, false));

        // Stamina
        rows.add(new StatRow(null, null, null, false, false));
        rows.add(new StatRow("STAMINA", null, null, false, true));
        rows.add(new StatRow("Max Stamina", RPGStyles.formatFlat(stats.getMaxStamina()),
            formatPercent(stats.getMaxStaminaPercent()), false, false));
        rows.add(new StatRow("Stamina Regen", formatRegen(stats.getStaminaRegen()),
            formatPercent(stats.getStaminaRegenPercent()), stats.getStaminaRegen() > 0, false));
        if (stats.getStaminaRegenStartDelay() > 0) {
            rows.add(new StatRow("Regen Start Delay",
                RPGStyles.formatValue(stats.getStaminaRegenStartDelay(), true), null, true, false));
        }

        // Oxygen
        rows.add(new StatRow(null, null, null, false, false));
        rows.add(new StatRow("OXYGEN", null, null, false, true));
        rows.add(new StatRow("Max Oxygen", RPGStyles.formatFlat(stats.getMaxOxygen()), null, false, false));
        rows.add(new StatRow("Oxygen Regen", formatRegen(stats.getOxygenRegen()), null, stats.getOxygenRegen() > 0, false));

        // Signature Energy (hide entire section if all stats are zero — absent resource pool)
        boolean hasSigEnergy = stats.getMaxSignatureEnergy() != 0
                || stats.getSignatureEnergyRegen() != 0
                || stats.getSignatureEnergyMaxPercent() != 0
                || stats.getSignatureEnergyPerHit() != 0;
        if (hasSigEnergy) {
            rows.add(new StatRow(null, null, null, false, false));
            rows.add(new StatRow("SIGNATURE ENERGY", null, null, false, true));
            rows.add(new StatRow("Max Sig. Energy", RPGStyles.formatFlat(stats.getMaxSignatureEnergy()), null, false, false));
            rows.add(new StatRow("Sig. Energy Regen", formatRegen(stats.getSignatureEnergyRegen()), null, stats.getSignatureEnergyRegen() > 0, false));
            rows.add(new StatRow("Sig. Energy Max %", RPGStyles.formatValue(stats.getSignatureEnergyMaxPercent(), true), null, stats.getSignatureEnergyMaxPercent() != 0, false));
            rows.add(new StatRow("Sig. Energy per Hit", RPGStyles.formatFlat(stats.getSignatureEnergyPerHit()), null, stats.getSignatureEnergyPerHit() > 0, false));
        }

        // Energy Shield
        if (stats.getEnergyShield() > 0) {
            rows.add(new StatRow(null, null, null, false, false));
            rows.add(new StatRow("ENERGY SHIELD", null, null, false, true));
            rows.add(new StatRow("Energy Shield", RPGStyles.formatFlat(stats.getEnergyShield()), null, true, false));
        }

        // Breakdown tooltips
        putBreakdownTooltip(tooltips, "Max Health", breakdown, ComputedStats::getMaxHealth);
        putBreakdownTooltip(tooltips, "Health Regen", breakdown, ComputedStats::getHealthRegen);
        putBreakdownTooltip(tooltips, "Health Recovery", breakdown, ComputedStats::getHealthRecoveryPercent);
        putBreakdownTooltip(tooltips, "Max Mana", breakdown, ComputedStats::getMaxMana);
        putBreakdownTooltip(tooltips, "Mana Regen", breakdown, ComputedStats::getManaRegen);
        putBreakdownTooltip(tooltips, "Mana on Kill", breakdown, ComputedStats::getManaOnKill);
        putBreakdownTooltip(tooltips, "Mana Cost", breakdown, ComputedStats::getManaCostPercent);
        putBreakdownTooltip(tooltips, "Mana Cost Reduction", breakdown, ComputedStats::getManaCostReduction);
        putBreakdownTooltip(tooltips, "Max Stamina", breakdown, ComputedStats::getMaxStamina);
        putBreakdownTooltip(tooltips, "Stamina Regen", breakdown, ComputedStats::getStaminaRegen);
        putBreakdownTooltip(tooltips, "Regen Start Delay", breakdown, ComputedStats::getStaminaRegenStartDelay);
        putBreakdownTooltip(tooltips, "Max Oxygen", breakdown, ComputedStats::getMaxOxygen);
        putBreakdownTooltip(tooltips, "Oxygen Regen", breakdown, ComputedStats::getOxygenRegen);
        putBreakdownTooltip(tooltips, "Max Sig. Energy", breakdown, ComputedStats::getMaxSignatureEnergy);
        putBreakdownTooltip(tooltips, "Sig. Energy Regen", breakdown, ComputedStats::getSignatureEnergyRegen);
        putBreakdownTooltip(tooltips, "Sig. Energy Max %", breakdown, ComputedStats::getSignatureEnergyMaxPercent);
        putBreakdownTooltip(tooltips, "Sig. Energy per Hit", breakdown, ComputedStats::getSignatureEnergyPerHit);
        putBreakdownTooltip(tooltips, "Energy Shield", breakdown, ComputedStats::getEnergyShield);

        return buildStatRows(rows, tooltips);
    }

    /**
     * Builds the Offense tab content with section-level zero-hiding.
     *
     * <p>14 sections organized by category. Sections where all rows are zero
     * are hidden entirely (header + divider + rows). DAMAGE OUTPUT and CRITICAL
     * are always visible.
     */
    @Nonnull
    private String buildOffenseContent(@Nonnull ComputedStats stats, @Nullable StatBreakdownResult breakdown) {
        List<StatRow> rows = new ArrayList<>();
        Map<String, String> tooltips = new LinkedHashMap<>();
        List<StatRow> section;

        // ── DAMAGE OUTPUT (always show) ──
        section = new ArrayList<>();
        addRowIfNonZero(section, new StatRow("Physical Damage", RPGStyles.formatFlat(stats.getPhysicalDamage()), "flat", stats.getPhysicalDamage() > 0, false), stats.getPhysicalDamage());
        addRowIfNonZero(section, new StatRow("Physical Damage %", RPGStyles.formatValue(stats.getPhysicalDamagePercent(), true), null, stats.getPhysicalDamagePercent() != 0, false), stats.getPhysicalDamagePercent());
        addRowIfNonZero(section, new StatRow("Spell Damage", RPGStyles.formatFlat(stats.getSpellDamage()), "flat", stats.getSpellDamage() > 0, false), stats.getSpellDamage());
        addRowIfNonZero(section, new StatRow("Spell Damage %", RPGStyles.formatValue(stats.getSpellDamagePercent(), true), null, stats.getSpellDamagePercent() != 0, false), stats.getSpellDamagePercent());
        addSection(rows, "DAMAGE OUTPUT", section, true);

        // ── CRITICAL (always show — rows always visible) ──
        section = new ArrayList<>();
        section.add(new StatRow("Critical Chance", RPGStyles.formatValue(stats.getCriticalChance(), true), null, stats.getCriticalChance() > 0, false));
        section.add(new StatRow("Critical Multiplier", RPGStyles.formatFlat(stats.getCriticalMultiplier()) + "%", null, false, false));
        addSection(rows, "CRITICAL", section, true);

        // ── ATTACK TYPES (hide if all zero) ──
        section = new ArrayList<>();
        addRowIfNonZero(section, new StatRow("Melee Damage", RPGStyles.formatFlat(stats.getMeleeDamage()), "flat", stats.getMeleeDamage() > 0, false), stats.getMeleeDamage());
        addRowIfNonZero(section, new StatRow("Melee Damage %", RPGStyles.formatValue(stats.getMeleeDamagePercent(), true), null, stats.getMeleeDamagePercent() != 0, false), stats.getMeleeDamagePercent());
        addRowIfNonZero(section, new StatRow("Projectile Damage %", RPGStyles.formatValue(stats.getProjectileDamagePercent(), true), null, stats.getProjectileDamagePercent() != 0, false), stats.getProjectileDamagePercent());
        addRowIfNonZero(section, new StatRow("All Damage %", RPGStyles.formatValue(stats.getAllDamagePercent(), true), null, stats.getAllDamagePercent() != 0, false), stats.getAllDamagePercent());
        addSection(rows, "ATTACK TYPES", section, false);

        // ── ATTACK SPEED (hide if 0) ──
        section = new ArrayList<>();
        addRowIfNonZero(section, new StatRow("Attack Speed", RPGStyles.formatValue(stats.getAttackSpeedPercent(), true), null, stats.getAttackSpeedPercent() != 0, false), stats.getAttackSpeedPercent());
        addSection(rows, "ATTACK SPEED", section, false);

        // ── ELEMENTAL DAMAGE (hide section if all zero; within section, hide zero elements) ──
        section = new ArrayList<>();
        if (stats.getFireDamage() != 0 || stats.getFireDamagePercent() != 0) {
            section.add(new StatRow("Fire Damage", RPGStyles.formatFlat(stats.getFireDamage()), formatPercent(stats.getFireDamagePercent()), true, false, RPGStyles.ELEMENT_FIRE));
        }
        if (stats.getWaterDamage() != 0 || stats.getWaterDamagePercent() != 0) {
            section.add(new StatRow("Water Damage", RPGStyles.formatFlat(stats.getWaterDamage()), formatPercent(stats.getWaterDamagePercent()), true, false, RPGStyles.ELEMENT_WATER));
        }
        if (stats.getLightningDamage() != 0 || stats.getLightningDamagePercent() != 0) {
            section.add(new StatRow("Lightning Damage", RPGStyles.formatFlat(stats.getLightningDamage()), formatPercent(stats.getLightningDamagePercent()), true, false, RPGStyles.ELEMENT_LIGHTNING));
        }
        if (stats.getEarthDamage() != 0 || stats.getEarthDamagePercent() != 0) {
            section.add(new StatRow("Earth Damage", RPGStyles.formatFlat(stats.getEarthDamage()), formatPercent(stats.getEarthDamagePercent()), true, false, RPGStyles.ELEMENT_EARTH));
        }
        if (stats.getWindDamage() != 0 || stats.getWindDamagePercent() != 0) {
            section.add(new StatRow("Wind Damage", RPGStyles.formatFlat(stats.getWindDamage()), formatPercent(stats.getWindDamagePercent()), true, false, RPGStyles.ELEMENT_WIND));
        }
        if (stats.getVoidDamage() != 0 || stats.getVoidDamagePercent() != 0) {
            section.add(new StatRow("Void Damage", RPGStyles.formatFlat(stats.getVoidDamage()), formatPercent(stats.getVoidDamagePercent()), true, false, RPGStyles.ELEMENT_VOID));
        }
        addSection(rows, "ELEMENTAL DAMAGE", section, false);

        // ── DAMAGE MODIFIERS (hide if all zero) ──
        section = new ArrayList<>();
        addRowIfNonZero(section, new StatRow("Increased Damage", RPGStyles.formatValue(stats.getDamagePercent(), true), null, stats.getDamagePercent() != 0, false), stats.getDamagePercent());
        addRowIfNonZero(section, new StatRow("More Damage", RPGStyles.formatValue(stats.getDamageMultiplier(), true), null, stats.getDamageMultiplier() != 0, false), stats.getDamageMultiplier());
        addRowIfNonZero(section, new StatRow("All Elemental Dmg", RPGStyles.formatValue(stats.getAllElementalDamagePercent(), true), null, stats.getAllElementalDamagePercent() != 0, false), stats.getAllElementalDamagePercent());
        addRowIfNonZero(section, new StatRow("Charged Attack Damage", RPGStyles.formatFlat(stats.getChargedAttackDamage()), "flat", stats.getChargedAttackDamage() > 0, false), stats.getChargedAttackDamage());
        addRowIfNonZero(section, new StatRow("Charged Attack Damage %", RPGStyles.formatValue(stats.getChargedAttackDamagePercent(), true), null, stats.getChargedAttackDamagePercent() != 0, false), stats.getChargedAttackDamagePercent());
        addRowIfNonZero(section, new StatRow("Non-Crit Damage", RPGStyles.formatValue(stats.getNonCritDamagePercent(), true), null, stats.getNonCritDamagePercent() != 0, false), stats.getNonCritDamagePercent());
        addSection(rows, "DAMAGE MODIFIERS", section, false);

        // ── CONDITIONAL DAMAGE (hide if all zero) ──
        section = new ArrayList<>();
        addRowIfNonZero(section, new StatRow("Low-Life Damage", RPGStyles.formatValue(stats.getDamageAtLowLife(), true), null, stats.getDamageAtLowLife() > 0, false), stats.getDamageAtLowLife());
        addRowIfNonZero(section, new StatRow("Execute Damage", RPGStyles.formatValue(stats.getExecuteDamagePercent(), true), null, stats.getExecuteDamagePercent() > 0, false), stats.getExecuteDamagePercent());
        addRowIfNonZero(section, new StatRow("Damage vs Frozen", RPGStyles.formatValue(stats.getDamageVsFrozenPercent(), true), null, stats.getDamageVsFrozenPercent() != 0, false), stats.getDamageVsFrozenPercent());
        addRowIfNonZero(section, new StatRow("Damage vs Shocked", RPGStyles.formatValue(stats.getDamageVsShockedPercent(), true), null, stats.getDamageVsShockedPercent() != 0, false), stats.getDamageVsShockedPercent());
        addRowIfNonZero(section, new StatRow("Damage from Mana", RPGStyles.formatValue(stats.getDamageFromManaPercent(), true), null, stats.getDamageFromManaPercent() != 0, false), stats.getDamageFromManaPercent());
        addSection(rows, "CONDITIONAL DAMAGE", section, false);

        // ── TRUE DAMAGE (hide if all zero) ──
        section = new ArrayList<>();
        addRowIfNonZero(section, new StatRow("True Damage", RPGStyles.formatFlat(stats.getTrueDamage()), "flat", stats.getTrueDamage() > 0, false), stats.getTrueDamage());
        addRowIfNonZero(section, new StatRow("True Damage %", RPGStyles.formatValue(stats.getTrueDamagePercent(), true), null, stats.getTrueDamagePercent() != 0, false), stats.getTrueDamagePercent());
        addRowIfNonZero(section, new StatRow("% Hit as True Damage", RPGStyles.formatValue(stats.getPercentHitAsTrueDamage(), true), null, stats.getPercentHitAsTrueDamage() != 0, false), stats.getPercentHitAsTrueDamage());
        addSection(rows, "TRUE DAMAGE", section, false);

        // ── SUSTAIN (hide if all zero) ──
        section = new ArrayList<>();
        addRowIfNonZero(section, new StatRow("Life Steal", RPGStyles.formatValue(stats.getLifeSteal(), true), null, stats.getLifeSteal() > 0, false), stats.getLifeSteal());
        addRowIfNonZero(section, new StatRow("Life Leech", RPGStyles.formatFlat(stats.getLifeLeech()), "flat", stats.getLifeLeech() > 0, false), stats.getLifeLeech());
        addRowIfNonZero(section, new StatRow("Mana Leech", RPGStyles.formatValue(stats.getManaLeech(), true), null, stats.getManaLeech() > 0, false), stats.getManaLeech());
        addRowIfNonZero(section, new StatRow("Mana Steal", RPGStyles.formatValue(stats.getManaSteal(), true), null, stats.getManaSteal() > 0, false), stats.getManaSteal());
        addSection(rows, "SUSTAIN", section, false);

        // ── PENETRATION (hide if all zero — merged physical + elemental) ──
        section = new ArrayList<>();
        addRowIfNonZero(section, new StatRow("Armor Penetration", RPGStyles.formatFlat(stats.getArmorPenetration()), null, stats.getArmorPenetration() > 0, false), stats.getArmorPenetration());
        addRowIfNonZero(section, new StatRow("Spell Penetration", RPGStyles.formatFlat(stats.getSpellPenetration()), null, stats.getSpellPenetration() > 0, false), stats.getSpellPenetration());
        addRowIfNonZero(section, new StatRow("Fire Penetration", RPGStyles.formatFlat(stats.getFirePenetration()), null, stats.getFirePenetration() > 0, false, RPGStyles.ELEMENT_FIRE), stats.getFirePenetration());
        addRowIfNonZero(section, new StatRow("Water Penetration", RPGStyles.formatFlat(stats.getWaterPenetration()), null, stats.getWaterPenetration() > 0, false, RPGStyles.ELEMENT_WATER), stats.getWaterPenetration());
        addRowIfNonZero(section, new StatRow("Lightning Penetration", RPGStyles.formatFlat(stats.getLightningPenetration()), null, stats.getLightningPenetration() > 0, false, RPGStyles.ELEMENT_LIGHTNING), stats.getLightningPenetration());
        addRowIfNonZero(section, new StatRow("Earth Penetration", RPGStyles.formatFlat(stats.getEarthPenetration()), null, stats.getEarthPenetration() > 0, false, RPGStyles.ELEMENT_EARTH), stats.getEarthPenetration());
        addRowIfNonZero(section, new StatRow("Wind Penetration", RPGStyles.formatFlat(stats.getWindPenetration()), null, stats.getWindPenetration() > 0, false, RPGStyles.ELEMENT_WIND), stats.getWindPenetration());
        addRowIfNonZero(section, new StatRow("Void Penetration", RPGStyles.formatFlat(stats.getVoidPenetration()), null, stats.getVoidPenetration() > 0, false, RPGStyles.ELEMENT_VOID), stats.getVoidPenetration());
        addSection(rows, "PENETRATION", section, false);

        // ── ACCURACY (hide if all zero) ──
        section = new ArrayList<>();
        addRowIfNonZero(section, new StatRow("Accuracy", RPGStyles.formatFlat(stats.getAccuracy()), "flat", stats.getAccuracy() > 0, false), stats.getAccuracy());
        addRowIfNonZero(section, new StatRow("Accuracy %", RPGStyles.formatValue(stats.getAccuracyPercent(), true), null, stats.getAccuracyPercent() != 0, false), stats.getAccuracyPercent());
        addSection(rows, "ACCURACY", section, false);

        // ── STATUS EFFECTS (hide if all zero) ──
        section = new ArrayList<>();
        addRowIfNonZero(section, new StatRow("Ignite Chance", RPGStyles.formatValue(stats.getIgniteChance(), true), null, stats.getIgniteChance() > 0, false), stats.getIgniteChance());
        addRowIfNonZero(section, new StatRow("Freeze Chance", RPGStyles.formatValue(stats.getFreezeChance(), true), null, stats.getFreezeChance() > 0, false), stats.getFreezeChance());
        addRowIfNonZero(section, new StatRow("Shock Chance", RPGStyles.formatValue(stats.getShockChance(), true), null, stats.getShockChance() > 0, false), stats.getShockChance());
        addRowIfNonZero(section, new StatRow("Status Effect Chance", RPGStyles.formatValue(stats.getStatusEffectChance(), true), null, stats.getStatusEffectChance() > 0, false), stats.getStatusEffectChance());
        addRowIfNonZero(section, new StatRow("Status Effect Duration", RPGStyles.formatValue(stats.getStatusEffectDuration(), true), null, stats.getStatusEffectDuration() != 0, false), stats.getStatusEffectDuration());
        addSection(rows, "STATUS EFFECTS", section, false);

        // ── AILMENT DAMAGE (hide if all zero) ──
        section = new ArrayList<>();
        if (stats.getBurnDamage() != 0 || stats.getBurnDamagePercent() != 0) {
            section.add(new StatRow("Burn Damage", RPGStyles.formatFlat(stats.getBurnDamage()), formatPercent(stats.getBurnDamagePercent()), true, false));
        }
        addRowIfNonZero(section, new StatRow("Burn Duration", RPGStyles.formatValue(stats.getBurnDurationPercent(), true), null, stats.getBurnDurationPercent() != 0, false), stats.getBurnDurationPercent());
        if (stats.getFreezeDamage() != 0 || stats.getFrostDamagePercent() != 0) {
            section.add(new StatRow("Freeze Damage", RPGStyles.formatFlat(stats.getFreezeDamage()), formatPercent(stats.getFrostDamagePercent()), true, false));
        }
        if (stats.getShockDamage() != 0 || stats.getShockDamagePercent() != 0) {
            section.add(new StatRow("Shock Damage", RPGStyles.formatFlat(stats.getShockDamage()), formatPercent(stats.getShockDamagePercent()), true, false));
        }
        addRowIfNonZero(section, new StatRow("Poison Damage", RPGStyles.formatFlat(stats.getPoisonDamage()), null, stats.getPoisonDamage() > 0, false), stats.getPoisonDamage());
        addRowIfNonZero(section, new StatRow("DoT Damage", RPGStyles.formatValue(stats.getDotDamagePercent(), true), null, stats.getDotDamagePercent() != 0, false), stats.getDotDamagePercent());
        addSection(rows, "AILMENT DAMAGE", section, false);

        // ── ELEMENTAL CONVERSION (hide if all zero) ──
        section = new ArrayList<>();
        addRowIfNonZero(section, new StatRow("Fire Conversion", RPGStyles.formatValue(stats.getFireConversion(), true), null, stats.getFireConversion() != 0, false, RPGStyles.ELEMENT_FIRE), stats.getFireConversion());
        addRowIfNonZero(section, new StatRow("Water Conversion", RPGStyles.formatValue(stats.getWaterConversion(), true), null, stats.getWaterConversion() != 0, false, RPGStyles.ELEMENT_WATER), stats.getWaterConversion());
        addRowIfNonZero(section, new StatRow("Lightning Conversion", RPGStyles.formatValue(stats.getLightningConversion(), true), null, stats.getLightningConversion() != 0, false, RPGStyles.ELEMENT_LIGHTNING), stats.getLightningConversion());
        addRowIfNonZero(section, new StatRow("Earth Conversion", RPGStyles.formatValue(stats.getEarthConversion(), true), null, stats.getEarthConversion() != 0, false, RPGStyles.ELEMENT_EARTH), stats.getEarthConversion());
        addRowIfNonZero(section, new StatRow("Wind Conversion", RPGStyles.formatValue(stats.getWindConversion(), true), null, stats.getWindConversion() != 0, false, RPGStyles.ELEMENT_WIND), stats.getWindConversion());
        addRowIfNonZero(section, new StatRow("Void Conversion", RPGStyles.formatValue(stats.getVoidConversion(), true), null, stats.getVoidConversion() != 0, false, RPGStyles.ELEMENT_VOID), stats.getVoidConversion());
        addSection(rows, "ELEMENTAL CONVERSION", section, false);

        // ── PROJECTILE (hide if all zero) ──
        section = new ArrayList<>();
        addRowIfNonZero(section, new StatRow("Projectile Speed", RPGStyles.formatValue(stats.getProjectileSpeedPercent(), true), null, stats.getProjectileSpeedPercent() != 0, false), stats.getProjectileSpeedPercent());
        addRowIfNonZero(section, new StatRow("Projectile Gravity", RPGStyles.formatValue(stats.getProjectileGravityPercent(), true), null, stats.getProjectileGravityPercent() != 0, false), stats.getProjectileGravityPercent());
        addSection(rows, "PROJECTILE", section, false);

        // ── Breakdown tooltips (all existing — hidden rows naturally skip rendering) ──
        putBreakdownTooltip(tooltips, "Physical Damage", breakdown, ComputedStats::getPhysicalDamage);
        putBreakdownTooltip(tooltips, "Physical Damage %", breakdown, ComputedStats::getPhysicalDamagePercent);
        putBreakdownTooltip(tooltips, "Spell Damage", breakdown, ComputedStats::getSpellDamage);
        putBreakdownTooltip(tooltips, "Spell Damage %", breakdown, ComputedStats::getSpellDamagePercent);
        putBreakdownTooltip(tooltips, "Critical Chance", breakdown, ComputedStats::getCriticalChance);
        putBreakdownTooltip(tooltips, "Critical Multiplier", breakdown, ComputedStats::getCriticalMultiplier);
        putBreakdownTooltip(tooltips, "Melee Damage", breakdown, ComputedStats::getMeleeDamage);
        putBreakdownTooltip(tooltips, "Melee Damage %", breakdown, ComputedStats::getMeleeDamagePercent);
        putBreakdownTooltip(tooltips, "Projectile Damage %", breakdown, ComputedStats::getProjectileDamagePercent);
        putBreakdownTooltip(tooltips, "All Damage %", breakdown, ComputedStats::getAllDamagePercent);
        putBreakdownTooltip(tooltips, "Attack Speed", breakdown, ComputedStats::getAttackSpeedPercent);
        putBreakdownTooltip(tooltips, "Life Steal", breakdown, ComputedStats::getLifeSteal);
        putBreakdownTooltip(tooltips, "Life Leech", breakdown, ComputedStats::getLifeLeech);
        putBreakdownTooltip(tooltips, "Mana Leech", breakdown, ComputedStats::getManaLeech);
        putBreakdownTooltip(tooltips, "Mana Steal", breakdown, ComputedStats::getManaSteal);
        putBreakdownTooltip(tooltips, "Armor Penetration", breakdown, ComputedStats::getArmorPenetration);
        putBreakdownTooltip(tooltips, "Spell Penetration", breakdown, ComputedStats::getSpellPenetration);
        putBreakdownTooltip(tooltips, "Fire Penetration", breakdown, ComputedStats::getFirePenetration);
        putBreakdownTooltip(tooltips, "Water Penetration", breakdown, ComputedStats::getWaterPenetration);
        putBreakdownTooltip(tooltips, "Lightning Penetration", breakdown, ComputedStats::getLightningPenetration);
        putBreakdownTooltip(tooltips, "Earth Penetration", breakdown, ComputedStats::getEarthPenetration);
        putBreakdownTooltip(tooltips, "Wind Penetration", breakdown, ComputedStats::getWindPenetration);
        putBreakdownTooltip(tooltips, "Void Penetration", breakdown, ComputedStats::getVoidPenetration);
        putBreakdownTooltip(tooltips, "Accuracy", breakdown, ComputedStats::getAccuracy);
        putBreakdownTooltip(tooltips, "Accuracy %", breakdown, ComputedStats::getAccuracyPercent);
        putBreakdownTooltip(tooltips, "Fire Damage", breakdown, ComputedStats::getFireDamage);
        putBreakdownTooltip(tooltips, "Water Damage", breakdown, ComputedStats::getWaterDamage);
        putBreakdownTooltip(tooltips, "Lightning Damage", breakdown, ComputedStats::getLightningDamage);
        putBreakdownTooltip(tooltips, "Earth Damage", breakdown, ComputedStats::getEarthDamage);
        putBreakdownTooltip(tooltips, "Wind Damage", breakdown, ComputedStats::getWindDamage);
        putBreakdownTooltip(tooltips, "Void Damage", breakdown, ComputedStats::getVoidDamage);
        putBreakdownTooltip(tooltips, "Ignite Chance", breakdown, ComputedStats::getIgniteChance);
        putBreakdownTooltip(tooltips, "Freeze Chance", breakdown, ComputedStats::getFreezeChance);
        putBreakdownTooltip(tooltips, "Shock Chance", breakdown, ComputedStats::getShockChance);
        putBreakdownTooltip(tooltips, "Status Effect Chance", breakdown, ComputedStats::getStatusEffectChance);
        putBreakdownTooltip(tooltips, "Status Effect Duration", breakdown, ComputedStats::getStatusEffectDuration);
        putBreakdownTooltip(tooltips, "Increased Damage", breakdown, ComputedStats::getDamagePercent);
        putBreakdownTooltip(tooltips, "More Damage", breakdown, ComputedStats::getDamageMultiplier);
        putBreakdownTooltip(tooltips, "All Elemental Dmg", breakdown, ComputedStats::getAllElementalDamagePercent);
        putBreakdownTooltip(tooltips, "Charged Attack Damage", breakdown, ComputedStats::getChargedAttackDamage);
        putBreakdownTooltip(tooltips, "Charged Attack Damage %", breakdown, ComputedStats::getChargedAttackDamagePercent);
        putBreakdownTooltip(tooltips, "Non-Crit Damage", breakdown, ComputedStats::getNonCritDamagePercent);
        putBreakdownTooltip(tooltips, "Low-Life Damage", breakdown, ComputedStats::getDamageAtLowLife);
        putBreakdownTooltip(tooltips, "Execute Damage", breakdown, ComputedStats::getExecuteDamagePercent);
        putBreakdownTooltip(tooltips, "Damage vs Frozen", breakdown, ComputedStats::getDamageVsFrozenPercent);
        putBreakdownTooltip(tooltips, "Damage vs Shocked", breakdown, ComputedStats::getDamageVsShockedPercent);
        putBreakdownTooltip(tooltips, "Damage from Mana", breakdown, ComputedStats::getDamageFromManaPercent);
        putBreakdownTooltip(tooltips, "True Damage", breakdown, ComputedStats::getTrueDamage);
        putBreakdownTooltip(tooltips, "True Damage %", breakdown, ComputedStats::getTrueDamagePercent);
        putBreakdownTooltip(tooltips, "% Hit as True Damage", breakdown, ComputedStats::getPercentHitAsTrueDamage);
        putBreakdownTooltip(tooltips, "Burn Damage", breakdown, ComputedStats::getBurnDamage);
        putBreakdownTooltip(tooltips, "Burn Duration", breakdown, ComputedStats::getBurnDurationPercent);
        putBreakdownTooltip(tooltips, "Freeze Damage", breakdown, ComputedStats::getFreezeDamage);
        putBreakdownTooltip(tooltips, "Shock Damage", breakdown, ComputedStats::getShockDamage);
        putBreakdownTooltip(tooltips, "Poison Damage", breakdown, ComputedStats::getPoisonDamage);
        putBreakdownTooltip(tooltips, "DoT Damage", breakdown, ComputedStats::getDotDamagePercent);
        putBreakdownTooltip(tooltips, "Fire Conversion", breakdown, ComputedStats::getFireConversion);
        putBreakdownTooltip(tooltips, "Water Conversion", breakdown, ComputedStats::getWaterConversion);
        putBreakdownTooltip(tooltips, "Lightning Conversion", breakdown, ComputedStats::getLightningConversion);
        putBreakdownTooltip(tooltips, "Earth Conversion", breakdown, ComputedStats::getEarthConversion);
        putBreakdownTooltip(tooltips, "Wind Conversion", breakdown, ComputedStats::getWindConversion);
        putBreakdownTooltip(tooltips, "Void Conversion", breakdown, ComputedStats::getVoidConversion);
        putBreakdownTooltip(tooltips, "Projectile Speed", breakdown, ComputedStats::getProjectileSpeedPercent);
        putBreakdownTooltip(tooltips, "Projectile Gravity", breakdown, ComputedStats::getProjectileGravityPercent);

        return buildStatRows(rows, tooltips);
    }

    /**
     * Builds the Defense tab content with SURVIVABILITY headline and section-level zero-hiding.
     *
     * <p>8 sections. SURVIVABILITY and ELEMENTAL RESISTANCES are always visible.
     * Other sections hide entirely when all their rows are zero.
     */
    @Nonnull
    private String buildDefenseContent(
            @Nonnull ComputedStats stats,
            @Nullable StatBreakdownResult breakdown,
            int level,
            @Nonnull BuildSummary buildSummary
    ) {
        List<StatRow> rows = new ArrayList<>();
        Map<String, String> tooltips = new LinkedHashMap<>();
        List<StatRow> section;

        // ── SURVIVABILITY (always show — hero section) ──
        section = new ArrayList<>();
        section.add(new StatRow("Effective HP", String.format("%,d", (int) buildSummary.effectiveHP()), null, true, false));
        tooltips.put("Effective HP", buildEHPTooltip(buildSummary));
        section.add(new StatRow("Max Health", RPGStyles.formatFlat(stats.getMaxHealth()), null, false, false));
        if (stats.getEnergyShield() > 0) {
            section.add(new StatRow("Energy Shield", RPGStyles.formatFlat(stats.getEnergyShield()), null, true, false));
        }
        section.add(new StatRow("Armor", RPGStyles.formatFlat(stats.getArmor()), formatArmorReduction(stats.getArmor()), stats.getArmor() > 0, false));
        addSection(rows, "SURVIVABILITY", section, true);

        // ── AVOIDANCE (hide if all zero) ──
        section = new ArrayList<>();
        addRowIfNonZero(section, new StatRow("Evasion", RPGStyles.formatFlat(stats.getEvasion()), formatEvasionChance(stats.getEvasion(), level), true, false), stats.getEvasion());
        addRowIfNonZero(section, new StatRow("Dodge Chance", RPGStyles.formatValue(stats.getDodgeChance(), true), null, stats.getDodgeChance() > 0, false), stats.getDodgeChance());
        // Passive block removed — block_chance feeds perfect block (shown in Block & Shield section)
        addRowIfNonZero(section, new StatRow("Parry Chance", RPGStyles.formatValue(stats.getParryChance(), true), null, stats.getParryChance() > 0, false), stats.getParryChance());
        addSection(rows, "AVOIDANCE", section, false);

        // ── BLOCK & SHIELD (hide if all zero) ──
        section = new ArrayList<>();
        addRowIfNonZero(section, new StatRow("Block Chance", RPGStyles.formatValue(stats.getBlockChance(), true), null, stats.getBlockChance() > 0, false), stats.getBlockChance());
        addRowIfNonZero(section, new StatRow("Block Dmg Reduction", RPGStyles.formatValue(stats.getBlockDamageReduction(), true), null, stats.getBlockDamageReduction() > 0, false), stats.getBlockDamageReduction());
        addRowIfNonZero(section, new StatRow("Block Heal", RPGStyles.formatValue(stats.getBlockHealPercent(), true), null, stats.getBlockHealPercent() > 0, false), stats.getBlockHealPercent());
        addRowIfNonZero(section, new StatRow("Block Recovery", RPGStyles.formatValue(stats.getBlockRecoveryPercent(), true), null, stats.getBlockRecoveryPercent() > 0, false), stats.getBlockRecoveryPercent());
        addRowIfNonZero(section, new StatRow("Shield Effectiveness", RPGStyles.formatValue(stats.getShieldEffectivenessPercent(), true), null, stats.getShieldEffectivenessPercent() != 0, false), stats.getShieldEffectivenessPercent());
        addRowIfNonZero(section, new StatRow("Stamina Drain Reduce", RPGStyles.formatFlat(stats.getStaminaDrainReduction()), "flat", stats.getStaminaDrainReduction() > 0, false), stats.getStaminaDrainReduction());
        addSection(rows, "BLOCK & SHIELD", section, false);

        // ── ELEMENTAL RESISTANCES (always show — 0% resistance is meaningful) ──
        section = new ArrayList<>();
        section.add(new StatRow("Fire Resistance", RPGStyles.formatValue(stats.getFireResistance(), true), null, stats.getFireResistance() != 0, false, RPGStyles.ELEMENT_FIRE));
        section.add(new StatRow("Water Resistance", RPGStyles.formatValue(stats.getWaterResistance(), true), null, stats.getWaterResistance() != 0, false, RPGStyles.ELEMENT_WATER));
        section.add(new StatRow("Lightning Resistance", RPGStyles.formatValue(stats.getLightningResistance(), true), null, stats.getLightningResistance() != 0, false, RPGStyles.ELEMENT_LIGHTNING));
        section.add(new StatRow("Earth Resistance", RPGStyles.formatValue(stats.getEarthResistance(), true), null, stats.getEarthResistance() != 0, false, RPGStyles.ELEMENT_EARTH));
        section.add(new StatRow("Wind Resistance", RPGStyles.formatValue(stats.getWindResistance(), true), null, stats.getWindResistance() != 0, false, RPGStyles.ELEMENT_WIND));
        section.add(new StatRow("Void Resistance", RPGStyles.formatValue(stats.getVoidResistance(), true), null, stats.getVoidResistance() != 0, false, RPGStyles.ELEMENT_VOID));
        addSection(rows, "ELEMENTAL RESISTANCES", section, true);

        // ── DAMAGE TAKEN (hide if all zero) ──
        section = new ArrayList<>();
        addRowIfNonZero(section, new StatRow("Damage Taken", RPGStyles.formatValue(stats.getDamageTakenPercent(), true), null, stats.getDamageTakenPercent() != 0, false), stats.getDamageTakenPercent());
        addRowIfNonZero(section, new StatRow("Damage When Hit", RPGStyles.formatValue(stats.getDamageWhenHitPercent(), true), null, stats.getDamageWhenHitPercent() != 0, false), stats.getDamageWhenHitPercent());
        addRowIfNonZero(section, new StatRow("Critical Reduction", RPGStyles.formatValue(stats.getCriticalReduction(), true), null, stats.getCriticalReduction() > 0, false), stats.getCriticalReduction());
        addSection(rows, "DAMAGE TAKEN", section, false);

        // ── THORNS & REFLECT (hide if all zero) ──
        section = new ArrayList<>();
        addRowIfNonZero(section, new StatRow("Thorns Damage", RPGStyles.formatFlat(stats.getThornsDamage()), "flat", stats.getThornsDamage() > 0, false), stats.getThornsDamage());
        addRowIfNonZero(section, new StatRow("Thorns Damage %", RPGStyles.formatValue(stats.getThornsDamagePercent(), true), null, stats.getThornsDamagePercent() != 0, false), stats.getThornsDamagePercent());
        addRowIfNonZero(section, new StatRow("Reflect Damage", RPGStyles.formatValue(stats.getReflectDamagePercent(), true), null, stats.getReflectDamagePercent() > 0, false), stats.getReflectDamagePercent());
        addSection(rows, "THORNS & REFLECT", section, false);

        // ── SPECIAL DEFENSES (hide if all zero) ──
        section = new ArrayList<>();
        addRowIfNonZero(section, new StatRow("Crit Nullify Chance", RPGStyles.formatValue(stats.getCritNullifyChance(), true), null, stats.getCritNullifyChance() > 0, false), stats.getCritNullifyChance());
        addRowIfNonZero(section, new StatRow("Knockback Resistance", RPGStyles.formatValue(stats.getKnockbackResistance(), true), null, stats.getKnockbackResistance() > 0, false), stats.getKnockbackResistance());
        addRowIfNonZero(section, new StatRow("Fall Damage Reduction", RPGStyles.formatValue(stats.getFallDamageReduction(), true), null, stats.getFallDamageReduction() > 0, false), stats.getFallDamageReduction());
        addRowIfNonZero(section, new StatRow("Mana as Dmg Buffer", RPGStyles.formatValue(stats.getManaAsDamageBuffer(), true), null, stats.getManaAsDamageBuffer() > 0, false), stats.getManaAsDamageBuffer());
        addSection(rows, "SPECIAL DEFENSES", section, false);

        // ── STATUS THRESHOLDS (hide if all zero) ──
        section = new ArrayList<>();
        addRowIfNonZero(section, new StatRow("Burn Threshold", RPGStyles.formatFlat(stats.getBurnThreshold()), null, stats.getBurnThreshold() > 0, false), stats.getBurnThreshold());
        addRowIfNonZero(section, new StatRow("Freeze Threshold", RPGStyles.formatFlat(stats.getFreezeThreshold()), null, stats.getFreezeThreshold() > 0, false), stats.getFreezeThreshold());
        addRowIfNonZero(section, new StatRow("Shock Threshold", RPGStyles.formatFlat(stats.getShockThreshold()), null, stats.getShockThreshold() > 0, false), stats.getShockThreshold());
        addSection(rows, "STATUS THRESHOLDS", section, false);

        // ── Breakdown tooltips ──
        putBreakdownTooltip(tooltips, "Max Health", breakdown, ComputedStats::getMaxHealth);
        putBreakdownTooltip(tooltips, "Energy Shield", breakdown, ComputedStats::getEnergyShield);
        putBreakdownTooltip(tooltips, "Armor", breakdown, ComputedStats::getArmor);
        putBreakdownTooltip(tooltips, "Evasion", breakdown, ComputedStats::getEvasion);
        putBreakdownTooltip(tooltips, "Dodge Chance", breakdown, ComputedStats::getDodgeChance);
        putBreakdownTooltip(tooltips, "Parry Chance", breakdown, ComputedStats::getParryChance);
        putBreakdownTooltip(tooltips, "Perfect Block Chance", breakdown, ComputedStats::getBlockChance);
        putBreakdownTooltip(tooltips, "Block Dmg Reduction", breakdown, ComputedStats::getBlockDamageReduction);
        putBreakdownTooltip(tooltips, "Block Heal", breakdown, ComputedStats::getBlockHealPercent);
        putBreakdownTooltip(tooltips, "Block Recovery", breakdown, ComputedStats::getBlockRecoveryPercent);
        putBreakdownTooltip(tooltips, "Shield Effectiveness", breakdown, ComputedStats::getShieldEffectivenessPercent);
        putBreakdownTooltip(tooltips, "Stamina Drain Reduce", breakdown, ComputedStats::getStaminaDrainReduction);
        putBreakdownTooltip(tooltips, "Fire Resistance", breakdown, ComputedStats::getFireResistance);
        putBreakdownTooltip(tooltips, "Water Resistance", breakdown, ComputedStats::getWaterResistance);
        putBreakdownTooltip(tooltips, "Lightning Resistance", breakdown, ComputedStats::getLightningResistance);
        putBreakdownTooltip(tooltips, "Earth Resistance", breakdown, ComputedStats::getEarthResistance);
        putBreakdownTooltip(tooltips, "Wind Resistance", breakdown, ComputedStats::getWindResistance);
        putBreakdownTooltip(tooltips, "Void Resistance", breakdown, ComputedStats::getVoidResistance);
        putBreakdownTooltip(tooltips, "Damage Taken", breakdown, ComputedStats::getDamageTakenPercent);
        putBreakdownTooltip(tooltips, "Damage When Hit", breakdown, ComputedStats::getDamageWhenHitPercent);
        putBreakdownTooltip(tooltips, "Critical Reduction", breakdown, ComputedStats::getCriticalReduction);
        putBreakdownTooltip(tooltips, "Thorns Damage", breakdown, ComputedStats::getThornsDamage);
        putBreakdownTooltip(tooltips, "Thorns Damage %", breakdown, ComputedStats::getThornsDamagePercent);
        putBreakdownTooltip(tooltips, "Reflect Damage", breakdown, ComputedStats::getReflectDamagePercent);
        putBreakdownTooltip(tooltips, "Crit Nullify Chance", breakdown, ComputedStats::getCritNullifyChance);
        putBreakdownTooltip(tooltips, "Knockback Resistance", breakdown, ComputedStats::getKnockbackResistance);
        putBreakdownTooltip(tooltips, "Fall Damage Reduction", breakdown, ComputedStats::getFallDamageReduction);
        putBreakdownTooltip(tooltips, "Mana as Dmg Buffer", breakdown, ComputedStats::getManaAsDamageBuffer);
        putBreakdownTooltip(tooltips, "Burn Threshold", breakdown, ComputedStats::getBurnThreshold);
        putBreakdownTooltip(tooltips, "Freeze Threshold", breakdown, ComputedStats::getFreezeThreshold);
        putBreakdownTooltip(tooltips, "Shock Threshold", breakdown, ComputedStats::getShockThreshold);

        return buildStatRows(rows, tooltips);
    }

    /**
     * Builds the Movement tab content.
     */
    @Nonnull
    private String buildMovementContent(@Nonnull ComputedStats stats, @Nullable StatBreakdownResult breakdown) {
        List<StatRow> rows = new ArrayList<>();
        Map<String, String> tooltips = new LinkedHashMap<>();

        rows.add(new StatRow("MOVEMENT SPEED", null, null, false, true));
        rows.add(new StatRow("Movement Speed", RPGStyles.formatValue(stats.getMovementSpeedPercent(), true), null, stats.getMovementSpeedPercent() != 0, false));
        rows.add(new StatRow("Walk Speed", RPGStyles.formatValue(stats.getWalkSpeedPercent(), true), null, stats.getWalkSpeedPercent() != 0, false));
        rows.add(new StatRow("Run Speed", RPGStyles.formatValue(stats.getRunSpeedPercent(), true), null, stats.getRunSpeedPercent() != 0, false));
        rows.add(new StatRow("Sprint Speed", RPGStyles.formatValue(stats.getSprintSpeedBonus(), true), null, stats.getSprintSpeedBonus() != 0, false));
        rows.add(new StatRow("Crouch Speed", RPGStyles.formatValue(stats.getCrouchSpeedPercent(), true), null, stats.getCrouchSpeedPercent() != 0, false));

        rows.add(new StatRow(null, null, null, false, false));
        rows.add(new StatRow("VERTICAL MOVEMENT", null, null, false, true));
        rows.add(new StatRow("Jump Force", RPGStyles.formatValue(stats.getJumpForceBonus(), true), null, stats.getJumpForceBonus() != 0, false));
        rows.add(new StatRow("Climb Speed", RPGStyles.formatValue(stats.getClimbSpeedBonus(), true), null, stats.getClimbSpeedBonus() != 0, false));

        // Breakdown tooltips
        putBreakdownTooltip(tooltips, "Movement Speed", breakdown, ComputedStats::getMovementSpeedPercent);
        putBreakdownTooltip(tooltips, "Walk Speed", breakdown, ComputedStats::getWalkSpeedPercent);
        putBreakdownTooltip(tooltips, "Run Speed", breakdown, ComputedStats::getRunSpeedPercent);
        putBreakdownTooltip(tooltips, "Sprint Speed", breakdown, ComputedStats::getSprintSpeedBonus);
        putBreakdownTooltip(tooltips, "Crouch Speed", breakdown, ComputedStats::getCrouchSpeedPercent);
        putBreakdownTooltip(tooltips, "Jump Force", breakdown, ComputedStats::getJumpForceBonus);
        putBreakdownTooltip(tooltips, "Climb Speed", breakdown, ComputedStats::getClimbSpeedBonus);

        return buildStatRows(rows, tooltips);
    }

    // ═══════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Builds HTML for a list of stat rows.
     */
    @Nonnull
    private String buildStatRows(@Nonnull List<StatRow> rows) {
        return buildStatRows(rows, Map.of());
    }

    @Nonnull
    private String buildStatRows(@Nonnull List<StatRow> rows, @Nonnull Map<String, String> tooltips) {
        StringBuilder sb = new StringBuilder();
        sb.append("        <div style=\"layout-mode: Top; anchor-horizontal: 0;\" data-hyui-style=\"Padding: (Horizontal: 16; Vertical: 8)\">\n");

        for (StatRow row : rows) {
            if (row.name == null) {
                // Spacer row - larger for better section separation
                sb.append("          <div style=\"anchor-height: 16;\"></div>\n");
            } else if (row.isHeader) {
                // Section header - clean text with underline, no background box
                sb.append("          <div style=\"anchor-horizontal: 0; anchor-height: 26;\" data-hyui-style=\"Padding: (Left: 4)\">\n");
                sb.append("            <p style=\"font-size: 14; color: ").append(RPGStyles.TITLE_GOLD).append(";\">").append(row.name).append("</p>\n");
                sb.append("          </div>\n");
                // Subtle underline
                sb.append("          <div style=\"anchor-height: 1; anchor-horizontal: 0; background-color: #3a3a5a;\"></div>\n");
                sb.append("          <div style=\"anchor-height: 6;\"></div>\n");
            } else {
                // Regular stat row - taller with better spacing
                // Layout order: Name (flex) → Detail (if any) → Value (always far right)
                String valueColor = row.isHighlighted ? RPGStyles.POSITIVE : RPGStyles.STAT_VALUE;
                String labelColor = row.nameColor != null ? row.nameColor : RPGStyles.TEXT_SECONDARY;
                // Tooltip as <tooltip><span> child elements (colored, one span per line)
                String tooltip = tooltips.get(row.name);
                String rowIdAttr = "";
                if (tooltip != null) {
                    String rowId = "tt-" + tooltipRowIds.size();
                    tooltipRowIds.add(rowId);
                    rowIdAttr = " id=\"" + rowId + "\"";
                }
                sb.append("          <div").append(rowIdAttr).append(" style=\"layout-mode: Left; anchor-height: 32; anchor-horizontal: 0;\" data-hyui-style=\"Padding: (Horizontal: 8; Vertical: 6)\">\n");
                sb.append("            <div style=\"flex-weight: 1; layout-mode: Left;\"><p style=\"font-size: 14; color: ").append(labelColor).append(";\">").append(row.name).append("</p></div>\n");
                // Detail comes before value so value stays on the far right
                if (row.detail != null && !row.detail.isEmpty()) {
                    sb.append("            <div style=\"anchor-width: 180; layout-mode: Right;\"><p style=\"font-size: 12; color: ").append(RPGStyles.TEXT_MUTED).append(";\">").append(row.detail).append("</p></div>\n");
                }
                sb.append("            <div style=\"anchor-width: 100; layout-mode: Right;\"><p style=\"font-size: 14; color: ").append(valueColor).append(";\">").append(row.value != null ? row.value : "").append("</p></div>\n");
                if (tooltip != null) {
                    sb.append("            ").append(tooltip).append("\n");
                }
                sb.append("          </div>\n");
            }
        }

        sb.append("        </div>\n");
        return sb.toString();
    }

    /**
     * Formats armor reduction percentage.
     */
    @Nullable
    private String formatArmorReduction(float armor) {
        if (armor <= 0) return null;
        float reduction = (armor / (armor + 1000f)) * 100f;
        return String.format("%.1f%% reduction", reduction);
    }

    /**
     * Formats evasion chance percentage against a same-level mob reference.
     *
     * <p>Computes the effective evasion chance using the PoE-style hit chance
     * formula against a baseline mob's accuracy at the player's level.
     *
     * @param evasion The player's evasion rating
     * @param level The player's current level
     * @return A detail string like "23.5% vs Lv.50", or null if unavailable
     */
    @Nullable
    private String formatEvasionChance(float evasion, int level) {
        if (evasion <= 0 || level < 1) return null;
        try {
            ConfigService configService = ServiceRegistry.require(ConfigService.class);
            RPGConfig rpgConfig = configService.getRPGConfig();
            if (rpgConfig == null) return null;

            RPGConfig.CombatConfig.EvasionConfig evasionConfig =
                    rpgConfig.getCombat().getEvasion();
            MobStatPoolConfig poolConfig = configService.getMobStatPoolConfig();
            if (poolConfig == null) return null;

            float refAccuracy = (float) new MobStatGenerator(poolConfig)
                    .getBaseStats(level).accuracy();
            float hitChance = AvoidanceProcessor.calculateHitChance(
                    evasionConfig, refAccuracy, evasion);
            float evadeChance = (1f - hitChance) * 100f;
            return String.format("%.1f%% vs Lv.%d", evadeChance, level);
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e)
                    .log("Failed to compute evasion chance detail");
            return null;
        }
    }

    /**
     * Formats a percentage value for display in detail.
     */
    @Nullable
    private String formatPercent(float value) {
        if (value == 0) return null;
        return RPGStyles.formatValue(value, true);
    }

    /**
     * Formats a regen value.
     */
    @Nonnull
    private String formatRegen(float value) {
        if (value <= 0) return "0";
        return String.format("+%.1f/sec", value);
    }

    /**
     * Formats a regen value as a detail string (e.g. "(+3.2/s)") for the Overview tab.
     * Returns null if regen is zero (no detail shown).
     */
    @Nullable
    private String formatRegenDetail(float value) {
        if (value <= 0) return null;
        return String.format("(+%.1f/s)", value);
    }

    /**
     * Computes the per-source breakdown for a stat and adds tooltip HTML to the map.
     * Skips sources with zero contribution. Null-safe (no tooltip if breakdown is null).
     */
    private void putBreakdownTooltip(
        @Nonnull Map<String, String> tooltips,
        @Nonnull String name,
        @Nullable StatBreakdownResult breakdown,
        @Nonnull ToDoubleFunction<ComputedStats> getter
    ) {
        if (breakdown == null) return;

        float baseVal  = (float) getter.applyAsDouble(breakdown.base());
        float attrsVal = (float) getter.applyAsDouble(breakdown.afterAttributes()) - baseVal;
        float treeVal  = (float) getter.applyAsDouble(breakdown.afterSkillTree())
                       - (float) getter.applyAsDouble(breakdown.afterAttributes());
        float gearVal  = (float) getter.applyAsDouble(breakdown.afterGear())
                       - (float) getter.applyAsDouble(breakdown.afterSkillTree());
        float condVal  = (float) getter.applyAsDouble(breakdown.afterConditionals())
                       - (float) getter.applyAsDouble(breakdown.afterGear());
        float total    = (float) getter.applyAsDouble(breakdown.afterConditionals());

        // Skip if only base contributes (nothing interesting to show)
        boolean hasNonBase = attrsVal != 0 || treeVal != 0 || gearVal != 0 || condVal != 0;
        if (!hasNonBase) return;

        StringBuilder sb = new StringBuilder("<tooltip>");
        ttSpan(sb, name + ": " + RPGStyles.formatFlat(total), RPGStyles.TITLE_GOLD, true);
        ttSeparator(sb);

        // Source lines (only non-zero) — each source has a distinct color
        if (baseVal != 0)  ttSourceSpan(sb, "Base",         baseVal, RPGStyles.TEXT_GRAY);
        if (attrsVal != 0) ttSourceSpan(sb, "Attributes",   attrsVal, RPGStyles.TEXT_INFO);
        if (treeVal != 0)  ttSourceSpan(sb, "Skill Tree",   treeVal, RPGStyles.POSITIVE);
        if (gearVal != 0)  ttSourceSpan(sb, "Gear",         gearVal, RPGStyles.TEXT_ORANGE);
        if (condVal != 0)  ttSourceSpan(sb, "Conditionals", condVal, "#BB88DD");

        ttClose(sb);
        tooltips.put(name, sb.toString());
    }

    /**
     * Builds a hover tooltip for an attribute element showing its archetype and stat grants.
     *
     * <p>When points == 0, shows per-point rates with a "Per point:" label.
     * When points > 0, shows total grants from the invested points.
     */
    private void buildAttributeTooltip(
        @Nonnull Map<String, String> tooltips,
        @Nonnull String name,
        int points,
        @Nonnull String color,
        @Nonnull String archetype,
        @Nonnull String... grantLines
    ) {
        StringBuilder sb = new StringBuilder("<tooltip>");

        // Title: element name + point count (in element color)
        ttSpan(sb, name + ": " + points + (points == 1 ? " Point" : " Points"), color, true);

        // Archetype subtitle
        ttSpan(sb, archetype, RPGStyles.TEXT_GRAY);

        // Separator
        ttSeparator(sb);

        // "Per point" label when no points allocated
        if (points == 0) {
            ttSpan(sb, "Per point:", RPGStyles.TEXT_MUTED);
        }

        // Grant lines
        for (String line : grantLines) {
            ttSpan(sb, line, RPGStyles.STAT_VALUE);
        }

        ttClose(sb);
        tooltips.put(name, sb.toString());
    }

    // ── Tooltip span helpers ────────────────────────────────────────────

    /** Appends a colored tooltip span with a trailing newline (&#10;) for line break. */
    private void ttSpan(@Nonnull StringBuilder sb, @Nonnull String text, @Nonnull String color) {
        sb.append("<span data-hyui-color=\"").append(color).append("\">").append(text).append("&#10;</span>");
    }

    /** Appends a colored, optionally bold tooltip span with a trailing newline. */
    private void ttSpan(@Nonnull StringBuilder sb, @Nonnull String text, @Nonnull String color, boolean bold) {
        sb.append("<span data-hyui-color=\"").append(color).append("\"");
        if (bold) sb.append(" data-hyui-bold=\"true\"");
        sb.append(">").append(text).append("&#10;</span>");
    }

    /** Strips trailing &#10; from the last span and closes the tooltip. */
    private void ttClose(@Nonnull StringBuilder sb) {
        int idx = sb.lastIndexOf("&#10;</span>");
        if (idx >= 0) {
            sb.delete(idx, idx + 5); // remove "&#10;", keep "</span>"
        }
        sb.append("</tooltip>");
    }

    /** Appends a dim gray separator span. */
    private void ttSeparator(@Nonnull StringBuilder sb) {
        ttSpan(sb, "------------------------", RPGStyles.DARK_GRAY);
    }

    /** Appends a signed source contribution span (e.g. "Gear: +25.0"). */
    private void ttSourceSpan(@Nonnull StringBuilder sb, @Nonnull String label, float value, @Nonnull String color) {
        String sign = value >= 0 ? "+" : "";
        ttSpan(sb, label + ": " + sign + RPGStyles.formatFlat(value), color);
    }

    /**
     * Adds a stat row only if the raw value is non-zero.
     *
     * @return true if the row was added (for section-emptiness tracking)
     */
    private boolean addRowIfNonZero(@Nonnull List<StatRow> rows, @Nonnull StatRow row, float rawValue) {
        if (rawValue != 0) {
            rows.add(row);
            return true;
        }
        return false;
    }

    /**
     * Adds a section (header + rows) to the main list only if at least one row
     * is present, or if alwaysShow is true. Adds a spacer before the section
     * if the main list is non-empty.
     */
    private void addSection(@Nonnull List<StatRow> target, @Nonnull String header,
                            @Nonnull List<StatRow> sectionRows, boolean alwaysShow) {
        if (!alwaysShow && sectionRows.isEmpty()) return;
        if (!target.isEmpty()) {
            target.add(new StatRow(null, null, null, false, false)); // spacer
        }
        target.add(new StatRow(header, null, null, false, true)); // header
        target.addAll(sectionRows);
    }

    /**
     * Simple record for stat row data.
     */
    private record StatRow(
        @Nullable String name,
        @Nullable String value,
        @Nullable String detail,
        boolean isHighlighted,
        boolean isHeader,
        @Nullable String nameColor
    ) {
        /** Convenience constructor without custom name color (most rows). */
        StatRow(@Nullable String name, @Nullable String value, @Nullable String detail,
                boolean isHighlighted, boolean isHeader) {
            this(name, value, detail, isHighlighted, isHeader, null);
        }
    }
}
