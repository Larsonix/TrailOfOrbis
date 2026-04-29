package io.github.larsonix.trailoforbis.lootfilter.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import io.github.larsonix.trailoforbis.commands.base.OpenPlayerCommand;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.gear.model.EquipmentType;
import io.github.larsonix.trailoforbis.gear.model.GearData;
import io.github.larsonix.trailoforbis.gear.model.GearRarity;
import io.github.larsonix.trailoforbis.gear.util.GearUtils;
import io.github.larsonix.trailoforbis.commands.tooadmin.HeldItemHelper;
import io.github.larsonix.trailoforbis.lootfilter.LootFilterManager;
import io.github.larsonix.trailoforbis.lootfilter.model.FilterAction;
import io.github.larsonix.trailoforbis.lootfilter.model.FilterProfile;
import io.github.larsonix.trailoforbis.lootfilter.model.FilterRule;
import io.github.larsonix.trailoforbis.lootfilter.model.PlayerFilterState;
import io.github.larsonix.trailoforbis.lootfilter.system.LootFilterInventoryHandler;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Player-facing loot filter command with subcommands for controlling
 * filter behavior, switching profiles, and testing against held items.
 *
 * <p>Usage:
 * <ul>
 *   <li>{@code /lf} — Shows status/usage</li>
 *   <li>{@code /lf toggle} — Toggle filtering on/off</li>
 *   <li>{@code /lf on | off} — Explicitly enable/disable</li>
 *   <li>{@code /lf quick <rarity>} — Set quick rarity filter</li>
 *   <li>{@code /lf switch <name>} — Switch to a custom profile</li>
 *   <li>{@code /lf list} — List all profiles with summaries</li>
 *   <li>{@code /lf status} — Show current filter state</li>
 *   <li>{@code /lf test} — Test filter against held item</li>
 *   <li>{@code /lf preset [name]} — List or copy a preset profile</li>
 * </ul>
 *
 * @see LootFilterManager
 */
public final class LfCommand extends OpenPlayerCommand {

    private static final String PREFIX = "[Loot Filter] ";

    private final LootFilterManager filterManager;
    private final OptionalArg<String> actionArg;
    private final OptionalArg<String> valueArg;

    public LfCommand(@Nonnull LootFilterManager filterManager) {
        super("lf", "Loot filter controls");
        this.addAliases("lootfilter");
        this.filterManager = filterManager;

        actionArg = this.withOptionalArg("action",
                "toggle/on/off/quick/switch/list/status/test/preset", ArgTypes.STRING);
        valueArg = this.withOptionalArg("value",
                "Rarity for quick, name for switch/preset", ArgTypes.STRING);
    }

    @Override
    protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef player,
            @Nonnull World world) {

        String action = actionArg.get(context);
        if (action == null) {
            // Open the loot filter UI page
            io.github.larsonix.trailoforbis.api.ServiceRegistry
                    .get(io.github.larsonix.trailoforbis.api.services.UIService.class)
                    .ifPresentOrElse(
                            ui -> ui.openLootFilterPage(player, store),
                            () -> handleStatus(player));
            return;
        }

        switch (action.toLowerCase()) {
            case "toggle" -> handleToggle(player);
            case "on" -> handleEnable(player);
            case "off" -> handleDisable(player);
            case "quick" -> handleQuickFilter(player, context);
            case "switch" -> handleSwitch(player, context);
            case "list" -> handleList(player);
            case "status" -> handleStatus(player);
            case "test" -> handleTest(player, store, ref);
            case "preset" -> handlePreset(player, context);
            default -> sendUsage(player);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // TOGGLE / ON / OFF
    // ═══════════════════════════════════════════════════════════════════

    private void handleToggle(@Nonnull PlayerRef player) {
        UUID playerId = player.getUuid();
        filterManager.toggleFiltering(playerId);
        PlayerFilterState state = filterManager.getState(playerId);

        if (state.isFilteringEnabled()) {
            Optional<FilterProfile> profile = state.getActiveProfile();
            if (state.isUsingQuickFilter()) {
                sendPrefixed(player, Message.raw("Filtering enabled.")
                        .color(MessageColors.SUCCESS)
                        .insert(Message.raw(" (Quick Filter: " + state.getQuickFilterRarity().name() + "+)")
                                .color(MessageColors.GRAY)));
            } else if (profile.isPresent()) {
                sendPrefixed(player, Message.raw("Filtering enabled.")
                        .color(MessageColors.SUCCESS)
                        .insert(Message.raw(" (Active: \"" + profile.get().getName() + "\", "
                                + profile.get().getRules().size() + " rules)")
                                .color(MessageColors.GRAY)));
            } else {
                sendPrefixed(player, Message.raw("Filtering enabled.")
                        .color(MessageColors.SUCCESS));
            }
        } else {
            sendPrefixed(player, Message.raw("Filtering disabled.").color(MessageColors.GRAY));
        }
    }

    private void handleEnable(@Nonnull PlayerRef player) {
        filterManager.setFilteringEnabled(player.getUuid(), true);
        sendPrefixed(player, Message.raw("Filtering enabled.").color(MessageColors.SUCCESS));
    }

    private void handleDisable(@Nonnull PlayerRef player) {
        filterManager.setFilteringEnabled(player.getUuid(), false);
        sendPrefixed(player, Message.raw("Filtering disabled.").color(MessageColors.GRAY));
    }

    // ═══════════════════════════════════════════════════════════════════
    // QUICK FILTER
    // ═══════════════════════════════════════════════════════════════════

    private void handleQuickFilter(@Nonnull PlayerRef player, @Nonnull CommandContext context) {
        String value = valueArg.get(context);
        if (value == null) {
            sendPrefixed(player, Message.raw("Usage: /lf quick <common|uncommon|rare|epic|legendary|off>")
                    .color(MessageColors.WARNING));
            return;
        }

        if (value.equalsIgnoreCase("off") || value.equalsIgnoreCase("clear")) {
            filterManager.clearQuickFilter(player.getUuid());
            sendPrefixed(player, Message.raw("Quick filter disabled.").color(MessageColors.GRAY));
            return;
        }

        GearRarity rarity;
        try {
            rarity = GearRarity.fromString(value);
        } catch (IllegalArgumentException e) {
            sendPrefixed(player, Message.raw("Unknown rarity: " + value + ". Use: common, uncommon, rare, epic, legendary, mythic")
                    .color(MessageColors.ERROR));
            return;
        }

        filterManager.setQuickFilter(player.getUuid(), rarity);
        sendPrefixed(player, Message.raw("Quick filter: blocking everything below ")
                .color(MessageColors.SUCCESS)
                .insert(Message.raw(rarity.name()).color(MessageColors.WARNING))
                .insert(Message.raw(".").color(MessageColors.SUCCESS)));
    }

    // ═══════════════════════════════════════════════════════════════════
    // SWITCH PROFILE
    // ═══════════════════════════════════════════════════════════════════

    private void handleSwitch(@Nonnull PlayerRef player, @Nonnull CommandContext context) {
        String name = valueArg.get(context);
        if (name == null) {
            sendPrefixed(player, Message.raw("Usage: /lf switch <profile name>")
                    .color(MessageColors.WARNING));
            return;
        }

        UUID playerId = player.getUuid();
        PlayerFilterState state = filterManager.getState(playerId);
        Optional<FilterProfile> match = state.getProfileByName(name);

        if (match.isEmpty()) {
            sendPrefixed(player, Message.raw("No profile found with name \"" + name + "\". Use /lf list to see profiles.")
                    .color(MessageColors.ERROR));
            return;
        }

        FilterProfile profile = match.get();
        filterManager.setActiveProfile(playerId, profile.getId());
        sendPrefixed(player, Message.raw("Switched to profile \"" + profile.getName() + "\".")
                .color(MessageColors.SUCCESS)
                .insert(Message.raw(" (" + profile.getRules().size() + " rules, default: "
                        + profile.getDefaultAction().name() + ")")
                        .color(MessageColors.GRAY)));
    }

    // ═══════════════════════════════════════════════════════════════════
    // LIST PROFILES
    // ═══════════════════════════════════════════════════════════════════

    private void handleList(@Nonnull PlayerRef player) {
        PlayerFilterState state = filterManager.getState(player.getUuid());
        List<FilterProfile> profiles = state.getProfiles();

        if (profiles.isEmpty()) {
            sendPrefixed(player, Message.raw("You have no profiles. Use /lf preset <name> to create one.")
                    .color(MessageColors.GRAY));
            return;
        }

        sendPrefixed(player, Message.raw("Your profiles:").color(MessageColors.INFO));

        for (FilterProfile profile : profiles) {
            boolean isActive = profile.getId().equals(state.getActiveProfileId());
            String marker = isActive ? " [ACTIVE]" : "";

            Message header = Message.raw("  " + (isActive ? "> " : "  ") + profile.getName()
                    + " (" + profile.getRules().size() + " rules)" + marker)
                    .color(isActive ? MessageColors.SUCCESS : MessageColors.WHITE);
            player.sendMessage(header);

            // Show rule summaries for the active profile
            if (isActive) {
                List<FilterRule> rules = profile.getRules();
                int shown = Math.min(rules.size(), 5);
                for (int i = 0; i < shown; i++) {
                    FilterRule rule = rules.get(i);
                    player.sendMessage(Message.raw("      #" + (i + 1) + " " + rule.action().name()
                            + ": " + rule.describeSummary()).color(MessageColors.GRAY));
                }
                if (rules.size() > shown) {
                    player.sendMessage(Message.raw("      ... and " + (rules.size() - shown) + " more")
                            .color(MessageColors.GRAY));
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // STATUS
    // ═══════════════════════════════════════════════════════════════════

    private void handleStatus(@Nonnull PlayerRef player) {
        PlayerFilterState state = filterManager.getState(player.getUuid());

        if (!state.isFilteringEnabled()) {
            sendPrefixed(player, Message.raw("Status: ")
                    .color(MessageColors.GRAY)
                    .insert(Message.raw("DISABLED").color(MessageColors.ERROR)));
            player.sendMessage(Message.raw("  No active filter.").color(MessageColors.GRAY));
            return;
        }

        if (state.isUsingQuickFilter()) {
            GearRarity rarity = state.getQuickFilterRarity();
            sendPrefixed(player, Message.raw("Status: ")
                    .color(MessageColors.GRAY)
                    .insert(Message.raw("ENABLED").color(MessageColors.SUCCESS))
                    .insert(Message.raw(" (Quick Filter: " + rarity.name() + "+)").color(MessageColors.INFO)));
            player.sendMessage(Message.raw("  Blocking everything below " + rarity.name() + ".")
                    .color(MessageColors.GRAY));
            return;
        }

        Optional<FilterProfile> profileOpt = state.getActiveProfile();
        if (profileOpt.isPresent()) {
            FilterProfile profile = profileOpt.get();
            sendPrefixed(player, Message.raw("Status: ")
                    .color(MessageColors.GRAY)
                    .insert(Message.raw("ENABLED").color(MessageColors.SUCCESS))
                    .insert(Message.raw(" (Profile: \"" + profile.getName() + "\")").color(MessageColors.INFO)));
            player.sendMessage(Message.raw("  " + profile.getRules().size() + " rules, default: "
                    + profile.getDefaultAction().name()).color(MessageColors.GRAY));
        } else {
            sendPrefixed(player, Message.raw("Status: ")
                    .color(MessageColors.GRAY)
                    .insert(Message.raw("ENABLED").color(MessageColors.SUCCESS))
                    .insert(Message.raw(" (No active filter)").color(MessageColors.WARNING)));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // TEST AGAINST HELD ITEM
    // ═══════════════════════════════════════════════════════════════════

    private void handleTest(@Nonnull PlayerRef player, @Nonnull Store<EntityStore> store,
                            @Nonnull Ref<EntityStore> ref) {
        // Read held item
        ItemStack heldItem = HeldItemHelper.getHeldItem(player, store, ref);
        if (heldItem == null || !GearUtils.isRpgGear(heldItem)) {
            sendPrefixed(player, Message.raw("You must be holding an RPG gear item to test.")
                    .color(MessageColors.ERROR));
            return;
        }

        Optional<GearData> gearOpt = GearUtils.readGearData(heldItem);
        if (gearOpt.isEmpty()) {
            sendPrefixed(player, Message.raw("Could not read gear data from held item.")
                    .color(MessageColors.ERROR));
            return;
        }

        GearData gearData = gearOpt.get();
        EquipmentType equipType = LootFilterInventoryHandler.resolveEquipmentType(gearData);
        UUID playerId = player.getUuid();
        PlayerFilterState state = filterManager.getState(playerId);

        int modCount = gearData.prefixes().size() + gearData.suffixes().size();
        String itemDesc = gearData.rarity().name() + " " + equipType.name()
                + " (Lv" + gearData.level() + ", Quality " + gearData.quality()
                + ", " + modCount + " mods)";
        sendPrefixed(player, Message.raw("Testing: ")
                .color(MessageColors.INFO)
                .insert(Message.raw(itemDesc).color(MessageColors.WHITE)));

        if (!state.isFilteringEnabled()) {
            player.sendMessage(Message.raw("  Filtering is disabled - item would be ALLOWED")
                    .color(MessageColors.GRAY));
            return;
        }

        // Quick filter test
        if (state.isUsingQuickFilter()) {
            GearRarity minRarity = state.getQuickFilterRarity();
            boolean passes = gearData.rarity().ordinal() >= minRarity.ordinal();
            String result = passes ? "ALLOWED" : "BLOCKED";
            String resultColor = passes ? MessageColors.SUCCESS : MessageColors.ERROR;
            player.sendMessage(Message.raw("  Quick filter: " + minRarity.name() + "+ -> ")
                    .color(MessageColors.GRAY)
                    .insert(Message.raw(result).color(resultColor))
                    .insert(Message.raw(" (" + gearData.rarity().name()
                            + (passes ? " >= " : " < ") + minRarity.name() + ")")
                            .color(MessageColors.GRAY)));
            return;
        }

        // Profile-based test with trace
        Optional<FilterProfile> profileOpt = state.getActiveProfile();
        if (profileOpt.isEmpty()) {
            player.sendMessage(Message.raw("  No active profile - item would be ALLOWED")
                    .color(MessageColors.GRAY));
            return;
        }

        FilterProfile profile = profileOpt.get();
        FilterProfile.EvaluationTrace trace = profile.evaluateWithTrace(gearData, equipType);

        for (FilterProfile.RuleTrace ruleTrace : trace.ruleTraces()) {
            String ruleHeader = "  Rule #" + ruleTrace.ruleNumber() + " \"" + ruleTrace.ruleName() + "\":";
            player.sendMessage(Message.raw(ruleHeader)
                    .color(ruleTrace.matched() ? MessageColors.SUCCESS : MessageColors.GRAY));

            for (String detail : ruleTrace.conditionDetails()) {
                player.sendMessage(Message.raw("    " + detail)
                        .color(detail.startsWith("[x]") ? MessageColors.SUCCESS : MessageColors.ERROR));
            }

            if (ruleTrace.matched()) {
                String actionResult = ruleTrace.action() == FilterAction.ALLOW ? "ALLOWED" : "BLOCKED";
                String color = ruleTrace.action() == FilterAction.ALLOW ? MessageColors.SUCCESS : MessageColors.ERROR;
                player.sendMessage(Message.raw("    -> " + actionResult + " by rule #" + ruleTrace.ruleNumber())
                        .color(color));
            }
        }

        if (!trace.matchedByRule()) {
            String defaultResult = profile.getDefaultAction() == FilterAction.ALLOW ? "ALLOWED" : "BLOCKED";
            String color = profile.getDefaultAction() == FilterAction.ALLOW ? MessageColors.SUCCESS : MessageColors.ERROR;
            player.sendMessage(Message.raw("  No rule matched -> " + defaultResult + " (default action)")
                    .color(color));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // PRESETS
    // ═══════════════════════════════════════════════════════════════════

    private void handlePreset(@Nonnull PlayerRef player, @Nonnull CommandContext context) {
        String name = valueArg.get(context);

        if (name == null) {
            // List available presets
            List<String> presetNames = filterManager.getPresetNames();
            if (presetNames.isEmpty()) {
                sendPrefixed(player, Message.raw("No presets available.")
                        .color(MessageColors.GRAY));
            } else {
                sendPrefixed(player, Message.raw("Available presets: ")
                        .color(MessageColors.INFO)
                        .insert(Message.raw(String.join(", ", presetNames)).color(MessageColors.WHITE)));
            }
            return;
        }

        try {
            filterManager.createProfileFromPreset(player.getUuid(), name);
            sendPrefixed(player, Message.raw("Created profile \"" + name + "\" from preset.")
                    .color(MessageColors.SUCCESS)
                    .insert(Message.raw(" Use /lf switch \"" + name + "\" to activate.")
                            .color(MessageColors.GRAY)));
        } catch (IllegalArgumentException e) {
            List<String> presetNames = filterManager.getPresetNames();
            sendPrefixed(player, Message.raw("Unknown preset. Available: ")
                    .color(MessageColors.ERROR)
                    .insert(Message.raw(String.join(", ", presetNames)).color(MessageColors.WHITE)));
        } catch (IllegalStateException e) {
            sendPrefixed(player, Message.raw(e.getMessage()).color(MessageColors.ERROR));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // USAGE
    // ═══════════════════════════════════════════════════════════════════

    private void sendUsage(@Nonnull PlayerRef player) {
        sendPrefixed(player, Message.raw("Commands:").color(MessageColors.INFO));
        player.sendMessage(Message.raw("  /lf toggle         - Toggle filter on/off").color(MessageColors.GRAY));
        player.sendMessage(Message.raw("  /lf on | off       - Enable/disable filtering").color(MessageColors.GRAY));
        player.sendMessage(Message.raw("  /lf quick <rarity> - Quick rarity filter").color(MessageColors.GRAY));
        player.sendMessage(Message.raw("  /lf switch <name>  - Switch to a profile").color(MessageColors.GRAY));
        player.sendMessage(Message.raw("  /lf list           - List your profiles").color(MessageColors.GRAY));
        player.sendMessage(Message.raw("  /lf status         - Show filter status").color(MessageColors.GRAY));
        player.sendMessage(Message.raw("  /lf test           - Test against held item").color(MessageColors.GRAY));
        player.sendMessage(Message.raw("  /lf preset [name]  - List or copy presets").color(MessageColors.GRAY));
    }

    // ═══════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════

    private void sendPrefixed(@Nonnull PlayerRef player, @Nonnull Message message) {
        player.sendMessage(Message.raw(PREFIX).color(MessageColors.GOLD).insert(message));
    }

}
