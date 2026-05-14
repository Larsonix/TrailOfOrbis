package io.github.larsonix.trailoforbis.combat.format;

import com.hypixel.hytale.server.core.Message;
import io.github.larsonix.trailoforbis.combat.AttackType;
import io.github.larsonix.trailoforbis.combat.deathrecap.CombatSnapshot;
import io.github.larsonix.trailoforbis.elemental.ElementType;
import io.github.larsonix.trailoforbis.mobs.classification.RPGMobClass;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Shared constants and formatting helpers for all combat feedback output.
 *
 * <p>Provides element colors, mob class colors, entity name formatting,
 * and player-friendly label resolution used by both the combat detail
 * formatter and the death recap formatter.
 */
public final class CombatFormatConstants {

    // --- Muted color for low-importance context (darker than GRAY) ---
    public static final String MUTED = "#888888";

    // --- Element colors (consistent across all combat output) ---
    public static final String COLOR_FIRE = MessageColors.ORANGE;
    public static final String COLOR_WATER = MessageColors.LIGHT_BLUE;
    public static final String COLOR_LIGHTNING = MessageColors.WARNING;
    public static final String COLOR_EARTH = MessageColors.SUCCESS;
    public static final String COLOR_WIND = MessageColors.WHITE;
    public static final String COLOR_VOID = MessageColors.DARK_PURPLE;

    private CombatFormatConstants() {}

    // ==================== Element & Class Colors ====================

    @Nonnull
    public static String getElementColor(@Nonnull ElementType element) {
        return switch (element) {
            case FIRE -> COLOR_FIRE;
            case WATER -> COLOR_WATER;
            case LIGHTNING -> COLOR_LIGHTNING;
            case EARTH -> COLOR_EARTH;
            case WIND -> COLOR_WIND;
            case VOID -> COLOR_VOID;
        };
    }

    @Nonnull
    public static String getClassColor(@Nonnull RPGMobClass mobClass) {
        return switch (mobClass) {
            case BOSS -> MessageColors.DARK_PURPLE;
            case ELITE -> MessageColors.GOLD;
            case HOSTILE -> MessageColors.ERROR;
            case MINOR -> MessageColors.WARNING;
            case PASSIVE -> MessageColors.GRAY;
        };
    }

    @Nonnull
    public static String formatClassName(@Nonnull RPGMobClass mobClass) {
        return switch (mobClass) {
            case BOSS -> "Boss";
            case ELITE -> "Elite";
            case HOSTILE -> "Hostile";
            case MINOR -> "Minor";
            case PASSIVE -> "Passive";
        };
    }

    // ==================== Attack Type Labels ====================

    /**
     * Returns a player-friendly label for an attack type bonus.
     * Used in the "% Increased" section to label the attack-type-specific bonus.
     */
    @Nonnull
    public static String attackTypeBonusLabel(@Nonnull AttackType type) {
        return switch (type) {
            case MELEE -> "Melee Damage";
            case PROJECTILE -> "Projectile Damage";
            case AREA -> "Area Damage";
            case SPELL -> "Spell Damage";
            default -> "Attack Bonus";
        };
    }

    // ==================== Entity Name Formatting ====================

    /**
     * Formats an entity name with colored mob class prefix and level as a Message.
     */
    @Nonnull
    public static Message formatEntityNameColored(
        @Nonnull String name,
        @Nonnull String type,
        int level,
        @Nullable RPGMobClass mobClass
    ) {
        if ("environment".equals(type) || "dot".equals(type)) {
            return Message.raw(name).color(MessageColors.ORANGE);
        }

        Message message = Message.empty();

        if (mobClass != null && mobClass != RPGMobClass.HOSTILE && mobClass != RPGMobClass.PASSIVE) {
            message = message.insert(Message.raw("[" + formatClassName(mobClass) + "] ").color(getClassColor(mobClass)));
        }

        String nameColor = "player".equals(type) ? MessageColors.INFO : MessageColors.WHITE;
        message = message.insert(Message.raw(name).color(nameColor));

        if (level > 0) {
            message = message.insert(Message.raw(" (Lv" + level + ")").color(MessageColors.GRAY));
        }

        return message;
    }

    /**
     * Formats an entity name with colored mob class prefix from a CombatSnapshot.
     */
    @Nonnull
    public static Message formatAttackerNameColored(@Nonnull CombatSnapshot snapshot) {
        return formatEntityNameColored(
            snapshot.attackerName(), snapshot.attackerType(),
            snapshot.attackerLevel(), snapshot.attackerClass());
    }

    /**
     * Formats a mob role name into display name (plain string).
     * Example: "trork_warrior" -> "Trork Warrior"
     */
    @Nonnull
    public static String formatMobName(@Nullable String roleName) {
        if (roleName == null || roleName.isEmpty()) {
            return "Unknown Entity";
        }

        return Arrays.stream(roleName.split("_"))
            .map(word -> {
                if (word.isEmpty()) return "";
                return Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase();
            })
            .collect(Collectors.joining(" "));
    }

    /**
     * Formats attacker name as a plain string from a CombatSnapshot.
     */
    @Nonnull
    public static String formatAttackerName(@Nonnull CombatSnapshot snapshot) {
        if ("environment".equals(snapshot.attackerType()) || "dot".equals(snapshot.attackerType())) {
            return snapshot.attackerName();
        }

        StringBuilder sb = new StringBuilder();

        if (snapshot.attackerClass() != null && snapshot.attackerClass() != RPGMobClass.HOSTILE) {
            sb.append("[").append(formatClassName(snapshot.attackerClass())).append("] ");
        }

        sb.append(snapshot.attackerName());

        if (snapshot.attackerLevel() > 0) {
            sb.append(" (Lv").append(snapshot.attackerLevel()).append(")");
        }

        return sb.toString();
    }
}
