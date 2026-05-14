package io.github.larsonix.trailoforbis.sanctum;

import com.hypixel.hytale.logger.HytaleLogger;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.sanctum.components.SkillNodeComponent;
import io.github.larsonix.trailoforbis.sanctum.ui.SkillNodeDetailHud;
import io.github.larsonix.trailoforbis.sanctum.ui.SkillNodeHudManager;
import io.github.larsonix.trailoforbis.skilltree.NodeState;
import io.github.larsonix.trailoforbis.skilltree.SkillTreeManager;
import io.github.larsonix.trailoforbis.skilltree.config.SkillNode;

import javax.annotation.Nonnull;
import java.util.Optional;
import java.util.UUID;

/**
 * Handles skill node inspection when a player attacks a skill node entity in the sanctum.
 *
 * <p>When a player left-clicks a skill node entity, this handler cancels the damage
 * and opens/closes the node detail HUD. Only the node's owner player can inspect it.
 *
 * <p>Extracted from RPGDamageSystem Phase 1 early-return check to separate sanctum
 * UI concerns from the damage pipeline.
 */
public class SkillNodeInspectionHandler {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final TrailOfOrbis plugin;

    public SkillNodeInspectionHandler(@Nonnull TrailOfOrbis plugin) {
        this.plugin = plugin;
    }

    /**
     * Handles skill node inspection (left-click on a skill node entity).
     *
     * <p>If the defender entity is a skill node owned by the attacking player,
     * cancels the damage and toggles the node detail HUD.
     *
     * @param store The entity store
     * @param defenderRef The entity being attacked
     * @param damage The damage event
     * @return true if the damage should be cancelled (target was a skill node)
     */
    public boolean handleSkillNodeInspection(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> defenderRef,
        @Nonnull Damage damage
    ) {
        TrailOfOrbis pluginInstance = TrailOfOrbis.getInstance();
        if (pluginInstance == null) {
            return false;
        }

        ComponentType<EntityStore, SkillNodeComponent> nodeComponentType = pluginInstance.getSkillNodeComponentType();
        if (nodeComponentType == null) {
            return false;
        }

        SkillNodeComponent nodeComponent = store.getComponent(defenderRef, nodeComponentType);
        if (nodeComponent == null) {
            return false;
        }

        damage.setCancelled(true);

        if (!(damage.getSource() instanceof Damage.EntitySource entitySource)) {
            return true;
        }

        Ref<EntityStore> attackerRef = entitySource.getRef();
        if (attackerRef == null || !attackerRef.isValid()) {
            return true;
        }

        Player playerComponent = store.getComponent(attackerRef, Player.getComponentType());
        if (playerComponent == null) {
            return true;
        }

        PlayerRef playerRef = store.getComponent(attackerRef, PlayerRef.getComponentType());
        if (playerRef == null || !playerRef.isValid()) {
            return true;
        }

        UUID playerId = playerRef.getUuid();
        UUID nodeOwnerId = nodeComponent.getOwnerPlayerId();

        if (!playerId.equals(nodeOwnerId)) {
            return true;
        }

        String nodeId = nodeComponent.getNodeId();
        SkillTreeManager skillTreeManager = pluginInstance.getSkillTreeManager();
        if (skillTreeManager == null) {
            return true;
        }

        Optional<SkillNode> nodeOpt = skillTreeManager.getNode(nodeId);
        if (nodeOpt.isEmpty()) {
            return true;
        }

        SkillNodeHudManager hudManager = pluginInstance.getSkillSanctumManager().getSkillNodeHudManager();
        String currentNodeId = hudManager.getActiveNodeId(playerId);
        if (nodeId.equals(currentNodeId)) {
            hudManager.removeHud(playerId);
            return true;
        }

        // Skip if a HUD was just opened — prevents client crash from rapid
        // add/hide/remove CustomUI command bursts during multi-hit swings
        if (hudManager.isOnCooldown(playerId)) {
            return true;
        }

        SkillNode node = nodeOpt.get();
        NodeState nodeState = nodeComponent.getState();
        int availablePoints = skillTreeManager.getAvailablePoints(playerId);

        // Check if this node can be deallocated (connectivity + refund points)
        boolean canDeallocate = (nodeState == NodeState.ALLOCATED)
            && skillTreeManager.canDeallocate(playerId, nodeId)
            && skillTreeManager.getSkillTreeData(playerId).getSkillRefundPoints() > 0;

        // Calculate synergy progress for live display (null if not a synergy node)
        var synergyProgress = skillTreeManager.getSynergyProgress(playerId, node);

        SkillNodeDetailHud detailHud = new SkillNodeDetailHud(
            pluginInstance, playerRef, node, nodeState, availablePoints, canDeallocate, hudManager, synergyProgress);
        detailHud.show();

        LOGGER.atInfo().log("Opened skill node detail HUD for player=%s, node=%s",
            playerId.toString().substring(0, 8), nodeId);

        return true;
    }
}
