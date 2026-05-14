package io.github.larsonix.trailoforbis.mobs.modifiers;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.QuerySystem;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.modules.entity.component.EntityScaleComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.mobs.component.MobScalingComponent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.logging.Level;

/**
 * ECS tick system for behavioral mob modifiers.
 *
 * <p>Processes all mobs with both {@link MobScalingComponent} and {@link MobModifierComponent},
 * executing runtime behaviors at 0.5-second intervals.
 *
 * <p>Implemented behaviors:
 * <ul>
 *   <li><b>Enraged</b>: One-time HP threshold → speed + scale EntityEffect</li>
 *   <li><b>Regenerating</b>: Heal if not damaged for N seconds</li>
 *   <li><b>Frost Aura</b>: Slow nearby players (20%, 6 block radius)</li>
 *   <li><b>Frozen</b>: Lighter slow (15%, 4 block radius)</li>
 *   <li><b>Thunderous</b>: Periodic lightning strike at nearest player</li>
 *   <li><b>Summoner</b>: Spawn minions at 60%/30% HP thresholds</li>
 * </ul>
 *
 * <p>All EntityEffect assets (enrage, slow auras, pack leader speed) are registered by
 * {@link MobModifierEffectRegistry} during plugin init. This system only looks them up by ID.
 * Each behavior handler is a separate private method for maintainability.
 */
public class MobModifierTickSystem extends TickingSystem<EntityStore>
    implements QuerySystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final float TICK_INTERVAL = 0.5f;

    private final TrailOfOrbis plugin;
    private final ComponentType<EntityStore, MobScalingComponent> scalingType;
    private final ComponentType<EntityStore, MobModifierComponent> modifierType;
    private final ComponentType<EntityStore, EntityStatMap> statMapType;
    private final ComponentType<EntityStore, DeathComponent> deathType;
    private final ComponentType<EntityStore, TransformComponent> transformType;

    private Archetype<EntityStore> query = null;
    private float accumulator = 0;
    private int healthStatIndex = -1;

    public MobModifierTickSystem(@Nonnull TrailOfOrbis plugin) {
        this.plugin = plugin;
        this.scalingType = MobScalingComponent.getComponentType();
        this.modifierType = MobModifierComponent.getComponentType();
        this.statMapType = EntityStatMap.getComponentType();
        this.deathType = DeathComponent.getComponentType();
        this.transformType = TransformComponent.getComponentType();
    }

    @Override
    public Query<EntityStore> getQuery() {
        if (query == null) {
            query = Archetype.of(scalingType, modifierType);
        }
        return query;
    }

    @Override
    public void tick(float dt, int tick, @Nonnull Store<EntityStore> store) {
        MobModifierManager modManager = plugin.getMobModifierManager();
        if (modManager == null || !modManager.isEnabled()) return;

        if (healthStatIndex < 0) {
            healthStatIndex = EntityStatType.getAssetMap().getIndex("Health");
        }

        // Process pending Volatile explosions EVERY tick (not gated by interval)
        processPendingExplosions(modManager, store);

        // Process active area damage zones (Blazing trail, Venomous cloud) EVERY tick
        processAreaDamageZones(modManager, store);

        // Gate behavioral modifier processing to 0.5s intervals
        accumulator += dt;
        if (accumulator < TICK_INTERVAL) return;
        accumulator -= TICK_INTERVAL;

        // Clear Pack Leader buffs each cycle — they'll be re-applied by active leaders
        modManager.clearPackLeaderBuffs();

        World world = store.getExternalData().getWorld();

        store.forEachChunk(tick, (chunk, commandBuffer) -> {
            processChunk(chunk, store, commandBuffer, modManager, world);
        });
    }

    /**
     * Processes pending Volatile explosions that have passed their charge delay.
     * Runs every tick for timing accuracy.
     */
    private void processPendingExplosions(
            @Nonnull MobModifierManager modManager,
            @Nonnull Store<EntityStore> store) {

        var pendingQueue = modManager.getPendingExplosions();
        if (pendingQueue.isEmpty()) return;

        World world = store.getExternalData().getWorld();
        if (world == null) return;

        // Process all ready explosions
        var iterator = pendingQueue.iterator();
        while (iterator.hasNext()) {
            MobModifierDeathHandler.PendingExplosion explosion = iterator.next();
            if (explosion.isReady()) {
                iterator.remove();
                executeVolatileExplosion(explosion, store, world);
            }
        }
    }

    /**
     * Fires a Volatile explosion at the recorded position.
     * Deals damage to all players within radius via DamageSystems.executeDamage
     * so all player defenses apply (armor, resistance, blocking, energy shield).
     */
    private void executeVolatileExplosion(
            @Nonnull MobModifierDeathHandler.PendingExplosion explosion,
            @Nonnull Store<EntityStore> store,
            @Nonnull World world) {

        double radiusSq = explosion.radius() * explosion.radius();
        Vector3d pos = explosion.position();

        for (PlayerRef playerRef : world.getPlayerRefs()) {
            Ref<EntityStore> playerEntityRef = playerRef.getReference();
            if (playerEntityRef == null || !playerEntityRef.isValid()) continue;

            TransformComponent playerTransform = store.getComponent(playerEntityRef, transformType);
            if (playerTransform == null) continue;

            Vector3d playerPos = playerTransform.getPosition();
            double dx = playerPos.x - pos.x;
            double dy = playerPos.y - pos.y;
            double dz = playerPos.z - pos.z;
            if (dx * dx + dy * dy + dz * dz > radiusSq) continue;

            // Deal explosion damage — non-lethal (floor at 1 HP)
            // Using direct stat subtraction because the mob source entity is already dead
            // and can't be used as a Damage.EntitySource
            EntityStatMap playerStats = store.getComponent(playerEntityRef, statMapType);
            if (playerStats != null && healthStatIndex >= 0) {
                EntityStatValue health = playerStats.get(healthStatIndex);
                if (health != null && health.get() > 0) {
                    float newHp = Math.max(1f, health.get() - explosion.damage());
                    playerStats.setStatValue(healthStatIndex, newHp);
                    LOGGER.at(Level.FINE).log("[MobModifier] Volatile explosion hit player for %.1f damage",
                        explosion.damage());
                }
            }
        }
    }

    private void processChunk(
            @Nonnull ArchetypeChunk<EntityStore> chunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull MobModifierManager modManager,
            @Nullable World world) {

        MobModifierConfig config = modManager.getConfig();
        int size = chunk.size();

        for (int i = 0; i < size; i++) {
            Ref<EntityStore> mobRef = chunk.getReferenceTo(i);
            if (mobRef == null || !mobRef.isValid()) continue;
            if (store.getComponent(mobRef, deathType) != null) continue;

            MobScalingComponent scaling = chunk.getComponent(i, scalingType);
            if (scaling == null || scaling.isDying()) continue;

            MobModifierComponent modComp = chunk.getComponent(i, modifierType);
            if (modComp == null || !modComp.hasAnyTickable()) continue;

            TransformComponent transform = store.getComponent(mobRef, transformType);
            Vector3d mobPos = transform != null ? transform.getPosition() : null;

            // ===== ENRAGED =====
            if (modComp.hasModifier(ModifierType.ENRAGED) && !modComp.isEnrageTriggered()) {
                processEnraged(mobRef, modComp, store, config);
            }

            // ===== REGENERATING =====
            if (modComp.hasModifier(ModifierType.REGENERATING)) {
                processRegenerating(mobRef, modComp, store, config);
            }

            // ===== FROST AURA =====
            if (modComp.hasModifier(ModifierType.FROST_AURA) && mobPos != null && world != null) {
                applySlowToNearbyPlayers(mobPos, store, world,
                    config.getModifierSettings(ModifierType.FROST_AURA).getAura_radius(),
                    config.getModifierSettings(ModifierType.FROST_AURA).getSlow_percent(),
                    MobModifierEffectRegistry.FROST_AURA_SLOW_ID);
            }

            // ===== FROZEN (proximity slow) =====
            if (modComp.hasModifier(ModifierType.FROZEN) && mobPos != null && world != null) {
                applySlowToNearbyPlayers(mobPos, store, world,
                    config.getModifierSettings(ModifierType.FROZEN).getAura_radius(),
                    config.getModifierSettings(ModifierType.FROZEN).getSlow_percent(),
                    MobModifierEffectRegistry.FROZEN_SLOW_ID);
            }

            // ===== THUNDEROUS =====
            if (modComp.hasModifier(ModifierType.THUNDEROUS) && mobPos != null && world != null) {
                processThunderous(mobRef, modComp, scaling, mobPos, store, world, commandBuffer, config);
            }

            // ===== SUMMONER =====
            if (modComp.hasModifier(ModifierType.SUMMONER) && world != null) {
                processSummoner(mobRef, modComp, scaling, store, world, config);
            }

            // ===== PACK LEADER =====
            if (modComp.hasModifier(ModifierType.PACK_LEADER) && mobPos != null && world != null) {
                processPackLeader(mobRef, mobPos, store, world, modManager, config);
            }

            // ===== BLAZING (fire trail) =====
            if (modComp.hasModifier(ModifierType.BLAZING) && mobPos != null && scaling.getStats() != null) {
                processBlazingTrail(modComp, mobPos, scaling, modManager, config);
            }
        }
    }

    // ==================== ENRAGED ====================

    private void processEnraged(
            @Nonnull Ref<EntityStore> mobRef,
            @Nonnull MobModifierComponent modComp,
            @Nonnull Store<EntityStore> store,
            @Nonnull MobModifierConfig config) {

        EntityStatMap statMap = store.getComponent(mobRef, statMapType);
        if (statMap == null || healthStatIndex < 0) return;

        EntityStatValue healthStat = statMap.get(healthStatIndex);
        if (healthStat == null) return;

        float currentHp = healthStat.get();
        float maxHp = healthStat.getMax();
        if (maxHp <= 0) return;

        MobModifierConfig.ModifierSettings settings = config.getModifierSettings(ModifierType.ENRAGED);
        if (currentHp / maxHp > settings.getHp_threshold()) return;

        // Trigger enrage — one time only
        modComp.setEnrageTriggered(true);

        // Apply enrage visual + speed via pre-registered effect
        EffectControllerComponent effectController = store.getComponent(
            mobRef, EffectControllerComponent.getComponentType());
        if (effectController != null) {
            int effectIndex = EntityEffect.getAssetMap().getIndex(MobModifierEffectRegistry.ENRAGE_EFFECT_ID);
            if (effectIndex != Integer.MIN_VALUE) {
                EntityEffect enrageEffect = EntityEffect.getAssetMap().getAsset(effectIndex);
                if (enrageEffect != null) {
                    effectController.addEffect(mobRef, enrageEffect, store);
                }
            }
        }

        // Scale increase
        float scaleIncrease = (float) settings.getScale_increase();
        if (scaleIncrease > 0) {
            EntityScaleComponent scaleComp = store.getComponent(mobRef,
                EntityScaleComponent.getComponentType());
            if (scaleComp != null) {
                scaleComp.setScale(scaleComp.getScale() + scaleIncrease);
            }
        }

        LOGGER.at(Level.FINE).log("[MobModifier] Enraged triggered at %.0f%% HP", (currentHp / maxHp) * 100);
    }

    // ==================== REGENERATING ====================

    private void processRegenerating(
            @Nonnull Ref<EntityStore> mobRef,
            @Nonnull MobModifierComponent modComp,
            @Nonnull Store<EntityStore> store,
            @Nonnull MobModifierConfig config) {

        MobModifierConfig.ModifierSettings settings = config.getModifierSettings(ModifierType.REGENERATING);
        long idleDelayMs = (long) (settings.getIdle_delay_seconds() * 1000);
        long now = System.currentTimeMillis();

        if (now - modComp.getLastDamageTimestamp() < idleDelayMs) return;

        EntityStatMap statMap = store.getComponent(mobRef, statMapType);
        if (statMap == null || healthStatIndex < 0) return;

        EntityStatValue healthStat = statMap.get(healthStatIndex);
        if (healthStat == null) return;

        float currentHp = healthStat.get();
        float maxHp = healthStat.getMax();
        if (currentHp <= 0 || currentHp >= maxHp) return;

        float healAmount = (float) (maxHp * settings.getHeal_percent_per_second() * TICK_INTERVAL);
        statMap.setStatValue(healthStatIndex, Math.min(currentHp + healAmount, maxHp));
    }

    // ==================== THUNDEROUS ====================

    /**
     * Fires a lightning strike at the nearest player on cooldown.
     *
     * <p>Damage is fired through {@link DamageSystems#executeDamage} so it flows
     * through the full RPG combat pipeline: armor, elemental resistance, blocking,
     * energy shield, death recap. The mob entity ref is used as the source for
     * kill attribution.
     *
     * <p>Uses {@link DamageCause#PHYSICAL} as the cause — our RPGDamageSystem
     * processes it through the standard mob-to-player path with full defenses.
     */
    private void processThunderous(
            @Nonnull Ref<EntityStore> mobRef,
            @Nonnull MobModifierComponent modComp,
            @Nonnull MobScalingComponent scaling,
            @Nonnull Vector3d mobPos,
            @Nonnull Store<EntityStore> store,
            @Nonnull World world,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull MobModifierConfig config) {

        MobModifierConfig.ModifierSettings settings = config.getModifierSettings(ModifierType.THUNDEROUS);
        long cooldownMs = (long) (settings.getStrike_cooldown_seconds() * 1000);
        long now = System.currentTimeMillis();

        if (now - modComp.getLastLightningTimestamp() < cooldownMs) return;

        // Find nearest player within 20 blocks
        PlayerRef nearestPlayer = findNearestPlayer(mobPos, store, world, 20.0);
        if (nearestPlayer == null) return;

        Ref<EntityStore> playerRef = nearestPlayer.getReference();
        if (playerRef == null || !playerRef.isValid()) return;

        // Mark cooldown
        modComp.setLastLightningTimestamp(now);

        // Calculate strike damage
        if (scaling.getStats() == null) return;
        float strikeDamage = (float) (scaling.getStats().physicalDamage() * settings.getStrike_damage_percent());
        if (strikeDamage <= 0) return;

        // Fire damage through the combat pipeline using mob as source
        Damage.Source source = new Damage.EntitySource(mobRef);
        Damage dmgEvent = new Damage(source, DamageCause.PHYSICAL, strikeDamage);
        DamageSystems.executeDamage(playerRef, commandBuffer, dmgEvent);

        LOGGER.at(Level.FINE).log("[MobModifier] Thunderous strike: %.1f damage via combat pipeline", strikeDamage);
    }

    // ==================== SUMMONER ====================

    /**
     * Spawns minions at HP thresholds (60% and 30%).
     *
     * <p>CRITICAL: Spawning is deferred via {@code world.execute()} because calling
     * {@link NPCPlugin#spawnEntity} during ECS chunk iteration throws
     * {@code IllegalStateException} ("Store is currently processing!").
     * This is the same pattern used by {@link io.github.larsonix.trailoforbis.mobs.systems.DeferredSpawnSystem}.
     */
    private void processSummoner(
            @Nonnull Ref<EntityStore> mobRef,
            @Nonnull MobModifierComponent modComp,
            @Nonnull MobScalingComponent scaling,
            @Nonnull Store<EntityStore> store,
            @Nonnull World world,
            @Nonnull MobModifierConfig config) {

        EntityStatMap statMap = store.getComponent(mobRef, statMapType);
        if (statMap == null || healthStatIndex < 0) return;

        EntityStatValue healthStat = statMap.get(healthStatIndex);
        if (healthStat == null) return;

        float currentHp = healthStat.get();
        float maxHp = healthStat.getMax();
        if (maxHp <= 0 || currentHp <= 0) return;
        float hpPercent = currentHp / maxHp;

        MobModifierConfig.ModifierSettings settings = config.getModifierSettings(ModifierType.SUMMONER);
        int maxSummons = settings.getMax_summons();
        int summonCount = settings.getSummon_count();
        String roleName = scaling.getRoleName();

        if (roleName == null || roleName.isEmpty()) return;

        boolean shouldSpawn = false;

        // Check threshold 1 (60%)
        if (!modComp.isSummonThreshold60Triggered() && hpPercent <= settings.getThreshold_1_hp()) {
            modComp.setSummonThreshold60Triggered(true);
            shouldSpawn = true;
        }

        // Check threshold 2 (30%)
        if (!modComp.isSummonThreshold30Triggered() && hpPercent <= settings.getThreshold_2_hp()) {
            modComp.setSummonThreshold30Triggered(true);
            shouldSpawn = true;
        }

        if (!shouldSpawn) return;

        // Capture values for deferred lambda
        int currentSummons = modComp.activeSummonCount();
        int toSpawn = Math.min(summonCount, maxSummons - currentSummons);
        if (toSpawn <= 0) return;

        TransformComponent summonerTransform = store.getComponent(mobRef, transformType);
        if (summonerTransform == null) return;
        Vector3d basePos = new Vector3d(summonerTransform.getPosition());

        // Defer spawning to after ECS iteration completes
        final int finalToSpawn = toSpawn;
        final String finalRoleName = roleName;
        world.execute(() -> {
            spawnMinionsDeferred(modComp, finalRoleName, world, basePos, finalToSpawn);
        });
    }

    /**
     * Spawns N minions near a position. Called via {@code world.execute()} to avoid
     * ECS "Store is currently processing" errors.
     */
    private void spawnMinionsDeferred(
            @Nonnull MobModifierComponent modComp,
            @Nonnull String roleName,
            @Nonnull World world,
            @Nonnull Vector3d basePos,
            int toSpawn) {

        NPCPlugin npcPlugin = NPCPlugin.get();
        if (npcPlugin == null) return;

        int roleIndex = npcPlugin.getIndex(roleName);
        if (roleIndex < 0) {
            LOGGER.atFine().log("[MobModifier] Cannot spawn minion: role '%s' not found", roleName);
            return;
        }

        Store<EntityStore> worldStore = world.getEntityStore().getStore();
        if (worldStore == null) return;

        for (int i = 0; i < toSpawn; i++) {
            double angle = (2 * Math.PI / toSpawn) * i;
            double offsetX = Math.cos(angle) * 3.0;
            double offsetZ = Math.sin(angle) * 3.0;
            Vector3d spawnPos = new Vector3d(basePos.x + offsetX, basePos.y, basePos.z + offsetZ);

            try {
                var result = npcPlugin.spawnEntity(
                    worldStore, roleIndex, spawnPos, null, null, null, null);
                if (result != null && result.first() != null) {
                    modComp.addSummonedMinion(result.first());
                    LOGGER.at(Level.FINE).log("[MobModifier] Summoner spawned minion %d/%d at (%.0f, %.0f, %.0f)",
                        i + 1, toSpawn, spawnPos.x, spawnPos.y, spawnPos.z);
                }
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("[MobModifier] Failed to spawn minion");
            }
        }
    }

    // ==================== PACK LEADER ====================

    /**
     * Buffs nearby non-elite/non-boss mobs with speed bonus.
     * Also marks them in the manager for damage bonus lookup during combat.
     *
     * <p>Speed buff is applied via a short-duration EntityEffect (auto-expires).
     * Damage buff is tracked in MobModifierManager and read by
     * ConditionalMultiplierCalculator during damage calculation.
     */
    private void processPackLeader(
            @Nonnull Ref<EntityStore> leaderRef,
            @Nonnull Vector3d leaderPos,
            @Nonnull Store<EntityStore> store,
            @Nonnull World world,
            @Nonnull MobModifierManager modManager,
            @Nonnull MobModifierConfig config) {

        MobModifierConfig.ModifierSettings settings = config.getModifierSettings(ModifierType.PACK_LEADER);
        double radius = settings.getAura_radius();
        float speedBonus = (float) settings.getSpeed_bonus();
        float damageBonus = (float) settings.getDamage_bonus();

        if (radius <= 0) return;
        double radiusSq = radius * radius;

        int effectIndex = EntityEffect.getAssetMap().getIndex(MobModifierEffectRegistry.PACK_LEADER_SPEED_ID);

        // Iterate ALL mobs (not just modified ones) to find nearby allies to buff
        store.forEachChunk((chunk, cb) -> {
            for (int j = 0; j < chunk.size(); j++) {
                Ref<EntityStore> mobRef = chunk.getReferenceTo(j);
                if (mobRef == null || !mobRef.isValid()) continue;
                if (mobRef.equals(leaderRef)) continue; // Don't buff self

                // Must have MobScalingComponent (is a mob)
                MobScalingComponent mobScaling = store.getComponent(mobRef, scalingType);
                if (mobScaling == null || mobScaling.isDying()) continue;

                // Skip elite/boss mobs (design: only buffs normal mobs)
                var classification = mobScaling.getClassification();
                if (classification == io.github.larsonix.trailoforbis.mobs.classification.RPGMobClass.ELITE
                    || classification == io.github.larsonix.trailoforbis.mobs.classification.RPGMobClass.BOSS) continue;

                // Distance check
                TransformComponent mobTransform = store.getComponent(mobRef, transformType);
                if (mobTransform == null) continue;
                Vector3d mobPos = mobTransform.getPosition();
                double dx = mobPos.x - leaderPos.x;
                double dy = mobPos.y - leaderPos.y;
                double dz = mobPos.z - leaderPos.z;
                if (dx * dx + dy * dy + dz * dz > radiusSq) continue;

                // Apply speed buff via pre-registered short-duration effect
                if (effectIndex != Integer.MIN_VALUE && speedBonus > 0) {
                    EffectControllerComponent effects = store.getComponent(mobRef, EffectControllerComponent.getComponentType());
                    if (effects != null) {
                        EntityEffect speedEffect = EntityEffect.getAssetMap().getAsset(effectIndex);
                        if (speedEffect != null) {
                            effects.addEffect(mobRef, speedEffect, store);
                        }
                    }
                }

                // Mark for damage bonus (used by ConditionalMultiplierCalculator)
                if (damageBonus > 0) {
                    modManager.markPackLeaderBuffed(mobRef.hashCode(), damageBonus);
                }
            }
        });
    }

    // ==================== BLAZING TRAIL ====================

    /**
     * Creates area damage zones at the mob's past positions.
     * Each tick, if the mob moved more than 1 block, the previous position
     * becomes a fire trail zone that damages players for the configured duration.
     */
    private void processBlazingTrail(
            @Nonnull MobModifierComponent modComp,
            @Nonnull Vector3d currentPos,
            @Nonnull MobScalingComponent scaling,
            @Nonnull MobModifierManager modManager,
            @Nonnull MobModifierConfig config) {

        Vector3d lastPos = modComp.getLastTrailPosition();
        modComp.setLastTrailPosition(new Vector3d(currentPos));

        if (lastPos == null) return; // First tick — no trail yet

        // Only create trail if mob moved more than 1 block
        double dx = currentPos.x - lastPos.x;
        double dz = currentPos.z - lastPos.z;
        if (dx * dx + dz * dz < 1.0) return;

        MobModifierConfig.ModifierSettings settings = config.getModifierSettings(ModifierType.BLAZING);
        float trailDps = (float) (scaling.getStats().physicalDamage() * settings.getTrail_damage_percent());
        double trailDuration = settings.getTrail_duration_seconds();

        if (trailDps <= 0 || trailDuration <= 0) return;

        long expiryTime = System.currentTimeMillis() + (long) (trailDuration * 1000);
        modManager.addAreaDamageZone(new MobModifierManager.AreaDamageZone(
            new Vector3d(lastPos), trailDps, 1.5, expiryTime));
    }

    // ==================== AREA DAMAGE ZONES ====================

    /**
     * Processes all active area damage zones (Blazing trail, Venomous cloud).
     * Deals damage per tick to players within radius. Removes expired zones.
     * Uses direct stat subtraction (non-lethal, 1 HP floor) since the zone
     * source entity may be dead (Venomous cloud) or moved (Blazing trail).
     */
    private void processAreaDamageZones(
            @Nonnull MobModifierManager modManager,
            @Nonnull Store<EntityStore> store) {

        var zones = modManager.getAreaDamageZones();
        if (zones.isEmpty()) return;

        World world = store.getExternalData().getWorld();
        if (world == null) return;

        // Approximate dt for area damage (we run every game tick, ~0.05s at 20 TPS)
        float dt = 0.05f;

        var iterator = zones.iterator();
        while (iterator.hasNext()) {
            MobModifierManager.AreaDamageZone zone = iterator.next();
            if (zone.isExpired()) {
                iterator.remove();
                continue;
            }

            double radiusSq = zone.radius() * zone.radius();
            float damageThisTick = zone.damagePerSecond() * dt;
            if (damageThisTick <= 0) continue;

            for (PlayerRef playerRef : world.getPlayerRefs()) {
                Ref<EntityStore> playerEntityRef = playerRef.getReference();
                if (playerEntityRef == null || !playerEntityRef.isValid()) continue;

                TransformComponent playerTransform = store.getComponent(playerEntityRef, transformType);
                if (playerTransform == null) continue;

                Vector3d playerPos = playerTransform.getPosition();
                double pdx = playerPos.x - zone.position().x;
                double pdy = playerPos.y - zone.position().y;
                double pdz = playerPos.z - zone.position().z;
                if (pdx * pdx + pdy * pdy + pdz * pdz > radiusSq) continue;

                EntityStatMap playerStats = store.getComponent(playerEntityRef, statMapType);
                if (playerStats != null && healthStatIndex >= 0) {
                    EntityStatValue health = playerStats.get(healthStatIndex);
                    if (health != null && health.get() > 1f) {
                        float newHp = Math.max(1f, health.get() - damageThisTick);
                        playerStats.setStatValue(healthStatIndex, newHp);
                    }
                }
            }
        }
    }

    // ==================== Slow Aura Helpers ====================

    /**
     * Applies a pre-registered slow effect to nearby players.
     * Uses IGNORE overlap so multiple applications don't stack.
     */
    private void applySlowToNearbyPlayers(
            @Nonnull Vector3d mobPos,
            @Nonnull Store<EntityStore> store,
            @Nonnull World world,
            double radius,
            double slowPercent,
            @Nonnull String effectId) {

        if (radius <= 0 || slowPercent <= 0) return;
        double radiusSq = radius * radius;

        int effectIndex = EntityEffect.getAssetMap().getIndex(effectId);
        if (effectIndex == Integer.MIN_VALUE) return; // Effect not registered

        for (PlayerRef playerRef : world.getPlayerRefs()) {
            Ref<EntityStore> playerEntityRef = playerRef.getReference();
            if (playerEntityRef == null || !playerEntityRef.isValid()) continue;

            TransformComponent playerTransform = store.getComponent(playerEntityRef, transformType);
            if (playerTransform == null) continue;

            Vector3d playerPos = playerTransform.getPosition();
            double dx = playerPos.x - mobPos.x;
            double dy = playerPos.y - mobPos.y;
            double dz = playerPos.z - mobPos.z;
            if (dx * dx + dy * dy + dz * dz > radiusSq) continue;

            EffectControllerComponent playerEffects = store.getComponent(
                playerEntityRef, EffectControllerComponent.getComponentType());
            if (playerEffects != null) {
                // Retrieve pre-registered effect by index
                EntityEffect slowEffect = EntityEffect.getAssetMap().getAsset(effectIndex);
                if (slowEffect != null) {
                    playerEffects.addEffect(playerEntityRef, slowEffect, store);
                }
            }
        }
    }

    // ==================== Player Proximity Helpers ====================

    /**
     * Finds the nearest player within radius of a position.
     */
    @Nullable
    private PlayerRef findNearestPlayer(
            @Nonnull Vector3d pos,
            @Nonnull Store<EntityStore> store,
            @Nonnull World world,
            double radius) {

        double radiusSq = radius * radius;
        double nearestDistSq = Double.MAX_VALUE;
        PlayerRef nearest = null;

        for (PlayerRef playerRef : world.getPlayerRefs()) {
            Ref<EntityStore> entityRef = playerRef.getReference();
            if (entityRef == null || !entityRef.isValid()) continue;

            TransformComponent transform = store.getComponent(entityRef, transformType);
            if (transform == null) continue;

            Vector3d playerPos = transform.getPosition();
            double dx = playerPos.x - pos.x;
            double dy = playerPos.y - pos.y;
            double dz = playerPos.z - pos.z;
            double distSq = dx * dx + dy * dy + dz * dz;

            if (distSq <= radiusSq && distSq < nearestDistSq) {
                nearestDistSq = distSq;
                nearest = playerRef;
            }
        }

        return nearest;
    }

}
