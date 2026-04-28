package io.github.larsonix.trailoforbis.gems.tooltip;

import com.hypixel.hytale.server.core.Message;
import io.github.larsonix.trailoforbis.gear.tooltip.TooltipStyles;
import io.github.larsonix.trailoforbis.gems.model.ActiveGemDefinition;
import io.github.larsonix.trailoforbis.gems.model.CastType;
import io.github.larsonix.trailoforbis.gems.model.GemData;
import io.github.larsonix.trailoforbis.gems.model.GemDefinition;
import io.github.larsonix.trailoforbis.gems.model.SupportGemDefinition;
import io.github.larsonix.trailoforbis.gems.model.SupportModification;
import javax.annotation.Nonnull;

public final class GemTooltipBuilder {
    private static final String FIRE_COLOR = "#FF6B35";
    private static final String ICE_COLOR = "#5CC8FF";
    private static final String LIGHTNING_COLOR = "#FFE066";
    private static final String EARTH_COLOR = "#8B6914";
    private static final String WIND_COLOR = "#90EE90";
    private static final String VOID_COLOR = "#9B59B6";
    private static final String WATER_COLOR = "#3498DB";
    private static final String PHYSICAL_COLOR = "#C9D2DD";
    private static final String SUPPORT_COLOR = "#3498DB";

    private GemTooltipBuilder() {
        throw new UnsupportedOperationException("Utility class");
    }

    @Nonnull
    public static Message buildTooltip(@Nonnull GemDefinition definition, @Nonnull GemData data) {
        return switch (definition) {
            case ActiveGemDefinition active -> buildActiveTooltip(active, data);
            case SupportGemDefinition support -> buildSupportTooltip(support, data);
            default -> throw new IllegalArgumentException("Unknown GemDefinition type: " + definition.getClass().getName());
        };
    }

    private static Message buildActiveTooltip(ActiveGemDefinition def, GemData data) {
        String costStr;
        String nameColor = getElementColor(def.damage().element());
        Message tooltip = Message.empty();
        tooltip = tooltip.insert(Message.raw("[Active Gem]").color("#888888"));
        tooltip = tooltip.insert(Message.raw("\nLevel " + data.level()).color("#FFFFFF"));
        tooltip = tooltip.insert(Message.raw("  Quality " + data.quality()).color(TooltipStyles.getQualityColor(data.quality())));
        tooltip = tooltip.insert(Message.raw("\n---").color("#555555"));
        if (def.damage().basePercent() > 0.0f) {
            String element = def.damage().element() != null ? def.damage().element() : "Physical";
            tooltip = tooltip.insert(Message.raw("\n" + formatPercent(def.damage().basePercent()) + " weapon damage").color("#55FF55"));
            tooltip = tooltip.insert(Message.raw(" (" + element + ")").color(nameColor));
            if (def.damage().aoeRadius() > 0.0f) {
                tooltip = tooltip.insert(Message.raw("  AoE: " + def.damage().aoeRadius() + " blocks").color("#888888"));
            }
        }
        if (!(costStr = formatCost(def.cost())).isEmpty()) {
            tooltip = tooltip.insert(Message.raw("\nCost: " + costStr).color("#888888"));
        }
        if (def.cooldown() > 0.0f) {
            tooltip = tooltip.insert(Message.raw("\nCooldown: " + def.cooldown() + "s").color("#888888"));
        }
        if (def.castType() != CastType.INSTANT) {
            tooltip = tooltip.insert(Message.raw("\nCast: " + formatCastType(def.castType())).color("#888888"));
        }
        if (def.ailment() != null) {
            tooltip = tooltip.insert(Message.raw("\n" + def.ailment().type() + " chance: " + formatPercent(def.ailment().baseChance())).color(getElementColor(def.ailment().type())));
        }
        if (!def.description().isEmpty()) {
            tooltip = tooltip.insert(Message.raw("\n---").color("#555555"));
            tooltip = tooltip.insert(Message.raw("\n" + def.description()).color("#888888"));
        }
        return tooltip;
    }

    private static Message buildSupportTooltip(SupportGemDefinition def, GemData data) {
        Message tooltip = Message.empty();
        tooltip = tooltip.insert(Message.raw("[Support Gem]").color("#3498DB"));
        tooltip = tooltip.insert(Message.raw("\nLevel " + data.level()).color("#FFFFFF"));
        tooltip = tooltip.insert(Message.raw("  Quality " + data.quality()).color(TooltipStyles.getQualityColor(data.quality())));
        if (!def.requiresTags().isEmpty()) {
            tooltip = tooltip.insert(Message.raw("\nRequires: " + String.join(", ", def.requiresTags())).color("#888888"));
        }
        tooltip = tooltip.insert(Message.raw("\n---").color("#555555"));
        for (SupportModification mod : def.modifications()) {
            tooltip = tooltip.insert(formatModification(mod));
        }
        if (!def.description().isEmpty()) {
            tooltip = tooltip.insert(Message.raw("\n---").color("#555555"));
            tooltip = tooltip.insert(Message.raw("\n" + def.description()).color("#888888"));
        }
        return tooltip;
    }

    private static Message formatModification(SupportModification mod) {
        return switch (mod.type()) {
            case "add_behavior" -> Message.raw("\n+ " + mod.getString("behavior")).color("#55FF55");
            case "multiply" -> {
                float mult = mod.getFloat("multiplier", 1.0f);
                String prefix = mult >= 1.0f ? "+" : "";
                yield Message.raw("\n" + prefix + formatPercent((mult - 1.0f) * 100.0f) + " " + mod.getString("stat")).color(mult >= 1.0f ? "#55FF55" : "#FF5555");
            }
            case "add_flat_damage" -> Message.raw("\n+" + (int) mod.getFloat("base_amount", 0.0f) + " " + mod.getString("element") + " damage").color("#55FF55");
            case "add_flat" -> Message.raw("\n+" + mod.getFloat("value", 0.0f) + " " + mod.getString("stat")).color("#55FF55");
            case "add_tag" -> Message.raw("\n+ Adds [" + mod.getString("tag") + "]").color("#55FF55");
            case "remove_tag" -> Message.raw("\n- Removes [" + mod.getString("tag") + "]").color("#FF5555");
            case "override" -> Message.raw("\n= " + mod.getString("stat") + " -> " + mod.getFloat("value", 0.0f)).color("#FFFFFF");
            case "conditional" -> Message.raw("\n? " + mod.getString("condition")).color("#888888");
            default -> Message.raw("\n" + mod.type()).color("#888888");
        };
    }

    private static String formatPercent(float value) {
        if (value == (float) ((int) value)) {
            return (int) value + "%";
        }
        return String.format("%.1f%%", Float.valueOf(value));
    }

    private static String formatCost(ActiveGemDefinition.CostConfig cost) {
        return switch (cost.type()) {
            case "mana" -> (int) cost.amount() + " mana";
            case "stamina" -> (int) cost.amount() + " stamina";
            case "hybrid" -> (int) cost.staminaAmount() + " stamina + " + (int) cost.manaAmount() + " mana";
            case "none" -> "";
            default -> (int) cost.amount() + " " + cost.type();
        };
    }

    private static String formatCastType(CastType type) {
        return switch (type) {
            case INSTANT -> "Instant";
            case QUICK_CAST -> "Quick Cast";
            case COMMITTED -> "Committed";
            case CHANNELED -> "Channeled";
            default -> type.name();
        };
    }

    private static String getElementColor(String element) {
        if (element == null) {
            return PHYSICAL_COLOR;
        }
        return switch (element.toLowerCase()) {
            case "fire", "burn" -> FIRE_COLOR;
            case "ice", "freeze" -> ICE_COLOR;
            case "lightning", "shock" -> LIGHTNING_COLOR;
            case "earth" -> EARTH_COLOR;
            case "wind" -> WIND_COLOR;
            case "void", "poison" -> VOID_COLOR;
            case "water", "drenched" -> WATER_COLOR;
            default -> PHYSICAL_COLOR;
        };
    }
}
