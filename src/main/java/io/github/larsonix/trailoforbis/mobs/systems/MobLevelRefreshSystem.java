package io.github.larsonix.trailoforbis.mobs.systems;

import com.hypixel.hytale.logger.HytaleLogger;
import java.util.logging.Level;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.QuerySystem;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.movement.controllers.MotionController;
import com.hypixel.hytale.server.npc.role.Role;
import io.github.larsonix.trailoforbis.TrailOfOrbis;
import io.github.larsonix.trailoforbis.elemental.ElementType;
import io.github.larsonix.trailoforbis.elemental.ElementalStats;
import io.github.larsonix.trailoforbis.mobs.MobScalingConfig;
import io.github.larsonix.trailoforbis.mobs.MobScalingManager;
import io.github.larsonix.trailoforbis.mobs.calculator.DistanceBonusCalculator;
import io.github.larsonix.trailoforbis.mobs.calculator.PlayerLevelCalculator;
import io.github.larsonix.trailoforbis.mobs.classification.MobClassificationConfig;
import io.github.larsonix.trailoforbis.mobs.classification.RPGMobClass;
import io.github.larsonix.trailoforbis.mobs.component.MobScalingComponent;
import io.github.larsonix.trailoforbis.mobs.infobar.MobInfoFormatter;
import io.github.larsonix.trailoforbis.mobs.model.MobStats;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import io.github.larsonix.trailoforbis.mobs.speed.MobSpeedEffectManager;
import io.github.larsonix.trailoforbis.mobs.stats.MobStatGenerator;
import io.github.larsonix.trailoforbis.mobs.stats.MobStatProfile;
import io.github.larsonix.trailoforbis.maps.components.RealmMobComponent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicInteger;

public class MobLevelRefreshSystem extends TickingSystem<EntityStore>
    implements QuerySystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String RPG_HEALTH_KEY = "RPG_HEALTH_SCALING";

    private final TrailOfOrbis plugin;
    private final AtomicInteger tickCount = new AtomicInteger(0);

    private Archetype<EntityStore> query = null;

    private final ComponentType<EntityStore, TransformComponent> transformType;
    private final ComponentType<EntityStore, MobScalingComponent> scalingType;
    private final ComponentType<EntityStore, NPCEntity> npcType;
    private final ComponentType<EntityStore, DeathComponent> deathType;

    /** ComponentType for realm mobs - obtained from plugin, may be null if realms not initialized */
    @Nullable
    private ComponentType<EntityStore, RealmMobComponent> realmMobType;

    private int healthStatIndex = -1;

    private double proximityRadiusSquared;
    private int levelChangeThreshold;

    /** Maximum mobs to refresh per tick (0 = unlimited) */
    private static final int MAX_REFRESHES_PER_TICK = 5;

    /** Counter for refreshes this tick */
    private int refreshesThisTick = 0;


    public MobLevelRefreshSystem(@Nonnull TrailOfOrbis plugin) {
        this.plugin = plugin;
        this.transformType = TransformComponent.getComponentType();
        this.scalingType = MobScalingComponent.getComponentType();
        this.npcType = NPCEntity.getComponentType();
        this.deathType = DeathComponent.getComponentType();
    }

    @Override
    public Query<EntityStore> getQuery() {
        if (query == null) {
            query = Archetype.of(transformType, scalingType);
        }
        return query;
    }

    @Override
    public void tick(float dt, int tick, @Nonnull Store<EntityStore> store) {
        int currentTick = tickCount.incrementAndGet();

        // Skip processing during world shutdown to prevent race conditions with entity removal
        World world = store.getExternalData().getWorld();
        if (world == null || !world.isAlive()) {
            return;
        }

        // Lazy-init realm mob component type (realms may be initialized after mob systems)
        if (realmMobType == null) {
            realmMobType = plugin.getRealmMobComponentType();
        }

        MobScalingManager manager = plugin.getMobScalingManager();
        if (manager == null || !manager.isInitialized()) {
            return;
        }

        MobScalingConfig config = manager.getConfig();
        if (!config.isEnabled()) {
            return;
        }

        MobScalingConfig.DynamicRefreshConfig refreshConfig = config.getDynamicRefresh();
        if (!refreshConfig.isEnabled()) {
            return;
        }

        this.proximityRadiusSquared = refreshConfig.getPlayerProximityRadius() * refreshConfig.getPlayerProximityRadius();
        this.levelChangeThreshold = refreshConfig.getLevelChangeThreshold();

        int intervalTicks = (int) (refreshConfig.getIntervalSeconds() * 20);
        if (intervalTicks < 1) {
            intervalTicks = 1;
        }

        if (currentTick % intervalTicks != 0) {
            return;
        }

        PlayerLevelCalculator levelCalculator = manager.getPlayerLevelCalculator();
        DistanceBonusCalculator distanceCalculator = manager.getDistanceCalculator();
        MobStatGenerator statGenerator = manager.getStatGenerator();

        if (levelCalculator == null || distanceCalculator == null || statGenerator == null) {
            return;
        }

        final int[] stats = {0, 0, 0, 0};

        // Reset throttle counter at start of refresh tick
        refreshesThisTick = 0;

        store.forEachChunk(tick, (chunk, commandBuffer) -> {
            // Stop processing if we hit the throttle limit
            if (MAX_REFRESHES_PER_TICK > 0 && refreshesThisTick >= MAX_REFRESHES_PER_TICK) {
                return;
            }
            processChunk(chunk, store, manager, levelCalculator, distanceCalculator,
                        statGenerator, refreshConfig, stats);
        });

        if (plugin.getConfigManager().getRPGConfig().isDebugMode() && (stats[1] > 0 || stats[2] > 0)) {
            LOGGER.at(Level.FINE).log("Checked: %d, Updated: %d, Skipped (far): %d, Skipped (no change): %d",
                stats[0], stats[1], stats[2], stats[3]);
        }
    }

    private void processChunk(
        @Nonnull ArchetypeChunk<EntityStore> chunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull MobScalingManager manager,
        @Nonnull PlayerLevelCalculator levelCalculator,
        @Nonnull DistanceBonusCalculator distanceCalculator,
        @Nonnull MobStatGenerator statGenerator,
        @Nonnull MobScalingConfig.DynamicRefreshConfig refreshConfig,
        @Nonnull int[] stats
    ) {
        int size = chunk.size();

        for (int i = 0; i < size; i++) {
            stats[0]++;

            MobScalingComponent scaling = chunk.getComponent(i, scalingType);
            if (scaling == null) {
                continue;
            }

            Ref<EntityStore> mobRef = chunk.getReferenceTo(i);

            // Skip realm mobs - their level is fixed based on realm map, not player proximity
            if (realmMobType != null) {
                RealmMobComponent realmMob = store.getComponent(mobRef, realmMobType);
                if (realmMob != null && realmMob.getRealmId() != null) {
                    continue; // Skip - realm mob has fixed level
                }
            }

            TransformComponent transform = chunk.getComponent(i, transformType);
            if (transform == null) {
                continue;
            }

            // Skip dead or dying mobs - they should not have their stats refreshed
            if (store.getComponent(mobRef, deathType) != null) {
                continue;
            }

            // CRITICAL: Check isDying flag to catch mobs in the race condition window
            // (health=0 but DeathComponent not yet committed via CommandBuffer)
            if (scaling.isDying()) {
                continue;
            }

            // Additional safety: skip mobs with 0 or negative health
            // This catches the race condition where DeathComponent hasn't been added yet
            EntityStatMap statMap = store.getComponent(mobRef, EntityStatMap.getComponentType());
            if (statMap != null) {
                // Lazy init healthStatIndex if needed
                if (healthStatIndex < 0) {
                    healthStatIndex = EntityStatType.getAssetMap().getIndex("Health");
                }
                EntityStatValue healthStat = statMap.get(healthStatIndex);
                if (healthStat != null && healthStat.get() <= 0) {
                    continue;
                }
            }

            // Skip PASSIVE mobs — non-combat creatures don't need dynamic level refresh
            RPGMobClass classification = scaling.getClassification();
            if (classification == RPGMobClass.PASSIVE) {
                continue;
            }

            Vector3d mobPosition = transform.getPosition();

            if (!isNearAnyPlayer(mobPosition, store)) {
                stats[2]++;
                continue;
            }

            int currentLevel = scaling.getMobLevel();

            int newLevel = levelCalculator.calculateEffectiveLevel(mobPosition, store);

            if (Math.abs(newLevel - currentLevel) < levelChangeThreshold) {
                stats[3]++;
                continue;
            }

            // Throttle: stop if we've refreshed enough mobs this tick
            if (MAX_REFRESHES_PER_TICK > 0 && refreshesThisTick >= MAX_REFRESHES_PER_TICK) {
                return; // Exit chunk processing early
            }

            // Calculate distance from spawn (not origin) for correct level scaling
            World world = store.getExternalData().getWorld();
            double distanceFromSpawn = DistanceBonusCalculator.calculateDistanceFromSpawn(
                mobPosition.x, mobPosition.z, world);
            double newBonusPool = distanceCalculator.calculateBonusPool(distanceFromSpawn);

            // Apply classification stat multiplier (same as MobScalingSystem at spawn)
            MobClassificationConfig classConfig = manager.getClassificationService().getConfig();
            double statMultiplier = classConfig.getStatMultiplier(classification);

            long seed = System.nanoTime() ^ mobRef.hashCode();
            MobStatProfile profile;
            if (statMultiplier != 1.0) {
                profile = statGenerator.generateSpecial(newLevel, newBonusPool, statMultiplier, seed);
            } else {
                profile = statGenerator.generate(newLevel, newBonusPool, seed);
            }
            MobStats newStats = convertToLegacyStats(profile);

            scaling.setMobLevel(newLevel);
            scaling.setPlayerLevelUsed(newLevel);
            scaling.setDistanceBonus(newBonusPool);
            scaling.setStats(newStats);

            updateEntityStats(store, mobRef, scaling, newStats, manager);

            // Update native nameplate with new level
            Nameplate nameplate = store.getComponent(mobRef, Nameplate.getComponentType());
            if (nameplate != null) {
                String newText = MobInfoFormatter.formatPlainText(
                    newLevel, 0, classification, null);
                nameplate.setText(newText);
            }

            stats[1]++;
            refreshesThisTick++;
        }
    }

    private boolean isNearAnyPlayer(@Nonnull Vector3d mobPosition, @Nonnull Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        if (world == null) {
            return false;
        }

        for (PlayerRef playerRef : world.getPlayerRefs()) {
            Ref<EntityStore> entityRef = playerRef.getReference();
            if (entityRef == null || !entityRef.isValid()) {
                continue;
            }

            TransformComponent playerTransform = store.getComponent(entityRef, transformType);
            if (playerTransform == null) {
                continue;
            }

            double distSquared = distanceSquared(mobPosition, playerTransform.getPosition());
            if (distSquared <= proximityRadiusSquared) {
                return true;
            }
        }

        return false;
    }

    /**
     * Updates entity health stats using the same weighted RPG formula as MobScalingSystem.
     *
     * <p>CRITICAL: This must match MobScalingSystem.applyStatModifiers() exactly:
     * - ADDITIVE modifier (not MULTIPLICATIVE) to replace vanilla HP
     * - Weighted RPG formula with progressive + exponential scaling
     * - Clear vanilla health regen after modifier update
     */
    private void updateEntityStats(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> mobRef,
        @Nonnull MobScalingComponent scaling,
        @Nonnull MobStats newStats,
        @Nonnull MobScalingManager manager
    ) {
        EntityStatMap statMap = store.getComponent(mobRef, EntityStatMap.getComponentType());
        if (statMap == null) {
            return;
        }

        if (healthStatIndex < 0) {
            healthStatIndex = EntityStatType.getAssetMap().getIndex("Health");
        }

        EntityStatValue healthStat = statMap.get(healthStatIndex);
        if (healthStat == null) {
            return;
        }

        // CRITICAL: Get current health value first and validate
        float currentHealth = healthStat.get();

        // Safety: Don't update dead entities (health already at 0)
        if (currentHealth <= 0) {
            return;
        }

        // Calculate current ratio to preserve health percentage during refresh
        float max = healthStat.getMax();

        // CRITICAL: Guard against invalid max health (can happen during modifier transitions)
        if (max <= 0) {
            LOGGER.atWarning().log("Skipping health update: invalid max health %.1f", max);
            return;
        }

        float currentRatio = currentHealth / max;

        // === WEIGHTED RPG FORMULA (same as MobScalingSystem.applyStatModifiers) ===
        int vanillaHP = scaling.getVanillaHP();
        if (vanillaHP <= 0) {
            vanillaHP = 100;
        }

        double rpgTargetHP = newStats.maxHealth();
        int mobLevel = scaling.getMobLevel();

        double progressiveScale = manager.getStatPoolConfig().calculateScalingFactor(mobLevel);
        double expMultiplier = manager.getConfig().getExponentialScaling().calculateMultiplier(mobLevel);
        double bossMultiplier = (scaling.getClassification() == RPGMobClass.BOSS) ? 2.5 : 1.0;
        double weight = Math.sqrt((double) vanillaHP / 100.0);
        double effectiveRpg = Math.max(rpgTargetHP * progressiveScale * expMultiplier, 10.0 * bossMultiplier);
        double finalHP = effectiveRpg * weight;

        // ADDITIVE offset to replace vanilla HP (not MULTIPLICATIVE)
        float healthOffset = (float) (finalHP - vanillaHP);

        statMap.putModifier(healthStatIndex, RPG_HEALTH_KEY,
            new StaticModifier(
                Modifier.ModifierTarget.MAX,
                StaticModifier.CalculationType.ADDITIVE,
                healthOffset
            ));

        // Restore health based on preserved ratio
        if (currentRatio >= 0.999f && currentHealth > 0) {
            statMap.maximizeStatValue(healthStatIndex);
        } else {
            float newMax = healthStat.getMax();
            statMap.setStatValue(healthStatIndex, newMax * currentRatio);
        }

        // Clear vanilla regen (same as MobScalingSystem — prevents percentage-based
        // vanilla regen from scaling with our modified HP)
        MobScalingSystem.clearVanillaHealthRegen(healthStat);
    }

    /**
     * Updates the movement speed effect for a mob when its level changes.
     *
     * @param store The entity store
     * @param mobRef The mob entity reference
     * @param newStats The new mob stats
     */
    private void updateSpeedEffect(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> mobRef,
        @Nonnull MobStats newStats
    ) {
        float speedMultiplier = (float) newStats.moveSpeed();
        if (Math.abs(speedMultiplier - 1.0f) < 0.01f) {
            return; // Normal speed, no effect needed
        }

        MobScalingManager manager = plugin.getMobScalingManager();
        if (manager == null) {
            return;
        }

        MobSpeedEffectManager speedManager = manager.getSpeedEffectManager();
        if (speedManager == null || !speedManager.isInitialized()) {
            return;
        }

        // Remove existing speed effects first, then apply new one
        speedManager.removeSpeedEffects(mobRef, store);
        speedManager.applySpeedEffect(mobRef, speedMultiplier, store);
    }

    /**
     * Updates the knockback resistance for a mob when its level changes.
     *
     * @param store The entity store
     * @param mobRef The mob entity reference
     * @param newStats The new mob stats
     */
    private void updateKnockbackResistance(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> mobRef,
        @Nonnull MobStats newStats
    ) {
        double knockbackResistance = newStats.knockbackResistance();

        // Skip if no resistance (0% = normal knockback)
        if (knockbackResistance <= 0.0) {
            return;
        }

        NPCEntity npc = store.getComponent(mobRef, npcType);
        if (npc == null) {
            return;
        }

        Role role = npc.getRole();
        if (role == null) {
            return;
        }

        MotionController motionController = role.getActiveMotionController();
        if (motionController == null) {
            return;
        }

        // Get base knockback scale from role (default is 1.0)
        double baseKnockbackScale = role.getKnockbackScale();

        // Calculate effective knockback scale: base * (1 - resistance/100)
        double resistanceFactor = 1.0 - Math.min(knockbackResistance, 100.0) / 100.0;
        double effectiveKnockbackScale = baseKnockbackScale * resistanceFactor;

        // Apply the modified knockback scale
        motionController.setKnockbackScale(effectiveKnockbackScale);
    }

    @Nonnull
    private MobStats convertToLegacyStats(@Nonnull MobStatProfile profile) {
        // Create ElementalStats with all elemental modifiers from profile
        ElementalStats elementalStats = new ElementalStats();
        elementalStats.setFlatDamage(ElementType.FIRE, profile.fireDamage());
        elementalStats.setFlatDamage(ElementType.WATER, profile.waterDamage());
        elementalStats.setFlatDamage(ElementType.LIGHTNING, profile.lightningDamage());
        elementalStats.setFlatDamage(ElementType.VOID, profile.voidDamage());
        elementalStats.setResistance(ElementType.FIRE, profile.fireResistance());
        elementalStats.setResistance(ElementType.WATER, profile.waterResistance());
        elementalStats.setResistance(ElementType.LIGHTNING, profile.lightningResistance());
        elementalStats.setResistance(ElementType.VOID, profile.voidResistance());
        elementalStats.setPenetration(ElementType.FIRE, profile.firePenetration());
        elementalStats.setPenetration(ElementType.WATER, profile.waterPenetration());
        elementalStats.setPenetration(ElementType.LIGHTNING, profile.lightningPenetration());
        elementalStats.setPenetration(ElementType.VOID, profile.voidPenetration());
        // Increased damage (additive %)
        elementalStats.setPercentDamage(ElementType.FIRE, profile.fireIncreasedDamage());
        elementalStats.setPercentDamage(ElementType.WATER, profile.waterIncreasedDamage());
        elementalStats.setPercentDamage(ElementType.LIGHTNING, profile.lightningIncreasedDamage());
        elementalStats.setPercentDamage(ElementType.VOID, profile.voidIncreasedDamage());
        // More damage (multiplicative %)
        elementalStats.setMultiplierDamage(ElementType.FIRE, profile.fireMoreDamage());
        elementalStats.setMultiplierDamage(ElementType.WATER, profile.waterMoreDamage());
        elementalStats.setMultiplierDamage(ElementType.LIGHTNING, profile.lightningMoreDamage());
        elementalStats.setMultiplierDamage(ElementType.VOID, profile.voidMoreDamage());

        return new MobStats(
            profile.mobLevel(),
            profile.totalPool(),
            profile.maxHealth(),
            profile.physicalDamage(),
            profile.armor(),
            profile.moveSpeed(),
            profile.criticalChance(),
            profile.criticalMultiplier(),
            profile.dodgeChance(),
            profile.lifeSteal(),
            profile.armorPenetration(),
            profile.healthRegen(),
            profile.blockChance(),
            profile.parryChance(),
            profile.trueDamage(),
            profile.accuracy(),
            profile.knockbackResistance(),
            elementalStats
        );
    }

    private static double distanceSquared(@Nonnull Vector3d a, @Nonnull Vector3d b) {
        double dx = a.x - b.x;
        double dy = a.y - b.y;
        double dz = a.z - b.z;
        return dx * dx + dy * dy + dz * dz;
    }
}
