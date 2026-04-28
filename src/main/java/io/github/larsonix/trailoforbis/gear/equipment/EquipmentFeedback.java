package io.github.larsonix.trailoforbis.gear.equipment;

import io.github.larsonix.trailoforbis.attributes.AttributeType;
import io.github.larsonix.trailoforbis.gear.equipment.EquipmentValidator.LevelRequirementStatus;
import io.github.larsonix.trailoforbis.gear.equipment.EquipmentValidator.RequirementStatus;
import io.github.larsonix.trailoforbis.gear.equipment.EquipmentValidator.ValidationResult;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.util.Map;

/**
 * Provides feedback to players about equipment validation.
 *
 * <p>Messages are sent via the game's chat/notification system.
 */
public final class EquipmentFeedback {

    // Colors for formatting
    private static final String COLOR_ERROR = "#FF5555";
    private static final String COLOR_WARNING = "#FFAA00";
    private static final String COLOR_GRAY = "#AAAAAA";
    private static final String COLOR_WHITE = "#FFFFFF";
    private static final String COLOR_GREEN = "#55FF55";
    private static final String COLOR_RED = "#FF5555";
    private static final String COLOR_YELLOW = "#FFFF55";

    /**
     * Sends feedback when requirements are not met.
     *
     * <p>Includes both level and attribute requirements.
     *
     * @param slotType "weapon", "armor", or "utility"
     */
    public void sendRequirementsNotMet(
            PlayerRef playerRef,
            String slotType,
            ValidationResult result
    ) {
        if (playerRef == null) return;

        // Guide milestone: first equip rejection
        io.github.larsonix.trailoforbis.TrailOfOrbis rpg = io.github.larsonix.trailoforbis.TrailOfOrbis.getInstanceOrNull();
        if (rpg != null && rpg.getGuideManager() != null) {
            rpg.getGuideManager().tryShow(playerRef.getUuid(), io.github.larsonix.trailoforbis.guide.GuideMilestone.GEAR_REQUIREMENTS);
        }

        // Build message
        Message message = Message.empty();
        message = message.insert(Message.raw("You cannot equip this " + slotType + ". Requirements not met :").color(COLOR_ERROR));

        // Show level requirement first (if present)
        // Color alone indicates met/unmet (Hytale's font doesn't support Unicode checkmarks)
        LevelRequirementStatus levelStatus = result.levelRequirement();
        if (levelStatus != null) {
            Message levelLine = Message.raw("\n  ");
            String levelColor = levelStatus.met() ? COLOR_GREEN : COLOR_RED;
            levelLine = levelLine.insert(Message.raw("Level : " + levelStatus.requiredLevel()).color(levelColor));

            if (!levelStatus.met()) {
                levelLine = levelLine.insert(Message.raw(" (you are level ").color(COLOR_GRAY));
                levelLine = levelLine.insert(Message.raw(String.valueOf(levelStatus.playerLevel())).color(COLOR_WHITE));
                levelLine = levelLine.insert(Message.raw(")").color(COLOR_GRAY));
            }

            message = message.insert(levelLine);
        }

        // Show attribute requirements
        // Color alone indicates met/unmet (Hytale's font doesn't support Unicode checkmarks)
        for (Map.Entry<AttributeType, RequirementStatus> entry : result.requirements().entrySet()) {
            AttributeType attr = entry.getKey();
            RequirementStatus status = entry.getValue();

            Message line = Message.raw("\n  ");
            String attrColor = status.met() ? COLOR_GREEN : COLOR_RED;
            line = line.insert(Message.raw(attr.getDisplayName() + " : " + status.required()).color(attrColor));

            if (!status.met()) {
                line = line.insert(Message.raw(" (you have : ").color(COLOR_GRAY));
                line = line.insert(Message.raw(String.valueOf(status.actual())).color(COLOR_WHITE));
                line = line.insert(Message.raw(")").color(COLOR_GRAY));
            }

            message = message.insert(line);
        }

        playerRef.sendMessage(message);
    }

    /**
     * Sends feedback when armor is auto-unequipped.
     */
    public void sendAutoUnequipped(PlayerRef playerRef, String slotType) {
        if (playerRef == null) return;
        Message message = Message.raw("Your " + slotType + " was unequipped because you no longer meet the requirements.")
                .color(COLOR_WARNING);
        playerRef.sendMessage(message);
    }

    /**
     * Sends warning that weapon bonuses are disabled.
     */
    public void sendWarningWeaponInvalid(PlayerRef playerRef) {
        if (playerRef == null) return;
        Message message = Message.raw("Warning : Your weapon no longer meets requirements. Its bonuses are disabled.")
                .color(COLOR_WARNING);
        playerRef.sendMessage(message);
    }

    /**
     * Sends warning that utility bonuses are disabled.
     */
    public void sendWarningUtilityInvalid(PlayerRef playerRef) {
        if (playerRef == null) return;
        Message message = Message.raw("Warning : Your utility item no longer meets requirements. Its bonuses are disabled.")
                .color(COLOR_WARNING);
        playerRef.sendMessage(message);
    }
}
