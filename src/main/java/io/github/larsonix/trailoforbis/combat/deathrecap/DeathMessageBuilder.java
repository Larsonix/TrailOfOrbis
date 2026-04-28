package io.github.larsonix.trailoforbis.combat.deathrecap;

import com.hypixel.hytale.server.core.Message;
import io.github.larsonix.trailoforbis.combat.DamageType;
import io.github.larsonix.trailoforbis.util.MessageColors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Builds contextual death messages from {@link CombatSnapshot} data.
 *
 * <p>Used by the kill feed systems to produce element-aware, crit-aware
 * death messages that are broadcast to all players. Also usable for
 * any future death message display contexts.
 *
 * <p>Message examples:
 * <ul>
 *   <li>"Scorched to death by [Elite] Fire Trork (Lv45)"</li>
 *   <li>"Frozen solid by Frost Elemental (Lv30) — Critical Strike!"</li>
 *   <li>"Slain by Trork Warrior (Lv27)"</li>
 *   <li>"Consumed by the void"</li>
 * </ul>
 */
public final class DeathMessageBuilder {

    private DeathMessageBuilder() {
        // Utility class
    }

    /**
     * Builds a contextual death message for the victim's kill feed entry.
     *
     * @param snapshot The combat snapshot from the killing blow
     * @param contextual Whether to use element-aware verbs (true) or generic (false)
     * @return Formatted kill feed message for the decedent, or null if no snapshot
     */
    @Nullable
    public static Message buildDecedentMessage(@Nonnull CombatSnapshot snapshot, boolean contextual) {
        String attackerName = DeathRecapFormatter.formatAttackerName(snapshot);

        if (!contextual) {
            return Message.raw("Killed by " + attackerName).color(MessageColors.ERROR);
        }

        String verb = getElementVerb(snapshot.damageType(), snapshot.wasCritical());
        String message;

        if ("environment".equals(snapshot.attackerType())) {
            message = verb + " by " + attackerName;
        } else if (snapshot.wasCritical()) {
            message = verb + " by " + attackerName;
        } else {
            message = verb + " by " + attackerName;
        }

        String color = getDamageTypeColor(snapshot.damageType());
        return Message.raw(message).color(color);
    }

    /**
     * Builds a contextual message for the killer's kill feed entry.
     *
     * @param snapshot The combat snapshot from the killing blow
     * @param targetName The display name of the killed entity
     * @param contextual Whether to use element-aware verbs
     * @return Formatted kill feed message for the killer, or null if no data
     */
    @Nullable
    public static Message buildKillerMessage(
        @Nonnull CombatSnapshot snapshot,
        @Nonnull String targetName,
        boolean contextual
    ) {
        if (!contextual) {
            return Message.raw("You killed " + targetName).color(MessageColors.SUCCESS);
        }

        String verb = getKillerVerb(snapshot.damageType(), snapshot.wasCritical());
        String suffix = "";
        if (snapshot.wasCritical()) {
            suffix = " — Critical Strike!";
        }

        return Message.raw("You " + verb + " " + targetName + suffix).color(MessageColors.SUCCESS);
    }

    /**
     * Gets an element-aware verb for death messages (victim perspective).
     *
     * <p>Returns different verbs for normal vs critical hits to add variety.
     *
     * @param type The damage type that dealt the killing blow
     * @param crit Whether the killing blow was a critical hit
     * @return A past-tense verb phrase
     */
    @Nonnull
    public static String getElementVerb(@Nonnull DamageType type, boolean crit) {
        return switch (type) {
            case PHYSICAL -> crit ? "Struck down" : "Slain";
            case MAGIC -> crit ? "Obliterated" : "Destroyed";
            case FIRE -> crit ? "Scorched to death" : "Burned to death";
            case WATER -> crit ? "Frozen solid" : "Drowned";
            case LIGHTNING -> crit ? "Shocked to death" : "Electrocuted";
            case EARTH -> crit ? "Shattered" : "Crushed";
            case WIND -> crit ? "Ripped asunder" : "Torn apart";
            case VOID -> crit ? "Annihilated" : "Consumed";
        };
    }

    /**
     * Gets an element-aware verb for kill messages (killer perspective).
     *
     * @param type The damage type used for the killing blow
     * @param crit Whether it was a critical hit
     * @return A present-tense verb
     */
    @Nonnull
    public static String getKillerVerb(@Nonnull DamageType type, boolean crit) {
        return switch (type) {
            case PHYSICAL -> crit ? "struck down" : "slew";
            case MAGIC -> crit ? "obliterated" : "destroyed";
            case FIRE -> crit ? "scorched" : "burned";
            case WATER -> crit ? "froze" : "drowned";
            case LIGHTNING -> crit ? "electrocuted" : "shocked";
            case EARTH -> crit ? "shattered" : "crushed";
            case WIND -> crit ? "ripped apart" : "tore apart";
            case VOID -> crit ? "annihilated" : "consumed";
        };
    }

    /**
     * Gets a color for the damage type to tint kill feed messages.
     */
    @Nonnull
    private static String getDamageTypeColor(@Nonnull DamageType type) {
        return switch (type) {
            case PHYSICAL -> MessageColors.ERROR;
            case MAGIC -> MessageColors.LIGHT_BLUE;
            case FIRE -> MessageColors.ORANGE;
            case WATER -> MessageColors.LIGHT_BLUE;
            case LIGHTNING -> MessageColors.WARNING;
            case EARTH -> MessageColors.SUCCESS;
            case WIND -> MessageColors.WHITE;
            case VOID -> MessageColors.DARK_PURPLE;
        };
    }
}
