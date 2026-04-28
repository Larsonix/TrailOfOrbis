package io.github.larsonix.trailoforbis.compat;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Rewrites hex spell damage source from EnvironmentSource to EntitySource (caster).
 *
 * <p>Runs in FilterDamageGroup — the same group as Hexcode's Erode/Fortify systems —
 * because hex spell damage dispatched via {@code commandBuffer.invoke()} from glyph
 * execution only reaches systems in this group. RPGDamageSystem (ungrouped) never
 * sees hex damage.
 *
 * <p>At damage time, the caster is necessarily within spell range of the target,
 * so nearest-player attribution is accurate even with many players online.
 * The rewritten EntitySource persists to DeathComponent, enabling all death-time
 * systems (XP, loot, realm kills, stone drops, map drops) to attribute the kill.
 */
public class HexDamageAttributionSystem extends DamageEventSystem {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    @Override
    @Nonnull
    public Query<EntityStore> getQuery() {
        // Match all entities — we filter by source type in handle() (fast instanceof check)
        return Query.any();
    }

    @Nullable
    @Override
    public SystemGroup<EntityStore> getGroup() {
        // Same group as Erode/Fortify — confirmed to receive hex spell damage
        return DamageModule.get().getFilterDamageGroup();
    }

    @Override
    public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
            @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> buffer,
            @Nonnull Damage damage) {

        // Fast path: skip non-hex damage (vast majority of damage events)
        if (!(damage.getSource() instanceof Damage.EnvironmentSource envSource)) {
            return;
        }
        if (!HexcodeSpellConfig.isHexSpellSource(envSource.getType())) {
            return;
        }

        // Already rewritten by a previous hit on this entity (shouldn't happen, but guard)
        if (damage.getSource() instanceof Damage.EntitySource) {
            return;
        }

        Ref<EntityStore> targetRef = chunk.getReferenceTo(index);
        if (targetRef == null || !targetRef.isValid()) {
            return;
        }

        // Find the nearest player in the same world/store — at damage time,
        // the caster is within spell range of the target
        PlayerRef caster = findNearestPlayer(targetRef, store);
        if (caster == null) {
            LOGGER.atInfo().log("[HexAttrib] %s: no player found near target", envSource.getType());
            return;
        }

        Ref<EntityStore> casterEntityRef = store.getExternalData().getRefFromUUID(caster.getUuid());
        if (casterEntityRef == null || !casterEntityRef.isValid()) {
            return;
        }

        // Rewrite source: EnvironmentSource → EntitySource(caster)
        // This persists to DeathComponent, enabling XP/loot/realm kill attribution
        damage.setSource(new Damage.EntitySource(casterEntityRef));

        LOGGER.atInfo().log("[HexAttrib] %s: attributed to player %s",
            envSource.getType(), caster.getUuid().toString().substring(0, 8));
    }

    /**
     * Finds the nearest connected player to the target entity in the same store.
     * Only considers players whose entity ref is valid in this store (same world).
     */
    @Nullable
    private PlayerRef findNearestPlayer(@Nonnull Ref<EntityStore> targetRef,
            @Nonnull Store<EntityStore> store) {

        TransformComponent targetTc = store.getComponent(targetRef, TransformComponent.getComponentType());
        if (targetTc == null) {
            return null;
        }
        Vector3d targetPos = targetTc.getPosition();

        PlayerRef nearest = null;
        double nearestDistSq = Double.MAX_VALUE;

        for (PlayerRef playerRef : Universe.get().getPlayers()) {
            // getRefFromUUID returns null if the player isn't in this store/world
            Ref<EntityStore> playerEntityRef = store.getExternalData().getRefFromUUID(playerRef.getUuid());
            if (playerEntityRef == null || !playerEntityRef.isValid()) {
                continue;
            }

            TransformComponent playerTc = store.getComponent(playerEntityRef, TransformComponent.getComponentType());
            if (playerTc == null) {
                continue;
            }

            Vector3d playerPos = playerTc.getPosition();
            double dx = playerPos.getX() - targetPos.getX();
            double dy = playerPos.getY() - targetPos.getY();
            double dz = playerPos.getZ() - targetPos.getZ();
            double distSq = dx * dx + dy * dy + dz * dz;

            if (distSq < nearestDistSq) {
                nearestDistSq = distSq;
                nearest = playerRef;
            }
        }

        return nearest;
    }
}
