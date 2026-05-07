package io.github.larsonix.trailoforbis.combat.effects.impl;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.ailments.AilmentState;
import io.github.larsonix.trailoforbis.ailments.AilmentTracker;
import io.github.larsonix.trailoforbis.ailments.AilmentType;
import io.github.larsonix.trailoforbis.combat.effects.CombatEffectContext;
import io.github.larsonix.trailoforbis.combat.effects.KeystoneCombatEffect;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightning KS1: Thundergod — Chain Lightning.
 *
 * <p>When you Shock a target, the shock CHAINS to 1 nearby enemy within 8 blocks,
 * applying 50% of the original shock strength. Chained shocks can chain once more
 * (3 targets total). Single-target damage is reduced by 15% (from drawback).
 *
 * <p>Implementation: After damage is dealt, checks if the defender was just shocked.
 * If so, applies shock to the nearest enemy within range via AilmentTracker.
 * Uses a cooldown per-attacker to prevent infinite chain loops within the same tick.
 */
public class ThundergodEffect extends KeystoneCombatEffect {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Maximum range for shock chain in blocks. */
    private static final float CHAIN_RANGE = 8.0f;
    /** Strength multiplier for chained shock (50% of original). */
    private static final float CHAIN_STRENGTH = 0.50f;
    /** Cooldown in ms between chain procs to prevent loops. */
    private static final long CHAIN_COOLDOWN_MS = 500L;

    private final AilmentTracker ailmentTracker;
    /** Per-attacker cooldown to prevent chain loops. */
    private final ConcurrentHashMap<UUID, Long> lastChainTime = new ConcurrentHashMap<>();

    public ThundergodEffect(@Nonnull AilmentTracker ailmentTracker) {
        super("lightning_keystone_1");
        this.ailmentTracker = ailmentTracker;
    }

    @Nonnull
    @Override
    public String getId() {
        return "thundergod";
    }

    @Override
    public float onPostModifications(@Nonnull CombatEffectContext ctx) {
        if (ctx.attackerUuid() == null || ctx.defenderUuid() == null) return ctx.rpgDamage();

        // Check if the defender has shock (applied this frame by ailment applicator)
        if (!ailmentTracker.hasAilment(ctx.defenderUuid(), AilmentType.SHOCK)) {
            return ctx.rpgDamage();
        }

        // Check cooldown to prevent chain loops
        long now = System.currentTimeMillis();
        Long lastChain = lastChainTime.get(ctx.attackerUuid());
        if (lastChain != null && (now - lastChain) < CHAIN_COOLDOWN_MS) {
            return ctx.rpgDamage();
        }
        lastChainTime.put(ctx.attackerUuid(), now);

        // Find nearest enemy to the defender and apply chained shock
        // This requires iterating entities with position checks
        try {
            chainShockToNearby(ctx);
        } catch (Exception e) {
            LOGGER.atFine().log("Thundergod: chain failed — %s", e.getMessage());
        }

        return ctx.rpgDamage();
    }

    private void chainShockToNearby(@Nonnull CombatEffectContext ctx) {
        if (ctx.defenderRef() == null || !ctx.defenderRef().isValid()) return;

        // Get defender position
        var transformType = com.hypixel.hytale.server.core.modules.entity.component.TransformComponent.getComponentType();
        var defenderTransform = ctx.store().getComponent(ctx.defenderRef(), transformType);
        if (defenderTransform == null) return;

        var defenderPos = defenderTransform.getPosition();
        float rangeSquared = CHAIN_RANGE * CHAIN_RANGE;

        // Find nearest entity with health that isn't the defender or attacker
        final Ref<EntityStore>[] nearestRef = new Ref[]{null};
        final double[] nearestDistSq = {Double.MAX_VALUE};

        ctx.store().forEachChunk(transformType, (chunk, cmdBuf) -> {
            for (int i = 0; i < chunk.size(); i++) {
                Ref<EntityStore> ref = chunk.getReferenceTo(i);
                if (ref == null || !ref.isValid()) continue;
                if (ref.equals(ctx.defenderRef())) continue; // Skip self

                // Skip the attacker
                if (ctx.attackerRef() != null && ref.equals(ctx.attackerRef())) continue;

                // Check if entity has health (is a living entity)
                var statMap = ctx.store().getComponent(ref, EntityStatMap.getComponentType());
                if (statMap == null) continue;

                var transform = chunk.getComponent(i, transformType);
                if (transform == null) continue;

                var pos = transform.getPosition();
                double dx = pos.x - defenderPos.x;
                double dy = pos.y - defenderPos.y;
                double dz = pos.z - defenderPos.z;
                double distSq = dx * dx + dy * dy + dz * dz;

                if (distSq < rangeSquared && distSq < nearestDistSq[0]) {
                    nearestDistSq[0] = distSq;
                    nearestRef[0] = ref;
                }
            }
        });

        if (nearestRef[0] != null) {
            // Apply chained shock to the nearest entity
            UUID targetUuid = resolveUuid(nearestRef[0], ctx.store());
            if (targetUuid != null) {
                // Apply shock at reduced strength (50% magnitude, 50% duration)
                float chainMagnitude = 10f * CHAIN_STRENGTH; // 50% of typical 10% damage increase
                float chainDuration = 3.0f * CHAIN_STRENGTH; // 50% of normal 3s
                ailmentTracker.applyAilment(targetUuid,
                    AilmentState.shock(chainMagnitude, chainDuration, ctx.attackerUuid()));
                LOGGER.atFine().log("Thundergod: shock chained to nearby entity (%.1f blocks, 50%% strength)",
                    Math.sqrt(nearestDistSq[0]));
            }
        }
    }

    private UUID resolveUuid(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        try {
            // Players: use PlayerRef UUID
            var playerRef = store.getComponent(ref,
                com.hypixel.hytale.server.core.universe.PlayerRef.getComponentType());
            if (playerRef != null) return playerRef.getUuid();

            // Mobs: use UUIDComponent (Hytale assigns UUID to every entity)
            var uuidComponent = store.getComponent(ref,
                com.hypixel.hytale.server.core.entity.UUIDComponent.getComponentType());
            if (uuidComponent != null) return uuidComponent.getUuid();
        } catch (Exception ignored) {}
        return null;
    }

    @Override
    public void cleanup(@Nonnull UUID playerId) {
        lastChainTime.remove(playerId);
    }
}
