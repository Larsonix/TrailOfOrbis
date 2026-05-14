package io.github.larsonix.trailoforbis.mobs.modifiers;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.mobs.component.MobScalingComponent;
import io.github.larsonix.trailoforbis.mobs.speed.RPGApplicationEffects;
import io.github.larsonix.trailoforbis.mobs.speed.RPGEntityEffect;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.logging.Level;

/**
 * Handles death-triggered modifier effects.
 *
 * <p>Called from RPGDamageSystem when a modified mob's isDying flag is set.
 * Death effects are deferred or queued — never executed synchronously
 * during damage processing.
 *
 * <ul>
 *   <li><b>Volatile</b>: Queues a delayed AoE explosion (processed by tick system)</li>
 *   <li><b>Summoner</b>: Despawns tracked minions via world.execute()</li>
 * </ul>
 */
public class MobModifierDeathHandler {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final TrailOfOrbis plugin;
    private final MobModifierConfig config;

    public MobModifierDeathHandler(@Nonnull TrailOfOrbis plugin, @Nonnull MobModifierConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    /**
     * Called when a modified mob dies. Checks for death-trigger modifiers
     * and queues their effects.
     *
     * <p>IMPORTANT: This runs during damage event processing. Do NOT modify
     * the entity store directly — defer via world.execute() or queue in tick system.
     */
    public void onMobDeath(
            @Nonnull Ref<EntityStore> mobRef,
            @Nonnull MobModifierComponent modComp,
            @Nonnull MobScalingComponent scaling,
            @Nonnull Store<EntityStore> store) {

        List<ModifierType> modifiers = modComp.getModifiers();

        for (ModifierType mod : modifiers) {
            switch (mod) {
                case VOLATILE -> handleVolatile(mobRef, scaling, store);
                case VENOMOUS -> handleVenomousCloud(mobRef, scaling, store);
                case RALLYING -> handleRallying(mobRef, store);
                case SUMMONER -> handleSummonerCleanup(modComp, store);
                default -> {}
            }
        }
    }

    /**
     * Volatile: Queues a delayed AoE explosion at the mob's death position.
     *
     * <p>The explosion is added to {@link MobModifierTickSystem}'s pending
     * explosion queue. The tick system processes it after the configured
     * charge delay (default 2.0 seconds). No raw threads, no executors —
     * everything runs on the world thread via the tick system.
     */
    private void handleVolatile(
            @Nonnull Ref<EntityStore> mobRef,
            @Nonnull MobScalingComponent scaling,
            @Nonnull Store<EntityStore> store) {

        TransformComponent transform = store.getComponent(mobRef, TransformComponent.getComponentType());
        if (transform == null || scaling.getStats() == null) return;

        MobModifierConfig.ModifierSettings settings = config.getModifierSettings(ModifierType.VOLATILE);
        float damage = (float) (scaling.getStats().physicalDamage() * settings.getExplosion_damage_percent());
        if (damage <= 0) return;

        Vector3d deathPos = new Vector3d(transform.getPosition());
        double radius = settings.getExplosion_radius();
        long triggerTime = System.currentTimeMillis() + (long) (settings.getCharge_delay_seconds() * 1000);

        MobModifierManager modManager = plugin.getMobModifierManager();
        if (modManager != null) {
            modManager.queuePendingExplosion(new PendingExplosion(deathPos, damage, radius, triggerTime));
            LOGGER.at(Level.FINE).log("[MobModifier] Volatile death: queued %.1f damage explosion at (%.0f, %.0f, %.0f) in %.1fs",
                damage, deathPos.x, deathPos.y, deathPos.z, settings.getCharge_delay_seconds());
        }
    }

    /**
     * Venomous: Creates a poison cloud at the death position.
     *
     * <p>The cloud is an area damage zone that deals DoT to players standing
     * in it for the configured duration (default 5 seconds).
     */
    private void handleVenomousCloud(
            @Nonnull Ref<EntityStore> mobRef,
            @Nonnull MobScalingComponent scaling,
            @Nonnull Store<EntityStore> store) {

        TransformComponent transform = store.getComponent(mobRef, TransformComponent.getComponentType());
        if (transform == null || scaling.getStats() == null) return;

        MobModifierConfig.ModifierSettings settings = config.getModifierSettings(ModifierType.VENOMOUS);
        float cloudDps = (float) (scaling.getStats().physicalDamage() * settings.getDeath_cloud_dps_percent());
        double cloudDuration = settings.getDeath_cloud_duration_seconds();
        if (cloudDps <= 0 || cloudDuration <= 0) return;

        Vector3d deathPos = new Vector3d(transform.getPosition());
        long expiryTime = System.currentTimeMillis() + (long) (cloudDuration * 1000);

        MobModifierManager modManager = plugin.getMobModifierManager();
        if (modManager != null) {
            modManager.addAreaDamageZone(new MobModifierManager.AreaDamageZone(
                deathPos, cloudDps, 3.0, expiryTime));
            LOGGER.at(Level.FINE).log("[MobModifier] Venomous death cloud: %.1f DPS for %.0fs at (%.0f, %.0f, %.0f)",
                cloudDps, cloudDuration, deathPos.x, deathPos.y, deathPos.z);
        }
    }

    /**
     * Rallying: Buffs nearby non-elite mobs with speed on death.
     *
     * <p>Applies a speed boost via short-duration EntityEffect to all nearby
     * mobs within radius. The damage bonus is tracked via the Pack Leader
     * buff system in MobModifierManager (shared infrastructure).
     *
     * <p>Uses world.execute() since we're inside damage event processing.
     */
    private void handleRallying(
            @Nonnull Ref<EntityStore> mobRef,
            @Nonnull Store<EntityStore> store) {

        TransformComponent transform = store.getComponent(mobRef, TransformComponent.getComponentType());
        if (transform == null) return;

        MobModifierConfig.ModifierSettings settings = config.getModifierSettings(ModifierType.RALLYING);
        double buffRadius = settings.getBuff_radius();
        float damageBonus = (float) settings.getDamage_bonus();
        float speedBonus = (float) settings.getSpeed_bonus();
        float buffDuration = (float) settings.getBuff_duration_seconds();

        if (buffRadius <= 0) return;

        Vector3d deathPos = new Vector3d(transform.getPosition());
        World world = store.getExternalData().getWorld();
        if (world == null) return;

        LOGGER.at(Level.FINE).log("[MobModifier] Rallying death: buffing mobs within %.0f blocks", buffRadius);

        // Defer buff application to after damage processing
        world.execute(() -> {
            Store<EntityStore> worldStore = world.getEntityStore().getStore();
            if (worldStore == null) return;

            double radiusSq = buffRadius * buffRadius;
            var scalingType = plugin.getMobScalingComponentType();
            var transformType = TransformComponent.getComponentType();

            worldStore.forEachChunk((chunk, cb) -> {
                for (int j = 0; j < chunk.size(); j++) {
                    Ref<EntityStore> nearbyRef = chunk.getReferenceTo(j);
                    if (nearbyRef == null || !nearbyRef.isValid()) continue;
                    if (nearbyRef.equals(mobRef)) continue;

                    MobScalingComponent nearbyScaling = worldStore.getComponent(nearbyRef, scalingType);
                    if (nearbyScaling == null || nearbyScaling.isDying()) continue;

                    var classification = nearbyScaling.getClassification();
                    if (classification == io.github.larsonix.trailoforbis.mobs.classification.RPGMobClass.ELITE
                        || classification == io.github.larsonix.trailoforbis.mobs.classification.RPGMobClass.BOSS) continue;

                    TransformComponent nearbyTransform = worldStore.getComponent(nearbyRef, transformType);
                    if (nearbyTransform == null) continue;

                    Vector3d nearbyPos = nearbyTransform.getPosition();
                    double dx = nearbyPos.x - deathPos.x;
                    double dy = nearbyPos.y - deathPos.y;
                    double dz = nearbyPos.z - deathPos.z;
                    if (dx * dx + dy * dy + dz * dz > radiusSq) continue;

                    // Apply speed buff via EntityEffect
                    if (speedBonus > 0) {
                        EffectControllerComponent effects = worldStore.getComponent(
                            nearbyRef, EffectControllerComponent.getComponentType());
                        if (effects != null) {
                            RPGEntityEffect rallyEffect = createRallyingEffect(speedBonus, buffDuration);
                            effects.addEffect(nearbyRef, rallyEffect, worldStore);
                        }
                    }

                    // Mark for damage bonus
                    if (damageBonus > 0) {
                        MobModifierManager modManager = plugin.getMobModifierManager();
                        if (modManager != null) {
                            modManager.markPackLeaderBuffed(nearbyRef.hashCode(), damageBonus);
                        }
                    }
                }
            });
        });
    }

    /**
     * Creates a temporary speed + visual effect for Rallying buff.
     */
    @Nonnull
    private RPGEntityEffect createRallyingEffect(float speedBonus, float duration) {
        RPGApplicationEffects appEffects = RPGApplicationEffects.create();
        appEffects.withSpeed(1.0f + speedBonus);
        appEffects.withTint(
            RPGApplicationEffects.colorFromHex("#AA2222"),
            RPGApplicationEffects.colorFromHex("#FF4444")
        );

        RPGEntityEffect effect = new RPGEntityEffect("rpg_mod_rallying_buff_" + System.nanoTime());
        effect.setApplicationEffects(appEffects);
        effect.setInfinite(false);
        effect.setDuration(duration);
        effect.setName(null);
        effect.setDebuff(false);
        effect.setOverlapBehavior(com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior.OVERWRITE);
        effect.setRemovalBehavior(com.hypixel.hytale.server.core.asset.type.entityeffect.config.RemovalBehavior.COMPLETE);
        return effect;
    }

    /**
     * Summoner: Despawns all tracked minions when the summoner dies.
     *
     * <p>Uses world.execute() to defer entity removal since we're inside
     * damage event processing and can't modify the store directly.
     */
    private void handleSummonerCleanup(
            @Nonnull MobModifierComponent modComp,
            @Nonnull Store<EntityStore> store) {

        List<Ref<EntityStore>> minions = modComp.getSummonedMinions();
        if (minions.isEmpty()) return;

        World world = store.getExternalData().getWorld();
        if (world == null) return;

        // Capture valid refs before deferring
        List<Ref<EntityStore>> validMinions = minions.stream()
            .filter(ref -> ref != null && ref.isValid())
            .toList();
        minions.clear();

        if (validMinions.isEmpty()) return;

        LOGGER.at(Level.FINE).log("[MobModifier] Summoner death: despawning %d minions", validMinions.size());

        world.execute(() -> {
            Store<EntityStore> worldStore = world.getEntityStore().getStore();
            if (worldStore == null) return;
            for (Ref<EntityStore> minionRef : validMinions) {
                if (minionRef.isValid()) {
                    try {
                        worldStore.removeEntity(minionRef, RemoveReason.REMOVE);
                    } catch (Exception e) {
                        LOGGER.atFine().log("[MobModifier] Failed to despawn minion: %s", e.getMessage());
                    }
                }
            }
        });
    }

    /**
     * Pending explosion data for the Volatile modifier.
     * Stored in MobModifierManager and processed by MobModifierTickSystem.
     */
    public record PendingExplosion(
        @Nonnull Vector3d position,
        float damage,
        double radius,
        long triggerTimeMs
    ) {
        public boolean isReady() {
            return System.currentTimeMillis() >= triggerTimeMs;
        }
    }
}
