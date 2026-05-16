package io.github.larsonix.trailoforbis.skilltree.conditional;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.RemovalBehavior;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.mobs.speed.RPGApplicationEffects;
import io.github.larsonix.trailoforbis.mobs.speed.RPGEntityEffect;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages visual status effect icons for conditional skill tree buffs.
 *
 * <p>Each skill node with a conditional config and an {@code icon} field gets its own
 * registered EntityEffect. When the node's conditional activates, the icon appears on
 * the player's HUD with a countdown ring. Each node is visually distinct.
 *
 * <p>Effects are pre-registered during plugin init (required by Hytale's asset store).
 * Runtime application/removal is dispatched to the world thread.
 *
 * <p>Thread safety: All EntityEffect operations dispatch to the world thread.
 * Tracking maps use ConcurrentHashMap for safe access from combat event threads.
 */
public class ConditionalVisualEffectManager implements ConditionalVisualCallback {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String EFFECT_ID_PREFIX = "rpg_cond_";
    private static final String PACK_KEY = "trailoforbis";

    /**
     * Maps nodeId → registered effect ID.
     * Only nodes with a configured icon are present.
     */
    private final Map<String, String> nodeEffectIds = new ConcurrentHashMap<>();

    /**
     * Tracks which node effects are currently active per player (for cleanup on disconnect).
     * playerId → set of active nodeIds
     */
    private final ConcurrentHashMap<UUID, ConcurrentHashMap<String, Boolean>> activeVisuals = new ConcurrentHashMap<>();

    private boolean initialized = false;

    /**
     * Creates and registers visual effects for all conditional nodes that have icons.
     *
     * <p>Must be called during plugin initialization (never during a world tick).
     *
     * @param conditionalNodes Map of nodeId → ConditionalConfig for all nodes with conditionals
     */
    public void initialize(@Nonnull Map<String, ConditionalConfig> conditionalNodes) {
        if (initialized) return;

        List<EntityEffect> effects = new ArrayList<>();

        for (Map.Entry<String, ConditionalConfig> entry : conditionalNodes.entrySet()) {
            String nodeId = entry.getKey();
            ConditionalConfig config = entry.getValue();
            String icon = config.getIcon();

            if (icon == null || icon.isBlank()) continue;

            String effectId = EFFECT_ID_PREFIX + nodeId;

            RPGEntityEffect effect = new RPGEntityEffect(effectId);
            RPGApplicationEffects emptyApp = RPGApplicationEffects.create();
            effect.setApplicationEffects(emptyApp);
            effect.setStatusEffectIcon(icon);
            effect.setDebuff(false);
            effect.setInfinite(config.isPersistentEffect());
            effect.setDuration(config.isTimedEffect() ? (float) config.getDuration() : 1.0f);
            effect.setOverlapBehavior(OverlapBehavior.OVERWRITE);
            effect.setRemovalBehavior(RemovalBehavior.COMPLETE);

            effects.add(effect);
            nodeEffectIds.put(nodeId, effectId);
        }

        if (!effects.isEmpty()) {
            EntityEffect.getAssetStore().loadAssets(PACK_KEY, effects);
        }

        initialized = true;
        LOGGER.atInfo().log("ConditionalVisualEffectManager initialized: %d node effects registered", effects.size());
    }

    // ═══════════════════════════════════════════════════════════════════
    // ConditionalVisualCallback implementation
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public void onActivate(@Nonnull UUID playerId, @Nonnull String nodeId, float durationSeconds) {
        if (!initialized) return;

        String effectId = nodeEffectIds.get(nodeId);
        if (effectId == null) return; // Node has no icon configured

        EntityEffect effect = EntityEffect.getAssetMap().getAsset(effectId);
        if (effect == null) return;

        PlayerRef playerRef = Universe.get().getPlayer(playerId);
        if (playerRef == null) return;

        World world = Universe.get().getWorld(playerRef.getWorldUuid());
        if (world == null) return;

        final float finalDuration = durationSeconds > 0 ? durationSeconds : 1.0f;

        world.execute(() -> {
            Ref<EntityStore> freshRef = playerRef.getReference();
            if (freshRef == null || !freshRef.isValid()) return;

            Store<EntityStore> store = world.getEntityStore().getStore();
            EffectControllerComponent ec = store.getComponent(freshRef, EffectControllerComponent.getComponentType());
            if (ec == null) return;

            ec.addEffect(freshRef, effect, finalDuration, OverlapBehavior.OVERWRITE, store);

            activeVisuals.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
                    .put(nodeId, Boolean.TRUE);
        });
    }

    @Override
    public void onDeactivate(@Nonnull UUID playerId, @Nonnull String nodeId) {
        if (!initialized) return;

        String effectId = nodeEffectIds.get(nodeId);
        if (effectId == null) return;

        // Clear tracking
        ConcurrentHashMap<String, Boolean> playerVisuals = activeVisuals.get(playerId);
        if (playerVisuals != null) {
            playerVisuals.remove(nodeId);
        }

        int effectIndex = EntityEffect.getAssetMap().getIndex(effectId);
        if (effectIndex == Integer.MIN_VALUE) return;

        PlayerRef playerRef = Universe.get().getPlayer(playerId);
        if (playerRef == null) return;

        World world = Universe.get().getWorld(playerRef.getWorldUuid());
        if (world == null) return;

        final int idx = effectIndex;
        world.execute(() -> {
            Ref<EntityStore> freshRef = playerRef.getReference();
            if (freshRef == null || !freshRef.isValid()) return;

            Store<EntityStore> store = world.getEntityStore().getStore();
            EffectControllerComponent ec = store.getComponent(freshRef, EffectControllerComponent.getComponentType());
            if (ec == null) return;

            ec.removeEffect(freshRef, idx, store);
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Cleans up tracking for a player on disconnect.
     * Native effects auto-expire or are cleared with the entity.
     */
    public void cleanupPlayer(@Nonnull UUID playerId) {
        activeVisuals.remove(playerId);
    }

    public void shutdown() {
        activeVisuals.clear();
        nodeEffectIds.clear();
        initialized = false;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public int getRegisteredEffectCount() {
        return nodeEffectIds.size();
    }
}
